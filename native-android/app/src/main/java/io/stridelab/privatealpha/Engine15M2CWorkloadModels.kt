package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.Locale
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.max

// =============================================================================
// SECTION 1: CANONICAL FRAMING, COMPENSATED SUMMATION, TRUST & TYPED QUANTITIES
// =============================================================================

internal object Engine15M2CCanonical {
    fun frame(vararg values: Any?): String = m2bCanonical(*values)

    fun normalizeAlias(alias: M2ASourceQualifiedAlias): M2ASourceQualifiedAlias =
        M2ASourceQualifiedAlias(alias.normalizedProvider, alias.externalId)

    fun aliasOf(provider: String, externalId: String): M2ASourceQualifiedAlias {
        val norm = provider.trim().lowercase(Locale.ROOT)
        require(norm.isNotBlank() && norm.length <= 128 && norm.none { it.isISOControl() }) {
            "provider must be non-blank, <= 128 chars, and free of ISO control characters"
        }
        return M2ASourceQualifiedAlias(norm, externalId)
    }

    fun aliasesFromCanonicalActivity(activity: CanonicalActivity): Set<M2ASourceQualifiedAlias> {
        val out = linkedSetOf<M2ASourceQualifiedAlias>()
        for (comp in activity.rawComponents) {
            val prov = comp.provider.trim().lowercase(Locale.ROOT)
            if (prov.isBlank() || prov.length > 128 || prov.any { it.isISOControl() }) continue
            runCatching { M2ASourceQualifiedAlias(prov, comp.recordId) }.getOrNull()?.let { out += it }
            comp.externalId?.let { ext ->
                runCatching { M2ASourceQualifiedAlias(prov, ext) }.getOrNull()?.let { out += it }
            }
        }
        return Collections.unmodifiableSet(LinkedHashSet(out.sorted()))
    }

    fun sourceIdentityTokens(activity: CanonicalActivity): Set<String> = buildSet {
        aliasesFromCanonicalActivity(activity).forEach { add("a:${it.canonicalToken()}") }
        activity.rawComponents.mapNotNull { it.sha256 }.forEach { add("s:${it.lowercase(Locale.ROOT)}") }
    }

    /** Neumaier compensated summation with non-finite rejection and -0.0 normalization. */
    fun compensatedSum(values: List<Double>): Double {
        var sum = 0.0
        var comp = 0.0
        for (v in values) {
            if (!v.isFinite()) return Double.NaN
            val t = sum + v
            if (!t.isFinite()) return Double.NaN
            comp += if (abs(sum) >= abs(v)) (sum - t) + v else (v - t) + sum
            sum = t
        }
        val total = sum + comp
        return if (!total.isFinite()) Double.NaN else if (total == 0.0) 0.0 else total
    }

    /** Exact completion instant matching frozen M2-B m2bSessionEnd nanosecond rounding (M2C-A06). */
    fun completedInstant(startedAtUtc: Instant, durationSeconds: Double): Instant? {
        if (!durationSeconds.isFinite() || durationSeconds < 0.0 ||
            durationSeconds > Engine15M2CPolicyRegistry.MAX_PLAUSIBLE_SESSION_DURATION_SECONDS
        ) return null
        val wholeSec = durationSeconds.toLong()
        val nanos = Math.round((durationSeconds - wholeSec) * 1_000_000_000.0)
        return runCatching { startedAtUtc.plusSeconds(wholeSec).plusNanos(nanos) }.getOrNull()
    }

    fun sha256Hex(payload: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

/** Explicit evidence trust tier preventing caller Booleans from conferring trusted custody (M2C-A01). */
internal enum class M2CEvidenceTrustTier {
    REPORTED_UNVERIFIED,
    STRUCTURALLY_VALIDATED_OBSERVED,
    INDEPENDENTLY_AUTHENTICATED_M9;

    val isIndependentlyAuthenticated: Boolean
        get() = this == INDEPENDENTLY_AUTHENTICATED_M9 &&
            Engine15M2CPolicyRegistry.TRUSTED_M9_EXECUTION_CUSTODY_ACTIVE
}

/** Explicit physiological/biomechanical modality separating running impact from cross-training (INV-06). */
internal enum class M2CModalityKind(val discipline: AthleteDiscipline, val producesRunningImpact: Boolean) {
    RUNNING(AthleteDiscipline.RUN, true),
    CYCLING(AthleteDiscipline.CONDITIONING, false),
    SWIMMING(AthleteDiscipline.CONDITIONING, false),
    ROWING(AthleteDiscipline.CONDITIONING, false),
    STRENGTH(AthleteDiscipline.STRENGTH, false),
    CALISTHENICS(AthleteDiscipline.CALISTHENICS, false),
    OTHER_CONDITIONING(AthleteDiscipline.CONDITIONING, false),
    UNKNOWN(AthleteDiscipline.CONDITIONING, false);

    companion object {
        fun infer(discipline: AthleteDiscipline, rawActivityType: String): M2CModalityKind {
            if (discipline == AthleteDiscipline.RUN) return RUNNING
            if (discipline == AthleteDiscipline.STRENGTH) return STRENGTH
            if (discipline == AthleteDiscipline.CALISTHENICS) return CALISTHENICS
            val norm = rawActivityType.trim().lowercase(Locale.ROOT).replace(Regex("[ _-]"), "")
            return when {
                norm.contains("cycl") || norm.contains("bike") || norm.contains("biking") || norm == "ride" -> CYCLING
                norm.contains("swim") -> SWIMMING
                norm.contains("row") -> ROWING
                norm.isNotBlank() -> OTHER_CONDITIONING
                else -> UNKNOWN
            }
        }
    }
}

/** Typed session-RPE internal load quantity in Arbitrary Units [AU ≡ min] (MOD-09, M2C-A05). */
internal class M2CSessionLoadAu private constructor(val value: Double) : Comparable<M2CSessionLoadAu> {
    fun canonicalToken(): String = "au:${CanonicalSiQuantity.canonicalDecimalString(value)}"
    override fun compareTo(other: M2CSessionLoadAu): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is M2CSessionLoadAu && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "${CanonicalSiQuantity.canonicalDecimalString(value)} AU"

    companion object {
        val ZERO: M2CSessionLoadAu = M2CSessionLoadAu(0.0)
        fun of(au: Double): M2CSessionLoadAu =
            M2CSessionLoadAu(CanonicalSiQuantity.requireNonNegative(au, "sessionLoadAu"))
    }
}

internal sealed interface M2CInternalLoadBelief {
    fun canonicalRepresentation(): String

    data class Unknown(val reasonCode: String) : M2CInternalLoadBelief {
        init { RecordPolicy.identifier(reasonCode) }
        override fun canonicalRepresentation(): String = Engine15M2CCanonical.frame("AU_UNKNOWN", reasonCode)
    }

    data class Bounded(
        val lower: M2CSessionLoadAu,
        val upper: M2CSessionLoadAu? = null,
        val authority: EvidenceAuthorityClass = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL
    ) : M2CInternalLoadBelief {
        init {
            if (upper != null) require(lower.value <= upper.value)
            require(authority != EvidenceAuthorityClass.UNOBSERVED && authority != EvidenceAuthorityClass.PROJECTED_NON_AUTHORITATIVE)
        }
        override fun canonicalRepresentation(): String =
            Engine15M2CCanonical.frame("AU_BOUNDED", authority.name, lower.canonicalToken(), upper?.canonicalToken())
    }

    data class Estimated(
        val value: M2CSessionLoadAu,
        val authority: EvidenceAuthorityClass = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL
    ) : M2CInternalLoadBelief {
        init {
            require(authority != EvidenceAuthorityClass.UNOBSERVED && authority != EvidenceAuthorityClass.PROJECTED_NON_AUTHORITATIVE)
        }
        override fun canonicalRepresentation(): String =
            Engine15M2CCanonical.frame("AU_ESTIMATED", authority.name, value.canonicalToken())
    }
}

// =============================================================================
// SECTION 2: ADDITIVE INGRESS EVIDENCE CONTRACTS (ZERO FROZEN-CLASS MUTATION)
// =============================================================================

internal data class M2CSessionInternalLoadInput(
    val accountId: String,
    val canonicalActivityId: String,
    val sourceAlias: M2ASourceQualifiedAlias,
    val cr10Rpe: Double?,
    val recordedAtUtc: Instant,
    val trustTier: M2CEvidenceTrustTier = M2CEvidenceTrustTier.REPORTED_UNVERIFIED
) {
    init {
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(canonicalActivityId)
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        accountId, canonicalActivityId, Engine15M2CCanonical.normalizeAlias(sourceAlias).canonicalToken(),
        cr10Rpe?.takeIf { it.isFinite() }?.let(CanonicalSiQuantity::canonicalDecimalString) ?: cr10Rpe?.toString(),
        recordedAtUtc, trustTier.name
    )
}

internal data class M2CWorkoutDomainBreakdownInput(
    val accountId: String,
    val canonicalActivityId: String,
    val sourceAlias: M2ASourceQualifiedAlias,
    val recordedAtUtc: Instant,
    val trustTier: M2CEvidenceTrustTier = M2CEvidenceTrustTier.REPORTED_UNVERIFIED,
    val moderateWorkSeconds: Double? = null,
    val heavyWorkSeconds: Double? = null,
    val severeWorkSeconds: Double? = null,
    val neuromuscularWorkSeconds: Double? = null,
    val warmUpCoolDownRecoverySeconds: Double? = null,
    val moderateWorkMeters: Double? = null,
    val heavyWorkMeters: Double? = null,
    val severeWorkMeters: Double? = null,
    val neuromuscularWorkMeters: Double? = null,
    val warmUpCoolDownRecoveryMeters: Double? = null
) {
    init {
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(canonicalActivityId)
    }

    fun canonicalRepresentation(): String {
        fun fmt(v: Double?): String? = v?.takeIf { it.isFinite() }?.let(CanonicalSiQuantity::canonicalDecimalString) ?: v?.toString()
        return Engine15M2CCanonical.frame(
            accountId, canonicalActivityId, Engine15M2CCanonical.normalizeAlias(sourceAlias).canonicalToken(),
            recordedAtUtc, trustTier.name,
            fmt(moderateWorkSeconds), fmt(heavyWorkSeconds), fmt(severeWorkSeconds),
            fmt(neuromuscularWorkSeconds), fmt(warmUpCoolDownRecoverySeconds),
            fmt(moderateWorkMeters), fmt(heavyWorkMeters), fmt(severeWorkMeters),
            fmt(neuromuscularWorkMeters), fmt(warmUpCoolDownRecoveryMeters)
        )
    }
}

/**
 * Scope-reconciled wrapper binding a frozen CanonicalActivity to optional RPE, intensity domain
 * breakdown, and M2BRunningStream without mutating frozen M1/M2-B classes (M2C-A02).
 */
internal class M2CCompletedActivityInput(
    val canonicalActivity: CanonicalActivity,
    val modalityRefinement: M2CModalityKind? = null,
    val internalLoad: M2CSessionInternalLoadInput? = null,
    val domainBreakdown: M2CWorkoutDomainBreakdownInput? = null,
    val runningStream: M2BRunningStream? = null,
    val corroborationToken: String? = null,
    val isProjected: Boolean = false
) {
    val resolvedModality: M2CModalityKind

    init {
        if (corroborationToken != null) RecordPolicy.identifier(corroborationToken)
        val inferred = M2CModalityKind.infer(canonicalActivity.discipline, canonicalActivity.rawActivityType)
        if (modalityRefinement != null) {
            require(modalityRefinement.discipline == canonicalActivity.discipline) {
                "M2C-A02: modalityRefinement ($modalityRefinement) must match discipline ${canonicalActivity.discipline}"
            }
            require(canonicalActivity.discipline == AthleteDiscipline.RUN || !modalityRefinement.producesRunningImpact) {
                "M2C-A02: non-running activity cannot be reclassified into running-impact exposure"
            }
            this.resolvedModality = modalityRefinement
        } else {
            this.resolvedModality = inferred
        }
        if (runningStream != null) {
            require(
                runningStream.activity.accountId == canonicalActivity.accountId &&
                    runningStream.activity.discipline == canonicalActivity.discipline &&
                    runningStream.activity.canonicalId == canonicalActivity.canonicalId &&
                    runningStream.activity.contentHash == canonicalActivity.contentHash &&
                    runningStream.sourceRecord() != null
            ) {
                "M2C-A02: runningStream must match exact account, discipline, canonicalId, contentHash, and source component"
            }
        }
        if (internalLoad != null) {
            require(internalLoad.accountId == canonicalActivity.accountId && internalLoad.canonicalActivityId == canonicalActivity.canonicalId)
        }
        if (domainBreakdown != null) {
            require(domainBreakdown.accountId == canonicalActivity.accountId && domainBreakdown.canonicalActivityId == canonicalActivity.canonicalId)
        }
    }
}

internal enum class M2CConfirmedWeekStatus {
    OBSERVED_ACTIVE_WEEK,
    CONFIRMED_SPARSE_OR_REST_WEEK,
    UNCONFIRMED_MISSING_DATA_GAP
}

internal data class M2CWeeklyConfirmationInput(
    val weekStartDate: LocalDate,
    val confirmedSparseOrRest: Boolean,
    val noteCode: String? = null
) {
    init { if (noteCode != null) RecordPolicy.identifier(noteCode) }
}

internal enum class M2CInterruptionKind {
    NONE, ORDINARY_BREAK, POST_ILLNESS, POST_INJURY_USER_REPORTED
}

internal data class M2CConfirmedInterruptionInput(
    val kind: M2CInterruptionKind,
    val confirmedBreakDays: Int,
    val illnessResolvedAtUtc: Instant? = null,
    val symptomFreeConfirmed: Boolean = false,
    val restingAndSubmaxHrStabilityConfirmed: Boolean = false,
    val userReportedClearanceToResumeRunning: Boolean = false
) {
    init { require(confirmedBreakDays >= 0) }
}

// =============================================================================
// SECTION 3: COVERAGE-AWARE BASELINE ARBITRATION (POL-011..013)
// =============================================================================

internal enum class M2CBaselineArbitrationStatus {
    CONFIRMED_ZERO,
    MISSING_UNOBSERVED,
    PROVISIONAL_SELF_REPORT,
    PARTIAL_RECORDED_WITH_SELF_REPORT,
    PARTIAL_RECORDED_ONLY,
    CONTINUOUS_RECORDED,
    CONFLICT_CONSERVATIVE_CEILING,
    CONTINUOUS_RECORDED_CONFLICT_ABOVE_SELF_REPORT
}

internal class M2CCoverageAwareBaseline(
    val coverage: HistoryCoverageStatus,
    val arbitrationStatus: M2CBaselineArbitrationStatus,
    val observedSpanDays: Int,
    val verifiedSessionCount28d: Int,
    val maxGapDays: Int?,
    val observedWeeklyMean: BeliefState<Meters>,
    val provisionalSelfReportWeekly: Meters?,
    val observedLowerBoundWeekly: Meters?,
    val conservativePolicyCeilingWeekly: Meters?,
    val pol013RecordedToSelfReportRatio: DimensionlessRatio?,
    val conflictDetected: Boolean,
    val selfReportReplacementAuthorized: Boolean,
    diagnosticFlags: Set<String>
) {
    val diagnosticFlags: Set<String>

    init {
        require(observedSpanDays >= 0 && verifiedSessionCount28d >= 0 && (maxGapDays == null || maxGapDays >= 0))
        if (arbitrationStatus == M2CBaselineArbitrationStatus.CONFIRMED_ZERO) require(coverage == HistoryCoverageStatus.CONFIRMED_ZERO)
        if (arbitrationStatus == M2CBaselineArbitrationStatus.CONFLICT_CONSERVATIVE_CEILING) {
            require(conflictDetected && conservativePolicyCeilingWeekly != null)
        }
        if (selfReportReplacementAuthorized) require(coverage == HistoryCoverageStatus.CONTINUOUS_COVERAGE)
        this.diagnosticFlags = Collections.unmodifiableSet(
            LinkedHashSet(diagnosticFlags.map { f -> RecordPolicy.identifier(f); f }.distinct().sorted())
        )
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        coverage.name, arbitrationStatus.name, observedSpanDays, verifiedSessionCount28d, maxGapDays,
        observedWeeklyMean.canonicalRepresentation(), provisionalSelfReportWeekly?.canonicalToken(),
        observedLowerBoundWeekly?.canonicalToken(), conservativePolicyCeilingWeekly?.canonicalToken(),
        pol013RecordedToSelfReportRatio?.canonicalToken(), conflictDetected, selfReportReplacementAuthorized,
        diagnosticFlags.joinToString(",")
    )

    override fun equals(other: Any?): Boolean = other is M2CCoverageAwareBaseline && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

// =============================================================================
// SECTION 4: ROLLING WINDOWS, RECONCILED INVENTORY, VALIDATOR, MOD-10 & MOD-11
// =============================================================================

internal enum class M2CRollingWindowKind(val spanDays: Int) {
    TRAILING_7D(Engine15M2CPolicyRegistry.WINDOW_7D_DAYS),
    TRAILING_28D(Engine15M2CPolicyRegistry.WINDOW_28D_DAYS),
    PRECEDING_30D(Engine15M2CPolicyRegistry.WINDOW_30D_DAYS),
    TRAILING_56D(Engine15M2CPolicyRegistry.WINDOW_56D_QUALITY_DAYS),
    PRECEDING_90D(Engine15M2CPolicyRegistry.WINDOW_90D_OVERRIDE_LOOKBACK_DAYS)
}

internal data class M2CWindowBounds(
    val kind: M2CRollingWindowKind,
    val startDateInclusive: LocalDate,
    val endDateInclusive: LocalDate,
    val evaluatedLocalDate: LocalDate,
    val evaluatedAtUtc: Instant,
    val zoneId: ZoneId
) {
    init {
        require(!startDateInclusive.isAfter(endDateInclusive))
        require(endDateInclusive == evaluatedLocalDate.minusDays(1))
        require(startDateInclusive == evaluatedLocalDate.minusDays(kind.spanDays.toLong()))
    }

    fun contains(localDate: LocalDate): Boolean = localDate in startDateInclusive..endDateInclusive

    fun canonicalRepresentation(): String =
        Engine15M2CCanonical.frame(kind.name, startDateInclusive, endDateInclusive, evaluatedLocalDate, evaluatedAtUtc, zoneId.id)
}

/**
 * Reconciled running session retaining M1C start-date attribution, exact completion timestamp,
 * cross-midnight marker, and explicit separation of source-qualified aliases vs opaque upstream IDs.
 */
internal class M2CVerifiedRunningSessionRecord(
    val canonicalActivityId: String,
    val accountId: String,
    val startedAtUtc: Instant,
    val completedAtUtc: Instant,
    val attributedLocalDate: LocalDate,
    val completedLocalDate: LocalDate,
    val crossMidnightStartToEnd: Boolean,
    val distance: Meters?,
    val duration: Seconds?,
    val distanceQuality: TelemetryQualityStatus,
    val durationQuality: TelemetryQualityStatus,
    val hrQuality: TelemetryQualityStatus,
    val workloadEligibility: WorkloadAggregationEligibility,
    val fromCompletedPlanLogOnly: Boolean,
    val sameDayAsEvaluation: Boolean,
    val ambiguityGroupKey: String?,
    sourceAliases: Set<M2ASourceQualifiedAlias>,
    opaqueUpstreamRecordIds: Set<String> = emptySet()
) : Comparable<M2CVerifiedRunningSessionRecord> {
    val sourceAliases: Set<M2ASourceQualifiedAlias>
    val opaqueUpstreamRecordIds: Set<String>

    init {
        RecordPolicy.identifier(canonicalActivityId)
        RecordPolicy.identifier(accountId)
        require(!completedAtUtc.isBefore(startedAtUtc))
        if (ambiguityGroupKey != null) RecordPolicy.identifier(ambiguityGroupKey)
        val cleanAliases = sourceAliases.map(Engine15M2CCanonical::normalizeAlias).distinct().sorted()
        val cleanOpaque = opaqueUpstreamRecordIds.map { id -> RecordPolicy.identifier(id); id }.distinct().sorted()
        require(cleanAliases.isNotEmpty() || cleanOpaque.isNotEmpty())
        this.sourceAliases = Collections.unmodifiableSet(LinkedHashSet(cleanAliases))
        this.opaqueUpstreamRecordIds = Collections.unmodifiableSet(LinkedHashSet(cleanOpaque))
    }

    override fun compareTo(other: M2CVerifiedRunningSessionRecord): Int =
        compareBy<M2CVerifiedRunningSessionRecord> { it.attributedLocalDate }
            .thenBy { it.completedAtUtc }
            .thenBy { it.startedAtUtc }
            .thenBy { it.canonicalActivityId }
            .compare(this, other)

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        canonicalActivityId, accountId, startedAtUtc, completedAtUtc, attributedLocalDate, completedLocalDate,
        crossMidnightStartToEnd, distance?.canonicalToken(), duration?.canonicalToken(),
        distanceQuality.name, durationQuality.name, hrQuality.name, workloadEligibility.name,
        fromCompletedPlanLogOnly, sameDayAsEvaluation, ambiguityGroupKey,
        sourceAliases.joinToString(",") { it.canonicalToken() },
        opaqueUpstreamRecordIds.joinToString(",")
    )

    override fun equals(other: Any?): Boolean = other is M2CVerifiedRunningSessionRecord && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

internal class M2CRunningWindowExposure(
    val bounds: M2CWindowBounds,
    val totalDistanceBelief: BeliefState<Meters>,
    val totalDurationBelief: BeliefState<Seconds>,
    val longestSessionDistanceBelief: BeliefState<Meters>,
    val longestSessionDurationBelief: BeliefState<Seconds>,
    val verifiedSessionCount: Int,
    val distinctActiveDayCount: Int,
    val ambiguousSessionCount: Int,
    val blockedCollisionCount: Int,
    val unknownDistanceSessionCount: Int,
    val hrQuarantinedButDistanceValidCount: Int,
    sessions: List<M2CVerifiedRunningSessionRecord>
) {
    val sessions: List<M2CVerifiedRunningSessionRecord>

    init {
        require(verifiedSessionCount >= 0 && distinctActiveDayCount >= 0 && ambiguousSessionCount >= 0)
        require(blockedCollisionCount >= 0 && unknownDistanceSessionCount >= 0 && hrQuarantinedButDistanceValidCount >= 0)
        require(sessions.all { bounds.contains(it.attributedLocalDate) && !it.sameDayAsEvaluation })
        this.sessions = Collections.unmodifiableList(ArrayList(sessions.sorted()))
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        bounds.canonicalRepresentation(), totalDistanceBelief.canonicalRepresentation(),
        totalDurationBelief.canonicalRepresentation(), longestSessionDistanceBelief.canonicalRepresentation(),
        longestSessionDurationBelief.canonicalRepresentation(), verifiedSessionCount, distinctActiveDayCount,
        ambiguousSessionCount, blockedCollisionCount, unknownDistanceSessionCount, hrQuarantinedButDistanceValidCount,
        sessions.joinToString("|") { it.canonicalRepresentation() }
    )

    override fun equals(other: Any?): Boolean = other is M2CRunningWindowExposure && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

// Reconciled evidence inventory entries (M2C-B02, M2C-B03, M2C-C05, M2C-C06)
internal data class M2CReconciledRunningEntry(
    val record: M2CVerifiedRunningSessionRecord,
    val canonicalActivity: CanonicalActivity?,
    val internalLoad: M2CSessionInternalLoadInput?,
    val internalLoadConflict: Boolean,
    val domainBreakdown: M2CWorkoutDomainBreakdownInput?,
    val domainBreakdownConflict: Boolean,
    val runningStream: M2BRunningStream?,
    val downhillStreamConflict: Boolean
) {
    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        record.canonicalRepresentation(),
        canonicalActivity?.contentHash,
        internalLoad?.canonicalRepresentation(), internalLoadConflict,
        domainBreakdown?.canonicalRepresentation(), domainBreakdownConflict,
        runningStream?.let { s ->
            Engine15M2CCanonical.frame(
                s.source.canonicalToken(), s.surface.name,
                s.recording?.let { m2bRecordingTraceKey(M2BRecordingTrace(s.activity.canonicalId, it)) },
                s.segments.joinToString(",") { "${it.start}|${it.end}|${it.horizontalDistance.canonicalToken()}|${it.riseMeters}" }
            )
        },
        downhillStreamConflict
    )
}

internal class M2CReconciledNonRunningEntry(
    val exposureId: String,
    val accountId: String,
    val discipline: AthleteDiscipline,
    val modality: M2CModalityKind,
    val startedAtUtc: Instant,
    val completedAtUtc: Instant,
    val attributedLocalDate: LocalDate,
    val distance: Meters?,
    val duration: Seconds?,
    val distanceQuality: TelemetryQualityStatus,
    val durationQuality: TelemetryQualityStatus,
    val workloadEligibility: WorkloadAggregationEligibility,
    val ambiguityGroupKey: String?,
    val canonicalActivity: CanonicalActivity?,
    val internalLoad: M2CSessionInternalLoadInput?,
    val internalLoadConflict: Boolean,
    val pinnedInObservedWorkload: Boolean,
    sourceAliases: Set<M2ASourceQualifiedAlias>,
    opaqueUpstreamRecordIds: Set<String>
) {
    val sourceAliases: Set<M2ASourceQualifiedAlias>
    val opaqueUpstreamRecordIds: Set<String>

    init {
        RecordPolicy.identifier(exposureId)
        RecordPolicy.identifier(accountId)
        require(discipline != AthleteDiscipline.RUN && !modality.producesRunningImpact)
        require(!completedAtUtc.isBefore(startedAtUtc))
        if (ambiguityGroupKey != null) RecordPolicy.identifier(ambiguityGroupKey)
        val cleanA = sourceAliases.map(Engine15M2CCanonical::normalizeAlias).distinct().sorted()
        val cleanO = opaqueUpstreamRecordIds.map { id -> RecordPolicy.identifier(id); id }.distinct().sorted()
        require(cleanA.isNotEmpty() || cleanO.isNotEmpty())
        this.sourceAliases = Collections.unmodifiableSet(LinkedHashSet(cleanA))
        this.opaqueUpstreamRecordIds = Collections.unmodifiableSet(LinkedHashSet(cleanO))
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        exposureId, accountId, discipline.code, modality.name, startedAtUtc, completedAtUtc, attributedLocalDate,
        distance?.canonicalToken(), duration?.canonicalToken(), distanceQuality.name, durationQuality.name,
        workloadEligibility.name, ambiguityGroupKey, canonicalActivity?.contentHash,
        internalLoad?.canonicalRepresentation(), internalLoadConflict, pinnedInObservedWorkload,
        sourceAliases.joinToString(",") { it.canonicalToken() }, opaqueUpstreamRecordIds.joinToString(",")
    )

    override fun equals(other: Any?): Boolean = other is M2CReconciledNonRunningEntry && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

internal class M2CReconciledEvidenceInventory private constructor(
    val context: M2BEvaluationContext,
    val observedWorkloadContentHash: String,
    pinned30dRunningEntries: List<M2CReconciledRunningEntry>,
    sameDayCompletedRunningEntries: List<M2CReconciledRunningEntry>,
    extendedHistorical31to90RunningEntries: List<M2CReconciledRunningEntry>,
    nonRunningEntries28d: List<M2CReconciledNonRunningEntry>,
    collidedCanonicalActivityIds: Set<String>,
    unpinnedExcludedCanonicalIds: Set<String>,
    diagnosticFlags: Set<String>
) {
    val pinned30dRunningEntries: List<M2CReconciledRunningEntry> =
        Collections.unmodifiableList(ArrayList(pinned30dRunningEntries.sortedBy { it.record }))
    val sameDayCompletedRunningEntries: List<M2CReconciledRunningEntry> =
        Collections.unmodifiableList(ArrayList(sameDayCompletedRunningEntries.sortedBy { it.record }))
    val extendedHistorical31to90RunningEntries: List<M2CReconciledRunningEntry> =
        Collections.unmodifiableList(ArrayList(extendedHistorical31to90RunningEntries.sortedBy { it.record }))
    val nonRunningEntries28d: List<M2CReconciledNonRunningEntry> =
        Collections.unmodifiableList(ArrayList(nonRunningEntries28d.sortedWith(
            compareBy<M2CReconciledNonRunningEntry> { it.attributedLocalDate }
                .thenBy { it.completedAtUtc }
                .thenBy { it.discipline.code }
                .thenBy { it.exposureId }
        )))
    val collidedCanonicalActivityIds: Set<String> = Collections.unmodifiableSet(LinkedHashSet(collidedCanonicalActivityIds.sorted()))
    val unpinnedExcludedCanonicalIds: Set<String> = Collections.unmodifiableSet(LinkedHashSet(unpinnedExcludedCanonicalIds.sorted()))
    val diagnosticFlags: Set<String> = Collections.unmodifiableSet(
        LinkedHashSet(diagnosticFlags.map { f -> RecordPolicy.identifier(f); f }.distinct().sorted())
    )
    val contentHash: String = Engine15M2CCanonical.sha256Hex(canonicalRepresentation())

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        "M2C-INV", Engine15M2CPolicyRegistry.POLICY_VERSION,
        context.accountId, context.discipline.code, context.physiologicalModality.name, context.asOf, context.zone.id,
        observedWorkloadContentHash,
        pinned30dRunningEntries.joinToString("|") { it.canonicalRepresentation() },
        sameDayCompletedRunningEntries.joinToString("|") { it.canonicalRepresentation() },
        extendedHistorical31to90RunningEntries.joinToString("|") { it.canonicalRepresentation() },
        nonRunningEntries28d.joinToString("|") { it.canonicalRepresentation() },
        collidedCanonicalActivityIds.joinToString(","),
        unpinnedExcludedCanonicalIds.joinToString(","),
        diagnosticFlags.joinToString(",")
    )

    internal companion object {
        internal fun create(
            context: M2BEvaluationContext,
            observedWorkloadContentHash: String,
            pinned30dRunningEntries: List<M2CReconciledRunningEntry>,
            sameDayCompletedRunningEntries: List<M2CReconciledRunningEntry>,
            extendedHistorical31to90RunningEntries: List<M2CReconciledRunningEntry>,
            nonRunningEntries28d: List<M2CReconciledNonRunningEntry>,
            collidedCanonicalActivityIds: Set<String>,
            unpinnedExcludedCanonicalIds: Set<String>,
            diagnosticFlags: Set<String>
        ): M2CReconciledEvidenceInventory = M2CReconciledEvidenceInventory(
            context, observedWorkloadContentHash, pinned30dRunningEntries, sameDayCompletedRunningEntries,
            extendedHistorical31to90RunningEntries, nonRunningEntries28d,
            collidedCanonicalActivityIds, unpinnedExcludedCanonicalIds, diagnosticFlags
        )
    }

    override fun equals(other: Any?): Boolean = other is M2CReconciledEvidenceInventory && contentHash == other.contentHash
    override fun hashCode(): Int = contentHash.hashCode()
}

internal data class M2CValidatedPinnedRunAlignment(
    val excludedPinnedRunExps30d: List<DatedObservedSessionExposure>,
    val excludedPinnedRunExps28d: List<DatedObservedSessionExposure>
)

/**
 * Enforces FINAL-02 & FINAL-04 inventory integrity: prevents independently forged or tampered
 * M2CReconciledEvidenceInventory instances from establishing unsupported verified distance authority,
 * and identifies pinned M1C exposures excluded due to future completion or implausible duration.
 */
internal object M2CInventoryValidator {

    internal fun incompleteAmbiguousDistanceGroups(
        records: List<M2CVerifiedRunningSessionRecord>,
        excluded: List<DatedObservedSessionExposure>
    ): Set<String> = buildSet {
        records.filter {
            it.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY &&
                (it.distance == null || it.distanceQuality != TelemetryQualityStatus.VALID_OBSERVED)
        }.forEach { add(it.ambiguityGroupKey ?: it.canonicalActivityId) }
        excluded.filter {
            it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_AMBIGUOUS_BOUNDED
        }.forEach { add(it.ambiguityGroupKey ?: it.exposureId) }
    }

    internal fun eligibilityFromProvenance(prov: ObservedExposureProvenance): WorkloadAggregationEligibility = when (prov) {
        ObservedExposureProvenance.CANONICAL_ACTIVITY_VERIFIED,
        ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_VERIFIED,
        ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE -> WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED
        ObservedExposureProvenance.CANONICAL_ACTIVITY_AMBIGUOUS_BOUNDED -> WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY
        ObservedExposureProvenance.CANONICAL_ACTIVITY_COLLISION_BLOCKED -> WorkloadAggregationEligibility.BLOCKED_COLLISION
    }

    internal fun completedInstantForPinnedExposure(exp: DatedObservedSessionExposure): Instant? {
        val dur = exp.duration
        return if (dur != null && dur.value > 0.0) {
            Engine15M2CCanonical.completedInstant(exp.startedAtUtc, dur.value)
        } else {
            exp.startedAtUtc
        }
    }

    internal fun canonicalMatchesPinnedExposure(
        act: CanonicalActivity,
        exp: DatedObservedSessionExposure,
        zoneId: ZoneId
    ): Boolean {
        val expectedElig = eligibilityFromProvenance(exp.provenance)
        val rawIdsMatch = act.rawComponents.map { it.recordId }.toSet() == exp.sourceRecordIds
        val distOk = if (exp.distance == null) {
            act.normalizedMetrics.distance == null || act.telemetryQuality.distanceQuality == TelemetryQualityStatus.QUARANTINED_UNRELIABLE
        } else {
            act.normalizedMetrics.distance != null &&
                abs(exp.distance.value - act.normalizedMetrics.distance.value) <= Engine15M2CPolicyRegistry.DISTANCE_CONSERVATION_TOLERANCE_METERS
        }
        val durOk = if (exp.duration == null) {
            act.normalizedMetrics.duration == null || act.telemetryQuality.durationQuality == TelemetryQualityStatus.QUARANTINED_UNRELIABLE
        } else {
            act.normalizedMetrics.duration != null &&
                abs(exp.duration.value - act.normalizedMetrics.duration.value) <= Engine15M2CPolicyRegistry.DURATION_CONSERVATION_TOLERANCE_SECONDS
        }
        return act.accountId == exp.accountId &&
            act.discipline == exp.discipline &&
            act.startedAtUtc == exp.startedAtUtc &&
            act.startedAtUtc.atZone(zoneId).toLocalDate() == exp.localDate &&
            act.workloadAggregationEligibility == expectedElig &&
            rawIdsMatch && distOk && durOk
    }

    fun validateAgainstObservedWorkload(
        context: M2BEvaluationContext,
        observedWorkload: ObservedWorkloadState,
        inventory: M2CReconciledEvidenceInventory
    ): M2CValidatedPinnedRunAlignment {
        require(observedWorkload.accountId == context.accountId) {
            "FINAL-02: observedWorkload accountId mismatch"
        }
        require(observedWorkload.asOfDate == context.localDate && observedWorkload.zoneId == context.zone) {
            "FINAL-02: observedWorkload (asOfDate, zoneId) must match evaluation context"
        }
        require(inventory.context == context && inventory.observedWorkloadContentHash == observedWorkload.contentHash) {
            "FINAL-02: reconciled inventory must match exact evaluation context and observedWorkload contentHash"
        }

        val bounds28 = M2CWindowBounds(
            M2CRollingWindowKind.TRAILING_28D, context.localDate.minusDays(28),
            context.localDate.minusDays(1), context.localDate, context.asOf, context.zone
        )
        val bounds30 = M2CWindowBounds(
            M2CRollingWindowKind.PRECEDING_30D, context.localDate.minusDays(30),
            context.localDate.minusDays(1), context.localDate, context.asOf, context.zone
        )
        val bounds90 = M2CWindowBounds(
            M2CRollingWindowKind.PRECEDING_90D, context.localDate.minusDays(90),
            context.localDate.minusDays(1), context.localDate, context.asOf, context.zone
        )

        val pinnedRunExps30 = observedWorkload.disciplines[AthleteDiscipline.RUN]?.exposures.orEmpty()
            .filter { bounds30.contains(it.localDate) }
        val eligiblePinnedById = linkedMapOf<String, DatedObservedSessionExposure>()
        val excluded30 = mutableListOf<DatedObservedSessionExposure>()
        val excluded28 = mutableListOf<DatedObservedSessionExposure>()

        for (exp in pinnedRunExps30) {
            val comp = completedInstantForPinnedExposure(exp)
            if (comp == null || comp.isAfter(context.asOf)) {
                excluded30 += exp
                if (bounds28.contains(exp.localDate)) excluded28 += exp
            } else {
                eligiblePinnedById[exp.exposureId] = exp
            }
        }

        val invPinnedIds = inventory.pinned30dRunningEntries.map { it.record.canonicalActivityId }
        require(invPinnedIds.size == invPinnedIds.distinct().size) {
            "FINAL-02: duplicate canonicalActivityId in pinned30dRunningEntries"
        }
        require(invPinnedIds.toSet() == eligiblePinnedById.keys) {
            "FINAL-02: forged or incomplete pinned30dRunningEntries relative to pinned ObservedWorkloadState"
        }

        for (entry in inventory.pinned30dRunningEntries) {
            val rec = entry.record
            val exp = eligiblePinnedById.getValue(rec.canonicalActivityId)
            val expectedComp = completedInstantForPinnedExposure(exp)!!
            require(rec.accountId == context.accountId) { "FINAL-02: pinned entry accountId mismatch" }
            require(rec.startedAtUtc == exp.startedAtUtc && rec.attributedLocalDate == exp.localDate) {
                "FINAL-02: pinned entry timestamp or localDate contradicts ObservedWorkloadState"
            }
            require(!rec.sameDayAsEvaluation && rec.completedAtUtc == expectedComp && !rec.completedAtUtc.isAfter(context.asOf)) {
                "FINAL-02: pinned entry completion chronology invalid"
            }
            require(rec.distance == exp.distance && rec.duration == exp.duration) {
                "FINAL-02: pinned entry distance or duration contradicts ObservedWorkloadState"
            }
            require(rec.ambiguityGroupKey == exp.ambiguityGroupKey) {
                "FINAL-02: pinned entry ambiguityGroupKey contradicts ObservedWorkloadState"
            }
            require(rec.workloadEligibility == eligibilityFromProvenance(exp.provenance)) {
                "FINAL-02: pinned entry workloadEligibility contradicts ObservedWorkloadState provenance"
            }
            val act = entry.canonicalActivity
            val expectedDistanceQuality = act?.telemetryQuality?.distanceQuality
                ?: if (exp.distance == null) TelemetryQualityStatus.MISSING_TELEMETRY else TelemetryQualityStatus.VALID_OBSERVED
            val expectedDurationQuality = act?.telemetryQuality?.durationQuality
                ?: if (exp.duration == null) TelemetryQualityStatus.MISSING_TELEMETRY else TelemetryQualityStatus.VALID_OBSERVED
            require(rec.distanceQuality == expectedDistanceQuality && rec.durationQuality == expectedDurationQuality) {
                "Pinned record measurement quality contradicts the reconciled evidence"
            }
            if (act != null) {
                require(act.canonicalId == exp.exposureId && canonicalMatchesPinnedExposure(act, exp, context.zone)) {
                    "FINAL-02: pinned entry canonicalActivity contradicts pinned ObservedWorkloadState exposure"
                }
                val expectedAliases = Engine15M2CCanonical.aliasesFromCanonicalActivity(act)
                require(rec.sourceAliases == expectedAliases) {
                    "FINAL-02: pinned entry sourceAliases do not match reconciled CanonicalActivity"
                }
                if (expectedAliases.isNotEmpty()) {
                    require(rec.opaqueUpstreamRecordIds.isEmpty()) {
                        "FINAL-02: reconciled aliases should not duplicate opaqueUpstreamRecordIds"
                    }
                }
            } else {
                require(rec.sourceAliases.isEmpty() && rec.opaqueUpstreamRecordIds == exp.sourceRecordIds) {
                    "FINAL-02: unenriched pinned entry cannot carry forged sourceAliases or altered opaqueUpstreamRecordIds"
                }
            }
        }

        val seenExtIds = linkedSetOf<String>()
        val occupiedSources = mutableSetOf<String>()
        val otherActivities = (inventory.pinned30dRunningEntries + inventory.sameDayCompletedRunningEntries)
            .mapNotNull { it.canonicalActivity } + inventory.nonRunningEntries28d.mapNotNull { it.canonicalActivity }
        otherActivities.forEach { occupiedSources += Engine15M2CCanonical.sourceIdentityTokens(it) }
        val pinnedRecordIds = observedWorkload.disciplines.values.flatMap { it.exposures }
            .flatMap { it.sourceRecordIds }.toSet()
        for (entry in inventory.extendedHistorical31to90RunningEntries) {
            val rec = entry.record
            val act = entry.canonicalActivity
            require(act != null) {
                "FINAL-02: extended 31-90d running entry must be backed by a CanonicalActivity"
            }
            require(seenExtIds.add(rec.canonicalActivityId) && rec.canonicalActivityId !in eligiblePinnedById) {
                "FINAL-02: duplicate or overlapping extended 31-90d canonicalActivityId"
            }
            require(act.accountId == context.accountId && rec.accountId == context.accountId && act.discipline == AthleteDiscipline.RUN) {
                "FINAL-02: extended 31-90d entry account or discipline mismatch"
            }
            require(act.canonicalId == rec.canonicalActivityId && act.startedAtUtc == rec.startedAtUtc) {
                "FINAL-02: extended 31-90d record identity contradicts CanonicalActivity"
            }
            val tokens = Engine15M2CCanonical.sourceIdentityTokens(act)
            require(act.canonicalId !in inventory.collidedCanonicalActivityIds &&
                tokens.none { it in occupiedSources } && act.rawComponents.none { it.recordId in pinnedRecordIds }
            ) { "FINAL-02: extended source collides with another reconciled activity" }
            occupiedSources += tokens
            require((rec.ambiguityGroupKey != null) ==
                (act.workloadAggregationEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY)
            ) { "FINAL-02: extended ambiguity contradicts CanonicalActivity" }
            val expectedLocalDate = act.startedAtUtc.atZone(context.zone).toLocalDate()
            require(rec.attributedLocalDate == expectedLocalDate && bounds90.contains(expectedLocalDate) && !bounds30.contains(expectedLocalDate)) {
                "FINAL-02: extended 31-90d entry date out of [d-90, d-31] bounds"
            }
            val dur = act.normalizedMetrics.duration
            val expectedComp = if (dur != null && dur.value > 0.0) {
                Engine15M2CCanonical.completedInstant(act.startedAtUtc, dur.value)
                    ?: throw IllegalArgumentException("FINAL-02: extended 31-90d entry has implausible duration")
            } else act.startedAtUtc
            require(!rec.sameDayAsEvaluation && rec.completedAtUtc == expectedComp && !rec.completedAtUtc.isAfter(context.asOf)) {
                "FINAL-02: extended 31-90d entry completion chronology invalid"
            }
            val expectedDist = if (act.telemetryQuality.distanceQuality != TelemetryQualityStatus.QUARANTINED_UNRELIABLE) act.normalizedMetrics.distance else null
            val expectedDur = if (act.telemetryQuality.durationQuality != TelemetryQualityStatus.QUARANTINED_UNRELIABLE) dur else null
            require(rec.distance == expectedDist && rec.duration == expectedDur) {
                "FINAL-02: extended 31-90d entry metrics contradict backing CanonicalActivity"
            }
            require(rec.distanceQuality == act.telemetryQuality.distanceQuality &&
                rec.durationQuality == act.telemetryQuality.durationQuality &&
                rec.workloadEligibility == act.workloadAggregationEligibility
            ) {
                "FINAL-02: extended 31-90d entry quality or eligibility contradicts backing CanonicalActivity"
            }
            require(rec.sourceAliases == Engine15M2CCanonical.aliasesFromCanonicalActivity(act)) {
                "FINAL-02: extended 31-90d entry sourceAliases contradict backing CanonicalActivity"
            }
        }

        return M2CValidatedPinnedRunAlignment(
            excludedPinnedRunExps30d = Collections.unmodifiableList(excluded30),
            excludedPinnedRunExps28d = Collections.unmodifiableList(excluded28)
        )
    }
}

/** Explicitly distinguishes verified maximum among available recordings from fully observed historical maximum (FINAL-02). */
internal enum class M2CAnchorCompletenessKind {
    NO_VERIFIED_RECORDED_ANCHOR,
    CONFIRMED_ZERO_PROVISIONAL,
    PROVISIONAL_SELF_REPORT_NON_AUTHORITATIVE,
    PARTIAL_OR_AMBIGUOUS_LOWER_BOUND_ONLY,
    VERIFIED_MAX_AMONG_AVAILABLE_RECORDINGS,
    FULLY_OBSERVED_CONTINUOUS_WINDOW_MAXIMUM
}

/**
 * Controlled single-session anchor derived STRICTLY from validated reconciled inventory + pinned
 * ObservedWorkloadState (M2C-B01, M2C-C01, D1-02, D3-03, FINAL-02).
 * Never claims authenticated production custody pre-M9.
 */
internal class M2CVerifiedSingleSessionAnchor private constructor(
    val context: M2BEvaluationContext,
    val observedWorkloadContentHash: String,
    val reconciledInventoryContentHash: String,
    val bounds: M2CWindowBounds,
    val distanceBelief: BeliefState<Meters>,
    val isObservedAuthoritativeExact: Boolean,
    val completenessKind: M2CAnchorCompletenessKind,
    sourceAliases: Set<M2ASourceQualifiedAlias>,
    opaqueUpstreamRecordIds: Set<String>,
    canonicalActivityIds: Set<String>,
    val policyVersion: String,
    val authenticatedProductionCustody: Boolean = false
) {
    val sourceAliases: Set<M2ASourceQualifiedAlias>
    val opaqueUpstreamRecordIds: Set<String>
    val canonicalActivityIds: Set<String>

    init {
        RecordPolicy.identifier(policyVersion)
        require(!authenticatedProductionCustody) {
            "FINAL-02: no authenticated production custody before M9"
        }
        require(observedWorkloadContentHash.isNotBlank() && reconciledInventoryContentHash.isNotBlank())
        val exactObserved = distanceBelief is BeliefState.Estimated && distanceBelief.authority.isObservedAuthoritative
        require(isObservedAuthoritativeExact == exactObserved)
        if (completenessKind == M2CAnchorCompletenessKind.FULLY_OBSERVED_CONTINUOUS_WINDOW_MAXIMUM) {
            require(bounds.kind == M2CRollingWindowKind.PRECEDING_30D && isObservedAuthoritativeExact) {
                "FINAL-02: fully observed historical maximum requires clean continuous 30d window"
            }
        }
        this.sourceAliases = Collections.unmodifiableSet(
            LinkedHashSet(sourceAliases.map(Engine15M2CCanonical::normalizeAlias).distinct().sorted())
        )
        this.opaqueUpstreamRecordIds = Collections.unmodifiableSet(
            LinkedHashSet(opaqueUpstreamRecordIds.map { id -> RecordPolicy.identifier(id); id }.distinct().sorted())
        )
        this.canonicalActivityIds = Collections.unmodifiableSet(
            LinkedHashSet(canonicalActivityIds.map { id -> RecordPolicy.identifier(id); id }.distinct().sorted())
        )
    }

    val exactObservedMeters: Double?
        get() = if (isObservedAuthoritativeExact) (distanceBelief as BeliefState.Estimated).estimate.value else null

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        context.accountId, context.discipline.code, context.physiologicalModality.name, context.asOf, context.zone.id,
        observedWorkloadContentHash, reconciledInventoryContentHash, bounds.canonicalRepresentation(),
        distanceBelief.canonicalRepresentation(), isObservedAuthoritativeExact, completenessKind.name,
        sourceAliases.joinToString(",") { it.canonicalToken() },
        opaqueUpstreamRecordIds.joinToString(","), canonicalActivityIds.joinToString(","),
        policyVersion, authenticatedProductionCustody
    )

    override fun equals(other: Any?): Boolean = other is M2CVerifiedSingleSessionAnchor && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()

    internal companion object {
        /** FINAL-02: Derives 30-day anchor strictly from validated reconciled inventory + pinned ObservedWorkloadState. */
        internal fun fromReconciled30dInventory(
            context: M2BEvaluationContext,
            observedWorkload: ObservedWorkloadState,
            inventory: M2CReconciledEvidenceInventory
        ): M2CVerifiedSingleSessionAnchor {
            val alignment = M2CInventoryValidator.validateAgainstObservedWorkload(context, observedWorkload, inventory)
            val bounds30 = Engine15M2CRollingExposureEngine.boundsFor(context, M2CRollingWindowKind.PRECEDING_30D)
            val records30 = inventory.pinned30dRunningEntries.map { it.record }.filter { bounds30.contains(it.attributedLocalDate) }
            val runWl = observedWorkload.disciplines[AthleteDiscipline.RUN]
            val srRun = observedWorkload.provisionalSelfReport[AthleteDiscipline.RUN]
            val rawCoverage = runWl?.coverage ?: inferEmptyCoverage(srRun)
            val effectiveCoverage = if (alignment.excludedPinnedRunExps30d.isNotEmpty() && rawCoverage == HistoryCoverageStatus.CONTINUOUS_COVERAGE) {
                HistoryCoverageStatus.PARTIAL_COVERAGE
            } else rawCoverage

            val (belief, completeness) = deriveLongestDistanceBelief(
                records = records30,
                incompleteDistanceGroups = M2CInventoryValidator.incompleteAmbiguousDistanceGroups(
                    records30, alignment.excludedPinnedRunExps30d
                ),
                coverage = effectiveCoverage,
                provisionalLongest = srRun?.longestSession?.takeIf { it.value > 0.0 },
                allowFullyObservedClassification = false
            )
            return M2CVerifiedSingleSessionAnchor(
                context = context,
                observedWorkloadContentHash = observedWorkload.contentHash,
                reconciledInventoryContentHash = inventory.contentHash,
                bounds = bounds30,
                distanceBelief = belief,
                isObservedAuthoritativeExact = belief is BeliefState.Estimated && belief.authority.isObservedAuthoritative,
                completenessKind = completeness,
                sourceAliases = records30.flatMap { it.sourceAliases }.toSet(),
                opaqueUpstreamRecordIds = records30.flatMap { it.opaqueUpstreamRecordIds }.toSet(),
                canonicalActivityIds = records30.map { it.canonicalActivityId }.toSet(),
                policyVersion = Engine15M2CPolicyRegistry.POLICY_VERSION,
                authenticatedProductionCustody = false
            )
        }

        /** FINAL-02: Derives 90-day anchor strictly from validated reconciled 30d + extended 31-90d inventory. */
        internal fun fromReconciled90dInventory(
            context: M2BEvaluationContext,
            observedWorkload: ObservedWorkloadState,
            inventory: M2CReconciledEvidenceInventory
        ): M2CVerifiedSingleSessionAnchor {
            val alignment = M2CInventoryValidator.validateAgainstObservedWorkload(context, observedWorkload, inventory)
            val bounds90 = Engine15M2CRollingExposureEngine.boundsFor(context, M2CRollingWindowKind.PRECEDING_90D)
            val records90 = (inventory.pinned30dRunningEntries + inventory.extendedHistorical31to90RunningEntries)
                .map { it.record }
                .filter { bounds90.contains(it.attributedLocalDate) }
            val runWl = observedWorkload.disciplines[AthleteDiscipline.RUN]
            val srRun = observedWorkload.provisionalSelfReport[AthleteDiscipline.RUN]
            val coverage = runWl?.coverage ?: inferEmptyCoverage(srRun)

            // 90-day window NEVER claims fully observed historical maximum pre-M9 (M1C only checks 28d coverage).
            val (belief, completeness) = deriveLongestDistanceBelief(
                records = records90,
                incompleteDistanceGroups = M2CInventoryValidator.incompleteAmbiguousDistanceGroups(
                    records90, alignment.excludedPinnedRunExps30d
                ),
                coverage = coverage,
                provisionalLongest = srRun?.longestSession?.takeIf { it.value > 0.0 },
                allowFullyObservedClassification = false
            )
            return M2CVerifiedSingleSessionAnchor(
                context = context,
                observedWorkloadContentHash = observedWorkload.contentHash,
                reconciledInventoryContentHash = inventory.contentHash,
                bounds = bounds90,
                distanceBelief = belief,
                isObservedAuthoritativeExact = belief is BeliefState.Estimated && belief.authority.isObservedAuthoritative,
                completenessKind = completeness,
                sourceAliases = records90.flatMap { it.sourceAliases }.toSet(),
                opaqueUpstreamRecordIds = records90.flatMap { it.opaqueUpstreamRecordIds }.toSet(),
                canonicalActivityIds = records90.map { it.canonicalActivityId }.toSet(),
                policyVersion = Engine15M2CPolicyRegistry.POLICY_VERSION,
                authenticatedProductionCustody = false
            )
        }

        private fun inferEmptyCoverage(sr: ProvisionalSelfReportWorkload?): HistoryCoverageStatus = when {
            sr?.confirmedZero == true -> HistoryCoverageStatus.CONFIRMED_ZERO
            sr?.weeklyVolume != null && sr.basis == RecentMileageBasis.SELF_REPORTED_AVERAGE ->
                HistoryCoverageStatus.PROVISIONAL_SELF_REPORT
            sr?.longestSession != null && sr.longestSession.value > 0.0 && sr.basis == RecentMileageBasis.SELF_REPORTED_AVERAGE ->
                HistoryCoverageStatus.PROVISIONAL_SELF_REPORT
            else -> HistoryCoverageStatus.MISSING_UNOBSERVED
        }

        private fun deriveLongestDistanceBelief(
            records: List<M2CVerifiedRunningSessionRecord>,
            incompleteDistanceGroups: Set<String>,
            coverage: HistoryCoverageStatus,
            provisionalLongest: Meters?,
            allowFullyObservedClassification: Boolean
        ): Pair<BeliefState<Meters>, M2CAnchorCompletenessKind> {
            if (records.isEmpty()) {
                return when {
                    coverage == HistoryCoverageStatus.CONFIRMED_ZERO ->
                        BeliefState.Estimated(Meters.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE) to
                            M2CAnchorCompletenessKind.CONFIRMED_ZERO_PROVISIONAL
                    coverage == HistoryCoverageStatus.PROVISIONAL_SELF_REPORT && provisionalLongest != null ->
                        BeliefState.Estimated(provisionalLongest, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND) to
                            M2CAnchorCompletenessKind.PROVISIONAL_SELF_REPORT_NON_AUTHORITATIVE
                    else ->
                        BeliefState.Unknown("NO_OBSERVED_RUNNING_SESSIONS_IN_WINDOW") to
                            M2CAnchorCompletenessKind.NO_VERIFIED_RECORDED_ANCHOR
                }
            }

            val verifiedRecords = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED }
            val ambigRecords = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY }
            val hasBlocked = records.any { it.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION }
            val hasMissingOrDegraded = records.any {
                it.workloadEligibility != WorkloadAggregationEligibility.BLOCKED_COLLISION &&
                    (it.distance == null || it.distanceQuality != TelemetryQualityStatus.VALID_OBSERVED)
            }

            val verifiedMax = verifiedRecords
                .filter { it.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED }
                .mapNotNull { it.distance?.value?.takeIf { d -> d > 0.0 } }
                .maxOrNull()

            val ambigGroups = ambigRecords.groupBy { it.ambiguityGroupKey ?: it.canonicalActivityId }.values
            val groupMins = mutableListOf<Double>()
            val groupMaxs = mutableListOf<Double>()
            for (grp in ambigGroups) {
                val key = grp.first().let { it.ambiguityGroupKey ?: it.canonicalActivityId }
                val validDists = grp.filter { it.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED }
                    .mapNotNull { it.distance?.value?.takeIf { d -> d > 0.0 } }
                if (key !in incompleteDistanceGroups && validDists.size == grp.size && validDists.isNotEmpty()) {
                    groupMins += validDists.min()
                    groupMaxs += validDists.max()
                } else if (validDists.isNotEmpty()) {
                    groupMaxs += validDists.max()
                }
            }

            val hasUnresolvedBlockOrMissing = hasBlocked || hasMissingOrDegraded || ambigRecords.any {
                (it.ambiguityGroupKey ?: it.canonicalActivityId) in incompleteDistanceGroups
            }
            if (ambigRecords.isEmpty() && !hasUnresolvedBlockOrMissing) {
                return if (verifiedMax != null) {
                    val kind = if (allowFullyObservedClassification) {
                        M2CAnchorCompletenessKind.FULLY_OBSERVED_CONTINUOUS_WINDOW_MAXIMUM
                    } else {
                        M2CAnchorCompletenessKind.VERIFIED_MAX_AMONG_AVAILABLE_RECORDINGS
                    }
                    BeliefState.Estimated(Meters.of(verifiedMax), EvidenceAuthorityClass.SENSOR_SUPPORTED, UncertaintyKind.NONE) to kind
                } else {
                    BeliefState.Unknown("NO_VERIFIED_LONGEST_SESSION_DISTANCE") to
                        M2CAnchorCompletenessKind.NO_VERIFIED_RECORDED_ANCHOR
                }
            }

            val lo = listOfNotNull(verifiedMax, groupMins.maxOrNull()).maxOrNull()
            val hi = if (hasUnresolvedBlockOrMissing) null else listOfNotNull(verifiedMax, groupMaxs.maxOrNull()).maxOrNull()
            return if (lo != null) {
                BeliefState.Bounded(
                    lower = Meters.of(lo),
                    upper = hi?.let { Meters.of(max(lo, it)) },
                    authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
                ) to M2CAnchorCompletenessKind.PARTIAL_OR_AMBIGUOUS_LOWER_BOUND_ONLY
            } else {
                BeliefState.Unknown("UNKNOWN_LONGEST_SESSION_DISTANCE") to
                    M2CAnchorCompletenessKind.NO_VERIFIED_RECORDED_ANCHOR
            }
        }
    }
}

internal data class M2CSingleSessionOverrideAcknowledgment(
    val accountId: String,
    val evaluatedFor: M2BEvaluationContext,
    val proposedDistance: Meters,
    val policyVersion: String,
    val acknowledgedRestrictionReasons: Set<M2BRestrictionReason>,
    val acknowledgedAtUtc: Instant
) {
    init {
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(policyVersion)
        require(accountId == evaluatedFor.accountId)
        require(!acknowledgedAtUtc.isAfter(evaluatedFor.asOf))
    }
}

internal enum class M2CSingleSessionSpikeBand {
    NOT_APPLICABLE_MISSING_BASELINE,
    NOT_APPLICABLE_AMBIGUOUS_BOUNDED_BASELINE,
    NOT_APPLICABLE_UNVERIFIED_AUTHORITY,
    NOT_APPLICABLE_BELOW_4000M_FLOOR,
    WITHIN_DEFAULT_GUARDRAIL_LE_1_10,
    SMALL_SPIKE_GT_1_10_TO_1_30,
    MODERATE_SPIKE_GT_1_30_TO_2_00,
    LARGE_SPIKE_GT_2_00
}

internal enum class M2CSingleSessionOverrideEligibility {
    NOT_NEEDED,
    INELIGIBLE_ACTIVE_HEALTH_RESTRICTION,
    INELIGIBLE_NO_VERIFIED_30D_ANCHOR,
    INELIGIBLE_BELOW_BASELINE_FLOOR,
    INELIGIBLE_NO_VERIFIED_90D_ANCHOR,
    INELIGIBLE_EXCEEDS_90D_VERIFIED_MAX,
    ELIGIBLE_WITHIN_90D_VERIFIED_MAX_EXPLICIT_ACK_REQUIRED
}

/** MOD-10 single-session r_30 assessment bound to reconciled inventory identity (M2C-C01, FINAL-02). */
internal class M2CSingleSessionExposureAssessment(
    val evaluatedFor: M2BEvaluationContext,
    val observedWorkloadContentHash: String,
    val reconciledInventoryContentHash: String,
    val evaluatedDistance: Meters,
    val anchor30d: M2CVerifiedSingleSessionAnchor,
    val anchor90d: M2CVerifiedSingleSessionAnchor,
    val r30Ratio: DimensionlessRatio?,
    val defaultGuardrailCapMeters: Meters?,
    val spikeBand: M2CSingleSessionSpikeBand,
    val policyGuardrailExceeded: Boolean,
    val observationalRiskSignalActive: Boolean,
    val overrideEligibility: M2CSingleSessionOverrideEligibility,
    val overrideAcknowledgmentMatched: Boolean,
    val overrideEligibleAndAcknowledgedPreM9: Boolean,
    val healthRestrictionBlocksOverride: Boolean,
    val guaranteesInjurySafety: Boolean = false,
    val isIndividualInjuryProbability: Boolean = false,
    val overrideErasesRiskSignal: Boolean = false,
    val overrideBypassesHealthRestrictions: Boolean = false,
    val overrideGrantsProductionAuthority: Boolean = false,
    val pol023SpecDiscrepancyNoted: Boolean = true,
    notes: List<String> = emptyList()
) {
    val notes: List<String>
    val anchor30dCompletenessKind: M2CAnchorCompletenessKind get() = anchor30d.completenessKind
    val anchor90dCompletenessKind: M2CAnchorCompletenessKind get() = anchor90d.completenessKind

    init {
        require(!guaranteesInjurySafety && !isIndividualInjuryProbability)
        require(!overrideErasesRiskSignal && !overrideBypassesHealthRestrictions && !overrideGrantsProductionAuthority)
        require(observedWorkloadContentHash.isNotBlank() && reconciledInventoryContentHash.isNotBlank())
        require(anchor30d.context == evaluatedFor &&
            anchor30d.observedWorkloadContentHash == observedWorkloadContentHash &&
            anchor30d.reconciledInventoryContentHash == reconciledInventoryContentHash) {
            "FINAL-02: anchor30d must match evaluation context, observedWorkloadContentHash, and reconciledInventoryContentHash"
        }
        require(anchor90d.context == evaluatedFor &&
            anchor90d.observedWorkloadContentHash == observedWorkloadContentHash &&
            anchor90d.reconciledInventoryContentHash == reconciledInventoryContentHash) {
            "FINAL-02: anchor90d must match evaluation context, observedWorkloadContentHash, and reconciledInventoryContentHash"
        }
        val auth30M = anchor30d.exactObservedMeters
        if (auth30M == null || auth30M < Engine15M2CPolicyRegistry.POL_023_SINGLE_SESSION_BASELINE_FLOOR_METERS) {
            require(r30Ratio == null && defaultGuardrailCapMeters == null && !policyGuardrailExceeded && !observationalRiskSignalActive)
        } else {
            val cap = auth30M * Engine15M2CPolicyRegistry.POL_022_SINGLE_SESSION_GUARDRAIL_RATIO
            require(r30Ratio != null && defaultGuardrailCapMeters?.value == cap.takeIf { it.isFinite() })
            require(abs(r30Ratio.value - (evaluatedDistance.value / auth30M)) <= 1e-9)
            if (r30Ratio.value > Engine15M2CPolicyRegistry.POL_022_SINGLE_SESSION_GUARDRAIL_RATIO) {
                require(observationalRiskSignalActive)
            }
        }
        if (overrideEligibleAndAcknowledgedPreM9) {
            require(!healthRestrictionBlocksOverride && overrideAcknowledgmentMatched &&
                overrideEligibility == M2CSingleSessionOverrideEligibility.ELIGIBLE_WITHIN_90D_VERIFIED_MAX_EXPLICIT_ACK_REQUIRED)
        }
        this.notes = Collections.unmodifiableList(ArrayList(notes.map { n -> RecordPolicy.identifier(n); n }.distinct().sorted()))
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        evaluatedFor.accountId, evaluatedFor.asOf, evaluatedFor.zone.id,
        observedWorkloadContentHash, reconciledInventoryContentHash,
        evaluatedDistance.canonicalToken(), anchor30d.canonicalRepresentation(), anchor90d.canonicalRepresentation(),
        r30Ratio?.canonicalToken(), defaultGuardrailCapMeters?.canonicalToken(), spikeBand.name,
        policyGuardrailExceeded, observationalRiskSignalActive, overrideEligibility.name,
        overrideAcknowledgmentMatched, overrideEligibleAndAcknowledgedPreM9, healthRestrictionBlocksOverride,
        notes.joinToString(",")
    )

    override fun equals(other: Any?): Boolean = other is M2CSingleSessionExposureAssessment && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

// MOD-11 Stopping Condition Contract (M2C-A03, M2C-B04)
internal enum class M2CStoppingEnforcementMode {
    PRESCRIBED_INSTRUCTION,
    ATHLETE_MONITORED,
    CLAIMED_OR_CONFIGURED_APP_ENFORCEMENT
}

internal enum class M2CStoppingEvaluationOutcome {
    CONTINUE_WITHIN_CAPS,
    STOP_DURATION_CAP_REACHED,
    STOP_DISTANCE_CAP_REACHED,
    STOP_BOTH_CAPS_REACHED,
    UNVERIFIABLE_DISTANCE_CAP_WITHOUT_LIVE_DISTANCE
}

internal sealed interface M2CStoppingVerificationEvidence {
    fun canonicalRepresentation(): String

    data object NonePendingExecution : M2CStoppingVerificationEvidence {
        override fun canonicalRepresentation(): String = "NONE_PENDING_EXECUTION"
    }

    data class ClaimedAppEnforcementUnauthenticatedPreM9(val deviceSessionId: String) : M2CStoppingVerificationEvidence {
        init { RecordPolicy.identifier(deviceSessionId) }
        override fun canonicalRepresentation(): String = Engine15M2CCanonical.frame("CLAIMED_APP_PRE_M9", deviceSessionId)
    }

    data class RetrospectiveCompletedObservation(
        val accountId: String,
        val canonicalActivityId: String,
        val sourceAlias: M2ASourceQualifiedAlias,
        val completedAtUtc: Instant,
        val observedDistance: Meters?,
        val observedDuration: Seconds?,
        val distanceQuality: TelemetryQualityStatus,
        val durationQuality: TelemetryQualityStatus
    ) : M2CStoppingVerificationEvidence {
        init {
            RecordPolicy.identifier(accountId)
            RecordPolicy.identifier(canonicalActivityId)
        }
        override fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
            "RETRO_OBS", accountId, canonicalActivityId, Engine15M2CCanonical.normalizeAlias(sourceAlias).canonicalToken(),
            completedAtUtc, observedDistance?.canonicalToken(), observedDuration?.canonicalToken(),
            distanceQuality.name, durationQuality.name
        )
    }
}

internal enum class M2CStoppingComplianceStatus {
    UNENFORCED_INSTRUCTION_NO_COMPLIANCE_CERTIFICATE,
    CLAIMED_APP_ENFORCEMENT_UNAUTHENTICATED_PRE_M9,
    RETROSPECTIVELY_VERIFIED_COMPLIANT,
    RETROSPECTIVELY_VERIFIED_CAP_EXCEEDED,
    RETROSPECTIVE_EVIDENCE_INCOMPLETE_OR_DEGRADED
}

internal class M2CStoppingConditionContract private constructor(
    val evaluatedFor: M2BEvaluationContext,
    val capIdentity: String,
    val durationCap: Seconds?,
    val distanceCap: Meters?,
    val enforcementMode: M2CStoppingEnforcementMode,
    val verificationEvidence: M2CStoppingVerificationEvidence,
    val complianceStatus: M2CStoppingComplianceStatus,
    val structurallyCompliantWithDistanceCap: Boolean,
    val structurallyCompliantWithDurationCap: Boolean,
    val authenticatedDistanceComplianceCertificate: Boolean
) {
    init {
        RecordPolicy.identifier(capIdentity)
        require((durationCap != null && durationCap.value > 0.0) || (distanceCap != null && distanceCap.value > 0.0))
        require(!authenticatedDistanceComplianceCertificate)
        if (distanceCap == null) require(!structurallyCompliantWithDistanceCap)
        if (verificationEvidence !is M2CStoppingVerificationEvidence.RetrospectiveCompletedObservation) {
            require(!structurallyCompliantWithDistanceCap && !structurallyCompliantWithDurationCap)
        }
    }

    fun evaluatePredicate(elapsedDuration: Seconds, coveredDistance: Meters?): M2CStoppingEvaluationOutcome {
        val hitTime = durationCap != null && elapsedDuration.value >= durationCap.value
        val hitDist = distanceCap != null && coveredDistance != null && coveredDistance.value >= distanceCap.value
        return when {
            hitTime && hitDist -> M2CStoppingEvaluationOutcome.STOP_BOTH_CAPS_REACHED
            hitTime -> M2CStoppingEvaluationOutcome.STOP_DURATION_CAP_REACHED
            hitDist -> M2CStoppingEvaluationOutcome.STOP_DISTANCE_CAP_REACHED
            distanceCap != null && coveredDistance == null -> M2CStoppingEvaluationOutcome.UNVERIFIABLE_DISTANCE_CAP_WITHOUT_LIVE_DISTANCE
            else -> M2CStoppingEvaluationOutcome.CONTINUE_WITHIN_CAPS
        }
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        evaluatedFor.accountId, evaluatedFor.asOf, evaluatedFor.zone.id, capIdentity,
        durationCap?.canonicalToken(), distanceCap?.canonicalToken(), enforcementMode.name,
        verificationEvidence.canonicalRepresentation(), complianceStatus.name,
        structurallyCompliantWithDistanceCap, structurallyCompliantWithDurationCap,
        authenticatedDistanceComplianceCertificate
    )

    override fun equals(other: Any?): Boolean = other is M2CStoppingConditionContract && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()

    internal companion object {
        internal fun createValidated(
            context: M2BEvaluationContext,
            capIdentity: String,
            durationCap: Seconds?,
            distanceCap: Meters?,
            enforcementMode: M2CStoppingEnforcementMode,
            verificationEvidence: M2CStoppingVerificationEvidence,
            reconciledSession: M2CVerifiedRunningSessionRecord?
        ): M2CStoppingConditionContract {
            var distStructCompliant = false
            var durStructCompliant = false
            val compliance = when (verificationEvidence) {
                M2CStoppingVerificationEvidence.NonePendingExecution -> when (enforcementMode) {
                    M2CStoppingEnforcementMode.CLAIMED_OR_CONFIGURED_APP_ENFORCEMENT ->
                        M2CStoppingComplianceStatus.CLAIMED_APP_ENFORCEMENT_UNAUTHENTICATED_PRE_M9
                    M2CStoppingEnforcementMode.PRESCRIBED_INSTRUCTION,
                    M2CStoppingEnforcementMode.ATHLETE_MONITORED ->
                        M2CStoppingComplianceStatus.UNENFORCED_INSTRUCTION_NO_COMPLIANCE_CERTIFICATE
                }
                is M2CStoppingVerificationEvidence.ClaimedAppEnforcementUnauthenticatedPreM9 ->
                    M2CStoppingComplianceStatus.CLAIMED_APP_ENFORCEMENT_UNAUTHENTICATED_PRE_M9
                is M2CStoppingVerificationEvidence.RetrospectiveCompletedObservation -> {
                    val normObsAlias = Engine15M2CCanonical.normalizeAlias(verificationEvidence.sourceAlias)
                    val boundOk = reconciledSession != null &&
                        verificationEvidence.accountId == context.accountId &&
                        reconciledSession.accountId == context.accountId &&
                        reconciledSession.canonicalActivityId == verificationEvidence.canonicalActivityId &&
                        !verificationEvidence.completedAtUtc.isAfter(context.asOf) &&
                        reconciledSession.completedAtUtc == verificationEvidence.completedAtUtc &&
                        (reconciledSession.sourceAliases.isNotEmpty() && normObsAlias in reconciledSession.sourceAliases) &&
                        reconciledSession.distance == verificationEvidence.observedDistance &&
                        reconciledSession.duration == verificationEvidence.observedDuration
                    if (!boundOk) {
                        M2CStoppingComplianceStatus.RETROSPECTIVE_EVIDENCE_INCOMPLETE_OR_DEGRADED
                    } else {
                        val distValid = distanceCap == null || (reconciledSession!!.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED && reconciledSession.distance != null)
                        val durValid = durationCap == null || (reconciledSession!!.durationQuality == TelemetryQualityStatus.VALID_OBSERVED && reconciledSession.duration != null)
                        if (!distValid || !durValid) {
                            M2CStoppingComplianceStatus.RETROSPECTIVE_EVIDENCE_INCOMPLETE_OR_DEGRADED
                        } else {
                            val distOk = distanceCap != null && reconciledSession!!.distance!!.value <= distanceCap.value
                            val durOk = durationCap != null && reconciledSession!!.duration!!.value <= durationCap.value
                            distStructCompliant = distOk
                            durStructCompliant = durOk
                            val allCapsOk = (distanceCap == null || distOk) && (durationCap == null || durOk)
                            if (allCapsOk) M2CStoppingComplianceStatus.RETROSPECTIVELY_VERIFIED_COMPLIANT
                            else M2CStoppingComplianceStatus.RETROSPECTIVELY_VERIFIED_CAP_EXCEEDED
                        }
                    }
                }
            }
            return M2CStoppingConditionContract(
                context, capIdentity, durationCap, distanceCap, enforcementMode, verificationEvidence,
                compliance, distStructCompliant, durStructCompliant, authenticatedDistanceComplianceCertificate = false
            )
        }
    }
}

// =============================================================================
// SECTION 5: DOWNHILL EXPOSURE LEDGER (E_down; M2C-A04, M2C-B05, M2C-C05)
// =============================================================================

internal enum class M2CDownhillCoverageStatus {
    CONFIRMED_ZERO_RECORDING_DESCENT,
    COMPLETE_RECORDING_DERIVED_DESCRIPTIVE,
    PARTIAL_MEASURED_DESCENT_BOUNDED,
    UNVERIFIED_RECONSTRUCTED_ONLY,
    MISSING_OR_CONFLICTING_ELEVATION_EVIDENCE
}

internal class M2CDownhillIntervalRecord(
    val canonicalActivityId: String,
    val sourceAlias: M2ASourceQualifiedAlias,
    val provenance: M2BSourceProvenance,
    val start: Instant,
    val end: Instant,
    val attributedLocalDate: LocalDate,
    val elevationLoss: Meters,
    val horizontalDistance: Meters,
    val slopePathDistance: Meters,
    val duration: Seconds,
    val observationBasis: M2BObservationBasis,
    val surface: M2BSurface,
    val parentTraceHadPausesOrUnknownContinuity: Boolean,
    gapRejections: List<M2BGAPRejection>
) : Comparable<M2CDownhillIntervalRecord> {
    val gapRejections: List<M2BGAPRejection>

    init {
        RecordPolicy.identifier(canonicalActivityId)
        require(end.isAfter(start) && elevationLoss.value > 0.0 && horizontalDistance.value > 0.0 && slopePathDistance.value > 0.0 && duration.value > 0.0)
        this.gapRejections = Collections.unmodifiableList(
            ArrayList(gapRejections.distinct().sortedWith(compareBy<M2BGAPRejection> { it.start }.thenBy { it.end }.thenBy { it.reason.name }))
        )
    }

    override fun compareTo(other: M2CDownhillIntervalRecord): Int =
        compareBy<M2CDownhillIntervalRecord> { it.start }
            .thenBy { it.end }
            .thenBy { it.canonicalActivityId }
            .thenBy { it.sourceAlias }
            .compare(this, other)

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        canonicalActivityId, Engine15M2CCanonical.normalizeAlias(sourceAlias).canonicalToken(),
        provenance.map { it.canonicalToken() }.sorted().joinToString(","),
        start, end, attributedLocalDate, elevationLoss.canonicalToken(), horizontalDistance.canonicalToken(),
        slopePathDistance.canonicalToken(), duration.canonicalToken(), observationBasis.name, surface.name,
        parentTraceHadPausesOrUnknownContinuity,
        gapRejections.joinToString(",") { "${it.start}|${it.end}|${it.reason.name}" }
    )

    override fun equals(other: Any?): Boolean = other is M2CDownhillIntervalRecord && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

internal class M2CRollingDownhillExposure(
    val bounds: M2CWindowBounds,
    val coverageStatus: M2CDownhillCoverageStatus,
    val cumulativeElevationLossBelief: BeliefState<Meters>,
    val recordedDescentHorizontalDistance: BeliefState<Meters>,
    val recordedDescentSlopePathDistance: BeliefState<Meters>,
    val recordedDescentDuration: BeliefState<Seconds>,
    val contributingActivityCount: Int,
    val partialOrPausedActivityCount: Int,
    val missingElevationActivityCount: Int,
    val conflictingRecordingCount: Int,
    val excludedDuplicateOrOverlapCount: Int,
    retainedGapRejectionReasons: Set<M2BGAPRejectionReason>,
    intervals: List<M2CDownhillIntervalRecord>,
    diagnosticFlags: Set<String>,
    val verifiedWorkloadAuthority: Boolean = false,
    val isBiomechanicalTissueDamageEstimate: Boolean = false
) {
    val retainedGapRejectionReasons: Set<M2BGAPRejectionReason>
    val intervals: List<M2CDownhillIntervalRecord>
    val diagnosticFlags: Set<String>

    init {
        require(!verifiedWorkloadAuthority && !isBiomechanicalTissueDamageEstimate)
        require(contributingActivityCount >= 0 && partialOrPausedActivityCount >= 0 &&
            missingElevationActivityCount >= 0 && conflictingRecordingCount >= 0 && excludedDuplicateOrOverlapCount >= 0)
        when (coverageStatus) {
            M2CDownhillCoverageStatus.MISSING_OR_CONFLICTING_ELEVATION_EVIDENCE ->
                require(cumulativeElevationLossBelief is BeliefState.Unknown)
            M2CDownhillCoverageStatus.PARTIAL_MEASURED_DESCENT_BOUNDED,
            M2CDownhillCoverageStatus.UNVERIFIED_RECONSTRUCTED_ONLY ->
                require(cumulativeElevationLossBelief is BeliefState.Bounded &&
                    cumulativeElevationLossBelief.upper == null &&
                    cumulativeElevationLossBelief.authority == EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL)
            M2CDownhillCoverageStatus.CONFIRMED_ZERO_RECORDING_DESCENT ->
                require(cumulativeElevationLossBelief is BeliefState.Estimated &&
                    cumulativeElevationLossBelief.estimate.value == 0.0 &&
                    cumulativeElevationLossBelief.authority == EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL &&
                    conflictingRecordingCount == 0 && partialOrPausedActivityCount == 0 && missingElevationActivityCount == 0)
            M2CDownhillCoverageStatus.COMPLETE_RECORDING_DERIVED_DESCRIPTIVE ->
                require(cumulativeElevationLossBelief is BeliefState.Estimated &&
                    cumulativeElevationLossBelief.authority == EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL &&
                    conflictingRecordingCount == 0 && partialOrPausedActivityCount == 0 && missingElevationActivityCount == 0)
        }
        this.retainedGapRejectionReasons = Collections.unmodifiableSet(LinkedHashSet(retainedGapRejectionReasons.sortedBy { it.name }))
        this.intervals = Collections.unmodifiableList(ArrayList(intervals.sorted()))
        this.diagnosticFlags = Collections.unmodifiableSet(
            LinkedHashSet(diagnosticFlags.map { f -> RecordPolicy.identifier(f); f }.distinct().sorted())
        )
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        bounds.canonicalRepresentation(), coverageStatus.name, cumulativeElevationLossBelief.canonicalRepresentation(),
        recordedDescentHorizontalDistance.canonicalRepresentation(), recordedDescentSlopePathDistance.canonicalRepresentation(),
        recordedDescentDuration.canonicalRepresentation(), contributingActivityCount, partialOrPausedActivityCount,
        missingElevationActivityCount, conflictingRecordingCount, excludedDuplicateOrOverlapCount,
        retainedGapRejectionReasons.joinToString(",") { it.name },
        intervals.joinToString("|") { it.canonicalRepresentation() },
        diagnosticFlags.joinToString(",")
    )

    override fun equals(other: Any?): Boolean = other is M2CRollingDownhillExposure && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

// =============================================================================
// SECTION 6: INTERNAL LOAD (MOD-09), INTENSITY EXPOSURE & WEEKLY VOLATILITY
// =============================================================================

internal class M2CSessionRpeLoadRecord(
    val canonicalActivityId: String,
    val sourceAlias: M2ASourceQualifiedAlias?,
    opaqueUpstreamRecordIds: Set<String>,
    val discipline: AthleteDiscipline,
    val modality: M2CModalityKind,
    val completedLocalDate: LocalDate,
    val completedAtUtc: Instant,
    val duration: Seconds,
    val cr10Rpe: Double?,
    val loadAu: M2CSessionLoadAu?,
    val isDescriptiveOnly: Boolean = true
) : Comparable<M2CSessionRpeLoadRecord> {
    val opaqueUpstreamRecordIds: Set<String>

    init {
        RecordPolicy.identifier(canonicalActivityId)
        require(isDescriptiveOnly)
        val cleanO = opaqueUpstreamRecordIds.map { id -> RecordPolicy.identifier(id); id }.distinct().sorted()
        require(sourceAlias != null || cleanO.isNotEmpty())
        this.opaqueUpstreamRecordIds = Collections.unmodifiableSet(LinkedHashSet(cleanO))
        if (cr10Rpe == null) {
            require(loadAu == null)
        } else {
            require(cr10Rpe.isFinite() && cr10Rpe in Engine15M2CPolicyRegistry.MOD_09_CR10_MIN_RPE..Engine15M2CPolicyRegistry.MOD_09_CR10_MAX_RPE)
            require(loadAu != null)
            val expected = cr10Rpe * (duration.value / Engine15M2CPolicyRegistry.SECONDS_PER_MINUTE)
            require(abs(loadAu.value - expected) <= 1e-6 * max(1.0, expected))
        }
    }

    override fun compareTo(other: M2CSessionRpeLoadRecord): Int =
        compareBy<M2CSessionRpeLoadRecord> { it.completedLocalDate }
            .thenBy { it.completedAtUtc }
            .thenBy { it.discipline.code }
            .thenBy { it.canonicalActivityId }
            .compare(this, other)

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        canonicalActivityId, sourceAlias?.let(Engine15M2CCanonical::normalizeAlias)?.canonicalToken(),
        opaqueUpstreamRecordIds.joinToString(","), discipline.code, modality.name,
        completedLocalDate, completedAtUtc, duration.canonicalToken(),
        cr10Rpe?.let(CanonicalSiQuantity::canonicalDecimalString), loadAu?.canonicalToken()
    )

    override fun equals(other: Any?): Boolean = other is M2CSessionRpeLoadRecord && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

internal class M2CRollingInternalLoadSummary(
    val bounds: M2CWindowBounds,
    val runningLoadAu: M2CInternalLoadBelief,
    val nonRunningSystemicLoadAu: M2CInternalLoadBelief,
    val combinedSystemicLoadAu: M2CInternalLoadBelief,
    val validRunningRpeCount: Int,
    val missingRunningRpeCount: Int,
    val validNonRunningRpeCount: Int,
    val missingNonRunningRpeCount: Int,
    val invalidRpeExcludedCount: Int,
    records: List<M2CSessionRpeLoadRecord>,
    val impliesModalityInterchangeability: Boolean = false,
    val establishesSystemicFailureCeiling: Boolean = false
) {
    val records: List<M2CSessionRpeLoadRecord>

    init {
        require(!impliesModalityInterchangeability && !establishesSystemicFailureCeiling)
        require(validRunningRpeCount >= 0 && missingRunningRpeCount >= 0 && validNonRunningRpeCount >= 0 && missingNonRunningRpeCount >= 0 && invalidRpeExcludedCount >= 0)
        this.records = Collections.unmodifiableList(ArrayList(records.sorted()))
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        bounds.canonicalRepresentation(), runningLoadAu.canonicalRepresentation(),
        nonRunningSystemicLoadAu.canonicalRepresentation(), combinedSystemicLoadAu.canonicalRepresentation(),
        validRunningRpeCount, missingRunningRpeCount, validNonRunningRpeCount, missingNonRunningRpeCount,
        invalidRpeExcludedCount, records.joinToString("|") { it.canonicalRepresentation() }
    )

    override fun equals(other: Any?): Boolean = other is M2CRollingInternalLoadSummary && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

internal class M2CIntensityDomainExposure(
    val bounds: M2CWindowBounds,
    val moderateWorkDuration: BeliefState<Seconds>,
    val heavyWorkDuration: BeliefState<Seconds>,
    val severeWorkDuration: BeliefState<Seconds>,
    val highIntensityHeavyPlusSevereDuration: BeliefState<Seconds>,
    val neuromuscularWorkDuration: BeliefState<Seconds>,
    val warmUpCoolDownRecoveryDuration: BeliefState<Seconds>,
    val unclassifiedRunningDuration: BeliefState<Seconds>,
    val moderateWorkDistance: BeliefState<Meters>,
    val heavyWorkDistance: BeliefState<Meters>,
    val severeWorkDistance: BeliefState<Meters>,
    val highIntensityHeavyPlusSevereDistance: BeliefState<Meters>,
    val neuromuscularWorkDistance: BeliefState<Meters>,
    val warmUpCoolDownRecoveryDistance: BeliefState<Meters>,
    val unclassifiedRunningDistance: BeliefState<Meters>,
    val fullyClassifiedDurationSessionCount: Int,
    val partiallyClassifiedDurationSessionCount: Int,
    val fullyClassifiedDistanceSessionCount: Int,
    val unclassifiedOrIncompleteSessionCount: Int,
    val conflictingDomainBreakdownCount: Int,
    val authenticatedExecutionAuthority: Boolean = false,
    val promotesResearchOnlyPhysiology: Boolean = false
) {
    init {
        require(!authenticatedExecutionAuthority && !promotesResearchOnlyPhysiology)
        require(fullyClassifiedDurationSessionCount >= 0 && partiallyClassifiedDurationSessionCount >= 0 &&
            fullyClassifiedDistanceSessionCount >= 0 && unclassifiedOrIncompleteSessionCount >= 0 && conflictingDomainBreakdownCount >= 0)
        if (fullyClassifiedDurationSessionCount == 0 && partiallyClassifiedDurationSessionCount == 0 && unclassifiedOrIncompleteSessionCount > 0) {
            require(highIntensityHeavyPlusSevereDuration is BeliefState.Unknown) {
                "M2C-B06/C03: missing intensity classification cannot establish zero high-intensity exposure"
            }
        }
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        bounds.canonicalRepresentation(), moderateWorkDuration.canonicalRepresentation(),
        heavyWorkDuration.canonicalRepresentation(), severeWorkDuration.canonicalRepresentation(),
        highIntensityHeavyPlusSevereDuration.canonicalRepresentation(), neuromuscularWorkDuration.canonicalRepresentation(),
        warmUpCoolDownRecoveryDuration.canonicalRepresentation(), unclassifiedRunningDuration.canonicalRepresentation(),
        moderateWorkDistance.canonicalRepresentation(), heavyWorkDistance.canonicalRepresentation(),
        severeWorkDistance.canonicalRepresentation(), highIntensityHeavyPlusSevereDistance.canonicalRepresentation(),
        neuromuscularWorkDistance.canonicalRepresentation(), warmUpCoolDownRecoveryDistance.canonicalRepresentation(),
        unclassifiedRunningDistance.canonicalRepresentation(), fullyClassifiedDurationSessionCount,
        partiallyClassifiedDurationSessionCount, fullyClassifiedDistanceSessionCount,
        unclassifiedOrIncompleteSessionCount, conflictingDomainBreakdownCount
    )

    override fun equals(other: Any?): Boolean = other is M2CIntensityDomainExposure && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}


internal data class M2CWeeklyBucketTotal(
    val weekOffsetFromMostRecent: Int,
    val startDateInclusive: LocalDate,
    val endDateInclusive: LocalDate,
    val distanceBelief: BeliefState<Meters>,
    val durationBelief: BeliefState<Seconds>,
    val verifiedSessionCount: Int,
    val distinctActiveDayCount: Int,
    val confirmationStatus: M2CConfirmedWeekStatus
) {
    init {
        require(weekOffsetFromMostRecent in 0 until Engine15M2CPolicyRegistry.TRAILING_FOUR_WEEK_COUNT)
        require(startDateInclusive.plusDays(6) == endDateInclusive && verifiedSessionCount >= 0 && distinctActiveDayCount >= 0)
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        weekOffsetFromMostRecent, startDateInclusive, endDateInclusive,
        distanceBelief.canonicalRepresentation(), durationBelief.canonicalRepresentation(),
        verifiedSessionCount, distinctActiveDayCount, confirmationStatus.name
    )
}

internal class M2CWeeklyDistributionSummary(
    weeklyBuckets: List<M2CWeeklyBucketTotal>,
    val exactRecordedSubtotalWeekCount: Int,
    val completeActualWeeklyWorkloadCount: Int,
    val partialOrBoundedWeekCount: Int,
    val activeWeekCount: Int,
    val confirmedSparseWeekCount: Int,
    val unconfirmedGapWeekCount: Int,
    val coverageQualification: HistoryCoverageStatus,
    val meanWeeklyDistance: Meters?,
    val medianWeeklyDistance: Meters?,
    val trimmedMeanWeeklyDistance: Meters?,
    val sampleStdDevWeeklyDistance: Meters?,
    val coefficientOfVariation: DimensionlessRatio?,
    val isolatedConfirmedSparseWeekPattern: Boolean,
    diagnosticFlags: Set<String>,
    val hasUnresolvedVolatilityPolicy: Boolean = !Engine15M2CPolicyRegistry.WEEKLY_CV_VOLATILITY_THRESHOLD_APPROVED,
    val authorizesStabilizationAction: Boolean = false
) {
    val weeklyBuckets: List<M2CWeeklyBucketTotal>
    val diagnosticFlags: Set<String>

    init {
        require(weeklyBuckets.size == Engine15M2CPolicyRegistry.TRAILING_FOUR_WEEK_COUNT)
        require(weeklyBuckets.map { it.weekOffsetFromMostRecent }.sorted() == listOf(0, 1, 2, 3))
        require(exactRecordedSubtotalWeekCount in 0..4 && completeActualWeeklyWorkloadCount in 0..exactRecordedSubtotalWeekCount)
        require(partialOrBoundedWeekCount in 0..4 && activeWeekCount in 0..4 &&
            confirmedSparseWeekCount in 0..4 && unconfirmedGapWeekCount in 0..4)
        require(hasUnresolvedVolatilityPolicy && !authorizesStabilizationAction)
        if (coefficientOfVariation != null) {
            require(exactRecordedSubtotalWeekCount >= 2 && meanWeeklyDistance != null &&
                meanWeeklyDistance.value > Engine15M2CPolicyRegistry.ZERO_MEAN_CV_GUARD_METERS &&
                sampleStdDevWeeklyDistance != null)
        }
        this.weeklyBuckets = Collections.unmodifiableList(ArrayList(weeklyBuckets.sortedBy { it.weekOffsetFromMostRecent }))
        this.diagnosticFlags = Collections.unmodifiableSet(
            LinkedHashSet(diagnosticFlags.map { f -> RecordPolicy.identifier(f); f }.distinct().sorted())
        )
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        weeklyBuckets.joinToString("|") { it.canonicalRepresentation() },
        exactRecordedSubtotalWeekCount, completeActualWeeklyWorkloadCount, partialOrBoundedWeekCount,
        activeWeekCount, confirmedSparseWeekCount, unconfirmedGapWeekCount, coverageQualification.name,
        meanWeeklyDistance?.canonicalToken(), medianWeeklyDistance?.canonicalToken(),
        trimmedMeanWeeklyDistance?.canonicalToken(), sampleStdDevWeeklyDistance?.canonicalToken(),
        coefficientOfVariation?.canonicalToken(), isolatedConfirmedSparseWeekPattern,
        diagnosticFlags.joinToString(",")
    )

    override fun equals(other: Any?): Boolean = other is M2CWeeklyDistributionSummary && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

// =============================================================================
// SECTION 7: TEN-FAMILY QUALITY EXPOSURE LEDGER (CON-06; M2C-C02)
// =============================================================================

internal enum class M2CQualityWorkoutFamily(
    val requiresIndependentIntroExposure: Boolean,
    partialTransferSources: Set<String>
) {
    NEUROMUSCULAR_STRIDES(true, emptySet()),
    SHORT_HILL_SPRINTS(true, setOf("NEUROMUSCULAR_STRIDES")),
    TEMPO(true, setOf("HALF_MARATHON_PACE", "THRESHOLD")),
    THRESHOLD(true, setOf("TEMPO", "HALF_MARATHON_PACE")),
    HALF_MARATHON_PACE(false, setOf("TEMPO", "THRESHOLD", "MARATHON_PACE_WORK")),
    MARATHON_PACE_WORK(true, setOf("TEMPO", "HALF_MARATHON_PACE")),
    LONG_HILL_INTERVALS(true, setOf("TEMPO", "THRESHOLD", "SHORT_HILL_SPRINTS")),
    VO2MAX_INTERVALS(true, setOf("THRESHOLD", "LONG_HILL_INTERVALS")),
    SHORT_REPETITIONS(true, setOf("NEUROMUSCULAR_STRIDES", "VO2MAX_INTERVALS")),
    PROGRESSIVE_OR_FAST_FINISH_LONG_RUN(true, setOf("TEMPO", "MARATHON_PACE_WORK"));

    val partialTransferSources: Set<String> = Collections.unmodifiableSet(LinkedHashSet(partialTransferSources))
}

internal enum class M2CFamilyExposureState {
    UNKNOWN_EXPOSURE,
    CONFIRMED_ABSENT,
    SELF_REPORTED_EXPERIENCE,
    OBSERVED_COMPLETED,
    SUCCESSFUL_EXPOSURE,
    EXCESSIVE_OR_UNSUCCESSFUL
}

internal enum class M2CRecoveryEvidenceState {
    VERIFIED_ACCEPTABLE,
    REPORTED_ACCEPTABLE_UNAUTHENTICATED_PRE_M9,
    ADVERSE_SYMPTOM_OR_FATIGUE,
    MISSING_UNREPORTED
}

internal enum class M2CQualityExecutionOutcome {
    COMPLETED_ON_TARGET,
    COMPLETED_UNVERIFIED_EXECUTION,
    OVERSHOT_OR_FADED,
    ABORTED_OR_UNSUCCESSFUL
}

internal class M2CQualityFamilyObservationInput(
    val accountId: String,
    val canonicalActivity: CanonicalActivity,
    val sourceAlias: M2ASourceQualifiedAlias,
    val family: M2CQualityWorkoutFamily,
    val totalWorkDuration: Seconds,
    val longestBoutDuration: Seconds,
    val executionOutcome: M2CQualityExecutionOutcome,
    val executionTrustTier: M2CEvidenceTrustTier = M2CEvidenceTrustTier.REPORTED_UNVERIFIED,
    val recoveryEvidence: M2CRecoveryEvidenceState = M2CRecoveryEvidenceState.MISSING_UNREPORTED,
    val recoveryTrustTier: M2CEvidenceTrustTier = M2CEvidenceTrustTier.REPORTED_UNVERIFIED,
    val recordedAtUtc: Instant,
    val isPlannedOrProjected: Boolean = false
) {
    init {
        RecordPolicy.identifier(accountId)
        require(accountId == canonicalActivity.accountId)
        require(longestBoutDuration.value <= totalWorkDuration.value + Engine15M2CPolicyRegistry.DURATION_CONSERVATION_TOLERANCE_SECONDS)
    }
}

internal data class M2CFamilySelfReportInput(
    val accountId: String,
    val family: M2CQualityWorkoutFamily,
    val recordedAtUtc: Instant,
    val confirmedNoExperience: Boolean = false,
    val declaredRecentFamiliarity: Boolean = false,
    val sourceAlias: M2ASourceQualifiedAlias
) {
    init {
        RecordPolicy.identifier(accountId)
        require(!(confirmedNoExperience && declaredRecentFamiliarity))
    }
}

internal class M2CQualityFamilyExposureRecord(
    val family: M2CQualityWorkoutFamily,
    val exposureState: M2CFamilyExposureState,
    val recentRecoveryEvidence: M2CRecoveryEvidenceState,
    val observedSessionCount56d: Int,
    val authenticatedSuccessfulSessionCount56d: Int,
    val unsuccessfulSessionCount56d: Int,
    val conflictingObservationCount56d: Int,
    val adverseRecoveryConflictPreserved: Boolean,
    val totalObservedWorkDuration56d: BeliefState<Seconds>,
    val longestObservedBoutDuration56d: Seconds?,
    val mostRecentObservedDate: LocalDate?,
    val requiresIndependentIntroExposure: Boolean,
    partialTransferSourceFamilies: Set<M2CQualityWorkoutFamily>,
    canonicalActivityIds: Set<String>,
    sourceAliases: Set<M2ASourceQualifiedAlias>,
    val transferFabricatesCompletedExposure: Boolean = false,
    val authorizesWorkoutDose: Boolean = false
) {
    val partialTransferSourceFamilies: Set<M2CQualityWorkoutFamily>
    val canonicalActivityIds: Set<String>
    val sourceAliases: Set<M2ASourceQualifiedAlias>

    init {
        require(!transferFabricatesCompletedExposure && !authorizesWorkoutDose)
        require(observedSessionCount56d >= 0 && authenticatedSuccessfulSessionCount56d >= 0 &&
            unsuccessfulSessionCount56d >= 0 && conflictingObservationCount56d >= 0)
        if (exposureState == M2CFamilyExposureState.SUCCESSFUL_EXPOSURE) {
            require(recentRecoveryEvidence == M2CRecoveryEvidenceState.VERIFIED_ACCEPTABLE &&
                authenticatedSuccessfulSessionCount56d >= 1 &&
                Engine15M2CPolicyRegistry.TRUSTED_M9_EXECUTION_CUSTODY_ACTIVE)
        }
        if (adverseRecoveryConflictPreserved) {
            require(recentRecoveryEvidence == M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE &&
                exposureState == M2CFamilyExposureState.EXCESSIVE_OR_UNSUCCESSFUL)
        }
        this.partialTransferSourceFamilies = Collections.unmodifiableSet(LinkedHashSet(partialTransferSourceFamilies.sortedBy { it.ordinal }))
        this.canonicalActivityIds = Collections.unmodifiableSet(LinkedHashSet(canonicalActivityIds.map { id -> RecordPolicy.identifier(id); id }.distinct().sorted()))
        this.sourceAliases = Collections.unmodifiableSet(LinkedHashSet(sourceAliases.map(Engine15M2CCanonical::normalizeAlias).distinct().sorted()))
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        family.name, exposureState.name, recentRecoveryEvidence.name,
        observedSessionCount56d, authenticatedSuccessfulSessionCount56d, unsuccessfulSessionCount56d,
        conflictingObservationCount56d, adverseRecoveryConflictPreserved,
        totalObservedWorkDuration56d.canonicalRepresentation(), longestObservedBoutDuration56d?.canonicalToken(),
        mostRecentObservedDate, requiresIndependentIntroExposure,
        partialTransferSourceFamilies.joinToString(",") { it.name },
        canonicalActivityIds.joinToString(","), sourceAliases.joinToString(",") { it.canonicalToken() }
    )

    override fun equals(other: Any?): Boolean = other is M2CQualityFamilyExposureRecord && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

internal class M2CQualityExposureLedger(
    val bounds: M2CWindowBounds,
    recordsByFamily: Map<M2CQualityWorkoutFamily, M2CQualityFamilyExposureRecord>,
    val excludedProjectedOrPlannedCount: Int,
    diagnosticFlags: Set<String>
) {
    val recordsByFamily: Map<M2CQualityWorkoutFamily, M2CQualityFamilyExposureRecord>
    val diagnosticFlags: Set<String>

    init {
        require(bounds.kind == M2CRollingWindowKind.TRAILING_56D)
        require(recordsByFamily.keys == M2CQualityWorkoutFamily.entries.toSet())
        require(excludedProjectedOrPlannedCount >= 0)
        val sorted = TreeMap<M2CQualityWorkoutFamily, M2CQualityFamilyExposureRecord>()
        for ((k, v) in recordsByFamily) { require(v.family == k); sorted[k] = v }
        this.recordsByFamily = Collections.unmodifiableMap(sorted)
        this.diagnosticFlags = Collections.unmodifiableSet(LinkedHashSet(diagnosticFlags.map { f -> RecordPolicy.identifier(f); f }.distinct().sorted()))
    }

    fun forFamily(family: M2CQualityWorkoutFamily): M2CQualityFamilyExposureRecord = recordsByFamily.getValue(family)

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        bounds.canonicalRepresentation(), excludedProjectedOrPlannedCount,
        recordsByFamily.values.joinToString("|") { it.canonicalRepresentation() },
        diagnosticFlags.joinToString(",")
    )

    override fun equals(other: Any?): Boolean = other is M2CQualityExposureLedger && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

// =============================================================================
// SECTION 8: MONOTONIC RESTRICTIONS, MULTIMODAL, VECTOR, PROJECTION & HANDOFF
// =============================================================================

/**
 * Context- and workload-bound monotonic effective restriction wrapper preserving all non-neutral
 * upstream M2-B reasons (including EVALUATION_CONTEXT_MISMATCH) so restrictions may only
 * strengthen, never weaken (M2C-C04, D1-01, D2-04, D3-03).
 */
internal class M2CEffectiveRestrictionState private constructor(
    val context: M2BEvaluationContext,
    val observedWorkloadContentHash: String,
    val policyVersion: String,
    val upstreamM2BDecision: M2BRestrictionDecision,
    upstreamReasons: Set<M2BRestrictionReason>,
    effectiveReasons: Set<M2BRestrictionReason>,
    val hold: Boolean,
    val hrQuarantined: Boolean,
    val recoveryOnly: Boolean,
    val verifiedClearance: Boolean = false
) {
    val upstreamReasons: Set<M2BRestrictionReason> = Collections.unmodifiableSet(LinkedHashSet(upstreamReasons.sortedBy { it.name }))
    val effectiveReasons: Set<M2BRestrictionReason> = Collections.unmodifiableSet(LinkedHashSet(effectiveReasons.sortedBy { it.name }))
    val reasons: Set<M2BRestrictionReason> get() = effectiveReasons

    init {
        RecordPolicy.identifier(policyVersion)
        require(observedWorkloadContentHash.isNotBlank())
        require(!verifiedClearance) { "Pre-M9 M2-C cannot independently verify restriction clearance" }
        val nonNeutralUpstream = this.upstreamReasons.filter { it != M2BRestrictionReason.NO_RESTRICTION_REPORTED }
        require(this.effectiveReasons.containsAll(nonNeutralUpstream)) {
            "Non-neutral upstream M2-B restriction reasons cannot be dropped"
        }
        if (upstreamM2BDecision.hold) require(hold)
        if (upstreamM2BDecision.hrQuarantined) require(hrQuarantined)
        if (upstreamM2BDecision.recoveryOnly) require(recoveryOnly)
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        context.accountId, context.discipline.code, context.physiologicalModality.name, context.asOf, context.zone.id,
        observedWorkloadContentHash, policyVersion,
        upstreamReasons.joinToString(",") { it.name },
        effectiveReasons.joinToString(",") { it.name },
        hold, hrQuarantined, recoveryOnly, verifiedClearance
    )

    override fun equals(other: Any?): Boolean = other is M2CEffectiveRestrictionState && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()

    internal companion object {
        internal fun strengthen(
            context: M2BEvaluationContext,
            observedWorkloadContentHash: String,
            upstream: M2BRestrictionDecision,
            addedReasons: Set<M2BRestrictionReason>
        ): M2CEffectiveRestrictionState {
            val nonNeutral = (upstream.reasons + addedReasons)
                .filter { it != M2BRestrictionReason.NO_RESTRICTION_REPORTED }
                .toSortedSet()
            val effective = if (nonNeutral.isEmpty()) setOf(M2BRestrictionReason.NO_RESTRICTION_REPORTED) else nonNeutral
            val hold = upstream.hold || effective.any {
                it in setOf(
                    M2BRestrictionReason.PERSISTENT_HOLD, M2BRestrictionReason.CLEARANCE_CLAIMED,
                    M2BRestrictionReason.UNKNOWN_STATE, M2BRestrictionReason.RUNNING_RESTRICTION
                )
            }
            val recoveryOnly = upstream.recoveryOnly || M2BRestrictionReason.RECOVERY_INTENSITY in effective
            val hrQuar = upstream.hrQuarantined || hold || recoveryOnly || M2BRestrictionReason.HR_QUARANTINE in effective
            return M2CEffectiveRestrictionState(
                context = context,
                observedWorkloadContentHash = observedWorkloadContentHash,
                policyVersion = Engine15M2CPolicyRegistry.POLICY_VERSION,
                upstreamM2BDecision = upstream,
                upstreamReasons = upstream.reasons,
                effectiveReasons = effective,
                hold = hold,
                hrQuarantined = hrQuar,
                recoveryOnly = recoveryOnly,
                verifiedClearance = false
            )
        }
    }
}

internal enum class M2CInterruptionBucket {
    DAYS_0_TO_6, DAYS_7_TO_13, DAYS_14_TO_27, DAYS_28_TO_55, DAYS_56_TO_83, DAYS_84_PLUS, UNCONFIRMED_MISSING_SYNC;

    companion object {
        fun fromConfirmedBreakDays(days: Int): M2CInterruptionBucket {
            require(days >= 0)
            return when {
                days <= Engine15M2CPolicyRegistry.POL_033_BUCKET_0_6_MAX_DAYS -> DAYS_0_TO_6
                days <= Engine15M2CPolicyRegistry.POL_033_BUCKET_7_13_MAX_DAYS -> DAYS_7_TO_13
                days <= Engine15M2CPolicyRegistry.POL_033_BUCKET_14_27_MAX_DAYS -> DAYS_14_TO_27
                days <= Engine15M2CPolicyRegistry.POL_033_BUCKET_28_55_MAX_DAYS -> DAYS_28_TO_55
                days <= Engine15M2CPolicyRegistry.POL_033_BUCKET_56_83_MAX_DAYS -> DAYS_56_TO_83
                else -> DAYS_84_PLUS
            }
        }
    }
}

internal data class M2CInterruptionEvidenceSummary(
    val kind: M2CInterruptionKind,
    val bucket: M2CInterruptionBucket,
    val confirmedBreakDays: Int?,
    val postIllnessConservativeAndHrRestrictionRequired: Boolean,
    val missingSyncTreatedAsConfirmedBreak: Boolean = false,
    val grantsMedicalClearance: Boolean = false
) {
    init {
        require(!missingSyncTreatedAsConfirmedBreak && !grantsMedicalClearance)
        if (bucket == M2CInterruptionBucket.UNCONFIRMED_MISSING_SYNC) {
            require(kind == M2CInterruptionKind.NONE && confirmedBreakDays == null)
        }
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        kind.name, bucket.name, confirmedBreakDays, postIllnessConservativeAndHrRestrictionRequired
    )
}

internal data class M2CModalityExposureSummary(
    val modality: M2CModalityKind,
    val verifiedPinnedSessionCount28d: Int,
    val ambiguousPinnedSessionCount28d: Int,
    val unpinnedDescriptiveSessionCount28d: Int,
    val totalDuration28d: BeliefState<Seconds>,
    val totalDistance28d: BeliefState<Meters>,
    val internalLoadAu28d: M2CInternalLoadBelief
) {
    init {
        require(verifiedPinnedSessionCount28d >= 0 && ambiguousPinnedSessionCount28d >= 0 && unpinnedDescriptiveSessionCount28d >= 0)
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        modality.name, verifiedPinnedSessionCount28d, ambiguousPinnedSessionCount28d, unpinnedDescriptiveSessionCount28d,
        totalDuration28d.canonicalRepresentation(), totalDistance28d.canonicalRepresentation(),
        internalLoadAu28d.canonicalRepresentation()
    )
}

internal class M2CSystemicMultimodalExposure(
    byModality: Map<M2CModalityKind, M2CModalityExposureSummary>,
    val totalNonRunningDuration28d: BeliefState<Seconds>,
    val crossModalRunningTissueMismatchSignal: Boolean,
    val nonRunningFabricatesRunningImpactTolerance: Boolean = false
) {
    val byModality: Map<M2CModalityKind, M2CModalityExposureSummary>

    init {
        require(!nonRunningFabricatesRunningImpactTolerance)
        val sorted = TreeMap<M2CModalityKind, M2CModalityExposureSummary>()
        for ((k, v) in byModality) { require(v.modality == k); sorted[k] = v }
        this.byModality = Collections.unmodifiableMap(sorted)
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        totalNonRunningDuration28d.canonicalRepresentation(), crossModalRunningTissueMismatchSignal,
        byModality.values.joinToString("|") { it.canonicalRepresentation() }
    )

    override fun equals(other: Any?): Boolean = other is M2CSystemicMultimodalExposure && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

internal class M2COrthogonalWorkloadVector(
    val running7d: M2CRunningWindowExposure,
    val running28d: M2CRunningWindowExposure,
    val running30d: M2CRunningWindowExposure,
    val running90dAnchor: M2CVerifiedSingleSessionAnchor,
    sameDayCompletedRunningSessions: List<M2CVerifiedRunningSessionRecord>,
    val downhill7d: M2CRollingDownhillExposure,
    val downhill28d: M2CRollingDownhillExposure,
    val downhill30d: M2CRollingDownhillExposure,
    val intensityExposure28d: M2CIntensityDomainExposure,
    val qualityLedger56d: M2CQualityExposureLedger,
    val internalLoad28d: M2CRollingInternalLoadSummary,
    val systemicMultimodal28d: M2CSystemicMultimodalExposure,
    val weeklyDistribution28d: M2CWeeklyDistributionSummary,
    val collapsesToSingleCompositeScore: Boolean = false
) {
    val sameDayCompletedRunningSessions: List<M2CVerifiedRunningSessionRecord>

    init {
        require(!collapsesToSingleCompositeScore)
        require(running7d.bounds.kind == M2CRollingWindowKind.TRAILING_7D)
        require(running28d.bounds.kind == M2CRollingWindowKind.TRAILING_28D)
        require(running30d.bounds.kind == M2CRollingWindowKind.PRECEDING_30D)
        require(running90dAnchor.bounds.kind == M2CRollingWindowKind.PRECEDING_90D)
        require(downhill7d.bounds.kind == M2CRollingWindowKind.TRAILING_7D)
        require(downhill28d.bounds.kind == M2CRollingWindowKind.TRAILING_28D)
        require(downhill30d.bounds.kind == M2CRollingWindowKind.PRECEDING_30D)
        require(sameDayCompletedRunningSessions.all { it.sameDayAsEvaluation })
        this.sameDayCompletedRunningSessions = Collections.unmodifiableList(ArrayList(sameDayCompletedRunningSessions.sorted()))
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        running7d.canonicalRepresentation(), running28d.canonicalRepresentation(),
        running30d.canonicalRepresentation(), running90dAnchor.canonicalRepresentation(),
        sameDayCompletedRunningSessions.joinToString("|") { it.canonicalRepresentation() },
        downhill7d.canonicalRepresentation(), downhill28d.canonicalRepresentation(), downhill30d.canonicalRepresentation(),
        intensityExposure28d.canonicalRepresentation(), qualityLedger56d.canonicalRepresentation(),
        internalLoad28d.canonicalRepresentation(), systemicMultimodal28d.canonicalRepresentation(),
        weeklyDistribution28d.canonicalRepresentation()
    )

    override fun equals(other: Any?): Boolean = other is M2COrthogonalWorkloadVector && canonicalRepresentation() == other.canonicalRepresentation()
    override fun hashCode(): Int = canonicalRepresentation().hashCode()
}

/**
 * Isolated conditional projected workload boundary (INV-02, M2C-C06).
 * Horizon-specific observedEvidenceShare(w) over sliding projected weeks is explicitly deferred to M2-D.
 */
internal data class M2CConditionalProjectedAnchorContract(
    val projectedWorkloadContentHash: String?,
    val projectedSessionCount: Int,
    val horizonObservedEvidenceShareDeferredToM2D: Boolean = true,
    val antiChainingRuleRequired: Boolean = true,
    val preCutbackAnchorPreservationRequired: Boolean = true,
    val writesToObservedWorkloadState: Boolean = false,
    val establishesObservedCapacityOrQuality: Boolean = false
) {
    init {
        require(projectedSessionCount >= 0)
        require(horizonObservedEvidenceShareDeferredToM2D && antiChainingRuleRequired && preCutbackAnchorPreservationRequired)
        require(!writesToObservedWorkloadState && !establishesObservedCapacityOrQuality)
    }

    fun canonicalRepresentation(): String = Engine15M2CCanonical.frame(
        projectedWorkloadContentHash, projectedSessionCount, horizonObservedEvidenceShareDeferredToM2D,
        antiChainingRuleRequired, preCutbackAnchorPreservationRequired
    )
}

internal class M2CDownstreamWorkloadHandoff internal constructor(
    val context: M2BEvaluationContext,
    val policyVersion: String,
    val upstreamObservedWorkloadHash: String,
    val upstreamAthleteSnapshotId: String?,
    val upstreamM2BContentHash: String,
    val upstreamM2BPolicyVersion: String,
    val reconciledInventoryContentHash: String,
    val baseline: M2CCoverageAwareBaseline,
    val workloadVector: M2COrthogonalWorkloadVector,
    val defaultLongRunStoppingContract: M2CStoppingConditionContract,
    val interruptionEvidence: M2CInterruptionEvidenceSummary,
    val effectiveRestrictionState: M2CEffectiveRestrictionState,
    persistentRestrictionIds: Set<String>,
    paceConflicts: Set<M2BPaceConflict>,
    val operationalThresholdCalibration: M2APerformanceCalibrationResult?,
    val paceCalibrationEligible: Boolean,
    val hrZoneAuthorized: Boolean,
    val projectedBoundaryContract: M2CConditionalProjectedAnchorContract,
    unresolvedPolicyDependencies: Set<String>,
    sourceProvenance: Set<M2ASourceQualifiedAlias>,
    opaqueUpstreamRecordIds: Set<String>,
    diagnosticFlags: Set<String>,
    val productionAuthorized: Boolean = false,
    val workoutPrescriptionAuthorized: Boolean = false,
    val independentlyClearsPersistentHold: Boolean = false
) {
    val persistentRestrictionIds: Set<String>
    val paceConflicts: Set<M2BPaceConflict>
    val unresolvedPolicyDependencies: Set<String>
    val sourceProvenance: Set<M2ASourceQualifiedAlias>
    val opaqueUpstreamRecordIds: Set<String>
    val diagnosticFlags: Set<String>
    val contentHash: String

    init {
        RecordPolicy.identifier(policyVersion)
        RecordPolicy.identifier(upstreamM2BPolicyVersion)
        if (upstreamAthleteSnapshotId != null) RecordPolicy.identifier(upstreamAthleteSnapshotId)
        require(!productionAuthorized && !workoutPrescriptionAuthorized && !hrZoneAuthorized && !independentlyClearsPersistentHold)
        require(effectiveRestrictionState.context == context &&
            effectiveRestrictionState.observedWorkloadContentHash == upstreamObservedWorkloadHash &&
            effectiveRestrictionState.policyVersion == policyVersion) {
            "D2-04: handoff effectiveRestrictionState must bind to exact context, observedWorkloadHash, and policyVersion"
        }
        require(workloadVector.running90dAnchor.context == context &&
            workloadVector.running90dAnchor.observedWorkloadContentHash == upstreamObservedWorkloadHash &&
            workloadVector.running90dAnchor.reconciledInventoryContentHash == reconciledInventoryContentHash) {
            "FINAL-02: handoff running90dAnchor must bind to exact reconciledInventoryContentHash"
        }
        if (effectiveRestrictionState.hold || effectiveRestrictionState.recoveryOnly) {
            require(!paceCalibrationEligible) {
                "M2C-C04: active hold or recovery-only restriction must withhold ordinary paceCalibrationEligible"
            }
        }
        this.persistentRestrictionIds = Collections.unmodifiableSet(LinkedHashSet(persistentRestrictionIds.map { id -> RecordPolicy.identifier(id); id }.distinct().sorted()))
        this.paceConflicts = Collections.unmodifiableSet(LinkedHashSet(paceConflicts.sortedBy { it.name }))
        this.unresolvedPolicyDependencies = Collections.unmodifiableSet(LinkedHashSet(unresolvedPolicyDependencies.map { p -> RecordPolicy.identifier(p); p }.distinct().sorted()))
        this.sourceProvenance = Collections.unmodifiableSet(LinkedHashSet(sourceProvenance.map(Engine15M2CCanonical::normalizeAlias).distinct().sorted()))
        this.opaqueUpstreamRecordIds = Collections.unmodifiableSet(LinkedHashSet(opaqueUpstreamRecordIds.map { id -> RecordPolicy.identifier(id); id }.distinct().sorted()))
        this.diagnosticFlags = Collections.unmodifiableSet(LinkedHashSet(diagnosticFlags.map { f -> RecordPolicy.identifier(f); f }.distinct().sorted()))
        this.contentHash = Engine15M2CCanonical.sha256Hex(canonicalPayload())
    }

    internal fun canonicalPayload(): String = Engine15M2CCanonical.frame(
        "M2C-HANDOFF", policyVersion, context.accountId, context.discipline.code,
        context.physiologicalModality.name, context.asOf, context.zone.id,
        upstreamObservedWorkloadHash, upstreamAthleteSnapshotId, upstreamM2BContentHash, upstreamM2BPolicyVersion,
        reconciledInventoryContentHash, baseline.canonicalRepresentation(), workloadVector.canonicalRepresentation(),
        defaultLongRunStoppingContract.canonicalRepresentation(), interruptionEvidence.canonicalRepresentation(),
        effectiveRestrictionState.canonicalRepresentation(), persistentRestrictionIds.joinToString(","),
        paceConflicts.joinToString(",") { it.name }, operationalThresholdCalibration?.contentHash,
        paceCalibrationEligible, projectedBoundaryContract.canonicalRepresentation(),
        unresolvedPolicyDependencies.joinToString(","),
        sourceProvenance.joinToString(",") { it.canonicalToken() },
        opaqueUpstreamRecordIds.joinToString(","),
        diagnosticFlags.joinToString(",")
    )

    override fun equals(other: Any?): Boolean = other is M2CDownstreamWorkloadHandoff && contentHash == other.contentHash
    override fun hashCode(): Int = contentHash.hashCode()
}
