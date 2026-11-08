"""P2-C DEV-0: non-executable synthetic review of progression and re-entry.

Returns an evidence-grounded review category, NEVER a dose, medical clearance,
automatic revision, risk score, or recommendation for a real athlete.
"""
from __future__ import annotations

from copy import deepcopy
from typing import Any

from p2c_exposure import _id, summarize

SCHEMA = "p2c-dev0-reentry-v1"
BREAKS = {"none", "isolated_confirmed_miss", "confirmed_absence", "unverified_gap"}
REASONS = {"none", "logistical", "travel", "planned_rest", "race", "illness", "injury", "unknown"}
RESTRICTIONS = {"none", "active", "unknown", "reported_cleared"}
INTENTS = {"maintain", "rebuild", "restore", "progress"}
TARGETS = {"easy", "long_run", "threshold", "intervals"}


def _category(category: str, reason: str, evidence: list[str] | None = None) -> dict:
    return {"review_category": category, "reason": reason,
            "evidence_ids": sorted(evidence or []), "dose_change": None,
            "plan_change": None, "applied": False, "requires_separate_approval": True,
            "clinical_clearance": False, "prediction_status": "NOT_VALIDATED_NO_MODEL",
            "risk_probability": None}


def _registered_restoration_gate(context: dict[str, Any], roster: list[dict], target: str) -> list[str] | None:
    """Synthetic gate is a reference to audited same-type events, not a clinical rule."""
    gates = context.get("registered_restoration_gates", {})
    if not isinstance(gates, dict):
        raise ValueError("RESTORATION_GATE_MAP_REQUIRED")
    gate = gates.get(target)
    if not isinstance(gate, dict) or gate.get("status") != "reviewed_synthetic":
        return None
    if not _id(gate.get("review_protocol_id")):
        return None
    ids = gate.get("evidence_ids")
    if not isinstance(ids, list) or len(ids) < 2 or any(not _id(x) for x in ids) or len(ids) != len(set(ids)):
        return None
    epoch = context.get("current_exposure_epoch")
    if not _id(epoch) or gate.get("exposure_epoch") != epoch:
        return None
    matches = {r["evidence_ids"][0]: r for r in roster if r["status"] == "completed"
               and len(r["evidence_ids"]) == 1 and r["kind"] == target
               and r.get("exposure_epoch") == epoch
               and r["response"] == "tolerated" and r["next_day_response"] == "tolerated"}
    if not set(ids).issubset(matches):
        return None
    from datetime import datetime
    if len({datetime.fromisoformat(matches[key]["observed_at"]).date() for key in ids}) < 2:
        return None
    return sorted(ids)


def review(snapshot: dict[str, Any]) -> dict[str, Any]:
    """Apply hard safety/uncertainty precedence to an invented training roster.

    All outputs are research review labels; no workouts are authorized.
    """
    if not isinstance(snapshot, dict):
        raise ValueError("SNAPSHOT_MAP_REQUIRED")
    exposure = summarize(snapshot)
    ctx = snapshot.get("review_context")
    if not isinstance(ctx, dict):
        raise ValueError("REVIEW_CONTEXT_REQUIRED")
    if ctx.get("expected_plan_version") != exposure["plan_version"]:
        raise ValueError("STALE_PLAN_VERSION")
    if (ctx.get("break_status") not in BREAKS or ctx.get("break_reason") not in REASONS
            or ctx.get("restriction_status") not in RESTRICTIONS
            or ctx.get("intent") not in INTENTS or ctx.get("target_session") not in TARGETS):
        raise ValueError("INVALID_REVIEW_CONTEXT")
    if ctx.get("break_status") == "none" and ctx.get("break_reason") not in {"none", "unknown"}:
        raise ValueError("BREAK_REASON_WITHOUT_BREAK")
    if ctx.get("break_status") != "none" and ctx.get("break_reason") == "none":
        raise ValueError("BREAK_REASON_REQUIRED")
    roster = exposure["roster"]
    performed = [r for r in roster if r["status"] in {"completed", "partial"}]
    verified_ids = sorted({event for r in performed for event in r["evidence_ids"]})
    status = ctx["restriction_status"]
    reason = ctx["break_reason"]
    target = ctx["target_session"]
    recent_ids = ctx.get("recent_response_event_ids", [])
    if (not isinstance(recent_ids, list) or any(not _id(x) for x in recent_ids)
            or len(set(recent_ids)) != len(recent_ids)):
        raise ValueError("RECENT_RESPONSE_IDS_INVALID")
    by_id = {e: r for r in performed for e in r["evidence_ids"]}
    # The caller must not claim an excluded, missing or future event is recent.
    if not set(recent_ids).issubset(by_id):
        raise ValueError("RECENT_RESPONSE_NOT_VERIFIED")

    if status in {"active", "unknown"} or reason in {"illness", "injury"}:
        outcome = _category("DEFER_CLINICAL_REVIEW", "RESTRICTION_OR_CLINICAL_CONTEXT")
    elif ctx["break_status"] == "unverified_gap" or (ctx["break_status"] != "none" and reason == "unknown"):
        outcome = _category("DEFER_RECONCILIATION", "BREAK_REASON_OR_COVERAGE_UNKNOWN")
    elif not exposure["coverage_complete"]:
        outcome = _category("DEFER_RECONCILIATION", "EXPOSURE_NOT_FULLY_RECONCILED")
    elif reason == "race":
        outcome = _category("RACE_RECOVERY_REVIEW", "POST_RACE_RESPONSE_UNVERIFIED", verified_ids)
    elif any(by_id[e]["response"] == "difficult" or by_id[e]["next_day_response"] == "difficult"
             for e in recent_ids):
        outcome = _category("REDUCE_OR_DELOAD_REVIEW", "REPORTED_DIFFICULT_RESPONSE", recent_ids)
    elif ctx["break_status"] == "confirmed_absence" and reason == "planned_rest":
        outcome = _category("MAINTAIN_REVIEW", "PLANNED_REST_NOT_DETRAINING", verified_ids)
    elif ctx["break_status"] == "confirmed_absence":
        outcome = _category("REBUILD_EXPOSURE_REVIEW", "CONFIRMED_NONCLINICAL_BREAK", verified_ids)
    elif ctx["intent"] == "restore" and target in {"long_run", "threshold", "intervals"}:
        evidence = _registered_restoration_gate(ctx, roster, target)
        outcome = (_category("RESTORE_LONG_RUN_REVIEW" if target == "long_run"
                             else "RESTORE_QUALITY_REVIEW", "SYNTHETIC_GATE_REVIEWED", evidence)
                   if evidence else _category("DEFER_SESSION_TYPE", "RESTORATION_EVIDENCE_NOT_REGISTERED"))
    elif ctx["intent"] == "progress":
        if not performed:
            outcome = _category("DEFER_INSUFFICIENT_EXPOSURE", "NO_VERIFIED_EXPOSURE")
        elif not recent_ids or any(by_id[e]["response"] != "tolerated" or
                                   by_id[e]["next_day_response"] != "tolerated"
                                   for e in recent_ids):
            outcome = _category("DEFER_FOLLOWUP_UNVERIFIED", "NO_CONFIRMED_TOLERANCE")
        else:
            outcome = _category("PROGRESS_ONE_DIMENSION_REVIEW", "NO_CALIBRATED_DOSE_RULE", recent_ids)
    else:
        outcome = _category("MAINTAIN_REVIEW", "NO_AUTHORIZED_CHANGE", verified_ids)

    output = {"schema": SCHEMA, "athlete_id": exposure["athlete_id"],
              "as_of": exposure["as_of"], "plan_version": exposure["plan_version"],
              "target_session": target, "break_status": ctx["break_status"],
              "break_reason": reason, "exposure": deepcopy(exposure),
              "session_concentration": {
                  "max_verified_run_km": exposure["max_verified_run_km"],
                  "known_running_km": exposure["known_running_km"],
                  "comparison_status": ("DESCRIPTIVE_ONLY" if exposure["coverage_complete"]
                                        else "INCOMPLETE_COVERAGE")},
              "coaching_authority": "NONE_SYNTHETIC_RESEARCH",
              "gate2": "CLOSED"}
    output.update(outcome)
    return output
