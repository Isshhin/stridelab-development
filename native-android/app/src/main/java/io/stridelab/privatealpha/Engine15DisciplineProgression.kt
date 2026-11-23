package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import java.time.temporal.ChronoUnit

/** Named prescriptions use existing goal fields, leaving historical goal contracts intact. */
internal object Engine15DisciplineProgression {
    val goals = mapOf(
        "str" to listOf("Lower / Runner Durability", "Strength for Runners", "Upper Body & Posture", "Full Body", "Isometrics", "Plyometrics"),
        "cal" to listOf("Push", "Pull", "Core / Anti-Rotation", "Single-Leg Control", "Handstand / Inversion"),
        "cond" to listOf("Steady aerobic cross-conditioning", "EMOM work capacity", "AMRAP work capacity", "Threshold intervals")
    )

    fun supports(request: PlanRequest, discipline: String) =
        request.disciplineGoal(discipline) in goals[discipline].orEmpty()

    fun isStructured(session: PlannedSession) = session.kind.startsWith("structured-")

    fun validateRequest(request: PlanRequest, discipline: String) {
        val count = request.disciplineSessionCount(discipline)
        val assignments = request.weeklySlots[discipline]
        if (request.policyChoice != "engine15" || request.weeks !in 1..16 || count !in 1..4 ||
            request.days.size < count || request.days.any { it !in 1..7 } ||
            goals.keys.plus("run").any { it != discipline && request.disciplineSessionCount(it) != 0 } ||
            request.weeklySlots.keys.any { it != discipline } || request.disciplineGoals.keys.any { it != discipline } ||
            request.limitations.isNotBlank() || request.recoveryNotes.isNotBlank() ||
            request.strengthExperience !in 0..2 || request.calLevel !in 0..2 || request.pullLevel !in 0..2 ||
            request.coreLevel !in 0..2 || request.equipment.values.any { it !in 0..20 } ||
            (request.timeBudgetEnabled && request.minutes !in 15..120) ||
            assignments?.let { it.size != count || it.distinct().size != count || !request.days.containsAll(it) } == true
        ) throw PlanConstraint("The selected discipline requires valid weekdays, experience and time limits. Limitations or recovery notes require review.")
        if (request.disciplineGoal(discipline) == "Pull" && (request.equipment["bar"] ?: 0) < 1)
            throw PlanConstraint("Pull training requires a secure bar.")
        Engine15SessionLocks.validateRequest(request)
    }

    fun compose(request: PlanRequest, id: String, discipline: String, reschedule: Boolean): List<PlannedSession> {
        validateRequest(request, discipline)
        val count = request.disciplineSessionCount(discipline)
        val available = (0..6).map { request.start.plusDays(it.toLong()).dayOfWeek.value }.filter { it in request.days }
        val days = request.weeklySlots[discipline] ?: evenlySpaced(request.start, available, count)
        val phases = Engine15MacrocyclePeriodization.assignPhases(
            request.copy(goal = "General fitness", experience = maxOf(request.strengthExperience, request.calLevel)),
            List(request.weeks) { it % 4 == 3 }
        )
        val sessions = phases.toSortedMap().flatMap { (week, phase) ->
            val start = request.start.plusWeeks(week - 1L)
            days.mapIndexed { slot, weekday ->
                val date = start.plusDays(((weekday - start.dayOfWeek.value + 7) % 7).toLong())
                val suffix = "-w$week-$discipline$slot"
                val sessionId = request.lockedSessionDays.keys.singleOrNull { it.endsWith(suffix) } ?: "$id$suffix"
                workout(request, discipline, sessionId, date, week, phase)
            }
        }
        if (!reschedule) {
            Engine15SessionLocks.validateOutput(request, sessions)
            checkSpacing(sessions)
        }
        return sessions.sortedBy { it.date }
    }

    private fun evenlySpaced(start: LocalDate, available: List<Int>, count: Int): List<Int> {
        val offsets = available.map { (it - start.dayOfWeek.value + 7) % 7 }
        return (1 until (1 shl offsets.size)).asSequence()
            .filter { Integer.bitCount(it) == count }
            .map { mask -> offsets.filterIndexed { index, _ -> mask and (1 shl index) != 0 } }
            .maxBy { days -> (days.zipWithNext { a, b -> b - a } + (7 + days.first() - days.last())).min() }
            .map { start.plusDays(it.toLong()).dayOfWeek.value }
    }

    private fun workout(
        request: PlanRequest, discipline: String, id: String, date: LocalDate,
        week: Int, phase: Engine15MacroPhase
    ): PlannedSession {
        val goal = request.disciplineGoal(discipline)
        val recovery = phase == Engine15MacroPhase.RECOVERY
        val experienced = !request.returning && if (discipline == "str") request.strengthExperience > 0 else request.calLevel > 0
        val rung = if (recovery || request.returning) 0 else (week - 1) % 4
        val sets = if (recovery) 2 else if (experienced && phase == Engine15MacroPhase.BUILD && rung == 2) 4 else 3
        val reps = if (sets == 4) 6 else 6 + rung
        val hold = if (sets == 4) 15 else 15 + 2 * rung
        fun rep(id: String, name: String, cue: String = "Lower for 3 seconds. Leave 3 clean reps in reserve.") =
            ExercisePrescription(id, name, sets, reps = reps, rest = 60, cue = cue)
        fun timed(id: String, name: String, seconds: Int = hold, cue: String, rounds: Int = sets, rest: Int = 60) =
            ExercisePrescription(id, name, rounds, seconds = seconds, rest = rest, cue = cue)
        val lower = goal in setOf("Lower / Runner Durability", "Strength for Runners", "Full Body", "Isometrics", "Plyometrics", "Single-Leg Control")
        val quality = !recovery && goal in setOf("Plyometrics", "Threshold intervals", "EMOM work capacity", "AMRAP work capacity")
        val row = rep("prone-w", "Prone W raise", "Pause for 2 seconds with shoulders relaxed.")
        val push = rep("wall-push", "Wall push-up")
        val split = rep("split-stance-squat", "Supported split squat").copy(reps = reps * 2, cue = "$reps per side, alternating sides. Lower for 3 seconds.")
        val bridge = rep("glute-bridge", "Glute bridge")
        val exercises = when (goal) {
            "Strength for Runners" -> listOf(split, bridge, rep("standing-calf-raise", "Standing calf raise"),
                timed("side-plank", "Short lever side plank", cue = "Share the hold evenly between sides."))
            "Lower / Runner Durability" -> listOf(split, bridge, rep("standing-calf-raise", "Standing calf raise"))
            "Upper Body & Posture" -> listOf(push, row, rep("wall-slide", "Wall slide"))
            "Full Body" -> listOf(split, push, row)
            "Isometrics" -> listOf(
                timed("wall-sit", "Wall sit", cue = "Hold at moderate effort. End with about 5 seconds in reserve."),
                timed("calf-iso", "Supported single-leg calf hold", cue = "Split each hold evenly between sides. Keep 5 seconds in reserve."),
                timed("side-plank", "Short lever side plank", cue = "Split each hold evenly between sides. Keep breathing and stop before losing position.")
            )
            "Plyometrics" -> if (recovery) listOf(bridge, rep("landing-rehearsal", "Landing rehearsal without jumping")) else {
                val jumpSets = if (experienced) 3 else 2
                val contactsPerSet = if (experienced) 4 + rung else 3 + rung.coerceAtMost(2)
                listOf(
                    ExercisePrescription("pogo-hop", "Low pogo hop", jumpSets, reps = contactsPerSet, rest = 90, cue = "One landing per rep. Stop if fatigued or if landings get loud."),
                    ExercisePrescription("line-bound", "Low line bound", jumpSets, reps = contactsPerSet, rest = 90, cue = "One landing per rep. Alternate sides. Reset between bounds.")
                )
            }
            "Push" -> listOf(if (experienced) rep("incline-push", "Stable counter push-up") else push, row)
            "Pull" -> listOf(
                if (request.pullLevel == 2) rep("strict-pull", "Strict pull-up").copy(reps = 3 + rung, equipment = "Secure bar")
                else timed("active-hang", "Feet-assisted active hang", cue = "Use foot support and leave at least 5 seconds in reserve.").copy(equipment = "Secure bar"),
                row
            )
            "Core / Anti-Rotation" -> listOf(
                timed("bird-dog", "Bird dog hold", cue = "Alternate sides. Keep hips square and breathe."),
                timed("side-plank", "Short lever side plank", cue = "Split the hold between sides.")
            )
            "Single-Leg Control" -> listOf(split, rep("single-leg-hinge", "Supported single-leg hinge").copy(reps = reps * 2, cue = "$reps per side. Use a wall for balance."))
            "Handstand / Inversion" -> listOf(
                rep("quadruped-rock", "Quadruped wrist rocks"),
                timed("floor-pike", "Floor pike support", seconds = 10 + rung * 2,
                    cue = "Feet stay on the floor. Shift gently toward the hands. Stop for wrist or shoulder pain. No kick-ups.")
            )
            else -> {
                val work = when {
                    recovery -> when (goal) {
                        "EMOM work capacity" -> 25
                        "AMRAP work capacity" -> 45
                        "Threshold intervals" -> 60
                        else -> 100
                    }
                    goal == "EMOM work capacity" -> 30 + rung * 5
                    goal == "AMRAP work capacity" -> 60 + rung * 5
                    goal == "Threshold intervals" -> 90 + rung * 10
                    else -> 120 + rung * 10
                }
                val rest = when {
                    !quality -> 30
                    goal == "EMOM work capacity" -> 60 - work
                    goal == "AMRAP work capacity" -> 60
                    else -> 90
                }
                val cue = when {
                    !quality -> "Stay conversational. Step quietly and keep impact low."
                    goal == "EMOM work capacity" -> "Start each round on the minute. Use the remaining time for easy recovery."
                    goal == "AMRAP work capacity" -> "Complete as many clean, low impact repetitions as fit the work window at 6/10 effort."
                    else -> "Hold a controlled 7/10 effort. Recover with easy marching."
                }
                listOf(timed("cond-march", "Brisk march", work, cue, rest = rest),
                    timed("cond-step-jack", "Low impact step jack", work, cue, rest = rest))
            }
        }
        if (exercises.any { it.id in request.excluded }) throw PlanConstraint("The selected prescription includes an excluded movement.")
        val minutes = duration(exercises)
        if (request.timeBudgetEnabled && minutes > request.minutes)
            throw PlanConstraint("The selected prescription needs $minutes minutes per session.")
        val contacts = if (goal == "Plyometrics" && !recovery) exercises.sumOf { it.sets * (it.reps ?: 0) } else 0
        val steps = listOf("5 min easy warm-up", "Complete each movement with its listed work and rest", "3 min comfortable finish")
        return PlannedSession(id, date, discipline, goal, minutes, null,
            if (contacts > 0) "structured-plyometrics" else "structured-$discipline",
            lower = lower && !(goal == "Plyometrics" && recovery), quality = quality, exercises = exercises,
            steps = if (contacts > 0) steps + "$contacts ground contacts for the session. Skip if fatigued." else steps,
            reason = "${phase.name.lowercase().replaceFirstChar(Char::uppercase)} week. Keep effort and technique within the prescribed limits.")
    }

    fun duration(exercises: List<ExercisePrescription>): Int = ceil(8 + exercises.sumOf {
        (it.sets * (it.seconds ?: (it.reps ?: 0) * 3) + (it.sets - 1) * it.rest) / 60.0
    }).toInt()

    fun reduce(base: PlannedSession, step: Int, baseline: SessionDifficultyBaseline): PlannedSession {
        if (step > 0) throw PlanConstraint("This prescription progresses within the plan's coached limits.")
        val exercises = if (base.kind == "structured-plyometrics") {
            listOf(ExercisePrescription("glute-bridge", "Glute bridge", 2, reps = 6,
                cue = "Easy technique only. No jumps or ground contacts."))
        } else base.exercises.map { exercise ->
            exercise.copy(sets = (exercise.sets + step).coerceAtLeast(1))
        }
        return base.copy(minutes = duration(exercises), exercises = exercises, difficultyStep = step,
            difficultyBaseline = baseline, steps = listOf("5 min easy warm-up",
                "Complete the reduced sets with the listed work and rest", "3 min comfortable finish"),
            reason = "${base.reason} Reduced exposure. No missed work is added.")
    }

    fun validateSaved(plan: PlanSnapshot, discipline: String, reschedule: Boolean) {
        val expected = compose(plan.request, plan.id, discipline, reschedule).associateBy { it.id }
        if (plan.sessions.size != expected.size || plan.sessions.map { it.id }.toSet() != expected.keys)
            throw PlanConstraint("The saved plan is missing structured discipline slots.")
        plan.sessions.forEach { session ->
            val slot = expected.getValue(session.id)
            val base = session.baselineSession()
            if ((!reschedule && slot.date != session.date) ||
                base.copy(date = slot.date, reason = slot.reason, status = slot.status, log = slot.log, missedReason = slot.missedReason,
                    suggestion = slot.suggestion, linkedActivityId = slot.linkedActivityId, linkPreviousStatus = slot.linkPreviousStatus,
                    swaps = slot.swaps, logRevision = slot.logRevision, runResponse = slot.runResponse) != slot ||
                !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(session, plan.request))
                throw PlanConstraint("A saved prescription no longer matches its structured dose or calendar.")
        }
        if (!reschedule) checkSpacing(plan.sessions)
        Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
    }

    fun spacingConflict(a: PlannedSession, b: PlannedSession): Boolean {
        if (!isStructured(a) && !isStructured(b)) return false
        if (abs(ChronoUnit.DAYS.between(a.date, b.date)) >= 2) return false
        fun hard(session: PlannedSession) = session.quality || session.kind == "long" ||
            session.discipline != "run" && session.lower
        return hard(a) && hard(b)
    }

    private fun checkSpacing(sessions: List<PlannedSession>) {
        if (sessions.zipWithNext().any { (a, b) -> spacingConflict(a, b) })
            throw PlanConstraint("Leave a full day between hard lower-body, speed and high intensity sessions.")
    }
}

/** Separate builder tracks share the legacy resistance fields on disk. */
internal fun strengthTrack(request: PlanRequest): String = when (request.disciplineGoal("str")) {
    "Plyometrics" -> "plyo"
    "Isometrics" -> "iso"
    else -> "str"
}

internal fun chooseStrengthTrack(request: PlanRequest, track: String, deselect: Boolean): PlanRequest {
    val goal = when (track) {
        "plyo" -> "Plyometrics"
        "iso" -> "Isometrics"
        else -> "General strength"
    }
    return request.copy(
        goal = when {
            request.runDays > 0 -> request.goal
            deselect && request.calDays > 0 -> request.disciplineGoal("cal")
            deselect && request.conditioningDays > 0 -> request.disciplineGoal("cond")
            deselect -> "General fitness"
            else -> goal
        },
        strengthDays = if (deselect) 0 else 2,
        disciplineGoals = if (deselect) request.disciplineGoals - "str"
            else request.disciplineGoals + ("str" to goal),
        weeklySlots = request.weeklySlots - "str"
    )
}

internal fun plannedDisciplineLabel(session: PlannedSession): String =
    if (session.discipline == "str" && session.title in setOf("Plyometrics", "Isometrics", "Strength for Runners"))
        session.title else disciplineLabel(session.discipline)
