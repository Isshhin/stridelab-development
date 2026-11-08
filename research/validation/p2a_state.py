"""P2-A DEV-0: synthetic-only athlete evidence interpreter.

Describes registered observations at a decision-time cutoff. This module is
NOT a physiological model, validated readiness score, coach, or production code.
It performs no I/O and must not be connected to real/private athlete records.
"""
from __future__ import annotations

from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
from math import isfinite
from typing import Any

SCHEMA = "p2a-dev0-synthetic-state-v1"
KINDS = {"benchmark", "quality_session", "fatigue_report",
         "hrv", "sleep", "soreness", "training"}
QUALITY = {"good", "suspect", "invalid"}
STATUSES = {"measured", "not_collected", "missing_upload",
            "unknown", "withheld", "invalid"}


def _instant(value: Any) -> datetime:
    if not isinstance(value, str):
        raise ValueError("TIMESTAMP_REQUIRED")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None or parsed.utcoffset() is None:
            raise ValueError()
        return parsed.astimezone(timezone.utc)
    except (ValueError, OverflowError) as exc:
        raise ValueError("INVALID_OFFSET_TIMESTAMP") from exc


def _nonempty(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _result(state: str, reason: str, rows: list[dict] | None = None) -> dict:
    return {"state": state, "reason": reason,
            "evidence_ids": sorted(r["event_id"] for r in (rows or []))}


def _benchmarks(rows: list[dict], references: dict) -> dict:
    entries = [r for r in rows if r["kind"] == "benchmark"]
    if not entries:
        return _result("unknown", "NO_COMPARABLE_BENCHMARK")
    anchor = max(entries, key=lambda r: (r["_observed"], r["event_id"]))
    group = anchor.get("comparison_group")
    epoch = anchor.get("device_epoch")
    if not _nonempty(group) or not _nonempty(epoch):
        return _result("unknown", "UNREGISTERED_COMPARISON")
    register = references.get(group)
    if (not isinstance(register, dict) or register.get("device_epoch") != epoch
            or register.get("protocol_id") != anchor.get("protocol_id")):
        return _result("unknown", "NO_REGISTERED_REFERENCE")
    margin = register.get("minimum_detectable_change_mps")
    if (isinstance(margin, bool) or not isinstance(margin, (int, float))
            or not isfinite(margin) or margin <= 0):
        return _result("unknown", "NO_VALID_DETECTABILITY_BOUND")
    matching = sorted(
        [r for r in entries if r.get("comparison_group") == group
         and r.get("device_epoch") == epoch
         and r.get("protocol_id") == anchor.get("protocol_id")],
        key=lambda r: (r["_observed"], r["event_id"]))
    if len(matching) < 3 or len({r["_observed"].date() for r in matching}) < 3:
        return _result("unknown", "INSUFFICIENT_INDEPENDENT_REPEATS")
    if any(a["_observed"] == b["_observed"] for a, b in zip(matching, matching[1:])):
        return _result("unknown", "SAME_INSTANT_CONFLICT")
    values = [r["value"] for r in matching]
    if any(isinstance(v, bool) or not isinstance(v, (int, float))
           or not isfinite(v) or v <= 0 for v in values):
        return _result("unknown", "INVALID_BENCHMARK_VALUE")
    difference = values[-1] - values[0]
    if abs(difference) <= margin:
        return _result("within_registered_error", "DESCRIPTIVE_ONLY", matching)
    nondecreasing = all(a <= b for a, b in zip(values, values[1:]))
    nonincreasing = all(a >= b for a, b in zip(values, values[1:]))
    if difference > margin and nondecreasing:
        return _result("observed_rising", "DESCRIPTIVE_ONLY_NOT_FITNESS", matching)
    if difference < -margin and nonincreasing:
        return _result("observed_falling", "DESCRIPTIVE_ONLY_NOT_FITNESS", matching)
    return _result("mixed", "NONMONOTONIC_OBSERVATIONS", matching)


def _fatigue(rows: list[dict], as_of: datetime, freshness_hours: Any) -> dict:
    entries = [r for r in rows if r["kind"] == "fatigue_report"]
    if not entries:
        return _result("unknown", "NO_FATIGUE_REPORT")
    if (isinstance(freshness_hours, bool) or
            not isinstance(freshness_hours, (int, float)) or
            not isfinite(freshness_hours) or freshness_hours <= 0):
        return _result("unknown", "NO_REGISTERED_FRESHNESS_WINDOW")
    latest = max(r["_observed"] for r in entries)
    contemporaneous = [r for r in entries if r["_observed"] == latest]
    if any(not isinstance(r["value"], str) for r in contemporaneous):
        return _result("unknown", "INVALID_FATIGUE_REPORT")
    if len({r["value"] for r in contemporaneous}) != 1:
        return _result("unknown", "CONCURRENT_REPORT_CONFLICT")
    if (as_of - latest).total_seconds() > freshness_hours * 3600:
        return _result("unknown", "STALE_FATIGUE_REPORT")
    value = contemporaneous[0]["value"]
    if value not in {"low", "moderate", "high"}:
        return _result("unknown", "INVALID_FATIGUE_REPORT")
    return _result("reported_" + value, "SELF_REPORT_NOT_PREDICTION",
                   contemporaneous)


def _tolerance(rows: list[dict], registered_sessions: dict) -> dict:
    entries = [r for r in rows if r["kind"] == "quality_session"]
    if not entries:
        return _result("unknown", "NO_COMPARABLE_QUALITY_SESSIONS")
    anchor = max(entries, key=lambda r: (r["_observed"], r["event_id"]))
    group = anchor.get("comparison_group")
    if not _nonempty(group):
        return _result("unknown", "UNREGISTERED_SESSION_COMPARISON")
    registration = registered_sessions.get(group)
    if (not isinstance(registration, dict)
            or registration.get("device_epoch") != anchor.get("device_epoch")
            or registration.get("protocol_id") != anchor.get("protocol_id")):
        return _result("unknown", "NO_REGISTERED_SESSION_REFERENCE")
    same = sorted([r for r in entries if r.get("comparison_group") == group
                   and r.get("device_epoch") == anchor.get("device_epoch")
                   and r.get("protocol_id") == anchor.get("protocol_id")],
                  key=lambda r: (r["_observed"], r["event_id"]))
    if len(same) < 2 or same[-1]["_observed"].date() == same[-2]["_observed"].date():
        return _result("unknown", "INSUFFICIENT_QUALITY_REPEATS")
    last = same[-2:]
    outcomes = [r["value"] for r in last]
    if any(not isinstance(x, str) or x not in {"met_intent", "struggled"}
           for x in outcomes):
        return _result("unknown", "UNVERIFIED_SESSION_OUTCOME")
    if outcomes == ["met_intent", "met_intent"]:
        return _result("observed_consistent", "DESCRIPTIVE_ONLY", last)
    if outcomes == ["struggled", "struggled"]:
        return _result("observed_difficulty", "DESCRIPTIVE_ONLY", last)
    return _result("mixed", "CONFLICTING_SESSION_OUTCOMES", last)


def interpret(snapshot: dict[str, Any]) -> dict[str, Any]:
    """Interpret synthetic observations with strict decision-time and identity rules."""
    if not isinstance(snapshot, dict) or not _nonempty(snapshot.get("athlete_id")):
        raise ValueError("ATHLETE_ID_REQUIRED")
    athlete_id = snapshot["athlete_id"]
    as_of = _instant(snapshot.get("as_of"))
    incoming = snapshot.get("observations")
    if not isinstance(incoming, list):
        raise ValueError("OBSERVATIONS_LIST_REQUIRED")
    seen: dict[str, dict] = {}
    duplicate_count = 0
    for record in incoming:
        if not isinstance(record, dict):
            raise ValueError("MALFORMED_OBSERVATION")
        if record.get("athlete_id") != athlete_id:
            raise ValueError("CROSS_ATHLETE_OBSERVATION")
        event_id = record.get("event_id")
        if not _nonempty(event_id):
            raise ValueError("EVENT_ID_REQUIRED")
        if event_id in seen:
            if seen[event_id] != record:
                raise ValueError("CONFLICTING_EVENT_ID")
            duplicate_count += 1
        else:
            seen[event_id] = deepcopy(record)
    eligible: list[dict] = []
    exclusions: Counter[str] = Counter()
    for event_id in sorted(seen):
        record = seen[event_id]
        kind = record.get("kind")
        if not isinstance(kind, str) or kind not in KINDS:
            exclusions["UNSUPPORTED_KIND"] += 1
            continue
        observed = _instant(record.get("observed_at"))
        available = _instant(record.get("available_at"))
        if observed > as_of or available > as_of:
            exclusions["UNAVAILABLE_AT_DECISION"] += 1
            continue
        if available < observed:
            exclusions["INCONSISTENT_AVAILABILITY"] += 1
            continue
        if not isinstance(record.get("status"), str) or record["status"] not in STATUSES:
            exclusions["INVALID_STATUS"] += 1
            continue
        if record["status"] != "measured":
            exclusions["NOT_MEASURED"] += 1
            continue
        if not isinstance(record.get("quality"), str) or record["quality"] != "good":
            exclusions["LOW_QUALITY"] += 1
            continue
        if not _nonempty(record.get("source_id")) or not _nonempty(record.get("protocol_id")):
            exclusions["MISSING_PROVENANCE"] += 1
            continue
        if record.get("value") is None:
            exclusions["MISSING_VALUE"] += 1
            continue
        item = deepcopy(record)
        item["_observed"] = observed
        eligible.append(item)
    refs = snapshot.get("registered_benchmarks", {})
    if not isinstance(refs, dict):
        raise ValueError("REGISTERED_BENCHMARKS_MUST_BE_MAP")
    sessions = snapshot.get("registered_sessions", {})
    if not isinstance(sessions, dict):
        raise ValueError("REGISTERED_SESSIONS_MUST_BE_MAP")
    window = snapshot.get("fatigue_freshness_hours")
    fitness = _benchmarks(eligible, refs)
    fatigue = _fatigue(eligible, as_of, window)
    tolerance = _tolerance(eligible, sessions)
    warnings = []
    if any(r["kind"] in {"hrv", "sleep"} for r in eligible):
        warnings.append("WEARABLE_CONTEXT_NOT_INDEPENDENT_VOTE")
    if fatigue["state"] == "reported_high" and any(
        r["kind"] == "hrv" and r.get("assessment") == "favorable"
        for r in eligible
    ):
        warnings.append("FAVORABLE_WEARABLE_DOES_NOT_OVERRIDE_SELF_REPORT")
    return {
        "schema": SCHEMA,
        "athlete_id": athlete_id,
        "as_of": as_of.isoformat(),
        "fitness_trend": fitness,
        "recent_fatigue": fatigue,
        "workout_tolerance": tolerance,
        "evidence_quality": {
            "unique_inputs": len(seen),
            "eligible_inputs": len(eligible),
            "exact_duplicate_inputs": duplicate_count,
            "excluded_by_reason": dict(sorted(exclusions.items())),
            "warnings": sorted(warnings),
        },
        "prediction_status": "NOT_VALIDATED_NO_MODEL",
        "predicted_performance": None,
        "proposed_workout_change": None,
        "coaching_authority": "NONE_SYNTHETIC_RESEARCH",
    }
