package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Test

class Engine19DecisionPipelineAndSafetyTest {
    private val owner = "engine19-tester"
    private val t0 = Instant.parse("2026-11-30T08:00:00Z")
    private val monday = LocalDate.parse("2026-11-30")

    private fun sampleAssessment(
        completion: SessionCompletionState = SessionCompletionState.COMPLETE,
        heat: Boolean = false,
        recovery: RecoveryEvidenceStatus = RecoveryEvidenceStatus.VERIFIED_ACCEPTABLE,
        symptom: SymptomStatus = SymptomStatus.CONFIRMED_NONE,
        tech: TechniqueObservationStatus = TechniqueObservationStatus.UNOBSERVED,
        intensity: IntensityAdherenceBand = IntensityAdherenceBand.ON_TARGET
    ) = MultidimensionalExecutionAssessment(
        activityId = "act-1",
        plannedSessionId = "plan-1",
        discipline = "run",
        completion = completion,
        stimulusOutcome = if (heat) StimulusOutcome.STIMULUS_ACHIEVED_ENV_ADJUSTED else StimulusOutcome.STIMULUS_ACHIEVED,
        domainNote = "Execution evaluated.",
        envHeatConfounded = heat,
        envAltitudeOrTerrainConfounded = false,
        telemetryCorruptedOrQuarantined = false,
        reportedRpe = 6,
        targetRpeRange = 5..7,
        reportedRir = null,
        symptomStatus = symptom,
        recoveryEvidence = recovery,
        techniqueStatus = tech,
        durationAdherence = DimensionValue.Measured(1.0),
        distanceAdherence = DimensionValue.Measured(1.0),
        intensityAdherence = DimensionValue.Measured(intensity),
        repetitionStructureAdherence = DimensionValue.NotApplicable,
        recoveryIntervalAdherence = DimensionValue.NotApplicable,
        strengthLoadRepAdherence = DimensionValue.NotApplicable,
        calisthenicsNodeAndTechAdherence = DimensionValue.NotApplicable,
        conditioningDensityAdherence = DimensionValue.NotApplicable,
        subjectiveDifficultyAligned = DimensionValue.Measured(true),
        lateRepOrFinishFadeDetected = false,
        difficultyFromRepOne = false,
        objectiveHrOrPaceAnomalyFlag = false
    )

    @Test fun cd01DuplicateCanonicalImportsIncrementConfirmationCounterOnceOnly() {
        val state = DecisionStateSnapshot()
        val ev1 = CoachingEvent(
            eventId = "ev-fit",
            canonicalActivityId = "canon-run-42",
            discipline = "run",
            evaluatedAtUtc = t0,
            assessment = sampleAssessment(),
            excessReserveTargetKey = "run:threshold",
            excessReserveObserved = true
        )
        val (_, delta1) = Engine19CoachingDecisionEngine.evaluate(state, ev1)
        val (_, delta2) = Engine19CoachingDecisionEngine.evaluate(
            delta1.nextState,
            ev1.copy(eventId = "ev-hc-duplicate", evaluatedAtUtc = t0.plusSeconds(60))
        )
        assertEquals(setOf("canon-run-42"), delta2.nextState.uniqueFreshConfirmations("run:threshold", t0.plusSeconds(120)))
    }

    @Test fun cd04HeatAbortPlusVolumeSpikeEmitsBothRecoveryAndShieldWithoutSuppression() {
        val state = DecisionStateSnapshot()
        val (bundle, _) = Engine19CoachingDecisionEngine.evaluate(
            state,
            CoachingEvent(
                eventId = "d05-heat-abort",
                canonicalActivityId = "canon-heat-1",
                discipline = "run",
                evaluatedAtUtc = t0,
                assessment = sampleAssessment(completion = SessionCompletionState.ABORTED, heat = true),
                volumeSpikeDetected = true
            )
        )
        assertEquals(CoachingActionType.RECOMMEND_RECOVERY, bundle.primary.action)
        assertTrue(bundle.secondaries.any { it.action == CoachingActionType.ABSTAIN })
        assertTrue("ABSTAIN_ON(RECALIBRATE_PACE_OR_HR)" in bundle.scopedAbstentions)
    }

    @Test fun cd05AndD03PathwaysEnforceMissingRecoveryLimitAndSingleAxisUntrapping() {
        // CD-05 / D02: missing recovery prevents Pathway B upgrade even after 3 sessions -> capped to MINOR continuity
        var state = DecisionStateSnapshot()
        for (i in 1..3) {
            val ev = CoachingEvent(
                eventId = "ev-unverified-$i",
                canonicalActivityId = "canon-u-$i",
                discipline = "run",
                evaluatedAtUtc = t0.plus(i.toLong(), ChronoUnit.DAYS),
                assessment = sampleAssessment(recovery = RecoveryEvidenceStatus.MISSING_UNREPORTED),
                excessReserveTargetKey = "run:tempo",
                excessReserveObserved = true,
                athleteHasEstablishedBaseCapacity = true
            )
            val (b, d) = Engine19CoachingDecisionEngine.evaluate(state, ev)
            state = d.nextState
            assertNotEquals(AdvancementPathway.UNDERESTIMATED_CORRECTION_UNTRAPPING, b.primary.pathway)
            if (b.primary.action == CoachingActionType.PROGRESS) {
                assertEquals(AdvancementPathway.MINIMAL_INPUT_MINOR_CONTINUITY, b.primary.pathway)
            }
        }

        // D03: 3 non-confounded easy executions with CONFIRMED_NONE pain + VERIFIED_ACCEPTABLE recovery + established base
        var stateB = DecisionStateSnapshot()
        lateinit var thirdBundle: CoachingDecisionBundle
        for (i in 1..3) {
            val ev = CoachingEvent(
                eventId = "ev-verified-$i",
                canonicalActivityId = "canon-v-$i",
                discipline = "run",
                evaluatedAtUtc = t0.plus(i.toLong(), ChronoUnit.DAYS),
                assessment = sampleAssessment(recovery = RecoveryEvidenceStatus.VERIFIED_ACCEPTABLE),
                excessReserveTargetKey = "run:tempo",
                excessReserveObserved = true,
                athleteHasEstablishedBaseCapacity = true
            )
            val (b, d) = Engine19CoachingDecisionEngine.evaluate(stateB, ev)
            stateB = d.nextState
            thirdBundle = b
        }
        assertEquals(CoachingActionType.RECOMMEND_RECALIBRATION, thirdBundle.primary.action)
        assertEquals(AdvancementPathway.UNDERESTIMATED_CORRECTION_UNTRAPPING, thirdBundle.primary.pathway)
        assertTrue(thirdBundle.primary.message.contains("never both", ignoreCase = true))
    }

    @Test fun cd06To09OccCommitsRejectStaleVersionsLocksAndDownstreamBreakageAndRevalidateBranches() {
        val fp1 = OccInputFingerprint("a1", "s0", "r0", "av1", "10k", 1, "cal1", Engine19CoachingPolicyRegistry.POLICY_VERSION)
        val cand = PlanRevisionCandidate(
            candidateId = "cand-swap-w2",
            scope = RevisionScope.SCOPE_B_SINGLE_WORKOUT_SWAP,
            expectedBasePlanVersion = 2,
            inputStateHash = fp1.computeSha256(),
            createdAtUtc = t0,
            expiresAtUtc = t0.plus(7, ChronoUnit.DAYS),
            targetSessionIds = setOf("p-w2-q0"),
            affectedWeekIndices = listOf(1, 2, 3, 4, 5, 6, 7),
            reasonSummary = "Swap week-2 quality session"
        )
        // CD-06: Stale state hash rejected atomically
        val fpStale = fp1.copy(restrictionsDigest = "h2-achilles-added")
        val staleRes = Engine19CoachingDecisionEngine.validateAndCommitRevision(cand, 2, fpStale, t0.plusSeconds(30))
        assertTrue(staleRes is OccCommitOutcome.Rejected)
        assertEquals("OCC_INPUT_STATE_STALE", (staleRes as OccCommitOutcome.Rejected).reasonCode)

        // CD-07: Concurrent commit against wrong version rejected cleanly
        val conflictRes = Engine19CoachingDecisionEngine.validateAndCommitRevision(cand, 3, fp1, t0.plusSeconds(30))
        assertEquals("OCC_VERSION_CONFLICT", (conflictRes as OccCommitOutcome.Rejected).reasonCode)

        // D23: Lock collision rejected cleanly
        val lockRes = Engine19CoachingDecisionEngine.validateAndCommitRevision(
            cand, 2, fp1, t0.plusSeconds(30), activeLockSessionIds = setOf("p-w2-q0")
        )
        assertEquals("LOCKED_SESSION_COLLISION", (lockRes as OccCommitOutcome.Rejected).reasonCode)

        // CD-09: Transitive failure in Week 7 (> 30 d later!) caught
        val downstreamRes = Engine19CoachingDecisionEngine.validateAndCommitRevision(
            cand, 2, fp1, t0.plusSeconds(30), downstreamWeekValidator = { wk -> wk != 7 }
        )
        assertEquals("TRANSITIVE_DOWNSTREAM_INVALID_WEEK_7", (downstreamRes as OccCommitOutcome.Rejected).reasonCode)

        // Happy commit + non-destructive Undo
        val ok = Engine19CoachingDecisionEngine.validateAndCommitRevision(cand, 2, fp1, t0.plusSeconds(30))
        assertTrue(ok is OccCommitOutcome.Committed)
        assertEquals(3, (ok as OccCommitOutcome.Committed).newPlanVersion)
        val undoCand = Engine19CoachingDecisionEngine.buildUndoRevision(2, 3, fp1, t0.plusSeconds(60), listOf(2, 3, 4))
        val undoOk = Engine19CoachingDecisionEngine.validateAndCommitRevision(undoCand, 3, fp1, t0.plusSeconds(90))
        assertEquals(4, (undoOk as OccCommitOutcome.Committed).newPlanVersion)

        // CD-08 & D26: Simple completion tap satisfies only completionRequired; activation revalidation blocks stale hold branch
        val gate = ConditionalProgressionGate(
            gateId = "g-w4-thr",
            sessionLifecycle = GateSessionLifecycle.DUE_TODAY,
            spec = ConditionalGatePrerequisiteSpec(),
            statuses = mapOf(
                "completionRequired" to PrerequisiteStatus.UNVERIFIED,
                "intensityAdherenceRequired" to PrerequisiteStatus.UNVERIFIED,
                "recoveryOrContinuityRequired" to PrerequisiteStatus.UNVERIFIED
            ),
            validUntilUtc = t0.plus(2, ChronoUnit.DAYS)
        ).withUserSimpleCompletionTap()
        assertEquals(PrerequisiteStatus.SATISFIED, gate.statuses["completionRequired"])
        assertEquals(PrerequisiteStatus.UNVERIFIED, gate.statuses["recoveryOrContinuityRequired"])

        val blockedHold = Engine19CoachingDecisionEngine.evaluateConditionalBranchActivation(
            gate = gate,
            requestedBranch = ContinuationBranchKind.CONSERVATIVE_HOLD,
            ctx = ActivationValidationContext(t0, hasBlockingHealthHold = true, false, false, false)
        )
        assertFalse(blockedHold.allowed)
    }

    @Test fun cd10And11AndD28HealthHoldsPersistSurviveBackupRollbackAndPreserveLocalizedUpper() {
        val backend = InMemoryM1RecordBackend()
        val store = Engine19CoachingStateLocalStore(backend)
        val hold = Engine19HealthEscalationAndState.classifyHealthSymptom(
            report = HealthSymptomReport(
                focalBonyOrWorseningGaitPain = true,
                localizedRegion = "achilles",
                affectedDisciplines = setOf(AthleteDiscipline.RUN),
                summaryNote = "Worsening focal Achilles tendon pain during run"
            ),
            accountId = owner,
            restrictionId = "hold-achilles",
            nowUtc = t0,
            nextSeq = 5L
        )!!
        assertEquals(HealthEscalationTier.H2_CLINICAL_EVALUATION_REQUIRED, hold.tier)
        assertTrue(hold.blocksSelfDirectedTesting)
        assertTrue(hold.allowsUnaffectedDiscipline(AthleteDiscipline.STRENGTH))
        assertFalse(hold.allowsUnaffectedDiscipline(AthleteDiscipline.RUN))

        store.saveHealthHold(owner, hold, t0.toEpochMilli())
        assertEquals(0, backend.outboxWriteCount)

        // Restart reload preserves H2 hold cleanly; symptom-free tap cannot clear H2 without clinical clearance (`CD-11`, `D28`)
        val reloaded = store.loadDecisionState(owner)
        val persistedHold = reloaded.activeHealthHolds.getValue("hold-achilles")
        val (_, clearedWithoutClinician) = Engine19HealthEscalationAndState.attemptClearHold(
            persistedHold, HealthClearanceProvenance.USER_REPORTED_SYMPTOM_FREE, t0.plus(4, ChronoUnit.DAYS)
        )
        assertFalse(clearedWithoutClinician)

        // CD-10: Restoring older backup (empty or seq=1 cleared) monotonically keeps newer active seq=5 hold!
        val olderBackup = mapOf(
            "hold-achilles" to hold.copy(
                createdAtUtc = t0.minus(11, ChronoUnit.DAYS),
                updatedAtUtc = t0.minus(10, ChronoUnit.DAYS),
                monotonicSeq = 1L,
                clearedAtUtc = t0.minus(9, ChronoUnit.DAYS),
                clearedReason = "old resolved issue"
            )
        )
        val merged = Engine19HealthEscalationAndState.mergeHealthHoldsMonotonically(reloaded.activeHealthHolds, olderBackup, owner)
        assertTrue(merged.getValue("hold-achilles").isActive)

        // Composable decision evaluation on running pauses running while continuing localized strength (`D13`)
        val (bundle, _) = Engine19CoachingDecisionEngine.evaluate(
            reloaded,
            CoachingEvent(eventId = "mon-speedwork", discipline = "run", evaluatedAtUtc = t0.plus(4, ChronoUnit.DAYS))
        )
        assertEquals(CoachingActionType.PAUSE_TRAINING, bundle.primary.action)
        assertTrue(bundle.secondaries.any { it.action == CoachingActionType.CONTINUE && it.discipline == "str" })

        // Clinical clearance clears H2 cleanly with explicit provenance
        val (clearedHold, okClear) = Engine19HealthEscalationAndState.attemptClearHold(
            persistedHold, HealthClearanceProvenance.USER_REPORTED_CLINICAL_CLEARANCE, t0.plus(10, ChronoUnit.DAYS), "Physio cleared"
        )
        assertTrue(okClear)
        assertFalse(clearedHold.isActive)
        assertEquals(HealthClearanceProvenance.USER_REPORTED_CLINICAL_CLEARANCE, clearedHold.clearanceProvenance)
    }

    @Test fun d14AndCd12To16CoverFebrileClarificationCooldownExemptionsDeterministicReplayAndNotificationCaps() {
        // D14: Febrile illness triggers H3 hold + BLOCKING_CLARIFICATION without assuming medical clearance
        val febrileState = DecisionStateSnapshot()
        val (febBundle, _) = Engine19CoachingDecisionEngine.evaluate(
            febrileState,
            CoachingEvent(
                eventId = "d14-febrile",
                discipline = "run",
                evaluatedAtUtc = t0,
                symptomReport = HealthSymptomReport(recentFebrileIllnessDaysAgo = 0, summaryNote = "5-day fever ended today")
            )
        )
        assertEquals(CoachingActionType.PAUSE_TRAINING, febBundle.primary.action)
        assertTrue(febBundle.secondaries.any { it.action == CoachingActionType.REQUEST_CLARIFICATION })

        // CD-12 & D24: Reversal stability hold suppresses Scope D churn while keeping urgent conflict / unplanned race active
        val reversalsState = DecisionStateSnapshot(
            approvalAndRejectionHistory = listOf(
                ApprovalRejectionRecord("c1", CoachingDecisionClass.SCOPE_D_RECALIBRATION, "APPROVED", t0.minus(8, ChronoUnit.DAYS)),
                ApprovalRejectionRecord("c1", CoachingDecisionClass.SCOPE_D_RECALIBRATION, "REJECTED", t0.minus(6, ChronoUnit.DAYS)),
                ApprovalRejectionRecord("c2", CoachingDecisionClass.SCOPE_D_RECALIBRATION, "APPROVED", t0.minus(3, ChronoUnit.DAYS)),
                ApprovalRejectionRecord("c2", CoachingDecisionClass.SCOPE_D_RECALIBRATION, "REJECTED", t0.minus(1, ChronoUnit.DAYS))
            )
        )
        val (urgentBundle, _) = Engine19CoachingDecisionEngine.evaluate(
            reversalsState,
            CoachingEvent(
                eventId = "cd12-race-conflict",
                discipline = "run",
                evaluatedAtUtc = t0,
                urgentScheduleOrLockConflict = true,
                unplannedRaceDetected = true
            )
        )
        assertEquals(CoachingActionType.RECOMMEND_RECOVERY, urgentBundle.primary.action)
        assertTrue(urgentBundle.secondaries.any { it.action == CoachingActionType.RECOMMEND_SCHEDULE_CHANGE })

        // CD-15: 10x identical deterministic replay
        val baselines = (1..10).map { Engine19CoachingDecisionEngine.evaluate(reversalsState, CoachingEvent("replay", discipline = "run", evaluatedAtUtc = t0)) }
        assertTrue(baselines.all { it == baselines.first() })

        // CD-16: Exhausted weekly optional budget (2/2) STILL delivers H1/H2 safety alert & blocking clarification immediately
        val filtered = Engine19ExplanationAndNotificationPolicy.filterAndPrioritizeNotifications(
            candidates = listOf(
                CoachingNotificationCandidate("opt-summary", NotificationClass.WEEKLY_SUMMARY, "Weekly digest", "Routine check"),
                CoachingNotificationCandidate("safety-h2", NotificationClass.SAFETY_ALERT, "Health Hold", "Pause running due to focal pain"),
                CoachingNotificationCandidate("block-1", NotificationClass.BLOCKING_CLARIFICATION, "Confirm", "Confirm clearance")
            ),
            optionalAlreadySentThisSession = 1,
            optionalAlreadySentThisWeek = 2
        )
        assertEquals(listOf("safety-h2", "block-1"), filtered.map { it.notificationId })
    }

    @Test fun engine16StrengthDebriefWorksThroughEngine15DebriefEngineAfterParityFix() {
        val req = PlanRequest(
            goal = "General strength",
            start = monday,
            weeks = 4,
            days = setOf(1, 3, 5),
            runDays = 0,
            strengthDays = 2,
            strengthExperience = 1,
            equipment = mapOf("db" to 2, "bench" to 1),
            policyChoice = "engine15"
        )
        val built = Engine16StrengthProgramBuilder.build(req, owner, "str-debrief-19", t0).snapshot
        val approved = built.copy(status = "approved")
        val target = approved.sessions.first()
        val loggedSession = target.copy(
            status = "completed",
            log = WorkoutLog(
                at = t0,
                minutes = target.minutes.toDouble(),
                effort = 7,
                performedOn = target.date,
                completionDifficulty = "As planned",
                discomfort = "None",
                sets = target.exercises.take(1).flatMap { ex ->
                    (1..ex.sets).map { setNum -> SetRecord(ex.id, setNum, reps = ex.reps ?: 8, kg = 20.0) }
                }
            )
        )
        val planWithLog = approved.copy(sessions = approved.sessions.map { if (it.id == target.id) loggedSession else it })
        val debrief = Engine15DebriefEngine.workout(planWithLog, target.id, listOf(planWithLog), emptyList(), ZoneOffset.UTC)
        assertNotNull("Engine 1.6 completed strength workout should produce a valid debrief.", debrief)
    }
}
