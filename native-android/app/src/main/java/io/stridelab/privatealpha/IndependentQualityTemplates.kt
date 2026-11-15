package io.stridelab.privatealpha

/**
 * M2 research-only quality-session examples built from the versioned workout contract.
 * A template defines structure and an illustrative dose, NOT athlete eligibility,
 * measured threshold pace, physiological safety, or a progression decision.
 */
internal enum class IndependentQualityFamily { THRESHOLD, TEMPO }
internal enum class IndependentQualityStage { INTRO, EXTENDED }

internal data class IndependentQualityTemplate(
    val id: String,
    val family: IndependentQualityFamily,
    val stage: IndependentQualityStage,
    val workout: IndependentWorkoutComposition,
    val evidence: String = "HEURISTIC"
) {
    init {
        require(id == "engine15-${family.name.lowercase()}-${stage.name.lowercase()}-v1")
        require(evidence == "HEURISTIC")
        // This first library deliberately limits examples to one warm-up, one
        // quality block and one cool-down; more complex structures can be added
        // as separately versioned candidates.
        require(workout.elements.size == 3)
        fun easyStep(element: IndependentWorkoutElement, purpose: WorkoutPurpose): Boolean =
            (element as? IndependentWorkoutElement.Step)?.value?.let { step ->
                step.purpose == purpose && step.unit == WorkoutUnit.SECONDS &&
                    step.target == WorkoutTarget.EASY && step.repetitions == 1
            } == true
        require(easyStep(workout.elements.first(), WorkoutPurpose.WARM_UP))
        require(easyStep(workout.elements.last(), WorkoutPurpose.COOL_DOWN))
        when (family) {
            IndependentQualityFamily.THRESHOLD -> {
                val group = (workout.elements[1] as? IndependentWorkoutElement.Intervals)?.value
                    ?: throw IllegalArgumentException("Threshold example requires interval group")
                require(group.work.unit == WorkoutUnit.SECONDS &&
                    group.work.target == WorkoutTarget.THRESHOLD &&
                    group.recovery.unit == WorkoutUnit.SECONDS &&
                    group.recovery.target == WorkoutTarget.EASY)
            }
            IndependentQualityFamily.TEMPO -> {
                val step = (workout.elements[1] as? IndependentWorkoutElement.Step)?.value
                    ?: throw IllegalArgumentException("Tempo example requires continuous work")
                require(step.purpose == WorkoutPurpose.WORK &&
                    step.unit == WorkoutUnit.SECONDS &&
                    step.target == WorkoutTarget.TEMPO && step.repetitions == 1)
            }
        }
        require(workout.total(WorkoutUnit.SECONDS) != null)
    }

    fun totalSeconds(): Double = requireNotNull(workout.total(WorkoutUnit.SECONDS))
    fun workSeconds(): Double = requireNotNull(workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK))
    fun recoverySeconds(): Double = workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.RECOVERY) ?: 0.0
}

/** Fixed examples only; no auto-selection by fitness, history, goals or feedback. */
internal object IndependentQualityTemplates {
    private fun step(purpose: WorkoutPurpose, seconds: Int, target: WorkoutTarget) =
        WorkoutStep(purpose, WorkoutUnit.SECONDS, seconds.toDouble(), 1, target)

    private fun threshold(stage: IndependentQualityStage, repetitions: Int, workSeconds: Int,
                          recoverySeconds: Int, warmSeconds: Int, coolSeconds: Int) =
        IndependentQualityTemplate(
            "engine15-threshold-${stage.name.lowercase()}-v1", IndependentQualityFamily.THRESHOLD, stage,
            IndependentWorkoutComposition(listOf(
                IndependentWorkoutElement.Step(step(WorkoutPurpose.WARM_UP, warmSeconds, WorkoutTarget.EASY)),
                IndependentWorkoutElement.Intervals(IndependentIntervalGroup(
                    repetitions,
                    step(WorkoutPurpose.WORK, workSeconds, WorkoutTarget.THRESHOLD),
                    step(WorkoutPurpose.RECOVERY, recoverySeconds, WorkoutTarget.EASY)
                )),
                IndependentWorkoutElement.Step(step(WorkoutPurpose.COOL_DOWN, coolSeconds, WorkoutTarget.EASY))
            ))
        )

    private fun tempo(stage: IndependentQualityStage, workSeconds: Int,
                      warmSeconds: Int, coolSeconds: Int) =
        IndependentQualityTemplate(
            "engine15-tempo-${stage.name.lowercase()}-v1", IndependentQualityFamily.TEMPO, stage,
            IndependentWorkoutComposition(listOf(
                IndependentWorkoutElement.Step(step(WorkoutPurpose.WARM_UP, warmSeconds, WorkoutTarget.EASY)),
                IndependentWorkoutElement.Step(step(WorkoutPurpose.WORK, workSeconds, WorkoutTarget.TEMPO)),
                IndependentWorkoutElement.Step(step(WorkoutPurpose.COOL_DOWN, coolSeconds, WorkoutTarget.EASY))
            ))
        )

    // Intro doses match the existing controlled Engine 1.5 text examples;
    // extended doses are synthetic research alternatives, not auto-progression.
    fun all(): List<IndependentQualityTemplate> = listOf(
        threshold(IndependentQualityStage.INTRO, 3, 4 * 60, 2 * 60, 10 * 60, 8 * 60),
        threshold(IndependentQualityStage.EXTENDED, 4, 5 * 60, 2 * 60, 10 * 60, 10 * 60),
        tempo(IndependentQualityStage.INTRO, 12 * 60, 10 * 60, 8 * 60),
        tempo(IndependentQualityStage.EXTENDED, 20 * 60, 10 * 60, 10 * 60)
    )

    fun get(family: IndependentQualityFamily, stage: IndependentQualityStage): IndependentQualityTemplate =
        all().single { it.family == family && it.stage == stage }
}
