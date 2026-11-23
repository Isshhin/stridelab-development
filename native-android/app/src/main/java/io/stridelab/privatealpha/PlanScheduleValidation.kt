package io.stridelab.privatealpha

/** Read-only checks for imported schedules; historical facts and opaque policies are retained. */
internal object PlanScheduleValidation {
    private val independentPolicies = setOf(
        ENGINE15_INDEPENDENT_RUNNING_POLICY, ENGINE15_INDEPENDENT_STRENGTH_POLICY,
        ENGINE15_INDEPENDENT_CALISTHENICS_POLICY, ENGINE15_INDEPENDENT_CONDITIONING_POLICY,
        ENGINE15_INDEPENDENT_COMBINED_POLICY
    )

    fun isIndependent(policy: String): Boolean = policy in independentPolicies

    fun separateTracks(a: PlannedSession, b: PlannedSession, policyA: String, policyB: String): Boolean =
        isIndependent(policyA) && isIndependent(policyB) && track(a) != track(b)

    private fun track(session: PlannedSession) = when {
        session.discipline != "str" -> session.discipline
        session.title == "Plyometrics" || session.kind == "structured-plyometrics" -> "plyo"
        session.title == "Isometrics" -> "iso"
        else -> "str"
    }

    fun acrossPlansConflict(a: PlannedSession, b: PlannedSession, policyA: String, policyB: String): Boolean =
        !separateTracks(a, b, policyA, policyB) && conflict(a, b, policyA, policyB)

    fun crossPlanRecoveryNote(plan: PlanSnapshot, others: List<PlanSnapshot>): String? {
        val close = others.asSequence().filter { it.id != plan.id && it.owner == plan.owner && it.status == "approved" }
            .flatMap { other -> scheduled(other).asSequence().flatMap { peer ->
                scheduled(plan).asSequence().filter { candidate ->
                    separateTracks(candidate, peer, plan.policyId, other.policyId) &&
                        Engine15IndependentPlanGenerator.hardSpacingConflict(candidate, peer)
                }.map { it.date }
            } }.distinct().count()
        if (close == 0) return null
        return "On $close of this plan’s training days, hard efforts are close to another plan. Leave a full day between hard efforts where possible, and skip jumps if your legs aren’t fresh."
    }

    fun isMultiPolicy(policy:String) =
        policy==ENGINE15_INDEPENDENT_COMBINED_POLICY ||
            policy==MULTI_SESSION_POLICY || policy==ENGINE15_MULTI_POLICY ||
            policy==ENGINE15_INTERFERENCE_POLICY

    fun conflict(a:PlannedSession,b:PlannedSession,policyA:String,policyB:String):Boolean {
        if (Engine15DisciplineProgression.spacingConflict(a, b)) return true
        if(a.date==b.date && isMultiPolicy(policyA) && policyA==policyB && a.discipline!=b.discipline) {
            val easyRunWithUpperOrCore =
                a.discipline=="run" && a.kind=="easy" && !a.quality && !b.lower ||
                b.discipline=="run" && b.kind=="easy" && !b.quality && !a.lower
            if(easyRunWithUpperOrCore) return false
        }
        return Engine15IndependentPlanGenerator.conflict(a,b)
    }

    private fun scheduled(plan:PlanSnapshot)=plan.sessions.filter { it.status in setOf("planned","in_progress") }

    fun validatePlan(plan:PlanSnapshot) {
        if(plan.status!="approved" || !Engine1Engines.canManage(plan))return
        if(plan.policyId in setOf(ENGINE15_INDEPENDENT_RUNNING_POLICY, ENGINE15_INDEPENDENT_STRENGTH_POLICY, ENGINE15_INDEPENDENT_CALISTHENICS_POLICY, ENGINE15_INDEPENDENT_CONDITIONING_POLICY, ENGINE15_INDEPENDENT_COMBINED_POLICY))
            Engine15IndependentPlanGenerator.validateSaved(plan)
        Engine15SessionLocks.validateSaved(plan)
        Engine15Availability.requireReady(plan)
        val sessions=scheduled(plan)
        for(i in sessions.indices)for(k in i+1 until sessions.size) {
            val conflict = if(plan.policyId==ENGINE15_INDEPENDENT_COMBINED_POLICY)
                Engine15StaticMultiDisciplinePlanner.conflict(sessions[i],sessions[k],plan.request)
            else conflict(sessions[i],sessions[k],plan.policyId,plan.policyId)
            if(conflict)throw PlanConstraint("The backup contains conflicting sessions inside an approved plan. No records were changed.")
        }
        val enforceDailyBudget = plan.policyId != ENGINE15_INDEPENDENT_COMBINED_POLICY || plan.request.timeBudgetEnabled
        if(isMultiPolicy(plan.policyId) && sessions.groupBy { it.date }.values.any { day -> day.size>2 || (enforceDailyBudget && day.sumOf { it.effectiveMinutes }>plan.request.dailyMinutes) })throw PlanConstraint("The backup exceeds an approved plan's daily time budget. No records were changed.")
    }

    fun validateRestoration(plans:List<PlanSnapshot>) {
        val active=plans.filter { it.status=="approved" }
        active.forEach(::validatePlan)
        for(i in active.indices)for(k in i+1 until active.size) {
            val a=active[i];val b=active[k]
            if(scheduled(a).any { x -> scheduled(b).any { y -> acrossPlansConflict(x,y,a.policyId,b.policyId) } })throw PlanConstraint("The backup's approved calendar conflicts with another approved plan. No records were changed.")
        }
        val multi=active.filter { Engine1Engines.canManage(it) && isMultiPolicy(it.policyId) && (it.policyId != ENGINE15_INDEPENDENT_COMBINED_POLICY || it.request.timeBudgetEnabled) }
        val days=multi.flatMap { p -> scheduled(p).map { p to it } }.groupBy { it.second.date }
        for(day in days.values) {
            if(day.sumOf { it.second.effectiveMinutes }>day.minOf { it.first.request.dailyMinutes })throw PlanConstraint("The backup exceeds an approved plan's shared daily time budget. No records were changed.")
        }
    }
}
