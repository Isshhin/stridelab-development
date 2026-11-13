#!/usr/bin/env python3
"""Batch 8B frozen synthetic input testing of NEW independent research policies.

B0 and H1 are independently executable policy implementations, not the user's
historical local Athlete Simulation Lab or the actual Android Engine 2.0.
Assertions are stored separately in frozen_policy_cases.json and evaluated
against outputs. No prediction/effectiveness or medical safety is verified.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from policy_candidates import run_policy

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
FIXTURE = HERE / "frozen_policy_cases.json"
EXPECTED = {
    "E003": ("docs/research/experiments/experiment-003-load-policy-counterfactuals.csv", 18),
    "E004": ("docs/research/experiments/experiment-004-multidiscipline-coordination.csv", 22),
    "E005": ("docs/research/experiments/experiment-005-adaptation-state-and-load-metrics.csv", 18),
}
OPERATORS = {"eq", "ne", "gt", "lt", "eq_ref", "ne_ref", "gt_ref", "lt_ref", "approx"}


def deep_patch(original: dict, patch: dict) -> dict:
    merged = deepcopy(original)
    for k, v in patch.items():
        if k in merged and isinstance(merged[k], dict) and isinstance(v, dict):
            merged[k] = deep_patch(merged[k], v)
        else:
            merged[k] = deepcopy(v)
    return merged


def get_path(outputs: dict[str, Any], path: str) -> Any:
    parts = path.split(".")
    if len(parts) < 3 or parts[0] not in ("B0", "H1") or parts[1] not in ("base", "variant"):
        raise ValueError(f"Invalid independent expectation path: {path}")
    x: Any = outputs
    for key in parts:
        if not isinstance(x, dict) or key not in x:
            raise KeyError(f"Missing output field: {path}")
        x = x[key]
    return x


def verify_expectation(outputs: dict, check: list) -> tuple[bool, str]:
    if not isinstance(check, list) or len(check) != 3:
        raise ValueError(f"Invalid fixture assertion {check!r}")
    path, operator, expected = check
    if operator not in OPERATORS:
        raise ValueError(f"Unsupported independent operator: {operator}")
    actual = get_path(outputs, path)
    if operator.endswith("_ref"):
        expected = get_path(outputs, expected)
    if operator in ("eq", "eq_ref"):
        ok = actual == expected
    elif operator in ("ne", "ne_ref"):
        ok = actual != expected
    elif operator in ("gt", "gt_ref"):
        ok = actual > expected
    elif operator in ("lt", "lt_ref"):
        ok = actual < expected
    elif operator == "approx":
        ok = isinstance(actual, (int, float)) and abs(actual - expected) <= 1e-7
    else:
        raise ValueError(operator)
    return ok, f"{path} {operator} {expected!r} (actual={actual!r})"


def canonical_ids() -> list[str]:
    ids: list[str] = []
    for prefix, (relative, count) in EXPECTED.items():
        with (ROOT / relative).open(newline="", encoding="utf-8") as stream:
            rows = list(csv.DictReader(stream))
        if len(rows) != count:
            raise ValueError(f"{prefix} registry count changed: {len(rows)} != {count}")
        for row in rows:
            status = row.get("status", row.get("execution_status"))
            if status != "NOT_RUN":
                raise ValueError(f"{prefix} canonical status changed: {status}")
            ids.append(f"{prefix}:{row['case_id']}")
    if len(ids) != 58 or len(set(ids)) != 58:
        raise ValueError("Expected 58 unique qualified canonical cases")
    return ids


def validate_fixture(fixture: dict) -> list[dict]:
    if fixture["schema"] != "stride-research-8b-frozen-v1":
        raise ValueError("Unknown fixture schema")
    cases = fixture["cases"]
    canonical = canonical_ids()
    ids = [c["id"] for c in cases]
    if ids != canonical:
        raise ValueError(
            f"Fixture drift: missing={sorted(set(canonical) - set(ids))}; "
            f"extra={sorted(set(ids) - set(canonical))}; order may have changed")
    for row in cases:
        if len(row["checks"]) < 2:
            raise ValueError(f"Insufficient independent expectations: {row['id']}")
        for check in row["checks"]:
            if len(check) != 3 or check[1] not in OPERATORS:
                raise ValueError(f"Invalid check {row['id']}: {check}")
        if row.get("blocked_reason") and not row["blocked_reason"].startswith("NO_"):
            raise ValueError(f"Unrecognized blocked reason: {row['id']}")
    return cases


def run_suite() -> dict:
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
    cases = validate_fixture(fixture)
    report: dict[str, Any] = {
        "suite": "Batch 8B new B0/H1 candidate structural comparison",
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "commit": os.environ.get("GITHUB_SHA"),
        "fixture_sha256": hashlib.sha256(FIXTURE.read_bytes()).hexdigest(),
        "policy_sha256": hashlib.sha256((HERE / "policy_candidates.py").read_bytes()).hexdigest(),
        "evaluator_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "scope": "NEW research candidates only, NOT user's local lab or Android Engine 2.0",
        "claims_outcome_effectiveness": False,
        "cases": [],
    }
    decisions_differ = 0
    for item in cases:
        baseline = deep_patch(fixture["base_state"], item["base_patch"])
        variant = deep_patch(baseline, item["variant_patch"])
        snapshots = {"base": baseline, "variant": variant}
        outputs = {
            policy: {name: run_policy(policy, snapshot)
                     for name, snapshot in snapshots.items()}
            for policy in ("B0", "H1")
        }
        errors: list[str] = []
        checked = 0
        for check in item["checks"]:
            try:
                ok, message = verify_expectation(outputs, check)
                checked += 1
                if not ok:
                    errors.append(message)
            except (TypeError, ValueError, KeyError, AssertionError) as error:
                errors.append(f"{check!r}: {type(error).__name__}: {error}")
        # Independent invariant: neither research policy may apply its own
        # proposed change or alter the original plan record.
        for policy in ("B0", "H1"):
            for label in ("base", "variant"):
                out = outputs[policy][label]
                expected_week = snapshots[label]["weekly"]
                rev = out["revision"]
                if (rev["applied"] is not False or
                        rev["before"] != expected_week or
                        rev["after"] != expected_week):
                    errors.append(f"{policy}/{label}: unapproved revision mutated plan")
                if out["action"].startswith("PROPOSE") and not rev["requires_approval"]:
                    errors.append(f"{policy}/{label}: proposal without approval gate")
                if out["forecast"]["actual_outcome"] is not None:
                    errors.append(f"{policy}/{label}: forecast mistaken for observed outcome")
        if item.get("blocked_reason"):
            status = "BLOCKED_UNCALIBRATED_PREDICTION" if not errors else "FAIL"
        else:
            status = "PASS" if not errors else "FAIL"
        diff = any(outputs["B0"][label]["action"] != outputs["H1"][label]["action"]
                   for label in ("base", "variant"))
        if diff:
            decisions_differ += 1
        report["cases"].append({
            "case_id": item["id"],
            "status": status,
            "scope": "structural_test_new_candidate_policies",
            "blocked_reason": item.get("blocked_reason"),
            "checks_evaluated": checked,
            "errors": errors,
            "input_sha256": hashlib.sha256(json.dumps(
                snapshots, sort_keys=True, separators=(",", ":")
            ).encode("utf-8")).hexdigest(),
            "actions": {
                p: {label: outputs[p][label]["action"] for label in ("base", "variant")}
                for p in ("B0", "H1")
            },
            "decisions_differ": diff,
        })
    totals = {"PASS": 0, "FAIL": 0, "BLOCKED_UNCALIBRATED_PREDICTION": 0}
    registry_totals: dict[str, dict[str, int]] = {}
    for c in report["cases"]:
        totals[c["status"]] += 1
        name = c["case_id"].split(":")[0]
        registry_totals.setdefault(name, {key: 0 for key in totals})
        registry_totals[name][c["status"]] += 1
    report["counts"] = totals
    report["per_experiment"] = registry_totals
    report["cases_with_B0_H1_action_difference"] = decisions_differ
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="research-policy-comparison.json")
    args = parser.parse_args()
    result = run_suite()
    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    totals = result["counts"]
    print("NEW B0/H1 RESEARCH POLICY VALIDATION: "
          f"{totals['PASS']} PASS, {totals['FAIL']} FAIL, "
          f"{totals['BLOCKED_UNCALIBRATED_PREDICTION']} BLOCKED")
    print(f"CASES WITH B0/H1 ACTION DIFFERENCES: {result['cases_with_B0_H1_action_difference']}")
    for name, values in result["per_experiment"].items():
        print(f"{name}: {values}")
    for c in result["cases"]:
        if c["status"] == "FAIL":
            print(f"FAIL {c['case_id']}: {c['errors']}")
    print("LOCAL PRE-EXISTING ENGINE 2.0 VALIDATION: BLOCKED_NO_SOURCE")
    print(f"Report: {path}")
    return 1 if totals["FAIL"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
