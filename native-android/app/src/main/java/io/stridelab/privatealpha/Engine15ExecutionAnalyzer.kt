package io.stridelab.privatealpha

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure deterministic post-run execution analyzer comparing scheduled Engine 1.5
 * workout steps/intents against FIT lap/record telemetry and optional verified HR zones.
 */
internal object Engine15ExecutionAnalyzer {
    private val TARGETED_WORK_INTENTS = setOf(
        RunningStepIntent.TEMPO,
        RunningStepIntent.THRESHOLD,
        RunningStepIntent.VO2_INTERVAL,
        RunningStepIntent.HILL,
        RunningStepIntent.STRIDE,
        RunningStepIntent.RACE_PACE
    )
    private val QUALITY_PACE_INTENTS = setOf(
        RunningStepIntent.TEMPO,
        RunningStepIntent.THRESHOLD,
        RunningStepIntent.VO2_INTERVAL,
        RunningStepIntent.RACE_PACE
    )

    private data class PlannedStepTarget(
        val stepIndex: Int,
        val purpose: String,
        val stepIntent: RunningStepIntent?,
        val plannedMeters: Int?,
        val plannedSeconds: Int?,
        val targetPaceMinSecPerKm: Int?,
        val targetPaceMaxSecPerKm: Int?,
        val targetHrMinBpm: Int?,
        val targetHrMaxBpm: Int?
    ) {
        val isTargetedWork: Boolean
            get() = purpose == "WORK" || stepIntent in TARGETED_WORK_INTENTS

        val nominalSeconds: Double
            get() = plannedSeconds?.toDouble()
                ?: ((plannedMeters ?: 0) / 1000.0) * (midTargetPaceSecPerKm ?: 330.0)

        val midTargetPaceSecPerKm: Double?
            get() = when {
                targetPaceMinSecPerKm != null && targetPaceMaxSecPerKm != null ->
                    (targetPaceMinSecPerKm + targetPaceMaxSecPerKm) / 2.0
                targetPaceMaxSecPerKm != null -> targetPaceMaxSecPerKm.toDouble()
                targetPaceMinSecPerKm != null -> targetPaceMinSecPerKm.toDouble()
                else -> null
            }
    }

    private data class IndexedSample(
        val sample: ActivitySample,
        val dtSec: Double,
        val cumulativeMeters: Double
    )

    private data class MeasuredSlice(
        val distanceKm: Double,
        val durationSec: Double,
        val avgPaceSecPerKm: Double?,
        val avgHrBpm: Double?,
        val elevationGainM: Double?,
        val samplePaces: List<Pair<Double, Double>>, // (paceSecPerKm, dtSec)
        val source: String
    )

    fun analyze(
        session: PlannedSession,
        activity: ActivityRecord,
        detail: ActivityDetail? = null,
        athleteHrZones: ExecutionHrZoneProfile? = null,
        analyzedAtEpochMs: Long = 0L
    ): WorkoutExecutionAnalysis {
        val plannedKm = session.distanceKm?.takeIf { it.isFinite() && it > 0.0 }
        val plannedMinutes = session.minutes.takeIf { it > 0 }
        val actualKm = activity.distanceM?.takeIf { it.isFinite() && it > 0.0 }?.div(1000.0)
        val actualDurationSec = (activity.movingTimeS ?: activity.durationS)
            ?.takeIf { it.isFinite() && it > 0.0 }
        val actualMinutes = actualDurationSec?.div(60.0)

        val validSamples = detail?.samples
            ?.filter { it.seconds.isFinite() && it.seconds >= 0.0 }
            .orEmpty()
        val validLaps = detail?.laps
            ?.filter { (it.distance ?: 0.0) > 0.0 || (it.seconds ?: 0.0) > 0.0 }
            .orEmpty()

        val evidenceTier = when {
            detail != null && validLaps.size >= 2 && validSamples.size >= 10 ->
                ExecutionEvidenceTier.LAP_AND_RECORD
            detail != null && validSamples.size >= 10 ->
                ExecutionEvidenceTier.RECORD_WINDOW
            else -> ExecutionEvidenceTier.SUMMARY_ONLY
        }

        val steps = extractPlannedSteps(session)
        val volumeRatio = computeVolumeRatio(plannedKm, actualKm, plannedMinutes, actualMinutes)

        if ((actualKm == null || actualKm <= 0.0) && (actualMinutes == null || actualMinutes <= 0.0)) {
            return WorkoutExecutionAnalysis(
                plannedSessionId = session.id,
                activityId = activity.id,
                evidenceTier = ExecutionEvidenceTier.SUMMARY_ONLY,
                verdict = ExecutionVerdict.INSUFFICIENT_TELEMETRY,
                volumeRatio = 0.0,
                plannedKm = plannedKm,
                actualKm = actualKm,
                plannedMinutes = plannedMinutes,
                actualMinutes = actualMinutes,
                workStepPaceAdherencePct = null,
                qualityStimulusVerified = if (session.quality) false else null,
                paceBias = ExecutionPaceBias.UNKNOWN,
                hrZoneCompliancePct = null,
                aerobicDecouplingPct = null,
                stepBreakdowns = emptyList(),
                reasonCodes = listOf("MISSING_DISTANCE_AND_DURATION"),
                coachingSummary = buildInsufficientSummary(session),
                analyzedAtEpochMs = analyzedAtEpochMs
            )
        }

        val timeline = buildTimeline(validSamples, activity.distanceM)
        val targetedSteps = steps.filter { it.isTargetedWork }
        val isLongFastFinish = !session.quality &&
            session.runningPrescription?.role in setOf("LONG", "ENDURANCE") &&
            targetedSteps.isNotEmpty()
        val hasHillWork = targetedSteps.any { it.stepIntent == RunningStepIntent.HILL }
        val hasStrideWork = targetedSteps.any { it.stepIntent == RunningStepIntent.STRIDE }
        val hasQualityPaceWork = targetedSteps.any {
            it.stepIntent in QUALITY_PACE_INTENTS || (it.purpose == "WORK" && !hasHillWork && !hasStrideWork)
        }

        val stepMeasurements = measurePlannedSteps(
            steps = steps,
            evidenceTier = evidenceTier,
            laps = validLaps,
            workoutSteps = detail?.workoutSteps.orEmpty(),
            timeline = timeline,
            activity = activity,
            isLongFastFinish = isLongFastFinish
        )

        val stepBreakdowns = steps.mapIndexed { idx, step ->
            val slice = stepMeasurements[idx]
            buildStepBreakdown(step, slice)
        }

        val reasons = LinkedHashSet<String>()
        reasons += "EVIDENCE_${evidenceTier.name}"

        val effectiveAvgPace = overallPace(activity, timeline, actualKm, actualDurationSec)
        val hrCompliance = computeHrCompliancePct(steps, timeline, athleteHrZones)
        val decouplingPct = computeAerobicDecouplingPct(
            session = session,
            timeline = timeline,
            profile = athleteHrZones,
            actualDurationSec = actualDurationSec
        )

        // Grade execution strictly according to coaching intent.
        val grading = when {
            isLongFastFinish -> gradeLongFastFinish(
                session = session,
                steps = steps,
                targetedSteps = targetedSteps,
                breakdowns = stepBreakdowns,
                volumeRatio = volumeRatio,
                reasons = reasons
            )
            hasHillWork -> gradeHillSession(
                targetedSteps = targetedSteps,
                breakdowns = stepBreakdowns,
                timeline = timeline,
                activity = activity,
                volumeRatio = volumeRatio,
                reasons = reasons
            )
            hasStrideWork -> gradeStrideSession(
                steps = steps,
                targetedSteps = targetedSteps,
                breakdowns = stepBreakdowns,
                laps = validLaps,
                timeline = timeline,
                volumeRatio = volumeRatio,
                reasons = reasons
            )
            session.quality || hasQualityPaceWork -> gradeQualityPaceSession(
                targetedSteps = targetedSteps,
                breakdowns = stepBreakdowns,
                volumeRatio = volumeRatio,
                evidenceTier = evidenceTier,
                effectiveAvgPace = effectiveAvgPace,
                reasons = reasons
            )
            else -> gradeEasyOrSteadySession(
                steps = steps,
                breakdowns = stepBreakdowns,
                volumeRatio = volumeRatio,
                effectiveAvgPace = effectiveAvgPace,
                reasons = reasons
            )
        }

        val summary = buildCoachingSummary(
            session = session,
            verdict = grading.verdict,
            plannedKm = plannedKm,
            actualKm = actualKm,
            plannedMinutes = plannedMinutes,
            actualMinutes = actualMinutes,
            targetPaceMin = grading.referenceTargetMinPace,
            targetPaceMax = grading.referenceTargetMaxPace,
            actualWorkPace = grading.observedPaceSecPerKm ?: effectiveAvgPace,
            elevationGainM = grading.elevationGainM,
            decouplingPct = decouplingPct
        )

        return WorkoutExecutionAnalysis(
            plannedSessionId = session.id,
            activityId = activity.id,
            evidenceTier = evidenceTier,
            verdict = grading.verdict,
            volumeRatio = round2(volumeRatio),
            plannedKm = plannedKm?.let(::round2),
            actualKm = actualKm?.let(::round2),
            plannedMinutes = plannedMinutes,
            actualMinutes = actualMinutes?.let(::round1),
            workStepPaceAdherencePct = grading.workAdherencePct?.let(::round1),
            qualityStimulusVerified = grading.qualityStimulusVerified,
            paceBias = grading.paceBias,
            hrZoneCompliancePct = hrCompliance?.let(::round1),
            aerobicDecouplingPct = decouplingPct?.let(::round1),
            stepBreakdowns = stepBreakdowns,
            reasonCodes = reasons.toList(),
            coachingSummary = summary,
            analyzedAtEpochMs = analyzedAtEpochMs
        )
    }

    private data class GradingOutcome(
        val verdict: ExecutionVerdict,
        val paceBias: ExecutionPaceBias,
        val workAdherencePct: Double?,
        val qualityStimulusVerified: Boolean?,
        val referenceTargetMinPace: Int?,
        val referenceTargetMaxPace: Int?,
        val observedPaceSecPerKm: Double?,
        val elevationGainM: Double? = null
    )

    private fun gradeEasyOrSteadySession(
        steps: List<PlannedStepTarget>,
        breakdowns: List<ExecutionStepBreakdown>,
        volumeRatio: Double,
        effectiveAvgPace: Double?,
        reasons: MutableSet<String>
    ): GradingOutcome {
        val primaryStep = steps.firstOrNull {
            it.stepIntent in setOf(
                RunningStepIntent.AEROBIC_EASY,
                RunningStepIntent.RECOVERY,
                RunningStepIntent.STEADY
            )
        } ?: steps.firstOrNull()
        val minPace = primaryStep?.targetPaceMinSecPerKm
        val maxPace = primaryStep?.targetPaceMaxSecPerKm
        val observedPace = breakdowns.firstOrNull()?.actualPaceSecPerKm ?: effectiveAvgPace
        val adherence = if (minPace != null || maxPace != null) {
            paceAdherence(observedPace, emptyList(), minPace, maxPace)
        } else null

        if (volumeRatio < 0.75) {
            reasons += "EASY_VOLUME_CUT_SHORT"
            return GradingOutcome(
                verdict = ExecutionVerdict.PARTIALLY_EXECUTED,
                paceBias = classifyPaceBias(observedPace, minPace, maxPace, 5.0, 12.0),
                workAdherencePct = adherence,
                qualityStimulusVerified = null,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = observedPace
            )
        }

        if (observedPace == null) {
            reasons += "MISSING_PACE_TELEMETRY"
            return GradingOutcome(
                verdict = ExecutionVerdict.INSUFFICIENT_TELEMETRY,
                paceBias = ExecutionPaceBias.UNKNOWN,
                workAdherencePct = null,
                qualityStimulusVerified = null,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = null
            )
        }

        if (minPace != null && observedPace < minPace - 5.0) {
            // Never praise running too fast on easy/recovery days ("gray-zone creep").
            reasons += "EASY_DAY_TOO_FAST"
            return GradingOutcome(
                verdict = ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST,
                paceBias = ExecutionPaceBias.TOO_FAST,
                workAdherencePct = adherence,
                qualityStimulusVerified = null,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = observedPace
            )
        }

        if (maxPace != null && observedPace > maxPace + 25.0) {
            reasons += "EASY_PACE_MISMATCH_TOO_SLOW"
            return GradingOutcome(
                verdict = ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW,
                paceBias = ExecutionPaceBias.TOO_SLOW,
                workAdherencePct = adherence,
                qualityStimulusVerified = null,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = observedPace
            )
        }

        reasons += "EASY_INTENSITY_AND_VOLUME_ON_TARGET"
        return GradingOutcome(
            verdict = ExecutionVerdict.ON_TARGET,
            paceBias = ExecutionPaceBias.ON_TARGET,
            workAdherencePct = adherence,
            qualityStimulusVerified = null,
            referenceTargetMinPace = minPace,
            referenceTargetMaxPace = maxPace,
            observedPaceSecPerKm = observedPace
        )
    }

    private fun gradeQualityPaceSession(
        targetedSteps: List<PlannedStepTarget>,
        breakdowns: List<ExecutionStepBreakdown>,
        volumeRatio: Double,
        evidenceTier: ExecutionEvidenceTier,
        effectiveAvgPace: Double?,
        reasons: MutableSet<String>
    ): GradingOutcome {
        val workBreakdowns = targetedSteps.mapNotNull { target ->
            breakdowns.find { it.stepIndex == target.stepIndex }
        }
        val minPace = targetedSteps.mapNotNull { it.targetPaceMinSecPerKm }.minOrNull()
        val maxPace = targetedSteps.mapNotNull { it.targetPaceMaxSecPerKm }.maxOrNull()

        val plannedWorkMeters = targetedSteps.sumOf { it.plannedMeters ?: 0 }
        val actualWorkMeters = workBreakdowns.sumOf { ((it.actualDistanceKm ?: 0.0) * 1000.0).roundToInt() }
        val plannedWorkSeconds = targetedSteps.sumOf { it.nominalSeconds }
        val actualWorkSeconds = workBreakdowns.sumOf { (it.actualSeconds ?: 0).toDouble() }
        val workVolumeRatio = when {
            plannedWorkMeters > 0 && actualWorkMeters > 0 ->
                actualWorkMeters.toDouble() / plannedWorkMeters
            plannedWorkSeconds > 0.0 && actualWorkSeconds > 0.0 ->
                actualWorkSeconds / plannedWorkSeconds
            else -> volumeRatio
        }

        val observedWorkPace = weightedBreakdownPace(workBreakdowns) ?: effectiveAvgPace
        val workAdherence = weightedBreakdownAdherence(workBreakdowns)
            ?: paceAdherence(observedWorkPace, emptyList(), minPace, maxPace)

        if (volumeRatio < 0.70 || (evidenceTier != ExecutionEvidenceTier.SUMMARY_ONLY && workVolumeRatio < 0.65)) {
            reasons += if (volumeRatio < 0.70) "TOTAL_VOLUME_CUT_SHORT" else "WORK_VOLUME_CUT_SHORT"
            return GradingOutcome(
                verdict = ExecutionVerdict.PARTIALLY_EXECUTED,
                paceBias = classifyPaceBias(observedWorkPace, minPace, maxPace, 6.0, 8.0),
                workAdherencePct = workAdherence,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = observedWorkPace
            )
        }

        if (observedWorkPace == null) {
            reasons += "MISSING_WORK_PACE_TELEMETRY"
            return GradingOutcome(
                verdict = ExecutionVerdict.INSUFFICIENT_TELEMETRY,
                paceBias = ExecutionPaceBias.UNKNOWN,
                workAdherencePct = null,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = null
            )
        }

        if (volumeRatio >= 0.80 && maxPace != null && observedWorkPace > maxPace + 15.0) {
            // Total distance was completed, but work segments stayed in easy/aerobic pace.
            reasons += "WORK_STAYED_IN_EASY_ZONE"
            return GradingOutcome(
                verdict = ExecutionVerdict.VOLUME_ONLY_MATCH,
                paceBias = ExecutionPaceBias.TOO_SLOW,
                workAdherencePct = workAdherence,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = observedWorkPace
            )
        }

        if (minPace != null && observedWorkPace < minPace - 12.0) {
            reasons += "WORK_PACE_TOO_FAST"
            return GradingOutcome(
                verdict = ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST,
                paceBias = ExecutionPaceBias.TOO_FAST,
                workAdherencePct = workAdherence,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = observedWorkPace
            )
        }

        if (maxPace != null && observedWorkPace > maxPace + 8.0) {
            reasons += "WORK_PACE_MODERATELY_TOO_SLOW"
            return GradingOutcome(
                verdict = ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW,
                paceBias = ExecutionPaceBias.TOO_SLOW,
                workAdherencePct = workAdherence,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = observedWorkPace
            )
        }

        val paceInWindow = (minPace == null || observedWorkPace >= minPace - 6.0) &&
            (maxPace == null || observedWorkPace <= maxPace + 8.0)
        val adherenceMet = (workAdherence ?: 0.0) >= 70.0 || paceInWindow
        if (adherenceMet && workVolumeRatio >= 0.80) {
            if (evidenceTier == ExecutionEvidenceTier.SUMMARY_ONLY) {
                reasons += "SUMMARY_ONLY_PROVISIONAL_QUALITY"
            } else {
                reasons += "QUALITY_WORK_VERIFIED"
            }
            return GradingOutcome(
                verdict = ExecutionVerdict.ON_TARGET,
                paceBias = ExecutionPaceBias.ON_TARGET,
                workAdherencePct = workAdherence,
                qualityStimulusVerified = evidenceTier != ExecutionEvidenceTier.SUMMARY_ONLY,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = observedWorkPace
            )
        }

        reasons += "WORK_INTENSITY_MIXED"
        return GradingOutcome(
            verdict = ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW,
            paceBias = classifyPaceBias(observedWorkPace, minPace, maxPace, 6.0, 8.0),
            workAdherencePct = workAdherence,
            qualityStimulusVerified = false,
            referenceTargetMinPace = minPace,
            referenceTargetMaxPace = maxPace,
            observedPaceSecPerKm = observedWorkPace
        )
    }

    private fun gradeLongFastFinish(
        session: PlannedSession,
        steps: List<PlannedStepTarget>,
        targetedSteps: List<PlannedStepTarget>,
        breakdowns: List<ExecutionStepBreakdown>,
        volumeRatio: Double,
        reasons: MutableSet<String>
    ): GradingOutcome {
        val finishBreakdowns = targetedSteps.mapNotNull { t -> breakdowns.find { it.stepIndex == t.stepIndex } }
        val minPace = targetedSteps.mapNotNull { it.targetPaceMinSecPerKm }.minOrNull()
        val maxPace = targetedSteps.mapNotNull { it.targetPaceMaxSecPerKm }.maxOrNull()
        val finishPace = weightedBreakdownPace(finishBreakdowns)
        val finishAdherence = weightedBreakdownAdherence(finishBreakdowns)
            ?: paceAdherence(finishPace, emptyList(), minPace, maxPace)

        if (volumeRatio < 0.75) {
            reasons += "LONG_RUN_VOLUME_CUT_SHORT"
            return GradingOutcome(
                verdict = ExecutionVerdict.PARTIALLY_EXECUTED,
                paceBias = classifyPaceBias(finishPace, minPace, maxPace, 6.0, 8.0),
                workAdherencePct = finishAdherence,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = finishPace
            )
        }

        if (finishPace == null) {
            reasons += "MISSING_FAST_FINISH_TELEMETRY"
            return GradingOutcome(
                verdict = ExecutionVerdict.INSUFFICIENT_TELEMETRY,
                paceBias = ExecutionPaceBias.UNKNOWN,
                workAdherencePct = null,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = null
            )
        }

        if (maxPace != null && finishPace > maxPace + 15.0) {
            reasons += "FAST_FINISH_STAYED_EASY"
            return GradingOutcome(
                verdict = ExecutionVerdict.VOLUME_ONLY_MATCH,
                paceBias = ExecutionPaceBias.TOO_SLOW,
                workAdherencePct = finishAdherence,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = finishPace
            )
        }

        if (minPace != null && finishPace < minPace - 12.0) {
            reasons += "FAST_FINISH_TOO_FAST"
            return GradingOutcome(
                verdict = ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST,
                paceBias = ExecutionPaceBias.TOO_FAST,
                workAdherencePct = finishAdherence,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = finishPace
            )
        }

        val paceInWindow = (minPace == null || finishPace >= minPace - 6.0) &&
            (maxPace == null || finishPace <= maxPace + 8.0)
        if (((finishAdherence ?: 0.0) >= 70.0 || paceInWindow) && volumeRatio >= 0.85) {
            reasons += "FAST_FINISH_VERIFIED"
            return GradingOutcome(
                verdict = ExecutionVerdict.ON_TARGET,
                paceBias = ExecutionPaceBias.ON_TARGET,
                workAdherencePct = finishAdherence,
                qualityStimulusVerified = true,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = finishPace
            )
        }

        reasons += "FAST_FINISH_MODERATELY_SLOW"
        return GradingOutcome(
            verdict = ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW,
            paceBias = ExecutionPaceBias.TOO_SLOW,
            workAdherencePct = finishAdherence,
            qualityStimulusVerified = false,
            referenceTargetMinPace = minPace,
            referenceTargetMaxPace = maxPace,
            observedPaceSecPerKm = finishPace
        )
    }

    private fun gradeHillSession(
        targetedSteps: List<PlannedStepTarget>,
        breakdowns: List<ExecutionStepBreakdown>,
        timeline: List<IndexedSample>,
        activity: ActivityRecord,
        volumeRatio: Double,
        reasons: MutableSet<String>
    ): GradingOutcome {
        val workBreakdowns = targetedSteps.mapNotNull { t -> breakdowns.find { it.stepIndex == t.stepIndex } }
        val workPace = weightedBreakdownPace(workBreakdowns)
        val totalAltitudeGain = computeAltitudeGain(timeline) ?: activity.elevationM?.takeIf { it.isFinite() && it >= 0.0 }
        val workAltitudeGain = workBreakdowns.mapNotNull { it.elevationGainM }.sum()
            .takeIf { it > 0.0 } ?: totalAltitudeGain

        if (volumeRatio < 0.70) {
            reasons += "HILL_VOLUME_CUT_SHORT"
            return GradingOutcome(
                verdict = ExecutionVerdict.PARTIALLY_EXECUTED,
                paceBias = ExecutionPaceBias.UNKNOWN,
                workAdherencePct = null,
                qualityStimulusVerified = false,
                referenceTargetMinPace = null,
                referenceTargetMaxPace = null,
                observedPaceSecPerKm = workPace,
                elevationGainM = totalAltitudeGain
            )
        }

        val gainConfirmed = (workAltitudeGain ?: 0.0) >= 15.0 || (activity.elevationM ?: 0.0) >= 20.0
        val continuityConfirmed = workBreakdowns.sumOf { it.actualSeconds ?: 0 } >=
            (targetedSteps.sumOf { it.nominalSeconds } * 0.65) ||
            workBreakdowns.size >= max(1, (targetedSteps.size * 0.65).roundToInt())

        if (gainConfirmed && continuityConfirmed) {
            reasons += "HILL_ELEVATION_AND_CONTINUITY_VERIFIED"
            reasons += "FLAT_PACE_PENALTY_WAIVED_FOR_HILLS"
            return GradingOutcome(
                verdict = ExecutionVerdict.ON_TARGET,
                paceBias = ExecutionPaceBias.ON_TARGET,
                workAdherencePct = 90.0,
                qualityStimulusVerified = true,
                referenceTargetMinPace = null,
                referenceTargetMaxPace = null,
                observedPaceSecPerKm = workPace,
                elevationGainM = totalAltitudeGain
            )
        }

        reasons += "HILL_ELEVATION_NOT_OBSERVED"
        return GradingOutcome(
            verdict = ExecutionVerdict.VOLUME_ONLY_MATCH,
            paceBias = ExecutionPaceBias.TOO_SLOW,
            workAdherencePct = 40.0,
            qualityStimulusVerified = false,
            referenceTargetMinPace = null,
            referenceTargetMaxPace = null,
            observedPaceSecPerKm = workPace,
            elevationGainM = totalAltitudeGain
        )
    }

    private fun gradeStrideSession(
        steps: List<PlannedStepTarget>,
        targetedSteps: List<PlannedStepTarget>,
        breakdowns: List<ExecutionStepBreakdown>,
        laps: List<ActivityLap>,
        timeline: List<IndexedSample>,
        volumeRatio: Double,
        reasons: MutableSet<String>
    ): GradingOutcome {
        val workBreakdowns = targetedSteps.mapNotNull { t -> breakdowns.find { it.stepIndex == t.stepIndex } }
        val minPace = targetedSteps.mapNotNull { it.targetPaceMinSecPerKm }.minOrNull()
        val maxPace = targetedSteps.mapNotNull { it.targetPaceMaxSecPerKm }.maxOrNull()
        val easyBreakdowns = steps.filterNot { it.isTargetedWork }
            .mapNotNull { t -> breakdowns.find { it.stepIndex == t.stepIndex } }
        val easyPace = weightedBreakdownPace(easyBreakdowns)
        val stridePace = weightedBreakdownPace(workBreakdowns)

        if (volumeRatio < 0.75) {
            reasons += "STRIDE_BASE_VOLUME_CUT_SHORT"
            return GradingOutcome(
                verdict = ExecutionVerdict.PARTIALLY_EXECUTED,
                paceBias = ExecutionPaceBias.UNKNOWN,
                workAdherencePct = null,
                qualityStimulusVerified = false,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = stridePace
            )
        }

        // Strides tolerate GPS lag: verify either distinct surge laps OR fastest short surges noticeably
        // quicker than the surrounding easy running (~20+ s/km faster or in fast band).
        val validSamplePaces = timeline.mapNotNull { it.sample.pace?.takeIf { p -> p in 120.0..1200.0 } }.sorted()
        val fastTailPace = if (validSamplePaces.isNotEmpty()) {
            val topIdx = max(0, (validSamplePaces.size * 0.08).toInt())
            validSamplePaces[topIdx]
        } else null
        val referenceEasyPace = easyPace ?: validSamplePaces.getOrNull(validSamplePaces.size / 2)
        val surgeDetected = laps.size >= targetedSteps.size + 1 ||
            (fastTailPace != null && referenceEasyPace != null && fastTailPace <= referenceEasyPace - 20.0) ||
            (stridePace != null && referenceEasyPace != null && stridePace <= referenceEasyPace - 15.0)

        if (volumeRatio >= 0.80 && (surgeDetected || laps.size >= 2)) {
            reasons += "STRIDES_SURGE_EFFORT_VERIFIED"
            reasons += "GPS_LAG_TOLERATED_FOR_SHORT_STRIDES"
            return GradingOutcome(
                verdict = ExecutionVerdict.ON_TARGET,
                paceBias = ExecutionPaceBias.ON_TARGET,
                workAdherencePct = 90.0,
                qualityStimulusVerified = true,
                referenceTargetMinPace = minPace,
                referenceTargetMaxPace = maxPace,
                observedPaceSecPerKm = stridePace ?: fastTailPace
            )
        }

        reasons += "STRIDE_SURGES_NOT_DETECTED"
        return GradingOutcome(
            verdict = ExecutionVerdict.VOLUME_ONLY_MATCH,
            paceBias = ExecutionPaceBias.TOO_SLOW,
            workAdherencePct = 35.0,
            qualityStimulusVerified = false,
            referenceTargetMinPace = minPace,
            referenceTargetMaxPace = maxPace,
            observedPaceSecPerKm = stridePace ?: fastTailPace
        )
    }

    private fun extractPlannedSteps(session: PlannedSession): List<PlannedStepTarget> {
        val rx = session.runningPrescription
        if (rx != null) {
            if (rx.mixedSegments != null) {
                return rx.mixedSegments.mapIndexed { idx, step ->
                    PlannedStepTarget(
                        stepIndex = idx,
                        purpose = step.purpose,
                        stepIntent = step.stepIntent,
                        plannedMeters = step.meters ?: step.distanceEquivalentMeters,
                        plannedSeconds = step.seconds,
                        targetPaceMinSecPerKm = step.targetPaceMinSecPerKm,
                        targetPaceMaxSecPerKm = step.targetPaceMaxSecPerKm,
                        targetHrMinBpm = step.targetHrMinBpm,
                        targetHrMaxBpm = step.targetHrMaxBpm
                    )
                }
            }
            if (rx.segments.isNotEmpty()) {
                return rx.segments.mapIndexed { idx, seg ->
                    PlannedStepTarget(
                        stepIndex = idx,
                        purpose = seg.purpose,
                        stepIntent = seg.stepIntent,
                        plannedMeters = seg.meters,
                        plannedSeconds = null,
                        targetPaceMinSecPerKm = seg.targetPaceMinSecPerKm,
                        targetPaceMaxSecPerKm = seg.targetPaceMaxSecPerKm,
                        targetHrMinBpm = seg.targetHrMinBpm,
                        targetHrMaxBpm = seg.targetHrMaxBpm
                    )
                }
            }
        }
        val intent = when {
            session.kind == "recovery" -> RunningStepIntent.RECOVERY
            session.quality -> RunningStepIntent.TEMPO
            else -> RunningStepIntent.AEROBIC_EASY
        }
        return listOf(
            PlannedStepTarget(
                stepIndex = 0,
                purpose = if (session.quality) "WORK" else "EASY",
                stepIntent = intent,
                plannedMeters = session.distanceKm?.let { (it * 1000.0).roundToInt() },
                plannedSeconds = session.minutes.takeIf { it > 0 }?.times(60),
                targetPaceMinSecPerKm = null,
                targetPaceMaxSecPerKm = null,
                targetHrMinBpm = null,
                targetHrMaxBpm = null
            )
        )
    }

    private fun buildTimeline(
        samples: List<ActivitySample>,
        activityDistanceM: Double?
    ): List<IndexedSample> {
        if (samples.size < 2) return emptyList()
        val rawDistances = DoubleArray(samples.size)
        var totalRaw = 0.0
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val cur = samples[i]
            val dt = (cur.seconds - prev.seconds).coerceIn(0.0, 30.0)
            val pace = prev.pace?.takeIf { it.isFinite() && it in 120.0..1200.0 }
                ?: cur.pace?.takeIf { it.isFinite() && it in 120.0..1200.0 }
            if (pace != null && dt > 0.0) {
                totalRaw += dt * (1000.0 / pace)
            }
            rawDistances[i] = totalRaw
        }
        val scale = if (totalRaw > 0.0 && activityDistanceM != null && activityDistanceM > 0.0) {
            activityDistanceM / totalRaw
        } else 1.0

        return samples.mapIndexed { i, sample ->
            val nextSec = if (i + 1 < samples.size) {
                (samples[i + 1].seconds - sample.seconds).coerceIn(0.0, 30.0)
            } else {
                (sample.seconds - samples[i - 1].seconds).coerceIn(0.0, 30.0)
            }
            IndexedSample(
                sample = sample,
                dtSec = nextSec,
                cumulativeMeters = rawDistances[i] * scale
            )
        }
    }

    private fun measurePlannedSteps(
        steps: List<PlannedStepTarget>,
        evidenceTier: ExecutionEvidenceTier,
        laps: List<ActivityLap>,
        workoutSteps: List<ActivityWorkoutStep>,
        timeline: List<IndexedSample>,
        activity: ActivityRecord,
        isLongFastFinish: Boolean
    ): List<MeasuredSlice> {
        if (evidenceTier == ExecutionEvidenceTier.LAP_AND_RECORD) {
            val stepLapsMap = mapLapsToSteps(steps, laps, workoutSteps)
            if (stepLapsMap != null) {
                return steps.indices.map { idx ->
                    sliceFromLaps(stepLapsMap[idx].orEmpty(), timeline)
                }
            }
            // Auto-laps (e.g., every 1 km) don't always align 1:1 with multi-step intervals;
            // fall through to window extraction across laps/records while keeping LAP_AND_RECORD tier.
            return measureStepsFromWindows(
                steps = steps,
                laps = laps,
                timeline = timeline,
                activity = activity,
                isLongFastFinish = isLongFastFinish,
                sourceTag = "LAP_AND_RECORD"
            )
        }
        if (evidenceTier == ExecutionEvidenceTier.RECORD_WINDOW) {
            return measureStepsFromWindows(
                steps = steps,
                laps = emptyList(),
                timeline = timeline,
                activity = activity,
                isLongFastFinish = isLongFastFinish,
                sourceTag = "RECORD_WINDOW"
            )
        }
        // SUMMARY_ONLY fallback
        val totalKm = activity.distanceM?.div(1000.0) ?: 0.0
        val totalSec = (activity.movingTimeS ?: activity.durationS) ?: 0.0
        val summaryPace = overallPace(activity, timeline, totalKm.takeIf { it > 0.0 }, totalSec.takeIf { it > 0.0 })
        val totalPlannedWeight = steps.sumOf { max(1.0, it.nominalSeconds) }.coerceAtLeast(1.0)
        return steps.map { step ->
            val share = max(1.0, step.nominalSeconds) / totalPlannedWeight
            MeasuredSlice(
                distanceKm = totalKm * share,
                durationSec = totalSec * share,
                avgPaceSecPerKm = summaryPace,
                avgHrBpm = activity.averageHr,
                elevationGainM = activity.elevationM?.times(share),
                samplePaces = emptyList(),
                source = "SUMMARY"
            )
        }
    }

    private fun mapLapsToSteps(
        steps: List<PlannedStepTarget>,
        laps: List<ActivityLap>,
        workoutSteps: List<ActivityWorkoutStep>
    ): Map<Int, List<ActivityLap>>? {
        if (laps.size == steps.size) {
            return steps.indices.associateWith { listOf(laps[it]) }
        }
        if (laps.any { it.workoutStepIndex != null } && workoutSteps.isNotEmpty()) {
            val stepMetaByIndex = workoutSteps.associateBy { it.index }
            val result = steps.indices.associateWith { mutableListOf<ActivityLap>() }.toMutableMap()
            val workTargetIndices = steps.filter { it.isTargetedWork }.map { it.stepIndex }
            var workCursor = 0
            for (lap in laps) {
                val role = lap.workoutStepIndex?.let { stepMetaByIndex[it]?.role }
                val mappedIdx = when (role) {
                    "WARM_UP" -> steps.firstOrNull { it.purpose == "WARM_UP" }?.stepIndex
                    "COOL_DOWN" -> steps.lastOrNull { it.purpose == "COOL_DOWN" }?.stepIndex
                    "RECOVERY" -> steps.firstOrNull { it.purpose == "RECOVERY" }?.stepIndex
                    "WORK" -> {
                        val target = workTargetIndices.getOrNull(workCursor)
                            ?: workTargetIndices.lastOrNull()
                        if (workTargetIndices.isNotEmpty()) {
                            workCursor = min(workCursor + 1, workTargetIndices.lastIndex)
                        }
                        target
                    }
                    else -> null
                }
                if (mappedIdx != null) result[mappedIdx]?.add(lap)
            }
            if (result.values.any { it.isNotEmpty() }) return result
        }
        return null
    }

    private fun measureStepsFromWindows(
        steps: List<PlannedStepTarget>,
        laps: List<ActivityLap>,
        timeline: List<IndexedSample>,
        activity: ActivityRecord,
        isLongFastFinish: Boolean,
        sourceTag: String
    ): List<MeasuredSlice> {
        if (steps.size == 1) {
            val single = if (laps.isNotEmpty()) sliceFromLaps(laps, timeline)
            else sliceFromSampleRange(timeline, 0, timeline.lastIndex, sourceTag)
            return listOf(single)
        }

        val usedRanges = ArrayList<IntRange>()
        val slices = arrayOfNulls<MeasuredSlice>(steps.size)

        if (isLongFastFinish) {
            val finishIndices = steps.indices.filter { steps[it].isTargetedWork }
            val totalFinishMeters = finishIndices.sumOf { steps[it].plannedMeters ?: 0 }.toDouble()
            val totalFinishSec = finishIndices.sumOf { steps[it].nominalSeconds }
            val range = findTailWindow(timeline, totalFinishMeters, totalFinishSec)
            usedRanges += range
            val finishSlice = sliceFromSampleRange(timeline, range.first, range.last, sourceTag)
            finishIndices.forEach { slices[it] = finishSlice }
        } else {
            val targetedIndices = steps.indices.filter { steps[it].isTargetedWork }
            for (idx in targetedIndices) {
                val target = steps[idx]
                val bestRange = findBestWorkWindow(
                    timeline = timeline,
                    targetMeters = target.plannedMeters?.toDouble(),
                    targetSeconds = target.nominalSeconds,
                    excluded = usedRanges
                )
                if (bestRange != null) {
                    usedRanges += bestRange
                    slices[idx] = sliceFromSampleRange(timeline, bestRange.first, bestRange.last, sourceTag)
                }
            }
        }

        val remainingSamples = timeline.indices.filter { i -> usedRanges.none { i in it } }
        val nonWorkSlice = if (remainingSamples.isNotEmpty()) {
            sliceFromIndexSubset(timeline, remainingSamples, sourceTag)
        } else {
            sliceFromSampleRange(timeline, 0, timeline.lastIndex, sourceTag)
        }
        val nonWorkSteps = steps.count { !it.isTargetedWork }.coerceAtLeast(1)
        for (i in steps.indices) {
            if (slices[i] == null) {
                slices[i] = if (!steps[i].isTargetedWork) {
                    nonWorkSlice.copy(
                        distanceKm = nonWorkSlice.distanceKm / nonWorkSteps,
                        durationSec = nonWorkSlice.durationSec / nonWorkSteps
                    )
                } else {
                    sliceFromSampleRange(timeline, 0, timeline.lastIndex, sourceTag)
                }
            }
        }
        return slices.map { it!! }
    }

    private fun findTailWindow(
        timeline: List<IndexedSample>,
        targetMeters: Double,
        targetSeconds: Double
    ): IntRange {
        if (timeline.size < 4) return timeline.indices
        val totalMeters = timeline.last().cumulativeMeters
        val endIdx = timeline.lastIndex
        var startIdx = 0
        if (targetMeters > 0.0 && totalMeters > 0.0) {
            val startDist = (totalMeters - targetMeters).coerceAtLeast(0.0)
            startIdx = timeline.indexOfFirst { it.cumulativeMeters >= startDist }
                .takeIf { it >= 0 } ?: 0
        } else if (targetSeconds > 0.0) {
            val endSec = timeline.last().sample.seconds
            val startSec = (endSec - targetSeconds).coerceAtLeast(0.0)
            startIdx = timeline.indexOfFirst { it.sample.seconds >= startSec }
                .takeIf { it >= 0 } ?: 0
        }
        return startIdx.coerceAtMost(endIdx - 1)..endIdx
    }

    private fun findBestWorkWindow(
        timeline: List<IndexedSample>,
        targetMeters: Double?,
        targetSeconds: Double,
        excluded: List<IntRange>
    ): IntRange? {
        if (timeline.size < 4) return null
        val useMeters = targetMeters != null && targetMeters > 0.0 && timeline.last().cumulativeMeters > 0.0
        val minExtent = if (useMeters) targetMeters!! * 0.85 else targetSeconds * 0.85
        var bestRange: IntRange? = null
        var bestPace = Double.POSITIVE_INFINITY

        var right = 0
        for (left in timeline.indices) {
            if (excluded.any { left in it }) continue
            if (right < left) right = left
            while (right < timeline.lastIndex) {
                val next = right + 1
                if (excluded.any { next in it }) break
                val extent = if (useMeters) {
                    timeline[right].cumulativeMeters - timeline[left].cumulativeMeters
                } else {
                    timeline[right].sample.seconds - timeline[left].sample.seconds
                }
                if (extent >= minExtent) break
                right = next
            }
            if (excluded.any { idx -> (left..right).any { it in idx } }) continue
            val extent = if (useMeters) {
                timeline[right].cumulativeMeters - timeline[left].cumulativeMeters
            } else {
                timeline[right].sample.seconds - timeline[left].sample.seconds
            }
            if (extent < minExtent * 0.75) continue
            val pace = weightedPace(timeline, left..right) ?: continue
            if (pace < bestPace) {
                bestPace = pace
                bestRange = left..right
            }
        }
        return bestRange
    }

    private fun weightedPace(timeline: List<IndexedSample>, indices: Iterable<Int>): Double? {
        var weightedSum = 0.0
        var totalWeight = 0.0
        for (i in indices) {
            val item = timeline[i]
            val p = item.sample.pace?.takeIf { it.isFinite() && it in 120.0..1200.0 } ?: continue
            val w = item.dtSec.coerceAtLeast(1.0)
            weightedSum += p * w
            totalWeight += w
        }
        return if (totalWeight > 0.0) weightedSum / totalWeight else null
    }

    private fun sliceFromLaps(laps: List<ActivityLap>, timeline: List<IndexedSample>): MeasuredSlice {
        val distM = laps.sumOf { it.distance ?: 0.0 }
        val durS = laps.sumOf { it.seconds ?: 0.0 }
        val lapAvgPace = if (distM > 0.0 && durS > 0.0) {
            1000.0 * durS / distM
        } else {
            val paces = laps.mapNotNull { lap ->
                val p = lap.pace?.takeIf { it.isFinite() && it in 120.0..1200.0 } ?: return@mapNotNull null
                val w = (lap.seconds ?: 1.0).coerceAtLeast(1.0)
                p to w
            }
            if (paces.isNotEmpty()) {
                paces.sumOf { it.first * it.second } / paces.sumOf { it.second }
            } else null
        }

        val matchingSamples = if (timeline.isNotEmpty() && laps.any { it.startSeconds != null && it.seconds != null }) {
            val intervals = laps.mapNotNull { lap ->
                val s = lap.startSeconds ?: return@mapNotNull null
                val d = lap.seconds ?: return@mapNotNull null
                s..(s + d)
            }
            timeline.filter { item -> intervals.any { item.sample.seconds in it } }
        } else emptyList()

        val hrs = laps.mapNotNull { lap ->
            val hr = lap.hr?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            val w = (lap.seconds ?: 1.0).coerceAtLeast(1.0)
            hr to w
        }
        val avgHr = if (hrs.isNotEmpty()) {
            hrs.sumOf { it.first * it.second } / hrs.sumOf { it.second }
        } else null
        val elevGain = computeAltitudeGain(matchingSamples)
        val samplePaces = matchingSamples.mapNotNull { item ->
            item.sample.pace?.takeIf { it in 120.0..1200.0 }?.let { it to item.dtSec }
        }
        return MeasuredSlice(
            distanceKm = distM / 1000.0,
            durationSec = durS,
            avgPaceSecPerKm = lapAvgPace,
            avgHrBpm = avgHr,
            elevationGainM = elevGain,
            samplePaces = samplePaces,
            source = "LAP_AND_RECORD"
        )
    }

    private fun sliceFromSampleRange(
        timeline: List<IndexedSample>,
        startIdx: Int,
        endIdx: Int,
        sourceTag: String
    ): MeasuredSlice = sliceFromIndexSubset(timeline, (startIdx..endIdx).toList(), sourceTag)

    private fun sliceFromIndexSubset(
        timeline: List<IndexedSample>,
        indices: List<Int>,
        sourceTag: String
    ): MeasuredSlice {
        if (indices.isEmpty()) {
            return MeasuredSlice(0.0, 0.0, null, null, null, emptyList(), sourceTag)
        }
        val distM = max(0.0, timeline[indices.last()].cumulativeMeters - timeline[indices.first()].cumulativeMeters)
        val durSec = indices.sumOf { timeline[it].dtSec }
        val pacePairs = indices.mapNotNull { idx ->
            val item = timeline[idx]
            item.sample.pace?.takeIf { it.isFinite() && it in 120.0..1200.0 }?.let { it to item.dtSec }
        }
        val avgPace = if (pacePairs.isNotEmpty()) {
            val wSum = pacePairs.sumOf { it.second }.coerceAtLeast(1.0)
            pacePairs.sumOf { it.first * it.second } / wSum
        } else if (distM > 0.0 && durSec > 0.0) {
            1000.0 * durSec / distM
        } else null

        val hrPairs = indices.mapNotNull { idx ->
            val item = timeline[idx]
            item.sample.hr?.takeIf { it.isFinite() && it > 0.0 }?.let { it to item.dtSec }
        }
        val avgHr = if (hrPairs.isNotEmpty()) {
            hrPairs.sumOf { it.first * it.second } / hrPairs.sumOf { it.second }.coerceAtLeast(1.0)
        } else null
        val elevGain = computeAltitudeGain(indices.map { timeline[it] })
        return MeasuredSlice(
            distanceKm = distM / 1000.0,
            durationSec = durSec,
            avgPaceSecPerKm = avgPace,
            avgHrBpm = avgHr,
            elevationGainM = elevGain,
            samplePaces = pacePairs,
            source = sourceTag
        )
    }

    private fun buildStepBreakdown(
        step: PlannedStepTarget,
        slice: MeasuredSlice
    ): ExecutionStepBreakdown {
        val pace = slice.avgPaceSecPerKm
        val adherence = if (step.targetPaceMinSecPerKm != null || step.targetPaceMaxSecPerKm != null) {
            paceAdherence(pace, slice.samplePaces, step.targetPaceMinSecPerKm, step.targetPaceMaxSecPerKm)
        } else null
        return ExecutionStepBreakdown(
            stepIndex = step.stepIndex,
            purpose = step.purpose,
            stepIntent = step.stepIntent,
            plannedDistanceKm = step.plannedMeters?.div(1000.0)?.let(::round2),
            actualDistanceKm = round2(slice.distanceKm),
            plannedSeconds = step.plannedSeconds,
            actualSeconds = slice.durationSec.roundToInt(),
            targetPaceMinSecPerKm = step.targetPaceMinSecPerKm,
            targetPaceMaxSecPerKm = step.targetPaceMaxSecPerKm,
            actualPaceSecPerKm = pace?.let(::round1),
            paceAdherencePct = adherence?.let(::round1),
            paceBias = classifyPaceBias(pace, step.targetPaceMinSecPerKm, step.targetPaceMaxSecPerKm, 6.0, 8.0),
            averageHrBpm = slice.avgHrBpm?.let(::round1),
            elevationGainM = slice.elevationGainM?.let(::round1),
            source = slice.source
        )
    }

    private fun paceAdherence(
        avgPace: Double?,
        samplePaces: List<Pair<Double, Double>>,
        minPace: Int?,
        maxPace: Int?
    ): Double? {
        if (minPace == null && maxPace == null) return null
        val low = (minPace?.toDouble() ?: 120.0) - 8.0
        val high = (maxPace?.toDouble() ?: 1200.0) + 10.0

        if (samplePaces.isNotEmpty()) {
            val totalWeight = samplePaces.sumOf { it.second }.coerceAtLeast(1.0)
            val inBandWeight = samplePaces.filter { (p, _) -> p in low..high }.sumOf { it.second }
            val samplePct = (inBandWeight / totalWeight) * 100.0
            if (avgPace != null && avgPace in low..high) {
                return max(samplePct, 85.0).coerceIn(0.0, 100.0)
            }
            return samplePct.coerceIn(0.0, 100.0)
        }

        val pace = avgPace ?: return null
        val delta = when {
            pace < low -> low - pace
            pace > high -> pace - high
            else -> 0.0
        }
        return (100.0 - delta * 3.0).coerceIn(0.0, 100.0)
    }

    private fun classifyPaceBias(
        pace: Double?,
        minPace: Int?,
        maxPace: Int?,
        fastTolSec: Double,
        slowTolSec: Double
    ): ExecutionPaceBias {
        if (pace == null || (minPace == null && maxPace == null)) return ExecutionPaceBias.UNKNOWN
        if (minPace != null && pace < minPace - fastTolSec) return ExecutionPaceBias.TOO_FAST
        if (maxPace != null && pace > maxPace + slowTolSec) return ExecutionPaceBias.TOO_SLOW
        return ExecutionPaceBias.ON_TARGET
    }

    private fun weightedBreakdownPace(breakdowns: List<ExecutionStepBreakdown>): Double? {
        val valid = breakdowns.mapNotNull { b ->
            val p = b.actualPaceSecPerKm ?: return@mapNotNull null
            val w = (b.actualSeconds ?: 1).toDouble().coerceAtLeast(1.0)
            p to w
        }
        if (valid.isEmpty()) return null
        return valid.sumOf { it.first * it.second } / valid.sumOf { it.second }
    }

    private fun weightedBreakdownAdherence(breakdowns: List<ExecutionStepBreakdown>): Double? {
        val valid = breakdowns.mapNotNull { b ->
            val a = b.paceAdherencePct ?: return@mapNotNull null
            val w = (b.actualSeconds ?: 1).toDouble().coerceAtLeast(1.0)
            a to w
        }
        if (valid.isEmpty()) return null
        return valid.sumOf { it.first * it.second } / valid.sumOf { it.second }
    }

    private fun computeAltitudeGain(samples: List<IndexedSample>): Double? {
        val alts = samples.mapNotNull { it.sample.altitude?.takeIf { a -> a.isFinite() } }
        if (alts.size < 2) return null
        var gain = 0.0
        for (i in 1 until alts.size) {
            val delta = alts[i] - alts[i - 1]
            if (delta in 0.2..40.0) gain += delta
        }
        return round1(gain)
    }

    private fun overallPace(
        activity: ActivityRecord,
        timeline: List<IndexedSample>,
        actualKm: Double?,
        actualDurationSec: Double?
    ): Double? {
        activity.recordedPace?.takeIf { it.isFinite() && it in 120.0..1200.0 }?.let { return it }
        activity.garminMovingPaceSPerKm?.takeIf { it.isFinite() && it in 120.0..1200.0 }?.let { return it }
        if (timeline.isNotEmpty()) {
            weightedPace(timeline, timeline.indices)?.let { return it }
        }
        if (actualKm != null && actualKm > 0.0 && actualDurationSec != null && actualDurationSec > 0.0) {
            return (actualDurationSec / actualKm).takeIf { it in 120.0..1200.0 }
        }
        return null
    }

    private fun computeVolumeRatio(
        plannedKm: Double?,
        actualKm: Double?,
        plannedMinutes: Int?,
        actualMinutes: Double?
    ): Double {
        if (plannedKm != null && plannedKm > 0.0 && actualKm != null && actualKm > 0.0) {
            return actualKm / plannedKm
        }
        if (plannedMinutes != null && plannedMinutes > 0 && actualMinutes != null && actualMinutes > 0.0) {
            return actualMinutes / plannedMinutes.toDouble()
        }
        return 0.0
    }

    private fun computeHrCompliancePct(
        steps: List<PlannedStepTarget>,
        timeline: List<IndexedSample>,
        profile: ExecutionHrZoneProfile?
    ): Double? {
        if (profile == null || timeline.size < 10) return null
        val hrSamples = timeline.filter { it.sample.hr != null && it.sample.hr.isFinite() && it.sample.hr > 0.0 }
        if (hrSamples.size < 10) return null

        val primaryStep = steps.firstOrNull { it.isTargetedWork } ?: steps.firstOrNull()
        val (minBpm, maxBpm) = resolveTargetHrBounds(primaryStep, profile) ?: return null
        val totalWeight = hrSamples.sumOf { it.dtSec.coerceAtLeast(1.0) }.coerceAtLeast(1.0)
        val inZoneWeight = hrSamples
            .filter { (it.sample.hr ?: 0.0) in (minBpm - 3).toDouble()..(maxBpm + 3).toDouble() }
            .sumOf { it.dtSec.coerceAtLeast(1.0) }
        return ((inZoneWeight / totalWeight) * 100.0).coerceIn(0.0, 100.0)
    }

    private fun resolveTargetHrBounds(
        step: PlannedStepTarget?,
        profile: ExecutionHrZoneProfile
    ): Pair<Int, Int>? {
        if (step?.targetHrMinBpm != null && step.targetHrMaxBpm != null) {
            return step.targetHrMinBpm to step.targetHrMaxBpm
        }
        val intent = step?.stepIntent ?: return null
        return when (intent) {
            RunningStepIntent.RECOVERY -> profile.z1FloorBpm to (profile.z2FloorBpm - 1)
            RunningStepIntent.WARMUP,
            RunningStepIntent.COOLDOWN,
            RunningStepIntent.AEROBIC_EASY -> profile.z1FloorBpm to (profile.z3FloorBpm - 1)
            RunningStepIntent.STEADY -> profile.z2FloorBpm to (profile.z4FloorBpm - 1)
            RunningStepIntent.TEMPO,
            RunningStepIntent.RACE_PACE -> profile.z3FloorBpm to (profile.z5FloorBpm - 1)
            RunningStepIntent.THRESHOLD -> profile.z4FloorBpm to (profile.z5FloorBpm - 1)
            RunningStepIntent.VO2_INTERVAL,
            RunningStepIntent.HILL,
            RunningStepIntent.STRIDE -> profile.z4FloorBpm to (profile.maxHrBpm ?: 240)
        }
    }

    private fun computeAerobicDecouplingPct(
        session: PlannedSession,
        timeline: List<IndexedSample>,
        profile: ExecutionHrZoneProfile?,
        actualDurationSec: Double?
    ): Double? {
        if (profile == null || session.quality || timeline.size < 20) return null
        if ((actualDurationSec ?: 0.0) < 1800.0 && (session.minutes < 30)) return null
        val paired = timeline.filter {
            val p = it.sample.pace
            val hr = it.sample.hr
            p != null && p.isFinite() && p in 120.0..1200.0 &&
                hr != null && hr.isFinite() && hr > 40.0
        }
        if (paired.size < 20) return null
        val startT = paired.first().sample.seconds
        val endT = paired.last().sample.seconds
        if (endT - startT < 1200.0) return null
        val midT = startT + (endT - startT) / 2.0

        val firstHalf = paired.filter { it.sample.seconds <= midT }
        val secondHalf = paired.filter { it.sample.seconds > midT }
        val ef1 = efficiencyFactor(firstHalf) ?: return null
        val ef2 = efficiencyFactor(secondHalf) ?: return null
        if (ef1 <= 0.0) return null
        return ((ef1 - ef2) / ef1) * 100.0
    }

    private fun efficiencyFactor(samples: List<IndexedSample>): Double? {
        if (samples.size < 5) return null
        val totalW = samples.sumOf { it.dtSec.coerceAtLeast(1.0) }.coerceAtLeast(1.0)
        val avgSpeedMps = samples.sumOf {
            (1000.0 / it.sample.pace!!) * it.dtSec.coerceAtLeast(1.0)
        } / totalW
        val avgHr = samples.sumOf {
            it.sample.hr!! * it.dtSec.coerceAtLeast(1.0)
        } / totalW
        return if (avgHr > 0.0) avgSpeedMps / avgHr else null
    }

    private fun buildInsufficientSummary(session: PlannedSession): String =
        "${session.title} lacks distance and duration telemetry for execution grading."

    private fun buildCoachingSummary(
        session: PlannedSession,
        verdict: ExecutionVerdict,
        plannedKm: Double?,
        actualKm: Double?,
        plannedMinutes: Int?,
        actualMinutes: Double?,
        targetPaceMin: Int?,
        targetPaceMax: Int?,
        actualWorkPace: Double?,
        elevationGainM: Double?,
        decouplingPct: Double?
    ): String {
        val volumeText = when {
            plannedKm != null && actualKm != null ->
                String.format(Locale.US, "%.1f/%.1f km", actualKm, plannedKm)
            plannedMinutes != null && actualMinutes != null ->
                String.format(Locale.US, "%.0f/%d min", actualMinutes, plannedMinutes)
            actualKm != null -> String.format(Locale.US, "%.1f km", actualKm)
            else -> "recorded distance"
        }
        val actualPaceText = actualWorkPace?.let(::formatPace) ?: "unrecorded pace"
        val targetBandText = when {
            targetPaceMin != null && targetPaceMax != null ->
                "${formatPace(targetPaceMin.toDouble())}–${formatPace(targetPaceMax.toDouble())}"
            targetPaceMax != null -> formatPace(targetPaceMax.toDouble())
            targetPaceMin != null -> formatPace(targetPaceMin.toDouble())
            else -> null
        }

        val sentence1 = when (verdict) {
            ExecutionVerdict.ON_TARGET ->
                if (elevationGainM != null && session.runningPrescription?.family == "Hills") {
                    "${session.title} hit target ($volumeText, ${elevationGainM.roundToInt()} m climb)."
                } else if (targetBandText != null) {
                    "${session.title} hit target at $actualPaceText (goal $targetBandText) over $volumeText."
                } else {
                    "${session.title} hit target over $volumeText."
                }
            ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST ->
                if (targetBandText != null) {
                    "${session.title} ran too fast at $actualPaceText vs. $targetBandText over $volumeText."
                } else {
                    "${session.title} ran faster than prescribed effort over $volumeText."
                }
            ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW ->
                if (targetBandText != null) {
                    "${session.title} was slower than goal ($actualPaceText vs. $targetBandText) over $volumeText."
                } else {
                    "${session.title} missed target pace over $volumeText."
                }
            ExecutionVerdict.VOLUME_ONLY_MATCH ->
                if (targetBandText != null) {
                    "${session.title} covered $volumeText, but work stayed easy at $actualPaceText (goal $targetBandText)."
                } else {
                    "${session.title} covered $volumeText without hitting the prescribed work stimulus."
                }
            ExecutionVerdict.PARTIALLY_EXECUTED ->
                "${session.title} finished short at $volumeText ($actualPaceText)."
            ExecutionVerdict.INSUFFICIENT_TELEMETRY ->
                buildInsufficientSummary(session)
        }

        val sentence2 = when {
            verdict == ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST && !session.quality ->
                "Keep aerobic days controlled so key sessions stay sharp."
            verdict == ExecutionVerdict.VOLUME_ONLY_MATCH ->
                "Credit the aerobic volume, while noting the target quality stimulus did not register."
            decouplingPct != null ->
                String.format(Locale.US, "Aerobic decoupling (Pa:Hr) was %.1f%%.", decouplingPct)
            else -> null
        }
        return if (sentence2 != null) "$sentence1 $sentence2" else sentence1
    }

    private fun formatPace(secondsPerKm: Double): String {
        val total = secondsPerKm.roundToInt().coerceAtLeast(1)
        val min = total / 60
        val sec = total % 60
        return String.format(Locale.US, "%d:%02d/km", min, sec)
    }

    private fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0
}
