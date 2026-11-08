"""Invented-only adversarial P2-C exposure and return-state contract tests."""
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
import math
import unittest

from p2c_exposure import summarize
from p2c_progression import review

NOW = datetime(2026, 11, 8, 12, tzinfo=timezone.utc)


def at(days):
    return (NOW + timedelta(days=days)).isoformat()


def add(s, key, kind="easy", day=-3, status="completed", *, km=5.0,
        minutes=40.0, epoch="current", reason=None, response="tolerated",
        next_day="tolerated"):
    s["opportunities"].append({"opportunity_id": key, "athlete_id": "invented-A",
        "plan_version": 7, "kind": kind, "source_id": "synthetic-plan-v7",
        "planned_at": at(day-1), "available_at": at(day-1)})
    event = {"event_id": "event-"+key, "athlete_id": "invented-A",
        "opportunity_id": key, "plan_version": 7, "kind": kind,
        "source_id": "synthetic-watch-"+key,
        "observed_at": at(day), "available_at": at(day), "status": status,
        "actual_km": km if status in ("completed", "partial") else None,
        "actual_minutes": minutes if status in ("completed", "partial") else None,
        "exposure_epoch": epoch,
        "response": response if status in ("completed", "partial") else "unknown",
        "next_day_response": next_day if status in ("completed", "partial") else "unknown"}
    if status == "confirmed_skipped":
        event["reason"] = reason or "logistical"
    s["events"].append(event)
    return event


def basic():
    s={"synthetic_only":True,"athlete_id":"invented-A", "plan_version":7,
       "as_of":at(0), "opportunities":[],"events":[],
       "review_context":{"expected_plan_version":7,"break_status":"none",
        "break_reason":"none","restriction_status":"none","intent":"maintain",
        "target_session":"easy","recent_response_event_ids":[]}}
    add(s,"easy-1",day=-5,km=5)
    add(s,"easy-2",day=-2,km=6)
    return s


def with_gate(s, target, epoch="current", dates=(-7,-3)):
    for index, day in enumerate(dates,1):
        add(s, f"{target}-{index}",kind=target,day=day,km=8,minutes=65,epoch=epoch)
    s["review_context"].update(intent="restore",target_session=target,
        current_exposure_epoch="current",
        registered_restoration_gates={target:{"status":"reviewed_synthetic",
            "review_protocol_id":"research-only-v1",
            "exposure_epoch":"current", "evidence_ids":[
                f"event-{target}-1",f"event-{target}-2"]}})
    return s


class ExposureBatch1(unittest.TestCase):
    def test_01_known_only_sum(self):
        x=summarize(basic())
        self.assertEqual(11,x["known_running_km"])
        self.assertEqual(80,x["known_running_minutes"])
        self.assertEqual(2,x["verified_run_count"])
    def test_02_no_activity_not_a_skipped_session(self):
        s=basic();s["events"].pop()
        x=summarize(s)
        self.assertEqual("unknown",x["roster"][-1]["status"])
        self.assertIsNone(x["roster"][-1]["actual_km"])
        self.assertFalse(x["coverage_complete"])
    def test_03_confirmed_skip_distinct(self):
        s=basic();add(s,"skip-1",status="confirmed_skipped")
        x=summarize(s)
        self.assertEqual(1,x["status_counts"]["confirmed_skipped"])
        self.assertEqual(11,x["known_running_km"])
    def test_04_partial_not_full(self):
        s=basic();add(s,"part-1",status="partial",km=2,minutes=20)
        x=summarize(s)
        self.assertEqual(1,x["status_counts"]["partial"])
        self.assertEqual(13,x["known_running_km"])
    def test_05_zero_recorded_not_unknown(self):
        s=basic();add(s,"zero",km=0,minutes=0)
        self.assertEqual("completed",summarize(s)["roster"][-1]["status"])
    def test_06_unknown_event_has_no_numeric_dose(self):
        s=basic();add(s,"unknown",status="unknown")
        x=summarize(s)
        self.assertEqual("unknown",x["roster"][-1]["status"])
        self.assertFalse(x["coverage_complete"])
    def test_07_future_event_no_lookahead(self):
        s=basic();future=deepcopy(s["events"][0]);future["event_id"]="future"
        future["observed_at"]=at(1);future["available_at"]=at(1);future["opportunity_id"]=None
        s["events"].append(future)
        x=summarize(s)
        self.assertEqual(11,x["known_running_km"])
        self.assertEqual(1,x["excluded_by_reason"]["EVENT_UNAVAILABLE"])
        self.assertTrue(x["coverage_complete"])
    def test_08_late_uploaded_existing_event_unknown(self):
        s=basic();s["events"][-1]["available_at"]=at(1)
        x=summarize(s)
        self.assertEqual(5,x["known_running_km"])
        self.assertFalse(x["coverage_complete"])
    def test_09_unlinked_event_not_double_counted(self):
        s=basic();other=deepcopy(s["events"][0]);other["event_id"]="event-unlinked";other["opportunity_id"]=None
        s["events"].append(other)
        x=summarize(s)
        self.assertEqual(11,x["known_running_km"])
        self.assertFalse(x["coverage_complete"])
        self.assertEqual(["event-unlinked"],x["unlinked_event_ids"])
    def test_10_duplicate_import_exactly_once(self):
        s=basic();s["events"].append(deepcopy(s["events"][0]))
        x=summarize(s);self.assertEqual(1,x["exact_duplicate_imports"]);self.assertEqual(11,x["known_running_km"])
    def test_11_conflicting_duplicate_id_rejected(self):
        s=basic();e=deepcopy(s["events"][0]);e["actual_km"]=999;s["events"].append(e)
        with self.assertRaisesRegex(ValueError,"CONFLICTING_EVENT_ID"):summarize(s)
    def test_12_two_distinct_sources_same_opportunity_conflict(self):
        s=basic();e=deepcopy(s["events"][0]);e["event_id"]="alias";s["events"].append(e)
        x=summarize(s);self.assertEqual(6,x["known_running_km"])
        self.assertFalse(x["coverage_complete"])
    def test_13_cross_athlete_event_rejected(self):
        s=basic();s["events"][0]["athlete_id"]="invented-B"
        with self.assertRaisesRegex(ValueError,"CROSS_ATHLETE"):summarize(s)
    def test_14_cross_athlete_plan_rejected(self):
        s=basic();s["opportunities"][0]["athlete_id"]="invented-B"
        with self.assertRaisesRegex(ValueError,"CROSS_ATHLETE"):summarize(s)
    def test_15_wrong_source_kind_conflicts(self):
        s=basic();s["events"][0]["kind"]="intervals"
        x=summarize(s);self.assertEqual("conflict",x["roster"][0]["status"])
    def test_16_stale_event_version_rejected(self):
        s=basic();s["events"][0]["plan_version"]=6
        with self.assertRaisesRegex(ValueError,"PLAN_VERSION"):summarize(s)
    def test_17_stale_plan_version_rejected(self):
        s=basic();s["opportunities"][0]["plan_version"]=6
        with self.assertRaisesRegex(ValueError,"PLAN_VERSION"):summarize(s)
    def test_18_invalid_naive_time_rejected(self):
        s=basic();s["events"][0]["observed_at"]="2026-11-07T12:00:00"
        with self.assertRaisesRegex(ValueError,"OFFSET_TIME_REQUIRED"):summarize(s)
    def test_19_available_before_observed_rejected(self):
        s=basic();s["events"][0]["available_at"]=at(-6)
        with self.assertRaisesRegex(ValueError,"INCONSISTENT_AVAILABILITY"):summarize(s)
    def test_20_nonnumeric_dose_rejected(self):
        s=basic();s["events"][0]["actual_km"]="5"
        with self.assertRaisesRegex(ValueError,"ACTUAL_DOSE_REQUIRED"):summarize(s)
    def test_21_nan_dose_rejected(self):
        s=basic();s["events"][0]["actual_km"]=float("nan")
        with self.assertRaisesRegex(ValueError,"ACTUAL_DOSE_REQUIRED"):summarize(s)
    def test_22_negative_dose_rejected(self):
        s=basic();s["events"][0]["actual_minutes"]=-1
        with self.assertRaisesRegex(ValueError,"ACTUAL_DOSE_REQUIRED"):summarize(s)
    def test_23_skip_with_dose_rejected(self):
        s=basic();add(s,"bad-skip",status="confirmed_skipped");s["events"][-1]["actual_km"]=1
        with self.assertRaisesRegex(ValueError,"DOSE_MUST_BE_NULL"):summarize(s)
    def test_24_skip_without_reason_rejected(self):
        s=basic();add(s,"skip",status="confirmed_skipped");del s["events"][-1]["reason"]
        with self.assertRaisesRegex(ValueError,"CONFIRMED_SKIP_REASON_REQUIRED"):summarize(s)
    def test_25_unknown_with_response_rejected(self):
        s=basic();add(s,"unknown",status="unknown");s["events"][-1]["response"]="tolerated"
        with self.assertRaisesRegex(ValueError,"RESPONSE_WITHOUT_PERFORMED_EXPOSURE"):summarize(s)
    def test_26_no_privacy_data_without_synthetic_flag(self):
        s=basic();del s["synthetic_only"]
        with self.assertRaisesRegex(ValueError,"SYNTHETIC_ONLY_REQUIRED"):summarize(s)
    def test_27_duplicate_plan_id_rejected(self):
        s=basic();s["opportunities"].append(deepcopy(s["opportunities"][0]))
        with self.assertRaisesRegex(ValueError,"DUPLICATE_OPPORTUNITY_ID"):summarize(s)
    def test_28_future_plan_does_not_change_known_exposure(self):
        s=basic();add(s,"future",day=2)
        x=summarize(s);self.assertEqual(11,x["known_running_km"]);self.assertEqual(2,x["planned_count"])
    def test_29_order_and_nonmutation(self):
        s=basic();original=deepcopy(s);r1=summarize(s)
        s["opportunities"].reverse();s["events"].reverse();r2=summarize(s)
        self.assertEqual(r1,r2);self.assertEqual(original["events"][0]["actual_km"],5)
        json.dumps(r1,sort_keys=True,allow_nan=False)
    def test_30_unsupported_discipline_cannot_sneak_in(self):
        s=basic();s["events"][0]["kind"]="strength"
        with self.assertRaisesRegex(ValueError,"UNSUPPORTED_SESSION_KIND"):summarize(s)

class ProgressionBatches23(unittest.TestCase):
    def test_31_default_maintain_no_plan_change(self):
        x=review(basic());self.assertEqual("MAINTAIN_REVIEW",x["review_category"])
        self.assertIsNone(x["dose_change"]);self.assertFalse(x["applied"])
    def test_32_clinical_active_restriction_takes_precedence(self):
        s=basic();s["review_context"].update(restriction_status="active",intent="progress")
        self.assertEqual("DEFER_CLINICAL_REVIEW",review(s)["review_category"])
    def test_33_unknown_restriction_takes_precedence(self):
        s=basic();s["review_context"].update(restriction_status="unknown")
        self.assertEqual("DEFER_CLINICAL_REVIEW",review(s)["review_category"])
    def test_34_illness_even_with_reported_clearance(self):
        s=basic();s["review_context"].update(break_status="confirmed_absence",break_reason="illness",restriction_status="reported_cleared")
        self.assertEqual("DEFER_CLINICAL_REVIEW",review(s)["review_category"])
    def test_35_injury_no_automatic_easy_permission(self):
        s=basic();s["review_context"].update(break_status="confirmed_absence",break_reason="injury")
        x=review(s);self.assertEqual("DEFER_CLINICAL_REVIEW",x["review_category"]);self.assertFalse(x["clinical_clearance"])
    def test_36_unknown_gap_stays_unknown(self):
        s=basic();s["review_context"].update(break_status="unverified_gap",break_reason="unknown")
        self.assertEqual("DEFER_RECONCILIATION",review(s)["review_category"])
    def test_37_confirmed_logistical_break_not_inferred_from_file_absence(self):
        s=basic();s["review_context"].update(break_status="confirmed_absence",break_reason="logistical")
        self.assertEqual("REBUILD_EXPOSURE_REVIEW",review(s)["review_category"])
    def test_38_planned_rest_not_automatically_detraining(self):
        s=basic();s["review_context"].update(break_status="confirmed_absence",break_reason="planned_rest")
        self.assertEqual("MAINTAIN_REVIEW",review(s)["review_category"])
    def test_39_race_not_ordinary_logistical_break(self):
        s=basic();s["review_context"].update(break_status="confirmed_absence",break_reason="race")
        self.assertEqual("RACE_RECOVERY_REVIEW",review(s)["review_category"])
    def test_40_missing_activity_blocks_progress(self):
        s=basic();s["events"].pop();s["review_context"]["intent"]="progress"
        self.assertEqual("DEFER_RECONCILIATION",review(s)["review_category"])
    def test_41_recent_difficult_response_review(self):
        s=basic();s["events"][-1]["response"]="difficult";s["review_context"]["recent_response_event_ids"]=["event-easy-2"]
        self.assertEqual("REDUCE_OR_DELOAD_REVIEW",review(s)["review_category"])
    def test_42_old_difficult_response_not_automatically_current(self):
        s=basic();s["events"][0]["response"]="difficult"
        self.assertEqual("MAINTAIN_REVIEW",review(s)["review_category"])
    def test_43_unknown_followup_never_unlocks_progress(self):
        s=basic();s["review_context"].update(intent="progress",recent_response_event_ids=["event-easy-2"])
        s["events"][-1]["next_day_response"]="unknown"
        self.assertEqual("DEFER_FOLLOWUP_UNVERIFIED",review(s)["review_category"])
    def test_44_verified_followup_only_review_not_numeric_dose(self):
        s=basic();s["review_context"].update(intent="progress",recent_response_event_ids=["event-easy-2"])
        x=review(s);self.assertEqual("PROGRESS_ONE_DIMENSION_REVIEW",x["review_category"])
        self.assertIsNone(x["dose_change"]);self.assertIsNone(x["risk_probability"])
    def test_45_unknown_recent_event_invalid(self):
        s=basic();s["review_context"]["recent_response_event_ids"]=["not-real"]
        with self.assertRaisesRegex(ValueError,"RECENT_RESPONSE_NOT_VERIFIED"):review(s)
    def test_46_old_plan_version_request_rejected(self):
        s=basic();s["review_context"]["expected_plan_version"]=6
        with self.assertRaisesRegex(ValueError,"STALE_PLAN_VERSION"):review(s)
    def test_47_no_long_gate_defer(self):
        s=basic();s["review_context"].update(intent="restore",target_session="long_run")
        self.assertEqual("DEFER_SESSION_TYPE",review(s)["review_category"])
    def test_48_long_gate_reviewed_not_automatically_applied(self):
        s=with_gate(basic(),"long_run")
        x=review(s);self.assertEqual("RESTORE_LONG_RUN_REVIEW",x["review_category"])
        self.assertFalse(x["applied"]);self.assertIsNone(x["plan_change"])
    def test_49_threshold_gate_separate_from_intervals(self):
        s=with_gate(basic(),"threshold")
        self.assertEqual("RESTORE_QUALITY_REVIEW",review(s)["review_category"])
        s["review_context"]["target_session"]="intervals"
        self.assertEqual("DEFER_SESSION_TYPE",review(s)["review_category"])
    def test_50_old_epoch_cannot_unlock_quality(self):
        s=with_gate(basic(),"threshold",epoch="old")
        self.assertEqual("DEFER_SESSION_TYPE",review(s)["review_category"])
    def test_51_unreviewed_gate_cannot_unlock(self):
        s=with_gate(basic(),"long_run")
        s["review_context"]["registered_restoration_gates"]["long_run"]["status"]="unverified"
        self.assertEqual("DEFER_SESSION_TYPE",review(s)["review_category"])
    def test_52_mismatched_epoch_gate_cannot_unlock(self):
        s=with_gate(basic(),"long_run")
        s["review_context"]["current_exposure_epoch"]="other"
        self.assertEqual("DEFER_SESSION_TYPE",review(s)["review_category"])
    def test_53_same_day_not_repeated_independent_days(self):
        s=with_gate(basic(),"long_run",dates=(-4,-4))
        self.assertEqual("DEFER_SESSION_TYPE",review(s)["review_category"])
    def test_54_difficult_followup_does_not_unlock(self):
        s=with_gate(basic(),"long_run");s["events"][-1]["next_day_response"]="difficult"
        self.assertEqual("DEFER_SESSION_TYPE",review(s)["review_category"])
    def test_55_changed_kind_not_gate(self):
        s=with_gate(basic(),"long_run");s["events"][-1]["kind"]="threshold"
        self.assertEqual("DEFER_RECONCILIATION",review(s)["review_category"])
    def test_56_weekly_total_can_hide_single_session_concentration(self):
        a=basic();b=basic();a["events"][0]["actual_km"]=8;a["events"][1]["actual_km"]=2
        b["events"][0]["actual_km"]=5;b["events"][1]["actual_km"]=5
        x=review(a);y=review(b)
        self.assertEqual(x["exposure"]["known_running_km"],y["exposure"]["known_running_km"])
        self.assertNotEqual(x["session_concentration"]["max_verified_run_km"],y["session_concentration"]["max_verified_run_km"])
        self.assertIsNone(x["risk_probability"])
    def test_57_future_extra_record_does_not_change_category(self):
        s=basic();future=deepcopy(s["events"][0]);future.update(event_id="future-extra",opportunity_id=None,observed_at=at(2),available_at=at(2))
        before=review(s)["review_category"];s["events"].append(future)
        self.assertEqual(before,review(s)["review_category"])
    def test_58_no_automatic_training_change_or_medical_clearance(self):
        s=with_gate(basic(),"intervals")
        x=review(s)
        self.assertTrue(x["requires_separate_approval"])
        self.assertEqual("NONE_SYNTHETIC_RESEARCH",x["coaching_authority"])
        self.assertEqual("CLOSED",x["gate2"])
        self.assertIsNone(x["dose_change"])
        self.assertIsNone(x["plan_change"])
        self.assertFalse(x["clinical_clearance"])
        json.dumps(x,allow_nan=False,sort_keys=True)
    def test_59_input_immutable_permutation_determinism(self):
        s=basic();b=deepcopy(s);x=review(s)
        s["events"].reverse();s["opportunities"].reverse()
        self.assertEqual(x,review(s));self.assertEqual(b["events"][0]["actual_km"],5)
    def test_60_skip_cannot_trigger_invented_makeup_dose(self):
        s=basic();add(s,"skip-1",status="confirmed_skipped")
        x=review(s)
        self.assertEqual("MAINTAIN_REVIEW",x["review_category"])
        self.assertIsNone(x["dose_change"])

class FinalBoundaries(unittest.TestCase):
    def test_61_event_cannot_precede_plan_available(self):
        s=basic();s["events"][0]["observed_at"]=at(-10)
        s["events"][0]["available_at"]=at(-10)
        x=summarize(s)
        self.assertFalse(x["coverage_complete"])
        self.assertEqual(1,x["excluded_by_reason"]["EVENT_PRECEDES_PLAN_AVAILABILITY"])
    def test_62_clinical_restriction_overrides_missing_data(self):
        s=basic();s["events"].pop()
        s["review_context"]["restriction_status"]="active"
        self.assertEqual("DEFER_CLINICAL_REVIEW",review(s)["review_category"])
    def test_63_invalid_context_is_rejected(self):
        s=basic();s["review_context"]["break_status"]="made_up"
        with self.assertRaisesRegex(ValueError,"INVALID_REVIEW_CONTEXT"):review(s)
    def test_64_break_reason_without_break_rejected(self):
        s=basic();s["review_context"]["break_reason"]="travel"
        with self.assertRaisesRegex(ValueError,"BREAK_REASON_WITHOUT_BREAK"):review(s)
    def test_65_break_requires_reason(self):
        s=basic();s["review_context"].update(break_status="confirmed_absence",break_reason="none")
        with self.assertRaisesRegex(ValueError,"BREAK_REASON_REQUIRED"):review(s)
    def test_66_duplicate_recent_response_ids_rejected(self):
        s=basic();s["review_context"]["recent_response_event_ids"]=["event-easy-2"]*2
        with self.assertRaisesRegex(ValueError,"RECENT_RESPONSE_IDS_INVALID"):review(s)
    def test_67_malformed_status_fails_closed(self):
        s=basic();s["events"][0]["status"]={"bad":"object"}
        with self.assertRaisesRegex(ValueError,"INVALID_ACTIVITY_STATUS"):summarize(s)
    def test_68_malformed_kind_fails_closed(self):
        s=basic();s["events"][0]["kind"]=["easy"]
        with self.assertRaisesRegex(ValueError,"UNSUPPORTED_SESSION_KIND"):summarize(s)
    def test_69_malformed_response_fails_closed(self):
        s=basic();s["events"][0]["response"]={"bad":"object"}
        with self.assertRaisesRegex(ValueError,"INVALID_RESPONSE"):summarize(s)
    def test_70_denial_with_nonboolean_synthetic_flag(self):
        s=basic();s["synthetic_only"]="true"
        with self.assertRaisesRegex(ValueError,"SYNTHETIC_ONLY_REQUIRED"):review(s)


if __name__ == "__main__":
    unittest.main()
