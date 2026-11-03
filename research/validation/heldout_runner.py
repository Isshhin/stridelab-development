#!/usr/bin/env python3
"""Seeded adversarial comparison external to the Batch 8B frozen case registry.

This is NEW synthetic data, generated with a fixed registered seed. It is not
external athlete validation and was not used to fit/tune B0 or H1. Expected
properties below are specified independently of policy_candidates helpers.
"""
from __future__ import annotations

import argparse
from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import random
from typing import Any

from policy_candidates import run_policy

DEFAULT_SEED = 82603
PER_COHORT = 12
COHORTS = (
    "OVER_CAP", "BELOW_CAP", "TIME_OVER", "PAIN_ONLY",
    "EQUIPMENT_GAP", "SKILL_GAP", "LOWER_OVERLAP", "MISSING_ACTIVITY",
    "CAPACITY_UNKNOWN", "RETURN_FROM_BREAK", "SKIPPED_SESSION",
    "PARTIAL_QUALITY", "HEAT_CONTEXT", "MISSING_HR",
    "INVALID_STRESS", "MUSCLE_ALLOCATION", "NO_PREDICTOR",
    "PAIN_AND_CAP", "CAP_AND_TIME", "TIME_AND_SKILL",
    "CROSS_DOMAIN", "STEP_SEPARATION", "REPRODUCIBILITY",
    "UNOBSERVED_ACTUAL_STEPS",
)


def sample(index: int, cohort: str, rng: random.Random) -> dict[str, Any]:
    cap = rng.choice((25, 40, 60, 70, 90, 110))
    weekly = cap - rng.choice((2, 5, 10))
    dist = rng.choice((5.0, 8.0, 10.0, 14.0))
    secs = rng.choice((1800, 2400, 3600, 4200))
    state: dict[str, Any] = {
        "athlete": {"verified_capacity_km": cap, "phase": "build"},
        "weekly": {"planned_km": weekly},
        "day": {"durations_m": [35], "minutes_available": 120, "max_sessions": 2},
        "session": {
            "status": "completed",
            "planned_steps": [{"kind": "easy", "km": dist, "seconds": secs}],
            "actual_steps": [{"kind": "easy", "km": dist, "seconds": secs}],
            "hr_bpm": rng.randint(130, 147),
            "hr_quality": "valid",
        },
        "environment": {
            "temperature_c": rng.choice((23, 27, 30, 34)),
            "route_class": "road",
            "descent_m": rng.choice((0, 10, 30)),
        },
        "next": {"kind": "easy"},
        "recent": [],
        "request_adaptation": False,
        "strength": {"status": "completed", "actual_sets": 3, "load_kg": 25, "rir": 3, "reps": 8},
        "conditioning": {"status": "completed", "actual_rounds": 4, "impacts": 0},
        "equipment": {"required": [], "available": ["bodyweight", "dumbbell"]},
        "skill": {"required": [], "mastered": [], "form_quality": "good"},
        "feedback": {"pain": False},
        "stress": {
            "method": "generic_hr", "score": rng.choice((35.0, 65.0, 85.0)),
            "calibration_version": "hr-v1", "threshold_verified": True,
            "gps_verified": True, "grade_verified": True,
        },
        "forecast": {"athlete_history": f"synthetic-{index}", "workout_level": 5,
                     "source_version": "unvalidated"},
        "muscle_sets": {"total": 10, "weights": [7, 3]},
        "temporal": {"yesterday_ctl": 50, "yesterday_atl": 55, "today_score": 70},
    }
    if cohort in ("OVER_CAP", "CAP_AND_TIME", "PAIN_AND_CAP"):
        state["weekly"]["planned_km"] = cap + rng.choice((1, 4, 12))
    if cohort in ("TIME_OVER", "CAP_AND_TIME", "TIME_AND_SKILL"):
        state["day"]["durations_m"] = [rng.randint(50, 65), rng.randint(55, 90)]
        state["day"]["minutes_available"] = 60
    if cohort in ("PAIN_ONLY", "PAIN_AND_CAP"):
        state["feedback"]["pain"] = True
    if cohort == "EQUIPMENT_GAP":
        state["equipment"]["required"] = ["barbell"]
    if cohort in ("SKILL_GAP", "TIME_AND_SKILL"):
        state["skill"] = {"required": ["20s_handstand"], "mastered": [],
                          "form_quality": "unknown"}
    if cohort == "LOWER_OVERLAP":
        state["recent"] = [{"movement": "lower_body_strength", "status": "completed"}]
        state["next"]["kind"] = rng.choice(("threshold", "long_run", "hard_run"))
    if cohort in ("MISSING_ACTIVITY", "UNOBSERVED_ACTUAL_STEPS"):
        state["session"]["status"] = "unknown"
        if cohort == "MISSING_ACTIVITY":
            state["session"]["actual_steps"] = None
            state["request_adaptation"] = True
        else:
            state["session"]["actual_steps"] = [
                {"kind": "hard", "km": 4.0, "seconds": 1200}
            ]
    if cohort == "CAPACITY_UNKNOWN":
        state["athlete"]["verified_capacity_km"] = None
        state["request_adaptation"] = True
    if cohort == "RETURN_FROM_BREAK":
        state["return_from_break"] = True
        state["request_adaptation"] = True
    if cohort == "SKIPPED_SESSION":
        state["session"]["status"] = "skipped"
        state["session"]["actual_steps"] = None
    if cohort == "PARTIAL_QUALITY":
        state["session"]["status"] = "partial"
        state["session"]["planned_steps"] = [
            {"kind": "hard", "km": 7.0, "seconds": 2400},
            {"kind": "recovery", "km": 0.5, "seconds": 300},
        ]
        state["session"]["actual_steps"] = [
            {"kind": "hard", "km": 3.5, "seconds": 1200},
            {"kind": "recovery", "km": 0.25, "seconds": 150},
        ]
    if cohort == "HEAT_CONTEXT":
        state["environment"]["temperature_c"] = rng.choice((36, 38, 40))
    if cohort == "MISSING_HR":
        state["session"]["hr_bpm"] = None
        state["session"]["hr_quality"] = "missing"
    if cohort == "INVALID_STRESS":
        state["stress"]["method"] = "generic_pace"
        state["stress"]["gps_verified"] = False
        state["stress"]["threshold_verified"] = False
    if cohort == "MUSCLE_ALLOCATION":
        state["muscle_sets"] = {"total": rng.randint(8, 25), "weights": [2, 1, 3]}
    if cohort == "CROSS_DOMAIN":
        state["strength"]["actual_sets"] = rng.randint(3, 7)
        state["strength"]["load_kg"] = rng.randint(10, 90)
    if cohort == "STEP_SEPARATION":
        state["session"]["actual_steps"] = [
            {"kind": "easy", "km": 5, "seconds": 1800},
            {"kind": "hard", "km": 3, "seconds": 900},
            {"kind": "recovery", "km": 1, "seconds": 240},
        ]
        state["session"]["planned_steps"] = deepcopy(state["session"]["actual_steps"])
    return state


def assess(cohort: str, state: dict[str, Any],
           output: dict[str, Any], baseline: dict[str, Any]) -> list[str]:
    """Externally specified invariants; no production policy helpers used."""
    issues: list[str] = []
    obs = output["observation"]
    indicator = output["indicators"]
    rev = output["revision"]
    act = output["action"]

    def expect(ok: bool, label: str):
        if not ok:
            issues.append(label)

    expect(output["policy_id"].startswith("H1_"), "wrong_policy_id")
    expect(rev["before"] == state["weekly"], "lost_original_plan")
    expect(rev["after"] == state["weekly"], "unapproved_plan_mutation")
    expect(rev["applied"] is False, "unapproved_plan_apply")
    expect(bool(rev["requires_approval"]) == act.startswith("PROPOSE"),
           "approval_flag_mismatch")
    expect(output["forecast"]["actual_outcome"] is None, "predicted_as_actual")
    expect(output["forecast"]["predicted_difficulty"] is None,
           "invented_calibrated_prediction")
    expect(obs["status"] == state["session"]["status"], "wrong_recorded_status")
    expect(obs["hr_bpm"] == state["session"]["hr_bpm"], "fabricated_hr")
    expect(obs["temperature_c"] == state["environment"]["temperature_c"],
           "lost_heat_provenance")
    expect(obs["strength_actual_sets"] == state["strength"]["actual_sets"],
           "lost_strength_sets")
    expect(obs["conditioning_actual_rounds"] == state["conditioning"]["actual_rounds"],
           "lost_conditioning_rounds")
    expect(output["monitor"]["method"] == state["stress"]["method"],
           "wrong_monitor_method")
    expect(output["monitor"]["calibration_version"] == state["stress"]["calibration_version"],
           "lost_calibration")
    expect(sum(output["strength_allocation"]["exercise_sets"]) ==
           output["strength_allocation"]["group_sets"], "group_sets_not_conserved")
    if state["session"]["status"] in ("skipped",):
        expect(obs["actual_run"] is not None and obs["actual_run"]["hard_s"] == 0,
               "skipped_run_credited")
    if state["session"]["status"] in ("unknown", "planned"):
        expect(obs["actual_run"] is None, "unobserved_work_credited")
    if state["session"]["status"] in ("completed", "partial"):
        steps = state["session"]["actual_steps"]
        if steps is not None:
            expected_hard = sum(z["seconds"] for z in steps if z["kind"] == "hard")
            expected_easy = sum(z["seconds"] for z in steps if z["kind"] == "easy")
            expected_recovery = sum(z["seconds"] for z in steps if z["kind"] == "recovery")
            expect(obs["actual_run"]["hard_s"] == expected_hard, "bad_hard_step_sum")
            expect(obs["actual_run"]["easy_s"] == expected_easy, "bad_easy_step_sum")
            expect(obs["actual_run"]["recovery_s"] == expected_recovery,
                   "recovery_counted_as_quality")
    expected_action = {
        "OVER_CAP": "PROPOSE_WEEK_REDUCTION",
        "BELOW_CAP": "KEEP",
        "TIME_OVER": "PROPOSE_SESSION_ADJUSTMENT",
        "PAIN_ONLY": "DEFER_UNKNOWN",
        "EQUIPMENT_GAP": "PROPOSE_EXERCISE_SUBSTITUTION",
        "SKILL_GAP": "DEFER_UNKNOWN",
        "LOWER_OVERLAP": "PROPOSE_MOVE_SESSION",
        "MISSING_ACTIVITY": "DEFER_UNKNOWN",
        "CAPACITY_UNKNOWN": "DEFER_UNKNOWN",
        "RETURN_FROM_BREAK": "DEFER_UNKNOWN",
        "SKIPPED_SESSION": "KEEP",
        "PARTIAL_QUALITY": "KEEP",
        "HEAT_CONTEXT": "KEEP",
        "MISSING_HR": "KEEP",
        "INVALID_STRESS": "KEEP",
        "MUSCLE_ALLOCATION": "KEEP",
        "NO_PREDICTOR": "KEEP",
        "PAIN_AND_CAP": "DEFER_UNKNOWN",
        "CAP_AND_TIME": "PROPOSE_WEEK_REDUCTION",
        "TIME_AND_SKILL": "PROPOSE_SESSION_ADJUSTMENT",
        "CROSS_DOMAIN": "KEEP",
        "STEP_SEPARATION": "KEEP",
        "REPRODUCIBILITY": "KEEP",
        "UNOBSERVED_ACTUAL_STEPS": "KEEP",
    }[cohort]
    expect(act == expected_action, f"expected_{expected_action}_got_{act}")
    if cohort == "PAIN_AND_CAP":
        expect(baseline["action"] == "PROPOSE_WEEK_REDUCTION",
               "baseline_does_not_expose_conflicting_priority")
    if cohort == "CAP_AND_TIME":
        expect(indicator["weekly_over_cap"] and not indicator["time_feasible"],
               "missing_conflict_attributes")
    if cohort == "INVALID_STRESS":
        expect(output["monitor"]["value"] is None and
               output["monitor"]["reason"] == "NOT_COMPUTABLE",
               "invalid_gps_scored")
    if cohort == "PARTIAL_QUALITY":
        expect(obs["planned_run"]["hard_s"] == 2400 and
               obs["actual_run"]["hard_s"] == 1200, "partial_quality_credited_as_full")
    if cohort == "HEAT_CONTEXT":
        expect(output["monitor"]["value"] is not None,
               "heat_improperly_zeroed_measurement")
    return issues


def run_suite(seed: int = DEFAULT_SEED, per_cohort: int = PER_COHORT) -> dict[str, Any]:
    if per_cohort <= 0:
        raise ValueError("Must test positive number per cohort")
    rng = random.Random(seed)
    results: list[dict[str, Any]] = []
    differences = 0
    errors = 0
    for cohort in COHORTS:
        for i in range(per_cohort):
            state = sample(i, cohort, rng)
            previous = deepcopy(state)
            b0 = run_policy("B0", state)
            h1 = run_policy("H1", state)
            issues = assess(cohort, state, h1, b0)
            if state != previous:
                issues.append("input_mutation")
            if cohort == "REPRODUCIBILITY":
                other = run_policy("H1", deepcopy(state))
                if h1 != other:
                    issues.append("not_deterministic")
            if cohort == "CROSS_DOMAIN":
                other = deepcopy(state)
                other["strength"]["actual_sets"] += 10
                re = run_policy("H1", other)
                if re["observation"]["actual_run"] != h1["observation"]["actual_run"]:
                    issues.append("cross_discipline_inflated_run_mileage")
            if b0["action"] != h1["action"]:
                differences += 1
            errors += bool(issues)
            results.append({
                "id": f"HELDOUT-{cohort}-{i:03d}",
                "cohort": cohort,
                "status": "PASS" if not issues else "FAIL",
                "violations": issues,
                "b0_action": b0["action"],
                "h1_action": h1["action"],
                "input_sha256": hashlib.sha256(json.dumps(
                    state, sort_keys=True, separators=(",", ":")
                ).encode("utf-8")).hexdigest(),
            })
    return {
        "suite": "independent seeded research adversarial structural tests",
        "scope": "new B0/H1 research policy, not real Athlete Simulation Lab",
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "github_sha": os.environ.get("GITHUB_SHA"),
        "seed": seed, "per_cohort": per_cohort,
        "cohorts": list(COHORTS), "cases": results,
        "counts": {"PASS": len(results) - errors, "FAIL": errors,
                   "TOTAL": len(results)},
        "b0_h1_action_differences": differences,
        "policy_sha256": hashlib.sha256(
            (Path(__file__).parent / "policy_candidates.py").read_bytes()
        ).hexdigest(),
        "validator_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "not_longitudinal_outcome_evidence": True,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--per-cohort", type=int, default=PER_COHORT)
    parser.add_argument("--output", default="research-heldout-results.json")
    args = parser.parse_args()
    result = run_suite(args.seed, args.per_cohort)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n",
                      encoding="utf-8")
    print(f"SEEDED ADVERSARIAL STRUCTURAL TESTS: {result['counts']['PASS']} PASS, "
          f"{result['counts']['FAIL']} FAIL, {result['counts']['TOTAL']} TOTAL")
    print(f"COHORTS: {len(COHORTS)}; SEED: {args.seed}; B0/H1 action differences: "
          f"{result['b0_h1_action_differences']}")
    for item in result["cases"]:
        if item["status"] == "FAIL":
            print(f"FAIL {item['id']}: {item['violations']}")
    print(f"Report: {output}")
    return 1 if result["counts"]["FAIL"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
