"""Deterministic, research-only volume-policy harness. NOT a coaching prescription."""
from __future__ import annotations
import hashlib
import json
import math
from datetime import date, timedelta

VERSION = "skeleton-research-0.1"
ORIGINS = {"synthetic", "manual", "real_opt_in"}

def finite(value, name, *, required=True):
    if value is None and not required:
        return None
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{name}: expected a number")
    result = float(value)
    if not math.isfinite(result) or result < 0:
        raise ValueError(f"{name}: expected a finite nonnegative value")
    return result

def fingerprint(item):
    return hashlib.sha256(json.dumps(item, sort_keys=True, allow_nan=False).encode()).hexdigest()

def validate_config(cfg):
    required = ("growth_fraction", "growth_absolute_km", "long_run_step_km",
                "long_run_fraction", "long_run_max_fraction", "deload_fraction",
                "deload_long_fraction", "low_confidence_multiplier", "disagreement_km")
    for key in required:
        finite(cfg[key], key)
    for key in ("growth_fraction", "long_run_fraction", "long_run_max_fraction",
                "deload_fraction", "deload_long_fraction", "low_confidence_multiplier"):
        if not 0 < cfg[key] <= 1:
            raise ValueError(f"{key}: outside research configuration range")
    if cfg["long_run_fraction"] > cfg["long_run_max_fraction"]:
        raise ValueError("target long-run fraction cannot exceed share ceiling")
    if type(cfg["deload_every"]) is not int or cfg["deload_every"] < 2:
        raise ValueError("deload_every must be >= 2")
    if type(cfg["forecast_horizon"]) is not int or cfg["forecast_horizon"] < 16:
        raise ValueError("forecast_horizon must be >= 16")
    if cfg.get("evidence") != "HEURISTIC_SYNTHETIC":
        raise ValueError("experimental values must be labeled HEURISTIC_SYNTHETIC")

def evaluate(case, cfg):
    validate_config(cfg)
    if case.get("origin") not in ORIGINS:
        raise ValueError("unknown input origin")
    if case["origin"] == "real_opt_in" and case.get("explicit_consent") is not True:
        raise ValueError("real replay requires explicit consent")
    if not isinstance(case.get("id"), str) or not case["id"]:
        raise ValueError("scenario ID required")
    date_start = date.fromisoformat(case["start_date"])
    length = case["weeks"]
    days = case["run_days"]
    if type(length) is not int or not 1 <= length <= 16:
        raise ValueError("plan duration must be 1 to 16 weeks")
    if type(days) is not int or not 1 <= days <= 7:
        raise ValueError("run days must be 1 to 7")
    target = finite(case["target_km"], "target_km")
    if target == 0:
        raise ValueError("target mileage must be positive")
    ceiling = finite(case.get("configured_ceiling_km"), "configured_ceiling_km", required=False)
    requested_long = finite(case.get("minimum_long_run_km"), "minimum_long_run_km", required=False)
    history = case["history"]
    if not isinstance(history, list) or len(history) != 4:
        raise ValueError("provide four chronological weekly records")
    valid_weeks, longest = [], []
    for index, row in enumerate(history):
        if type(row.get("complete")) is not bool:
            raise ValueError("complete must be boolean")
        km = finite(row.get("km"), f"history[{index}].km", required=False)
        lr = finite(row.get("long_km"), f"history[{index}].long_km", required=False)
        if row["complete"] and km is None:
            raise ValueError("complete week cannot have missing distance")
        if lr is not None and km is not None and lr > km:
            raise ValueError("long run exceeds weekly distance")
        valid_weeks.append(km if row["complete"] else None)
        if row["complete"] and lr is not None and lr > 0:
            longest.append(lr)
    confirmed = [km for km in valid_weeks if km is not None]
    four_mean = sum(confirmed) / len(confirmed) if len(confirmed) >= 2 else None
    recent_mean = sum(valid_weeks[-2:]) / 2 if all(km is not None for km in valid_weeks[-2:]) else None
    declared = finite(case.get("declared_km"), "declared_km", required=False)
    trace, conflicts, weeks = [], [], []
    confidence = "high" if len(confirmed) == 4 else "medium" if len(confirmed) == 3 else "low"
    baseline = four_mean if four_mean is not None else declared
    if four_mean is not None and len(confirmed) == 4 and recent_mean is not None and recent_mean < four_mean:
        baseline = min(baseline, recent_mean)
        confidence = "medium"
        trace.append({"rule": "RECENT_DOWNWARD_TREND", "detail": "Use lower mean of last two complete weeks"})
    if len(confirmed) < 4:
        confidence = "low"
        trace.append({"rule": "INCOMPLETE_HISTORY", "detail": "Unknown weekly distance excluded, not treated as zero"})
    if declared is not None and four_mean is not None:
        if abs(declared - four_mean) > cfg["disagreement_km"] and case.get("declared_confirmed") is not True:
            baseline = min(baseline, declared)
            confidence = "low"
            trace.append({"rule": "UNRESOLVED_DECLARATION", "detail": "Use lower value pending athlete confirmation"})
    if four_mean is None and declared is not None:
        trace.append({"rule": "DECLARED_ONLY", "detail": "Manual baseline has low confidence"})
    result = {
        "scenario": case["id"], "source_origin": case["origin"],
        "version": VERSION, "policy_name": cfg["name"],
        "config_hash": fingerprint(cfg), "input_hash": fingerprint(case),
        "status": "ok", "feasibility": "unsupported", "target_date_forecast": None,
        "baseline_km": round(baseline, 3) if baseline is not None else None,
        "confidence": confidence,
        "evidence": {"four_week_observed_mean_km": round(four_mean, 3) if four_mean is not None else None,
                     "last_two_complete_mean_km": round(recent_mean, 3) if recent_mean is not None else None,
                     "complete_weeks": len(confirmed), "declared_km": declared,
                     "observed_longest_km": max(longest, default=None)},
        "weeks": weeks, "conflicts": conflicts, "decision_trace": trace,
        "unmodeled": ["post_run_feedback"] if "post_run_feedback" in case else [],
        "limitations": [
            "Synthetic/software arithmetic only: no physiological safety, injury prevention or effectiveness claim.",
            "No actual workout steps, quality-dose budget, Android player or full schedule allocation.",
            "Recent longest recorded run is not confirmed repeatable capacity."
        ]
    }
    if case.get("safety_flag") in {"pain", "illness"}:
        result["status"] = "needs_human_review"
        trace.append({"rule": "PAIN_ILLNESS_HOLD", "detail": "No automatic medical clearance or progression"})
        return result
    if baseline is None or baseline <= 0:
        result["status"] = "insufficient_baseline"
        trace.append({"rule": "BASELINE_UNKNOWN_OR_ZERO", "detail": "No percentage progression from an unknown or zero baseline"})
        return result
    if ceiling is not None and ceiling < baseline:
        result["status"] = "conflict"
        conflicts.append({"rule": "BASELINE_OVER_CONFIGURED_CEILING", "baseline_km": baseline, "ceiling_km": ceiling})
        return result
    minimum_days = case.get("minimum_days", 1)
    if type(minimum_days) is not int or not 1 <= minimum_days <= 7:
        raise ValueError("minimum_days must be 1 to 7")
    if minimum_days > days:
        result["status"] = "conflict"
        conflicts.append({"rule": "INSUFFICIENT_DAYS", "available": days, "required": minimum_days})
        return result
    if ceiling is not None and target > ceiling:
        conflicts.append({"rule": "TARGET_ABOVE_CONFIGURED_CEILING",
                          "requested_target_km": target, "ceiling_km": ceiling})
        trace.append({"rule": "TARGET_ABOVE_CONFIGURED_CEILING",
                      "detail": "Requested target is infeasible under the configured workload ceiling; preserve the complete projected plan."})
    factor = cfg["low_confidence_multiplier"] if confidence == "low" else 1.
    if confidence == "low":
        trace.append({"rule": "LOW_CONFIDENCE_ENVELOPE", "detail": "Narrow hypothetical progression constraints"})
    last_build = baseline
    prior_long = max(longest, default=None)
    attainment = None
    for i in range(cfg["forecast_horizon"]):
        week = i + 1
        fired = []
        recovering = week % cfg["deload_every"] == 0
        if i == 0:
            volume = baseline
            fired.append("INITIAL_BASELINE")
        elif recovering:
            volume = last_build * cfg["deload_fraction"]
            fired.append("RECOVERY_WEEK")
        else:
            rel_step = last_build * cfg["growth_fraction"] * factor
            abs_step = cfg["growth_absolute_km"] * factor
            volume = last_build + min(rel_step, abs_step)
            fired.append("RELATIVE_LIMIT_BINDS" if rel_step <= abs_step else "ABSOLUTE_LIMIT_BINDS")
            if ceiling is not None and volume > ceiling:
                volume = ceiling
                fired.append("CONFIGURED_CEILING_BINDS")
            last_build = volume
        volume = round(volume, 3)
        # Never round a capped workload above its configured ceiling.
        if ceiling is not None and volume > ceiling:
            volume = math.floor(ceiling * 1000) / 1000
            fired.append("FINAL_ROUNDING_CEILING")
        if ceiling is not None and volume > ceiling:
            raise AssertionError("final capacity constraint violated")
        if not recovering:
            last_build = volume  # Subsequent increments use the actual emitted workload.
        if prior_long is None:
            long_km = None
            fired.append("LONG_RUN_HISTORY_UNKNOWN")
        elif recovering:
            long_km = min(prior_long * cfg["deload_long_fraction"],
                          volume * cfg["long_run_fraction"])
            fired.append("LONG_RUN_RECOVERY")
        else:
            step = prior_long + cfg["long_run_step_km"] * factor
            share = volume * cfg["long_run_fraction"]
            hard_share = volume * cfg["long_run_max_fraction"]
            long_km = min(step, share, hard_share)
            if step <= min(share, hard_share):
                fired.append("LONG_RUN_STEP_BINDS")
            elif share <= min(step, hard_share):
                fired.append("LONG_RUN_TARGET_SHARE_BINDS")
            else:
                fired.append("LONG_RUN_MAX_SHARE_BINDS")
            prior_long = long_km
        if long_km is not None:
            # Floor long-run output at reporting precision: rounding up can exceed a share cap.
            long_km = math.floor(long_km * 1000) / 1000
            if long_km > volume * cfg["long_run_max_fraction"]:
                raise AssertionError("final long run constraint violated")
        if requested_long is not None and (long_km is None or requested_long > long_km + 1e-7):
            fired.append("REQUESTED_LONG_RUN_CONFLICT")
            if i < length:
                conflicts.append({"rule": "REQUESTED_LONG_RUN_CONFLICT", "week": week,
                                  "requested_km": requested_long, "supported_km": long_km})
        if i < length:
            weeks.append({"week": week, "starts": str(date_start + timedelta(weeks=i)),
                          "weekly_km": volume, "long_run_km": long_km,
                          "rule_ids": fired, "target_weekly_km": target})
        if attainment is None and not recovering and volume >= target - 1e-7:
            attainment = week
    if attainment is not None:
        result["feasibility"] = "within_window" if attainment <= length else "later"
        result["target_date_forecast"] = str(date_start + timedelta(weeks=attainment-1))
    if conflicts:
        result["status"] = "conflict"
    return result

def evaluate_matrix(cases, policies):
    if len({c["id"] for c in cases}) != len(cases):
        raise ValueError("duplicate scenario IDs")
    if len({c["name"] for c in policies}) != len(policies):
        raise ValueError("duplicate policy names")
    return {"version": VERSION, "rubric": "docs/qa/engine15-gate-b-rubric-v1.md",
            "results": [evaluate(c, p) for c in cases for p in policies],
            "interpretation": "Synthetic conformance only; human Gate B review not performed"}
