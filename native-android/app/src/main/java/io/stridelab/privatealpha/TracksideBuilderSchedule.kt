package io.stridelab.privatealpha

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Refined obsidian icon badge for date selector. */
@Composable
private fun PlanDatesIconTile(icon: Int, tag: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PlanBuilderPalette.accent.copy(alpha = 0.14f))
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = PlanBuilderPalette.accent
        )
    }
}

/**
 * Art-directed Plan Dates & Inline Schedule screen.
 * Pure AMOLED obsidian surfaces, visual Start -> Finish timeline bar,
 * high-contrast obsidian-on-lime active length pills, zero dead-end dialogs.
 */
@Composable
internal fun TracksidePlanDetailsScreen(
    start: String, weeks: String, minutes: String,
    onStart: (String) -> Unit, onWeeks: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onBack: () -> Unit, onContinue: () -> Unit,
    dailyBudget: String = "90",
    multiSession: Boolean = false,
    onDailyBudget: (String) -> Unit = {},
    generationBusy: Boolean = false,
    scheduleValid: Boolean = true,
    scheduleContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    var customWeeks by rememberSaveable { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(start) }.getOrDefault(LocalDate.now())
    val weekCount = weeks.toIntOrNull()?.coerceIn(1, 16) ?: 8
    val endDate = date.plusWeeks(weekCount.toLong()).minusDays(1)
    val shortDateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val fullDateFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

    TracksideBuilderFrame(
        progressStep = 5,
        eyebrow = "PLAN / SCHEDULE",
        toolbarTitle = "Schedule",
        headline = "Dates & weekly\nschedule",
        description = "Set your block timeline and review your weekly slots before generating.",
        ctaLabel = if (generationBusy) "Building your plan…" else "Generate My Plan",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = scheduleValid && !generationBusy &&
            weeks.toIntOrNull() in 1..16 && !date.isBefore(LocalDate.now()),
        descriptionBottomGap = 16,
        contentTopPadding = 12,
        headlineLineHeight = 34
    ) {
        // Hero Timeline Card: Start Date -> End Date visual span
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = PlanBuilderPalette.surface,
            border = BorderStroke(1.dp, PlanBuilderPalette.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(PlanBuilderPalette.accent.copy(alpha = 0.05f), Color.Transparent)
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TracksideFormLabel("TRAINING BLOCK")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PlanBuilderPalette.selected)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "$weekCount WEEKS",
                            fontSize = 10.sp,
                            letterSpacing = 0.9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PlanBuilderPalette.accent
                        )
                    }
                }

                // Tappable Start Date control
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 62.dp)
                        .testTag("builder-start-date"),
                    color = PlanBuilderPalette.surfaceRaised,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PlanBuilderPalette.borderStrong),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> onStart(LocalDate.of(y, m + 1, d).toString()) },
                            date.year, date.monthValue - 1, date.dayOfMonth
                        ).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PlanDatesIconTile(R.drawable.trackside_plan_dates_calendar, "builder-date-calendar-vector")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "START DATE",
                                fontSize = 9.5.sp,
                                letterSpacing = 1.1.sp,
                                color = PlanBuilderPalette.muted,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                date.format(fullDateFmt),
                                fontSize = 14.5.sp,
                                color = PlanBuilderPalette.text,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("›", color = PlanBuilderPalette.secondary, fontSize = 20.sp)
                    }
                }

                // Visual Start -> Finish gradient track
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(StrideLabVisualPalette.easyTeal)
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            StrideLabVisualPalette.easyTeal,
                                            PlanBuilderPalette.accent
                                        )
                                    )
                                )
                        )
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(PlanBuilderPalette.accent)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Start · ${date.format(shortDateFmt)}",
                            fontSize = 11.sp,
                            color = PlanBuilderPalette.secondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Finish · ${endDate.format(shortDateFmt)}",
                            fontSize = 11.sp,
                            color = PlanBuilderPalette.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Plan Length Selector Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PlanBuilderPalette.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, PlanBuilderPalette.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TracksideFormLabel("PLAN LENGTH")
                    Text(
                        "1 – 16 weeks",
                        fontSize = 10.5.sp,
                        color = PlanBuilderPalette.muted
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("4", "8", "12", "16", "Custom").forEach { option ->
                        val active = if (option == "Custom") customWeeks
                        else weeks == option && !customWeeks
                        Surface(
                            onClick = {
                                if (option == "Custom") customWeeks = true
                                else {
                                    customWeeks = false
                                    onWeeks(option)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("builder-weeks-$option"),
                            color = if (active) PlanBuilderPalette.accent else PlanBuilderPalette.surfaceRaised,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (active) PlanBuilderPalette.accent else PlanBuilderPalette.border
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (option == "Custom") "Custom" else "${option}w",
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (active) PlanBuilderPalette.accentOn else PlanBuilderPalette.text
                                )
                            }
                        }
                    }
                }
                if (customWeeks) {
                    OutlinedTextField(
                        value = weeks,
                        onValueChange = { onWeeks(it.filter(Char::isDigit).take(2)) },
                        label = { Text("Custom weeks (1–16)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("builder-custom-weeks"),
                        singleLine = true
                    )
                }
            }
        }

        scheduleContent?.invoke()

        Spacer(Modifier.height(12.dp))
        Text(
            "Generates an editable draft — you can inspect every workout before activating.",
            fontSize = 11.5.sp,
            color = PlanBuilderPalette.muted,
            modifier = Modifier.testTag("builder-generate-draft-note")
        )
    }
}

/**
 * Standalone Availability Screen (preserved for compatibility; zero same-day restriction toggle).
 */
@Composable
internal fun TracksideAvailabilityScreen(
    request: PlanRequest,
    onDay: (Int) -> Unit,
    onRemovePinnedDay: (Int) -> Unit = {},
    onMultiSessionToggle: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onContinue: () -> Unit,
    selectedOtherDiscipline: String = "str",
    selectedOtherSlot: Int = 0,
    onSelectOtherDiscipline: (String) -> Unit = {},
    onSelectOtherSlot: (Int) -> Unit = {},
    onAssignOtherDay: (String, Int, Int) -> Unit = { _, _, _ -> },
    onClearOtherSlot: (String, Int) -> Unit = { _, _ -> },
    onAutoOther: (String) -> Unit = {},
    onCountOther: (String, Int) -> Unit = { _, _ -> },
    dailyBudget: String = "90",
    onDailyBudget: (String) -> Unit = {},
    generationBusy: Boolean = false,
    generationError: String? = null,
    researchContent: (@Composable () -> Unit)? = null
) {
    var pendingRemovalDay by rememberSaveable { mutableIntStateOf(0) }
    val chosen = request.days.size
    val total = request.runDays + request.strengthDays + request.calDays + request.conditioningDays
    val requiredDoubleDays = (total - chosen).coerceAtLeast(0)
    val assignments = request.weeklySlots.flatMap { (discipline, slots) ->
        slots.mapIndexedNotNull { index, day ->
            if (day in 1..7) Triple(day, discipline, index) else null
        }
    }
    val pinned = assignments.map { it.first }
    val byDay = assignments.groupBy { it.first }
    val overlap = byDay.values.any { it.size > 1 }
    val placementError = WeeklyPlacement.validationError(request)
    val capacityFits = chosen > 0 && total > 0 && placementError == null

    TracksideBuilderFrame(
        progressStep = 5,
        eyebrow = "PLAN / AVAILABILITY",
        toolbarTitle = "Availability",
        headline = "Your weekly\navailability",
        description = "Choose your training days — StrideLab adapts multi-discipline stacking automatically.",
        ctaLabel = if (generationBusy) "Building your plan…" else "Generate My Plan",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = capacityFits && !generationBusy,
        descriptionBottomGap = 16,
        contentTopPadding = 12,
        headlineLineHeight = 34
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                .forEachIndexed { index, label ->
                    val day = index + 1
                    val available = day in request.days
                    val preferred = available && request.runDays > 0 && day == request.longDay
                    val fixedSessions = byDay[day]?.size ?: 0
                    Surface(
                        onClick = {
                            if (available && day in pinned) pendingRemovalDay = day
                            else onDay(day)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("available-day-$day")
                            .semantics {
                                stateDescription = when {
                                    available && fixedSessions > 0 ->
                                        "Available, $fixedSessions placed workouts. Tap to change."
                                    available -> "Available. Tap to make unavailable."
                                    else -> "Unavailable. Tap to make available."
                                }
                            },
                        shape = RoundedCornerShape(13.dp),
                        color = if (preferred) PlanBuilderPalette.selected else PlanBuilderPalette.surface,
                        border = BorderStroke(
                            1.dp,
                            if (preferred) PlanBuilderPalette.accent else PlanBuilderPalette.border
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                label,
                                color = PlanBuilderPalette.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                modifier = Modifier.width(38.dp)
                            )
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            preferred -> PlanBuilderPalette.accent
                                            available -> StrideLabVisualPalette.easyTeal
                                            else -> PlanBuilderPalette.disabled
                                        }
                                    )
                            )
                            Text(
                                when {
                                    preferred -> "Long run day"
                                    available && fixedSessions > 0 -> "Available · $fixedSessions placed"
                                    available -> "Available"
                                    else -> "Rest day"
                                },
                                fontSize = 12.5.sp,
                                color = if (available) PlanBuilderPalette.text else PlanBuilderPalette.muted,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (available) "Active" else "Off",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (preferred) PlanBuilderPalette.accent
                                else if (available) StrideLabVisualPalette.easyTeal
                                else PlanBuilderPalette.muted
                            )
                        }
                    }
                }
        }

        Spacer(Modifier.height(16.dp))

        // Adaptive Schedule Summary Card (zero restriction toggles)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = PlanBuilderPalette.surface,
            border = BorderStroke(1.dp, PlanBuilderPalette.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TracksideFormLabel("ADAPTIVE SCHEDULE ENGINE")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        total.toString(),
                        fontSize = 32.sp,
                        color = PlanBuilderPalette.text,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("builder-capacity-count")
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "sessions across $chosen days",
                        fontSize = 12.5.sp,
                        color = PlanBuilderPalette.secondary,
                        modifier = Modifier
                            .padding(bottom = 5.dp)
                            .testTag("builder-capacity-days")
                    )
                }
                Text(
                    when {
                        placementError != null -> placementError
                        chosen == 0 -> "Select at least one available training day."
                        total == 0 -> "Select at least one weekly workout."
                        requiredDoubleDays > 0 || overlap ->
                            "Adaptive stacking active — multi-discipline days receive automatic AM/PM recovery spacing."
                        else -> "Balanced weekly cadence — recovery spacing runs automatically."
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = when {
                        !capacityFits -> StrideLabVisualPalette.highCoral
                        requiredDoubleDays > 0 || overlap -> StrideLabVisualPalette.moderateAmber
                        else -> PlanBuilderPalette.accent
                    },
                    modifier = Modifier.testTag("builder-capacity-status")
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        TracksideOtherDisciplineSlots(
            request = request,
            selectedDiscipline = selectedOtherDiscipline,
            selectedSlot = selectedOtherSlot,
            onSelectDiscipline = onSelectOtherDiscipline,
            onSelectSlot = onSelectOtherSlot,
            onAssignDay = onAssignOtherDay,
            onClearSlot = onClearOtherSlot,
            onAuto = onAutoOther,
            onCount = onCountOther
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "Preview every workout before activating your plan.",
            color = PlanBuilderPalette.muted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.testTag("builder-generate-draft-note")
        )

        generationError?.let { msg ->
            Spacer(Modifier.height(12.dp))
            PlanBuilderErrorBanner(msg)
        }

        Spacer(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .testTag("builder-availability-coaching-clearance")
        )
    }

    if (pendingRemovalDay in 1..7) {
        val day = pendingRemovalDay
        val count = byDay[day]?.size ?: 0
        val label = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[day - 1]
        AlertDialog(
            onDismissRequest = { pendingRemovalDay = 0 },
            containerColor = PlanBuilderPalette.surface,
            title = { Text("Change $label availability?", color = PlanBuilderPalette.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "$count placed workout(s) use $label. Making this day unavailable will let the coach reassign them automatically.",
                    color = PlanBuilderPalette.secondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemovePinnedDay(day)
                        pendingRemovalDay = 0
                    },
                    modifier = Modifier.testTag("builder-confirm-remove-pinned-day")
                ) { Text("Make unavailable", color = PlanBuilderPalette.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemovalDay = 0 }) {
                    Text("Cancel", color = PlanBuilderPalette.secondary)
                }
            }
        )
    }
}

@Composable
internal fun PlanBuilderErrorBanner(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plan-form-error"),
        color = StrideLabVisualPalette.highCoral.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.highCoral.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StrideLabVisualPalette.highCoral)
            )
            Text(
                text = message,
                color = PlanBuilderPalette.text,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TracksideFormLabel(s: String) {
    Text(
        s,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = PlanBuilderPalette.muted,
        letterSpacing = 1.4.sp
    )
}
