package io.stridelab.privatealpha;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackupCodecTest {
    @Test public void roundTripPreservesSessionsImportsAndLinks() throws Exception {
        String state = "{\"log\":{\"2026-10-26\":{\"type\":\"run\"}},\"importedRuns\":[{\"id\":\"a\"}],\"runLinks\":{\"a\":\"s1\"}}";
        String backup = BackupCodec.encode(state);
        assertEquals(state, BackupCodec.decode(backup));
        JSONObject restored = new JSONObject(BackupCodec.decode(backup));
        assertEquals("run", restored.getJSONObject("log").getJSONObject("2026-10-26").getString("type"));
        assertEquals("a", restored.getJSONArray("importedRuns").getJSONObject(0).getString("id"));
        assertEquals("s1",restored.getJSONObject("runLinks").getString("a"));
        assertEquals(BackupCodec.SCHEMA,new JSONObject(backup).getInt("schemaVersion"));
    }
    @Test public void rejectWrongAppAndSchema() throws Exception {
        JSONObject good = new JSONObject(BackupCodec.encode("{\"sel\":\"2026-10-26\"}"));
        good.put("appId","other");
        assertRejected(good.toString());
        good.put("appId","io.stridelab.privatealpha").put("schemaVersion",BackupCodec.SCHEMA+1);
        assertRejected(good.toString());
    }
    @Test public void rejectForeignAndBlankState() throws Exception {
        assertRejected("{\"format\":\"fake\",\"schemaVersion\":1,\"state\":{\"a\":1}}");
        try{BackupCodec.encode("[]");fail("Expected rejection");}catch(JSONException ok){}
        try{BackupCodec.encode("{}");fail("Expected rejection");}catch(JSONException ok){}
    }
    @Test public void rejectTamperedPayload() throws Exception {
        JSONObject back = new JSONObject(BackupCodec.encode("{\"log\":{\"ok\":true}}"));
        back.put("stateJson", "{\"log\":{\"ok\":false}}");
        assertRejected(back.toString());
    }
    @Test public void rejectOverlyLargePayload() throws Exception {
        StringBuilder b=new StringBuilder("{\"v\":\"");
        for(int i=0;i<BackupCodec.MAX_BYTES;i++) b.append('a');
        b.append("\"}");
        try{BackupCodec.encode(b.toString());fail("Expected size cap");}catch(JSONException ok){}
    }
    private static void assertRejected(String s){try{BackupCodec.decode(s);fail("Expected backup rejection");}catch(JSONException ok){}}
}
