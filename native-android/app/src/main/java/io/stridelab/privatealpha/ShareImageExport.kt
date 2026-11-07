package io.stridelab.privatealpha

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.util.UUID

internal object ShareImageExport {
    fun fileName():String = "StrideLab-${UUID.randomUUID()}.png"
    fun writePng(bitmap:Bitmap, output:OutputStream) {
        output.use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) { "Could not render PNG" } }
    }

    fun saveToGallery(context:Context, bitmap:Bitmap):Uri {
        check(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)
        val resolver=context.contentResolver
        val uri=checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,fileName())
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/StrideLab")
            put(MediaStore.Images.Media.IS_PENDING,1)
        })) { "Could not create gallery image" }
        try {
            writePng(bitmap,checkNotNull(resolver.openOutputStream(uri)) { "Could not open image" })
            check(resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)>0)
            return uri
        } catch(error:Exception) {
            resolver.delete(uri,null,null)
            throw error
        }
    }

    fun shareIntent(context:Context, bitmap:Bitmap):Intent {
        val directory=File(context.cacheDir,"share-studio").apply { check(isDirectory || mkdirs()) }
        // shortcut: cache survives the share sheet, expire old PNGs on the next share after 24 hours.
        directory.listFiles()?.filter { it.isFile && it.extension=="png" && it.lastModified()<System.currentTimeMillis()-86_400_000 }
            ?.forEach { it.delete() }
        val file=File(directory,fileName())
        try { writePng(bitmap,file.outputStream()) } catch(error:Exception) { file.delete(); throw error }
        val uri=FileProvider.getUriForFile(context,context.packageName+".share-studio",file)
        return Intent(Intent.ACTION_SEND).apply {
            type="image/png"; putExtra(Intent.EXTRA_STREAM,uri)
            clipData=ClipData.newUri(context.contentResolver,"StrideLab activity image",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
