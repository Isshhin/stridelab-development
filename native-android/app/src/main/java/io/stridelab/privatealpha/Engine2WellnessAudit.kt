package io.stridelab.privatealpha

import java.time.Duration
import java.time.Instant

/**
 * A wellness-availability summary, not a readiness score or diagnosis.
 * No numeric HRV, RHR or sleep cutoffs are treated as universal safe/unsafe thresholds.
 */
internal data class Engine2WellnessMetricCoverage(
    val count: Int,
    val latestObservedAt: Instant?,
    val latestReceivedAt: Instant?,
    val presentInWindow: Boolean
)
internal data class Engine2WellnessCoverage(
    val windowDays: Long,
    val metricCoverage: Map<Engine2Metric, Engine2WellnessMetricCoverage>,
    val missingMetrics: Set<Engine2Metric>,
    val hasEnoughInformationForReadinessClassification: Boolean = false
)

internal object Engine2WellnessAudit {
    private val tracked = listOf(
        Engine2Metric.RESTING_HR_BPM, Engine2Metric.HRV_MS, Engine2Metric.SLEEP_SECONDS
    )

    fun inspect(context: Engine2AthleteContext, days: Long = 14): Engine2WellnessCoverage {
        require(days in 1..365)
        val from = context.asOf.minus(Duration.ofDays(days))
        val perMetric = tracked.associateWith { metric ->
            val known = context.wellness.map { it.metric }.filter {
                it.metric == metric &&
                    !it.source.observedAt.isBefore(from) &&
                    !it.source.observedAt.isAfter(context.asOf)
            }.sortedBy { it.source.observedAt }
            Engine2WellnessMetricCoverage(
                count = known.size,
                latestObservedAt = known.lastOrNull()?.source?.observedAt,
                latestReceivedAt = known.lastOrNull()?.source?.receivedAt,
                presentInWindow = known.isNotEmpty()
            )
        }
        return Engine2WellnessCoverage(
            windowDays = days,
            metricCoverage = perMetric,
            missingMetrics = perMetric.filterValues { !it.presentInWindow }.keys,
            hasEnoughInformationForReadinessClassification = false
        )
    }
}
