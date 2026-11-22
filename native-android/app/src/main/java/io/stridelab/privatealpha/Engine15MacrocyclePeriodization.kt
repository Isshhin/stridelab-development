package io.stridelab.privatealpha

import kotlin.math.roundToInt

/**
 * Step 03 proportional macrocycle phase model for Engine 1.5.
 *
 * Pure, deterministic function of [PlanRequest] and [Engine15IndependentVolumePlanner.Result]
 * so saved plans round-trip identically through [Engine15IndependentPlanGenerator.validateSaved].
 */
internal enum class Engine15MacroPhase {
    BASE,
    BUILD,
    PEAK,
    TAPER,
    RECOVERY
}

internal object Engine15MacrocyclePeriodization {
    private val RACE_GOALS = setOf("Improve 5K", "Improve 10K", "Half marathon", "Marathon")

    fun isRaceGoal(goal: String?): Boolean = goal in RACE_GOALS

    /**
     * Assigns a proportional [Engine15MacroPhase] to every 1-based week number in [volume].
     *
     * - Non-race "Train Your Way" goals (`Aerobic foundation`, `General fitness`) use repeating
     *   3:1 loading-to-recovery cycles without artificial race tapers or peak sharpening.
     * - Race goals scale BASE / BUILD / PEAK / TAPER proportionally by plan horizon (short 4–6w,
     *   medium 7–11w, long 12–16+w) and readiness (`experience == 0` / `returning == true` receive
     *   proportionally longer BASE accumulation and omit harsh PEAK weeks).
     */
    fun assignPhases(
        request: PlanRequest,
        volume: Engine15IndependentVolumePlanner.Result
    ): Map<Int, Engine15MacroPhase> {
        val totalWeeks = volume.weeks.size
        if (totalWeeks == 0) return emptyMap()

        val raceGoal = isRaceGoal(request.goal)
        val taperLen = if (raceGoal) {
            Engine15IndependentVolumePlanner.raceTaperLength(request.goal, totalWeeks)
        } else 0
        val taperWeekNumbers = if (taperLen > 0) {
            ((totalWeeks - taperLen + 1)..totalWeeks).toSet()
        } else emptySet()

        val result = LinkedHashMap<Int, Engine15MacroPhase>(totalWeeks)
        val loadingWeeks = mutableListOf<Int>()

        for (week in volume.weeks) {
            val wkNum = week.index + 1
            when {
                wkNum in taperWeekNumbers -> result[wkNum] = Engine15MacroPhase.TAPER
                week.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY -> result[wkNum] = Engine15MacroPhase.RECOVERY
                else -> loadingWeeks += wkNum
            }
        }

        if (loadingWeeks.isEmpty()) return result

        val isConservative = request.experience == 0 || request.returning
        val loadingCount = loadingWeeks.size

        if (!raceGoal) {
            val baseCount = when {
                request.returning -> (loadingCount * 0.75).roundToInt().coerceIn(1, loadingCount)
                request.goal == "Aerobic foundation" -> (loadingCount * 0.65).roundToInt().coerceIn(1, loadingCount)
                isConservative -> (loadingCount * 0.55).roundToInt().coerceIn(1, loadingCount)
                else -> minOf(2, (loadingCount * 0.25).roundToInt().coerceAtLeast(1))
            }
            loadingWeeks.forEachIndexed { idx, wkNum ->
                result[wkNum] = if (idx < baseCount) Engine15MacroPhase.BASE else Engine15MacroPhase.BUILD
            }
            return result
        }

        val (baseCount, peakCount) = when {
            isConservative -> {
                val baseShare = when {
                    totalWeeks <= 6 -> 0.50
                    totalWeeks <= 11 -> 0.65
                    else -> 0.55
                }
                val b = (loadingCount * baseShare).roundToInt().coerceIn(1, loadingCount)
                b to 0
            }
            totalWeeks <= 6 -> {
                val p = if (loadingCount >= 3) 1 else 0
                0 to p
            }
            totalWeeks <= 11 -> {
                val b = (loadingCount * 0.28).roundToInt().coerceIn(1, maxOf(1, loadingCount - 2))
                val p = when {
                    loadingCount - b >= 3 -> minOf(2, (loadingCount * 0.22).roundToInt().coerceAtLeast(1))
                    loadingCount - b >= 2 -> 1
                    else -> 0
                }
                b to p
            }
            else -> {
                val b = (loadingCount * 0.28).roundToInt().coerceIn(2, maxOf(2, loadingCount - 4))
                val p = (loadingCount * 0.22).roundToInt().coerceIn(2, maxOf(2, loadingCount - b - 2))
                b to p
            }
        }

        val buildEndExclusive = (loadingCount - peakCount).coerceAtLeast(baseCount)
        loadingWeeks.forEachIndexed { idx, wkNum ->
            result[wkNum] = when {
                idx < baseCount -> Engine15MacroPhase.BASE
                idx < buildEndExclusive -> Engine15MacroPhase.BUILD
                else -> Engine15MacroPhase.PEAK
            }
        }
        return result
    }

    fun phaseSummary(phases: Map<Int, Engine15MacroPhase>): String {
        val base = phases.values.count { it == Engine15MacroPhase.BASE }
        val build = phases.values.count { it == Engine15MacroPhase.BUILD }
        val peak = phases.values.count { it == Engine15MacroPhase.PEAK }
        val taper = phases.values.count { it == Engine15MacroPhase.TAPER }
        val rec = phases.values.count { it == Engine15MacroPhase.RECOVERY }
        return "MACROCYCLE_PHASES: BASE=${base}w, BUILD=${build}w, PEAK=${peak}w, TAPER=${taper}w, RECOVERY=${rec}w"
    }

    /**
     * Pure deterministic goal-feasibility and ramp advisories attached to [PlanSnapshot.explanations]
     * without breaking plan generation contracts.
     */
    fun feasibilityAdvisories(
        request: PlanRequest,
        volume: Engine15IndependentVolumePlanner.Result
    ): List<String> = buildList {
        if (request.goal == "Marathon" && request.weeks < 12) {
            add("GOAL_HORIZON_SHORT: Marathon build (${request.weeks} weeks) is compressed below the standard 12–16 week horizon; volume and long-run progression are safely capped by current baseline.")
        } else if (request.goal == "Half marathon" && request.weeks < 8) {
            add("GOAL_HORIZON_SHORT: Half marathon build (${request.weeks} weeks) is compressed; progression prioritizes sustainable stamina over peak volume.")
        }

        if (request.goal == "Marathon" && (request.weeklyKm < 30.0 || request.longestKm < 12.0)) {
            add("GOAL_READINESS_ADVISORY: Current baseline (${request.weeklyKm} km/wk, ${request.longestKm} km long) is below typical Marathon entry volume; weekly ramp and long-run jumps stay safely capped.")
        } else if (request.goal == "Half marathon" && (request.weeklyKm < 18.0 || request.longestKm < 8.0)) {
            add("GOAL_READINESS_ADVISORY: Current baseline (${request.weeklyKm} km/wk, ${request.longestKm} km long) is conservative for Half marathon; mileage and long runs stay within safe single-session caps.")
        }

        if ((volume.shortfallToRequestedTargetKm ?: 0.0) > 2.0) {
            add("VOLUME_JUMP_GUARDRAIL: Weekly volume ramp is bounded to prevent single-session and weekly mileage spikes.")
        }
    }
}
