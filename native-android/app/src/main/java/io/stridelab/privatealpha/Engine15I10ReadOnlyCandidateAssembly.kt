package io.stridelab.privatealpha

import java.time.temporal.ChronoUnit
import kotlin.math.abs

internal data class Engine15I10QualityReviewInput(
    val request: Engine15M5QualityPreviewRequest,
    val sourceEvidence: Engine15I3QualityDurationInput,
    val feasibilityInput: Engine15I5DistanceFeasibilityInput,
    val calibrationInput: Engine15I7CalibrationReviewInput,
    val artifactBytes: ByteArray?
)

internal data class Engine15I10ReadOnlyCandidateInput(
    val source: Engine15IndependentVolumePlanner.Result,
    val baseInput: Engine15I9BasePlanAssemblyInput,
    val qualityInput: Engine15I10QualityReviewInput? = null
)

internal sealed interface Engine15I10ReadOnlyCandidateDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String
    ) : Engine15I10ReadOnlyCandidateDecision

    data class Candidate(
        val policyId: String,
        val base: Engine15I9BasePlanAssemblyDecision.ReadOnlyBasePreview,
        val qualityReview: Engine15I8ArtifactIntegrityDecision.ArtifactConsistentForExternalReview?,
        val qualityAlternative: Engine15M6QualityOpportunity?,
        val ruleIds: List<String>,
        val qualityIncludedInBaseMileage: Boolean = false,
        val qualityDistanceReconciled: Boolean = false,
        val qualityScheduled: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15I10ReadOnlyCandidateDecision {
        init {
            require(base.weeks.isNotEmpty())
            require((qualityReview == null) == (qualityAlternative == null))
            require(ruleIds.distinct() == ruleIds)
            require(!qualityIncludedInBaseMileage && !qualityDistanceReconciled &&
                !qualityScheduled && !executable && !authorizedForProduction)
            qualityAlternative?.let {
                require(it.existingRun.role == Engine15M5RunRole.EASY)
                require(!it.scheduledQualityWorkout && !it.executable)
            }
        }
    }
}

/**
 * Recomputes base and quality branches from one source input. The dated
 * EASY/LONG budget stays separate from an I8-reviewed timed alternative.
 */
internal object Engine15I10ReadOnlyCandidateAssembly {
    const val POLICY_ID = "engine15.i10.readonly-candidate-assembly-shadow-20261117.1"

    fun assemble(
        input: Engine15I10ReadOnlyCandidateInput
    ): Engine15I10ReadOnlyCandidateDecision {
        fun defer(code: String, explanation: String) =
            Engine15I10ReadOnlyCandidateDecision.Deferred(code, explanation)

        val base = when (val result = Engine15I9ReadOnlyBasePlanAssembly.assemble(
            input.source, input.baseInput
        )) {
            is Engine15I9BasePlanAssemblyDecision.Deferred ->
                return defer("BASE_" + result.reasonCode, result.explanation)
            is Engine15I9BasePlanAssemblyDecision.ReadOnlyBasePreview -> result
        }
        val qualityInput = input.qualityInput
            ?: return candidate(base, null, null)
        val qualityReview = when (val result = Engine15I8CalibrationArtifactIntegrity.inspect(
            input.source,
            qualityInput.request,
            qualityInput.sourceEvidence,
            qualityInput.feasibilityInput,
            qualityInput.calibrationInput,
            qualityInput.artifactBytes
        )) {
            is Engine15I8ArtifactIntegrityDecision.Deferred ->
                return defer("QUALITY_" + result.reasonCode, result.explanation)
            is Engine15I8ArtifactIntegrityDecision.ArtifactConsistentForExternalReview -> result
        }

        val m6b = qualityReview.originalI7.originalI6.originalI5.originalM6B
        if (base.weeks.size != m6b.baseCalendar.size)
            return defer("BASE_QUALITY_CALENDAR_MISMATCH",
                "I9 and the I8 quality review cover different calendar horizons.")

        for ((baseWeek, qualityWeek) in base.weeks.zip(m6b.baseCalendar)) {
            if (baseWeek.weekNumber != qualityWeek.weekNumber ||
                abs(baseWeek.proposedKm - qualityWeek.budgetKm) > 1e-7 ||
                baseWeek.runs.size != qualityWeek.runs.size)
                return defer("BASE_QUALITY_CALENDAR_MISMATCH",
                    "I9 and the I8 quality review disagree on a weekly budget or run count.")
            val runsBySlot = qualityWeek.runs.associateBy { it.slot.slotNumber }
            if (runsBySlot.size != qualityWeek.runs.size ||
                baseWeek.runs.any { run ->
                    val other = runsBySlot[run.slotNumber]
                    other == null || other.date != run.date || other.role != run.role ||
                        abs(other.distanceKm - run.distanceKm) > 1e-7
                })
                return defer("BASE_QUALITY_CALENDAR_MISMATCH",
                    "I9 and the I8 quality review do not identify the same dated running slots.")
        }

        val opportunity = qualityReview.originalI7.originalI6.originalI5.selectedOpportunity
        val baseRun = base.weeks.singleOrNull {
            it.weekNumber == opportunity.weekNumber
        }?.runs?.singleOrNull { it.slotNumber == opportunity.slotNumber }
            ?: return defer("QUALITY_BASE_SLOT_MISSING",
                "The reviewed quality alternative has no corresponding I9 base slot.")
        if (baseRun.role != Engine15M5RunRole.EASY ||
            baseRun.date != opportunity.proposedDate ||
            abs(baseRun.distanceKm - opportunity.existingRun.distanceKm) > 1e-7 ||
            abs(baseRun.distanceKm - opportunity.preview.originalEasyDistanceKm) > 1e-7)
            return defer("QUALITY_BASE_SLOT_MISMATCH",
                "The quality alternative does not match the original I9 EASY slot.")

        val externalSessions = base.originalM6D.originalConflictReview.externalSessions
        if (externalSessions.any { it.date == opportunity.proposedDate })
            return defer("QUALITY_EXTERNAL_SAME_DAY_CONFLICT",
                "An external session already occupies the proposed quality day.")
        val hardSpacingDays = maxOf(
            input.baseInput.interferenceReview.minDaysFromLongOrQualityForExternalHard,
            qualityInput.feasibilityInput.calendar.policy.minCalendarDaysFromKnownOtherHard
        )
        if (externalSessions.any { session ->
                session.hardLowerOrFull && abs(ChronoUnit.DAYS.between(
                    session.date, opportunity.proposedDate
                )) < hardSpacingDays
            })
            return defer("QUALITY_EXTERNAL_HARD_TOO_CLOSE",
                "A reported lower/full-body hard session is too close to the quality alternative.")

        return candidate(base, qualityReview, opportunity)
    }

    private fun candidate(
        base: Engine15I9BasePlanAssemblyDecision.ReadOnlyBasePreview,
        review: Engine15I8ArtifactIntegrityDecision.ArtifactConsistentForExternalReview?,
        opportunity: Engine15M6QualityOpportunity?
    ) = Engine15I10ReadOnlyCandidateDecision.Candidate(
        policyId = POLICY_ID,
        base = base,
        qualityReview = review,
        qualityAlternative = opportunity,
        ruleIds = listOf(
            "I9_READ_ONLY_BASE_RETAINED",
            if (review == null) "NO_QUALITY_REVIEW_SUPPLIED"
            else "I8_EXTERNAL_REVIEW_PACKET_RETAINED",
            if (opportunity == null) "BASE_ONLY_PREVIEW"
            else "QUALITY_IS_UNSCHEDULED_ALTERNATIVE_ON_MATCHED_EASY_SLOT",
            if (opportunity == null) "NO_QUALITY_CROSS_DISCIPLINE_CHECK"
            else "ASSERTED_EXTERNAL_CALENDAR_RECHECKED_FOR_QUALITY",
            "QUALITY_DISTANCE_NOT_INCLUDED_OR_RECONCILED",
            "NO_EXECUTION_OR_PRODUCTION_AUTHORIZATION"
        )
    )
}
