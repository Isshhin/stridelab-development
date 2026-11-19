package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.abs

private val activityLime = Color(0xFFD8FB70)
private val activitySheetBlack = Color(0xFF080D0C)
private val activityText = Color(0xFFF3F6F3)
private val activityMuted = Color(0xFFA6B4AD)
private val activityBorder = Color(0xFF303E37)
private val activitySurface = Color(0xFF111A16)

private data class SourceChartAvailability(
    val pace: Boolean, val heartRate: Boolean, val elevation: Boolean, val cadence: Boolean
) {
    val any: Boolean get() = pace || heartRate || elevation || cadence
}

/** Approved initial peek: unboxed three metrics, no tabs, tags or dummy data. */
@Composable
internal fun TracksideActivityInitialPreview(activity: ActivityRecord) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .testTag("activity-parity-overview"),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            activityDisplayName(activity.title),
            color = activityText, fontSize = 29.sp, lineHeight = 35.sp,
            fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("activity-preview-title")
        )
        Spacer(Modifier.height(5.dp))
        Text(activity.dateLabel, color = activityMuted, fontSize = 12.sp)
        Spacer(Modifier.height(26.dp))
        TracksidePrimaryActivityMetrics(activity)
        // This is the bottom of the collapsed sheet, not a large empty
        // scrolling region. The sheet peek is sized to the measured preview.
        Spacer(Modifier.height(18.dp).testTag("activity-preview-bottom"))
    }
}

private fun activityDisplayName(name: String): String {
    // Shorten the redundant generic sport suffix only for display; source data
    // and the recorded title remain untouched.
    return if (name.endsWith(" Running", ignoreCase = true) && name.length > 8)
        name.dropLast(8).trimEnd() else name
}

private data class ActivityMetricItem(
    val label: String, val value: String, val unit: String, val glyph: ActivityMetricGlyph
)

@Composable
internal fun TracksidePrimaryActivityMetrics(activity: ActivityRecord) {
    val recordedHr = activity.averageHr?.takeIf { it.isFinite() && it > 0.0 }
    val metrics = buildList {
        if (activityUsesStationaryDetail(activity)) {
            add(ActivityMetricItem(
                "DURATION",
                activityDisplayDurationSeconds(activity)?.let { ActivityRecord.duration(it) } ?: "—",
                "", ActivityMetricGlyph.Duration
            ))
            if (recordedHr != null) add(ActivityMetricItem(
                "AVG HR", recordedHr.roundToInt().toString(), "bpm",
                ActivityMetricGlyph.Heart
            ))
            activity.maximumHr?.takeIf { it.isFinite() && it > 0.0 }?.let {
                add(ActivityMetricItem(
                    "MAX HR", it.roundToInt().toString(), "bpm",
                    ActivityMetricGlyph.Heart
                ))
            }
        } else {
            add(ActivityMetricItem("DISTANCE", activity.distanceM?.let {
                String.format(Locale.getDefault(), "%.2f", it / 1000.0)
            } ?: "—", if (activity.distanceM != null) "km" else "", ActivityMetricGlyph.Route))
            add(ActivityMetricItem("AVG PACE",
                if (activity.supportsPace && activity.detailPaceLabel != "Not recorded")
                    activity.detailPaceLabel.removeSuffix(" /km").trim() else "—",
                if (activity.supportsPace && activity.detailPaceLabel != "Not recorded") "/km" else "",
                ActivityMetricGlyph.Shoe))
            if (recordedHr != null) add(ActivityMetricItem("AVG HR",
                recordedHr.roundToInt().toString(), "bpm", ActivityMetricGlyph.Heart))
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("activity-primary-metrics")) {
        // Keep the map-dominant collapsed sheet compact on regular phones.
        // A larger system font alone must not turn three metrics into a tall
        // three-row sheet when the viewport can fit the compact metrics.
        // Narrow devices and very large accessibility text may still stack.
        val fontScale = LocalDensity.current.fontScale
        val stacked = maxWidth < 300.dp ||
            (fontScale > 1.8f && maxWidth < 420.dp)
        val compactMetrics = fontScale > 1.35f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                metrics.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = activityBorder.copy(alpha = .55f),
                            thickness = 1.dp
                        )
                    }
                    CenteredActivityMetricSections(listOf(item), compact = false)
                }
            }
        } else {
            CenteredActivityMetricSections(metrics, compact = compactMetrics)
        }
    }
}

@Composable
private fun CenteredActivityMetricSections(
    items: List<ActivityMetricItem>, compact: Boolean
) {
    Row(
        Modifier.fillMaxWidth().testTag("activity-metric-sections"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    Modifier.width(1.dp).height(if (compact) 54.dp else 60.dp)
                        .background(activityBorder.copy(alpha = .72f))
                        .testTag("activity-metric-divider-$index")
                )
            }
            PrimaryActivityMetric(
                item.label, item.value, item.unit, item.glyph,
                Modifier.weight(1f)
                    .testTag("activity-metric-" +
                        item.label.lowercase(Locale.ROOT).replace(" ", "-")),
                compact = compact
            )
        }
    }
}

private enum class ActivityMetricGlyph { Route, Duration, Shoe, Heart }

@Composable
private fun PrimaryActivityMetric(
    label: String, value: String, unit: String,
    symbol: ActivityMetricGlyph, modifier: Modifier = Modifier, compact: Boolean = false
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(23.dp)) {
            val w = size.width
            val h = size.height
            val sw = 2.3.dp.toPx()
            when (symbol) {
                ActivityMetricGlyph.Duration -> {
                    drawCircle(activityLime, radius = w * .35f,
                        center = Offset(w * .5f, h * .55f), style = Stroke(sw))
                    drawLine(activityLime,
                        Offset(w * .5f, h * .55f), Offset(w * .5f, h * .33f),
                        sw, StrokeCap.Round)
                    drawLine(activityLime,
                        Offset(w * .5f, h * .55f), Offset(w * .66f, h * .62f),
                        sw, StrokeCap.Round)
                    drawLine(activityLime,
                        Offset(w * .37f, h * .10f), Offset(w * .63f, h * .10f),
                        sw, StrokeCap.Round)
                }
                ActivityMetricGlyph.Route -> {
                    drawCircle(activityLime, radius = w * .12f,
                        center = Offset(w * .16f, h * .65f), style = Stroke(sw))
                    drawCircle(activityLime, radius = w * .12f,
                        center = Offset(w * .78f, h * .22f), style = Stroke(sw))
                    val p = Path().apply {
                        moveTo(w * .16f, h * .79f)
                        lineTo(w * .34f, h * .89f)
                        lineTo(w * .60f, h * .63f)
                        lineTo(w * .50f, h * .44f)
                        lineTo(w * .78f, h * .36f)
                    }
                    drawPath(p, activityLime, style = Stroke(sw, cap = StrokeCap.Round))
                }
                ActivityMetricGlyph.Shoe -> {
                    val p = Path().apply {
                        moveTo(w * .10f, h * .67f)
                        lineTo(w * .35f, h * .49f)
                        lineTo(w * .54f, h * .18f)
                        lineTo(w * .67f, h * .42f)
                        lineTo(w * .89f, h * .58f)
                        lineTo(w * .89f, h * .78f)
                        lineTo(w * .12f, h * .78f)
                        close()
                    }
                    drawPath(p, activityLime, style = Stroke(sw, cap = StrokeCap.Round))
                }
                ActivityMetricGlyph.Heart -> {
                    val p = Path().apply {
                        moveTo(w * .5f, h * .85f)
                        cubicTo(w * .29f, h * .72f, w * .09f, h * .53f, w * .15f, h * .32f)
                        cubicTo(w * .23f, h * .12f, w * .43f, h * .16f, w * .5f, h * .31f)
                        cubicTo(w * .58f, h * .16f, w * .78f, h * .12f, w * .86f, h * .32f)
                        cubicTo(w * .93f, h * .53f, w * .72f, h * .72f, w * .5f, h * .85f)
                        close()
                    }
                    drawPath(p, activityLime, style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
        }
        Text(
            label, color = activityMuted, fontSize = 9.5.sp,
            lineHeight = 12.sp, maxLines = 1, textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)
        ) {
            Text(
                value, color = activityText,
                fontSize = if (compact) 16.sp else 19.sp,
                lineHeight = if (compact) 20.sp else 23.sp,
                fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (unit.isNotEmpty()) Text(
                unit, color = activityMuted,
                fontSize = if (compact) 9.sp else 10.5.sp,
                lineHeight = 19.sp, maxLines = 1
            )
        }
    }
}

/** Compact header used only after the user expands the details sheet. */
@Composable
internal fun TracksideActivityExpandedHeader(
    activity: ActivityRecord,
    onBack: () -> Unit,
    onShare: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .heightIn(min = 51.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(42.dp).testTag("activity-expanded-back")
        ) { Text("‹", fontSize = 28.sp, color = activityText) }
        Column(Modifier.weight(1f)) {
            Text(
                activityDisplayName(activity.title), color = activityText,
                fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(activity.dateLabel, color = activityMuted, fontSize = 10.sp, maxLines = 1)
        }
        if (onEdit != null) ActivityMapHeaderAction(
            label = "Edit", tag = "activity-edit-expanded", onClick = onEdit
        )
        if (onShare != null) ActivityMapHeaderAction(
            label = "Share", tag = "activity-share-expanded",
            onClick = onShare, shareIcon = true
        )
    }
}

/**
 * One continuous lazy list. The segmented rail never replaces the body.
 * Four checkpoints scroll one sheet; its visible section updates the active checkpoint.
 */
@Composable
internal fun TracksideActivityContinuousSections(
    activity: ActivityRecord,
    recorded: ActivityDetail?,
    detailSource: ActivityRecord,
    matchedSourceIds: Set<String>,
    history: List<ActivityRecord>,
    state: TrainingState,
    actions: NativeActions,
    online: Boolean,
    selectedTab: Int,
    onTab: (Int) -> Unit,
    onLoadGarminGps: (() -> Unit)?,
    selectedSeconds: Double? = null,
    onSelectSeconds: (Double) -> Unit = {},
    onSelectRange: (Pair<Double,Double>?) -> Unit = {}
) {
    val laps = remember(recorded) { recorded?.laps?.take(100).orEmpty() }
    val hasSplitHr = remember(laps) { laps.any { it.hr != null } }
    val paceBounds = remember(laps, activity.supportsPace) {
        val paces = if (activity.supportsPace)
            laps.mapNotNull { it.pace?.takeIf { pace -> pace.isFinite() && pace > 0 } }
        else emptyList()
        (paces.minOrNull() to paces.maxOrNull())
    }
    // A FIT recording may contain thousands of samples. Inspect them once
    // when the source changes, never for every LazyColumn measurement.
    val chartAvailability = remember(recorded, activity.supportsPace) {
        val samples = recorded?.samples.orEmpty()
        var pace = 0
        var hr = 0
        var altitude = 0
        var cadence = 0
        for (sample in samples) {
            if (activity.supportsPace && sample.pace?.isFinite() == true) pace++
            if (sample.hr?.isFinite() == true) hr++
            if (sample.altitude?.isFinite() == true) altitude++
            if (sample.cadenceSpm?.isFinite() == true) cadence++
            if (pace >= 2 && hr >= 2 && altitude >= 2 && cadence >= 2) break
        }
        SourceChartAvailability(pace >= 2, hr >= 2, altitude >= 2, cadence >= 2)
    }
    val splitWindows = remember(laps, recorded) {
        var cumulative = 0.0
        val allowCumulativeFallback = recorded?.pauseIntervals.isNullOrEmpty()
        val recordingEnd = recorded?.samples?.lastOrNull()?.seconds
        laps.mapIndexed { index, lap ->
            val duration = lap.seconds?.takeIf { it.isFinite() && it > 0.0 }
            val start = lap.startSeconds?.takeIf { it.isFinite() && it >= 0.0 }
                ?: cumulative.takeIf { allowCumulativeFallback && duration != null }
            if (duration != null) cumulative += duration
            val nextSourceStart = laps.getOrNull(index + 1)?.startSeconds
                ?.takeIf { next ->
                    start != null && next.isFinite() && next > start
                }
            val recordingEndForLap = recordingEnd?.takeIf { end ->
                index == laps.lastIndex && start != null && end > start
            }
            val durationEnd = if (start != null && duration != null) start + duration else null
            val end = nextSourceStart ?: recordingEndForLap ?: durationEnd
            if (start != null && end != null && end > start) start to end else null
        }
    }
    val splitInsight = remember(laps, activity.supportsPace) {
        ActivityDetailSectionAnalysis.splits(laps, activity.supportsPace)
    }
    val analysisStartIndex = 1
    val splitStartIndex = 2
    val chartsStartIndex = 3 + laps.size + (if (splitInsight != null) 1 else 0)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentSection by remember(listState, chartsStartIndex) {
        derivedStateOf {
            val layout = listState.layoutInfo
            // The active section changes as its heading crosses the upper
            // third of the viewport; avoid measuring every scroll pixel.
            val threshold = layout.viewportStartOffset +
                ((layout.viewportEndOffset - layout.viewportStartOffset) * .32f).roundToInt()
            val anchor = layout.visibleItemsInfo.lastOrNull { it.offset <= threshold }?.index
                ?: listState.firstVisibleItemIndex
            when {
                // With no fabricated trailing spacer, shorter chart sections
                // may end before their heading can cross the upper threshold.
                // At the genuine content end, Charts must still be selected.
                !listState.canScrollForward &&
                    layout.visibleItemsInfo.any { it.index >= chartsStartIndex } -> 3
                anchor >= chartsStartIndex -> 3
                anchor >= splitStartIndex -> 2
                anchor >= analysisStartIndex -> 1
                else -> 0
            }
        }
    }
    var pendingSection by remember(activity.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(activity.id) {
        listState.scrollToItem(0)
        if (selectedTab != 0) onTab(0)
    }
    LaunchedEffect(currentSection, pendingSection) {
        if (pendingSection == null && selectedTab != currentSection) onTab(currentSection)
    }
    val visibleSection = pendingSection ?: currentSection
    val sectionOffset = listOf(0, analysisStartIndex, splitStartIndex, chartsStartIndex)
    Column(Modifier.fillMaxSize()) {
        TracksideActivitySectionPill(visibleSection) { index ->
            pendingSection = index
            onTab(index)
            scope.launch {
                try { listState.animateScrollToItem(sectionOffset[index]) }
                finally { pendingSection = null }
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("activity-continuous-scroll"),
            state = listState,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item(key = "section-overview") {
                Column(Modifier.fillMaxWidth().testTag("activity-section-overview")) {
                    Spacer(Modifier.height(8.dp))
                    TracksideActivityOverviewBody(
                        activity, recorded, detailSource, matchedSourceIds,
                        history, state, actions, online, onLoadGarminGps
                    )
                }
            }
            item(key = "section-analysis") {
                TracksideActivityAnalysisBody(
                    activity, recorded, history, state, actions, matchedSourceIds
                )
            }
            item(key = "section-splits") {
                Column(Modifier.fillMaxWidth().testTag("activity-section-splits")) {
                    ActivitySectionHeading("Splits")
                    Spacer(Modifier.height(3.dp))
                    if (laps.isEmpty()) {
                        Text(
                            "No recorded splits are available.",
                            color = activityMuted, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("SPLIT", color = activityMuted, fontSize = 10.sp,
                                modifier = Modifier.width(34.dp))
                            Text("PACE", color = activityMuted, fontSize = 10.sp,
                                modifier = Modifier.weight(1f))
                            if (hasSplitHr) Text("HR", color = activityMuted, fontSize = 10.sp,
                                modifier = Modifier.width(33.dp))
                        }
                    }
                }
            }
            itemsIndexed(
                laps,
                key = { index, lap -> "source-lap-" + index + "-" + lap.index }
            ) { index, lap ->
                val window = splitWindows.getOrNull(index)
                val selected = window != null && selectedSeconds != null &&
                    selectedSeconds >= window.first && selectedSeconds <= window.second
                TracksideActivitySplitRow(
                    index, lap, hasSplitHr, paceBounds, activity.supportsPace,
                    selected = selected,
                    onSelect = window?.let { range ->
                        {
                            if (selected) {
                                onSelectRange(null)
                            } else {
                                onSelectSeconds((range.first + range.second) / 2.0)
                                onSelectRange(range)
                            }
                        }
                    }
                )
            }
            if (splitInsight != null) {
                item(key = "section-splits-analysis") {
                    Spacer(Modifier.height(9.dp))
                    TracksideActivityIntelligenceCard(
                        splitInsight, "Splits Analysis", "activity-splits-insights"
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
            item(key = "section-charts") {
                Column(Modifier.fillMaxWidth().testTag("activity-section-charts")) {
                    ActivitySectionHeading("Charts")
                    Spacer(Modifier.height(5.dp))
                    if (!chartAvailability.any) {
                        Text(
                            "No recorded charts are available.",
                            color = activityMuted, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                }
            }
            if (recorded != null) {
                if (chartAvailability.pace)
                    item(key = "chart-pace") {
                        TracksideActivitySourceChart(
                            "Pace", "/km", recorded.samples, { it.pace }, pace = true,
                            selectedSeconds = selectedSeconds,
                            onSelectSeconds = onSelectSeconds,
                            pauseIntervals = recorded.pauseIntervals
                        )
                    }
                if (chartAvailability.heartRate)
                    item(key = "chart-heart-rate") {
                        TracksideActivitySourceChart(
                            "Heart rate", "bpm", recorded.samples, { it.hr },
                            selectedSeconds = selectedSeconds,
                            onSelectSeconds = onSelectSeconds,
                            pauseIntervals = recorded.pauseIntervals
                        )
                    }
                if (chartAvailability.heartRate)
                    item(key = "chart-heart-rate-zones") {
                        TracksideActivityHeartRateZones(
                            recorded, activityCorrectionOwner(activity.id, state), state.account)
                    }
                if (chartAvailability.elevation)
                    item(key = "chart-elevation") {
                        TracksideActivitySourceChart(
                            "Elevation", "m", recorded.samples, { it.altitude },
                            selectedSeconds = selectedSeconds,
                            onSelectSeconds = onSelectSeconds,
                            pauseIntervals = recorded.pauseIntervals
                        )
                    }
                if (chartAvailability.cadence)
                    item(key = "chart-cadence") {
                        TracksideActivitySourceChart(
                            "Cadence", "spm", recorded.samples, { it.cadenceSpm },
                            selectedSeconds = selectedSeconds,
                            onSelectSeconds = onSelectSeconds,
                            pauseIntervals = recorded.pauseIntervals
                        )
                    }
            }
        }
        }
    }
}

@Composable
private fun TracksideActivitySectionPill(selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)
            .height(48.dp).background(activitySurface, RoundedCornerShape(28.dp))
            .testTag("activity-sticky-pill")
    ) {
        val segment = maxWidth / 4
        val density = LocalDensity.current
        val position by animateDpAsState(
            targetValue = segment * selected,
            animationSpec = tween(durationMillis = 180),
            label = "activity-section-pill-position"
        )
        // Translate only the highlight layer; avoid remeasuring the rail
        // during every animation frame on high-refresh-rate phones.
        Box(
            Modifier.width(segment).fillMaxHeight()
                .graphicsLayer { translationX = with(density) { position.toPx() } }
                .padding(3.dp)
                .background(activityLime, RoundedCornerShape(25.dp))
        )
        Row(Modifier.fillMaxSize()) {
            listOf("Overview", "Analysis", "Splits", "Charts").forEachIndexed { index, name ->
                val color by animateColorAsState(
                    targetValue = if (selected == index) activitySheetBlack else activityMuted,
                    animationSpec = tween(140), label = "activity-tab-ink"
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .testTag("activity-tab-" + name.lowercase())
                        .semantics {
                            role = Role.Tab
                            this.selected = index == selected
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name, color = color, fontSize = 11.sp,
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable private fun ActivitySectionHeading(title: String) {
    Text(
        title, color = activityText, fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 13.dp)
            .testTag("activity-section-heading-" +
                title.lowercase(Locale.ROOT).replace(" ", "-"))
            .semantics { heading() }
    )
}

@Composable
private fun TracksideActivityAnalysisBody(
    activity: ActivityRecord,
    recorded: ActivityDetail?,
    history: List<ActivityRecord>,
    state: TrainingState,
    actions: NativeActions,
    matchedSourceIds: Set<String>
) {
    val insight = remember(activity, recorded, history) {
        ActivityIntelligence.presentation(activity, recorded, history)
    }
    val character = remember(activity, recorded) {
        ActivityIntelligence.workoutCharacter(activity, recorded)
    }
    val context = LocalContext.current
    val labelStore = remember(context) { UserActivityWorkoutLabelStore(context) }
    val correctionOwner = remember(activity.id, state.account, state.localPlanOwner,
        state.localGarminOwner, state.localGarminIds) {
        activityCorrectionOwner(activity.id, state)
    }
    var userLabel by remember(correctionOwner, activity.id) {
        mutableStateOf(labelStore.load(correctionOwner, activity.id))
    }
    var labelSaveFailed by remember(correctionOwner, activity.id) { mutableStateOf(false) }
    val heartRateSettings = remember(context, state.account) {
        AthleteHeartRatePreferences(context).settings(state.account)
    }
    val recordedZones = remember(context, correctionOwner, recorded, heartRateSettings) {
        recorded?.let { detail ->
            val sources = GarminZonePreferenceStore(context)
            if (sources.source(correctionOwner) == DisplayHeartRateZoneSource.GARMIN)
                sources.load(correctionOwner)?.let {
                    HeartRateZoneAnalysis.distribute(detail.samples, it.profile)
                }
            else HeartRateZoneAnalysis.distribute(detail.samples, heartRateSettings)
        }
    }
    val workoutAssessment = remember(activity, recorded, state.plans, matchedSourceIds,
        history, recordedZones) {
        ActivityWorkoutIntelligence.assess(
            activity, recorded, state.plans, matchedSourceIds,
            listOfNotNull(state.account, state.localPlanOwner).toSet(),
            history, recordedZones
        )
    }
    val effort = remember(activity, recorded, history, state.plans, matchedSourceIds,
        heartRateSettings, correctionOwner) {
        ActivityEffortAnalysis.assess(
            activity, recorded, matchedSourceIds, state.plans, history,
            listOfNotNull(state.account, state.localPlanOwner).toSet(),
            history.mapTo(mutableSetOf()) { it.id } + activity.id,
            heartRateSettings
        )
    }
    val athleteEvidence = remember(history, activity, correctionOwner, userLabel) {
        correctionOwner?.let { owner ->
            val rows = (history + activity).distinctBy { it.id }
            val labels = rows.mapNotNull { row ->
                labelStore.load(owner, row.id)?.let { row.id to it }
            }.toMap() + listOfNotNull(userLabel?.let { activity.id to it })
            ActivityAthleteEvidence.profile(
                owner, rows, rows.mapTo(mutableSetOf()) { it.id }, labels,
                java.time.Instant.now(), historyWeeks = 4
            )
        }
    }
    Column(
        Modifier.fillMaxWidth().testTag("activity-analysis-screen")
            .padding(top = 16.dp, bottom = 16.dp)
    ) {
        if (!DashboardSummary.isRun(activity)) {
            ActivitySectionHeading("Analysis")
            Text(
                "Run analysis is unavailable for this activity type. No running workout type or training conclusion is inferred.",
                color = activityMuted, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
            return@Column
        }
        Text(
            "YOUR RUN, EXPLAINED",
            color = activityLime, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            insight?.headline ?: "A record of your run.",
            color = activityText, fontSize = 23.sp, lineHeight = 29.sp,
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().testTag("activity-analysis-headline")
                .semantics { heading() }
        )
        Spacer(Modifier.height(9.dp))
        Text(
            insight?.summary ?: "There isn't enough recorded information for a meaningful comparison yet.",
            color = Color(0xFFCBD7CF), fontSize = 13.sp, lineHeight = 20.sp,
            modifier = Modifier.testTag("activity-analysis-explained")
        )
        Spacer(Modifier.height(12.dp))
        ActivityWorkoutClassificationCard(
            assessment = workoutAssessment,
            userLabel = userLabel,
            canSave = correctionOwner != null,
            saveFailed = labelSaveFailed,
            onSelect = { label ->
                labelSaveFailed = !labelStore.save(correctionOwner, activity.id, label)
                if (!labelSaveFailed) userLabel = label
            },
            onClear = {
                labelSaveFailed = !labelStore.clear(correctionOwner, activity.id)
                if (!labelSaveFailed) userLabel = null
            }
        )
        workoutAssessment.adherence?.let { adherence ->
            Spacer(Modifier.height(12.dp))
            ActivityWorkoutComparisonCard(activity, workoutAssessment.linkedSession, adherence)
        }
        ActivityRecordedWorkoutTimeline(recorded)
        Spacer(Modifier.height(20.dp))
        val focusShape = RoundedCornerShape(14.dp)
        Column(
            Modifier.fillMaxWidth()
                .background(Color(0xFF181C12), focusShape)
                .border(1.dp, Color(0xFF444329), focusShape)
                .testTag("activity-analysis-focus")
                .padding(horizontal = 15.dp, vertical = 14.dp)
        ) {
            Text(
                "FOCUS FOR YOUR NEXT RUN",
                color = activityLime, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "Use your next planned session and how you feel to guide effort. One recorded run alone can't establish fitness, fatigue or recovery.",
                color = activityText, fontSize = 13.sp, lineHeight = 19.sp
            )
        }
        ActivityAnalysisSection("How It Went", "activity-analysis-how-it-went") {
            if (character != null) {
                Text(character, color = activityText, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(vertical = 6.dp))
            } else {
                Text(
                    if (recorded == null)
                        "Recorded splits and sensor samples aren't available for this activity."
                    else "Not enough recorded detail to describe pacing or cadence reliably.",
                    color = activityMuted, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            val measured = remember(activity, recorded, history) {
                ActivityIntelligence.briefInsights(activity, recorded, history)
                    .filterNot { it.startsWith("No reliable pace comparison") }
                    .take(3)
            }
            measured.forEach { line ->
                HorizontalDivider(color = activityBorder.copy(alpha = .6f), thickness = .7.dp)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 7.dp).size(5.dp)
                        .background(activityLime, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(line, color = activityMuted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            athleteEvidence?.longestRecordedRunLast28DaysKm?.let { distance ->
                Text(
                    "Your longest recorded run in the last four weeks was " +
                        "${String.format(Locale.ROOT, "%.1f", distance)} km. Saved activity history may be incomplete.",
                    color = activityMuted, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                        .testTag("activity-longest-recorded-run")
                )
            }
            HorizontalDivider(color = activityBorder.copy(alpha = .6f), thickness = .7.dp)
            Text("SESSION EFFORT", color = activityLime, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text(
                effort.reportedEffort?.let { "You rated this session $it out of 10." }
                    ?: "No post-run effort rating is available for this session.",
                color = if (effort.reportedEffort == null) activityMuted else activityText,
                fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp)
            )
            Text(
                effort.comparison?.let {
                    "Compared with ${it.sessionCount} similar completed runs, your median reported effort was " +
                        "${String.format(Locale.ROOT, "%.1f", it.medianReportedEffort)} out of 10."
                } ?: "A comparison needs at least three comparable past runs with saved effort ratings.",
                color = activityMuted, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp).testTag("activity-effort-comparison")
            )
            Text(
                when (effort.confidence) {
                    ActivityEffortConfidence.USER_REPORTED -> "Based on your saved feedback."
                    ActivityEffortConfidence.RECORDED_HR_WITH_GAPS -> "Heart-rate evidence is approximate; some recording intervals were excluded."
                    ActivityEffortConfidence.RECORDED_HR_WITHOUT_GAPS -> "Heart-rate evidence is approximate and uses your saved zone settings."
                    ActivityEffortConfidence.USER_REPORTED_AND_HR -> "Uses your saved feedback and approximate heart-rate data as separate evidence."
                    ActivityEffortConfidence.INSUFFICIENT -> "There isn't enough reliable effort evidence to assess this session."
                },
                color = activityMuted, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp).testTag("activity-effort-confidence")
            )
        }
        ActivityAnalysisSection("Time in Each Zone", "activity-analysis-heart-rate-zones") {
            val hrSamples = recorded?.samples.orEmpty().count { it.hr?.isFinite() == true && it.hr > 0.0 }
            when {
                recorded == null -> Text(
                    "No recorded heart-rate stream is available for this activity.",
                    color = activityMuted, fontSize = 12.sp, lineHeight = 17.sp
                )
                hrSamples < 2 -> Text(
                    "Heart-rate zones are unavailable because this recording has fewer than two valid heart-rate samples.",
                    color = activityMuted, fontSize = 12.sp, lineHeight = 17.sp
                )
                else -> TracksideActivityHeartRateZones(recorded, correctionOwner, state.account)
            }
        }
        ActivityAnalysisSection("How Did It Feel?", "activity-analysis-feel") {
            TracksideActivityFeedbackEditor(
                state, actions.planning, matchedSourceIds
            )
        }
        if (insight != null) {
            Spacer(Modifier.height(7.dp))
            TracksideActivityIntelligenceCard(
                insight,
                heading = "Detailed Analysis",
                tag = "activity-analysis-detailed",
                descriptor = character,
                collapsedLabel = "Show technical details",
                expandedLabel = "Hide technical details",
                technicalOnly = true
            )
        } else {
            ActivityAnalysisSection("Detailed Analysis", "activity-analysis-detailed") {
                Text("Detailed running analysis is unavailable for this activity.",
                    color = activityMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ActivityAnalysisSection(title: String, tag: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp).testTag(tag)
    ) {
        Text(
            title, color = activityText, fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun TracksideActivityFeedbackEditor(
    state: TrainingState,
    actions: PlanActions,
    matchedSourceIds: Set<String>
) {
    val target = remember(state.plans, matchedSourceIds) {
        findLinkedCompletedActivityRun(state.plans, matchedSourceIds)
    }
    if (target == null) {
        Text(
            "This activity is not linked to one completed StrideLab run. Link it to its workout in Overview to record feedback there.",
            color = activityMuted, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.testTag("activity-feedback-unavailable")
        )
        return
    }
    val (plan, session) = target
    val existing = session.coachingLog()
    var effort by rememberSaveable(plan.id, session.id, session.logRevision) {
        mutableStateOf(existing?.effort?.toString().orEmpty())
    }
    var difficulty by rememberSaveable(plan.id, session.id, session.logRevision) {
        mutableStateOf(existing?.completionDifficulty)
    }
    var discomfort by rememberSaveable(plan.id, session.id, session.logRevision) {
        mutableStateOf(existing?.discomfort)
    }
    val parsedEffort = effort.takeIf { it.isNotBlank() }?.toIntOrNull()
    val invalidEffort = effort.isNotBlank() && (parsedEffort == null || parsedEffort !in 1..10)
    Text(
        if (session.runResponse == null)
            "Your response is saved with this completed workout."
        else "Your response is saved with this completed workout and can be updated.",
        color = activityMuted, fontSize = 11.sp, lineHeight = 16.sp
    )
    OutlinedTextField(
        value = effort, onValueChange = { effort = it.filter(Char::isDigit).take(2) },
        label = { Text("Effort (1–10), optional") }, singleLine = true,
        isError = invalidEffort, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().testTag("activity-feedback-effort")
    )
    if (invalidEffort) Text("Enter effort from 1 to 10.", color = Color(0xFFFF8C87), fontSize = 11.sp)
    CoachFeedbackChoices("How did completion feel?", listOf("As planned", "Easier", "Harder", "Could not finish"),
        difficulty, enabled = !state.busy) { difficulty = it }
    CoachFeedbackChoices("Any discomfort?", listOf("None", "Soreness", "Pain"),
        discomfort, enabled = !state.busy) { discomfort = it }
    Button(
        onClick = {
            actions.saveRunResponse(
                plan.id, plan.revision, session.id, parsedEffort, difficulty, discomfort
            )
        },
        enabled = !state.busy && !invalidEffort,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("activity-feedback-save"),
        colors = ButtonDefaults.buttonColors(
            containerColor = activityLime, contentColor = activitySheetBlack
        )
    ) {
        Text(if (state.busy) "Saving…" else if (session.runResponse == null) "Save feedback" else "Update feedback")
    }
}

@Composable
private fun TracksideActivityOverviewBody(
    activity: ActivityRecord,
    recorded: ActivityDetail?,
    detailSource: ActivityRecord,
    matchedSourceIds: Set<String>,
    history: List<ActivityRecord>,
    state: TrainingState,
    actions: NativeActions,
    online: Boolean,
    onLoadGarminGps: (() -> Unit)?
) {
    val missingSavedCadence = remember(recorded) {
        recorded != null && recorded.samples.isNotEmpty() &&
            recorded.samples.none { it.cadenceSpm != null }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (DashboardSummary.isRun(activity) &&
            hasRunningPlanForActivityLink(state.plans)
        ) {
            ActivityPlanLink(state, activity, actions.planning, matchedSourceIds)
            Spacer(Modifier.height(15.dp))
        }
        TracksideActivitySummary(activity)
        if (activity.source == "garmin" && detailSource.id == activity.id &&
            (recorded == null || missingSavedCadence)) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (recorded == null)
                    "Garmin recording is not yet cached for offline analysis."
                else
                    "Cadence is not in this saved recording. Refresh to check if Garmin recorded it.",
                color = activityMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onLoadGarminGps?.invoke() },
                enabled = online && !state.busy && !state.needsSignIn && onLoadGarminGps != null,
                modifier = Modifier.fillMaxWidth().testTag("garmin-load-gps")
            ) {
                Text(when {
                    state.busy -> "Loading recording…"
                    recorded != null -> "Refresh Garmin cadence"
                    else -> "Load Garmin recording"
                })
            }
        } else if (detailSource.source != "garmin" && online && !state.busy && !state.needsSignIn) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { actions.loadDetail(detailSource.id) },
                modifier = Modifier.fillMaxWidth().testTag("activity-refresh-streams")
            ) {
                Text(if (recorded != null) "Refresh source data" else "Load recorded streams")
            }
        }
        if (activity.source == "garmin" && state.message?.contains("Garmin", ignoreCase = true) == true) {
            Text(state.message.orEmpty(), color = activityMuted, fontSize = 11.sp)
        }
    }
}


/**
 * Athlete Intelligence-inspired interaction with StrideLab's own styling.
 * Explain activity evidence in the same draggable sheet, with technical
 * details collapsed until requested.
 */
private data class ActivitySummaryMetric(
    val label: String,
    val value: String,
    val glyph: ActivitySummaryGlyph
)

private enum class ActivitySummaryGlyph {
    Distance, Duration, Pace, Heart, Elevation, MaximumHeartRate
}

@Composable
private fun TracksideActivitySummary(activity: ActivityRecord, heading: String = "Summary") {
    val metrics = buildList {
        if (activityHasDisplayDistance(activity)) {
            add(ActivitySummaryMetric("Distance", activity.distanceLabel, ActivitySummaryGlyph.Distance))
        }
        (activity.movingTimeS?.takeIf { it.isFinite() && it > 0.0 }
            ?: activityDisplayDurationSeconds(activity))?.let {
            add(ActivitySummaryMetric("Duration", ActivityRecord.duration(it), ActivitySummaryGlyph.Duration))
        }
        if (activity.supportsPace && activity.detailPaceLabel != "Not recorded") {
            add(ActivitySummaryMetric("Average pace", activity.detailPaceLabel, ActivitySummaryGlyph.Pace))
        }
        activity.averageHr?.let {
            add(ActivitySummaryMetric(
                "Average heart rate", it.roundToInt().toString() + " bpm", ActivitySummaryGlyph.Heart
            ))
        }
        activity.elevationM?.let {
            add(ActivitySummaryMetric(
                "Elevation gain", it.roundToInt().toString() + " m", ActivitySummaryGlyph.Elevation
            ))
        }
        activity.maximumHr?.let {
            add(ActivitySummaryMetric(
                "Maximum heart rate", it.roundToInt().toString() + " bpm",
                ActivitySummaryGlyph.MaximumHeartRate
            ))
        }
    }

    Column(
        Modifier.fillMaxWidth().testTag("activity-summary-redesign"),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            heading, color = activityText, fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 9.dp)
                .testTag("activity-summary-heading")
                .semantics { heading() }
        )
        metrics.chunked(2).forEachIndexed { index, pair ->
            if (index > 0) {
                HorizontalDivider(
                    color = activityBorder.copy(alpha = .48f),
                    thickness = .7.dp
                )
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp)
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pair.size > 1) {
                    SummaryMetricCell(pair[0], Modifier.weight(1f))
                    Box(
                        Modifier.width(1.dp).height(42.dp)
                            .background(activityBorder.copy(alpha = .62f))
                    )
                    SummaryMetricCell(pair[1], Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(.5f))
                    SummaryMetricCell(pair[0], Modifier.weight(1f))
                    Spacer(Modifier.weight(.5f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SummaryMetricCell(metric: ActivitySummaryMetric, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 4.dp).testTag("activity-summary-metric-${metric.label.lowercase().replace(' ', '-') }"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryMetricGlyph(metric.glyph)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                metric.label, color = activityMuted, fontSize = 10.sp,
                lineHeight = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                metric.value, color = activityText, fontSize = 15.sp,
                lineHeight = 19.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummaryMetricGlyph(glyph: ActivitySummaryGlyph) {
    Canvas(Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        when (glyph) {
            ActivitySummaryGlyph.Distance -> {
                drawCircle(activityLime, w * .17f, Offset(w * .5f, h * .42f), style = Stroke(sw))
                val pin = Path().apply {
                    moveTo(w * .5f, h * .94f)
                    cubicTo(w * .28f, h * .67f, w * .18f, h * .52f, w * .18f, h * .38f)
                    cubicTo(w * .18f, h * .18f, w * .32f, h * .08f, w * .5f, h * .08f)
                    cubicTo(w * .68f, h * .08f, w * .82f, h * .18f, w * .82f, h * .38f)
                    cubicTo(w * .82f, h * .52f, w * .72f, h * .67f, w * .5f, h * .94f)
                }
                drawPath(pin, activityLime, style = Stroke(sw, cap = StrokeCap.Round))
            }
            ActivitySummaryGlyph.Duration -> {
                drawCircle(activityLime, w * .36f, Offset(w * .5f, h * .57f), style = Stroke(sw))
                drawLine(activityLime, Offset(w * .5f, h * .05f), Offset(w * .5f, h * .20f), sw, StrokeCap.Round)
                drawLine(activityLime, Offset(w * .35f, h * .05f), Offset(w * .65f, h * .05f), sw, StrokeCap.Round)
                drawLine(activityLime, Offset(w * .5f, h * .57f), Offset(w * .5f, h * .35f), sw, StrokeCap.Round)
            }
            ActivitySummaryGlyph.Pace -> {
                drawArc(activityLime, 190f, 160f, false,
                    topLeft = Offset(w * .10f, h * .12f),
                    size = androidx.compose.ui.geometry.Size(w * .80f, h * .80f),
                    style = Stroke(sw, cap = StrokeCap.Round))
                drawLine(activityLime, Offset(w * .5f, h * .56f), Offset(w * .77f, h * .35f), sw, StrokeCap.Round)
            }
            ActivitySummaryGlyph.Heart -> {
                val p = Path().apply {
                    moveTo(w * .5f, h * .88f)
                    cubicTo(w * .26f, h * .72f, w * .10f, h * .50f, w * .16f, h * .30f)
                    cubicTo(w * .23f, h * .10f, w * .43f, h * .14f, w * .5f, h * .31f)
                    cubicTo(w * .57f, h * .14f, w * .77f, h * .10f, w * .84f, h * .30f)
                    cubicTo(w * .90f, h * .50f, w * .74f, h * .72f, w * .5f, h * .88f)
                }
                drawPath(p, activityLime, style = Stroke(sw, cap = StrokeCap.Round))
            }
            ActivitySummaryGlyph.Elevation -> {
                val p = Path().apply {
                    moveTo(w * .08f, h * .82f)
                    lineTo(w * .36f, h * .42f)
                    lineTo(w * .52f, h * .61f)
                    lineTo(w * .69f, h * .31f)
                    lineTo(w * .94f, h * .82f)
                    close()
                }
                drawPath(p, activityLime, style = Stroke(sw, cap = StrokeCap.Round))
            }
            ActivitySummaryGlyph.MaximumHeartRate -> {
                val p = Path().apply {
                    moveTo(w * .05f, h * .55f)
                    lineTo(w * .25f, h * .55f)
                    lineTo(w * .35f, h * .32f)
                    lineTo(w * .46f, h * .76f)
                    lineTo(w * .58f, h * .43f)
                    lineTo(w * .68f, h * .55f)
                    lineTo(w * .95f, h * .55f)
                }
                drawPath(p, activityLime, style = Stroke(sw, cap = StrokeCap.Round))
                drawCircle(activityLime, w * .43f, Offset(w * .5f, h * .53f), style = Stroke(sw))
            }
        }
    }
}

@Composable
private fun TracksideActivityIntelligenceCard(
    insight: ActivityInsightPresentation,
    heading: String = "Overall Analysis",
    tag: String = "activity-insights",
    descriptor: String? = null,
    collapsedLabel: String = "See analysis",
    expandedLabel: String = "Show less",
    technicalOnly: Boolean = false
) {
    var expanded by remember(insight.headline) { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF151F1A), shape)
            .border(1.dp, Color(0xFF33463B), shape)
            .testTag(tag)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            heading,
            color = activityText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        if (!technicalOnly || expanded) {
            if (!descriptor.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    descriptor,
                    color = activityLime,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag(tag + "-descriptor")
                )
                Spacer(Modifier.height(9.dp))
            } else {
                Spacer(Modifier.height(13.dp))
            }
            Text(
                insight.headline,
                color = activityText,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                modifier = Modifier.testTag(tag + "-headline")
            )
            Spacer(Modifier.height(8.dp))
            Text(
                insight.summary,
                color = Color(0xFFCBD7CF),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.testTag(tag + "-summary")
            )
        }
        if (expanded) {
            Spacer(Modifier.height(15.dp))
            HorizontalDivider(color = activityBorder, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))
            Text("IN THE DATA", color = activityLime,
                fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            insight.evidence.forEachIndexed { index, line ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier.padding(top = 7.dp).size(5.dp)
                            .background(activityLime, RoundedCornerShape(5.dp))
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        line,
                        color = activityMuted, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.weight(1f)
                            .testTag(tag + "-detail-" + index)
                    )
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                "These are observations from saved activity data, not a fitness, " +
                    "recovery or training-readiness assessment.",
                color = activityMuted.copy(alpha = .8f),
                fontSize = 10.sp, lineHeight = 14.sp
            )
            Spacer(Modifier.height(11.dp))
        } else {
            Spacer(Modifier.height(15.dp))
        }
        Button(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .testTag(tag + "-toggle"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = activityLime,
                contentColor = activitySheetBlack
            ),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            Text(if (expanded) expandedLabel else collapsedLabel,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TracksideActivitySplitRow(
    index: Int, lap: ActivityLap, hasHr: Boolean,
    paceBounds: Pair<Double?, Double?>, supportsPace: Boolean,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null
) {
    val recordedPace = if (supportsPace) lap.pace?.takeIf { it.isFinite() && it > 0 } else null
    val fastest = paceBounds.first
    val slowest = paceBounds.second
    val meaningfulSpread = fastest != null && slowest != null && slowest - fastest >= 1.0
    val isFastest = meaningfulSpread && recordedPace != null &&
        fastest != null && abs(recordedPace - fastest) < .5
    val isSlowest = meaningfulSpread && recordedPace != null &&
        slowest != null && abs(recordedPace - slowest) < .5
    val splitAccent = when {
        isFastest -> activityLime
        isSlowest -> Color(0xFFFF8C87)
        else -> activityMuted
    }
    val rowBackground = when {
        isFastest -> activityLime.copy(alpha = .055f)
        isSlowest -> Color(0xFFFF8C87).copy(alpha = .045f)
        else -> Color.Transparent
    }
    val fraction = if (recordedPace != null && slowest != null && fastest != null) {
        (.35f + .65f * ((slowest - recordedPace) / (slowest - fastest).coerceAtLeast(1.0)).toFloat())
            .coerceIn(.35f, 1f)
    } else 0f

    Row(
        Modifier.fillMaxWidth().heightIn(min = 27.dp)
            .background(
                if (selected) activityLime.copy(alpha = .10f) else rowBackground,
                RoundedCornerShape(7.dp)
            )
            .then(
                if (onSelect != null) Modifier.clickable(
                    role = Role.Button,
                    onClick = onSelect
                ) else Modifier
            )
            .testTag("activity-split-" + index)
            .semantics {
                contentDescription = "Lap " + (index + 1) +
                    (if (isFastest) ", fastest split" else if (isSlowest) ", slowest split" else "") +
                    (if (selected) ", selected on map" else "") +
                    ", distance " + (lap.distance?.let {
                        String.format(Locale.getDefault(), "%.2f km", it / 1000.0)
                    } ?: "not recorded") +
                    ", pace " + (recordedPace?.let { ActivityRecord.duration(it) + " per km" }
                        ?: "not recorded") +
                    (if (hasHr) ", heart rate " +
                        (lap.hr?.roundToInt()?.toString()?.plus(" bpm") ?: "not recorded") else "")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.width(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier.size(4.dp)
                    .background(
                        if (isFastest || isSlowest) splitAccent else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
            )
            Text(
                (index + 1).toString(),
                color = if (isFastest || isSlowest) splitAccent else activityMuted,
                fontSize = 11.sp, maxLines = 1
            )
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                recordedPace?.let { ActivityRecord.duration(it) } ?: "—",
                color = activityText, fontSize = 12.sp,
                modifier = Modifier.width(49.dp), maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(Modifier.weight(1f).height(4.dp)
                .background(Color(0xFF2A3530), RoundedCornerShape(3.dp))) {
                if (recordedPace != null) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .background(
                            if (isSlowest) Color(0xFFFF8C87) else activityLime,
                            RoundedCornerShape(3.dp)
                        ))
                }
            }
        }
        if (hasHr) {
            Text(lap.hr?.roundToInt()?.toString() ?: "—",
                color = activityMuted, fontSize = 11.sp,
                modifier = Modifier.width(33.dp), maxLines = 1)
        }
    }
}

/**
 * Heart-rate zones use the athlete-level calculation method saved in Preferences.
 * A workout peak is never substituted for missing athlete settings.
 */
@Composable
private fun TracksideActivityHeartRateZones(
    recorded: ActivityDetail, owner: String?, manualOwner: String? = owner
) {
    val context = LocalContext.current
    val preferences = remember(context) { AthleteHeartRatePreferences(context) }
    val settings = remember(recorded.activityId, manualOwner) {
        preferences.settings(manualOwner)
    }
    val zoneStore = remember(context) { GarminZonePreferenceStore(context) }
    val selectedSource = remember(recorded.activityId, owner) { zoneStore.source(owner) }
    val importedZones = remember(recorded.activityId, owner) { zoneStore.load(owner) }
    val useGarmin = selectedSource == DisplayHeartRateZoneSource.GARMIN
    val sourceZones = recorded.sourceHeartRateZones
    val zones = remember(recorded, settings, selectedSource, importedZones) {
        if (useGarmin) importedZones?.let {
            HeartRateZoneAnalysis.distribute(recorded.samples, it.profile)
        } else HeartRateZoneAnalysis.distribute(recorded.samples, settings)
    }
    val colors = listOf(
        Color(0xFF9FABBA), Color(0xFF79C9B3), activityLime,
        Color(0xFFFFBE7A), Color(0xFFFF8C87)
    )

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .background(activitySurface, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 13.dp)
                .testTag("activity-chart-heart-rate-zones")
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Heart Rate Zones", color = activityText,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (useGarmin && importedZones != null)
                            "Garmin Connect BPM floors · cached for offline display"
                        else if (useGarmin)
                            "Sync Garmin running zones in More > App preferences"
                        else if (settings.ready)
                            "Calculated from your saved manual athlete settings"
                        else
                            "Configure " + settings.method.displayName +
                                " in More > App preferences",
                        color = activityMuted, fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (useGarmin) "GARMIN" else settings.method.displayName,
                    color = activityLime, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("activity-hr-zone-method")
                )
            }
            if (sourceZones != null) {
                Spacer(Modifier.height(8.dp))
                val sourceSummary = buildList {
                    sourceZones.maximumHr?.let { add("$it max") }
                    sourceZones.thresholdHr?.let { add("$it LTHR") }
                    sourceZones.restingHr?.let { add("$it resting") }
                    sourceZones.calculationType?.let { add(it.replace('_', ' ')) }
                }.joinToString(" · ")
                if (sourceSummary.isNotBlank()) {
                    Text(
                        "Garmin FIT recorded · " + sourceSummary,
                        color = Color(0xFF79C9B3), fontSize = 9.5.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.testTag("activity-hr-zone-fit-source")
                    )
                }
                if (sourceZones.highBpms.size >= 5) {
                    Text(
                        sourceZones.highBpms.take(5).mapIndexed { index, bpm ->
                            "Z${index + 1} ≤$bpm"
                        }.joinToString(" · ") + " bpm",
                        color = activityMuted, fontSize = 9.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.testTag("activity-hr-zone-fit-boundaries")
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            if (zones == null) {
                Text(
                    if (useGarmin && importedZones == null)
                        "Garmin running boundaries have not been synced. Select Manual zones or sync Garmin in App preferences."
                    else if (!useGarmin && !settings.ready)
                        when (settings.method) {
                            HeartRateZoneMethod.MAX_BPM ->
                                "Save your maximum heart rate to calculate zones."
                            HeartRateZoneMethod.LTHR_PERCENT ->
                                "Save your lactate-threshold heart rate to calculate zones."
                            HeartRateZoneMethod.HRR_PERCENT ->
                                "Save both maximum and resting heart rate to calculate HRR zones."
                        }
                    else
                        "Not enough consecutive recorded HR samples to estimate time in zones.",
                    color = activityMuted, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.testTag("activity-hr-zone-help")
                )
            } else {
                zones.buckets.forEachIndexed { index, zone ->
                    val range = when {
                        zone.lowerBpm == null -> "Below ${zone.upperBpm!! + 1} bpm"
                        zone.upperBpm == null -> "${zone.lowerBpm}+ bpm"
                        else -> "${zone.lowerBpm}–${zone.upperBpm} bpm"
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("activity-hr-zone-${zone.zone}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text(
                            "Z${zone.zone}", color = colors[index],
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(22.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(range, color = activityMuted, fontSize = 10.sp)
                                Text(
                                    "${ActivityRecord.duration(zone.seconds)} · " +
                                        "${(zone.fraction * 100).roundToInt()}%",
                                    color = activityText, fontSize = 11.sp
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Box(
                                Modifier.fillMaxWidth().height(6.dp)
                                    .background(activityBorder, RoundedCornerShape(4.dp))
                            ) {
                                if (zone.seconds > 0.0) {
                                    Box(
                                        Modifier.fillMaxWidth(zone.fraction.toFloat())
                                            .fillMaxHeight()
                                            .background(colors[index], RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    (if (useGarmin)
                        "Garmin Connect ${importedZones?.profile?.sport ?: "unavailable"} BPM zones. "
                    else
                        HeartRateZoneAnalysis.methodSummary(settings) +
                            ". These are StrideLab estimates, not imported Garmin zones. ") +
                        "Percentage of classified recorded time, not total activity duration. " +
                        "Gaps over 30 seconds are excluded.",
                    color = activityMuted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }
        }
        if (zones != null) {
            Spacer(Modifier.height(10.dp))
            TracksideActivityIntelligenceCard(
                HeartRateZoneAnalysis.presentation(zones),
                "Heart Rate Zones Analysis", "activity-chart-insights-heart-rate-zones"
            )
        }
    }
}

@Composable
private fun TracksideActivitySourceChart(
    title: String,
    unit: String,
    samples: List<ActivitySample>,
    metric: (ActivitySample) -> Double?,
    pace: Boolean = false,
    selectedSeconds: Double? = null,
    onSelectSeconds: (Double) -> Unit = {},
    pauseIntervals: List<ActivityPauseInterval> = emptyList()
) {
    // The x-axis uses moving time: timer pauses have zero horizontal width.
    // Plot only actual recorded measurements outside paused intervals, joining
    // successive available points without fabricating readings or altering FIT.
    val movingAxis = remember(samples, pauseIntervals) {
        ActivityChartMovingTimeAxis.from(samples, pauseIntervals)
    }
    val validSamples = remember(samples, pauseIntervals, title) {
        samples.mapNotNull { sample ->
            if (movingAxis.isPaused(sample.seconds)) null
            else metric(sample)?.takeIf { it.isFinite() }?.let { sample to it }
        }
    }
    // Keep rendering bounded while preserving genuine endpoints and full-stream
    // statistics. A missing metric does not introduce an artificial line break.
    val renderedPoints = remember(validSamples) {
        val stride = ((validSamples.size + 449) / 450).coerceAtLeast(1)
        validSamples.filterIndexed { index, _ ->
            index % stride == 0 || index == validSamples.lastIndex
        }.map { it.first }
    }
    if (validSamples.size < 2 || samples.isEmpty()) return
    // A shared chart cursor can update frequently while dragging. Cache
    // full-stream statistics rather than scanning 20,000 FIT points on each
    // touch event or each LazyColumn recomposition.
    val stats = remember(validSamples) {
        Triple(
            validSamples.minOf { it.second },
            validSamples.maxOf { it.second },
            validSamples.sumOf { it.second } / validSamples.size
        )
    }
    val (low, high, sampleAverage) = stats
    val displayScale = remember(validSamples, title) {
        ActivityChartDisplayScale.from(title, validSamples.map { it.second })
            ?: ActivityChartDisplayScale(low, high, low, high)
    }
    val displayLow = displayScale.displayLow
    val displayHigh = displayScale.displayHigh
    val chartInsight = remember(validSamples, title) {
        ActivityDetailSectionAnalysis.chart(title, validSamples)
    }
    // All points may belong to a paused interval; in that case no moving
    // chart exists, even though the original FIT samples remain available.
    if (movingAxis.durationSeconds <= 0.0) return
    val displayedOutliers = remember(renderedPoints, displayScale, title) {
        renderedPoints.filter { sample ->
            metric(sample)?.let(displayScale::isOutside) == true
        }
    }
    val selectedPoint = remember(selectedSeconds, renderedPoints, movingAxis, title) {
        selectedSeconds?.let { at ->
            val movingAt = movingAxis.position(at)
            renderedPoints.minByOrNull { abs(movingAxis.position(it.seconds) - movingAt) }
                ?.takeIf { abs(movingAxis.position(it.seconds) - movingAt) <= 60.0 }
        }
    }
    val selectedValue = selectedPoint?.let(metric)
    fun valueText(value: Double): String = if (pace) {
        ActivityRecord.duration(value) + "/km"
    } else String.format(Locale.getDefault(), "%.0f %s", value, unit)
    fun axisText(value: Double): String = if (pace) ActivityRecord.duration(value)
        else value.roundToInt().toString()
    var chartWidthPx by remember(samples, title) { mutableFloatStateOf(0f) }
    fun selectAt(x: Float) {
        if(chartWidthPx <= 0f) return
        val fraction = (x / chartWidthPx).coerceIn(0f, 1f)
        val target = movingAxis.durationSeconds * fraction
        // Select a REAL recorded sample on the moving timeline. The callback
        // still receives its original elapsed timestamp for cross-chart sync.
        val nearest = renderedPoints.minByOrNull {
            abs(movingAxis.position(it.seconds) - target)
        }
        if(nearest != null) onSelectSeconds(nearest.seconds)
    }
    val lineColor = when(title) {
        "Heart rate" -> Color(0xFFFF8C87)
        "Elevation" -> Color(0xFF9EC8D7)
        "Cadence" -> Color(0xFFC2A8FF)
        else -> activityLime
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        // The chart and its branded analysis are sibling surfaces. Expanding
        // the analysis must not enlarge the plot card or change its gestures.
        Column(
            Modifier.fillMaxWidth()
                .background(activitySurface, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp)
                .testTag("activity-chart-" + title.lowercase().replace(" ", "-"))
        ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = activityText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(unit, color = activityMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Avg: " + valueText(sampleAverage) +
                "    Range: " + valueText(low) + "–" + valueText(high),
            color = activityMuted, fontSize = 10.sp, maxLines = 2,
            modifier = Modifier.testTag("activity-chart-summary-" + title.lowercase().replace(" ", "-"))
        )
        if (displayScale.trimmed) {
            Spacer(Modifier.height(3.dp))
            Text(
                "Scale focuses on typical recorded values; extreme points remain inspectable.",
                color = activityMuted.copy(alpha = .82f),
                fontSize = 9.sp, lineHeight = 12.sp,
                modifier = Modifier.testTag(
                    "activity-chart-trim-note-" +
                        title.lowercase(Locale.ROOT).replace(" ", "-")
                )
            )
        }
        if (selectedSeconds != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                ActivityRecord.duration(movingAxis.position(selectedSeconds)) +
                    " moving · " +
                    (selectedValue?.let { valueText(it) } ?: "No measurement at this point"),
                color = lineColor, fontSize = 11.sp,
                modifier = Modifier.testTag("activity-chart-cursor-" + title.lowercase().replace(" ", "-"))
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(43.dp).height(124.dp)
                    .testTag("activity-chart-y-axis-" + title.lowercase(Locale.ROOT).replace(" ", "-")),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val top = if (pace) displayLow else displayHigh
                val bottom = if (pace) displayHigh else displayLow
                listOf(top, (displayLow + displayHigh) / 2.0, bottom).forEach { value ->
                    Text(
                        axisText(value), color = activityMuted, fontSize = 10.sp,
                        maxLines = 1, textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.width(5.dp))
        Canvas(
            Modifier.weight(1f).height(124.dp)
                .onSizeChanged { chartWidthPx = it.width.toFloat() }
                .pointerInput(samples,title) {
                    detectTapGestures(onTap = { selectAt(it.x) })
                }
                .pointerInput(samples,title) {
                    // Horizontal scrubbing must not steal vertical gestures
                    // from the parent LazyColumn / draggable details sheet.
                    detectHorizontalDragGestures(
                        onDragStart = { selectAt(it.x) },
                        onHorizontalDrag = { change, _ ->
                            selectAt(change.position.x)
                            change.consume()
                        }
                    )
                }
                .testTag("stream-" + title)
                .semantics {
                    contentDescription = title + " recorded chart, " +
                        validSamples.size + " moving-time measurements. " +
                        "Average " + valueText(sampleAverage) +
                        ". Drag to inspect recorded values."
                }
        ) {
            val span = (displayHigh - displayLow).coerceAtLeast(1.0)
            fun position(sample: ActivitySample, value: Double): Offset {
                val x = (movingAxis.position(sample.seconds) / movingAxis.durationSeconds * size.width).toFloat()
                val displayed = value.coerceIn(displayLow, displayHigh)
                val norm = ((displayed - displayLow) / span).toFloat().coerceIn(0f, 1f)
                val y = if (pace) norm * (size.height - 8f) + 4f
                    else (1f - norm) * (size.height - 8f) + 4f
                return Offset(x, y)
            }
            for (step in 1..3) {
                val y = size.height * step / 4f
                drawLine(activityMuted.copy(alpha = .18f),
                    Offset(0f, y), Offset(size.width, y), 1f)
            }
            val displayAverage = sampleAverage.coerceIn(displayLow, displayHigh)
            val avgY = if(pace) {
                ((displayAverage - displayLow) / span).toFloat() * (size.height-8f) + 4f
            } else {
                (1f - ((displayAverage - displayLow) / span).toFloat()) *
                    (size.height-8f) + 4f
            }
            drawLine(
                activityLime.copy(alpha = .75f),
                Offset(0f, avgY), Offset(size.width, avgY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                )
            )
            renderedPoints.zipWithNext().forEach { (a, b) ->
                val av = metric(a)
                val bv = metric(b)
                if (av != null && bv != null) {
                    drawLine(
                        lineColor, position(a, av), position(b, bv),
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
                    )
                }
            }
            // Highlight clipped real outliers without breaking the line.
            displayedOutliers.forEach { sample ->
                val value = metric(sample)
                if (value != null) {
                    drawCircle(
                        lineColor.copy(alpha = .36f),
                        2.2.dp.toPx(),
                        position(sample, value)
                    )
                }
            }
            if(selectedPoint != null && selectedValue != null) {
                val center=position(selectedPoint,selectedValue)
                drawLine(activityText.copy(alpha = .5f),
                    Offset(center.x,0f),Offset(center.x,size.height),1.dp.toPx())
                drawCircle(lineColor,5.dp.toPx(),center)
                drawCircle(activitySheetBlack,2.dp.toPx(),center)
            }
        }
        }
        Row(Modifier.fillMaxWidth().padding(start = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00", color = activityMuted, fontSize = 10.sp)
            Text(
                ActivityRecord.duration(movingAxis.durationSeconds),
                color = activityMuted, fontSize = 10.sp,
                modifier = Modifier.testTag(
                    "activity-chart-moving-axis-" + title.lowercase(Locale.ROOT).replace(" ", "-")
                )
            )
        }
        }
        if (chartInsight != null) {
            Spacer(Modifier.height(10.dp))
            TracksideActivityIntelligenceCard(
                chartInsight, "$title Analysis",
                "activity-chart-insights-" + title.lowercase(Locale.ROOT).replace(" ", "-")
            )
        }
    }
}

/** Existing source-only heart rate chart reused by stationary activity details. */
@Composable
internal fun TracksideStationaryHeartRateCharts(
    recorded: ActivityDetail,
    owner: String?
) {
    val count = remember(recorded) {
        recorded.samples.count { it.hr?.isFinite() == true && it.hr > 0.0 }
    }
    if (count < 2) return
    TracksideActivitySourceChart(
        "Heart rate", "bpm", recorded.samples, { it.hr },
        pauseIntervals = recorded.pauseIntervals
    )
    TracksideActivityHeartRateZones(recorded, owner)
}
