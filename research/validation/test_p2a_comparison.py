"""Deterministic, invented-only P2-A feature-comparison adversarial tests."""
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
import unittest

from p2a_comparison import compare

NOW = datetime(2026, 11, 8, 12, tzinfo=timezone.utc)


def stamp(days=0, hours=0):
    return (NOW + timedelta(days=days, hours=hours)).isoformat()


def observation(name, kind, value, *, days=-1, hours=0, **fields):
    result = {
        "event_id": name, "athlete_id": "invented-A", "kind": kind,
        "source_id": "invented-" + name,
        "protocol_id": "run-v1", "observed_at": stamp(days, hours),
        "available_at": stamp(days, hours),
        "status": "measured", "quality": "good", "value": value,
    }
    result.update(fields)
    return result


def benchmarks():
    return [observation("b1", "benchmark", 3.00, days=-6,
                        comparison_group="loop-1", device_epoch="watch-1"),
            observation("b2", "benchmark", 3.10, days=-4,
                        comparison_group="loop-1", device_epoch="watch-1"),
            observation("b3", "benchmark", 3.21, days=-2,
                        comparison_group="loop-1", device_epoch="watch-1")]


def checkin():
    return observation("f1", "fatigue_report", "high", days=0, hours=-1)


def wearable(kind="hrv", **changes):
    result = observation("w-"+kind, kind, 64, days=0, hours=-2,
                         protocol_id="night-v1", device_epoch="band-1",
                         baseline_id="night-baseline-1", lineage_root_id="night-1")
    result.update(changes)
    return result


def sample(*, opportunity="invented-001", history=True, fatigue=False, wearables=()):
    rows = benchmarks() if history else []
    if fatigue:
        rows.append(checkin())
    rows.extend(wearables)
    return {
        "athlete_id": "invented-A", "opportunity_id": opportunity,
        "as_of": stamp(), "observations": rows,
        "registered_benchmarks": {"loop-1": {
            "device_epoch": "watch-1", "protocol_id": "run-v1",
            "minimum_detectable_change_mps": 0.10}},
        "registered_sessions": {},
        "fatigue_freshness_hours": 24,
        "registered_wearables": {"hrv": {
            "device_epoch": "band-1", "protocol_id": "night-v1",
            "baseline_id": "night-baseline-1", "status": "qualified",
            "max_age_hours": 24}, "sleep": {
            "device_epoch": "band-1", "protocol_id": "night-v1",
            "baseline_id": "night-baseline-1", "status": "qualified",
            "max_age_hours": 24}},
    }


def decisions(payload):
    return payload["cases"][0]["candidate_states"]


class ComparisonWaveTests(unittest.TestCase):
    def test_01_no_inputs_are_not_predictions(self):
        value = compare([sample(history=False)])
        self.assertEqual(3, len(decisions(value)))
        self.assertTrue(all(v["status"] == "ABSTAIN" for v in decisions(value).values()))
        self.assertIsNone(value["predictive_skill"])
        self.assertIsNone(value["best_candidate"])

    def test_02_history_only(self):
        output = decisions(compare([sample()]))
        self.assertEqual("FEATURES_READY", output["A_HISTORY"]["status"])
        self.assertEqual("CHECKIN_UNAVAILABLE", output["B_HISTORY_CHECKIN"]["reason"])
        self.assertEqual("CHECKIN_UNAVAILABLE", output["C_HISTORY_CHECKIN_WEARABLE"]["reason"])

    def test_03_checkin_without_wearable(self):
        output = decisions(compare([sample(fatigue=True)]))
        self.assertEqual("FEATURES_READY", output["B_HISTORY_CHECKIN"]["status"])
        self.assertEqual("WEARABLE_CONTEXT_UNQUALIFIED", output["C_HISTORY_CHECKIN_WEARABLE"]["reason"])

    def test_04_all_three_have_features_not_skill(self):
        output = compare([sample(fatigue=True, wearables=[wearable()])])
        self.assertEqual(1, output["coverage"]["C_HISTORY_CHECKIN_WEARABLE"]["features_ready"])
        self.assertIsNone(output["predictive_skill"])
        self.assertEqual("CLOSED", output["gate2"])

    def test_05_same_denominator_even_when_abstaining(self):
        rows = [sample(opportunity="a"), sample(opportunity="b", fatigue=True),
                sample(opportunity="c", fatigue=True, wearables=[wearable()])]
        o = compare(rows)
        self.assertEqual(3, o["opportunity_count"])
        self.assertTrue(all(v["same_denominator"] == 3 for v in o["coverage"].values()))
        self.assertEqual(3, o["coverage"]["A_HISTORY"]["features_ready"])
        self.assertEqual(2, o["coverage"]["B_HISTORY_CHECKIN"]["features_ready"])
        self.assertEqual(1, o["coverage"]["C_HISTORY_CHECKIN_WEARABLE"]["features_ready"])

    def test_06_reordering_deterministic(self):
        rows = [sample(opportunity="b", fatigue=True), sample(opportunity="a")]
        before = deepcopy(rows)
        a = compare(rows)
        b = compare(list(reversed(rows)))
        self.assertEqual(a, b)
        self.assertEqual(rows, before)
        json.dumps(a, sort_keys=True, allow_nan=False)

    def test_07_duplicate_opportunity_rejected(self):
        a = sample()
        with self.assertRaisesRegex(ValueError, "DUPLICATE_OPPORTUNITY"):
            compare([a, deepcopy(a)])

    def test_08_same_id_different_athlete_allowed(self):
        a = sample()
        b = deepcopy(a)
        b["athlete_id"] = "invented-B"
        for r in b["observations"]:
            r["athlete_id"] = "invented-B"
        self.assertEqual(2, compare([a, b])["opportunity_count"])

    def test_09_same_event_exact_duplicate_no_inflation(self):
        s = sample(fatigue=True, wearables=[wearable()])
        s["observations"].append(deepcopy(s["observations"][-1]))
        self.assertEqual(["w-hrv"], compare([s])["cases"][0]["wearable"]["evidence_ids"])

    def test_10_same_event_conflict_fails(self):
        s = sample()
        s["observations"].append(deepcopy(s["observations"][0]))
        s["observations"][-1]["value"] = 999
        with self.assertRaisesRegex(ValueError, "CONFLICTING_EVENT_ID"):
            compare([s])

    def test_11_cross_athlete_fails(self):
        s = sample()
        s["observations"][0]["athlete_id"] = "invented-B"
        with self.assertRaisesRegex(ValueError, "CROSS_ATHLETE_OBSERVATION"):
            compare([s])

    def test_12_future_checkin_does_not_enter_B(self):
        s = sample(fatigue=True)
        s["observations"][-1]["available_at"] = stamp(days=1)
        self.assertEqual("ABSTAIN", decisions(compare([s]))["B_HISTORY_CHECKIN"]["status"])

    def test_13_future_wearable_does_not_enter_C(self):
        s = sample(fatigue=True, wearables=[wearable(available_at=stamp(days=1))])
        self.assertEqual("ABSTAIN", decisions(compare([s]))["C_HISTORY_CHECKIN_WEARABLE"]["status"])

    def test_14_future_observation_wearable_does_not_enter_C(self):
        s = sample(fatigue=True, wearables=[wearable(observed_at=stamp(days=1))])
        self.assertEqual("ABSTAIN", decisions(compare([s]))["C_HISTORY_CHECKIN_WEARABLE"]["status"])

    def test_15_stale_wearable_excluded(self):
        s = sample(fatigue=True, wearables=[wearable(observed_at=stamp(days=-5), available_at=stamp(days=-5))])
        self.assertEqual("STALE_WEARABLE", next(iter(compare([s])["cases"][0]["wearable"]["excluded_by_reason"])))

    def test_16_new_device_requires_bridge(self):
        s = sample(fatigue=True, wearables=[wearable(device_epoch="band-2")])
        self.assertEqual("ABSTAIN", decisions(compare([s]))["C_HISTORY_CHECKIN_WEARABLE"]["status"])

    def test_17_new_protocol_requires_new_reference(self):
        s = sample(fatigue=True, wearables=[wearable(protocol_id="night-v2")])
        self.assertEqual("ABSTAIN", decisions(compare([s]))["C_HISTORY_CHECKIN_WEARABLE"]["status"])

    def test_18_baseline_change_requires_registration(self):
        s = sample(fatigue=True, wearables=[wearable(baseline_id="baseline-new")])
        self.assertEqual("ABSTAIN", decisions(compare([s]))["C_HISTORY_CHECKIN_WEARABLE"]["status"])

    def test_19_missing_lineage_cannot_count(self):
        s = sample(fatigue=True, wearables=[wearable(lineage_root_id=None)])
        self.assertEqual("MISSING_LINEAGE_ROOT", next(iter(compare([s])["cases"][0]["wearable"]["excluded_by_reason"])))

    def test_20_correlated_sleep_hrv_do_not_count_as_two_votes(self):
        s = sample(fatigue=True, wearables=[wearable(), wearable("sleep")])
        w = compare([s])["cases"][0]["wearable"]
        self.assertEqual(["night-1"], w["correlated_lineages"])
        self.assertEqual(["hrv", "sleep"], w["families"])
        self.assertEqual(["night-1"], w["lineage_roots"])

    def test_21_bad_measurement_is_excluded(self):
        s = sample(fatigue=True, wearables=[wearable(value=True)])
        self.assertEqual("INVALID_WEARABLE_VALUE", next(iter(compare([s])["cases"][0]["wearable"]["excluded_by_reason"])))

    def test_22_suspect_quality_with_unhashable_object_does_not_crash(self):
        s = sample(fatigue=True, wearables=[wearable(quality={"bad": "object"})])
        self.assertEqual("ABSTAIN", decisions(compare([s]))["C_HISTORY_CHECKIN_WEARABLE"]["status"])

    def test_23_unregistered_freshness_is_not_defaulted(self):
        s = sample(fatigue=True, wearables=[wearable()])
        del s["registered_wearables"]["hrv"]["max_age_hours"]
        self.assertEqual("ABSTAIN", decisions(compare([s]))["C_HISTORY_CHECKIN_WEARABLE"]["status"])

    def test_24_unqualified_reference_is_not_valid(self):
        s = sample(fatigue=True, wearables=[wearable()])
        s["registered_wearables"]["hrv"]["status"] = "unverified"
        self.assertEqual("ABSTAIN", decisions(compare([s]))["C_HISTORY_CHECKIN_WEARABLE"]["status"])

    def test_25_tampered_naive_time_fails_closed(self):
        s = sample(fatigue=True, wearables=[wearable()])
        s["observations"][-1]["observed_at"] = "2026-11-08T10:00:00"
        with self.assertRaisesRegex(ValueError, "INVALID_OFFSET_TIMESTAMP"):
            compare([s])

    def test_26_nested_outcome_label_forbidden(self):
        s = sample()
        s["observations"][0]["future_outcome"] = "fast"
        with self.assertRaisesRegex(ValueError, "FUTURE_OR_OUTCOME_LABEL_PRESENT"):
            compare([s])

    def test_27_top_level_target_forbidden(self):
        s = sample()
        s["next_session_result"] = "met_intent"
        with self.assertRaisesRegex(ValueError, "FUTURE_OR_OUTCOME_LABEL_PRESENT"):
            compare([s])

    def test_28_no_b_or_c_if_history_unknown(self):
        s = sample(history=False, fatigue=True, wearables=[wearable()])
        self.assertEqual({"ABSTAIN"}, {x["status"] for x in decisions(compare([s])).values()})

    def test_29_no_outcome_fit_or_model_comparison(self):
        value = compare([sample(fatigue=True, wearables=[wearable()])])
        self.assertEqual("SYNTHETIC_FEATURE_ELIGIBILITY_ONLY", value["status"])
        self.assertEqual("NONE_SYNTHETIC_RESEARCH", value["coaching_authority"])
        self.assertNotIn("accuracy", value)
        self.assertNotIn("readiness_score", value)

    def test_30_empty_cohort_is_not_successful_model(self):
        out = compare([])
        self.assertEqual(0, out["opportunity_count"])
        self.assertEqual(0, out["coverage"]["A_HISTORY"]["same_denominator"])
        self.assertIsNone(out["best_candidate"])

    def test_31_invalid_registration_shape_rejected(self):
        s = sample(fatigue=True, wearables=[wearable()])
        s["registered_wearables"] = ["bad"]
        with self.assertRaisesRegex(ValueError, "WEARABLE_REGISTER_NOT_MAP"):
            compare([s])

    def test_32_invalid_snapshot_collection_rejected(self):
        with self.assertRaisesRegex(ValueError, "SNAPSHOTS_LIST_REQUIRED"):
            compare({})

    def test_33_missing_opportunity_id_rejected(self):
        s = sample()
        del s["opportunity_id"]
        with self.assertRaisesRegex(ValueError, "OPPORTUNITY_ID_REQUIRED"):
            compare([s])

    def test_34_missing_fatigue_does_not_become_low_fatigue(self):
        s = sample(fatigue=True, wearables=[wearable()])
        s["observations"][3]["status"] = "missing_upload"
        self.assertEqual("ABSTAIN", decisions(compare([s]))["B_HISTORY_CHECKIN"]["status"])

    def test_35_unknown_wearable_family_not_accepted(self):
        s = sample(fatigue=True, wearables=[wearable()])
        s["observations"][-1]["kind"] = "unknown_sensor"
        self.assertEqual("ABSTAIN", decisions(compare([s]))["C_HISTORY_CHECKIN_WEARABLE"]["status"])


    def test_36_favorable_wearable_does_not_override_high_fatigue(self):
        s = sample(fatigue=True, wearables=[wearable(assessment="favorable")])
        out = compare([s])
        self.assertEqual("FEATURES_READY", decisions(out)["C_HISTORY_CHECKIN_WEARABLE"]["status"])
        self.assertEqual(["f1"], out["cases"][0]["checkin_evidence_ids"])
        self.assertIsNone(out["predictive_skill"])

    def test_37_nan_wearable_excluded_from_serializable_output(self):
        s = sample(fatigue=True, wearables=[wearable(value=float("nan"))])
        value = compare([s])
        self.assertEqual("ABSTAIN", decisions(value)["C_HISTORY_CHECKIN_WEARABLE"]["status"])
        json.dumps(value, sort_keys=True, allow_nan=False)

    def test_38_only_future_benchmark_abstains(self):
        s = sample(fatigue=True, wearables=[wearable()])
        for r in s["observations"][:3]:
            r["available_at"] = stamp(days=1)
        out = compare([s])
        self.assertTrue(all(v["status"] == "ABSTAIN" for v in decisions(out).values()))

    def test_39_quality_session_history_is_usable_only_when_registered(self):
        s = sample(history=False, fatigue=True)
        for n, d in ((1, -5), (2, -3)):
            s["observations"].append(observation("q"+str(n), "quality_session",
                           "met_intent", days=d, comparison_group="quality-a",
                           device_epoch="watch-1"))
        self.assertEqual("ABSTAIN", decisions(compare([s]))["A_HISTORY"]["status"])
        s["registered_sessions"] = {"quality-a": {
            "protocol_id": "run-v1", "device_epoch": "watch-1"}}
        self.assertEqual("FEATURES_READY", decisions(compare([s]))["A_HISTORY"]["status"])

    def test_40_fuzzed_permutations_preserve_all_candidate_counts(self):
        base = sample(opportunity="invented-fuzz", fatigue=True,
                      wearables=[wearable(), wearable("sleep")])
        reference = compare([base])
        for offset in range(len(base["observations"])):
            other = deepcopy(base)
            other["observations"] = (other["observations"][offset:]
                                      + other["observations"][:offset])
            self.assertEqual(reference, compare([other]))

if __name__ == "__main__":
    unittest.main()
