package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import java.util.Locale

/** Read-only run-to-run comparison; does not change training plans or source recordings. */
@Composable
internal fun ActivityRunComparisonScreen(
    activities: List<ActivityRecord>,
    onBack: () -> Unit
) {
    val runs = remember(activities) { ActivityRunComparison.recentRuns(activities) }
    fun idOf(record: ActivityRecord) = "${record.source}:${record.id}"
    var recentKey by rememberSaveable { mutableStateOf<String?>(null) }
    var earlierKey by rememberSaveable { mutableStateOf<String?>(null) }
    val recent = runs.firstOrNull { idOf(it) == recentKey }
    val candidates = remember(recent, runs) {
        recent?.let { ActivityRunComparison.earlierCandidates(it, runs) }.orEmpty()
    }
    val earlier = candidates.firstOrNull { idOf(it) == earlierKey }
    val comparison = if (recent != null && earlier != null)
        ActivityRunComparison.compare(recent, earlier) else null

    Screen("Compare runs", "RECORDED HISTORY", compactTitle = true) {
        item(key = "compare-back") {
            TextButton(onClick = onBack, modifier = Modifier.testTag("compare-back")) {
                Text("← Back to analytics")
            }
        }
        if (runs.isEmpty()) {
            item(key = "compare-empty") {
                SignalCard {
                    Text("No comparable running history yet", style = MaterialTheme.typography.titleMedium)
                    Text("Record runs of at least 1 km with source distance to compare them here.")
                }
            }
        } else if (recent == null) {
            item(key = "compare-choose-recent") {
                SignalCard {
                    Text("Choose a recent run", style = MaterialTheme.typography.titleMedium)
                    Text("Select a run to find earlier recordings from the same source and sport within 10% of its distance. Only saved records appear.")
                }
            }
            items(runs, key = { "recent-${it.source}-${it.id}" }) { record ->
                CompareRunRow(record, "recent-${record.id}") {
                    recentKey = idOf(record)
                    earlierKey = null
                }
            }
        } else {
            item(key = "compare-selected-recent") {
                SignalCard {
                    Text("RECENT RUN", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(recent.title, style = MaterialTheme.typography.titleMedium)
                    Text("${recent.dateLabel} · ${recent.distanceLabel} · ${recent.sourceLabel}")
                    TextButton(onClick = {
                        recentKey = null
                        earlierKey = null
                    }, modifier = Modifier.testTag("compare-change-recent")) {
                        Text("Change recent run")
                    }
                }
            }
            if (earlier == null || comparison == null) {
                item(key = "compare-choose-earlier") {
                    SignalCard {
                        Text("Choose an earlier comparable run",
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (candidates.isEmpty())
                                "No earlier recorded runs from this source and sport are within 10% of this distance."
                            else
                                "${candidates.size} earlier matches · same provider and sport, similar distance. " +
                                "Effort, elevation, weather, and route may still differ.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                items(candidates, key = { "earlier-${it.source}-${it.id}" }) { record ->
                    CompareRunRow(record, "earlier-${record.id}") { earlierKey = idOf(record) }
                }
            } else {
                item(key = "compare-details") {
                    SignalCard {
                        Text("RECORDED COMPARISON", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text("${earlier.dateLabel} → ${recent.dateLabel}",
                            style = MaterialTheme.typography.titleMedium)
                        Text("${recent.sourceLabel} · ${recent.type}")
                        ComparisonMetric("Distance", earlier.distanceLabel, recent.distanceLabel)
                        ComparisonMetric("Recorded average pace", earlier.paceLabel, recent.paceLabel)
                        ComparisonMetric("Elapsed duration", earlier.durationLabel, recent.durationLabel)
                        ComparisonMetric(
                            "Recorded average HR",
                            earlier.averageHr?.let {
                                String.format(Locale.getDefault(), "%.0f bpm", it)
                            } ?: "Not recorded",
                            recent.averageHr?.let {
                                String.format(Locale.getDefault(), "%.0f bpm", it)
                            } ?: "Not recorded"
                        )
                        Text(
                            comparison.recordedPaceChangePercent?.let { change ->
                                when {
                                    kotlin.math.abs(change) < 1.0 -> "Recorded pace is within 1% of the earlier run."
                                    change < 0.0 -> String.format(
                                        Locale.getDefault(), "Recorded pace was %.1f%% faster than the earlier run.", -change
                                    )
                                    else -> String.format(
                                        Locale.getDefault(), "Recorded pace was %.1f%% slower than the earlier run.", change
                                    )
                                }
                            } ?: "Recorded average pace is unavailable for one or both runs.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Descriptive records only. Similar distance does not prove similar effort, conditions or fitness. No training changes are made.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { earlierKey = null },
                            modifier = Modifier.testTag("compare-change-earlier")
                        ) { Text("Compare with another run") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareRunRow(record: ActivityRecord, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("compare-row-" + tag),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(record.title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text("${record.dateLabel} · ${record.distanceLabel}",
                style = MaterialTheme.typography.bodyMedium)
            Text(record.sourceLabel, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ComparisonMetric(label: String, earlier: String, recent: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Earlier: $earlier", style = MaterialTheme.typography.bodyMedium)
        Text("Recent: $recent", style = MaterialTheme.typography.bodyMedium)
    }
}
