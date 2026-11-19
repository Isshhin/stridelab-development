package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal enum class Engine15QualitySelectionDecision { FAMILY_CANDIDATE, DEFERRED, UNSUPPORTED }
internal enum class Engine15FamilyPolicyQualification { NOT_ASSESSED, USER_DIRECTED, PROVISIONAL_ENGINEERING_RULE, COACH_QUALIFIED }
internal enum class Engine15QualitySelectionBasis { EXPLICIT_USER_PREFERENCE, GOAL_EXPERIENCE_MATRIX }
internal enum class Engine15TemplateAuthorization { NOT_AUTHORIZED, AUTHORIZED }
internal enum class Engine15WorkoutExecutability { NON_EXECUTABLE, EXECUTABLE }

internal data class Engine15QualityFamilyCandidateSlot(
    val weekNumber: Int,
    val slotNumber: Int,
    val sessionId: String,
    val date: LocalDate,
    val originalEasyDistanceKm: Double
) {
    init {
        require(weekNumber > 0 && slotNumber > 0 && sessionId.isNotBlank())
        require(originalEasyDistanceKm.isFinite() && originalEasyDistanceKm > 0.0)
    }
}

internal data class Engine15QualityFamilySelectionResult(
    val policyId: String,
    val decision: Engine15QualitySelectionDecision,
    val familyPolicyQualification: Engine15FamilyPolicyQualification,
    val selectionBasis: Engine15QualitySelectionBasis? = null,
    val selectedFamily: IndependentQualityFamily? = null,
    val candidateSlots: List<Engine15QualityFamilyCandidateSlot> = emptyList(),
    val athleteBaselineStatus: AthleteBaselineStatus? = null,
    val athleteBaselineConfidence: AthleteBaselineConfidence? = null,
    val evidenceProvenance: String? = null,
    val exposureConfidence: IndependentQualityExposureConfidence? = null,
    val assertedRepeatedWorkSeconds: Int? = null,
    val reasonCode: String? = null,
    val explanation: String,
    val evidenceSessionIds: List<String> = emptyList(),
    val ruleIds: List<String>,
    val qualificationNotes: List<String>,
    val sourceEvidenceVerified: Boolean = false,
    val templateAuthorization: Engine15TemplateAuthorization = Engine15TemplateAuthorization.NOT_AUTHORIZED,
    val workoutExecutability: Engine15WorkoutExecutability = Engine15WorkoutExecutability.NON_EXECUTABLE,
    val templateId: String? = null
) {
    init {
        require(policyId == Engine15QualityFamilySelection.POLICY_ID)
        require(evidenceSessionIds.distinct() == evidenceSessionIds)
        require(candidateSlots.map { it.weekNumber }.distinct() == candidateSlots.map { it.weekNumber })
        require(ruleIds.distinct() == ruleIds && qualificationNotes.isNotEmpty())
        require(!sourceEvidenceVerified)
        require(templateAuthorization == Engine15TemplateAuthorization.NOT_AUTHORIZED)
        require(workoutExecutability == Engine15WorkoutExecutability.NON_EXECUTABLE)
        require(templateId == null)
        require(assertedRepeatedWorkSeconds == null || assertedRepeatedWorkSeconds in 1..14400)
        require((decision == Engine15QualitySelectionDecision.FAMILY_CANDIDATE) ==
            (selectedFamily != null && selectionBasis != null && reasonCode == null &&
                familyPolicyQualification != Engine15FamilyPolicyQualification.NOT_ASSESSED && candidateSlots.isNotEmpty()))
        require((decision == Engine15QualitySelectionDecision.FAMILY_CANDIDATE) ||
            (selectedFamily == null && selectionBasis == null && reasonCode != null &&
                familyPolicyQualification == Engine15FamilyPolicyQualification.NOT_ASSESSED && candidateSlots.isEmpty()))
    }
}

/** Chooses a family for review only; it deliberately does not construct a workout. */
internal object Engine15QualityFamilySelection {
    const val POLICY_ID = "stridelab.engine15.quality-family-selection-v3"

    private fun result(
        decision: Engine15QualitySelectionDecision,
        qualification: Engine15FamilyPolicyQualification = Engine15FamilyPolicyQualification.NOT_ASSESSED,
        basis: Engine15QualitySelectionBasis? = null,
        reason: String?,
        explanation: String,
        family: IndependentQualityFamily? = null,
        candidateSlots: List<Engine15QualityFamilyCandidateSlot> = emptyList(),
        athlete: Engine15AthleteCapacity? = null,
        evidenceIds: List<String> = emptyList(),
        rules: List<String> = emptyList(),
        provenance: String? = null,
        exposureConfidence: IndependentQualityExposureConfidence? = null,
        repeatedWorkSeconds: Int? = null,
        qualificationNotes: List<String> = emptyList()
    ) = Engine15QualityFamilySelectionResult(
        policyId = POLICY_ID,
        decision = decision,
        familyPolicyQualification = qualification,
        selectionBasis = basis,
        selectedFamily = family,
        candidateSlots = candidateSlots,
        athleteBaselineStatus = athlete?.status,
        athleteBaselineConfidence = athlete?.confidence,
        evidenceProvenance = provenance,
        exposureConfidence = exposureConfidence,
        assertedRepeatedWorkSeconds = repeatedWorkSeconds,
        reasonCode = reason,
        explanation = explanation,
        evidenceSessionIds = evidenceIds,
        ruleIds = (listOf("NO_TEMPLATE_OR_EXECUTION_AUTHORIZATION") + rules).distinct(),
        qualificationNotes = listOf(
            "Family selection is not authorization to construct or execute a quality workout.",
            "M2 Threshold and Tempo structures remain heuristic and do not qualify selection, dose, recovery or intensity.",
            "Quality exposure records supplied to this selector remain caller-asserted and are not source-verified."
        ) + qualificationNotes
    )

    fun select(
        request: PlanRequest,
        ownerId: String,
        asOf: LocalDate,
        evidence: List<IndependentQualitySegmentEvidence>,
        athlete: Engine15AthleteCapacity,
        scheduledSessions: List<PlannedSession>,
        recoveryWeeks: Set<Int>,
        externalSessions: List<Engine15M6CExternalSession>
    ): Engine15QualityFamilySelectionResult {
        if (request.qualityCount == 0)
            return result(Engine15QualitySelectionDecision.DEFERRED, reason = "NO_QUALITY_REQUESTED",
                explanation = "No coach-generated quality workout was requested.")
        if (request.qualityCount != 1)
            return result(Engine15QualitySelectionDecision.UNSUPPORTED, reason = "QUALITY_FREQUENCY_UNSUPPORTED",
                explanation = "This selector has no reviewed policy for choosing more than one quality family per loading week.")
        if (request.runDays !in 2..6 || request.weeks !in 1..16)
            return result(Engine15QualitySelectionDecision.UNSUPPORTED, reason = "RUNNING_PLAN_BOUNDS_UNSUPPORTED",
                explanation = "The selector supports two to six running days and one to sixteen weeks.")
        if (request.goal !in PlanDisciplineContract.goals.getValue("run"))
            return result(Engine15QualitySelectionDecision.UNSUPPORTED, reason = "RUNNING_GOAL_UNSUPPORTED",
                explanation = "The requested running goal is outside the current Engine 1.5 goal contract.")
        if (request.experience !in 0..2)
            return result(Engine15QualitySelectionDecision.UNSUPPORTED, reason = "RUNNER_EXPERIENCE_UNSUPPORTED",
                explanation = "The request uses an experience value outside the supported 0–2 contract.")
        if (request.runningRecoveryHold)
            return result(Engine15QualitySelectionDecision.DEFERRED, reason = "QUALITY_RECOVERY_REVIEW_REQUIRED",
                explanation = "An active running recovery hold requires a separate quality eligibility review.")
        if (ownerId.isBlank() || ownerId.length > 256)
            return result(Engine15QualitySelectionDecision.DEFERRED, reason = "QUALITY_EVIDENCE_OWNER_INVALID",
                explanation = "A valid local owner scope is required before training history can be compared.")
        if (evidence.any { it.ownerId != ownerId })
            return result(Engine15QualitySelectionDecision.DEFERRED, reason = "QUALITY_EVIDENCE_OWNER_CONFLICT",
                explanation = "Quality evidence from another owner scope cannot be considered.")

        val exposure = IndependentQualityFamily.entries.map { family ->
            IndependentQualityExposure.assess(evidence, ownerId, family, asOf)
        }
        if (exposure.any { it.confidence == IndependentQualityExposureConfidence.CONFLICT_REVIEW })
            return result(Engine15QualitySelectionDecision.DEFERRED, reason = "QUALITY_EXPOSURE_CONFLICT_REVIEW",
                explanation = "Recent quality records reuse a source or session identity inconsistently; resolve the evidence conflict before family review.",
                athlete = athlete, rules = listOf("M3_EXPOSURE_CONFLICT_REQUIRES_REVIEW"))

        val loadingWeeks = (1..request.weeks).filterNot { it in recoveryWeeks }
        if (loadingWeeks.isEmpty())
            return result(Engine15QualitySelectionDecision.DEFERRED, reason = "NO_LOADING_WEEK_FOR_QUALITY",
                explanation = "All requested weeks are recovery weeks; no quality-family slot was selected.",
                athlete = athlete, rules = listOf("RECOVERY_WEEKS_EXCLUDED"))
        val candidateSlots = mutableListOf<Engine15QualityFamilyCandidateSlot>()
        for (week in loadingWeeks) {
            val weekSessions = scheduledSessions.filter {
                ChronoUnit.DAYS.between(request.start, it.date) / 7L == week - 1L
            }
            val candidates = weekSessions.filter { session ->
                session.discipline == "run" && session.kind == "easy" &&
                    session.distanceKm?.let { it.isFinite() && it > 0.0 } == true &&
                    (request.qualityDays.isEmpty() || session.date.dayOfWeek.value in request.qualityDays) &&
                    session.id !in request.lockedSessionDays &&
                    externalSessions.none { external ->
                        Engine15IndependentPlanGenerator.conflict(
                            session.copy(kind = "quality", quality = true, runningPrescription = null), external.asPlannedSession()
                        )
                    } && weekSessions.none { other ->
                        other.id != session.id && Engine15IndependentPlanGenerator.conflict(
                            session.copy(kind = "quality", quality = true, runningPrescription = null), other
                        )
                    }
            }
            if (candidates.isEmpty())
                return result(Engine15QualitySelectionDecision.DEFERRED, reason = "QUALITY_SLOT_UNAVAILABLE",
                    explanation = "No unlocked, available easy-run slot in loading week $week satisfies the selected quality day and existing session-spacing checks.",
                    athlete = athlete, rules = listOf("CALENDAR_AND_LOCKS_CHECKED", "KNOWN_HARD_SESSION_SPACING_CHECKED"))
            candidates.minWith(compareBy<PlannedSession> { it.date }.thenBy { it.id }).let { session ->
                val slotNumber = weekSessions.filter { it.discipline == "run" }
                    .sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
                    .indexOfFirst { it.id == session.id } + 1
                candidateSlots += Engine15QualityFamilyCandidateSlot(
                    week, slotNumber, session.id, session.date, requireNotNull(session.distanceKm)
                )
            }
        }

        val supported = IndependentQualityFamily.entries.associateBy {
            it.name.lowercase().replaceFirstChar(Char::uppercase)
        }
        if (request.allowedQualityTypes.isNotEmpty() &&
            request.allowedQualityTypes.none { allowed -> supported.keys.any { it.equals(allowed, true) } })
            return result(Engine15QualitySelectionDecision.UNSUPPORTED, reason = "QUALITY_FAMILY_UNSUPPORTED",
                explanation = "The allowed quality families do not include a family with a current Engine 1.5 structure contract.", athlete = athlete)

        val basis: Engine15QualitySelectionBasis
        val qualification: Engine15FamilyPolicyQualification
        val family: IndependentQualityFamily
        if (request.preferredQualityExplicit) {
            family = supported.entries.singleOrNull { it.key.equals(request.preferredQuality, true) }?.value
                ?: return result(Engine15QualitySelectionDecision.UNSUPPORTED, reason = "QUALITY_PREFERRED_FAMILY_UNSUPPORTED",
                    explanation = "The explicitly chosen family has no supported Engine 1.5 family contract; no substitute was selected.", athlete = athlete)
            basis = Engine15QualitySelectionBasis.EXPLICIT_USER_PREFERENCE
            qualification = Engine15FamilyPolicyQualification.USER_DIRECTED
        } else {
            if (request.returning)
                return result(Engine15QualitySelectionDecision.DEFERRED, reason = "RETURN_TO_RUNNING_QUALITY_POLICY_UNAVAILABLE",
                    explanation = "Automatic quality-family selection for returning runners has no reviewed eligibility rule.", athlete = athlete,
                    rules = listOf("RETURNING_RUNNER_POLICY_REQUIRED"))
            if (request.runDays < 3)
                return result(Engine15QualitySelectionDecision.DEFERRED, reason = "RUN_FREQUENCY_POLICY_UNAVAILABLE",
                    explanation = "Automatic selection is deferred below three weekly runs because this provisional plan policy reserves a separate easy-running opportunity. This is an engineering safeguard, not a research-derived cutoff.", athlete = athlete,
                    rules = listOf("PROVISIONAL_MINIMUM_RUN_FREQUENCY"),
                    qualificationNotes = listOf("The three-day frequency boundary is a provisional engineering safeguard and requires coaching review."))
            if (athlete.reviewReasons.isNotEmpty() || athlete.typicalWeeklyKm == null || athlete.typicalWeeklyKm <= 0.0 ||
                athlete.status !in setOf(AthleteBaselineStatus.PROVISIONAL_AVERAGE, AthleteBaselineStatus.COMPLETE_HISTORY))
                return result(Engine15QualitySelectionDecision.DEFERRED, reason = "ATHLETE_RUNNING_BASELINE_INSUFFICIENT",
                    explanation = "Automatic family selection needs a positive, internally consistent recent-running baseline; no minimum mileage threshold is applied.", athlete = athlete,
                    rules = listOf("BASELINE_EVIDENCE_REVIEWED"))
            family = when (request.goal) {
                "Improve 5K", "Improve 10K" -> if (request.experience == 2)
                    IndependentQualityFamily.THRESHOLD else IndependentQualityFamily.TEMPO
                "Aerobic foundation", "Half marathon", "Marathon" -> IndependentQualityFamily.TEMPO
                else -> return result(Engine15QualitySelectionDecision.UNSUPPORTED, reason = "RUNNING_GOAL_UNSUPPORTED",
                    explanation = "The goal has no family mapping in the bounded selection matrix.", athlete = athlete)
            }
            basis = Engine15QualitySelectionBasis.GOAL_EXPERIENCE_MATRIX
            qualification = Engine15FamilyPolicyQualification.PROVISIONAL_ENGINEERING_RULE
        }

        val familyName = family.name.lowercase().replaceFirstChar(Char::uppercase)
        if (request.allowedQualityTypes.isNotEmpty() &&
            request.allowedQualityTypes.none { it.equals(familyName, ignoreCase = true) })
            return result(Engine15QualitySelectionDecision.DEFERRED, reason = "QUALITY_SELECTED_FAMILY_NOT_ALLOWED",
                explanation = "The selected family is excluded by the request's allowed-family set; no substitute was selected.", athlete = athlete,
                rules = listOf("NO_SILENT_FAMILY_SUBSTITUTION"))

        val baseline = athlete.status.name.lowercase().replace('_', ' ')
        val selectedExposure = exposure.single { it.family == family }
        val explicit = basis == Engine15QualitySelectionBasis.EXPLICIT_USER_PREFERENCE
        return result(
            decision = Engine15QualitySelectionDecision.FAMILY_CANDIDATE,
            qualification = qualification,
            basis = basis,
            reason = null,
            explanation = if (explicit)
                "$familyName is shown because the runner explicitly chose it. This records user direction only; it is not a coach recommendation or workout prescription."
            else "$familyName is a provisional family candidate from the Engine 1.5 goal/experience matrix for '${request.goal}' (experience ${request.experience}). The positive running baseline is ${athlete.typicalWeeklyKm} km/week ($baseline; ${athlete.confidence}); it supplies evidence context, not a mileage cutoff. This engineering rule is unqualified and does not prescribe a workout.",
            family = family,
            candidateSlots = candidateSlots,
            athlete = athlete,
            evidenceIds = selectedExposure.evidenceSessionIds,
            provenance = selectedExposure.provenance.takeIf {
                selectedExposure.confidence == IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED
            },
            exposureConfidence = selectedExposure.confidence,
            repeatedWorkSeconds = selectedExposure.provisionalRepeatedWorkSeconds,
            rules = listOf(
                if (explicit) "EXPLICIT_USER_FAMILY_CHOICE" else "GOAL_EXPERIENCE_MATRIX_PROVISIONAL",
                "PRIOR_QUALITY_EXPOSURE_IS_CONTEXT_NOT_ELIGIBILITY",
                "NO_WEEKLY_MILEAGE_THRESHOLD_APPLIED",
                "QUALITY_EVIDENCE_NOT_SOURCE_VERIFIED",
                "CALENDAR_LOCKS_AND_HARD_SESSION_SPACING_CHECKED",
                "RECOVERY_WEEKS_EXCLUDED",
                "M2_TEMPLATE_NOT_SELECTED"
            ) + selectedExposure.ruleIds,
            qualificationNotes = listOf(
                "No reviewed research record qualifies this goal/experience-to-family mapping; it is a provisional engineering rule.",
                "The experience value is a user-declared category, not an independently measured ability.",
                "M3 exposure is descriptive only because source/session identity and completed step purpose are not source-verified.",
                "The family candidate does not authorize a template, workout structure, work dose, recovery dose, intensity target or execution."
            )
        )
    }

    private fun Engine15M6CExternalSession.asPlannedSession() = PlannedSession(
        id = id,
        date = date,
        discipline = when (discipline) {
            Engine15M6CDiscipline.STRENGTH -> "str"
            Engine15M6CDiscipline.CALISTHENICS -> "cal"
            Engine15M6CDiscipline.CONDITIONING -> "cond"
        },
        title = "Existing session",
        minutes = 0,
        distanceKm = null,
        kind = "external",
        lower = load == Engine15M6CLoad.LOWER_BODY,
        quality = load == Engine15M6CLoad.FULL_BODY_HARD
    )
}
