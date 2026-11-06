package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Four-tab, always-floating AMOLED dock: Today, Training, Activity, More.
 * The library has not been deleted: it remains reachable from More.
 * All destination routes and navigation actions remain unchanged.
 */
@Composable
internal fun TracksideFloatingNavigation(route: String?, onTab: (String) -> Unit) {
    val destinations = listOf(
        Triple("home", "Today", R.drawable.trackside_figma_home),
        Triple("training", "Training", R.drawable.trackside_figma_calendar),
        Triple("activities", "Activity", R.drawable.trackside_figma_activity),
        Triple("settings", "More", R.drawable.trackside_figma_more)
    )
    val lime = Color(0xFFD8FB70)
    Row(
        modifier = Modifier.fillMaxWidth().height(69.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF222528), Color(0xFF151719), Color(0xFF111315))
                )
            )
            .border(
                BorderStroke(1.dp, Color(0xFF45484B)),
                RoundedCornerShape(36.dp)
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEach { (path, label, drawable) ->
            val isSelected = route == path ||
                (path == "activities" && route == "activity/{id}") ||
                (path == "training" && route in setOf(
                    "builder/{discipline}", "workout/{planId}/{sessionId}",
                    "manage/{planId}/{date}", "move/{planId}/{sessionId}",
                    "review-move/{planId}/{sessionId}/{date}",
                    "missed/{planId}/{sessionId}"
                )) ||
                (path == "settings" && route in setOf("library", "practice/{key}"))
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (isSelected) lime else Color.Transparent)
                    .clickable { onTab(path) }
                    .testTag("nav-" + path)
                    .semantics {
                        contentDescription = label
                        role = Role.Tab
                        selected = isSelected
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(drawable),
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF10120D) else Color(0xFFC0C4C6),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF10120D) else Color(0xFFC0C4C6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
