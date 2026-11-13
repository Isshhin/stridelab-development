package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Test

/** Synthetic contract tests; NOT evidence of coaching physiological efficacy. */
class Engine15CoachingTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val start = LocalDate.parse("2026-11-09")
    private val owner = "synthetic-owner"

    private fun request(
        goal: String = "Improve 5K", weekly: Double = 45.0,
        weeks: Int = 8, runDays: Int = 4, quality: Int = 1
    ) = PlanRequest(
        goal = goal, policyChoice = "engine15", start = start, weeks = weeks,
        minutes = 90, days = (1..7).toSet(), runDays = runDays, longDay = 6,
        qualityDays = setOf(2, 4), qualityCount = quality,
        experience = 2, weeklyKm = weekly, longestKm = 16.0,
        planningPace = 480.0
    )

    private fun week(plan: PlanSnapshot, index: Int) = plan.sessions.filter {
        ChronoUnit.DAYS.between(start, it.date) / 7L == index.toLong()
    }
    private fun volume(plan: PlanSnapshot, index: Int) =
        week(plan, index).filter { it.discipline == "run" }
            .sumOf { it.distanceKm ?: 0.0 }

    @Test fun boundedProgressionReallyReducesMileageAndKeepsDeloads() {
        val req = request(weekly = 60.0, quality = 0).copy(
            minutes = 120, planningPace = 360.0
        )
        val baseline = Engine1.generate(req.copy(policyChoice = "baseline"), owner, "v", now)
        val bounded = Engine15.generate(req, owner, "v", now)
        assertEquals(baseline.sessions.map { it.id to it.date },
            bounded.sessions.map { it.id to it.date })
        assertTrue(volume(bounded, 0) < volume(baseline, 0))
        assertTrue(volume(bounded, 0) < volume(bounded, 1))
        assertTrue(volume(bounded, 1) < volume(bounded, 2))
        assertTrue(volume(bounded, 3) < volume(bounded, 2))
        assertTrue(volume(bounded, 4) > volume(bounded, 3))
        assertTrue(volume(bounded, 4) < volume(bounded, 2))
        for (i in 0 until req.weeks) {
            val current = volume(bounded, i)
            assertTrue(current <= Engine15.runningCeiling(i, req.weeklyKm) + 1e-6)
            assertTrue(current <= volume(baseline, i) + 1e-6)
            val runs = week(bounded, i).filter { it.discipline == "run" }
            assertEquals(req.runDays, runs.size)
            val long = runs.single { it.kind == "long" }
            assertTrue(long.distanceKm!! <= current * 0.40 + 1e-6)
        }
        assertTrue(bounded.sessions.zip(baseline.sessions).all { (a, b) ->
            a.minutes <= b.minutes &&
                (a.distanceKm == null || (b.distanceKm != null && a.distanceKm <= b.distanceKm))
        })
    }

    @Test fun targetMileageIsASeparateCappedGoalAndMissingBaselineStaysTimeBased() {
        val req = request(weekly = 40.0, quality = 0).copy(
            weeks = 12, targetWeeklyKm = 70.0, minutes = 120, planningPace = 360.0
        )
        val plan = Engine15.generate(req, owner, "target-ramp", now)
        val weekly = (0 until req.weeks).map { volume(plan, it) }
        assertTrue(weekly.any { it > weekly.first() })
        assertTrue(weekly.all { it <= req.targetWeeklyKm!! + 1e-6 })
        assertTrue(weekly[3] < weekly[2])
        assertTrue(weekly[4] > weekly[3])
        assertEquals(req, PlanSnapshot.decode(plan.encode(), owner).request)

        val unknown = Engine15.generate(req.copy(weeklyKm = 0.0), owner, "no-base", now)
        assertTrue(unknown.sessions.filter { it.discipline == "run" }.all { it.distanceKm == null })
        assertTrue(unknown.sessions.filter { it.discipline == "run" }.all { !it.quality })
    }

    @Test fun absentTargetRetainsLegacyMileageCeilingWhileExplicitTargetUsesRamp() {
        assertEquals(18.81, Engine15.runningCeiling(6, 18.0), 0.001)
        assertTrue(Engine15.runningCeiling(6, 18.0, 70.0) > 18.0)
        val legacy = Engine15.generate(request(weekly=18.0, quality=0).copy(weeks=8), owner, "legacy-ramp", now)
        assertTrue(volume(legacy, 6) <= Engine15.runningCeiling(6, 18.0) + 1e-6)
    }

    @Test fun zeroTargetExplicitlyDisablesMileageWhileLegacyNullTargetDoesNot() {
        val baseline=request(weekly=24.0,quality=0).copy(weeks=8)
        val legacy=Engine15.generate(baseline,owner,"legacy-target",now)
        val noTarget=Engine15.generate(baseline.copy(targetWeeklyKm=0.0),owner,"zero-target",now)
        assertTrue(volume(legacy,0)>0.0)
        assertTrue((0 until 8).all { volume(noTarget,it)==0.0 })
    }

    @Test fun distinctGoalsHaveDifferentControlledQualitySessions() {
        val variants = listOf(
            "Improve 5K" to "3 × 3 min",
            "Improve 10K" to "2 × 6 min",
            "Half marathon" to "2 × 8 min",
            "Marathon" to "2 × 8 min"
        )
        val titles = mutableSetOf<String>()
        variants.forEach { (goal, phrase) ->
            val p = Engine15.generate(request(goal = goal, weeks = 4), owner, goal, now)
            val q = week(p, 0).single { it.quality }
            titles.add(q.title)
            assertTrue(q.steps.any { it.contains(phrase) })
            assertTrue(q.steps.any { it.contains("warm-up") })
            assertTrue(q.steps.any { it.contains("cool-down") })
            assertFalse(q.steps.any { it.contains("bpm") })
            assertTrue(q.minutes <= p.request.minutes)
        }
        assertEquals(4, titles.size)
    }

    @Test fun sixSelectedQualityStylesGenerateMatchingPrescriptions() {
        val expected = mapOf(
            "Threshold" to "Controlled threshold intervals", "Tempo" to "Controlled tempo run",
            "Intervals" to "Controlled 5K intervals", "Hills" to "Controlled hill repetitions",
            "Fartlek" to "Controlled fartlek run", "Progression" to "Easy progression run"
        )
        expected.forEach { (style, title) ->
            val p = Engine15.generate(request(weeks=4).copy(
                preferredQuality=style, allowedQualityTypes=setOf(style)
            ), owner, style, now)
            val quality = week(p, 0).single { it.quality }
            assertEquals(style, title, quality.title)
            assertTrue(quality.steps.any { it.contains("warm-up") })
            assertTrue(quality.steps.any { it.contains("cool-down") })
        }
    }

    @Test fun limitedMileageTurnsRequestedQualityIntoEasy() {
        val req = request(weekly = 18.0, weeks = 4)
            .copy(planningPace = 900.0, longestKm = 6.0)
        val baseline = Engine1.generate(req.copy(policyChoice = "baseline"), owner, "low", now)
        val conservative = Engine15.generate(req, owner, "low", now)
        assertTrue(baseline.sessions.any { it.quality })
        assertFalse(conservative.sessions.any { it.quality })
        assertTrue(conservative.sessions.none { it.kind == "quality" })
        assertEquals(baseline.sessions.map { it.id to it.date },
            conservative.sessions.map { it.id to it.date })
    }

    @Test fun secondQualityRequiresFortyKmAndDeloadOmitsHardRunning() {
        val limited = Engine15.generate(request(weekly = 35.0, quality = 2, weeks = 4),
            owner, "limited", now)
        assertEquals(1, week(limited, 0).count { it.quality })
        val boundary = Engine15.generate(request(weekly = 39.9, quality = 2, weeks = 4),
            owner, "boundary", now)
        assertEquals(1, week(boundary, 0).count { it.quality })
        assertEquals(0, week(limited, 3).count { it.quality })
        val experienced = Engine15.generate(request(weekly = 45.0, quality = 2, weeks = 4),
            owner, "experienced", now)
        assertEquals(2, week(experienced, 0).count { it.quality })
        assertEquals(0, week(experienced, 3).count { it.quality })
    }

    @Test fun returnFromBreakAndRaceTaperWithholdQuality() {
        val returning = Engine15.generate(request().copy(returning = true), owner, "return", now)
        for (i in 0..2) assertEquals(0, week(returning, i).count { it.quality })
        val half = Engine15.generate(request(goal = "Half marathon"), owner, "half", now)
        for (i in 6..7) assertEquals(0, week(half, i).count { it.quality })
        assertEquals(0, week(half, 3).count { it.quality })
    }

    @Test fun missingMileageUsesOnlyTimeBasedEasyRunning() {
        val plan = Engine15.generate(request(weekly = 0.0, weeks = 4).copy(
            experience = 0, longestKm = 0.0
        ), owner, "unknown", now)
        assertTrue(plan.sessions.filter { it.discipline == "run" }.all {
            it.distanceKm == null && !it.quality
        })
    }

    @Test fun foundationPolicyRemainsEditableAndOtherVersionsAreNot() {
        val plan = Engine15.generate(request(weeks = 4, quality = 0),
            owner, "compat", now)
        val foundation = plan.copy(policyId = ENGINE15_FOUNDATION_POLICY)
        val loaded = PlanSnapshot.decode(foundation.encode(), owner)
        assertEquals(foundation, loaded)
        assertTrue(Engine1Engines.canManage(loaded))
        assertEquals("approved", PlanOperations.approve(loaded, emptyList()).status)
        assertFalse(Engine1Engines.supports(ENGINE1_VERSION, ENGINE15_FOUNDATION_POLICY))
        assertFalse(Engine1Engines.supports(ENGINE15_VERSION, ENGINE1_POLICY))
    }

    @Test fun timeCapsScheduleSafetyAndRoundtripRemainIntact() {
        val req = request(weekly = 70.0, weeks = 4).copy(
            minutes = 35, longestKm = 8.0
        )
        val plan = Engine15.generate(req, owner, "short", now)
        assertEquals(plan, PlanSnapshot.decode(plan.encode(), owner))
        assertEquals(plan.sessions.size, plan.sessions.map { it.id }.distinct().size)
        assertTrue(plan.sessions.all { it.minutes <= 35 })
        assertTrue(plan.sessions.filter { it.kind == "quality" }.all { it.quality })
        assertFalse(plan.sessions.any { a -> plan.sessions.any { b ->
            a.id != b.id && Engine1.conflict(a, b)
        } })
    }
}
