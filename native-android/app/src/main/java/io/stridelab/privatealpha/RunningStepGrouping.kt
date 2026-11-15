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
