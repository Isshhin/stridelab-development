package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val garminLime = Color(0xFFD8FB70)
private val garminMuted = Color(0xFFADB8B0)

/** Account-scoped connection actions; source credentials remain encrypted. */
@Composable
internal fun GarminSettingsCard(
    accountId: String,
    syncGarmin: (suspend (GarminPersonalSource) -> GarminConnectionStatus)? = null,
    onBusyChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val source = remember(accountId) {
        GarminPersonalSource(accountId,
            GarminCredentialVault(context.applicationContext), GarminPersonalClient())
    }
    var status by remember(accountId) { mutableStateOf(source.status()) }
    var connected by remember(accountId) { mutableStateOf(status.state == "connected") }
    var apiKey by remember(accountId) { mutableStateOf("") }
    var busy by remember(accountId) { mutableStateOf(false) }
    var confirmDisconnect by remember(accountId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun operation(action: suspend () -> GarminConnectionStatus) {
        if (busy) return
        busy = true
        onBusyChange(true)
        scope.launch {
            try { status = withContext(Dispatchers.IO) { action() } }
            catch (error: Exception) { status = GarminConnectionStatus.fromError(error) }
            finally {
                // Keystore reads must not occur in every Compose frame.
                connected = withContext(Dispatchers.IO) {
                    runCatching { source.status().state == "connected" }.getOrDefault(false)
                }
                busy = false
                onBusyChange(false)
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().testTag("garmin-connection-card"),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text(if (connected) "✓  Connected" else "Not connected",
            modifier = Modifier.testTag("garmin-status"),
            color = if (connected) garminLime else Color.White,
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        status.error?.let {
            Text(it, color = Color(0xFFF2AD96), fontSize = 12.sp,
                modifier = Modifier.testTag("garmin-error"))
        }
        if (!connected) {
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                label = { Text("Personal API key") },
                singleLine = true, enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = garminLime, focusedLabelColor = garminLime,
                    cursorColor = garminLime),
                modifier = Modifier.fillMaxWidth().testTag("garmin-api-key"))
            Button(onClick = {
                val key = apiKey
                apiKey = ""
                operation { source.connect(key) }
            }, enabled = !busy && apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("garmin-connect"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = garminLime, contentColor = Color(0xFF071009))) {
                Text("Connect Garmin", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(onClick = {
                operation {
                    val sync = syncGarmin ?: throw GarminProtocolException("Garmin sync is unavailable")
                    sync(source)
                }
            }, enabled = !busy && syncGarmin != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("garmin-sync"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = garminLime, contentColor = Color(0xFF071009))) {
                Text(if (busy) "Syncing Garmin…" else "Sync activities",
                    fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = { confirmDisconnect = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("garmin-disconnect"),
                border = BorderStroke(1.dp, Color(0xFF46554C)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFE5EEE6))) { Text("Disconnect") }
        }
        if (busy) LinearProgressIndicator(
            Modifier.fillMaxWidth().testTag("garmin-sync-progress"),
            color = garminLime, trackColor = Color(0xFF26372D))
        if (status.lastSuccessfulSync != null) {
            Surface(shape = RoundedCornerShape(16.dp),
                color = Color(0xFF142019),
                modifier = Modifier.fillMaxWidth().testTag("garmin-sync-result")) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        status.importedCount.toString() + " new summaries · " +
                            status.detailsImported.toString() + " recordings",
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(if (status.detailsRemaining > 0)
                        status.detailsRemaining.toString() + " run recordings still pending. Sync again to continue."
                        else "Available source recordings have been checked.",
                        color = garminMuted, fontSize = 12.sp)
                    if (status.failedDetails > 0) Text(
                        status.failedDetails.toString() +
                            " recordings could not be verified in this pass. Originals were retained.",
                        color = Color(0xFFF2AD96), fontSize = 12.sp)
                }
            }
        }
        Text("Credentials are encrypted on this device. Sync preserves existing activities and caches available run recordings for offline analysis.",
            color = garminMuted, fontSize = 12.sp, lineHeight = 18.sp)
    }
    if (confirmDisconnect) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        title = { Text("Disconnect Garmin?") },
        text = { Text("Existing offline activities will remain saved on this device.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDisconnect = false
                operation { source.disconnect(); GarminConnectionStatus("not_connected") }
            }, colors = ButtonDefaults.textButtonColors(contentColor = garminLime)) {
                Text("Disconnect")
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
        }
    )
}
