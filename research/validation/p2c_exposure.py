"""P2-C DEV-0: deterministic research-only reconciliation of invented exposure.

No I/O, physiological inference, training prescription or real-data admission.
A missing activity is unknown, not a confirmed skipped session.
"""
from __future__ import annotations

from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
from math import isfinite
from typing import Any

SCHEMA = "p2c-dev0-exposure-v1"
KINDS = {"easy", "long_run", "threshold", "intervals", "race"}
STATUSES = {"completed", "partial", "confirmed_skipped", "unknown"}
RESPONSES = {"tolerated", "difficult", "unknown"}


def _time(value: Any) -> datetime:
    if not isinstance(value, str):
        raise ValueError("OFFSET_TIME_REQUIRED")
    try:
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if result.tzinfo is None or result.utcoffset() is None:
            raise ValueError()
        return result.astimezone(timezone.utc)
    except (ValueError, OverflowError) as exc:
        raise ValueError("OFFSET_TIME_REQUIRED") from exc


def _id(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _dose(value: Any) -> bool:
    return (type(value) in (int, float) and isfinite(value) and value >= 0)


def _record(item: Any, athlete_id: str, version: int, is_event: bool) -> dict:
    if not isinstance(item, dict) or item.get("athlete_id") != athlete_id:
        raise ValueError("CROSS_ATHLETE_OR_BAD_RECORD")
    field = "event_id" if is_event else "opportunity_id"
    if not _id(item.get(field)) or item.get("plan_version") != version:
        raise ValueError("IDENTITY_OR_PLAN_VERSION_INVALID")
    if not isinstance(item.get("kind"), str) or item["kind"] not in KINDS:
        raise ValueError("UNSUPPORTED_SESSION_KIND")
    if not _id(item.get("source_id")):
        raise ValueError("SOURCE_ID_REQUIRED")
    instant = _time(item.get("observed_at" if is_event else "planned_at"))
    available = _time(item.get("available_at"))
    if available < instant:
        raise ValueError("INCONSISTENT_AVAILABILITY")
    copy = deepcopy(item)
    copy["_instant"] = instant
    copy["_available"] = available
    if is_event:
        if not isinstance(item.get("status"), str) or item["status"] not in STATUSES:
            raise ValueError("INVALID_ACTIVITY_STATUS")
        link = item.get("opportunity_id")
        if link is not None and not _id(link):
            raise ValueError("INVALID_OPPORTUNITY_LINK")
        if (not isinstance(item.get("response", "unknown"), str)
                or not isinstance(item.get("next_day_response", "unknown"), str)
                or item.get("response", "unknown") not in RESPONSES
                or item.get("next_day_response", "unknown") not in RESPONSES):
            raise ValueError("INVALID_RESPONSE")
        km, minutes = item.get("actual_km"), item.get("actual_minutes")
        if item["status"] in ("completed", "partial"):
            if not _dose(minutes) or not _dose(km):
                raise ValueError("ACTUAL_DOSE_REQUIRED")
        elif km is not None or minutes is not None:
            raise ValueError("UNKNOWN_OR_SKIP_DOSE_MUST_BE_NULL")
        if item["status"] == "confirmed_skipped" and item.get("reason") not in {
            "logistical", "planned_rest", "travel", "race_recovery", "illness", "injury", "other_confirmed"
        }:
            raise ValueError("CONFIRMED_SKIP_REASON_REQUIRED")
        if item["status"] not in ("completed", "partial") and (
            item.get("response", "unknown") != "unknown" or
            item.get("next_day_response", "unknown") != "unknown"
        ):
            raise ValueError("RESPONSE_WITHOUT_PERFORMED_EXPOSURE")
    return copy


def summarize(snapshot: dict[str, Any]) -> dict[str, Any]:
    """Return known-only exposure totals and complete reason-coded roster."""
    if not isinstance(snapshot, dict) or snapshot.get("synthetic_only") is not True:
        raise ValueError("SYNTHETIC_ONLY_REQUIRED")
    athlete = snapshot.get("athlete_id")
    version = snapshot.get("plan_version")
    if not _id(athlete) or type(version) is not int or version < 1:
        raise ValueError("ATHLETE_OR_PLAN_VERSION_INVALID")
    cutoff = _time(snapshot.get("as_of"))
    planned, events = snapshot.get("opportunities"), snapshot.get("events")
    if not isinstance(planned, list) or not isinstance(events, list):
        raise ValueError("ROSTERS_MUST_BE_LISTS")

    plans: dict[str, dict] = {}
    exclusions: Counter[str] = Counter()
    for raw in planned:
        item = _record(raw, athlete, version, False)
        key = item["opportunity_id"]
        if key in plans:
            raise ValueError("DUPLICATE_OPPORTUNITY_ID")
        if item["_instant"] > cutoff or item["_available"] > cutoff:
            exclusions["PLAN_UNAVAILABLE"] += 1
            continue
        plans[key] = item

    seen: dict[str, dict] = {}
    exact_replays = 0
    for raw in events:
        if not isinstance(raw, dict):
            raise ValueError("MALFORMED_EVENT")
        key = raw.get("event_id")
        if not _id(key):
            raise ValueError("EVENT_ID_REQUIRED")
        if key in seen:
            if seen[key] != raw:
                raise ValueError("CONFLICTING_EVENT_ID")
            exact_replays += 1
            continue
        seen[key] = deepcopy(raw)

    by_plan: dict[str, list[dict]] = {key: [] for key in plans}
    unlinked: list[str] = []
    for event_id in sorted(seen):
        event = _record(seen[event_id], athlete, version, True)
        if event["_instant"] > cutoff or event["_available"] > cutoff:
            exclusions["EVENT_UNAVAILABLE"] += 1
            continue
        linked = event.get("opportunity_id")
        if linked not in plans:
            unlinked.append(event_id)
            exclusions["UNLINKED_OR_UNAVAILABLE_PLAN"] += 1
            continue
        if event["kind"] != plans[linked]["kind"]:
            exclusions["DISCIPLINE_CONFLICT"] += 1
            by_plan[linked].append({"_conflict": True, "event_id": event_id})
            continue
        if event["_instant"] < plans[linked]["_available"]:
            exclusions["EVENT_PRECEDES_PLAN_AVAILABILITY"] += 1
            by_plan[linked].append({"_conflict": True, "event_id": event_id})
            continue
        by_plan[linked].append(event)

    roster: list[dict] = []
    statuses: Counter[str] = Counter()
    total_km = total_minutes = 0.0
    verified: list[dict] = []
    for key in sorted(plans):
        rows = by_plan[key]
        status = "unknown" if not rows else rows[0]["status"] if len(rows) == 1 and not rows[0].get("_conflict") else "conflict"
        evidence_ids = sorted(r["event_id"] for r in rows)
        item = {"opportunity_id": key, "kind": plans[key]["kind"],
                "status": status, "evidence_ids": evidence_ids,
                "actual_km": None, "actual_minutes": None,
                "response": "unknown", "next_day_response": "unknown"}
        if status in ("completed", "partial"):
            row = rows[0]
            item["observed_at"] = row["_instant"].isoformat()
            item["exposure_epoch"] = row.get("exposure_epoch")
            item.update(actual_km=row["actual_km"], actual_minutes=row["actual_minutes"],
                        response=row.get("response", "unknown"),
                        next_day_response=row.get("next_day_response", "unknown"))
            verified.append(item)
            if item["kind"] in {"easy", "long_run", "threshold", "intervals", "race"}:
                total_km += row["actual_km"]
                total_minutes += row["actual_minutes"]
        if status == "confirmed_skipped":
            item["reason"] = rows[0]["reason"]
        statuses[status] += 1
        roster.append(item)
    runs = [r for r in verified if r["kind"] in {"easy", "long_run", "threshold", "intervals", "race"}]
    return {"schema": SCHEMA, "athlete_id": athlete, "as_of": cutoff.isoformat(),
            "plan_version": version, "roster": roster,
            "planned_count": len(plans), "status_counts": dict(sorted(statuses.items())),
            "known_running_km": round(total_km, 6),
            "known_running_minutes": round(total_minutes, 6),
            "verified_run_count": len(runs),
            "max_verified_run_km": max((r["actual_km"] for r in runs), default=None),
            "max_verified_run_minutes": max((r["actual_minutes"] for r in runs), default=None),
            "unlinked_event_ids": sorted(unlinked),
            "exact_duplicate_imports": exact_replays,
            "excluded_by_reason": dict(sorted(exclusions.items())),
            "coverage_complete": bool(plans) and not unlinked and
                not any(r["status"] in ("unknown", "conflict") for r in roster),
            "model_status": "SYNTHETIC_ACCOUNTING_ONLY"}
