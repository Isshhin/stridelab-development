package io.stridelab.privatealpha

import java.time.LocalDate

/** Engine 1.5 static cross-discipline coordination for composed, already constructed sessions. */
internal object Engine15StaticMultiDisciplinePlanner {
    private val disciplines = listOf("run", "str", "cal", "cond")

    fun validateRequest(request: PlanRequest) {
        val active = disciplines.filter { Engine1Engines.count(request, it) > 0 }
        if (request.policyChoice != "engine15" || active.size < 2 || request.weeks !in 1..16 ||
            request.days.isEmpty() || request.days.any { it !in 1..7 } ||
            request.disciplineGoals.keys.any { it !in active } ||
            request.weeklySlots.keys.any { it !in active } ||
            request.timeBudgetEnabled && request.dailyMinutes !in 15..240)
            throw PlanConstraint("ENGINE15_COMBINED_REQUEST_UNSUPPORTED: Choose at least two supported disciplines, valid available weekdays and a valid daily time limit.")
        active.forEach { discipline ->
            val solo = soloRequest(request, discipline)
            when (discipline) {
                "run" -> when (val outcome = Engine15IndependentPlanGenerator.preview(solo, "combined-validation", "combined-validation", java.time.Instant.EPOCH)) {
                    is Engine15IndependentPlanDecision.Deferred -> throw PlanConstraint("${outcome.reasonCode}: ${outcome.explanation}")
                    is Engine15IndependentPlanDecision.Candidate -> Unit
                }
                "str" -> Engine15StrengthWorkoutComposer.validateRequest(solo)
                "cal" -> Engine15CalisthenicsWorkoutComposer.validateRequest(solo)
                "cond" -> Engine15ConditioningWorkoutComposer.validateRequest(solo)
            }
        }
        validateWeeklySlots(request, active)
    }

    fun compose(request: PlanRequest, owner: String, id: String, now: java.time.Instant,
                existingPlans: List<PlanSnapshot>, library: ExerciseLibraryV2): PlanSnapshot {
        validateRequest(request)
        val start = request.start.minusDays(1)
        val end = request.start.plusWeeks(request.weeks.toLong())
        val activeSessions = existingPlans.asSequence().filter {
            it.owner == owner && it.status in setOf("approved", "paused")
        }.flatMap { plan -> plan.sessions.asSequence().filter { it.status in setOf("planned", "in_progress") } }
            .filter { it.date in start..end }.toList()
        if (activeSessions.isNotEmpty())
            throw PlanConstraint("EXTERNAL_SCHEDULE_REVIEW_REQUIRED: Combined generation does not yet merge pre-existing saved workouts; no existing session was ignored.")
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)
        val sessions = disciplines.filter { Engine1Engines.count(request, it) > 0 }.flatMap { discipline ->
            val solo = soloRequest(request, discipline)
            when (discipline) {
                "run" -> (Engine15IndependentPlanGenerator.preview(solo, owner, id, now) as? Engine15IndependentPlanDecision.Candidate)
                    ?.plan?.sessions ?: throw PlanConstraint("RUNNING_COMPONENT_UNAVAILABLE: No independent running candidate was produced.")
                "str" -> Engine15StrengthWorkoutComposer.compose(solo, id, library, allowCalendarReschedule = true)
                "cal" -> Engine15CalisthenicsWorkoutComposer.compose(solo, id, library, allowCalendarReschedule = true)
                else -> Engine15ConditioningWorkoutComposer.compose(solo, id, library, allowCalendarReschedule = true)
            }
        }
        val scheduled = schedule(request, sessions)
        if (scheduled.size != request.weeks * disciplines.sumOf { Engine1Engines.count(request, it) } ||
            scheduled.map { it.id }.distinct().size != scheduled.size)
            throw PlanConstraint("ENGINE15_COMBINED_PLAN_INCOMPLETE: A combined discipline slot is missing or duplicated.")
        return PlanSnapshot(
            id = id, owner = owner, engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_COMBINED_POLICY, createdAt = now,
            request = request, sessions = scheduled,
            explanations = listOf(
                "Independent Engine 1.5 static multi-discipline plan. Every requested session was constructed by its Engine 1.5 discipline composer; no legacy generator or coaching validator was used.",
                "The calendar preserves selected long-run days, explicit weekday assignments and session locks, then checks hard-session spacing and the daily time budget.",
                "A same-day pairing is permitted only for an easy run and a calisthenics session when double days are explicitly allowed.",
                "This is a static review draft. It does not adapt training load from completed or missed sessions. Review all sessions before explicit approval."
            ),
            name = "Engine 1.5 independent combined plan"
        )
    }

    fun validateSaved(plan: PlanSnapshot) {
        val request = plan.request
        val active = disciplines.filter { Engine1Engines.count(request, it) > 0 }
        validateWeeklySlots(request, active)
        val expected = request.weeks * active.sumOf { Engine1Engines.count(request, it) }
        if (plan.engineVersion != ENGINE15_VERSION || plan.policyId != ENGINE15_INDEPENDENT_COMBINED_POLICY ||
            active.size < 2 || plan.sessions.size != expected || plan.sessions.any { session ->
                val match = Regex(".*-w([1-9][0-9]*)-(run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(session.id)
                val week = match?.groupValues?.get(1)?.toIntOrNull()
                val discipline = match?.groupValues?.get(2)
                val slot = match?.groupValues?.get(3)?.toIntOrNull()
                week == null || week !in 1..request.weeks || discipline == null || discipline !in active || slot == null ||
                    slot >= Engine1Engines.count(request, discipline.orEmpty()) || session.discipline != discipline ||
                    session.date !in request.start.plusWeeks((week - 1).toLong())..request.start.plusWeeks(week.toLong()).minusDays(1) ||
                    session.date.dayOfWeek.value !in request.days || session.quality || session.distanceKm?.let { !it.isFinite() || it <= 0.0 } == true
            } || plan.sessions.map { it.id }.distinct().size != expected)
            throw PlanConstraint("The saved independent combined plan no longer matches its discipline and calendar contract.")
        for (week in 1..request.weeks) for (discipline in active) {
            val sessions = plan.sessions.filter { session ->
                val match = Regex(".*-w([1-9][0-9]*)-(run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(session.id)
                match?.groupValues?.get(1)?.toIntOrNull() == week && match.groupValues[2] == discipline
            }
            if (sessions.size != Engine1Engines.count(request, discipline) ||
                sessions.mapNotNull { Regex(".*-(?:run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(it.id)?.groupValues?.get(1)?.toIntOrNull() }.distinct().size != sessions.size)
                throw PlanConstraint("The saved independent combined plan is missing a discipline slot.")
            val assignedDays = request.weeklySlots[discipline]
            if (assignedDays != null && sessions.any { session -> assignedDays.getOrNull(slotIndex(session)) != session.date.dayOfWeek.value })
                throw PlanConstraint("The saved independent combined plan no longer matches its fixed weekday assignments.")
            if (discipline == "run" && sessions.any { it.kind == "long" && it.date.dayOfWeek.value != request.longDay })
                throw PlanConstraint("The saved independent combined plan moved the long run away from its selected weekday.")
        }
        val scheduled = plan.sessions.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        validateCalendar(request, scheduled)
        Engine15SessionLocks.validateOutput(request, scheduled)
        active.forEach { discipline ->
            val candidate = plan.copy(
                policyId = when (discipline) {
                    "run" -> ENGINE15_INDEPENDENT_RUNNING_POLICY
                    "str" -> ENGINE15_INDEPENDENT_STRENGTH_POLICY
                    "cal" -> ENGINE15_INDEPENDENT_CALISTHENICS_POLICY
                    else -> ENGINE15_INDEPENDENT_CONDITIONING_POLICY
                },
                request = soloRequest(request, discipline),
                sessions = scheduled.filter { it.discipline == discipline }
            )
            when (discipline) {
                "run" -> Engine15IndependentPlanGenerator.validateSaved(candidate)
                "str" -> Engine15StrengthWorkoutComposer.validateSaved(candidate, allowCalendarReschedule = true)
                "cal" -> Engine15CalisthenicsWorkoutComposer.validateSaved(candidate, allowCalendarReschedule = true)
                "cond" -> Engine15ConditioningWorkoutComposer.validateSaved(candidate, allowCalendarReschedule = true)
            }
        }
    }

    fun conflict(a: PlannedSession, b: PlannedSession, request: PlanRequest): Boolean =
        if (a.date == b.date) !sameDayCompatible(a, b, request)
        else Engine15IndependentPlanGenerator.conflict(a, b)

    private fun schedule(request: PlanRequest, sessions: List<PlannedSession>): List<PlannedSession> {
        validateWeeklySlots(request, disciplines.filter { Engine1Engines.count(request, it) > 0 })
        val placed = mutableListOf<PlannedSession>()
        repeat(request.weeks) { weekIndex ->
            val first = request.start.plusWeeks(weekIndex.toLong())
            val last = first.plusDays(6)
            val weekSessions = sessions.filter { it.date in first..last }.sortedWith(
                compareBy<PlannedSession> { request.lockedSessionDays[it.id] == null }
                    .thenBy { if (it.kind == "long") 0 else if (isHard(it)) 1 else 2 }
                    .thenBy { it.id }
            )
            val weekPlaced = mutableListOf<PlannedSession>()
            var attempts = 0
            fun assign(index: Int): Boolean {
                if (index == weekSessions.size) return true
                if (++attempts > 100_000) return false
                val template = weekSessions[index]
                val locked = request.lockedSessionDays[template.id]?.let(LocalDate::parse)
                val fixedDay = request.weeklySlots[template.discipline]?.getOrNull(slotIndex(template))
                val eligible = (0L..6L).map(first::plusDays).filter { date ->
                    date.dayOfWeek.value in request.days && (locked == null || locked == date) &&
                        (fixedDay == null || fixedDay == date.dayOfWeek.value) &&
                        (template.kind != "long" || date.dayOfWeek.value == request.longDay)
                }.sortedWith(compareBy<LocalDate> { date ->
                    if ((placed + weekPlaced).none { it.date == date }) 0 else 1
                }.thenBy { it })
                for (date in eligible) {
                    val candidate = template.copy(date = date)
                    val sameDay = (placed + weekPlaced).filter { it.date == date }
                    if (sameDay.size >= if (request.allowDoubleDays) 2 else 1) continue
                    if (sameDay.any { !sameDayCompatible(candidate, it, request) }) continue
                    if (request.timeBudgetEnabled && sameDay.sumOf { it.effectiveMinutes } + candidate.effectiveMinutes > request.dailyMinutes) continue
                    if ((placed + weekPlaced).any { it.date != date && Engine15IndependentPlanGenerator.conflict(candidate, it) }) continue
                    weekPlaced += candidate
                    if (assign(index + 1)) return true
                    weekPlaced.removeAt(weekPlaced.lastIndex)
                }
                return false
            }
            if (!assign(0)) throw PlanConstraint(
                "ENGINE15_COMBINED_SCHEDULE_CONFLICT: Requested sessions, weekday assignments, locks, spacing or daily time limits do not fit; no session was dropped."
            )
            placed += weekPlaced
        }
        val result = placed.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        validateCalendar(request, result)
        return result
    }

    private fun validateCalendar(request: PlanRequest, sessions: List<PlannedSession>) {
        for (index in sessions.indices) for (other in index + 1 until sessions.size) {
            val a = sessions[index]
            val b = sessions[other]
            if (a.date == b.date) {
                if (!sameDayCompatible(a, b, request)) throw PlanConstraint("ENGINE15_COMBINED_SCHEDULE_CONFLICT: Same-day sessions violate the explicit double-day rule.")
            } else if (Engine15IndependentPlanGenerator.conflict(a, b))
                throw PlanConstraint("ENGINE15_COMBINED_SCHEDULE_CONFLICT: Sessions violate Engine 1.5 hard-session spacing.")
        }
        if (request.timeBudgetEnabled && sessions.groupBy { it.date }.values.any { it.sumOf(PlannedSession::effectiveMinutes) > request.dailyMinutes })
            throw PlanConstraint("ENGINE15_COMBINED_DAILY_LIMIT: The scheduled sessions exceed the selected daily time budget.")
    }

    private fun sameDayCompatible(a: PlannedSession, b: PlannedSession, request: PlanRequest): Boolean =
        request.allowDoubleDays && setOf(a.discipline, b.discipline) == setOf("run", "cal") &&
            listOf(a, b).any { it.discipline == "run" && it.kind == "easy" && !it.quality } &&
            listOf(a, b).any { it.discipline == "cal" && !it.lower }

    private fun isHard(session: PlannedSession) = session.quality || session.kind == "long" ||
        session.discipline == "cal" || session.discipline in setOf("str", "cond") && session.lower

    private fun slotIndex(session: PlannedSession): Int {
        val suffix = Regex(".*-(?:run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(session.id)
            ?: throw PlanConstraint("ENGINE15_COMBINED_SLOT_ASSIGNMENT_INVALID: A session ID does not identify a discipline slot.")
        return suffix.groupValues[1].toInt()
    }

    private fun validateWeeklySlots(request: PlanRequest, active: List<String>) {
        active.forEach { discipline ->
            val assigned = request.weeklySlots[discipline] ?: return@forEach
            val count = Engine1Engines.count(request, discipline)
            if (assigned.size != count || assigned.any { it !in request.days } || assigned.distinct().size != count ||
                discipline == "run" && assigned.firstOrNull() != request.longDay)
                throw PlanConstraint("ENGINE15_COMBINED_SLOT_ASSIGNMENT_INVALID: Each fixed discipline slot needs a distinct available weekday and running slot one must match the long-run day.")
        }
        request.weeklySlots.filterKeys { it in active }.flatMap { (discipline, days) ->
            days.map { it to discipline }
        }.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }.forEach { (day, pair) ->
            val allowedEasyRunPair = request.allowDoubleDays && day != request.longDay &&
                pair.size == 2 && pair.toSet() == setOf("run", "cal")
            if (!allowedEasyRunPair)
                throw PlanConstraint("ENGINE15_COMBINED_FIXED_DOUBLE_DAY_UNSUPPORTED: Explicit weekday assignments overlap; only an easy run and calisthenics may share a day with double days enabled.")
        }
    }

    private fun soloRequest(request: PlanRequest, discipline: String) = request.copy(
        goal = Engine1Engines.goal(request, discipline),
        runDays = if (discipline == "run") request.runDays else 0,
        strengthDays = if (discipline == "str") request.strengthDays else 0,
        calDays = if (discipline == "cal") request.calDays else 0,
        conditioningDays = if (discipline == "cond") request.conditioningDays else 0,
        weeklySlots = request.weeklySlots.filterKeys { it == discipline },
        lockedSessionDays = request.lockedSessionDays.filterKeys { Regex(".*-$discipline(0|[1-9][0-9]*)$").matches(it) },
        allowDoubleDays = discipline != "run" && request.allowDoubleDays,
        disciplineGoals = emptyMap()
    )

}
