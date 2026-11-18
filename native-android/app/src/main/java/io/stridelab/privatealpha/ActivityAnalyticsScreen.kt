package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
internal fun ActivityAnalyticsScreen(activities: List<ActivityRecord>, onBack: () -> Unit) {
    var period by rememberSaveable { mutableStateOf(ActivityAnalyticsPeriod.WEEK) }
    val zone = remember { ZoneId.systemDefault() }
    val series = remember(activities, period, zone) {
        ActivityHistoryAnalytics.series(activities, Instant.now(), zone, period)
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
        item(key = "analytics-chart") {
            SignalCard {
                Text("DISTANCE BY PERIOD", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (count == 0) Text(
                    "No saved running activities in this period. Import or log a run to see recorded history.",
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
    }
}
