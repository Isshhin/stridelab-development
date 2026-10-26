package io.stridelab.privatealpha;
import org.junit.Test;
import static org.junit.Assert.*;
public class NativeSessionTest {
    @Test public void nativeSessionRoundTripRetainsRefreshMaterial() throws Exception {
        NativeSession original=new NativeSession("synthetic-user","synthetic-access","synthetic-refresh",1800000000L);
        NativeSession restored=NativeSession.decode(original.encode(),"synthetic-user");
        assertEquals(original.account,restored.account);assertEquals(original.accessToken,restored.accessToken);assertEquals(original.refreshToken,restored.refreshToken);assertEquals(original.expiresAtSeconds,restored.expiresAtSeconds);
    }
    @Test public void rejectsAnotherAccountAndUnsupportedSchema() throws Exception {
        NativeSession session=new NativeSession("synthetic-a","synthetic-access","synthetic-refresh",1800000000L);
        assertThrows(IllegalArgumentException.class,()->NativeSession.decode(session.encode(),"synthetic-b"));
        assertThrows(IllegalArgumentException.class,()->NativeSession.decode(session.encode().replace("\"schemaVersion\":1","\"schemaVersion\":2"),"synthetic-a"));
    }
    @Test public void rejectsIncompleteSessionWithoutExposingMaterialInErrors() {
        assertThrows(IllegalArgumentException.class,()->new NativeSession("synthetic",null,"refresh",1));
        assertThrows(IllegalArgumentException.class,()->new NativeSession("synthetic","access","",1));
        assertThrows(IllegalArgumentException.class,()->new NativeSession("synthetic","access","refresh",0));
    }
}
