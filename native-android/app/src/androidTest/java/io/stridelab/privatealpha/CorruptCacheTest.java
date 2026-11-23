package io.stridelab.privatealpha;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Corrupts only a dedicated synthetic test database, never the athlete database. */
@RunWith(AndroidJUnit4.class)
public class CorruptCacheTest {
    @Test public void damagedDatabaseIsNotSilentlyDeletedOrRecreated() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        String name="synthetic-corrupt-cache-test.db";
        context.deleteDatabase(name);
        File file=context.getDatabasePath(name);
        if(!file.getParentFile().isDirectory())assertTrue(file.getParentFile().mkdirs());
        byte[] snapshot="synthetic damaged database fixture; retain for recovery".getBytes(StandardCharsets.UTF_8);
        Files.write(file.toPath(),snapshot);
        try(TrainingStore store=new TrainingStore(context,name)) {
            try {store.getReadableDatabase();fail("Damaged database must not be replaced with an empty database");}
            catch(SQLiteException expected) { /* Fail closed; require explicit recovery. */ }
            assertTrue("Original damaged file must remain",file.isFile());
            assertArrayEquals("Original bytes must remain available for recovery",snapshot,Files.readAllBytes(file.toPath()));
        } finally {context.deleteDatabase(name);}
    }
}
