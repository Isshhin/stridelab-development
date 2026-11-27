package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReviewAuditFixesTest {

    private val owner = "review-audit-athlete"
    private val monday = LocalDate.of(2026, 11, 2)
    private val now = Instant.parse("2026-11-01T08:00:00Z")

    private fun restriction(
        id: String,
        discipline: AthleteDiscipline,
        severity: SafetyRestrictionSeverity,
        cap: SafetyIntensityCap,
        kind: SafetyRestrictionKind = if (severity == SafetyRestrictionSeverity.HARD_BLOCK) {
            SafetyRestrictionKind.MEDICAL_OR_REST_OVERRIDE
        } else {
            SafetyRestrictionKind.MAX_INTENSITY_CAP
        }
    ) = AthleteSafetyRestriction(
        restrictionId = id,
        accountId = owner,
        kind = kind,
        disciplines = setOf(discipline),
        severity = severity,
        maxSessionIntensityCap = cap,
        note = "Test safety hold $id",
        createdAtUtc = now.minusSeconds(3600)
    )

    @Test
    fun p0SafetyRestrictionsEnforcedAcrossAllDisciplinesAndSurvivePostExpiryReplay() {
        val hardStr = AthleteSafetyRestrictionSet(
            owner,
            listOf(restriction("r-str-hard", AthleteDiscipline.STRENGTH, SafetyRestrictionSeverity.HARD_BLOCK, SafetyIntensityCap.REST_ONLY))
        )
        val restCal = AthleteSafetyRestrictionSet(
            owner,
            listOf(restriction("r-cal-rest", AthleteDiscipline.CALISTHENICS, SafetyRestrictionSeverity.CAP_LIMITED, SafetyIntensityCap.REST_ONLY))
        )
        val restCond = AthleteSafetyRestrictionSet(
            owner,
            listOf(restriction("r-cond-rest", AthleteDiscipline.CONDITIONING, SafetyRestrictionSeverity.CAP_LIMITED, SafetyIntensityCap.REST_ONLY))
        )

        val strReq = PlanRequest(
            goal = "Strength",
            start = monday,
            weeks = 4,
            minutes = 60,
            days = setOf(1, 3, 5),
            runDays = 0,
            strengthDays = 3,
            equipment = mapOf("bb" to 1, "bench" to 1, "db" to 1)
        )
        val calReq = PlanRequest(
            goal = "Calisthenics",
            start = monday,
            weeks = 4,
            minutes = 45,
            days = setOf(1, 3, 5),
            runDays = 0,
            calDays = 3
        )
        val condReq = PlanRequest(
            goal = "EMOM",
            start = monday,
            weeks = 4,
            minutes = 35,
            days = setOf(2, 4, 6),
            runDays = 0,
            conditioningDays = 2
        )

        assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(strReq, owner, emptyList(), safetyRestrictions = hardStr, now = now)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(calReq, owner, emptyList(), safetyRestrictions = restCal, now = now)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(condReq, owner, emptyList(), safetyRestrictions = restCond, now = now)
        }

        // Hybrid plan containing a blocked strength component must also fail closed.
        val hybReq = PlanRequest(
            goal = "Hybrid",
            start = monday,
            weeks = 6,
            minutes = 50,
            days = setOf(1, 2, 3, 4, 6, 7),
            runDays = 3,
            strengthDays = 2,
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(2),
            planningPace = 330.0
        )
        assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(hybReq, owner, emptyList(), safetyRestrictions = hardStr, now = now)
        }

        // EASY_AEROBIC_ONLY cap projected into request.safetyIntensityCaps persists into validateSaved without external restrictions.
        val easyAll = AthleteSafetyRestrictionSet(
            owner,
            listOf(
                restriction("r-cond-easy", AthleteDiscipline.CONDITIONING, SafetyRestrictionSeverity.CAP_LIMITED, SafetyIntensityCap.EASY_AEROBIC_ONLY),
                restriction("r-cal-easy", AthleteDiscipline.CALISTHENICS, SafetyRestrictionSeverity.CAP_LIMITED, SafetyIntensityCap.EASY_AEROBIC_ONLY),
                restriction("r-str-easy", AthleteDiscipline.STRENGTH, SafetyRestrictionSeverity.CAP_LIMITED, SafetyIntensityCap.EASY_AEROBIC_ONLY)
            )
        )
        val cappedCondReq = condReq.copy(safetyIntensityCaps = mapOf("cond" to SafetyIntensityCap.EASY_AEROBIC_ONLY.name))
        val condPlan = Engine15IndependentPlanGenerator.generateWithHistory(cappedCondReq, owner, emptyList(), safetyRestrictions = easyAll, now = now)
        assertTrue(condPlan.sessions.all { "continuous aerobic" in it.title.lowercase() && !it.quality })
        val decodedCond = PlanSnapshot.decode(JSONObject(condPlan.encode().toString()), owner)
        // Post-expiry deterministic replay without passing external safetyRestrictions:
        Engine15IndependentPlanGenerator.validateSaved(decodedCond)

        val cappedCalReq = calReq.copy(goal = "Explosive Power", safetyIntensityCaps = mapOf("cal" to SafetyIntensityCap.EASY_AEROBIC_ONLY.name))
        val calPlan = Engine15IndependentPlanGenerator.generateWithHistory(cappedCalReq, owner, emptyList(), safetyRestrictions = easyAll, now = now)
        assertTrue(calPlan.sessions.none { it.quality })
        Engine15IndependentPlanGenerator.validateSaved(PlanSnapshot.decode(JSONObject(calPlan.encode().toString()), owner))

        val cappedStrReq = strReq.copy(safetyIntensityCaps = mapOf("str" to SafetyIntensityCap.EASY_AEROBIC_ONLY.name))
        val strPlan = Engine15IndependentPlanGenerator.generateWithHistory(cappedStrReq, owner, emptyList(), safetyRestrictions = easyAll, now = now)
        Engine15IndependentPlanGenerator.validateSaved(PlanSnapshot.decode(JSONObject(strPlan.encode().toString()), owner))

        // Adaptation safety guard: NO_HIGH_INTENSITY_Z3 on RUN blocks PACE_RECALIBRATION_UP (which proposes when unrestricted).
        val runReq = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = monday,
            weeks = 10,
            runDays = 4,
            days = setOf(2, 4, 6, 7),
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 40.0,
            longestKm = 14.0,
            targetWeeklyKm = 50.0,
            planningPace = 330.0,
            preferredQuality = "Intervals",
            preferredQualityExplicit = true,
            timeBudgetEnabled = false
        )
        val rawRunPlan = (Engine15IndependentPlanGenerator.previewAutoReconciled(runReq, owner, "run-cap", now = now) as Engine15IndependentPlanDecision.Candidate)
            .plan.copy(status = "approved", policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY)
        val asOf = monday.plusDays(27)
        val activities = ArrayList<ActivityRecord>()
        val matches = LinkedHashMap<String, ActivityMatchRecord>()
        val runPlan = rawRunPlan.copy(
            sessions = rawRunPlan.sessions.map { s ->
                if (!s.date.isAfter(asOf) && s.discipline == "run") {
                    val actId = "act-${s.id}"
                    activities += ActivityRecord(
                        id = actId,
                        title = s.title,
                        type = "running",
                        source = "garmin",
                        startedAt = s.date.atTime(7, 0).toInstant(ZoneOffset.UTC),
                        distanceM = (s.distanceKm ?: 8.0) * 1000.0,
                        durationS = s.minutes * 60.0,
                        movingTimeS = s.minutes * 60.0,
                        recordedPace = 330.0,
                        averageHr = 148.0,
                        elevationM = 20.0
                    )
                    matches[actId] = ActivityMatchRecord(
                        activityId = actId,
                        plannedSessionId = s.id,
                        matchStatus = ActivityMatchStatus.AUTO_LINKED,
                        confidenceScore = 0.95,
                        dateOffsetDays = 0,
                        reasonCodes = listOf("DATE_EXACT_MATCH")
                    )
                    s.copy(status = "completed", linkedActivityId = actId, linkPreviousStatus = "planned")
                } else s
            }
        )
        val fasterDiag = AthleteDiagnosticSnapshot(
            planId = runPlan.id,
            asOfDate = asOf,
            overallConfidence = DiagnosticConfidence.HIGH,
            pendingConfirmationCount = 0,
            volumeTolerance = VolumeToleranceDiagnostic(
                windowDays = 28,
                plannedKm = 160.0,
                completedKm = 160.0,
                volumeCompletionRatio = 1.0,
                plannedSessions = 16,
                completedSessions = 16,
                consecutiveMissedSessions = 0,
                rolling28dWeeklyAvgKm = 40.0,
                last7dKm = 40.0,
                acuteToBaselineRatio = 1.0,
                longest30dKm = 14.0,
                exposureStatus = ExposureStatus.SAFE,
                confidence = DiagnosticConfidence.HIGH,
                reasonCodes = emptyList()
            ),
            qualityExecution = QualityExecutionDiagnostic(
                evaluatedQualityCount = 4,
                verifiedHitCount = 4,
                verifiedHitRatePct = 100.0,
                volumeOnlySubstitutionCount = 0,
                tooSlowCount = 0,
                tooFastCount = 0,
                partialCount = 0,
                paceTrend = LongitudinalPaceTrend.FASTER_THAN_TARGET,
                avgWorkPaceDeltaSecPerKm = -7.5,
                confidence = DiagnosticConfidence.HIGH,
                reasonCodes = emptyList()
            ),
            easyDiscipline = EasyDisciplineDiagnostic(
                evaluatedEasyCount = 12,
                tooFastCount = 0,
                grayZoneCreepRatePct = 0.0,
                avgOvershootSecPerKm = null,
                hiddenFatigueRisk = false,
                confidence = DiagnosticConfidence.HIGH,
                reasonCodes = emptyList()
            ),
            aerobicEfficiency = AerobicEfficiencyDiagnostic(
                hrDataAvailable = true,
                eligibleRunCount = 8,
                earlyWindowEf = 0.0210,
                recentWindowEf = 0.0221,
                efChangePct = 5.2,
                medianDecouplingPct = 3.0,
                trend = AerobicEfficiencyTrend.IMPROVING,
                confidence = DiagnosticConfidence.HIGH,
                reasonCodes = emptyList()
            ),
            recalibrationAdvisory = RecalibrationAdvisory.SUGGEST_FASTER_CHECK,
            insights = emptyList(),
            reasonCodes = emptyList()
        )
        val unrestrictedProp = Engine15PlanAdaptationEngine.evaluateProposal(
            plan = runPlan,
            diagnostic = fasterDiag,
            activities = activities,
            matchesByActivityId = matches,
            asOfDate = asOf,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(AdaptationType.PACE_RECALIBRATION_UP, unrestrictedProp?.type)

        val runEasyRestrictions = AthleteSafetyRestrictionSet(
            owner,
            listOf(restriction("r-run-noz3", AthleteDiscipline.RUN, SafetyRestrictionSeverity.CAP_LIMITED, SafetyIntensityCap.NO_HIGH_INTENSITY_Z3))
        )
        assertNull(
            Engine15PlanAdaptationEngine.evaluateProposal(
                plan = runPlan,
                diagnostic = fasterDiag,
                activities = activities,
                matchesByActivityId = matches,
                asOfDate = asOf,
                zoneId = ZoneOffset.UTC,
                safetyRestrictions = runEasyRestrictions,
                evaluatedAtUtc = now
            )
        )
    }

    @Test
    fun p1ConditioningDurationAndRecoveryNeverExceedAdvertisedOrBudgetMinutes() {
        val goals = listOf(
            "Continuous Aerobic",
            "EMOM",
            "Quality AMRAP",
            "Modality HIIT",
            "Structured Circuits",
            "Muscular Endurance Circuits",
            "Work-Rest Intervals",
            "Mixed Modal Conditioning"
        )
        val fullEq = mapOf("rower" to 1, "bike" to 1, "kb" to 1, "db" to 1, "box" to 1, "rope" to 1)
        val budgets = listOf(20, 25, 30, 45)

        for (goal in goals) {
            for (budget in budgets) {
                val req = PlanRequest(
                    goal = "Conditioning",
                    disciplineGoals = mapOf("cond" to goal),
                    start = monday,
                    weeks = 4,
                    minutes = budget,
                    timeBudgetEnabled = true,
                    days = setOf(2, 4, 6),
                    runDays = 0,
                    conditioningDays = 2,
                    experience = 1,
                    equipment = fullEq,
                    policyChoice = "engine15"
                )
                val fam = Engine18ConditioningWorkoutConstructor.familyFromGoal(req.disciplineGoal("cond"), experience = 1, hasErgOrExternalEquipment = true)
                val plan = Engine18ConditioningWorkoutConstructor.buildSoloPlan(req, owner, "cond-${fam.name}-$budget", now)
                Engine18ConditioningWorkoutConstructor.validateSavedSolo(plan)
                val (wuMin, cdMin) = Engine18ConditioningWorkoutConstructor.bookendMinutes(fam, true, budget)
                for (session in plan.sessions) {
                    assertTrue(
                        "${fam.name} budget=$budget session=${session.id} advertised=${session.minutes} > budget",
                        session.minutes <= budget
                    )
                    val est = Engine18ConditioningWorkoutConstructor.estimateSessionDuration(wuMin, cdMin, fam, session.exercises)
                    assertTrue(
                        "${fam.name} budget=$budget session=${session.id} actualSec=${est.totalSeconds} > advertisedSec=${session.minutes * 60}",
                        est.totalSeconds <= session.minutes * 60
                    )
                    val totalSets = session.exercises.sumOf { it.sets }
                    if (fam != ConditioningFamily8.EMOM) {
                        val manualMain = session.exercises.sumOf { it.sets * (it.seconds ?: 0) } +
                            (totalSets - 1).coerceAtLeast(0) * (session.exercises.firstOrNull()?.rest ?: 0)
                        assertTrue(
                            "${fam.name} manual=$manualMain + bookends=${(wuMin + cdMin) * 60} > ${session.minutes * 60}",
                            (wuMin + cdMin) * 60 + manualMain <= session.minutes * 60
                        )
                    } else {
                        assertEquals("EMOM stations sum sets == mainBlockMinutes", est.mainBlockMinutes, totalSets)
                    }
                }
            }
        }

        // Impossibly short budget must throw explicit conflict rather than overflowing advertised time.
        assertThrows(PlanConstraint::class.java) {
            Engine18ConditioningWorkoutConstructor.buildSoloPlan(
                PlanRequest(goal = "EMOM", start = monday, weeks = 2, minutes = 5, timeBudgetEnabled = true, days = setOf(2, 4), runDays = 0, conditioningDays = 2),
                owner,
                "cond-impossible",
                now
            )
        }
    }

    @Test
    fun p1HybridTaperUsesActualMacrocyclePhasesAcrossBuildAndValidateSaved() {
        val req = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            strengthExperience = 1,
            start = monday,
            weeks = 12,
            minutes = 90,
            timeBudgetEnabled = false,
            days = setOf(1, 2, 3, 4, 6, 7),
            runDays = 4,
            strengthDays = 2,
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 30.0,
            longestKm = 12.0,
            targetWeeklyKm = 40.0,
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            planningPace = 330.0,
            equipment = mapOf("bb" to 1, "db" to 1, "bench" to 1),
            disciplineGoals = mapOf("run" to "Improve 10K", "str" to "General strength"),
            policyChoice = "engine15"
        )
        val hybrid = Engine18HybridProgramBuilder.build(req, owner, "hybrid-taper", now, emptyList())
        Engine18HybridProgramBuilder.validateSaved(hybrid)

        val runningSessions = hybrid.sessions.filter { it.discipline == "run" }
        val soloRun = req.copy(strengthDays = 0, calDays = 0, conditioningDays = 0)
        val vol = Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(soloRun),
            Engine15IndependentVolumePlanner.Input(
                weeks = soloRun.weeks,
                runningDays = soloRun.runDays,
                targetWeeklyKm = soloRun.targetWeeklyKm,
                returning = soloRun.returning,
                recoveryHold = soloRun.runningRecoveryHold,
                userDirected = soloRun.userDirectedPlanning,
                raceGoal = soloRun.goal,
                experience = soloRun.experience,
                useMultiFactorProgression = true
            )
        )
        val (expectedMajor, expectedTaper) = Engine18HybridProgramBuilder.computeRunningCoordinationWeeks(soloRun, vol, runningSessions)
        assertTrue("Expected at least one actual Engine15MacroPhase.TAPER week", expectedTaper.isNotEmpty())

        val strByWeek = hybrid.sessions.filter { it.discipline == "str" }
            .groupBy { ((it.date.toEpochDay() - req.start.toEpochDay()) / 7).toInt() + 1 }
        val buildStrSets = strByWeek.getValue(2).sumOf { s -> s.exercises.sumOf { it.sets } }
        val taperWeek = expectedTaper.max()
        val taperStr = strByWeek.getValue(taperWeek)
        val taperStrSets = taperStr.sumOf { s -> s.exercises.sumOf { it.sets } }
        assertTrue(
            "Expected taper strength week $taperWeek sets ($taperStrSets) < build week 2 ($buildStrSets)",
            taperStrSets < buildStrSets
        )
        assertTrue(taperStr.all { s -> s.reason.contains("Running race taper", ignoreCase = true) })
        assertTrue("Major step coordination weeks should remain computed cleanly", expectedMajor.all { it in 1..req.weeks })
    }

    @Test
    fun p1StrengthProgressionIsExecutionGatedNotBlindlyPredetermined() {
        val eq = mapOf("bb" to 1, "bench" to 1, "rack" to 1, "db" to 1, "cable" to 1)
        val req = PlanRequest(
            goal = "Muscle development",
            start = monday,
            weeks = 12,
            minutes = 75,
            days = setOf(1, 2, 4, 5),
            runDays = 0,
            strengthDays = 4,
            strengthExperience = 1,
            equipment = eq,
            policyChoice = "engine15"
        )
        val built = Engine16StrengthProgramBuilder.build(req, owner, "str-gated", now).snapshot.copy(status = "approved")
        val w1 = built.sessions.take(4).first()
        val w2 = built.sessions[1 * 4]
        val w5 = built.sessions[4 * 4]
        val w6 = built.sessions[5 * 4]
        val w9 = built.sessions[8 * 4]
        assertEquals("Block 2 Week 5 unexecuted baseline should match Block 1 Week 1 before receipts", w1.exercises.sumOf { it.sets * (it.reps ?: 1) }, w5.exercises.sumOf { it.sets * (it.reps ?: 1) })
        assertEquals("Block 2 Week 6 unexecuted baseline should match Block 1 Week 2 before receipts", w2.exercises.sumOf { it.sets * (it.reps ?: 1) }, w6.exercises.sumOf { it.sets * (it.reps ?: 1) })
        assertEquals("Block 3 Week 9 unexecuted baseline should match Block 1 Week 1 before receipts", w1.exercises.sumOf { it.sets * (it.reps ?: 1) }, w9.exercises.sumOf { it.sets * (it.reps ?: 1) })

        // Confirm clean execution with target RIR 3 -> gates +1 progression onto upcoming matching session.
        val execClean = WorkoutExecution.create("exec-1", built.id, w1, "fin-1").let { ex ->
            val rows = ex.sets.map { s ->
                s.confirmed(SetActual(reps = s.target.reps, kg = 60.0, seconds = s.target.seconds, rir = 3))
            }
            val log = WorkoutLog(
                now, w1.minutes.toDouble(), null, 6,
                rows.map { SetRecord(it.target.id, it.number, it.actual!!.reps, it.actual.kg, it.actual.seconds) },
                "Clean controlled sets", w1.date, discomfort = "None"
            )
            ex.copy(sets = rows, phase = PlayerPhase.Completed, lifecycle = ExecutionLifecycle.FINISHED_NORMALLY, receipt = true, finalLog = log) to log
        }
        val completedPlan = built.copy(sessions = listOf(w1.copy(status = "completed", log = execClean.second)) + built.sessions.drop(1))
        val proposal = Engine15PlanAdaptationEngine.evaluateStrengthExecutionProgression(
            completedPlan, emptyList(), w1.date
        ) { execClean.first.takeIf { r -> r.sessionId == it.id } }
        assertNotNull("Confirmed clean execution should propose STRENGTH_PROGRESSION", proposal)
        assertEquals(AdaptationType.STRENGTH_PROGRESSION, proposal!!.type)

        val applied = Engine15PlanAdaptationEngine.applyProposal(completedPlan, proposal, w1.date)
        val restoredApplied = PlanSnapshot.decode(JSONObject(applied.appliedPlan.encode().toString()), owner)
        Engine15IndependentPlanGenerator.validateSaved(restoredApplied)
        val progressedSession = restoredApplied.sessions.first { it.id != w1.id && it.difficultyStep > 0 }
        assertEquals(1, progressedSession.difficultyStep)
        assertTrue(progressedSession.exercises.sumOf { it.sets * (it.reps ?: 1) } > restoredApplied.sessions.first { it.id == progressedSession.id }.baselineSession().exercises.sumOf { it.sets * (it.reps ?: 1) })

        // Lossless one-tap rollback:
        val reverted = Engine15PlanAdaptationEngine.revertRevision(restoredApplied, applied.revision, w1.date)
        assertEquals(completedPlan.sessions, reverted.restoredPlan.sessions)

        // Concerning discomfort/strain receipt must step back (-1), never advance.
        val execStrain = WorkoutExecution.create("exec-strain", built.id, w1, "fin-2").let { ex ->
            val rows = ex.sets.map { s ->
                s.confirmed(SetActual(reps = (s.target.reps ?: 8) - 1, kg = 60.0, seconds = s.target.seconds, rir = 0))
            }
            val log = WorkoutLog(
                now, w1.minutes.toDouble(), null, 9,
                rows.map { SetRecord(it.target.id, it.number, it.actual!!.reps, it.actual.kg, it.actual.seconds) },
                "Joint tightness", w1.date, discomfort = "Pain"
            )
            ex.copy(sets = rows, phase = PlayerPhase.Completed, lifecycle = ExecutionLifecycle.FINISHED_NORMALLY, receipt = true, finalLog = log) to log
        }
        val strainPlan = built.copy(sessions = listOf(w1.copy(status = "completed", log = execStrain.second)) + built.sessions.drop(1))
        val deloadProp = Engine15PlanAdaptationEngine.evaluateStrengthExecutionProgression(
            strainPlan, emptyList(), w1.date
        ) { execStrain.first.takeIf { r -> r.sessionId == it.id } }!!
        val appliedDeload = Engine15PlanAdaptationEngine.applyProposal(strainPlan, deloadProp, w1.date)
        assertTrue(appliedDeload.appliedPlan.sessions.any { it.difficultyStep == -1 })
        Engine15IndependentPlanGenerator.validateSaved(appliedDeload.appliedPlan)
    }

    @Test
    fun p2LimitedEquipmentRotationProfilesAcrossStrengthCalisthenicsAndConditioning() {
        val profiles = listOf(
            "bodyweight" to emptyMap(),
            "dumbbell-only" to mapOf("db" to 1),
            "kettlebell-only" to mapOf("kb" to 1),
            "bar-and-bands" to mapOf("bar" to 1, "bands" to 1),
            "barbell-and-bench" to mapOf("bb" to 1, "bench" to 1)
        )
        val catalogById = Engine16StrengthExerciseCatalog.allMetadata(null).associateBy { it.id }

        for ((label, eq) in profiles) {
            val strReq = PlanRequest(
                goal = "Muscle development",
                start = monday,
                weeks = 6,
                minutes = 60,
                days = setOf(1, 3, 5),
                runDays = 0,
                strengthDays = 3,
                strengthExperience = 1,
                equipment = eq,
                policyChoice = "engine15"
            )
            val strPlan = Engine16StrengthProgramBuilder.build(strReq, owner, "p2-str-$label", now).snapshot
            assertEquals("Strength $label session count", 18, strPlan.sessions.size)
            Engine16StrengthProgramBuilder.validateSaved(strPlan)
            val availableTokens = Engine16StrengthExerciseCatalog.availableEquipmentTokens(strReq)
            for (s in strPlan.sessions) {
                for (ex in s.exercises) {
                    val meta = catalogById[ex.id]
                    if (meta != null) {
                        assertTrue(
                            "Strength $label exercise ${ex.id} incompatible with $availableTokens",
                            Engine16StrengthExerciseCatalog.matchesEquipment(meta, availableTokens)
                        )
                    }
                }
            }

            val calReq = PlanRequest(
                goal = "Core and Posture",
                start = monday,
                weeks = 4,
                minutes = 45,
                days = setOf(1, 3, 5),
                runDays = 0,
                calDays = 3,
                experience = 1,
                equipment = eq,
                policyChoice = "engine15"
            )
            val calPlan = Engine17CalisthenicsProgramBuilder.build(calReq, owner, "p2-cal-$label", now).snapshot
            assertEquals("Calisthenics $label session count", 12, calPlan.sessions.size)
            Engine17CalisthenicsProgramBuilder.validateSaved(calPlan)

            val condReq = PlanRequest(
                goal = "Structured Circuits",
                start = monday,
                weeks = 4,
                minutes = 30,
                timeBudgetEnabled = true,
                days = setOf(2, 4, 6),
                runDays = 0,
                conditioningDays = 2,
                experience = 1,
                equipment = eq,
                policyChoice = "engine15"
            )
            val condPlan = Engine18ConditioningWorkoutConstructor.buildSoloPlan(condReq, owner, "p2-cond-$label", now)
            assertEquals("Conditioning $label session count", 8, condPlan.sessions.size)
            Engine18ConditioningWorkoutConstructor.validateSavedSolo(condPlan)
        }
    }
}
