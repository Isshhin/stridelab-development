package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val activityFilterDisciplines = listOf(
    ManualDiscipline.RUNNING,
    ManualDiscipline.STRENGTH,
    ManualDiscipline.CALISTHENICS,
    ManualDiscipline.CONDITIONING,
    ManualDiscipline.PLYOMETRICS,
    ManualDiscipline.ISOMETRICS
)

/**
 * Dedicated primary Activity tab backed read-only by projectWorkoutEvidence (WorkoutEvidence.kt).
 * Unifies Garmin / .FIT, Coached, Custom, and Freeform completed sessions in reverse-chronological order
 * while leaving Training focused on plans and forward-looking schedules.
 */
@Composable
internal fun ActivityHistoryTabScreen(
    evidence: List<WorkoutEvidence>?,
    loading: Boolean,
    details: Map<String, ActivityDetail>,
    onOpenActivityDetail: (String) -> Unit,
    onOpenWorkout: (String, String) -> Unit,
    onOpenFreeformHistory: (String, Boolean) -> Unit,
    onOpenQuickLog: () -> Unit
) {
    var selectedDiscipline by rememberSaveable { mutableStateOf<ManualDiscipline?>(null) }
    val allItems = evidence.orEmpty()
    val filtered = remember(allItems, selectedDiscipline) {
        if (selectedDiscipline == null) allItems
        else allItems.filter { it.discipline == selectedDiscipline }
    }
    val showSkeleton = rememberDelayedSkeletonVisible(loading && evidence == null)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StrideLabSurfaceTokens.canvas)
            .statusBarsPadding()
            .testTag("activity-history-tab"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = if (LocalFloatingDockVisible.current) 104.dp else 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "activity-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Activity",
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("activity-tab-title")
                )
                if (!showSkeleton) {
                    Text(
                        text = "${filtered.size} completed",
                        color = StrideLabSurfaceTokens.inkSecondary,
                        fontSize = 12.5.sp,
                        style = TabularTextStyle,
                        maxLines = 1
                    )
                }
            }
        }

        item(key = "activity-filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp)
                    .testTag("activity-filters"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityFilterChip(
                    label = "All",
                    selected = selectedDiscipline == null,
                    tag = "activity-filter-all",
                    onClick = { selectedDiscipline = null }
                )
                activityFilterDisciplines.forEach { discipline ->
                    ActivityFilterChip(
                        label = discipline.label,
                        selected = selectedDiscipline == discipline,
                        tag = "activity-filter-${discipline.name.lowercase(Locale.ROOT)}",
                        onClick = {
                            selectedDiscipline = if (selectedDiscipline == discipline) null else discipline
                        }
                    )
                }
            }
            StrideLabHairline()
        }

        if (showSkeleton) {
            item(key = "activity-skeleton") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    SkeletonActivityRow()
                    SkeletonActivityRow()
                    SkeletonActivityRow()
                }
            }
        } else if (filtered.isEmpty()) {
            item(key = "activity-empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp)
                        .testTag("activity-empty-state"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (selectedDiscipline == null) {
                            "No completed workouts yet"
                        } else {
                            "No completed ${selectedDiscipline!!.label.lowercase(Locale.ROOT)} workouts yet"
                        },
                        color = StrideLabSurfaceTokens.ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Completed coached sessions, synced Garmin runs, and logged workouts will appear here chronologically.",
                        color = StrideLabSurfaceTokens.inkSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                    Button(
                        onClick = onOpenQuickLog,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StrideLabSurfaceTokens.lime,
                            contentColor = StrideLabSurfaceTokens.onLime
                        ),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .heightIn(min = 48.dp)
                            .testTag("activity-empty-quick-log")
                    ) {
                        Text("Log a workout", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            items(filtered, key = { it.key }) { item ->
                ActivityEvidenceRow(
                    item = item,
                    details = details,
                    onClick = {
                        when (val link = item.link) {
                            is WorkoutEvidenceLink.Imported -> onOpenActivityDetail(link.id)
                            is WorkoutEvidenceLink.Coached -> onOpenWorkout(link.planId, link.sessionId)
                            is WorkoutEvidenceLink.Manual -> onOpenFreeformHistory(link.id, link.legacy)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActivityFilterChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = StrideLabSurfaceTokens.surfaceSubtle,
            labelColor = StrideLabSurfaceTokens.inkSecondary,
            selectedContainerColor = StrideLabSurfaceTokens.lime,
            selectedLabelColor = StrideLabSurfaceTokens.onLime
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = StrideLabSurfaceTokens.hairline,
            selectedBorderColor = StrideLabSurfaceTokens.lime
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
    )
}

@Composable
private fun ActivityEvidenceRow(
    item: WorkoutEvidence,
    details: Map<String, ActivityDetail>,
    onClick: () -> Unit
) {
    val accent = item.discipline?.markColor ?: StrideLabSurfaceTokens.lime
    val eyebrow = listOfNotNull(
        item.discipline?.label ?: "Workout",
        item.sourceLabel.takeIf { it.isNotBlank() }
    ).joinToString(" · ").uppercase(Locale.ROOT)
    val summaryMetrics = item.detail.ifBlank {
        listOfNotNull(
            item.durationMinutes?.takeIf { it > 0.0 }?.let { "${freeformNumber(it)} min" },
            item.distanceKm?.takeIf { it > 0.0 }?.let { "${freeformNumber(it)} km" }
        ).joinToString(" · ")
    }
    val subline = listOfNotNull(
        item.date.toString(),
        summaryMetrics.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    val importedId = (item.link as? WorkoutEvidenceLink.Imported)?.id
    val showGpsThumb = importedId != null && item.discipline == ManualDiscipline.RUNNING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity-evidence-${item.key}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = eyebrow,
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.title,
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subline,
                    color = StrideLabSurfaceTokens.inkSecondary,
                    fontSize = 12.5.sp,
                    style = TabularTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showGpsThumb) {
                TodayCachedGpsThumbnail(
                    activityId = importedId,
                    detail = details[importedId],
                    modifier = Modifier.size(width = 64.dp, height = 40.dp)
                )
            } else {
                Text(
                    text = "›",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 18.sp
                )
            }
        }
        StrideLabHairline()
    }
}
