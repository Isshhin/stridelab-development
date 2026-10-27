package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine1Test {
    private val start=LocalDate.parse("2026-11-02");private val now=Instant.parse("2026-10-26T00:00:00Z")
    private fun generate(p:PlanRequest)=Engine1.generate(p,"synthetic-owner","stable-plan",now)
    @Test fun beginnerWithoutWatchGetsActualRunWalkAndNoInventedMileageOrQuality() {
        val p=generate(PlanRequest(start=start,weeklyKm=0.0))
        assertEquals(12,p.sessions.size);assertTrue(p.sessions.all { it.steps.size>=3&&it.minutes in 10..45 })
        assertTrue(p.sessions.all { it.distanceKm==null&&!it.quality });assertEquals("draft",p.status)
        assertEquals(ENGINE1_VERSION,p.engineVersion);assertEquals(ENGINE1_POLICY,p.policyId)
    }
    @Test fun experiencedMileageQualityAndLongDayRemainTruthfulAndSpaced() {
        val p=generate(PlanRequest(start=start,experience=2,weeklyKm=30.0,longestKm=10.0,runDays=4,qualityCount=1,qualityDays=setOf(2),days=setOf(1,2,4,5,6),longDay=6))
        assertEquals(16,p.sessions.size)
        assertTrue(p.sessions.filter { it.kind=="long" }.all { it.date.dayOfWeek.value==6 })
        assertTrue(p.sessions.filter { it.quality }.all { it.date.dayOfWeek.value==2 })
        assertTrue(p.sessions.all { it.minutes<=45 });assertTrue(p.sessions.all { it.distanceKm!=null })
        assertFalse(p.sessions.any { a->p.sessions.any { b->a.id!=b.id&&Engine1.conflict(a,b) } })
    }
    @Test fun hybridKeepsEveryDisciplineAndDoesNotPutLegWorkBeforeLongOrNextToQuality() {
        val request=PlanRequest(start=start,experience=1,weeklyKm=25.0,longestKm=8.0,runDays=3,strengthDays=2,calDays=1,conditioningDays=1,days=(1..7).toSet(),qualityCount=1,qualityDays=setOf(2),minutes=60)
        val plan=generate(request)
        assertEquals(28,plan.sessions.size)
        assertEquals(setOf("run","str","cal","cond"),plan.sessions.map { it.discipline }.toSet())
        assertFalse(plan.sessions.any { a->plan.sessions.any { b->a.id!=b.id&&Engine1.conflict(a,b) } })
        assertTrue(plan.sessions.filter { it.discipline=="str"||it.discipline=="cal" }.all { it.exercises.size>=2&&it.exercises.all { e->e.sets>0&&(e.reps!=null||e.seconds!=null) } })
    }
    @Test fun impossibleRequestsExplainRatherThanDropSessions() {
        assertThrows(PlanConstraint::class.java) { generate(PlanRequest(start=start,runDays=3,strengthDays=2,calDays=2,days=setOf(1,2,4))) }
        assertThrows(PlanConstraint::class.java) { generate(PlanRequest(start=start,experience=2,weeklyKm=30.0,longestKm=8.0,runDays=3,qualityCount=1,qualityDays=setOf(2),longDay=3,days=setOf(1,2,3))) }
    }
    @Test fun equipmentQuantitiesAndNoEquipmentOptionsAreReal() {
        val bare=generate(PlanRequest(start=start,runDays=0,strengthDays=2))
        assertFalse(bare.sessions.flatMap { it.exercises }.any { it.name.contains("Inverted row")||it.equipment.contains("Dumbbell",true) })
        val one=generate(PlanRequest(start=start,runDays=0,strengthDays=2,equipment=mapOf("db" to 1)))
        assertTrue(one.sessions.flatMap { it.exercises }.any { it.id=="gs" });assertFalse(one.sessions.flatMap { it.exercises }.any { it.id=="fp" })
    }
    @Test fun beginnerCalisthenicsUsesAssistanceAndNeverAutoPromotesByWeek() {
        val p=generate(PlanRequest(start=start,runDays=0,calDays=2,weeks=8,calLevel=0))
        assertTrue(p.sessions.all { s->s.exercises.any { it.name=="Wall push-up" } })
        assertFalse(p.sessions.flatMap { it.exercises }.any { it.name=="Pull-up"||it.name.contains("Archer") })
        assertThrows(PlanConstraint::class.java) { generate(PlanRequest(start=start,goal="Pull-up progression",runDays=0,calDays=2)) }
    }
    @Test fun generationIsDeterministicAndExclusionsAreRespected() {
        val request=PlanRequest(start=start,runDays=0,strengthDays=2,excluded=setOf("sq"))
        assertEquals(generate(request),generate(request));assertFalse(generate(request).sessions.flatMap { it.exercises }.any { it.id=="sq" })
    }
    @Test fun savedContractKeepsPrescriptionsUnderUnknownFutureEngineAndRejectsOtherOwner() {
        val original=generate(PlanRequest(start=start));val j=original.encode().put("engineVersion","2.0-future")
        j.put("futurePolicy",org.json.JSONObject().put("opaqueRule",7))
        j.getJSONArray("sessions").getJSONObject(0).put("futureSession", "retain me")
        val decoded=PlanSnapshot.decode(j,"synthetic-owner")
        assertEquals(original.sessions,decoded.sessions);assertEquals("2.0-future",decoded.engineVersion)
        assertEquals(7,decoded.encode().getJSONObject("futurePolicy").getInt("opaqueRule"))
        assertEquals("retain me",decoded.encode().getJSONArray("sessions").getJSONObject(0).getString("futureSession"))
        assertThrows(PlanConstraint::class.java) { PlanOperations.approve(decoded,emptyList()) }
        assertThrows(PlanConstraint::class.java) { PlanOperations.acceptSuggestion(decoded,decoded.sessions.first().id) }
        assertThrows(IllegalArgumentException::class.java) { PlanSnapshot.decode(j,"another-owner") }
    }
    @Test fun approvalIsExplicitAndConflictingAcceptedPlansAreRetained() {
        val draft=generate(PlanRequest(start=start));assertEquals("draft",draft.status)
        val approved=PlanOperations.approve(draft,emptyList());assertEquals("approved",approved.status)
        assertThrows(PlanConstraint::class.java) { PlanOperations.approve(draft.copy(id="other"),listOf(approved)) }
        assertEquals(draft.sessions,approved.sessions)
    }
    @Test fun realLogsStayMissingUntilEnteredAndCompletionCannotDuplicateOrRegenerate() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start)),emptyList());val s=p.sessions.first()
        val log=WorkoutLog(now,minutes=18.0,effort=3,notes="Felt comfortable")
        val done=PlanOperations.record(p,s.id,log,true)
        assertEquals(1,done.sessions.count { it.status=="completed" });assertNull(done.sessions.first { it.id==s.id }.log!!.distanceKm)
        assertEquals(done,PlanOperations.record(done,s.id,log,true));assertEquals(p.sessions.map { it.id },done.sessions.map { it.id })
    }
    @Test fun missedReasonCreatesOnlyPendingReductionAndPreservesOriginalPrescription() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start)),emptyList());val first=p.sessions.first()
        val missed=PlanOperations.missed(p,first.id,"Fatigue",false)
        val suggested=missed.sessions.first { it.suggestion!=null }
        assertFalse(suggested.suggestion!!.accepted);assertEquals(p.sessions.find { it.id==suggested.id }!!.minutes,suggested.minutes)
        val accepted=PlanOperations.acceptSuggestion(missed,suggested.id).sessions.first { it.id==suggested.id }
        assertTrue(accepted.effectiveMinutes<=accepted.minutes);assertEquals(p.sessions.find { it.id==suggested.id }!!.steps,accepted.steps)
    }
    @Test fun feedbackRecordsEngineContextAndInvalidActualsAreRejected() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start)),emptyList())
        val feedback=PlanOperations.feedback(p,"Inappropriate prescription","This session needs review",p.sessions.first().id,now)
        assertEquals(1,feedback.feedback.size);assertEquals(p.policyId,feedback.policyId)
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,p.sessions.first().id,WorkoutLog(now,minutes=Double.NaN),true) }
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,p.sessions.first().id,WorkoutLog(now,effort=11),true) }
    }
    @Test fun backupKeepsAcceptedSnapshotsAndSkipsExistingIdsWithoutReplacingLogs() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start)),emptyList())
        val done=PlanOperations.record(p,p.sessions.first().id,WorkoutLog(now,minutes=22.0),true)
        val raw=org.json.JSONObject().put("schemaVersion",1).put("plans",org.json.JSONArray().put(done.encode())).toString()
        assertEquals(done,PlanBackup.read(raw,"synthetic-owner").single())
        assertTrue(PlanBackup.additions(listOf(p),listOf(done)).isEmpty())
        assertThrows(PlanConstraint::class.java) { PlanBackup.read(raw,"different-owner") }
        assertThrows(PlanConstraint::class.java) { PlanBackup.read(raw+" trailing", "synthetic-owner") }
        assertThrows(PlanConstraint::class.java) { PlanBackup.additions(listOf(p.copy(id="new")),listOf(p)) }
    }
    @Test fun shelfRoundtripIsOwnerBoundedAndCollectionRemovalKeepsFavorites() {
        val shelf=LibraryShelf().change("sq").change("sq","At home").change("sq","At home",true)
        assertTrue("sq" in shelf.favorites);assertTrue(shelf.collections.getValue("At home").isEmpty())
        assertEquals(shelf,LibraryShelf.decode(shelf.encode("synthetic-owner"),"synthetic-owner"))
        assertThrows(IllegalArgumentException::class.java) { LibraryShelf.decode(shelf.encode("synthetic-owner"),"different-owner") }
        assertThrows(PlanConstraint::class.java) { shelf.change("sq","Favorites") }
    }
    @Test fun explicitActualDateAndManualSourceLinksPreserveOriginalTargets() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start)),emptyList());val s=p.sessions.first()
        val linked=PlanOperations.linkActivity(p,s.id,"real-source-id")
        assertEquals(s.copy(status="completed",linkedActivityId="real-source-id",linkPreviousStatus="planned"),linked.sessions.first())
        assertEquals(p,PlanOperations.linkActivity(linked,s.id,null))
        val actual=LocalDate.now().minusDays(1)
        val done=PlanOperations.record(p,s.id,WorkoutLog(now,minutes=15.0,performedOn=actual),true)
        assertEquals(actual,PlanSnapshot.decode(done.encode(),"synthetic-owner").sessions.first().log!!.performedOn)
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,s.id,WorkoutLog(now,performedOn=LocalDate.now().plusDays(1)),true) }
    }
    @Test fun assistedPullingNeverAssumesUnselectedFootSupport() {
        val p=PlanRequest(start=start,goal="Pull-up progression",runDays=0,calDays=2,equipment=mapOf("bar" to 1))
        assertThrows(PlanConstraint::class.java) { generate(p) }
        assertTrue(generate(p.copy(equipment=mapOf("bar" to 1,"support" to 1))).sessions.flatMap { it.exercises }.any { it.id=="supported-hang" })
        assertFalse(generate(p.copy(goal="Calisthenics foundation")).sessions.flatMap { it.exercises }.any { it.id=="supported-hang" })
    }
    @Test fun reviewedExerciseSwapPreservesOriginalAndRecordsOnlyTheNewUnits() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start,runDays=0,strengthDays=2)),emptyList());val session=p.sessions.first();val original=session.exercises.first()
        val swapped=PlanOperations.swapExercise(p,session.id,original.id,now)
        val updated=swapped.sessions.first();assertEquals(session.exercises,updated.exercises);assertEquals(1,updated.swaps.size)
        val replacement=updated.effectiveExercises.first();assertTrue(replacement.id.startsWith("swap-"));assertTrue(replacement.sets<=original.sets)
        val done=PlanOperations.record(swapped,session.id,WorkoutLog(now,sets=listOf(SetRecord(replacement.id,1,reps=5))),true)
        assertEquals(done,PlanSnapshot.decode(done.encode(),"synthetic-owner"))
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(swapped,session.id,WorkoutLog(now,sets=listOf(SetRecord(original.id,1,reps=5))),false) }
    }
    @Test fun completionRequiresPerformedStrengthAndCalisthenicsSetsWithoutRejectingPartialWork() {
        for(discipline in listOf("str","cal")) {
            val request=PlanRequest(start=start,runDays=0,strengthDays=if(discipline=="str") 2 else 0,calDays=if(discipline=="cal") 2 else 0)
            val p=PlanOperations.approve(generate(request),emptyList())
            val session=p.sessions.first()
            val exercise=session.effectiveExercises.first()
            assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,session.id,WorkoutLog(now),true) }
            assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,session.id,WorkoutLog(now,minutes=20.0,notes="Finished"),true) }
            assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,session.id,WorkoutLog(now,sets=listOf(SetRecord(exercise.id,1,kg=4.0))),true) }
            val saved=PlanOperations.record(p,session.id,WorkoutLog(now,minutes=10.0),false)
            assertEquals("in_progress",saved.sessions.first().status)
            val wrongUnits=if(exercise.seconds!=null)SetRecord(exercise.id,1,reps=1)else SetRecord(exercise.id,1,seconds=1)
            assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,session.id,WorkoutLog(now,sets=listOf(wrongUnits)),true) }
            val actual=SetRecord(exercise.id,1,reps=if(exercise.seconds==null) 1 else null,seconds=if(exercise.seconds!=null) 1 else null)
            val completed=PlanOperations.record(saved,session.id,WorkoutLog(now,minutes=10.0,sets=listOf(actual)),true)
            assertEquals("completed",completed.sessions.first().status)
            assertEquals(listOf(actual),completed.sessions.first().log!!.sets)
        }
    }
    @Test fun savedExerciseSetsAndHistoricalWorkoutsCannotBeSwappedAway() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start,runDays=0,strengthDays=2)),emptyList());val session=p.sessions.first();val original=session.exercises.first()
        val partial=PlanOperations.record(p,session.id,WorkoutLog(now,sets=listOf(SetRecord(original.id,1,reps=5))),false)
        assertThrows(PlanConstraint::class.java) { PlanOperations.swapExercise(partial,session.id,original.id,now) }
        val done=PlanOperations.record(p,session.id,WorkoutLog(now,sets=listOf(SetRecord(original.id,1,reps=5))),true)
        assertThrows(PlanConstraint::class.java) { PlanOperations.swapExercise(done,session.id,original.id,now) }
    }
    @Test fun reusableTemplateCreatesNewDatedDraftsWithoutRewritingPriorLogs() {
        val t=SessionTemplate("synthetic-template","Squat practice","str",30,listOf(ExercisePrescription("sq","Bodyweight squat",2,6)))
        val first=t.draft("synthetic-owner",PlanRequest(),LocalDate.now(),"practice-one",now)
        assertEquals("draft",first.status);assertEquals("Squat practice",first.name);assertEquals(1,first.sessions.size)
        val approved=PlanOperations.approve(first,emptyList());val completed=PlanOperations.record(approved,first.sessions.first().id,WorkoutLog(now,minutes=16.0,sets=listOf(SetRecord("sq",1,reps=6))),true)
        val second=t.copy(exercises=listOf(ExercisePrescription("sq","Bodyweight squat",3,8))).draft("synthetic-owner",PlanRequest(),LocalDate.now().plusDays(3),"practice-two",now)
        assertEquals(2,completed.sessions.first().exercises.first().sets);assertEquals(3,second.sessions.first().exercises.first().sets);assertNotEquals(first.sessions.first().id,second.sessions.first().id)
        assertEquals(t,SessionTemplate.decode(t.encode("synthetic-owner"),"synthetic-owner"))
        assertEquals(completed,PlanSnapshot.decode(completed.encode(),"synthetic-owner"))
    }
    @Test fun practiceRejectsMissingEquipmentUncontrolledSkillsAndImpossibleTimeBudget() {
        val date=LocalDate.now()
        val pull=SessionTemplate("pull-template","Pull-up practice","cal",30,listOf(ExercisePrescription("e2","Pull-up",2,1)))
        assertThrows(PlanConstraint::class.java) { pull.draft("synthetic-owner",PlanRequest(),date) }
        assertThrows(PlanConstraint::class.java) { pull.draft("synthetic-owner",PlanRequest(equipment=mapOf("bar" to 1)),date) }
        assertEquals(1,pull.draft("synthetic-owner",PlanRequest(equipment=mapOf("bar" to 1),pullLevel=2),date).sessions.size)
        assertThrows(PlanConstraint::class.java) { pull.copy(minutes=15,exercises=listOf(ExercisePrescription("e2","Pull-up",10,100))).draft("synthetic-owner",PlanRequest(equipment=mapOf("bar" to 1),pullLevel=2),date) }
    }
    @Test fun historicalMissReasonDoesNotRetroactivelyAdaptOldOrCurrentWorkouts() {
        val plan=PlanOperations.approve(generate(PlanRequest(start=LocalDate.now().minusWeeks(3),weeks=5)),emptyList())
        val old=PlanOperations.missed(plan,plan.sessions.first().id,"Illness",false)
        assertTrue(old.sessions.all { it.suggestion==null })
        val recent=plan.sessions.last { it.date<=LocalDate.now() }
        val adjusted=PlanOperations.missed(plan,recent.id,"Fatigue",false)
        assertTrue(adjusted.sessions.filter { it.suggestion!=null }.all { it.date>=LocalDate.now() })
        assertTrue(adjusted.sessions.any { it.suggestion!=null })
    }
}
