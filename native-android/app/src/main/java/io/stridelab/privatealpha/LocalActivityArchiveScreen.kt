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
import androidx.compose.ui.unit.sp
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
    LaunchedEffect(cloudAvailable) {
        if(!cloudAvailable && source == LocalActivityArchiveScope.CLOUD_ACCOUNT)
            source = LocalActivityArchiveScope.LOCAL_GARMIN
    }

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
                var temp: File? = null
                try {
                    val staged = File.createTempFile(
                        "stridelab-import-", ".zip", context.cacheDir)
                    temp = staged
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            staged.outputStream().buffered().use { output ->
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
                    val result = onRestore(source, staged)
                    message = if(result.newlyRestored == 0)
                        "Archive verified. All activities were already present and kept unchanged."
                    else "Recovered ${result.newlyRestored} source records. " +
                        "Saved activity history has been reloaded."
                } catch(error: Exception) {
                    if(error is CancellationException) throw error
                    message = "Archive was rejected, incomplete, or conflicts with saved recordings. " +
                        "No existing activity was overwritten."
                } finally {
                    temp?.delete()
                    busy = false
                }
            }
        }
    }


    MoreFeatureScreen("Activity backup", onBack, "archive-back", backEnabled = !busy) {
        item(key = "archive-intro") {
            MoreFeatureCard {
                MoreFeatureEyebrow("PRIVATE · OFFLINE")
                Text("Protect recorded activities",
                    fontSize = 17.sp, lineHeight = 23.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MoreFeatureColors.text)
                MoreFeatureMuted(
                    "Back up saved run summaries, GPS, heart-rate samples, laps and " +
                        "original FIT files when the app retained them. Older recordings " +
                        "without the original FIT are still included through saved activity details."
                )
                MoreFeatureDivider()
                MoreFeatureEyebrow("DATA PRIVACY")
                MoreFeatureMuted(
                    "ZIP files are NOT encrypted and may reveal precise routes and health data. " +
                        "Keep exported files private."
                )
                MoreFeatureMuted(
                    "Activity archives do not include generated plans, manual strength logs, " +
                        "account credentials or offline maps. Those use separate storage."
                )
            }
        }
        item(key = "archive-source") {
            MoreFeatureCard {
                MoreFeatureEyebrow("ACTIVITY SOURCE")
                MoreFeatureMuted("Choose which saved history to export or restore.")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoreFeatureChip(
                        "Local Garmin history",
                        selected = source == LocalActivityArchiveScope.LOCAL_GARMIN,
                        onClick = { source = LocalActivityArchiveScope.LOCAL_GARMIN },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("archive-local-garmin")
                    )
                    MoreFeatureChip(
                        "Signed-in account history",
                        selected = source == LocalActivityArchiveScope.CLOUD_ACCOUNT,
                        onClick = { source = LocalActivityArchiveScope.CLOUD_ACCOUNT },
                        enabled = !busy && cloudAvailable,
                        modifier = Modifier.fillMaxWidth().testTag("archive-cloud-account")
                    )
                }
                MoreFeatureMuted(
                    if (source == LocalActivityArchiveScope.LOCAL_GARMIN)
                        "Can be restored to a new installation's local Garmin owner. " +
                            "Existing records are never replaced."
                    else
                        "Restore only while signed in to the same StrideLab account. " +
                            "An archive never signs you in."
                )
            }
        }
        item(key = "archive-actions") {
            MoreFeatureCard {
                MoreFeatureEyebrow("EXPORT OR RESTORE")
                MoreFeaturePrimaryAction(
                    text = if (busy) "Preparing…" else "Export activity ZIP",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            try {
                                pendingFile = onExport(source)
                                createArchive.launch(
                                    "StrideLab-${source.name.lowercase()}-${LocalDate.now()}.zip"
                                )
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                pendingFile?.delete()
                                pendingFile = null
                                busy = false
                                message = "Archive preparation failed; no complete backup was generated. " +
                                    "Saved activities were kept."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("archive-export")
                )
                MoreFeatureSecondaryAction(
                    text = "Restore activity ZIP",
                    enabled = !busy,
                    onClick = {
                        message = null
                        importArchive.launch(arrayOf(
                            "application/zip", "application/octet-stream",
                            "application/x-zip-compressed"
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("archive-restore")
                )
                MoreFeatureDivider()
                MoreFeatureMuted(
                    "Restore validates the entire archive and every checksum before " +
                        "inserting records. Conflicting IDs stop recovery rather than " +
                        "replacing your existing activities."
                )
            }
        }
        message?.let { note ->
            item(key = "archive-feedback") {
                MoreFeatureCard {
                    MoreFeatureEyebrow("ARCHIVE STATUS")
                    Text(note, modifier = Modifier.testTag("archive-feedback"),
                        fontSize = 13.sp, lineHeight = 19.sp,
                        color = MoreFeatureColors.text)
                }
            }
        }
    }
}
