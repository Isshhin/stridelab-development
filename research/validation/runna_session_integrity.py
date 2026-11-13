"""Validate Runna E007 session transcriptions against captured weekly plans.

A research-only capture audit, never a vendor API client or an injury predictor.
"""
from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from datetime import date, timedelta
from pathlib import Path

from runna_frequency_comparison import CASE_FIELDS, WEEK_FIELDS, EXPECTED, load_csv, positive_float, whole_number

SESSION_FIELDS = (
    "case_id", "week_index", "scheduled_date", "day_of_week", "session_index",
    "session_category", "prescribed_total_km", "prescribed_hard_work_minutes",
    "is_long_run", "workout_step_detail_ref", "source_ref", "notes",
)
START_DATE = date(2026, 11, 9)
EXPECTED_WEEK_COUNT = 12
DAYS = ("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")


def audit(case_path: str | Path, week_path: str | Path, session_path: str | Path) -> dict:
    cases = load_csv(case_path, CASE_FIELDS)
    weeks = load_csv(week_path, WEEK_FIELDS)
    sessions = load_csv(session_path, SESSION_FIELDS)
    if len(cases) != 6 or set(r["case_id"] for r in cases) != set(EXPECTED):
        raise ValueError("exactly six distinct preregistered cases required")
    case_map = {r["case_id"]: r for r in cases}
    if not sessions:
        return {
            "status": "BLOCKED_NO_SESSION_OUTPUT", "evidence": "REGISTERED_HEADER_ONLY",
            "session_rows": 0, "observed_vendor_plans": 0,
            "interpretation_limit": "No session-level Runna output has been captured",
        }
    if any(r["status"] != "GENERATED" for r in cases):
        return {
            "status": "BLOCKED_NO_COMPLETE_GENERATED_COHORT",
            "session_rows": len(sessions), "generated_cases": sum(r["status"] == "GENERATED" for r in cases),
            "interpretation_limit": "Do not reconcile sessions against non-generated plan cases",
        }
    config = (
        "baseline_km", "recent_long_km", "ability", "current_5k", "plan_type",
        "target_date", "plan_weeks", "difficulty", "volume", "strength_sessions",
    )
    if any(row["baseline_km"] != "65" or row["recent_long_km"] != "18" or
           row["plan_weeks"] != "12" for row in cases):
        raise ValueError("preregistered E007 inputs changed")
    if any(any(r[k] != cases[0][k] for k in config) for r in cases):
        raise ValueError("E007 case inputs are not matched")
    if len({r["app_version"] for r in cases}) != 1 or not cases[0]["app_version"].strip():
        return {"status": "BLOCKED_APP_VERSION_MISMATCH", "session_rows": len(sessions)}
    if any(not all(r[k].strip() for k in ("source_ref", "generated_at")) for r in cases):
        raise ValueError("case evidence provenance or generation timestamp is missing")
    weekly = {}
    for row in weeks:
        cid = row["case_id"]
        if cid not in EXPECTED:
            raise ValueError(f"unknown week case: {cid}")
        n = whole_number(row["week_index"], "week_index")
        if n not in range(1, 13) or (cid, n) in weekly:
            raise ValueError("invalid or duplicate case/week in weekly capture")
        if not row["source_ref"].strip():
            raise ValueError("week source reference is missing")
        total = positive_float(row["total_km"], "total_km")
        long = positive_float(row["long_run_km"], "long_run_km", allow_zero=True)
        runs = whole_number(row["run_days"], "run_days")
        rest = whole_number(row["recovery_days"], "recovery_days")
        if runs + rest != 7 or not (0 <= runs <= 7) or long > total:
            raise ValueError("weekly volume or run-day accounting invalid")
        weekly[cid, n] = {
            "total_km": total, "long_run_km": long, "run_days": runs,
            "source_ref": row["source_ref"], "phase": row["phase"],
        }
    if len(weekly) != 72:
        return {
            "status": "BLOCKED_INCOMPLETE_WEEKLY_CAPTURE",
            "observed_week_rows": len(weekly), "expected_week_rows": 72,
        }
    parsed = defaultdict(list)
    session_keys = set()
    for row in sessions:
        cid = row["case_id"]
        if cid not in EXPECTED:
            raise ValueError(f"unknown session case: {cid}")
        n = whole_number(row["week_index"], "week_index")
        index = whole_number(row["session_index"], "session_index")
        if not (1 <= n <= 12) or index <= 0:
            raise ValueError("invalid session week/index")
        key = (cid, n, index)
        if key in session_keys:
            raise ValueError(f"duplicate session identity: {key}")
        session_keys.add(key)
        try:
            scheduled = date.fromisoformat(row["scheduled_date"])
        except ValueError as exc:
            raise ValueError("invalid scheduled_date, expected YYYY-MM-DD") from exc
        first = START_DATE + timedelta(weeks=n - 1)
        if not (first <= scheduled < first + timedelta(days=7)):
            raise ValueError(f"{key}: scheduled date outside registered calendar week")
        if row["day_of_week"] != DAYS[scheduled.weekday()]:
            raise ValueError(f"{key}: weekday mismatch")
        distance = positive_float(row["prescribed_total_km"], "prescribed_total_km")
        raw_hard = row["prescribed_hard_work_minutes"].strip()
        hard = positive_float(raw_hard, "prescribed_hard_work_minutes", allow_zero=True) if raw_hard else None
        is_long = row["is_long_run"].strip().lower()
        if is_long not in ("true", "false"):
            raise ValueError(f"{key}: is_long_run must be true or false")
        if not row["session_category"].strip() or not row["source_ref"].strip():
            raise ValueError(f"{key}: missing session category or source")
        parsed[cid, n].append({
            "session_index": index, "date": scheduled.isoformat(), "distance_km": distance,
            "hard_minutes": hard, "category": row["session_category"].strip(),
            "long": is_long == "true", "source_ref": row["source_ref"],
        })
    issues = []
    summaries = {}
    for cid in sorted(EXPECTED):
        by_week = []
        for n in range(1, 13):
            data = weekly[cid, n]
            items = parsed.get((cid, n), [])
            if not items:
                issues.append({"case_id": cid, "week": n, "code": "MISSING_SESSION_WEEK"})
                continue
            unique_dates = {s["date"] for s in items}
            sum_km = sum(s["distance_km"] for s in items)
            long_items = [s for s in items if s["long"]]
            # Each card rounded to 0.1 km can contribute <=0.05 km rounding error.
            # Allow at least 0.1 km and at most 0.05 km per captured session; if the
            # app uses coarser rounding, transcribe with explicit notes and review.
            tolerance_km = max(0.1, 0.05 * len(items) + 1e-6)
            if len(unique_dates) != data["run_days"]:
                issues.append({
                    "case_id": cid, "week": n, "code": "RUN_DAY_COUNT_MISMATCH",
                    "captured": len(unique_dates), "weekly": data["run_days"],
                })
            if abs(sum_km - data["total_km"]) > tolerance_km:
                issues.append({
                    "case_id": cid, "week": n, "code": "WEEKLY_DISTANCE_MISMATCH",
                    "session_sum_km": round(sum_km, 4), "weekly_km": data["total_km"],
                    "tolerance_km": round(tolerance_km, 4),
                })
            if data["long_run_km"] > 0 and len(long_items) != 1:
                issues.append({
                    "case_id": cid, "week": n, "code": "LONG_RUN_COUNT_MISMATCH",
                    "captured": len(long_items), "expected": 1,
                })
            if data["long_run_km"] == 0 and long_items:
                issues.append({"case_id": cid, "week": n, "code": "UNEXPECTED_LONG_RUN"})
            if len(long_items) == 1 and abs(long_items[0]["distance_km"] - data["long_run_km"]) > 0.11:
                issues.append({
                    "case_id": cid, "week": n, "code": "LONG_RUN_DISTANCE_MISMATCH",
                    "session_long_km": long_items[0]["distance_km"],
                    "weekly_long_km": data["long_run_km"],
                })
            by_week.append({
                "week": n, "phase": data["phase"], "session_count": len(items),
                "run_days": len(unique_dates), "weekly_card_km": data["total_km"],
                "session_distance_total_km": round(sum_km, 4),
                "largest_prescribed_session_km": max(s["distance_km"] for s in items),
                "largest_session_share_of_week": round(max(s["distance_km"] for s in items) / sum_km, 4),
                "categories": dict(sorted(Counter(s["category"] for s in items).items())),
                "hard_minutes_available_for_all_sessions": all(s["hard_minutes"] is not None for s in items),
            })
        summaries[cid] = by_week
    if issues:
        return {
            "status": "BLOCKED_SESSION_RECONCILIATION",
            "evidence": "MANUAL_TRANSCRIPTION_NOT_AUTHENTICATED",
            "session_rows": len(sessions), "issue_count": len(issues),
            "issues": issues[:100], "issue_list_truncated": len(issues) > 100,
        }
    return {
        "status": "DESCRIPTIVE_ONLY_PENDING_CAPTURE_REVIEW",
        "evidence": "MANUAL_TRANSCRIPTION_NOT_AUTHENTICATED",
        "session_rows": len(sessions), "complete_case_weeks": 72,
        "per_case_weeks": summaries,
        "interpretation_limit": (
            "Planned-session comparisons only. Do not use a published observed-injury "
            "threshold from completed runs as a universal safety gate for prescriptions."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", default="docs/research/experiments/experiment-007-runna-frequency-cases.csv")
    parser.add_argument("--weeks", default="docs/research/experiments/experiment-007-runna-frequency-weeks.csv")
    parser.add_argument("--sessions", default="docs/research/experiments/experiment-007-runna-frequency-sessions.csv")
    parser.add_argument("--output", default="research-runna-session-readiness.json")
    args = parser.parse_args()
    try:
        report = audit(args.cases, args.weeks, args.sessions)
    except ValueError as exc:
        report = {"status": "INVALID_CAPTURE", "reason": str(exc)}
    output = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        Path(args.output).write_text(output, encoding="utf-8")
    print(output)
    return 2 if report["status"] == "INVALID_CAPTURE" else 0


if __name__ == "__main__":
    raise SystemExit(main())
