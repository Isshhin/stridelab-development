package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val garminSheetBackground = Color(0xFF090F0D)
private val garminSheetLime = Color(0xFFD8FB70)

/** No independent navigation destination: this dialog overlays the More root. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TracksideGarminConnectionSheet(
    accountId: String,
    syncGarmin: (suspend (GarminPersonalSource) -> GarminConnectionStatus)?,
    backupGarminCloud: (suspend () -> Int)? = null,
    restoreGarminCloud: (suspend () -> Int)? = null,
    busy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = garminSheetBackground,
        contentColor = Color(0xFFF2F5F2),
        scrimColor = Color.Black.copy(alpha = .74f),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 8.dp)
                    .size(42.dp, 5.dp)
                    .background(Color(0xFF83918A), RoundedCornerShape(6.dp))
            )
        },
        modifier = Modifier.testTag("garmin-modal-sheet")
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "CONNECTIONS", color = garminSheetLime,
                        fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                        letterSpacing = 1.1.sp
                    )
                    Text("Garmin Connect", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.testTag("garmin-modal-close")
                ) { Text("Close", color = if (busy) Color.Gray else garminSheetLime) }
            }
            GarminSettingsCard(
                accountId = accountId,
                syncGarmin = syncGarmin,
                backupGarminCloud = backupGarminCloud,
                restoreGarminCloud = restoreGarminCloud,
                onBusyChange = onBusyChange
            )
        }
    }
}
