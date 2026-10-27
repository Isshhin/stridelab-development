#!/usr/bin/env python3
"""Materialize V2 fictional athlete histories from reviewed 12–16-week recipes.

Only test inputs. NOT validated coaching labels, real physiological data, ML training
material, diagnostic decisions, or authorized plan changes. Offline/stdlib only.
"""
import argparse
import json
import random
import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECIPES = ROOT / "app/src/test/resources/engine/engine2_longitudinal_recipes_v2.json"
START = date(2026, 11, 2)
DISCIPLINES = ("run", "str", "cal", "cond")


def load_recipes():
    doc = json.loads(RECIPES.read_text(encoding="utf-8"))
    if doc["schemaVersion"] != 2 or doc["classification"] != "synthetic-only":
        raise ValueError("Unrecognized or unsafe V2 recipe contract")
    return doc


def materialize(seed=20261027, variants=1, limit=8):
    """Generate a bounded repeatable corpus. Variant input changes IDs and measurements."""
    doc = load_recipes()
    recipes = doc["scenarios"]
    if not isinstance(variants, int) or not 1 <= variants <= 20:
        raise ValueError("Use 1–20 variants")
    if not isinstance(limit, int) or not 1 <= limit <= len(recipes):
        raise ValueError("Scenario limit must be 1 through the number of recipes")
    if variants * limit > 1280:
        raise ValueError("At most 1280 generated fictional athletes")
    scenarios = []
    for recipe_idx, recipe in enumerate(recipes[:limit]):
        for variant in range(variants):
            rng = random.Random(seed + recipe_idx * 1_000_003 + variant * 13_007)
            athlete = f"{recipe['id']}-V{variant:02d}"
            weeks = recipe["weeks"]
            options = recipe["profile"]
            events = []
            for event in recipe["events"]:
                event_date = START + timedelta(weeks=event["week"], days=event["day"])
                events.append({
                    "date": event_date.isoformat(),
                    "type": event["type"],
                    "value": event["value"],
                    "source": "synthetic-user-or-source-event",
                    "automaticPlanMutation": False,
                })
            missing = set(recipe["missingWeeks"])
            recorded = []
            wellbeing = []
            for week in range(weeks):
                week_start = START + timedelta(weeks=week)
                if options["device"] == "connected" and recipe["wellnessCoverage"] != "none":
                    density = recipe["wellnessCoverage"]
                    if density == "regular" or density == "partial" or week % 3 == 0:
                        hr = rng.randint(48, 70) if density != "partial" or rng.random() < .70 else None
                        hrv = rng.randint(35, 95) if density != "partial" or rng.random() < .70 else None
                        sleep = round(rng.uniform(5.6, 8.7), 1) if density != "partial" or rng.random() < .70 else None
                        if any(e["type"] == "baseline_missing" for e in recipe["events"]) and week <= 6:
                            hr = None
                        if any(e["type"] == "hrv_missing" for e in recipe["events"]) and 4 <= week <= 8:
                            hrv = None
                        if any(e["type"] == "sleep_interruption" for e in recipe["events"]) and week == 6:
                            sleep = 4.9  # entirely invented perturbation; not a clinical cutoff
                        wellbeing.append({
                            "date": week_start.isoformat(),
                            "restingHrBpm": hr,
                            "hrvMs": hrv,
                            "sleepHours": sleep,
                            "selfReportedIllness": any(
                                e["type"] == "illness_self_report" and e["week"] == week
                                for e in recipe["events"]),
                            "source": "synthetic-provider-a",
                        })
                if week in missing or recipe["noHistory"]:
                    continue  # absence is not a Missed/Skipped event
                for discipline_idx, discipline in enumerate(DISCIPLINES):
                    count = recipe["weeklySessions"][discipline]
                    if count <= 0:
                        continue
                    # Different disciplines can occupy the same calendar day as real history can.
                    days = rng.sample(range(7), count)
                    for ordinal, day in enumerate(sorted(days)):
                        scheduled = week_start + timedelta(days=day)
                        rid = f"{athlete}-W{week:02d}-{discipline}-D{day}"
                        if any(e["type"] == "approval_pending" and e["week"] == week
                               for e in recipe["events"]) and ordinal == 0:
                            recorded.append({
                                "id": rid, "date": scheduled.isoformat(),
                                "discipline": discipline, "kind": "proposed",
                                "status": "planned", "source": "synthetic-manual",
                                "plannedTargetMinutes": rng.randint(25, 70),
                            })
                            continue
                        minutes = rng.randint(20, 85)
                        effort = rng.randint(2, 8)
                        kind = {
                            "run": ("easy", "long", "quality")[ordinal % 3],
                            "str": "strength", "cal": "calisthenics_practice",
                            "cond": "conditioning",
                        }[discipline]
                        row = {
                            "id": rid, "date": scheduled.isoformat(),
                            "discipline": discipline, "kind": kind,
                            "status": "recorded", "source": "synthetic-manual",
                            "durationMinutes": minutes, "effortRpe": effort,
                        }
                        if discipline == "run":
                            if not recipe["omitDistance"] and rng.random() < .84:
                                row["distanceKm"] = round(rng.uniform(2.0, 17.0), 2)
                            if options["device"] == "connected" and rng.random() < .72:
                                row["avgHrBpm"] = rng.randint(110, 181)
                                if any(e["type"] == "hr_outlier" and e["week"] == week
                                       for e in recipe["events"]) and ordinal == 0:
                                    row["avgHrBpm"] = 248
                                    row["qualityFlag"] = "suspicious-synthetic-sensor-value"
                            if options["device"] == "connected" and (
                                recipe["duplicateImport"] or rng.random() < .35
                            ):
                                row.update({
                                    "source": "synthetic-provider-a",
                                    "providerActivityId": f"a-{rid}",
                                    "physicalEventKey": f"fictional-event-{rid}",
                                })
                            if any(e["type"] == "timezone_change" and e["week"] <= week
                                   for e in recipe["events"]):
                                row["rawTimeZoneOffsetMinutes"] = 540
                            if recipe["duplicateImport"] and week == 2 and ordinal == 0:
                                # Two provider records claiming a common *fictional* event.
                                row.update({
                                    "source": "synthetic-provider-a",
                                    "providerActivityId": f"a-{rid}",
                                    "physicalEventKey": f"fictional-event-{rid}",
                                })
                                duplicate = dict(row)
                                duplicate.update({
                                    "id": rid + "-provider-b",
                                    "source": "synthetic-provider-b",
                                    "providerActivityId": f"b-{rid}",
                                })
                                recorded.append(duplicate)
                        elif discipline == "str":
                            equipment_removed = any(e["type"] == "equipment_removed"
                                and week >= e["week"] for e in recipe["events"])
                            can_use_db = "dumbbells" in options["equipment"] and not equipment_removed
                            ids = ["rl", "gs"] if can_use_db else ["rl"]
                            row["sets"] = [{
                                "exerciseId": eid, "reps": rng.randint(4, 15),
                                **({"loadKg": float(rng.randint(1, 20))}
                                   if eid == "gs" and not recipe["omitLoad"] else {}),
                            } for eid in ids]
                        elif discipline == "cal":
                            row["sets"] = [
                                {"exerciseId": "e4", "reps": rng.randint(2, 12)},
                                {"exerciseId": "core-tuck", "seconds": rng.randint(8, 35)},
                            ]
                            if any(e["type"] == "missing_hold" and week == e["week"]
                                   for e in recipe["events"]):
                                row["sets"] = [{"exerciseId": "e4", "reps": rng.randint(2, 12)}]
                        recorded.append(row)
            recorded.sort(key=lambda item: (item["date"], item["id"]))
            scenarios.append({
                "id": athlete,
                "recipeId": recipe["id"],
                "dimension": recipe["dimension"],
                "focus": recipe["title"],
                "synthetic": True,
                "profile": {
                    "experience": options["level"],
                    "wearableConnected": options["device"] == "connected",
                    "equipment": list(options["equipment"]),
                },
                "period": {
                    "start": START.isoformat(),
                    "end": (START + timedelta(weeks=weeks, days=-1)).isoformat(),
                },
                "records": recorded,
                "wellness": wellbeing,
                "events": events,
                "checks": recipe["checks"],  # future Engine 2.0 behavior; not executed labels
            })
    return {
        "schemaVersion": 2,
        "datasetId": "stridelab-generated-longitudinal-athletes-v2",
        "classification": "synthetic-only",
        "intent": "Software stress data, not real athletes, medical thresholds or validated coaching decisions",
        "seed": seed,
        "variants": variants,
        "scenarioLimit": limit,
        "units": {
            "durationMinutes": "min", "distanceKm": "km",
            "avgHrBpm": "beats/min", "restingHrBpm": "beats/min",
            "hrvMs": "ms", "sleepHours": "h", "loadKg": "kg",
            "effortRpe": "fictional ordinal 1-10",
        },
        "scenarios": scenarios,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=20261027)
    parser.add_argument("--limit", type=int, default=8)
    parser.add_argument("--variants", type=int, default=1)
    parser.add_argument("--output", type=Path, help="Create a NEW JSON file; never overwrite")
    args = parser.parse_args(argv)
    try:
        data = materialize(args.seed, args.variants, args.limit)
        payload = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
        if args.output:
            with args.output.open("x", encoding="utf-8") as f:
                f.write(payload)
        else:
            sys.stdout.write(payload)
    except (OSError, ValueError, KeyError) as e:
        parser.error(str(e))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
