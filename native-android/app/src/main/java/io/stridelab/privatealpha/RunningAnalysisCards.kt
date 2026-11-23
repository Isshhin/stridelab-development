package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
private fun RunningAnalysisCard(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(16.dp),
        color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
internal fun TrainingEffectCard(effect: TrainingEffect) {
    RunningAnalysisCard("Training Effect", "activity-training-effect") {
        Text("StrideLab estimate", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        effect.aerobic?.let { TrainingEffectRow("Aerobic", it) }
        effect.anaerobic?.let { TrainingEffectRow("Anaerobic", it) }
    }
}

@Composable
private fun TrainingEffectRow(label: String, score: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AnalysisMetric(label, "${String.format(Locale.ROOT, "%.1f", score)} · ${TrainingEffectCalculator.label(score)}")
        LinearProgressIndicator(progress = { (score / 5).toFloat() },
            modifier = Modifier.fillMaxWidth().height(4.dp))
    }
}

@Composable
internal fun RunningDynamicsCard(summary: RunningDynamicsSummary) {
    val data = summary.values
    RunningAnalysisCard("Running Dynamics", "activity-running-dynamics") {
        data.groundContactMs?.let { AnalysisMetric("Ground contact time", String.format(Locale.ROOT, "%.0f ms", it)) }
        data.verticalOscillationCm?.let { AnalysisMetric("Vertical oscillation", String.format(Locale.ROOT, "%.1f cm", it)) }
        data.stepLengthM?.let { AnalysisMetric(
            if (summary.estimatedStepLength) "Stride length (estimated)" else "Stride length",
            String.format(Locale.ROOT, "%.2f m", it)) }
        data.verticalRatioPct?.let { AnalysisMetric(
            if (summary.estimatedVerticalRatio) "Vertical ratio (estimated)" else "Vertical ratio",
            String.format(Locale.ROOT, "%.1f%%", it)) }
        data.cadenceSpm?.let { AnalysisMetric("Cadence", String.format(Locale.ROOT, "%.0f spm", it)) }
    }
}

@Composable
private fun AnalysisMetric(label: String, value: String) {
    val style = MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = style.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium)
    }
}
