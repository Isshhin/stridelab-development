"""P2-E DEV-0 synthetic-only concurrent-training review and shadow baseline.

Review labels are not workout prescriptions or a physiological fatigue model.
No duration-to-dose conversion, fixed rest-hours rule, saved plan, or advice.
"""
from __future__ import annotations

from collections import Counter
from copy import deepcopy
from datetime import datetime
from typing import Any

from p2e_exposure import _instant, _name, summarize

SCHEMA = "p2e-dev0-coordination-v1"
CLINICAL = {"active", "unknown", "reported_cleared", "none"}
TARGETS = {"running", "strength", "calisthenics", "conditioning"}
ABSTAIN_CATEGORIES = {
    "DEFER_STOP_REQUESTED", "DEFER_CLINICAL_CONTEXT",
    "DEFER_SYMPTOM_REVIEW", "DEFER_EXPOSURE_RECONCILIATION",
    "DEFER_CAPACITY_UNKNOWN", "DEFER_LOCKED_DAY",
    "REVIEW_CAPACITY_CONFLICT", "DEFER_EQUIPMENT_UNKNOWN",
    "REVIEW_EQUIPMENT_UNAVAILABLE", "DEFER_PREREQUISITE_UNKNOWN",
    "REVIEW_UNVERIFIED_SKILL", "DEFER_PLAN_VERSION",
    "DEFER_TARGET_NOT_FOUND",
}


def _ctx(snapshot: dict[str, Any], exposure: dict[str, Any]) -> dict:
    ctx = snapshot.get("review_context")
    if not isinstance(ctx, dict) or not _name(ctx.get("target_session_id")):
        raise ValueError("INVALID_REVIEW_CONTEXT")
    if ctx.get("expected_plan_version") != exposure["plan_version"]:
        raise ValueError("STALE_PLAN_VERSION")
    if ctx.get("restriction_status") not in CLINICAL \
            or type(ctx.get("stop_requested")) is not bool:
        raise ValueError("RESTRICTION_OR_STOP_STATUS_INVALID")
    budgets = ctx.get("available_minutes_by_date")
    if not isinstance(budgets, dict):
        raise ValueError("DAY_BUDGET_MAP_REQUIRED")
    for date, minutes in budgets.items():
        try:
            if not isinstance(date, str) or datetime.fromisoformat(date).date().isoformat() != date:
                raise ValueError()
        except (ValueError, TypeError):
            raise ValueError("DAY_BUDGET_DATE_INVALID") from None
        if type(minutes) is not int or minutes < 0:
            raise ValueError("DAY_BUDGET_INVALID")
    count = ctx.get("max_sessions_per_day")
    if type(count) is not int or count < 1:
        raise ValueError("SESSION_COUNT_CAP_INVALID")
    locks = ctx.get("locked_dates", [])
    if not isinstance(locks, list):
        raise ValueError("LOCKED_DATES_INVALID")
    for day in locks:
        if not isinstance(day, str):
            raise ValueError("LOCKED_DATES_INVALID")
        try:
            if datetime.fromisoformat(day).date().isoformat() != day:
                raise ValueError()
        except ValueError as exc:
            raise ValueError("LOCKED_DATES_INVALID") from exc
    if len(set(locks)) != len(locks):
        raise ValueError("DUPLICATE_LOCKED_DATE")
    if ctx.get("equipment_status", "available") not in ("available", "unavailable", "unknown"):
        raise ValueError("EQUIPMENT_STATUS_INVALID")
    if ctx.get("prerequisites_verified", True) is not None and type(
            ctx.get("prerequisites_verified", True)) is not bool:
        raise ValueError("SKILL_PREREQUISITES_INVALID")
    return deepcopy(ctx)


def _overlap(target: dict, roster: list[dict]) -> dict:
    """Describe only verified same-calendar-day demand, never infer fatigue."""
    target_day = target["scheduled_local_date"]
    involved = []
    possible = []
    target_run = (target["discipline"] == "running"
                  and target["intent"] in {"threshold", "intervals", "long_run"})
    for row in roster:
        if row["session_id"] == target["session_id"] or row["scheduled_local_date"] != target_day:
            continue
        dose = row["native_dose"]
        if row["status"] not in ("completed", "partial") or dose is None:
            possible.append(row["session_id"])
            continue
        if target_run:
            if row["discipline"] == "strength" and ("lower" in dose["muscle_roles"] or
                     "full_body" in dose["muscle_roles"]) and dose["reported_demand"] == "high":
                involved.append({"session_id": row["session_id"], "event_ids": row["evidence_ids"],
                                 "reason": "REPORTED_HIGH_LOWER_STRENGTH"})
            elif row["discipline"] == "calisthenics" and dose["muscle_region"] == "lower" \
                    and dose["reported_demand"] == "high":
                involved.append({"session_id": row["session_id"], "event_ids": row["evidence_ids"],
                                 "reason": "REPORTED_HIGH_LOWER_SKILL"})
            elif row["discipline"] == "conditioning" and dose["impact_profile"] == "jump_heavy":
                involved.append({"session_id": row["session_id"], "event_ids": row["evidence_ids"],
                                 "reason": "REPORTED_JUMP_HEAVY_CONDITIONING"})
        elif target["discipline"] in {"strength", "calisthenics", "conditioning"}:
            if row["discipline"] == "running" and row["intent"] in {"threshold", "intervals", "long_run"}:
                involved.append({"session_id": row["session_id"], "event_ids": row["evidence_ids"],
                                 "reason": "RECORDED_PRIORITY_RUNNING_EXPOSURE"})
    return {"observed": sorted(involved, key=lambda v: v["session_id"]),
            "not_observed": sorted(possible),
            "interpretation": "SAME_DAY_CONTEXT_NOT_FATIGUE_OR_CAUSATION"}


def review(snapshot: dict[str, Any]) -> dict[str, Any]:
    exposure = summarize(snapshot)
    ctx = _ctx(snapshot, exposure)
    roster = exposure["roster"]
    selected = [r for r in roster if r["session_id"] == ctx["target_session_id"]]
    if not selected:
        raise ValueError("TARGET_SESSION_NOT_FOUND")
    target = selected[0]
    if target["discipline"] not in TARGETS:
        raise ValueError("UNSUPPORTED_TARGET_DISCIPLINE")
    date = target["scheduled_local_date"]
    day_sessions = [r for r in roster if r["scheduled_local_date"] == date]
    used_minutes = sum(r["duration_min"] for r in day_sessions)
    overlap = _overlap(target, roster)
    with_symptoms = sorted(e for r in roster if r["symptom_report"] == "pain"
                           for e in r["evidence_ids"])
    # Hard boundaries dominate capacity/overlap and unknown training.
    if ctx["stop_requested"]:
        category, reason = "DEFER_STOP_REQUESTED", "ATHLETE_REQUESTED_STOP"
    elif ctx["restriction_status"] != "none":
        category, reason = "DEFER_CLINICAL_CONTEXT", "CLINICAL_STATUS_NOT_CLEARED_BY_APP"
    elif with_symptoms:
        category, reason = "DEFER_SYMPTOM_REVIEW", "REPORTED_PAIN_NEEDS_HUMAN_REVIEW"
    elif not exposure["past_coverage_complete"]:
        category, reason = "DEFER_EXPOSURE_RECONCILIATION", "PRIOR_EXPOSURE_NOT_FULLY_KNOWN"
    elif date not in ctx["available_minutes_by_date"]:
        category, reason = "DEFER_CAPACITY_UNKNOWN", "NO_REGISTERED_DAY_BUDGET"
    elif date in ctx.get("locked_dates", []):
        category, reason = "DEFER_LOCKED_DAY", "DAY_LOCKED"
    elif used_minutes > ctx["available_minutes_by_date"][date] \
            or len(day_sessions) > ctx["max_sessions_per_day"]:
        category, reason = "REVIEW_CAPACITY_CONFLICT", "PLANNED_DAY_EXCEEDS_CONSTRAINT"
    elif target["discipline"] in {"strength", "calisthenics"} \
            and ctx.get("equipment_status", "available") != "available":
        category = ("DEFER_EQUIPMENT_UNKNOWN" if ctx["equipment_status"] == "unknown"
                    else "REVIEW_EQUIPMENT_UNAVAILABLE")
        reason = "EQUIPMENT_FEASIBILITY_NOT_ESTABLISHED"
    elif target["discipline"] == "calisthenics" and ctx.get("prerequisites_verified", True) is not True:
        category = ("DEFER_PREREQUISITE_UNKNOWN" if ctx["prerequisites_verified"] is None
                    else "REVIEW_UNVERIFIED_SKILL")
        reason = "FORM_OR_PREREQUISITE_NOT_CONFIRMED"
    elif overlap["observed"]:
        category, reason = "REVIEW_OBSERVED_OVERLAP", "PRIORITY_SESSION_AND_REPORTED_DEMAND_SAME_DAY"
    elif overlap["not_observed"]:
        category, reason = "REVIEW_POSSIBLE_OVERLAP", "SAME_DAY_OTHER_EXPOSURES_NOT_VERIFIED"
    else:
        category, reason = "NO_STRUCTURAL_CONFLICT_OBSERVED", "NO_SYNTHETIC_CONFLICT_IN_REGISTER"
    return {
        "schema": SCHEMA, "athlete_id": exposure["athlete_id"],
        "opportunity_id": exposure["opportunity_id"], "as_of": exposure["as_of"],
        "plan_id": exposure["plan_id"], "plan_version": exposure["plan_version"],
        "target_session_id": target["session_id"], "target_discipline": target["discipline"],
        "review_category": category, "reason": reason,
        "day_scheduled_sessions": len(day_sessions),
        "day_planned_minutes": used_minutes,
        "overlap": overlap, "symptom_evidence_ids": with_symptoms,
        "native_exposure": deepcopy(exposure["known_native_totals"]),
        "source_evidence_ids": sorted(x for r in roster for x in r["evidence_ids"]),
        "shadow_status": "RESEARCH_ONLY_NO_PLAN_EDIT",
        "candidate_plan": None, "proposed_dose": None,
        "predicted_performance": None, "risk_probability": None,
        "auto_skill_mastery": False, "clinical_clearance": False,
        "applied": False, "requires_separate_approval": True,
        "fixed_recovery_hours": None, "coaching_authority": "NONE_SYNTHETIC_RESEARCH",
        "gate2": "CLOSED",
    }


def compare_shadow(snapshots: list[dict[str, Any]]) -> dict:
    """Same-denominator calendar-only vs contextual *review*; not effectiveness."""
    if not isinstance(snapshots, list):
        raise ValueError("SNAPSHOTS_LIST_REQUIRED")
    seen: set[tuple[str, str]] = set()
    records = []
    for s in snapshots:
        v = review(s)
        key = (v["athlete_id"], v["opportunity_id"])
        if key in seen:
            raise ValueError("DUPLICATE_OPPORTUNITY")
        seen.add(key)
        ctx = s["review_context"]
        if v["review_category"].startswith("DEFER_STOP") or v["review_category"] in {
                "DEFER_CLINICAL_CONTEXT", "DEFER_SYMPTOM_REVIEW"}:
            baseline = "HOLD_NO_APPROVAL_TO_STOP"
        elif v["review_category"] in {"REVIEW_CAPACITY_CONFLICT", "DEFER_CAPACITY_UNKNOWN",
                                       "DEFER_LOCKED_DAY"}:
            baseline = "REVIEW_CALENDAR_CONSTRAINT"
        else:
            baseline = "CALENDAR_ONLY_NO_PHYSIOLOGICAL_INFERENCE"
        records.append({"athlete_id": v["athlete_id"],
                        "opportunity_id": v["opportunity_id"], "baseline": baseline,
                        "contextual_review": v["review_category"],
                        "reason": v["reason"],
                        "native_exposure": v["native_exposure"],
                        "overlap": v["overlap"], "applied": False})
    records.sort(key=lambda x: (x["athlete_id"], x["opportunity_id"]))
    return {"schema": "p2e-dev0-shadow-comparison-v1",
            "status": "SYNTHETIC_POLICY_STRUCTURE_ONLY",
            "opportunity_count": len(records),
            "common_denominator": len(records),
            "case_count_by_category": dict(sorted(Counter(
                r["contextual_review"] for r in records).items())),
            "cases": records, "best_policy": None, "effectiveness": None,
            "risk_probability": None, "new_plan": None, "applied": False,
            "coaching_authority": "NONE_SYNTHETIC_RESEARCH", "gate2": "CLOSED"}
