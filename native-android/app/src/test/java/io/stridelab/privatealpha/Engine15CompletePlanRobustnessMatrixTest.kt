package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

/**
 * Batch 30 (Stage 4): Complete-plan robustness & scenario matrix across 8–16-week
 * horizons, 2–6 running days, all running goals, multi-discipline external interference,
 * session locks, recovery/deload/taper weeks, tampering rejection, and snapshot round-trips.
 */
class Engine15CompletePlanRobustnessMatrixTest {
    private val owner = "matrix-robustness-runner"
    private val now = Instant.parse("2026-11-17T00:00:00Z")
    private val monday = LocalDate.parse("2026-11-16")

    private fun daysForCount(runDays: Int): Set<Int> = when (runDays) {
        2 -> setOf(2, 6)
        3 -> setOf(2, 4, 6)
        4 -> setOf(1, 2, 4, 6)
        5 -> setOf(1, 2, 4, 6, 7)
        6 -> setOf(1, 2, 3, 5, 6, 7)
        else -> error("Unsupported runDays=$runDays")
    }

    private fun baseRequest(
        goal: String = "Improve 10K",
        experience: Int = 2,
        weeks: Int = 12,
        runDays: Int = 5,
        qualityCount: Int = 0
    ) = PlanRequest(
        goal = goal,
        experience = experience,
        start = monday,
        weeks = weeks,
        days = daysForCount(runDays),
        runDays = runDays,
        longDay = 6,
        qualityCount = qualityCount,
        qualityDays = setOf(2),
        weeklyKm = 36.0,
        longestKm = 12.0,
        targetWeeklyKm = 36.0,
        timeBudgetEnabled = false,
        policyChoice = "engine15",
        recentMileageBasis = "SELF_REPORTED_AVERAGE"
    )

    private fun repeatedEvidence(
        family: IndependentQualityFamily,
        workSegments: List<Int>
    ) = listOf(
        IndependentQualitySegmentEvidence(
            ownerId = owner,
            sourceRecordId = "matrix-src-${family.name.lowercase()}-1",
            sessionId = "matrix-sess-${family.name.lowercase()}-1",
            performedOn = monday.minusDays(10),
            family = family,
            origin = IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
            sourceSessionMatchAsserted = true,
            performedWorkSegmentsSeconds = workSegments
        ),
        IndependentQualitySegmentEvidence(
            ownerId = owner,
            sourceRecordId = "matrix-src-${family.name.lowercase()}-2",
            sessionId = "matrix-sess-${family.name.lowercase()}-2",
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
        val weights = phases.map { step ->
            step.amount * if (step.purpose == WorkoutPurpose.WORK) 1.25 else 1.0
        }
        val sumWeights = weights.sum()
        val raw = weights.dropLast(1).map { kotlin.math.floor(it / sumWeights * totalMeters) }
        Engine15M5SegmentDistanceClaim(
            weekNumber = alt.weekNumber,
            slotNumber = alt.slotNumber,
            assertedMetersByTimedSegment = raw + (totalMeters - raw.sum()),
            externalReviewReference = null,
            provenance = Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT
        )
    }

    @Test fun fullHorizonFrequencyAndGoalMatrixReconcilesApprovesAndRoundTrips() {
        val horizons = listOf(8, 10, 12, 16)
        val frequencies = listOf(2, 3, 4, 5, 6)
        val goals = PlanDisciplineContract.goals.getValue("run")
        var checkedPlans = 0

        for (weeks in horizons) {
            for (runDays in frequencies) {
                val goal = goals[(weeks + runDays) % goals.size]
                val experience = (weeks + runDays) % 3
                val weekly = if (runDays == 2) 18.0 else 36.0
                val req = baseRequest(goal = goal, experience = experience, weeks = weeks, runDays = runDays)
                    .copy(weeklyKm = weekly, targetWeeklyKm = weekly)
                val first = Engine15IndependentPlanGenerator.generateWithHistory(
                    req, owner, emptyList(), "matrix-$weeks-$runDays", now
                )
                val second = Engine15IndependentPlanGenerator.generateWithHistory(
                    req, owner, emptyList(), "matrix-$weeks-$runDays", now
                )
                assertEquals("Non-deterministic plan for weeks=$weeks runDays=$runDays", first, second)
                assertEquals(weeks * runDays, first.sessions.size)
                assertEquals(first.sessions.size, first.sessions.map { it.date }.distinct().size)
                assertTrue(first.sessions.all { it.date.dayOfWeek.value in req.days })

                val byWeek = first.sessions.groupBy {
                    ChronoUnit.DAYS.between(monday, it.date).toInt() / 7 + 1
                }
                assertEquals((1..weeks).toSet(), byWeek.keys)
                for (w in 1..weeks) {
                    val weekSessions = byWeek.getValue(w)
                    assertEquals(runDays, weekSessions.size)
                    if (runDays >= 3) {
                        val longRun = weekSessions.single { it.kind == "long" }
                        assertEquals(req.longDay, longRun.date.dayOfWeek.value)
                    }
                    if (w % 4 == 0 && w >= 4) {
                        val prevKm = byWeek.getValue(w - 1).sumOf { it.distanceKm!! }
                        val recKm = weekSessions.sumOf { it.distanceKm!! }
                        assertTrue("Recovery week $w ($recKm km) should be lighter than week ${w - 1} ($prevKm km)",
                            recKm < prevKm)
                    }
                }

                val approved = PlanOperations.approve(first, listOf(first))
                val restored = PlanSnapshot.decode(approved.encode(), owner)
                assertEquals(approved, restored)
                Engine15IndependentPlanGenerator.validateSaved(restored)
                checkedPlans++
            }
        }
        assertEquals(20, checkedPlans)
    }

    @Test fun progressiveQualityMatrixAcrossAllThreeFamiliesAndEightToSixteenWeeks() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val scenarios = listOf(
            Triple("Improve 5K", IndependentQualityFamily.INTERVALS, listOf(120, 120, 120, 120)),
            Triple("Improve 10K", IndependentQualityFamily.THRESHOLD, listOf(240, 240, 240)),
            Triple("Marathon", IndependentQualityFamily.TEMPO, listOf(720))
        )
        for ((goal, expectedFamily, segments) in scenarios) {
            for (weeks in listOf(8, 12, 16)) {
                val req = baseRequest(goal = goal, experience = 2, weeks = weeks, runDays = 5, qualityCount = 1)
                val evidence = repeatedEvidence(expectedFamily, segments)
                val preview = Engine15IndependentPlanGenerator.preview(
                    req, owner, "qmatrix-${expectedFamily.name.lowercase()}-$weeks", now,
                    qualityEvidence = evidence
                ) as Engine15IndependentPlanDecision.Deferred
                assertEquals(expectedFamily, preview.qualitySelection!!.selectedFamily)
                val dryRun = preview.qualityDryRun!!
                val claims = claimsForDryRun(dryRun)

                val baseEasy = Engine15IndependentPlanGenerator.generateWithHistory(
                    req.copy(qualityCount = 0), owner, emptyList(),
                    "qmatrix-${expectedFamily.name.lowercase()}-$weeks", now
                )
                val draft = Engine15IndependentPlanGenerator.generateWithHistory(
                    req.copy(qualityDistanceClaims = claims), owner, emptyList(),
                    "qmatrix-${expectedFamily.name.lowercase()}-$weeks", now,
                    qualityEvidence = evidence
                )

                // Weekly mileage budgets and long-run prescriptions are strictly preserved under hard/easy recovery shaping
                assertEquals(
                    baseEasy.sessions.map { it.id to it.date },
                    draft.sessions.map { it.id to it.date }
                )
                assertEquals(
                    baseEasy.sessions.filter { it.kind == "long" }.map { it.date to it.distanceKm },
                    draft.sessions.filter { it.kind == "long" }.map { it.date to it.distanceKm }
                )
                for (w in 0 until weeks) {
                    val startW = monday.plusWeeks(w.toLong())
                    val endW = startW.plusWeeks(1)
                    val baseKm = baseEasy.sessions.filter { it.date >= startW && it.date < endW }.sumOf { it.distanceKm!! }
                    val draftKm = draft.sessions.filter { it.date >= startW && it.date < endW }.sumOf { it.distanceKm!! }
                    assertEquals(baseKm, draftKm, 1e-6)
                }
                // Recovery weeks never receive quality sessions
                val qualityWeeks = draft.sessions.filter { it.quality }.map {
                    ChronoUnit.DAYS.between(monday, it.date).toInt() / 7 + 1
                }
                assertEquals((1..weeks).filterNot { it % 4 == 0 }, qualityWeeks)
                // Every quality session stays >= 2 days from the long run
                val qualitySessions = draft.sessions.filter { it.quality }
                val longRuns = draft.sessions.filter { it.kind == "long" }
                for (q in qualitySessions) {
                    assertTrue(longRuns.all { abs(ChronoUnit.DAYS.between(q.date, it.date)) >= 2L })
                }
                // Race taper applies to the final loading week
                assertTrue(qualitySessions.last().reason.contains("Taper hold"))

                val approved = PlanOperations.approve(draft, listOf(draft))
                val restored = PlanSnapshot.decode(approved.encode(), owner)
                assertEquals(approved, restored)
                assertEquals(segments.sum(), restored.experimentalQualityExposureSeconds)
                Engine15IndependentPlanGenerator.validateSaved(restored)
            }
        }
    }

    @Test fun externalHardLowerBodySessionBlocksAdjacentQualityButUpperBodyAllowsIt() {
        val req = baseRequest(goal = "Improve 10K", experience = 2, weeks = 8, runDays = 5, qualityCount = 1)
        val qualityDate = monday.plusDays(1) // Tuesday (week 1)
        val adjacentWednesday = monday.plusDays(2) // Non-running day, 1 day after Tuesday quality

        val lowerBodyExternal = PlanSnapshot(
            id = "ext-str-lower",
            owner = owner,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_STRENGTH_POLICY,
            createdAt = now,
            request = PlanRequest(start = monday, weeks = 8, runDays = 0, strengthDays = 1),
            sessions = listOf(
                PlannedSession(
                    id = "ext-lower-1",
                    date = adjacentWednesday,
                    discipline = "str",
                    title = "Lower body strength",
                    minutes = 45,
                    distanceKm = null,
                    kind = "strength",
                    lower = true,
                    quality = false
                )
            ),
            explanations = listOf("External lower-body strength"),
            status = "approved"
        )
        val blocked = Engine15IndependentPlanGenerator.preview(
            req, owner, "ext-check-blocked", now, existingPlans = listOf(lowerBodyExternal)
        ) as Engine15IndependentPlanDecision.Deferred
        assertEquals("QUALITY_SLOT_UNAVAILABLE", blocked.qualitySelection!!.reasonCode)
        assertNull(blocked.qualityDryRun)

        val upperBodyExternal = lowerBodyExternal.copy(
            id = "ext-str-upper",
            sessions = listOf(
                lowerBodyExternal.sessions.single().copy(
                    id = "ext-upper-1",
                    title = "Upper body strength",
                    lower = false
                )
            )
        )
        val allowed = Engine15IndependentPlanGenerator.preview(
            req, owner, "ext-check-allowed", now, existingPlans = listOf(upperBodyExternal)
        ) as Engine15IndependentPlanDecision.Deferred
        assertEquals(Engine15QualitySelectionDecision.FAMILY_CANDIDATE, allowed.qualitySelection!!.decision)
        assertEquals(qualityDate, allowed.qualitySelection.candidateSlots.first().date)
        assertNotNull(allowed.qualityDryRun)
    }

    @Test fun tamperingWithExperimentalQualityExposureAnchorIsRejectedByValidateSaved() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val req = baseRequest(goal = "Improve 10K", experience = 2, weeks = 12, runDays = 5, qualityCount = 1)
        val evidence = repeatedEvidence(IndependentQualityFamily.THRESHOLD, listOf(240, 240, 240))
        val preview = Engine15IndependentPlanGenerator.preview(
            req, owner, "tamper-test", now, qualityEvidence = evidence
        ) as Engine15IndependentPlanDecision.Deferred
        val claims = claimsForDryRun(preview.qualityDryRun!!)
        val draft = Engine15IndependentPlanGenerator.generateWithHistory(
            req.copy(qualityDistanceClaims = claims), owner, emptyList(), "tamper-test", now,
            qualityEvidence = evidence
        )

        val outOfRange = assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.validateSaved(draft.copy(experimentalQualityExposureSeconds = 0))
        }
        assertTrue(outOfRange.message.orEmpty().contains("invalid experimental quality exposure"))

        val mismatched = assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.validateSaved(draft.copy(experimentalQualityExposureSeconds = null))
        }
        assertTrue(mismatched.message.orEmpty().contains("QUALITY_GENERATION_NOT_QUALIFIED"))
    }
}
