package io.stridelab.privatealpha

import kotlin.math.abs
import kotlin.math.floor

/**
 * M5 Stage A: independent M4B -> distance-only workout SLOT composition.
 *
 * Slots have no calendar dates. This module neither schedules nor authorizes
 * workouts and never calls Engine 1.0, the live Engine15 generator, or old
 * sessions. Future M5 quality and M6 scheduling must be reviewed separately.
 */
internal enum class Engine15M5RunRole { EASY, LONG }

internal data class Engine15M5WorkoutSlot(
    val weekNumber: Int,
    val slotNumber: Int,
    val role: Engine15M5RunRole,
    val distanceKm: Double,
    val workout: IndependentWorkoutComposition
) {
    init {
        require(weekNumber > 0 && slotNumber > 0)
        require(distanceKm.isFinite() && distanceKm > 0.0)
        val step = (workout.elements.singleOrNull() as? IndependentWorkoutElement.Step)?.value
            ?: throw IllegalArgumentException("M5A slot requires one distance step")
        require(step.purpose == WorkoutPurpose.WORK && step.unit == WorkoutUnit.METERS &&
            step.target == WorkoutTarget.EASY && step.repetitions == 1)
        require(abs(step.amount - distanceKm * 1000.0) < 1e-6)
    }
    val dateAssigned: Boolean get() = false
    val qualityAuthorized: Boolean get() = false
}

internal data class Engine15M5WeekPreview(
    val weekNumber: Int,
    val kind: Engine15IndependentVolumePlanner.WeekKind,
    val budgetKm: Double,
    val slots: List<Engine15M5WorkoutSlot>
) {
    init {
        require(weekNumber > 0 && budgetKm.isFinite() && budgetKm > 0.0)
        require(slots.isNotEmpty() && slots.all { it.weekNumber == weekNumber })
        require(slots.map { it.slotNumber } == (1..slots.size).toList())
        require(abs(slots.sumOf { it.distanceKm } - budgetKm) < 1e-6)
    }
}

internal sealed interface Engine15M5CompositionDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val weekNumber: Int? = null
    ) : Engine15M5CompositionDecision

    data class Candidate(
        val policyId: String,
        val weeks: List<Engine15M5WeekPreview>,
        val ruleIds: List<String>,
        val scheduled: Boolean = false,
        val qualityAuthorized: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15M5CompositionDecision {
        init {
            require(weeks.isNotEmpty() && weeks.map { it.weekNumber } ==
                (1..weeks.size).toList())
            require(ruleIds.distinct() == ruleIds)
            require(!scheduled && !qualityAuthorized && !authorizedForProduction)
        }
    }
}

/**
 * Rechecks the source M4B result, then exactly partitions each weekly 100 m
 * budget into positive, bounded EASY/LONG slots without inventing pace or dose.
 * If the bound cannot be satisfied, the entire proposal is deferred.
 */
internal object Engine15M5WorkoutComposer {
    const val POLICY_ID = "engine15.m5a.m4b-distance-shadow-20261117.1"
    private const val EPS = 1e-8

    private fun tenths(km: Double): Int = floor(km * 10.0 + EPS).toInt()

    fun compose(source: Engine15IndependentVolumePlanner.Result):
        Engine15M5CompositionDecision {
        fun defer(id: String, why: String, week: Int? = null) =
            Engine15M5CompositionDecision.Deferred(id, why, week)

        if (source.status != Engine15IndependentVolumePlanner.Status.DISTANCE_CANDIDATE)
            return defer("M4B_NON_DISTANCE_STATUS",
                "M4B did not supply an eligible numeric weekly budget.")
        // M4B is the policy owner. A caller cannot substitute a larger budget,
        // bypass a recovery cap or alter a week while preserving its type.
        if (source != Engine15IndependentVolumePlanner.propose(
                source.athleteBaseline, source.input))
            return defer("M4B_RESULT_MISMATCH",
                "M4B candidate does not match recomputed independent policy output.")

        val runDays = source.input.runningDays
        if (runDays < 2)
            return defer("ONE_DAY_COMPOSITION_UNREVIEWED",
                "One-day running plans need a separately reviewed composition policy.")
        val userDirected=source.input.userDirected
        val longest = source.athleteBaseline.reportedLongestRunKm
        if(longest==null && !userDirected) return defer("LONGEST_RUN_EXPOSURE_UNKNOWN",
            "A numeric weekly budget does not establish per-session exposure.")

        // M4B's provisional session bounds: one potential long exposure and
        // a separate 80% bound for every other day. No exposure is increased.
        val longMaxUnits = if(userDirected) 1200 else tenths(longest!! + 1.0)
        val easyMaxUnits = if(userDirected) 1200 else tenths((longest!! + 1.0) * 0.8)
        if (easyMaxUnits < 1 || longMaxUnits < 1)
            return defer("SESSION_EXPOSURE_TOO_SMALL",
                "Available exposure cannot support positive 100 m workout slots.")

        var previousLongUnits: Int? = null
        val result = mutableListOf<Engine15M5WeekPreview>()
        for (week in source.weeks) {
            val number = week.index + 1
            val km = week.proposedKm
                ?: return defer("MISSING_WEEKLY_BUDGET",
                    "Every M5A week needs a numeric M4B budget.", number)
            val total = tenths(km)
            if (total < runDays || abs(total / 10.0 - km) > 1e-6)
                return defer("WEEKLY_BUDGET_NOT_REPRESENTABLE",
                    "Cannot represent this budget as positive 100 m run slots.", number)

            val lengths: List<Int> = if (runDays == 2) {
                val first = total / 2
                val second = total - first
                if (first < 1 || second > easyMaxUnits)
                    return defer("TWO_DAY_EXPOSURE_CONFLICT",
                        "Two easy runs cannot contain this M4B upper-bound budget.", number)
                listOf(first, second)
            } else {
                val longCap = if(userDirected) longMaxUnits
                    else minOf(longMaxUnits, tenths(total / 10.0 * 0.40))
                // LONG is not a separate intensity. Give it at least the
                // longest share and enough distance to keep EASY slots in cap.
                val minimumLong = maxOf(
                    (total + runDays - 1) / runDays,
                    total - (runDays - 1) * easyMaxUnits
                )
                val preferredLong = floor(total * 0.30 + EPS).toInt()
                val chosenLong = maxOf(minimumLong, minOf(preferredLong, longCap))
                if (chosenLong > longCap || chosenLong < 1)
                    return defer("LONG_OR_EASY_EXPOSURE_CONFLICT",
                        "A long slot capped at 40% and the session exposure limits cannot fit the week.",
                        number)
                val remaining = total - chosenLong
                val quotient = remaining / (runDays - 1)
                val remainder = remaining % (runDays - 1)
                val easy = (0 until runDays - 1).map { index ->
                    quotient + if (index < remainder) 1 else 0
                }
                if (easy.any { it < 1 || it > easyMaxUnits || it > chosenLong })
                    return defer("EASY_SLOT_EXPOSURE_CONFLICT",
                        "Cannot fit positive easy runs without exceeding exposure limits.",
                        number)
                easy + chosenLong
            }

            val longUnits = if (runDays >= 3) lengths.last() else null
            if (week.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY &&
                longUnits != null && previousLongUnits != null &&
                longUnits > previousLongUnits)
                return defer("RECOVERY_LONG_RUN_INCREASE",
                    "The recovery week must not increase the longest session.", number)

            val sessions = lengths.mapIndexed { index, units ->
                val role = if (runDays >= 3 && index == runDays - 1)
                    Engine15M5RunRole.LONG else Engine15M5RunRole.EASY
                Engine15M5WorkoutSlot(
                    weekNumber = number,
                    slotNumber = index + 1,
                    role = role,
                    distanceKm = units / 10.0,
                    workout = IndependentWorkoutComposition(
                        listOf(IndependentWorkoutElement.Step(
                            WorkoutStep(
                                purpose = WorkoutPurpose.WORK,
                                unit = WorkoutUnit.METERS,
                                amount = units * 100.0,
                                repetitions = 1,
                                target = WorkoutTarget.EASY
                            )
                        ))
                    )
                )
            }
            result += Engine15M5WeekPreview(number, week.kind, km, sessions)
            previousLongUnits = longUnits
        }
        return Engine15M5CompositionDecision.Candidate(
            policyId = POLICY_ID,
            weeks = result,
            ruleIds = listOf(
                "M4B_RECOMPUTED_AND_MATCHED",
                "WEEKLY_100M_BUDGET_EXACTLY_RECONCILED",
                "LONG_MAX_40_PERCENT_AND_REPORTED_EXPOSURE",
                "EASY_MAX_80_PERCENT_REPORTED_EXPOSURE",
                "EASY_ONLY_NO_QUALITY_INFERENCE",
                "UNSCHEDULED_SHADOW_ONLY_NO_PRODUCTION_MUTATION"
            )
        )
    }
}
