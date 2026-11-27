package io.stridelab.privatealpha

internal val ENGINE15_COACHING_POLICIES = setOf(
    ENGINE15_POLICY,
    ENGINE15_MULTI_POLICY,
    ENGINE15_INTERFERENCE_POLICY,
    ENGINE15_INDEPENDENT_RUNNING_POLICY,
    ENGINE15_INDEPENDENT_STRENGTH_POLICY,
    ENGINE15_INDEPENDENT_CALISTHENICS_POLICY,
    ENGINE15_INDEPENDENT_CONDITIONING_POLICY,
    ENGINE15_INDEPENDENT_COMBINED_POLICY
)

/** Policy registry routing all plan validation and generation to Engine 1.5 Independent. */
internal object Engine1Engines {
    val goals=PlanDisciplineContract.goals
    val knownPolicies=setOf(
        ENGINE1_POLICY,
        "stridelab.engine1.coordinated-goals-20261027.1",
        MULTI_SESSION_POLICY,
        SessionTemplate.POLICY_ID
    )
    /** Saved plans remain editable when they match a supported Engine 1.5 policy, historical saved policy, or user session template. */
    fun supports(version:String,policy:String):Boolean =
        (version==ENGINE1_VERSION && policy in knownPolicies) ||
        (version==ENGINE15_VERSION && (policy in ENGINE15_COACHING_POLICIES || policy == ENGINE15_FOUNDATION_POLICY)) ||
        (version==ENGINE16_VERSION && policy == ENGINE16_STRENGTH_POLICY) ||
        (version==ENGINE17_VERSION && policy == ENGINE17_CALISTHENICS_POLICY) ||
        (version==ENGINE18_VERSION && policy in setOf(ENGINE18_HYBRID_POLICY, ENGINE18_CONDITIONING_POLICY))
    fun canManage(plan:PlanSnapshot)=supports(plan.engineVersion,plan.policyId)
    fun supportsCoachingLoop(plan: PlanSnapshot): Boolean =
        (plan.engineVersion == ENGINE15_VERSION && plan.policyId in ENGINE15_COACHING_POLICIES) ||
        (plan.engineVersion == ENGINE16_VERSION && plan.policyId == ENGINE16_STRENGTH_POLICY) ||
        (plan.engineVersion == ENGINE17_VERSION && plan.policyId == ENGINE17_CALISTHENICS_POLICY) ||
        (plan.engineVersion == ENGINE18_VERSION && plan.policyId in setOf(ENGINE18_HYBRID_POLICY, ENGINE18_CONDITIONING_POLICY))
    fun discipline(goal:String)=goals.entries.find { goal in it.value }?.key
    fun count(p:PlanRequest,discipline:String)=p.disciplineSessionCount(discipline)
    fun goal(p:PlanRequest,discipline:String):String=p.disciplineGoal(discipline)
    fun resolve(p:PlanRequest):PlanGenerationEngine=Engine15IndependentPlanGenerator
    fun validate(p:PlanRequest)=Engine15IndependentPlanGenerator.validateRequest(p)
}
