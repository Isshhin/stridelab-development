package io.stridelab.privatealpha

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private enum class ShareSection(val label: String) {
    TEMPLATES("Templates"),
    VARIANT("Variant"),
    RATIO("Ratio"),
    ROUTE("Route"),
    METRICS("Metrics"),
    STYLE("Style"),
    BRANDING("Branding"),
    PRESETS("Presets")
}

private data class ShareMapRequest(val mode: ShareRouteMode, val width: Int, val height: Int)
private data class ShareMapResult(val source: ShareRenderableActivity, val request: ShareMapRequest, val bitmap: Bitmap?, val loading: Boolean)
private data class ShareCardResult(val source: ShareRenderableActivity, val state: ShareStudioState, val map: Bitmap?, val photo: Bitmap?, val bitmap: Bitmap)

@Composable
private fun studioChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = StrideLabSurfaceTokens.surfaceSubtle,
    labelColor = StrideLabSurfaceTokens.inkSecondary,
    selectedContainerColor = Color(0x24D8FB70),
    selectedLabelColor = StrideLabSurfaceTokens.lime
)

@Composable
private fun studioChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = StrideLabSurfaceTokens.hairline,
    selectedBorderColor = StrideLabSurfaceTokens.lime,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.dp
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShareStudioScreen(
    activity: ActivityRecord,
    detail: ActivityDetail?,
    online: Boolean,
    onBack: () -> Unit,
    assessment: ActivityWorkoutAssessment? = null,
    userLabel: UserActivityWorkoutLabel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { SharePresetStore(context) }
    val offlineMaps = remember(context) {
        StrideOfflineMapStore.get(context).also { it.initialize(); it.refresh() }
    }
    var configJson by rememberSaveable(activity.id) {
        mutableStateOf(SharePresetCodec.encode(listOf(SharePreset("draft", "Draft", ShareStudioState()))))
    }
    val config = remember(configJson) { SharePresetCodec.decode(configJson).single().state }
    // Keep the user's last nonzero trim choice when they explicitly reveal the full GPS route.
    // This is editor-only state; presets still store the actual privacy level, not activity data.
    var lastTrimPrivacy by rememberSaveable(activity.id) { mutableStateOf(SharePrivacy.STANDARD.name) }
    fun configure(value: ShareStudioState) {
        if (value.privacy != SharePrivacy.OFF) lastTrimPrivacy = value.privacy.name
        configJson = SharePresetCodec.encode(listOf(SharePreset("draft", "Draft", value)))
    }

    var section by rememberSaveable { mutableStateOf(ShareSection.TEMPLATES) }
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
    // User photos are editor-only and never embedded in saved presets or cloud records.
    var selectedPhoto by remember(activity.id) { mutableStateOf<Bitmap?>(null) }
    // Guides are never drawn into the actual PNG bitmap.
    var showStoryGuides by rememberSaveable(activity.id) { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    selectedPhoto = withContext(Dispatchers.IO) { SharePhotoImport.decode(context, uri) }
                    configure(config.copy(background = ShareBackground.PHOTO))
                    message = "Photo loaded for this edit. Presets never save its image."
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    message = "Could not open this photo. Choose another image."
                } finally {
                    busy = false
                }
            }
        }
    }

    val renderable by produceState<ShareRenderableActivity?>(null, activity, detail, config.privacy, assessment, userLabel) {
        value = withContext(Dispatchers.Default) {
            ShareRenderableActivity.from(activity, detail, config.privacy, assessment, userLabel)
        }
    }
    LaunchedEffect(store) {
        try {
            presets = withContext(Dispatchers.IO) { store.load() }
            presetsReady = true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            presetLoadFailed = true
            message = "Saved presets could not be read. Existing presets have been kept."
        }
    }

    // Never display/export a stale (less private) rendering after the privacy control changes.
    val source = renderable?.takeIf { it.id == activity.id && it.privacy == config.privacy }
    // Reuse a downloaded map only if one complete area covers the exact
    // already-trimmed displayed route. Otherwise use a clearly labeled trace.
    val hasOfflineMap = source?.let { offlineMaps.hasCompleteCoverage(it.route) } == true
    val effective = source?.let { config.forActivity(it) }
    val geometry = effective?.let(ShareCardRenderer::geometry)
    val request = if (
        source != null && effective != null && geometry != null &&
        (online || hasOfflineMap) &&
        effective.template != ShareTemplate.WORKOUT && effective.background == ShareBackground.MAP &&
        effective.routeMode != ShareRouteMode.HIDDEN && source.route.pointCount >= 2 && geometry.route.height() > 0
    ) {
        ShareMapRequest(effective.routeMode, geometry.route.width().toInt(), geometry.route.height().toInt())
    } else null

    // Template, metric-count and ratio changes may alter the map rectangle; unrelated controls reuse the map.
    val mapResult by produceState<ShareMapResult?>(null, source, request) {
        val current = request
        if (current == null || source == null || effective == null) {
            value = null
            return@produceState
        }
        value = ShareMapResult(source, current, null, true)
        try {
            value = ShareMapResult(source, current, shareMapSnapshot(context, source, effective), false)
        } catch (error: TimeoutCancellationException) {
            value = ShareMapResult(source, current, null, false)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            value = ShareMapResult(source, current, null, false)
        }
    }
    val map = mapResult?.takeIf { it.source === source && it.request == request }?.bitmap
    val mapLoading = request != null && (mapResult?.source !== source || mapResult?.request != request || mapResult?.loading == true)
    val cardResult by produceState<ShareCardResult?>(null, source, effective, map, selectedPhoto) {
        if (source != null && effective != null) {
            try {
                value = withContext(Dispatchers.Default) {
                    ShareCardResult(source, effective, map, selectedPhoto, ShareCardRenderer.render(source, effective, map, selectedPhoto))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                value = null
                message = "Could not render the card. Try another template."
            }
        }
    }
    val card = cardResult?.takeIf {
        it.source === source && it.state == effective && it.map === map && it.photo === selectedPhoto
    }?.bitmap?.takeIf { effective?.background != ShareBackground.PHOTO || selectedPhoto != null }
    val exportReady = card != null && !mapLoading && !busy
    BackHandler { if (!busy) onBack() }

    fun savePresets(next: List<SharePreset>, onSaved: () -> Unit) {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(next) }
                presets = next
                message = "Preset saved"
                onSaved()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = error.message ?: "Could not save preset. Try again."
            } finally {
                busy = false
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val file = pendingDocumentName?.takeIf { it.matches(Regex("StrideLab-[a-fA-F0-9-]+\\.png")) }
            ?.let { File(File(context.cacheDir, "share-studio"), it) }
        pendingDocumentName = null
        if (uri == null) {
            file?.delete()
            busy = false
        } else {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        try {
                            check(file != null && file.isFile) { "Pending image unavailable" }
                            file.inputStream().use { input ->
                                checkNotNull(context.contentResolver.openOutputStream(uri)).use { output -> input.copyTo(output) }
                            }
                        } catch (error: Exception) {
                            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
                            throw error
                        }
                    }
                    message = "Image saved"
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    message = "Could not save image. Check storage and try again."
                } finally {
                    file?.delete()
                    busy = false
                }
            }
        }
    }

    // Export a real PNG via the Android document picker (any supported API level).
    // Cache the rendered preview first so the exact image survives picker navigation.
    fun exportPngDocument(image: Bitmap) {
        busy = true
        message = null
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { ShareImageExport.cachePng(context, image) }
                pendingDocumentName = file.name
                documentLauncher.launch(file.name)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                pendingDocumentName?.let { File(File(context.cacheDir, "share-studio"), it).delete() }
                busy = false
                pendingDocumentName = null
                message = "Could not open PNG export. Try again."
            }
        }
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = StrideLabSurfaceTokens.lime,
            onPrimary = StrideLabSurfaceTokens.onLime,
            secondaryContainer = StrideLabSurfaceTokens.surfaceSubtle,
            onSecondaryContainer = StrideLabSurfaceTokens.ink,
            background = StrideLabSurfaceTokens.canvas,
            surface = StrideLabSurfaceTokens.surfaceElevated,
            onBackground = StrideLabSurfaceTokens.ink,
            onSurface = StrideLabSurfaceTokens.ink
        )
    ) {
        Surface(
            color = StrideLabSurfaceTokens.canvas,
            contentColor = StrideLabSurfaceTokens.ink,
            modifier = Modifier
                .fillMaxSize()
                .testTag("share-studio")
                .semantics { paneTitle = "Share Studio" }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
            ) {
                // Top Studio Header Bar (#000000 canvas + hairline)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(StrideLabSurfaceTokens.canvas)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        enabled = !busy,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("share-studio-back")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to activity",
                            tint = StrideLabSurfaceTokens.ink
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(end = 12.dp, top = 2.dp, bottom = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Share Studio",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = StrideLabSurfaceTokens.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            activePreset?.let { id ->
                                presets.firstOrNull { it.id == id }?.let { preset ->
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = StrideLabSurfaceTokens.surfaceSubtle,
                                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                                    ) {
                                        Text(
                                            preset.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StrideLabSurfaceTokens.inkSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "ACTIVITY DESIGN · ${config.template.label.uppercase(java.util.Locale.ROOT)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StrideLabSurfaceTokens.lime,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("share-active-template")
                        )
                        if (config.template == ShareTemplate.WORKOUT) {
                            // Visible outside the virtualized editor, even on narrow screens.
                            val workout = source?.workout
                            val detailText = if (workout == null) {
                                "Preparing workout recap…"
                            } else {
                                val label = workout.label ?: "Unclassified"
                                "$label / ${workout.provenance.label} / ${workout.totalLaps} recorded laps"
                            }
                            Text(
                                detailText,
                                style = MaterialTheme.typography.bodySmall,
                                color = StrideLabSurfaceTokens.inkSecondary,
                                modifier = Modifier
                                    .testTag("share-workout-provenance")
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }
                    }
                }
                StrideLabHairline()

                LazyColumn(
                    Modifier
                        .weight(1f)
                        .testTag("share-studio-scroll"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Item 0: Hero Canvas Stage
                    item {
                        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val ratio = effective?.ratio ?: config.ratio
                            val maxPreviewHeight = if (maxWidth < 340.dp) 300.dp else 390.dp
                            val stageWidth = minOf(maxWidth, maxPreviewHeight * (1080f / ratio.height))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF0A0B0D),
                                border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                            ) {
                                Box(
                                    Modifier
                                        .width(stageWidth)
                                        .aspectRatio(1080f / ratio.height)
                                        .drawBehind {
                                            if (config.background == ShareBackground.TRANSPARENT) {
                                                val tile = 12.dp.toPx().toInt().coerceAtLeast(1)
                                                for (y in 0 until size.height.toInt() step tile) {
                                                    for (x in ((y / tile) % 2) * tile until size.width.toInt() step tile * 2) {
                                                        drawRect(
                                                            StrideLabSurfaceTokens.surfaceSubtle,
                                                            Offset(x.toFloat(), y.toFloat()),
                                                            Size(
                                                                minOf(tile.toFloat(), size.width - x),
                                                                minOf(tile.toFloat(), size.height - y)
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (config.background == ShareBackground.PHOTO && selectedPhoto == null) {
                                        Text(
                                            "Choose a photo under Style to preview or export.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = StrideLabSurfaceTokens.inkMuted,
                                            modifier = Modifier
                                                .padding(20.dp)
                                                .testTag("share-photo-required")
                                        )
                                    } else if (card != null) {
                                        Image(
                                            card.asImageBitmap(),
                                            contentDescription = "Share card preview. " + source?.metrics
                                                ?.filterKeys { it in effective?.metrics.orEmpty() }
                                                ?.values
                                                ?.joinToString(". "),
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("share-card-preview")
                                        )
                                    } else {
                                        CircularProgressIndicator(color = StrideLabSurfaceTokens.lime)
                                    }
                                    if (showStoryGuides && ratio == ShareRatio.STORY) {
                                        Column(Modifier.fillMaxSize().testTag("share-story-safe-guides")) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight(.12f)
                                                    .background(Color(0x77000000)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "STORY TOP GUIDE",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            Spacer(Modifier.weight(1f))
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(64.dp)
                                                    .background(Color(0x77000000)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "STORY BOTTOM GUIDE",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Item 1: Tabular Status & Export Resolution Bar
                    item {
                        val status = when {
                            mapLoading -> "Loading Activity Dark map…"
                            effective?.template == ShareTemplate.WORKOUT && source?.workout?.provenance == ShareWorkoutSource.RECORDED_ONLY ->
                                "Workout recap · Recorded laps not classified as intervals · ${config.ratio.label} PNG"
                            effective?.template == ShareTemplate.WORKOUT ->
                                "Workout recap · Label provenance shown · ${config.ratio.label} PNG"
                            effective?.background == ShareBackground.TRANSPARENT ->
                                "Transparent background · ${config.ratio.label} PNG"
                            effective?.background == ShareBackground.PHOTO && selectedPhoto == null ->
                                "Choose a photo in Style before exporting · ${config.ratio.label} PNG"
                            effective?.background == ShareBackground.PHOTO ->
                                "Personal photo background · kept on this device for this edit · ${config.ratio.label} PNG"
                            effective?.routeMode == ShareRouteMode.HIDDEN ->
                                "Route hidden or too short to trim · Full activity stats · ${config.ratio.label} PNG"
                            map != null ->
                                "Activity Dark map · ${if (config.privacy == SharePrivacy.OFF) "Full GPS route" else "Route trimmed"} · ${config.ratio.label} PNG"
                            effective?.background == ShareBackground.MAP ->
                                "${if (online) "Activity Dark map unavailable" else if (hasOfflineMap) "Downloaded map snapshot unavailable" else "No downloaded map covers this full route"} · GPS trace only · ${config.ratio.label} PNG"
                            config.privacy == SharePrivacy.OFF ->
                                "Full GPS route · trimming off · no basemap · ${config.ratio.label} PNG"
                            else ->
                                "Route trimmed · Full activity stats · no basemap · ${config.ratio.label} PNG"
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                status,
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = StrideLabSurfaceTokens.inkSecondary,
                                modifier = Modifier
                                    .testTag("share-status")
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            Text(
                                "PNG export: 1080 x ${config.ratio.height} pixels",
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = StrideLabSurfaceTokens.inkMuted,
                                modifier = Modifier.testTag("share-export-resolution")
                            )
                        }
                    }

                    // Item 2: Compact Studio Tool Rail
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShareSection.entries.forEach { value ->
                                val selected = section == value
                                FilterChip(
                                    selected = selected,
                                    onClick = { section = value },
                                    label = {
                                        Text(
                                            value.label,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    },
                                    colors = studioChipColors(),
                                    border = studioChipBorder(selected),
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("share-section-${value.name}")
                                )
                            }
                        }
                    }

                    // Item 3: Sleek #121212 Dark Studio Tray
                    item {
                        Surface(
                            color = StrideLabSurfaceTokens.surfaceElevated,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    section.label.uppercase(java.util.Locale.ROOT),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StrideLabSurfaceTokens.lime,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                StrideLabHairline()
                                when (section) {
                                    ShareSection.TEMPLATES -> ShareTemplateGallery(config.template) {
                                        configure(config.selectTemplate(it))
                                        activePreset = null
                                    }

                                    ShareSection.VARIANT -> ShareChoices(
                                        config.template.variants,
                                        config.variant,
                                        { it.label }
                                    ) { configure(config.selectVariant(it)) }

                                    ShareSection.RATIO -> ShareChoices(
                                        ShareRatio.entries,
                                        config.ratio,
                                        { it.label }
                                    ) { configure(config.copy(ratio = it)) }

                                    ShareSection.ROUTE -> {
                                        Text(
                                            "Choose whether to remove the start and finish of the GPS route. All stats still cover the full activity.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = StrideLabSurfaceTokens.inkSecondary
                                        )
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    "Trim route endpoints",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = StrideLabSurfaceTokens.ink
                                                )
                                                Text(
                                                    "On by default",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = StrideLabSurfaceTokens.inkMuted
                                                )
                                            }
                                            Switch(
                                                checked = config.privacy != SharePrivacy.OFF,
                                                onCheckedChange = { enabled ->
                                                    val next = if (enabled) SharePrivacy.valueOf(lastTrimPrivacy) else SharePrivacy.OFF
                                                    configure(config.copy(privacy = next))
                                                },
                                                modifier = Modifier.testTag("share-trim-switch")
                                            )
                                        }
                                        StrideLabHairline()
                                        if (config.privacy == SharePrivacy.OFF) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0x26FFBE7A),
                                                border = BorderStroke(1.dp, Color(0x66FFBE7A)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    "Privacy warning: trimming is OFF. The full GPS route, including your exact start and finish, will appear in saved and shared PNG images.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color(0xFFFFBE7A),
                                                    modifier = Modifier
                                                        .padding(12.dp)
                                                        .testTag("share-trim-off-warning")
                                                )
                                            }
                                        } else {
                                            Text(
                                                "Trim distance from each endpoint",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = StrideLabSurfaceTokens.ink
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                SharePrivacy.entries.filterNot { it == SharePrivacy.OFF }.forEach { level ->
                                                    val selected = config.privacy == level
                                                    FilterChip(
                                                        selected = selected,
                                                        onClick = { configure(config.copy(privacy = level)) },
                                                        label = { Text(level.label) },
                                                        colors = studioChipColors(),
                                                        border = studioChipBorder(selected),
                                                        modifier = Modifier
                                                            .heightIn(min = 48.dp)
                                                            .testTag("share-trim-${level.meters.toInt()}")
                                                    )
                                                }
                                            }
                                            Text(
                                                "Trimming reduces endpoint exposure but does not guarantee location anonymity.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = StrideLabSurfaceTokens.inkMuted
                                            )
                                        }
                                        StrideLabHairline()
                                        ShareChoices(
                                            source?.availableModes?.toList().orEmpty(),
                                            effective?.routeMode,
                                            { it.label }
                                        ) { configure(config.copy(routeMode = it)) }
                                        if (config.routeMode != effective?.routeMode) {
                                            Text(
                                                "This activity cannot provide ${config.routeMode.label}; using ${effective?.routeMode?.label ?: "Hidden"}.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = StrideLabSurfaceTokens.inkMuted
                                            )
                                        }
                                    }

                                    ShareSection.METRICS -> {
                                        Text(
                                            "Keep at least one available stat. Missing fields are omitted.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = StrideLabSurfaceTokens.inkSecondary
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (source != null) {
                                                source.metrics.keys.forEach { metric ->
                                                    val selected = metric in effective?.metrics.orEmpty()
                                                    val next = effective?.metrics.orEmpty().let { if (selected) it - metric else it + metric }
                                                    val allowed = effective != null && effective.copy(metrics = next).forActivity(source).metrics == next
                                                    FilterChip(
                                                        selected = selected,
                                                        enabled = allowed || !selected,
                                                        onClick = {
                                                            configure(
                                                                config.copy(
                                                                    metrics = if (selected) config.metrics - metric else config.metrics + metric
                                                                )
                                                            )
                                                        },
                                                        label = { Text(metric.label) },
                                                        colors = studioChipColors(),
                                                        border = studioChipBorder(selected),
                                                        modifier = Modifier.heightIn(min = 48.dp)
                                                    )
                                                }
                                            }
                                        }
                                        val ordered = if (source != null && effective != null) {
                                            effective.visibleNumericMetrics(source.metrics.keys)
                                        } else emptyList()
                                        if (ordered.size > 1) {
                                            StrideLabHairline()
                                            Text(
                                                "Reorder displayed stats",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = StrideLabSurfaceTokens.ink
                                            )
                                            Text(
                                                "Your first stat is featured in Stats Focus; the order is saved with your preset.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = StrideLabSurfaceTokens.inkMuted
                                            )
                                            Column(Modifier.fillMaxWidth()) {
                                                ordered.forEachIndexed { index, metric ->
                                                    Row(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 48.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(
                                                            metric.label,
                                                            style = MaterialTheme.typography.bodyMedium.tabular(),
                                                            color = StrideLabSurfaceTokens.ink,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        TextButton(
                                                            onClick = { configure(config.moveMetric(metric, -1)) },
                                                            enabled = index > 0 && !busy,
                                                            modifier = Modifier
                                                                .heightIn(min = 48.dp)
                                                                .testTag("share-metric-up-" + metric.name)
                                                        ) {
                                                            Text("Up")
                                                        }
                                                        TextButton(
                                                            onClick = { configure(config.moveMetric(metric, 1)) },
                                                            enabled = index < ordered.lastIndex && !busy,
                                                            modifier = Modifier
                                                                .heightIn(min = 48.dp)
                                                                .testTag("share-metric-down-" + metric.name)
                                                        ) {
                                                            Text("Down")
                                                        }
                                                    }
                                                    if (index < ordered.lastIndex) StrideLabHairline()
                                                }
                                            }
                                        }
                                    }

                                    ShareSection.STYLE -> {
                                        ShareChoices(
                                            ShareBackground.entries,
                                            config.background,
                                            { it.label }
                                        ) { configure(config.copy(background = it)) }
                                        if (config.background == ShareBackground.PHOTO) {
                                            Text(
                                                "The original photo stays in memory for this editor session. Presets do not store its image.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = StrideLabSurfaceTokens.inkMuted
                                            )
                                            OutlinedButton(
                                                onClick = { photoLauncher.launch("image/*") },
                                                enabled = !busy,
                                                border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .testTag("share-choose-photo")
                                            ) {
                                                Text(if (selectedPhoto == null) "Choose photo" else "Change photo")
                                            }
                                        }
                                        StrideLabHairline()
                                        Text(
                                            "Composition",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = StrideLabSurfaceTokens.ink
                                        )
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    "Instagram Story safe-area guides",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = StrideLabSurfaceTokens.ink
                                                )
                                                Text(
                                                    "Preview only. Approximate areas; the exported PNG is unchanged.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = StrideLabSurfaceTokens.inkMuted
                                                )
                                            }
                                            Switch(
                                                checked = showStoryGuides,
                                                onCheckedChange = { showStoryGuides = it },
                                                enabled = config.ratio == ShareRatio.STORY,
                                                modifier = Modifier.testTag("share-story-guide-switch")
                                            )
                                        }
                                        ShareChoices(
                                            ShareLayout.entries,
                                            config.layout,
                                            { it.label }
                                        ) { configure(config.copy(layout = it)) }
                                        if (config.template in setOf(ShareTemplate.ROUTE, ShareTemplate.MINIMAL)) {
                                            StrideLabHairline()
                                            Text(
                                                "Route and statistics placement",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = StrideLabSurfaceTokens.ink
                                            )
                                            Slider(
                                                value = config.routeBalance.toFloat(),
                                                onValueChange = { configure(config.copy(routeBalance = it.toInt().coerceIn(0, 100))) },
                                                valueRange = 0f..100f,
                                                steps = 9,
                                                enabled = effective?.routeMode != ShareRouteMode.HIDDEN && !busy,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("share-route-stats-balance"),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = StrideLabSurfaceTokens.lime,
                                                    activeTrackColor = StrideLabSurfaceTokens.lime
                                                )
                                            )
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    "Stats higher",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StrideLabSurfaceTokens.inkMuted
                                                )
                                                Text(
                                                    "Larger route",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StrideLabSurfaceTokens.inkMuted
                                                )
                                            }
                                            Text(
                                                "Layout remains inside safe margins to protect stats and map attribution. " +
                                                    "The exported PNG matches the preview.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = StrideLabSurfaceTokens.inkMuted
                                            )
                                        }
                                    }

                                    ShareSection.BRANDING -> ShareChoices(
                                        ShareBranding.entries,
                                        config.branding,
                                        { it.label }
                                    ) { configure(config.copy(branding = it)) }

                                    ShareSection.PRESETS -> {
                                        if (presetLoadFailed) {
                                            Text(
                                                "Saved presets are unavailable. Stored designs have not been replaced.",
                                                color = StrideLabSurfaceTokens.inkSecondary
                                            )
                                        } else if (!presetsReady) {
                                            CircularProgressIndicator(color = StrideLabSurfaceTokens.lime)
                                        } else if (presets.isEmpty()) {
                                            Text(
                                                "Save your design to reuse it on another activity.",
                                                color = StrideLabSurfaceTokens.inkMuted
                                            )
                                        } else {
                                            ShareChoices(
                                                presets,
                                                presets.firstOrNull { it.id == activePreset },
                                                { it.name }
                                            ) { preset ->
                                                configure(preset.state)
                                                activePreset = preset.id
                                            }
                                            presets.firstOrNull { it.id == activePreset }?.let { selected ->
                                                TextButton(
                                                    onClick = { message = null; deleting = selected },
                                                    enabled = !busy,
                                                    modifier = Modifier.heightIn(min = 48.dp)
                                                ) {
                                                    Text("Delete selected preset")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Item 4: Save Preset CTA
                    item {
                        OutlinedButton(
                            onClick = {
                                message = null
                                name = presets.firstOrNull { it.id == activePreset }?.name.orEmpty()
                                naming = true
                            },
                            enabled = presetsReady && !busy,
                            border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = StrideLabSurfaceTokens.surfaceElevated,
                                contentColor = StrideLabSurfaceTokens.ink
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("share-save-preset")
                        ) {
                            Text("Save Preset", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Anchored #121212 Export Footer separated by hairline
                StrideLabHairline()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(StrideLabSurfaceTokens.surfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message?.let { text ->
                        Text(
                            text,
                            color = StrideLabSurfaceTokens.lime,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("share-message")
                        )
                    }
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = exportReady,
                            onClick = {
                                val image = card ?: return@OutlinedButton
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    exportPngDocument(image)
                                } else {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { ShareImageExport.saveToGallery(context, image) }
                                            message = "Saved to Pictures / StrideLab"
                                        } catch (error: Exception) {
                                            if (error is CancellationException) throw error
                                            message = "Could not save image. Check storage and try again."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                            border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("share-save-image")
                        ) {
                            Text("Save Image")
                        }
                        OutlinedButton(
                            enabled = exportReady,
                            onClick = {
                                val image = card ?: return@OutlinedButton
                                exportPngDocument(image)
                            },
                            border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("share-export-png")
                        ) {
                            Text("Export PNG")
                        }
                        Button(
                            enabled = exportReady,
                            onClick = {
                                val image = card ?: return@Button
                                busy = true
                                message = null
                                scope.launch {
                                    try {
                                        val intent = withContext(Dispatchers.IO) { ShareImageExport.shareIntent(context, image) }
                                        context.startActivity(Intent.createChooser(intent, "Share activity"))
                                    } catch (error: Exception) {
                                        if (error is CancellationException) throw error
                                        message = "Could not share image. Try Save Image instead."
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StrideLabSurfaceTokens.lime,
                                contentColor = StrideLabSurfaceTokens.onLime
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("share-send")
                        ) {
                            Text(if (busy) "Working…" else "Share", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (naming) {
            AlertDialog(
                onDismissRequest = { if (!busy) naming = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = StrideLabSurfaceTokens.surfaceElevated,
                titleContentColor = StrideLabSurfaceTokens.ink,
                textContentColor = StrideLabSurfaceTokens.ink,
                title = { Text("Save Preset", style = MaterialTheme.typography.titleLarge) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 60) name = it },
                        label = { Text("Preset name") },
                        supportingText = { Text(message ?: "${name.length}/60") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StrideLabSurfaceTokens.ink,
                            unfocusedTextColor = StrideLabSurfaceTokens.ink,
                            focusedBorderColor = StrideLabSurfaceTokens.lime,
                            unfocusedBorderColor = StrideLabSurfaceTokens.hairline,
                            focusedLabelColor = StrideLabSurfaceTokens.lime,
                            unfocusedLabelColor = StrideLabSurfaceTokens.inkMuted,
                            cursorColor = StrideLabSurfaceTokens.lime
                        ),
                        modifier = Modifier.testTag("share-preset-name")
                    )
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = name.isNotBlank() && !busy && presets.size < 100,
                            onClick = {
                                val preset = SharePreset(UUID.randomUUID().toString(), name.trim(), config)
                                savePresets(presets + preset) { activePreset = preset.id; naming = false }
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StrideLabSurfaceTokens.lime,
                                contentColor = StrideLabSurfaceTokens.onLime
                            )
                        ) {
                            Text("Save new", style = MaterialTheme.typography.labelLarge)
                        }
                        if (activePreset != null) {
                            TextButton(
                                enabled = name.isNotBlank() && !busy,
                                onClick = {
                                    val selected = activePreset
                                    savePresets(
                                        presets.map { if (it.id == selected) it.copy(name = name.trim(), state = config) else it }
                                    ) { naming = false }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = StrideLabSurfaceTokens.lime)
                            ) {
                                Text("Update selected")
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = { naming = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = StrideLabSurfaceTokens.inkMuted)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        deleting?.let { selected ->
            AlertDialog(
                onDismissRequest = { if (!busy) deleting = null },
                containerColor = StrideLabSurfaceTokens.surfaceElevated,
                titleContentColor = StrideLabSurfaceTokens.ink,
                textContentColor = StrideLabSurfaceTokens.ink,
                title = { Text("Delete preset?") },
                text = { Column { Text(selected.name); message?.let { Text(it) } } },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            savePresets(presets.filterNot { it.id == selected.id }) {
                                deleting = null
                                activePreset = null
                                message = "Preset deleted"
                            }
                        }
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareTemplateGallery(selected: ShareTemplate, onSelect: (ShareTemplate) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val columns = if (maxWidth >= 290.dp) 2 else 1
        val tileWidth = (maxWidth - 10.dp * (columns - 1)) / columns
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = columns
        ) {
            ShareTemplate.entries.forEach { template ->
                val chosen = template == selected
                val explanation = when (template) {
                    ShareTemplate.ROUTE -> "Map-led"
                    ShareTemplate.STATS -> "Metrics-led"
                    ShareTemplate.MINIMAL -> "Clean typography"
                    ShareTemplate.WORKOUT -> "Recorded lap recap"
                }
                // A single selectable modifier owns both click handling and selected semantics.
                // Avoid layering the clickable Surface semantics with a separate selected node.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (chosen) StrideLabSurfaceTokens.surfaceSubtle else Color(0xFF0A0B0D),
                    border = BorderStroke(
                        1.dp,
                        if (chosen) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.hairline
                    ),
                    modifier = Modifier
                        .width(tileWidth)
                        .heightIn(min = 96.dp)
                        .testTag("share-template-${template.name}")
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(template) })
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShareTemplateWireframe(template = template, chosen = chosen)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                template.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (chosen) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.ink,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = StrideLabSurfaceTokens.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTemplateWireframe(template: ShareTemplate, chosen: Boolean) {
    val accent = if (chosen) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.inkSecondary
    val muted = StrideLabSurfaceTokens.hairline
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF060708))
    ) {
        val w = size.width
        val h = size.height
        when (template) {
            ShareTemplate.ROUTE -> {
                val p = Path().apply {
                    moveTo(w * 0.14f, h * 0.56f)
                    cubicTo(w * 0.30f, h * 0.14f, w * 0.52f, h * 0.72f, w * 0.84f, h * 0.26f)
                }
                drawPath(p, color = accent, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(muted, Offset(w * 0.12f, h * 0.82f), Offset(w * 0.42f, h * 0.82f), strokeWidth = 3f, cap = StrokeCap.Round)
                drawLine(muted, Offset(w * 0.56f, h * 0.82f), Offset(w * 0.84f, h * 0.82f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
            ShareTemplate.STATS -> {
                drawLine(accent, Offset(w * 0.12f, h * 0.32f), Offset(w * 0.56f, h * 0.32f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(muted, Offset(w * 0.12f, h * 0.62f), Offset(w * 0.38f, h * 0.62f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                drawLine(muted, Offset(w * 0.54f, h * 0.62f), Offset(w * 0.82f, h * 0.62f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                drawLine(muted, Offset(w * 0.12f, h * 0.82f), Offset(w * 0.78f, h * 0.82f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            }
            ShareTemplate.MINIMAL -> {
                drawLine(accent, Offset(w * 0.20f, h * 0.34f), Offset(w * 0.76f, h * 0.24f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(muted, Offset(w * 0.20f, h * 0.64f), Offset(w * 0.46f, h * 0.64f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                drawLine(muted, Offset(w * 0.54f, h * 0.64f), Offset(w * 0.78f, h * 0.64f), strokeWidth = 2.5f, cap = StrokeCap.Round)
            }
            ShareTemplate.WORKOUT -> {
                drawRoundRect(
                    color = muted,
                    topLeft = Offset(w * 0.12f, h * 0.14f),
                    size = Size(w * 0.76f, h * 0.72f),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 1.4f)
                )
                drawLine(accent, Offset(w * 0.20f, h * 0.34f), Offset(w * 0.36f, h * 0.34f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                drawLine(muted, Offset(w * 0.44f, h * 0.34f), Offset(w * 0.78f, h * 0.34f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.20f, h * 0.56f), Offset(w * 0.36f, h * 0.56f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                drawLine(muted, Offset(w * 0.44f, h * 0.56f), Offset(w * 0.78f, h * 0.56f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ShareChoices(values: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        values.forEach { value ->
            val isSelected = value == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
                colors = studioChipColors(),
                border = studioChipBorder(isSelected),
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}
