package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityChartMovingTimeAxisTest {
    private fun sample(seconds: Double): ActivitySample =
        ActivitySample(0, seconds, 350.0, 145.0, 10.0, null, null, 175.0)

    @Test fun removesOneTimerPauseWithoutRewritingSourceTimes() {
        val samples = listOf(sample(0.0), sample(25.0), sample(30.0), sample(90.0), sample(120.0))
        val axis = ActivityChartMovingTimeAxis.from(
            samples, listOf(ActivityPauseInterval(30.0, 90.0))
        )
        assertEquals(60.0, axis.durationSeconds, 0.001)
        assertEquals(30.0, axis.position(30.0), 0.001)
        assertEquals(30.0, axis.position(60.0), 0.001)
        assertEquals(30.0, axis.position(90.0), 0.001)
        assertEquals(60.0, axis.position(120.0), 0.001)
        assertTrue(axis.isPaused(50.0))
        assertFalse(axis.isPaused(90.0))
        assertEquals(120.0, samples.last().seconds, 0.001)
    }

    @Test fun overlapsAreMergedAndOutOfRangePausesAreClamped() {
        val axis = ActivityChartMovingTimeAxis.from(
            listOf(sample(10.0), sample(110.0)),
            listOf(
                ActivityPauseInterval(40.0, 70.0),
                ActivityPauseInterval(60.0, 80.0),
                ActivityPauseInterval(-30.0, 20.0),
                ActivityPauseInterval(100.0, 120.0)
            )
        )
        // 100 elapsed - 10 early - 40 merged - 10 late = 40 moving seconds.
        assertEquals(40.0, axis.durationSeconds, 0.001)
        assertEquals(0.0, axis.position(15.0), 0.001)
        assertEquals(20.0, axis.position(40.0), 0.001)
        assertEquals(20.0, axis.position(80.0), 0.001)
        assertEquals(40.0, axis.position(110.0), 0.001)
    }

    @Test fun noPausesKeepElapsedSpacingWithZeroBasedAxis() {
        val axis = ActivityChartMovingTimeAxis.from(
            listOf(sample(15.0), sample(30.0), sample(75.0)), emptyList()
        )
        assertEquals(60.0, axis.durationSeconds, 0.001)
        assertEquals(15.0, axis.position(30.0), 0.001)
        assertEquals(60.0, axis.position(75.0), 0.001)
        assertFalse(axis.isPaused(30.0))
    }

    @Test fun invalidPausesDoNotRemoveMovingTime() {
        val axis = ActivityChartMovingTimeAxis.from(
            listOf(sample(0.0), sample(100.0)),
            listOf(ActivityPauseInterval(90.0, 20.0), ActivityPauseInterval(Double.NaN, 40.0))
        )
        assertEquals(100.0, axis.durationSeconds, 0.001)
    }
}
