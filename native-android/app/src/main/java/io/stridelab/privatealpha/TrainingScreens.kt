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
        Text(if(!Engine1Engines.canManage(plan))"Saved engine/policy · read-only here" else when(plan.policyId) {ENGINE15_INDEPENDENT_RUNNING_POLICY->"Engine 1.5 independent running plan";ENGINE15_INDEPENDENT_STRENGTH_POLICY->"Engine 1.5 independent strength plan";ENGINE15_INDEPENDENT_CALISTHENICS_POLICY->"Engine 1.5 independent calisthenics plan";ENGINE15_INDEPENDENT_CONDITIONING_POLICY->"Engine 1.5 independent conditioning plan";ENGINE15_INDEPENDENT_COMBINED_POLICY->"Engine 1.5 independent combined plan";SessionTemplate.POLICY_ID->"User-reviewed session targets";else->"Saved legacy plan"},style=MaterialTheme.typography.bodySmall)
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
                else s.steps.take(2).forEach { Text(formatStageForCard(it),style=MaterialTheme.typography.bodyMedium) }
                if(s.suggestion!=null&&!s.suggestion.accepted) Text("Recovery suggestion awaiting your review",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                Text(if(s.status=="completed") "Open saved workout →" else "Preview, start or manage →",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
internal fun androidx.compose.foundation.lazy.LazyListScope.trainingBackupItems(
    state: TrainingState, actions: PlanActions
) {
    if(state.researchDrafts.isNotEmpty()) item(key = "training-research-reviews") {
        var openedResearchReviewId by rememberSaveable(state.account ?: state.localPlanOwner) { mutableStateOf<String?>(null) }
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
}

internal fun androidx.compose.foundation.lazy.LazyListScope.trainingCoachingItems(
    state: TrainingState,
    plans: List<PlanSnapshot>,
    actions: PlanActions,
    onReviewAdaptation: (String) -> Unit
) {
    for (plan in plans) item(key = "coaching-review-${plan.id}") {
        Engine15CoachReviewCard(plan, actions, state.busy, state.message)
    }
    if (state.activeAthleteDiagnostic != null) item(key = "training-diagnostics") {
        AthleteDiagnosticsCard(state.activeAthleteDiagnostic)
    }
    if (state.activeAdaptationProposal?.type == AdaptationType.EASY_PACING_LOCK &&
        state.activeAthleteDiagnostic?.recalibrationAdvisory != RecalibrationAdvisory.ADDRESS_EASY_PACING_FIRST
    ) item(key = "training-easy-lock") { EasyPacingLockBanner() }
    for (proposal in state.pendingAdaptationProposals.values.filter { proposal ->
        state.plans.any { it.id == proposal.planId && it.status == "approved" }
    }) item(key = "adaptation-review-${proposal.planId}") {
        AdaptationProposalBanner(proposal, onReview = { onReviewAdaptation(proposal.planId) })
    }
    for (plan in state.plans.filter { it.status == "approved" &&
        state.adaptationRevisions[it.id].orEmpty().any { revision -> revision.revertedAtDate == null }
    }) item(key = "undo-adaptation-${plan.id}") {
        Text(plan.name ?: plan.request.goal, style = MaterialTheme.typography.labelMedium,
            color = StrideLabVisualPalette.ink)
        UndoAdaptationButton(plan, state.adaptationRevisions[plan.id].orEmpty(), state.busy, actions.revertLastAdaptation)
    }
    if (state.activityMatches.isNotEmpty()) item(key = "training-activity-matches") {
        PendingActivityMatchCards(state, actions)
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
                Triple(bundle,PlanBackup.additions(bundle.plans.filter { it.status != "draft" },state.plans),PlanBackup.researchAdditions(bundle.researchReviews,state.researchDrafts))
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
