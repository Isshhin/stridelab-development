package io.stridelab.privatealpha

import kotlin.math.abs
import kotlin.math.min

/**
 * M6b: choose *review-only* hard-work opportunities within the M6a distance
 * calendar. A slot is NOT a threshold workout, authorized intensity, or an
 * additional distance. Real dose and quality kilometers remain unresolved.
 *
 * This is a deterministic proposal based on externally requested frequency
 * and scheduling constraints, not an athlete-readiness or injury-risk model.
 */
internal data class IndependentQualitySpacingRequest(
    val proposedQualityDaysPerBuildWeek: Int,
    val preferredQualityDays: Set<Int> = emptySet(),
    val blockedDaysByWeek: Map<Int, Set<Int>> = emptyMap(),
    val minimumCalendarDaySeparation: Int = 2
) {
    init {
        require(proposedQualityDaysPerBuildWeek in 0..2)
        require(preferredQualityDays.all { it in 1..7 })
        require(blockedDaysByWeek.keys.all { it in 1..52 })
        require(blockedDaysByWeek.values.all { block -> block.all { it in 1..7 } })
        require(minimumCalendarDaySeparation in 2..3)
    }
}

internal data class IndependentQualityOpportunity(
    val week: Int,
    val isoDay: Int,
    val preexistingEasyRunKm: Double,
    val proposedOnly: Boolean = true,
    val requiresSourceAndDoseReview: Boolean = true,
    val executable: Boolean = false
) {
    init {
        require(week > 0 && isoDay in 1..7)
        require(preexistingEasyRunKm.isFinite() && preexistingEasyRunKm > 0.0)
        require(proposedOnly && requiresSourceAndDoseReview && !executable)
    }
}

internal data class IndependentWeekQualitySpacing(
    val week: Int,
    val recoveryWeek: Boolean,
    val heldForRecovery: Boolean,
    val opportunities: List<IndependentQualityOpportunity>
) {
    init {
        require(week > 0)
        require(opportunities.map { it.isoDay }.distinct().size == opportunities.size)
        require(opportunities.all { it.week == week })
        require(!recoveryWeek || opportunities.isEmpty())
        require(!heldForRecovery || recoveryWeek)
    }
}

internal sealed interface IndependentQualitySpacingDecision {
    data class Deferred(
        val week: Int?,
        val ruleId: String,
        val explanation: String
    ) : IndependentQualitySpacingDecision

    data class Candidate(
        val weeks: List<IndependentWeekQualitySpacing>,
        val ruleIds: List<String>,
        val sourceEvidenceVerified: Boolean = false,
        val qualityDoseApproved: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentQualitySpacingDecision {
        init {
            require(weeks.isNotEmpty())
            require(weeks.map { it.week }.distinct().size == weeks.size)
            require(ruleIds.distinct() == ruleIds)
            require(!sourceEvidenceVerified && !qualityDoseApproved && !authorizedForProduction)
        }
    }
}

internal object IndependentQualitySpacingPlanner {
    const val POLICY_ID = "stridelab.engine15.m6b-quality-spacing-research-v1"

    private fun defer(week: Int?, code: String, reason: String) =
        IndependentQualitySpacingDecision.Deferred(week, code, reason)

    private fun cyclicDistance(a: Int, b: Int): Int {
        val raw = abs(a - b)
        return min(raw, 7 - raw)
    }

    fun propose(
        base: IndependentConstrainedMultiweekDecision.Candidate,
        request: IndependentQualitySpacingRequest
    ): IndependentQualitySpacingDecision {
        val allWeeks = base.mileage.weeks
        val horizon = allWeeks.map { it.week }.toSet()
        if (request.blockedDaysByWeek.keys.any { it !in horizon })
            return defer(null, "BLOCKED_WEEK_OUTSIDE_HORIZON",
                "Blocked days must identify one of the requested plan weeks.")
        val selected = mutableListOf<IndependentWeekQualitySpacing>()
        var previousWeekQualityDays = emptyList<Int>()

        for ((week, typed) in allWeeks.zip(base.baseWorkouts.weeks)) {
            if (week.week != typed.week)
                return defer(week.week, "MISMATCHED_BASE_WEEK",
                    "Source mileage and typed workouts refer to different weeks.")
            if (week.recoveryWeek) {
                selected += IndependentWeekQualitySpacing(
                    week.week, true,
                    request.proposedQualityDaysPerBuildWeek > 0,
                    emptyList()
                )
                previousWeekQualityDays = emptyList()
                continue
            }
            if (request.proposedQualityDaysPerBuildWeek == 0) {
                selected += IndependentWeekQualitySpacing(week.week, false, false, emptyList())
                previousWeekQualityDays = emptyList()
                continue
            }
            if (typed.sessions.size < 3)
                return defer(week.week, "QUALITY_SLOT_INSUFFICIENT_RUN_DAYS",
                    "Quality opportunities are not proposed on fewer than three running days.")
            val longDay = typed.sessions.singleOrNull {
                it.kind == IndependentRunSessionKind.LONG
            }?.isoDay ?: return defer(week.week, "LONG_RUN_REFERENCE_MISSING",
                "Quality spacing requires one designated long-running day.")
            val blocked = request.blockedDaysByWeek[week.week].orEmpty()
            val possibilities = typed.sessions.filter {
                it.kind == IndependentRunSessionKind.EASY &&
                    it.isoDay !in blocked &&
                    cyclicDistance(it.isoDay, longDay) >= request.minimumCalendarDaySeparation
            }.map { it.isoDay }.sorted()

            val combinations = when (request.proposedQualityDaysPerBuildWeek) {
                1 -> possibilities.map { listOf(it) }
                else -> possibilities.indices.flatMap { i ->
                    ((i + 1) until possibilities.size).map { j ->
                        listOf(possibilities[i], possibilities[j])
                    }
                }
            }
            val valid = combinations.filter { proposed ->
                // Check within-week quality-to-quality distance.
                proposed.zipWithNext().all { (a, b) ->
                    b - a >= request.minimumCalendarDaySeparation
                } &&
                    // Check end-of-previous-week -> start-of-current-week
                    // even if this week's available days differ.
                    previousWeekQualityDays.all { prior ->
                        proposed.all { current ->
                            (7 + current - prior) >= request.minimumCalendarDaySeparation
                        }
                    }
            }
            if (valid.isEmpty()) {
                return defer(week.week, "NO_SPACED_QUALITY_OPPORTUNITIES",
                    "Requested frequency cannot fit selected run days, long-run spacing or blocked days.")
            }
            val chosen = valid.maxWithOrNull(
                compareBy<List<Int>> {
                    it.count { day -> day in request.preferredQualityDays }
                }.thenBy {
                    it.sumOf { day -> cyclicDistance(day, longDay) }
                }.thenBy {
                    // Earlier weekdays win deterministic score ties.
                    -it.sum()
                }
            ) ?: return defer(week.week, "NO_SPACED_QUALITY_OPPORTUNITIES",
                "No candidate meets the requested calendar spacing.")

            selected += IndependentWeekQualitySpacing(
                week = week.week,
                recoveryWeek = false,
                heldForRecovery = false,
                opportunities = chosen.map { day ->
                    val baseRun = typed.sessions.single { it.isoDay == day }
                    IndependentQualityOpportunity(
                        week = week.week,
                        isoDay = day,
                        preexistingEasyRunKm = baseRun.allocatedKilometers
                    )
                }
            )
            previousWeekQualityDays = chosen
        }
        return IndependentQualitySpacingDecision.Candidate(
            weeks = selected,
            ruleIds = listOf(
                "M6A_BASE_DISTANCE_UNCHANGED",
                "CALENDAR_ONLY_PROPOSED_QUALITY",
                "RECOVERY_WEEK_QUALITY_HOLD",
                "LONG_AND_QUALITY_CALENDAR_SPACING",
                "NO_INTENSITY_SOURCE_AUTHENTICATION",
                "NO_QUALITY_DISTANCE_RECONCILIATION",
                "NO_WORKOUT_OR_PLAN_MUTATION"
            )
        )
    }
}
