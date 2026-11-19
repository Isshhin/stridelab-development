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
import androidx.compose.ui.unit.sp
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


    MoreFeatureScreen("Compare runs", onBack, "compare-back") {
        if (runs.isEmpty()) {
            item(key = "compare-empty") {
                MoreFeatureCard {
                    MoreFeatureEyebrow("RECORDED HISTORY")
                    Text("No comparable running history yet",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = MoreFeatureColors.text)
                    MoreFeatureMuted("Record runs of at least 1 km with source distance to compare them here.")
                }
            }
        } else if (recent == null) {
            item(key = "compare-choose-recent") {
                MoreFeatureCard {
                    MoreFeatureEyebrow("STEP 1 OF 2")
                    Text("Choose a recent run", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = MoreFeatureColors.text)
                    MoreFeatureMuted(
                        "Select a saved run to find earlier recordings from the same source and sport within 10% of its distance."
                    )
                }
            }
            items(runs, key = { "recent-${it.source}-${it.id}" }) { record ->
                MoreFeatureActivityRow(record, "compare-row-recent-${record.id}") {
                    recentKey = idOf(record)
                    earlierKey = null
                }
            }
        } else {
            item(key = "compare-selected-recent") {
                MoreFeatureCard {
                    MoreFeatureEyebrow("RECENT RUN")
                    Text(recent.title, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = MoreFeatureColors.text)
                    MoreFeatureMuted(
                        "${recent.dateLabel} · ${recent.distanceLabel} · ${recent.sourceLabel}"
                    )
                    MoreFeatureSecondaryAction(
                        "Change recent run",
                        onClick = { recentKey = null; earlierKey = null },
                        enabled = true,
                        modifier = Modifier.fillMaxWidth().testTag("compare-change-recent")
                    )
                }
            }
            if (earlier == null || comparison == null) {
                item(key = "compare-choose-earlier") {
                    MoreFeatureCard {
                        MoreFeatureEyebrow("STEP 2 OF 2")
                        Text("Choose an earlier comparable run",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = MoreFeatureColors.text)
                        MoreFeatureMuted(
                            if (candidates.isEmpty())
                                "No earlier recorded runs from this source and sport are within 10% of this distance."
                            else
                                "${candidates.size} earlier matches · same provider and sport, similar distance. " +
                                    "Effort, elevation, weather, and route may still differ."
                        )
                    }
                }
                items(candidates, key = { "earlier-${it.source}-${it.id}" }) { record ->
                    MoreFeatureActivityRow(record, "compare-row-earlier-${record.id}") {
                        earlierKey = idOf(record)
                    }
                }
            } else {
                item(key = "compare-details") {
                    MoreFeatureCard {
                        MoreFeatureEyebrow("RECORDED COMPARISON")
                        Text("${earlier.dateLabel} → ${recent.dateLabel}",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = MoreFeatureColors.text)
                        MoreFeatureMuted("${recent.sourceLabel} · ${recent.type}")
                        MoreFeatureDivider()
                        ComparisonMetric("Distance", earlier.distanceLabel, recent.distanceLabel)
                        MoreFeatureDivider()
                        ComparisonMetric("Recorded average pace", earlier.paceLabel, recent.paceLabel)
                        MoreFeatureDivider()
                        ComparisonMetric("Elapsed duration", earlier.durationLabel, recent.durationLabel)
                        MoreFeatureDivider()
                        ComparisonMetric(
                            "Recorded average HR",
                            earlier.averageHr?.let {
                                String.format(Locale.getDefault(), "%.0f bpm", it)
                            } ?: "Not recorded",
                            recent.averageHr?.let {
                                String.format(Locale.getDefault(), "%.0f bpm", it)
                            } ?: "Not recorded"
                        )
                        MoreFeatureDivider()
                        Text(
                            comparison.recordedPaceChangePercent?.let { change ->
                                when {
                                    kotlin.math.abs(change) < 1.0 ->
                                        "Recorded pace is within 1% of the earlier run."
                                    change < 0.0 -> String.format(Locale.getDefault(),
                                        "Recorded pace was %.1f%% faster than the earlier run.", -change)
                                    else -> String.format(Locale.getDefault(),
                                        "Recorded pace was %.1f%% slower than the earlier run.", change)
                                }
                            } ?: "Recorded average pace is unavailable for one or both runs.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MoreFeatureColors.lime
                        )
                        MoreFeatureMuted(
                            "Descriptive records only. Similar distance does not prove similar effort, " +
                                "conditions or fitness. No training changes are made."
                        )
                        MoreFeatureSecondaryAction(
                            "Compare with another run",
                            onClick = { earlierKey = null },
                            enabled = true,
                            modifier = Modifier.fillMaxWidth().testTag("compare-change-earlier")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonMetric(label: String, earlier: String, recent: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        MoreFeatureEyebrow(label.uppercase())
        Text("Earlier: $earlier", fontSize = 13.sp, color = MoreFeatureColors.muted)
        Text("Recent: $recent", fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, color = MoreFeatureColors.text)
    }
}
