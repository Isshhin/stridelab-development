package io.stridelab.privatealpha

import kotlin.math.abs

/** I4 reports measured HR-sample availability, NOT training intensity. */
internal data class Engine15I4WorkLapHrSignal(
    val lapIndex: Int,
    val lapSeconds: Double,
    val coveredSeconds: Double,
    val coverageFraction: Double,
    val timeWeightedMeanBpm: Double,
    val sourceLapMeanBpm: Double?,
    val sourceLapMeanCrossChecked: Boolean,
    val observedHrSampleCount: Int
) {
    init {
        require(lapIndex >= 0 && lapSeconds.isFinite() && lapSeconds > 0.0)
        require(coveredSeconds.isFinite() &&
            coveredSeconds > 0.0 && coveredSeconds <= lapSeconds + 1e-6)
        require(coverageFraction.isFinite() && coverageFraction in 0.0..1.000001)
        require(timeWeightedMeanBpm.isFinite() && timeWeightedMeanBpm in 35.0..230.0)
        require(sourceLapMeanBpm == null ||
            (sourceLapMeanBpm.isFinite() && sourceLapMeanBpm in 35.0..230.0))
        require(observedHrSampleCount >= 2)
    }
}

internal data class Engine15I4RecordingHrSignal(
    val activityId: String,
    val selectedWorkLaps: List<Engine15I4WorkLapHrSignal>,
    val selectedLapSeconds: Double,
    val coveredSeconds: Double,
    val coverageFraction: Double,
    val timeWeightedMeanBpm: Double
) {
    init {
        require(activityId.isNotBlank() && selectedWorkLaps.isNotEmpty())
        require(selectedLapSeconds.isFinite() && selectedLapSeconds > 0.0)
        require(coveredSeconds.isFinite() && coveredSeconds > 0.0)
        require(coverageFraction.isFinite() && coverageFraction in 0.0..1.000001)
        require(timeWeightedMeanBpm.isFinite() && timeWeightedMeanBpm in 35.0..230.0)
    }
}

internal sealed interface Engine15I4HrSignalDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val activityId: String? = null,
        val lapIndex: Int? = null
    ) : Engine15I4HrSignalDecision

    /** Source HR stream *coverage only*; no physiological classification. */
    data class SignalReview(
        val policyId: String,
        val originalI3: Engine15I3QualityDurationDecision.DurationConsistent,
        val recordings: List<Engine15I4RecordingHrSignal>,
        val ruleIds: List<String>,
        val hrStreamReviewOnly: Boolean = true,
        val sourceOwnerAuthenticated: Boolean = false,
        val sensorCalibrationVerified: Boolean = false,
        val thresholdOrTempoIntensityVerified: Boolean = false,
        val athleteResponseVerified: Boolean = false,
        val doseAuthorized: Boolean = false,
        val distanceReconciled: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15I4HrSignalDecision {
        init {
            require(recordings.size == 2)
            require(recordings.map { it.activityId }.distinct().size == 2)
            require(ruleIds.distinct() == ruleIds)
            require(hrStreamReviewOnly && !sourceOwnerAuthenticated &&
                !sensorCalibrationVerified && !thresholdOrTempoIntensityVerified &&
                !athleteResponseVerified && !doseAuthorized &&
                !distanceReconciled && !executable && !authorizedForProduction)
        }
    }
}

/**
 * Reuses I3's exact, two-recording source-link/lap-duration review.
 * Descriptive HR quality is checked on the original selected work-lap windows.
 * No source threshold HR/pace/power calibrations are authenticated here.
 */
internal object Engine15I4WorkLapHeartRateReview {
    const val POLICY_ID = "engine15.i4.work-lap-hr-coverage-shadow-20261117.1"
    private const val MAX_SAMPLE_GAP_SECONDS = 30.0
    private const val REQUIRED_LAP_COVERAGE = 0.80
    private const val MAX_LAP_HR_AVG_DELTA_BPM = 12.0

    fun inspect(
        source: Engine15IndependentVolumePlanner.Result,
        request: Engine15M5QualityPreviewRequest,
        evidence: Engine15I3QualityDurationInput
    ): Engine15I4HrSignalDecision {
        fun defer(
            code: String, why: String,
            activity: String? = null, lap: Int? = null
        ) = Engine15I4HrSignalDecision.Deferred(code, why, activity, lap)

        val prior = when (val inspected =
            Engine15I3SourceLinkedQualityDuration.inspect(source, request, evidence)
        ) {
            is Engine15I3QualityDurationDecision.Deferred ->
                return defer("UPSTREAM_" + inspected.reasonCode, inspected.explanation)
            is Engine15I3QualityDurationDecision.DurationConsistent -> inspected
        }

        val recordings = mutableListOf<Engine15I4RecordingHrSignal>()
        for (bound in prior.lapBindings) {
            val sourceObservation = evidence.observations.singleOrNull {
                it.lapClaim.evidence.sourceRecordId == bound.activityId
            } ?: return defer("I3_LINKED_OBSERVATION_NOT_UNIQUE",
                "Every I3 source must correspond to one original work-lap claim.",
                bound.activityId)
            val detail = sourceObservation.detail
            if (detail == null || detail.activityId != bound.activityId)
                return defer("SOURCE_DETAIL_UNAVAILABLE",
                    "No matching HR time-series detail for the linked source.",
                    bound.activityId)
            if (detail.samples.size > 20_000)
                return defer("TOO_MANY_HR_SAMPLES",
                    "A source time-series exceeds the bounded review size.",
                    bound.activityId)
            if (detail.samples.zipWithNext().any { (a, b) ->
                a.index < 0 || b.index <= a.index ||
                    !a.seconds.isFinite() || !b.seconds.isFinite() ||
                    a.seconds < 0.0 || b.seconds <= a.seconds
            } || detail.samples.any {
                it.index < 0 || !it.seconds.isFinite() || it.seconds < 0.0
            })
                return defer("SOURCE_SAMPLE_ORDER_INVALID",
                    "Source samples must be indexed and strictly elapsed-time ordered.",
                    bound.activityId)

            val perLap = mutableListOf<Engine15I4WorkLapHrSignal>()
            for (lapIndex in bound.workLapIndices) {
                val lap = detail.laps.singleOrNull { it.index == lapIndex }
                    ?: return defer("SOURCE_WORK_LAP_MISSING",
                        "Selected I3 work lap is not unique in the source detail.",
                        bound.activityId, lapIndex)
                val start = lap.startSeconds
                val duration = lap.seconds
                if (start == null || duration == null || !start.isFinite() ||
                    !duration.isFinite() || start < 0.0 || duration <= 0.0 ||
                    !((start + duration).isFinite()))
                    return defer("SOURCE_WORK_LAP_WINDOW_INVALID",
                        "The work lap has no valid recorded time window.",
                        bound.activityId, lapIndex)

                val end = start + duration
                // An I3 work-lap window cannot overlap any timer pause.
                // Check again here so the descriptive HR calculation cannot
                // count paused elapsed time as measured work time.
                if (detail.pauseIntervals.any {
                    it.startSeconds < end && it.endSeconds > start
                })
                    return defer("SOURCE_WORK_HR_OVERLAPS_PAUSE",
                        "Work-lap HR must not include a recorded timer pause.",
                        bound.activityId, lapIndex)
                val samples = detail.samples.filter {
                    it.seconds >= start && it.seconds <= end
                }
                if (samples.any {
                    it.hr != null && (!it.hr.isFinite() || it.hr !in 35.0..230.0)
                })
                    return defer("INVALID_WORK_LAP_HEART_RATE",
                        "A selected work lap contains an implausible or nonfinite HR sample.",
                        bound.activityId, lapIndex)

                var covered = 0.0
                var weightedSum = 0.0
                for ((a, b) in samples.zipWithNext()) {
                    val gap = b.seconds - a.seconds
                    val first = a.hr
                    val second = b.hr
                    if (first == null || second == null ||
                        gap <= 0.0 || gap > MAX_SAMPLE_GAP_SECONDS) continue
                    covered += gap
                    weightedSum += (first + second) * 0.5 * gap
                }
                val coverage = covered / duration
                if (!coverage.isFinite() || coverage + 1e-9 < REQUIRED_LAP_COVERAGE)
                    return defer("INSUFFICIENT_WORK_LAP_HR_COVERAGE",
                        "Each individual selected work lap needs at least 80% time-covered HR.",
                        bound.activityId, lapIndex)
                val mean = weightedSum / covered
                if (!mean.isFinite() || mean !in 35.0..230.0)
                    return defer("INVALID_WEIGHTED_WORK_LAP_HR",
                        "The selected work lap cannot produce a valid descriptive HR mean.",
                        bound.activityId, lapIndex)

                val lapAverage = lap.hr
                if (lapAverage != null &&
                    (!lapAverage.isFinite() || lapAverage !in 35.0..230.0 ||
                        abs(lapAverage - mean) > MAX_LAP_HR_AVG_DELTA_BPM))
                    return defer("SOURCE_LAP_AVG_HR_CONFLICT",
                        "Source lap average HR differs too much from covered HR samples.",
                        bound.activityId, lapIndex)

                perLap += Engine15I4WorkLapHrSignal(
                    lapIndex = lapIndex,
                    lapSeconds = duration,
                    coveredSeconds = covered,
                    coverageFraction = coverage,
                    timeWeightedMeanBpm = mean,
                    sourceLapMeanBpm = lapAverage,
                    sourceLapMeanCrossChecked = lapAverage != null,
                    observedHrSampleCount = samples.count { it.hr != null }
                )
            }
            val totalTime = perLap.sumOf { it.lapSeconds }
            val totalCovered = perLap.sumOf { it.coveredSeconds }
            recordings += Engine15I4RecordingHrSignal(
                activityId = bound.activityId,
                selectedWorkLaps = perLap,
                selectedLapSeconds = totalTime,
                coveredSeconds = totalCovered,
                coverageFraction = totalCovered / totalTime,
                timeWeightedMeanBpm = perLap.sumOf {
                    it.timeWeightedMeanBpm * it.coveredSeconds
                } / totalCovered
            )
        }
        return Engine15I4HrSignalDecision.SignalReview(
            policyId = POLICY_ID,
            originalI3 = prior,
            recordings = recordings,
            ruleIds = listOf(
                "I3_SOURCE_LAP_DURATION_BINDING_RECOMPUTED",
                "SELECTED_WORK_LAPS_ONLY_NOT_ENTIRE_ACTIVITY",
                "STRICT_SAMPLE_ORDER_AND_NO_INVALID_WORK_HR",
                "MAX_30_SECOND_SAMPLE_PAIR_GAP",
                "MIN_80_PERCENT_PER_LAP_TIME_COVERAGE",
                "OPTIONAL_SOURCE_LAP_HR_MEAN_CROSSCHECK",
                "HR_SIGNAL_ONLY_NOT_THRESHOLD_OR_TEMPO_CLASSIFICATION",
                "NO_SENSOR_CALIBRATION_OR_PRODUCTION_AUTHORITY"
            )
        )
    }
}
