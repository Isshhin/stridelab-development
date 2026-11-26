package io.stridelab.privatealpha

import java.time.Instant
import java.util.Collections

internal enum class M2BHeartRateEvidenceKind {
    SELF_REPORTED_MAXIMUM, OBSERVED_PEAK, CONFIGURED_ZONES, IMPORTED_ZONES,
    REPORTED_VERIFIED_MAXIMUM, REPORTED_VERIFIED_LT1, REPORTED_VERIFIED_LT2
}

internal data class M2BHeartRateEvidenceScope(
    val accountId: String,
    val discipline: AthleteDiscipline,
    val physiologicalModality: M2BPhysiologicalModality,
    val source: M2ASourceQualifiedAlias,
    val completedAt: Instant
)

internal sealed interface M2BHeartRateEvidence {
    val scope: M2BHeartRateEvidenceScope
    val kind: M2BHeartRateEvidenceKind

    data class SelfReportedMaximum(
        override val scope: M2BHeartRateEvidenceScope,
        val value: BeatsPerSecond
    ) : M2BHeartRateEvidence {
        override val kind = M2BHeartRateEvidenceKind.SELF_REPORTED_MAXIMUM
    }

    data class ObservedPeak(
        override val scope: M2BHeartRateEvidenceScope,
        val value: BeatsPerSecond,
        val sensor: HeartRateSensorModality,
        val quality: TelemetryQualityStatus
    ) : M2BHeartRateEvidence {
        override val kind = M2BHeartRateEvidenceKind.OBSERVED_PEAK
    }

    class Zones(
        override val scope: M2BHeartRateEvidenceScope,
        val imported: Boolean,
        floors: List<BeatsPerSecond>
    ) : M2BHeartRateEvidence {
        val floors: List<BeatsPerSecond> = Collections.unmodifiableList(ArrayList(floors))
        override val kind = if (imported) M2BHeartRateEvidenceKind.IMPORTED_ZONES
            else M2BHeartRateEvidenceKind.CONFIGURED_ZONES

        init {
            require(this.floors.size >= 2 && this.floors.zipWithNext().all { (a, b) -> a < b })
        }
    }

    /** The protocol label is carried from intake. M9 must independently authenticate it. */
    data class ReportedVerifiedAnchor(
        override val scope: M2BHeartRateEvidenceScope,
        override val kind: M2BHeartRateEvidenceKind,
        val value: BeatsPerSecond,
        val sensor: HeartRateSensorModality,
        val quality: TelemetryQualityStatus,
        val protocolId: String
    ) : M2BHeartRateEvidence {
        init {
            require(kind in setOf(M2BHeartRateEvidenceKind.REPORTED_VERIFIED_MAXIMUM,
                M2BHeartRateEvidenceKind.REPORTED_VERIFIED_LT1, M2BHeartRateEvidenceKind.REPORTED_VERIFIED_LT2))
            RecordPolicy.identifier(protocolId)
        }
    }
}

internal class M2BHeartRateHierarchy private constructor(
    val educationalPrior: M2BTanakaPrior,
    val evidence: List<M2BHeartRateEvidence>,
    val conflicts: Set<M2BHeartRateEvidenceKind>,
    val observedPeakLowerBound: BeliefState<BeatsPerSecond>,
    val selfReportedMaximum: BeliefState<BeatsPerSecond>,
    val quarantined: Boolean
) {
    val coachingAuthorized: Boolean get() = false

    fun unambiguousEvidence(kind: M2BHeartRateEvidenceKind): List<M2BHeartRateEvidence> =
        if (kind in conflicts) emptyList() else Collections.unmodifiableList(evidence.filter { it.kind == kind })

    companion object {
        fun reconcile(
            context: M2BEvaluationContext,
            prior: M2BTanakaPrior,
            supplied: List<M2BHeartRateEvidence>,
            quarantined: Boolean
        ): M2BHeartRateHierarchy {
            val eligible = supplied.filter {
                it.scope.accountId == context.accountId && it.scope.discipline == context.discipline &&
                    it.scope.physiologicalModality == context.physiologicalModality &&
                    context.physiologicalModality != M2BPhysiologicalModality.UNSPECIFIED &&
                    it.scope.completedAt <= context.asOf &&
                    when (it) {
                        is M2BHeartRateEvidence.ObservedPeak -> !quarantined &&
                            usable(it.value, it.sensor, it.quality)
                        is M2BHeartRateEvidence.ReportedVerifiedAnchor -> !quarantined &&
                            usable(it.value, it.sensor, it.quality)
                        is M2BHeartRateEvidence.SelfReportedMaximum -> plausible(it.value)
                        is M2BHeartRateEvidence.Zones -> true
                    }
            }.distinctBy(::m2bHREvidenceKey).sortedBy(::m2bHREvidenceKey)
            val conflictingIdentities = eligible.groupBy { it.kind to it.scope.source.canonicalToken() }
                .filterValues { it.size > 1 }.keys
            val consistent = eligible.filterNot { (it.kind to it.scope.source.canonicalToken()) in conflictingIdentities }
            val conflicts = conflictingIdentities.mapTo(sortedSetOf()) { it.first }
            val metrics = consistent.filter { it.kind != M2BHeartRateEvidenceKind.OBSERVED_PEAK }
                .groupBy { it.kind }.filterValues { records -> records.map(::valueKey).distinct().size > 1 }
            conflicts.addAll(metrics.keys)
            val peak = if (M2BHeartRateEvidenceKind.OBSERVED_PEAK !in conflicts) {
                consistent.filterIsInstance<M2BHeartRateEvidence.ObservedPeak>().maxOfOrNull { it.value }
            } else null
            val selfReport = if (M2BHeartRateEvidenceKind.SELF_REPORTED_MAXIMUM !in conflicts) {
                consistent.filterIsInstance<M2BHeartRateEvidence.SelfReportedMaximum>().firstOrNull()?.value
            } else null
            return M2BHeartRateHierarchy(prior, Collections.unmodifiableList(eligible), Collections.unmodifiableSet(conflicts),
                peak?.let { BeliefState.Bounded(lower = it, authority = EvidenceAuthorityClass.SENSOR_SUPPORTED) }
                    ?: BeliefState.Unknown("NO_UNAMBIGUOUS_RELIABLE_PEAK"),
                selfReport?.let { BeliefState.Estimated(it, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL) }
                    ?: BeliefState.Unknown("NO_UNAMBIGUOUS_SELF_REPORTED_MAXIMUM"),
                quarantined)
        }

        private fun plausible(value: BeatsPerSecond): Boolean =
            value.toBpm() in Engine15M1PolicyRegistry.POL_005_MIN_PLAUSIBLE_HR_BPM..
                Engine15M1PolicyRegistry.POL_005_MAX_PLAUSIBLE_HR_BPM

        private fun usable(value: BeatsPerSecond, sensor: HeartRateSensorModality, quality: TelemetryQualityStatus) =
            plausible(value) && sensor != HeartRateSensorModality.UNKNOWN && quality == TelemetryQualityStatus.VALID_OBSERVED
    }
}

private fun valueKey(value: M2BHeartRateEvidence): String = when (value) {
    is M2BHeartRateEvidence.SelfReportedMaximum -> value.value.canonicalToken()
    is M2BHeartRateEvidence.ObservedPeak -> value.value.canonicalToken()
    is M2BHeartRateEvidence.ReportedVerifiedAnchor -> value.value.canonicalToken()
    is M2BHeartRateEvidence.Zones -> value.floors.joinToString(",") { it.canonicalToken() }
}

internal fun m2bHREvidenceKey(value: M2BHeartRateEvidence): String = m2bCanonical(
    value.kind, value.scope.accountId, value.scope.discipline, value.scope.physiologicalModality,
    value.scope.source.canonicalToken(), value.scope.completedAt, valueKey(value),
    (value as? M2BHeartRateEvidence.ObservedPeak)?.sensor,
    (value as? M2BHeartRateEvidence.ObservedPeak)?.quality,
    (value as? M2BHeartRateEvidence.ReportedVerifiedAnchor)?.protocolId,
    (value as? M2BHeartRateEvidence.ReportedVerifiedAnchor)?.sensor,
    (value as? M2BHeartRateEvidence.ReportedVerifiedAnchor)?.quality
)
