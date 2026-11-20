package io.stridelab.privatealpha

import java.time.temporal.ChronoUnit
import kotlin.math.abs

internal enum class Engine15TidPattern {
    LOW_INTENSITY_ONLY,
    PYRAMIDAL,
    POLARIZED,
    HIGH_INTENSITY_HEAVY,
    INCOMPLETE_PRESCRIPTION_DATA
}

internal data class Engine15TidZoneBreakdown(
    val zone1LowMeters: Double,
    val zone2ModerateMeters: Double,
    val zone3HighMeters: Double,
    val unclassifiedMeters: Double
) {
    init {
        require(zone1LowMeters >= 0.0 && zone2ModerateMeters >= 0.0 &&
            zone3HighMeters >= 0.0 && unclassifiedMeters >= 0.0)
    }

    val totalMeters: Double get() = zone1LowMeters + zone2ModerateMeters + zone3HighMeters + unclassifiedMeters
    val classifiedMeters: Double get() = zone1LowMeters + zone2ModerateMeters + zone3HighMeters
    val zone1Fraction: Double get() = if (classifiedMeters > 0.0) zone1LowMeters / classifiedMeters else 0.0
    val zone2Fraction: Double get() = if (classifiedMeters > 0.0) zone2ModerateMeters / classifiedMeters else 0.0
    val zone3Fraction: Double get() = if (classifiedMeters > 0.0) zone3HighMeters / classifiedMeters else 0.0
}

internal data class Engine15TidWeekSummary(
    val weekNumber: Int,
    val breakdown: Engine15TidZoneBreakdown,
    val longRunShare: Double?
) {
    init {
        require(weekNumber > 0)
        require(longRunShare == null || (longRunShare.isFinite() && longRunShare in 0.0..1.0001))
    }
}

internal data class Engine15TidEvaluation(
    val policyId: String,
    val planId: String,
    val goal: String,
    val weeks: Int,
    val overall: Engine15TidZoneBreakdown,
    val weekly: List<Engine15TidWeekSummary>,
    val pattern: Engine15TidPattern,
    val advisories: List<String>,
    val ruleIds: List<String>,
    val readOnlyAdvisory: Boolean = true,
    val mutatesPlan: Boolean = false,
    val physiologyVerified: Boolean = false
) {
    init {
        require(policyId == Engine15MarathonTidEvaluator.POLICY_ID)
        require(planId.isNotBlank() && weeks > 0)
        require(ruleIds.distinct() == ruleIds && ruleIds.isNotEmpty())
        require(readOnlyAdvisory && !mutatesPlan && !physiologyVerified)
    }
}

/**
 * Stage 8 (Batch 34): Read-only advisory Training Intensity Distribution (TID)
 * research evaluator for running plans (especially 8–16-week Marathon & Half Marathon).
 *
 * - Classifies planned volume into 3-zone TID strictly from explicit prescription
 *   segments/distance equivalents; unlabelled steps stay unclassified.
 * - Never mutates plans, never infers physiological thresholds, and never claims
 *   actual performed intensity matches planned structure.
 */
internal object Engine15MarathonTidEvaluator {
    const val POLICY_ID = "stridelab.engine15.marathon-tid-evaluator-v1"

    fun evaluate(plan: PlanSnapshot): Engine15TidEvaluation {
        val runSessions = plan.sessions.filter { it.discipline == "run" }
        val byWeek = runSessions.groupBy { session ->
            (ChronoUnit.DAYS.between(plan.request.start, session.date).toInt() / 7) + 1
        }

        val weeklySummaries = (1..plan.request.weeks).map { weekNumber ->
            val sessions = byWeek[weekNumber].orEmpty()
            var z1 = 0.0
            var z2 = 0.0
            var z3 = 0.0
            var unclassified = 0.0
            var longRunMeters = 0.0

            for (session in sessions) {
                val distMeters = (session.distanceKm ?: 0.0) * 1000.0
                if (session.kind == "long") longRunMeters += distMeters
                val prescription = session.runningPrescription
                if (!session.quality) {
                    if (distMeters > 0.0) z1 += distMeters else unclassified += 0.0
                } else if (prescription == null) {
                    unclassified += distMeters.coerceAtLeast(0.0)
                } else {
                    val family = prescription.family?.lowercase().orEmpty()
                    val mixed = prescription.mixedSegments
                    if (mixed != null) {
                        var sumStepMeters = 0.0
                        var missingMeters = false
                        for (step in mixed) {
                            val m = (step.meters ?: step.distanceEquivalentMeters)?.toDouble()
                            if (m == null || m <= 0.0) {
                                missingMeters = true
                                continue
                            }
                            sumStepMeters += m
                            when (step.purpose.uppercase()) {
                                "WORK" -> when (family) {
                                    "intervals", "hills" -> z3 += m
                                    "tempo", "threshold", "fartlek", "progression" -> z2 += m
                                    else -> unclassified += m
                                }
                                "WARM_UP", "RECOVERY", "COOL_DOWN", "EASY" -> z1 += m
                                else -> unclassified += m
                            }
                        }
                        if (missingMeters && distMeters > sumStepMeters) {
                            unclassified += (distMeters - sumStepMeters)
                        }
                    } else if (prescription.segments.isNotEmpty()) {
                        for (seg in prescription.segments) {
                            val m = seg.meters.toDouble()
                            when (seg.purpose.uppercase()) {
                                "WORK" -> when (family) {
                                    "intervals", "hills" -> z3 += m
                                    "tempo", "threshold", "fartlek", "progression" -> z2 += m
                                    else -> unclassified += m
                                }
                                "WARM_UP", "RECOVERY", "COOL_DOWN", "EASY" -> z1 += m
                                else -> unclassified += m
                            }
                        }
                    } else {
                        unclassified += distMeters.coerceAtLeast(0.0)
                    }
                }
            }
            val breakdown = Engine15TidZoneBreakdown(z1, z2, z3, unclassified)
            val share = if (longRunMeters > 0.0 && breakdown.totalMeters > 0.0) {
                (longRunMeters / breakdown.totalMeters).coerceIn(0.0, 1.0)
            } else null
            Engine15TidWeekSummary(weekNumber, breakdown, share)
        }

        val overall = Engine15TidZoneBreakdown(
            zone1LowMeters = weeklySummaries.sumOf { it.breakdown.zone1LowMeters },
            zone2ModerateMeters = weeklySummaries.sumOf { it.breakdown.zone2ModerateMeters },
            zone3HighMeters = weeklySummaries.sumOf { it.breakdown.zone3HighMeters },
            unclassifiedMeters = weeklySummaries.sumOf { it.breakdown.unclassifiedMeters }
        )

        val pattern = when {
            overall.totalMeters <= 0.0 || overall.unclassifiedMeters > 1e-6 ->
                Engine15TidPattern.INCOMPLETE_PRESCRIPTION_DATA
            overall.zone2ModerateMeters <= 1e-6 && overall.zone3HighMeters <= 1e-6 ->
                Engine15TidPattern.LOW_INTENSITY_ONLY
            overall.zone1Fraction < 0.75 ->
                Engine15TidPattern.HIGH_INTENSITY_HEAVY
            overall.zone2ModerateMeters >= overall.zone3HighMeters ->
                Engine15TidPattern.PYRAMIDAL
            else ->
                Engine15TidPattern.POLARIZED
        }

        val advisories = buildList {
            when (pattern) {
                Engine15TidPattern.INCOMPLETE_PRESCRIPTION_DATA ->
                    add("Some quality sessions lack per-segment distance accounting; TID classification is incomplete.")
                Engine15TidPattern.LOW_INTENSITY_ONLY ->
                    add("Planned volume is 100% low-intensity aerobic running (easy and long runs).")
                Engine15TidPattern.HIGH_INTENSITY_HEAVY ->
                    add("Planned moderate/high-intensity work exceeds 25% of classified volume; review workload balance for endurance sustainability.")
                Engine15TidPattern.PYRAMIDAL ->
                    add("Planned volume follows a pyramidal endurance distribution (${"%.0f".format(java.util.Locale.US, overall.zone1Fraction * 100)}% Z1 / ${"%.0f".format(java.util.Locale.US, overall.zone2Fraction * 100)}% Z2 / ${"%.0f".format(java.util.Locale.US, overall.zone3Fraction * 100)}% Z3).")
                Engine15TidPattern.POLARIZED ->
                    add("Planned volume follows a polarized distribution (${"%.0f".format(java.util.Locale.US, overall.zone1Fraction * 100)}% Z1 / ${"%.0f".format(java.util.Locale.US, overall.zone2Fraction * 100)}% Z2 / ${"%.0f".format(java.util.Locale.US, overall.zone3Fraction * 100)}% Z3).")
            }
            val highLongShareWeeks = weeklySummaries.filter { (it.longRunShare ?: 0.0) > 0.40 }
            if (highLongShareWeeks.isNotEmpty() && plan.request.runDays >= 4) {
                add("Long-run distance exceeds 40% of weekly volume in week(s) ${highLongShareWeeks.map { it.weekNumber }.joinToString()}; review single-session exposure.")
            }
            if (plan.request.goal == "Marathon" && plan.request.weeks < 12) {
                add("Short marathon horizon (${plan.request.weeks} weeks); a 12–16-week build is typically preferred for full marathon preparation.")
            }
        }

        return Engine15TidEvaluation(
            policyId = POLICY_ID,
            planId = plan.id,
            goal = plan.request.goal,
            weeks = plan.request.weeks,
            overall = overall,
            weekly = weeklySummaries,
            pattern = pattern,
            advisories = advisories,
            ruleIds = listOf(
                "READ_ONLY_TID_EVALUATION",
                "PRESCRIPTION_SEGMENTS_ONLY_NO_PHYSIOLOGY_INFERRED",
                "UNLABELLED_STEPS_STAY_UNCLASSIFIED"
            )
        )
    }
}
