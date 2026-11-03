"""Batch 8E source-link gate adversarial tests (synthetic credentials only)."""
import tempfile
from pathlib import Path
import unittest

from source_link_gate import (
    LinkRejected, create_attestation, ingest_verified_events, verify_attestation)
from multiweek_reconciliation import reconcile_week
from sqlite_ledger import ResearchPlanStore

KEY = bytes.fromhex("a4" * 32)  # TEST-ONLY KEY: never a real credential


def run_event(source="watch", external_id="a1", claimed="W1-run"):
    return {
        "week": 1, "source": source, "external_id": external_id,
        "verified_session_id": claimed, "athlete_id": "pretend-athlete",
        "discipline": "running", "status": "completed",
        "actual_km": 8.0, "actual_hard_s": 900,
        "actual_easy_s": 2400, "actual_sets": None,
    }


def grant(source="watch", external_id="a1", session="W1-run", athlete="athlete-A",
          reviewer="trusted-reviewer", connection="active-connection",
          evidence="review-001", issued=100, expires=300):
    return create_attestation({
        "source": source, "external_id": external_id,
        "verified_session_id": session, "athlete_id": athlete,
        "reviewer_id": reviewer,
        "provider_connection_id": connection, "evidence_ref": evidence,
        "issued_at": issued, "expires_at": expires,
    }, KEY)


class LinkGateTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="stride-link-")
        self.addCleanup(temp.cleanup)
        self.db = ResearchPlanStore(Path(temp.name) / "links.sqlite3")
        self.addCleanup(self.db.close)
        self.db.initialize({"week": 1})
        self.plan = [{"id": "W1-run", "discipline": "running",
                      "planned_km": 8.0, "planned_hard_s": 900}]

    def _import(self, events=None, grants=None, **options):
        return ingest_verified_events(
            self.db, [run_event()] if events is None else events,
            [grant()] if grants is None else grants,
            KEY, athlete_id="athlete-A", session_ids={"W1-run"},
            accepted_reviewers={"trusted-reviewer"},
            active_connections={"active-connection"}, at_unix=150,
            **options
        )

    def _rec(self):
        return reconcile_week(self.plan, self.db.activity_snapshot(), 1)

    def test_signed_link_counts_one_verified_session(self):
        result = self._import()
        self.assertEqual(1, result["linked"])
        self.assertEqual(0, result["unlinked"])
        rec = self._rec()
        self.assertEqual(8, rec["known_actual_running_km"])
        self.assertTrue(rec["total_actual_complete"])

    def test_raw_claim_without_signed_attestation_is_discarded(self):
        result = self._import(grants=[])
        self.assertEqual(0, result["linked"])
        self.assertEqual(1, result["unlinked"])
        self.assertEqual(0, self._rec()["known_actual_running_km"])
        self.assertEqual(["watch::a1"], self._rec()["unlinked_source_records"])

    def test_tampered_session_id_invalidates_signature(self):
        wrong = grant()
        wrong["verified_session_id"] = "W1-other"
        result = self._import(grants=[wrong])
        self.assertEqual("ATTESTATION_REJECTED", result["link_decisions"][0]["reason"])
        self.assertFalse(self._rec()["total_actual_complete"])

    def test_signed_cross_athlete_claim_does_not_link(self):
        result = self._import(grants=[grant(athlete="athlete-B")])
        self.assertEqual(0, result["linked"])
        self.assertEqual(0, self._rec()["known_actual_running_km"])

    def test_signed_wrong_external_activity_id_does_not_link(self):
        result = self._import(grants=[grant(external_id="different")])
        self.assertEqual("NO_ATTESTATION", result["link_decisions"][0]["reason"])
        self.assertEqual(1, result["unlinked"])

    def test_expired_grant_is_rejected(self):
        result = self._import(grants=[grant(expires=149)])
        self.assertEqual(0, result["linked"])
        self.assertFalse(self._rec()["total_actual_complete"])

    def test_reviewer_must_be_currently_authorized(self):
        result = self._import(grants=[grant(reviewer="imposter")])
        self.assertEqual(0, result["linked"])

    def test_provider_connection_must_be_active(self):
        result = self._import(grants=[grant(connection="not-connected")])
        self.assertEqual(0, result["linked"])

    def test_revoked_evidence_is_not_accepted(self):
        result = self._import(revoked_evidence_refs={"review-001"})
        self.assertEqual("ATTESTATION_REJECTED", result["link_decisions"][0]["reason"])

    def test_ambiguous_two_grants_are_quarantined(self):
        result = self._import(grants=[grant(), grant(evidence="review-002")])
        self.assertEqual("AMBIGUOUS_ATTESTATIONS",
                         result["link_decisions"][0]["reason"])
        self.assertEqual(0, result["linked"])

    def test_replay_is_idempotent(self):
        first = self._import()
        second = self._import()
        self.assertEqual({"inserted": 1, "replayed": 0}, first["ingest"])
        self.assertEqual({"inserted": 0, "replayed": 1}, second["ingest"])
        self.assertEqual(1, len(self.db.activity_snapshot()))

    def test_conflicting_replay_rejected_without_overwrite(self):
        self._import()
        altered = run_event()
        altered["actual_km"] = 9
        with self.assertRaises(ValueError):
            self._import(events=[altered])
        self.assertEqual(8, self._rec()["known_actual_running_km"])

    def test_signature_cannot_be_produced_with_short_key(self):
        with self.assertRaises(LinkRejected):
            create_attestation({
                "source": "watch", "external_id": "a1",
                "verified_session_id": "W1-run",
            }, b"not-a-real-key")

    def test_signed_plan_session_not_in_user_plan_rejected(self):
        result = self._import(grants=[grant(session="other-session")])
        self.assertEqual(0, result["linked"])
        self.assertEqual(0, self._rec()["known_actual_running_km"])

    def test_source_identity_is_not_derived_from_distance_or_time(self):
        upload = run_event(external_id="other-watch-activity")
        result = self._import(events=[upload])
        self.assertEqual(0, result["linked"])
        self.assertEqual(["watch::other-watch-activity"],
                         self._rec()["unlinked_source_records"])


if __name__ == "__main__":
    unittest.main()
