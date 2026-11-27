package io.stridelab.privatealpha

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal enum class PickerScope(
    val label: String,
    val discipline: ManualDiscipline? = null,
    val tagSuffix: String
) {
    ALL("All", null, "all"),
    RECENT("Recent", null, "recent"),
    STRENGTH("Strength", ManualDiscipline.STRENGTH, "strength"),
    CALISTHENICS("Calisthenics", ManualDiscipline.CALISTHENICS, "calisthenics"),
    PLYOMETRICS("Plyometrics", ManualDiscipline.PLYOMETRICS, "plyometrics"),
    ISOMETRICS("Isometrics", ManualDiscipline.ISOMETRICS, "isometrics"),
    CONDITIONING("Conditioning", ManualDiscipline.CONDITIONING, "conditioning"),
    MOBILITY("Mobility", null, "mobility")
}

internal data class PickerExerciseOption(
    val id: String?,
    val name: String,
    val subtitle: String,
    val categories: List<String>,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val discipline: ManualDiscipline = ManualDiscipline.STRENGTH
)

internal object FreeformExerciseCatalog {
    fun builtinRecords(): List<ExerciseLibraryRecordV2> = ExpandedExerciseCatalog.allRecords()

    fun isRunningRecord(record: ExerciseLibraryRecordV2): Boolean =
        record.categories.any { it.equals("running", ignoreCase = true) }

    fun augment(records: List<ExerciseLibraryRecordV2>): List<ExerciseLibraryRecordV2> {
        val nonRunning = records.filterNot(::isRunningRecord)
        val byId = nonRunning.mapTo(HashSet()) { it.id.trim().lowercase(Locale.ROOT) }
        val byName = nonRunning.mapTo(HashSet()) { it.name.trim().lowercase(Locale.ROOT) }
        val additions = builtinRecords().filter {
            it.id.trim().lowercase(Locale.ROOT) !in byId &&
                it.name.trim().lowercase(Locale.ROOT) !in byName
        }
        return nonRunning + additions
    }

    fun matchesScope(record: ExerciseLibraryRecordV2, scope: PickerScope): Boolean {
        if (isRunningRecord(record)) return false
        return when (scope) {
            PickerScope.ALL, PickerScope.RECENT -> true
            PickerScope.MOBILITY -> record.categories.any { it.equals("mobility", ignoreCase = true) }
            else -> scope.discipline?.let { matchesDiscipline(record, it) } ?: true
        }
    }

    private fun tokenizeNormalized(raw: String): List<String> =
        raw.lowercase(Locale.ROOT)
            .replace('-', ' ')
            .replace('_', ' ')
            .split(Regex("\\s+"))
            .map { it.trim(',', '.', '(', ')', '/', ':') }
            .filter { it.isNotEmpty() }

    private fun tokenVariants(token: String): List<String> = buildList {
        add(token)
        when {
            token == "flyes" -> add("fly")
            token.endsWith("ies") && token.length > 4 -> add(token.dropLast(3) + "y")
            token.endsWith("es") && token.length > 4 -> {
                add(token.dropLast(1))
                if (token.endsWith("sses") || token.endsWith("ches") || token.endsWith("shes") || token.endsWith("xes")) {
                    add(token.dropLast(2))
                }
            }
            token.endsWith("s") && token.length > 3 -> add(token.dropLast(1))
        }
    }

    private fun matchesQuery(record: ExerciseLibraryRecordV2, search: String): Boolean {
        if (search.isBlank()) return true
        val tokens = tokenizeNormalized(search)
        if (tokens.isEmpty()) return true
        val muscles = record.primaryMuscles + record.secondaryMuscles
        val haystack = buildString {
            append(record.name)
            append(' ')
            append(record.movementPatterns.joinToString(" "))
            append(' ')
            append(record.categories.joinToString(" "))
            append(' ')
            muscles.forEach { key ->
                append(key)
                append(' ')
                append(ExerciseMuscleCatalog.formatLabel(key))
                append(' ')
            }
        }.lowercase(Locale.ROOT).replace('-', ' ').replace('_', ' ')
        return tokens.all { token ->
            tokenVariants(token).any { variant -> haystack.contains(variant) }
        }
    }

    fun matchesDiscipline(record: ExerciseLibraryRecordV2, discipline: ManualDiscipline): Boolean {
        if (isRunningRecord(record)) return false
        val cats = record.categories.map { it.lowercase(Locale.ROOT) }.toSet()
        val n = record.name.lowercase(Locale.ROOT)
        return when (discipline) {
            ManualDiscipline.STRENGTH -> "strength_gym" in cats || "bodyweight" in cats
            ManualDiscipline.CALISTHENICS -> "calisthenics" in cats || "bodyweight" in cats
            ManualDiscipline.CONDITIONING -> "conditioning" in cats
            ManualDiscipline.PLYOMETRICS -> "plyometrics" in cats || "plyometric" in cats ||
                n.contains("jump") || n.contains("hop") || n.contains("bound") || n.contains("skip")
            ManualDiscipline.ISOMETRICS -> "isometrics" in cats || "isometric" in cats ||
                n.contains("hold") || n.contains("plank") || n.contains("wall sit") || n.contains("hang")
            ManualDiscipline.RUNNING -> false
        }
    }

    fun resolveRecordDiscipline(record: ExerciseLibraryRecordV2): ManualDiscipline = when {
        matchesDiscipline(record, ManualDiscipline.PLYOMETRICS) -> ManualDiscipline.PLYOMETRICS
        matchesDiscipline(record, ManualDiscipline.ISOMETRICS) -> ManualDiscipline.ISOMETRICS
        matchesDiscipline(record, ManualDiscipline.CONDITIONING) -> ManualDiscipline.CONDITIONING
        matchesDiscipline(record, ManualDiscipline.CALISTHENICS) &&
            "strength_gym" !in record.categories.map { it.lowercase(Locale.ROOT) } -> ManualDiscipline.CALISTHENICS
        else -> ManualDiscipline.STRENGTH
    }

    fun findRecord(
        exercise: FreeformExercise,
        catalog: List<ExerciseLibraryRecordV2>
    ): ExerciseLibraryRecordV2? {
        val augmented = augment(catalog)
        return exercise.catalogId?.let { id -> augmented.firstOrNull { it.id == id } }
            ?: augmented.firstOrNull { it.name.equals(exercise.name.trim(), ignoreCase = true) }
    }

    fun resolveExerciseDiscipline(
        exercise: FreeformExercise,
        catalog: List<ExerciseLibraryRecordV2>,
        fallback: ManualDiscipline = ManualDiscipline.STRENGTH
    ): ManualDiscipline {
        val found = findRecord(exercise, catalog)
        if (found != null) return resolveRecordDiscipline(found)
        val n = exercise.name.lowercase(Locale.ROOT)
        return when {
            n.contains("jump") || n.contains("hop") || n.contains("bound") -> ManualDiscipline.PLYOMETRICS
            n.contains("hold") || n.contains("plank") || n.contains("wall sit") || n.contains("hang") -> ManualDiscipline.ISOMETRICS
            else -> if (fallback == ManualDiscipline.RUNNING) ManualDiscipline.STRENGTH else fallback
        }
    }

    fun isCustomExercise(exercise: FreeformExercise, catalog: List<ExerciseLibraryRecordV2>): Boolean =
        exercise.catalogId == null && findRecord(exercise, catalog) == null

    private fun subtitleFor(record: ExerciseLibraryRecordV2, fallbackDiscipline: ManualDiscipline): String {
        val primary = record.movementPatterns.firstOrNull()?.takeIf { it.isNotBlank() }
        val muscleTag = ExerciseMuscleCatalog.formatPrimaryTag(record.primaryMuscles)
        if (primary != null && primary.contains("·")) {
            return listOfNotNull(primary, muscleTag).joinToString(" · ")
        }
        val cat = when {
            "plyometrics" in record.categories -> "Plyometrics"
            "isometrics" in record.categories -> "Isometrics"
            "strength_gym" in record.categories -> "Strength"
            "calisthenics" in record.categories -> "Calisthenics"
            "conditioning" in record.categories -> "Conditioning"
            "mobility" in record.categories -> "Mobility"
            "bodyweight" in record.categories -> "Bodyweight"
            else -> fallbackDiscipline.label
        }
        return listOfNotNull(
            cat,
            primary?.replace('_', ' ')?.replaceFirstChar { it.uppercase(Locale.ROOT) },
            muscleTag
        ).joinToString(" · ")
    }

    fun filter(
        catalog: List<ExerciseLibraryRecordV2>,
        scope: PickerScope,
        query: String,
        recentNames: List<String>
    ): List<PickerExerciseOption> {
        val search = query.trim()
        val augmented = augment(catalog)
        if (scope == PickerScope.RECENT) {
            return recentNames
                .filter { search.isBlank() || it.contains(search, ignoreCase = true) }
                .map { name ->
                    val found = augmented.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    val resolved = found?.let(::resolveRecordDiscipline) ?: ManualDiscipline.STRENGTH
                    PickerExerciseOption(
                        id = found?.id,
                        name = name,
                        subtitle = found?.let { subtitleFor(it, resolved) } ?: "Recent",
                        categories = found?.categories.orEmpty(),
                        primaryMuscles = found?.primaryMuscles.orEmpty(),
                        secondaryMuscles = found?.secondaryMuscles.orEmpty(),
                        discipline = resolved
                    )
                }
        }

        val targetDiscipline = scope.discipline
        return augmented.asSequence()
            .filter { record ->
                matchesScope(record, scope) && matchesQuery(record, search)
            }
            .sortedWith(
                compareBy<ExerciseLibraryRecordV2> { it.name.lowercase(Locale.ROOT) }
            )
            .map { record ->
                val resolved = targetDiscipline ?: resolveRecordDiscipline(record)
                PickerExerciseOption(
                    id = record.id,
                    name = record.name,
                    subtitle = subtitleFor(record, resolved),
                    categories = record.categories,
                    primaryMuscles = record.primaryMuscles,
                    secondaryMuscles = record.secondaryMuscles,
                    discipline = resolved
                )
            }
            .toList()
    }
}

private val loggerHeaderDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

private fun FreeformWorkout.formattedDateLabel(): String =
    date?.format(loggerHeaderDateFormatter) ?: performedOn

private fun FreeformWorkout.withInferredDiscipline(catalog: List<ExerciseLibraryRecordV2>): FreeformWorkout {
    if (discipline == ManualDiscipline.RUNNING) return this
    val confirmedDisciplines = exercises
        .filter { ex -> ex.sets.any { it.completed } }
        .map { ex -> FreeformExerciseCatalog.resolveExerciseDiscipline(ex, catalog, discipline) }
        .distinct()
    val inferred = confirmedDisciplines.singleOrNull() ?: ManualDiscipline.STRENGTH
    val normalizedExercises = if (inferred == ManualDiscipline.CONDITIONING) {
        exercises
    } else {
        exercises.map { it.copy(format = ConditioningFormat.INTERVALS, blockMinutes = "") }
    }
    return copy(discipline = inferred, exercises = normalizedExercises)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FreeformEditor(
    initial: FreeformWorkout,
    board: FreeformBoard,
    access: FreeformAccess,
    persisted: Boolean = true,
    update: (FreeformWorkout) -> Unit,
    remove: (String) -> Unit,
    leave: () -> Unit,
    saved: (String) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var isPersisted by rememberSaveable(initial.id) { mutableStateOf(persisted) }
    var persistedAt by remember(initial.id) {
        mutableLongStateOf(if (persisted) initial.modifiedAt else 0L)
    }
    var working by remember(initial.id) { mutableStateOf(false) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    var review by rememberSaveable(initial.id) { mutableStateOf(false) }
    var picker by rememberSaveable(initial.id) { mutableStateOf(false) }
    var details by rememberSaveable(initial.id) { mutableStateOf(false) }
    var discard by rememberSaveable(initial.id) { mutableStateOf(false) }
    var finished by rememberSaveable(initial.id) { mutableStateOf(initial.isComplete) }
    var weightUnit by rememberSaveable(initial.id) { mutableStateOf(FreeformWeightUnit.KG) }
    var inspectExercise by remember(initial.id) { mutableStateOf<FreeformExercise?>(null) }
    val exerciseCatalog = rememberAugmentedExerciseCatalog()

    // Unobtrusive non-blocking rest timer state (session-scoped)
    var restPresetSeconds by rememberSaveable(initial.id) { mutableIntStateOf(90) }
    var restRemainingSeconds by rememberSaveable(initial.id) { mutableIntStateOf(0) }
    var restRunning by rememberSaveable(initial.id) { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val persistMutex = remember(initial.id) { Mutex() }

    val recentExercises = remember(board.workouts, draft.exercises) {
        val existingNames = draft.exercises.map { it.name.trim().lowercase(Locale.ROOT) }.toSet()
        board.workouts.asSequence()
            .filter { it.isComplete && it.discipline != ManualDiscipline.RUNNING }
            .flatMap { it.exercises.asSequence() }
            .map { it.name.trim() }
            .filter { it.isNotEmpty() && it.lowercase(Locale.ROOT) !in existingNames }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(8)
            .toList()
    }

    LaunchedEffect(restRunning, restRemainingSeconds) {
        if (restRunning && restRemainingSeconds > 0) {
            delay(1000L)
            val next = restRemainingSeconds - 1
            restRemainingSeconds = next
            if (next <= 0) restRunning = false
        }
    }

    suspend fun persistDraft(candidate: FreeformWorkout): FreeformWorkout = persistMutex.withLock {
        when {
            !isPersisted -> access.start(candidate).also { stored ->
                isPersisted = true
                persistedAt = stored.modifiedAt
            }
            candidate.modifiedAt > persistedAt -> access.save(candidate).also { stored ->
                persistedAt = maxOf(persistedAt, stored.modifiedAt)
            }
            else -> candidate
        }
    }

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
        if (finished || draft.isComplete) {
            leave()
            return
        }
        if (!isPersisted && draft == initial) {
            leave()
            return
        }
        scope.launch(Dispatchers.Main.immediate) {
            working = true
            error = null
            freeformAttempt { persistDraft(draft) }.onSuccess {
                update(it)
                if (it.isComplete) {
                    finished = true
                    saved(it.id)
                } else {
                    leave()
                }
            }.onFailure { error = it.freeformMessage("Could not save your draft. Try again.") }
            working = false
        }
    }

    fun back() {
        if (working) return
        if (review) {
            review = false
            error = null
        } else {
            saveAndLeave()
        }
    }

    BackHandler { back() }
    LaunchedEffect(draft.modifiedAt, working, isPersisted, finished) {
        val pending = draft
        val shouldPersist = (isPersisted && pending.modifiedAt > persistedAt) ||
            (!isPersisted && pending != initial)
        if (!working && !finished && !pending.isComplete && shouldPersist) {
            delay(350)
            freeformAttempt { persistDraft(pending) }.onSuccess { stored ->
                update(stored)
            }.onFailure { error = it.freeformMessage("Draft changes could not be saved.") }
        }
    }

    if (review) {
        val reviewSubtitle = if (draft.discipline == ManualDiscipline.RUNNING) {
            "${draft.discipline.label} · ${draft.formattedDateLabel()}"
        } else {
            draft.formattedDateLabel()
        }
        FreeformPage("Review workout", reviewSubtitle, ::back) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FreeformPanel {
                        Text(
                            "Recorded totals",
                            color = StrideLabSurfaceTokens.inkSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            FreeformWeightConverter.formatWorkoutSummary(draft, weightUnit),
                            color = StrideLabSurfaceTokens.ink,
                            style = MaterialTheme.typography.headlineSmall.tabular(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("freeform-review-totals")
                        )
                        if (draft.exercises.any { exercise -> exercise.sets.any { !it.completed } }) {
                            Text(
                                "Only confirmed sets and rounds count.",
                                color = StrideLabSurfaceTokens.inkSecondary
                            )
                        }
                        if (draft.effort.isNotBlank()) {
                            Text(
                                "Workout effort ${draft.effort}/10",
                                color = StrideLabSurfaceTokens.inkSecondary,
                                style = TabularTextStyle
                            )
                        }
                        if (draft.notes.isNotBlank()) {
                            Text(draft.notes, color = StrideLabSurfaceTokens.inkSecondary)
                        }
                    }
                }
                draft.exercises.filter { exercise -> exercise.sets.any { it.completed } }.forEach { exercise ->
                    item(key = exercise.id) {
                        val exDiscipline = FreeformExerciseCatalog.resolveExerciseDiscipline(
                            exercise, exerciseCatalog, draft.discipline
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                exercise.name,
                                color = StrideLabSurfaceTokens.ink,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            exercise.sets.filter { it.completed }.forEach { set ->
                                Text(
                                    FreeformWeightConverter.formatMeasureLabel(set.actual, exDiscipline, weightUnit)
                                        .ifBlank { "Round completed" },
                                    color = StrideLabSurfaceTokens.inkSecondary,
                                    style = TabularTextStyle
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            StrideLabHairline()
                        }
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(StrideLabSurfaceTokens.surfaceElevated)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                FreeformError(error)
                FreeformPrimary(
                    "Save workout",
                    {
                        scope.launch(Dispatchers.Main.immediate) {
                            working = true
                            error = null
                            freeformAttempt {
                                val normalized = draft.withInferredDiscipline(exerciseCatalog)
                                val target = persistDraft(normalized)
                                persistMutex.withLock { access.finish(target) }
                            }.onSuccess {
                                finished = true
                                update(it)
                                saved(it.id)
                            }.onFailure {
                                error = it.freeformMessage("Could not finish your workout. Your draft is still saved.")
                            }
                            working = false
                        }
                    },
                    Modifier.fillMaxWidth().testTag("freeform-save-completed"),
                    enabled = !working
                )
            }
        }
        return
    }

    val editorContext = LocalContext.current
    val confirmedSets = remember(draft.exercises) {
        draft.exercises.flatMap { it.sets }.filter { it.completed }
    }
    val hasConfirmedSet = confirmedSets.isNotEmpty() || (
        draft.discipline == ManualDiscipline.RUNNING &&
            draft.distanceKm.isNotBlank() && draft.durationMinutes.isNotBlank()
        )
    val confirmedVolume = remember(confirmedSets) {
        confirmedSets.sumOf { set ->
            val q = set.actual.quantity.trim().toIntOrNull() ?: 0
            val kg = set.actual.kg.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
            q * kg
        }
    }
    val confirmedHoldSec = remember(confirmedSets) {
        confirmedSets.sumOf { it.actual.seconds.trim().toIntOrNull() ?: 0 }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(StrideLabSurfaceTokens.canvas)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = ::back)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = PlanBuilderPalette.surface,
                    border = BorderStroke(1.dp, PlanBuilderPalette.border)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "‹",
                            color = StrideLabSurfaceTokens.ink,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = draft.workoutName.ifBlank {
                        if (draft.discipline == ManualDiscipline.RUNNING) draft.discipline.label else "Log Workout"
                    },
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = draft.formattedDateLabel(),
                    color = StrideLabSurfaceTokens.inkSecondary,
                    fontSize = 12.5.sp,
                    style = TabularTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = {
                    if (!isPersisted && draft == initial) {
                        remove(draft.id)
                        leave()
                    } else {
                        discard = true
                    }
                },
                enabled = !working,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("freeform-discard")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Discard draft",
                    tint = StrideLabSurfaceTokens.inkSecondary
                )
            }
        }
        StrideLabHairline()

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("freeform-editor"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (draft.discipline == ManualDiscipline.RUNNING) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Run details",
                            color = StrideLabSurfaceTokens.ink,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val targets = listOfNotNull(
                            draft.targetDistanceKm.takeIf { it.isNotBlank() }?.let { "$it km" },
                            draft.targetDurationMinutes.takeIf { it.isNotBlank() }?.let { "$it min" }
                        )
                        if (targets.isNotEmpty()) {
                            Text(
                                "Target · ${targets.joinToString(" · ")}",
                                color = StrideLabSurfaceTokens.inkSecondary,
                                style = MaterialTheme.typography.bodyMedium.tabular(),
                                modifier = Modifier.testTag("freeform-run-targets")
                            )
                        }
                        FreeformField(
                            "Distance (km)",
                            draft.distanceKm,
                            { edit(draft.copy(distanceKm = it)) },
                            Modifier.fillMaxWidth(),
                            "freeform-run-distance",
                            decimal = true
                        )
                        FreeformField(
                            "Duration (min)",
                            draft.durationMinutes,
                            { edit(draft.copy(durationMinutes = it)) },
                            Modifier.fillMaxWidth(),
                            "freeform-run-duration",
                            decimal = true
                        )
                        FreeformField(
                            "Workout type (optional)",
                            draft.runType,
                            { edit(draft.copy(runType = it)) },
                            Modifier.fillMaxWidth(),
                            "freeform-run-type",
                            limit = 80
                        )
                        StrideLabHairline()
                    }
                }
            } else {
                item(key = "freeform-live-summary") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = PlanBuilderPalette.surface,
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LoggerSummaryMetric(
                                label = "EXERCISES",
                                value = draft.exercises.size.toString(),
                                accent = false,
                                modifier = Modifier.weight(1f)
                            )
                            LoggerSummaryMetric(
                                label = "CONFIRMED",
                                value = "${confirmedSets.size} sets",
                                accent = confirmedSets.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            )
                            val thirdLabel = if (confirmedVolume <= 0.0 && confirmedHoldSec > 0) "TIME" else "VOLUME"
                            val thirdValue = when {
                                confirmedVolume > 0.0 -> FreeformWeightConverter.formatVolume(confirmedVolume, weightUnit)
                                confirmedHoldSec > 0 -> "${confirmedHoldSec}s"
                                else -> "—"
                            }
                            LoggerSummaryMetric(
                                label = thirdLabel,
                                value = thirdValue,
                                accent = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (draft.exercises.isEmpty()) {
                    item(key = "freeform-empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("freeform-empty-state"),
                                shape = RoundedCornerShape(18.dp),
                                color = PlanBuilderPalette.surface,
                                border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    StrideLabSurfaceTokens.lime.copy(alpha = 0.06f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "UNIVERSAL WORKOUT",
                                        color = StrideLabSurfaceTokens.lime,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                    Text(
                                        "Ready to log",
                                        color = StrideLabSurfaceTokens.ink,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.4).sp
                                    )
                                    Text(
                                        "Mix strength, calisthenics, plyometrics, isometrics, or conditioning in one session.",
                                        color = StrideLabSurfaceTokens.inkSecondary,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Button(
                                        onClick = { picker = true },
                                        enabled = !working,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StrideLabSurfaceTokens.lime,
                                            contentColor = StrideLabSurfaceTokens.onLime
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 50.dp)
                                            .testTag("freeform-add-exercise")
                                    ) {
                                        Text(
                                            "+ Add exercise",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                            if (recentExercises.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "QUICK ADD RECENT",
                                        color = StrideLabSurfaceTokens.inkMuted,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.0.sp
                                    )
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        recentExercises.take(6).forEach { name ->
                                            val matched = remember(name, exerciseCatalog) {
                                                FreeformExerciseCatalog.augment(exerciseCatalog)
                                                    .firstOrNull { it.name.equals(name, ignoreCase = true) }
                                            }
                                            Surface(
                                                onClick = {
                                                    edit(
                                                        draft.copy(
                                                            exercises = draft.exercises + FreeformExercise(
                                                                name = name,
                                                                catalogId = matched?.id
                                                            )
                                                        )
                                                    )
                                                },
                                                enabled = !working,
                                                shape = RoundedCornerShape(100.dp),
                                                color = StrideLabSurfaceTokens.surfaceSubtle,
                                                border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("freeform-quick-add-$name")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        "+",
                                                        color = StrideLabSurfaceTokens.lime,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                    Text(
                                                        name,
                                                        color = StrideLabSurfaceTokens.ink,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(draft.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                        val exerciseDiscipline = remember(exercise, exerciseCatalog, draft.discipline) {
                            FreeformExerciseCatalog.resolveExerciseDiscipline(
                                exercise, exerciseCatalog, draft.discipline
                            )
                        }
                        val customExercise = remember(exercise, exerciseCatalog) {
                            FreeformExerciseCatalog.isCustomExercise(exercise, exerciseCatalog)
                        }
                        FreeformExercisePanel(
                            exercise = exercise,
                            discipline = exerciseDiscipline,
                            previous = board.previous(exerciseDiscipline, exercise),
                            hasPrevious = index > 0,
                            hasNext = index < draft.exercises.lastIndex,
                            change = { editExercise(index, it) },
                            move = { offset ->
                                edit(draft.copy(exercises = draft.exercises.toMutableList().apply {
                                    add(index + offset, removeAt(index))
                                }))
                            },
                            delete = {
                                edit(draft.copy(exercises = draft.exercises.filterNot { it.id == exercise.id }))
                            },
                            onInspect = { inspectExercise = exercise },
                            onSetConfirmed = {
                                restRemainingSeconds = restPresetSeconds
                                restRunning = true
                            },
                            allowAllMetrics = customExercise,
                            weightUnit = weightUnit,
                            onToggleWeightUnit = { weightUnit = weightUnit.toggle() }
                        )
                    }
                    item(key = "freeform-add-exercise-btn") {
                        Surface(
                            onClick = { picker = true },
                            enabled = draft.exercises.size < 30 && !working,
                            shape = RoundedCornerShape(14.dp),
                            color = StrideLabSurfaceTokens.surfaceElevated,
                            border = BorderStroke(1.dp, StrideLabSurfaceTokens.lime.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("freeform-add-exercise")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "+ Add exercise",
                                    color = StrideLabSurfaceTokens.lime,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item(key = "freeform-details-section") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = PlanBuilderPalette.surface,
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { details = !details }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Session details",
                                color = StrideLabSurfaceTokens.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val preview = listOfNotNull(
                                draft.workoutName.takeIf { it.isNotBlank() },
                                draft.formattedDateLabel(),
                                draft.effort.takeIf { it.isNotBlank() }?.let { "RPE $it" }
                            ).joinToString(" · ")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    if (details) "Hide" else preview,
                                    color = StrideLabSurfaceTokens.inkMuted,
                                    fontSize = 12.sp,
                                    style = TabularTextStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (details) "▴" else "▾",
                                    color = StrideLabSurfaceTokens.inkMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        if (details) {
                            StrideLabHairline()
                            Column(
                                modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FreeformField(
                                    "Workout name (optional)",
                                    draft.workoutName,
                                    { edit(draft.copy(workoutName = it)) },
                                    Modifier.fillMaxWidth(),
                                    "freeform-workout-name",
                                    limit = 100
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FreeformField(
                                        "Date (YYYY-MM-DD)",
                                        draft.performedOn,
                                        { edit(draft.copy(performedOn = it)) },
                                        Modifier.weight(1f),
                                        "freeform-date"
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val initialDate = draft.date ?: LocalDate.now()
                                            DatePickerDialog(
                                                editorContext,
                                                { _, y, m, d ->
                                                    edit(draft.copy(performedOn = LocalDate.of(y, m + 1, d).toString()))
                                                },
                                                initialDate.year,
                                                initialDate.monthValue - 1,
                                                initialDate.dayOfMonth
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                                        modifier = Modifier.heightIn(min = 52.dp)
                                    ) {
                                        Text("Pick", color = StrideLabSurfaceTokens.lime, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                FreeformField(
                                    "Effort (1–10, optional)",
                                    draft.effort,
                                    { edit(draft.copy(effort = it)) },
                                    Modifier.fillMaxWidth(),
                                    "freeform-workout-effort",
                                    integer = true
                                )
                                FreeformField(
                                    "Workout notes",
                                    draft.notes,
                                    { edit(draft.copy(notes = it)) },
                                    Modifier.fillMaxWidth(),
                                    "freeform-workout-notes",
                                    limit = 2000,
                                    lines = 3
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0B0D))
                .navigationBarsPadding()
        ) {
            if (restRunning && restRemainingSeconds > 0) {
                val denom = maxOf(restPresetSeconds, restRemainingSeconds).coerceAtLeast(1)
                val fraction = (restRemainingSeconds.toFloat() / denom.toFloat()).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(StrideLabSurfaceTokens.hairline)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(StrideLabSurfaceTokens.lime)
                    )
                }
            } else {
                StrideLabHairline()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (draft.discipline != ManualDiscipline.RUNNING) {
                    FreeformRestTimerBar(
                        presetSeconds = restPresetSeconds,
                        remainingSeconds = restRemainingSeconds,
                        running = restRunning,
                        onCyclePreset = {
                            val next = when (restPresetSeconds) {
                                60 -> 90
                                90 -> 120
                                else -> 60
                            }
                            restPresetSeconds = next
                            if (!restRunning && restRemainingSeconds == 0) {
                                restRemainingSeconds = 0
                            }
                        },
                        onToggle = {
                            if (restRunning) {
                                restRunning = false
                            } else {
                                if (restRemainingSeconds <= 0) restRemainingSeconds = restPresetSeconds
                                restRunning = true
                            }
                        },
                        onAddFifteen = {
                            restRemainingSeconds = (if (restRemainingSeconds <= 0) restPresetSeconds else restRemainingSeconds) + 15
                            restRunning = true
                        },
                        onReset = {
                            restRunning = false
                            restRemainingSeconds = 0
                        }
                    )
                }
                FreeformError(error)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = ::saveAndLeave,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PlanBuilderPalette.border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF14161A),
                            contentColor = StrideLabSurfaceTokens.inkSecondary
                        ),
                        modifier = Modifier
                            .weight(0.40f)
                            .heightIn(min = 50.dp)
                            .testTag("freeform-save-progress"),
                        enabled = !working
                    ) {
                        Text("Save draft", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            runCatching {
                                draft.withInferredDiscipline(exerciseCatalog).finish(System.currentTimeMillis())
                            }
                                .onSuccess {
                                    error = null
                                    review = true
                                }
                                .onFailure { error = it.freeformMessage("Check the workout before finishing.") }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = if (hasConfirmedSet) null else BorderStroke(
                            1.dp,
                            StrideLabSurfaceTokens.lime.copy(alpha = 0.35f)
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasConfirmedSet) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.surfaceSubtle,
                            contentColor = if (hasConfirmedSet) StrideLabSurfaceTokens.onLime else StrideLabSurfaceTokens.lime
                        ),
                        modifier = Modifier
                            .weight(0.60f)
                            .heightIn(min = 50.dp)
                            .testTag("freeform-finish"),
                        enabled = !working
                    ) {
                        Text("Finish workout", fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
            }
        }
    }

    if (picker) {
        FreeformExercisePicker(
            recentExerciseNames = recentExercises,
            close = { picker = false }
        ) { exercise ->
            edit(draft.copy(exercises = draft.exercises + exercise))
            picker = false
        }
    }
    inspectExercise?.let { target ->
        val inspectDiscipline = FreeformExerciseCatalog.resolveExerciseDiscipline(
            target, exerciseCatalog, draft.discipline
        )
        ExerciseInfoSheet(
            exerciseId = target.catalogId,
            exerciseName = target.name,
            disciplineLabel = inspectDiscipline.label,
            disciplineTint = inspectDiscipline.markColor,
            catalog = exerciseCatalog,
            onDismiss = { inspectExercise = null }
        )
    }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard this draft?") },
            text = { Text("The saved draft and its sets will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    discard = false
                    if (!isPersisted) {
                        remove(draft.id)
                        leave()
                    } else {
                        scope.launch(Dispatchers.Main.immediate) {
                            working = true
                            freeformAttempt { access.discard(draft.owner, draft.id) }.onSuccess { removed ->
                                if (removed) {
                                    remove(draft.id)
                                    leave()
                                } else {
                                    error = "This draft has already changed. Reopen it from Training."
                                }
                            }.onFailure { error = it.freeformMessage("Could not discard the draft.") }
                            working = false
                        }
                    }
                }) { Text("Discard", color = freeformPalette.highCoral) }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep draft") } }
        )
    }
}

@Composable
private fun LoggerSummaryMetric(
    label: String,
    value: String,
    accent: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = StrideLabSurfaceTokens.inkMuted,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )
        Text(
            text = value,
            color = if (accent) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TabularTextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FreeformRestTimerBar(
    presetSeconds: Int,
    remainingSeconds: Int,
    running: Boolean,
    onCyclePreset: () -> Unit,
    onToggle: () -> Unit,
    onAddFifteen: () -> Unit,
    onReset: () -> Unit
) {
    val active = running || remainingSeconds > 0
    val displaySeconds = if (active) remainingSeconds else presetSeconds
    val formatted = String.format(Locale.US, "%d:%02d", displaySeconds / 60, displaySeconds % 60)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("freeform-rest-timer"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF14161A),
        border = BorderStroke(
            1.dp,
            if (running) StrideLabSurfaceTokens.lime.copy(alpha = 0.45f) else PlanBuilderPalette.border
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCyclePreset)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (running) "REST" else "TIMER",
                    color = if (running) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.inkMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                )
                Text(
                    text = formatted,
                    color = if (active) StrideLabSurfaceTokens.ink else StrideLabSurfaceTokens.inkSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TabularTextStyle
                )
            }
            TextButton(
                onClick = onAddFifteen,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("freeform-rest-plus15"),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("+15s", color = StrideLabSurfaceTokens.inkSecondary, fontSize = 12.sp, style = TabularTextStyle)
            }
            if (active) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("freeform-rest-reset"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Reset", color = StrideLabSurfaceTokens.inkMuted, fontSize = 12.sp)
                }
            }
            TextButton(
                onClick = onToggle,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("freeform-rest-toggle"),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    if (running) "Pause" else "Start",
                    color = StrideLabSurfaceTokens.lime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
internal fun FreeformExercisePanel(
    exercise: FreeformExercise,
    discipline: ManualDiscipline,
    previous: FreeformMeasure?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    change: (FreeformExercise) -> Unit,
    move: (Int) -> Unit,
    delete: () -> Unit,
    authoring: Boolean = false,
    onInspect: (() -> Unit)? = null,
    onSetConfirmed: () -> Unit = {},
    allowAllMetrics: Boolean = false,
    weightUnit: FreeformWeightUnit = FreeformWeightUnit.KG,
    onToggleWeightUnit: () -> Unit = {}
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    val conditioning = discipline == ManualDiscipline.CONDITIONING
    val firstUncompletedIndex = remember(exercise.sets) {
        exercise.sets.indexOfFirst { !it.completed }
    }
    val metrics = remember(discipline, allowAllMetrics) {
        if (allowAllMetrics) listOf(MeasureInput.REPS, MeasureInput.LOAD, MeasureInput.HOLD)
        else measureInputs(discipline)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(discipline.markColor)
            )
            Column(
                Modifier
                    .weight(1f)
                    .then(
                        if (onInspect != null) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onInspect)
                                .padding(vertical = 4.dp)
                        } else Modifier
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    exercise.name,
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (previous != null) {
                    Text(
                        "Previous · ${FreeformWeightConverter.formatMeasureLabel(previous, discipline, weightUnit)}",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 12.sp,
                        style = TabularTextStyle
                    )
                }
            }
            if (onInspect != null) {
                IconButton(
                    onClick = onInspect,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("exercise-info-trigger-${exercise.catalogId ?: exercise.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Exercise info for ${exercise.name}",
                        tint = StrideLabSurfaceTokens.inkSecondary
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "Options for ${exercise.name}",
                        tint = StrideLabSurfaceTokens.inkSecondary
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Move up") }, enabled = hasPrevious, onClick = { menu = false; move(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, enabled = hasNext, onClick = { menu = false; move(1) })
                    DropdownMenuItem(text = { Text("Remove exercise") }, onClick = {
                        menu = false
                        confirmDelete = true
                    })
                }
            }
        }

        // Inline borderless exercise note field
        if (!authoring) {
            BasicTextField(
                value = exercise.notes,
                onValueChange = { if (it.length <= 1000) change(exercise.copy(notes = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("freeform-exercise-notes"),
                singleLine = true,
                cursorBrush = SolidColor(StrideLabSurfaceTokens.lime),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = StrideLabSurfaceTokens.inkSecondary),
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 2.dp)) {
                        if (exercise.notes.isEmpty()) {
                            Text(
                                "Add notes...",
                                color = StrideLabSurfaceTokens.inkMuted,
                                fontSize = 12.5.sp
                            )
                        }
                        inner()
                    }
                }
            )
        }

        if (conditioning) {
            Box {
                Surface(
                    onClick = { showFormat = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF161616),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Format · ${exercise.format.label}", color = StrideLabSurfaceTokens.ink, fontSize = 13.sp)
                    }
                }
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
                FreeformField(
                    label,
                    exercise.blockMinutes,
                    { change(exercise.copy(blockMinutes = it)) },
                    Modifier.fillMaxWidth(),
                    "freeform-block-minutes",
                    decimal = true
                )
            }
        }

        val compact = !authoring
        if (compact && exercise.sets.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (conditioning) "RD" else "SET",
                    Modifier.width(36.dp),
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "PREV",
                    Modifier.width(54.dp),
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                metrics.forEach { metric ->
                    if (metric == MeasureInput.LOAD) {
                        Surface(
                            onClick = onToggleWeightUnit,
                            shape = RoundedCornerShape(8.dp),
                            color = StrideLabSurfaceTokens.surfaceElevated,
                            border = BorderStroke(1.dp, StrideLabSurfaceTokens.lime.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 32.dp)
                                .testTag("freeform-weight-unit-toggle")
                                .semantics {
                                    contentDescription = "Switch weight unit, currently ${weightUnit.shortLabel}"
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${weightUnit.shortLabel} ⇄",
                                    color = StrideLabSurfaceTokens.lime,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text(
                            metric.headerLabel,
                            Modifier.weight(1f),
                            color = StrideLabSurfaceTokens.inkMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    "✓",
                    Modifier.width(48.dp),
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        exercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                FreeformSetRow(
                    set = set,
                    index = index,
                    canRemove = exercise.sets.size > 1,
                    isNextActive = !set.completed && index == firstUncompletedIndex,
                    discipline = discipline,
                    metrics = metrics,
                    format = exercise.format,
                    weightUnit = weightUnit,
                    previousHint = FreeformWeightConverter.formatMeasureLabel(set.target, discipline, weightUnit)
                        .ifBlank {
                            previous?.let { FreeformWeightConverter.formatMeasureLabel(it, discipline, weightUnit) }
                                .orEmpty()
                        },
                    edit = { next ->
                        val becameCompleted = !set.completed && next.completed
                        change(exercise.copy(sets = exercise.sets.toMutableList().apply { set(index, next) }))
                        if (becameCompleted) onSetConfirmed()
                    },
                    remove = { change(exercise.copy(sets = exercise.sets.filterNot { it.id == set.id })) },
                    authoring = authoring,
                    compact = compact
                )
            }
        }

        if (exercise.sets.size < 30) {
            val unit = if (conditioning) "round" else "set"
            Surface(
                onClick = {
                    val last = exercise.sets.lastOrNull()
                    val nextSet = if (last != null) {
                        last.duplicate()
                    } else if (previous != null) {
                        FreeformSet(actual = previous)
                    } else {
                        FreeformSet()
                    }
                    change(exercise.copy(sets = exercise.sets + nextSet))
                },
                shape = RoundedCornerShape(12.dp),
                color = StrideLabSurfaceTokens.surfaceElevated,
                border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("freeform-add-set")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "+ Add $unit",
                        color = StrideLabSurfaceTokens.ink,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        StrideLabHairline()
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${exercise.name}?") },
            text = { Text("The exercise and its sets will be removed from this draft.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; delete() }) {
                    Text("Remove", color = freeformPalette.highCoral)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep exercise") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreeformSetRow(
    set: FreeformSet,
    index: Int,
    canRemove: Boolean,
    isNextActive: Boolean,
    discipline: ManualDiscipline,
    metrics: List<MeasureInput>,
    format: ConditioningFormat,
    weightUnit: FreeformWeightUnit = FreeformWeightUnit.KG,
    previousHint: String,
    edit: (FreeformSet) -> Unit,
    remove: () -> Unit,
    authoring: Boolean,
    compact: Boolean
) {
    var options by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(set.id) { mutableStateOf(false) }
    var setError by remember { mutableStateOf(false) }
    val unit = if (discipline == ManualDiscipline.CONDITIONING) "Round" else "Set"
    var loadInputText by remember(set.id, weightUnit) {
        mutableStateOf(FreeformWeightConverter.kgStringToDisplay(set.actual.kg, weightUnit))
    }
    LaunchedEffect(set.actual.kg, weightUnit) {
        if (weightUnit == FreeformWeightUnit.KG) {
            if (loadInputText != set.actual.kg) loadInputText = set.actual.kg
        } else {
            val currentCanonical = FreeformWeightConverter.displayStringToCanonicalKg(loadInputText, FreeformWeightUnit.LB)
            if (set.actual.kg.isBlank()) {
                if (loadInputText.isNotBlank() && loadInputText != ".") loadInputText = ""
            } else if (currentCanonical != set.actual.kg) {
                val typedNum = loadInputText.trim().replace(',', '.').toDoubleOrNull()
                val storedNum = set.actual.kg.trim().replace(',', '.').toDoubleOrNull()
                if (typedNum == null || storedNum == null ||
                    kotlin.math.abs(FreeformWeightConverter.lbToKg(typedNum) - storedNum) > 0.05
                ) {
                    loadInputText = FreeformWeightConverter.kgStringToDisplay(set.actual.kg, FreeformWeightUnit.LB)
                }
            }
        }
    }

    if (authoring) {
        Column(
            Modifier.fillMaxWidth().testTag("freeform-set-$index"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StrideLabHairline()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$unit ${index + 1}",
                    Modifier.weight(1f),
                    color = StrideLabSurfaceTokens.ink,
                    style = MaterialTheme.typography.titleSmall.tabular()
                )
                TextButton(onClick = remove, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Remove", color = StrideLabSurfaceTokens.inkSecondary)
                }
            }
            MeasureFields(discipline, set.target, "Target", "freeform-target-$index") {
                edit(set.copy(target = it))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FreeformField(
                    "Rest (s)",
                    set.restSeconds,
                    { edit(set.copy(restSeconds = it)) },
                    Modifier.weight(1f),
                    "freeform-rest-$index",
                    integer = true
                )
                if (discipline in setOf(ManualDiscipline.STRENGTH, ManualDiscipline.CALISTHENICS)) {
                    FreeformField(
                        "Target RIR",
                        set.targetRir,
                        { edit(set.copy(targetRir = it)) },
                        Modifier.weight(1f),
                        "freeform-target-rir-$index",
                        integer = true
                    )
                }
            }
        }
        return
    }

    val rowBg = when {
        set.completed -> Color(0xFF101808)
        isNextActive -> StrideLabSurfaceTokens.surfaceElevated
        else -> Color(0xFF0B0D10)
    }
    val rowBorder = when {
        set.completed -> StrideLabSurfaceTokens.lime.copy(alpha = 0.25f)
        isNextActive -> StrideLabSurfaceTokens.hairline
        else -> StrideLabSurfaceTokens.hairline.copy(alpha = 0.45f)
    }
    val rowAlpha = if (set.completed) 0.92f else 1f
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && canRemove) {
                remove()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canRemove,
        backgroundContent = {
            val revealDelete = canRemove && (
                dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart ||
                    dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                )
            if (revealDelete) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3A151B))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Remove $unit ${index + 1}",
                        tint = freeformPalette.highCoral
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(rowBg)
                .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .testTag("freeform-set-$index"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .let { if (compact) it.width(36.dp) else it.weight(0.35f) }
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { options = true }
                        .testTag("freeform-set-menu-$index")
                        .graphicsLayer { alpha = rowAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (compact) "${index + 1}" else "$unit ${index + 1}",
                        color = if (isNextActive) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.ink,
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                        DropdownMenuItem(
                            text = { Text(if (expanded) "Hide RIR / RPE & details" else "RIR / RPE, target & notes") },
                            onClick = { options = false; expanded = !expanded }
                        )
                        if (canRemove) {
                            DropdownMenuItem(
                                text = { Text("Remove ${unit.lowercase()}") },
                                onClick = { options = false; remove() }
                            )
                        }
                    }
                }

                if (compact) {
                    Text(
                        text = previousHint.ifBlank { "—" },
                        modifier = Modifier
                            .width(54.dp)
                            .graphicsLayer { alpha = rowAlpha },
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 11.5.sp,
                        style = TabularTextStyle,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = rowAlpha },
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        metrics.forEach { metric ->
                            if (metric == MeasureInput.LOAD) {
                                CompactSetInput(
                                    value = loadInputText,
                                    label = "Actual ${weightUnit.unitSuffix}",
                                    tag = "freeform-actual-$index-${metric.suffix}",
                                    decimal = true,
                                    modifier = Modifier.weight(1f)
                                ) { raw ->
                                    loadInputText = raw
                                    setError = false
                                    val canonicalKg = FreeformWeightConverter.displayStringToCanonicalKg(raw, weightUnit)
                                    edit(set.copy(actual = set.actual.copy(kg = canonicalKg)))
                                }
                            } else {
                                CompactSetInput(
                                    value = metric.get(set.actual),
                                    label = "Actual ${metric.label}",
                                    tag = "freeform-actual-$index-${metric.suffix}",
                                    decimal = false,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    setError = false
                                    edit(set.copy(actual = metric.update(set.actual, it)))
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(0.65f))
                }

                IconButton(
                    onClick = {
                        if (set.completed || set.validActual(discipline, format)) {
                            setError = false
                            edit(set.copy(completed = !set.completed))
                        } else {
                            setError = true
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("freeform-confirm-$index")
                ) {
                    val label = if (set.completed) "Unconfirm $unit ${index + 1}" else "Confirm $unit ${index + 1}"
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (set.completed) StrideLabSurfaceTokens.lime
                                else Color(0xFF14161A)
                            )
                            .border(
                                width = 1.dp,
                                color = if (set.completed) StrideLabSurfaceTokens.lime else PlanBuilderPalette.border,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = label,
                            tint = if (set.completed) StrideLabSurfaceTokens.onLime else StrideLabSurfaceTokens.inkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (!compact) {
                Box(modifier = Modifier.graphicsLayer { alpha = rowAlpha }) {
                    MeasureFields(discipline, set.actual, "Actual", "freeform-actual-$index") {
                        setError = false
                        edit(set.copy(actual = it))
                    }
                }
            }

            val targetSummary = listOfNotNull(
                FreeformWeightConverter.formatMeasureLabel(set.target, discipline, weightUnit).takeIf { it.isNotBlank() && !compact },
                set.targetRir.takeIf { it.isNotBlank() }?.let { "RIR $it" },
                set.rpe.takeIf { it.isNotBlank() }?.let { "RPE $it" },
                set.rir.takeIf { it.isNotBlank() }?.let { "RIR $it" }
            )
            if (canRemove || targetSummary.isNotEmpty() || discipline == ManualDiscipline.STRENGTH || discipline == ManualDiscipline.CALISTHENICS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (targetSummary.isNotEmpty() && !expanded) {
                        Text(
                            targetSummary.joinToString(" · "),
                            color = StrideLabSurfaceTokens.inkMuted,
                            fontSize = 11.5.sp,
                            style = TabularTextStyle,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (discipline == ManualDiscipline.STRENGTH || discipline == ManualDiscipline.CALISTHENICS) {
                            Text(
                                text = if (expanded) "Hide RIR/RPE" else "+ RIR/RPE",
                                color = StrideLabSurfaceTokens.inkMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { expanded = !expanded }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                        if (canRemove) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = remove)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("freeform-remove-set-$index")
                                    .semantics { contentDescription = "Remove $unit ${index + 1}" }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = freeformPalette.highCoral,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Remove",
                                    color = freeformPalette.highCoral,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            if (setError || (set.completed && !set.validActual(discipline, format))) {
                FreeformError("Enter valid actual work to confirm this ${unit.lowercase()}.")
            }

            if (expanded) {
                StrideLabHairline()
                MeasureFields(discipline, set.target, "Target", "freeform-target-$index") {
                    edit(set.copy(target = it))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FreeformField(
                        "Rest (s)",
                        set.restSeconds,
                        { edit(set.copy(restSeconds = it)) },
                        Modifier.weight(1f),
                        "freeform-rest-$index",
                        integer = true
                    )
                    FreeformField(
                        "RPE",
                        set.rpe,
                        { edit(set.copy(rpe = it)) },
                        Modifier.weight(1f),
                        "freeform-rpe-$index",
                        integer = true
                    )
                }
                if (discipline == ManualDiscipline.STRENGTH || discipline == ManualDiscipline.CALISTHENICS) {
                    FreeformField(
                        "RIR",
                        set.rir,
                        { edit(set.copy(rir = it)) },
                        Modifier.fillMaxWidth(),
                        "freeform-rir-$index",
                        integer = true
                    )
                }
                FreeformField(
                    "$unit notes",
                    set.notes,
                    { edit(set.copy(notes = it)) },
                    Modifier.fillMaxWidth(),
                    "freeform-set-notes-$index",
                    limit = 400,
                    lines = 2
                )
            }
        }
    }
}

private enum class MeasureInput(val label: String, val headerLabel: String, val suffix: String) {
    REPS("Reps", "REPS", "quantity"),
    CONTACTS("Contacts", "CONTACTS", "quantity"),
    HOLD("Hold (s)", "SEC", "seconds"),
    WORK("Work (s)", "SEC", "seconds"),
    LOAD("kg", "KG", "kg");

    fun get(value: FreeformMeasure) = when (this) {
        REPS, CONTACTS -> value.quantity
        HOLD, WORK -> value.seconds
        LOAD -> value.kg
    }

    fun update(value: FreeformMeasure, input: String) = when (this) {
        REPS, CONTACTS -> value.copy(quantity = input)
        HOLD, WORK -> value.copy(seconds = input)
        LOAD -> value.copy(kg = input)
    }
}

private fun measureInputs(discipline: ManualDiscipline) = when (discipline) {
    ManualDiscipline.RUNNING -> emptyList()
    ManualDiscipline.STRENGTH -> listOf(MeasureInput.REPS, MeasureInput.LOAD)
    ManualDiscipline.CALISTHENICS -> listOf(MeasureInput.REPS, MeasureInput.LOAD, MeasureInput.HOLD)
    ManualDiscipline.PLYOMETRICS -> listOf(MeasureInput.CONTACTS)
    ManualDiscipline.ISOMETRICS -> listOf(MeasureInput.HOLD, MeasureInput.LOAD)
    ManualDiscipline.CONDITIONING -> listOf(MeasureInput.WORK)
}

@Composable
private fun CompactSetInput(
    value: String,
    label: String,
    tag: String,
    decimal: Boolean,
    modifier: Modifier,
    change: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 32) change(it) },
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label }
            .background(PlanBuilderPalette.surface, shape)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) StrideLabSurfaceTokens.lime else PlanBuilderPalette.border,
                shape = shape
            ),
        singleLine = true,
        cursorBrush = SolidColor(StrideLabSurfaceTokens.lime),
        textStyle = MaterialTheme.typography.bodyMedium.tabular().copy(
            color = StrideLabSurfaceTokens.ink,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(
                        "–",
                        color = StrideLabSurfaceTokens.inkMuted,
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        textAlign = TextAlign.Center
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun MeasureFields(
    discipline: ManualDiscipline,
    value: FreeformMeasure,
    group: String,
    tag: String,
    edit: (FreeformMeasure) -> Unit
) {
    val metrics = measureInputs(discipline)
    Text(group, color = StrideLabSurfaceTokens.inkSecondary, style = MaterialTheme.typography.labelLarge)
    metrics.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { metric ->
                FreeformField(
                    metric.label,
                    metric.get(value),
                    { edit(metric.update(value, it)) },
                    Modifier.weight(1f),
                    "$tag-${metric.suffix}",
                    integer = metric != MeasureInput.LOAD,
                    decimal = metric == MeasureInput.LOAD,
                    accessibilityLabel = "$group ${metric.label}"
                )
            }
            if (row.size == 1 && metrics.size > 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun FreeformField(
    label: String,
    value: String,
    edit: (String) -> Unit,
    modifier: Modifier,
    tag: String,
    limit: Int = 32,
    integer: Boolean = false,
    decimal: Boolean = false,
    lines: Int = 1,
    accessibilityLabel: String = label
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = StrideLabSurfaceTokens.inkSecondary, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= limit) edit(it) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(tag)
                .semantics { contentDescription = accessibilityLabel },
            singleLine = lines == 1,
            maxLines = lines,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    integer -> KeyboardType.Number
                    decimal -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                }
            ),
            textStyle = MaterialTheme.typography.bodyMedium.tabular(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = StrideLabSurfaceTokens.ink,
                unfocusedTextColor = StrideLabSurfaceTokens.ink,
                focusedBorderColor = StrideLabSurfaceTokens.lime,
                unfocusedBorderColor = StrideLabSurfaceTokens.hairline,
                cursorColor = StrideLabSurfaceTokens.lime
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FreeformExercisePicker(
    recentExerciseNames: List<String> = emptyList(),
    close: () -> Unit,
    select: (FreeformExercise) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = remember(context) { isSystemReducedMotion(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var scopeFilter by rememberSaveable { mutableStateOf(PickerScope.ALL) }
    var loadingCatalog by remember { mutableStateOf(true) }
    var inspectOption by remember { mutableStateOf<PickerExerciseOption?>(null) }
    val catalog by produceState<List<ExerciseLibraryRecordV2>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) {
            freeformAttempt { ExerciseLibraryV2.load { context.assets.open(it) }.records }
                .getOrDefault(emptyList()).filter { it.kind == "exercise" }
        }
        loadingCatalog = false
    }
    val augmentedCatalog = remember(catalog) { FreeformExerciseCatalog.augment(catalog) }
    val showSkeleton = rememberDelayedSkeletonVisible(loadingCatalog && catalog.isEmpty())
    val matches = remember(catalog, query, scopeFilter, recentExerciseNames) {
        FreeformExerciseCatalog.filter(catalog, scopeFilter, query, recentExerciseNames)
    }

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheetState,
        containerColor = StrideLabSurfaceTokens.surfaceElevated,
        contentColor = StrideLabSurfaceTokens.ink,
        modifier = Modifier.testTag("freeform-exercise-picker-sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Add exercise",
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = close,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close exercise picker",
                        tint = StrideLabSurfaceTokens.inkSecondary
                    )
                }
            }

            FreeformField(
                label = "Search or add custom",
                value = query,
                edit = { query = it },
                modifier = Modifier.fillMaxWidth(),
                tag = "freeform-exercise-query",
                limit = 100
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup()
                    .testTag("freeform-picker-filters"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PickerScope.entries.forEach { entry ->
                    val selected = scopeFilter == entry
                    val accent = entry.discipline?.markColor ?: StrideLabSurfaceTokens.lime
                    val animMs = if (reducedMotion) 0 else 160
                    val bg by animateColorAsState(
                        targetValue = if (selected) accent else StrideLabSurfaceTokens.surfaceSubtle,
                        animationSpec = tween(animMs),
                        label = "picker-pill-bg-${entry.tagSuffix}"
                    )
                    val border by animateColorAsState(
                        targetValue = if (selected) accent else StrideLabSurfaceTokens.hairline,
                        animationSpec = tween(animMs),
                        label = "picker-pill-border-${entry.tagSuffix}"
                    )
                    val ink by animateColorAsState(
                        targetValue = if (selected) StrideLabSurfaceTokens.onLime else StrideLabSurfaceTokens.ink,
                        animationSpec = tween(animMs),
                        label = "picker-pill-ink-${entry.tagSuffix}"
                    )
                    Box(
                        modifier = Modifier
                            .heightIn(min = StrideLabSurfaceTokens.minTouchTarget)
                            .clip(RoundedCornerShape(100.dp))
                            .selectable(selected = selected, role = Role.Tab) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scopeFilter = entry
                            }
                            .testTag("freeform-picker-filter-${entry.tagSuffix}")
                            .semantics { this.selected = selected },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(100.dp),
                            border = BorderStroke(1.dp, border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(34.dp)
                                    .padding(horizontal = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (entry.discipline != null && !selected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                }
                                Text(
                                    text = entry.label,
                                    color = ink,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            if (query.isNotBlank()) {
                val trimmed = query.trim()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { select(FreeformExercise(name = trimmed)) }
                            .padding(vertical = 10.dp)
                            .testTag("freeform-add-custom"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Add \"$trimmed\" as custom exercise",
                            color = StrideLabSurfaceTokens.lime,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text("+", color = StrideLabSurfaceTokens.lime, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    StrideLabHairline()
                }
            }

            if (showSkeleton) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonExerciseRow()
                    SkeletonExerciseRow()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("freeform-picker-list"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (matches.isEmpty()) {
                        item {
                            Text(
                                text = if (scopeFilter == PickerScope.RECENT) "No recent exercises yet."
                                else "No matching exercises in ${scopeFilter.label.lowercase(Locale.ROOT)}.",
                                color = StrideLabSurfaceTokens.inkMuted,
                                fontSize = 13.5.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(matches, key = { it.id ?: it.name }) { option ->
                            StrideLabFlatRow(
                                title = option.name,
                                subtitle = option.subtitle,
                                accentColor = option.discipline.markColor,
                                onClick = {
                                    select(FreeformExercise(name = option.name, catalogId = option.id))
                                },
                                trailing = {
                                    IconButton(
                                        onClick = { inspectOption = option },
                                        modifier = Modifier
                                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("exercise-info-trigger-${option.id ?: option.name}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = "Exercise info for ${option.name}",
                                            tint = StrideLabSurfaceTokens.inkSecondary
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    inspectOption?.let { option ->
        ExerciseInfoSheet(
            exerciseId = option.id,
            exerciseName = option.name,
            disciplineLabel = option.discipline.label,
            disciplineTint = option.discipline.markColor,
            catalog = augmentedCatalog,
            onDismiss = { inspectOption = null }
        )
    }
}
