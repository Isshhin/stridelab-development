package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp

/**
 * Activity hero. The street map uses real source coordinates through MapLibre.
 * No recorded GPS means no map location or illustrative/fake route is shown.
 */
@Composable
internal fun TracksideParityActivityMapHero(
    detail: ActivityDetail?,
    onBack: () -> Unit,
    heightDp: Dp = 425.dp,
    online:Boolean = true,
    gpsSourceLabel:String? = null,
    fullScreen:Boolean = false,
    mapMode:TracksideActivityMapMode = TracksideActivityMapMode.ROUTE,
    onMapMode:(TracksideActivityMapMode) -> Unit = {},
    onToggleFullScreen:() -> Unit = {},
    selectedSeconds:Double? = null,
    selectedRange:Pair<Double,Double>? = null,
    onSelectRange:(Pair<Double,Double>?) -> Unit = {},
    onShare:(() -> Unit)? = null,
    onEdit:(() -> Unit)? = null
) {
    val route=remember(detail) {
        RecordedGpsRoute.from(
            detail?.samples.orEmpty(),
            detail?.pauseIntervals.orEmpty()
        )
    }
    Box(Modifier.fillMaxWidth().height(heightDp).testTag("activity-map-hero")) {
        if(route.pointCount > 0) {
            TracksideGeographicMap(
                route, online, Modifier.fillMaxSize(),
                selectedSeconds = selectedSeconds,
                laps = detail?.laps.orEmpty(),
                samples = detail?.samples.orEmpty(),
                mapMode = mapMode,
                selectedRange = selectedRange,
                onSelectRange = onSelectRange
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0xFF131C17),Color(0xFF080D0C)))))
        }
        // Keep actual vector streets crisp. The old full-screen tint was
        // darkening even the lighter road/label palette; only shade the empty
        // no-GPS placeholder, not a genuine MapLibre map.
        if (route.pointCount == 0) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0x28070B09), Color.Transparent, Color(0x14070B09))
            )))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack, shape = RoundedCornerShape(25.dp),
                color = Color(0xDD080E0C), border = BorderStroke(1.dp, Color(0x5546574F)),
                modifier = Modifier.size(44.dp).testTag("activity-map-back")
            ) { Box(contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 25.sp, color = TracksidePalette.text)
            } }
            Spacer(Modifier.weight(1f))
            if (onEdit != null) {
                ActivityMapHeaderAction(
                    label = "Edit",
                    tag = "activity-edit",
                    onClick = onEdit
                )
                Spacer(Modifier.width(8.dp))
            }
            if (onShare != null) {
                ActivityMapHeaderAction(
                    label = "Share",
                    tag = "activity-share",
                    onClick = onShare,
                    shareIcon = true
                )
            }
            // Full-screen map is entered by dragging the activity sheet down.
            // The bottom map-only grip restores the summary; a second top-right
            // exit button crowded the header and duplicated that control.
        }
        if (fullScreen && route.pointCount > 0) {
            val samples=detail?.samples.orEmpty()
            val availableModes=buildList {
                add(TracksideActivityMapMode.ROUTE)
                if(samples.count { it.latitude != null && it.longitude != null && it.pace != null } >= 2)
                    add(TracksideActivityMapMode.PACE)
                if(samples.count { it.latitude != null && it.longitude != null && it.altitude != null } >= 2)
                    add(TracksideActivityMapMode.ELEVATION)
                if(samples.count { it.latitude != null && it.longitude != null && it.hr != null } >= 2)
                    add(TracksideActivityMapMode.HEART_RATE)
            }
            Surface(
                color = Color(0xE6080E0C),
                border = BorderStroke(1.dp, Color(0x5546574F)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 68.dp)
                    .testTag("activity-map-mode-selector")
            ) {
                Row(
                    Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    availableModes.forEach { mode ->
                        Surface(
                            onClick = { onMapMode(mode) },
                            shape = RoundedCornerShape(17.dp),
                            color = if (mapMode == mode) Color(0xFFD8FB70)
                                else Color.Transparent,
                            modifier = Modifier.testTag(
                                "activity-map-mode-" + mode.name.lowercase()
                            )
                        ) {
                            Text(
                                mode.label,
                                color = if (mapMode == mode) Color(0xFF080D0C)
                                    else Color(0xFFD7E0DB),
                                fontSize = 10.sp,
                                fontWeight = if (mapMode == mode)
                                    FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
        if (fullScreen && mapMode != TracksideActivityMapMode.ROUTE) {
            Surface(
                color = Color(0xE6080E0C),
                border = BorderStroke(1.dp, Color(0x445A6B62)),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 36.dp)
                    .testTag("activity-map-analysis-legend")
            ) {
                Column(
                    Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        when(mapMode) {
                            TracksideActivityMapMode.PACE -> "Recorded pace"
                            TracksideActivityMapMode.ELEVATION -> "Recorded elevation"
                            TracksideActivityMapMode.HEART_RATE -> "Recorded heart rate"
                            TracksideActivityMapMode.ROUTE -> "Recorded route"
                        },
                        color = Color(0xFFE5ECE8), fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        Modifier.width(94.dp).height(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    when(mapMode) {
                                        TracksideActivityMapMode.PACE -> listOf(
                                            Color(0xFFD8FB70), Color(0xFFFFBE7A), Color(0xFFFF8C87)
                                        )
                                        TracksideActivityMapMode.ELEVATION -> listOf(
                                            Color(0xFF5EABD6), Color(0xFFC2A8FF), Color(0xFFFFBE7A)
                                        )
                                        TracksideActivityMapMode.HEART_RATE -> listOf(
                                            Color(0xFF79C9B3), Color(0xFFD8FB70), Color(0xFFFF8C87)
                                        )
                                        TracksideActivityMapMode.ROUTE -> listOf(
                                            Color(0xFFD8FB70), Color(0xFFD8FB70)
                                        )
                                    }
                                ),
                                RoundedCornerShape(3.dp)
                            )
                    )
                    Row(
                        Modifier.width(94.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if(mapMode == TracksideActivityMapMode.PACE) "Faster" else "Lower",
                            color = Color(0xFFB8C5BE), fontSize = 7.5.sp
                        )
                        Text(
                            if(mapMode == TracksideActivityMapMode.PACE) "Slower" else "Higher",
                            color = Color(0xFFB8C5BE), fontSize = 7.5.sp
                        )
                    }
                }
            }
        }
        // GPS continuity remains available in Activity Overview. Do not render
        // an overlay on the map: pause fixes and source GPS remain unchanged.
        if (route.pointCount == 0) {
            Text(
                "No recorded GPS track",
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xCC080D0C), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                color = Color(0xFFB4C1BA),
                fontSize = 12.sp
            )
        }
    }
}

/** Consistent AMOLED activity actions, independent of the map sheet state. */
@Composable
internal fun ActivityMapHeaderAction(
    label: String,
    tag: String,
    onClick: () -> Unit,
    shareIcon: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEF080E0C),
        border = BorderStroke(1.dp, Color(0x66536A5B)),
        modifier = Modifier.height(44.dp).testTag(tag)
            .semantics { contentDescription = if (shareIcon) "Share activity" else "Edit activity" }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (shareIcon) {
                Canvas(Modifier.size(18.dp)) {
                    val lime = Color(0xFFD8FB70)
                    val stroke = 2.dp.toPx()
                    val w = size.width
                    val h = size.height
                    drawLine(lime, Offset(w * .5f, h * .08f), Offset(w * .5f, h * .60f), stroke, cap = StrokeCap.Round)
                    drawLine(lime, Offset(w * .30f, h * .29f), Offset(w * .5f, h * .08f), stroke, cap = StrokeCap.Round)
                    drawLine(lime, Offset(w * .70f, h * .29f), Offset(w * .5f, h * .08f), stroke, cap = StrokeCap.Round)
                    drawLine(lime, Offset(w * .18f, h * .50f), Offset(w * .18f, h * .88f), stroke, cap = StrokeCap.Round)
                    drawLine(lime, Offset(w * .18f, h * .88f), Offset(w * .82f, h * .88f), stroke, cap = StrokeCap.Round)
                    drawLine(lime, Offset(w * .82f, h * .88f), Offset(w * .82f, h * .50f), stroke, cap = StrokeCap.Round)
                }
            } else {
                Text("✎", color = Color(0xFFD8FB70), fontSize = 20.sp, lineHeight = 20.sp)
            }
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TracksidePalette.text,
                maxLines = 1
            )
        }
    }
}

/** Approved Figma 551:235: activity summary within the premium glass sheet. */
