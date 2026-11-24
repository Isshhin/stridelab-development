package io.stridelab.privatealpha

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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
import java.util.Locale

internal enum class PickerScope(val label: String) {
    DISCIPLINE("Discipline"),
    RECENT("Recent"),
    ALL("All")
}

internal data class PickerExerciseOption(
    val id: String?,
    val name: String,
    val subtitle: String,
    val categories: List<String>,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList()
)

internal object FreeformExerciseCatalog {
    private val builtinPlyometrics = listOf(
        builtin(
            "plyo-pogo-hops", "Pogo hops", "Plyometrics · Ankle stiffness",
            listOf("calves", "foot_stabilizers"), listOf("quadriceps", "tibialis_anterior")
        ),
        builtin(
            "plyo-box-jump", "Box jump", "Plyometrics · Lower power",
            listOf("quadriceps", "gluteus_maximus"), listOf("calves", "hamstrings")
        ),
        builtin(
            "plyo-broad-jump", "Broad jump", "Plyometrics · Horizontal power",
            listOf("gluteus_maximus", "quadriceps"), listOf("hamstrings", "calves")
        ),
        builtin(
            "plyo-depth-drop", "Depth drop", "Plyometrics · Landing mechanics",
            listOf("quadriceps", "gluteus_maximus"), listOf("calves", "trunk_stabilizers")
        ),
        builtin(
            "plyo-single-leg-hop", "Single-leg hop", "Plyometrics · Unilateral reactivity",
            listOf("calves", "quadriceps"), listOf("gluteus_medius", "foot_stabilizers")
        ),
        builtin(
            "plyo-split-squat-jump", "Split squat jump", "Plyometrics · Explosive stride",
            listOf("quadriceps", "gluteus_maximus"), listOf("calves", "gluteus_medius")
        ),
        builtin(
            "plyo-skater-hops", "Skater hops", "Plyometrics · Lateral reactive",
            listOf("gluteus_medius", "quadriceps"), listOf("calves", "adductors")
        ),
        builtin(
            "plyo-bounds", "Alternate leg bounds", "Plyometrics · Running power",
            listOf("gluteus_maximus", "hamstrings"), listOf("calves", "quadriceps")
        ),
        builtin(
            "plyo-a-skip", "A-skip", "Plyometrics · Coordination & stiffness",
            listOf("hip_flexors", "calves"), listOf("gluteus_maximus", "abdominals")
        ),
        builtin(
            "plyo-tuck-jump", "Tuck jump", "Plyometrics · Vertical elastic",
            listOf("quadriceps", "hip_flexors"), listOf("calves", "abdominals")
        )
    )

    private val builtinIsometrics = listOf(
        builtin(
            "iso-wall-sit", "Wall sit", "Isometrics · Quad endurance",
            listOf("quadriceps"), listOf("gluteus_maximus", "calves")
        ),
        builtin(
            "iso-calf-hold", "Single-leg calf isometric hold", "Isometrics · Achilles & soleus",
            listOf("calves"), listOf("foot_stabilizers", "tibialis_anterior")
        ),
        builtin(
            "iso-split-squat-hold", "Split squat isometric hold", "Isometrics · Single-leg stability",
            listOf("quadriceps", "gluteus_maximus"), listOf("gluteus_medius", "trunk_stabilizers")
        ),
        builtin(
            "iso-glute-bridge-hold", "Glute bridge isometric hold", "Isometrics · Posterior chain",
            listOf("gluteus_maximus"), listOf("hamstrings", "spinal_extensors")
        ),
        builtin(
            "iso-forearm-plank", "Forearm plank", "Isometrics · Core bracing",
            listOf("abdominals", "trunk_stabilizers"), listOf("shoulder_stabilizers", "gluteus_maximus")
        ),
        builtin(
            "iso-side-plank", "Side plank", "Isometrics · Lateral trunk",
            listOf("obliques", "lateral_trunk_muscles"), listOf("gluteus_medius", "shoulder_stabilizers")
        ),
        builtin(
            "iso-dead-hang", "Dead hang", "Isometrics · Grip & shoulder",
            listOf("forearm_grip", "latissimus_dorsi"), listOf("shoulder_stabilizers", "lower_trapezius")
        ),
        builtin(
            "iso-hamstring-hold", "Hamstring bridge isometric hold", "Isometrics · Hamstring tendon",
            listOf("hamstrings"), listOf("gluteus_maximus", "calves")
        ),
        builtin(
            "iso-hollow-hold", "Hollow body hold", "Isometrics · Anterior core",
            listOf("abdominals"), listOf("hip_flexors", "trunk_stabilizers")
        )
    )

    private fun builtin(
        id: String,
        name: String,
        subtitle: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>
    ): ExerciseLibraryRecordV2 =
        ExerciseLibraryRecordV2(
            id = id,
            name = name,
            kind = "exercise",
            categories = listOf(if (id.startsWith("plyo-")) "plyometrics" else "isometrics"),
            movementPatterns = listOf(subtitle),
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            equipmentOptions = listOf(emptySet()),
            setupNotes = emptyList(),
            difficulty = "all",
            prerequisites = emptySet(),
            prerequisiteNotes = emptyList(),
            regressions = emptyList(),
            progressions = emptyList(),
            alternatives = emptyList(),
            technique = emptyList(),
            easier = "",
            harder = "",
            caveats = emptyList(),
            provenance = emptyList(),
            media = ExerciseMediaV2(
                status = "none",
                bundledFile = null,
                author = null,
                license = null,
                licenseUrl = null,
                verificationUrls = emptyList(),
                reason = "built-in",
                fit = ""
            )
        )

    fun builtinRecords(): List<ExerciseLibraryRecordV2> = builtinPlyometrics + builtinIsometrics

    fun augment(records: List<ExerciseLibraryRecordV2>): List<ExerciseLibraryRecordV2> {
        val byName = records.mapTo(HashSet()) { it.name.trim().lowercase(Locale.ROOT) }
        val additions = (builtinPlyometrics + builtinIsometrics).filter {
            it.name.lowercase(Locale.ROOT) !in byName
        }
        return records + additions
    }

    fun matchesDiscipline(record: ExerciseLibraryRecordV2, discipline: ManualDiscipline): Boolean {
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
            ManualDiscipline.RUNNING -> "running" in cats
        }
    }

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
            "running" in record.categories -> "Running"
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
        discipline: ManualDiscipline,
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
                    PickerExerciseOption(
                        id = found?.id,
                        name = name,
                        subtitle = found?.let { subtitleFor(it, discipline) } ?: "${discipline.label} · Recent",
                        categories = found?.categories.orEmpty(),
                        primaryMuscles = found?.primaryMuscles.orEmpty(),
                        secondaryMuscles = found?.secondaryMuscles.orEmpty()
                    )
                }
        }

        val filtered = augmented.asSequence()
            .filter { record ->
                (scope == PickerScope.ALL || matchesDiscipline(record, discipline)) &&
                    (search.isBlank() || record.name.contains(search, ignoreCase = true))
            }
            .sortedWith(
                compareByDescending<ExerciseLibraryRecordV2> { matchesDiscipline(it, discipline) }
                    .thenByDescending { it.id.startsWith("plyo-") || it.id.startsWith("iso-") }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            .take(80)
            .map { record ->
                PickerExerciseOption(
                    id = record.id,
                    name = record.name,
                    subtitle = subtitleFor(record, discipline),
                    categories = record.categories,
                    primaryMuscles = record.primaryMuscles,
                    secondaryMuscles = record.secondaryMuscles
                )
            }
            .toList()

        return filtered
    }
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
    var inspectExercise by remember(initial.id) { mutableStateOf<FreeformExercise?>(null) }
    val exerciseCatalog = rememberAugmentedExerciseCatalog()

    // Unobtrusive non-blocking rest timer state (session-scoped)
    var restPresetSeconds by rememberSaveable(initial.id) { mutableIntStateOf(90) }
    var restRemainingSeconds by rememberSaveable(initial.id) { mutableIntStateOf(0) }
    var restRunning by rememberSaveable(initial.id) { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val persistMutex = remember(initial.id) { Mutex() }

    val recentExercises = remember(board.workouts, draft.discipline, draft.exercises) {
        val existingNames = draft.exercises.map { it.name.trim().lowercase(Locale.ROOT) }.toSet()
        board.workouts.asSequence()
            .filter { it.isComplete && it.discipline == draft.discipline }
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
        if (!isPersisted && draft == initial) {
            leave()
            return
        }
        scope.launch(Dispatchers.Main.immediate) {
            working = true
            error = null
            freeformAttempt { persistDraft(draft) }.onSuccess {
                update(it)
                if (it.isComplete) saved(it.id) else leave()
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
    LaunchedEffect(draft.modifiedAt, working, isPersisted) {
        val pending = draft
        val shouldPersist = (isPersisted && pending.modifiedAt > persistedAt) ||
            (!isPersisted && pending != initial)
        if (!working && shouldPersist) {
            delay(350)
            freeformAttempt { persistDraft(pending) }.onSuccess { stored ->
                update(stored)
            }.onFailure { error = it.freeformMessage("Draft changes could not be saved.") }
        }
    }

    if (review) {
        FreeformPage("Review workout", "${draft.discipline.label} · ${draft.performedOn}", ::back) {
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
                            draft.summary(),
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
                                    set.actual.label(draft.discipline).ifBlank { "Round completed" },
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
                                val target = persistDraft(draft)
                                persistMutex.withLock { access.finish(target) }
                            }.onSuccess {
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

    FreeformPage(
        draft.workoutName.ifBlank { draft.discipline.label },
        draft.date?.toString(),
        ::back
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("freeform-editor"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                if (draft.exercises.isEmpty()) {
                    item(key = "freeform-empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .testTag("freeform-empty-state"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Empty ${draft.discipline.label.lowercase(Locale.getDefault())} workout",
                                color = StrideLabSurfaceTokens.ink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Add your first movement to begin logging sets.",
                                color = StrideLabSurfaceTokens.inkSecondary,
                                fontSize = 13.5.sp
                            )
                            FreeformPrimary(
                                label = "+ Add exercise",
                                onClick = { picker = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("freeform-add-exercise"),
                                enabled = !working
                            )
                            if (recentExercises.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "QUICK ADD RECENT",
                                    color = StrideLabSurfaceTokens.inkMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    recentExercises.take(5).forEach { name ->
                                        Surface(
                                            onClick = {
                                                edit(draft.copy(exercises = draft.exercises + FreeformExercise(name = name)))
                                            },
                                            enabled = !working,
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF161616),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("freeform-quick-add-$name")
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "+ $name",
                                                    color = StrideLabSurfaceTokens.ink,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(draft.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                        FreeformExercisePanel(
                            exercise = exercise,
                            discipline = draft.discipline,
                            previous = board.previous(draft.discipline, exercise),
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
                            }
                        )
                    }
                    item(key = "freeform-add-exercise-btn") {
                        Surface(
                            onClick = { picker = true },
                            enabled = draft.exercises.size < 30 && !working,
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF161616),
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { details = !details }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Date, effort & notes",
                            color = StrideLabSurfaceTokens.inkSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val preview = listOfNotNull(
                            draft.performedOn.takeIf { it.isNotBlank() },
                            draft.effort.takeIf { it.isNotBlank() }?.let { "RPE $it" }
                        ).joinToString(" · ")
                        Text(
                            if (details) "Hide" else preview.ifBlank { "Add" },
                            color = StrideLabSurfaceTokens.inkMuted,
                            fontSize = 12.5.sp,
                            style = TabularTextStyle
                        )
                    }
                    if (details) {
                        Column(
                            modifier = Modifier.padding(bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FreeformField(
                                "Date (YYYY-MM-DD)",
                                draft.performedOn,
                                { edit(draft.copy(performedOn = it)) },
                                Modifier.fillMaxWidth(),
                                "freeform-date"
                            )
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
                    StrideLabHairline()
                }
            }

            item(key = "freeform-discard-action") {
                TextButton(
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
                        .heightIn(min = 48.dp)
                        .testTag("freeform-discard")
                ) {
                    Text("Discard draft", color = freeformPalette.highCoral)
                }
            }
        }

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

        StrideLabHairline()
        Surface(color = StrideLabSurfaceTokens.surfaceElevated) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FreeformError(error)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = ::saveAndLeave,
                        modifier = Modifier
                            .weight(0.42f)
                            .heightIn(min = 48.dp)
                            .testTag("freeform-save-progress"),
                        enabled = !working
                    ) {
                        Text("Save draft", color = StrideLabSurfaceTokens.inkSecondary)
                    }
                    FreeformPrimary(
                        "Finish workout",
                        {
                            runCatching { draft.finish(System.currentTimeMillis()) }
                                .onSuccess {
                                    error = null
                                    review = true
                                }
                                .onFailure { error = it.freeformMessage("Check the workout before finishing.") }
                        },
                        Modifier.weight(0.58f).testTag("freeform-finish"),
                        enabled = !working
                    )
                }
            }
        }
    }

    if (picker) {
        FreeformExercisePicker(
            discipline = draft.discipline,
            recentExerciseNames = recentExercises,
            close = { picker = false }
        ) { exercise ->
            edit(draft.copy(exercises = draft.exercises + exercise))
            picker = false
        }
    }
    inspectExercise?.let { target ->
        ExerciseInfoSheet(
            exerciseId = target.catalogId,
            exerciseName = target.name,
            disciplineLabel = draft.discipline.label,
            disciplineTint = draft.discipline.markColor,
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

    StrideLabHairline()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StrideLabSurfaceTokens.surfaceElevated)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .heightIn(min = 48.dp)
            .testTag("freeform-rest-timer"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = formatted,
                color = if (active) StrideLabSurfaceTokens.ink else StrideLabSurfaceTokens.inkSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                style = TabularTextStyle
            )
        }
        TextButton(
            onClick = onAddFifteen,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("freeform-rest-plus15"),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text("+15s", color = StrideLabSurfaceTokens.inkSecondary, fontSize = 12.5.sp, style = TabularTextStyle)
        }
        if (active) {
            TextButton(
                onClick = onReset,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("freeform-rest-reset"),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text("Reset", color = StrideLabSurfaceTokens.inkMuted, fontSize = 12.5.sp)
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
                fontWeight = FontWeight.Bold
            )
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
    onSetConfirmed: () -> Unit = {}
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    val conditioning = discipline == ManualDiscipline.CONDITIONING
    val firstUncompletedIndex = remember(exercise.sets) {
        exercise.sets.indexOfFirst { !it.completed }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        "Previous · ${previous.label(discipline)}",
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

        val compact = discipline != ManualDiscipline.CALISTHENICS && !authoring
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
                measureInputs(discipline).forEach { metric ->
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
                    format = exercise.format,
                    previousHint = set.target.label(discipline).ifBlank {
                        previous?.label(discipline).orEmpty()
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
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF161616),
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
    format: ConditioningFormat,
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

    val rowBg = if (isNextActive) StrideLabSurfaceTokens.surfaceElevated else Color.Transparent
    val rowAlpha = if (set.completed) 0.65f else 1f
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
            if (canRemove) {
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
                        measureInputs(discipline).forEach { metric ->
                            CompactSetInput(
                                value = metric.get(set.actual),
                                label = "Actual ${metric.label}",
                                tag = "freeform-actual-$index-${metric.suffix}",
                                decimal = metric == MeasureInput.LOAD,
                                modifier = Modifier.weight(1f)
                            ) {
                                setError = false
                                edit(set.copy(actual = metric.update(set.actual, it)))
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
                                else Color(0xFF1E2228)
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
                set.target.label(discipline).takeIf { it.isNotBlank() && !compact },
                set.targetRir.takeIf { it.isNotBlank() }?.let { "RIR $it" },
                set.rpe.takeIf { it.isNotBlank() }?.let { "RPE $it" },
                set.rir.takeIf { it.isNotBlank() }?.let { "RIR $it" }
            )
            if (targetSummary.isNotEmpty() || discipline == ManualDiscipline.STRENGTH || discipline == ManualDiscipline.CALISTHENICS) {
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
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 32) change(it) },
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label }
            .background(Color(0xFF161616), shape)
            .then(
                if (focused) Modifier.border(1.dp, StrideLabSurfaceTokens.lime, shape)
                else Modifier
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
    discipline: ManualDiscipline,
    recentExerciseNames: List<String> = emptyList(),
    close: () -> Unit,
    select: (FreeformExercise) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var scopeFilter by rememberSaveable(discipline) { mutableStateOf(PickerScope.DISCIPLINE) }
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
    val matches = remember(catalog, query, discipline, scopeFilter, recentExerciseNames) {
        FreeformExerciseCatalog.filter(catalog, discipline, scopeFilter, query, recentExerciseNames)
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
                    .testTag("freeform-picker-filters"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PickerScope.entries.forEach { entry ->
                    val label = if (entry == PickerScope.DISCIPLINE) discipline.label else entry.label
                    FilterChip(
                        selected = scopeFilter == entry,
                        onClick = { scopeFilter = entry },
                        label = { Text(label) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("freeform-picker-filter-${entry.name.lowercase(Locale.ROOT)}")
                    )
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
                                text = if (scopeFilter == PickerScope.RECENT) "No recent exercises for ${discipline.label} yet."
                                else "No matching exercises in ${if (scopeFilter == PickerScope.DISCIPLINE) discipline.label else "catalog"}.",
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
                                accentColor = discipline.markColor,
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
            disciplineLabel = discipline.label,
            disciplineTint = discipline.markColor,
            catalog = augmentedCatalog,
            onDismiss = { inspectOption = null }
        )
    }
}
