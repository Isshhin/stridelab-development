package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** M6C external events are caller assertions, not verified activity sources. */
internal enum class Engine15M6CDiscipline { STRENGTH, CALISTHENICS, CONDITIONING }
internal enum class Engine15M6CLoad { UPPER_BODY, LOWER_BODY, FULL_BODY_HARD, EASY_GENERAL }

internal data class Engine15M6CExternalSession(
    val id: String,
    val date: LocalDate,
    val discipline: Engine15M6CDiscipline,
    val load: Engine15M6CLoad
) {
    val hardLowerOrFull: Boolean
        get() = load == Engine15M6CLoad.LOWER_BODY ||
            load == Engine15M6CLoad.FULL_BODY_HARD
}

/**
 * A complete external calendar is NOT certified by this caller assertion.
 * Null external sessions mean the calendar has not even been supplied.
 */
internal data class Engine15M6CReviewInput(
    val calendar: Engine15M6CalendarInput,
    val externalSessions: List<Engine15M6CExternalSession>?,
    val externalCalendarCoverageAsserted: Boolean,
    val qualityRequests: List<Engine15M5QualityPreviewRequest> = emptyList(),
    val blockedQualityDates: Set<LocalDate> = emptySet(),
    val qualitySpacingPolicy: Engine15M6QualitySpacingPolicy =
        Engine15M6QualitySpacingPolicy(),
    val allowEasyUpperPair: Boolean = false,
    val minDaysFromLongOrQualityForExternalHard: Int = 2
) {
    init {
        require(minDaysFromLongOrQualityForExternalHard in 2..3)
    }
}

internal sealed interface Engine15M6CReviewDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val date: LocalDate? = null
    ) : Engine15M6CReviewDecision

    /**
     * Original typed running sessions remain untouched. An optional quality
     * opportunity retains its preview status; none is executable/scheduled.
     */
    data class Candidate(
        val policyId: String,
        val runningWeeks: List<Engine15M6CalendarWeek>,
        val qualityOpportunities: List<Engine15M6QualityOpportunity>,
        val externalSessions: List<Engine15M6CExternalSession>,
        val ruleIds: List<String>,
        val externalCalendarVerified: Boolean = false,
        val sourceEvidenceVerified: Boolean = false,
        val timeBudgetValidated: Boolean = false,
        val qualityScheduled: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15M6CReviewDecision {
        init {
            require(runningWeeks.isNotEmpty())
            require(ruleIds.distinct() == ruleIds)
            require(!externalCalendarVerified && !sourceEvidenceVerified &&
                !timeBudgetValidated && !qualityScheduled &&
                !executable && !authorizedForProduction)
        }
    }
}

/**
 * Does not create external workouts or alter the independent run calendar.
 * Any conflict defers the WHOLE shadow review instead of relocating, dropping,
 * downgrading or silently reclassifying a session.
 */
internal object Engine15M6IndependentInterferenceReview {
    const val POLICY_ID = "engine15.m6c.m4b-interference-shadow-20261117.1"

    private fun gap(a: LocalDate, b: LocalDate): Long =
        abs(ChronoUnit.DAYS.between(a, b))

    private fun canPairEasyUpper(
        run: Engine15M6PlacedRun,
        qualityDates: Set<LocalDate>,
        external: Engine15M6CExternalSession
    ): Boolean =
        run.role == Engine15M5RunRole.EASY &&
            run.date !in qualityDates &&
            external.discipline in setOf(
                Engine15M6CDiscipline.STRENGTH,
                Engine15M6CDiscipline.CALISTHENICS
            ) && external.load == Engine15M6CLoad.UPPER_BODY

    fun review(
        source: Engine15IndependentVolumePlanner.Result,
        input: Engine15M6CReviewInput
    ): Engine15M6CReviewDecision {
        fun defer(code: String, reason: String, date: LocalDate? = null) =
            Engine15M6CReviewDecision.Deferred(code, reason, date)

        if (!input.externalCalendarCoverageAsserted)
            return defer("EXTERNAL_CALENDAR_NOT_ASSERTED",
                "An explicit caller assertion about external calendar coverage is required.")
        val others = input.externalSessions
            ?: return defer("EXTERNAL_CALENDAR_MISSING",
                "No external-session listing was supplied for cross-discipline review.")
        if (others.any { it.id.isBlank() || it.id.length > 128 })
            return defer("INVALID_EXTERNAL_ID",
                "Every external session requires a unique nonblank bounded identifier.")
        if (others.map { it.id }.toSet().size != others.size)
            return defer("DUPLICATE_EXTERNAL_ID",
                "A duplicate external session must not be counted or silently ignored.")

        // Engine 1.0 sessions and saved plans are NOT inputs to the reviewer.
        // At this boundary M6A rechecks the original typed M4B and M5A.
        val dated = when (val result =
            Engine15M6IndependentCalendar.schedule(source, input.calendar)
        ) {
            is Engine15M6CalendarDecision.Deferred ->
                return defer("CALENDAR_" + result.reasonCode, result.explanation)
            is Engine15M6CalendarDecision.Candidate -> result
        }

        val first = input.calendar.startDate.minusDays(1)
        val last = input.calendar.startDate.plusWeeks(dated.weeks.size.toLong())
        if (others.any { it.date < first || it.date > last })
            return defer("EXTERNAL_DATE_OUTSIDE_REVIEW_WINDOW",
                "External items must lie within the plan or its one-day adjacency window.")
        if (others.groupBy { it.date }.any { it.value.size > 1 })
            return defer("MULTIPLE_EXTERNAL_SAME_DATE",
                "M6C has not validated simultaneous external workouts on one day.")

        val hardDates = others.filter { it.hardLowerOrFull }.map { it.date }.toSet()
        val quality: List<Engine15M6QualityOpportunity> =
            if (input.qualityRequests.isEmpty()) emptyList()
            else when (val proposed = Engine15M6IndependentQualitySpacing.propose(
                source,
                Engine15M6QualitySpacingInput(
                    calendar = input.calendar,
                    blockedQualityDates = input.blockedQualityDates,
                    assertedOtherHardDates = hardDates,
                    policy = input.qualitySpacingPolicy
                ),
                input.qualityRequests
            )) {
                is Engine15M6QualitySpacingDecision.Deferred ->
                    return defer("QUALITY_" + proposed.reasonCode, proposed.explanation)
                is Engine15M6QualitySpacingDecision.Candidate -> {
                    if (proposed.baseCalendar != dated.weeks)
                        return defer("INDEPENDENT_CALENDAR_MISMATCH",
                            "Quality spacing and the external conflict check disagree on M6A dates.")
                    proposed.opportunities
                }
            }

        val runs = dated.weeks.flatMap { it.runs }
        val qualityDates = quality.map { it.proposedDate }.toSet()
        val longDates = runs.filter { it.role == Engine15M5RunRole.LONG }
            .map { it.date }

        for (external in others.sortedWith(compareBy({ it.date }, { it.id }))) {
            val coincident = runs.singleOrNull { it.date == external.date }
            if (coincident != null &&
                !(input.allowEasyUpperPair &&
                    canPairEasyUpper(coincident, qualityDates, external)))
                return defer("RUN_EXTERNAL_SAME_DAY_CONFLICT",
                    "Running/external double days are not approved by this independent policy.",
                    external.date)

            if (external.hardLowerOrFull &&
                longDates.any { gap(it, external.date) <
                    input.minDaysFromLongOrQualityForExternalHard })
                return defer("EXTERNAL_HARD_TOO_CLOSE_TO_LONG",
                    "Lower/full-body hard sessions must be separated from independent long runs.",
                    external.date)
            if (external.hardLowerOrFull &&
                qualityDates.any { gap(it, external.date) <
                    input.minDaysFromLongOrQualityForExternalHard })
                return defer("EXTERNAL_HARD_TOO_CLOSE_TO_QUALITY",
                    "Lower/full-body hard sessions must be separated from quality opportunities.",
                    external.date)
        }

        val sortedHard = others.filter { it.hardLowerOrFull }
            .sortedBy { it.date }
        if (sortedHard.zipWithNext().any { (a, b) -> gap(a.date, b.date) < 2 })
            return defer("EXTERNAL_HARD_BACK_TO_BACK",
                "Adjacent lower/full-body hard external sessions require separate review.")

        return Engine15M6CReviewDecision.Candidate(
            policyId = POLICY_ID,
            runningWeeks = dated.weeks,
            qualityOpportunities = quality,
            externalSessions = others,
            ruleIds = listOf(
                "M4B_M5A_M6A_RECOMPUTED",
                "OPTIONAL_M5B_M6B_QUALITY_OPPORTUNITIES_RECOMPUTED",
                "EXPLICIT_EXTERNAL_CALENDAR_CALLER_ASSERTION_ONLY",
                "SAME_DAY_AND_LOWER_BODY_HARD_SPACING_CHECKED",
                "NO_TIME_BUDGET_OR_EXTERNAL_GENERATION_INFERENCE",
                "ORIGINAL_TYPED_WORKOUTS_AND_WEEKLY_DISTANCE_UNCHANGED",
                "NO_PRODUCTION_OR_EXECUTION_AUTHORIZATION"
            )
        )
    }
}
