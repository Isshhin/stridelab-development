"""Invented-only negative controls for P2-E native exposure and coordination."""
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
import unittest

from p2e_exposure import summarize
from p2e_coordination import compare_shadow, review

NOW = datetime(2026, 11, 8, 12, tzinfo=timezone.utc)


def timestamp(hours=0):
    return (NOW + timedelta(hours=hours)).isoformat()


def session(sid="R", discipline="running", *, h=6, duration=60, **kwargs):
    x = {"id": sid, "discipline": discipline,
         "scheduled_at": timestamp(h), "duration_min": duration}
    if discipline == "running":
        x["intent"] = "threshold"
    x.update(kwargs)
    return x


def dose(discipline, **kwargs):
    d = {
        "running": {"km": 10.0, "easy_s": 2000, "hard_s": 600, "recovery_s": 400},
        "strength": {"exercise_id": "squat", "sets": 3, "reps": 8,
                     "load_kg": 40.0, "rir": None, "muscle_roles": ["lower"],
                     "reported_demand": "high"},
        "calisthenics": {"skill_node": "pistol_squat", "reps": 8, "hold_s": 0,
                         "form_quality": "unknown", "prerequisites_verified": None,
                         "muscle_region": "lower", "reported_demand": "high"},
        "conditioning": {"rounds": 4, "work_s": 800, "rest_s": 300,
                         "landing_count": None, "impact_profile": "jump_heavy",
                         "reported_demand": "high"},
    }[discipline]
    d = deepcopy(d)
    d.update(kwargs)
    return d


def snap(*sessions, target="R", athlete="synthetic-A", id="op-1"):
    if not sessions:
        sessions = (session(),)
    target_date = datetime.fromisoformat(sessions[0]["scheduled_at"]).date().isoformat()
    return {
        "synthetic_only": True, "athlete_id": athlete, "opportunity_id": id,
        "as_of": timestamp(),
        "plan": {"id": "plan-invented", "version": 1, "athlete_id": athlete,
                 "sessions": list(sessions)},
        "observations": [],
        "review_context": {
            "target_session_id": target, "expected_plan_version": 1,
            "restriction_status": "none", "stop_requested": False,
            "available_minutes_by_date": {target_date: 180},
            "max_sessions_per_day": 3, "locked_dates": [],
            "equipment_status": "available", "prerequisites_verified": True,
        },
    }


def event(s, sid, event_id="ev-1", *, status="completed", dose_patch=None, **kwargs):
    target = next(r for r in s["plan"]["sessions"] if r["id"] == sid)
    scheduled = datetime.fromisoformat(target["scheduled_at"])
    e = {"athlete_id": s["athlete_id"], "plan_id": s["plan"]["id"],
         "plan_version": s["plan"]["version"], "event_id": event_id,
         "session_id": sid, "discipline": target["discipline"],
         "source_id": "invented-source", "link_status": "synthetic_verified",
         "quality": "good",
         "observed_at": (scheduled + timedelta(minutes=10)).isoformat(),
         "available_at": (scheduled + timedelta(minutes=15)).isoformat(),
         "status": status,
         "dose": dose(target["discipline"], **(dose_patch or {}))
                 if status in ("completed", "partial") else None,
         "symptom_report": "unknown"}
    if status == "confirmed_skipped":
        e["reason"] = "logistical"
    e.update(kwargs)
    return e


def add_strength(s, *, demand="high", roles=None, status="completed", **kwargs):
    s["plan"]["sessions"].append(session("S", "strength", h=-6, duration=40))
    payload = event(s, "S", "strength-1", status=status, dose_patch={
        "reported_demand": demand, "muscle_roles": ["lower"] if roles is None else roles
    }, **kwargs)
    s["observations"].append(payload)
    return s


def add_calis(s, *, region="lower", form="unknown", status="completed", **kwargs):
    s["plan"]["sessions"].append(session("C", "calisthenics", h=-5, duration=40))
    payload = event(s, "C", "calis-1", status=status, dose_patch={
        "muscle_region": region, "form_quality": form}, **kwargs)
    s["observations"].append(payload)
    return s


def add_conditioning(s, *, profile="jump_heavy", status="completed", **kwargs):
    s["plan"]["sessions"].append(session("K", "conditioning", h=-4, duration=20))
    payload = event(s, "K", "cond-1", status=status,
                    dose_patch={"impact_profile": profile}, **kwargs)
    s["observations"].append(payload)
    return s


class ExposureTests(unittest.TestCase):
    def test_01_empty_plan_with_no_running_dose(self):
        s=snap()
        x=summarize(s)
        self.assertEqual(0, x["known_native_totals"]["running_km"])
        self.assertEqual("unknown", x["roster"][0]["status"])

    def test_02_verified_running_dose_is_native(self):
        s=snap(session(h=-4))
        s["observations"]=[event(s,"R")]
        x=summarize(s)
        self.assertEqual(10, x["known_native_totals"]["running_km"])
        self.assertEqual(600, x["known_native_totals"]["running_hard_s"])
        self.assertEqual(0, x["known_native_totals"]["strength_sets"])

    def test_03_strength_sets_do_not_become_running_km(self):
        s=add_strength(snap())
        x=summarize(s)
        self.assertEqual(3, x["known_native_totals"]["strength_sets"])
        self.assertEqual(0, x["known_native_totals"]["running_km"])
        self.assertIsNone(x["roster"][0]["native_dose"])

    def test_04_calis_holds_and_reps_separate(self):
        s=add_calis(snap())
        s["observations"][0]["dose"]["hold_s"]=35
        x=summarize(s)
        self.assertEqual(35, x["known_native_totals"]["calis_hold_s"])
        self.assertEqual(8, x["known_native_totals"]["calis_reps"])
        self.assertEqual(0, x["known_native_totals"]["strength_sets"])

    def test_05_conditioning_work_and_rest_separate(self):
        x=summarize(add_conditioning(snap()))
        self.assertEqual(800, x["known_native_totals"]["conditioning_work_s"])
        self.assertEqual(300, x["known_native_totals"]["conditioning_rest_s"])
        self.assertEqual(4, x["known_native_totals"]["conditioning_rounds"])

    def test_06_missing_landing_count_stays_unknown(self):
        x=summarize(add_conditioning(snap()))
        row=next(r for r in x["roster"] if r["session_id"]=="K")
        self.assertIsNone(row["native_dose"]["landing_count"])

    def test_07_partial_run_dose_not_planned_dose(self):
        s=snap(session(h=-4));s["observations"]=[event(s,"R",status="partial",
                   dose_patch={"km":3.0,"hard_s":0})]
        x=summarize(s)
        self.assertEqual(3.0,x["known_native_totals"]["running_km"])
        self.assertEqual("partial",x["roster"][0]["status"])

    def test_08_future_upload_not_counted(self):
        s=add_strength(snap())
        s["observations"][0]["available_at"]=timestamp(hours=2)
        x=summarize(s)
        self.assertEqual(0,x["known_native_totals"]["strength_sets"])
        self.assertFalse(x["past_coverage_complete"])

    def test_09_future_workout_not_incomplete_past(self):
        x=summarize(snap())
        self.assertTrue(x["past_coverage_complete"])

    def test_10_confirmed_skip_distinct_from_missing(self):
        s=snap(session(h=-4))
        s["observations"]=[event(s,"R",status="confirmed_skipped")]
        x=summarize(s)
        self.assertEqual("confirmed_skipped",x["roster"][0]["status"])
        self.assertTrue(x["past_coverage_complete"])

    def test_11_unknown_prior_is_not_confirmed_skip(self):
        s=snap(session(h=-4));x=summarize(s)
        self.assertEqual("unknown",x["roster"][0]["status"])
        self.assertFalse(x["past_coverage_complete"])

    def test_12_exact_duplicate_idempotent(self):
        s=add_strength(snap())
        s["observations"].append(deepcopy(s["observations"][0]))
        x=summarize(s)
        self.assertEqual(1,x["duplicate_inputs"])
        self.assertEqual(3,x["known_native_totals"]["strength_sets"])

    def test_13_conflicting_duplicate_rejected(self):
        s=add_strength(snap());r=deepcopy(s["observations"][0]);r["dose"]["sets"]=8
        s["observations"].append(r)
        with self.assertRaisesRegex(ValueError,"CONFLICTING_EVENT_ID"):summarize(s)

    def test_14_two_sources_one_session_are_conflict(self):
        s=add_strength(snap());r=deepcopy(s["observations"][0])
        r["event_id"]="strength-2";r["source_id"]="other-source"
        s["observations"].append(r);x=summarize(s)
        strength=next(row for row in x["roster"] if row["session_id"]=="S")
        self.assertEqual("conflict",strength["status"])
        self.assertEqual(0,x["known_native_totals"]["strength_sets"])

    def test_15_cross_athlete_identity_rejected(self):
        s=add_strength(snap());s["observations"][0]["athlete_id"]="synthetic-B"
        with self.assertRaisesRegex(ValueError,"CROSS_IDENTITY"):summarize(s)

    def test_16_foreign_plan_rejected(self):
        s=snap();s["plan"]["athlete_id"]="synthetic-B"
        with self.assertRaisesRegex(ValueError,"FOREIGN_PLAN"):summarize(s)

    def test_17_stale_event_plan_version_rejected(self):
        s=add_strength(snap());s["observations"][0]["plan_version"]=9
        with self.assertRaisesRegex(ValueError,"STALE_OR_FOREIGN"):summarize(s)

    def test_18_missing_synthetic_flag_rejected(self):
        s=snap();del s["synthetic_only"]
        with self.assertRaisesRegex(ValueError,"SYNTHETIC_ONLY_REQUIRED"):summarize(s)

    def test_19_fake_source_grant_has_no_effect(self):
        s=add_strength(snap());s["observations"][0]["link_status"]="not_verified"
        s["source_authorized"]=True
        x=summarize(s)
        self.assertEqual(0,x["known_native_totals"]["strength_sets"])
        self.assertIn("LINK_NOT_SYNTHETICALLY_VERIFIED",x["excluded_by_reason"])

    def test_20_low_quality_not_counted(self):
        s=add_strength(snap());s["observations"][0]["quality"]="suspect"
        self.assertEqual(0,summarize(s)["known_native_totals"]["strength_sets"])

    def test_21_missing_source_not_counted(self):
        s=add_strength(snap());s["observations"][0]["source_id"]=""
        self.assertEqual(0,summarize(s)["known_native_totals"]["strength_sets"])

    def test_22_future_observation_not_counted(self):
        s=add_strength(snap());s["observations"][0]["observed_at"]=timestamp(hours=5)
        self.assertEqual(0,summarize(s)["known_native_totals"]["strength_sets"])

    def test_23_inconsistent_availability_excluded(self):
        s=add_strength(snap());s["observations"][0]["available_at"]=timestamp(hours=-8)
        self.assertEqual(0,summarize(s)["known_native_totals"]["strength_sets"])

    def test_24_early_against_versioned_session_excluded(self):
        s=add_strength(snap());s["observations"][0]["observed_at"]=timestamp(hours=-7)
        s["observations"][0]["available_at"]=timestamp(hours=-6.5)
        self.assertIn("BEFORE_VERSIONED_SESSION",summarize(s)["excluded_by_reason"])

    def test_25_naive_time_rejected(self):
        s=snap();s["as_of"]="2026-11-08T12:00:00"
        with self.assertRaisesRegex(ValueError,"INVALID_OFFSET_TIMESTAMP"):summarize(s)

    def test_26_outcome_leakage_rejected(self):
        s=snap();s["plan"]["sessions"][0]["future_outcome"]="success"
        with self.assertRaisesRegex(ValueError,"OUTCOME_LABEL_LEAKAGE"):summarize(s)

    def test_27_strength_missing_rir_remains_unknown(self):
        s=add_strength(snap());x=summarize(s)
        row=next(r for r in x["roster"] if r["session_id"]=="S")
        self.assertIsNone(row["native_dose"]["rir"])

    def test_28_strength_invalid_rir_rejected(self):
        s=add_strength(snap());s["observations"][0]["dose"]["rir"]=-1
        with self.assertRaisesRegex(ValueError,"STRENGTH_RIR_INVALID"):summarize(s)

    def test_29_strength_muscle_roles_validate(self):
        s=add_strength(snap());s["observations"][0]["dose"]["muscle_roles"]=[["bad"]]
        with self.assertRaisesRegex(ValueError,"STRENGTH_MUSCLE_ROLES_INVALID"):summarize(s)

    def test_30_skill_form_not_automatically_mastered(self):
        s=add_calis(snap(),form="poor")
        x=summarize(s)
        row=next(r for r in x["roster"] if r["session_id"]=="C")
        self.assertEqual("poor",row["native_dose"]["form_quality"])
        self.assertIsNone(row["native_dose"]["prerequisites_verified"])

    def test_31_strength_zero_sets_valid_but_not_fitness(self):
        s=add_strength(snap());s["observations"][0]["dose"]["sets"]=0
        x=summarize(s)
        self.assertEqual(0,x["known_native_totals"]["strength_sets"])
        self.assertEqual("completed",next(r for r in x["roster"] if r["session_id"]=="S")["status"])

    def test_32_negative_distance_rejected(self):
        s=snap(session(h=-4));s["observations"]=[event(s,"R",dose_patch={"km":-1})]
        with self.assertRaisesRegex(ValueError,"RUN_NATIVE_DOSE_INVALID"):summarize(s)

    def test_33_nan_load_rejected(self):
        s=add_strength(snap());s["observations"][0]["dose"]["load_kg"]=float("nan")
        with self.assertRaisesRegex(ValueError,"STRENGTH_NATIVE_DOSE_INVALID"):summarize(s)

    def test_34_boolean_reps_rejected(self):
        s=add_calis(snap());s["observations"][0]["dose"]["reps"]=True
        with self.assertRaisesRegex(ValueError,"CALISTHENICS_NATIVE_DOSE_INVALID"):summarize(s)

    def test_35_negative_landing_count_rejected(self):
        s=add_conditioning(snap());s["observations"][0]["dose"]["landing_count"]=-1
        with self.assertRaisesRegex(ValueError,"CONDITIONING_IMPACTS_INVALID"):summarize(s)

    def test_36_unknown_discipline_rejected(self):
        s=snap();s["plan"]["sessions"][0]["discipline"]="yoga"
        with self.assertRaisesRegex(ValueError,"INVALID_DISCIPLINE"):summarize(s)

    def test_37_confirmed_skip_with_dose_rejected(self):
        s=snap(session(h=-4))
        r=event(s,"R",status="confirmed_skipped");r["dose"]=dose("running")
        s["observations"]=[r]
        with self.assertRaisesRegex(ValueError,"NONEXPOSURE_HAS_DOSE"):summarize(s)

    def test_38_completed_without_native_dose_rejected(self):
        s=add_strength(snap());s["observations"][0]["dose"]=None
        with self.assertRaisesRegex(ValueError,"NATIVE_DOSE_REQUIRED"):summarize(s)

    def test_39_duplicate_sessions_rejected(self):
        s=snap();s["plan"]["sessions"].append(deepcopy(s["plan"]["sessions"][0]))
        with self.assertRaisesRegex(ValueError,"DUPLICATE_PLANNED_SESSION"):summarize(s)

    def test_40_unknown_session_excluded(self):
        s=add_strength(snap());s["observations"][0]["session_id"]="not-in-plan"
        self.assertIn("UNLINKED_OR_UNKNOWN_SESSION",summarize(s)["excluded_by_reason"])

    def test_41_stable_under_event_reordering(self):
        s=add_calis(add_strength(snap()))
        x=summarize(s);q=deepcopy(s);q["observations"].reverse()
        self.assertEqual(x,summarize(q))

    def test_42_does_not_mutate_inputs(self):
        s=add_conditioning(add_strength(snap()))
        old=deepcopy(s);summarize(s)
        self.assertEqual(s,old)

    def test_43_local_day_preserved_even_across_utc_boundary(self):
        s=snap(session(scheduled_at="2026-11-09T00:30:00+08:00"))
        x=summarize(s)
        self.assertEqual("2026-11-09",x["roster"][0]["scheduled_local_date"])

    def test_44_partial_strength_is_not_planned_extra_sets(self):
        s=add_strength(snap(),status="partial")
        s["observations"][0]["dose"]["sets"]=1
        self.assertEqual(1,summarize(s)["known_native_totals"]["strength_sets"])

    def test_45_simultaneous_session_ids_not_merged(self):
        s=add_strength(snap())
        self.assertEqual({"R","S"},{row["session_id"] for row in summarize(s)["roster"]})

class CoordinationTests(unittest.TestCase):
    def test_46_empty_upcoming_plan_no_conflict_not_validated(self):
        out=review(snap())
        self.assertEqual("NO_STRUCTURAL_CONFLICT_OBSERVED",out["review_category"])
        self.assertIsNone(out["predicted_performance"])
        self.assertIsNone(out["candidate_plan"])
        self.assertFalse(out["applied"])

    def test_47_high_lower_strength_same_day_flags_overlap(self):
        s=add_strength(snap())
        out=review(s)
        self.assertEqual("REVIEW_OBSERVED_OVERLAP",out["review_category"])
        self.assertEqual("REPORTED_HIGH_LOWER_STRENGTH",out["overlap"]["observed"][0]["reason"])

    def test_48_upper_strength_control_no_lower_overlap(self):
        s=add_strength(snap(),roles=["upper"])
        self.assertEqual("NO_STRUCTURAL_CONFLICT_OBSERVED",review(s)["review_category"])

    def test_49_low_demand_lower_strength_no_high_overlap(self):
        s=add_strength(snap(),demand="low")
        self.assertEqual("NO_STRUCTURAL_CONFLICT_OBSERVED",review(s)["review_category"])

    def test_50_calis_lower_high_flags(self):
        out=review(add_calis(snap()))
        self.assertEqual("REVIEW_OBSERVED_OVERLAP",out["review_category"])
        self.assertEqual("REPORTED_HIGH_LOWER_SKILL",out["overlap"]["observed"][0]["reason"])

    def test_51_jump_heavy_conditioning_flags(self):
        out=review(add_conditioning(snap()))
        self.assertEqual("REVIEW_OBSERVED_OVERLAP",out["review_category"])

    def test_52_low_impact_conditioning_not_equated_to_jumps(self):
        out=review(add_conditioning(snap(),profile="low_impact"))
        self.assertEqual("NO_STRUCTURAL_CONFLICT_OBSERVED",out["review_category"])

    def test_53_unobserved_other_session_only_possible_overlap(self):
        s=add_strength(snap())
        s["observations"]=[]
        self.assertEqual("DEFER_EXPOSURE_RECONCILIATION",review(s)["review_category"])

    def test_54_stop_overrides_capacity_conflict(self):
        s=add_strength(snap())
        s["review_context"]["max_sessions_per_day"]=1
        s["review_context"]["stop_requested"]=True
        self.assertEqual("DEFER_STOP_REQUESTED",review(s)["review_category"])

    def test_55_unknown_clinical_status_defers(self):
        s=snap();s["review_context"]["restriction_status"]="unknown"
        self.assertEqual("DEFER_CLINICAL_CONTEXT",review(s)["review_category"])

    def test_56_reported_cleared_is_not_app_clearance(self):
        s=snap();s["review_context"]["restriction_status"]="reported_cleared"
        self.assertEqual("DEFER_CLINICAL_CONTEXT",review(s)["review_category"])

    def test_57_symptom_overrides_capacity_conflict(self):
        s=add_strength(snap(),symptom_report="pain")
        s["review_context"]["max_sessions_per_day"]=1
        self.assertEqual("DEFER_SYMPTOM_REVIEW",review(s)["review_category"])

    def test_58_missing_day_budget_defers(self):
        s=snap();s["review_context"]["available_minutes_by_date"]={}
        self.assertEqual("DEFER_CAPACITY_UNKNOWN",review(s)["review_category"])

    def test_59_time_budget_conflict_does_not_change_workout(self):
        s=add_strength(snap())
        s["review_context"]["available_minutes_by_date"]["2026-11-08"]=80
        x=review(s)
        self.assertEqual("REVIEW_CAPACITY_CONFLICT",x["review_category"])
        self.assertIsNone(x["proposed_dose"])

    def test_60_session_cap_respected(self):
        s=add_strength(snap())
        s["review_context"]["max_sessions_per_day"]=1
        self.assertEqual("REVIEW_CAPACITY_CONFLICT",review(s)["review_category"])

    def test_61_locked_day_deferred(self):
        s=snap();s["review_context"]["locked_dates"]=["2026-11-08"]
        self.assertEqual("DEFER_LOCKED_DAY",review(s)["review_category"])

    def test_62_missing_equipment_unknown_not_substitute(self):
        s=snap(session("R","strength"),target="R")
        s["review_context"]["equipment_status"]="unknown"
        out=review(s)
        self.assertEqual("DEFER_EQUIPMENT_UNKNOWN",out["review_category"])
        self.assertIsNone(out["candidate_plan"])

    def test_63_unavailable_equipment_review_only(self):
        s=snap(session("R","strength"),target="R")
        s["review_context"]["equipment_status"]="unavailable"
        self.assertEqual("REVIEW_EQUIPMENT_UNAVAILABLE",review(s)["review_category"])

    def test_64_unverified_skill_does_not_promote(self):
        s=snap(session("R","calisthenics"),target="R")
        s["review_context"]["prerequisites_verified"]=False
        out=review(s)
        self.assertEqual("REVIEW_UNVERIFIED_SKILL",out["review_category"])
        self.assertFalse(out["auto_skill_mastery"])

    def test_65_unknown_prerequisite_defers(self):
        s=snap(session("R","calisthenics"),target="R")
        s["review_context"]["prerequisites_verified"]=None
        self.assertEqual("DEFER_PREREQUISITE_UNKNOWN",review(s)["review_category"])

    def test_66_bogus_numeric_prerequisite_not_boolean(self):
        s=snap(session("R","calisthenics"),target="R")
        s["review_context"]["prerequisites_verified"]=1
        with self.assertRaisesRegex(ValueError,"SKILL_PREREQUISITES_INVALID"):review(s)

    def test_67_no_automatic_recovery_timer(self):
        out=review(add_strength(snap()))
        self.assertIsNone(out["fixed_recovery_hours"])
        self.assertIsNone(out["risk_probability"])

    def test_68_known_only_native_totals_stay_independent(self):
        out=review(add_conditioning(add_strength(snap())))
        self.assertEqual(3,out["native_exposure"]["strength_sets"])
        self.assertEqual(4,out["native_exposure"]["conditioning_rounds"])
        self.assertEqual(0,out["native_exposure"]["running_km"])

    def test_69_unapproved_source_cannot_create_overlap(self):
        s=add_strength(snap())
        s["observations"][0]["link_status"]="unverified"
        self.assertEqual("DEFER_EXPOSURE_RECONCILIATION",review(s)["review_category"])

    def test_70_old_day_high_strength_does_not_assert_same_day_overlap(self):
        s=snap(session())
        s["plan"]["sessions"].append(session("S","strength",h=-30))
        s["observations"].append(event(s,"S","old"))
        self.assertEqual("NO_STRUCTURAL_CONFLICT_OBSERVED",review(s)["review_category"])

    def test_71_nonpriority_easy_run_does_not_flag_strength_overlap(self):
        s=add_strength(snap(session(intent="easy")))
        self.assertEqual("NO_STRUCTURAL_CONFLICT_OBSERVED",review(s)["review_category"])

    def test_72_conditional_overlap_is_symmetrical_to_strength_target(self):
        s=snap(session("S","strength",h=6),session("R","running",h=-4),
               target="S")
        s["observations"]=[event(s,"R",event_id="run-1")]
        out=review(s)
        self.assertEqual("REVIEW_OBSERVED_OVERLAP",out["review_category"])

    def test_73_review_immutable(self):
        s=add_strength(snap());old=deepcopy(s)
        review(s)
        self.assertEqual(old,s)

    def test_74_unsupported_target_rejected(self):
        s=snap();s["review_context"]["target_session_id"]="absent"
        with self.assertRaisesRegex(ValueError,"TARGET_SESSION_NOT_FOUND"):review(s)

    def test_75_stale_plan_version_rejected(self):
        s=snap();s["review_context"]["expected_plan_version"]=0
        with self.assertRaisesRegex(ValueError,"STALE_PLAN_VERSION"):review(s)

    def test_76_local_date_day_budget_correct_across_zone(self):
        s=snap(session(scheduled_at="2026-11-09T00:30:00+08:00"))
        s["review_context"]["available_minutes_by_date"]={"2026-11-09":180}
        x=review(s)
        self.assertEqual("NO_STRUCTURAL_CONFLICT_OBSERVED",x["review_category"])

    def test_77_future_evidence_does_not_make_false_overlap(self):
        s=add_strength(snap())
        s["observations"][0]["available_at"]=timestamp(hours=1)
        x=review(s)
        self.assertEqual([],x["overlap"]["observed"])

    def test_78_readiness_not_returned(self):
        x=review(snap())
        self.assertNotIn("readiness_score",x)
        self.assertEqual("NONE_SYNTHETIC_RESEARCH",x["coaching_authority"])

    def test_79_stop_needs_no_approval_to_stop_but_no_plan_save(self):
        s=snap();s["review_context"]["stop_requested"]=True
        x=review(s)
        self.assertTrue(x["requires_separate_approval"])
        self.assertIsNone(x["candidate_plan"])
        self.assertEqual("DEFER_STOP_REQUESTED",x["review_category"])

    def test_80_empty_cohort_no_best_policy(self):
        x=compare_shadow([])
        self.assertEqual(0,x["common_denominator"])
        self.assertIsNone(x["best_policy"])

    def test_81_same_denominator_two_review_arms(self):
        s=snap(id="a");other=add_strength(snap(id="b"))
        x=compare_shadow([s,other])
        self.assertEqual(2,x["opportunity_count"])
        self.assertEqual(2,x["common_denominator"])
        self.assertIsNone(x["effectiveness"])

    def test_82_comparison_deterministic_order(self):
        a=snap(id="a");b=add_strength(snap(id="b"))
        self.assertEqual(compare_shadow([b,a]),compare_shadow([a,b]))

    def test_83_duplicate_opportunity_fails(self):
        s=snap()
        with self.assertRaisesRegex(ValueError,"DUPLICATE_OPPORTUNITY"):
            compare_shadow([s,deepcopy(s)])

    def test_84_foreign_athlete_same_opportunity_allowed(self):
        a=snap();b=snap(athlete="synthetic-B")
        self.assertEqual(2,compare_shadow([a,b])["opportunity_count"])

    def test_85_baseline_holds_explicit_stop(self):
        s=snap();s["review_context"]["stop_requested"]=True
        x=compare_shadow([s])
        self.assertEqual("HOLD_NO_APPROVAL_TO_STOP",x["cases"][0]["baseline"])

    def test_86_baseline_holds_pain_not_keep(self):
        s=add_strength(snap(),symptom_report="pain")
        x=compare_shadow([s])
        self.assertEqual("HOLD_NO_APPROVAL_TO_STOP",x["cases"][0]["baseline"])

    def test_87_comparison_no_claimed_training_outcome(self):
        x=compare_shadow([snap()])
        self.assertIsNone(x["effectiveness"])
        self.assertIsNone(x["risk_probability"])
        self.assertIsNone(x["new_plan"])
        self.assertFalse(x["applied"])

    def test_88_invalid_budget_type_fails(self):
        s=snap();s["review_context"]["available_minutes_by_date"]["2026-11-08"]=True
        with self.assertRaisesRegex(ValueError,"DAY_BUDGET_INVALID"):review(s)

    def test_89_forged_recovery_clock_ignored(self):
        s=add_strength(snap())
        s["review_context"]["fixed_recovery_hours"]=0
        self.assertIsNone(review(s)["fixed_recovery_hours"])

    def test_90_all_four_discipline_sessions_keep_separate_native_dose(self):
        s=add_conditioning(add_calis(add_strength(snap())))
        x=summarize(s)
        self.assertEqual(4,len(x["roster"]))
        self.assertEqual(3,x["known_native_totals"]["strength_sets"])
        self.assertEqual(8,x["known_native_totals"]["calis_reps"])
        self.assertEqual(4,x["known_native_totals"]["conditioning_rounds"])
        self.assertEqual(0,x["known_native_totals"]["running_hard_s"])

if __name__ == "__main__":
    unittest.main()
