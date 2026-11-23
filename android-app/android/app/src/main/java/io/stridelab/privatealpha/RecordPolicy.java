package io.stridelab.privatealpha;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Shared storage boundary; original provenance, units and JSON bytes are retained. */
public final class RecordPolicy {
    public static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;
    private static final Set<String> KINDS = new HashSet<>(Arrays.asList(
            "activity", "activity_detail", "wellness", "plan", "workout", "exercise"));
    private RecordPolicy() { }
    public static void identifier(String value) {
        if (value == null || value.isEmpty() || value.length() > 128 || value.trim().isEmpty())
            throw new IllegalArgumentException("A bounded account or record identifier is required");
        for (int i=0;i<value.length();i++) if (Character.isISOControl(value.charAt(i)))
            throw new IllegalArgumentException("Control characters are not allowed in identifiers");
    }
    public static void kind(String value) {
        if (!KINDS.contains(value)) throw new IllegalArgumentException("Unsupported record kind");
    }
    public static void payload(String value) {
        payload(value, MAX_PAYLOAD_BYTES);
    }
    static void payload(String value, int maxBytes) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > maxBytes)
            throw new IllegalArgumentException("Record payload is missing or too large");
        try {
            JSONTokener tokens = new JSONTokener(value);
            Object parsed = tokens.nextValue();
            if (!(parsed instanceof JSONObject) || tokens.nextClean() != 0)
                throw new IllegalArgumentException("A complete JSON object is required");
        } catch (JSONException e) { throw new IllegalArgumentException("Invalid record JSON", e); }
    }
    public static void version(String value, boolean required) {
        if (value == null && !required) return;
        if (value == null || value.isEmpty() || value.length()>512)
            throw new IllegalArgumentException("A server version is required for safe synchronization");
    }
    public static void record(String account,String kind,String id,String payload) {
        identifier(account);kind(kind);identifier(id);payload(payload);
    }
}
