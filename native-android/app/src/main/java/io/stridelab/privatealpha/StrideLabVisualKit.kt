package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Shared AMOLED Black + StrideLab Lime visual component kit.
 * Presentation-only: zero engine mutation, zero fabricated metrics.
 */
internal object StrideLabVisualPalette {
    val amoledBlack = Color(0xFF000000)
    val canvas = Color(0xFF0B0B0B)
    val surface = Color(0xFF111315)
    val surfaceRaised = Color(0xFF171A1E)
    val border = Color(0xFF2A2E33)
    val ink = Color(0xFFF7F7F5)
    val muted = Color(0xFF9FA4AA)

    // Core brand + semantic intensity palette
    val lime = Color(0xFFD8FB70)
    val limeTint = Color(0xFF1D240B)
    val easyTeal = Color(0xFF5EEAD4)       // Z1 / Easy / Warm-up / Cooldown
    val moderateAmber = Color(0xFFFBBF24)  // Z2 / Tempo / Threshold / Taper
    val highCoral = Color(0xFFFB7185)      // Z3 / Intervals / Hills / Conflict
    val recoveryCyan = Color(0xFF38BDF8)   // Recovery / Deload weeks
    val strengthCoral = Color(0xFFFF8A77)
    val calisthenicsViolet = Color(0xFFC8A8FF)
    val conditioningSky = Color(0xFF91BFFF)

    fun disciplineColor(discipline: String): Color = when (discipline) {
        "run" -> lime
        "str" -> strengthCoral
        "cal" -> calisthenicsViolet
        "cond" -> conditioningSky
        else -> muted
    }
}

internal data class WorkoutVisualBlock(
    val label: String,
    val purpose: String,
    val weight: Float,
    val intensityFraction: Float,
    val color: Color,
    val detail: String,
    val paceHint: String? = null
)

internal object WorkoutVisualBlocks {
    fun fromSession(
        session: PlannedSession,
        zones: Engine15ProvisionalPaceZones? = null
    ): List<WorkoutVisualBlock> {
        val prescription = session.runningPrescription
        val family = prescription?.family?.lowercase(Locale.ROOT).orEmpty()
        fun workColor(): Color = when (family) {
            "intervals", "hills" -> StrideLabVisualPalette.highCoral
            "tempo", "threshold", "fartlek", "progression" -> StrideLabVisualPalette.moderateAmber
            else -> StrideLabVisualPalette.lime
        }
        fun workHeight(): Float = when (family) {
            "intervals", "hills" -> 1.0f
            "threshold", "fartlek" -> 0.85f
            "tempo", "progression" -> 0.72f
            else -> 0.60f
        }
        fun workPace(): String? = zones?.let { z ->
            when (family) {
                "intervals", "hills", "fartlek" -> z.intervals.formatRange()
                "threshold" -> z.threshold.formatRange()
                "tempo", "progression" -> z.tempo.formatRange()
                else -> z.easy.formatRange()
            }
        }

        val mixed = prescription?.mixedSegments
        if (!mixed.isNullOrEmpty()) {
            return mixed.map { step ->
                val isWork = step.purpose.equals("WORK", ignoreCase = true)
                val isRec = step.purpose.equals("RECOVERY", ignoreCase = true)
                val weight = (step.seconds?.toFloat() ?: step.meters?.div(3.5f) ?: 120f).coerceAtLeast(30f)
                val durText = step.seconds?.let { s -> if (s >= 60) "${s / 60}m" else "${s}s" }
                    ?: step.meters?.let { "${it}m" }.orEmpty()
                val distText = (step.meters ?: step.distanceEquivalentMeters)?.let { " · ${it}m" }.orEmpty()
                WorkoutVisualBlock(
                    label = when {
                        isWork -> family.replaceFirstChar { it.uppercase() }.ifBlank { "Work" }
                        isRec -> "Recovery"
                        step.purpose.equals("WARM_UP", true) -> "Warm-up"
                        step.purpose.equals("COOL_DOWN", true) -> "Cooldown"
                        else -> "Easy"
                    },
                    purpose = step.purpose,
                    weight = weight,
                    intensityFraction = when {
                        isWork -> workHeight()
                        isRec -> 0.28f
                        else -> 0.42f
                    },
                    color = when {
                        isWork -> workColor()
                        isRec -> StrideLabVisualPalette.recoveryCyan
                        else -> StrideLabVisualPalette.easyTeal
                    },
                    detail = "$durText$distText · ${step.effort}",
                    paceHint = if (isWork) workPace() else zones?.easy?.formatRange()
                )
            }
        }

        val segments = prescription?.segments
        if (!segments.isNullOrEmpty()) {
            return segments.map { seg ->
                val isWork = seg.purpose.equals("WORK", ignoreCase = true)
                val isLong = session.kind == "long"
                WorkoutVisualBlock(
                    label = if (isWork) family.replaceFirstChar { it.uppercase() }.ifBlank { "Work" }
                    else if (isLong) "Long Run" else "Easy Run",
                    purpose = seg.purpose,
                    weight = seg.meters.toFloat().coerceAtLeast(200f),
                    intensityFraction = if (isWork) workHeight() else if (isLong) 0.56f else 0.42f,
                    color = if (isWork) workColor() else if (isLong) StrideLabVisualPalette.lime else StrideLabVisualPalette.easyTeal,
                    detail = "${"%.1f".format(Locale.US, seg.meters / 1000.0)} km · ${seg.effort}",
                    paceHint = if (isWork) workPace() else zones?.easy?.formatRange()
                )
            }
        }

        val isLong = session.kind == "long"
        return listOf(
            WorkoutVisualBlock(
                label = if (isLong) "Long Run" else "Easy Run",
                purpose = "EASY",
                weight = 1f,
                intensityFraction = if (isLong) 0.58f else 0.42f,
                color = if (isLong) StrideLabVisualPalette.lime else StrideLabVisualPalette.easyTeal,
                detail = "${session.distanceKm?.let { "%.1f km".format(Locale.US, it) } ?: "${session.minutes} min"} · Conversational",
                paceHint = zones?.easy?.formatRange()
            )
        )
    }
}

/** Proportional intensity/duration workout block chart (interactive or compact mini thumbnail). */
@Composable
internal fun WorkoutStructureBarChart(
    blocks: List<WorkoutVisualBlock>,
    modifier: Modifier = Modifier,
    height: Dp = 76.dp,
    interactive: Boolean = true,
    tag: String = "workout-structure-bars"
) {
    if (blocks.isEmpty()) return
    var selectedIndex by remember(blocks) { mutableStateOf<Int?>(null) }
    val totalWeight = blocks.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)

    Column(modifier = modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            blocks.forEachIndexed { index, block ->
                val isSelected = selectedIndex == index
                val fraction = (block.weight / totalWeight).coerceAtLeast(0.05f)
                val barColor by animateColorAsState(
                    if (selectedIndex == null || isSelected) block.color else block.color.copy(alpha = 0.35f),
                    animationSpec = tween(160),
                    label = "bar-$index"
                )
                Box(
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxHeight(block.intensityFraction.coerceIn(0.22f, 1f))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(barColor)
                        .then(if (interactive) Modifier.clickable {
                            selectedIndex = if (isSelected) null else index
                        } else Modifier)
                )
            }
        }
        if (interactive) {
            val active = selectedIndex?.let { blocks.getOrNull(it) }
            if (active != null) {
                Surface(
                    color = StrideLabVisualPalette.surfaceRaised,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, active.color.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(active.label, color = active.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(active.detail, color = StrideLabVisualPalette.ink, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        active.paceHint?.let { pace ->
                            Surface(
                                color = StrideLabVisualPalette.canvas,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, StrideLabVisualPalette.border)
                            ) {
                                Text(
                                    "$pace · Est.",
                                    color = StrideLabVisualPalette.lime,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Interactive 4–16 week periodization bar chart for Plan Blueprint Review. */
@Composable
internal fun PlanPeriodizationChart(
    plan: PlanSnapshot,
    selectedWeekIndex: Int,
    onSelectWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalWeeks = plan.request.weeks.coerceAtLeast(1)
    val weekTotals = remember(plan.id, plan.revision) {
        (0 until totalWeeks).map { w ->
            val start = plan.request.start.plusWeeks(w.toLong())
            val end = start.plusDays(6)
            val rows = plan.sessions.filter { it.date in start..end }
            val runKm = rows.filter { it.discipline == "run" }.sumOf { it.distanceKm ?: 0.0 }
            val hasQuality = rows.any { it.quality }
            val isRecovery = w % 4 == 3
            Triple(runKm, hasQuality, isRecovery)
        }
    }
    val maxKm = (weekTotals.maxOfOrNull { it.first } ?: 1.0).coerceAtLeast(1.0)

    Surface(
        modifier = modifier.fillMaxWidth().testTag("plan-periodization-chart"),
        color = StrideLabVisualPalette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("VOLUME ARC", color = StrideLabVisualPalette.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot("Build", StrideLabVisualPalette.lime)
                    LegendDot("Recovery", StrideLabVisualPalette.recoveryCyan)
                    LegendDot("Quality", StrideLabVisualPalette.moderateAmber)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(92.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weekTotals.forEachIndexed { index, (km, hasQuality, isRecovery) ->
                    val selected = index == selectedWeekIndex
                    val barHeight = (km / maxKm).toFloat().coerceIn(0.14f, 1f)
                    val baseColor = when {
                        isRecovery -> StrideLabVisualPalette.recoveryCyan
                        else -> StrideLabVisualPalette.lime
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectWeek(index) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (hasQuality) {
                            Box(
                                Modifier.size(5.dp).clip(CircleShape)
                                    .background(StrideLabVisualPalette.moderateAmber)
                            )
                            Spacer(Modifier.height(3.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.76f)
                                .fillMaxHeight(barHeight * 0.72f)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(if (selected) baseColor else baseColor.copy(alpha = 0.42f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "W${index + 1}",
                            fontSize = if (totalWeeks > 12) 8.sp else 9.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) StrideLabVisualPalette.ink else StrideLabVisualPalette.muted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, color = StrideLabVisualPalette.muted, fontSize = 10.sp)
    }
}

/** Compact 3-zone Training Intensity Distribution bar powered by Engine15MarathonTidEvaluator. */
@Composable
internal fun TidIntensityBar(
    evaluation: Engine15TidEvaluation,
    modifier: Modifier = Modifier
) {
    val z1 = evaluation.overall.zone1Fraction.toFloat().coerceIn(0f, 1f)
    val z2 = evaluation.overall.zone2Fraction.toFloat().coerceIn(0f, 1f)
    val z3 = evaluation.overall.zone3Fraction.toFloat().coerceIn(0f, 1f)
    val patternLabel = when (evaluation.pattern) {
        Engine15TidPattern.LOW_INTENSITY_ONLY -> "Aerobic Base"
        Engine15TidPattern.PYRAMIDAL -> "Pyramidal"
        Engine15TidPattern.POLARIZED -> "Polarized"
        Engine15TidPattern.HIGH_INTENSITY_HEAVY -> "High Intensity"
        Engine15TidPattern.INCOMPLETE_PRESCRIPTION_DATA -> "Partial"
    }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("tid-intensity-bar"),
        color = StrideLabVisualPalette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("INTENSITY MIX", color = StrideLabVisualPalette.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Surface(
                    color = StrideLabVisualPalette.limeTint,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StrideLabVisualPalette.lime.copy(alpha = 0.45f))
                ) {
                    Text(patternLabel, color = StrideLabVisualPalette.lime, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp))
                    .background(StrideLabVisualPalette.surfaceRaised),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (z1 > 0.005f) Box(Modifier.weight(z1).fillMaxHeight().background(StrideLabVisualPalette.easyTeal))
                if (z2 > 0.005f) Box(Modifier.weight(z2).fillMaxHeight().background(StrideLabVisualPalette.moderateAmber))
                if (z3 > 0.005f) Box(Modifier.weight(z3).fillMaxHeight().background(StrideLabVisualPalette.highCoral))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Easy ${(z1 * 100).toInt()}%", color = StrideLabVisualPalette.easyTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("Tempo/Thr ${(z2 * 100).toInt()}%", color = StrideLabVisualPalette.moderateAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("Intervals ${(z3 * 100).toInt()}%", color = StrideLabVisualPalette.highCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Compact 4-tile provisional pace-zone strip (clearly badged EST.). */
@Composable
internal fun ProvisionalPaceZoneStrip(
    zones: Engine15ProvisionalPaceZones,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("provisional-pace-zone-strip"),
        color = StrideLabVisualPalette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TRAINING PACE ZONES", color = StrideLabVisualPalette.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("ESTIMATED", color = StrideLabVisualPalette.lime, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val items = listOf(
                    Triple("Easy", zones.easy, StrideLabVisualPalette.easyTeal),
                    Triple("Tempo", zones.tempo, StrideLabVisualPalette.moderateAmber),
                    Triple("Threshold", zones.threshold, StrideLabVisualPalette.lime),
                    Triple("Intervals", zones.intervals, StrideLabVisualPalette.highCoral)
                )
                items.forEach { (label, range, color) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = StrideLabVisualPalette.canvas,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(vertical = 7.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                Engine15PaceRange.formatPace(range.midpointSecondsPerKm),
                                color = StrideLabVisualPalette.ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text("/km", color = StrideLabVisualPalette.muted, fontSize = 8.5.sp)
                        }
                    }
                }
            }
        }
    }
}

/** 4-week (28-day) consistency heatmap strip for Running Dashboard & Today. */
@Composable
internal fun ConsistencyHeatmapStrip(
    completedDates: Set<LocalDate>,
    plannedDates: Set<LocalDate>,
    asOf: LocalDate,
    modifier: Modifier = Modifier
) {
    val start = asOf.minusDays(27)
    val days = (0L..27L).map { start.plusDays(it) }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("consistency-heatmap-strip"),
        color = StrideLabVisualPalette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("28-DAY CONSISTENCY", color = StrideLabVisualPalette.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${days.count { it in completedDates }} active days", color = StrideLabVisualPalette.lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { day ->
                            val done = day in completedDates
                            val planned = day in plannedDates
                            val fill = when {
                                done -> StrideLabVisualPalette.lime
                                planned && day.isBefore(asOf) -> StrideLabVisualPalette.highCoral.copy(alpha = 0.45f)
                                planned -> StrideLabVisualPalette.border
                                else -> StrideLabVisualPalette.surfaceRaised
                            }
                            Box(
                                Modifier.weight(1f).height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(fill)
                            )
                        }
                    }
                }
            }
        }
    }
}
