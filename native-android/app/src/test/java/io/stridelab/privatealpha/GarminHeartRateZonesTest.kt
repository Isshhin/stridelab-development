package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GarminHeartRateZonesTest {
    private val now = Instant.parse("2026-11-19T00:00:00Z")
    private val zones = """[
        {"sport":"DEFAULT","trainingMethod":"HR_MAX","zone1Floor":100,
         "zone2Floor":120,"zone3Floor":140,"zone4Floor":160,"zone5Floor":180},
        {"sport":"RUNNING","trainingMethod":"LTHR","zone1Floor":110,
         "zone2Floor":132,"zone3Floor":145,"zone4Floor":168,"zone5Floor":182},
        {"sport":"CYCLING","zone1Floor":95,"zone2Floor":110,
         "zone3Floor":125,"zone4Floor":140,"zone5Floor":160}
    ]"""

    @Test fun actualRunningFloorsTakePrecedenceOverGeneralAndCycling() {
        val profiles = GarminHeartRateZones.parse(zones)
        val selected = GarminHeartRateZones.forRunning(profiles)!!
        assertEquals("RUNNING", selected.sport)
        assertEquals(listOf(110, 132, 145, 168, 182), selected.floorsBpm)
        assertEquals(listOf(132, 145, 168, 182), selected.upperTransitionsBpm)
        assertEquals("LTHR", selected.trainingMethod)
    }

    @Test fun generalFallbackOnlyWhenRunningAbsent() {
        val general = GarminHeartRateZones.parse(
            """[{"sport":"DEFAULT","zone1Floor":90,"zone2Floor":105,
                "zone3Floor":125,"zone4Floor":150,"zone5Floor":175}]"""
        )
        assertEquals("DEFAULT", GarminHeartRateZones.forRunning(general)?.sport)
        val cycling = GarminHeartRateZones.parse(
            """[{"sport":"CYCLING","zone1Floor":90,"zone2Floor":105,
                "zone3Floor":125,"zone4Floor":150,"zone5Floor":175}]"""
        )
        assertNull(GarminHeartRateZones.forRunning(cycling))
    }

    @Test fun malformedFloorsNeverBecomeEstimatedHeartRateZones() {
        val invalid = GarminHeartRateZones.parse(
            """[{"sport":"RUNNING","zone1Floor":90,"zone2Floor":130,
                "zone3Floor":125,"zone4Floor":150,"zone5Floor":175},
               {"sport":"RUNNING","zone1Floor":90,"zone2Floor":105,
                "zone3Floor":125,"zone4Floor":150}]"""
        )
        assertNull(GarminHeartRateZones.forRunning(invalid))
        assertFails<GarminProtocolException> { GarminHeartRateZones.parse("{}") }
        assertFails<GarminProtocolException> { GarminHeartRateZones.parse("broken") }
    }

    @Test fun conflictingRunningProfilesDoNotSilentlyChooseOne() {
        val conflicts = GarminHeartRateZones.parse(
            """[{"sport":"RUNNING","zone1Floor":90,"zone2Floor":105,"zone3Floor":125,
                "zone4Floor":150,"zone5Floor":175},
               {"sport":"RUNNING","zone1Floor":90,"zone2Floor":110,"zone3Floor":130,
                "zone4Floor":150,"zone5Floor":175},
               {"sport":"DEFAULT","zone1Floor":85,"zone2Floor":100,"zone3Floor":115,
                "zone4Floor":130,"zone5Floor":160}]"""
        )
        assertNull(GarminHeartRateZones.forRunning(conflicts))
    }

    @Test fun readUsesReadOnlyPersonalApiEndpointAndPreservesBearerPrivacy() {
        var request: GarminHttpRequest? = null
        val client = GarminPersonalClient(object : GarminHttpTransport {
            override fun execute(value: GarminHttpRequest): GarminHttpResponse {
                request = value
                return GarminHttpResponse(200, zones)
            }
        }) { now }
        val actual = client.heartRateZones(
            GarminBearerToken("private-bearer", now.plusSeconds(3600)))
        assertEquals(3, actual.size)
        assertEquals("GET", request!!.method)
        assertEquals("https://personal.connectapi.garmin.com/biometric-service/heartRateZones/v2",
            request!!.url)
        assertEquals("Bearer private-bearer", request!!.headers["Authorization"])
        assertFalse(request.toString().contains("private-bearer"))
        assertFails<GarminAuthenticationException> {
            client.heartRateZones(GarminBearerToken("expired", now.minusSeconds(1)))
        }
    }

    private inline fun <reified T : Throwable> assertFails(action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            if (error is T) return
            throw AssertionError("Expected ${T::class.java.simpleName}", error)
        }
        fail("Expected ${T::class.java.simpleName}")
    }
}
