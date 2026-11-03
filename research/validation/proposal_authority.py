"""Bridge signed research source links to pending workout revisions.

The underlying week_change_contract is only a pure structural validator. An
untrusted caller can label evidence "verified", so *this wrapper* must verify
the signed source attestation before setting that label. No user-authentication
mechanism or real provider account validation is implemented here.

For this reason the bridge only accepts RESEARCH_POLICY-sourced proposals.
Actual athlete requests and consent must arrive from a future authenticated
application boundary. Even signed proposals remain uncommitted.
"""
from __future__ import annotations

from copy import deepcopy
from typing import Any

from source_link_gate import LinkRejected, verify_attestation
from multiweek_reconciliation import validate_event
from week_change_contract import ProposalBlocked, draft_revision


def draft_from_signed_observation(
    plan: dict[str, Any],
    requested_change: dict[str, Any],
    constraints: dict[str, Any],
    observed_event: dict[str, Any],
    signed_grant: dict[str, Any] | None,
    *,
    signing_key: bytes,
    athlete_id: str,
    accepted_reviewers: set[str],
    active_connections: set[str],
    at_unix: int,
    revoked_evidence_refs: set[str] | None = None,
) -> dict[str, Any]:
    if requested_change.get("source") != "research_policy":
        raise ProposalBlocked("ATHLETE_AUTHENTICATION_NOT_CONNECTED")
    if signed_grant is None:
        raise ProposalBlocked("SIGNED_EVIDENCE_REQUIRED")
    validate_event(observed_event)
    if observed_event["status"] not in {"completed", "partial", "skipped"}:
        raise ProposalBlocked("OBSERVED_WORKOUT_STATUS_UNVERIFIED")
    if (observed_event["source"] != signed_grant.get("source")
            or observed_event["external_id"] != signed_grant.get("external_id")):
        raise ProposalBlocked("SIGNED_LINK_DOES_NOT_MATCH_OBSERVATION")
    session_ids = {s["id"] for s in plan.get("sessions", [])}
    try:
        verify_attestation(
            signed_grant, signing_key, athlete_id=athlete_id,
            session_ids=session_ids,
            accepted_reviewers=accepted_reviewers,
            active_connections=active_connections,
            at_unix=at_unix, revoked_evidence_refs=revoked_evidence_refs,
        )
    except LinkRejected as exc:
        raise ProposalBlocked("SIGNED_EVIDENCE_REJECTED") from exc
    # Untrusted caller-provided evidence markers are replaced, not trusted.
    request = deepcopy(requested_change)
    request["evidence_status"] = "verified"
    request["evidence_refs"] = [
        "signed-source:" + signed_grant["source"] + "::" + signed_grant["external_id"],
        "review-proof:" + signed_grant["evidence_ref"],
    ]
    result = draft_revision(plan, request, constraints)
    result["source_authentication"] = "HMAC_RESEARCH_LINK_ONLY"
    result["provider_account_authentication"] = "NOT_CONNECTED"
    result["athlete_approval"] = "REQUIRED_NOT_PERFORMED"
    return result
