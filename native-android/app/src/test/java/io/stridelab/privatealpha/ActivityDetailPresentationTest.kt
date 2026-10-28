package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ActivityDetailPresentationTest {
    private val whenRecorded=Instant.parse("2026-10-27T10:30:00Z")
    private fun summary()=ActivityRecord(
        "source-123","Actual logged workout","run","intervals",whenRecorded,
        10100.0,3600.0,3500.0,346.0,147.0,45.0
    )

    @Test fun missingStreamsAreUnknownNotSynthesized() {
        val source=ActivityDetailPresentationFactory.recorded("owner-a",summary(),null)
        assertEquals("source-123",source.originalId)
        assertEquals(10100.0,source.distanceMeters!!,0.0001)
        assertFalse(source.hasGpsTrace)
        assertFalse(source.hasPaceChart)
        assertFalse(source.hasHrChart)
        assertTrue(source.laps.isEmpty())
        assertNull(source.fetchedAtMillis)
    }

    @Test fun recordedSourceSamplesAndLapsRemainIdentical() {
        val sample=listOf(
            ActivitySample(0,0.0,350.0,145.0,12.0,14.60,121.03),
            ActivitySample(1,5.0,346.0,147.0,12.1,14.61,121.04)
        )
        val lap=listOf(ActivityLap(0,1000.0,346.0,346.0,146.0))
        val detail=ActivityDetail("source-123",sample,lap,42L)
        val view=ActivityDetailPresentationFactory.recorded("owner-a",summary(),detail)
        assertTrue(view.hasGpsTrace)
        assertTrue(view.hasPaceChart)
        assertTrue(view.hasHrChart)
        assertEquals(listOf(14.60 to 121.03,14.61 to 121.04),view.gpsCoordinates)
        assertEquals(sample,view.samples)
        assertEquals(lap,view.laps)
        assertEquals(42L,view.fetchedAtMillis)
    }

    @Test fun wrongSourceDetailsAreRejectedRatherThanReused() {
        val wrong=ActivityDetail("someone-elses-activity",emptyList(),emptyList(),1L)
        assertThrows(IllegalArgumentException::class.java) {
            ActivityDetailPresentationFactory.recorded("owner-a",summary(),wrong)
        }
    }

    @Test fun localWorkoutActualSetsRemainIndependentOfSourceSummary() {
        val start=LocalDate.parse("2026-10-26")
        val base=Engine1.generate(PlanRequest(start=start,weeks=1,runDays=0,
            strengthDays=2,calDays=0,conditioningDays=0), "owner-a","plan-a",whenRecorded)
        val target=base.sessions.first { it.discipline=="str" }
        val updated=base.copy(sessions=base.sessions.map { s ->
            if(s.id!=target.id)s else s.copy(status="in_progress",
                log=WorkoutLog(whenRecorded,20.0,null,6,
                    listOf(SetRecord(s.exercises.first().id,1,7,12.5,null)),
                    "Stopped before the remaining sets",start))
        })
        val view=ActivityDetailPresentationFactory.logged(updated,target.id)
        assertEquals("owner-a",view.accountId)
        assertEquals("in_progress",view.status)
        assertEquals(1,view.sets.size)
        assertEquals(7,view.sets.first().reps)
        assertNull(view.distanceKm)
        assertNull(view.linkedActivityId)
    }
}
