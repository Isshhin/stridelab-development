package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val moreBlack = Color(0xFF080B0A)
private val moreSurface = Color(0xFF151A18)
private val moreBorder = Color(0xFF364039)
private val moreWhite = Color(0xFFF1F4F1)
private val moreMuted = Color(0xFFAAB4AD)
private val moreSecondary = Color(0xFF82918A)
private val moreLime = Color(0xFFD8FB70)

@Composable
internal fun AthleteHeartRatePreferenceCard(account: String?, garminOwner: String?) {
    var manualOpen by rememberSaveable(account) { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember(context) { AthleteHeartRatePreferences(context) }
    val zoneStore = remember(context) { GarminZonePreferenceStore(context) }
    var selectedSource by remember(garminOwner) {
        mutableStateOf(zoneStore.source(garminOwner))
    }
    var importedZones by remember(garminOwner) {
        mutableStateOf(zoneStore.load(garminOwner))
    }
    var zoneSyncing by remember(garminOwner) { mutableStateOf(false) }
    var zoneSyncError by remember(garminOwner) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var saved by remember(account) {
        mutableStateOf(preferences.settings(account))
    }
    var method by remember(account, saved) { mutableStateOf(saved.method) }
    var maximumText by remember(account, saved) {
        mutableStateOf(saved.maximumHr?.toString().orEmpty())
    }
    var thresholdText by remember(account, saved) {
        mutableStateOf(saved.lactateThresholdHr?.toString().orEmpty())
    }
    var restingText by remember(account, saved) {
        mutableStateOf(saved.restingHr?.toString().orEmpty())
    }

    val maximum = maximumText.toIntOrNull()
    val threshold = thresholdText.toIntOrNull()
    val resting = restingText.toIntOrNull()
    val candidate = AthleteHeartRateZoneSettings(
        method = method,
        maximumHr = maximum,
        lactateThresholdHr = threshold,
        restingHr = resting
    )
    val valid = candidate.ready

    Surface(
        shape = RoundedCornerShape(21.dp), color = moreSurface,
        border = BorderStroke(1.dp, moreBorder),
        modifier = Modifier.fillMaxWidth().testTag("athlete-hr-preference")
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text(
                "Heart rate zones", color = moreWhite, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "ZONE SOURCE",
                color = moreMuted, fontSize = 12.sp, lineHeight = 18.sp
            )
            Row(Modifier.fillMaxWidth().selectableGroup()
                .background(Color(0xFF242B22), RoundedCornerShape(14.dp)).padding(4.dp)) {
                listOf(DisplayHeartRateZoneSource.GARMIN to "Garmin",
                    DisplayHeartRateZoneSource.MANUAL to "Manual").forEach { (source, label) ->
                    val selected = selectedSource == source
                    Box(Modifier.weight(1f).heightIn(min = 48.dp)
                        .background(if (selected) moreLime else Color.Transparent, RoundedCornerShape(10.dp))
                        .selectable(selected, role = Role.RadioButton) {
                            if (zoneStore.select(garminOwner, source)) {
                                selectedSource = source
                                zoneSyncError = null
                            }
                        }.testTag("athlete-zone-source-" + source.name.lowercase(java.util.Locale.ROOT)),
                        contentAlignment = Alignment.Center) {
                        Text(label, color = if (selected) moreBlack else moreWhite)
                    }
                }
            }
            if (importedZones != null) {
                val cache = importedZones!!
                val date = java.time.Instant.ofEpochMilli(cache.syncedAtMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                Text("Garmin zones available · Cached offline", color = moreLime)
                Text(if (cache.profile.sport.equals("running", true)) "Running heart-rate zones"
                    else "Garmin default heart-rate zones",
                    color = moreWhite, fontWeight = FontWeight.SemiBold)
                Column(Modifier.fillMaxWidth().testTag("athlete-garmin-zone-bounds")) {
                    val floors = cache.profile.floorsBpm
                    val zoneColors = listOf(Color(0xFFAAB3BF), Color(0xFF7CD9A3),
                        Color(0xFF83D6E2), Color(0xFFF2C565), Color(0xFFF18D89))
                    floors.forEachIndexed { index, floor ->
                        val nextFloor = floors.getOrNull(index + 1)
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val tint = zoneColors[index]
                            Text("Z${index + 1}", color = tint)
                            Box(Modifier.weight(1f).height(6.dp)
                                .background(Color(0xFF343D36), RoundedCornerShape(6.dp))) {
                                Box(Modifier.fillMaxWidth((index + 1) / 5f).fillMaxHeight()
                                    .background(tint, RoundedCornerShape(6.dp)))
                            }
                            Text(if (nextFloor == null) "$floor+ bpm" else "$floor–${nextFloor - 1} bpm",
                                color = moreWhite)
                        }
                    }
                }
                Text("Last synced: $date",
                    color = moreSecondary, fontSize = 10.sp,
                    modifier = Modifier.testTag("athlete-garmin-zone-synced"))
            } else {
                Text(if (garminOwner == null) "Connect Garmin in More to sync zones."
                    else "Garmin zones have not been synced on this device.",
                    color = moreSecondary, fontSize = 11.sp)
            }
            Button(
                onClick = {
                    val owner = garminOwner ?: return@Button
                    if (!zoneSyncing) {
                        zoneSyncing = true
                        zoneSyncError = null
                        coroutineScope.launch {
                            try {
                                val profile = withContext(Dispatchers.IO) {
                                    val source = GarminPersonalSource(
                                        owner, GarminCredentialVault(context.applicationContext),
                                        GarminPersonalClient())
                                    GarminHeartRateZones.forRunning(source.heartRateZones(owner))
                                        ?: throw GarminProtocolException(
                                            "Garmin did not provide one valid running or default BPM profile")
                                }
                                val saved = withContext(Dispatchers.IO) {
                                    zoneStore.save(owner, profile)
                                }
                                if (!saved) throw IllegalStateException("Could not cache Garmin zones")
                                importedZones = zoneStore.load(owner)
                            } catch (error: Exception) {
                                zoneSyncError = when (error) {
                                    is GarminException -> GarminConnectionStatus.fromError(error).error
                                    else -> "Garmin zones could not be saved. Previous settings remain unchanged."
                                }
                            } finally {
                                zoneSyncing = false
                            }
                        }
                    }
                },
                enabled = garminOwner != null && !zoneSyncing,
                modifier = Modifier.fillMaxWidth().testTag("athlete-garmin-zone-sync"),
                colors = ButtonDefaults.buttonColors(containerColor = moreLime, contentColor = moreBlack)
            ) { Text(if (zoneSyncing) "Syncing Garmin zones…" else "Sync Garmin HR zones") }
            zoneSyncError?.let { error ->
                Text(error, color = Color(0xFFF2AD96), fontSize = 11.sp,
                    modifier = Modifier.testTag("athlete-garmin-zone-error"))
            }
            TextButton(onClick = { manualOpen = !manualOpen },
                modifier = Modifier.fillMaxWidth().testTag("athlete-manual-config")) {
                Text(if (manualOpen) "Hide manual HR configuration" else "Manual HR configuration",
                    color = moreLime)
            }
            if (manualOpen) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeartRateZoneMethod.entries.forEach { option ->
                        FilterChip(
                            selected = method == option,
                            onClick = { method = option },
                            label = {
                                Text(
                                    option.displayName,
                                    maxLines = 1,
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.weight(1f)
                                .testTag("athlete-hr-method-" + option.storageValue)
                        )
                    }
                }

                Text(
                    when (method) {
                        HeartRateZoneMethod.MAX_BPM ->
                            "MAX BPM uses percentages of your saved maximum heart rate."
                        HeartRateZoneMethod.LTHR_PERCENT ->
                            "LTHR% uses your lactate-threshold heart rate."
                        HeartRateZoneMethod.HRR_PERCENT ->
                            "HRR% uses heart-rate reserve: maximum HR minus resting HR."
                    },
                    color = moreSecondary, fontSize = 11.sp, lineHeight = 16.sp
                )

                if (method == HeartRateZoneMethod.MAX_BPM ||
                    method == HeartRateZoneMethod.HRR_PERCENT
                ) {
                    OutlinedTextField(
                        value = maximumText,
                        onValueChange = { value ->
                            if (value.length <= 3 && value.all { it.isDigit() }) {
                                maximumText = value
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                            .testTag("athlete-max-hr-input"),
                        label = { Text("Maximum heart rate") },
                        placeholder = { Text("100–240") },
                        suffix = { Text("bpm") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = maximumText.isNotBlank() && (maximum == null || maximum !in 100..240),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = moreLime,
                            focusedLabelColor = moreLime,
                            unfocusedBorderColor = moreBorder,
                            focusedTextColor = moreWhite,
                            unfocusedTextColor = moreWhite
                        )
                    )
                }

                if (method == HeartRateZoneMethod.LTHR_PERCENT) {
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { value ->
                            if (value.length <= 3 && value.all { it.isDigit() }) {
                                thresholdText = value
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                            .testTag("athlete-lthr-input"),
                        label = { Text("Lactate threshold HR") },
                        placeholder = { Text("100–230") },
                        suffix = { Text("bpm") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = thresholdText.isNotBlank() && (threshold == null || threshold !in 100..230),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = moreLime,
                            focusedLabelColor = moreLime,
                            unfocusedBorderColor = moreBorder,
                            focusedTextColor = moreWhite,
                            unfocusedTextColor = moreWhite
                        )
                    )
                }

                if (method == HeartRateZoneMethod.HRR_PERCENT) {
                    OutlinedTextField(
                        value = restingText,
                        onValueChange = { value ->
                            if (value.length <= 3 && value.all { it.isDigit() }) {
                                restingText = value
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                            .testTag("athlete-resting-hr-input"),
                        label = { Text("Resting heart rate") },
                        placeholder = { Text("30–120") },
                        suffix = { Text("bpm") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = restingText.isNotBlank() &&
                            (resting == null || resting !in 30..120 ||
                                (maximum != null && resting >= maximum)),
                        supportingText = {
                            if (maximum != null && resting != null && resting >= maximum) {
                                Text("Resting HR must be below maximum HR.")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = moreLime,
                            focusedLabelColor = moreLime,
                            unfocusedBorderColor = moreBorder,
                            focusedTextColor = moreWhite,
                            unfocusedTextColor = moreWhite
                        )
                    )
                }

                Text(
                    when (method) {
                        HeartRateZoneMethod.MAX_BPM ->
                            "Zones: <60%, 60–69%, 70–79%, 80–89%, ≥90% Max HR."
                        HeartRateZoneMethod.LTHR_PERCENT ->
                            "Zones: <85%, 85–89%, 90–94%, 95–99%, ≥100% LTHR."
                        HeartRateZoneMethod.HRR_PERCENT ->
                            "Zones: <60%, 60–69%, 70–79%, 80–89%, ≥90% HRR."
                    },
                    color = moreMuted, fontSize = 10.sp, lineHeight = 15.sp
                )

                Text(
                    if (account == null)
                        "Saved locally on this device."
                    else
                        "Saved for the signed-in athlete on this device.",
                    color = moreSecondary, fontSize = 10.sp
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (saved.maximumHr != null || saved.lactateThresholdHr != null ||
                        saved.restingHr != null
                    ) {
                        TextButton(
                            onClick = {
                                preferences.clear(account)
                                saved = preferences.settings(account)
                            },
                            modifier = Modifier.testTag("athlete-max-hr-clear")
                        ) {
                            Text("Clear", color = moreMuted)
                        }
                    }
                    Button(
                        onClick = {
                            preferences.save(account, candidate)
                            saved = candidate
                        },
                        enabled = valid && candidate != saved,
                        modifier = Modifier.testTag("athlete-max-hr-save"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = moreLime,
                            contentColor = moreBlack
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
