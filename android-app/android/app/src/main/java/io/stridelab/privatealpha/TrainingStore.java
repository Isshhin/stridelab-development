package io.stridelab.privatealpha;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Durable native records/outbox. Does not read, replace or migrate WebView afterglow2. */
public final class TrainingStore extends SQLiteOpenHelper {
    public static final String DATABASE = "stridelab-athlete-v1.db";
    private static final String[] RECORD_COLUMNS = {"record_id","payload","server_version","synced_at"};

    public static final class RemoteRecord {
        public final String id,payload,version;
        public RemoteRecord(String id,String payload,String version) {this.id=id;this.payload=payload;this.version=version;}
    }
    public static final class CachedRecord {
        public final String id,payload,serverVersion;
        public final long syncedAt;
        CachedRecord(Cursor c) {id=c.getString(0);payload=c.getString(1);serverVersion=c.getString(2);syncedAt=c.getLong(3);}
    }
    public static final class Mutation {
        public final String operationId,kind,recordId,payload,baseVersion;
        public final int attempts;
        Mutation(Cursor c) {operationId=c.getString(0);kind=c.getString(1);recordId=c.getString(2);payload=c.getString(3);baseVersion=c.getString(4);attempts=c.getInt(5);}
    }
    public static final class Conflict {
        public final String localPayload,remotePayload,remoteVersion;
        Conflict(Cursor c) {localPayload=c.getString(0);remotePayload=c.getString(1);remoteVersion=c.getString(2);}
    }

    public TrainingStore(Context context) {this(context,DATABASE);}
    public TrainingStore(Context context,String name) {
        super(context.getApplicationContext(),name,null,1, database -> {
            // Android's default handler deletes corrupt databases. Keep unsynced records
            // and raw files available for explicit recovery instead of silently resetting.
            throw new SQLiteDatabaseCorruptException("Local database is corrupt; files retained for recovery");
        });
        setWriteAheadLoggingEnabled(true);
    }
    @Override public void onConfigure(SQLiteDatabase db) {super.onConfigure(db);db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records(account TEXT NOT NULL,kind TEXT NOT NULL,record_id TEXT NOT NULL,payload TEXT NOT NULL,server_version TEXT,synced_at INTEGER NOT NULL,PRIMARY KEY(account,kind,record_id))");
        db.execSQL("CREATE TABLE outbox(seq INTEGER PRIMARY KEY AUTOINCREMENT,operation_id TEXT NOT NULL UNIQUE,account TEXT NOT NULL,kind TEXT NOT NULL,record_id TEXT NOT NULL,payload TEXT NOT NULL,base_version TEXT,parent_operation_id TEXT,state TEXT NOT NULL CHECK(state IN ('pending','conflict')),attempts INTEGER NOT NULL DEFAULT 0,last_error TEXT,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX outbox_account_record ON outbox(account,kind,record_id,seq)");
        db.execSQL("CREATE TABLE conflicts(account TEXT NOT NULL,kind TEXT NOT NULL,record_id TEXT NOT NULL,local_payload TEXT NOT NULL,remote_payload TEXT NOT NULL,remote_version TEXT,observed_at INTEGER NOT NULL,PRIMARY KEY(account,kind,record_id))");
        db.execSQL("CREATE TABLE cursors(account TEXT NOT NULL,scope TEXT NOT NULL,cursor TEXT NOT NULL,updated_at INTEGER NOT NULL,PRIMARY KEY(account,scope))");
        db.execSQL("CREATE TABLE resolutions(seq INTEGER PRIMARY KEY AUTOINCREMENT,account TEXT NOT NULL,kind TEXT NOT NULL,record_id TEXT NOT NULL,local_payload TEXT NOT NULL,remote_payload TEXT NOT NULL,remote_version TEXT,merged_payload TEXT NOT NULL,resolved_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        throw new IllegalStateException("A non-destructive database migration is required; existing records were retained");
    }
    private static String[] key(String account,String kind,String id) {return new String[]{account,kind,id};}
    private static void keyCheck(String account,String kind,String id) {RecordPolicy.identifier(account);RecordPolicy.kind(kind);RecordPolicy.identifier(id);}
    private static ContentValues recordValues(String account,String kind,String id,String payload,String version,long now) {
        ContentValues v=new ContentValues();v.put("account",account);v.put("kind",kind);v.put("record_id",id);v.put("payload",payload);v.put("server_version",version);v.put("synced_at",now);return v;
    }
    private static void replace(SQLiteDatabase db,String table,ContentValues values) {
        if(db.insertWithOnConflict(table,null,values,SQLiteDatabase.CONFLICT_REPLACE)<0)
            throw new IllegalStateException("Local database write did not complete");
    }
    private static CachedRecord find(SQLiteDatabase db,String account,String kind,String id) {
        try(Cursor c=db.query("records",RECORD_COLUMNS,"account=? AND kind=? AND record_id=?",key(account,kind,id),null,null,null)) {
            return c.moveToFirst()?new CachedRecord(c):null;
        }
    }

    /**
     * Read a saved recording without placing its entire JSON payload in one
     * Android CursorWindow. Only activity_detail is eligible. This is strictly
     * read-only; missing, oversized or changed records must never be replaced.
     */
    public String getActivityDetailPayloadChunked(String account, String id) {
        keyCheck(account,"activity_detail",id);
        SQLiteDatabase db=getReadableDatabase();
        final int chunkChars=32768;
        final long maxChars=16L*1024*1024;
        long chars;
        try(Cursor c=db.rawQuery(
                "SELECT length(payload) FROM records WHERE account=? AND kind='activity_detail' AND record_id=?",
                new String[]{account,id})) {
            if(!c.moveToFirst())return null;
            chars=c.getLong(0);
        }
        if(chars<=0||chars>maxChars)
            throw new IllegalStateException("Stored recording is outside the safe read limit; original data retained");
        StringBuilder result=new StringBuilder((int)chars);
        for(long start=1;start<=chars;start+=chunkChars) {
            try(Cursor c=db.rawQuery(
                    "SELECT substr(payload,?,?) FROM records WHERE account=? AND kind='activity_detail' AND record_id=?",
                    new String[]{Long.toString(start),Integer.toString(chunkChars),account,id})) {
                if(!c.moveToFirst()||c.isNull(0))
                    throw new IllegalStateException("Stored recording changed during read; original data retained");
                result.append(c.getString(0));
            }
        }
        return result.toString();
    }

    public CachedRecord get(String account,String kind,String id) {keyCheck(account,kind,id);return find(getReadableDatabase(),account,kind,id);}

    /** Permanently removes one local record along with any queued offline mutations or conflicts. */
    public void deleteRecord(String account,String kind,String id) {
        keyCheck(account,kind,id);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            String[] k=key(account,kind,id);
            db.delete("records","account=? AND kind=? AND record_id=?",k);
            db.delete("outbox","account=? AND kind=? AND record_id=?",k);
            db.delete("conflicts","account=? AND kind=? AND record_id=?",k);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public List<CachedRecord> list(String account,String kind,int limit) {
        RecordPolicy.identifier(account);RecordPolicy.kind(kind);limit(limit);
        List<CachedRecord> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("records",RECORD_COLUMNS,"account=? AND kind=?",new String[]{account,kind},null,null,"record_id",String.valueOf(limit))) {while(c.moveToNext())out.add(new CachedRecord(c));}
        return out;
    }
    private static void limit(int limit) {if(limit<1||limit>1000)throw new IllegalArgumentException("Page size must be 1–1000");}
    private static boolean hasPending(SQLiteDatabase db,String account,String kind,String id) {
        try(Cursor c=db.rawQuery("SELECT 1 FROM outbox WHERE account=? AND kind=? AND record_id=? LIMIT 1",key(account,kind,id))) {return c.moveToFirst();}
    }
    private static Conflict conflict(SQLiteDatabase db,String account,String kind,String id) {
        try(Cursor c=db.query("conflicts",new String[]{"local_payload","remote_payload","remote_version"},"account=? AND kind=? AND record_id=?",key(account,kind,id),null,null,null)) {return c.moveToFirst()?new Conflict(c):null;}
    }
    public Conflict getConflict(String account,String kind,String id) {keyCheck(account,kind,id);return conflict(getReadableDatabase(),account,kind,id);}

    /** One transaction commits both visible local edits and their replay record. */
    public void enqueue(String account,String kind,String id,String operationId,String payload,String expectedVersion,long now) {
        RecordPolicy.record(account,kind,id,payload);RecordPolicy.identifier(operationId);RecordPolicy.version(expectedVersion,false);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {enqueueInside(db,account,kind,id,operationId,payload,expectedVersion,now);db.setTransactionSuccessful();}
        finally {db.endTransaction();}
    }
    private static void enqueueInside(SQLiteDatabase db,String account,String kind,String id,String op,String payload,String version,long now) {
        try(Cursor c=db.query("outbox",new String[]{"account","kind","record_id","payload","base_version"},"operation_id=?",new String[]{op},null,null,null)) {
            if(c.moveToFirst()) {
                if(!account.equals(c.getString(0))||!kind.equals(c.getString(1))||!id.equals(c.getString(2))||!payload.equals(c.getString(3)))
                    throw new IllegalArgumentException("An operation identifier cannot be reused for a different edit");
                return;
            }
        }
        CachedRecord old=find(db,account,kind,id);
        if(old!=null&&!Objects.equals(old.serverVersion,version))throw new IllegalStateException("Local record version changed; review the current data before saving");
        if(old!=null&&old.syncedAt>0&&old.serverVersion==null)throw new IllegalStateException("This record has no server version and cannot be safely edited offline");
        String parent=null,state="pending";
        try(Cursor c=db.query("outbox",new String[]{"operation_id","state"},"account=? AND kind=? AND record_id=?",key(account,kind,id),null,null,"seq DESC","1")) {
            if(c.moveToFirst()) {parent=c.getString(0);state=c.getString(1);}
        }
        replace(db,"records",recordValues(account,kind,id,payload,version,old==null?0:old.syncedAt));
        ContentValues v=new ContentValues();v.put("operation_id",op);v.put("account",account);v.put("kind",kind);v.put("record_id",id);v.put("payload",payload);v.put("base_version",version);v.put("parent_operation_id",parent);v.put("state",state);v.put("created_at",now);
        db.insertOrThrow("outbox",null,v);
        ContentValues local=new ContentValues();local.put("local_payload",payload);
        db.update("conflicts",local,"account=? AND kind=? AND record_id=?",key(account,kind,id));
    }
    /** Validate the entire page before updating records or the durable cursor. */
    public void applyRemotePage(String account,String kind,List<RemoteRecord> rows,String cursor,long now) {
        RecordPolicy.identifier(account);RecordPolicy.kind(kind);
        if(rows==null||rows.size()>1000)throw new IllegalArgumentException("Remote page is missing or too large");
        if(cursor!=null&&cursor.length()>8192)throw new IllegalArgumentException("Remote cursor is too large");
        Set<String> ids=new HashSet<>();
        for(RemoteRecord row:rows) {if(row==null)throw new IllegalArgumentException("Null remote record");RecordPolicy.record(account,kind,row.id,row.payload);RecordPolicy.version(row.version,false);if(!ids.add(row.id))throw new IllegalArgumentException("Duplicate record in remote page");}
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(RemoteRecord row:rows) {
                CachedRecord old=find(db,account,kind,row.id);
                if(hasPending(db,account,kind,row.id)) {
                    // A matching version is the unchanged server base. Keep the user's local edit.
                    if(row.version==null||old==null||!Objects.equals(row.version,old.serverVersion)) {
                        ContentValues v=new ContentValues();v.put("account",account);v.put("kind",kind);v.put("record_id",row.id);v.put("local_payload",old==null?"{}":old.payload);v.put("remote_payload",row.payload);v.put("remote_version",row.version);v.put("observed_at",now);
                        replace(db,"conflicts",v);
                        ContentValues state=new ContentValues();state.put("state","conflict");db.update("outbox",state,"account=? AND kind=? AND record_id=?",key(account,kind,row.id));
                    }
                } else replace(db,"records",recordValues(account,kind,row.id,row.payload,row.version,now));
            }
            if(cursor!=null) {ContentValues v=new ContentValues();v.put("account",account);v.put("scope",kind);v.put("cursor",cursor);v.put("updated_at",now);replace(db,"cursors",v);}
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /** Restore plans and non-executable reviews together or leave the profile untouched. */
    public void restoreReviewedBackup(String account,List<RemoteRecord> plans,List<RemoteRecord> reviews,boolean syncPlans,long now) {
        RecordPolicy.identifier(account);
        if(plans==null||reviews==null||plans.size()>50||reviews.size()>20||plans.isEmpty()&&reviews.isEmpty())
            throw new IllegalArgumentException("Backup record set is missing or too large");
        Set<String> planIds=new HashSet<>(),reviewIds=new HashSet<>();
        for(RemoteRecord row:plans) {
            if(row==null)throw new IllegalArgumentException("Null plan in reviewed backup");
            RecordPolicy.record(account,"plan",row.id,row.payload);
            if(!planIds.add(row.id))throw new IllegalArgumentException("Duplicate plan in reviewed backup");
        }
        for(RemoteRecord row:reviews) {
            if(row==null)throw new IllegalArgumentException("Null research review in reviewed backup");
            RecordPolicy.record(account,"exercise",row.id,row.payload);
            if(!reviewIds.add(row.id))throw new IllegalArgumentException("Duplicate research review in reviewed backup");
        }
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(RemoteRecord row:plans) {
                if(find(db,account,"plan",row.id)!=null||hasPending(db,account,"plan",row.id))
                    throw new IllegalStateException("Plan identifier already exists");
            }
            for(RemoteRecord row:reviews) {
                if(find(db,account,"exercise",row.id)!=null||hasPending(db,account,"exercise",row.id))
                    throw new IllegalStateException("Research review identifier already exists");
            }
            for(RemoteRecord row:plans) {
                if(syncPlans)enqueueInside(db,account,"plan",row.id,java.util.UUID.randomUUID().toString(),row.payload,null,now);
                else replace(db,"records",recordValues(account,"plan",row.id,row.payload,null,now));
            }
            for(RemoteRecord row:reviews)
                replace(db,"records",recordValues(account,"exercise",row.id,row.payload,null,now));
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    public String cursor(String account,String kind) {
        RecordPolicy.identifier(account);RecordPolicy.kind(kind);
        try(Cursor c=getReadableDatabase().query("cursors",new String[]{"cursor"},"account=? AND scope=?",new String[]{account,kind},null,null,null)) {return c.moveToFirst()?c.getString(0):null;}
    }
    /** Only the head of each record's edit chain is eligible; unrelated records can sync independently. */
    public List<Mutation> ready(String account,int limit) {
        RecordPolicy.identifier(account);limit(limit);List<Mutation> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("outbox",new String[]{"operation_id","kind","record_id","payload","base_version","attempts"},"account=? AND state='pending' AND parent_operation_id IS NULL",new String[]{account},null,null,"seq",String.valueOf(limit))) {while(c.moveToNext())out.add(new Mutation(c));}
        return out;
    }
    /** Acknowledge only a replayable operation. Later offline edits remain visible and queued. */
    public boolean acknowledge(String account,String operationId,String serverPayload,String serverVersion,long now) {
        RecordPolicy.identifier(account);RecordPolicy.identifier(operationId);RecordPolicy.payload(serverPayload);RecordPolicy.version(serverVersion,true);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            String kind,id;
            try(Cursor c=db.query("outbox",new String[]{"kind","record_id","state","parent_operation_id"},"account=? AND operation_id=?",new String[]{account,operationId},null,null,null)) {
                if(!c.moveToFirst()) {db.setTransactionSuccessful();return false;}
                if(!"pending".equals(c.getString(2))||!c.isNull(3))throw new IllegalStateException("Only an unblocked head operation can be acknowledged");
                kind=c.getString(0);id=c.getString(1);
            }
            ContentValues next=new ContentValues();next.putNull("parent_operation_id");next.put("base_version",serverVersion);
            db.update("outbox",next,"account=? AND parent_operation_id=?",new String[]{account,operationId});
            db.delete("outbox","account=? AND operation_id=?",new String[]{account,operationId});
            CachedRecord local=find(db,account,kind,id);
            String visible=hasPending(db,account,kind,id)&&local!=null?local.payload:serverPayload;
            replace(db,"records",recordValues(account,kind,id,visible,serverVersion,now));
            db.setTransactionSuccessful();return true;
        } finally {db.endTransaction();}
    }
    public void recordRetry(String account,String operationId,String category) {
        RecordPolicy.identifier(account);RecordPolicy.identifier(operationId);
        if(!"network".equals(category)&&!"authentication".equals(category)&&!"server".equals(category))throw new IllegalArgumentException("Use a non-sensitive retry category");
        getWritableDatabase().execSQL("UPDATE outbox SET attempts=attempts+1,last_error=? WHERE account=? AND operation_id=?",new Object[]{category,account,operationId});
    }
    /** Explicitly reviewed merge; preserve both versions in a recovery audit before replacing the chain. */
    public void resolveConflict(String account,String kind,String id,String operationId,String mergedPayload,boolean approved,long now) {
        if(!approved)throw new IllegalStateException("Explicit user approval is required to resolve a conflict");
        RecordPolicy.record(account,kind,id,mergedPayload);RecordPolicy.identifier(operationId);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Conflict c=conflict(db,account,kind,id);
            if(c==null)throw new IllegalStateException("There is no conflict to resolve");
            RecordPolicy.version(c.remoteVersion,true);
            // Check globally unique operation IDs before deleting the old chain.
            try(Cursor op=db.rawQuery("SELECT 1 FROM outbox WHERE operation_id=?",new String[]{operationId})) {if(op.moveToFirst())throw new IllegalArgumentException("Choose a new operation identifier for the approved merge");}
            ContentValues audit=new ContentValues();audit.put("account",account);audit.put("kind",kind);audit.put("record_id",id);audit.put("local_payload",c.localPayload);audit.put("remote_payload",c.remotePayload);audit.put("remote_version",c.remoteVersion);audit.put("merged_payload",mergedPayload);audit.put("resolved_at",now);db.insertOrThrow("resolutions",null,audit);
            db.delete("outbox","account=? AND kind=? AND record_id=?",key(account,kind,id));
            db.delete("conflicts","account=? AND kind=? AND record_id=?",key(account,kind,id));
            replace(db,"records",recordValues(account,kind,id,c.localPayload,c.remoteVersion,now));
            enqueueInside(db,account,kind,id,operationId,mergedPayload,c.remoteVersion,now);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}
