package io.stridelab.privatealpha

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Explicit local archive management. No credentials, telemetry, plan changes
 * or automatic restore. Export/restore is always initiated by the owner.
 */
@Composable
internal fun LocalActivityArchiveScreen(
    cloudAvailable: Boolean,
    onBack: () -> Unit,
    onExport: suspend (LocalActivityArchiveScope) -> File,
    onRestore: suspend (LocalActivityArchiveScope, File) -> LocalActivityArchiveCounts
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf(LocalActivityArchiveScope.LOCAL_GARMIN) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    // A staged export is held in app-private cache, never saved in a preset.
    DisposableEffect(Unit) {
        onDispose { pendingFile?.delete() }
    }

    val createArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val sourceFile = pendingFile
        if(uri == null || sourceFile == null) {
            sourceFile?.delete()
            pendingFile = null
            busy = false
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openOutputStream(uri, "w")).use { output ->
                            sourceFile.inputStream().buffered().use { it.copyTo(output) }
                        }
                    }
                    message = "Activity archive saved. Keep this unencrypted ZIP private."
                } catch(error: Exception) {
                    if(error is CancellationException) throw error
                    message = "Archive could not be written. Check or delete the incomplete destination file."
                } finally {
                    sourceFile.delete()
                    pendingFile = null
                    busy = false
                }
            }
        }
    }
    val importArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        uri ->
        if(uri != null) {
            busy = true
            scope.launch {
                val temp = File.createTempFile("stridelab-import-", ".zip", context.cacheDir)
                try {
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            temp.outputStream().buffered().use { output ->
                                val buffer = ByteArray(65536)
                                var total = 0L
                                while(true) {
                                    val count = input.read(buffer)
                                    if(count < 0) break
                                    total += count
                                    require(total <= 600L * 1024 * 1024) {
                                        "Archive file exceeds the supported size."
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                    val result = onRestore(source, temp)
                    message = if(result.newlyRestored == 0)
                        "Archive verified. All activities were already present and kept unchanged."
                    else "Recovered ${result.newlyRestored} source records. " +
                        "Saved activity history has been reloaded."
                } catch(error: Exception) {
                    if(error is CancellationException) throw error
                    message = "Archive was rejected, incomplete, or conflicts with saved recordings. " +
                        "No existing activity was overwritten."
                } finally {
                    temp.delete()
                    busy = false
                }
            }
        }
    }

    Screen("Activity backup", "PRIVATE · OFFLINE", compactTitle = true) {
        item(key = "archive-back") {
            TextButton(onClick = onBack, enabled = !busy,
                modifier = Modifier.testTag("archive-back")) { Text("← Back to More") }
        }
        item(key = "archive-intro") {
            SignalCard {
                Text("Protect your recorded activities", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Export saved activity summaries, GPS, heart-rate samples, laps and " +
                        "original FIT files where the app retained those bytes. " +
                        "Previously imported recordings without original FIT files " +
                        "are still archived through their saved activity details.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "This archive does not contain generated plans, manual strength logs, " +
                        "account credentials, or maps. Those use separate storage. " +
                        "ZIP files are NOT encrypted and may reveal precise routes and health data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "archive-source") {
            SignalCard {
                Text("ACTIVITY SOURCE", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = source == LocalActivityArchiveScope.LOCAL_GARMIN,
                        onClick = { source = LocalActivityArchiveScope.LOCAL_GARMIN },
                        enabled = !busy,
                        label = { Text("Local Garmin history") },
                        modifier = Modifier.testTag("archive-local-garmin")
                    )
                    FilterChip(
                        selected = source == LocalActivityArchiveScope.CLOUD_ACCOUNT,
                        onClick = { source = LocalActivityArchiveScope.CLOUD_ACCOUNT },
                        enabled = !busy && cloudAvailable,
                        label = { Text("Signed-in account history") },
                        modifier = Modifier.testTag("archive-cloud-account")
                    )
                    Text(
                        if(source == LocalActivityArchiveScope.LOCAL_GARMIN)
                            "Can be restored to a new installation's local Garmin owner. " +
                                "Existing records are never replaced."
                        else "Restore only while signed in to the same StrideLab account. " +
                            "An archive never signs you in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item(key = "archive-actions") {
            SignalCard {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            try {
                                pendingFile = onExport(source)
                                createArchive.launch(
                                    "StrideLab-${source.name.lowercase()}-${LocalDate.now()}.zip")
                            } catch(error: Exception) {
                                if(error is CancellationException) throw error
                                pendingFile?.delete()
                                pendingFile = null
                                busy = false
                                message = "Archive preparation failed; no complete backup was generated. " +
                                    "Saved activities were kept."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("archive-export")
                ) { Text(if(busy) "Preparing…" else "Export activity ZIP") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { message = null; importArchive.launch(arrayOf(
                        "application/zip", "application/octet-stream",
                        "application/x-zip-compressed"
                    )) },
                    modifier = Modifier.fillMaxWidth().testTag("archive-restore")
                ) { Text("Restore activity ZIP") }
                Text(
                    "Restore checks the entire ZIP and every source checksum before " +
                        "inserting records. A conflicting activity ID stops the restore " +
                        "rather than replacing the original.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        message?.let { note ->
            item(key = "archive-feedback") {
                SignalCard {
                    Text(note, modifier = Modifier.testTag("archive-feedback"),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
