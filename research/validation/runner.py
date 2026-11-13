#!/usr/bin/env python3
"""Run the independent synthetic contract checks and produce auditable results.

Usage:
    python research/validation/runner.py --output research-validation.json

Scope:
- Executes named assertions against contract_model.py (research-only toy code).
- DOES NOT execute the actual StrideLab Engine 1.0/1.5/2.0 or private local lab.
- Every scenario has separate contract_status and engine_status.
- Any failing check, duplicate, missing check, or mismatch is a nonzero CI exit.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import os
import json
import sys
import traceback
from datetime import datetime, timezone
from pathlib import Path

from checks import registered_checks

ROOT = Path(__file__).resolve().parents[2]
REGISTRIES = {
    "E003": ROOT / "docs/research/experiments/experiment-003-load-policy-counterfactuals.csv",
    "E004": ROOT / "docs/research/experiments/experiment-004-multidiscipline-coordination.csv",
    "E005": ROOT / "docs/research/experiments/experiment-005-adaptation-state-and-load-metrics.csv",
}
EXPECTED_COUNTS = {"E003": 18, "E004": 22, "E005": 18}


def registry_rows() -> list[dict[str, str]]:
    result = []
    seen_qualified = set()
    seen_bare = set()
    for experiment, path in REGISTRIES.items():
        if not path.is_file():
            raise ValueError(f"Missing canonical registry: {path}")
        with path.open(newline="", encoding="utf-8") as file:
            rows = list(csv.DictReader(file))
        if len(rows) != EXPECTED_COUNTS[experiment]:
            raise ValueError(
                f"{experiment}: expected {EXPECTED_COUNTS[experiment]}, got {len(rows)}")
        for row in rows:
            raw_id = row["case_id"]
            qualified = f"{experiment}:{raw_id}"
            if qualified in seen_qualified:
                raise ValueError(f"Duplicate qualified case ID: {qualified}")
            seen_qualified.add(qualified)
            if raw_id in seen_bare:
                raise ValueError(
                    f"Overlapping bare ID in E003–E005: {raw_id}; prefix required")
            seen_bare.add(raw_id)
            status = row.get("status", row.get("execution_status"))
            if status != "NOT_RUN":
                raise ValueError(f"Unexpected canonical research status {qualified}: {status}")
            result.append({"experiment": experiment,
                           "case_id": raw_id,
                           "qualified_case_id": qualified,
                           "source_registry": str(path.relative_to(ROOT)),
                           "assertion": row.get("core_assertion")
                                        or row.get("required_invariant")
                                        or row.get("required_assertion")})
    if len(result) != 58:
        raise ValueError(f"Expected 58 unique simulation cases, saw {len(result)}")
    return result


def execute_all() -> dict:
    rows = registry_rows()
    checks = registered_checks()
    expected_ids = {row["case_id"] for row in rows}
    if set(checks) != expected_ids:
        raise ValueError(
            "Check implementation coverage drift: "
            f"missing={sorted(expected_ids-set(checks))}; "
            f"extra={sorted(set(checks)-expected_ids)}")
    report = {
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "suite": "StrideLab research-only synthetic contracts",
        "ci_checkout_sha": os.environ.get("GITHUB_SHA"),
        "canonical_registry_sha256": {
            name: hashlib.sha256(path.read_bytes()).hexdigest()
            for name, path in REGISTRIES.items()
        },
        "harness_sha256": {
            name: hashlib.sha256((Path(__file__).resolve().parent / name).read_bytes()).hexdigest()
            for name in ("runner.py", "checks.py", "contract_model.py", "test_harness.py")
        },
        "version": "synthetic-contract-harness-v0.1",
        "scope": "toy-model structural unit tests; NOT real Engine 2.0 verification",
        "engine_implementation_connected": False,
        "engine_status": "BLOCKED_NO_REAL_ENGINE_ADAPTER",
        "runna_authenticated_e002": "BLOCKED_0_OF_16",
        "garmin_run_coach_pairs": "BLOCKED_NO_PAIRED_OBSERVATIONS",
        "cases": [],
    }
    for row in rows:
        entry = dict(row)
        entry["contract_status"] = "PASS"
        entry["engine_status"] = "BLOCKED_NO_REAL_ENGINE_ADAPTER"
        entry["error"] = None
        try:
            checks[row["case_id"]]()
        except Exception as exc:
            entry["contract_status"] = "FAIL"
            entry["error"] = (f"{type(exc).__name__}: {exc}"
                              if str(exc) else type(exc).__name__)
            entry["traceback"] = traceback.format_exc(limit=4)
        report["cases"].append(entry)

    counts = {"PASS": 0, "FAIL": 0, "BLOCKED": 0}
    by_registry = {}
    for row in report["cases"]:
        counts[row["contract_status"]] += 1
        by_registry.setdefault(row["experiment"], {"PASS": 0, "FAIL": 0})
        by_registry[row["experiment"]][row["contract_status"]] += 1
    report["contract_results"] = counts
    report["contract_by_experiment"] = by_registry
    report["engine_results"] = {
        "PASS": 0, "FAIL": 0, "BLOCKED": len(report["cases"]),
    }
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="research-validation.json")
    args = parser.parse_args()
    output = Path(args.output)
    try:
        report = execute_all()
    except Exception as exc:
        print(f"HARNESS ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    c = report["contract_results"]
    print(f"SYNTHETIC CONTRACT CHECKS: {c['PASS']} PASS, {c['FAIL']} FAIL")
    print(f"REAL ENGINE VALIDATION: 0 PASS, {len(report['cases'])} BLOCKED")
    for section, section_counts in report["contract_by_experiment"].items():
        print(f"{section}: {section_counts['PASS']} PASS / {section_counts['FAIL']} FAIL")
    for row in report["cases"]:
        if row["contract_status"] == "FAIL":
            print(f"FAIL {row['qualified_case_id']}: {row['error']}", file=sys.stderr)
    print(f"JSON report: {output}")
    return 1 if c["FAIL"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
