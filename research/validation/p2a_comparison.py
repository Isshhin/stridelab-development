"""P2-A research-only feature availability comparison; no forecasting or coaching.

Only invented/synthetic snapshots may be supplied. No I/O, model fitting,
real-data custody, performance labels, inference or policy application.
"""
from __future__ import annotations

from collections import Counter
from datetime import datetime
from math import isfinite
from typing import Any

from p2a_state import _instant, interpret

SCHEMA = "p2a-dev0-comparison-v1"
CANDIDATES = ("A_HISTORY", "B_HISTORY_CHECKIN", "C_HISTORY_CHECKIN_WEARABLE")
FORBIDDEN_KEYS = frozenset({
    "outcome", "future_outcome", "target_outcome", "outcome_label",
    "heldout_label", "held_out_label", "next_session_result",
    "prediction", "predicted_performance", "performance_label",
})


def _named(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _check_no_labels(value: Any) -> None:
    if isinstance(value, dict):
        if FORBIDDEN_KEYS.intersection(value):
            raise ValueError("FUTURE_OR_OUTCOME_LABEL_PRESENT")
        for item in value.values():
            _check_no_labels(item)
    elif isinstance(value, list):
        for item in value:
            _check_no_labels(item)


def _wearable_families(snapshot: dict[str, Any]) -> dict[str, Any]:
    """Require an explicit synthetic baseline per family; never score readiness."""
    registrations = snapshot.get("registered_wearables", {})
    if not isinstance(registrations, dict):
        raise ValueError("WEARABLE_REGISTER_NOT_MAP")
    as_of = _instant(snapshot["as_of"])
    families: dict[str, set[str]] = {}
    discarded: Counter[str] = Counter()
    roots: dict[str, set[str]] = {}
    seen: dict[str, dict] = {}
    for item in snapshot["observations"]:
        if item.get("kind") not in ("hrv", "sleep"):
            continue
        event_id = item["event_id"]  # interpret already validates scoped IDs
        if event_id in seen:
            continue  # exact duplicate identity already audited by interpret
        seen[event_id] = item
        family = item["kind"]
        reg = registrations.get(family)
        if not isinstance(reg, dict) or reg.get("status") != "qualified":
            discarded["BASELINE_NOT_REGISTERED"] += 1
            continue
        keys = ("device_epoch", "protocol_id", "baseline_id")
        if not all(_named(item.get(k)) and item[k] == reg.get(k) for k in keys):
            discarded["PROTOCOL_OR_DEVICE_BRIDGE_UNVERIFIED"] += 1
            continue
        max_age = reg.get("max_age_hours")
        if (isinstance(max_age, bool) or not isinstance(max_age, (int, float))
                or not isfinite(max_age) or max_age <= 0):
            discarded["FRESHNESS_UNREGISTERED"] += 1
            continue
        observed = _instant(item.get("observed_at"))
        available = _instant(item.get("available_at"))
        if available < observed or observed > as_of or available > as_of:
            discarded["UNAVAILABLE_OR_INCONSISTENT_TIME"] += 1
            continue
        if (as_of - observed).total_seconds() > max_age * 3600:
            discarded["STALE_WEARABLE"] += 1
            continue
        if (item.get("status") != "measured" or item.get("quality") != "good"
                or not _named(item.get("source_id"))):
            discarded["UNQUALIFIED_WEARABLE_OBSERVATION"] += 1
            continue
        number = item.get("value")
        if (isinstance(number, bool) or not isinstance(number, (int, float))
                or not isfinite(number)):
            discarded["INVALID_WEARABLE_VALUE"] += 1
            continue
        root = item.get("lineage_root_id")
        if not _named(root):
            discarded["MISSING_LINEAGE_ROOT"] += 1
            continue
        roots.setdefault(root, set()).add(family)
        families.setdefault(family, set()).add(event_id)
    return {
        "families": sorted(families),
        "evidence_ids": sorted(set().union(*families.values()) if families else set()),
        "lineage_roots": sorted(roots),
        "correlated_lineages": sorted(k for k, v in roots.items() if len(v) > 1),
        "excluded_by_reason": dict(sorted(discarded.items())),
    }


def _row(snapshot: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(snapshot, dict) or not _named(snapshot.get("opportunity_id")):
        raise ValueError("OPPORTUNITY_ID_REQUIRED")
    _check_no_labels(snapshot)
    state = interpret(snapshot)  # validates ownership, duplicates and timestamps
    wearable = _wearable_families(snapshot)
    history_ids = sorted(set(state["fitness_trend"]["evidence_ids"]
                             + state["workout_tolerance"]["evidence_ids"]))
    checkin_ids = state["recent_fatigue"]["evidence_ids"]
    states: dict[str, dict[str, str]] = {}
    if not history_ids:
        states = {name: {"status": "ABSTAIN", "reason": "HISTORY_UNINTERPRETABLE"}
                  for name in CANDIDATES}
    else:
        states["A_HISTORY"] = {"status": "FEATURES_READY", "reason": "HISTORY_ONLY"}
        if not checkin_ids:
            states["B_HISTORY_CHECKIN"] = {
                "status": "ABSTAIN", "reason": "CHECKIN_UNAVAILABLE"}
            states["C_HISTORY_CHECKIN_WEARABLE"] = {
                "status": "ABSTAIN", "reason": "CHECKIN_UNAVAILABLE"}
        else:
            states["B_HISTORY_CHECKIN"] = {
                "status": "FEATURES_READY", "reason": "HISTORY_AND_CHECKIN"}
            states["C_HISTORY_CHECKIN_WEARABLE"] = (
                {"status": "FEATURES_READY", "reason": "WEARABLE_CONTEXT_REGISTERED"}
                if wearable["families"] else
                {"status": "ABSTAIN", "reason": "WEARABLE_CONTEXT_UNQUALIFIED"})
    return {
        "athlete_id": state["athlete_id"],
        "opportunity_id": snapshot["opportunity_id"],
        "as_of": state["as_of"],
        "candidate_states": states,
        "history_evidence_ids": history_ids,
        "checkin_evidence_ids": sorted(checkin_ids),
        "wearable": wearable,
        "evidence_exclusions": state["evidence_quality"]["excluded_by_reason"],
    }


def compare(snapshots: list[dict[str, Any]]) -> dict[str, Any]:
    """Compare *feature readiness* on identical synthetic opportunities only.

    Never compute predictive skill, declare a winning model or alter plans.
    """
    if not isinstance(snapshots, list):
        raise ValueError("SNAPSHOTS_LIST_REQUIRED")
    rows = []
    keys: set[tuple[str, str]] = set()
    for snapshot in snapshots:
        row = _row(snapshot)
        key = (row["athlete_id"], row["opportunity_id"])
        if key in keys:
            raise ValueError("DUPLICATE_OPPORTUNITY")
        keys.add(key)
        rows.append(row)
    rows.sort(key=lambda r: (r["athlete_id"], r["opportunity_id"]))
    coverage = {}
    for candidate in CANDIDATES:
        counts = Counter(row["candidate_states"][candidate]["status"] for row in rows)
        coverage[candidate] = {
            "same_denominator": len(rows),
            "features_ready": counts["FEATURES_READY"],
            "abstained": counts["ABSTAIN"],
        }
    return {
        "schema": SCHEMA,
        "status": "SYNTHETIC_FEATURE_ELIGIBILITY_ONLY",
        "candidate_order": list(CANDIDATES),
        "opportunity_count": len(rows),
        "coverage": coverage,
        "cases": rows,
        "predictive_skill": None,
        "best_candidate": None,
        "coaching_authority": "NONE_SYNTHETIC_RESEARCH",
        "gate2": "CLOSED",
    }
