package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * M10a: real Week Preview read-only experiment, not a plan-generation engine.
 * The entered limits are asserted numbers, not authenticated safe training
 * ceilings. Never calls PlanActions.generate, PlanRepository, or approval.
 */
@Composable
internal fun IndependentAndroidResearchPreviewPanel(request: PlanRequest) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var dayCsv by rememberSaveable { mutableStateOf("") }
    var startingKm by rememberSaveable { mutableStateOf("") }
    var ceilingKm by rememberSaveable { mutableStateOf("") }
    var dayMaxKm by rememberSaveable { mutableStateOf("") }
    var previousLongKm by rememberSaveable { mutableStateOf("") }
    var longCapKm by rememberSaveable { mutableStateOf("") }
    var longIncreaseKm by rememberSaveable { mutableStateOf("") }
    var display by remember(request) {
        mutableStateOf<IndependentAndroidPreviewDecision?>(null)
    }
    var parseError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("builder-experimental-research-panel"),
        color = PlanBuilderPalette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PlanBuilderPalette.border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Experimental mileage calendar",
                color = PlanBuilderPalette.text,
                style = MaterialTheme.typography.titleMedium)
            Text(
                "Read-only research. No training plan is saved, approved or replaced. " +
                    "Workout intensity and fitness readiness have not been verified.",
                color = PlanBuilderPalette.secondary,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(
                modifier = Modifier.testTag("builder-research-preview-toggle"),
                onClick = { expanded = !expanded }
            ) {
                Text(if (expanded) "Hide research limits" else "Enter research limits",
                    color = PlanBuilderPalette.accent)
            }
            if (expanded) {
                Text(
                    "Enter " + request.runDays +
                        " running weekdays (1=Mon to 7=Sun) from available days: " +
                        request.days.sorted().joinToString(",") +
                        ". Limits are your own unverified assumptions.",
                    color = PlanBuilderPalette.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
                @Composable
                fun entry(
                    label: String,
                    value: String,
                    tag: String,
                    change: (String) -> Unit
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            change(it)
                            display = null
                            parseError = null
                        },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(tag)
                    )
                }
                entry("Run weekdays, e.g. 1,2,3,5,6,7", dayCsv,
                    "builder-research-running-days") { dayCsv = it }
                entry("Entered starting week km", startingKm,
                    "builder-research-starting-km") { startingKm = it }
                entry("Entered weekly km ceiling", ceilingKm,
                    "builder-research-weekly-cap") { ceilingKm = it }
                entry("Maximum km on EACH selected day", dayMaxKm,
                    "builder-research-daily-cap") { dayMaxKm = it }
                if (request.runDays >= 3) {
                    entry("Previous long run km", previousLongKm,
                        "builder-research-previous-long") { previousLongKm = it }
                    entry("Absolute long-run ceiling km", longCapKm,
                        "builder-research-long-cap") { longCapKm = it }
                    entry("Maximum long-run increase km", longIncreaseKm,
                        "builder-research-long-increase") { longIncreaseKm = it }
                }
                Text(
                    "This first preview uses one identical day cap for every selected run day. " +
                        "It does not infer or approve any individual training limit.",
                    color = PlanBuilderPalette.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    modifier = Modifier.fillMaxWidth().testTag("builder-research-preview-run"),
                    onClick = {
                        val dayTokens = dayCsv.split(',').map { it.trim() }
                        val parsed = dayTokens.map { it.toIntOrNull() }
                        if (parsed.any { it == null } ||
                            parsed.toSet().size != parsed.size) {
                            parseError = "Enter distinct comma-separated day numbers 1 through 7."
                            display = null
                        } else {
                            parseError = null
                            val selected = parsed.filterNotNull().toSet()
                            val cap = dayMaxKm.toDoubleOrNull()
                            val entered = IndependentAndroidPreviewConstraints(
                                selectedRunDays = selected,
                                explicitlyEnteredStartingWeeklyKm = startingKm.toDoubleOrNull(),
                                explicitlyEnteredWeeklyCeilingKm = ceilingKm.toDoubleOrNull(),
                                explicitlyEnteredMaxKmByDay =
                                    cap?.let { value -> selected.associateWith { value } },
                                explicitlyEnteredPreviousLongRunKm =
                                    previousLongKm.toDoubleOrNull(),
                                explicitlyEnteredLongRunCeilingKm =
                                    longCapKm.toDoubleOrNull(),
                                explicitlyEnteredMaxLongRunIncreaseKm =
                                    longIncreaseKm.toDoubleOrNull()
                            )
                            display = runCatching {
                                IndependentAndroidResearchBridge.preview(request, entered)
                            }.getOrElse {
                                IndependentAndroidPreviewDecision.Deferred(
                                    "RESEARCH_INPUT_REVIEW_REQUIRED",
                                    "Review the research input values. No plan was generated or saved."
                                )
                            }
                        }
                    }
                ) { Text("Compare research mileage (no save)") }
                parseError?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("builder-research-input-error"))
                }
                when (val outcome = display) {
                    is IndependentAndroidPreviewDecision.Deferred -> {
                        Text(
                            "Preview held: " + outcome.ruleId,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.testTag("builder-research-deferred")
                        )
                        Text(outcome.explanation, color = PlanBuilderPalette.secondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    is IndependentAndroidPreviewDecision.ReadOnly -> {
                        Text(
                            "Research hypothesis only · not executable",
                            color = PlanBuilderPalette.accent,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.testTag("builder-research-readonly")
                        )
                        outcome.mileage.mileage.weeks.forEachIndexed { index, week ->
                            val selectedQuality = outcome.qualitySlots.weeks[index]
                                .opportunities.map { it.isoDay }
                            Text(
                                String.format(Locale.US, "Week %d: %.2f km",
                                    week.week, week.weeklyKilometers) +
                                    if (week.recoveryWeek) " · lighter week" else "",
                                color = PlanBuilderPalette.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                week.dayAllocations.joinToString(" · ") { day ->
                                    String.format(Locale.US, "%s %.2f km",
                                        dayName(day.isoDay), day.kilometers)
                                },
                                color = PlanBuilderPalette.secondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (selectedQuality.isNotEmpty()) {
                                Text(
                                    "Possible quality weekdays: " +
                                        selectedQuality.joinToString(", ") { dayName(it) } +
                                        " · no workout intensity authorized",
                                    color = PlanBuilderPalette.secondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text(
                            "Owner/source coverage, measured intensity, and complete " +
                                "coaching qualification remain unresolved. These are not approved workouts.",
                            color = PlanBuilderPalette.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    null -> Unit
                }
            }
        }
    }
}
