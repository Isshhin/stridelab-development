"""Independent synthetic research contract model. NOT StrideLab Engine 2.0.

These small deterministic operations are validation ORACLES and sample adapters,
not calibrated athlete prescriptions. Never present their pass count as proof
that the production engine, a competitor app, or a real training outcome passed.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any

UNKNOWN = "UNKNOWN"
VALID_STATES = {"planned", "completed", "partial", "skipped", "unplanned", "unknown"}


@dataclass(frozen=True)
class Dose:
    running_km: float = 0.0
    easy_sec: int = 0
    hard_sec: int = 0
    recovery_sec: int = 0
    strength_sets: int = 0
    conditioning_sec: int = 0
    impacts: int = 0


def run_dose(steps: list[dict[str, Any]]) -> Dose:
    """Sum step-level running dose without relabeling recovery as quality work."""
    distance = easy = hard = recovery = 0
    for step in steps:
        kind = step["kind"]
        sec = step.get("sec", 0)
        km = step.get("km", 0)
        if kind not in {"easy", "hard", "recovery"}:
            raise ValueError("Unsupported run step type")
        if sec < 0 or km < 0:
            raise ValueError("Negative duration or distance")
        distance += km
        if kind == "easy":
            easy += sec
        elif kind == "hard":
            hard += sec
        else:
            recovery += sec
    return Dose(running_km=round(distance, 9), easy_sec=easy,
                hard_sec=hard, recovery_sec=recovery)


def observed_run(status: str, planned: Dose, observed: Dose | None) -> Dose | None:
    if status not in VALID_STATES:
        raise ValueError("Invalid completion status")
    if status in {"unknown", "planned"}:
        return None
    if status == "skipped":
        return Dose()
    if observed is None:
        return None
    return observed


def stress(method: str, value: float | None,
           *, threshold_verified: bool = True,
           grade_verified: bool = True,
           gps_verified: bool = True) -> dict[str, Any]:
    """Provenance wrapper: never claim a proprietary TSS implementation."""
    if method not in {"generic_hr", "generic_run_pace", "generic_power"}:
        raise ValueError("Unknown research score method")
    valid = value is not None
    if method == "generic_run_pace":
        valid = valid and threshold_verified and grade_verified and gps_verified
    if value is not None and value < 0:
        valid = False
    return {"method": method, "value": value if valid else None,
            "valid": bool(valid), "calibrated": bool(threshold_verified),
            "reason": None if valid else "NOT_COMPUTABLE"}


def generic_ema(yesterday_ctl: float, yesterday_atl: float,
                today_score: float) -> dict[str, float]:
    """Generic illustrative EMA arithmetic; NOT a claimed vendor implementation."""
    now_ctl = yesterday_ctl + (today_score - yesterday_ctl) / 42
    now_atl = yesterday_atl + (today_score - yesterday_atl) / 7
    return {"today_tsb": yesterday_ctl - yesterday_atl,
            "ctl_after_today": now_ctl,
            "atl_after_today": now_atl,
            "next_day_tsb": now_ctl - now_atl}


def capability(observations: list[dict[str, Any]], exercise: str) -> dict[str, Any]:
    logged = [o for o in observations
              if o.get("exercise") == exercise and o.get("status") == "completed"]
    return {"exercise": exercise, "known": bool(logged),
            "actual_sets": sum(o.get("actual_sets", 0) for o in logged),
            "rir": logged[-1].get("rir") if logged else None}


def allocate_sets(total: int, weights: list[int]) -> list[int]:
    if total < 0 or not weights or any(w < 0 for w in weights) or not sum(weights):
        raise ValueError("Need a nonnegative total and nonempty positive weights")
    raw = [total * w / sum(weights) for w in weights]
    base = [int(x) for x in raw]
    remaining = total - sum(base)
    rank = sorted(range(len(weights)), key=lambda i: (-(raw[i] - base[i]), i))
    for i in rank[:remaining]:
        base[i] += 1
    return base


def skill_eligible(required: list[str], mastered: set[str] | None,
                   technique_quality: str | None) -> bool:
    return (mastered is not None and technique_quality == "good"
            and all(node in mastered for node in required))


def equipment_ok(requires: set[str], available: set[str]) -> bool:
    return requires.issubset(available)


def time_feasible(durations: list[int], minutes_available: int,
                  max_sessions: int = 2) -> bool:
    return (len(durations) <= max_sessions
            and minutes_available >= 0
            and all(x >= 0 for x in durations)
            and sum(durations) <= minutes_available)


def strength_dose(sets: int | None, reps: int | None, load_kg: float | None,
                  rir: float | None = None) -> dict[str, Any]:
    return {"sets": sets, "reps": reps, "load_kg": load_kg, "rir": rir,
            "tonnage_kg": sets * reps * load_kg
            if None not in (sets, reps, load_kg) else None}


def overlap(prior: dict[str, Any], next_session: dict[str, Any]) -> set[str]:
    """Flags scheduling consideration, NOT a mandatory medical rest interval."""
    flags: set[str] = set()
    if (prior.get("movement") in {"lower_body_strength", "jump_heavy"}
            and next_session.get("kind") in {"threshold", "long_run", "hard_run"}):
        flags.add("LOWER_BODY_OVERLAP_REVIEW")
    if prior.get("movement") == "jump_heavy":
        flags.add("IMPACT_EXPOSURE")
    return flags


def deterministic_proposal(context: dict[str, Any]) -> dict[str, Any]:
    """Tiny rules-first RESEARCH STUB to verify action semantics, not safe coaching."""
    status = context.get("status", UNKNOWN)
    if context.get("pain") is True:
        action, why = "DEFER_UNKNOWN", "SELF_REPORTED_PAIN"
    elif context.get("capacity_km") is None and context.get("needs_capacity"):
        action, why = "DEFER_UNKNOWN", "CAPACITY_UNKNOWN"
    elif (context.get("planned_week_km") is not None
          and context.get("capacity_km") is not None
          and context["planned_week_km"] > context["capacity_km"]):
        action, why = "WEEK_REVISION_PROPOSED", "WEEK_CAPACITY_CONFLICT"
    elif not time_feasible(context.get("durations", [30]),
                           context.get("minutes_available", 120),
                           context.get("max_sessions", 2)):
        action, why = "SESSION_ALTERNATIVE", "SCHEDULING_CONFLICT"
    elif context.get("overlap"):
        action, why = "MOVE_SESSION_PROPOSED", "MUSCLE_OVERLAP_REVIEW"
    elif status in {"unknown", UNKNOWN}:
        action, why = "DEFER_UNKNOWN", "COMPLETION_UNOBSERVED"
    else:
        action, why = "KEEP", "NO_EVIDENCED_CHANGE"
    return {"action": action, "reason": why,
            "requires_approval": action not in {"KEEP", "DEFER_UNKNOWN"},
            "version": context.get("policy_version", "research-v0")}


def apply_revision(original: dict[str, Any], proposed: dict[str, Any],
                   decision: str) -> dict[str, Any]:
    if decision not in {"pending", "rejected", "approved"}:
        raise ValueError("Unknown decision")
    return dict(proposed) if decision == "approved" else dict(original)


def normalized_evidence_status(status: str | None) -> str:
    if status is None:
        return "unknown"
    if status not in VALID_STATES:
        raise ValueError("Invalid evidence status")
    return status


def planned_actual(planned: int, actual: int | None,
                   status: str) -> dict[str, Any]:
    if status not in VALID_STATES:
        raise ValueError("Invalid evidence status")
    credited = 0 if status == "skipped" else (
        actual if status in {"completed", "partial", "unplanned"} else None)
    return {"planned": planned, "actual": credited, "status": status}


def athlete_difficulty(workout_level: float,
                       recent_exertion: float | None,
                       history_id: str) -> dict[str, Any]:
    """Synthetic explanatory forecast, no claimed empirical calibration."""
    return {"objective_workout_level": workout_level,
            "athlete_history": history_id,
            "predicted_relative_difficulty": (
                None if recent_exertion is None
                else round(workout_level + recent_exertion / 10, 3)),
            "observed_outcome": None, "predictor_version": "toy-unvalidated-0"}


def dose_vector(run: Dose | None = None, *,
                strength: dict[str, Any] | None = None,
                conditioning: dict[str, Any] | None = None) -> dict[str, Any]:
    return {"running": run, "strength": strength,
            "conditioning": conditioning}


def score_series(score: float | None, method: str,
                 threshold_version: str | None) -> dict[str, Any]:
    return {"score": score, "method": method,
            "threshold_version": threshold_version}


def safe_merge_series(a: dict[str, Any], b: dict[str, Any]) -> bool:
    return (a["method"] == b["method"]
            and a["threshold_version"] == b["threshold_version"])


def approval_ledger(original: dict[str, Any], proposed: dict[str, Any],
                    decision: str) -> dict[str, Any]:
    after = apply_revision(original, proposed, decision)
    return {"before": dict(original), "proposal": dict(proposed),
            "after": after, "decision": decision}
