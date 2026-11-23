package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.ceil

/** Engine 1.5-owned workout policy over the shared educational exercise records. */
internal object Engine15StrengthWorkoutComposer {
    private val exerciseIds = mapOf(
        "squat" to listOf("gs", "e1", "sq", "sit-to-stand"),
        "hinge" to listOf("dh", "e3", "hg", "glute-bridge"),
        "horizontal_press" to listOf("fp", "bp", "e4", "ip", "wall-push"),
        "horizontal_pull" to listOf("dr", "br", "cable-latpulldown"),
        "core" to listOf("plank", "side-plank", "bird-dog", "fc")
    )
    private val allowedExerciseIds = exerciseIds.values.flatten().toSet()

    fun compose(
        request: PlanRequest,
        id: String,
        library: ExerciseLibraryV2,
        allowCalendarReschedule: Boolean = false
    ): List<PlannedSession> {
        validateRequest(request)
        if (Engine15DisciplineProgression.supports(request, "str"))
            return Engine15DisciplineProgression.compose(request, id, "str", allowCalendarReschedule)
        val equipment = equipmentTokens(request)
        val selected = exerciseIds.map { (pattern, ids) ->
            val record = ids.asSequence().mapNotNull(library::find).firstOrNull { exercise ->
                exercise.id in allowedExerciseIds && exercise.kind == "exercise" &&
                    (exercise.difficulty == "foundation" || request.strengthExperience >= 1 && exercise.difficulty == "developing") && exercise.prerequisites.isEmpty() &&
                    pattern in exercise.movementPatterns &&
                    exercise.equipmentOptions.any(equipment::containsAll) &&
                    (exercise.id !in setOf("fp", "bp") || (request.equipment["db"] ?: 0) >= 2) &&
                    exercise.id !in request.excluded
            } ?: throw PlanConstraint(
                "STRENGTH_EXERCISE_UNAVAILABLE: No reviewed $pattern movement fits the selected equipment, exclusions and current exercise prerequisites."
            )
            val option = record.equipmentOptions.first(equipment::containsAll)
            pattern to (record to option)
        }.toMap()

        val explicit = request.weeklySlots["str"]
        val weekdays = explicit ?: spacedWeekdays(request.days, request.strengthDays)
        if (weekdays.size != request.strengthDays || weekdays.any { it !in request.days } ||
            weekdays.distinct().size != weekdays.size)
            throw PlanConstraint("STRENGTH_WEEKDAY_ASSIGNMENTS_INVALID: Each strength session needs a distinct available weekday.")

        val locked = request.lockedSessionDays.mapNotNull { (sessionId, rawDate) ->
            val match = Regex(".*-w([1-9][0-9]*)-str(0|[1-9][0-9]*)$").matchEntire(sessionId)
                ?: throw PlanConstraint("STRENGTH_SESSION_LOCK_UNSUPPORTED: A lock must identify an Engine 1.5 strength week and slot.")
            (match.groupValues[1].toInt() to match.groupValues[2].toInt()) to LocalDate.parse(rawDate)
        }.toMap()
        if (locked.size != request.lockedSessionDays.size ||
            locked.keys.any { (week, slot) -> week !in 1..request.weeks || slot !in 0 until request.strengthDays })
            throw PlanConstraint("STRENGTH_SESSION_LOCK_UNSUPPORTED: A lock identifies a duplicate or unavailable plan slot.")

        val (firstReps, maxReps, repStep) = when (request.goal) {
            "General strength", "General fitness" -> Triple(8, 10, 1)
            "Muscle development" -> Triple(8, 12, 2)
            "Strength endurance" -> Triple(10, 15, 2)
            else -> throw PlanConstraint("STRENGTH_GOAL_UNSUPPORTED: Choose a supported strength goal.")
        }
        val sessions = buildList {
            repeat(request.weeks) { weekIndex ->
                val weekStart = request.start.plusDays(weekIndex * 7L)
                val weekStartIso = weekStart.dayOfWeek.value
                val weekReps = if (weekIndex % 4 == 3) firstReps else
                    (firstReps + weekIndex % 4 * repStep).coerceAtMost(maxReps)
                val sets = if (weekIndex % 4 == 3 || request.strengthExperience == 0 || request.returning && weekIndex < 2) 1 else 2
                repeat(request.strengthDays) { slot ->
                    val day = weekdays[slot]
                    val date = weekStart.plusDays(((day - weekStartIso + 7) % 7).toLong())
                    val lock = locked[weekIndex + 1 to slot]
                    if (lock != null && ((!allowCalendarReschedule && lock != date) || lock.dayOfWeek.value !in request.days))
                        throw PlanConstraint("STRENGTH_SESSION_LOCK_CONFLICT: A locked date conflicts with its fixed weekday or plan week.")
                    val exercises = selected.values.map { (record, equipmentOption) ->
                        val detail = equipmentOption.sorted().joinToString(" + ") { it.replace('_', ' ') }.ifBlank { "No equipment" }
                        val technique = record.technique.firstOrNull().orEmpty()
                        val caution = record.caveats.firstOrNull().orEmpty()
                        ExercisePrescription(
                            id = record.id,
                            name = record.name,
                            sets = sets,
                            reps = weekReps,
                            rest = 75,
                            equipment = detail,
                            cue = listOf(technique, caution,
                                "Keep at least 3 controlled repetitions in reserve; no automatic load increase.")
                                .filter { it.isNotBlank() }.joinToString(" ")
                        )
                    }
                    val minutes = resistanceMinutes(exercises)
                    if (request.timeBudgetEnabled && minutes > request.minutes)
                        throw PlanConstraint("STRENGTH_TIME_BUDGET_CONFLICT: The reviewed strength workout does not fit the entered session limit.")
                    val sessionId = request.lockedSessionDays.keys.firstOrNull { key ->
                        Regex(".*-w([1-9][0-9]*)-str(0|[1-9][0-9]*)$").matchEntire(key)?.let {
                            it.groupValues[1].toInt() == weekIndex + 1 && it.groupValues[2].toInt() == slot
                        } == true
                    } ?: "$id-w${weekIndex + 1}-str$slot"
                    add(PlannedSession(
                        id = sessionId,
                        date = date,
                        discipline = "str",
                        title = when (request.goal) {
                            "Muscle development" -> "Strength · muscle development"
                            "Strength endurance" -> "Strength · endurance"
                            else -> "Strength · general"
                        },
                        minutes = minutes,
                        distanceKm = null,
                        kind = "strength",
                        lower = true,
                        exercises = exercises,
                        steps = listOf("5 min comfortable warm-up", "Complete each exercise with controlled repetitions and the listed rest", "3 min comfortable finish"),
                        reason = if (weekIndex % 4 == 3)
                            "Planned recovery week: one set per movement. Repetition progression restarts next week."
                        else "Static ${request.goal.lowercase()} progression: the repetition target rises by week within its goal range. Keep 3 repetitions in reserve; load is not automatically increased."
                    ))
                }
            }
        }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        if (!allowCalendarReschedule) Engine15SessionLocks.validateOutput(request, sessions)
        if (sessions.size != request.weeks * request.strengthDays || sessions.map { it.date }.distinct().size != sessions.size)
            throw PlanConstraint("STRENGTH_PLAN_INCOMPLETE: Every strength slot must have one distinct dated workout.")
        return sessions
    }

    fun validateRequest(request: PlanRequest) {
        if (Engine15DisciplineProgression.supports(request, "str"))
            return Engine15DisciplineProgression.validateRequest(request, "str")
        if (request.runDays > 0 || request.calDays > 0 || request.conditioningDays > 0)
            throw PlanConstraint("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT: Strength combined with other disciplines is deferred until Engine 1.5 static multi-discipline scheduling is available.")
        if (request.policyChoice != "engine15" || request.runDays != 0 || request.strengthDays !in 1..4 ||
            request.calDays != 0 || request.conditioningDays != 0 || request.weeks !in 1..16 ||
            request.goal !in setOf("General strength", "General fitness", "Muscle development", "Strength endurance") ||
            request.qualityCount != 0 || request.days.isEmpty() || request.days.any { it !in 1..7 } ||
            request.days.size < request.strengthDays || request.strengthExperience !in 0..2 ||
            (request.timeBudgetEnabled && request.minutes !in 15..120) ||
            request.equipment.values.any { it !in 0..20 } || request.disciplineGoals.keys.any { it != "str" } ||
            request.lockedSessionDays.keys.any { !Regex(".*-w([1-9][0-9]*)-str(0|[1-9][0-9]*)$").matches(it) })
            throw PlanConstraint("STRENGTH_REQUEST_UNSUPPORTED: Choose one to four strength sessions, a supported goal, valid available weekdays and no unsupported discipline.")
        if (request.weeklySlots.keys.any { it != "str" })
            throw PlanConstraint("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT: Combined workout construction and scheduling are still deferred.")
        if (request.limitations.isNotBlank() || request.recoveryNotes.isNotBlank())
            throw PlanConstraint("STRENGTH_NOTES_REQUIRE_REVIEW: Free-text limitations or recovery notes need coach review before a strength plan can be generated.")
        if (request.weeklySlots["str"]?.let { days ->
                days.size != request.strengthDays || days.any { it !in request.days } || days.distinct().size != days.size
            } == true)
            throw PlanConstraint("STRENGTH_WEEKDAY_ASSIGNMENTS_INVALID: Each strength session needs a distinct available weekday.")
        Engine15SessionLocks.validateRequest(request)
    }

    fun validateSaved(plan: PlanSnapshot, allowCalendarReschedule: Boolean = false) {
        if (Engine15DisciplineProgression.supports(plan.request, "str"))
            return Engine15DisciplineProgression.validateSaved(plan, "str", allowCalendarReschedule)
        validateRequest(plan.request)
        val expectedCount = plan.request.weeks * plan.request.strengthDays
        val (firstReps, maxReps, repStep) = when (plan.request.goal) {
            "General strength", "General fitness" -> Triple(8, 10, 1)
            "Muscle development" -> Triple(8, 12, 2)
            "Strength endurance" -> Triple(10, 15, 2)
            else -> throw PlanConstraint("The saved independent strength plan has an unsupported goal.")
        }
        val weekdays = plan.request.weeklySlots["str"] ?: spacedWeekdays(plan.request.days, plan.request.strengthDays)
        if (plan.sessions.size != expectedCount || plan.sessions.any { session ->
                val match = Regex(".*-w([1-9][0-9]*)-str(0|[1-9][0-9]*)$").matchEntire(session.id)
                val week = match?.groupValues?.get(1)?.toIntOrNull()
                val slot = match?.groupValues?.get(2)?.toIntOrNull()
                val expectedDate = if (week != null && week in 1..plan.request.weeks && slot != null && slot in weekdays.indices) {
                    val start = plan.request.start.plusWeeks((week - 1).toLong())
                    start.plusDays(((weekdays[slot] - start.dayOfWeek.value + 7) % 7).toLong())
                } else null
                val expectedReps = if (week != null && (week - 1) % 4 != 3)
                    (firstReps + (week - 1) % 4 * repStep).coerceAtMost(maxReps) else firstReps
                val expectedSets = if (week != null && ((week - 1) % 4 == 3 || plan.request.strengthExperience == 0 || plan.request.returning && week <= 2)) 1 else 2
                val base = session.baselineSession()
                base.discipline != "str" || base.kind != "strength" || base.quality || !base.lower ||
                    base.exercises.size != exerciseIds.size || base.exercises.any { it.id !in allowedExerciseIds } ||
                    base.exercises.map { it.id }.distinct().size != exerciseIds.size ||
                    base.exercises.any { it.reps != expectedReps || it.sets != expectedSets } ||
                    !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(session, plan.request) ||
                    expectedDate == null || !allowCalendarReschedule && session.date != expectedDate ||
                    session.date.isBefore(plan.request.start) ||
                    !session.date.isBefore(plan.request.start.plusWeeks(plan.request.weeks.toLong())) ||
                    session.date.dayOfWeek.value !in plan.request.days
            } || plan.sessions.map { it.id }.distinct().size != expectedCount ||
            plan.sessions.map { it.date }.distinct().size != expectedCount)
            throw PlanConstraint("The saved independent strength plan no longer matches its supported exercise and calendar contract.")
        Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
    }

    private fun equipmentTokens(request: PlanRequest) = buildSet {
        if ((request.equipment["db"] ?: 0) > 0) add("dumbbells")
        if ((request.equipment["bb"] ?: 0) > 0) add("barbell")
        if ((request.equipment["rack"] ?: 0) > 0) addAll(setOf("rack", "safeties"))
        if ((request.equipment["bench"] ?: 0) > 0) add("bench")
        if ((request.equipment["bands"] ?: 0) > 0) add("band")
        if ((request.equipment["anchor"] ?: 0) > 0) add("anchor")
        if ((request.equipment["support"] ?: 0) > 0) add("raised_surface")
    }

    private fun spacedWeekdays(available: Set<Int>, count: Int): List<Int> {
        if (available.size < count) throw PlanConstraint("STRENGTH_AVAILABILITY_UNSUPPORTED: Not enough available days for the requested strength sessions.")
        val choices = available.sorted().toList()
        val combinations = mutableListOf<List<Int>>()
        fun select(from: Int, chosen: List<Int>) {
            if (chosen.size == count) { combinations += chosen; return }
            for (index in from until choices.size) select(index + 1, chosen + choices[index])
        }
        select(0, emptyList())
        return combinations.maxWithOrNull(compareBy<List<Int>> { days ->
            (days.zipWithNext().map { (a, b) -> b - a } + (days.first() + 7 - days.last()))
                .minOrNull() ?: 7
        }.thenBy { days -> days.last() - days.first() })!!
    }

    private fun resistanceMinutes(exercises: List<ExercisePrescription>) = ceil(8 + exercises.sumOf {
        (it.sets * (it.reps ?: 0) * 3 + (it.sets - 1) * it.rest) / 60.0 + 1
    }).toInt()
}
