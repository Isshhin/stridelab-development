@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun AthleteDiagnosticsVisualCard(
    diagnostic: AthleteDiagnosticSnapshot,
    modifier: Modifier = Modifier
) {
    val volume = diagnostic.volumeTolerance
    val quality = diagnostic.qualityExecution
    val easy = diagnostic.easyDiscipline
    val hr = diagnostic.aerobicEfficiency

    val volumeFraction = if (volume.plannedKm > 0.0) {
        (volume.completedKm / volume.plannedKm).toFloat().coerceIn(0f, 1f)
    } else 0f
    val qualityFraction = when {
        quality.verifiedHitRatePct != null -> (quality.verifiedHitRatePct / 100.0).toFloat().coerceIn(0f, 1f)
        quality.evaluatedQualityCount > 0 -> (quality.verifiedHitCount.toFloat() / quality.evaluatedQualityCount.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    val easyInZone = (easy.evaluatedEasyCount - easy.tooFastCount).coerceAtLeast(0)
    val easyFraction = if (easy.evaluatedEasyCount > 0) {
        (easyInZone.toFloat() / easy.evaluatedEasyCount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("coaching-diagnostics"),
        color = StrideLabSurfaceTokens.surfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "COACHING DIAGNOSTICS",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = volume.exposureStatus.name.replace('_', ' ').lowercase(Locale.ROOT)
                            .replaceFirstChar { it.titlecase(Locale.ROOT) },
                        color = StrideLabSurfaceTokens.inkSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Surface(
                    color = StrideLabSurfaceTokens.surfaceMuted,
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                ) {
                    Text(
                        text = diagnostic.overallConfidence.name,
                        color = StrideLabSurfaceTokens.ink,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticVisualMeterTile(
                    eyebrow = "VOLUME",
                    value = "${String.format(Locale.US, "%.1f", volume.completedKm)}/${String.format(Locale.US, "%.0f", volume.plannedKm)}",
                    unit = "km",
                    subLabel = "${volume.completedSessions}/${volume.plannedSessions} sess",
                    fraction = volumeFraction,
                    accent = StrideLabSurfaceTokens.lime,
                    modifier = Modifier.weight(1f)
                )
                DiagnosticVisualMeterTile(
                    eyebrow = "QUALITY",
                    value = quality.verifiedHitRatePct?.let { "${it.toInt()}%" }
                        ?: "${quality.verifiedHitCount}/${quality.evaluatedQualityCount}",
                    unit = if (quality.verifiedHitRatePct != null) "hit" else "sess",
                    subLabel = "${quality.verifiedHitCount}/${quality.evaluatedQualityCount} hit",
                    fraction = qualityFraction,
                    accent = StrideLabSurfaceTokens.amber,
                    modifier = Modifier.weight(1f)
                )
                DiagnosticVisualMeterTile(
                    eyebrow = "EASY ZONE",
                    value = "$easyInZone/${easy.evaluatedEasyCount}",
                    unit = "ok",
                    subLabel = if (easy.tooFastCount == 0) "On target" else "${easy.tooFastCount} hot",
                    fraction = easyFraction,
                    accent = if (easy.tooFastCount == 0) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.coral,
                    modifier = Modifier.weight(1f)
                )
            }

            if (hr.hrDataAvailable) {
                Surface(
                    color = StrideLabSurfaceTokens.surfaceMuted,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HR efficiency · ${hr.trend.name.replace('_', ' ').lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }}",
                            color = StrideLabSurfaceTokens.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        hr.efChangePct?.let { pct ->
                            Text(
                                text = String.format(Locale.US, "%+.1f%%", pct),
                                color = if (pct >= 0.0) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.amber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                style = TabularTextStyle
                            )
                        }
                    }
                }
            }

            val topInsights = diagnostic.insights
                .sortedBy {
                    when (it.severity) {
                        DiagnosticInsightSeverity.ACTIONABLE -> 0
                        DiagnosticInsightSeverity.CAUTION -> 1
                        DiagnosticInsightSeverity.INFO -> 2
                    }
                }
                .take(2)
            if (topInsights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    topInsights.forEach { insight ->
                        val dotColor = when (insight.severity) {
                            DiagnosticInsightSeverity.ACTIONABLE -> StrideLabSurfaceTokens.coral
                            DiagnosticInsightSeverity.CAUTION -> StrideLabSurfaceTokens.amber
                            DiagnosticInsightSeverity.INFO -> StrideLabSurfaceTokens.inkMuted
                        }
                        Surface(
                            color = StrideLabSurfaceTokens.surfaceMuted,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = insight.message,
                                    color = if (insight.severity == DiagnosticInsightSeverity.INFO) {
                                        StrideLabSurfaceTokens.inkSecondary
                                    } else {
                                        StrideLabSurfaceTokens.ink
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (diagnostic.recalibrationAdvisory == RecalibrationAdvisory.ADDRESS_EASY_PACING_FIRST) {
                EasyPacingLockBanner()
            }
        }
    }
}

@Composable
private fun DiagnosticVisualMeterTile(
    eyebrow: String,
    value: String,
    unit: String,
    subLabel: String,
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = StrideLabSurfaceTokens.surfaceMuted,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = eyebrow,
                color = StrideLabSurfaceTokens.inkMuted,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TabularTextStyle,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = unit,
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(StrideLabSurfaceTokens.hairline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(100.dp))
                        .background(accent)
                )
            }
            Text(
                text = subLabel,
                color = StrideLabSurfaceTokens.inkSecondary,
                fontSize = 10.sp,
                style = TabularTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable internal fun CoachFeedbackChoices(label:String,options:List<String>,value:String?,enabled:Boolean=true,onChange:(String?)->Unit) {
    Text(label,style=MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        (listOf("Unanswered")+options).forEach { option ->
            val choice=option.takeUnless { it=="Unanswered" }
            SignalChip(selected=value==choice,onClick={onChange(choice)},enabled=enabled,label={Text(option)},modifier=Modifier.heightIn(min=48.dp))
        }
    }
}

@Composable private fun FatigueSegmentBar(label:String,value:String?,enabled:Boolean=true,onChange:(String?)->Unit) {
    val options = remember { (1..5).map { it.toString() } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(Locale.ROOT),
                color = StrideLabSurfaceTokens.inkMuted,
                fontSize = 10.sp,
                letterSpacing = 1.0.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value?.let { "Level $it/5" } ?: "Optional",
                color = if (value != null) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.inkSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                style = TabularTextStyle
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val selected = value == option
                val accent = when (option.toIntOrNull()) {
                    4, 5 -> StrideLabSurfaceTokens.coral
                    3 -> StrideLabSurfaceTokens.amber
                    else -> StrideLabSurfaceTokens.lime
                }
                Surface(
                    color = if (selected) accent else StrideLabSurfaceTokens.surfaceMuted,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (selected) accent else StrideLabSurfaceTokens.hairline),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled) {
                            onChange(if (selected) null else option)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = option,
                            color = if (selected) StrideLabSurfaceTokens.onLime else StrideLabSurfaceTokens.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TabularTextStyle
                        )
                    }
                }
            }
        }
    }
}

@Composable internal fun Engine15CoachReviewCard(plan:PlanSnapshot,actions:PlanActions,busy:Boolean,error:String?=null) {
    if(plan.status!="approved" || !Engine1Engines.supportsCoachingLoop(plan))return
    var open by rememberSaveable(plan.owner,plan.id) { mutableStateOf(false) }
    var fatigue by rememberSaveable(plan.owner,plan.id) { mutableStateOf<String?>(null) }
    var unavailableCsv by rememberSaveable(plan.owner,plan.id) { mutableStateOf("") }
    var review by remember(plan.owner,plan.id,plan.revision) { mutableStateOf<Engine15WeeklyReview.Review?>(null) }
    var overrun by remember(plan.owner,plan.id,plan.revision) { mutableStateOf<Engine15WeeklyReview.Review?>(null) }
    var dismissedOverrun by rememberSaveable(plan.owner,plan.id) { mutableStateOf("") }
    var localError by remember(plan.owner,plan.id,plan.revision) { mutableStateOf<String?>(null) }
    val today=LocalDate.now()
    val unavailable=unavailableCsv.split(',').filter { it.isNotBlank() }.map(LocalDate::parse).filter { it>today && it<=today.plusDays(7) }.toSet()
    LaunchedEffect(plan.owner,plan.id,plan.revision,today) {
        overrun=runCatching { actions.previewWeek(plan.id,Engine15WeeklyReview.Input(trigger=Engine15WeeklyReview.Trigger.OVERRUN)) }.getOrNull()?.takeIf { it.overrunSessionIds.isNotEmpty() }
    }
    Surface(
        modifier=Modifier.fillMaxWidth().testTag("coach-week-review"),
        color=StrideLabSurfaceTokens.surfaceElevated,
        shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp, if (open) StrideLabSurfaceTokens.lime.copy(alpha = 0.45f) else StrideLabSurfaceTokens.hairline)
    ) {
        Column(Modifier.padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(
                modifier=Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .strideLabPressFeedback(
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { open = !open }
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "WEEKLY REVIEW",
                            color = StrideLabSurfaceTokens.inkMuted,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (overrun != null) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StrideLabSurfaceTokens.amber)
                            )
                        }
                    }
                    Text(
                        text = "Weekly coach review",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = StrideLabSurfaceTokens.ink
                    )
                }
                Surface(
                    color = if (open) StrideLabSurfaceTokens.limeMuted else StrideLabSurfaceTokens.surfaceMuted,
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, if (open) StrideLabSurfaceTokens.lime.copy(alpha = 0.4f) else StrideLabSurfaceTokens.hairline)
                ) {
                    Text(
                        text = if (open) "Hide" else "Review week",
                        color = StrideLabSurfaceTokens.lime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            overrun?.takeIf { it.overrunSessionIds.joinToString()!=dismissedOverrun }?.let { extra ->
                Surface(
                    color = StrideLabSurfaceTokens.surfaceMuted,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.amber.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StrideLabSurfaceTokens.amber)
                            )
                            Text(
                                text = "${extra.overrunSessionIds.size} extra volume session logged",
                                color = StrideLabSurfaceTokens.ink,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick={
                                open=true;localError=null
                                try { review=actions.previewWeek(plan.id,Engine15WeeklyReview.Input(fatigue?.toInt(),unavailable,Engine15WeeklyReview.Trigger.OVERRUN)) }
                                catch(e:IllegalArgumentException) { localError=e.message }
                            },enabled=!busy) { Text("Review extra running", color = StrideLabSurfaceTokens.lime) }
                            TextButton(onClick={dismissedOverrun=extra.overrunSessionIds.joinToString()}) {
                                Text("Not now", color = StrideLabSurfaceTokens.inkMuted)
                            }
                        }
                    }
                }
            }
            if(open) {
                FatigueSegmentBar("Fatigue · 1 fresh – 5 exhausted",fatigue,!busy) { fatigue=it;review=null }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "UNAVAILABLE DAYS",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.0.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        (1L..7L).forEach { offset ->
                            val date=today.plusDays(offset)
                            val blocked = date in unavailable
                            Surface(
                                color = if (blocked) StrideLabSurfaceTokens.coral.copy(alpha = 0.16f) else StrideLabSurfaceTokens.surfaceMuted,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (blocked) StrideLabSurfaceTokens.coral else StrideLabSurfaceTokens.hairline),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !busy) {
                                        unavailableCsv = (if (blocked) unavailable - date else unavailable + date)
                                            .sorted()
                                            .joinToString(",")
                                        review = null
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = dayName(date.dayOfWeek.value).take(1),
                                        color = if (blocked) StrideLabSurfaceTokens.coral else StrideLabSurfaceTokens.inkMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = if (blocked) StrideLabSurfaceTokens.coral else StrideLabSurfaceTokens.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = TabularTextStyle
                                    )
                                }
                            }
                        }
                    }
                }
                SignalButton("Preview weekly decision",{
                    localError=null
                    try { review=actions.previewWeek(plan.id,Engine15WeeklyReview.Input(fatigue?.toInt(),unavailable)) ?: throw PlanConstraint("Review is unavailable. Refresh your plan.") }
                    catch(e:IllegalArgumentException) { localError=e.message }
                },!busy)
                review?.let { proposed ->
                    Surface(
                        color = StrideLabSurfaceTokens.surfaceMuted,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if(proposed.changes.isEmpty())"Keep this week's plan" else "${proposed.changes.size} proposed ${if(proposed.changes.size==1)"change" else "changes"}",
                                    style=MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StrideLabSurfaceTokens.ink
                                )
                                proposed.aerobicEfficiencyPercentChange?.let { pct ->
                                    Text(
                                        text = String.format(Locale.US,"AE %+.1f%%",pct),
                                        style = TabularTextStyle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StrideLabSurfaceTokens.lime
                                    )
                                }
                            }
                            proposed.changes.forEach { change ->
                                val session=plan.sessions.single { it.id==change.sessionId }
                                val summary = when {
                                    change.minutes == 0 -> "Rest"
                                    change.minutes > session.minutes -> "${change.minutes}m (+${change.minutes - session.minutes}m${change.suggestedDistanceKm?.let { String.format(Locale.US," · %.1f km",it) }.orEmpty()})"
                                    else -> "${change.minutes}m"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${session.title} · ${session.date}", color = StrideLabSurfaceTokens.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text(change.reason, color = StrideLabSurfaceTokens.inkMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(
                                        text = "${session.minutes}m → $summary",
                                        color = StrideLabSurfaceTokens.lime,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = TabularTextStyle
                                    )
                                }
                            }
                            proposed.explanations.filterNot { it.startsWith("Evidence:") }.take(2).forEach {
                                Text(it, style=MaterialTheme.typography.bodySmall, color = StrideLabSurfaceTokens.inkSecondary)
                            }
                            SignalButton(if(proposed.changes.isEmpty())"Approve unchanged week" else "Approve these changes",{actions.acceptWeek(proposed)},!busy)
                        }
                    }
                }
                (localError ?: error)?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                TextButton(onClick={open=false;review=null}) { Text("Close review", color = StrideLabSurfaceTokens.inkMuted) }
            }
        }
    }
}

@Composable internal fun RunningHistoryConfirmation(state:TrainingState,onUse:(Double)->Unit) {
    val rows=remember(state.activities,state.plans) {
        observedRunningWeeks(state.activities,plans=state.plans)
    }
    val detected=remember(state.activities,state.plans) {
        automaticRunningBaseline(state.activities,plans=state.plans)
    }
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var selectedCsv by rememberSaveable { mutableStateOf("") }
    val selected=selectedCsv.split(',').filter { it.isNotBlank() }.map(LocalDate::parse).toSet()
    SignalCard(Modifier.testTag("running-history-confirmation")) {
        Text("RECENT RUNNING HISTORY",style=MaterialTheme.typography.labelMedium,
            color=MaterialTheme.colorScheme.primary)
        if(detected!=null) {
            Text("Estimated baseline · %.1f km/week".format(detected.weeklyKm),
                style=MaterialTheme.typography.titleMedium,
                modifier=Modifier.testTag("running-history-auto-baseline"))
            Text("${detected.weeks.size} completed calendar weeks with recorded runs. Used when no existing baseline is set; previously chosen mileage is preserved.",
                style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={onUse(detected.weeklyKm)}) {
                Text("Use estimated mileage")
            }
            TextButton(onClick={manualOpen=!manualOpen}) {
                Text(if(manualOpen) "Hide manual week selection" else "Select different weeks")
            }
        } else {
            Text("An automatic baseline needs at least two completed weeks with three recorded runs each. Enter your usual mileage above, or select complete weeks manually.",
                style=MaterialTheme.typography.bodySmall)
        }
        if(detected==null || manualOpen) {
            rows.forEach { row ->
                val complete=row.start.plusDays(6)<LocalDate.now()
                SignalChip(selected=row.start in selected,enabled=complete && row.distanceKm!=null,
                    onClick={
                        selectedCsv=(if(row.start in selected) selected-row.start
                            else selected+row.start).sorted().joinToString(",")
                    },label={
                        Text("${row.start} · ${row.distanceKm?.let { "%.1f km".format(it) } ?: "distance incomplete"} · ${row.runs} ${if(row.runs==1)"run" else "runs"}${if(!complete)" · in progress" else ""}")
                    },
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp))
            }
            val average=historyWeeklyKm(state.activities,plans=state.plans,confirmedWeeks=selected)
            SignalButton(
                average?.let { "Use selected average · %.1f km/week".format(it) }
                    ?: "Confirm complete weeks",
                { average?.let(onUse) },average!=null)
        }
    }
}

@Composable internal fun MileageGoalChoices(plan:PlanSnapshot,busy:Boolean,onChoose:(PlanRequest)->Unit) {
    val options=plan.mileageOptions ?: return
    SignalCard(Modifier.testTag("mileage-goal-options")) {
        Text("RECONSIDER YOUR MILEAGE GOAL",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Text("The current constraints do not reach ${options.targetKm} km/week. Choosing an option creates another draft for review; nothing is approved automatically.")
        SignalButton("Review %.1f km within %d weeks".format(options.lowerTargetKm,plan.request.weeks),{onChoose(plan.request.copy(targetWeeklyKm=options.lowerTargetKm))},!busy && options.lowerTargetKm>0)
        options.longerWeeks?.let { weeks ->
            SignalButton("Review ${options.targetKm} km over $weeks weeks",{onChoose(plan.request.copy(weeks=weeks))},!busy)
        } ?: Text("A longer timeline is not established within 16 weeks. Review history and availability before extending.",style=MaterialTheme.typography.bodySmall)
    }
}
