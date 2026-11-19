package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    Screen("Running analytics", "RECORDED HISTORY", compactTitle = true) {
        item(key = "analytics-back") {
            TextButton(onClick = onBack, modifier = Modifier.testTag("analytics-back")) {
                Text("← Back to More")
            }
        }
        item(key = "analytics-window") {
            SignalCard {
                Text(heading, style = MaterialTheme.typography.titleMedium)
                Text("$count recorded runs", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("analytics-run-count"))
                Text(
                    if (measured == 0) "Distance not recorded"
                    else String.format(Locale.getDefault(), "%.2f km recorded", totalMeters / 1000.0),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("analytics-distance")
                )
                Text(
                    "$measured of $count runs have recorded distance. " +
                        "$elapsedCount have recorded elapsed duration" +
                        (if (elapsedCount > 0) " (${ActivityRecord.duration(elapsedSeconds)} total)." else "."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Recorded data only · no estimated fitness, effort, or missing measurements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onCompare != null) {
            item(key = "analytics-compare") {
                SignalCard {
                    Text("COMPARE RECORDED RUNS", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Select two earlier and recent runs of similar recorded distance from the same provider and sport.",
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onCompare, modifier = Modifier.testTag("analytics-compare-open")) {
                        Text("Compare runs")
                    }
                }
            }
        }
        item(key = "analytics-range") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityAnalyticsPeriod.entries.forEach { option ->
                    FilterChip(
                        selected = period == option,
                        onClick = { period = option },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.weight(1f).testTag("analytics-period-${option.name.lowercase()}")
                    )
                }
            }
        }
        item(key = "analytics-filters") {
            SignalCard {
                Text("FILTER RECORDED RUNS", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("Source", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = provider == null, onClick = { provider = null },
                        label = { Text("All sources") },
                        modifier = Modifier.testTag("analytics-source-all"))
                    providers.forEach { option ->
                        FilterChip(selected = provider == option, onClick = { provider = option },
                            label = { Text(if (option == "garmin") "Garmin" else option) },
                            modifier = Modifier.testTag("analytics-source-" + option))
                    }
                }
                Text("Discipline", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityAnalyticsKind.entries.forEach { option ->
                        FilterChip(selected = discipline == option, onClick = { discipline = option },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("analytics-discipline-" + option.name.lowercase()))
                    }
                }
                Text("Distance", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityAnalyticsDistance.entries.forEach { option ->
                        FilterChip(selected = distance == option, onClick = { distance = option },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("analytics-distance-" + option.name.lowercase()))
                    }
                }
                if (provider != null || discipline != ActivityAnalyticsKind.ALL ||
                    distance != ActivityAnalyticsDistance.ALL) {
                    TextButton(onClick = {
                        provider = null
                        discipline = ActivityAnalyticsKind.ALL
                        distance = ActivityAnalyticsDistance.ALL
                    }, modifier = Modifier.testTag("analytics-clear-filters")) {
                        Text("Clear filters")
                    }
                }
            }
        }
        item(key = "analytics-distance-bests") {
            SignalCard {
                Text("BEST SAVED AVERAGE PACES", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(
                    "All saved runs matching these filters · within 2% of the named distance. " +
                        "Not certified race personal records or proof of fitness improvement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RecordedDistanceBests.distanceTargets.forEach { (label, target) ->
                    val result = RecordedDistanceBests.find(filtered, target)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            result?.activity?.recordedPace?.let { ActivityRecord.duration(it) + " /km" }
                                ?: "Not recorded",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if(result != null) {
                        Text(
                            "${result.activity.distanceLabel} · ${result.activity.dateLabel} · " +
                                result.activity.sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item(key = "analytics-chart") {
            SignalCard {
                Text("DISTANCE BY PERIOD", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (count == 0) Text(
                    "No saved running activities match this period and filters. Missing distance is excluded only when a distance band is selected.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                series.forEachIndexed { index, bucket ->
                    if (index != 0) HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
                    )
                    Column(
                        Modifier.fillMaxWidth().testTag("analytics-bucket-${bucket.start}"),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bucket.start.format(labelFormat),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    bucket.activityCount == 0 -> "No runs"
                                    bucket.recordedDistanceM == null -> "Distance not recorded"
                                    else -> String.format(Locale.getDefault(), "%.2f km", bucket.recordedDistanceM / 1000.0)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth().height(6.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                        ) {
                            if (maxMeters > 0 && bucket.recordedDistanceM != null) {
                                Box(
                                    Modifier.fillMaxWidth(
                                        (bucket.recordedDistanceM / maxMeters).toFloat().coerceIn(0f, 1f)
                                    ).fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                        Text(
                            "${bucket.activityCount} runs · ${bucket.recordedDistanceCount} with distance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if(onOpenActivity != null) {
            item(key = "analytics-history-heading") {
                SignalCard {
                    Text("BROWSE SAVED RUNS", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("analytics-history-heading"))
                    Text(
                        "${browsable.size} running activities matching the selected filters, " +
                            "across all saved dates. Open one for its actual GPS, splits and source measurements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(browsable, key = { "analytics-record-${it.source}-${it.id}" }) { activity ->
                ActivityRow(activity) { onOpenActivity(activity.id) }
            }
        }

    }
}
