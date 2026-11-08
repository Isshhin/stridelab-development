package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Presentation-only composition patterns from the current Today/More/Activity UI.
 * Do not swap the app-wide legacy palette globally: older flows and discipline
 * meanings need independent review, and saved engine data must not be touched.
 */
internal object StrideWorkoutVisuals {
    val black = Color(0xFF000000)
    val card = Color(0xFF111315)
    val divider = Color(0xFF303438)
    val white = Color(0xFFF7F7F5)
    val muted = Color(0xFF9FA4AA)
    val lime = Color(0xFFD8FB70)
}

@Composable
internal fun StrideWorkoutDetailFrame(
    title: String,
    discipline: String,
    status: String,
    dateLabel: String,
    metrics: List<Pair<String, String>>,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    val ink = StrideWorkoutVisuals.white
    val subtle = StrideWorkoutVisuals.muted
    val lime = StrideWorkoutVisuals.lime
    MaterialTheme(
        colorScheme=MaterialTheme.colorScheme.copy(
            primary=lime,
            onPrimary=Color(0xFF10120D),
            surface=StrideWorkoutVisuals.card,
            onSurface=ink,
            onSurfaceVariant=subtle,
            outline=StrideWorkoutVisuals.divider,
            surfaceVariant=StrideWorkoutVisuals.card
        )
    ) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StrideWorkoutVisuals.black,
        contentColor = ink
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("screen").semantics { paneTitle = "Workout Details" },
            contentPadding = PaddingValues(
                start = 22.dp, end = 22.dp, top = 8.dp,
                bottom = if(LocalFloatingDockVisible.current) 112.dp else 36.dp
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item(key = "workout-header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Training", tint = ink)
                    }
                    Text(
                        "Workout Details",
                        style = MaterialTheme.typography.titleLarge,
                        color = ink,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            item(key = "workout-overview") {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(18.dp).height(4.dp)
                                .background(lime, RoundedCornerShape(12.dp))
                        )
                        Text(
                            discipline.uppercase() + "  ·  " + status.uppercase(),
                            color = subtle,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 11.dp)
                        )
                    }
                    Text(
                        title, color = ink,
                        fontSize = 30.sp, lineHeight = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(dateLabel, color = subtle, style = MaterialTheme.typography.bodyMedium)
                    if (metrics.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            metrics.take(3).forEachIndexed { index, metric ->
                                if(index > 0) {
                                    VerticalDivider(
                                        Modifier.height(43.dp).padding(horizontal = 12.dp),
                                        color = StrideWorkoutVisuals.divider
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(metric.second, fontSize = 22.sp, lineHeight = 26.sp,
                                        color = ink, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(metric.first, style = MaterialTheme.typography.labelSmall, color = subtle,
                                        maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
            content()
        }
    }
    }
}

@Composable
internal fun StrideWorkoutSection(
    heading: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth(),verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HorizontalDivider(color = StrideWorkoutVisuals.divider)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Box(Modifier.width(21.dp).height(4.dp).background(
                StrideWorkoutVisuals.lime, RoundedCornerShape(10.dp)))
            Text(
                heading, color = StrideWorkoutVisuals.white,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 13.dp)
            )
        }
        content()
    }
}

@Composable
internal fun StrideWorkoutTimeline(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        val visible=steps.filter { it.isNotBlank() }
        visible.forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.width(22.dp).padding(top = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(9.dp).background(
                        StrideWorkoutVisuals.lime, RoundedCornerShape(50)))
                    if(i < visible.lastIndex)
                        Box(Modifier.width(1.dp).weight(1f).padding(top = 5.dp)
                            .background(StrideWorkoutVisuals.lime.copy(alpha = 0.55f)))
                }
                Column(
                    Modifier.weight(1f).padding(start = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("STEP ${i+1}", style = MaterialTheme.typography.labelSmall,
                        color = StrideWorkoutVisuals.muted)
                    Text(step, style = MaterialTheme.typography.bodyLarge,
                        color = StrideWorkoutVisuals.white)
                }
            }
        }
    }
}

@Composable
internal fun StrideWorkoutPrimaryButton(
    label: String, onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StrideWorkoutVisuals.lime,
            contentColor = Color(0xFF10120D),
            disabledContainerColor = Color(0xFF343D2C),
            disabledContentColor = StrideWorkoutVisuals.muted
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}
