package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ActivityCardDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE | MMM d", Locale.ENGLISH)

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
    val countsByDiscipline = remember(allItems) {
        allItems.mapNotNull { it.discipline }.groupingBy { it }.eachCount()
    }
    val filtered = remember(allItems, selectedDiscipline) {
        if (selectedDiscipline == null) allItems
        else allItems.filter { it.discipline == selectedDiscipline }
    }
    val showSkeleton = rememberDelayedSkeletonVisible(loading && allItems.isEmpty())
    val context = LocalContext.current
    val reducedMotion = remember(context) { isSystemReducedMotion(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StrideLabSurfaceTokens.canvas)
            .testTag("activity-history-tab"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 14.dp,
            bottom = if (LocalFloatingDockVisible.current) 104.dp else 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "activity-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Activity",
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 30.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("activity-tab-title")
                )
                if (loading && allItems.isEmpty()) {
                    if (showSkeleton) {
                        SkeletonShimmerContainer(reducedMotion = reducedMotion) {
                            Box(
                                modifier = Modifier
                                    .size(width = 68.dp, height = 24.dp)
                                    .neutralSkeletonBrush(cornerRadius = 12.dp, reducedMotion = reducedMotion)
                            )
                        }
                    }
                } else {
                    Surface(
                        color = StrideLabSurfaceTokens.surfaceElevated,
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                    ) {
                        Text(
                            text = "${filtered.size} completed",
                            color = if (selectedDiscipline == null) {
                                StrideLabSurfaceTokens.inkSecondary
                            } else {
                                selectedDiscipline!!.markColor
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TabularTextStyle,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        item(key = "activity-filters") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .selectableGroup()
                        .testTag("activity-filters"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActivityModernFilterPill(
                        label = "All",
                        count = allItems.size,
                        accent = StrideLabSurfaceTokens.lime,
                        showDot = false,
                        selected = selectedDiscipline == null,
                        reducedMotion = reducedMotion,
                        tag = "activity-filter-all",
                        onClick = { selectedDiscipline = null }
                    )
                    activityFilterDisciplines.forEach { discipline ->
                        ActivityModernFilterPill(
                            label = discipline.label,
                            count = countsByDiscipline[discipline] ?: 0,
                            accent = discipline.markColor,
                            showDot = true,
                            selected = selectedDiscipline == discipline,
                            reducedMotion = reducedMotion,
                            tag = "activity-filter-${discipline.name.lowercase(Locale.ROOT)}",
                            onClick = {
                                selectedDiscipline = if (selectedDiscipline == discipline) null else discipline
                            }
                        )
                    }
                }
                StrideLabHairline()
            }
        }

        if (showSkeleton) {
            item(key = "activity-skeleton") {
                SkeletonShimmerContainer(reducedMotion = reducedMotion) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("activity-skeleton")
                    ) {
                        repeat(4) {
                            SkeletonActivityRow(reducedMotion = reducedMotion)
                        }
                    }
                }
            }
        } else if (!loading && filtered.isEmpty()) {
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
                ActivityHistoryRow(
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

/**
 * StrideLab AMOLED capsule filter pill:
 * - Sleek 34.dp visual pill centered inside a >= 48.dp accessible touch target.
 * - Unselected: #121212 surface, #1F242B hairline, discipline color dot, subtle count.
 * - Selected: signature discipline color fill with crisp #080A08 bold ink.
 */
@Composable
private fun ActivityModernFilterPill(
    label: String,
    count: Int,
    accent: Color,
    showDot: Boolean,
    selected: Boolean,
    reducedMotion: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val animMs = if (reducedMotion) 0 else 170
    val containerColor by animateColorAsState(
        targetValue = if (selected) accent else StrideLabSurfaceTokens.surfaceElevated,
        animationSpec = tween(animMs),
        label = "activity-pill-bg-$tag"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else StrideLabSurfaceTokens.hairline,
        animationSpec = tween(animMs),
        label = "activity-pill-border-$tag"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) StrideLabSurfaceTokens.onLime else StrideLabSurfaceTokens.ink,
        animationSpec = tween(animMs),
        label = "activity-pill-ink-$tag"
    )

    Box(
        modifier = Modifier
            .heightIn(min = StrideLabSurfaceTokens.minTouchTarget)
            .clip(RoundedCornerShape(100.dp))
            .selectable(selected = selected, role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .testTag(tag)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (showDot && !selected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                if (count > 0) {
                    Text(
                        text = count.toString(),
                        color = if (selected) {
                            StrideLabSurfaceTokens.onLime.copy(alpha = 0.72f)
                        } else {
                            StrideLabSurfaceTokens.inkMuted
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        style = TabularTextStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityHistoryRow(
    item: WorkoutEvidence,
    details: Map<String, ActivityDetail>,
    onClick: () -> Unit
) {
    val accent = item.discipline?.markColor ?: StrideLabSurfaceTokens.lime
    val dateLabel = remember(item.date) { item.date.format(ActivityCardDateFormatter) }
    val isCoached = WorkoutEvidenceSource.ENGINE15 in item.sources
    val providerLabel = remember(item.sourceLabel, isCoached) {
        val raw = item.sourceLabel.ifBlank { if (isCoached) "Coached" else "Freeform" }
        raw.uppercase(Locale.ENGLISH)
    }
    val importedId = (item.link as? WorkoutEvidenceLink.Imported)?.id
    val cachedDetail = importedId?.let { details[it] }
    val showGpsThumb = importedId != null && item.discipline == ManualDiscipline.RUNNING

    val totalSec = item.movingTimeSeconds?.takeIf { it.isFinite() && it > 0.0 }
        ?: item.durationMinutes?.takeIf { it.isFinite() && it > 0.0 }?.times(60.0)
    val distKm = item.distanceKm?.takeIf { it.isFinite() && it > 0.0 }
    val avgPaceSec = item.avgPaceSecPerKm?.takeIf { it.isFinite() && it > 0.0 }
        ?: if (item.discipline == ManualDiscipline.RUNNING && distKm != null && distKm >= 0.05 && totalSec != null) {
            totalSec / distKm
        } else null
    val avgHr = item.avgHrBpm?.takeIf { it.isFinite() && it > 0.0 }

    val metrics: List<Pair<String, String>> = if (item.discipline == ManualDiscipline.RUNNING) {
        val distStr = distKm?.let { String.format(Locale.US, "%.2f km", it) } ?: "--"
        val paceStr = avgPaceSec?.let { formatActivityPace(it) } ?: "--"
        val hrStr = avgHr?.let { "${it.toInt()} bpm" } ?: "--"
        listOf(
            "DISTANCE" to distStr,
            "AVG PACE" to paceStr,
            "AVG HR" to hrStr
        )
    } else {
        val durStr = formatActivityDuration(totalSec, item.durationMinutes)
        val setsMatch = Regex("""(\d+)\s+sets?""", RegexOption.IGNORE_CASE).find(item.detail)?.groupValues?.getOrNull(1)
        val secondMetric = when {
            distKm != null -> "DISTANCE" to String.format(Locale.US, "%.2f km", distKm)
            setsMatch != null -> "SETS" to "$setsMatch sets"
            else -> "DISCIPLINE" to (item.discipline?.label ?: "Workout")
        }
        val thirdMetric = when {
            avgHr != null -> "AVG HR" to "${avgHr.toInt()} bpm"
            else -> "TYPE" to (if (isCoached) "Coached" else "Freeform")
        }
        listOf(
            "DURATION" to durStr,
            secondMetric,
            thirdMetric
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .strideLabPressFeedback(
                    shape = RoundedCornerShape(12.dp),
                    highlightBorderColor = accent,
                    onClick = onClick
                )
                .padding(horizontal = 2.dp, vertical = 14.dp)
                .testTag("activity-evidence-${item.key}"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 10.dp, height = 4.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(accent)
                    )
                    Text(
                        text = dateLabel,
                        color = StrideLabSurfaceTokens.inkSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = TabularTextStyle,
                        maxLines = 1
                    )
                }
                Text(
                    text = providerLabel,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = (item.discipline?.label ?: "Workout").uppercase(Locale.ENGLISH),
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.title,
                        color = StrideLabSurfaceTokens.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showGpsThumb) {
                    TodayCachedGpsThumbnail(
                        activityId = importedId,
                        detail = cachedDetail,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                metrics.forEach { (label, value) ->
                    Column(
                        modifier = Modifier.weight(1f),
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
                            color = StrideLabSurfaceTokens.ink,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TabularTextStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        StrideLabHairline()
    }
}

private fun formatActivityPace(secPerKm: Double): String {
    val totalSec = secPerKm.toInt().coerceAtLeast(1)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%d:%02d /km", mins, secs)
}

private fun formatActivityDuration(movingSeconds: Double?, durationMinutes: Double?): String {
    val sec = movingSeconds?.takeIf { it.isFinite() && it > 0.0 }?.toInt()
        ?: durationMinutes?.takeIf { it.isFinite() && it > 0.0 }?.let { (it * 60.0).toInt() }
        ?: return "--"
    val hrs = sec / 3600
    val mins = (sec % 3600) / 60
    val remSec = sec % 60
    return when {
        hrs > 0 -> String.format(Locale.US, "%d:%02d:%02d", hrs, mins, remSec)
        else -> String.format(Locale.US, "%d:%02d", mins, remSec)
    }
}
