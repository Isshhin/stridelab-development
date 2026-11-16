package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ActivityPlanLinkWindowTest {
    private val owner="synthetic"
    private val recorded=Instant.parse("2026-11-19T10:00:00Z")
    private val date=LocalDate.parse("2026-11-19")
    private fun run(id:String,offset:Long,discipline:String="run",
        status:String="planned",linked:String?=null)=PlannedSession(
            id,date.plusDays(offset),discipline,id,45,8.0,"easy",
            status=status,linkedActivityId=linked
        )
    private fun plan(vararg sessions:PlannedSession)=PlanSnapshot(
        "plan",owner,ENGINE15_VERSION,ENGINE15_POLICY,Instant.EPOCH,
        PlanRequest(policyChoice="engine15"),
        sessions.toList(),emptyList(),status="approved"
    )
    @Test fun onlyActivityDayAndThreePrecedingDaysAreSelectable() {
        val rows=listOf(
            run("old",-4),run("three",-3),run("two",-2),run("one",-1),
            run("same",0),run("future",1),
            run("strength",-2,"str"),run("linked",0,linked="a"),
            run("missed",0,status="missed"),run("skipped",0,status="skipped")
        )
        val result=eligiblePlannedRunsForLink(listOf(plan(*rows.toTypedArray())),
            recorded,ZoneOffset.UTC)
        assertEquals(listOf("same","one","two","three"),result.map { it.second.id })
        assertTrue(result.none { it.second.linkedActivityId!=null })
    }
    @Test fun usesLocalRecordedDateNotUtcDay() {
        val instant=Instant.parse("2026-11-19T19:30:00Z")
        val rows=listOf(run("three",-2),run("outside",-3),
            run("recorded-local",1),run("future-local",2))
        val result=eligiblePlannedRunsForLink(listOf(plan(*rows.toTypedArray())),
            instant,ZoneId.of("Asia/Manila"))
        assertEquals(listOf("recorded-local","three"),result.map { it.second.id })
    }
    @Test fun neverShowsUnapprovedOrArchivedPlans() {
        val saved=plan(run("eligible",0))
        val result=eligiblePlannedRunsForLink(listOf(
            saved.copy(status="draft"),saved.copy(status="archived")
        ),recorded,ZoneOffset.UTC)
        assertTrue(result.isEmpty())
    }
}
