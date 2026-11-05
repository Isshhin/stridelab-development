package io.stridelab.privatealpha

import java.time.Instant

internal enum class GarminIssueKind {
    AUTHENTICATION, NETWORK, RATE_LIMIT, SERVICE, ACTIVITY_DATA, SECURE_STORAGE, OTHER
}

internal data class GarminConnectionStatus(
    val state:String,
    val lastSuccessfulSync:Instant?=null,
    val importedCount:Int=0,
    val error:String?=null,
    val issueKind:GarminIssueKind?=null
) {
    init { require(state in setOf("not_connected","connected","error")) }

    /** Never present a malformed activity or offline source as expired credentials. */
    fun heading():String=when(state) {
        "connected" -> "Connected"
        "not_connected" -> "Not connected"
        else -> when(issueKind) {
            GarminIssueKind.AUTHENTICATION -> "Garmin authentication needs attention"
            GarminIssueKind.NETWORK -> "Garmin connection unavailable"
            GarminIssueKind.RATE_LIMIT -> "Garmin is rate limiting requests"
            GarminIssueKind.SERVICE -> "Garmin service needs attention"
            GarminIssueKind.ACTIVITY_DATA -> "Garmin activity data needs attention"
            GarminIssueKind.SECURE_STORAGE -> "Saved Garmin connection needs attention"
            else -> "Garmin sync needs attention"
        }
    }

    companion object {
        fun fromError(error:Throwable):GarminConnectionStatus {
            val kind=when(error) {
                is GarminAuthenticationException -> GarminIssueKind.AUTHENTICATION
                is GarminRateLimitException -> GarminIssueKind.RATE_LIMIT
                is GarminServiceException -> GarminIssueKind.SERVICE
                is GarminProtocolException -> GarminIssueKind.ACTIVITY_DATA
                is GarminNetworkException -> GarminIssueKind.NETWORK
                is java.io.IOException -> GarminIssueKind.SECURE_STORAGE
                else -> GarminIssueKind.OTHER
            }
            // Error messages from network/JSON code must never contain API keys or tokens.
            val safeMessage=when(kind) {
                GarminIssueKind.AUTHENTICATION -> error.message ?: "Garmin authorization was rejected"
                GarminIssueKind.NETWORK -> "Garmin could not be reached. Check your connection."
                GarminIssueKind.RATE_LIMIT -> "Garmin rate limit reached; try again later."
                GarminIssueKind.SERVICE -> error.message ?: "Garmin service request failed"
                GarminIssueKind.ACTIVITY_DATA -> error.message ?: "Garmin returned unsupported activity data"
                GarminIssueKind.SECURE_STORAGE -> "Secure Garmin credential storage is unavailable."
                else -> "Garmin synchronization could not be completed."
            }
            return GarminConnectionStatus("error",error=safeMessage,issueKind=kind)
        }
    }
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
        if(record.bearerToken!=null)GarminConnectionStatus("connected")
        else GarminConnectionStatus("error",error="Garmin authorization is required",
            issueKind=GarminIssueKind.AUTHENTICATION)
    } catch(error:Exception) {
        GarminConnectionStatus("error",error="Saved Garmin credentials could not be verified",
            issueKind=GarminIssueKind.SECURE_STORAGE)
    }

    fun connect(apiKey:String):GarminConnectionStatus {
        require(apiKey.isNotBlank()) { "A Garmin Personal API key is required" }
        credentials.save(GarminCredentialRecord(accountId,apiKey))
        return try {
            val token=api.exchange(apiKey)
            credentials.save(GarminCredentialRecord(accountId,apiKey,token.value,token.expiresAt))
            GarminConnectionStatus("connected")
        } catch(error:GarminException) {
            GarminConnectionStatus.fromError(error)
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
        val storedToken=if(record.bearerToken!=null && record.tokenExpiresAt!=null) {
            GarminBearerToken(record.bearerToken,record.tokenExpiresAt)
        } else null
        // Refresh close to expiry, not a whole hour early: a paged sync can
        // otherwise exchange a short-lived token separately on every page.
        val usableStored=storedToken?.takeIf { it.usableAt(clock(),refreshWindowSeconds=60) }
        fun exchangeAndStore():GarminBearerToken=api.exchange(record.apiKey).also {
            credentials.save(GarminCredentialRecord(accountId,record.apiKey,it.value,it.expiresAt))
        }
        val current=usableStored ?: exchangeAndStore()
        val activities=try {
            api.activities(current,start,limit)
        } catch(error:GarminAuthenticationException) {
            // A stored token may be revoked before its expiry. Retry exactly once
            // with a freshly exchanged token. Never retry a just-exchanged token.
            if(usableStored==null)throw error
            api.activities(exchangeAndStore(),start,limit)
        }
        val records=activities.map { GarminActivityNormalizer.normalize(accountId,it) }
        return WearablePage(accountId,records,(start+records.size).takeIf { records.size==limit }?.toString())
    }

    override fun wellness(accountId:String,cursor:String?,limit:Int):WearablePage<WearableWellnessInput> {
        require(accountId==this.accountId)
        require(limit in 1..100)
        // Official health endpoints are intentionally not guessed into this foundation.
        return WearablePage(accountId,emptyList(),null)
    }
}
