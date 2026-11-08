package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityChartRenderBudgetTest {
    @Test fun smallMarkerSetsRemainUnchanged() {
        val points = (0..30).toList()
        assertEquals(points, ActivityChartRenderBudget.markers(points))
    }

    @Test fun densePauseMarkersAreBoundedAndKeepBothEndpoints() {
        val points = (0 until 20_000).toList()
        val drawn = ActivityChartRenderBudget.markers(points)
        assertTrue(drawn.size <= 250)
        assertEquals(points.first(), drawn.first())
        assertEquals(points.last(), drawn.last())
        assertEquals(drawn.sorted(), drawn)
        assertEquals(20_000, points.size) // Raw samples are never mutated.
    }

    @Test fun markerBudgetAppliesToSmallLimits() {
        val drawn = ActivityChartRenderBudget.markers((0..20).toList(), 2)
        assertEquals(listOf(0,20),drawn)
    }
}
