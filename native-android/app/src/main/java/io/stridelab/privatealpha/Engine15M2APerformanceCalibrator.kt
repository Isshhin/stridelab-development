package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Locale
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

internal enum class M2ABenchmarkFreshness {
    FRESH,
    AGING,
    STALE
}

/**
 * Explicit maximal-effort execution intent (DEF-13).
 * Ordinary training sessions MUST NEVER auto-promote into maximal race benchmarks.
 */
internal enum class M2APerformanceIntent {
    VERIFIED_OFFICIAL_RACE,
    VERIFIED_TIME_TRIAL,
    SELF_REPORTED_RACE_OR_TIME_TRIAL,
    ORDINARY_TRAINING_RUN,
    STRUCTURED_INTERVAL_WORKOUT,
    UNKNOWN_INTENT;

    val isEligibleMaximalPerformance: Boolean
        get() = this == VERIFIED_OFFICIAL_RACE ||
            this == VERIFIED_TIME_TRIAL ||
            this == SELF_REPORTED_RACE_OR_TIME_TRIAL
}

/**
 * Explicit temporal compatibility policy for combining two races in MOD-05 (DEF-02 / DEF-10).
 * No unapproved production day-window constant is hardcoded; default requires an explicit
 * shared fitnessEpochId or identical date unless an approved maxSeparationDays is configured.
 */
internal sealed interface M2ATemporalCompatibilityPolicy {
    data object ExplicitSharedEpochOrSameDate : M2ATemporalCompatibilityPolicy

    data class MaxSeparationDays(val maxDays: Int) : M2ATemporalCompatibilityPolicy {
        init {
            require(maxDays >= 0) { "maxDays must be >= 0, was $maxDays" }
        }
    }

    fun areCompatible(a: M2ASelectedBenchmark, b: M2ASelectedBenchmark): Boolean {
        if (a.fitnessEpochId != null && b.fitnessEpochId != null) {
            return a.fitnessEpochId == b.fitnessEpochId
        }
        return when (this) {
            is ExplicitSharedEpochOrSameDate -> a.performedOn == b.performedOn
            is MaxSeparationDays ->
                a.freshness == b.freshness &&
                    abs(ChronoUnit.DAYS.between(a.performedOn, b.performedOn)) <= maxDays.toLong()
        }
    }
}

/**
 * Source-qualified alias preventing cross-provider ID collisions (DEF-01).
 */
internal data class M2ASourceQualifiedAlias(
    val provider: String,
    val externalId: String
) : Comparable<M2ASourceQualifiedAlias> {
    val normalizedProvider: String = provider.trim().lowercase(Locale.ROOT)

    init {
        require(normalizedProvider.isNotBlank() && normalizedProvider.length <= 128) {
            "provider must be non-blank and <= 128 chars"
        }
        RecordPolicy.identifier(externalId)
    }

    fun canonicalToken(): String =
        "${AthleteStateSnapshot.canonicalSegment(normalizedProvider)}/${AthleteStateSnapshot.canonicalSegment(externalId)}"

    override fun compareTo(other: M2ASourceQualifiedAlias): Int =
        compareBy<M2ASourceQualifiedAlias> { it.normalizedProvider }
            .thenBy { it.externalId }
            .compare(this, other)
}

/**
 * Separated authorization summary resolving DEF-03:
 * distinguishes verified reference presence, calibration participation precondition,
 * provisional coaching guidance, and downstream M2-B/M3/M4 zone/workout authority.
 */
internal data class M2ACalibrationAuthorization(
    val hasVerifiedPerformanceReference: Boolean,
    val eligibleToParticipateInPaceCalibration: Boolean,
    val provisionalPaceGuidanceAvailable: Boolean,
    val workoutSpecificPacePrescriptionAuthorized: Boolean = false,
    val hrTrainingZoneAuthorized: Boolean = false
) {
    init {
        require(!workoutSpecificPacePrescriptionAuthorized) {
            "DEF-03: M2-A alone cannot grant final workout-specific pace prescription authority (requires M2-B/M3/M4 checks)"
        }
        require(!hrTrainingZoneAuthorized) {
            "DEF-03 / INV-05: M2-A performance calibration never grants HR training-zone authority"
        }
        if (eligibleToParticipateInPaceCalibration) {
            require(hasVerifiedPerformanceReference) {
                "DEF-03: pace calibration participation precondition requires a verified performance reference"
            }
        }
    }

    fun canonicalRepresentation(): String =
        "hasVer=$hasVerifiedPerformanceReference|eligCal=$eligibleToParticipateInPaceCalibration|" +
            "provGuide=$provisionalPaceGuidanceAvailable|wkAuth=$workoutSpecificPacePrescriptionAuthorized|hrAuth=$hrTrainingZoneAuthorized"
}

/**
 * Six strictly separated endurance mileage semantics resolving DEF-05.
 * Only VerifiedContinuousObserved and ProvisionalSelfReport represent typical weekly mileage estimates.
 * PartialObservedLowerBound and ConflictConservativeCeiling are NEVER passed to V&V as point typical averages!
 */
internal sealed interface M2AEnduranceMileageAssessment {
    fun canonicalRepresentation(): String

    data class VerifiedContinuousObserved(
        val typicalWeeklyMeters: Meters
    ) : M2AEnduranceMileageAssessment {
        override fun canonicalRepresentation(): String =
            "VERIFIED_CONTINUOUS|wk=${typicalWeeklyMeters.canonicalToken()}"
    }

    data class ProvisionalSelfReport(
        val typicalWeeklyMeters: Meters
    ) : M2AEnduranceMileageAssessment {
        override fun canonicalRepresentation(): String =
            "PROVISIONAL_SELF_REPORT|wk=${typicalWeeklyMeters.canonicalToken()}"
    }

    data class PartialObservedLowerBound(
        val observed28dLowerBoundWeeklyMeters: Meters,
        val provisionalSelfReportWeeklyMeters: Meters?
    ) : M2AEnduranceMileageAssessment {
        override fun canonicalRepresentation(): String =
            "PARTIAL_OBSERVED_LOWER_BOUND|lo=${observed28dLowerBoundWeeklyMeters.canonicalToken()}|" +
                "sr=${provisionalSelfReportWeeklyMeters?.canonicalToken() ?: "NONE"}"
    }

    data class ConflictConservativeCeiling(
        val conservativePlanningCeilingMeters: Meters,
        val observedContinuousWeeklyMeters: Meters,
        val selfReportedWeeklyMeters: Meters
    ) : M2AEnduranceMileageAssessment {
        override fun canonicalRepresentation(): String =
            "CONFLICT_CONSERVATIVE_CEILING|cap=${conservativePlanningCeilingMeters.canonicalToken()}|" +
                "obs=${observedContinuousWeeklyMeters.canonicalToken()}|sr=${selfReportedWeeklyMeters.canonicalToken()}"
    }

    data object ConfirmedZero : M2AEnduranceMileageAssessment {
        override fun canonicalRepresentation(): String = "CONFIRMED_ZERO"
    }

    data class MissingEvidence(val reasonCode: String) : M2AEnduranceMileageAssessment {
        init { RecordPolicy.identifier(reasonCode) }
        override fun canonicalRepresentation(): String =
            "MISSING|reason=${AthleteStateSnapshot.canonicalSegment(reasonCode)}"
    }
}

internal enum class M2ACalibrationAuthorityMode {
    VERIFIED_RECENT_BENCHMARK,
    VERIFIED_AGING_BENCHMARK,
    PROVISIONAL_SELF_REPORT_BENCHMARK,
    EFFORT_AND_TALK_TEST_FALLBACK
}

internal enum class M2ATargetRaceDistance(val meters: Double, val label: String) {
    RACE_5K(Engine15M2APolicyRegistry.DISTANCE_5K_METERS, "5K"),
    RACE_10K(Engine15M2APolicyRegistry.DISTANCE_10K_METERS, "10K"),
    HALF_MARATHON(Engine15M2APolicyRegistry.DISTANCE_HALF_MARATHON_METERS, "HM"),
    MARATHON(Engine15M2APolicyRegistry.DISTANCE_MARATHON_METERS, "MARATHON");

    val siMeters: Meters get() = Meters.of(meters)
}

/**
 * Pure deeply immutable M2-A race performance input contract (DEF-01, DEF-08, DEF-13).
 * Explicitly requires M2APerformanceIntent so ordinary runs never convert into maximal benchmarks.
 */
internal class M2ARacePerformanceInput(
    val performanceId: String,
    val accountId: String,
    val discipline: AthleteDiscipline,
    val performedOn: LocalDate?, // Nullable so legacy undated benchmarks fail closed explicitly (DEF-14)
    val distance: Meters,
    val duration: Seconds,
    val authority: EvidenceAuthorityClass,
    val performanceIntent: M2APerformanceIntent,
    val sourceProvider: String,
    val canonicalActivityId: String? = null,
    sourceQualifiedAliases: Set<M2ASourceQualifiedAlias> = emptySet(),
    val corroborationToken: String? = null,
    val sha256: String? = null,
    val fitnessEpochId: String? = null,
    val calibrationEligibility: CalibrationEligibility = CalibrationEligibility.ELIGIBLE_OBSERVED,
    val courseDifficulty: VickersCourseDifficulty = VickersCourseDifficulty.AVERAGE,
    val observedAtUtc: Instant? = null,
    val isProjected: Boolean = false
) {
    val sourceQualifiedAliases: Set<M2ASourceQualifiedAlias>

    init {
        RecordPolicy.identifier(performanceId)
        RecordPolicy.identifier(accountId)
        require(sourceProvider.isNotBlank() && sourceProvider.length <= 128) {
            "sourceProvider must be non-blank and <= 128 chars"
        }
        if (canonicalActivityId != null) RecordPolicy.identifier(canonicalActivityId)
        if (corroborationToken != null) RecordPolicy.identifier(corroborationToken)
        if (fitnessEpochId != null) RecordPolicy.identifier(fitnessEpochId)
        if (sha256 != null) {
            require(sha256.isNotBlank() && sha256.length <= 128) { "Invalid sha256" }
        }
        val baseAlias = M2ASourceQualifiedAlias(sourceProvider, performanceId)
        val merged = (sourceQualifiedAliases + baseAlias).distinct().sorted()
        this.sourceQualifiedAliases = Collections.unmodifiableSet(LinkedHashSet(merged))
    }

    companion object {
        /**
         * Adapter converting an M1B CanonicalActivity into M2ARacePerformanceInput ONLY when
         * explicit maximal performance intent is provided (DEF-13).
         * Keeps authority strictly at SENSOR_SUPPORTED unless official certified verification is declared.
         */
        fun fromVerifiedMaximalCanonicalActivity(
            activity: CanonicalActivity,
            zoneId: ZoneId,
            performanceIntent: M2APerformanceIntent,
            officiallyCertifiedRace: Boolean = false,
            courseDifficulty: VickersCourseDifficulty = VickersCourseDifficulty.AVERAGE,
            fitnessEpochId: String? = null,
            corroborationToken: String? = null
        ): M2ARacePerformanceInput? {
            if (performanceIntent != M2APerformanceIntent.VERIFIED_OFFICIAL_RACE &&
                performanceIntent != M2APerformanceIntent.VERIFIED_TIME_TRIAL
            ) {
                return null
            }
            if (activity.telemetryQuality.distanceQuality != TelemetryQualityStatus.VALID_OBSERVED ||
                activity.telemetryQuality.durationQuality != TelemetryQualityStatus.VALID_OBSERVED
            ) {
                return null
            }
            val dist = activity.normalizedMetrics.distance?.takeIf { it.value > 0.0 } ?: return null
            val dur = activity.normalizedMetrics.duration?.takeIf { it.value > 0.0 } ?: return null

            val aliases = activity.rawComponents.flatMap { comp ->
                buildList {
                    add(M2ASourceQualifiedAlias(comp.provider, comp.recordId))
                    if (comp.externalId != null) add(M2ASourceQualifiedAlias(comp.provider, comp.externalId))
                }
            }.toSet()
            val primarySha = activity.rawComponents.mapNotNull { it.sha256?.lowercase(Locale.ROOT) }.sorted().firstOrNull()
            val auth = if (officiallyCertifiedRace && performanceIntent == M2APerformanceIntent.VERIFIED_OFFICIAL_RACE) {
                EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED
            } else {
                EvidenceAuthorityClass.SENSOR_SUPPORTED
            }
            return M2ARacePerformanceInput(
                performanceId = activity.canonicalId,
                accountId = activity.accountId,
                discipline = activity.discipline,
                performedOn = activity.startedAtUtc.atZone(zoneId).toLocalDate(),
                distance = dist,
                duration = dur,
                authority = auth,
                performanceIntent = performanceIntent,
                sourceProvider = activity.primarySource,
                canonicalActivityId = activity.canonicalId,
                sourceQualifiedAliases = aliases,
                corroborationToken = corroborationToken,
                sha256 = primarySha,
                fitnessEpochId = fitnessEpochId,
                calibrationEligibility = activity.calibrationEligibility,
                courseDifficulty = courseDifficulty,
                observedAtUtc = activity.startedAtUtc,
                isProjected = false
            )
        }
    }
}

/**
 * Deeply immutable selected benchmark contract (DEF-08).
 */
internal class M2ASelectedBenchmark(
    val representativeId: String,
    val performedOn: LocalDate,
    val ageDays: Int,
    val freshness: M2ABenchmarkFreshness,
    val distance: Meters,
    val duration: Seconds,
    val speed: MetersPerSecond,
    val authority: EvidenceAuthorityClass,
    val performanceIntent: M2APerformanceIntent,
    val courseDifficulty: VickersCourseDifficulty,
    val fitnessEpochId: String?,
    mergedEvidenceIds: Set<String>
) {
    val mergedEvidenceIds: Set<String>

    init {
        RecordPolicy.identifier(representativeId)
        require(ageDays >= 0) { "ageDays must be >= 0" }
        require(mergedEvidenceIds.isNotEmpty()) { "mergedEvidenceIds must be non-empty" }
        val sorted = mergedEvidenceIds.map { id ->
            RecordPolicy.identifier(id)
            id
        }.distinct().sorted()
        this.mergedEvidenceIds = Collections.unmodifiableSet(LinkedHashSet(sorted))
    }

    fun canonicalRepresentation(): String = buildString {
        append("id=").append(AthleteStateSnapshot.canonicalSegment(representativeId))
        append("|date=").append(performedOn)
        append("|age=").append(ageDays)
        append("|fresh=").append(freshness.name)
        append("|d=").append(distance.canonicalToken())
        append("|t=").append(duration.canonicalToken())
        append("|auth=").append(authority.name)
        append("|intent=").append(performanceIntent.name)
        append("|course=").append(courseDifficulty.name)
        append("|epoch=").append(fitnessEpochId?.let { AthleteStateSnapshot.canonicalSegment(it) } ?: "0:NONE")
        append("|ev=").append(AthleteStateSnapshot.canonicalList(mergedEvidenceIds))
    }

    override fun equals(other: Any?): Boolean =
        other is M2ASelectedBenchmark &&
            representativeId == other.representativeId &&
            performedOn == other.performedOn &&
            ageDays == other.ageDays &&
            freshness == other.freshness &&
            distance == other.distance &&
            duration == other.duration &&
            speed == other.speed &&
            authority == other.authority &&
            performanceIntent == other.performanceIntent &&
            courseDifficulty == other.courseDifficulty &&
            fitnessEpochId == other.fitnessEpochId &&
            mergedEvidenceIds == other.mergedEvidenceIds

    override fun hashCode(): Int {
        var r = representativeId.hashCode()
        r = 31 * r + performedOn.hashCode()
        r = 31 * r + ageDays
        r = 31 * r + freshness.hashCode()
        r = 31 * r + distance.hashCode()
        r = 31 * r + duration.hashCode()
        r = 31 * r + speed.hashCode()
        r = 31 * r + authority.hashCode()
        r = 31 * r + performanceIntent.hashCode()
        r = 31 * r + courseDifficulty.hashCode()
        r = 31 * r + (fitnessEpochId?.hashCode() ?: 0)
        r = 31 * r + mergedEvidenceIds.hashCode()
        return r
    }
}

/**
 * Deeply immutable race prediction outcome with prediction-specific provenance (DEF-04),
 * exact kinematic invariant (DEF-07), deep immutability (DEF-08), and explicit separation
 * from race readiness & race-pace training authorization (DEF-12).
 */
internal sealed interface M2ARacePredictionOutcome {
    val targetDistance: M2ATargetRaceDistance

    fun canonicalRepresentation(): String

    class Predicted(
        override val targetDistance: M2ATargetRaceDistance,
        val predictedDuration: Seconds,
        val predictedSpeed: MetersPerSecond,
        val modelId: String,
        val benchmarkAuthority: EvidenceAuthorityClass,
        val benchmarkFreshness: M2ABenchmarkFreshness,
        val isProvisional: Boolean,
        val isConditionalOnProvisionalMileage: Boolean,
        val uncertaintyKind: UncertaintyKind,
        val guidanceBandFactor: Double,
        evidenceIds: Set<String>,
        caveats: List<String>,
        downstreamPrerequisitesRequired: Set<String>,
        val certifiesRaceReadiness: Boolean = false,
        val authorizesRaceSpecificTrainingPace: Boolean = false
    ) : M2ARacePredictionOutcome {
        val evidenceIds: Set<String>
        val caveats: List<String>
        val downstreamPrerequisitesRequired: Set<String>

        init {
            RecordPolicy.identifier(modelId)
            val impliedSpeed = targetDistance.meters / predictedDuration.value
            require(abs(impliedSpeed - predictedSpeed.value) <= Engine15M2APolicyRegistry.KINEMATIC_IDENTITY_TOLERANCE_MPS) {
                "DEF-07 kinematic identity violated for ${targetDistance.label}: $impliedSpeed != ${predictedSpeed.value}"
            }
            require(uncertaintyKind != UncertaintyKind.STATISTICAL_INTERVAL_RESERVED) {
                "Tier A prediction cannot claim statistical prediction interval calibration"
            }
            require(guidanceBandFactor.isFinite() && guidanceBandFactor >= 1.0) {
                "guidanceBandFactor must be finite and >= 1.0"
            }
            require(!certifiesRaceReadiness) {
                "DEF-12: M2-A prediction must never certify race readiness or workload feasibility"
            }
            require(!authorizesRaceSpecificTrainingPace) {
                "DEF-12: M2-A prediction alone never authorizes marathon/race-specific training paces"
            }
            val cleanEv = evidenceIds.map { id -> RecordPolicy.identifier(id); id }.distinct().sorted()
            val cleanCav = caveats.map { c -> RecordPolicy.identifier(c); c }.distinct().sorted()
            val cleanPrereq = downstreamPrerequisitesRequired.map { p -> RecordPolicy.identifier(p); p }.distinct().sorted()
            this.evidenceIds = Collections.unmodifiableSet(LinkedHashSet(cleanEv))
            this.caveats = Collections.unmodifiableList(ArrayList(cleanCav))
            this.downstreamPrerequisitesRequired = Collections.unmodifiableSet(LinkedHashSet(cleanPrereq))
        }

        override fun canonicalRepresentation(): String = buildString {
            append("PRED|tgt=").append(targetDistance.name)
            append("|dur=").append(predictedDuration.canonicalToken())
            append("|spd=").append(predictedSpeed.canonicalToken())
            append("|mod=").append(AthleteStateSnapshot.canonicalSegment(modelId))
            append("|auth=").append(benchmarkAuthority.name)
            append("|fresh=").append(benchmarkFreshness.name)
            append("|prov=").append(isProvisional)
            append("|condMi=").append(isConditionalOnProvisionalMileage)
            append("|band=").append(CanonicalSiQuantity.canonicalDecimalString(guidanceBandFactor))
            append("|ready=").append(certifiesRaceReadiness)
            append("|racePaceAuth=").append(authorizesRaceSpecificTrainingPace)
            append("|ev=").append(AthleteStateSnapshot.canonicalList(evidenceIds))
            append("|cav=").append(AthleteStateSnapshot.canonicalList(caveats))
            append("|pre=").append(AthleteStateSnapshot.canonicalList(downstreamPrerequisitesRequired))
        }

        override fun equals(other: Any?): Boolean =
            other is Predicted &&
                targetDistance == other.targetDistance &&
                predictedDuration == other.predictedDuration &&
                predictedSpeed == other.predictedSpeed &&
                modelId == other.modelId &&
                benchmarkAuthority == other.benchmarkAuthority &&
                benchmarkFreshness == other.benchmarkFreshness &&
                isProvisional == other.isProvisional &&
                isConditionalOnProvisionalMileage == other.isConditionalOnProvisionalMileage &&
                uncertaintyKind == other.uncertaintyKind &&
                guidanceBandFactor == other.guidanceBandFactor &&
                evidenceIds == other.evidenceIds &&
                caveats == other.caveats &&
                downstreamPrerequisitesRequired == other.downstreamPrerequisitesRequired &&
                certifiesRaceReadiness == other.certifiesRaceReadiness &&
                authorizesRaceSpecificTrainingPace == other.authorizesRaceSpecificTrainingPace

        override fun hashCode(): Int {
            var r = targetDistance.hashCode()
            r = 31 * r + predictedDuration.hashCode()
            r = 31 * r + predictedSpeed.hashCode()
            r = 31 * r + modelId.hashCode()
            r = 31 * r + benchmarkAuthority.hashCode()
            r = 31 * r + benchmarkFreshness.hashCode()
            r = 31 * r + isProvisional.hashCode()
            r = 31 * r + isConditionalOnProvisionalMileage.hashCode()
            r = 31 * r + uncertaintyKind.hashCode()
            r = 31 * r + guidanceBandFactor.hashCode()
            r = 31 * r + evidenceIds.hashCode()
            r = 31 * r + caveats.hashCode()
            r = 31 * r + downstreamPrerequisitesRequired.hashCode()
            return r
        }
    }

    data class Abstained(
        override val targetDistance: M2ATargetRaceDistance,
        val reasonCode: String,
        val detail: String
    ) : M2ARacePredictionOutcome {
        init { RecordPolicy.identifier(reasonCode) }
        override fun canonicalRepresentation(): String =
            "ABSTAIN|tgt=${targetDistance.name}|reason=${AthleteStateSnapshot.canonicalSegment(reasonCode)}"
    }
}

internal data class M2APerformanceCalibrationRequest(
    val accountId: String,
    val asOfDate: LocalDate,
    val zoneId: ZoneId,
    val observedWorkload: ObservedWorkloadState,
    val racePerformances: List<M2ARacePerformanceInput>,
    val asOfUtc: Instant? = null,
    val athleteStateSnapshot: AthleteStateSnapshot? = null,
    val temporalCompatibilityPolicy: M2ATemporalCompatibilityPolicy =
        M2ATemporalCompatibilityPolicy.ExplicitSharedEpochOrSameDate,
    val allowUnverifiedSecondaryCourseAdjustments: Boolean =
        Engine15M2APolicyRegistry.VV_COURSE_ADJUSTMENTS_PRIMARY_VERIFIED
) {
    init {
        RecordPolicy.identifier(accountId)
        require(observedWorkload.accountId == accountId) {
            "INV-03 violation: observedWorkload.accountId != request.accountId"
        }
        require(athleteStateSnapshot == null || athleteStateSnapshot.accountId == accountId) {
            "INV-03 violation: athleteStateSnapshot.accountId != request.accountId"
        }
    }
}

/**
 * Deeply immutable, deterministic M2-A performance calibration result (DEF-03..DEF-13).
 */
internal class M2APerformanceCalibrationResult(
    val accountId: String,
    val asOfDate: LocalDate,
    val policyVersion: String,
    val authorityMode: M2ACalibrationAuthorityMode,
    val authorization: M2ACalibrationAuthorization,
    val operationalThresholdGuidanceBandFactor: Double?,
    val operationalThresholdBenchmark: M2ASelectedBenchmark?,
    val marathonPrimaryBenchmark: M2ASelectedBenchmark?,
    val marathonSecondaryBenchmark: M2ASelectedBenchmark?,
    val operationalThresholdSpeedBelief: BeliefState<MetersPerSecond>,
    val operationalThresholdReferenceDuration: Seconds,
    val enduranceMileageAssessment: M2AEnduranceMileageAssessment,
    racePredictions: Map<M2ATargetRaceDistance, M2ARacePredictionOutcome>,
    diagnosticFlags: Set<String>,
    contributingEvidenceIds: Set<String>,
    val isMeasuredPhysiologicalThreshold: Boolean = false
) {
    val racePredictions: Map<M2ATargetRaceDistance, M2ARacePredictionOutcome>
    val diagnosticFlags: Set<String>
    val contributingEvidenceIds: Set<String>
    val contentHash: String

    init {
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(policyVersion)
        require(!isMeasuredPhysiologicalThreshold) {
            "IR-18 violation: M2-A operational threshold is never a measured laboratory threshold"
        }
        if (operationalThresholdGuidanceBandFactor != null) {
            require(operationalThresholdGuidanceBandFactor.isFinite() && operationalThresholdGuidanceBandFactor >= 1.0)
        }
        val sortedPreds = TreeMap<M2ATargetRaceDistance, M2ARacePredictionOutcome>()
        for ((k, v) in racePredictions) {
            require(v.targetDistance == k)
            sortedPreds[k] = v
        }
        this.racePredictions = Collections.unmodifiableMap(sortedPreds)
        this.diagnosticFlags = Collections.unmodifiableSet(LinkedHashSet(diagnosticFlags.distinct().sorted()))
        this.contributingEvidenceIds = Collections.unmodifiableSet(LinkedHashSet(contributingEvidenceIds.distinct().sorted()))
        this.contentHash = computeSha256(canonicalPayload())
    }

    internal fun canonicalPayload(): String = buildString {
        append("policy=").append(AthleteStateSnapshot.canonicalSegment(policyVersion))
        append("|acct=").append(AthleteStateSnapshot.canonicalSegment(accountId))
        append("|asOf=").append(asOfDate)
        append("|mode=").append(authorityMode.name)
        append("|auth=").append(AthleteStateSnapshot.canonicalSegment(authorization.canonicalRepresentation()))
        append("|thrBand=").append(operationalThresholdGuidanceBandFactor?.let { CanonicalSiQuantity.canonicalDecimalString(it) } ?: "NONE")
        append("|thrBench=").append(operationalThresholdBenchmark?.canonicalRepresentation()?.let { AthleteStateSnapshot.canonicalSegment(it) } ?: "0:NONE")
        append("|marPrim=").append(marathonPrimaryBenchmark?.canonicalRepresentation()?.let { AthleteStateSnapshot.canonicalSegment(it) } ?: "0:NONE")
        append("|marSec=").append(marathonSecondaryBenchmark?.canonicalRepresentation()?.let { AthleteStateSnapshot.canonicalSegment(it) } ?: "0:NONE")
        append("|thr=").append(AthleteStateSnapshot.canonicalSegment(operationalThresholdSpeedBelief.canonicalRepresentation()))
        append("|thrRef=").append(operationalThresholdReferenceDuration.canonicalToken())
        append("|endur=").append(AthleteStateSnapshot.canonicalSegment(enduranceMileageAssessment.canonicalRepresentation()))
        append("|preds=").append(AthleteStateSnapshot.canonicalList(racePredictions.values.map { it.canonicalRepresentation() }))
        append("|flags=").append(AthleteStateSnapshot.canonicalList(diagnosticFlags))
        append("|ev=").append(AthleteStateSnapshot.canonicalList(contributingEvidenceIds))
    }

    override fun equals(other: Any?): Boolean =
        other is M2APerformanceCalibrationResult && contentHash == other.contentHash

    override fun hashCode(): Int = contentHash.hashCode()

    companion object {
        private fun computeSha256(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

/**
 * Pure, deterministic Tier A M2-A Performance Calibration orchestrator (Corrective Pass R2).
 * Resolves DEF-01 through DEF-14 cleanly with zero Android dependencies and zero unapproved magic constants.
 */
internal object Engine15M2APerformanceCalibrator {

    private data class DedupOutput(
        val benchmarks: List<M2ASelectedBenchmark>,
        val collisionConflictingSpeeds: List<MetersPerSecond>
    )

    private val marathonDownstreamPrereqs: Set<String> = setOf(
        "M4_MARATHON_READINESS_ASSESSMENT_REQUIRED",
        "M2C_LONG_RUN_CAPACITY_VERIFICATION_REQUIRED",
        "M2_MUSCULOSKELETAL_EXPOSURE_CHECK_REQUIRED",
        "M4_GOAL_AND_WORKLOAD_FEASIBILITY_REQUIRED"
    )

    private val shortMidDownstreamPrereqs: Set<String> = setOf(
        "M3_WORKOUT_SPECIFIC_PRESCRIPTION_CHECK_REQUIRED",
        "M4_GOAL_FEASIBILITY_CHECK_REQUIRED"
    )

    fun calibrate(request: M2APerformanceCalibrationRequest): M2APerformanceCalibrationResult {
        val flags = linkedSetOf<String>()
        val refDuration = Seconds.of(Engine15M2APolicyRegistry.POL_020_OPERATIONAL_THRESHOLD_REF_SECONDS)

        // Step 1: Assess endurance mileage strictly from ObservedWorkloadState (DEF-05).
        val endurance = assessEnduranceMileage(request.observedWorkload, flags)

        // Step 2: Strict ingress filtering (DEF-13 maximal intent, INV-02/03/05/06, legacy undated benchmarks).
        val eligibleInputs = filterEligibleInputs(request, flags)

        // Step 3: Canonical/corroborated transitive deduplication + explicit collision detection (DEF-01).
        val dedup = deduplicateByCorroboratedIdentity(eligibleInputs, request.asOfDate, flags)
        val deduplicated = dedup.benchmarks

        if (deduplicated.isEmpty()) {
            if (dedup.collisionConflictingSpeeds.map { it.value }.distinct().size >= 2) {
                return buildFallbackResult(
                    request = request,
                    endurance = endurance,
                    refDuration = refDuration,
                    thresholdBelief = BeliefState.Conflicting(
                        dedup.collisionConflictingSpeeds,
                        "SOURCE_IDENTIFIER_COLLISION_CONTRADICTORY_PERFORMANCE"
                    ),
                    abstainReason = "SOURCE_IDENTIFIER_COLLISION",
                    abstainDetail = "All candidate performances were blocked by contradictory source-identifier collisions.",
                    flags = flags,
                    evidenceIds = emptySet()
                )
            }
            flags += "NO_ELIGIBLE_MAXIMAL_RACE_BENCHMARK"
            return buildFallbackResult(
                request = request,
                endurance = endurance,
                refDuration = refDuration,
                thresholdBelief = BeliefState.Unknown("NO_ELIGIBLE_MAXIMAL_RACE_BENCHMARK"),
                abstainReason = "NO_ELIGIBLE_MAXIMAL_RACE_BENCHMARK",
                abstainDetail = "No eligible maximal race or time-trial benchmark is available; use Effort-First (Talk Test / RPE).",
                flags = flags,
                evidenceIds = emptySet()
            )
        }

        // Step 4: Partition active (<= 180 d) vs stale (> 180 d) per POL-018.
        val activeAll = deduplicated.filter { it.freshness != M2ABenchmarkFreshness.STALE }
        val staleAll = deduplicated.filter { it.freshness == M2ABenchmarkFreshness.STALE }

        if (activeAll.isEmpty()) {
            val latestStale = staleAll.sortedWith(
                compareByDescending<M2ASelectedBenchmark> { it.performedOn }
                    .thenByDescending { authorityRank(it.authority) }
                    .thenBy { it.representativeId }
            ).first()
            flags += "ALL_BENCHMARKS_STALE_OVER_180D"
            val staleThrSpeed = when (val thrRes = Engine15M2AOperationalThresholdModel.estimate(latestStale.distance, latestStale.duration, refDuration)) {
                is M2AModelResult.Computed -> thrRes.value.operationalThresholdSpeed
                else -> latestStale.speed
            }
            return buildFallbackResult(
                request = request,
                endurance = endurance,
                refDuration = refDuration,
                thresholdBelief = BeliefState.Stale(
                    lastValue = staleThrSpeed,
                    ageDays = latestStale.ageDays.coerceAtLeast(1),
                    authority = latestStale.authority
                ),
                abstainReason = "BENCHMARK_STALE_OVER_180_DAYS",
                abstainDetail = "Most recent benchmark is ${latestStale.ageDays} days old (> 180 d POL-018 limit).",
                flags = flags,
                evidenceIds = latestStale.mergedEvidenceIds
            )
        }

        // Step 5: Prefer verified observed pool over self-reported provisional pool (never silently promote self-report).
        val verifiedActive = activeAll.filter { it.authority.isObservedAuthoritative }
        val selfReportActive = activeAll.filter { !it.authority.isObservedAuthoritative }
        val activePool = if (verifiedActive.isNotEmpty()) {
            if (selfReportActive.isNotEmpty()) flags += "VERIFIED_BENCHMARK_SUPERSEDED_SELF_REPORT"
            verifiedActive
        } else {
            flags += "PROVISIONAL_SELF_REPORT_BENCHMARK_ONLY"
            selfReportActive
        }

        // Step 6: Detect ONLY genuine mathematical contradictions (velocity inversion within temporally compatible pair, DEF-02 & DEF-11).
        // Legitimate endurance drop-offs (k_between > 1.20) or temporally separated improvements are NOT global conflicts!
        val contradiction = detectCompatibleVelocityInversion(activePool, request.temporalCompatibilityPolicy, refDuration)
        if (contradiction != null) {
            flags += "TEMPORALLY_COMPATIBLE_VELOCITY_INVERSION_CONFLICT"
            val allEv = activePool.flatMap { it.mergedEvidenceIds }.toSet()
            return buildFallbackResult(
                request = request,
                endurance = endurance,
                refDuration = refDuration,
                thresholdBelief = contradiction,
                abstainReason = "CONFLICTING_RACE_BENCHMARKS",
                abstainDetail = "Temporally compatible races exhibit a velocity inversion (longer distance faster or equal speed to shorter distance).",
                flags = flags,
                evidenceIds = allEv
            )
        }

        // Step 7: Model-specific anchor selection for MOD-06 Operational Threshold (DEF-06).
        // Filters strictly to POL-020 [720, 7200] s BEFORE ranking so an ineligible 10-min 3K never suppresses a valid 10K!
        val thrAnchor = activePool
            .filter {
                it.duration.value in
                    Engine15M2APolicyRegistry.POL_020_MIN_BENCHMARK_DURATION_SECONDS..Engine15M2APolicyRegistry.POL_020_MAX_BENCHMARK_DURATION_SECONDS
            }
            .sortedWith(
                compareBy<M2ASelectedBenchmark> { it.freshness.ordinal }
                    .thenByDescending { it.performedOn }
                    .thenBy { abs(it.duration.value - refDuration.value) }
                    .thenByDescending { it.distance.value }
                    .thenBy { it.representativeId }
            )
            .firstOrNull()

        val thrBandFactor = thrAnchor?.let { guidanceFactorFor(it) }
        val thrBelief: BeliefState<MetersPerSecond> = if (thrAnchor != null) {
            when (val r = Engine15M2AOperationalThresholdModel.estimate(thrAnchor.distance, thrAnchor.duration, refDuration)) {
                is M2AModelResult.Computed -> BeliefState.Estimated(
                    estimate = r.value.operationalThresholdSpeed,
                    authority = thrAnchor.authority,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND,
                    guidanceSpreadFactor = thrBandFactor
                )
                is M2AModelResult.Abstained -> {
                    flags += "OPERATIONAL_THRESHOLD_ABSTAINED_${r.reasonCode}"
                    BeliefState.Unknown(r.reasonCode)
                }
                is M2AModelResult.InvalidInput -> {
                    flags += "OPERATIONAL_THRESHOLD_INVALID_${r.reasonCode}"
                    BeliefState.Unknown(r.reasonCode)
                }
            }
        } else {
            flags += "NO_BENCHMARK_IN_POL020_DURATION_DOMAIN"
            BeliefState.Unknown("NO_BENCHMARK_IN_POL020_DURATION_DOMAIN")
        }

        // Step 8: Model-specific predictions for 5K, 10K, HM (DEF-04, DEF-06, DEF-07).
        val predictions = TreeMap<M2ATargetRaceDistance, M2ARacePredictionOutcome>()
        for (target in listOf(
            M2ATargetRaceDistance.RACE_5K,
            M2ATargetRaceDistance.RACE_10K,
            M2ATargetRaceDistance.HALF_MARATHON
        )) {
            predictions[target] = predictShortOrMidDistance(target, activePool)
        }

        // Step 9: Model-specific Marathon prediction (MOD-05 compatible pair -> MOD-04 longest eligible -> Abstained) (DEF-02,04,05,06,11,12).
        val (marOutcome, marPrim, marSec) = predictMarathon(
            activePool = activePool,
            endurance = endurance,
            temporalPolicy = request.temporalCompatibilityPolicy,
            allowUnverifiedCourseAdj = request.allowUnverifiedSecondaryCourseAdjustments,
            flags = flags
        )
        predictions[M2ATargetRaceDistance.MARATHON] = marOutcome

        // Step 10: Assemble separated authority & authorization contract (DEF-03).
        val authorityMode = when {
            thrAnchor != null && thrBelief is BeliefState.Estimated -> when {
                thrAnchor.authority.isObservedAuthoritative && thrAnchor.freshness == M2ABenchmarkFreshness.FRESH ->
                    M2ACalibrationAuthorityMode.VERIFIED_RECENT_BENCHMARK
                thrAnchor.authority.isObservedAuthoritative && thrAnchor.freshness == M2ABenchmarkFreshness.AGING ->
                    M2ACalibrationAuthorityMode.VERIFIED_AGING_BENCHMARK
                else -> M2ACalibrationAuthorityMode.PROVISIONAL_SELF_REPORT_BENCHMARK
            }
            else -> M2ACalibrationAuthorityMode.EFFORT_AND_TALK_TEST_FALLBACK
        }
        val authorization = M2ACalibrationAuthorization(
            hasVerifiedPerformanceReference = verifiedActive.isNotEmpty(),
            eligibleToParticipateInPaceCalibration = thrBelief.canParticipateInZoneAuthorization,
            provisionalPaceGuidanceAvailable = thrBelief is BeliefState.Estimated,
            workoutSpecificPacePrescriptionAuthorized = false,
            hrTrainingZoneAuthorized = false
        )

        val evidenceIds = linkedSetOf<String>()
        if (thrAnchor != null) evidenceIds += thrAnchor.mergedEvidenceIds
        if (marPrim != null) evidenceIds += marPrim.mergedEvidenceIds
        if (marSec != null) evidenceIds += marSec.mergedEvidenceIds
        for (pred in predictions.values) {
            if (pred is M2ARacePredictionOutcome.Predicted) evidenceIds += pred.evidenceIds
        }

        return M2APerformanceCalibrationResult(
            accountId = request.accountId,
            asOfDate = request.asOfDate,
            policyVersion = Engine15M2APolicyRegistry.POLICY_VERSION,
            authorityMode = authorityMode,
            authorization = authorization,
            operationalThresholdGuidanceBandFactor = if (thrBelief is BeliefState.Estimated) thrBandFactor else null,
            operationalThresholdBenchmark = thrAnchor,
            marathonPrimaryBenchmark = marPrim,
            marathonSecondaryBenchmark = marSec,
            operationalThresholdSpeedBelief = thrBelief,
            operationalThresholdReferenceDuration = refDuration,
            enduranceMileageAssessment = endurance,
            racePredictions = predictions,
            diagnosticFlags = flags,
            contributingEvidenceIds = evidenceIds,
            isMeasuredPhysiologicalThreshold = false
        )
    }

    private fun filterEligibleInputs(
        request: M2APerformanceCalibrationRequest,
        flags: MutableSet<String>
    ): List<M2ARacePerformanceInput> {
        val eligible = mutableListOf<M2ARacePerformanceInput>()
        for (input in request.racePerformances) {
            if (input.accountId != request.accountId) {
                flags += "FOREIGN_ACCOUNT_PERFORMANCE_EXCLUDED"
                continue
            }
            if (input.discipline != AthleteDiscipline.RUN) {
                flags += "NON_RUNNING_PERFORMANCE_EXCLUDED"
                continue
            }
            if (input.isProjected || input.authority == EvidenceAuthorityClass.PROJECTED_NON_AUTHORITATIVE) {
                flags += "PROJECTED_PERFORMANCE_EXCLUDED"
                continue
            }
            if (input.authority == EvidenceAuthorityClass.UNOBSERVED) {
                flags += "UNOBSERVED_PERFORMANCE_EXCLUDED"
                continue
            }
            if (!input.performanceIntent.isEligibleMaximalPerformance) {
                flags += "NON_MAXIMAL_TRAINING_ACTIVITY_EXCLUDED"
                continue
            }
            if (input.performedOn == null) {
                flags += "UNDATED_LEGACY_BENCHMARK_EXCLUDED"
                continue
            }
            if (input.performedOn.isAfter(request.asOfDate) ||
                (request.asOfUtc != null && input.observedAtUtc != null && input.observedAtUtc.isAfter(request.asOfUtc))
            ) {
                flags += "FUTURE_DATED_PERFORMANCE_EXCLUDED"
                continue
            }
            if (input.authority.isObservedAuthoritative &&
                input.calibrationEligibility != CalibrationEligibility.ELIGIBLE_OBSERVED &&
                input.calibrationEligibility != CalibrationEligibility.ELIGIBLE_NON_HR_ONLY
            ) {
                flags += "INELIGIBLE_QUALITY_PERFORMANCE_EXCLUDED"
                continue
            }
            if (input.distance.value <= 0.0 || input.duration.value <= 0.0) {
                flags += "NON_POSITIVE_PERFORMANCE_EXCLUDED"
                continue
            }
            val rawSpeed = input.distance.value / input.duration.value
            if (!rawSpeed.isFinite() ||
                rawSpeed < Engine15M2APolicyRegistry.MIN_PLAUSIBLE_RUNNING_SPEED_MPS ||
                rawSpeed > Engine15M2APolicyRegistry.MAX_PLAUSIBLE_RUNNING_SPEED_MPS
            ) {
                flags += "IMPLAUSIBLE_SPEED_PERFORMANCE_EXCLUDED"
                continue
            }
            eligible += input
        }
        return eligible
    }

    /**
     * Canonical & corroborated transitive deduplication (DEF-01).
     * NEVER collapses records purely on same date + similar distance/duration!
     * Detects and quarantines contradictory source-identifier collisions explicitly.
     */
    private fun deduplicateByCorroboratedIdentity(
        inputs: List<M2ARacePerformanceInput>,
        asOfDate: LocalDate,
        flags: MutableSet<String>
    ): DedupOutput {
        if (inputs.isEmpty()) return DedupOutput(emptyList(), emptyList())

        val ordered = inputs.sortedWith(
            compareByDescending<M2ARacePerformanceInput> { it.performedOn!! }
                .thenByDescending { authorityRank(it.authority) }
                .thenBy { RawSourceComponent.providerPriority(it.sourceProvider) }
                .thenBy { it.performanceId }
        )

        val n = ordered.size
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var cur = x
            while (parent[cur] != cur) {
                parent[cur] = parent[parent[cur]]
                cur = parent[cur]
            }
            return cur
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        val collidedIndices = mutableSetOf<Int>()
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val a = ordered[i]
                val b = ordered[j]
                if (!sharesCorroboratedIdentity(a, b)) continue

                if (isConsistentIdentityMatch(a, b)) {
                    union(i, j)
                } else {
                    // Explicit source-identifier collision: same canonical/provider ID or SHA on contradictory performances!
                    collidedIndices += i
                    collidedIndices += j
                    flags += "SOURCE_IDENTIFIER_COLLISION_EXCLUDED"
                }
            }
        }

        // Any group transitively touching a collided record is quarantined from calibration.
        val collidedRoots = collidedIndices.map { find(it) }.toSet()
        val collisionSpeeds = mutableListOf<MetersPerSecond>()
        val groups = linkedMapOf<Int, MutableList<M2ARacePerformanceInput>>()
        for (i in 0 until n) {
            val root = find(i)
            if (root in collidedRoots) {
                val item = ordered[i]
                collisionSpeeds += MetersPerSecond.of(item.distance.value / item.duration.value)
            } else {
                groups.getOrPut(root) { mutableListOf() }.add(ordered[i])
            }
        }

        val benchmarks = groups.values.map { group ->
            if (group.size > 1) flags += "DUPLICATE_RACE_PERFORMANCE_DEDUPLICATED"
            val primary = group.sortedWith(
                compareByDescending<M2ARacePerformanceInput> { authorityRank(it.authority) }
                    .thenBy { RawSourceComponent.providerPriority(it.sourceProvider) }
                    .thenBy { it.performanceId }
            ).first()
            val performed = primary.performedOn!!
            val ageDays = ChronoUnit.DAYS.between(performed, asOfDate).toInt().coerceAtLeast(0)
            val freshness = when {
                ageDays <= Engine15M2APolicyRegistry.POL_018_BENCHMARK_FRESH_MAX_DAYS -> M2ABenchmarkFreshness.FRESH
                ageDays <= Engine15M2APolicyRegistry.POL_018_BENCHMARK_AGING_MAX_DAYS -> M2ABenchmarkFreshness.AGING
                else -> M2ABenchmarkFreshness.STALE
            }
            val mergedIds = group.flatMap { item ->
                buildList {
                    add(item.performanceId)
                    if (item.canonicalActivityId != null) add(item.canonicalActivityId)
                    addAll(item.sourceQualifiedAliases.map { it.externalId })
                }
            }.toSet()
            val epoch = group.mapNotNull { it.fitnessEpochId }.distinct().sorted().firstOrNull()
            M2ASelectedBenchmark(
                representativeId = primary.canonicalActivityId ?: primary.performanceId,
                performedOn = performed,
                ageDays = ageDays,
                freshness = freshness,
                distance = primary.distance,
                duration = primary.duration,
                speed = MetersPerSecond.of(primary.distance.value / primary.duration.value),
                authority = primary.authority,
                performanceIntent = primary.performanceIntent,
                courseDifficulty = primary.courseDifficulty,
                fitnessEpochId = epoch,
                mergedEvidenceIds = mergedIds
            )
        }
        return DedupOutput(benchmarks, collisionSpeeds)
    }

    private fun sharesCorroboratedIdentity(a: M2ARacePerformanceInput, b: M2ARacePerformanceInput): Boolean {
        // Distinct non-null M1B canonicalActivityIds are already proven distinct unless corroborated via sha/token/alias!
        if (a.canonicalActivityId != null && b.canonicalActivityId != null &&
            a.canonicalActivityId == b.canonicalActivityId
        ) return true
        if (a.sha256 != null && b.sha256 != null &&
            a.sha256.equals(b.sha256, ignoreCase = true)
        ) return true
        if (a.corroborationToken != null && b.corroborationToken != null &&
            a.corroborationToken == b.corroborationToken
        ) return true
        if (a.sourceQualifiedAliases.any { it in b.sourceQualifiedAliases }) return true
        return false
    }

    private fun isConsistentIdentityMatch(a: M2ARacePerformanceInput, b: M2ARacePerformanceInput): Boolean {
        if (a.performedOn != b.performedOn) return false
        val distDiff = abs(a.distance.value - b.distance.value) / max(a.distance.value, b.distance.value)
        val durDiff = abs(a.duration.value - b.duration.value) / max(a.duration.value, b.duration.value)
        return distDiff <= Engine15M1PolicyRegistry.POL_002_MAX_DISTANCE_DIFF_RATIO &&
            durDiff <= Engine15M1PolicyRegistry.POL_002_MAX_DURATION_DIFF_RATIO
    }

    /**
     * Detects ONLY genuine velocity inversions (D2 > D1 with v2 >= v1, i.e., k_between <= 1.00)
     * across temporally compatible pairs (DEF-02 & DEF-11).
     * Never misclassifies historical improvement or legitimate endurance drop-off (k_between > 1.20) as conflict!
     */
    private fun detectCompatibleVelocityInversion(
        activePool: List<M2ASelectedBenchmark>,
        temporalPolicy: M2ATemporalCompatibilityPolicy,
        refDuration: Seconds
    ): BeliefState.Conflicting<MetersPerSecond>? {
        if (activePool.size < 2) return null
        val sorted = activePool.sortedWith(
            compareBy<M2ASelectedBenchmark> { it.distance.value }
                .thenBy { it.performedOn }
                .thenBy { it.representativeId }
        )
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val shorter = sorted[i]
                val longer = sorted[j]
                if (!temporalPolicy.areCompatible(shorter, longer)) continue
                if (longer.distance.value <= shorter.distance.value + Engine15M2APolicyRegistry.DIRECT_ANCHOR_EXACT_DISTANCE_TOLERANCE_METERS) {
                    continue
                }
                if (longer.speed.value >= shorter.speed.value) {
                    val sA = impliedThresholdOrRawSpeed(shorter, refDuration)
                    val sB = impliedThresholdOrRawSpeed(longer, refDuration)
                    val cands = if (sA.value != sB.value) listOf(sA, sB) else listOf(shorter.speed, longer.speed)
                    if (cands.map { it.value }.distinct().size >= 2) {
                        return BeliefState.Conflicting(
                            candidates = cands,
                            conflictReason = "TEMPORALLY_COMPATIBLE_VELOCITY_INVERSION"
                        )
                    }
                }
            }
        }
        return null
    }

    private fun impliedThresholdOrRawSpeed(b: M2ASelectedBenchmark, refDuration: Seconds): MetersPerSecond =
        when (val r = Engine15M2AOperationalThresholdModel.estimate(b.distance, b.duration, refDuration)) {
            is M2AModelResult.Computed -> r.value.operationalThresholdSpeed
            else -> b.speed
        }

    /**
     * Strictly separates all 6 endurance mileage semantics (DEF-05).
     */
    private fun assessEnduranceMileage(
        observed: ObservedWorkloadState,
        flags: MutableSet<String>
    ): M2AEnduranceMileageAssessment {
        val run = observed.disciplines[AthleteDiscipline.RUN]
        val sr = observed.provisionalSelfReport[AthleteDiscipline.RUN]

        if (run == null) {
            return when {
                sr?.confirmedZero == true -> {
                    flags += "ENDURANCE_CONFIRMED_ZERO"
                    M2AEnduranceMileageAssessment.ConfirmedZero
                }
                sr?.weeklyVolume != null && sr.weeklyVolume.value > 0.0 -> {
                    flags += "ENDURANCE_FROM_PROVISIONAL_SELF_REPORT"
                    M2AEnduranceMileageAssessment.ProvisionalSelfReport(sr.weeklyVolume)
                }
                else -> {
                    flags += "ENDURANCE_MISSING_EVIDENCE"
                    M2AEnduranceMileageAssessment.MissingEvidence("NO_RUN_WORKLOAD_OR_SELF_REPORT")
                }
            }
        }

        if (run.coverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
            flags += "ENDURANCE_CONFIRMED_ZERO"
            return M2AEnduranceMileageAssessment.ConfirmedZero
        }

        if (run.coverage == HistoryCoverageStatus.CONTINUOUS_COVERAGE &&
            run.rolling28dWeeklyAverageBelief is BeliefState.Estimated
        ) {
            val recM = run.rolling28dWeeklyAverageBelief.estimate.value
            val srM = sr?.weeklyVolume?.value
            if (srM != null && srM > 0.0) {
                val ratio = recM / srM
                if (run.selfReportConflictFlag ||
                    ratio < Engine15M2APolicyRegistry.POL_013_CONFLICT_LOW_RATIO ||
                    ratio > Engine15M2APolicyRegistry.POL_013_CONFLICT_HIGH_RATIO
                ) {
                    flags += "WEEKLY_MILEAGE_CONFLICT_DETECTED"
                    if (recM < srM) {
                        val cap = min(
                            Engine15M2APolicyRegistry.POL_013_CONSERVATIVE_SELF_REPORT_FACTOR * srM,
                            max(
                                Engine15M2APolicyRegistry.POL_013_CONSERVATIVE_RECORDED_FACTOR * recM,
                                recM + Engine15M2APolicyRegistry.POL_013_CONSERVATIVE_RECORDED_BONUS_METERS
                            )
                        )
                        return M2AEnduranceMileageAssessment.ConflictConservativeCeiling(
                            conservativePlanningCeilingMeters = Meters.of(cap),
                            observedContinuousWeeklyMeters = Meters.of(recM),
                            selfReportedWeeklyMeters = Meters.of(srM)
                        )
                    }
                    // When continuous observed > 1.5x self-report, continuous observed is verified typical mileage.
                    return M2AEnduranceMileageAssessment.VerifiedContinuousObserved(Meters.of(recM))
                }
            }
            return M2AEnduranceMileageAssessment.VerifiedContinuousObserved(Meters.of(recM))
        }

        // Partial / Provisional / Missing coverage:
        val observedLowerM = when (val b = run.rolling28dWeeklyAverageBelief) {
            is BeliefState.Bounded -> b.lower?.value
            is BeliefState.Estimated -> b.estimate.value
            else -> null
        }
        val srM = sr?.weeklyVolume?.value?.takeIf { it > 0.0 }
        return when {
            observedLowerM != null && observedLowerM > 0.0 && (srM == null || observedLowerM > srM) -> {
                if (srM != null) flags += "OBSERVED_PARTIAL_LOWER_BOUND_EXCEEDED_SELF_REPORT"
                flags += "ENDURANCE_PARTIAL_OBSERVED_LOWER_BOUND_ONLY"
                M2AEnduranceMileageAssessment.PartialObservedLowerBound(
                    observed28dLowerBoundWeeklyMeters = Meters.of(observedLowerM),
                    provisionalSelfReportWeeklyMeters = srM?.let(Meters::of)
                )
            }
            srM != null -> {
                flags += "ENDURANCE_FROM_PROVISIONAL_SELF_REPORT"
                M2AEnduranceMileageAssessment.ProvisionalSelfReport(Meters.of(srM))
            }
            else -> {
                flags += "ENDURANCE_MISSING_EVIDENCE"
                M2AEnduranceMileageAssessment.MissingEvidence("PARTIAL_OR_MISSING_COVERAGE_WITHOUT_TYPICAL_ESTIMATE")
            }
        }
    }

    /**
     * Model-specific short/mid-distance prediction (DEF-04, DEF-06, DEF-07, DEF-12).
     * Exact DIRECT_ANCHOR triggers strictly within 1.0 m; near-distances like 4,950 m normalize via MOD-03.
     */
    private fun predictShortOrMidDistance(
        target: M2ATargetRaceDistance,
        activePool: List<M2ASelectedBenchmark>
    ): M2ARacePredictionOutcome {
        // Exact certified match (<= 1.0 m, DEF-07): prefer FRESH over AGING, then most recent.
        val exactMatch = activePool
            .filter { abs(it.distance.value - target.meters) <= Engine15M2APolicyRegistry.DIRECT_ANCHOR_EXACT_DISTANCE_TOLERANCE_METERS }
            .sortedWith(
                compareBy<M2ASelectedBenchmark> { it.freshness.ordinal }
                    .thenByDescending { it.performedOn }
                    .thenBy { it.representativeId }
            )
            .firstOrNull()

        if (exactMatch != null) {
            // Recompute exact speed against target.meters if <= 1.0 m sub-meter rounding exists so DEF-07 identity holds exactly.
            val exactSpeed = MetersPerSecond.of(target.meters / exactMatch.duration.value)
            val band = guidanceFactorFor(exactMatch)
            return M2ARacePredictionOutcome.Predicted(
                targetDistance = target,
                predictedDuration = exactMatch.duration,
                predictedSpeed = exactSpeed,
                modelId = "DIRECT_ANCHOR",
                benchmarkAuthority = exactMatch.authority,
                benchmarkFreshness = exactMatch.freshness,
                isProvisional = !exactMatch.authority.isObservedAuthoritative || exactMatch.freshness == M2ABenchmarkFreshness.AGING,
                isConditionalOnProvisionalMileage = false,
                uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND,
                guidanceBandFactor = band,
                evidenceIds = exactMatch.mergedEvidenceIds,
                caveats = listOf("DIRECT_EXACT_DISTANCE_BENCHMARK"),
                downstreamPrerequisitesRequired = shortMidDownstreamPrereqs
            )
        }

        // Model-specific MOD-03 filter FIRST (DEF-06): only eligible [3,000, 21,097.5] m with ratio <= 4.25.
        val mod03Eligible = activePool.filter { b ->
            val ratio = max(target.meters / b.distance.value, b.distance.value / target.meters)
            b.distance.value in Engine15M2APolicyRegistry.POL_017_MIN_DISTANCE_METERS..Engine15M2APolicyRegistry.POL_017_MAX_DISTANCE_METERS &&
                ratio <= Engine15M2APolicyRegistry.POL_017_MAX_DISTANCE_RATIO
        }
        val bestSource = mod03Eligible.sortedWith(
            compareBy<M2ASelectedBenchmark> { it.freshness.ordinal }
                .thenBy { abs(ln(target.meters / it.distance.value)) }
                .thenByDescending { it.performedOn }
                .thenBy { it.representativeId }
        ).firstOrNull()
            ?: return M2ARacePredictionOutcome.Abstained(
                target,
                "NO_MOD03_ELIGIBLE_BENCHMARK",
                "No active benchmark meets MOD-03 distance [3000, 21097.5] m and ratio <= 4.25 bounds for ${target.label}."
            )

        return when (val riegel = Engine15M2ARiegelModel.predictAdjacent(bestSource.distance, bestSource.duration, target.siMeters)) {
            is M2AModelResult.Computed -> {
                val band = guidanceFactorFor(bestSource)
                val caveats = buildList {
                    add("RIEGEL_1_07_ADJACENT_ESTIMATE_NOT_GUARANTEED_OUTCOME")
                    if (abs(bestSource.distance.value - target.meters) / target.meters <= 0.05) {
                        add("NEAR_STANDARD_DISTANCE_NORMALIZED_VIA_MOD03")
                    }
                }
                M2ARacePredictionOutcome.Predicted(
                    targetDistance = target,
                    predictedDuration = riegel.value.predictedDuration,
                    predictedSpeed = riegel.value.predictedSpeed,
                    modelId = riegel.modelId,
                    benchmarkAuthority = bestSource.authority,
                    benchmarkFreshness = bestSource.freshness,
                    isProvisional = !bestSource.authority.isObservedAuthoritative || bestSource.freshness == M2ABenchmarkFreshness.AGING,
                    isConditionalOnProvisionalMileage = false,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND,
                    guidanceBandFactor = band,
                    evidenceIds = bestSource.mergedEvidenceIds,
                    caveats = caveats,
                    downstreamPrerequisitesRequired = shortMidDownstreamPrereqs
                )
            }
            is M2AModelResult.Abstained -> M2ARacePredictionOutcome.Abstained(target, riegel.reasonCode, riegel.detail)
            is M2AModelResult.InvalidInput -> M2ARacePredictionOutcome.Abstained(target, riegel.reasonCode, riegel.detail)
        }
    }

    /**
     * Model-specific Marathon prediction (DEF-02, DEF-04, DEF-05, DEF-06, DEF-11, DEF-12).
     * Never passes partial lower bounds or conflict conservative ceilings into V&V regression as typical mileage!
     */
    private fun predictMarathon(
        activePool: List<M2ASelectedBenchmark>,
        endurance: M2AEnduranceMileageAssessment,
        temporalPolicy: M2ATemporalCompatibilityPolicy,
        allowUnverifiedCourseAdj: Boolean,
        flags: MutableSet<String>
    ): Triple<M2ARacePredictionOutcome, M2ASelectedBenchmark?, M2ASelectedBenchmark?> {
        val target = M2ATargetRaceDistance.MARATHON

        // Exact direct recent marathon benchmark (<= 1.0 m) bypasses V&V regression.
        val directMar = activePool
            .filter { abs(it.distance.value - target.meters) <= Engine15M2APolicyRegistry.DIRECT_ANCHOR_EXACT_DISTANCE_TOLERANCE_METERS }
            .sortedWith(
                compareBy<M2ASelectedBenchmark> { it.freshness.ordinal }
                    .thenByDescending { it.performedOn }
                    .thenBy { it.representativeId }
            )
            .firstOrNull()
        if (directMar != null) {
            val exactSpeed = MetersPerSecond.of(target.meters / directMar.duration.value)
            val pred = M2ARacePredictionOutcome.Predicted(
                targetDistance = target,
                predictedDuration = directMar.duration,
                predictedSpeed = exactSpeed,
                modelId = "DIRECT_ANCHOR",
                benchmarkAuthority = directMar.authority,
                benchmarkFreshness = directMar.freshness,
                isProvisional = !directMar.authority.isObservedAuthoritative || directMar.freshness == M2ABenchmarkFreshness.AGING,
                isConditionalOnProvisionalMileage = false,
                uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND,
                guidanceBandFactor = guidanceFactorFor(directMar),
                evidenceIds = directMar.mergedEvidenceIds,
                caveats = listOf("DIRECT_RECENT_MARATHON_BENCHMARK"),
                downstreamPrerequisitesRequired = marathonDownstreamPrereqs
            )
            return Triple(pred, directMar, null)
        }

        // DEF-05: Only VerifiedContinuousObserved and ProvisionalSelfReport are valid typical weekly mileage inputs!
        val (typicalWeeklyM, isCondProvMileage) = when (endurance) {
            is M2AEnduranceMileageAssessment.VerifiedContinuousObserved ->
                endurance.typicalWeeklyMeters to false
            is M2AEnduranceMileageAssessment.ProvisionalSelfReport ->
                endurance.typicalWeeklyMeters to true
            is M2AEnduranceMileageAssessment.PartialObservedLowerBound ->
                return Triple(
                    M2ARacePredictionOutcome.Abstained(
                        target,
                        "PARTIAL_OBSERVED_LOWER_BOUND_NOT_TYPICAL_MILEAGE",
                        "Partial observed lower bound (${endurance.observed28dLowerBoundWeeklyMeters}) is not a typical weekly mileage point estimate for Vickers & Vertosick."
                    ),
                    null, null
                )
            is M2AEnduranceMileageAssessment.ConflictConservativeCeiling ->
                return Triple(
                    M2ARacePredictionOutcome.Abstained(
                        target,
                        "WEEKLY_MILEAGE_CONFLICT_BLOCKS_MARATHON_PREDICTION",
                        "Conservative POL-013 planning ceiling (${endurance.conservativePlanningCeilingMeters}) is not a verified typical mileage estimate; resolve mileage conflict first."
                    ),
                    null, null
                )
            is M2AEnduranceMileageAssessment.ConfirmedZero ->
                return Triple(
                    M2ARacePredictionOutcome.Abstained(
                        target,
                        "WEEKLY_MILEAGE_CONFIRMED_ZERO",
                        "Confirmed-zero weekly running mileage is outside POL-015 [15, 75] mi/wk marathon domain."
                    ),
                    null, null
                )
            is M2AEnduranceMileageAssessment.MissingEvidence ->
                return Triple(
                    M2ARacePredictionOutcome.Abstained(
                        target,
                        "INSUFFICIENT_ENDURANCE_EVIDENCE",
                        "Marathon prediction requires verified continuous or provisional self-reported typical weekly mileage."
                    ),
                    null, null
                )
        }

        // Filter benchmarks strictly in [5,000, 21,097.5] m for MOD-04 / MOD-05 (DEF-06).
        val vvEligible = activePool
            .filter {
                it.distance.value in
                    Engine15M2APolicyRegistry.POL_015_VV1_MIN_DISTANCE_METERS..Engine15M2APolicyRegistry.POL_015_VV1_MAX_DISTANCE_METERS
            }
            .sortedWith(
                compareBy<M2ASelectedBenchmark> { it.freshness.ordinal }
                    .thenByDescending { it.distance.value }
                    .thenByDescending { it.performedOn }
                    .thenBy { it.representativeId }
            )

        if (vvEligible.isEmpty()) {
            return Triple(
                M2ARacePredictionOutcome.Abstained(
                    target,
                    "OUT_OF_VV1_DISTANCE_DOMAIN",
                    "No active non-marathon race lies in [5,000, 21,097.5] m for MOD-04/MOD-05."
                ),
                null, null
            )
        }

        // Try temporally compatible MOD-05 pair first (DEF-02, DEF-04, DEF-11).
        var sawIncompatiblePair = false
        var sawEnduranceDropAbstain = false
        val pairCandidates = mutableListOf<Pair<M2ASelectedBenchmark, M2ASelectedBenchmark>>()
        for (i in vvEligible.indices) {
            for (j in i + 1 until vvEligible.size) {
                val a = vvEligible[i]
                val b = vvEligible[j]
                val shorter = if (a.distance.value <= b.distance.value) a else b
                val longer = if (a.distance.value <= b.distance.value) b else a
                if (longer.distance.value / shorter.distance.value < Engine15M2APolicyRegistry.POL_016_VV2_MIN_DISTANCE_RATIO) continue
                if (!temporalPolicy.areCompatible(shorter, longer)) {
                    sawIncompatiblePair = true
                    continue
                }
                pairCandidates += shorter to longer
            }
        }
        if (sawIncompatiblePair && pairCandidates.isEmpty()) {
            flags += "MOD05_SKIPPED_TEMPORALLY_INCOMPATIBLE_PAIR"
        }

        val orderedPairs = pairCandidates.sortedWith(
            compareBy<Pair<M2ASelectedBenchmark, M2ASelectedBenchmark>> { max(it.first.freshness.ordinal, it.second.freshness.ordinal) }
                .thenByDescending { it.second.distance.value }
                .thenByDescending { maxOf(it.first.performedOn, it.second.performedOn) }
                .thenBy { it.first.representativeId }
                .thenBy { it.second.representativeId }
        )

        for ((shorter, longer) in orderedPairs) {
            when (val vv2 = Engine15M2AVickersModel2.predictMarathon(
                shorterDistance = shorter.distance,
                shorterDuration = shorter.duration,
                longerDistance = longer.distance,
                longerDuration = longer.duration,
                weeklyVolumePerWeek = typicalWeeklyM,
                shorterCourseDifficulty = shorter.courseDifficulty,
                longerCourseDifficulty = longer.courseDifficulty,
                allowUnverifiedSecondaryCourseAdjustments = allowUnverifiedCourseAdj
            )) {
                is M2AModelResult.Computed -> {
                    // DEF-04: Preserve real benchmark authority & take WORST freshness/guidance band across contributing races!
                    val combinedAuth = lowerAuthority(shorter.authority, longer.authority)
                    val combinedFreshness = worstFreshness(shorter.freshness, longer.freshness)
                    val combinedBand = max(guidanceFactorFor(shorter), guidanceFactorFor(longer))
                    val isProv = !combinedAuth.isObservedAuthoritative ||
                        combinedFreshness == M2ABenchmarkFreshness.AGING ||
                        isCondProvMileage
                    val caveats = buildList {
                        addAll(vv2.notes)
                        if (isCondProvMileage) add("CONDITIONAL_ON_PROVISIONAL_SELF_REPORT_MILEAGE")
                        if (combinedFreshness == M2ABenchmarkFreshness.AGING) add("INCLUDES_AGING_BENCHMARK_91_TO_180D")
                    }
                    val pred = M2ARacePredictionOutcome.Predicted(
                        targetDistance = target,
                        predictedDuration = vv2.value.predictedMarathonDuration,
                        predictedSpeed = vv2.value.predictedMarathonSpeed,
                        modelId = vv2.modelId,
                        benchmarkAuthority = combinedAuth,
                        benchmarkFreshness = combinedFreshness,
                        isProvisional = isProv,
                        isConditionalOnProvisionalMileage = isCondProvMileage,
                        uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND,
                        guidanceBandFactor = combinedBand,
                        evidenceIds = shorter.mergedEvidenceIds + longer.mergedEvidenceIds,
                        caveats = caveats,
                        downstreamPrerequisitesRequired = marathonDownstreamPrereqs
                    )
                    return Triple(pred, longer, shorter)
                }
                is M2AModelResult.Abstained -> {
                    if (vv2.reasonCode == "K_BETWEEN_OUT_OF_POL016_RANGE") {
                        sawEnduranceDropAbstain = true
                        flags += "MOD05_ABSTAINED_ENDURANCE_PROFILE_OUTSIDE_POL016"
                    } else {
                        flags += "MOD05_FALLBACK_TO_MOD04_${vv2.reasonCode}"
                    }
                }
                is M2AModelResult.InvalidInput -> {
                    flags += "MOD05_INVALID_${vv2.reasonCode}"
                }
            }
        }

        // DEF-11: If a temporally compatible two-race pair showed steep endurance drop (k_between > 1.20),
        // fallback MOD-04 MUST anchor on the LONGER race (most endurance-representative), and note endurance limitation!
        val vv1Anchor = vvEligible.first()
        return when (val vv1 = Engine15M2AVickersModel1.predictMarathon(
            raceDistance = vv1Anchor.distance,
            raceDuration = vv1Anchor.duration,
            weeklyVolumePerWeek = typicalWeeklyM,
            courseDifficulty = vv1Anchor.courseDifficulty,
            allowUnverifiedSecondaryCourseAdjustments = allowUnverifiedCourseAdj
        )) {
            is M2AModelResult.Computed -> {
                val band = guidanceFactorFor(vv1Anchor)
                val isProv = !vv1Anchor.authority.isObservedAuthoritative ||
                    vv1Anchor.freshness == M2ABenchmarkFreshness.AGING ||
                    isCondProvMileage
                val caveats = buildList {
                    addAll(vv1.notes)
                    if (isCondProvMileage) add("CONDITIONAL_ON_PROVISIONAL_SELF_REPORT_MILEAGE")
                    if (vv1Anchor.freshness == M2ABenchmarkFreshness.AGING) add("AGING_BENCHMARK_91_TO_180D")
                    if (sawEnduranceDropAbstain) add("OBSERVED_STEEP_ENDURANCE_DROP_TWO_RACE_MODEL_ABSTAINED")
                }
                val pred = M2ARacePredictionOutcome.Predicted(
                    targetDistance = target,
                    predictedDuration = vv1.value.predictedMarathonDuration,
                    predictedSpeed = vv1.value.predictedMarathonSpeed,
                    modelId = vv1.modelId,
                    benchmarkAuthority = vv1Anchor.authority,
                    benchmarkFreshness = vv1Anchor.freshness,
                    isProvisional = isProv,
                    isConditionalOnProvisionalMileage = isCondProvMileage,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND,
                    guidanceBandFactor = band,
                    evidenceIds = vv1Anchor.mergedEvidenceIds,
                    caveats = caveats,
                    downstreamPrerequisitesRequired = marathonDownstreamPrereqs
                )
                Triple(pred, vv1Anchor, null)
            }
            is M2AModelResult.Abstained ->
                Triple(M2ARacePredictionOutcome.Abstained(target, vv1.reasonCode, vv1.detail), vv1Anchor, null)
            is M2AModelResult.InvalidInput ->
                Triple(M2ARacePredictionOutcome.Abstained(target, vv1.reasonCode, vv1.detail), vv1Anchor, null)
        }
    }

    private fun buildFallbackResult(
        request: M2APerformanceCalibrationRequest,
        endurance: M2AEnduranceMileageAssessment,
        refDuration: Seconds,
        thresholdBelief: BeliefState<MetersPerSecond>,
        abstainReason: String,
        abstainDetail: String,
        flags: Set<String>,
        evidenceIds: Set<String>
    ): M2APerformanceCalibrationResult {
        val abstainedPreds = M2ATargetRaceDistance.entries.associateWith { tgt ->
            M2ARacePredictionOutcome.Abstained(tgt, abstainReason, abstainDetail)
        }
        return M2APerformanceCalibrationResult(
            accountId = request.accountId,
            asOfDate = request.asOfDate,
            policyVersion = Engine15M2APolicyRegistry.POLICY_VERSION,
            authorityMode = M2ACalibrationAuthorityMode.EFFORT_AND_TALK_TEST_FALLBACK,
            authorization = M2ACalibrationAuthorization(
                hasVerifiedPerformanceReference = false,
                eligibleToParticipateInPaceCalibration = false,
                provisionalPaceGuidanceAvailable = false,
                workoutSpecificPacePrescriptionAuthorized = false,
                hrTrainingZoneAuthorized = false
            ),
            operationalThresholdGuidanceBandFactor = null,
            operationalThresholdBenchmark = null,
            marathonPrimaryBenchmark = null,
            marathonSecondaryBenchmark = null,
            operationalThresholdSpeedBelief = thresholdBelief,
            operationalThresholdReferenceDuration = refDuration,
            enduranceMileageAssessment = endurance,
            racePredictions = abstainedPreds,
            diagnosticFlags = flags,
            contributingEvidenceIds = evidenceIds,
            isMeasuredPhysiologicalThreshold = false
        )
    }

    private fun guidanceFactorFor(benchmark: M2ASelectedBenchmark): Double = when {
        !benchmark.authority.isObservedAuthoritative -> Engine15M2APolicyRegistry.POL_021_GUIDANCE_BAND_PROVISIONAL
        benchmark.freshness == M2ABenchmarkFreshness.AGING -> Engine15M2APolicyRegistry.POL_021_GUIDANCE_BAND_AGING
        else -> Engine15M2APolicyRegistry.POL_021_GUIDANCE_BAND_FRESH
    }

    private fun worstFreshness(a: M2ABenchmarkFreshness, b: M2ABenchmarkFreshness): M2ABenchmarkFreshness =
        if (a.ordinal >= b.ordinal) a else b

    private fun lowerAuthority(a: EvidenceAuthorityClass, b: EvidenceAuthorityClass): EvidenceAuthorityClass =
        if (authorityRank(a) <= authorityRank(b)) a else b

    private fun authorityRank(authority: EvidenceAuthorityClass): Int = when (authority) {
        EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED -> 4
        EvidenceAuthorityClass.SENSOR_SUPPORTED -> 3
        EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL -> 2
        EvidenceAuthorityClass.PROJECTED_NON_AUTHORITATIVE -> 1
        EvidenceAuthorityClass.UNOBSERVED -> 0
    }
}
