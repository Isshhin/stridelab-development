@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class,kotlinx.coroutines.FlowPreview::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

@Composable internal fun WorkoutScreen(state:TrainingState,planId:String,sessionId:String,actions:PlanActions,onBack:()->Unit,onMissed:(()->Unit)?=null,onMove:(()->Unit)?=null) {
    val plan=state.plans.find { it.id==planId };val session=plan?.sessions?.find { it.id==sessionId }
    if (plan == null || session == null) {
        Screen("Workout unavailable", "TRAINING") {
            item {
                Text("This workout is not saved for the current account.", color = WorkoutDetailPalette.textMuted)
                TextButton(onClick = onBack) { Text("Back to Training", color = WorkoutDetailPalette.text) }
            }
        }
        return
    }
    val current = Engine15SessionPresentation.forSession(plan, session.id)
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    val shown = plan.cardPresentation(session)
    val rest = current.state == Engine15SessionPresentation.State.ACCEPTED_REST
    val timeOnly = current.state == Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY
    val exercises=shown.effectiveExercises
    val entryDraft=state.workoutDrafts[plan.id to session.id]?.takeIf { it.owner==plan.owner && it.logRevision==session.logRevision }
    var pendingSwap by rememberSaveable(plan.owner,session.id) { mutableStateOf<String?>(null) }
    var swapId by rememberSaveable(plan.owner,session.id) { mutableStateOf<String?>(null) }
    var swapDirection by rememberSaveable(plan.owner,session.id) { mutableStateOf("EASIER") }
    val done=session.status=="completed";val active=Engine1Engines.canManage(plan) && plan.status in setOf("approved","paused") && session.status in setOf("planned","in_progress")
    val exportToWatch = if (session.discipline == "run" && !rest && current.currentMinutes > 0 &&
        session.status in setOf("planned", "in_progress", "completed")
    ) {
        rememberRunningWorkoutFitExport(plan.owner, listOfNotNull(state.account, state.localGarminOwner).distinct(),
            shown.effectiveTitle, session, current)
    } else null
    val crossDebrief = remember(plan, session, state.plans, state.activities) {
        if (done && session.discipline != "run") Engine15DebriefEngine.workout(
            plan, session.id, state.plans, state.activities, java.time.ZoneId.systemDefault()
        ) else null
    }
    val plannedRun=session.discipline=="run" && session.status in setOf("planned","in_progress")
    var recordingOpen by rememberSaveable(plan.owner,session.id) { mutableStateOf(!plannedRun) }
    var selectedStepIndex by rememberSaveable(plan.owner,session.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(session.id, session.difficultyStep) { selectedStepIndex = null }
    var minutes by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.minutes ?: session.log?.minutes?.toString() ?: "") };var distance by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.distance ?: session.log?.distanceKm?.toString() ?: "") };var effort by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.effort ?: session.coachingLog()?.effort?.toString() ?: "") };var notes by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.notes ?: session.log?.notes ?: "") }
    val responseEditable=done && Engine1Engines.canManage(plan) && session.discipline=="run" && plan.engineVersion==ENGINE15_VERSION && plan.status in setOf("approved","paused")
    var difficulty by rememberSaveable(plan.owner,session.id) { mutableStateOf(if(entryDraft!=null)entryDraft.completionDifficulty else session.coachingLog()?.completionDifficulty) }
    var discomfort by rememberSaveable(plan.owner,session.id) { mutableStateOf(if(entryDraft!=null)entryDraft.discomfort else session.coachingLog()?.discomfort) }
    var qualityWork by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.qualityWorkKm ?: session.log?.qualityWorkKm?.toString() ?: "") }
    var performedOn by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.performedOn ?: session.log?.performedOn?.toString() ?: if(done)state.activities.singleOrNull { it.id==session.linkedActivityId }?.startedAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?.toString() ?: "" else if(session.log==null)LocalDate.now().toString() else "") }
    var entries by rememberSaveable(plan.owner,session.id) { mutableStateOf(entryDraft?.visibleEntries(exercises) ?: JSONObject().apply { session.log?.sets?.forEach { r -> put("${r.exerciseId}|${r.set}|reps",r.reps?.toString() ?: "");put("${r.exerciseId}|${r.set}|kg",r.kg?.toString() ?: "");put("${r.exerciseId}|${r.set}|seconds",r.seconds?.toString() ?: "") } }.toString()) }
    var exerciseIndex by rememberSaveable(plan.owner,session.id) { mutableIntStateOf(0) };var formError by rememberSaveable(plan.owner,session.id) { mutableStateOf<String?>(null) }
    var missed by rememberSaveable(plan.owner,session.id) { mutableStateOf(false) };var reason by rememberSaveable(plan.owner,session.id) { mutableStateOf("Time") };var move by rememberSaveable(plan.owner,session.id) { mutableStateOf(false) };var moveDate by rememberSaveable(plan.owner,session.id) { mutableStateOf(session.date.toString()) }
    var restSeconds by rememberSaveable(plan.owner,session.id) { mutableIntStateOf(0) };var restRunning by rememberSaveable(plan.owner,session.id) { mutableStateOf(false) }
    var guided by rememberSaveable(plan.owner,session.id) { mutableStateOf(true) }
    val initialContent=remember(plan.owner,session.id) { listOf(minutes,distance,effort,notes,performedOn,entries,difficulty ?: "",discomfort ?: "",qualityWork) }
    val editClock=remember(plan.owner,session.id) { java.util.concurrent.atomic.AtomicLong(entryDraft?.editedAt ?: 0L) }
    fun nextInputStamp()=editClock.updateAndGet { maxOf(System.currentTimeMillis(),it+1) }
    val inputStamp=remember(minutes,distance,effort,notes,performedOn,entries,difficulty,discomfort,qualityWork,session.logRevision) { nextInputStamp() }
    val currentCandidate=if(active)WorkoutEntryDraft(plan.owner,plan.id,session.id,session.logRevision,minutes,distance,effort,notes,performedOn,entries,inputStamp,difficulty,discomfort,qualityWork)else null
    val latestCandidate=rememberUpdatedState(currentCandidate)
    val latestStagingInfo=rememberUpdatedState(active to session.logRevision)
    val savedInputKey=rememberUpdatedState(entryDraft?.contentKey)
    val flushInputs=rememberUpdatedState<()->Unit>({
        if(latestStagingInfo.value.first) {
            val value=WorkoutEntryDraft(plan.owner,plan.id,session.id,latestStagingInfo.value.second,minutes,distance,effort,notes,performedOn,entries,nextInputStamp(),difficulty,discomfort,qualityWork)
            if(value.contentKey!=initialContent || savedInputKey.value?.let { it!=value.contentKey }==true)actions.stageWorkoutDraft(value)
        }
    })
    LaunchedEffect(plan.owner,session.id) {
        snapshotFlow { listOf(minutes,distance,effort,notes,performedOn,entries,difficulty ?: "",discomfort ?: "",qualityWork) }.drop(1).debounce(500).collect {
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
        val actualDistance=number(distance,"distance in km")
        val actualWork=if(session.quality)number(qualityWork,"quality work distance") else null
        if(actualWork!=null && (actualWork<=0 || actualWork>120 || actualDistance!=null && actualWork>actualDistance))throw PlanConstraint("Quality work must be positive and cannot exceed the actual total distance.")
        return WorkoutLog(Instant.now(),number(minutes,"duration in minutes"),actualDistance,whole(effort,"effort from 1 to 10"),sets,notes,if(performedOn.isBlank())null else try {LocalDate.parse(performedOn)}catch(_:Exception){throw PlanConstraint("Choose a valid actual workout date.")},difficulty,discomfort,actualWork)
    }
    @Composable
    fun LinkedSourceRunCard(activityId: String) {
        SignalCard {
            Text("LINKED SOURCE RUN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            val source = state.activities.find { it.id == activityId }
            Text(source?.let { "${it.title} · ${it.dateLabel} · ${it.distanceLabel} · ${it.durationLabel}" } ?: "The linked source is not currently cached. Its link and original prescription remain saved.")
            Text("The manual link confirms your association, not whether every prescribed interval or effort was performed. Manage the link in Activities.", style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    fun PendingSuggestionCard(suggestion: SessionSuggestion) {
        TracksideWorkoutNeutralCard {
            Text(if (suggestion.accepted) "RECOVERY CHANGE ACCEPTED" else "SUGGESTED · YOUR CHOICE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(suggestion.reason)
            if (!suggestion.accepted && active) {
                SignalButton(if (suggestion.minutes == 0) "Choose rest for this session" else "Accept lighter alternative", { actions.acceptSuggestion(plan.id, plan.revision, session.id) }, !state.busy)
                Text("Original prescriptions remain saved. No catch-up workout is added.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    fun ActualWorkoutRecordFormCard() {
        TracksideWorkoutNeutralCard(Modifier.testTag("workout-record-form")) {
            Text(if (done) "SAVED WORKOUT" else "ACTUAL WORKOUT RECORD",
                style = MaterialTheme.typography.labelMedium,
                color = TracksidePalette.lime)
            if (active) Text("Entries auto-save locally as an unconfirmed draft.",
                style = MaterialTheme.typography.bodySmall,
                color = TracksidePalette.textMuted)
            Text("Enter only what you actually performed. Blank fields remain unrecorded.",
                style = MaterialTheme.typography.bodySmall,
                color = TracksidePalette.text)
            if (plannedRun) {
                TracksideWorkoutDateSelector(performedOn,
                    { performedOn = it.take(32) }, active)
            } else {
                NativeDateField("Actual workout date", performedOn,
                    { performedOn = it.take(32) }, active)
                Text("Choose the actual date; it may differ from the schedule.",
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(minutes, { minutes = it.take(64) }, label = { Text("Actual duration (minutes), optional") }, enabled = active, modifier = Modifier.fillMaxWidth().testTag("workout-actual-minutes"), colors = TracksideWorkoutInputColors())
            if (session.discipline == "run" || session.discipline == "cond") OutlinedTextField(distance, { distance = it.take(64) }, label = { Text("Actual distance (km), optional") }, enabled = active, modifier = Modifier.fillMaxWidth().testTag("workout-actual-distance"), colors = TracksideWorkoutInputColors())
            OutlinedTextField(effort, { effort = it.take(64) }, label = { Text("Effort 1–10, optional") }, enabled = (active || responseEditable) && !state.busy, modifier = Modifier.fillMaxWidth(), colors = TracksideWorkoutInputColors())
            if (session.discipline == "run" && plan.engineVersion == ENGINE15_VERSION) {
                CoachFeedbackChoices("How did completion feel?", listOf("As planned", "Easier", "Harder", "Could not finish"), difficulty, (active || responseEditable) && !state.busy) { difficulty = it }
                CoachFeedbackChoices("Any discomfort?", listOf("None", "Soreness", "Pain"), discomfort, (active || responseEditable) && !state.busy) { discomfort = it }
                if (session.quality) {
                    if (timeOnly) Text("Your current target is time-only. Record extra quality work only if you actually performed it; it will not establish progression evidence.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(qualityWork, { qualityWork = it.take(64) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }), label = { Text("Actual quality work (km), optional") }, enabled = active && !state.busy, modifier = Modifier.fillMaxWidth(), colors = TracksideWorkoutInputColors())
                    Text("Count only completed work repetitions; exclude warm-up, recovery and cool-down. Leave blank if unknown.", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("How did it feel?") }, enabled = active, modifier = Modifier.fillMaxWidth(), colors = TracksideWorkoutInputColors())
            session.log?.let { Text("Saved ${it.at} · ${it.sets.size} sets with recorded values", style = MaterialTheme.typography.bodySmall) }
        }
    }

    @Composable
    fun SaveWorkoutButtonsCard() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SignalButton("Save completed workout", { try { formError = null; actions.record(plan.id, plan.revision, session.id, log(), true) } catch (e: PlanConstraint) { formError = e.message } }, !state.busy)
            OutlinedButton(onClick = { try { formError = null; actions.record(plan.id, plan.revision, session.id, log(), false) } catch (e: PlanConstraint) { formError = e.message } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Save progress for offline reopening") }
            if (!plannedRun) Row {
                TextButton(onClick = { if (onMissed != null) onMissed() else missed = true }) { Text("Missed / skipped") }
                TextButton(onClick = { if (onMove != null) onMove() else move = true }, enabled = session.log == null && session.id !in plan.request.lockedSessionDays) { Text("Move workout") }
            }
        }
    }

    val executeWorkout = actions.executeWorkout
    val supportsExecution = session.discipline != "run" && exercises.isNotEmpty() && !rest &&
        !(session.suggestion?.accepted == true && session.suggestion.minutes == 0)
    val durableExecution = executeWorkout != null && supportsExecution
    var showPrescription by rememberSaveable(plan.owner, plan.id, session.id) { mutableStateOf(false) }
    val playerMode = session.playerMode()
    val defaultSetTable = playerMode in setOf(PlayerMode.Strength, PlayerMode.Calisthenics)
    var showFullPlayer by rememberSaveable(plan.owner, plan.id, session.id) { mutableStateOf(!defaultSetTable) }
    var scopedTimer by rememberSaveable(plan.owner, plan.id, session.id) { mutableStateOf(false) }
    if (executeWorkout != null && supportsExecution && !showPrescription && (active || done)) {
        var execution by remember(plan.owner, plan.id, session.id) { mutableStateOf<WorkoutExecution?>(null) }
        var executionError by remember(plan.owner, plan.id, session.id) { mutableStateOf<String?>(null) }
        var saving by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()
        suspend fun applyEvent(event: PlayerEvent?) {
            try {
                executionError = null
                execution = executeWorkout(plan.id, session.id, execution?.revision, event)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (invalid: PlanConstraint) {
                executionError = invalid.message
            } catch (_: IllegalArgumentException) {
                executionError = "Check the actual values you entered. If this persists, reopen the workout and retry."
            } catch (_: Exception) {
                executionError = "The workout could not be saved. Reopen it or retry."
            } finally {
                saving = false
            }
        }
        LaunchedEffect(plan.owner, plan.id, session.id) { applyEvent(null) }
        LaunchedEffect(session.swaps) {
            if (execution != null && session.swaps.isNotEmpty()) applyEvent(null)
        }
        if (active) {
            val currentExecution = execution
            if (currentExecution == null) {
                Screen(shown.effectiveTitle, "WORKOUT") {
                    item {
                        Text(executionError ?: "Opening workout…")
                        if (!saving) TextButton(onClick = { saving = true; scope.launch { applyEvent(null) } }) { Text("Retry") }
                        TextButton(onClick = onBack) { Text("Back to Training") }
                    }
                }
            } else if (
                !showFullPlayer &&
                !scopedTimer &&
                currentExecution.phase != PlayerPhase.Reviewing &&
                currentExecution.phase != PlayerPhase.Completed
            ) {
                CoachedSetTable(
                    plan = plan,
                    session = session,
                    execution = currentExecution,
                    allPlans = state.plans,
                    entries = entries,
                    onEntries = { entries = it },
                    busy = saving || state.busy,
                    error = executionError,
                    onBack = onBack,
                    onOpenFullPlayer = { showFullPlayer = true },
                    onOpenScopedTimer = { setIdx ->
                        val row = currentExecution.sets.getOrNull(setIdx)
                        if (row != null) {
                            val updated = runCatching { JSONObject(entries) }.getOrDefault(JSONObject())
                            updated.remove("${row.target.id}|${row.number}|seconds")
                            entries = updated.toString()
                        }
                        scopedTimer = true
                    },
                    onSwapHarder = { exId ->
                        actions.swapHarder(plan.id, plan.revision, session.id, exId)
                    },
                    onSwapEasier = { exId ->
                        actions.swap(plan.id, plan.revision, session.id, exId)
                    }
                ) { event ->
                    if (!saving && !state.busy) {
                        saving = true
                        scope.launch { applyEvent(event) }
                    }
                }
            } else {
                WorkoutPlayer(
                    execution = currentExecution,
                    entries = entries,
                    onEntries = { entries = it },
                    effort = effort,
                    onEffort = { effort = it },
                    notes = notes,
                    onNotes = { notes = it },
                    date = performedOn,
                    onDate = { performedOn = it },
                    minutes = minutes,
                    onMinutes = { minutes = it },
                    distance = distance.takeIf { session.discipline == "cond" },
                    onDistance = { distance = it },
                    busy = saving || state.busy,
                    saveError = executionError,
                    onBack = onBack,
                    onPrescription = { showPrescription = true },
                    mode = playerMode,
                    runnerDurability = session.title in setOf("Strength for Runners", "Lower / Runner Durability"),
                    scopedToTable = scopedTimer,
                    onBackToTable = if (scopedTimer) {
                        {
                            scopedTimer = false
                            showFullPlayer = false
                        }
                    } else null
                ) { event ->
                    if (!saving && !state.busy) {
                        saving = true
                        scope.launch { applyEvent(event) }
                    }
                }
            }
            return
        }
    }

    if (active && exercises.isNotEmpty() && guided && !durableExecution) {
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
    } else if (plannedRun) {
        val weekNumber = (ChronoUnit.DAYS.between(plan.request.start, session.date) / 7L + 1L)
            .coerceAtLeast(1L)
        val difficultyEditable = Engine1Engines.canManage(plan) &&
            plan.status in setOf("approved", "paused") &&
            session.status in setOf("planned", "in_progress")
        val listState = rememberLazyListState()
        LaunchedEffect(recordingOpen) {
            if (recordingOpen && listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = WorkoutDetailPalette.background,
            topBar = {
                WorkoutDetailTopBar(
                    weekNumber = weekNumber,
                    statusLabel = session.status,
                    onBack = onBack
                )
            },
            bottomBar = {
                if (!rest && session.status != "unassigned") {
                    WorkoutDetailBottomBar(
                        label = when {
                            recordingOpen -> "HIDE RECORDING FORM"
                            active -> "START WORKOUT"
                            else -> "APPROVE PLAN TO RECORD"
                        },
                        enabled = active,
                        onClick = { recordingOpen = !recordingOpen }
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("screen")
                    .semantics { paneTitle = "Workout details" },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val executionAnalysis = state.executionAnalyses[session.id].takeIf {
                    session.discipline == "run" && (session.linkedActivityId != null || session.status == "completed")
                }
                if (executionAnalysis != null) item { WorkoutExecutionVerdictCard(executionAnalysis) }
                item {
                    TracksidePlannedRunningPreview(
                        session = session,
                        target = current,
                        displayTitle = shown.effectiveTitle,
                        onBack = onBack,
                        planStart = plan.request.start,
                        onMissed = if (active && !state.busy) ({ if (onMissed != null) onMissed() else missed = true }) else null,
                        onMove = if (active && !state.busy && session.log == null &&
                            session.id !in plan.request.lockedSessionDays)
                            ({ if (onMove != null) onMove() else move = true })
                        else null,
                        onAdjustDifficulty = if (difficultyEditable) ({ delta ->
                            actions.adjustDifficulty(plan.id, plan.revision, session.id, delta)
                        }) else null,
                        busy = state.busy,
                        selectedStepIndex = selectedStepIndex,
                        onSelectStep = { selectedStepIndex = it },
                        planningPaceSecPerKm = plan.request.planningPace,
                        showInlineTopBar = false,
                        onExportToWatch = exportToWatch
                    )
                }
                if (executionAnalysis != null) item { ExecutionStepBreakdownCards(executionAnalysis) }
                session.linkedActivityId?.let { activityId ->
                    item { LinkedSourceRunCard(activityId) }
                }
                session.suggestion?.takeUnless { current.disclosure != null }?.let { suggestion ->
                    item { PendingSuggestionCard(suggestion) }
                }
                if (!rest && session.status != "unassigned" && recordingOpen) {
                    item { ActualWorkoutRecordFormCard() }
                    formError?.let { err ->
                        item { Text(err, color = MaterialTheme.colorScheme.error) }
                    }
                    if (active) {
                        item { SaveWorkoutButtonsCard() }
                    }
                }
            }
        }
    } else {
    Screen(
        shown.effectiveTitle,
        "${plannedDisciplineLabel(session).uppercase()} · ${session.status.uppercase()}",
        compactTitle=true,dense=false,
        bottomOverlayPadding=106.dp,
        hideHeader=false
    ) {
        val executionOutcome = state.workoutExecutionOutcomes[plan.id to session.id]
        if (executionOutcome?.prescription == PrescriptionOutcome.PARTIALLY_PERFORMED) item {
            TracksideWorkoutNeutralCard(Modifier.testTag("workout-prescription-outcome")) {
                Text("Partial · ${executionOutcome.confirmedSets} of ${executionOutcome.prescribedSets} sets",
                    color = TracksidePalette.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (crossDebrief != null) item { CoachDebriefCard(crossDebrief) }
        if (exportToWatch != null) item { RunningWorkoutFitExportButton(exportToWatch) }
        if(active && exercises.isNotEmpty()) item {
            SignalButton("Return to guided workout", { showPrescription = false; guided = true }, !state.busy)
        }
        if (active && showPrescription) item {
            Row {
                TextButton(onClick = { if (onMissed != null) onMissed() else missed = true }, enabled = !state.busy) { Text("Missed / skipped") }
                TextButton(onClick = { if (onMove != null) onMove() else move = true },
                    enabled = !state.busy && session.id !in plan.request.lockedSessionDays) { Text("Move workout") }
            }
        }
        item {
            TextButton(onClick=onBack) { Text("Back to Training") }
            Text("${session.date} · ${current.currentMinutes} planned min",
                style=MaterialTheme.typography.bodyMedium,
                color=TracksidePalette.textMuted)
            Text(shown.reason,style=MaterialTheme.typography.bodyLarge,
                color=TracksidePalette.textMuted)
        }
        item {
            val workoutCatalog = rememberAugmentedExerciseCatalog()
            val nonRunningFocus = remember(done, session.log, exercises, workoutCatalog, rest) {
                when {
                    session.discipline == "run" || rest -> MuscleFocusMap.EMPTY
                    done && session.log != null ->
                        MuscleFocusProjector.forConfirmedSession(session.log.sets, exercises, workoutCatalog)
                    exercises.isNotEmpty() ->
                        MuscleFocusProjector.forPlannedSession(exercises, workoutCatalog)
                    else -> MuscleFocusMap.EMPTY
                }
            }
            if (!nonRunningFocus.isEmpty) {
                val provenance = if (done && session.log != null) {
                    "Based on ${nonRunningFocus.setCount} confirmed sets"
                } else {
                    "Planned session focus"
                }
                SessionMuscleFocusSection(
                    focus = nonRunningFocus,
                    provenance = provenance
                )
            }
        }
        session.linkedActivityId?.let { activityId -> item { LinkedSourceRunCard(activityId) } }
        session.suggestion?.takeUnless { current.disclosure != null }?.let { suggestion -> item { PendingSuggestionCard(suggestion) } }
        if(!rest && Engine1Engines.canManage(plan) &&
            plan.status in setOf("approved","paused") &&
            session.status in setOf("planned","in_progress")) item {
            TracksideWorkoutDifficultyCard(
                session=session,
                busy=state.busy,
                onAdjust={ delta -> actions.adjustDifficulty(plan.id,plan.revision,session.id,delta) }
            )
        }
        item { SignalCard {
            Text("YOUR PRESCRIPTION",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            if (rest || timeOnly) {
                Text(if (rest) "Reviewed rest · 0 minutes" else "Time-only run · ${current.currentMinutes} minutes")
                current.currentSteps.forEach { Text(it) }
                Text("Original prescription · ${current.originalMinutes} min" + (current.originalDistanceKm?.let { " · $it km" } ?: ""), style=MaterialTheme.typography.bodySmall)
            }
            else if(session.suggestion?.accepted==true && current.disclosure == null && session.suggestion.minutes==0) Text("You chose rest for this session. The original prescription remains saved; no workout completion is inferred.")
            else if(session.suggestion?.accepted==true && current.disclosure == null) { Text("Keep any running conversational. Use an easier variation and fewer controlled resistance sets, or rest if needed. Stop at ${session.effectiveMinutes} minutes; do not chase the original distance or hard intervals.");Text("Original target: ${session.minutes} min · ${session.title}",style=MaterialTheme.typography.bodySmall) }
            else {current.currentSteps.forEach { Text(it) };current.currentDistanceTargetKm?.let { Text("Planned target: %.1f km. This is not a recorded distance.".format(it),style=MaterialTheme.typography.bodySmall) } }
        } }
        if(exercises.isNotEmpty()) {
            val ex=exercises[exerciseIndex.coerceIn(0,exercises.lastIndex)]
            val setCount=if(session.suggestion?.accepted==true) maxOf(1,(ex.sets*.7).toInt()) else ex.sets
            item { SignalCard {
                Text("${exerciseIndex+1} OF ${exercises.size} · ${ex.equipment.uppercase()}",style=MaterialTheme.typography.labelMedium,color=disciplineColor(session.discipline))
                Text(ex.name, style = MaterialTheme.typography.headlineSmall)
                Text(workoutDoseLabel(session, ex, setCount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                Text(ex.cue)
                session.swaps.find { it.replacement.id==ex.id }?.let { swap ->
                    val prefix = if(swap.replacement.id.endsWith("-progression")) "Harder progression of " else "Easier alternative to "
                    Text(prefix + session.exercises.first { it.id==swap.originalId }.name,style=MaterialTheme.typography.bodySmall)
                    Text(swap.reason,style=MaterialTheme.typography.bodySmall)
                }
                if(active && session.log?.sets?.none { it.exerciseId==ex.id }!=false && ExerciseAlternatives.easier(ex)!=null)OutlinedButton(onClick={swapDirection="EASIER";swapId=ex.id},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Choose an easier alternative") }
                if(active && session.log?.sets?.none { it.exerciseId==ex.id }!=false && ExerciseAlternatives.harder(ex)!=null)OutlinedButton(onClick={swapDirection="HARDER";swapId=ex.id},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Choose a harder progression") }
                if(!active) Text(if(done) "Actual values below are your saved records. Blank values were not recorded." else if(!Engine1Engines.canManage(plan))"This saved engine/policy is read-only here. Its original prescription is retained for viewing and export." else "Approve the plan before recording. Targets are not completed sets.",style=MaterialTheme.typography.bodySmall)
            } }
            if (!showPrescription) (1..setCount).forEach { i -> item {
                val data=JSONObject(entries);val prefix="${ex.id}|$i|"
                fun set(key:String,value:String) { entries=JSONObject(entries).put(prefix+key,value.take(64)).toString() }
                SignalCard { Text("Set $i",style=MaterialTheme.typography.titleMedium)
                    if(ex.seconds!=null) OutlinedTextField(data.optString(prefix+"seconds"),{set("seconds",it)},label={Text("Actual hold seconds")},enabled=active,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                    else OutlinedTextField(data.optString(prefix+"reps"),{set("reps",it)},label={Text("Actual reps")},enabled=active,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                    OutlinedTextField(data.optString(prefix+"kg"),{set("kg",it)},label={Text("Actual load (kg), optional")},enabled=active,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
            } }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={exerciseIndex--},enabled=exerciseIndex>0,modifier=Modifier.weight(1f)) { Text("Previous") }
                OutlinedButton(onClick={exerciseIndex++},enabled=exerciseIndex<exercises.lastIndex,modifier=Modifier.weight(1f)) { Text("Next exercise") }
            } }
            if(active && !showPrescription) item { SignalCard { Text("Rest timer · ${restSeconds/60}:${(restSeconds%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.titleLarge)
                Text("Pauses when you leave the app. Resume it here when ready.",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={restSeconds=ex.rest;restRunning=true}) { Text("Start rest") };TextButton(onClick={restRunning=!restRunning},enabled=restSeconds>0) { Text(if(restRunning) "Pause" else "Resume") } }
            } }
        }
        if (!showPrescription && !rest && session.status!="unassigned") item { ActualWorkoutRecordFormCard() }
        formError?.let { item { Text(it,color=MaterialTheme.colorScheme.error) } }
        if(active && !showPrescription) item { SaveWorkoutButtonsCard() }
        if(responseEditable) item {
            Text("Feedback is saved separately. Original workout measurements and prescriptions stay unchanged.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            SignalButton("Save run feedback",{try {formError=null;actions.saveRunResponse(plan.id,plan.revision,session.id,whole(effort,"effort from 1 to 10"),difficulty,discomfort)}catch(e:PlanConstraint){formError=e.message}},!state.busy)
        }
        if(done) item { Engine15CoachReviewCard(plan,actions,state.busy,state.message) }
        if(done) item { Text("Completion is your recorded confirmation, not a sensor-verified activity. It appears separately in workout history without invented pace, GPS or heart rate.",style=MaterialTheme.typography.bodySmall) }
    }
    }
    swapId?.let { id -> exercises.find { it.id==id }?.let { original ->
        val dir = if(swapDirection=="HARDER") ExerciseAlternatives.SwapDirection.HARDER else ExerciseAlternatives.SwapDirection.EASIER
        ExerciseAlternatives.alternative(original,dir)?.let { replacement ->
            val harderSwap = dir == ExerciseAlternatives.SwapDirection.HARDER
            AlertDialog(onDismissRequest={swapId=null},title={Text(if(harderSwap) "Review harder progression" else "Review easier alternative")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(replacement.name,style=MaterialTheme.typography.titleLarge)
                Text("${replacement.sets} sets · ${replacement.reps?.let { "$it reps" } ?: "${replacement.seconds} seconds"} · rest ${replacement.rest} seconds")
                Text(replacement.cue);Text(ExerciseAlternatives.reasonFor(dir))
                Text("Any unsaved entries for this exercise will be cleared. Previously saved sets cannot be replaced.",style=MaterialTheme.typography.bodySmall)
            }},confirmButton={TextButton(onClick={pendingSwap=id;if(harderSwap) actions.swapHarder(plan.id,plan.revision,session.id,id) else actions.swap(plan.id,plan.revision,session.id,id);swapId=null}) { Text(if(harderSwap) "Use harder progression" else "Use easier alternative") }},dismissButton={TextButton(onClick={swapId=null}) { Text("Keep original") }})
        }
    } }
    if(missed && onMissed==null) AlertDialog(onDismissRequest={missed=false},title={Text("Why was this session missed?")},text={ Column { ChoiceChips(listOf("Time","Fatigue","Soreness","Illness","Other"),listOf("Time","Fatigue","Soreness","Illness","Other").indexOf(reason)) { reason=listOf("Time","Fatigue","Soreness","Illness","Other")[it] };Text("No catch-up volume is added. Any recovery suggestion requires your explicit choice.") } },confirmButton={TextButton(onClick={actions.missed(plan.id,plan.revision,session.id,reason,false);missed=false}) { Text("Mark missed") }},dismissButton={Row { TextButton(onClick={actions.missed(plan.id,plan.revision,session.id,reason,true);missed=false}) { Text("Skip") };TextButton(onClick={missed=false}) { Text("Cancel") } }})
    if(move && onMove==null) AlertDialog(onDismissRequest={move=false},title={Text("Move workout")},text={Column { NativeDateField("Scheduled date",moveDate,{moveDate=it});Text("Available-day, long-run-day and recovery conflicts are checked before saving.");formError?.let { Text(it) } }},confirmButton={TextButton(onClick={try {actions.move(plan.id,plan.revision,session.id,LocalDate.parse(moveDate));move=false}catch(e:PlanConstraint){formError=e.message}catch(_:java.time.format.DateTimeParseException){formError="Enter a valid YYYY-MM-DD date."}}) { Text("Save move") }},dismissButton={TextButton(onClick={move=false}) { Text("Cancel") }})
}

internal fun workoutDoseLabel(session: PlannedSession, exercise: ExercisePrescription, sets: Int): String {
    val contacts = session.kind == "structured-plyometrics"
    val timed = session.discipline == "cond"
    val unit = when {
        contacts -> "${exercise.reps} contacts"
        exercise.seconds != null -> "${exercise.seconds} s ${if (timed) "work" else "hold"}"
        else -> "${exercise.reps} reps"
    }
    return "$sets ${if (timed) "rounds" else "sets"} · $unit · ${exercise.rest} s rest"
}
