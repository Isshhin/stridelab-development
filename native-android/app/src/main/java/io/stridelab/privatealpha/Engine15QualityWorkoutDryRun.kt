package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

internal enum class Engine15QualityDryRunStatus {
    STRUCTURE_VALIDATED_DISTANCE_RECONCILED_FROM_ASSERTIONS,
    STRUCTURE_VALIDATED_DISTANCE_UNRECONCILED
}

internal enum class Engine15QualityDistanceStatus {
    DISTANCE_EQUIVALENTS_NOT_SUPPLIED, CLAIM_SET_CONFLICT, INVALID_CLAIM,
    USER_ASSERTED_ARITHMETIC_RECONCILED
}

internal data class Engine15TempoAlternative(
    val slot: Engine15QualityFamilyCandidateSlot,
    val budgetKm: Double,
    val distanceStatus: Engine15QualityDistanceStatus,
    val distanceMatch: Engine15M5ArithmeticMatch? = null,
    val distanceFailureCode: String? = null,
    val prescription: Engine15QualityWeekPrescription? = null
) {
    val weekNumber get() = slot.weekNumber
    val slotNumber get() = slot.slotNumber
    val sessionId get() = slot.sessionId
    val originalEasyDistanceKm get() = slot.originalEasyDistanceKm
    val workSeconds get() = prescription?.step?.workSeconds
    val totalSeconds get() = prescription?.step?.totalSeconds
    val progressionBasis get() = prescription?.basis
    val structurallyValid get() = true
    val distanceClaimProvenance get() = distanceMatch?.distanceClaimProvenance
    val weeklyDistanceAfterReplacementKm get() = budgetKm.takeIf { distanceMatch != null }
    val sourceDistancesVerified get() = false
    val coachDistanceVerified get() = false
    val intensityQualified get() = false
    val doseQualified get() = false
    val executable get() = false
}

internal data class Engine15QualityDryRunWeekDistance(
    val weekNumber: Int,
    val kind: Engine15IndependentVolumePlanner.WeekKind,
    val budgetKm: Double,
    val scheduledRunKm: Double
) {
    init {
        require(weekNumber > 0 && budgetKm.isFinite() && budgetKm > 0.0)
        require(scheduledRunKm.isFinite() && scheduledRunKm > 0.0)
        require(abs(budgetKm - scheduledRunKm) <= 1e-6)
    }
}

/** One development-only alternative; it never mutates the executable plan or weekly distance. */
internal data class Engine15QualityWorkoutDryRun(
    val policyId: String,
    val selectionPolicyId: String,
    val familyPolicyQualification: Engine15FamilyPolicyQualification,
    val selectionBasis: Engine15QualitySelectionBasis,
    val family: IndependentQualityFamily,
    val weekNumber: Int,
    val sessionId: String,
    val date: LocalDate,
    val originalEasyDistanceKm: Double,
    val templateId: String,
    val templateEvidence: String,
    val workout: IndependentWorkoutComposition,
    val totalSeconds: Int,
    val workSeconds: Int,
    val weeklyDistance: List<Engine15QualityDryRunWeekDistance>,
    val alternatives: List<Engine15TempoAlternative>,
    val status: Engine15QualityDryRunStatus = if (alternatives.all { it.distanceMatch != null })
        Engine15QualityDryRunStatus.STRUCTURE_VALIDATED_DISTANCE_RECONCILED_FROM_ASSERTIONS else
        Engine15QualityDryRunStatus.STRUCTURE_VALIDATED_DISTANCE_UNRECONCILED,
    val qualitySessionDistanceKm: Double? = null,
    val weeklyDistanceAfterReplacementKm: Double? = null,
    val sourceEvidenceVerified: Boolean = false,
    val templateAuthorized: Boolean = false,
    val doseAuthorized: Boolean = false,
    val intensityAuthorized: Boolean = false,
    val scheduled: Boolean = false,
    val includedInPlan: Boolean = false,
    val executable: Boolean = false
) {
    init {
        require(policyId == Engine15QualityWorkoutDryRunBuilder.POLICY_ID)
        require(selectionPolicyId == Engine15QualityFamilySelection.POLICY_ID)
        require(familyPolicyQualification in setOf(
            Engine15FamilyPolicyQualification.PROVISIONAL_ENGINEERING_RULE,
            Engine15FamilyPolicyQualification.USER_DIRECTED
        ))
        require(selectionBasis in setOf(
            Engine15QualitySelectionBasis.GOAL_EXPERIENCE_MATRIX,
            Engine15QualitySelectionBasis.EXPLICIT_USER_PREFERENCE
        ))

        require(templateId == "engine15-${family.name.lowercase()}-intro-v1" && templateEvidence == "HEURISTIC")
        require(weekNumber > 0 && sessionId.isNotBlank())
        require(originalEasyDistanceKm.isFinite() && originalEasyDistanceKm > 0.0)
        require(totalSeconds > 0 && workSeconds > 0 && totalSeconds >= workSeconds)
        require(workout.total(WorkoutUnit.SECONDS)?.toInt() == totalSeconds)
        require(workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK)?.toInt() == workSeconds)
        require(workout.total(WorkoutUnit.METERS) == null)
        require(((workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.RECOVERY) ?: 0.0) > 0.0) ==
            (family in setOf(
                IndependentQualityFamily.THRESHOLD,
                IndependentQualityFamily.INTERVALS,
                IndependentQualityFamily.HILLS,
                IndependentQualityFamily.FARTLEK
            )))
        require(weeklyDistance.isNotEmpty() && weeklyDistance.map { it.weekNumber } ==
            weeklyDistance.map { it.weekNumber }.distinct().sorted())
        require(weeklyDistance.any { it.weekNumber == weekNumber && it.kind == Engine15IndependentVolumePlanner.WeekKind.LOADING })
        require(qualitySessionDistanceKm == null && weeklyDistanceAfterReplacementKm == null)
        require(!sourceEvidenceVerified && !templateAuthorized && !doseAuthorized &&
            !intensityAuthorized && !scheduled && !includedInPlan && !executable)
    }

    val baseWeeklyAccountingReconciled: Boolean get() = true
    val qualityDistanceReconciled: Boolean get() = alternatives.isNotEmpty() && alternatives.all { it.distanceMatch != null }
    val completePlanDistanceAccounting: Boolean get() = qualityDistanceReconciled
    val alternativesAreAuthorizedProgression get() = false
    val developmentOnly: Boolean get() = true
}

internal object Engine15QualityWorkoutDryRunBuilder {
    const val POLICY_ID = "stridelab.engine15.tempo-structural-dry-run-v1"
    private const val EPS = 1e-6

    // The current exposure contract cannot verify completed Tempo work.
    // Keep the private dose at the lower pilot value across supported experience bands.
    fun workSeconds(experience: Int) = if (experience in 1..2) 600 else 720

    fun build(
        request: PlanRequest,
        selection: Engine15QualityFamilySelectionResult,
        volume: Engine15IndependentVolumePlanner.Result,
        sessions: List<PlannedSession>,
        assertedExposureSecondsOverride: Int? = null,
        allowExplicitUserFamily: Boolean = false,
        adaptiveQualityMode: Boolean = false
    ): Engine15QualityWorkoutDryRun? {
        val family = selection.selectedFamily ?: return null
        if (selection.decision != Engine15QualitySelectionDecision.FAMILY_CANDIDATE)
            return null
        val explicitAllowed = allowExplicitUserFamily &&
            selection.selectionBasis == Engine15QualitySelectionBasis.EXPLICIT_USER_PREFERENCE &&
            selection.familyPolicyQualification == Engine15FamilyPolicyQualification.USER_DIRECTED
        val matrixAllowed = selection.selectionBasis == Engine15QualitySelectionBasis.GOAL_EXPERIENCE_MATRIX &&
            selection.familyPolicyQualification == Engine15FamilyPolicyQualification.PROVISIONAL_ENGINEERING_RULE
        if (!matrixAllowed && !explicitAllowed) return null

        val slot = selection.candidateSlots.firstOrNull() ?: return null
        val selectedSession = sessions.singleOrNull { it.id == slot.sessionId } ?: return null
        if (selectedSession.kind != "easy" || selectedSession.date != slot.date ||
            selectedSession.distanceKm?.let { abs(it - slot.originalEasyDistanceKm) <= EPS } != true)
            return null

        val weeklyDistance = volume.weeks.map { week ->
            val weekNumber = week.index + 1
            val scheduled = sessions.filter {
                ChronoUnit.DAYS.between(request.start, it.date) / 7L == week.index.toLong()
            }
            val budget = week.proposedKm ?: return null
            if (scheduled.size != request.runDays || scheduled.any { it.distanceKm == null }) return null
            val actual = scheduled.sumOf { it.distanceKm!! }
            if (abs(budget - actual) > EPS) return null
            Engine15QualityDryRunWeekDistance(weekNumber, week.kind, budget, actual)
        }
        val template = IndependentQualityTemplates.get(
            family, IndependentQualityStage.INTRO
        )
        val prescriptions = Engine15QualityProgressionPolicy.plan(
            request, selection, assertedExposureSecondsOverride, adaptiveQualityMode = adaptiveQualityMode
        ).associateBy { it.weekNumber to it.slotNumber }
        val firstPrescription = prescriptions[slot.weekNumber to slot.slotNumber] ?: return null
        val workout = firstPrescription.step.workout
        val totalSeconds = firstPrescription.step.totalSeconds
        val workSeconds = firstPrescription.step.workSeconds

        val claims = request.qualityDistanceClaims.groupBy { it.weekNumber to it.slotNumber }
        val keys = selection.candidateSlots.map { it.weekNumber to it.slotNumber }.toSet()
        val conflict = claims.any { (key, rows) -> key !in keys || rows.size != 1 }
        val alternatives = selection.candidateSlots.map { candidate ->
            val budget = weeklyDistance.single { it.weekNumber == candidate.weekNumber }.budgetKm
            val weekPrescription = prescriptions[candidate.weekNumber to candidate.slotNumber] ?: return null
            val claim = claims[candidate.weekNumber to candidate.slotNumber]?.singleOrNull()
            when {
                conflict -> Engine15TempoAlternative(candidate, budget, Engine15QualityDistanceStatus.CLAIM_SET_CONFLICT, prescription = weekPrescription)
                claim == null -> Engine15TempoAlternative(candidate, budget, Engine15QualityDistanceStatus.DISTANCE_EQUIVALENTS_NOT_SUPPLIED, prescription = weekPrescription)
                else -> when (val result = Engine15M5TimedDistanceReconciler.reconcileSegmentClaim(
                    candidate.weekNumber, candidate.slotNumber, weekPrescription.family,
                    candidate.originalEasyDistanceKm, weekPrescription.step.workout,
                    weekPrescription.step.totalSeconds, weekPrescription.step.workSeconds, claim
                )) {
                    is Engine15M5SegmentDistanceDecision.Deferred -> Engine15TempoAlternative(candidate, budget,
                        Engine15QualityDistanceStatus.INVALID_CLAIM, distanceFailureCode = result.reasonCode, prescription = weekPrescription)
                    is Engine15M5SegmentDistanceDecision.ArithmeticOnly -> Engine15TempoAlternative(candidate, budget,
                        Engine15QualityDistanceStatus.USER_ASSERTED_ARITHMETIC_RECONCILED, result.match, prescription = weekPrescription)
                }
            }
        }

        return Engine15QualityWorkoutDryRun(
            policyId = POLICY_ID,
            selectionPolicyId = selection.policyId,
            familyPolicyQualification = selection.familyPolicyQualification,
            selectionBasis = selection.selectionBasis,
            family = family,
            weekNumber = slot.weekNumber,
            sessionId = slot.sessionId,
            date = slot.date,
            originalEasyDistanceKm = slot.originalEasyDistanceKm,
            templateId = template.id,
            templateEvidence = template.evidence,
            workout = workout,
            totalSeconds = totalSeconds,
            workSeconds = workSeconds,
            weeklyDistance = weeklyDistance,
            alternatives = alternatives
        )
    }
}
