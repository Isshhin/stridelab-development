package io.stridelab.privatealpha

/**
 * Read-only display grouping of the *saved* running prescription.
 *
 * No distances, intensities, rest periods or steps are generated here. In
 * particular, a final recovery must not be implied when the saved sequence
 * stops immediately after its final work repetition.
 */
internal sealed interface RunningStepBlock {
    data class Single(val segment: RunningSegment) : RunningStepBlock
    data class Repeat(
        val times: Int,
        val work: RunningSegment,
        val recovery: RunningSegment?,
        val recoveryAfterLast: Boolean
    ) : RunningStepBlock
}

internal object RunningStepGrouping {
    fun of(segments: List<RunningSegment>): List<RunningStepBlock> {
        val blocks = mutableListOf<RunningStepBlock>()
        var index = 0
        while (index < segments.size) {
            val current = segments[index]
            if (current.purpose == "WORK") {
                val recovery = segments.getOrNull(index + 1)
                    ?.takeIf { it.purpose == "RECOVERY" }
                if (recovery != null) {
                    var lastWork = index
                    var times = 1
                    // Exact value comparison includes prescribed purpose,
                    // distance, and effort. Different targets never merge.
                    while (lastWork + 2 < segments.size &&
                        segments[lastWork + 1] == recovery &&
                        segments[lastWork + 2] == current
                    ) {
                        times++
                        lastWork += 2
                    }
                    if (times >= 2) {
                        val trailingRecovery = segments.getOrNull(lastWork + 1) == recovery
                        blocks += RunningStepBlock.Repeat(
                            times = times,
                            work = current,
                            recovery = recovery,
                            recoveryAfterLast = trailingRecovery
                        )
                        index = lastWork + 1 + if (trailingRecovery) 1 else 0
                        continue
                    }
                }
                // Also support identical back-to-back quality efforts
                // where the saved prescription contains no rest segments.
                var next = index + 1
                while (next < segments.size && segments[next] == current) next++
                if (next - index >= 2) {
                    blocks += RunningStepBlock.Repeat(
                        times = next - index,
                        work = current,
                        recovery = null,
                        recoveryAfterLast = false
                    )
                    index = next
                    continue
                }
            }
            blocks += RunningStepBlock.Single(current)
            index++
        }
        return blocks
    }
}

/** Group only identical persisted v2 repetitions; never invent missing recovery. */
internal sealed interface MixedRunningStepBlock {
    data class Single(val segment:RunningStepV2):MixedRunningStepBlock
    data class Repeat(val times:Int,val work:RunningStepV2,
        val recovery:RunningStepV2?,val recoveryAfterLast:Boolean):MixedRunningStepBlock
}
internal object MixedRunningStepGrouping {
    fun of(steps:List<RunningStepV2>):List<MixedRunningStepBlock> {
        val blocks=mutableListOf<MixedRunningStepBlock>()
        var index=0
        while(index<steps.size) {
            val current=steps[index]
            if(current.purpose=="WORK") {
                val recovery=steps.getOrNull(index+1)?.takeIf { it.purpose=="RECOVERY" }
                if(recovery!=null) {
                    var lastWork=index
                    var count=1
                    while(lastWork+2<steps.size &&
                        steps[lastWork+1]==recovery && steps[lastWork+2]==current) {
                        count++
                        lastWork+=2
                    }
                    if(count>=2) {
                        val after=steps.getOrNull(lastWork+1)==recovery
                        blocks+=MixedRunningStepBlock.Repeat(count,current,recovery,after)
                        index=lastWork+1+if(after)1 else 0
                        continue
                    }
                }
                var next=index+1
                while(next<steps.size && steps[next]==current)next++
                if(next-index>=2) {
                    blocks+=MixedRunningStepBlock.Repeat(next-index,current,null,false)
                    index=next
                    continue
                }
            }
            blocks+=MixedRunningStepBlock.Single(current)
            index++
        }
        return blocks
    }
}
