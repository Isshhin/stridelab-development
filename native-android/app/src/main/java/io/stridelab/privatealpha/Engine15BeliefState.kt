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

    fun canonicalRepresentation(): String

    data class Unknown(
        val reasonCode: String
    ) : BeliefState<Nothing> {
        init {
            require(reasonCode.isNotBlank()) { "Unknown BeliefState requires a non-blank reasonCode" }
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
                require(lower.siValue <= upper.siValue) {
                    "STRUCTURAL_INPUT_INVALID: lower (${lower.siValue}) > upper (${upper.siValue})"
                }
            }
            require(authority != EvidenceAuthorityClass.UNOBSERVED) {
                "Bounded BeliefState with bounds cannot claim UNOBSERVED authority"
            }
        }

        override fun canonicalRepresentation(): String =
            "BOUNDED|auth=${authority.name}|unc=${uncertaintyKind.name}|" +
                "lo=${lower?.canonicalToken() ?: "NONE"}|hi=${upper?.canonicalToken() ?: "NONE"}"
    }

    data class PriorOnly<out T : CanonicalSiQuantity>(
        val prior: T,
        val priorSource: String,
        val authorizesZones: Boolean = false
    ) : BeliefState<T> {
        init {
            require(priorSource.isNotBlank()) { "PriorOnly BeliefState requires a priorSource" }
            require(!authorizesZones) {
                "INV-05 violation: PriorOnly beliefs must never authorize HR or pace zones"
            }
        }

        override fun canonicalRepresentation(): String =
            "PRIOR_ONLY|src=${canonicalSegment(priorSource)}|authZones=$authorizesZones|" +
                "val=${prior.canonicalToken()}"
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
        }

        override fun canonicalRepresentation(): String {
            val spreadToken = normalizedGuidanceSpreadFactor?.let {
                CanonicalSiQuantity.canonicalDecimalString(it)
            } ?: "NONE"
            return "ESTIMATED|auth=${authority.name}|unc=${uncertaintyKind.name}|" +
                "spread=$spreadToken|val=${estimate.canonicalToken()}"
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
        }

        override fun canonicalRepresentation(): String =
            "STALE|age=$ageDays|auth=${authority.name}|val=${lastValue.canonicalToken()}"
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
            require(conflictReason.isNotBlank()) { "Conflicting BeliefState requires a conflictReason" }
            val sorted = candidates.sortedWith(
                compareBy<T> { it.canonicalTypeTag }.thenBy { it.siValue }
            )
            this.candidates = Collections.unmodifiableList(ArrayList(sorted))
        }

        override fun canonicalRepresentation(): String {
            val tokens = candidates.joinToString(",") { it.canonicalToken() }
            return "CONFLICTING|reason=${canonicalSegment(conflictReason)}|cands=[$tokens]"
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
