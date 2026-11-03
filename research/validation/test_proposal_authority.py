"""End-to-end research evidence gate tests for training plan proposals."""
import unittest

from proposal_authority import draft_from_signed_observation
from week_change_contract import ProposalBlocked
from test_source_link_gate import KEY, run_event, grant
from test_week_change_contract import plan, limits, request


class SignedProposalBoundaryTests(unittest.TestCase):
    def _draft(self, changed=None, signed=None, observed=None, **options):
        candidate = request(source="research_policy")
        if changed:
            candidate.update(changed)
        return draft_from_signed_observation(
            plan(), candidate, limits(),
            run_event() if observed is None else observed,
            grant(session="quality") if signed is None else signed,
            signing_key=KEY, athlete_id="athlete-A",
            accepted_reviewers={"trusted-reviewer"},
            active_connections={"active-connection"},
            at_unix=150, **options,
        )

    def test_signed_observation_generates_only_a_pending_proposal(self):
        result = self._draft()
        self.assertEqual("PROPOSED_PENDING_EXPLICIT_APPROVAL", result["status"])
        self.assertFalse(result["applied"])
        self.assertTrue(result["requires_explicit_athlete_approval"])
        self.assertEqual("HMAC_RESEARCH_LINK_ONLY", result["source_authentication"])
        self.assertEqual("NOT_CONNECTED", result["provider_account_authentication"])
        self.assertEqual("REQUIRED_NOT_PERFORMED", result["athlete_approval"])
        self.assertTrue(all("user-confirmation-001" not in x
                            for x in result["evidence_refs"]))

    def test_missing_attestation_fails_even_when_caller_says_verified(self):
        with self.assertRaisesRegex(ProposalBlocked, "SIGNED_EVIDENCE_REQUIRED"):
            self._draft(changed={
                "evidence_status": "verified",
                "evidence_refs": ["fake:confirmed"],
            }, signed={})

    def test_signed_grant_must_match_source_and_external_id(self):
        signed = grant(source="other-provider", session="quality")
        with self.assertRaisesRegex(ProposalBlocked, "SIGNED_LINK_DOES_NOT_MATCH_OBSERVATION"):
            self._draft(signed=signed)

    def test_tampered_signed_session_rejected(self):
        signed = grant(session="quality")
        signed["verified_session_id"] = "long"
        with self.assertRaisesRegex(ProposalBlocked, "SIGNED_EVIDENCE_REJECTED"):
            self._draft(signed=signed)

    def test_wrong_athlete_signed_session_rejected(self):
        with self.assertRaisesRegex(ProposalBlocked, "SIGNED_EVIDENCE_REJECTED"):
            self._draft(signed=grant(session="quality", athlete="another-athlete"))

    def test_revoked_evidence_rejected_for_future_proposal(self):
        with self.assertRaisesRegex(ProposalBlocked, "SIGNED_EVIDENCE_REJECTED"):
            self._draft(revoked_evidence_refs={"review-001"})

    def test_athlete_request_without_authentication_is_blocked(self):
        with self.assertRaisesRegex(ProposalBlocked, "ATHLETE_AUTHENTICATION_NOT_CONNECTED"):
            self._draft(changed={"source": "athlete_request"})

    def test_signed_observation_cannot_invent_missing_target_day(self):
        with self.assertRaisesRegex(ProposalBlocked, "EXPLICIT_TARGET_DAY_REQUIRED"):
            self._draft(changed={"target_day": None})

    def test_expired_research_attestation_rejected(self):
        with self.assertRaisesRegex(ProposalBlocked, "SIGNED_EVIDENCE_REJECTED"):
            self._draft(signed=grant(session="quality", expires=140))

    def test_unknown_workout_outcome_is_not_promoted_to_verified_evidence(self):
        unknown = run_event()
        unknown.update(status="unknown", actual_km=None,
                       actual_hard_s=None, actual_easy_s=None)
        with self.assertRaisesRegex(ProposalBlocked, "OBSERVED_WORKOUT_STATUS_UNVERIFIED"):
            self._draft(observed=unknown)


if __name__ == "__main__":
    unittest.main()
