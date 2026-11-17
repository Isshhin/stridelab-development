package io.stridelab.privatealpha

import java.time.Instant

/** Engine 1.0 policy registry. The verified original generator is retained unchanged. */
internal object Engine1Engines {
    val goals=mapOf(
        "run" to listOf("Aerobic foundation","Improve 5K","Improve 10K","Half marathon","Marathon"),
        "str" to listOf("General strength","Muscle development","Strength endurance"),
        "cal" to listOf("Calisthenics foundation","Pull-up progression","Push-up progression","Core strength and control","Handstand foundations"),
        "cond" to listOf("Conditioning"))
    val knownPolicies=setOf(ENGINE1_POLICY,CoordinatedEngine1.policyId,MultiSessionEngine1.policyId,SessionTemplate.POLICY_ID)
    /** A policy is editable only when both its exact version and policy ID are supported. */
    fun supports(version:String,policy:String):Boolean =
        (version==ENGINE1_VERSION && policy in knownPolicies) ||
        (version==ENGINE15_VERSION && policy in setOf(ENGINE15_POLICY, ENGINE15_FOUNDATION_POLICY, ENGINE15_MULTI_POLICY, ENGINE15_INTERFERENCE_POLICY, ENGINE15_INDEPENDENT_RUNNING_POLICY))
    fun canManage(plan:PlanSnapshot)=supports(plan.engineVersion,plan.policyId)
    fun discipline(goal:String)=goals.entries.find { goal in it.value }?.key
    fun goal(p:PlanRequest,discipline:String):String=p.disciplineGoals[discipline] ?: p.goal.takeIf { it in goals.getValue(discipline) } ?: goals.getValue(discipline).first()
    fun resolve(p:PlanRequest):PlanGenerationEngine=when(p.policyChoice) {
        "baseline"->Engine1
        "coordinated"->CoordinatedEngine1
        "multi-session"->MultiSessionEngine1
        "engine15"->if(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE) Engine15IndependentPlanGenerator else Engine15
        else->throw PlanConstraint("Choose a supported coaching engine policy.")
    }
    fun validate(p:PlanRequest) {
        when(resolve(p)) {
            MultiSessionEngine1->MultiSessionEngine1.validate(p)
            Engine15IndependentPlanGenerator->Engine15IndependentPlanGenerator.validateRequest(p)
            Engine15->Engine15.validate(p)
            else->Engine1.validate(p)
        }
    }
}
/** Additional explicit discipline goals, not response-learning or Advanced Engine 2.0. */
internal object CoordinatedEngine1:PlanGenerationEngine {
    override val version=ENGINE1_VERSION
    override val policyId="stridelab.engine1.coordinated-goals-20261027.1"
    override fun generate(request:PlanRequest,owner:String,id:String,now:Instant):PlanSnapshot {
        Engine1.validate(request)
        if(request.disciplineGoals.any { (d,g)->g !in (Engine1Engines.goals[d] ?: emptyList()) })throw PlanConstraint("Choose supported goals for each discipline.")
        val p=request
        val active=listOf("run" to p.runDays,"str" to p.strengthDays,"cal" to p.calDays,"cond" to p.conditioningDays).filter { it.second>0 }.map { it.first }
        val baseGoal=Engine1Engines.goal(p,active.first())
        val base=Engine1.generate(p.copy(goal=baseGoal),owner,id,now)
        // Reuse the exact verified prescription rules with the SAME discipline counts,
        // ability and equipment. Only take prescriptions, never the variant calendar.
        // Removing variant quality requests avoids irrelevant duplicate scheduling constraints.
        val alternatives=active.filter { it in setOf("str","cal") && Engine1Engines.goal(p,it)!=baseGoal }.associateWith { d ->
            Engine1.generate(p.copy(goal=Engine1Engines.goal(p,d),qualityCount=0,
                weeklySlots=emptyMap()),owner,id,now).sessions.associateBy { it.id }
        }
        val sessions=base.sessions.map { original ->
            val variant=alternatives[original.discipline]?.get(original.id)
            if(variant==null) original.copy(reason="${disciplineLabelForPolicy(original.discipline)} goal: ${Engine1Engines.goal(p,original.discipline)}. ${original.reason}")
            else {
                require(variant.lower==original.lower && variant.discipline==original.discipline && variant.minutes<=p.minutes)
                original.copy(title=variant.title,minutes=variant.minutes,exercises=variant.exercises,steps=variant.steps,reason="${disciplineLabelForPolicy(original.discipline)} goal: ${Engine1Engines.goal(p,original.discipline)}. ${variant.reason}")
            }
        }
        val conservative=sessions.map { session ->
            val week=java.time.temporal.ChronoUnit.DAYS.between(p.start,session.date)/7
            if(!p.returning || week>=2 || session.exercises.isEmpty())session else {
                val ex=session.exercises.map { it.copy(sets=minOf(it.sets,2)) }
                val time=kotlin.math.ceil(8+ex.sumOf { (it.sets*(it.seconds ?: ((it.reps?:0)*3))+(it.sets-1)*it.rest)/60.0+1 }).toInt()
                session.copy(exercises=ex,minutes=minOf(session.minutes,time),reason=session.reason+" Return-from-break practice is capped at two sets per movement for the first two weeks; keep several controlled reps in reserve.")
            }
        }
        require(sessions.size==base.sessions.size && sessions.map { it.id }==base.sessions.map { it.id })
        return base.copy(policyId=policyId,request=p,sessions=conservative,explanations=listOf("Engine 1.0 coordinated goals: "+active.joinToString { "${disciplineLabelForPolicy(it)} — ${Engine1Engines.goal(p,it)}" }+". The shared dated schedule and recovery checks are retained; these are fixed rules, not learned adaptation.")+base.explanations)
    }
    private fun disciplineLabelForPolicy(d:String)=when(d){"run"->"Running";"str"->"Strength";"cal"->"Calisthenics";else->"Conditioning"}
}
