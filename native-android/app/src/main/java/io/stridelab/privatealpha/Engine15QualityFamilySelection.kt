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
    const val POLICY_ID = "stridelab.engine15.quality-family-selection-v2"

    private fun result(
        status: Engine15QualitySelectionStatus,
        reason: String?,
        explanation: String,
        family: IndependentQualityFamily? = null,
        evidenceIds: List<String> = emptyList(),
        rules: List<String> = emptyList(),
        provenance: String? = null,
        confidence: IndependentQualityExposureConfidence? = null,
        repeatedWorkSeconds: Int? = null,
        qualificationNotes: List<String> = emptyList()
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
            "Any supplied quality-exposure records remain caller-asserted and are not source-verified.",
            "No M2 template, work dose, recovery dose, pace, heart-rate or effort target was selected."
        ) + qualificationNotes
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

        val preferred = IndependentQualityFamily.entries.singleOrNull { family ->
            family.name.equals(request.preferredQuality, ignoreCase = true)
        } ?: return result(Engine15QualitySelectionStatus.UNSUPPORTED,
            "QUALITY_PREFERRED_FAMILY_UNSUPPORTED",
            "The preferred family has no supported Engine 1.5 family contract; no substitute was selected.")
        val preferredName = preferred.name.lowercase().replaceFirstChar(Char::uppercase)
        if (request.allowedQualityTypes.isNotEmpty() &&
            request.allowedQualityTypes.none { it.equals(preferredName, ignoreCase = true) })
            return result(Engine15QualitySelectionStatus.DEFERRED,
                "QUALITY_PREFERRED_FAMILY_NOT_ALLOWED",
                "The preferred family is excluded by the request's allowed-family set; no substitute was selected.")

        val exposure = IndependentQualityFamily.entries.map { family ->
            IndependentQualityExposure.assess(evidence, ownerId, family, asOf)
        }
        if (exposure.any { it.confidence == IndependentQualityExposureConfidence.CONFLICT_REVIEW })
            return result(Engine15QualitySelectionStatus.DEFERRED,
                "QUALITY_EXPOSURE_CONFLICT_REVIEW",
                "Recent quality records reuse a source or session identity inconsistently; resolve the evidence conflict before family review.",
                rules = listOf("M3_EXPOSURE_CONFLICT_REQUIRES_REVIEW"))

        val selected = exposure.single { it.family == preferred }
        return result(
            status = Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE,
            reason = null,
            explanation = "$preferredName is a provisional family candidate because it is the preferred supported family in the request and a schedulable weekday is available. Training background and weekly volume remain context, not validated eligibility thresholds; prior same-family exposure is reported separately and is not required. This is not a coach-qualified choice and is not authorized for template use or execution.",
            family = preferred,
            evidenceIds = selected.evidenceSessionIds,
            provenance = selected.provenance.takeIf {
                selected.confidence == IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED
            },
            confidence = selected.confidence,
            repeatedWorkSeconds = selected.provisionalRepeatedWorkSeconds,
            rules = listOf(
                "REQUEST_PREFERRED_FAMILY_REVIEW_ONLY",
                "PRIOR_QUALITY_EXPOSURE_IS_CONTEXT_NOT_ELIGIBILITY",
                "NO_ATHLETE_CAPACITY_THRESHOLD_APPLIED",
                "ANY_REPORTED_EXPOSURE_NOT_SOURCE_VERIFIED",
                "CALENDAR_AND_LOCKS_CHECKED",
                "KNOWN_HARD_SESSION_SPACING_CHECKED",
                "RECOVERY_WEEKS_EXCLUDED",
                "M2_TEMPLATE_NOT_SELECTED"
            ) + selected.ruleIds,
            qualificationNotes = listOf(
                "Family choice follows PlanRequest.preferredQuality; this request-constrained engineering policy is not a research-supported goal-to-family rule.",
                "Goal '${request.goal}', experience code ${request.experience}, and declared recent running volume ${request.weeklyKm} km are retained as context; no reviewed threshold uses them to qualify a family.",
                "Prior same-family exposure is descriptive context only and is not a family-eligibility requirement."
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
