package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private val activityLime = Color(0xFFD8FB70)
private val activitySheetBlack = Color(0xFF080D0C)
private val activityText = Color(0xFFF3F6F3)
private val activityMuted = Color(0xFFA6B4AD)
private val activityBorder = Color(0xFF303E37)
private val activitySurface = Color(0xFF111A16)

private data class SourceChartAvailability(
    val pace: Boolean, val heartRate: Boolean, val elevation: Boolean
) {
    val any: Boolean get() = pace || heartRate || elevation
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
    }
}

private fun activityDisplayName(name: String): String {
    // Shorten the redundant generic sport suffix only for display; source data
    // and the recorded title remain untouched.
    return if (name.endsWith(" Running", ignoreCase = true) && name.length > 8)
        name.dropLast(8).trimEnd() else name
}

@Composable
internal fun TracksidePrimaryActivityMetrics(activity: ActivityRecord) {
    Row(
        Modifier.fillMaxWidth().testTag("activity-primary-metrics"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PrimaryActivityMetric(
            "DISTANCE", activity.distanceM?.let { 
                String.format(Locale.getDefault(), "%.2f", it / 1000.0) 
            } ?: "—",
            if (activity.distanceM != null) "km" else "",
            ActivityMetricGlyph.Route, Modifier.weight(1f)
        )
        PrimaryActivityMetric(
            "MOVING TIME", activity.movingTimeS?.let { ActivityRecord.duration(it) } ?: "—",
            "", ActivityMetricGlyph.Timer, Modifier.weight(1.13f)
        )
        PrimaryActivityMetric(
            "AVG PACE", if (activity.supportsPace && activity.detailPaceLabel != "Not recorded") {
                activity.detailPaceLabel.removeSuffix(" /km").trim()
            } else "—",
            if (activity.supportsPace && activity.detailPaceLabel != "Not recorded") "/km" else "",
            ActivityMetricGlyph.Shoe, Modifier.weight(1f)
        )
    }
}

private enum class ActivityMetricGlyph { Route, Timer, Shoe }

@Composable
private fun PrimaryActivityMetric(
    label: String, value: String, unit: String,
    symbol: ActivityMetricGlyph, modifier: Modifier = Modifier
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
            }
        }
        Text(
            label, color = activityMuted, fontSize = 9.5.sp,
            lineHeight = 12.sp, maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                value, color = activityText, fontSize = 19.sp,
                lineHeight = 23.sp, fontWeight = FontWeight.Bold,
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
        for (sample in samples) {
            if (activity.supportsPace && sample.pace?.isFinite() == true) pace++
            if (sample.hr?.isFinite() == true) hr++
            if (sample.altitude?.isFinite() == true) altitude++
            if (pace >= 2 && hr >= 2 && altitude >= 2) break
        }
        SourceChartAvailability(pace >= 2, hr >= 2, altitude >= 2)
    }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "section-overview") {
                Column(Modifier.fillMaxWidth().testTag("activity-section-overview")) {
                    ActivitySectionHeading("Activity details")
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
                            Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "LAP", color = activityMuted, fontSize = 10.sp,
                                modifier = Modifier.width(33.dp)
                            )
                            Text(
                                "PACE", color = activityMuted, fontSize = 10.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasSplitHr) Text(
                                "HR", color = activityMuted, fontSize = 10.sp
                            )
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
                            "Pace", "/km", recorded.samples, { it.pace }, pace = true
                        )
                    }
                if (chartAvailability.heartRate)
                    item(key = "chart-heart-rate") {
                        TracksideActivitySourceChart(
                            "Heart rate", "bpm", recorded.samples, { it.hr }
                        )
                    }
                if (chartAvailability.elevation)
                    item(key = "chart-elevation") {
                        TracksideActivitySourceChart(
                            "Elevation", "m", recorded.samples, { it.altitude }
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        ActivityInfoLine("Distance", activity.distanceM?.let { activity.distanceLabel })
        ActivityInfoLine("Moving time", activity.movingTimeS?.let { ActivityRecord.duration(it) })
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
        if (DashboardSummary.isRun(activity)) {
            Spacer(Modifier.height(13.dp))
            ActivityPlanLink(state, activity, actions.planning, matchedSourceIds)
        }
        if (activity.source == "garmin" && detailSource.id == activity.id && recorded == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Garmin recording is not yet cached for offline analysis.",
                color = activityMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onLoadGarminGps?.invoke() },
                enabled = online && !state.busy && !state.needsSignIn && onLoadGarminGps != null,
                modifier = Modifier.fillMaxWidth().testTag("garmin-load-gps")
            ) { Text(if (state.busy) "Loading recording…" else "Load Garmin recording") }
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
        // Preserve source-derived training context without inventing training effect.
        if (history.size > 1) {
            val comparison = ActivityIntelligence.comparison(activity, history)
            if (comparison != null) {
                Spacer(Modifier.height(12.dp))
                Text("Compared with " + comparison.count + " earlier saved runs",
                    color = activityMuted, fontSize = 11.sp)
            }
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
        Modifier.fillMaxWidth().heightIn(min = 38.dp)
            .testTag("activity-split-" + index),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text((index + 1).toString(), color = activityText, fontSize = 12.sp,
            modifier = Modifier.width(33.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    recordedPace?.let { ActivityRecord.duration(it) }
                        ?: lap.seconds?.let { ActivityRecord.duration(it) } ?: "—",
                    color = activityText, fontSize = 12.sp,
                    modifier = Modifier.width(52.dp)
                )
                if (recordedPace != null) {
                    Box(Modifier.fillMaxWidth().height(7.dp)
                        .background(Color(0xFF2A3530), RoundedCornerShape(5.dp))) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                            .background(activityLime, RoundedCornerShape(5.dp)))
                    }
                } else {
                    Text("Pace not recorded", color = activityMuted, fontSize = 10.sp)
                }
            }
        }
        if (hasHr) {
            Text(
                lap.hr?.roundToInt()?.toString() ?: "—",
                color = activityMuted, fontSize = 11.sp
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
    pace: Boolean = false
) {
    // Limit drawing cost for long FIT recordings; missing samples and gaps
    // remain breaks, never interpolated measurements.
    // Build real contiguous segments before downsampling. Never draw a
    // connecting line across missing values or gaps in the source recording.
    val segments = remember(samples, title) {
        val result = mutableListOf<List<ActivitySample>>()
        var segment = mutableListOf<ActivitySample>()
        var previous: ActivitySample? = null
        fun finish() {
            if (segment.isNotEmpty()) result.add(segment)
            segment = mutableListOf()
            previous = null
        }
        samples.forEach { sample ->
            val value = metric(sample)
            if (value == null || !value.isFinite() ||
                (previous != null && sample.seconds - previous!!.seconds > 120.0)
            ) {
                finish()
            }
            if (value != null && value.isFinite()) {
                segment.add(sample)
                previous = sample
            }
        }
        finish()
        val step = (samples.size / 450).coerceAtLeast(1)
        result.map { contiguous ->
            contiguous.filterIndexed { i, _ -> i % step == 0 || i == contiguous.lastIndex }
        }
    }
    val values = remember(segments, title) { segments.flatten().mapNotNull(metric) }
    if (values.size < 2) return
    val low = values.minOrNull() ?: return
    val high = values.maxOrNull() ?: return
    val firstTime = samples.first().seconds
    val lastTime = samples.last().seconds
    if (lastTime <= firstTime) return
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .background(activitySurface, RoundedCornerShape(17.dp))
            .padding(horizontal = 13.dp, vertical = 14.dp)
            .testTag("activity-chart-" + title.lowercase().replace(" ", "-"))
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = activityText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(unit, color = activityMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(9.dp))
        Canvas(
            Modifier.fillMaxWidth().height(112.dp)
                .testTag("stream-" + title)
                .semantics { contentDescription = title + " source chart, recorded values only" }
        ) {
            val span = (high - low).coerceAtLeast(1.0)
            val timeSpan = lastTime - firstTime
            for (step in 1..3) {
                val y = size.height * step / 4f
                drawLine(activityMuted.copy(alpha = .14f),
                    Offset(0f, y), Offset(size.width, y), 1f)
            }
            fun position(sample: ActivitySample, value: Double): Offset {
                val x = ((sample.seconds - firstTime) / timeSpan * size.width).toFloat()
                val norm = ((value - low) / span).toFloat().coerceIn(0f, 1f)
                val y = if (pace) norm * (size.height - 8f) + 4f
                    else (1f - norm) * (size.height - 8f) + 4f
                return Offset(x, y)
            }
            segments.forEach { segment ->
                segment.zipWithNext().forEach { (a, b) ->
                    val first = metric(a)
                    val second = metric(b)
                    if (first != null && second != null) {
                        drawLine(
                            if (title == "Elevation") activityMuted else activityLime,
                            position(a, first), position(b, second),
                            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", color = activityMuted, fontSize = 10.sp)
            Text(ActivityRecord.duration(lastTime), color = activityMuted, fontSize = 10.sp)
        }
    }
}
