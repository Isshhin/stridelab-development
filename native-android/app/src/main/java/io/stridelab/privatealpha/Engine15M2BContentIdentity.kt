package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Length-framed fields keep opaque source IDs distinct across delimiters and nullability. */
internal fun m2bCanonical(vararg values: Any?): String = buildString {
    for (value in values) {
        if (value == null) {
            append("-1:")
        } else {
            val text = value.toString()
            append(text.length).append(':').append(text)
        }
    }
}

internal fun m2bRecordingTraceKey(trace: M2BRecordingTrace): String {
    val window = trace.window
    val origin = window.origin
    val samples = window.samples.joinToString("") {
        m2bCanonical(it.at, it.cumulativeHorizontalDistance.canonicalToken(),
            it.elevationMeters, it.continuity)
    }
    return m2bCanonical(trace.canonicalActivityId, origin.accountId, origin.discipline, origin.provider,
        origin.recordId, origin.externalId, origin.sha256, origin.recordingStart, window.distanceSemantics,
        window.sourceQuality.canonicalRepresentation(), window.distanceTolerance?.canonicalToken(), samples)
}

private fun contextKey(value: M2BEvaluationContext): String =
    m2bCanonical(value.accountId, value.discipline, value.physiologicalModality, value.asOf, value.zone.id)

private fun measurementKey(value: M2BHeartRateMeasurement): String = m2bCanonical(
    value.accountId, value.discipline, value.physiologicalModality, value.source.canonicalToken(),
    value.role, value.value.canonicalToken(), value.modality, value.started, value.completed, value.quality
)

private fun speedKey(value: M2BObservedSpeed): String = m2bCanonical(
    value.canonicalActivityId, value.source.canonicalToken(), sourceKey(value.provenance),
    value.completed, value.expiresAt, value.duration.canonicalToken(), value.rawSpeed.canonicalToken(),
    value.gradeAdjustedSpeed?.canonicalToken(), value.sensorTolerance?.canonicalToken(),
    value.effort, value.sourceClass, value.basis
)

private fun sourceKey(aliases: Collection<M2ASourceQualifiedAlias>): String =
    aliases.map { it.canonicalToken() }.sorted().joinToString("") { m2bCanonical(it) }

internal fun m2bCalibrationContentHash(result: Engine15M2BIntensityCalibrator): String {
    val guidance = when (val value = result.guidance) {
        null -> null
        M2BGuidance.EffortFirst -> m2bCanonical("EFFORT_FIRST")
        is M2BGuidance.VerifiedBenchmark ->
            m2bCanonical("VERIFIED", value.evidence.canonicalRepresentation(), value.referenceFactor)
        is M2BGuidance.ProvisionalBenchmark ->
            m2bCanonical("PROVISIONAL_RACE", value.evidence.canonicalRepresentation(), value.referenceFactor,
                value.recordedCoverage, value.requiresCr10CrossCheck, value.requiresTalkTestCrossCheck)
        is M2BGuidance.ProvisionalTraining -> m2bCanonical("PROVISIONAL_TRAINING", value.evidence.family,
            value.evidence.confirmedAt, value.evidence.source.canonicalToken(),
            value.evidence.observedSpeed.canonicalToken(), value.effortTakesPriority)
    }
    val hrr = when (val value = result.descriptiveHrr) {
        is M2BDescriptiveHeartRateReserve.ResearchOnly -> m2bCanonical("RESEARCH_ONLY", value.ratio.canonicalToken(),
            measurementKey(value.evidence.rest), measurementKey(value.evidence.maximum),
            measurementKey(value.evidence.observation))
        M2BDescriptiveHeartRateReserve.StructurallyInvalidOrMissing -> "INVALID_ANCHORS"
        M2BDescriptiveHeartRateReserve.ObservationInvalid -> "INVALID_OBSERVATION"
        M2BDescriptiveHeartRateReserve.StructurallyValidAwaitingCorroboration -> "AWAITING_CORROBORATION"
        M2BDescriptiveHeartRateReserve.QuarantinedForEvaluation -> "QUARANTINED"
    }
    val training = result.trainingReferences.joinToString("") {
        m2bCanonical(it.pathway, it.family, it.distribution.q1SecondsPerKm, it.distribution.medianSecondsPerKm,
            it.distribution.q3SecondsPerKm, it.restriction, it.canonicalActivityIds.sorted()
                .joinToString("") { id -> m2bCanonical(id) }, sourceKey(it.sourceEvidence),
            it.observations.joinToString("") { point ->
                m2bCanonical(point.canonicalActivityId, point.source.canonicalToken(), point.observedAt,
                    point.speed.canonicalToken())
            })
    }
    val cap = result.effortGuidance.durationExposureConstraint?.let {
        m2bCanonical(it.maximumContinuousDuration.canonicalToken(), it.source.canonicalToken())
    }
    val payload = m2bCanonical(result.policyVersion, contextKey(result.context), result.recordedHistoryCoverage,
        result.operationalThresholdCalibration?.contentHash, result.observedWorkloadContentHash, result.upstreamSnapshotId,
        result.persistentRestrictionIds.joinToString("") { m2bCanonical(it) },
        result.restrictionDecision.reasons.sorted().joinToString(","), result.hold,
        result.restrictionDecision.hrQuarantined, result.restrictionDecision.recoveryOnly,
        result.effortGuidance.restriction, cap, guidance, training,
        result.envelope.exactObservations.joinToString("") { m2bCanonical(speedKey(it)) },
        result.envelope.elapsedAverages.joinToString("") { m2bCanonical(speedKey(it)) },
        result.envelope.recordingTraces.joinToString("") { m2bCanonical(m2bRecordingTraceKey(it)) },
        result.tanakaPrior.belief.canonicalRepresentation(), result.tanakaPrior.residualFloor?.canonicalToken(),
        result.heartRateHierarchy.evidence.joinToString("") { m2bCanonical(m2bHREvidenceKey(it)) },
        result.heartRateHierarchy.conflicts.sorted().joinToString(","), hrr)
    return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
