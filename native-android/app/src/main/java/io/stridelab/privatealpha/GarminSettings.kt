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

@Composable
internal fun GarminSettingsCard(
    accountId:String,
    syncGarmin: (suspend (GarminPersonalSource) -> GarminConnectionStatus)? = null
) {
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
            catch(error:Exception) { GarminConnectionStatus.fromError(error) }
            finally { busy=false }
        }
    }
    SignalCard(Modifier.testTag("garmin-connection-card")) {
        Text("GARMIN",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Text(status.heading(),style=MaterialTheme.typography.titleLarge,
            modifier=Modifier.testTag("garmin-status"))
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
                    val import=syncGarmin ?: throw IllegalStateException("Garmin history sync is not available here")
                    import(source)
                }
            },enabled=!busy && syncGarmin!=null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
                .testTag("garmin-sync")) { Text("Sync to Activity") }
            OutlinedButton(onClick={
                operation { source.disconnect();GarminConnectionStatus("not_connected") }
            },enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
                .testTag("garmin-disconnect")) { Text("Disconnect") }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.lastSuccessfulSync!=null)Text(
            if(status.importedCount>0) "${status.importedCount} new Garmin activities saved to your offline Activity history."
            else "No new Garmin activities. Existing offline history is unchanged.",
            style=MaterialTheme.typography.bodySmall)
        Text("Official Garmin Connect Personal API. Credentials are encrypted on this device. Sync to Activity saves Garmin activity summaries into account-scoped offline history without deleting, merging or overwriting your existing sources. Detailed Garmin GPS and charts are not imported by this summary sync.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
