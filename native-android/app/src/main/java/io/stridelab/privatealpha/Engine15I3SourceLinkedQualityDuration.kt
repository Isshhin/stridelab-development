package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/** One asserted quality recording and its (possibly unavailable) detail. */
internal data class Engine15I3LinkedQualityObservation(
    val lapClaim: IndependentQualityLapClaim,
    val detail: ActivityDetail?
)

internal data class Engine15I3QualityDurationInput(
    val assertedOwnerId: String,
    val asOf: LocalDate,
    val zone: ZoneId,
    val plans: List<PlanSnapshot>,
    val sourceActivities: List<ActivityRecord>,
    val observations: List<Engine15I3LinkedQualityObservation>
) {
    init {
        require(assertedOwnerId.isNotBlank() && assertedOwnerId.length <= 256)
        require(plans.size <= 200 && sourceActivities.size <= 500)
        require(observations.size <= 10)
    }
}

internal sealed interface Engine15I3QualityDurationDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String
    ) : Engine15I3QualityDurationDecision

    /**
     * Reconciled lap durations do NOT authenticate work intensity, family,
     * recorded-owner namespace, athlete response or prescription authority.
     */
    data class DurationConsistent(
        val policyId: String,
        val originalM5B: Engine15M5QualityDecision.Candidate,
        val lapBindings: List<IndependentQualitySourceBindingDecision.LapDurationsReconciled>,
        val repeatedWorkDurationFloorSeconds: Int,
        val requestedWorkExposureSeconds: Int,
        val ruleIds: List<String>,
        val lapDurationsReconciled: Boolean = true,
        val ownerSourceNamespaceAuthenticated: Boolean = false,
        val qualityIntensityVerified: Boolean = false,
        val athleteResponseVerified: Boolean = false,
        val doseAuthorized: Boolean = false,
        val distanceReconciled: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15I3QualityDurationDecision {
        init {
            require(lapBindings.size == 2)
            require(lapBindings.map { it.activityId }.distinct().size == 2)
            require(lapBindings.map { it.sessionId }.distinct().size == 2)
            require(repeatedWorkDurationFloorSeconds > 0)
            require(requestedWorkExposureSeconds in 1..repeatedWorkDurationFloorSeconds)
            require(originalM5B.qualityPreviews.size == 1)
            require(ruleIds.distinct() == ruleIds)
            require(lapDurationsReconciled && !ownerSourceNamespaceAuthenticated &&
                !qualityIntensityVerified && !athleteResponseVerified &&
                !doseAuthorized && !distanceReconciled && !executable &&
                !authorizedForProduction)
        }
    }
}

/**
 * I3 recomputes M4B/M5B and source-binds TWO different recent recordings.
 * It never fills an unknown work dose or upgrades source-lap timings to
 * verified threshold/tempo intensity or an executable workout.
 */
internal object Engine15I3SourceLinkedQualityDuration {
    const val POLICY_ID = "engine15.i3.repeated-source-lap-duration-shadow-20261117.1"

    fun inspect(
        source: Engine15IndependentVolumePlanner.Result,
        qualityRequest: Engine15M5QualityPreviewRequest,
        input: Engine15I3QualityDurationInput
    ): Engine15I3QualityDurationDecision {
        fun defer(code: String, reason: String) =
            Engine15I3QualityDurationDecision.Deferred(code, reason)

        val m5b = when (val found =
            Engine15M5QualityPreviewComposer.compose(source, listOf(qualityRequest))
        ) {
            is Engine15M5QualityDecision.Deferred ->
                return defer("UPSTREAM_" + found.reasonCode, found.explanation)
            is Engine15M5QualityDecision.Candidate -> found
        }
        if (input.observations.size != 2)
            return defer("EXACTLY_TWO_RECORDINGS_REQUIRED",
                "Two separately linked recent recordings are required.")
        val claims = input.observations.map { it.lapClaim.evidence }
        if (claims.any { it.ownerId != input.assertedOwnerId })
            return defer("QUALITY_OWNER_SCOPE_CONFLICT",
                "Every observation must remain in one caller-asserted owner scope.")
        if (claims.any { it.family != qualityRequest.family })
            return defer("QUALITY_FAMILY_CLAIM_MISMATCH",
                "Each caller-asserted family must match the preview family.")
        if (claims.map { it.sourceRecordId }.distinct().size != 2)
            return defer("DUPLICATE_QUALITY_SOURCE",
                "Selecting two lap sets from one activity does not prove repetition.")
        if (claims.map { it.sessionId }.distinct().size != 2)
            return defer("DUPLICATE_QUALITY_PLAN_SESSION",
                "Both claims must name different linked planned sessions.")
        if (claims.map { it.performedOn }.distinct().size != 2)
            return defer("DUPLICATE_QUALITY_PERFORMED_DAY",
                "Both recordings must come from different local dates.")

        val ordered = input.observations.sortedWith(
            compareByDescending<Engine15I3LinkedQualityObservation> {
                it.lapClaim.evidence.performedOn
            }.thenBy { it.lapClaim.evidence.sourceRecordId }
        )
        val bindings = mutableListOf<
            IndependentQualitySourceBindingDecision.LapDurationsReconciled>()
        for (record in ordered) {
            when (val bound = IndependentQualitySourceBinding.inspect(
                claim = record.lapClaim,
                plans = input.plans,
                sourceActivities = input.sourceActivities,
                detail = record.detail,
                asOf = input.asOf,
                zone = input.zone
            )) {
                is IndependentQualitySourceBindingDecision.Deferred ->
                    return defer("SOURCE_" + bound.ruleId, bound.explanation)
                is IndependentQualitySourceBindingDecision.LapDurationsReconciled ->
                    bindings += bound
            }
        }

        // Floor the smaller observed lap total, never round fractional
        // seconds up to a larger demonstrated work dose.
        val repeated = floor(bindings.minOf { it.recordedWorkSeconds }).toInt()
        if (repeated <= 0)
            return defer("RECORDED_WORK_DURATION_TOO_SMALL",
                "No positive whole seconds remain from recorded source laps.")
        val claimed = qualityRequest.fitnessInput.confirmedRecentWorkSeconds
            ?: return defer("REQUESTED_WORK_EXPOSURE_UNKNOWN",
                "An explicit work-dose assertion is required; never autofill from laps.")
        if (claimed > repeated)
            return defer("REQUESTED_WORK_EXCEEDS_SOURCE_LAPS",
                "Claimed work dose exceeds the smaller source-linked lap duration.")
        val dose = qualityRequest.fitnessInput
        val permitted = minOf(
            requireNotNull(dose.approvedWorkCeilingSeconds),
            claimed + dose.approvedIncreaseSeconds
        )
        val preview = m5b.qualityPreviews.single()
        if (preview.workSeconds > permitted || dose.availableSessionSeconds?.let {
                preview.totalSeconds > it
            } == true)
            return defer("REQUESTED_PREVIEW_EXCEEDS_EXPLICIT_BOUND",
                "Quality preview exceeds the original caller-reviewed dose or explicit time limit.")

        return Engine15I3QualityDurationDecision.DurationConsistent(
            policyId = POLICY_ID,
            originalM5B = m5b,
            lapBindings = bindings,
            repeatedWorkDurationFloorSeconds = repeated,
            requestedWorkExposureSeconds = claimed,
            ruleIds = listOf(
                "M4B_M5A_M5B_RECOMPUTED",
                "TWO_DISTINCT_LINKED_SOURCE_LAP_DURATIONS",
                "REPEATED_WORK_DURATION_IS_CONSERVATIVE_FLOOR",
                "REQUESTED_DOSE_NOT_GREATER_THAN_SOURCE_LAP_DURATION",
                "QUALITY_FAMILY_AND_OWNER_SCOPE_CALLER_ASSERTED",
                "NO_VERIFIED_INTENSITY_ATHLETE_RESPONSE_OR_SOURCE_OWNER",
                "NO_AUTO_EXPOSURE_FILL_NO_PRODUCTION_OR_EXECUTION"
            )
        )
    }
}
