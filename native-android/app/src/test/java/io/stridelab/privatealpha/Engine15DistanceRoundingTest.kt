package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class Engine15DistanceRoundingTest {
    @Test fun examplesRoundUpWhenWeeklyAndSessionBudgetsAllow() {
        assertEquals(13.0,Engine15DistanceRounding.roundUpHalfKm(12.7),0.001)
        assertEquals(14.5,Engine15DistanceRounding.roundUpHalfKm(14.3),0.001)
        assertEquals(18.0,Engine15DistanceRounding.roundUpHalfKm(18.0),0.001)
        val result=Engine15DistanceRounding.allocate(
            intArrayOf(127,143),intArrayOf(140,150),
            intArrayOf(140,150),275)
        assertArrayEquals(intArrayOf(130,145),result)
        assertEquals(275,result.sum())
    }

    @Test fun budgetAlwaysWinsOverRequestedUpwardRounding() {
        val result=Engine15DistanceRounding.allocate(
            intArrayOf(127,143),intArrayOf(140,150),
            intArrayOf(140,150),270)
        assertArrayEquals(intArrayOf(125,145),result)
        assertTrue(result.sum()<=270)
    }

    @Test fun preservesQualitySegmentsAndRespectsIndividualLimits() {
        val result=Engine15DistanceRounding.allocate(
            intArrayOf(127,143),intArrayOf(140,150),
            intArrayOf(130,143),275,fixedIndices=setOf(1))
        assertArrayEquals(intArrayOf(130,143),result)
    }

    @Test fun retainsOriginalWhenHalfKmGridWouldBreachLongRunShare() {
        val source=intArrayOf(100,73,77)
        val result=Engine15DistanceRounding.allocate(
            source,intArrayOf(100,90,90),
            intArrayOf(100,90,90),250,longIndex=0)
        assertArrayEquals(source,result)
        assertTrue(result[0]*5<=result.sum()*2)
    }

    @Test fun tinyConstrainedRunsAreNotRoundedUpPastTheirCeiling() {
        val result=Engine15DistanceRounding.allocate(
            intArrayOf(3,6),intArrayOf(3,6),
            intArrayOf(3,6),9)
        assertArrayEquals(intArrayOf(3,5),result)
        assertTrue(result.all { it>0 })
    }
}
