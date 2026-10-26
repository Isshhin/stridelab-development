@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

@Composable internal fun PlanBuilder(state:TrainingState,actions:PlanActions,discipline:String="all",onDone:()->Unit,onSignIn:()->Unit,onPreview:(String,String)->Unit) {
    if(state.account==null) { Screen("Create a plan","TRAINING") { item { SignalCard { Text("A watch is optional.",style=MaterialTheme.typography.headlineSmall);Text("Sign in once to keep plans and logs private to your account. You can then build, approve and manage plans offline.");SignalButton("Sign in",onSignIn) } } };return }
    val baseline=remember { state.plans.firstOrNull { it.policyId==ENGINE1_POLICY }?.request ?: PlanRequest(weeklyKm=historyWeeklyKm(state.activities) ?: 0.0,longestKm=state.activities.filter { DashboardSummary.isRun(it) }.mapNotNull { it.distanceM }.maxOrNull()?.div(1000) ?: 0.0) }
    val initial=remember { when(discipline) { "run"->baseline.copy(runDays=3,strengthDays=0,calDays=0,conditioningDays=0);"str"->baseline.copy(goal="General strength",runDays=0,strengthDays=2,calDays=0,conditioningDays=0,qualityCount=0);"cal"->baseline.copy(goal="Calisthenics foundation",runDays=0,strengthDays=0,calDays=2,conditioningDays=0,qualityCount=0);else->baseline } }
    var encoded by rememberSaveable { mutableStateOf(initial.encode().toString()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val input=PlanRequest.decode(JSONObject(encoded))
    fun edit(p:PlanRequest) { encoded=p.encode().toString() }
    var km by rememberSaveable { mutableStateOf(initial.weeklyKm.toString()) };var longest by rememberSaveable { mutableStateOf(initial.longestKm.toString()) }
    var pace by rememberSaveable { mutableStateOf((initial.planningPace/60).toString()) };var start by rememberSaveable { mutableStateOf(maxOf(initial.start,LocalDate.now()).toString()) }
    var weeks by rememberSaveable { mutableStateOf(initial.weeks.toString()) };var minutes by rememberSaveable { mutableStateOf(initial.minutes.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) };var awaiting by rememberSaveable { mutableStateOf(false) };var beforeId by rememberSaveable { mutableStateOf<String?>(null) };var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.busy,state.plans,state.message) { if(awaiting&&!state.busy) {
        val newest=state.plans.firstOrNull();if(newest!=null&&newest.id!=beforeId) { previewId=newest.id;step=4;awaiting=false } else if(state.message!=null) awaiting=false
    } }
    fun request():PlanRequest=try { input.copy(start=LocalDate.parse(start),weeks=weeks.toInt(),minutes=minutes.toInt(),weeklyKm=km.toDouble(),longestKm=longest.toDouble(),planningPace=pace.toDouble()*60) } catch(e:Exception) { throw PlanConstraint("Enter a valid YYYY-MM-DD date and numeric mileage, pace, weeks and minutes.") }
    var previewWeek by rememberSaveable { mutableIntStateOf(0) }
    val preview=state.plans.find { it.id==previewId }
    if(step==4&&preview!=null) { Screen("Your plan","PREVIEW · NOT APPROVED") {
        item { PlanOverview(preview) }
        item { Text("Review the actual dated workouts before approval. The original prescriptions and engine version are saved with this draft.") }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { TextButton(onClick={previewWeek--},enabled=previewWeek>0) { Text("‹ Previous") };Text("Week ${previewWeek+1} of ${preview.request.weeks}");TextButton(onClick={previewWeek++},enabled=previewWeek<preview.request.weeks-1) { Text("Next ›") } } }
        val weekStart=preview.request.start.plusWeeks(previewWeek.toLong())
        preview.sessions.filter { it.date in weekStart..weekStart.plusDays(6) }.forEach { s -> item(key=s.id) { PlannedWorkoutCard(s) { onPreview(preview.id,s.id) } } }
        item { SignalButton("Approve plan",{actions.approve(preview.id,preview.revision);onDone()},!state.busy);OutlinedButton(onClick={step=2},modifier=Modifier.fillMaxWidth()) { Text("Edit inputs and generate a new draft") };TextButton(onClick=onDone) { Text("Keep draft and return to Training") } }
    };return }
    key(step) { Screen(when(step) {0->"Choose disciplines";1->"Goals & ability";2->"Your schedule";else->"Review your choices"},"CREATE TRAINING PLAN · ${step+1} / 4") {
        item { LinearProgressIndicator(progress={(step+1)/4f},modifier=Modifier.fillMaxWidth());Text("One coordinated plan · Engine 1.0 · no watch required",style=MaterialTheme.typography.bodySmall) }
        if(step==0) {
            item { Text("Pick the disciplines you want to train. All selected disciplines share one calendar.",style=MaterialTheme.typography.bodyLarge) }
            listOf("run","str","cal","cond").forEach { d -> item {
                val frequency=when(d) {"run"->input.runDays;"str"->input.strengthDays;"cal"->input.calDays;else->input.conditioningDays}
                val toggle={ val n=if(frequency>0)0 else if(d=="run")3 else if(d=="cond")1 else 2;edit(when(d){"run"->input.copy(runDays=n,qualityCount=0);"str"->input.copy(strengthDays=n);"cal"->input.copy(calDays=n);else->input.copy(conditioningDays=n)}) }
                Surface(onClick=toggle,shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,border=androidx.compose.foundation.BorderStroke(if(frequency>0)2.dp else 1.dp,if(frequency>0)disciplineColor(d)else MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(disciplineLabel(d),style=MaterialTheme.typography.titleLarge,color=disciplineColor(d));Text(if(d=="run") "Build an easy running habit, distance or controlled quality." else if(d=="str") "Equipment-aware resistance prescriptions." else if(d=="cal") "Current ability, supported variations and skill practice." else "Comfortable low-impact conditioning.",style=MaterialTheme.typography.bodyMedium)}
                        Checkbox(frequency>0,{toggle()})
                    }
                }
            } }
        }
        if(step==1) {
            listOf("run" to input.runDays,"str" to input.strengthDays,"cal" to input.calDays,"cond" to input.conditioningDays).filter { it.second>0 }.forEach { (d,f) -> item { Text(disciplineLabel(d)+" sessions per week",style=MaterialTheme.typography.titleLarge);FrequencyChips(f,if(d=="run")6 else if(d=="cond")3 else 4) { n -> edit(when(d){"run"->input.copy(runDays=n,qualityCount=0);"str"->input.copy(strengthDays=n);"cal"->input.copy(calDays=n);else->input.copy(conditioningDays=n)}) } } }
            item { Text("Primary goal",style=MaterialTheme.typography.titleLarge);if(input.goal !in builderGoals(input))Text("Your previous goal needs a discipline you removed. Choose a goal for the selected disciplines.",color=MaterialTheme.colorScheme.error);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { builderGoals(input).forEach { goal -> SignalChip(input.goal==goal,{edit(input.copy(goal=goal))},label={Text(goal)}) } } }
            item { FormText("Secondary goal notes",input.secondaryGoal,{edit(input.copy(secondaryGoal=it.take(200)))}) }
            item { Text(if(input.runDays>0) "Running experience" else "General training experience",style=MaterialTheme.typography.titleLarge);ChoiceChips(listOf("Starting","Some experience","Experienced"),input.experience) { edit(input.copy(experience=it)) } }
            if(input.runDays>0) {
                item { FormText("Recent weekly running distance (km)",km,{km=it},true);Text(historyWeeklyKm(state.activities)?.let { "Saved-record average over the last 28 days: %.1f km/week. History may be incomplete; edit this value.".format(it) } ?: "No history estimate available. Enter your own distance, or use 0 for a time-based beginner block.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                item { FormText("Longest recent run (km)",longest,{longest=it},true);FormText("Planning pace estimate (min/km)",pace,{pace=it},true);Text("An editable planning assumption, not a recorded pace. Use effort and your available time; a watch is optional.",style=MaterialTheme.typography.bodySmall) }
                item { Text("Quality sessions per week");FrequencyChips(input.qualityCount,2) { edit(input.copy(qualityCount=it)) };Text("Beginners and return-to-running weeks remain easy. Hard sessions require adequate running history and recovery spacing.",style=MaterialTheme.typography.bodySmall) }
            }
            if(input.strengthDays>0) item { Text("Strength experience");ChoiceChips(listOf("Starting","Some experience","Experienced"),input.strengthExperience) { edit(input.copy(strengthExperience=it)) } }
            if(input.calDays>0) {
                item { Text("Current pushing ability");ChoiceChips(listOf("Starting / assisted","Controlled push-up","Comfortable push-up"),input.calLevel) { edit(input.copy(calLevel=it)) } }
                item { Text("Current pulling ability");ChoiceChips(listOf("Starting / assisted","Controlled supported pull","Controlled pull-up"),input.pullLevel) { edit(input.copy(pullLevel=it)) } }
                item { Text("Current core ability");ChoiceChips(listOf("Dead bug","Hollow tuck","Comfortable hollow hold"),input.coreLevel) { edit(input.copy(coreLevel=it)) };Text("Report each movement separately. No skill stage advances automatically by elapsed weeks.",style=MaterialTheme.typography.bodySmall) }
            }
        }
        if(step==2) {
            item { NativeDateField("Start date",start,{start=it});Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={start=LocalDate.now().toString()}) { Text("Today") };OutlinedButton(onClick={start=LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).toString()}) { Text("Next Monday") } } }
            item { FormText("Plan weeks (1–16)",weeks,{weeks=it},true);FormText("Available minutes per session (15–120)",minutes,{minutes=it},true) }
            item { Text("Available training days · ${input.days.size}",style=MaterialTheme.typography.titleLarge);DayChoices(input.days) { d -> edit(input.copy(days=if(d in input.days)input.days-d else input.days+d)) };Text("Requested: ${input.runDays+input.strengthDays+input.calDays+input.conditioningDays} sessions per week. Conflicts will be explained; sessions are not silently dropped.",style=MaterialTheme.typography.bodySmall) }
            if(input.runDays>0) {
                item { Text("One preferred long-run day",style=MaterialTheme.typography.titleLarge);DayChoices(setOf(input.longDay)) { d -> edit(input.copy(longDay=d,days=input.days+d,qualityDays=input.qualityDays-d)) } }
                item { Text("Preferred quality days",style=MaterialTheme.typography.titleLarge);DayChoices(input.qualityDays) { d -> edit(input.copy(qualityDays=if(d in input.qualityDays)input.qualityDays-d else input.qualityDays+d)) } }
            }
            item { Text("Equipment quantities",style=MaterialTheme.typography.titleLarge);Text("Bodyweight/floor options remain available. Choose only verified, suitable equipment and stable supports.",style=MaterialTheme.typography.bodySmall) }
            listOf("db" to "Dumbbells","bands" to "Bands","anchor" to "Secure band anchors","bar" to "Secure overhead bars","support" to "Stable raised supports","bb" to "Barbells","rack" to "Racks with safeties","bench" to "Benches").forEach { (key,label) -> item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,Modifier.weight(1f));OutlinedButton(onClick={edit(input.copy(equipment=input.equipment+(key to maxOf(0,(input.equipment[key]?:0)-1))))}) { Text("−") };Text("${input.equipment[key]?:0}",Modifier.padding(12.dp));OutlinedButton(onClick={edit(input.copy(equipment=input.equipment+(key to minOf(20,(input.equipment[key]?:0)+1))))}) { Text("+") } } } }
            item { Row { Checkbox(input.returning,{edit(input.copy(returning=it))});Text("Returning from a break",Modifier.padding(top=12.dp)) };FormText("Limitations / preferences for review",input.limitations,{edit(input.copy(limitations=it.take(1000)))});FormText("Optional recovery notes",input.recoveryNotes,{edit(input.copy(recoveryNotes=it.take(1000)))}) }
            item { Text("Exclude specific exercises",style=MaterialTheme.typography.titleMedium);Text("Notes are retained for review; they do not automatically interpret an injury. Use exclusions to remove a movement.",style=MaterialTheme.typography.bodySmall);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("sq" to "Bodyweight squat","gs" to "Goblet squat","hg" to "Hip hinge","ip" to "Incline push-up","dr" to "DB row","plank" to "Plank").forEach { (id,label) -> SignalChip(id in input.excluded,{edit(input.copy(excluded=if(id in input.excluded)input.excluded-id else input.excluded+id))},label={Text(label)}) } } }
        }
        if(step==3) item { SignalCard {
            Text(input.goal,style=MaterialTheme.typography.headlineSmall);Text("Running ${input.runDays} · Strength ${input.strengthDays} · Calisthenics ${input.calDays} · Conditioning ${input.conditioningDays}")
            Text("$start · $weeks weeks · up to $minutes min/session");Text("Available: "+input.days.sorted().joinToString { dayName(it) })
            if(input.runDays>0) {Text("Recent: $km km/week · longest $longest km · planning pace $pace min/km");Text("Long run: ${dayName(input.longDay)} · quality: ${input.qualityCount}, ${input.qualityDays.sorted().joinToString { dayName(it) }}")}
            Text("Nothing is approved yet. Generate a saved draft, inspect the prescriptions and calendar, then explicitly approve it.")
        } }
        error?.let { item { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("plan-form-error")) } }
        item { SignalButton(if(step<3) "Continue" else "Generate plan",{
            error=null
            if(step<3) step++ else try { val p=request();if(p.start<LocalDate.now())throw PlanConstraint("Choose today or a future start date.");Engine1.validate(p);beforeId=state.plans.firstOrNull()?.id;awaiting=true;actions.generate(p) } catch(e:PlanConstraint) { error=e.message }
        },!state.busy&&!awaiting)
            if(step>0) TextButton(onClick={step--;error=null},modifier=Modifier.fillMaxWidth()) { Text("Back") }
            TextButton(onClick=onDone,modifier=Modifier.fillMaxWidth()) { Text("Close builder") }
        }
    } }
}
@Composable internal fun FormText(label:String,value:String,onChange:(String)->Unit,numeric:Boolean=false) { OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text)) }
@Composable internal fun FrequencyChips(value:Int,max:Int,onSelect:(Int)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { (0..max).forEach { n -> SignalChip(value==n,{onSelect(n)},label={Text(if(n==0) "Off" else "$n / week")}) } } }
@Composable internal fun ChoiceChips(values:List<String>,selected:Int,onSelect:(Int)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEachIndexed { i,label -> SignalChip(i==selected,{onSelect(i)},label={Text(label)}) } } }
@Composable internal fun DayChoices(selected:Set<Int>,onSelect:(Int)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { (1..7).forEach { d -> SignalChip(d in selected,{onSelect(d)},label={Text(dayName(d))}) } } }
internal fun dayName(day:Int)=java.time.DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault())
internal fun historyWeeklyKm(history:List<ActivityRecord>,now:Instant=Instant.now()):Double? {
    val rows=history.distinctBy { it.id }.filter { DashboardSummary.isRun(it)&&it.startedAt<=now&&it.startedAt>=now.minusSeconds(28*86400L)&&it.distanceM!=null }
    return rows.takeIf { it.size>=3 }?.sumOf { it.distanceM!! }?.div(4000.0)
}

@Composable internal fun NativeDateField(label:String,value:String,onChange:(String)->Unit,enabled:Boolean=true) {
    val context=androidx.compose.ui.platform.LocalContext.current
    Column {
        OutlinedTextField(value,onChange,label={Text("$label (YYYY-MM-DD)")},enabled=enabled,modifier=Modifier.fillMaxWidth(),singleLine=true)
        if(enabled) TextButton(onClick={
            val date=runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
            android.app.DatePickerDialog(context,{_,y,m,d->onChange(LocalDate.of(y,m+1,d).toString())},date.year,date.monthValue-1,date.dayOfMonth).show()
        }) { Text("Choose date") }
    }
}

private fun builderGoals(p:PlanRequest)=ENGINE1_GOALS.filter { goal -> when(goal) {
    "General fitness"->true
    "Aerobic foundation","Improve 5K","Improve 10K","Half marathon","Marathon"->p.runDays>0
    "General strength","Muscle development","Strength endurance"->p.strengthDays>0
    "Calisthenics foundation","Pull-up progression","Push-up progression","Core strength and control","Handstand foundations"->p.calDays>0
    "Conditioning"->p.conditioningDays>0
    else->false
} }
