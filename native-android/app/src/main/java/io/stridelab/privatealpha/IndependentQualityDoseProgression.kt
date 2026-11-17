package io.stridelab.privatealpha

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * M9b research-only session history. Recorded work duration, linked identity,
 * owner scope and reported tolerance are CALLER ASSERTIONS. These cannot be
 * promoted to independently verified source evidence.
 */
internal data class IndependentQualityWorkObservation(
    val assertedOwnerId: String,
    val sessionId: String,
    val sourceRecordId: String,
    val performedOn: LocalDate,
    val family: IndependentQualityFamily,
    val recordedWorkSeconds: Int?,
    val reportedResponse: IndependentWeekResponse
) {
    init {
        require(assertedOwnerId.isNotBlank() && assertedOwnerId.length <= 256)
        require(sessionId.isNotBlank() && sessionId.length <= 256)
        require(sourceRecordId.isNotBlank() && sourceRecordId.length <= 256)
        require(recordedWorkSeconds == null || recordedWorkSeconds in 1..14400)
    }
}

/** Each prospective slot must explicitly carry reviewed dose constraints. */
internal data class IndependentQualityDoseSlotRequest(
    val isoDay: Int,
    val family: IndependentQualityFamily,
    /** Null means the athlete supplied no time cap; it is not missing-dose evidence. */
    val availableSessionSeconds: Int? = null,
    val approvedSessionWorkCeilingSeconds: Int?,
    val explicitlyApprovedIncreaseSeconds: Int = 0,
    val maxReviewedSingleStepIncreaseSeconds: Int? = null,
    val increaseReviewReference: String? = null,
    val qualityAllowed: Boolean = true
) {
    init {
        require(isoDay in 1..7)
        require(availableSessionSeconds == null || availableSessionSeconds >= 0)
        require(approvedSessionWorkCeilingSeconds == null ||
            approvedSessionWorkCeilingSeconds in 0..14400)
        require(explicitlyApprovedIncreaseSeconds in 0..14400)
        require(maxReviewedSingleStepIncreaseSeconds == null ||
            maxReviewedSingleStepIncreaseSeconds in 0..14400)
        require(increaseReviewReference == null || increaseReviewReference.length <= 256)
    }
}

internal data class IndependentQualityDoseReviewInput(
    val assertedOwnerId: String,
    val planningMonday: LocalDate,
    /** Only the immediately upcoming week has observed prior-dose evidence. */
    val requestedWeek: Int,
    val requestedSlots: List<IndependentQualityDoseSlotRequest>,
    val observedHistory: List<IndependentQualityWorkObservation>,
    val approvedWeeklyQualityWorkCeilingSeconds: Int?,
    val weeklyReviewReference: String?,
    val explicitRecoveryOrReturnHold: Boolean = false,
    /** Explicit coverage assertions, never inferred from the presence of two records. */
    val completeQualityWeekMondays: Set<LocalDate> = emptySet()
) {
    init {
        require(assertedOwnerId.isNotBlank() && assertedOwnerId.length <= 256)
        require(planningMonday.dayOfWeek == DayOfWeek.MONDAY)
        require(requestedWeek in 1..52)
        require(requestedSlots.size <= 2)
        require(approvedWeeklyQualityWorkCeilingSeconds == null ||
            approvedWeeklyQualityWorkCeilingSeconds in 0..28800)
        require(weeklyReviewReference == null || weeklyReviewReference.length <= 256)
        require(completeQualityWeekMondays.all { it.dayOfWeek == DayOfWeek.MONDAY && it < planningMonday })
    }
}

internal data class IndependentReviewedQualityOpportunity(
    val isoDay: Int,
    val family: IndependentQualityFamily,
    val assertedRepeatedWorkSeconds: Int,
    val approvedIncreaseSeconds: Int,
    val selected: IndependentQualityDayPreview,
    val provenanceRuleIds: List<String>
) {
    init {
        require(isoDay in 1..7 && selected.isoDay == isoDay && selected.week == 1)
        require(family == selected.family)
        require(assertedRepeatedWorkSeconds > 0 && approvedIncreaseSeconds >= 0)
        require(selected.workSeconds <= assertedRepeatedWorkSeconds + approvedIncreaseSeconds)
        require(provenanceRuleIds.distinct().size == provenanceRuleIds.size)
    }
    val sourceVerified: Boolean get() = false
    val executable: Boolean get() = false
}

internal sealed interface IndependentQualityDoseReviewDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentQualityDoseReviewDecision

    data class Preview(
        val opportunities: List<IndependentReviewedQualityOpportunity>,
        val totalProposedWeeklyWorkSeconds: Int,
        val approvedWeeklyWorkCeilingSeconds: Int,
        val ruleIds: List<String>,
        val sourceEvidenceVerified: Boolean = false,
        val distanceReconciled: Boolean = false,
        val intensityVerified: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentQualityDoseReviewDecision {
        init {
            require(opportunities.isNotEmpty() && opportunities.size <= 2)
            require(opportunities.map { it.isoDay }.distinct().size == opportunities.size)
            require(totalProposedWeeklyWorkSeconds == opportunities.sumOf {
                it.selected.workSeconds
            })
            require(totalProposedWeeklyWorkSeconds in 1..approvedWeeklyWorkCeilingSeconds)
            require(ruleIds.distinct().size == ruleIds.size)
            require(!sourceEvidenceVerified && !distanceReconciled &&
                !intensityVerified && !authorizedForProduction)
        }
    }
}

/**
 * M9b adds a quality-dose progression *review boundary* on top of the
 * M6a calendar, M6b spacing and M2 structure selection:
 *
 * - Never project completed exposure into future plan weeks.
 * - Require two distinct recent, same-family work-dose claims, not a single
 *   peak or total running minutes. Unknown/difficult reports defer.
 * - Require separately reviewed per-session AND combined weekly dose caps.
 * - An increase requires an explicit external approval reference and maximum
 *   step cap; no increase occurs merely because a week number advanced.
 * - Preserve original M6a EASY distances: previews replace those slots only
 *   if later reconciled with M9a; no double-counted quality distance.
 *
 * This does NOT prove athlete eligibility, clinical safety, source truth, HR
 * or threshold pace, nor does it create a saved/executable workout.
 */
internal object IndependentQualityDoseProgression {
    const val POLICY_ID = "stridelab.engine15.m9b-quality-dose-progression-review-v1"
    private const val LOOKBACK_DAYS = 28L

    private fun defer(rule: String, explanation: String) =
        IndependentQualityDoseReviewDecision.Deferred(rule, explanation)

    fun review(
        base: IndependentConstrainedMultiweekDecision.Candidate,
        spacing: IndependentQualitySpacingDecision.Candidate,
        request: IndependentQualityDoseReviewInput
    ): IndependentQualityDoseReviewDecision {
        if (request.requestedWeek != 1)
            return defer("FUTURE_WEEK_DOSE_NOT_SUPPORTED",
                "A future training week cannot borrow completed-dose evidence not yet observed.")
        if (request.explicitRecoveryOrReturnHold)
            return defer("RECOVERY_OR_RETURN_HOLD",
                "Recovery or return-to-running requires a separate quality review.")
        if (request.requestedSlots.isEmpty())
            return defer("NO_QUALITY_REQUESTED", "No quality slots were requested.")
        if (request.requestedSlots.map { it.isoDay }.distinct().size != request.requestedSlots.size)
            return defer("DUPLICATE_REQUESTED_QUALITY_DAY",
                "A running day cannot receive two different quality proposals.")
        if (request.observedHistory.any { it.assertedOwnerId != request.assertedOwnerId })
            return defer("HISTORY_OWNER_SCOPE_CONFLICT",
                "Quality exposure from different owner scopes cannot be combined.")
        if (request.observedHistory.any { it.performedOn >= request.planningMonday })
            return defer("FUTURE_OR_CURRENT_HISTORY_RECORD",
                "A future or upcoming quality session is not completed training evidence.")
        if (request.observedHistory.groupBy { it.sessionId }.any { it.value.size > 1 } ||
            request.observedHistory.groupBy { it.sourceRecordId }.any { it.value.size > 1 })
            return defer("DUPLICATE_QUALITY_SOURCE_OR_SESSION",
                "A source or planned session appears more than once and needs attribution review.")
        val weeklyCeiling = request.approvedWeeklyQualityWorkCeilingSeconds
            ?: return defer("WEEKLY_QUALITY_CEILING_UNKNOWN",
                "A separately reviewed total weekly work-dose ceiling is required.")
        if (weeklyCeiling <= 0 || request.weeklyReviewReference.isNullOrBlank())
            return defer("WEEKLY_QUALITY_REVIEW_REQUIRED",
                "Weekly work-dose budget must be positive and carry a review reference.")
        val firstWeek = base.baseWorkouts.weeks.singleOrNull { it.week == 1 }
            ?: return defer("M6A_FIRST_WEEK_UNAVAILABLE", "The first M6a week is unavailable.")
        val mileage = base.mileage.weeks.singleOrNull { it.week == 1 }
            ?: return defer("M6A_FIRST_WEEK_UNAVAILABLE", "The source mileage week is unavailable.")
        if (firstWeek.recoveryWeek || mileage.recoveryWeek)
            return defer("RECOVERY_WEEK_QUALITY_HOLD",
                "No quality candidate is proposed on an explicitly lighter week.")
        val selectedWeek = spacing.weeks.singleOrNull { it.week == 1 }
            ?: return defer("M6B_SPACING_MISSING", "No M6b quality-spacing decision exists for the first week.")
        if (selectedWeek.recoveryWeek)
            return defer("RECOVERY_WEEK_QUALITY_HOLD", "Quality spacing is held during recovery.")
        val output = mutableListOf<IndependentReviewedQualityOpportunity>()
        for (slot in request.requestedSlots.sortedBy { it.isoDay }) {
            if (!slot.qualityAllowed)
                return defer("QUALITY_NOT_ALLOWED",
                    "The requested hard-work family/day has not been authorized for review.")
            val scheduled = firstWeek.sessions.singleOrNull { it.isoDay == slot.isoDay }
                ?: return defer("RUNNING_DAY_NOT_FOUND", "Quality must replace a scheduled running day.")
            if (scheduled.kind != IndependentRunSessionKind.EASY)
                return defer("LONG_RUN_QUALITY_CONFLICT", "The designated long run cannot become a quality session.")
            val allowed = selectedWeek.opportunities.singleOrNull { it.isoDay == slot.isoDay }
                ?: return defer("M6B_QUALITY_SLOT_NOT_SELECTED",
                    "Quality is allowed only on an explicitly selected M6b day.")
            if (kotlin.math.abs(allowed.preexistingEasyRunKm - scheduled.allocatedKilometers) > 0.00001)
                return defer("M6A_M6B_DISTANCE_MISMATCH",
                    "M6b spacing does not reference the original M6a easy-run budget.")
            val sourceDistance = mileage.dayAllocations.singleOrNull { it.isoDay == slot.isoDay }
                ?: return defer("M6A_DAY_MISSING", "The M6a mileage day is unavailable.")
            if (kotlin.math.abs(sourceDistance.kilometers - scheduled.allocatedKilometers) > 0.00001)
                return defer("M6A_WORKOUT_DISTANCE_MISMATCH",
                    "M6a mileage and typed workout disagree.")

            val latest = request.observedHistory.filter {
                it.family == slot.family &&
                    ChronoUnit.DAYS.between(it.performedOn, request.planningMonday) in 1L..LOOKBACK_DAYS
            }.sortedWith(compareByDescending<IndependentQualityWorkObservation> {
                it.performedOn
            }.thenBy { it.sessionId }).take(2)
            if (latest.size != 2)
                return defer("TWO_RECENT_QUALITY_EXPOSURES_REQUIRED",
                    "Two distinct recent same-family completed-dose claims are required.")
            if (latest[0].performedOn == latest[1].performedOn)
                return defer("SAME_DAY_QUALITY_CLAIMS_NEED_REVIEW",
                    "Two records on one day cannot establish repeated comparable exposure.")
            if (latest.any { it.recordedWorkSeconds == null })
                return defer("RECENT_QUALITY_WORK_DURATION_UNKNOWN",
                    "Do not infer recorded work time from elapsed activity or prescribed steps.")
            if (latest.any { it.reportedResponse == IndependentWeekResponse.UNKNOWN })
                return defer("QUALITY_TOLERANCE_UNKNOWN",
                    "Reported completed duration does not establish a tolerated work dose.")
            if (latest.any { it.reportedResponse == IndependentWeekResponse.DIFFICULT })
                return defer("QUALITY_RESPONSE_DIFFICULT_REVIEW",
                    "Difficult reported quality exposure requires a separate recovery decision.")
            val repeated = latest.minOf { requireNotNull(it.recordedWorkSeconds) }
            val perSession = slot.approvedSessionWorkCeilingSeconds
                ?: return defer("SESSION_QUALITY_CEILING_UNKNOWN",
                    "The selected family needs an explicit session work-dose ceiling.")
            if (perSession <= 0)
                return defer("SESSION_QUALITY_CEILING_INVALID",
                    "A positive reviewed session work-dose ceiling is required.")
            if (repeated > perSession)
                return defer("OBSERVED_DOSE_EXCEEDS_APPROVED_CEILING",
                    "Prior asserted exposure exceeds the reviewed ceiling; an independent reduction decision is needed.")
            if (slot.explicitlyApprovedIncreaseSeconds > 0) {
                if (slot.increaseReviewReference.isNullOrBlank())
                    return defer("DOSE_INCREASE_REVIEW_MISSING",
                        "No additional quality dose may be proposed without a review reference.")
                val bound = slot.maxReviewedSingleStepIncreaseSeconds
                    ?: return defer("MAX_SINGLE_STEP_INCREASE_UNKNOWN",
                        "A separately supplied maximum allowed dose increase is required.")
                if (slot.explicitlyApprovedIncreaseSeconds > bound)
                    return defer("APPROVED_INCREASE_EXCEEDS_STEP_LIMIT",
                        "A claimed increase cannot exceed the independently reviewed step limit.")
            }
            val candidate = when (val result = IndependentQualityFitnessAdapter.generate(
                slot.family,
                IndependentQualityFitnessInput(
                    confirmedRecentWorkSeconds = repeated,
                    approvedWorkCeilingSeconds = perSession,
                    approvedIncreaseSeconds = slot.explicitlyApprovedIncreaseSeconds,
                    availableSessionSeconds = slot.availableSessionSeconds,
                    qualityAuthorized = slot.qualityAllowed,
                    recoveryOrReturnHold = request.explicitRecoveryOrReturnHold
                )
            )) {
                is IndependentQualityFitnessDecision.Deferred ->
                    return defer("M2_" + result.ruleId, result.explanation)
                is IndependentQualityFitnessDecision.Candidate -> result
            }
            if (candidate.workSeconds > repeated + slot.explicitlyApprovedIncreaseSeconds ||
                candidate.workSeconds > perSession ||
                slot.availableSessionSeconds?.let { candidate.totalSeconds > it } == true)
                return defer("QUALITY_DOSE_POLICY_INCONSISTENT",
                    "M2 structure exceeds the reviewed session dose or duration.")
            val preview = IndependentQualityDayPreview(
                week = 1,
                isoDay = slot.isoDay,
                baseAllocatedKilometers = scheduled.allocatedKilometers,
                family = slot.family,
                qualityWorkout = candidate.workout,
                totalSeconds = candidate.totalSeconds,
                workSeconds = candidate.workSeconds,
                ruleIds = (candidate.ruleIds + listOf(
                    "TWO_RECENT_CALLER_ASSERTED_DOSES",
                    "M6B_SPACING_AND_M6A_DISTANCE_RETAINED",
                    "NEXT_WEEK_ONLY_NO_FUTURE_DOSE_ASSUMPTION",
                    "NO_QUALITY_SOURCE_AUTHENTICATION",
                    "DISTANCE_NOT_RECONCILED_WITH_M9A"
                )).distinct()
            )
            output += IndependentReviewedQualityOpportunity(
                isoDay = slot.isoDay,
                family = slot.family,
                assertedRepeatedWorkSeconds = repeated,
                approvedIncreaseSeconds = slot.explicitlyApprovedIncreaseSeconds,
                selected = preview,
                provenanceRuleIds = listOf(
                    "ACTIVITY_AND_TOLERANCE_CALLER_ASSERTED",
                    "EXTERNAL_APPROVAL_NOT_SOURCE_VERIFIED",
                    "NOT_AN_EXECUTABLE_TRAINING_PRESCRIPTION"
                )
            )
        }
        val total = output.sumOf { it.selected.workSeconds }
        if (total > weeklyCeiling)
            return defer("WEEKLY_QUALITY_WORK_CEILING_EXCEEDED",
                "Combined proposed work doses exceed the independently supplied weekly ceiling.")
        val recentWeeks = request.completeQualityWeekMondays.filter {
            ChronoUnit.DAYS.between(it, request.planningMonday) in 7L..LOOKBACK_DAYS
        }.sortedDescending().take(2)
        if (recentWeeks.size != 2 || recentWeeks[0] != request.planningMonday.minusWeeks(1) || recentWeeks[1].plusWeeks(1) != recentWeeks[0])
            return defer("QUALITY_WEEK_COVERAGE_UNKNOWN",
                "Two consecutive recent complete quality weeks must be explicitly reviewed; two sessions do not establish weekly exposure.")
        for (monday in recentWeeks) {
            val history = request.observedHistory.filter { it.performedOn in monday..monday.plusDays(6) }
            if (history.any { it.recordedWorkSeconds == null || it.reportedResponse != IndependentWeekResponse.TOLERATED })
                return defer("WEEKLY_QUALITY_RESPONSE_UNRESOLVED", "All quality exposure in the reviewed weeks needs known duration and tolerated response.")
            for (family in output.map { it.family }.distinct()) {
                val prior = history.filter { it.family == family }
                val next = output.filter { it.family == family }
                if (next.size > prior.map { it.performedOn }.distinct().size)
                    return defer("QUALITY_FREQUENCY_INCREASE_REVIEW",
                        "Repeating a session does not authorize more weekly sessions of that family. Review frequency separately.")
                if (next.sumOf { it.selected.workSeconds } > prior.sumOf { requireNotNull(it.recordedWorkSeconds) } + next.sumOf { it.approvedIncreaseSeconds })
                    return defer("QUALITY_WEEKLY_EXPOSURE_INCREASE_REVIEW", "Combined work exceeds demonstrated weekly exposure plus separately reviewed increments.")
            }
        }
        return IndependentQualityDoseReviewDecision.Preview(
            opportunities = output,
            totalProposedWeeklyWorkSeconds = total,
            approvedWeeklyWorkCeilingSeconds = weeklyCeiling,
            ruleIds = listOf(
                "NEXT_WEEK_ONLY",
                "TWO_RECENT_DOSES_PER_FAMILY_ASSERTED_NOT_AUTHENTICATED",
                "SESSION_AND_WEEKLY_WORK_CEILINGS_CHECKED",
                "EXPLICIT_INCREMENT_ONLY",
                "NO_AUTOMATIC_EFFORT_OR_VOLUME_PROGRESSION",
                "M9A_DISTANCE_AND_INTENSITY_VERIFICATION_PENDING",
                "NO_PRODUCTION_PLAN_MUTATION"
            )
        )
    }
}
