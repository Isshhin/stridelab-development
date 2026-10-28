package io.stridelab.privatealpha

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

/** Original FIT bytes, app-private and account-scoped. No production DB migration or sync mutation. */
internal class FitImportCache(context: Context) {
    private val root = File(context.filesDir,"fit-import-cache-v1")
    private fun file(account: String, hash: String): AtomicFile {
        require(account.isNotBlank() && account.length<=256)
        require(hash.matches(Regex("[0-9a-f]{64}")))
        val owner = MessageDigest.getInstance("SHA-256").digest(account.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(File(root,owner),"$hash.fit"))
    }
    fun saveSelected(bytes: ByteArray, account: String): FitActivityDecoder.Activity = synchronized(lock) {
        val activity = FitActivityDecoder.decode(bytes,account)
        val target = file(account,activity.identity.sha256!!)
        if(target.baseFile.exists() || File(target.baseFile.path+".bak").exists()) {
            return@synchronized load(account,activity.identity.sha256!!).also {
                require(it.identity==activity.identity)
            }
        }
        require(target.baseFile.parentFile!!.mkdirs() || target.baseFile.parentFile!!.isDirectory)
        val output = target.startWrite()
        try { output.write(bytes); target.finishWrite(output) }
        catch(e: Exception) { target.failWrite(output); throw e }
        activity
    }
    fun load(account: String, hash: String): FitActivityDecoder.Activity = synchronized(lock) {
        // A failed decode keeps the original cache for explicit recovery/review.
        val bytes = file(account,hash).openRead().use { input ->
            input.readBytesBounded()
        }
        FitActivityDecoder.decode(bytes,account).also { require(it.identity.sha256==hash) }
    }
    companion object { private val lock=Any() }
}

internal fun java.io.InputStream.readBytesBounded(): ByteArray {
    val output=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(true) {
        val n=read(buffer)
        if(n<0)break
        require(output.size()+n<=FitContainerReader.MAX_BYTES) { "FIT file exceeds size limit" }
        output.write(buffer,0,n)
    }
    return output.toByteArray()
}
