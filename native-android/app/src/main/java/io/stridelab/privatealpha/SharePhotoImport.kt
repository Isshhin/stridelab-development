package io.stridelab.privatealpha

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlin.math.roundToInt

/**
 * Imports a bounded editor-only image. URI permissions are not persisted; the
 * picked image is never copied into a preset, database, backup, or network request.
 */
internal object SharePhotoImport {
    fun decode(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        require(mime == null || mime.startsWith("image/")) { "Choose an image file." }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) {
                    decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                require(width > 0 && height > 0 && width.toLong() * height <= 100_000_000L) {
                    "Photo dimensions are unsupported."
                }
                val ratio = minOf(1.0, 2160.0 / width, 2160.0 / height)
                decoder.setTargetSize(
                    (width * ratio).roundToInt().coerceAtLeast(1),
                    (height * ratio).roundToInt().coerceAtLeast(1)
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        requireNotNull(resolver.openInputStream(uri)).use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0 && width.toLong() * height <= 100_000_000L) {
            "Photo dimensions are unsupported."
        }
        var sample = 1
        while (width / sample > 2160 || height / sample > 2160) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return requireNotNull(resolver.openInputStream(uri)).use {
            requireNotNull(BitmapFactory.decodeStream(it, null, options)) {
                "Could not decode the selected photo."
            }
        }
    }
}
