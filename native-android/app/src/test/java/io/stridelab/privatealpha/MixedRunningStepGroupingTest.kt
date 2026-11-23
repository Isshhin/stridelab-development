package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Test

/** Exact mixed-step grouping with no invented recovery or target conversions. */
class MixedRunningStepGroupingTest {
    private val warm=RunningStepV2("WARM_UP","Easy",seconds=600)
    private val work=RunningStepV2("WORK","Controlled",meters=800)
    private val recovery=RunningStepV2("RECOVERY","Easy",seconds=90)
    private val cool=RunningStepV2("COOL_DOWN","Easy",seconds=480)

    @Test fun repeatBlockRetainsExactTimeAndDistanceSequence() {
        val original=listOf(warm,work,recovery,work,recovery,work,cool)
        val groups=MixedRunningStepGrouping.of(original)
        assertEquals(listOf(
            MixedRunningStepBlock.Single(warm),
            MixedRunningStepBlock.Repeat(3,work,recovery,false),
            MixedRunningStepBlock.Single(cool)),groups)
        val decoded=groups.flatMap { block ->
            when(block) {
                is MixedRunningStepBlock.Single -> listOf(block.segment)
                is MixedRunningStepBlock.Repeat -> buildList {
                    repeat(block.times) { i ->
                        add(block.work)
                        if(block.recovery!=null && (i<block.times-1 || block.recoveryAfterLast))
                            add(block.recovery)
                    }
                }
            }
        }
        assertEquals(original,decoded)
    }

    @Test fun recoveryAfterLastIsOnlyDisplayedWhenSaved() {
        val blocks=MixedRunningStepGrouping.of(listOf(work,recovery,work,recovery))
        assertEquals(listOf(MixedRunningStepBlock.Repeat(2,work,recovery,true)),blocks)
    }

    @Test fun differingRecoveryOrIntervalExtentsNeverMerge() {
        val varied=RunningStepV2("RECOVERY","Easy",seconds=120)
        val rows=listOf(work,recovery,work,varied,work)
        assertEquals(listOf(
            MixedRunningStepBlock.Repeat(2,work,recovery,false),
            MixedRunningStepBlock.Single(varied),
            MixedRunningStepBlock.Single(work)
        ),MixedRunningStepGrouping.of(rows))
    }
}
