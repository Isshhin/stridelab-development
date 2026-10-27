package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun WeeklyDashboard(history: List<ActivityRecord>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(),plans:List<PlanSnapshot> = emptyList()) {
    val recent=DashboardSummary.window(history,now,zone,plans=plans)
    val previous=DashboardSummary.window(history,now,zone,7,plans)
    SignalCard {
        Text("LAST 7 DAYS",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Text("${recent.start} – ${recent.end}",style=MaterialTheme.typography.bodySmall)
        Text("${recent.activeDays} active ${if(recent.activeDays==1) "day" else "days"} · ${recent.sessionCount} ${if(recent.sessionCount==1) "activity" else "activities"}",style=MaterialTheme.typography.titleLarge)
        val time=recent.durationSeconds?.let { ActivityRecord.duration(it) } ?: "Not recorded"
        val distance=recent.runningDistanceM?.let { String.format(Locale.getDefault(),"%.2f km",it/1000) } ?: "Not recorded"
        if(LocalDensity.current.fontScale>1.15f) MetricPair("Recorded training time" to time,"Running distance" to distance)
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Metric("Training time",time,Modifier.weight(1f).fillMaxHeight())
            Metric("Running distance",distance,Modifier.weight(1f).fillMaxHeight())
        }
        if(recent.missingDuration>0 || recent.missingRunDistance>0) Text("Partial totals: ${recent.missingDuration} sessions lack duration; ${recent.missingRunDistance} runs lack distance.",style=MaterialTheme.typography.bodySmall)
        if(plans.any { p->p.sessions.any { it.status=="completed"&&it.log!=null } })Text("${recent.nativeLogs.size} dated, user-confirmed workout logs included. Linked synced activities are counted once. ${recent.omittedUndatedLogs} workout logs without an actual workout date are excluded.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val daily=recent.daily(zone)
        val max=daily.maxOfOrNull { it.durations.sum() } ?: 0.0
        daily.forEach { row ->
            val day=row.date;val values=row.durations;val seconds=values.sum()
            val label=when { row.sessions==0 -> "No cached activity"; values.isEmpty() -> "Duration not recorded"; else -> ActivityRecord.duration(seconds) + if(values.size<row.sessions) " · partial" else "" }
            Text("${day.dayOfWeek.name.take(3)} · $label",style=MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(3.dp))) {
                if(max>0 && seconds>0) Box(Modifier.fillMaxWidth((seconds/max).toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary,RoundedCornerShape(3.dp)))
            }
        }
        if(recent.distribution.isNotEmpty()) Text(recent.distribution.entries.joinToString(" · ") { "${it.key} ${it.value}" },style=MaterialTheme.typography.bodyMedium)
        val prior=previous.durationSeconds?.let { ActivityRecord.duration(it) } ?: "not recorded"
        Text("Previous 7 days: ${previous.activeDays} active days · recorded time $prior. These are cached-record totals, not a fitness or recovery assessment. Missing activities may change them.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun WellnessOverview(records:List<WellnessRecord>, refresh:()->Unit, enabled:Boolean) {
    SignalCard {
        Text("WELLNESS RECORDS",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        val latest=records.filter { it.recordedAt<=Instant.now() }.sortedByDescending { it.recordedAt }.distinctBy { it.type }
        if(latest.isEmpty()) Text("No authentic wellness records cached. No readiness score or missing measurements are estimated.")
        latest.forEach { row ->
            val label=when(row.type) { "resting_hr" -> "Resting heart rate"; "hrv" -> "HRV (source metric)"; "hrv_sdnn" -> "HRV SDNN"; "sleep_duration" -> "Sleep duration"; else -> "Steps" }
            Text(String.format(Locale.getDefault(),"%s · %.1f %s",label,row.value,row.unit),style=MaterialTheme.typography.titleMedium)
            val date=DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(row.recordedAt)
            Text("$date · ${row.source} · recorded value",style=MaterialTheme.typography.bodySmall)
        }
        Text("Latest available records retain their source date. HRV methods differ; no cross-method comparison or training adjustment is inferred.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=refresh,enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text("Refresh wellness") }
    }
}
