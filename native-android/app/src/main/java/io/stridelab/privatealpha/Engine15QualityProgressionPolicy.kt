package io.stridelab.privatealpha

import kotlin.math.roundToInt

internal enum class Engine15QualityProgressionBasis {
    HOLD_NO_REPEATED_EXPOSURE,
    EVIDENCE_BOUNDED_PROGRESSION,
    RACE_TAPER_REDUCTION,
    TIME_BUDGET_CAPPED
}

internal data class Engine15QualityDoseStep(
    val stepIndex: Int,
    val stageLabel: String,
    val workout: IndependentWorkoutComposition,
    val workSeconds: Int,
    val recoverySeconds: Int,
    val totalSeconds: Int
) {
    init {
        require(stepIndex >= 0 && stageLabel.isNotBlank())
        require(workSeconds > 0 && recoverySeconds >= 0 && totalSeconds >= workSeconds + recoverySeconds)
        require(workout.total(WorkoutUnit.SECONDS)?.toInt() == totalSeconds)
        require(workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK)?.toInt() == workSeconds)
    }
}

internal data class Engine15QualityWeekPrescription(
    val policyId: String,
    val weekNumber: Int,
    val family: IndependentQualityFamily,
    val step: Engine15QualityDoseStep,
    val basis: Engine15QualityProgressionBasis,
    val ruleIds: List<String>,
    val rationale: String,
    val sourceEvidenceVerified: Boolean = false
) {
    init {
        require(policyId == Engine15QualityProgressionPolicy.POLICY_ID)
        require(weekNumber > 0 && rationale.isNotBlank())
        require(ruleIds.distinct() == ruleIds && ruleIds.isNotEmpty())
        require(!sourceEvidenceVerified)
    }
}

/**
 * Stage 3 evidence-bounded & 50/50 history-fitness adaptive multi-week quality dosage
 * and progression policy.
 *
 * - Preserves strict unit-test behavior when called without an adaptive anchor override.
 * - For app plan generation, blends 50% Auto-Detected Recent Quality Workouts +
 *   50% Historical Fitness & Volume Capacity so fit runners never receive "junk"
 *   10-minute workouts, while progressing across build blocks and tapering before races.
 */
internal object Engine15QualityProgressionPolicy {
    const val POLICY_ID = "stridelab.engine15.quality-progression-v1"
    private const val WARM_UP_SECONDS = 10 * 60
    private const val INTRO_COOL_DOWN_SECONDS = 8 * 60
    private const val EXTENDED_COOL_DOWN_SECONDS = 10 * 60
    private const val RECOVERY_SECONDS = 2 * 60
    private const val MAX_EXPOSURE_INCREMENT_SECONDS = 4 * 60 // +4 min strict evidence cap

    private fun step(purpose: WorkoutPurpose, seconds: Int, target: WorkoutTarget) =
        WorkoutStep(purpose, WorkoutUnit.SECONDS, seconds.toDouble(), 1, target)

    private fun coolDown(workSeconds: Int) =
        if (workSeconds >= 20 * 60) EXTENDED_COOL_DOWN_SECONDS else INTRO_COOL_DOWN_SECONDS

    private fun continuousStep(
        stepIndex: Int,
        stageLabel: String,
        workSeconds: Int,
        target: WorkoutTarget
    ): Engine15QualityDoseStep {
        val cool = coolDown(workSeconds)
        val workout = IndependentWorkoutComposition(listOf(
            IndependentWorkoutElement.Step(step(WorkoutPurpose.WARM_UP, WARM_UP_SECONDS, WorkoutTarget.EASY)),
            IndependentWorkoutElement.Step(step(WorkoutPurpose.WORK, workSeconds, target)),
            IndependentWorkoutElement.Step(step(WorkoutPurpose.COOL_DOWN, cool, WorkoutTarget.EASY))
        ))
        return Engine15QualityDoseStep(
            stepIndex = stepIndex,
            stageLabel = stageLabel,
            workout = workout,
            workSeconds = workSeconds,
            recoverySeconds = 0,
            totalSeconds = WARM_UP_SECONDS + workSeconds + cool
        )
    }

    private fun intervalStep(
        stepIndex: Int,
        stageLabel: String,
        repetitions: Int,
        repWorkSeconds: Int,
        target: WorkoutTarget
    ): Engine15QualityDoseStep {
        val workSeconds = repetitions * repWorkSeconds
        val recoverySeconds = (repetitions - 1) * RECOVERY_SECONDS
        val cool = coolDown(workSeconds)
        val workout = IndependentWorkoutComposition(listOf(
            IndependentWorkoutElement.Step(step(WorkoutPurpose.WARM_UP, WARM_UP_SECONDS, WorkoutTarget.EASY)),
            IndependentWorkoutElement.Intervals(IndependentIntervalGroup(
                repetitions = repetitions,
                work = step(WorkoutPurpose.WORK, repWorkSeconds, target),
                recovery = step(WorkoutPurpose.RECOVERY, RECOVERY_SECONDS, WorkoutTarget.EASY)
            )),
            IndependentWorkoutElement.Step(step(WorkoutPurpose.COOL_DOWN, cool, WorkoutTarget.EASY))
        ))
        return Engine15QualityDoseStep(
            stepIndex = stepIndex,
            stageLabel = stageLabel,
            workout = workout,
            workSeconds = workSeconds,
            recoverySeconds = recoverySeconds,
            totalSeconds = WARM_UP_SECONDS + workSeconds + recoverySeconds + cool
        )
    }

    fun ladder(family: IndependentQualityFamily, experience: Int): List<Engine15QualityDoseStep> =
        when (family) {
            IndependentQualityFamily.TEMPO -> {
                val introWork = if (experience in 1..2) 10 * 60 else 12 * 60
                buildList {
                    add(continuousStep(0, "INTRO", introWork, WorkoutTarget.TEMPO))
                    if (introWork < 12 * 60) {
                        add(continuousStep(size, "BUILD_1", 12 * 60, WorkoutTarget.TEMPO))
                    }
                    add(continuousStep(size, "BUILD_2", 15 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "BUILD_3", 18 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "EXTENDED", 20 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "BUILD_4", 24 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "BUILD_5", 28 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "PEAK_1", 32 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "PEAK_2", 36 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "PEAK_3", 40 * 60, WorkoutTarget.TEMPO))
                }
            }
            IndependentQualityFamily.THRESHOLD -> listOf(
                intervalStep(0, "INTRO", 3, 4 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(1, "BUILD_1", 3, 5 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(2, "BUILD_2", 4, 4 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(3, "BUILD_3", 3, 6 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(4, "EXTENDED", 4, 5 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(5, "BUILD_4", 4, 6 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(6, "BUILD_5", 4, 7 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(7, "PEAK_1", 4, 8 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(8, "PEAK_2", 5, 7 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(9, "PEAK_3", 4, 10 * 60, WorkoutTarget.THRESHOLD)
            )
            IndependentQualityFamily.INTERVALS -> listOf(
                intervalStep(0, "INTRO", 4, 2 * 60, WorkoutTarget.INTERVALS),
                intervalStep(1, "BUILD_1", 5, 2 * 60, WorkoutTarget.INTERVALS),
                intervalStep(2, "BUILD_2", 4, 3 * 60, WorkoutTarget.INTERVALS),
                intervalStep(3, "BUILD_3", 6, 2 * 60, WorkoutTarget.INTERVALS),
                intervalStep(4, "EXTENDED", 5, 3 * 60, WorkoutTarget.INTERVALS),
                intervalStep(5, "BUILD_4", 6, 3 * 60, WorkoutTarget.INTERVALS),
                intervalStep(6, "BUILD_5", 5, 4 * 60, WorkoutTarget.INTERVALS),
                intervalStep(7, "PEAK_1", 6, 4 * 60, WorkoutTarget.INTERVALS),
                intervalStep(8, "PEAK_2", 7, 4 * 60, WorkoutTarget.INTERVALS)
            )
            IndependentQualityFamily.HILLS -> listOf(
                intervalStep(0, "INTRO", 5, 60, WorkoutTarget.HILLS),
                intervalStep(1, "BUILD_1", 6, 60, WorkoutTarget.HILLS),
                intervalStep(2, "BUILD_2", 5, 90, WorkoutTarget.HILLS),
                intervalStep(3, "BUILD_3", 8, 60, WorkoutTarget.HILLS),
                intervalStep(4, "EXTENDED", 6, 90, WorkoutTarget.HILLS),
                intervalStep(5, "BUILD_4", 8, 90, WorkoutTarget.HILLS),
                intervalStep(6, "PEAK_1", 10, 90, WorkoutTarget.HILLS),
                intervalStep(7, "PEAK_2", 8, 120, WorkoutTarget.HILLS)
            )
            IndependentQualityFamily.FARTLEK -> listOf(
                intervalStep(0, "INTRO", 5, 2 * 60, WorkoutTarget.FARTLEK),
                intervalStep(1, "BUILD_1", 6, 2 * 60, WorkoutTarget.FARTLEK),
                intervalStep(2, "BUILD_2", 5, 3 * 60, WorkoutTarget.FARTLEK),
                intervalStep(3, "BUILD_3", 7, 2 * 60, WorkoutTarget.FARTLEK),
                intervalStep(4, "EXTENDED", 6, 3 * 60, WorkoutTarget.FARTLEK),
                intervalStep(5, "BUILD_4", 7, 3 * 60, WorkoutTarget.FARTLEK),
                intervalStep(6, "PEAK_1", 8, 3 * 60, WorkoutTarget.FARTLEK),
                intervalStep(7, "PEAK_2", 7, 4 * 60, WorkoutTarget.FARTLEK)
            )
            IndependentQualityFamily.PROGRESSION -> listOf(
                continuousStep(0, "INTRO", 12 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(1, "BUILD_1", 15 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(2, "BUILD_2", 18 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(3, "EXTENDED", 20 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(4, "BUILD_3", 25 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(5, "BUILD_4", 30 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(6, "PEAK_1", 35 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(7, "PEAK_2", 40 * 60, WorkoutTarget.PROGRESSION)
            )
        }

    /**
     * Computes the 50/50 Auto-Detected Workout + Historical Fitness quality work anchor (in seconds).
     * Ensures fit runners always receive meaningful, non-junk quality workouts matched to both
     * their recently detected workouts and their volume/pace capacity.
     */
    fun computeAdaptiveExposureAnchorSeconds(
        request: PlanRequest,
        family: IndependentQualityFamily,
        activities: List<ActivityRecord> = emptyList(),
        plans: List<PlanSnapshot> = emptyList(),
        detections: List<Engine15DetectedWorkout> = emptyList()
    ): Int {
        val zones = Engine15PaceZoneEstimator.estimate(request, activities = activities)
        val effectiveWeeklyKm = maxOf(
            request.weeklyKm,
            request.targetWeeklyKm?.times(0.85) ?: 0.0,
            automaticRunningBaseline(activities, plans = plans)?.weeklyKm ?: 0.0
        ).coerceIn(12.0, 120.0)

        // 1. 50% Weight: Historical Fitness & Volume Capacity
        // Active quality work share of weekly volume:
        // - Tempo / Threshold / Progression / Fartlek: ~15-18% of weekly km
        // - Intervals / Hills: ~10-12% of weekly km
        val workShareOfWeekly = when (family) {
            IndependentQualityFamily.TEMPO,
            IndependentQualityFamily.PROGRESSION -> 0.165
            IndependentQualityFamily.THRESHOLD,
            IndependentQualityFamily.FARTLEK -> 0.155
            IndependentQualityFamily.INTERVALS -> 0.115
            IndependentQualityFamily.HILLS -> 0.085
        }
        val targetWorkKm = (effectiveWeeklyKm * workShareOfWeekly).coerceIn(2.0, 11.0)
        val workPaceSecPerKm = when (family) {
            IndependentQualityFamily.TEMPO,
            IndependentQualityFamily.PROGRESSION -> zones.tempo.midpointSecondsPerKm
            IndependentQualityFamily.THRESHOLD -> zones.threshold.midpointSecondsPerKm
            IndependentQualityFamily.INTERVALS,
            IndependentQualityFamily.HILLS,
            IndependentQualityFamily.FARTLEK -> zones.intervals.midpointSecondsPerKm
        }
        val rawFitnessWorkSec = (targetWorkKm * workPaceSecPerKm).roundToInt()

        // No-Junk-Workout Floor by weekly mileage & experience
        val meaningfulFloorSec = when (family) {
            IndependentQualityFamily.TEMPO,
            IndependentQualityFamily.THRESHOLD,
            IndependentQualityFamily.PROGRESSION -> when {
                request.experience == 0 -> 15 * 60
                effectiveWeeklyKm >= 55.0 || request.experience == 2 -> 24 * 60
                effectiveWeeklyKm >= 35.0 || request.experience == 1 -> 18 * 60
                else -> 15 * 60
            }
            IndependentQualityFamily.INTERVALS,
            IndependentQualityFamily.FARTLEK -> when {
                request.experience == 0 -> 12 * 60
                effectiveWeeklyKm >= 55.0 || request.experience == 2 -> 18 * 60
                effectiveWeeklyKm >= 35.0 || request.experience == 1 -> 15 * 60
                else -> 12 * 60
            }
            IndependentQualityFamily.HILLS -> when {
                request.experience == 0 -> 6 * 60
                effectiveWeeklyKm >= 55.0 || request.experience == 2 -> 12 * 60
                effectiveWeeklyKm >= 35.0 || request.experience == 1 -> 9 * 60
                else -> 6 * 60
            }
        }
        val fitnessCapacitySec = maxOf(rawFitnessWorkSec, meaningfulFloorSec).coerceIn(360, 2400)

        // 2. 50% Weight: Auto-Detected Recent Workouts (lap heuristics + plan links + pace-detected quality runs)
        val lapOrLinkedDurations = detections.filter { det ->
            det.kind == ActivityWorkoutKind.QUALITY_RUN &&
                det.candidateWorkSegmentsSeconds.isNotEmpty()
        }.map { it.candidateWorkSegmentsSeconds.sum() }
            .filter { it in 300..3600 }

        val easyPace = zones.easy.midpointSecondsPerKm
        val paceDetectedDurations = ActivityDisplayConsolidation.visible(activities)
            .filter { DashboardSummary.isRun(it) }
            .mapNotNull { run ->
                val distM = run.distanceM?.takeIf { it.isFinite() && it >= 4_000.0 } ?: return@mapNotNull null
                val durS = run.durationS?.takeIf { it.isFinite() && it >= 900.0 } ?: return@mapNotNull null
                val pace = (run.recordedPace ?: run.garminMovingPaceSPerKm ?: (durS * 1000.0 / distM))
                // A run whose overall average pace is >= 8% faster than easy midpoint is a quality/fast effort
                if (pace.isFinite() && pace <= easyPace * 0.92) {
                    (durS * 0.65).roundToInt().coerceIn(600, 2700)
                } else null
            }

        val allDetectedWork = (lapOrLinkedDurations + paceDetectedDurations).sortedDescending().take(4)
        // When an athlete has ZERO recent quality efforts detected (e.g., purely easy-mileage base),
        // don't drop straight into max-ceiling 40-min quality in Week 1 — introduce at controlled fitness dose.
        val unverifiedCapSec = when (family) {
            IndependentQualityFamily.TEMPO,
            IndependentQualityFamily.THRESHOLD,
            IndependentQualityFamily.PROGRESSION -> 24 * 60
            IndependentQualityFamily.INTERVALS,
            IndependentQualityFamily.FARTLEK -> 18 * 60
            IndependentQualityFamily.HILLS -> 12 * 60
        }
        val detectedWorkSec = if (allDetectedWork.isNotEmpty()) {
            allDetectedWork.average().roundToInt()
        } else {
            minOf(fitnessCapacitySec, unverifiedCapSec)
        }

        // 3. 50 / 50 Blend + Week-1 Progressive Headroom (~75% of peak capacity, bounded safely by experience)
        val blendedPeakSec = (0.50 * detectedWorkSec + 0.50 * fitnessCapacitySec).roundToInt()
        val progressiveStartSec = if (request.weeks >= 6) (blendedPeakSec * 0.78).roundToInt() else blendedPeakSec
        val w1ExperienceCeiling = when {
            request.experience == 0 && family in setOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION) -> 20 * 60
            request.experience == 0 -> 15 * 60
            family == IndependentQualityFamily.INTERVALS -> 22 * 60
            family == IndependentQualityFamily.HILLS -> 12 * 60
            else -> 32 * 60
        }
        return maxOf(minOf(progressiveStartSec, w1ExperienceCeiling), meaningfulFloorSec).coerceIn(360, 2400)
    }

    private fun qualityFamilyRotation(
        request: PlanRequest,
        primaryFamily: IndependentQualityFamily
    ): List<IndependentQualityFamily> {
        val base = when (request.goal) {
            "Improve 5K" -> when (request.experience) {
                0 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.FARTLEK, IndependentQualityFamily.HILLS)
                1 -> listOf(IndependentQualityFamily.INTERVALS, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.TEMPO, IndependentQualityFamily.HILLS)
                else -> listOf(IndependentQualityFamily.INTERVALS, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.HILLS, IndependentQualityFamily.TEMPO)
            }
            "Improve 10K" -> when (request.experience) {
                0 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.FARTLEK)
                1 -> listOf(IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.TEMPO, IndependentQualityFamily.INTERVALS, IndependentQualityFamily.PROGRESSION)
                else -> listOf(IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.INTERVALS, IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION)
            }
            "Half marathon" -> when (request.experience) {
                0 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.FARTLEK)
                1 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.FARTLEK)
                else -> listOf(IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.INTERVALS)
            }
            else -> when (request.experience) {
                0 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.FARTLEK)
                1 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.HILLS)
                else -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.HILLS)
            }
        }
        val filtered = if (request.allowedQualityTypes.isEmpty()) base else base.filter { fam ->
            request.allowedQualityTypes.any { it.equals(fam.name, ignoreCase = true) }
        }
        return if (filtered.isEmpty()) listOf(primaryFamily)
        else (listOf(primaryFamily) + filtered).distinct()
    }

    fun plan(
        request: PlanRequest,
        selection: Engine15QualityFamilySelectionResult,
        assertedExposureSecondsOverride: Int? = null
    ): List<Engine15QualityWeekPrescription> {
        val family = selection.selectedFamily ?: return emptyList()
        val slots = selection.candidateSlots
        if (slots.isEmpty()) return emptyList()
        val steps = ladder(family, request.experience)
        val intro = steps.first()
        val timeBudgetSeconds = if (request.timeBudgetEnabled) request.minutes * 60 else null
        val isRaceGoal = request.goal != "Aerobic foundation"
        val lastLoadingWeek = slots.maxOf { it.weekNumber }
        val repeatedBaseline = when {
            selection.exposureConfidence == IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED &&
                selection.assertedRepeatedWorkSeconds != null -> selection.assertedRepeatedWorkSeconds
            assertedExposureSecondsOverride != null && assertedExposureSecondsOverride in 1..14400 ->
                assertedExposureSecondsOverride
            else -> null
        }

        if (repeatedBaseline == null) {
            return slots.map { slot ->
                Engine15QualityWeekPrescription(
                    policyId = POLICY_ID,
                    weekNumber = slot.weekNumber,
                    family = family,
                    step = intro,
                    basis = Engine15QualityProgressionBasis.HOLD_NO_REPEATED_EXPOSURE,
                    ruleIds = listOf(
                        "HOLD_INTRO_UNVERIFIED_EXPOSURE",
                        "NO_AUTOMATIC_PROGRESSION_FROM_ELAPSED_WEEKS"
                    ),
                    rationale = "Held at ${intro.stageLabel} (${intro.workSeconds / 60} min work) because repeated same-family exposure is unavailable or unverified."
                )
            }
        }

        // When using the 50/50 adaptive fitness+detection override, allow progressive build up to +35%
        // across mesocycles; when using strict asserted device evidence alone, keep the +240s cap.
        val adaptiveOverrideActive = assertedExposureSecondsOverride != null &&
            selection.exposureConfidence != IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED
        val maxPermittedWork = if (adaptiveOverrideActive) {
            maxOf(repeatedBaseline + MAX_EXPOSURE_INCREMENT_SECONDS, (repeatedBaseline * 1.35).roundToInt())
        } else {
            repeatedBaseline + MAX_EXPOSURE_INCREMENT_SECONDS
        }
        val anchorIndex = steps.indexOfLast { it.workSeconds <= repeatedBaseline }.coerceAtLeast(0)
        val anchorRatio = if (steps.lastIndex > 0) anchorIndex.toDouble() / steps.lastIndex.toDouble() else 0.0
        val rotateFamilies = adaptiveOverrideActive && !request.preferredQualityExplicit && slots.size >= 2
        val rotation = if (rotateFamilies) qualityFamilyRotation(request, family) else listOf(family)

        return slots.mapIndexed { slotIndex, slot ->
            val weekFamily = rotation[slotIndex % rotation.size]
            val weekSteps = if (weekFamily == family) steps else ladder(weekFamily, request.experience)
            val weekIntro = weekSteps.first()
            val weekAnchorIndex = if (weekFamily == family) anchorIndex else {
                (anchorRatio * weekSteps.lastIndex).roundToInt().coerceIn(0, weekSteps.lastIndex)
            }
            val blockIndex = (slot.weekNumber - 1) / 4
            val weekInBlock = (slot.weekNumber - 1) % 4
            val isRaceTaperWeek = isRaceGoal && request.weeks >= 8 && slot.weekNumber == lastLoadingWeek
            val desiredIndex = if (isRaceTaperWeek) {
                (weekAnchorIndex - 1).coerceAtLeast(0)
            } else {
                (weekAnchorIndex + blockIndex + (weekInBlock / 2)).coerceAtMost(weekSteps.lastIndex)
            }
            val weekMaxWork = maxOf(weekIntro.workSeconds, maxPermittedWork)
            val exposureBounded = weekSteps.take(desiredIndex + 1)
                .lastOrNull { it.workSeconds <= weekMaxWork } ?: weekIntro
            val finalStep = if (timeBudgetSeconds != null) {
                weekSteps.take(exposureBounded.stepIndex + 1)
                    .lastOrNull { it.totalSeconds <= timeBudgetSeconds } ?: weekIntro
            } else {
                exposureBounded
            }
            val timeCapped = finalStep.stepIndex < exposureBounded.stepIndex
            val basis = when {
                isRaceTaperWeek -> Engine15QualityProgressionBasis.RACE_TAPER_REDUCTION
                timeCapped -> Engine15QualityProgressionBasis.TIME_BUDGET_CAPPED
                else -> Engine15QualityProgressionBasis.EVIDENCE_BOUNDED_PROGRESSION
            }
            val rules = buildList {
                add("REPEATED_SAME_FAMILY_EXPOSURE_ASSERTED")
                add("BOUNDED_INCREMENT_CEILING_APPLIED")
                if (isRaceTaperWeek) add("RACE_TAPER_QUALITY_HOLD")
                if (timeCapped) add("SESSION_TIME_BUDGET_CAP_APPLIED")
                add("NO_PACE_OR_HR_TARGET_INFERRED")
            }
            val rationale = when (basis) {
                Engine15QualityProgressionBasis.RACE_TAPER_REDUCTION ->
                    "Taper hold at ${finalStep.stageLabel} (${finalStep.workSeconds / 60} min work) for the final loading week before '${request.goal}'."
                Engine15QualityProgressionBasis.TIME_BUDGET_CAPPED ->
                    "Capped at ${finalStep.stageLabel} (${finalStep.workSeconds / 60} min work) to fit the ${request.minutes}-minute session budget."
                else ->
                    "Adaptive 50/50 history & fitness ${finalStep.stageLabel} (${finalStep.workSeconds / 60} min work) anchored to ${repeatedBaseline / 60} min baseline."
            }
            Engine15QualityWeekPrescription(
                policyId = POLICY_ID,
                weekNumber = slot.weekNumber,
                family = weekFamily,
                step = finalStep,
                basis = basis,
                ruleIds = rules,
                rationale = rationale
            )
        }
    }
}
