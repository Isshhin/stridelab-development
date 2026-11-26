package io.stridelab.privatealpha

import java.time.Duration
import java.time.Instant

internal sealed interface M2BRestrictionStatus {
    data object NoRestrictionReported : M2BRestrictionStatus
    data class PersistentActive(val evaluatedFor: M2BEvaluationContext) : M2BRestrictionStatus
    data class ClearanceClaimed(val evaluatedFor: M2BEvaluationContext) : M2BRestrictionStatus
    data object StatusUnavailable : M2BRestrictionStatus
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
    val quality: TelemetryQualityStatus
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
                if (measurements == null || context.discipline != AthleteDiscipline.RUN) return invalid
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
                    value.quality == TelemetryQualityStatus.VALID_OBSERVED &&
                    value.modality != HeartRateSensorModality.UNKNOWN &&
                    value.completed >= value.started && value.completed <= context.asOf &&
                    value.value.toBpm() in Engine15M1PolicyRegistry.POL_005_MIN_PLAUSIBLE_HR_BPM..
                    Engine15M1PolicyRegistry.POL_005_MAX_PLAUSIBLE_HR_BPM
        }
    }

    val structurallyValidAnchors: Boolean get() = this != StructurallyInvalidOrMissing
    val grantsCoachingEligibility: Boolean get() = false
    val grantsProductionAuthority: Boolean get() = false
}

internal enum class M2BRunningReferenceFamily { EASY, STEADY, TEMPO }

internal data class M2BConfirmedTrainingReference(
    val evaluatedFor: M2BEvaluationContext,
    val source: M2ASourceQualifiedAlias,
    val family: M2BRunningReferenceFamily,
    val confirmedAt: Instant,
    val observedSpeed: MetersPerSecond
)

internal sealed interface M2BGuidance {
    data class VerifiedBenchmark(val evidence: M2ASelectedBenchmark, val referenceFactor: Double) : M2BGuidance
    data class ProvisionalBenchmark(val evidence: M2ASelectedBenchmark, val referenceFactor: Double) : M2BGuidance
    data class ProvisionalTraining(val evidence: M2BConfirmedTrainingReference) : M2BGuidance
    data object EffortFirst : M2BGuidance {
        val conversationalCr10: IntRange = 2..3
        val talkTest = "Comfortable sentences"
    }
}

internal class Engine15M2BIntensityCalibrator private constructor(
    val context: M2BEvaluationContext,
    val hold: Boolean,
    val guidance: M2BGuidance?,
    val envelope: Engine15M2BPerformanceLowerEnvelope,
    val tanakaPrior: M2BTanakaPrior,
    val descriptiveHrr: M2BDescriptiveHeartRateReserve
) {
    val policyVersion: String get() = Engine15M2BPolicyRegistry.POLICY_VERSION
    val productionAuthorized: Boolean get() = false
    val workoutPrescriptionAuthorized: Boolean get() = false
    val hrZoneAuthorized: Boolean get() = false
    val paceCalibrationEligible: Boolean get() = guidance is M2BGuidance.VerifiedBenchmark

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
            researchAttestations: List<M2BResearchHrAttestation> = emptyList()
        ): Engine15M2BIntensityCalibrator {
            // An unresolved hold or a replayed/caller-claimed clearance always blocks guidance before M9.
            val hold = restriction != M2BRestrictionStatus.NoRestrictionReported
            val guidance = when {
                hold || context.discipline != AthleteDiscipline.RUN -> null
                condition != M2BExecutionCondition.ORDINARY ||
                    recovery?.requiresConservativeGuidance(context) == true -> M2BGuidance.EffortFirst
                else -> benchmarkGuidance(context, performance)
                    ?: confirmedTraining?.takeIf {
                        it.evaluatedFor == context && it.confirmedAt <= context.asOf
                    }?.let { M2BGuidance.ProvisionalTraining(it) }
                    ?: M2BGuidance.EffortFirst
            }
            return Engine15M2BIntensityCalibrator(
                context, hold, guidance, Engine15M2BPerformanceLowerEnvelope.build(context, streams),
                Engine15M2BIntensityModels.tanaka(ageYears),
                M2BDescriptiveHeartRateReserve.ResearchOnly.evaluate(context, heartRate, researchAttestations)
            )
        }

        private fun benchmarkGuidance(
            context: M2BEvaluationContext,
            performance: M2APerformanceCalibrationRequest?
        ): M2BGuidance? {
            if (performance == null || performance.accountId != context.accountId ||
                performance.zoneId != context.zone || performance.asOfDate != context.localDate ||
                performance.observedWorkload.zoneId != context.zone ||
                performance.observedWorkload.asOfDate != context.localDate ||
                (performance.asOfUtc != null && performance.asOfUtc != context.asOf)
            ) return null
            val completed = performance.racePerformances.filter {
                it.accountId == context.accountId && it.discipline == context.discipline &&
                    completedBy(it, context)
            }
            val accepted = Engine15M2APerformanceCalibrator.calibrate(
                performance.copy(racePerformances = completed, asOfUtc = context.asOf)
            )
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
                        M2BGuidance.ProvisionalBenchmark(benchmark, factor)
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
