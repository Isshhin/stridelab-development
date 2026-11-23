package io.stridelab.privatealpha

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun WorkoutTemplateLibrary(
    owner: String,
    templates: List<ManualWorkoutTemplate>,
    access: FreeformAccess,
    back: () -> Unit,
    create: (ManualDiscipline) -> Unit,
    edit: (String) -> Unit,
    schedule: (String) -> Unit,
    added: (ManualWorkoutTemplate) -> Unit,
    deleted: (String) -> Unit,
    started: (FreeformWorkout) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<ManualDiscipline?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<ManualWorkoutTemplate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val visible = templates.filter {
        (filter == null || it.discipline == filter) && it.name.contains(query.trim(), ignoreCase = true)
    }

    fun <T> perform(fallback: String, action: suspend () -> T, success: (T) -> Unit) {
        if (busy) return
        busy = true
        failure = null
        scope.launch(Dispatchers.Main.immediate) {
            freeformAttempt(action).onSuccess(success).onFailure { failure = it.freeformMessage(fallback) }
            busy = false
        }
    }

    FreeformPage("Workout templates", null, back) {
        LazyColumn(Modifier.fillMaxSize().testTag("workout-template-list"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FreeformPrimary("Create template", { choosing = true },
                    Modifier.fillMaxWidth().testTag("workout-template-new"), enabled = !busy)
            }
            if (templates.isNotEmpty()) {
                item {
                    FreeformField("Search templates", query, { query = it }, Modifier.fillMaxWidth(),
                        "workout-template-search", limit = 100)
                    Box {
                        TextButton(onClick = { showFilter = true }, modifier = Modifier.testTag("workout-template-filter")) {
                            Text(filter?.label ?: "All disciplines", color = freeformPalette.ink)
                        }
                        DropdownMenu(expanded = showFilter, onDismissRequest = { showFilter = false }) {
                            DropdownMenuItem(text = { Text("All disciplines") }, onClick = { filter = null; showFilter = false })
                            ManualDiscipline.entries.forEach { discipline ->
                                DropdownMenuItem(text = { Text(discipline.label) },
                                    onClick = { filter = discipline; showFilter = false })
                            }
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    FreeformPanel {
                        Text(if (templates.isEmpty()) "Save a routine you want to repeat" else "No matching templates",
                            color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium)
                        if (templates.isEmpty()) Text("Start with a blank template or save one from a completed workout.",
                            color = freeformPalette.secondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(visible, key = { it.id }) { template ->
                TemplateRow(template, enabled = !busy, edit = { edit(template.id) },
                    schedule = { schedule(template.id) },
                    start = {
                        perform("Could not start the workout.", {
                            access.start(template.startWorkout(LocalDate.now(), System.currentTimeMillis()))
                        }, started)
                    },
                    duplicate = {
                        val copy = template.duplicate()
                        perform("Could not duplicate the template.", { access.saveTemplate(copy, null) }) { added(copy) }
                    },
                    delete = { confirming = template })
            }
            item { FreeformError(failure) }
        }
    }

    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text("Choose a discipline") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(ManualDiscipline.entries, key = { it.name }) { discipline ->
                    FreeformTile(discipline.label, color = discipline.markColor,
                        modifier = Modifier.testTag("workout-template-discipline-${discipline.name}"),
                        onClick = { choosing = false; create(discipline) })
                }
            }
        }, confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } })

    confirming?.let { template ->
        AlertDialog(onDismissRequest = { confirming = null }, title = { Text("Delete ${template.name}?") },
            text = { Text("Workouts you’ve already started or saved will keep their details.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    perform("Could not delete the template.", {
                        check(access.deleteTemplate(owner, template.id)) { "This template is already gone." }
                    }) { deleted(template.id) }
                }) { Text("Delete") }
            }, dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } })
    }
}

@Composable
private fun TemplateRow(
    template: ManualWorkoutTemplate, enabled: Boolean, edit: () -> Unit, start: () -> Unit,
    duplicate: () -> Unit, delete: () -> Unit, schedule: () -> Unit
) {
    var options by remember { mutableStateOf(false) }
    FreeformPanel(Modifier.testTag("workout-template-row")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(template.name, color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("workout-template-name"))
                Text(template.summary(), color = freeformPalette.secondary, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { options = true }, enabled = enabled) {
                    Icon(Icons.Filled.MoreVert, "Options for ${template.name}", tint = freeformPalette.secondary)
                }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text("Edit template") }, onClick = { options = false; edit() })
                    DropdownMenuItem(text = { Text("Duplicate template") }, onClick = { options = false; duplicate() })
                    DropdownMenuItem(text = { Text("Delete template") }, onClick = { options = false; delete() })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = start, enabled = enabled,
                modifier = Modifier.weight(1f).testTag("workout-template-start")) {
                Text("Start workout", color = if (enabled) freeformPalette.lime else freeformPalette.muted)
            }
            TextButton(onClick = schedule, enabled = enabled,
                modifier = Modifier.weight(1f).testTag("workout-template-schedule")) {
                Text("Schedule", color = freeformPalette.secondary)
            }
        }
    }
}

internal fun ManualWorkoutTemplate.summary(): String {
    val recipe = structure
    return if (discipline == ManualDiscipline.RUNNING) {
        listOfNotNull(discipline.label, recipe.distanceKm.takeIf(String::isNotBlank)?.let { "$it km target" },
            recipe.durationMinutes.takeIf(String::isNotBlank)?.let { "$it min target" }).joinToString(" · ")
    } else {
        val count = recipe.exercises.size
        "${discipline.label} · $count exercise${if (count == 1) "" else "s"}"
    }
}

@Composable
internal fun WorkoutTemplateEditor(
    seed: ManualWorkoutTemplate,
    previous: ManualWorkoutTemplate?,
    access: FreeformAccess,
    back: () -> Unit,
    saved: (ManualWorkoutTemplate) -> Unit
) {
    val snapshot = remember(seed.id) { seed.startWorkout(LocalDate.now(), System.currentTimeMillis()) }
    var editor by remember(seed.id) { mutableStateOf(snapshot) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun goBack() {
        if (!saving) {
            if (editor != snapshot) leaving = true else back()
        }
    }
    BackHandler { goBack() }

    FreeformPage(if (previous == null) "New template" else "Edit template", seed.discipline.label, ::goBack) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("workout-template-editor"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FreeformPanel {
                    FreeformField("Template name", editor.workoutName, { editor = editor.copy(workoutName = it) },
                        Modifier.fillMaxWidth(), "workout-template-title", limit = 100)
                    if (seed.discipline == ManualDiscipline.RUNNING) {
                        FreeformField("Workout type", editor.runType, { editor = editor.copy(runType = it) },
                            Modifier.fillMaxWidth(), "workout-template-run-type", limit = 80)
                        FreeformField("Target distance (km)", editor.targetDistanceKm, { editor = editor.copy(targetDistanceKm = it) },
                            Modifier.fillMaxWidth(), "workout-template-distance", decimal = true)
                        FreeformField("Target duration (min)", editor.targetDurationMinutes,
                            { editor = editor.copy(targetDurationMinutes = it) }, Modifier.fillMaxWidth(),
                            "workout-template-duration", decimal = true)
                    }
                }
            }
            itemsIndexed(editor.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                FreeformExercisePanel(exercise, seed.discipline, null, index > 0, index < editor.exercises.lastIndex,
                    change = { next ->
                        editor = editor.copy(exercises = editor.exercises.map { if (it.id == next.id) next else it })
                    }, move = { offset ->
                        editor = editor.copy(exercises = editor.exercises.toMutableList().apply {
                            add(index + offset, removeAt(index))
                        })
                    }, delete = { editor = editor.copy(exercises = editor.exercises.filterNot { it.id == exercise.id }) },
                    authoring = true)
            }
            if (seed.discipline != ManualDiscipline.RUNNING) item {
                OutlinedButton(onClick = { picker = true }, enabled = editor.exercises.size < 30,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("workout-template-exercise"),
                    shape = RoundedCornerShape(12.dp)) { Text("Add exercise", color = freeformPalette.lime) }
            }
            item {
                FreeformPanel {
                    FreeformField("Workout notes", editor.notes, { editor = editor.copy(notes = it) },
                        Modifier.fillMaxWidth(), "workout-template-notes", limit = 2000, lines = 3)
                }
            }
        }
        Surface(color = freeformPalette.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                FreeformError(error)
                FreeformPrimary("Save template", {
                    val changed = seed.editedFrom(editor)
                    saving = true
                    error = null
                    scope.launch(Dispatchers.Main.immediate) {
                        freeformAttempt { access.saveTemplate(changed, previous) }.onSuccess { saved(changed) }
                            .onFailure { error = it.freeformMessage("Could not save this template.") }
                        saving = false
                    }
                }, Modifier.fillMaxWidth().testTag("workout-template-save"), enabled = !saving)
            }
        }
    }
    if (picker) FreeformExercisePicker(seed.discipline, { picker = false }) {
        editor = editor.copy(exercises = editor.exercises + it)
        picker = false
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("Discard template changes?") },
        confirmButton = { TextButton(onClick = back) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep editing") } })
}
