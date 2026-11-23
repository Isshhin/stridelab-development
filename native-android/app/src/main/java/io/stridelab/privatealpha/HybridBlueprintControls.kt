package io.stridelab.privatealpha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HybridBlueprintControls(
    preview: HybridPlanPreview, selected: String?, weekIndex: Int, onSelect: (String) -> Unit
) {
    val first = preview.drafts.minOf { it.second.request.start }
    val start = first.plusWeeks(weekIndex.toLong())
    val end = start.plusDays(6)
    TracksideWorkoutNeutralCard {
        Text("Review all ${preview.drafts.size} tracks", color = PlanBuilderPalette.text)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((track, _) in preview.drafts) {
                SignalChip(
                    selected = track == (selected ?: preview.drafts.first().first),
                    onClick = { onSelect(track) },
                    label = { Text(trainingTrackLabel(track)) },
                    modifier = Modifier.testTag("builder-blueprint-track-$track")
                )
            }
        }
        val week = preview.drafts.flatMap { (_, draft) ->
            draft.sessions.filter { it.date in start..end }
        }.groupBy { it.date }.toSortedMap()
        for ((date, sessions) in week) {
            Text("${date.dayOfWeek.name.take(3)} · ${sessions.joinToString { it.title }}",
                color = PlanBuilderPalette.secondary)
        }
        preview.notes.filter { note ->
            val date = runCatching { LocalDate.parse(note.substringBefore(" · ")) }.getOrNull()
            date == null || date in start..end
        }.forEach { Text(it, color = PlanBuilderPalette.text) }
    }
}
