"""Batch 8B: independent, executable RESEARCH policy candidates B0 and H1.

These are newly written prototypes, NOT the user's pre-existing local lab engine,
and not production Engine 1.0, 1.5 or 2.0. No physiological optimum, predicted
fitness improvement, or medical clearance is estimated.

Policy input: an immutable JSON-compatible synthetic athlete state.
Policy output: observed dose/provenance, evidence flags and a *proposed* decision.
No input is mutated and no proposed change is applied automatically.
"""
from __future__ import annotations

from copy import deepcopy
from typing import Any


def _steps(steps: list[dict[str, Any]] | None) -> dict[str, Any] | None:
    if steps is None:
        return None
    result = {"km": 0.0, "easy_s": 0, "hard_s": 0, "recovery_s": 0}
    for step in steps:
        kind = step["kind"]
        if kind not in ("easy", "hard", "recovery"):
            raise ValueError(f"Invalid running step kind: {kind}")
        seconds = step.get("seconds")
        km = step.get("km")
        if seconds is None or km is None or seconds < 0 or km < 0:
            raise ValueError("Run step must include nonnegative seconds and km")
        result["km"] += km
        result[{"easy": "easy_s", "hard": "hard_s",
                "recovery": "recovery_s"}[kind]] += seconds
    result["km"] = round(result["km"], 6)
    return result


def _observation(state: dict[str, Any]) -> dict[str, Any]:
    session = state.get("session", {})
    status = session.get("status", "unknown")
    if status not in ("completed", "partial", "skipped", "planned",
                      "unplanned", "unknown"):
        raise ValueError(f"Invalid completion status: {status}")
    planned = _steps(session.get("planned_steps"))
    actual = (_steps(session.get("actual_steps"))
              if status in ("completed", "partial", "unplanned") else None)
    if status == "skipped":
        actual = {"km": 0.0, "easy_s": 0, "hard_s": 0, "recovery_s": 0}
    strength = state.get("strength", {})
    conditioning = state.get("conditioning", {})
    actual_sets = strength.get("actual_sets")
    if strength.get("status") in ("skipped",):
        actual_sets = 0
    if strength.get("status") in ("unknown", None, "planned"):
        actual_sets = None
    actual_rounds = conditioning.get("actual_rounds")
    if conditioning.get("status") == "skipped":
        actual_rounds = 0
    if conditioning.get("status") in ("planned", "unknown", None):
        actual_rounds = None
    return {
        "status": status,
        "planned_run": planned,
        "actual_run": actual,
        "strength_actual_sets": actual_sets,
        "strength_rir": strength.get("rir"),
        "strength_load_kg": strength.get("load_kg"),
        "strength_reps": strength.get("reps"),
        "conditioning_actual_rounds": actual_rounds,
        "conditioning_impact": conditioning.get("impacts"),
        "route_class": state.get("environment", {}).get("route_class"),
        "descent_m": state.get("environment", {}).get("descent_m"),
        "temperature_c": state.get("environment", {}).get("temperature_c"),
        "hr_bpm": session.get("hr_bpm"),
        "hr_quality": session.get("hr_quality", "unknown"),
        "timer_seconds": session.get("timer_seconds"),
        "elapsed_seconds": session.get("elapsed_seconds"),
        "ability_known": bool(state.get("capabilities", {}).get("supported", False)),
        "skill_mastered": list(state.get("skill", {}).get("mastered", [])),
        "calibration": deepcopy(state.get("calibration")),
    }


def _monitor(state: dict[str, Any]) -> dict[str, Any]:
    obs = state.get("stress", {})
    method = obs.get("method")
    value = obs.get("score")
    result: dict[str, Any] = {
        "method": method, "value": None, "reason": "UNKNOWN",
        "calibration_version": obs.get("calibration_version"),
    }
    if method is None:
        return result
    if method not in ("generic_hr", "generic_pace", "generic_power"):
        result["reason"] = "UNSUPPORTED_METHOD"
        return result
    if value is None:
        result["reason"] = "NOT_RECORDED"
        return result
    if value < 0:
        result["reason"] = "INVALID_VALUE"
        return result
    if method == "generic_pace" and not (
        obs.get("threshold_verified", False)
        and obs.get("gps_verified", False)
        and obs.get("grade_verified", False)
    ):
        result["reason"] = "NOT_COMPUTABLE"
        return result
    result["value"] = value
    result["reason"] = "OBSERVED_SCORE_WITH_PROVENANCE"
    return result


def _indicators(state: dict[str, Any]) -> dict[str, Any]:
    day = state.get("day", {})
    durations = day.get("durations_m", [])
    limit = day.get("max_sessions", 2)
    available = day.get("minutes_available")
    time_feasible = bool(available is not None and all(d >= 0 for d in durations)
                         and len(durations) <= limit and sum(durations) <= available)
    athlete = state.get("athlete", {})
    weekly = state.get("weekly", {})
    cap = athlete.get("verified_capacity_km")
    proposed_km = weekly.get("planned_km")
    above_cap = (cap is not None and proposed_km is not None and proposed_km > cap)
    recent = state.get("recent", [])
    target = state.get("next", {})
    relevant_lower = any(
        s.get("movement") in ("lower_body_strength", "jump_heavy")
        for s in recent
        if s.get("status") == "completed"
    )
    difficult_run = target.get("kind") in ("threshold", "long_run", "hard_run")
    overlap = bool(relevant_lower and difficult_run)
    equipment = state.get("equipment", {})
    need = set(equipment.get("required", []))
    available_items = set(equipment.get("available", []))
    equipment_known = "available" in equipment
    equipment_missing = bool(equipment_known and not need.issubset(available_items))
    skill = state.get("skill", {})
    req = set(skill.get("required", []))
    done = set(skill.get("mastered", []))
    skill_unverified = bool(req and (not req.issubset(done) or
                            skill.get("form_quality") != "good"))
    return {
        "time_feasible": time_feasible,
        "session_count": len(durations),
        "daily_minutes": sum(durations),
        "capacity_known": cap is not None,
        "weekly_over_cap": above_cap,
        "overlap_review": overlap,
        "equipment_missing": equipment_missing,
        "skill_unverified": skill_unverified,
        "pain_reported": state.get("feedback", {}).get("pain") is True,
    }


def _ema(state: dict[str, Any]) -> dict[str, float] | None:
    temporal = state.get("temporal")
    if not temporal:
        return None
    ctl = temporal.get("yesterday_ctl")
    atl = temporal.get("yesterday_atl")
    today = temporal.get("today_score")
    if None in (ctl, atl, today):
        return None
    return {
        "today_tsb": ctl - atl,
        "ctl_after_today": ctl + (today - ctl) / 42,
        "atl_after_today": atl + (today - atl) / 7,
        "tomorrow_tsb": (ctl + (today - ctl) / 42)
                        - (atl + (today - atl) / 7),
        "method": "generic_ema_not_trainingpeaks",
    }


def _strength_allocation(state: dict[str, Any]) -> dict[str, Any] | None:
    group = state.get("muscle_sets", {})
    if "total" not in group or "weights" not in group:
        return None
    total = group["total"]
    weights = group["weights"]
    if total < 0 or not weights or sum(weights) <= 0 or any(x < 0 for x in weights):
        raise ValueError("Invalid synthetic muscle allocation")
    base = [total * w // sum(weights) for w in weights]
    residual = [total * w % sum(weights) for w in weights]
    for i in sorted(range(len(base)), key=lambda x: (-residual[x], x))[:total - sum(base)]:
        base[i] += 1
    return {"group_sets": total, "exercise_sets": base}


def _common(state: dict[str, Any], policy_id: str) -> dict[str, Any]:
    return {
        "policy_id": policy_id,
        "observation": _observation(state),
        "indicators": _indicators(state),
        "monitor": _monitor(state),
        "temporal": _ema(state),
        "strength_allocation": _strength_allocation(state),
        "forecast": {
            "workout_level": state.get("forecast", {}).get("workout_level"),
            "athlete_history": state.get("forecast", {}).get("athlete_history"),
            "source_version": state.get("forecast", {}).get("source_version"),
            "predicted_difficulty": None,
            "prediction_status": "NOT_VALIDATED_NO_MODEL",
            "actual_outcome": None,
        },
        "revision": {"scope": "none", "before": deepcopy(state.get("weekly", {})),
                     "after": deepcopy(state.get("weekly", {})),
                     "applied": False, "requires_approval": False},
        "action": "KEEP", "reason": "NO_CONFLICT",
    }


def baseline_b0(state: dict[str, Any]) -> dict[str, Any]:
    """Minimal deterministic capacity + day feasibility comparator."""
    output = _common(state, "B0_distance_capacity_v1")
    flags = output["indicators"]
    if flags["weekly_over_cap"]:
        output.update(action="PROPOSE_WEEK_REDUCTION", reason="CAPACITY_CAP")
    elif not flags["time_feasible"]:
        output.update(action="PROPOSE_SESSION_ADJUSTMENT", reason="TIME_BUDGET")
    if output["action"] != "KEEP":
        output["revision"].update(
            scope="proposal", requires_approval=True)
    return output


def hybrid_h1(state: dict[str, Any]) -> dict[str, Any]:
    """Type-aware proposal rules. Not a calibrated coach or release candidate."""
    output = _common(state, "H1_constrained_multidose_v1")
    flags = output["indicators"]
    sess = output["observation"]
    weekly = state.get("weekly", {})
    if flags["pain_reported"]:
        action, reason = "DEFER_UNKNOWN", "PAIN_NEEDS_HUMAN_REVIEW"
    elif flags["weekly_over_cap"]:
        action, reason = "PROPOSE_WEEK_REDUCTION", "CAPACITY_CAP"
        output["revision"]["candidate_week_km"] = state["athlete"]["verified_capacity_km"]
    elif not flags["time_feasible"]:
        action, reason = "PROPOSE_SESSION_ADJUSTMENT", "TIME_BUDGET"
    elif flags["equipment_missing"]:
        action, reason = "PROPOSE_EXERCISE_SUBSTITUTION", "EQUIPMENT_MISSING"
    elif flags["skill_unverified"]:
        action, reason = "DEFER_UNKNOWN", "SKILL_PREREQUISITE_OR_FORM"
    elif state.get("return_from_break", False) and state.get("request_adaptation", False):
        action, reason = "DEFER_UNKNOWN", "RETURN_CAPACITY_UNVERIFIED"
    elif flags["overlap_review"]:
        action, reason = "PROPOSE_MOVE_SESSION", "LOWER_BODY_OVERLAP_REVIEW"
    elif sess["status"] == "unknown" and state.get("request_adaptation", False):
        action, reason = "DEFER_UNKNOWN", "MISSING_ACTIVITY_IS_NOT_SKIPPED"
    elif state.get("request_adaptation", False) and not flags["capacity_known"]:
        action, reason = "DEFER_UNKNOWN", "UNVERIFIED_CAPACITY"
    elif (state.get("next", {}).get("kind") == "threshold"
          and state.get("recent_unplanned_hard", False)):
        action, reason = "PROPOSE_MOVE_SESSION", "RECENT_UNPLANNED_HARD_WORK"
    else:
        action, reason = "KEEP", "NO_SUPPORTED_CHANGE"
    output["action"] = action
    output["reason"] = reason
    if action.startswith("PROPOSE"):
        output["revision"].update(scope="proposal", requires_approval=True)
    # Unapproved proposals never change actual or planned dose.
    assert output["revision"]["before"] == weekly
    assert output["revision"]["after"] == weekly
    return output


def run_policy(policy_id: str, state: dict[str, Any]) -> dict[str, Any]:
    snapshot = deepcopy(state)
    if policy_id == "B0":
        output = baseline_b0(snapshot)
    elif policy_id == "H1":
        output = hybrid_h1(snapshot)
    else:
        raise ValueError("Unknown research policy")
    if snapshot != state:
        raise AssertionError("Policy modified its input state")
    return output
