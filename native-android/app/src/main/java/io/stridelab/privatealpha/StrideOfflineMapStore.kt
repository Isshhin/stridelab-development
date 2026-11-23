package io.stridelab.privatealpha

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.IOException
import java.util.Locale
import kotlin.math.*

internal data class StrideSavedMap(
    val id: Long, val name: String, val bounds: LatLngBounds,
    val completed: Boolean, val progress: Int?, val downloadedBytes: Long
)

internal data class OfflineMapProgress(val resources: Long, val bytes: Long) {
    fun percent(required: Long, precise: Boolean, complete: Boolean): Int? = when {
        complete -> 100
        !precise || required <= 0 -> null
        else -> (resources * 100.0 / required).toInt().coerceIn(0, 99)
    }

    fun advancedFrom(previous: OfflineMapProgress) =
        resources > previous.resources || bytes > previous.bytes
}

internal fun offlineMapStalled(nowMs: Long, lastProgressMs: Long) =
    nowMs - lastProgressMs >= 60_000L

internal fun offlineMapError(reason: String, message: String?): String = when {
    message?.contains("429") == true ->
        "The map server is limiting requests. Wait a moment before retrying."
    reason == OfflineRegionError.REASON_CONNECTION -> "Couldn’t reach the map server. Check Wi-Fi and retry."
    reason == OfflineRegionError.REASON_NOT_FOUND -> "A required map resource is missing from the server."
    reason == OfflineRegionError.REASON_SERVER -> "The map server could not finish this download. Try again later."
    else -> "Map download interrupted. Retry to continue this saved area."
}

/** Testable, strict coverage check over the already privacy-trimmed route. */
internal object StrideOfflineCoverage {
    fun covers(maps: List<StrideSavedMap>, route: RecordedGpsRoute): Boolean {
        val points = route.allPoints
        if(points.size < 2) return false
        return maps.any { area ->
            area.completed && points.all { point ->
                val b = area.bounds
                point.latitude.isFinite() && point.longitude.isFinite() &&
                    point.latitude in b.latitudeSouth..b.latitudeNorth &&
                    point.longitude in b.longitudeWest..b.longitudeEast
            }
        }
    }
}

/** Shared, on-device MapLibre areas. No activity ID or personal profile is uploaded. */
internal class StrideOfflineMapStore private constructor(private val app: Context) {
    companion object {
        @Volatile private var instance: StrideOfflineMapStore? = null
        fun get(context: Context): StrideOfflineMapStore =
            instance ?: synchronized(this) {
                instance ?: StrideOfflineMapStore(context.applicationContext).also {
                    instance = it
                }
            }

        const val MIN_ZOOM = 11.0
        const val MAX_ZOOM = 14.0
        private const val MAX_ESTIMATED_TILES = 1000L
        private const val STOP_DOWNLOADED_TILES = 2300L
        private const val STOP_DOWNLOADED_BYTES = 40L * 1024L * 1024L

        /** Reject large requests before contacting the provider. */
        fun estimatedTiles(bounds: LatLngBounds): Long {
            val north = bounds.latitudeNorth
            val south = bounds.latitudeSouth
            val west = bounds.longitudeWest
            val east = bounds.longitudeEast
            if (!north.isFinite() || !south.isFinite() || !west.isFinite() ||
                !east.isFinite() || south < -75 || north > 75 ||
                north <= south || east <= west || west < -180 || east > 180) return Long.MAX_VALUE
            var tiles = 0L
            for (z in 11..14) {
                val n = 2.0.pow(z)
                fun tileY(lat: Double) = (1 - asinh(tan(Math.toRadians(lat))) / Math.PI) * n / 2
                val left = floor((west + 180.0) / 360 * n).toLong()
                val right = floor((east + 180.0) / 360 * n).toLong()
                val top = floor(tileY(north)).toLong()
                val bottom = floor(tileY(south)).toLong()
                tiles += (right - left + 1).coerceAtLeast(0) *
                    (bottom - top + 1).coerceAtLeast(0)
                if (tiles > MAX_ESTIMATED_TILES) return tiles
            }
            return tiles
        }

        fun isSelectionAllowed(bounds: LatLngBounds): Boolean =
            estimatedTiles(bounds) in 1..MAX_ESTIMATED_TILES
    }

    var maps by mutableStateOf<List<StrideSavedMap>>(emptyList())
        private set
    var activeId by mutableStateOf<Long?>(null)
        private set
    var failures by mutableStateOf<Map<Long, String>>(emptyMap())
        private set
    var preparing by mutableStateOf(false)
        private set
    var notice by mutableStateOf("")
        private set

    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var active: OfflineRegion? = null
    private var poll: Runnable? = null
    private var connectivityListener: ConnectivityManager.NetworkCallback? = null

    private val offlineManager by lazy {
        MapLibre.getInstance(app)
        val dispatcher = Dispatcher().apply {
            maxRequests = 4
            maxRequestsPerHost = 4
        }
        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .callTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                if (original.url.queryParameter("offline") == "true" && !onWifi())
                    throw IOException("Wi-Fi is required for offline maps")
                val modified = if (original.url.host == "tiles.openfreemap.org") {
                    original.newBuilder()
                        .header("User-Agent", "StrideLab-Android/0.7 (offline maps)")
                        .build()
                } else original
                chain.proceed(modified)
            }.build()
        HttpRequestUtil.setOkHttpClient(client)
        OfflineManager.getInstance(app)
    }

    fun initialize() { offlineManager }

    /**
     * Conservative basemap reuse: the entire visible (already privacy-trimmed)
     * route must fit inside one completely downloaded map area. Never infer
     * coverage from a partial map or silently mix nearby incomplete regions.
     */
    internal fun hasCompleteCoverage(route: RecordedGpsRoute): Boolean =
        StrideOfflineCoverage.covers(maps, route)

    fun onWifi(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun seedBundledStyle() {
        val url = TracksideMapTiles.STYLE_URL
        val bytes = app.assets.open(url.removePrefix("asset://")).use { it.readBytes() }
        offlineManager.putResourceWithUrl(url, bytes, 0L, 0L, null, false)
    }

    private fun ownMetadata(region: OfflineRegion): JSONObject? = runCatching {
        JSONObject(String(region.metadata, Charsets.UTF_8))
            .takeIf { it.optInt("stride_offline_map") == 1 }
    }.getOrNull()

    private fun snapshot(region: OfflineRegion, status: OfflineRegionStatus): StrideSavedMap? {
        val info = ownMetadata(region) ?: return null
        val bounds = region.definition.bounds ?: return null
        val progress = OfflineMapProgress(status.completedResourceCount, status.completedResourceSize)
        return StrideSavedMap(region.id, info.optString("name").ifBlank { "Offline area" }, bounds,
            status.isComplete, progress.percent(status.requiredResourceCount,
                status.isRequiredResourceCountPrecise, status.isComplete), progress.bytes)
    }

    private fun publish(area: StrideSavedMap) {
        maps = (maps.filterNot { it.id == area.id } + area).sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun refresh() {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val ours = regions.orEmpty().filter { ownMetadata(it) != null }
                val ids = ours.mapTo(mutableSetOf()) { it.id }
                maps = maps.filter { it.id in ids || it.id == activeId }
                for (region in ours) {
                    if (region.id == activeId && maps.any { it.id == activeId }) continue
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status != null && (region.id != activeId || maps.none { it.id == activeId }))
                                snapshot(region, status)?.let(::publish)
                        }
                        override fun onError(error: String?) {
                            notice = "Could not inspect one saved map."
                        }
                    })
                }
            }
            override fun onError(error: String) {
                notice = "Unable to read downloaded maps."
            }
        })
    }

    private fun stopWatching() {
        poll?.let(main::removeCallbacks)
        poll = null
        connectivityListener?.let {
            runCatching { connectivity.unregisterNetworkCallback(it) }
        }
        connectivityListener = null
    }

    fun pause(message: String = "Download paused.") {
        active?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        active?.setObserver(null)
        active = null
        activeId = null
        stopWatching()
        notice = message
        refresh()
    }

    private fun reachedLimit(status: OfflineRegionStatus) =
        status.completedTileCount >= STOP_DOWNLOADED_TILES ||
            status.completedResourceSize >= STOP_DOWNLOADED_BYTES

    private fun fail(region: OfflineRegion, message: String) {
        if (active !== region) return
        failures = failures + (region.id to message)
        pause("")
    }

    private fun start(region: OfflineRegion) {
        if (active != null) return
        if (!onWifi()) {
            notice = ""
            failures = failures + (region.id to "Connect to Wi-Fi before downloading.")
            refresh()
            return
        }
        active = region
        activeId = region.id
        failures = failures - region.id
        notice = "Downloading selected map area over Wi-Fi…"
        var progress = OfflineMapProgress(0, 0)
        var lastProgress = SystemClock.elapsedRealtime()
        var missingResource: String? = null

        fun consume(status: OfflineRegionStatus) {
            if (active !== region) return
            snapshot(region, status)?.let(::publish)
            if (status.isComplete) {
                pause("Map saved. Runs within the selected box can reuse it.")
                return
            }
            if (reachedLimit(status)) {
                fail(region, "This area reached the download limit. Choose a smaller box.")
                return
            }
            val next = OfflineMapProgress(status.completedResourceCount, status.completedResourceSize)
            if (next.advancedFrom(progress)) {
                progress = next
                lastProgress = SystemClock.elapsedRealtime()
            }
        }

        try {
            val watcher = object : ConnectivityManager.NetworkCallback() {
                private fun checkWifi() {
                    main.post {
                        if (active === region && !onWifi())
                            fail(region, "Wi-Fi disconnected. Reconnect and retry.")
                    }
                }
                override fun onLost(network: Network) = checkWifi()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = checkWifi()
            }
            connectivity.registerDefaultNetworkCallback(watcher)
            connectivityListener = watcher
            region.setDeliverInactiveMessages(true)
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) = consume(status)
                override fun onError(error: OfflineRegionError) {
                    if (active !== region) return
                    val message = offlineMapError(error.reason, error.message)
                    // MapLibre skips 404s for optional tiles and glyph ranges. A missing
                    // required resource still surfaces if the download stops advancing.
                    if (error.reason == OfflineRegionError.REASON_NOT_FOUND) missingResource = message
                    else fail(region, message)
                }
                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    fail(region, "Map tile limit reached. Select a smaller box.")
                }
            })
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            val monitor = object : Runnable {
                override fun run() {
                    if (active !== region) return
                    if (!onWifi()) {
                        fail(region, "Wi-Fi disconnected. Reconnect and retry.")
                        return
                    }
                    if (offlineMapStalled(SystemClock.elapsedRealtime(), lastProgress)) {
                        fail(region, missingResource ?: "The map server stopped sending data. Retry to continue this area.")
                        return
                    }
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) { status?.let(::consume) }
                        override fun onError(error: String?) {
                            fail(region, "Could not check download progress. Retry to continue this area.")
                        }
                    })
                    main.postDelayed(this, 3_000L)
                }
            }
            poll = monitor
            main.postDelayed(monitor, 3_000L)
        } catch (_: RuntimeException) {
            fail(region, "Could not start the map download. Retry to continue this area.")
        }
    }

    fun save(name: String, bounds: LatLngBounds?) {
        if (name.isBlank() || name.length > 60) {
            notice = "Give the selected map a name (up to 60 characters)."
            return
        }
        if (bounds == null || !isSelectionAllowed(bounds)) {
            notice = "Zoom in or make the map selection smaller."
            return
        }
        if (!onWifi()) {
            notice = "Connect to Wi-Fi before downloading."
            return
        }
        if (activeId != null || preparing) {
            notice = "Finish or pause the current download first."
            return
        }
        val duplicate = maps.any {
            abs(it.bounds.latitudeNorth - bounds.latitudeNorth) < 0.00001 &&
                abs(it.bounds.latitudeSouth - bounds.latitudeSouth) < 0.00001 &&
                abs(it.bounds.longitudeEast - bounds.longitudeEast) < 0.00001 &&
                abs(it.bounds.longitudeWest - bounds.longitudeWest) < 0.00001
        }
        if (duplicate) {
            notice = "This selection is already saved. Use the existing area below."
            return
        }
        val metadata = JSONObject()
            .put("stride_offline_map", 1)
            .put("name", name.trim())
            .toString().toByteArray(Charsets.UTF_8)
        if (!prepareStyle()) return
        preparing = true
        notice = "Preparing selected area…"
        val definition = OfflineTilePyramidRegionDefinition(
            TracksideMapTiles.STYLE_URL, bounds, MIN_ZOOM, MAX_ZOOM, app.resources.displayMetrics.density, false
        )
        offlineManager.createOfflineRegion(definition, metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    preparing = false
                    start(region)
                    refresh()
                }
                override fun onError(error: String) {
                    preparing = false
                    notice = "Could not prepare the offline map."
                }
            })
    }

    private fun prepareStyle(): Boolean = try {
        // OfflineDownload consults its database and HTTP source, so seed the APK style before activation.
        seedBundledStyle()
        true
    } catch (_: Exception) {
        notice = "Could not load the bundled map style."
        false
    }

    fun resume(id: Long) {
        if (activeId != null || preparing) {
            notice = "Pause the current map download first."
            return
        }
        if (!prepareStyle()) return
        preparing = true
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val region = regions.orEmpty().firstOrNull {
                    it.id == id && ownMetadata(it) != null
                }
                if (region == null) {
                    preparing = false
                    notice = "Saved map not found."
                    return
                }
                region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                    override fun onStatus(status: OfflineRegionStatus?) {
                        preparing = false
                        if (status == null) {
                            failures = failures + (id to "Could not read the saved area. Try again.")
                        } else if (status.isComplete) {
                            failures = failures - id
                            snapshot(region, status)?.let(::publish)
                        } else if (reachedLimit(status)) {
                            failures = failures + (id to "This area reached the download limit. Choose a smaller box.")
                        } else start(region)
                    }

                    override fun onError(error: String?) {
                        preparing = false
                        failures = failures + (id to "Could not read the saved area. Try again.")
                    }
                })
            }
            override fun onError(error: String) {
                preparing = false
                failures = failures + (id to "Could not reopen this saved area. Try again.")
            }
        })
    }

    fun delete(id: Long) {
        if (activeId == id) pause()
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val region = regions.orEmpty().firstOrNull {
                    it.id == id && ownMetadata(it) != null
                } ?: return
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        maps = maps.filterNot { it.id == id }
                        failures = failures - id
                        notice = "Offline map deleted; recorded activities were not changed."
                    }
                    override fun onError(error: String) {
                        notice = "Could not delete offline map."
                    }
                })
            }
            override fun onError(error: String) {
                notice = "Could not locate offline map."
            }
        })
    }
}
