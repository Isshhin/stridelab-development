package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine2RepositoryReadProjectionTest {
    private val now=Instant.parse("2026-10-28T10:00:00Z")

    @Test fun actualStrengthSetBecomesConfirmedEvidenceWithoutWritingOrInventingMeasurements() {
        val plan=Engine1.generate(PlanRequest(start=LocalDate.parse("2026-10-26"),
            weeks=1,runDays=0,strengthDays=2,calDays=0,conditioningDays=0),
            "owner","p",now.minusSeconds(3600))
        val target=plan.sessions.first { it.discipline=="str" }
        val actual=WorkoutLog(now.minusSeconds(120),25.0,null,6,
            listOf(SetRecord(target.exercises.first().id,1,8,20.0,null)),
            "A confirmed partial strength session",LocalDate.parse("2026-10-27"))
        val updated=plan.copy(status="approved",sessions=plan.sessions.map {
            if(it.id==target.id)it.copy(status="completed",log=actual,logRevision=1)else it
        })
        val before=updated.encode().toString()
        val result=Engine2RepositoryReadProjection.build(
            TrainingState(initializing=false,account="owner",plans=listOf(updated)),now)
        val one=result.context.workouts.single()
        assertEquals(Engine2Discipline.STRENGTH,one.discipline)
        assertEquals(Engine2RecordStatus.CONFIRMED,one.status)
        assertEquals(8.0,one.measurements.first { it.metric==Engine2Metric.REPS }.value,0.0)
        assertEquals(20.0,one.measurements.first { it.metric==Engine2Metric.LOAD_KG }.value,0.0)
        assertFalse(one.measurements.any { it.metric==Engine2Metric.DISTANCE_KM })
        assertEquals(before,updated.encode().toString())
    }

    @Test fun linkedRecordedRunIsNotCountedTwice() {
        val plan=Engine1.generate(PlanRequest(start=LocalDate.parse("2026-10-26"),
            weeks=1,runDays=2,strengthDays=0,calDays=0,conditioningDays=0),
            "owner","p",now.minusSeconds(3600))
        val target=plan.sessions.first()
        val recorded=ActivityRecord("source-run","Actual run","running","intervals",
            now.minusSeconds(3000),5000.0,1800.0,1750.0,350.0,145.0,10.0)
        val saved=plan.copy(status="approved",sessions=plan.sessions.map {
            if(it.id!=target.id)it else it.copy(status="completed",linkedActivityId="source-run",
                log=WorkoutLog(now.minusSeconds(3000),30.0,5.0,5))
        })
        val result=Engine2RepositoryReadProjection.build(TrainingState(
            initializing=false,account="owner",plans=listOf(saved),activities=listOf(recorded)),now)
        assertEquals(1,result.context.workouts.size)
        assertEquals(1,result.context.runningCoverage().confirmedRuns)
        assertEquals(5.0,result.context.runningCoverage().knownDistanceKm!!,0.0)
    }

    @Test fun mixedAccountPlanCannotEnterProjection() {
        val plan=Engine1.generate(PlanRequest(start=LocalDate.parse("2026-10-26"),
            weeks=1,runDays=2), "other","p",now)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2RepositoryReadProjection.build(TrainingState(
                initializing=false,account="owner",plans=listOf(plan)),now)
        }
    }

    @Test fun unsupportedWellnessMethodsAreNotConvertedToUndifferentiatedHrv() {
        val state=TrainingState(initializing=false,account="owner",
            wellness=listOf(
                WellnessRecord("rhr","resting_hr",53.0,"bpm",now.minusSeconds(3600),"manual"),
                WellnessRecord("hrv","hrv_sdnn",64.0,"ms",now.minusSeconds(3600),"intervals")
            ))
        val result=Engine2RepositoryReadProjection.build(state,now)
        assertEquals(1,result.context.wellness.size)
        assertEquals(Engine2Metric.RESTING_HR_BPM,result.context.wellness.single().metric.metric)
        assertTrue(result.rejected.any { it.recordId=="hrv" })
    }
}
