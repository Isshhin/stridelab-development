package io.stridelab.privatealpha

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal sealed interface Engine15I8ArtifactIntegrityDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String
    ) : Engine15I8ArtifactIntegrityDecision

    /**
     * The caller's supplied bytes and claimed metadata agree. Neither the
     * record's issuer nor its physiology/testing protocol is authenticated.
     */
    data class ArtifactConsistentForExternalReview(
        val policyId: String,
        val originalI7: Engine15I7CalibrationReviewDecision.ExternalQualificationRequired,
        val matchedSha256: String,
        val schemaVersion: String,
        val fieldsAgreeWithClaim: Boolean = true,
        val bytesMatchClaimedFingerprint: Boolean = true,
        val ruleIds: List<String>,
        val sourceOwnerAuthenticated: Boolean = false,
        val externallyAuthenticated: Boolean = false,
        val protocolVerified: Boolean = false,
        val thresholdIntensityVerified: Boolean = false,
        val doseAuthorized: Boolean = false,
        val athleteResponseVerified: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15I8ArtifactIntegrityDecision {
        init {
            require(matchedSha256.matches(Regex("[0-9a-f]{64}")))
            require(schemaVersion == Engine15I8CalibrationArtifactIntegrity.SCHEMA)
            require(fieldsAgreeWithClaim && bytesMatchClaimedFingerprint)
            require(ruleIds.distinct() == ruleIds)
            require(!sourceOwnerAuthenticated && !externallyAuthenticated &&
                !protocolVerified && !thresholdIntensityVerified &&
                !doseAuthorized && !athleteResponseVerified &&
                !executable && !authorizedForProduction)
            require(!originalI7.calibrationArtifactVerified &&
                !originalI7.thresholdIntensityVerified &&
                !originalI7.authorizedForProduction)
        }
    }
}

/**
 * I8 checks a small, intentionally strict INTERNAL interchange record.
 * This does NOT parse real laboratory PDFs, independently verify FIT files,
 * or certify test issuer, participant identity, sensor or protocol provenance.
 */
internal object Engine15I8CalibrationArtifactIntegrity {
    const val POLICY_ID = "engine15.i8.calibration-artifact-integrity-shadow-20261117.1"
    const val SCHEMA = "stridelab-calibration-record/v1"
    private const val MAX_BYTES = 16_384
    private val TOKEN = Regex("[A-Za-z0-9._:/-]{1,256}")

    private val KEYS = listOf(
        "owner",
        "family",
        "measured_on",
        "origin",
        "source_reference",
        "protocol_reference",
        "threshold_hr_bpm",
        "threshold_pace_seconds_per_km",
        "resting_hr_bpm",
        "maximum_hr_bpm"
    )

    /** Canonical encoding is a format descriptor, NOT a signed attestation. */
    internal fun canonicalRecordBytes(
        claim: Engine15I7CalibrationClaim
    ): ByteArray {
        fun requiredToken(value: String?) =
            requireNotNull(value).also { require(TOKEN.matches(it)) }
        val values = listOf(
            requiredToken(claim.assertedOwnerId),
            claim.family.name,
            claim.measuredOn.toString(),
            claim.origin.name,
            requiredToken(claim.subjectSpecificSourceReference),
            requiredToken(claim.testingProtocolReference),
            requireNotNull(claim.claimedThresholdHrBpm).toString(),
            requireNotNull(claim.claimedThresholdPaceSecondsPerKm).toString(),
            claim.assertedRestingHrBpm?.toString() ?: "-",
            claim.assertedMaximumHrBpm?.toString() ?: "-"
        )
        return (
            (listOf(SCHEMA) + KEYS.zip(values).map { (key, value) ->
                "$key=$value"
            }).joinToString("\n") + "\n"
        ).toByteArray(StandardCharsets.UTF_8)
    }

    fun inspect(
        source: Engine15IndependentVolumePlanner.Result,
        qualityRequest: Engine15M5QualityPreviewRequest,
        sourceEvidence: Engine15I3QualityDurationInput,
        feasibilityInput: Engine15I5DistanceFeasibilityInput,
        calibrationInput: Engine15I7CalibrationReviewInput,
        artifactBytes: ByteArray?
    ): Engine15I8ArtifactIntegrityDecision {
        fun defer(code: String, detail: String) =
            Engine15I8ArtifactIntegrityDecision.Deferred(code, detail)

        val previous = when (val reviewed = Engine15I7IntensityCalibrationReview.inspect(
            source, qualityRequest, sourceEvidence, feasibilityInput, calibrationInput
        )) {
            is Engine15I7CalibrationReviewDecision.Deferred ->
                return defer("UPSTREAM_" + reviewed.reasonCode, reviewed.explanation)
            is Engine15I7CalibrationReviewDecision.ExternalQualificationRequired ->
                reviewed
        }
        if (artifactBytes == null || artifactBytes.isEmpty())
            return defer("ARTIFACT_BYTES_MISSING",
                "Calibration bytes were not supplied; an asserted digest alone is insufficient.")
        if (artifactBytes.size > MAX_BYTES)
            return defer("ARTIFACT_BYTES_TOO_LARGE",
                "I8 only processes bounded internal calibration interchange records.")

        val claim = previous.originalCalibrationClaim
        if (!TOKEN.matches(claim.assertedOwnerId) ||
            !TOKEN.matches(requireNotNull(claim.subjectSpecificSourceReference)) ||
            !TOKEN.matches(requireNotNull(claim.testingProtocolReference)))
            return defer("ARTIFACT_CLAIM_TOKEN_INVALID",
                "Owner and references need unambiguous canonical ASCII tokens.")

        val expectedSha = requireNotNull(claim.artifactSha256Claim)
        val digest = MessageDigest.getInstance("SHA-256").digest(artifactBytes)
        val expectedHash = expectedSha.chunked(2)
            .map { it.toInt(16).toByte() }.toByteArray()
        if (!MessageDigest.isEqual(digest, expectedHash))
            return defer("ARTIFACT_SHA256_MISMATCH",
                "The supplied byte sequence does not match its caller-claimed digest.")

        val decoded = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(artifactBytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            return defer("ARTIFACT_UTF8_INVALID",
                "The interchange record must contain strict UTF-8 text.")
        }
        if (!decoded.endsWith("\n") || decoded.contains('\r') ||
            decoded.startsWith('\uFEFF'))
            return defer("ARTIFACT_SCHEMA_NOT_CANONICAL",
                "Record must use canonical LF lines without CR or BOM.")
        val lines = decoded.removeSuffix("\n").split('\n')
        if (lines.size != KEYS.size + 1 || lines.first() != SCHEMA ||
            lines.drop(1).map { it.substringBefore('=', "") } != KEYS ||
            lines.drop(1).any { it.count { char -> char == '=' } != 1 })
            return defer("ARTIFACT_SCHEMA_INVALID",
                "Canonical calibration record must have exactly the expected ordered keys.")

        val required = try {
            canonicalRecordBytes(claim)
        } catch (_: IllegalArgumentException) {
            return defer("ARTIFACT_CLAIM_NOT_CANONICAL",
                "Claim cannot be represented in the canonical interchange schema.")
        }
        if (!MessageDigest.isEqual(required, artifactBytes))
            return defer("ARTIFACT_FIELDS_CONTRADICT_CLAIM",
                "Source bytes and I7 claim disagree on at least one calibration field.")

        val computedHex = digest.joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
        return Engine15I8ArtifactIntegrityDecision.ArtifactConsistentForExternalReview(
            policyId = POLICY_ID,
            originalI7 = previous,
            matchedSha256 = computedHex,
            schemaVersion = SCHEMA,
            ruleIds = listOf(
                "I7_I6_I5_I4_I3_M4B_M5B_M6B_RECOMPUTED",
                "BOUNDED_STRICT_CANONICAL_UTF8_RECORD",
                "SHA256_OF_SUPPLIED_BYTES_MATCHES_CALLER_CLAIM",
                "ALL_CANONICAL_FIELDS_MATCH_I7_CLAIM",
                "MATCHED_DIGEST_DOES_NOT_AUTHENTICATE_ISSUER",
                "NO_VERIFIED_THRESHOLD_DOSE_OR_PRODUCTION_PERMISSION"
            )
        )
    }
}
