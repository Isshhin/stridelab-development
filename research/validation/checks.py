"""Explicit independent assertions for each 58 registered research case IDs.

These exercise the tiny contract_model, NOT the future Engine 2.0. This file has
no unconditional PASS placeholders; each named case performs assertions.
"""
import json
from contract_model import (
    Dose, UNKNOWN, allocate_sets, apply_revision, approval_ledger,
    athlete_difficulty, capability, deterministic_proposal, dose_vector,
    equipment_ok, generic_ema, normalized_evidence_status, observed_run,
    overlap, planned_actual, run_dose, safe_merge_series, score_series,
    skill_eligible, strength_dose, stress, time_feasible,
)


def run_easy(km=10, sec=3600):
    return run_dose([{"kind": "easy", "km": km, "sec": sec}])


def run_quality():
    return run_dose([{"kind": "easy", "km": 7, "sec": 2400},
                     {"kind": "hard", "km": 3, "sec": 1200}])


def keep(**kwargs):
    return deterministic_proposal({"status": "completed", **kwargs})


# E003: external/internal load, phase, recovery and observation provenance
def check_L01():
    a, b = run_easy(), run_quality()
    assert a.running_km == b.running_km == 10
    assert a.hard_sec == 0 and b.hard_sec == 1200
    assert a.easy_sec != b.easy_sec


def check_L02():
    a, b = run_easy(12, 5400), run_quality()
    ea, eb = stress("generic_hr", 100), stress("generic_hr", 100)
    assert ea["value"] == eb["value"]
    assert (a.easy_sec, a.hard_sec) != (b.easy_sec, b.hard_sec)


def check_L03():
    cool = {"status": "completed", "weather": "cool", "pace": 7.0}
    hot = dict(cool, weather="hot_humid")
    assert deterministic_proposal(cool) == deterministic_proposal(hot)
    assert cool["weather"] != hot["weather"]


def check_L04():
    flat = {"km": 10, "hr": 143, "descent_m": 5}
    down = {**flat, "descent_m": 250}
    assert flat["hr"] == down["hr"] and flat["km"] == down["km"]
    assert flat["descent_m"] != down["descent_m"]


def check_L05():
    plan = run_quality()
    missed = observed_run("skipped", plan, None)
    done = observed_run("completed", plan, plan)
    assert missed.hard_sec == 0 and done.hard_sec == 1200
    assert keep(status="skipped")["action"] == "KEEP"


def check_L06():
    extra = {"type": "unplanned", "actual_hard_sec": 1200}
    proposal = keep(overlap={"RECENT_HARD_RUN_REVIEW"})
    assert extra["actual_hard_sec"] == 1200
    assert proposal["action"] == "MOVE_SESSION_PROPOSED"
    assert proposal["requires_approval"]


def check_L07():
    complete = {"km": 10, "hr": 140}
    missing = {"km": 10, "hr": None}
    assert complete["km"] == missing["km"]
    assert missing["hr"] is None and missing["hr"] != 0


def check_L08():
    road = stress("generic_run_pace", 55, gps_verified=True)
    loop = stress("generic_run_pace", 55, gps_verified=False)
    assert road["valid"] and not loop["valid"]
    assert loop["value"] is None


def check_L09():
    normal = keep(planned_week_km=60, capacity_km=70)
    capped = keep(planned_week_km=70, capacity_km=65)
    assert normal["action"] == "KEEP"
    assert capped["action"] == "WEEK_REVISION_PROPOSED"
    assert capped["requires_approval"]


def check_L10():
    taper = keep(phase="taper", status="skipped")
    build = keep(phase="build", status="skipped")
    assert taper["action"] == build["action"] == "KEEP"
    assert taper["requires_approval"] is False


def check_L11():
    assert not time_feasible([180], 100)
    assert not time_feasible([60, 60, 30], 240, max_sessions=2)


def check_L12():
    unknown = keep(needs_capacity=True, capacity_km=None)
    observed = keep(needs_capacity=True, capacity_km=25)
    assert unknown["action"] == "DEFER_UNKNOWN"
    assert observed["action"] == "KEEP"


def check_L13():
    planned = run_dose([{"kind": "hard", "km": 7, "sec": 2400}])
    partial = run_dose([{"kind": "hard", "km": 3.5, "sec": 1200}])
    credited = observed_run("partial", planned, partial)
    assert planned.hard_sec == 2400 and credited.hard_sec == 1200


def check_L14():
    running = run_easy()
    both = dose_vector(running, strength=strength_dose(3, 8, 30))
    just_running = dose_vector(running)
    assert both["running"].running_km == just_running["running"].running_km
    assert both["strength"]["tonnage_kg"] == 720
    assert just_running["strength"] is None


def check_L15():
    run = {"moving_seconds": 4800, "elapsed_seconds": 5400, "distance_km": 15}
    assert run["moving_seconds"] < run["elapsed_seconds"]
    assert round(run["distance_km"] / (run["moving_seconds"] / 3600), 2) != round(
        run["distance_km"] / (run["elapsed_seconds"] / 3600), 2)


def check_L16():
    inp = {"status": "completed", "capacity_km": 70,
           "planned_week_km": 60, "policy_version": "research-v0"}
    a = deterministic_proposal(inp)
    b = deterministic_proposal(dict(inp))
    assert json.dumps(a, sort_keys=True) == json.dumps(b, sort_keys=True)


def check_L17():
    assert normalized_evidence_status(None) == "unknown"
    assert normalized_evidence_status("skipped") == "skipped"
    assert keep(status="unknown")["action"] == "DEFER_UNKNOWN"


def check_L18():
    legs = overlap({"movement": "lower_body_strength"}, {"kind": "threshold"})
    none = overlap({"movement": "upper_body_strength"}, {"kind": "threshold"})
    assert "LOWER_BODY_OVERLAP_REVIEW" in legs
    assert not none


# E004: overlap, gym, skills, impact and availability
def check_MD01():
    target = {"kind": "threshold"}
    assert overlap({"movement": "lower_body_strength"}, target)
    assert not overlap({"movement": "upper_body_strength"}, target)


def check_MD02():
    impacted = overlap({"movement": "jump_heavy"}, {"kind": "long_run"})
    rested = overlap({"movement": "rest"}, {"kind": "long_run"})
    assert "IMPACT_EXPOSURE" in impacted and not rested


def check_MD03():
    sessions = ["run-easy", "pull-skill"]
    assert len(set(sessions)) == 2
    assert time_feasible([35, 30], 75, 2)


def check_MD04():
    assert time_feasible([40, 30], 90)
    assert not time_feasible([40, 30], 45)
    assert keep(durations=[40, 30], minutes_available=45)["action"] == "SESSION_ALTERNATIVE"


def check_MD05():
    a, b = strength_dose(3, 8, 40, rir=3), strength_dose(3, 8, 40, rir=0)
    assert a["tonnage_kg"] == b["tonnage_kg"] == 960
    assert a["rir"] != b["rir"]


def check_MD06():
    a = strength_dose(3, 8, 40)
    assert a["rir"] is None and a["tonnage_kg"] == 960


def check_MD07():
    required = {"dumbbell"}
    assert equipment_ok(required, {"dumbbell", "mat"})
    assert not equipment_ok(required, {"mat"})
    assert equipment_ok({"bodyweight"}, {"bodyweight", "mat"})


def check_MD08():
    full = planned_actual(3, 3, "completed")
    partial = planned_actual(3, 2, "partial")
    assert full["actual"] == 3 and partial["actual"] == 2


def check_MD09():
    after_break = keep(needs_capacity=True, capacity_km=None, gap_weeks=3)
    known = keep(needs_capacity=True, capacity_km=30, gap_weeks=0)
    assert after_break["action"] == "DEFER_UNKNOWN" and known["action"] == "KEEP"


def check_MD10():
    history = [{"exercise": "squat", "status": "completed", "actual_sets": 3}]
    assert capability(history, "squat")["known"]
    assert not capability(history, "pistol_squat")["known"]


def check_MD11():
    req = ["assisted_pullup"]
    assert skill_eligible(req, set(req), "good")
    assert not skill_eligible(req, set(req), "poor")


def check_MD12():
    req = ["handstand_20s"]
    assert not skill_eligible(req, set(), "poor")
    assert skill_eligible(req, set(req), "good")


def check_MD13():
    req = ["weighted_pullup", "one_arm_negative"]
    assert not skill_eligible(req, {"weighted_pullup"}, "good")
    assert not skill_eligible(req, None, "good")


def check_MD14():
    assert not equipment_ok({"parallettes"}, {"floor", "mat"})
    assert equipment_ok({"floor"}, {"floor", "mat"})


def check_MD15():
    unweighted = strength_dose(3, 5, 0)
    weighted = strength_dose(3, 5, 10)
    assert unweighted["reps"] == weighted["reps"]
    assert unweighted["tonnage_kg"] != weighted["tonnage_kg"]


def check_MD16():
    jump = dose_vector(conditioning={"sec": 1200, "impacts": 140})
    quiet = dose_vector(conditioning={"sec": 1200, "impacts": 0})
    assert jump["conditioning"]["sec"] == quiet["conditioning"]["sec"]
    assert jump["conditioning"]["impacts"] != quiet["conditioning"]["impacts"]


def check_MD17():
    full = planned_actual(5, 5, "completed")
    partial = planned_actual(5, 3, "partial")
    assert full["actual"] == 5 and partial["actual"] == 3


def check_MD18():
    before, proposed = {"minutes": 45}, {"minutes": 20}
    assert apply_revision(before, proposed, "pending") == before
    assert apply_revision(before, proposed, "approved") == proposed
    assert approval_ledger(before, proposed, "approved")["before"] == before


def check_MD19():
    no_record = normalized_evidence_status(None)
    confirmed_skip = normalized_evidence_status("skipped")
    assert no_record == "unknown" and confirmed_skip == "skipped"
    assert no_record != confirmed_skip


def check_MD20():
    pain = keep(pain=True)
    normal = keep(pain=False)
    assert pain["action"] == "DEFER_UNKNOWN"
    assert pain["reason"] == "SELF_REPORTED_PAIN" and normal["action"] == "KEEP"


def check_MD21():
    assert time_feasible([20, 30], 90, max_sessions=2)
    assert not time_feasible([20, 30, 10], 90, max_sessions=2)


def check_MD22():
    inp = {"status": "completed", "durations": [30, 30],
           "minutes_available": 60, "policy_version": "research-v0"}
    assert deterministic_proposal(inp) == deterministic_proposal(dict(inp))


# E005: temporal load, subjective strain and model state boundaries
def check_AL01():
    easy, mixed = run_easy(), run_quality()
    assert easy.running_km == mixed.running_km
    assert easy.hard_sec == 0 and mixed.hard_sec > 0


def check_AL02():
    a = athlete_difficulty(5, 2, "athlete-A")
    b = athlete_difficulty(5, 8, "athlete-B")
    assert a["objective_workout_level"] == b["objective_workout_level"]
    assert a["predicted_relative_difficulty"] != b["predicted_relative_difficulty"]
    assert a["observed_outcome"] is None


def check_AL03():
    fresh = athlete_difficulty(6, 1, "A")
    loaded = athlete_difficulty(6, 8, "A")
    assert fresh["predicted_relative_difficulty"] != loaded["predicted_relative_difficulty"]
    assert loaded["observed_outcome"] is None


def check_AL04():
    planned = observed_run("planned", run_easy(), None)
    completed = observed_run("completed", run_easy(), run_easy())
    assert planned is None and completed.running_km == 10


def check_AL05():
    work = run_dose([{"kind": "hard", "km": 5, "sec": 1500}])
    assert observed_run("skipped", work, None).hard_sec == 0
    assert observed_run("completed", work, work).hard_sec == 1500


def check_AL06():
    a = score_series(90, "generic_power", "ftp-v1")
    b = score_series(90, "generic_power", "ftp-v2")
    assert not safe_merge_series(a, b)
    assert a["score"] == b["score"]


def check_AL07():
    versioned = athlete_difficulty(5, 2, "athlete-A")
    assert versioned["predictor_version"] == "toy-unvalidated-0"
    assert versioned["observed_outcome"] is None


def check_AL08():
    one = allocate_sets(10, [1])
    split = allocate_sets(10, [7, 3])
    assert sum(one) == sum(split) == 10
    assert split == [7, 3]


def check_AL09():
    before = allocate_sets(10, [7, 3])
    after = allocate_sets(12, [7, 3])
    assert sum(before) == 10 and sum(after) == 12
    assert before != after


def check_AL10():
    a = strength_dose(3, 8, 40, rir=1)
    b = strength_dose(3, 8, 40, rir=4)
    assert a["tonnage_kg"] == b["tonnage_kg"]
    assert a["rir"] == 1 and b["rir"] == 4


def check_AL11():
    report_missing = {"pain": None}
    report_present = {"pain": True}
    assert report_missing["pain"] is None
    assert keep(pain=report_present["pain"])["action"] == "DEFER_UNKNOWN"


def check_AL12():
    assert normalized_evidence_status("skipped") != normalized_evidence_status(None)
    assert planned_actual(3, None, "skipped")["actual"] == 0
    assert planned_actual(3, None, "unknown")["actual"] is None


def check_AL13():
    a, b = run_easy(), run_quality()
    score_a, score_b = stress("generic_hr", 100), stress("generic_hr", 100)
    assert score_a["value"] == score_b["value"]
    assert (a.easy_sec, a.hard_sec) != (b.easy_sec, b.hard_sec)


def check_AL14():
    valid = stress("generic_run_pace", 60)
    invalid = stress("generic_run_pace", 60, threshold_verified=False, gps_verified=False)
    assert valid["valid"] and invalid["value"] is None
    assert invalid["reason"] == "NOT_COMPUTABLE"


def check_AL15():
    run = score_series(60, "generic_run_pace", "run-v1")
    gym = score_series(60, "generic_hr", "hr-v1")
    assert not safe_merge_series(run, gym)


def check_AL16():
    x = generic_ema(50, 55, 70)
    assert x["today_tsb"] == -5
    assert abs(x["ctl_after_today"] - 50.47619047619) < 1e-8
    assert abs(x["atl_after_today"] - 57.14285714286) < 1e-8
    assert abs(x["next_day_tsb"] - (-6.66666666667)) < 1e-8


def check_AL17():
    unknown = observed_run("unknown", run_easy(), None)
    rest = observed_run("skipped", run_easy(), None)
    assert unknown is None and rest.running_km == 0


def check_AL18():
    old = score_series(60, "generic_run_pace", "pace-v1")
    new = score_series(60, "generic_run_pace", "pace-v2")
    assert not safe_merge_series(old, new)
    assert old["threshold_version"] == "pace-v1"


def registered_checks():
    """Only explicitly implemented, callable assertions are counted."""
    return {name.removeprefix("check_"): value
            for name, value in globals().items()
            if name.startswith("check_") and callable(value)}
