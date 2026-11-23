package io.stridelab.privatealpha

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

internal val freeformPalette = StrideLabVisualPalette

internal val ManualDiscipline.markColor: Color get() = when (this) {
    ManualDiscipline.PLYOMETRICS -> freeformPalette.moderateAmber
    ManualDiscipline.ISOMETRICS -> freeformPalette.easyTeal
    else -> freeformPalette.disciplineColor(trainingTrackKey)
}

@Composable
internal fun FreeformPage(title: String, subtitle: String?, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(freeformPalette.amoledBlack)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = freeformPalette.ink) }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = freeformPalette.ink)
                if (subtitle != null) Text(subtitle, color = freeformPalette.secondary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider(color = freeformPalette.border)
        content()
    }
}

@Composable
internal fun FreeformPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = freeformPalette.surface, border = BorderStroke(1.dp, freeformPalette.border)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun FreeformPrimary(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = freeformPalette.lime, contentColor = Color.Black)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun FreeformError(message: String?) {
    if (message != null) Text(message, color = freeformPalette.highCoral,
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("freeform-error"))
}

internal suspend fun <T> freeformAttempt(action: suspend () -> T): Result<T> =
    try {
        Result.success(action())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

internal fun Throwable.freeformMessage(fallback: String): String =
    if (this is IllegalArgumentException || this is IllegalStateException) {
        message?.takeUnless { it == "Failed requirement." || it == "Check failed." } ?: fallback
    } else fallback

@Composable
internal fun FreeformHub(
    owner: String,
    board: FreeformBoard?,
    readError: String?,
    access: FreeformAccess,
    back: () -> Unit,
    retry: () -> Unit,
    update: (FreeformWorkout) -> Unit,
    restored: (FreeformBoard) -> Unit,
    openTemplates: () -> Unit,
    openDraft: (String) -> Unit
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.Main.immediate) {
            working = true
            error = null
            message = null
            freeformAttempt {
                val raw = access.export(owner)
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openOutputStream(uri, "wt")).use { output ->
                        output.write(raw.toByteArray(Charsets.UTF_8))
                    }
                }
            }.onSuccess { message = "Workout backup saved." }
                .onFailure { error = it.freeformMessage("Could not save the workout backup.") }
            working = false
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.Main.immediate) {
            working = true
            error = null
            message = null
            freeformAttempt {
                val raw = withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openInputStream(uri)).use { input ->
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(bytes.size() + count <= ManualDisciplineArchive.MAX_FILE_BYTES) {
                                "This workout backup is too large."
                            }
                            bytes.write(buffer, 0, count)
                        }
                        bytes.toString(Charsets.UTF_8.name())
                    }
                }
                val added = access.restore(owner, raw)
                restored(access.read(owner))
                added
            }.onSuccess { message = if (it == 0) "These workouts are already saved." else "Workout backup restored." }
                .onFailure { error = it.freeformMessage("Could not restore this workout backup.") }
            working = false
        }
    }

    FreeformPage("Workouts", null, back) {
        val drafts = board?.workouts.orEmpty().filterNot { it.isComplete }
        LazyColumn(
            Modifier.fillMaxSize().testTag("freeform-hub"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (board == null && readError == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (readError != null) item {
                FreeformError(readError)
                TextButton(onClick = retry) { Text("Retry") }
            }
            if (drafts.isNotEmpty()) {
                item { Text("In progress", style = MaterialTheme.typography.titleMedium, color = freeformPalette.ink) }
                items(drafts, key = { it.id }) { draft ->
                    FreeformTile(
                        title = "Resume ${draft.title.lowercase()}",
                        supporting = draft.discipline.label,
                        color = draft.discipline.markColor,
                        onClick = { openDraft(draft.id) },
                        modifier = Modifier.testTag("freeform-resume")
                    )
                }
            }
            item {
                FreeformPrimary(
                    if (choosing) "Hide disciplines" else "Start empty workout", { choosing = !choosing }, Modifier.fillMaxWidth().testTag("freeform-log"),
                    enabled = board != null && !working
                )
            }
            if (choosing) items(ManualDiscipline.entries, key = { it.name }) { discipline ->
                FreeformTile(
                    title = discipline.label,
                    color = discipline.markColor,
                    modifier = Modifier.testTag("freeform-discipline-${discipline.name}"),
                    onClick = { if (!working && board != null) scope.launch(Dispatchers.Main.immediate) {
                        working = true
                        error = null
                        val draft = FreeformWorkout.start(owner, discipline, LocalDate.now(), System.currentTimeMillis())
                        freeformAttempt { access.start(draft) }.onSuccess {
                            update(it)
                            openDraft(it.id)
                        }.onFailure { error = it.freeformMessage("Could not start the workout. Try again.") }
                        working = false
                    } }
                )
            }
            item {
                val count = board?.templates?.size ?: 0
                if (board != null) FreeformTile(
                    title = "Workout templates",
                    supporting = if (count == 0) "Create a routine you can reuse" else "$count saved",
                    color = freeformPalette.secondary,
                    modifier = Modifier.testTag("freeform-open-templates"),
                    onClick = openTemplates
                )
            }
            item {
                TextButton(onClick = { showBackup = !showBackup }) { Text("Workout backup", color = freeformPalette.secondary) }
                if (showBackup) {
                    Text("Restore workouts to the same profile.", color = freeformPalette.secondary)
                    TextButton(onClick = { exporter.launch("stridelab-workouts.json") }, enabled = board != null && !working) {
                        Text("Export workout backup")
                    }
                    TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        enabled = board != null && !working) { Text("Restore workout backup") }
                }
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message != null) Text(message.orEmpty(), color = freeformPalette.secondary)
                FreeformError(error)
            }
        }
    }
}

@Composable
internal fun FreeformTile(
    title: String, supporting: String? = null, color: Color, onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        color = freeformPalette.surface, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, freeformPalette.border)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(8.dp).background(color, RoundedCornerShape(3.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = freeformPalette.ink, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                if (supporting != null) Text(supporting, color = freeformPalette.secondary,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = color, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
internal fun FreeformHistoryDetail(
    workout: FreeformWorkout?, legacy: ManualDisciplineSession?, loading: Boolean,
    onSaveAsTemplate: (() -> Unit)? = null, back: () -> Unit
) {
    val title = workout?.title ?: legacy?.title ?: "Workout"
    val subtitle = workout?.let { "${it.discipline.label} · Freeform · ${it.performedOn}" }
        ?: legacy?.let { "${it.discipline.label} · Manual · ${it.performedOn}" }
    FreeformPage(title, subtitle, back) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (workout != null) {
                item {
                    FreeformPanel {
                        Text(workout.summary(), color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("freeform-history-summary"))
                        if (workout.effort.isNotBlank()) Text("Workout effort ${workout.effort}/10", color = freeformPalette.secondary)
                        if (workout.notes.isNotBlank()) Text(workout.notes, color = freeformPalette.secondary)
                    }
                }
                if (onSaveAsTemplate != null) item {
                    OutlinedButton(onClick = onSaveAsTemplate,
                        modifier = Modifier.fillMaxWidth().testTag("freeform-save-as-template"),
                        shape = RoundedCornerShape(12.dp)) { Text("Save as template", color = freeformPalette.lime) }
                }
                items(workout.exercises.filter { exercise -> exercise.sets.any { it.completed } }, key = { it.id }) { exercise ->
                    FreeformPanel {
                        Text(exercise.name, color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium)
                        if (workout.discipline == ManualDiscipline.CONDITIONING) {
                            Text(listOfNotNull(exercise.format.label, exercise.blockMinutes.takeIf { it.isNotBlank() }
                                ?.let { "$it min cap" }).joinToString(" · "), color = freeformPalette.secondary)
                        }
                        if (exercise.notes.isNotBlank()) Text(exercise.notes, color = freeformPalette.secondary)
                        exercise.sets.forEachIndexed { index, set ->
                            if (set.completed) {
                                val label = if (workout.discipline == ManualDiscipline.CONDITIONING) "Round" else "Set"
                                val actual = set.actual.label(workout.discipline).ifBlank { "Completed" }
                                Text("$label ${index + 1}   $actual", color = freeformPalette.ink)
                                val context = listOfNotNull(
                                    set.target.label(workout.discipline).takeIf { it.isNotBlank() }?.let { "Target $it" },
                                    set.restSeconds.takeIf { it.isNotBlank() }?.let { "Rest $it s" },
                                    set.rpe.takeIf { it.isNotBlank() }?.let { "RPE $it" },
                                    set.rir.takeIf { it.isNotBlank() }?.let { "RIR $it" }
                                )
                                if (context.isNotEmpty()) Text(context.joinToString(" · "), color = freeformPalette.secondary)
                                if (set.notes.isNotBlank()) Text(set.notes, color = freeformPalette.secondary)
                            }
                        }
                    }
                }
            } else if (legacy != null) {
                item {
                    FreeformPanel {
                        if (legacy.notes.isNotBlank()) Text(legacy.notes, color = freeformPalette.secondary)
                        legacy.exercises.forEach { exercise ->
                            Text(exercise.name, color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(
                                "${exercise.sets} sets", exercise.reps?.let { "$it reps" },
                                exercise.seconds?.let { "$it s" }, exercise.weightKg?.let { "${freeformNumber(it)} kg" }
                            ).joinToString(" · "), color = freeformPalette.secondary)
                        }
                    }
                }
            } else {
                item {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else Text("This workout is unavailable in the current profile.", color = freeformPalette.secondary)
                }
            }
        }
    }
}
