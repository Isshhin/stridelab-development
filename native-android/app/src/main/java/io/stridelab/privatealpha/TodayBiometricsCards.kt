package io.stridelab.privatealpha

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val RhrRose = Color(0xFFFB7185)
private val HrvCyan = Color(0xFF2DD4BF)
private val SleepViolet = Color(0xFFA78BFA)
private val SleepDeepColor = Color(0xFF7C3AED)
private val SleepRemColor = Color(0xFF38BDF8)
private val SleepLightColor = Color(0xFFA78BFA)
private val SleepAwakeColor = Color(0xFF475569)

@Composable
internal fun TodayBiometricsSection(
    wellness: List<WellnessRecord>,
    selectedDate: LocalDate,
    garminConnected: Boolean,
    lastWellnessSyncMillis: Long?,
    onConnectGarmin: () -> Unit,
    onRefreshWellness: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val snapshot = remember(wellness, selectedDate) {
        BiometricReadinessEvaluator.evaluate(wellness, selectedDate)
    }
    var expandedCard by rememberSaveable { mutableStateOf<String?>(null) }

    val syncLabel = remember(garminConnected, lastWellnessSyncMillis, snapshot.hasAnyData) {
        when {
            lastWellnessSyncMillis != null && lastWellnessSyncMillis > 0L -> {
                val time = Instant.ofEpochMilli(lastWellnessSyncMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
                "Garmin · Synced $time"
            }
            garminConnected -> "Garmin · Tap to sync"
            else -> "Connect Garmin"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("today-biometrics-section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recovery & Biometrics",
                color = TodayAdaptivePalette.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = syncLabel,
                color = if (garminConnected) TodayAdaptivePalette.textMuted else TodayAdaptivePalette.runningLime,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (garminConnected) onRefreshWellness?.invoke() else onConnectGarmin()
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .testTag("today-biometrics-sync-action")
            )
        }

        // 2+1 Grid: Top row = RHR + HRV side-by-side; Bottom row = Sleep full-width
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TodayRhrCard(
                rhr = snapshot.rhr,
                expanded = expandedCard == "rhr",
                onClick = { expandedCard = if (expandedCard == "rhr") null else "rhr" },
                modifier = Modifier.weight(1f)
            )
            TodayHrvCard(
                hrv = snapshot.hrv,
                expanded = expandedCard == "hrv",
                onClick = { expandedCard = if (expandedCard == "hrv") null else "hrv" },
                modifier = Modifier.weight(1f)
            )
        }

        TodaySleepCard(
            sleep = snapshot.sleep,
            expanded = expandedCard == "sleep",
            onClick = { expandedCard = if (expandedCard == "sleep") null else "sleep" },
            modifier = Modifier.fillMaxWidth()
        )

        if (!garminConnected && !snapshot.hasAnyData) {
            Surface(
                onClick = onConnectGarmin,
                shape = RoundedCornerShape(14.dp),
                color = TodayAdaptivePalette.card,
                border = BorderStroke(1.dp, TodayAdaptivePalette.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-biometrics-connect-prompt")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Connect Garmin to sync RHR, HRV & Sleep",
                        color = TodayAdaptivePalette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Connect",
                        color = TodayAdaptivePalette.runningLime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayRhrCard(
    rhr: RhrBiometricSummary,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        rhr.latestBpm == null -> "No data"
        rhr.status == BiometricMetricStatus.BUILDING_BASELINE -> "Baseline ${rhr.baseline28dCount}/7d"
        rhr.elevatedFlag -> {
            val d = rhr.deltaFromBaselineBpm?.roundToInt() ?: 0
            "+$d bpm"
        }
        else -> {
            val d = rhr.deltaFromBaselineBpm?.roundToInt() ?: 0
            if (d >= 0) "+$d bpm" else "$d bpm"
        }
    }
    val statusColor = when (rhr.status) {
        BiometricMetricStatus.ELEVATED_STRAIN -> RhrRose
        BiometricMetricStatus.NORMAL -> HrvCyan
        else -> TodayAdaptivePalette.textMuted
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = TodayAdaptivePalette.card,
        border = BorderStroke(1.dp, TodayAdaptivePalette.border),
        modifier = modifier.testTag("today-biometric-rhr")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RHR",
                    color = RhrRose,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                BiometricStatusPill(text = statusText, accent = statusColor)
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = rhr.latestBpm?.roundToInt()?.toString() ?: "--",
                    color = TodayAdaptivePalette.text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TabularTextStyle,
                    maxLines = 1
                )
                Text(
                    text = "bpm",
                    color = TodayAdaptivePalette.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            BiometricSparkline(
                values = rhr.series7d.map { it.second },
                accent = RhrRose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )

            Text(
                text = rhr.baseline28dMeanBpm?.let { "28d avg ${it.roundToInt()} bpm" }
                    ?: "Needs 7 days for baseline",
                color = TodayAdaptivePalette.textMuted,
                fontSize = 11.sp,
                style = TabularTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(visible = expanded && rhr.series7d.any { it.second != null }) {
                Biometric7DayBreakdown(
                    series = rhr.series7d,
                    formatter = { v -> v?.let { "${it.roundToInt()}" } ?: "--" }
                )
            }
        }
    }
}

@Composable
private fun TodayHrvCard(
    hrv: HrvBiometricSummary,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        hrv.latestMs == null -> "No data"
        hrv.status == BiometricMetricStatus.BUILDING_BASELINE -> "${hrv.baseline28dCount}/14d"
        hrv.suppressedFlag -> "Below range"
        else -> hrv.garminStatus?.lowercase(Locale.ENGLISH)
            ?.replaceFirstChar { it.uppercase() } ?: "Balanced"
    }
    val statusColor = when (hrv.status) {
        BiometricMetricStatus.SUPPRESSED_STRAIN -> SleepViolet
        BiometricMetricStatus.NORMAL -> HrvCyan
        else -> TodayAdaptivePalette.textMuted
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = TodayAdaptivePalette.card,
        border = BorderStroke(1.dp, TodayAdaptivePalette.border),
        modifier = modifier.testTag("today-biometric-hrv")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HRV",
                    color = HrvCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                BiometricStatusPill(text = statusText, accent = statusColor)
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = hrv.latestMs?.roundToInt()?.toString() ?: "--",
                    color = TodayAdaptivePalette.text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TabularTextStyle,
                    maxLines = 1
                )
                Text(
                    text = "ms",
                    color = TodayAdaptivePalette.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            BiometricSparkline(
                values = hrv.series7d.map { it.second },
                accent = HrvCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )

            val subLabel = when {
                hrv.garminBaselineLowMs != null && hrv.garminBaselineHighMs != null ->
                    "Range ${hrv.garminBaselineLowMs.roundToInt()}-${hrv.garminBaselineHighMs.roundToInt()} ms"
                hrv.baseline28dMeanMs != null ->
                    "28d avg ${hrv.baseline28dMeanMs.roundToInt()} ms"
                else -> "Overnight rMSSD"
            }
            Text(
                text = subLabel,
                color = TodayAdaptivePalette.textMuted,
                fontSize = 11.sp,
                style = TabularTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(visible = expanded && hrv.series7d.any { it.second != null }) {
                Biometric7DayBreakdown(
                    series = hrv.series7d,
                    formatter = { v -> v?.let { "${it.roundToInt()}" } ?: "--" }
                )
            }
        }
    }
}

@Composable
private fun TodaySleepCard(
    sleep: SleepBiometricSummary,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalStageMin = (sleep.deepMinutes ?: 0) + (sleep.lightMinutes ?: 0) +
        (sleep.remMinutes ?: 0) + (sleep.awakeMinutes ?: 0)
    val statusText = when {
        sleep.latestHours == null -> "No data"
        sleep.sleepScore != null -> "Score ${sleep.sleepScore}"
        sleep.cumulativeDebtFlag -> "Sleep debt"
        sleep.severeAcuteLossFlag -> "Short sleep"
        else -> "On track"
    }
    val statusColor = when {
        sleep.cumulativeDebtFlag || sleep.severeAcuteLossFlag -> RhrRose
        sleep.latestHours != null -> SleepViolet
        else -> TodayAdaptivePalette.textMuted
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = TodayAdaptivePalette.card,
        border = BorderStroke(1.dp, TodayAdaptivePalette.border),
        modifier = modifier.testTag("today-biometric-sleep")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SLEEP",
                        color = SleepViolet,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                    sleep.average7dHours?.let { avg ->
                        Text(
                            text = "7d avg ${formatSleepHours(avg)}",
                            color = TodayAdaptivePalette.textMuted,
                            fontSize = 11.sp,
                            style = TabularTextStyle
                        )
                    }
                }
                BiometricStatusPill(text = statusText, accent = statusColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = sleep.latestHours?.let(::formatSleepHours) ?: "--",
                    color = TodayAdaptivePalette.text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TabularTextStyle,
                    maxLines = 1
                )
                if (totalStageMin > 0) {
                    Text(
                        text = "Deep ${sleep.deepMinutes ?: 0}m · REM ${sleep.remMinutes ?: 0}m · Light ${sleep.lightMinutes ?: 0}m",
                        color = TodayAdaptivePalette.textSecondary,
                        fontSize = 11.sp,
                        style = TabularTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (totalStageMin > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(TodayAdaptivePalette.track),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    val segments = listOf(
                        (sleep.deepMinutes ?: 0) to SleepDeepColor,
                        (sleep.remMinutes ?: 0) to SleepRemColor,
                        (sleep.lightMinutes ?: 0) to SleepLightColor,
                        (sleep.awakeMinutes ?: 0) to SleepAwakeColor
                    ).filter { it.first > 0 }
                    segments.forEach { (mins, color) ->
                        Box(
                            modifier = Modifier
                                .weight(mins.toFloat())
                                .fillMaxHeight()
                                .background(color)
                        )
                    }
                }
            } else {
                BiometricSparkline(
                    values = sleep.series7d.map { it.second },
                    accent = SleepViolet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                )
            }

            AnimatedVisibility(visible = expanded && sleep.series7d.any { it.second != null }) {
                Biometric7DayBreakdown(
                    series = sleep.series7d,
                    formatter = { v -> v?.let(::formatSleepHours) ?: "--" }
                )
            }
        }
    }
}

@Composable
private fun BiometricStatusPill(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            style = TabularTextStyle,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun BiometricSparkline(
    values: List<Double?>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val nonNull = values.filterNotNull()
    Canvas(modifier = modifier) {
        if (nonNull.size < 2) {
            drawLine(
                color = TodayAdaptivePalette.track,
                start = Offset(0f, size.height * 0.5f),
                end = Offset(size.width, size.height * 0.5f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            return@Canvas
        }
        val minVal = nonNull.minOrNull() ?: 0.0
        val maxVal = nonNull.maxOrNull() ?: 1.0
        val span = (maxVal - minVal).coerceAtLeast(1.0)
        val padY = 3.dp.toPx()
        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
        val count = values.size.coerceAtLeast(2)
        val stepX = size.width / (count - 1).toFloat()

        val path = Path()
        var started = false
        var lastPoint: Offset? = null
        values.forEachIndexed { index, v ->
            if (v != null) {
                val x = index * stepX
                val norm = ((v - minVal) / span).toFloat().coerceIn(0f, 1f)
                val y = padY + usableH * (1f - norm)
                val pt = Offset(x, y)
                if (!started) {
                    path.moveTo(pt.x, pt.y)
                    started = true
                } else {
                    path.lineTo(pt.x, pt.y)
                }
                lastPoint = pt
            }
        }
        drawPath(
            path = path,
            color = accent,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        lastPoint?.let { pt ->
            drawCircle(color = accent, radius = 3.dp.toPx(), center = pt)
        }
    }
}

@Composable
private fun Biometric7DayBreakdown(
    series: List<Pair<LocalDate, Double?>>,
    formatter: (Double?) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        series.forEach { (day, value) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = day.dayOfWeek.name.take(1),
                    color = TodayAdaptivePalette.textMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatter(value),
                    color = TodayAdaptivePalette.textSecondary,
                    fontSize = 10.sp,
                    style = TabularTextStyle
                )
            }
        }
    }
}

private fun formatSleepHours(hours: Double): String {
    val totalMinutes = (hours * 60.0).roundToInt().coerceAtLeast(0)
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h}h ${m}m"
}
