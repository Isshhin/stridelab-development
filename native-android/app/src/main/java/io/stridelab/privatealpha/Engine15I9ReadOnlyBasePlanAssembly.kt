package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.abs

/**
 * I9 is a READ-ONLY dated representation of original M5A EASY/LONG slots.
 * These are not saved/executable PlannedSession instances or quality workouts.
 */
internal data class Engine15I9DatedBaseRun(
    val weekNumber: Int,
    val slotNumber: Int,
    val date: LocalDate,
    val role: Engine15M5RunRole,
    val distanceKm: Double,
    val originalDistanceWorkout: IndependentWorkoutComposition,
    val executable: Boolean = false,
    val qualityAuthorized: Boolean = false
) {
    init {
        require(weekNumber > 0 && slotNumber > 0)
        require(distanceKm.isFinite() && distanceKm > 0.0)
        require(role == Engine15M5RunRole.EASY || role == Engine15M5RunRole.LONG)
        require(abs(distanceKm * 10.0 - kotlin.math.round(distanceKm * 10.0)) < 1e-6)
        require(abs((originalDistanceWorkout.total(WorkoutUnit.METERS) ?: Double.NaN) -
            distanceKm * 1000.0) <= 1e-6)
        require(!executable && !qualityAuthorized)
    }
}

internal data class Engine15I9ReadOnlyWeek(
    val weekNumber: Int,
    val phase: Engine15M6DPhase,
    val proposedKm: Double,
    val runs: List<Engine15I9DatedBaseRun>,
    val requiresCompletedWeekReassessment: Boolean,
    val originallyAssertedExternalSessionCount: Int
) {
    init {
        require(weekNumber > 0 && proposedKm.isFinite() && proposedKm > 0.0)
        require(runs.isNotEmpty() && runs.all { it.weekNumber == weekNumber })
        require(runs.map { it.date }.distinct().size == runs.size)
        require(runs.map { it.slotNumber }.distinct().size == runs.size)
        require(runs.zipWithNext().all { (a, b) -> a.date < b.date })
        require(abs(runs.sumOf { it.distanceKm } - proposedKm) <= 1e-6)
        require(originallyAssertedExternalSessionCount >= 0)
    }
}

internal data class Engine15I9BasePlanAssemblyInput(
    val interferenceReview: Engine15M6CReviewInput,
    val explicitBaseOnlyReview: Boolean
)

internal sealed interface Engine15I9BasePlanAssemblyDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String
    ) : Engine15I9BasePlanAssemblyDecision

    data class ReadOnlyBasePreview(
        val policyId: String,
        val originalM6D: Engine15M6DReviewDecision.Candidate,
        val weeks: List<Engine15I9ReadOnlyWeek>,
        val totalProposedKm: Double,
        val peakProposedKm: Double,
        val requestedTargetKm: Double?,
        val targetShortfallKm: Double?,
        val reviewAfterCompletedWeek: Int?,
        val ruleIds: List<String>,
        val calendarArithmeticReconciled: Boolean = true,
        val explicitBaseOnly: Boolean = true,
        val qualityIncluded: Boolean = false,
        val externalCalendarAuthenticated: Boolean = false,
        val sourceEvidenceVerified: Boolean = false,
        val coachingDoseApproved: Boolean = false,
        val executable: Boolean = false,
        val authorizedForPlanMutation: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15I9BasePlanAssemblyDecision {
        init {
            require(weeks.isNotEmpty())
            require(weeks.map { it.weekNumber } == (1..weeks.size).toList())
            require(abs(weeks.sumOf { it.proposedKm } - totalProposedKm) <= 1e-6)
            require(abs(weeks.maxOf { it.proposedKm } - peakProposedKm) <= 1e-6)
            require(weeks.map { it.phase } == originalM6D.weeks.map { it.phase })
            require(weeks.flatMap { it.runs }.all { !it.executable && !it.qualityAuthorized })
            require(ruleIds.distinct() == ruleIds)
            require(calendarArithmeticReconciled && explicitBaseOnly && !qualityIncluded &&
                !externalCalendarAuthenticated && !sourceEvidenceVerified &&
                !coachingDoseApproved && !executable &&
                !authorizedForPlanMutation && !authorizedForProduction)
        }
    }
}

/**
 * A single read-only assembled base-distance week/day view. Does not infer
 * completed mileage or safe athlete pace/HR, qualify quality prescriptions,
 * create PlannedSession, or mutate stored plans. M6D is fully recomputed.
 */
internal object Engine15I9ReadOnlyBasePlanAssembly {
    const val POLICY_ID = "engine15.i9.readonly-base-calendar-shadow-20261117.1"

    fun assemble(
        source: Engine15IndependentVolumePlanner.Result,
        input: Engine15I9BasePlanAssemblyInput
    ): Engine15I9BasePlanAssemblyDecision {
        fun defer(code: String, explanation: String) =
            Engine15I9BasePlanAssemblyDecision.Deferred(code, explanation)

        if (!input.explicitBaseOnlyReview)
            return defer("BASE_ONLY_SCOPE_NOT_ACKNOWLEDGED",
                "I9 is a read-only EASY/LONG preview, not an approved full coaching plan.")
        if (input.interferenceReview.qualityRequests.isNotEmpty())
            return defer("UNQUALIFIED_QUALITY_REQUEST_NOT_ADMITTED",
                "I9 must not silently omit or downgrade a requested quality workout.")
        val audited = when (val decision =
            Engine15M6IndependentPeriodizationReview.audit(source, input.interferenceReview)
        ) {
            is Engine15M6DReviewDecision.Deferred ->
                return defer("M6D_" + decision.reasonCode, decision.explanation)
            is Engine15M6DReviewDecision.Candidate -> decision
        }
        if (audited.originalConflictReview.qualityOpportunities.isNotEmpty() ||
            audited.weeks.any { it.qualityOpportunityCount != 0 })
            return defer("QUALITY_OPPORTUNITY_NOT_ALLOWED_IN_BASE_PREVIEW",
                "No M6B quality candidate may appear as an I9 base run.")

        val originalWeeks = audited.originalConflictReview.runningWeeks
        if (originalWeeks.size != audited.weeks.size)
            return defer("M6D_CALENDAR_WEEK_COUNT_MISMATCH",
                "The original calendar and periodization audit have different horizons.")
        val weeks = mutableListOf<Engine15I9ReadOnlyWeek>()
        for (audit in audited.weeks) {
            val original = originalWeeks.singleOrNull { it.weekNumber == audit.weekNumber }
                ?: return defer("M6D_CALENDAR_WEEK_MISSING",
                    "A reviewed original calendar week is unavailable.")
            if (original.runs.size != audit.runningSessionCount ||
                abs(original.budgetKm - audit.proposedKm) > 1e-6)
                return defer("M6D_BASE_WEEK_BUDGET_MISMATCH",
                    "The original week distance or run count changed.")
            val runs = original.runs.sortedBy { it.date }.map { placed ->
                // Retain the exact typed M5A distance-only workout, never a
                // guessed or newly composed time/pace/intensity prescription.
                Engine15I9DatedBaseRun(
                    weekNumber = original.weekNumber,
                    slotNumber = placed.slot.slotNumber,
                    date = placed.date,
                    role = placed.role,
                    distanceKm = placed.distanceKm,
                    originalDistanceWorkout = placed.slot.workout
                )
            }
            val requiredLongRuns = if (source.input.runningDays >= 3) 1 else 0
            if (runs.count { it.role == Engine15M5RunRole.LONG } != requiredLongRuns)
                return defer("BASE_LONG_RUN_ROLE_COUNT_MISMATCH",
                    "Only the original M5A long-run role may be represented.")
            if (runs.map { it.slotNumber }.toSet().size != runs.size ||
                runs.map { it.date }.toSet().size != runs.size)
                return defer("BASE_RUN_IDENTITY_MISMATCH",
                    "The original M6A placement cannot duplicate dated slots.")
            weeks += Engine15I9ReadOnlyWeek(
                weekNumber = audit.weekNumber,
                phase = audit.phase,
                proposedKm = audit.proposedKm,
                runs = runs,
                requiresCompletedWeekReassessment = audit.requiresCompletedWeekReassessment,
                originallyAssertedExternalSessionCount = audit.externalSessionCount
            )
        }
        return Engine15I9BasePlanAssemblyDecision.ReadOnlyBasePreview(
            policyId = POLICY_ID,
            originalM6D = audited,
            weeks = weeks,
            totalProposedKm = audited.proposedTotalKm,
            peakProposedKm = audited.proposedPeakKm,
            requestedTargetKm = audited.requestedTargetKm,
            targetShortfallKm = audited.targetBudgetShortfallKm,
            reviewAfterCompletedWeek = audited.reviewAfterCompletedWeek,
            ruleIds = listOf(
                "M4B_M5A_M6A_M6C_M6D_INDEPENDENTLY_RECOMPUTED",
                "ORIGINAL_DATED_EASY_LONG_DISTANCE_WORKOUTS_UNMODIFIED",
                "ORIGINAL_100M_WEEK_BUDGET_AND_RECOVERY_PHASES",
                "EXPLICIT_READ_ONLY_BASE_SCOPE_NO_QUALITY_DOWNGRADE",
                "M6C_EXTERNAL_CALENDAR_CALLER_ASSERTED_NOT_AUTHENTICATED",
                "NO_PACE_HR_TIME_DOSE_OR_RESPONSIVENESS_INFERENCE",
                "NO_PLANNEDSESSION_OR_PRODUCTION_PLAN_MUTATION"
            )
        )
    }
}
