package io.stridelab.privatealpha

import java.time.Instant

internal const val ENGINE15_VERSION = "1.5.0"
internal const val ENGINE15_POLICY = "stridelab.engine15.foundation-20261103.1"

/**
 * Batch A: versioned Engine 1.5 foundation.
 *
 * Explicit opt-in only. Until Batch B's coaching rules are implemented and
 * validated, this candidate intentionally uses the verified Engine 1.0
 * prescriptions and scheduling. It is not selectable in the Plan Builder UI.
 */
internal object Engine15 : PlanGenerationEngine {
    override val version = ENGINE15_VERSION
    override val policyId = ENGINE15_POLICY

    internal fun validate(request: PlanRequest) {
        if (request.policyChoice != "engine15") {
            throw PlanConstraint("Engine 1.5 must be selected explicitly.")
        }
        Engine1.validate(request)
    }

    override fun generate(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant
    ): PlanSnapshot {
        validate(request)
        val verifiedBaseline = Engine1.generate(
            request.copy(policyChoice = "baseline"), owner, id, now
        )
        return verifiedBaseline.copy(
            engineVersion = version,
            policyId = policyId,
            request = request,
            explanations = listOf(
                "Engine 1.5 foundation candidate ($policyId). This batch preserves " +
                    "Engine 1.0 prescriptions, recovery spacing and schedule unchanged. " +
                    "No new adaptive coaching rules are active yet.",
                "This is an opt-in draft for compatibility testing. Review the " +
                    "individual scheduled sessions before approval; existing plans " +
                    "are not regenerated or replaced."
            ) + verifiedBaseline.explanations
        )
    }
}
