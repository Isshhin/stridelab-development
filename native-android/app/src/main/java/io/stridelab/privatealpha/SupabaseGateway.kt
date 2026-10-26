package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

internal class ServiceFailure(val status: Int, message: String, val requiresSignIn: Boolean = status == 401): IOException(message)
internal interface TrainingGateway {
    fun signIn(email: String, password: String): NativeSession
    fun refresh(session: NativeSession): NativeSession
    fun verify(session: NativeSession)
    fun activities(session: NativeSession, before: ActivityRecord?): JSONArray
    fun intervals(session: NativeSession, action: String): JSONObject
    fun wellness(session: NativeSession): JSONArray = throw java.io.IOException("Wellness unavailable")
    fun detail(session: NativeSession, id: String): JSONObject = throw java.io.IOException("Activity streams unavailable")
    fun signOut(session: NativeSession)
}
internal fun interface BackendTransport {
    fun execute(path: String, token: String?, body: JSONObject?): String
}

/** Whitelisted messages only: never echo server bodies, credentials or JWTs. */
internal object ServiceErrors {
    fun from(status: Int, path: String, raw: String): ServiceFailure {
        val code = runCatching { val j = JSONObject(raw); j.optString("error_code", j.optString("code")) }.getOrDefault("")
        val message = when {
            status == 429 -> "Too many requests. Wait a moment before trying again."
            code == "email_not_confirmed" -> "Confirm your email address before signing in."
            code == "invalid_credentials" -> "The email or password was not accepted. Please check both."
            code in setOf("mfa_required", "insufficient_aal") -> "This account requires an additional sign-in step that this build does not yet support."
            path.contains("grant_type=refresh_token") && status in setOf(400,401,403) -> "Your saved session has expired or was revoked. Sign in again; cached activities are retained."
            status == 401 -> "Your session needs to be renewed. Cached activities are retained."
            status == 403 -> "This account does not have permission for this request. Cached data is retained."
            status == 409 -> "Intervals.icu is not connected or needs attention in your existing integration settings."
            status == 400 && path.startsWith("/auth/") -> "Sign-in was not accepted. Check your email and password."
            else -> "The service could not complete the request (HTTP $status). Cached data is retained."
        }
        return ServiceFailure(status, message, status == 401 || (path.contains("grant_type=refresh_token") && status in setOf(400,403)))
    }
}

/** Fixed HTTPS origin, bounded bodies, no redirects, disk cache or secret-bearing diagnostics. */
internal class HttpsBackendTransport: BackendTransport {
    override fun execute(path: String, token: String?, body: JSONObject?): String {
        require(path.startsWith("/") && !path.startsWith("//"))
        val connection = URI(BackendConfig.URL + path).toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false; connection.useCaches = false
            connection.connectTimeout = 20_000; connection.readTimeout = if(path.startsWith("/functions/")) 125_000 else 30_000
            connection.setRequestProperty("apikey", BackendConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            if(token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if(body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if(status !in 200..299) {
                val error = runCatching { connection.errorStream?.use { input -> val bytes = java.io.ByteArrayOutputStream()
                    val block = ByteArray(1024)
                    while(bytes.size() < 8192) { val count = input.read(block,0,minOf(block.size,8192-bytes.size())); if(count < 0) break; bytes.write(block,0,count) }
                    bytes.toString("UTF-8") } ?: "" }.getOrDefault("")
                throw ServiceErrors.from(status, path, error)
            }
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val block = ByteArray(8192)
                while(true) { val n = input.read(block); if(n < 0) break
                    if(out.size() + n > 8 * 1024 * 1024) throw IOException("Server response is too large; cache retained")
                    out.write(block,0,n)
                }
            }
            return out.toString("UTF-8")
        } finally { connection.disconnect() }
    }
}

internal class SupabaseGateway(private val transport: BackendTransport = HttpsBackendTransport(), private val now: () -> Long = { System.currentTimeMillis()/1000 }): TrainingGateway {
    private fun request(path: String, token: String? = null, body: JSONObject? = null) = transport.execute(path, token, body)
    private fun parseSession(raw: String, expected: String? = null): NativeSession {
        val j = JSONObject(raw); val account = j.getJSONObject("user").getString("id")
        if(expected != null && expected != account) throw ServiceFailure(401,"The renewed session did not match this account. Sign in again; cached data is retained.")
        val expires = if(!j.isNull("expires_at")) j.getLong("expires_at") else Math.addExact(now(),j.getLong("expires_in"))
        return NativeSession(account,j.getString("access_token"),j.getString("refresh_token"),expires)
    }
    override fun signIn(email: String, password: String) = parseSession(request("/auth/v1/token?grant_type=password",body = JSONObject().put("email",email.trim()).put("password",password)))
    override fun refresh(session: NativeSession) = parseSession(request("/auth/v1/token?grant_type=refresh_token",body = JSONObject().put("refresh_token",session.refreshToken)),session.account)
    override fun verify(session: NativeSession) { require(JSONObject(request("/auth/v1/user",session.accessToken)).getString("id") == session.account) { "Session owner does not match" } }
    override fun activities(session: NativeSession, before: ActivityRecord?): JSONArray {
        val owner = URLEncoder.encode(session.account,"UTF-8")
        val cursor = before?.let {
            val quotedId = JSONObject.quote(it.id)
            val filter = "(started_at.lt.${it.startedAt},and(started_at.eq.${it.startedAt},id.lt.$quotedId))"
            "&or=" + URLEncoder.encode(filter,"UTF-8")
        } ?: ""
        return JSONArray(request("/rest/v1/activities?select=*&user_id=eq.$owner&source=neq.demo&order=started_at.desc,id.desc&limit=200$cursor",session.accessToken))
    }
    override fun intervals(session: NativeSession, action: String): JSONObject {
        require(action in setOf("status","sync","backfill"))
        return JSONObject(request("/functions/v1/stridelab-intervals",session.accessToken,JSONObject().put("action",action)))
    }
    override fun wellness(session: NativeSession): JSONArray {
        val all=JSONArray(); var offset=0
        val owner=URLEncoder.encode(session.account,"UTF-8")
        val since=URLEncoder.encode(java.time.Instant.ofEpochSecond(now()).minusSeconds(30*86400L).toString(),"UTF-8")
        while(true) {
            val page=JSONArray(request("/rest/v1/wellness_metrics?select=*&user_id=eq.$owner&recorded_at=gte.$since&order=recorded_at.desc,id.desc&limit=200&offset=$offset",session.accessToken))
            require(page.length()<=200 && all.length()+page.length()<=2000)
            if(page.length()==0) return all
            for(i in 0 until page.length()) { val row=page.getJSONObject(i); require(row.getString("user_id")==session.account); all.put(row) }
            offset+=page.length()
        }
    }
    override fun detail(session: NativeSession, id: String): JSONObject {
        RecordPolicy.identifier(id)
        val activity = URLEncoder.encode(id,"UTF-8")
        val owner = URLEncoder.encode(session.account,"UTF-8")
        fun rows(table: String, order: String, cap: Int): JSONArray {
            val all = JSONArray()
            var offset = 0
            while(true) {
                val page = JSONArray(request("/rest/v1/$table?select=*,activities!inner(user_id)&activity_id=eq.$activity&activities.user_id=eq.$owner&order=$order.asc&limit=1000&offset=$offset",session.accessToken))
                require(page.length() <= 1000)
                if(page.length() == 0) return all
                require(all.length()+page.length() <= cap) { "Activity detail exceeds safe size" }
                for(i in 0 until page.length()) {
                    val row = page.getJSONObject(i)
                    require(row.getString("activity_id") == id && row.getJSONObject("activities").getString("user_id") == session.account)
                    row.remove("activities"); all.put(row)
                }
                offset += page.length()
            }
        }
        return JSONObject().put("user_id",session.account).put("activity_id",id)
            .put("samples",rows("activity_samples","sample_index",20000))
            .put("laps",rows("activity_laps","lap_index",1000)).put("fetched_at",System.currentTimeMillis())
    }
    override fun signOut(session: NativeSession) { request("/auth/v1/logout?scope=local",session.accessToken,JSONObject()) }
}
