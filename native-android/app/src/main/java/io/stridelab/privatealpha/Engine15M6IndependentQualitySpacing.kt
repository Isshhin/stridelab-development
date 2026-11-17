package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * M6B: shadow-only quality DAY opportunities, not scheduled quality workouts.
 * It recomputes M4B/M5A/M5B and M6A; no Engine 1.0 or PlanSnapshot dependency.
 *
 * This does not authenticate recorded intensity, distance, recovery tolerance,
 * other disciplines or the prior-week training history outside the horizon.
 */
internal data class Engine15M6QualitySpacingPolicy(
    val minCalendarDaysFromLong: Int = 2,
    val minCalendarDaysBetweenQuality: Int = 2,
    val minCalendarDaysFromKnownOtherHard: Int = 2
) {
    init {
        require(minCalendarDaysFromLong in 2..3)
        require(minCalendarDaysBetweenQuality in 2..3)
        require(minCalendarDaysFromKnownOtherHard in 1..3)
    }
}

internal data class Engine15M6QualitySpacingInput(
    val calendar: Engine15M6CalendarInput,
    val blockedQualityDates: Set<LocalDate> = emptySet(),
    /**
     * Null means we have NO evidence about strength/other hard training dates.
     * An explicitly supplied empty set is a caller assertion, not proof of
     * complete cross-discipline history.
     */
    val assertedOtherHardDates: Set<LocalDate>? = null,
    val policy: Engine15M6QualitySpacingPolicy = Engine15M6QualitySpacingPolicy()
)

internal data class Engine15M6QualityOpportunity(
    val existingRun: Engine15M6PlacedRun,
    val preview: Engine15M5QualitySlotPreview
) {
    init {
        require(existingRun.role == Engine15M5RunRole.EASY)
        require(existingRun.slot.weekNumber == preview.weekNumber &&
            existingRun.slot.slotNumber == preview.slotNumber)
        require(abs(existingRun.distanceKm - preview.originalEasyDistanceKm) <= 1e-8)
    }

    val proposedDate: LocalDate get() = existingRun.date
    val weekNumber: Int get() = preview.weekNumber
    val slotNumber: Int get() = preview.slotNumber
    val scheduledQualityWorkout: Boolean get() = false
    val executable: Boolean get() = false
}

internal sealed interface Engine15M6QualitySpacingDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val weekNumber: Int? = null
    ) : Engine15M6QualitySpacingDecision

    data class Candidate(
        val policyId: String,
        val baseCalendar: List<Engine15M6CalendarWeek>,
        val originalQualityPreviews: List<Engine15M5QualitySlotPreview>,
        val opportunities: List<Engine15M6QualityOpportunity>,
        val ruleIds: List<String>,
        val priorTrainingHistoryReviewed: Boolean = false,
        val externalHardTrainingEvidenceComplete: Boolean = false,
        val sourceEvidenceVerified: Boolean = false,
        val qualityDoseApproved: Boolean = false,
        val distanceReconciled: Boolean = false,
        val qualityScheduled: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15M6QualitySpacingDecision {
        init {
            require(baseCalendar.isNotEmpty() && opportunities.isNotEmpty())
            require(originalQualityPreviews.size == opportunities.size)
            require(opportunities.map { it.weekNumber to it.slotNumber }.toSet().size ==
                opportunities.size)
            require(ruleIds.distinct() == ruleIds)
            require(!priorTrainingHistoryReviewed && !externalHardTrainingEvidenceComplete &&
                !sourceEvidenceVerified && !qualityDoseApproved &&
                !distanceReconciled && !qualityScheduled &&
                !executable && !authorizedForProduction)
        }
    }
}

/** Every hard-date distance is measured across actual calendar weeks. */
internal object Engine15M6IndependentQualitySpacing {
    const val POLICY_ID = "engine15.m6b.m4b-quality-opportunities-shadow-20261117.1"

    private fun dayGap(a: LocalDate, b: LocalDate): Long =
        abs(ChronoUnit.DAYS.between(a, b))

    fun propose(
        source: Engine15IndependentVolumePlanner.Result,
        calendar: Engine15M6QualitySpacingInput,
        qualityRequests: List<Engine15M5QualityPreviewRequest>
    ): Engine15M6QualitySpacingDecision {
        fun defer(code: String, explanation: String, week: Int? = null) =
            Engine15M6QualitySpacingDecision.Deferred(code, explanation, week)

        // Independently recompute both branches from the SAME M4B source.
        val datedBase = when (val found = Engine15M6IndependentCalendar.schedule(
            source, calendar.calendar
        )) {
            is Engine15M6CalendarDecision.Deferred ->
                return defer("CALENDAR_" + found.reasonCode, found.explanation, found.weekNumber)
            is Engine15M6CalendarDecision.Candidate -> found
        }
        val reviewedQuality = when (val found = Engine15M5QualityPreviewComposer.compose(
            source, qualityRequests
        )) {
            is Engine15M5QualityDecision.Deferred ->
                return defer("QUALITY_" + found.reasonCode, found.explanation, found.weekNumber)
            is Engine15M5QualityDecision.Candidate -> found
        }

        // No caller-supplied "approved" objects are trusted. The original
        // typed EASY/LONG slots must match the dated calendar exactly.
        if (datedBase.weeks.size != reviewedQuality.baseWeeks.size ||
            datedBase.weeks.zip(reviewedQuality.baseWeeks).any { (placed, original) ->
                placed.weekNumber != original.weekNumber ||
                    placed.kind != original.kind ||
                    abs(placed.budgetKm - original.budgetKm) > 1e-8 ||
                    placed.runs.map { it.slot }.toSet() != original.slots.toSet()
            })
            return defer("INDEPENDENT_BASE_MISMATCH",
                "M6A dates and M5B reviewed quality must refer to identical M5A sessions.")

        val index = datedBase.weeks.flatMap { week ->
            week.runs.map { run -> (week.weekNumber to run.slot.slotNumber) to run }
        }.toMap()
        val longs = datedBase.weeks.flatMap { week ->
            week.runs.filter { it.role == Engine15M5RunRole.LONG }.map { it.date }
        }
        val opportunities = mutableListOf<Engine15M6QualityOpportunity>()
        for (preview in reviewedQuality.qualityPreviews.sortedWith(
            compareBy({ it.weekNumber }, { it.slotNumber })
        )) {
            val run = index[preview.weekNumber to preview.slotNumber]
                ?: return defer("QUALITY_SLOT_NOT_PLACED",
                    "The reviewed quality candidate has no M6A dated EASY slot.",
                    preview.weekNumber)
            if (run.role != Engine15M5RunRole.EASY ||
                abs(run.distanceKm - preview.originalEasyDistanceKm) > 1e-8)
                return defer("QUALITY_BASE_SLOT_MISMATCH",
                    "The quality candidate must refer to the original unchanged EASY slot.",
                    preview.weekNumber)
            if (run.date in calendar.blockedQualityDates)
                return defer("QUALITY_DAY_BLOCKED",
                    "The existing run day was explicitly blocked for quality work.",
                    preview.weekNumber)
            if (longs.any {
                dayGap(run.date, it) < calendar.policy.minCalendarDaysFromLong
            })
                return defer("QUALITY_TOO_CLOSE_TO_LONG",
                    "A quality preview conflicts with a long run in the same, previous or next week.",
                    preview.weekNumber)
            if (calendar.assertedOtherHardDates?.any {
                dayGap(run.date, it) < calendar.policy.minCalendarDaysFromKnownOtherHard
            } == true)
                return defer("QUALITY_TOO_CLOSE_TO_OTHER_HARD",
                    "A caller-reported other hard session is too close to this quality opportunity.",
                    preview.weekNumber)
            if (opportunities.any {
                dayGap(run.date, it.proposedDate) <
                    calendar.policy.minCalendarDaysBetweenQuality
            })
                return defer("QUALITY_TOO_CLOSE_TO_QUALITY",
                    "Quality opportunities need separation even across week boundaries.",
                    preview.weekNumber)

            opportunities += Engine15M6QualityOpportunity(run, preview)
        }

        return Engine15M6QualitySpacingDecision.Candidate(
            policyId = POLICY_ID,
            baseCalendar = datedBase.weeks,
            originalQualityPreviews = reviewedQuality.qualityPreviews,
            opportunities = opportunities,
            ruleIds = listOf(
                "M4B_M5A_M5B_AND_M6A_RECOMPUTED",
                "ONLY_EXISTING_EASY_SLOT_DATES_CAN_BE_QUALITY_OPPORTUNITIES",
                "CROSS_WEEK_LONG_AND_QUALITY_SPACING",
                "EXPLICIT_BLOCKED_QUALITY_AND_ASSERTED_HARD_DATES_CHECKED",
                "M4B_M5A_WEEKLY_DISTANCE_UNCHANGED",
                "NO_PREHORIZON_OR_CROSS_DISCIPLINE_COMPLETENESS_PROOF",
                "TIMED_QUALITY_NOT_SCHEDULED_OR_EXECUTABLE"
            )
        )
    }
}
