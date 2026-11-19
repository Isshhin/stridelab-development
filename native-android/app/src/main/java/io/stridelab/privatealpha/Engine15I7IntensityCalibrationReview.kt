package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Metadata supplied for EXTERNAL qualification review, NOT an independently
 * verified threshold measurement. All origins and references are assertions.
 */
internal enum class Engine15I7CalibrationOrigin {
    LAB_TEST_ASSERTED,
    STRUCTURED_FIELD_TEST_ASSERTED,
    DEVICE_ESTIMATE,
    MANUAL_ENTRY
}

internal data class Engine15I7CalibrationClaim(
    val assertedOwnerId: String,
    val family: IndependentQualityFamily,
    val measuredOn: LocalDate,
    val origin: Engine15I7CalibrationOrigin,
    val subjectSpecificSourceReference: String?,
    val testingProtocolReference: String?,
    val artifactSha256Claim: String?,
    val claimedThresholdHrBpm: Int?,
    val claimedThresholdPaceSecondsPerKm: Double?,
    val assertedRestingHrBpm: Int? = null,
    val assertedMaximumHrBpm: Int? = null
)

internal data class Engine15I7CalibrationReviewInput(
    val claim: Engine15I7CalibrationClaim?,
    /** Display-only; these user preferences never certify source calibration. */
    val displayHrSettings: AthleteHeartRateZoneSettings? = null
)

internal sealed interface Engine15I7CalibrationReviewDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String
    ) : Engine15I7CalibrationReviewDecision

    /**
     * An externally reviewable *claim*, not a calibrated running target.
     * Source identity and physiological test provenance remain unresolved.
     */
    data class ExternalQualificationRequired(
        val policyId: String,
        val originalI6: Engine15I6PaceConsistencyDecision.DescriptiveSourceConsistency,
        val originalCalibrationClaim: Engine15I7CalibrationClaim,
        val outstandingVerificationGaps: List<String>,
        val ruleIds: List<String>,
        val ownerSourceAuthenticated: Boolean = false,
        val calibrationArtifactVerified: Boolean = false,
        val calibrationProtocolVerified: Boolean = false,
        val thresholdIntensityVerified: Boolean = false,
        val workDoseAuthorized: Boolean = false,
        val athleteResponseVerified: Boolean = false,
        val futureDistanceVerified: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15I7CalibrationReviewDecision {
        init {
            require(outstandingVerificationGaps.isNotEmpty())
            require(outstandingVerificationGaps.distinct() == outstandingVerificationGaps)
            require(ruleIds.distinct() == ruleIds)
            require(originalCalibrationClaim.origin in setOf(
                Engine15I7CalibrationOrigin.LAB_TEST_ASSERTED,
                Engine15I7CalibrationOrigin.STRUCTURED_FIELD_TEST_ASSERTED
            ))
            require(!ownerSourceAuthenticated && !calibrationArtifactVerified &&
                !calibrationProtocolVerified && !thresholdIntensityVerified &&
                !workDoseAuthorized && !athleteResponseVerified &&
                !futureDistanceVerified && !executable &&
                !authorizedForProduction)
        }
    }
}

/**
 * I7 is an evidence intake and conflict-review layer, not a physiological
 * threshold estimator or a production workout intensity gate. Even syntactically
 * complete claimed lab records have no authenticated source bytes or testing
 * protocol verification in the present offline engine.
 */
internal object Engine15I7IntensityCalibrationReview {
    const val POLICY_ID = "engine15.i7.calibration-intake-shadow-20261117.1"
    private const val ADMINISTRATIVE_MAX_AGE_DAYS = 90L
    private val SHA256_FORMAT = Regex("[0-9a-f]{64}")

    fun inspect(
        source: Engine15IndependentVolumePlanner.Result,
        request: Engine15M5QualityPreviewRequest,
        sourceEvidence: Engine15I3QualityDurationInput,
        feasibilityInput: Engine15I5DistanceFeasibilityInput,
        input: Engine15I7CalibrationReviewInput
    ): Engine15I7CalibrationReviewDecision {
        fun defer(code: String, reason: String) =
            Engine15I7CalibrationReviewDecision.Deferred(code, reason)

        val prior = when (val inspected = Engine15I6SourceWorkLapPaceConsistency.inspect(
            source, request, sourceEvidence, feasibilityInput
        )) {
            is Engine15I6PaceConsistencyDecision.Deferred ->
                return defer("I6_" + inspected.reasonCode, inspected.explanation)
            is Engine15I6PaceConsistencyDecision.DescriptiveSourceConsistency -> inspected
        }

        val claim = input.claim ?: return defer(
            "CALIBRATION_CLAIM_MISSING",
            "No individual-specific test claim was supplied for independent review."
        )
        if (claim.assertedOwnerId != sourceEvidence.assertedOwnerId)
            return defer("CALIBRATION_OWNER_SCOPE_MISMATCH",
                "Claimed calibration owner is not the same asserted history scope.")
        if (claim.family != request.family)
            return defer("CALIBRATION_FAMILY_MISMATCH",
                "Calibration evidence needs an explicit matching quality-family label.")
        if (claim.origin !in setOf(
            Engine15I7CalibrationOrigin.LAB_TEST_ASSERTED,
            Engine15I7CalibrationOrigin.STRUCTURED_FIELD_TEST_ASSERTED
        ))
            return defer("CALIBRATION_ORIGIN_NOT_ELIGIBLE_FOR_REVIEW",
                "A device estimate or manual entry does not establish a reviewed test protocol.")

        val age = ChronoUnit.DAYS.between(claim.measuredOn, sourceEvidence.asOf)
        if (age !in 0L..ADMINISTRATIVE_MAX_AGE_DAYS)
            return defer("CALIBRATION_TEST_DATE_OUTSIDE_REVIEW_WINDOW",
                "The test date is future or outside the administrative review window.")
        val reference = claim.subjectSpecificSourceReference
        if (reference.isNullOrBlank() || reference.length > 256)
            return defer("CALIBRATION_SOURCE_REFERENCE_MISSING",
                "A bounded, nonblank subject-specific source reference is required.")
        val protocol = claim.testingProtocolReference
        if (protocol.isNullOrBlank() || protocol.length > 256)
            return defer("CALIBRATION_PROTOCOL_REFERENCE_MISSING",
                "A bounded, nonblank testing-protocol reference is required.")
        val fingerprint = claim.artifactSha256Claim
        if (fingerprint == null || !SHA256_FORMAT.matches(fingerprint))
            return defer("CALIBRATION_ARTIFACT_FINGERPRINT_INVALID",
                "Provide a lowercase SHA-256-format fingerprint; format alone cannot authenticate it.")

        val hr = claim.claimedThresholdHrBpm
            ?: return defer("CALIBRATION_THRESHOLD_HR_UNKNOWN",
                "No individual-specific claimed threshold heart rate was recorded.")
        if (hr !in 100..230)
            return defer("CALIBRATION_THRESHOLD_HR_NUMERICALLY_INVALID",
                "Claimed threshold HR is outside coarse numeric sanity bounds.")
        val pace = claim.claimedThresholdPaceSecondsPerKm
            ?: return defer("CALIBRATION_THRESHOLD_PACE_UNKNOWN",
                "No individual-specific claimed threshold pace was recorded.")
        if (!pace.isFinite() || pace !in 120.0..1200.0)
            return defer("CALIBRATION_THRESHOLD_PACE_NUMERICALLY_INVALID",
                "Claimed threshold pace is outside coarse numeric sanity bounds.")

        val rest = claim.assertedRestingHrBpm
        val max = claim.assertedMaximumHrBpm
        if (rest != null && rest !in 30..120 ||
            max != null && max !in 100..240 ||
            rest != null && rest >= hr ||
            max != null && hr > max ||
            rest != null && max != null && rest >= max)
            return defer("CALIBRATION_INTERNAL_HR_CONFLICT",
                "Asserted resting, maximum and threshold HR cannot be reconciled.")

        val display = input.displayHrSettings
        if (display != null) {
            if (display.method == HeartRateZoneMethod.LTHR_PERCENT &&
                display.lactateThresholdHr != null &&
                display.lactateThresholdHr != hr)
                return defer("DISPLAY_LTHR_CONTRADICTS_CALIBRATION",
                    "The displayed user-set LTHR disagrees with the new calibration claim.")
            if ((display.maximumHr != null && hr > display.maximumHr) ||
                (display.restingHr != null && hr <= display.restingHr))
                return defer("DISPLAY_HR_BOUNDS_CONTRADICT_CALIBRATION",
                    "The displayed HR bounds contradict the asserted threshold HR.")
        }

        // In particular, do NOT compare two caller-labelled quality sessions
        // with the claimed threshold HR/pace: source segment family and test
        // intensity have NOT been independently established.
        return Engine15I7CalibrationReviewDecision.ExternalQualificationRequired(
            policyId = POLICY_ID,
            originalI6 = prior,
            originalCalibrationClaim = claim,
            outstandingVerificationGaps = listOf(
                "CALIBRATION_SOURCE_BYTES_NOT_VERIFIED",
                "TEST_PROTOCOL_AND_DATE_NOT_INDEPENDENTLY_ATTESTED",
                "OWNER_AND_SOURCE_NAMESPACE_NOT_AUTHENTICATED",
                "HISTORICAL_WORK_LAPS_NOT_THRESHOLD_CLASSIFIED",
                "NO_APPROVED_DOSE_RECOVERY_OR_INTENSITY_TARGET",
                "NO_EXECUTABLE_OR_PRODUCTION_PLAN_AUTHORIZATION"
            ),
            ruleIds = listOf(
                "I6_I5_I4_I3_AND_M4B_M5B_M6B_RECOMPUTED",
                "CLAIMED_LAB_OR_FIELD_RECORD_METADATA_PRESENT",
                "CLAIM_DATE_REFERENCE_AND_HASH_FORMAT_VALIDATED_ONLY",
                "CLAIMED_HR_AND_PACE_NUMERICALLY_PLAUSIBLE",
                "OPTIONAL_DISPLAY_HR_CONFLICT_CHECK_ONLY",
                "EXTERNAL_INTENSITY_QUALIFICATION_REQUIRED",
                "NO_QUALITY_PRESCRIPTION_FROM_UNVERIFIED_CALIBRATION"
            )
        )
    }
}
