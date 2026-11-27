package io.stridelab.privatealpha

import java.util.Collections

/**
 * Explicit provenance authority classification separating unobserved, provisional,
 * projected (non-authoritative), sensor-supported, and verified anchors.
 */
internal enum class EvidenceAuthorityClass {
    UNOBSERVED,
    SELF_REPORTED_PROVISIONAL,
    PROJECTED_NON_AUTHORITATIVE,
    SENSOR_SUPPORTED,
    COACH_OR_RACE_VERIFIED;

    val isObservedAuthoritative: Boolean
        get() = this == SENSOR_SUPPORTED || this == COACH_OR_RACE_VERIFIED
}

/**
 * Explicitly distinguishes heuristic coaching guidance bands from statistical intervals
 * (Tier B shadow-only). Coaching guidance bands are never statistical confidence intervals.
 */
internal enum class UncertaintyKind {
    NONE,
    COACHING_GUIDANCE_BAND,
    STATISTICAL_INTERVAL_RESERVED
}

/**
 * Exhaustive typed uncertainty wrapper preventing bare nullable value access or silent
 * promotion of Unknown, Bounded, PriorOnly, Stale, or Conflicting evidence into capacity.
 */
internal sealed interface BeliefState<out T : CanonicalSiQuantity> {

    /**
     * True only for observed authoritative Estimated beliefs. This is a NECESSARY
     * precondition only — NEVER sufficient alone to authorize HR or pace zones,
     * which additionally require metric-specific physiological anchor type,
     * modality compatibility, temporal freshness, and non-quarantined quality in M2.
     */
    val canParticipateInZoneAuthorization: Boolean get() = false
    val isAuthoritativeObserved: Boolean get() = false

    fun canonicalRepresentation(): String

    data class Unknown(
        val reasonCode: String
    ) : BeliefState<Nothing> {
        init {
            require(reasonCode.isNotBlank() && reasonCode.length <= 128) {
                "Unknown BeliefState requires a bounded non-blank reasonCode"
            }
            require(reasonCode.none { it.isISOControl() }) {
                "STRUCTURAL_INPUT_INVALID: reasonCode must not contain control characters"
            }
        }

        override fun canonicalRepresentation(): String =
            "UNKNOWN|reason=${canonicalSegment(reasonCode)}"
    }

    data class Bounded<out T : CanonicalSiQuantity>(
        val lower: T? = null,
        val upper: T? = null,
        val authority: EvidenceAuthorityClass,
        val uncertaintyKind: UncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
    ) : BeliefState<T> {
        init {
            require(lower != null || upper != null) {
                "Bounded BeliefState requires at least one finite bound"
            }
            if (lower != null && upper != null) {
                require(lower.canonicalTypeTag == upper.canonicalTypeTag) {
                    "STRUCTURAL_INPUT_INVALID: incompatible bound units ${lower.canonicalTypeTag} vs ${upper.canonicalTypeTag}"
                }
                require(lower.siValue <= upper.siValue) {
                    "STRUCTURAL_INPUT_INVALID: lower (${lower.siValue}) > upper (${upper.siValue})"
                }
            }
            require(authority != EvidenceAuthorityClass.UNOBSERVED) {
                "Bounded BeliefState with bounds cannot claim UNOBSERVED authority"
            }
            require(authority != EvidenceAuthorityClass.PROJECTED_NON_AUTHORITATIVE) {
                "INV-02 violation: Bounded BeliefState cannot claim PROJECTED_NON_AUTHORITATIVE authority"
            }
            require(uncertaintyKind != UncertaintyKind.STATISTICAL_INTERVAL_RESERVED) {
                "Tier A Bounded BeliefState must not claim statistical interval authority"
            }
        }

        override fun canonicalRepresentation(): String =
            "BOUNDED|auth=${canonicalSegment(authority.name)}|unc=${canonicalSegment(uncertaintyKind.name)}|" +
                "lo=${lower?.let { canonicalSegment(it.canonicalToken()) } ?: "0:NONE"}|" +
                "hi=${upper?.let { canonicalSegment(it.canonicalToken()) } ?: "0:NONE"}"
    }

    data class PriorOnly<out T : CanonicalSiQuantity>(
        val prior: T,
        val priorSource: String,
        val authorizesZones: Boolean = false
    ) : BeliefState<T> {
        init {
            require(priorSource.isNotBlank() && priorSource.length <= 128) {
                "PriorOnly BeliefState requires a bounded non-blank priorSource"
            }
            require(priorSource.none { it.isISOControl() }) {
                "STRUCTURAL_INPUT_INVALID: priorSource must not contain control characters"
            }
            require(!authorizesZones) {
                "INV-05 violation: PriorOnly beliefs must never authorize HR or pace zones"
            }
        }

        override fun canonicalRepresentation(): String =
            "PRIOR_ONLY|src=${canonicalSegment(priorSource)}|authZones=$authorizesZones|" +
                "val=${canonicalSegment(prior.canonicalToken())}"
    }

    data class Estimated<out T : CanonicalSiQuantity>(
        val estimate: T,
        val authority: EvidenceAuthorityClass,
        val uncertaintyKind: UncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND,
        val guidanceSpreadFactor: Double? = null
    ) : BeliefState<T> {
        val normalizedGuidanceSpreadFactor: Double? = guidanceSpreadFactor?.let { factor ->
            val clean = CanonicalSiQuantity.requirePositive(factor, "guidanceSpreadFactor")
            require(clean >= 1.0) {
                "STRUCTURAL_INPUT_INVALID: guidanceSpreadFactor must be >= 1.0, was $clean"
            }
            clean
        }

        override val canParticipateInZoneAuthorization: Boolean get() = authority.isObservedAuthoritative
        override val isAuthoritativeObserved: Boolean get() = authority.isObservedAuthoritative

        init {
            require(authority != EvidenceAuthorityClass.UNOBSERVED) {
                "Estimated BeliefState cannot claim UNOBSERVED authority"
            }
            require(authority != EvidenceAuthorityClass.PROJECTED_NON_AUTHORITATIVE) {
                "INV-02 violation: projected values cannot become Estimated observed capacity"
            }
            require(uncertaintyKind != UncertaintyKind.STATISTICAL_INTERVAL_RESERVED) {
                "Tier A Estimated BeliefState must not claim statistical interval authority"
            }
            if (uncertaintyKind == UncertaintyKind.NONE) {
                require(normalizedGuidanceSpreadFactor == null || normalizedGuidanceSpreadFactor == 1.0) {
                    "STRUCTURAL_INPUT_INVALID: UncertaintyKind.NONE cannot pair with guidanceSpreadFactor $normalizedGuidanceSpreadFactor"
                }
            }
        }

        override fun canonicalRepresentation(): String {
            val spreadToken = normalizedGuidanceSpreadFactor?.let {
                CanonicalSiQuantity.canonicalDecimalString(it)
            } ?: "NONE"
            return "ESTIMATED|auth=${canonicalSegment(authority.name)}|unc=${canonicalSegment(uncertaintyKind.name)}|" +
                "spread=${canonicalSegment(spreadToken)}|val=${canonicalSegment(estimate.canonicalToken())}"
        }
    }

    data class Stale<out T : CanonicalSiQuantity>(
        val lastValue: T,
        val ageDays: Int,
        val authority: EvidenceAuthorityClass
    ) : BeliefState<T> {
        init {
            require(ageDays > 0) { "Stale BeliefState requires positive ageDays, was $ageDays" }
            require(authority != EvidenceAuthorityClass.UNOBSERVED) {
                "Stale BeliefState cannot claim UNOBSERVED authority"
            }
            require(authority != EvidenceAuthorityClass.PROJECTED_NON_AUTHORITATIVE) {
                "INV-02 violation: Stale BeliefState cannot claim PROJECTED_NON_AUTHORITATIVE authority"
            }
        }

        override fun canonicalRepresentation(): String =
            "STALE|age=$ageDays|auth=${canonicalSegment(authority.name)}|val=${canonicalSegment(lastValue.canonicalToken())}"
    }

    class Conflicting<out T : CanonicalSiQuantity>(
        candidates: List<T>,
        val conflictReason: String
    ) : BeliefState<T> {
        val candidates: List<T>

        init {
            require(candidates.size >= 2) {
                "Conflicting BeliefState requires at least 2 candidates, had ${candidates.size}"
            }
            require(conflictReason.isNotBlank() && conflictReason.length <= 128) {
                "Conflicting BeliefState requires a bounded non-blank conflictReason"
            }
            require(conflictReason.none { it.isISOControl() }) {
                "STRUCTURAL_INPUT_INVALID: conflictReason must not contain control characters"
            }
            require(candidates.map { it.canonicalTypeTag }.distinct().size == 1) {
                "STRUCTURAL_INPUT_INVALID: Conflicting candidates must share a single SI dimension"
            }
            require(candidates.map { it.siValue }.distinct().size >= 2) {
                "Conflicting BeliefState requires at least 2 distinct candidate values"
            }
            val sorted = candidates.sortedWith(
                compareBy<T> { it.canonicalTypeTag }.thenBy { it.siValue }
            )
            this.candidates = Collections.unmodifiableList(ArrayList(sorted))
        }

        override fun canonicalRepresentation(): String {
            val tokens = candidates.joinToString(",") { canonicalSegment(it.canonicalToken()) }
            return "CONFLICTING|reason=${canonicalSegment(conflictReason)}|cands=${candidates.size}:[$tokens]"
        }

        override fun equals(other: Any?): Boolean =
            other is Conflicting<*> &&
                conflictReason == other.conflictReason &&
                candidates == other.candidates

        override fun hashCode(): Int = 31 * conflictReason.hashCode() + candidates.hashCode()

        override fun toString(): String =
            "Conflicting(candidates=$candidates, conflictReason='$conflictReason')"
    }

    companion object {
        private fun canonicalSegment(raw: String): String =
            "${raw.length}:${raw}"
    }
}
