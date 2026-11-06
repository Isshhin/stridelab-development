"""Adversarial checks for the additive F2.2 synthetic reference."""
from datetime import datetime, timedelta, timezone
import unittest

from p2f_foundation import FoundationLedger, Quarantine, validate_trace_segment


BASE = datetime(2026, 11, 1, tzinfo=timezone.utc)


def ts(minutes):
    return (BASE + timedelta(minutes=minutes)).isoformat()


def event(event_id, account, kind, minute, **data):
    return {"id": event_id, "account_id": account, "kind": kind,
            "recorded_at": ts(minute), "effective_at": ts(minute),
            "available_at": ts(minute), "data": data}


def measured(value, unit, method="native"):
    return {"status": "measured", "value": value, "unit": unit,
            "method": method}


def missing(status="not_collected", unit="bpm"):
    return {"status": status, "value": None, "unit": unit, "method": None}


def planned(account="A"):
    return [
        event("r1", account, "plan_revision", 0, opportunity_id="o1",
              revision_id="r1", parent_revision_id=None, intent="easy_aerobic",
              planned_duration=measured(1800, "s")),
        event("d1", account, "delivery", 1, opportunity_id="o1", revision_id="r1"),
    ]


class FoundationReplayTests(unittest.TestCase):
    def test_garmin_and_fit_aliases_count_one_bout_only_with_adjudication(self):
        ledger = FoundationLedger()
        rows = planned() + [
            event("a1", "A", "activity", 40, activity_id="a1", started_at=ts(10),
                  ended_at=ts(40), discipline="running", intent="easy_aerobic",
                  plan_ownership="planned_by_stridelab", completion="completed"),
            event("s-garmin", "A", "source", 45, source_id="sg", activity_id="a1",
                  alias=["garmin", "activity-7"], digest="a" * 64,
                  measurements={"distance": measured(5000, "m")}),
            event("s-fit", "A", "source", 46, source_id="sf", activity_id="a1",
                  alias=["fit", "file-9"], digest="b" * 64,
                  measurements={"distance": measured(5000, "m")},
                  alias_evidence_id="review-1"),
            event("l1", "A", "link", 47, activity_id="a1", opportunity_id="o1",
                  revision_id="r1", evidence_id="link-review"),
        ]
        ledger.append(rows)
        view = ledger.replay("A", ts(60))
        self.assertEqual(1, view.data["exposure_count"])
        self.assertEqual(["sf", "sg"], view.data["activities"]["a1"]["source_ids"])

    def test_unadjudicated_resemblance_never_deduplicates(self):
        ledger = FoundationLedger()
        ledger.append([
            event("a1", "A", "activity", 20, activity_id="a1", started_at=ts(1),
                  ended_at=ts(20), discipline="running", intent="unknown",
                  plan_ownership="unplanned_or_unknown_intent", completion="completed"),
            event("a2", "A", "activity", 20, activity_id="a2", started_at=ts(1),
                  ended_at=ts(20), discipline="running", intent="unknown",
                  plan_ownership="unplanned_or_unknown_intent", completion="completed"),
            event("s1", "A", "source", 21, source_id="s1", activity_id="a1",
                  alias=["garmin", "same-looking"], digest="1" * 64, measurements={}),
            event("s2", "A", "source", 22, source_id="s2", activity_id="a2",
                  alias=["fit", "same-looking"], digest="2" * 64, measurements={}),
        ])
        self.assertEqual(2, ledger.replay("A", ts(30)).data["exposure_count"])

    def test_identical_event_retry_is_idempotent_and_keeps_projection_current(self):
        ledger = FoundationLedger()
        row = event("a1", "A", "activity", 20, activity_id="a1", started_at=ts(1),
                    ended_at=ts(20), discipline="running", intent="unknown",
                    plan_ownership="unplanned_or_unknown_intent", completion="completed")
        ledger.append([row])
        view = ledger.replay("A", ts(30))
        ledger.append([row])
        self.assertIs(view, ledger.assert_current(view))
        self.assertEqual(1, ledger.replay("A", ts(30)).data["exposure_count"])

    def test_late_sync_and_offline_import_are_absent_from_earlier_view(self):
        ledger = FoundationLedger()
        row = event("a1", "A", "activity", 90, activity_id="a1", started_at=ts(10),
                    ended_at=ts(30), discipline="running", intent="unknown",
                    plan_ownership="unplanned_or_unknown_intent", completion="completed",
                    offline_created_at=ts(31))
        ledger.append([row])
        self.assertEqual(0, ledger.replay("A", ts(60)).data["exposure_count"])
        self.assertEqual(1, ledger.replay("A", ts(100)).data["exposure_count"])

    def test_offline_creation_cannot_postdate_trusted_availability(self):
        ledger = FoundationLedger()
        row = event("a1", "A", "activity", 40, activity_id="a1", started_at=ts(10),
                    ended_at=ts(30), discipline="running", intent="unknown",
                    plan_ownership="unplanned_or_unknown_intent", completion="completed",
                    offline_created_at=ts(50))
        with self.assertRaises(Quarantine):
            ledger.append([row])

    def test_stale_revision_cannot_link_and_partial_keeps_three_doses(self):
        ledger = FoundationLedger()
        rows = planned() + [
            event("r2", "A", "plan_revision", 2, opportunity_id="o1", revision_id="r2",
                  parent_revision_id="r1", intent="easy_aerobic",
                  planned_duration=measured(1200, "s")),
            event("d2", "A", "delivery", 3, opportunity_id="o1", revision_id="r2"),
            event("a1", "A", "activity", 30, activity_id="a1", started_at=ts(10),
                  ended_at=ts(22), discipline="running", intent="easy_aerobic",
                  plan_ownership="planned_by_stridelab", completion="partial",
                  performed_duration=measured(720, "s")),
            event("bad", "A", "link", 31, activity_id="a1", opportunity_id="o1",
                  revision_id="r1", evidence_id="stale"),
        ]
        ledger.append(rows)
        with self.assertRaises(Quarantine):
            ledger.replay("A", ts(40))

    def test_manual_only_completion_preserves_unknown_numeric_dose(self):
        ledger = FoundationLedger()
        ledger.append(planned() + [
            event("a1", "A", "activity", 30, activity_id="a1", started_at=ts(10),
                  ended_at=ts(30), discipline="running", intent="easy_aerobic",
                  plan_ownership="planned_by_stridelab", completion="completed",
                  occurrence_basis="manual", performed_duration=missing("unknown", "s")),
            event("l1", "A", "link", 31, activity_id="a1", opportunity_id="o1",
                  revision_id="r1", evidence_id="manual-review"),
        ])
        activity = ledger.replay("A", ts(40)).data["activities"]["a1"]
        self.assertEqual("unknown", activity["performed_duration"]["status"])
        self.assertEqual([], activity["source_ids"])

    def test_correction_invalidates_old_source_and_conflict_is_not_zero(self):
        ledger = FoundationLedger()
        ledger.append([
            event("a1", "A", "activity", 20, activity_id="a1", started_at=ts(1),
                  ended_at=ts(20), discipline="running", intent="unknown",
                  plan_ownership="unplanned_or_unknown_intent", completion="completed"),
            event("s1", "A", "source", 21, source_id="s1", activity_id="a1",
                  alias=["fit", "v1"], digest="1" * 64,
                  measurements={"distance": measured(5000, "m"), "hr": missing()}),
            event("s2", "A", "source", 22, source_id="s2", activity_id="a1",
                  alias=["garmin", "activity"], digest="2" * 64,
                  measurements={"distance": measured(5100, "m"), "hr": measured(0, "bpm")},
                  alias_evidence_id="review"),
            event("s3", "A", "source_correction", 30, source_id="s3", activity_id="a1",
                  alias=["fit", "v2"], digest="3" * 64, supersedes_source_id="s1",
                  correction_evidence_id="correction-review",
                  measurements={"distance": measured(5050, "m"), "hr": missing()}),
        ])
        activity = ledger.replay("A", ts(40)).data["activities"]["a1"]
        self.assertEqual("conflict", activity["measurements"]["distance"]["status"])
        self.assertEqual("conflict", activity["measurements"]["hr"]["status"])
        self.assertIn("s1", activity["invalidated_source_ids"])

    def test_cross_account_reference_and_duplicate_alias_fail_closed(self):
        ledger = FoundationLedger()
        ledger.append([event("a1", "A", "activity", 10, activity_id="a1", started_at=ts(1),
                                   ended_at=ts(10), discipline="running", intent="unknown",
                                   plan_ownership="unplanned_or_unknown_intent", completion="completed")])
        with self.assertRaises(Quarantine):
            ledger.append([event("s1", "B", "source", 11, source_id="s1", activity_id="a1",
                                       alias=["fit", "foreign"], digest="1" * 64, measurements={})])
        ledger.append([event("s1", "A", "source", 11, source_id="s1", activity_id="a1",
                                   alias=["fit", "one"], digest="1" * 64, measurements={})])
        with self.assertRaises(Quarantine):
            ledger.append([event("s2", "A", "source", 12, source_id="s2", activity_id="a1",
                                       alias=["fit", "one"], digest="2" * 64, measurements={})])

    def test_unknown_intent_is_not_rewritten_as_stridelab_plan(self):
        ledger = FoundationLedger()
        ledger.append([event("a1", "A", "activity", 20, activity_id="a1", started_at=ts(1),
                                   ended_at=ts(20), discipline="strength", intent="unknown",
                                   plan_ownership="unplanned_or_unknown_intent", completion="completed")])
        activity = ledger.replay("A", ts(30)).data["activities"]["a1"]
        self.assertEqual("unknown", activity["intent"])
        self.assertEqual("unplanned_or_unknown_intent", activity["plan_ownership"])

    def test_withdrawal_invalidates_returned_projection_and_other_account_survives(self):
        ledger = FoundationLedger()
        ledger.append([
            event("a1", "A", "activity", 10, activity_id="a1", started_at=ts(1), ended_at=ts(10),
                  discipline="running", intent="unknown", plan_ownership="unplanned_or_unknown_intent",
                  completion="completed"),
            event("b1", "B", "activity", 10, activity_id="b1", started_at=ts(1), ended_at=ts(10),
                  discipline="running", intent="unknown", plan_ownership="unplanned_or_unknown_intent",
                  completion="completed"),
        ])
        old = ledger.replay("A", ts(20))
        ledger.withdraw("A", "participant withdrawal")
        with self.assertRaises(Quarantine):
            ledger.assert_current(old)
        with self.assertRaises(Quarantine):
            ledger.replay("A", ts(20))
        self.assertEqual(1, ledger.replay("B", ts(20)).data["exposure_count"])

    def test_malformed_timestamp_is_rejected_before_ordering(self):
        ledger = FoundationLedger()
        bad = event("a1", "A", "activity", 10, activity_id="a1", started_at="2026-11-01T01:00:00",
                    ended_at=ts(10), discipline="running", intent="unknown",
                    plan_ownership="unplanned_or_unknown_intent", completion="completed")
        with self.assertRaises(Quarantine):
            ledger.append([bad])


class TraceContractTests(unittest.TestCase):
    def segment(self):
        return {"account_id": "A", "activity_id": "a1", "segment_id": "seg1",
                "source_ids": ["s1"], "source_digest": "a" * 64,
                "parser_version": "trace/1", "device_epoch": "watch-fw-1",
                "sensor_epoch": "strap-1", "start_at": ts(0), "end_at": ts(1),
                "distance_method": "calibrated_footpod", "timer_basis": "device_timer",
                "samples": [
                    {"at": ts(0), "distance_m": 0, "timer_state": "running",
                     "hr": missing()},
                    {"at": ts(1), "distance_m": 180, "timer_state": "running",
                     "hr": measured(0, "bpm")},
                ], "max_gap_s": 60}

    def test_missing_hr_and_literal_zero_are_distinct(self):
        result = validate_trace_segment(self.segment())
        self.assertEqual(1, result["missing_hr_samples"])
        self.assertEqual(1, result["zero_hr_samples"])
        self.assertIsNone(result["pace_s_per_km_at_zero_speed"])

    def test_gap_distance_reset_pause_and_bad_clock_are_quarantined(self):
        cases = []
        gap = self.segment(); gap["max_gap_s"] = 30; cases.append(gap)
        reset = self.segment(); reset["samples"][1]["distance_m"] = -1; cases.append(reset)
        pause = self.segment(); pause["samples"][1]["timer_state"] = "paused"; cases.append(pause)
        clock = self.segment(); clock["samples"][1]["at"] = "2026-11-01T00:01:00"; cases.append(clock)
        for item in cases:
            with self.subTest(case=cases.index(item)), self.assertRaises(Quarantine):
                validate_trace_segment(item)


if __name__ == "__main__":
    unittest.main()
