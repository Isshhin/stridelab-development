package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

internal class ServiceFailure(val status: Int, message: String): IOException(message)
internal interface TrainingGateway {
    fun signIn(email: String, password: String): NativeSession
    fun refresh(session: NativeSession): NativeSession
    fun verify(session: NativeSession)
    fun activities(session: NativeSession, before: ActivityRecord?): JSONArray
    fun intervals(session: NativeSession, action: String): JSONObject
    fun signOut(session: NativeSession)
}

/** Fixed HTTPS origin, bounded responses, no redirects or secret-bearing diagnostic output. */
internal class SupabaseGateway: TrainingGateway {
    private fun request(path: String, token: String? = null, body: JSONObject? = null): String {
        val connection = URI(BackendConfig.URL + path).toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000; connection.readTimeout = 30_000
            connection.setRequestProperty("apikey", BackendConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw ServiceFailure(status, when(status) {
                400, 401, 403 -> "Sign-in or data access was not accepted. Check your account and try again."
                429 -> "Too many requests. Please try again later."
                else -> "The service could not complete the request (HTTP $status). Your cached data is retained."
            })
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val block = ByteArray(8192)
                while (true) { val n = input.read(block); if(n < 0) break
                    if(out.size() + n > 8 * 1024 * 1024) throw IOException("Server response is too large; cached data retained")
                    out.write(block, 0, n)
                }
            }
            return out.toString("UTF-8")
        } finally { connection.disconnect() }
    }
    private fun parseSession(raw: String, expected: String? = null): NativeSession {
        val j = JSONObject(raw); val account = j.getJSONObject("user").getString("id")
        require(expected == null || expected == account) { "Refreshed session changed account" }
        val expires = if(j.has("expires_at")) j.getLong("expires_at") else System.currentTimeMillis()/1000 + j.getLong("expires_in")
        return NativeSession(account, j.getString("access_token"), j.getString("refresh_token"), expires)
    }
    override fun signIn(email: String, password: String) = parseSession(request("/auth/v1/token?grant_type=password", body = JSONObject().put("email", email.trim()).put("password", password)))
    override fun refresh(session: NativeSession) = parseSession(request("/auth/v1/token?grant_type=refresh_token", body = JSONObject().put("refresh_token", session.refreshToken)), session.account)
    override fun verify(session: NativeSession) { require(JSONObject(request("/auth/v1/user", session.accessToken)).getString("id") == session.account) { "Session owner does not match" } }
    override fun activities(session: NativeSession, before: ActivityRecord?): JSONArray {
        val owner = URLEncoder.encode(session.account, "UTF-8")
        val cursor = before?.let {
            val filter = "(started_at.lt.${it.startedAt},and(started_at.eq.${it.startedAt},id.lt.${it.id}))"
            "&or=" + URLEncoder.encode(filter, "UTF-8")
        } ?: ""
        return JSONArray(request("/rest/v1/activities?select=*&user_id=eq.$owner&source=neq.demo&order=started_at.desc,id.desc&limit=200$cursor", session.accessToken))
    }
    override fun intervals(session: NativeSession, action: String): JSONObject {
        require(action in setOf("status", "sync", "backfill"))
        return JSONObject(request("/functions/v1/stridelab-intervals", session.accessToken, JSONObject().put("action", action)))
    }
    override fun signOut(session: NativeSession) { request("/auth/v1/logout?scope=local", session.accessToken, JSONObject()) }
}
