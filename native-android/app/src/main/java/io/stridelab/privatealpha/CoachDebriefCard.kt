package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun DebriefTone.accent() = when (this) {
    DebriefTone.POSITIVE -> StrideLabSurfaceTokens.lime
    DebriefTone.NEUTRAL -> StrideLabSurfaceTokens.inkSecondary
    DebriefTone.CAUTION -> StrideLabSurfaceTokens.amber
}

private fun DebriefTone.badgeLabel() = when (this) {
    DebriefTone.POSITIVE -> "STRONG EXECUTION"
    DebriefTone.CAUTION -> "WORTH ADJUSTING"
    DebriefTone.NEUTRAL -> "SESSION INSIGHT"
}

@Composable
internal fun CoachDebriefCard(debrief: SessionDebrief) {
    val lead = debrief.insights.firstOrNull() ?: return
    val leadAccent = lead.tone.accent()
    val numbers = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("coach-debrief"),
        shape = RoundedCornerShape(18.dp),
        color = StrideLabSurfaceTokens.surfaceElevated,
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(leadAccent.copy(alpha = 0.07f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DebriefCopyTemplates.eyebrow,
                    color = leadAccent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp
                )
                Surface(
                    color = leadAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = lead.tone.badgeLabel(),
                        color = leadAccent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.7.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = lead.title,
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("coach-debrief-headline")
                )
                Text(
                    text = lead.body,
                    color = StrideLabSurfaceTokens.inkSecondary,
                    style = numbers,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.testTag("coach-debrief-summary")
                )
            }

            if (debrief.insights.size > 1) {
                StrideLabHairline()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    debrief.insights.drop(1).forEachIndexed { index, insight ->
                        val itemAccent = insight.tone.accent()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("coach-debrief-insight-$index"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier
                                    .padding(top = 3.dp)
                                    .width(3.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(itemAccent)
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = insight.title,
                                    color = StrideLabSurfaceTokens.ink,
                                    style = numbers,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = insight.body,
                                    color = StrideLabSurfaceTokens.inkSecondary,
                                    style = numbers,
                                    fontSize = 13.5.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            debrief.paragraphs.forEach { paragraph ->
                Text(
                    text = paragraph,
                    color = StrideLabSurfaceTokens.inkSecondary,
                    style = numbers,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )
            }

            debrief.nextFocus?.takeIf { it.isNotBlank() }?.let { focus ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coach-debrief-next-focus"),
                    shape = RoundedCornerShape(12.dp),
                    color = StrideLabSurfaceTokens.surfaceSubtle,
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "NEXT FOCUS",
                            color = leadAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.0.sp
                        )
                        Text(
                            text = focus,
                            color = StrideLabSurfaceTokens.ink,
                            style = numbers,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}
