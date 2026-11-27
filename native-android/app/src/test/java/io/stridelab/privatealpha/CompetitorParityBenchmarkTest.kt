package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

class CompetitorParityBenchmarkTest {

    private val fixedNow: Instant = Instant.parse("2026-11-27T08:00:00Z")
    private val fixedStart: LocalDate = LocalDate.of(2026, 11, 30)
    private val owner = "competitor-parity-bench"
    private val hrFloors = listOf(115, 138, 154, 168, 178)
    private val fullGym = mapOf(
        "db" to 1, "bb" to 1, "rack" to 1, "bench" to 1,
        "cable" to 1, "machine" to 1, "kb" to 1, "bands" to 1,
        "bar" to 1, "rings" to 1, "wall" to 1, "dip_belt" to 1
    )

    @Test
    fun benchmarkStrideLabAgainstRunnaGarminFitbodHevyAndFreeletics() {
        val violations = mutableListOf<String>()
        val scorecard = mutableListOf<String>()

        // =========================================================================
        // TRACK 1: Running vs. Runna & Garmin Run Coach (Pace + HR)
        // =========================================================================
        run {
            val t1Violations = mutableListOf<String>()
            val runProfiles = listOf(
                "HM_Pace_12w" to PlanRequest(
                    goal = "Half marathon", experience = 1, start = fixedStart, weeks = 12,
                    minutes = 150, days = setOf(1, 2, 4, 5, 7), runDays = 5, longDay = 7,
                    qualityCount = 2, qualityDays = setOf(2, 4), weeklyKm = 52.0, longestKm = 16.0,
                    targetWeeklyKm = 64.0, recentMileageBasis = "SELF_REPORTED_AVERAGE", planningPace = 315.0
                ),
                "HM_HR_12w" to PlanRequest(
                    goal = "Half marathon", experience = 1, start = fixedStart, weeks = 12,
                    minutes = 150, days = setOf(1, 2, 4, 5, 7), runDays = 5, longDay = 7,
                    qualityCount = 2, qualityDays = setOf(2, 4), weeklyKm = 50.0, longestKm = 15.0,
                    targetWeeklyKm = 60.0, recentMileageBasis = "SELF_REPORTED_AVERAGE", planningPace = 320.0,
                    runningTargetMode = "HR", hrZoneFloors = hrFloors, hrMaxBpm = 192
                ),
                "Marathon_Pace_14w" to PlanRequest(
                    goal = "Marathon", experience = 2, start = fixedStart, weeks = 14,
                    minutes = 210, days = setOf(1, 2, 3, 4, 5, 7), runDays = 6, longDay = 7,
                    qualityCount = 2, qualityDays = setOf(2, 5), weeklyKm = 75.0, longestKm = 22.0,
                    targetWeeklyKm = 90.0, recentMileageBasis = "SELF_REPORTED_AVERAGE", planningPace = 295.0
                )
            )
            for ((label, req) in runProfiles) {
                val plan = Engine15IndependentPlanGenerator.generateWithHistory(req, owner, emptyList(), "cp-$label", fixedNow)
                Engine15IndependentPlanGenerator.validateSaved(plan)
                val qualitySessions = plan.sessions.filter { it.quality }
                val archetypes = qualitySessions.mapNotNull { it.runningPrescription?.archetypeId }.toSet()
                if (archetypes.size < 2) t1Violations += "$label: fewer than 2 quality archetypes ($archetypes)"
                val longTitles = plan.sessions.filter { it.kind == "long" }.map { it.title }
                val hasBlockLong = longTitles.any { "Blocks Long Run" in it }
                val hasContinuousOrFastFinish = longTitles.any { "Pace Long Run" in it || "Fast-Finish" in it }
                if (!hasBlockLong) t1Violations += "$label: missing alternating race-pace block long run ($longTitles)"
                if (!hasContinuousOrFastFinish) t1Violations += "$label: missing fast-finish / continuous race-pace long run"
                if (plan.sessions.none { it.title == "Easy Run + Strides" }) {
                    t1Violations += "$label: missing Easy Run + Strides"
                }
                // Check FIT export on alternating block long run
                val fitCandidate = plan.sessions.firstOrNull { "Blocks Long Run" in it.title }
                val fitBytes = fitCandidate?.let {
                    runCatching {
                        encodeRunningWorkoutForWatch(
                            title = it.title,
                            session = it,
                            target = Engine15SessionPresentation.Target(
                                state = Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY,
                                currentMinutes = it.minutes,
                                currentDistanceTargetKm = it.distanceKm,
                                currentSteps = it.steps,
                                originalMinutes = it.minutes,
                                originalDistanceKm = it.distanceKm,
                                disclosure = null
                            ),
                            createdAt = fixedNow,
                            verifiedZones = null
                        )
                    }.getOrNull()
                }
                if (fitBytes == null || fitBytes.isEmpty()) {
                    t1Violations += "$label: alternating block long run failed FIT export"
                }
            }
            val status = if (t1Violations.isEmpty()) "PASS" else "FAIL(${t1Violations.size})"
            scorecard += "Track 1 | Running vs Runna & Garmin Coach   | $status | 3 plans (HM Pace, HM HR, Marathon Pace)"
            violations += t1Violations.map { "[Track 1] $it" }
        }

        // =========================================================================
        // TRACK 2: Strength vs. Fitbod & Hevy
        // =========================================================================
        run {
            val t2Violations = mutableListOf<String>()
            val catalogById = Engine16StrengthExerciseCatalog.allMetadata(null).associateBy { it.id }
            val strProfiles = listOf(
                "4d_UL" to PlanRequest(
                    goal = "Muscle development", start = fixedStart, weeks = 8, minutes = 75,
                    days = setOf(1, 2, 4, 5), runDays = 0, strengthDays = 4, strengthExperience = 1,
                    equipment = fullGym, timeBudgetEnabled = true, policyChoice = "engine15"
                ),
                "5d_DB_ULPPL" to PlanRequest(
                    goal = "Muscle development", start = fixedStart, weeks = 8, minutes = 60,
                    days = setOf(1, 2, 3, 5, 6), runDays = 0, strengthDays = 5, strengthExperience = 1,
                    equipment = mapOf("db" to 1, "bench" to 1), timeBudgetEnabled = true, policyChoice = "engine15"
                ),
                "6d_PPL" to PlanRequest(
                    goal = "Muscle development", start = fixedStart, weeks = 8, minutes = 75,
                    days = setOf(1, 2, 3, 4, 5, 6), runDays = 0, strengthDays = 6, strengthExperience = 2,
                    equipment = fullGym, timeBudgetEnabled = true, policyChoice = "engine15"
                )
            )
            for ((label, req) in strProfiles) {
                val res = Engine16StrengthProgramBuilder.build(req, owner, "cp-$label", fixedNow)
                Engine16StrengthProgramBuilder.validateSaved(res.snapshot)
                val byWeek = res.snapshot.sessions.chunked(req.strengthDays)
                // Hevy continuity: W1 & W2 track identical core exercise IDs (zero randomizer churn)
                val w1Ids = byWeek[0].map { s -> s.exercises.map { it.id } }
                val w2Ids = byWeek[1].map { s -> s.exercises.map { it.id } }
                if (w1Ids != w2Ids) {
                    t2Violations += "$label: W1 vs W2 exercise churn detected (anti-Hevy randomizer)"
                }
                // Fitbod MRV protection: <= 22 direct hard sets per muscle per week
                val w1MuscleSets = linkedMapOf<String, Int>()
                byWeek[0].flatMap { it.exercises }.forEach { ex ->
                    catalogById[ex.id]?.primaryMuscles?.forEach { m ->
                        w1MuscleSets[m] = (w1MuscleSets[m] ?: 0) + ex.sets
                    }
                }
                w1MuscleSets.filterValues { it > 22 }.forEach { (m, sets) ->
                    t2Violations += "$label: $m weekly direct sets=$sets > 22 MRV"
                }
            }
            val status = if (t2Violations.isEmpty()) "PASS" else "FAIL(${t2Violations.size})"
            scorecard += "Track 2 | Strength vs Fitbod & Hevy         | $status | 3 plans (4d UL, 5d DB, 6d PPL)"
            violations += t2Violations.map { "[Track 2] $it" }
        }

        // =========================================================================
        // TRACK 3: Calisthenics vs. Freeletics Coach (Tempo + Live DAG Adaptation)
        // =========================================================================
        run {
            val t3Violations = mutableListOf<String>()
            val hsReq = PlanRequest(
                goal = "Handstand foundations", start = fixedStart, weeks = 8,
                days = setOf(1, 2, 4, 5), runDays = 0, calDays = 4,
                calLevel = 1, pullLevel = 1, coreLevel = 1, minutes = 45,
                timeBudgetEnabled = true, equipment = fullGym, policyChoice = "engine15"
            )
            val hsPlan = Engine17CalisthenicsProgramBuilder.build(hsReq, owner, "cp-cal-hs", fixedNow).snapshot
                .copy(status = "approved")
            Engine17CalisthenicsProgramBuilder.validateSaved(hsPlan)
            // Verify Freeletics tempo cues & Fresh Skill FIRST
            val firstSession = hsPlan.sessions.first()
            if (firstSession.exercises.firstOrNull()?.cue?.contains("[Skill") != true) {
                t3Violations += "Handstand missing Fresh Skill first"
            }
            if (hsPlan.sessions.flatMap { it.exercises }.none { "tempo" in it.cue.lowercase(Locale.US) }) {
                t3Violations += "Calisthenics missing structured tempo cues"
            }
            // Live execution-driven Calisthenics progression (+1 on clean control, -1 on strain/discomfort)
            val s0 = hsPlan.sessions[0]
            val s1 = hsPlan.sessions[1]
            fun confirmClean(session: PlannedSession, effort: Int, discomfort: String): Pair<WorkoutExecution, WorkoutLog> {
                val ex = WorkoutExecution.create("exec-${session.id}", hsPlan.id, session, "fin-${session.id}")
                val rows = ex.sets.map { s ->
                    s.confirmed(SetActual(reps = s.target.reps, kg = null, seconds = s.target.seconds, rir = 2))
                }
                val log = WorkoutLog(
                    fixedNow, session.minutes.toDouble(), null, effort,
                    rows.map { SetRecord(it.target.id, it.number, it.actual!!.reps, null, it.actual.seconds) },
                    "Clean execution", session.date, discomfort = discomfort
                )
                return ex.copy(
                    sets = rows, phase = PlayerPhase.Completed,
                    lifecycle = ExecutionLifecycle.FINISHED_NORMALLY, receipt = true, finalLog = log
                ) to log
            }
            val (exec0, log0) = confirmClean(s0, effort = 6, discomfort = "None")
            val (exec1, log1) = confirmClean(s1, effort = 6, discomfort = "None")
            val completed2 = hsPlan.copy(
                sessions = listOf(
                    s0.copy(status = "completed", log = log0),
                    s1.copy(status = "completed", log = log1)
                ) + hsPlan.sessions.drop(2)
            )
            val receiptsClean = mapOf(s0.id to exec0, s1.id to exec1)
            val propUp = Engine15PlanAdaptationEngine.evaluateCalisthenicsExecutionProgression(
                completed2, emptyList(), s1.date
            ) { receiptsClean[it.id] }
            if (propUp == null || propUp.type != AdaptationType.CALISTHENICS_PROGRESSION) {
                t3Violations += "Expected CALISTHENICS_PROGRESSION (+1) after 2 clean sessions, got $propUp"
            } else {
                val applied = Engine15PlanAdaptationEngine.applyProposal(completed2, propUp, s1.date)
                val restored = PlanSnapshot.decode(JSONObject(applied.appliedPlan.encode().toString()), owner)
                Engine15IndependentPlanGenerator.validateSaved(restored)
                if (restored.sessions.none { it.difficultyStep == 1 }) {
                    t3Violations += "Applied CALISTHENICS_PROGRESSION (+1) did not advance target session"
                }
            }
            val (execStrain, logStrain) = confirmClean(s1, effort = 9, discomfort = "Pain")
            val strainedPlan = hsPlan.copy(
                sessions = listOf(s0, s1.copy(status = "completed", log = logStrain)) + hsPlan.sessions.drop(2)
            )
            val propDown = Engine15PlanAdaptationEngine.evaluateCalisthenicsExecutionProgression(
                strainedPlan, emptyList(), s1.date
            ) { execStrain.takeIf { r -> r.sessionId == it.id } }
            if (propDown == null || propDown.type != AdaptationType.CALISTHENICS_PROGRESSION) {
                t3Violations += "Expected CALISTHENICS_PROGRESSION (-1) regression on discomfort"
            } else {
                val appliedDown = Engine15PlanAdaptationEngine.applyProposal(strainedPlan, propDown, s1.date)
                val restoredDown = PlanSnapshot.decode(JSONObject(appliedDown.appliedPlan.encode().toString()), owner)
                Engine15IndependentPlanGenerator.validateSaved(restoredDown)
                if (restoredDown.sessions.none { it.difficultyStep == -1 }) {
                    t3Violations += "Applied CALISTHENICS_PROGRESSION (-1) did not ease upcoming session"
                }
            }

            val status = if (t3Violations.isEmpty()) "PASS" else "FAIL(${t3Violations.size})"
            scorecard += "Track 3 | Calisthenics vs Freeletics Coach    | $status | Tempo + FreshSkill + Live +/-1 DAG"
            violations += t3Violations.map { "[Track 3] $it" }
        }

        // =========================================================================
        // TRACK 4: Engine 1.8 Solo Conditioning (8 Families x Equipment x Budgets)
        // =========================================================================
        run {
            val t4Violations = mutableListOf<String>()
            val condGoals = listOf(
                "Continuous aerobic", "Modality HIIT intervals", "Structured circuits",
                "EMOM work capacity", "Quality-paced AMRAP", "Work-rest intervals",
                "Mixed-modal conditioning", "Muscular endurance circuit"
            )
            val equipSets = listOf(
                "FullErg" to mapOf("erg" to 1, "kb" to 1, "db" to 1, "rope" to 1, "box" to 1),
                "BW" to emptyMap()
            )
            for (g in condGoals) {
                for ((eqName, eq) in equipSets) {
                    val req = PlanRequest(
                        goal = g, disciplineGoals = mapOf("cond" to g),
                        start = fixedStart, weeks = 4, minutes = 30, timeBudgetEnabled = true,
                        days = setOf(2, 4, 6), runDays = 0, conditioningDays = 2,
                        experience = if (eqName == "BW") 0 else 1, equipment = eq, policyChoice = "engine15"
                    )
                    val plan = Engine18ConditioningWorkoutConstructor.buildSoloPlan(req, owner, "cp-cond-$eqName-${g.take(6)}", fixedNow)
                    Engine18ConditioningWorkoutConstructor.validateSavedSolo(plan)
                    for (s in plan.sessions) {
                        if (s.minutes > 30) t4Violations += "$g/$eqName: ${s.id} minutes=${s.minutes} > 30"
                        if ("\u2014" in s.title || "\u2014" in s.reason || s.exercises.any { "\u2014" in it.cue }) {
                            t4Violations += "$g/$eqName: em-dash in ${s.id}"
                        }
                    }
                }
            }
            val status = if (t4Violations.isEmpty()) "PASS" else "FAIL(${t4Violations.size})"
            scorecard += "Track 4 | Conditioning 8-Family Engine 1.8  | $status | 8 families x 2 equip presets @ 30m"
            violations += t4Violations.map { "[Track 4] $it" }
        }

        // =========================================================================
        // TRACK 5: Engine 1.8 Hybrid Coordination (4 Multi-Discipline Combos)
        // =========================================================================
        run {
            val t5Violations = mutableListOf<String>()
            val hybrids = listOf(
                "Run_Str_12w_HM" to PlanRequest(
                    goal = "Half marathon", experience = 1, strengthExperience = 1,
                    start = fixedStart, weeks = 12, days = (1..7).toSet(),
                    runDays = 4, strengthDays = 2, longDay = 7, qualityCount = 1, qualityDays = setOf(2),
                    weeklyKm = 40.0, longestKm = 14.0, targetWeeklyKm = 50.0,
                    recentMileageBasis = "SELF_REPORTED_AVERAGE", planningPace = 320.0,
                    timeBudgetEnabled = false, equipment = fullGym,
                    disciplineGoals = mapOf("run" to "Half marathon", "str" to "Strength for runners"),
                    policyChoice = "engine15"
                ),
                "Run_Cal_12w_10K" to PlanRequest(
                    goal = "Improve 10K", experience = 1, calLevel = 1, pullLevel = 1, coreLevel = 1,
                    start = fixedStart, weeks = 12, days = (1..7).toSet(),
                    runDays = 4, calDays = 2, longDay = 7, qualityCount = 1, qualityDays = setOf(2),
                    weeklyKm = 36.0, longestKm = 12.0, targetWeeklyKm = 44.0,
                    recentMileageBasis = "SELF_REPORTED_AVERAGE", planningPace = 325.0,
                    timeBudgetEnabled = false, equipment = fullGym,
                    disciplineGoals = mapOf("run" to "Improve 10K", "cal" to "Calisthenics foundation"),
                    policyChoice = "engine15"
                ),
                "Str_Cal_8w" to PlanRequest(
                    goal = "General strength", strengthExperience = 1, calLevel = 1, pullLevel = 1, coreLevel = 1,
                    start = fixedStart, weeks = 8, days = (1..7).toSet(),
                    runDays = 0, strengthDays = 3, calDays = 2,
                    timeBudgetEnabled = false, equipment = fullGym,
                    disciplineGoals = mapOf("str" to "General strength", "cal" to "Handstand foundations"),
                    policyChoice = "engine15"
                ),
                "Run_Str_Cond_8w" to PlanRequest(
                    goal = "Improve 10K", experience = 1, strengthExperience = 1,
                    start = fixedStart, weeks = 8, days = (1..7).toSet(),
                    runDays = 3, strengthDays = 2, conditioningDays = 1, longDay = 7, qualityCount = 1, qualityDays = setOf(2),
                    weeklyKm = 28.0, longestKm = 10.0, targetWeeklyKm = 34.0,
                    recentMileageBasis = "SELF_REPORTED_AVERAGE", planningPace = 330.0,
                    timeBudgetEnabled = false, equipment = fullGym + mapOf("erg" to 1, "rope" to 1),
                    disciplineGoals = mapOf("run" to "Improve 10K", "str" to "General strength", "cond" to "Structured circuits"),
                    policyChoice = "engine15"
                )
            )
            for ((label, req) in hybrids) {
                val snap = Engine18HybridProgramBuilder.build(req, owner, "cp-$label", fixedNow, emptyList())
                try {
                    Engine18HybridProgramBuilder.validateSaved(snap)
                } catch (e: Throwable) {
                    val runDates = snap.sessions.filter { it.discipline == "run" && (it.quality || it.kind == "long") }
                        .joinToString { "${it.id}@${it.date}(q=${it.quality},k=${it.kind})" }
                    throw AssertionError("$label validateSaved failed: ${e.message} | reqQ=${snap.request.qualityDays} | runs=[$runDates]", e)
                }
                if (req.runDays > 0) {
                    val longAndQuality = snap.sessions.filter { it.discipline == "run" && (it.kind == "long" || it.quality) }
                    val otherHard = snap.sessions.filter { it.discipline != "run" && (it.lower || it.quality) }
                    for (r in longAndQuality) {
                        val tooClose = otherHard.any { other ->
                            ChronoUnit.DAYS.between(other.date, r.date) in 1L..1L &&
                                "Back-to-back load" !in other.reason
                        }
                        if (tooClose) {
                            t5Violations += "$label: unannotated lower/hard session 1 day before ${r.id} (${r.date})"
                        }
                    }
                }
                if (label == "Run_Cal_12w_10K") {
                    val calSessions = snap.sessions.filter { it.discipline == "cal" }
                    val w2Sets = calSessions.filter { ((it.date.toEpochDay() - req.start.toEpochDay()) / 7) == 1L }.sumOf { s -> s.exercises.sumOf { it.sets } }
                    val taperCal = calSessions.filter { "Running race taper" in it.reason }
                    if (taperCal.isEmpty()) {
                        t5Violations += "$label: Run+Cal hybrid did not coordinate Calisthenics taper on running taper week"
                    } else {
                        val taperWeeksCount = taperCal.map { (it.date.toEpochDay() - req.start.toEpochDay()) / 7 }.distinct().size.coerceAtLeast(1)
                        val taperAvgSetsPerWeek = taperCal.sumOf { s -> s.exercises.sumOf { it.sets } } / taperWeeksCount
                        if (taperAvgSetsPerWeek >= w2Sets) {
                            t5Violations += "$label: Run+Cal taper sets/wk ($taperAvgSetsPerWeek) not reduced vs build ($w2Sets)"
                        }
                    }
                }
            }
            val status = if (t5Violations.isEmpty()) "PASS" else "FAIL(${t5Violations.size})"
            scorecard += "Track 5 | Hybrid Coordination Engine 1.8    | $status | 4 combos (Run+Str, Run+Cal, Str+Cal, 3-disc)"
            violations += t5Violations.map { "[Track 5] $it" }
        }

        println("=== STRIDELAB COMPETITOR PARITY BENCHMARK SCORECARD ===")
        scorecard.forEach(::println)
        if (violations.isNotEmpty()) {
            println("=== VIOLATIONS (${violations.size}) ===")
            violations.forEach(::println)
        }
        assertTrue(
            "Competitor parity benchmark failed with ${violations.size} violation(s):\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
