package io.stridelab.privatealpha

import kotlin.math.floor

internal enum class HybridGoalPriorityMode {
    PRIMARY_SECONDARY,
    EQUAL_PRIORITY_LEXIMIN,
    ALTERNATING_EMPHASIS_BLOCKS,
    MAINTENANCE_ONLY
}

internal enum class DisciplineEmphasisPhase {
    DEVELOPMENT,
    MAINTENANCE
}

internal data class GoalShortfallInput(
    val goalId: String,
    val discipline: String,
    val active: Boolean,
    val achievedDoseQ: Double,
    val minimumViableM: Double,
    val targetDoseTStar: Double
) {
    init {
        require(goalId.isNotBlank() && discipline.isNotBlank())
        require(achievedDoseQ.isFinite() && achievedDoseQ >= 0.0) { "STRUCTURAL_INPUT_INVALID: achievedDoseQ must be finite >= 0" }
        require(minimumViableM.isFinite() && minimumViableM >= 0.0) { "STRUCTURAL_INPUT_INVALID: minimumViableM must be finite >= 0" }
        require(targetDoseTStar.isFinite() && targetDoseTStar >= minimumViableM) {
            "STRUCTURAL_INPUT_INVALID: targetDoseTStar ($targetDoseTStar) must be finite >= minimumViableM ($minimumViableM)"
        }
    }
}

internal data class HybridScheduleScoreCandidate(
    val candidateId: String,
    val goalInputs: List<GoalShortfallInput>,
    val interferencePenalty: Double = 0.0
) {
    val normalizedShortfalls: Map<String, Double> = goalInputs.associate {
        it.goalId to Engine18HybridGoalOptimizer.computeNormalizedShortfall(it)
    }
    val descendingShortfalls: List<Double> = normalizedShortfalls.values.sortedDescending()
}

internal data class ParetoAlternativeOption(
    val optionId: String,
    val label: String,
    val runBiasFraction: Double,
    val strengthOrCalBiasFraction: Double,
    val disciplineAllocatedDays: Map<String, Int>
)

internal enum class MultiGoalFeasibilityOutcome {
    FEASIBLE_CONCURRENT_DEVELOPMENT,
    FEASIBLE_WITH_ALTERNATING_OR_MAINTENANCE,
    REQUESTED_MULTI_GOAL_PLAN_INFEASIBLE
}

internal data class MultiGoalCapacityAssessment(
    val outcome: MultiGoalFeasibilityOutcome,
    val availableWeeklyMinutes: Int,
    val requiredMinimumMinutes: Int,
    val pathways: List<String>,
    val note: String
)

/**
 * MOD-19 Normalized Goal Shortfall (s_g in [0.0, 2.0]) & Leximin Multi-Goal Optimizer:
 * - Short-circuited piecewise s_g eliminating eager 0/0 division on T* == m_g or m_g == 0
 * - Strict Pareto-efficient Leximin comparator without arbitrary scalar cross-discipline weights
 * - Secondary Development preservation when primary shortfall == 0
 * - Spiering (2021) Alternating Emphasis Blocks & S15 Pareto-frontier alternatives
 * - S19 honest modeled capacity infeasibility (never claims biological impossibility)
 */
internal object Engine18HybridGoalOptimizer {

    /** MOD-19 short-circuited piecewise shortfall strictly bounded in [0.0, 2.0]. */
    fun computeNormalizedShortfall(input: GoalShortfallInput): Double {
        if (!input.active || input.targetDoseTStar == 0.0 || input.achievedDoseQ >= input.targetDoseTStar) {
            return 0.0
        }
        val m = input.minimumViableM
        val q = input.achievedDoseQ
        val tStar = input.targetDoseTStar
        return if (q >= m) {
            (tStar - q) / (tStar - m)
        } else {
            1.0 + ((m - q) / m)
        }
    }

    /** Two-pass strictly transitive Leximin comparator (quantized 5% buckets -> raw vector -> interference -> ID). */
    val leximinComparator: Comparator<HybridScheduleScoreCandidate> = Comparator { a, b ->
        val maxLen = maxOf(a.descendingShortfalls.size, b.descendingShortfalls.size)
        for (i in 0 until maxLen) {
            val va = a.descendingShortfalls.getOrElse(i) { 0.0 }
            val vb = b.descendingShortfalls.getOrElse(i) { 0.0 }
            val qa = floor(va / Engine18HybridPolicyRegistry.MOD_19_LEXIMIN_BUCKET_WIDTH).toLong()
            val qb = floor(vb / Engine18HybridPolicyRegistry.MOD_19_LEXIMIN_BUCKET_WIDTH).toLong()
            val cmpQ = qa.compareTo(qb)
            if (cmpQ != 0) return@Comparator cmpQ
        }
        for (i in 0 until maxLen) {
            val va = a.descendingShortfalls.getOrElse(i) { 0.0 }
            val vb = b.descendingShortfalls.getOrElse(i) { 0.0 }
            val cmpRaw = va.compareTo(vb)
            if (cmpRaw != 0) return@Comparator cmpRaw
        }
        val cmpInterf = a.interferencePenalty.compareTo(b.interferencePenalty)
        if (cmpInterf != 0) cmpInterf else a.candidateId.compareTo(b.candidateId)
    }

    /** Secondary Development Rule: preserves full secondary development if primary shortfall == 0 and budget fits. */
    fun evaluateSecondaryEmphasis(
        primaryShortfall: Double,
        remainingWeeklyMinutes: Int,
        secondaryDevelopmentMinMinutes: Int
    ): DisciplineEmphasisPhase = if (primaryShortfall <= 1e-6 && remainingWeeklyMinutes >= secondaryDevelopmentMinMinutes) {
        DisciplineEmphasisPhase.DEVELOPMENT
    } else {
        DisciplineEmphasisPhase.MAINTENANCE
    }

    /** Alternating 4-6 wk Development vs. Maintenance blocks (Spiering 2021). */
    fun assignAlternatingEmphasisBlocks(
        totalWeeks: Int,
        disciplines: List<String>,
        blockWeeks: Int = 4
    ): Map<Int, Map<String, DisciplineEmphasisPhase>> {
        require(totalWeeks in 1..16 && disciplines.isNotEmpty() && blockWeeks in 2..6)
        return (1..totalWeeks).associateWith { week ->
            val blockIndex = (week - 1) / blockWeeks
            val activeDevDiscipline = disciplines[blockIndex % disciplines.size]
            disciplines.associateWith { d ->
                if (d == activeDevDiscipline) DisciplineEmphasisPhase.DEVELOPMENT else DisciplineEmphasisPhase.MAINTENANCE
            }
        }
    }

    /** Builds S15 Pareto-frontier alternatives when constrained days cannot give full concurrent development. */
    fun buildParetoAlternatives(
        availableDays: Int,
        disciplines: List<String>
    ): List<ParetoAlternativeOption> {
        if (disciplines.size < 2 || availableDays <= 0) return emptyList()
        val first = disciplines.first()
        val second = disciplines[1]
        val halfDown = availableDays / 2
        val halfUp = availableDays - halfDown
        return listOf(
            ParetoAlternativeOption(
                optionId = "pareto-run-biased",
                label = "Run-Biased",
                runBiasFraction = 0.67,
                strengthOrCalBiasFraction = 0.33,
                disciplineAllocatedDays = mapOf(first to maxOf(1, halfUp), second to maxOf(1, availableDays - maxOf(1, halfUp)))
            ),
            ParetoAlternativeOption(
                optionId = "pareto-strength-biased",
                label = "Strength/Skill-Biased",
                runBiasFraction = 0.33,
                strengthOrCalBiasFraction = 0.67,
                disciplineAllocatedDays = mapOf(second to maxOf(1, halfUp), first to maxOf(1, availableDays - maxOf(1, halfUp)))
            ),
            ParetoAlternativeOption(
                optionId = "pareto-balanced",
                label = "Balanced Hybrid",
                runBiasFraction = 0.50,
                strengthOrCalBiasFraction = 0.50,
                disciplineAllocatedDays = mapOf(first to maxOf(1, halfUp), second to maxOf(1, halfDown))
            )
        )
    }

    /** S19 modeled workload/time capacity check (never asserts biological human limits). */
    fun checkMultiGoalCapacityFeasibility(
        weeklyAvailableMinutes: Int,
        goalMinDevelopmentMinutes: Map<String, Int>,
        goalMaintenanceMinutes: Map<String, Int>
    ): MultiGoalCapacityAssessment {
        val totalDevMin = goalMinDevelopmentMinutes.values.sum()
        val maxPrimaryPlusMaint = goalMinDevelopmentMinutes.keys.minOfOrNull { primary ->
            goalMinDevelopmentMinutes.getValue(primary) +
                goalMaintenanceMinutes.filterKeys { it != primary }.values.sum()
        } ?: totalDevMin

        return when {
            weeklyAvailableMinutes >= totalDevMin -> MultiGoalCapacityAssessment(
                outcome = MultiGoalFeasibilityOutcome.FEASIBLE_CONCURRENT_DEVELOPMENT,
                availableWeeklyMinutes = weeklyAvailableMinutes,
                requiredMinimumMinutes = totalDevMin,
                pathways = emptyList(),
                note = "Weekly time budget supports concurrent minimum development across requested goals."
            )
            weeklyAvailableMinutes >= maxPrimaryPlusMaint -> MultiGoalCapacityAssessment(
                outcome = MultiGoalFeasibilityOutcome.FEASIBLE_WITH_ALTERNATING_OR_MAINTENANCE,
                availableWeeklyMinutes = weeklyAvailableMinutes,
                requiredMinimumMinutes = maxPrimaryPlusMaint,
                pathways = listOf("Choose 1 primary goal + MED maintenance", "Use 4-week alternating emphasis blocks"),
                note = "Time budget fits one primary development focus with maintenance on secondary tracks."
            )
            else -> MultiGoalCapacityAssessment(
                outcome = MultiGoalFeasibilityOutcome.REQUESTED_MULTI_GOAL_PLAN_INFEASIBLE,
                availableWeeklyMinutes = weeklyAvailableMinutes,
                requiredMinimumMinutes = totalDevMin,
                pathways = listOf(
                    "Designate 1 primary goal and move secondary disciplines to minimal effective maintenance",
                    "Run 4 to 6 week sequential emphasis blocks",
                    "Adopt moderate hybrid targets instead of simultaneous peak development",
                    "Increase weekly available training time above $totalDevMin min/wk"
                ),
                note = "Requested multi-goal workload ($totalDevMin min/wk modeled minimum) exceeds your $weeklyAvailableMinutes min/wk time budget under modeled constraints."
            )
        }
    }
}
