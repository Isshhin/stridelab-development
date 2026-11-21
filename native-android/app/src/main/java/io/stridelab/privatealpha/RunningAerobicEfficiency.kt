package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class AerobicEfficiencyWeekPoint(
    val weekStart: LocalDate,
    val avgPaceSecPerKm: Double,
    val avgHrBpm: Double,
    val efficiencyFactor: Double,
    val runCount: Int
)

internal data class AerobicEfficiencySummary(
    val windowWeeks: Int,
    val points: List<AerobicEfficiencyWeekPoint>,
    val totalQualifyingRuns: Int,
    val earlyAvgPace: Double?,
    val lateAvgPace: Double?,
    val earlyAvgHr: Double?,
    val lateAvgHr: Double?,
    val percentChange: Double?,
    val caveat: String
)

internal object RunningAerobicEfficiency {
    private data class AerobicSample(
        val weekStart: LocalDate,
        val paceSecPerKm: Double,
        val avgHr: Double,
        val efficiencyFactor: Double
    )

    fun analyze(
        activities: List<ActivityRecord>,
        windowWeeks: Int = 8,
        asOf: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): AerobicEfficiencySummary {
        val clampedWeeks = windowWeeks.coerceIn(4, 24)
        val endWeek = TodayDisciplineAnalytics.monday(asOf.atZone(zone).toLocalDate())
        val startWeek = endWeek.minusWeeks((clampedWeeks - 1).toLong())

        val candidates = ActivityDisplayConsolidation.visible(activities)
            .filter { DashboardSummary.isRun(it) && !it.startedAt.isAfter(asOf) }
            .mapNotNull { run ->
                val distM = run.distanceM?.takeIf { it.isFinite() && it >= 3_000.0 } ?: return@mapNotNull null
                val durS = (run.movingTimeS ?: run.durationS)?.takeIf { it.isFinite() && it >= 900.0 } ?: return@mapNotNull null
                val pace = (run.recordedPace ?: run.garminMovingPaceSPerKm ?: (durS * 1000.0 / distM))
                    .takeIf { it.isFinite() && it in 180.0..750.0 } ?: return@mapNotNull null
                val hr = run.averageHr?.takeIf { it.isFinite() && it in 90.0..215.0 } ?: return@mapNotNull null
                val wkStart = TodayDisciplineAnalytics.monday(run.startedAt.atZone(zone).toLocalDate())
                if (wkStart.isBefore(startWeek) || wkStart.isAfter(endWeek)) return@mapNotNull null
                Triple(wkStart, pace, hr)
            }

        if (candidates.isEmpty()) {
            return AerobicEfficiencySummary(
                windowWeeks = clampedWeeks,
                points = emptyList(),
                totalQualifyingRuns = 0,
                earlyAvgPace = null,
                lateAvgPace = null,
                earlyAvgHr = null,
                lateAvgHr = null,
                percentChange = null,
                caveat = "Need at least 4 steady runs (>= 3 km with HR) over the last $clampedWeeks weeks to compute aerobic efficiency."
            )
        }

        // Exclude likely interval/threshold/race efforts so hard outliers do not masquerade as easy aerobic gains.
        val sortedPaces = candidates.map { it.second }.sorted()
        val medianPace = sortedPaces[sortedPaces.size / 2]
        val easyCutoff = medianPace * 0.88

        val filtered = candidates
            .filter { it.second >= easyCutoff }
            .map { (wk, pace, hr) ->
                val speedMetersPerMin = 60_000.0 / pace
                AerobicSample(
                    weekStart = wk,
                    paceSecPerKm = pace,
                    avgHr = hr,
                    efficiencyFactor = speedMetersPerMin / hr
                )
            }

        val byWeek = filtered.groupBy { it.weekStart }
        val points = byWeek.entries
            .sortedBy { it.key }
            .map { (wk, runs) ->
                val avgPace = runs.map { it.paceSecPerKm }.average()
                val avgHr = runs.map { it.avgHr }.average()
                val avgEf = runs.map { it.efficiencyFactor }.average()
                AerobicEfficiencyWeekPoint(
                    weekStart = wk,
                    avgPaceSecPerKm = avgPace,
                    avgHrBpm = avgHr,
                    efficiencyFactor = avgEf,
                    runCount = runs.size
                )
            }

        if (filtered.size < 4 || points.size < 3) {
            return AerobicEfficiencySummary(
                windowWeeks = clampedWeeks,
                points = points,
                totalQualifyingRuns = filtered.size,
                earlyAvgPace = null,
                lateAvgPace = null,
                earlyAvgHr = null,
                lateAvgHr = null,
                percentChange = null,
                caveat = "Building baseline (${filtered.size} qualifying easy runs across ${points.size} weeks; need >= 4 runs & >= 3 weeks)."
            )
        }

        val splitIdx = points.size / 2
        val earlyWeeks = points.take(splitIdx)
        val lateWeeks = points.drop(splitIdx)
        val earlyAvgPace = earlyWeeks.map { it.avgPaceSecPerKm }.average()
        val lateAvgPace = lateWeeks.map { it.avgPaceSecPerKm }.average()
        val earlyAvgHr = earlyWeeks.map { it.avgHrBpm }.average()
        val lateAvgHr = lateWeeks.map { it.avgHrBpm }.average()
        val earlyEf = earlyWeeks.map { it.efficiencyFactor }.average()
        val lateEf = lateWeeks.map { it.efficiencyFactor }.average()
        val pctChange = if (earlyEf > 0.0) ((lateEf - earlyEf) / earlyEf) * 100.0 else null

        val caveat = "Based on ${filtered.size} aerobic runs (>= 3 km) across ${points.size} weeks. Heat, hills, and wrist-HR lag can affect short-term readings."

        return AerobicEfficiencySummary(
            windowWeeks = clampedWeeks,
            points = points,
            totalQualifyingRuns = filtered.size,
            earlyAvgPace = earlyAvgPace,
            lateAvgPace = lateAvgPace,
            earlyAvgHr = earlyAvgHr,
            lateAvgHr = lateAvgHr,
            percentChange = pctChange,
            caveat = caveat
        )
    }
}
