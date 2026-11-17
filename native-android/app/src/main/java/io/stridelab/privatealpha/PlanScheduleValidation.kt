package io.stridelab.privatealpha

/** Read-only checks for imported schedules; historical facts and opaque policies are retained. */
internal object PlanScheduleValidation {
    fun isMultiPolicy(policy:String) =
        policy==MULTI_SESSION_POLICY || policy==ENGINE15_MULTI_POLICY ||
            policy==ENGINE15_INTERFERENCE_POLICY

    fun conflict(a:PlannedSession,b:PlannedSession,policyA:String,policyB:String)=
        if(policyA in setOf(ENGINE15_INDEPENDENT_RUNNING_POLICY, ENGINE15_INDEPENDENT_STRENGTH_POLICY, ENGINE15_INDEPENDENT_CALISTHENICS_POLICY) ||
            policyB in setOf(ENGINE15_INDEPENDENT_RUNNING_POLICY, ENGINE15_INDEPENDENT_STRENGTH_POLICY, ENGINE15_INDEPENDENT_CALISTHENICS_POLICY))
            Engine15IndependentPlanGenerator.conflict(a,b)
        else if(policyA==ENGINE15_INTERFERENCE_POLICY && policyB==ENGINE15_INTERFERENCE_POLICY)
            Engine15InterferenceScheduling.conflict(a,b)
        else if(policyA==ENGINE15_INTERFERENCE_POLICY || policyB==ENGINE15_INTERFERENCE_POLICY)
            // Retain baseline cross-policy restrictions and also honor the
            // interference policy's symmetric hard-day spacing.
            Engine1.conflict(a,b) || Engine15InterferenceScheduling.conflict(a,b)
        else if(policyA==policyB && isMultiPolicy(policyA))MultiSessionScheduling.conflict(a,b)
        else Engine1.conflict(a,b)

    private fun scheduled(plan:PlanSnapshot)=plan.sessions.filter { it.status in setOf("planned","in_progress") }

    fun validatePlan(plan:PlanSnapshot) {
        if(plan.status!="approved" || !Engine1Engines.canManage(plan))return
        if(plan.policyId in setOf(ENGINE15_INDEPENDENT_RUNNING_POLICY, ENGINE15_INDEPENDENT_STRENGTH_POLICY, ENGINE15_INDEPENDENT_CALISTHENICS_POLICY))
            Engine15IndependentPlanGenerator.validateSaved(plan)
        Engine15SessionLocks.validateSaved(plan)
        Engine15Availability.requireReady(plan)
        val sessions=scheduled(plan)
        for(i in sessions.indices)for(k in i+1 until sessions.size) {
            if(conflict(sessions[i],sessions[k],plan.policyId,plan.policyId))throw PlanConstraint("The backup contains conflicting sessions inside an approved plan. No records were changed.")
        }
        if(isMultiPolicy(plan.policyId) && sessions.groupBy { it.date }.values.any { day -> day.size>2 || day.sumOf { it.effectiveMinutes }>plan.request.dailyMinutes })throw PlanConstraint("The backup exceeds an approved plan's daily time budget. No records were changed.")
    }

    fun validateRestoration(plans:List<PlanSnapshot>) {
        val active=plans.filter { it.status=="approved" }
        active.forEach(::validatePlan)
        for(i in active.indices)for(k in i+1 until active.size) {
            val a=active[i];val b=active[k]
            if(scheduled(a).any { x -> scheduled(b).any { y -> conflict(x,y,a.policyId,b.policyId) } })throw PlanConstraint("The backup's approved calendar conflicts with another approved plan. No records were changed.")
        }
        val multi=active.filter { Engine1Engines.canManage(it) && isMultiPolicy(it.policyId) }
        val days=multi.flatMap { p -> scheduled(p).map { p to it } }.groupBy { it.second.date }
        for(day in days.values) {
            if(day.sumOf { it.second.effectiveMinutes }>day.minOf { it.first.request.dailyMinutes })throw PlanConstraint("The backup exceeds an approved plan's shared daily time budget. No records were changed.")
        }
    }
}
