@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.stridelab.privatealpha

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun PlanOverview(plan:PlanSnapshot) {
    var expanded by rememberSaveable(plan.id) { mutableStateOf(false) }
    val firstWeek=plan.sessions.filter { it.date in plan.request.start..plan.request.start.plusDays(6) }
    SignalCard {
        Text(plan.status.uppercase()+" · ENGINE ${plan.engineVersion}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Text(plan.name ?: plan.request.goal,style=MaterialTheme.typography.headlineSmall)
        Text(if(!Engine1Engines.canManage(plan))"Saved engine/policy · read-only here" else when(plan.policyId) {CoordinatedEngine1.policyId->"Coordinated discipline goals";MultiSessionEngine1.policyId->"Multi-session scheduling";SessionTemplate.POLICY_ID->"User-reviewed session targets";else->"Verified baseline fallback"},style=MaterialTheme.typography.bodySmall)
        Text("${plan.request.start} – ${plan.request.start.plusWeeks(plan.request.weeks.toLong()).minusDays(1)} · ${plan.request.weeks} weeks",style=MaterialTheme.typography.bodyMedium)
        if(plan.policyId==MULTI_SESSION_POLICY) {
            val requested=plan.request.runDays+plan.request.strengthDays+plan.request.calDays+plan.request.conditioningDays
            Text("$requested sessions/week · ${firstWeek.map { it.date }.distinct().size} scheduled training days/week · ${plan.request.days.size} available days",style=MaterialTheme.typography.bodyMedium)
            Text("${plan.sessions.size} total prescribed sessions · ${plan.sessions.count { it.status=="completed" }} completed · ${plan.request.dailyMinutes} min daily budget",style=MaterialTheme.typography.bodyMedium)
        } else Text("${plan.sessions.size} prescribed sessions · ${plan.sessions.count { it.status=="completed" }} completed",style=MaterialTheme.typography.bodyMedium)
        Text("Account-private · stored on this device · not cloud-synced",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Hide plan explanation" else "Why this plan") }
        if(expanded) plan.explanations.forEach { Text(it,style=MaterialTheme.typography.bodyMedium) }
    }
}
@Composable internal fun PlannedWorkoutCard(s:PlannedSession,onClick:()->Unit) {
    val color=disciplineColor(s.discipline)
    Surface(onClick=onClick,shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().testTag("workout-${s.id}")) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(18.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("${disciplineLabel(s.discipline).uppercase()} · ${if(s.status=="completed") if(s.linkedActivityId!=null) "LINKED SOURCE RUN" else s.log?.minutes?.let { "${it.toInt()} RECORDED MIN" } ?: "DURATION UNRECORDED" else "${s.effectiveMinutes} PLANNED MIN"} · ${s.status.uppercase()}",style=MaterialTheme.typography.labelMedium,color=color)
                Text(s.effectiveTitle,style=MaterialTheme.typography.headlineSmall)
                Text(if(s.status=="completed"&&s.log!=null) (s.log.performedOn?.let { "Performed $it · logged " } ?: "Actual date unrecorded · logged ")+s.log.at.atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("EEE, d MMM"))+" · scheduled ${s.date}" else s.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(s.distanceKm!=null && s.suggestion?.accepted!=true) Text("Target %.1f km · planned, not recorded".format(s.distanceKm),style=MaterialTheme.typography.bodyMedium)
                if(s.effectiveExercises.isNotEmpty()) s.effectiveExercises.take(3).forEach { e -> Text("${e.name} · ${e.sets} × ${e.reps?.let { "$it reps" } ?: "${e.seconds} sec"}",style=MaterialTheme.typography.bodyMedium) }
                else s.steps.take(2).forEach { Text(it,style=MaterialTheme.typography.bodyMedium) }
                if(s.suggestion!=null&&!s.suggestion.accepted) Text("Recovery suggestion awaiting your review",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                Text(if(s.status=="completed") "Open saved workout →" else "Preview, start or manage →",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable internal fun TrainingScreen(state:TrainingState,actions:PlanActions,onBuild:()->Unit,onOpen:(String,String)->Unit,onSignIn:()->Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) };var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) };var monthView by rememberSaveable { mutableStateOf(false) };var planMenu by rememberSaveable { mutableStateOf(false) }
    val plans=state.plans.filter { it.status!="archived" }
    val plan=plans.find { it.id==selectedId } ?: plans.find { it.status=="approved" } ?: plans.firstOrNull()
    LaunchedEffect(plan?.id) { if(plan!=null) { val end=plan.request.start.plusWeeks(plan.request.weeks.toLong()).minusDays(1);selectedDate=LocalDate.now().coerceIn(plan.request.start,end).toString() } }
    val date=LocalDate.parse(selectedDate)
    Screen("Training","YOUR SHARED CALENDAR") {
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick={monthView=!monthView},modifier=Modifier.weight(1f)) { Text(if(monthView) "Week" else "Month") }
            OutlinedButton(onClick={planMenu=true},enabled=plan!=null,modifier=Modifier.weight(1f)) { Text("Plan") }
        } }
        if(state.account==null) item { SignalCard { Text("Plan without a watch",style=MaterialTheme.typography.headlineSmall);Text("Sign in once to create private, account-isolated plans. Generation, calendar and logging then work offline.");SignalButton("Sign in",onSignIn) } }
        else if(plan==null) item { SignalCard { Text("Build your training around your life.",style=MaterialTheme.typography.headlineMedium);Text("Running, strength, calisthenics and conditioning share one calendar. Choose your goal, ability, days and equipment. Review real prescriptions before approving.");SignalButton("Create a plan",onBuild) } }
        if(plans.size>1) item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { plans.forEach { p -> SignalChip(p.id==plan?.id,{selectedId=p.id},label={Text((p.name ?: p.request.goal)+" · "+p.status)}) } } }
        if(plan!=null) {
            item { Column(Modifier.testTag("training-calendar")) { TrainingCalendar(plan,date,monthView) { selectedDate=it.toString() } } }
            item { PlanOverview(plan) }
            item { Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            val day=plan.sessions.filter { it.date==date }
            if(day.isEmpty()) item { SignalCard { Text("No workout scheduled",style=MaterialTheme.typography.titleLarge);Text("This calendar date has no prescribed session. No workout or completion is assumed.") } }
            day.forEach { s -> item { PlannedWorkoutCard(s) { onOpen(plan.id,s.id) } } }
            if(plan.status=="draft"||plan.status=="paused") item { SignalButton(if(plan.status=="draft") "Approve this plan" else "Resume this plan",{actions.approve(plan.id,plan.revision)},!state.busy&&Engine1Engines.canManage(plan)) }
            item { PlanFeedbackForm(plan,actions) }
            item { NativePlanExport(state.plans) }
        }
        if(state.account!=null) item { NativePlanImport(state,actions) }
        if(state.account!=null && plan!=null) item { OutlinedButton(onClick=onBuild,modifier=Modifier.fillMaxWidth()) { Text("Create / edit a reviewed draft") } }
    }
    if(planMenu&&plan!=null) ModalBottomSheet(onDismissRequest={planMenu=false},containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(plan.name ?: plan.request.goal,style=MaterialTheme.typography.headlineSmall)
        Text(if(!Engine1Engines.canManage(plan))"Saved engine/policy · read-only here" else when(plan.policyId) {CoordinatedEngine1.policyId->"Coordinated discipline goals";MultiSessionEngine1.policyId->"Multi-session scheduling";SessionTemplate.POLICY_ID->"User-reviewed session targets";else->"Verified baseline fallback"},style=MaterialTheme.typography.bodySmall);Text("Editing creates a new reviewed draft. Approved prescriptions and previous logs are retained.")
            SignalButton("Edit inputs / create draft",{planMenu=false;onBuild()},Engine1Engines.canManage(plan))
            if(plan.status=="approved") OutlinedButton(onClick={actions.pause(plan.id,plan.revision);planMenu=false},enabled=Engine1Engines.canManage(plan),modifier=Modifier.fillMaxWidth()) { Text("Pause plan") }
            OutlinedButton(onClick={actions.archive(plan.id,plan.revision);planMenu=false},enabled=Engine1Engines.canManage(plan),modifier=Modifier.fillMaxWidth()) { Text("Archive plan · keep records") }
            TextButton(onClick={planMenu=false}) { Text("Close") }
        }
    }
}
@Composable internal fun TrainingCalendar(plan:PlanSnapshot,selected:LocalDate,month:Boolean,onSelect:(LocalDate)->Unit) {
    val first=plan.request.start;val last=first.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
    if(month) {
        val ym=YearMonth.from(selected)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={onSelect(maxOf(first,ym.minusMonths(1).atDay(1)))},enabled=ym.atDay(1)>first) { Text("‹") }
            Text(ym.month.toString()+" "+ym.year,style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={onSelect(minOf(last,ym.plusMonths(1).atDay(1)))},enabled=ym.atEndOfMonth()<last) { Text("›") }
        }
        Row { (1..7).forEach { d -> Text(dayName(d).take(2),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall) } }
        val gridStart=ym.atDay(1).minusDays((ym.atDay(1).dayOfWeek.value-1).toLong())
        (0 until 6).forEach { row -> Row(Modifier.fillMaxWidth()) { (0 until 7).forEach { col -> val d=gridStart.plusDays((row*7+col).toLong());DayCell(d,d==selected,plan.sessions.filter { it.date==d },d in first..last && YearMonth.from(d)==ym,{onSelect(d)},Modifier.weight(1f),showWeekday=false) } } }
    } else {
        val week=ChronoUnit.DAYS.between(first,selected).toInt().coerceAtLeast(0)/7
        val start=first.plusWeeks(week.toLong())
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={onSelect(maxOf(first,start.minusWeeks(1)))},enabled=start>first) { Text("‹") }
            Text("Week ${week+1} of ${plan.request.weeks}",style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={onSelect(minOf(last,start.plusWeeks(1)))},enabled=start.plusWeeks(1)<=last) { Text("›") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)) { (0L..6L).forEach { i -> val d=start.plusDays(i);DayCell(d,d==selected,plan.sessions.filter { it.date==d },d<=last,{onSelect(d)},Modifier.width(46.dp)) } }
        val sessions=plan.sessions.filter { it.date in start..start.plusDays(6) }
        Text("${sessions.size} sessions · ${sessions.count { it.status=="completed" }} completed · ${sessions.sumOf { it.effectiveMinutes }} planned min",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun DayCell(date:LocalDate,selected:Boolean,sessions:List<PlannedSession>,enabled:Boolean,onClick:()->Unit,modifier:Modifier,showWeekday:Boolean=true) {
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(18.dp),color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,modifier=modifier.heightIn(min=64.dp).padding(1.dp).testTag("calendar-day-$date").semantics { contentDescription="$date, ${sessions.joinToString { it.title+", "+it.status }.ifEmpty { "no scheduled workout" }}" }) {
        Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
            if(showWeekday) Text(dayName(date.dayOfWeek.value).take(2),style=MaterialTheme.typography.labelSmall,color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${date.dayOfMonth}",style=MaterialTheme.typography.titleMedium,color=if(!enabled)MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f) else if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            if(sessions.isNotEmpty()) {
                Box(Modifier.size(7.dp).background(if(sessions.all { it.status=="completed" }) MaterialTheme.colorScheme.primary else disciplineColor(sessions.first().discipline),RoundedCornerShape(4.dp)))
                if(sessions.size>1) Text("${sessions.size} workouts",style=MaterialTheme.typography.labelSmall,color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable internal fun PlanFeedbackForm(plan:PlanSnapshot,actions:PlanActions,sessionId:String?=null) {
    var pendingCount by rememberSaveable(plan.id,sessionId) { mutableStateOf<Int?>(null) }
    var category by rememberSaveable(plan.id,sessionId) { mutableStateOf("Plan quality") };var text by rememberSaveable(plan.id,sessionId) { mutableStateOf("") }
    LaunchedEffect(plan.feedback.size) { if(pendingCount!=null && plan.feedback.size>pendingCount!!) { text="";pendingCount=null } }
    SignalCard {
        Text("PRIVATE TESTING FEEDBACK",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Plan quality","Inappropriate prescription","Workout quality","Usability").forEach { c -> SignalChip(category==c,{category=c},label={Text(c)}) } }
        FormText("What worked, or what should change?",text,{text=it.take(2000)})
        Text("Saved privately on this device and included in your manual plan export. Nothing is sent automatically.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={pendingCount=plan.feedback.size;actions.feedback(plan.id,plan.revision,category,text,sessionId)},enabled=text.trim().length>=3,modifier=Modifier.fillMaxWidth()) { Text("Save feedback") }
        Text("${plan.feedback.size} saved feedback records",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable internal fun NativePlanExport(plans:List<PlanSnapshot>) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();var result by remember { mutableStateOf<String?>(null) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null)scope.launch {
        result=runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out -> val bundle=JSONObject().put("schemaVersion",1).put("plans",JSONArray(plans.map { it.encode() }));out.write(bundle.toString(2).toByteArray(Charsets.UTF_8)) } ?: error("No output stream") };"Plan backup exported" }.getOrDefault("Export could not be completed. Your saved plans remain on this device.")
    } }
    OutlinedButton(onClick={launcher.launch("StrideLab-plans-${LocalDate.now()}.json")},modifier=Modifier.fillMaxWidth(),enabled=plans.isNotEmpty()) { Text("Export plans, logs & feedback") }
    result?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
}

@Composable internal fun HomePlanHero(plan:PlanSnapshot,onOpen:(String)->Unit) {
    val next=plan.sessions.filter { it.date>=LocalDate.now() && it.status in setOf("planned","in_progress") }.minByOrNull { it.date }
    if(next!=null) {
        Text(if(next.date==LocalDate.now()) "TODAY" else "UP NEXT",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        PlannedWorkoutCard(next) { onOpen(next.id) }
        SignalCard { Text("COACH",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Text(if(next.suggestion!=null&&!next.suggestion.accepted) "Your recent missed-workout reason prompted a recovery suggestion. Review the shorter alternative or choose rest; there is no extra catch-up volume." else "Your next session is ${next.title.lowercase()} on ${next.date}. ${next.reason} Use the planned prescription as a starting point and record what you actually complete.",style=MaterialTheme.typography.bodyLarge)
        }
    }
    val start=LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val week=plan.sessions.filter { it.date in start..start.plusDays(6) }
    if(week.isNotEmpty()) SignalCard { Text("THIS WEEK",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${week.count { it.status=="completed" }} of ${week.size} sessions completed",style=MaterialTheme.typography.titleLarge);LinearProgressIndicator(progress={week.count { it.status=="completed" }.toFloat()/week.size},modifier=Modifier.fillMaxWidth());Text(week.groupingBy { disciplineLabel(it.discipline) }.eachCount().entries.joinToString(" · ") { "${it.value} ${it.key}" },style=MaterialTheme.typography.bodyMedium) }
}

@Composable internal fun NativePlanImport(state:TrainingState,actions:PlanActions) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var raw by remember { mutableStateOf<String?>(null) };var description by remember { mutableStateOf<String?>(null) };var error by remember { mutableStateOf<String?>(null) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)scope.launch {
        error=null
        try {
            val text=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { input -> val bytes=readPlanBackupBytes(input);if(bytes.size>RecordPolicy.MAX_PAYLOAD_BYTES)throw PlanConstraint("Choose a plan backup smaller than 2 MB.");String(bytes,Charsets.UTF_8) } ?: throw PlanConstraint("The backup could not be opened.") }
            val (plans,additions)=withContext(Dispatchers.Default) { val rows=PlanBackup.read(text,state.account ?: "");rows to PlanBackup.additions(rows,state.plans) }
            description="${plans.size} plans in this account's backup; ${additions.size} new plans will be restored. Existing plan IDs are skipped, never overwritten. Approval states, original prescriptions, logs and feedback stay attached to their engine versions."
            raw=text
        } catch(e:Exception) { error=if(e is PlanConstraint)e.message else "The backup could not be verified. Existing records are unchanged." }
    } }
    OutlinedButton(onClick={launcher.launch(arrayOf("application/json","text/plain"))},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Restore a plan backup") }
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    if(raw!=null) AlertDialog(onDismissRequest={raw=null},title={Text("Review backup restore")},text={Text(description ?: "")},confirmButton={TextButton(onClick={raw?.let(actions.restore);raw=null}) { Text("Restore new plans") }},dismissButton={TextButton(onClick={raw=null}) { Text("Cancel") }})
}

private fun readPlanBackupBytes(input:java.io.InputStream):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(out.size()<=RecordPolicy.MAX_PAYLOAD_BYTES) { val count=input.read(buffer,0,minOf(buffer.size,RecordPolicy.MAX_PAYLOAD_BYTES+1-out.size()));if(count<0)break;out.write(buffer,0,count) }
    return out.toByteArray()
}
