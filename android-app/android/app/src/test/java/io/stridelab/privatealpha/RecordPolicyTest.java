package io.stridelab.privatealpha;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecordPolicyTest {
    @Test public void acceptsAllDisciplinesWithoutInventingMetrics() {
        for(String kind:new String[]{"activity","wellness","plan","workout","exercise"})
            RecordPolicy.record("athlete-a",kind,"id-1","{\"source\":\"fit\",\"distance_m\":1500,\"heart_rate\":null}");
    }
    @Test public void requiresAnExplicitAccountAndBoundedIdentifiers() {
        for(String value:new String[]{null,""," ","a\nb",new String(new char[129]).replace('\0','x')})
            assertThrows(IllegalArgumentException.class,()->RecordPolicy.identifier(value));
    }
    @Test public void rejectsUnknownKindsAndOversizedPayloads() {
        assertThrows(IllegalArgumentException.class,()->RecordPolicy.kind("credentials"));
        assertThrows(IllegalArgumentException.class,()->RecordPolicy.payload(new String(new char[RecordPolicy.MAX_PAYLOAD_BYTES+1]).replace('\0','x')));
    }
    @Test public void rejectsPartialJsonAndNonObjectPayloads() {
        for(String value:new String[]{null,"[]","null","{broken","{} trailing"})
            assertThrows(IllegalArgumentException.class,()->RecordPolicy.payload(value));
    }
    @Test public void updateAcknowledgementNeedsARealServerVersion() {
        RecordPolicy.version(null,false);
        assertThrows(IllegalArgumentException.class,()->RecordPolicy.version(null,true));
        assertThrows(IllegalArgumentException.class,()->RecordPolicy.version("",true));
        RecordPolicy.version("2026-10-26T06:00:00Z",true);
    }
}
