package io.stridelab.privatealpha
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class Engine15CoachingUiContractTest {
    @Test fun unconfirmedHistoryNeverPretendsMissingWeeksAreZero() {
        val now=Instant.parse("2026-11-15T12:00:00Z")
        val date=LocalDate.of(2026,11,10)
        val sessions=(0..2).map { i -> PlannedSession("s$i",date.plusDays(i.toLong()),"run","Easy",60,10.0,"easy",status="completed",logRevision=1,log=WorkoutLog(now.minusSeconds(86400),distanceKm=10.0,performedOn=date.plusDays(i.toLong()))) }
        val p=PlanSnapshot("p","o",ENGINE15_VERSION,ENGINE15_POLICY,now,PlanRequest(),sessions,emptyList())
        assertNull(historyWeeklyKm(emptyList(),now,listOf(p),ZoneId.of("UTC")))
    }
    @Test fun reviewedTimeOnlyTargetsAreUnknownInWeekSummary() {
        val s=PlannedSession("s",LocalDate.now(),"run","Run",60,10.0,"easy",suggestion=SessionSuggestion(30,Engine15Feedback.RULE_ID+": accepted",true))
        assertNull(weekReviewDistance(listOf(s)))
    }
    @Test fun qualityActualsAreExplicitValidatedAndResumable() {
        val log=WorkoutLog(Instant.now(),distanceKm=14.0,effort=7,completionDifficulty="As planned",discomfort="None",qualityWorkKm=9.0)
        assertEquals(log,WorkoutLog.decode(log.encode()))
        val draft=WorkoutEntryDraft("o","p","s",0,"","14","7","","2026-11-15","{}",1,completionDifficulty="As planned",discomfort="None",qualityWorkKm="9")
        assertEquals(draft,WorkoutEntryDraft.decode(draft.encode(),"o"))
        try { log.copy(qualityWorkKm=15.0);fail("Work cannot exceed total") } catch(expected:IllegalArgumentException) {}
    }
    @Test fun goalOptionsAreTypedAndPersistWithTheDraft() {
        val p=PlanRequest(goal="Aerobic foundation",experience=2,start=LocalDate.of(2026,11,16),weeks=4,days=(1..7).toSet(),runDays=6,longDay=7,weeklyKm=40.0,longestKm=18.0,planningPace=360.0,policyChoice="engine15",timeBudgetEnabled=false,targetWeeklyKm=45.0)
        val plan=Engine15.generate(p,"o","p",Instant.parse("2026-11-15T12:00:00Z"))
        assertNotNull(plan.mileageOptions)
        assertTrue(plan.mileageOptions!!.lowerTargetKm<45.0)
        assertTrue(plan.mileageOptions!!.longerWeeks!!>4)
        assertEquals(plan,PlanSnapshot.decode(plan.encode(),"o"))
    }
    @Test fun repeatedExplicitThresholdWorkInformsDoseWithoutInventingIntervalsFromTotalDistance() {
        val day=LocalDate.of(2026,11,16);val now=Instant.parse("2026-11-16T12:00:00Z")
        val req=PlanRequest(goal="Aerobic foundation",experience=2,start=day,weeks=4,days=(1..7).toSet(),runDays=6,longDay=7,qualityCount=1,qualityDays=setOf(2),weeklyKm=80.0,longestKm=24.0,planningPace=360.0,policyChoice="engine15",timeBudgetEnabled=false,targetWeeklyKm=80.0)
        val sessions=listOf(8L,1L).map { age ->
            val date=day.minusDays(age)
            PlannedSession("q$age",date,"run","Threshold",90,14.0,"quality",quality=true,status="completed",logRevision=1,
                log=WorkoutLog(now.minusSeconds(age*86400),distanceKm=14.0,effort=7,performedOn=date,completionDifficulty="As planned",discomfort="None",qualityWorkKm=9.0),
                runningPrescription=RunningPrescription("QUALITY","Threshold",listOf(RunningSegment("WORK",9000,"Controlled"),RunningSegment("EASY",5000,"Easy"))))
        }
        val h=PlanSnapshot("h","o",ENGINE15_VERSION,ENGINE15_POLICY,now,req,sessions,emptyList(),status="approved")
        val result=Engine15.generateWithHistory(req,"o",listOf(h),"draft",now)
        val first=result.sessions.first { it.quality }.runningPrescription!!
        assertEquals("Threshold",first.family)
        assertTrue(first.workMeters>2000 && first.workMeters<=9000)
        val unknown=h.copy(sessions=sessions.map { it.copy(log=it.log!!.copy(qualityWorkKm=null)) })
        assertNull(Engine15RunHistory.assess("o",listOf(unknown),now))
    }
    @Test fun savingProgressDoesNotRestoreTheOriginalHardTarget() {
        val s=PlannedSession("s",LocalDate.now(),"run","Threshold",60,10.0,"quality",quality=true,status="in_progress",log=WorkoutLog(Instant.now(),minutes=10.0),suggestion=SessionSuggestion(30,Engine15Feedback.RULE_ID+": accepted",true))
        val p=PlanSnapshot("p","o",ENGINE15_VERSION,ENGINE15_POLICY,Instant.now(),PlanRequest(),listOf(s),emptyList(),status="approved")
        val target=Engine15SessionPresentation.forSession(p,"s")
        assertEquals(30,target.currentMinutes)
        assertNull(target.currentDistanceTargetKm)
    }
    @Test fun completedRunFeedbackIsSeparateFromOriginalMeasurements() {
        val now=Instant.parse("2026-11-15T12:00:00Z");val day=LocalDate.of(2026,11,15)
        val source=PlannedSession("s",day.minusDays(1),"run","Easy",60,10.0,"easy",status="completed",linkedActivityId="activity")
        val target=source.copy(id="next",date=day.plusDays(1),status="planned",linkedActivityId=null)
        val p=PlanSnapshot("p","o",ENGINE15_VERSION,ENGINE15_POLICY,now,PlanRequest(),listOf(source,target),emptyList(),status="approved")
        val saved=PlanOperations.runResponse(p,"s",9,"Harder","Pain",day.minusDays(1),now)
        assertNull(saved.sessions.first().log)
        assertEquals(source.distanceKm,saved.sessions.first().distanceKm)
        assertEquals(source.linkedActivityId,saved.sessions.first().linkedActivityId)
        assertNotNull(saved.sessions.first().runResponse)
        assertEquals(saved,PlanSnapshot.decode(saved.encode(),"o"))
        assertTrue(Engine15WeeklyReview.preview(saved,day,now=now).changes.isNotEmpty())
        val undated=PlanOperations.runResponse(p,"s",9,"Harder","Pain",null,now)
        assertTrue(Engine15WeeklyReview.preview(undated,day,now=now).changes.isEmpty())
    }
    @Test fun feedbackDoesNotFollowAReplacedSourceAndKnownActualDatesSurviveOverlay() {
        val now=Instant.parse("2026-11-15T12:00:00Z");val day=LocalDate.of(2026,11,14)
        val original=WorkoutLog(now,minutes=45.0)
        val session=PlannedSession("s",day,"run","Easy",60,10.0,"easy",log=original)
        val p=PlanSnapshot("p","o",ENGINE15_VERSION,ENGINE15_POLICY,now,PlanRequest(),listOf(session),emptyList(),status="approved")
        val linked=PlanOperations.linkActivity(p,"s","activity-a")
        val responded=PlanOperations.runResponse(linked,"s",8,"Harder","Pain",day,now)
        assertEquals(day,responded.sessions.single().coachingLog()!!.performedOn)
        assertNull(responded.sessions.single().log!!.performedOn)
        val unlinked=PlanOperations.linkActivity(responded,"s",null)
        val replaced=PlanOperations.linkActivity(unlinked,"s","activity-b")
        assertNull(replaced.sessions.single().runResponse)
        assertEquals(original,replaced.sessions.single().log)
        assertTrue(replaced.feedback.any { it.text.contains("activity-a") && it.text.contains("Pain") })
    }
}
