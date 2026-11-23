package io.stridelab.privatealpha

import java.time.LocalDate

/** Engine 1.5 low-impact conditioning dose over the shared educational exercise record. */
internal object Engine15ConditioningWorkoutComposer {
    private const val EXERCISE_ID = "cond-step-jack"

    fun validateRequest(request: PlanRequest) {
        if (Engine15DisciplineProgression.supports(request, "cond"))
            return Engine15DisciplineProgression.validateRequest(request, "cond")
        if (request.runDays > 0 || request.strengthDays > 0 || request.calDays > 0)
            throw PlanConstraint("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT: Conditioning combined with other disciplines is deferred until Engine 1.5 static multi-discipline scheduling is available.")
        if (request.policyChoice != "engine15" || request.conditioningDays !in 1..3 || request.weeks !in 1..16 ||
            request.disciplineGoal("cond") != "Conditioning" || request.days.isEmpty() ||
            request.days.any { it !in 1..7 } || request.days.size < request.conditioningDays ||
            request.qualityCount != 0 || (request.timeBudgetEnabled && request.minutes !in 15..120) ||
            request.disciplineGoals.keys.any { it != "cond" } || request.weeklySlots.keys.any { it != "cond" } ||
            request.lockedSessionDays.keys.any { !Regex(".*-w([1-9][0-9]*)-cond(0|[1-9][0-9]*)$").matches(it) })
            throw PlanConstraint("CONDITIONING_REQUEST_UNSUPPORTED: Choose one to three low-impact conditioning sessions, valid weekdays and a 15–120 minute session limit.")
        if (request.limitations.isNotBlank() || request.recoveryNotes.isNotBlank())
            throw PlanConstraint("CONDITIONING_NOTES_REQUIRE_REVIEW: Free-text limitations or recovery notes need coach review before generation.")
        if (EXERCISE_ID in request.excluded)
            throw PlanConstraint("CONDITIONING_EXERCISE_UNAVAILABLE: The supported low-impact conditioning movement is excluded and no substitute is qualified.")
        val assigned = request.weeklySlots["cond"]
        if (assigned != null && (assigned.size != request.conditioningDays || assigned.any { it !in request.days } || assigned.distinct().size != assigned.size))
            throw PlanConstraint("CONDITIONING_WEEKDAY_ASSIGNMENTS_INVALID: Assign each session to a distinct available weekday.")
        val locks = lockSlots(request)
        if (locks.size != request.lockedSessionDays.size || locks.keys.any { (week, slot) -> week !in 1..request.weeks || slot !in 0 until request.conditioningDays })
            throw PlanConstraint("CONDITIONING_SESSION_LOCK_UNSUPPORTED: A lock identifies a duplicate or unavailable plan slot.")
        Engine15SessionLocks.validateRequest(request)
    }

    fun compose(request: PlanRequest, id: String, library: ExerciseLibraryV2, allowCalendarReschedule: Boolean = false): List<PlannedSession> {
        validateRequest(request)
        if (Engine15DisciplineProgression.supports(request, "cond"))
            return Engine15DisciplineProgression.compose(request, id, "cond", allowCalendarReschedule)
        val exercise = library.find(EXERCISE_ID)?.takeIf {
            it.kind == "exercise" && it.difficulty == "foundation" && it.prerequisites.isEmpty() &&
                it.movementPatterns.contains("stepping") && it.equipmentOptions.any { option -> option.isEmpty() }
        } ?: throw PlanConstraint("CONDITIONING_CATALOG_UNAVAILABLE: No reviewed low-impact stepping movement is available; no substitute was generated.")
        val weekdays = request.weeklySlots["cond"] ?: spacedWeekdays(request.days, request.conditioningDays)
        val locks = lockSlots(request)
        val sessions = buildList {
            repeat(request.weeks) { weekIndex ->
                val weekStart = request.start.plusWeeks(weekIndex.toLong())
                val inCycle = weekIndex % 4
                val recovery = inCycle == 3
                val duration = if (recovery) 15 else minOf(21, 15 + 2 * inCycle)
                val minutes = minOf(duration, if (request.timeBudgetEnabled) request.minutes else duration)
                repeat(request.conditioningDays) { slot ->
                    val date = weekStart.plusDays(((weekdays[slot] - weekStart.dayOfWeek.value + 7) % 7).toLong())
                    val lock = locks[weekIndex + 1 to slot]
                    if (lock != null && !allowCalendarReschedule && lock != date)
                        throw PlanConstraint("CONDITIONING_SESSION_LOCK_CONFLICT: A locked date conflicts with its assigned weekday or plan week.")
                    val sessionId = request.lockedSessionDays.keys.firstOrNull { key ->
                        Regex(".*-w([1-9][0-9]*)-cond(0|[1-9][0-9]*)$").matchEntire(key)?.let {
                            it.groupValues[1].toInt() == weekIndex + 1 && it.groupValues[2].toInt() == slot
                        } == true
                    } ?: "$id-w${weekIndex + 1}-cond$slot"
                    add(PlannedSession(
                        id = sessionId, date = date, discipline = "cond", title = "Conditioning · low impact",
                        minutes = minutes, distanceKm = null, kind = "conditioning", lower = true,
                        exercises = listOf(ExercisePrescription(
                            id = exercise.id, name = exercise.name, sets = 1, seconds = minutes * 60, rest = 0,
                            equipment = "None",
                            cue = "Keep the movement comfortable and low impact. Use a conversational effort, step instead of jumping, and take easy breaks. ${exercise.caveats.firstOrNull().orEmpty()}"
                        )),
                        steps = listOf(
                            "5 min easy walking or marching",
                            "${minutes - 10} min comfortable low-impact step jacks or easy marching; take breaks to stay conversational",
                            "5 min easy walking or marching"
                        ),
                        reason = if (recovery) "Planned recovery week: conditioning time returns to 15 minutes." else "Static low-impact conditioning progression; duration rises within a four-week block, with no effort escalation or feedback-based adjustment."
                    ))
                }
            }
        }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        if (!allowCalendarReschedule) Engine15SessionLocks.validateOutput(request, sessions)
        if (sessions.size != request.weeks * request.conditioningDays || sessions.map { it.date }.distinct().size != sessions.size)
            throw PlanConstraint("CONDITIONING_PLAN_INCOMPLETE: Every requested slot must have one distinct dated workout.")
        return sessions
    }

    fun validateSaved(plan: PlanSnapshot, allowCalendarReschedule: Boolean = false) {
        if (Engine15DisciplineProgression.supports(plan.request, "cond"))
            return Engine15DisciplineProgression.validateSaved(plan, "cond", allowCalendarReschedule)
        validateRequest(plan.request)
        val count = plan.request.weeks * plan.request.conditioningDays
        val weekdays = plan.request.weeklySlots["cond"] ?: spacedWeekdays(plan.request.days, plan.request.conditioningDays)
        if (plan.sessions.size != count || plan.sessions.any {
                val match = Regex(".*-w([1-9][0-9]*)-cond(0|[1-9][0-9]*)$").matchEntire(it.id)
                val week = match?.groupValues?.get(1)?.toIntOrNull()
                val slot = match?.groupValues?.get(2)?.toIntOrNull()
                val expectedDate = if (week != null && week in 1..plan.request.weeks && slot != null && slot in weekdays.indices) {
                    val firstDay = plan.request.start.plusWeeks((week - 1).toLong())
                    firstDay.plusDays(((weekdays[slot] - firstDay.dayOfWeek.value + 7) % 7).toLong())
                } else null
                val expectedMinutes = if (week != null) minOf(if ((week - 1) % 4 == 3) 15 else minOf(21, 15 + 2 * ((week - 1) % 4)), if (plan.request.timeBudgetEnabled) plan.request.minutes else 21) else 0
                val base = it.baselineSession()
                base.discipline != "cond" || base.kind != "conditioning" || !base.lower || base.quality ||
                    base.distanceKm != null || base.minutes != expectedMinutes || expectedDate == null || !allowCalendarReschedule && it.date != expectedDate ||
                    base.exercises.singleOrNull()?.let { exercise -> exercise.id != EXERCISE_ID || exercise.seconds != expectedMinutes * 60 || exercise.sets != 1 } != false ||
                    !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(it, plan.request)
            } || plan.sessions.map { it.id }.distinct().size != count || plan.sessions.map { it.date }.distinct().size != count)
            throw PlanConstraint("The saved independent conditioning plan no longer matches its supported workout and calendar contract.")
        Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
    }

    private fun lockSlots(request: PlanRequest): Map<Pair<Int, Int>, LocalDate> = request.lockedSessionDays.map { (key, date) ->
        val match = Regex(".*-w([1-9][0-9]*)-cond(0|[1-9][0-9]*)$").matchEntire(key)
            ?: throw PlanConstraint("CONDITIONING_SESSION_LOCK_UNSUPPORTED: A lock must identify an Engine 1.5 conditioning week and slot.")
        (match.groupValues[1].toInt() to match.groupValues[2].toInt()) to LocalDate.parse(date)
    }.toMap()

    private fun spacedWeekdays(available: Set<Int>, count: Int): List<Int> {
        val days = available.sorted()
        if (days.size < count) throw PlanConstraint("CONDITIONING_AVAILABILITY_UNSUPPORTED: Not enough available days for the requested sessions.")
        val options = mutableListOf<List<Int>>()
        fun choose(from: Int, selected: List<Int>) {
            if (selected.size == count) { options += selected; return }
            for (index in from until days.size) choose(index + 1, selected + days[index])
        }
        choose(0, emptyList())
        return options.maxWithOrNull(compareBy<List<Int>> { slots ->
            (slots.zipWithNext().map { (a, b) -> b - a } + (slots.first() + 7 - slots.last())).minOrNull() ?: 7
        }.thenBy { it.last() - it.first() })!!
    }
}
