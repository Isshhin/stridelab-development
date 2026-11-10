package io.stridelab.privatealpha

import java.time.LocalDate

/** Pure, reviewable repair; unaffected placements and actuals are immutable. */
internal object Engine15Availability {
    data class Impact(
        val affectedSessionIds:List<String>,
        val newlyAvailableDays:Set<Int>,
        val newlyUnavailableDays:Set<Int>,
        val relocations:Map<String,LocalDate> = emptyMap(),
        val unresolvedSessionIds:List<String> = emptyList()
    )

    /** Bounded same-week search; asOf is supplied for deterministic midweek decisions. */
    fun preview(
        plan:PlanSnapshot,days:Set<Int>,
        otherPlans:List<PlanSnapshot> = emptyList(),
        asOf:LocalDate = LocalDate.now()
    ):Impact {
        if(plan.engineVersion!=ENGINE15_VERSION || !Engine1Engines.canManage(plan))
            throw PlanConstraint("Availability editing requires a supported Engine 1.5 plan.")
        if(plan.status !in setOf("approved","paused","draft"))
            throw PlanConstraint("Archived plans cannot change training availability.")
        if(days.isEmpty() || days.any { it !in 1..7 })
            throw PlanConstraint("Choose at least one available weekday.")
        Engine15SessionLocks.validateSaved(plan)
        if(plan.request.lockedSessionDays.values.any {
                LocalDate.parse(it).dayOfWeek.value !in days
            })throw PlanConstraint(
                "A locked workout falls on a newly unavailable day. Unlock or review the lock first.")
        val outside=plan.sessions.filter { it.date.dayOfWeek.value !in days }
        if(outside.any { it.status=="in_progress" })
            throw PlanConstraint(
                "An in-progress workout occupies an unavailable day. Finish or review it first.")
        val affected=outside.filter { it.status=="planned" }.map { it.id }
        val affectedIds=affected.toSet()
        val candidates=plan.sessions.filter {
            it.id in affectedIds || it.status=="unassigned"
        }
        val candidateIds=candidates.map { it.id }.toSet()
        val fixed=plan.sessions.filter {
            it.id !in candidateIds &&
                it.status !in setOf("unassigned","missed","skipped")
        }
        val approvedOthers=otherPlans.filter {
            it.id!=plan.id && it.owner==plan.owner && it.status=="approved"
        }.flatMap { other ->
            other.sessions.filter {
                it.status !in setOf("unassigned","missed","skipped")
            }.map { other to it }
        }

        val placed=mutableListOf<PlannedSession>()
        val maxOnDay=if(PlanScheduleValidation.isMultiPolicy(plan.policyId)) 2 else 1
        val request=plan.request
        for(week in 0 until request.weeks) {
            val first=request.start.plusWeeks(week.toLong())
            val last=first.plusWeeks(1)
            val ordered=candidates.filter { it.date>=first && it.date<last }
                .sortedWith(compareBy<PlannedSession> {
                    when {
                        it.kind=="long" -> 0
                        it.quality -> 1
                        it.lower -> 2
                        it.discipline=="str" -> 3
                        it.discipline=="cal" -> 4
                        else -> 5
                    }
                }.thenBy { it.id })
            if(ordered.isEmpty())continue
            var best=emptyList<PlannedSession>()
            val chosen=mutableListOf<PlannedSession>()
            var attempts=0
            fun solve(index:Int) {
                if(++attempts>200_000)return
                if(chosen.size>best.size)best=chosen.toList()
                if(index==ordered.size ||
                    chosen.size+(ordered.size-index)<=best.size)return
                val session=ordered[index]
                val eligible=(0L..6L).map(first::plusDays).filter { date ->
                    date>=asOf && date.dayOfWeek.value in days &&
                    (session.kind!="long" || date.dayOfWeek.value==request.longDay) &&
                    (!session.quality || date.dayOfWeek.value in request.qualityDays)
                }.sortedWith(compareBy<LocalDate> { date ->
                    (fixed+placed+chosen).count { it.date==date }
                }.thenBy { it })
                for(date in eligible) {
                    val proposed=session.copy(date=date,status="planned")
                    val current=fixed+placed+chosen
                    val localDay=current.filter { it.date==date }
                    val externalDay=approvedOthers.filter { it.second.date==date }
                    if(localDay.size+externalDay.size>=maxOnDay)continue
                    if(request.timeBudgetEnabled && localDay.sumOf { it.effectiveMinutes }+
                        proposed.effectiveMinutes>request.dailyMinutes)continue
                    if(current.any {
                        PlanScheduleValidation.conflict(
                            proposed,it,plan.policyId,plan.policyId)
                    })continue
                    if(approvedOthers.any { (other,sessionInOther) ->
                        PlanScheduleValidation.conflict(
                            proposed,sessionInOther,plan.policyId,other.policyId)
                    })continue
                    if(externalDay.isNotEmpty()) {
                        val budget=(listOfNotNull(request.dailyMinutes.takeIf { request.timeBudgetEnabled }) +
                            externalDay.mapNotNull { (other, _) -> other.request.dailyMinutes.takeIf { other.request.timeBudgetEnabled } }).minOrNull()
                        if(budget != null && localDay.sumOf { it.effectiveMinutes }+
                            externalDay.sumOf { it.second.effectiveMinutes }+
                            proposed.effectiveMinutes>budget)continue
                    }
                    chosen+=proposed
                    solve(index+1)
                    chosen.removeAt(chosen.lastIndex)
                }
                solve(index+1) // Optional unassignment, never forced cramming.
            }
            solve(0)
            placed+=best
        }
        val relocations=placed.associate { it.id to it.date }
        return Impact(
            affected,days-request.days,request.days-days,
            relocations,candidates.map { it.id }.filterNot { it in relocations }
        )
    }

    /** Validate approved schedules and restored snapshots against their current availability. */
    fun requireReady(plan:PlanSnapshot) {
        if(plan.engineVersion!=ENGINE15_VERSION)return
        if(plan.sessions.any { it.status=="unassigned" })
            throw PlanConstraint("Place or review every unassigned workout before approving this plan.")
        if(plan.sessions.any { it.status in setOf("planned","in_progress") &&
                it.date.dayOfWeek.value !in plan.request.days })
            throw PlanConstraint("A planned workout falls outside approved availability.")
        if(plan.request.runDays>0 && plan.request.longDay !in plan.request.days)
            throw PlanConstraint("Choose a new available long-run day before approving.")
        if(plan.request.qualityCount>0 &&
            (plan.request.qualityDays.size<plan.request.qualityCount ||
                plan.request.qualityDays.any {
                    it !in plan.request.days || it==plan.request.longDay
                }))
            throw PlanConstraint("Review preferred quality days before approving.")
    }
}
