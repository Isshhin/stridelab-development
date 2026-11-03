"""Batch 8M: capacity arithmetic comparison at STRICTLY shared semantics.

Original Lab produces six-week plans; B0/H1 propose revisions to state
snapshots. The only comparable feature today is whether a *scheduled weekly
distance* exceeds the original synthetic athlete's ASSUMED (not verified)
capacity. No overall action, scheduling, recovery, progression or coaching
quality comparison is scientifically valid on these data.
"""
from __future__ import annotations

import argparse
from collections import Counter
from hashlib import sha256
import json
from math import isfinite
from pathlib import Path
import re
from typing import Any

from original_lab_evidence_8l import (
    FINGERPRINT, FIXTURE, ORIGINAL_ANCHOR, REPO,
    verify as verify_original_evidence,
    canonical_json,
)
from policy_candidates import run_policy

SCHEMA = "stridelab-original-case-semantics-8m-v1"
PIN_SCHEMA = "stridelab-manual-original-case-semantics-pin-8m-v1"
ASSUMPTION = "SYNTHETIC_PROFILE_ASSUMPTION_NOT_VERIFIED"
BODY_KEYS = {
    "source_repo", "original_main_anchor",
    "source_policy_evidence_8l_body_sha256",
    "source_fingerprint_sha256", "fixture_sha256",
    "data_classification", "scope", "human_effectiveness_data_included",
    "input_cases",
}
CASE_KEYS = {
    "case_id", "original_athlete_input_sha256", "synthetic_category",
    "configured_capacity_km", "current_weekly_km", "runs_per_week_profile",
    "default_required_sessions", "available_days", "history_status_counts",
    "capacity_basis", "session_duration_minutes", "measured_activity_source",
    "heart_rate_observed", "recovery_readiness_verified",
    "planned_actual_links_verified",
}
WEEKDAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}
STATUSES = {"completed", "partial", "skipped", "unknown", "unplanned", "planned"}
POLICY_CAPACITY = "HYPOTHETICAL_CAPACITY_INDICATOR_ONLY"
SHA = re.compile(r"^[a-f0-9]{64}$")
GIT_COMMIT = re.compile(r"^[a-f0-9]{40}$")


class SemanticComparisonRejected(ValueError):
    pass


def require(condition: bool, code: str) -> None:
    if not condition:
        raise SemanticComparisonRejected(code)


def verify_semantics(sidecar: Any, pin: Any, old_evidence: Any) -> list[dict[str, Any]]:
    require(isinstance(sidecar, dict) and set(sidecar) == {
        "schema", "body", "body_sha256", "origin_assurance",
    }, "INVALID_SIDECAR_ENVELOPE")
    require(sidecar["schema"] == SCHEMA and
            sidecar["origin_assurance"] == "MANUALLY_REVIEWED_SHA256_NOT_SIGNED",
            "SIDECAR_ORIGIN_OVERCLAIM")
    require(isinstance(pin, dict) and set(pin) == {
        "schema", "sidecar_body_sha256", "producer_branch",
        "producer_ci_head", "producer_ci_run_id", "transfer_review"
    }, "INVALID_SIDECAR_REVIEW_PIN")
    require(pin["schema"] == PIN_SCHEMA and
            pin["transfer_review"] == "MANUAL_IDENTICAL_PYTHON_3_11_AND_3_12_NO_SIGNATURE"
            and pin["producer_branch"] == "research/batch8m-capacity-semantics-20261103"
            and isinstance(pin["producer_ci_run_id"], int)
            and pin["producer_ci_run_id"] > 0
            and isinstance(pin["producer_ci_head"], str)
            and GIT_COMMIT.fullmatch(pin["producer_ci_head"]) is not None,
            "UNTRUSTED_MANUAL_SIDECAR_PIN")
    body = sidecar["body"]
    require(isinstance(body, dict) and set(body) == BODY_KEYS, "INVALID_SIDECAR_FIELDS")
    require(isinstance(sidecar["body_sha256"], str) and
            SHA.fullmatch(sidecar["body_sha256"]) is not None and
            sha256(canonical_json(body)).hexdigest() == sidecar["body_sha256"],
            "SIDECAR_DIGEST_MISMATCH")
    require(sidecar["body_sha256"] == pin["sidecar_body_sha256"],
            "SIDECAR_NOT_MANUALLY_PINNED")
    require(body["source_repo"] == REPO and
            body["original_main_anchor"] == ORIGINAL_ANCHOR and
            body["source_fingerprint_sha256"] == FINGERPRINT and
            body["fixture_sha256"] == FIXTURE and
            body["source_policy_evidence_8l_body_sha256"] == old_evidence["body_sha256"],
            "ORIGINAL_POLICY_OUTPUT_ALIGNMENT_FAILED")
    require(body["data_classification"] == "SYNTHETIC_ONLY" and
            body["scope"] == "HYPOTHETICAL_CAPACITY_ARITHMETIC_ONLY" and
            body["human_effectiveness_data_included"] is False,
            "FALSE_REAL_WORLD_EVIDENCE_CLAIM")
    rows = body["input_cases"]
    cases = old_evidence["body"]["cases"]
    require(isinstance(rows, list) and len(rows) == len(cases) == 7,
            "INPUT_CASE_COUNT_MISMATCH")
    for source, old in zip(rows, cases):
        require(isinstance(source, dict) and set(source) == CASE_KEYS and
                source["case_id"] == old["case_id"],
                "INPUT_CASE_ID_OR_FIELDS_MISMATCH")
        require(isinstance(source["original_athlete_input_sha256"], str) and
                SHA.fullmatch(source["original_athlete_input_sha256"]) is not None,
                "INVALID_ORIGINAL_ATHLETE_FINGERPRINT")
        require(source["synthetic_category"] in {
            "beginner", "intermediate", "experienced_high_mileage", "returning"
        }, "UNKNOWN_SYNTHETIC_ATHLETE_TYPE")
        require(source["capacity_basis"] == ASSUMPTION and
                source["session_duration_minutes"] is None and
                source["measured_activity_source"] is None and
                source["heart_rate_observed"] is None and
                source["recovery_readiness_verified"] is False and
                source["planned_actual_links_verified"] is False,
                "FABRICATED_REAL_WORLD_INPUT")
        cap, current = source["configured_capacity_km"], source["current_weekly_km"]
        require(type(current) in (int, float) and isfinite(current)
                and 0 < current <= 250 and
                (cap is None or (type(cap) in (int, float)
                 and isfinite(cap) and 0 < cap <= 250)),
                "INVALID_SYNTHETIC_KM_INPUT")
        days = source["available_days"]
        require(isinstance(days, list) and bool(days)
                and len(days) == len(set(days)) and set(days) <= WEEKDAYS,
                "INVALID_SYNTHETIC_DAY_AVAILABILITY")
        require(type(source["runs_per_week_profile"]) is int and
                1 <= source["runs_per_week_profile"] <= 7 and
                type(source["default_required_sessions"]) is int and
                1 <= source["default_required_sessions"] <= 7,
                "INVALID_SYNTHETIC_SESSION_FREQUENCY")
        statuses = source["history_status_counts"]
        require(isinstance(statuses, dict) and
                all(k in STATUSES and type(v) is int and 0 <= v <= 100
                    for k, v in statuses.items()),
                "INVALID_HISTORY_STATUS_SUMMARY")
        if len(days) < source["default_required_sessions"]:
            require(all(r["status"] == "EXPLICITLY_INFEASIBLE"
                        for r in old["results"]),
                    "SCHEDULE_INFEASIBILITY_NOT_PRESERVED")
        for r in old["results"]:
            if r["status"] != "EXPLICITLY_INFEASIBLE" and r["policy_id"].startswith("base."):
                require(abs(r["weekly_target_km"][0] - current) <= 0.011,
                        "ORIGINAL_WEEK0_INITIAL_VOLUME_MISMATCH")
    return rows


def evaluate(original: dict[str, Any], original_pin: dict[str, Any],
             sidecar: dict[str, Any], semantic_pin: dict[str, Any]) -> dict[str, Any]:
    verify_original_evidence(original, original_pin)
    rows = verify_semantics(sidecar, semantic_pin, original)
    decisions = Counter()
    grouped: list[dict[str, Any]] = []
    mismatches: list[str] = []
    for case, original_case in zip(rows, original["body"]["cases"]):
        cap = case["configured_capacity_km"]
        for result in original_case["results"]:
            policy = result["policy_id"]
            status = result["status"]
            if status == "EXPLICITLY_INFEASIBLE":
                decisions["infeasible_original_plans"] += 1
                grouped.append({
                    "case_id": case["case_id"], "original_policy_id": policy,
                    "status": "BLOCKED_ORIGINAL_PLAN_INFEASIBLE",
                    "hypothetical_capacity_weeks_checked": 0,
                    "weeks_over_configured_cap": 0,
                    "target_mismatch_weeks": 0,
                })
                continue
            require(cap is not None, "MISSING_CONFIGURED_CAPACITY_CANNOT_COMPARE")
            over = 0
            target_mismatch = 0
            for i, (scheduled, target) in enumerate(zip(
                    result["weekly_scheduled_km"], result["weekly_target_km"])):
                # A B0/H1 input field is called "verified_capacity_km". Here
                # it carries a synthetic configured assumption purely to
                # compare BOOLEAN arithmetic. It is NOT a verified real field.
                hypothetical_state = {
                    "athlete": {"verified_capacity_km": cap},
                    "weekly": {"planned_km": scheduled},
                    "day": {},  # minutes / timing UNKNOWN, action not comparable
                    "session": {"status": "unknown"},
                    "request_adaptation": False,
                }
                expected = scheduled > cap
                for candidate in ("B0", "H1"):
                    output = run_policy(candidate, hypothetical_state)
                    actual = output["indicators"]["weekly_over_cap"]
                    if actual != expected:
                        mismatches.append(case["case_id"] + ":" + policy +
                                          ":week" + str(i) + ":" + candidate)
                    decisions["boolean_capacity_checks"] += 1
                decisions["original_generated_weeks_checked"] += 1
                over += expected
                target_mismatch += abs(scheduled - target) > 0.011
            flagged = "workload.capacity" in result["validator_error_rules"]
            if flagged != (over > 0):
                mismatches.append(case["case_id"] + ":" + policy +
                                  ":ORIGINAL_VALIDATOR_CAPACITY_DISAGREEMENT")
            decisions["original_validator_flagged_plans"] += flagged
            decisions["observed_over_configured_capacity_weeks"] += over
            decisions["target_vs_schedule_mismatch_weeks"] += target_mismatch
            grouped.append({
                "case_id": case["case_id"], "original_policy_id": policy,
                "status": "HYPOTHETICAL_CAPACITY_ARITHMETIC_ONLY",
                "hypothetical_capacity_weeks_checked": 6,
                "weeks_over_configured_cap": over,
                "target_mismatch_weeks": target_mismatch,
            })
    require(not mismatches, "SHARED_CAPACITY_BOOLEAN_OR_VALIDATOR_MISMATCH")
    return {
        "schema": "stridelab-batch8m-shared-capacity-evaluation-v1",
        "status": "HYPOTHETICAL_CAPACITY_FLAGS_MATCH_ONLY",
        "scope": POLICY_CAPACITY,
        "source_original_policy_body_sha256": original["body_sha256"],
        "source_case_semantics_body_sha256": sidecar["body_sha256"],
        "source_authentication": "MANUAL_PIN_ONLY_NOT_SIGNED",
        "source_python_executed_in_this_repository": False,
        "source_capacity_verified_in_real_athletes": False,
        "b0_h1_full_actions_compared": 0,
        "original_lab_six_week_generator_vs_b0_h1_equivalent": False,
        "original_policy_quality_ranked": False,
        "human_outcome_effectiveness_measured": False,
        "private_workout_files_used": False,
        "counts": dict(sorted(decisions.items())),
        "original_policy_case_rows": grouped,
        "blocked_comparisons": [
            "ACTUAL_GENERATED_PLAN_VS_REVISION_ACTION_NO_SAME_OUTPUT_TYPE",
            "UNVERIFIED_CONFIGURED_CAPACITY_IS_NOT_REAL_CAPACITY",
            "MISSING_DAILY_TIME_BUDGET_AND_SCHEDULING_INPUTS",
            "NO_AUTHENTIC_PLANNED_VS_COMPLETED_TRAINING_HISTORY",
            "NO_SENSOR_VERIFIED_HR_PACE_RECOVERY_OUTCOMES",
            "NO_MEASURED_PERFORMANCE_OR_VENDOR_PARITY",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--original", type=Path, required=True)
    parser.add_argument("--original-pin", type=Path, required=True)
    parser.add_argument("--semantics", type=Path, required=True)
    parser.add_argument("--semantic-pin", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("research-capacity-comparison-8m.json"))
    args = parser.parse_args()
    result = evaluate(*[
        json.loads(path.read_text(encoding="utf-8"))
        for path in (args.original, args.original_pin, args.semantics, args.semantic_pin)
    ])
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n",
                           encoding="utf-8")
    print("8M_SHARED_CAPACITY_STATUS:", result["status"])
    print("8M_CAPACITY_COUNTS:", json.dumps(result["counts"], sort_keys=True))
    print("B0_H1_FULL_ACTION_COMPARISONS:", result["b0_h1_full_actions_compared"])
    print("REAL_COACHING_OUTCOMES:", result["human_outcome_effectiveness_measured"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
