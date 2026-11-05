package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Today is always an intentional, designed screen, including before an athlete
 * creates a plan. Only the decorative source artwork is previewed here.
 * Never present sample workout targets or an example route as athlete data.
 */
@Composable
internal fun TracksideTodayNoPlanHero(onCreatePlan: () -> Unit) {
    TracksideTodayEmptyFeature(
        eyebrow = "YOUR TRAINING",
        title = "Your next run starts here",
        description = "Create and approve a training plan to see your upcoming workouts.",
        actionLabel = "Create a training plan",
        onAction = onCreatePlan
    )
}

@Composable
internal fun TracksideTodayNoUpcomingHero(onOpenTraining: () -> Unit) {
    TracksideTodayEmptyFeature(
        eyebrow = "YOUR NEXT SESSION",
        title = "Your schedule is clear",
        description = "There are no upcoming scheduled workouts in your approved plan.",
        actionLabel = "View your training plan",
        onAction = onOpenTraining
    )
}

@Composable
private fun TracksideTodayEmptyFeature(
    eyebrow: String,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("today-empty-feature"),
        color = TracksidePalette.surfaceLow,
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.2.dp, Color(0x94FF4A61)),
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF25323D), Color(0xFF101B22), Color(0xFF081219))
                )
            )
        ) {
            Image(
                painter = painterResource(R.drawable.trackside_today_atmospheric_field),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter)
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(35.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.trackside_figma_run),
                        contentDescription = null,
                        tint = TracksidePalette.running,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        eyebrow,
                        style = MaterialTheme.typography.labelMedium,
                        color = TracksidePalette.textMuted,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Surface(
                        color = Color(0xFF20313C),
                        shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, Color(0x99677987))
                    ) {
                        Text(
                            "Run  ›",
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                            color = TracksidePalette.text,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    title,
                    color = TracksidePalette.text,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    description,
                    color = TracksidePalette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(17.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(232.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFF09202B))
                        .testTag("today-empty-illustration")
                ) {
                    Image(
                        painter = painterResource(R.drawable.trackside_today_illustrative_map),
                        contentDescription = "Decorative illustration, not an athlete GPS route",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(53.dp).align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xF0051019))
                                )
                            )
                    ) {
                        Text(
                            "Illustrative preview · Not recorded GPS",
                            modifier = Modifier.align(Alignment.BottomStart)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            color = TracksidePalette.text,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().height(53.dp).testTag("today-plan-action"),
                    colors = ButtonDefaults.buttonColors(containerColor = TracksidePalette.red),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** The compact Today row is visible even when there is no approved schedule. */
@Composable
internal fun TracksideTodayEmptySession(
    hasApprovedPlan: Boolean,
    onOpenTraining: () -> Unit
) {
    Surface(
        onClick = onOpenTraining,
        modifier = Modifier.fillMaxWidth().testTag("today-empty-session"),
        shape = RoundedCornerShape(13.dp),
        color = TracksidePalette.surface,
        border = BorderStroke(1.dp, TracksidePalette.outline.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.trackside_figma_run),
                contentDescription = null,
                tint = TracksidePalette.running,
                modifier = Modifier.size(27.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    if (hasApprovedPlan) "No workout scheduled today" else "No workouts yet",
                    color = TracksidePalette.text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (hasApprovedPlan) "View your training schedule" else "Create a plan to get started",
                    color = TracksidePalette.textMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text("›", color = TracksidePalette.textMuted, style = MaterialTheme.typography.titleLarge)
        }
    }
}
