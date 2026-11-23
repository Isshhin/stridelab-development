@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package io.stridelab.privatealpha

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

private val black = Color.Black
private val slate = Color(0xFF151A20)
private val muted = Color(0xFF9CAABB)
private val lime = Color(0xFFD6FA70)

internal enum class PlayerMode { Strength, Calisthenics, Plyometrics, Isometrics, Conditioning, Emom, Amrap }

internal fun PlannedSession.playerMode() = when {
    discipline == "cond" && title.startsWith("EMOM") -> PlayerMode.Emom
    discipline == "cond" && title.startsWith("AMRAP") -> PlayerMode.Amrap
    discipline == "cond" -> PlayerMode.Conditioning
    discipline == "plyo" || kind == "structured-plyometrics" -> PlayerMode.Plyometrics
    discipline == "iso" || title == "Isometrics" -> PlayerMode.Isometrics
    discipline == "cal" -> PlayerMode.Calisthenics
    else -> PlayerMode.Strength
}

@Composable
internal fun WorkoutPlayer(
    execution: WorkoutExecution, entries: String, onEntries: (String) -> Unit,
    effort: String, onEffort: (String) -> Unit, notes: String, onNotes: (String) -> Unit,
    date: String, onDate: (String) -> Unit, minutes: String, onMinutes: (String) -> Unit,
    distance: String?, onDistance: (String) -> Unit,
    busy: Boolean, saveError: String?, onBack: () -> Unit, onPrescription: () -> Unit,
    mode: PlayerMode, runnerDurability: Boolean,
    dispatch: (PlayerEvent) -> Unit
) {
    val step = execution.current
    val timer = execution.timer
    val resting = execution.phase == PlayerPhase.Resting
    val reviewing = execution.phase == PlayerPhase.Reviewing
    val finished = execution.phase == PlayerPhase.Completed
    val timed = step.target.seconds != null
    val rounds = mode in setOf(PlayerMode.Conditioning, PlayerMode.Emom, PlayerMode.Amrap)
    val plyo = mode == PlayerMode.Plyometrics
    val enabled = !busy && execution.finalLog == null
    val previousIndex = execution.sets.indices.lastOrNull {
        execution.sets[it].actual != null && (resting && it == execution.cursor || it < execution.cursor)
    }
    val nextIndex = execution.sets.indices.firstOrNull { it > execution.cursor && execution.sets[it].disposition == SetDisposition.PENDING }
        ?: execution.sets.indices.firstOrNull {
            it != execution.cursor && execution.sets[it].disposition == SetDisposition.PENDING && execution.sets[it].measuredWorkMs == null
        }
    var mutedAudio by rememberSaveable(execution.executionId) { mutableStateOf(false) }
    var exit by rememberSaveable(execution.executionId) { mutableStateOf(false) }
    var editIndex by rememberSaveable(execution.executionId) { mutableStateOf<Int?>(null) }
    var guide by rememberSaveable(execution.executionId) { mutableStateOf(false) }
    var error by rememberSaveable(execution.executionId) { mutableStateOf<String?>(null) }
    var sessionRir by rememberSaveable(execution.executionId) { mutableStateOf("") }
    val foreground by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val isResumed = foreground.isAtLeast(Lifecycle.State.RESUMED)
    val now by produceState(SystemClock.elapsedRealtime(), timer?.deadlineMs, isResumed) {
        value = SystemClock.elapsedRealtime()
        while (isResumed && timer?.deadlineMs != null && timer.remaining(value) > 0) {
            val remaining = timer.remaining(value)
            delay(((remaining - 1) % 1000) + 1)
            value = SystemClock.elapsedRealtime()
        }
    }
    val remaining = timer?.remaining(now)
    val displayedSeconds = ((remaining ?: (if (resting) 0 else step.target.seconds ?: 0) * 1000L) + 999) / 1000
    val running = timer?.deadlineMs != null && remaining != null && remaining > 0
    val expired = timer?.deadlineMs != null && remaining == 0L
    fun measuredSeconds(index: Int): Int? {
        val row = execution.sets[index]
        val work = row.measuredWorkMs ?: if (index == execution.cursor && !resting && !reviewing)
            timer?.elapsed(SystemClock.elapsedRealtime()) else null
        return work?.div(1000)?.toInt()?.takeIf { it > 0 }
    }
    PlayerDeviceEffects(
        keepAwake = isResumed && (execution.phase == PlayerPhase.ActiveWork || resting),
        foreground = isResumed, muted = mutedAudio, resting = resting, deadline = timer?.deadlineMs,
        seconds = displayedSeconds, expired = expired, cursor = execution.cursor
    )
    LaunchedEffect(expired, isResumed, resting, enabled, mode, exit, editIndex) {
        if (expired && isResumed && enabled && mode == PlayerMode.Emom && !exit && editIndex == null && !reviewing && !finished) {
            delay(150)
            dispatch(if (resting) PlayerEvent.AdvanceEmom else PlayerEvent.EmomWorkElapsed)
        }
    }

    fun draft(row: ExecutionSet, field: String, fallback: String = ""): String =
        JSONObject(entries).optString("${row.target.id}|${row.number}|$field", fallback)
    fun setDraft(row: ExecutionSet, field: String, value: String) {
        onEntries(JSONObject(entries).put("${row.target.id}|${row.number}|$field", value.take(64)).toString())
    }
    val repCount = draft(step, "reps", (step.actual?.reps ?: step.target.reps ?: 0).toString()).toIntOrNull() ?: 0
    fun confirm(index: Int) {
        val row = execution.sets[index]
        try {
            val editingRecorded = row.actual != null || reviewing
            val seconds = draft(row, "seconds", (row.actual?.seconds ?: measuredSeconds(index))?.toString().orEmpty())
                .takeIf(String::isNotBlank)?.toInt()
            val reps = draft(row, "reps", if (row.target.seconds == null) (row.actual?.reps ?: row.target.reps)?.toString().orEmpty() else row.actual?.reps?.toString().orEmpty())
                .takeIf(String::isNotBlank)?.toInt()
            val actual = SetActual(reps, draft(row, "kg", row.actual?.kg?.toString().orEmpty()).takeIf(String::isNotBlank)?.toDouble(), seconds,
                draft(row, "rir", row.actual?.rir?.toString().orEmpty()).takeIf(String::isNotBlank)?.toInt())
            require(if (row.target.seconds == null) (actual.reps ?: 0) > 0 else (actual.seconds ?: 0) > 0)
            error = null
            editIndex = null
            dispatch(if (editingRecorded) PlayerEvent.EditPreviousSet(index, actual) else PlayerEvent.CompleteSet(actual, mode == PlayerMode.Emom))
        } catch (_: IllegalArgumentException) {
            error = "Enter a valid actual set."
            editIndex = index
        }
    }

    BackHandler(enabled = !finished && execution.finalLog == null) { exit = true }
    Surface(Modifier.fillMaxSize().testTag("workout-player"), color = black, contentColor = Color.White) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            PlayerHeader(
                step = execution.cursor + 1, total = execution.sets.size, audioMuted = mutedAudio,
                settled = { execution.sets[it].disposition != SetDisposition.PENDING },
                onMute = { mutedAudio = !mutedAudio }, onExit = { if (finished || execution.finalLog != null) onBack() else exit = true }
            )
            if (resting && previousIndex != null) {
                TextButton(onClick = { editIndex = previousIndex }, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Edit previous set", color = muted)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (resting) Arrangement.Center else Arrangement.spacedBy(12.dp)) {
                when {
                    finished -> Text(when {
                        execution.lifecycle == ExecutionLifecycle.ABANDONED -> "Workout abandoned"
                        execution.finalLog == null -> "No work recorded"
                        else -> "Workout saved"
                    },
                        style = MaterialTheme.typography.headlineMedium)
                    reviewing -> {
                        val elapsed = remember(execution.executionId, execution.phase) { execution.openedAtMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0) / 1000 } }
                        Text("Workout summary", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
                        val performed = execution.sets.mapNotNull { it.actual }
                        val reps = execution.sets.sumOf { if (!plyo || it.isJump()) it.actual?.reps ?: 0 else 0 }
                        val seconds = performed.sumOf { it.seconds ?: 0 }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${performed.size} ${if (rounds) "rounds" else "sets"}", color = lime)
                            if (reps > 0) Text("$reps ${if (plyo) "contacts" else "reps"}")
                            if (seconds > 0) Text("${clockLabel(seconds.toLong())} ${if (rounds) "work" else "holds"}")
                            if (elapsed != null) Text("${clockLabel(elapsed)} elapsed", color = muted)
                        }
                        Text("Session effort", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..10).forEach { rating ->
                                FilterChip(selected = effort == rating.toString(), onClick = { onEffort(if (effort == rating.toString()) "" else rating.toString()) },
                                    label = { Text(rating.toString()) }, enabled = enabled)
                            }
                        }
                        OutlinedTextField(notes, { onNotes(it.take(2000)) }, enabled = enabled, label = { Text("Notes, optional") }, modifier = Modifier.fillMaxWidth())
                        if (runnerDurability) OutlinedTextField(sessionRir, { sessionRir = it.take(2) }, enabled = enabled,
                            label = { Text("Reps in reserve, optional") }, modifier = Modifier.fillMaxWidth())
                        NativeDateField("Actual workout date", date, { onDate(it.take(32)) }, enabled)
                        OutlinedTextField(minutes, { onMinutes(it.take(64)) }, enabled = enabled,
                            label = { Text("Actual duration (minutes), optional") }, modifier = Modifier.fillMaxWidth())
                        if (distance != null) OutlinedTextField(distance, { onDistance(it.take(64)) }, enabled = enabled,
                            label = { Text("Actual distance (km), optional") }, modifier = Modifier.fillMaxWidth())
                        execution.sets.forEachIndexed { index, row ->
                            if (row.actual != null || row.disposition == SetDisposition.PENDING && row.measuredWorkMs != null)
                                TextButton(onClick = { editIndex = index }, enabled = enabled) {
                                    val label = if (rounds) "Round" else "Set"
                                    Text("${row.target.name} · $label ${row.number}" + if (row.actual == null) " · Confirm actuals" else "")
                                }
                        }
                        if (execution.sets.any { it.disposition == SetDisposition.PENDING })
                            TextButton(onClick = { dispatch(PlayerEvent.Continue) }, enabled = enabled) { Text("Resume workout", color = muted) }
                    }
                    resting -> {
                        Text("REST", color = lime, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
                        PlayerNumber(clockLabel(displayedSeconds), "Rest countdown")
                        if (mode == PlayerMode.Emom && step.actual == null && step.measuredWorkMs != null)
                            SlateButton("Confirm round", enabled) { editIndex = execution.cursor }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SlateButton("+15s", enabled, Modifier.testTag("player-extend-rest")) { dispatch(PlayerEvent.ExtendRest) }
                            SlateButton("Skip Rest", enabled, Modifier.testTag("player-skip-rest")) { dispatch(PlayerEvent.SkipRest) }
                            if (displayedSeconds > 0) TextButton(onClick = {
                                dispatch(if (running) PlayerEvent.PauseTimer else PlayerEvent.ResumeTimer)
                            }, enabled = enabled) { Text(if (running) "Pause" else "Resume", color = muted) }
                        }
                    }
                    else -> {
                        val count = execution.sets.count { it.target.id == step.target.id }
                        Text(if (rounds) "ROUND ${execution.cursor + 1} / ${execution.sets.size}" else "SET ${step.number} OF $count",
                            color = lime, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                        Text(step.target.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.testTag("player-exercise"))
                        Text(step.target.cue, color = muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        if (timed) {
                            PlayerNumber(clockLabel(displayedSeconds), "Work countdown")
                            LinearProgressIndicator(
                                progress = { if (timer == null || timer.budgetMs == 0L) 1f
                                    else (remaining ?: 0L).toFloat() / timer.budgetMs },
                                modifier = Modifier.fillMaxWidth(.72f), color = lime, trackColor = slate
                            )
                            if (expired) Text(if (mode == PlayerMode.Amrap) "Time. Confirm this round and record the reps you completed."
                                else "Time. Confirm the seconds you completed.", color = muted, textAlign = TextAlign.Center)
                            else SlateButton(if (running) "Pause" else if (timer == null) "Start" else "Resume", enabled) {
                                dispatch(when {
                                    running -> PlayerEvent.PauseTimer
                                    timer == null -> PlayerEvent.StartWorkTimer
                                    else -> PlayerEvent.ResumeTimer
                                })
                            }
                            if (mode == PlayerMode.Amrap) SlateButton("Enter round results", enabled) { editIndex = execution.cursor }
                        } else {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                                SlateButton("−", enabled && repCount > 1, Modifier.semantics { contentDescription = "Decrease reps" }) {
                                    setDraft(step, "reps", (repCount - 1).toString())
                                }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    PlayerNumber(repCount.toString(), if (plyo && step.isJump()) "Contacts" else "Repetitions")
                                }
                                SlateButton("+", enabled && repCount < 500, Modifier.semantics { contentDescription = "Increase reps" }) {
                                    setDraft(step, "reps", (repCount + 1).toString())
                                }
                            }
                            Text(if (plyo && step.isJump()) "CONTACTS" else "REPS", color = muted, letterSpacing = 3.sp)
                            val load = draft(step, "kg", step.actual?.kg?.toString().orEmpty())
                            SlateButton(if (load.isBlank()) "Add load" else "$load kg", enabled) { editIndex = execution.cursor }
                        }
                        if (plyo) {
                            val jumps = execution.sets.filter { it.isJump() }
                            val cap = jumps.sumOf { it.target.reps ?: 0 }
                            if (cap > 0) Text("${jumps.sumOf { it.actual?.reps ?: 0 }} / $cap contacts", color = lime,
                                modifier = Modifier.testTag("player-contact-count"))
                        }
                        if (previousIndex != null) TextButton(onClick = { editIndex = previousIndex }, enabled = enabled) {
                            Text("Edit previous set", color = muted)
                        }
                        TextButton(onClick = { guide = true }, enabled = enabled) { Text("Form guide", color = muted) }
                        if (execution.canChangePrescription) TextButton(onClick = onPrescription, enabled = enabled) {
                            Text("Workout details and alternatives", color = muted)
                        }
                    }
                }
                (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            when {
                finished && execution.finalLog == null -> {
                    PlayerPrimary("Start again", !busy) { dispatch(PlayerEvent.StartSession) }
                    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Back to Training", color = muted)
                    }
                }
                finished -> PlayerPrimary("Back to Training", true, action = onBack)
                reviewing -> PlayerPrimary(
                    if (execution.outcome.confirmedSets > 0) "Save & View Debrief" else "Finish without logging",
                    !busy
                ) {
                    if (execution.finalLog != null) {
                        dispatch(PlayerEvent.FinalizeSession(Instant.now(), null, ""))
                    } else try {
                        val rir = sessionRir.takeIf(String::isNotBlank)?.toInt()
                        require(rir == null || rir in 0..10)
                        val logNotes = if (rir == null || !runnerDurability) notes else
                            listOf(notes.lineSequence().filterNot { it.trimStart().startsWith("RIR:", ignoreCase = true) }.joinToString("\n").take(1989),
                                "RIR: $rir").filter(String::isNotBlank).joinToString("\n")
                        val event = PlayerEvent.FinalizeSession(Instant.now(), effort.toIntOrNull(), logNotes,
                            date.takeIf(String::isNotBlank)?.let(LocalDate::parse), minutes.takeIf(String::isNotBlank)?.toDouble(),
                            distance?.takeIf(String::isNotBlank)?.toDouble())
                        error = null
                        dispatch(event)
                    } catch (_: RuntimeException) {
                        error = "Check the date, duration and effort you entered."
                    }
                }
                resting -> NextUp(nextIndex?.let { execution.sets[it] }, nextIndex?.let { draft(execution.sets[it], "kg") }.orEmpty())
                step.disposition == SetDisposition.PENDING -> {
                    PlayerPrimary(if (mode == PlayerMode.Amrap && expired) "CONFIRM ROUND" else "DONE SET",
                        enabled, Modifier.testTag("player-done-set")) { if (mode == PlayerMode.Amrap && expired) editIndex = execution.cursor else confirm(execution.cursor) }
                    TextButton(onClick = { dispatch(PlayerEvent.SkipSet) }, enabled = enabled,
                        modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip Set", color = muted) }
                }
                else -> PlayerPrimary("Continue", enabled) { dispatch(PlayerEvent.Browse(1)) }
            }
            if (resting && nextIndex == null && !running)
                TextButton(onClick = { dispatch(PlayerEvent.SkipRest) }, enabled = enabled,
                    modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Review workout", color = lime) }
        }
    }

    if (exit) ModalBottomSheet(onDismissRequest = { exit = false }, containerColor = slate, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Leave this workout?", style = MaterialTheme.typography.titleLarge)
            Text("Finish early to review and save confirmed sets.", color = muted)
            PlayerPrimary("Resume", !busy) { exit = false }
            TextButton(onClick = { exit = false; dispatch(PlayerEvent.FinishEarly) }, enabled = enabled) { Text("Finish Early", color = lime) }
            TextButton(onClick = { exit = false; dispatch(PlayerEvent.AbandonSession) }, enabled = enabled) { Text("Abandon Session", color = Color(0xFFFFB4A8)) }
            TextButton(onClick = onBack, enabled = !busy) { Text("Leave and resume later", color = muted) }
        }
    }
    editIndex?.let { index ->
        val row = execution.sets[index]
        ModalBottomSheet(onDismissRequest = { editIndex = null; error = null }, containerColor = slate, contentColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (row.actual != null) "Edit previous set" else "Confirm actual set", style = MaterialTheme.typography.titleLarge)
                Text(row.target.name, color = muted)
                if (row.target.seconds != null) OutlinedTextField(
                    draft(row, "seconds", (row.actual?.seconds ?: measuredSeconds(index))?.toString().orEmpty()),
                    { setDraft(row, "seconds", it) }, label = { Text("Actual work seconds") }, modifier = Modifier.fillMaxWidth())
                if (row.target.seconds == null || mode == PlayerMode.Amrap) OutlinedTextField(
                    draft(row, "reps", (row.actual?.reps ?: row.target.reps)?.toString().orEmpty()),
                    { setDraft(row, "reps", it) }, label = { Text(if (plyo && row.isJump()) "Actual contacts" else "Actual reps") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft(row, "kg", row.actual?.kg?.toString().orEmpty()), { setDraft(row, "kg", it) },
                    label = { Text("Load (kg), optional") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                PlayerPrimary(if (row.actual != null) "Save actuals" else "Confirm set", enabled) { confirm(index) }
                if (row.actual != null && (timer == null || resting && execution.cursor == index)) {
                    TextButton(onClick = {
                        val updated = JSONObject(entries)
                        listOf("reps", "seconds", "kg", "rir").forEach { updated.remove("${row.target.id}|${row.number}|$it") }
                        onEntries(updated.toString())
                        editIndex = null
                        dispatch(PlayerEvent.EditPreviousSet(index, null))
                    }, enabled = enabled) { Text("Undo set", color = lime) }
                }
            }
        }
    }
    if (guide) PlayerFormGuide(step.target, onClose = { guide = false })
}

@Composable private fun PlayerHeader(step: Int, total: Int, audioMuted: Boolean, settled: (Int) -> Boolean, onMute: () -> Unit, onExit: () -> Unit) {
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val gap = minOf(3.dp.toPx(), size.width / (total * 3))
        val width = (size.width - (total - 1) * gap) / total
        repeat(total) { index ->
            val color = if (settled(index)) lime else if (index == step - 1) lime.copy(alpha = .55f) else slate
            drawRoundRect(color, Offset(index * (width + gap), 0f), Size(width, size.height), CornerRadius(2.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onExit, modifier = Modifier.semantics { contentDescription = "Exit workout" }) { Text("×", fontSize = 29.sp) }
        Text("Step $step / $total", color = muted, fontWeight = FontWeight.Medium)
        TextButton(onClick = onMute) { Text(if (audioMuted) "Unmute" else "Mute", color = muted) }
    }
}

private object WorkoutPlayerPalette {
    val mutedText = Color(0xFF9CAABB)
}

@Composable private fun PlayerNumber(value: String, label: String) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val size = (maxWidth.value / (value.length.coerceAtLeast(2) * .67f * fontScale)).coerceIn(30f, 100f)
        Text(value, textAlign = TextAlign.Center, maxLines = 1,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFeatureSettings = "tnum", fontSize = size.sp, lineHeight = (size * 1.05f).sp,
                fontWeight = FontWeight.SemiBold
            ), modifier = Modifier.semantics { contentDescription = "$label $value" })
    }
}

@Composable private fun SlateButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, action: () -> Unit) =
    Button(action, modifier.heightIn(min = 48.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = slate, contentColor = Color.White)) { Text(label, fontWeight = FontWeight.SemiBold) }

@Composable private fun PlayerPrimary(label: String, enabled: Boolean, modifier: Modifier = Modifier, action: () -> Unit) =
    Button(action, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = lime, contentColor = black)) { Text(label, fontWeight = FontWeight.Bold) }

@Composable private fun NextUp(next: ExecutionSet?, load: String) {
    Column(Modifier.fillMaxWidth().background(slate, RoundedCornerShape(16.dp)).testTag("player-next-up").padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("NEXT UP", color = muted, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Text(next?.target?.name ?: "Workout review", style = MaterialTheme.typography.titleMedium)
        if (next != null) Text(listOfNotNull("Set ${next.number}", next.target.seconds?.let { "${clockLabel(it.toLong())} work" }
            ?: next.target.reps?.let { "$it reps" }, load.takeIf(String::isNotBlank)?.let { "$it kg" }).joinToString(" · "), color = muted)
    }
}

private fun ExecutionSet.isJump() = target.id in setOf("pogo-hop", "line-bound")
private fun clockLabel(seconds: Long) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
private fun PlayerDeviceEffects(
    keepAwake: Boolean, foreground: Boolean, muted: Boolean, resting: Boolean, deadline: Long?,
    seconds: Long, expired: Boolean, cursor: Int
) {
    val context = LocalContext.current
    val view = LocalView.current
    val feedback = remember(context, view) { PlayerAudioFeedback(context, view) }
    val window = context.activity()?.window
    DisposableEffect(window, keepAwake) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val owned = keepAwake && window != null && window.attributes.flags and flag == 0
        if (owned) window?.addFlags(flag)
        onDispose { if (owned) window?.clearFlags(flag) }
    }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    var lastCue by remember { mutableStateOf<String?>(null) }
    var observedRunning by remember(deadline, cursor, resting) { mutableStateOf(false) }
    LaunchedEffect(deadline, seconds, foreground, cursor, resting) {
        if (!foreground || deadline == null) return@LaunchedEffect
        val cue = when {
            expired && observedRunning -> if (resting) PlayerCue.RestComplete else PlayerCue.WorkComplete
            seconds in 1..3 -> PlayerCue.Countdown
            !resting && seconds > 3 && !observedRunning -> PlayerCue.WorkStart
            else -> null
        }
        if (seconds > 0) observedRunning = true
        val key = "$cursor/$resting/$deadline/$seconds"
        if (cue != null && lastCue != key) {
            lastCue = key
            feedback.play(cue, muted)
        }
    }
}

@Composable private fun PlayerFormGuide(exercise: ExercisePrescription, onClose: () -> Unit) {
    val context = LocalContext.current
    val entry by produceState<LibraryEntry?>(null, context, exercise.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { readLibrary(context).firstOrNull { it.id == exercise.id && !it.guide } }.getOrNull()
        }
    }
    val exact = entry?.raw?.optJSONObject("image")?.optJSONArray("fit")?.optString(0) in setOf("exact", "schematic") ||
        (exercise.id == "e4" && exercise.name == "Push-up") || (exercise.id == "e2" && exercise.name == "Controlled pull-up")
    AlertDialog(onDismissRequest = onClose, title = { Text(exercise.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exact) entry?.let { LibraryImage(it, compact = true) }
                Text(exercise.cue)
                entry?.list("tech")?.forEach { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Back to workout") } })
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.activity()
    else -> null
}
