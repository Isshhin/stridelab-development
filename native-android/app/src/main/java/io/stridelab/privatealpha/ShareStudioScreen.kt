package io.stridelab.privatealpha

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File

private enum class ShareSection(val label:String) {
    LAYOUT("Layout"), MAP("Map"), STATS("Stats"), STYLE("Style"), EXPORT("Export")
}
private data class ShareMapRequest(val mode:ShareRouteMode, val width:Int, val height:Int, val color:ShareRouteColor, val stroke:ShareStroke, val zoom:ShareZoom, val position:SharePosition, val verticalPosition:ShareVerticalPosition)
private data class ShareMapResult(val source:ShareRenderableActivity, val request:ShareMapRequest, val bitmap:Bitmap?, val loading:Boolean)
private data class ShareCardResult(val source:ShareRenderableActivity, val state:ShareStudioState, val map:Bitmap?, val bitmap:Bitmap)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShareStudioScreen(activity:ActivityRecord, detail:ActivityDetail?, online:Boolean, onBack:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val store=remember(context) { SharePresetStore(context) }
    var configJson by rememberSaveable(activity.id) { mutableStateOf(SharePresetCodec.encode(listOf(SharePreset("draft","Draft",ShareStudioState())))) }
    val config=remember(configJson) { SharePresetCodec.decode(configJson).single().state }
    fun configure(value:ShareStudioState) { configJson=SharePresetCodec.encode(listOf(SharePreset("draft","Draft",value))) }
    var section by rememberSaveable { mutableStateOf(ShareSection.LAYOUT) }
    var activePreset by rememberSaveable(activity.id) { mutableStateOf<String?>(null) }
    var presets by remember { mutableStateOf<List<SharePreset>>(emptyList()) }
    var presetsReady by remember { mutableStateOf(false) }
    var presetLoadFailed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SharePreset?>(null) }
    var pendingDocumentName by rememberSaveable { mutableStateOf<String?>(null) }
    val renderable by produceState<ShareRenderableActivity?>(null,activity,detail,config.privacy) {
        value=withContext(Dispatchers.Default) { ShareRenderableActivity.from(activity,detail,config.privacy) }
    }
    LaunchedEffect(store) {
        try { presets=withContext(Dispatchers.IO) { store.load() }; presetsReady=true }
        catch(error:Exception) {
            if(error is CancellationException) throw error
            presetLoadFailed=true
            message="Saved presets could not be read. Existing presets have been kept."
        }
    }
    val source=renderable?.takeIf { it.id==activity.id && it.privacy==config.privacy }
    val effective=source?.let { config.forActivity(it) }
    val geometry=effective?.let(ShareCardRenderer::geometry)
    val request=if(source!=null && effective!=null && geometry!=null && online && !effective.transparent && effective.background==ShareBackground.MAP &&
        effective.routeMode!=ShareRouteMode.HIDDEN && source.route.pointCount>=2 && geometry.route.height()>0)
        ShareMapRequest(effective.routeMode,geometry.route.width().toInt(),geometry.route.height().toInt(),effective.routeColor,effective.stroke,effective.zoom,effective.position,effective.verticalPosition) else null
    // Template, metric-count and ratio changes may alter the map rectangle; unrelated controls reuse the map.
    val mapCache=remember(source) { android.util.LruCache<ShareMapRequest,Bitmap>(4) }
    val mapResult by produceState<ShareMapResult?>(null,source,request) {
        val current=request
        if(current==null || source==null || effective==null) { value=null; return@produceState }
        value=ShareMapResult(source,current,null,true)
        try {
            val snapshot=mapCache.get(current) ?: shareMapSnapshot(context,source,effective).also { mapCache.put(current,it) }
            value=ShareMapResult(source,current,snapshot,false)
        }
        catch(error:TimeoutCancellationException) { value=ShareMapResult(source,current,null,false) }
        catch(error:Exception) {
            if(error is CancellationException) throw error
            value=ShareMapResult(source,current,null,false)
        }
    }
    val map=mapResult?.takeIf { it.source===source && it.request==request }?.bitmap
    val mapLoading=request!=null && (mapResult?.source!==source || mapResult?.request!=request || mapResult?.loading==true)
    val cardResult by produceState<ShareCardResult?>(null,source,effective,map) {
        if(source!=null && effective!=null) {
            try { value=withContext(Dispatchers.Default) { ShareCardResult(source,effective,map,ShareCardRenderer.render(source,effective,map,width=540)) } }
            catch(error:Exception) {
                if(error is CancellationException) throw error
                value=null; message="Could not render the card. Try another template."
            }
        }
    }
    val card=cardResult?.takeIf { it.source===source && it.state==effective && it.map===map }?.bitmap
    val exportReady=card!=null && !mapLoading && !busy
    BackHandler { if(!busy) onBack() }

    fun savePresets(next:List<SharePreset>, onSaved:()->Unit) {
        busy=true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(next) }
                presets=next; message="Preset saved"; onSaved()
            } catch(error:Exception) {
                if(error is CancellationException) throw error
                message=error.message ?: "Could not save preset. Try again."
            } finally { busy=false }
        }
    }
    val documentLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val file=pendingDocumentName?.takeIf { it.matches(Regex("StrideLab-[a-fA-F0-9-]+\\.png")) }
            ?.let { File(File(context.cacheDir,"share-studio"),it) }
        pendingDocumentName=null
        if(uri==null) { file?.delete(); busy=false }
        else {
            busy=true
            scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    try {
                        check(file!=null && file.isFile) { "Pending image unavailable" }
                        file.inputStream().use { input ->
                            checkNotNull(context.contentResolver.openOutputStream(uri)).use { output -> input.copyTo(output) }
                        }
                    }
                    catch(error:Exception) {
                        runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri) }
                        throw error
                    }
                }
                message="Image saved"
            } catch(error:Exception) {
                if(error is CancellationException) throw error
                message="Could not save image. Check storage and try again."
            } finally { file?.delete(); busy=false }
            }
        }
    }

    MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(primary=Color(ShareCardPalette.LIME),onPrimary=Color.Black,
        secondaryContainer=Color(0xFF26381C),onSecondaryContainer=Color(ShareCardPalette.WHITE))) {
    Surface(color=Color.Black,contentColor=Color(ShareCardPalette.WHITE),modifier=Modifier.fillMaxSize().testTag("share-studio").semantics { paneTitle="Share Studio" }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack,enabled=!busy,modifier=Modifier.testTag("share-studio-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back to activity")
                }
                Column(Modifier.weight(1f).padding(vertical=8.dp)) {
                    Text("Share Studio",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    activePreset?.let { id -> presets.firstOrNull { it.id==id }?.let {
                        Text(it.name,style=MaterialTheme.typography.labelMedium,color=Color(ShareCardPalette.MUTED))
                    } }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal=16.dp),contentAlignment=Alignment.Center) {
                val ratio=effective?.ratio ?: config.ratio
                val width=minOf(maxWidth,maxHeight*(1080f/ratio.height))
                Box(Modifier.width(width).aspectRatio(1080f/ratio.height).background(Color(0xFF101A15),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {
                    if(card!=null) Image(card.asImageBitmap(),contentDescription="Share card preview. "+source?.metrics?.filterKeys { it in effective?.metrics.orEmpty() }?.values?.joinToString(". "),
                        contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize().testTag("share-card-preview"))
                    else CircularProgressIndicator(color=Color(ShareCardPalette.LIME))
                }
            }
            val status=when {
                mapLoading -> "Loading Activity Dark map…"
                effective?.routeMode==ShareRouteMode.HIDDEN -> "Route hidden · Full activity stats"
                !config.transparent && config.background==ShareBackground.MAP && map==null -> "Street map unavailable · showing route only"
                else -> "Route trimmed · Full activity stats"
            }
            Text(status,style=MaterialTheme.typography.labelSmall,color=Color(ShareCardPalette.MUTED),
                modifier=Modifier.padding(horizontal=16.dp,vertical=3.dp).semantics { liveRegion=LiveRegionMode.Polite })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ShareTemplate.entries.forEach { template ->
                    FilterChip(selected=config.template==template,onClick={ configure(config.selectTemplate(template)); activePreset=null },
                        label={ Text(if(template==ShareTemplate.STATS) "Performance" else template.label) },modifier=Modifier.heightIn(min=48.dp).testTag("share-template-${template.name}"))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp)) {
                ShareSection.entries.forEach { tool ->
                    TextButton(onClick={ section=tool },contentPadding=PaddingValues(0.dp),modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("share-section-${tool.name}")) {
                        Text(tool.label,style=MaterialTheme.typography.labelMedium,color=if(section==tool) Color(ShareCardPalette.LIME) else Color(ShareCardPalette.MUTED))
                    }
                }
            }
            Surface(color=Color(0xFF101A15),shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp),modifier=Modifier.fillMaxWidth().height(142.dp).animateContentSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    when(section) {
                        ShareSection.LAYOUT -> {
                            Text("Composition",style=MaterialTheme.typography.labelLarge)
                            ShareChoices(config.template.variants,config.variant,{it.label}) { configure(config.selectVariant(it)) }
                            Text("Aspect ratio",style=MaterialTheme.typography.labelLarge)
                            ShareChoices(ShareRatio.entries,config.ratio,{it.label}) { configure(config.copy(ratio=it)) }
                            ShareChoices(ShareLayout.entries,config.layout,{it.label}) { configure(config.copy(layout=it)) }
                        }
                        ShareSection.MAP -> {
                            Text("Trimmed endpoints do not guarantee location anonymity.",style=MaterialTheme.typography.bodySmall,color=Color(ShareCardPalette.MUTED))
                            ShareChoices(SharePrivacy.entries,config.privacy,{it.label}) { configure(config.copy(privacy=it)) }
                            ShareChoices(source?.availableModes?.toList().orEmpty(),effective?.routeMode,{it.label}) { configure(config.copy(routeMode=it)) }
                            ShareChoices(ShareBackground.entries,config.background,{it.label}) { configure(config.copy(background=it)) }
                            Text("Route color and line",style=MaterialTheme.typography.labelLarge)
                            ShareChoices(ShareRouteColor.entries,config.routeColor,{it.label}) { configure(config.copy(routeColor=it)) }
                            ShareChoices(ShareStroke.entries,config.stroke,{it.label}) { configure(config.copy(stroke=it)) }
                            Text("Framing",style=MaterialTheme.typography.labelLarge)
                            ShareChoices(ShareZoom.entries,config.zoom,{it.label}) { configure(config.copy(zoom=it)) }
                            ShareChoices(SharePosition.entries,config.position,{it.label}) { configure(config.copy(position=it)) }
                            ShareChoices(ShareVerticalPosition.entries,config.verticalPosition,{it.label}) { configure(config.copy(verticalPosition=it)) }
                        }
                        ShareSection.STATS -> {
                            Text("Full activity stats · select at least one",style=MaterialTheme.typography.bodySmall,color=Color(ShareCardPalette.MUTED))
                            Text("Featured metric",style=MaterialTheme.typography.labelLarge)
                            ShareChoices(source?.metrics?.keys?.filter { it in setOf(ShareMetric.DISTANCE,ShareMetric.DURATION,ShareMetric.PACE,ShareMetric.HR,ShareMetric.ELEVATION) }.orEmpty(),config.featuredMetric,{it.label}) { configure(config.copy(featuredMetric=it,metrics=config.metrics+it)) }
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(source!=null) source.metrics.keys.forEach { metric ->
                                    val selected=metric in effective?.metrics.orEmpty()
                                    val next=effective?.metrics.orEmpty().let { if(selected) it-metric else it+metric }
                                    val allowed=effective!=null && effective.copy(metrics=next).forActivity(source).metrics==next
                                    FilterChip(selected=selected,enabled=allowed || !selected,onClick={ configure(config.copy(metrics=if(selected) config.metrics-metric else config.metrics+metric)) },
                                        label={ Text(metric.label) },modifier=Modifier.heightIn(min=48.dp))
                                }
                            }
                        }
                        ShareSection.STYLE -> {
                            Text("Background",style=MaterialTheme.typography.labelLarge)
                            ShareChoices(ShareBackground.entries,config.background,{it.label}) { configure(config.copy(background=it)) }
                            Text("Typography",style=MaterialTheme.typography.labelLarge)
                            ShareChoices(ShareTypography.entries,config.typography,{it.label}) { configure(config.copy(typography=it)) }
                            Text("Branding",style=MaterialTheme.typography.labelLarge)
                            ShareChoices(ShareBranding.entries,config.branding,{it.label}) { configure(config.copy(branding=it)) }
                        }
                        ShareSection.EXPORT -> {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Switch(checked=config.transparent,onCheckedChange={ configure(config.copy(transparent=it)) },modifier=Modifier.testTag("share-transparent"))
                                Text("Transparent PNG · route and stats",modifier=Modifier.padding(start=12.dp))
                            }
                            OutlinedButton(onClick={ message=null; name=presets.firstOrNull { it.id==activePreset }?.name.orEmpty(); naming=true },
                                enabled=presetsReady && !busy,modifier=Modifier.heightIn(min=48.dp).testTag("share-save-preset")) { Text("Save preset") }
                            if(presetLoadFailed) Text("Saved presets unavailable; stored designs have been kept.")
                            else if(presets.isNotEmpty()) {
                                ShareChoices(presets,presets.firstOrNull { it.id==activePreset },{it.name}) { preset -> configure(preset.state); activePreset=preset.id }
                                presets.firstOrNull { it.id==activePreset }?.let { selected ->
                                    TextButton(onClick={ message=null; deleting=selected },enabled=!busy) { Text("Delete selected preset") }
                                }
                            }
                        }
                    }
                }
            }
            message?.let { text ->
                Text(text,color=Color(ShareCardPalette.LIME),modifier=Modifier.padding(horizontal=16.dp)
                    .semantics { liveRegion=LiveRegionMode.Polite }.testTag("share-message"))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled=exportReady,onClick={
                    val currentSource=source ?: return@OutlinedButton
                    val currentState=effective ?: return@OutlinedButton
                    val currentMap=map
                    busy=true; message=null
                    scope.launch {
                        var image:Bitmap?=null
                        try {
                            image=withContext(Dispatchers.Default) { ShareCardRenderer.render(currentSource,currentState,currentMap) }
                            if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) {
                                val file=withContext(Dispatchers.IO) { ShareImageExport.cachePng(context,image) }
                                pendingDocumentName=file.name
                                documentLauncher.launch(file.name)
                            } else {
                                withContext(Dispatchers.IO) { ShareImageExport.saveToGallery(context,image) }
                                message="Saved to Pictures / StrideLab"; busy=false
                            }
                        } catch(error:Exception) {
                            if(error is CancellationException) throw error
                            message="Could not save image. Check storage and try again."; busy=false
                        } finally { image?.recycle() }
                    }
                },modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("share-save-image")) { Text("Save Image") }
                Button(enabled=exportReady,onClick={
                    val currentSource=source ?: return@Button
                    val currentState=effective ?: return@Button
                    val currentMap=map
                    busy=true; message=null
                    scope.launch {
                        var image:Bitmap?=null
                        try {
                            image=withContext(Dispatchers.Default) { ShareCardRenderer.render(currentSource,currentState,currentMap) }
                            val intent=withContext(Dispatchers.IO) { ShareImageExport.shareIntent(context,image) }
                            context.startActivity(Intent.createChooser(intent,"Share activity"))
                        } catch(error:Exception) {
                            if(error is CancellationException) throw error
                            message="Could not share image. Try Save Image instead."
                        } finally { image?.recycle(); busy=false }
                    }
                },colors=ButtonDefaults.buttonColors(containerColor=Color(ShareCardPalette.LIME),contentColor=Color.Black),
                    modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("share-send")) { Text(if(busy) "Working…" else "Share") }
            }
        }
    }
    if(naming) AlertDialog(onDismissRequest={ if(!busy) naming=false },title={ Text("Save Preset") },
        text={ OutlinedTextField(value=name,onValueChange={ if(it.length<=60) name=it },label={ Text("Preset name") },
            supportingText={ Text(message ?: "${name.length}/60") },singleLine=true,modifier=Modifier.testTag("share-preset-name")) },
        confirmButton={
            Column {
                TextButton(enabled=name.isNotBlank() && !busy && presets.size<100,onClick={
                    val preset=SharePreset(UUID.randomUUID().toString(),name.trim(),config)
                    savePresets(presets+preset) { activePreset=preset.id; naming=false }
                }) { Text("Save new") }
                if(activePreset!=null) TextButton(enabled=name.isNotBlank() && !busy,onClick={
                    val selected=activePreset
                    savePresets(presets.map { if(it.id==selected) it.copy(name=name.trim(),state=config) else it }) { naming=false }
                }) { Text("Update selected") }
            }
        },dismissButton={ TextButton(enabled=!busy,onClick={ naming=false }) { Text("Cancel") } })
    deleting?.let { selected -> AlertDialog(onDismissRequest={ if(!busy) deleting=null },
        title={ Text("Delete preset?") },text={ Column { Text(selected.name); message?.let { Text(it) } } },
        confirmButton={ TextButton(enabled=!busy,onClick={
            savePresets(presets.filterNot { it.id==selected.id }) { deleting=null; activePreset=null; message="Preset deleted" }
        }) { Text("Delete") } },dismissButton={ TextButton(enabled=!busy,onClick={ deleting=null }) { Text("Cancel") } }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ShareChoices(values:List<T>, selected:T?, label:(T)->String, onSelect:(T)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            FilterChip(selected=value==selected,onClick={ onSelect(value) },label={ Text(label(value)) },modifier=Modifier.heightIn(min=48.dp))
        }
    }
}
