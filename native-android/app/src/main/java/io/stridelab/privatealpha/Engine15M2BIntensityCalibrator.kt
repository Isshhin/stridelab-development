package io.stridelab.privatealpha

import java.time.Duration
import java.time.Instant
import java.util.Collections

internal sealed interface M2BRestrictionStatus {
    data object NoRestrictionReported : M2BRestrictionStatus
    data class PersistentActive(val evaluatedFor: M2BEvaluationContext) : M2BRestrictionStatus
    data class ClearanceClaimed(val evaluatedFor: M2BEvaluationContext) : M2BRestrictionStatus
    data object StatusUnavailable : M2BRestrictionStatus
    data class RunningSpecific(val evaluatedFor: M2BEvaluationContext) : M2BRestrictionStatus
    data class HeartRateQuarantine(val evaluatedFor: M2BEvaluationContext) : M2BRestrictionStatus
    data class RecoveryIntensity(val evaluatedFor: M2BEvaluationContext) : M2BRestrictionStatus
}

internal enum class M2BRestrictionReason {
    NO_RESTRICTION_REPORTED, UNKNOWN_STATE, PERSISTENT_HOLD, CLEARANCE_CLAIMED,
    RUNNING_RESTRICTION, HR_QUARANTINE, RECOVERY_INTENSITY, EVALUATION_CONTEXT_MISMATCH
}

internal class M2BRestrictionDecision private constructor(
    val reasons: Set<M2BRestrictionReason>,
    val hold: Boolean,
    val hrQuarantined: Boolean,
    val recoveryOnly: Boolean
) {
    val verifiedClearance: Boolean get() = false

    companion object {
        fun resolve(
            context: M2BEvaluationContext,
            restrictions: List<M2BRestrictionStatus>,
            recovery: M2BRecoveryContext?
        ): M2BRestrictionDecision {
            val reasons = sortedSetOf<M2BRestrictionReason>()
            fun checkContext(evaluatedFor: M2BEvaluationContext) {
                if (evaluatedFor != context) reasons += M2BRestrictionReason.EVALUATION_CONTEXT_MISMATCH
            }
            for (restriction in restrictions) {
                when (restriction) {
                    M2BRestrictionStatus.NoRestrictionReported -> Unit
                    M2BRestrictionStatus.StatusUnavailable -> reasons += M2BRestrictionReason.UNKNOWN_STATE
                    is M2BRestrictionStatus.PersistentActive -> {
                        checkContext(restriction.evaluatedFor)
                        reasons += M2BRestrictionReason.PERSISTENT_HOLD
                    }
                    is M2BRestrictionStatus.ClearanceClaimed -> {
                        checkContext(restriction.evaluatedFor)
                        reasons += M2BRestrictionReason.CLEARANCE_CLAIMED
                    }
                    is M2BRestrictionStatus.RunningSpecific -> {
                        checkContext(restriction.evaluatedFor)
                        if (context.discipline == AthleteDiscipline.RUN) reasons += M2BRestrictionReason.RUNNING_RESTRICTION
                    }
                    is M2BRestrictionStatus.HeartRateQuarantine -> {
                        checkContext(restriction.evaluatedFor)
                        reasons += M2BRestrictionReason.HR_QUARANTINE
                    }
                    is M2BRestrictionStatus.RecoveryIntensity -> {
                        checkContext(restriction.evaluatedFor)
                        reasons += M2BRestrictionReason.RECOVERY_INTENSITY
                    }
                }
            }
            if (recovery?.requiresConservativeGuidance(context) == true) reasons += M2BRestrictionReason.RECOVERY_INTENSITY
            if (reasons.isEmpty()) reasons += M2BRestrictionReason.NO_RESTRICTION_REPORTED
            val hold = reasons.any {
                it in setOf(M2BRestrictionReason.PERSISTENT_HOLD, M2BRestrictionReason.CLEARANCE_CLAIMED,
                    M2BRestrictionReason.UNKNOWN_STATE, M2BRestrictionReason.RUNNING_RESTRICTION)
            }
            val recoveryOnly = M2BRestrictionReason.RECOVERY_INTENSITY in reasons
            return M2BRestrictionDecision(
                Collections.unmodifiableSet(reasons), hold,
                hold || recoveryOnly || M2BRestrictionReason.HR_QUARANTINE in reasons, recoveryOnly
            )
        }
    }
}

internal enum class M2BExecutionCondition {
    ORDINARY,
    ENVIRONMENTAL_CONFOUNDER,
    ACUTE_FATIGUE,
    RETURN_TO_TRAINING,
    ACTIVE_EVIDENCE_CONFLICT
}

internal data class M2BRecoveryContext(
    val evaluatedFor: M2BEvaluationContext,
    val illnessEndedAt: Instant?,
    val recoveryStabilityEstablished: Boolean
) {
    fun requiresConservativeGuidance(context: M2BEvaluationContext): Boolean {
        if (context != evaluatedFor || !recoveryStabilityEstablished) return true
        val endedAt = illnessEndedAt ?: return false
        return Duration.between(endedAt, context.asOf) <
            Duration.ofDays(Engine15M2BPolicyRegistry.POST_ILLNESS_MIN_QUARANTINE_DAYS)
    }
}

internal enum class M2BHeartRateRole { REST, MAXIMUM, OBSERVATION }

internal data class M2BHeartRateMeasurement(
    val accountId: String,
    val discipline: AthleteDiscipline,
    val source: M2ASourceQualifiedAlias,
    val role: M2BHeartRateRole,
    val value: BeatsPerSecond,
    val modality: HeartRateSensorModality,
    val started: Instant,
    val completed: Instant,
    val quality: TelemetryQualityStatus,
    val physiologicalModality: M2BPhysiologicalModality =
        if (discipline == AthleteDiscipline.RUN) M2BPhysiologicalModality.RUNNING else M2BPhysiologicalModality.UNSPECIFIED
)

/** Evaluation-only corroboration. A caller can construct this, so it confers research access only. */
internal data class M2BResearchHrAttestation(
    val measurement: M2BHeartRateMeasurement,
    val evaluatedFor: M2BEvaluationContext
)

internal data class M2BResearchHeartRateInputs(
    val rest: M2BHeartRateMeasurement,
    val maximum: M2BHeartRateMeasurement,
    val observation: M2BHeartRateMeasurement
)

internal sealed interface M2BDescriptiveHeartRateReserve {
    data object StructurallyInvalidOrMissing : M2BDescriptiveHeartRateReserve
    data object ObservationInvalid : M2BDescriptiveHeartRateReserve
    data object QuarantinedForEvaluation : M2BDescriptiveHeartRateReserve
    data object StructurallyValidAwaitingCorroboration : M2BDescriptiveHeartRateReserve
    class ResearchOnly private constructor(
        val evidence: M2BResearchHeartRateInputs,
        val ratio: DimensionlessRatio
    ) : M2BDescriptiveHeartRateReserve {
        companion object {
            fun evaluate(
                context: M2BEvaluationContext,
                measurements: M2BResearchHeartRateInputs?,
                attestations: List<M2BResearchHrAttestation>
            ): M2BDescriptiveHeartRateReserve {
                val invalid = M2BDescriptiveHeartRateReserve.StructurallyInvalidOrMissing
                if (measurements == null || context.discipline != AthleteDiscipline.RUN ||
                    context.physiologicalModality != M2BPhysiologicalModality.RUNNING
                ) return invalid
                val (rest, maximum, observation) = measurements
                if (rest.role != M2BHeartRateRole.REST || maximum.role != M2BHeartRateRole.MAXIMUM ||
                    rest.source.canonicalToken() == maximum.source.canonicalToken() ||
                    !isUsableMeasurement(rest, context) || !isUsableMeasurement(maximum, context) ||
                    maximum.value <= rest.value
                ) return invalid
                val values = listOf(rest, maximum, observation)
                if (observation.role != M2BHeartRateRole.OBSERVATION ||
                    values.map { it.source.canonicalToken() }.distinct().size != values.size ||
                    !isUsableMeasurement(observation, context) ||
                    rest.completed > observation.started || maximum.completed > observation.started
                ) return M2BDescriptiveHeartRateReserve.ObservationInvalid
                val relevantClaims = attestations.filter { it.evaluatedFor == context }
                    .groupBy { it.measurement.source.canonicalToken() }
                if (values.any { value ->
                        relevantClaims[value.source.canonicalToken()]
                            ?.map(M2BResearchHrAttestation::measurement)?.distinct()?.singleOrNull() != value
                    }
                ) return M2BDescriptiveHeartRateReserve.StructurallyValidAwaitingCorroboration
                val ratio = Engine15M2BIntensityModels.hrrRatio(observation.value, rest.value, maximum.value)
                    ?: return M2BDescriptiveHeartRateReserve.ObservationInvalid
                return ResearchOnly(measurements, ratio)
            }

            private fun isUsableMeasurement(value: M2BHeartRateMeasurement, context: M2BEvaluationContext): Boolean =
                value.accountId == context.accountId && value.discipline == context.discipline &&
                    value.physiologicalModality == context.physiologicalModality &&
                    value.quality == TelemetryQualityStatus.VALID_OBSERVED &&
                    value.modality != HeartRateSensorModality.UNKNOWN &&
                    value.completed >= value.started && value.completed <= context.asOf &&
                    value.value.toBpm() in Engine15M1PolicyRegistry.POL_005_MIN_PLAUSIBLE_HR_BPM..
                    Engine15M1PolicyRegistry.POL_005_MAX_PLAUSIBLE_HR_BPM
        }
    }

    val structurallyValidAnchors: Boolean
        get() = this != StructurallyInvalidOrMissing && this != QuarantinedForEvaluation
    val grantsCoachingEligibility: Boolean get() = false
    val grantsProductionAuthority: Boolean get() = false
}

internal enum class M2BRunningReferenceFamily { EASY, STEADY, TEMPO, CRUISE, INTERVALS }

internal data class M2BConfirmedTrainingReference(
    val evaluatedFor: M2BEvaluationContext,
    val source: M2ASourceQualifiedAlias,
    val family: M2BRunningReferenceFamily,
    val confirmedAt: Instant,
    val observedSpeed: MetersPerSecond
)

internal sealed interface M2BGuidance {
    data class VerifiedBenchmark(val evidence: M2ASelectedBenchmark, val referenceFactor: Double) : M2BGuidance
    data class ProvisionalBenchmark(
        val evidence: M2ASelectedBenchmark,
        val referenceFactor: Double,
        val recordedCoverage: HistoryCoverageStatus
    ) : M2BGuidance {
        val requiresCr10CrossCheck: Boolean get() = true
        val requiresTalkTestCrossCheck: Boolean get() = true
    }
    data class ProvisionalTraining(val evidence: M2BConfirmedTrainingReference) : M2BGuidance {
        val effortTakesPriority: Boolean get() = true
    }
    data object EffortFirst : M2BGuidance {
        val conversationalCr10: IntRange = 2..3
        val talkTest = "Comfortable sentences"
    }
}

internal class Engine15M2BIntensityCalibrator private constructor(
    val context: M2BEvaluationContext,
    val restrictionDecision: M2BRestrictionDecision,
    val persistentRestrictionIds: Set<String>,
    val guidance: M2BGuidance?,
    val envelope: Engine15M2BPerformanceLowerEnvelope,
    val tanakaPrior: M2BTanakaPrior,
    val descriptiveHrr: M2BDescriptiveHeartRateReserve,
    val operationalThresholdCalibration: M2APerformanceCalibrationResult?,
    val observedWorkloadContentHash: String?,
    val upstreamSnapshotId: String?,
    val trainingReferences: List<M2BTrainingReference>,
    val effortGuidance: M2BEffortGuidance,
    val heartRateHierarchy: M2BHeartRateHierarchy,
    val recordedHistoryCoverage: HistoryCoverageStatus
) {
    val policyVersion: String get() = Engine15M2BPolicyRegistry.POLICY_VERSION
    val hold: Boolean get() = restrictionDecision.hold
    val productionAuthorized: Boolean get() = false
    val workoutPrescriptionAuthorized: Boolean get() = false
    val hrZoneAuthorized: Boolean get() = false
    val paceCalibrationEligible: Boolean get() = guidance is M2BGuidance.VerifiedBenchmark
    val contentHash: String = m2bCalibrationContentHash(this)

    companion object {
        fun calibrate(
            context: M2BEvaluationContext,
            restriction: M2BRestrictionStatus = M2BRestrictionStatus.NoRestrictionReported,
            condition: M2BExecutionCondition = M2BExecutionCondition.ORDINARY,
            recovery: M2BRecoveryContext? = null,
            ageYears: Int? = null,
            performance: M2APerformanceCalibrationRequest? = null,
            streams: List<M2BRunningStream> = emptyList(),
            confirmedTraining: M2BConfirmedTrainingReference? = null,
            heartRate: M2BResearchHeartRateInputs? = null,
            researchAttestations: List<M2BResearchHrAttestation> = emptyList(),
            additionalRestrictions: List<M2BRestrictionStatus> = emptyList(),
            trainingObservations: List<M2BTrainingObservation> = emptyList(),
            heartRateEvidence: List<M2BHeartRateEvidence> = emptyList(),
            durationExposureConstraint: M2BDurationExposureConstraint? = null,
            athleteSnapshot: AthleteStateSnapshot? = null
        ): Engine15M2BIntensityCalibrator {
            val snapshots = listOfNotNull(athleteSnapshot, performance?.athleteStateSnapshot)
                .filter { it.accountId == context.accountId }
            val persistentIds = snapshots.flatMap { it.activeRestrictionIds }.toSortedSet()
            val snapshotRestriction = if (persistentIds.isNotEmpty()) M2BRestrictionStatus.PersistentActive(context) else null
            val decision = M2BRestrictionDecision.resolve(context,
                listOfNotNull(restriction, snapshotRestriction) + additionalRestrictions, recovery)
            val running = context.discipline == AthleteDiscipline.RUN &&
                context.physiologicalModality == M2BPhysiologicalModality.RUNNING
            val pinnedSnapshot = snapshots.filter { it.evaluatedAtUtc == context.asOf }
                .distinctBy { it.snapshotId }.singleOrNull()
            val scopedPerformance = performance?.takeIf {
                compatible(context, it) && running &&
                    (athleteSnapshot == null || pinnedSnapshot != null &&
                        (pinnedSnapshot.observedWorkload == null ||
                            pinnedSnapshot.observedWorkload.contentHash == it.observedWorkload.contentHash))
            }
            val workload = scopedPerformance?.observedWorkload ?: pinnedSnapshot?.observedWorkload?.takeIf {
                it.accountId == context.accountId && it.asOfDate == context.localDate && it.zoneId == context.zone
            }
            val runHistory = workload?.disciplines?.get(AthleteDiscipline.RUN)
            val coverage = runHistory?.coverage ?: HistoryCoverageStatus.MISSING_UNOBSERVED
            val accepted = scopedPerformance?.let { evaluateBenchmarks(context, it, coverage) }
            val confirmed = confirmedTraining?.takeIf {
                running && it.evaluatedFor == context && it.confirmedAt <= context.asOf &&
                    it.family in setOf(M2BRunningReferenceFamily.EASY, M2BRunningReferenceFamily.STEADY,
                        M2BRunningReferenceFamily.TEMPO) && it.observedSpeed.toPaceSecondsPerKm().isFinite()
            }
            val effortRestriction = when {
                decision.hold || !running -> M2BEffortRestriction.WITHHELD
                decision.recoveryOnly || condition == M2BExecutionCondition.RETURN_TO_TRAINING -> M2BEffortRestriction.RECOVERY_ONLY
                condition != M2BExecutionCondition.ORDINARY || runHistory?.selfReportConflictFlag == true ||
                    coverage == HistoryCoverageStatus.CONFIRMED_ZERO ->
                    M2BEffortRestriction.EFFORT_FIRST
                else -> M2BEffortRestriction.STANDARD
            }
            val guidance = when {
                effortRestriction == M2BEffortRestriction.WITHHELD -> null
                effortRestriction != M2BEffortRestriction.STANDARD -> M2BGuidance.EffortFirst
                else -> accepted?.let { benchmarkGuidance(it, coverage) }
                    ?: confirmed?.let { M2BGuidance.ProvisionalTraining(it) }
                    ?: M2BGuidance.EffortFirst
            }
            val prior = Engine15M2BIntensityModels.tanaka(ageYears)
            val training = Engine15M2BTrainingEvidence.extract(context, trainingObservations) +
                listOfNotNull(confirmed?.let { M2BTrainingReference.historical(it) })
            return Engine15M2BIntensityCalibrator(
                context, decision, Collections.unmodifiableSet(persistentIds.toSortedSet()), guidance,
                Engine15M2BPerformanceLowerEnvelope.build(context, streams), prior,
                if (decision.hrQuarantined) M2BDescriptiveHeartRateReserve.QuarantinedForEvaluation
                else M2BDescriptiveHeartRateReserve.ResearchOnly.evaluate(context, heartRate, researchAttestations),
                accepted, workload?.contentHash, pinnedSnapshot?.snapshotId,
                Collections.unmodifiableList(training), M2BEffortGuidance(effortRestriction, durationExposureConstraint?.takeIf {
                    it.evaluatedFor == context
                }), M2BHeartRateHierarchy.reconcile(context, prior, heartRateEvidence, decision.hrQuarantined), coverage
            )
        }

        private fun compatible(context: M2BEvaluationContext, performance: M2APerformanceCalibrationRequest): Boolean =
            performance.accountId == context.accountId && performance.zoneId == context.zone &&
                performance.asOfDate == context.localDate && performance.observedWorkload.zoneId == context.zone &&
                performance.observedWorkload.asOfDate == context.localDate &&
                (performance.asOfUtc == null || performance.asOfUtc == context.asOf) &&
                (performance.athleteStateSnapshot == null ||
                    performance.athleteStateSnapshot.evaluatedAtUtc == context.asOf &&
                    (performance.athleteStateSnapshot.observedWorkload == null ||
                        performance.athleteStateSnapshot.observedWorkload.contentHash == performance.observedWorkload.contentHash))

        private fun evaluateBenchmarks(
            context: M2BEvaluationContext,
            performance: M2APerformanceCalibrationRequest,
            coverage: HistoryCoverageStatus
        ): M2APerformanceCalibrationResult {
            val provisionalAllowed = coverage in setOf(HistoryCoverageStatus.MISSING_UNOBSERVED,
                HistoryCoverageStatus.PARTIAL_COVERAGE, HistoryCoverageStatus.PROVISIONAL_SELF_REPORT)
            val completed = performance.racePerformances.filter {
                it.accountId == context.accountId && it.discipline == context.discipline &&
                    completedBy(it, context) &&
                    (provisionalAllowed || (it.authority.isObservedAuthoritative &&
                        it.performanceIntent != M2APerformanceIntent.SELF_REPORTED_RACE_OR_TIME_TRIAL))
            }
            return Engine15M2APerformanceCalibrator.calibrate(
                performance.copy(racePerformances = completed, asOfUtc = context.asOf)
            )
        }

        private fun benchmarkGuidance(
            accepted: M2APerformanceCalibrationResult,
            coverage: HistoryCoverageStatus
        ): M2BGuidance? {
            val benchmark = accepted.operationalThresholdBenchmark ?: return null
            val factor = accepted.operationalThresholdGuidanceBandFactor ?: return null
            return when (accepted.authorityMode) {
                M2ACalibrationAuthorityMode.VERIFIED_RECENT_BENCHMARK,
                M2ACalibrationAuthorityMode.VERIFIED_AGING_BENCHMARK ->
                    if (accepted.authorization.eligibleToParticipateInPaceCalibration &&
                        !benchmark.hasConflictingFitnessEpoch
                    ) M2BGuidance.VerifiedBenchmark(benchmark, factor) else null
                M2ACalibrationAuthorityMode.PROVISIONAL_SELF_REPORT_BENCHMARK ->
                    if (accepted.authorization.provisionalPaceGuidanceAvailable) {
                        M2BGuidance.ProvisionalBenchmark(benchmark, factor, coverage)
                    } else {
                        null
                    }
                M2ACalibrationAuthorityMode.EFFORT_AND_TALK_TEST_FALLBACK -> null
            }
        }

        private fun completedBy(benchmark: M2ARacePerformanceInput, context: M2BEvaluationContext): Boolean {
            val date = benchmark.performedOn ?: return false
            val start = benchmark.observedAtUtc ?: return date < context.localDate &&
                benchmark.performanceIntent == M2APerformanceIntent.SELF_REPORTED_RACE_OR_TIME_TRIAL
            if (start > context.asOf || start.atZone(context.zone).toLocalDate() != date) return false
            val elapsed = Duration.between(start, context.asOf)
            return benchmark.duration.value <= elapsed.seconds + elapsed.nano / 1_000_000_000.0
        }

    }
}
