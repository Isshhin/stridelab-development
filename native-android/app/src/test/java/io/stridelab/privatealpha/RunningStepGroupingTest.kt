package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class RunningStepGroupingTest {
    private val warm=RunningSegment("WARM_UP",1300,"Easy conversational running")
    private val work=RunningSegment("WORK",400,"Controlled effort")
    private val recovery=RunningSegment("RECOVERY",200,"Easy jog or walk")
    private val cool=RunningSegment("COOL_DOWN",900,"Easy running")

    @Test fun fiveRepeatsWithRecoveryBetweenRepsKeepActualSevenOrMoreSteps() {
        val segments=listOf(warm)+buildList {
            repeat(5) { i ->
                add(work)
                if(i<4)add(recovery)
            }
        }+cool
        val blocks=RunningStepGrouping.of(segments)
        assertEquals(3,blocks.size)
        assertEquals(RunningStepBlock.Single(warm),blocks.first())
        assertEquals(
            RunningStepBlock.Repeat(5,work,recovery,false),blocks[1]
        )
        assertEquals(RunningStepBlock.Single(cool),blocks.last())
        val reconstructed=blocks.flatMap {
            when(it) {
                is RunningStepBlock.Single -> listOf(it.segment)
                is RunningStepBlock.Repeat -> buildList {
                    repeat(it.times) { i ->
                        add(it.work)
                        if(it.recovery!=null && (i<it.times-1 || it.recoveryAfterLast))
                            add(it.recovery)
                    }
                }
            }
        }
        assertEquals("Grouping cannot alter or invent steps",segments,reconstructed)
    }

    @Test fun finalRecoveryOnlyAppearsWhenActuallySaved() {
        val segments=listOf(warm,work,recovery,work,recovery,cool)
        val blocks=RunningStepGrouping.of(segments)
        assertEquals(RunningStepBlock.Repeat(2,work,recovery,true),blocks[1])
        assertEquals(3,blocks.size)
    }

    @Test fun unequalIntervalsAndRecoveriesStaySeparate() {
        val variedWork=work.copy(meters=600)
        val variedRecovery=recovery.copy(effort="Walk")
        val segments=listOf(warm,work,recovery,variedWork,variedRecovery,work,cool)
        assertEquals(segments.map(RunningStepBlock::Single),RunningStepGrouping.of(segments))
    }

    @Test fun easyLongAndSingleTempoRemainIndividualCards() {
        val easy=RunningSegment("EASY",13600,"Conversational")
        assertEquals(listOf(RunningStepBlock.Single(easy)),RunningStepGrouping.of(listOf(easy)))
        assertEquals(listOf(RunningStepBlock.Single(warm),RunningStepBlock.Single(work),
            RunningStepBlock.Single(cool)),
            RunningStepGrouping.of(listOf(warm,work,cool)))
    }

    @Test fun repeatedQualityWithoutSavedRestHasNoInventedRecovery() {
        assertEquals(
            listOf(RunningStepBlock.Repeat(3,work,null,false)),
            RunningStepGrouping.of(listOf(work,work,work))
        )
    }

    @Test fun segmentDistanceLabelsDoNotRoundAwayExactTargets() {
        assertEquals("400 m",runningStepDistanceLabel(400))
        assertEquals("1.3 km",runningStepDistanceLabel(1300))
        assertEquals("1.35 km",runningStepDistanceLabel(1350))
        assertEquals("1355 m",runningStepDistanceLabel(1355))
    }
}
