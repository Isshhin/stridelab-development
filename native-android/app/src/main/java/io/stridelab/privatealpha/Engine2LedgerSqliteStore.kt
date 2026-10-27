package io.stridelab.privatealpha

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper

/**
 * Isolated Engine 2.0 **candidate** database. It never opens or upgrades
 * TrainingStore.DATABASE ("stridelab-athlete-v1.db") and never deletes a corrupt file.
 *
 * Not wired to UI or a sync outbox. Validate upgrade, process-death and restore
 * before using any of these records as authoritative app state.
 */
internal class Engine2LedgerSqliteStore(
    context: Context,
    databaseName: String = DATABASE
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, VERSION,
    DatabaseErrorHandler {
        throw SQLiteDatabaseCorruptException(
            "Engine 2.0 ledger is corrupt; original file retained for recovery")
    }), Engine2LedgerCommitPort {

    companion object {
        const val DATABASE = "stridelab-engine2-proposals-v1.db"
        const val VERSION = 1
        private const val TABLE = "proposal_ledgers"
    }

    init {
        require(databaseName.isNotBlank())
        require(databaseName != TrainingStore.DATABASE) {
            "Engine 2.0 may not repurpose or auto-upgrade the existing athlete store"
        }
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE proposal_ledgers(
            account TEXT NOT NULL,
            plan_id TEXT NOT NULL,
            ledger_revision INTEGER NOT NULL CHECK(ledger_revision > 0),
            plan_revision INTEGER NOT NULL CHECK(plan_revision > 0),
            schema_version INTEGER NOT NULL CHECK(schema_version = 1),
            payload TEXT NOT NULL,
            PRIMARY KEY(account, plan_id)
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException(
            "Unreviewed Engine 2.0 upgrade denied. Preserve the original ledger database.")
    }

    private fun key(account: String, planId: String) {
        require(account.isNotBlank() && account.length <= 256)
        require(planId.isNotBlank() && planId.length <= 256)
    }

    fun seedIfAbsent(ledger: Engine2ApprovalLedger): Boolean {
        val p = ledger.plan
        key(p.ownerId, p.planId)
        val values = ContentValues().apply {
            put("account", p.ownerId)
            put("plan_id", p.planId)
            put("ledger_revision", ledger.ledgerRevision)
            put("plan_revision", p.revision)
            put("schema_version", Engine2LedgerCodec.SCHEMA)
            put("payload", Engine2LedgerCodec.encode(ledger))
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val inserted = db.insertWithOnConflict(
                TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE) >= 0L
            db.setTransactionSuccessful()
            return inserted
        } finally {
            db.endTransaction()
        }
    }

    fun read(accountId: String, planId: String): Engine2ApprovalLedger? {
        key(accountId, planId)
        return readInside(readableDatabase, accountId, planId)
    }

    private fun readInside(db: SQLiteDatabase, accountId: String,
                           planId: String): Engine2ApprovalLedger? {
        db.query(TABLE, arrayOf("payload", "ledger_revision", "plan_revision", "schema_version"),
            "account=? AND plan_id=?", arrayOf(accountId, planId),
            null, null, null).use { cursor ->
            if (!cursor.moveToFirst()) return null
            require(cursor.getInt(3) == Engine2LedgerCodec.SCHEMA)
            val snapshot = Engine2LedgerCodec.decode(cursor.getString(0))
            require(snapshot.plan.ownerId == accountId && snapshot.plan.planId == planId)
            require(snapshot.ledgerRevision == cursor.getLong(1) &&
                snapshot.plan.revision == cursor.getLong(2)) {
                "Database header does not match stored ledger; preserve raw bytes."
            }
            return snapshot
        }
    }

    /** Atomically stage a pending proposal without modifying any approved workouts. */
    fun stageIfRevision(
        accountId: String, planId: String, expectedLedgerRevision: Long,
        proposal: Engine2DurationProposal
    ): Boolean {
        key(accountId, planId)
        require(proposal.ownerId == accountId && proposal.planId == planId)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val previous = readInside(db, accountId, planId) ?: return false
            if (previous.ledgerRevision != expectedLedgerRevision) return false
            require(proposal.id !in previous.proposals) {
                "Existing proposal IDs may not be silently replaced"
            }
            val staged = Engine2ApprovalLedgerReducer.stage(previous, proposal)
            val values = ContentValues().apply {
                put("ledger_revision", staged.ledgerRevision)
                put("payload", Engine2LedgerCodec.encode(staged))
            }
            val affected = db.update(TABLE, values,
                "account=? AND plan_id=? AND ledger_revision=?",
                arrayOf(accountId, planId, expectedLedgerRevision.toString()))
            require(affected in 0..1)
            if (affected != 1) return false
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Atomic compare-and-swap of all proposal/receipt/plan changes. Revision
     * expectations and persisted source are checked in the SAME transaction.
     */
    override fun commitIfRevision(
        accountId: String,
        planId: String,
        expectedLedgerRevision: Long,
        expectedPlanRevision: Long,
        updated: Engine2ApprovalLedger
    ): Boolean {
        key(accountId, planId)
        require(updated.plan.ownerId == accountId && updated.plan.planId == planId)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val previous = readInside(db, accountId, planId) ?: return false
            if (previous.ledgerRevision != expectedLedgerRevision ||
                previous.plan.revision != expectedPlanRevision) return false
            require(updated.ledgerRevision == previous.ledgerRevision + 1)
            require(updated.decisions.size == previous.decisions.size + 1)
            require(updated.decisions.dropLast(1) == previous.decisions)
            val receipt = updated.decisions.last()
            require(Engine2ApprovalLedgerReducer.decide(
                previous, receipt.proposalId, receipt.decision, receipt.at
            ) == updated) { "Attempted write diverges from verified decision reducer" }
            val values = ContentValues().apply {
                put("ledger_revision", updated.ledgerRevision)
                put("plan_revision", updated.plan.revision)
                put("payload", Engine2LedgerCodec.encode(updated))
            }
            val affected = db.update(TABLE, values,
                "account=? AND plan_id=? AND ledger_revision=? AND plan_revision=?",
                arrayOf(accountId, planId, expectedLedgerRevision.toString(),
                    expectedPlanRevision.toString()))
            require(affected in 0..1)
            if (affected != 1) return false
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }
}
