package io.stridelab.privatealpha;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Explicit, versioned backup of the prototype's single authoritative state key. */
public final class BackupCodec {
    public static final String FORMAT = "io.stridelab.privatealpha.backup";
    public static final int SCHEMA = 1;
    public static final String STORAGE_KEY = "afterglow2";
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private BackupCodec() { }

    public static JSONObject validatedState(String value) throws JSONException {
        if (value == null || value.isEmpty()) throw new JSONException("No saved StrideLab data to back up");
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new JSONException("Data too large");
        JSONObject state = new JSONObject(value);
        if (state.length() == 0) throw new JSONException("Saved data is empty");
        return state;
    }

    public static String encode(String localStorageJson) throws JSONException {
        validatedState(localStorageJson);
        JSONObject out = new JSONObject();
        out.put("format", FORMAT);
        out.put("schemaVersion", SCHEMA);
        out.put("createdAt", Instant.now().toString());
        out.put("appId", "io.stridelab.privatealpha");
        out.put("stateKey", STORAGE_KEY);
        out.put("stateJson", localStorageJson);
        out.put("stateSha256", sha256(localStorageJson));
        String result = out.toString(2);
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new JSONException("Backup too large");
        return result;
    }

    public static String decode(String fileJson) throws JSONException {
        if (fileJson == null || fileJson.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new JSONException("Backup too large");
        JSONObject back = new JSONObject(fileJson);
        if (!FORMAT.equals(back.optString("format"))) throw new JSONException("This is not a StrideLab Android backup");
        if (back.optInt("schemaVersion", -1) != SCHEMA) throw new JSONException("Unsupported backup version; no data changed");
        if (!"io.stridelab.privatealpha".equals(back.optString("appId"))) throw new JSONException("Wrong app identifier");
        if (!STORAGE_KEY.equals(back.optString("stateKey"))) throw new JSONException("Unexpected saved-state key");
        Object value = back.opt("stateJson");
        if (!(value instanceof String)) throw new JSONException("Missing exact state snapshot");
        String encodedState = (String) value;
        validatedState(encodedState);
        if (!sha256(encodedState).equals(back.optString("stateSha256"))) throw new JSONException("State checksum mismatch; no data changed");
        return encodedState;
    }

    private static String sha256(String value) throws JSONException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            char[] hex="0123456789abcdef".toCharArray();
            char[] out=new char[digest.length*2];
            for(int i=0;i<digest.length;i++){int b=digest[i]&255;out[2*i]=hex[b>>4];out[2*i+1]=hex[b&15];}
            return new String(out);
        } catch(NoSuchAlgorithmException e) { throw new JSONException("SHA-256 unavailable"); }
    }
}
