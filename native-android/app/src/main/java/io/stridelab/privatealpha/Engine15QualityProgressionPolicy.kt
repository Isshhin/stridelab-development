package io.stridelab.privatealpha

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
 * Stage 3 evidence-bounded multi-week quality dosage and progression policy.
 *
 * - Without repeated same-family exposure, holds at the conservative introductory
 *   dose across all loading weeks; elapsed weeks alone never escalate quality work.
 * - With repeated same-family exposure, advances along a bounded, discrete family
 *   ladder capped by asserted repeated exposure + bounded allowance, family ceilings,
 *   available session time, and race-taper reductions on the final loading week.
 * - Never fabricates pace, heart-rate, or physiological thresholds.
 */
internal object Engine15QualityProgressionPolicy {
    const val POLICY_ID = "stridelab.engine15.quality-progression-v1"
    private const val WARM_UP_SECONDS = 10 * 60
    private const val INTRO_COOL_DOWN_SECONDS = 8 * 60
    private const val EXTENDED_COOL_DOWN_SECONDS = 10 * 60
    private const val RECOVERY_SECONDS = 2 * 60
    private const val MAX_EXPOSURE_INCREMENT_SECONDS = 4 * 60 // +4 min total cap above repeated baseline

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
                }
            }
            IndependentQualityFamily.THRESHOLD -> listOf(
                intervalStep(0, "INTRO", 3, 4 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(1, "BUILD_1", 3, 5 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(2, "BUILD_2", 4, 4 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(3, "BUILD_3", 3, 6 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(4, "EXTENDED", 4, 5 * 60, WorkoutTarget.THRESHOLD)
            )
            IndependentQualityFamily.INTERVALS -> listOf(
                intervalStep(0, "INTRO", 4, 2 * 60, WorkoutTarget.INTERVALS),
                intervalStep(1, "BUILD_1", 5, 2 * 60, WorkoutTarget.INTERVALS),
                intervalStep(2, "BUILD_2", 4, 3 * 60, WorkoutTarget.INTERVALS),
                intervalStep(3, "BUILD_3", 6, 2 * 60, WorkoutTarget.INTERVALS),
                intervalStep(4, "EXTENDED", 5, 3 * 60, WorkoutTarget.INTERVALS)
            )
            IndependentQualityFamily.HILLS -> listOf(
                intervalStep(0, "INTRO", 5, 60, WorkoutTarget.HILLS),
                intervalStep(1, "BUILD_1", 6, 60, WorkoutTarget.HILLS),
                intervalStep(2, "BUILD_2", 5, 90, WorkoutTarget.HILLS),
                intervalStep(3, "BUILD_3", 8, 60, WorkoutTarget.HILLS),
                intervalStep(4, "EXTENDED", 6, 90, WorkoutTarget.HILLS)
            )
            IndependentQualityFamily.FARTLEK -> listOf(
                intervalStep(0, "INTRO", 5, 2 * 60, WorkoutTarget.FARTLEK),
                intervalStep(1, "BUILD_1", 6, 2 * 60, WorkoutTarget.FARTLEK),
                intervalStep(2, "BUILD_2", 5, 3 * 60, WorkoutTarget.FARTLEK),
                intervalStep(3, "BUILD_3", 7, 2 * 60, WorkoutTarget.FARTLEK),
                intervalStep(4, "EXTENDED", 6, 3 * 60, WorkoutTarget.FARTLEK)
            )
            IndependentQualityFamily.PROGRESSION -> listOf(
                continuousStep(0, "INTRO", 12 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(1, "BUILD_1", 15 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(2, "BUILD_2", 18 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(3, "EXTENDED", 20 * 60, WorkoutTarget.PROGRESSION)
            )
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

        val maxPermittedWork = repeatedBaseline + MAX_EXPOSURE_INCREMENT_SECONDS
        val anchorIndex = steps.indexOfLast { it.workSeconds <= repeatedBaseline }.coerceAtLeast(0)

        return slots.map { slot ->
            val blockIndex = (slot.weekNumber - 1) / 4
            val weekInBlock = (slot.weekNumber - 1) % 4
            val isRaceTaperWeek = isRaceGoal && request.weeks >= 8 && slot.weekNumber == lastLoadingWeek
            val desiredIndex = if (isRaceTaperWeek) {
                (anchorIndex - 1).coerceAtLeast(0)
            } else {
                // Advance at most +1 step per mesocycle and +1 within a 3-week build block,
                // strictly bounded by asserted repeated exposure + MAX_EXPOSURE_INCREMENT_SECONDS.
                (anchorIndex + blockIndex + (weekInBlock / 2)).coerceAtMost(steps.lastIndex)
            }
            val exposureBounded = steps.take(desiredIndex + 1)
                .lastOrNull { it.workSeconds <= maxPermittedWork } ?: intro
            val finalStep = if (timeBudgetSeconds != null) {
                steps.take(exposureBounded.stepIndex + 1)
                    .lastOrNull { it.totalSeconds <= timeBudgetSeconds } ?: intro
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
                    "Evidence-bounded ${finalStep.stageLabel} (${finalStep.workSeconds / 60} min work) anchored to asserted repeated exposure (${repeatedBaseline / 60} min)."
            }
            Engine15QualityWeekPrescription(
                policyId = POLICY_ID,
                weekNumber = slot.weekNumber,
                family = family,
                step = finalStep,
                basis = basis,
                ruleIds = rules,
                rationale = rationale
            )
        }
    }
}
