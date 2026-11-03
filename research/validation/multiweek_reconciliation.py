"""Versioned synthetic training-history reconciliation for independent research.

Never infer a skipped workout from an absent source record, identify a device
activity across providers by GPS/time similarity, or rewrite completed records
after plan edits. Explicit verified canonical IDs are required to deduplicate
cross-source observations. Conflicting sources remain unresolved.
"""
from __future__ import annotations

from collections import defaultdict
from copy import deepcopy
from typing import Any

from sqlite_ledger import Conflict, ResearchPlanStore, encode

VALID_STATUS = {"completed", "partial", "skipped", "unplanned", "unknown"}


def source_activity_id(event: dict[str, Any]) -> str:
    source, external = event.get("source"), event.get("external_id")
    if not isinstance(source, str) or not source or not isinstance(external, str) or not external:
        raise ValueError("Source and external event ID must be nonempty strings")
    if "::" in source:
        raise ValueError("Source must not include identity delimiter")
    return source + "::" + external


def validate_event(event: dict[str, Any]) -> None:
    source_activity_id(event)
    if event.get("status") not in VALID_STATUS:
        raise ValueError("Invalid observed status")
    if not isinstance(event.get("week"), int) or event["week"] < 1:
        raise ValueError("Week number missing")
    if event.get("discipline") not in {"running", "strength", "calisthenics", "conditioning"}:
        raise ValueError("Unknown discipline")
    if event.get("verified_session_id") is not None and not isinstance(
            event.get("verified_session_id"), str):
        raise ValueError("Verified session identity must be a string or null")
    for key in ("actual_km", "actual_hard_s", "actual_easy_s", "actual_sets"):
        value = event.get(key)
        if value is not None and (not isinstance(value, (int, float))
                                  or isinstance(value, bool) or value < 0):
            raise ValueError(f"Invalid {key}: {value}")
    if event["status"] == "skipped" and any(event.get(k) not in (0, 0.0, None)
            for k in ("actual_km", "actual_hard_s", "actual_easy_s", "actual_sets")):
        raise ValueError("A confirmed skip cannot have completed training exposure")
    if event["status"] == "unknown" and any(event.get(k) is not None
            for k in ("actual_km", "actual_hard_s", "actual_easy_s", "actual_sets")):
        raise ValueError("Unknown work dose must be null, not a number")
    if event["status"] in {"completed", "partial", "unplanned"}:
        expected = ["actual_km", "actual_hard_s", "actual_easy_s"] if (
            event["discipline"] == "running") else ["actual_sets"]
        if any(event.get(k) is None for k in expected):
            raise ValueError("Confirmed work requires recorded discipline-native dose")


def ingest_source_batch(db: ResearchPlanStore, events: list[dict[str, Any]],
                        inject_after: int | None = None) -> dict[str, int]:
    """Atomic ingestion of a batch, with identical replay accepted.

    An injection raises in the middle of the transaction to verify that no
    partial batch is committed. Caller must not infer cross-source identity.
    """
    for event in events:
        validate_event(event)
    seen: dict[str, str] = {}
    for event in events:
        ident, payload = source_activity_id(event), encode(event)
        if ident in seen and seen[ident] != payload:
            raise Conflict("Contradictory events in same batch")
        seen[ident] = payload

    added = replayed = 0
    with db.transaction() as sql:
        for idx, (identity, payload) in enumerate(seen.items(), 1):
            prior = sql.execute("SELECT data FROM activities WHERE id=?", (identity,)).fetchone()
            if prior is None:
                sql.execute("INSERT INTO activities (id,data) VALUES (?,?)",
                            (identity, payload))
                added += 1
            elif prior[0] == payload:
                replayed += 1
            else:
                raise Conflict(f"Existing event {identity} has different content")
            if inject_after == idx:
                raise RuntimeError("SIMULATED_BATCH_INTERRUPTION")
    return {"inserted": added, "replayed": replayed}


def reconcile_week(planned_sessions: list[dict[str, Any]],
                   activity_snapshot: list[tuple[str, dict[str, Any]]],
                   week: int) -> dict[str, Any]:
    """Reconcile confirmed data and explicit cross-source links only.

    Conflicted and unlinked observations are excluded from totals and surfaced.
    Returned actual quantities are *known-only sums*, not whole-week estimates.
    """
    plans = {session["id"]: deepcopy(session) for session in planned_sessions}
    if len(plans) != len(planned_sessions):
        raise ValueError("Duplicate planned session IDs")
    groups: dict[str, list[tuple[str, dict[str, Any]]]] = defaultdict(list)
    unlinked: list[str] = []
    invalid_week: list[str] = []
    for identity, event in activity_snapshot:
        validate_event(event)
        if event["week"] != week:
            continue
        if identity != source_activity_id(event):
            raise Conflict("Recorded identity does not match source/external ID")
        canonical = event.get("verified_session_id")
        if canonical is None:
            unlinked.append(identity)
        elif canonical in plans or event["status"] == "unplanned":
            groups[canonical].append((identity, event))
        else:
            invalid_week.append(identity)

    observations: dict[str, dict[str, Any]] = {}
    conflicts: list[str] = []
    counted_unplanned: list[str] = []
    actual_km = actual_hard_s = actual_easy_s = actual_sets = 0
    completed = partial = skipped = missing = 0
    for canonical, plan in plans.items():
        rows = groups.pop(canonical, [])
        if not rows:
            observations[canonical] = {"status": "unknown", "sources": [],
                                       "observed": None}
            missing += 1
            continue
        comparable = {
            encode({key: val for key, val in event.items()
                    if key not in ("source", "external_id", "recorded_at")})
            for _, event in rows
        }
        if len(comparable) > 1:
            observations[canonical] = {"status": "conflict", "sources": [
                ident for ident, _ in rows], "observed": None}
            conflicts.append(canonical)
            continue
        event = rows[0][1]
        if event["discipline"] != plan["discipline"] or event["status"] == "unplanned":
            conflicts.append(canonical)
            observations[canonical] = {"status": "conflict",
                                       "sources": [ident for ident, _ in rows],
                                       "observed": None}
            continue
        observations[canonical] = {"status": event["status"],
                                   "sources": [ident for ident, _ in rows],
                                   "observed": deepcopy(event)}
        if event["status"] == "completed":
            completed += 1
        elif event["status"] == "partial":
            partial += 1
        elif event["status"] == "skipped":
            skipped += 1
        else:
            missing += 1
        if event["status"] in {"completed", "partial"}:
            actual_km += event.get("actual_km") or 0
            actual_hard_s += event.get("actual_hard_s") or 0
            actual_easy_s += event.get("actual_easy_s") or 0
            actual_sets += event.get("actual_sets") or 0

    for canonical, rows in groups.items():
        if any(item["status"] != "unplanned" for _, item in rows):
            invalid_week.extend(ident for ident, _ in rows)
            continue
        comparable = {
            encode({key: val for key, val in event.items()
                    if key not in ("source", "external_id", "recorded_at")})
            for _, event in rows
        }
        if len(comparable) > 1:
            conflicts.append(canonical)
            continue
        event = rows[0][1]
        counted_unplanned.append(canonical)
        actual_km += event.get("actual_km") or 0
        actual_hard_s += event.get("actual_hard_s") or 0
        actual_easy_s += event.get("actual_easy_s") or 0
        actual_sets += event.get("actual_sets") or 0

    total_plan_km = sum(
        s.get("planned_km", 0) for s in planned_sessions if s["discipline"] == "running")
    return {
        "week": week, "planned_running_km": round(total_plan_km, 6),
        "known_actual_running_km": round(actual_km, 6),
        "known_actual_hard_s": int(actual_hard_s),
        "known_actual_easy_s": int(actual_easy_s),
        "known_actual_strength_sets": int(actual_sets),
        "completed": completed, "partial": partial, "skipped": skipped,
        "missing_or_unknown": missing, "conflicts": sorted(conflicts),
        "unlinked_source_records": sorted(unlinked),
        "invalid_source_links": sorted(invalid_week),
        "unplanned_verified": sorted(counted_unplanned),
        "observations": observations,
        "total_actual_complete": (missing == 0 and not conflicts
                                  and not unlinked and not invalid_week),
    }
