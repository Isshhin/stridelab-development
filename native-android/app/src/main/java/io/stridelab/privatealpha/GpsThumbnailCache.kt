package io.stridelab.privatealpha

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.cos

/** One atomic private file contains the source fingerprint and rendered route. */
internal class GpsThumbnailCache(context: Context) {
    private val root = File(context.filesDir, "gps-thumbnails-v1")
    private fun hash(value: String) = hashBytes(value.toByteArray(Charsets.UTF_8))
    private fun hashBytes(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun file(account: String, id: String) = AtomicFile(File(File(root, hash(account)), hash(id)))

    fun remove(account: String, id: String) {
        val target = file(account, id)
        target.delete()
        check(!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists())
    }
    fun hasNoRoute(account: String, id: String): Boolean = try {
        DataInputStream(file(account, id).openRead()).use { input ->
            input.readInt() == 2 && input.readUTF().matches(Regex("[0-9a-f]{64}")) &&
                input.readUTF() == hashBytes(ByteArray(0)) && input.readInt() == 0 && input.read() == -1
        }
    } catch (_: Exception) { false }
    fun fingerprint(route: RecordedGpsRoute): String = hash(route.segments.joinToString("|") { segment ->
        segment.joinToString(";") { "${it.latitude},${it.longitude},${it.elapsedSeconds}" }
    })
    fun storedFingerprint(account: String, id: String): String? = try {
        DataInputStream(file(account, id).openRead()).use { input ->
            require(input.readInt() == 2)
            input.readUTF().also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        }
    } catch (_: Exception) { null }

    fun read(account: String, id: String): Bitmap? = try {
        DataInputStream(file(account, id).openRead()).use { input ->
            require(input.readInt() == 2)
            require(input.readUTF().matches(Regex("[0-9a-f]{64}")))
            val checksum = input.readUTF()
            val bytes = input.readInt().also { require(it in 1..262144) }
            val content = ByteArray(bytes)
            input.readFully(content)
            require(input.read() == -1 && hashBytes(content) == checksum)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(content, 0, bytes, options)
            require(options.outWidth == 198 && options.outHeight == 192)
            BitmapFactory.decodeByteArray(content, 0, bytes)
        }
    } catch (_: Exception) { null }

    fun write(account: String, id: String, route: RecordedGpsRoute) =
        save(account, id, fingerprint(route), render(route))

    fun render(route: RecordedGpsRoute): ByteArray {
        val segments = if (route.allPoints.let { it.isNotEmpty() && it.maxOf { p -> p.longitude } - it.minOf { p -> p.longitude } > 180 })
            listOf(route.segments.maxByOrNull { it.size }.orEmpty()) else route.segments
        val points = segments.flatten()
        if (segments.none { it.size >= 2 }) return ByteArray(0)
        val projection = cos(Math.toRadians(points.map { it.latitude }.average())).coerceAtLeast(.01)
        val minX = points.minOf { it.longitude * projection }; val maxX = points.maxOf { it.longitude * projection }
        val minY = points.minOf { it.latitude }; val maxY = points.maxOf { it.latitude }
        val scale = minOf(174.0 / (maxX-minX).coerceAtLeast(.000001), 168.0 / (maxY-minY).coerceAtLeast(.000001))
        val bitmap = Bitmap.createBitmap(198,192,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8B9498.toInt(); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        segments.filter { it.size > 1 }.forEach { segment ->
            val path = Path()
            segment.forEachIndexed { i, p ->
                val x = ((198 - (maxX-minX)*scale)/2 + (p.longitude*projection-minX)*scale).toFloat()
                val y = ((192 - (maxY-minY)*scale)/2 + (maxY-p.latitude)*scale).toFloat()
                if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            canvas.drawPath(path,paint)
        }
        val bytes = java.io.ByteArrayOutputStream().use { output ->
            @Suppress("DEPRECATION")
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output))
            output.toByteArray()
        }
        bitmap.recycle()
        return bytes
    }

    fun save(account: String, id: String, fingerprint: String, bytes: ByteArray) {
        val target = file(account,id)
        check(target.baseFile.parentFile!!.mkdirs() || target.baseFile.parentFile!!.isDirectory)
        val stream = target.startWrite()
        try {
            val output = DataOutputStream(stream)
            output.writeInt(2); output.writeUTF(fingerprint); output.writeUTF(hashBytes(bytes)); output.writeInt(bytes.size); output.write(bytes)
            output.flush(); target.finishWrite(stream)
        } catch (e: Exception) { target.failWrite(stream); throw e }
    }
}
