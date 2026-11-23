package io.stridelab.privatealpha

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

internal fun customDateLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

internal fun customAdvisory(
    discipline: ManualDiscipline, date: LocalDate, coached: List<PlannedSession>, custom: List<CustomCalendarItem>
): String? {
    val crossTraining = setOf(ManualDiscipline.STRENGTH, ManualDiscipline.PLYOMETRICS,
        ManualDiscipline.CALISTHENICS, ManualDiscipline.ISOMETRICS, ManualDiscipline.CONDITIONING)
    val upcoming = coached.filter { it.status == "planned" || it.status == "in_progress" }
    val nearKeyRun = discipline in crossTraining && upcoming.any {
        it.discipline == "run" && (it.quality || it.kind.contains("long", ignoreCase = true)) &&
            it.date in date.minusDays(1)..date.plusDays(1)
    }
    if (nearKeyRun) {
        return "A key coached run is on or near this date. If this workout is demanding, consider spacing the sessions out."
    }
    val sharedDay = upcoming.any { it.date == date } || custom.any { it.date == date }
    return if (sharedDay) "Another workout is booked for this day. If both are demanding, leave recovery time between them."
        else null
}

@Composable
internal fun ScheduleRuleScreen(
    title: String,
    template: ManualWorkoutTemplate,
    initial: ScheduleRule,
    allowRepeat: Boolean,
    advisory: (LocalDate) -> String?,
    back: () -> Unit,
    persist: suspend (ScheduleRule) -> Unit,
    saved: (LocalDate) -> Unit
) {
    var start by rememberSaveable(initial) { mutableStateOf(initial.start.toString()) }
    var repeating by rememberSaveable(initial) { mutableStateOf(initial.weekly) }
    var end by rememberSaveable(initial) { mutableStateOf(initial.end?.toString().orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val parsed = parseCustomDate(start)
    BackHandler(enabled = busy) {}
    FreeformPage(title, template.name, { if (!busy) back() }) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("custom-schedule-editor"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FreeformPanel {
                    Text(template.summary(), color = freeformPalette.secondary, style = MaterialTheme.typography.bodyMedium)
                    PlannerDateField(if (allowRepeat) "First workout date" else "Scheduled date", start,
                        { start = it; error = null }, "custom-schedule-date")
                    if (allowRepeat) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !repeating, onClick = { repeating = false }, label = { Text("Once") },
                                modifier = Modifier.testTag("custom-repeat-once"))
                            FilterChip(selected = repeating, onClick = { repeating = true }, label = { Text("Weekly") },
                                modifier = Modifier.testTag("custom-repeat-weekly"))
                        }
                        if (repeating) {
                            if (parsed != null) Text(
                                "Every ${parsed.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                                color = freeformPalette.ink, style = MaterialTheme.typography.titleSmall
                            )
                            PlannerDateField("End date (optional)", end, { end = it; error = null },
                                "custom-schedule-end")
                        }
                    }
                }
            }
            parsed?.let(advisory)?.let { note -> item { CustomRecoveryNote(note) } }
        }
        Surface(color = freeformPalette.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FreeformError(error)
                FreeformPrimary("Save schedule", {
                    val date = parsed
                    val last = if (repeating && end.isNotBlank()) parseCustomDate(end) else null
                    if (date == null || (repeating && end.isNotBlank() && last == null)) {
                        error = "Use a valid date in YYYY-MM-DD format."
                    } else scope.launch(Dispatchers.Main.immediate) {
                        busy = true
                        error = null
                        freeformAttempt {
                            persist(ScheduleRule(date, weekly = allowRepeat && repeating, end = last))
                        }.onSuccess { saved(date) }
                            .onFailure { error = it.freeformMessage("Could not save this schedule.") }
                        busy = false
                    }
                }, Modifier.fillMaxWidth().testTag("custom-save-schedule"), enabled = !busy)
            }
        }
    }
}

private fun parseCustomDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
    ?.takeIf { it in customDateRange }

@Composable
private fun PlannerDateField(label: String, text: String, change: (String) -> Unit, tag: String) {
    val context = LocalContext.current
    FreeformField(label, text, change, Modifier.fillMaxWidth(), tag, limit = 10)
    TextButton(onClick = {
        val date = parseCustomDate(text) ?: LocalDate.now()
        pickCustomDate(context, date) { change(it.toString()) }
    }) { Text("Choose date", color = freeformPalette.secondary) }
}

internal fun pickCustomDate(context: Context, date: LocalDate, change: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, year, month, day -> change(LocalDate.of(year, month + 1, day)) },
        date.year, date.monthValue - 1, date.dayOfMonth).show()
}

@Composable
private fun CustomRecoveryNote(note: String) {
    FreeformPanel(Modifier.testTag("custom-recovery-note")) {
        Text("Recovery note", color = freeformPalette.moderateAmber, style = MaterialTheme.typography.titleSmall)
        Text(note, color = freeformPalette.secondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun CustomOccurrenceScreen(
    owner: String,
    item: CustomCalendarItem,
    schedule: CustomSchedule?,
    access: FreeformAccess,
    advisory: (LocalDate) -> String?,
    back: () -> Unit,
    returnToDate: (LocalDate) -> Unit,
    changed: (CustomSchedule) -> Unit,
    openWorkout: (FreeformWorkout) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf<CustomEditMode?>(null) }
    var remove by rememberSaveable { mutableStateOf<CustomEditMode?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val occurrence = schedule?.resolve(item.key)
    val edit = editing
    if (edit != null && occurrence != null) {
        val rule = schedule.ruleAt(item.key.anchor)
        val initial = if (edit == CustomEditMode.FUTURE) {
            val start = maxOf(item.key.anchor, rule?.start ?: item.date)
            ScheduleRule(start, weekly = true, end = rule?.end?.takeIf { it >= start })
        } else ScheduleRule(item.date)
        ScheduleRuleScreen(
            if (edit == CustomEditMode.FUTURE) "Edit future workouts" else "Reschedule workout",
            occurrence.template, initial, edit == CustomEditMode.FUTURE, advisory,
            back = { editing = null },
            persist = { updated ->
                val action = if (edit == CustomEditMode.FUTURE) CustomScheduleEdit.Future(updated, schedule.modifiedAt)
                else CustomScheduleEdit.One(updated.start, schedule.modifiedAt)
                changed(access.changeSchedule(owner, item.key, action))
            },
            saved = { returnToDate(it) }
        )
        return
    }
    BackHandler(enabled = busy) {}
    FreeformPage(item.title, "Custom · ${customDateLabel(item.date)}", { if (!busy) back() }) {
        LazyColumn(Modifier.fillMaxSize().testTag("custom-occurrence"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FreeformPanel {
                    Text(if (item.inProgress) "In progress" else "Scheduled workout", color = freeformPalette.ink,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(occurrence?.template?.summary() ?: item.discipline.label, color = freeformPalette.secondary)
                    if (occurrence?.weekly == true) {
                        Text("Weekly", color = freeformPalette.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            advisory(item.date)?.let { note -> item { CustomRecoveryNote(note) } }
            item {
                FreeformPrimary(if (item.inProgress) "Resume workout" else "Start workout", {
                    scope.launch(Dispatchers.Main.immediate) {
                        busy = true
                        error = null
                        freeformAttempt { access.startCustom(owner, item.key, LocalDate.now()) }
                            .onSuccess(openWorkout)
                            .onFailure { error = it.freeformMessage("Could not start this workout.") }
                        busy = false
                    }
                }, Modifier.fillMaxWidth().testTag("custom-start-workout"), enabled = !busy)
            }
            if (occurrence != null && !item.inProgress) item {
                FreeformPanel {
                    TextButton(onClick = { editing = CustomEditMode.ONE }, enabled = !busy,
                        modifier = Modifier.testTag("custom-reschedule")) { Text("Reschedule this workout") }
                    if (occurrence.weekly) {
                        TextButton(onClick = { editing = CustomEditMode.FUTURE }, enabled = !busy,
                            modifier = Modifier.testTag("custom-edit-future")) { Text("Edit this and future workouts") }
                    }
                    TextButton(onClick = { remove = CustomEditMode.ONE }, enabled = !busy,
                        modifier = Modifier.testTag("custom-remove-one")) {
                        Text(if (occurrence.weekly) "Skip this workout" else "Remove this workout",
                            color = freeformPalette.highCoral)
                    }
                    if (occurrence.weekly) {
                        TextButton(onClick = { remove = CustomEditMode.FUTURE }, enabled = !busy,
                            modifier = Modifier.testTag("custom-remove-future")) {
                            Text("Remove this and future workouts", color = freeformPalette.highCoral)
                        }
                    }
                }
            }
            item { FreeformError(error) }
        }
    }
    val removal = remove
    if (removal != null && schedule != null) {
        AlertDialog(onDismissRequest = { remove = null },
            title = { Text(if (removal == CustomEditMode.FUTURE) "Remove this and future workouts?" else
                if (occurrence?.weekly == true) "Skip this workout?" else "Remove this workout?") },
            text = { Text("Workouts already in progress or completed stay in Training.") },
            confirmButton = { TextButton(onClick = {
                remove = null
                scope.launch(Dispatchers.Main.immediate) {
                    busy = true
                    val action = if (removal == CustomEditMode.FUTURE) CustomScheduleEdit.Future(null, schedule.modifiedAt)
                    else CustomScheduleEdit.One(null, schedule.modifiedAt)
                    freeformAttempt { access.changeSchedule(owner, item.key, action) }
                        .onSuccess { changed(it); returnToDate(item.date) }
                        .onFailure { error = it.freeformMessage("Could not remove this workout.") }
                    busy = false
                }
            }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } })
    }
}

private enum class CustomEditMode { ONE, FUTURE }
