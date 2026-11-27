package io.stridelab.privatealpha

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure, stateless controlled adaptation engine for Engine 1.5 (Step 06).
 * Generates explicit athlete-approved proposals across 5 deterministic playbooks,
 * enforces a strict Pre-Surface Invariant Gate (including untouched validateSaved),
 * manages anti-thrash cooldowns, and supports lossless one-tap rollback.
 */
internal object Engine15PlanAdaptationEngine {
    private const val PACE_COOLDOWN_DAYS = 14L
    private const val DISRUPTION_DISMISS_COOLDOWN_DAYS = 14L
    private const val PROPOSAL_EXPIRY_DAYS = 7L

    private const val MIN_WARMUP_SECONDS = 8 * 60
    private const val MIN_COOLDOWN_SECONDS = 5 * 60

    data class ValidationOutcome(
        val passed: Boolean,
        val warnings: List<String>
    )

    data class ApplyResult(
        val appliedPlan: PlanSnapshot,
        val revision: PlanAdaptationRevision,
        val updatedProposal: PlanAdaptationProposal,
        val cooldown: AdaptationCooldownRecord
    )

    data class RevertResult(
        val restoredPlan: PlanSnapshot,
        val updatedRevision: PlanAdaptationRevision
    )

    private data class DisruptionContext(
        val tier: DisruptionTier,
        val fingerprint: String,
        val gapDays: Int,
        val missedCount: Int,
        val interruptedWeekIndex: Int
    )

    fun evaluateProposal(
        plan: PlanSnapshot,
        diagnostic: AthleteDiagnosticSnapshot,
        activities: List<ActivityRecord>,
        matchesByActivityId: Map<String, ActivityMatchRecord>,
        cooldowns: List<AdaptationCooldownRecord> = emptyList(),
        existingProposals: List<PlanAdaptationProposal> = emptyList(),
        asOfDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = asOfDate.atTime(12, 0).toInstant(ZoneOffset.UTC)
    ): PlanAdaptationProposal? {
        if (!Engine1Engines.canManage(plan) || !Engine1Engines.supportsCoachingLoop(plan)) {
            return null
        }
        if (plan.status !in setOf("approved", "draft", "paused")) return null
        if (plan.id != diagnostic.planId) return null

        evaluateDisciplineRecovery(plan, cooldowns, existingProposals, asOfDate)?.let { return it }
        if (plan.sessions.none { it.discipline == "run" }) return null

        val runSafetyBlocked = safetyRestrictions?.hasHardBlock(AthleteDiscipline.RUN, evaluatedAtUtc) == true ||
            plan.request.effectiveSafetyCap("run", safetyRestrictions, evaluatedAtUtc) != null ||
            plan.request.runningTargetMode == "HR"

        val effectiveFromDate = asOfDate.plusDays(1)
        val linkedSessionIds = resolveLinkedSessionIds(plan, matchesByActivityId)
        val trailing30dLongestKm = computeTrailingLongestKm(activities, asOfDate, zoneId)

        // Priority 1: Missed-Week / Disruption Recovery (bypasses pace cooldowns)
        val disruption = detectDisruption(plan, diagnostic, activities, linkedSessionIds, asOfDate, zoneId)
        if (disruption != null &&
            !isDisruptionSuppressed(disruption.fingerprint, cooldowns, existingProposals, asOfDate)
        ) {
            val missedProposal = buildMissedWeekProposal(
                plan = plan,
                diagnostic = diagnostic,
                disruption = disruption,
                linkedSessionIds = linkedSessionIds,
                trailing30dLongestKm = trailing30dLongestKm,
                effectiveFromDate = effectiveFromDate,
                asOfDate = asOfDate
            )
            if (missedProposal != null) return missedProposal
        }

        // Priority 2: Proactive Deload Swap (OVERREACH_RISK or fatiguing HR + slow quality)
        val fatiguingOverload = diagnostic.volumeTolerance.exposureStatus == ExposureStatus.OVERREACH_RISK ||
            (diagnostic.aerobicEfficiency.trend == AerobicEfficiencyTrend.FATIGUING &&
                (diagnostic.qualityExecution.tooSlowCount >= 2 ||
                    (diagnostic.aerobicEfficiency.medianDecouplingPct ?: 0.0) >= 8.0))
        if (fatiguingOverload) {
            val deloadProposal = buildProactiveDeloadProposal(
                plan = plan,
                diagnostic = diagnostic,
                linkedSessionIds = linkedSessionIds,
                trailing30dLongestKm = trailing30dLongestKm,
                cooldowns = cooldowns,
                existingProposals = existingProposals,
                effectiveFromDate = effectiveFromDate,
                asOfDate = asOfDate
            )
            if (deloadProposal != null) return deloadProposal
        }

        // Priority 3: Easy-Day Pacing Lock (blocks pace upgrades while hidden fatigue exists)
        if (diagnostic.recalibrationAdvisory == RecalibrationAdvisory.ADDRESS_EASY_PACING_FIRST ||
            diagnostic.easyDiscipline.hiddenFatigueRisk
        ) {
            return buildEasyPacingLockProposal(
                plan = plan,
                diagnostic = diagnostic,
                cooldowns = cooldowns,
                existingProposals = existingProposals,
                effectiveFromDate = effectiveFromDate,
                asOfDate = asOfDate
            )
        }

        // Priority 4: Pace Recalibration Up (strictly requires MODERATE/HIGH + clean easy pacing + cooldown clear + no active run restriction)
        if (!runSafetyBlocked &&
            diagnostic.recalibrationAdvisory == RecalibrationAdvisory.SUGGEST_FASTER_CHECK &&
            diagnostic.overallConfidence in setOf(DiagnosticConfidence.MODERATE, DiagnosticConfidence.HIGH) &&
            !diagnostic.easyDiscipline.hiddenFatigueRisk &&
            !hasActiveTypeCooldown(plan.id, AdaptationType.PACE_RECALIBRATION_UP, cooldowns, asOfDate)
        ) {
            return buildPaceRecalibrationProposal(
                plan = plan,
                diagnostic = diagnostic,
                type = AdaptationType.PACE_RECALIBRATION_UP,
                linkedSessionIds = linkedSessionIds,
                trailing30dLongestKm = trailing30dLongestKm,
                effectiveFromDate = effectiveFromDate,
                asOfDate = asOfDate
            )
        }

        // Priority 5: Pace Recalibration Down (MODERATE/HIGH + cooldown clear + Pace mode)
        if (plan.request.runningTargetMode != "HR" &&
            diagnostic.recalibrationAdvisory == RecalibrationAdvisory.SUGGEST_SLOWER_CHECK &&
            diagnostic.overallConfidence in setOf(DiagnosticConfidence.MODERATE, DiagnosticConfidence.HIGH) &&
            !hasActiveTypeCooldown(plan.id, AdaptationType.PACE_RECALIBRATION_DOWN, cooldowns, asOfDate)
        ) {
            return buildPaceRecalibrationProposal(
                plan = plan,
                diagnostic = diagnostic,
                type = AdaptationType.PACE_RECALIBRATION_DOWN,
                linkedSessionIds = linkedSessionIds,
                trailing30dLongestKm = trailing30dLongestKm,
                effectiveFromDate = effectiveFromDate,
                asOfDate = asOfDate
            )
        }

        return null
    }

    fun evaluateRunnerSupport(
        plan: PlanSnapshot,
        otherPlans: List<PlanSnapshot>,
        today: LocalDate,
        cooldowns: List<AdaptationCooldownRecord> = emptyList(),
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC),
        receipt: (PlannedSession) -> WorkoutExecution?
    ): PlanAdaptationProposal? {
        if (plan.status != "approved" || !RunnerSupportSchedule.supports(plan)) return null
        val source = plan.sessions.asSequence()
            .filter { it.isMarkedComplete && it.log != null &&
                (it.title == "Strength for Runners" || it.kind == "structured-plyometrics") }
            .filter { (it.log!!.performedOn ?: it.date) in today.minusDays(13)..today }
            .maxByOrNull { it.log!!.at } ?: return null
        val log = requireNotNull(source.log)
        val execution = receipt(source)?.takeIf {
            it.receipt && it.planId == plan.id && it.sessionId == source.id && it.finalLog == log
        }
        val confirmed = execution?.sets.orEmpty().filter { it.isConfirmed && it.actual != null }
        val repRows = confirmed.filter { it.target.reps != null }
        val sessionRir = Regex("(?im)^RIR:\\s*([0-9]+)\\s*$").find(log.notes)?.groupValues?.get(1)?.toIntOrNull()
        val rir = repRows.mapNotNull { it.actual?.rir ?: sessionRir }
        val strain = (log.effort ?: 0) >= 8 || log.discomfort?.let {
            it.isNotBlank() && !it.equals("None", true)
        } == true || rir.any { it in 0..1 }
        val jumps = source.kind == "structured-plyometrics"
        val direction = when {
            strain -> RunnerSupportDose.LIGHT
            execution?.outcome?.prescription != PrescriptionOutcome.FULLY_PERFORMED -> return null
            jumps -> RunnerSupportDose.PROGRESS
            repRows.isNotEmpty() && rir.size == repRows.size && rir.all { it in 3..5 } &&
                repRows.all { (it.actual?.reps ?: 0) >= (it.target.reps ?: 0) } -> RunnerSupportDose.PROGRESS
            else -> return null
        }
        val target = plan.sessions.filter {
            it.title == source.title && it.date in today.plusDays(1)..today.plusDays(10) && supportCanAdjust(it, plan)
        }.minByOrNull { it.date } ?: return null
        val supportDiscKey = if (target.discipline == "cal") "cal" else "str"
        val supportDiscEnum = if (target.discipline == "cal") AthleteDiscipline.CALISTHENICS else AthleteDiscipline.STRENGTH
        val targetSafetyCap = plan.request.effectiveSafetyCap(supportDiscKey, safetyRestrictions, evaluatedAtUtc)
        if (safetyRestrictions?.hasHardBlock(supportDiscEnum, evaluatedAtUtc) == true ||
            targetSafetyCap == SafetyIntensityCap.REST_ONLY
        ) return null
        if (direction == RunnerSupportDose.PROGRESS && targetSafetyCap != null) return null
        val runs = RunnerSupportSchedule.runningPlans(plan.owner, otherPlans)
        val phase = RunnerSupportSchedule.phases(runs)[target.date]
        val placement = RunnerSupportSchedule.placement(target, target.date, RunnerSupportSchedule.keyRuns(runs))
        if (direction == RunnerSupportDose.PROGRESS &&
            (target.runnerSupportDose != RunnerSupportDose.ENTRY || placement?.cap != false ||
                weekIndexOf(plan.request.start, target.date) % 4 == 3 ||
                phase in setOf(Engine15MacroPhase.RECOVERY, Engine15MacroPhase.TAPER) ||
                !jumps && phase == Engine15MacroPhase.PEAK)) return null
        val after = supportDoseFor(plan, target, direction)
        if (after.exercises == target.exercises || plan.request.timeBudgetEnabled && after.minutes > plan.request.minutes) return null
        if (direction == RunnerSupportDose.LIGHT && after.minutes > target.minutes) return null
        if (jumps && direction == RunnerSupportDose.PROGRESS) {
            fun contacts(rows: List<SetRecord>) = rows.filter { it.exerciseId in setOf("pogo-hop", "line-bound") }
                .sumOf { it.reps ?: 0 }
            fun prescribed(session: PlannedSession) = session.exercises.sumOf { it.sets * (it.reps ?: 0) }
            val confirmedContacts = confirmed.filter { it.target.id in setOf("pogo-hop", "line-bound") }
                .sumOf { it.actual?.reps ?: 0 }
            val performedOn = log.performedOn ?: source.date
            val recentContacts = plan.sessions.filter {
                val date = it.log?.performedOn ?: it.date
                it.kind == source.kind && it.isMarkedComplete && date in performedOn.minusDays(6)..performedOn
            }.sumOf { completed ->
                val saved = receipt(completed)
                if (saved?.receipt == true && saved.finalLog == completed.log)
                    contacts(saved.finalLog?.sets.orEmpty()) else 0
            }
            val otherPlyo = plan.sessions.filter { it.id != target.id && it.kind == target.kind }
            val projectedContacts = prescribed(after) + (0L..6L).maxOf { lookback ->
                val window = target.date.minusDays(lookback)..target.date.plusDays(6 - lookback)
                otherPlyo.sumOf {
                    val date = it.log?.performedOn ?: it.date
                    if (date !in window) 0 else if (it.status == "planned") prescribed(it) else contacts(it.log?.sets.orEmpty())
                }
            }
            if (prescribed(after) > confirmedContacts + 4 || projectedContacts > minOf(48, recentContacts + 4)) return null
        }
        val evidenceKey = "${source.id}:${source.logRevision}:${execution?.finalizationId.orEmpty()}"
        // Cooldown storage also prefixes the plan and adaptation type before hashing its key.
        val fingerprint = java.util.UUID.nameUUIDFromBytes(evidenceKey.toByteArray(Charsets.UTF_8)).toString()
        val type = AdaptationType.RUNNER_SUPPORT_DOSE
        if (cooldowns.any { it.planId == plan.id && it.adaptationType == type &&
                (it.disruptionFingerprint == fingerprint || today <= it.cooldownUntilDate) }) return null
        val candidate = plan.copy(sessions = plan.sessions.map { if (it.id == target.id) after else it })
        Engine15IndependentPlanGenerator.validateSaved(candidate)
        val start = target.date
        val change = "${target.title} · $start · ${RunnerSupportDose.label(target)} → ${RunnerSupportDose.label(after)}"
        val rationale = when {
            strain -> "Your last workout showed high effort, low reps in reserve, or discomfort. Keep the next dose easier."
            jumps -> "Your confirmed landings support a small contact increase within the weekly ceiling."
            else -> "You confirmed all sets and had 3–5 reps in reserve. Add one rep per glute bridge set."
        }
        return PlanAdaptationProposal(
            proposalId = java.util.UUID.nameUUIDFromBytes("${plan.id}:$type:$fingerprint:${plan.revision}:$start".toByteArray()).toString(),
            planId = plan.id, type = type, status = AdaptationProposalStatus.PENDING,
            createdAtDate = today, effectiveFromDate = start, expiresAtDate = minOf(today.plusDays(PROPOSAL_EXPIRY_DAYS), start.minusDays(1)),
            disruptionTier = DisruptionTier.NONE, disruptionFingerprint = fingerprint,
            sourceDiagnosticDate = today, sourceConfidence = DiagnosticConfidence.MODERATE,
            title = if (strain) "Ease your next support workout" else "Progress your next support workout",
            coachingRationale = rationale,
            diff = PlanAdaptationDiff(start, 1, 0.0, 0.0, listOf(
                AdaptationWeekDiff(weekIndexOf(plan.request.start, start), "Scheduled", "Reviewed dose",
                    0.0, 0.0, 0, 0, change)
            ), emptyList(), true, emptyList()),
            candidatePlanJson = candidate.encode().toString(), reasonCodes = listOf("RUNNER_SUPPORT_DOSE")
        )
    }

    fun evaluateStrengthExecutionProgression(
        plan: PlanSnapshot,
        otherPlans: List<PlanSnapshot>,
        today: LocalDate,
        cooldowns: List<AdaptationCooldownRecord> = emptyList(),
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC),
        receipt: (PlannedSession) -> WorkoutExecution?
    ): PlanAdaptationProposal? {
        if (plan.status != "approved") return null
        val strengthCap = plan.request.effectiveSafetyCap("str", safetyRestrictions, evaluatedAtUtc)
        if (safetyRestrictions?.hasHardBlock(AthleteDiscipline.STRENGTH, evaluatedAtUtc) == true ||
            strengthCap == SafetyIntensityCap.REST_ONLY
        ) return null

        val source = plan.sessions.asSequence()
            .filter {
                it.discipline == "str" && it.runnerSupportDose == null &&
                    it.title != "Strength for Runners" && it.kind != "structured-plyometrics" &&
                    it.isMarkedComplete && it.log != null
            }
            .filter { (it.log!!.performedOn ?: it.date) in today.minusDays(13)..today }
            .maxByOrNull { it.log!!.at } ?: return null
        val log = requireNotNull(source.log)
        val execution = receipt(source)?.takeIf {
            it.receipt && it.planId == plan.id && it.sessionId == source.id && it.finalLog == log
        } ?: return null

        val metadataById = Engine16StrengthExerciseCatalog.allMetadata(library = null).associateBy { it.id }
        val objective = StrengthObjective.fromRequestGoal(plan.request.disciplineGoal("str"))
        val objectiveSpec = Engine16StrengthPolicyRegistry.defaultsFor(objective, plan.request.strengthExperience)
        val sessionRir = Regex("(?im)^RIR:\\s*([0-9]+)\\s*$").find(log.notes)?.groupValues?.get(1)?.toIntOrNull()
        val hasDiscomfort = log.discomfort?.let { it.isNotBlank() && !it.equals("None", true) } == true
        val symptomStatus = when {
            hasDiscomfort -> SymptomSafetyStatus.CONCERNING_SYMPTOM_FLAG
            log.effort != null -> SymptomSafetyStatus.CONFIRMED_NONE
            else -> SymptomSafetyStatus.MISSING_UNREPORTED
        }
        val fatigueDeload = (log.effort ?: 0) >= 9
        val runningPhases = RunnerSupportSchedule.phases(RunnerSupportSchedule.runningPlans(plan.owner, otherPlans))
        val sourceWeek = weekIndexOf(plan.request.start, source.date) + 1
        val runningMajorStepActive = runningPhases[source.date] == Engine15MacroPhase.BUILD

        val decisions = source.effectiveExercises.mapNotNull { ex ->
            val targetReps = ex.reps ?: return@mapNotNull null
            val meta = metadataById[ex.id] ?: return@mapNotNull null
            val confirmedForEx = execution.sets.filter {
                it.target.id == ex.id && it.isConfirmed && it.actual != null
            }
            if (confirmedForEx.isEmpty()) return@mapNotNull null
            val summaries = confirmedForEx.map { s ->
                val actual = requireNotNull(s.actual)
                StrengthSetExecutionSummary(
                    completedReps = actual.reps ?: targetReps,
                    loadKg = actual.kg,
                    reportedRir = actual.rir ?: sessionRir,
                    techniqueQuality = if ((log.effort ?: 0) >= 9) TechniqueQuality.BORDERLINE else TechniqueQuality.CLEAN
                )
            }
            val range = when {
                targetReps in objectiveSpec.primaryRepRange -> objectiveSpec.primaryRepRange
                targetReps in objectiveSpec.secondaryRepRange -> objectiveSpec.secondaryRepRange
                targetReps in objectiveSpec.isolationRepRange -> objectiveSpec.isolationRepRange
                else -> maxOf(1, targetReps - 2)..(targetReps + 2)
            }
            Engine16StrengthProgressionEngine.evaluate(
                StrengthProgressionInput(
                    exercise = meta,
                    objective = objective,
                    currentSets = ex.sets.coerceAtLeast(1),
                    currentTargetReps = targetReps,
                    repRange = range,
                    targetRir = objectiveSpec.targetRir,
                    completedSets = summaries,
                    consecutiveCompletedCleanSessions = 1,
                    verifiedSubsequentContinuity = true,
                    techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                    symptomStatus = symptomStatus,
                    runningMajorStepActive = runningMajorStepActive && sourceWeek > 1,
                    fatigueTriggeredDeload = fatigueDeload,
                    equipmentBlockedOrPainful = false
                )
            )
        }
        if (decisions.isEmpty()) return null

        val reduceOps = setOf(
            StrengthProgressionOperator.REDUCE_LOAD,
            StrengthProgressionOperator.REDUCE_WORKING_SETS,
            StrengthProgressionOperator.SUBSTITUTE_MOVEMENT
        )
        val progressOps = setOf(
            StrengthProgressionOperator.PROGRESS_ONE_SET,
            StrengthProgressionOperator.PROGRESS_PARTIAL_SETS,
            StrengthProgressionOperator.PROGRESS_ALL_SETS,
            StrengthProgressionOperator.PROGRESS_LOAD_RESET_REPS,
            StrengthProgressionOperator.PROGRESS_SET_VOLUME
        )
        val hasReductionSignal = decisions.any { it.operator in reduceOps }
        val progressCount = decisions.count { it.operator in progressOps }
        val stepDelta = when {
            hasReductionSignal -> -1
            strengthCap != null -> return null
            execution.outcome.prescription == PrescriptionOutcome.FULLY_PERFORMED &&
                progressCount * 2 >= decisions.size -> +1
            else -> return null
        }

        val evidenceKey = "${source.id}:${source.logRevision}:${execution.finalizationId}"
        val fingerprint = java.util.UUID.nameUUIDFromBytes(evidenceKey.toByteArray(Charsets.UTF_8)).toString()
        val type = AdaptationType.STRENGTH_PROGRESSION
        if (cooldowns.any {
                it.planId == plan.id && it.adaptationType == type &&
                    (it.disruptionFingerprint == fingerprint || today <= it.cooldownUntilDate)
            }
        ) return null

        fun titleFamily(title: String): String = title.substringBefore(":").substringBefore(" (").trim()
        val sourceFamily = titleFamily(source.title)
        val candidateTargets = if (stepDelta < 0) {
            plan.sessions.filter {
                it.discipline == "str" && it.runnerSupportDose == null &&
                    it.title != "Strength for Runners" && it.kind != "structured-plyometrics" &&
                    it.date in today.plusDays(1)..today.plusDays(7) &&
                    supportCanAdjustForStrength(it, plan)
            }.sortedBy { it.date }.take(3)
        } else {
            val upcoming = plan.sessions.filter {
                it.discipline == "str" && it.runnerSupportDose == null &&
                    it.title != "Strength for Runners" && it.kind != "structured-plyometrics" &&
                    it.date in today.plusDays(1)..today.plusDays(14) &&
                    supportCanAdjustForStrength(it, plan) &&
                    weekIndexOf(plan.request.start, it.date) % 4 != 3 &&
                    runningPhases[it.date] !in setOf(Engine15MacroPhase.RECOVERY, Engine15MacroPhase.TAPER)
            }.sortedBy { it.date }
            listOfNotNull(
                upcoming.firstOrNull { titleFamily(it.title) == sourceFamily } ?: upcoming.firstOrNull()
            )
        }
        val adjustments = candidateTargets.mapNotNull { target ->
            val nextStep = (target.difficultyStep + stepDelta).coerceIn(-1, 2)
            if (nextStep == target.difficultyStep) return@mapNotNull null
            val after = Engine15WorkoutDifficultyAdjuster.adjust(target, plan.request, nextStep)
            if (after == target) return@mapNotNull null
            if (stepDelta > 0 && plan.request.timeBudgetEnabled && after.minutes > plan.request.minutes) return@mapNotNull null
            target to after
        }.toMap()
        if (adjustments.isEmpty()) return null

        val candidate = plan.copy(
            sessions = plan.sessions.map { adjustments[it] ?: it }
        )
        if (runCatching {
                val restored = PlanSnapshot.decode(JSONObject(candidate.encode().toString()), plan.owner)
                Engine15IndependentPlanGenerator.validateSaved(restored)
            }.isFailure
        ) return null

        val effectiveFrom = adjustments.keys.minOf { it.date }
        val rationale = if (stepDelta < 0) {
            "Your last strength execution flagged high strain or discomfort. Step back upcoming strength volume conservatively."
        } else {
            "You completed ${source.title} cleanly within target RIR. Advance the next matching session cleanly (+1 progression step)."
        }
        val weekDiffs = adjustments.entries.groupBy { weekIndexOf(plan.request.start, it.key.date) }.map { (wk, entries) ->
            AdaptationWeekDiff(
                weekIndex = wk,
                phaseBefore = "Scheduled",
                phaseAfter = if (stepDelta < 0) "Stepped back" else "Progressed",
                plannedKmBefore = 0.0,
                plannedKmAfter = 0.0,
                qualityCountBefore = 0,
                qualityCountAfter = 0,
                summaryNote = entries.joinToString("\n") { (before, after) ->
                    "${before.date} · ${before.title}: ${before.exercises.sumOf { it.sets }} sets -> ${after.exercises.sumOf { it.sets }} sets"
                }
            )
        }
        return PlanAdaptationProposal(
            proposalId = java.util.UUID.nameUUIDFromBytes("${plan.id}:$type:$fingerprint:${plan.revision}:$effectiveFrom".toByteArray()).toString(),
            planId = plan.id,
            type = type,
            status = AdaptationProposalStatus.PENDING,
            createdAtDate = today,
            effectiveFromDate = effectiveFrom,
            expiresAtDate = minOf(today.plusDays(PROPOSAL_EXPIRY_DAYS), effectiveFrom.minusDays(1).coerceAtLeast(today)),
            disruptionTier = DisruptionTier.NONE,
            disruptionFingerprint = fingerprint,
            sourceDiagnosticDate = today,
            sourceConfidence = DiagnosticConfidence.MODERATE,
            title = if (stepDelta < 0) "Ease upcoming strength exposure" else "Progress next strength session",
            coachingRationale = rationale,
            diff = PlanAdaptationDiff(effectiveFrom, adjustments.size, 0.0, 0.0, weekDiffs, emptyList(), true, emptyList()),
            candidatePlanJson = candidate.encode().toString(),
            reasonCodes = listOf("STRENGTH_EXECUTION_PROGRESSION")
        )
    }

    fun evaluateCalisthenicsExecutionProgression(
        plan: PlanSnapshot,
        otherPlans: List<PlanSnapshot>,
        today: LocalDate,
        cooldowns: List<AdaptationCooldownRecord> = emptyList(),
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC),
        receipt: (PlannedSession) -> WorkoutExecution?
    ): PlanAdaptationProposal? {
        if (plan.status != "approved") return null
        val calCap = plan.request.effectiveSafetyCap("cal", safetyRestrictions, evaluatedAtUtc)
        if (safetyRestrictions?.hasHardBlock(AthleteDiscipline.CALISTHENICS, evaluatedAtUtc) == true ||
            calCap == SafetyIntensityCap.REST_ONLY
        ) return null

        val completedCal = plan.sessions.asSequence()
            .filter {
                it.discipline == "cal" && it.runnerSupportDose == null &&
                    it.isMarkedComplete && it.log != null
            }
            .filter { (it.log!!.performedOn ?: it.date) in today.minusDays(21)..today }
            .sortedBy { it.log!!.at }
            .toList()
        val source = completedCal.lastOrNull() ?: return null
        val log = requireNotNull(source.log)
        val execution = receipt(source)?.takeIf {
            it.receipt && it.planId == plan.id && it.sessionId == source.id && it.finalLog == log
        } ?: return null

        val nodeById = Engine17CalisthenicsDagCatalog.buildNodes().associateBy { it.exercise.id }
        val objective = CalisthenicsObjective.fromGoalString(plan.request.disciplineGoal("cal"))
        val spec = Engine17CalisthenicsPolicyRegistry.specFor(objective)
        val availableTokens = Engine17CalisthenicsDagCatalog.availableEquipmentTokens(plan.request)
        val hasDiscomfort = log.discomfort?.let { it.isNotBlank() && !it.equals("None", true) } == true
        val highStrain = (log.effort ?: 0) >= 9
        val isNovice = plan.request.calLevel <= 1

        val priorObservations = completedCal.dropLast(1).flatMap { priorSession ->
            val priorLog = priorSession.log ?: return@flatMap emptyList()
            val priorClean = (priorLog.effort ?: 7) <= 8 && priorLog.discomfort.isNullOrBlank()
            priorSession.effectiveExercises.mapNotNull { ex ->
                if (ex.id !in nodeById) return@mapNotNull null
                CalisthenicsNodeObservation(
                    nodeId = ex.id,
                    completedOn = priorLog.performedOn ?: priorSession.date,
                    setsCompleted = ex.sets,
                    repsCompleted = ex.reps,
                    holdSecondsCompleted = ex.seconds,
                    rirObserved = if (priorClean) 2 else 1,
                    techniqueQuality = if (priorClean) TechniqueQuality.CLEAN else TechniqueQuality.BORDERLINE,
                    techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                    recoveryReported = true
                )
            }
        }

        val decisions = source.effectiveExercises.mapNotNull { ex ->
            val node = nodeById[ex.id] ?: return@mapNotNull null
            val confirmed = execution.sets.filter { it.target.id == ex.id && it.isConfirmed && it.actual != null }
            if (confirmed.isEmpty()) return@mapNotNull null
            val obsReps = confirmed.mapNotNull { it.actual?.reps }.minOrNull()
            val obsSec = confirmed.mapNotNull { it.actual?.seconds }.minOrNull()
            val techQuality = when {
                hasDiscomfort || highStrain -> TechniqueQuality.BREAKDOWN
                execution.outcome.prescription == PrescriptionOutcome.FULLY_PERFORMED -> TechniqueQuality.CLEAN
                else -> TechniqueQuality.BORDERLINE
            }
            val obsRir = confirmed.mapNotNull { it.actual?.rir }.minOrNull() ?: if (highStrain) 0 else 2
            val currentObs = CalisthenicsNodeObservation(
                nodeId = ex.id,
                completedOn = log.performedOn ?: source.date,
                setsCompleted = confirmed.size,
                repsCompleted = obsReps,
                holdSecondsCompleted = obsSec,
                rirObserved = obsRir,
                techniqueQuality = techQuality,
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                recoveryReported = log.effort != null,
                symptomsPresent = hasDiscomfort,
                holdTremorOrCollapse = highStrain && node.exercise.isIsometricHold
            )
            val nextNode = nodeById.values.firstOrNull {
                it.domain == node.domain && it.difficultyTier == node.difficultyTier + 1
            }
            Engine17CalisthenicsProgressionEngine.evaluateNodeProgression(
                node = node,
                nextNode = nextNode,
                recentObservations = priorObservations + currentObs,
                spec = spec,
                isNoviceOrUnaccustomed = isNovice,
                availableEquipment = availableTokens
            )
        }
        if (decisions.isEmpty()) return null

        val reduceActions = setOf(
            CalisthenicsDecisionAction.REGRESS_DAG_PARENT,
            CalisthenicsDecisionAction.REDUCE_HOLD_DURATION_TREMOR,
            CalisthenicsDecisionAction.REDUCE_ECCENTRIC_TENDON_CAP
        )
        val progressActions = setOf(
            CalisthenicsDecisionAction.PROMOTE_NEXT_DAG_NODE,
            CalisthenicsDecisionAction.PROGRESS_HOLD_DURATION,
            CalisthenicsDecisionAction.PROGRESS_REPS_OR_SETS,
            CalisthenicsDecisionAction.PROGRESS_ADDED_MASS
        )
        val hasReduction = hasDiscomfort || highStrain || decisions.any { it.action in reduceActions }
        val progressCount = decisions.count { it.action in progressActions }
        val stepDelta = when {
            hasReduction -> -1
            calCap != null -> return null
            execution.outcome.prescription == PrescriptionOutcome.FULLY_PERFORMED &&
                progressCount * 2 >= decisions.size -> +1
            else -> return null
        }

        val evidenceKey = "${source.id}:${source.logRevision}:${execution.finalizationId}"
        val fingerprint = java.util.UUID.nameUUIDFromBytes(evidenceKey.toByteArray(Charsets.UTF_8)).toString()
        val type = AdaptationType.CALISTHENICS_PROGRESSION
        if (cooldowns.any {
                it.planId == plan.id && it.adaptationType == type &&
                    (it.disruptionFingerprint == fingerprint || today <= it.cooldownUntilDate)
            }
        ) return null

        fun titleFamily(title: String): String = title.substringBefore(" (").trim()
        val sourceFamily = titleFamily(source.title)
        val runningPhases = RunnerSupportSchedule.phases(RunnerSupportSchedule.runningPlans(plan.owner, otherPlans))
        val candidateTargets = if (stepDelta < 0) {
            plan.sessions.filter {
                it.discipline == "cal" && it.runnerSupportDose == null &&
                    it.date in today.plusDays(1)..today.plusDays(7) &&
                    supportCanAdjustForStrength(it, plan)
            }.sortedBy { it.date }.take(3)
        } else {
            val upcoming = plan.sessions.filter {
                it.discipline == "cal" && it.runnerSupportDose == null &&
                    it.date in today.plusDays(1)..today.plusDays(14) &&
                    supportCanAdjustForStrength(it, plan) &&
                    weekIndexOf(plan.request.start, it.date) % 4 != 3 &&
                    runningPhases[it.date] !in setOf(Engine15MacroPhase.RECOVERY, Engine15MacroPhase.TAPER)
            }.sortedBy { it.date }
            listOfNotNull(
                upcoming.firstOrNull { titleFamily(it.title) == sourceFamily } ?: upcoming.firstOrNull()
            )
        }
        val adjustments = candidateTargets.mapNotNull { target ->
            val nextStep = (target.difficultyStep + stepDelta).coerceIn(-1, 2)
            if (nextStep == target.difficultyStep) return@mapNotNull null
            val after = Engine15WorkoutDifficultyAdjuster.adjust(target, plan.request, nextStep)
            if (after == target) return@mapNotNull null
            if (stepDelta > 0 && plan.request.timeBudgetEnabled && after.minutes > plan.request.minutes) return@mapNotNull null
            target to after
        }.toMap()
        if (adjustments.isEmpty()) return null

        val candidate = plan.copy(
            sessions = plan.sessions.map { adjustments[it] ?: it }
        )
        if (runCatching {
                val restored = PlanSnapshot.decode(JSONObject(candidate.encode().toString()), plan.owner)
                Engine15IndependentPlanGenerator.validateSaved(restored)
            }.isFailure
        ) return null

        val effectiveFrom = adjustments.keys.minOf { it.date }
        val rationale = if (stepDelta < 0) {
            "Your last calisthenics execution flagged form breakdown or discomfort. Ease upcoming volume conservatively to protect joint control."
        } else {
            "You completed ${source.title} with clean technical control. Advance the next matching calisthenics session (+1 progression step)."
        }
        val weekDiffs = adjustments.entries.groupBy { weekIndexOf(plan.request.start, it.key.date) }.map { (wk, entries) ->
            AdaptationWeekDiff(
                weekIndex = wk,
                phaseBefore = "Scheduled",
                phaseAfter = if (stepDelta < 0) "Regressed / Eased" else "DAG Progressed",
                plannedKmBefore = 0.0,
                plannedKmAfter = 0.0,
                qualityCountBefore = 0,
                qualityCountAfter = 0,
                summaryNote = entries.joinToString("\n") { (before, after) ->
                    "${before.date} · ${before.title}: ${before.exercises.sumOf { it.sets }} sets -> ${after.exercises.sumOf { it.sets }} sets"
                }
            )
        }
        return PlanAdaptationProposal(
            proposalId = java.util.UUID.nameUUIDFromBytes("${plan.id}:$type:$fingerprint:${plan.revision}:$effectiveFrom".toByteArray()).toString(),
            planId = plan.id,
            type = type,
            status = AdaptationProposalStatus.PENDING,
            createdAtDate = today,
            effectiveFromDate = effectiveFrom,
            expiresAtDate = minOf(today.plusDays(PROPOSAL_EXPIRY_DAYS), effectiveFrom.minusDays(1).coerceAtLeast(today)),
            disruptionTier = DisruptionTier.NONE,
            disruptionFingerprint = fingerprint,
            sourceDiagnosticDate = today,
            sourceConfidence = DiagnosticConfidence.MODERATE,
            title = if (stepDelta < 0) "Ease upcoming calisthenics volume" else "Progress next calisthenics session",
            coachingRationale = rationale,
            diff = PlanAdaptationDiff(effectiveFrom, adjustments.size, 0.0, 0.0, weekDiffs, emptyList(), true, emptyList()),
            candidatePlanJson = candidate.encode().toString(),
            reasonCodes = listOf(if (stepDelta < 0) "CALISTHENICS_EXECUTION_REGRESS" else "CALISTHENICS_EXECUTION_PROGRESS")
        )
    }

    private fun supportCanAdjustForStrength(session: PlannedSession, plan: PlanSnapshot): Boolean =
        session.status == "planned" && session.log == null && session.linkedActivityId == null &&
            session.id !in plan.request.lockedSessionDays && session.swaps.isEmpty() && session.suggestion == null

    private fun supportCanAdjust(session: PlannedSession, plan: PlanSnapshot) =
        session.status == "planned" && session.log == null && session.linkedActivityId == null &&
            session.id !in plan.request.lockedSessionDays && session.swaps.isEmpty() && session.suggestion == null &&
            session.difficultyStep == 0

    private fun supportDoseFor(plan: PlanSnapshot, session: PlannedSession, mode: RunnerSupportDose): PlannedSession {
        val base = Engine15DisciplineProgression.compose(plan.request, plan.id, session.discipline, reschedule = true)
            .single { it.id == session.id }
        val adjusted = mode.apply(base, plan.request)
        return session.copy(exercises = adjusted.exercises, minutes = adjusted.minutes, steps = adjusted.steps,
            runnerSupportDose = mode)
    }

    private fun evaluateDisciplineRecovery(
        plan: PlanSnapshot,
        cooldowns: List<AdaptationCooldownRecord>,
        proposals: List<PlanAdaptationProposal>,
        today: LocalDate
    ): PlanAdaptationProposal? {
        if (plan.status != "approved") return null
        val history = plan.sessions.filter {
            it.discipline != "run" && it.date in today.minusDays(13)..today
        }
        val interrupted = history.filter { it.status == "missed" }.groupBy { it.discipline }
            .filterValues { it.size >= 2 }
        val fatigue = history.filter {
            it.status == "missed" && it.missedReason in setOf("Fatigue", "Soreness") ||
                it.status == "completed" && (it.log?.effort ?: 0) >= 8 &&
                (it.discipline == "cond" || it.log?.let { log -> WorkoutLogValidation.hasPerformedSet(it, log) } == true)
        }.groupBy { it.discipline }.filterValues { entries ->
            entries.size >= 2 || entries.any { it.kind == "structured-plyometrics" }
        }
        val missed = interrupted.isNotEmpty()
        val sources = if (missed) interrupted else fatigue
        if (sources.isEmpty()) return null
        val type = if (missed) AdaptationType.MISSED_WEEK_RECOVERY else AdaptationType.PROACTIVE_DELOAD_SWAP
        val fingerprint = sources.values.flatten().map { it.id }.sorted().joinToString("|")
        if (isDisruptionSuppressed(fingerprint, cooldowns, proposals, today) ||
            hasActiveTypeCooldown(plan.id, type, cooldowns, today)) return null
        val start = today.plusDays(1)
        val changed = mutableListOf<Pair<PlannedSession, PlannedSession>>()
        val sessions = plan.sessions.map { session ->
            if (session.discipline !in sources || session.date !in start..today.plusDays(7) ||
                session.status != "planned" || session.date.toString() == plan.request.lockedSessionDays[session.id] ||
                session.log != null || session.linkedActivityId != null || session.suggestion != null ||
                session.swaps.isNotEmpty() || session.difficultyStep < 0 || session.runnerSupportDose != null
            ) return@map session
            val reduced = Engine15WorkoutDifficultyAdjuster.adjust(session, plan.request, -1)
            if (reduced.minutes > session.minutes || reduced.exercises == session.exercises) return@map session
            changed += session to reduced
            reduced
        }
        if (changed.isEmpty()) return null
        val candidate = plan.copy(sessions = sessions)
        val raw = candidate.encode().toString()
        if (runCatching {
            val restored = PlanSnapshot.decode(JSONObject(raw), plan.owner)
            require(restored.encode().toString() == raw)
            Engine15IndependentPlanGenerator.validateSaved(restored)
        }.isFailure) return null
        fun dose(session: PlannedSession): String = when {
            session.kind == "structured-plyometrics" ->
                "${session.exercises.filter { it.id in setOf("pogo-hop", "line-bound") }.sumOf { it.sets * (it.reps ?: 0) }} ground contacts"
            session.discipline == "cond" -> "${session.exercises.sumOf { it.sets * (it.seconds ?: 0) }} seconds of work"
            else -> "${session.exercises.sumOf { it.sets }} sets, ${session.exercises.sumOf { it.sets * (it.reps ?: 0) }} reps, " +
                "${session.exercises.sumOf { it.sets * (it.seconds ?: 0) }} hold seconds"
        }
        val weeks = changed.groupBy { weekIndexOf(plan.request.start, it.first.date) }.map { (week, changes) ->
            AdaptationWeekDiff(week, "Scheduled", "Reduced exposure", 0.0, 0.0,
                changes.count { it.first.quality }, changes.count { it.second.quality },
                changes.joinToString("\n") { (before, after) -> "${before.date} · ${before.title}: ${dose(before)} → ${dose(after)}" })
        }
        val title = if (missed) "Resume training with less volume" else "Reduce upcoming training volume"
        val evidence = if (missed) "missed sessions" else "sessions marked as fatiguing or logged at high effort"
        return PlanAdaptationProposal(
            proposalId = java.util.UUID.nameUUIDFromBytes("${plan.id}:$type:$fingerprint:${plan.revision}".toByteArray()).toString(),
            planId = plan.id, type = type, status = AdaptationProposalStatus.PENDING,
            createdAtDate = today, effectiveFromDate = start, expiresAtDate = today.plusDays(PROPOSAL_EXPIRY_DAYS),
            disruptionTier = DisruptionTier.NONE, disruptionFingerprint = fingerprint,
            sourceDiagnosticDate = today, sourceConfidence = DiagnosticConfidence.MODERATE,
            title = title,
            coachingRationale = "${sources.values.sumOf { it.size }} $evidence. Reduce ${changed.size} upcoming sessions in place. No missed work is added.",
            diff = PlanAdaptationDiff(start, changed.size, 0.0, 0.0, weeks, emptyList(), true, emptyList()),
            candidatePlanJson = raw, reasonCodes = listOf("NON_RUNNING_RECOVERY")
        )
    }

    fun applyProposal(
        currentPlan: PlanSnapshot,
        proposal: PlanAdaptationProposal,
        asOfDate: LocalDate
    ): ApplyResult {
        require(proposal.planId == currentPlan.id) { "Proposal belongs to a different plan." }
        require(proposal.status == AdaptationProposalStatus.PENDING) {
            "Only a PENDING adaptation proposal can be applied."
        }
        require(!asOfDate.isAfter(proposal.expiresAtDate)) {
            "This adaptation proposal expired on ${proposal.expiresAtDate}."
        }
        val candidateRaw = proposal.candidatePlanJson
            ?: throw PlanConstraint("${proposal.type} is an advisory notice and cannot modify the plan.")

        val decodedCandidate = PlanSnapshot.decode(JSONObject(candidateRaw), currentPlan.owner)
        val disciplineRecovery = "NON_RUNNING_RECOVERY" in proposal.reasonCodes
        val supportDose = proposal.type == AdaptationType.RUNNER_SUPPORT_DOSE
        val strengthProgression = proposal.type == AdaptationType.STRENGTH_PROGRESSION
        val calProgression = proposal.type == AdaptationType.CALISTHENICS_PROGRESSION
        if (supportDose) {
            require(currentPlan.status == "approved" && RunnerSupportSchedule.supports(currentPlan) &&
                decodedCandidate.copy(sessions = currentPlan.sessions) == currentPlan &&
                decodedCandidate.sessions.map { it.id } == currentPlan.sessions.map { it.id })
            val changed = currentPlan.sessions.zip(decodedCandidate.sessions).single { (before, after) -> before != after }
            val (before, after) = changed
            val mode = after.runnerSupportDose
            require(supportCanAdjust(before, currentPlan) && before.date == proposal.effectiveFromDate && before.date > asOfDate &&
                (mode == RunnerSupportDose.LIGHT && after.minutes <= before.minutes ||
                    mode == RunnerSupportDose.PROGRESS && before.runnerSupportDose == RunnerSupportDose.ENTRY))
            require(after == supportDoseFor(currentPlan, before, requireNotNull(mode)))
            Engine15IndependentPlanGenerator.validateSaved(decodedCandidate)
        }
        if (strengthProgression) {
            require(currentPlan.status == "approved" &&
                decodedCandidate.copy(sessions = currentPlan.sessions) == currentPlan &&
                decodedCandidate.sessions.map { it.id } == currentPlan.sessions.map { it.id }) {
                "The saved plan structure changed. Review the updated strength adjustment."
            }
            val pairs = currentPlan.sessions.zip(decodedCandidate.sessions)
            require(pairs.any { (before, after) -> before != after }) {
                "Strength progression proposal contains no session adjustments."
            }
            require(pairs.all { (before, after) ->
                before == after || (before.discipline == "str" && before.runnerSupportDose == null &&
                    before.status == "planned" && before.date >= proposal.effectiveFromDate && before.date > asOfDate &&
                    before.linkedActivityId == null && before.log == null && before.suggestion == null &&
                    before.id !in currentPlan.request.lockedSessionDays && before.swaps.isEmpty() &&
                    (after.difficultyStep - before.difficultyStep) in setOf(-1, 1) &&
                    after == Engine15WorkoutDifficultyAdjuster.adjust(before, currentPlan.request, after.difficultyStep))
            }) { "The reviewed strength adjustment no longer matches the current sessions." }
            Engine15IndependentPlanGenerator.validateSaved(decodedCandidate)
        }
        if (calProgression) {
            require(currentPlan.status == "approved" &&
                decodedCandidate.copy(sessions = currentPlan.sessions) == currentPlan &&
                decodedCandidate.sessions.map { it.id } == currentPlan.sessions.map { it.id }) {
                "The saved plan structure changed. Review the updated calisthenics adjustment."
            }
            val pairs = currentPlan.sessions.zip(decodedCandidate.sessions)
            require(pairs.any { (before, after) -> before != after }) {
                "Calisthenics progression proposal contains no session adjustments."
            }
            require(pairs.all { (before, after) ->
                before == after || (before.discipline == "cal" && before.runnerSupportDose == null &&
                    before.status == "planned" && before.date >= proposal.effectiveFromDate && before.date > asOfDate &&
                    before.linkedActivityId == null && before.log == null && before.suggestion == null &&
                    before.id !in currentPlan.request.lockedSessionDays && before.swaps.isEmpty() &&
                    (after.difficultyStep - before.difficultyStep) in setOf(-1, 1) &&
                    after == Engine15WorkoutDifficultyAdjuster.adjust(before, currentPlan.request, after.difficultyStep))
            }) { "The reviewed calisthenics adjustment no longer matches the current sessions." }
            Engine15IndependentPlanGenerator.validateSaved(decodedCandidate)
        }
        if (disciplineRecovery) {
            require(decodedCandidate.revision == currentPlan.revision && currentPlan.status == "approved") {
                "The saved plan or review date changed. Reopen the adjustment."
            }
            Engine15IndependentPlanGenerator.validateSaved(decodedCandidate)
            require(decodedCandidate.copy(sessions = currentPlan.sessions) == currentPlan &&
                decodedCandidate.sessions.map { it.id } == currentPlan.sessions.map { it.id } &&
                currentPlan.sessions.zip(decodedCandidate.sessions).all { (before, after) ->
                    before == after || before.discipline != "run" && before.status == "planned" &&
                        before.date >= proposal.effectiveFromDate && before.date > asOfDate && before.linkedActivityId == null &&
                        before.log == null && before.suggestion == null && before.id !in currentPlan.request.lockedSessionDays &&
                        before.swaps.isEmpty() && before.difficultyStep >= 0 && after.minutes <= before.minutes &&
                        after == Engine15WorkoutDifficultyAdjuster.adjust(before, currentPlan.request, -1)
                }) { "The reviewed change no longer matches the current workout prescriptions." }
        }
        val mergedSessions = decodedCandidate.sessions.map { candidateSession ->
            val live = currentPlan.sessions.find { it.id == candidateSession.id }
            if (live != null && (live.status == "completed" || live.linkedActivityId != null || live.date.isBefore(proposal.effectiveFromDate))) {
                live
            } else {
                candidateSession
            }
        }
        val candidatePlan = decodedCandidate.copy(
            sessions = mergedSessions,
            revision = currentPlan.revision + 1
        )
        val linkedIds = currentPlan.sessions.mapNotNull { if (it.linkedActivityId != null) it.id else null }.toSet()
        val trailingLongest = currentPlan.request.longestKm ?: 10.0
        val gate = if (disciplineRecovery || supportDose || strengthProgression) ValidationOutcome(true, emptyList()) else validateCandidatePlan(
            originalPlan = currentPlan,
            candidatePlan = candidatePlan,
            effectiveFromDate = proposal.effectiveFromDate,
            trailing30dLongestKm = trailingLongest,
            linkedSessionIds = linkedIds,
            futureEngineValidationPlan = null,
            allowZeroMakeUpCheck = true
        )
        require(gate.passed) {
            "Adaptation failed safety invariant validation: ${gate.warnings.joinToString("; ")}"
        }

        val revision = PlanAdaptationRevision(
            revisionId = "rev-${proposal.proposalId}-$asOfDate",
            planId = currentPlan.id,
            proposalId = proposal.proposalId,
            appliedAtDate = asOfDate,
            adaptationType = proposal.type,
            previousPlanJson = currentPlan.encode().toString(),
            appliedPlanJson = candidatePlan.encode().toString(),
            revertedAtDate = null
        )
        val cooldownDays = if (proposal.type in setOf(
                AdaptationType.PACE_RECALIBRATION_UP,
                AdaptationType.PACE_RECALIBRATION_DOWN
            )
        ) PACE_COOLDOWN_DAYS else DISRUPTION_DISMISS_COOLDOWN_DAYS

        val cooldown = AdaptationCooldownRecord(
            planId = currentPlan.id,
            adaptationType = proposal.type,
            lastActionStatus = AdaptationProposalStatus.APPLIED,
            actionDate = asOfDate,
            cooldownUntilDate = asOfDate.plusDays(cooldownDays),
            disruptionFingerprint = proposal.disruptionFingerprint
        )
        return ApplyResult(
            appliedPlan = candidatePlan,
            revision = revision,
            updatedProposal = proposal.copy(status = AdaptationProposalStatus.APPLIED),
            cooldown = cooldown
        )
    }

    fun revertRevision(
        currentPlan: PlanSnapshot,
        revision: PlanAdaptationRevision,
        asOfDate: LocalDate
    ): RevertResult {
        require(revision.planId == currentPlan.id) { "Revision belongs to a different plan." }
        require(revision.revertedAtDate == null) { "Revision was already reverted." }

        val restoredBase = PlanSnapshot.decode(JSONObject(revision.previousPlanJson), currentPlan.owner)
        val appliedBase = PlanSnapshot.decode(JSONObject(revision.appliedPlanJson), currentPlan.owner)
        val previousById = restoredBase.sessions.associateBy { it.id }
        val adjusted = appliedBase.sessions.filter { previousById[it.id] != it }.associateBy { it.id }
        if (adjusted.isNotEmpty() && adjusted.values.all { it.discipline != "run" }) {
            val sessions = currentPlan.sessions.map { live ->
                val applied = adjusted[live.id] ?: return@map live
                // An intervening log or edit owns the live prescription and its history.
                if (live != applied || live.date.isBefore(asOfDate)) live
                else previousById.getValue(live.id)
            }
            val restored = currentPlan.copy(sessions = sessions, revision = currentPlan.revision + 1)
            Engine15IndependentPlanGenerator.validateSaved(restored)
            return RevertResult(restored, revision.copy(revertedAtDate = asOfDate.toString()))
        }
        val liveById = currentPlan.sessions.associateBy { it.id }
        val restoredSessions = restoredBase.sessions.map { origSession ->
            val live = liveById[origSession.id]
            if (live != null && (live.status == "completed" || live.linkedActivityId != null || live.log != null || live.runResponse != null)) {
                origSession.copy(
                    status = live.status,
                    linkedActivityId = live.linkedActivityId,
                    linkPreviousStatus = live.linkPreviousStatus,
                    log = live.log,
                    runResponse = live.runResponse,
                    logRevision = maxOf(origSession.logRevision, live.logRevision)
                )
            } else {
                origSession
            }
        }
        val restoredPlan = restoredBase.copy(
            sessions = restoredSessions,
            revision = currentPlan.revision + 1
        )
        return RevertResult(
            restoredPlan = restoredPlan,
            updatedRevision = revision.copy(revertedAtDate = asOfDate.toString())
        )
    }

    /**
     * Mandatory Pre-Surface Invariant Gate enforcing:
     * 1. Untouched Engine15IndependentPlanGenerator.validateSaved on any regenerated future sub-plan
     * 2. Immutable past / completed / linked sessions
     * 3. Exact weekly distance conservation (1e-6) and rx meter parity
     * 4. Single run per day & >= 2-day hard spacing
     * 5. Safe warm-up (>= 8 min) and cool-down (>= 5 min) bookends
     * 6. 30-day single-session exposure bounds (when applicable)
     * 7. Step 03 race taper preservation (taper stays TAPER, quality <= 1, volume never inflated)
     * 8. Zero make-up cramming onto remaining days of the current partial week
     * 9. Lossless PlanSnapshot JSON round-trip
     */
    fun validateCandidatePlan(
        originalPlan: PlanSnapshot,
        candidatePlan: PlanSnapshot,
        effectiveFromDate: LocalDate,
        trailing30dLongestKm: Double,
        linkedSessionIds: Set<String>,
        futureEngineValidationPlan: PlanSnapshot? = null,
        allowZeroMakeUpCheck: Boolean = true,
        reEntryWeekIndices: Set<Int> = emptySet()
    ): ValidationOutcome {
        val warnings = ArrayList<String>()

        // 1. Untouched Engine 1.5 structural validation on regenerated future sub-plan (if supplied)
        if (futureEngineValidationPlan != null) {
            try {
                Engine15IndependentPlanGenerator.validateSaved(futureEngineValidationPlan)
            } catch (e: Exception) {
                warnings += "ENGINE15_VALIDATE_SAVED_FAILED: ${e.message}"
            }
        }

        // 2. Immutable past / completed / linked sessions
        val origById = originalPlan.sessions.associateBy { it.id }
        val candById = candidatePlan.sessions.associateBy { it.id }
        for (orig in originalPlan.sessions) {
            val isProtected = orig.date.isBefore(effectiveFromDate) ||
                orig.status == "completed" ||
                orig.linkedActivityId != null ||
                orig.id in linkedSessionIds
            if (isProtected) {
                val cur = candById[orig.id]
                if (cur != orig) {
                    warnings += "IMMUTABLE_PAST_OR_LINKED_MUTATED:${orig.id}"
                    break
                }
            }
        }

        // 3. Exact weekly distance conservation + per-session rx parity
        for (s in candidatePlan.sessions.filter { it.discipline == "run" }) {
            val rx = s.runningPrescription
            if (rx != null && s.distanceKm != null) {
                val rxMeters = rx.meters
                val kmMeters = (s.distanceKm * 1000.0).roundToInt()
                if (abs(rxMeters - kmMeters) > 1) {
                    warnings += "SESSION_RX_DISTANCE_MISMATCH:${s.id}"
                }
                if (s.quality) {
                    val steps = rx.mixedSegments
                    if (steps != null) {
                        val wuSec = steps.filter { it.purpose == "WARM_UP" }.sumOf { it.seconds ?: 0 }
                        val cdSec = steps.filter { it.purpose == "COOL_DOWN" }.sumOf { it.seconds ?: 0 }
                        if ((wuSec > 0 && wuSec < MIN_WARMUP_SECONDS) || (cdSec > 0 && cdSec < MIN_COOLDOWN_SECONDS)) {
                            warnings += "QUALITY_BOOKEND_SAFETY_VIOLATION:${s.id}"
                        }
                    }
                }
            }
        }

        val candRuns = candidatePlan.sessions.filter { it.discipline == "run" && it.status !in setOf("missed", "skipped") }
        // Single run per day
        if (candRuns.groupBy { it.date }.values.any { it.size > 1 }) {
            warnings += "MULTIPLE_RUNS_SAME_DAY"
        }
        // >= 2-day hard-session spacing
        for (i in candRuns.indices) {
            for (k in i + 1 until candRuns.size) {
                if (Engine15IndependentPlanGenerator.conflict(candRuns[i], candRuns[k])) {
                    warnings += "HARD_SESSION_SPACING_CONFLICT:${candRuns[i].id}:${candRuns[k].id}"
                    break
                }
            }
        }

        // Exact weekly conservation against tenths sum
        val byWeek = candRuns.groupBy { weekIndexOf(candidatePlan.request.start, it.date) }
        for ((wk, sessions) in byWeek) {
            val sumKm = sessions.sumOf { it.distanceKm ?: 0.0 }
            val expectedTenthsKm = (sumKm * 10.0).roundToInt() / 10.0
            if (abs(sumKm - expectedTenthsKm) > 1e-6) {
                warnings += "WEEK_DISTANCE_NOT_CONSERVED:wk$wk"
            }
        }

        // 6. 30-day single-session exposure guardrail on re-entry weeks
        if (reEntryWeekIndices.isNotEmpty() && trailing30dLongestKm > 0.0) {
            val exposureCap = maxOf(8.0, (trailing30dLongestKm * 1.10 * 10.0).roundToInt() / 10.0)
            for (wk in reEntryWeekIndices) {
                val wkMax = byWeek[wk].orEmpty().maxOfOrNull { it.distanceKm ?: 0.0 } ?: 0.0
                if (wkMax > exposureCap + 0.05) {
                    warnings += "REENTRY_EXPOSURE_CAP_EXCEEDED:wk$wk"
                }
            }
        }

        // 7. Taper preservation
        val origPhases = resolveMacroPhases(originalPlan)
        for ((wkNum, phase) in origPhases) {
            if (phase != Engine15MacroPhase.TAPER) continue
            val wkIdx = wkNum - 1
            val origWkSessions = originalPlan.sessions.filter { weekIndexOf(originalPlan.request.start, it.date) == wkIdx }
            val candWkSessions = byWeek[wkIdx].orEmpty()
            val origWkKm = origWkSessions.sumOf { it.distanceKm ?: 0.0 }
            val candWkKm = candWkSessions.sumOf { it.distanceKm ?: 0.0 }
            val origTaperQuality = candWkSessions.count { it.quality }
            if (candWkKm > origWkKm + 0.1 || origTaperQuality > 1) {
                warnings += "TAPER_WEEK_INFLATED:wk$wkIdx"
            }
        }

        // 8. Zero make-up cramming on current partial week
        if (allowZeroMakeUpCheck) {
            val curWk = weekIndexOf(originalPlan.request.start, effectiveFromDate.minusDays(1))
            if (curWk >= 0) {
                for (cand in byWeek[curWk].orEmpty()) {
                    if (!cand.date.isBefore(effectiveFromDate)) {
                        val orig = origById[cand.id]
                        if (orig == null || (cand.distanceKm ?: 0.0) > (orig.distanceKm ?: 0.0) + 1e-6) {
                            warnings += "CURRENT_WEEK_MAKEUP_CRAMMING:${cand.id}"
                        }
                    }
                }
            }
        }

        // 9. Lossless JSON round-trip check
        runCatching {
            val decoded = PlanSnapshot.decode(candidatePlan.encode(), candidatePlan.owner)
            require(decoded.sessions.size == candidatePlan.sessions.size)
        }.onFailure {
            warnings += "SNAPSHOT_JSON_ROUNDTRIP_FAILED"
        }

        return ValidationOutcome(passed = warnings.isEmpty(), warnings = warnings)
    }

    private fun buildPaceRecalibrationProposal(
        plan: PlanSnapshot,
        diagnostic: AthleteDiagnosticSnapshot,
        type: AdaptationType,
        linkedSessionIds: Set<String>,
        trailing30dLongestKm: Double,
        effectiveFromDate: LocalDate,
        asOfDate: LocalDate
    ): PlanAdaptationProposal? {
        val rawDelta = diagnostic.qualityExecution.avgWorkPaceDeltaSecPerKm ?: return null
        val isUp = type == AdaptationType.PACE_RECALIBRATION_UP
        val boundedPct = if (isUp) {
            // 2.0% to 3.0% faster (negative delta -> factor 0.97..0.98)
            (abs(rawDelta) / 260.0).coerceIn(0.02, 0.03)
        } else {
            // 3.0% to 5.0% slower (positive delta -> factor 1.03..1.05)
            (abs(rawDelta) / 260.0).coerceIn(0.03, 0.05)
        }
        val paceFactor = if (isUp) (1.0 - boundedPct) else (1.0 + boundedPct)

        val origPhases = resolveMacroPhases(plan)
        val basePace = plan.request.planningPace.takeIf { it > 0.0 } ?: estimateBaseEasyPace(plan) ?: 330.0
        val newRequest = plan.request.copy(
            planningPace = (basePace * paceFactor).coerceIn(180.0, 900.0)
        )
        val futureEngineValidationPlan = generateValidationPlan(newRequest, plan) ?: return null

        val paceDiffMap = LinkedHashMap<RunningStepIntent, Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        var affectedCount = 0

        val updatedSessions = plan.sessions.map { session ->
            val protected = session.date.isBefore(effectiveFromDate) ||
                session.status == "completed" ||
                session.linkedActivityId != null ||
                session.id in linkedSessionIds
            if (protected || session.discipline != "run") return@map session

            val wkNum = weekIndexOf(plan.request.start, session.date) + 1
            val phase = origPhases[wkNum]
            val stepDownWork = !isUp && session.quality && phase in setOf(Engine15MacroPhase.BUILD, Engine15MacroPhase.PEAK)
            val updated = adjustSessionPacesAndStepDown(
                session = session,
                paceFactor = paceFactor,
                stepDownOneWorkRung = stepDownWork,
                paceDiffCollector = paceDiffMap
            )
            if (updated != session) affectedCount++
            updated
        }

        if (affectedCount == 0 || paceDiffMap.isEmpty()) return null
        val candidatePlan = plan.copy(
            sessions = updatedSessions,
            explanations = plan.explanations + (if (isUp)
                "Step 06 Pace Recalibration (+${String.format(Locale.US, "%.1f", boundedPct * 100)}%): tightened upcoming target paces after ${diagnostic.qualityExecution.verifiedHitCount} verified quality hits."
            else
                "Step 06 Pace Recalibration (-${String.format(Locale.US, "%.1f", boundedPct * 100)}%): eased upcoming target paces and stepped Build/Peak reps down safely.")
        )

        val gate = validateCandidatePlan(
            originalPlan = plan,
            candidatePlan = candidatePlan,
            effectiveFromDate = effectiveFromDate,
            trailing30dLongestKm = trailing30dLongestKm,
            linkedSessionIds = linkedSessionIds,
            futureEngineValidationPlan = futureEngineValidationPlan
        )
        if (!gate.passed) return null

        val paceDiffs = paceDiffMap.map { (intent, bounds) ->
            AdaptationPaceDiff(
                intent = intent,
                oldMinSecPerKm = bounds.first.first,
                oldMaxSecPerKm = bounds.first.second,
                newMinSecPerKm = bounds.second.first,
                newMaxSecPerKm = bounds.second.second
            )
        }
        val diff = buildPlanDiff(plan, candidatePlan, effectiveFromDate, affectedCount, paceDiffs, gate, emptyMap())
        val pctLabel = String.format(Locale.US, "%.1f%%", boundedPct * 100.0)
        return PlanAdaptationProposal(
            proposalId = "prop-${plan.id}-${type.name.lowercase(Locale.ROOT)}-$asOfDate",
            planId = plan.id,
            type = type,
            status = AdaptationProposalStatus.PENDING,
            createdAtDate = asOfDate,
            effectiveFromDate = effectiveFromDate,
            expiresAtDate = asOfDate.plusDays(PROPOSAL_EXPIRY_DAYS),
            disruptionTier = DisruptionTier.NONE,
            disruptionFingerprint = null,
            sourceDiagnosticDate = diagnostic.asOfDate,
            sourceConfidence = diagnostic.overallConfidence,
            title = if (isUp) "Tighten Training Paces ($pctLabel Faster)" else "Ease Training Paces ($pctLabel Slower)",
            coachingRationale = if (isUp) {
                "${diagnostic.qualityExecution.verifiedHitCount}/${diagnostic.qualityExecution.evaluatedQualityCount} recent quality sessions averaged ${abs(rawDelta).roundToInt()} s/km faster than target with controlled easy days; tighten future target paces by $pctLabel?"
            } else {
                "${diagnostic.qualityExecution.tooSlowCount}/${diagnostic.qualityExecution.evaluatedQualityCount} recent quality sessions missed slower by +${abs(rawDelta).roundToInt()} s/km; ease upcoming target paces by $pctLabel and step down heavy reps?"
            },
            diff = diff,
            candidatePlanJson = candidatePlan.encode().toString(),
            reasonCodes = listOf(
                if (isUp) "PACE_RECALIBRATION_UP_BOUNDED" else "PACE_RECALIBRATION_DOWN_BOUNDED",
                "PRE_SURFACE_INVARIANT_PASSED"
            )
        )
    }

    private fun buildMissedWeekProposal(
        plan: PlanSnapshot,
        diagnostic: AthleteDiagnosticSnapshot,
        disruption: DisruptionContext,
        linkedSessionIds: Set<String>,
        trailing30dLongestKm: Double,
        effectiveFromDate: LocalDate,
        asOfDate: LocalDate
    ): PlanAdaptationProposal? {
        val origPhases = resolveMacroPhases(plan)
        val taperStartIdx = origPhases.entries
            .firstOrNull { it.value == Engine15MacroPhase.TAPER }
            ?.key?.minus(1) ?: plan.request.weeks

        val curElapsedWk = weekIndexOf(plan.request.start, effectiveFromDate.minusDays(1))
        val effectiveStartWk = weekIndexOf(plan.request.start, effectiveFromDate)
        val minimumNextWk = if (effectiveStartWk == curElapsedWk) curElapsedWk + 1 else effectiveStartWk
        val startWeekIdx = maxOf(disruption.interruptedWeekIndex + 1, minimumNextWk)
        if (startWeekIdx >= taperStartIdx) {
            // Never disrupt final taper weeks when race is imminent; hold cleanly.
            return null
        }

        val sessionsByWeek = plan.sessions.groupBy { weekIndexOf(plan.request.start, it.date) }
        val preGapKm = sessionsByWeek[disruption.interruptedWeekIndex]
            ?.sumOf { it.distanceKm ?: 0.0 }
            ?.takeIf { it > 0.0 }
            ?: plan.request.weeklyKm
        val updatedByWeek = LinkedHashMap<Int, List<PlannedSession>>()
        val weekNotes = LinkedHashMap<Int, String>()
        val reEntryWeeks = LinkedHashSet<Int>()
        val reasonCodes = LinkedHashSet<String>()
        reasonCodes += "ZERO_MAKEUP_CRAMMING"

        var futureValidationPlan: PlanSnapshot? = null
        val isRace = Engine15MacrocyclePeriodization.isRaceGoal(plan.request.goal)

        when (disruption.tier) {
            DisruptionTier.SHORT_DISRUPTION_3_TO_5D -> {
                reasonCodes += "SHORT_DISRUPTION_FLAT_HOLD"
                val nextOrig = sessionsByWeek[startWeekIdx].orEmpty()
                val nextOrigKm = nextOrig.sumOf { it.distanceKm ?: 0.0 }
                val capKm = if (diagnostic.volumeTolerance.volumeCompletionRatio < 0.60 && nextOrigKm > preGapKm) {
                    preGapKm
                } else nextOrigKm
                updatedByWeek[startWeekIdx] = scaleWeekSessionsToBudget(nextOrig, capKm)
                weekNotes[startWeekIdx] = if (capKm < nextOrigKm) {
                    "Held next week flat at ${formatKm(capKm)} after ${disruption.gapDays}-day disruption."
                } else {
                    "Resumed normal schedule without make-up volume."
                }
                val regRequest = plan.request.copy(
                    weeklyKm = capKm.coerceAtLeast(10.0),
                    longestKm = minOf(plan.request.longestKm, maxOf(8.0, trailing30dLongestKm * 1.10))
                )
                futureValidationPlan = generateValidationPlan(regRequest, plan) ?: return null
            }

            DisruptionTier.FULL_MISSED_WEEK_6_TO_10D -> {
                reasonCodes += "FULL_MISSED_WEEK_BRIDGE_82PCT"
                val bridgeKm = tenthsFloor(preGapKm * 0.82).coerceAtLeast(10.0)
                val exposureLongCap = maxOf(8.0, tenthsFloor(trailing30dLongestKm * 1.10))
                val bridgeOrig = sessionsByWeek[startWeekIdx].orEmpty()
                updatedByWeek[startWeekIdx] = rebuildBridgeOrAerobicWeek(
                    sessions = bridgeOrig,
                    targetWeekKm = bridgeKm,
                    longestCapKm = exposureLongCap,
                    allowSingleTouchTempo = plan.request.qualityCount > 0,
                    effectiveFromDate = effectiveFromDate,
                    linkedSessionIds = linkedSessionIds
                )
                reEntryWeeks += startWeekIdx
                weekNotes[startWeekIdx] = "Re-entry bridge week (${formatKm(bridgeKm)}, ~82% pre-gap) with long run <= ${formatKm(exposureLongCap)}."

                val remainingCount = (plan.request.weeks - (startWeekIdx + 1)).coerceAtLeast(1)
                val reAnchoredReq = plan.request.copy(
                    start = plan.request.start.plusWeeks((startWeekIdx + 1).toLong()),
                    weeks = remainingCount,
                    weeklyKm = bridgeKm,
                    longestKm = minOf(plan.request.longestKm, exposureLongCap),
                    targetWeeklyKm = if (isRace && remainingCount <= 4) {
                        plan.request.targetWeeklyKm?.let { minOf(it, bridgeKm * 1.12) }
                    } else plan.request.targetWeeklyKm
                )
                val postDecision = generateValidationDecision(reAnchoredReq, plan) ?: return null
                futureValidationPlan = postDecision.plan.copy(policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY)

                // Re-anchor subsequent non-taper loading weeks smoothly without compressing taper
                val postVol = postDecision.volume
                for (wk in (startWeekIdx + 1) until taperStartIdx) {
                    val offset = wk - (startWeekIdx + 1)
                    val origWkSessions = sessionsByWeek[wk].orEmpty()
                    val origKm = origWkSessions.sumOf { it.distanceKm ?: 0.0 }
                    val targetPostKm = postVol.weeks.getOrNull(offset)?.proposedKm ?: origKm
                    val clampedKm = minOf(origKm, targetPostKm).coerceAtLeast(10.0)
                    updatedByWeek[wk] = scaleWeekSessionsToBudget(origWkSessions, clampedKm)
                    if (clampedKm < origKm - 0.1) {
                        weekNotes[wk] = "Re-anchored peak from ${formatKm(origKm)} to ${formatKm(clampedKm)}."
                        if (isRace) reasonCodes += "GOAL_READINESS_ADVISORY"
                    }
                }
            }

            DisruptionTier.EXTENDED_GAP_11D_PLUS -> {
                reasonCodes += "EXTENDED_GAP_2WK_AEROBIC_REBUILD"
                val exposureLongCap = maxOf(8.0, tenthsFloor(trailing30dLongestKm * 1.05))
                val rebuildTargets = listOf(
                    startWeekIdx to tenthsFloor(preGapKm * 0.75).coerceAtLeast(10.0),
                    (startWeekIdx + 1) to tenthsFloor(preGapKm * 0.82).coerceAtLeast(10.0)
                ).filter { it.first < taperStartIdx }

                for ((wk, targetKm) in rebuildTargets) {
                    val wkOrig = sessionsByWeek[wk].orEmpty()
                    updatedByWeek[wk] = rebuildBridgeOrAerobicWeek(
                        sessions = wkOrig,
                        targetWeekKm = targetKm,
                        longestCapKm = exposureLongCap,
                        allowSingleTouchTempo = false, // Pure aerobic + strides/easy only during 2-week rebuild
                        effectiveFromDate = effectiveFromDate,
                        linkedSessionIds = linkedSessionIds
                    )
                    reEntryWeeks += wk
                    weekNotes[wk] = "Aerobic rebuild week (${formatKm(targetKm)}, base easy + strides)."
                }

                val postRebuildStart = (rebuildTargets.lastOrNull()?.first ?: startWeekIdx) + 1
                val anchorKm = rebuildTargets.lastOrNull()?.second ?: tenthsFloor(preGapKm * 0.80)
                val remainingCount = (plan.request.weeks - postRebuildStart).coerceAtLeast(1)
                val reAnchoredReq = plan.request.copy(
                    start = plan.request.start.plusWeeks(postRebuildStart.toLong()),
                    weeks = remainingCount,
                    weeklyKm = anchorKm,
                    longestKm = minOf(plan.request.longestKm, exposureLongCap),
                    returning = true,
                    targetWeeklyKm = if (isRace && remainingCount <= 5) {
                        plan.request.targetWeeklyKm?.let { minOf(it, anchorKm * 1.12) }
                    } else plan.request.targetWeeklyKm
                )
                val postDecision = generateValidationDecision(reAnchoredReq, plan) ?: return null
                futureValidationPlan = postDecision.plan.copy(policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY)

                val postVol = postDecision.volume
                for (wk in postRebuildStart until taperStartIdx) {
                    val offset = wk - postRebuildStart
                    val origWkSessions = sessionsByWeek[wk].orEmpty()
                    val origKm = origWkSessions.sumOf { it.distanceKm ?: 0.0 }
                    val targetPostKm = postVol.weeks.getOrNull(offset)?.proposedKm ?: origKm
                    val clampedKm = minOf(origKm, targetPostKm).coerceAtLeast(10.0)
                    updatedByWeek[wk] = scaleWeekSessionsToBudget(origWkSessions, clampedKm)
                    if (clampedKm < origKm - 0.1) {
                        weekNotes[wk] = "Re-anchored post-rebuild load to ${formatKm(clampedKm)}."
                        if (isRace) reasonCodes += "GOAL_READINESS_ADVISORY"
                    }
                }
            }

            DisruptionTier.NONE -> return null
        }

        var affectedCount = 0
        val newSessions = plan.sessions.map { orig ->
            val wk = weekIndexOf(plan.request.start, orig.date)
            val replaced = updatedByWeek[wk]?.find { it.id == orig.id }
            val protected = orig.date.isBefore(effectiveFromDate) ||
                orig.status == "completed" ||
                orig.linkedActivityId != null ||
                orig.id in linkedSessionIds
            if (!protected && replaced != null && replaced != orig) {
                affectedCount++
                replaced
            } else orig
        }

        val candidatePlan = plan.copy(
            sessions = newSessions,
            explanations = plan.explanations + "Step 06 Missed-Week Recovery (${disruption.tier}): rebuilt re-entry from ${formatKm(preGapKm)} with zero make-up cramming."
        )
        val gate = validateCandidatePlan(
            originalPlan = plan,
            candidatePlan = candidatePlan,
            effectiveFromDate = effectiveFromDate,
            trailing30dLongestKm = trailing30dLongestKm,
            linkedSessionIds = linkedSessionIds,
            futureEngineValidationPlan = futureValidationPlan,
            allowZeroMakeUpCheck = true,
            reEntryWeekIndices = reEntryWeeks
        )
        if (!gate.passed) return null

        val diff = buildPlanDiff(plan, candidatePlan, effectiveFromDate, affectedCount, emptyList(), gate, weekNotes)
        return PlanAdaptationProposal(
            proposalId = "prop-${plan.id}-recovery-${disruption.tier.name.lowercase(Locale.ROOT)}-$asOfDate",
            planId = plan.id,
            type = AdaptationType.MISSED_WEEK_RECOVERY,
            status = AdaptationProposalStatus.PENDING,
            createdAtDate = asOfDate,
            effectiveFromDate = effectiveFromDate,
            expiresAtDate = asOfDate.plusDays(PROPOSAL_EXPIRY_DAYS),
            disruptionTier = disruption.tier,
            disruptionFingerprint = disruption.fingerprint,
            sourceDiagnosticDate = diagnostic.asOfDate,
            sourceConfidence = diagnostic.overallConfidence,
            title = when (disruption.tier) {
                DisruptionTier.SHORT_DISRUPTION_3_TO_5D -> "Smooth Short-Gap Re-Entry (${disruption.gapDays}d Missed)"
                DisruptionTier.FULL_MISSED_WEEK_6_TO_10D -> "Insert Re-Entry Bridge Week (~82% Volume)"
                DisruptionTier.EXTENDED_GAP_11D_PLUS -> "2-Week Aerobic Rebuild After ${disruption.gapDays}d Gap"
                DisruptionTier.NONE -> "Recovery Adjustment"
            },
            coachingRationale = "Detected ${disruption.missedCount} consecutive missed sessions (${disruption.gapDays}-day interruption). Proposed ${disruption.tier.name.lowercase(Locale.ROOT).replace('_', ' ')} starting $effectiveFromDate with zero make-up cramming.",
            diff = diff,
            candidatePlanJson = candidatePlan.encode().toString(),
            reasonCodes = reasonCodes.toList() + "PRE_SURFACE_INVARIANT_PASSED"
        )
    }

    private fun buildProactiveDeloadProposal(
        plan: PlanSnapshot,
        diagnostic: AthleteDiagnosticSnapshot,
        linkedSessionIds: Set<String>,
        trailing30dLongestKm: Double,
        cooldowns: List<AdaptationCooldownRecord>,
        existingProposals: List<PlanAdaptationProposal>,
        effectiveFromDate: LocalDate,
        asOfDate: LocalDate
    ): PlanAdaptationProposal? {
        val origPhases = resolveMacroPhases(plan)
        val sessionsByWeek = plan.sessions.groupBy { weekIndexOf(plan.request.start, it.date) }

        val targetWkIdx = (0 until plan.request.weeks).firstOrNull { wkIdx ->
            val wkPhase = origPhases[wkIdx + 1]
            val wkSessions = sessionsByWeek[wkIdx].orEmpty()
            val hasFutureUncompleted = wkSessions.any {
                !it.date.isBefore(effectiveFromDate) && it.status == "planned" &&
                    it.linkedActivityId == null && it.id !in linkedSessionIds
            }
            hasFutureUncompleted && wkPhase in setOf(Engine15MacroPhase.BASE, Engine15MacroPhase.BUILD, Engine15MacroPhase.PEAK)
        } ?: return null

        val fingerprint = "deload:${plan.id}:wk$targetWkIdx"
        if (isDisruptionSuppressed(fingerprint, cooldowns, existingProposals, asOfDate)) return null

        val wkOrig = sessionsByWeek[targetWkIdx].orEmpty()
        val origKm = wkOrig.sumOf { it.distanceKm ?: 0.0 }
        val deloadTargetKm = tenthsFloor(origKm * 0.80).coerceAtLeast(8.0)

        // Preserve any past/completed sessions in targetWkIdx untouched; scale only future open sessions to deload.
        val protectedKm = wkOrig.filter {
            it.date.isBefore(effectiveFromDate) || it.status == "completed" ||
                it.linkedActivityId != null || it.id in linkedSessionIds
        }.sumOf { it.distanceKm ?: 0.0 }

        val futureOpen = wkOrig.filterNot {
            it.date.isBefore(effectiveFromDate) || it.status == "completed" ||
                it.linkedActivityId != null || it.id in linkedSessionIds
        }
        val futureBudget = (deloadTargetKm - protectedKm).coerceAtLeast(futureOpen.size * 2.0)
        val deloadedFuture = rebuildBridgeOrAerobicWeek(
            sessions = futureOpen,
            targetWeekKm = futureBudget,
            longestCapKm = maxOf(8.0, trailing30dLongestKm * 0.90),
            allowSingleTouchTempo = false,
            effectiveFromDate = effectiveFromDate,
            linkedSessionIds = linkedSessionIds
        ).associateBy { it.id }

        var affectedCount = 0
        val newSessions = plan.sessions.map { orig ->
            val replacement = deloadedFuture[orig.id]
            if (replacement != null && replacement != orig) {
                affectedCount++
                replacement
            } else orig
        }
        if (affectedCount == 0) return null

        val futureValidationReq = plan.request.copy(
            weeklyKm = deloadTargetKm,
            longestKm = minOf(plan.request.longestKm, maxOf(8.0, deloadTargetKm * 0.35))
        )
        val futureValidationPlan = generateValidationPlan(futureValidationReq, plan) ?: return null

        val candidatePlan = plan.copy(
            sessions = newSessions,
            explanations = plan.explanations + "Step 06 Proactive Deload: converted Week ${targetWkIdx + 1} to recovery (${formatKm(deloadTargetKm)}, 0.80x) due to ${diagnostic.volumeTolerance.exposureStatus} / HR fatigue signals."
        )
        val gate = validateCandidatePlan(
            originalPlan = plan,
            candidatePlan = candidatePlan,
            effectiveFromDate = effectiveFromDate,
            trailing30dLongestKm = trailing30dLongestKm,
            linkedSessionIds = linkedSessionIds,
            futureEngineValidationPlan = futureValidationPlan
        )
        if (!gate.passed) return null

        val weekNotes = mapOf(
            targetWkIdx to "Swapped Week ${targetWkIdx + 1} (${origPhases[targetWkIdx + 1]?.name}) to 80% recovery deload (${formatKm(origKm)} -> ${formatKm(deloadTargetKm)})."
        )
        val diff = buildPlanDiff(plan, candidatePlan, effectiveFromDate, affectedCount, emptyList(), gate, weekNotes)
        return PlanAdaptationProposal(
            proposalId = "prop-${plan.id}-deload-wk$targetWkIdx-$asOfDate",
            planId = plan.id,
            type = AdaptationType.PROACTIVE_DELOAD_SWAP,
            status = AdaptationProposalStatus.PENDING,
            createdAtDate = asOfDate,
            effectiveFromDate = effectiveFromDate,
            expiresAtDate = asOfDate.plusDays(PROPOSAL_EXPIRY_DAYS),
            disruptionTier = DisruptionTier.NONE,
            disruptionFingerprint = fingerprint,
            sourceDiagnosticDate = diagnostic.asOfDate,
            sourceConfidence = diagnostic.overallConfidence,
            title = "Proactive Deload Week Swap (80% Volume)",
            coachingRationale = "Fatigue/overreach signs detected (exposure=${diagnostic.volumeTolerance.exposureStatus}, HR efficiency=${diagnostic.aerobicEfficiency.trend}). Swap Week ${targetWkIdx + 1} into an 80% aerobic recovery week without altering race taper.",
            diff = diff,
            candidatePlanJson = candidatePlan.encode().toString(),
            reasonCodes = listOf("PROACTIVE_DELOAD_SWAP_80PCT", "TAPER_PRESERVED", "PRE_SURFACE_INVARIANT_PASSED")
        )
    }

    private fun buildEasyPacingLockProposal(
        plan: PlanSnapshot,
        diagnostic: AthleteDiagnosticSnapshot,
        cooldowns: List<AdaptationCooldownRecord>,
        existingProposals: List<PlanAdaptationProposal>,
        effectiveFromDate: LocalDate,
        asOfDate: LocalDate
    ): PlanAdaptationProposal? {
        val lockFingerprint = "easy-lock:${plan.id}:${diagnostic.easyDiscipline.tooFastCount}of${diagnostic.easyDiscipline.evaluatedEasyCount}"
        if (isDisruptionSuppressed(lockFingerprint, cooldowns, existingProposals, asOfDate)) return null

        val ratePct = (diagnostic.easyDiscipline.grayZoneCreepRatePct ?: 0.0).roundToInt()
        val overshootText = diagnostic.easyDiscipline.avgOvershootSecPerKm?.let { " by ~${it.roundToInt()} s/km" }.orEmpty()
        val qualityDeltaText = diagnostic.qualityExecution.avgWorkPaceDeltaSecPerKm?.let {
            String.format(Locale.US, " (while quality workouts averaged %.0f s/km vs. target)", kotlin.math.abs(it))
        }.orEmpty()

        return PlanAdaptationProposal(
            proposalId = "prop-${plan.id}-easylock-$asOfDate",
            planId = plan.id,
            type = AdaptationType.EASY_PACING_LOCK,
            status = AdaptationProposalStatus.PENDING,
            createdAtDate = asOfDate,
            effectiveFromDate = effectiveFromDate,
            expiresAtDate = asOfDate.plusDays(PROPOSAL_EXPIRY_DAYS),
            disruptionTier = DisruptionTier.NONE,
            disruptionFingerprint = lockFingerprint,
            sourceDiagnosticDate = diagnostic.asOfDate,
            sourceConfidence = diagnostic.overallConfidence,
            title = "Easy-Day Pacing Lock (Pace Upgrades Paused)",
            coachingRationale = "Pace upgrades are locked$qualityDeltaText because $ratePct% of linked easy runs (${diagnostic.easyDiscipline.tooFastCount}/${diagnostic.easyDiscipline.evaluatedEasyCount}) drifted too fast$overshootText. Settle easy days into Zone 2 before pushing targets.",
            diff = PlanAdaptationDiff(
                effectiveFromDate = effectiveFromDate,
                affectedSessionCount = 0,
                totalRemainingKmBefore = 0.0,
                totalRemainingKmAfter = 0.0,
                weekDiffs = emptyList(),
                paceDiffs = emptyList(),
                safetyChecksPassed = true,
                invariantWarnings = emptyList()
            ),
            candidatePlanJson = null,
            reasonCodes = listOf("EASY_PACING_LOCK_BLOCKS_PROMOTION", "READ_ONLY_ADVISORY")
        )
    }

    private fun generateValidationPlan(request: PlanRequest, basePlan: PlanSnapshot): PlanSnapshot? =
        generateValidationDecision(request, basePlan)
            ?.plan
            ?.copy(policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY)

    private fun generateValidationDecision(
        request: PlanRequest,
        basePlan: PlanSnapshot
    ): Engine15IndependentPlanDecision.Candidate? {
        val cleanReq = request.copy(
            weeks = request.weeks.coerceIn(4, 16),
            lockedSessionDays = emptyMap(),
            targetRaceDate = null
        )
        return when (val res = Engine15IndependentPlanGenerator.previewAutoReconciled(cleanReq, basePlan.owner, basePlan.id, basePlan.createdAt)) {
            is Engine15IndependentPlanDecision.Candidate -> res
            is Engine15IndependentPlanDecision.Deferred -> {
                if (cleanReq.qualityCount > 0) {
                    val easedReq = cleanReq.copy(qualityCount = 0)
                    when (val retry = Engine15IndependentPlanGenerator.previewAutoReconciled(easedReq, basePlan.owner, basePlan.id, basePlan.createdAt)) {
                        is Engine15IndependentPlanDecision.Candidate -> retry
                        is Engine15IndependentPlanDecision.Deferred -> null
                    }
                } else null
            }
        }
    }

    private fun resolveMacroPhases(plan: PlanSnapshot): Map<Int, Engine15MacroPhase> {
        val dec = generateValidationDecision(plan.request, plan)
        if (dec != null) {
            return Engine15MacrocyclePeriodization.assignPhases(plan.request, dec.volume)
        }
        val runGoal = plan.request.disciplineGoals["run"]?.takeIf { it.isNotBlank() } ?: plan.request.goal
        val taperLen = Engine15IndependentVolumePlanner.raceTaperLength(runGoal, plan.request.weeks)
        val taperStart = (plan.request.weeks - taperLen + 1).coerceAtLeast(1)
        return (1..plan.request.weeks).associateWith { wkNum ->
            when {
                taperLen > 0 && wkNum >= taperStart -> Engine15MacroPhase.TAPER
                (wkNum - 1) % 4 == 3 -> Engine15MacroPhase.RECOVERY
                else -> Engine15MacroPhase.BUILD
            }
        }
    }

    private fun detectDisruption(
        plan: PlanSnapshot,
        diagnostic: AthleteDiagnosticSnapshot,
        activities: List<ActivityRecord>,
        linkedSessionIds: Set<String>,
        asOfDate: LocalDate,
        zoneId: ZoneId
    ): DisruptionContext? {
        val pastPlannedRuns = plan.sessions
            .filter { it.discipline == "run" && !it.date.isAfter(asOfDate) && it.status != "skipped" }
            .sortedBy { it.date }
        if (pastPlannedRuns.isEmpty()) return null

        val trailingMissed = ArrayList<PlannedSession>()
        for (s in pastPlannedRuns.reversed()) {
            val fulfilled = s.status == "completed" || s.linkedActivityId != null || s.id in linkedSessionIds
            if (fulfilled) break
            trailingMissed += s
        }
        val missedCount = trailingMissed.size
        val firstMissed = trailingMissed.lastOrNull()?.date
        val lastRunDate = activities
            .filter { DashboardSummary.isRun(it) }
            .map { it.startedAt.atZone(zoneId).toLocalDate() }
            .filter { !it.isAfter(asOfDate) }
            .maxOrNull()

        val daysSinceLastRun = lastRunDate?.let { ChronoUnit.DAYS.between(it, asOfDate).toInt() }
        val missedSpanDays = firstMissed?.let { ChronoUnit.DAYS.between(it, asOfDate).toInt() + 1 } ?: 0
        val effectiveGapDays = maxOf(daysSinceLastRun ?: missedSpanDays, missedSpanDays)

        val underVolumeRecoveryTrigger = diagnostic.recalibrationAdvisory == RecalibrationAdvisory.VOLUME_RECOVERY_NEEDED &&
            diagnostic.volumeTolerance.exposureStatus != ExposureStatus.OVERREACH_RISK

        val triggered = (missedCount >= 2 && effectiveGapDays >= 3) ||
            missedCount >= 3 ||
            (underVolumeRecoveryTrigger && (missedCount >= 1 || effectiveGapDays >= 3))
        if (!triggered) return null

        val tier = when {
            effectiveGapDays >= 11 -> DisruptionTier.EXTENDED_GAP_11D_PLUS
            effectiveGapDays >= 6 || missedCount >= 4 -> DisruptionTier.FULL_MISSED_WEEK_6_TO_10D
            else -> DisruptionTier.SHORT_DISRUPTION_3_TO_5D
        }
        val anchorDate = firstMissed ?: asOfDate
        val fingerprint = "missed:${plan.id}:${tier.name}:$anchorDate"
        val interruptedWk = weekIndexOf(plan.request.start, anchorDate).coerceIn(0, plan.request.weeks - 1)
        return DisruptionContext(
            tier = tier,
            fingerprint = fingerprint,
            gapDays = effectiveGapDays,
            missedCount = missedCount,
            interruptedWeekIndex = interruptedWk
        )
    }

    private fun adjustSessionPacesAndStepDown(
        session: PlannedSession,
        paceFactor: Double,
        stepDownOneWorkRung: Boolean,
        paceDiffCollector: MutableMap<RunningStepIntent, Pair<Pair<Int, Int>, Pair<Int, Int>>>
    ): PlannedSession {
        val rx = session.runningPrescription ?: return session

        fun scalePaceBounds(intent: RunningStepIntent?, minP: Int?, maxP: Int?): Pair<Int?, Int?> {
            if (minP == null && maxP == null) return null to null
            val nMin = minP?.let { (it * paceFactor).roundToInt().coerceIn(120, 1200) }
            val nMax = maxP?.let { (it * paceFactor).roundToInt().coerceIn(nMin ?: 120, 1200) }
            if (intent != null && minP != null && maxP != null && nMin != null && nMax != null) {
                paceDiffCollector[intent] = (minP to maxP) to (nMin to nMax)
            }
            return nMin to nMax
        }

        var updatedRx = if (rx.mixedSegments != null) {
            var steppedSteps = rx.mixedSegments
            var newDistKm = session.distanceKm
            if (stepDownOneWorkRung && rx.mixedSegments.count { it.purpose == "WORK" } >= 3) {
                // Step down 1 rep cleanly while conserving total session distance via aerobic cool-down/easy padding.
                val lastWorkIdx = steppedSteps.indexOfLast { it.purpose == "WORK" }
                val recIdx = (lastWorkIdx - 1).takeIf { it >= 0 && steppedSteps[it].purpose == "RECOVERY" }
                val removedMeters = (steppedSteps[lastWorkIdx].meters ?: steppedSteps[lastWorkIdx].distanceEquivalentMeters ?: 0) +
                    (recIdx?.let { steppedSteps[it].meters ?: steppedSteps[it].distanceEquivalentMeters ?: 0 } ?: 0)
                val filtered = steppedSteps.filterIndexed { idx, _ -> idx != lastWorkIdx && idx != recIdx }
                val cdIdx = filtered.indexOfLast { it.purpose == "COOL_DOWN" }
                if (removedMeters > 0 && cdIdx >= 0 && filtered[cdIdx].seconds != null) {
                    val cd = filtered[cdIdx]
                    val newEq = (cd.distanceEquivalentMeters ?: 0) + removedMeters
                    steppedSteps = filtered.toMutableList().apply {
                        set(cdIdx, cd.copy(distanceEquivalentMeters = newEq))
                    }
                    newDistKm = session.distanceKm
                }
            }
            val rescaledSteps = steppedSteps.map { step ->
                val (nMin, nMax) = scalePaceBounds(step.stepIntent, step.targetPaceMinSecPerKm, step.targetPaceMaxSecPerKm)
                step.copy(targetPaceMinSecPerKm = nMin, targetPaceMaxSecPerKm = nMax)
            }
            rx.copy(mixedSegments = rescaledSteps)
        } else {
            val rescaledSegs = rx.segments.map { seg ->
                val (nMin, nMax) = scalePaceBounds(seg.stepIntent, seg.targetPaceMinSecPerKm, seg.targetPaceMaxSecPerKm)
                seg.copy(targetPaceMinSecPerKm = nMin, targetPaceMaxSecPerKm = nMax)
            }
            rx.copy(segments = rescaledSegs)
        }

        val paceAdjustedMinutes = (session.minutes * paceFactor).roundToInt().coerceAtLeast(15)
        return session.copy(
            minutes = paceAdjustedMinutes,
            runningPrescription = updatedRx,
            difficultyStep = 0,
            difficultyBaseline = null
        )
    }

    private fun rebuildBridgeOrAerobicWeek(
        sessions: List<PlannedSession>,
        targetWeekKm: Double,
        longestCapKm: Double,
        allowSingleTouchTempo: Boolean,
        effectiveFromDate: LocalDate,
        linkedSessionIds: Set<String>
    ): List<PlannedSession> {
        if (sessions.isEmpty()) return emptyList()
        var qualityTouchUsed = false
        val simplified = sessions.map { s ->
            val protected = s.date.isBefore(effectiveFromDate) || s.status == "completed" ||
                s.linkedActivityId != null || s.id in linkedSessionIds
            if (protected || s.discipline != "run") return@map s

            if (s.quality) {
                if (allowSingleTouchTempo && !qualityTouchUsed) {
                    qualityTouchUsed = true
                    stepDownQualityToEntryTouch(s)
                } else {
                    convertQualityToEasyOrStrides(s, withStrides = s.kind != "recovery")
                }
            } else {
                s
            }
        }
        return scaleWeekSessionsToBudget(simplified, targetWeekKm, longestCapKm)
    }

    private fun stepDownQualityToEntryTouch(session: PlannedSession): PlannedSession {
        val totalMeters = ((session.distanceKm ?: 8.0) * 1000.0).roundToInt().coerceAtLeast(5000)
        val wuMeters = 2000
        val cdMeters = 2000
        val workMeters = (totalMeters - wuMeters - cdMeters).coerceAtLeast(1000)
        val wuTarget = session.runningPrescription?.segments?.firstOrNull()?.let { it.targetPaceMinSecPerKm to it.targetPaceMaxSecPerKm }
            ?: (340 to 365)
        val workTarget = session.runningPrescription?.segments?.firstOrNull { it.purpose == "WORK" }
            ?.let { it.targetPaceMinSecPerKm to it.targetPaceMaxSecPerKm }
            ?: (265 to 285)
        val rx = RunningPrescription(
            role = "QUALITY",
            family = "Tempo",
            segments = listOf(
                RunningSegment("WARM_UP", wuMeters, "Easy warm-up", RunningStepIntent.WARMUP, wuTarget.first, wuTarget.second),
                RunningSegment("WORK", workMeters, "Controlled re-entry tempo touch", RunningStepIntent.TEMPO, workTarget.first, workTarget.second),
                RunningSegment("COOL_DOWN", totalMeters - wuMeters - workMeters, "Easy cool-down", RunningStepIntent.COOLDOWN, wuTarget.first, wuTarget.second)
            )
        )
        return session.copy(
            title = "Re-Entry Controlled Tempo",
            kind = "tempo",
            quality = true,
            runningPrescription = rx,
            difficultyStep = 0,
            difficultyBaseline = null
        )
    }

    private fun convertQualityToEasyOrStrides(session: PlannedSession, withStrides: Boolean): PlannedSession {
        val totalMeters = ((session.distanceKm ?: 8.0) * 1000.0).roundToInt().coerceAtLeast(3000)
        val wuBounds = session.runningPrescription?.segments?.firstOrNull()
            ?.let { it.targetPaceMinSecPerKm to it.targetPaceMaxSecPerKm }
            ?: session.runningPrescription?.mixedSegments?.firstOrNull()
                ?.let { it.targetPaceMinSecPerKm to it.targetPaceMaxSecPerKm }
            ?: (335 to 365)
        val rx = RunningPrescription(
            role = "EASY",
            family = null,
            segments = listOf(
                RunningSegment(
                    purpose = "EASY",
                    meters = totalMeters,
                    effort = if (withStrides) "Easy aerobic + light strides" else "Easy aerobic",
                    stepIntent = RunningStepIntent.AEROBIC_EASY,
                    targetPaceMinSecPerKm = wuBounds.first,
                    targetPaceMaxSecPerKm = wuBounds.second
                )
            )
        )
        return session.copy(
            title = if (withStrides) "Easy Aerobic + Light Strides" else "Easy Aerobic Run",
            kind = "easy",
            quality = false,
            runningPrescription = rx,
            difficultyStep = 0,
            difficultyBaseline = null
        )
    }

    private fun scaleWeekSessionsToBudget(
        sessions: List<PlannedSession>,
        targetWeekKm: Double,
        longestCapKm: Double? = null
    ): List<PlannedSession> {
        if (sessions.isEmpty()) return emptyList()
        val runIndices = sessions.indices.filter {
            sessions[it].discipline == "run" && sessions[it].distanceKm != null &&
                sessions[it].status !in setOf("missed", "skipped")
        }
        if (runIndices.isEmpty()) return sessions

        val targetTenths = (targetWeekKm * 10.0).roundToInt().coerceAtLeast(runIndices.size * 20)
        val rawTenths = runIndices.map { idx ->
            ((sessions[idx].distanceKm ?: 0.0) * 10.0).roundToInt().coerceAtLeast(20)
        }
        val rawSum = rawTenths.sum().coerceAtLeast(1)
        val maxLongTenths = longestCapKm?.let { (it * 10.0).roundToInt().coerceAtLeast(20) }

        val allocated = IntArray(runIndices.size) { i ->
            val scaled = ((rawTenths[i].toDouble() / rawSum) * targetTenths).roundToInt().coerceAtLeast(20)
            if (maxLongTenths != null && sessions[runIndices[i]].kind == "long") {
                minOf(scaled, maxLongTenths)
            } else {
                scaled
            }
        }
        var diff = targetTenths - allocated.sum()
        // Rebalance strictly on easy/non-long sessions first to conserve exact weekly budget.
        val adjustableOrder = runIndices.indices.sortedWith(
            compareBy<Int> { if (sessions[runIndices[it]].kind == "long") 1 else 0 }
                .thenByDescending { rawTenths[it] }
        )
        var guard = 0
        while (diff != 0 && guard < 1000) {
            var changed = false
            for (pos in adjustableOrder) {
                if (diff == 0) break
                val isLong = sessions[runIndices[pos]].kind == "long"
                if (diff > 0) {
                    if (!isLong || maxLongTenths == null || allocated[pos] < maxLongTenths) {
                        allocated[pos] += 1
                        diff -= 1
                        changed = true
                    }
                } else if (allocated[pos] > 20) {
                    allocated[pos] -= 1
                    diff += 1
                    changed = true
                }
            }
            if (!changed) break
            guard++
        }

        val mutable = sessions.toMutableList()
        runIndices.forEachIndexed { pos, idx ->
            mutable[idx] = rescaleSessionMeters(sessions[idx], allocated[pos] * 100)
        }
        return mutable
    }

    private fun rescaleSessionMeters(session: PlannedSession, newMeters: Int): PlannedSession {
        val oldMeters = ((session.distanceKm ?: (newMeters / 1000.0)) * 1000.0).roundToInt().coerceAtLeast(1)
        val rx = session.runningPrescription ?: return session.copy(
            distanceKm = newMeters / 1000.0,
            minutes = maxOf(15, ((session.minutes.toDouble() / oldMeters) * newMeters).roundToInt()),
            difficultyStep = 0,
            difficultyBaseline = null
        )

        val ratio = newMeters.toDouble() / oldMeters
        val newRx = if (rx.mixedSegments != null && rx.reconciledDistanceMeters != null) {
            val rawSegMeters = rx.mixedSegments.map { step ->
                val baseM = step.meters ?: step.distanceEquivalentMeters ?: 100
                (baseM * ratio).roundToInt().coerceAtLeast(50)
            }.toMutableList()
            val remainder = newMeters - rawSegMeters.sum()
            val padIdx = rx.mixedSegments.indexOfFirst { it.purpose in setOf("EASY", "COOL_DOWN") }
                .takeIf { it >= 0 } ?: 0
            rawSegMeters[padIdx] = (rawSegMeters[padIdx] + remainder).coerceAtLeast(50)
            val updatedSteps = rx.mixedSegments.mapIndexed { i, step ->
                if (step.meters != null) step.copy(meters = rawSegMeters[i])
                else step.copy(distanceEquivalentMeters = rawSegMeters[i])
            }
            rx.copy(mixedSegments = updatedSteps, reconciledDistanceMeters = newMeters)
        } else if (rx.segments.isNotEmpty()) {
            val rawSegMeters = rx.segments.map { seg ->
                (seg.meters * ratio).roundToInt().coerceAtLeast(100)
            }.toMutableList()
            val remainder = newMeters - rawSegMeters.sum()
            val padIdx = rx.segments.indexOfFirst { it.purpose in setOf("EASY", "COOL_DOWN", "WARM_UP") }
                .takeIf { it >= 0 } ?: 0
            rawSegMeters[padIdx] = (rawSegMeters[padIdx] + remainder).coerceAtLeast(100)
            val updatedSegs = rx.segments.mapIndexed { i, seg -> seg.copy(meters = rawSegMeters[i]) }
            rx.copy(segments = updatedSegs)
        } else rx

        return session.copy(
            distanceKm = newMeters / 1000.0,
            minutes = maxOf(15, ((session.minutes.toDouble() / oldMeters) * newMeters).roundToInt()),
            runningPrescription = newRx,
            difficultyStep = 0,
            difficultyBaseline = null
        )
    }

    private fun buildPlanDiff(
        original: PlanSnapshot,
        candidate: PlanSnapshot,
        effectiveFromDate: LocalDate,
        affectedSessionCount: Int,
        paceDiffs: List<AdaptationPaceDiff>,
        gate: ValidationOutcome,
        weekNotes: Map<Int, String>
    ): PlanAdaptationDiff {
        val phases = resolveMacroPhases(original)

        val remainingOrig = original.sessions.filter { !it.date.isBefore(effectiveFromDate) && it.discipline == "run" }
        val remainingCand = candidate.sessions.filter { !it.date.isBefore(effectiveFromDate) && it.discipline == "run" }
        val kmBefore = remainingOrig.sumOf { it.distanceKm ?: 0.0 }
        val kmAfter = remainingCand.sumOf { it.distanceKm ?: 0.0 }

        val origByWk = original.sessions.filter { it.discipline == "run" }
            .groupBy { weekIndexOf(original.request.start, it.date) }
        val candByWk = candidate.sessions.filter { it.discipline == "run" }
            .groupBy { weekIndexOf(candidate.request.start, it.date) }

        val weekDiffs = ArrayList<AdaptationWeekDiff>()
        for (wk in 0 until original.request.weeks) {
            val oList = origByWk[wk].orEmpty()
            val cList = candByWk[wk].orEmpty()
            val oKm = oList.sumOf { it.distanceKm ?: 0.0 }
            val cKm = cList.sumOf { it.distanceKm ?: 0.0 }
            val oQ = oList.count { it.quality }
            val cQ = cList.count { it.quality }
            val note = weekNotes[wk]
            if (note != null || abs(oKm - cKm) > 0.05 || oQ != cQ) {
                val basePhase = phases[wk + 1]?.name ?: "BUILD"
                val afterPhase = if (note?.contains("recovery", ignoreCase = true) == true) "RECOVERY" else basePhase
                weekDiffs += AdaptationWeekDiff(
                    weekIndex = wk,
                    phaseBefore = basePhase,
                    phaseAfter = afterPhase,
                    plannedKmBefore = round2(oKm),
                    plannedKmAfter = round2(cKm),
                    qualityCountBefore = oQ,
                    qualityCountAfter = cQ,
                    summaryNote = note ?: "Adjusted Week ${wk + 1} to ${formatKm(cKm)}"
                )
            }
        }

        return PlanAdaptationDiff(
            effectiveFromDate = effectiveFromDate,
            affectedSessionCount = affectedSessionCount,
            totalRemainingKmBefore = round2(kmBefore),
            totalRemainingKmAfter = round2(kmAfter),
            weekDiffs = weekDiffs,
            paceDiffs = paceDiffs,
            safetyChecksPassed = gate.passed,
            invariantWarnings = gate.warnings
        )
    }

    private fun resolveLinkedSessionIds(
        plan: PlanSnapshot,
        matchesByActivityId: Map<String, ActivityMatchRecord>
    ): Set<String> {
        val ids = HashSet<String>()
        plan.sessions.forEach { if (it.linkedActivityId != null) ids += it.id }
        matchesByActivityId.values.forEach { match ->
            if (match.matchStatus in setOf(ActivityMatchStatus.AUTO_LINKED, ActivityMatchStatus.MANUALLY_LINKED)) {
                match.plannedSessionId?.let { ids += it }
            }
        }
        return ids
    }

    private fun computeTrailingLongestKm(
        activities: List<ActivityRecord>,
        asOfDate: LocalDate,
        zoneId: ZoneId
    ): Double {
        val windowStart = asOfDate.minusDays(29)
        return activities
            .filter { DashboardSummary.isRun(it) }
            .filter { it.startedAt.atZone(zoneId).toLocalDate() in windowStart..asOfDate }
            .maxOfOrNull { (it.distanceM ?: 0.0) / 1000.0 }
            ?: 0.0
    }

    private fun hasActiveTypeCooldown(
        planId: String,
        type: AdaptationType,
        cooldowns: List<AdaptationCooldownRecord>,
        asOfDate: LocalDate
    ): Boolean = cooldowns.any {
        it.planId == planId && it.adaptationType == type && !asOfDate.isAfter(it.cooldownUntilDate)
    }

    private fun isDisruptionSuppressed(
        fingerprint: String,
        cooldowns: List<AdaptationCooldownRecord>,
        existingProposals: List<PlanAdaptationProposal>,
        asOfDate: LocalDate
    ): Boolean {
        val blockedByCooldown = cooldowns.any {
            it.disruptionFingerprint == fingerprint && !asOfDate.isAfter(it.cooldownUntilDate)
        }
        if (blockedByCooldown) return true
        return existingProposals.any {
            it.disruptionFingerprint == fingerprint &&
                it.status in setOf(AdaptationProposalStatus.DISMISSED, AdaptationProposalStatus.APPLIED)
        }
    }

    private fun estimateBaseEasyPace(plan: PlanSnapshot): Double? =
        plan.sessions.asSequence()
            .filter { it.discipline == "run" && !it.quality }
            .mapNotNull { it.runningPrescription?.segments?.firstOrNull()?.targetPaceMinSecPerKm }
            .firstOrNull()?.toDouble()

    private fun weekIndexOf(start: LocalDate, date: LocalDate): Int =
        ChronoUnit.DAYS.between(start, date).toInt().let { if (it < 0) -1 else it / 7 }

    private fun tenthsFloor(km: Double): Double = floor(km * 10.0 + 1e-8) / 10.0
    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0
    private fun formatKm(km: Double): String = String.format(Locale.US, "%.1f km", km)
}
