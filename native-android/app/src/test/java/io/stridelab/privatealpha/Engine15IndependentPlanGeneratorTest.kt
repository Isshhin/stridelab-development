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

    @Test fun userWorkoutAuthoringBuildsDistanceAndMixedUnitPlansWithoutInferringDistance() {
        val distance=Engine15UserWorkoutAuthoring.build("Intervals",listOf(
            RunningStepV2("WARM_UP","Easy",meters=2000),
            RunningStepV2("WORK","User fast target",meters=800),
            RunningStepV2("RECOVERY","Easy",meters=400),
            RunningStepV2("WORK","User fast target",meters=800),
            RunningStepV2("COOL_DOWN","Easy",meters=2000)
        ))
        assertEquals(1,distance.encode().getInt("version"))
        assertEquals(6000,distance.meters)
        assertEquals(3,MixedRunningStepGrouping.of(listOf(
            RunningStepV2("WARM_UP","Easy",meters=2000),
            RunningStepV2("WORK","User fast target",meters=800),
            RunningStepV2("RECOVERY","Easy",meters=400),
            RunningStepV2("WORK","User fast target",meters=800),
            RunningStepV2("COOL_DOWN","Easy",meters=2000)
        )).size)

        val timed=Engine15UserWorkoutAuthoring.build("Threshold",listOf(
            RunningStepV2("WARM_UP","Easy",meters=2000),
            RunningStepV2("WORK","User threshold target",seconds=300,distanceEquivalentMeters=1200),
            RunningStepV2("RECOVERY","Easy",seconds=90,distanceEquivalentMeters=300),
            RunningStepV2("WORK","User threshold target",seconds=300,distanceEquivalentMeters=1200),
            RunningStepV2("TRANSITION","Easy between blocks",seconds=180,distanceEquivalentMeters=300),
            RunningStepV2("WORK","User speed target",meters=200),
            RunningStepV2("RECOVERY","Easy",meters=200),
            RunningStepV2("WORK","User speed target",meters=200),
            RunningStepV2("COOL_DOWN","Easy",meters=4000)
        ))
        assertEquals(3,timed.encode().getInt("version"))
        assertEquals(9600,timed.reconciledDistanceMeters)
        assertEquals(timed,RunningPrescription.decode(timed.encode()))
    }

    @Test fun userWorkoutAuthoringRejectsUnknownTimedDistanceAndInvalidStepOrder() {
        assertThrows(PlanConstraint::class.java) {
            Engine15UserWorkoutAuthoring.build("Tempo",listOf(
                RunningStepV2("WARM_UP","Easy",meters=1000),
                RunningStepV2("WORK","User target",seconds=300),
                RunningStepV2("COOL_DOWN","Easy",meters=1000)
            ))
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15UserWorkoutAuthoring.build("Fartlek",listOf(
                RunningStepV2("WARM_UP","Easy",meters=1000),
                RunningStepV2("RECOVERY","Easy",meters=100),
                RunningStepV2("WORK","User target",meters=400),
                RunningStepV2("COOL_DOWN","Easy",meters=1000)
            ))
        }
    }

    @Test fun workoutEditorReopensAndReordersRepeatGroupsAsAtomicBlocks() {
        val workout=Engine15UserWorkoutAuthoring.build("Intervals",listOf(
            RunningStepV2("WARM_UP","Easy",meters=1000),
            RunningStepV2("WORK","User target",meters=800),
            RunningStepV2("RECOVERY","Easy",meters=400),
            RunningStepV2("WORK","User target",meters=800),
            RunningStepV2("TRANSITION","Easy between blocks",meters=200),
            RunningStepV2("WORK","Second block",meters=200),
            RunningStepV2("COOL_DOWN","Easy",meters=1000)
        ))
        val savedRequest=PlanRequest(policyChoice="engine15",userDefinedQualityWorkouts=mapOf("w1-run1" to workout))
        val reopened=WorkoutDraft.from(PlanRequest.decode(savedRequest.encode())
            .userDefinedQualityWorkouts.getValue("w1-run1"),1)
        val group=reopened.steps.filter { it.groupId!=null }
        assertEquals(listOf("WORK","RECOVERY","WORK"),group.map { it.purpose })
        assertEquals(2,group.count { it.purpose=="WORK" })

        val moved=moveWorkoutStepBlock(reopened.steps,1,1)
        val groupIndices=moved.indices.filter { moved[it].groupId==group.first().groupId }
        assertEquals((groupIndices.first()..groupIndices.last()).toList(),groupIndices)
        assertEquals(listOf("WORK","RECOVERY","WORK"),groupIndices.map { moved[it].purpose })
        val edited=updateWorkoutStep(moved,groupIndices.first()) { it.copy(amount="900") }
        assertEquals(listOf("900","900"),edited.filter { it.groupId==group.first().groupId && it.purpose=="WORK" }.map { it.amount })
        assertEquals(1,edited.count { it.groupId==group.first().groupId && it.purpose=="RECOVERY" })
        assertTrue(removeWorkoutStepBlock(edited,groupIndices.first()).none { it.groupId==group.first().groupId })
    }

    @Test fun assemblesCompleteEightWeekSaturdayLongSundayEasyCandidate() {
        val decision = Engine15IndependentPlanGenerator.preview(request(), owner, "candidate", now)
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
        assertEquals(plan, Engine15IndependentPlanGenerator.preview(request(), owner, "candidate", now).let {
            (it as Engine15IndependentPlanDecision.Candidate).plan
        })
    }

    @Test fun activityEvidenceIsOwnerCheckedAndDescriptiveOnly() {
        val evidence = ActivityAthleteEvidenceProfile(
            owner = owner, asOf = now, recordedRunCount = 4,
            recordedDistanceByWeekKm = emptyMap(), recordedRunDaysByWeek = emptyMap(),
            longestRecordedRunLast28DaysKm = 12.0,
            userReportedRunCharacteristics = emptyMap(), evidenceActivityIds = listOf("run-a", "run-b"),
            sourceCoverage = "UNKNOWN"
        )
        val without = Engine15IndependentPlanGenerator.preview(request(), owner, "same", now)
            as Engine15IndependentPlanDecision.Candidate
        val with = Engine15IndependentPlanGenerator.preview(
            request(), owner, "same", now, activityEvidence = evidence
        ) as Engine15IndependentPlanDecision.Candidate

        assertEquals(without.volume.weeks, with.volume.weeks)
        assertEquals(without.volume.athleteBaseline.typicalWeeklyKm,
            with.volume.athleteBaseline.typicalWeeklyKm)
        assertEquals(evidence, with.volume.athleteBaseline.activityEvidence)
        assertEquals(without.plan.sessions, with.plan.sessions)
        assertTrue(with.plan.explanations.any {
            it.contains("ACTIVITY_EVIDENCE_CONTEXT") && it.contains("did not set weekly capacity")
        })
        val mismatch = Engine15IndependentPlanGenerator.preview(
            request(), owner, "mismatch", now,
            activityEvidence = evidence.copy(owner = "another-owner")
        )
        assertTrue(mismatch is Engine15IndependentPlanDecision.Deferred)
        assertEquals("ACTIVITY_EVIDENCE_SCOPE_INVALID",
            (mismatch as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test fun defersQualityRequestsInsteadOfDroppingOrSubstitutingThem() {
        for (qualityCount in 1..2) {
            val decision = Engine15IndependentPlanGenerator.preview(
                request().copy(qualityCount = qualityCount), owner, "quality-$qualityCount", now
            )
            assertTrue(decision is Engine15IndependentPlanDecision.Deferred)
            val deferred = decision as Engine15IndependentPlanDecision.Deferred
            assertEquals("QUALITY_GENERATION_NOT_QUALIFIED", deferred.reasonCode)
            assertTrue(deferred.explanation.contains("No coach-generated workout was added"))
            assertNotNull(deferred.qualitySelection)
            assertFalse(deferred.qualitySelection!!.executable)
            if (qualityCount == 1) assertTrue(deferred.explanation.contains("caller-asserted"))
            else assertEquals(Engine15QualitySelectionStatus.UNSUPPORTED, deferred.qualitySelection!!.status)
        }
    }

    @Test fun repeatedQualityHistoryProducesReviewOnlyFamilyCandidateThroughGenerator() {
        val evidence = listOf(
            IndependentQualitySegmentEvidence(
                owner, "source-old", "session-old", now.atZone(java.time.ZoneOffset.UTC).toLocalDate().minusDays(9),
                IndependentQualityFamily.THRESHOLD, IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
                true, listOf(240, 240, 240)
            ),
            IndependentQualitySegmentEvidence(
                owner, "source-new", "session-new", now.atZone(java.time.ZoneOffset.UTC).toLocalDate().minusDays(2),
                IndependentQualityFamily.THRESHOLD, IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
                true, listOf(300, 300, 300)
            )
        )
        val deferred = Engine15IndependentPlanGenerator.preview(
            request().copy(qualityCount = 1), owner, "quality-candidate", now,
            qualityEvidence = evidence
        ) as Engine15IndependentPlanDecision.Deferred

        val selection = deferred.qualitySelection!!
        assertEquals(Engine15QualitySelectionStatus.PROVISIONAL_CANDIDATE, selection.status)
        assertEquals(IndependentQualityFamily.THRESHOLD, selection.selectedFamily)
        assertFalse(selection.sourceEvidenceVerified)
        assertFalse(selection.authorizedForTemplateSelection)
        assertNull(selection.templateId)
        assertTrue(selection.explanation.contains("not authorized"))
    }

    @Test fun representativeGoalsProduceCompleteDeterministicEightToSixteenWeekPlans() {
        val scenarios = listOf(
            Triple("Improve 5K", 3, 35.0),
            Triple("Improve 10K", 4, 45.0),
            Triple("Half marathon", 5, 60.0),
            Triple("Marathon", 6, 75.0)
        )
        for ((index, scenario) in scenarios.withIndex()) {
            val (goal, frequency, target) = scenario
            val input = request(if (index % 2 == 0) 8 else 16).copy(
                goal = goal,
                runDays = frequency,
                weeklyKm = target,
                targetWeeklyKm = target,
                longestKm = target * 0.3,
                days = (1..7).toSet(),
                userDirectedPlanning = true
            )
            val first = Engine15IndependentPlanGenerator.preview(input, owner, "matrix-$index", now)
            assertTrue("$goal/$frequency/$target: $first", first is Engine15IndependentPlanDecision.Candidate)
            val candidate = first as Engine15IndependentPlanDecision.Candidate
            val repeated = Engine15IndependentPlanGenerator.preview(input, owner, "matrix-$index", now)
                as Engine15IndependentPlanDecision.Candidate
            assertEquals(candidate.plan, repeated.plan)
            assertEquals(input.weeks * frequency, candidate.plan.sessions.size)
            assertEquals(input.weeks, candidate.periodization.weeks.size)
            for (week in 0 until input.weeks) {
                val from = input.start.plusWeeks(week.toLong())
                val rows = candidate.plan.sessions.filter { it.date >= from && it.date < from.plusWeeks(1) }
                assertEquals(frequency, rows.size)
                assertEquals(candidate.volume.weeks[week].proposedKm!!, rows.sumOf { it.distanceKm!! }, 1e-6)
                assertEquals(
                    if (week % 4 == 3) Engine15IndependentVolumePlanner.WeekKind.RECOVERY
                    else Engine15IndependentVolumePlanner.WeekKind.LOADING,
                    candidate.volume.weeks[week].kind
                )
            }
            val savedDraft = Engine15IndependentPlanGenerator.generateWithHistory(
                input, owner, emptyList(), "matrix-saved-$index", now
            )
            Engine15IndependentPlanGenerator.validateSaved(savedDraft)
            assertEquals(candidate.plan.sessions.map { listOf(it.date, it.distanceKm, it.kind, it.runningPrescription) },
                savedDraft.sessions.map { listOf(it.date, it.distanceKm, it.kind, it.runningPrescription) })
            assertFalse(candidate.executable)
        }
    }

    @Test fun userDirectedModeHonorsHighWeeklyTargetAndLabelsItAsUnvalidated() {
        val input=request(8).copy(targetWeeklyKm=110.0,longestKm=5.0,userDirectedPlanning=true)
        val result=Engine15IndependentPlanGenerator.preview(input,owner,"user-directed",now)
        assertTrue("Expected a reviewable user-directed plan, got $result",result is Engine15IndependentPlanDecision.Candidate)
        val candidate=result as Engine15IndependentPlanDecision.Candidate
        assertTrue(candidate.periodization.ruleIds.contains("USER_DIRECTED_BUDGET_RECONCILED_NOT_CAPACITY_VALIDATED"))
        for(week in 0 until input.weeks) {
            val rows=candidate.plan.sessions.filter {
                it.date>=input.start.plusWeeks(week.toLong()) && it.date<input.start.plusWeeks((week+1).toLong())
            }
            assertEquals(if(week%4==3)88.0 else 110.0,rows.sumOf { it.distanceKm!! },1e-6)
        }
        assertTrue(candidate.plan.explanations.any { it.contains("user-requested workload") })
        assertTrue(candidate.plan.explanations.any { it.contains("not validated athlete capacity") })
        assertTrue(candidate.plan.explanations.any { it.contains("PROVISIONAL_SESSION_EXPOSURE_WARNING") })
        assertTrue(PlanRequest.decode(candidate.plan.request.encode()).userDirectedPlanning)
        val unknownLongest=Engine15IndependentPlanGenerator.preview(input.copy(longestKm=0.0),owner,"unknown-longest",now)
        assertTrue("A provisional longest-run estimate must not cap user-directed volume: $unknownLongest",
            unknownLongest is Engine15IndependentPlanDecision.Candidate)
    }

    @Test fun userDefinedDistanceIntervalsReplaceOneEasySlotWithoutChangingWeeklyMileage() {
        val workout=RunningPrescription("QUALITY","Intervals",listOf(
            RunningSegment("WARM_UP",2000,"User supplied easy effort"),
            RunningSegment("WORK",800,"User supplied fast target"),
            RunningSegment("RECOVERY",400,"User supplied easy recovery") ,
            RunningSegment("WORK",800,"User supplied fast target"),
            RunningSegment("RECOVERY",400,"User supplied easy recovery"),
            RunningSegment("WORK",800,"User supplied fast target"),
            RunningSegment("RECOVERY",400,"User supplied easy recovery"),
            RunningSegment("WORK",800,"User supplied fast target"),
            RunningSegment("RECOVERY",400,"User supplied easy recovery"),
            RunningSegment("WORK",800,"User supplied fast target"),
            RunningSegment("RECOVERY",400,"User supplied easy recovery"),
            RunningSegment("WORK",800,"User supplied fast target"),
            RunningSegment("COOL_DOWN",4500,"User supplied easy effort")
        ))
        val input=request(1).copy(runDays=3,weeks=1,days=setOf(1,2,3,4,5,6),
            weeklyKm=40.0,targetWeeklyKm=40.0,longestKm=30.0,longDay=6,
            weeklySlots=mapOf("run" to listOf(6,2,4)),qualityDays=setOf(2),
            qualityCount=1,userDirectedPlanning=true,userDefinedQualityWorkouts=mapOf("w1-run1" to workout))
        val result=Engine15IndependentPlanGenerator.preview(input,owner,"structured",now)
        assertTrue("Expected structured user-defined session, got $result",result is Engine15IndependentPlanDecision.Candidate)
        val plan=(result as Engine15IndependentPlanDecision.Candidate).plan
        val quality=plan.sessions.single { it.quality }
        assertEquals(13.3,quality.distanceKm!!,1e-6)
        assertEquals(workout,quality.runningPrescription)
        assertEquals(40.0,plan.sessions.sumOf { it.distanceKm!! },1e-6)
        assertEquals(2,quality.date.dayOfWeek.value)
        assertTrue(quality.reason.contains("user-defined",ignoreCase=true))
        assertFalse((result as Engine15IndependentPlanDecision.Candidate).executable)
        assertEquals(workout,PlanRequest.decode(plan.request.encode()).userDefinedQualityWorkouts["w1-run1"])
        val saved=Engine15IndependentPlanGenerator.generateWithHistory(input,owner,emptyList(),"saved-structured",now)
        Engine15IndependentPlanGenerator.validateSaved(saved)
        assertEquals(saved.sessions,PlanSnapshot.decode(saved.encode(),owner).sessions)
    }

    @Test fun userDefinedMixedUnitQualityDefersWithoutInventingRecoveryDistance() {
        val mixed=RunningPrescription("QUALITY","Intervals",emptyList(),listOf(
            RunningStepV2("WARM_UP","easy",meters=2000),
            RunningStepV2("WORK","user target",seconds=180),
            RunningStepV2("RECOVERY","easy",seconds=90),
            RunningStepV2("COOL_DOWN","easy",meters=2000)
        ),estimatedDistanceKm=8.0)
        val input=request(1).copy(weeks=1,userDirectedPlanning=true,
            userDefinedQualityWorkouts=mapOf("w1-run1" to mixed))
        val result=Engine15IndependentPlanGenerator.preview(input,owner,"mixed-user",now)
        assertTrue(result is Engine15IndependentPlanDecision.Deferred)
        assertEquals("USER_DEFINED_MIXED_DISTANCE_UNRECONCILED",
            (result as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test fun mixedUnitQualityUsesOnlyExplicitPerStepDistanceEquivalents() {
        val mixed=RunningPrescription("QUALITY","Intervals",emptyList(),listOf(
            RunningStepV2("WARM_UP","Easy",meters=2000),
            RunningStepV2("WORK","User supplied target",seconds=300,distanceEquivalentMeters=1200),
            RunningStepV2("RECOVERY","User supplied recovery",seconds=90,distanceEquivalentMeters=300),
            RunningStepV2("WORK","User supplied target",seconds=300,distanceEquivalentMeters=1200),
            RunningStepV2("RECOVERY","User supplied recovery",seconds=90,distanceEquivalentMeters=300),
            RunningStepV2("WORK","User supplied target",seconds=300,distanceEquivalentMeters=1200),
            RunningStepV2("COOL_DOWN","Easy",meters=7100)
        ),reconciledDistanceMeters=13300)
        assertThrows(IllegalArgumentException::class.java) {
            mixed.copy(mixedSegments=mixed.mixedSegments!!.mapIndexed { index,step ->
                if(index==2) step.copy(distanceEquivalentMeters=null) else step
            })
        }
        val input=request(1).copy(weeks=1,runDays=3,days=setOf(1,2,3,4,5,6),
            weeklyKm=40.0,targetWeeklyKm=40.0,longestKm=30.0,longDay=6,
            weeklySlots=mapOf("run" to listOf(6,2,4)),qualityDays=setOf(2),qualityCount=1,
            userDirectedPlanning=true,userDefinedQualityWorkouts=mapOf("w1-run1" to mixed))
        val result=Engine15IndependentPlanGenerator.preview(input,owner,"mixed-distance",now)
        assertTrue("Expected explicitly reconciled mixed workout, got $result",result is Engine15IndependentPlanDecision.Candidate)
        val plan=(result as Engine15IndependentPlanDecision.Candidate).plan
        val session=plan.sessions.single { it.quality }
        assertEquals(13.3,session.distanceKm!!,1e-6)
        assertEquals(mixed,session.runningPrescription)
        assertTrue(session.steps[1].contains("300 s") && session.steps[1].contains("1200 m user-supplied distance equivalent") && session.steps[1].contains("work"))
        assertTrue(session.steps[2].contains("90 s") && session.steps[2].contains("300 m user-supplied distance equivalent") && session.steps[2].contains("recovery"))
        assertEquals(40.0,plan.sessions.sumOf { it.distanceKm!! },1e-6)
        val saved=Engine15IndependentPlanGenerator.generateWithHistory(input,owner,emptyList(),"mixed-saved",now)
        Engine15IndependentPlanGenerator.validateSaved(saved)
        assertEquals(mixed,PlanSnapshot.decode(saved.encode(),owner).sessions.single { it.quality }.runningPrescription)
    }

    @Test fun multiBlockWorkoutPreservesAnExplicitTransitionInTheSavedSequence() {
        val workout=RunningPrescription("QUALITY","Threshold",emptyList(),listOf(
            RunningStepV2("WARM_UP","Easy",meters=2000),
            RunningStepV2("WORK","User threshold target",meters=1000),
            RunningStepV2("RECOVERY","Easy jog",meters=400),
            RunningStepV2("WORK","User threshold target",meters=1000),
            RunningStepV2("RECOVERY","Easy jog",meters=400),
            RunningStepV2("WORK","User threshold target",meters=1000),
            RunningStepV2("TRANSITION","Easy between blocks",seconds=180,distanceEquivalentMeters=300),
            RunningStepV2("WORK","User speed target",meters=200),
            RunningStepV2("RECOVERY","Easy jog",meters=200),
            RunningStepV2("WORK","User speed target",meters=200),
            RunningStepV2("RECOVERY","Easy jog",meters=200),
            RunningStepV2("WORK","User speed target",meters=200),
            RunningStepV2("RECOVERY","Easy jog",meters=200),
            RunningStepV2("WORK","User speed target",meters=200),
            RunningStepV2("COOL_DOWN","Easy",meters=5800)
        ),reconciledDistanceMeters=13300)
        val input=request(1).copy(weeks=1,runDays=3,days=(1..7).toSet(),weeklyKm=40.0,
            targetWeeklyKm=40.0,longestKm=30.0,longDay=6,
            weeklySlots=mapOf("run" to listOf(6,2,4)),qualityDays=setOf(2),qualityCount=1,
            userDirectedPlanning=true,userDefinedQualityWorkouts=mapOf("w1-run1" to workout))
        val result=Engine15IndependentPlanGenerator.preview(input,owner,"two-block",now)
        assertTrue("Expected a complete multi-block candidate, got $result",result is Engine15IndependentPlanDecision.Candidate)
        val plan=(result as Engine15IndependentPlanDecision.Candidate).plan
        val session=plan.sessions.single { it.quality }
        assertEquals(13.3,session.distanceKm!!,1e-6)
        assertEquals(workout,session.runningPrescription)
        assertTrue(session.steps.any { it.contains("transition") && it.contains("300 m") })
        val saved=Engine15IndependentPlanGenerator.generateWithHistory(input,owner,emptyList(),"two-block-saved",now)
        assertEquals(workout,PlanSnapshot.decode(saved.encode(),owner).sessions.single { it.quality }.runningPrescription)
    }

    @Test fun twoUserDefinedQualitySessionsFitOnlyWhenRunDaysAndSpacingPermit() {
        fun workout()=RunningPrescription("QUALITY","Threshold",listOf(
            RunningSegment("WARM_UP",2000,"Easy"),RunningSegment("WORK",1000,"User threshold target"),
            RunningSegment("RECOVERY",400,"Easy jog"),RunningSegment("WORK",1000,"User threshold target"),
            RunningSegment("COOL_DOWN",4400,"Easy")
        ))
        val input=request(1).copy(weeks=1,runDays=5,days=(1..7).toSet(),weeklyKm=50.0,
            targetWeeklyKm=50.0,longestKm=30.0,longDay=6,weeklySlots=mapOf("run" to listOf(6,2,4,1,5)),
            qualityCount=2,qualityDays=setOf(2,4),userDirectedPlanning=true,
            userDefinedQualityWorkouts=mapOf("w1-run1" to workout(),"w1-run2" to workout()))
        val result=Engine15IndependentPlanGenerator.preview(input,owner,"two-quality",now)
        assertTrue("Expected spaced user-defined sessions, got $result",result is Engine15IndependentPlanDecision.Candidate)
        val plan=(result as Engine15IndependentPlanDecision.Candidate).plan
        assertEquals(2,plan.sessions.count { it.quality })
        assertEquals(50.0,plan.sessions.sumOf { it.distanceKm!! },1e-6)
        assertEquals(setOf(2,4),plan.sessions.filter { it.quality }.map { it.date.dayOfWeek.value }.toSet())
    }

    @Test fun eightWeekPlanKeepsStructuredQualityInLoadingWeeksAndEasyRecoveryWeeks() {
        val workout=RunningPrescription("QUALITY","Intervals",listOf(
            RunningSegment("WARM_UP",2000,"Easy"),RunningSegment("WORK",1000,"User supplied threshold"),
            RunningSegment("RECOVERY",400,"Easy jog"),RunningSegment("WORK",1000,"User supplied threshold"),
            RunningSegment("COOL_DOWN",4400,"Easy")
        ))
        val inputs=(1..8).filter { it%4!=0 }.associate { "w$it-run1" to workout }
        val input=request(8).copy(weeks=8,runDays=5,days=(1..7).toSet(),weeklyKm=50.0,
            targetWeeklyKm=50.0,longestKm=30.0,longDay=6,weeklySlots=mapOf("run" to listOf(6,2,4,1,5)),
            qualityCount=1,qualityDays=setOf(2),userDirectedPlanning=true,userDefinedQualityWorkouts=inputs)
        val result=Engine15IndependentPlanGenerator.preview(input,owner,"eight-structured",now)
        assertTrue("Expected a complete eight-week plan, got $result",result is Engine15IndependentPlanDecision.Candidate)
        val plan=(result as Engine15IndependentPlanDecision.Candidate).plan
        assertEquals(40,plan.sessions.size)
        for(week in 0 until 8) {
            val rows=plan.sessions.filter { it.date>=input.start.plusWeeks(week.toLong()) && it.date<input.start.plusWeeks((week+1).toLong()) }
            assertEquals(if(week%4==3)40.0 else 50.0,rows.sumOf { it.distanceKm!! },1e-6)
            assertEquals(if(week%4==3)0 else 1,rows.count { it.quality })
        }
        assertTrue(plan.sessions.filter { it.quality }.all { it.runningPrescription==workout })
    }

    @Test fun defersMissingOrUnclassifiedTrainingHistory() {
        for (input in listOf(
            request().copy(weeklyKm = 0.0, recentMileageBasis = "UNKNOWN"),
            request().copy(recentMileageBasis = "LEGACY_UNSPECIFIED")
        )) {
            assertTrue(Engine15IndependentPlanGenerator.preview(input, owner, "history", now)
                is Engine15IndependentPlanDecision.Deferred)
        }
    }

    @Test fun incompleteRecordedHistoryCannotBecomeTheWeeklyMileageBaseline() {
        val decision = Engine15IndependentPlanGenerator.preview(
            request().copy(recentMileageBasis = "RECORDED_PARTIAL"), owner, "partial", now
        )
        assertTrue(decision is Engine15IndependentPlanDecision.Deferred)
        assertEquals("ATHLETE_BASELINE_NEEDS_BASELINE",
            (decision as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test fun conflictingTrainingHistoryIsDeferredForReview() {
        val decision = Engine15IndependentPlanGenerator.preview(
            request().copy(reportedPeakWeeklyKm = 60.0), owner, "conflict", now
        )
        assertTrue(decision is Engine15IndependentPlanDecision.Deferred)
        assertEquals("ATHLETE_BASELINE_NEEDS_REVIEW",
            (decision as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test fun beginnerIntermediateAndAdvancedProfilesUseReportedInputsWithoutInventedQuality() {
        for (experience in 0..2) {
            val decision = Engine15IndependentPlanGenerator.preview(
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
        val decision = Engine15IndependentPlanGenerator.preview(input, owner, "sixteen", now)
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
        val candidate = Engine15IndependentPlanGenerator.preview(request(), owner, "readonly", now)
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
        val hold = Engine15IndependentPlanGenerator.preview(
            request().copy(runningRecoveryHold = true), owner, "hold", now
        )
        assertTrue(hold is Engine15IndependentPlanDecision.Deferred)
        assertEquals("RUNNING_RECOVERY_HOLD", (hold as Engine15IndependentPlanDecision.Deferred).reasonCode)

        val mixed = Engine15IndependentPlanGenerator.preview(
            request().copy(calDays = 1), owner, "mixed", now
        )
        assertTrue(mixed is Engine15IndependentPlanDecision.Deferred)
        assertEquals("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT",
            (mixed as Engine15IndependentPlanDecision.Deferred).reasonCode)
    }

    @Test fun arbitraryStartDateAndExactSessionLockAreHonored() {
        val input = request().copy(start = LocalDate.parse("2026-11-17"))
        val shifted = Engine15IndependentPlanGenerator.preview(input, owner, "shifted", now)
        assertTrue("Expected candidate, got $shifted", shifted is Engine15IndependentPlanDecision.Candidate)
        val sessions = (shifted as Engine15IndependentPlanDecision.Candidate).plan.sessions
        assertTrue(sessions.all { it.date >= input.start && it.date < input.start.plusWeeks(input.weeks.toLong()) })

        val lockedId = "locked-w1-run1"
        val lockedInput = input.copy(lockedSessionDays = mapOf(lockedId to "2026-11-18"))
        val locked = Engine15IndependentPlanGenerator.preview(lockedInput, owner, "locked", now)
        assertTrue("Expected locked candidate, got $locked", locked is Engine15IndependentPlanDecision.Candidate)
        assertEquals(LocalDate.parse("2026-11-18"),
            (locked as Engine15IndependentPlanDecision.Candidate).plan.sessions.single { it.id == lockedId }.date)

        val longLock = Engine15IndependentPlanGenerator.preview(
            input.copy(lockedSessionDays = mapOf("prior-w1-run0" to "2026-11-21")), owner, "locked-long", now
        ) as Engine15IndependentPlanDecision.Candidate
        assertEquals(LocalDate.parse("2026-11-21"), longLock.plan.sessions.single {
            it.kind == "long" && it.date < input.start.plusWeeks(1)
        }.date)
    }

    @Test fun explicitRunningWeekdayAssignmentsArePreservedInEveryWeek() {
        val assigned = listOf(6, 1, 2, 3, 5, 7)
        val input = request().copy(weeklySlots = mapOf("run" to assigned))
        val result = Engine15IndependentPlanGenerator.preview(input, owner, "assigned", now)
        assertTrue("Expected candidate, got $result", result is Engine15IndependentPlanDecision.Candidate)
        val sessions = (result as Engine15IndependentPlanDecision.Candidate).plan.sessions
        for (week in 1..input.weeks) {
            for (runIndex in assigned.indices) {
                val session = sessions.single { it.id == "assigned-w$week-run$runIndex" }
                assertEquals(assigned[runIndex], session.date.dayOfWeek.value)
            }
        }
    }

    @Test fun appGenerationReturnsAReviewableManagedDraftAndKeepsQualityDeferred() {
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(request(), owner, emptyList(), "app-draft", now)
        assertEquals(ENGINE15_INDEPENDENT_RUNNING_POLICY, plan.policyId)
        assertEquals("draft", plan.status)
        assertTrue(Engine1Engines.canManage(plan))
        assertTrue(plan.sessions.all { it.discipline == "run" && !it.quality })
        val decoded = PlanSnapshot.decode(plan.encode(), owner)
        assertEquals(plan, decoded)
        assertNull(decoded.foreignSnapshot)

        val error = assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(request().copy(qualityCount = 1), owner, emptyList(), "quality", now)
        }
        assertTrue(error.message.orEmpty().contains("QUALITY_GENERATION_NOT_QUALIFIED"))
    }

    @Test fun directGenerationWithoutRepositoryCalendarContextFailsClosed() {
        val error = assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generate(request(), owner, "missing-calendar", now)
        }
        assertTrue(error.message.orEmpty().contains("EXTERNAL_PLAN_CONTEXT_REQUIRED"))
    }

    @Test fun savedHardSessionsAreCheckedAndOtherOwnersAreIsolated() {
        val strength = PlannedSession("leg-day", LocalDate.parse("2026-11-20"), "str",
            "Lower body strength", 45, null, "strength", lower = true)
        val saved = PlanSnapshot("saved-strength", owner, ENGINE1_VERSION, ENGINE1_POLICY, now,
            PlanRequest(start = request().start, weeks = 8, strengthDays = 1), listOf(strength),
            emptyList(), status = "approved")
        val input = request().copy(runDays = 3, days = setOf(2, 4, 6), longDay = 6,
            weeklyKm = 30.0, targetWeeklyKm = 30.0, longestKm = 10.0)
        val blocked = assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(input, owner, listOf(saved), "blocked", now)
        }
        assertTrue(blocked.message.orEmpty().contains("EXTERNAL_HARD_TOO_CLOSE_TO_LONG"))

        val foreign = Engine15IndependentPlanGenerator.generateWithHistory(
            input, owner, listOf(saved.copy(owner = "another-owner")), "isolated", now
        )
        assertTrue(foreign.sessions.all { it.date.dayOfWeek.value in input.days })
    }

    @Test fun savedQualityConditioningSessionIsNeverDowngradedToEasyExternalLoad() {
        val session = PlannedSession("hard-conditioning", LocalDate.parse("2026-11-20"), "cond",
            "Conditioning intervals", 35, null, "conditioning", quality = true)
        val saved = PlanSnapshot("saved-conditioning", owner, ENGINE1_VERSION, ENGINE1_POLICY, now,
            PlanRequest(start = request().start, weeks = 8, conditioningDays = 1), listOf(session),
            emptyList(), status = "approved")
        val input = request().copy(runDays = 3, days = setOf(2, 4, 6), longDay = 6,
            weeklyKm = 30.0, targetWeeklyKm = 30.0, longestKm = 10.0)
        val error = assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(input, owner, listOf(saved), "conditioning", now)
        }
        assertTrue(error.message.orEmpty().contains("EXTERNAL_HARD_TOO_CLOSE_TO_LONG"))
    }

    @Test fun defersDoubleDaysAndUnmappableSessionLocks() {
        val doubleDays = Engine15IndependentPlanGenerator.preview(
            request().copy(allowDoubleDays = true), owner, "double", now
        )
        assertTrue(doubleDays is Engine15IndependentPlanDecision.Deferred)
        assertEquals("DOUBLE_DAYS_UNSUPPORTED", (doubleDays as Engine15IndependentPlanDecision.Deferred).reasonCode)

        val locked = Engine15IndependentPlanGenerator.preview(
            request().copy(lockedSessionDays = mapOf("unknown-session" to "2026-11-16")),
            owner, "candidate", now
        )
        assertTrue(locked is Engine15IndependentPlanDecision.Deferred)
        assertEquals("SESSION_LOCK_MAPPING_UNSUPPORTED",
            (locked as Engine15IndependentPlanDecision.Deferred).reasonCode)
        val staleLock = Engine15IndependentPlanGenerator.preview(
            request().copy(lockedSessionDays = mapOf("previous-w9-run1" to "2026-11-16")),
            owner, "candidate", now
        )
        assertTrue(staleLock is Engine15IndependentPlanDecision.Deferred)
        assertEquals("SESSION_LOCK_MAPPING_UNSUPPORTED",
            (staleLock as Engine15IndependentPlanDecision.Deferred).reasonCode)

        val tightTime = Engine15IndependentPlanGenerator.preview(
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
            "Engine15StaticMultiDisciplinePlanner.kt",
            "Engine15StrengthWorkoutComposer.kt",
            "Engine15CalisthenicsWorkoutComposer.kt",
            "Engine15ConditioningWorkoutComposer.kt",
            "Engine15AthleteCapacity.kt",
            "Engine15IndependentVolumePlanner.kt",
            "Engine15M5WorkoutComposer.kt",
            "Engine15M5QualityPreviewComposer.kt",
            "Engine15WeeklyVolumeSkeleton.kt",
            "Engine15M6IndependentCalendar.kt",
            "Engine15M6IndependentInterferenceReview.kt",
            "Engine15M6IndependentPeriodizationReview.kt",
            "Engine15M6IndependentQualitySpacing.kt",
            "Engine15QualityFamilySelection.kt"
        )
        val forbidden = Regex("\\b(?:Engine1\\.(?:generate|validate)|MultiSessionEngine1|CoordinatedEngine1|Engine15InterferenceScheduling|Engine1Engines)\\b")
        for (file in files) {
            val source = java.io.File(root, file).readText()
            assertFalse("Legacy planning dependency in $file", forbidden.containsMatchIn(source))
        }
    }
}
