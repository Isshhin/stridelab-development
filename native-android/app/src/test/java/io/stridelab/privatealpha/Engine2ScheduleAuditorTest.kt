package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class Engine2ScheduleAuditorTest {
    private val monday = LocalDate.parse("2026-11-02")
    private fun target(id: String, day: Int, type: Engine2Discipline, minutes: Int,
                       status: Engine2SessionStatus = Engine2SessionStatus.PLANNED) =
        Engine2SessionTarget(id, monday.plusDays(day.toLong()), type, minutes, status)
    private fun plan(vararg sessions: Engine2SessionTarget) =
        Engine2PlanView("athlete-a", "review-only", 3, Engine2PlanStatus.APPROVED,
            sessions.toList())
    private fun demand(id: String, stress: Engine2StressTag,
                       lower: Boolean = false, long: Boolean = false) =
        Engine2SessionDemand(id, stress, lower, long)

    @Test fun reviewFlagsHardAdjacentSessionsAndLowerBodyBeforeLongRun() {
        val p = plan(
            target("quality", 1, Engine2Discipline.RUNNING, 45),
            target("threshold", 2, Engine2Discipline.RUNNING, 40),
            target("lower", 4, Engine2Discipline.STRENGTH, 40),
            target("long", 5, Engine2Discipline.RUNNING, 105)
        )
        val original = p.copy()
        val a = Engine2ScheduleAuditor.inspect(p, listOf(
            demand("quality", Engine2StressTag.HARD),
            demand("threshold", Engine2StressTag.HARD),
            demand("lower", Engine2StressTag.MODERATE, lower = true),
            demand("long", Engine2StressTag.EASY, long = true)
        ), 120)
        assertTrue(a.any { it.code == Engine2ScheduleIssueCode.QUALITY_ADJACENCY &&
            it.sessionIds == setOf("quality", "threshold") })
        assertTrue(a.any { it.code == Engine2ScheduleIssueCode.LOWER_BEFORE_LONG &&
            it.sessionIds == setOf("lower", "long") })
        assertEquals(original, p)
    }

    @Test fun sameDayBudgetAndHardLegOverlapAreVisibleWithoutMutation() {
        val p = plan(
            target("hard", 0, Engine2Discipline.RUNNING, 65),
            target("leg", 0, Engine2Discipline.STRENGTH, 45)
        )
        val issues = Engine2ScheduleAuditor.inspect(p, listOf(
            demand("hard", Engine2StressTag.HARD),
            demand("leg", Engine2StressTag.MODERATE, lower = true)
        ), 90)
        assertTrue(issues.any { it.code == Engine2ScheduleIssueCode.DAILY_BUDGET })
        assertTrue(issues.any { it.code == Engine2ScheduleIssueCode.HARD_WITH_LOWER_BODY })
        assertEquals(2, issues.size)
    }

    @Test fun missingIntensityIsUncertaintyNotInferredRecoverySafety() {
        val p = plan(
            target("easy", 0, Engine2Discipline.RUNNING, 30),
            target("unknown", 2, Engine2Discipline.CALISTHENICS, 25)
        )
        val issues = Engine2ScheduleAuditor.inspect(p,
            listOf(demand("easy", Engine2StressTag.EASY)), 90)
        assertEquals(listOf(Engine2ScheduleIssueCode.UNKNOWN_LOAD), issues.map { it.code })
        assertEquals(setOf("unknown"), issues.single().sessionIds)
    }

    @Test fun completedAndSkippedHistoryIsExcludedFromFutureScheduleWarnings() {
        val p = plan(
            target("done", 0, Engine2Discipline.RUNNING, 90, Engine2SessionStatus.COMPLETED),
            target("skipped", 0, Engine2Discipline.STRENGTH, 60, Engine2SessionStatus.SKIPPED),
            target("planned", 1, Engine2Discipline.RUNNING, 35)
        )
        val issues = Engine2ScheduleAuditor.inspect(p, listOf(
            demand("done", Engine2StressTag.HARD),
            demand("skipped", Engine2StressTag.HARD, lower = true),
            demand("planned", Engine2StressTag.EASY)
        ), 70)
        assertTrue(issues.isEmpty())
    }

    @Test fun invalidDailyBudgetDuplicateAndUnknownDemandsAreRejected() {
        val p = plan(target("a", 0, Engine2Discipline.RUNNING, 30))
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ScheduleAuditor.inspect(p, emptyList(), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ScheduleAuditor.inspect(p,
                listOf(demand("a", Engine2StressTag.EASY),
                       demand("a", Engine2StressTag.HARD)), 90)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ScheduleAuditor.inspect(p, listOf(demand("missing", Engine2StressTag.HARD)), 90)
        }
    }
}
