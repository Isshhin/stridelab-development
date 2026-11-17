package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.ceil

/** Engine 1.5 calisthenics selection and static dose over shared educational records. */
internal object Engine15CalisthenicsWorkoutComposer {
    private val allowed = setOf("wall-push", "e4", "ap", "e2", "br", "fc", "side-plank", "core-hollow")

    fun validateRequest(request: PlanRequest) {
        val goal = Engine1Engines.goal(request, "cal")
        if (request.runDays > 0 || request.strengthDays > 0 || request.conditioningDays > 0)
            throw PlanConstraint("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT: Calisthenics combined with other disciplines is deferred until Engine 1.5 static multi-discipline scheduling is available.")
        if (request.policyChoice != "engine15" || request.runDays != 0 || request.strengthDays != 0 ||
            request.calDays !in 1..4 || request.conditioningDays != 0 || request.weeks !in 1..16 ||
            goal !in setOf("Calisthenics foundation", "Pull-up progression", "Push-up progression", "Core strength and control", "Handstand foundations") ||
            request.days.isEmpty() || request.days.any { it !in 1..7 } || request.days.size < request.calDays ||
            request.calLevel !in 0..2 || request.pullLevel !in 0..2 || request.coreLevel !in 0..2 ||
            request.qualityCount != 0 || request.equipment.values.any { it !in 0..20 } ||
            request.disciplineGoals.keys.any { it != "cal" } || (request.timeBudgetEnabled && request.minutes !in 15..120) ||
            request.lockedSessionDays.keys.any { !Regex(".*-w([1-9][0-9]*)-cal(0|[1-9][0-9]*)$").matches(it) })
            throw PlanConstraint("CALISTHENICS_REQUEST_UNSUPPORTED: Choose a supported goal, one to four sessions, valid available weekdays and supported skill levels.")
        if (request.weeklySlots.keys.any { it != "cal" })
            throw PlanConstraint("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT: Combined workout construction and scheduling are still deferred.")
        if (request.limitations.isNotBlank() || request.recoveryNotes.isNotBlank())
            throw PlanConstraint("CALISTHENICS_NOTES_REQUIRE_REVIEW: Free-text limitations or recovery notes need coach review before generation.")
        if (goal == "Handstand foundations")
            throw PlanConstraint("CALISTHENICS_HANDSTAND_EVIDENCE_UNAVAILABLE: The shared catalog has no qualified handstand preparation prescription.")
        if (goal == "Pull-up progression" && (request.equipment["bar"] ?: 0) == 0)
            throw PlanConstraint("CALISTHENICS_PULLUP_BAR_REQUIRED: Pull-up progression requires an explicitly reported secure bar.")
        val assignments = request.weeklySlots["cal"]
        if (assignments != null && (assignments.size != request.calDays || assignments.any { it !in request.days } || assignments.distinct().size != assignments.size))
            throw PlanConstraint("CALISTHENICS_WEEKDAY_ASSIGNMENTS_INVALID: Assign each session to a distinct available weekday.")
        val locks = lockSlots(request)
        if (locks.size != request.lockedSessionDays.size || locks.keys.any { (week, slot) -> week !in 1..request.weeks || slot !in 0 until request.calDays })
            throw PlanConstraint("CALISTHENICS_SESSION_LOCK_UNSUPPORTED: A lock identifies a duplicate or unavailable plan slot.")
        Engine15SessionLocks.validateRequest(request)
    }

    fun compose(request: PlanRequest, id: String, library: ExerciseLibraryV2): List<PlannedSession> {
        validateRequest(request)
        val goal = Engine1Engines.goal(request, "cal")
        fun choose(ids: List<String>): ExerciseLibraryRecordV2 = ids.asSequence().mapNotNull(library::find).firstOrNull {
            it.id in allowed && it.id !in request.excluded && it.kind == "exercise" && it.prerequisites.isEmpty() &&
                (it.difficulty == "foundation" || request.calLevel >= 1 && it.difficulty == "developing") &&
                it.equipmentOptions.any { option -> hasEquipment(option, request) }
        } ?: throw PlanConstraint("CALISTHENICS_EXERCISE_UNAVAILABLE: No reviewed catalog movement meets the requested skill and equipment constraints.")

        val push = when (goal) {
            "Push-up progression" -> choose(if (request.calLevel == 0) listOf("e4", "wall-push") else listOf("e4"))
            "Calisthenics foundation" -> choose(if (request.calLevel == 0) listOf("e4", "wall-push") else listOf("e4"))
            else -> null
        }
        val pull = when (goal) {
            "Pull-up progression" -> choose(if (request.pullLevel == 2) listOf("e2") else listOf("ap"))
            "Calisthenics foundation" -> if ((request.equipment["bar"] ?: 0) > 0)
                choose(if (request.pullLevel == 2) listOf("e2", "ap") else listOf("ap"))
            else choose(listOf("br"))
            else -> null
        }
        val core = choose(when (request.coreLevel) {
            2 -> listOf("core-hollow", "fc")
            1 -> listOf("side-plank", "fc")
            else -> listOf("fc", "side-plank")
        })
        val pattern = when (goal) {
            "Core strength and control" -> listOf(core, choose(listOf("side-plank", "fc")))
            else -> listOfNotNull(push, pull, core)
        }.distinctBy { it.id }
        if (pattern.size < 2) throw PlanConstraint("CALISTHENICS_EXERCISE_UNAVAILABLE: At least two supported movement families are required.")

        val assignments = request.weeklySlots["cal"] ?: spacedWeekdays(request.days, request.calDays)
        val locks = lockSlots(request)
        val sessions = buildList {
            repeat(request.weeks) { weekIndex ->
                val weekStart = request.start.plusWeeks(weekIndex.toLong())
                val recovery = weekIndex % 4 == 3
                val reps = if (recovery) 6 else 6 + (weekIndex % 4).coerceAtMost(2)
                val sets = if (recovery || request.returning && weekIndex < 2) 1 else 2
                repeat(request.calDays) { slot ->
                    val date = weekStart.plusDays(((assignments[slot] - weekStart.dayOfWeek.value + 7) % 7).toLong())
                    val lock = locks[weekIndex + 1 to slot]
                    if (lock != null && lock != date)
                        throw PlanConstraint("CALISTHENICS_SESSION_LOCK_CONFLICT: A locked date conflicts with its assigned weekday or plan week.")
                    val exercises = pattern.map { record ->
                        val isHold = record.id == "core-hollow" || record.id == "side-plank"
                        ExercisePrescription(
                            id = record.id, name = record.name, sets = sets,
                            reps = if (isHold) null else reps,
                            seconds = if (isHold) if (record.id == "core-hollow") 10 + (request.coreLevel * 5) else 15 else null,
                            rest = if (record.movementPatterns.any { it == "vertical_pull" }) 90 else 60,
                            equipment = record.equipmentOptions.firstOrNull { hasEquipment(it, request) }?.joinToString(" + ").orEmpty().ifBlank { "None" },
                            cue = (record.technique.firstOrNull().orEmpty() + " " + record.caveats.firstOrNull().orEmpty() + " Keep several controlled repetitions in reserve; stop before form breaks.").trim()
                        )
                    }
                    val minutes = ceil(8 + exercises.sumOf { (it.sets * (it.seconds ?: (it.reps ?: 0) * 3) + (it.sets - 1) * it.rest) / 60.0 + 1 }).toInt()
                    if (request.timeBudgetEnabled && minutes > request.minutes)
                        throw PlanConstraint("CALISTHENICS_TIME_BUDGET_CONFLICT: The supported workout does not fit the entered session limit.")
                    val sessionId = request.lockedSessionDays.keys.firstOrNull { key ->
                        Regex(".*-w([1-9][0-9]*)-cal(0|[1-9][0-9]*)$").matchEntire(key)?.let {
                            it.groupValues[1].toInt() == weekIndex + 1 && it.groupValues[2].toInt() == slot
                        } == true
                    } ?: "$id-w${weekIndex + 1}-cal$slot"
                    add(PlannedSession(
                        id = sessionId, date = date, discipline = "cal", title = "Calisthenics · ${goal.lowercase()}",
                        minutes = minutes, distanceKm = null, kind = "calisthenics", lower = false,
                        exercises = exercises,
                        steps = listOf("5 min comfortable warm-up", "Use only the listed controlled variation; do not advance automatically", "3 min comfortable finish"),
                        reason = if (recovery) "Planned recovery week: one set per movement; return to the starting repetition target next week." else "Static skill-specific progression. Progress by repeatable control, not elapsed time; no automatic stage promotion."
                    ))
                }
            }
        }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        Engine15SessionLocks.validateOutput(request, sessions)
        if (sessions.size != request.weeks * request.calDays || sessions.map { it.date }.distinct().size != sessions.size)
            throw PlanConstraint("CALISTHENICS_PLAN_INCOMPLETE: Every requested slot must have one distinct dated workout.")
        return sessions
    }

    fun validateSaved(plan: PlanSnapshot) {
        validateRequest(plan.request)
        val count = plan.request.weeks * plan.request.calDays
        val assignments = plan.request.weeklySlots["cal"] ?: spacedWeekdays(plan.request.days, plan.request.calDays)
        if (plan.sessions.size != count || plan.sessions.any {
                val match = Regex(".*-w([1-9][0-9]*)-cal(0|[1-9][0-9]*)$").matchEntire(it.id)
                val week = match?.groupValues?.get(1)?.toIntOrNull()
                val slot = match?.groupValues?.get(2)?.toIntOrNull()
                val expectedDate = if (week != null && week in 1..plan.request.weeks && slot != null && slot in assignments.indices) {
                    val firstDay = plan.request.start.plusWeeks((week - 1).toLong())
                    firstDay.plusDays(((assignments[slot] - firstDay.dayOfWeek.value + 7) % 7).toLong())
                } else null
                val expectedReps = if (week != null && (week - 1) % 4 != 3) 6 + ((week - 1) % 4).coerceAtMost(2) else 6
                val expectedSets = if (week != null && ((week - 1) % 4 == 3 || plan.request.returning && week <= 2)) 1 else 2
                it.discipline != "cal" || it.kind != "calisthenics" || it.exercises.size < 2 ||
                    it.date.dayOfWeek.value !in plan.request.days || it.quality || it.distanceKm != null ||
                    expectedDate == null || it.date != expectedDate ||
                    it.exercises.any { exercise -> exercise.id !in allowed || exercise.id in plan.request.excluded ||
                        exercise.sets != expectedSets || (exercise.reps != null && exercise.reps != expectedReps) }
            } || plan.sessions.map { it.id }.distinct().size != count || plan.sessions.map { it.date }.distinct().size != count)
            throw PlanConstraint("The saved independent calisthenics plan no longer matches its supported workout and calendar contract.")
        Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
    }

    private fun hasEquipment(option: Set<String>, request: PlanRequest): Boolean {
        // Equipment IDs in the exercise library map to explicitly reported PlanRequest counts.
        val available = buildSet {
            if ((request.equipment["bar"] ?: 0) > 0) add("pullup_bar")
            if ((request.equipment["bands"] ?: 0) > 0) add("band")
            if ((request.equipment["anchor"] ?: 0) > 0) add("anchor")
            if ((request.equipment["wall"] ?: 0) > 0) add("wall")
        }
        return available.containsAll(option)
    }

    private fun lockSlots(request: PlanRequest): Map<Pair<Int, Int>, LocalDate> = request.lockedSessionDays.map { (key, date) ->
        val match = Regex(".*-w([1-9][0-9]*)-cal(0|[1-9][0-9]*)$").matchEntire(key)
            ?: throw PlanConstraint("CALISTHENICS_SESSION_LOCK_UNSUPPORTED: A lock must identify an Engine 1.5 calisthenics week and slot.")
        (match.groupValues[1].toInt() to match.groupValues[2].toInt()) to LocalDate.parse(date)
    }.toMap()

    private fun spacedWeekdays(available: Set<Int>, count: Int): List<Int> {
        if (available.size < count) throw PlanConstraint("CALISTHENICS_AVAILABILITY_UNSUPPORTED: Not enough available days for the requested sessions.")
        val choices = available.sorted()
        return choices.combinations(count).maxWithOrNull(compareBy<List<Int>> { days ->
            (days.zipWithNext().map { (a, b) -> b - a } + (days.first() + 7 - days.last())).minOrNull() ?: 7
        }.thenBy { it.last() - it.first() })!!
    }

    private fun <T> List<T>.combinations(count: Int): List<List<T>> {
        val result = mutableListOf<List<T>>()
        fun add(start: Int, chosen: List<T>) {
            if (chosen.size == count) { result += chosen; return }
            for (i in start until size) add(i + 1, chosen + this[i])
        }
        add(0, emptyList())
        return result
    }
}
