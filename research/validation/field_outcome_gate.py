"""Fail-closed admission for future consented field outcome evaluation.

NO workouts are collected or analyzed here. The tool checks only a metadata
handoff, and never upgrades self-declared consent, source validity, or simulated
fitness outputs to independently verified human evidence. Protected FIT and
device recordings must stay out of GitHub Actions.
"""
from __future__ import annotations

import argparse
from datetime import date
import json
import re
from pathlib import Path
from typing import Any

SCHEMA = "stridelab-field-outcome-handoff-v1"
SHA256 = re.compile(r"^[a-f0-9]{64}$")
REQUIRED = ("schema", "protocol_id", "athlete_pseudonym", "dataset_manifest_sha256",
            "measurement_source", "baseline_period", "followup_period",
            "primary_endpoint", "consent_review_status", "quality_review_status",
            "sampling_review_status")


def _period(value: Any) -> bool:
    if not isinstance(value, dict):
        return False
    try:
        begin, end = date.fromisoformat(value["start"]), date.fromisoformat(value["end"])
    except (ValueError, TypeError, KeyError):
        return False
    return begin <= end

def inspect_manifest(data: dict[str, Any] | None) -> dict[str, Any]:
    report = {
        "status": "BLOCKED_NO_CONSENTED_FIELD_DATA",
        "athlete_outcomes_analyzed": 0,
        "actual_performance_effect_demonstrated": False,
        "data_access": "NO_RAW_ACTIVITY_READ",
        "human_consent_verification": "NOT_PERFORMED",
        "quality_review": "NOT_PERFORMED",
        "study_conclusion": "NO_INFERENCE",
        "next_gate": "AUTHENTICATED_CONSENT_AND_INDEPENDENT_SOURCE_REVIEW",
    }
    if data is None:
        return report
    if not isinstance(data, dict) or any(k not in data for k in REQUIRED):
        report.update(status="REJECTED_INCOMPLETE_OUTCOME_HANDOFF")
        return report
    if data["schema"] != SCHEMA or data["primary_endpoint"] != "easy_speed_at_comparable_hr":
        report.update(status="REJECTED_UNREGISTERED_ENDPOINT")
        return report
    if not isinstance(data["protocol_id"], str) or len(data["protocol_id"]) < 5:
        report.update(status="REJECTED_PROTOCOL_ID")
        return report
    if not isinstance(data["athlete_pseudonym"], str) or len(data["athlete_pseudonym"]) < 3:
        report.update(status="REJECTED_ATHLETE_PSEUDONYM")
        return report
    d = data["dataset_manifest_sha256"]
    if not isinstance(d, str) or not SHA256.fullmatch(d):
        report.update(status="REJECTED_DATASET_PROVENANCE")
        return report
    if not isinstance(data["measurement_source"], str) or not data["measurement_source"].strip():
        report.update(status="REJECTED_MEASUREMENT_SOURCE")
        return report
    if not _period(data["baseline_period"]) or not _period(data["followup_period"]):
        report.update(status="REJECTED_PERIODS")
        return report
    if data["baseline_period"]["end"] >= data["followup_period"]["start"]:
        report.update(status="REJECTED_NON_TEMPORAL_SPLIT")
        return report
    if data["consent_review_status"] != "externally_verified":
        report.update(status="BLOCKED_CONSENT_REVIEW")
        return report
    if data["quality_review_status"] != "externally_verified":
        report.update(status="BLOCKED_MEASUREMENT_QUALITY")
        return report
    if data["sampling_review_status"] != "externally_verified":
        report.update(status="BLOCKED_SAMPLING_REVIEW")
        return report
    # The strings above are claims by a metadata producer, not authenticated
    # approvals. This gate can never claim real-world readiness on its own.
    report.update(
        status="READY_FOR_INDEPENDENT_HUMAN_PROVENANCE_REVIEW",
        next_gate="VERIFY_CONSENT_AND_SOURCE_ATTESTATION_OUT_OF_BAND",
    )
    return report


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--manifest", default=None, help="Local metadata JSON only, never FIT")
    p.add_argument("--output", default="research-field-outcome-readiness.json")
    args = p.parse_args()
    data = None
    if args.manifest is not None:
        path = Path(args.manifest)
        if path.suffix.lower() != ".json" or path.stat().st_size > 100_000:
            raise ValueError("Only bounded local metadata JSON is admissible")
        data = json.loads(path.read_text(encoding="utf-8"))
    report = inspect_manifest(data)
    Path(args.output).write_text(json.dumps(report, sort_keys=True, indent=2) + "\n",
                                 encoding="utf-8")
    print("FIELD OUTCOME EVIDENCE:", report["status"])
    print("Athlete observations analyzed:", report["athlete_outcomes_analyzed"])
    print("Effect established:", report["actual_performance_effect_demonstrated"])
    return 0 if not report["status"].startswith("REJECTED") else 1


if __name__ == "__main__":
    raise SystemExit(main())
