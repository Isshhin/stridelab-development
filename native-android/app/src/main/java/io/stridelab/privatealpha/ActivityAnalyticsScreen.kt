package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Local, descriptive running totals. No remote analytics collection and no coaching decisions. */
@Composable
internal fun ActivityAnalyticsScreen(
    activities: List<ActivityRecord>,
    onBack: () -> Unit,
    onCompare: (() -> Unit)? = null,
    onOpenActivity: ((String) -> Unit)? = null
) {
    var period by rememberSaveable { mutableStateOf(ActivityAnalyticsPeriod.WEEK) }
    var provider by rememberSaveable { mutableStateOf<String?>(null) }
    var discipline by rememberSaveable { mutableStateOf(ActivityAnalyticsKind.ALL) }
    var distance by rememberSaveable { mutableStateOf(ActivityAnalyticsDistance.ALL) }
    val providers = remember(activities) { ActivityAnalyticsFiltering.availableSources(activities) }
    val filtered = remember(activities, provider, discipline, distance) {
        ActivityAnalyticsFiltering.apply(activities,
            ActivityAnalyticsFilter(provider, discipline, distance))
    }
    val zone = remember { ZoneId.systemDefault() }
    val browsable = remember(filtered) {
        val now = Instant.now()
        filtered.asSequence().filter { it.startedAt <= now }
            .distinctBy { it.source to it.id }
            .sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenBy { it.id })
            .toList()
    }
    val series = remember(filtered, period, zone) {
        ActivityHistoryAnalytics.series(filtered, Instant.now(), zone, period)
    }
    val count = series.sumOf { it.activityCount }
    val measured = series.sumOf { it.recordedDistanceCount }
    val totalMeters = series.mapNotNull { it.recordedDistanceM }.sum()
    val elapsedCount = series.sumOf { it.recordedElapsedCount }
    val elapsedSeconds = series.mapNotNull { it.recordedElapsedS }.sum()
    val maxMeters = series.mapNotNull { it.recordedDistanceM }.maxOrNull() ?: 0.0
    val heading = when (period) {
        ActivityAnalyticsPeriod.WEEK -> "Last 8 calendar weeks"
        ActivityAnalyticsPeriod.MONTH -> "Last 12 calendar months"
        ActivityAnalyticsPeriod.YEAR -> "Last 5 calendar years"
    }
    val labelFormat = DateTimeFormatter.ofPattern(
        when (period) {
            ActivityAnalyticsPeriod.WEEK -> "d MMM yyyy"
            ActivityAnalyticsPeriod.MONTH -> "MMM yyyy"
            ActivityAnalyticsPeriod.YEAR -> "yyyy"
        }, Locale.getDefault()
    )


    MoreFeatureScreen("Running analytics", onBack, "analytics-back") {
        item(key = "analytics-window") {
            MoreFeatureCard {
                MoreFeatureEyebrow("RECORDED HISTORY")
                MoreFeatureMuted(heading)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$count", fontSize = 30.sp, lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold, color = MoreFeatureColors.lime,
                            modifier = Modifier.testTag("analytics-run-count"))
                        MoreFeatureMuted("Recorded runs")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (measured == 0) "—"
                            else String.format(Locale.getDefault(), "%.2f km", totalMeters / 1000.0),
                            fontSize = 24.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold,
                            color = MoreFeatureColors.text,
                            modifier = Modifier.testTag("analytics-distance")
                        )
                        MoreFeatureMuted("Recorded distance")
                    }
                }
                MoreFeatureDivider()
                MoreFeatureMuted(
                    "$measured of $count runs have recorded distance. " +
                        "$elapsedCount have recorded elapsed duration" +
                        (if (elapsedCount > 0) " (${ActivityRecord.duration(elapsedSeconds)} total)." else ".")
                )
                MoreFeatureMuted("Saved source records only · no estimated fitness or missing measurements.")
            }
        }
        item(key = "analytics-range") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityAnalyticsPeriod.entries.forEach { option ->
                    MoreFeatureChip(
                        text = option.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = period == option,
                        onClick = { period = option },
                        modifier = Modifier.weight(1f)
                            .testTag("analytics-period-${option.name.lowercase()}")
                    )
                }
            }
        }
        if (onCompare != null) {
            item(key = "analytics-compare") {
                MoreFeatureCard {
                    MoreFeatureEyebrow("RUN COMPARISON")
                    Text("Compare recorded runs", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = MoreFeatureColors.text)
                    MoreFeatureMuted("Compare two saved runs from the same source and sport at similar distances.")
                    MoreFeatureSecondaryAction(
                        "Compare runs", onCompare, enabled = true,
                        modifier = Modifier.fillMaxWidth().testTag("analytics-compare-open")
                    )
                }
            }
        }
        item(key = "analytics-filters") {
            MoreFeatureCard {
                MoreFeatureEyebrow("FILTER RECORDED RUNS")
                MoreFeatureMuted("Source")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoreFeatureChip(
                        "All sources", provider == null, onClick = { provider = null },
                        modifier = Modifier.testTag("analytics-source-all")
                    )
                    providers.forEach { option ->
                        MoreFeatureChip(
                            if (option == "garmin") "Garmin" else option,
                            selected = provider == option,
                            onClick = { provider = option },
                            modifier = Modifier.testTag("analytics-source-" + option)
                        )
                    }
                }
                MoreFeatureDivider()
                MoreFeatureMuted("Discipline")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityAnalyticsKind.entries.forEach { option ->
                        MoreFeatureChip(
                            option.label, selected = discipline == option,
                            onClick = { discipline = option },
                            modifier = Modifier.testTag("analytics-discipline-" + option.name.lowercase())
                        )
                    }
                }
                MoreFeatureDivider()
                MoreFeatureMuted("Distance")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityAnalyticsDistance.entries.forEach { option ->
                        MoreFeatureChip(
                            option.label, selected = distance == option,
                            onClick = { distance = option },
                            modifier = Modifier.testTag("analytics-distance-" + option.name.lowercase())
                        )
                    }
                }
                if (provider != null || discipline != ActivityAnalyticsKind.ALL ||
                    distance != ActivityAnalyticsDistance.ALL) {
                    MoreFeatureSecondaryAction(
                        "Clear filters",
                        onClick = {
                            provider = null
                            discipline = ActivityAnalyticsKind.ALL
                            distance = ActivityAnalyticsDistance.ALL
                        },
                        enabled = true,
                        modifier = Modifier.fillMaxWidth().testTag("analytics-clear-filters")
                    )
                }
            }
        }
        item(key = "analytics-chart") {
            MoreFeatureCard {
                MoreFeatureEyebrow("DISTANCE BY PERIOD")
                if (count == 0) {
                    MoreFeatureMuted(
                        "No runs match these filters in this period. Missing distance is excluded only when you select a distance band."
                    )
                }
                series.forEachIndexed { index, bucket ->
                    if (index != 0) MoreFeatureDivider()
                    Column(
                        Modifier.fillMaxWidth().testTag("analytics-bucket-${bucket.start}"),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bucket.start.format(labelFormat),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = MoreFeatureColors.text)
                            Text(
                                when {
                                    bucket.activityCount == 0 -> "No runs"
                                    bucket.recordedDistanceM == null -> "Distance not recorded"
                                    else -> String.format(Locale.getDefault(), "%.2f km",
                                        bucket.recordedDistanceM / 1000.0)
                                },
                                fontSize = 13.sp, color = MoreFeatureColors.text
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(6.dp)
                            .background(MoreFeatureColors.divider, RoundedCornerShape(3.dp))) {
                            if (maxMeters > 0 && bucket.recordedDistanceM != null) {
                                Box(
                                    Modifier.fillMaxWidth(
                                        (bucket.recordedDistanceM / maxMeters).toFloat().coerceIn(0f, 1f)
                                    ).fillMaxHeight()
                                        .background(MoreFeatureColors.lime, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                        MoreFeatureMuted("${bucket.activityCount} runs · ${bucket.recordedDistanceCount} with distance")
                    }
                }
            }
        }
        item(key = "analytics-distance-bests") {
            MoreFeatureCard {
                MoreFeatureEyebrow("BEST RECORDED AVERAGE PACES")
                MoreFeatureMuted("Saved activity averages within 2% of each distance. Not certified race PRs.")
                RecordedDistanceBests.distanceTargets.forEachIndexed { index, (label, target) ->
                    if (index > 0) MoreFeatureDivider()
                    val result = RecordedDistanceBests.find(filtered, target)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 13.sp, color = MoreFeatureColors.text)
                        Text(
                            result?.activity?.recordedPace?.let { ActivityRecord.duration(it) + " /km" }
                                ?: "Not recorded",
                            fontSize = 13.sp,
                            color = if (result == null) MoreFeatureColors.muted else MoreFeatureColors.lime,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (result != null) {
                        MoreFeatureMuted(
                            "${result.activity.distanceLabel} · ${result.activity.dateLabel} · " +
                                result.activity.sourceLabel
                        )
                    }
                }
            }
        }
        if (onOpenActivity != null) {
            item(key = "analytics-history-heading") {
                MoreFeatureCard {
                    MoreFeatureEyebrow(
                        "BROWSE SAVED RUNS",
                        modifier = Modifier.testTag("analytics-history-heading")
                    )
                    MoreFeatureMuted(
                        "${browsable.size} runs match these filters across all saved dates. " +
                            "Open an activity to inspect its recorded GPS, splits, and metrics."
                    )
                }
            }
            items(browsable, key = { "analytics-record-${it.source}-${it.id}" }) { activity ->
                MoreFeatureActivityRow(
                    activity, tag = "activity-${activity.id}",
                    onClick = { onOpenActivity(activity.id) }
                )
            }
        }
    }
}
