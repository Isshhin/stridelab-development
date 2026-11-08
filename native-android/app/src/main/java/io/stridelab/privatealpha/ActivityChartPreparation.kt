package io.stridelab.privatealpha

/**
 * Pure chart-model preparation. Run this on Dispatchers.Default, never from a
 * Compose measure/draw or LazyColumn item composition. The full source samples
 * remain authoritative for statistics; only visual lines are downsampled.
 */
internal data class PreparedActivityChart(
    val validSamples: List<Pair<ActivitySample, Double>>,
    val pausedMetricSamples: List<ActivitySample>,
    val pausedDrawSamples: List<ActivitySample>,
    val lowest: Double,
    val highest: Double,
    val sampleAverage: Double,
    val displayScale: ActivityChartDisplayScale,
    val displaySegments: List<List<ActivitySample>>,
    val insight: ActivityInsightPresentation?,
    val renderedPoints: List<ActivitySample>,
    val displayedOutliers: List<ActivitySample>,
    val firstTime: Double,
    val lastTime: Double
)

internal object ActivityChartPreparation {
    val titles: List<String> = listOf("Pace", "Heart rate", "Elevation", "Cadence")

    fun metric(title: String, sample: ActivitySample): Double? = when (title) {
        "Pace" -> sample.pace
        "Heart rate" -> sample.hr
        "Elevation" -> sample.altitude
        "Cadence" -> sample.cadenceSpm
        else -> null
    }

    fun prepare(
        samples: List<ActivitySample>,
        title: String,
        pauses: List<ActivityPauseInterval> = emptyList()
    ): PreparedActivityChart? {
        if (title !in titles || samples.size < 2) return null
        val firstTime = samples.first().seconds
        val lastTime = samples.last().seconds
        if (lastTime <= firstTime) return null

        val sourceSegments = mutableListOf<List<ActivitySample>>()
        var current = mutableListOf<ActivitySample>()
        var previous: ActivitySample? = null
        fun flush() {
            if (current.isNotEmpty()) sourceSegments.add(current)
            current = mutableListOf()
            previous = null
        }
        fun crossesPause(from: Double, to: Double): Boolean =
            pauses.any { pause -> from < pause.endSeconds && to > pause.startSeconds }

        val valid = ArrayList<Pair<ActivitySample, Double>>(samples.size)
        val paused = ArrayList<ActivitySample>()
        for (sample in samples) {
            val value = metric(title, sample)
            if (value == null || !value.isFinite() ||
                (previous != null && (
                    sample.seconds - previous!!.seconds > 120.0 ||
                        crossesPause(previous!!.seconds, sample.seconds)
                ))
            ) flush()
            if (value != null && value.isFinite()) {
                current.add(sample)
                previous = sample
                valid.add(sample to value)
                if (pauses.any { interval ->
                    sample.seconds >= interval.startSeconds &&
                        sample.seconds <= interval.endSeconds
                }) paused.add(sample)
            }
        }
        flush()
        if (valid.size < 2) return null

        val lowest = valid.minOf { it.second }
        val highest = valid.maxOf { it.second }
        val average = valid.sumOf { it.second } / valid.size
        val scale = ActivityChartDisplayScale.from(title, valid.map { it.second })
            ?: ActivityChartDisplayScale(lowest, highest, lowest, highest)

        // Preserve the existing per-source-segment sampling contract and
        // original gap/pause boundaries; do not invent or reconnect points.
        val step = (samples.size / 450).coerceAtLeast(1)
        val reduced = sourceSegments.map { segment ->
            segment.filterIndexed { index, _ ->
                index % step == 0 || index == segment.lastIndex
            }
        }
        val visible = buildList {
            reduced.forEach { segment ->
                var section = mutableListOf<ActivitySample>()
                fun emit() {
                    if (section.isNotEmpty()) add(section.toList())
                    section = mutableListOf()
                }
                segment.forEach { sample ->
                    val value = metric(title, sample)
                    if (value == null || scale.isOutside(value)) emit()
                    else section.add(sample)
                }
                emit()
            }
        }
        val renderedPoints = reduced.flatten()
        val outliers = renderedPoints.filter { sample ->
            metric(title, sample)?.let(scale::isOutside) == true
        }
        return PreparedActivityChart(
            validSamples = valid,
            pausedMetricSamples = paused,
            // Keep every real pause value for semantics; limit circles on the canvas.
            pausedDrawSamples = paused.filterIndexed { index, _ ->
                index % (paused.size / 450).coerceAtLeast(1) == 0 || index == paused.lastIndex
            },
            lowest = lowest,
            highest = highest,
            sampleAverage = average,
            displayScale = scale,
            displaySegments = visible,
            insight = ActivityDetailSectionAnalysis.chart(title, valid),
            renderedPoints = renderedPoints,
            displayedOutliers = outliers,
            firstTime = firstTime,
            lastTime = lastTime
        )
    }
}

