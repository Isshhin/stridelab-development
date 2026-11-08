package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainYourWayMileageTest {
    private val start = LocalDate.parse("2026-11-09")
    private val now = Instant.parse("2026-11-08T00:00:00Z")

    private fun request(baseline: Double = 80.0, target: Double = 80.0) =
        PlanRequest(
            goal = "Improve 10K",
            disciplineGoals = mapOf("run" to "Improve 10K"),
            policyChoice = "engine15",
            start = start,
            weeks = 8,
            experience = 2,
            weeklyKm = baseline,
            targetWeeklyKm = target,
            trainYourWay = true,
            longestKm = 16.0,
            minutes = 120,
            planningPace = 400.0,
            runDays = 5,
            qualityCount = 1,
            qualityDays = setOf(2),
            allowedQualityTypes = setOf("Hills"),
            preferredQuality = "Hills"
        )

    @Test fun customPlanUsesNeutralGoalAndTrainYourWayIdentity() {
        val plan = Engine15.generate(request(), "synthetic-owner", "custom", now)

        assertEquals("Train Your Way", plan.name)
        assertEquals("General fitness", plan.request.goal)
        assertFalse("run" in plan.request.disciplineGoals)
        assertEquals(80.0, plan.request.targetWeeklyKm!!, 0.0)
    }

    @Test fun normalWeeksDistributeTargetMileageAndRecoveryWeeksExplainReduction() {
        val plan = Engine15.generate(request(), "synthetic-owner", "mileage", now)
        fun volume(week: Int) = plan.sessions.filter {
            it.discipline == "run" && ChronoUnit.DAYS.between(start, it.date) / 7 == week.toLong()
        }.sumOf { it.distanceKm ?: 0.0 }

        assertEquals(80.0, volume(2), 0.11)
        assertEquals(60.8, volume(3), 0.11)
        assertTrue(plan.sessions.filter { ChronoUnit.DAYS.between(start, it.date) / 7 == 3L }
            .filter { it.discipline == "run" }
            .all { it.reason.contains("recovery", ignoreCase = true) ||
                it.reason.contains("deload", ignoreCase = true) ||
                it.reason.contains("lighter week", ignoreCase = true) })
        assertTrue(plan.explanations.any { it.contains("80 km/week") &&
            it.contains("recovery", ignoreCase = true) })
    }

    @Test fun targetAboveSustainableMileageIsCappedAndExplained() {
        val plan = Engine15.generate(request(baseline = 30.0, target = 80.0),
            "synthetic-owner", "bounded", now)
        val firstNormalWeek = plan.sessions.filter {
            it.discipline == "run" && ChronoUnit.DAYS.between(start, it.date) / 7 == 2L
        }

        assertEquals(30.0, firstNormalWeek.sumOf { it.distanceKm ?: 0.0 }, 0.11)
        assertTrue(firstNormalWeek.any { it.reason.contains("target", ignoreCase = true) })
        assertTrue(plan.explanations.any { it.contains("target") && it.contains("30.0") })
    }

    @Test fun onlySelectedQualityTypesArePrescribed() {
        val hills = Engine15.generate(request(), "synthetic-owner", "hills", now)
        val workout = hills.sessions.first { it.quality }
        assertEquals("Hill repeats", workout.title)
        assertTrue(workout.steps.any { it.contains("hill", ignoreCase = true) })

        val empty = Engine15.generate(request().copy(qualityCount=0, allowedQualityTypes = emptySet()),
            "synthetic-owner", "no-quality", now)
        assertTrue(empty.sessions.none { it.quality })
    }

    @Test fun customFieldsRoundTripWithoutChangingExistingRaceRequestDefaults() {
        val custom = request()
        assertEquals(custom, PlanRequest.decode(custom.encode()))
        val legacy = PlanRequest()
        assertEquals(legacy, PlanRequest.decode(legacy.encode()))
        assertFalse(PlanRequest.decode(legacy.encode()).trainYourWay)
        assertEquals(null, PlanRequest.decode(legacy.encode()).targetWeeklyKm)
        val race = custom.copy(trainYourWay = false, targetWeeklyKm = null)
        assertEquals(race, PlanRequest.decode(race.encode()))
    }

    @Test fun customRouteSkipsRaceIntentAndClearsInheritedRaceGoal() {
        val previous = PlanRequest(
            goal = "Improve 10K",
            disciplineGoals = mapOf("run" to "Improve 10K"),
            weeklyKm = 42.0,
            policyChoice = "coordinated",
            days = setOf(1, 2, 4, 6),
            longDay = 6,
            runDays = 4,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklySlots = mapOf("run" to listOf(6, 2, 4, 1))
        )
        val custom = TracksideCoaching.trainYourWay(previous, 80.0, setOf("Hills"))

        assertEquals(3, TracksideCoaching.runningGoalNextPage("custom"))
        assertEquals(1, TracksideCoaching.runningGoalNextPage("race"))
        assertEquals(5, TracksideCoaching.runningGoalNextPage("faster"))
        assertEquals("General fitness", custom.goal)
        assertFalse("run" in custom.disciplineGoals)
        assertEquals(42.0, custom.weeklyKm, 0.0)
        assertEquals(80.0, custom.targetWeeklyKm!!, 0.0)
        assertEquals(setOf("Hills"), custom.allowedQualityTypes)
        assertEquals(previous.weeklySlots, custom.weeklySlots)
        assertEquals(previous.qualityDays, custom.qualityDays)
        assertThrows(PlanConstraint::class.java) {
            TracksideCoaching.request(custom, engine15Active = false)
        }
        assertEquals("engine15",
            TracksideCoaching.request(custom, engine15Active = true).policyChoice)
    }

    @Test fun racePlanMileageAndGoalRemainOnExistingPath() {
        val race = request().copy(trainYourWay = false, targetWeeklyKm = null)
        val plan = Engine15.generate(race, "synthetic-owner", "race", now)

        assertEquals("Improve 10K", plan.request.goal)
        assertEquals("Improve 10K", plan.request.disciplineGoals["run"])
        assertEquals(null, plan.name)
        assertTrue(plan.sessions.filter { it.discipline == "run" }
            .zip(Engine1.generate(race.copy(policyChoice = "baseline"),
                "synthetic-owner", "race", now).sessions.filter { it.discipline == "run" })
            .all { (current, original) -> (current.distanceKm ?: 0.0) <= (original.distanceKm ?: 0.0) })
    }
}
