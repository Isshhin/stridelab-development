package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ActivityRecordTest {
    private fun raw() = JSONObject().put("id", "activity-1").put("user_id", "synthetic-athlete")
        .put("source", "fit").put("title", "Morning run").put("activity_type", "run")
        .put("started_at", "2026-10-25T23:30:00+00:00")
    @Test fun missingMetricsStayMissing() {
        val activity = ActivityRecord.parse(raw().toString(), "synthetic-athlete")
        assertNull(activity.averageHr); assertNull(activity.recordedPace)
        assertEquals("Not recorded", activity.distanceLabel)
    }
    @Test fun unitsAndStoppedTimeRemainDistinct() {
        val activity = ActivityRecord.parse(raw().put("distance_m", 5000).put("duration_s", 2100)
            .put("moving_time_s", 1800).put("avg_pace_sec_per_km", 360).toString(), "synthetic-athlete")
        assertEquals(5000.0, activity.distanceM!!, 0.0)
        assertEquals("35:00", activity.durationLabel)
        assertEquals("6:00 /km", activity.paceLabel)
    }
    @Test fun neverInfersPaceWhenSourceOmitsIt() {
        val activity = ActivityRecord.parse(raw().put("distance_m", 5000).put("duration_s", 2100).toString(), "synthetic-athlete")
        assertNull(activity.recordedPace)
    }
    @Test fun rejectsWrongOwnerAndDemo() {
        for(j in listOf(raw().put("user_id", "another"), raw().put("source", "demo"))) {
            assertThrows(IllegalArgumentException::class.java) { ActivityRecord.parse(j.toString(), "synthetic-athlete") }
        }
    }
    @Test fun zeroDistanceIsValidButZeroHeartRateIsNot() {
        assertEquals(0.0, ActivityRecord.parse(raw().put("distance_m", 0).toString(), "synthetic-athlete").distanceM!!, 0.0)
        assertThrows(IllegalArgumentException::class.java) { ActivityRecord.parse(raw().put("avg_hr", 0).toString(), "synthetic-athlete") }
    }
    @Test fun durationRoundsWithoutSixtySecondRemainders() {
        assertEquals("6:00", ActivityRecord.duration(359.8))
        assertEquals("1:00:00", ActivityRecord.duration(3599.8))
    }
    @Test fun maximumHeartRateStaysDistinctFromAverageAndMissing() {
        val activity = ActivityRecord.parse(raw().put("avg_hr",145.5).put("max_hr",177).toString(),"synthetic-athlete")
        assertEquals(145.5,activity.averageHr!!,0.0); assertEquals(177.0,activity.maximumHr!!,0.0)
        assertNull(ActivityRecord.parse(raw().toString(),"synthetic-athlete").maximumHr)
    }
    @Test fun trainingAndCalisthenicsDoNotAcquireRunningPace() {
        for(type in listOf("training","strength","calisthenics")) {
            assertFalse(ActivityRecord.parse(raw().put("activity_type",type).put("avg_pace_sec_per_km",300).toString(),"synthetic-athlete").supportsPace)
        }
        assertTrue(ActivityRecord.parse(raw().put("activity_type","running").toString(),"synthetic-athlete").supportsPace)
    }
    @Test fun utcInstantUsesDeviceZoneWithoutChangingSourceInstant() {
        val activity = ActivityRecord.parse(raw().put("started_at","2026-10-25T16:30:00Z").toString(),"synthetic-athlete")
        assertTrue(activity.dateLabel(java.time.ZoneId.of("Asia/Manila")).endsWith("00:30"))
        assertTrue(activity.dateLabel(java.time.ZoneId.of("UTC")).endsWith("16:30"))
        assertEquals("2026-10-25T16:30:00Z",activity.startedAt.toString())
    }

}
