package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15PhaseACoreTest {
    private val owner = "phase-a-runner"
    private val now = Instant.parse("2026-11-21T00:00:00Z")
    private val monday = LocalDate.parse("2026-11-23")
    private val library by lazy {
        ExerciseLibraryV2.load { path -> File("src/main/assets", path).inputStream() }
    }

    @Test fun provisionalPaceZonesDeriveFromBenchmarkAndAutoReconcileQualityPlans() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val req = PlanRequest(
            goal = "Improve 5K",
            experience = 2,
            start = monday,
            weeks = 12,
            days = setOf(1, 2, 4, 6, 7),
            runDays = 5,
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 36.0,
            longestKm = 12.0,
            targetWeeklyKm = 36.0,
            timeBudgetEnabled = false,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            targetRaceDate = monday.plusWeeks(12),
            benchmarkDistanceMeters = 5_000,
            benchmarkDurationSeconds = 1_350 // 22:30 5K
        )
        assertEquals(req, PlanRequest.decode(req.encode()))

        val zones = Engine15PaceZoneEstimator.estimate(req)
        assertEquals(Engine15PaceBandBasis.RECENT_BENCHMARK_ESTIMATE, zones.basis)
        assertTrue(zones.provisional && !zones.sourceEvidenceVerified)
        assertTrue(zones.intervals.midpointSecondsPerKm < zones.threshold.midpointSecondsPerKm)
        assertTrue(zones.threshold.midpointSecondsPerKm < zones.tempo.midpointSecondsPerKm)
        assertTrue(zones.tempo.midpointSecondsPerKm < zones.easy.midpointSecondsPerKm)

        val preview = Engine15IndependentPlanGenerator.preview(req, owner, "auto-reconcile", now)
            as Engine15IndependentPlanDecision.Deferred
        val autoClaims = Engine15PaceZoneEstimator.estimateSegmentClaims(preview.qualityDryRun!!, zones)
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req.copy(qualityDistanceClaims = autoClaims), owner, emptyList(), "auto-reconcile", now
        )
        assertEquals(9, plan.sessions.count { it.quality })
        Engine15IndependentPlanGenerator.validateSaved(plan)
    }

    @Test fun marathonAndHalfMarathonVolumePlannerProduceProgressiveMultiWeekTaper() {
        val capacity = Engine15AthleteBaseline.assess(
            PlanRequest(weeklyKm = 50.0, longestKm = 20.0, recentMileageBasis = "SELF_REPORTED_AVERAGE")
        )
        val marathon = Engine15IndependentVolumePlanner.propose(
            capacity,
            Engine15IndependentVolumePlanner.Input(weeks = 16, runningDays = 5, targetWeeklyKm = 60.0, raceGoal = "Marathon")
        )
        val lastThree = marathon.weeks.takeLast(3)
        assertTrue(lastThree.all { "RACE_TAPER_VOLUME_REDUCTION" in it.constraintCodes })
        assertTrue(lastThree[0].proposedKm!! > lastThree[1].proposedKm!!)
        assertTrue(lastThree[1].proposedKm!! > lastThree[2].proposedKm!!)
        assertEquals(Engine15IndependentVolumePlanner.WeekKind.RECOVERY, lastThree[2].kind)

        val half = Engine15IndependentVolumePlanner.propose(
            capacity,
            Engine15IndependentVolumePlanner.Input(weeks = 12, runningDays = 5, targetWeeklyKm = 55.0, raceGoal = "Half marathon")
        )
        val lastTwo = half.weeks.takeLast(2)
        assertTrue(lastTwo.all { "RACE_TAPER_VOLUME_REDUCTION" in it.constraintCodes })
        assertTrue(lastTwo[0].proposedKm!! > lastTwo[1].proposedKm!!)
    }

    @Test fun userFirstMultiDisciplinePlannerAllowsExplicitOverlapWithoutDoubleDayToggle() {
        // User puts Run and Lower-Body Strength on the exact same weekday WITHOUT toggling allowDoubleDays!
        val req = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = monday,
            weeks = 4,
            days = setOf(1, 3, 6),
            runDays = 3,
            strengthDays = 2,
            longDay = 6,
            weeklySlots = mapOf("run" to listOf(6, 1, 3), "str" to listOf(1, 3)),
            allowDoubleDays = false,
            weeklyKm = 22.0,
            longestKm = 10.0,
            targetWeeklyKm = 22.0,
            timeBudgetEnabled = false,
            equipment = mapOf("bands" to 1, "anchor" to 1, "dumbbells" to 1, "bench" to 1),
            disciplineGoals = mapOf("run" to "Improve 10K", "str" to "General strength"),
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "user-freedom-overlap", now, exerciseLibrary = library
        )
        assertEquals(20, plan.sessions.size)
        assertTrue(plan.explanations.any { it.contains("Stacked", ignoreCase = true) })
        assertTrue(plan.sessions.any { it.reason.contains("AM/PM") })
        Engine15IndependentPlanGenerator.validateSaved(plan)
    }
}
