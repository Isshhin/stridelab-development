package io.stridelab.privatealpha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A locally logged session is not a source-recorded wearable activity. Show only
 * performed values, with omissions explicit and no synthetic splits or GPS.
 */
@Composable internal fun LoggedActivityDetail(
    log: ActivityDetailPresentation.LocallyLogged,
    onOpenSession: () -> Unit
) {
    SignalCard {
        Text("ACTUAL WORKOUT LOG",style=MaterialTheme.typography.labelMedium,
            color=MaterialTheme.colorScheme.primary)
        Text(log.kind.uppercase(Locale.ROOT)+" · "+log.status.replace('_',' '),
            style=MaterialTheme.typography.titleMedium)
        Text(log.performedOn?.toString() ?: "Actual workout date not recorded",
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        val recorded=log.recordedAt?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
        if(recorded!=null) Text("Saved $recorded",style=MaterialTheme.typography.bodySmall)
        Text("Actual duration: "+(log.minutes?.let {
            String.format(Locale.getDefault(),"%.1f minutes",it)
        } ?: "Not recorded"))
        Text("Actual distance: "+(log.distanceKm?.let {
            String.format(Locale.getDefault(),"%.2f km",it)
        } ?: "Not recorded"))
        Text("Session effort: "+(log.effort?.let { "$it / 10" } ?: "Not recorded"))
        if(log.linkedActivityId!=null) Text(
            "Linked source activity is retained separately; no second activity was generated.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Performed sets",style=MaterialTheme.typography.titleMedium)
        if(log.sets.isEmpty()) {
            Text("No actual sets were recorded. Do not interpret this as all prescribed sets being completed.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            log.sets.take(100).forEach { set ->
                val values=listOfNotNull(
                    set.reps?.let { "$it reps" },
                    set.kg?.let { String.format(Locale.getDefault(),"%.1f kg",it) },
                    set.seconds?.let { "$it seconds" }
                ).joinToString(" · ").ifBlank { "Measurements not recorded" }
                Text("Exercise ${set.exerciseId} · set ${set.set} · $values",
                    style=MaterialTheme.typography.bodyMedium)
            }
            if(log.sets.size>100) Text("Showing first 100 of ${log.sets.size} recorded sets.")
        }
        if(log.notes.isNotBlank()) {
            Text("Notes",style=MaterialTheme.typography.titleMedium)
            Text(log.notes)
        }
        OutlinedButton(onClick=onOpenSession,modifier=Modifier.fillMaxWidth().padding(top=4.dp)) {
            Text("Open saved session")
        }
    }
}
