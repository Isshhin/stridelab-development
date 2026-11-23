package io.stridelab.privatealpha

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
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
        LazyColumn(
            Modifier.fillMaxSize().testTag("workout-template-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                FreeformPrimary(
                    "Create template",
                    { choosing = true },
                    Modifier.fillMaxWidth().testTag("workout-template-new"),
                    enabled = !busy
                )
            }
            if (templates.isNotEmpty()) {
                item {
                    FreeformField(
                        "Search templates",
                        query,
                        { query = it },
                        Modifier.fillMaxWidth(),
                        "workout-template-search",
                        limit = 100
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("workout-template-filter"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filter == null,
                            onClick = { filter = null },
                            label = { Text("All") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        ManualDiscipline.entries.forEach { discipline ->
                            FilterChip(
                                selected = filter == discipline,
                                onClick = { filter = if (filter == discipline) null else discipline },
                                label = { Text(discipline.label) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (templates.isEmpty()) "Save a routine you want to repeat" else "No matching templates",
                            color = StrideLabSurfaceTokens.ink,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (templates.isEmpty()) {
                            Text(
                                "Start with a blank template or save one from a completed workout.",
                                color = StrideLabSurfaceTokens.inkSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            items(visible, key = { it.id }) { template ->
                TemplateRow(
                    template = template,
                    enabled = !busy,
                    edit = { edit(template.id) },
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
                    delete = { confirming = template }
                )
            }
            item { FreeformError(failure) }
        }
    }

    if (choosing) {
        ModalBottomSheet(
            onDismissRequest = { choosing = false },
            containerColor = StrideLabSurfaceTokens.surfaceElevated,
            contentColor = StrideLabSurfaceTokens.ink
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Choose a discipline",
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                ManualDiscipline.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { discipline ->
                            Surface(
                                onClick = {
                                    choosing = false
                                    create(discipline)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = StrideLabSurfaceTokens.surfaceSubtle,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .testTag("workout-template-discipline-${discipline.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(22.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(discipline.markColor)
                                    )
                                    Text(
                                        discipline.label,
                                        color = StrideLabSurfaceTokens.ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    confirming?.let { template ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Delete ${template.name}?") },
            text = { Text("Workouts you’ve already started or saved will keep their details.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    perform("Could not delete the template.", {
                        check(access.deleteTemplate(owner, template.id)) { "This template is already gone." }
                    }) { deleted(template.id) }
                }) { Text("Delete", color = freeformPalette.highCoral) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TemplateRow(
    template: ManualWorkoutTemplate,
    enabled: Boolean,
    edit: () -> Unit,
    start: () -> Unit,
    duplicate: () -> Unit,
    delete: () -> Unit,
    schedule: () -> Unit
) {
    var options by remember { mutableStateOf(false) }
    val preview = remember(template) {
        if (template.discipline == ManualDiscipline.RUNNING) {
            template.structure.runType.takeIf { it.isNotBlank() }
        } else {
            val names = template.structure.exercises.take(3).map { it.name }
            if (names.isEmpty()) null
            else names.joinToString(", ") + if (template.structure.exercises.size > 3) "…" else ""
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("workout-template-row"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(template.discipline.markColor)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    template.name,
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("workout-template-name")
                )
                Text(
                    template.summary(),
                    color = StrideLabSurfaceTokens.inkSecondary,
                    fontSize = 12.5.sp,
                    style = TabularTextStyle
                )
                if (!preview.isNullOrBlank()) {
                    Text(
                        preview,
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { options = true },
                    enabled = enabled,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "Options for ${template.name}",
                        tint = StrideLabSurfaceTokens.inkSecondary
                    )
                }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text("Edit template") }, onClick = { options = false; edit() })
                    DropdownMenuItem(text = { Text("Duplicate template") }, onClick = { options = false; duplicate() })
                    DropdownMenuItem(text = { Text("Delete template") }, onClick = { options = false; delete() })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = start,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("workout-template-start")
            ) {
                Text(
                    "Start workout",
                    color = if (enabled) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.inkMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(
                onClick = schedule,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("workout-template-schedule")
            ) {
                Text("Schedule", color = StrideLabSurfaceTokens.inkSecondary)
            }
        }
        StrideLabHairline()
    }
}

internal fun ManualWorkoutTemplate.summary(): String {
    val recipe = structure
    return if (discipline == ManualDiscipline.RUNNING) {
        listOfNotNull(
            discipline.label,
            recipe.distanceKm.takeIf(String::isNotBlank)?.let { "$it km target" },
            recipe.durationMinutes.takeIf(String::isNotBlank)?.let { "$it min target" }
        ).joinToString(" · ")
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
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("workout-template-editor"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FreeformField(
                        "Template name",
                        editor.workoutName,
                        { editor = editor.copy(workoutName = it) },
                        Modifier.fillMaxWidth(),
                        "workout-template-title",
                        limit = 100
                    )
                    if (seed.discipline == ManualDiscipline.RUNNING) {
                        FreeformField(
                            "Workout type",
                            editor.runType,
                            { editor = editor.copy(runType = it) },
                            Modifier.fillMaxWidth(),
                            "workout-template-run-type",
                            limit = 80
                        )
                        FreeformField(
                            "Target distance (km)",
                            editor.targetDistanceKm,
                            { editor = editor.copy(targetDistanceKm = it) },
                            Modifier.fillMaxWidth(),
                            "workout-template-distance",
                            decimal = true
                        )
                        FreeformField(
                            "Target duration (min)",
                            editor.targetDurationMinutes,
                            { editor = editor.copy(targetDurationMinutes = it) },
                            Modifier.fillMaxWidth(),
                            "workout-template-duration",
                            decimal = true
                        )
                    }
                    StrideLabHairline()
                }
            }
            itemsIndexed(editor.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                FreeformExercisePanel(
                    exercise,
                    seed.discipline,
                    null,
                    index > 0,
                    index < editor.exercises.lastIndex,
                    change = { next ->
                        editor = editor.copy(exercises = editor.exercises.map { if (it.id == next.id) next else it })
                    },
                    move = { offset ->
                        editor = editor.copy(exercises = editor.exercises.toMutableList().apply {
                            add(index + offset, removeAt(index))
                        })
                    },
                    delete = {
                        editor = editor.copy(exercises = editor.exercises.filterNot { it.id == exercise.id })
                    },
                    authoring = true
                )
            }
            if (seed.discipline != ManualDiscipline.RUNNING) {
                item {
                    OutlinedButton(
                        onClick = { picker = true },
                        enabled = editor.exercises.size < 30,
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("workout-template-exercise"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+ Add exercise", color = StrideLabSurfaceTokens.lime)
                    }
                }
            }
            item {
                FreeformField(
                    "Workout notes",
                    editor.notes,
                    { editor = editor.copy(notes = it) },
                    Modifier.fillMaxWidth(),
                    "workout-template-notes",
                    limit = 2000,
                    lines = 3
                )
            }
        }
        StrideLabHairline()
        Surface(color = StrideLabSurfaceTokens.surfaceElevated) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                FreeformError(error)
                FreeformPrimary(
                    "Save template",
                    {
                        val changed = seed.editedFrom(editor)
                        saving = true
                        error = null
                        scope.launch(Dispatchers.Main.immediate) {
                            freeformAttempt { access.saveTemplate(changed, previous) }.onSuccess { saved(changed) }
                                .onFailure { error = it.freeformMessage("Could not save this template.") }
                            saving = false
                        }
                    },
                    Modifier.fillMaxWidth().testTag("workout-template-save"),
                    enabled = !saving
                )
            }
        }
    }
    if (picker) {
        FreeformExercisePicker(seed.discipline, { picker = false }) {
            editor = editor.copy(exercises = editor.exercises + it)
            picker = false
        }
    }
    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Discard template changes?") },
            confirmButton = { TextButton(onClick = back) { Text("Discard", color = freeformPalette.highCoral) } },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep editing") } }
        )
    }
}
