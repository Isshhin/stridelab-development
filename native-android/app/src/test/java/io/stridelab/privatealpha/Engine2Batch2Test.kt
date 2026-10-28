package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine2Batch2Test {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val monday = LocalDate.parse("2026-11-02")
    private fun source(id: String = "s") = Engine2Source("owner", "synthetic-manual", id,
        now.minusSeconds(3600), now.minusSeconds(60))
    private fun run(status: Engine2RecordStatus = Engine2RecordStatus.CONFIRMED) =
        Engine2WorkoutEvidence("run-1", Engine2Discipline.RUNNING,
            now.minusSeconds(3600), status, listOf(
                Engine2Measurement(Engine2Metric.DISTANCE_KM, 7.0, source())))
    private fun context(workouts: List<Engine2WorkoutEvidence> = listOf(run())) =
        Engine2AthleteContext("owner", now, workouts, emptyList())
    private fun plan() = Engine2PlanView("owner", "plan", 2, Engine2PlanStatus.APPROVED,
        listOf(Engine2SessionTarget("easy", monday, Engine2Discipline.RUNNING, 50)))

    @Test fun explicitAthleteRequestProducesReviewableOnlyProposal() {
        val p = plan()
        val request = Engine2AthleteRequest("owner", "easy",
            "Request a lighter session while returning from a break.", now)
        val proposed = Engine2ConservativeAdaptation.proposeLighter(p, context(),
            request, "proposal-1")
        assertEquals(Engine2ProposalStatus.PENDING, proposed.status)
        assertEquals(37, proposed.suggestedMinutes)
        assertEquals(50, proposed.originalMinutes)
        assertEquals(setOf("run-1"), proposed.evidenceRecordIds)
        assertEquals(50, p.sessions.single().minutes)
        assertTrue(Engine2ReviewPreview.build(p, proposed).eligibleForApproval)
    }

    @Test fun missingDraftOrLateEvidenceDoesNotAuthorizeCoachRecommendation() {
        val r = Engine2AthleteRequest("owner", "easy", "Please make the session shorter.", now)
        for (missing in listOf(emptyList(), listOf(run(Engine2RecordStatus.DRAFT)))) {
            assertThrows(IllegalArgumentException::class.java) {
                Engine2ConservativeAdaptation.proposeLighter(
                    plan(), context(missing), r, "bad")
            }
        }
        val late = run().copy(measurements = listOf(Engine2Measurement(
            Engine2Metric.DISTANCE_KM, 7.0,
            source().copy(receivedAt = now.plusSeconds(1)))))
        assertThrows(IllegalArgumentException::class.java) {
            context(listOf(late))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ConservativeAdaptation.proposeLighter(
                plan(), context(), r.copy(ownerId = "other"), "bad")
        }
    }

    @Test fun exerciseSelectionHonorsEquipmentAndConfirmedProgressions() {
        val bare = Engine2ExerciseSelection.available(
            Engine2ExerciseDiscipline.STRENGTH, Engine2Movement.SQUAT, emptySet())
        assertEquals(listOf("rl"), bare.map { it.catalogId })
        val dumbbells = Engine2ExerciseSelection.available(
            Engine2ExerciseDiscipline.STRENGTH, Engine2Movement.SQUAT,
            setOf(Engine2Equipment.DUMBBELLS))
        assertEquals(setOf("rl", "gs"), dumbbells.map { it.catalogId }.toSet())
        val noPromotion = Engine2ExerciseSelection.available(
            Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.CORE,
            setOf(Engine2Equipment.MAT), maximumSkill = Engine2Skill.DEVELOPING)
        assertFalse(noPromotion.any { it.catalogId == "core-hollow" })
        val confirmed = Engine2ExerciseSelection.available(
            Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.CORE,
            setOf(Engine2Equipment.MAT), setOf("core-tuck"), Engine2Skill.DEVELOPING)
        assertTrue(confirmed.any { it.catalogId == "core-hollow" })
    }

    @Test fun providerNormalizationKeepsMissingMeasurementsAbsentAndConvertsMeters() {
        val page = Engine2ProviderPage("synthetic-provider",
            listOf(Engine2ProviderActivity("123", Engine2Discipline.RUNNING,
                now.minusSeconds(3600), 10250.0, 3600.0, null)), null, now)
        val rows = Engine2ProviderNormalizer.normalize("owner", page, now)
        assertEquals(1, rows.size)
        assertEquals("source:synthetic-provider:123", rows.single().id)
        assertEquals(2, rows.single().measurements.size)
        assertEquals(10.25, rows.single().measurements.first { it.metric ==
            Engine2Metric.DISTANCE_KM }.value, 0.0)
        val result = Engine2ContextAssembler.build("owner", now, rows, emptyList())
        assertTrue(result.rejected.isEmpty())
        assertEquals(1, result.context.runningCoverage().confirmedRuns)
        assertEquals(10.25, result.context.runningCoverage().knownDistanceKm!!, 0.0)
    }

    @Test fun providerRejectsInvalidUnitsAndFutureOrUntrustedTiming() {
        val later = now.plusSeconds(60)
        val bad = Engine2ProviderPage("synthetic-provider", listOf(
            Engine2ProviderActivity("123", Engine2Discipline.RUNNING,
                now.minusSeconds(600), -10.0)), null, now)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProviderNormalizer.normalize("owner", bad, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProviderNormalizer.normalize("owner", bad.copy(fetchedAt = later), now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProviderNormalizer.normalize("owner", bad.copy(activities = listOf(
                Engine2ProviderActivity("123", Engine2Discipline.RUNNING, later)
            )), now)
        }
    }

    @Test fun datasetLikeUncertaintyCasesMustYieldConservativeOutcomes() {
        val plan = plan()
        val sourceOnly = Engine2AthleteContext("owner", now,
            listOf(Engine2WorkoutEvidence("no-gps", Engine2Discipline.RUNNING,
                now.minusSeconds(200), Engine2RecordStatus.CONFIRMED)), emptyList())
        assertFalse(sourceOnly.runningCoverage().complete)
        assertEquals(3, Engine2WellnessAudit.inspect(sourceOnly).missingMetrics.size)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ConservativeAdaptation.proposeLighter(plan, sourceOnly,
                Engine2AthleteRequest("owner", "easy", "Need to reduce this session.", now),
                "unsupported")
        }
        assertEquals(50, plan.sessions.single().minutes)
    }
}
