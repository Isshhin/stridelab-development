package io.stridelab.privatealpha

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

internal val freeformPalette = StrideLabVisualPalette

internal val ManualDiscipline.markColor: Color get() = when (this) {
    ManualDiscipline.PLYOMETRICS -> freeformPalette.moderateAmber
    ManualDiscipline.ISOMETRICS -> freeformPalette.easyTeal
    else -> freeformPalette.disciplineColor(trainingTrackKey)
}

@Composable
internal fun FreeformPage(
    title: String,
    subtitle: String?,
    back: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(StrideLabSurfaceTokens.canvas)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = back,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = StrideLabSurfaceTokens.ink)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = StrideLabSurfaceTokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = StrideLabSurfaceTokens.inkSecondary,
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailingAction?.invoke()
        }
        StrideLabHairline()
        content()
    }
}

@Composable
internal fun FreeformPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = StrideLabSurfaceTokens.surfaceElevated,
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
internal fun FreeformPrimary(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StrideLabSurfaceTokens.lime,
            contentColor = StrideLabSurfaceTokens.onLime
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun FreeformError(message: String?) {
    if (message != null) {
        Text(
            message,
            color = freeformPalette.highCoral,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("freeform-error")
        )
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FreeformHub(
    owner: String,
    board: FreeformBoard?,
    readError: String?,
    access: FreeformAccess,
    back: () -> Unit,
    retry: () -> Unit,
    update: (FreeformWorkout) -> Unit,
    removeDraft: (String) -> Unit,
    restored: (FreeformBoard) -> Unit,
    openTemplates: () -> Unit,
    startEmpty: () -> Unit,
    openDraft: (String) -> Unit
) {
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val showSkeleton = rememberDelayedSkeletonVisible(board == null && readError == null)

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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showSkeleton) {
                items(3, key = { "hub-skeleton-$it" }) { SkeletonWorkoutRow() }
            }
            if (readError != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FreeformError(readError)
                        TextButton(onClick = retry, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Retry", color = StrideLabSurfaceTokens.lime)
                        }
                    }
                }
            }
            if (drafts.isNotEmpty()) {
                item {
                    StrideLabSectionHeader(
                        title = "In progress",
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                itemsIndexed(drafts, key = { _, item -> item.id }) { index, draft ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("freeform-resume-${draft.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .then(if (index == 0) Modifier.testTag("freeform-resume") else Modifier)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !working) { openDraft(draft.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(draft.discipline.markColor)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Resume ${draft.title.lowercase()}",
                                        color = StrideLabSurfaceTokens.ink,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${draft.discipline.label} · ${draft.performedOn}",
                                        color = StrideLabSurfaceTokens.inkSecondary,
                                        fontSize = 12.5.sp,
                                        style = TabularTextStyle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (!working) scope.launch(Dispatchers.Main.immediate) {
                                        working = true
                                        error = null
                                        freeformAttempt { access.discard(owner, draft.id) }.onSuccess { removed ->
                                            if (removed) removeDraft(draft.id)
                                            else error = "Could not discard this draft."
                                        }.onFailure {
                                            error = it.freeformMessage("Could not discard the draft.")
                                        }
                                        working = false
                                    }
                                },
                                enabled = !working,
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("freeform-discard-row-${draft.id}")
                                    .semantics { contentDescription = "Discard ${draft.title}" }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Discard ${draft.title}",
                                    tint = freeformPalette.highCoral
                                )
                            }
                        }
                        StrideLabHairline()
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                FreeformPrimary(
                    label = "Start empty workout",
                    onClick = startEmpty,
                    modifier = Modifier.fillMaxWidth().testTag("freeform-log"),
                    enabled = board != null && !working
                )
            }
            item {
                val count = board?.templates?.size ?: 0
                if (board != null) {
                    StrideLabFlatRow(
                        title = "Workout templates",
                        subtitle = if (count == 0) "Create a routine to start or schedule" else "$count saved · Start or schedule",
                        accentColor = StrideLabSurfaceTokens.lime,
                        onClick = openTemplates,
                        modifier = Modifier.testTag("freeform-open-templates"),
                        trailing = {
                            Text("›", color = StrideLabSurfaceTokens.inkMuted, fontSize = 20.sp)
                        }
                    )
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showBackup = !showBackup }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Workout backup",
                            color = StrideLabSurfaceTokens.inkSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (showBackup) "Hide" else "Import / Export",
                            color = StrideLabSurfaceTokens.inkMuted,
                            fontSize = 12.5.sp
                        )
                    }
                    if (showBackup) {
                        Column(
                            modifier = Modifier.padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Restore workouts to the same profile.",
                                color = StrideLabSurfaceTokens.inkMuted,
                                fontSize = 12.5.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(
                                    onClick = { exporter.launch("stridelab-workouts.json") },
                                    enabled = board != null && !working,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Export backup", color = StrideLabSurfaceTokens.lime)
                                }
                                TextButton(
                                    onClick = {
                                        importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                                    },
                                    enabled = board != null && !working,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Restore backup", color = StrideLabSurfaceTokens.ink)
                                }
                            }
                        }
                    }
                    StrideLabHairline()
                }
                if (working) LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = StrideLabSurfaceTokens.lime,
                    trackColor = StrideLabSurfaceTokens.surfaceSubtle
                )
                if (message != null) {
                    Text(
                        message.orEmpty(),
                        color = StrideLabSurfaceTokens.inkSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                FreeformError(error)
            }
        }
    }
}

@Composable
internal fun FreeformTile(
    title: String,
    supporting: String? = null,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StrideLabFlatRow(
        title = title,
        subtitle = supporting,
        accentColor = color,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            Text("›", color = StrideLabSurfaceTokens.inkMuted, style = MaterialTheme.typography.titleLarge)
        }
    )
}

@Composable
internal fun FreeformHistoryDetail(
    workout: FreeformWorkout?,
    legacy: ManualDisciplineSession?,
    loading: Boolean,
    onSaveAsTemplate: (() -> Unit)? = null,
    onRepeatWorkout: (() -> Unit)? = null,
    back: () -> Unit
) {
    val title = workout?.title ?: legacy?.title ?: "Workout"
    val subtitle = workout?.let { "${it.discipline.label} · ${it.sourceLabel} · ${it.performedOn}" }
        ?: legacy?.let { "${it.discipline.label} · Manual · ${it.performedOn}" }
    val showSkeleton = rememberDelayedSkeletonVisible(loading && workout == null && legacy == null)
    val catalog = rememberAugmentedExerciseCatalog()
    BackHandler(onBack = back)
    FreeformPage(
        title = title,
        subtitle = subtitle,
        back = back,
        trailingAction = {
            TextButton(
                onClick = back,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("freeform-history-done")
            ) {
                Text(
                    text = "Done",
                    color = StrideLabSurfaceTokens.lime,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (workout != null) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            workout.summary(),
                            color = StrideLabSurfaceTokens.ink,
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("freeform-history-summary")
                        )
                        workout.custom?.let { binding ->
                            Text(
                                "Scheduled ${customDateLabel(binding.date)}",
                                color = StrideLabSurfaceTokens.inkSecondary,
                                style = TabularTextStyle
                            )
                        }
                        if (workout.effort.isNotBlank()) {
                            Text(
                                "Workout effort ${workout.effort}/10",
                                color = StrideLabSurfaceTokens.inkSecondary,
                                style = TabularTextStyle
                            )
                        }
                        if (workout.notes.isNotBlank()) {
                            Text(workout.notes, color = StrideLabSurfaceTokens.inkSecondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        StrideLabHairline()
                    }
                }
                item(key = "freeform-history-muscle-focus") {
                    val workoutFocus = remember(workout, catalog) {
                        MuscleFocusProjector.forConfirmedFreeformWorkout(workout, catalog)
                    }
                    SessionMuscleFocusSection(
                        focus = workoutFocus,
                        provenance = "Based on ${workoutFocus.setCount} confirmed sets"
                    )
                }
                if (onSaveAsTemplate != null || (onRepeatWorkout != null && workout.custom != null)) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onSaveAsTemplate != null) {
                                OutlinedButton(
                                    onClick = onSaveAsTemplate,
                                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("freeform-save-as-template"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Save as template", color = StrideLabSurfaceTokens.lime)
                                }
                            }
                            if (onRepeatWorkout != null && workout.custom != null) {
                                OutlinedButton(
                                    onClick = onRepeatWorkout,
                                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("custom-repeat-workout"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Repeat workout", color = StrideLabSurfaceTokens.lime)
                                }
                            }
                        }
                    }
                }
                val historyCatalog = catalog
                items(workout.exercises.filter { exercise -> exercise.sets.any { it.completed } }, key = { it.id }) { exercise ->
                    val exerciseDiscipline = remember(exercise, workout.discipline, historyCatalog) {
                        if (workout.discipline == ManualDiscipline.RUNNING) workout.discipline
                        else FreeformExerciseCatalog.resolveExerciseDiscipline(exercise, historyCatalog, workout.discipline)
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            exercise.name,
                            color = StrideLabSurfaceTokens.ink,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (exerciseDiscipline == ManualDiscipline.CONDITIONING) {
                            Text(
                                listOfNotNull(
                                    exercise.format.label,
                                    exercise.blockMinutes.takeIf { it.isNotBlank() }?.let { "$it min cap" }
                                ).joinToString(" · "),
                                color = StrideLabSurfaceTokens.inkSecondary,
                                style = TabularTextStyle
                            )
                        }
                        if (exercise.notes.isNotBlank()) {
                            Text(exercise.notes, color = StrideLabSurfaceTokens.inkSecondary)
                        }
                        exercise.sets.forEachIndexed { index, set ->
                            if (set.completed) {
                                val label = if (exerciseDiscipline == ManualDiscipline.CONDITIONING) "Round" else "Set"
                                val actual = set.actual.label(exerciseDiscipline).ifBlank { "Completed" }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "$label ${index + 1}",
                                        color = StrideLabSurfaceTokens.inkSecondary,
                                        style = TabularTextStyle
                                    )
                                    Text(
                                        actual,
                                        color = StrideLabSurfaceTokens.ink,
                                        style = TabularTextStyle,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                val context = listOfNotNull(
                                    set.target.label(exerciseDiscipline).takeIf { it.isNotBlank() }?.let { "Target $it" },
                                    set.restSeconds.takeIf { it.isNotBlank() }?.let { "Rest $it s" },
                                    set.rpe.takeIf { it.isNotBlank() }?.let { "RPE $it" },
                                    set.rir.takeIf { it.isNotBlank() }?.let { "RIR $it" }
                                )
                                if (context.isNotEmpty()) {
                                    Text(
                                        context.joinToString(" · "),
                                        color = StrideLabSurfaceTokens.inkMuted,
                                        fontSize = 12.sp,
                                        style = TabularTextStyle
                                    )
                                }
                                if (set.notes.isNotBlank()) {
                                    Text(set.notes, color = StrideLabSurfaceTokens.inkSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        StrideLabHairline()
                    }
                }
            } else if (legacy != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (legacy.notes.isNotBlank()) {
                            Text(legacy.notes, color = StrideLabSurfaceTokens.inkSecondary)
                        }
                        legacy.exercises.forEach { exercise ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    exercise.name,
                                    color = StrideLabSurfaceTokens.ink,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    listOfNotNull(
                                        "${exercise.sets} sets",
                                        exercise.reps?.let { "$it reps" },
                                        exercise.seconds?.let { "$it s" },
                                        exercise.weightKg?.let { "${freeformNumber(it)} kg" }
                                    ).joinToString(" · "),
                                    color = StrideLabSurfaceTokens.inkSecondary,
                                    style = TabularTextStyle
                                )
                            }
                            StrideLabHairline()
                        }
                    }
                }
            } else {
                item {
                    if (showSkeleton) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkeletonWorkoutRow()
                            SkeletonWorkoutRow()
                        }
                    } else if (!loading) {
                        Text(
                            "This workout is unavailable in the current profile.",
                            color = StrideLabSurfaceTokens.inkSecondary
                        )
                    }
                }
            }
        }
    }
}
