package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

internal const val ENGINE15_INDEPENDENT_CANDIDATE_POLICY =
    "stridelab.engine15.independent-running-candidate-20261117.1"

internal sealed interface Engine15IndependentPlanDecision {
    data class Deferred(val reasonCode: String, val explanation: String) : Engine15IndependentPlanDecision

    data class Candidate(
        val plan: PlanSnapshot,
        val volume: Engine15IndependentVolumePlanner.Result,
        val periodization: Engine15M6DReviewDecision.Candidate,
        val executable: Boolean = false,
        val productionReady: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15IndependentPlanDecision {
        init {
            require(plan.engineVersion == ENGINE15_VERSION &&
                plan.policyId == ENGINE15_INDEPENDENT_CANDIDATE_POLICY)
            require(plan.sessions.isNotEmpty() && plan.sessions.all {
                it.discipline == "run" && it.quality.not() &&
                    it.runningPrescription?.family == null
            })
            require(!executable && !productionReady && !authorizedForProduction)
        }
    }
}

/**
 * Assembles the reviewed independent M3–M6 running path into app workout
 * contracts. Mixed disciplines, quality workouts and unresolved inputs defer.
 */
internal object Engine15IndependentPlanGenerator {
    fun generate(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant
    ): Engine15IndependentPlanDecision {
        fun defer(code: String, explanation: String) =
            Engine15IndependentPlanDecision.Deferred(code, explanation)

        if (request.policyChoice != "engine15")
            return defer("ENGINE15_NOT_SELECTED", "Select Engine 1.5 before requesting an independent candidate.")
        if (request.runningRecoveryHold)
            return defer("RUNNING_RECOVERY_HOLD", "Running remains on hold until the recovery review is resolved.")
        if (request.qualityCount != 0)
            return defer("QUALITY_GENERATION_NOT_QUALIFIED", "Requested quality work is not qualified for an executable plan; no substitute was generated.")
        if (request.strengthDays != 0 || request.calDays != 0 || request.conditioningDays != 0 ||
            request.disciplineGoals.keys.any { it != "run" })
            return defer("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT", "Independent workout construction and calendar coordination for mixed disciplines are not available yet.")
        if (request.runDays !in 2..6 || request.weeks !in 1..16)
            return defer("RUNNING_SCOPE_UNSUPPORTED", "This candidate supports two to six running days and one to sixteen weeks.")
        if (request.days.isEmpty() || request.days.any { it !in 1..7 } || request.days.size < request.runDays ||
            (request.runDays >= 3 && request.longDay !in request.days))
            return defer("RUNNING_AVAILABILITY_UNSUPPORTED", "Available weekdays must fit every run and the selected long-run day.")
        if (request.weeklySlots.isNotEmpty())
            return defer("WEEKLY_SLOT_ASSIGNMENTS_UNSUPPORTED", "This candidate does not yet honor per-discipline weekday assignments.")
        if (request.allowDoubleDays)
            return defer("DOUBLE_DAYS_UNSUPPORTED", "The running-only calendar does not generate or validate double sessions.")
        if (!request.weeklyKm.isFinite() || request.weeklyKm !in 0.0..120.0 ||
            !request.longestKm.isFinite() || request.longestKm !in 0.0..42.0 ||
            request.targetWeeklyKm?.let { !it.isFinite() || it !in 0.1..120.0 } == true ||
            request.planningPace.let { !it.isFinite() || it !in 180.0..1800.0 })
            return defer("INVALID_RUNNING_INPUT", "Recent mileage, longest-run exposure, target and planning pace must be valid before generation.")
        if (request.recentMileageBasis == RecentMileageBasis.RECORDED_PARTIAL.name)
            return defer("ATHLETE_BASELINE_NEEDS_BASELINE", "Recorded mileage has incomplete coverage; confirm a usual weekly average or provide complete history before a numeric plan is generated.")

        Engine15SessionLocks.validateRequest(request)
        val lockPattern = Regex(".*-w([1-9][0-9]*)-run([1-9][0-9]*)$")
        val lockedSlots = request.lockedSessionDays.mapNotNull { (sessionId, rawDate) ->
            val match = lockPattern.matchEntire(sessionId)
                ?: return defer("SESSION_LOCK_MAPPING_UNSUPPORTED", "A locked workout ID does not identify an Engine 1.5 running week and slot.")
            val slot = match.groupValues[1].toInt() to match.groupValues[2].toInt()
            (slot to LocalDate.parse(rawDate))
        }.toMap()
        if (lockedSlots.size != request.lockedSessionDays.size)
            return defer("SESSION_LOCK_MAPPING_UNSUPPORTED", "Multiple locks target the same Engine 1.5 running slot.")
        if (lockedSlots.keys.any { (week, slot) -> week !in 1..request.weeks || slot !in 1..request.runDays })
            return defer("SESSION_LOCK_MAPPING_UNSUPPORTED", "A locked workout ID refers to a week or run slot outside this plan.")

        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)

        val athlete = Engine15AthleteBaseline.assess(request)
        val volume = Engine15IndependentVolumePlanner.propose(
            athlete,
            Engine15IndependentVolumePlanner.Input(
                weeks = request.weeks,
                runningDays = request.runDays,
                targetWeeklyKm = request.targetWeeklyKm,
                returning = request.returning,
                recoveryHold = request.runningRecoveryHold
            )
        )
        if (volume.status != Engine15IndependentVolumePlanner.Status.DISTANCE_CANDIDATE)
            return defer("ATHLETE_BASELINE_${volume.status.name}", volume.reasons.joinToString(" "))

        val calendar = Engine15M6CalendarInput(
            startDate = request.start,
            availableIsoDays = request.days,
            preferredLongIsoDay = request.longDay,
            preferredEasyIsoDays = request.days - request.longDay,
            lockedSlotDates = lockedSlots
        )
        val periodization = when (val outcome = Engine15M6IndependentPeriodizationReview.audit(
            volume,
            Engine15M6CReviewInput(
                calendar = calendar,
                externalSessions = emptyList(),
                externalCalendarCoverageAsserted = true
            )
        )) {
            is Engine15M6DReviewDecision.Deferred ->
                return defer("PERIODIZATION_${outcome.reasonCode}", outcome.explanation)
            is Engine15M6DReviewDecision.Candidate -> outcome
        }
        val placedWeeks = periodization.originalConflictReview.runningWeeks

        val sessions = placedWeeks.flatMap { week ->
            week.runs.map { placed ->
                val slot = placed.slot
                val meters = (slot.distanceKm * 1000.0).toInt()
                val sessionId = request.lockedSessionDays.keys.firstOrNull { lockedId ->
                    lockPattern.matchEntire(lockedId)?.let {
                        it.groupValues[1].toInt() == week.weekNumber && it.groupValues[2].toInt() == slot.slotNumber
                    } == true
                } ?: "$id-w${week.weekNumber}-run${slot.slotNumber}"
                val estimatedMinutes = ceil(slot.distanceKm * request.planningPace / 60.0)
                    .toInt().coerceAtLeast(1)
                if (request.timeBudgetEnabled && estimatedMinutes > request.minutes)
                    return defer("DISTANCE_TIME_BUDGET_CONFLICT", "A distance-only run exceeds the entered session-time limit; no shorter substitute was generated.")
                PlannedSession(
                    id = sessionId,
                    date = placed.date,
                    discipline = "run",
                    title = if (slot.role == Engine15M5RunRole.LONG) "Long easy run" else "Easy run",
                    minutes = estimatedMinutes,
                    distanceKm = slot.distanceKm,
                    kind = if (slot.role == Engine15M5RunRole.LONG) "long" else "easy",
                    quality = false,
                    steps = listOf("${meters} m at easy, conversational effort"),
                    reason = buildString {
                        append(if (week.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY) "Recovery week. " else "Loading week. ")
                        append("Distance and calendar were independently constructed; duration is estimated from the entered planning pace.")
                    },
                    runningPrescription = RunningPrescription(
                        role = if (slot.role == Engine15M5RunRole.LONG) "LONG" else "EASY",
                        family = null,
                        segments = listOf(RunningSegment("EASY", meters, "Easy, conversational effort; no pace or heart-rate target."))
                    )
                )
            }
        }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        if (sessions.size != request.weeks * request.runDays || sessions.map { it.id }.distinct().size != sessions.size)
            return defer("INCOMPLETE_PLAN_ASSEMBLY", "Independent output did not contain exactly one dated workout for every weekly running slot.")

        val snapshot = PlanSnapshot(
            id = id,
            owner = owner,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_CANDIDATE_POLICY,
            createdAt = now,
            request = request,
            sessions = sessions,
            explanations = buildList {
                add("Independent Engine 1.5 running candidate; no Engine 1.0 generation or validation was used.")
                add("Mileage basis: ${athlete.basis}; reported/recorded history remains provisional and is not source-authenticated.")
                add("M4B owns weekly volume and recovery weeks; M5A owns easy/long workout distances; M6A/M6C/M6D own date placement, interference review and periodization audit.")
                add("Duration is an estimate from the entered planning pace. No pace or heart-rate target is prescribed.")
                add("Preview only: executable, production-ready and production-authorized are all false.")
                volume.shortfallToRequestedTargetKm?.takeIf { it > 0.01 }?.let {
                    add("Requested target shortfall: ${"%.1f".format(java.util.Locale.US, it)} km/week; the candidate does not add unreviewed mileage to close it.")
                }
                addAll(volume.reasons)
            },
            name = "Engine 1.5 independent running preview"
        )
        check(sessions.all { it.date >= request.start && it.date < request.start.plusWeeks(request.weeks.toLong()) })
        check(sessions.groupBy { it.date }.values.all { it.size == 1 })
        check(sessions.groupBy { ChronoUnit.DAYS.between(request.start, it.date) / 7L }
            .all { (index, rows) -> kotlin.math.abs(rows.sumOf { it.distanceKm!! } - volume.weeks[index.toInt()].proposedKm!!) < 1e-6 })
        return Engine15IndependentPlanDecision.Candidate(snapshot, volume, periodization)
    }
}
