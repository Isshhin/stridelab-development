package io.stridelab.privatealpha

import java.time.Instant

internal data class GarminCredentialRecord(
    val accountId:String,
    val apiKey:String,
    val bearerToken:String?=null,
    val tokenExpiresAt:Instant?=null
) {
    init {
        require(accountId.isNotBlank() && apiKey.isNotBlank())
        require((bearerToken==null)==(tokenExpiresAt==null))
    }
    override fun toString()="GarminCredentialRecord(accountId=$accountId,tokenExpiresAt=$tokenExpiresAt)"
}

internal interface GarminCredentialStore {
    fun save(record:GarminCredentialRecord)
    fun read(accountId:String):GarminCredentialRecord?
    fun clear(accountId:String)
}
