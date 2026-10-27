package io.stridelab.privatealpha

import java.time.temporal.ChronoUnit

/**
 * Offline, explainable schedule screening. These are structural warning rules,
 * not a sports-science risk score or authorization to change approved sessions.
 */
internal enum class Engine2StressTag { EASY, MODERATE, HARD, UNKNOWN }
internal enum class Engine2ScheduleIssueCode {
    DAILY_BUDGET, QUALITY_ADJACENCY, HARD_WITH_LOWER_BODY, LOWER_BEFORE_LONG,
    UNKNOWN_LOAD
}
internal data class Engine2SessionDemand(
    val sessionId: String,
    val stress: Engine2StressTag = Engine2StressTag.UNKNOWN,
    val lowerBody: Boolean = false,
    val longRun: Boolean = false
) {
    init { require(sessionId.isNotBlank()) }
}
internal data class Engine2ScheduleIssue(
    val code: Engine2ScheduleIssueCode,
    val sessionIds: Set<String>,
    val explanation: String
)

/** The budget is a user-supplied limit, not an estimated athletic capacity. */
internal object Engine2ScheduleAuditor {
    fun inspect(
        plan: Engine2PlanView,
        demands: List<Engine2SessionDemand>,
        dailyBudgetMinutes: Int
    ): List<Engine2ScheduleIssue> {
        require(dailyBudgetMinutes in 15..1440)
        require(demands.map { it.sessionId }.distinct().size == demands.size)
        val sessionIds = plan.sessions.map { it.id }.toSet()
        require(demands.all { it.sessionId in sessionIds })
        val map = demands.associateBy { it.sessionId }
        val active = plan.sessions.filter { it.status in setOf(
            Engine2SessionStatus.PLANNED, Engine2SessionStatus.IN_PROGRESS
        ) }.sortedWith(compareBy<Engine2SessionTarget> { it.date }.thenBy { it.id })
        val issues = mutableListOf<Engine2ScheduleIssue>()
        active.groupBy { it.date }.toSortedMap().forEach { (date, day) ->
            if (day.sumOf { it.minutes.toLong() } > dailyBudgetMinutes.toLong()) {
                issues += Engine2ScheduleIssue(
                    Engine2ScheduleIssueCode.DAILY_BUDGET, day.map { it.id }.toSet(),
                    "Planned minutes on " + date + " exceed the selected daily time budget."
                )
            }
        }
        active.forEach { session ->
            if (map[session.id]?.stress == null ||
                map[session.id]?.stress == Engine2StressTag.UNKNOWN) {
                issues += Engine2ScheduleIssue(
                    Engine2ScheduleIssueCode.UNKNOWN_LOAD, setOf(session.id),
                    "Training intensity for " + session.id + " is unknown; no recovery guarantee is inferred."
                )
            }
        }
        for (i in active.indices) for (j in i + 1 until active.size) {
            val a = active[i]
            val b = active[j]
            val da = map[a.id] ?: continue
            val db = map[b.id] ?: continue
            val gap = ChronoUnit.DAYS.between(a.date, b.date)
            if (gap > 1) break
            val ids = setOf(a.id, b.id)
            if (gap == 1L && da.stress == Engine2StressTag.HARD && db.stress == Engine2StressTag.HARD) {
                issues += Engine2ScheduleIssue(
                    Engine2ScheduleIssueCode.QUALITY_ADJACENCY, ids,
                    "Two explicitly hard sessions are scheduled on consecutive days. Review recovery spacing."
                )
            }
            if (gap == 0L && (
                da.stress == Engine2StressTag.HARD && db.lowerBody ||
                db.stress == Engine2StressTag.HARD && da.lowerBody
            )) {
                issues += Engine2ScheduleIssue(
                    Engine2ScheduleIssueCode.HARD_WITH_LOWER_BODY, ids,
                    "Hard work and lower-body training share a day. Review the combined schedule."
                )
            }
            if (gap == 1L && da.lowerBody && db.longRun) {
                issues += Engine2ScheduleIssue(
                    Engine2ScheduleIssueCode.LOWER_BEFORE_LONG, ids,
                    "Lower-body training falls the day before a long run; review the spacing."
                )
            }
        }
        return issues.distinctBy { it.code to it.sessionIds }.sortedWith(
            compareBy<Engine2ScheduleIssue> { it.code.ordinal }
                .thenBy { it.sessionIds.sorted().joinToString("|") }
        )
    }
}
