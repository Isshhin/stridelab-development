package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64

internal data class GarminHttpRequest(
    val method:String,
    val url:String,
    val headers:Map<String,String>
) {
    override fun toString()="GarminHttpRequest(method=$method,url=$url,headerNames=${headers.keys})"
}
internal data class GarminHttpResponse(val status:Int,val body:String)
internal fun interface GarminHttpTransport { fun execute(request:GarminHttpRequest):GarminHttpResponse }

internal class GarminUrlConnectionTransport:GarminHttpTransport {
    override fun execute(request:GarminHttpRequest):GarminHttpResponse {
        try {
            val connection=(URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod=request.method
                connectTimeout=15_000
                readTimeout=30_000
                useCaches=false
                request.headers.forEach(::setRequestProperty)
            }
            val status=connection.responseCode
            val stream=if(status in 200..299)connection.inputStream else connection.errorStream
            val body=stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()
            return GarminHttpResponse(status,body)
        } catch(error:IOException) {
            throw GarminNetworkException("Garmin could not be reached",error)
        }
    }
}

internal open class GarminException(message:String,cause:Throwable?=null):Exception(message,cause)
internal class GarminAuthenticationException(message:String):GarminException(message)
internal class GarminRateLimitException:GarminException("Garmin rate limit reached; try again later")
internal class GarminServiceException(status:Int):GarminException("Garmin service request failed with status $status")
internal class GarminTokenException:GarminException("Garmin returned an invalid access token")
internal class GarminProtocolException(message:String):GarminException(message)
internal class GarminNetworkException(message:String,cause:Throwable):GarminException(message,cause)

internal data class GarminBearerToken(val value:String,val expiresAt:Instant) {
    init { require(value.isNotBlank()) }
    fun usableAt(now:Instant,refreshWindowSeconds:Long=3600)=expiresAt.isAfter(now.plusSeconds(refreshWindowSeconds))
    override fun toString()="GarminBearerToken(expiresAt=$expiresAt)"
}

internal data class GarminActivity(
    val externalId:String,
    val title:String,
    val type:String,
    val startedAt:Instant,
    val durationSeconds:Double?,
    val distanceMeters:Double?,
    val movingDurationSeconds:Double?,
    val averageHeartRate:Double?
)

internal interface GarminApi {
    fun exchange(apiKey:String):GarminBearerToken
    fun activities(token:GarminBearerToken,start:Int,limit:Int):List<GarminActivity>
}

/** Official Garmin Connect Personal API boundary. It neither persists nor alters coaching. */
internal class GarminPersonalClient(
    private val transport:GarminHttpTransport=GarminUrlConnectionTransport(),
    private val clock:()->Instant={Instant.now()}
):GarminApi {
    companion object {
        const val BASE_URL="https://personal.connectapi.garmin.com"
        const val TOKEN_PATH="/personalapitoken-service/personal-api/personal-api-key/token"
        const val SEARCH_PATH="/activitylist-service/activities/search/activities"
    }

    override fun exchange(apiKey:String):GarminBearerToken {
        require(apiKey.isNotBlank()) { "A Garmin Personal API key is required" }
        val response=execute(GarminHttpRequest("POST",BASE_URL+TOKEN_PATH,
            mapOf("Accept" to "*/*","Authorization" to "ApiKey $apiKey")))
        validate(response)
        val token=response.body.trim()
        val expires=parseExpiry(token)
        if(!expires.isAfter(clock()))throw GarminAuthenticationException("Garmin authentication has expired")
        return GarminBearerToken(token,expires)
    }

    override fun activities(token:GarminBearerToken,start:Int,limit:Int):List<GarminActivity> {
        require(start>=0 && limit in 1..100)
        if(!token.expiresAt.isAfter(clock()))throw GarminAuthenticationException("Garmin authentication has expired")
        val response=execute(GarminHttpRequest("GET",
            "$BASE_URL$SEARCH_PATH?start=$start&limit=$limit",
            mapOf("Accept" to "application/json","Authorization" to "Bearer ${token.value}")))
        validate(response)
        return try {
            val trimmed=response.body.trim()
            val array=if(trimmed.startsWith("["))JSONArray(trimmed)
                else JSONObject(trimmed).optJSONArray("activities")
                    ?: throw GarminProtocolException("Garmin returned no activity list")
            (0 until array.length()).map { decodeActivity(array.getJSONObject(it)) }
        } catch(error:GarminException) { throw error }
        catch(error:Exception) { throw GarminProtocolException("Garmin returned malformed activity data") }
    }

    private fun execute(request:GarminHttpRequest)=try { transport.execute(request) }
        catch(error:GarminException) { throw error }
        catch(error:IOException) { throw GarminNetworkException("Garmin could not be reached",error) }

    private fun validate(response:GarminHttpResponse) {
        when(response.status) {
            in 200..299 -> return
            401,403 -> throw GarminAuthenticationException("Garmin authentication was rejected")
            429 -> throw GarminRateLimitException()
            else -> throw GarminServiceException(response.status)
        }
    }

    private fun parseExpiry(token:String):Instant=try {
        val parts=token.split('.')
        if(parts.size!=3)throw IllegalArgumentException()
        val payload=String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8)
        Instant.ofEpochSecond(JSONObject(payload).getLong("exp"))
    } catch(error:Exception) { throw GarminTokenException() }

    private fun decodeActivity(json:JSONObject):GarminActivity {
        fun metric(vararg keys:String):Double?=keys.firstNotNullOfOrNull { key ->
            if(!json.has(key)||json.isNull(key))null else json.optDouble(key).takeIf { it.isFinite() && it>=0 }
        }
        val uuid=json.opt("activityUUID")?.let { value ->
            when(value) { is JSONObject -> value.optString("uuid"); else -> value.toString() }
        }.orEmpty()
        val id=uuid.ifBlank { json.optString("activityId") }
        if(id.isBlank())throw GarminProtocolException("Garmin activity is missing its identifier")
        val typeObject=json.optJSONObject("activityType") ?: json.optJSONObject("activityTypeDTO")
        val type=typeObject?.optString("typeKey")?.ifBlank { typeObject.optString("activityTypeKey") }
            ?.ifBlank { null } ?: json.optString("activityType","unknown")
        val gmt=json.optString("startTimeGMT").trim()
        val timestamp=if(gmt.isNotEmpty()) gmt else {
            val local=json.optString("startTimeLocal").trim()
            // A zone-less "local" timestamp cannot be safely mapped to UTC.
            // It is NOT interchangeable with a UTC/GMT timestamp.
            if(local.isNotEmpty() &&
                !Regex("(?:Z|[+-][0-9]{2}:[0-9]{2})$",RegexOption.IGNORE_CASE).containsMatchIn(local))
                throw GarminProtocolException("Garmin activity is missing a UTC timestamp; local time has no offset")
            local
        }
        return GarminActivity(id,json.optString("activityName","Garmin activity"),type,
            parseGarminTime(timestamp),metric("duration"),metric("distance"),
            metric("movingDuration"),metric("averageHR","averageHeartRate"))
    }

    private fun parseGarminTime(value:String):Instant {
        val timestamp=value.trim()
        if(timestamp.isEmpty())throw GarminProtocolException("Garmin activity is missing its timestamp")
        // Garmin's GMT activity field can use either ISO T or a space separator.
        // Explicit UTC offsets are honored; a zone-less GMT field is interpreted
        // as UTC. Do not use the device timezone for a server GMT timestamp.
        val iso=timestamp.replace(' ','T')
        return runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC) }.getOrNull()
            ?: throw GarminProtocolException("Garmin activity has an invalid timestamp")
    }
}

internal object GarminActivityNormalizer {
    fun normalize(accountId:String,activity:GarminActivity)=WearableActivityInput(
        accountId=accountId,provider="garmin",externalId=activity.externalId,
        activityType=activity.type,startedAt=activity.startedAt,
        durationSeconds=activity.durationSeconds,distanceMeters=activity.distanceMeters,
        title=activity.title,movingDurationSeconds=activity.movingDurationSeconds,
        averageHeartRate=activity.averageHeartRate?.takeIf { it>0 }
    )
    fun candidate(accountId:String,activity:GarminActivity)=ImportedActivityCandidate(
        ImportedSourceIdentity(accountId,"garmin",activity.externalId),activity.type,
        activity.startedAt,activity.durationSeconds,activity.distanceMeters
    )
}
