"""Research-only, fail-closed analyzer for matched-frequency Runna plan captures.

No vendor API access, no training recommendations, and no synthetic data in live reports.
"""
from __future__ import annotations

import argparse
import csv
import json
import statistics
from pathlib import Path

EXPECTED = {f"F{days}{rep}": (days, rep) for days in (5, 6, 7) for rep in ("A", "B")}
PHASES = {"BUILD", "DELOAD", "TAPER", "RACE", "RECOVERY", "OTHER"}
CONFIG_KEYS = (
    "baseline_km", "recent_long_km", "ability", "current_5k", "plan_type",
    "target_date", "plan_weeks", "difficulty", "volume", "strength_sessions",
)
CASE_FIELDS = (
    "case_id", "runs_per_week", "replicate", *CONFIG_KEYS, "availability_days",
    "status", "app_version", "generated_at", "source_ref", "warning",
)
WEEK_FIELDS = (
    "case_id", "week_index", "phase", "total_km", "long_run_km",
    "quality_count", "quality_types", "easy_count", "run_days",
    "recovery_days", "warning_state", "source_ref",
)


def load_csv(path: str | Path, fields: tuple[str, ...]) -> list[dict[str, str]]:
    with open(path, newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None or not set(fields).issubset(reader.fieldnames):
            raise ValueError(f"{path}: missing required columns")
        return list(reader)


def positive_float(value: str, field: str, allow_zero: bool = False) -> float:
    try:
        result = float(value)
    except (ValueError, TypeError) as exc:
        raise ValueError(f"{field}: missing/invalid numeric value") from exc
    if not (result >= 0 if allow_zero else result > 0) or result == float("inf"):
        raise ValueError(f"{field}: out of bounds")
    return result


def whole_number(value: str, field: str) -> int:
    try:
        number = int(value)
    except (ValueError, TypeError) as exc:
        raise ValueError(f"{field}: expected integer") from exc
    if number < 0:
        raise ValueError(f"{field}: negative integer")
    return number


def pct_change(current: float, previous: float) -> float | None:
    return 100 * (current - previous) / previous if previous > 0 else None


def analyze(case_csv: str | Path, week_csv: str | Path) -> dict:
    cases = load_csv(case_csv, CASE_FIELDS)
    weeks = load_csv(week_csv, WEEK_FIELDS)
    if len(cases) != len(EXPECTED) or {r["case_id"] for r in cases} != set(EXPECTED):
        raise ValueError("case matrix must contain exactly six distinct F5/F6/F7 A/B cases")
    reference = {key: cases[0][key] for key in CONFIG_KEYS}
    if reference["baseline_km"] != "65" or reference["recent_long_km"] != "18":
        raise ValueError("preregistered synthetic athlete baseline changed")
    if reference["plan_weeks"] != "12":
        raise ValueError("preregistered 12-week horizon changed")
    by_case = {}
    for row in cases:
        cid = row["case_id"]
        expected_days, expected_rep = EXPECTED[cid]
        if whole_number(row["runs_per_week"], cid + ".runs_per_week") != expected_days or row["replicate"] != expected_rep:
            raise ValueError(f"{cid}: identity/frequency mismatch")
        if any(row[key] != reference[key] for key in CONFIG_KEYS):
            raise ValueError(f"{cid}: athlete/goal/configuration mismatch")
        if row["status"] not in ("NOT_RUN", "GENERATED", "REJECTED_BY_APP", "DRAFT_UNAVAILABLE", "INVALID_COMPARISON"):
            raise ValueError(f"{cid}: unsupported case status")
        if row["status"] == "GENERATED" and not all(row[k].strip() for k in ("app_version", "generated_at", "source_ref")):
            raise ValueError(f"{cid}: generated capture missing app version, time or source")
        if row["status"] == "REJECTED_BY_APP" and not row["warning"].strip():
            raise ValueError(f"{cid}: rejected plan missing verbatim reason")
        by_case[cid] = row
    observed: dict[str, dict[int, dict]] = {}
    for row in weeks:
        cid = row["case_id"]
        if cid not in by_case or by_case[cid]["status"] != "GENERATED":
            raise ValueError(f"{cid}: week output without GENERATED status")
        week = whole_number(row["week_index"], cid + ".week_index")
        if week not in range(1, 13) or week in observed.get(cid, {}):
            raise ValueError(f"{cid}: week index outside 1..12 or repeated")
        if row["phase"] not in PHASES:
            raise ValueError(f"{cid} week {week}: unknown training phase")
        if not row["source_ref"].strip():
            raise ValueError(f"{cid} week {week}: no source capture")
        km = positive_float(row["total_km"], "total_km")
        long_km = positive_float(row["long_run_km"], "long_run_km", allow_zero=True)
        if long_km > km + 1e-9:
            raise ValueError(f"{cid} week {week}: long run exceeds weekly mileage")
        quality = whole_number(row["quality_count"], "quality_count")
        easy = whole_number(row["easy_count"], "easy_count")
        run_days = whole_number(row["run_days"], "run_days")
        recovery_days = whole_number(row["recovery_days"], "recovery_days")
        if run_days > 7 or recovery_days > 7 or run_days + recovery_days != 7:
            raise ValueError(f"{cid} week {week}: running/rest day accounting mismatch")
        if quality + easy > run_days:
            raise ValueError(f"{cid} week {week}: quality+easy exceeds run days")
        observed.setdefault(cid, {})[week] = {
            "phase": row["phase"], "total_km": km, "long_run_km": long_km,
            "quality_count": quality, "quality_types": row["quality_types"],
            "easy_count": easy, "run_days": run_days, "recovery_days": recovery_days,
            "warning_state": row["warning_state"], "source_ref": row["source_ref"],
        }
    if not any(row["status"] == "GENERATED" for row in cases):
        return {
            "status": "BLOCKED_NO_CURRENT_APP_OUTPUT", "evidence": "NOT_RUN_OR_REJECTED_ONLY",
            "expected_drafts": 6, "generated_drafts": 0, "reported_comparisons": 0,
            "case_statuses": {cid: by_case[cid]["status"] for cid in sorted(EXPECTED)},
        }
    complete = {cid for cid in EXPECTED if by_case[cid]["status"] == "GENERATED" and len(observed.get(cid, {})) == 12}
    if len(complete) < 6:
        return {
            "status": "BLOCKED_INCOMPLETE_MATCHED_CAPTURE", "evidence": "DIRECT_OBSERVATION_PENDING_REVIEW",
            "expected_drafts": 6, "generated_drafts": sum(r["status"] == "GENERATED" for r in cases),
            "complete_drafts": len(complete), "missing_weeks": {cid: sorted(set(range(1, 13)) - set(observed.get(cid, {}))) for cid in sorted(EXPECTED) if cid not in complete},
            "case_statuses": {cid: by_case[cid]["status"] for cid in sorted(EXPECTED)},
        }
    if len({by_case[cid]["app_version"] for cid in EXPECTED}) != 1:
        return {"status": "BLOCKED_APP_VERSION_MISMATCH", "reported_comparisons": 0}
    if len({by_case[cid]["source_ref"] for cid in EXPECTED}) != 6:
        raise ValueError("six captures need distinct evidence references")
    # Frequency is an actual exposure, not merely a requested configuration field.
    # An app may silently omit workouts or reduce days; retain the observation but
    # do not report it as a clean matched-frequency progression comparison.
    plan_summaries = {}
    frequency_deviations = []
    for cid in sorted(EXPECTED):
        plan = observed[cid]
        target_days = EXPECTED[cid][0]
        transitions = []
        for week in range(2, 13):
            old, new = plan[week - 1], plan[week]
            transitions.append({
                "from_week": week - 1, "to_week": week,
                "from_phase": old["phase"], "to_phase": new["phase"],
                "kind": "BUILD_TO_BUILD" if old["phase"] == new["phase"] == "BUILD"
                    else "NON_BUILD_TRANSITION",
                "weekly_change_pct": round(pct_change(new["total_km"], old["total_km"]), 4),
                "long_run_change_pct": round(pct_change(new["long_run_km"], old["long_run_km"]), 4)
                    if old["long_run_km"] > 0 else None,
            })
        for week, data in sorted(plan.items()):
            if data["phase"] == "BUILD" and data["run_days"] != target_days:
                frequency_deviations.append({
                    "case_id": cid, "week": week,
                    "requested_runs_per_week": target_days,
                    "observed_run_days": data["run_days"],
                    "source_ref": data["source_ref"],
                })
        first = plan[1]
        peak_week = max(plan, key=lambda n: plan[n]["total_km"])
        long_peak_week = max(plan, key=lambda n: plan[n]["long_run_km"])
        plan_summaries[cid] = {
            "first_week_km": first["total_km"],
            "peak_week_index": peak_week,
            "peak_week_km": plan[peak_week]["total_km"],
            "last_week_km": plan[12]["total_km"],
            "peak_long_run_week": long_peak_week,
            "peak_long_run_km": plan[long_peak_week]["long_run_km"],
            "deload_weeks": [w for w, d in sorted(plan.items()) if d["phase"] == "DELOAD"],
            "taper_weeks": [w for w, d in sorted(plan.items()) if d["phase"] == "TAPER"],
            "race_weeks": [w for w, d in sorted(plan.items()) if d["phase"] == "RACE"],
            "weekly_composition": [
                {"week": w, "phase": d["phase"], "quality_count": d["quality_count"],
                 "quality_types": d["quality_types"], "easy_count": d["easy_count"],
                 "run_days": d["run_days"], "recovery_days": d["recovery_days"],
                 "long_run_share": round(d["long_run_km"] / d["total_km"], 4),
                 "warning_state": d["warning_state"]}
                for w, d in sorted(plan.items())
            ],
            "transitions": transitions,
        }
    # A/B draft repeats help quantify generator variability before attributing
    # small changes to the frequency treatment.
    replicate_stability = {}
    for days in (5, 6, 7):
        lhs, rhs = observed[f"F{days}A"], observed[f"F{days}B"]
        absolute_diffs = [abs(lhs[w]["total_km"] - rhs[w]["total_km"]) for w in range(1, 13)]
        replicate_stability[str(days)] = {
            "identical_weekly_total_count": sum(d < 1e-6 for d in absolute_diffs),
            "mean_abs_weekly_total_difference_km": round(statistics.mean(absolute_diffs), 4),
            "max_abs_weekly_total_difference_km": round(max(absolute_diffs), 4),
            "phase_sequence_identical": all(lhs[w]["phase"] == rhs[w]["phase"] for w in range(1, 13)),
        }
    if frequency_deviations:
        return {
            "status": "BLOCKED_FREQUENCY_NOT_REALIZED",
            "reported_comparisons": 0,
            "reason": "Generated BUILD weeks do not consistently contain the requested number of run days",
            "frequency_deviations": frequency_deviations,
            "plan_summaries": plan_summaries,
            "replicate_stability": replicate_stability,
        }
    by_pair = {}
    for rep in ("A", "B"):
        for days in (6, 7):
            control = observed[f"F5{rep}"]
            treatment = observed[f"F{days}{rep}"]
            values = []
            for week in range(2, 13):
                c0, c1 = control[week - 1], control[week]
                t0, t1 = treatment[week - 1], treatment[week]
                if all(w["phase"] == "BUILD" for w in (c0, c1, t0, t1)):
                    values.append({
                        "week": week,
                        "five_day_progression_pct": pct_change(c1["total_km"], c0["total_km"]),
                        "higher_frequency_progression_pct": pct_change(t1["total_km"], t0["total_km"]),
                        "weekly_delta_percentage_points": pct_change(t1["total_km"], t0["total_km"]) - pct_change(c1["total_km"], c0["total_km"]),
                        "long_run_delta_percentage_points": pct_change(t1["long_run_km"], t0["long_run_km"]) - pct_change(c1["long_run_km"], c0["long_run_km"]) if t0["long_run_km"] > 0 and c0["long_run_km"] > 0 else None,
                    })
            by_pair[f"F{days}{rep}_vs_F5{rep}"] = {
                "build_to_build_week_count": len(values),
                "weekly_progression_delta_pp_mean": round(statistics.mean(x["weekly_delta_percentage_points"] for x in values), 3) if values else None,
                "long_run_progression_delta_pp_mean": round(statistics.mean(x["long_run_delta_percentage_points"] for x in values if x["long_run_delta_percentage_points"] is not None), 3) if any(x["long_run_delta_percentage_points"] is not None for x in values) else None,
                "matched_build_weeks": values,
            }
    if any(v["build_to_build_week_count"] < 2 for v in by_pair.values()):
        return {"status": "BLOCKED_TOO_FEW_MATCHED_BUILD_WEEKS", "pair_details": by_pair, "plan_summaries": plan_summaries, "replicate_stability": replicate_stability}
    return {
        "status": "DESCRIPTIVE_ONLY_PENDING_CAPTURE_REVIEW",
        "evidence": "MANUALLY_TRANSCRIBED_PLAN_OUTPUTS_NOT_INDEPENDENTLY_VERIFIED",
        "generated_drafts": 6, "pair_details": by_pair,
        "plan_summaries": plan_summaries, "replicate_stability": replicate_stability,
        "interpretation_limit": "Two replicas per frequency are descriptive, not independent proof of causal policy or physiological superiority.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", default="docs/research/experiments/experiment-007-runna-frequency-cases.csv")
    parser.add_argument("--weeks", default="docs/research/experiments/experiment-007-runna-frequency-weeks.csv")
    parser.add_argument("--output", default=None)
    args = parser.parse_args()
    try:
        report = analyze(args.cases, args.weeks)
    except ValueError as exc:
        report = {"status": "INVALID_CAPTURE", "reason": str(exc)}
    result = json.dumps(report, indent=2, sort_keys=True)
    if args.output:
        Path(args.output).write_text(result + "\n", encoding="utf-8")
    print(result)
    return 2 if report["status"] == "INVALID_CAPTURE" else 0


if __name__ == "__main__":
    raise SystemExit(main())
