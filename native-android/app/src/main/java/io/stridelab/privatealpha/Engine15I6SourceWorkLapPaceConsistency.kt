package io.stridelab.privatealpha

import kotlin.math.abs
import kotlin.math.max

/**
 * Recorded pace/distance values are descriptive caller-scoped FIT/Garmin
 * data. Neither source ownership nor training intensity is authenticated.
 */
internal data class Engine15I6WorkLapPaceObservation(
    val lapIndex: Int,
    val recordedMeters: Double,
    val recordedSeconds: Double,
    val derivedLapPaceSecondsPerKm: Double,
    val sourceReportedLapPaceSecondsPerKm: Double?,
    val paceCoveredSeconds: Double,
    val paceCoverageFraction: Double,
    val timeWeightedSampleSpeedMetersPerSecond: Double,
    val sourceLapSpeedMetersPerSecond: Double,
    val relativeSampleLapSpeedError: Double
) {
    init {
        require(lapIndex >= 0)
        require(recordedMeters.isFinite() && recordedMeters > 0.0)
        require(recordedSeconds.isFinite() && recordedSeconds > 0.0)
        require(derivedLapPaceSecondsPerKm.isFinite() &&
            derivedLapPaceSecondsPerKm > 0.0)
        require(sourceReportedLapPaceSecondsPerKm == null ||
            (sourceReportedLapPaceSecondsPerKm.isFinite() &&
                sourceReportedLapPaceSecondsPerKm > 0.0))
        require(paceCoveredSeconds.isFinite() &&
            paceCoveredSeconds > 0.0 && paceCoveredSeconds <= recordedSeconds + 1e-6)
        require(paceCoverageFraction.isFinite() && paceCoverageFraction in 0.0..1.000001)
        require(timeWeightedSampleSpeedMetersPerSecond.isFinite() &&
            timeWeightedSampleSpeedMetersPerSecond > 0.0)
        require(sourceLapSpeedMetersPerSecond.isFinite() &&
            sourceLapSpeedMetersPerSecond > 0.0)
        require(relativeSampleLapSpeedError.isFinite() &&
            relativeSampleLapSpeedError >= 0.0)
    }
}

internal data class Engine15I6RecordingPaceObservation(
    val activityId: String,
    val workLaps: List<Engine15I6WorkLapPaceObservation>,
    val recordedWorkMeters: Double,
    val recordedWorkSeconds: Double
) {
    init {
        require(activityId.isNotBlank() && workLaps.isNotEmpty())
        require(workLaps.map { it.lapIndex }.distinct().size == workLaps.size)
        require(recordedWorkMeters.isFinite() && recordedWorkMeters > 0.0)
        require(recordedWorkSeconds.isFinite() && recordedWorkSeconds > 0.0)
    }
}

internal sealed interface Engine15I6PaceConsistencyDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val activityId: String? = null,
        val lapIndex: Int? = null
    ) : Engine15I6PaceConsistencyDecision

    data class DescriptiveSourceConsistency(
        val policyId: String,
        val originalI5: Engine15I5DistanceFeasibilityDecision.EnvelopeReview,
        val recordings: List<Engine15I6RecordingPaceObservation>,
        val ruleIds: List<String>,
        val lapAndPaceSignalsConsistent: Boolean = true,
        val sourceOwnerAuthenticated: Boolean = false,
        val paceCalibrationVerified: Boolean = false,
        val qualityIntensityVerified: Boolean = false,
        val actualFutureSegmentDistanceVerified: Boolean = false,
        val athleteResponseVerified: Boolean = false,
        val doseAuthorized: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15I6PaceConsistencyDecision {
        init {
            require(recordings.size == 2)
            require(recordings.map { it.activityId }.distinct().size == 2)
            require(ruleIds.distinct() == ruleIds)
            require(lapAndPaceSignalsConsistent &&
                !sourceOwnerAuthenticated && !paceCalibrationVerified &&
                !qualityIntensityVerified && !actualFutureSegmentDistanceVerified &&
                !athleteResponseVerified && !doseAuthorized &&
                !executable && !authorizedForProduction)
        }
    }
}

/**
 * I6 checks only recorded source data consistency. It cannot assign a pace
 * to a future timed quality workout or infer a threshold/tempo intensity.
 *
 * Entire independent chain is recomputed:
 * M4B/M5B -> I3/I4 -> I5 -> I6, plus M6A/M6B within I5.
 */
internal object Engine15I6SourceWorkLapPaceConsistency {
    const val POLICY_ID = "engine15.i6.source-lap-pace-signal-shadow-20261117.1"

    // Input-integrity heuristics, not personalized training limits.
    private const val MIN_VALID_PACE_SECONDS_PER_KM = 60.0
    private const val MAX_VALID_PACE_SECONDS_PER_KM = 7200.0
    private const val MAX_VALID_WORK_LAP_METERS = 100_000.0
    private const val MAX_SAMPLE_GAP_SECONDS = 30.0
    private const val REQUIRED_LAP_PACE_COVERAGE = 0.80
    private const val MAX_RELATIVE_SOURCE_LAP_SPEED_ERROR = 0.15
    private const val SOURCE_LAP_PACE_RELATIVE_TOLERANCE = 0.12
    private const val SOURCE_LAP_PACE_ABSOLUTE_TOLERANCE = 20.0
    private const val WORK_SPEED_RANGE_EPS = 0.05

    fun inspect(
        source: Engine15IndependentVolumePlanner.Result,
        qualityRequest: Engine15M5QualityPreviewRequest,
        sourceEvidence: Engine15I3QualityDurationInput,
        feasibilityInput: Engine15I5DistanceFeasibilityInput
    ): Engine15I6PaceConsistencyDecision {
        fun defer(
            code: String, message: String,
            activity: String? = null, lap: Int? = null
        ) = Engine15I6PaceConsistencyDecision.Deferred(
            code, message, activity, lap
        )

        val feasible = when (val reviewed = Engine15I5TimedDistanceFeasibility.review(
            source, qualityRequest, sourceEvidence, feasibilityInput
        )) {
            is Engine15I5DistanceFeasibilityDecision.Deferred ->
                return defer("UPSTREAM_" + reviewed.reasonCode, reviewed.explanation)
            is Engine15I5DistanceFeasibilityDecision.EnvelopeReview -> reviewed
        }

        val workSpeedWindows = feasibilityInput.phaseSpeedWindows.filter {
            it.purpose == WorkoutPurpose.WORK
        }
        if (workSpeedWindows.isEmpty())
            return defer("NO_ORIGINAL_WORK_SPEED_RANGES",
                "No original work-phase speed bounds survived I5 validation.")

        val originalBindings = feasible.originalI4.originalI3.lapBindings
        val observations = mutableListOf<Engine15I6RecordingPaceObservation>()
        for (bound in originalBindings) {
            val selected = sourceEvidence.observations.singleOrNull {
                it.lapClaim.evidence.sourceRecordId == bound.activityId
            } ?: return defer("ORIGINAL_SOURCE_OBSERVATION_MISMATCH",
                "I6 requires the same uniquely linked source recording as I3.",
                bound.activityId)
            val detail = selected.detail
                ?: return defer("ORIGINAL_SOURCE_DETAIL_MISSING",
                    "I6 cannot review pace without the original source detail.",
                    bound.activityId)
            if (detail.activityId != bound.activityId)
                return defer("ORIGINAL_SOURCE_DETAIL_MISMATCH",
                    "The source detail identity differs from the I3 work-lap binding.",
                    bound.activityId)

            val workLaps = mutableListOf<Engine15I6WorkLapPaceObservation>()
            for (lapIndex in bound.workLapIndices) {
                val lap = detail.laps.singleOrNull { it.index == lapIndex }
                    ?: return defer("ORIGINAL_WORK_LAP_MISSING",
                        "Selected source work-lap index was missing or duplicated.",
                        bound.activityId, lapIndex)
                val distance = lap.distance
                val duration = lap.seconds
                val start = lap.startSeconds
                if (distance == null || !distance.isFinite() ||
                    distance <= 0.0 || distance > MAX_VALID_WORK_LAP_METERS)
                    return defer("SOURCE_WORK_LAP_DISTANCE_UNKNOWN_OR_INVALID",
                        "A selected recorded work lap needs valid positive metres.",
                        bound.activityId, lapIndex)
                if (duration == null || start == null ||
                    !duration.isFinite() || duration <= 0.0 ||
                    !start.isFinite() || start < 0.0 ||
                    !(start + duration).isFinite())
                    return defer("SOURCE_WORK_LAP_TIME_UNKNOWN_OR_INVALID",
                        "Selected work lap has no valid source time interval.",
                        bound.activityId, lapIndex)

                val derivedPace = duration * 1000.0 / distance
                val lapSpeed = distance / duration
                if (!derivedPace.isFinite() ||
                    derivedPace !in MIN_VALID_PACE_SECONDS_PER_KM..MAX_VALID_PACE_SECONDS_PER_KM)
                    return defer("SOURCE_WORK_LAP_PACE_NUMERICALLY_INVALID",
                        "Recorded lap distance divided by time is outside sanity bounds.",
                        bound.activityId, lapIndex)

                val sourcePace = lap.pace
                if (sourcePace != null &&
                    (!sourcePace.isFinite() ||
                        sourcePace !in MIN_VALID_PACE_SECONDS_PER_KM..MAX_VALID_PACE_SECONDS_PER_KM ||
                        abs(sourcePace - derivedPace) > max(
                            SOURCE_LAP_PACE_ABSOLUTE_TOLERANCE,
                            derivedPace * SOURCE_LAP_PACE_RELATIVE_TOLERANCE
                        )))
                    return defer("SOURCE_REPORTED_LAP_PACE_MISMATCH",
                        "Reported lap mean pace conflicts with recorded lap metres/time.",
                        bound.activityId, lapIndex)

                val samples = detail.samples.filter {
                    it.seconds >= start && it.seconds <= start + duration
                }
                if (samples.any {
                    it.pace != null &&
                        (!it.pace.isFinite() ||
                            it.pace !in MIN_VALID_PACE_SECONDS_PER_KM..MAX_VALID_PACE_SECONDS_PER_KM)
                })
                    return defer("SOURCE_WORK_LAP_PACE_SAMPLE_INVALID",
                        "Selected source work lap contains invalid/nonfinite pace.",
                        bound.activityId, lapIndex)

                var covered = 0.0
                var weightedSpeed = 0.0
                for ((a, b) in samples.zipWithNext()) {
                    val gap = b.seconds - a.seconds
                    val paceA = a.pace
                    val paceB = b.pace
                    if (gap <= 0.0 || gap > MAX_SAMPLE_GAP_SECONDS ||
                        paceA == null || paceB == null) continue
                    val speedA = 1000.0 / paceA
                    val speedB = 1000.0 / paceB
                    covered += gap
                    weightedSpeed += (speedA + speedB) * 0.5 * gap
                }
                val coverage = covered / duration
                if (!coverage.isFinite() ||
                    coverage + 1e-9 < REQUIRED_LAP_PACE_COVERAGE)
                    return defer("INSUFFICIENT_WORK_LAP_PACE_COVERAGE",
                        "At least 80% of every selected work lap needs bounded pace samples.",
                        bound.activityId, lapIndex)
                val meanSampleSpeed = weightedSpeed / covered
                val difference = abs(meanSampleSpeed - lapSpeed) / lapSpeed
                if (!difference.isFinite() ||
                    difference > MAX_RELATIVE_SOURCE_LAP_SPEED_ERROR)
                    return defer("SAMPLE_VS_LAP_SPEED_INCONSISTENT",
                        "Time-weighted source pace samples conflict with recorded lap speed.",
                        bound.activityId, lapIndex)

                if (workSpeedWindows.none {
                    lapSpeed >= it.minimumMetersPerSecond - WORK_SPEED_RANGE_EPS &&
                        lapSpeed <= it.maximumMetersPerSecond + WORK_SPEED_RANGE_EPS
                })
                    return defer("RECORDED_WORK_SPEED_OUTSIDE_ASSERTED_RANGE",
                        "Asserted quality-work speed intervals conflict with recent recorded work laps.",
                        bound.activityId, lapIndex)

                workLaps += Engine15I6WorkLapPaceObservation(
                    lapIndex = lapIndex,
                    recordedMeters = distance,
                    recordedSeconds = duration,
                    derivedLapPaceSecondsPerKm = derivedPace,
                    sourceReportedLapPaceSecondsPerKm = sourcePace,
                    paceCoveredSeconds = covered,
                    paceCoverageFraction = coverage,
                    timeWeightedSampleSpeedMetersPerSecond = meanSampleSpeed,
                    sourceLapSpeedMetersPerSecond = lapSpeed,
                    relativeSampleLapSpeedError = difference
                )
            }
            observations += Engine15I6RecordingPaceObservation(
                activityId = bound.activityId,
                workLaps = workLaps,
                recordedWorkMeters = workLaps.sumOf { it.recordedMeters },
                recordedWorkSeconds = workLaps.sumOf { it.recordedSeconds }
            )
        }

        return Engine15I6PaceConsistencyDecision.DescriptiveSourceConsistency(
            policyId = POLICY_ID,
            originalI5 = feasible,
            recordings = observations,
            ruleIds = listOf(
                "I5_I4_I3_AND_ORIGINAL_M6B_RECOMPUTED",
                "ORIGINAL_TWO_DISTINCT_SELECTED_SOURCE_RECORDINGS",
                "SOURCE_LAP_METERS_TIME_AND_OPTIONAL_PACE_COHERENT",
                "MINIMUM_80_PERCENT_VALID_WORK_LAP_PACE_COVERAGE",
                "RECORDED_SAMPLE_SPEED_CONSISTENT_WITH_LAP_DISTANCE",
                "RECORDED_WORK_SPEED_WITHIN_ASSERTED_WORK_PHASE_ENVELOPE",
                "NO_AUTOMATIC_SPEED_BOUNDS_OR_PACE_PRESCRIPTION",
                "UNVERIFIED_OWNER_INTENSITY_RECOVERY_DOSE_AND_PRODUCTION"
            )
        )
    }
}
