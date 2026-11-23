package io.stridelab.privatealpha

import android.os.Trace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
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

@Composable
internal fun ActivityThumbnailProvider(
    loader: (suspend (String) -> RecordedGpsRoute?)?,
    account: String?,
    loginGeneration: Long,
    content: @Composable () -> Unit
) = CompositionLocalProvider(
    LocalThumbnailLoader provides loader,
    LocalThumbnailAccount provides (account to loginGeneration),
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
