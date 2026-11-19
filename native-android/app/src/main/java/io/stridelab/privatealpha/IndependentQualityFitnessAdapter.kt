package io.stridelab.privatealpha

/**
 * M2 candidate adapter: explicit evidence and dose envelopes shape quality-session
 * structure. It does NOT infer a runner's fitness from mileage, diagnose readiness,
 * change stored plans, or decide whether a runner should perform quality training.
 *
 * M3/M4 must eventually supply verified exposure, eligibility and the approved
 * ceiling/increase. Until then this stays a standalone research candidate.
 */
internal data class IndependentQualityFitnessInput(
    val reportedRecentWorkSeconds: Int?,
    val approvedWorkCeilingSeconds: Int?,
    val approvedIncreaseSeconds: Int = 0,
    /** Null means no user-supplied time cap; it does not mean missing workout evidence. */
    val availableSessionSeconds: Int? = null,
    val qualityAuthorized: Boolean,
    val recoveryOrReturnHold: Boolean = false
) {
    init {
        require(reportedRecentWorkSeconds == null ||
            reportedRecentWorkSeconds in 0..14400)
        require(approvedWorkCeilingSeconds == null ||
            approvedWorkCeilingSeconds in 0..14400)
        require(approvedIncreaseSeconds in 0..14400)
        require(availableSessionSeconds == null || availableSessionSeconds >= 0)
    }
}

internal sealed interface IndependentQualityFitnessDecision {
    data class Deferred(val ruleId: String, val explanation: String) : IndependentQualityFitnessDecision
    data class Candidate(
        val id: String,
        val family: IndependentQualityFamily,
        val workout: IndependentWorkoutComposition,
        val workSeconds: Int,
        val recoverySeconds: Int,
        val totalSeconds: Int,
        val ruleIds: List<String>,
        val policyEvidence: String = "HEURISTIC"
    ) : IndependentQualityFitnessDecision
}

/**
 * A small, transparent selection mechanism, not a fitness estimator.
 *
 * - A confirmed completed quality dose is the starting exposure.
 * - Additional work can only be considered with an explicit external allowance.
 * - A separate external ceiling and available-time ceiling both bind.
 * - Structure varies to fit these constraints; no pace, HR, or athlete progression
 *   is inferred, and no workout is automatically assigned to a saved plan.
 *
 * All numbers used to enumerate examples are HEURISTIC research candidates.
 */
internal object IndependentQualityFitnessAdapter {
    private const val WARM_UP = 10 * 60
    private const val INTRO_COOL_DOWN = 8 * 60
    private const val EXTENDED_COOL_DOWN = 10 * 60
    private fun coolDown(workSeconds: Int) =
        if (workSeconds >= 20 * 60) EXTENDED_COOL_DOWN else INTRO_COOL_DOWN
    private const val RECOVERY = 2 * 60

    fun generate(
        family: IndependentQualityFamily,
        input: IndependentQualityFitnessInput
    ): IndependentQualityFitnessDecision {
        fun defer(rule: String, message: String) =
            IndependentQualityFitnessDecision.Deferred(rule, message)

        if (!input.qualityAuthorized)
            return defer("QUALITY_NOT_AUTHORIZED", "An upstream coaching policy must authorize quality.")
        if (input.recoveryOrReturnHold)
            return defer("RECOVERY_HOLD", "A recovery/return hold prevents quality-session selection.")
        val observed = input.reportedRecentWorkSeconds
            ?: return defer("QUALITY_EXPOSURE_UNKNOWN", "Recorded quality-work exposure is unavailable.")
        val approved = input.approvedWorkCeilingSeconds
            ?: return defer("WORK_CEILING_UNKNOWN", "No independent coaching work-dose ceiling was supplied.")
        if (observed <= 0 || approved <= 0)
            return defer("NO_CONFIRMED_QUALITY_DOSE", "No positive demonstrated and approved work dose.")
        val exposurePlusAllowance = observed.toLong() + input.approvedIncreaseSeconds
        val permitted = minOf(approved.toLong(), exposurePlusAllowance).toInt()
        // The helper is stateless and uses only existing versioned M2 element types.
        fun stepOf(purpose: WorkoutPurpose, seconds: Int, target: WorkoutTarget) =
            WorkoutStep(purpose, WorkoutUnit.SECONDS, seconds.toDouble(), 1, target)
        fun composition(middle: IndependentWorkoutElement, workSeconds: Int): IndependentWorkoutComposition =
            IndependentWorkoutComposition(listOf(
                IndependentWorkoutElement.Step(stepOf(WorkoutPurpose.WARM_UP, WARM_UP, WorkoutTarget.EASY)),
                middle,
                IndependentWorkoutElement.Step(stepOf(WorkoutPurpose.COOL_DOWN, coolDown(workSeconds), WorkoutTarget.EASY))
            ))
        val candidatePairs = when (family) {
            IndependentQualityFamily.THRESHOLD -> {
                // 2–6 repeats of 3–8 min, with easy recovery ONLY between repeats.
                (2..6).flatMap { repetitions ->
                    (3..12).mapNotNull { workMinutes ->
                        val workSeconds = repetitions * workMinutes * 60
                        val recoverySeconds = (repetitions - 1) * RECOVERY
                        val total = WARM_UP + workSeconds + recoverySeconds + coolDown(workSeconds)
                        if (workSeconds > permitted ||
                            input.availableSessionSeconds?.let { total > it } == true) null
                        else {
                            val group = IndependentIntervalGroup(repetitions,
                                stepOf(WorkoutPurpose.WORK, workMinutes * 60, WorkoutTarget.THRESHOLD),
                                stepOf(WorkoutPurpose.RECOVERY, RECOVERY, WorkoutTarget.EASY))
                            Triple(composition(IndependentWorkoutElement.Intervals(group), workSeconds),
                                workSeconds, recoverySeconds)
                        }
                    }
                }
            }
            IndependentQualityFamily.TEMPO -> {
                // Enumerate one-minute structures up to the evidenced and reviewed dose.
                (8..(permitted / 60)).mapNotNull { minutes ->
                    val seconds = minutes * 60
                    if (seconds > permitted || input.availableSessionSeconds?.let {
                            WARM_UP + seconds + coolDown(seconds) > it
                        } == true) null
                    else Triple(composition(IndependentWorkoutElement.Step(
                        stepOf(WorkoutPurpose.WORK, seconds, WorkoutTarget.TEMPO)), seconds), seconds, 0)
                }
            }
        }
        if (candidatePairs.isEmpty())
            return defer("DOSE_OR_TIME_CONFLICT", "No example fits both the approved work ceiling and available time.")
        val preferredRepetitions = if (permitted <= 12 * 60) 3 else 4
        // Prefer the greatest supportable dose. Equal-dose threshold ties favor a
        // repeat count near 3–4, then shorter total workout time.
        val selected = candidatePairs.sortedWith(
            compareByDescending<Triple<IndependentWorkoutComposition, Int, Int>> { it.second }
                .thenBy {
                    val middle = it.first.elements[1]
                    if (middle is IndependentWorkoutElement.Intervals)
                        kotlin.math.abs(middle.value.repetitions - preferredRepetitions) else 0
                }
                .thenBy { it.first.total(WorkoutUnit.SECONDS) ?: Double.MAX_VALUE }
        ).first()
        val total = requireNotNull(selected.first.total(WorkoutUnit.SECONDS)).toInt()
        check(selected.second <= permitted &&
            input.availableSessionSeconds?.let { total <= it } != false)
        check(requireNotNull(selected.first.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK)).toInt() == selected.second)
        val structureId = when (val middle = selected.first.elements[1]) {
            is IndependentWorkoutElement.Step -> "${selected.second}s-continuous"
            is IndependentWorkoutElement.Intervals ->
                "${middle.value.repetitions}x${middle.value.work.amount.toInt()}s-${middle.value.recovery.amount.toInt()}s-recovery"
        }
        return IndependentQualityFitnessDecision.Candidate(
            id = "engine15-${family.name.lowercase()}-${structureId}-fitness-v1",
            family = family,
            workout = selected.first,
            workSeconds = selected.second,
            recoverySeconds = selected.third,
            totalSeconds = total,
            ruleIds = listOf(
                "CALLER_REPORTED_QUALITY_WORK_EXPOSURE",
                "EXPLICIT_WORK_CEILING",
                "EXPLICIT_INCREMENT_ALLOWANCE",
                if (input.availableSessionSeconds == null)
                    "USER_SESSION_TIME_LIMIT_NOT_PROVIDED"
                else "USER_SESSION_TIME_LIMIT",
                "HEURISTIC_TEMPLATE_STRUCTURE"
            )
        )
    }
}
