package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class Engine15Batch1AuditFixesTest {
    private val owner = "batch1-audit-runner"
    private val now = Instant.parse("2026-11-21T08:00:00Z")
    private val monday = LocalDate.parse("2026-11-23")
    private val library by lazy {
        ExerciseLibraryV2.load { path -> File("src/main/assets", path).inputStream() }
    }

    private fun runRequest(
        goal: String = "Half marathon",
        experience: Int = 2,
        weeks: Int = 8,
        runDays: Int = 5,
        days: Set<Int> = setOf(1, 2, 4, 6, 7),
        longDay: Int = 6,
        qualityCount: Int = 1,
        qualityDays: Set<Int> = setOf(2),
        weeklyKm: Double = 80.0,
        longestKm: Double = 22.0,
        targetWeeklyKm: Double? = null,
        planningPace: Double = 360.0,
        recentMileageBasis: String = "SELF_REPORTED_AVERAGE",
        reportedPeakWeeklyKm: Double? = null,
        allowedQualityTypes: Set<String> = emptySet(),
        strengthDays: Int = 0,
        equipment: Map<String, Int> = emptyMap(),
        disciplineGoals: Map<String, String> = emptyMap()
    ) = PlanRequest(
        goal = goal,
        experience = experience,
        start = monday,
        weeks = weeks,
        days = days,
        runDays = runDays,
        strengthDays = strengthDays,
        longDay = longDay,
        qualityCount = qualityCount,
        qualityDays = qualityDays,
        allowedQualityTypes = allowedQualityTypes,
        weeklyKm = weeklyKm,
        longestKm = longestKm,
        targetWeeklyKm = targetWeeklyKm,
        planningPace = planningPace,
        timeBudgetEnabled = false,
        equipment = equipment,
        disciplineGoals = disciplineGoals,
        policyChoice = "engine15",
        recentMileageBasis = recentMileageBasis,
        reportedPeakWeeklyKm = reportedPeakWeeklyKm
    )

    @Test
    fun highMileage80kmQualityPacesStayWithinZoneBounds() {
        assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val req = runRequest(
            goal = "Half marathon",
            experience = 2,
            weeks = 8,
            runDays = 5,
            days = setOf(1, 2, 4, 5, 6),
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 80.0,
            longestKm = 22.0,
            targetWeeklyKm = 80.0,
            planningPace = 360.0
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "batch1-80km", now
        )
        Engine15IndependentPlanGenerator.validateSaved(plan)

        val zones = Engine15PaceZoneEstimator.estimate(req)
        val qualitySessions = plan.sessions.filter { it.quality }
        assertTrue(qualitySessions.isNotEmpty())

        for (w in 0 until req.weeks) {
            val s = monday.plusWeeks(w.toLong())
            val weekRows = plan.sessions.filter { it.date >= s && it.date < s.plusWeeks(1) }
            val sumKm = weekRows.sumOf { it.distanceKm!! }
            assertTrue("Expected positive reconciled week km, got $sumKm", sumKm > 0.0)
        }

        for (session in qualitySessions) {
            val archetype = Engine15WorkoutArchetype.fromId(session.runningPrescription!!.archetypeId)
            val segments = session.runningPrescription!!.mixedSegments!!
            for (seg in segments) {
                val target = when (seg.purpose) {
                    "WORK" -> when (seg.stepIntent) {
                        RunningStepIntent.THRESHOLD -> WorkoutTarget.THRESHOLD
                        RunningStepIntent.VO2_INTERVAL -> WorkoutTarget.INTERVALS
                        RunningStepIntent.HILL -> WorkoutTarget.HILLS
                        RunningStepIntent.TEMPO -> WorkoutTarget.TEMPO
                        else -> when (session.runningPrescription!!.family) {
                            "Threshold" -> WorkoutTarget.THRESHOLD
                            "Intervals" -> WorkoutTarget.INTERVALS
                            "Hills" -> WorkoutTarget.HILLS
                            "Fartlek" -> WorkoutTarget.FARTLEK
                            "Progression" -> WorkoutTarget.PROGRESSION
                            else -> WorkoutTarget.TEMPO
                        }
                    }
                    else -> WorkoutTarget.EASY
                }
                val sec = seg.seconds
                if (sec != null) {
                    val meters = seg.distanceEquivalentMeters!!
                    val impliedPace = sec * 1000.0 / meters
                    if (target == WorkoutTarget.HILLS) {
                        assertTrue(
                            "Implied hill pace $impliedPace s/km out of uphill zone [${zones.hillShort.fastSecondsPerKm * 0.80}, ${zones.hillLong.slowSecondsPerKm * 1.25}]",
                            impliedPace in (zones.hillShort.fastSecondsPerKm * 0.80)..(zones.hillLong.slowSecondsPerKm * 1.25)
                        )
                    } else {
                        val range = zones.forTarget(target, archetype)
                        assertTrue(
                            "Implied pace $impliedPace s/km for ${seg.purpose} out of zone [${range.fastSecondsPerKm * 0.82}, ${range.slowSecondsPerKm * 1.22}]",
                            impliedPace in (range.fastSecondsPerKm * 0.82)..(range.slowSecondsPerKm * 1.22)
                        )
                    }
                } else {
                    assertTrue((seg.meters ?: 0) > 0)
                    val pMin = requireNotNull(seg.targetPaceMinSecPerKm)
                    val pMax = requireNotNull(seg.targetPaceMaxSecPerKm)
                    val midpoint = (pMin + pMax) / 2.0
                    val range = zones.forTarget(target, archetype)
                    assertTrue(
                        "Distance step midpoint pace $midpoint out of zone [${range.fastSecondsPerKm * 0.82}, ${range.slowSecondsPerKm * 1.22}]",
                        midpoint in (range.fastSecondsPerKm * 0.82)..(range.slowSecondsPerKm * 1.22)
                    )
                }
            }
        }
    }

    @Test
    fun lowMileage25kmFastRunnerDoesNotWarpThresholdSlowerThanEasy() {
        assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val req = runRequest(
            goal = "Improve 10K",
            experience = 1,
            weeks = 8,
            runDays = 5,
            days = setOf(1, 2, 4, 6, 7),
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 25.0,
            longestKm = 8.0,
            targetWeeklyKm = 25.0,
            planningPace = 300.0
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "batch1-25km-fast", now
        )
        Engine15IndependentPlanGenerator.validateSaved(plan)

        val zones = Engine15PaceZoneEstimator.estimate(req)
        val qualitySessions = plan.sessions.filter { it.quality }
        assertTrue(qualitySessions.isNotEmpty())
        for (session in qualitySessions) {
            val archetype = Engine15WorkoutArchetype.fromId(session.runningPrescription!!.archetypeId)
            val segs = session.runningPrescription!!.mixedSegments!!
            fun stepPace(seg: RunningStepV2): Double =
                seg.seconds?.let { it * 1000.0 / seg.distanceEquivalentMeters!! }
                    ?: ((seg.targetPaceMinSecPerKm!! + seg.targetPaceMaxSecPerKm!!) / 2.0)
            val nonHillWork = segs.filter { it.purpose == "WORK" && it.stepIntent != RunningStepIntent.HILL }
            val easyPaces = segs.filter { it.purpose != "WORK" }.map(::stepPace)
            if (nonHillWork.isNotEmpty()) {
                val workPaces = nonHillWork.map(::stepPace)
                assertTrue("Work pace $workPaces must be faster than easy pace $easyPaces", workPaces.max() < easyPaces.min())
            }
            for (seg in nonHillWork) {
                val target = when (seg.stepIntent) {
                    RunningStepIntent.THRESHOLD -> WorkoutTarget.THRESHOLD
                    RunningStepIntent.VO2_INTERVAL -> WorkoutTarget.INTERVALS
                    RunningStepIntent.TEMPO -> WorkoutTarget.TEMPO
                    else -> WorkoutTarget.THRESHOLD
                }
                val workRange = zones.forTarget(target, archetype)
                val pace = stepPace(seg)
                assertTrue(
                    "Work pace $pace warped outside zone [${workRange.fastSecondsPerKm * 0.82}, ${workRange.slowSecondsPerKm * 1.22}]",
                    pace in (workRange.fastSecondsPerKm * 0.82)..(workRange.slowSecondsPerKm * 1.22)
                )
            }
        }
    }

    @Test
    fun twoQualitySessionsPerWeekSplitWeeklyHardWorkBudget() {
        assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val singleReq = runRequest(
            goal = "Half marathon",
            experience = 2,
            weeks = 8,
            runDays = 6,
            days = setOf(1, 2, 3, 4, 5, 6),
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 60.0,
            longestKm = 18.0,
            targetWeeklyKm = 60.0,
            planningPace = 360.0
        )
        val doubleReq = singleReq.copy(qualityCount = 2, qualityDays = setOf(2, 4))

        val singleAnchor = Engine15QualityProgressionPolicy.computeAdaptiveExposureAnchorSeconds(
            request = singleReq,
            family = IndependentQualityFamily.THRESHOLD
        )
        val doubleAnchor = Engine15QualityProgressionPolicy.computeAdaptiveExposureAnchorSeconds(
            request = doubleReq,
            family = IndependentQualityFamily.THRESHOLD
        )
        assertTrue("Expected 2-session anchor ($doubleAnchor s) < 1-session anchor ($singleAnchor s)", doubleAnchor < singleAnchor)

        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            doubleReq, owner, emptyList(), "batch1-2q-split", now
        )
        Engine15IndependentPlanGenerator.validateSaved(plan)

        val byWeek = plan.sessions.groupBy { ChronoUnit.DAYS.between(monday, it.date).toInt() / 7 + 1 }
        for ((wk, rows) in byWeek) {
            val qRows = rows.filter { it.quality }
            if (qRows.isEmpty()) continue
            val weekKm = rows.sumOf { it.distanceKm!! }
            val totalWorkKm = qRows.sumOf { session ->
                session.runningPrescription!!.mixedSegments!!
                    .filter { it.purpose == "WORK" }
                    .sumOf { it.meters ?: it.distanceEquivalentMeters!! } / 1000.0
            }
            assertTrue(
                "Week $wk combined quality WORK distance ${totalWorkKm} km exceeded 20% of ${weekKm} km",
                totalWorkKm <= weekKm * 0.20 + 1e-6
            )
        }
    }

    @Test
    fun qualityRotationRespectsPerFamilyWorkCeilings() {
        assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val req = runRequest(
            goal = "Half marathon",
            experience = 2,
            weeks = 12,
            runDays = 6,
            days = setOf(1, 2, 3, 4, 5, 6),
            longDay = 6,
            qualityCount = 2,
            qualityDays = setOf(2, 4),
            weeklyKm = 75.0,
            longestKm = 22.0,
            targetWeeklyKm = 80.0,
            planningPace = 330.0,
            allowedQualityTypes = setOf("Tempo", "Intervals", "Hills")
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "batch1-rotation-caps", now
        )
        Engine15IndependentPlanGenerator.validateSaved(plan)

        val qualitySessions = plan.sessions.filter { it.quality }
        assertTrue(qualitySessions.isNotEmpty())
        for (session in qualitySessions) {
            val fam = session.runningPrescription!!.family
            val workSec = session.runningPrescription!!.mixedSegments!!
                .filter { it.purpose == "WORK" }
                .sumOf {
                    it.seconds ?: kotlin.math.round(
                        (it.meters ?: 0) * (((it.targetPaceMinSecPerKm ?: 240) + (it.targetPaceMaxSecPerKm ?: 260)) / 2000.0)
                    ).toInt()
                }
            when (fam) {
                "Intervals" -> assertTrue("Intervals work $workSec s exceeded 1080 s", workSec <= 1080)
                "Hills" -> assertTrue("Hills work $workSec s exceeded 600 s", workSec <= 600)
            }
        }
    }

    @Test
    fun qualityPaceWarningFlagsUniformlyScaledAbsurdPaces() {
        val req = runRequest(weeklyKm = 80.0, planningPace = 360.0)
        val zones = Engine15PaceZoneEstimator.estimate(req)

        // 1.93x warped fast match (~2:35/km threshold, ~3:13/km easy)
        val warpedFast = Engine15M5ArithmeticMatch(
            weekNumber = 1,
            slotNumber = 2,
            family = IndependentQualityFamily.THRESHOLD,
            originalEasySlotKm = 16.0,
            sumAssertedMeters = 16_000.0,
            workSeconds = 1_440,
            totalSeconds = 2_760,
            segments = listOf(
                Engine15M5TimedDistanceSegment(WorkoutPurpose.WARM_UP, WorkoutTarget.EASY, 600.0, 3_100.0),
                Engine15M5TimedDistanceSegment(WorkoutPurpose.WORK, WorkoutTarget.THRESHOLD, 1_440.0, 9_300.0),
                Engine15M5TimedDistanceSegment(WorkoutPurpose.COOL_DOWN, WorkoutTarget.EASY, 720.0, 3_600.0)
            )
        )
        assertNotNull("Expected warning for 2:35/km warped threshold", warpedFast.qualityPaceWarning(zones))

        // 0.52x warped slow match (~9:20/km threshold, ~11:30/km easy)
        val warpedSlow = Engine15M5ArithmeticMatch(
            weekNumber = 1,
            slotNumber = 2,
            family = IndependentQualityFamily.THRESHOLD,
            originalEasySlotKm = 4.5,
            sumAssertedMeters = 4_500.0,
            workSeconds = 1_440,
            totalSeconds = 2_520,
            segments = listOf(
                Engine15M5TimedDistanceSegment(WorkoutPurpose.WARM_UP, WorkoutTarget.EASY, 600.0, 870.0),
                Engine15M5TimedDistanceSegment(WorkoutPurpose.WORK, WorkoutTarget.THRESHOLD, 1_440.0, 2_570.0),
                Engine15M5TimedDistanceSegment(WorkoutPurpose.COOL_DOWN, WorkoutTarget.EASY, 480.0, 1_060.0)
            )
        )
        assertNotNull("Expected warning for 9:20/km warped threshold", warpedSlow.qualityPaceWarning(zones))
    }

    @Test
    fun confirmedZeroWithStrengthUsesTimeOnlyRunWalkWithoutSyntheticKm() {
        val req = runRequest(
            goal = "General fitness",
            experience = 0,
            weeks = 4,
            runDays = 3,
            days = setOf(1, 2, 3, 5, 6),
            longDay = 6,
            qualityCount = 0,
            qualityDays = emptySet(),
            weeklyKm = 0.0,
            longestKm = 0.0,
            recentMileageBasis = "CONFIRMED_ZERO",
            strengthDays = 2,
            equipment = mapOf("bands" to 1, "anchor" to 1, "dumbbells" to 1, "bench" to 1),
            disciplineGoals = mapOf("run" to "General fitness", "str" to "General strength")
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "batch1-zero-str", now, exerciseLibrary = library
        )
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, plan.policyId)
        val runSessions = plan.sessions.filter { it.discipline == "run" }
        assertEquals(12, runSessions.size)
        assertTrue(runSessions.all { it.distanceKm == null && !it.quality })
        assertTrue(runSessions.all {
            val rx = it.runningPrescription!!
            rx.mixedSegments != null && rx.estimatedDistanceKm == null && rx.reconciledDistanceMeters == null
        })

        val approved = PlanOperations.approve(plan, listOf(plan))
        val restored = PlanSnapshot.decode(approved.encode(), owner)
        assertEquals(approved, restored)
        Engine15IndependentPlanGenerator.validateSaved(restored)
    }

    @Test
    fun confirmedZeroWithTargetOrQualityDoesNotFabricate20Km() {
        val req = runRequest(
            goal = "Improve 5K",
            experience = 0,
            weeks = 8,
            runDays = 5,
            days = setOf(1, 2, 4, 6, 7),
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 0.0,
            longestKm = 0.0,
            targetWeeklyKm = 25.0,
            recentMileageBasis = "CONFIRMED_ZERO"
        )
        val previewDecision = Engine15IndependentPlanGenerator.previewAutoReconciled(
            req, owner, "batch1-zero-preview", now
        )
        assertTrue(previewDecision is Engine15IndependentPlanDecision.Deferred)
        assertEquals("ATHLETE_BASELINE_TIME_ONLY", (previewDecision as Engine15IndependentPlanDecision.Deferred).reasonCode)

        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "batch1-zero-target-q", now
        )
        assertEquals("CONFIRMED_ZERO", plan.request.recentMileageBasis)
        assertEquals(0.0, plan.request.weeklyKm, 1e-9)
        assertTrue(plan.sessions.all { it.distanceKm == null && !it.quality })
        Engine15IndependentPlanGenerator.validateSaved(plan)
    }

    @Test
    fun peakWeekOnlyBasisIsNotPromotedToSelfReportedAverage() {
        val contradictingPeak = runRequest(
            weeklyKm = 60.0,
            longestKm = 18.0,
            recentMileageBasis = "PEAK_WEEK_ONLY"
        )
        val d1 = Engine15IndependentPlanGenerator.previewAutoReconciled(
            contradictingPeak, owner, "batch1-peak-60", now
        )
        assertTrue("Expected Deferred for PEAK_WEEK_ONLY with 60km, got $d1", d1 is Engine15IndependentPlanDecision.Deferred)
        assertEquals("ATHLETE_BASELINE_NEEDS_REVIEW", (d1 as Engine15IndependentPlanDecision.Deferred).reasonCode)

        val zeroWithPeak = runRequest(
            weeklyKm = 0.0,
            longestKm = 0.0,
            recentMileageBasis = "PEAK_WEEK_ONLY",
            reportedPeakWeeklyKm = 60.0
        )
        val d2 = Engine15IndependentPlanGenerator.previewAutoReconciled(
            zeroWithPeak, owner, "batch1-peak-only", now
        )
        assertTrue("Expected Deferred for PEAK_WEEK_ONLY, got $d2", d2 is Engine15IndependentPlanDecision.Deferred)
        assertEquals("ATHLETE_BASELINE_NEEDS_BASELINE", (d2 as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test
    fun unknownZeroBaselineDefersForConfirmationInsteadOfSynthesizing() {
        val req = runRequest(
            weeklyKm = 0.0,
            longestKm = 0.0,
            recentMileageBasis = "UNKNOWN"
        )
        val decision = Engine15IndependentPlanGenerator.previewAutoReconciled(
            req, owner, "batch1-unknown-zero", now
        )
        assertTrue("Expected Deferred for UNKNOWN zero baseline, got $decision", decision is Engine15IndependentPlanDecision.Deferred)
        assertEquals("ATHLETE_BASELINE_NEEDS_BASELINE", (decision as Engine15IndependentPlanDecision.Deferred).reasonCode)
        assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(req, owner, emptyList(), "batch1-unknown-zero", now)
        }
    }
}
