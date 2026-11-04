package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
internal fun GarminSettingsCard(accountId:String) {
    val context=LocalContext.current
    val source=remember(accountId) {
        GarminPersonalSource(accountId,GarminCredentialVault(context),GarminPersonalClient())
    }
    var status by remember(accountId) { mutableStateOf(source.status()) }
    var apiKey by remember(accountId) { mutableStateOf("") }
    var busy by remember(accountId) { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    fun operation(block:suspend ()->GarminConnectionStatus) {
        if(busy)return
        busy=true
        scope.launch {
            status=try { withContext(Dispatchers.IO) { block() } }
            catch(error:Exception) { GarminConnectionStatus("error",error=error.message ?: "Garmin connection failed") }
            finally { busy=false }
        }
    }
    SignalCard(Modifier.testTag("garmin-connection-card")) {
        Text("GARMIN",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Text(when(status.state) {
            "connected" -> "Connected"
            "error" -> "Authentication expired or needs attention"
            else -> "Not connected"
        },style=MaterialTheme.typography.titleLarge,modifier=Modifier.testTag("garmin-status"))
        status.lastSuccessfulSync?.let { Text("Last successful check ${it}",style=MaterialTheme.typography.bodySmall) }
        status.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        if(status.state=="not_connected") {
            OutlinedTextField(apiKey,{apiKey=it},label={Text("Personal API key")},singleLine=true,
                visualTransformation=PasswordVisualTransformation(),enabled=!busy,
                modifier=Modifier.fillMaxWidth().testTag("garmin-api-key"))
            Button(onClick={
                val submitted=apiKey
                apiKey=""
                operation { source.connect(submitted) }
            },enabled=!busy && apiKey.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
                .testTag("garmin-connect")) { Text("Connect Garmin") }
        } else {
            Button(onClick={
                operation {
                    val page=source.activities(accountId,null,100)
                    GarminConnectionStatus("connected",Instant.now(),page.records.size)
                }
            },enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
                .testTag("garmin-sync")) { Text("Sync now") }
            OutlinedButton(onClick={
                operation { source.disconnect();GarminConnectionStatus("not_connected") }
            },enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
                .testTag("garmin-disconnect")) { Text("Disconnect") }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.importedCount>0)Text("${status.importedCount} Garmin activities available from the latest source check.",style=MaterialTheme.typography.bodySmall)
        Text("Official Garmin Connect Personal API. The key and short-lived token are encrypted on this device. This foundation checks the source; importing it into saved StrideLab history remains disabled until repository-level persistence is verified.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
