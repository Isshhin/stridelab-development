package io.stridelab.privatealpha

import java.time.Instant

internal data class GarminConnectionStatus(
    val state:String,
    val lastSuccessfulSync:Instant?=null,
    val importedCount:Int=0,
    val error:String?=null
) {
    init { require(state in setOf("not_connected","connected","error")) }
}

/** Token lifecycle and source paging only; activity history remains owned by TrainingRepository. */
internal class GarminPersonalSource(
    private val accountId:String,
    private val credentials:GarminCredentialStore,
    private val api:GarminApi,
    private val clock:()->Instant={Instant.now()}
):WearableImportProvider {
    fun status():GarminConnectionStatus = try {
        val record=credentials.read(accountId) ?: return GarminConnectionStatus("not_connected")
        GarminConnectionStatus(if(record.bearerToken!=null)"connected" else "error",
            error=if(record.bearerToken==null)"Authentication required" else null)
    } catch(error:Exception) { GarminConnectionStatus("error",error="Saved Garmin connection needs attention") }

    fun connect(apiKey:String):GarminConnectionStatus {
        require(apiKey.isNotBlank()) { "A Garmin Personal API key is required" }
        credentials.save(GarminCredentialRecord(accountId,apiKey))
        return try {
            val token=api.exchange(apiKey)
            credentials.save(GarminCredentialRecord(accountId,apiKey,token.value,token.expiresAt))
            GarminConnectionStatus("connected")
        } catch(error:GarminException) {
            GarminConnectionStatus("error",error=error.message ?: "Garmin authentication failed")
        }
    }

    fun disconnect() { credentials.clear(accountId) }

    override fun activities(accountId:String,cursor:String?,limit:Int):WearablePage<WearableActivityInput> {
        require(accountId==this.accountId) { "Garmin source owner does not match" }
        require(limit in 1..100)
        val start=cursor?.toIntOrNull() ?: 0
        require(start>=0)
        val record=credentials.read(accountId)
            ?: throw GarminAuthenticationException("Garmin is not connected")
        val token=if(record.bearerToken!=null && record.tokenExpiresAt!=null) {
            GarminBearerToken(record.bearerToken,record.tokenExpiresAt)
        } else null
        val usable=token?.takeIf { it.usableAt(clock()) } ?: api.exchange(record.apiKey).also {
            credentials.save(GarminCredentialRecord(accountId,record.apiKey,it.value,it.expiresAt))
        }
        val records=api.activities(usable,start,limit).map { GarminActivityNormalizer.normalize(accountId,it) }
        return WearablePage(accountId,records,(start+records.size).takeIf { records.size==limit }?.toString())
    }

    override fun wellness(accountId:String,cursor:String?,limit:Int):WearablePage<WearableWellnessInput> {
        require(accountId==this.accountId)
        require(limit in 1..100)
        // Official health endpoints are intentionally not guessed into this foundation.
        return WearablePage(accountId,emptyList(),null)
    }
}
