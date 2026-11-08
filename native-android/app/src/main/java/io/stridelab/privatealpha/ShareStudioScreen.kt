package io.stridelab.privatealpha

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File

private enum class ShareSection(val label:String) {
    TEMPLATE("Template"), LAYOUT("Layout"), MAP("Route"), STATS("Stats"), STYLE("Style"), EXPORT("Export")
}
private enum class ShareRoutePanel(val label:String) {
    STYLE("Style"), MAP("Map"), PRIVACY("Privacy")
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
    var section by rememberSaveable { mutableStateOf(ShareSection.TEMPLATE) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var presetsOpen by rememberSaveable { mutableStateOf(false) }
    var routePanel by rememberSaveable { mutableStateOf(ShareRoutePanel.PRIVACY) }
    var presetMenuId by remember { mutableStateOf<String?>(null) }
    var activePreset by rememberSaveable(activity.id) { mutableStateOf<String?>(null) }
    var presets by remember { mutableStateOf<List<SharePreset>>(emptyList()) }
    var presetsReady by remember { mutableStateOf(false) }
    var presetLoadFailed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SharePreset?>(null) }
    var renaming by remember { mutableStateOf<SharePreset?>(null) }
    var updating by remember { mutableStateOf<SharePreset?>(null) }
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
    BackHandler {
        if(!busy) when {
            presetsOpen -> presetsOpen=false
            sheetOpen -> sheetOpen=false
            else -> onBack()
        }
    }

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
    Surface(color=Color.Black,contentColor=Color(ShareCardPalette.WHITE),modifier=Modifier.fillMaxSize()
        .testTag("share-studio").semantics { paneTitle="Share Studio" }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            // Open directly on the completed Route Focus poster. A tool is optional.
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),
                verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={ if(presetsOpen) presetsOpen=false else if(sheetOpen) sheetOpen=false else onBack() },
                    enabled=!busy,modifier=Modifier.testTag("share-studio-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back to activity")
                }
                Column(Modifier.weight(1f).padding(start=4.dp)) {
                    Text("Share Studio",style=MaterialTheme.typography.titleMedium,
                        fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(activePreset?.let { id -> presets.firstOrNull { it.id==id }?.name }
                        ?: activity.type.replaceFirstChar { it.uppercase() },
                        style=MaterialTheme.typography.labelSmall,color=Color(ShareCardPalette.MUTED),
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                TextButton(onClick={ sheetOpen=false; presetsOpen=true },enabled=!busy,
                    modifier=Modifier.heightIn(min=48.dp).testTag("share-presets-open"),
                    contentPadding=PaddingValues(horizontal=6.dp)) {
                    Text("Presets",style=MaterialTheme.typography.labelMedium)
                }
                Button(onClick={ presetsOpen=false; section=ShareSection.EXPORT; sheetOpen=true },enabled=!busy,
                    contentPadding=PaddingValues(horizontal=12.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=Color(ShareCardPalette.LIME),contentColor=Color.Black),
                    modifier=Modifier.heightIn(min=48.dp).testTag("share-export-open")) { Text("Export") }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal=20.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
                val ratio=effective?.ratio ?: config.ratio
                val width=minOf(maxWidth,maxHeight*(1080f/ratio.height))
                Box(Modifier.width(width).aspectRatio(1080f/ratio.height)
                    .background(Color(0xFF101A15),RoundedCornerShape(10.dp)),
                    contentAlignment=Alignment.Center) {
                    if(card!=null) Image(card.asImageBitmap(),
                        contentDescription="Share card preview. "+source?.metrics
                            ?.filterKeys { it in effective?.metrics.orEmpty() }?.values?.joinToString(". "),
                        contentScale=ContentScale.Fit,
                        modifier=Modifier.fillMaxSize().testTag("share-card-preview"))
                    else CircularProgressIndicator(color=Color(ShareCardPalette.LIME))
                    if(card!=null && source!=null && source.route.pointCount>=2 &&
                        effective?.routeMode!=ShareRouteMode.HIDDEN) {
                        Surface(color=Color(0xEF101713),shape=RoundedCornerShape(50),
                            border=BorderStroke(1.dp,Color(ShareCardPalette.LIME)),
                            modifier=Modifier.align(Alignment.BottomCenter).padding(8.dp)
                                .testTag("share-privacy-indicator")) {
                            Text("Route trimmed · "+config.privacy.label,
                                style=MaterialTheme.typography.labelSmall,
                                color=Color(ShareCardPalette.LIME),
                                modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp))
                        }
                    }
                }
            }
            val status=when {
                source==null -> "Preparing privacy-safe preview…"
                mapLoading -> "Loading Activity Dark map…"
                source.route.pointCount<2 -> "No GPS route · Saved activity stats only"
                effective?.routeMode==ShareRouteMode.HIDDEN -> "Route hidden · Full activity stats"
                !config.transparent && config.background==ShareBackground.MAP && map==null ->
                    "Street map unavailable · showing route only"
                else -> "Route trimmed · "+config.privacy.label+" · Full activity stats"
            }
            Text(status,style=MaterialTheme.typography.labelSmall,color=Color(ShareCardPalette.MUTED),
                modifier=Modifier.padding(horizontal=16.dp,vertical=4.dp)
                    .semantics { liveRegion=LiveRegionMode.Polite }.testTag("share-preview-status"))
            if(presetsOpen) {
                Surface(color=Color(0xFF141817),shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp),
                    modifier=Modifier.fillMaxWidth().testTag("share-presets-sheet")) {
                    Column(Modifier.fillMaxWidth().heightIn(max=310.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal=16.dp,vertical=12.dp),
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("Presets",style=MaterialTheme.typography.titleMedium,
                                fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                            TextButton(onClick={ presetsOpen=false }) { Text("Close") }
                        }
                        Text("Saved poster settings only · no GPS samples",
                            style=MaterialTheme.typography.bodySmall,color=Color(ShareCardPalette.MUTED))
                        OutlinedButton(onClick={ message=null; name=""; naming=true },
                            enabled=presetsReady && !busy && presets.size<100,
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("share-save-preset")) {
                            Text("Save current as preset")
                        }
                        if(presetLoadFailed) {
                            Text("Saved presets could not be loaded. Existing designs have been kept.")
                            TextButton(onClick={
                                scope.launch {
                                    try {
                                        presets=withContext(Dispatchers.IO) { store.load() }
                                        presetsReady=true; presetLoadFailed=false; message=null
                                    } catch(error:Exception) {
                                        if(error is CancellationException) throw error
                                        message="Could not load presets. Try again."
                                    }
                                }
                            },enabled=!busy) { Text("Retry loading") }
                        } else if(presetsReady && presets.isEmpty()) {
                            Text("No saved presets yet",color=Color(ShareCardPalette.MUTED))
                        }
                        if(!presetLoadFailed) presets.forEach { preset ->
                            val active=activePreset==preset.id && config==preset.state
                            val shape=RoundedCornerShape(12.dp)
                            Row(Modifier.fillMaxWidth()
                                .background(if(active) Color(0xFF253516) else Color(0xFF1D2220),shape)
                                .border(BorderStroke(1.dp,if(active) Color(ShareCardPalette.LIME)
                                    else Color(0xFF3C4540)),shape).padding(horizontal=10.dp,vertical=8.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(preset.name,style=MaterialTheme.typography.bodyMedium,
                                        fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text((if(preset.state.template==ShareTemplate.STATS) "Performance"
                                        else preset.state.template.label)+" · "+preset.state.ratio.label,
                                        style=MaterialTheme.typography.labelSmall,
                                        color=Color(ShareCardPalette.MUTED))
                                }
                                TextButton(onClick={
                                    configure(preset.state); activePreset=preset.id; presetsOpen=false
                                },enabled=!busy,modifier=Modifier.heightIn(min=48.dp)
                                    .testTag("share-preset-apply-"+preset.id)) { Text("Apply") }
                                Box {
                                    TextButton(onClick={ presetMenuId=preset.id },enabled=!busy,
                                        modifier=Modifier.heightIn(min=48.dp)) { Text("More") }
                                    DropdownMenu(expanded=presetMenuId==preset.id,
                                        onDismissRequest={ presetMenuId=null }) {
                                        DropdownMenuItem(text={ Text("Rename") },onClick={
                                            presetMenuId=null; renaming=preset; name=preset.name
                                        })
                                        DropdownMenuItem(text={ Text("Update with current") },onClick={
                                            presetMenuId=null; updating=preset
                                        })
                                        DropdownMenuItem(text={ Text("Delete") },onClick={
                                            presetMenuId=null; deleting=preset
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            } else if(sheetOpen) {
                Surface(color=Color(0xFF141817),shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp),
                    modifier=Modifier.fillMaxWidth().testTag("share-tool-sheet")) {
                    Column(Modifier.fillMaxWidth().heightIn(max=300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal=16.dp,vertical=12.dp),
                        verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(section.label,style=MaterialTheme.typography.titleMedium,
                                fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                            TextButton(onClick={ sheetOpen=false }) { Text("Close") }
                        }
                        when(section) {
                            ShareSection.TEMPLATE -> {
                                Text("Choose a poster composition",
                                    color=Color(ShareCardPalette.MUTED),
                                    style=MaterialTheme.typography.bodySmall)
                                ShareTemplate.entries.forEach { template ->
                                    ShareStudioOptionRow(
                                        title=if(template==ShareTemplate.STATS) "Performance" else template.label,
                                        detail=when(template) {
                                            ShareTemplate.ROUTE -> "GPS route and distance lead"
                                            ShareTemplate.STATS -> "Bold activity metrics"
                                            ShareTemplate.MINIMAL -> "Quiet layout with generous space"
                                        },
                                        selected=config.template==template,
                                        tag="share-template-"+template.name,
                                        onClick={ configure(config.selectTemplate(template)); activePreset=null }
                                    )
                                }
                            }
                            ShareSection.LAYOUT -> {
                                Text("Aspect ratio",style=MaterialTheme.typography.labelLarge)
                                ShareChoices(ShareRatio.entries,config.ratio,{it.label}) {
                                    configure(config.copy(ratio=it))
                                }
                                Text("Composition",style=MaterialTheme.typography.labelLarge)
                                ShareChoices(config.template.variants,config.variant,{it.label}) {
                                    configure(config.selectVariant(it))
                                }
                                Text("Spacing",style=MaterialTheme.typography.labelLarge)
                                ShareChoices(ShareLayout.entries,config.layout,{it.label}) {
                                    configure(config.copy(layout=it))
                                }
                            }
                            ShareSection.MAP -> {
                                ShareChoices(ShareRoutePanel.entries,routePanel,{it.label}) {
                                    routePanel=it
                                }
                                when(routePanel) {
                                    ShareRoutePanel.PRIVACY -> {
                                        Text("Trim route ends",style=MaterialTheme.typography.labelLarge)
                                        ShareChoices(SharePrivacy.entries,config.privacy,{it.label}) {
                                            configure(config.copy(privacy=it))
                                        }
                                        Text("Start and finish markers are hidden. Trimming removes endpoints from the share image, not from the saved activity.",
                                            style=MaterialTheme.typography.bodySmall,
                                            color=Color(ShareCardPalette.MUTED))
                                        Text("Trimming does not guarantee location anonymity.",
                                            style=MaterialTheme.typography.bodySmall,
                                            color=Color(ShareCardPalette.MUTED))
                                    }
                                    ShareRoutePanel.STYLE -> {
                                        Text("Route presentation",style=MaterialTheme.typography.labelLarge)
                                        ShareChoices(source?.availableModes?.toList().orEmpty(),
                                            effective?.routeMode,{it.label}) {
                                            configure(config.copy(routeMode=it))
                                        }
                                        Text("Route color",style=MaterialTheme.typography.labelLarge)
                                        ShareChoices(ShareRouteColor.entries,config.routeColor,{it.label}) {
                                            configure(config.copy(routeColor=it))
                                        }
                                        Text("Route thickness",style=MaterialTheme.typography.labelLarge)
                                        ShareChoices(ShareStroke.entries,config.stroke,{it.label}) {
                                            configure(config.copy(stroke=it))
                                        }
                                    }
                                    ShareRoutePanel.MAP -> {
                                        Text("Background",style=MaterialTheme.typography.labelLarge)
                                        ShareChoices(ShareBackground.entries,config.background,{it.label}) {
                                            configure(config.copy(background=it))
                                        }
                                        Text("Route scale",style=MaterialTheme.typography.labelLarge)
                                        ShareChoices(ShareZoom.entries,config.zoom,{it.label}) {
                                            configure(config.copy(zoom=it))
                                        }
                                        Text("Horizontal position",style=MaterialTheme.typography.labelLarge)
                                        ShareChoices(SharePosition.entries,config.position,{it.label}) {
                                            configure(config.copy(position=it))
                                        }
                                        Text("Vertical position",style=MaterialTheme.typography.labelLarge)
                                        ShareChoices(ShareVerticalPosition.entries,config.verticalPosition,{it.label}) {
                                            configure(config.copy(verticalPosition=it))
                                        }
                                    }
                                }
                            }
                            ShareSection.STATS -> {
                                val numeric=setOf(ShareMetric.DISTANCE,ShareMetric.DURATION,
                                    ShareMetric.PACE,ShareMetric.HR,ShareMetric.ELEVATION)
                                val choices=source?.metrics?.keys?.filter { it in numeric }.orEmpty()
                                val chosen=choices.filter { it in config.metrics }
                                Text("Select 1–4 recorded metrics. Missing fields are not invented.",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=Color(ShareCardPalette.MUTED))
                                choices.forEach { metric ->
                                    val checked=metric in config.metrics
                                    ShareStudioOptionRow(title=metric.label,
                                        detail=source?.metrics?.get(metric),
                                        selected=checked,
                                        enabled=if(checked) chosen.size>1 else chosen.size<4,
                                        tag="share-metric-"+metric.name,
                                        onClick={
                                            configure(config.copy(metrics=if(checked) config.metrics-metric
                                                else config.metrics+metric))
                                        })
                                }
                                if(choices.isEmpty()) Text("No recorded metrics available")
                                Text("Featured metric",style=MaterialTheme.typography.labelLarge)
                                ShareChoices(chosen,config.featuredMetric,{it.label}) {
                                    configure(config.copy(featuredMetric=it))
                                }
                            }
                            ShareSection.STYLE -> {
                                Text("Background",style=MaterialTheme.typography.labelLarge)
                                ShareChoices(ShareBackground.entries,config.background,{it.label}) {
                                    configure(config.copy(background=it))
                                }
                                Text("Typography",style=MaterialTheme.typography.labelLarge)
                                ShareChoices(ShareTypography.entries,config.typography,{it.label}) {
                                    configure(config.copy(typography=it))
                                }
                                Text("Branding",style=MaterialTheme.typography.labelLarge)
                                ShareChoices(ShareBranding.entries,config.branding,{it.label}) {
                                    configure(config.copy(branding=it))
                                }
                            }
                            ShareSection.EXPORT -> {
                                Text("PNG · "+config.ratio.dimensions.first+" × "+
                                    config.ratio.dimensions.second+" · Standard",
                                    style=MaterialTheme.typography.bodyMedium)
                                Text("Ratio",style=MaterialTheme.typography.labelLarge)
                                ShareChoices(ShareRatio.entries,config.ratio,{it.label}) {
                                    configure(config.copy(ratio=it))
                                }
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Switch(checked=config.transparent,
                                        onCheckedChange={ configure(config.copy(transparent=it)) },
                                        modifier=Modifier.testTag("share-transparent"))
                                    Column(Modifier.padding(start=12.dp)) {
                                        Text("Transparent PNG",style=MaterialTheme.typography.bodyMedium)
                                        Text("Route and recorded stats only · no map",
                                            style=MaterialTheme.typography.labelSmall,
                                            color=Color(ShareCardPalette.MUTED))
                                    }
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
                    }
                }
            }
            message?.let { info ->
                Text(info,color=Color(ShareCardPalette.LIME),
                    modifier=Modifier.padding(horizontal=16.dp,vertical=4.dp)
                        .semantics { liveRegion=LiveRegionMode.Polite }.testTag("share-message"))
            }
            // Always-visible editing entry points. Canvas taps are never required.
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=5.dp),
                horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                ShareSection.entries.forEach { tool ->
                    TextButton(onClick={ presetsOpen=false; section=tool; sheetOpen=true },
                        modifier=Modifier.weight(1f).heightIn(min=52.dp)
                            .testTag("share-section-"+tool.name),
                        shape=RoundedCornerShape(10.dp),
                        colors=ButtonDefaults.textButtonColors(
                            containerColor=if(sheetOpen && section==tool) Color(0xFF2A371A) else Color.Transparent),
                        contentPadding=PaddingValues(horizontal=2.dp)) {
                        Text(tool.label,style=MaterialTheme.typography.labelSmall,
                            color=if(sheetOpen && section==tool) Color(ShareCardPalette.LIME)
                                else Color(ShareCardPalette.MUTED),maxLines=1)
                    }
                }
            }
        }
    }
    if(naming) AlertDialog(onDismissRequest={ if(!busy) naming=false },
        title={ Text("Save current as preset") },
        text={ OutlinedTextField(value=name,onValueChange={ if(it.length<=60) name=it },
            label={ Text("Preset name") },
            supportingText={ Text(message ?: (name.length.toString()+"/60")) },
            singleLine=true,modifier=Modifier.testTag("share-preset-name")) },
        confirmButton={ TextButton(enabled=name.isNotBlank() && !busy && presetsReady &&
            !presetLoadFailed && presets.size<100,onClick={
            val preset=SharePreset(UUID.randomUUID().toString(),name.trim(),config)
            savePresets(presets+preset) { activePreset=preset.id; naming=false }
        }) { Text("Save") } },
        dismissButton={ TextButton(enabled=!busy,onClick={ naming=false }) { Text("Cancel") } })
    renaming?.let { selected -> AlertDialog(onDismissRequest={ if(!busy) renaming=null },
        title={ Text("Rename preset") },
        text={ OutlinedTextField(value=name,onValueChange={ if(it.length<=60) name=it },
            label={ Text("Preset name") },singleLine=true) },
        confirmButton={ TextButton(enabled=name.isNotBlank() && !busy,onClick={
            savePresets(presets.map { if(it.id==selected.id) it.copy(name=name.trim()) else it }) {
                renaming=null
            }
        }) { Text("Rename") } },
        dismissButton={ TextButton(enabled=!busy,onClick={ renaming=null }) { Text("Cancel") } }) }
    updating?.let { selected -> AlertDialog(onDismissRequest={ if(!busy) updating=null },
        title={ Text("Update saved preset?") },
        text={ Text("Replace the layout settings in "+selected.name+
            " with your current poster settings? The original activity is unchanged.") },
        confirmButton={ TextButton(enabled=!busy,onClick={
            savePresets(presets.map { if(it.id==selected.id) it.copy(state=config) else it }) {
                activePreset=selected.id; updating=null
            }
        }) { Text("Update") } },
        dismissButton={ TextButton(enabled=!busy,onClick={ updating=null }) { Text("Cancel") } }) }
    deleting?.let { selected -> AlertDialog(onDismissRequest={ if(!busy) deleting=null },
        title={ Text("Delete preset?") },text={ Text(selected.name) },
        confirmButton={ TextButton(enabled=!busy,onClick={
            savePresets(presets.filterNot { it.id==selected.id }) {
                deleting=null
                if(activePreset==selected.id) activePreset=null
                message="Preset deleted"
            }
        }) { Text("Delete") } },
        dismissButton={ TextButton(enabled=!busy,onClick={ deleting=null }) { Text("Cancel") } }) }
    }
}

@Composable
private fun ShareStudioOptionRow(
    title:String, detail:String?=null, selected:Boolean, enabled:Boolean=true,
    tag:String="", onClick:()->Unit
) {
    Surface(onClick=onClick,enabled=enabled,
        color=if(selected) Color(0xFF253516) else Color(0xFF1C211F),
        contentColor=Color(ShareCardPalette.WHITE),
        shape=RoundedCornerShape(12.dp),
        border=BorderStroke(1.dp,if(selected) Color(ShareCardPalette.LIME)
            else Color(0xFF3D4540)),
        modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag)) {
        Row(Modifier.padding(horizontal=14.dp,vertical=11.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Text(title,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,
                fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,
                color=if(selected) Color(ShareCardPalette.LIME) else Color(ShareCardPalette.WHITE))
            if(detail!=null) Text(detail,style=MaterialTheme.typography.labelMedium,
                color=Color(ShareCardPalette.MUTED),modifier=Modifier.padding(start=8.dp))
        }
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
