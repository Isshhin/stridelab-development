"""Batch 8L: OFFLINE manual-copy verification of original-lab policy evidence.

This validates a sanitized handoff copied by an authorized reviewer from the
separate private Coaching Lab's successful CI run. It does NOT check out,
execute, or authenticate another private repository. SHA-256 by itself is an
integrity digest, NOT a digital signature. We preserve validation failures.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from math import isfinite
from pathlib import Path
import re
from typing import Any

SCHEMA = "stridelab-original-policy-sanitized-handoff-v1"
REPO = "Isshhin/stridelab-coaching-lab"
ORIGINAL_ANCHOR = "e255c5d369af3576cc6ccfec44696ed4f15e1c35"
FINGERPRINT = "748e3c84a9a19db570f3275ee8fcfd70305fc1023c884deaefa6b6e988dc9314"
FIXTURE = "b9be1149fb3468fcf191f3fb17c8f6b94b70ba36edc18aff502dfa98fb90fc98"
POLICIES = (
    "base.conservative", "base.stable", "base.fixed_10_percent",
    "combined.conservative_dose", "combined.history_scaled",
    "combined.return_to_quality",
)
CASES = (
    "beginner-seed-4", "intermediate-seed-9", "high-mileage-70-to-85",
    "returning-seed-3", "low-frequency-two-days",
    "completed-quality-followed-by-gap", "partial-quality-unknown-recovery",
)
SOURCE_MODULES = (
    "domain.py", "planning.py", "periodization.py", "dose.py",
    "feedback.py", "profiles.py", "workouts.py", "validation.py",
    "v021_audit.py", "v022_audit.py", "v02_benchmark.py",
)
VALID_STATUS = {"GENERATED", "GENERATED_WITH_STRUCTURAL_FINDINGS", "EXPLICITLY_INFEASIBLE"}
BODY_KEYS = {
    "source_repo", "original_main_anchor", "evidence_type", "data_classification",
    "private_athlete_data_used", "activity_gps_included", "coaching_policy_changed",
    "plans_applied", "real_world_effect_demonstrated",
    "original_source_fingerprint_sha256", "fixture_sha256",
    "source_files", "policy_ids", "summary", "cases",
}
SUMMARY_KEYS = {
    "cases", "policy_runs", "generated", "generated_with_validation_errors",
    "generated_without_validation_errors", "infeasible", "oracle_failures",
    "real_athlete_activities_analyzed",
}
POLICY_KEYS = {
    "policy_id", "status", "plan_sha256", "validator_error_rules",
    "weekly_scheduled_km", "weekly_target_km", "structural_oracle_failures",
}
SHA = re.compile(r"^[a-f0-9]{64}$")
COMMIT = re.compile(r"^[a-f0-9]{40}$")
PIN_KEYS = {"schema", "body_sha256", "producer_branch", "producer_ci_head",
            "producer_ci_run_id", "origin_review"}


class HandoffRejected(ValueError):
    pass


def _require(ok: bool, code: str) -> None:
    if not ok:
        raise HandoffRejected(code)


def canonical_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=True, sort_keys=True,
                      separators=(",", ":"), allow_nan=False).encode("utf-8")


def _sha(value: Any) -> bool:
    return isinstance(value, str) and SHA.fullmatch(value) is not None


def _km_list(value: Any) -> bool:
    return (
        isinstance(value, list) and len(value) == 6
        and all(type(v) in (int, float) and isfinite(v) and 0 <= v <= 250 for v in value)
    )


def verify(handoff: Any, pinned: Any) -> dict[str, Any]:
    _require(isinstance(handoff, dict) and set(handoff) == {
        "schema", "body", "body_sha256", "integrity_note"
    }, "INVALID_HANDOFF_ENVELOPE")
    _require(handoff["schema"] == SCHEMA, "UNSUPPORTED_HANDOFF_SCHEMA")
    _require(
        handoff["integrity_note"] == "SHA256_SELF_CONSISTENT_NOT_SIGNED_OR_ORIGIN_AUTHENTICATED",
        "MISREPRESENTED_ORIGIN_ASSURANCE"
    )
    _require(isinstance(pinned, dict) and set(pinned) == PIN_KEYS
             and pinned["schema"] == "stridelab-manual-original-lab-review-pin-v1",
             "INVALID_REVIEW_PIN")
    _require(pinned["origin_review"] == "MANUAL_CROSS_REPOSITORY_EVIDENCE_TRANSFER_NOT_SIGNATURE",
             "INVALID_REVIEW_PROVENANCE")
    _require(
        pinned["producer_branch"] == "research/batch8l-sanitized-handoff-20261103"
        and isinstance(pinned["producer_ci_run_id"], int)
        and pinned["producer_ci_run_id"] > 0
        and isinstance(pinned["producer_ci_head"], str)
        and COMMIT.fullmatch(pinned["producer_ci_head"]) is not None,
        "INVALID_PRODUCER_CI_REFERENCE"
    )
    body = handoff["body"]
    _require(isinstance(body, dict) and set(body) == BODY_KEYS, "INVALID_BODY_SCHEMA")
    _require(_sha(handoff["body_sha256"]) and _sha(pinned["body_sha256"]),
             "INVALID_EVIDENCE_DIGEST")
    digest = hashlib.sha256(canonical_json(body)).hexdigest()
    _require(digest == handoff["body_sha256"], "HANDOFF_BODY_TAMPERED")
    _require(digest == pinned["body_sha256"], "NOT_THE_MANUALLY_REVIEWED_EVIDENCE")

    _require(body["source_repo"] == REPO and body["original_main_anchor"] == ORIGINAL_ANCHOR,
             "WRONG_ORIGINAL_LAB_SOURCE")
    _require(
        body["evidence_type"] == "DETERMINISTIC_SYNTHETIC_SOFTWARE_BEHAVIOR_ONLY"
        and body["data_classification"] == "SYNTHETIC_ONLY",
        "REAL_DATA_NOT_ADMISSIBLE"
    )
    for k in ("private_athlete_data_used", "activity_gps_included",
              "coaching_policy_changed", "plans_applied",
              "real_world_effect_demonstrated"):
        _require(body[k] is False, "UNSUPPORTED_APPLICATION_OR_REAL_EVIDENCE_CLAIM")
    _require(body["fixture_sha256"] == FIXTURE and
             body["original_source_fingerprint_sha256"] == FINGERPRINT,
             "BASELINE_SOURCE_OR_FIXTURE_DRIFT")

    modules = body["source_files"]
    _require(isinstance(modules, list) and len(modules) == len(SOURCE_MODULES),
             "INCOMPLETE_ORIGINAL_POLICY_INVENTORY")
    _require(all(
        isinstance(x, dict) and set(x) == {"path", "sha256"}
        and x["path"] == "src/coaching_lab/" + name and _sha(x["sha256"])
        for name, x in zip(SOURCE_MODULES, modules)
    ), "INVALID_ORIGINAL_POLICY_INVENTORY")
    fingerprints = {name: modules[i]["sha256"] for i, name in enumerate(SOURCE_MODULES)}
    _require(hashlib.sha256(canonical_json(fingerprints)).hexdigest() == FINGERPRINT,
             "MODULE_FINGERPRINT_MISMATCH")
    _require(body["policy_ids"] == list(POLICIES), "UNEXPECTED_POLICY_LIST")
    cases = body["cases"]
    _require(isinstance(cases, list) and len(cases) == 7, "INVALID_FROZEN_CASE_COUNT")
    _require([r.get("case_id") for r in cases if isinstance(r, dict)] == list(CASES),
             "UNEXPECTED_CASE_IDS")
    counts = {"generated_without_validation_errors": 0, "generated_with_validation_errors": 0,
              "infeasible": 0, "oracle_failures": 0}
    for case in cases:
        _require(isinstance(case, dict) and set(case) == {"case_id", "results"},
                 "INVALID_CASE_SHAPE")
        results = case["results"]
        _require(isinstance(results, list) and len(results) == len(POLICIES),
                 "INCOMPLETE_POLICY_MATRIX")
        for idx, item in enumerate(results):
            _require(isinstance(item, dict) and set(item) == POLICY_KEYS,
                     "INVALID_POLICY_RESULT_FIELDS")
            _require(item["policy_id"] == POLICIES[idx] and item["status"] in VALID_STATUS,
                     "INVALID_POLICY_ID_OR_STATUS")
            errors, oracle = item["validator_error_rules"], item["structural_oracle_failures"]
            _require(isinstance(errors, list) and all(isinstance(x, str) for x in errors)
                     and errors == sorted(set(errors)), "INVALID_VALIDATOR_FINDINGS")
            _require(isinstance(oracle, list) and not oracle,
                     "INDEPENDENT_ORACLE_FAILURES_PRESENT")
            status = item["status"]
            if status == "EXPLICITLY_INFEASIBLE":
                _require(case["case_id"] == "low-frequency-two-days" and
                         item["weekly_scheduled_km"] is None and
                         item["weekly_target_km"] is None and
                         item["plan_sha256"] is None and not errors,
                         "INFEASIBILITY_NOT_PRESERVED")
                counts["infeasible"] += 1
            else:
                _require(_sha(item["plan_sha256"]) and
                         _km_list(item["weekly_scheduled_km"]) and
                         _km_list(item["weekly_target_km"]),
                         "MISSING_OR_INVALID_GENERATED_WEEKLY_PLAN")
                if status == "GENERATED_WITH_STRUCTURAL_FINDINGS":
                    _require(item["policy_id"] == "base.fixed_10_percent" and
                             case["case_id"] in {
                                 "intermediate-seed-9",
                                 "completed-quality-followed-by-gap",
                                 "partial-quality-unknown-recovery"
                             } and errors == ["workload.capacity"],
                             "KNOWN_CAPACITY_FAILURE_NOT_PRESERVED")
                    counts["generated_with_validation_errors"] += 1
                else:
                    _require(not errors, "SILENT_VALIDATION_ERROR")
                    counts["generated_without_validation_errors"] += 1

    _require(counts == {
        "generated_without_validation_errors": 33,
        "generated_with_validation_errors": 3,
        "infeasible": 6, "oracle_failures": 0,
    }, "UNEXPECTED_ORIGINAL_LAB_RESULT_COUNTS")
    expected_summary = {
        "cases": 7, "policy_runs": 42, "generated": 36,
        "generated_with_validation_errors": 3,
        "generated_without_validation_errors": 33,
        "infeasible": 6, "oracle_failures": 0,
        "real_athlete_activities_analyzed": 0,
    }
    _require(body["summary"] == expected_summary, "RESULT_SUMMARY_MISMATCH")
    return {
        "schema": "stridelab-original-lab-reviewed-evidence-result-v1",
        "status": "PINNED_SYNTHETIC_POLICY_EVIDENCE_STRUCTURALLY_VERIFIED",
        "evidence_body_sha256": digest,
        "source_baseline_sha": ORIGINAL_ANCHOR,
        "source_repo": REPO,
        "producer_ci_head_claim": pinned["producer_ci_head"],
        "producer_ci_run_id_claim": pinned["producer_ci_run_id"],
        "manual_origin_review_required": True,
        "origin_authenticated_cryptographically": False,
        "cross_repo_source_checkout_performed": False,
        "actual_original_lab_source_executed_by_this_ci": False,
        "real_athlete_records_analyzed": 0,
        "real_performance_effect_demonstrated": False,
        "private_data_exposed": False,
        "original_policy_comparison_rows_checked": 42,
        "original_validation_error_plans_preserved": 3,
        "original_infeasible_plans_preserved": 6,
        "android_engine2_integration_ready": False,
    }


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--handoff", type=Path, required=True)
    p.add_argument("--review-pin", type=Path, required=True)
    p.add_argument("--output", type=Path, default=Path("research-original-evidence-8l.json"))
    args = p.parse_args()
    for f in (args.handoff, args.review_pin):
        if f.suffix != ".json" or f.stat().st_size > 300_000:
            raise HandoffRejected("INVALID_BOUNDED_RESEARCH_JSON_INPUT")
    out = verify(json.loads(args.handoff.read_text(encoding="utf-8")),
                 json.loads(args.review_pin.read_text(encoding="utf-8")))
    args.output.write_text(json.dumps(out, sort_keys=True, indent=2) + "\n",
                           encoding="utf-8")
    print("ORIGINAL_LAB_EVIDENCE_8L:", out["status"])
    print("ORIGIN_INDEPENDENTLY_AUTHENTICATED:", out["origin_authenticated_cryptographically"])
    print("REAL_ATHELETE_OUTCOMES:", out["real_athlete_records_analyzed"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
