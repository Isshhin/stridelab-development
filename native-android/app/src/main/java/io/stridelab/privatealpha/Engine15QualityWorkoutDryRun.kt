package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

internal enum class Engine15QualityDryRunStatus {
    STRUCTURE_VALIDATED_DISTANCE_UNRECONCILED
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
    val status: Engine15QualityDryRunStatus =
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
        require(familyPolicyQualification == Engine15FamilyPolicyQualification.PROVISIONAL_ENGINEERING_RULE)
        require(selectionBasis == Engine15QualitySelectionBasis.GOAL_EXPERIENCE_MATRIX)
        require(family == IndependentQualityFamily.TEMPO)
        require(templateId == "engine15-tempo-intro-v1" && templateEvidence == "HEURISTIC")
        require(weekNumber > 0 && sessionId.isNotBlank())
        require(originalEasyDistanceKm.isFinite() && originalEasyDistanceKm > 0.0)
        require(totalSeconds > 0 && workSeconds > 0 && totalSeconds >= workSeconds)
        require(workout.total(WorkoutUnit.SECONDS)?.toInt() == totalSeconds)
        require(workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK)?.toInt() == workSeconds)
        require(workout.total(WorkoutUnit.METERS) == null)
        require((workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.RECOVERY) ?: 0.0) == 0.0)
        require(weeklyDistance.isNotEmpty() && weeklyDistance.map { it.weekNumber } ==
            weeklyDistance.map { it.weekNumber }.distinct().sorted())
        require(weeklyDistance.any { it.weekNumber == weekNumber && it.kind == Engine15IndependentVolumePlanner.WeekKind.LOADING })
        require(qualitySessionDistanceKm == null && weeklyDistanceAfterReplacementKm == null)
        require(!sourceEvidenceVerified && !templateAuthorized && !doseAuthorized &&
            !intensityAuthorized && !scheduled && !includedInPlan && !executable)
    }

    val baseWeeklyAccountingReconciled: Boolean get() = true
    val qualityDistanceReconciled: Boolean get() = false
    val completePlanDistanceAccounting: Boolean get() = false
    val developmentOnly: Boolean get() = true
}

internal object Engine15QualityWorkoutDryRunBuilder {
    const val POLICY_ID = "stridelab.engine15.tempo-structural-dry-run-v1"
    private const val EPS = 1e-6

    fun build(
        request: PlanRequest,
        selection: Engine15QualityFamilySelectionResult,
        volume: Engine15IndependentVolumePlanner.Result,
        sessions: List<PlannedSession>
    ): Engine15QualityWorkoutDryRun? {
        if (!BuildConfig.DEBUG ||
            selection.decision != Engine15QualitySelectionDecision.FAMILY_CANDIDATE ||
            selection.familyPolicyQualification != Engine15FamilyPolicyQualification.PROVISIONAL_ENGINEERING_RULE ||
            selection.selectionBasis != Engine15QualitySelectionBasis.GOAL_EXPERIENCE_MATRIX ||
            selection.selectedFamily != IndependentQualityFamily.TEMPO)
            return null

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
            IndependentQualityFamily.TEMPO, IndependentQualityStage.INTRO
        )
        val totalSeconds = template.totalSeconds().toInt()
        val workSeconds = template.workSeconds().toInt()
        if (template.workout.total(WorkoutUnit.METERS) != null ||
            template.workout.total(WorkoutUnit.SECONDS)?.toInt() != totalSeconds)
            return null

        return Engine15QualityWorkoutDryRun(
            policyId = POLICY_ID,
            selectionPolicyId = selection.policyId,
            familyPolicyQualification = selection.familyPolicyQualification,
            selectionBasis = selection.selectionBasis,
            family = IndependentQualityFamily.TEMPO,
            weekNumber = slot.weekNumber,
            sessionId = slot.sessionId,
            date = slot.date,
            originalEasyDistanceKm = slot.originalEasyDistanceKm,
            templateId = template.id,
            templateEvidence = template.evidence,
            workout = IndependentWorkoutComposition.decode(template.workout.encode()),
            totalSeconds = totalSeconds,
            workSeconds = workSeconds,
            weeklyDistance = weeklyDistance
        )
    }
}
