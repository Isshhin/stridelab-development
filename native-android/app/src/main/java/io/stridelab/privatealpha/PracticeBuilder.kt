@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.util.UUID

@Composable internal fun PracticeBuilder(state:TrainingState,actions:PlanActions,key:String,onDone:()->Unit,onPreview:(String,String)->Unit,onApproved:()->Unit) {
    val context=LocalContext.current
    var catalog by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) };var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withContext(Dispatchers.Main.immediate) { runCatching { withContext(Dispatchers.IO) { readLibrary(context) } }.onSuccess { catalog=it }.onFailure { failed=true } } }
    if(state.account==null || catalog.isEmpty()) { StrideLibraryFrame("Prepare a session","LIBRARY",onBack=onDone) { item { Text(if(state.account==null)"Sign in once through Settings to keep practice and templates private to your account." else if(failed)"The preserved library could not be opened." else "Opening offline movement library…");TextButton(onClick=onDone) { Text("Back") } } };return }
    val saved=state.templates.find { "saved-${it.id}"==key }
    val entry=catalog.find { "entry-${it.id}"==key }
    val initialIds=if(saved!=null)saved.exercises.map { it.id }else if(entry!=null)listOf(entry.id)else if(key=="lower")listOf("e1","e3","e6")else emptyList()
    var templateId by rememberSaveable(key) { mutableStateOf(saved?.id ?: UUID.randomUUID().toString()) }
    var name by rememberSaveable(key) { mutableStateOf(saved?.name ?: entry?.title ?: if(key=="lower")"Lower body strength" else "My practice session") }
    var type by rememberSaveable(key) { mutableStateOf(saved?.discipline ?: entry?.discipline ?: "str") }
    var idsRaw by rememberSaveable(key) { mutableStateOf(JSONArray(initialIds).toString()) }
    var doses by rememberSaveable(key) { mutableStateOf(JSONObject().apply { initialIds.forEach { id -> val e=saved?.exercises?.find { it.id==id };put(id,JSONObject().put("sets",(e?.sets ?: 2).toString()).put("reps",(e?.reps ?: 6).toString()).put("rest",(e?.rest ?: 60).toString())) } }.toString()) }
    var date by rememberSaveable(key) { mutableStateOf(LocalDate.now().toString()) };var minutes by rememberSaveable(key) { mutableStateOf((saved?.minutes ?: 30).toString()) }
    var requestRaw by rememberSaveable(key) { mutableStateOf((state.plans.firstOrNull()?.request ?: PlanRequest()).encode().toString()) }
    val request=PlanRequest.decode(JSONObject(requestRaw));fun edit(p:PlanRequest) { requestRaw=p.encode().toString() }
    val ids=JSONArray(idsRaw).strings()
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) };var awaiting by rememberSaveable(key) { mutableStateOf(false) };var beforeId by rememberSaveable(key) { mutableStateOf<String?>(null) };var previewId by rememberSaveable(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.busy,state.plans,state.message) { if(awaiting&&!state.busy) { state.plans.firstOrNull()?.takeIf { it.id!=beforeId }?.let { previewId=it.id;awaiting=false };if(state.message!=null)awaiting=false } }
    fun template():SessionTemplate {
        val raw=JSONObject(doses)
        val ex=ids.map { id -> val entry=catalog.find { it.id==id && !it.guide && it.discipline!="run" } ?: throw PlanConstraint("Choose a supported resistance movement.")
            val dose=raw.optJSONObject(id) ?: JSONObject()
            fun n(k:String)=dose.optString(k).toIntOrNull() ?: throw PlanConstraint("Enter whole target sets, repetitions and rest seconds for ${entry.title}.")
            ExercisePrescription(id,entry.title,n("sets"),n("reps"),rest=n("rest"),equipment=entry.text("eq"),cue=entry.list("tech").firstOrNull() ?: entry.text("purpose")) }
        return SessionTemplate(templateId,name,type,minutes.toIntOrNull() ?: throw PlanConstraint("Enter the available minutes."),ex,if(key=="lower")"Accepted Phase 4E lower-body template (e1/e3/e6), with user-reviewed targets" else "Accepted Phase 4E movement catalog; user-selected session targets").also { it.validate() }
    }
    val preview=state.plans.find { it.id==previewId }
    LaunchedEffect(preview?.status) { if(preview?.status=="approved")onApproved() }
    if(preview!=null) { StrideLibraryFrame("Practice preview","DRAFT · NOT APPROVED",onBack=onDone) {
        item { PlanOverview(preview) };preview.sessions.forEach { session -> item { PlannedWorkoutCard(session) { onPreview(preview.id,session.id) } } }
        item { StrideWorkoutPrimaryButton("Approve practice session",{actions.approve(preview.id,preview.revision)},!state.busy);TextButton(onClick={previewId=null;awaiting=false}) { Text("Edit session targets / date") };TextButton(onClick=onDone) { Text("Keep draft and return") } }
    };return }
    StrideLibraryFrame("Prepare a session","LIBRARY · YOUR TARGETS",onBack=onDone) {
        item { Text("Choose a movement or reusable template, review the targets, then create a dated draft. Actual workout values remain blank until you log them.") }
        item { FormText("Session / template name",name,{name=it.take(80)});NativeDateField("Scheduled date",date,{date=it});FormText("Available session minutes",minutes,{minutes=it},true) }
        item { ChoiceChips(listOf("Strength","Calisthenics"),if(type=="cal")1 else 0) { val next=if(it==1)"cal" else "str";if(next!=type){type=next;idsRaw="[]"} } }
        item { Text("Choose up to eight movements",style=MaterialTheme.typography.titleLarge);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { catalog.filter { !it.guide && it.discipline==type }.forEach { entry -> SignalChip(entry.id in ids,{
            val next=if(entry.id in ids)ids-entry.id else if(ids.size<8)ids+entry.id else ids
            idsRaw=JSONArray(next).toString()
            if(!JSONObject(doses).has(entry.id))doses=JSONObject(doses).put(entry.id,JSONObject().put("sets","2").put("reps","6").put("rest","60")).toString()
        },label={Text(entry.title)}) } } }
        ids.forEach { id -> item(key=id) {
            val entry=catalog.first { it.id==id };val d=JSONObject(doses).optJSONObject(id) ?: JSONObject()
            fun set(k:String,v:String) { doses=JSONObject(doses).put(id,JSONObject(d.toString()).put(k,v)).toString() }
            StrideWorkoutSection(entry.title) { Text(entry.text("level")+" · "+entry.text("eq"));Text(entry.text("reg"),style=MaterialTheme.typography.bodySmall)
                FormText("Target sets",d.optString("sets"),{set("sets",it)},true);FormText("Target reps per set",d.optString("reps"),{set("reps",it)},true);FormText("Rest seconds",d.optString("rest"),{set("rest",it)},true)
            }
        } }
        item { StrideWorkoutSection("Current ability") {Text("Choose what you can control now. Use an easier library option when needed.");Text("Strength experience");ChoiceChips(listOf("Starting","Some experience","Experienced"),request.strengthExperience) { edit(request.copy(strengthExperience=it)) };Text("Pushing ability");ChoiceChips(listOf("Assisted","Controlled push-up","Comfortable push-up"),request.calLevel) { edit(request.copy(calLevel=it)) };Text("Pulling ability");ChoiceChips(listOf("Assisted","Supported pull","Controlled pull-up"),request.pullLevel) { edit(request.copy(pullLevel=it)) };Text("Core ability");ChoiceChips(listOf("Starting","Hollow tuck","Controlled hollow hold"),request.coreLevel) { edit(request.copy(coreLevel=it)) } } }
        item { StrideWorkoutSection("Available equipment") {Text("Only selected suitable equipment is used when preparing this session.")} }
        listOf("db" to "Dumbbells","bb" to "Barbells","rack" to "Racks with safeties","bench" to "Benches","bar" to "Secure overhead bars","support" to "Stable raised / foot supports","bands" to "Bands","anchor" to "Secure band anchors").forEach { (k,label) -> item { Row(Modifier.fillMaxWidth()) { Text(label,Modifier.weight(1f));TextButton(onClick={edit(request.copy(equipment=request.equipment+(k to maxOf(0,(request.equipment[k]?:0)-1))))}) { Text("−") };Text("${request.equipment[k]?:0}",Modifier.padding(12.dp));TextButton(onClick={edit(request.copy(equipment=request.equipment+(k to minOf(20,(request.equipment[k]?:0)+1))))}) { Text("+") } } } }
        error?.let { item { Text(it,color=MaterialTheme.colorScheme.error) } }
        item { StrideWorkoutPrimaryButton("Create practice draft",{try { error=null;val t=template();val day=LocalDate.parse(date);t.draft(state.account,request,day);beforeId=state.plans.firstOrNull()?.id;awaiting=true;actions.prepareSession(t,request,day) }catch(e:Exception){error=if(e is PlanConstraint)e.message else "Check the date and numeric targets."}},!state.busy&&!awaiting)
            OutlinedButton(onClick={try {error=null;actions.saveTemplate(template())}catch(e:PlanConstraint){error=e.message}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text(if(state.templates.any { it.id==templateId })"Update reusable template" else "Save reusable template") }
            if(state.templates.any { it.id==templateId })Text("Template saved for this account. Its edits never rewrite previous sessions.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=onDone) { Text("Back to Library") }
        }
    }
}
