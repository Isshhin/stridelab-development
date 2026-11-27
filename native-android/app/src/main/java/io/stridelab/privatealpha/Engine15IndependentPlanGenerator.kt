package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

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
            if (FeatureFlags.USE_ENGINE18_HYBRID_V1) {
                Engine18HybridProgramBuilder.validateRequest(request)
            } else {
                Engine15StaticMultiDisciplinePlanner.validateRequest(request)
            }
            return
        }
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays == 0 && request.conditioningDays > 0) {
            if (FeatureFlags.USE_ENGINE18_HYBRID_V1) {
                Engine18ConditioningWorkoutConstructor.validateSoloRequest(request)
            } else {
                Engine15ConditioningWorkoutComposer.validateRequest(request)
            }
            return
        }
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays > 0) {
            if (FeatureFlags.USE_ENGINE17_CALISTHENICS_V1) {
                Engine17CalisthenicsProgramBuilder.validateRequest(request)
                return
            }
            Engine15CalisthenicsWorkoutComposer.validateRequest(request)
            return
        }
        if (request.runDays == 0 && request.strengthDays > 0) {
            if (FeatureFlags.USE_ENGINE16_STRENGTH_V1 && strengthTrack(request) == "str") {
                Engine16StrengthProgramBuilder.validateRequest(request)
                return
            }
            Engine15StrengthWorkoutComposer.validateRequest(request)
            return
        }
        if (Engine15TimeOnlyRunWalkBuilder.isEligible(request)) {
            Engine15TimeOnlyRunWalkBuilder.compose(request, "engine15-validation", "validation", Instant.EPOCH)
            return
        }
        appDraft(previewAutoReconciled(request, "engine15-validation", "validation", Instant.EPOCH))
    }

    private fun formatMetersAsKm(meters: Int?): String {
        if (meters == null || meters <= 0) return "0.0 km"
        val km = meters / 1000.0
        return if (km < 1.0 && meters % 100 != 0) {
            String.format(java.util.Locale.US, "%.2f km", km)
        } else {
            String.format(java.util.Locale.US, "%.1f km", km)
        }
    }

    internal fun previewAutoReconciled(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        existingPlans: List<PlanSnapshot>? = null,
        activityEvidence: ActivityAthleteEvidenceProfile? = null,
        qualityEvidence: List<IndependentQualitySegmentEvidence> = emptyList(),
        activities: List<ActivityRecord> = emptyList(),
        detections: List<Engine15DetectedWorkout> = emptyList(),
        assertedExposureSecondsOverride: Int? = null
    ): Engine15IndependentPlanDecision {
        val isLegacyPositive = request.weeklyKm > 0.0 && request.recentMileageBasis == "LEGACY_UNSPECIFIED"
        val cleanedPeak = if (request.recentMileageBasis == "SELF_REPORTED_AVERAGE" || isLegacyPositive) {
            request.reportedPeakWeeklyKm?.takeIf {
                it.isFinite() && it >= request.weeklyKm && it <= 120.0
            }
        } else {
            request.reportedPeakWeeklyKm
        }
        val effectiveRequest = when {
            isLegacyPositive -> request.copy(
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                reportedPeakWeeklyKm = cleanedPeak
            )
            cleanedPeak != request.reportedPeakWeeklyKm -> request.copy(reportedPeakWeeklyKm = cleanedPeak)
            else -> request
        }
        val autoAdaptiveQuality = effectiveRequest.qualityCount > 0 &&
            effectiveRequest.userDefinedQualityWorkouts.isEmpty() &&
            effectiveRequest.qualityDistanceClaims.isEmpty()
        val enrichArchetypes = autoAdaptiveQuality
        var resolvedRequest = effectiveRequest
        var initialDecision = preview(
            resolvedRequest, owner, id, now, existingPlans, activityEvidence, qualityEvidence,
            assertedExposureSecondsOverride = assertedExposureSecondsOverride,
            allowExplicitUserFamily = true,
            adaptiveQualityMode = autoAdaptiveQuality,
            enrichArchetypes = enrichArchetypes
        )
        val qualityDeferralReason = initialDecision is Engine15IndependentPlanDecision.Deferred && (
            initialDecision.qualitySelection != null ||
                initialDecision.qualityDryRun != null ||
                initialDecision.reasonCode == "QUALITY_GENERATION_NOT_QUALIFIED"
            )
        if (qualityDeferralReason && autoAdaptiveQuality) {
            val startingDeferred = initialDecision as Engine15IndependentPlanDecision.Deferred
            if (startingDeferred.qualitySelection?.reasonCode == "QUALITY_SLOT_UNAVAILABLE" &&
                resolvedRequest.qualityDays.isNotEmpty()
            ) {
                resolvedRequest = resolvedRequest.copy(qualityDays = emptySet())
                initialDecision = preview(
                    resolvedRequest, owner, id, now, existingPlans, activityEvidence, qualityEvidence,
                    assertedExposureSecondsOverride = assertedExposureSecondsOverride,
                    allowExplicitUserFamily = true,
                    adaptiveQualityMode = true,
                    enrichArchetypes = enrichArchetypes
                )
            }
            if (initialDecision is Engine15IndependentPlanDecision.Deferred) {
                val activeDeferred = initialDecision
                val selectedFamily = activeDeferred.qualitySelection?.selectedFamily
                    ?: return activeDeferred
                val adaptiveExposure = assertedExposureSecondsOverride
                    ?: Engine15QualityProgressionPolicy.computeAdaptiveExposureAnchorSeconds(
                        request = resolvedRequest,
                        family = selectedFamily,
                        activities = activities,
                        plans = existingPlans.orEmpty(),
                        detections = detections,
                        now = now
                    )
                val zones = Engine15PaceZoneEstimator.estimate(resolvedRequest, activities = activities)

                fun tryReconcileWithExposure(
                    exposureOverride: Int?,
                    fallbackDeferred: Engine15IndependentPlanDecision.Deferred
                ): Engine15IndependentPlanDecision.Candidate? {
                    val deferredAttempt = if (exposureOverride != assertedExposureSecondsOverride) {
                        val next = preview(
                            resolvedRequest, owner, id, now, existingPlans, activityEvidence, qualityEvidence,
                            assertedExposureSecondsOverride = exposureOverride,
                            allowExplicitUserFamily = true,
                            adaptiveQualityMode = true,
                            enrichArchetypes = enrichArchetypes
                        )
                        if (next is Engine15IndependentPlanDecision.Candidate) return next
                        next as? Engine15IndependentPlanDecision.Deferred ?: fallbackDeferred
                    } else fallbackDeferred

                    val dryRun = deferredAttempt.qualityDryRun ?: return null
                    val autoClaims = Engine15PaceZoneEstimator.estimateSegmentClaims(dryRun, zones)
                    val resolvedQualityDays = dryRun.alternatives.map { it.slot.date.dayOfWeek.value }.toSet()
                    val enrichedRequest = resolvedRequest.copy(
                        qualityDays = resolvedQualityDays,
                        qualityDistanceClaims = autoClaims
                    )
                    val retry = preview(
                        enrichedRequest, owner, id, now, existingPlans, activityEvidence, qualityEvidence,
                        assertedExposureSecondsOverride = exposureOverride,
                        allowExplicitUserFamily = true,
                        adaptiveQualityMode = true,
                        enrichArchetypes = enrichArchetypes
                    ) as? Engine15IndependentPlanDecision.Candidate ?: return null

                    val retryPaceValid = dryRun.alternatives.zip(autoClaims).all { (alt, claim) ->
                        val rx = alt.prescription ?: return@all false
                        val rec = Engine15M5TimedDistanceReconciler.reconcileSegmentClaim(
                            alt.weekNumber, alt.slotNumber, rx.family,
                            alt.originalEasyDistanceKm, rx.step.workout,
                            rx.step.totalSeconds, rx.step.workSeconds, claim,
                            allowMixedUnits = true,
                            zones = zones
                        )
                        rec is Engine15M5SegmentDistanceDecision.ArithmeticOnly &&
                            rec.match.qualityPaceWarning(zones) == null &&
                            rec.match.bookendSafetyWarning() == null
                    }
                    return retry.takeIf { retryPaceValid }
                }

                // Steps 1 & 2: Reconcile claims with safe >= 8 min WU / >= 5 min CD bookends.
                tryReconcileWithExposure(adaptiveExposure, activeDeferred)?.let { return it }

                // Step 3: Step down within the same quality family ladder before ever failing.
                val ceilingSec = adaptiveExposure
                val lowerRungs = Engine15QualityProgressionPolicy.ladder(selectedFamily, resolvedRequest.experience)
                    .map { it.workSeconds }
                    .distinct()
                    .filter { it < ceilingSec }
                    .sortedDescending()
                val familyLabel = selectedFamily.name.lowercase().replaceFirstChar(Char::uppercase)
                for (rungWorkSec in lowerRungs) {
                    val steppedCandidate = tryReconcileWithExposure(rungWorkSec, activeDeferred)
                    if (steppedCandidate != null) {
                        return steppedCandidate.copy(
                            plan = steppedCandidate.plan.copy(
                                explanations = steppedCandidate.plan.explanations +
                                    "Quality workout dose was stepped down within the $familyLabel ladder to safely fit your session distance, warm-up/cool-down floors, and time budget."
                            )
                        )
                    }
                }

                // Step 4: Explicit reviewable deferral instead of silent qualityCount = 0 erasure.
                return Engine15IndependentPlanDecision.Deferred(
                    reasonCode = "QUALITY_RECONCILIATION_UNSAFE_OR_INFEASIBLE",
                    explanation = "Requested quality workouts could not safely fit within your current weekly mileage, session duration budget, and minimum warm-up/cool-down guardrails (8 min warm-up / 5 min cool-down). Adjust session duration, weekly mileage, or reduce quality frequency.",
                    qualitySelection = activeDeferred.qualitySelection,
                    qualityDryRun = activeDeferred.qualityDryRun
                )
            }
        }
        return initialDecision
    }

    fun generateWithHistory(
        request: PlanRequest,
        owner: String,
        plans: List<PlanSnapshot>,
        id: String = java.util.UUID.randomUUID().toString(),
        now: Instant = Instant.now(),
        exerciseLibrary: ExerciseLibraryV2? = null,
        activityEvidence: ActivityAthleteEvidenceProfile? = null,
        qualityEvidence: List<IndependentQualitySegmentEvidence> = emptyList(),
        activities: List<ActivityRecord> = emptyList(),
        detections: List<Engine15DetectedWorkout> = emptyList(),
        safetyRestrictions: AthleteSafetyRestrictionSet? = null
    ): PlanSnapshot {
        if (listOf("run", "str", "cal", "cond").count { request.disciplineSessionCount(it) > 0 } > 1) {
            if (FeatureFlags.USE_ENGINE18_HYBRID_V1) {
                return Engine18HybridProgramBuilder.build(
                    request = request,
                    owner = owner,
                    id = id,
                    now = now,
                    existingPlans = plans,
                    library = exerciseLibrary,
                    activities = activities,
                    detections = detections,
                    safetyRestrictions = safetyRestrictions
                )
            }
            val library = exerciseLibrary ?: throw PlanConstraint(
                "ENGINE15_COMBINED_CATALOG_REQUIRED: The shared offline exercise catalog is unavailable; no substitute was generated."
            )
            return Engine15StaticMultiDisciplinePlanner.compose(
                request, owner, id, now, plans, library, activities = activities, detections = detections
            )
        }
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays == 0 && request.conditioningDays > 0) {
            if (FeatureFlags.USE_ENGINE18_HYBRID_V1) {
                Engine18ConditioningWorkoutConstructor.validateSoloRequest(request, safetyRestrictions, now)
                RecordPolicy.identifier(owner)
                RecordPolicy.identifier(id)
                val (scheduledRequest, _) = soloExternalCalendar(request, owner, plans, "cond") { r ->
                    Engine18ConditioningWorkoutConstructor.composeSessions(
                        r, id, safetyRestrictions = safetyRestrictions, evaluatedAtUtc = now
                    )
                }
                return Engine18ConditioningWorkoutConstructor.buildSoloPlan(
                    scheduledRequest, owner, id, now, safetyRestrictions = safetyRestrictions
                )
            }
            Engine15ConditioningWorkoutComposer.validateRequest(request)
            val library = exerciseLibrary ?: throw PlanConstraint(
                "CONDITIONING_CATALOG_REQUIRED: The shared offline exercise catalog is unavailable; no substitute was generated."
            )
            RecordPolicy.identifier(owner)
            RecordPolicy.identifier(id)
            val (scheduledRequest, sessions) = soloExternalCalendar(request, owner, plans, "cond") {
                Engine15ConditioningWorkoutComposer.compose(it, id, library)
            }
            return PlanSnapshot(
                id = id, owner = owner, engineVersion = version,
                policyId = ENGINE15_INDEPENDENT_CONDITIONING_POLICY, createdAt = now,
                request = scheduledRequest, sessions = sessions,
                explanations = listOf(
                    "Independent Engine 1.5 conditioning plan; no legacy generator or coaching validator was used.",
                    "Work and recovery targets are set by the selected conditioning goal and reduced in recovery weeks.",
                    "This is a draft. Review the movement, schedule and comfortable talk-test limit before explicit approval."
                ),
                name = "Engine 1.5 independent conditioning plan"
            )
        }
        if (request.runDays == 0 && request.strengthDays == 0 && request.calDays > 0) {
            if (FeatureFlags.USE_ENGINE17_CALISTHENICS_V1) {
                Engine17CalisthenicsProgramBuilder.validateRequest(request, safetyRestrictions, now)
                RecordPolicy.identifier(owner)
                RecordPolicy.identifier(id)
                val (scheduledRequest, _) = soloExternalCalendar(request, owner, plans, "cal") { r ->
                    Engine17CalisthenicsProgramBuilder.build(
                        r, owner, id, now, safetyRestrictions = safetyRestrictions, evaluatedAtUtc = now
                    ).snapshot.sessions
                }
                return Engine17CalisthenicsProgramBuilder.build(
                    scheduledRequest, owner, id, now, safetyRestrictions = safetyRestrictions, evaluatedAtUtc = now
                ).snapshot
            }
            Engine15CalisthenicsWorkoutComposer.validateRequest(request)
            val library = exerciseLibrary ?: throw PlanConstraint(
                "CALISTHENICS_CATALOG_REQUIRED: The shared offline exercise catalog is unavailable; no substitute was generated."
            )
            RecordPolicy.identifier(owner)
            RecordPolicy.identifier(id)
            val (scheduledRequest, sessions) = soloExternalCalendar(request, owner, plans, "cal") {
                Engine15CalisthenicsWorkoutComposer.compose(it, id, library)
            }
            return PlanSnapshot(
                id = id, owner = owner, engineVersion = version,
                policyId = ENGINE15_INDEPENDENT_CALISTHENICS_POLICY, createdAt = now,
                request = scheduledRequest, sessions = sessions,
                explanations = listOf(
                    "Independent Engine 1.5 calisthenics plan; no legacy generator or coaching validator was used.",
                    "The selected goal and skill levels set the movement, repetition or hold targets and recovery periods.",
                    "This is a draft. Review each exercise, equipment requirement, schedule and repetition/hold target before explicit approval."
                ),
                name = "Engine 1.5 independent calisthenics plan"
            )
        }
        if (request.runDays == 0 && request.strengthDays > 0) {
            if (FeatureFlags.USE_ENGINE16_STRENGTH_V1 && strengthTrack(request) == "str") {
                Engine16StrengthProgramBuilder.validateRequest(request, safetyRestrictions, now)
                RecordPolicy.identifier(owner)
                RecordPolicy.identifier(id)
                val (scheduledRequest, _) = soloExternalCalendar(request, owner, plans, "str") { r ->
                    Engine16StrengthProgramBuilder.build(
                        r, owner, id, now, safetyRestrictions = safetyRestrictions
                    ).snapshot.sessions
                }
                return Engine16StrengthProgramBuilder.build(
                    scheduledRequest, owner, id, now, safetyRestrictions = safetyRestrictions
                ).snapshot
            }
            Engine15StrengthWorkoutComposer.validateRequest(request)
            val library = exerciseLibrary ?: throw PlanConstraint(
                "STRENGTH_CATALOG_REQUIRED: The shared offline exercise catalog is unavailable; no substitute was generated."
            )
            RecordPolicy.identifier(owner)
            RecordPolicy.identifier(id)
            val (scheduledRequest, sessions) = soloExternalCalendar(request, owner, plans, "str") {
                Engine15StrengthWorkoutComposer.compose(it, id, library)
            }
            return PlanSnapshot(
                id = id, owner = owner, engineVersion = version,
                policyId = ENGINE15_INDEPENDENT_STRENGTH_POLICY, createdAt = now,
                request = scheduledRequest, sessions = sessions,
                explanations = listOf(
                    "Independent Engine 1.5 strength plan; no Engine 1.0 generation or validation was used.",
                    "The selected goal sets the movement and whether the dose is prescribed in reps, hold seconds or ground contacts.",
                    "Every fourth week reduces exposure. Changes from workout feedback require review and approval.",
                    "This is a draft. Review each exercise, equipment requirement, schedule and repetition target before explicit approval."
                ),
                name = "Engine 1.5 independent strength plan"
            )
        }
        val runCap = request.effectiveSafetyCap("run", safetyRestrictions, now)
        val effectiveRunRequest = when {
            safetyRestrictions?.hasHardBlock(AthleteDiscipline.RUN, now) == true || runCap == SafetyIntensityCap.REST_ONLY ->
                request.copy(runningRecoveryHold = true)
            runCap == SafetyIntensityCap.EASY_AEROBIC_ONLY ->
                request.copy(qualityCount = 0, qualityDays = emptySet())
            runCap == SafetyIntensityCap.NO_HIGH_INTENSITY_Z3 ->
                request.copy(qualityCount = minOf(request.qualityCount, 1))
            else -> request
        }
        if (Engine15TimeOnlyRunWalkBuilder.isEligible(effectiveRunRequest)) {
            if (effectiveRunRequest.runningRecoveryHold) {
                throw PlanConstraint("RUNNING_RECOVERY_HOLD: Running remains on hold until the recovery review is resolved.")
            }
            RecordPolicy.identifier(owner)
            RecordPolicy.identifier(id)
            val external = externalPlanContext(effectiveRunRequest, owner, plans)
            return Engine15TimeOnlyRunWalkBuilder.compose(effectiveRunRequest, owner, id, now, external.blockedRunDates)
        }
        // Independent app plans share a calendar, but only overlapping running slots
        // and legacy policies enter the strict M6 interference review.
        val runningCalendar = plans.map { plan ->
            if (PlanScheduleValidation.isIndependent(plan.policyId)) plan.copy(
                sessions = plan.sessions.filter { it.discipline == "run" || it.status !in setOf("planned", "in_progress") }
            ) else plan
        }
        return appDraft(previewAutoReconciled(
            effectiveRunRequest, owner, id, now, runningCalendar, activityEvidence, qualityEvidence,
            activities = activities, detections = detections
        ))
    }

    private fun appDraft(decision: Engine15IndependentPlanDecision): PlanSnapshot = when (decision) {
        is Engine15IndependentPlanDecision.Deferred ->
            throw PlanConstraint("${decision.reasonCode}: ${decision.explanation}")
        is Engine15IndependentPlanDecision.Candidate -> {
            val req = decision.plan.request
            val optSessions = decision.plan.sessions.map { s ->
                val distM = (s.distanceKm ?: 0.0) * 1000.0
                OptimizerSessionCandidate(
                    id = s.id,
                    date = s.date,
                    discipline = s.discipline,
                    isHighStress = s.quality || s.kind == "long",
                    isLowerBodyHeavy = false,
                    durationMinSeconds = (s.minutes * 60).toDouble() * 0.85,
                    durationMaxSeconds = (s.minutes * 60).toDouble(),
                    distanceConservativeMeters = distM,
                    distanceOptimisticMeters = distM
                )
            }
            val availByDate = if (req.timeBudgetEnabled) {
                decision.plan.sessions.associate { s ->
                    s.date to OptimizerDayAvailability(
                        date = s.date,
                        windows = emptyList(),
                        unspecifiedAllDayBudgetSeconds = maxOf(s.minutes * 60, req.dailyMinutes * 60)
                    )
                }
            } else {
                emptyMap()
            }
            val finalCheck = Engine15RunningPlanOptimizer.validateFinal(optSessions, availByDate)
            val optimizerNote = "M4 F_final oracle: ${finalCheck.outcome.name}."
            decision.plan.copy(
                policyId = policyId,
                name = "StrideLab ${req.goal} Plan",
                explanations = decision.plan.explanations.filterNot { it.startsWith("Preview only:") } +
                    listOf(optimizerNote, "Engine 1.5 personalized plan ready for your review.")
            )
        }
    }

    fun preview(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        existingPlans: List<PlanSnapshot>? = null,
        activityEvidence: ActivityAthleteEvidenceProfile? = null,
        qualityEvidence: List<IndependentQualitySegmentEvidence> = emptyList(),
        applyTempoExposureGate: Boolean = true,
        assertedExposureSecondsOverride: Int? = null,
        allowExplicitUserFamily: Boolean = false,
        adaptiveQualityMode: Boolean = false,
        enrichArchetypes: Boolean = false
    ): Engine15IndependentPlanDecision {
        fun defer(code: String, explanation: String) =
            Engine15IndependentPlanDecision.Deferred(code, explanation)

        if (activityEvidence != null && (activityEvidence.version != ActivityAthleteEvidenceProfile.VERSION ||
                activityEvidence.owner != owner))
            return defer("ACTIVITY_EVIDENCE_SCOPE_INVALID", "Activity evidence is from an unsupported profile version or another local owner; it was not used.")

        if (request.policyChoice != "engine15")
            return defer("ENGINE15_NOT_SELECTED", "Select Engine 1.5 before requesting an independent candidate.")
        if (request.runningRecoveryHold)
            return defer("RUNNING_RECOVERY_HOLD", "Running remains on hold until the recovery review is resolved.")
        if (request.qualityCount !in 0..2)
            return defer("QUALITY_FREQUENCY_UNSUPPORTED", "At most two quality sessions per loading week can be represented.")
        if (request.userDefinedQualityWorkouts.isNotEmpty() && !request.userDirectedPlanning)
            return defer("USER_DEFINED_WORKOUT_DEBUG_ONLY", "User-defined quality workouts are available only in private development plans.")
        if (request.strengthDays != 0 || request.calDays != 0 || request.conditioningDays != 0 ||
            request.disciplineGoals.keys.any { it != "run" })
            return defer("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT", "Independent workout construction and calendar coordination for mixed disciplines are not available yet.")
        if (request.runDays !in 1..6 || request.weeks !in 1..16)
            return defer("RUNNING_SCOPE_UNSUPPORTED", "This candidate supports one to six running days and one to sixteen weeks.")
        if (request.days.isEmpty() || request.days.any { it !in 1..7 } || request.days.size < request.runDays ||
            ((request.runDays == 1 || request.runDays >= 3) && request.longDay !in request.days))
            return defer("RUNNING_AVAILABILITY_UNSUPPORTED", "Available weekdays must fit every run and the selected long-run day.")
        if (request.weeklySlots.keys.any { it != "run" })
            return defer("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT", "This Engine 1.5 running route cannot accept fixed assignments for another discipline.")
        val assignedRunDays = request.weeklySlots["run"]
        if (assignedRunDays != null && (assignedRunDays.size != request.runDays ||
                assignedRunDays.any { it !in request.days } || assignedRunDays.distinct().size != assignedRunDays.size ||
                assignedRunDays.firstOrNull() != request.longDay))
            return defer("RUNNING_SLOT_ASSIGNMENTS_INVALID", "Every running slot needs a distinct available weekday, with its first assignment matching the selected long-run day.")
        if (!request.weeklyKm.isFinite() || request.weeklyKm !in 0.0..120.0 ||
            !request.longestKm.isFinite() || request.longestKm !in 0.0..42.0 ||
            request.targetWeeklyKm?.let { !it.isFinite() || it !in 0.1..120.0 } == true ||
            request.planningPace.let { !it.isFinite() || it !in 180.0..1800.0 })
            return defer("INVALID_RUNNING_INPUT", "Recent mileage, longest-run exposure, target and planning pace must be valid before generation.")

        val hrProfile = if (request.runningTargetMode == "HR") {
            request.resolvedHrZoneProfile()
                ?: return defer("HR_ZONES_REQUIRED", "Configure your heart-rate zones (Garmin sync, LTHR, HRR, or Max HR) to generate a heart-rate-based running plan.")
        } else null

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
                userDirected = request.userDirectedPlanning,
                raceGoal = request.goal,
                experience = request.experience,
                useMultiFactorProgression = true
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

        val baseSessions = placedWeeks.flatMap { week ->
            week.runs.map { placed ->
                val slot = placed.slot
                val meters = kotlin.math.round(slot.distanceKm * 1000.0).toInt()
                val runIndex = legacyRunIndex(request.runDays, slot.slotNumber, slot.role)
                val sessionId = request.lockedSessionDays.keys.firstOrNull { lockedId ->
                    lockPattern.matchEntire(lockedId)?.let {
                        it.groupValues[1].toInt() == week.weekNumber && it.groupValues[2].toInt() == runIndex
                    } == true
                } ?: "$id-w${week.weekNumber}-run$runIndex"
                val estimatedMinutes = ceil(slot.distanceKm * request.planningPace / 60.0)
                    .toInt().coerceAtLeast(1)
                val sessionMinuteLimit = if (slot.role == Engine15M5RunRole.LONG) {
                    request.longRunMinutes ?: request.minutes
                } else {
                    request.minutes
                }
                if (request.timeBudgetEnabled && estimatedMinutes > sessionMinuteLimit)
                    return defer("DISTANCE_TIME_BUDGET_CONFLICT", "A distance-only run exceeds the entered session-time limit; no shorter substitute was generated.")
                val baseEasyHr = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, RunningStepIntent.AEROBIC_EASY)
                val baseHrSuffix = baseEasyHr?.let { " · ${it.first}-${it.second} bpm" }.orEmpty()
                PlannedSession(
                    id = sessionId,
                    date = placed.date,
                    discipline = "run",
                    title = if (slot.role == Engine15M5RunRole.LONG) "Long easy run" else "Easy run",
                    minutes = estimatedMinutes,
                    distanceKm = slot.distanceKm,
                    kind = if (slot.role == Engine15M5RunRole.LONG) "long" else "easy",
                    quality = false,
                    steps = listOf("${formatMetersAsKm(meters)} at easy, conversational effort$baseHrSuffix"),
                    reason = buildString {
                        append(if (week.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY) "Recovery week. " else "Loading week. ")
                        append(
                            if (hrProfile != null) "Distance and calendar were independently constructed; intensity is guided by your configured HR zones."
                            else "Distance and calendar were independently constructed; duration is estimated from the entered planning pace."
                        )
                    },
                    runningPrescription = RunningPrescription(
                        role = if (slot.role == Engine15M5RunRole.LONG) "LONG" else "EASY",
                        family = null,
                        segments = listOf(
                            RunningSegment(
                                "EASY",
                                meters,
                                if (hrProfile != null) "Easy conversational effort in target aerobic HR zone."
                                else "Easy, conversational effort; no pace or heart-rate target.",
                                stepIntent = if (hrProfile != null) RunningStepIntent.AEROBIC_EASY else null,
                                targetHrMinBpm = baseEasyHr?.first,
                                targetHrMaxBpm = baseEasyHr?.second
                            )
                        )
                    )
                )
            }
        }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        if (baseSessions.size != request.weeks * request.runDays || baseSessions.map { it.id }.distinct().size != baseSessions.size)
            return defer("INCOMPLETE_PLAN_ASSEMBLY", "Independent output did not contain exactly one dated workout for every weekly running slot.")

        val asOfDate = now.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val recoveryWeekNumbers = volume.weeks.filter {
            it.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY
        }.map { it.index + 1 }.toSet()
        val taperWeekNumbers = volume.weeks.filter {
            "RACE_TAPER_VOLUME_REDUCTION" in it.constraintCodes
        }.map { it.index + 1 }.toSet()
        val userDefinedSlotPattern = Regex(".*-(w[1-9][0-9]*-run(?:0|[1-9][0-9]*))$")
        val userDefinedQualityIds = baseSessions.asSequence()
            .filter { session ->
                userDefinedSlotPattern.matchEntire(session.id)?.groupValues?.get(1) in request.userDefinedQualityWorkouts.keys
            }
            .map { it.id }
            .toSet()
        val preSelection = if (request.qualityCount > 0 && request.userDefinedQualityWorkouts.isEmpty()) {
            Engine15QualityFamilySelection.select(
                request = request,
                ownerId = owner,
                asOf = asOfDate,
                evidence = qualityEvidence,
                athlete = athlete,
                scheduledSessions = baseSessions,
                recoveryWeeks = recoveryWeekNumbers,
                externalSessions = external.sessions,
                adaptiveMode = adaptiveQualityMode,
                taperWeeks = taperWeekNumbers
            )
        } else null
        val preQualityIds = userDefinedQualityIds + preSelection?.candidateSlots.orEmpty().map { it.sessionId }
        val minQualitySlotKm = if (adaptiveQualityMode && preQualityIds.isNotEmpty() && preSelection?.selectedFamily != null) {
            val estZones = Engine15PaceZoneEstimator.estimate(request)
            val archPool = (Engine15QualityProgressionPolicy.adaptiveArchetypeRotation(request, preSelection.selectedFamily) +
                Engine15QualityProgressionPolicy.phaseArchetypePool(request, preSelection.selectedFamily, Engine15MacroPhase.BASE) +
                Engine15QualityProgressionPolicy.phaseArchetypePool(request, preSelection.selectedFamily, Engine15MacroPhase.BUILD)).distinct()
            val easySpeed = 1000.0 / estZones.easy.midpointSecondsPerKm
            archPool.maxOf { arch ->
                val intro = Engine15QualityProgressionPolicy.adaptiveLadder(
                    arch, request.experience, estZones, preferDistanceBased = false
                ).first()
                val target = Engine15QualityProgressionPolicy.workTargetForFamily(arch.family ?: preSelection.selectedFamily)
                val workM = if (intro.workMeters > 0) {
                    intro.workMeters.toDouble()
                } else {
                    intro.workSeconds * (1000.0 / estZones.forTarget(target, arch).midpointSecondsPerKm)
                }
                val recM = intro.recoveryMeters.toDouble() +
                    (if (intro.recoveryMeters > 0) 0 else intro.recoverySeconds) * easySpeed
                (780.0 * easySpeed + workM + recM) / 1000.0 * 1.03
            }
        } else null
        val maxQualitySlotKm = if (adaptiveQualityMode && preQualityIds.isNotEmpty() && preSelection?.selectedFamily != null) {
            val estZones = Engine15PaceZoneEstimator.estimate(request)
            Engine15QualityProgressionPolicy.recommendedMaxQualitySlotKm(
                request, preSelection.selectedFamily, estZones, assertedExposureSecondsOverride
            )
        } else null
        val initialShaped = Engine15DailyLoadShaper.shape(
            sessions = baseSessions,
            volume = volume,
            request = request,
            qualitySessionIds = preQualityIds,
            pinnedSessionIds = userDefinedQualityIds,
            minQualitySlotKm = minQualitySlotKm,
            maxQualitySlotKm = maxQualitySlotKm
        )
        val sessions = if (request.qualityDistanceClaims.isNotEmpty() && preSelection != null) {
            val claimsByKey = request.qualityDistanceClaims.groupBy { it.weekNumber to it.slotNumber }
            val shapedById = initialShaped.associateBy { it.id }
            val legacyClaimPins = preSelection.candidateSlots.mapNotNull { slot ->
                val claim = claimsByKey[slot.weekNumber to slot.slotNumber]?.singleOrNull() ?: return@mapNotNull null
                val claimKm = claim.assertedMetersByTimedSegment.sum() / 1000.0
                val shapedKm = shapedById[slot.sessionId]?.distanceKm ?: return@mapNotNull null
                val matchesUnshaped = kotlin.math.abs(claimKm - slot.originalEasyDistanceKm) <= 1e-6
                val matchesShaped = kotlin.math.abs(claimKm - shapedKm) <= 1e-6
                slot.sessionId.takeIf { matchesUnshaped && !matchesShaped }
            }.toSet()
            if (legacyClaimPins.isNotEmpty()) {
                Engine15DailyLoadShaper.shape(
                    sessions = baseSessions,
                    volume = volume,
                    request = request,
                    qualitySessionIds = preQualityIds,
                    pinnedSessionIds = userDefinedQualityIds + legacyClaimPins,
                    minQualitySlotKm = minQualitySlotKm,
                    maxQualitySlotKm = maxQualitySlotKm
                )
            } else initialShaped
        } else initialShaped

        var experimentalTempo = emptyList<PlannedSession>()
        var experimentalQualityExposureSeconds: Int? = null
        var qualityBuildAdvisory: String? = null
        if (request.qualityCount > 0 && request.userDefinedQualityWorkouts.isEmpty()) {
            val selection = Engine15QualityFamilySelection.select(
                request = request,
                ownerId = owner,
                asOf = asOfDate,
                evidence = qualityEvidence,
                athlete = athlete,
                scheduledSessions = sessions,
                recoveryWeeks = recoveryWeekNumbers,
                externalSessions = external.sessions,
                adaptiveMode = adaptiveQualityMode,
                taperWeeks = taperWeekNumbers
            )
            val qualityDryRun = Engine15QualityWorkoutDryRunBuilder.build(
                request, selection, volume, sessions, assertedExposureSecondsOverride, allowExplicitUserFamily,
                adaptiveQualityMode = adaptiveQualityMode
            )
            val familyName = when (selection.selectedFamily) {
                IndependentQualityFamily.THRESHOLD -> "Threshold"
                IndependentQualityFamily.INTERVALS -> "Intervals"
                IndependentQualityFamily.HILLS -> "Hills"
                IndependentQualityFamily.FARTLEK -> "Fartlek"
                IndependentQualityFamily.PROGRESSION -> "Progression"
                IndependentQualityFamily.TEMPO, null -> "Tempo"
            }
            val paceCheckZones = if (adaptiveQualityMode && request.benchmarkDistanceMeters != null) {
                Engine15PaceZoneEstimator.estimate(request)
            } else null
            val paceWarnings = qualityDryRun?.alternatives.orEmpty().mapNotNull { alternative ->
                alternative.distanceMatch?.qualityPaceWarning(paceCheckZones)?.let { "Week ${alternative.weekNumber}: $it" }
            }
            val usualKm = athlete.typicalWeeklyKm
            if (qualityDryRun != null && usualKm != null && qualityDryRun.weeklyDistance.any { it.budgetKm > usualKm + 1e-6 }) {
                qualityBuildAdvisory = "Mileage progresses alongside $familyName workouts; monitor fatigue and prioritize recovery weeks."
            }
            if (paceWarnings.isEmpty() &&
                request.weeks in 1..16 && request.experience in 0..2 &&
                qualityDryRun?.completePlanDistanceAccounting == true &&
                (!request.timeBudgetEnabled || qualityDryRun.alternatives.all { alt ->
                    request.minutes * 60L >= (alt.totalSeconds ?: qualityDryRun.totalSeconds)
                })
            ) {
                val deterministicStepZones = Engine15PaceZoneEstimator.estimate(request)
                experimentalTempo = qualityDryRun.alternatives.mapNotNull { alternative ->
                    val match = requireNotNull(alternative.distanceMatch)
                    val original = sessions.single { it.id == alternative.sessionId }
                    if (match.segments.any { it.assertedMeters != it.assertedMeters.toInt().toDouble() })
                        return@mapNotNull null
                    val meters = match.segments.sumOf { it.assertedMeters.toInt() }
                    if (kotlin.math.abs(meters / 1000.0 - alternative.originalEasyDistanceKm) > 1e-8)
                        return@mapNotNull null
                    val altFamily = alternative.prescription?.family ?: selection.selectedFamily
                    val archetype = alternative.prescription?.step?.archetype
                    val altFamilyName = when (altFamily) {
                        IndependentQualityFamily.THRESHOLD -> "Threshold"
                        IndependentQualityFamily.INTERVALS -> "Intervals"
                        IndependentQualityFamily.HILLS -> "Hills"
                        IndependentQualityFamily.FARTLEK -> "Fartlek"
                        IndependentQualityFamily.PROGRESSION -> "Progression"
                        IndependentQualityFamily.TEMPO, null -> "Tempo"
                    }
                    val steps = match.segments.map { segment ->
                        val work = segment.purpose == WorkoutPurpose.WORK
                        val intent = when (segment.purpose) {
                            WorkoutPurpose.WARM_UP -> RunningStepIntent.WARMUP
                            WorkoutPurpose.COOL_DOWN -> RunningStepIntent.COOLDOWN
                            WorkoutPurpose.RECOVERY -> RunningStepIntent.RECOVERY
                            WorkoutPurpose.WORK -> when {
                                segment.target == WorkoutTarget.THRESHOLD -> RunningStepIntent.THRESHOLD
                                segment.target == WorkoutTarget.INTERVALS -> RunningStepIntent.VO2_INTERVAL
                                archetype == Engine15WorkoutArchetype.SHORT_HILL_SPRINTS ||
                                    archetype == Engine15WorkoutArchetype.LONG_AEROBIC_HILLS -> RunningStepIntent.HILL
                                archetype == Engine15WorkoutArchetype.AEROBIC_FARTLEK ||
                                    archetype == Engine15WorkoutArchetype.CONTINUOUS_TEMPO ||
                                    archetype == Engine15WorkoutArchetype.BROKEN_TEMPO -> RunningStepIntent.TEMPO
                                else -> Engine15PaceZoneEstimator.stepIntentFor(segment.purpose, altFamily)
                            }
                        }
                        val (paceMin, paceMax) = Engine15PaceZoneEstimator.paceBoundsForIntent(
                            deterministicStepZones, intent, request.goal, archetype
                        )
                        val effortText = when {
                            work -> when (archetype) {
                                Engine15WorkoutArchetype.SHORT_HILL_SPRINTS ->
                                    "Strong uphill drive (5-8% grade) at ~5K effort (RPE 8/10): focus on tall posture and power, judge by effort rather than flat pace."
                                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS ->
                                    "Steady uphill climb (4-6% grade) at Threshold/10K effort (RPE 7-8/10): hold rhythm by effort on the incline."
                                Engine15WorkoutArchetype.AEROBIC_FARTLEK ->
                                    "Controlled surge at Tempo–5K effort; settle smoothly into easy float recovery."
                                Engine15WorkoutArchetype.CONTINUOUS_TEMPO ->
                                    "Sustained Tempo effort; keep rhythm smooth and controlled."
                                Engine15WorkoutArchetype.BROKEN_TEMPO ->
                                    "Controlled Tempo / Half Marathon block; settle into easy float recovery between blocks."
                                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS ->
                                    "Comfortably hard Lactate Threshold effort; keep reps crisp with short jog recovery."
                                Engine15WorkoutArchetype.SHORT_TRACK_REPEATS ->
                                    "Fast 5K track effort; run tall and quick, take full easy recovery between reps."
                                Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS ->
                                    "Hard VO2max effort; keep every repetition smooth and repeatable."
                                Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID ->
                                    if (intent == RunningStepIntent.THRESHOLD) {
                                        "Threshold ladder segment; stay controlled and even before opening up."
                                    } else {
                                        "Fast VO2max ladder segment; maintain sharp form with controlled recovery."
                                    }
                                else -> when (altFamilyName) {
                                    "Threshold" -> "Controlled, comfortably hard effort. Keep repetitions even and ease off if effort rises."
                                    "Intervals" -> "Hard, repeatable effort. Keep each repetition controlled and use the full easy recovery between efforts."
                                    "Hills" -> "Strong uphill drive (5–8% grade) with tall posture; judge by effort and jog/walk down."
                                    "Fartlek" -> "Playful, controlled surge effort; settle smoothly back into easy running between surges."
                                    "Progression" -> "Start steady and gradually build effort across the finish block without straining."
                                    else -> "Steady, controlled effort. Ease off if you cannot speak a short phrase."
                                }
                            }
                            segment.purpose == WorkoutPurpose.RECOVERY -> when (archetype) {
                                Engine15WorkoutArchetype.SHORT_HILL_SPRINTS,
                                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS ->
                                    "Easy walk or slow jog back down the hill for full recovery."
                                Engine15WorkoutArchetype.BROKEN_TEMPO,
                                Engine15WorkoutArchetype.AEROBIC_FARTLEK ->
                                    "Easy aerobic float recovery between efforts."
                                else -> "Easy conversational walk/jog recovery."
                            }
                            else -> "Easy conversational effort"
                        }
                        val hrBounds = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, intent, request.goal)
                        val isShortHrRep = hrBounds != null && work && (
                            (segment.meters != null && segment.meters <= 400) ||
                                (segment.meters == null && segment.seconds <= 90.0)
                            )
                        val finalEffortText = if (isShortHrRep) {
                            "$effortText (Short rep: HR lags; guide by smooth RPE 8/10 while HR builds toward target zone.)"
                        } else effortText
                        if (segment.meters != null) {
                            RunningStepV2(
                                segment.purpose.name,
                                finalEffortText,
                                meters = segment.meters,
                                seconds = null,
                                distanceEquivalentMeters = null,
                                stepIntent = intent,
                                targetPaceMinSecPerKm = paceMin,
                                targetPaceMaxSecPerKm = paceMax,
                                targetHrMinBpm = hrBounds?.first,
                                targetHrMaxBpm = hrBounds?.second
                            )
                        } else {
                            RunningStepV2(
                                segment.purpose.name,
                                finalEffortText,
                                seconds = kotlin.math.round(segment.seconds).toInt().coerceAtLeast(1),
                                distanceEquivalentMeters = kotlin.math.round(segment.assertedMeters).toInt().coerceAtLeast(1),
                                stepIntent = intent,
                                targetPaceMinSecPerKm = paceMin,
                                targetPaceMaxSecPerKm = paceMax,
                                targetHrMinBpm = hrBounds?.first,
                                targetHrMaxBpm = hrBounds?.second
                            )
                        }
                    }
                    fun formatStepLine(st: RunningStepV2): String {
                        val extent = if (st.meters != null) {
                            "${st.meters} m (${formatMetersAsKm(st.meters)})"
                        } else {
                            val sec = requireNotNull(st.seconds)
                            val durText = when {
                                sec % 60 == 0 -> "${sec / 60} min"
                                sec > 60 -> "${sec / 60}m ${sec % 60}s"
                                else -> "${sec}s"
                            }
                            "$durText · ${formatMetersAsKm(st.distanceEquivalentMeters)}"
                        }
                        val hrTag = if (st.targetHrMinBpm != null && st.targetHrMaxBpm != null) {
                            " · ${st.targetHrMinBpm}-${st.targetHrMaxBpm} bpm"
                        } else ""
                        return "$extent ${st.purpose.lowercase().replace('_', ' ')}$hrTag · ${st.effort}"
                    }
                    val totalMin = (kotlin.math.round(match.segments.sumOf { it.seconds } / 60.0).toInt())
                        .coerceAtLeast(10)
                        .let { if (request.timeBudgetEnabled) minOf(it, request.minutes) else it }
                    original.copy(kind = "quality", quality = true,
                        title = archetype?.displayTitle ?: "$altFamilyName Workout",
                        minutes = totalMin,
                        runningPrescription = RunningPrescription("QUALITY", altFamilyName, emptyList(),
                            mixedSegments = steps, reconciledDistanceMeters = meters, archetypeId = archetype?.id),
                        steps = steps.map(::formatStepLine),
                        reason = if (alternative.progressionBasis != null &&
                            alternative.progressionBasis != Engine15QualityProgressionBasis.HOLD_NO_REPEATED_EXPOSURE) {
                            "${alternative.prescription!!.rationale} Effort cues guide intensity alongside estimated pace zones."
                        } else when (altFamilyName) {
                            "Threshold" -> "Structured Threshold intervals (3 × 4 min comfortably hard with 2 min easy recovery)."
                            "Intervals" -> "Structured VO₂max Intervals (4 × 2 min repeatable hard effort with 2 min easy recovery)."
                            "Hills" -> "Structured Hill Repeats (5 × 1 min strong uphill drive with 2 min easy jog-down recovery)."
                            "Fartlek" -> "Structured Fartlek surges (5 × 2 min controlled surges with 2 min easy float)."
                            "Progression" -> "Continuous Progression finish (12 min building gradually toward steady tempo effort)."
                            else -> "Structured Tempo run (10–12 min steady controlled effort with easy warm-up and cooldown)."
                        })
                }
                if (experimentalTempo.size != qualityDryRun.alternatives.size ||
                    experimentalTempo.any { quality ->
                        sessions.any { it.id != quality.id && conflict(quality, it) } ||
                            experimentalTempo.any { it.id != quality.id && conflict(quality, it) }
                    }) experimentalTempo = emptyList()
                if (experimentalTempo.isNotEmpty()) {
                    experimentalQualityExposureSeconds = assertedExposureSecondsOverride
                        ?: selection.assertedRepeatedWorkSeconds.takeIf {
                            selection.exposureConfidence == IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED
                        }
                }
            }
            if (experimentalTempo.isEmpty()) {
            val dryRunNote = when {
                selection.decision != Engine15QualitySelectionDecision.FAMILY_CANDIDATE ->
                    "No family dry-run was constructed because selection deferred or is unsupported."
                selection.selectionBasis != Engine15QualitySelectionBasis.GOAL_EXPERIENCE_MATRIX && !allowExplicitUserFamily ->
                    "No coach-generated dry-run was constructed because this family came from explicit user direction."
                qualityDryRun == null ->
                    "The $familyName structure could not be linked to a valid calendar slot."
                paceWarnings.isNotEmpty() -> paceWarnings.joinToString(" ")
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
            val shouldHaveQuality=if(experimentalTempo.isNotEmpty() || volume.weeks[week - 1].kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY) 0 else request.qualityCount
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
                    "${i+1}. $extent${equivalent} ${step.purpose.lowercase().replace('_',' ')}: ${step.effort}."
                } ?: workout.segments.mapIndexed { i,step -> "${i+1}. ${step.meters} m ${step.purpose.lowercase().replace('_',' ')}: ${step.effort}." },
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
        val preEnrichSessions = sessions.map { replacements[it.id] ?: it }
        val assembledSessions = if (enrichArchetypes) {
            enrichNonQualitySessions(preEnrichSessions, request, volume)
        } else {
            preEnrichSessions
        }

        val macroPhases = if (adaptiveQualityMode || enrichArchetypes) {
            Engine15MacrocyclePeriodization.assignPhases(request, volume)
        } else emptyMap()

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
                if (macroPhases.isNotEmpty()) {
                    add(Engine15MacrocyclePeriodization.phaseSummary(macroPhases))
                    addAll(Engine15MacrocyclePeriodization.feasibilityAdvisories(request, volume))
                }
                athlete.activityEvidence?.let { evidence ->
                    add("ACTIVITY_EVIDENCE_CONTEXT v${evidence.version}: ${evidence.recordedRunCount} recorded runs; " +
                        "longest recorded in the recent window: ${evidence.longestRecordedRunLast28DaysKm?.let { "%.1f km".format(java.util.Locale.US, it) } ?: "not available"}; " +
                        "source coverage is ${evidence.sourceCoverage}. This evidence is descriptive only and did not set weekly capacity, quality eligibility, workout dose, or intensity.")
                }
                if(request.userDirectedPlanning)
                    add("Weekly volume follows the user-requested workload, with scheduled recovery weeks. This is not validated athlete capacity; coach-recommended progression remains available for comparison.")
                if(experimentalTempo.isNotEmpty() && qualityBuildAdvisory != null) add(qualityBuildAdvisory)
                if(experimentalTempo.isNotEmpty()) add(
                    if (hrProfile != null) "Heart-rate-based training active: workouts target your configured BPM zones alongside perceived-effort cues."
                    else "Structured quality workouts use estimated pace zones and perceived-effort cues. Review and approve before starting."
                )
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
            experimentalQualityPolicy = QUALITY_POLICY.takeIf { experimentalTempo.isNotEmpty() && applyTempoExposureGate },
            experimentalQualityExposureSeconds = experimentalQualityExposureSeconds
        )
        check(sessions.all { it.date >= request.start && it.date < request.start.plusWeeks(request.weeks.toLong()) })
        check(sessions.groupBy { it.date }.values.all { it.size == 1 })
        check(sessions.groupBy { ChronoUnit.DAYS.between(request.start, it.date) / 7L }
            .all { (index, rows) -> kotlin.math.abs(rows.sumOf { it.distanceKm!! } - volume.weeks[index.toInt()].proposedKm!!) < 1e-6 })
        return Engine15IndependentPlanDecision.Candidate(snapshot, volume, periodization)
    }

    internal fun enrichNonQualitySessions(
        sessions: List<PlannedSession>,
        request: PlanRequest,
        volume: Engine15IndependentVolumePlanner.Result
    ): List<PlannedSession> {
        val zones = Engine15PaceZoneEstimator.estimate(request)
        val (easyMin, easyMax) = Engine15PaceZoneEstimator.paceBoundsForIntent(zones, RunningStepIntent.AEROBIC_EASY)
        val (recMin, recMax) = Engine15PaceZoneEstimator.paceBoundsForIntent(zones, RunningStepIntent.RECOVERY)
        val (steadyMin, steadyMax) = Engine15PaceZoneEstimator.paceBoundsForIntent(zones, RunningStepIntent.STEADY)
        val (strideMin, strideMax) = Engine15PaceZoneEstimator.paceBoundsForIntent(zones, RunningStepIntent.STRIDE)
        val hrProfile = if (request.runningTargetMode == "HR") request.resolvedHrZoneProfile() else null
        val easyHr = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, RunningStepIntent.AEROBIC_EASY)
        val recHr = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, RunningStepIntent.RECOVERY)
        val steadyHr = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, RunningStepIntent.STEADY)
        val strideHr = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, RunningStepIntent.STRIDE)
        val marathonHr = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, RunningStepIntent.RACE_PACE, goal = "Marathon")
        val hmHr = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, RunningStepIntent.RACE_PACE, goal = "Half marathon")

        val phasesByWeek = Engine15MacrocyclePeriodization.assignPhases(request, volume)
        val recoveryWeeks = volume.weeks.filter {
            it.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY
        }.map { it.index + 1 }.toSet()
        val taperWeeks = volume.weeks.filter {
            "RACE_TAPER_VOLUME_REDUCTION" in it.constraintCodes ||
                phasesByWeek[it.index + 1] == Engine15MacroPhase.TAPER
        }.map { it.index + 1 }.toSet()
        val loadingOrdinalByWeek = volume.weeks
            .filter {
                val wkNum = it.index + 1
                it.kind != Engine15IndependentVolumePlanner.WeekKind.RECOVERY && wkNum !in taperWeeks
            }
            .mapIndexed { idx, wk -> (wk.index + 1) to idx }
            .toMap()
        val hardOrLongDates = sessions.filter { it.quality || it.kind == "long" }.map { it.date }.toSet()

        // Pick up to one fresh easy day per eligible loading OR taper week for Easy Run + Strides.
        val strideConfigBySessionId = HashMap<String, Pair<Int, Int>>() // sessionId -> (reps, recSec)
        for (weekNum in 1..request.weeks) {
            val phase = phasesByWeek[weekNum] ?: Engine15MacroPhase.BUILD
            if (phase == Engine15MacroPhase.RECOVERY) continue
            val isTaperWeek = phase == Engine15MacroPhase.TAPER
            if (!isTaperWeek && (request.experience == 0 || request.returning) && weekNum <= 2) continue
            val loadingOrd = loadingOrdinalByWeek[weekNum]
            val includeStridesThisWeek = when {
                isTaperWeek -> !request.returning
                loadingOrd == null -> false
                request.goal in setOf("Half marathon", "Marathon") -> loadingOrd % 2 == 0
                else -> true
            }
            if (!includeStridesThisWeek) continue
            val weekStart = request.start.plusWeeks((weekNum - 1).toLong())
            val weekEnd = weekStart.plusWeeks(1)
            val minMetersForStrides = if (isTaperWeek) 3200 else 4000
            val easyCandidates = sessions.filter { s ->
                s.date >= weekStart && s.date < weekEnd &&
                    !s.quality && s.kind == "easy" &&
                    s.runningPrescription?.role == "EASY" &&
                    s.distanceKm != null && kotlin.math.round(s.distanceKm * 1000.0).toInt() >= minMetersForStrides
            }
            if (easyCandidates.isEmpty()) continue
            val chosen = easyCandidates.filter { s ->
                s.date.minusDays(1) !in hardOrLongDates && s.date.plusDays(1) !in hardOrLongDates
            }.maxByOrNull { it.distanceKm!! } ?: easyCandidates.maxByOrNull { it.distanceKm!! } ?: continue
            val totalM = kotlin.math.round(chosen.distanceKm!! * 1000.0).toInt()
            val reps = when {
                isTaperWeek -> 4
                request.experience == 0 || request.returning -> 4
                request.experience == 1 -> 6
                totalM >= 6000 -> 8
                else -> 6
            }
            val recSec = if (isTaperWeek || reps > 6) 75 else 60
            val strideM = kotlin.math.round(20.0 * 1000.0 / zones.fastIntervals.midpointSecondsPerKm).toInt().coerceIn(40, 140)
            val recM = kotlin.math.round(recSec * 1000.0 / zones.recovery.midpointSecondsPerKm).toInt().coerceIn(50, 260)
            val blockM = reps * (strideM + recM)
            val mainM = totalM - blockM
            val estMainMin = kotlin.math.ceil(mainM * zones.easy.midpointSecondsPerKm / 60_000.0).toInt()
            val estStrideMin = (reps * (20 + recSec) + 59) / 60
            if (mainM >= 2000 && (!request.timeBudgetEnabled || estMainMin + estStrideMin <= request.minutes)) {
                strideConfigBySessionId[chosen.id] = reps to recSec
            }
        }

        return sessions.map { s ->
            if (s.quality || s.distanceKm == null) return@map s
            val weekNum = (ChronoUnit.DAYS.between(request.start, s.date).toInt() / 7) + 1
            val weekPhase = phasesByWeek[weekNum] ?: Engine15MacroPhase.BUILD
            val totalMeters = kotlin.math.round(s.distanceKm * 1000.0).toInt().coerceAtLeast(100)
            val rxRole = s.runningPrescription?.role

            when {
                s.kind == "long" -> {
                    val loadingOrd = loadingOrdinalByWeek[weekNum]
                    val eligibleFastFinish = (weekPhase == Engine15MacroPhase.BUILD || weekPhase == Engine15MacroPhase.PEAK) &&
                        !request.returning &&
                        loadingOrd != null &&
                        request.goal != "Aerobic foundation" &&
                        totalMeters >= 10_000
                    if (eligibleFastFinish && request.goal == "Marathon" && weekNum >= 5 && loadingOrd!! % 2 == 1 && totalMeters >= 18_000) {
                        val (raceMin, raceMax) = Engine15PaceZoneEstimator.paceBoundsForIntent(
                            zones, RunningStepIntent.RACE_PACE, goal = "Marathon"
                        )
                        if (loadingOrd % 4 == 3 && totalMeters >= 20_000) {
                            val blockMeters = (((totalMeters * 0.16) / 100.0).roundToInt() * 100).coerceIn(3_000, 5_000)
                            val floatMeters = 1_000
                            val tailEasyMeters = 2_000
                            val leadEasyMeters = (totalMeters - 2 * blockMeters - floatMeters - tailEasyMeters).coerceAtLeast(4_000)
                            val remAfterLead = totalMeters - leadEasyMeters - floatMeters - tailEasyMeters
                            val adjBlock = ((remAfterLead / 2) / 100) * 100
                            val adjTail = totalMeters - leadEasyMeters - 2 * adjBlock - floatMeters
                            s.copy(
                                title = "Marathon-Pace Blocks Long Run",
                                steps = listOf(
                                    "${formatMetersAsKm(leadEasyMeters)} easy aerobic base · Z2",
                                    "2 x (${formatMetersAsKm(adjBlock)} Marathon pace / ${formatMetersAsKm(floatMeters)} easy float)",
                                    "${formatMetersAsKm(adjTail)} easy cool-down finish"
                                ),
                                reason = "Loading week. Alternating Marathon-pace blocks (2 x ${formatMetersAsKm(adjBlock)}) build race-specific rhythm and lactate clearance on tired legs.",
                                runningPrescription = RunningPrescription(
                                    role = "LONG",
                                    family = null,
                                    segments = listOf(
                                        RunningSegment("EASY", leadEasyMeters, "Easy aerobic long-run base pace.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second),
                                        RunningSegment("TRANSITION", adjBlock, "Marathon goal race pace block 1.", RunningStepIntent.RACE_PACE, raceMin, raceMax, marathonHr?.first, marathonHr?.second),
                                        RunningSegment("RECOVERY", floatMeters, "Easy aerobic float between Marathon-pace blocks.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second),
                                        RunningSegment("TRANSITION", adjBlock, "Marathon goal race pace block 2.", RunningStepIntent.RACE_PACE, raceMin, raceMax, marathonHr?.first, marathonHr?.second),
                                        RunningSegment("COOL_DOWN", adjTail, "Easy aerobic cool-down finish.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second)
                                    ),
                                    archetypeId = Engine15WorkoutArchetype.FAST_FINISH_PROGRESSIVE_LONG_RUN.id
                                )
                            )
                        } else {
                            val finishRatio = (0.26 + 0.03 * ((weekNum - 5) / 2)).coerceIn(0.25, 0.38)
                            val maxCapM = minOf(14_000, ((totalMeters * 0.40).toInt() / 100) * 100)
                            val minCapM = maxOf(6_000, ((totalMeters * 0.25 + 99).toInt() / 100) * 100).coerceAtMost(maxCapM)
                            val finishMeters = (((totalMeters * finishRatio) / 100.0).roundToInt() * 100).coerceIn(minCapM, maxCapM)
                            val baseMeters = totalMeters - finishMeters
                            s.copy(
                                title = "Marathon-Pace Long Run",
                                steps = listOf(
                                    "${formatMetersAsKm(baseMeters)} easy aerobic base · Z2",
                                    "${formatMetersAsKm(finishMeters)} Marathon pace finish · Stay relaxed and locked into goal rhythm"
                                ),
                                reason = "Loading week. Progressive Marathon-pace finish (${formatMetersAsKm(finishMeters)}) builds late-race muscular endurance.",
                                runningPrescription = RunningPrescription(
                                    role = "LONG",
                                    family = null,
                                    segments = listOf(
                                        RunningSegment("EASY", baseMeters, "Easy aerobic long-run base pace.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second),
                                        RunningSegment("TRANSITION", finishMeters, "Marathon goal race pace finish.", RunningStepIntent.RACE_PACE, raceMin, raceMax, marathonHr?.first, marathonHr?.second)
                                    ),
                                    archetypeId = Engine15WorkoutArchetype.FAST_FINISH_PROGRESSIVE_LONG_RUN.id
                                )
                            )
                        }
                    } else if (eligibleFastFinish && request.goal == "Half marathon" && weekNum >= 5 && loadingOrd!! % 2 == 1 && totalMeters >= 14_000) {
                        val (raceMin, raceMax) = Engine15PaceZoneEstimator.paceBoundsForIntent(
                            zones, RunningStepIntent.RACE_PACE, goal = "Half marathon"
                        )
                        if (loadingOrd % 4 == 3 && totalMeters >= 15_000) {
                            val blockMeters = (((totalMeters * 0.16) / 100.0).roundToInt() * 100).coerceIn(2_000, 3_500)
                            val floatMeters = 1_000
                            val tailEasyMeters = 1_500
                            val leadEasyMeters = (totalMeters - 2 * blockMeters - floatMeters - tailEasyMeters).coerceAtLeast(3_000)
                            val remAfterLead = totalMeters - leadEasyMeters - floatMeters - tailEasyMeters
                            val adjBlock = ((remAfterLead / 2) / 100) * 100
                            val adjTail = totalMeters - leadEasyMeters - 2 * adjBlock - floatMeters
                            s.copy(
                                title = "HM-Pace Blocks Long Run",
                                steps = listOf(
                                    "${formatMetersAsKm(leadEasyMeters)} easy aerobic base · Z2",
                                    "2 x (${formatMetersAsKm(adjBlock)} Half Marathon pace / ${formatMetersAsKm(floatMeters)} easy float)",
                                    "${formatMetersAsKm(adjTail)} easy cool-down finish"
                                ),
                                reason = "Loading week. Alternating Half Marathon pace blocks (2 x ${formatMetersAsKm(adjBlock)}) build race-specific rhythm under fatigue.",
                                runningPrescription = RunningPrescription(
                                    role = "LONG",
                                    family = null,
                                    segments = listOf(
                                        RunningSegment("EASY", leadEasyMeters, "Easy aerobic long-run base pace.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second),
                                        RunningSegment("TRANSITION", adjBlock, "Half marathon race pace block 1.", RunningStepIntent.RACE_PACE, raceMin, raceMax, hmHr?.first, hmHr?.second),
                                        RunningSegment("RECOVERY", floatMeters, "Easy aerobic float between HM-pace blocks.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second),
                                        RunningSegment("TRANSITION", adjBlock, "Half marathon race pace block 2.", RunningStepIntent.RACE_PACE, raceMin, raceMax, hmHr?.first, hmHr?.second),
                                        RunningSegment("COOL_DOWN", adjTail, "Easy aerobic cool-down finish.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second)
                                    ),
                                    archetypeId = Engine15WorkoutArchetype.FAST_FINISH_PROGRESSIVE_LONG_RUN.id
                                )
                            )
                        } else {
                            val finishRatio = (0.26 + 0.03 * ((weekNum - 5) / 2)).coerceIn(0.25, 0.38)
                            val maxCapM = minOf(10_000, ((totalMeters * 0.40).toInt() / 100) * 100)
                            val minCapM = maxOf(4_000, ((totalMeters * 0.25 + 99).toInt() / 100) * 100).coerceAtMost(maxCapM)
                            val finishMeters = (((totalMeters * finishRatio) / 100.0).roundToInt() * 100).coerceIn(minCapM, maxCapM)
                            val baseMeters = totalMeters - finishMeters
                            s.copy(
                                title = "HM-Pace Long Run",
                                steps = listOf(
                                    "${formatMetersAsKm(baseMeters)} easy aerobic base · Z2",
                                    "${formatMetersAsKm(finishMeters)} Half Marathon pace finish · Controlled strong closing kilometers"
                                ),
                                reason = "Loading week. Progressive Half Marathon pace finish (${formatMetersAsKm(finishMeters)}) practices race rhythm under fatigue.",
                                runningPrescription = RunningPrescription(
                                    role = "LONG",
                                    family = null,
                                    segments = listOf(
                                        RunningSegment("EASY", baseMeters, "Easy aerobic long-run base pace.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second),
                                        RunningSegment("TRANSITION", finishMeters, "Half marathon race pace finish.", RunningStepIntent.RACE_PACE, raceMin, raceMax, hmHr?.first, hmHr?.second)
                                    ),
                                    archetypeId = Engine15WorkoutArchetype.FAST_FINISH_PROGRESSIVE_LONG_RUN.id
                                )
                            )
                        }
                    } else if (eligibleFastFinish &&
                        request.goal in setOf("Improve 10K", "Half marathon", "Marathon", "General fitness") &&
                        loadingOrd!! % 2 == 0
                    ) {
                        val finishMeters = (((totalMeters * 0.25) / 100.0).roundToInt() * 100).coerceIn(2_000, totalMeters - 4_000)
                        val baseMeters = totalMeters - finishMeters
                        s.copy(
                            title = "Fast-Finish Long Run",
                            steps = listOf(
                                "${formatMetersAsKm(baseMeters)} easy aerobic base · Z2",
                                "${formatMetersAsKm(finishMeters)} steady progressive finish · Smoothly close at steady aerobic effort"
                            ),
                            reason = "Loading week. 75% easy aerobic base followed by a controlled 25% steady finish.",
                            runningPrescription = RunningPrescription(
                                role = "LONG",
                                family = null,
                                segments = listOf(
                                    RunningSegment("EASY", baseMeters, "Easy aerobic long-run base pace.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second),
                                    RunningSegment("TRANSITION", finishMeters, "Steady controlled finish effort.", RunningStepIntent.STEADY, steadyMin, steadyMax, steadyHr?.first, steadyHr?.second)
                                ),
                                archetypeId = Engine15WorkoutArchetype.FAST_FINISH_PROGRESSIVE_LONG_RUN.id
                            )
                        )
                    } else {
                        s.copy(
                            title = "Steady Aerobic Long Run",
                            steps = listOf("${formatMetersAsKm(totalMeters)} at steady easy aerobic long-run effort"),
                            runningPrescription = RunningPrescription(
                                role = "LONG",
                                family = null,
                                segments = listOf(
                                    RunningSegment("EASY", totalMeters, "Easy, conversational aerobic long-run effort.", RunningStepIntent.AEROBIC_EASY, easyMin, easyMax, easyHr?.first, easyHr?.second)
                                ),
                                archetypeId = Engine15WorkoutArchetype.STEADY_AEROBIC_LONG_RUN.id
                            )
                        )
                    }
                }
                s.id in strideConfigBySessionId -> {
                    val (reps, recSec) = strideConfigBySessionId.getValue(s.id)
                    val strideM = kotlin.math.round(20.0 * 1000.0 / zones.fastIntervals.midpointSecondsPerKm).toInt().coerceIn(40, 140)
                    val recM = kotlin.math.round(recSec * 1000.0 / zones.recovery.midpointSecondsPerKm).toInt().coerceIn(50, 260)
                    val mainM = totalMeters - reps * (strideM + recM)
                    val strideSteps = buildList {
                        add(
                            RunningStepV2(
                                purpose = "EASY",
                                effort = "Easy conversational aerobic base before strides.",
                                meters = mainM,
                                stepIntent = RunningStepIntent.AEROBIC_EASY,
                                targetPaceMinSecPerKm = easyMin,
                                targetPaceMaxSecPerKm = easyMax,
                                targetHrMinBpm = easyHr?.first,
                                targetHrMaxBpm = easyHr?.second
                            )
                        )
                        repeat(reps) {
                            add(
                                RunningStepV2(
                                    purpose = "TRANSITION",
                                    effort = if (strideHr != null) {
                                        "20s smooth relaxed stride: quick cadence and tall posture, never an all-out sprint. (Short rep: HR lags; guide by smooth relaxation.)"
                                    } else {
                                        "20s smooth relaxed stride: quick cadence and tall posture, never an all-out sprint."
                                    },
                                    seconds = 20,
                                    distanceEquivalentMeters = strideM,
                                    stepIntent = RunningStepIntent.STRIDE,
                                    targetPaceMinSecPerKm = strideMin,
                                    targetPaceMaxSecPerKm = strideMax,
                                    targetHrMinBpm = strideHr?.first,
                                    targetHrMaxBpm = strideHr?.second
                                )
                            )
                            add(
                                RunningStepV2(
                                    purpose = "RECOVERY",
                                    effort = "Full easy walk/jog recovery so every stride stays relaxed.",
                                    seconds = recSec,
                                    distanceEquivalentMeters = recM,
                                    stepIntent = RunningStepIntent.RECOVERY,
                                    targetPaceMinSecPerKm = recMin,
                                    targetPaceMaxSecPerKm = recMax,
                                    targetHrMinBpm = recHr?.first,
                                    targetHrMaxBpm = recHr?.second
                                )
                            )
                        }
                    }
                    s.copy(
                        title = "Easy Run + Strides",
                        steps = listOf(
                            "${formatMetersAsKm(mainM)} easy aerobic running",
                            "$reps × 20s relaxed strides (${formatMetersAsKm(reps * (strideM + recM))} w/ ${recSec}s walk/jog recovery)"
                        ),
                        reason = "${s.reason.trimEnd()} Includes $reps × 20s neuromuscular strides with full walk/jog recovery.",
                        runningPrescription = RunningPrescription(
                            role = "EASY",
                            family = null,
                            segments = emptyList(),
                            mixedSegments = strideSteps,
                            reconciledDistanceMeters = totalMeters,
                            archetypeId = Engine15WorkoutArchetype.EASY_RUN_WITH_STRIDES.id
                        )
                    )
                }
                rxRole == "RECOVERY" -> {
                    s.copy(
                        title = "Recovery run",
                        runningPrescription = RunningPrescription(
                            role = "RECOVERY",
                            family = null,
                            segments = listOf(
                                RunningSegment(
                                    "RECOVERY",
                                    totalMeters,
                                    "Recovery, very easy conversational pace (15–30 s/km slower than easy max).",
                                    RunningStepIntent.RECOVERY,
                                    recMin,
                                    recMax,
                                    recHr?.first,
                                    recHr?.second
                                )
                            ),
                            archetypeId = Engine15WorkoutArchetype.RECOVERY_RUN.id
                        )
                    )
                }
                else -> {
                    s.copy(
                        title = "Easy Aerobic Run",
                        runningPrescription = RunningPrescription(
                            role = "EASY",
                            family = null,
                            segments = listOf(
                                RunningSegment(
                                    "EASY",
                                    totalMeters,
                                    "Easy, conversational aerobic effort.",
                                    RunningStepIntent.AEROBIC_EASY,
                                    easyMin,
                                    easyMax,
                                    easyHr?.first,
                                    easyHr?.second
                                )
                            ),
                            archetypeId = Engine15WorkoutArchetype.EASY_AEROBIC_RUN.id
                        )
                    )
                }
            }
        }
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
        if ((runDays >= 3 || runDays == 1) && runIndex == 0) runDays else if (runDays >= 3) runIndex else runIndex + 1

    private fun legacyRunIndex(runDays: Int, slotNumber: Int, role: Engine15M5RunRole): Int =
        if ((runDays >= 3 || runDays == 1) && role == Engine15M5RunRole.LONG) 0
        else if (runDays >= 3) slotNumber else slotNumber - 1

    private fun soloExternalCalendar(
        request: PlanRequest, owner: String, plans: List<PlanSnapshot>, discipline: String,
        compose: (PlanRequest) -> List<PlannedSession>
    ): Pair<PlanRequest, List<PlannedSession>> {
        val window = request.start.minusDays(1)..request.start.plusWeeks(request.weeks.toLong())
        val existing = plans.filter { it.owner == owner && it.status == "approved" }
            .flatMap { plan -> plan.sessions.map { plan.policyId to it } }
            .filter { (_, session) -> session.status in setOf("planned", "in_progress") && session.date in window }
        val policy = when (discipline) {
            "str" -> if (FeatureFlags.USE_ENGINE16_STRENGTH_V1 && strengthTrack(request) == "str") ENGINE16_STRENGTH_POLICY
                else ENGINE15_INDEPENDENT_STRENGTH_POLICY
            "cal" -> if (FeatureFlags.USE_ENGINE17_CALISTHENICS_V1) ENGINE17_CALISTHENICS_POLICY
                else ENGINE15_INDEPENDENT_CALISTHENICS_POLICY
            else -> if (FeatureFlags.USE_ENGINE18_HYBRID_V1) ENGINE18_CONDITIONING_POLICY
                else ENGINE15_INDEPENDENT_CONDITIONING_POLICY
        }
        fun fits(rows: List<PlannedSession>, strict: Boolean) = rows.none { row ->
            existing.any { (savedPolicy, saved) ->
                if (strict) conflict(row, saved)
                else PlanScheduleValidation.acrossPlansConflict(row, saved, policy, savedPolicy)
            }
        }
        val initial = runCatching { compose(request) }.getOrElse { error ->
            if (error !is PlanConstraint || existing.isEmpty() || discipline in request.weeklySlots) throw error
            null
        }
        if (initial != null && fits(initial, strict = true)) return request to initial
        var fallback = initial?.takeIf { fits(it, strict = false) }?.let { request to it }
        if (discipline !in request.weeklySlots && request.lockedSessionDays.isEmpty()) {
            val days = (0..6).map { request.start.plusDays(it.toLong()).dayOfWeek.value }.filter { it in request.days }
            for (mask in 1 until (1 shl days.size)) {
                if (Integer.bitCount(mask) != request.disciplineSessionCount(discipline)) continue
                val pattern = days.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
                val scheduled = request.copy(weeklySlots = mapOf(discipline to pattern))
                val rows = try { compose(scheduled) } catch (_: PlanConstraint) { continue }
                if (fits(rows, strict = true)) return scheduled to rows
                if (fallback == null && fits(rows, strict = false)) fallback = scheduled to rows
            }
        }
        fallback?.let { return it }
        throw PlanConstraint("The selected workouts cannot fit around active plans. Choose more available days or revise the fixed days.")
    }

    fun conflict(a: PlannedSession, b: PlannedSession): Boolean =
        a.date == b.date || hardSpacingConflict(a, b)

    fun hardSpacingConflict(a: PlannedSession, b: PlannedSession): Boolean {
        val gap = kotlin.math.abs(ChronoUnit.DAYS.between(a.date, b.date))
        fun hard(session: PlannedSession) = session.quality ||
            if (Engine15DisciplineProgression.isStructured(session)) session.lower
            else session.discipline == "cal" || session.discipline in setOf("str", "cond") && session.lower
        fun longRun(session: PlannedSession) = session.discipline == "run" && session.kind == "long"
        return hard(a) && hard(b) && gap < 2 ||
            longRun(a) && hard(b) && gap < 2 || longRun(b) && hard(a) && gap < 2
    }

    fun validateSaved(
        plan: PlanSnapshot,
        qualityEvidence: List<IndependentQualitySegmentEvidence> = emptyList(),
        combinedRunDatesById: Map<String, LocalDate>? = null,
        combinedExternalHardDates: Set<LocalDate> = emptySet(),
        combinedMaxMinutesById: Map<String, Int> = emptyMap()
    ) {
        if (plan.policyId == ENGINE18_HYBRID_POLICY) {
            Engine18HybridProgramBuilder.validateSaved(plan)
            return
        }
        if (plan.policyId == ENGINE15_INDEPENDENT_COMBINED_POLICY) {
            Engine15StaticMultiDisciplinePlanner.validateSaved(plan)
            return
        }
        if (plan.policyId == ENGINE18_CONDITIONING_POLICY) {
            Engine18ConditioningWorkoutConstructor.validateSavedSolo(plan)
            return
        }
        if (plan.policyId == ENGINE15_INDEPENDENT_CONDITIONING_POLICY) {
            if (plan.engineVersion != version)
                throw PlanConstraint("The independent conditioning plan has an unsupported engine identity.")
            Engine15ConditioningWorkoutComposer.validateSaved(plan)
            return
        }
        if (plan.policyId == ENGINE17_CALISTHENICS_POLICY) {
            Engine17CalisthenicsProgramBuilder.validateSaved(plan)
            return
        }
        if (plan.policyId == ENGINE15_INDEPENDENT_CALISTHENICS_POLICY) {
            if (plan.engineVersion != version)
                throw PlanConstraint("The independent calisthenics plan has an unsupported engine identity.")
            Engine15CalisthenicsWorkoutComposer.validateSaved(plan)
            return
        }
        if (plan.policyId == ENGINE16_STRENGTH_POLICY) {
            Engine16StrengthProgramBuilder.validateSaved(plan)
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
        if (plan.experimentalQualityExposureSeconds != null && plan.experimentalQualityExposureSeconds !in 1..14400)
            throw PlanConstraint("This plan uses an invalid experimental quality exposure anchor.")
        val baselines = plan.sessions.map { it.baselineSession() }
        var expectedVolume: Engine15IndependentVolumePlanner.Result? = null
        val rawExpected = if (Engine15TimeOnlyRunWalkBuilder.isEligible(plan.request)) {
            Engine15TimeOnlyRunWalkBuilder.compose(plan.request, plan.owner, plan.id, plan.createdAt)
        } else {
            // Historical pilots retain their original eligibility semantics during structural validation.
            val savedUsesAdaptiveRotation = baselines.filter { it.quality }
                .mapNotNull { it.runningPrescription?.family }
                .distinct().size > 1
            val savedUsesArchetypeEnrichment = baselines.any { it.runningPrescription?.archetypeId != null }
            fun runPreview(adaptive: Boolean, enrich: Boolean = savedUsesArchetypeEnrichment) = preview(
                plan.request, plan.owner, plan.id, plan.createdAt,
                qualityEvidence = qualityEvidence,
                applyTempoExposureGate = plan.experimentalQualityPolicy == QUALITY_POLICY,
                assertedExposureSecondsOverride = plan.experimentalQualityExposureSeconds,
                allowExplicitUserFamily = true,
                adaptiveQualityMode = adaptive,
                enrichArchetypes = enrich
            )
            if (plan.request.qualityDistanceClaims.isEmpty() && (baselines.any { it.quality } || savedUsesArchetypeEnrichment)) {
                when (val decision = previewAutoReconciled(
                    plan.request, plan.owner, plan.id, plan.createdAt,
                    qualityEvidence = qualityEvidence,
                    assertedExposureSecondsOverride = plan.experimentalQualityExposureSeconds
                )) {
                    is Engine15IndependentPlanDecision.Deferred ->
                        throw PlanConstraint("${decision.reasonCode}: ${decision.explanation}")
                    is Engine15IndependentPlanDecision.Candidate -> {
                        expectedVolume = decision.volume
                        decision.plan
                    }
                }
            } else {
                val initialDecision = runPreview(savedUsesAdaptiveRotation || savedUsesArchetypeEnrichment)
                val needsAdaptiveRetry = plan.experimentalQualityExposureSeconds != null &&
                    !savedUsesAdaptiveRotation &&
                    plan.request.qualityDistanceClaims.isNotEmpty() &&
                    baselines.any { it.quality } &&
                    (initialDecision is Engine15IndependentPlanDecision.Deferred || (
                        initialDecision is Engine15IndependentPlanDecision.Candidate &&
                            initialDecision.plan.sessions.associateBy { it.id }.let { expectedById ->
                                baselines.any { session ->
                                    expectedById[session.id]?.runningPrescription != session.runningPrescription ||
                                        expectedById[session.id]?.distanceKm != session.distanceKm
                                }
                            }
                    ))
                val resolvedDecision = if (needsAdaptiveRetry) runPreview(true) else initialDecision
                when (resolvedDecision) {
                    is Engine15IndependentPlanDecision.Deferred ->
                        throw PlanConstraint("${resolvedDecision.reasonCode}: ${resolvedDecision.explanation}")
                    is Engine15IndependentPlanDecision.Candidate -> {
                        expectedVolume = resolvedDecision.volume
                        resolvedDecision.plan
                    }
                }
            }
        }
        val expected = if (combinedRunDatesById != null && expectedVolume != null) {
            val relocated = rawExpected.sessions.map { s ->
                val targetDate = combinedRunDatesById[s.id] ?: s.date
                if (targetDate == s.date) s else s.copy(date = targetDate)
            }
            val qualityIds = relocated.filter { it.quality }.map { it.id }.toSet()
            val reshaped = Engine15DailyLoadShaper.shape(
                sessions = relocated,
                volume = expectedVolume!!,
                request = plan.request,
                qualitySessionIds = qualityIds,
                pinnedSessionIds = qualityIds,
                externalHardDates = combinedExternalHardDates,
                maxMinutesBySessionId = combinedMaxMinutesById
            )
            val reEnriched = if (baselines.any { it.runningPrescription?.archetypeId != null }) {
                enrichNonQualitySessions(reshaped, plan.request, expectedVolume!!)
            } else {
                reshaped
            }
            rawExpected.copy(sessions = reEnriched)
        } else {
            rawExpected
        }
        if (plan.sessions.size != expected.sessions.size ||
            plan.sessions.map { it.id }.toSet() != expected.sessions.map { it.id }.toSet())
            throw PlanConstraint("The independent running plan is missing or duplicating generated sessions.")
        val expectedById = expected.sessions.associateBy { it.id }
        if (plan.sessions.any { session ->
                val source = expectedById[session.id] ?: return@any true
                val base = session.baselineSession()
                val rxMatches = if (base.runningPrescription == null || source.runningPrescription == null) {
                    base.runningPrescription == source.runningPrescription
                } else {
                    base.runningPrescription.matchesStructuralBaseline(source.runningPrescription)
                }
                base.discipline != "run" || base.quality != source.quality || base.kind != source.kind ||
                    base.distanceKm != source.distanceKm || !rxMatches ||
                    !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(session, plan.request) ||
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
        val actualTotals = baselines.groupBy { ChronoUnit.DAYS.between(plan.request.start, it.date) / 7L }
            .mapValues { (_, rows) -> rows.sumOf { it.distanceKm ?: 0.0 } }
        if (actualTotals.keys != expectedTotals.keys || expectedTotals.any { (week, km) ->
                kotlin.math.abs((actualTotals[week] ?: Double.NaN) - km) > 1e-6
            })
            throw PlanConstraint("The saved independent running plan's weekly distance no longer reconciles.")
    }
}
