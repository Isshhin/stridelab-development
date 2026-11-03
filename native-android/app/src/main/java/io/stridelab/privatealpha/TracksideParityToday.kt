package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Native, data-safe reconstruction of APPROVED master-gallery Figma 551:6.
 * The asset is the ORIGINAL approved decorative illustration, not a stored GPS route.
 * A real athlete's time/HR/pace is never inferred from the Figma demo.
 */
@Composable
internal fun TracksideApprovedRunningHero(
    session: PlannedSession,
    dateLabel: String,
    onOpen: () -> Unit
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("today-hero"),
        shape = RoundedCornerShape(23.dp),
        color = TracksidePalette.surfaceLow,
        border = BorderStroke(1.2f.dp, Color(0x94FF4A61)),
        shadowElevation = 6.dp
    ) {
        Box(Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(
                Color(0xFF25323D), Color(0xFF101B22), Color(0xFF081219)
            )))) {
            Image(
                painterResource(R.drawable.trackside_today_atmospheric_field),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter)
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(35.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.trackside_figma_run),
                        contentDescription = null,
                        tint = TracksidePalette.running,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(dateLabel, style = MaterialTheme.typography.bodyMedium,
                        color = TracksidePalette.textMuted, modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(17.dp),
                        color = Color(0xFF20313C),
                        border = BorderStroke(1.dp, Color(0x99677987))
                    ) {
                        Text("Run  ›", Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelMedium, color = TracksidePalette.text)
                    }
                }
                Spacer(Modifier.height(13.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(session.effectiveTitle, style = MaterialTheme.typography.headlineMedium,
                        fontSize = 30.sp, color = TracksidePalette.text, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Surface(
                        color = TracksidePalette.running, shape = RoundedCornerShape(25.dp),
                        modifier = Modifier.size(53.dp), onClick = onOpen
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.trackside_figma_play),
                                contentDescription = "Open planned workout",
                                tint = Color.White, modifier = Modifier.size(23.dp))
                        }
                    }
                }
                val distance = session.distanceKm?.let {
                    String.format(Locale.getDefault(), "%.1f km", it)
                } ?: "Distance not set"
                Text(distance + " · " + session.kind.replace('_', ' '),
                    fontSize = 12.sp, lineHeight = 16.sp, color = TracksidePalette.textMuted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                Image(
                    painterResource(R.drawable.trackside_today_illustrative_map),
                    contentDescription = "Figma decorative example route, not an athlete GPS recording",
                    modifier = Modifier.fillMaxWidth().height(232.dp)
                        .testTag("today-map-illustration"),
                    contentScale = ContentScale.FillBounds
                )
                Spacer(Modifier.height(11.dp))
                Row(
                    Modifier.fillMaxWidth().height(91.dp)
                        .background(Color(0x88101B22), RoundedCornerShape(13.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TracksideHeroMetric(
                        label = "TIME", value = session.effectiveMinutes.toString(), unit = "min",
                        icon = R.drawable.trackside_figma_calendar, modifier = Modifier.weight(1.05f)
                    )
                    Box(Modifier.width(1.dp).height(66.dp).background(Color(0x667C94A3)))
                    TracksideHeroMetric(
                        label = "HEART RATE", value = "—", unit = "not prescribed",
                        icon = R.drawable.trackside_figma_activity, modifier = Modifier.weight(0.92f)
                    )
                    Box(Modifier.width(1.dp).height(66.dp).background(Color(0x667C94A3)))
                    TracksideHeroMetric(
                        label = "PACE", value = "—", unit = "not prescribed",
                        icon = R.drawable.trackside_figma_run, modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TracksideHeroMetric(
    label: String, value: String, unit: String, icon: Int, modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null,
                tint = TracksidePalette.textMuted, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 9.sp, color = TracksidePalette.textMuted, maxLines = 1)
        }
        Text(value, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold,
            color = TracksidePalette.text, maxLines = 1)
        Text(unit, fontSize = 10.sp, lineHeight = 12.sp,
            color = TracksidePalette.textMuted, maxLines = 1)
    }
}
