package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate

internal enum class SetDisposition { PENDING, COMPLETED, SKIPPED, MODIFIED }
internal enum class ExecutionLifecycle { IN_PROGRESS, PAUSED, FINISHED_EARLY, ABANDONED, FINISHED_NORMALLY }
internal enum class PrescriptionOutcome { FULLY_PERFORMED, PARTIALLY_PERFORMED, NO_WORK_PERFORMED, UNKNOWN }

internal data class WorkoutExecutionOutcome(
    val lifecycle: ExecutionLifecycle,
    val prescription: PrescriptionOutcome,
    val prescribedSets: Int,
    val confirmedSets: Int,
    val skippedSets: Int
)

internal val PlannedSession.isMarkedComplete: Boolean get() = status == "completed"
internal enum class PlayerPhase { Ready, ActiveWork, Resting, Reviewing, Completed }

internal data class SetActual(val reps: Int? = null, val kg: Double? = null, val seconds: Int? = null, val rir: Int? = null) {
    init {
        require(reps == null || reps in 0..500)
        require(kg == null || kg.isFinite() && kg >= 0)
        require(seconds == null || seconds in 0..3600)
        require(rir == null || rir in 0..10)
    }
}

internal data class ExecutionSet(
    val target: ExercisePrescription,
    val number: Int,
    val disposition: SetDisposition = SetDisposition.PENDING,
    val actual: SetActual? = null,
    val measuredWorkMs: Long? = null,
    val actualRestMs: Long? = null
) {
    val isConfirmed get() = disposition == SetDisposition.COMPLETED || disposition == SetDisposition.MODIFIED
    fun confirmed(value: SetActual): ExecutionSet {
        require(if (target.seconds != null) (value.seconds ?: 0) > 0 else (value.reps ?: 0) > 0)
        val modified = value.reps != target.reps || value.seconds != target.seconds || value.kg != null
        return copy(actual = value, disposition = if (modified) SetDisposition.MODIFIED else SetDisposition.COMPLETED)
    }
}

internal data class PlayerTimer(
    val budgetMs: Long, val remainingMs: Long = budgetMs, val deadlineMs: Long? = null
) {
    fun rawRemaining(nowMs: Long) = deadlineMs?.minus(nowMs) ?: remainingMs
    fun remaining(nowMs: Long) = rawRemaining(nowMs).coerceIn(0, budgetMs)
    fun elapsed(nowMs: Long) = budgetMs - remaining(nowMs)
    fun elapsedRest(nowMs: Long) = budgetMs - rawRemaining(nowMs)
}

internal data class WorkoutExecution(
    val executionId: String,
    val planId: String,
    val sessionId: String,
    val finalizationId: String,
    val sets: List<ExecutionSet>,
    val cursor: Int = 0,
    val phase: PlayerPhase = PlayerPhase.Ready,
    val lifecycle: ExecutionLifecycle = ExecutionLifecycle.IN_PROGRESS,
    val timer: PlayerTimer? = null,
    val finalLog: WorkoutLog? = null,
    val receipt: Boolean = false,
    val revision: Long = 0,
    val openedAtMs: Long? = null
) {
    val current get() = sets[cursor]
    val outcome: WorkoutExecutionOutcome get() {
        val confirmed = sets.count { it.isConfirmed }
        val prescription = when {
            confirmed == 0 -> PrescriptionOutcome.NO_WORK_PERFORMED
            confirmed == sets.size -> PrescriptionOutcome.FULLY_PERFORMED
            else -> PrescriptionOutcome.PARTIALLY_PERFORMED
        }
        return WorkoutExecutionOutcome(lifecycle, prescription, sets.size, confirmed,
            sets.count { it.disposition == SetDisposition.SKIPPED })
    }
    val canChangePrescription get() = lifecycle == ExecutionLifecycle.IN_PROGRESS && timer == null && finalLog == null &&
        sets.all { it.disposition == SetDisposition.PENDING && it.measuredWorkMs == null }

    fun matchesPrescribedStructure(currentTargets: WorkoutExecution): Boolean {
        val prescribedKeys = currentTargets.sets.map { it.target.id to it.number }.toSet()
        val baseFiltered = sets.filter { (it.target.id to it.number) in prescribedKeys }
        return baseFiltered.map { it.target to it.number } == currentTargets.sets.map { it.target to it.number }
    }

    companion object {
        fun create(id: String, planId: String, session: PlannedSession, finalizationId: String): WorkoutExecution {
            val sets = session.effectiveExercises.flatMap { exercise ->
                val count = if (session.suggestion?.accepted == true) maxOf(1, (exercise.sets * .7).toInt()) else exercise.sets
                (1..count).map { number ->
                    val step = ExecutionSet(exercise, number)
                    val row = session.log?.sets?.singleOrNull { it.exerciseId == exercise.id && it.set == number }
                    val actual = row?.let { SetActual(it.reps, it.kg, it.seconds) }
                    if (actual != null && (if (exercise.seconds != null) (actual.seconds ?: 0) > 0 else (actual.reps ?: 0) > 0))
                        step.confirmed(actual) else step
                }
            }
            require(sets.isNotEmpty())
            val pending = sets.indexOfFirst { it.disposition == SetDisposition.PENDING }
            return WorkoutExecution(id, planId, session.id, finalizationId, sets,
                cursor = pending.coerceAtLeast(0), phase = if (pending < 0) PlayerPhase.Reviewing else PlayerPhase.Ready)
        }
    }
}

internal sealed interface PlayerEvent {
    data object StartSession : PlayerEvent
    data object StartWorkTimer : PlayerEvent
    data object PauseTimer : PlayerEvent
    data object ResumeTimer : PlayerEvent
    data class CompleteSet(val actual: SetActual, val emom: Boolean = false) : PlayerEvent
    data object SkipSet : PlayerEvent
    data class EditPreviousSet(val index: Int, val actual: SetActual?) : PlayerEvent
    data object EmomWorkElapsed : PlayerEvent
    data object AdvanceEmom : PlayerEvent
    data object EnterRest : PlayerEvent
    data object ExtendRest : PlayerEvent
    data object SkipRest : PlayerEvent
    data object Review : PlayerEvent
    data object Continue : PlayerEvent
    data object ResetWork : PlayerEvent
    data class Browse(val direction: Int) : PlayerEvent
    data object FinishEarly : PlayerEvent
    data object AbandonSession : PlayerEvent
    data class FinalizeSession(
        val at: Instant, val effort: Int?, val notes: String,
        val performedOn: LocalDate? = null, val minutes: Double? = null, val distanceKm: Double? = null
    ) : PlayerEvent
    data class Tick(val nowMs: Long) : PlayerEvent
    data class FocusSet(val index: Int) : PlayerEvent
    data class CompleteSetAt(val index: Int, val actual: SetActual, val startRest: Boolean = true) : PlayerEvent
    data class SaveUnconfirmedPartialHold(val index: Int, val seconds: Int) : PlayerEvent
    data class SkipSetAt(val index: Int) : PlayerEvent
    data class UnconfirmSetAt(val index: Int) : PlayerEvent
    data class AddExtraSet(val exerciseId: String) : PlayerEvent
    data class StartNextSet(val exerciseId: String? = null, val autoStartTimer: Boolean = true) : PlayerEvent
}

internal fun reconcileWorkoutFinalization(plan: PlanSnapshot, sessionId: String, log: WorkoutLog): PlanSnapshot {
    val session = plan.sessions.single { it.id == sessionId }
    if (session.status == "completed") {
        if (session.log != log) throw PlanConstraint("This workout already has a different completed log.")
        return plan
    }
    return PlanOperations.record(plan, sessionId, log, complete = true)
}

internal class WorkoutPlayerEngine(private val clock: () -> Long) {
    fun reduce(state: WorkoutExecution, event: PlayerEvent): WorkoutExecution {
        if (event is PlayerEvent.Tick) return state.copy(timer = state.timer?.let {
            it.copy(remainingMs = it.remaining(event.nowMs))
        })
        if (state.phase == PlayerPhase.Completed || state.finalLog != null) return state
        val now = clock()
        val timer = state.timer
        val step = state.current
        val next = when (event) {
            PlayerEvent.StartSession -> if (state.phase == PlayerPhase.Ready) state.copy(phase = PlayerPhase.ActiveWork) else state
            PlayerEvent.StartWorkTimer -> {
                if (state.phase !in setOf(PlayerPhase.Ready, PlayerPhase.ActiveWork) || timer != null ||
                    step.disposition != SetDisposition.PENDING) return state
                val duration = step.target.seconds?.times(1000L) ?: return state
                state.copy(phase = PlayerPhase.ActiveWork, timer = PlayerTimer(duration, deadlineMs = now + duration))
            }
            PlayerEvent.PauseTimer -> {
                if (timer?.deadlineMs == null) return state
                state.copy(timer = timer.copy(remainingMs = timer.remaining(now), deadlineMs = null), lifecycle = ExecutionLifecycle.PAUSED)
            }
            PlayerEvent.ResumeTimer -> {
                if (timer == null || timer.deadlineMs != null || timer.remainingMs == 0L) return state
                state.copy(timer = timer.copy(deadlineMs = now + timer.remainingMs), lifecycle = ExecutionLifecycle.IN_PROGRESS)
            }
            is PlayerEvent.CompleteSet -> {
                val emomConfirmation = event.emom && state.phase == PlayerPhase.Resting && step.measuredWorkMs != null
                if ((!emomConfirmation && state.phase !in setOf(PlayerPhase.Ready, PlayerPhase.ActiveWork)) ||
                    step.disposition != SetDisposition.PENDING) return state
                if (emomConfirmation) state.replace(state.cursor, step.confirmed(event.actual))
                else {
                    val completed = step.confirmed(event.actual).copy(measuredWorkMs = timer?.elapsed(now))
                    // The confirmed row and its rest phase are one checkpoint.
                    val remainingWork = if (event.emom) timer?.rawRemaining(now) ?: 0L else 0L
                    enterRest(state.replace(state.cursor, completed), now, remainingWork)
                }
            }
            PlayerEvent.SkipSet -> {
                if (state.phase !in setOf(PlayerPhase.Ready, PlayerPhase.ActiveWork) ||
                    step.disposition != SetDisposition.PENDING) return state
                advance(state.replace(state.cursor, step.copy(disposition = SetDisposition.SKIPPED, measuredWorkMs = timer?.elapsed(now))))
            }
            is PlayerEvent.EditPreviousSet -> {
                val previous = state.sets.getOrNull(event.index) ?: return state
                val needsReview = state.phase == PlayerPhase.Reviewing && previous.disposition == SetDisposition.PENDING &&
                    previous.measuredWorkMs != null && event.actual != null
                if (previous.actual == null && !needsReview) return state
                if (event.actual != null) state.replace(event.index, previous.confirmed(event.actual))
                else {
                    if (state.timer != null && !(state.phase == PlayerPhase.Resting && state.cursor == event.index)) return state
                    state.replace(event.index, ExecutionSet(previous.target, previous.number)).copy(
                        cursor = event.index, phase = PlayerPhase.ActiveWork, lifecycle = ExecutionLifecycle.IN_PROGRESS, timer = null)
                }
            }
            PlayerEvent.EnterRest -> if (state.phase == PlayerPhase.ActiveWork && step.actual != null) enterRest(state, now) else state
            PlayerEvent.ExtendRest -> {
                if (state.phase != PlayerPhase.Resting || timer == null) return state
                val remaining = timer.remaining(now) + 15_000
                state.copy(timer = PlayerTimer(timer.elapsedRest(now) + remaining, remaining, timer.deadlineMs?.let { now + remaining }))
            }
            PlayerEvent.SkipRest -> if (state.phase == PlayerPhase.Resting) advance(captureRest(state, now)) else state
            PlayerEvent.EmomWorkElapsed -> {
                val deadline = timer?.deadlineMs ?: return state
                if (state.phase != PlayerPhase.ActiveWork || now < deadline || step.disposition != SetDisposition.PENDING) return state
                val measured = step.copy(measuredWorkMs = timer.elapsed(deadline))
                enterRest(state.replace(state.cursor, measured), deadline)
            }
            PlayerEvent.AdvanceEmom -> {
                val boundary = timer?.deadlineMs ?: return state
                if (state.phase != PlayerPhase.Resting || now < boundary) return state
                val onTime = now - boundary <= 1000
                val following = advance(captureRest(state, if (onTime) boundary else now), forwardOnly = true)
                val work = following.current.target.seconds?.times(1000L)
                if (following.phase == PlayerPhase.ActiveWork && work != null) {
                    // Keep normal boundary jitter out of the next round. A long absence
                    // starts a fresh full work window when the athlete returns.
                    val start = if (onTime) boundary else now
                    following.copy(timer = PlayerTimer(work, deadlineMs = start + work))
                } else following
            }
            PlayerEvent.Review, PlayerEvent.FinishEarly -> captureRest(state, now).copy(
                phase = PlayerPhase.Reviewing, timer = null,
                lifecycle = if (event == PlayerEvent.FinishEarly && state.sets.any { it.disposition == SetDisposition.PENDING }) ExecutionLifecycle.FINISHED_EARLY else ExecutionLifecycle.IN_PROGRESS
            )
            PlayerEvent.Continue -> if (state.phase == PlayerPhase.Reviewing) {
                val pending = state.sets.indexOfFirst { it.disposition == SetDisposition.PENDING }
                if (pending < 0) state else state.copy(cursor = pending, phase = PlayerPhase.ActiveWork, lifecycle = ExecutionLifecycle.IN_PROGRESS)
            } else state
            PlayerEvent.ResetWork -> if (state.phase == PlayerPhase.ActiveWork)
                state.copy(timer = null, lifecycle = ExecutionLifecycle.IN_PROGRESS) else state
            is PlayerEvent.Browse -> if (state.phase in setOf(PlayerPhase.Ready, PlayerPhase.ActiveWork)) {
                val end = if (event.direction > 0) state.sets.indices.firstOrNull { it > state.cursor && state.sets[it].target.id != step.target.id }
                    else (state.cursor - 1 downTo 0).firstOrNull { state.sets[it].target.id != step.target.id }
                val index = end ?: -1
                if (index < 0 && event.direction > 0) state.copy(phase = PlayerPhase.Reviewing, timer = null, lifecycle = ExecutionLifecycle.IN_PROGRESS)
                else if (index >= 0) state.copy(cursor = index, timer = null, lifecycle = ExecutionLifecycle.IN_PROGRESS) else state
            } else state
            PlayerEvent.AbandonSession -> captureRest(state, now).copy(phase = PlayerPhase.Completed,
                lifecycle = ExecutionLifecycle.ABANDONED, timer = null)
            is PlayerEvent.FinalizeSession -> {
                if (state.phase != PlayerPhase.Reviewing) return state
                val rows = state.sets.filter { it.isConfirmed }.mapNotNull { set ->
                    set.actual?.let { SetRecord(set.target.id, set.number, it.reps, it.kg, it.seconds) }
                }
                val lifecycle = if (state.lifecycle == ExecutionLifecycle.FINISHED_EARLY)
                    ExecutionLifecycle.FINISHED_EARLY else ExecutionLifecycle.FINISHED_NORMALLY
                val log = rows.takeIf { it.isNotEmpty() }?.let {
                    WorkoutLog(event.at, event.minutes, event.distanceKm, event.effort, it, event.notes, event.performedOn)
                }
                state.copy(lifecycle = lifecycle, finalLog = log,
                    phase = if (log == null) PlayerPhase.Completed else state.phase, receipt = log == null)
            }
            is PlayerEvent.FocusSet -> {
                if (event.index !in state.sets.indices) return state
                state.copy(
                    cursor = event.index,
                    phase = PlayerPhase.ActiveWork,
                    lifecycle = ExecutionLifecycle.IN_PROGRESS,
                    timer = null
                )
            }
            is PlayerEvent.CompleteSetAt -> {
                val targetSet = state.sets.getOrNull(event.index) ?: return state
                val updatedSet = targetSet.confirmed(event.actual)
                val nextSets = state.replace(event.index, updatedSet)
                if (event.startRest) {
                    enterRest(nextSets.copy(cursor = event.index), now)
                } else {
                    val nextPending = nextSets.sets.indexOfFirst { it.disposition == SetDisposition.PENDING }
                    nextSets.copy(
                        cursor = if (nextPending >= 0) nextPending else event.index,
                        phase = PlayerPhase.ActiveWork,
                        lifecycle = ExecutionLifecycle.IN_PROGRESS,
                        timer = null
                    )
                }
            }
            is PlayerEvent.SaveUnconfirmedPartialHold -> {
                if (event.seconds < 3) return state
                val targetSet = state.sets.getOrNull(event.index) ?: return state
                if (targetSet.disposition != SetDisposition.PENDING) return state
                val draftActual = (targetSet.actual ?: SetActual()).copy(seconds = event.seconds)
                state.replace(event.index, targetSet.copy(actual = draftActual)).copy(
                    phase = PlayerPhase.ActiveWork,
                    lifecycle = ExecutionLifecycle.IN_PROGRESS,
                    timer = null
                )
            }
            is PlayerEvent.SkipSetAt -> {
                val targetSet = state.sets.getOrNull(event.index) ?: return state
                if (targetSet.disposition != SetDisposition.PENDING) return state
                state.replace(event.index, targetSet.copy(disposition = SetDisposition.SKIPPED))
            }
            is PlayerEvent.UnconfirmSetAt -> {
                val targetSet = state.sets.getOrNull(event.index) ?: return state
                if (targetSet.disposition == SetDisposition.PENDING) return state
                state.replace(
                    event.index,
                    ExecutionSet(targetSet.target, targetSet.number, SetDisposition.PENDING, targetSet.actual)
                ).copy(lifecycle = ExecutionLifecycle.IN_PROGRESS)
            }
            is PlayerEvent.AddExtraSet -> {
                val lastIdx = state.sets.indexOfLast { it.target.id == event.exerciseId }
                if (lastIdx < 0 || state.sets.size >= 120) return state
                val template = state.sets[lastIdx]
                val extra = ExecutionSet(template.target, template.number + 1)
                val expanded = state.sets.toMutableList().apply { add(lastIdx + 1, extra) }
                val adjustedCursor = if (state.cursor > lastIdx) state.cursor + 1 else state.cursor
                state.copy(sets = expanded, cursor = adjustedCursor, lifecycle = ExecutionLifecycle.IN_PROGRESS)
            }
            is PlayerEvent.StartNextSet -> {
                val captured = captureRest(state, now)
                val targetExercise = event.exerciseId
                val candidate = if (targetExercise != null) {
                    captured.sets.indices.firstOrNull {
                        it > captured.cursor && captured.sets[it].target.id == targetExercise && captured.sets[it].disposition == SetDisposition.PENDING
                    } ?: captured.sets.indices.firstOrNull {
                        captured.sets[it].target.id == targetExercise && captured.sets[it].disposition == SetDisposition.PENDING
                    }
                } else {
                    captured.sets.indices.firstOrNull { it > captured.cursor && captured.sets[it].disposition == SetDisposition.PENDING }
                        ?: captured.sets.indexOfFirst { it.disposition == SetDisposition.PENDING }.takeIf { it >= 0 }
                }
                if (candidate == null) {
                    captured.copy(phase = PlayerPhase.ActiveWork, timer = null, lifecycle = ExecutionLifecycle.IN_PROGRESS)
                } else {
                    val nextTarget = captured.sets[candidate].target
                    val durationMs = if (event.autoStartTimer) nextTarget.seconds?.times(1000L) else null
                    captured.copy(
                        cursor = candidate,
                        phase = PlayerPhase.ActiveWork,
                        lifecycle = ExecutionLifecycle.IN_PROGRESS,
                        timer = durationMs?.let { PlayerTimer(it, deadlineMs = now + it) }
                    )
                }
            }
            is PlayerEvent.Tick -> state
        }
        return if (next == state) state else next.copy(revision = state.revision + 1)
    }

    private fun enterRest(state: WorkoutExecution, now: Long, remainingWork: Long = 0): WorkoutExecution {
        val duration = (state.current.target.rest * 1000L + remainingWork).coerceAtLeast(0)
        return state.copy(phase = PlayerPhase.Resting, lifecycle = ExecutionLifecycle.IN_PROGRESS,
            timer = PlayerTimer(duration, deadlineMs = now + duration))
    }

    private fun captureRest(state: WorkoutExecution, now: Long): WorkoutExecution =
        if (state.phase == PlayerPhase.Resting) state.replace(state.cursor,
            state.current.copy(actualRestMs = state.timer?.elapsedRest(now))) else state

    private fun advance(state: WorkoutExecution, forwardOnly: Boolean = false): WorkoutExecution {
        val pending = state.sets.indices.firstOrNull { it > state.cursor && state.sets[it].disposition == SetDisposition.PENDING }
            ?: if (forwardOnly) null else state.sets.indexOfFirst {
                it.disposition == SetDisposition.PENDING && it.measuredWorkMs == null
            }.takeUnless { it < 0 }
        return state.copy(cursor = pending ?: state.cursor,
            phase = if (pending == null) PlayerPhase.Reviewing else PlayerPhase.ActiveWork,
            lifecycle = ExecutionLifecycle.IN_PROGRESS, timer = null)
    }

    private fun WorkoutExecution.replace(index: Int, value: ExecutionSet) =
        copy(sets = sets.mapIndexed { i, step -> if (i == index) value else step })
}
