package io.stridelab.privatealpha

internal object Engine15M2BPolicyRegistry {
    const val SPEC_VERSION = Engine15M2APolicyRegistry.SPEC_VERSION
    const val POLICY_VERSION = "policy-registry-1.5.0-m2b-isolated-v1"
    const val GAP_MIN_GRADE = -0.45 // POL-014
    const val GAP_MAX_GRADE = 0.45
    const val GAP_MIN_METABOLIC_GRADE = -0.15
    const val ENVELOPE_MAX_AGE_DAYS = 60L // POL-019
    const val TANAKA_MIN_AGE_YEARS = 18 // MOD-07
    const val TANAKA_MAX_AGE_YEARS = 90
    const val TANAKA_RESIDUAL_FLOOR_BPM = 10.0
    const val POST_ILLNESS_MIN_QUARANTINE_DAYS = 14L // POL-034
}
