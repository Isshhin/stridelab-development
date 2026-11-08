@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class,kotlinx.coroutines.FlowPreview::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

@Composable internal fun WorkoutScreen(state:TrainingState,planId:String,sessionId:String,actions:PlanActions,onBack:()->Unit,onMissed:(()->Unit)?=null,onMove:(()->Unit)?=null) {
    val plan=state.plans.find { it.id==planId };val session=plan?.sessions?.find { it.id==sessionId }
    if(plan==null||session==null) { Screen("Workout unavailable","TRAINING") { item { Text("This workout is not saved for the current account.");TextButton(onClick=onBack) { Text("Back to Training") } } };return }
    val current = Engine15SessionPresentation.forSession(plan, session.id)
    val shown = plan.cardPresentation(session)
    val rest = current.state == Engine15SessionPresentation.State.ACCEPTED_REST
    val timeOnly = current.state == Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY
    val exercises=shown.effectiveExercises
    val entryDraft=state.workoutDrafts[plan.id to session.id]?.takeIf { it.owner==plan.owner && it.logRevision==session.logRevision }
    var pendingSwap by rememberSaveable(plan.owner,session.id) { mutableStateOf<String?>(null) }
    var swapId by rememberSaveable(plan.owner,session.id) { mutableStateOf<String?>(null) }
    val done=session.status=="completed";val active=Engine1Engines.canManage(plan) && plan.status in setOf("approved","paused") && session.status in setOf("planned","in_progress")
    var minutes by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.minutes ?: session.log?.minutes?.toString() ?: "") };var distance by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.distance ?: session.log?.distanceKm?.toString() ?: "") };var effort by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.effort ?: session.log?.effort?.toString() ?: "") };var notes by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.notes ?: session.log?.notes ?: "") }
    var performedOn by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.performedOn ?: session.log?.performedOn?.toString() ?: if(session.log==null)LocalDate.now().toString() else "") }
    var entries by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.visibleEntries(exercises) ?: JSONObject().apply { session.log?.sets?.forEach { r -> put("${r.exerciseId}|${r.set}|reps",r.reps?.toString() ?: "");put("${r.exerciseId}|${r.set}|kg",r.kg?.toString() ?: "");put("${r.exerciseId}|${r.set}|seconds",r.seconds?.toString() ?: "") } }.toString()) }
    var exerciseIndex by rememberSaveable(plan.owner,session.id) { mutableIntStateOf(0) };var formError by rememberSaveable(plan.owner,session.id) { mutableStateOf<String?>(null) }
    var missed by rememberSaveable(plan.owner,session.id) { mutableStateOf(false) };var reason by rememberSaveable(plan.owner,session.id) { mutableStateOf("Time") };var move by rememberSaveable(plan.owner,session.id) { mutableStateOf(false) };var moveDate by rememberSaveable(plan.owner,session.id) { mutableStateOf(session.date.toString()) }
    var restSeconds by rememberSaveable(plan.owner,session.id) { mutableIntStateOf(0) };var restRunning by rememberSaveable(plan.owner,session.id) { mutableStateOf(false) }
    var guided by rememberSaveable(plan.owner,session.id) { mutableStateOf(true) }
    var recordSheetOpen by rememberSaveable(plan.owner,session.id) { mutableStateOf(false) }
    val initialContent=remember(plan.owner,session.id) { listOf(minutes,distance,effort,notes,performedOn,entries) }
    val editClock=remember(plan.owner,session.id) { java.util.concurrent.atomic.AtomicLong(entryDraft?.editedAt ?: 0L) }
    fun nextInputStamp()=editClock.updateAndGet { maxOf(System.currentTimeMillis(),it+1) }
    val inputStamp=remember(minutes,distance,effort,notes,performedOn,entries,session.logRevision) { nextInputStamp() }
    val currentCandidate=if(active)WorkoutEntryDraft(plan.owner,plan.id,session.id,session.logRevision,minutes,distance,effort,notes,performedOn,entries,inputStamp)else null
    val latestCandidate=rememberUpdatedState(currentCandidate)
    val latestStagingInfo=rememberUpdatedState(active to session.logRevision)
    val savedInputKey=rememberUpdatedState(entryDraft?.contentKey)
    val flushInputs=rememberUpdatedState<()->Unit>({
        if(latestStagingInfo.value.first) {
            val value=WorkoutEntryDraft(plan.owner,plan.id,session.id,latestStagingInfo.value.second,minutes,distance,effort,notes,performedOn,entries,nextInputStamp())
            if(value.contentKey!=initialContent || savedInputKey.value?.let { it!=value.contentKey }==true)actions.stageWorkoutDraft(value)
        }
    })
    LaunchedEffect(plan.owner,session.id) {
        snapshotFlow { listOf(minutes,distance,effort,notes,performedOn,entries) }.drop(1).debounce(500).collect {
            latestCandidate.value?.let(actions.stageWorkoutDraft)
        }
    }
    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner,plan.owner,session.id) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) {restRunning=false;flushInputs.value()} }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { restRunning=false;flushInputs.value();lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session.swaps) {
        pendingSwap?.let { id -> if(session.swaps.any { it.originalId==id }) {
            val values=JSONObject(entries);values.keys().asSequence().filter { it.startsWith("$id|") }.toList().forEach(values::remove)
            entries=values.toString();restRunning=false;pendingSwap=null
        } }
    }
    LaunchedEffect(restSeconds,restRunning) { if(restRunning&&restSeconds>0) { delay(1000);restSeconds--;if(restSeconds==0)restRunning=false } }
    fun number(text:String,label:String):Double?=if(text.isBlank()) null else text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw PlanConstraint("Enter a valid $label, or leave it blank.")
    fun whole(text:String,label:String):Int?=if(text.isBlank()) null else text.toIntOrNull() ?: throw PlanConstraint("Enter whole $label, or leave it blank.")
    fun log():WorkoutLog {
        val j=JSONObject(entries)
        val sets=exercises.flatMap { ex -> val count=if(session.suggestion?.accepted==true)maxOf(1,(ex.sets*.7).toInt())else ex.sets;(1..count).mapNotNull { i -> val prefix="${ex.id}|$i|";val r=whole(j.optString(prefix+"reps"),"repetitions");val kg=number(j.optString(prefix+"kg"),"load in kg");val seconds=whole(j.optString(prefix+"seconds"),"hold seconds");if(r==null&&kg==null&&seconds==null)null else SetRecord(ex.id,i,r,kg,seconds) } }
        return WorkoutLog(Instant.now(),number(minutes,"duration in minutes"),number(distance,"distance in km"),whole(effort,"effort from 1 to 10"),sets,notes,if(performedOn.isBlank())null else try {LocalDate.parse(performedOn)}catch(_:Exception){throw PlanConstraint("Choose a valid actual workout date.")})
    }
    if(active && exercises.isNotEmpty() && guided) {
        WorkoutPlayer(plan.owner,session,state.busy,entries,{entries=it},restSeconds,restRunning,
            onStartRest={restSeconds=it;restRunning=it>0},onRestRunning={restRunning=it},
            onRestSeconds={restSeconds=it},onBack=onBack,onReview={restRunning=false;guided=false},
            onConfirm={ exercise,set ->
                try {
                    formError=null
                    val values=JSONObject(entries);val prefix="${exercise.id}|$set|"
                    if(values.optString(prefix+"reps").isBlank() && values.optString(prefix+"seconds").isBlank()) {
                        values.put(prefix+if(exercise.seconds!=null)"seconds" else "reps",(exercise.seconds ?: exercise.reps).toString())
                        entries=values.toString()
                    }
                    val row=SetRecord(exercise.id,set,whole(values.optString(prefix+"reps"),"repetitions"),number(values.optString(prefix+"kg"),"load in kg"),whole(values.optString(prefix+"seconds"),"hold seconds"))
                    val actualDate=if(performedOn.isBlank())null else try {LocalDate.parse(performedOn)}catch(_:Exception){throw PlanConstraint("Choose a valid actual workout date.")}
                    val previous=session.log ?: WorkoutLog(Instant.now(),performedOn=actualDate)
                    val updated=previous.copy(at=Instant.now(),sets=previous.sets.filterNot { it.exerciseId==exercise.id&&it.set==set }+row)
                    actions.record(plan.id,plan.revision,session.id,updated,false)
                    true
                } catch(e:PlanConstraint) {formError=e.message;false}
            },error=formError)
    } else {
    val summaryMetrics:List<Pair<String,String>> = if(done) listOfNotNull(
        session.log?.minutes?.let { "Actual duration" to "%.0f min".format(it) },
        session.log?.distanceKm?.let { "Actual distance" to "%.2f km".format(it) },
        session.log?.effort?.let { "Recorded effort" to "$it / 10" }
    ) else listOfNotNull(
        "Planned duration" to "${current.currentMinutes} min",
        current.currentDistanceTargetKm?.let { "Planned target" to "%.1f km".format(it) }
    )
    StrideWorkoutDetailFrame(
        title=shown.effectiveTitle,
        discipline=disciplineLabel(session.discipline),
        status=if(done) "Completed" else session.status.replace('_',' '),
        dateLabel=if(done) session.log?.performedOn?.let { "Performed $it" }
            ?: "Scheduled ${session.date}" else "${session.date}",
        metrics=summaryMetrics,
        onBack=onBack
    ) {
        if(active && exercises.isNotEmpty()) item { StrideWorkoutPrimaryButton("Return to guided workout",{guided=true}) }
        if(shown.reason.isNotBlank()) item {
            Text(shown.reason,color=StrideWorkoutVisuals.muted,
                style=MaterialTheme.typography.bodyMedium)
        }
        session.linkedActivityId?.let { activityId -> item { StrideWorkoutSection("Linked activity") {val source=state.activities.find { it.id==activityId };Text(source?.let { "${it.title} · ${it.dateLabel} · ${it.distanceLabel} · ${it.durationLabel}" } ?: "The linked source is not currently cached. Its link and original prescription remain saved.");Text("The manual link confirms your association, not whether every prescribed interval or effort was performed. Manage the link in Activities.",style=MaterialTheme.typography.bodySmall) } } }
        if (plan.engineVersion == ENGINE15_VERSION && Engine1Engines.canManage(plan) && (plan.status == "approved" || current.disclosure != null)) item {
            Engine15RecommendationReview(plan, actions, state.busy, session.id)
        }
        session.suggestion?.takeUnless { current.disclosure != null }?.let { suggestion -> item { StrideWorkoutSection(
            if(suggestion.accepted) "Recovery adjustment" else "Suggested adjustment"
        ) {
            Text(suggestion.reason)
            if(!suggestion.accepted&&active) { StrideWorkoutPrimaryButton(if(suggestion.minutes==0) "Choose rest for this session" else "Accept lighter alternative",{actions.acceptSuggestion(plan.id,plan.revision,session.id)},!state.busy);Text("Original prescriptions remain saved. No catch-up workout is added.",style=MaterialTheme.typography.bodySmall) }
        } } }
        item { StrideWorkoutSection("Workout prescription") {
            if (rest || timeOnly) {
                Text(if (rest) "Reviewed rest · 0 minutes" else "Time-only run · ${current.currentMinutes} minutes")
                StrideWorkoutTimeline(current.currentSteps)
                Text("Original prescription · ${current.originalMinutes} min" + (current.originalDistanceKm?.let { " · $it km" } ?: ""), style=MaterialTheme.typography.bodySmall)
            }
            else if(session.suggestion?.accepted==true && current.disclosure == null && session.suggestion.minutes==0) Text("You chose rest for this session. The original prescription remains saved; no workout completion is inferred.")
            else if(session.suggestion?.accepted==true && current.disclosure == null) { Text("Keep any running conversational. Use an easier variation and fewer controlled resistance sets, or rest if needed. Stop at ${session.effectiveMinutes} minutes; do not chase the original distance or hard intervals.");Text("Original target: ${session.minutes} min · ${session.title}",style=MaterialTheme.typography.bodySmall) }
            else {StrideWorkoutTimeline(current.currentSteps);current.currentDistanceTargetKm?.let { Text("Planned target: %.1f km. This is not a recorded distance.".format(it),style=MaterialTheme.typography.bodySmall,color=StrideWorkoutVisuals.muted) } }
        } }
        if(exercises.isNotEmpty()) {
            val ex=exercises[exerciseIndex.coerceIn(0,exercises.lastIndex)]
            val setCount=if(session.suggestion?.accepted==true) maxOf(1,(ex.sets*.7).toInt()) else ex.sets
            item { StrideWorkoutSection("Exercise ${exerciseIndex+1} of ${exercises.size}") {
                Text(ex.equipment.uppercase(),style=MaterialTheme.typography.labelMedium,color=StrideWorkoutVisuals.muted)
                Text(ex.name,style=MaterialTheme.typography.headlineSmall);Text("$setCount sets · ${ex.reps?.let { "$it reps" } ?: "${ex.seconds} sec hold"} · rest ${ex.rest} sec")
                Text(ex.cue)
                session.swaps.find { it.replacement.id==ex.id }?.let { swap -> Text("Easier alternative to "+session.exercises.first { it.id==swap.originalId }.name,style=MaterialTheme.typography.bodySmall);Text(swap.reason,style=MaterialTheme.typography.bodySmall) }
                if(active && session.log?.sets?.none { it.exerciseId==ex.id }!=false && ExerciseAlternatives.easier(ex)!=null)OutlinedButton(onClick={swapId=ex.id},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Choose an easier alternative") }
                if(!active) Text(if(done) "Actual values below are your saved records. Blank values were not recorded." else if(!Engine1Engines.canManage(plan))"This saved engine/policy is read-only here. Its original prescription is retained for viewing and export." else "Approve the plan before recording. Targets are not completed sets.",style=MaterialTheme.typography.bodySmall)
            } }
            (1..setCount).forEach { i -> item {
                val data=JSONObject(entries);val prefix="${ex.id}|$i|"
                fun set(key:String,value:String) { entries=JSONObject(entries).put(prefix+key,value.take(64)).toString() }
                StrideWorkoutSection("Set $i") {
                    if(ex.seconds!=null) OutlinedTextField(data.optString(prefix+"seconds"),{set("seconds",it)},label={Text("Actual hold seconds")},enabled=active,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                    else OutlinedTextField(data.optString(prefix+"reps"),{set("reps",it)},label={Text("Actual reps")},enabled=active,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                    OutlinedTextField(data.optString(prefix+"kg"),{set("kg",it)},label={Text("Actual load (kg), optional")},enabled=active,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
            } }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={exerciseIndex--},enabled=exerciseIndex>0,modifier=Modifier.weight(1f)) { Text("Previous") }
                OutlinedButton(onClick={exerciseIndex++},enabled=exerciseIndex<exercises.lastIndex,modifier=Modifier.weight(1f)) { Text("Next exercise") }
            } }
            if(active) item { StrideWorkoutSection("Rest timer") { Text("${restSeconds/60}:${(restSeconds%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.titleLarge,color=StrideWorkoutVisuals.white)
                Text("Pauses when you leave the app. Resume it here when ready.",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={restSeconds=ex.rest;restRunning=true}) { Text("Start rest") };TextButton(onClick={restRunning=!restRunning},enabled=restSeconds>0) { Text(if(restRunning) "Pause" else "Resume") } }
            } }
        }
        if (!rest && session.status!="unassigned" && exercises.isNotEmpty()) item { StrideWorkoutSection(if(done) "Recorded workout" else "Actual workout record") {
            if(active)Text(if(entryDraft?.contentKey==currentCandidate?.contentKey)"Entry draft saved on this device · not yet confirmed as training" else "Entries auto-save on this device. Save progress or completion to confirm what you performed.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Only enter values you actually performed. Blank values stay unrecorded; no target is copied into your history.",style=MaterialTheme.typography.bodySmall)
            NativeDateField("Actual workout date",performedOn,{performedOn=it.take(32)},active)
            Text("Choose the date you actually trained. It may differ from the scheduled date; older logs may have no recorded workout date.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(minutes,{minutes=it.take(64)},label={Text("Actual duration (minutes), optional")},enabled=active,modifier=Modifier.fillMaxWidth().testTag("workout-actual-minutes"))
            if(session.discipline=="run"||session.discipline=="cond") OutlinedTextField(distance,{distance=it.take(64)},label={Text("Actual distance (km), optional")},enabled=active,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(effort,{effort=it.take(64)},label={Text("Effort 1–10, optional")},enabled=active,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(notes,{notes=it.take(2000)},label={Text("How did it feel?")},enabled=active,modifier=Modifier.fillMaxWidth())
            session.log?.let { Text("Saved ${it.at} · ${it.sets.size} sets with recorded values",style=MaterialTheme.typography.bodySmall) }
        } }
        if(active && exercises.isEmpty() && !rest && session.status!="unassigned") item {
            StrideWorkoutPrimaryButton("Log Actual Workout",
                onClick={ recordSheetOpen=true },
                modifier=Modifier.testTag("workout-open-record"))
        }
        formError?.let { item { Text(it,color=MaterialTheme.colorScheme.error) } }
        if(active && exercises.isNotEmpty()) item { StrideWorkoutPrimaryButton("Save completed workout",{try {formError=null;actions.record(plan.id,plan.revision,session.id,log(),true)}catch(e:PlanConstraint){formError=e.message}},!state.busy)
            OutlinedButton(onClick={try {formError=null;actions.record(plan.id,plan.revision,session.id,log(),false)}catch(e:PlanConstraint){formError=e.message}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Save progress for offline reopening") }
        }
        if(active) item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={ if(onMissed!=null) onMissed() else missed=true }) {
                    Text("Missed / skipped",color=StrideWorkoutVisuals.muted)
                }
                TextButton(onClick={ if(onMove!=null) onMove() else move=true },
                    enabled=session.log==null && session.id !in plan.request.lockedSessionDays) {
                    Text("Move workout",color=StrideWorkoutVisuals.muted)
                }
            }
        }
        if(done) item { StrideWorkoutSection("Recording status") {
            session.log?.let { saved ->
                saved.performedOn?.let { Text("Performed $it",
                    style=MaterialTheme.typography.bodyMedium,color=StrideWorkoutVisuals.white) }
                saved.notes.takeIf { it.isNotBlank() }?.let { Text(it,
                    style=MaterialTheme.typography.bodyLarge,color=StrideWorkoutVisuals.white) }
            }
            Text("Completion is your recorded confirmation, not a sensor-verified activity. It appears separately in workout history without invented pace, GPS or heart rate.",
                style=MaterialTheme.typography.bodySmall,color=StrideWorkoutVisuals.muted)
        } }
        item { PlanFeedbackForm(plan,actions,session.id) }
    }
    }
    if(recordSheetOpen && active && exercises.isEmpty()) {
        ModalBottomSheet(
            onDismissRequest={ recordSheetOpen=false },
            containerColor=StrideWorkoutVisuals.card,
            contentColor=StrideWorkoutVisuals.white,
            shape=androidx.compose.foundation.shape.RoundedCornerShape(
                topStart=26.dp,topEnd=26.dp),
            dragHandle={ BottomSheetDefaults.DragHandle(color=StrideWorkoutVisuals.muted) }
        ) {
            MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(
                primary=StrideWorkoutVisuals.lime,
                onPrimary=Color(0xFF10120D),
                surface=StrideWorkoutVisuals.card,
                onSurface=StrideWorkoutVisuals.white,
                onSurfaceVariant=StrideWorkoutVisuals.muted,
                outline=StrideWorkoutVisuals.divider
            )) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .imePadding().padding(horizontal=22.dp).padding(bottom=36.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp)
            ) {
                Text("Log Actual Workout",style=MaterialTheme.typography.headlineSmall,
                    color=StrideWorkoutVisuals.white)
                Text("Only record what you performed. Planned targets are not copied into your history.",
                    style=MaterialTheme.typography.bodySmall,color=StrideWorkoutVisuals.muted)
                NativeDateField("Actual workout date",performedOn,
                    {performedOn=it.take(32)},active)
                OutlinedTextField(minutes,{minutes=it.take(64)},
                    label={Text("Actual duration (minutes), optional")},
                    enabled=active,modifier=Modifier.fillMaxWidth()
                        .testTag("workout-actual-minutes"))
                if(session.discipline=="run" || session.discipline=="cond")
                    OutlinedTextField(distance,{distance=it.take(64)},
                        label={Text("Actual distance (km), optional")},
                        enabled=active,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(effort,{effort=it.take(64)},
                    label={Text("Effort 1–10, optional")},
                    enabled=active,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(notes,{notes=it.take(2000)},
                    label={Text("How did it feel?")},
                    enabled=active,modifier=Modifier.fillMaxWidth(),
                    minLines=2)
                formError?.let {
                    Text(it,color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                }
                StrideWorkoutPrimaryButton("Save completed workout",onClick={
                    try {
                        formError=null
                        actions.record(plan.id,plan.revision,session.id,log(),true)
                        recordSheetOpen=false
                    } catch(error:PlanConstraint) { formError=error.message }
                },enabled=!state.busy)
                OutlinedButton(onClick={
                    try {
                        formError=null
                        actions.record(plan.id,plan.revision,session.id,log(),false)
                        recordSheetOpen=false
                    } catch(error:PlanConstraint) { formError=error.message }
                },enabled=!state.busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text("Save progress for offline reopening")
                }
            }
            }
        }
    }
    swapId?.let { id -> exercises.find { it.id==id }?.let { original -> ExerciseAlternatives.easier(original)?.let { replacement ->
        AlertDialog(onDismissRequest={swapId=null},title={Text("Review easier alternative")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(replacement.name,style=MaterialTheme.typography.titleLarge)
            Text("${replacement.sets} sets · ${replacement.reps?.let { "$it reps" } ?: "${replacement.seconds} seconds"} · rest ${replacement.rest} seconds")
            Text(replacement.cue);Text(ExerciseAlternatives.REASON)
            Text("Any unsaved entries for this exercise will be cleared. Previously saved sets cannot be replaced.",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick={pendingSwap=id;actions.swap(plan.id,plan.revision,session.id,id);swapId=null}) { Text("Use easier alternative") }},dismissButton={TextButton(onClick={swapId=null}) { Text("Keep original") }})
    } } }
    if(missed && onMissed==null) AlertDialog(onDismissRequest={missed=false},title={Text("Why was this session missed?")},text={ Column { ChoiceChips(listOf("Time","Fatigue","Soreness","Illness","Other"),listOf("Time","Fatigue","Soreness","Illness","Other").indexOf(reason)) { reason=listOf("Time","Fatigue","Soreness","Illness","Other")[it] };Text("No catch-up volume is added. Any recovery suggestion requires your explicit choice.") } },confirmButton={TextButton(onClick={actions.missed(plan.id,plan.revision,session.id,reason,false);missed=false}) { Text("Mark missed") }},dismissButton={Row { TextButton(onClick={actions.missed(plan.id,plan.revision,session.id,reason,true);missed=false}) { Text("Skip") };TextButton(onClick={missed=false}) { Text("Cancel") } }})
    if(move && onMove==null) AlertDialog(onDismissRequest={move=false},title={Text("Move workout")},text={Column { NativeDateField("Scheduled date",moveDate,{moveDate=it});Text("Available-day, long-run-day and recovery conflicts are checked before saving.");formError?.let { Text(it) } }},confirmButton={TextButton(onClick={try {actions.move(plan.id,plan.revision,session.id,LocalDate.parse(moveDate));move=false}catch(e:PlanConstraint){formError=e.message}catch(_:java.time.format.DateTimeParseException){formError="Enter a valid YYYY-MM-DD date."}}) { Text("Save move") }},dismissButton={TextButton(onClick={move=false}) { Text("Cancel") }})
}
