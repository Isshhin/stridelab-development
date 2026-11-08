"""P2-X DEV-1 synthetic-only provenance/withdrawal/receipt fence.

This module cannot authenticate provider rights, real user approvals or keys.
It never issues a write capability. SHA-256 here checks fixture consistency,
not origin authority or resistance to an attacker controlling the caller.
"""
from __future__ import annotations

from datetime import datetime, timezone
from string import hexdigits
from hashlib import sha256
from json import dumps
from typing import Any

SCHEMA = "p2x-dev1-fixture-provenance-v1"
ENTRY_FIELDS = frozenset({
    "seq", "event_id", "kind", "athlete_id", "plan_id", "plan_version",
    "source_id", "target_id", "payload_digest", "observed_at",
    "available_at", "prev_digest", "digest"
})
KINDS = {"ORIGINAL", "CORRECT", "WITHDRAW", "REVOKE_SOURCE"}
GENESIS = "0" * 64


def _named(v: Any) -> bool:
    return isinstance(v, str) and bool(v.strip())


def _hex_digest(v: Any) -> bool:
    return (isinstance(v, str) and len(v) == 64
            and all(ch in "0123456789abcdef" for ch in v))


def _time(v: Any) -> datetime:
    if not isinstance(v, str):
        raise ValueError("OFFSET_TIMESTAMP_REQUIRED")
    try:
        d = datetime.fromisoformat(v.replace("Z", "+00:00"))
        if d.tzinfo is None or d.utcoffset() is None:
            raise ValueError()
        return d.astimezone(timezone.utc)
    except (ValueError, OverflowError) as exc:
        raise ValueError("INVALID_OFFSET_TIMESTAMP") from exc


def _digest(v: Any) -> str:
    try:
        return sha256(dumps(v, sort_keys=True, separators=(",", ":"),
                            ensure_ascii=True, allow_nan=False).encode()).hexdigest()
    except (TypeError, ValueError) as exc:
        raise ValueError("NONCANONICAL_FIXTURE") from exc


def seal_entry(data: dict, prev_digest: str = GENESIS) -> dict:
    """Seal an invented event; no signature, key or real source custody."""
    if not isinstance(data, dict) or set(data) != ENTRY_FIELDS - {"digest"}:
        raise ValueError("ENTRY_SCHEMA_MISMATCH")
    if not isinstance(prev_digest, str) or len(prev_digest) != 64:
        raise ValueError("PREV_DIGEST_FORMAT")
    if data["prev_digest"] != prev_digest:
        raise ValueError("PREV_DIGEST_MISMATCH")
    result = dict(data)
    result["digest"] = _digest(result)
    return result


def _scope(context: dict) -> tuple[str, str, int, str, datetime]:
    if not isinstance(context, dict) or context.get("synthetic_only") is not True:
        raise ValueError("SYNTHETIC_ONLY_REQUIRED")
    fields = ("athlete_id", "plan_id", "opportunity_id", "policy_version")
    if not all(_named(context.get(k)) for k in fields):
        raise ValueError("SCOPE_ID_REQUIRED")
    version = context.get("plan_version")
    if type(version) is not int or version < 1:
        raise ValueError("PLAN_VERSION_INVALID")
    cutoff = _time(context.get("decision_as_of"))
    if not isinstance(context.get("ledger"), list):
        raise ValueError("LEDGER_LIST_REQUIRED")
    return (context["athlete_id"], context["plan_id"], version,
            context["opportunity_id"], cutoff)


def ledger_state(context: dict, as_of: str | None = None) -> dict:
    """Validate complete append chain; project only decision-available prefix."""
    athlete, plan, version, opportunity, cutoff = _scope(context)
    time = _time(as_of) if as_of is not None else cutoff
    previous_hash = GENESIS
    last_available = None
    seen = set()
    effective_tip = GENESIS
    active: dict[str, dict] = {}
    current_to_root: dict[str, str] = {}
    revoked_sources = set()
    corrections: list[str] = []
    withdrawals: list[str] = []
    future_count = 0
    # Check entire fixture for tampering, independently of as_of.
    for seq, entry in enumerate(context["ledger"], 1):
        if not isinstance(entry, dict) or set(entry) != ENTRY_FIELDS:
            raise ValueError("ENTRY_SCHEMA_MISMATCH")
        if type(entry["seq"]) is not int or entry["seq"] != seq:
            raise ValueError("NONCONTIGUOUS_LEDGER_SEQUENCE")
        if entry["event_id"] in seen or not _named(entry["event_id"]):
            raise ValueError("DUPLICATE_EVENT_ID")
        seen.add(entry["event_id"])
        if entry["prev_digest"] != previous_hash:
            raise ValueError("BROKEN_LEDGER_CHAIN")
        stored = entry["digest"]
        if not isinstance(stored, str) or stored != _digest(
                {k: v for k, v in entry.items() if k != "digest"}):
            raise ValueError("LEDGER_DIGEST_MISMATCH")
        if (entry["athlete_id"] != athlete or entry["plan_id"] != plan
                or type(entry["plan_version"]) is not int
                or entry["plan_version"] != version):
            raise ValueError("CROSS_SCOPE_LEDGER_RECORD")
        if entry["kind"] not in KINDS or not _named(entry["source_id"]):
            raise ValueError("INVALID_EVENT_KIND_OR_SOURCE")
        observed = _time(entry["observed_at"])
        available = _time(entry["available_at"])
        if available < observed:
            raise ValueError("BACKDATED_AVAILABILITY")
        if last_available is not None and available < last_available:
            raise ValueError("BACKDATED_APPEND")
        last_available = available
        previous_hash = stored
        if available > time:
            future_count += 1
            continue
        effective_tip = stored
        kind = entry["kind"]
        source = entry["source_id"]
        target = entry["target_id"]
        payload = entry["payload_digest"]
        if source in revoked_sources:
            raise ValueError("SOURCE_EVENT_AFTER_REVOCATION")
        if kind == "ORIGINAL":
            if target is not None or not _hex_digest(payload):
                raise ValueError("ORIGINAL_RECORD_INVALID")
            active[entry["event_id"]] = {
                "event_id": entry["event_id"], "source_id": source,
                "payload_digest": payload}
            current_to_root[entry["event_id"]] = entry["event_id"]
        elif kind in {"CORRECT", "WITHDRAW"}:
            if not _named(target) or target not in current_to_root:
                raise ValueError("TARGET_NOT_CURRENT")
            root = current_to_root[target]
            if root not in active or active[root]["event_id"] != target:
                raise ValueError("STALE_CORRECTION_TARGET")
            if active[root]["source_id"] != source:
                raise ValueError("CROSS_SOURCE_REVISION")
            del current_to_root[target]
            if kind == "CORRECT":
                if not _hex_digest(payload):
                    raise ValueError("CORRECTION_PAYLOAD_REQUIRED")
                active[root] = {"event_id": entry["event_id"],
                                "source_id": source, "payload_digest": payload}
                current_to_root[entry["event_id"]] = root
                corrections.append(entry["event_id"])
            else:
                if payload is not None:
                    raise ValueError("WITHDRAWAL_PAYLOAD_FORBIDDEN")
                del active[root]
                withdrawals.append(entry["event_id"])
        else:
            if target is not None or payload is not None:
                raise ValueError("REVOKE_SOURCE_PAYLOAD_FORBIDDEN")
            revoked_sources.add(source)
            withdrawals.append(entry["event_id"])
            for root in list(active):
                if active[root]["source_id"] == source:
                    current_to_root.pop(active[root]["event_id"], None)
                    del active[root]
    return {
        "schema": SCHEMA, "athlete_id": athlete, "plan_id": plan,
        "plan_version": version, "opportunity_id": opportunity,
        "as_of": time.isoformat(), "ledger_tip": effective_tip,
        "chain_complete_digest": previous_hash, "future_events": future_count,
        "active_records": sorted(active.values(), key=lambda v: v["event_id"]),
        "corrected_event_ids": corrections,
        "withdrawal_event_ids": withdrawals,
        "revoked_sources": sorted(revoked_sources),
        "source_authentication": "NOT_PROVEN_SYNTHETIC_FIXTURE",
        "plan_linkage": "ASSERTED_ONLY_P2C_NATIVE_ID_UNVERIFIED",
    }


def inspect_cross_track(context: dict, base_audit: dict) -> dict:
    """Check the DEV-0 audit result without silently upgrading lineage."""
    state = ledger_state(context)
    if not isinstance(base_audit, dict):
        raise ValueError("BASE_AUDIT_REQUIRED")
    if (base_audit.get("athlete_id") != state["athlete_id"]
            or base_audit.get("opportunity_id") != state["opportunity_id"]
            or base_audit.get("as_of") != state["as_of"]
            or base_audit.get("common_plan_version") != state["plan_version"]
            or base_audit.get("p2d_p2e_asserted_plan_id") != state["plan_id"]):
        raise ValueError("BASE_AUDIT_SCOPE_MISMATCH")
    if (base_audit.get("p2c_plan_lineage") != "UNVERIFIED_NO_P2C_PLAN_ID"
            or base_audit.get("cross_track_source_linkage") != "NOT_AUTHENTICATED"
            or base_audit.get("coaching_authority") != "NONE_SYNTHETIC_RESEARCH"
            or base_audit.get("safe_to_auto_apply") is not False
            or base_audit.get("applied") is not False
            or base_audit.get("plan_patch") is not None
            or base_audit.get("selected_action") is not None
            or base_audit.get("gate2") != "CLOSED"):
        raise ValueError("BASE_AUDIT_AUTHORITY_MISMATCH")
    if state["withdrawal_event_ids"]:
        category = "DEFER_EVIDENCE_WITHDRAWN"
    elif state["corrected_event_ids"]:
        category = "DEFER_CORRECTION_RECONCILIATION"
    elif base_audit.get("review_status", "").startswith("DEFER"):
        category = "DEFER_UPSTREAM_REVIEW"
    else:
        category = "SHADOW_SNAPSHOT_ONLY"
    return {
        "schema": SCHEMA, "decision_status": category,
        "plan_id": state["plan_id"], "plan_version": state["plan_version"],
        "athlete_id": state["athlete_id"],
        "opportunity_id": state["opportunity_id"],
        "decision_as_of": state["as_of"],
        "policy_version": context["policy_version"],
        "ledger_tip": state["ledger_tip"],
        "evidence_count": len(state["active_records"]),
        "base_audit_digest": _digest(base_audit),
        "source_authentication": "NOT_PROVEN_SYNTHETIC_FIXTURE",
        "p2c_plan_lineage": "UNVERIFIED_NO_P2C_PLAN_ID",
        "can_write_plan": False, "clinical_clearance": False,
        "coaching_authority": "NONE_SYNTHETIC_RESEARCH",
        "gate2": "CLOSED",
    }


def issue_shadow_receipt(review: dict, issued_at: str) -> dict:
    """A replay token. Explicitly never a participant approval or write grant."""
    if not isinstance(review, dict) or review.get("schema") != SCHEMA:
        raise ValueError("REVIEW_REQUIRED")
    if review.get("decision_status") != "SHADOW_SNAPSHOT_ONLY":
        raise ValueError("UNRESOLVED_REVIEW_NOT_RECEIPTABLE")
    if review.get("can_write_plan") is not False or review.get("gate2") != "CLOSED":
        raise ValueError("WRITABLE_REVIEW_FORBIDDEN")
    issued = _time(issued_at)
    if issued < _time(review["decision_as_of"]):
        raise ValueError("RECEIPT_BEFORE_DECISION")
    data = {k: review[k] for k in (
        "athlete_id", "opportunity_id", "plan_id", "plan_version",
        "policy_version", "decision_as_of", "ledger_tip", "base_audit_digest")}
    data["issued_at"] = issued.isoformat()
    data["capability"] = "NONE_SHADOW_REPLAY_TOKEN"
    return {**data, "receipt_digest": _digest(data)}


def check_shadow_receipt(receipt: dict, review: dict,
                         context: dict, check_as_of: str) -> dict:
    """Any new ledger append, policy change, revision or withdrawal fences token."""
    if not isinstance(receipt, dict):
        raise ValueError("RECEIPT_REQUIRED")
    keys = {
        "athlete_id", "opportunity_id", "plan_id", "plan_version",
        "policy_version", "decision_as_of", "ledger_tip", "base_audit_digest",
        "issued_at", "capability", "receipt_digest",
    }
    if set(receipt) != keys or receipt.get("capability") != "NONE_SHADOW_REPLAY_TOKEN":
        raise ValueError("RECEIPT_SCHEMA_MISMATCH")
    if receipt["receipt_digest"] != _digest({
            k: v for k, v in receipt.items() if k != "receipt_digest"}):
        raise ValueError("RECEIPT_TAMPERED")
    checked = _time(check_as_of)
    if checked < _time(receipt["issued_at"]):
        raise ValueError("CHECK_BEFORE_RECEIPT")
    athlete, plan, version, opportunity, decision = _scope(context)
    if (athlete != receipt["athlete_id"] or plan != receipt["plan_id"]
            or version != receipt["plan_version"]
            or opportunity != receipt["opportunity_id"]
            or decision.isoformat() != receipt["decision_as_of"]
            or context["policy_version"] != receipt["policy_version"]):
        return {"status": "STALE_SHADOW_RECEIPT", "reason": "CONTEXT_OR_POLICY_CHANGED",
                "can_write_plan": False}
    if (not isinstance(review, dict)
            or review.get("decision_status") != "SHADOW_SNAPSHOT_ONLY"
            or review.get("can_write_plan") is not False
            or review.get("gate2") != "CLOSED"):
        return {"status": "STALE_SHADOW_RECEIPT", "reason": "REVIEW_IS_NOT_SAFE",
                "can_write_plan": False}
    for key in ("athlete_id", "opportunity_id", "plan_id",
                "plan_version", "policy_version", "decision_as_of",
                "ledger_tip", "base_audit_digest"):
        if receipt[key] != review.get(key):
            return {"status": "STALE_SHADOW_RECEIPT", "reason": "REVIEW_OR_POLICY_CHANGED",
                    "can_write_plan": False}
    current = ledger_state(context, checked.isoformat())
    if (current["ledger_tip"] != receipt["ledger_tip"]
            or current["revoked_sources"] or current["withdrawal_event_ids"]):
        return {"status": "STALE_SHADOW_RECEIPT", "reason": "LEDGER_CHANGED_OR_WITHDRAWN",
                "can_write_plan": False}
    return {"status": "SHADOW_RECEIPT_INTACT_NOT_APPROVAL",
            "reason": "SYNTHETIC_REPLAY_ONLY", "can_write_plan": False}



def inspect_bundle(bundle: dict, context: dict) -> dict:
    """Replay actual P2-X DEV-0 audit before synthetic provenance checks.

    The imported DEV-0 module is the existing source in the same research tree.
    No approval or write operation is exposed.
    """
    from p2x_integration import audit
    return inspect_cross_track(context, audit(bundle))
