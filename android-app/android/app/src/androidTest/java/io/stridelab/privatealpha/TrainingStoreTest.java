package io.stridelab.privatealpha;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TrainingStoreTest {
    private static final String DB="stridelab-synthetic-store-tests.db";
    private static final String A="synthetic-athlete-a",B="synthetic-athlete-b";
    private Context context;
    private TrainingStore store;
    @Before public void setup() {context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase(DB);store=new TrainingStore(context,DB);}
    @After public void cleanup() {store.close();context.deleteDatabase(DB);}
    private void base(String kind) {store.applyRemotePage(A,kind,Collections.singletonList(new TrainingStore.RemoteRecord("r1","{\"value\":1}","v1")),"cursor-1",10);}
    private void reopen() {store.close();store=new TrainingStore(context,DB);}

    @Test public void rawMetricsAndCursorSurviveDatabaseCloseAndReopen() {
        String raw="{\"source\":\"intervals\",\"distance_m\":1234.5,\"heart_rate\":null,\"sleep_duration\":null}";
        store.applyRemotePage(A,"activity",Collections.singletonList(new TrainingStore.RemoteRecord("recorded",raw,"v1")),"page-1",10);reopen();
        assertEquals(raw,store.get(A,"activity","recorded").payload);
        assertEquals("page-1",store.cursor(A,"activity"));
        assertTrue(store.list(B,"activity",10).isEmpty());
    }
    @Test public void offlineEditAndReplayIntentSurviveRestartAtomically() {
        base("workout");store.enqueue(A,"workout","r1","op1","{\"value\":2}","v1",11);reopen();
        assertEquals("{\"value\":2}",store.get(A,"workout","r1").payload);
        assertEquals("op1",store.ready(A,10).get(0).operationId);
        assertTrue(store.ready(B,10).isEmpty());
    }
    @Test public void repeatedOperationIsIdempotentAndCannotBeReusedForAnotherEdit() {
        base("workout");store.enqueue(A,"workout","r1","op1","{\"value\":2}","v1",11);
        store.enqueue(A,"workout","r1","op1","{\"value\":2}","v1",12);
        assertEquals(1,store.ready(A,10).size());
        assertThrows(IllegalArgumentException.class,()->store.enqueue(A,"workout","r1","op1","{\"value\":3}","v1",13));
        assertEquals("{\"value\":2}",store.get(A,"workout","r1").payload);
    }
    @Test public void acknowledgingOneEditRetainsLaterOfflineEditsAndAdvancesTheirBase() {
        base("workout");store.enqueue(A,"workout","r1","op1","{\"value\":2}","v1",11);store.enqueue(A,"workout","r1","op2","{\"value\":3}","v1",12);
        assertEquals(1,store.ready(A,10).size());
        assertTrue(store.acknowledge(A,"op1","{\"value\":2}","v2",13));
        assertEquals("{\"value\":3}",store.get(A,"workout","r1").payload);
        assertEquals("op2",store.ready(A,10).get(0).operationId);assertEquals("v2",store.ready(A,10).get(0).baseVersion);
        // Retrying the original local enqueue is still idempotent after its effective base advanced.
        store.enqueue(A,"workout","r1","op2","{\"value\":3}","v1",14);
        assertTrue(store.acknowledge(A,"op2","{\"value\":3}","v3",15));
        assertTrue(store.ready(A,10).isEmpty());assertEquals("v3",store.get(A,"workout","r1").serverVersion);
        assertFalse(store.acknowledge(A,"op2","{\"value\":3}","v3",16));
    }
    @Test public void conflictingRemoteChangePreservesBothVersionsAndRequiresApproval() {
        base("plan");store.enqueue(A,"plan","r1","op1","{\"value\":2}","v1",11);
        store.applyRemotePage(A,"plan",Collections.singletonList(new TrainingStore.RemoteRecord("r1","{\"value\":9}","v2")),"page-2",12);reopen();
        assertEquals("{\"value\":2}",store.get(A,"plan","r1").payload);
        assertEquals("{\"value\":9}",store.getConflict(A,"plan","r1").remotePayload);
        assertTrue(store.ready(A,10).isEmpty());
        assertThrows(IllegalStateException.class,()->store.resolveConflict(A,"plan","r1","merged","{\"value\":7}",false,13));
        store.resolveConflict(A,"plan","r1","merged","{\"value\":7}",true,14);
        assertNull(store.getConflict(A,"plan","r1"));assertEquals("v2",store.ready(A,10).get(0).baseVersion);
        assertEquals("{\"value\":7}",store.get(A,"plan","r1").payload);
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT local_payload,remote_payload FROM resolutions",null)) {assertTrue(c.moveToFirst());assertEquals("{\"value\":2}",c.getString(0));assertEquals("{\"value\":9}",c.getString(1));}
    }
    @Test public void unchangedServerBaseNeverOverwritesLocalWork() {
        base("workout");store.enqueue(A,"workout","r1","op1","{\"value\":2}","v1",11);base("workout");
        assertEquals("{\"value\":2}",store.get(A,"workout","r1").payload);assertNull(store.getConflict(A,"workout","r1"));assertEquals(1,store.ready(A,10).size());
    }
    @Test public void malformedPageCannotPartiallyWriteOrAdvanceCursor() {
        base("activity");
        assertThrows(IllegalArgumentException.class,()->store.applyRemotePage(A,"activity",Arrays.asList(new TrainingStore.RemoteRecord("r1","{\"value\":2}","v2"),new TrainingStore.RemoteRecord("bad","[]","v2")),"bad-cursor",12));
        assertEquals("{\"value\":1}",store.get(A,"activity","r1").payload);assertEquals("cursor-1",store.cursor(A,"activity"));
    }
    @Test public void databaseFailureRollsBackVisibleEditAndOutboxTogether() {
        base("workout");
        store.getWritableDatabase().execSQL("CREATE TRIGGER fail_synthetic_outbox BEFORE INSERT ON outbox BEGIN SELECT RAISE(ABORT,'synthetic write failure'); END");
        assertThrows(android.database.sqlite.SQLiteException.class,()->store.enqueue(A,"workout","r1","op1","{\"value\":2}","v1",11));
        assertEquals("{\"value\":1}",store.get(A,"workout","r1").payload);assertTrue(store.ready(A,10).isEmpty());
    }
    @Test public void anotherAccountCannotAcknowledgeOrInspectTheFirstAccountsQueue() {
        base("workout");store.enqueue(A,"workout","r1","op1","{\"value\":2}","v1",11);
        assertFalse(store.acknowledge(B,"op1","{\"value\":99}","v9",12));assertNull(store.get(B,"workout","r1"));assertNull(store.cursor(B,"workout"));assertEquals(1,store.ready(A,10).size());
    }
    @Test public void missingServerVersionBlocksUnsafeOfflineEdits() {
        store.applyRemotePage(A,"workout",Collections.singletonList(new TrainingStore.RemoteRecord("r1","{}",null)),"page",10);
        assertThrows(IllegalStateException.class,()->store.enqueue(A,"workout","r1","op1","{\"value\":2}",null,11));
        assertEquals("{}",store.get(A,"workout","r1").payload);
    }
}
