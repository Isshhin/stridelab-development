"""8E research source-link gate, with an explicit attestation trust boundary.

Provider uploads are UNTRUSTED. A provider-supplied "verified_session_id"
is always discarded. Only an independently issued, HMAC-authenticated
attestation from a configured reviewer/service can link an event to a planned
session, and only within the specified pseudonymous athlete's plan.

This does NOT prove provider OAuth, account ownership, reviewer authorization,
or that a clinical/training match is correct. Keys are injected at runtime;
production integrations need their own authentication, revocation and audit.
"""
from __future__ import annotations

from copy import deepcopy
import hashlib
import hmac
import json
from typing import Any

from multiweek_reconciliation import ingest_source_batch, source_activity_id
from sqlite_ledger import ResearchPlanStore

REQUIRED_FIELDS = (
    "athlete_id", "source", "external_id", "verified_session_id",
    "reviewer_id", "provider_connection_id", "evidence_ref",
    "issued_at", "expires_at",
)


class LinkRejected(ValueError):
    pass


def _canonical(grant: dict[str, Any]) -> bytes:
    if any(k not in grant for k in REQUIRED_FIELDS):
        raise LinkRejected("Attestation missing required field")
    for name in REQUIRED_FIELDS[:7]:
        value = grant[name]
        if not isinstance(value, str) or not value.strip():
            raise LinkRejected(f"Attestation {name} must be a nonempty string")
    if (not isinstance(grant["issued_at"], int)
            or not isinstance(grant["expires_at"], int)
            or isinstance(grant["issued_at"], bool)
            or isinstance(grant["expires_at"], bool)
            or grant["issued_at"] >= grant["expires_at"]):
        raise LinkRejected("Invalid issuance/expiry range")
    return json.dumps(
        {key: grant[key] for key in REQUIRED_FIELDS},
        separators=(",", ":"), sort_keys=True, ensure_ascii=True
    ).encode("utf-8")


def _secret(key: bytes) -> None:
    if not isinstance(key, bytes) or len(key) < 32:
        raise LinkRejected("Runtime signing secret must be 32 bytes or longer")


def create_attestation(grant: dict[str, Any], signing_key: bytes) -> dict[str, Any]:
    """Test fixture/authorized issuer primitive; not provider authentication."""
    _secret(signing_key)
    payload = _canonical(grant)
    signed = {key: grant[key] for key in REQUIRED_FIELDS}
    signed["signature"] = hmac.new(signing_key, payload, hashlib.sha256).hexdigest()
    return signed


def verify_attestation(
    grant: dict[str, Any], signing_key: bytes, *,
    athlete_id: str,
    session_ids: set[str],
    accepted_reviewers: set[str],
    active_connections: set[str],
    at_unix: int,
    revoked_evidence_refs: set[str] | None = None,
) -> None:
    _secret(signing_key)
    payload = _canonical(grant)
    signature = grant.get("signature")
    if not isinstance(signature, str) or len(signature) != 64:
        raise LinkRejected("Signature absent or malformed")
    good = hmac.new(signing_key, payload, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(signature, good):
        raise LinkRejected("Invalid link attestation signature")
    if grant["athlete_id"] != athlete_id:
        raise LinkRejected("Cross-athlete claim rejected")
    if grant["verified_session_id"] not in session_ids:
        raise LinkRejected("Link target does not exist in athlete plan")
    if grant["reviewer_id"] not in accepted_reviewers:
        raise LinkRejected("Issuer not authorized by current runtime configuration")
    if grant["provider_connection_id"] not in active_connections:
        raise LinkRejected("Provider connection not active")
    if revoked_evidence_refs and grant["evidence_ref"] in revoked_evidence_refs:
        raise LinkRejected("Attestation evidence revoked")
    if not isinstance(at_unix, int) or not (grant["issued_at"] <= at_unix < grant["expires_at"]):
        raise LinkRejected("Attestation not valid at evaluation time")


def ingest_verified_events(
    db: ResearchPlanStore,
    events: list[dict[str, Any]],
    attestations: list[dict[str, Any]],
    signing_key: bytes,
    *,
    athlete_id: str,
    session_ids: set[str],
    accepted_reviewers: set[str],
    active_connections: set[str],
    at_unix: int,
    revoked_evidence_refs: set[str] | None = None,
) -> dict[str, Any]:
    """Scrub untrusted claims, then ingest atomically through existing ledger.

    Invalid/expired/mismatched attestations cause *unlinked* recording, never a
    guessed matched workout. Duplicate/ambiguous grants are not selected.
    """
    claims: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for grant in attestations:
        source = grant.get("source")
        external = grant.get("external_id")
        if isinstance(source, str) and isinstance(external, str):
            claims.setdefault((source, external), []).append(grant)

    cleaned: list[dict[str, Any]] = []
    decisions: list[dict[str, str]] = []
    for event in events:
        raw = deepcopy(event)
        identity = source_activity_id(raw)
        source, external = raw["source"], raw["external_id"]
        raw.pop("athlete_id", None)
        raw["verified_session_id"] = None
        presented = claims.get((source, external), [])
        reason = "NO_ATTESTATION"
        if len(presented) > 1:
            reason = "AMBIGUOUS_ATTESTATIONS"
        elif len(presented) == 1:
            grant = presented[0]
            try:
                verify_attestation(
                    grant, signing_key, athlete_id=athlete_id,
                    session_ids=session_ids, accepted_reviewers=accepted_reviewers,
                    active_connections=active_connections, at_unix=at_unix,
                    revoked_evidence_refs=revoked_evidence_refs,
                )
                raw["verified_session_id"] = grant["verified_session_id"]
                reason = "SIGNED_LINK_CONFIRMED"
            except LinkRejected:
                reason = "ATTESTATION_REJECTED"
        cleaned.append(raw)
        decisions.append({"source_activity_id": identity, "reason": reason})
    outcome = ingest_source_batch(db, cleaned)
    return {
        "ingest": outcome, "link_decisions": decisions,
        "linked": sum(x["reason"] == "SIGNED_LINK_CONFIRMED" for x in decisions),
        "unlinked": sum(x["reason"] != "SIGNED_LINK_CONFIRMED" for x in decisions),
        "credential_validation": "NOT_PERFORMED_HMAC_ONLY",
    }
