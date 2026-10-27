@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Guided presentation only: the approved prescription and explicit actual log remain authoritative. */
@Composable internal fun WorkoutPlayer(
    owner:String,session:PlannedSession,busy:Boolean,entries:String,onEntries:(String)->Unit,
    restSeconds:Int,restRunning:Boolean,onStartRest:(Int)->Unit,onRestRunning:(Boolean)->Unit,onRestSeconds:(Int)->Unit,
    onBack:()->Unit,onReview:()->Unit,onConfirm:(ExercisePrescription,Int)->Boolean,error:String?
) {
    val exercises=session.effectiveExercises
    fun count(ex:ExercisePrescription)=if(session.suggestion?.accepted==true)maxOf(1,(ex.sets*.7).toInt())else ex.sets
    var index by rememberSaveable(owner,session.id) { mutableIntStateOf(exercises.indexOfFirst { ex -> (1..count(ex)).any { set -> session.log?.sets?.none { it.exerciseId==ex.id && it.set==set }!=false } }.coerceAtLeast(0)) }
    val ex=exercises[index.coerceIn(exercises.indices)]
    var set by rememberSaveable(owner,session.id,ex.id) { mutableIntStateOf((1..count(ex)).firstOrNull { n->session.log?.sets?.none { it.exerciseId==ex.id && it.set==n }!=false } ?: count(ex)) }
    set=set.coerceIn(1,count(ex))
    var resting by rememberSaveable(owner,session.id) { mutableStateOf(false) }
    var waitingForSave by rememberSaveable(owner,session.id) { mutableStateOf<String?>(null) }
    var saveNotice by rememberSaveable(owner,session.id) { mutableStateOf<String?>(null) }
    var observedSaving by rememberSaveable(owner,session.id) { mutableStateOf(false) }
    var savingRevision by rememberSaveable(owner,session.id) { mutableIntStateOf(session.logRevision) }
    var editing by rememberSaveable(owner,session.id,ex.id,set) { mutableStateOf(false) }
    var showGuide by rememberSaveable(owner,session.id) { mutableStateOf(false) }
    var holdSeconds by rememberSaveable(owner,session.id,ex.id,set) { mutableIntStateOf(ex.seconds ?: 0) }
    var holdRunning by rememberSaveable(owner,session.id,ex.id,set) { mutableStateOf(false) }
    val saved=session.log?.sets?.find { it.exerciseId==ex.id && it.set==set }
    val recorded=exercises.sumOf { movement -> (1..count(movement)).count { n->session.log?.sets?.any { it.exerciseId==movement.id&&it.set==n }==true } }
    val total=exercises.sumOf(::count)
    val playerScroll=rememberLazyListState()
    LaunchedEffect(ex.id,set,resting) { playerScroll.scrollToItem(0) }
    val context=LocalContext.current
    var catalog by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    LaunchedEffect(Unit) { withContext(Dispatchers.Main.immediate) { catalog=withContext(Dispatchers.IO) { runCatching { readLibrary(context) }.getOrDefault(emptyList()) } } }
    // Never display a full/progression movement as a demonstration of an easier saved variant.
    val guide=catalog.firstOrNull { it.id==ex.id && !it.guide }
    val image=guide?.raw?.optJSONObject("image")
    val exactImage=image?.optJSONArray("fit")?.optString(0) in setOf("exact","schematic") || (ex.id=="e4"&&ex.name=="Push-up") || (ex.id=="e2"&&ex.name=="Controlled pull-up")
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)holdRunning=false }
        lifecycle.lifecycle.addObserver(observer);onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(holdRunning,holdSeconds) { if(holdRunning&&holdSeconds>0) {delay(1000);holdSeconds--;if(holdSeconds==0)holdRunning=false} }
    LaunchedEffect(session.logRevision,busy,error) {
        if(busy && waitingForSave!=null)observedSaving=true
        val expected=waitingForSave
        if(expected!=null && session.logRevision>savingRevision && saved!=null && expected=="${ex.id}|$set") {
            waitingForSave=null;resting=true;onStartRest(ex.rest);holdRunning=false
        } else if(expected!=null && !busy && (error!=null || observedSaving))waitingForSave=null
    }
    LaunchedEffect(waitingForSave) {
        val expected=waitingForSave
        if(expected!=null) {
            delay(10000)
            if(waitingForSave==expected) {waitingForSave=null;saveNotice="A saved confirmation has not arrived. Check the record in Review & finish before retrying."}
        }
    }
    fun advance() {
        onRestRunning(false);resting=false;holdRunning=false
        if(set<count(ex))set++ else if(index<exercises.lastIndex)index++ else onReview()
    }
    val prefix="${ex.id}|$set|"
    val input=JSONObject(entries)
    val actualText=input.optString(prefix+if(ex.seconds!=null)"seconds" else "reps")
    val confirmation=actualText.ifBlank { (ex.seconds ?: ex.reps).toString() }
    val unit=if(ex.seconds!=null)"seconds" else "reps"
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().testTag("workout-player").semantics { paneTitle="Guided workout" }) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("Leave workout") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick=onReview,modifier=Modifier.testTag("workout-review")) { Text("Review & finish") }
        }
        LinearProgressIndicator(progress={recorded.toFloat()/total.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp).semantics { contentDescription="$recorded of $total sets recorded" })
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("screen"),state=playerScroll,contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Text("EXERCISE ${index+1} OF ${exercises.size}",color=disciplineColor(session.discipline),style=MaterialTheme.typography.labelLarge)
                Text(ex.name,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.testTag("player-exercise").semantics { heading() })
                Text("Set $set of ${count(ex)} · ${ex.equipment}",style=MaterialTheme.typography.bodyLarge)
            }
            if(resting) {
                item { SignalCard(Modifier.testTag("player-rest")) {
                    Text(if(restSeconds>0)"TAKE A BREATHER" else "READY WHEN YOU ARE",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
                    Text("${restSeconds/60}:${(restSeconds%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.displayLarge,modifier=Modifier.semantics { contentDescription="$restSeconds seconds rest remaining" })
                    Text("Set recorded. ${if(set<count(ex))"Next: set ${set+1} of ${count(ex)}" else if(index<exercises.lastIndex)"Up next: ${exercises[index+1].name}" else "Next: review your workout"}",style=MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={onRestRunning(!restRunning)},enabled=restSeconds>0) { Text(if(restRunning)"Pause rest" else "Resume rest") }
                        OutlinedButton(onClick={onRestSeconds((restSeconds+15).coerceAtMost(3600))}) { Text("+15 sec") }
                    }
                    Text("The timer pauses when you leave the app. Continue when you feel ready.",style=MaterialTheme.typography.bodySmall)
                } }
            } else {
                item { SignalCard {
                    Text("${ex.seconds ?: ex.reps}",style=MaterialTheme.typography.displayLarge,color=MaterialTheme.colorScheme.primary)
                    Text(if(ex.seconds!=null)"SECOND HOLD · PLANNED TARGET" else "REPETITIONS · PLANNED TARGET",style=MaterialTheme.typography.labelLarge)
                    Text(ex.cue,style=MaterialTheme.typography.bodyLarge)
                    if(exactImage && guide!=null)LibraryImage(guide,compact=true)
                    else Text("Follow the written cues. An exact movement demonstration is not available offline.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(ex.seconds!=null) {
                        Text("Hold timer · $holdSeconds sec",style=MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={if(holdSeconds==0)holdSeconds=ex.seconds;holdRunning=!holdRunning}) { Text(if(holdRunning)"Pause hold" else "Start hold") }
                            TextButton(onClick={holdRunning=false;holdSeconds=ex.seconds}) { Text("Reset") }
                        }
                        Text("Timer completion does not log a set. Confirm only what you performed.",style=MaterialTheme.typography.bodySmall)
                    }
                } }
                item {
                    if(saved!=null)Text("Set saved · ${saved.seconds ?: saved.reps ?: "Not recorded"} $unit${saved.kg?.let { " · $it kg" } ?: ""}",color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("player-set-saved"))
                    else Text("Confirm only the reps or hold you actually performed. You can enter a different value.",style=MaterialTheme.typography.bodyMedium)
                }
                item { Text(if(index<exercises.lastIndex)"UP NEXT · ${exercises[index+1].name}" else "LAST EXERCISE · Review your actual workout when ready",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={index--;holdRunning=false},enabled=index>0&&!busy&&waitingForSave==null,modifier=Modifier.weight(1f)) { Text("Previous") }
                    OutlinedButton(onClick={if(index<exercises.lastIndex){index++;holdRunning=false}else onReview()},enabled=!busy&&waitingForSave==null,modifier=Modifier.weight(1f)) { Text(if(index<exercises.lastIndex)"Next exercise" else "Review workout") }
                };Text("Browsing exercises does not record skipped sets.",style=MaterialTheme.typography.bodySmall) }
            }
            (error ?: saveNotice)?.let { item { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) } }
        }
        Surface(tonalElevation=3.dp) { Column(Modifier.padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            // Stable touch targets: image decoding and scrolling must not move these controls.
            if(!resting)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={holdRunning=false;showGuide=true},modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("Form guide") }
                OutlinedButton(onClick={if(saved!=null)onReview()else editing=true},modifier=Modifier.weight(1f).heightIn(min=48.dp),enabled=!busy&&waitingForSave==null) { Text(if(saved!=null)"Review set" else "Adjust set") }
            }
            SignalButton(if(resting) { if(restSeconds>0)"Finish rest & continue" else "Continue" } else if(saved!=null)"Continue to next set" else if(busy||waitingForSave!=null)"Saving set…" else "Confirm $confirmation $unit & rest",{
                if(resting||saved!=null)advance() else {saveNotice=null;observedSaving=false;savingRevision=session.logRevision;waitingForSave="${ex.id}|$set";holdRunning=false;if(!onConfirm(ex,set))waitingForSave=null}
            },!busy&&(waitingForSave==null||resting))
        } }
    }
    }
    if(editing)ModalBottomSheet(onDismissRequest={editing=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=22.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Your actual set",style=MaterialTheme.typography.headlineMedium)
            Text("${ex.name} · set $set",style=MaterialTheme.typography.titleMedium)
            Text("Enter what you performed. These entries remain a draft until you confirm the set in the workout.")
            fun write(key:String,value:String) {onEntries(JSONObject(entries).put(prefix+key,value.take(64)).toString())}
            OutlinedTextField(actualText,{write(if(ex.seconds!=null)"seconds" else "reps",it)},label={Text(if(ex.seconds!=null)"Actual hold seconds" else "Actual reps")},modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
            OutlinedTextField(input.optString(prefix+"kg"),{write("kg",it)},label={Text("Actual load (kg), optional")},modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
            SignalButton("Use these values",{editing=false})
        }
    }
    if(showGuide)ModalBottomSheet(onDismissRequest={showGuide=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(ex.name,style=MaterialTheme.typography.headlineMedium)
            Text(ex.equipment,style=MaterialTheme.typography.labelLarge)
            if(exactImage&&guide!=null)LibraryImage(guide)
            Text(ex.cue,style=MaterialTheme.typography.bodyLarge)
            guide?.list("tech")?.forEachIndexed { n,cue->Text("${n+1}. $cue",style=MaterialTheme.typography.bodyLarge) }
            guide?.text("mistake")?.takeIf { it.isNotBlank() }?.let { Text(it) }
            Text("Use a comfortable range. Stop if the movement causes pain or you cannot maintain control. Choose an easier alternative from Review & finish if needed.")
            if(!exactImage)Text("No exact offline demonstration is available for this saved variation. Related or harder movements are not shown as a substitute.",style=MaterialTheme.typography.bodySmall)
            SignalButton("Back to workout",{showGuide=false})
        }
    }
}
