package io.stridelab.privatealpha

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Explicit distance claims for each chronological timed workout phase.
 * These are caller assertions, not measurements or inferred pace evidence.
 */
internal data class IndependentWeeklyQualityDistanceClaim(
    val isoDay: Int,
    val segmentDistancesMeters: List<Double>,
    val reviewReference: String?
) {
    init {
        require(isoDay in 1..7)
        require(segmentDistancesMeters.size <= 400)
        require(reviewReference == null || reviewReference.length <= 256)
    }
}

/**
 * Other-disciplines calendar is externally reviewed and limited to activities
 * already classified as HARD by a separate reviewer. This does not infer
 * muscle fatigue or assign physiological load from training labels.
 */
internal data class IndependentWeeklyProgramReviewInput(
    val planningMonday: LocalDate,
    val qualityDistances: List<IndependentWeeklyQualityDistanceClaim> = emptyList(),
    val otherDisciplineHardDates: Set<LocalDate> = emptySet(),
    val otherDisciplineCalendarReviewed: Boolean,
    val reviewedMinimumHardDayGap: Int = 2
) {
    init {
        require(planningMonday.dayOfWeek == DayOfWeek.MONDAY)
        require(qualityDistances.size <= 2)
        require(reviewedMinimumHardDayGap in 1..3)
    }
}

internal enum class IndependentProgramDayRole {
    EASY_BASE,
    LONG_BASE,
    QUALITY_REPLACEMENT_PREVIEW
}

internal data class IndependentProgramDayReview(
    val isoDay: Int,
    val originallyAllocatedKm: Double,
    val role: IndependentProgramDayRole,
    val qualityFamily: IndependentQualityFamily? = null,
    val qualityWorkSeconds: Int? = null,
    val timeToDistanceCheck: IndependentTimedDistanceDecision.MathematicallyReconciled? = null
) {
    init {
        require(isoDay in 1..7)
        require(originallyAllocatedKm.isFinite() && originallyAllocatedKm > 0.0)
        if (role == IndependentProgramDayRole.QUALITY_REPLACEMENT_PREVIEW) {
            require(qualityFamily != null && qualityWorkSeconds != null &&
                qualityWorkSeconds > 0 && timeToDistanceCheck != null &&
                timeToDistanceCheck.isoDay == isoDay &&
                abs(timeToDistanceCheck.sessionDistanceKm - originallyAllocatedKm) < 0.00001)
        } else {
            require(qualityFamily == null && qualityWorkSeconds == null &&
                timeToDistanceCheck == null)
        }
    }
    val executable: Boolean get() = false
}

internal sealed interface IndependentWeeklyProgramReviewDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentWeeklyProgramReviewDecision

    data class Preview(
        val monday: LocalDate,
        val weeklyKmUnchanged: Double,
        val days: List<IndependentProgramDayReview>,
        val qualityWorkSeconds: Int,
        val reviewedOtherDisciplineHardDates: Set<LocalDate>,
        val futureWeeksRemainBaseOnly: List<Int>,
        val ruleIds: List<String>,
        val segmentDistanceArithmeticChecked: Boolean,
        val ownerAndSourceProvenanceVerified: Boolean = false,
        val qualityIntensityVerified: Boolean = false,
        val otherDisciplineFatigueVerified: Boolean = false,
        val authorizedForPlanMutation: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentWeeklyProgramReviewDecision {
        init {
            require(monday.dayOfWeek == DayOfWeek.MONDAY)
            require(weeklyKmUnchanged.isFinite() && weeklyKmUnchanged > 0.0)
            require(days.isNotEmpty())
            require(days.map { it.isoDay }.distinct().size == days.size)
            require(days.zipWithNext().all { (a, b) -> a.isoDay < b.isoDay })
            require(abs(days.sumOf { it.originallyAllocatedKm } - weeklyKmUnchanged) < 0.00001)
            require(qualityWorkSeconds == days.sumOf { it.qualityWorkSeconds ?: 0 })
            require(qualityWorkSeconds >= 0)
            require(segmentDistanceArithmeticChecked ==
                days.filter { it.role == IndependentProgramDayRole.QUALITY_REPLACEMENT_PREVIEW }
                    .all { it.timeToDistanceCheck != null })
            require(futureWeeksRemainBaseOnly.distinct().size == futureWeeksRemainBaseOnly.size)
            require(ruleIds.distinct().size == ruleIds.size)
            require(!ownerAndSourceProvenanceVerified && !qualityIntensityVerified &&
                !otherDisciplineFatigueVerified && !authorizedForPlanMutation &&
                !authorizedForProduction)
        }

        val qualityDays: List<Int> get() =
            days.filter { it.role == IndependentProgramDayRole.QUALITY_REPLACEMENT_PREVIEW }
                .map { it.isoDay }
        val longDay: Int? get() =
            days.singleOrNull { it.role == IndependentProgramDayRole.LONG_BASE }?.isoDay
        val totalQualityReplacements: Int get() = qualityDays.size
    }
}

/**
 * M9c audits ONE complete week under the M6a distance budget. It does not
 * create an executable training plan. Quality preview substitutes for an
 * existing EASY slot only after M9b work-dose checks and M9a arithmetic
 * reconciliation. It can also preserve an all-EASY/LONG base week.
 *
 * The upcoming week is the only week for which observed quality exposure
 * exists. The remaining M6a future weeks are not falsely qualified.
 */
internal object IndependentWeeklyProgramReview {
    const val POLICY_ID = "stridelab.engine15.m9c-weekly-program-audit-v1"

    private fun defer(rule: String, reason: String) =
        IndependentWeeklyProgramReviewDecision.Deferred(rule, reason)

    fun inspect(
        base: IndependentConstrainedMultiweekDecision.Candidate,
        spacing: IndependentQualitySpacingDecision.Candidate,
        dose: IndependentQualityDoseReviewDecision?,
        input: IndependentWeeklyProgramReviewInput
    ): IndependentWeeklyProgramReviewDecision {
        if (!input.otherDisciplineCalendarReviewed)
            return defer("OTHER_DISCIPLINE_CALENDAR_UNREVIEWED",
                "An explicit review of high-load activity days or a reviewed empty calendar is needed.")
        val firstAllowed = input.planningMonday.minusDays(3)
        val lastAllowed = input.planningMonday.plusDays(9)
        if (input.otherDisciplineHardDates.any { it < firstAllowed || it > lastAllowed })
            return defer("EXTERNAL_HARD_DATE_OUTSIDE_WINDOW",
                "The reviewed high-load calendar may only cover three days before and after the training week.")

        val week = base.baseWorkouts.weeks.singleOrNull { it.week == 1 }
            ?: return defer("M6A_WEEK_ONE_MISSING", "M6a has no initial workout week.")
        val distance = base.mileage.weeks.singleOrNull { it.week == 1 }
            ?: return defer("M6A_MILEAGE_ONE_MISSING", "M6a has no initial mileage week.")
        val slots = spacing.weeks.singleOrNull { it.week == 1 }
            ?: return defer("M6B_SPACING_ONE_MISSING", "M6b has no initial quality-spacing calendar.")
        if (week.recoveryWeek != distance.recoveryWeek ||
            week.recoveryWeek != slots.recoveryWeek)
            return defer("RECOVERY_WEEK_STATE_MISMATCH",
                "Mileage, base workouts and quality spacing do not agree on recovery status.")
        val baseDays = week.sessions.sortedBy { it.isoDay }
        if (baseDays.map { it.isoDay }.distinct().size != baseDays.size ||
            baseDays.map { it.isoDay } != distance.dayAllocations.map { it.isoDay }.sorted())
            return defer("M6A_DAY_CALENDAR_MISMATCH",
                "Every planned running day must have exactly one distance allocation.")
        if (abs(baseDays.sumOf { it.allocatedKilometers } - week.weeklyKilometers) > 0.00001 ||
            abs(week.weeklyKilometers - distance.weeklyKilometers) > 0.00001)
            return defer("M6A_WEEKLY_DISTANCE_MISMATCH", "Day and week kilometers must reconcile.")

        val selected = slots.opportunities.map { it.isoDay }.toSet()
        if (slots.opportunities.size != selected.size ||
            slots.opportunities.any { candidate ->
                candidate.week != 1 ||
                    baseDays.none {
                        it.isoDay == candidate.isoDay &&
                            it.kind == IndependentRunSessionKind.EASY &&
                            abs(it.allocatedKilometers - candidate.preexistingEasyRunKm) < 0.00001
                    }
            })
            return defer("M6B_M6A_CALENDAR_MISMATCH",
                "M6b must refer to distinct unchanged EASY distance slots.")
        val reviewed = when (dose) {
            null -> {
                if (selected.isNotEmpty())
                    return defer("QUALITY_DOSE_REVIEW_MISSING",
                        "Every proposed quality day needs the separate M9b dose review.")
                emptyList()
            }
            is IndependentQualityDoseReviewDecision.Deferred ->
                return defer("M9B_" + dose.ruleId, dose.explanation)
            is IndependentQualityDoseReviewDecision.Preview -> dose.opportunities
        }
        val approvedDays = reviewed.map { it.isoDay }.toSet()
        if (reviewed.size != approvedDays.size || approvedDays != selected)
            return defer("M9B_SPACING_SLOTS_NOT_ALL_REVIEWED",
                "M9b must review every selected quality day, without adding or silently dropping one.")
        if (week.recoveryWeek && approvedDays.isNotEmpty())
            return defer("RECOVERY_WEEK_QUALITY_HOLD",
                "Recovery weeks retain their base EASY/LONG calendar.")
        if (dose is IndependentQualityDoseReviewDecision.Preview &&
            (dose.totalProposedWeeklyWorkSeconds != reviewed.sumOf { it.selected.workSeconds } ||
                dose.totalProposedWeeklyWorkSeconds > dose.approvedWeeklyWorkCeilingSeconds))
            return defer("M9B_WEEKLY_WORK_DOSE_MISMATCH",
                "Combined selected quality work cannot exceed the reviewed weekly work budget.")
        val claims = input.qualityDistances.associateBy { it.isoDay }
        if (claims.size != input.qualityDistances.size ||
            claims.keys != approvedDays)
            return defer("QUALITY_DISTANCE_CLAIMS_NOT_EXACT",
                "Every selected quality day requires exactly one matching segment-distance claim.")

        // Check the HARD running schedule independently from M6b; it must
        // also satisfy this explicitly reviewed minimum spacing.
        val runningHard = baseDays.filter {
            it.kind == IndependentRunSessionKind.LONG || it.isoDay in approvedDays
        }.map { input.planningMonday.plusDays((it.isoDay - 1).toLong()) }
        for (i in runningHard.indices) for (j in i + 1 until runningHard.size) {
            if (ChronoUnit.DAYS.between(runningHard[i], runningHard[j]) <
                input.reviewedMinimumHardDayGap)
                return defer("RUNNING_HARD_DAY_SPACING_CONFLICT",
                    "Quality and long-run dates do not meet the reviewed minimum gap.")
        }
        if (input.otherDisciplineHardDates.any { other ->
                runningHard.any { run ->
                    abs(ChronoUnit.DAYS.between(other, run)) <
                        input.reviewedMinimumHardDayGap.toLong()
                }
            })
            return defer("OTHER_DISCIPLINE_HARD_DAY_CONFLICT",
                "An explicitly reported hard session falls too close to a quality or long run.")

        val qualityByDay = reviewed.associateBy { it.isoDay }
        val checked = mutableMapOf<Int, IndependentTimedDistanceDecision.MathematicallyReconciled>()
        for (day in approvedDays.sorted()) {
            val preview = requireNotNull(qualityByDay[day]).selected
            val claim = requireNotNull(claims[day])
            when (val result = IndependentTimedQualityDistanceReconciliation.review(
                base, spacing, preview, claim.segmentDistancesMeters, claim.reviewReference
            )) {
                is IndependentTimedDistanceDecision.Deferred ->
                    return defer("M9A_" + result.ruleId, result.explanation)
                is IndependentTimedDistanceDecision.MathematicallyReconciled -> {
                    if (abs(result.weekDistanceKmUnchanged - week.weeklyKilometers) > 0.00001 ||
                        abs(result.workSeconds - preview.workSeconds) > 0.00001)
                        return defer("QUALITY_RECONCILIATION_CONTRACT_MISMATCH",
                            "Quality arithmetic differs from the original M6a/M9b week.")
                    checked[day] = result
                }
            }
        }

        val schedule = baseDays.map { session ->
            val quality = qualityByDay[session.isoDay]
            val role = when {
                quality != null -> IndependentProgramDayRole.QUALITY_REPLACEMENT_PREVIEW
                session.kind == IndependentRunSessionKind.LONG -> IndependentProgramDayRole.LONG_BASE
                else -> IndependentProgramDayRole.EASY_BASE
            }
            IndependentProgramDayReview(
                isoDay = session.isoDay,
                originallyAllocatedKm = session.allocatedKilometers,
                role = role,
                qualityFamily = quality?.family,
                qualityWorkSeconds = quality?.selected?.workSeconds,
                timeToDistanceCheck = checked[session.isoDay]
            )
        }
        return IndependentWeeklyProgramReviewDecision.Preview(
            monday = input.planningMonday,
            weeklyKmUnchanged = week.weeklyKilometers,
            days = schedule,
            qualityWorkSeconds = reviewed.sumOf { it.selected.workSeconds },
            reviewedOtherDisciplineHardDates = input.otherDisciplineHardDates,
            futureWeeksRemainBaseOnly = base.mileage.weeks
                .filter { it.week > 1 }.map { it.week },
            segmentDistanceArithmeticChecked = true,
            ruleIds = listOf(
                "M6A_ORIGINAL_DAY_AND_WEEK_KM_UNCHANGED",
                "M6B_SPACING_AGREES_WITH_M9B_SELECTED_SLOTS",
                "M9B_SESSION_AND_WEEK_WORK_DOSE_REVIEWED",
                "M9A_ASSERTED_SEGMENT_DISTANCES_ARITHMETICALLY_RECONCILED",
                "OTHER_DISCIPLINE_HARD_DAYS_CALLER_REVIEWED_NOT_PHYSIOLOGICALLY_VERIFIED",
                "FUTURE_WEEKS_STILL_UNQUALIFIED",
                "QUALITY_INTENSITY_AND_SOURCE_PROVENANCE_UNVERIFIED",
                "NO_EXECUTABLE_OR_PRODUCTION_PLAN_MUTATION"
            )
        )
    }
}
