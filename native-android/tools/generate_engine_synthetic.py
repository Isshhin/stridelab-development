#!/usr/bin/env python3
"""Deterministic synthetic training-history stress fixtures.

NOT real athletes, medical guidance, adaptive-plan labels, or a model-training set.
This tool does not read device data, access network services or modify app storage.
"""
import argparse
import json
import random
import sys
from datetime import date, timedelta
from pathlib import Path

START = date(2026, 9, 28)
GOALS = {
    "run": ["Aerobic foundation", "Improve 5K", "Improve 10K", "Half marathon"],
    "str": ["General strength", "Strength endurance"],
    "cal": ["Calisthenics foundation", "Core strength and control"],
    "cond": ["Conditioning"],
}
EXERCISES = {
    "str": ("sq", "gs"),
    "cal": ("e4",),
}


def generate(seed: int, count: int) -> dict:
    if not 1 <= count <= 1000:
        raise ValueError("Use 1–1000 fictional athlete scenarios")
    rng = random.Random(seed)
    scenarios = []
    for athlete in range(count):
        sid = f"G-{athlete:05d}"
        active = rng.sample(list(GOALS), k=rng.randint(1, 4))
        scheduled = {discipline: rng.randint(1, 3 if discipline == "run" else 2)
                     for discipline in active}
        wearable = rng.choice((True, False))
        equipment = ["dumbbells"] if "str" in active and rng.random() < 0.5 else []
        goals = [rng.choice(GOALS[d]) for d in active]
        records = []
        wellness = []
        events = []
        for week in range(4):
            for discipline, n in scheduled.items():
                days = rng.sample(range(7), n)
                for day in days:
                    day_date = START + timedelta(days=week * 7 + day)
                    rid = f"{sid}-{week}-{day}-{discipline}"
                    if rng.random() < 0.08:
                        records.append({
                            "id": rid, "date": day_date.isoformat(),
                            "discipline": discipline, "kind": "user_skipped",
                            "status": "skipped", "source": "synthetic-manual",
                            "reason": "User explicitly skipped the scheduled session",
                        })
                        continue
                    kind = {
                        "run": rng.choice(("easy", "quality", "long")),
                        "str": "strength", "cal": "skill", "cond": "conditioning",
                    }[discipline]
                    row = {
                        "id": rid, "date": day_date.isoformat(),
                        "discipline": discipline, "kind": kind,
                        "status": "recorded", "durationMinutes": rng.randint(20, 100),
                        "effortRpe": rng.randint(2, 8), "source": "synthetic-manual",
                    }
                    if discipline == "run":
                        if rng.random() < 0.75:
                            row["distanceKm"] = round(rng.uniform(2.5, 16.0), 2)
                        if wearable and rng.random() < 0.75:
                            row["avgHrBpm"] = rng.randint(120, 180)
                        if wearable and rng.random() < 0.45:
                            row.update(source="synthetic-provider-a",
                                       providerActivityId=f"provider-a-{rid}",
                                       physicalEventKey=f"simulated-event-{rid}")
                            if rng.random() < 0.12:
                                copy = dict(row)
                                copy["id"] = rid + "-provider-b"
                                copy["source"] = "synthetic-provider-b"
                                copy["providerActivityId"] = f"provider-b-{rid}"
                                records.append(copy)
                    elif discipline in EXERCISES:
                        row["sets"] = [{
                            "exerciseId": key,
                            "reps": rng.randint(4, 15),
                            **({"loadKg": rng.randint(0, 20)}
                               if discipline == "str" else {}),
                        } for key in EXERCISES[discipline]]
                    records.append(row)
            if wearable and rng.random() < 0.8:
                day_date = START + timedelta(days=week * 7)
                wellness.append({
                    "date": day_date.isoformat(),
                    "restingHrBpm": rng.randint(46, 70)
                        if rng.random() < 0.8 else None,
                    "hrvMs": rng.randint(35, 90)
                        if rng.random() < 0.8 else None,
                    "sleepHours": round(rng.uniform(5.3, 9.0), 1)
                        if rng.random() < 0.8 else None,
                    "selfReportedIllness": False,
                })
        if rng.random() < 0.2:
            events.append({
                "date": (START + timedelta(days=14)).isoformat(),
                "type": "equipment_change",
                "value": "Synthetic equipment availability changed; proposal needs user review",
            })
        records.sort(key=lambda x: (x["date"], x["id"]))
        scenarios.append({
            "id": sid, "focus": "seeded cross-discipline robustness case",
            "profile": {
                "experience": rng.choice(("beginner", "intermediate", "advanced")),
                "goals": goals, "equipment": equipment, "wearableConnected": wearable,
            },
            "period": {
                "start": START.isoformat(),
                "end": (START + timedelta(days=27)).isoformat(),
            },
            "records": records, "wellness": wellness, "events": events,
            "requiredChecks": [
                "offline-deterministic", "unknown-data-is-not-zero",
                "no-unapproved-plan-changes",
            ],
        })
    return {
        "schemaVersion": 1,
        "datasetId": "stridelab-generated-engine-stress-v1",
        "classification": "synthetic-only",
        "intent": "Generated tests only: no real physiology, efficacy, expert labels, or model training",
        "generated": {"seed": seed, "count": count},
        "scenarios": scenarios,
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=20261027)
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--output", type=Path, help="Write to a new JSON file, otherwise stdout")
    args = parser.parse_args(argv)
    try:
        payload = json.dumps(generate(args.seed, args.count), indent=2) + "\n"
        if args.output is None:
            sys.stdout.write(payload)
        else:
            # Never silently overwrite an existing fixture or user file.
            with args.output.open("x", encoding="utf-8") as handle:
                handle.write(payload)
    except (ValueError, FileExistsError, OSError) as e:
        parser.error(str(e))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
