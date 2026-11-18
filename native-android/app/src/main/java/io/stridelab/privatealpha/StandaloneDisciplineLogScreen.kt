package io.stridelab.privatealpha

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID

private data class ManualEntryDraft(
    val name: String = "",
    val sets: String = "3",
    val reps: String = "",
    val seconds: String = "",
    val weightKg: String = ""
)

/** One independent offline activity log, never automatically linked to a coaching plan. */
@Composable
internal fun StandaloneDisciplineLogScreen(owner: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { ManualDisciplineStore(context) }
    val scope = rememberCoroutineScope()
    var sessions by remember(owner) { mutableStateOf<List<ManualDisciplineSession>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ManualDisciplineSession?>(null) }

    var discipline by rememberSaveable { mutableStateOf(ManualDiscipline.STRENGTH) }
    var name by rememberSaveable { mutableStateOf("") }
    var performedOn by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var drafts by remember { mutableStateOf(listOf(ManualEntryDraft())) }

    LaunchedEffect(owner) {
        try { sessions = store.list(owner) }
        catch(error: Exception) {
            if(error is CancellationException) throw error
            message = "Some saved logs could not be opened. Existing records were retained."
        }
    }

    fun asRecord(): ManualDisciplineSession {
        require(name.trim().isNotEmpty()) { "Enter a session name." }
        val date = runCatching { LocalDate.parse(performedOn.trim()) }.getOrElse {
            throw IllegalArgumentException("Enter a valid date in YYYY-MM-DD format.")
        }
        fun optionalInt(text: String, label: String): Int? =
            if(text.isBlank()) null else text.toIntOrNull()
                ?: throw IllegalArgumentException("Enter a valid $label.")
        fun optionalWeight(text: String): Double? =
            if(text.isBlank()) null else text.toDoubleOrNull()
                ?: throw IllegalArgumentException("Enter a valid weight.")
        val entries = drafts.map { item ->
            val sets = item.sets.toIntOrNull()
                ?: throw IllegalArgumentException("Enter a valid set count.")
            ManualExerciseEntry(
                name = item.name.trim(),
                sets = sets,
                reps = optionalInt(item.reps, "repetition count"),
                seconds = optionalInt(item.seconds, "duration in seconds"),
                weightKg = optionalWeight(item.weightKg)
            )
        }
        val record = ManualDisciplineSession(
            UUID.randomUUID().toString(), owner, discipline, name.trim(), date, entries,
            notes.trim()
        )
        ManualDisciplineCodec.encode(record)
        return record
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if(uri != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val backup = store.export(owner)
                        checkNotNull(context.contentResolver.openOutputStream(uri, "w")).use {
                            it.write(backup.toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                    message = "Manual activity logs exported. Keep the backup in a safe place."
                } catch(error: Exception) {
                    if(error is CancellationException) throw error
                    message = "Could not export manual logs. Existing records were retained."
                } finally { busy = false }
            }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) {
            busy = true
            scope.launch {
                try {
                    val backup = withContext(Dispatchers.IO) {
                        val result = ByteArrayOutputStream()
                        checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            val buffer = ByteArray(8192)
                            while(true) {
                                val size = input.read(buffer)
                                if(size < 0) break
                                check(result.size() + size <= 8_100_000) { "Backup is too large." }
                                result.write(buffer, 0, size)
                            }
                        }
                        result.toString("UTF-8")
                    }
                    val restored = store.restore(owner, backup)
                    sessions = store.list(owner)
                    message = if(restored == 0) "Backup already imported; existing logs kept."
                        else "Restored $restored manual activity logs."
                } catch(error: Exception) {
                    if(error is CancellationException) throw error
                    message = "Backup rejected or unavailable. No saved records were changed."
                } finally { busy = false }
            }
        }
    }

    Screen("Activity logging", "OFFLINE · MANUAL", compactTitle = true) {
        item(key = "manual-back") {
            TextButton(onClick = onBack, modifier = Modifier.testTag("manual-log-back")) {
                Text("← Back to More")
            }
        }
        item(key = "manual-intro") {
            SignalCard {
                Text("Record your workout", style = MaterialTheme.typography.titleLarge)
                Text("Log completed strength, calisthenics or conditioning sessions. " +
                    "Manual entries stay separate from Garmin recordings and generated plans. " +
                    "No sign-in or internet is required.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "manual-editor") {
            SignalCard {
                Text("DISCIPLINE", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ManualDiscipline.entries.forEach { option ->
                        FilterChip(
                            selected = discipline == option,
                            onClick = { discipline = option },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("manual-discipline-" + option.name.lowercase())
                        )
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { if(it.length <= 100) name = it },
                    label = { Text("Session name") },
                    placeholder = { Text("Evening strength session") },
                    modifier = Modifier.fillMaxWidth().testTag("manual-title")
                )
                OutlinedTextField(
                    value = performedOn,
                    onValueChange = { if(it.length <= 10) performedOn = it },
                    label = { Text("Performed on (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual-date")
                )
                drafts.forEachIndexed { index, row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Exercise ${index + 1}", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium)
                    fun edit(next: ManualEntryDraft) {
                        drafts = drafts.toMutableList().also { it[index] = next }
                    }
                    OutlinedTextField(
                        value = row.name, onValueChange = { if(it.length <= 100) edit(row.copy(name = it)) },
                        label = { Text("Exercise name") },
                        modifier = Modifier.fillMaxWidth().testTag("manual-exercise-name-$index")
                    )
                    OutlinedTextField(
                        value = row.sets, onValueChange = { if(it.length <= 2) edit(row.copy(sets = it)) },
                        label = { Text("Sets or rounds") },
                        modifier = Modifier.fillMaxWidth().testTag("manual-sets-$index")
                    )
                    OutlinedTextField(
                        value = row.reps, onValueChange = { if(it.length <= 3) edit(row.copy(reps = it)) },
                        label = { Text("Repetitions (optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("manual-reps-$index")
                    )
                    OutlinedTextField(
                        value = row.seconds, onValueChange = { if(it.length <= 4) edit(row.copy(seconds = it)) },
                        label = { Text("Seconds per set (optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("manual-seconds-$index")
                    )
                    OutlinedTextField(
                        value = row.weightKg,
                        onValueChange = { if(it.length <= 9) edit(row.copy(weightKg = it)) },
                        label = { Text("Weight in kg (optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("manual-weight-$index")
                    )
                    if(drafts.size > 1) {
                        TextButton(onClick = { drafts = drafts.filterIndexed { i, _ -> i != index } },
                            modifier = Modifier.testTag("manual-remove-exercise-$index")) {
                            Text("Remove exercise")
                        }
                    }
                }
                if(drafts.size < 30) {
                    TextButton(
                        onClick = { drafts = drafts + ManualEntryDraft() },
                        modifier = Modifier.testTag("manual-add-exercise")
                    ) { Text("Add exercise") }
                }
                OutlinedTextField(
                    value = notes, onValueChange = { if(it.length <= 1000) notes = it },
                    label = { Text("Personal notes (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("manual-notes")
                )
                Button(onClick = {
                    try {
                        val record = asRecord()
                        busy = true
                        message = null
                        scope.launch {
                            try {
                                store.save(record)
                                sessions = store.list(owner)
                                name = ""
                                notes = ""
                                drafts = listOf(ManualEntryDraft())
                                message = "Manual session saved offline."
                            } catch(error: Exception) {
                                if(error is CancellationException) throw error
                                message = "Could not save the record. Existing logs were kept."
                            } finally { busy = false }
                        }
                    } catch(error: IllegalArgumentException) {
                        message = error.message ?: "Please correct the recorded values."
                    }
                }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("manual-save")) {
                    Text("Save completed session")
                }
            }
        }
        item(key = "manual-backup") {
            SignalCard {
                Text("BACKUP YOUR MANUAL LOGS", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("Export a checksummed JSON file or restore matching owner data. " +
                    "Import is insert-only and never overwrites existing sessions.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportPicker.launch("StrideLab-manual-logs-${LocalDate.now()}.json") },
                        enabled = !busy,
                        modifier = Modifier.testTag("manual-export")
                    ) { Text("Export") }
                    OutlinedButton(
                        onClick = { importPicker.launch(arrayOf("application/json", "text/plain")) },
                        enabled = !busy,
                        modifier = Modifier.testTag("manual-import")
                    ) { Text("Restore") }
                }
            }
        }
        message?.let { note ->
            item(key = "manual-feedback") {
                Text(note, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("manual-feedback"))
            }
        }
        item(key = "manual-history-title") {
            Text("SAVED MANUAL SESSIONS · ${sessions.size}",
                style = MaterialTheme.typography.titleMedium)
            if(sessions.isEmpty()) Text("No manual sessions saved yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(sessions.size, key = { "manual-log-${sessions[it].id}" }) { index ->
            val item = sessions[index]
            SignalCard {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text("${item.discipline.label} · ${item.performedOn}",
                    style = MaterialTheme.typography.bodyMedium)
                item.exercises.forEach { exercise ->
                    val amounts = listOfNotNull(
                        "${exercise.sets} sets",
                        exercise.reps?.let { "$it reps" },
                        exercise.seconds?.let { "$it s" },
                        exercise.weightKg?.let { "$it kg" }
                    )
                    Text(exercise.name + " · " + amounts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall)
                }
                if(item.notes.isNotBlank()) Text(item.notes,
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { deleting = item }, enabled = !busy) { Text("Delete") }
            }
        }
    }

    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { if(!busy) deleting = null },
            title = { Text("Delete manual session?") },
            text = { Text(record.title + " · This only deletes this manually entered log.") },
            confirmButton = {
                TextButton(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            store.delete(owner, record.id)
                            sessions = store.list(owner)
                            message = "Manual log deleted."
                        } catch(error: Exception) {
                            if(error is CancellationException) throw error
                            message = "Could not delete manual log."
                        } finally { deleting = null; busy = false }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}
