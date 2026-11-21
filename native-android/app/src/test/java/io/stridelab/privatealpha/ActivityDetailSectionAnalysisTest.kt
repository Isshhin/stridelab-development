package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Presentation observations must never invent data or physiological conclusions. */
class ActivityDetailSectionAnalysisTest {
    @Test fun splitsUsesRecordedKilometerLapsAndIdentifiesFasterLaterHalf() {
        val laps = listOf(
            ActivityLap(0, 1000.0, 390.0, 390.0, 145.0),
            ActivityLap(1, 1000.0, 380.0, 380.0, 150.0),
            ActivityLap(2, 1000.0, 360.0, 360.0, 157.0),
            ActivityLap(3, 1000.0, 355.0, 355.0, 159.0)
        )
        val analysis = ActivityDetailSectionAnalysis.splits(laps, supportsPace = true)
        assertNotNull(analysis)
        assertTrue(analysis!!.summary.contains("Split 4"))
        assertTrue(analysis.evidence.any { it.contains("Later splits were faster") })
        assertFalse(analysis.evidence.any { it.contains("readiness", ignoreCase = true) })
    }

    @Test fun partialOrUnequalLapsDoNotBecomeInventedFullKilometerTrends() {
        val analysis = ActivityDetailSectionAnalysis.splits(
            listOf(
                ActivityLap(0, 700.0, 300.0, 430.0, null),
                ActivityLap(1, 1600.0, 500.0, 312.5, null)
            ), supportsPace = true
        )
        assertNotNull(analysis)
        assertTrue(analysis!!.evidence.any { it.contains("distances may differ") })
        assertNull(ActivityDetailSectionAnalysis.splits(emptyList(), supportsPace = true))
        assertNull(ActivityDetailSectionAnalysis.splits(
            listOf(ActivityLap(0, 1000.0, 390.0, 390.0, null)),
            supportsPace = false
        ))
    }

    @Test fun heartRateChartDescribesRecordedPointsNotCardiacDrift() {
        val samples = listOf(
            ActivitySample(0, 0.0, null, 131.0, null, null, null),
            ActivitySample(1, 30.0, null, 142.0, null, null, null),
            ActivitySample(2, 60.0, null, 157.0, null, null, null),
            ActivitySample(3, 90.0, null, 162.0, null, null, null)
        )
        val result = ActivityDetailSectionAnalysis.chart(
            "Heart rate", samples.map { it to it.hr!! }
        )
        assertNotNull(result)
        assertEquals("Heart rate was higher late in the run", result!!.headline)
        assertTrue(result.summary.contains("131 bpm") && result.summary.contains("162 bpm"))
        assertTrue(result.evidence.any { it.contains("establish cardiac drift") })
        assertEquals(false, result.hasComparison)
    }

    @Test fun noChartAnalysisIsGeneratedWithoutMeasuredValues() {
        assertNull(ActivityDetailSectionAnalysis.chart("Pace", emptyList()))
        val sample = ActivitySample(0, 0.0, null, null, null, null, null)
        assertNull(ActivityDetailSectionAnalysis.chart("Cadence", listOf(sample to 170.0)))
        assertNull(ActivityDetailSectionAnalysis.chart(
            "Unknown", listOf(sample to 170.0, sample.copy(index = 1) to 171.0)
        ))
    }

    @Test fun activityFeedbackTargetsOnlyOneLinkedCompletedEngine15Run() {
        val now = java.time.Instant.parse("2026-11-17T00:00:00Z")
        val session = PlannedSession(
            "linked-run", java.time.LocalDate.parse("2026-11-16"), "run", "Easy run", 30, 5.0, "easy",
            status = "completed", linkedActivityId = "source-activity"
        )
        val plan = PlanSnapshot(
            "plan", "owner", ENGINE15_VERSION, ENGINE15_POLICY, now,
            PlanRequest(policyChoice = "engine15"), listOf(session), emptyList(), status = "approved"
        )
        assertEquals(session, findLinkedCompletedActivityRun(listOf(plan), setOf("source-activity"))?.second)
        assertNull(findLinkedCompletedActivityRun(listOf(plan), setOf("other-source")))
        assertNull(findLinkedCompletedActivityRun(
            listOf(plan.copy(sessions = listOf(session.copy(status = "planned")))), setOf("source-activity")
        ))
        assertNull(findLinkedCompletedActivityRun(
            listOf(plan.copy(engineVersion = ENGINE1_VERSION)), setOf("source-activity")
        ))
        assertNull(findLinkedCompletedActivityRun(
            listOf(plan, plan.copy(id = "duplicate")), setOf("source-activity")
        ))
    }
}
