package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale

/**
 * Art-directed AMOLED Black + StrideLab Lime visual component kit.
 * Uses vertical luminance gradients, glowing top caps, and crisp hairline framing.
 * Presentation-only: zero engine mutation, zero fabricated metrics.
 */
internal object StrideLabVisualPalette {
    val amoledBlack = Color(0xFF000000)
    val canvas = Color(0xFF090B0D)
    val surface = Color(0xFF101214)
    val surfaceRaised = Color(0xFF16191E)
    val border = Color(0xFF22262D)
    val borderSubtle = Color(0xFF1B1F25)
    val ink = Color(0xFFF7F7F5)
    val secondary = Color(0xFFA0A7B2)
    val muted = Color(0xFF707885)

    // Signature brand + semantic intensity palette
    val lime = Color(0xFFD8FB70)
    val limeTint = Color(0xFF17200B)
    val easyTeal = Color(0xFF5EEAD4)       // Z1 / Easy / Warm-up / Cooldown
    val moderateAmber = Color(0xFFFBBF24)  // Z2 / Tempo / Threshold / Taper
    val highCoral = Color(0xFFFB7185)      // Z3 / Intervals / Hills / Warning
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

    fun barGradient(topColor: Color, alphaScale: Float = 1f): Brush = Brush.verticalGradient(
        listOf(
            topColor.copy(alpha = 0.95f * alphaScale),
            topColor.copy(alpha = 0.42f * alphaScale),
            topColor.copy(alpha = 0.14f * alphaScale)
        )
    )
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
            "threshold", "fartlek" -> 0.86f
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

        fun formatKm(meters: Int?): String? {
            if (meters == null || meters <= 0) return null
            val km = meters / 1000.0
            return if (km < 1.0 && meters % 100 != 0) {
                String.format(Locale.US, "%.2f km", km)
            } else {
                String.format(Locale.US, "%.1f km", km)
            }
        }

        val mixed = prescription?.mixedSegments
        if (!mixed.isNullOrEmpty()) {
            return mixed.map { step ->
                val isWork = step.purpose.equals("WORK", ignoreCase = true)
                val isRec = step.purpose.equals("RECOVERY", ignoreCase = true)
                val weight = (step.seconds?.toFloat() ?: step.meters?.div(3.5f) ?: 120f).coerceAtLeast(30f)
                val durText = step.seconds?.let { s -> if (s >= 60) "${s / 60}m" else "${s}s" }
                    ?: formatKm(step.meters).orEmpty()
                val distText = if (step.seconds != null) {
                    formatKm(step.meters ?: step.distanceEquivalentMeters)?.let { " · $it" }.orEmpty()
                } else ""
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
                        isRec -> 0.30f
                        else -> 0.44f
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
                    intensityFraction = if (isWork) workHeight() else if (isLong) 0.58f else 0.44f,
                    color = if (isWork) workColor() else if (isLong) StrideLabVisualPalette.lime else StrideLabVisualPalette.easyTeal,
                    detail = "${"%.1f".format(Locale.US, seg.meters / 1000.0)} km · ${seg.effort}",
                    paceHint = if (isWork) workPace() else zones?.easy?.formatRange()
                )
            }
        }

        if (session.quality || session.kind == "quality") {
            return synthesizeQualityBlocks(session, zones)
        }

        val isLong = session.kind == "long"
        return listOf(
            WorkoutVisualBlock(
                label = if (isLong) "Long Run" else "Easy Run",
                purpose = "EASY",
                weight = 1f,
                intensityFraction = if (isLong) 0.58f else 0.44f,
                color = if (isLong) StrideLabVisualPalette.lime else StrideLabVisualPalette.easyTeal,
                detail = "${session.distanceKm?.let { "%.1f km".format(Locale.US, it) } ?: "${session.minutes} min"} · Conversational",
                paceHint = zones?.easy?.formatRange()
            )
        )
    }

    private fun synthesizeQualityBlocks(
        session: PlannedSession,
        zones: Engine15ProvisionalPaceZones?
    ): List<WorkoutVisualBlock> {
        val titleLower = session.title.lowercase(Locale.ROOT)
        val rawFamily = session.runningPrescription?.family?.takeIf { it.isNotBlank() } ?: when {
            "hill" in titleLower -> "Hills"
            "threshold" in titleLower -> "Threshold"
            "fartlek" in titleLower -> "Fartlek"
            "progression" in titleLower -> "Progression"
            "interval" in titleLower -> "Intervals"
            else -> "Tempo"
        }
        val famKey = rawFamily.lowercase(Locale.ROOT)
        val workCol = when (famKey) {
            "intervals", "hills" -> StrideLabVisualPalette.highCoral
            else -> StrideLabVisualPalette.moderateAmber
        }
        val workHeight = when (famKey) {
            "intervals", "hills" -> 1.0f
            "threshold", "fartlek" -> 0.86f
            "progression" -> 0.78f
            else -> 0.72f
        }
        val workPace = zones?.let { z ->
            when (famKey) {
                "intervals", "hills", "fartlek" -> z.intervals.formatRange()
                "threshold" -> z.threshold.formatRange()
                else -> z.tempo.formatRange()
            }
        }
        val firstStep = session.steps.firstOrNull().orEmpty()
        val mainStep = session.steps.getOrNull(1).orEmpty()
        val lastStep = session.steps.lastOrNull().orEmpty()
        val warmUpMin = Regex("""^(\d+)\s*min""", RegexOption.IGNORE_CASE)
            .find(firstStep)?.groupValues?.get(1)?.toIntOrNull()
            ?: (session.minutes * 0.22).toInt().coerceIn(8, 15)
        val coolDownMin = Regex("""^(\d+)\s*min""", RegexOption.IGNORE_CASE)
            .find(lastStep)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { session.steps.size >= 3 }
            ?: (session.minutes * 0.18).toInt().coerceIn(8, 15)
        val blocks = mutableListOf(
            WorkoutVisualBlock(
                label = "Warm-up",
                purpose = "WARM_UP",
                weight = warmUpMin.toFloat().coerceAtLeast(5f),
                intensityFraction = 0.44f,
                color = StrideLabVisualPalette.easyTeal,
                detail = "$warmUpMin min · Easy warm-up",
                paceHint = zones?.easy?.formatRange()
            )
        )
        val repMatch = Regex("""(\d+)\s*[×x]\s*(\d+)\s*(min|sec)""", RegexOption.IGNORE_CASE).find(mainStep)
        if (repMatch != null) {
            val reps = repMatch.groupValues[1].toInt().coerceIn(1, 12)
            val workVal = repMatch.groupValues[2].toInt().coerceAtLeast(1)
            val unit = repMatch.groupValues[3].lowercase(Locale.ROOT)
            val workWeight = if (unit == "sec") (workVal / 60f).coerceAtLeast(1f) else workVal.toFloat()
            val recMin = Regex("""with\s+(\d+)\s*min""", RegexOption.IGNORE_CASE)
                .find(mainStep)?.groupValues?.get(1)?.toFloatOrNull()?.coerceAtLeast(1f) ?: 2f
            for (rep in 1..reps) {
                blocks += WorkoutVisualBlock(
                    label = "$rawFamily $rep",
                    purpose = "WORK",
                    weight = workWeight,
                    intensityFraction = workHeight,
                    color = workCol,
                    detail = "$workVal $unit · $rawFamily",
                    paceHint = workPace
                )
                if (rep < reps) {
                    blocks += WorkoutVisualBlock(
                        label = "Recovery",
                        purpose = "RECOVERY",
                        weight = recMin,
                        intensityFraction = 0.30f,
                        color = StrideLabVisualPalette.recoveryCyan,
                        detail = "${recMin.toInt()} min · Easy jog recovery",
                        paceHint = zones?.easy?.formatRange()
                    )
                }
            }
        } else if (famKey == "progression") {
            val nums = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).findAll(mainStep)
                .mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            val steadyMin = nums.getOrNull(0) ?: ((session.minutes - warmUpMin - coolDownMin).coerceAtLeast(10) / 2)
            val tempoMin = nums.getOrNull(1) ?: ((session.minutes - warmUpMin - coolDownMin).coerceAtLeast(10) - steadyMin).coerceAtLeast(5)
            blocks += WorkoutVisualBlock(
                label = "Steady",
                purpose = "WORK",
                weight = steadyMin.toFloat().coerceAtLeast(5f),
                intensityFraction = 0.56f,
                color = StrideLabVisualPalette.lime,
                detail = "$steadyMin min · Steady aerobic",
                paceHint = zones?.easy?.formatRange()
            )
            blocks += WorkoutVisualBlock(
                label = "Tempo finish",
                purpose = "WORK",
                weight = tempoMin.toFloat().coerceAtLeast(5f),
                intensityFraction = workHeight,
                color = workCol,
                detail = "$tempoMin min · Controlled tempo finish",
                paceHint = workPace
            )
        } else {
            val mainMin = Regex("""^(\d+)\s*min""", RegexOption.IGNORE_CASE)
                .find(mainStep)?.groupValues?.get(1)?.toIntOrNull()
                ?: (session.minutes - warmUpMin - coolDownMin).coerceAtLeast(10)
            blocks += WorkoutVisualBlock(
                label = rawFamily,
                purpose = "WORK",
                weight = mainMin.toFloat().coerceAtLeast(6f),
                intensityFraction = workHeight,
                color = workCol,
                detail = "$mainMin min · Steady controlled $famKey",
                paceHint = workPace
            )
        }
        blocks += WorkoutVisualBlock(
            label = "Cooldown",
            purpose = "COOL_DOWN",
            weight = coolDownMin.toFloat().coerceAtLeast(5f),
            intensityFraction = 0.44f,
            color = StrideLabVisualPalette.easyTeal,
            detail = "$coolDownMin min · Easy cooldown",
            paceHint = zones?.easy?.formatRange()
        )
        return blocks
    }
}

/** Proportional gradient workout block chart with glowing top caps & interactive tap callout. */
@Composable
internal fun WorkoutStructureBarChart(
    blocks: List<WorkoutVisualBlock>,
    modifier: Modifier = Modifier,
    height: Dp = 84.dp,
    interactive: Boolean = true,
    tag: String = "workout-structure-bars"
) {
    if (blocks.isEmpty()) return
    var selectedIndex by remember(blocks) { mutableStateOf<Int?>(null) }
    val totalWeight = blocks.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)

    Column(modifier = modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(height)) {
            // Subtle background guide lines
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                repeat(3) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(StrideLabVisualPalette.borderSubtle))
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                blocks.forEachIndexed { index, block ->
                    val isSelected = selectedIndex == index
                    val dimmed = selectedIndex != null && !isSelected
                    val fraction = (block.weight / totalWeight).coerceAtLeast(0.05f)
                    val capColor by animateColorAsState(
                        if (dimmed) block.color.copy(alpha = 0.28f) else block.color,
                        animationSpec = tween(160),
                        label = "cap-$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(fraction)
                            .fillMaxHeight(block.intensityFraction.coerceIn(0.24f, 1f))
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(StrideLabVisualPalette.barGradient(block.color, if (dimmed) 0.30f else 1f))
                            .then(if (interactive) Modifier.clickable {
                                selectedIndex = if (isSelected) null else index
                            } else Modifier)
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(3.dp)
                                .align(Alignment.TopCenter)
                                .background(capColor)
                        )
                    }
                }
            }
        }
        if (interactive) {
            val active = selectedIndex?.let { blocks.getOrNull(it) }
            if (active != null) {
                Surface(
                    color = StrideLabVisualPalette.surfaceRaised,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, active.color.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(active.label.uppercase(), color = active.color, fontSize = 10.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                            Text(active.detail, color = StrideLabVisualPalette.ink, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        active.paceHint?.let { pace ->
                            Surface(
                                color = StrideLabVisualPalette.canvas,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, StrideLabVisualPalette.border)
                            ) {
                                Text(
                                    "$pace · Est.",
                                    color = StrideLabVisualPalette.lime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Interactive 4–16 week gradient volume arc chart for Plan Blueprint Review. */
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
    val activeWeek = weekTotals.getOrNull(selectedWeekIndex.coerceIn(0, weekTotals.lastIndex))

    Surface(
        modifier = modifier.fillMaxWidth().testTag("plan-periodization-chart"),
        color = StrideLabVisualPalette.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "VOLUME PROGRESSION",
                        color = StrideLabVisualPalette.muted,
                        fontSize = 10.sp,
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendDot("Build", StrideLabVisualPalette.lime)
                        LegendDot("Rec", StrideLabVisualPalette.recoveryCyan)
                        LegendDot("Key", StrideLabVisualPalette.moderateAmber)
                    }
                }
                activeWeek?.let { (km, _, rec) ->
                    Text(
                        "Week ${selectedWeekIndex + 1} · ${"%.1f".format(Locale.US, km)} km" + if (rec) " · Recovery" else " · Build",
                        color = if (rec) StrideLabVisualPalette.recoveryCyan else StrideLabVisualPalette.lime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weekTotals.forEachIndexed { index, (km, hasQuality, isRecovery) ->
                    val selected = index == selectedWeekIndex
                    val barHeight = (km / maxKm).toFloat().coerceIn(0.18f, 1f)
                    val baseColor = if (isRecovery) StrideLabVisualPalette.recoveryCyan else StrideLabVisualPalette.lime
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectWeek(index) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.height(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasQuality) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(StrideLabVisualPalette.moderateAmber)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.76f)
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StrideLabVisualPalette.surfaceRaised),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((barHeight * 80f).coerceAtLeast(10f).dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(StrideLabVisualPalette.barGradient(baseColor, if (selected) 1f else 0.45f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(2.5.dp)
                                        .align(Alignment.TopCenter)
                                        .background(if (selected) baseColor else baseColor.copy(alpha = 0.55f))
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${index + 1}",
                            fontSize = if (totalWeeks > 12) 8.5.sp else 9.5.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selected) StrideLabVisualPalette.ink else StrideLabVisualPalette.muted,
                            maxLines = 1,
                            softWrap = false
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
        Text(
            label,
            color = StrideLabVisualPalette.secondary,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Sleek 3-zone Training Intensity Distribution card powered by Engine15MarathonTidEvaluator. */
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("INTENSITY DISTRIBUTION", color = StrideLabVisualPalette.muted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                Surface(
                    color = StrideLabVisualPalette.limeTint,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, StrideLabVisualPalette.lime.copy(alpha = 0.45f))
                ) {
                    Text(patternLabel, color = StrideLabVisualPalette.lime, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp))
                    .background(StrideLabVisualPalette.surfaceRaised),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (z1 > 0.005f) Box(Modifier.weight(z1).fillMaxHeight().background(StrideLabVisualPalette.easyTeal))
                if (z2 > 0.005f) Box(Modifier.weight(z2).fillMaxHeight().background(StrideLabVisualPalette.moderateAmber))
                if (z3 > 0.005f) Box(Modifier.weight(z3).fillMaxHeight().background(StrideLabVisualPalette.highCoral))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendValue("Easy", "${(z1 * 100).toInt()}%", StrideLabVisualPalette.easyTeal)
                LegendValue("Tempo / Thr", "${(z2 * 100).toInt()}%", StrideLabVisualPalette.moderateAmber)
                LegendValue("Intervals", "${(z3 * 100).toInt()}%", StrideLabVisualPalette.highCoral)
            }
        }
    }
}

@Composable
private fun LegendValue(label: String, pct: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text("$label ", color = StrideLabVisualPalette.secondary, fontSize = 11.sp)
        Text(pct, color = StrideLabVisualPalette.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Art-directed 4-tile provisional pace-zone strip (clearly badged ESTIMATED). */
@Composable
internal fun ProvisionalPaceZoneStrip(
    zones: Engine15ProvisionalPaceZones,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("provisional-pace-zone-strip"),
        color = StrideLabVisualPalette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TRAINING PACE ZONES", color = StrideLabVisualPalette.muted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                Text("ESTIMATED", color = StrideLabVisualPalette.lime, fontSize = 9.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val items = listOf(
                    Triple("EASY", zones.easy, StrideLabVisualPalette.easyTeal),
                    Triple("TEMPO", zones.tempo, StrideLabVisualPalette.moderateAmber),
                    Triple("THR", zones.threshold, StrideLabVisualPalette.lime),
                    Triple("INT", zones.intervals, StrideLabVisualPalette.highCoral)
                )
                items.forEach { (label, range, color) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = StrideLabVisualPalette.canvas,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
                    ) {
                        Column(
                            Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.width(18.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(color))
                            Spacer(Modifier.height(5.dp))
                            Text(label, color = color, fontSize = 9.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                Engine15PaceRange.formatPace(range.midpointSecondsPerKm),
                                color = StrideLabVisualPalette.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            Text("/km", color = StrideLabVisualPalette.muted, fontSize = 9.sp)
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("28-DAY CONSISTENCY", color = StrideLabVisualPalette.muted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                Text("${days.count { it in completedDates }} active days", color = StrideLabVisualPalette.lime, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        week.forEach { day ->
                            val done = day in completedDates
                            val planned = day in plannedDates
                            val fill = when {
                                done -> StrideLabVisualPalette.lime
                                planned && day.isBefore(asOf) -> StrideLabVisualPalette.highCoral.copy(alpha = 0.42f)
                                planned -> StrideLabVisualPalette.border
                                else -> StrideLabVisualPalette.surfaceRaised
                            }
                            Box(
                                Modifier.weight(1f).height(15.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(fill)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Gradient AMOLED Lap Split Bar Chart highlighting faster work laps vs easy recoveries. */
@Composable
internal fun ActivityLapBarChart(
    laps: List<ActivityLap>,
    modifier: Modifier = Modifier
) {
    val valid = laps.filter { it.pace != null && it.pace.isFinite() && it.pace > 0.0 && it.seconds != null && it.seconds > 0.0 }
    if (valid.size < 2) return
    val paces = valid.map { it.pace!! }.sorted()
    val medianPace = paces[paces.size / 2]
    val fastest = paces.first()
    val slowest = paces.last().coerceAtLeast(fastest + 1.0)
    Surface(
        modifier = modifier.fillMaxWidth().testTag("activity-lap-bar-chart"),
        color = StrideLabVisualPalette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LAP INTENSITY PROFILE", color = StrideLabVisualPalette.muted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                Text("${valid.size} laps", color = StrideLabVisualPalette.lime, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val totalSec = valid.sumOf { it.seconds!! }.toFloat().coerceAtLeast(1f)
                valid.forEach { lap ->
                    val pace = lap.pace!!
                    val speedRatio = ((slowest - pace) / (slowest - fastest)).toFloat().coerceIn(0.24f, 1f)
                    val color = when {
                        pace <= medianPace * 0.88 -> StrideLabVisualPalette.highCoral
                        pace <= medianPace * 0.94 -> StrideLabVisualPalette.moderateAmber
                        else -> StrideLabVisualPalette.easyTeal
                    }
                    Box(
                        Modifier
                            .weight((lap.seconds!!.toFloat() / totalSec).coerceAtLeast(0.04f))
                            .fillMaxHeight(speedRatio)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(StrideLabVisualPalette.barGradient(color))
                    ) {
                        Box(Modifier.fillMaxWidth().height(2.5.dp).align(Alignment.TopCenter).background(color))
                    }
                }
            }
        }
    }
}
