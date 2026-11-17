package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

internal const val ENGINE15_INDEPENDENT_CANDIDATE_POLICY =
    "stridelab.engine15.independent-running-candidate-20261117.1"
internal const val ENGINE15_INDEPENDENT_RUNNING_POLICY =
    "stridelab.engine15.independent-running-20261117.1"
internal const val ENGINE15_INDEPENDENT_STRENGTH_POLICY =
    "stridelab.engine15.independent-strength-20261117.1"
internal const val ENGINE15_INDEPENDENT_CALISTHENICS_POLICY =
    "stridelab.engine15.independent-calisthenics-20261117.1"

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
internal object Engine15IndependentPlanGenerator : PlanGenerationEngine {
    override val version = ENGINE15_VERSION
    override val policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY

    override fun generate(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant
    ): PlanSnapshot = throw PlanConstraint(
        "EXTERNAL_PLAN_CONTEXT_REQUIRED: Generate Engine 1.5 running drafts through TrainingRepository so saved workouts are included in interference checks."
    )

    fun validateRequest(request: PlanRequest) {
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays == 0 && request.conditioningDays > 0)
            throw PlanConstraint("CONDITIONING_GENERATION_NOT_INDEPENDENT: Conditioning workout construction has not yet been added to the Engine 1.5 pipeline.")
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays > 0) {
            Engine15CalisthenicsWorkoutComposer.validateRequest(request)
            return
        }
        if (request.runDays == 0 && request.strengthDays > 0) {
            Engine15StrengthWorkoutComposer.validateRequest(request)
            return
        }
        appDraft(preview(request, "engine15-validation", "validation", Instant.EPOCH))
    }

    fun generateWithHistory(
        request: PlanRequest,
        owner: String,
        plans: List<PlanSnapshot>,
        id: String = java.util.UUID.randomUUID().toString(),
        now: Instant = Instant.now(),
        exerciseLibrary: ExerciseLibraryV2? = null
    ): PlanSnapshot {
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays == 0 && request.conditioningDays > 0)
            throw PlanConstraint("CONDITIONING_GENERATION_NOT_INDEPENDENT: Conditioning workout construction has not yet been added to the Engine 1.5 pipeline.")
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays > 0) {
            Engine15CalisthenicsWorkoutComposer.validateRequest(request)
            val library = exerciseLibrary ?: throw PlanConstraint(
                "CALISTHENICS_CATALOG_REQUIRED: The shared offline exercise catalog is unavailable; no substitute was generated."
            )
            val existing = plans.asSequence().filter { it.owner == owner && it.status in setOf("approved", "paused") }
                .flatMap { plan -> plan.sessions.asSequence().filter { it.status in setOf("planned", "in_progress") }.map { it.date } }
                .any { it in request.start.minusDays(1)..request.start.plusWeeks(request.weeks.toLong()) }
            if (existing) throw PlanConstraint(
                "EXTERNAL_SCHEDULE_REVIEW_REQUIRED: Calisthenics-only generation does not yet coordinate known saved sessions; no existing session was ignored."
            )
            RecordPolicy.identifier(owner)
            RecordPolicy.identifier(id)
            val sessions = Engine15CalisthenicsWorkoutComposer.compose(request, id, library)
            return PlanSnapshot(
                id = id, owner = owner, engineVersion = version,
                policyId = ENGINE15_INDEPENDENT_CALISTHENICS_POLICY, createdAt = now,
                request = request, sessions = sessions,
                explanations = listOf(
                    "Independent Engine 1.5 calisthenics plan; no legacy generator or coaching validator was used.",
                    "Exercise records come from the shared offline educational catalog; selection, skill gates, repetitions, recovery periods and calendar placement are Engine 1.5-owned.",
                    "This static plan does not promote skills based on elapsed time or adjust workload from completed sessions.",
                    "This is a draft. Review each exercise, equipment requirement, schedule and repetition/hold target before explicit approval."
                ),
                name = "Engine 1.5 independent calisthenics plan"
            )
        }
        if (request.runDays == 0 && request.strengthDays > 0) {
            Engine15StrengthWorkoutComposer.validateRequest(request)
            val library = exerciseLibrary ?: throw PlanConstraint(
                "STRENGTH_CATALOG_REQUIRED: The shared offline exercise catalog is unavailable; no substitute was generated."
            )
            val first = request.start.minusDays(1)
            val last = request.start.plusWeeks(request.weeks.toLong())
            val existing = plans.asSequence().filter { it.owner == owner && it.status in setOf("approved", "paused") }
                .flatMap { plan -> plan.sessions.asSequence().filter { it.status in setOf("planned", "in_progress") }.map { plan.id to it } }
                .filter { (_, session) -> session.date in first..last }.toList()
            if (existing.isNotEmpty()) throw PlanConstraint(
                "EXTERNAL_SCHEDULE_REVIEW_REQUIRED: Strength-only generation does not yet coordinate known saved sessions; no existing session was ignored."
            )
            RecordPolicy.identifier(owner)
            RecordPolicy.identifier(id)
            val sessions = Engine15StrengthWorkoutComposer.compose(request, id, library)
            return PlanSnapshot(
                id = id, owner = owner, engineVersion = version,
                policyId = ENGINE15_INDEPENDENT_STRENGTH_POLICY, createdAt = now,
                request = request, sessions = sessions,
                explanations = listOf(
                    "Independent Engine 1.5 strength plan; no Engine 1.0 generation or validation was used.",
                    "Exercise records come from the shared offline library; selection, goal-specific repetition progression, deloads and constraints are owned by Engine 1.5.",
                    "Repetition targets progress within each four-week block and reset in the fourth-week one-set recovery period; no load increase or feedback-based recalculation is inferred.",
                    "This is a draft. Review each exercise, equipment requirement, schedule and repetition target before explicit approval."
                ),
                name = "Engine 1.5 independent strength plan"
            )
        }
        return appDraft(preview(request, owner, id, now, plans))
    }

    private fun appDraft(decision: Engine15IndependentPlanDecision): PlanSnapshot = when (decision) {
        is Engine15IndependentPlanDecision.Deferred ->
            throw PlanConstraint("${decision.reasonCode}: ${decision.explanation}")
        is Engine15IndependentPlanDecision.Candidate -> decision.plan.copy(
            policyId = policyId,
            name = "Engine 1.5 independent running plan",
            explanations = decision.plan.explanations.filterNot { it.startsWith("Preview only:") } +
                "Development-route draft. Review every easy/long run and explicitly approve before use; no quality prescription is included."
        )
    }

    fun preview(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        existingPlans: List<PlanSnapshot>? = null
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
        if (request.weeklySlots.keys.any { it != "run" })
            return defer("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT", "This Engine 1.5 running route cannot accept fixed assignments for another discipline.")
        val assignedRunDays = request.weeklySlots["run"]
        if (assignedRunDays != null && (assignedRunDays.size != request.runDays ||
                assignedRunDays.any { it !in request.days } || assignedRunDays.distinct().size != assignedRunDays.size ||
                assignedRunDays.firstOrNull() != request.longDay))
            return defer("RUNNING_SLOT_ASSIGNMENTS_INVALID", "Every running slot needs a distinct available weekday, with its first assignment matching the selected long-run day.")
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
        val lockPattern = Regex(".*-w([1-9][0-9]*)-run(0|[1-9][0-9]*)$")
        val lockedSlots = request.lockedSessionDays.mapNotNull { (sessionId, rawDate) ->
            val match = lockPattern.matchEntire(sessionId)
                ?: return defer("SESSION_LOCK_MAPPING_UNSUPPORTED", "A locked workout ID does not identify an Engine 1.5 running week and slot.")
            val week = match.groupValues[1].toInt()
            val runIndex = match.groupValues[2].toInt()
            val slot = week to m5SlotForRunIndex(request.runDays, runIndex)
            (slot to LocalDate.parse(rawDate))
        }.toMap()
        if (lockedSlots.size != request.lockedSessionDays.size)
            return defer("SESSION_LOCK_MAPPING_UNSUPPORTED", "Multiple locks target the same Engine 1.5 running slot.")
        if (lockedSlots.keys.any { (week, slot) -> week !in 1..request.weeks || slot !in 1..request.runDays })
            return defer("SESSION_LOCK_MAPPING_UNSUPPORTED", "A locked workout ID refers to a week or run slot outside this plan.")

        val assignedSlots = assignedRunDays?.flatMapIndexed { runIndex, weekday ->
            val slot = m5SlotForRunIndex(request.runDays, runIndex)
            (1..request.weeks).map { week -> (week to slot) to weekday }
        }?.toMap().orEmpty()

        val external = try {
            externalPlanContext(request, owner, existingPlans)
        } catch (e: PlanConstraint) {
            val detail = e.message.orEmpty()
            return defer(detail.substringBefore(':').ifBlank { "EXTERNAL_PLAN_CONTEXT_UNSUPPORTED" }, detail.substringAfter(':').trim())
        }

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
            blockedDates = external.blockedRunDates,
            lockedSlotDates = lockedSlots,
            assignedSlotDays = assignedSlots
        )
        val periodization = when (val outcome = Engine15M6IndependentPeriodizationReview.audit(
            volume,
            Engine15M6CReviewInput(
                calendar = calendar,
                externalSessions = external.sessions,
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
                val runIndex = legacyRunIndex(request.runDays, slot.slotNumber, slot.role)
                val sessionId = request.lockedSessionDays.keys.firstOrNull { lockedId ->
                    lockPattern.matchEntire(lockedId)?.let {
                        it.groupValues[1].toInt() == week.weekNumber && it.groupValues[2].toInt() == runIndex
                    } == true
                } ?: "$id-w${week.weekNumber}-run$runIndex"
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

    private data class ExternalPlanContext(
        val blockedRunDates: Set<LocalDate>,
        val sessions: List<Engine15M6CExternalSession>
    )

    private fun externalPlanContext(
        request: PlanRequest,
        owner: String,
        plans: List<PlanSnapshot>?
    ): ExternalPlanContext {
        if (plans == null) return ExternalPlanContext(emptySet(), emptyList())
        val first = request.start.minusDays(1)
        val last = request.start.plusWeeks(request.weeks.toLong())
        val sessions = plans.asSequence().filter { it.owner == owner && it.status in setOf("approved", "paused") }
            .flatMap { plan -> plan.sessions.asSequence().filter { it.status in setOf("planned", "in_progress") }
                .map { plan.id to it } }
            .filter { (_, session) -> session.date in first..last }
            .toList()
        val blockedRuns = sessions.filter { it.second.discipline == "run" }.map { it.second.date }.toSet()
        val external = sessions.filter { it.second.discipline != "run" }.map { (planId, session) ->
            val discipline = when (session.discipline) {
                "str" -> Engine15M6CDiscipline.STRENGTH
                "cal" -> Engine15M6CDiscipline.CALISTHENICS
                "cond" -> Engine15M6CDiscipline.CONDITIONING
                else -> throw PlanConstraint("EXTERNAL_DISCIPLINE_UNSUPPORTED: A saved plan includes an unknown discipline; its workload cannot be safely ignored.")
            }
            val load = if (session.quality) Engine15M6CLoad.FULL_BODY_HARD else when (discipline) {
                Engine15M6CDiscipline.STRENGTH -> if (session.lower) Engine15M6CLoad.LOWER_BODY else Engine15M6CLoad.UPPER_BODY
                Engine15M6CDiscipline.CALISTHENICS -> Engine15M6CLoad.FULL_BODY_HARD
                Engine15M6CDiscipline.CONDITIONING -> Engine15M6CLoad.EASY_GENERAL
            }
            Engine15M6CExternalSession("$planId:${session.id}", session.date, discipline, load)
        }
        return ExternalPlanContext(blockedRuns, external)
    }

    private fun m5SlotForRunIndex(runDays: Int, runIndex: Int): Int =
        if (runDays >= 3 && runIndex == 0) runDays else if (runDays >= 3) runIndex else runIndex + 1

    private fun legacyRunIndex(runDays: Int, slotNumber: Int, role: Engine15M5RunRole): Int =
        if (runDays >= 3 && role == Engine15M5RunRole.LONG) 0
        else if (runDays >= 3) slotNumber else slotNumber - 1

    fun conflict(a: PlannedSession, b: PlannedSession): Boolean {
        if (a.date == b.date) return true
        val gap = kotlin.math.abs(ChronoUnit.DAYS.between(a.date, b.date))
        fun hard(session: PlannedSession) = session.quality ||
            session.discipline == "cal" || session.discipline == "str" && session.lower
        fun longRun(session: PlannedSession) = session.discipline == "run" && session.kind == "long"
        return hard(a) && hard(b) && gap < 2 ||
            longRun(a) && hard(b) && gap < 2 || longRun(b) && hard(a) && gap < 2
    }

    fun validateSaved(plan: PlanSnapshot) {
        if (plan.policyId == ENGINE15_INDEPENDENT_CALISTHENICS_POLICY) {
            if (plan.engineVersion != version)
                throw PlanConstraint("The independent calisthenics plan has an unsupported engine identity.")
            Engine15CalisthenicsWorkoutComposer.validateSaved(plan)
            return
        }
        if (plan.policyId == ENGINE15_INDEPENDENT_STRENGTH_POLICY) {
            if (plan.engineVersion != version)
                throw PlanConstraint("The independent strength plan has an unsupported engine identity.")
            Engine15StrengthWorkoutComposer.validateSaved(plan)
            return
        }
        if (plan.engineVersion != version || plan.policyId != policyId)
            throw PlanConstraint("The independent running plan has an unsupported engine identity.")
        val expected = when (val decision = preview(plan.request, plan.owner, plan.id, plan.createdAt)) {
            is Engine15IndependentPlanDecision.Deferred ->
                throw PlanConstraint("${decision.reasonCode}: ${decision.explanation}")
            is Engine15IndependentPlanDecision.Candidate -> decision.plan
        }
        if (plan.sessions.size != expected.sessions.size ||
            plan.sessions.map { it.id }.toSet() != expected.sessions.map { it.id }.toSet())
            throw PlanConstraint("The independent running plan is missing or duplicating generated sessions.")
        val expectedById = expected.sessions.associateBy { it.id }
        if (plan.sessions.any { session ->
                val source = expectedById[session.id] ?: return@any true
                session.discipline != "run" || session.quality || session.kind != source.kind ||
                    session.distanceKm != source.distanceKm || session.runningPrescription != source.runningPrescription ||
                    session.date.isBefore(plan.request.start) || !session.date.isBefore(plan.request.start.plusWeeks(plan.request.weeks.toLong())) ||
                    session.date.dayOfWeek.value !in plan.request.days
            })
            throw PlanConstraint("A saved independent running session no longer matches its reviewed workload or available calendar.")
        Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
        if (plan.sessions.groupBy { it.date }.values.any { it.size > 1 })
            throw PlanConstraint("The independent running plan schedules more than one run on a date.")
        val expectedTotals = expected.sessions.groupBy { ChronoUnit.DAYS.between(plan.request.start, it.date) / 7L }
            .mapValues { (_, rows) -> rows.sumOf { it.distanceKm ?: 0.0 } }
        val actualTotals = plan.sessions.groupBy { ChronoUnit.DAYS.between(plan.request.start, it.date) / 7L }
            .mapValues { (_, rows) -> rows.sumOf { it.distanceKm ?: 0.0 } }
        if (actualTotals != expectedTotals)
            throw PlanConstraint("The saved independent running plan's weekly distance no longer reconciles.")
    }
}
