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

    private fun sessions(weeks: Int = 4): List<PlannedSession> = (1..weeks).flatMap { week ->
        setOf(1, 2, 3, 5, 6, 7).map { day ->
            PlannedSession(
                id = "candidate-w$week-run${if (day == 6) 0 else day}",
                date = monday.plusWeeks((week - 1).toLong()).plusDays((day - 1).toLong()),
                discipline = "run", title = if (day == 6) "Long easy run" else "Easy run",
                minutes = 40, distanceKm = 7.0, kind = if (day == 6) "long" else "easy"
            )
        }
    }

    private fun select(
        input: PlanRequest = request,
        evidence: List<IndependentQualitySegmentEvidence> = listOf(
            record(day = 9, id = "older"), record(day = 2, id = "recent")
        ),
        scheduled: List<PlannedSession> = sessions(),
        recoveryWeeks: Set<Int> = setOf(4),
        external: List<Engine15M6CExternalSession> = emptyList()
    ) = Engine15QualityFamilySelection.select(
        input, owner, monday.plusDays(1), evidence, scheduled, recoveryWeeks, external
    )

    @Test fun repeatedSameFamilyEvidenceProducesOnlyAProvisionalNonExecutableCandidate() {
        val result = select()
        assertEquals(Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE, result.status)
        assertEquals(IndependentQualityFamily.THRESHOLD, result.selectedFamily)
        assertEquals(listOf("session-recent", "session-older"), result.evidenceSessionIds)
        assertEquals("CALLER_ASSERTED_DEVICE_SEGMENTS", result.evidenceProvenance)
        assertEquals(IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED,
            result.exposureConfidence)
        assertEquals(720, result.assertedRepeatedWorkSeconds)
        assertFalse(result.sourceEvidenceVerified)
        assertFalse(result.authorizedForTemplateSelection)
        assertFalse(result.executable)
        assertNull(result.templateId)
        assertTrue(result.ruleIds.contains("ANY_REPORTED_EXPOSURE_NOT_SOURCE_VERIFIED"))
    }

    @Test fun noExposureDoesNotBlockAReviewOnlyRequestPreferredFamilyCandidate() {
        val missing = select(
            input = request.copy(experience = 0, weeklyKm = 0.0), evidence = emptyList()
        )
        assertEquals(Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE, missing.status)
        assertEquals(IndependentQualityFamily.THRESHOLD, missing.selectedFamily)
        assertEquals(IndependentQualityExposureConfidence.UNAVAILABLE, missing.exposureConfidence)
        assertTrue(missing.evidenceSessionIds.isEmpty())
        assertNull(missing.assertedRepeatedWorkSeconds)
        assertTrue(missing.ruleIds.contains("REQUEST_PREFERRED_FAMILY_REVIEW_ONLY"))
        assertTrue(missing.qualificationNotes.any { it.contains("no reviewed threshold") })
        assertFalse(missing.executable)

        val twoFamilies = select(evidence = listOf(
            record(day = 9, id = "th-old"), record(day = 2, id = "th-new"),
            record(IndependentQualityFamily.TEMPO, day = 8, id = "tm-old"),
            record(IndependentQualityFamily.TEMPO, day = 1, id = "tm-new")
        ))
        assertEquals(Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE, twoFamilies.status)
        assertEquals(IndependentQualityFamily.THRESHOLD, twoFamilies.selectedFamily)

        val novice = select(input = request.copy(experience = 0), evidence = listOf(
            record(day = 2, id = "single-recent")
        ))
        assertEquals(Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE, novice.status)
        assertEquals(IndependentQualityExposureConfidence.INSUFFICIENT_MATCHED_DEVICE_RECORDS,
            novice.exposureConfidence)

        val selfReportedOnly = select(evidence = listOf(
            record(day = 9, id = "self-old").copy(
                origin = IndependentQualityEvidenceOrigin.SELF_REPORTED_SEGMENTS
            ),
            record(day = 2, id = "self-new").copy(
                origin = IndependentQualityEvidenceOrigin.SELF_REPORTED_SEGMENTS
            )
        ))
        assertEquals(Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE, selfReportedOnly.status)
        assertEquals(IndependentQualityExposureConfidence.SELF_REPORTED_ONLY,
            selfReportedOnly.exposureConfidence)
        assertNull(selfReportedOnly.assertedRepeatedWorkSeconds)
    }

    @Test fun conflictingExposureDefersButLowFrequencyCanUseItsOnlyAvailableQualitySlot() {
        val conflicting = listOf(
            record(day = 9, id = "same"),
            record(IndependentQualityFamily.TEMPO, day = 2, id = "same")
                .copy(sourceRecordId = "source-conflicting")
        )
        val conflictResult = select(evidence = conflicting)
        assertEquals(Engine15QualitySelectionStatus.DEFERRED, conflictResult.status)
        assertEquals("QUALITY_EXPOSURE_CONFLICT_REVIEW", conflictResult.reasonCode)

        val lowFrequency = select(
            input = request.copy(runDays = 2, days = setOf(2, 6), qualityDays = setOf(2)),
            evidence = emptyList(),
            scheduled = (1..4).flatMap { week -> listOf(2, 6).map { day ->
                PlannedSession(
                    id = "candidate-w$week-run${if (day == 6) 0 else day}",
                    date = monday.plusWeeks((week - 1).toLong()).plusDays((day - 1).toLong()),
                    discipline = "run", title = if (day == 6) "Long easy run" else "Easy run",
                    minutes = 40, distanceKm = 7.0, kind = if (day == 6) "long" else "easy"
                )
            } }
        )
        assertEquals(Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE, lowFrequency.status)
        assertEquals(IndependentQualityFamily.THRESHOLD, lowFrequency.selectedFamily)
    }

    @Test fun unsupportedOrExcludedPreferredFamilyIsNotSilentlySubstituted() {
        val unsupported = select(input = request.copy(preferredQuality = "Fartlek"))
        assertEquals(Engine15QualitySelectionStatus.UNSUPPORTED, unsupported.status)
        assertEquals("QUALITY_PREFERRED_FAMILY_UNSUPPORTED", unsupported.reasonCode)

        val excluded = select(input = request.copy(
            preferredQuality = "Threshold", allowedQualityTypes = setOf("Tempo")
        ))
        assertEquals(Engine15QualitySelectionStatus.DEFERRED, excluded.status)
        assertEquals("QUALITY_PREFERRED_FAMILY_NOT_ALLOWED", excluded.reasonCode)
    }

    @Test fun unsupportedFrequencyAndConflictingOwnerEvidenceAreExplicit() {
        assertEquals(Engine15QualitySelectionStatus.UNSUPPORTED,
            select(input = request.copy(qualityCount = 2)).status)
        assertEquals(Engine15QualitySelectionStatus.UNSUPPORTED,
            select(input = request.copy(allowedQualityTypes = setOf("Intervals"))).status)
        val crossOwner = select(evidence = listOf(
            record(day = 9, id = "inside"), record(day = 2, id = "outside").copy(ownerId = "other")
        ))
        assertEquals(Engine15QualitySelectionStatus.DEFERRED, crossOwner.status)
        assertEquals("QUALITY_EVIDENCE_OWNER_CONFLICT", crossOwner.reasonCode)
    }

    @Test fun everyLoadingWeekNeedsAnAvailableUnlockedNonConflictingQualityDay() {
        val onlyLocked = request.copy(lockedSessionDays = mapOf(
            "candidate-w1-run2" to monday.plusDays(1).toString()
        ))
        val lockResult = select(input = onlyLocked)
        assertEquals(Engine15QualitySelectionStatus.DEFERRED, lockResult.status)
        assertEquals("QUALITY_SLOT_UNAVAILABLE", lockResult.reasonCode)

        val hard = Engine15M6CExternalSession(
            "external-lower", monday.plusDays(1), Engine15M6CDiscipline.STRENGTH,
            Engine15M6CLoad.LOWER_BODY
        )
        val spacing = select(external = listOf(hard))
        assertEquals(Engine15QualitySelectionStatus.DEFERRED, spacing.status)
        assertEquals("QUALITY_SLOT_UNAVAILABLE", spacing.reasonCode)
    }

    @Test fun scheduledRecoveryWeeksAreNotRequiredToContainQualitySlots() {
        val firstThreeWeeks = sessions(weeks = 3)
        val result = select(scheduled = firstThreeWeeks, recoveryWeeks = setOf(4))
        assertEquals(Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE, result.status)
        assertTrue(result.ruleIds.contains("RECOVERY_WEEKS_EXCLUDED"))
    }

    @Test fun selectionIsDeterministicAndVersioned() {
        val first = select()
        val second = select()
        assertEquals(Engine15QualityFamilySelection.POLICY_ID, first.policyId)
        assertEquals(first, second)
        assertTrue(first.qualificationNotes.any { it.contains("not source-verified") })
    }
}
