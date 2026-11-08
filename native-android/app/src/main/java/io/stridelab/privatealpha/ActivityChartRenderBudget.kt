package io.stridelab.privatealpha

/**
 * Bounds visual marker draw calls without discarding any recorded samples
 * from chart statistics, analysis, scrubbing, or local persistence.
 */
internal object ActivityChartRenderBudget {
    fun <T> markers(points: List<T>, maxMarkers: Int = 250): List<T> {
        require(maxMarkers >= 2)
        if (points.size <= maxMarkers) return points
        val stride = (points.lastIndex + maxMarkers - 2) / (maxMarkers - 1)
        return points.filterIndexed { index, _ ->
            index % stride == 0 || index == points.lastIndex
        }
    }
}
