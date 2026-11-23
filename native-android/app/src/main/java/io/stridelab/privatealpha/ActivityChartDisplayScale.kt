package io.stridelab.privatealpha

internal data class ActivityChartDisplayScale(
    val fullLow: Double,
    val fullHigh: Double,
    val displayLow: Double,
    val displayHigh: Double
) {
    val trimmed: Boolean
        get() = displayLow > fullLow || displayHigh < fullHigh

    fun isOutside(value: Double): Boolean =
        value < displayLow || value > displayHigh

    companion object {
        fun from(title: String, values: List<Double>): ActivityChartDisplayScale? {
            val valid = values.filter { it.isFinite() }.sorted()
            if (valid.size < 2) return null
            val fullLow = valid.first()
            val fullHigh = valid.last()
            if (title !in setOf("Pace", "Cadence") || valid.size < 10) {
                return ActivityChartDisplayScale(fullLow, fullHigh, fullLow, fullHigh)
            }

            // Pauses and sensor dropouts can create extreme pace/cadence points.
            // Keep every raw sample available to the cursor and summary, but use
            // a conservative median-relative display window so those extremes
            // do not flatten the rest of the chart.
            val typical = valid.filter { it > 0.0 }
            if (typical.size < 10) {
                return ActivityChartDisplayScale(fullLow, fullHigh, fullLow, fullHigh)
            }
            val median = typical[typical.size / 2]
            val candidateLow = when (title) {
                "Pace" -> median * .65
                "Cadence" -> median * .50
                else -> fullLow
            }
            val candidateHigh = when (title) {
                "Pace" -> median * 1.75
                "Cadence" -> median * 1.25
                else -> fullHigh
            }
            val displayLow = maxOf(fullLow, candidateLow)
            val displayHigh = minOf(fullHigh, candidateHigh)
            return if (displayHigh - displayLow >= 1.0) {
                ActivityChartDisplayScale(fullLow, fullHigh, displayLow, displayHigh)
            } else {
                ActivityChartDisplayScale(fullLow, fullHigh, fullLow, fullHigh)
            }
        }
    }
}
