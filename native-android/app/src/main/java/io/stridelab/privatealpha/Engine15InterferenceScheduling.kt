package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Engine 1.5-only conservative placement of existing Engine 1.0 prescriptions.
 * This is scheduling interference, not an inferred physiological readiness model.
 */
internal object Engine15InterferenceScheduling {
    private fun safePair(a:PlannedSession,b:PlannedSession):Boolean {
        val run=listOf(a,b).singleOrNull { it.discipline=="run" } ?: return false
        val other=if(a.id==run.id)b else a
        return run.kind=="easy" && !run.quality && !run.lower &&
            ((other.discipline=="cal" && !other.lower) ||
                (other.discipline=="str" && !other.lower))
    }
    fun conflict(a:PlannedSession,b:PlannedSession):Boolean =
        if(a.date==b.date) !safePair(a,b) else MultiSessionScheduling.conflict(a,b)

    private fun templates(p:PlanRequest,discipline:String,count:Int,
                          owner:String,id:String,now:Instant):List<PlannedSession> {
        val strength=discipline=="str"
        // Engine 1.0 alternates lower and upper strength only alongside running.
        // Generate a disposable one-run strength draft, then retain strength only.
        val solo=p.copy(goal=Engine1Engines.goal(p,discipline),
            policyChoice="baseline",
            runDays=if(discipline=="run")count else if(strength)1 else 0,
            strengthDays=if(strength)count else 0,
            calDays=if(discipline=="cal")count else 0,
            conditioningDays=if(discipline=="cond")count else 0,
            qualityCount=if(discipline=="run")p.qualityCount else 0)
        return Engine1.generate(solo,owner,id,now).sessions.filter {
            it.discipline==discipline
        }
    }

    fun generateBase(p:PlanRequest,owner:String,id:String,now:Instant):PlanSnapshot {
        val active=listOf("run" to p.runDays,"str" to p.strengthDays,
            "cal" to p.calDays,"cond" to p.conditioningDays).filter { it.second>0 }
        val allTemplates=active.flatMap { (d,n)->templates(p,d,n,owner,id,now) }
        if(allTemplates.map { it.id }.distinct().size!=allTemplates.size)
            throw PlanConstraint("Duplicate workout IDs; no plan was saved.")
        val scheduled=mutableListOf<PlannedSession>()
        for(week in 0 until p.weeks) {
            val first=p.start.plusWeeks(week.toLong())
            val slots=(0L..6L).map(first::plusDays).filter { it.dayOfWeek.value in p.days }
            val ordered=allTemplates.filter { it.date>=first && it.date<first.plusWeeks(1) }
                .sortedWith(compareBy<PlannedSession> {
                    when {
                        it.kind=="long"->0
                        it.quality->1
                        it.lower->2
                        it.discipline=="str"->3
                        it.discipline=="cal"->4
                        else->5
                    }
                }.thenBy { it.id })
            val placed=mutableListOf<PlannedSession>()
            var attempts=0
            fun solve(index:Int):Boolean {
                if(index==ordered.size)return true
                if(++attempts>250_000)return false
                val session=ordered[index]
                val candidates=slots.filter {
                    when {
                        session.kind=="long"->it.dayOfWeek.value==p.longDay
                        session.quality->it.dayOfWeek.value in p.qualityDays
                        else->true
                    }
                }.let { days -> Engine15SessionLocks.restrictDates(p,session,days) }
                .sortedWith(compareBy<LocalDate> { date ->
                    val current=scheduled+placed
                    val nearHard=if(session.lower)current.count {
                        (it.quality || it.kind=="long") &&
                            abs(ChronoUnit.DAYS.between(it.date,date))==2L
                    } else 0
                    nearHard*3 + if(current.any { it.date==date })1 else 0
                }.thenBy { it })
                for(date in candidates) {
                    val proposed=session.copy(date=date)
                    val current=scheduled+placed
                    val day=current.filter { it.date==date }
                    if(day.size>=2 || day.sumOf { it.effectiveMinutes }+
                        proposed.effectiveMinutes>p.dailyMinutes)continue
                    if(current.any { conflict(proposed,it) })continue
                    placed+=proposed
                    if(solve(index+1))return true
                    placed.removeAt(placed.lastIndex)
                }
                return false
            }
            if(!solve(0))throw PlanConstraint(
                "These mixed sessions cannot fit the selected available days, " +
                "daily time budget and lower-body recovery spacing. Add days, " +
                "increase the budget or reduce sessions; no workout was dropped.")
            scheduled+=placed
        }
        if(scheduled.size!=allTemplates.size)
            throw PlanConstraint("An incomplete schedule was rejected before saving.")
        return PlanSnapshot(id,owner,ENGINE1_VERSION,MULTI_SESSION_POLICY,now,p,
            scheduled.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id }),
            listOf("Existing Engine 1.0 prescriptions with isolated " +
                "Engine 1.5 interference-aware multidisciplinary placement."))
    }
}
