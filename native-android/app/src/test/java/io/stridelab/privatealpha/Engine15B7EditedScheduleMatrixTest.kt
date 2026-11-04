package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** B7: fixed integration checks for consented edits, conflicts, and backup. */
class Engine15B7EditedScheduleMatrixTest {
    private val start=LocalDate.parse("2026-11-09")
    private val now=Instant.parse("2026-11-03T00:00:00Z")
    private val owner="b7-edit-owner"
    private fun req()=PlanRequest(
        goal="Improve 5K",policyChoice="engine15",start=start,weeks=4,
        days=setOf(2,3,4,5,6,7),runDays=5,strengthDays=2,calDays=2,
        longDay=7,qualityDays=setOf(2),qualityCount=1,
        weeklyKm=65.0,longestKm=16.0,planningPace=400.0,
        minutes=45,dailyMinutes=120,experience=2,strengthExperience=2,
        equipment=mapOf("db" to 2,"support" to 1)
    )
    private fun plan()=PlanOperations.approve(
        Engine15.generate(req(),owner,"b7-edited-plan",now),emptyList())

    @Test fun completedLogLockAndAvailabilityRepairKeepUnaffectedHistoryIntact() {
        val original=plan()
        val quality=original.sessions.first { it.quality }
        val locked=PlanOperations.setSessionLock(original,quality.id,true)
        val completed=locked.sessions.first {
            it.kind=="easy" && it.discipline=="run" &&
                it.date.dayOfWeek.value!=5
        }
        // Actual history occurs on the scheduled workout day, after plan creation.
        val performedAt=completed.date.atTime(18,0).toInstant(ZoneOffset.UTC)
        assertTrue(performedAt.isAfter(now))
        val actual=WorkoutLog(performedAt,minutes=35.0,distanceKm=5.0,effort=4)
        val recorded=PlanOperations.record(locked,completed.id,actual,true)
        val newDays=setOf(1,2,3,4,6,7)
        val displaced=recorded.sessions.filter {
            it.status=="planned" && it.date.dayOfWeek.value==5
        }.map { it.id }.toSet()
        assertTrue(displaced.isNotEmpty())

        val revised=PlanOperations.updateAvailability(
            recorded,newDays,asOf=completed.date.plusDays(1))
        assertEquals("paused",revised.status)
        assertEquals(newDays,revised.request.days)
        assertEquals(original.sessions.size,revised.sessions.size)
        assertEquals(original.sessions.map { it.id }.toSet(),
            revised.sessions.map { it.id }.toSet())
        assertEquals(quality.date.toString(),
            revised.request.lockedSessionDays[quality.id])
        val history=revised.sessions.single { it.id==completed.id }
        assertEquals("completed",history.status)
        assertEquals(completed.date,history.date)
        assertEquals(actual,history.log)
        assertEquals(recorded.sessions.filterNot { it.id in displaced },
            revised.sessions.filterNot { it.id in displaced })
        assertEquals(original.sessions.sumOf { it.distanceKm?:0.0 },
            revised.sessions.sumOf { it.distanceKm?:0.0 },0.0)

        val active=revised.sessions.filter {
            it.status in setOf("planned","in_progress")
        }
        assertTrue(active.all { it.date.dayOfWeek.value in newDays })
        assertTrue(active.groupBy { it.date }.values.all { day ->
            day.size<=2 && day.sumOf { it.effectiveMinutes }<=revised.request.dailyMinutes
        })
        for(i in active.indices)for(j in i+1 until active.size)
            assertFalse("Unsafe B7 edit ${active[i].id} vs ${active[j].id}",
                PlanScheduleValidation.conflict(active[i],active[j],
                    revised.policyId,revised.policyId))

        val encoded=revised.encode()
        assertEquals(revised,PlanSnapshot.decode(encoded,owner))
        val backup=JSONObject().put("schemaVersion",1)
            .put("plans",JSONArray().put(encoded)).toString()
        assertEquals(listOf(revised),PlanBackup.read(backup,owner))
        assertTrue(PlanBackup.additions(listOf(revised),listOf(original)).isEmpty())
        assertThrows(PlanConstraint::class.java) {
            PlanBackup.read(backup,"different-owner")
        }
        if(revised.sessions.any { it.status=="unassigned" })
            assertThrows(PlanConstraint::class.java) {
                PlanOperations.approve(revised,emptyList())
            }
        else {
            val approved=PlanOperations.approve(revised,emptyList())
            PlanScheduleValidation.validatePlan(approved)
        }
    }

    @Test fun unavailableLongPreferenceLeavesFourHistoricalLongRunsForReview() {
        val original=plan()
        val oldLongs=original.sessions.filter { it.kind=="long" }
        assertEquals(req().weeks,oldLongs.size)
        val newDays=setOf(1,2,3,4,5,6)
        val changed=PlanOperations.updateAvailability(original,newDays,asOf=start)
        assertEquals(original.sessions.size,changed.sessions.size)
        assertEquals(original.sessions.map { it.id }.toSet(),
            changed.sessions.map { it.id }.toSet())
        assertEquals("paused",changed.status)
        for(long in oldLongs) {
            val kept=changed.sessions.single { it.id==long.id }
            assertEquals("unassigned",kept.status)
            assertEquals(long.date,kept.date)
        }
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.approve(changed,emptyList())
        }
    }

    @Test fun unlockedManualMoveIntoConflictingQualityDayIsRejectedWithoutChangingPlan() {
        val original=plan()
        val quality=original.sessions.first { it.quality && it.status=="planned" }
        val easy=original.sessions.first {
            it.discipline=="run" && it.kind=="easy" && it.status=="planned" &&
                it.date!=quality.date
        }
        assertFalse(original.request.lockedSessionDays.containsKey(easy.id))
        assertTrue(quality.date.dayOfWeek.value in original.request.days)
        assertTrue(quality.date>=original.request.start)
        assertTrue(quality.date<original.request.start.plusWeeks(original.request.weeks.toLong()))
        assertTrue(
            "Fixture must exercise recovery/scheduling conflict, not an invalid destination",
            PlanScheduleValidation.conflict(
                easy.copy(date=quality.date),quality,original.policyId,original.policyId)
        )
        val unchanged=original.encode().toString()
        val failure=assertThrows(PlanConstraint::class.java) {
            PlanOperations.move(original,easy.id,quality.date)
        }
        assertTrue(
            "Rejection must come from the scheduling conflict guard: ${failure.message}",
            failure.message.orEmpty().contains("conflicts with another workout")
        )
        assertEquals(easy.date,original.sessions.single { it.id==easy.id }.date)
        assertEquals(unchanged,original.encode().toString())
        PlanScheduleValidation.validatePlan(original)
        assertEquals(original,PlanSnapshot.decode(original.encode(),owner))
    }

    @Test fun lockedPlacementStopsUnavailableDayAndManualUnsafeMove() {
        val original=plan()
        val friday=original.sessions.first {
            it.date.dayOfWeek.value==5 && it.status=="planned"
        }
        val locked=PlanOperations.setSessionLock(original,friday.id,true)
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.updateAvailability(
                locked,setOf(1,2,3,4,6,7),asOf=start)
        }
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.move(locked,friday.id,friday.date.plusDays(1))
        }
        assertEquals(friday.date,
            locked.sessions.single { it.id==friday.id }.date)
        assertEquals(locked,PlanSnapshot.decode(locked.encode(),owner))
    }
}
