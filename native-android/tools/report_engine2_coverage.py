#!/usr/bin/env python3
"""Explain coverage of a V2 synthetic dataset; NOT a coaching-performance score."""
import argparse
import json
from collections import Counter, defaultdict
from datetime import date, timedelta
from pathlib import Path

from build_engine2_scenarios import materialize


def summarize(doc):
    if doc.get("classification") != "synthetic-only" or doc.get("schemaVersion") != 2:
        raise ValueError("Only versioned fictional V2 datasets are supported")
    dimensions = Counter()
    statuses = Counter()
    disciplines = Counter()
    sources = Counter()
    events = Counter()
    measurements_missing = Counter()
    duplicates_by_account = defaultdict(list)
    suspicious = 0
    quarantined = 0
    missing_calendar_weeks = 0
    observation_count = 0
    checks = set()
    for scenario in doc["scenarios"]:
        dimensions[scenario["dimension"]] += 1
        checks.update(scenario["checks"])
        observation_count += len(scenario["wellness"])
        events.update(x["type"] for x in scenario["events"])
        record_days = {date.fromisoformat(r["date"]) for r in scenario["records"]}
        start = date.fromisoformat(scenario["period"]["start"])
        weeks = ((date.fromisoformat(scenario["period"]["end"]) - start).days + 1) // 7
        for week in range(weeks):
            begin = start + timedelta(weeks=week)
            if not any(begin <= day <= begin + timedelta(days=6) for day in record_days):
                missing_calendar_weeks += 1
        for row in scenario["records"]:
            statuses[row["status"]] += 1
            disciplines[row["discipline"]] += 1
            sources[row["source"]] += 1
            if row["status"] == "recorded" and row["discipline"] == "run":
                for key in ("distanceKm", "avgHrBpm"):
                    if key not in row or row[key] is None:
                        measurements_missing[key] += 1
            suspicious += bool(row.get("qualityFlag"))
            quarantined += bool(row.get("quarantinedInput"))
            if row.get("physicalEventKey"):
                scope = row.get("accountScope", scenario["id"])
                duplicates_by_account[(scope, row["physicalEventKey"])].append(row["source"])
    duplicate_groups = sum(len(values) >= 2 for values in duplicates_by_account.values())
    return {
        "datasetId": doc["datasetId"],
        "classification": "synthetic-only",
        "seed": doc["seed"],
        "variants": doc["variants"],
        "scenarioCount": len(doc["scenarios"]),
        "activityRecordCount": sum(statuses.values()),
        "wellnessObservationCount": observation_count,
        "missingCalendarWeeks": missing_calendar_weeks,
        "distinctCandidateInvariantIds": len(checks),
        "byDimension": dict(sorted(dimensions.items())),
        "byDiscipline": dict(sorted(disciplines.items())),
        "byRecordStatus": dict(sorted(statuses.items())),
        "bySource": dict(sorted(sources.items())),
        "byFictionalEvent": dict(sorted(events.items())),
        "recordedRunMissingValues": dict(sorted(measurements_missing.items())),
        "providerDuplicateGroupsWithinAccount": duplicate_groups,
        "flaggedSuspiciousSensorRows": suspicious,
        "quarantinedInvalidInputRows": quarantined,
        "note": "Coverage counts do not demonstrate clinical safety, scientific validity or Engine 2.0 adaptation correctness.",
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=20261027)
    parser.add_argument("--limit", type=int, default=64)
    parser.add_argument("--variants", type=int, default=1)
    parser.add_argument("--input", type=Path, help="Summarize an existing fictional V2 JSON file")
    args = parser.parse_args(argv)
    try:
        dataset = (json.loads(args.input.read_text(encoding="utf-8"))
                   if args.input else materialize(args.seed, args.variants, args.limit))
        print(json.dumps(summarize(dataset), sort_keys=True, indent=2))
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
