package io.stridelab.privatealpha

import android.os.Trace
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal inline fun <T> traceStartup(section: String, read: () -> T): T {
    Trace.beginSection(section)
    try {
        return read()
    } finally {
        Trace.endSection()
    }
}

internal fun mergeActivitySummaries(cloud: List<ActivityRecord>, local: List<ActivityRecord>): List<ActivityRecord> {
    val localIds = local.mapTo(HashSet()) { it.id }
    return (cloud.filterNot { it.id in localIds } + local)
        .sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenByDescending { it.id })
}

private val LocalThumbnailLoader = staticCompositionLocalOf<(suspend (String) -> RecordedGpsRoute?)?> { null }
private val LocalThumbnailAccount = staticCompositionLocalOf<Pair<String?, Long>?> { null }
internal val LocalThumbnailCache = staticCompositionLocalOf<ActivityThumbnailCache?> { null }

@Composable
internal fun ActivityThumbnailProvider(
    loader: (suspend (String) -> RecordedGpsRoute?)?,
    account: String?,
    loginGeneration: Long,
    cache: ActivityThumbnailCache? = null,
    content: @Composable () -> Unit
) = CompositionLocalProvider(
    LocalThumbnailLoader provides loader,
    LocalThumbnailAccount provides (account to loginGeneration),
    LocalThumbnailCache provides cache,
    content = content
)

@Composable
internal fun rememberActivityThumbnail(id: String?, detail: ActivityDetail?): RecordedGpsRoute? {
    if (id == null && detail == null) return null
    val loader = LocalThumbnailLoader.current
    return key(id, LocalThumbnailAccount.current) {
        val route by produceState<RecordedGpsRoute?>(null, detail) {
            value = if (detail != null) withContext(Dispatchers.Default) { detail.thumbnailRoute() }
            else id?.let { loader?.invoke(it) }
        }
        route
    }
}

/**
 * Renders a pre-rendered GPS thumbnail bitmap backed synchronously by an in-memory LruCache
 * and disk PNG cache so tab switches never blank or re-parse GPS coordinates.
 */
@Composable
internal fun TodayCachedGpsThumbnail(
    activityId: String?,
    detail: ActivityDetail?,
    modifier: Modifier = Modifier
) {
    if (activityId == null && detail == null) return
    val context = LocalContext.current
    val cache = LocalThumbnailCache.current ?: remember(context.applicationContext) {
        SharedThumbnailCacheHolder.get(context.applicationContext)
    }
    val loader = LocalThumbnailLoader.current
    val owner = LocalThumbnailAccount.current?.first
    val stableId = activityId ?: detail?.activityId ?: return
    val keyString = remember(owner, stableId) { cache.cacheKey(owner, stableId) }

    // Synchronous in-memory read avoids blank frames across tab switches or recomposition.
    var bitmap by remember(keyString) {
        mutableStateOf<ImageBitmap?>(cache.getMemory(keyString))
    }

    LaunchedEffect(keyString, detail) {
        if (bitmap == null) {
            bitmap = cache.getOrRender(keyString) {
                detail?.let { withContext(Dispatchers.Default) { it.thumbnailRoute() } }
                    ?: activityId?.let { loader?.invoke(it) }
            }
        }
    }

    bitmap?.let { img ->
        Image(
            bitmap = img,
            contentDescription = "Recorded GPS route preview, north up",
            contentScale = ContentScale.Fit,
            modifier = modifier.testTag("gps-thumb-$stableId")
        )
    }
}

internal fun ActivityDetail.thumbnailRoute(): RecordedGpsRoute? {
    val segments = RecordedGpsRoute.from(samples, pauseIntervals).displaySegments
        .filter { it.size >= 2 }
    if (segments.isEmpty()) return null
    // Retain the longest lines when the route crosses the antimeridian repeatedly.
    val retained = segments.withIndex().sortedByDescending { it.value.size }
        .take(24).sortedBy { it.index }
    val perSegment = 240 / retained.size
    return RecordedGpsRoute(retained.map { (_, points) ->
        if (points.size <= perSegment) points
        else List(perSegment) { index ->
            points[(index * points.lastIndex.toDouble() / (perSegment - 1)).roundToInt()]
        }
    })
}
