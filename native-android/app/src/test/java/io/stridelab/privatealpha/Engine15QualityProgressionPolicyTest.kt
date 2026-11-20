package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15QualityProgressionPolicyTest {
    private val owner = "progression-test-runner"
    private val now = Instant.parse("2026-11-17T00:00:00Z")
    private val monday = LocalDate.parse("2026-11-16")

    private fun baseRequest(
        goal: String = "Improve 10K",
        experience: Int = 2,
        weeks: Int = 12
    ) = PlanRequest(
        goal = goal,
        experience = experience,
        start = monday,
        weeks = weeks,
        days = setOf(1, 2, 3, 5, 6, 7),
        runDays = 6,
        longDay = 6,
        qualityCount = 1,
        qualityDays = setOf(2),
        weeklyKm = 40.0,
        longestKm = 14.0,
        targetWeeklyKm = 40.0,
        timeBudgetEnabled = false,
        policyChoice = "engine15",
        recentMileageBasis = "SELF_REPORTED_AVERAGE"
    )

    private fun repeatedEvidence(
        family: IndependentQualityFamily,
        workSegments: List<Int>
    ): List<IndependentQualitySegmentEvidence> = listOf(
        IndependentQualitySegmentEvidence(
            ownerId = owner,
            sourceRecordId = "src-${family.name.lowercase()}-1",
            sessionId = "sess-${family.name.lowercase()}-1",
            performedOn = monday.minusDays(10),
            family = family,
            origin = IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
            sourceSessionMatchAsserted = true,
            performedWorkSegmentsSeconds = workSegments
        ),
        IndependentQualitySegmentEvidence(
            ownerId = owner,
            sourceRecordId = "src-${family.name.lowercase()}-2",
            sessionId = "sess-${family.name.lowercase()}-2",
            performedOn = monday.minusDays(3),
            family = family,
            origin = IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
            sourceSessionMatchAsserted = true,
            performedWorkSegmentsSeconds = workSegments
        )
    )

    private fun claimsForDryRun(
        dryRun: Engine15QualityWorkoutDryRun
    ): List<Engine15M5SegmentDistanceClaim> = dryRun.alternatives.map { alt ->
        val workout = alt.prescription!!.step.workout
        val phases = mutableListOf<WorkoutStep>()
        for (el in workout.elements) when (el) {
            is IndependentWorkoutElement.Step -> repeat(el.value.repetitions) { phases += el.value }
            is IndependentWorkoutElement.Intervals -> repeat(el.value.repetitions) { i ->
                phases += el.value.work
                if (i < el.value.repetitions - 1) phases += el.value.recovery
            }
        }
        val totalMeters = kotlin.math.round(alt.originalEasyDistanceKm * 1000.0).toInt()
        // Weight work steps slightly faster than easy steps so implied pace is plausible.
        val weights = phases.map { step ->
            val speedFactor = if (step.purpose == WorkoutPurpose.WORK) 1.25 else 1.0
            step.amount * speedFactor
        }
        val sumWeights = weights.sum()
        val raw = weights.dropLast(1).map { kotlin.math.floor(it / sumWeights * totalMeters) }
        val last = totalMeters - raw.sum()
        Engine15M5SegmentDistanceClaim(
            weekNumber = alt.weekNumber,
            slotNumber = alt.slotNumber,
            assertedMetersByTimedSegment = raw + last,
            externalReviewReference = null,
            provenance = Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT
        )
    }

    @Test fun absentExposureHoldsIntroDoseAcrossSixteenWeeksWithoutEscalation() {
        val input = baseRequest(goal = "Marathon", experience = 2, weeks = 16)
        val result = Engine15IndependentPlanGenerator.preview(input, owner, "prog-hold", now)
            as Engine15IndependentPlanDecision.Deferred
        val dryRun = result.qualityDryRun!!

        assertEquals(12, dryRun.alternatives.size)
        assertTrue(dryRun.alternatives.all {
            it.progressionBasis == Engine15QualityProgressionBasis.HOLD_NO_REPEATED_EXPOSURE &&
                it.workSeconds == 600
        })
    }

    @Test fun repeatedThresholdExposureProgressesBoundedlyAndTapersOnFinalRaceWeek() {
        val input = baseRequest(goal = "Improve 10K", experience = 2, weeks = 12)
        // Asserted repeated exposure of 12 min (3x4 min = 720 s); max permitted is 720 + 240 = 960 s.
        val evidence = repeatedEvidence(IndependentQualityFamily.THRESHOLD, listOf(240, 240, 240))
        val result = Engine15IndependentPlanGenerator.preview(
            input, owner, "prog-threshold", now, qualityEvidence = evidence
        ) as Engine15IndependentPlanDecision.Deferred
        val dryRun = result.qualityDryRun!!

        assertEquals(IndependentQualityFamily.THRESHOLD, dryRun.family)
        assertEquals(listOf(1, 2, 3, 5, 6, 7, 9, 10, 11), dryRun.alternatives.map { it.weekNumber })
        val workByWeek = dryRun.alternatives.associate { it.weekNumber to it.workSeconds!! }
        // Starts at 720s (3x4m), progresses up to at most 960s (4x4m = 720 + 240s cap), never exceeds cap.
        assertEquals(720, workByWeek[1])
        assertTrue(workByWeek.values.max() > 720)
        assertTrue(workByWeek.values.all { it <= 720 + 240 })
        // Week 11 (final loading week before 10K) tapers back with RACE_TAPER_REDUCTION.
        val finalAlt = dryRun.alternatives.last()
        assertEquals(11, finalAlt.weekNumber)
        assertEquals(Engine15QualityProgressionBasis.RACE_TAPER_REDUCTION, finalAlt.progressionBasis)
        assertEquals(720, finalAlt.workSeconds)
    }

    @Test fun aerobicFoundationDoesNotApplyRaceTaperReduction() {
        val input = baseRequest(goal = "Aerobic foundation", experience = 2, weeks = 12)
        val evidence = repeatedEvidence(IndependentQualityFamily.TEMPO, listOf(720))
        val result = Engine15IndependentPlanGenerator.preview(
            input, owner, "prog-aerobic", now, qualityEvidence = evidence
        ) as Engine15IndependentPlanDecision.Deferred
        val dryRun = result.qualityDryRun!!

        assertTrue(dryRun.alternatives.none {
            it.progressionBasis == Engine15QualityProgressionBasis.RACE_TAPER_REDUCTION
        })
        assertTrue(dryRun.alternatives.last().workSeconds!! >= dryRun.alternatives.first().workSeconds!!)
        assertTrue(dryRun.alternatives.all { it.workSeconds!! <= 720 + 240 })
    }

    @Test fun sessionTimeBudgetCapsProgressionWithoutExceedingAvailableMinutes() {
        val input = baseRequest(goal = "Improve 5K", experience = 2, weeks = 12).copy(
            weeklyKm = 24.0,
            targetWeeklyKm = 24.0,
            longestKm = 7.0,
            planningPace = 270.0, // 4:30/km so base runs fit inside 35 min
            timeBudgetEnabled = true,
            minutes = 35 // 2100 seconds: fits 4x2m (1920s) but NOT 5x2m (2160s)
        )
        val evidence = repeatedEvidence(IndependentQualityFamily.INTERVALS, listOf(120, 120, 120, 120))
        val result = Engine15IndependentPlanGenerator.preview(
            input, owner, "prog-budget", now, qualityEvidence = evidence
        ) as Engine15IndependentPlanDecision.Deferred
        val dryRun = result.qualityDryRun!!

        assertEquals(IndependentQualityFamily.INTERVALS, dryRun.family)
        assertTrue(dryRun.alternatives.all { it.totalSeconds!! <= 35 * 60 })
        assertTrue(dryRun.alternatives.any {
            it.progressionBasis == Engine15QualityProgressionBasis.TIME_BUDGET_CAPPED
        })
    }

    @Test fun twelveWeekProgressiveThresholdDraftReconcilesApprovesAndValidates() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val input = baseRequest(goal = "Improve 10K", experience = 2, weeks = 12)
        val evidence = repeatedEvidence(IndependentQualityFamily.THRESHOLD, listOf(240, 240, 240))
        val preview = Engine15IndependentPlanGenerator.preview(
            input, owner, "prog-12w", now, qualityEvidence = evidence
        ) as Engine15IndependentPlanDecision.Deferred
        val claims = claimsForDryRun(preview.qualityDryRun!!)

        val draft = Engine15IndependentPlanGenerator.generateWithHistory(
            input.copy(qualityDistanceClaims = claims),
            owner,
            emptyList(),
            "prog-12w",
            now,
            qualityEvidence = evidence
        )
        val qualitySessions = draft.sessions.filter { it.quality }
        assertEquals(9, qualitySessions.size)
        val distinctWorkSeconds = qualitySessions.map { session ->
            session.runningPrescription!!.mixedSegments!!
                .filter { it.purpose == "WORK" }
                .sumOf { it.seconds!! }
        }.distinct()
        assertTrue("Expected multi-week progression across loading weeks, got $distinctWorkSeconds",
            distinctWorkSeconds.size >= 2)
        assertTrue(qualitySessions.last().reason.contains("Taper hold"))

        val approved = PlanOperations.approve(draft, listOf(draft))
        assertEquals(approved, PlanSnapshot.decode(approved.encode(), owner))
        Engine15IndependentPlanGenerator.validateSaved(approved, qualityEvidence = evidence)
    }

    @Test fun sixteenWeekMarathonTempoDraftSupportsFullHorizonAndExcludesRecoveryWeeks() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val input = baseRequest(goal = "Marathon", experience = 2, weeks = 16)
        val preview = Engine15IndependentPlanGenerator.preview(input, owner, "marathon-16w", now)
            as Engine15IndependentPlanDecision.Deferred
        val claims = claimsForDryRun(preview.qualityDryRun!!)

        val draft = Engine15IndependentPlanGenerator.generateWithHistory(
            input.copy(qualityDistanceClaims = claims),
            owner,
            emptyList(),
            "marathon-16w",
            now
        )
        val qualityWeeks = draft.sessions.filter { it.quality }.map {
            java.time.temporal.ChronoUnit.DAYS.between(monday, it.date).toInt() / 7 + 1
        }
        assertEquals(listOf(1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15), qualityWeeks)
        Engine15IndependentPlanGenerator.validateSaved(draft)
    }
}
