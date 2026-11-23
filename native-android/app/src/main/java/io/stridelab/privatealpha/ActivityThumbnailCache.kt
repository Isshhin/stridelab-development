package io.stridelab.privatealpha

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.cos

/**
 * Two-tier GPS route thumbnail cache:
 * 1. Synchronous in-memory LruCache<String, ImageBitmap> so tab switches never blank or reload.
 * 2. Local disk PNG cache in cacheDir/gps_thumbs so cold starts skip GPS point parsing.
 */
internal class ActivityThumbnailCache(cacheRootDir: File) {
    private val diskDir = File(cacheRootDir, "gps_thumbs")
    private val memoryCache: LruCache<String, ImageBitmap> =
        object : LruCache<String, ImageBitmap>(6 * 1024) { // 6 MB budget (~70-120 thumbnails)
            override fun sizeOf(key: String, value: ImageBitmap): Int {
                // ARGB_8888 ~ 4 bytes/pixel, measured in KB
                val kb = (value.width * value.height * 4) / 1024
                return kb.coerceAtLeast(1)
            }
        }

    fun cacheKey(owner: String?, activityId: String): String =
        "${owner ?: "local"}:$activityId:v1"

    fun getMemory(key: String): ImageBitmap? = memoryCache.get(key)

    fun putMemory(key: String, bitmap: ImageBitmap) {
        memoryCache.put(key, bitmap)
    }

    suspend fun getOrRender(
        key: String,
        routeProvider: suspend () -> RecordedGpsRoute?
    ): ImageBitmap? {
        getMemory(key)?.let { return it }

        val fromDisk = withContext(Dispatchers.IO) { readDiskThumbnail(key) }
        if (fromDisk != null) {
            putMemory(key, fromDisk)
            return fromDisk
        }

        val route = routeProvider() ?: return null
        val rendered = withContext(Dispatchers.Default) { renderRouteAndroidBitmap(route) } ?: return null
        withContext(Dispatchers.IO) { writeDiskThumbnail(key, rendered) }
        val imageBitmap = rendered.asImageBitmap()
        putMemory(key, imageBitmap)
        return imageBitmap
    }

    internal fun readDiskThumbnail(key: String): ImageBitmap? = runCatching {
        val file = diskFile(key)
        if (!file.exists()) return null
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }.getOrNull()

    internal fun writeDiskThumbnail(key: String, bitmap: Bitmap) {
        runCatching {
            if (!diskDir.exists()) diskDir.mkdirs()
            val target = diskFile(key)
            val tmp = File(diskDir, "${target.name}.tmp")
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }

    private fun diskFile(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(diskDir, "$digest.png")
    }

    companion object {
        private const val THUMB_WIDTH = 168
        private const val THUMB_HEIGHT = 128

        internal fun renderRouteAndroidBitmap(
            route: RecordedGpsRoute,
            widthPx: Int = THUMB_WIDTH,
            heightPx: Int = THUMB_HEIGHT
        ): Bitmap? {
            val lines = route.displaySegments
            val segments = if (lines.size > 1) listOf(lines.maxByOrNull { it.size }.orEmpty()) else lines
            val points = segments.flatten()
            if (points.size < 2) return null

            val projection = cos(Math.toRadians(points.map { it.latitude }.average())).coerceAtLeast(0.01)
            val minX = points.minOf { it.longitude * projection }
            val maxX = points.maxOf { it.longitude * projection }
            val minY = points.minOf { it.latitude }
            val maxY = points.maxOf { it.latitude }

            val dx = (maxX - minX).coerceAtLeast(0.000001)
            val dy = (maxY - minY).coerceAtLeast(0.000001)
            val pad = 16f
            val innerW = (widthPx - pad).coerceAtLeast(1f)
            val innerH = (heightPx - pad).coerceAtLeast(1f)
            val scale = minOf(innerW / dx, innerH / dy)

            fun posX(p: RecordedGpsPoint): Float =
                ((widthPx - (maxX - minX) * scale) / 2.0 + (p.longitude * projection - minX) * scale).toFloat()

            fun posY(p: RecordedGpsPoint): Float =
                ((heightPx - (maxY - minY) * scale) / 2.0 + (maxY - p.latitude) * scale).toFloat()

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = android.graphics.Color.argb(51, 216, 251, 112)
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = android.graphics.Color.rgb(216, 251, 112)
                strokeWidth = 4.4f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            segments.forEach { segment ->
                if (segment.size > 1) {
                    val path = Path().apply {
                        moveTo(posX(segment.first()), posY(segment.first()))
                        segment.drop(1).forEach { pt ->
                            lineTo(posX(pt), posY(pt))
                        }
                    }
                    canvas.drawPath(path, glowPaint)
                    canvas.drawPath(path, linePaint)
                }
            }
            return bitmap
        }
    }
}

internal object SharedThumbnailCacheHolder {
    @Volatile private var instance: ActivityThumbnailCache? = null

    fun get(context: Context): ActivityThumbnailCache {
        return instance ?: synchronized(this) {
            instance ?: ActivityThumbnailCache(context.applicationContext.cacheDir).also {
                instance = it
            }
        }
    }
}
