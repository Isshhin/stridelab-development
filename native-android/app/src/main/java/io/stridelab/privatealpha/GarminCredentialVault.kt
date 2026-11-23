package io.stridelab.privatealpha

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Account-scoped Android Keystore storage. Files and logs never contain plaintext secrets. */
internal class GarminCredentialVault(context:Context):GarminCredentialStore {
    private val directory=java.io.File(context.applicationContext.filesDir,"garmin-personal")
    private val lock=Any()

    override fun save(record:GarminCredentialRecord) = synchronized(lock) {
        var stream:java.io.FileOutputStream?=null
        var target:AtomicFile?=null
        try {
            if(!directory.isDirectory && !directory.mkdirs())throw IOException("Credential directory is unavailable")
            val plain=JSONObject().put("accountId",record.accountId).put("apiKey",record.apiKey)
                .put("bearerToken",record.bearerToken).put("tokenExpiresAt",record.tokenExpiresAt?.toString())
                .toString().toByteArray(StandardCharsets.UTF_8)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,key(record.accountId,true))
            cipher.updateAAD(aad(record.accountId))
            val encrypted=cipher.doFinal(plain)
            require(cipher.iv.size==12 && encrypted.size+16<=MAX_BYTES)
            target=file(record.accountId)
            stream=target.startWrite()
            stream.write(MAGIC);stream.write(cipher.iv);stream.write(encrypted)
            target.finishWrite(stream);stream=null
        } catch(error:Exception) {
            if(target!=null && stream!=null)target.failWrite(stream)
            throw IOException("Could not securely save Garmin credentials; existing data was retained where possible")
        }
    }

    override fun read(accountId:String):GarminCredentialRecord? = synchronized(lock) {
        validateAccount(accountId)
        try {
            val source=file(accountId)
            if(!source.baseFile.exists() && !java.io.File(source.baseFile.path+".bak").exists())return null
            val bytes=ByteArrayOutputStream()
            source.openRead().use { input ->
                val block=ByteArray(4096)
                while(true) {
                    val count=input.read(block);if(count<0)break
                    if(bytes.size()+count>MAX_BYTES)throw IOException("Credential file is too large")
                    bytes.write(block,0,count)
                }
            }
            val encoded=bytes.toByteArray()
            if(encoded.size<32 || !encoded.copyOfRange(0,4).contentEquals(MAGIC))
                throw IOException("Unsupported credential format")
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(accountId,false),GCMParameterSpec(128,encoded.copyOfRange(4,16)))
            cipher.updateAAD(aad(accountId))
            val json=JSONObject(String(cipher.doFinal(encoded.copyOfRange(16,encoded.size)),StandardCharsets.UTF_8))
            if(json.getString("accountId")!=accountId)throw IOException("Credential owner mismatch")
            val token=json.optString("bearerToken").ifBlank { null }
            val expiry=json.optString("tokenExpiresAt").ifBlank { null }?.let(Instant::parse)
            GarminCredentialRecord(accountId,json.getString("apiKey"),token,expiry)
        } catch(error:Exception) {
            throw IOException("Saved Garmin credentials are unavailable or could not be verified; reconnect Garmin")
        }
    }

    override fun clear(accountId:String) = synchronized(lock) {
        validateAccount(accountId)
        try {
            val target=file(accountId);target.delete()
            val store=KeyStore.getInstance("AndroidKeyStore");store.load(null)
            val alias=alias(accountId);if(store.containsAlias(alias))store.deleteEntry(alias)
            if(target.baseFile.exists())throw IOException("Credential file could not be removed")
        } catch(error:Exception) { throw IOException("Could not finish removing Garmin credentials") }
    }

    private fun file(accountId:String)=AtomicFile(java.io.File(directory,digest(accountId)+".vault"))
    private fun aad(accountId:String)=("io.stridelab.privatealpha|garmin-personal-v1|$accountId")
        .toByteArray(StandardCharsets.UTF_8)
    private fun alias(accountId:String)="io.stridelab.privatealpha.garmin.v1.${digest(accountId)}"
    private fun key(accountId:String,create:Boolean):SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore");store.load(null)
        val name=alias(accountId)
        if(store.containsAlias(name))return store.getKey(name,null) as SecretKey
        if(!create)throw IOException("Garmin credential key is unavailable")
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(name,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun digest(accountId:String):String {
        validateAccount(accountId)
        return MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(java.util.Locale.ROOT,it.toInt() and 255) }
    }
    private fun validateAccount(accountId:String) {
        require(accountId.isNotBlank() && accountId.length<=256 && !accountId.contains('\u0000'))
    }
    companion object {
        private const val MAX_BYTES=128*1024
        private val MAGIC=byteArrayOf('S'.code.toByte(),'L'.code.toByte(),'G'.code.toByte(),1)
    }
}
