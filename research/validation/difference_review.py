#!/usr/bin/env python3
"""Audit every B0/H1 disagreement over frozen Batch 8B scenarios.

This is an evidence *review queue*, not a verdict that H1 is physiologically
better. Classifications are explicitly provisional and cite the output reason.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path

from policy_candidates import run_policy
from policy_runner import FIXTURE, deep_patch

CONSTRAINT_INTERPRETATIONS = {
    "LOWER_BODY_OVERLAP_REVIEW": (
        "qualitative lower-body workload overlap", "needs timing and athlete-specific evidence"),
    "RECENT_UNPLANNED_HARD_WORK": (
        "observed unexpected hard training before quality day", "no validated spacing/dose rule"),
    "UNVERIFIED_CAPACITY": (
        "history/capacity unavailable", "safe next workout dose not established"),
    "RETURN_CAPACITY_UNVERIFIED": (
        "return from training interruption", "break length does not prove readiness"),
    "MISSING_ACTIVITY_IS_NOT_SKIPPED": (
        "missing training record is unknown", "source-sync or athlete confirmation needed"),
    "PAIN_NEEDS_HUMAN_REVIEW": (
        "user-reported pain, not medical clearance", "must not automate a medical diagnosis"),
    "SKILL_PREREQUISITE_OR_FORM": (
        "skill prerequisite or technique unconfirmed", "prerequisite assessment not measured"),
    "EQUIPMENT_MISSING": (
        "requested equipment absent", "specific safe substitution and dose not established"),
    "TIME_BUDGET": (
        "sessions exceed stated time budget", "not equivalent training stimulus after shortening"),
    "CAPACITY_CAP": (
        "configured observed capacity exceeded", "numerical dose ceiling not physiologically validated"),
}


def run_review() -> dict:
    raw = FIXTURE.read_bytes()
    data = json.loads(raw)
    rows = []
    for case in data["cases"]:
        base = deep_patch(data["base_state"], case["base_patch"])
        varied = deep_patch(base, case["variant_patch"])
        compared = []
        for side, state in (("base", base), ("variant", varied)):
            b = run_policy("B0", state)
            h = run_policy("H1", state)
            if b["action"] != h["action"]:
                label, limit = CONSTRAINT_INTERPRETATIONS.get(
                    h["reason"], ("not classified by evidence", "human review required"))
                compared.append({
                    "side": side,
                    "b0_action": b["action"], "b0_reason": b["reason"],
                    "h1_action": h["action"], "h1_reason": h["reason"],
                    "b0_requires_approval": b["revision"]["requires_approval"],
                    "h1_requires_approval": h["revision"]["requires_approval"],
                    "h1_applied": h["revision"]["applied"],
                    "interpretation": label, "limitation": limit,
                    "review_status": "OPEN_NOT_OUTCOME_VALIDATED",
                })
        if compared:
            rows.append({
                "qualified_case_id": case["id"],
                "changed_inputs": sorted(case["variant_patch"].keys()),
                "disagreements": compared,
                "clinical_effectiveness_proven": False,
            })
    return {
        "scope": "manual review queue of NEW B0/H1 research decision disagreements",
        "frozen_fixture_sha256": hashlib.sha256(raw).hexdigest(),
        "policy_sha256": hashlib.sha256(
            (Path(__file__).parent / "policy_candidates.py").read_bytes()
        ).hexdigest(),
        "github_sha": os.environ.get("GITHUB_SHA"),
        "scenarios_with_difference": len(rows),
        "state_comparisons_differing": sum(len(x["disagreements"]) for x in rows),
        "all_differences_unverified_for_coaching_benefit": True,
        "items": rows,
    }


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--output", default="research-decision-differences.json")
    args = p.parse_args()
    report = run_review()
    f = Path(args.output)
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"B0/H1 DISAGREEMENT REVIEW: {report['scenarios_with_difference']} "
          f"scenarios, {report['state_comparisons_differing']} different state comparisons")
    for item in report["items"]:
        for diff in item["disagreements"]:
            print(f"REVIEW {item['qualified_case_id']} [{diff['side']}]: "
                  f"B0={diff['b0_action']} / H1={diff['h1_action']} "
                  f"({diff['h1_reason']})")
    print("All differences require independent human/athlete-outcome review; "
          "no superiority verdict assigned.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
