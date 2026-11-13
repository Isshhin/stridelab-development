"""Prospective, fail-closed Garmin Run Coach observation-pair gate.

Research-only. Does not reconstruct recommendations from completed activities.
"""
from __future__ import annotations

import argparse
import csv
import json
from datetime import date, datetime
from pathlib import Path

PRE_FIELDS = (
    "observation_id", "captured_at_local", "timezone", "source_surface", "plan_family",
    "plan_goal", "plan_phase", "target_event_date", "device_model", "firmware",
    "garmin_connect_version", "target_mode", "scheduled_workout_date",
    "recommendation_type", "prescribed_duration_min", "prescribed_distance_km",
    "prescribed_hr_range_bpm", "prescribed_pace_range_per_km",
    "acute_load", "chronic_load", "training_status", "training_readiness",
    "recovery_hours", "hrv_ms", "sleep_score", "recent_sessions_summary",
    "last_strength_session_summary", "source_ref", "notes",
)
POST_FIELDS = (
    "observation_id", "actual_start_at_local", "actual_end_at_local", "workout_status",
    "actual_type", "actual_duration_min", "actual_distance_km", "average_hr_bpm",
    "elevation_gain_m", "original_prescription_completed", "completion_source_ref",
    "next_recommendation_captured_at_local", "next_plan_family", "next_recommendation_type",
    "next_prescribed_duration_min", "next_prescribed_distance_km",
    "next_intensity_target", "next_recommendation_source_ref",
    "observed_plan_change", "intervening_events_and_confounders", "notes",
)
RECORDABLE = {"COMPLETED", "PARTIAL", "SUBSTITUTED", "SKIPPED"}
ELIGIBLE_FAMILY = "GARMIN_RUN_COACH"


def load(path: str | Path, required: tuple[str, ...]) -> list[dict[str, str]]:
    with open(path, encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file)
        if reader.fieldnames is None or not set(required).issubset(reader.fieldnames):
            raise ValueError(f"{path}: missing required columns")
        return list(reader)


def instant(raw: str, field: str) -> datetime:
    if not raw.strip():
        raise ValueError(f"{field}: timestamp required")
    try:
        result = datetime.fromisoformat(raw)
    except ValueError as exc:
        raise ValueError(f"{field}: invalid ISO timestamp") from exc
    if result.tzinfo is None or result.utcoffset() is None:
        raise ValueError(f"{field}: missing explicit UTC offset")
    return result


def optional_number(raw: str, field: str, allow_zero: bool = True) -> float | None:
    if not raw.strip():
        return None
    try:
        value = float(raw)
    except ValueError as exc:
        raise ValueError(f"{field}: invalid number") from exc
    if not (value >= 0 if allow_zero else value > 0):
        raise ValueError(f"{field}: negative, zero or non-finite")
    if value == float("inf"):
        raise ValueError(f"{field}: infinite")
    return value


def audit(pre_csv: str | Path, post_csv: str | Path) -> dict:
    pre = load(pre_csv, PRE_FIELDS)
    post = load(post_csv, POST_FIELDS)
    if not pre and not post:
        return {
            "status": "BLOCKED_NO_PROSPECTIVE_RUN_COACH_CAPTURE",
            "pre_count": 0, "post_count": 0, "paired": 0,
            "interpretation_limit": "No real Run Coach recommendations captured",
        }
    if not pre:
        return {
            "status": "BLOCKED_NO_PRE_WORKOUT_CAPTURE", "pre_count": 0,
            "post_count": len(post), "paired": 0,
            "reason": "Completed activities cannot reconstruct pre-workout recommendations",
        }
    by_pre = {}
    for row in pre:
        ident = row["observation_id"].strip()
        if not ident or ident in by_pre:
            raise ValueError("missing/duplicate pre-workout observation_id")
        if row["plan_family"] != ELIGIBLE_FAMILY:
            return {
                "status": "BLOCKED_NOT_GARMIN_RUN_COACH", "offending_observation_id": ident,
                "family": row["plan_family"], "paired": 0,
            }
        if any(not row[field].strip() for field in (
            "timezone", "source_surface", "plan_goal", "plan_phase",
            "device_model", "firmware", "garmin_connect_version",
            "recommendation_type", "source_ref"
        )):
            raise ValueError(f"{ident}: pre-workout provenance or prescription incomplete")
        if row["target_mode"] not in ("HR", "PACE", "AUTO"):
            raise ValueError(f"{ident}: invalid target mode (HR/PACE/AUTO)")
        t = instant(row["captured_at_local"], f"{ident}.captured_at_local")
        try:
            date.fromisoformat(row["scheduled_workout_date"])
        except ValueError as exc:
            raise ValueError(f"{ident}: scheduled date must be ISO YYYY-MM-DD") from exc
        if row["target_event_date"].strip():
            try:
                date.fromisoformat(row["target_event_date"])
            except ValueError as exc:
                raise ValueError(f"{ident}: target event date malformed") from exc
        dur = optional_number(row["prescribed_duration_min"], "prescribed_duration_min", allow_zero=False)
        km = optional_number(row["prescribed_distance_km"], "prescribed_distance_km", allow_zero=False)
        if dur is None and km is None:
            raise ValueError(f"{ident}: no prescribed distance or duration")
        by_pre[ident] = {"row": row, "time": t, "duration": dur, "km": km}
    by_post = {}
    for row in post:
        ident = row["observation_id"].strip()
        if not ident or ident in by_post:
            raise ValueError("missing/duplicate post-workout observation_id")
        if ident not in by_pre:
            raise ValueError(f"{ident}: post-workout record lacks a prospective pre-capture")
        if row["workout_status"] not in RECORDABLE:
            raise ValueError(f"{ident}: invalid workout_status")
        if not row["completion_source_ref"].strip():
            raise ValueError(f"{ident}: missing completion/skip source")
        before = by_pre[ident]["time"]
        started = ended = None
        if row["workout_status"] != "SKIPPED":
            started = instant(row["actual_start_at_local"], f"{ident}.actual_start")
            ended = instant(row["actual_end_at_local"], f"{ident}.actual_end")
            if not before < started <= ended:
                raise ValueError(f"{ident}: pre-workout capture not before actual workout")
            if optional_number(row["actual_duration_min"], "actual_duration_min") is None:
                raise ValueError(f"{ident}: actual duration missing")
        elif row["actual_start_at_local"].strip() or row["actual_end_at_local"].strip():
            raise ValueError(f"{ident}: skipped activity must not have completed start/end")
        actual_km = optional_number(row["actual_distance_km"], "actual_distance_km")
        next_raw = row["next_recommendation_captured_at_local"].strip()
        next_t = instant(next_raw, f"{ident}.next_recommendation_captured_at_local") if next_raw else None
        if next_t:
            if next_t <= (ended or before):
                raise ValueError(f"{ident}: next recommendation not captured after workout/skip")
            if row["next_plan_family"] != ELIGIBLE_FAMILY:
                return {
                    "status": "BLOCKED_NEXT_NOT_GARMIN_RUN_COACH",
                    "observation_id": ident, "next_plan_family": row["next_plan_family"],
                }
            if not row["next_recommendation_source_ref"].strip() or not row["next_recommendation_type"].strip():
                raise ValueError(f"{ident}: next recommendation missing source/type")
        elif any(row[field].strip() for field in (
            "next_plan_family", "next_recommendation_type", "next_recommendation_source_ref",
            "next_prescribed_duration_min", "next_prescribed_distance_km", "next_intensity_target"
        )):
            raise ValueError(f"{ident}: next recommendation values without capture timestamp")
        by_post[ident] = {
            "row": row, "actual_km": actual_km,
            "actual_duration": optional_number(row["actual_duration_min"], "actual_duration_min"),
            "next_captured": next_t is not None,
        }
    if set(by_post) != set(by_pre):
        return {
            "status": "BLOCKED_UNPAIRED_PRE_WORKOUT_CAPTURE",
            "pre_count": len(by_pre), "post_count": len(by_post),
            "missing_post_ids": sorted(set(by_pre) - set(by_post)),
        }
    pairs = []
    for ident in sorted(by_pre):
        pre_row = by_pre[ident]
        post_row = by_post[ident]
        row = post_row["row"]
        planned_km = pre_row["km"]
        actual_km = post_row["actual_km"]
        pairs.append({
            "observation_id": ident,
            "workout_status": row["workout_status"],
            "original_recommendation_type": pre_row["row"]["recommendation_type"],
            "completed_type": row["actual_type"],
            "planned_distance_km": planned_km,
            "actual_distance_km": actual_km,
            "distance_difference_km": round(actual_km - planned_km, 4)
                if actual_km is not None and planned_km is not None else None,
            "planned_duration_min": pre_row["duration"],
            "actual_duration_min": post_row["actual_duration"],
            "next_recommendation_captured": post_row["next_captured"],
            "next_recommendation_type": row["next_recommendation_type"] if post_row["next_captured"] else None,
            "observed_change_label": row["observed_plan_change"],
            "confounders_recorded": bool(row["intervening_events_and_confounders"].strip()),
        })
    if not all(p["next_recommendation_captured"] for p in pairs):
        return {
            "status": "COMPLETION_ONLY_PENDING_NEXT_RECOMMENDATION",
            "paired": len(pairs), "with_next_recommendation": sum(p["next_recommendation_captured"] for p in pairs),
            "pairs": pairs, "adaptive_claim_allowed": False,
        }
    return {
        "status": "DESCRIPTIVE_ONLY_PENDING_SOURCE_REVIEW",
        "paired": len(pairs), "pairs": pairs, "adaptive_claim_allowed": False,
        "interpretation_limit": (
            "A recommendation change following a completed run does not identify "
            "which internal recovery, load, schedule or manual-edit mechanism caused it."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pre", default="docs/research/experiments/experiment-008-garmin-run-coach-pre.csv")
    parser.add_argument("--post", default="docs/research/experiments/experiment-008-garmin-run-coach-post.csv")
    parser.add_argument("--output", default="research-garmin-run-coach-readiness.json")
    args = parser.parse_args()
    try:
        result = audit(args.pre, args.post)
    except ValueError as exc:
        result = {"status": "INVALID_CAPTURE", "reason": str(exc)}
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output:
        Path(args.output).write_text(payload, encoding="utf-8")
    print(payload)
    return 2 if result["status"] == "INVALID_CAPTURE" else 0


if __name__ == "__main__":
    raise SystemExit(main())
