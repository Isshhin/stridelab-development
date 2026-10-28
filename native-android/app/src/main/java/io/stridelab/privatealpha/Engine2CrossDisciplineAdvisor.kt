package io.stridelab.privatealpha

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 43 / 44(a): ONE conservative proposal candidate, no mutations.
 * Uses existing Engine1 lower/quality/long metadata and Engine2ScheduleAuditor.
 * Not a validated sports-science injury model or a recovery diagnosis.
 */
internal object Engine2CrossDisciplineAdvisor {
    fun demands(source:PlanSnapshot):List<Engine2SessionDemand> {
        require(Engine1Engines.canManage(source))
        return source.sessions.map { s ->
            Engine2SessionDemand(s.id,
                stress=when {
                    s.quality -> Engine2StressTag.HARD
                    s.discipline=="run" && s.kind=="long" -> Engine2StressTag.MODERATE
                    s.discipline=="run" && s.kind=="easy" -> Engine2StressTag.EASY
                    else -> Engine2StressTag.UNKNOWN
                },
                lowerBody=s.lower,
                longRun=s.discipline=="run" && s.kind=="long"
            )
        }
    }

    fun proposeReviewableLowerBodyReduction(
        source:PlanSnapshot,
        context:Engine2AthleteContext,
        proposalId:String,
        at:Instant
    ):Engine2DurationProposal? {
        require(context.ownerId==source.owner && !at.isAfter(context.asOf))
        val view=Engine2PlanBridge.preview(source)
        if(view.status!=Engine2PlanStatus.APPROVED) return null
        val demands=demands(source)
        val issues=Engine2ScheduleAuditor.inspect(view,demands,
            source.request.dailyMinutes.coerceIn(15,1440))
        val longIds=source.sessions.filter { it.discipline=="run" && it.kind=="long" }
            .map { it.id }.toSet()
        val lowerIds=source.sessions.filter {
            it.lower && it.discipline in setOf("str","cal")
        }.map { it.id }.toSet()
        val conflict=issues.firstOrNull {
            it.code==Engine2ScheduleIssueCode.LOWER_BEFORE_LONG &&
                it.sessionIds.any { id -> id in longIds } &&
                it.sessionIds.any { id -> id in lowerIds }
        } ?: return null
        val lower=view.sessions.filter {
            it.id in conflict.sessionIds && it.id in lowerIds &&
                it.status==Engine2SessionStatus.PLANNED &&
                it.date.isAfter(LocalDate.ofInstant(at,ZoneId.systemDefault())) &&
                it.minutes>=30
        }.minWithOrNull(compareBy<Engine2SessionTarget> { it.date }.thenBy { it.id })
            ?: return null

        // Observations establish that the athlete has actually logged both
        // domains, NOT that current fatigue is known or the schedule is unsafe.
        val cutoff=at.minus(Duration.ofDays(28))
        val relevant=context.workouts.filter { w ->
            w.status==Engine2RecordStatus.CONFIRMED &&
                !w.occurredAt.isAfter(at) && w.occurredAt.isAfter(cutoff) &&
                w.measurements.isNotEmpty()
        }
        val priorRun=relevant.filter { w ->
            w.discipline==Engine2Discipline.RUNNING &&
                w.measurements.any { it.metric==Engine2Metric.DISTANCE_KM }
        }.maxByOrNull { it.occurredAt } ?: return null
        val priorStrength=relevant.filter { w ->
            w.discipline in setOf(Engine2Discipline.STRENGTH,Engine2Discipline.CALISTHENICS) &&
                w.measurements.any { it.metric in setOf(Engine2Metric.REPS,Engine2Metric.LOAD_KG,
                    Engine2Metric.HOLD_SECONDS) }
        }.maxByOrNull { it.occurredAt } ?: return null
        val reduced=maxOf(10,lower.minutes*3/4)
        if(reduced>=lower.minutes) return null
        return Engine2ProposalWorkflow.prepare(
            view,context,proposalId,lower.id,reduced,
            "A lower-body workout is planned the day before a long run. " +
                "Earlier recorded running and resistance work provides context, " +
                "but actual fatigue and recovery are unknown. Review an optional " +
                "shorter lower-body session; no schedule change is applied.",
            setOf(priorRun.id,priorStrength.id),at
        )
    }
}
