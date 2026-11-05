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
                is GarminTokenException -> GarminIssueKind.SERVICE
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
        val activities=withBearer(accountId) { api.activities(it,start,limit) }
        val records=activities.map { GarminActivityNormalizer.normalize(accountId,it) }
        return WearablePage(accountId,records,(start+records.size).takeIf { records.size==limit }?.toString())
    }

    /** A single owner's explicit activity detail fetch, never background bulk ingestion. */
    fun downloadFitForSource(accountId:String,externalId:String,numericHint:Long?=null):ByteArray {
        require(accountId==this.accountId)
        require(externalId.isNotBlank() && externalId.length<=512)
        require(numericHint==null || numericHint>0)
        return withBearer(accountId) { token ->
            // Earlier offline summaries stored Garmin's UUID without the
            // numeric activityId needed for the FIT ZIP endpoint. Resolve
            // against the same owner's activity search on demand.
            var numeric=numericHint ?: externalId.toLongOrNull()?.takeIf { it>0 }
            if(numeric==null) {
                for(pageIndex in 0 until 20) {
                    val page=api.activities(token,pageIndex*100,100)
                    val found=page.firstOrNull { it.externalId==externalId }
                    if(found!=null) {
                        numeric=found.numericActivityId
                            ?: throw GarminProtocolException("Garmin did not provide the numeric activity ID")
                        break
                    }
                    if(page.size<100)break
                }
            }
            val id=numeric ?: throw GarminProtocolException(
                "The Garmin activity could not be found in the latest 2,000 records. No GPS was imported.")
            api.downloadActivity(token,id)
        }
    }

    /** Shared bound bearer lifecycle for list and FIT download. */
    private fun <T> withBearer(account:String,action:(GarminBearerToken)->T):T {
        require(account==accountId)
        val stored=credentials.read(account)
            ?: throw GarminAuthenticationException("Garmin is not connected")
        val reusable=if(stored.bearerToken!=null && stored.tokenExpiresAt!=null)
            GarminBearerToken(stored.bearerToken,stored.tokenExpiresAt)
                .takeIf { it.usableAt(clock(),refreshWindowSeconds=60) }
        else null
        fun exchangeAndSave():GarminBearerToken=api.exchange(stored.apiKey).also {
            credentials.save(GarminCredentialRecord(account,stored.apiKey,it.value,it.expiresAt))
        }
        val active=reusable ?: exchangeAndSave()
        return try { action(active) } catch(error:GarminAuthenticationException) {
            if(reusable==null)throw error
            action(exchangeAndSave()) // One retry for a revoked stored bearer.
        }
    }

    override fun wellness(accountId:String,cursor:String?,limit:Int):WearablePage<WearableWellnessInput> {
        require(accountId==this.accountId)
        require(limit in 1..100)
        // Official health endpoints are intentionally not guessed into this foundation.
        return WearablePage(accountId,emptyList(),null)
    }
}
