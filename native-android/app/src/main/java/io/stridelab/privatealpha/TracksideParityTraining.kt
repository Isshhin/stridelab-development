package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Figma master gallery 614:500 (draft) and 795:839 (active).
 * Day/Agenda is the approved visible control; month remains a separate existing route,
 * not an unapproved third segment inserted into this 162×44 switch.
 */
@Composable
internal fun TracksideParityTrainingControls(
    agenda: Boolean, draft: Boolean, hasPlan: Boolean,
    onDay: () -> Unit, onAgenda: () -> Unit, onManage: () -> Unit,
    plan: PlanSnapshot? = null,
    selected: LocalDate? = null,
    onSelectWeek: (LocalDate) -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            Modifier.width(162.dp).height(44.dp)
                .background(Color(0xFF14232D), RoundedCornerShape(12.dp))
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(Triple("Day", !agenda, onDay), Triple("Agenda", agenda, onAgenda)).forEach {
                (label, selected, action) ->
                Surface(
                    onClick = action,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .testTag(if (label == "Day") "training-view-day" else "training-view-agenda"),
                    color = if (selected) Color(0xFFF6F7F9) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold,
                            color = if (selected) Color.Black else Color(0xFFA2A4AF))
                    }
                }
            }
        }
        if (draft && plan != null && selected != null) {
            val first = plan.request.start
            val last = first.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
            val firstMonday = tracksideCalendarMonday(first)
            val weekStart = tracksideCalendarMonday(selected)
            val lastMonday = tracksideCalendarMonday(last)
            val weekIndex = ChronoUnit.WEEKS.between(firstMonday, weekStart).toInt() + 1
            val totalWeeks = ChronoUnit.WEEKS.between(firstMonday, lastMonday).toInt() + 1
            val weekEnd = weekStart.plusDays(6)
            val span = if (weekStart.month == weekEnd.month)
                "${weekStart.dayOfMonth}–${weekEnd.dayOfMonth} " +
                    weekEnd.format(DateTimeFormatter.ofPattern("MMM"))
            else "${weekStart.format(DateTimeFormatter.ofPattern("d MMM"))} – " +
                weekEnd.format(DateTimeFormatter.ofPattern("d MMM"))

            Row(
                Modifier.weight(1f).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onSelectWeek(maxOf(first, weekStart.minusWeeks(1))) },
                    enabled = weekStart > firstMonday,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(30.dp).testTag("training-prev-week")
                ) {
                    Text("‹", fontSize = 21.sp, color = TracksidePalette.text)
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        span,
                        fontSize = 14.8f.sp, lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TracksidePalette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("training-week-span")
                    )
                    Text(
                        "Week $weekIndex of $totalWeeks",
                        fontSize = 10.6f.sp, lineHeight = 13.sp,
                        color = TracksidePalette.textMuted,
                        maxLines = 1,
                        modifier = Modifier.testTag("training-week-counter")
                    )
                }
                TextButton(
                    onClick = { onSelectWeek(minOf(last, weekStart.plusWeeks(1))) },
                    enabled = weekStart < lastMonday,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(30.dp).testTag("training-next-week")
                ) {
                    Text("›", fontSize = 21.sp, color = TracksidePalette.text)
                }
            }
        } else if (draft) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, TracksidePalette.text),
                color = Color.Transparent
            ) {
                Text("DRAFT", Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    fontSize = 10.6f.sp, color = TracksidePalette.text,
                    fontWeight = FontWeight.Bold)
            }
        } else if (hasPlan) TextButton(onClick = onManage,
            modifier = Modifier.testTag("training-manage-plan")) {
            Text("Manage ›", color = TracksidePalette.text, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Compact Agenda rows from the approved draft/active Training states.
 * Day view keeps the larger workout cards below; only Agenda uses this density.
 */
@Composable
internal fun TracksideParityAgendaDay(
    day: TracksideTrainingDay,
    hasWorkoutDraft: (String) -> Boolean,
    onOpen: (String) -> Unit
) {
    if (day.sessions.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().testTag("training-agenda-day-" + day.date),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        day.sessions.forEachIndexed { index, session ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == 0) {
                    Column(
                        Modifier.width(34.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            day.date.dayOfWeek.name.take(3),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TracksidePalette.textMuted
                        )
                        Text(
                            day.date.dayOfMonth.toString(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TracksidePalette.text
                        )
                    }
                } else {
                    Spacer(Modifier.width(34.dp))
                }
                Spacer(Modifier.width(7.dp))
                TracksideParityAgendaWorkoutRow(
                    session = session,
                    hasDraft = hasWorkoutDraft(session.id),
                    onOpen = { onOpen(session.id) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TracksideParityAgendaWorkoutRow(
    session: PlannedSession,
    hasDraft: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = tracksideDisciplineColor(session.discipline)
    val detail = if (session.discipline == "run") {
        listOfNotNull(
            session.distanceKm?.let {
                String.format(Locale.getDefault(), "%.0f km", it)
            },
            session.effectiveMinutes.toString() + " min"
        ).joinToString(" · ")
    } else {
        session.effectiveMinutes.toString() + " min"
    }
    val iconRes = when (session.discipline) {
        "run" -> R.drawable.trackside_discipline_running
        "str" -> R.drawable.trackside_discipline_strength
        "cal" -> R.drawable.trackside_discipline_calisthenics
        else -> R.drawable.trackside_discipline_conditioning
    }

    Surface(
        onClick = onOpen,
        modifier = modifier.heightIn(min = 44.dp)
            .testTag("workout-" + session.id),
        shape = RoundedCornerShape(11.dp),
        color = TracksidePalette.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.13f), Color.Transparent)
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(29.dp)
                    .background(accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                session.effectiveTitle,
                modifier = Modifier.weight(1f),
                fontSize = 11.5f.sp,
                fontWeight = FontWeight.Bold,
                color = TracksidePalette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Text(
                detail,
                fontSize = 9.5f.sp,
                color = TracksidePalette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasDraft) {
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier.size(6.dp)
                        .background(TracksidePalette.red, RoundedCornerShape(3.dp))
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.width(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.trackside_draft_move),
                    contentDescription = "Move session",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Approved running card 614:500 x17/y380/w378/h155 and multi-exercise card y548/h232.
 * All labels and segments below are rendered from actual saved plan prescriptions.
 */
@Composable
internal fun TracksideParityWorkoutCard(
    session: PlannedSession,
    hasDraft: Boolean,
    onOpen: () -> Unit
) {
    val accent = tracksideDisciplineColor(session.discipline)
    val isRun = session.discipline == "run"
    val detail = if (isRun) {
        listOfNotNull(
            session.distanceKm?.let { String.format(Locale.getDefault(), "%.1f km", it) },
            session.effectiveMinutes.toString() + " min",
            session.kind.replace('_', ' ')
        ).joinToString(" · ")
    } else session.effectiveMinutes.toString() + " min · " +
        session.effectiveExercises.size.toString() + " exercises"
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = if (isRun) 155.dp else 232.dp)
            .testTag("workout-" + session.id),
        shape = RoundedCornerShape(14.dp),
        color = TracksidePalette.surface,
        border = BorderStroke(1.dp, Color(0x6658798C))
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.09f), Color.Transparent)))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp)
                        .background(accent.copy(alpha = .18f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRun) Icon(painterResource(R.drawable.trackside_figma_run), null,
                        tint = accent, modifier = Modifier.size(24.dp))
                    else Text(disciplineLabel(session.discipline).take(1), color = accent,
                        style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.effectiveTitle, fontSize = 16.sp,
                        color = TracksidePalette.text, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, fontSize = 11.sp, color = TracksidePalette.textMuted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("Open ›", fontSize = 12.sp, color = TracksidePalette.text)
            }
            if (session.kind == "rest" || session.kind == "time only") {
                Text(session.reason, style = MaterialTheme.typography.bodySmall, color = TracksidePalette.textMuted)
            } else if (isRun) {
                val stages = session.steps.take(3).filter { it.isNotBlank() }
                Row(
                    Modifier.fillMaxWidth().height(25.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (stages.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxHeight()
                            .background(accent.copy(alpha = .85f), RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center) {
                            Text("Planned run", fontSize = 10.sp, color = Color.White)
                        }
                    } else {
                        stages.forEachIndexed { i, stage ->
                            Box(Modifier.weight(if (i == 1) 4f else 1f).fillMaxHeight()
                                .background(accent.copy(alpha = if (i == 1) 0.9f else 0.35f),
                                    RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                                Text(stage.take(22), fontSize = 9.sp,
                                    color = TracksidePalette.text,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Text("Prescribed workout · tap for full steps",
                    fontSize = 10.sp, color = TracksidePalette.textMuted)
            } else {
                session.effectiveExercises.take(4).forEach { exercise ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(exercise.name, modifier = Modifier.weight(1f),
                            fontSize = 12.sp, color = TracksidePalette.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(exercise.sets.toString() + " × " +
                            (exercise.reps?.toString() ?: exercise.seconds?.let { it.toString() + "s" } ?: "—"),
                            fontSize = 12.sp, color = accent)
                    }
                }
            }
            if (hasDraft) Text("Saved workout draft · progress not submitted",
                fontSize = 11.sp, color = TracksidePalette.red)
            if (session.suggestion != null && !session.suggestion.accepted)
                Text("Recovery suggestion requires your review",
                    fontSize = 11.sp, color = TracksidePalette.red)
        }
    }
}
