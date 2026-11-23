package io.stridelab.privatealpha

import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Deterministic confidence-scored matcher between imported running activities and
 * scheduled Engine 1.5 running sessions. Never overwrites manual links/rejections
 * and never marks workout quality adherence as passed.
 */
internal object Engine15ActivityAutoMatcher {
    private const val WEIGHT_DATE = 0.45
    private const val WEIGHT_VOLUME = 0.40
    private const val WEIGHT_PLAUSIBILITY = 0.15

    const val AUTO_LINK_THRESHOLD = 0.80
    const val CONFIRM_THRESHOLD = 0.50
    const val AMBIGUITY_MARGIN = 0.15

    private data class ScoredCandidate(
        val activity: ActivityRecord,
        val session: PlannedSession,
        val candidate: ActivityMatchCandidate
    )

    fun match(
        activities: List<ActivityRecord>,
        sessions: List<PlannedSession>,
        detailsByActivityId: Map<String, ActivityDetail> = emptyMap(),
        existingMatches: Map<String, ActivityMatchRecord> = emptyMap(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = 0L
    ): Map<String, ActivityMatchRecord> {
        val runActivities = activities
            .filter(DashboardSummary::isRun)
            .sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenBy { it.id })
        val runSessions = sessions
            .filter { it.discipline == "run" && it.status != "skipped" }
        val activityById = runActivities.associateBy { it.id }
        val sessionById = runSessions.associateBy { it.id }

        val resolved = LinkedHashMap<String, ActivityMatchRecord>()
        val reservedActivityIds = HashSet<String>()
        val reservedSessionIds = HashSet<String>()
        val rejectedPairs = HashSet<Pair<String, String>>()

        // 1. Honor explicit session links on the plan snapshot first.
        for (session in runSessions) {
            val linkedId = session.linkedActivityId ?: continue
            val activity = activityById[linkedId]
            val offset = activity?.let { dateOffsetDays(session, it, zoneId) }
                ?.takeIf { it in -1..1 }
            val existing = existingMatches[linkedId]
            resolved[linkedId] = if (existing != null && existing.plannedSessionId == session.id &&
                existing.matchStatus == ActivityMatchStatus.MANUALLY_LINKED) {
                existing.copy(isManualOverride = true)
            } else {
                ActivityMatchRecord(
                    activityId = linkedId,
                    plannedSessionId = session.id,
                    matchStatus = ActivityMatchStatus.MANUALLY_LINKED,
                    confidenceScore = 1.0,
                    dateOffsetDays = offset,
                    reasonCodes = listOf("MANUAL_OVERRIDE_LINKED"),
                    isManualOverride = true,
                    candidates = existing?.candidates ?: emptyList(),
                    evaluatedAtEpochMs = existing?.evaluatedAtEpochMs?.takeIf { it > 0L } ?: nowEpochMs
                )
            }
            reservedActivityIds += linkedId
            reservedSessionIds += session.id
        }

        // 2. Preserve existing manual overrides (MANUALLY_LINKED & MANUALLY_REJECTED).
        for ((activityId, record) in existingMatches) {
            when (record.matchStatus) {
                ActivityMatchStatus.MANUALLY_LINKED -> {
                    val targetSessionId = record.plannedSessionId
                    if (activityId !in reservedActivityIds &&
                        targetSessionId != null &&
                        targetSessionId in sessionById &&
                        targetSessionId !in reservedSessionIds
                    ) {
                        resolved[activityId] = record.copy(isManualOverride = true)
                        reservedActivityIds += activityId
                        reservedSessionIds += targetSessionId
                    }
                }
                ActivityMatchStatus.MANUALLY_REJECTED -> {
                    if (record.plannedSessionId != null) {
                        rejectedPairs += (activityId to record.plannedSessionId)
                    }
                }
                else -> Unit
            }
        }

        val openActivities = runActivities.filter { it.id !in reservedActivityIds }
        val openSessions = runSessions.filter { it.id !in reservedSessionIds }

        // 3. Score all valid (activity, openSession) pairs within [-1, +1] calendar days.
        val candidatesByActivity = LinkedHashMap<String, List<ScoredCandidate>>()
        for (activity in openActivities) {
            val scored = openSessions.mapNotNull { session ->
                if ((activity.id to session.id) in rejectedPairs) return@mapNotNull null
                val offset = dateOffsetDays(session, activity, zoneId)
                if (offset !in -1..1) return@mapNotNull null
                scorePair(
                    activity = activity,
                    session = session,
                    offsetDays = offset,
                    detail = detailsByActivityId[activity.id]
                )
            }.sortedWith(
                compareByDescending<ScoredCandidate> { it.candidate.confidenceScore }
                    .thenBy { abs(it.candidate.dateOffsetDays) }
                    .thenBy { it.session.id }
            )
            candidatesByActivity[activity.id] = scored
        }

        // 4. Identify per-session contention among qualifying top candidates (>= 0.50).
        val topBySession = LinkedHashMap<String, MutableList<ScoredCandidate>>()
        for (scoredList in candidatesByActivity.values) {
            val top = scoredList.firstOrNull() ?: continue
            if (top.candidate.confidenceScore >= CONFIRM_THRESHOLD) {
                topBySession.getOrPut(top.session.id) { mutableListOf() }.add(top)
            }
        }
        val contestedSessionIds = HashSet<String>()
        for ((sessionId, contenders) in topBySession) {
            if (contenders.size < 2) continue
            val sortedScores = contenders.map { it.candidate.confidenceScore }.sortedDescending()
            val sameDayContenders = contenders.count { it.candidate.dateOffsetDays == 0 }
            val tightScoreTie = sortedScores[0] - sortedScores[1] < AMBIGUITY_MARGIN
            if (sameDayContenders >= 2 || tightScoreTie) {
                contestedSessionIds += sessionId
            }
        }

        // 5. Assign AUTO_LINKED greedily highest-score first when unambiguous.
        val assignedSessionIds = HashSet<String>(reservedSessionIds)
        val assignedRecords = LinkedHashMap<String, ActivityMatchRecord>()
        val autoLinkQueue = openActivities.mapNotNull { activity ->
            val list = candidatesByActivity[activity.id].orEmpty()
            val top = list.firstOrNull() ?: return@mapNotNull null
            val second = list.getOrNull(1)
            val activityTie = second != null &&
                second.candidate.confidenceScore >= CONFIRM_THRESHOLD &&
                (top.candidate.confidenceScore - second.candidate.confidenceScore) < AMBIGUITY_MARGIN
            val sessionContested = top.session.id in contestedSessionIds
            val offDay = top.candidate.dateOffsetDays != 0
            if (!activityTie && !sessionContested && !offDay &&
                top.candidate.confidenceScore >= AUTO_LINK_THRESHOLD
            ) {
                top to list
            } else null
        }.sortedWith(
            compareByDescending<Pair<ScoredCandidate, List<ScoredCandidate>>> {
                it.first.candidate.confidenceScore
            }.thenBy { it.first.activity.id }
        )

        for ((top, allForActivity) in autoLinkQueue) {
            if (top.session.id in assignedSessionIds) continue
            assignedSessionIds += top.session.id
            val candidateDtos = allForActivity.take(3).map { it.candidate }
            assignedRecords[top.activity.id] = ActivityMatchRecord(
                activityId = top.activity.id,
                plannedSessionId = top.session.id,
                matchStatus = ActivityMatchStatus.AUTO_LINKED,
                confidenceScore = top.candidate.confidenceScore,
                dateOffsetDays = top.candidate.dateOffsetDays,
                reasonCodes = (top.candidate.reasonCodes + "AUTO_LINK_THRESHOLD_MET").distinct(),
                isManualOverride = false,
                candidates = candidateDtos,
                evaluatedAtEpochMs = nowEpochMs
            )
        }

        // 6. Classify remaining open activities as NEEDS_CONFIRMATION, MANUALLY_REJECTED, or UNMATCHED.
        for (activity in openActivities) {
            if (activity.id in assignedRecords) continue
            val all = candidatesByActivity[activity.id].orEmpty()
            val remainingCandidates = all.filter { it.session.id !in assignedSessionIds }
            val top = remainingCandidates.firstOrNull() ?: all.firstOrNull()
            val priorRejected = existingMatches[activity.id]
                ?.takeIf { it.matchStatus == ActivityMatchStatus.MANUALLY_REJECTED }

            if (top == null || top.candidate.confidenceScore < CONFIRM_THRESHOLD) {
                if (priorRejected != null) {
                    assignedRecords[activity.id] = priorRejected.copy(isManualOverride = true)
                } else {
                    assignedRecords[activity.id] = ActivityMatchRecord(
                        activityId = activity.id,
                        plannedSessionId = null,
                        matchStatus = ActivityMatchStatus.UNMATCHED,
                        confidenceScore = top?.candidate?.confidenceScore ?: 0.0,
                        dateOffsetDays = null,
                        reasonCodes = listOf(
                            if (top == null) "NO_CANDIDATE_IN_WINDOW" else "BELOW_CONFIRMATION_THRESHOLD"
                        ),
                        isManualOverride = false,
                        candidates = remainingCandidates.take(3).map { it.candidate },
                        evaluatedAtEpochMs = nowEpochMs
                    )
                }
                continue
            }

            // If user explicitly rejected another session and only lower-tier or no non-rejected match exists,
            // normal confirmation proceeds only on non-rejected open candidates.
            val reasons = LinkedHashSet<String>(top.candidate.reasonCodes)
            val second = remainingCandidates.getOrNull(1)
            val activityTie = second != null &&
                second.candidate.confidenceScore >= CONFIRM_THRESHOLD &&
                (top.candidate.confidenceScore - second.candidate.confidenceScore) < AMBIGUITY_MARGIN
            if (activityTie) reasons += "AMBIGUOUS_SESSION_TIE"
            if (top.session.id in contestedSessionIds) reasons += "MULTI_RUN_SAME_DAY_TIE"
            if (top.candidate.dateOffsetDays != 0) reasons += "OFF_DAY_SHIFT_CONFIRMATION"
            if (top.session.id in assignedSessionIds) reasons += "PREFERRED_SESSION_ALREADY_LINKED"
            if (top.candidate.confidenceScore < AUTO_LINK_THRESHOLD) {
                reasons += "MEDIUM_CONFIDENCE_CONFIRMATION"
            }

            val topUnassigned = remainingCandidates.firstOrNull { it.session.id !in assignedSessionIds }
            if (topUnassigned == null) {
                assignedRecords[activity.id] = priorRejected?.copy(isManualOverride = true)
                    ?: ActivityMatchRecord(
                        activityId = activity.id,
                        plannedSessionId = null,
                        matchStatus = ActivityMatchStatus.UNMATCHED,
                        confidenceScore = top.candidate.confidenceScore,
                        dateOffsetDays = null,
                        reasonCodes = listOf("NO_OPEN_CANDIDATE_SESSION"),
                        isManualOverride = false,
                        candidates = all.take(3).map { it.candidate },
                        evaluatedAtEpochMs = nowEpochMs
                    )
                continue
            }

            assignedRecords[activity.id] = ActivityMatchRecord(
                activityId = activity.id,
                plannedSessionId = topUnassigned.session.id,
                matchStatus = ActivityMatchStatus.NEEDS_CONFIRMATION,
                confidenceScore = topUnassigned.candidate.confidenceScore,
                dateOffsetDays = topUnassigned.candidate.dateOffsetDays,
                reasonCodes = reasons.toList(),
                isManualOverride = false,
                candidates = remainingCandidates.take(3).map { it.candidate },
                evaluatedAtEpochMs = nowEpochMs
            )
        }

        return LinkedHashMap<String, ActivityMatchRecord>().apply {
            putAll(resolved)
            putAll(assignedRecords)
        }
    }

    private fun dateOffsetDays(
        session: PlannedSession,
        activity: ActivityRecord,
        zoneId: ZoneId
    ): Int {
        val activityDate = activity.startedAt.atZone(zoneId).toLocalDate()
        return ChronoUnit.DAYS.between(session.date, activityDate).toInt()
    }

    private fun scorePair(
        activity: ActivityRecord,
        session: PlannedSession,
        offsetDays: Int,
        detail: ActivityDetail?
    ): ScoredCandidate {
        val reasons = ArrayList<String>(4)
        val dateScore = if (offsetDays == 0) {
            reasons += "DATE_EXACT_MATCH"
            1.00
        } else {
            reasons += "DATE_ADJACENT_DAY"
            0.55
        }

        val volumeRatio = volumeRatioForMatching(session, activity)
        val volumeScore = when {
            volumeRatio == null -> {
                reasons += "VOLUME_UNAVAILABLE"
                0.20
            }
            volumeRatio in 0.85..1.15 -> {
                reasons += "VOLUME_STRONG_MATCH"
                1.00
            }
            volumeRatio in 0.70..1.30 -> {
                reasons += "VOLUME_MODERATE_MATCH"
                0.80
            }
            volumeRatio in 0.50..1.50 -> {
                reasons += "VOLUME_PARTIAL_MATCH"
                0.55
            }
            volumeRatio in 0.35..1.75 -> {
                reasons += "VOLUME_WIDE_MISMATCH"
                0.25
            }
            else -> {
                reasons += "VOLUME_LARGE_MISMATCH"
                0.05
            }
        }

        val hasStructuredTelemetry = hasStructuredPlausibility(detail)
        val plausibilityScore = if (session.quality) {
            if (hasStructuredTelemetry) {
                reasons += "QUALITY_STRUCTURE_PLAUSIBLE"
                1.00
            } else {
                // Soft plausibility only: never disqualify a slow/unlapped run from matching a quality slot.
                reasons += "SOFT_QUALITY_STRUCTURE_NEUTRAL"
                0.50
            }
        } else {
            if (!hasStructuredTelemetry) {
                reasons += "STEADY_EFFORT_PLAUSIBLE"
                1.00
            } else {
                reasons += "MULTI_LAP_ON_EASY_DAY"
                0.65
            }
        }

        val total = (WEIGHT_DATE * dateScore +
            WEIGHT_VOLUME * volumeScore +
            WEIGHT_PLAUSIBILITY * plausibilityScore)
            .coerceIn(0.0, 1.0)
        val rounded = kotlin.math.round(total * 1000.0) / 1000.0
        return ScoredCandidate(
            activity = activity,
            session = session,
            candidate = ActivityMatchCandidate(
                plannedSessionId = session.id,
                confidenceScore = rounded,
                dateOffsetDays = offsetDays,
                reasonCodes = reasons
            )
        )
    }

    private fun volumeRatioForMatching(
        session: PlannedSession,
        activity: ActivityRecord
    ): Double? {
        val plannedKm = session.distanceKm?.takeIf { it.isFinite() && it > 0.0 }
        val actualKm = activity.distanceM?.takeIf { it.isFinite() && it > 0.0 }?.div(1000.0)
        if (plannedKm != null && actualKm != null) {
            return actualKm / plannedKm
        }
        val plannedSeconds = session.minutes.takeIf { it > 0 }?.times(60.0)
        val actualSeconds = (activity.movingTimeS ?: activity.durationS)
            ?.takeIf { it.isFinite() && it > 0.0 }
        if (plannedSeconds != null && actualSeconds != null) {
            return actualSeconds / plannedSeconds
        }
        return null
    }

    private fun hasStructuredPlausibility(detail: ActivityDetail?): Boolean {
        if (detail == null) return false
        if (detail.workoutSteps.isNotEmpty() || detail.laps.size >= 3) return true
        val lapPaces = detail.laps.mapNotNull { it.pace?.takeIf { p -> p.isFinite() && p in 120.0..1200.0 } }
        if (lapPaces.size >= 2 && (lapPaces.max() - lapPaces.min()) >= 35.0) return true
        val samplePaces = detail.samples.mapNotNull { it.pace?.takeIf { p -> p.isFinite() && p in 120.0..1200.0 } }
        if (samplePaces.size >= 10) {
            val sorted = samplePaces.sorted()
            val p15 = sorted[(sorted.size * 0.15).toInt()]
            val p85 = sorted[((sorted.size * 0.85).toInt()).coerceAtMost(sorted.lastIndex)]
            if (p85 - p15 >= 45.0) return true
        }
        return false
    }
}
