"""Batch 8E: explicit workout-to-week change PROPOSAL contract.

This module does not decide an optimal training dose or generate progressions.
It validates an already-specified move or a non-increasing running-step
replacement, builds an auditable candidate plan, and requires external explicit
approval before anything could be saved. The original plan is never mutated.

No medical recovery constraints or proprietary coaching formula are inferred.
"""
from __future__ import annotations

from copy import deepcopy
import hashlib
import json
from typing import Any


class ProposalBlocked(ValueError):
    def __init__(self, reason: str):
        self.reason = reason
        super().__init__(reason)


DAYS = ("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
STEP_KINDS = {"easy", "hard", "recovery"}


def fingerprint(value: Any) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True,
                                     separators=(",", ":"), allow_nan=False).encode()).hexdigest()


def _nonnegative_number(value: Any) -> bool:
    return type(value) in (float, int) and value >= 0


def steps_summary(steps: list[dict[str, Any]]) -> dict[str, Any]:
    if not isinstance(steps, list) or not steps:
        raise ProposalBlocked("RUN_STEPS_REQUIRED")
    output = {"km": 0.0, "easy_s": 0, "hard_s": 0, "recovery_s": 0}
    for step in steps:
        if not isinstance(step, dict) or step.get("kind") not in STEP_KINDS:
            raise ProposalBlocked("RUN_STEP_TYPE_INVALID")
        km, seconds = step.get("km"), step.get("seconds")
        if not _nonnegative_number(km) or not _nonnegative_number(seconds):
            raise ProposalBlocked("RUN_STEP_DOSE_INVALID")
        if int(seconds) != seconds:
            raise ProposalBlocked("RUN_STEP_SECONDS_NOT_INTEGER")
        output["km"] += km
        output[{"easy": "easy_s", "hard": "hard_s",
                "recovery": "recovery_s"}[step["kind"]]] += int(seconds)
    output["km"] = round(output["km"], 6)
    return output


def plan_metrics(plan: dict[str, Any]) -> dict[str, Any]:
    sessions = plan.get("sessions")
    if not isinstance(sessions, list) or not sessions:
        raise ProposalBlocked("EMPTY_PLAN")
    ids: set[str] = set()
    days: dict[str, dict[str, int]] = {
        d: {"count": 0, "minutes": 0} for d in DAYS
    }
    metrics = {"running_km": 0.0, "run_hard_s": 0, "run_easy_s": 0,
               "run_recovery_s": 0, "strength_sessions": 0,
               "sessions": len(sessions), "days": days}
    for s in sessions:
        if not isinstance(s, dict) or not isinstance(s.get("id"), str) or not s["id"]:
            raise ProposalBlocked("SESSION_ID_INVALID")
        if s["id"] in ids:
            raise ProposalBlocked("DUPLICATE_SESSION_ID")
        ids.add(s["id"])
        if s.get("day") not in DAYS:
            raise ProposalBlocked("SESSION_DAY_INVALID")
        if not _nonnegative_number(s.get("duration_m")):
            raise ProposalBlocked("SESSION_DURATION_INVALID")
        days[s["day"]]["count"] += 1
        days[s["day"]]["minutes"] += s["duration_m"]
        if s.get("discipline") == "running":
            dose = steps_summary(s.get("planned_steps"))
            metrics["running_km"] += dose["km"]
            metrics["run_hard_s"] += dose["hard_s"]
            metrics["run_easy_s"] += dose["easy_s"]
            metrics["run_recovery_s"] += dose["recovery_s"]
        elif s.get("discipline") == "strength":
            metrics["strength_sessions"] += 1
        else:
            raise ProposalBlocked("UNSUPPORTED_DISCIPLINE")
    metrics["running_km"] = round(metrics["running_km"], 6)
    return metrics


def _constraints(plan: dict[str, Any], constraint: dict[str, Any],
                 metrics: dict[str, Any]) -> None:
    max_sessions = constraint.get("max_sessions_per_day")
    if type(max_sessions) is not int or max_sessions < 1:
        raise ProposalBlocked("CAPACITY_LIMIT_UNDEFINED")
    budgets = constraint.get("available_minutes_by_day")
    if not isinstance(budgets, dict) or set(budgets) != set(DAYS):
        raise ProposalBlocked("DAY_BUDGET_UNVERIFIED")
    locked = constraint.get("locked_days", [])
    if not isinstance(locked, list) or any(day not in DAYS for day in locked):
        raise ProposalBlocked("LOCKED_DAYS_INVALID")
    for day in DAYS:
        budget = budgets[day]
        if not _nonnegative_number(budget):
            raise ProposalBlocked("DAY_BUDGET_INVALID")
        if day in locked and metrics["days"][day]["count"]:
            raise ProposalBlocked("LOCKED_DAY_SCHEDULED")
        if metrics["days"][day]["count"] > max_sessions:
            raise ProposalBlocked("DAILY_SESSION_LIMIT")
        if metrics["days"][day]["minutes"] > budget:
            raise ProposalBlocked("DAY_MINUTES_EXCEEDED")
    limit = constraint.get("verified_weekly_running_cap_km")
    if limit is not None and (
        not _nonnegative_number(limit) or metrics["running_km"] > limit + 1e-6
    ):
        raise ProposalBlocked("WEEKLY_RUNNING_CAP")


def draft_revision(plan: dict[str, Any], request: dict[str, Any],
                   constraints: dict[str, Any]) -> dict[str, Any]:
    """Produce a concrete, versioned, *pending* proposal or an explicit blocker.

    Caller must separately authenticate the athlete's decision, check sources,
    and commit via a durable store. In particular, a model action such as
    PROPOSE_MOVE_SESSION with no specific target day is not an executable edit.
    """
    if type(plan.get("version")) is not int or plan["version"] < 1:
        raise ProposalBlocked("PLAN_VERSION_UNKNOWN")
    if request.get("expected_version") != plan["version"]:
        raise ProposalBlocked("STALE_PLAN_VERSION")
    if request.get("evidence_status") not in ("verified", "athlete_confirmed"):
        raise ProposalBlocked("EVIDENCE_UNVERIFIED")
    refs = request.get("evidence_refs")
    if not isinstance(refs, list) or not refs or any(
        not isinstance(x, str) or not x for x in refs
    ):
        raise ProposalBlocked("EVIDENCE_REFERENCE_REQUIRED")
    if not isinstance(request.get("reason"), str) or not request["reason"].strip():
        raise ProposalBlocked("EXPLANATION_REQUIRED")
    if request.get("source") not in ("athlete_request", "research_policy"):
        raise ProposalBlocked("CHANGE_SOURCE_UNKNOWN")

    before = deepcopy(plan)
    prior = plan_metrics(before)
    _constraints(before, constraints, prior)
    after = deepcopy(before)
    targets = [s for s in after["sessions"] if s["id"] == request.get("session_id")]
    if len(targets) != 1:
        raise ProposalBlocked("SESSION_NOT_FOUND")
    target = targets[0]
    kind = request.get("kind")
    if kind == "MOVE_SESSION":
        chosen_day = request.get("target_day")
        if chosen_day not in DAYS:
            raise ProposalBlocked("EXPLICIT_TARGET_DAY_REQUIRED")
        if chosen_day == target["day"]:
            raise ProposalBlocked("NO_OP_CHANGE")
        target["day"] = chosen_day
    elif kind == "REPLACE_RUN_STEPS":
        if target.get("discipline") != "running":
            raise ProposalBlocked("RUNNING_ONLY_STEP_REPLACEMENT")
        specified = request.get("target_steps")
        revised_dose = steps_summary(specified)
        original_dose = steps_summary(target["planned_steps"])
        # No automatic increase: explicit athlete-directed target can only
        # reduce or retain the work dose in this research boundary.
        if (revised_dose["hard_s"] > original_dose["hard_s"]
                or revised_dose["km"] > original_dose["km"] + 1e-6):
            raise ProposalBlocked("AUTOMATIC_DOSE_INCREASE_NOT_AUTHORIZED")
        target["planned_steps"] = deepcopy(specified)
        # Duration is provided by the athlete/policy request, not inferred
        # from pace, route, or physiology.
        target_duration = request.get("target_duration_m")
        if not _nonnegative_number(target_duration):
            raise ProposalBlocked("TARGET_DURATION_REQUIRED")
        if target_duration * 60 < (
                revised_dose["hard_s"] + revised_dose["easy_s"]
                + revised_dose["recovery_s"]):
            raise ProposalBlocked("TARGET_DURATION_SHORTER_THAN_STEPS")
        target["duration_m"] = target_duration
    else:
        raise ProposalBlocked("UNSUPPORTED_CHANGE_KIND")

    after_metrics = plan_metrics(after)
    _constraints(after, constraints, after_metrics)
    if (after_metrics["running_km"] > prior["running_km"] + 1e-6
            or after_metrics["run_hard_s"] > prior["run_hard_s"]):
        raise ProposalBlocked("UNAPPROVED_TRAINING_DOSE_INCREASE")
    if kind == "MOVE_SESSION" and any(
        after_metrics[k] != prior[k]
        for k in ("running_km", "run_hard_s", "run_easy_s", "run_recovery_s",
                  "strength_sessions", "sessions")
    ):
        raise ProposalBlocked("MOVE_CHANGED_TRAINING_DOSE")
    if before == after:
        raise ProposalBlocked("NO_OP_CHANGE")
    return {
        "status": "PROPOSED_PENDING_EXPLICIT_APPROVAL",
        "base_version": before["version"],
        "source": request["source"],
        "explanation": request["reason"],
        "evidence_status": request["evidence_status"],
        "evidence_refs": deepcopy(refs),
        "kind": kind,
        "session_id": request["session_id"],
        "before_sha256": fingerprint(before),
        "candidate_sha256": fingerprint(after),
        "before_plan": before, "candidate_plan": after,
        "before_metrics": prior, "after_metrics": after_metrics,
        "requires_explicit_athlete_approval": True,
        "applied": False,
        "coaching_effectiveness": "NOT_ESTABLISHED",
    }
