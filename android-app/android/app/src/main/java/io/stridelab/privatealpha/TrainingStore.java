package io.stridelab.privatealpha;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Durable native records/outbox. Does not read, replace or migrate WebView afterglow2. */
public final class TrainingStore extends SQLiteOpenHelper {
    public static final String DATABASE = "stridelab-athlete-v1.db";
    private static final String[] RECORD_COLUMNS = {"record_id","server_version","synced_at"};

    public static final class RemoteRecord {
        public final String id,payload,version;
        public RemoteRecord(String id,String payload,String version) {this.id=id;this.payload=payload;this.version=version;}
    }
    public static final class CachedRecord {
        public final String id,payload,serverVersion;
        public final long syncedAt;
        CachedRecord(Cursor c,String payload) {id=c.getString(0);this.payload=payload;serverVersion=c.getString(1);syncedAt=c.getLong(2);}
    }
    public static final class Mutation {
        public final String operationId,kind,recordId,payload,baseVersion;
        public final int attempts;
        Mutation(Cursor c,String payload) {operationId=c.getString(0);kind=c.getString(1);recordId=c.getString(2);this.payload=payload;baseVersion=c.getString(3);attempts=c.getInt(4);}
    }
    public static final class Conflict {
        public final String localPayload,remotePayload,remoteVersion;
        Conflict(String local,String remote,String version) {localPayload=local;remotePayload=remote;remoteVersion=version;}
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
    // Read bytes in bounded windows; a transaction keeps every chunk on one snapshot.
    private static String payload(SQLiteDatabase db,String table,String column,String where,String[] args) {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        int offset=1;
        while(true) {
            String[] params=new String[args.length+1];
            params[0]=String.valueOf(offset);System.arraycopy(args,0,params,1,args.length);
            try(Cursor c=db.rawQuery("SELECT substr(CAST("+column+" AS BLOB),?,65536) FROM "+table+" WHERE "+where,params)) {
                if(!c.moveToFirst()||c.isNull(0))throw new android.database.sqlite.SQLiteException("Local record disappeared while reading");
                byte[] part=c.getBlob(0);
                bytes.write(part,0,part.length);
                if(part.length<65536)break;
                offset+=part.length;
            }
        }
        try {return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString();}
        catch(CharacterCodingException e) {throw new android.database.sqlite.SQLiteException("Local record has invalid UTF-8",e); }
    }
    private static CachedRecord find(SQLiteDatabase db,String account,String kind,String id) {
        String where="account=? AND kind=? AND record_id=?";
        String[] args=key(account,kind,id);
        try(Cursor c=db.query("records",RECORD_COLUMNS,where,args,null,null,null)) {
            return c.moveToFirst()?new CachedRecord(c,payload(db,"records","payload",where,args)):null;
        }
    }
    public CachedRecord get(String account,String kind,String id) {
        keyCheck(account,kind,id);SQLiteDatabase db=getReadableDatabase();
        db.beginTransactionNonExclusive();
        try {CachedRecord row=find(db,account,kind,id);db.setTransactionSuccessful();return row;}
        finally {db.endTransaction();}
    }
    public List<CachedRecord> list(String account,String kind,int limit) {
        RecordPolicy.identifier(account);RecordPolicy.kind(kind);limit(limit);
        SQLiteDatabase db=getReadableDatabase();db.beginTransactionNonExclusive();
        try {
            List<CachedRecord> out=new ArrayList<>();
            try(Cursor c=db.query("records",RECORD_COLUMNS,"account=? AND kind=?",new String[]{account,kind},null,null,"record_id",String.valueOf(limit))) {
                while(c.moveToNext())out.add(new CachedRecord(c,payload(db,"records","payload","account=? AND kind=? AND record_id=?",key(account,kind,c.getString(0)))));
            }
            db.setTransactionSuccessful();return out;
        } finally {db.endTransaction();}
    }
    private static void limit(int limit) {if(limit<1||limit>1000)throw new IllegalArgumentException("Page size must be 1–1000");}
    private static boolean hasPending(SQLiteDatabase db,String account,String kind,String id) {
        try(Cursor c=db.rawQuery("SELECT 1 FROM outbox WHERE account=? AND kind=? AND record_id=? LIMIT 1",key(account,kind,id))) {return c.moveToFirst();}
    }
    private static Conflict conflict(SQLiteDatabase db,String account,String kind,String id) {
        String where="account=? AND kind=? AND record_id=?";String[] args=key(account,kind,id);
        try(Cursor c=db.query("conflicts",new String[]{"remote_version"},where,args,null,null,null)) {
            return c.moveToFirst()?new Conflict(payload(db,"conflicts","local_payload",where,args),payload(db,"conflicts","remote_payload",where,args),c.getString(0)):null;
        }
    }
    public Conflict getConflict(String account,String kind,String id) {
        keyCheck(account,kind,id);SQLiteDatabase db=getReadableDatabase();db.beginTransactionNonExclusive();
        try {Conflict result=conflict(db,account,kind,id);db.setTransactionSuccessful();return result;}
        finally {db.endTransaction();}
    }

    /** One transaction commits both visible local edits and their replay record. */
    public void enqueue(String account,String kind,String id,String operationId,String payload,String expectedVersion,long now) {
        RecordPolicy.record(account,kind,id,payload);RecordPolicy.identifier(operationId);RecordPolicy.version(expectedVersion,false);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {enqueueInside(db,account,kind,id,operationId,payload,expectedVersion,now);db.setTransactionSuccessful();}
        finally {db.endTransaction();}
    }
    private static void enqueueInside(SQLiteDatabase db,String account,String kind,String id,String op,String payload,String version,long now) {
        try(Cursor c=db.query("outbox",new String[]{"account","kind","record_id"},"operation_id=?",new String[]{op},null,null,null)) {
            if(c.moveToFirst()) {
                if(!account.equals(c.getString(0))||!kind.equals(c.getString(1))||!id.equals(c.getString(2))||!payload.equals(payload(db,"outbox","payload","operation_id=?",new String[]{op})))
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
    public String cursor(String account,String kind) {
        RecordPolicy.identifier(account);RecordPolicy.kind(kind);
        try(Cursor c=getReadableDatabase().query("cursors",new String[]{"cursor"},"account=? AND scope=?",new String[]{account,kind},null,null,null)) {return c.moveToFirst()?c.getString(0):null;}
    }
    /** Only the head of each record's edit chain is eligible; unrelated records can sync independently. */
    public List<Mutation> ready(String account,int limit) {
        RecordPolicy.identifier(account);limit(limit);List<Mutation> out=new ArrayList<>();
        SQLiteDatabase db=getReadableDatabase();db.beginTransactionNonExclusive();
        try {
            try(Cursor c=db.query("outbox",new String[]{"operation_id","kind","record_id","base_version","attempts"},"account=? AND state='pending' AND parent_operation_id IS NULL",new String[]{account},null,null,"seq",String.valueOf(limit))) {
                while(c.moveToNext())out.add(new Mutation(c,payload(db,"outbox","payload","account=? AND operation_id=?",new String[]{account,c.getString(0)})));
            }
            db.setTransactionSuccessful();return out;
        } finally {db.endTransaction();}
    }
    /** Acknowledge only a replayable operation. Later offline edits remain visible and queued. */
    public boolean acknowledge(String account,String operationId,String serverPayload,String serverVersion,long now) {
        RecordPolicy.identifier(account);RecordPolicy.identifier(operationId);RecordPolicy.version(serverVersion,true);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            String kind,id;
            try(Cursor c=db.query("outbox",new String[]{"kind","record_id","state","parent_operation_id"},"account=? AND operation_id=?",new String[]{account,operationId},null,null,null)) {
                if(!c.moveToFirst()) {db.setTransactionSuccessful();return false;}
                if(!"pending".equals(c.getString(2))||!c.isNull(3))throw new IllegalStateException("Only an unblocked head operation can be acknowledged");
                kind=c.getString(0);id=c.getString(1);
            }
            // Use the kind-specific storage policy for full FIT details too.
            RecordPolicy.record(account,kind,id,serverPayload);
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
