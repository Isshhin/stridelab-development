package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal const val MULTI_SESSION_POLICY = "stridelab.engine1.multi-session-20261027.1"

/** Separate Engine 1.0 scheduling policy. The frozen Engine1 generator remains unchanged. */
internal object MultiSessionEngine1 : PlanGenerationEngine {
    override val version = ENGINE1_VERSION
    override val policyId = MULTI_SESSION_POLICY

    override fun generate(request: PlanRequest, owner: String, id: String, now: Instant): PlanSnapshot {
        val p = request.copy(policyChoice = "multi-session")
        validate(p)
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)

        val active = listOf(
            "run" to p.runDays,
            "str" to p.strengthDays,
            "cal" to p.calDays,
            "cond" to p.conditioningDays
        ).filter { it.second > 0 }
        val templates = active.flatMap { (discipline, count) ->
            val goal = Engine1Engines.goal(p, discipline)
            val solo = p.copy(
                goal = goal,
                policyChoice = "baseline",
                runDays = if (discipline == "run") count else 0,
                strengthDays = if (discipline == "str") count else 0,
                calDays = if (discipline == "cal") count else 0,
                conditioningDays = if (discipline == "cond") count else 0,
                qualityCount = if (discipline == "run") request.qualityCount else 0,
                weeklySlots = request.weeklySlots.filterKeys { it == discipline }
            )
            Engine1.generate(solo, owner, id, now).sessions
        }
        require(templates.map { it.id }.distinct().size == templates.size)

        val scheduled = mutableListOf<PlannedSession>()
        for (week in 0 until p.weeks) {
            val weekStart = p.start.plusWeeks(week.toLong())
            val weekTemplates = templates.filter { it.date in weekStart..weekStart.plusDays(6) }
            scheduled += scheduleWeek(p, weekStart, weekTemplates, scheduled)
        }
        require(scheduled.size == active.sumOf { it.second } * p.weeks)
        val explanations = listOf(
            "Engine 1.0 multi-session scheduling policy $policyId. The original verified Engine 1.0 generator and fallback policy are unchanged.",
            "Weekly session counts are independent of the number of available and actually scheduled training days. Compatible easy running and calisthenics may share a day.",
            "Daily planned time is capped at ${p.dailyMinutes} minutes. Same-day and adjacent-day combinations are checked conservatively; impossible schedules are rejected rather than silently dropping sessions.",
            "Prescriptions remain saved snapshots. Approval, actual workout logs, and offline persistence retain the existing Engine 1.0 contract."
        )
        return PlanSnapshot(
            id, owner, version, policyId, now, p,
            scheduled.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id }),
            explanations
        )
    }

    internal fun validate(request: PlanRequest) {
        if (request.disciplineGoals.any { (discipline, goal) ->
                goal !in (Engine1Engines.goals[discipline] ?: emptyList())
            }) throw PlanConstraint("Choose supported goals for each discipline.")

        WeeklyPlacement.validationError(request)?.let { throw PlanConstraint(it) }
        val total = request.runDays + request.strengthDays + request.calDays + request.conditioningDays
        if (total == 0) throw PlanConstraint("Choose at least one weekly training session.")
        if (request.runDays == 0 && request.qualityCount > 0) throw PlanConstraint("Enable running before requesting quality running sessions.")
        if (request.runDays !in 0..6 || request.strengthDays !in 0..4 ||
            request.calDays !in 0..4 || request.conditioningDays !in 0..3
        ) throw PlanConstraint("Check session counts.")
        if (listOf(request.runDays, request.strengthDays, request.calDays, request.conditioningDays)
                .any { it > request.days.size }
        ) throw PlanConstraint("A discipline cannot schedule more than one session on the same day. Add available days or reduce that discipline's weekly count.")
        if (request.dailyMinutes !in 15..240) throw PlanConstraint("Set a daily training time budget from 15 to 240 minutes.")

        val active = listOf(
            "run" to request.runDays,
            "str" to request.strengthDays,
            "cal" to request.calDays,
            "cond" to request.conditioningDays
        ).filter { it.second > 0 }
        active.forEach { (discipline, count) ->
            val goal = Engine1Engines.goal(request, discipline)
            Engine1.validate(request.copy(
                goal = goal,
                runDays = if (discipline == "run") count else 0,
                strengthDays = if (discipline == "str") count else 0,
                calDays = if (discipline == "cal") count else 0,
                conditioningDays = if (discipline == "cond") count else 0,
                qualityCount = if (discipline == "run") request.qualityCount else 0,
                weeklySlots = request.weeklySlots.filterKeys { it == discipline }
            ))
        }
    }

    private fun scheduleWeek(
        request: PlanRequest,
        start: LocalDate,
        templates: List<PlannedSession>,
        prior: List<PlannedSession>
    ): List<PlannedSession> {
        val slots = (0L..6L).map(start::plusDays).filter { it.dayOfWeek.value in request.days }
        val ordered = templates.sortedBy {
            when {
                it.kind == "long" -> 0
                it.quality -> 1
                it.lower -> 2
                it.discipline == "str" -> 3
                it.discipline == "cal" -> 4
                else -> 5
            }
        }
        val placed = mutableListOf<PlannedSession>()
        var attempts = 0
        fun solve(index: Int): Boolean {
            if (index == ordered.size) return true
            if (++attempts > 250_000) return false
            val session = ordered[index]
            val pinnedDay = WeeklyPlacement.fixedDay(request,session)
            val eligible = slots.filter {
                when {
                    pinnedDay != null -> it.dayOfWeek.value == pinnedDay
                    session.kind == "long" -> it.dayOfWeek.value == request.longDay
                    session.quality -> it.dayOfWeek.value in request.qualityDays
                    else -> true
                }
            }.sortedWith(compareBy<LocalDate> { date ->
                if ((prior + placed).none { it.date == date }) 0 else 1
            }.thenBy { it })
            for (date in eligible) {
                val candidate = session.copy(date = date)
                val sameDay = (prior + placed).filter { it.date == date }
                if (sameDay.sumOf { it.effectiveMinutes } + candidate.effectiveMinutes > request.dailyMinutes) continue
                if ((prior + placed).any { MultiSessionScheduling.conflict(candidate, it) }) continue
                placed += candidate
                if (solve(index + 1)) return true
                placed.removeAt(placed.lastIndex)
            }
            return false
        }
        if (!solve(0)) throw PlanConstraint(
            if(request.weeklySlots.isEmpty())
                "These sessions cannot fit the available days, ${request.dailyMinutes}-minute daily budget and recovery constraints. Add days, reduce sessions or change long-run/quality preferences."
            else "Selected weekdays cannot fit the available days, ${request.dailyMinutes}-minute budget and recovery constraints. Change placements or switch to automatic scheduling."
        )
        return placed
    }
}

internal object MultiSessionScheduling {
    fun conflict(a: PlannedSession, b: PlannedSession): Boolean {
        val gap = ChronoUnit.DAYS.between(a.date, b.date)
        if (gap == 0L) return !sameDayCompatible(a, b)
        if (kotlin.math.abs(gap) > 1) return false
        if (a.quality && (b.quality || b.kind == "long" || b.lower)) return true
        if (b.quality && (a.kind == "long" || a.lower)) return true
        if (a.lower && b.kind == "long" && gap == 1L) return true
        if (b.lower && a.kind == "long" && gap == -1L) return true
        if (a.lower && b.lower) return true
        return false
    }

    fun sameDayCompatible(a: PlannedSession, b: PlannedSession): Boolean {
        val run = listOf(a, b).firstOrNull { it.discipline == "run" } ?: return false
        val cal = listOf(a, b).firstOrNull { it.discipline == "cal" } ?: return false
        return a.discipline != b.discipline && !run.quality && run.kind !in setOf("long", "quality") &&
            !run.lower && !cal.lower
    }
}
