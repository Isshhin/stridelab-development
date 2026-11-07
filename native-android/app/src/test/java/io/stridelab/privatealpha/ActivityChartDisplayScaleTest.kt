package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityChartDisplayScaleTest {
    @Test fun cadenceZeroDoesNotFlattenTypicalCadenceScale() {
        val values = List(30) { 174.0 + (it % 5) } + listOf(0.0, 2.0)
        val scale = ActivityChartDisplayScale.from("Cadence", values)!!
        assertTrue(scale.trimmed)
        assertEquals(0.0, scale.fullLow, 0.0)
        assertTrue(scale.displayLow > 80.0)
        assertTrue(scale.isOutside(0.0))
        assertFalse(scale.isOutside(175.0))
    }

    @Test fun extremeStoppedPaceDoesNotControlYAxis() {
        val values = List(30) { 380.0 + (it % 8) } + listOf(3058.0)
        val scale = ActivityChartDisplayScale.from("Pace", values)!!
        assertTrue(scale.trimmed)
        assertEquals(3058.0, scale.fullHigh, 0.0)
        assertTrue(scale.displayHigh < 800.0)
        assertTrue(scale.isOutside(3058.0))
        assertFalse(scale.isOutside(390.0))
    }

    @Test fun heartRateKeepsFullRecordedRange() {
        val values = (130..165).map(Int::toDouble)
        val scale = ActivityChartDisplayScale.from("Heart rate", values)!!
        assertFalse(scale.trimmed)
        assertEquals(130.0, scale.displayLow, 0.0)
        assertEquals(165.0, scale.displayHigh, 0.0)
    }
}
