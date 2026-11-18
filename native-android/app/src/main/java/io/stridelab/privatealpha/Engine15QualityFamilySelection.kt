package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal enum class Engine15QualitySelectionStatus {
    QUALIFIED_SELECTION, PROVISIONAL_CANDIDATE, DEFERRED, UNSUPPORTED
}

internal data class Engine15QualityFamilySelectionResult(
    val policyId: String,
    val status: Engine15QualitySelectionStatus,
    val selectedFamily: IndependentQualityFamily? = null,
    val evidenceProvenance: String? = null,
    val exposureConfidence: IndependentQualityExposureConfidence? = null,
    val assertedRepeatedWorkSeconds: Int? = null,
    val reasonCode: String? = null,
    val explanation: String,
    val evidenceSessionIds: List<String> = emptyList(),
    val ruleIds: List<String>,
    val qualificationNotes: List<String>,
    val sourceEvidenceVerified: Boolean = false,
    val authorizedForTemplateSelection: Boolean = false,
    val executable: Boolean = false,
    val templateId: String? = null
) {
    init {
        require(policyId == Engine15QualityFamilySelection.POLICY_ID)
        require(evidenceSessionIds.distinct() == evidenceSessionIds)
        require(ruleIds.distinct() == ruleIds && qualificationNotes.isNotEmpty())
        require(!sourceEvidenceVerified && !authorizedForTemplateSelection && !executable)
        require(templateId == null)
        require(assertedRepeatedWorkSeconds == null || assertedRepeatedWorkSeconds in 1..14400)
        require((status == Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE) ==
            (selectedFamily != null && reasonCode == null))
    }
}

/** Selects a family for review only; it deliberately does not construct a workout. */
internal object Engine15QualityFamilySelection {
    const val POLICY_ID = "stridelab.engine15.quality-family-selection-v1"

    private fun result(
        status: Engine15QualitySelectionStatus,
        reason: String?,
        explanation: String,
        family: IndependentQualityFamily? = null,
        evidenceIds: List<String> = emptyList(),
        rules: List<String> = emptyList(),
        provenance: String? = null,
        confidence: IndependentQualityExposureConfidence? = null,
        repeatedWorkSeconds: Int? = null
    ) = Engine15QualityFamilySelectionResult(
        policyId = POLICY_ID,
        status = status,
        selectedFamily = family,
        evidenceProvenance = provenance,
        exposureConfidence = confidence,
        assertedRepeatedWorkSeconds = repeatedWorkSeconds,
        reasonCode = reason,
        explanation = explanation,
        evidenceSessionIds = evidenceIds,
        ruleIds = (listOf("NO_SELECTION_AUTHORIZATION") + rules).distinct(),
        qualificationNotes = listOf(
            "This family decision is provisional and is not a coach-qualified prescription.",
            "Quality exposure provenance remains caller-asserted and is not verified.",
            "No M2 template, work dose, recovery dose, pace, heart-rate or effort target was selected."
        )
    )

    fun select(
        request: PlanRequest,
        ownerId: String,
        asOf: LocalDate,
        evidence: List<IndependentQualitySegmentEvidence>,
        scheduledSessions: List<PlannedSession>,
        recoveryWeeks: Set<Int>,
        externalSessions: List<Engine15M6CExternalSession>
    ): Engine15QualityFamilySelectionResult {
        if (request.qualityCount == 0)
            return result(Engine15QualitySelectionStatus.DEFERRED, "NO_QUALITY_REQUESTED",
                "No coach-generated quality workout was requested.")
        if (request.qualityCount != 1)
            return result(Engine15QualitySelectionStatus.UNSUPPORTED, "QUALITY_FREQUENCY_UNSUPPORTED",
                "This selector has no reviewed policy for choosing more than one quality family per loading week.")
        if (request.runDays !in 2..6 || request.weeks !in 1..16)
            return result(Engine15QualitySelectionStatus.UNSUPPORTED, "RUNNING_PLAN_BOUNDS_UNSUPPORTED",
                "The selector supports two to six running days and one to sixteen weeks.")
        if (request.goal !in PlanDisciplineContract.goals.getValue("run"))
            return result(Engine15QualitySelectionStatus.UNSUPPORTED, "RUNNING_GOAL_UNSUPPORTED",
                "The requested running goal is outside the current Engine 1.5 goal contract.")
        val supportedFamilyNames = IndependentQualityFamily.entries.map {
            it.name.lowercase().replaceFirstChar { first -> first.uppercase() }
        }.toSet()
        if (request.allowedQualityTypes.isNotEmpty() &&
            request.allowedQualityTypes.none { allowed -> supportedFamilyNames.any { it.equals(allowed, true) } })
            return result(Engine15QualitySelectionStatus.UNSUPPORTED, "QUALITY_FAMILY_UNSUPPORTED",
                "The allowed quality families do not include a family with a current Engine 1.5 structure contract.")
        if (request.runningRecoveryHold)
            return result(Engine15QualitySelectionStatus.DEFERRED, "QUALITY_RECOVERY_REVIEW_REQUIRED",
                "An active running recovery hold requires a separate quality eligibility review.")
        if (ownerId.isBlank() || ownerId.length > 256)
            return result(Engine15QualitySelectionStatus.DEFERRED, "QUALITY_EVIDENCE_OWNER_INVALID",
                "A valid local owner scope is required before training history can be compared.")
        if (evidence.any { it.ownerId != ownerId })
            return result(Engine15QualitySelectionStatus.DEFERRED, "QUALITY_EVIDENCE_OWNER_CONFLICT",
                "Quality evidence from another owner scope cannot be considered.")

        val loadingWeeks = (1..request.weeks).filterNot { it in recoveryWeeks }
        for (week in loadingWeeks) {
            val weekSessions = scheduledSessions.filter {
                ChronoUnit.DAYS.between(request.start, it.date) / 7L == week - 1L
            }
            val candidates = weekSessions.filter { session ->
                session.discipline == "run" && session.kind == "easy" &&
                    (request.qualityDays.isEmpty() || session.date.dayOfWeek.value in request.qualityDays) &&
                    session.id !in request.lockedSessionDays &&
                    externalSessions.none { external ->
                        val other = external.asPlannedSession()
                        Engine15IndependentPlanGenerator.conflict(
                            session.copy(kind = "quality", quality = true, runningPrescription = null), other
                        )
                    } && weekSessions.none { other ->
                        other.id != session.id && Engine15IndependentPlanGenerator.conflict(
                            session.copy(kind = "quality", quality = true, runningPrescription = null), other
                        )
                    }
            }
            if (candidates.isEmpty())
                return result(Engine15QualitySelectionStatus.DEFERRED, "QUALITY_SLOT_UNAVAILABLE",
                    "No unlocked, available easy-run slot in loading week $week satisfies the selected quality day and existing session-spacing checks.",
                    rules = listOf("CALENDAR_AND_LOCKS_CHECKED", "KNOWN_HARD_SESSION_SPACING_CHECKED"))
        }

        val families = IndependentQualityFamily.entries.map { family ->
            IndependentQualityExposure.assess(evidence, ownerId, family, asOf)
        }.filter { assessment ->
            assessment.provisionalRepeatedWorkSeconds != null &&
                (request.allowedQualityTypes.isEmpty() || request.allowedQualityTypes.any {
                    it.equals(assessment.family.name.lowercase().replaceFirstChar(Char::uppercase), ignoreCase = true)
                })
        }
        if (families.isEmpty())
            return result(Engine15QualitySelectionStatus.DEFERRED, "QUALITY_FAMILY_EVIDENCE_MISSING",
                "No supported family has two recent, distinct, caller-asserted work-segment records. Missing or self-reported history cannot establish an automatic family choice.",
                rules = listOf("M3_REPEATED_FAMILY_EXPOSURE_REQUIRED", "SOURCE_EVIDENCE_NOT_VERIFIED"))
        if (families.size != 1)
            return result(Engine15QualitySelectionStatus.DEFERRED, "QUALITY_FAMILY_AMBIGUOUS",
                "Recent asserted exposure supports more than one family, and Engine 1.5 has no qualified goal-to-family rule to resolve the choice.",
                rules = listOf("NO_GOAL_TO_FAMILY_POLICY", "FAMILY_EVIDENCE_AMBIGUOUS"))

        val selected = families.single()
        return result(
            status = Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE,
            reason = null,
            explanation = "${selected.family.name.lowercase().replaceFirstChar(Char::uppercase)} is a provisional family candidate because two recent same-family segment records and a schedulable weekday are present. The evidence source and session matching remain caller assertions; the selection is not authorized for template use or execution.",
            family = selected.family,
            evidenceIds = selected.evidenceSessionIds,
            provenance = selected.provenance,
            confidence = selected.confidence,
            repeatedWorkSeconds = selected.provisionalRepeatedWorkSeconds,
            rules = listOf(
                "M3_REPEATED_FAMILY_EXPOSURE_REQUIRED",
                "CALLER_ASSERTED_EXPOSURE_ONLY",
                "CALENDAR_AND_LOCKS_CHECKED",
                "KNOWN_HARD_SESSION_SPACING_CHECKED",
                "RECOVERY_WEEKS_EXCLUDED",
                "M2_TEMPLATE_NOT_SELECTED"
            ) + selected.ruleIds
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
