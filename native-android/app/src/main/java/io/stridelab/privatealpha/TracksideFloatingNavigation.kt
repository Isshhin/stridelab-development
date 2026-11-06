package io.stridelab.privatealpha

import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 * The same four navigation destinations, now with one persistent sliding pill.
 * Library is still reachable from More, and existing destination callbacks stay intact.
 */
@Composable
internal fun TracksideFloatingNavigation(route: String?, onTab: (String) -> Unit) {
    val destinations = listOf(
        Triple("home", "Today", R.drawable.trackside_figma_home),
        Triple("training", "Training", R.drawable.trackside_figma_calendar),
        Triple("activities", "Activity", R.drawable.trackside_figma_activity),
        Triple("settings", "More", R.drawable.trackside_figma_more)
    )
    val selectedIndex = when {
        route == "home" -> 0
        route == "training" || route in setOf(
            "builder/{discipline}", "workout/{planId}/{sessionId}",
            "manage/{planId}/{date}", "move/{planId}/{sessionId}",
            "review-move/{planId}/{sessionId}/{date}",
            "missed/{planId}/{sessionId}"
        ) -> 1
        route == "activities" || route == "activity/{id}" -> 2
        route == "settings" || route == "library" || route == "practice/{key}" -> 3
        else -> -1
    }
    val context = LocalContext.current
    val animationsDisabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val lime = Color(0xFFD8FB70)

    // Keep the original rounded dock pill and the sliding lime indicator.
    // Only the area OUTSIDE this capsule is transparent, with no tinted
    // footer, surrounding slab, or full-width bottom-bar background.
    // Slightly larger dock aligned with the cards. The capsule itself remains
    // the ONLY dark nav surface; there is no footer or backdrop behind it.
    val dockShape = RoundedCornerShape(36.dp)
    Box(
        modifier = Modifier.fillMaxWidth().height(68.dp)
            .clip(dockShape)
            .background(Color(0xFF17191A))
            .border(1.dp, Color(0xFF464B4C), dockShape)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cellWidth = maxWidth / destinations.size
            val pillOffset by animateDpAsState(
                targetValue = if (selectedIndex < 0) 0.dp else cellWidth * selectedIndex,
                animationSpec = tween(if (animationsDisabled) 0 else 260),
                label = "floating-nav-pill"
            )
            if (selectedIndex >= 0) {
                Box(
                    modifier = Modifier.offset(x = pillOffset)
                        .width(cellWidth).fillMaxHeight()
                        .clip(RoundedCornerShape(30.dp))
                        .background(lime)
                        .testTag("nav-selected-indicator")
                )
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                destinations.forEachIndexed { index, (path, label, drawable) ->
                    val isSelected = selectedIndex == index
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(30.dp))
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
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            label,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF10120D) else Color(0xFFC0C4C6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
