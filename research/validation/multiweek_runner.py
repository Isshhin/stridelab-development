#!/usr/bin/env python3
"""Batch 8D multiweek synthetic policy and event reconciliation experiment.

4 original seeded synthetic profiles x 8 weeks, comparing NEW research B0/H1.
No athlete measurements, predictive-model learning, valid training prescriptions,
actual Android Room persistence, or user's pre-existing local lab are involved.
"""
from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timezone
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import tempfile
from typing import Any

from multiweek_reconciliation import ingest_source_batch, reconcile_week
from policy_candidates import run_policy
from sqlite_ledger import Conflict, ResearchPlanStore

PROFILE_NAMES = ("STEADY", "INTERRUPTED", "CROSS_DISCIPLINE", "SENSOR_GAPS")
WEEKS = 8
SEED = 82604
WEEK_KM = (60, 64, 68, 52, 72, 58, 65, 45)
PROFILE_CAPS = {"STEADY": 75, "INTERRUPTED": 65,
                "CROSS_DISCIPLINE": 70, "SENSOR_GAPS": 70}


def sha(data: Any) -> str:
    return hashlib.sha256(json.dumps(data, sort_keys=True,
                                    separators=(",", ":")).encode("utf-8")).hexdigest()


def plan_for(profile: str, week: int) -> dict[str, Any]:
    """Research-only illustrative sequence, never a training recommendation."""
    target = WEEK_KM[week - 1]
    ratios = (.18, .15, .14, .13)
    distances = [round(target * ratio, 3) for ratio in ratios]
    distances.append(round(target - sum(distances), 3))
    ids = ("quality", "easy-a", "easy-b", "easy-c", "long")
    days = ("Tue", "Wed", "Thu", "Fri", "Sun")
    sessions = []
    for i, (label, km, day) in enumerate(zip(ids, distances, days)):
        sessions.append({
            "id": f"{profile}-W{week}-{label}", "discipline": "running",
            "kind": "threshold" if i == 0 else ("long_run" if i == 4 else "easy"),
            "day": day, "planned_km": km,
            "planned_hard_s": 1800 if i == 0 else 0,
            "planned_easy_s": int(km * 420) if i != 0 else 1200,
            "planned_sets": None,
        })
    for label, day in (("strength-upper", "Thu"), ("strength-lower", "Sat")):
        sessions.append({
            "id": f"{profile}-W{week}-{label}",
            "discipline": "strength", "kind": label, "day": day,
            "planned_km": 0.0, "planned_hard_s": 0,
            "planned_easy_s": 0, "planned_sets": 3,
        })
    return {"week": week, "phase": "deload" if week in (4, 8) else "build",
            "planned_km": target, "sessions": sessions,
            "source": "synthetic_fixture_v1"}


def create_event(profile: str, week: int, planned: dict[str, Any],
                 *, status: str = "completed", source: str = "manual",
                 external: str | None = None, canonical: str | None = "as_plan",
                 multiplier: float = 1.0) -> dict[str, Any]:
    if canonical == "as_plan":
        canonical = planned["id"]
    actual = status in {"completed", "partial", "unplanned"}
    running = planned["discipline"] == "running"
    return {
        "week": week, "source": source,
        "external_id": external or f"{planned['id']}-{source}",
        "verified_session_id": canonical,
        "discipline": planned["discipline"],
        "status": status,
        "actual_km": round(planned["planned_km"] * multiplier, 3)
                     if actual and running else None,
        "actual_hard_s": int(planned["planned_hard_s"] * multiplier)
                         if actual and running else None,
        "actual_easy_s": int(planned["planned_easy_s"] * multiplier)
                         if actual and running else None,
        "actual_sets": int(planned["planned_sets"] * multiplier)
                       if actual and not running else None,
    }


def generate_week_events(profile: str, week: int,
                         plan: dict[str, Any]) -> list[dict[str, Any]]:
    items = []
    for session in plan["sessions"]:
        status, multiplier = "completed", 1.0
        if profile == "INTERRUPTED":
            if week == 2 and session["kind"] == "threshold":
                status, multiplier = "partial", .5
            if week == 3 and session["kind"] == "threshold":
                status = "skipped"
            if week == 4 and session["kind"] == "easy" and session["day"] == "Wed":
                continue  # UNKNOWN is not a confirmed skipped training session.
        if profile == "CROSS_DISCIPLINE" and week == 4 and session["kind"] == "strength-lower":
            status, multiplier = "partial", 2 / 3
        if profile == "SENSOR_GAPS" and week == 6 and session["kind"] == "easy" and session["day"] == "Fri":
            continue
        event = create_event(profile, week, session, status=status,
                             multiplier=multiplier)
        items.append(event)
        if (profile == "STEADY" and week in (2, 5) and
                session["kind"] == "threshold"):
            items.append(create_event(profile, week, session,
                                      source="wearable", external=f"fit-{session['id']}",
                                      status=status, multiplier=multiplier))
        if (profile == "SENSOR_GAPS" and week == 4
                and session["kind"] == "threshold"):
            # Same verified session but conflicting numeric dose. QUARANTINE.
            items.append(create_event(profile, week, session,
                                      source="wearable", external=f"fit-{session['id']}",
                                      multiplier=.9))
    if profile == "INTERRUPTED" and week == 5:
        extra = {"id": f"{profile}-W{week}-unplanned-hard", "discipline": "running",
                 "planned_km": 4.5, "planned_hard_s": 900,
                 "planned_easy_s": 0, "planned_sets": None}
        items.append(create_event(profile, week, extra, source="wearable",
                                  status="unplanned"))
    if profile == "SENSOR_GAPS" and week == 5:
        phantom = {"id": "unverified-activity", "discipline": "running",
                   "planned_km": 6.2, "planned_hard_s": 300,
                   "planned_easy_s": 1200, "planned_sets": None}
        items.append(create_event(profile, week, phantom, source="wearable",
                                  canonical=None))
    return items


def weekly_policy_state(profile: str, week: int,
                        plan: dict[str, Any], rec: dict[str, Any]) -> dict[str, Any]:
    quality = plan["sessions"][0]["id"]
    q = rec["observations"][quality]
    status = q["status"]
    if status in ("conflict", "unknown"):
        status = "unknown"
    # Divide a quality session's distance and duration into distinct workout
    # steps; total session kilometers are NOT all threshold-work kilometers.
    quality = plan["sessions"][0]
    planned_hard_km = round(quality["planned_km"] * .6, 6)
    planned = [
        {"kind": "easy", "km": round(quality["planned_km"] - planned_hard_km, 6),
         "seconds": quality["planned_easy_s"]},
        {"kind": "hard", "km": planned_hard_km,
         "seconds": quality["planned_hard_s"]},
    ]
    q_actual = q["observed"]
    actual_steps = None
    if status in ("completed", "partial") and q_actual:
        actual_hard_km = round(q_actual["actual_km"] * .6, 6)
        actual_steps = [
            {"kind": "easy", "km": round(q_actual["actual_km"] - actual_hard_km, 6),
             "seconds": q_actual["actual_easy_s"]},
            {"kind": "hard", "km": actual_hard_km,
             "seconds": q_actual["actual_hard_s"]},
        ]
    return {
        "athlete": {"verified_capacity_km": PROFILE_CAPS[profile],
                    "phase": plan["phase"]},
        "weekly": {"planned_km": plan["planned_km"]},
        "day": {"durations_m": [50, 35], "minutes_available": 110,
                "max_sessions": 2},
        "session": {"status": status, "planned_steps": planned,
                    "actual_steps": actual_steps,
                    "hr_bpm": None if profile == "SENSOR_GAPS" and week == 2 else 140,
                    "hr_quality": "missing" if profile == "SENSOR_GAPS" and week == 2
                                  else "valid"},
        "environment": {
            "temperature_c": 38 if profile == "SENSOR_GAPS" and week == 3 else 28,
            "route_class": "tight_loop" if profile == "SENSOR_GAPS" and week == 3
                           else "road", "descent_m": 5,
        },
        "stress": {"method": "generic_pace" if profile == "SENSOR_GAPS" and week == 3
                   else "generic_hr",
                   "score": 70, "calibration_version": "test-v1",
                   "threshold_verified": True,
                   "gps_verified": not (profile == "SENSOR_GAPS" and week == 3),
                   "grade_verified": True},
        "feedback": {"pain": profile == "CROSS_DISCIPLINE" and week == 6},
        "recent": ([{"movement": "lower_body_strength", "status": "completed"}]
                   if profile == "CROSS_DISCIPLINE" and week in (3, 5) else []),
        "next": {"kind": "threshold"},
        "equipment": {"required": ["barbell"] if profile == "CROSS_DISCIPLINE" and week == 4 else [],
                      "available": ["bodyweight", "dumbbell"]},
        "skill": {"required": [], "mastered": [], "form_quality": "good"},
        "request_adaptation": status in ("skipped", "partial", "unknown"),
        "return_from_break": profile == "INTERRUPTED" and week == 5,
    }


def evaluate_one(profile: str, root: Path) -> dict[str, Any]:
    db_path = root / f"{profile}.sqlite3"
    db = ResearchPlanStore(db_path)
    problems: list[str] = []
    weeks = []
    accepted_revision_count = 0
    rejected_revision_count = 0
    rollback_count = 0
    recorded_source_keys: set[str] = set()
    all_initial_activities: dict[str, Any] = {}
    try:
        db.initialize(plan_for(profile, 1))
        for week in range(1, WEEKS + 1):
            ideal = plan_for(profile, week)
            if week > 1:
                version, prior = db.current()
                pid = f"{profile}-rollover-{week}"
                db.propose(pid, ideal, "Synthetic athlete explicitly approved next-week schedule",
                           "EXPLICIT_FIXTURE_CHANGE_NOT_AI", expected_version=version)
                if db.current() != (version, prior):
                    problems.append(f"W{week}: proposal silently changed plan")
                db.decide(pid, "approve", expected_version=version)
                accepted_revision_count += 1
            current_version, current_plan = db.current()
            if current_plan != ideal:
                problems.append(f"W{week}: incorrect committed week plan")
            if week == 2:
                candidate = deepcopy(current_plan)
                # Deliberate multi-session scheduling proposal (NOT prescribed).
                candidate["sessions"][0]["day"] = "Mon"
                candidate["sessions"][-1]["day"] = "Fri"
                db.propose(f"{profile}-multisession-edit", candidate,
                           "Explicit synthetic user two-session edit",
                           "RESEARCH_NOT_CLINICAL", expected_version=current_version)
                db.decide(f"{profile}-multisession-edit", "approve",
                          expected_version=current_version)
                accepted_revision_count += 1
                after_version, after_plan = db.current()
                if after_plan != candidate:
                    problems.append(f"W{week}: multi-session approval not persisted")
                db.rollback(f"{profile}-multisession-edit", expected_version=after_version)
                rollback_count += 1
                if db.current()[1] != ideal:
                    problems.append(f"W{week}: rollback failed to restore plan")
            if week == 3:
                v, before = db.current()
                db.propose(f"{profile}-rejected", {"invalid_plan": True},
                           "Fixture explicit rejection",
                           "RESEARCH_NOT_CLINICAL", expected_version=v)
                db.decide(f"{profile}-rejected", "reject", expected_version=v)
                rejected_revision_count += 1
                if db.current() != (v, before):
                    problems.append(f"W{week}: rejected edit changed plan")
            events = generate_week_events(profile, week, ideal)
            before = db.activity_snapshot()
            if week == 7:
                try:
                    ingest_source_batch(db, events, inject_after=2)
                    problems.append(f"W{week}: interruption injection failed to raise")
                except RuntimeError as exc:
                    if str(exc) != "SIMULATED_BATCH_INTERRUPTION":
                        raise
                if before != db.activity_snapshot():
                    problems.append(f"W{week}: partial event batch committed")
            inserted = ingest_source_batch(db, events)
            again = ingest_source_batch(db, events)
            if again["inserted"] != 0 or again["replayed"] != len(events):
                problems.append(f"W{week}: replay duplicated an observation")
            for source, item in db.activity_snapshot():
                if source in recorded_source_keys and source not in all_initial_activities:
                    problems.append(f"W{week}: existing source key changed")
                recorded_source_keys.add(source)
                if source in all_initial_activities and all_initial_activities[source] != item:
                    problems.append(f"W{week}: an activity was overwritten")
                all_initial_activities.setdefault(source, deepcopy(item))
            if week == 4:
                snapshot_before = db.activity_snapshot()
                current_before = db.current()
                db.close()
                db = ResearchPlanStore(db_path)
                if db.current() != current_before or db.activity_snapshot() != snapshot_before:
                    problems.append(f"W{week}: restart changed persisted state")
            rec = reconcile_week(ideal["sessions"], db.activity_snapshot(), week)
            state = weekly_policy_state(profile, week, ideal, rec)
            before_state = deepcopy(state)
            b0 = run_policy("B0", state)
            h1 = run_policy("H1", state)
            if before_state != state:
                problems.append(f"W{week}: policy mutated input")
            if db.current()[1] != ideal:
                problems.append(f"W{week}: policy proposal silently persisted")
            if not (h1["revision"]["applied"] is False and
                    h1["revision"]["before"] == state["weekly"] and
                    h1["revision"]["after"] == state["weekly"]):
                problems.append(f"W{week}: H1 applied unapproved revision")
            if rec["missing_or_unknown"] and rec["total_actual_complete"]:
                problems.append(f"W{week}: unknown session treated as complete week")
            if rec["conflicts"] and rec["total_actual_complete"]:
                problems.append(f"W{week}: conflicting source treated as complete week")
            if rec["unlinked_source_records"] and rec["total_actual_complete"]:
                problems.append(f"W{week}: unlinked record was marked resolved")
            if len(rec["conflicts"]) and profile != "SENSOR_GAPS":
                problems.append(f"W{week}: unexpected source conflict")
            if len(rec["unlinked_source_records"]) and profile != "SENSOR_GAPS":
                problems.append(f"W{week}: unexpected unlinked source")
            if rec["planned_running_km"] != ideal["planned_km"]:
                problems.append(f"W{week}: planned running totals drift")
            if rec["known_actual_running_km"] < 0 or rec["known_actual_hard_s"] < 0:
                problems.append(f"W{week}: invalid known-only actuals")
            if h1["action"].startswith("PROPOSE") and not h1["revision"]["requires_approval"]:
                problems.append(f"W{week}: no approval on H1 proposal")
            weeks.append({
                "week": week, "phase": ideal["phase"],
                "plan_version": db.current()[0],
                "b0_action": b0["action"], "h1_action": h1["action"],
                "action_diff": b0["action"] != h1["action"],
                "source_events_inserted": inserted["inserted"],
                "sources_replayed": again["replayed"],
                "reconciliation": {
                    k: v for k, v in rec.items() if k != "observations"
                },
                "plan_sha256": sha(db.current()[1]),
                "observed_snapshot_sha256": sha(db.activity_snapshot()),
                "state_sha256": sha(state),
            })
        if db.integrity_check() != "ok":
            problems.append("SQLite integrity check failed")
        if len(db.audit_snapshot()) != (
                accepted_revision_count + rejected_revision_count + rollback_count):
            problems.append("Audit entries do not match explicit decisions")
        last_version, last_plan = db.current()
        if last_plan["week"] != WEEKS or last_version < WEEKS + 2:
            problems.append("Final plan version or week drift")
        statuses = {
            "missing_weeks": sum(bool(w["reconciliation"]["missing_or_unknown"]) for w in weeks),
            "conflict_weeks": sum(bool(w["reconciliation"]["conflicts"]) for w in weeks),
            "unlinked_weeks": sum(bool(w["reconciliation"]["unlinked_source_records"]) for w in weeks),
            "partial_weeks": sum(bool(w["reconciliation"]["partial"]) for w in weeks),
            "skipped_weeks": sum(bool(w["reconciliation"]["skipped"]) for w in weeks),
            "unplanned_weeks": sum(bool(w["reconciliation"]["unplanned_verified"]) for w in weeks),
            "h1_diff_weeks": sum(w["action_diff"] for w in weeks),
        }
        if profile == "INTERRUPTED" and not all(statuses[k] >= 1 for k in (
                "missing_weeks", "partial_weeks", "skipped_weeks", "unplanned_weeks")):
            problems.append("Interrupted athlete fixture coverage missing")
        if profile == "SENSOR_GAPS" and not all(statuses[k] >= 1 for k in (
                "conflict_weeks", "unlinked_weeks", "missing_weeks")):
            problems.append("Sensor gap fixture coverage missing")
        if profile == "STEADY" and (statuses["missing_weeks"] or
                                    statuses["conflict_weeks"]):
            problems.append("Stable athlete unexpectedly incomplete")
        return {
            "profile": profile, "status": "PASS" if not problems else "FAIL",
            "issues": problems, "weeks": weeks, "summary": statuses,
            "audit_entries": len(db.audit_snapshot()),
            "approved_revisions": accepted_revision_count,
            "rejected_revisions": rejected_revision_count,
            "rollbacks": rollback_count,
            "final_version": last_version, "source_records": len(db.activity_snapshot()),
            "sqlite_integrity": db.integrity_check(),
        }
    finally:
        db.close()


def run_suite() -> dict[str, Any]:
    reports = []
    with tempfile.TemporaryDirectory(prefix="stride-engine-8d-") as dirname:
        root = Path(dirname)
        for profile in PROFILE_NAMES:
            reports.append(evaluate_one(profile, root))
    failures = sum(r["status"] == "FAIL" for r in reports)
    return {
        "suite": "Batch 8D independent eight-week synthetic observation/plan lifecycle",
        "scope": "new cloud research B0/H1 only; no local historic lab/Android outcomes",
        "seed": SEED, "profiles": len(PROFILE_NAMES), "weeks_per_profile": WEEKS,
        "total_week_scenarios": len(reports) * WEEKS,
        "counts": {"PASS": len(reports) - failures, "FAIL": failures},
        "source_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "policy_sha256": hashlib.sha256(
            (Path(__file__).parent / "policy_candidates.py").read_bytes()).hexdigest(),
        "created_at_utc": datetime.now(timezone.utc).isoformat(),
        "github_commit": os.environ.get("GITHUB_SHA"),
        "results": reports,
        "longitudinal_athlete_effectiveness_claim": False,
        "private_athlete_data_used": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="research-multiweek-results.json")
    args = parser.parse_args()
    result = run_suite()
    f = Path(args.output)
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"MULTIWEEK SYNTHETIC LIFECYCLE: {result['counts']['PASS']} "
          f"PROFILE PASS, {result['counts']['FAIL']} PROFILE FAIL, "
          f"{result['total_week_scenarios']} WEEKS")
    for entry in result["results"]:
        print(f"{entry['profile']}: {entry['status']}; "
              f"weeks={len(entry['weeks'])}, sources={entry['source_records']}, "
              f"proposals approved={entry['approved_revisions']}, "
              f"rollback={entry['rollbacks']}, "
              f"action_diff_weeks={entry['summary']['h1_diff_weeks']}")
        for problem in entry["issues"]:
            print("FAIL:", entry["profile"], problem)
    print("Research scenarios only: NO real-athlete validation.")
    return 1 if result["counts"]["FAIL"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
