package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

internal data class DisciplineDistributionSlice(
    val discipline: String,
    val label: String,
    val sessionCount: Int,
    val plannedMinutes: Int,
    val color: Color
)

internal data class HybridInterferenceInsight(
    val date: LocalDate,
    val headline: String,
    val explanation: String
)

internal object HybridTrainingInsights {
    fun weeklyDistribution(
        plans: List<PlanSnapshot>,
        weekStart: LocalDate
    ): List<DisciplineDistributionSlice> {
        val weekEnd = weekStart.plusDays(6)
        val sessions = plans.flatMap { it.sessions }
            .filter { it.date in weekStart..weekEnd && it.status != "unassigned" }
        if (sessions.isEmpty()) return emptyList()

        return sessions.groupBy { it.discipline }
            .map { (discipline, list) ->
                DisciplineDistributionSlice(
                    discipline = discipline,
                    label = trainingTrackLabel(discipline),
                    sessionCount = list.size,
                    plannedMinutes = list.sumOf { it.effectiveMinutes.coerceAtLeast(0) },
                    color = StrideLabVisualPalette.disciplineColor(discipline)
                )
            }
            .sortedByDescending { it.plannedMinutes }
    }

    fun detectInterference(
        plans: List<PlanSnapshot>,
        weekStart: LocalDate
    ): List<HybridInterferenceInsight> {
        val weekEnd = weekStart.plusDays(6)
        val sessions = plans.flatMap { it.sessions }
            .filter { it.date in weekStart..weekEnd && it.status != "unassigned" }
            .sortedBy { it.date }
        if (sessions.size < 2) return emptyList()

        val insights = mutableListOf<HybridInterferenceInsight>()
        val byDate = sessions.groupBy { it.date }
        for ((date, daySessions) in byDate) {
            val hasHardRun = daySessions.any { it.discipline == "run" && (it.quality || "long" in it.kind.lowercase()) }
            val hasLowerLoad = daySessions.any { it.discipline != "run" && it.lower }
            if (hasHardRun && hasLowerLoad) {
                insights += HybridInterferenceInsight(
                    date = date,
                    headline = "Same-day lower-body & key run stacking",
                    explanation = "Quality running and lower-body resistance share $date. Keep at least 6 hours between sessions or move lower-body work."
                )
            }
            val nextDaySessions = byDate[date.plusDays(1)].orEmpty()
            val nextHardRun = nextDaySessions.any { it.discipline == "run" && (it.quality || "long" in it.kind.lowercase()) }
            if (hasLowerLoad && nextHardRun && !hasHardRun) {
                insights += HybridInterferenceInsight(
                    date = date.plusDays(1),
                    headline = "Back-to-back leg fatigue window",
                    explanation = "Lower-body strength on $date precedes a key run on ${date.plusDays(1)}. Expect slightly heavier legs early in the run."
                )
            }
        }
        return insights.distinctBy { it.date }.take(2)
    }
}

@Composable
internal fun HybridWeeklyDistributionStrip(
    slices: List<DisciplineDistributionSlice>,
    insights: List<HybridInterferenceInsight>,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) return
    val totalMinutes = slices.sumOf { it.plannedMinutes }.coerceAtLeast(1)

    Surface(
        modifier = modifier.fillMaxWidth().testTag("hybrid-distribution-strip"),
        shape = RoundedCornerShape(14.dp),
        color = StrideLabSurfaceTokens.surfaceElevated,
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
                    text = "HYBRID WEEK DISTRIBUTION",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = "${slices.sumOf { it.sessionCount }} sessions · ${totalMinutes}m",
                    color = StrideLabSurfaceTokens.inkSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(StrideLabSurfaceTokens.surfaceSubtle),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                slices.forEach { slice ->
                    val weight = (slice.plannedMinutes.toFloat() / totalMinutes.toFloat()).coerceAtLeast(0.08f)
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(slice.color)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                slices.forEach { slice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(slice.color)
                        )
                        Text(
                            text = "${slice.label} ${slice.sessionCount}x",
                            color = StrideLabSurfaceTokens.inkSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            insights.firstOrNull()?.let { insight ->
                Text(
                    text = "${insight.headline}: ${insight.explanation}",
                    color = StrideLabSurfaceTokens.amber,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}
