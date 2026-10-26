package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun ActivityPlanLink(state:TrainingState,activity:ActivityRecord,actions:PlanActions) {
    var choosing by remember { mutableStateOf(false) }
    val linked=state.plans.flatMap { p->p.sessions.filter { it.linkedActivityId==activity.id }.map { p to it } }.firstOrNull()
    SignalCard {
        Text("PLANNED & RECORDED",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        if(linked!=null) {
            val (p,s)=linked
            Text(s.title,style=MaterialTheme.typography.titleLarge)
            Text("Scheduled ${s.date} · recorded ${activity.dateLabel}")
            Text("Planned ${s.effectiveMinutes} min · recorded elapsed ${activity.durationLabel}")
            if(s.suggestion?.accepted!=true)s.distanceKm?.let { Text("Planned %.2f km · recorded %s".format(it,activity.distanceLabel)) }
            Text("Manually linked. Distance/time do not establish interval compliance or fitness improvement. Original targets and any manual log are retained.",style=MaterialTheme.typography.bodySmall)
            if(p.engineVersion==ENGINE1_VERSION && p.status in setOf("approved","paused"))OutlinedButton(onClick={actions.link(p.id,p.revision,s.id,null)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Unlink recorded run") }
        } else {
            Text("Connect this genuine source run to a workout you planned. No automatic match or duplicate workout log is created.")
            OutlinedButton(onClick={choosing=true},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Link to planned run") }
        }
    }
    if(choosing) {
        val choices=state.plans.filter { it.status in setOf("approved","paused") && it.engineVersion==ENGINE1_VERSION }.flatMap { p->p.sessions.filter { it.discipline=="run" && it.linkedActivityId==null && it.status !in setOf("missed","skipped") }.map { p to it } }.sortedBy { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(it.second.date,activity.startedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate())) }
        AlertDialog(onDismissRequest={choosing=false},title={Text("Choose the planned run")},text={
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item { Text("Confirm the association by selecting a workout. The source date may differ from its scheduled date; targets are never copied into actual metrics.") }
                if(choices.isEmpty())item { Text("No eligible running sessions. Create and approve a running plan first.") }
                items(choices.size) { index -> val (p,s)=choices[index];OutlinedButton(onClick={actions.link(p.id,p.revision,s.id,activity.id);choosing=false},modifier=Modifier.fillMaxWidth()) { Text("${s.date} · ${s.title} · ${s.effectiveMinutes} planned min") } }
            }
        },confirmButton={TextButton(onClick={choosing=false}) { Text("Cancel") }})
    }
}
