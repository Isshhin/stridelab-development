package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GarminHistoryImportTest {
    private val started=Instant.parse("2026-11-04T02:06:00Z")

    @Test fun durableSummaryPreservesGarminProvenanceAndActualSourceMetrics() {
        val input=WearableActivityInput(
            accountId="athlete-a",provider="garmin",externalId="source-activity/123",
            activityType="running",startedAt=started,durationSeconds=3807.0,
            distanceMeters=9560.0,title="Morning Run",movingDurationSeconds=3720.0,
            averageHeartRate=138.0
        )
        val raw=GarminHistoryImport.payload(input)
        assertEquals("athlete-a",raw.getString("user_id"))
        assertEquals("garmin",raw.getString("source"))
        assertEquals("source-activity/123",raw.getString("external_id"))
        assertTrue(raw.getString("id").startsWith("garmin-"))
        assertTrue(raw.getString("id").length<=128)
        val parsed=ActivityRecord.parse(raw.toString(),"athlete-a")
        assertEquals("Morning Run",parsed.title)
        assertEquals(started,parsed.startedAt)
        assertEquals(9560.0,parsed.distanceM!!,0.0)
        assertEquals(3807.0,parsed.durationS!!,0.0)
        assertEquals(3720.0,parsed.movingTimeS!!,0.0)
        assertEquals(138.0,parsed.averageHr!!,0.0)
        assertNull(parsed.recordedPace) // Do not invent source measurements.
        assertEquals(raw.getString("id"),GarminHistoryImport.recordId(input.externalId))
        assertNotEquals(raw.getString("id"),GarminHistoryImport.recordId("source-activity/124"))
    }

    @Test fun absentMetricsRemainAbsentAndWrongProviderOrOwnerIsRejected() {
        val input=WearableActivityInput("athlete-a","garmin","source-2","cycling",
            started,null,null)
        val raw=GarminHistoryImport.payload(input)
        val parsed=ActivityRecord.parse(raw.toString(),"athlete-a")
        assertNull(parsed.distanceM)
        assertNull(parsed.averageHr)
        try { ActivityRecord.parse(raw.toString(),"athlete-b"); fail("Expected owner rejection") }
        catch(_:IllegalArgumentException) {}
        try { GarminHistoryImport.payload(input.copy(provider="intervals")); fail("Expected provider rejection") }
        catch(_:IllegalArgumentException) {}
    }
}
