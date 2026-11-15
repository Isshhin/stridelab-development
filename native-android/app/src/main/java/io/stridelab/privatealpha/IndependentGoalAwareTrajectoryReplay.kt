package io.stridelab.privatealpha

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

/** Research goal identities: a weekly-distance goal is not a race program. */
internal enum class IndependentCoachingGoalMode {
    TRAIN_YOUR_WAY_MILEAGE,
    RACE_PREPARATION
}

internal data class IndependentCoachingGoalIdentity(
    val mode: IndependentCoachingGoalMode,
    val targetWeeklyKm: Double? = null,
    val raceDistanceKm: Double? = null,
    val raceDate: LocalDate? = null
) {
    init {
        require(targetWeeklyKm == null || targetWeeklyKm.isFinite() &&
            targetWeeklyKm in 0.0..1000.0)
        require(raceDistanceKm == null || raceDistanceKm.isFinite() &&
            raceDistanceKm in 0.0..1000.0)
    }
}

internal data class IndependentTrajectoryReplayInput(
    val planId: String,
    val goal: IndependentCoachingGoalIdentity,
    val history: IndependentMultiweekResponseInput,
    val audit: IndependentWeeklyEvidenceLedgerDecision
) {
    init { require(planId.isNotBlank() && planId.length <= 256) }
}

internal data class IndependentTrajectoryCheckpoint(
    val planningMonday: LocalDate,
    val completedWeeksUsed: Int,
    val decision: IndependentLongitudinalShadowDecision
) {
    init {
        require(planningMonday.dayOfWeek == DayOfWeek.MONDAY)
        require(completedWeeksUsed in 2..8)
        if (decision is IndependentLongitudinalShadowDecision.Comparison)
            require(decision.planningMonday == planningMonday)
    }
    val proposedKm: Double? get() =
        (decision as? IndependentLongitudinalShadowDecision.Comparison)?.assertedResponseKm
    val ruleId: String get() = when (decision) {
        is IndependentLongitudinalShadowDecision.Deferred -> decision.ruleId
        is IndependentLongitudinalShadowDecision.Comparison -> decision.responseReason
    }
    val executable: Boolean get() = false
}

internal sealed interface IndependentTrajectoryReplayDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentTrajectoryReplayDecision

    data class Trace(
        val planId: String,
        val goalMode: IndependentCoachingGoalMode,
        val requestedTargetWeeklyKm: Double,
        val checkpoints: List<IndependentTrajectoryCheckpoint>,
        val ruleIds: List<String>,
        val sourceEvidenceVerified: Boolean = false,
        val productionAuthorized: Boolean = false
    ) : IndependentTrajectoryReplayDecision {
        init {
            require(planId.isNotBlank())
            require(goalMode == IndependentCoachingGoalMode.TRAIN_YOUR_WAY_MILEAGE)
            require(requestedTargetWeeklyKm.isFinite() && requestedTargetWeeklyKm > 0.0)
            require(checkpoints.size in 1..7)
            require(checkpoints.zipWithNext().all { (a, b) ->
                a.planningMonday.plusWeeks(1) == b.planningMonday
            })
            require(checkpoints.map { it.planningMonday }.distinct().size == checkpoints.size)
            require(ruleIds.distinct().size == ruleIds.size)
            require(!sourceEvidenceVerified && !productionAuthorized)
            require(checkpoints.all { it.proposedKm == null ||
                it.proposedKm!! <= requestedTargetWeeklyKm + 0.01 })
        }

        val comparisonCount: Int get() = checkpoints.count {
            it.decision is IndependentLongitudinalShadowDecision.Comparison
        }
        val deferredCount: Int get() = checkpoints.size - comparisonCount
        val finalProposedKm: Double? get() = checkpoints.last().proposedKm
    }
}

/**
 * M8b replays already-asserted completed histories one checkpoint at a time.
 * It never uses a later observed week to influence an earlier decision, and
 * never treats a preview as a subsequently completed workout.
 *
 * It also explicitly refuses to interpret "Train Your Way" mileage as a
 * race-specific program: race periodization remains a separate review gate.
 */
internal object IndependentGoalAwareTrajectoryReplay {
    const val POLICY_ID = "stridelab.engine15.m8b-goal-aware-trajectory-v1"

    private fun defer(rule: String, reason: String) =
        IndependentTrajectoryReplayDecision.Deferred(rule, reason)

    fun replay(input: IndependentTrajectoryReplayInput): IndependentTrajectoryReplayDecision {
        val goal = input.goal
        when (goal.mode) {
            IndependentCoachingGoalMode.RACE_PREPARATION -> {
                if (goal.targetWeeklyKm != null || goal.raceDistanceKm == null ||
                    goal.raceDistanceKm <= 0.0 || goal.raceDate == null)
                    return defer("INVALID_RACE_GOAL_IDENTITY",
                        "A race goal needs an explicit race distance and date, not a weekly mileage target.")
                if (goal.raceDate <= input.history.planningMonday)
                    return defer("RACE_DATE_NOT_FUTURE",
                        "A race program must have a future event date.")
                return defer("RACE_SPECIFIC_PERIODIZATION_NOT_QUALIFIED",
                    "A race goal requires separate periodization and race-specific workout review; no conversion from a weekly mileage plan.")
            }
            IndependentCoachingGoalMode.TRAIN_YOUR_WAY_MILEAGE -> {
                if (goal.raceDistanceKm != null || goal.raceDate != null)
                    return defer("MILEAGE_GOAL_CONTAINS_RACE_FIELDS",
                        "Train Your Way must not inherit a race target or date.")
                val target = goal.targetWeeklyKm
                    ?: return defer("MILEAGE_TARGET_REQUIRED",
                        "Train Your Way must retain its explicit weekly distance target.")
                if (target <= 0.0)
                    return defer("MILEAGE_TARGET_REQUIRED",
                        "Train Your Way requires a positive weekly distance target.")
                val requested = input.history.volume.requestedTargetWeeklyKm
                    ?: return defer("MILEAGE_TARGET_NOT_IN_VOLUME_REQUEST",
                        "The plan volume request must independently carry its weekly target.")
                if (abs(target - requested) > 0.00001)
                    return defer("MILEAGE_TARGET_IDENTITY_MISMATCH",
                        "The Train Your Way target differs from the volume request.")
            }
        }
        val audited = when (val result = input.audit) {
            is IndependentWeeklyEvidenceLedgerDecision.Deferred ->
                return defer("M7_LEDGER_UNAVAILABLE", result.ruleId + ": " + result.explanation)
            is IndependentWeeklyEvidenceLedgerDecision.Candidate -> result
        }
        if (audited.planId != input.planId)
            return defer("M7_LEDGER_PLAN_MISMATCH",
                "Trajectory analysis cannot reuse a different plan's evidence.")
        val past = input.history.completedWeeks
        if (past.size !in 3..8)
            return defer("INSUFFICIENT_REPLAY_HISTORY",
                "At least three completed-week claims are needed to compare multiple checkpoints.")
        if (past.last().monday.plusWeeks(1) != input.history.planningMonday)
            return defer("STALE_TRAJECTORY_HISTORY",
                "The final observed week must immediately precede the requested planning Monday.")

        val steps = (2..past.size).map { count ->
            val prefix = past.take(count)
            val nextMonday = prefix.last().monday.plusWeeks(1)
            val earlierOnly = input.history.copy(
                planningMonday = nextMonday,
                completedWeeks = prefix
            )
            val comparison = IndependentLongitudinalShadow.compare(
                IndependentLongitudinalShadowInput(input.planId, earlierOnly, audited)
            )
            IndependentTrajectoryCheckpoint(nextMonday, count, comparison)
        }
        return IndependentTrajectoryReplayDecision.Trace(
            planId = input.planId,
            goalMode = goal.mode,
            requestedTargetWeeklyKm = requireNotNull(goal.targetWeeklyKm),
            checkpoints = steps,
            ruleIds = listOf(
                "PAST_ONLY_ROLLING_REPLAY_NO_LOOKAHEAD",
                "EXPLICIT_MILEAGE_GOAL_NO_RACE_REDIRECTION",
                "NEXT_WEEK_REEVALUATION_NOT_FUTURE_PROJECTION",
                "M7_PLAN_LINKS_NOT_COMPLETE_SOURCE_COVERAGE",
                "HISTORICAL_INTENT_AND_TOLERANCE_CALLER_ASSERTED",
                "NO_AUTOMATIC_PLAN_CHANGES",
                "NO_PRODUCTION_AUTHORIZATION"
            )
        )
    }
}
