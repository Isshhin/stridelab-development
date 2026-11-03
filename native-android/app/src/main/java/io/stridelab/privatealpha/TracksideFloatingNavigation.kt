package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Approved Figma 551:6 / 736:554: 388×67 floating navigation at a 412px viewport.
 * Custom Compose, not Material3 NavigationBar; existing routes/semantics stay stable.
 */
@Composable
internal fun TracksideFloatingNavigation(route: String?, onTab: (String) -> Unit) {
    val destinations = listOf(
        Triple("home", "Today", R.drawable.trackside_figma_home),
        Triple("training", "Training", R.drawable.trackside_figma_calendar),
        Triple("activities", "Activity", R.drawable.trackside_figma_activity),
        Triple("library", "Library", R.drawable.trackside_figma_library),
        Triple("settings", "More", R.drawable.trackside_figma_more)
    )
    Row(
        Modifier.fillMaxWidth().height(67.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF25323D), Color(0xFF101B22), Color(0xFF081219))
                )
            )
            .border(BorderStroke(0.9.dp, Color(0x99677987)), RoundedCornerShape(31.dp))
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEach { (path, label, drawable) ->
            val selected = route == path ||
                (path == "activities" && route == "activity/{id}") ||
                (path == "training" && route in setOf(
                    "builder/{discipline}", "workout/{planId}/{sessionId}", "manage/{planId}/{date}"
                )) ||
                (path == "library" && route == "practice/{key}")
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (selected) Color(0x38AE1B36) else Color.Transparent)
                    .clickable { onTab(path) }
                    .testTag("nav-" + path)
                    .semantics { contentDescription = label; role = Role.Tab },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(drawable),
                    contentDescription = null,
                    tint = if (selected) TracksidePalette.red else TracksidePalette.textMuted,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    label,
                    fontSize = 9.8.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) TracksidePalette.red else TracksidePalette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
