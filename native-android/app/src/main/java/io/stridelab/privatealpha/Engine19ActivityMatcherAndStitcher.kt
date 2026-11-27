package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class StitchSegmentRole {
    WARM_UP,
    MAIN,
    COOL_DOWN,
    INTERRUPTED_SEGMENT,
    COMPLETE_STANDALONE
}

internal data class StitchableRecording(
    val activityId: String,
    val discipline: String,
    val startUtc: Instant,
    val endUtc: Instant,
    val role: StitchSegmentRole,
    val deviceSource: String? = null,
    val structuredWorkoutId: String? = null,
    val startLatDeg: Double? = null,
    val startLonDeg: Double? = null,
    val endLatDeg: Double? = null,
    val endLonDeg: Double? = null,
    val userConfirmedContinuity: Boolean = false
) {
    init {
        require(!endUtc.isBefore(startUtc)) { "endUtc ($endUtc) cannot precede startUtc ($startUtc)" }
    }
}

internal enum class StitchOutcome {
    STITCHED,
    REJECTED_DISCIPLINE_MISMATCH,
    ROUTE_TO_DEDUP_OVERLAP,
    REJECTED_TWO_STANDALONE,
    REJECTED_NO_CONTINUITY_SIGNAL,
    REJECTED_GAP_TOO_LONG
}

internal data class CompositeCanonicalActivity(
    val compositeId: String,
    val discipline: String,
    val startUtc: Instant,
    val endUtc: Instant,
    val componentRawActivityIds: List<String>,
    val continuityProvenance: String
)

internal data class StitchEvaluationResult(
    val outcome: StitchOutcome,
    val composite: CompositeCanonicalActivity? = null,
    val reason: String
)

internal enum class BipartiteMatchConfidence {
    MANUAL_CONFIRMED,
    AUTO_MATCH_HIGH,
    AMBIGUOUS_USER_CONFIRM,
    UNPLANNED_EXTRA
}

internal data class MatchableActivity(
    val activityId: String,
    val discipline: String,
    val performedDate: LocalDate,
    val durationMinutes: Int,
    val distanceKm: Double? = null,
    val isQualityObserved: Boolean = false,
    val isUserTaggedRace: Boolean = false,
    val componentRawActivityIds: List<String> = listOf(activityId)
)

internal data class MatchablePlannedSession(
    val sessionId: String,
    val discipline: String,
    val scheduledDate: LocalDate,
    val plannedMinutes: Int,
    val plannedDistanceKm: Double? = null,
    val isQualityPlanned: Boolean = false,
    val isRaceFixedDate: Boolean = false,
    val isSkipped: Boolean = false
)

internal data class GlobalMatchAssignment(
    val activityId: String,
    val plannedSessionId: String?,
    val confidence: BipartiteMatchConfidence,
    val compatibilityScore: Double,
    val alternativeSessionIds: List<String>,
    val reason: String
)

internal data class GlobalMatchResult(
    val assignmentsByActivityId: Map<String, GlobalMatchAssignment>,
    val matchedSessionIds: Set<String>,
    val missedSessionIds: Set<String>,
    val observedWorkloadActivityIds: Set<String>
)

/**
 * Engine 1.9 Multi-Condition Stitching Gate & Global 1-to-1 Bipartite Activity Matcher (Track 7 §3, MOD-22).
 *
 * Guarantees:
 * - Strictly blocks stitching overlapping recordings (routes to AS-02 dedup) or two standalone sessions (CD-02).
 * - Uses exact pure-Kotlin Hungarian minimum-cost 1-to-1 assignment without artificial subset caps (MOD-22).
 * - Enforces strict 1-activity <= 1-session & 1-session <= 1-activity invariance (CD-03).
 * - Always records 100% of deduplicated activities into [observedWorkloadActivityIds] even when ambiguous.
 */
internal object Engine19ActivityMatcherAndStitcher {

    fun evaluateStitchPair(
        first: StitchableRecording,
        second: StitchableRecording
    ): StitchEvaluationResult {
        if (first.discipline != second.discipline) {
            return StitchEvaluationResult(
                outcome = StitchOutcome.REJECTED_DISCIPLINE_MISMATCH,
                reason = "Stitching requires identical discipline (${first.discipline} vs ${second.discipline})."
            )
        }
        val (earlier, later) = if (!second.startUtc.isBefore(first.startUtc)) first to second else second to first
        if (later.startUtc.isBefore(earlier.endUtc)) {
            return StitchEvaluationResult(
                outcome = StitchOutcome.ROUTE_TO_DEDUP_OVERLAP,
                reason = "Overlapping timestamps belong to deduplication, never stitching."
            )
        }
        if (earlier.role == StitchSegmentRole.COMPLETE_STANDALONE &&
            later.role == StitchSegmentRole.COMPLETE_STANDALONE
        ) {
            return StitchEvaluationResult(
                outcome = StitchOutcome.REJECTED_TWO_STANDALONE,
                reason = "Two standalone completed workouts cannot be stitched into one activity."
            )
        }
        val complementaryRoles = (earlier.role == StitchSegmentRole.WARM_UP && later.role == StitchSegmentRole.MAIN) ||
            (earlier.role == StitchSegmentRole.MAIN && later.role == StitchSegmentRole.COOL_DOWN) ||
            (earlier.role == StitchSegmentRole.WARM_UP && later.role == StitchSegmentRole.COOL_DOWN) ||
            (earlier.role == StitchSegmentRole.INTERRUPTED_SEGMENT || later.role == StitchSegmentRole.INTERRUPTED_SEGMENT)
        if (!complementaryRoles) {
            return StitchEvaluationResult(
                outcome = StitchOutcome.REJECTED_TWO_STANDALONE,
                reason = "Segment roles (${earlier.role}, ${later.role}) are not complementary or interrupted."
            )
        }

        val gapSeconds = ChronoUnit.SECONDS.between(earlier.endUtc, later.startUtc)
        if (gapSeconds > Engine19CoachingPolicyRegistry.STITCH_MAX_GAP_SECONDS) {
            return StitchEvaluationResult(
                outcome = StitchOutcome.REJECTED_GAP_TOO_LONG,
                reason = "Gap of $gapSeconds s exceeds the ${Engine19CoachingPolicyRegistry.STITCH_MAX_GAP_SECONDS} s limit."
            )
        }

        val sharedWorkoutId = earlier.structuredWorkoutId != null &&
            earlier.structuredWorkoutId == later.structuredWorkoutId
        val matchingDevice = earlier.deviceSource != null && earlier.deviceSource == later.deviceSource
        val gpsProximity = if (matchingDevice &&
            earlier.endLatDeg != null && earlier.endLonDeg != null &&
            later.startLatDeg != null && later.startLonDeg != null
        ) {
            haversineMeters(earlier.endLatDeg, earlier.endLonDeg, later.startLatDeg, later.startLonDeg) <=
                Engine19CoachingPolicyRegistry.STITCH_MAX_GPS_GAP_METERS
        } else {
            false
        }
        val gpsMissingOnBoth = earlier.endLatDeg == null && later.startLatDeg == null

        val continuitySignal = when {
            later.userConfirmedContinuity || earlier.userConfirmedContinuity -> "USER_CONFIRMED_CONTINUITY"
            sharedWorkoutId -> "SHARED_STRUCTURED_WORKOUT_ID"
            matchingDevice && gpsProximity -> "DEVICE_AND_GPS_PROXIMITY"
            matchingDevice && gpsMissingOnBoth -> "MATCHING_DEVICE_INTERRUPTION"
            else -> null
        } ?: return StitchEvaluationResult(
            outcome = StitchOutcome.REJECTED_NO_CONTINUITY_SIGNAL,
            reason = "Short gap without shared device/GPS proximity, shared structured ID, or explicit confirmation."
        )

        val composite = CompositeCanonicalActivity(
            compositeId = "composite:${earlier.activityId}+${later.activityId}",
            discipline = earlier.discipline,
            startUtc = earlier.startUtc,
            endUtc = later.endUtc,
            componentRawActivityIds = listOf(earlier.activityId, later.activityId),
            continuityProvenance = continuitySignal
        )
        return StitchEvaluationResult(
            outcome = StitchOutcome.STITCHED,
            composite = composite,
            reason = "Stitched complementary segments cleanly via $continuitySignal."
        )
    }

    fun matchGlobalBipartite(
        activities: List<MatchableActivity>,
        plannedSessions: List<MatchablePlannedSession>,
        manualLinks: Map<String, String> = emptyMap(),
        manualRejections: Set<Pair<String, String>> = emptySet()
    ): GlobalMatchResult {
        val observedIds = activities.mapTo(LinkedHashSet()) { it.activityId }
        val activeSessions = plannedSessions.filterNot { it.isSkipped }
        val assignments = LinkedHashMap<String, GlobalMatchAssignment>()
        val reservedActivityIds = HashSet<String>()
        val reservedSessionIds = HashSet<String>()

        val sessionById = activeSessions.associateBy { it.sessionId }
        val activityById = activities.associateBy { it.activityId }
        for ((actId, sessId) in manualLinks) {
            val act = activityById[actId] ?: continue
            val sess = sessionById[sessId] ?: continue
            if (actId in reservedActivityIds || sessId in reservedSessionIds) continue
            if (act.discipline != sess.discipline) continue
            reservedActivityIds += actId
            reservedSessionIds += sessId
            assignments[actId] = GlobalMatchAssignment(
                activityId = actId,
                plannedSessionId = sessId,
                confidence = BipartiteMatchConfidence.MANUAL_CONFIRMED,
                compatibilityScore = 1.0,
                alternativeSessionIds = emptyList(),
                reason = "Explicitly linked by athlete."
            )
        }

        val openActivities = activities.filter { it.activityId !in reservedActivityIds }
            .sortedWith(compareBy<MatchableActivity> { it.performedDate }.thenBy { it.activityId })
        val openSessions = activeSessions.filter { it.sessionId !in reservedSessionIds }
            .sortedWith(compareBy<MatchablePlannedSession> { it.scheduledDate }.thenBy { it.sessionId })

        val m = openActivities.size
        val n = openSessions.size
        if (m == 0) {
            return GlobalMatchResult(
                assignmentsByActivityId = assignments,
                matchedSessionIds = reservedSessionIds,
                missedSessionIds = openSessions.mapTo(LinkedHashSet()) { it.sessionId },
                observedWorkloadActivityIds = observedIds
            )
        }

        val scores = Array(m) { i ->
            DoubleArray(n) { j ->
                val pair = openActivities[i].activityId to openSessions[j].sessionId
                if (pair in manualRejections) -1.0
                else scoreCompatibility(openActivities[i], openSessions[j])
            }
        }

        // Exact (m + n) x (m + n) cost matrix with dummy unmatched sinks & missed sources (MOD-22).
        val dim = m + n
        val largeCost = 1_000_000.0
        val cost = Array(dim) { DoubleArray(dim) { largeCost } }
        for (i in 0 until m) {
            for (j in 0 until n) {
                val q = scores[i][j]
                if (q >= Engine19CoachingPolicyRegistry.MATCH_COMPATIBILITY_FLOOR) {
                    cost[i][j] = -q
                }
            }
            // Diagonal unmatched sink for activity i (off-diagonal dummies stay largeCost so 1 dummy slot per row)
            cost[i][n + i] = -Engine19CoachingPolicyRegistry.MATCH_UNMATCHED_COST
        }
        for (k in 0 until n) {
            for (j in 0 until n) {
                cost[m + k][j] = -Engine19CoachingPolicyRegistry.MATCH_MISSED_COST
            }
            for (j in n until dim) {
                cost[m + k][j] = 0.0
            }
        }

        val matchedCols = if (dim > 0) solveHungarianMinCost(cost) else IntArray(0)
        val matchedSessions = HashSet<String>(reservedSessionIds)

        for (i in 0 until m) {
            val act = openActivities[i]
            val col = matchedCols[i]
            if (col !in 0 until n || scores[i][col] < Engine19CoachingPolicyRegistry.MATCH_COMPATIBILITY_FLOOR) {
                assignments[act.activityId] = GlobalMatchAssignment(
                    activityId = act.activityId,
                    plannedSessionId = null,
                    confidence = BipartiteMatchConfidence.UNPLANNED_EXTRA,
                    compatibilityScore = 0.0,
                    alternativeSessionIds = emptyList(),
                    reason = "No compatible planned session within matching window; recorded in observed workload."
                )
                continue
            }

            val chosenSession = openSessions[col]
            val chosenScore = scores[i][col]
            val rivalScoresForActivity = (0 until n)
                .filter { j -> j != col && scores[i][j] >= Engine19CoachingPolicyRegistry.MATCH_COMPATIBILITY_FLOOR }
                .sortedByDescending { scores[i][it] }
            val topActivityAlt = rivalScoresForActivity.firstOrNull()?.let { scores[i][it] }
            val activityAmbiguous = topActivityAlt != null &&
                (chosenScore - topActivityAlt) < Engine19CoachingPolicyRegistry.MATCH_AMBIGUITY_MARGIN

            val rivalActivitiesForSession = (0 until m)
                .filter { r -> r != i && scores[r][col] >= Engine19CoachingPolicyRegistry.MATCH_COMPATIBILITY_FLOOR }
                .sortedByDescending { scores[it][col] }
            val topSessionAlt = rivalActivitiesForSession.firstOrNull()?.let { scores[it][col] }
            val sessionContested = topSessionAlt != null &&
                (chosenScore - topSessionAlt) < Engine19CoachingPolicyRegistry.MATCH_AMBIGUITY_MARGIN

            val dateDelta = abs(ChronoUnit.DAYS.between(act.performedDate, chosenSession.scheduledDate))
            val confidence = if (activityAmbiguous || sessionContested || dateDelta > 0L ||
                chosenScore < Engine19CoachingPolicyRegistry.MATCH_AUTO_LINK_THRESHOLD
            ) {
                BipartiteMatchConfidence.AMBIGUOUS_USER_CONFIRM
            } else {
                matchedSessions += chosenSession.sessionId
                BipartiteMatchConfidence.AUTO_MATCH_HIGH
            }

            assignments[act.activityId] = GlobalMatchAssignment(
                activityId = act.activityId,
                plannedSessionId = chosenSession.sessionId,
                confidence = confidence,
                compatibilityScore = chosenScore,
                alternativeSessionIds = rivalScoresForActivity.take(2).map { openSessions[it].sessionId },
                reason = if (confidence == BipartiteMatchConfidence.AUTO_MATCH_HIGH) {
                    "Clear unique 1-to-1 match on scheduled day."
                } else {
                    "Candidate match found with competing options or shifted date; ask athlete before claiming session credit."
                }
            )
        }

        val missedSessions = openSessions
            .map { it.sessionId }
            .filterTo(LinkedHashSet()) { it !in matchedSessions }

        return GlobalMatchResult(
            assignmentsByActivityId = assignments,
            matchedSessionIds = matchedSessions,
            missedSessionIds = missedSessions,
            observedWorkloadActivityIds = observedIds
        )
    }

    private fun scoreCompatibility(act: MatchableActivity, sess: MatchablePlannedSession): Double {
        if (act.discipline != sess.discipline) return -1.0
        val dayDelta = abs(ChronoUnit.DAYS.between(act.performedDate, sess.scheduledDate))
        if (sess.isRaceFixedDate && (dayDelta != 0L && !act.isUserTaggedRace)) return -1.0
        if (dayDelta > Engine19CoachingPolicyRegistry.MATCH_WINDOW_CALENDAR_DAYS) return -1.0

        val dateScore = when (dayDelta) {
            0L -> 1.0
            1L -> 0.65
            else -> 0.35
        }
        val plannedDur = sess.plannedMinutes.coerceAtLeast(1).toDouble()
        val actDur = act.durationMinutes.coerceAtLeast(0).toDouble()
        val ratio = (minOf(actDur, plannedDur) / maxOf(actDur, plannedDur)).coerceIn(0.0, 1.0)
        val durScore = if (actDur == 0.0) 0.0 else ratio
        val structScore = if (act.isQualityObserved == sess.isQualityPlanned) 1.0 else 0.45

        return Engine19CoachingPolicyRegistry.MATCH_WEIGHT_DATE * dateScore +
            Engine19CoachingPolicyRegistry.MATCH_WEIGHT_DURATION * durScore +
            Engine19CoachingPolicyRegistry.MATCH_WEIGHT_STRUCTURE * structScore
    }

    /** Exact O(n^3) Jonker-Volgenant / Hungarian 1-indexed assignment solver for square double matrices. */
    private fun solveHungarianMinCost(matrix: Array<DoubleArray>): IntArray {
        val n = matrix.size
        val u = DoubleArray(n + 1)
        val v = DoubleArray(n + 1)
        val p = IntArray(n + 1)
        val way = IntArray(n + 1)
        val inf = 1e18

        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            val minv = DoubleArray(n + 1) { inf }
            val used = BooleanArray(n + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = inf
                var j1 = 0
                for (j in 1..n) {
                    if (!used[j]) {
                        val cur = matrix[i0 - 1][j - 1] - u[i0] - v[j]
                        if (cur < minv[j]) {
                            minv[j] = cur
                            way[j] = j0
                        }
                        if (minv[j] < delta) {
                            delta = minv[j]
                            j1 = j
                        }
                    }
                }
                for (j in 0..n) {
                    if (used[j]) {
                        u[p[j]] += delta
                        v[j] -= delta
                    } else {
                        minv[j] -= delta
                    }
                }
                j0 = j1
            } while (p[j0] != 0)

            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }

        val rowToCol = IntArray(n) { -1 }
        for (j in 1..n) {
            if (p[j] in 1..n) {
                rowToCol[p[j] - 1] = j - 1
            }
        }
        return rowToCol
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
    }
}
