package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class DurationFilter(val label: String) {
    ALL("Any length"),
    SHORT("<= 15m"),
    MEDIUM("16-30m"),
    LONG("> 30m");

    fun matches(minutes: Int): Boolean = when (this) {
        ALL -> true
        SHORT -> minutes <= 15
        MEDIUM -> minutes in 16..30
        LONG -> minutes > 30
    }
}

internal enum class CuratedCollection(val label: String, val subtitle: String) {
    UNDER_15("Quick <= 15m", "Short bursts when time is tight"),
    RUNNER_DURABILITY("Runner Armor", "Tendon, hip, and single-leg durability"),
    NO_GEAR("No Gear Needed", "Train anywhere with bodyweight only"),
    RECOVERY_SLEEP("Recovery & Sleep", "Downregulate strain and boost readiness");

    fun matches(workout: CuratedWorkout): Boolean = when (this) {
        UNDER_15 -> workout.durationMinutes <= 15
        RUNNER_DURABILITY -> workout.category in setOf(
            WorkoutHubCategory.STRENGTH,
            WorkoutHubCategory.PLYOMETRICS,
            WorkoutHubCategory.ISOMETRICS
        ) && (WorkoutEquipment.NONE in workout.equipment || WorkoutEquipment.DUMBBELLS in workout.equipment)
        NO_GEAR -> workout.equipment == setOf(WorkoutEquipment.NONE)
        RECOVERY_SLEEP -> workout.category in setOf(
            WorkoutHubCategory.RECOVERY,
            WorkoutHubCategory.MOBILITY
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutHubScreen(
    owner: String,
    board: FreeformBoard?,
    readError: String?,
    access: FreeformAccess,
    trainingState: TrainingState,
    back: () -> Unit,
    retry: () -> Unit,
    updateDraft: (FreeformWorkout) -> Unit,
    removeDraft: (String) -> Unit,
    onTemplateAdded: (ManualWorkoutTemplate) -> Unit,
    openTemplates: () -> Unit,
    startEmpty: () -> Unit,
    openDraft: (String) -> Unit,
    openGuided: (String) -> Unit,
    scheduleTemplate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<WorkoutHubCategory?>(null) }
    var selectedCollection by rememberSaveable { mutableStateOf<CuratedCollection?>(null) }
    var durationFilter by rememberSaveable { mutableStateOf(DurationFilter.ALL) }
    var difficultyFilter by rememberSaveable { mutableStateOf<WorkoutDifficulty?>(null) }
    var intensityFilter by rememberSaveable { mutableStateOf<WorkoutIntensity?>(null) }
    var equipmentFilter by rememberSaveable { mutableStateOf<WorkoutEquipment?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var selectedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var favorites by remember(owner) {
        mutableStateOf(WorkoutHubPreferences.favoriteIds(context, owner))
    }

    val today = remember { LocalDate.now() }
    val tomorrow = remember(today) { today.plusDays(1) }
    val readiness = remember(trainingState.wellness, today) {
        if (trainingState.wellness.isEmpty()) null
        else BiometricReadinessEvaluator.evaluate(trainingState.wellness, today)
    }
    val activePlans = remember(trainingState.plans, owner) {
        trainingState.plans.filter { it.owner == owner && it.status == "approved" }
    }
    val plannedToday = remember(activePlans, today) {
        activePlans.flatMap { it.sessions }.filter { it.date == today }
    }
    val plannedTomorrow = remember(activePlans, tomorrow) {
        activePlans.flatMap { it.sessions }.filter { it.date == tomorrow }
    }
    val recentCompleted = remember(board) {
        board?.workouts.orEmpty().filter { (it.completedAt != null) }.sortedByDescending { it.modifiedAt }.take(4)
    }
    val drafts = remember(board) {
        board?.workouts.orEmpty().filter { !(it.completedAt != null) }.sortedByDescending { it.modifiedAt }
    }

    val recommendations = remember(
        readiness,
        plannedToday,
        plannedTomorrow,
        recentCompleted,
        trainingState.savedEquipment
    ) {
        WorkoutHubRecommender.recommend(
            library = CuratedWorkoutLibrary.all,
            readiness = readiness,
            plannedSessionsToday = plannedToday,
            plannedSessionsTomorrow = plannedTomorrow,
            recentFreeform = recentCompleted,
            savedEquipment = trainingState.savedEquipment?.keys.orEmpty()
        )
    }

    val filteredLibrary = remember(
        searchQuery,
        selectedCategory,
        selectedCollection,
        durationFilter,
        difficultyFilter,
        intensityFilter,
        equipmentFilter,
        favoritesOnly,
        favorites
    ) {
        val q = searchQuery.trim().lowercase()
        CuratedWorkoutLibrary.all.filter { workout ->
            (selectedCategory == null || workout.category == selectedCategory) &&
                (selectedCollection == null || selectedCollection!!.matches(workout)) &&
                durationFilter.matches(workout.durationMinutes) &&
                (difficultyFilter == null || workout.difficulty == difficultyFilter) &&
                (intensityFilter == null || workout.intensity == intensityFilter) &&
                (equipmentFilter == null || equipmentFilter in workout.equipment) &&
                (!favoritesOnly || workout.id in favorites) &&
                (q.isEmpty() ||
                    q in workout.title.lowercase() ||
                    q in workout.subtitle.lowercase() ||
                    q in workout.targetFocus.lowercase() ||
                    workout.equipment.any { q in it.label.lowercase() })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StrideLabSurfaceTokens.canvas)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = back) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = StrideLabSurfaceTokens.ink
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Workout Hub",
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${CuratedWorkoutLibrary.all.size} offline sessions & custom workouts",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 12.sp
                )
            }
            StrideLabFilterPill(
                label = if (showFilters) "Hide Filters" else "Filters",
                selected = showFilters || durationFilter != DurationFilter.ALL ||
                    difficultyFilter != null || intensityFilter != null ||
                    equipmentFilter != null || favoritesOnly,
                onClick = { showFilters = !showFilters }
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("freeform-hub"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (readError != null) {
                item {
                    StrideLabEmptyStatePanel(
                        title = "Couldn't load saved workouts",
                        subtitle = readError,
                        actionLabel = "Retry",
                        onAction = retry,
                        modifier = Modifier.testTag("freeform-error")
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search workouts, focus, equipment...", color = StrideLabSurfaceTokens.inkMuted, fontSize = 13.sp)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StrideLabSurfaceTokens.lime,
                        unfocusedBorderColor = StrideLabSurfaceTokens.hairline,
                        focusedContainerColor = StrideLabSurfaceTokens.surfaceElevated,
                        unfocusedContainerColor = StrideLabSurfaceTokens.surfaceElevated,
                        focusedTextColor = StrideLabSurfaceTokens.ink,
                        unfocusedTextColor = StrideLabSurfaceTokens.ink
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StrideLabFilterPill(
                        label = "All",
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    WorkoutHubCategory.entries.forEach { category ->
                        StrideLabFilterPill(
                            label = category.label,
                            selected = selectedCategory == category,
                            accentColor = category.accentColor,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                            }
                        )
                    }
                }
            }

            if (showFilters) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StrideLabSurfaceTokens.surfaceElevated,
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DurationFilter.entries.forEach { d ->
                                    StrideLabFilterPill(
                                        label = d.label,
                                        selected = durationFilter == d,
                                        onClick = { durationFilter = d }
                                    )
                                }
                                StrideLabFilterPill(
                                    label = "Favorites",
                                    selected = favoritesOnly,
                                    onClick = { favoritesOnly = !favoritesOnly }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                WorkoutDifficulty.entries.forEach { diff ->
                                    StrideLabFilterPill(
                                        label = diff.label,
                                        selected = difficultyFilter == diff,
                                        onClick = { difficultyFilter = if (difficultyFilter == diff) null else diff }
                                    )
                                }
                                WorkoutIntensity.entries.forEach { inten ->
                                    StrideLabFilterPill(
                                        label = "${inten.label} intensity",
                                        selected = intensityFilter == inten,
                                        onClick = { intensityFilter = if (intensityFilter == inten) null else inten }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                WorkoutEquipment.entries.forEach { eq ->
                                    StrideLabFilterPill(
                                        label = eq.label,
                                        selected = equipmentFilter == eq,
                                        onClick = { equipmentFilter = if (equipmentFilter == eq) null else eq }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (board == null && readError == null) {
                item(key = "hub-skeleton") {
                    val reducedMotion = remember(context) { isSystemReducedMotion(context) }
                    SkeletonShimmerContainer(reducedMotion = reducedMotion) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkeletonActivityRow(reducedMotion = reducedMotion)
                            SkeletonActivityRow(reducedMotion = reducedMotion)
                        }
                    }
                }
            }

            if (drafts.isNotEmpty()) {
                item {
                    Text(
                        text = "IN PROGRESS",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                itemsIndexed(drafts, key = { _, d -> d.id }) { index, draft ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StrideLabSurfaceTokens.surfaceElevated,
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = draft.discipline.label,
                                    color = draft.discipline.markColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = draft.notes.ifBlank { "${draft.exercises.size} exercises logged" },
                                    color = StrideLabSurfaceTokens.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { openDraft(draft.id) },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StrideLabSurfaceTokens.lime,
                                    contentColor = StrideLabSurfaceTokens.onLime
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .testTag("freeform-resume-${draft.id}")
                                    .then(if (index == 0) Modifier.testTag("freeform-resume") else Modifier)
                            ) {
                                Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        access.discard(owner, draft.id)
                                        removeDraft(draft.id)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("freeform-discard-row-${draft.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Discard draft",
                                    tint = StrideLabSurfaceTokens.inkMuted
                                )
                            }
                        }
                    }
                }
            }

            if (recommendations.isNotEmpty() && searchQuery.isBlank() && selectedCategory == null && !favoritesOnly) {
                item {
                    Text(
                        text = "RECOMMENDED FOR TODAY",
                        color = StrideLabSurfaceTokens.lime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recommendations, key = { it.workout.id }) { rec ->
                            Surface(
                                onClick = { selectedWorkoutId = rec.workout.id },
                                shape = RoundedCornerShape(16.dp),
                                color = StrideLabSurfaceTokens.surfaceElevated,
                                border = BorderStroke(1.dp, rec.workout.category.accentColor.copy(alpha = 0.4f)),
                                modifier = Modifier.width(248.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        WorkoutHubVisualThumbnail(
                                            category = rec.workout.category,
                                            accentColor = rec.workout.category.accentColor,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = rec.workout.category.label.uppercase(),
                                                color = rec.workout.category.accentColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = rec.workout.title,
                                                color = StrideLabSurfaceTokens.ink,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = rec.reason,
                                        color = StrideLabSurfaceTokens.inkMuted,
                                        fontSize = 11.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StrideLabMetricBadge("${rec.workout.durationMinutes} min", rec.workout.category.accentColor)
                                        StrideLabMetricBadge(rec.workout.difficulty.label, StrideLabSurfaceTokens.inkMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = startEmpty,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StrideLabSurfaceTokens.lime,
                            contentColor = StrideLabSurfaceTokens.onLime
                        ),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("freeform-log")
                    ) {
                        Text("Start Empty", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                    OutlinedButton(
                        onClick = openTemplates,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("freeform-open-templates")
                    ) {
                        Text("My Templates", color = StrideLabSurfaceTokens.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                }
            }

            if (statusMessage != null) {
                item {
                    Text(
                        text = statusMessage!!,
                        color = StrideLabSurfaceTokens.lime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {
                Text(
                    text = "COLLECTIONS",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CuratedCollection.entries.forEach { collection ->
                        StrideLabFilterPill(
                            label = collection.label,
                            selected = selectedCollection == collection,
                            onClick = {
                                selectedCollection = if (selectedCollection == collection) null else collection
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "CURATED WORKOUTS (${filteredLibrary.size})",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (filteredLibrary.isEmpty()) {
                item {
                    StrideLabEmptyStatePanel(
                        title = "No matching workouts",
                        subtitle = "Clear one or more filters to browse the full offline library.",
                        actionLabel = "Reset Filters",
                        onAction = {
                            searchQuery = ""
                            selectedCategory = null
                            durationFilter = DurationFilter.ALL
                            difficultyFilter = null
                            intensityFilter = null
                            equipmentFilter = null
                            favoritesOnly = false
                        }
                    )
                }
            } else {
                items(filteredLibrary, key = { it.id }) { workout ->
                    val isFav = workout.id in favorites
                    Surface(
                        onClick = { selectedWorkoutId = workout.id },
                        shape = RoundedCornerShape(16.dp),
                        color = StrideLabSurfaceTokens.surfaceElevated,
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                        modifier = Modifier.fillMaxWidth().testTag("hub-workout-${workout.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            WorkoutHubVisualThumbnail(
                                category = workout.category,
                                accentColor = workout.category.accentColor,
                                modifier = Modifier.size(62.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = workout.title,
                                    color = StrideLabSurfaceTokens.ink,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = workout.subtitle,
                                    color = StrideLabSurfaceTokens.inkMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StrideLabMetricBadge(workout.category.label, workout.category.accentColor)
                                    StrideLabMetricBadge("${workout.durationMinutes} min", StrideLabSurfaceTokens.inkMuted)
                                    StrideLabMetricBadge(workout.difficulty.label, StrideLabSurfaceTokens.inkMuted)
                                }
                            }
                            IconButton(
                                onClick = {
                                    favorites = WorkoutHubPreferences.toggleFavorite(context, owner, workout.id)
                                }
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (isFav) "Remove favorite" else "Favorite",
                                    tint = if (isFav) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.inkMuted
                                )
                            }
                        }
                    }
                }
            }

            if (recentCompleted.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "RECENTLY COMPLETED",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(recentCompleted, key = { "recent-${it.id}" }) { session ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StrideLabSurfaceTokens.surfaceElevated,
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = session.notes.ifBlank { session.discipline.label },
                                    color = StrideLabSurfaceTokens.ink,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${session.discipline.label} · ${session.performedOn}",
                                    color = StrideLabSurfaceTokens.inkMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                            if (session.durationMinutes.isNotBlank()) {
                                StrideLabMetricBadge("${session.durationMinutes} min", session.discipline.markColor)
                            }
                        }
                    }
                }
            }
        }
    }

    val selectedWorkout = selectedWorkoutId?.let { CuratedWorkoutLibrary.byId(it) }
    if (selectedWorkout != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedWorkoutId = null },
            sheetState = sheetState,
            containerColor = StrideLabSurfaceTokens.surfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WorkoutHubVisualThumbnail(
                        category = selectedWorkout.category,
                        accentColor = selectedWorkout.category.accentColor,
                        modifier = Modifier.size(58.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = selectedWorkout.category.label.uppercase(),
                            color = selectedWorkout.category.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedWorkout.title,
                            color = StrideLabSurfaceTokens.ink,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StrideLabMetricBadge("${selectedWorkout.durationMinutes} min", selectedWorkout.category.accentColor)
                    StrideLabMetricBadge(selectedWorkout.difficulty.label, StrideLabSurfaceTokens.inkMuted)
                    StrideLabMetricBadge("${selectedWorkout.intensity.label} intensity", StrideLabSurfaceTokens.inkMuted)
                }
                Text(
                    text = selectedWorkout.coachingNotes,
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 13.sp
                )
                Text(
                    text = "EXERCISES (${selectedWorkout.steps.size})",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                selectedWorkout.steps.forEach { step ->
                    val detail = buildString {
                        append("${step.sets}x ")
                        when {
                            step.reps != null -> append("${step.reps} reps")
                            step.holdSeconds != null -> append("${step.holdSeconds}s hold")
                            step.workSeconds != null -> append("${step.workSeconds}s")
                        }
                        if (step.restSeconds > 0) append(" · ${step.restSeconds}s rest")
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StrideLabSurfaceTokens.surfaceSubtle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(step.name, color = StrideLabSurfaceTokens.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(detail, color = selectedWorkout.category.accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (step.cue.isNotBlank()) {
                                Text(step.cue, color = StrideLabSurfaceTokens.inkMuted, fontSize = 11.5.sp)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val id = selectedWorkout.id
                        selectedWorkoutId = null
                        openGuided(id)
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StrideLabSurfaceTokens.lime,
                        contentColor = StrideLabSurfaceTokens.onLime
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Start Guided Session", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val workout = selectedWorkout
                            selectedWorkoutId = null
                            scope.launch {
                                val now = System.currentTimeMillis()
                                val draft = access.start(workout.toFreeformWorkout(owner, LocalDate.now(), now))
                                updateDraft(draft)
                                openDraft(draft.id)
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Log Manually", color = StrideLabSurfaceTokens.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = {
                            val template = selectedWorkout.toManualTemplate(owner)
                            selectedWorkoutId = null
                            scope.launch {
                                val existing = board?.templates.orEmpty().firstOrNull { it.id == template.id }
                                if (existing == null) {
                                    access.saveTemplate(template, null)
                                    onTemplateAdded(template)
                                }
                                scheduleTemplate(template.id)
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Schedule Day", color = StrideLabSurfaceTokens.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        val template = selectedWorkout.toManualTemplate(owner)
                        scope.launch {
                            val existing = board?.templates.orEmpty().firstOrNull { it.id == template.id }
                            if (existing == null) {
                                access.saveTemplate(template, null)
                                onTemplateAdded(template)
                                statusMessage = "Saved \"${selectedWorkout.title}\" to My Templates."
                            } else {
                                statusMessage = "\"${selectedWorkout.title}\" is already in My Templates."
                            }
                            selectedWorkoutId = null
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Save to My Templates", color = StrideLabSurfaceTokens.inkMuted, fontSize = 13.sp)
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}
