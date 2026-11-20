package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

internal const val ENGINE15_INDEPENDENT_CANDIDATE_POLICY =
    "stridelab.engine15.independent-running-candidate-20261117.1"
internal const val ENGINE15_INDEPENDENT_RUNNING_POLICY =
    "stridelab.engine15.independent-running-20261117.1"
private const val QUALITY_POLICY = "stridelab.engine15.experimental-quality-v1"
private const val TEMPO_EXPOSURE_NOTE =
    "Tempo exposure policy v1: loading mileage stays within the usual baseline and each Tempo slot fits the reported longest completed run."
internal const val ENGINE15_INDEPENDENT_STRENGTH_POLICY =
    "stridelab.engine15.independent-strength-20261117.1"
internal const val ENGINE15_INDEPENDENT_CALISTHENICS_POLICY =
    "stridelab.engine15.independent-calisthenics-20261117.1"
internal const val ENGINE15_INDEPENDENT_CONDITIONING_POLICY =
    "stridelab.engine15.independent-conditioning-20261117.1"
internal const val ENGINE15_INDEPENDENT_COMBINED_POLICY =
    "stridelab.engine15.independent-combined-20261117.1"

internal sealed interface Engine15IndependentPlanDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val qualitySelection: Engine15QualityFamilySelectionResult? = null,
        val qualityDryRun: Engine15QualityWorkoutDryRun? = null
    ) : Engine15IndependentPlanDecision

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
                it.discipline == "run" && (!it.quality ||
                    it.runningPrescription?.let { prescription -> prescription.role=="QUALITY" && prescription.family!=null } == true)
            })
            require(!executable && !productionReady && !authorizedForProduction)
        }
    }
}

/**
 * Assembles the reviewed independent M3–M6 running path into app workout
 * contracts. Coach-generated quality and unresolved inputs defer.
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
        if (listOf("run", "str", "cal", "cond").count { request.disciplineSessionCount(it) > 0 } > 1) {
            Engine15StaticMultiDisciplinePlanner.validateRequest(request)
            return
        }
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays == 0 && request.conditioningDays > 0) {
            Engine15ConditioningWorkoutComposer.validateRequest(request)
            return
        }
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
        exerciseLibrary: ExerciseLibraryV2? = null,
        activityEvidence: ActivityAthleteEvidenceProfile? = null,
        qualityEvidence: List<IndependentQualitySegmentEvidence> = emptyList()
    ): PlanSnapshot {
        if (listOf("run", "str", "cal", "cond").count { request.disciplineSessionCount(it) > 0 } > 1) {
            val library = exerciseLibrary ?: throw PlanConstraint(
                "ENGINE15_COMBINED_CATALOG_REQUIRED: The shared offline exercise catalog is unavailable; no substitute was generated."
            )
            return Engine15StaticMultiDisciplinePlanner.compose(request, owner, id, now, plans, library)
        }
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays == 0 && request.conditioningDays > 0) {
            Engine15ConditioningWorkoutComposer.validateRequest(request)
            val library = exerciseLibrary ?: throw PlanConstraint(
                "CONDITIONING_CATALOG_REQUIRED: The shared offline exercise catalog is unavailable; no substitute was generated."
            )
            val existing = plans.asSequence().filter { it.owner == owner && it.status in setOf("approved", "paused") }
                .flatMap { plan -> plan.sessions.asSequence().filter { it.status in setOf("planned", "in_progress") }.map { it.date } }
                .any { it in request.start.minusDays(1)..request.start.plusWeeks(request.weeks.toLong()) }
            if (existing) throw PlanConstraint(
                "EXTERNAL_SCHEDULE_REVIEW_REQUIRED: Conditioning-only generation does not yet coordinate known saved sessions; no existing session was ignored."
            )
            RecordPolicy.identifier(owner)
            RecordPolicy.identifier(id)
            val sessions = Engine15ConditioningWorkoutComposer.compose(request, id, library)
            return PlanSnapshot(
                id = id, owner = owner, engineVersion = version,
                policyId = ENGINE15_INDEPENDENT_CONDITIONING_POLICY, createdAt = now,
                request = request, sessions = sessions,
                explanations = listOf(
                    "Independent Engine 1.5 conditioning plan; no legacy generator or coaching validator was used.",
                    "The low-impact stepping exercise is selected from the shared offline educational catalog; Engine 1.5 owns the fixed four-week duration progression and recovery weeks.",
                    "This static plan does not increase effort or recalculate workload from completed sessions.",
                    "This is a draft. Review the movement, schedule and comfortable talk-test limit before explicit approval."
                ),
                name = "Engine 1.5 independent conditioning plan"
            )
        }
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
        return appDraft(preview(request, owner, id, now, plans, activityEvidence, qualityEvidence))
    }

    private fun appDraft(decision: Engine15IndependentPlanDecision): PlanSnapshot = when (decision) {
        is Engine15IndependentPlanDecision.Deferred ->
            throw PlanConstraint("${decision.reasonCode}: ${decision.explanation}")
        is Engine15IndependentPlanDecision.Candidate -> decision.plan.copy(
            policyId = policyId,
            name = if (decision.plan.sessions.any { it.title == "Experimental Threshold" })
                "Private debug · Experimental Threshold plan" else if (decision.plan.sessions.any { it.title == "Experimental Tempo" })
                "Private debug · Experimental Tempo plan" else "Engine 1.5 independent running plan",
            explanations = decision.plan.explanations.filterNot { it.startsWith("Preview only:") } +
                "Development-route draft. Review every session and explicitly approve before use."
        )
    }

    fun preview(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        existingPlans: List<PlanSnapshot>? = null,
        activityEvidence: ActivityAthleteEvidenceProfile? = null,
        qualityEvidence: List<IndependentQualitySegmentEvidence> = emptyList(),
        applyTempoExposureGate: Boolean = true
    ): Engine15IndependentPlanDecision {
        fun defer(code: String, explanation: String) =
            Engine15IndependentPlanDecision.Deferred(code, explanation)

        if (activityEvidence != null && (activityEvidence.version != ActivityAthleteEvidenceProfile.VERSION ||
                activityEvidence.owner != owner))
            return defer("ACTIVITY_EVIDENCE_SCOPE_INVALID", "Activity evidence is from an unsupported profile version or another local owner; it was not used.")

        if (request.policyChoice != "engine15")
            return defer("ENGINE15_NOT_SELECTED", "Select Engine 1.5 before requesting an independent candidate.")
        if (request.userDirectedPlanning && !BuildConfig.DEBUG)
            return defer("USER_DIRECTED_POLICY_DEBUG_ONLY", "User-directed planning is available only in private development builds.")
        if (request.runningRecoveryHold)
            return defer("RUNNING_RECOVERY_HOLD", "Running remains on hold until the recovery review is resolved.")
        if (request.qualityCount !in 0..2)
            return defer("QUALITY_FREQUENCY_UNSUPPORTED", "At most two quality sessions per loading week can be represented.")
        if (request.userDefinedQualityWorkouts.isNotEmpty() && !request.userDirectedPlanning)
            return defer("USER_DEFINED_WORKOUT_DEBUG_ONLY", "User-defined quality workouts are available only in private development plans.")
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

        val athlete = Engine15AthleteBaseline.assess(request, activityEvidence = activityEvidence)
        val volume = Engine15IndependentVolumePlanner.propose(
            athlete,
            Engine15IndependentVolumePlanner.Input(
                weeks = request.weeks,
                runningDays = request.runDays,
                targetWeeklyKm = request.targetWeeklyKm,
                returning = request.returning,
                recoveryHold = request.runningRecoveryHold,
                userDirected = request.userDirectedPlanning
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

        var experimentalTempo = emptyList<PlannedSession>()
        if (request.qualityCount > 0 && request.userDefinedQualityWorkouts.isEmpty()) {
            val selection = Engine15QualityFamilySelection.select(
                request = request,
                ownerId = owner,
                asOf = now.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                evidence = qualityEvidence,
                athlete = athlete,
                scheduledSessions = sessions,
                recoveryWeeks = volume.weeks.filter {
                    it.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY
                }.map { it.index + 1 }.toSet(),
                externalSessions = external.sessions
            )
            val qualityDryRun = Engine15QualityWorkoutDryRunBuilder.build(
                request, selection, volume, sessions
            )
            val familyName = when (selection.selectedFamily) {
                IndependentQualityFamily.THRESHOLD -> "Threshold"
                IndependentQualityFamily.INTERVALS -> "Intervals"
                IndependentQualityFamily.TEMPO, null -> "Tempo"
            }
            val paceWarnings = qualityDryRun?.alternatives.orEmpty().mapNotNull { alternative ->
                alternative.distanceMatch?.qualityPaceWarning()?.let { "Week ${alternative.weekNumber}: $it" }
            }
            val usualKm = athlete.typicalWeeklyKm
            val tempoExposureHold = qualityDryRun
                ?.takeIf { applyTempoExposureGate && request.weeks == 8 && request.experience in 1..2 && !request.userDirectedPlanning }
                ?.let { dryRun ->
                when {
                    request.longestKm <= 0.0 -> "Enter a recent longest completed run before drafting Experimental $familyName."
                    dryRun.alternatives.any { it.originalEasyDistanceKm > request.longestKm + 1e-6 } ->
                        "An Experimental $familyName slot exceeds the reported longest completed run. Review the distance budget."
                    usualKm != null && dryRun.weeklyDistance.any { it.budgetKm > usualKm + 1e-6 } ->
                        "The pilot separates introducing $familyName from building beyond usual weekly mileage. Set a lower target or review an easy-only plan."
                    else -> null
                }
            }
            if (tempoExposureHold == null && paceWarnings.isEmpty() && BuildConfig.DEBUG && BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE &&
                request.weeks == 8 && request.experience in 1..2 && !request.userDirectedPlanning &&
                qualityDryRun?.completePlanDistanceAccounting == true &&
                (!request.timeBudgetEnabled || request.minutes * 60L >= qualityDryRun.totalSeconds)
            ) {
                experimentalTempo = qualityDryRun.alternatives.mapNotNull { alternative ->
                    val match = requireNotNull(alternative.distanceMatch)
                    val original = sessions.single { it.id == alternative.sessionId }
                    if (match.segments.any { it.assertedMeters != it.assertedMeters.toInt().toDouble() })
                        return@mapNotNull null
                    val meters = match.segments.sumOf { it.assertedMeters.toInt() }
                    if (kotlin.math.abs(meters / 1000.0 - alternative.originalEasyDistanceKm) > 1e-8)
                        return@mapNotNull null
                    val steps = match.segments.map { segment ->
                        val work = segment.purpose == WorkoutPurpose.WORK
                        RunningStepV2(segment.purpose.name,
                            if (work) when (familyName) {
                                "Threshold" -> "Controlled, comfortably hard effort. Keep repetitions even and ease off if effort rises."
                                "Intervals" -> "Hard, repeatable effort. Keep each repetition controlled and use the full easy recovery between efforts."
                                else -> "Steady, controlled effort. Ease off if you cannot speak a short phrase."
                            }
                            else "Easy conversational effort",
                            seconds = segment.seconds.toInt(),
                            distanceEquivalentMeters = segment.assertedMeters.toInt())
                    }
                    original.copy(kind = "quality", quality = true, title = "Experimental $familyName",
                        minutes = steps.sumOf { requireNotNull(it.seconds) } / 60,
                        runningPrescription = RunningPrescription("QUALITY", familyName, emptyList(),
                            mixedSegments = steps, reconciledDistanceMeters = meters),
                        steps = steps.map {
                            "${it.seconds!! / 60} min ${it.purpose.lowercase().replace('_', ' ')} · ${it.distanceEquivalentMeters} m asserted · ${it.effort}"
                        },
                        reason = when (familyName) {
                            "Threshold" -> "Private Threshold introduction using M2 Intro. Three four-minute work repetitions with two minutes easy between them. No completed Threshold exposure, pace or heart-rate target is inferred."
                            "Intervals" -> "Private Intervals introduction using M2 Intro. Four two-minute work repetitions with two minutes easy between them. No completed Intervals exposure, pace or heart-rate target is inferred."
                            else -> "Private Tempo pilot. This slot fits reported recent run distance and the plan stays within usual weekly mileage. Work stays at 10 minutes because completed Tempo exposure is unverified. Favor the controlled effort cue and review your distance entries."
                        })
                }
                if (experimentalTempo.size != qualityDryRun.alternatives.size ||
                    experimentalTempo.any { quality ->
                        sessions.any { it.id != quality.id && conflict(quality, it) } ||
                            experimentalTempo.any { it.id != quality.id && conflict(quality, it) }
                    }) experimentalTempo = emptyList()
            }
            if (experimentalTempo.isEmpty()) {
            val dryRunNote = when {
                selection.decision != Engine15QualitySelectionDecision.FAMILY_CANDIDATE ->
                    "No family dry-run was constructed because selection deferred or is unsupported."
                selection.selectionBasis != Engine15QualitySelectionBasis.GOAL_EXPERIENCE_MATRIX ->
                    "No coach-generated dry-run was constructed because this family came from explicit user direction."
                selection.selectedFamily in setOf(IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.INTERVALS) && qualityDryRun == null ->
                    "$familyName introduction needs eight weeks, at least four running days and a reported longest completed run; no Tempo substitution was made."
                qualityDryRun == null ->
                    "The Tempo structure could not be linked to a valid development calendar slot and complete base weekly-distance ledger."
                paceWarnings.isNotEmpty() -> paceWarnings.joinToString(" ")
                tempoExposureHold != null -> tempoExposureHold
                qualityDryRun.completePlanDistanceAccounting ->
                    "Segment arithmetic reconciled. Private drafting requires an eight-week coached plan, experience level 1 or 2, whole-meter equivalents and a feasible schedule."
                else ->
                    "A development-only $familyName Intro structure was dry-run for week ${qualityDryRun.weekNumber}: ${qualityDryRun.totalSeconds} seconds total, ${qualityDryRun.workSeconds} seconds work. For unresolved slots, quality distance and weekly mileage remain unknown. Quality slots (week / slot / total metres): ${qualityDryRun.alternatives.joinToString { "${it.weekNumber} / ${it.slotNumber} / ${(it.originalEasyDistanceKm * 1000).toInt()}" + (it.distanceFailureCode?.let { code -> " ($code)" } ?: "") }}."
            }
            return Engine15IndependentPlanDecision.Deferred(
                "QUALITY_GENERATION_NOT_QUALIFIED",
                "${selection.explanation} This family choice remains a provisional engineering rule, not a qualified prescription. $dryRunNote Coach-generated quality remains non-executable until family, dose, intensity and distance policies are qualified.",
                selection,
                qualityDryRun
            )
            }
        }

        val customPattern=Regex("w([1-9][0-9]*)-run(0|[1-9][0-9]*)")
        val mappedQuality=request.userDefinedQualityWorkouts.map { (key,workout) ->
            val match=customPattern.matchEntire(key)
                ?: return defer("USER_WORKOUT_SLOT_INVALID", "A user-defined workout must target a stable w<week>-run<slot> identifier.")
            val week=match.groupValues[1].toIntOrNull()
                ?: return defer("USER_WORKOUT_SLOT_INVALID", "The workout week is outside the supported calendar range.")
            val slot=match.groupValues[2].toIntOrNull()
                ?: return defer("USER_WORKOUT_SLOT_INVALID", "The workout slot is outside the supported calendar range.")
            if(week !in 1..request.weeks || slot !in 0 until request.runDays)
                return defer("USER_WORKOUT_SLOT_INVALID", "A user-defined workout refers to a week or running slot outside this plan.")
            if(workout.mixedSegments!=null && workout.reconciledDistanceMeters==null)
                return defer("USER_DEFINED_MIXED_DISTANCE_UNRECONCILED", "Timed/mixed-unit workouts need an explicit per-step distance basis before they can reconcile to weekly mileage; no recovery distance was inferred.")
            val purposes=workout.mixedSegments?.map { it.purpose } ?: workout.segments.map { it.purpose }
            val workIndices=purposes.indices.filter { purposes[it]=="WORK" }
            if(workout.role!="QUALITY" || workout.family==null || workIndices.isEmpty() ||
                purposes.firstOrNull()!="WARM_UP" || purposes.lastOrNull()!="COOL_DOWN" ||
                purposes.drop(1).dropLast(1).any { it in setOf("WARM_UP","COOL_DOWN") } ||
                purposes.indices.any { i -> purposes[i]=="RECOVERY" &&
                    (workIndices.none { it<i } || workIndices.none { it>i }) })
                return defer("USER_WORKOUT_STRUCTURE_INVALID", "A user-defined quality workout needs warm-up, one or more ordered work steps, recoveries only between work steps, and cooldown.")
            key to workout
        }.toMap()
        for(week in 1..request.weeks) {
            val customCount=mappedQuality.keys.count { customPattern.matchEntire(it)?.groupValues?.get(1)?.toInt()==week }
            val shouldHaveQuality=if(experimentalTempo.isNotEmpty() || (week-1)%4==3) 0 else request.qualityCount
            if(customCount!=shouldHaveQuality)
                return defer("USER_WORKOUT_FREQUENCY_MISMATCH", "Provide exactly the requested number of user-defined workouts for each loading week and none for scheduled recovery weeks.")
        }
        val qualityById=mutableMapOf<String,RunningPrescription>()
        for(session in sessions) {
            val match=Regex(".*-(w[1-9][0-9]*-run(?:0|[1-9][0-9]*))$").matchEntire(session.id) ?: continue
            mappedQuality[match.groupValues[1]]?.let { workout ->
                val workoutMeters=workout.reconciledDistanceMeters ?: workout.segments.sumOf { it.meters }
                if(session.kind=="long" || session.distanceKm==null ||
                    kotlin.math.abs(workoutMeters/1000.0-session.distanceKm)>1e-6)
                    return defer("USER_WORKOUT_DISTANCE_CONFLICT", "The complete user-defined workout distance (${"%.1f".format(java.util.Locale.US,workoutMeters/1000.0)} km) must exactly fit its scheduled run slot (${session.distanceKm} km), including warm-up, every work and recovery step, and cooldown.")
                if(request.qualityDays.isNotEmpty() && session.date.dayOfWeek.value !in request.qualityDays)
                    return defer("USER_WORKOUT_DAY_CONFLICT", "The selected workout slot does not land on one of the requested quality weekdays.")
                if(volume.weeks[match.groupValues[1].substringAfter('w').substringBefore('-').toInt()-1].kind==Engine15IndependentVolumePlanner.WeekKind.RECOVERY)
                    return defer("USER_WORKOUT_RECOVERY_WEEK", "Quality workouts are deferred during scheduled recovery weeks; choose an easy session or move it to a loading week.")
                qualityById[session.id]=workout
            }
        }
        if(qualityById.size!=mappedQuality.size)
            return defer("USER_WORKOUT_SLOT_NOT_FOUND", "A user-defined workout could not be matched to a generated running session.")
        val qualitySessions=sessions.filter { it.id in qualityById }.map { session ->
            val workout=qualityById.getValue(session.id)
            session.copy(quality=true,kind="quality",title="User-defined ${workout.family} workout",
                runningPrescription=workout,
                steps=workout.mixedSegments?.mapIndexed { i,step ->
                    val extent=if(step.meters!=null) "${step.meters} m" else "${step.seconds} s"
                    val equivalent=step.distanceEquivalentMeters?.let { " ($it m user-supplied distance equivalent)" }.orEmpty()
                    "${i+1}. $extent${equivalent} ${step.purpose.lowercase().replace('_',' ')} — ${step.effort}."
                } ?: workout.segments.mapIndexed { i,step -> "${i+1}. ${step.meters} m ${step.purpose.lowercase().replace('_',' ')} — ${step.effort}." },
                reason="User-defined workout structure and intensity targets are preserved as supplied; no coach qualification or athlete-specific intensity validation is claimed." +
                    if(workout.reconciledDistanceMeters!=null) " Timed steps use explicit user-supplied distance equivalents; none were inferred." else "")
        }
        if(qualitySessions.any { quality -> sessions.any { other -> other.id!=quality.id && conflict(quality,other) } })
            return defer("USER_WORKOUT_SPACING_CONFLICT", "A user-defined quality workout is too close to another quality session or the long run.")
        if(qualitySessions.any { quality -> external.sessions.any { other ->
                other.hardLowerOrFull && kotlin.math.abs(ChronoUnit.DAYS.between(quality.date,other.date))<2
            } })
            return defer("USER_WORKOUT_EXTERNAL_SPACING_CONFLICT", "A user-defined quality workout is too close to a known external hard session.")
        val replacements = (qualitySessions + experimentalTempo).associateBy { it.id }
        val assembledSessions=sessions.map { replacements[it.id] ?: it }

        val snapshot = PlanSnapshot(
            id = id,
            owner = owner,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_CANDIDATE_POLICY,
            createdAt = now,
            request = request,
            sessions = assembledSessions,
            explanations = buildList {
                add("Independent Engine 1.5 running candidate; no Engine 1.0 generation or validation was used.")
                add("Mileage basis: ${athlete.basis}; reported/recorded history remains provisional and is not source-authenticated.")
                athlete.activityEvidence?.let { evidence ->
                    add("ACTIVITY_EVIDENCE_CONTEXT v${evidence.version}: ${evidence.recordedRunCount} recorded runs; " +
                        "longest recorded in the recent window: ${evidence.longestRecordedRunLast28DaysKm?.let { "%.1f km".format(java.util.Locale.US, it) } ?: "not available"}; " +
                        "source coverage is ${evidence.sourceCoverage}. This evidence is descriptive only and did not set weekly capacity, quality eligibility, workout dose, or intensity.")
                }
                if(request.userDirectedPlanning)
                    add("Weekly volume follows the user-requested workload, with scheduled recovery weeks. This is not validated athlete capacity; coach-recommended progression remains available for comparison.")
                if(experimentalTempo.isNotEmpty()) add("Private debug quality pilot v1. The fixed dose and perceived-effort cue are provisional. Each timed step uses a runner-asserted distance equivalent. Review each session and approve explicitly before use.")
                if (experimentalTempo.isNotEmpty() && applyTempoExposureGate) add(TEMPO_EXPOSURE_NOTE)
                if(qualityById.isNotEmpty()) add("Quality workout steps and any intensity targets were explicitly user-defined; no automatic coach selection or intensity prescription was used. Timed steps are included in mileage only when explicit per-step distance equivalents were supplied.")
                if(request.userDirectedPlanning) volume.weeks.flatMap { it.constraintCodes }.distinct()
                    .filter { it in setOf("REQUESTED_TARGET_ABOVE_COACH_RECOMMENDED","PROVISIONAL_SESSION_EXPOSURE_WARNING","RETURN_FROM_BREAK_RECOMMENDATION_EXCEEDED") }
                    .takeIf { it.isNotEmpty() }?.let { add("Conservative recommendations exceeded by user request: ${it.joinToString()}; review before approval.") }
                add("M4B owns weekly volume and recovery weeks; M5A owns easy/long workout distances; M6A/M6C/M6D own date placement, interference review and periodization audit.")
                add("Duration is an estimate from the entered planning pace. No pace or heart-rate target is prescribed.")
                add("Preview only: executable, production-ready and production-authorized are all false.")
                volume.shortfallToRequestedTargetKm?.takeIf { it > 0.01 }?.let {
                    add("Requested target shortfall: ${"%.1f".format(java.util.Locale.US, it)} km/week; the candidate does not add unreviewed mileage to close it.")
                }
                addAll(volume.reasons)
            },
            name = "Engine 1.5 independent running preview",
            experimentalQualityPolicy = QUALITY_POLICY.takeIf { experimentalTempo.isNotEmpty() && applyTempoExposureGate }
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
            session.discipline == "cal" || session.discipline in setOf("str", "cond") && session.lower
        fun longRun(session: PlannedSession) = session.discipline == "run" && session.kind == "long"
        return hard(a) && hard(b) && gap < 2 ||
            longRun(a) && hard(b) && gap < 2 || longRun(b) && hard(a) && gap < 2
    }

    fun validateSaved(plan: PlanSnapshot) {
        if (plan.policyId == ENGINE15_INDEPENDENT_COMBINED_POLICY) {
            Engine15StaticMultiDisciplinePlanner.validateSaved(plan)
            return
        }
        if (plan.policyId == ENGINE15_INDEPENDENT_CONDITIONING_POLICY) {
            if (plan.engineVersion != version)
                throw PlanConstraint("The independent conditioning plan has an unsupported engine identity.")
            Engine15ConditioningWorkoutComposer.validateSaved(plan)
            return
        }
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
        if (plan.experimentalQualityPolicy != null && plan.experimentalQualityPolicy != QUALITY_POLICY)
            throw PlanConstraint("This plan uses an unsupported experimental quality policy.")
        // Historical pilots retain their original eligibility semantics during structural validation.
        val expected = when (val decision = preview(plan.request, plan.owner, plan.id, plan.createdAt,
            applyTempoExposureGate = plan.experimentalQualityPolicy == QUALITY_POLICY)) {
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
                session.discipline != "run" || session.quality != source.quality || session.kind != source.kind ||
                    session.distanceKm != source.distanceKm || session.runningPrescription != source.runningPrescription ||
                    session.date.isBefore(plan.request.start) || !session.date.isBefore(plan.request.start.plusWeeks(plan.request.weeks.toLong())) ||
                    session.date.dayOfWeek.value !in plan.request.days
            })
            throw PlanConstraint("A saved independent running session no longer matches its reviewed workload or available calendar.")
        Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
        if (plan.sessions.groupBy { it.date }.values.any { it.size > 1 })
            throw PlanConstraint("The independent running plan schedules more than one run on a date.")
        if (plan.experimentalQualityPolicy == QUALITY_POLICY) {
            for (quality in plan.sessions.filter { it.quality }) {
                val original = expectedById.getValue(quality.id)
                val originalWeek = ChronoUnit.DAYS.between(plan.request.start, original.date) / 7L
                val actualWeek = ChronoUnit.DAYS.between(plan.request.start, quality.date) / 7L
                if (actualWeek != originalWeek)
                    throw PlanConstraint("Experimental quality must stay in its reviewed loading week.")
                if (plan.sessions.any { it.id != quality.id && conflict(quality, it) })
                    throw PlanConstraint("Experimental quality must remain spaced from other quality and long runs.")
                if (plan.request.qualityDays.isNotEmpty() && quality.date.dayOfWeek.value !in plan.request.qualityDays)
                    throw PlanConstraint("Experimental quality must stay on a selected quality weekday.")
            }
        }
        val expectedTotals = expected.sessions.groupBy { ChronoUnit.DAYS.between(plan.request.start, it.date) / 7L }
            .mapValues { (_, rows) -> rows.sumOf { it.distanceKm ?: 0.0 } }
        val actualTotals = plan.sessions.groupBy { ChronoUnit.DAYS.between(plan.request.start, it.date) / 7L }
            .mapValues { (_, rows) -> rows.sumOf { it.distanceKm ?: 0.0 } }
        if (actualTotals.keys != expectedTotals.keys || expectedTotals.any { (week, km) ->
                kotlin.math.abs((actualTotals[week] ?: Double.NaN) - km) > 1e-6
            })
            throw PlanConstraint("The saved independent running plan's weekly distance no longer reconciles.")
    }
}
