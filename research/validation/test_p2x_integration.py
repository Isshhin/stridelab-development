"""P2-X invented-only adversarial cross-track integration contract tests."""
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
import unittest

from p2x_integration import audit, compare
from test_p2a_state import frame as a_frame, bench as a_bench, row as a_row
from test_p2c_wave import basic as c_basic
from test_p2d_shadow import frame as d_frame, event as d_event
from test_p2e_wave import snap as e_snap, session as e_session, add_strength


NOW = datetime(2026, 11, 8, 12, tzinfo=timezone.utc)


def at(hours=0):
    return (NOW + timedelta(hours=hours)).isoformat()


def fixture(opportunity="X-1"):
    a = a_frame(a_bench(1, 3.0, -6), a_bench(2, 3.1, -4),
                a_bench(3, 3.25, -2), a_row("fatigue-1", "fatigue_report", "moderate",
                                            days=0))
    a["athlete_id"] = "invented-A"
    a["synthetic_only"] = True
    for row in a["observations"]:
        row["athlete_id"] = "invented-A"
    c = c_basic()
    d = d_frame(opportunity=opportunity)
    d["plan"]["version"] = 7
    d["request"]["expected_plan_version"] = 7
    e = e_snap(athlete="invented-A", id=opportunity)
    e["plan"]["id"] = "P-1"
    e["plan"]["version"] = 7
    e["review_context"]["expected_plan_version"] = 7
    return {"synthetic_only": True, "athlete_id": "invented-A",
            "opportunity_id": opportunity, "as_of": at(),
            "p2a": a, "p2c": c, "p2d": d, "p2e": e}


class CrossTrackIntegrationTests(unittest.TestCase):
    def test_01_all_four_contracts_replay_without_promotion(self):
        v = audit(fixture())
        self.assertEqual("STRUCTURAL_COMPARISON_ONLY", v["review_status"])
        self.assertEqual("UNVERIFIED_NO_P2C_PLAN_ID", v["p2c_plan_lineage"])
        self.assertFalse(v["applied"])
        self.assertFalse(v["safe_to_auto_apply"])
        self.assertIsNone(v["plan_patch"])

    def test_02_a_state_remains_descriptive(self):
        v = audit(fixture())
        self.assertEqual("observed_rising", v["track_statuses"]["p2a"]["fitness_trend"])
        self.assertEqual("reported_moderate", v["track_statuses"]["p2a"]["recent_fatigue"])
        self.assertIsNone(v["predictive_skill"])

    def test_03_all_unvalidated_authority_boundaries(self):
        v = audit(fixture())
        self.assertEqual("NONE_SYNTHETIC_RESEARCH", v["coaching_authority"])
        self.assertEqual("CLOSED", v["gate2"])
        self.assertIsNone(v["selected_action"])
        self.assertIsNone(v["dose_change"])
        self.assertFalse(v["clinical_clearance"])

    def test_04_no_cross_track_evidence_merge(self):
        v = audit(fixture())
        self.assertIn("fatigue-1", v["source_evidence_ids"]["p2a"])
        self.assertIn("event-easy-1", v["source_evidence_ids"]["p2c"])
        self.assertIn("e1", v["source_evidence_ids"]["p2d"])
        self.assertEqual([], v["source_evidence_ids"]["p2e"])

    def test_05_preserves_input(self):
        b = fixture()
        before = deepcopy(b)
        audit(b)
        self.assertEqual(before, b)

    def test_06_replay_is_deterministic(self):
        b = fixture()
        self.assertEqual(audit(b), audit(deepcopy(b)))
        json.dumps(audit(b), sort_keys=True, allow_nan=False)

    def test_07_athlete_mismatch_in_a_fails(self):
        b = fixture(); b["p2a"]["athlete_id"] = "other"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_ATHLETE_MISMATCH"):
            audit(b)

    def test_08_athlete_mismatch_in_c_fails(self):
        b = fixture(); b["p2c"]["athlete_id"] = "other"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_ATHLETE_MISMATCH"):
            audit(b)

    def test_09_athlete_mismatch_in_d_fails(self):
        b = fixture(); b["p2d"]["athlete_id"] = "other"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_ATHLETE_MISMATCH"):
            audit(b)

    def test_10_athlete_mismatch_in_e_fails(self):
        b = fixture(); b["p2e"]["athlete_id"] = "other"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_ATHLETE_MISMATCH"):
            audit(b)

    def test_11_future_a_cutoff_fails(self):
        b = fixture(); b["p2a"]["as_of"] = at(24)
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_CUTOFF_MISMATCH"):
            audit(b)

    def test_12_c_cutoff_mismatch_fails(self):
        b = fixture(); b["p2c"]["as_of"] = at(1)
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_CUTOFF_MISMATCH"):
            audit(b)

    def test_13_d_cutoff_mismatch_fails(self):
        b = fixture(); b["p2d"]["as_of"] = at(-1)
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_CUTOFF_MISMATCH"):
            audit(b)

    def test_14_e_cutoff_mismatch_fails(self):
        b = fixture(); b["p2e"]["as_of"] = at(-1)
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_CUTOFF_MISMATCH"):
            audit(b)

    def test_15_equal_instant_different_offset_allowed(self):
        b = fixture()
        b["p2a"]["as_of"] = "2026-11-08T20:00:00+08:00"
        self.assertEqual("STRUCTURAL_COMPARISON_ONLY", audit(b)["review_status"])

    def test_16_naive_bundle_cutoff_rejected(self):
        b = fixture(); b["as_of"] = "2026-11-08T12:00:00"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_OFFSET_TIMESTAMP_REQUIRED"):
            audit(b)

    def test_17_d_e_plan_identity_mismatch_rejected(self):
        b = fixture(); b["p2e"]["plan"]["id"] = "other-plan"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_PLAN_ID_MISMATCH"):
            audit(b)

    def test_18_c_plan_version_mismatch_rejected(self):
        b = fixture(); b["p2c"]["plan_version"] = 6
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_PLAN_VERSION_MISMATCH"):
            audit(b)

    def test_19_d_plan_version_mismatch_rejected(self):
        b = fixture(); b["p2d"]["plan"]["version"] = 6
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_PLAN_VERSION_MISMATCH"):
            audit(b)

    def test_20_e_plan_version_mismatch_rejected(self):
        b = fixture(); b["p2e"]["plan"]["version"] = 6
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_PLAN_VERSION_MISMATCH"):
            audit(b)

    def test_21_d_opportunity_mismatch_rejected(self):
        b = fixture(); b["p2d"]["opportunity_id"] = "OTHER"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_OPPORTUNITY_MISMATCH"):
            audit(b)

    def test_22_e_opportunity_mismatch_rejected(self):
        b = fixture(); b["p2e"]["opportunity_id"] = "OTHER"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_OPPORTUNITY_MISMATCH"):
            audit(b)

    def test_23_false_synthetic_flag_rejected(self):
        b = fixture(); b["p2d"]["synthetic_only"] = False
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_SNAPSHOT_SYNTHETIC_REQUIRED"):
            audit(b)

    def test_24_top_level_synthetic_required(self):
        b = fixture(); b["synthetic_only"] = False
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_SYNTHETIC_ONLY"):
            audit(b)

    def test_25_nested_future_label_rejected(self):
        b = fixture(); b["p2a"]["observations"][0]["future_outcome"] = "success"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_OUTCOME_LEAKAGE"):
            audit(b)

    def test_26_top_level_label_rejected(self):
        b = fixture(); b["outcome"] = "improved"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_OUTCOME_LEAKAGE"):
            audit(b)

    def test_27_c_clinical_deferral_dominates_d_options(self):
        b = fixture(); b["p2c"]["review_context"]["restriction_status"] = "active"
        v = audit(b)
        self.assertEqual("DEFER_SAFETY_BOUNDARY", v["review_status"])
        self.assertIsNone(v["selected_action"])

    def test_28_d_clinical_deferral_dominates_c_maintain(self):
        b = fixture(); b["p2d"]["request"]["clinical_status"] = "unknown"
        self.assertEqual("DEFER_SAFETY_BOUNDARY", audit(b)["review_status"])

    def test_29_e_requested_stop_dominates_other_options(self):
        b = fixture(); b["p2e"]["review_context"]["stop_requested"] = True
        self.assertEqual("DEFER_SAFETY_BOUNDARY", audit(b)["review_status"])

    def test_30_d_reported_stop_dominates_e_calendar(self):
        b = fixture()
        b["p2d"]["request"]["reason"] = "requested_stop"
        b["p2d"]["observations"] = [d_event(value="requested_stop")]
        self.assertEqual("DEFER_SAFETY_BOUNDARY", audit(b)["review_status"])

    def test_31_c_missing_upload_does_not_clear_progress(self):
        b = fixture(); b["p2c"]["events"][-1]["available_at"] = at(24)
        v = audit(b)
        self.assertEqual("DEFER_MISSING_OR_CONFLICTING_EVIDENCE", v["review_status"])
        self.assertEqual("DEFER_RECONCILIATION", v["track_statuses"]["p2c"])

    def test_32_d_future_evidence_abstains_all(self):
        b = fixture(); b["p2d"]["observations"][0]["available_at"] = at(24)
        v = audit(b)
        self.assertEqual("DEFER_MISSING_OR_CONFLICTING_EVIDENCE", v["review_status"])
        self.assertEqual("ABSTAIN", v["track_statuses"]["p2d"])

    def test_33_e_capacity_conflict_no_edit(self):
        b = fixture()
        day = next(iter(b["p2e"]["review_context"]["available_minutes_by_date"]))
        b["p2e"]["review_context"]["available_minutes_by_date"][day] = 10
        v = audit(b)
        self.assertEqual("REVIEW_CAPACITY_CONFLICT", v["track_statuses"]["p2e"])
        self.assertIsNone(v["dose_change"])

    def test_34_e_lower_body_overlap_remains_review_only(self):
        b = fixture(); add_strength(b["p2e"])
        v = audit(b)
        self.assertEqual("REVIEW_CONCURRENT_CONSTRAINTS", v["review_status"])
        self.assertEqual("REVIEW_OBSERVED_OVERLAP", v["track_statuses"]["p2e"])
        self.assertIsNone(v["selected_action"])

    def test_35_c_historic_plan_id_injection_cannot_prove_lineage(self):
        b = fixture(); b["p2c"]["plan_id"] = "P-1"
        self.assertEqual("UNVERIFIED_NO_P2C_PLAN_ID", audit(b)["p2c_plan_lineage"])

    def test_36_reused_cross_track_event_ids_are_not_fused(self):
        b = fixture(); b["p2c"]["events"][0]["event_id"] = "e1"
        v = audit(b)
        self.assertIn("e1", v["source_evidence_ids"]["p2c"])
        self.assertIn("e1", v["source_evidence_ids"]["p2d"])
        self.assertEqual("NOT_AUTHENTICATED", v["cross_track_source_linkage"])

    def test_37_common_denominator_all_four_tracks(self):
        x = compare([fixture("A"), fixture("B")])
        self.assertEqual(2, x["same_denominator"])
        self.assertEqual(2, x["track_counts"]["p2a"])
        self.assertEqual(2, x["track_counts"]["p2e"])
        self.assertIsNone(x["best_track"])

    def test_38_duplicate_bundle_opportunity_rejected(self):
        b = fixture()
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_DUPLICATE_OPPORTUNITY"):
            compare([b, deepcopy(b)])

    def test_39_cohort_order_invariance(self):
        a, b = fixture("A"), fixture("B")
        self.assertEqual(compare([a, b]), compare([b, a]))

    def test_40_empty_comparison_no_model(self):
        x = compare([])
        self.assertEqual(0, x["same_denominator"])
        self.assertIsNone(x["training_effectiveness"])

    def test_41_wrong_bundle_collection_type_rejected(self):
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_BUNDLES_LIST_REQUIRED"):
            compare({})

    def test_42_unknown_a_fatigue_never_authorizes_training(self):
        b = fixture(); b["p2a"]["observations"][-1]["status"] = "missing_upload"
        v = audit(b)
        self.assertEqual("unknown", v["track_statuses"]["p2a"]["recent_fatigue"])
        self.assertFalse(v["safe_to_auto_apply"])

    def test_43_clinical_safety_takes_priority_over_overlap(self):
        b = fixture(); add_strength(b["p2e"])
        b["p2c"]["review_context"]["restriction_status"] = "unknown"
        self.assertEqual("DEFER_SAFETY_BOUNDARY", audit(b)["review_status"])

    def test_44_unverified_cross_track_lineage_never_promotes(self):
        b = fixture()
        b["p2c"]["plan_id"] = "P-1"
        x = audit(b)
        self.assertEqual("NOT_AUTHENTICATED", x["cross_track_source_linkage"])
        self.assertFalse(x["safe_to_auto_apply"])
        self.assertEqual("CLOSED", x["gate2"])


if __name__ == "__main__":
    unittest.main()
