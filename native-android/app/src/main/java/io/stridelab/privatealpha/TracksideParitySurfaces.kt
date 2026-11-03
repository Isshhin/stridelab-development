package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Figma master gallery 551:6 / 736:554. Never hides labels at large font sizes. */
@Composable
internal fun TracksideFloatingNavigation(currentRoute: String?, onNavigate: (String) -> Unit) {
    val roots = listOf(
        Triple("home", "Today", R.drawable.trackside_figma_home),
        Triple("training", "Training", R.drawable.trackside_figma_calendar),
        Triple("activities", "Activity", R.drawable.trackside_figma_activity),
        Triple("library", "Library", R.drawable.trackside_figma_library),
        Triple("settings", "More", R.drawable.trackside_figma_more)
    )
    val radius = RoundedCornerShape(31.dp)
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)
        .height(67.dp).clip(radius)
        .background(Brush.horizontalGradient(listOf(
            TracksidePalette.surfaceRaised, TracksidePalette.surface, TracksidePalette.surfaceLow
        )))
        .border(BorderStroke(0.9.dp, TracksidePalette.outline.copy(alpha = 0.62f)), radius)
        .testTag("trackside-floating-navigation"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        roots.forEach { (route, label, iconRes) ->
            val active = when (route) {
                "activities" -> currentRoute in setOf("activities", "activity/{id}")
                "training" -> currentRoute in setOf("training", "builder/{discipline}", "workout/{planId}/{sessionId}", "manage/{planId}/{date}")
                "library" -> currentRoute in setOf("library", "practice/{key}")
                else -> currentRoute == route
            }
            Box(Modifier.weight(1f).fillMaxHeight()
                .clickable(role = Role.Tab) { onNavigate(route) }
                .testTag("nav-$route").semantics { contentDescription = label },
                contentAlignment = Alignment.Center) {
                if (active) Box(Modifier.width(74.dp).height(56.dp)
                    .background(Color(0x38AE1B36), RoundedCornerShape(26.dp)))
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(iconRes), contentDescription = null,
                        tint = if (active) TracksidePalette.red else TracksidePalette.textMuted,
                        modifier = Modifier.size(19.dp))
                    Text(label, fontSize = 9.8.sp, lineHeight = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) TracksidePalette.red else TracksidePalette.textMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Figma 551:6. Original approved Figma art is layered under real Compose UI. */
@Composable
internal fun TracksideTodayHero(session: PlannedSession, onOpen: () -> Unit) {
    val running = session.discipline == "run"
    val accent = tracksideDisciplineColor(session.discipline)
    val dateLabel = when (session.date) {
        LocalDate.now() -> "Today"
        LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> session.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("today-hero"),
        shape = RoundedCornerShape(23.dp), color = TracksidePalette.surfaceLow,
        border = BorderStroke(1.2.dp, accent.copy(alpha = .62f)), shadowElevation = 8.dp) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(
            TracksidePalette.surfaceRaised, TracksidePalette.surface, TracksidePalette.surfaceLow
        )))) {
            if (running) Image(painterResource(R.drawable.trackside_approved_atmosphere),
                contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(160.dp))
            Column(Modifier.fillMaxWidth().heightIn(min = if (running) 496.dp else 225.dp)
                .padding(horizontal = 13.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (running) Icon(painterResource(R.drawable.trackside_figma_run),
                        contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                    else Box(Modifier.size(26.dp).background(accent.copy(alpha = .35f), RoundedCornerShape(7.dp)))
                    Text(dateLabel, style = MaterialTheme.typography.bodyMedium,
                        color = TracksidePalette.textMuted, modifier = Modifier.weight(1f))
                    Surface(color = TracksidePalette.surfaceRaised.copy(alpha = .9f),
                        shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = .45f))) {
                        Text(disciplineLabel(session.discipline),
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = TracksidePalette.text, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(session.effectiveTitle, color = TracksidePalette.text,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(
                            session.distanceKm?.let { String.format(Locale.getDefault(), "%.1f km", it) },
                            session.kind.replace('_', ' '), "Planned"
                        ).joinToString(" · "), color = TracksidePalette.textMuted,
                            style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    Box(Modifier.size(53.dp).background(accent, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.trackside_figma_play),
                            contentDescription = "Open workout", tint = TracksidePalette.text,
                            modifier = Modifier.size(23.dp))
                    }
                }
                if (running) {
                    // Figma explicitly labels this route artwork illustrative.
                    // It cannot be treated as a real GPX/FIT route or source telemetry.
                    Box(Modifier.fillMaxWidth().height(232.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF102A37)).testTag("today-route-unavailable")) {
                        Image(painterResource(R.drawable.trackside_approved_map_art),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                        Image(painterResource(R.drawable.trackside_approved_route_halo),
                            contentDescription = null, contentScale = ContentScale.FillBounds,
                            modifier = Modifier.align(Alignment.Center).width(139.dp).height(214.dp))
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(49.dp)
                            .background(Brush.verticalGradient(listOf(
                                Color(0x00101B22), Color(0xEA101B22)
                            ))))
                        Text("Illustrative route · not recorded GPS",
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                            style = MaterialTheme.typography.labelSmall, color = TracksidePalette.text)
                    }
                }
                Row(Modifier.fillMaxWidth().height(91.dp)
                    .background(Color(0xC0172935), RoundedCornerShape(13.dp))
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    HeroMetric("DURATION", session.effectiveMinutes.toString(), "min", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(65.dp)
                        .background(TracksidePalette.outline.copy(alpha = .52f)))
                    HeroMetric(if (running) "TARGET HR" else "EXERCISES",
                        if (running) "—" else session.effectiveExercises.size.toString(),
                        if (running) "bpm" else "planned", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(65.dp)
                        .background(TracksidePalette.outline.copy(alpha = .52f)))
                    HeroMetric(if (running) "PACE" else "STATUS",
                        if (running) "—" else session.status.replace('_', ' '),
                        if (running) "/km" else "planned", Modifier.weight(1f))
                }
            }
        }
    }
}

/** Master-gallery 653:842: factual approved-plan Today progress, no inferred completion. */
@Composable
internal fun TracksideTodayActiveSummary(plan: PlanSnapshot, today: List<PlannedSession>) {
    val completed = today.count { it.status == "completed" }
    val total = today.size.coerceAtLeast(1)
    val weekIndex = java.time.temporal.ChronoUnit.WEEKS.between(
        tracksideCalendarMonday(plan.request.start), tracksideCalendarMonday(LocalDate.now())
    ).toInt().plus(1).coerceIn(1, plan.request.weeks.coerceAtLeast(1))
    Surface(modifier = Modifier.fillMaxWidth().testTag("today-active-summary"),
        color = TracksidePalette.surface, shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, TracksidePalette.outline.copy(alpha = .55f))) {
        Column(Modifier.background(Brush.horizontalGradient(listOf(
            Color(0xFF25323D), TracksidePalette.surface, TracksidePalette.surfaceLow
        ))).padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PLAN ACTIVE", color = TracksidePalette.red, fontSize = 11.sp,
                fontWeight = FontWeight.Bold)
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.getDefault()))
                .uppercase(Locale.getDefault()), color = TracksidePalette.textMuted,
                style = MaterialTheme.typography.labelSmall)
            Text("Week $weekIndex of ${plan.request.weeks} · Your approved training",
                color = TracksidePalette.text, style = MaterialTheme.typography.titleLarge)
            Text("${completed} OF ${today.size} COMPLETED",
                color = TracksidePalette.textMuted, style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth().height(9.dp)
                .background(TracksidePalette.outline.copy(alpha = .25f), RoundedCornerShape(5.dp))) {
                if (today.isNotEmpty() && completed > 0) Box(
                    Modifier.fillMaxWidth(completed.toFloat() / total).height(9.dp)
                        .background(TracksidePalette.red, RoundedCornerShape(5.dp)))
            }
            Text(if (completed == today.size) "Today's scheduled sessions completed"
                else "$completed completed · ${today.size-completed} remaining",
                color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = TracksidePalette.textMuted, fontSize = 10.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = TracksidePalette.text, style = MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(unit, color = TracksidePalette.textMuted, fontSize = 11.sp)
    }
}
