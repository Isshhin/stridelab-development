package io.stridelab.privatealpha

import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal enum class DebriefDiscipline { RUNNING, STRENGTH, PLYOMETRICS, ISOMETRICS, CALISTHENICS, CONDITIONING }

internal data class DebriefDose(
    val met: Int, val logged: Int, val planned: Int, val loggedSeconds: Int, val plannedSeconds: Int
) {
    val tone get() = when {
        met == planned -> DebriefTone.POSITIVE
        logged == planned -> DebriefTone.CAUTION
        else -> DebriefTone.NEUTRAL
    }
}

internal class CrossTrainingDebriefEvidence(
    val session: PlannedSession,
    val log: WorkoutLog,
    val previousHardRunDays: Long?
) {
    val targets = session.effectiveExercises.map {
        if (session.suggestion?.accepted == true) it.copy(sets = (it.sets * .7).toInt().coerceAtLeast(1))
        else it
    }
    // Ambiguous duplicate rows have no reliable dose.
    val sets = log.sets.groupBy { it.exerciseId to it.set }.values.mapNotNull { it.singleOrNull() }

    fun dose(holdsOnly: Boolean = false): DebriefDose? {
        val prescribed = targets.filter { it.seconds != null || !holdsOnly && it.reps != null }
        val byId = prescribed.associateBy { it.id }
        if (prescribed.size != byId.size) return null
        var logged = 0
        var met = 0
        var loggedSeconds = 0
        for (set in sets) {
            val target = byId[set.exerciseId] ?: continue
            if (set.set !in 1..target.sets) continue
            val goal = (target.seconds ?: target.reps)?.takeIf { it > 0 } ?: continue
            val actual = (if (target.seconds != null) set.seconds else set.reps)?.takeIf { it > 0 } ?: continue
            logged++
            if (target.seconds != null) loggedSeconds += actual
            if (actual >= goal) met++
        }
        return if (logged == 0) null else DebriefDose(met, logged, prescribed.sumOf { it.sets },
            loggedSeconds, prescribed.sumOf { (it.seconds ?: 0) * it.sets })
    }

    fun effort(ceiling: Int): DebriefInsight? {
        val rating = log.effort?.takeIf { it in 1..10 }
        val harder = log.completionDifficulty in setOf("Harder", "Could not finish")
        if (rating == null && !harder) return null
        return DebriefCopyTemplates.effort(rating, ceiling, harder || (rating ?: 0) > ceiling, harder)
    }
}

/** Shared dispatch for read-only debriefs. Source records and workout logs stay separate. */
internal object Engine15DebriefEngine {
    private val crossTraining = mapOf<DebriefDiscipline, (CrossTrainingDebriefEvidence) -> List<DebriefInsight>>(
        DebriefDiscipline.STRENGTH to StrengthDebriefAnalyzer::analyze,
        DebriefDiscipline.PLYOMETRICS to PlyometricsDebriefAnalyzer::analyze,
        DebriefDiscipline.ISOMETRICS to IsometricsDebriefAnalyzer::analyze,
        DebriefDiscipline.CALISTHENICS to CalisthenicsDebriefAnalyzer::analyze,
        DebriefDiscipline.CONDITIONING to ConditioningDebriefAnalyzer::analyze
    )

    fun runningReport(
        activity: ActivityRecord,
        detail: ActivityDetail?,
        execution: WorkoutExecutionAnalysis?,
        zones: ExecutionHrZoneProfile?,
        intent: ActivityWorkoutKind?,
        history: List<ActivityRecord> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault()
    ) = Engine15RunningDebriefAnalyzer.report(activity, detail, execution, zones, intent, history, zone)

    fun running(
        activity: ActivityRecord,
        detail: ActivityDetail?,
        execution: WorkoutExecutionAnalysis?,
        zones: ExecutionHrZoneProfile?,
        intent: ActivityWorkoutKind?,
        weather: WeatherContext? = null,
        history: List<ActivityRecord> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault()
    ): SessionDebrief? = Engine15RunningDebriefAnalyzer.analyze(activity, detail, execution, zones, intent, weather, history, zone)

    fun workout(
        plan: PlanSnapshot, sessionId: String, plans: List<PlanSnapshot>,
        activities: List<ActivityRecord> = emptyList(), zone: ZoneId
    ): SessionDebrief? {
        if (!Engine1Engines.supportsCoachingLoop(plan) || plan.status == "draft") return null
        val session = plan.sessions.singleOrNull { it.id == sessionId && it.status == "completed" } ?: return null
        val log = session.log ?: return null
        if (session.suggestion?.accepted == true && session.suggestion.minutes == 0) return null
        val discipline = discipline(plan.request, session) ?: return null
        val analyze = crossTraining[discipline] ?: return null
        val date = log.performedOn ?: log.at.atZone(zone).toLocalDate()
        val knownActivity = activities.associateBy { it.id }
        val hardRunDays = plans.asSequence()
            .filter { it.owner == plan.owner && it.status == "approved" }
            .flatMap { it.sessions.asSequence() }
            .filter { it.discipline == "run" && it.status == "completed" && (it.quality || it.kind == "long") }
            .mapNotNull { run ->
                val actualDate = run.log?.performedOn ?: run.linkedActivityId?.let { id ->
                    knownActivity[id]?.takeIf(DashboardSummary::isRun)?.startedAt?.atZone(zone)?.toLocalDate()
                } ?: run.log?.at?.atZone(zone)?.toLocalDate()
                actualDate?.let { ChronoUnit.DAYS.between(it, date) }?.takeIf { it in 0L..1L }
            }.minOrNull()
        return SessionDebrief.fromInsights(analyze(CrossTrainingDebriefEvidence(session, log, hardRunDays)))
    }

    fun linkedWorkout(
        sourceIds: Set<String>, owner: String?, plans: List<PlanSnapshot>,
        activities: List<ActivityRecord>, zone: ZoneId
    ): SessionDebrief? {
        if (owner == null || sourceIds.isEmpty()) return null
        val (plan, session) = plans.asSequence()
            .filter { it.owner == owner && Engine1Engines.supportsCoachingLoop(it) && it.status != "draft" }
            .flatMap { plan -> plan.sessions.asSequence().map { plan to it } }
            .filter { (_, session) -> session.discipline != "run" && session.status == "completed" &&
                session.linkedActivityId in sourceIds && session.log != null }
            .take(2).toList().singleOrNull() ?: return null
        return workout(plan, session.id, plans, activities, zone)
    }

    private fun discipline(request: PlanRequest, session: PlannedSession): DebriefDiscipline? = when (session.discipline) {
        "run" -> DebriefDiscipline.RUNNING
        "plyo" -> DebriefDiscipline.PLYOMETRICS
        "iso" -> DebriefDiscipline.ISOMETRICS
        "str" -> when {
            session.kind == "structured-plyometrics" || strengthTrack(request) == "plyo" || session.title == "Plyometrics" ->
                DebriefDiscipline.PLYOMETRICS
            strengthTrack(request) == "iso" || session.title == "Isometrics" -> DebriefDiscipline.ISOMETRICS
            else -> DebriefDiscipline.STRENGTH
        }
        "cal" -> DebriefDiscipline.CALISTHENICS
        "cond" -> DebriefDiscipline.CONDITIONING
        else -> null
    }
}
