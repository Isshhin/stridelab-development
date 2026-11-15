package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Neutral StrideLab surface for workout detail controls, not the older blue glass card. */
@Composable
internal fun TracksideWorkoutNeutralCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier=modifier.fillMaxWidth(),
        color=TracksidePalette.surfaceRaised,
        contentColor=TracksidePalette.text,
        shape=RoundedCornerShape(15.dp),
        border=BorderStroke(1.dp,TracksidePalette.outline)
    ) {
        Column(
            Modifier.padding(15.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp),
            content=content
        )
    }
}

/** The action affects only visibility. No workout is logged or approved by opening it. */
@Composable
internal fun TracksideWorkoutAccordion(
    title: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    TracksideWorkoutNeutralCard(Modifier.testTag(tag)) {
        Surface(
            onClick=onToggle,
            color=Color.Transparent,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                .testTag("$tag-toggle")
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(title,color=TracksidePalette.text,
                        fontSize=14.sp,fontWeight=FontWeight.Bold)
                    if(subtitle!=null)Text(subtitle,
                        color=TracksidePalette.textMuted,fontSize=11.sp)
                }
                Text(if(expanded)"Hide −" else "Open ›",
                    color=TracksidePalette.lime,fontSize=12.sp,
                    modifier=Modifier.padding(start=8.dp))
            }
        }
        if(expanded)content()
    }
}

/** A short date control with an actual Material3 calendar picker.
 * Selected calendar days are converted at UTC midnight to prevent timezone drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TracksideWorkoutDateSelector(
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected=remember(value) { runCatching { LocalDate.parse(value) }.getOrNull() }
    Surface(
        onClick={ open=true },
        enabled=enabled,
        modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)
            .testTag("workout-actual-date"),
        color=Color(0xFF292E2A),
        shape=RoundedCornerShape(11.dp),
        border=BorderStroke(1.dp,TracksidePalette.outline)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=9.dp),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically
        ) {
            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text("ACTUAL WORKOUT DATE",color=TracksidePalette.textMuted,
                    fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                Text(
                    selected?.format(DateTimeFormatter.ofPattern(
                        "EEE, MMM d, yyyy",Locale.getDefault())) ?: "Choose date",
                    color=TracksidePalette.text,
                    fontSize=13.sp,fontWeight=FontWeight.Medium
                )
            }
            Text("Change",color=TracksidePalette.lime,fontSize=12.sp)
        }
    }
    if(open) {
        val initial=selected?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()?.toEpochMilli()
        val picker=rememberDatePickerState(initialSelectedDateMillis=initial)
        DatePickerDialog(
            onDismissRequest={open=false},
            confirmButton={
                TextButton(onClick={
                    picker.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it)
                            .atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open=false
                }) { Text("Use date") }
            },
            dismissButton={
                TextButton(onClick={open=false}) { Text("Cancel") }
            },
            colors=DatePickerDefaults.colors(containerColor=TracksidePalette.surfaceRaised)
        ) {
            DatePicker(
                state=picker,
                colors=DatePickerDefaults.colors(
                    containerColor=TracksidePalette.surfaceRaised
                )
            )
        }
    }
}
