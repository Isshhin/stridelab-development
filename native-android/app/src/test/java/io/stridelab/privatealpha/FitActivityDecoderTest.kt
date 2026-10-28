package io.stridelab.privatealpha

import com.garmin.fit.*
import org.junit.Assert.*
import org.junit.Test

class FitActivityDecoderTest {
    private fun fixture(sessions: Int = 1, missing: Boolean = false): ByteArray {
        val encoder = BufferEncoder(Fit.ProtocolVersion.V2_0)
        encoder.write(FileIdMesg().apply { type = com.garmin.fit.File.ACTIVITY })
        encoder.write(RecordMesg().apply {
            timestamp = DateTime(1_000_000_000L)
            if(!missing) { positionLat=536870912; positionLong=1073741824; heartRate=150; distance=1234.5f; speed=3.5f; altitude=42f; cadence=80; power=200 }
        })
        repeat(sessions) { encoder.write(SessionMesg().apply {
            startTime=DateTime(1_000_000_000L); sport=Sport.RUNNING
            totalDistance=1234.5f; totalElapsedTime=400f; totalTimerTime=350f
            if(!missing) { avgHeartRate=150; maxHeartRate=170 }
        }) }
        encoder.write(LapMesg().apply { startTime=DateTime(1_000_000_000L); totalDistance=1234.5f; totalElapsedTime=400f })
        return encoder.close()
    }
    @Test fun officialEncodedFixtureDecodesProfileUnitsAndProvenance() {
        val a=FitActivityDecoder.decode(fixture(),"owner")
        assertEquals("running",a.sport); assertEquals(1234.5,a.distanceM!!,0.01)
        assertEquals(400.0,a.elapsedS!!,0.001);assertEquals(350.0,a.timerS!!,0.001)
        val p=a.points.single();assertEquals(45.0,p.latitude!!,0.00001);assertEquals(90.0,p.longitude!!,0.00001)
        assertEquals(150.0,p.heartRate!!,0.01);assertEquals(3.5,p.speedMps!!,0.001)
        assertEquals(42.0,p.altitudeM!!,0.1);assertEquals(80.0,p.cadenceRpm!!,0.01)
        assertEquals(200.0,p.powerW!!,0.01);assertEquals(1,a.laps.size)
        assertTrue(a.evidence().any { it.contains("not moving time") })
    }
    @Test fun absentMetricsRemainAbsent() {
        val a=FitActivityDecoder.decode(fixture(missing=true),"owner")
        assertNull(a.averageHr);assertNull(a.points.single().latitude);assertNull(a.points.single().heartRate)
    }
    @Test fun rejectsCorruptionTruncationChainingAndMultisport() {
        val bytes=fixture()
        for(bad in listOf(bytes.copyOf(bytes.size-1),bytes+bytes,bytes.copyOf().also { it[20]=(it[20].toInt() xor 1).toByte() },fixture(2))) {
            assertThrows(RuntimeException::class.java) { FitActivityDecoder.decode(bad,"owner") }
        }
    }
    @Test fun repeatedSourceIsDeduplicatedOnlyWithinAccount() {
        val a=FitActivityDecoder.decode(fixture(),"a").candidate()
        assertEquals(ImportedActivityDisposition.ALREADY_SEEN_SOURCE,ActivityImportMatcher.inspect(a,listOf(a)))
        val b=FitActivityDecoder.decode(fixture(),"b").candidate()
        assertEquals(ImportedActivityDisposition.NEW_SOURCE,ActivityImportMatcher.inspect(b,listOf(a)))
    }
}
