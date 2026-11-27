package io.stridelab.privatealpha

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import kotlin.math.roundToInt

internal fun encodeRunningWorkoutForWatch(
    title: String,
    session: PlannedSession,
    target: Engine15SessionPresentation.Target,
    createdAt: Instant,
    verifiedZones: ExecutionHrZoneProfile?
): ByteArray {
    require(session.discipline == "run" && target.currentMinutes > 0)
    val reviewed = target.state == Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY ||
        target.state == Engine15SessionPresentation.State.ACCEPTED_PROGRESSION ||
        (session.status in setOf("planned", "in_progress") &&
            session.suggestion?.let { it.accepted && session.effectiveMinutes != session.minutes } == true)
    val timeOnly = target.state == Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY ||
        (reviewed && target.state != Engine15SessionPresentation.State.ACCEPTED_PROGRESSION)
    val meters = if (timeOnly) null else target.currentDistanceTargetKm
        ?.let { (it * 1_000).roundToInt() }?.takeIf { it > 0 }
    val prescription = if (reviewed) null else session.runningPrescription
    return GarminFitWorkoutEncoder.encode(
        title, prescription, createdAt, verifiedZones,
        fallbackMeters = meters?.takeIf { prescription == null },
        fallbackSeconds = (target.currentMinutes * 60).takeIf { prescription == null && meters == null },
        serialNumber = (session.id.hashCode().toLong() and 0xffffffffL).coerceAtLeast(1)
    )
}

@Composable
internal fun RunningWorkoutFitExportButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag("workout-fit-export")) {
        Text("Export to Watch (.FIT)")
    }
}

/** Register the picker at screen scope so scrolling cannot drop its result callback. */
@Composable
internal fun rememberRunningWorkoutFitExport(
    owner: String,
    zoneOwners: List<String>,
    title: String,
    session: PlannedSession,
    target: Engine15SessionPresentation.Target,
    hrFallbackProfile: ExecutionHrZoneProfile? = null
): () -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var review by rememberSaveable(owner, session.id) { mutableStateOf(false) }
    var payload by rememberSaveable(owner, session.id) { mutableStateOf<ByteArray?>(null) }
    var working by remember(owner, session.id) { mutableStateOf(false) }
    var result by rememberSaveable(owner, session.id) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = payload
        payload = null
        if (uri != null && bytes != null) scope.launch {
            working = true
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Document provider did not open the destination")
                    output.use { it.write(bytes) }
                }
                result = "Workout file saved."
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                result = "Couldn’t save the workout. Try another location."
            } finally {
                working = false
            }
        }
    }
    if (review) {
        AlertDialog(
            onDismissRequest = { if (!working) review = false },
            title = { Text("Export to Watch (.FIT)") },
            text = { Text("Copy this .FIT file to /GARMIN/NewFiles/ on your watch.") },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            confirmButton = {
                TextButton(enabled = !working, onClick = {
                    working = true
                    scope.launch {
                        try {
                            payload = withContext(Dispatchers.IO) {
                                val store = GarminZonePreferenceStore(context)
                                val zones = zoneOwners.firstNotNullOfOrNull {
                                    val profile = store.load(it)?.profile?.let { profile ->
                                        GarminHeartRateZones.forRunning(listOf(profile))
                                    }
                                    ExecutionHrZoneProfile.fromGarmin(profile)
                                } ?: hrFallbackProfile
                                encodeRunningWorkoutForWatch(title, session, target, Instant.now(), zones)
                            }
                            picker.launch(GarminFitWorkoutEncoder.fileName(title))
                            review = false
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            payload = null
                            review = false
                            result = "Couldn’t prepare the workout file. Try again."
                        } finally {
                            working = false
                        }
                    }
                }) { Text(if (working) "Preparing…" else "Choose save location") }
            },
            dismissButton = { TextButton(enabled = !working, onClick = { review = false }) { Text("Cancel") } }
        )
    }
    result?.let { message ->
        AlertDialog(
            onDismissRequest = { result = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { result = null }) { Text("Done") } }
        )
    }
    return { if (!working && payload == null) review = true }
}
