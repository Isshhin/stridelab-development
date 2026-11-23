package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun WorkoutEvidenceScreen(
    rows: List<WorkoutEvidence>,
    loading: Boolean,
    manualHistoryError: String?,
    retry: () -> Unit,
    today: LocalDate,
    back: () -> Unit,
    open: (WorkoutEvidenceLink) -> Unit
) {
    var view by rememberSaveable { mutableStateOf("history") }
    var source by rememberSaveable { mutableStateOf<WorkoutEvidenceSource?>(null) }
    var discipline by rememberSaveable { mutableStateOf<ManualDiscipline?>(null) }
    var chooseSource by remember { mutableStateOf(false) }
    var chooseDiscipline by remember { mutableStateOf(false) }
    val displayed = remember(rows, source, discipline) {
        rows.filter { (source == null || source in it.sources) && (discipline == null || it.discipline == discipline) }
    }
    val week = tracksideCalendarMonday(today)
    val recent = remember(rows, week, today) { rows.filter { it.date in week.minusWeeks(3)..today } }
    val comparisons = remember(recent) { exerciseComparisons(recent) }

    FreeformPage("Workout history", null, back) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("workout-evidence"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StrideLabLiquidSegmentedControl(
                    labels = listOf("history" to "History", "progress" to "Progress"),
                    selectedKey = view,
                    accent = freeformPalette.lime,
                    testTagPrefix = "workout-evidence-view",
                    onSelect = { view = it }
                )
            }
            if (manualHistoryError != null) item {
                FreeformPanel {
                    Text(manualHistoryError, color = freeformPalette.secondary)
                    TextButton(onClick = retry) { Text("Retry", color = freeformPalette.lime) }
                }
            }
            if (loading) {
                item { Text("Loading workouts…", color = freeformPalette.secondary) }
            } else if (view == "history") {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { chooseSource = true },
                                modifier = Modifier.fillMaxWidth().testTag("workout-evidence-source")) {
                                Text(source?.label ?: "All sources")
                            }
                            DropdownMenu(expanded = chooseSource, onDismissRequest = { chooseSource = false }) {
                                (listOf<WorkoutEvidenceSource?>(null) + WorkoutEvidenceSource.entries).forEach {
                                    DropdownMenuItem(text = { Text(it?.label ?: "All sources") },
                                        onClick = { source = it; chooseSource = false })
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { chooseDiscipline = true },
                                modifier = Modifier.fillMaxWidth().testTag("workout-evidence-discipline")) {
                                Text(discipline?.label ?: "All disciplines")
                            }
                            DropdownMenu(expanded = chooseDiscipline, onDismissRequest = { chooseDiscipline = false }) {
                                (listOf<ManualDiscipline?>(null) + ManualDiscipline.entries).forEach {
                                    DropdownMenuItem(text = { Text(it?.label ?: "All disciplines") },
                                        onClick = { discipline = it; chooseDiscipline = false })
                                }
                            }
                        }
                    }
                }
                if (displayed.isEmpty()) item {
                    Text("No completed workouts in this view.", color = freeformPalette.secondary)
                }
                var heading: LocalDate? = null
                for (row in displayed) {
                    if (heading != row.date) {
                        heading = row.date
                        item(key = "date-${row.date}") {
                            Text(customDateLabel(row.date), color = freeformPalette.ink,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    item(key = row.key) {
                        Column {
                            val support = listOfNotNull(
                                listOfNotNull(row.sourceLabel, row.discipline?.label).joinToString(" · "),
                                row.detail.takeIf { it.isNotBlank() }
                            ).joinToString("\n")
                            FreeformTile(
                                row.title, support, row.discipline?.markColor ?: freeformPalette.secondary,
                                onClick = { open(row.link) }, modifier = Modifier.testTag("evidence-${row.key}")
                            )
                            val linked = (row.link as? WorkoutEvidenceLink.Imported)?.coached.orEmpty()
                            linked.forEach { coach ->
                                TextButton(onClick = { open(coach) }) {
                                    Text(if (linked.size == 1) "View coached session" else "View coached session · ${coach.sessionId}",
                                        color = freeformPalette.secondary)
                                }
                            }
                        }
                    }
                }
            } else {
                item { EvidenceFrequency(recent, week) }
                if (recent.isNotEmpty()) {
                    item {
                        FreeformPanel {
                            Text("Workout sources", color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium)
                            val counts = recent.flatMap { it.sources }.groupingBy { it }.eachCount()
                            if (recent.any { it.sources.size > 1 }) {
                                Text("A linked workout can appear under two sources. Weekly totals count it once.",
                                    color = freeformPalette.secondary, style = MaterialTheme.typography.bodySmall)
                            }
                            WorkoutEvidenceSource.entries.forEach { kind ->
                                val count = counts[kind] ?: 0
                                if (count > 0) {
                                    Text(
                                        "${kind.label} · $count", color = freeformPalette.secondary,
                                        modifier = Modifier.testTag("progress-source-${kind.name}")
                                    )
                                }
                            }
                        }
                    }
                    item { Text("By discipline", color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium) }
                    items(recent.groupBy { it.discipline }.entries.toList(), key = { it.key?.name ?: "other" }) { (discipline, group) ->
                        FreeformPanel {
                            val count = group.size
                            Text("${discipline?.label ?: "Other activities"} · $count workout${if (count == 1) "" else "s"}",
                                color = freeformPalette.ink, style = MaterialTheme.typography.titleSmall)
                            val evidence = evidenceMetrics(group, discipline)
                            if (evidence.isNotEmpty()) Text(evidence.joinToString(" · "), color = freeformPalette.secondary)
                        }
                    }
                }

                if (comparisons.isNotEmpty()) {
                    item { Text("Exercise history", color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium) }
                    items(comparisons, key = { "${it.discipline}:${it.name.trim().lowercase(Locale.ROOT)}" }) { comparison ->
                        FreeformPanel {
                            Text(comparison.name, color = freeformPalette.ink, fontWeight = FontWeight.SemiBold)
                            Text("Latest · ${customDateLabel(comparison.latestDate)} · ${comparison.latest.label(comparison.discipline)}",
                                color = freeformPalette.ink, style = MaterialTheme.typography.bodyMedium)
                            val previous = comparison.previous
                            val previousDate = comparison.previousDate
                            if (previous != null && previousDate != null) {
                                Text("Previous · ${customDateLabel(previousDate)} · ${previous.label(comparison.discipline)}",
                                    color = freeformPalette.secondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceFrequency(rows: List<WorkoutEvidence>, monday: LocalDate) {
    val counts = rows.groupingBy { tracksideCalendarMonday(it.date) }.eachCount()
    val maximum = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    FreeformPanel {
        Text("Last four weeks", color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text("${rows.size} completed workout${if (rows.size == 1) "" else "s"} recorded",
            color = freeformPalette.secondary)
        (3L downTo 0).forEach { offset ->
            val start = monday.minusWeeks(offset)
            val count = counts[start] ?: 0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(customDateLabel(start), Modifier.weight(0.45f), color = freeformPalette.secondary,
                    style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(0.42f).height(6.dp).background(freeformPalette.border, RoundedCornerShape(4.dp))) {
                    if (count > 0) Box(Modifier.fillMaxWidth(count.toFloat() / maximum).fillMaxHeight()
                        .background(freeformPalette.lime, RoundedCornerShape(4.dp)))
                }
                Text(count.toString(), Modifier.weight(0.13f).testTag("progress-week-$start"), color = freeformPalette.ink)
            }
        }
    }
}

@Composable
internal fun HistoryBaselineCard(
    suggestion: HistoryBaselineSuggestion, useWeekly: (Double) -> Unit, useLongest: (Double) -> Unit
) {
    FreeformPanel(Modifier.testTag("builder-confirmed-history-suggestion")) {
        Text("From completed workouts", color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(
            "Distance is recorded for every logged run in ${suggestion.loggedWeeks} of the last 4 finished weeks." +
                if (suggestion.undistancedRuns == 0) "" else
                    " ${suggestion.undistancedRuns} run${if (suggestion.undistancedRuns == 1) "" else "s"} missing distance.",
            color = freeformPalette.secondary,
            style = MaterialTheme.typography.bodyMedium
        )
        suggestion.weeklyKm?.let { weekly ->
            Text("The weekly average uses only those weeks. You can edit both distances after applying them.",
                color = freeformPalette.secondary, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { useWeekly(weekly) }, modifier = Modifier.testTag("builder-use-history-weekly")) {
                Text("Use ${freeformNumber(weekly)} km/week", color = freeformPalette.lime)
            }
        }
        suggestion.longestKm?.let { longest ->
            TextButton(onClick = { useLongest(longest) }, modifier = Modifier.testTag("builder-use-history-longest")) {
                Text("Use ${freeformNumber(longest)} km as longest", color = freeformPalette.lime)
            }
        }
    }
}
