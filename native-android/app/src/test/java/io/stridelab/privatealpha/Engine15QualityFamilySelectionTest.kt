package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15QualityFamilySelectionTest {
    private val owner = "synthetic-runner"
    private val monday = LocalDate.parse("2026-11-16")
    private val request = PlanRequest(
        goal = "Improve 10K", start = monday, weeks = 4,
        days = setOf(1, 2, 3, 5, 6, 7), runDays = 6, longDay = 6,
        qualityCount = 1, qualityDays = setOf(2), weeklyKm = 45.0,
        longestKm = 12.0, targetWeeklyKm = 50.0,
        policyChoice = "engine15", recentMileageBasis = "SELF_REPORTED_AVERAGE"
    )

    private fun record(
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        ownerId: String = owner,
        day: Int,
        id: String
    ) = IndependentQualitySegmentEvidence(
        ownerId, "source-$id", "session-$id", monday.minusDays(day.toLong()), family,
        IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED, true, listOf(240, 240, 240)
    )

    private fun sessions(weeks: Int = 4, days: Set<Int> = setOf(1, 2, 3, 5, 6, 7)): List<PlannedSession> =
        (1..weeks).flatMap { week -> days.map { day ->
            PlannedSession(
                id = "candidate-w$week-run${if (day == 6) 0 else day}",
                date = monday.plusWeeks((week - 1).toLong()).plusDays((day - 1).toLong()),
                discipline = "run", title = if (day == 6) "Long easy run" else "Easy run",
                minutes = 40, distanceKm = 7.0, kind = if (day == 6) "long" else "easy"
            )
        } }

    private fun select(
        input: PlanRequest = request,
        evidence: List<IndependentQualitySegmentEvidence> = emptyList(),
        scheduled: List<PlannedSession> = sessions(),
        recoveryWeeks: Set<Int> = setOf(4),
        external: List<Engine15M6CExternalSession> = emptyList()
    ) = Engine15QualityFamilySelection.select(
        input, owner, monday.plusDays(1), evidence, Engine15AthleteBaseline.assess(input),
        scheduled, recoveryWeeks, external
    )

    @Test fun goalAndDeclaredExperienceChangeTheProvisionalAutomaticFamily() {
        val beginner5k = select(input = request.copy(goal = "Improve 5K", experience = 0))
        val intermediate5k = select(input = request.copy(goal = "Improve 5K", experience = 1))
        val experienced5k = select(input = request.copy(goal = "Improve 5K", experience = 2))
        val experienced10k = select(input = request.copy(goal = "Improve 10K", experience = 2))
        val experiencedHm = select(input = request.copy(goal = "Half marathon", experience = 2))
        val marathoner = select(input = request.copy(goal = "Marathon", experience = 2))
        val aerobic = select(input = request.copy(goal = "Aerobic foundation", experience = 2))

        assertEquals(Engine15QualitySelectionDecision.FAMILY_CANDIDATE, beginner5k.decision)
        assertEquals(IndependentQualityFamily.TEMPO, beginner5k.selectedFamily)
        assertEquals(IndependentQualityFamily.TEMPO, intermediate5k.selectedFamily)
        assertEquals(IndependentQualityFamily.INTERVALS, experienced5k.selectedFamily)
        assertEquals(IndependentQualityFamily.THRESHOLD, experienced10k.selectedFamily)
        assertEquals(IndependentQualityFamily.THRESHOLD, experiencedHm.selectedFamily)
        assertEquals(IndependentQualityFamily.TEMPO, marathoner.selectedFamily)
        assertEquals(IndependentQualityFamily.TEMPO, aerobic.selectedFamily)
        listOf(beginner5k, intermediate5k, experienced5k, experienced10k, experiencedHm, marathoner, aerobic).forEach {
            assertEquals(Engine15FamilyPolicyQualification.PROVISIONAL_ENGINEERING_RULE, it.familyPolicyQualification)
            assertEquals(Engine15QualitySelectionBasis.GOAL_EXPERIENCE_MATRIX, it.selectionBasis)
            assertTrue(it.qualificationNotes.any { note -> note.contains("No reviewed research record") })
        }
    }

    @Test fun absentHistoryDoesNotBlockAndAssertedExposureDoesNotChooseTheFamily() {
        val noHistory = select(input = request.copy(goal = "Improve 5K", experience = 0))
        val oneRecord = select(
            input = request.copy(goal = "Improve 5K", experience = 0),
            evidence = listOf(record(IndependentQualityFamily.TEMPO, day = 2, id = "single"))
        )
        val bothFamilyExposure = select(evidence = listOf(
            record(day = 9, id = "th-old"), record(day = 2, id = "th-new"),
            record(IndependentQualityFamily.TEMPO, day = 8, id = "tm-old"),
            record(IndependentQualityFamily.TEMPO, day = 1, id = "tm-new")
        ))
        assertEquals(IndependentQualityFamily.TEMPO, noHistory.selectedFamily)
        assertEquals(IndependentQualityFamily.TEMPO, oneRecord.selectedFamily)
        assertEquals(IndependentQualityFamily.TEMPO, bothFamilyExposure.selectedFamily)
        assertEquals(IndependentQualityExposureConfidence.UNAVAILABLE, noHistory.exposureConfidence)
        assertEquals(IndependentQualityExposureConfidence.INSUFFICIENT_MATCHED_DEVICE_RECORDS,
            oneRecord.exposureConfidence)
        assertEquals(IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED,
            bothFamilyExposure.exposureConfidence)
        assertFalse(noHistory.sourceEvidenceVerified)

        val conflictingIdentity = select(evidence = listOf(
            record(IndependentQualityFamily.THRESHOLD, day = 9, id = "same"),
            record(IndependentQualityFamily.TEMPO, day = 2, id = "same").copy(sourceRecordId = "other-source")
        ))
        assertEquals("QUALITY_EXPOSURE_CONFLICT_REVIEW", conflictingIdentity.reasonCode)
    }

    @Test fun explicitUserPreferenceIsNotReportedAsAutonomousCoaching() {
        val result = select(input = request.copy(
            goal = "Improve 5K", experience = 0, preferredQuality = "Threshold", preferredQualityExplicit = true
        ))
        assertEquals(IndependentQualityFamily.THRESHOLD, result.selectedFamily)
        assertEquals(Engine15QualitySelectionBasis.EXPLICIT_USER_PREFERENCE, result.selectionBasis)
        assertEquals(Engine15FamilyPolicyQualification.USER_DIRECTED, result.familyPolicyQualification)
        assertTrue(result.explanation.contains("runner explicitly chose"))
        assertTrue(result.explanation.contains("not a coach recommendation"))
    }

    @Test fun missingOrConflictingBaselineAndRecoveryHoldDeferAutomaticSelection() {
        val noBaseline = select(input = request.copy(
            weeklyKm = 0.0, recentMileageBasis = "UNKNOWN", targetWeeklyKm = 35.0
        ))
        assertEquals("ATHLETE_RUNNING_BASELINE_INSUFFICIENT", noBaseline.reasonCode)

        val conflictingBaseline = select(input = request.copy(
            weeklyKm = 45.0, recentMileageBasis = "UNKNOWN"
        ))
        assertEquals("ATHLETE_RUNNING_BASELINE_INSUFFICIENT", conflictingBaseline.reasonCode)

        val held = select(input = request.copy(runningRecoveryHold = true))
        assertEquals("QUALITY_RECOVERY_REVIEW_REQUIRED", held.reasonCode)
        assertEquals(Engine15QualitySelectionDecision.DEFERRED, held.decision)
    }

    @Test fun lowFrequencyReturningAndAllRecoveryPlansDeferAutomaticSelection() {
        val twoDays = request.copy(runDays = 2, days = setOf(2, 6))
        val lowFrequency = select(input = twoDays, scheduled = sessions(days = setOf(2, 6)))
        assertEquals("RUN_FREQUENCY_POLICY_UNAVAILABLE", lowFrequency.reasonCode)

        val explicitTwoDays = select(
            input = twoDays.copy(preferredQualityExplicit = true, preferredQuality = "Tempo"),
            scheduled = sessions(days = setOf(2, 6))
        )
        assertEquals(Engine15QualitySelectionDecision.FAMILY_CANDIDATE, explicitTwoDays.decision)
        assertEquals(Engine15FamilyPolicyQualification.USER_DIRECTED, explicitTwoDays.familyPolicyQualification)

        val returning = select(input = request.copy(returning = true))
        assertEquals("RETURN_TO_RUNNING_QUALITY_POLICY_UNAVAILABLE", returning.reasonCode)
        val recovery = select(recoveryWeeks = setOf(1, 2, 3, 4))
        assertEquals("NO_LOADING_WEEK_FOR_QUALITY", recovery.reasonCode)
    }

    @Test fun unsupportedPreferenceFrequencyFamilyAndOwnerEvidenceAreExplicit() {
        assertEquals("QUALITY_PREFERRED_FAMILY_UNSUPPORTED",
            select(input = request.copy(preferredQualityExplicit = true, preferredQuality = "Track sprints")).reasonCode)
        assertEquals(Engine15QualitySelectionDecision.UNSUPPORTED, select(input = request.copy(qualityCount = 2)).decision)
        assertEquals(Engine15QualitySelectionDecision.UNSUPPORTED,
            select(input = request.copy(allowedQualityTypes = setOf("Track sprints"))).decision)
        assertEquals(IndependentQualityFamily.FARTLEK,
            select(input = request.copy(preferredQualityExplicit = true, preferredQuality = "Fartlek")).selectedFamily)
        assertEquals(IndependentQualityFamily.HILLS,
            select(input = request.copy(preferredQualityExplicit = true, preferredQuality = "Hills")).selectedFamily)
        assertEquals(IndependentQualityFamily.PROGRESSION,
            select(input = request.copy(preferredQualityExplicit = true, preferredQuality = "Progression")).selectedFamily)
        assertEquals(IndependentQualityFamily.INTERVALS,
            select(input = request.copy(goal = "Improve 5K", experience = 2, allowedQualityTypes = setOf("Intervals"))).selectedFamily)
        assertEquals("QUALITY_SELECTED_FAMILY_NOT_ALLOWED",
            select(input = request.copy(goal = "Improve 10K", experience = 2, allowedQualityTypes = setOf("Intervals"))).reasonCode)
        assertEquals(IndependentQualityFamily.INTERVALS,
            select(input = request.copy(preferredQualityExplicit = true, preferredQuality = "Intervals")).selectedFamily)
        val crossOwner = select(evidence = listOf(record(day = 2, id = "outside").copy(ownerId = "other")))
        assertEquals("QUALITY_EVIDENCE_OWNER_CONFLICT", crossOwner.reasonCode)
    }

    @Test fun lockedOrInterferingWeekdayDefersWithoutFamilySubstitution() {
        val locked = request.copy(lockedSessionDays = mapOf(
            "candidate-w1-run2" to monday.plusDays(1).toString()
        ))
        assertEquals("QUALITY_SLOT_UNAVAILABLE", select(input = locked).reasonCode)
        val hard = Engine15M6CExternalSession(
            "external-lower", monday.plusDays(1), Engine15M6CDiscipline.STRENGTH,
            Engine15M6CLoad.LOWER_BODY
        )
        assertEquals("QUALITY_SLOT_UNAVAILABLE", select(external = listOf(hard)).reasonCode)
        assertEquals("QUALITY_SELECTED_FAMILY_NOT_ALLOWED", select(input = request.copy(
            goal = "Improve 5K", experience = 0, allowedQualityTypes = setOf("Threshold")
        )).reasonCode)
    }

    @Test fun everyLoadingWeekNeedsAFeasibleSlotButRecoveryWeeksAreExcluded() {
        val result = select(scheduled = sessions(weeks = 3), recoveryWeeks = setOf(4))
        assertEquals(Engine15QualitySelectionDecision.FAMILY_CANDIDATE, result.decision)
        assertTrue(result.ruleIds.contains("RECOVERY_WEEKS_EXCLUDED"))
        assertEquals(Engine15FamilyPolicyQualification.NOT_ASSESSED,
            select(input = request.copy(qualityCount = 0)).familyPolicyQualification)
    }

    @Test fun outputIsDeterministicVersionedAndNeverAuthorizesTemplatesOrExecution() {
        val first = select()
        val second = select()
        assertEquals("stridelab.engine15.quality-family-selection-v3", first.policyId)
        assertEquals(first, second)
        assertEquals(Engine15TemplateAuthorization.NOT_AUTHORIZED, first.templateAuthorization)
        assertEquals(Engine15WorkoutExecutability.NON_EXECUTABLE, first.workoutExecutability)
        assertFalse(first.sourceEvidenceVerified)
        assertNull(first.templateId)
    }
}
