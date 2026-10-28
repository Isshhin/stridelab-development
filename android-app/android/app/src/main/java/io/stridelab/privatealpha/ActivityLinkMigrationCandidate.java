package io.stridelab.privatealpha;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Step 31 candidate only. Run on an ISOLATED COPY of a v1 athlete database.
 * Intentionally not called from TrainingStore.onUpgrade or any app startup path.
 * The legacy schema remains version 1 until exact-head migration acceptance.
 */
public final class ActivityLinkMigrationCandidate {
    private ActivityLinkMigrationCandidate() {}

    public static final class Audit {
        public final int plans;
        public final int links;
        Audit(int plans, int links) { this.plans=plans; this.links=links; }
    }

    /** Throws and rolls back if any plan cannot be safely interpreted. */
    public static Audit migrateIsolatedCopy(SQLiteDatabase database) {
        if(database.inTransaction()) throw new IllegalStateException("The migration owns its isolated copy transaction");
        database.beginTransaction();
        try {
            database.execSQL("CREATE TABLE activity_links ("+
                "account TEXT NOT NULL,"+
                "activity_id TEXT NOT NULL,"+
                "plan_id TEXT NOT NULL,"+
                "session_id TEXT NOT NULL,"+
                "PRIMARY KEY(account,activity_id),"+
                "UNIQUE(account,plan_id,session_id))");
            database.execSQL("CREATE INDEX activity_links_plan ON activity_links(account,plan_id,session_id)");
            int plans=0, links=0;
            try(Cursor rows=database.rawQuery(
                "SELECT account,record_id,payload FROM records WHERE kind='plan' ORDER BY account,record_id",null)) {
                while(rows.moveToNext()) {
                    String account=rows.getString(0), planId=rows.getString(1), payload=rows.getString(2);
                    plans++;
                    links+=indexPlan(database,account,planId,payload);
                }
            }
            try(Cursor row=database.rawQuery("SELECT count(*) FROM activity_links",null)) {
                if(!row.moveToFirst()||row.getInt(0)!=links)
                    throw new IllegalStateException("Link index does not match source plans");
            }
            database.setTransactionSuccessful();
            return new Audit(plans,links);
        } finally {
            database.endTransaction();
        }
    }

    private static int indexPlan(SQLiteDatabase db,String account,String planId,String payload) {
        RecordPolicy.identifier(account); RecordPolicy.identifier(planId); RecordPolicy.payload(payload);
        try {
            JSONObject plan=new JSONObject(payload);
            if(plan.getInt("schemaVersion")!=1 ||
                !account.equals(plan.getString("user_id")) ||
                !planId.equals(plan.getString("id")))
                throw new IllegalStateException("Unsupported or owner-mismatched plan; original retained");
            JSONArray sessions=plan.getJSONArray("sessions");
            if(sessions.length()>400) throw new IllegalStateException("Oversized plan; original retained");
            Set<String> seenSessions=new HashSet<>(), seenActivityIds=new HashSet<>();
            int inserted=0;
            for(int i=0;i<sessions.length();i++) {
                JSONObject session=sessions.getJSONObject(i);
                String sessionId=session.getString("id");
                RecordPolicy.identifier(sessionId);
                if(!seenSessions.add(sessionId)) throw new IllegalStateException("Duplicate session ID");
                if(session.isNull("linkedActivityId")||!session.has("linkedActivityId")) continue;
                String activityId=session.getString("linkedActivityId");
                RecordPolicy.identifier(activityId);
                if(!seenActivityIds.add(activityId)) throw new IllegalStateException("Duplicate link within plan");
                if(!"run".equals(session.getString("discipline")))
                    throw new IllegalStateException("Non-running session linked to recorded run");
                ContentValues value=new ContentValues();
                value.put("account",account);
                value.put("activity_id",activityId);
                value.put("plan_id",planId);
                value.put("session_id",sessionId);
                db.insertOrThrow("activity_links",null,value);
                inserted++;
            }
            return inserted;
        } catch(JSONException invalid) {
            throw new IllegalStateException("Unrecognized stored plan; original retained for review",invalid);
        }
    }
}
