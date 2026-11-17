package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import org.junit.Assert.*
import org.junit.Test

class Engine15IndependentPlanGeneratorTest {
    private val owner = "independent-synthetic"
    private val now = Instant.parse("2026-11-17T00:00:00Z")

    private fun request(weeks: Int = 8) = PlanRequest(
        goal = "Aerobic foundation",
        start = LocalDate.parse("2026-11-16"),
        weeks = weeks,
        days = setOf(1, 2, 3, 5, 6, 7),
        runDays = 6,
        weeklyKm = 75.0,
        longestKm = 20.0,
        targetWeeklyKm = 80.0,
        longDay = 6,
        timeBudgetEnabled = false,
        policyChoice = "engine15",
        recentMileageBasis = "SELF_REPORTED_AVERAGE"
    )

    @Test fun assemblesCompleteEightWeekSaturdayLongSundayEasyCandidate() {
        val decision = Engine15IndependentPlanGenerator.generate(request(), owner, "candidate", now)
        assertTrue("Expected candidate, got $decision", decision is Engine15IndependentPlanDecision.Candidate)
        val candidate = decision as Engine15IndependentPlanDecision.Candidate
        val plan = candidate.plan
        assertEquals(48, plan.sessions.size)
        assertEquals(8, candidate.periodization.weeks.size)
        for (week in 0 until 8) {
            val start = request().start.plusWeeks(week.toLong())
            val sessions = plan.sessions.filter { it.date >= start && it.date < start.plusWeeks(1) }
            assertEquals(6, sessions.size)
            assertEquals(candidate.volume.weeks[week].proposedKm!!,
                sessions.sumOf { it.distanceKm!! }, 1e-6)
            assertEquals(6, sessions.single { it.kind == "long" }.date.dayOfWeek.value)
            assertEquals("easy", sessions.single { it.date.dayOfWeek.value == 7 }.kind)
        }
        assertFalse(candidate.executable)
        assertFalse(candidate.authorizedForProduction)
        assertTrue(plan.sessions.all { it.quality.not() && it.runningPrescription?.family == null })
        assertEquals(plan, Engine15IndependentPlanGenerator.generate(request(), owner, "candidate", now).let {
            (it as Engine15IndependentPlanDecision.Candidate).plan
        })
    }

    @Test fun defersQualityRequestsInsteadOfDroppingOrSubstitutingThem() {
        for (qualityCount in 1..2) {
            val decision = Engine15IndependentPlanGenerator.generate(
                request().copy(qualityCount = qualityCount), owner, "quality-$qualityCount", now
            )
            assertTrue(decision is Engine15IndependentPlanDecision.Deferred)
            assertEquals("QUALITY_GENERATION_NOT_QUALIFIED",
                (decision as Engine15IndependentPlanDecision.Deferred).reasonCode)
        }
    }

    @Test fun defersMissingOrUnclassifiedTrainingHistory() {
        for (input in listOf(
            request().copy(weeklyKm = 0.0, recentMileageBasis = "UNKNOWN"),
            request().copy(recentMileageBasis = "LEGACY_UNSPECIFIED")
        )) {
            assertTrue(Engine15IndependentPlanGenerator.generate(input, owner, "history", now)
                is Engine15IndependentPlanDecision.Deferred)
        }
    }

    @Test fun incompleteRecordedHistoryCannotBecomeTheWeeklyMileageBaseline() {
        val decision = Engine15IndependentPlanGenerator.generate(
            request().copy(recentMileageBasis = "RECORDED_PARTIAL"), owner, "partial", now
        )
        assertTrue(decision is Engine15IndependentPlanDecision.Deferred)
        assertEquals("ATHLETE_BASELINE_NEEDS_BASELINE",
            (decision as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test fun conflictingTrainingHistoryIsDeferredForReview() {
        val decision = Engine15IndependentPlanGenerator.generate(
            request().copy(reportedPeakWeeklyKm = 60.0), owner, "conflict", now
        )
        assertTrue(decision is Engine15IndependentPlanDecision.Deferred)
        assertEquals("ATHLETE_BASELINE_NEEDS_REVIEW",
            (decision as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test fun beginnerIntermediateAndAdvancedProfilesUseReportedInputsWithoutInventedQuality() {
        for (experience in 0..2) {
            val decision = Engine15IndependentPlanGenerator.generate(
                request().copy(experience = experience, returning = experience == 0),
                owner, "experience-$experience", now
            )
            assertTrue("experience=$experience: $decision", decision is Engine15IndependentPlanDecision.Candidate)
            val candidate = decision as Engine15IndependentPlanDecision.Candidate
            assertTrue(candidate.plan.sessions.none { it.quality })
            assertFalse(candidate.plan.explanations.any { it.contains("verified athlete response", ignoreCase = true) })
        }
    }

    @Test fun assemblesSixteenWeeksWithFourRecoveryCyclesAndExactWorkoutExtent() {
        val input = request(16).copy(planningPace = 360.0)
        val decision = Engine15IndependentPlanGenerator.generate(input, owner, "sixteen", now)
        assertTrue(decision is Engine15IndependentPlanDecision.Candidate)
        val candidate = decision as Engine15IndependentPlanDecision.Candidate
        assertEquals(listOf(4, 8, 12, 16), candidate.periodization.weeks
            .filter { it.phase == Engine15M6DPhase.RECOVERY }.map { it.weekNumber })
        assertEquals(96, candidate.plan.sessions.size)
        candidate.plan.sessions.forEach { session ->
            assertEquals(session.distanceKm!! * 1000.0,
                session.runningPrescription!!.meters.toDouble(), 1e-6)
            assertEquals(ceil(session.distanceKm * input.planningPace / 60.0).toInt(),
                session.minutes)
            assertTrue(session.runningPrescription!!.segments.all { it.purpose == "EASY" })
        }
        for (week in 0 until 16) {
            val sessions = candidate.plan.sessions.filter {
                it.date >= input.start.plusWeeks(week.toLong()) &&
                    it.date < input.start.plusWeeks((week + 1).toLong())
            }
            val total = candidate.volume.weeks[week].proposedKm!!
            val long = sessions.single { it.kind == "long" }.distanceKm!!
            assertTrue(long <= total * 0.40 + 1e-6)
            assertTrue(long <= input.longestKm + 1.0 + 1e-6)
            assertEquals(total, sessions.sumOf { it.distanceKm!! }, 1e-6)
        }
    }

    @Test fun independentPreviewDoesNotBecomeAnExecutableSavedPlan() {
        val candidate = Engine15IndependentPlanGenerator.generate(request(), owner, "readonly", now)
            as Engine15IndependentPlanDecision.Candidate
        val decoded = PlanSnapshot.decode(candidate.plan.encode(), owner)
        assertNotNull(decoded.foreignSnapshot)
        assertFalse(Engine1Engines.canManage(decoded))
        assertEquals(candidate.plan.sessions, decoded.sessions)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PlanSnapshot.decode(candidate.plan.encode(), "different-owner")
        }
    }

    @Test fun defersPersistentRecoveryHoldAndUnsupportedMixedDisciplineRequests() {
        val hold = Engine15IndependentPlanGenerator.generate(
            request().copy(runningRecoveryHold = true), owner, "hold", now
        )
        assertTrue(hold is Engine15IndependentPlanDecision.Deferred)
        assertEquals("RUNNING_RECOVERY_HOLD", (hold as Engine15IndependentPlanDecision.Deferred).reasonCode)

        val mixed = Engine15IndependentPlanGenerator.generate(
            request().copy(calDays = 1), owner, "mixed", now
        )
        assertTrue(mixed is Engine15IndependentPlanDecision.Deferred)
        assertEquals("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT",
            (mixed as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test fun arbitraryStartDateAndExactSessionLockAreHonored() {
        val input = request().copy(start = LocalDate.parse("2026-11-17"))
        val shifted = Engine15IndependentPlanGenerator.generate(input, owner, "shifted", now)
        assertTrue("Expected candidate, got $shifted", shifted is Engine15IndependentPlanDecision.Candidate)
        val sessions = (shifted as Engine15IndependentPlanDecision.Candidate).plan.sessions
        assertTrue(sessions.all { it.date >= input.start && it.date < input.start.plusWeeks(input.weeks.toLong()) })

        val lockedId = "locked-w1-run1"
        val lockedInput = input.copy(lockedSessionDays = mapOf(lockedId to "2026-11-18"))
        val locked = Engine15IndependentPlanGenerator.generate(lockedInput, owner, "locked", now)
        assertTrue("Expected locked candidate, got $locked", locked is Engine15IndependentPlanDecision.Candidate)
        assertEquals(LocalDate.parse("2026-11-18"),
            (locked as Engine15IndependentPlanDecision.Candidate).plan.sessions.single { it.id == lockedId }.date)
    }

    @Test fun defersDoubleDaysAndUnmappableSessionLocks() {
        val doubleDays = Engine15IndependentPlanGenerator.generate(
            request().copy(allowDoubleDays = true), owner, "double", now
        )
        assertTrue(doubleDays is Engine15IndependentPlanDecision.Deferred)
        assertEquals("DOUBLE_DAYS_UNSUPPORTED", (doubleDays as Engine15IndependentPlanDecision.Deferred).reasonCode)

        val locked = Engine15IndependentPlanGenerator.generate(
            request().copy(lockedSessionDays = mapOf("unknown-session" to "2026-11-16")),
            owner, "candidate", now
        )
        assertTrue(locked is Engine15IndependentPlanDecision.Deferred)
        assertEquals("SESSION_LOCK_MAPPING_UNSUPPORTED",
            (locked as Engine15IndependentPlanDecision.Deferred).reasonCode)
        val staleLock = Engine15IndependentPlanGenerator.generate(
            request().copy(lockedSessionDays = mapOf("previous-w9-run1" to "2026-11-16")),
            owner, "candidate", now
        )
        assertTrue(staleLock is Engine15IndependentPlanDecision.Deferred)
        assertEquals("SESSION_LOCK_MAPPING_UNSUPPORTED",
            (staleLock as Engine15IndependentPlanDecision.Deferred).reasonCode)

        val tightTime = Engine15IndependentPlanGenerator.generate(
            request().copy(timeBudgetEnabled = true, minutes = 45), owner, "time", now
        )
        assertTrue(tightTime is Engine15IndependentPlanDecision.Deferred)
        assertEquals("DISTANCE_TIME_BUDGET_CONFLICT",
            (tightTime as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test fun independentGenerationCallGraphContainsNoLegacyPlannerCalls() {
        val root = java.io.File("src/main/java/io/stridelab/privatealpha")
        val files = listOf(
            "Engine15IndependentPlanGenerator.kt",
            "Engine15AthleteCapacity.kt",
            "Engine15IndependentVolumePlanner.kt",
            "Engine15M5WorkoutComposer.kt",
            "Engine15M5QualityPreviewComposer.kt",
            "Engine15WeeklyVolumeSkeleton.kt",
            "Engine15M6IndependentCalendar.kt",
            "Engine15M6IndependentInterferenceReview.kt",
            "Engine15M6IndependentPeriodizationReview.kt",
            "Engine15M6IndependentQualitySpacing.kt"
        )
        val forbidden = Regex("\\b(?:Engine1\\.(?:generate|validate)|MultiSessionEngine1|CoordinatedEngine1|Engine15InterferenceScheduling)\\b")
        for (file in files) {
            val source = java.io.File(root, file).readText()
            assertFalse("Legacy planning dependency in $file", forbidden.containsMatchIn(source))
        }
    }
}
