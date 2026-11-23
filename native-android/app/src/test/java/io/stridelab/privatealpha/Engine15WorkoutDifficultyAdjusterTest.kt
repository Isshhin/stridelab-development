package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15WorkoutDifficultyAdjusterTest {
    private val owner = "difficulty-athlete"
    private val now = Instant.parse("2026-11-21T08:00:00Z")
    private val monday = LocalDate.parse("2026-11-23")
    private val library by lazy {
        ExerciseLibraryV2.load { path -> File("src/main/assets", path).inputStream() }
    }

    private fun intervalRunningDraft(
        goal: String = "Improve 10K",
        qualityTypes: Set<String> = setOf("Threshold")
    ): PlanSnapshot {
        val req = PlanRequest(
            goal = goal,
            experience = 2,
            start = monday,
            weeks = 4,
            days = setOf(1, 2, 4, 6),
            runDays = 4,
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            allowedQualityTypes = qualityTypes,
            weeklyKm = 40.0,
            targetWeeklyKm = 40.0,
            longestKm = 14.0,
            planningPace = 390.0,
            timeBudgetEnabled = false,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            userDirectedPlanning = true
        )
        return Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "diff-$goal", now
        )
    }

    @Test
    fun qualityIntervalWorkoutScalesAcrossAllStepsConvertsAtMinusTwoAndResetsCleanly() {
        val draft = intervalRunningDraft("Improve 10K", setOf("Threshold"))
        var plan = draft
        val qId = draft.sessions.first { it.quality }.id
        val orig = draft.sessions.single { it.id == qId }
        val origReps = orig.runningPrescription!!.mixedSegments!!.count { it.purpose == "WORK" }

        // +1 Harder -> adds rep + scales distance
        plan = PlanOperations.adjustDifficulty(plan, qId, +1)
        var cur = plan.sessions.single { it.id == qId }
        assertEquals(1, cur.difficultyStep)
        assertTrue(cur.quality)
        assertTrue(cur.runningPrescription!!.mixedSegments!!.count { it.purpose == "WORK" } >= origReps)
        assertTrue(cur.distanceKm!! >= orig.distanceKm!!)
        Engine15IndependentPlanGenerator.validateSaved(plan)

        // +2 Much harder -> further step up
        plan = PlanOperations.adjustDifficulty(plan, qId, +1)
        cur = plan.sessions.single { it.id == qId }
        assertEquals(2, cur.difficultyStep)
        Engine15IndependentPlanGenerator.validateSaved(plan)

        // Step down to -2 -> converts to easy recovery run while preserving baseline
        plan = PlanOperations.adjustDifficulty(plan, qId, -1) // +1
        plan = PlanOperations.adjustDifficulty(plan, qId, -1) // 0
        plan = PlanOperations.adjustDifficulty(plan, qId, -1) // -1
        cur = plan.sessions.single { it.id == qId }
        assertEquals(-1, cur.difficultyStep)
        assertTrue(cur.quality)
        assertTrue(cur.distanceKm!! <= orig.distanceKm!!)
        Engine15IndependentPlanGenerator.validateSaved(plan)

        plan = PlanOperations.adjustDifficulty(plan, qId, -1) // -2
        cur = plan.sessions.single { it.id == qId }
        assertEquals(-2, cur.difficultyStep)
        assertFalse(cur.quality)
        assertEquals("Recovery run (eased)", cur.title)
        assertEquals("RECOVERY", cur.runningPrescription?.role)
        assertNotNull(cur.difficultyBaseline)

        // Approve a plan whose ONLY quality session is eased to -2 -> validateSaved must pass!
        val singleQReq = draft.request.copy(weeks = 1, qualityDistanceClaims = emptyList())
        val oneWeek = Engine15IndependentPlanGenerator.generateWithHistory(
            singleQReq, owner, emptyList(), "one-q-eased", now
        )
        val oneQId = oneWeek.sessions.single { it.quality }.id
        val easedOne = PlanOperations.adjustDifficulty(
            PlanOperations.adjustDifficulty(oneWeek, oneQId, -1), oneQId, -1
        )
        val approvedEased = PlanOperations.approve(easedOne, emptyList())
        Engine15IndependentPlanGenerator.validateSaved(approvedEased)

        // Reset restores exact original quality session cleanly
        val reset = PlanOperations.adjustDifficulty(plan, qId, 0)
        assertEquals(orig, reset.sessions.single { it.id == qId })
        Engine15IndependentPlanGenerator.validateSaved(reset)
    }

    @Test
    fun continuousTempoAndEasyLongRunsScaleCleanlyAndRoundTripThroughJsonAndBackup() {
        val draft = intervalRunningDraft("Marathon", setOf("Tempo"))
        val tempoId = draft.sessions.first { it.quality }.id
        val longId = draft.sessions.first { it.kind == "long" }.id
        val easyId = draft.sessions.first { !it.quality && it.kind == "easy" }.id

        val origTempo = draft.sessions.single { it.id == tempoId }
        val origLong = draft.sessions.single { it.id == longId }
        val origEasy = draft.sessions.single { it.id == easyId }

        var edited = PlanOperations.adjustDifficulty(draft, tempoId, +1)
        edited = PlanOperations.adjustDifficulty(edited, longId, -1)
        edited = PlanOperations.adjustDifficulty(edited, easyId, -2)
        val approved = PlanOperations.approve(edited, emptyList())

        val adjTempo = approved.sessions.single { it.id == tempoId }
        val adjLong = approved.sessions.single { it.id == longId }
        val adjEasy = approved.sessions.single { it.id == easyId }

        assertTrue(adjTempo.minutes >= origTempo.minutes)
        assertTrue(adjLong.distanceKm!! < origLong.distanceKm!!)
        assertTrue(adjEasy.distanceKm!! < origEasy.distanceKm!!)
        assertNotNull(Engine15WorkoutDifficultyAdjuster.changeSummary(adjTempo))
        Engine15IndependentPlanGenerator.validateSaved(approved)

        // JSON & PlanBackup round-trip fidelity
        val decoded = PlanSnapshot.decode(approved.encode(), owner)
        assertEquals(approved, decoded)
        val backupJson = PlanBackup.encode(listOf(approved), emptyList(), owner)
        val restored = PlanBackup.readBundle(backupJson, owner).plans.single()
        assertEquals(approved, restored)
    }

    @Test
    fun strengthCalisthenicsAndConditioningAdjustAndValidateSaved() {
        val strReq = PlanRequest(
            goal = "General strength",
            strengthExperience = 1,
            start = monday,
            weeks = 4,
            days = setOf(1, 3, 5),
            runDays = 0,
            strengthDays = 2,
            equipment = mapOf("db" to 2, "bench" to 1),
            policyChoice = "engine15",
            timeBudgetEnabled = false
        )
        val strDraft = Engine15IndependentPlanGenerator.generateWithHistory(
            strReq, owner, emptyList(), "diff-str", now, exerciseLibrary = library
        )
        val strId = strDraft.sessions.first().id
        val strHard = PlanOperations.adjustDifficulty(
            PlanOperations.adjustDifficulty(strDraft, strId, +1), strId, +1
        )
        val approvedStr = PlanOperations.approve(strHard, emptyList())
        assertTrue(approvedStr.sessions.first().exercises.first().sets > strDraft.sessions.first().exercises.first().sets)
        Engine15IndependentPlanGenerator.validateSaved(approvedStr)

        val calReq = PlanRequest(
            goal = "Core strength and control",
            experience = 1,
            start = monday,
            weeks = 4,
            days = setOf(1, 3, 5),
            runDays = 0,
            calDays = 2,
            policyChoice = "engine15",
            timeBudgetEnabled = false
        )
        val calDraft = Engine15IndependentPlanGenerator.generateWithHistory(
            calReq, owner, emptyList(), "diff-cal", now, exerciseLibrary = library
        )
        val calId = calDraft.sessions.first().id
        val calEased = PlanOperations.adjustDifficulty(
            PlanOperations.adjustDifficulty(calDraft, calId, -1), calId, -1
        )
        val approvedCal = PlanOperations.approve(calEased, emptyList())
        assertTrue(approvedCal.sessions.first().exercises.all { it.sets >= 1 })
        Engine15IndependentPlanGenerator.validateSaved(approvedCal)

        val condReq = PlanRequest(
            goal = "Conditioning",
            start = monday,
            weeks = 4,
            days = setOf(1, 3, 5),
            runDays = 0,
            conditioningDays = 2,
            policyChoice = "engine15",
            timeBudgetEnabled = false
        )
        val condDraft = Engine15IndependentPlanGenerator.generateWithHistory(
            condReq, owner, emptyList(), "diff-cond", now, exerciseLibrary = library
        )
        val condId = condDraft.sessions.first().id
        val condHarder = PlanOperations.adjustDifficulty(condDraft, condId, +1)
        val approvedCond = PlanOperations.approve(condHarder, emptyList())
        assertTrue(approvedCond.sessions.first().minutes > condDraft.sessions.first().minutes)
        Engine15IndependentPlanGenerator.validateSaved(approvedCond)
    }

    @Test
    fun combinedPlanEnforcesDailyMinutesOnDifficultyIncreaseOnlyWhenTimeBudgetEnabled() {
        val stackedBase = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = monday,
            weeks = 2,
            days = setOf(1, 3, 6),
            runDays = 3,
            strengthDays = 2,
            longDay = 6,
            weeklySlots = mapOf("run" to listOf(6, 1, 3), "str" to listOf(1, 3)),
            dailyMinutes = 80,
            weeklyKm = 24.0,
            targetWeeklyKm = 24.0,
            longestKm = 10.0,
            planningPace = 420.0,
            minutes = 60,
            equipment = mapOf("db" to 2, "bench" to 1),
            disciplineGoals = mapOf("run" to "Improve 10K", "str" to "General strength"),
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            timeBudgetEnabled = false
        )
        val freeCombined = Engine15IndependentPlanGenerator.generateWithHistory(
            stackedBase, owner, emptyList(), "comb-free", now, exerciseLibrary = library
        )
        val stackedStrId = freeCombined.sessions.first { it.discipline == "str" }.id
        val approvedFree = PlanOperations.approve(
            PlanOperations.adjustDifficulty(
                PlanOperations.adjustDifficulty(freeCombined, stackedStrId, +1),
                stackedStrId, +1
            ),
            emptyList()
        )
        Engine15IndependentPlanGenerator.validateSaved(approvedFree)

        val peakStackedDay = freeCombined.sessions
            .groupBy { it.date }
            .filterValues { day -> day.any { it.discipline == "str" } }
            .maxBy { (_, day) -> day.sumOf { it.effectiveMinutes } }
        val baselineDayMinutes = peakStackedDay.value.sumOf { it.effectiveMinutes }
        val strictReq = stackedBase.copy(timeBudgetEnabled = true, minutes = 60, dailyMinutes = baselineDayMinutes + 1)
        val strictCombined = Engine15IndependentPlanGenerator.generateWithHistory(
            strictReq, owner, emptyList(), "comb-strict", now, exerciseLibrary = library
        )
        val strictStrId = strictCombined.sessions.first {
            it.date == peakStackedDay.key && it.discipline == "str"
        }.id
        val error = assertThrows(PlanConstraint::class.java) {
            PlanOperations.adjustDifficulty(
                PlanOperations.adjustDifficulty(strictCombined, strictStrId, +1),
                strictStrId, +1
            )
        }
        assertTrue(error.message.orEmpty().contains("daily time budget"))
    }
}
