package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Collections
import java.util.TreeMap

/**
 * Co-equal discipline keys matching EngineContracts.kt ("run", "str", "cal", "cond").
 */
internal enum class AthleteDiscipline(val code: String) {
    RUN("run"),
    STRENGTH("str"),
    CALISTHENICS("cal"),
    CONDITIONING("cond");

    companion object {
        fun fromCode(code: String): AthleteDiscipline =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unsupported discipline code: $code")
    }
}

/**
 * Canonical history coverage status preserving INV-04:
 * MISSING_UNOBSERVED is never equated with CONFIRMED_ZERO.
 */
internal enum class HistoryCoverageStatus {
    MISSING_UNOBSERVED,
    CONFIRMED_ZERO,
    PROVISIONAL_SELF_REPORT,
    PARTIAL_COVERAGE,
    CONTINUOUS_COVERAGE;

    companion object {
        fun fromRecentMileageBasis(basis: RecentMileageBasis): HistoryCoverageStatus = when (basis) {
            RecentMileageBasis.CONFIRMED_ZERO -> CONFIRMED_ZERO
            RecentMileageBasis.SELF_REPORTED_AVERAGE -> PROVISIONAL_SELF_REPORT
            RecentMileageBasis.RECORDED_PARTIAL -> PARTIAL_COVERAGE
            RecentMileageBasis.UNKNOWN,
            RecentMileageBasis.PEAK_WEEK_ONLY,
            RecentMileageBasis.LEGACY_UNSPECIFIED -> MISSING_UNOBSERVED
        }
    }
}

/**
 * Immutable discipline-scoped evidence summary inside AthleteStateSnapshot.
 * Each discipline maintains independent coverage, volume/longest beliefs, and evidence IDs.
 */
internal class DisciplineStateSummary(
    val discipline: AthleteDiscipline,
    val coverage: HistoryCoverageStatus,
    val weeklyVolumeBelief: BeliefState<Meters>,
    val longestSessionBelief: BeliefState<Meters>,
    evidenceIds: Set<String> = emptySet()
) {
    val evidenceIds: Set<String>

    init {
        if (coverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
            require(weeklyVolumeBelief !is BeliefState.Estimated || weeklyVolumeBelief.estimate.value == 0.0) {
                "CONFIRMED_ZERO cannot pair with positive Estimated weeklyVolumeBelief"
            }
        }
        val cleaned = evidenceIds.map { id ->
            RecordPolicy.identifier(id)
            id
        }.sorted()
        this.evidenceIds = Collections.unmodifiableSet(LinkedHashSet(cleaned))
    }

    internal fun canonicalRecord(): String = buildString {
        append("disc=").append(AthleteStateSnapshot.canonicalSegment(discipline.code))
        append("|cov=").append(AthleteStateSnapshot.canonicalSegment(coverage.name))
        append("|wkVol=").append(AthleteStateSnapshot.canonicalSegment(weeklyVolumeBelief.canonicalRepresentation()))
        append("|longSess=").append(AthleteStateSnapshot.canonicalSegment(longestSessionBelief.canonicalRepresentation()))
        append("|ev=").append(AthleteStateSnapshot.canonicalList(evidenceIds))
    }

    override fun equals(other: Any?): Boolean =
        other is DisciplineStateSummary &&
            discipline == other.discipline &&
            coverage == other.coverage &&
            weeklyVolumeBelief == other.weeklyVolumeBelief &&
            longestSessionBelief == other.longestSessionBelief &&
            evidenceIds == other.evidenceIds

    override fun hashCode(): Int {
        var result = discipline.hashCode()
        result = 31 * result + coverage.hashCode()
        result = 31 * result + weeklyVolumeBelief.hashCode()
        result = 31 * result + longestSessionBelief.hashCode()
        result = 31 * result + evidenceIds.hashCode()
        return result
    }
}

/**
 * Single authoritative immutable point-in-time athlete state snapshot (M1A foundation).
 *
 * - Content identity (`contentHash` / `snapshotId`) is computed deterministically over
 *   canonically ordered schemaVersion, policyVersion, accountId, discipline states,
 *   evidenceIds, and activeRestrictionIds.
 * - `evaluatedAtUtc` represents freshness/evaluation context and is intentionally excluded
 *   from `contentHash` so identical evidence evaluated at two timestamps keeps identical
 *   content identity while remaining distinguishable as evaluation context.
 */
internal class AthleteStateSnapshot(
    val accountId: String,
    val evaluatedAtUtc: Instant,
    disciplines: Map<AthleteDiscipline, DisciplineStateSummary>,
    evidenceIds: Set<String> = emptySet(),
    activeRestrictionIds: Set<String> = emptySet(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val policyVersion: String = CURRENT_POLICY_VERSION,
    val observedWorkload: ObservedWorkloadState? = null,
    val projectedWorkload: ProjectedWorkloadState? = null
) {
    val disciplines: Map<AthleteDiscipline, DisciplineStateSummary>
    val evidenceIds: Set<String>
    val activeRestrictionIds: Set<String>
    val contentHash: String
    val snapshotId: String

    init {
        RecordPolicy.identifier(accountId)
        require(schemaVersion > 0) { "schemaVersion must be positive" }
        RecordPolicy.identifier(policyVersion)
        require(observedWorkload == null || observedWorkload.accountId == accountId) {
            "INV-03 violation: observedWorkload accountId must match AthleteStateSnapshot.accountId"
        }
        require(projectedWorkload == null || projectedWorkload.accountId == accountId) {
            "INV-03 violation: projectedWorkload accountId must match AthleteStateSnapshot.accountId"
        }

        val sortedDisciplines = TreeMap<AthleteDiscipline, DisciplineStateSummary>()
        for ((key, summary) in disciplines) {
            require(summary.discipline == key) {
                "Discipline key $key does not match summary.discipline ${summary.discipline}"
            }
            sortedDisciplines[key] = summary
        }
        this.disciplines = Collections.unmodifiableMap(sortedDisciplines)

        val sortedEvidence = evidenceIds.map { id ->
            RecordPolicy.identifier(id)
            id
        }.sorted()
        this.evidenceIds = Collections.unmodifiableSet(LinkedHashSet(sortedEvidence))

        val sortedRestrictions = activeRestrictionIds.map { id ->
            RecordPolicy.identifier(id)
            id
        }.sorted()
        this.activeRestrictionIds = Collections.unmodifiableSet(LinkedHashSet(sortedRestrictions))

        this.contentHash = computeSha256(canonicalPayload())
        this.snapshotId = "athsnap-v$schemaVersion-${contentHash.take(16)}"
    }

    internal fun canonicalPayload(): String = buildString {
        append("schema=").append(schemaVersion)
        append("|policy=").append(canonicalSegment(policyVersion))
        append("|account=").append(canonicalSegment(accountId))
        append("|disciplines=").append(canonicalList(disciplines.values.map { it.canonicalRecord() }))
        append("|evidence=").append(canonicalList(evidenceIds))
        append("|restrictions=").append(canonicalList(activeRestrictionIds))
        append("|obsHash=").append(observedWorkload?.contentHash?.let { canonicalSegment(it) } ?: "0:NONE")
        append("|projHash=").append(projectedWorkload?.contentHash?.let { canonicalSegment(it) } ?: "0:NONE")
    }

    /**
     * Cache/decision authorization key combining content identity with evaluation timestamp,
     * schema version, and policy version. Future decision caches MUST NOT treat `contentHash`
     * alone as proof of temporal freshness or current policy applicability.
     */
    fun evaluationAuthorizationKey(): String {
        val authPayload = buildString {
            append("content=").append(canonicalSegment(contentHash))
            append("|evalUtc=").append(canonicalSegment(evaluatedAtUtc.toString()))
            append("|schema=").append(schemaVersion)
            append("|policy=").append(canonicalSegment(policyVersion))
        }
        return computeSha256(authPayload)
    }

    /**
     * Checks temporal freshness explicitly against [referenceUtc] and [maxAgeSeconds].
     * Future-dated evaluation timestamps fail closed (return false).
     */
    fun isWithinFreshnessWindow(referenceUtc: Instant, maxAgeSeconds: Long): Boolean {
        require(maxAgeSeconds >= 0L) { "maxAgeSeconds must be non-negative, was $maxAgeSeconds" }
        if (evaluatedAtUtc.isAfter(referenceUtc)) return false
        val ageSeconds = referenceUtc.epochSecond - evaluatedAtUtc.epochSecond
        val nanoAdjust = if (referenceUtc.nano < evaluatedAtUtc.nano) 1L else 0L
        return (ageSeconds - nanoAdjust) <= maxAgeSeconds
    }

    override fun equals(other: Any?): Boolean =
        other is AthleteStateSnapshot &&
            schemaVersion == other.schemaVersion &&
            policyVersion == other.policyVersion &&
            accountId == other.accountId &&
            evaluatedAtUtc == other.evaluatedAtUtc &&
            disciplines == other.disciplines &&
            evidenceIds == other.evidenceIds &&
            activeRestrictionIds == other.activeRestrictionIds &&
            observedWorkload == other.observedWorkload &&
            projectedWorkload == other.projectedWorkload

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + policyVersion.hashCode()
        result = 31 * result + accountId.hashCode()
        result = 31 * result + evaluatedAtUtc.hashCode()
        result = 31 * result + disciplines.hashCode()
        result = 31 * result + evidenceIds.hashCode()
        result = 31 * result + activeRestrictionIds.hashCode()
        result = 31 * result + (observedWorkload?.hashCode() ?: 0)
        result = 31 * result + (projectedWorkload?.hashCode() ?: 0)
        return result
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * Canonical specification registry version identified in Track-9-Part-3-source.md
         * alongside CANONICAL-ERRATA.md (superseding COACHING-POLICY-REGISTRY.md's v1 header).
         */
        const val CANONICAL_SPEC_POLICY_VERSION = "policy-registry-1.5.0-v1.1"

        /**
         * Active runtime identifier scoped strictly to the implemented M1 foundation contracts.
         * Does NOT imply deferred M2-M9 policies (POL-009..064) or Tier B/C models are active.
         */
        const val CURRENT_POLICY_VERSION = "policy-registry-1.5.0-v1.1-m1-foundation"

        fun fromWorkloads(
            accountId: String,
            evaluatedAtUtc: Instant,
            observedWorkload: ObservedWorkloadState,
            projectedWorkload: ProjectedWorkloadState? = null,
            evidenceIds: Set<String> = emptySet(),
            activeRestrictionIds: Set<String> = emptySet()
        ): AthleteStateSnapshot {
            require(observedWorkload.accountId == accountId) {
                "INV-03 violation: observedWorkload.accountId != accountId"
            }
            require(projectedWorkload == null || projectedWorkload.accountId == accountId) {
                "INV-03 violation: projectedWorkload.accountId != accountId"
            }
            val summaries = TreeMap<AthleteDiscipline, DisciplineStateSummary>()
            val combinedEvidence = LinkedHashSet(evidenceIds)
            for ((disc, workload) in observedWorkload.disciplines) {
                val discEvidence = workload.exposures.map { it.exposureId }.toSet()
                combinedEvidence += discEvidence
                summaries[disc] = DisciplineStateSummary(
                    discipline = disc,
                    coverage = workload.coverage,
                    weeklyVolumeBelief = workload.rolling28dWeeklyAverageBelief,
                    longestSessionBelief = workload.preceding30dLongestSessionBelief,
                    evidenceIds = discEvidence
                )
            }
            return AthleteStateSnapshot(
                accountId = accountId,
                evaluatedAtUtc = evaluatedAtUtc,
                disciplines = summaries,
                evidenceIds = combinedEvidence,
                activeRestrictionIds = activeRestrictionIds,
                observedWorkload = observedWorkload,
                projectedWorkload = projectedWorkload
            )
        }

        internal fun canonicalSegment(raw: String): String = "${raw.length}:$raw"

        internal fun canonicalList(items: Collection<String>): String =
            "${items.size}:[${items.joinToString(",") { canonicalSegment(it) }}]"

        private fun computeSha256(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}
