"""P2-D DEV-0 adversarial tests: synthetic-only shadow decisions, no plan writes."""
import copy
from datetime import datetime, timedelta, timezone
import json
import unittest
from p2d_shadow import shadow_review, compare_shadow, OPTION_ORDER

NOW = datetime(2026, 11, 8, 12, tzinfo=timezone.utc)

def tm(day=0, hour=0):
    return (NOW + timedelta(days=day, hours=hour)).isoformat()

def event(name="e1", value="time_short", *, kind="athlete_report", day=-1, available_day=None, **patch):
    result = {"event_id": name, "athlete_id": "invented-A", "session_id": "s1",
              "kind": kind, "observed_at": tm(day), "available_at": tm(day if available_day is None else available_day),
              "source_id": "invented-source-"+name, "protocol_id": "invented-protocol-v1",
              "lineage_root_id": "root-"+name, "status": "measured", "quality": "good", "value": value}
    result.update(patch)
    return result

def frame(reason="time_short", purpose="controlled_threshold", *, clinical="none", rows=None,
          opportunity="invented-opportunity-1", registered=True):
    if rows is None:
        rows=[event()]
    return {"synthetic_only": True, "athlete_id": "invented-A", "opportunity_id": opportunity,
            "as_of": tm(), "plan": {"athlete_id": "invented-A", "plan_id": "P-1",
            "version": 4, "sessions": [{"id": "s1", "discipline": "running", "purpose": purpose,
                                        "day": "Tue", "locked": False},
                                       {"id": "s2", "discipline": "running", "purpose": "easy_aerobic",
                                        "day": "Sat", "locked": False}]},
            "request": {"expected_plan_version": 4, "session_id": "s1",
                        "primary_purpose": purpose, "reason": reason,
                        "clinical_status": clinical, "evidence_ids": ["e1"],
                        "synthetic_fixture_registered": registered,
                        "reentry_status": "not_applicable"},
            "observations": rows}


def codes(v):
    return [x["code"] for x in v["options"]]


class P2DShadowContractTests(unittest.TestCase):
    def test_01_time_short_generates_three_options(self):
        self.assertEqual(["REDUCE_VOLUME", "MOVE", "REST"], codes(shadow_review(frame())))

    def test_02_uninterrupted_long_duration_tradeoff(self):
        v=shadow_review(frame(purpose="long_duration"))
        self.assertEqual("PARTIAL_LONG_DURATION_PURPOSE", v["options"][0]["tradeoff"])

    def test_03_pace_discomfort(self):
        self.assertEqual(["REDUCE_INTENSITY", "RECOVERY", "REST"], codes(shadow_review(frame("pace_uncomfortable"))))

    def test_04_late_bout_difficulty(self):
        self.assertEqual(["SHORTEN_INTERVALS", "REDUCE_REPS", "REST"], codes(shadow_review(frame("late_bout_fatigue"))))

    def test_05_interval_specific_bout_difficulty(self):
        self.assertIn("SHORTEN_INTERVALS", codes(shadow_review(frame("late_bout_fatigue", "fast_repetitions"))))

    def test_06_late_bout_no_generic_easy_edit(self):
        v=shadow_review(frame("late_bout_fatigue", "easy_aerobic"))
        self.assertEqual("ABSTAIN", v["status"])

    def test_07_equipment_substitution_not_equivalence(self):
        v=shadow_review(frame("equipment_unavailable"))
        self.assertEqual(["SUBSTITUTE", "MOVE", "REST"], codes(v))
        self.assertEqual("MODALITY_EQUIVALENCE_UNPROVEN", v["options"][0]["tradeoff"])

    def test_08_schedule_conflict(self):
        self.assertEqual(["REDUCE_VOLUME", "MOVE", "REST"], codes(shadow_review(frame("schedule_conflict"))))

    def test_09_fatigue_doesnt_keep_hard_training(self):
        self.assertEqual(["RECOVERY", "REST"], codes(shadow_review(frame("fatigue_reported"))))

    def test_10_requested_stop_only(self):
        self.assertEqual(["REST"], codes(shadow_review(frame("requested_stop"))))

    def test_11_none_keeps_without_modification(self):
        self.assertEqual(["KEEP"], codes(shadow_review(frame("none"))))

    def test_12_missing_session_is_defer(self):
        v=shadow_review(frame("missed_session"))
        self.assertEqual("ABSTAIN", v["status"])
        self.assertEqual([], v["options"])

    def test_13_unknown_reason_is_defer(self):
        self.assertEqual("ABSTAIN", shadow_review(frame("unknown"))["status"])

    def test_14_injury_is_clinical_boundary(self):
        v=shadow_review(frame("injury"))
        self.assertEqual("CLINICAL_BOUNDARY_DEFER", v["status"])
        self.assertEqual(["REST"],codes(v))

    def test_15_illness_is_clinical_boundary(self):
        self.assertEqual(["REST"], codes(shadow_review(frame("illness"))))

    def test_16_unknown_clinical_defer(self):
        v=shadow_review(frame("time_short",clinical="unknown"))
        self.assertEqual("CLINICAL_BOUNDARY_DEFER", v["status"])

    def test_17_active_clinical_defer(self):
        self.assertEqual("CLINICAL_BOUNDARY_DEFER",shadow_review(frame(clinical="active"))["status"])

    def test_18_clinical_observation_overrides_keep(self):
        s=frame("none",rows=[event(value="active",kind="clinical_context")])
        self.assertEqual("CLINICAL_BOUNDARY_DEFER",shadow_review(s)["status"])

    def test_19_clinical_unknown_observation_overrides_keep(self):
        s=frame("none",rows=[event(value="unknown",kind="clinical_context")])
        self.assertEqual(["REST"],codes(shadow_review(s)))

    def test_20_reported_stop_overrides_context(self):
        s=frame("none",rows=[event(value="cannot_continue")])
        self.assertEqual("STOP_REQUESTED",shadow_review(s)["status"])

    def test_21_difficult_session_conflict_does_not_keep(self):
        s=frame("none",rows=[event(value="struggled",kind="observed_session")])
        self.assertEqual("CONFLICTING_EFFORT_EVIDENCE",shadow_review(s)["reason"])

    def test_22_future_outcome_label_rejected(self):
        s=frame();s["observations"][0]["future_outcome"]="great"
        with self.assertRaisesRegex(ValueError,"OUTCOME_OR_FUTURE_LABEL_FORBIDDEN"):
            shadow_review(s)

    def test_23_nested_target_label_rejected(self):
        s=frame();s["request"]["extra"]={"future_performance":1}
        with self.assertRaisesRegex(ValueError,"OUTCOME_OR_FUTURE_LABEL_FORBIDDEN"):
            shadow_review(s)

    def test_24_future_observation_doesnt_count(self):
        s=frame(rows=[event(day=1)])
        self.assertEqual("ABSTAIN",shadow_review(s)["status"])

    def test_25_future_upload_doesnt_count(self):
        s=frame(rows=[event(available_day=1)])
        self.assertEqual("MISSING_OR_FUTURE_EVIDENCE",shadow_review(s)["reason"])

    def test_26_invalid_time_raises(self):
        s=frame();s["observations"][0]["available_at"]="not a time"
        with self.assertRaisesRegex(ValueError,"INVALID_OFFSET_TIMESTAMP"):
            shadow_review(s)

    def test_27_naive_timestamp_raises(self):
        s=frame();s["as_of"]="2026-11-08T12:00:00"
        with self.assertRaisesRegex(ValueError,"INVALID_OFFSET_TIMESTAMP"):
            shadow_review(s)

    def test_28_backdated_availability_excluded(self):
        s=frame(rows=[event(day=-1,available_day=-2)])
        self.assertEqual("ABSTAIN",shadow_review(s)["status"])

    def test_29_missing_upload_doesnt_mean_skipped(self):
        s=frame(rows=[event(status="missing_upload")])
        self.assertEqual("ABSTAIN",shadow_review(s)["status"])

    def test_30_invalid_quality_excluded(self):
        s=frame(rows=[event(quality={"invalid":1})])
        self.assertEqual("ABSTAIN",shadow_review(s)["status"])

    def test_31_missing_lineage_excluded(self):
        s=frame(rows=[event(lineage_root_id=None)])
        self.assertEqual("MISSING_LINEAGE_ROOT",next(iter(shadow_review(s)["provenance"]["excluded_by_reason"])))

    def test_32_foreign_session_record_excluded(self):
        s=frame(rows=[event(session_id="s2")])
        self.assertEqual("SESSION_LINK_MISMATCH",next(iter(shadow_review(s)["provenance"]["excluded_by_reason"])))

    def test_33_foreign_athlete_rejected(self):
        s=frame(rows=[event(athlete_id="invented-B")])
        with self.assertRaisesRegex(ValueError,"CROSS_ATHLETE_OR_INVALID_EVENT"):
            shadow_review(s)

    def test_34_foreign_plan_rejected(self):
        s=frame();s["plan"]["athlete_id"]="invented-B"
        with self.assertRaisesRegex(ValueError,"PLAN_IDENTITY_MISMATCH"):
            shadow_review(s)

    def test_35_stale_plan_version_rejected(self):
        s=frame();s["request"]["expected_plan_version"]=3
        with self.assertRaisesRegex(ValueError,"STALE_PLAN_VERSION"):
            shadow_review(s)

    def test_36_missing_plan_session_rejected(self):
        s=frame();s["request"]["session_id"]="absent"
        with self.assertRaisesRegex(ValueError,"SESSION_NOT_FOUND"):
            shadow_review(s)

    def test_37_session_purpose_mismatch_rejected(self):
        s=frame();s["request"]["primary_purpose"]="long_duration"
        with self.assertRaisesRegex(ValueError,"PURPOSE_MISMATCH"):
            shadow_review(s)

    def test_38_duplicate_session_ids_rejected(self):
        s=frame();s["plan"]["sessions"][1]["id"]="s1"
        with self.assertRaisesRegex(ValueError,"PLAN_SESSION_ID_INVALID"):
            shadow_review(s)

    def test_39_session_lock_blocks(self):
        s=frame();s["plan"]["sessions"][0]["locked"]=True
        self.assertEqual("LOCKED_SESSION",shadow_review(s)["reason"])

    def test_40_bad_lock_type_rejected(self):
        s=frame();s["plan"]["sessions"][0]["locked"]="false"
        with self.assertRaisesRegex(ValueError,"SESSION_LOCK_TYPE_INVALID"):
            shadow_review(s)

    def test_41_unregistered_reentry_blocks(self):
        s=frame();s["request"]["reentry_status"]="restored"
        self.assertEqual("UNVERIFIED_REENTRY_AUTHORITY",shadow_review(s)["reason"])

    def test_42_synthetic_fixture_not_registered_blocks(self):
        self.assertEqual("SYNTHETIC_FIXTURE_NOT_REGISTERED",shadow_review(frame(registered=False))["reason"])

    def test_43_not_synthetic_rejected(self):
        s=frame();s["synthetic_only"]=False
        with self.assertRaisesRegex(ValueError,"SYNTHETIC_ONLY_REQUIRED"):
            shadow_review(s)

    def test_44_exact_duplicate_deduplicated(self):
        s=frame();s["observations"].append(copy.deepcopy(s["observations"][0]))
        out=shadow_review(s)
        self.assertEqual(1,out["provenance"]["duplicate_input_count"])
        self.assertEqual(1,out["provenance"]["unique_observation_count"])

    def test_45_conflicting_event_id_rejected(self):
        s=frame();q=copy.deepcopy(s["observations"][0]);q["value"]="none";s["observations"].append(q)
        with self.assertRaisesRegex(ValueError,"CONFLICTING_EVENT_ID"):
            shadow_review(s)

    def test_46_duplicate_evidence_references_rejected(self):
        s=frame();s["request"]["evidence_ids"]=["e1","e1"]
        with self.assertRaisesRegex(ValueError,"EVIDENCE_IDS_REQUIRED_UNIQUE"):
            shadow_review(s)

    def test_47_unreferenced_evidence_abstains(self):
        s=frame();s["request"]["evidence_ids"]=["does-not-exist"]
        self.assertEqual("ABSTAIN",shadow_review(s)["status"])

    def test_48_output_never_changes_plan(self):
        s=frame();old=copy.deepcopy(s);out=shadow_review(s)
        self.assertEqual(s,old)
        self.assertIsNone(out["plan_patch"])
        self.assertFalse(out["applied"])
        for opt in out["options"]:
            self.assertIsNone(opt["candidate_plan"])
            self.assertIsNone(opt["proposed_dose"])
            self.assertTrue(opt["requires_explicit_approval_to_save"])

    def test_49_permutation_of_observations_reproducible(self):
        e1=event();e2=event("e2","some data",kind="schedule_constraint")
        s=frame(rows=[e1,e2]);s["request"]["evidence_ids"]=["e2","e1"]
        t=copy.deepcopy(s);t["observations"].reverse();t["request"]["evidence_ids"].reverse()
        self.assertEqual(shadow_review(s),shadow_review(t))

    def test_50_options_follow_stable_order(self):
        v=shadow_review(frame("equipment_unavailable"))
        self.assertEqual(sorted(codes(v),key=lambda x:OPTION_ORDER.index(x)),codes(v))

    def test_51_two_policies_same_denominator(self):
        rows=[frame(opportunity="a"),frame("requested_stop",opportunity="b"),
              frame("unknown",opportunity="c")]
        result=compare_shadow(rows)
        self.assertEqual(3,result["same_denominator"])
        self.assertEqual(3,result["baseline_count"])
        self.assertEqual(3,result["context_count"])
        self.assertIsNone(result["best_policy"])

    def test_52_shadow_comparator_deterministic(self):
        rows=[frame(opportunity="z"),frame("unknown",opportunity="a")]
        self.assertEqual(compare_shadow(rows),compare_shadow(list(reversed(rows))))

    def test_53_shadow_rejects_duplicate_opportunity(self):
        x=frame()
        with self.assertRaisesRegex(ValueError,"DUPLICATE_OPPORTUNITY"):
            compare_shadow([x,copy.deepcopy(x)])

    def test_54_shadow_requires_opportunity_id(self):
        s=frame();del s["opportunity_id"]
        with self.assertRaisesRegex(ValueError,"OPPORTUNITY_ID_REQUIRED"):
            compare_shadow([s])

    def test_55_shadow_no_synthetic_outcome_claims(self):
        out=compare_shadow([frame()])
        self.assertEqual("CLOSED",out["gate2"])
        self.assertTrue(out["no_plan_write"])
        self.assertIsNone(out["false_restriction_rate"])
        self.assertEqual("NOT_ESTABLISHED",out["coaching_effectiveness"])

    def test_56_stop_requires_no_approval_to_stop_but_save_still_requires(self):
        opt=shadow_review(frame("requested_stop"))["options"][0]
        self.assertFalse(opt["stop_needs_approval"])
        self.assertTrue(opt["requires_explicit_approval_to_save"])

    def test_57_clinical_baseline_never_keeps(self):
        s=frame("injury")
        self.assertEqual("HOLD_FOR_CLINICAL_REVIEW",compare_shadow([s])["cases"][0]["baseline_policy"])

    def test_58_unhashable_kind_is_excluded_not_crash(self):
        s=frame(rows=[event(kind=["bad"])])
        self.assertEqual("ABSTAIN",shadow_review(s)["status"])

    def test_59_unhashable_value_does_not_crash(self):
        s=frame(rows=[event(value=["no"])])
        self.assertEqual("SHADOW_REVIEW_ONLY",shadow_review(s)["status"])

    def test_60_evidence_future_cannot_change_historical_result(self):
        s=frame();r=shadow_review(s)
        s["observations"].append(event("future", "future",day=1))
        v=shadow_review(s)
        self.assertEqual(codes(r),codes(v))
        self.assertIn("UNAVAILABLE_AT_DECISION",v["provenance"]["excluded_by_reason"])


    def test_61_baseline_does_not_keep_athlete_stop(self):
        x=frame("requested_stop")
        self.assertEqual("HOLD_FOR_REPORTED_STOP",compare_shadow([x])["cases"][0]["baseline_policy"])

    def test_62_baseline_does_not_keep_missing_evidence(self):
        x=frame("time_short",rows=[event(day=1)])
        self.assertEqual("ABSTAIN_UNKNOWN_EVIDENCE",compare_shadow([x])["cases"][0]["baseline_policy"])

    def test_63_missing_source_permission_is_not_approved_by_boolean(self):
        x=frame(registered=False)
        x["request"]["source_authorized"] = True
        self.assertEqual("ABSTAIN",shadow_review(x)["status"])

    def test_64_clinical_report_without_requested_reference_preempts(self):
        x=frame("none",rows=[event(),event("e2", "active", kind="clinical_context")])
        self.assertEqual("CLINICAL_BOUNDARY_DEFER",shadow_review(x)["status"])

    def test_65_no_engine_predictor_or_real_data_admission(self):
        output=shadow_review(frame())
        self.assertEqual("NOT_EVALUATED",output["source_admission"])
        self.assertIsNone(output["forecast"])
        self.assertEqual("NOT_ESTABLISHED",output["coaching_effectiveness"])
        self.assertIsNone(output["automatic_selection"])


def codes(output):
    return [o["code"] for o in output["options"]]

if __name__ == "__main__":
    unittest.main()
