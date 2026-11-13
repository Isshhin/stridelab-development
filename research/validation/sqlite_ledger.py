"""SQLite research-only revision store, not StrideLab Android persistence.

Uses Python standard-library sqlite3 and disposable CI databases. Each proposal,
decision, and rollback is an explicit atomic transaction. Optimistic plan
versions, unique proposal IDs and immutable activity logs prevent replay errors.
Crash injection callbacks are used ONLY for testing; never production use.
"""
from __future__ import annotations

from contextlib import contextmanager
from copy import deepcopy
import json
import sqlite3
from pathlib import Path
from typing import Any, Callable, Iterator


class Conflict(ValueError):
    """Stale version, duplicate change, or a forbidden transition."""


def encode(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)


def decode(value: str) -> Any:
    return json.loads(value)


class ResearchPlanStore:
    def __init__(self, path: str | Path):
        self.connection = sqlite3.connect(str(path), timeout=5, isolation_level=None)
        self.connection.execute("PRAGMA foreign_keys=ON")
        self.connection.execute("PRAGMA journal_mode=WAL")
        self.connection.execute("PRAGMA busy_timeout=5000")
        self.connection.executescript("""
            CREATE TABLE IF NOT EXISTS current_plan (
                singleton INTEGER PRIMARY KEY CHECK(singleton = 1),
                version INTEGER NOT NULL CHECK(version > 0),
                data TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS activities (
                id TEXT PRIMARY KEY,
                data TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS proposals (
                id TEXT PRIMARY KEY,
                base_version INTEGER NOT NULL,
                before_data TEXT NOT NULL,
                candidate_data TEXT NOT NULL,
                reason TEXT NOT NULL,
                policy_id TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('pending','approved','rejected')),
                resolved_version INTEGER
            );
            CREATE TABLE IF NOT EXISTS audit (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                proposal_id TEXT NOT NULL REFERENCES proposals(id),
                action TEXT NOT NULL CHECK(action IN ('approved','rejected','rollback')),
                plan_version INTEGER NOT NULL,
                before_data TEXT NOT NULL,
                after_data TEXT NOT NULL
            );
        """)

    def close(self) -> None:
        self.connection.close()

    @contextmanager
    def transaction(self) -> Iterator[sqlite3.Connection]:
        self.connection.execute("BEGIN IMMEDIATE")
        try:
            yield self.connection
            self.connection.commit()
        except BaseException:
            self.connection.rollback()
            raise

    def initialize(self, plan: dict[str, Any]) -> None:
        with self.transaction() as db:
            old = db.execute("SELECT version, data FROM current_plan WHERE singleton=1").fetchone()
            if old is None:
                db.execute("INSERT INTO current_plan VALUES(1,1,?)", (encode(plan),))
            elif decode(old[1]) != plan:
                raise Conflict("Cannot overwrite existing plan through initialization")

    def _current(self, db: sqlite3.Connection) -> tuple[int, dict[str, Any]]:
        row = db.execute(
            "SELECT version, data FROM current_plan WHERE singleton=1"
        ).fetchone()
        if row is None:
            raise Conflict("Plan must be initialized first")
        return int(row[0]), decode(row[1])

    def current(self) -> tuple[int, dict[str, Any]]:
        return self._current(self.connection)

    def _expect(self, db: sqlite3.Connection, version: int) -> dict[str, Any]:
        actual, plan = self._current(db)
        if actual != version:
            raise Conflict(f"Expected plan version {version}, got {actual}")
        return plan

    def record_activity(self, activity_id: str, data: dict[str, Any]) -> None:
        """Activities are append-only; same ID + identical data is idempotent."""
        if not activity_id:
            raise ValueError("Activity ID required")
        payload = encode(data)
        with self.transaction() as db:
            old = db.execute("SELECT data FROM activities WHERE id=?", (activity_id,)).fetchone()
            if old is None:
                db.execute("INSERT INTO activities VALUES(?,?)", (activity_id, payload))
            elif old[0] != payload:
                raise Conflict("Recorded activity cannot be overwritten")

    def activity_snapshot(self) -> list[tuple[str, dict[str, Any]]]:
        return [(row[0], decode(row[1])) for row in self.connection.execute(
            "SELECT id,data FROM activities ORDER BY id")]

    def propose(self, proposal_id: str, candidate: dict[str, Any],
                reason: str, policy_id: str, *, expected_version: int,
                inject: Callable[[], None] | None = None) -> None:
        if not all((proposal_id, reason, policy_id)):
            raise ValueError("Proposal ID, reason and policy ID required")
        with self.transaction() as db:
            current = self._expect(db, expected_version)
            if db.execute("SELECT 1 FROM proposals WHERE id=?", (proposal_id,)).fetchone():
                raise Conflict("Proposal ID already exists")
            db.execute(
                """INSERT INTO proposals (id,base_version,before_data,candidate_data,
                       reason,policy_id,status,resolved_version)
                   VALUES (?,?,?,?,?,?,'pending',NULL)""",
                (proposal_id, expected_version, encode(current), encode(candidate),
                 reason, policy_id))
            if inject:
                inject()

    def decide(self, proposal_id: str, decision: str,
               *, expected_version: int,
               inject: Callable[[], None] | None = None) -> None:
        if decision not in ("approve", "reject"):
            raise ValueError("Decision must be approve or reject")
        with self.transaction() as db:
            current = self._expect(db, expected_version)
            row = db.execute(
                "SELECT base_version,before_data,candidate_data,status FROM proposals WHERE id=?",
                (proposal_id,)).fetchone()
            if row is None or row[3] != "pending":
                raise Conflict("Proposal is missing or already resolved")
            base_version, before_json, candidate_json, _ = row
            if base_version != expected_version or decode(before_json) != current:
                raise Conflict("Proposal based on different plan")
            after = decode(candidate_json) if decision == "approve" else deepcopy(current)
            new_version = expected_version + (decision == "approve")
            if decision == "approve":
                db.execute("UPDATE current_plan SET version=?,data=? WHERE singleton=1",
                           (new_version, encode(after)))
            if inject:
                inject()
            db.execute("UPDATE proposals SET status=?,resolved_version=? WHERE id=?",
                       ("approved" if decision == "approve" else "rejected",
                        new_version, proposal_id))
            db.execute("""INSERT INTO audit
                       (proposal_id,action,plan_version,before_data,after_data)
                       VALUES (?,?,?,?,?)""",
                       (proposal_id, "approved" if decision == "approve" else "rejected",
                        new_version, encode(current), encode(after)))

    def rollback(self, proposal_id: str, *, expected_version: int,
                 inject: Callable[[], None] | None = None) -> None:
        with self.transaction() as db:
            current = self._expect(db, expected_version)
            if db.execute("SELECT 1 FROM proposals WHERE status='pending' LIMIT 1").fetchone():
                raise Conflict("Unresolved proposals prevent rollback")
            last = db.execute(
                """SELECT proposal_id,action,plan_version,before_data,after_data
                   FROM audit ORDER BY id DESC LIMIT 1""").fetchone()
            if (last is None or last[0] != proposal_id or last[1] != "approved"
                or last[2] != expected_version or decode(last[4]) != current):
                raise Conflict("Only the current latest approved proposal is reversible")
            restored = decode(last[3])
            next_version = expected_version + 1
            db.execute("UPDATE current_plan SET version=?,data=? WHERE singleton=1",
                       (next_version, encode(restored)))
            if inject:
                inject()
            db.execute("""INSERT INTO audit
                       (proposal_id,action,plan_version,before_data,after_data)
                       VALUES (?,'rollback',?,?,?)""",
                       (proposal_id, next_version, encode(current), encode(restored)))

    def audit_snapshot(self) -> list[dict[str, Any]]:
        return [
            {"proposal_id": row[0], "action": row[1], "version": row[2],
             "before": decode(row[3]), "after": decode(row[4])}
            for row in self.connection.execute(
                """SELECT proposal_id,action,plan_version,before_data,after_data
                   FROM audit ORDER BY id""")
        ]

    def proposal_status(self, proposal_id: str) -> str | None:
        row = self.connection.execute("SELECT status FROM proposals WHERE id=?",
                                      (proposal_id,)).fetchone()
        return None if row is None else row[0]

    def integrity_check(self) -> str:
        return self.connection.execute("PRAGMA integrity_check").fetchone()[0]
