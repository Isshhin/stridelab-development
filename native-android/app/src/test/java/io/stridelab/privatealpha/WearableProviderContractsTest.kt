package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WearableProviderContractsTest {
    private val at=Instant.parse("2026-10-27T10:00:00Z")
    @Test fun explicitHrvMethodAndUnitAreRequired() {
        val rmssd=WearableWellnessInput("athlete","garmin","hrv-1","hrv_rmssd",68.0,"ms",at)
        val result=WearableWellnessNormalizer.normalize(rmssd)!!
        assertEquals(NormalizedWellnessKind.HRV_RMSSD,result.kind)
        assertEquals(68.0,result.value,0.0)
        assertNull(WearableWellnessNormalizer.normalize(rmssd.copy(kind="hrv")))
        assertThrows(IllegalArgumentException::class.java) {
            WearableWellnessNormalizer.normalize(rmssd.copy(unit="seconds"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WearableWellnessNormalizer.normalize(rmssd.copy(value=Double.NaN))
        }
        assertNull(WearableWellnessNormalizer.normalize(rmssd.copy(kind="stress_score")))
    }

    @Test fun fixturePagingUsesAccountScopesAndOpaqueCursorBoundaries() {
        val activities=(0..4).map { i ->
            WearableActivityInput("athlete-a","garmin","g-$i","running",
                at.plusSeconds(i.toLong()*3600),2000.0,null)
        }
        val provider=GarminFixtureProvider(mapOf("athlete-a" to activities),emptyMap())
        val first=provider.activities("athlete-a",null,2)
        assertEquals(listOf("g-0","g-1"),first.records.map { it.externalId })
        assertEquals("2",first.nextCursor)
        val next=provider.activities("athlete-a",first.nextCursor,2)
        assertEquals(listOf("g-2","g-3"),next.records.map { it.externalId })
        assertTrue(provider.activities("athlete-b",null,2).records.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            provider.activities("athlete-a","not-a-cursor",2)
        }
    }

    @Test fun differentAccountRecordCannotEnterFixturePage() {
        val poisoned=WearableActivityInput("other-athlete","garmin","id","running",at,100.0,null)
        val provider=GarminFixtureProvider(mapOf("athlete-a" to listOf(poisoned)),emptyMap())
        assertThrows(IllegalArgumentException::class.java) {
            provider.activities("athlete-a",null,10)
        }
    }

    @Test fun sleepAndStepsMustRetainCorrectUnitsAndMissingness() {
        val sleep=WearableWellnessNormalizer.normalize(
            WearableWellnessInput("athlete","garmin","s1","sleep_duration",7.5,"hours",at))
        assertEquals(7.5,sleep!!.value,0.0)
        assertThrows(IllegalArgumentException::class.java) {
            WearableWellnessNormalizer.normalize(
                WearableWellnessInput("athlete","garmin","s2","sleep_duration",25.0,"hours",at))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WearableWellnessNormalizer.normalize(
                WearableWellnessInput("athlete","garmin","steps","steps",100.25,"steps",at))
        }
    }
}
