package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal enum class Engine15BaselineMetricStatus {
    AVAILABLE_REPEATED,
    SPARSE_SINGLE_OBSERVATION,
    MISSING_DATA
}

internal enum class Engine15HistoryCoverage {
    NO_RECORDED_RUNS,
    UNKNOWN_PARTIAL_CACHE
}

internal data class Engine15EffortBandSummary(
    val sampleCount: Int,
    val status: Engine15BaselineMetricStatus,
    val medianPaceSecondsPerKm: Double?,
    val medianAverageHr: Double?,
    val sourceActivityIds: List<String>
) {
    init {
        require(sampleCount >= 0 && sourceActivityIds.distinct() == sourceActivityIds)
        require(medianPaceSecondsPerKm == null || (medianPaceSecondsPerKm.isFinite() && medianPaceSecondsPerKm > 0.0))
        require(medianAverageHr == null || (medianAverageHr.isFinite() && medianAverageHr > 0.0))
    }
}

internal data class Engine15FamilyExposureBaseline(
    val family: IndependentQualityFamily,
    val confidence: IndependentQualityExposureConfidence,
    val repeatedWorkSeconds: Int?,
    val confirmedSessionCount: Int,
    val unconfirmedHeuristicCandidateCount: Int,
    val evidenceSessionIds: List<String>
) {
    init {
        require(confirmedSessionCount >= 0 && unconfirmedHeuristicCandidateCount >= 0)
        require(repeatedWorkSeconds == null || repeatedWorkSeconds in 1..14400)
        require(evidenceSessionIds.distinct() == evidenceSessionIds)
    }
}

internal data class Engine15AthleteEffortBaseline(
    val policyId: String,
    val owner: String,
    val asOf: Instant,
    val historyCoverage: Engine15HistoryCoverage,
    val recordedRunCount28d: Int,
    val activeRunWeeks28d: Int,
    val recordedMeanWeeklyKm28d: Double?,
    val longestRunKm28d: Double?,
    val easyEffort: Engine15EffortBandSummary,
    val familyExposures: Map<IndependentQualityFamily, Engine15FamilyExposureBaseline>,
    val sourceHeartRateZones: ActivitySourceHeartRateZones?,
    val missingDataNotes: List<String>,
    val ruleIds: List<String>,
    val sourceEvidenceVerified: Boolean = false
) {
    init {
        require(policyId == Engine15AthleteEffortBaselineBuilder.POLICY_ID)
        require(owner.isNotBlank())
        require(recordedRunCount28d >= 0 && activeRunWeeks28d in 0..5)
        require(recordedMeanWeeklyKm28d == null || (recordedMeanWeeklyKm28d.isFinite() && recordedMeanWeeklyKm28d > 0.0))
        require(longestRunKm28d == null || (longestRunKm28d.isFinite() && longestRunKm28d > 0.0))
        require(familyExposures.keys == IndependentQualityFamily.entries.toSet())
        require(ruleIds.distinct() == ruleIds && ruleIds.isNotEmpty())
        require(!sourceEvidenceVerified)
    }
}

/**
 * Stage 6 (Batch 32): Auditable athlete effort, volume, and quality-exposure
 * baseline builder with explicit missing-data semantics.
 *
 * - Missing history or missing HR/pace is NEVER treated as poor fitness or inactivity.
 * - Never fabricates physiological thresholds or HR zones from generic formulas.
 * - Separates confirmed/linked quality exposure from unconfirmed heuristic candidates.
 */
internal object Engine15AthleteEffortBaselineBuilder {
    const val POLICY_ID = "stridelab.engine15.athlete-effort-baseline-v1"
    private const val RECENT_WINDOW_DAYS = 28L

    fun build(
        owner: String,
        activities: List<ActivityRecord>,
        allowedActivityIds: Set<String>,
        detailsByActivityId: Map<String, ActivityDetail>,
        plans: List<PlanSnapshot>,
        userLabels: Map<String, UserActivityWorkoutLabel>,
        asOf: Instant
    ): Engine15AthleteEffortBaseline {
        require(owner.isNotBlank())
        val asOfDate = asOf.atZone(ZoneOffset.UTC).toLocalDate()
        val windowStart = asOf.minusSeconds(RECENT_WINDOW_DAYS * 86_400L)

        val detections = Engine15WorkoutDetection.detectAll(
            owner = owner,
            activities = activities,
            allowedActivityIds = allowedActivityIds,
            detailsByActivityId = detailsByActivityId,
            plans = plans,
            userLabels = userLabels,
            asOf = asOf
        )
        val primaryById = ActivityDisplayConsolidation.groups(activities)
            .associate { it.primary.id to it.primary }

        val recentDetections = detections.filter { det ->
            val record = primaryById[det.activityId] ?: return@filter false
            !record.startedAt.isBefore(windowStart) && !record.startedAt.isAfter(asOf)
        }
        val recentRecords = recentDetections.mapNotNull { primaryById[it.activityId] }
        val totalRecentKm = recentRecords.mapNotNull { it.distanceM?.takeIf { d -> d.isFinite() && d > 0.0 } }
            .sum() / 1000.0
        val meanWeeklyKm = if (totalRecentKm > 0.0) totalRecentKm / 4.0 else null
        val longestKm = recentRecords.mapNotNull { it.distanceM?.takeIf { d -> d.isFinite() && d > 0.0 } }
            .maxOrNull()?.div(1000.0)
        val activeWeeks = recentRecords.map {
            it.startedAt.atZone(ZoneOffset.UTC).toLocalDate().with(java.time.DayOfWeek.MONDAY)
        }.distinct().size

        // Easy/recovery effort summary only from explicitly user-labelled or plan-linked easy runs
        val confirmedEasy = recentDetections.filter { det ->
            det.kind in setOf(ActivityWorkoutKind.EASY_RUN, ActivityWorkoutKind.RECOVERY_RUN) &&
                det.confidence in setOf(
                    Engine15DetectionConfidence.USER_ASSERTED,
                    Engine15DetectionConfidence.INTENT_LINKED_UNVERIFIED
                )
        }
        val easyRecords = confirmedEasy.mapNotNull { primaryById[it.activityId] }
        val easyStatus = when {
            easyRecords.size >= 2 -> Engine15BaselineMetricStatus.AVAILABLE_REPEATED
            easyRecords.size == 1 -> Engine15BaselineMetricStatus.SPARSE_SINGLE_OBSERVATION
            else -> Engine15BaselineMetricStatus.MISSING_DATA
        }
        val easySummary = Engine15EffortBandSummary(
            sampleCount = easyRecords.size,
            status = easyStatus,
            medianPaceSecondsPerKm = median(easyRecords.mapNotNull {
                (it.recordedPace ?: it.garminMovingPaceSPerKm)?.takeIf { p -> p.isFinite() && p > 0.0 }
            }),
            medianAverageHr = median(easyRecords.mapNotNull {
                it.averageHr?.takeIf { hr -> hr.isFinite() && hr > 0.0 }
            }),
            sourceActivityIds = easyRecords.map { it.id }.sorted()
        )

        val qualityEvidence = Engine15WorkoutDetection.toQualitySegmentEvidence(owner, recentDetections)
        val familyExposures = IndependentQualityFamily.entries.associateWith { family ->
            val assessed = IndependentQualityExposure.assess(qualityEvidence, owner, family, asOfDate)
            val targetSubtype = when (family) {
                IndependentQualityFamily.TEMPO -> ActivityQualitySubtype.TEMPO
                IndependentQualityFamily.THRESHOLD -> ActivityQualitySubtype.THRESHOLD
                IndependentQualityFamily.INTERVALS -> ActivityQualitySubtype.INTERVALS
            }
            val heuristicCount = recentDetections.count { det ->
                det.kind == ActivityWorkoutKind.QUALITY_RUN &&
                    det.qualitySubtype == targetSubtype &&
                    det.confidence == Engine15DetectionConfidence.HEURISTIC_CANDIDATE
            }
            val confirmedCount = qualityEvidence.count { it.family == family }
            Engine15FamilyExposureBaseline(
                family = family,
                confidence = assessed.confidence,
                repeatedWorkSeconds = assessed.provisionalRepeatedWorkSeconds,
                confirmedSessionCount = confirmedCount,
                unconfirmedHeuristicCandidateCount = heuristicCount,
                evidenceSessionIds = assessed.evidenceSessionIds
            )
        }

        val latestZones = recentDetections
            .sortedByDescending { primaryById[it.activityId]?.startedAt ?: Instant.EPOCH }
            .firstNotNullOfOrNull { det ->
                det.mergedSourceIds.firstNotNullOfOrNull { id -> detailsByActivityId[id]?.sourceHeartRateZones }
            }

        val missingNotes = buildList {
            if (recentRecords.isEmpty()) {
                add("No recorded runs in the last 28 days; missing history is never treated as confirmed inactivity or poor fitness.")
            } else {
                add("Recorded cache coverage is unverified; unlisted or offline runs may exist outside the cached history.")
            }
            if (easySummary.status == Engine15BaselineMetricStatus.MISSING_DATA) {
                add("No confirmed easy/recovery runs in the last 28 days; easy pace and HR baseline remain unavailable.")
            }
            if (latestZones == null) {
                add("No source-recorded heart-rate zones found; no HR zones or physiological thresholds were fabricated.")
            }
            val unconfirmedTotal = familyExposures.values.sumOf { it.unconfirmedHeuristicCandidateCount }
            if (unconfirmedTotal > 0) {
                add("$unconfirmedTotal unconfirmed lap-structure quality candidate(s) detected; confirm or label them before using them for progression.")
            }
        }

        return Engine15AthleteEffortBaseline(
            policyId = POLICY_ID,
            owner = owner,
            asOf = asOf,
            historyCoverage = if (recentRecords.isEmpty())
                Engine15HistoryCoverage.NO_RECORDED_RUNS else Engine15HistoryCoverage.UNKNOWN_PARTIAL_CACHE,
            recordedRunCount28d = recentRecords.size,
            activeRunWeeks28d = activeWeeks,
            recordedMeanWeeklyKm28d = meanWeeklyKm,
            longestRunKm28d = longestKm,
            easyEffort = easySummary,
            familyExposures = familyExposures,
            sourceHeartRateZones = latestZones,
            missingDataNotes = missingNotes,
            ruleIds = listOf(
                "MISSING_DATA_NOT_POOR_FITNESS",
                "NO_PHYSIOLOGICAL_THRESHOLD_INFERRED",
                "SOURCE_COVERAGE_UNVERIFIED",
                "HEURISTIC_CANDIDATES_SEPARATED_FROM_EXPOSURE"
            )
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
