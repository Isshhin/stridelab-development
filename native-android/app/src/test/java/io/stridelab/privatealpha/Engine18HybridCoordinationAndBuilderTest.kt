package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class Engine18HybridCoordinationAndBuilderTest {
    private val owner = "engine18-hybrid-tester"
    private val now = Instant.parse("2026-11-27T00:00:00Z")
    private val monday = LocalDate.parse("2026-11-30")
    private val library by lazy {
        ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
    }

    @Test fun pol036BidirectionalInterferenceAndHighLowConcentrationWorkDeterministically() {
        val heavyRun = HybridInterferenceSession(
            id = "run-qual",
            date = monday,
            discipline = "run",
            isRunningQualityOrLong = true,
            isHeavyOrEccentricLowerResistance = false,
            isUpperAxialCompound = false,
            isHighStressConditioning = false,
            plannedStartUtc = monday.atTime(6, 0).toInstant(ZoneOffset.UTC),
            durationMaxSeconds = 3600L
        )
        val heavyLowerNextDay = HybridInterferenceSession(
            id = "str-heavy",
            date = monday.plusDays(1),
            discipline = "str",
            isRunningQualityOrLong = false,
            isHeavyOrEccentricLowerResistance = true,
            isUpperAxialCompound = false,
            isHighStressConditioning = false,
            workingSetsPerLowerPattern = 4,
            minRir = 2,
            plannedStartUtc = monday.plusDays(1).atTime(6, 0).toInstant(ZoneOffset.UTC),
            durationMaxSeconds = 3600L
        )
        // Bidirectional: within 48h conflicts regardless of which order
        assertTrue(Engine18HybridCoordinationEngine.evaluateBidirectionalInterference(heavyRun, heavyLowerNextDay).conflict)
        assertTrue(Engine18HybridCoordinationEngine.evaluateBidirectionalInterference(heavyLowerNextDay, heavyRun).conflict)

        // Same-day MED maintenance strength separated by 8 h succeeds only when High/Low enabled and recovery known
        val sameDayMaintStr = heavyLowerNextDay.copy(
            date = monday,
            workingSetsPerLowerPattern = 2,
            minRir = 3,
            plannedStartUtc = monday.atTime(16, 0).toInstant(ZoneOffset.UTC)
        )
        val permitted = Engine18HybridCoordinationEngine.evaluateBidirectionalInterference(
            heavyRun, sameDayMaintStr, allowHighLowConcentration = true, missingOrUnreportedRecovery = false
        )
        assertFalse(permitted.conflict)
        assertTrue(permitted.permittedViaHighLowConcentration)

        val blockedMissingRecovery = Engine18HybridCoordinationEngine.evaluateBidirectionalInterference(
            heavyRun, sameDayMaintStr, allowHighLowConcentration = true, missingOrUnreportedRecovery = true
        )
        assertTrue(blockedMissingRecovery.conflict)
        assertFalse(blockedMissingRecovery.permittedViaHighLowConcentration)
    }

    @Test fun s18FixedCommitmentConflictAndPol059NineAxisStressorLimitsEnforced() {
        // Fixed heavy lower on Mon (1) & Thu (4), asking for 2 hard runs on Mon..Sat
        val s18 = Engine18HybridCoordinationEngine.resolveFixedCommitmentConflict(
            fixedLowerStrengthWeekdays = setOf(1, 4),
            desiredHardRunsPerWeek = 2,
            availableWeekdays = setOf(1, 2, 3, 4, 5, 6)
        )
        assertTrue(s18.bidirectionalCollisionImpossible)
        assertEquals(setOf(6), s18.zeroConflictHardRunWeekdays)
        assertNotNull(s18.optionBPrimaryException)

        // POL-059: Correlated Axis 4 bundle (+7% load + high %1RM) counts ONCE + Run MAJOR cannot coexist?
        // Actually +7% load is >5% minor cap -> MAJOR (+1 lower, +1 systemic). Adding Run MAJOR -> 2 lower MAJOR (violation!)
        val doubleLowerViolation = Engine18HybridCoordinationEngine.evaluateMultiDisciplineTransition(
            HybridWeekTransitionInput(
                runTransition = RunningWeekTransitionInput(baseWeeklyMeters = 20_000.0, nextWeeklyMeters = 23_000.0), // +15% run volume -> MAJOR lower/systemic
                lowerStrengthLoadRelDelta = 0.07, // > 5% lower str -> MAJOR lower/systemic
                lowerStrengthMinRir = 2
            )
        )
        assertFalse(doubleLowerViolation.valid)
        assertEquals(2, doubleLowerViolation.lowerBodyMajorCount)

        // Run MAJOR + small upper strength step (Axis 6 minor) is strictly valid (only 1 major lower & 1 major systemic)
        val runMajorPlusUpperMinor = Engine18HybridCoordinationEngine.evaluateMultiDisciplineTransition(
            HybridWeekTransitionInput(
                runTransition = RunningWeekTransitionInput(baseWeeklyMeters = 20_000.0, nextWeeklyMeters = 23_000.0),
                upperAddedSets = 1,
                upperSmallStep = true
            )
        )
        assertTrue(runMajorPlusUpperMinor.valid)
        assertTrue(runMajorPlusUpperMinor.upperBodyMinorActive)
    }

    @Test fun md15TaperDropMd06UnifiedMuscleLedgerAndMd13ContinuityIsolation() {
        assertEquals(
            LowerStrengthTaperAction.DROP_HEAVY_LOWER_FOR_TAPER,
            Engine18HybridCoordinationEngine.shouldHoldOrDropLowerStrength(isRunningMajorStepWeek = false, daysToPrimaryRace = 10, raceGoal = "Marathon")
        )
        assertEquals(
            LowerStrengthTaperAction.HOLD_CONSOLIDATE_MAINTAIN,
            Engine18HybridCoordinationEngine.shouldHoldOrDropLowerStrength(isRunningMajorStepWeek = true, daysToPrimaryRace = 45, raceGoal = "10K")
        )

        val heavyQuadsLedger = UnifiedLocalMuscleLedger(
            accountId = owner,
            windowStartInclusive = monday,
            windowEndInclusive = monday.plusDays(6),
            isProjected = true,
            byMuscle = mapOf("quadriceps" to MuscleGroupExposure(muscleId = "quadriceps", directHardSets = 12))
        )
        val (merged, blocked) = Engine18HybridCoordinationEngine.aggregateWeeklyUnifiedLedger(
            owner, monday, monday.plusDays(6), listOf(heavyQuadsLedger)
        )
        assertTrue("quadriceps" in blocked)
        assertEquals(12, merged.byMuscle.getValue("quadriceps").directHardSets)

        val continuity = Engine18HybridCoordinationEngine.evaluatePerDisciplineContinuity(
            gapDaysByDiscipline = mapOf("run" to 21, "str" to 2),
            cyclingMinutesPerWeek = 300,
            runningWeeklyMeters = 0.0
        )
        assertTrue(continuity.regimeByDiscipline.getValue("run").startsWith("REBUILD_REENTRY"))
        assertEquals("ACTIVE_CONSISTENT", continuity.regimeByDiscipline.getValue("str"))
        assertNotNull(continuity.crossModalRunningTissueMismatchWarning)
    }

    @Test fun hybridProgramBuilderCoordinatesFourDisciplinesAndFeedsCoachingLoop() {
        val req = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            strengthExperience = 1,
            calLevel = 1,
            start = monday,
            weeks = 8,
            days = (1..7).toSet(),
            runDays = 3,
            strengthDays = 1,
            calDays = 1,
            conditioningDays = 1,
            longDay = 6,
            qualityCount = 1,
            weeklyKm = 28.0,
            longestKm = 12.0,
            targetWeeklyKm = 32.0,
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            timeBudgetEnabled = false,
            equipment = mapOf("db" to 2, "bench" to 1, "bands" to 1, "anchor" to 1, "erg" to 1),
            disciplineGoals = mapOf(
                "run" to "Improve 10K",
                "str" to "General strength",
                "cal" to "Push-up mastery",
                "cond" to "EMOM conditioning"
            ),
            policyChoice = "engine15"
        )
        val plan = Engine18HybridProgramBuilder.build(req, owner, "hybrid-4d-test", now, emptyList(), library)
        assertEquals(ENGINE18_VERSION, plan.engineVersion)
        assertEquals(ENGINE18_HYBRID_POLICY, plan.policyId)
        assertEquals(48, plan.sessions.size)
        assertEquals(setOf("run", "str", "cal", "cond"), plan.sessions.map { it.discipline }.toSet())
        assertTrue(Engine1Engines.supportsCoachingLoop(plan))

        Engine18HybridProgramBuilder.validateSaved(plan)
        val approved = PlanOperations.approve(plan, emptyList())
        val decoded = PlanSnapshot.decode(approved.encode(), owner)
        Engine15IndependentPlanGenerator.validateSaved(decoded)

        // Verify HybridPlanBuilder shared muscle ledger note integration works cleanly across tracks
        val tracks = HybridPlanBuilder.split(
            req.copy(calDays = 0, conditioningDays = 0, timeBudgetEnabled = false),
            extraResistance = emptySet()
        )
        val preview = HybridPlanBuilder.build("hp-shared-test", owner, tracks, emptyList()) { trackReq, peers ->
            Engine15IndependentPlanGenerator.generateWithHistory(trackReq, owner, peers, "sub-${trackReq.hashCode()}", now, library)
        }
        assertEquals(2, preview.drafts.size)
    }
}
