package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun DebriefTone.accent() = when (this) {
    DebriefTone.POSITIVE -> Color(0xFFD8FB70)
    DebriefTone.NEUTRAL -> Color(0xFF94A3B8)
    DebriefTone.CAUTION -> Color(0xFFD6AE6A)
}

@Composable
internal fun CoachDebriefCard(debrief: SessionDebrief) {
    val lead = debrief.insights.firstOrNull() ?: return
    val shape = RoundedCornerShape(16.dp)
    val title = Color(0xFFF1F5F9)
    val body = Color(0xFFCBD5E1)
    val numbers = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF14181C), shape)
            .border(1.dp, Color(0xFF232A31), shape)
            .testTag("coach-debrief")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(DebriefCopyTemplates.eyebrow, color = lead.tone.accent(), fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text(lead.title, color = title, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() }
                .testTag("coach-debrief-headline"))
        Text(lead.body, color = body, style = numbers, fontSize = 14.sp,
            modifier = Modifier.testTag("coach-debrief-summary"))
        debrief.insights.drop(1).forEachIndexed { index, insight ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    .testTag("coach-debrief-insight-$index"),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).background(insight.tone.accent(), CircleShape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(insight.title, color = title, style = numbers, fontWeight = FontWeight.Bold)
                    Text(insight.body, color = body, style = numbers)
                }
            }
        }
        debrief.paragraphs.forEach { paragraph ->
            Text(paragraph, color = body, style = numbers, lineHeight = 21.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}
