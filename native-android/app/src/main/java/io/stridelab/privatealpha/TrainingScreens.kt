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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun PlanOverview(plan:PlanSnapshot) {
    var expanded by rememberSaveable(plan.id) { mutableStateOf(false) }
    val firstWeek=plan.sessions.filter { it.date in plan.request.start..plan.request.start.plusDays(6) }
    SignalCard {
        Text(plan.status.uppercase()+" · ENGINE ${plan.engineVersion}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Text(plan.name ?: plan.request.goal,style=MaterialTheme.typography.headlineSmall)
        Text(if(!Engine1Engines.canManage(plan))"Saved engine/policy · read-only here" else when(plan.policyId) {ENGINE15_INDEPENDENT_RUNNING_POLICY->"Engine 1.5 independent running plan";ENGINE15_INDEPENDENT_STRENGTH_POLICY->"Engine 1.5 independent strength plan";ENGINE15_INDEPENDENT_CALISTHENICS_POLICY->"Engine 1.5 independent calisthenics plan";ENGINE15_INDEPENDENT_CONDITIONING_POLICY->"Engine 1.5 independent conditioning plan";ENGINE15_INDEPENDENT_COMBINED_POLICY->"Engine 1.5 independent combined plan";ENGINE15_POLICY->"Engine 1.5 training plan";ENGINE15_MULTI_POLICY->"Engine 1.5 multi-session scheduling";ENGINE15_INTERFERENCE_POLICY->"Engine 1.5 interference-aware scheduling";CoordinatedEngine1.policyId->"Coordinated discipline goals";MultiSessionEngine1.policyId->"Multi-session scheduling";SessionTemplate.POLICY_ID->"User-reviewed session targets";else->"Verified baseline fallback"},style=MaterialTheme.typography.bodySmall)
        Text("${plan.request.start} – ${plan.request.start.plusWeeks(plan.request.weeks.toLong()).minusDays(1)} · ${plan.request.weeks} weeks",style=MaterialTheme.typography.bodyMedium)
        if(PlanScheduleValidation.isMultiPolicy(plan.policyId)) {
            val requested=plan.request.runDays+plan.request.strengthDays+plan.request.calDays+plan.request.conditioningDays
            Text("$requested sessions/week · ${firstWeek.map { it.date }.distinct().size} scheduled training days/week · ${plan.request.days.size} available days",style=MaterialTheme.typography.bodyMedium)
            Text("${plan.sessions.size} total prescribed sessions · ${plan.sessions.count { it.status=="completed" }} completed · ${plan.request.dailyMinutes} min daily budget",style=MaterialTheme.typography.bodyMedium)
        } else Text("${plan.sessions.size} prescribed sessions · ${plan.sessions.count { it.status=="completed" }} completed",style=MaterialTheme.typography.bodyMedium)
        Text(if (LocalPlanOwnership.isLocalPlanOwner(plan.owner))
            "Device-only plan · sign-in does not automatically move it to the cloud."
            else "Account-private · stored on this device · not automatically cloud-synced",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Hide plan explanation" else "Why this plan") }
        if(expanded) plan.explanations.forEach { Text(it,style=MaterialTheme.typography.bodyMedium) }
    }
}
@Composable internal fun PlannedWorkoutCard(s:PlannedSession,hasDraft:Boolean=false,compact:Boolean=false,onClick:()->Unit) {
    val color=tracksideDisciplineColor(s.discipline)
    if (compact) {
        TracksideParityWorkoutCard(s, hasDraft, onClick)
        return
    }

    Surface(onClick=onClick,shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().testTag("workout-${s.id}")) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(18.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("${disciplineLabel(s.discipline).uppercase()} · ${if(s.status=="completed") if(s.linkedActivityId!=null) "LINKED SOURCE RUN" else s.log?.minutes?.let { "${it.toInt()} RECORDED MIN" } ?: "DURATION UNRECORDED" else "${s.effectiveMinutes} PLANNED MIN"} · ${s.status.uppercase()}",style=MaterialTheme.typography.labelMedium,color=color)
                Text(s.effectiveTitle,style=MaterialTheme.typography.headlineSmall)
                if(hasDraft)Text("Saved entry draft · resume without marking complete",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
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
@Composable internal fun TrainingScreen(state:TrainingState,actions:PlanActions,onBuild:()->Unit,onOpen:(String,String)->Unit,onSignIn:()->Unit,onManage:(String,LocalDate)->Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) };var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) };var monthView by rememberSaveable { mutableStateOf(false) };var agendaView by rememberSaveable { mutableStateOf(false) }
    var openedResearchReviewId by rememberSaveable { mutableStateOf<String?>(null) }
    val plans=state.plans.filter { it.status!="archived" }
    val plan=plans.find { it.id==selectedId } ?: plans.find { it.status=="approved" } ?: plans.firstOrNull()
    // Keep the selected day when returning from Workout or Manage. Switch/reset only
    // when choosing a different plan, or when a stored day leaves the plan interval.
    var selectionPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(plan?.id) {
        if(plan!=null) {
            val first=plan.request.start
            val last=first.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
            val existing=runCatching { LocalDate.parse(selectedDate) }.getOrNull()
            val chosen=if(selectionPlanId!=plan.id) LocalDate.now() else existing ?: LocalDate.now()
            selectedDate=chosen.coerceIn(first,last).toString()
            selectionPlanId=plan.id
        }
    }
    val date=LocalDate.parse(selectedDate).let { stored ->
        plan?.let { stored.coerceIn(it.request.start,it.request.start.plusWeeks(it.request.weeks.toLong()).minusDays(1)) } ?: stored
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Screen(
                "Training", "YOUR SHARED CALENDAR", compactTitle = true, dense = true,
                titleBadge = if (plan?.status == "draft") "DRAFT" else null
            ) {
                item {
                    TracksideParityTrainingControls(
                        agenda = agendaView && !monthView,
                        draft = plan?.status == "draft",
                        hasPlan = plan != null,
                        onDay = { agendaView = false; monthView = false },
                        onAgenda = { agendaView = true; monthView = false },
                        onManage = { plan?.let { onManage(it.id, date) } },
                        plan = plan,
                        selected = date,
                        onSelectWeek = { selectedDate = it.toString() }
                    )
                }
                if(plan==null) item { SignalCard { Text("Build your training around your life.",style=MaterialTheme.typography.headlineMedium);Text("Running, strength, calisthenics and conditioning share one calendar. Your plan stays on this device when signed out.");SignalButton("Create a plan",onBuild) } }
                if(state.researchDrafts.isNotEmpty()) item {
                    SignalCard {
                        Text("SAVED RESEARCH REVIEWS · NOT PLANS",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        Text("These mileage comparisons remain non-executable and never enter your workout calendar.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        state.researchDrafts.forEach { review ->
                            TextButton(onClick={openedResearchReviewId=review.id},modifier=Modifier.fillMaxWidth().testTag("training-research-review-${review.id}")) {
                                Text("Open comparison · ${review.createdAt.toString().take(10)}")
                            }
                        }
                        state.researchDrafts.firstOrNull { it.id==openedResearchReviewId }?.let {
                            IndependentResearchReadOnlyReview(it,Modifier.testTag("training-research-reopened"))
                        }
                    }
                }
                if(plans.size>1) item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { plans.forEach { p -> SignalChip(p.id==plan?.id,{selectedId=p.id},label={Text((p.name ?: p.request.goal)+" · "+p.status)}) } } }
                if(plan!=null) {
                    item { Engine15CoachReviewCard(plan,actions,state.busy,state.message) }
                    item {
                        Column(Modifier.testTag("training-calendar")) {
                            TrainingCalendar(
                                plan, date, monthView,
                                showWeekNavigation = plan.status != "draft"
                            ) { selectedDate = it.toString() }
                        }
                    }
                    if(agendaView && !monthView) {
                        val rows=tracksideTrainingWeek(plan,date)
                        if(rows.all { it.sessions.isEmpty() }) item { SignalCard { Text("No sessions scheduled for this week",style=MaterialTheme.typography.titleLarge) } }
                        val agendaRows = rows.filter { it.sessions.isNotEmpty() }
                        if (agendaRows.isNotEmpty()) {
                            item(key = "agenda-week-${agendaRows.first().date}") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    agendaRows.forEach { day ->
                                        TracksideParityAgendaDay(
                                            day = day,
                                            hasWorkoutDraft = { sessionId ->
                                                state.workoutDrafts.containsKey(plan.id to sessionId)
                                            },
                                            onOpen = { sessionId -> onOpen(plan.id,sessionId) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item { Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                            style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        val day=plan.sessions.filter { it.date==date && it.status!="unassigned" }.map(plan::cardPresentation)
                        if(day.isEmpty()) item { SignalCard { Text("No workout scheduled",style=MaterialTheme.typography.titleLarge);Text("This calendar date has no prescribed session. No workout or completion is assumed.") } }
                        day.forEach { session -> item { PlannedWorkoutCard(session,state.workoutDrafts.containsKey(plan.id to session.id),compact=true) { onOpen(plan.id,session.id) } } }
                    }
                    item { PlanOverview(plan) }
                    if(Engine15MileageTargetIntegrity.review(plan)?.requiresFoundationDecision==true &&
                        !plan.request.foundationAcknowledged) {
                        item { TracksideTrainingTargetWarning(plan) }
                    }
                    item { PlanFeedbackForm(plan,actions) }
                }
                if(state.plans.isNotEmpty() || state.researchDrafts.isNotEmpty())
                    item { NativePlanExport(state.plans,state.researchDrafts) }
                if(state.account!=null || state.localPlanOwner!=null) item { NativePlanImport(state,actions) }
                if(plan!=null) item { OutlinedButton(onClick=onBuild,modifier=Modifier.fillMaxWidth()) { Text("Create / edit a reviewed draft") } }
                if(plan?.status in setOf("draft","paused")) item { Spacer(Modifier.height(96.dp)) }
            }
        }
        if(plan != null && plan.status in setOf("draft","paused")) {
            TracksideParityDraftApprovalDock(
                plan,
                enabled = !state.busy && Engine1Engines.canManage(plan) && plan.sessions.none { it.status == "unassigned" },
                onApprove = { actions.approve(plan.id,plan.revision) },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
            // The floating navigation pill is an overlay, not Scaffold's
            // bottom bar. Reserve the same clearance used by Screen lists
            // so the draft approval action remains fully above the pill.
            if (LocalFloatingDockVisible.current) {
                Spacer(Modifier.height(110.dp))
            }
        }
    }
}
@Composable internal fun TrainingCalendar(
    plan:PlanSnapshot, selected:LocalDate, month:Boolean,
    showWeekNavigation:Boolean = true, onSelect:(LocalDate)->Unit
) {
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
        (0 until 6).forEach { row -> Row(Modifier.fillMaxWidth()) { (0 until 7).forEach { col -> val d=gridStart.plusDays((row*7+col).toLong());DayCell(d,d==selected,plan.sessions.filter { it.date==d && it.status!="unassigned" }.map(plan::cardPresentation),d in first..last && YearMonth.from(d)==ym,{onSelect(d)},Modifier.weight(1f),showWeekday=false) } } }
    } else {
        TracksideTrainingWeekChart(
            plan, selected, showNavigation = showWeekNavigation, onSelect = onSelect
        )
    }
}
@Composable private fun DayCell(date:LocalDate,selected:Boolean,sessions:List<PlannedSession>,enabled:Boolean,onClick:()->Unit,modifier:Modifier,showWeekday:Boolean=true) {
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(18.dp),color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,modifier=modifier.heightIn(min=64.dp).padding(1.dp).testTag("calendar-day-$date").semantics { contentDescription="$date, ${sessions.joinToString { it.title+", "+it.status }.ifEmpty { "no scheduled workout" }}" }) {
        Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
            if(showWeekday) Text(dayName(date.dayOfWeek.value).take(2),style=MaterialTheme.typography.labelSmall,color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${date.dayOfMonth}",style=MaterialTheme.typography.titleMedium,color=if(!enabled)MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f) else if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            if(sessions.isNotEmpty()) {
                Box(Modifier.size(7.dp).background(if(sessions.all { it.status=="completed" }) MaterialTheme.colorScheme.primary else tracksideDisciplineColor(sessions.first().discipline),RoundedCornerShape(4.dp)))
                if(sessions.size>1) Text("${sessions.size} workouts",style=MaterialTheme.typography.labelSmall,color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable internal fun PlanFeedbackForm(plan:PlanSnapshot,actions:PlanActions,sessionId:String?=null,embedded:Boolean=false) {
    var pendingCount by rememberSaveable(plan.id,sessionId) { mutableStateOf<Int?>(null) }
    var category by rememberSaveable(plan.id,sessionId) { mutableStateOf("Plan quality") };var text by rememberSaveable(plan.id,sessionId) { mutableStateOf("") }
    LaunchedEffect(plan.feedback.size) { if(pendingCount!=null && plan.feedback.size>pendingCount!!) { text="";pendingCount=null } }
    val fields: @Composable ColumnScope.() -> Unit = {
        Text("PRIVATE TESTING FEEDBACK",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Plan quality","Inappropriate prescription","Workout quality","Usability").forEach { c -> SignalChip(category==c,{category=c},label={Text(c)}) } }
        FormText("What worked, or what should change?",text,{text=it.take(2000)})
        Text("Saved privately on this device and included in your manual plan export. Nothing is sent automatically.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={pendingCount=plan.feedback.size;actions.feedback(plan.id,plan.revision,category,text,sessionId)},enabled=text.trim().length>=3,modifier=Modifier.fillMaxWidth()) { Text("Save feedback") }
        Text("${plan.feedback.size} saved feedback records",style=MaterialTheme.typography.bodySmall)
    }
    if(embedded) {
        Column(Modifier.fillMaxWidth(),
            verticalArrangement=Arrangement.spacedBy(8.dp)) { fields() }
    } else {
        SignalCard { fields() }
    }
}
@Composable internal fun NativePlanExport(
    plans:List<PlanSnapshot>,
    researchReviews:List<IndependentResearchDraft> = emptyList()
) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();var result by remember { mutableStateOf<String?>(null) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null)scope.launch {
        result=runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out -> val owner=plans.firstOrNull()?.owner ?: researchReviews.firstOrNull()?.owner ?: error("No saved records");out.write(PlanBackup.encode(plans,researchReviews,owner).toByteArray(Charsets.UTF_8)) } ?: error("No output stream") };"Plan and research-review backup exported" }.getOrDefault("Export could not be completed. Your saved records remain on this device.")
    } }
    OutlinedButton(onClick={launcher.launch("StrideLab-backup-${LocalDate.now()}.json")},modifier=Modifier.fillMaxWidth(),enabled=plans.isNotEmpty() || researchReviews.isNotEmpty()) { Text("Export plans and research reviews") }
    result?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
}

@Composable internal fun NativePlanImport(state:TrainingState,actions:PlanActions) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var raw by remember { mutableStateOf<String?>(null) };var description by remember { mutableStateOf<String?>(null) };var error by remember { mutableStateOf<String?>(null) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)scope.launch {
        error=null
        try {
            val text=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { input -> val bytes=readPlanBackupBytes(input);if(bytes.size>RecordPolicy.MAX_PAYLOAD_BYTES)throw PlanConstraint("Choose a plan backup smaller than 2 MB.");String(bytes,Charsets.UTF_8) } ?: throw PlanConstraint("The backup could not be opened.") }
            val (backup,additions,reviewAdditions)=withContext(Dispatchers.Default) {
                val bundle=PlanBackup.readBundle(text,state.account ?: state.localPlanOwner ?: "")
                Triple(bundle,PlanBackup.additions(bundle.plans,state.plans),PlanBackup.researchAdditions(bundle.researchReviews,state.researchDrafts))
            }
            description="${backup.plans.size} plans and ${backup.researchReviews.size} non-executable research reviews in this profile's backup; ${additions.size} new plans and ${reviewAdditions.size} new reviews will be restored. Existing records are retained. Local-only backups require the original device identity and are not imported into another account."
            raw=text
        } catch(e:Exception) { error=if(e is PlanConstraint)e.message else "The backup could not be verified. Existing records are unchanged." }
    } }
    OutlinedButton(onClick={launcher.launch(arrayOf("application/json","text/plain"))},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Restore a plan backup") }
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    if(raw!=null) AlertDialog(onDismissRequest={raw=null},title={Text("Review backup restore")},text={Text(description ?: "")},confirmButton={TextButton(onClick={raw?.let(actions.restore);raw=null}) { Text("Restore new records") }},dismissButton={TextButton(onClick={raw=null}) { Text("Cancel") }})
}

private fun readPlanBackupBytes(input:java.io.InputStream):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(out.size()<=RecordPolicy.MAX_PAYLOAD_BYTES) { val count=input.read(buffer,0,minOf(buffer.size,RecordPolicy.MAX_PAYLOAD_BYTES+1-out.size()));if(count<0)break;out.write(buffer,0,count) }
    return out.toByteArray()
}
