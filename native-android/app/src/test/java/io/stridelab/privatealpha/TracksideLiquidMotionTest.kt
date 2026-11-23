package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Test

class TracksideLiquidMotionTest {
    @Test fun selectionPressureIsBoundedAndDoesNotLoopAtRest() {
        assertEquals(0f, TracksideLiquidMotion.pressure(2f, 2f, 1f), 0f)
        assertEquals(.5f, TracksideLiquidMotion.pressure(2f, 2.5f, 1f), .00001f)
        assertEquals(1f, TracksideLiquidMotion.pressure(0f, 7f, 1f), 0f)
        assertEquals(0f, TracksideLiquidMotion.pressure(0f, 5f, 0f), 0f)
    }

    @Test fun liquidStretchAndSquashReturnToNeutralAtRest() {
        assertEquals(1f, TracksideLiquidMotion.stretch(0f, .22f), 0f)
        assertEquals(1f, TracksideLiquidMotion.squash(0f, .1f), 0f)
        assertEquals(1.22f, TracksideLiquidMotion.stretch(1f, .22f), .00001f)
        assertEquals(.9f, TracksideLiquidMotion.squash(1f, .1f), .00001f)
    }

    @Test fun curvedNotchSettlesUnderEveryWeekdayAndWakesBetweenDays() {
        for (day in 0..6) {
            val atCenter = (day + .5f) / 7f
            assertEquals(0f, TracksideLiquidMotion.notchWake(atCenter), .00001f)
        }
        // Between Monday and Tuesday the notch briefly broadens.
        assertEquals(1f, TracksideLiquidMotion.notchWake(1f / 7f), .00001f)
    }
}
