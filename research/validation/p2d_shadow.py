"""P2-D DEV-0: non-executable adaptive decision shadow oracle (invented records only).

This is NOT an athlete-facing coaching policy. It emits no workout dose, edited
calendar, performance forecast, treatment advice or authorization to act.
"""
from __future__ import annotations

from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
from typing import Any

SCHEMA = "p2d-dev0-shadow-v1"
PURPOSES = {"easy_aerobic", "long_duration", "controlled_threshold", "fast_repetitions", "race_pace"}
REASONS = {"none", "time_short", "pace_uncomfortable", "late_bout_fatigue",
           "equipment_unavailable", "schedule_conflict", "fatigue_reported",
           "requested_stop", "missed_session", "unknown", "illness", "injury"}
CLINICAL = {"none", "active", "unknown"}
OBS_TYPES = {"athlete_report", "schedule_constraint", "observed_session", "clinical_context"}
LABEL_KEYS = {"outcome", "outcome_label", "future_outcome", "next_session_result",
              "heldout_label", "held_out_label", "target_outcome", "predictor_fit",
              "predicted_performance", "training_benefit", "calibration_target",
              "actual_outcome", "followup_outcome", "future_performance"}
OPTION_ORDER = ("KEEP", "REDUCE_VOLUME", "REDUCE_INTENSITY", "SHORTEN_INTERVALS",
                "REDUCE_REPS", "SUBSTITUTE", "MOVE", "RECOVERY", "REST")


def _nonempty(x: Any) -> bool:
    return isinstance(x, str) and bool(x.strip())


def _timestamp(value: Any) -> datetime:
    if not isinstance(value, str):
        raise ValueError("OFFSET_TIMESTAMP_REQUIRED")
    try:
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if dt.tzinfo is None or dt.utcoffset() is None:
            raise ValueError()
        return dt.astimezone(timezone.utc)
    except (ValueError, OverflowError) as err:
        raise ValueError("INVALID_OFFSET_TIMESTAMP") from err


def _block_labels(obj: Any) -> None:
    if isinstance(obj, dict):
        if LABEL_KEYS.intersection(obj):
            raise ValueError("OUTCOME_OR_FUTURE_LABEL_FORBIDDEN")
        for v in obj.values():
            _block_labels(v)
    elif isinstance(obj, list):
        for v in obj:
            _block_labels(v)


def _id_for(obj: Any) -> str:
    raw = json.dumps(obj, sort_keys=True, allow_nan=False, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _options_for(reason: str, purpose: str) -> list[tuple[str, str]]:
    if reason == "none":
        return [("KEEP", "UNCHANGED_PURPOSE")]
    if reason == "time_short":
        return [("REDUCE_VOLUME", "PARTIAL_LONG_DURATION_PURPOSE" if purpose == "long_duration"
                 else "LOWER_TOTAL_EXPOSURE"), ("MOVE", "DOWNSTREAM_SCHEDULE_UNCHECKED"),
                ("REST", "SESSION_PURPOSE_LOST")]
    if reason == "pace_uncomfortable":
        return [("REDUCE_INTENSITY", "SPEED_SPECIFIC_PURPOSE_MAY_BE_LOST"),
                ("RECOVERY", "QUALITY_PURPOSE_LOST"), ("REST", "SESSION_PURPOSE_LOST")]
    if reason == "late_bout_fatigue":
        if purpose not in {"controlled_threshold", "fast_repetitions"}:
            return []
        return [("SHORTEN_INTERVALS", "CONTINUOUS_BOUT_PURPOSE_CHANGED"),
                ("REDUCE_REPS", "TOTAL_QUALITY_EXPOSURE_LOWER"),
                ("REST", "QUALITY_PURPOSE_LOST")]
    if reason == "equipment_unavailable":
        return [("SUBSTITUTE", "MODALITY_EQUIVALENCE_UNPROVEN"),
                ("MOVE", "DOWNSTREAM_SCHEDULE_UNCHECKED"),
                ("REST", "SESSION_PURPOSE_LOST")]
    if reason == "schedule_conflict":
        return [("MOVE", "DOWNSTREAM_SCHEDULE_UNCHECKED"),
                ("REDUCE_VOLUME", "LESS_TOTAL_EXPOSURE"),
                ("REST", "SESSION_PURPOSE_LOST")]
    if reason == "fatigue_reported":
        return [("RECOVERY", "TRAINING_STIMULUS_RELINQUISHED"),
                ("REST", "TRAINING_STIMULUS_RELINQUISHED")]
    if reason in {"requested_stop", "illness", "injury"}:
        return [("REST", "TRAINING_STIMULUS_RELINQUISHED")]
    return []


def _option(code: str, tradeoff: str, refs: list[str]) -> dict[str, Any]:
    return {"code": code, "tradeoff": tradeoff, "evidence_ids": refs,
            "action_status": "SHADOW_ONLY_NOT_A_RECOMMENDATION",
            "preview_status": "NOT_MATERIALIZED",
            "requires_explicit_approval_to_save": True,
            "stop_needs_approval": False if code == "REST" else None,
            "proposed_dose": None, "candidate_plan": None, "applied": False}


def shadow_review(snapshot: dict[str, Any]) -> dict[str, Any]:
    """Create a deterministic policy *review record* with no trained decision.

    Caller supplied `synthetic_only` and fixture identifiers are not rights or
    source attestation. No actual athlete data may be connected to this oracle.
    """
    if not isinstance(snapshot, dict):
        raise ValueError("SNAPSHOT_MAP_REQUIRED")
    if snapshot.get("synthetic_only") is not True:
        raise ValueError("SYNTHETIC_ONLY_REQUIRED")
    _block_labels(snapshot)
    athlete = snapshot.get("athlete_id")
    if not _nonempty(athlete):
        raise ValueError("ATHLETE_ID_REQUIRED")
    now = _timestamp(snapshot.get("as_of"))
    plan = snapshot.get("plan")
    if not isinstance(plan, dict) or plan.get("athlete_id") != athlete:
        raise ValueError("PLAN_IDENTITY_MISMATCH")
    if not _nonempty(plan.get("plan_id")) or type(plan.get("version")) is not int or plan["version"] < 1:
        raise ValueError("PLAN_ID_VERSION_REQUIRED")
    sessions = plan.get("sessions")
    if not isinstance(sessions, list) or not sessions:
        raise ValueError("PLAN_SESSIONS_REQUIRED")
    session_ids: set[str] = set()
    for s in sessions:
        if not isinstance(s, dict) or not _nonempty(s.get("id")) or s["id"] in session_ids:
            raise ValueError("PLAN_SESSION_ID_INVALID")
        if s.get("purpose") not in PURPOSES or s.get("discipline") != "running":
            raise ValueError("UNSUPPORTED_PLAN_SESSION")
        if "locked" in s and type(s["locked"]) is not bool:
            raise ValueError("SESSION_LOCK_TYPE_INVALID")
        session_ids.add(s["id"])
    request = snapshot.get("request")
    if not isinstance(request, dict):
        raise ValueError("REQUEST_REQUIRED")
    if request.get("expected_plan_version") != plan["version"]:
        raise ValueError("STALE_PLAN_VERSION")
    wanted = request.get("session_id")
    if not _nonempty(wanted) or wanted not in session_ids:
        raise ValueError("SESSION_NOT_FOUND")
    target = next(s for s in sessions if s["id"] == wanted)
    purpose = request.get("primary_purpose")
    reason = request.get("reason")
    clinical = request.get("clinical_status", "unknown")
    if purpose != target["purpose"]:
        raise ValueError("PURPOSE_MISMATCH")
    if (not isinstance(reason, str) or reason not in REASONS
            or not isinstance(clinical, str) or clinical not in CLINICAL):
        raise ValueError("REASON_OR_CLINICAL_STATUS_INVALID")
    refs = request.get("evidence_ids")
    if not isinstance(refs, list) or not refs or any(not _nonempty(x) for x in refs) or len(set(refs)) != len(refs):
        raise ValueError("EVIDENCE_IDS_REQUIRED_UNIQUE")
    observations = snapshot.get("observations")
    if not isinstance(observations, list):
        raise ValueError("OBSERVATIONS_LIST_REQUIRED")
    seen: dict[str, dict[str, Any]] = {}
    duplicates = 0
    excluded: Counter[str] = Counter()
    for row in observations:
        if not isinstance(row, dict) or row.get("athlete_id") != athlete:
            raise ValueError("CROSS_ATHLETE_OR_INVALID_EVENT")
        event_id = row.get("event_id")
        if not _nonempty(event_id):
            raise ValueError("EVENT_ID_REQUIRED")
        if event_id in seen:
            if row != seen[event_id]:
                raise ValueError("CONFLICTING_EVENT_ID")
            duplicates += 1
        else:
            seen[event_id] = deepcopy(row)
    eligible: set[str] = set()
    for event_id in sorted(seen):
        row = seen[event_id]
        observed = _timestamp(row.get("observed_at"))
        available = _timestamp(row.get("available_at"))
        if observed > now or available > now:
            excluded["UNAVAILABLE_AT_DECISION"] += 1
        elif available < observed:
            excluded["INCONSISTENT_AVAILABILITY"] += 1
        elif not isinstance(row.get("kind"), str) or row["kind"] not in OBS_TYPES:
            excluded["UNSUPPORTED_OBSERVATION"] += 1
        elif row.get("status") != "measured" or row.get("quality") != "good" or row.get("value") is None:
            excluded["UNQUALIFIED_OBSERVATION"] += 1
        elif not _nonempty(row.get("source_id")) or not _nonempty(row.get("protocol_id")):
            excluded["MISSING_PROVENANCE"] += 1
        elif row.get("session_id") != wanted:
            excluded["SESSION_LINK_MISMATCH"] += 1
        elif not _nonempty(row.get("lineage_root_id")):
            excluded["MISSING_LINEAGE_ROOT"] += 1
        else:
            eligible.add(event_id)
    unknown_ref = sorted(set(refs) - eligible)
    explicit_stop = any(seen[e].get("kind") == "athlete_report" and
                        isinstance(seen[e].get("value"), str) and
                        seen[e]["value"] in {"stop", "cannot_continue"}
                        for e in eligible)
    clinical_observation = any(seen[e].get("kind") == "clinical_context" and
                               seen[e].get("value") != "none"
                               for e in eligible)
    difficult_observation = any(seen[e].get("kind") == "observed_session" and
                                 isinstance(seen[e].get("value"), str) and
                                 seen[e]["value"] in {"difficult", "struggled"}
                                 for e in eligible)
    record_status = "SHADOW_REVIEW_ONLY"
    record_reason = "EVIDENCE_AVAILABLE_SYNTHETIC_ONLY"
    if (clinical in {"active", "unknown"} or reason in {"illness", "injury"}
            or clinical_observation):
        record_status = "CLINICAL_BOUNDARY_DEFER"
        record_reason = "NO_CLINICAL_CLEARANCE_OR_RETURN_TIMELINE"
        pairs = [("REST", "TRAINING_STIMULUS_RELINQUISHED")]
    elif explicit_stop or reason == "requested_stop":
        record_status, record_reason = "STOP_REQUESTED", "ATHLETE_STOP_REPORTED"
        pairs = [("REST", "TRAINING_STIMULUS_RELINQUISHED")]
    elif unknown_ref:
        record_status, record_reason, pairs = ("ABSTAIN", "MISSING_OR_FUTURE_EVIDENCE", [])
    elif request.get("reentry_status") not in (None, "not_applicable"):
        record_status, record_reason, pairs = ("ABSTAIN", "UNVERIFIED_REENTRY_AUTHORITY", [])
    elif request.get("synthetic_fixture_registered") is not True:
        record_status, record_reason, pairs = ("ABSTAIN", "SYNTHETIC_FIXTURE_NOT_REGISTERED", [])
    elif target.get("locked") is True:
        record_status, record_reason, pairs = ("ABSTAIN", "LOCKED_SESSION", [])
    elif difficult_observation and reason == "none":
        record_status, record_reason, pairs = ("ABSTAIN", "CONFLICTING_EFFORT_EVIDENCE", [])
    elif reason in {"missed_session", "unknown"}:
        record_status, record_reason, pairs = ("ABSTAIN", "REASON_OR_ACTIVITY_UNVERIFIED", [])
    else:
        pairs = _options_for(reason, purpose)
        if not pairs:
            record_status, record_reason = "ABSTAIN", "PURPOSE_REASON_NOT_SUPPORTED"
    ordered = sorted(pairs, key=lambda p: OPTION_ORDER.index(p[0]))
    options = [_option(code, impact, sorted(refs)) for code, impact in ordered]
    provenance = {"plan_id": plan["plan_id"], "plan_version": plan["version"],
                  "plan_sha256": _id_for(plan), "session_id": wanted,
                  "decision_as_of": now.isoformat(), "decision_evidence_ids": sorted(refs),
                  "unique_observation_count": len(seen), "duplicate_input_count": duplicates,
                  "excluded_by_reason": dict(sorted(excluded.items())),
                  "unavailable_decision_evidence_ids": unknown_ref,
                  "lineage_roots": sorted({seen[e]["lineage_root_id"] for e in refs if e in eligible})}
    return {"schema": SCHEMA, "status": record_status, "reason": record_reason,
            "athlete_id": athlete, "primary_purpose": purpose,
            "constraint_reason": reason, "provenance": provenance,
            "options": options, "automatic_selection": None,
            "plan_patch": None, "applied": False,
            "requires_external_explicit_approval": True,
            "coaching_effectiveness": "NOT_ESTABLISHED",
            "forecast": None, "source_admission": "NOT_EVALUATED", "gate2": "CLOSED"}


def compare_shadow(snapshots: list[dict[str, Any]]) -> dict[str, Any]:
    """Track two *shadow* policies on an identical cohort; no outcome comparison."""
    if not isinstance(snapshots, list):
        raise ValueError("SNAPSHOTS_LIST_REQUIRED")
    items: list[dict[str, Any]] = []
    identities: set[tuple[str, str]] = set()
    for snapshot in snapshots:
        result = shadow_review(snapshot)
        opportunity = snapshot.get("opportunity_id")
        if not _nonempty(opportunity):
            raise ValueError("OPPORTUNITY_ID_REQUIRED")
        identity = (result["athlete_id"], opportunity)
        if identity in identities:
            raise ValueError("DUPLICATE_OPPORTUNITY")
        identities.add(identity)
        if result["status"] == "CLINICAL_BOUNDARY_DEFER":
            baseline = "HOLD_FOR_CLINICAL_REVIEW"
        elif result["status"] == "STOP_REQUESTED":
            baseline = "HOLD_FOR_REPORTED_STOP"
        elif result["status"] == "ABSTAIN":
            baseline = "ABSTAIN_UNKNOWN_EVIDENCE"
        else:
            baseline = "KEEP_SHADOW_UNVALIDATED"
        items.append({"athlete_id": result["athlete_id"], "opportunity_id": opportunity,
                      "baseline_policy": baseline,
                      "context_policy": result["status"],
                      "context_option_codes": [x["code"] for x in result["options"]],
                      "decision_provenance": result["provenance"],
                      "acted": False, "real_outcome": None})
    items.sort(key=lambda x: (x["athlete_id"], x["opportunity_id"]))
    counter = Counter(x["context_policy"] for x in items)
    return {"schema": "p2d-dev0-shadow-compare-v1", "scope": "INVENTED_ONLY_NO_OUTCOME",
            "same_denominator": len(items), "baseline_count": len(items),
            "context_count": len(items), "context_status_counts": dict(sorted(counter.items())),
            "cases": items, "best_policy": None, "outcome_quality": None,
            "false_restriction_rate": None, "coaching_effectiveness": "NOT_ESTABLISHED",
            "no_plan_write": True, "gate2": "CLOSED"}
