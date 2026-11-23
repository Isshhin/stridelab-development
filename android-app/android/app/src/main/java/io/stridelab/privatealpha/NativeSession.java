package io.stridelab.privatealpha;

import org.json.JSONException;
import org.json.JSONObject;

/** Native-only session material. Never send this object through a WebView bridge or logs. */
public final class NativeSession {
    public final String account;
    final String accessToken,refreshToken;
    public final long expiresAtSeconds;
    public NativeSession(String account,String accessToken,String refreshToken,long expiresAtSeconds) {
        RecordPolicy.identifier(account);
        if(accessToken==null||accessToken.isEmpty()||accessToken.length()>16384||refreshToken==null||refreshToken.isEmpty()||refreshToken.length()>16384||expiresAtSeconds<=0)
            throw new IllegalArgumentException("A complete bounded native session is required");
        this.account=account;this.accessToken=accessToken;this.refreshToken=refreshToken;this.expiresAtSeconds=expiresAtSeconds;
    }
    String encode() throws JSONException {
        JSONObject value=new JSONObject();value.put("schemaVersion",1);value.put("account",account);value.put("accessToken",accessToken);value.put("refreshToken",refreshToken);value.put("expiresAtSeconds",expiresAtSeconds);return value.toString();
    }
    static NativeSession decode(String value,String expectedAccount) throws JSONException {
        RecordPolicy.payload(value);JSONObject parsed=new JSONObject(value);
        if(parsed.optInt("schemaVersion",-1)!=1||!expectedAccount.equals(parsed.optString("account")))throw new IllegalArgumentException("Saved session account or version does not match");
        return new NativeSession(parsed.getString("account"),parsed.getString("accessToken"),parsed.getString("refreshToken"),parsed.getLong("expiresAtSeconds"));
    }
}
