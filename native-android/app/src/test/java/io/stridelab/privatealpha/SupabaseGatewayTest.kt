package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

/** Pure contract tests with a fake transport. No production account is contacted. */
class SupabaseGatewayTest {
    private val session = NativeSession("synthetic-owner","access-fixture","refresh-fixture",5000)
    private fun response(owner: String = session.account) = JSONObject().put("user",JSONObject().put("id",owner))
        .put("access_token","access-new").put("refresh_token","refresh-new").put("expires_in",3600).toString()
    @Test fun passwordContractTrimsEmailButPreservesPasswordAndParsesExpiry() {
        var body: JSONObject? = null
        val api = SupabaseGateway(BackendTransport { path,token,value ->
            assertEquals("/auth/v1/token?grant_type=password",path); assertNull(token); body = value; response()
        },now = { 1000 })
        val current = api.signIn(" fixture@example.invalid "," password fixture ")
        assertEquals("fixture@example.invalid",body!!.getString("email")); assertEquals(" password fixture ",body!!.getString("password"))
        assertEquals(session.account,current.account); assertEquals(4600,current.expiresAtSeconds)
    }
    @Test fun refreshRejectsDifferentAccount() {
        val api = SupabaseGateway(BackendTransport { path,token,body ->
            assertTrue(path.contains("grant_type=refresh_token")); assertNull(token)
            assertEquals(session.refreshToken,body!!.getString("refresh_token")); response("another-owner")
        })
        assertThrows(ServiceFailure::class.java) { api.refresh(session) }
    }
    @Test fun serverUserMustMatchSessionOwner() {
        val api = SupabaseGateway(BackendTransport { path,token,_ -> assertEquals("/auth/v1/user",path); assertEquals(session.accessToken,token); "{\"id\":\"other\"}" })
        assertThrows(IllegalArgumentException::class.java) { api.verify(session) }
    }
    @Test fun cursorKeepsOwnerFilterStableOrderingAndQuotedIdentifier() {
        val record = ActivityRecord.parse("""{"id":"cursor,quoted","user_id":"synthetic-owner","source":"fit","activity_type":"running","started_at":"2026-10-25T20:00:00Z"}""",session.account)
        val api = SupabaseGateway(BackendTransport { path,token,_ ->
            val decoded = URLDecoder.decode(path,"UTF-8")
            assertTrue(decoded.contains("user_id=eq.synthetic-owner")); assertTrue(decoded.contains("order=started_at.desc,id.desc"))
            assertTrue(decoded.contains("id.lt.\"cursor,quoted\"")); assertTrue(decoded.contains("source=neq.demo")); assertEquals(session.accessToken,token); "[]"
        })
        assertEquals(0,api.activities(session,record).length())
    }
    @Test fun rejectsIncompleteSessionResponse() {
        val api = SupabaseGateway(BackendTransport { _,_,_ -> "{\"user\":{\"id\":\"synthetic-owner\"}}" })
        assertThrows(org.json.JSONException::class.java) { api.signIn("fixture@example.invalid","fixture") }
    }
    @Test fun errorMessagesNeverEchoRawSecretsOrBackendDiagnostics() {
        val failure = ServiceErrors.from(400,"/auth/v1/token?grant_type=password","""{"error_code":"invalid_credentials","message":"refresh_token=PRIVATE_DIAGNOSTIC"}""")
        assertTrue(failure.message!!.contains("email or password")); assertFalse(failure.message!!.contains("PRIVATE_DIAGNOSTIC")); assertFalse(failure.requiresSignIn)
        assertTrue(ServiceErrors.from(400,"/auth/v1/token?grant_type=refresh_token","{}").requiresSignIn)
        assertFalse(ServiceErrors.from(503,"/rest/v1/activities","{}").requiresSignIn)
    }
    @Test fun signOutUsesCurrentSessionScopeOnly() {
        val api = SupabaseGateway(BackendTransport { path,token,_ -> assertEquals("/auth/v1/logout?scope=local",path); assertEquals(session.accessToken,token); "" })
        api.signOut(session)
    }
}
