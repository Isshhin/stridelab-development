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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        add(ActivityMetricItem("DISTANCE", activity.distanceM?.let {
            String.format(Locale.getDefault(), "%.2f", it / 1000.0)
        } ?: "—", if (activity.distanceM != null) "km" else "", ActivityMetricGlyph.Route))
        // Existing moving-time measurement is intentionally unchanged.
        add(ActivityMetricItem("DURATION",
            activity.movingTimeS?.let { ActivityRecord.duration(it) } ?: "—",
            "", ActivityMetricGlyph.Timer))
        add(ActivityMetricItem("AVG PACE",
            if (activity.supportsPace && activity.detailPaceLabel != "Not recorded")
                activity.detailPaceLabel.removeSuffix(" /km").trim() else "—",
            if (activity.supportsPace && activity.detailPaceLabel != "Not recorded") "/km" else "",
            ActivityMetricGlyph.Shoe))
        if (recordedHr != null) add(ActivityMetricItem("AVG HR",
            recordedHr.roundToInt().toString(), "bpm", ActivityMetricGlyph.Heart))
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("activity-primary-metrics")) {
        // Keep four metrics on one row at normal phone width; never clip
        // duration or HR when the user has increased the system font scale.
        val stacked = metrics.size == 4 &&
            (maxWidth < 305.dp || LocalDensity.current.fontScale > 1.2f)
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { item ->
                            PrimaryActivityMetric(item.label, item.value, item.unit,
                                item.glyph, Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (metrics.size == 4) 4.dp else 6.dp)) {
                metrics.forEach { item ->
                    PrimaryActivityMetric(item.label, item.value, item.unit,
                        item.glyph, Modifier.weight(1f), compact = metrics.size == 4)
                }
            }
        }
    }
}

private enum class ActivityMetricGlyph { Route, Timer, Shoe, Heart }

@Composable
private fun PrimaryActivityMetric(
    label: String, value: String, unit: String,
    symbol: ActivityMetricGlyph, modifier: Modifier = Modifier, compact: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(23.dp)) {
            val w = size.width
            val h = size.height
            val sw = 2.3.dp.toPx()
            when (symbol) {
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
                ActivityMetricGlyph.Timer -> {
                    drawCircle(activityLime, radius = w * .33f,
                        center = Offset(w * .5f, h * .57f), style = Stroke(sw))
                    drawLine(activityLime, Offset(w * .5f, h * .09f),
                        Offset(w * .5f, h * .24f), sw, StrokeCap.Round)
                    drawLine(activityLime, Offset(w * .36f, h * .08f),
                        Offset(w * .64f, h * .08f), sw, StrokeCap.Round)
                    drawLine(activityLime, Offset(w * .5f, h * .57f),
                        Offset(w * .5f, h * .39f), sw, StrokeCap.Round)
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
            lineHeight = 12.sp, maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                value, color = activityText, fontSize = if (compact) 16.sp else 19.sp,
                lineHeight = if (compact) 20.sp else 23.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.weight(1f, fill = false)
            )
            if (unit.isNotEmpty()) Text(
                unit, color = activityMuted, fontSize = 10.5.sp, lineHeight = 19.sp,
                maxLines = 1
            )
        }
    }
}

/** Compact header used only after the user expands the details sheet. */
@Composable
internal fun TracksideActivityExpandedHeader(activity: ActivityRecord, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .heightIn(min = 51.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(42.dp).testTag("activity-expanded-back")
        ) { Text("‹", fontSize = 28.sp, color = activityText) }
        Box(
            Modifier.size(32.dp).background(Color(0xFF1D2B20), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Text("↗", color = activityLime, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f)) {
            Text(activityDisplayName(activity.title), color = activityText, fontSize = 16.sp,
                lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(activity.dateLabel, color = activityMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

/**
 * One continuous lazy list. The segmented rail never replaces the body.
 * Only changes in first visible section update the rail, not every scroll pixel.
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
    onLoadGarminGps: (() -> Unit)?
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
    var selectedChartSeconds by remember(recorded) { mutableStateOf<Double?>(null) }
    val splitStartIndex = 1
    val chartsStartIndex = 2 + laps.size
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
                anchor >= chartsStartIndex -> 2
                anchor >= splitStartIndex -> 1
                else -> 0
            }
        }
    }
    var pendingSection by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(currentSection, pendingSection) {
        if (pendingSection == null && selectedTab != currentSection) onTab(currentSection)
    }
    val visibleSection = pendingSection ?: currentSection
    val sectionOffset = listOf(0, splitStartIndex, chartsStartIndex)
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
        val viewportHeight = maxHeight
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
            item(key = "section-splits") {
                Column(Modifier.fillMaxWidth().testTag("activity-section-splits")) {
                    ActivitySectionHeading("Splits")
                    Spacer(Modifier.height(5.dp))
                    if (laps.isEmpty()) {
                        Text(
                            "No recorded splits are available.",
                            color = activityMuted, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("LAP", color = activityMuted, fontSize = 10.sp,
                                modifier = Modifier.width(26.dp))
                            Text("KM", color = activityMuted, fontSize = 10.sp,
                                modifier = Modifier.width(40.dp))
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
                TracksideActivitySplitRow(index, lap, hasSplitHr, paceBounds, activity.supportsPace)
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
                            selectedSeconds = selectedChartSeconds,
                            onSelectSeconds = { selectedChartSeconds = it }
                        )
                    }
                if (chartAvailability.heartRate)
                    item(key = "chart-heart-rate") {
                        TracksideActivitySourceChart(
                            "Heart rate", "bpm", recorded.samples, { it.hr },
                            selectedSeconds = selectedChartSeconds,
                            onSelectSeconds = { selectedChartSeconds = it }
                        )
                    }
                if (chartAvailability.elevation)
                    item(key = "chart-elevation") {
                        TracksideActivitySourceChart(
                            "Elevation", "m", recorded.samples, { it.altitude },
                            selectedSeconds = selectedChartSeconds,
                            onSelectSeconds = { selectedChartSeconds = it }
                        )
                    }
                if (chartAvailability.cadence)
                    item(key = "chart-cadence") {
                        TracksideActivitySourceChart(
                            "Cadence", "spm", recorded.samples, { it.cadenceSpm },
                            selectedSeconds = selectedChartSeconds,
                            onSelectSeconds = { selectedChartSeconds = it }
                        )
                    }
            }
            // Give short offline activities enough scroll range for Charts to
            // reach the sticky selection threshold without inventing rows.
            item(key = "section-ending-space") {
                Spacer(Modifier.height(viewportHeight))
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
        val segment = maxWidth / 3
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
            listOf("Overview", "Splits", "Charts").forEachIndexed { index, name ->
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
                        name, color = color, fontSize = 12.sp,
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
        modifier = Modifier.padding(top = 13.dp).semantics { heading() }
    )
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
    val insightPresentation = remember(activity, recorded, history) {
        ActivityIntelligence.presentation(activity, recorded, history)
    }
    val missingSavedCadence = remember(recorded) {
        recorded != null && recorded.samples.isNotEmpty() &&
            recorded.samples.none { it.cadenceSpm != null }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (DashboardSummary.isRun(activity)) {
            ActivityPlanLink(state, activity, actions.planning, matchedSourceIds)
            Spacer(Modifier.height(15.dp))
        }
        ActivitySectionHeading("Summary")
        Spacer(Modifier.height(8.dp))
        ActivityInfoLine("Distance", activity.distanceM?.let { activity.distanceLabel })
        ActivityInfoLine("Duration", activity.movingTimeS?.let { ActivityRecord.duration(it) })
        ActivityInfoLine("Average pace",
            if (activity.supportsPace && activity.detailPaceLabel != "Not recorded")
                activity.detailPaceLabel else null)
        ActivityInfoLine("Elapsed time", activity.durationS?.let { ActivityRecord.duration(it) })
        ActivityInfoLine("Average heart rate",
            activity.averageHr?.let { it.roundToInt().toString() + " bpm" })
        ActivityInfoLine("Elevation gain",
            activity.elevationM?.let { it.roundToInt().toString() + " m" })
        ActivityInfoLine("Maximum heart rate",
            activity.maximumHr?.let { it.roundToInt().toString() + " bpm" })
        Spacer(Modifier.height(12.dp))
        Text("Recorded by " + activity.sourceLabel,
            color = activityMuted, fontSize = 11.sp)
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
        // Strava-style presentation rhythm: headline first, evidence on demand.
        // This is still deterministic recorded-data analysis, NOT a generative
        // AI coach or a recovery/fitness prediction.
        if (insightPresentation != null) {
            Spacer(Modifier.height(16.dp))
            TracksideActivityIntelligenceCard(insightPresentation)
        }
    }
}

/**
 * Athlete Intelligence-inspired interaction with StrideLab's own styling.
 * Short verified highlight after the activity stats; "See analysis" expands
 * in the same sheet rather than nesting another draggable bottom sheet.
 */
@Composable
private fun TracksideActivityIntelligenceCard(insight: ActivityInsightPresentation) {
    var expanded by remember(insight.headline) { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF151F1A), shape)
            .border(1.dp, Color(0xFF33463B), shape)
            .testTag("activity-insights")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp)
                    .background(activityLime, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = activitySheetBlack,
                    fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(9.dp))
            Text("StrideLab Insights", color = activityText,
                fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("RECORDED DATA", color = activityMuted, fontSize = 9.sp)
        }
        Spacer(Modifier.height(13.dp))
        Text(
            insight.headline,
            color = activityText,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            lineHeight = 24.sp,
            modifier = Modifier.testTag("activity-insights-headline")
        )
        Spacer(Modifier.height(8.dp))
        Text(
            insight.summary,
            color = Color(0xFFCBD7CF),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.testTag("activity-insights-summary")
        )
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
                            .testTag("activity-insights-detail-" + index)
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
                .testTag("activity-insights-toggle"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = activityLime,
                contentColor = activitySheetBlack
            ),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            Text(if (expanded) "Show less" else "See analysis",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun ActivityInfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier.fillMaxWidth().heightIn(min = 47.dp)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = activityMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = activityText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
    HorizontalDivider(color = activityBorder.copy(alpha = .55f), thickness = .6.dp)
}

@Composable
private fun TracksideActivitySplitRow(
    index: Int, lap: ActivityLap, hasHr: Boolean,
    paceBounds: Pair<Double?, Double?>, supportsPace: Boolean
) {
    val recordedPace = if (supportsPace) lap.pace?.takeIf { it.isFinite() && it > 0 } else null
    val fastest = paceBounds.first
    val slowest = paceBounds.second
    val fraction = if (recordedPace != null && slowest != null && fastest != null) {
        (.35f + .65f * ((slowest - recordedPace) / (slowest - fastest).coerceAtLeast(1.0)).toFloat())
            .coerceIn(.35f, 1f)
    } else 0f

    Row(
        Modifier.fillMaxWidth().heightIn(min = 30.dp)
            .testTag("activity-split-" + index)
            .semantics {
                contentDescription = "Lap " + (index + 1) +
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
        Text((index + 1).toString(), color = activityMuted, fontSize = 11.sp,
            modifier = Modifier.width(26.dp), maxLines = 1)
        Text(
            lap.distance?.let { String.format(Locale.getDefault(), "%.2f", it / 1000.0) } ?: "—",
            color = activityMuted, fontSize = 11.sp,
            modifier = Modifier.width(40.dp), maxLines = 1
        )
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
                        .background(activityLime, RoundedCornerShape(3.dp)))
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

@Composable
private fun TracksideActivitySourceChart(
    title: String,
    unit: String,
    samples: List<ActivitySample>,
    metric: (ActivitySample) -> Double?,
    pace: Boolean = false,
    selectedSeconds: Double? = null,
    onSelectSeconds: (Double) -> Unit = {}
) {
    // Segment by missing source values and timestamp gaps before drawing.
    // A line never bridges a pause or interpolates an unrecorded measurement.
    val segments = remember(samples, title) {
        val result = mutableListOf<List<ActivitySample>>()
        var current = mutableListOf<ActivitySample>()
        var previous: ActivitySample? = null
        fun flush() {
            if(current.isNotEmpty()) result.add(current)
            current = mutableListOf()
            previous = null
        }
        samples.forEach { sample ->
            val value = metric(sample)
            if (value == null || !value.isFinite() ||
                (previous != null && sample.seconds - previous!!.seconds > 120.0)
            ) flush()
            if(value != null && value.isFinite()) {
                current.add(sample)
                previous = sample
            }
        }
        flush()
        // Bound rendering and drag lookup to ~450 points, but compute summary
        // values from ALL actual samples rather than the downsampled display.
        val step = (samples.size / 450).coerceAtLeast(1)
        result.map { section ->
            section.filterIndexed { i, _ -> i % step == 0 || i == section.lastIndex }
        }
    }
    val validSamples = remember(samples, title) {
        samples.mapNotNull { sample ->
            metric(sample)?.takeIf { it.isFinite() }?.let { sample to it }
        }
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
    val firstTime = samples.first().seconds
    val lastTime = samples.last().seconds
    if (lastTime <= firstTime) return
    val renderedPoints = remember(segments) { segments.flatten() }
    val selectedPoint = remember(selectedSeconds, renderedPoints, title) {
        selectedSeconds?.let { at ->
            renderedPoints.minByOrNull { abs(it.seconds - at) }
                ?.takeIf { abs(it.seconds - at) <= 60.0 }
        }
    }
    val selectedValue = selectedPoint?.let(metric)
    fun valueText(value: Double): String = if (pace) {
        ActivityRecord.duration(value) + "/km"
    } else String.format(Locale.getDefault(), "%.0f %s", value, unit)
    var chartWidthPx by remember(samples, title) { mutableFloatStateOf(0f) }
    fun selectAt(x: Float) {
        if(chartWidthPx <= 0f) return
        val fraction = (x / chartWidthPx).coerceIn(0f, 1f)
        val target = firstTime + (lastTime - firstTime) * fraction
        // Select a REAL recorded sample, never a timestamp invented between
        // points. Other charts can report missing data at this same time.
        val nearest = renderedPoints.minByOrNull { abs(it.seconds - target) }
        if(nearest != null) onSelectSeconds(nearest.seconds)
    }
    val lineColor = when(title) {
        "Heart rate" -> Color(0xFFFF8C87)
        "Elevation" -> Color(0xFF9EC8D7)
        "Cadence" -> Color(0xFFC2A8FF)
        else -> activityLime
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
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
            "Sample avg: " + valueText(sampleAverage) +
                "    Range: " + valueText(low) + "–" + valueText(high),
            color = activityMuted, fontSize = 10.sp, maxLines = 2,
            modifier = Modifier.testTag("activity-chart-summary-" + title.lowercase().replace(" ", "-"))
        )
        if (selectedSeconds != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                ActivityRecord.duration(selectedSeconds) + " · " +
                    (selectedValue?.let { valueText(it) } ?: "No measurement at this point"),
                color = lineColor, fontSize = 11.sp,
                modifier = Modifier.testTag("activity-chart-cursor-" + title.lowercase().replace(" ", "-"))
            )
        }
        Spacer(Modifier.height(9.dp))
        Canvas(
            Modifier.fillMaxWidth().height(124.dp)
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
                        validSamples.size + " measured points. " +
                        "Average " + valueText(sampleAverage) +
                        ". Drag to inspect values."
                }
        ) {
            val span = (high - low).coerceAtLeast(1.0)
            val timeSpan = lastTime - firstTime
            fun position(sample: ActivitySample, value: Double): Offset {
                val x = ((sample.seconds - firstTime) / timeSpan * size.width).toFloat()
                val norm = ((value - low) / span).toFloat().coerceIn(0f, 1f)
                val y = if (pace) norm * (size.height - 8f) + 4f
                    else (1f - norm) * (size.height - 8f) + 4f
                return Offset(x, y)
            }
            for (step in 1..3) {
                val y = size.height * step / 4f
                drawLine(activityMuted.copy(alpha = .18f),
                    Offset(0f, y), Offset(size.width, y), 1f)
            }
            val avgY = if(pace) {
                ((sampleAverage-low) / span).toFloat() * (size.height-8f) + 4f
            } else {
                (1f - ((sampleAverage-low) / span).toFloat()) * (size.height-8f) + 4f
            }
            drawLine(activityMuted.copy(alpha = .45f),
                Offset(0f,avgY),Offset(size.width,avgY),1.dp.toPx())
            segments.forEach { section ->
                section.zipWithNext().forEach { (a,b) ->
                    val av=metric(a)
                    val bv=metric(b)
                    if(av != null && bv != null) {
                        drawLine(lineColor,position(a,av),position(b,bv),
                            strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)
                    }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ActivityRecord.duration(firstTime), color = activityMuted, fontSize = 10.sp)
            Text(ActivityRecord.duration(lastTime), color = activityMuted, fontSize = 10.sp)
        }
    }
}
