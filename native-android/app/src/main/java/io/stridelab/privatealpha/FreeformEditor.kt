package io.stridelab.privatealpha

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FreeformEditor(
    initial: FreeformWorkout,
    board: FreeformBoard,
    access: FreeformAccess,
    update: (FreeformWorkout) -> Unit,
    remove: (String) -> Unit,
    leave: () -> Unit,
    saved: (String) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var persistedAt by remember(initial.id) { mutableLongStateOf(initial.modifiedAt) }
    var working by remember(initial.id) { mutableStateOf(false) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    var review by rememberSaveable(initial.id) { mutableStateOf(false) }
    var picker by rememberSaveable(initial.id) { mutableStateOf(false) }
    var discard by rememberSaveable(initial.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun edit(after: FreeformWorkout) {
        if (working || after == draft) return
        draft = after.copy(modifiedAt = maxOf(draft.modifiedAt + 1, System.currentTimeMillis()))
        error = null
    }

    fun editExercise(index: Int, after: FreeformExercise) {
        edit(draft.copy(exercises = draft.exercises.toMutableList().apply { set(index, after) }))
    }

    fun saveAndLeave() {
        if (working) return
        scope.launch(Dispatchers.Main.immediate) {
            working = true
            error = null
            freeformAttempt { access.save(draft) }.onSuccess {
                update(it)
                if (it.isComplete) saved(it.id) else leave()
            }.onFailure { error = it.freeformMessage("Could not save your draft. Try again.") }
            working = false
        }
    }

    fun back() {
        if (working) return
        if (review) { review = false; error = null } else saveAndLeave()
    }

    BackHandler { back() }
    LaunchedEffect(draft.modifiedAt, working) {
        val pending = draft
        if (!working && pending.modifiedAt > persistedAt) {
            delay(350)
            freeformAttempt { access.save(pending) }.onSuccess { stored ->
                persistedAt = maxOf(persistedAt, stored.modifiedAt)
                update(stored)
            }.onFailure { error = it.freeformMessage("Draft changes could not be saved.") }
        }
    }

    if (review) {
        FreeformPage("Review workout", "${draft.discipline.label} · ${draft.performedOn}", ::back) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FreeformPanel {
                        Text("Recorded totals", color = freeformPalette.secondary, style = MaterialTheme.typography.labelLarge)
                        Text(draft.summary(), color = freeformPalette.ink, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold, modifier = Modifier.testTag("freeform-review-totals"))
                        if (draft.exercises.any { exercise -> exercise.sets.any { !it.completed } }) {
                            Text("Only confirmed sets and rounds count.", color = freeformPalette.secondary)
                        }
                        if (draft.effort.isNotBlank()) Text("Workout effort ${draft.effort}/10", color = freeformPalette.secondary)
                        if (draft.notes.isNotBlank()) Text(draft.notes, color = freeformPalette.secondary)
                    }
                }
                draft.exercises.filter { exercise -> exercise.sets.any { it.completed } }.forEach { exercise ->
                    item(key = exercise.id) {
                        FreeformPanel {
                            Text(exercise.name, color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium)
                            exercise.sets.filter { it.completed }.forEach { set ->
                                Text(set.actual.label(draft.discipline).ifBlank { "Round completed" }, color = freeformPalette.secondary)
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                FreeformError(error)
                FreeformPrimary("Save workout", {
                    scope.launch(Dispatchers.Main.immediate) {
                        working = true
                        error = null
                        freeformAttempt { access.finish(draft) }.onSuccess {
                            update(it)
                            saved(it.id)
                        }.onFailure { error = it.freeformMessage("Could not finish your workout. Your draft is still saved.") }
                        working = false
                    }
                }, Modifier.fillMaxWidth().testTag("freeform-save-completed"), enabled = !working)
            }
        }
        return
    }

    FreeformPage("Log ${draft.discipline.label.lowercase()}", "Freeform workout", ::back) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("freeform-editor"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (draft.discipline == ManualDiscipline.RUNNING) {
                item {
                    FreeformPanel {
                        Text("Run details", color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        FreeformField("Distance (km)", draft.distanceKm, { edit(draft.copy(distanceKm = it)) },
                            Modifier.fillMaxWidth(), "freeform-run-distance", decimal = true)
                        FreeformField("Duration (min)", draft.durationMinutes, { edit(draft.copy(durationMinutes = it)) },
                            Modifier.fillMaxWidth(), "freeform-run-duration", decimal = true)
                        FreeformField("Workout type (optional)", draft.runType, { edit(draft.copy(runType = it)) },
                            Modifier.fillMaxWidth(), "freeform-run-type", limit = 80)
                    }
                }
            } else {
                itemsIndexed(draft.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                    FreeformExercisePanel(
                        exercise = exercise, discipline = draft.discipline, previous = board.previous(draft.discipline, exercise),
                        hasPrevious = index > 0, hasNext = index < draft.exercises.lastIndex,
                        change = { editExercise(index, it) },
                        move = { offset ->
                            edit(draft.copy(exercises = draft.exercises.toMutableList().apply {
                                add(index + offset, removeAt(index))
                            }))
                        },
                        delete = { edit(draft.copy(exercises = draft.exercises.filterNot { it.id == exercise.id })) }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { picker = true }, enabled = draft.exercises.size < 30 && !working,
                        border = BorderStroke(1.dp, freeformPalette.border),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("freeform-add-exercise")
                    ) { Text("+ Add exercise", color = freeformPalette.lime) }
                }
            }
            item {
                FreeformPanel {
                    Text("Workout details", color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium)
                    FreeformField("Date (YYYY-MM-DD)", draft.performedOn, { edit(draft.copy(performedOn = it)) },
                        Modifier.fillMaxWidth(), "freeform-date")
                    FreeformField("Effort (1–10, optional)", draft.effort, { edit(draft.copy(effort = it)) },
                        Modifier.fillMaxWidth(), "freeform-workout-effort", integer = true)
                    FreeformField("Workout notes", draft.notes, { edit(draft.copy(notes = it)) },
                        Modifier.fillMaxWidth(), "freeform-workout-notes", limit = 2000, lines = 3)
                }
            }
            item {
                TextButton(onClick = { discard = true }, enabled = !working,
                    modifier = Modifier.testTag("freeform-discard")) {
                    Text("Discard draft", color = freeformPalette.highCoral)
                }
            }
        }
        HorizontalDivider(color = freeformPalette.border)
        Surface(color = freeformPalette.surface) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FreeformError(error)
                FreeformPrimary("Finish Workout", {
                    runCatching { draft.finish(System.currentTimeMillis()) }.onSuccess { error = null; review = true }
                        .onFailure { error = it.freeformMessage("Check the workout before finishing.") }
                }, Modifier.fillMaxWidth().testTag("freeform-finish"), enabled = !working)
                TextButton(
                    onClick = ::saveAndLeave,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("freeform-save-progress"),
                    enabled = !working
                ) {
                    Text("Save progress", color = freeformPalette.lime)
                }
            }
        }
    }

    if (picker) FreeformExercisePicker(draft.discipline, close = { picker = false }) { exercise ->
        edit(draft.copy(exercises = draft.exercises + exercise))
        picker = false
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text("Discard this draft?") },
        text = { Text("The saved draft and its sets will be removed.") },
        confirmButton = {
            TextButton(onClick = {
                discard = false
                scope.launch(Dispatchers.Main.immediate) {
                    working = true
                    freeformAttempt { access.discard(draft.owner, draft.id) }.onSuccess { removed ->
                        if (removed) { remove(draft.id); leave() }
                        else error = "This draft has already changed. Reopen it from Training."
                    }.onFailure { error = it.freeformMessage("Could not discard the draft.") }
                    working = false
                }
            }) { Text("Discard") }
        },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep draft") } }
    )
}

@Composable
private fun FreeformExercisePanel(
    exercise: FreeformExercise, discipline: ManualDiscipline, previous: FreeformMeasure?,
    hasPrevious: Boolean, hasNext: Boolean,
    change: (FreeformExercise) -> Unit, move: (Int) -> Unit, delete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var showNotes by rememberSaveable { mutableStateOf(exercise.notes.isNotBlank()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    val conditioning = discipline == ManualDiscipline.CONDITIONING
    FreeformPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise.name, color = freeformPalette.ink, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                if (previous != null) Text("Previous confirmed · ${previous.label(discipline)}",
                    color = freeformPalette.secondary, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, "Options for ${exercise.name}", tint = freeformPalette.secondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Move up") }, enabled = hasPrevious, onClick = { menu = false; move(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, enabled = hasNext, onClick = { menu = false; move(1) })
                    DropdownMenuItem(text = { Text("Exercise notes") }, onClick = { menu = false; showNotes = true })
                    DropdownMenuItem(text = { Text("Remove exercise") }, onClick = {
                        menu = false
                        confirmDelete = true
                    })
                }
            }
        }
        if (showNotes) FreeformField("Exercise notes", exercise.notes, { change(exercise.copy(notes = it)) },
            Modifier.fillMaxWidth(), "freeform-exercise-notes", limit = 1000, lines = 2)
        if (conditioning) {
            Box {
                OutlinedButton(onClick = { showFormat = true }) { Text("Format · ${exercise.format.label}") }
                DropdownMenu(expanded = showFormat, onDismissRequest = { showFormat = false }) {
                    ConditioningFormat.entries.forEach { mode ->
                        DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                            showFormat = false
                            change(exercise.copy(format = mode))
                        })
                    }
                }
            }
            if (exercise.format != ConditioningFormat.INTERVALS || exercise.blockMinutes.isNotEmpty()) {
                val label = if (exercise.format == ConditioningFormat.EMOM) "Block duration (min)" else "Time cap (min)"
                FreeformField(label, exercise.blockMinutes, { change(exercise.copy(blockMinutes = it)) },
                    Modifier.fillMaxWidth(), "freeform-block-minutes", decimal = true)
            }
        }
        exercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                FreeformSetRow(
                    set = set, index = index, discipline = discipline, format = exercise.format,
                    edit = { next -> change(exercise.copy(sets = exercise.sets.toMutableList().apply { set(index, next) })) },
                    duplicate = {
                        change(exercise.copy(sets = exercise.sets.toMutableList().apply { add(index + 1, set.duplicate()) }))
                    },
                    remove = { change(exercise.copy(sets = exercise.sets.filterNot { it.id == set.id })) },
                    canAdd = exercise.sets.size < 30
                )
            }
        }
        if (exercise.sets.size < 30) {
            val unit = if (conditioning) "round" else "set"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { change(exercise.copy(sets = exercise.sets + FreeformSet())) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("freeform-add-set")
                ) { Text("+ Add $unit", color = freeformPalette.lime) }
                if (exercise.sets.isNotEmpty()) {
                    TextButton(
                        onClick = { change(exercise.copy(sets = exercise.sets + exercise.sets.last().duplicate())) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("freeform-duplicate-last")
                    ) { Text("Duplicate last", color = freeformPalette.lime) }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove ${exercise.name}?") },
        text = { Text("The exercise and its sets will be removed from this draft.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; delete() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep exercise") } }
    )
}

@Composable
private fun FreeformSetRow(
    set: FreeformSet, index: Int, discipline: ManualDiscipline, format: ConditioningFormat,
    edit: (FreeformSet) -> Unit,
    duplicate: () -> Unit, remove: () -> Unit, canAdd: Boolean
) {
    var options by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var setError by remember { mutableStateOf(false) }
    val unit = if (discipline == ManualDiscipline.CONDITIONING) "Round" else "Set"

    Surface(
        color = freeformPalette.surfaceRaised, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (set.completed) freeformPalette.lime.copy(alpha = .5f) else freeformPalette.border),
        modifier = Modifier.fillMaxWidth().testTag("freeform-set-$index")
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$unit ${index + 1}", Modifier.weight(1f), color = freeformPalette.ink,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = {
                        if (set.completed || set.validActual(discipline, format)) {
                            setError = false
                            edit(set.copy(completed = !set.completed))
                        } else setError = true
                    },
                    modifier = Modifier.testTag("freeform-confirm-$index")
                ) {
                    val label = if (set.completed) "Unconfirm $unit ${index + 1}" else "Confirm $unit ${index + 1}"
                    Icon(Icons.Filled.Check, label, tint = if (set.completed) freeformPalette.lime else freeformPalette.secondary)
                }
                Box {
                    IconButton(onClick = { options = true }) {
                        Icon(Icons.Filled.MoreVert, "$unit ${index + 1} options", tint = freeformPalette.secondary)
                    }
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                        DropdownMenuItem(text = { Text(if (expanded) "Hide details" else "Targets, rest & effort") },
                            onClick = { options = false; expanded = !expanded })
                        DropdownMenuItem(text = { Text("Duplicate ${unit.lowercase()}") },
                            enabled = canAdd, onClick = { options = false; duplicate() })
                        DropdownMenuItem(text = { Text("Remove ${unit.lowercase()}") },
                            onClick = { options = false; remove() })
                    }
                }
            }
            MeasureFields(discipline, set.actual, "Actual", "freeform-actual-$index") {
                setError = false
                edit(set.copy(actual = it))
            }
            if (setError || set.completed && !set.validActual(discipline, format)) {
                FreeformError("Enter valid actual work to confirm this ${unit.lowercase()}.")
            }
            if (expanded) {
                HorizontalDivider(color = freeformPalette.border)
                MeasureFields(discipline, set.target, "Target", "freeform-target-$index") { edit(set.copy(target = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FreeformField("Rest (s)", set.restSeconds, { edit(set.copy(restSeconds = it)) },
                        Modifier.weight(1f), "freeform-rest-$index", integer = true)
                    FreeformField("RPE", set.rpe, { edit(set.copy(rpe = it)) },
                        Modifier.weight(1f), "freeform-rpe-$index", integer = true)
                }
                if (discipline == ManualDiscipline.STRENGTH || discipline == ManualDiscipline.CALISTHENICS) {
                    FreeformField("RIR", set.rir, { edit(set.copy(rir = it)) }, Modifier.fillMaxWidth(),
                        "freeform-rir-$index", integer = true)
                }
                FreeformField("$unit notes", set.notes, { edit(set.copy(notes = it)) },
                    Modifier.fillMaxWidth(), "freeform-set-notes-$index", limit = 400, lines = 2)
            }
        }
    }
}

private enum class MeasureInput(val label: String, val suffix: String) {
    REPS("Reps", "quantity"), CONTACTS("Contacts", "quantity"), HOLD("Hold (s)", "seconds"),
    WORK("Work (s)", "seconds"), LOAD("Load (kg)", "kg")
}

@Composable
private fun MeasureFields(
    discipline: ManualDiscipline, value: FreeformMeasure, group: String, tag: String,
    edit: (FreeformMeasure) -> Unit
) {
    val metrics = when (discipline) {
        ManualDiscipline.RUNNING -> emptyList()
        ManualDiscipline.STRENGTH -> listOf(MeasureInput.REPS, MeasureInput.LOAD)
        ManualDiscipline.CALISTHENICS -> listOf(MeasureInput.REPS, MeasureInput.LOAD, MeasureInput.HOLD)
        ManualDiscipline.PLYOMETRICS -> listOf(MeasureInput.CONTACTS)
        ManualDiscipline.ISOMETRICS -> listOf(MeasureInput.HOLD, MeasureInput.LOAD)
        ManualDiscipline.CONDITIONING -> listOf(MeasureInput.WORK)
    }
    Text(group, color = freeformPalette.secondary, style = MaterialTheme.typography.labelLarge)
    metrics.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { metric ->
                val current = when (metric) {
                    MeasureInput.REPS, MeasureInput.CONTACTS -> value.quantity
                    MeasureInput.HOLD, MeasureInput.WORK -> value.seconds
                    MeasureInput.LOAD -> value.kg
                }
                FreeformField(metric.label, current, { input ->
                    edit(when (metric) {
                        MeasureInput.REPS, MeasureInput.CONTACTS -> value.copy(quantity = input)
                        MeasureInput.HOLD, MeasureInput.WORK -> value.copy(seconds = input)
                        MeasureInput.LOAD -> value.copy(kg = input)
                    })
                }, Modifier.weight(1f), "$tag-${metric.suffix}", integer = metric != MeasureInput.LOAD,
                    decimal = metric == MeasureInput.LOAD, accessibilityLabel = "$group ${metric.label}")
            }
            if (row.size == 1 && metrics.size > 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FreeformField(
    label: String, value: String, edit: (String) -> Unit, modifier: Modifier, tag: String,
    limit: Int = 32, integer: Boolean = false, decimal: Boolean = false, lines: Int = 1,
    accessibilityLabel: String = label
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = freeformPalette.secondary, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = value, onValueChange = { if (it.length <= limit) edit(it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
                .semantics { contentDescription = accessibilityLabel },
            singleLine = lines == 1, maxLines = lines,
            keyboardOptions = KeyboardOptions(keyboardType = when {
                integer -> KeyboardType.Number
                decimal -> KeyboardType.Decimal
                else -> KeyboardType.Text
            }),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = freeformPalette.ink, unfocusedTextColor = freeformPalette.ink,
                focusedBorderColor = freeformPalette.lime, unfocusedBorderColor = freeformPalette.border.copy(alpha = 1f),
                cursorColor = freeformPalette.lime
            )
        )
    }
}

@Composable
private fun FreeformExercisePicker(discipline: ManualDiscipline, close: () -> Unit, select: (FreeformExercise) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val catalog by produceState<List<ExerciseLibraryRecordV2>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) {
            freeformAttempt { ExerciseLibraryV2.load { context.assets.open(it) }.records }
                .getOrDefault(emptyList()).filter { it.kind == "exercise" }
        }
    }
    val category = when (discipline) {
        ManualDiscipline.STRENGTH -> "strength_gym"
        ManualDiscipline.CALISTHENICS -> "calisthenics"
        ManualDiscipline.CONDITIONING -> "conditioning"
        ManualDiscipline.PLYOMETRICS -> "plyometrics"
        ManualDiscipline.ISOMETRICS -> "isometrics"
        ManualDiscipline.RUNNING -> "running"
    }
    val matches = remember(catalog, query, category) {
        val search = query.trim()
        catalog.asSequence().filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
            .sortedWith(compareByDescending<ExerciseLibraryRecordV2> { category in it.categories }.thenBy { it.name })
            .take(60).toList()
    }
    AlertDialog(
        onDismissRequest = close, title = { Text("Add exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FreeformField("Search or type a name", query, { query = it }, Modifier.fillMaxWidth(),
                    "freeform-exercise-query", limit = 100)
                if (query.isNotBlank()) {
                    TextButton(
                        onClick = { select(FreeformExercise(name = query.trim())) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("freeform-add-custom")
                    ) { Text("Use custom name", color = freeformPalette.lime) }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 192.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(matches, key = { _, record -> record.id }) { _, record ->
                        FreeformTile(record.name, color = discipline.markColor,
                            onClick = { select(FreeformExercise(name = record.name, catalogId = record.id)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } }
    )
}
