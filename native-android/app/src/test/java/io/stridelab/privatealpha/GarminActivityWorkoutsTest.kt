package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GarminActivityWorkoutsTest {
    private val now = Instant.parse("2026-11-19T00:00:00Z")

    @Test fun associatedWorkoutStepsRetainStructureButNotExecutionClaims() {
        val reference = GarminActivityWorkouts.parse(
            """[{"index":0,"workoutName":"5 x 3 min",
               "steps":[{"stepOrder":1,"stepName":"Threshold","intensity":"active"},
                        {"stepOrder":2,"stepName":"Recovery","intensity":"recovery"}]}]"""
        )
        assertEquals("5 x 3 min", reference.single().name)
        assertEquals(2, reference.single().steps.size)
        assertEquals(1, reference.single().steps.first().order)
        assertEquals("recovery", reference.single().steps.last().intensity)
    }

    @Test fun emptyAndOversizedWorkoutReferencesDoNotSynthesizeSteps() {
        assertTrue(GarminActivityWorkouts.parse("[]").isEmpty())
        assertFails<GarminProtocolException> {
            GarminActivityWorkouts.parse("{}")
        }
        assertFails<GarminProtocolException> {
            GarminActivityWorkouts.parse(" ")
        }
    }

    @Test fun endpointReadsWorkoutReferenceUnderSamePersonalBearer() {
        var request: GarminHttpRequest? = null
        val client = GarminPersonalClient(object : GarminHttpTransport {
            override fun execute(value: GarminHttpRequest): GarminHttpResponse {
                request = value
                return GarminHttpResponse(200,
                    """[{"workoutName":"Intervals","steps":[]}]""")
            }
        }) { now }
        val result = client.activityWorkouts(
            GarminBearerToken("secret", now.plusSeconds(3600)), 123L)
        assertEquals("Intervals", result.single().name)
        assertEquals("GET", request!!.method)
        assertEquals("https://personal.connectapi.garmin.com/activity-service/activity/123/workouts",
            request!!.url)
        assertFalse(request.toString().contains("secret"))
    }

    private inline fun <reified T : Throwable> assertFails(action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            if (error is T) return
            throw AssertionError("Unexpected exception", error)
        }
        fail("Expected " + T::class.java.simpleName)
    }
}
