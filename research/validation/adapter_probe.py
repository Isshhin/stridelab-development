#!/usr/bin/env python3
"""Explicit research implementation inventory. No fictitious local lab adapter.

This probe intentionally refuses to call a non-existent local source. The
research B0 and H1 prototypes can be exercised on a public-synthetic sample,
but neither claims to be the user's original local Athlete Simulation Lab or
Android production Engine 2.0.
"""
from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path

from policy_candidates import run_policy

HERE = Path(__file__).resolve().parent
FIXTURE = HERE / "frozen_policy_cases.json"


def inspect() -> dict:
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    athlete = deepcopy(data["base_state"])
    candidates = []
    for policy in ("B0", "H1"):
        original = deepcopy(athlete)
        result = run_policy(policy, athlete)
        if athlete != original:
            raise AssertionError("Research adapter modified athlete input")
        if result["revision"]["applied"]:
            raise AssertionError("Research adapter applied unapproved proposal")
        if result["forecast"]["actual_outcome"] is not None:
            raise AssertionError("Research adapter invented actual outcome")
        candidates.append({
            "adapter_id": f"cloud_research_{policy}",
            "status": "CONNECTED_RESEARCH_PROTOTYPE_ONLY",
            "policy_id": result["policy_id"],
            "proposal_action": result["action"],
            "proposal_applied": False,
            "capability": "single_context_structural_proposals",
        })
    return {
        "suite": "Research policy adapter boundary audit",
        "date_utc": datetime.now(timezone.utc).isoformat(),
        "commit": os.environ.get("GITHUB_SHA"),
        "fixture_sha256": hashlib.sha256(FIXTURE.read_bytes()).hexdigest(),
        "research_policy_sha256": hashlib.sha256(
            (HERE / "policy_candidates.py").read_bytes()
        ).hexdigest(),
        "adapters": candidates + [{
            "adapter_id": "existing_independent_python_athlete_simulation_lab",
            "status": "BLOCKED_SOURCE_NOT_CONNECTED",
            "policy_id": None,
            "reason": "No accessible, verified source/manifest or compatible runner",
        }, {
            "adapter_id": "android_production_coaching_engine",
            "status": "OUT_OF_SCOPE_NOT_TESTED",
            "policy_id": None,
            "reason": "Research PR has no production integration permission",
        }],
        "provider_credential_verification": "NOT_CONNECTED",
        "claim_real_training_effectiveness": False,
    }


def main() -> int:
    report = inspect()
    destination = Path("research-adapter-status.json")
    destination.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n",
                           encoding="utf-8")
    for entry in report["adapters"]:
        print(f"ADAPTER {entry['adapter_id']}: {entry['status']}")
    print(f"Adapter status report: {destination}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
