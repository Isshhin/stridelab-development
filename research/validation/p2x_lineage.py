"""P2-X DEV-2 synthetic plan/evidence identity join. No authentication or plan writes.

A caller-supplied manifest can demonstrate **consistency of invented fixtures**;
without a trusted plan authority and provider/source custody it proves no identity.
"""
from __future__ import annotations

from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
from typing import Any

SCHEMA = "p2x-dev2-synthetic-lineage-v1"
TRACKS = ("p2a", "p2c", "p2d", "p2e")
_ALLOWED = frozenset({"schema", "synthetic_only", "athlete_id", "plan_id",
                      "plan_version", "opportunity_id", "as_of", "plan_revision_sha256",
                      "links", "evidence_bindings", "ledger_tip", "p2c_native_plan_id"})
_LINKS = frozenset({"p2c_opportunity_id", "p2d_session_id", "p2e_session_id"})
_BINDING = frozenset({"track", "event_id", "source_id", "payload_sha256",
                      "observed_at", "available_at", "lineage_root_id",
                      "ledger_event_id", "source_revision"})


def _name(v: Any) -> bool:
    return isinstance(v, str) and bool(v.strip())


def _sha(v: Any) -> bool:
    return isinstance(v, str) and len(v) == 64 and all(c in "0123456789abcdef" for c in v)


def _instant(v: Any) -> datetime:
    if not isinstance(v, str):
        raise ValueError("OFFSET_TIME_REQUIRED")
    try:
        parsed = datetime.fromisoformat(v.replace("Z", "+00:00"))
        if parsed.tzinfo is None or parsed.utcoffset() is None:
            raise ValueError()
        return parsed.astimezone(timezone.utc)
    except (ValueError, OverflowError) as exc:
        raise ValueError("OFFSET_TIME_REQUIRED") from exc


_FORBIDDEN = frozenset({"future_outcome", "target_outcome", "heldout_label", "held_out_label",
                        "next_session_result", "outcome_label", "predicted_performance",
                        "performance_label", "training_benefit"})


def _no_future_labels(value: Any) -> None:
    if isinstance(value, dict):
        if _FORBIDDEN.intersection(value):
            raise ValueError("FUTURE_OUTCOME_LABEL_FORBIDDEN")
        for v in value.values():
            _no_future_labels(v)
    elif isinstance(value, list):
        for v in value:
            _no_future_labels(v)


def migrate_p2c_fixture(snapshot: dict[str, Any], plan_id: str) -> dict[str, Any]:
    """Add native plan identity to a COPY of an invented P2-C snapshot.

    Migration is mechanical and does not authenticate the caller's plan ID.
    """
    if not isinstance(snapshot, dict) or snapshot.get("synthetic_only") is not True or not _name(plan_id):
        raise ValueError("P2C_MIGRATION_SYNTHETIC_REQUIRED")
    if type(snapshot.get("plan_version")) is not int or snapshot["plan_version"] < 1:
        raise ValueError("P2C_MIGRATION_VERSION_REQUIRED")
    copied = deepcopy(snapshot)
    for section in ("opportunities", "events"):
        rows = copied.get(section)
        if not isinstance(rows, list) or any(not isinstance(r, dict) for r in rows):
            raise ValueError("P2C_MIGRATION_ROSTER_REQUIRED")
        for r in rows:
            if r.get("plan_id") not in (None, plan_id):
                raise ValueError("P2C_MIGRATION_CONFLICTING_NATIVE_ID")
            if r.get("plan_version") != copied["plan_version"]:
                raise ValueError("P2C_MIGRATION_VERSION_MISMATCH")
            r["plan_id"] = plan_id
    if copied.get("plan_id") not in (None, plan_id):
        raise ValueError("P2C_MIGRATION_CONFLICTING_NATIVE_ID")
    copied["plan_id"] = plan_id
    return copied


def _raw_events(snapshot: dict[str, Any], track: str) -> list[dict[str, Any]]:
    value = snapshot.get("events" if track == "p2c" else "observations")
    if not isinstance(value, list):
        raise ValueError("NATIVE_EVENTS_LIST_REQUIRED")
    if any(not isinstance(r, dict) for r in value):
        raise ValueError("MALFORMED_NATIVE_EVENT")
    return value


def assess(bundle: dict[str, Any], manifest: dict[str, Any],
           ledger_projection: dict[str, Any] | None = None) -> dict[str, Any]:
    """Review a canonical *invented* plan join; never return authority.

    Ledger projection must come from the separately vetted DEV-1 projector when
    used in the repository. This function does not attest that the provider,
    operator, planner or projection itself is independently trustworthy.
    """
    if not isinstance(bundle, dict) or bundle.get("synthetic_only") is not True:
        raise ValueError("SYNTHETIC_BUNDLE_REQUIRED")
    if not isinstance(manifest, dict) or set(manifest) != _ALLOWED or manifest.get("schema") != SCHEMA:
        raise ValueError("MANIFEST_SCHEMA_MISMATCH")
    _no_future_labels(bundle)
    _no_future_labels(manifest)
    if manifest.get("synthetic_only") is not True:
        raise ValueError("SYNTHETIC_MANIFEST_REQUIRED")
    for field in ("athlete_id", "opportunity_id"):
        if not _name(manifest.get(field)) or bundle.get(field) != manifest[field]:
            raise ValueError("MANIFEST_SCOPE_MISMATCH")
    if not _name(manifest.get("plan_id")):
        raise ValueError("MANIFEST_SCOPE_MISMATCH")
    if type(manifest.get("plan_version")) is not int or manifest["plan_version"] < 1:
        raise ValueError("PLAN_VERSION_INVALID")
    if not _sha(manifest.get("plan_revision_sha256")) or not _sha(manifest.get("ledger_tip")):
        raise ValueError("REVISION_OR_LEDGER_DIGEST_REQUIRED")
    if manifest.get("p2c_native_plan_id") is not None and (not _name(manifest["p2c_native_plan_id"]) or manifest["p2c_native_plan_id"] != manifest["plan_id"]):
        raise ValueError("P2C_PLAN_ID_CLAIM_MISMATCH")
    as_of = _instant(manifest.get("as_of"))
    if _instant(bundle.get("as_of")) != as_of:
        raise ValueError("MANIFEST_CUTOFF_MISMATCH")
    snaps = {}
    for track in TRACKS:
        s = bundle.get(track)
        if not isinstance(s, dict) or s.get("synthetic_only") is not True:
            raise ValueError("TRACK_SYNTHETIC_REQUIRED")
        if s.get("athlete_id") != manifest["athlete_id"] or _instant(s.get("as_of")) != as_of:
            raise ValueError("TRACK_SCOPE_OR_CUTOFF_MISMATCH")
        snaps[track] = s
    c, d, e = (snaps[k] for k in ("p2c", "p2d", "p2e"))
    dp, ep = d.get("plan"), e.get("plan")
    if not isinstance(dp, dict) or not isinstance(ep, dict):
        raise ValueError("NATIVE_PLAN_REQUIRED")
    plan_id, version = manifest["plan_id"], manifest["plan_version"]
    if (dp.get("plan_id") != plan_id or ep.get("id") != plan_id
            or type(c.get("plan_version")) is not int or c["plan_version"] != version
            or type(dp.get("version")) is not int or dp["version"] != version
            or type(ep.get("version")) is not int or ep["version"] != version):
        raise ValueError("CROSS_TRACK_PLAN_MISMATCH")
    if d.get("opportunity_id") != manifest["opportunity_id"] or e.get("opportunity_id") != manifest["opportunity_id"]:
        raise ValueError("CROSS_TRACK_OPPORTUNITY_MISMATCH")
    native_p2c_id = c.get("plan_id")
    if native_p2c_id is None:
        if manifest["p2c_native_plan_id"] is not None:
            raise ValueError("P2C_NATIVE_ID_CLAIM_UNBACKED")
        p2c_claim = "UNVERIFIED_MISSING_NATIVE_CONTRACT"
    else:
        if native_p2c_id != plan_id or manifest["p2c_native_plan_id"] != plan_id:
            raise ValueError("P2C_NATIVE_PLAN_ID_MISMATCH")
        for section in ("opportunities", "events"):
            values = c.get(section)
            if not isinstance(values, list) or any(not isinstance(r, dict) or r.get("plan_id") != plan_id for r in values):
                raise ValueError("P2C_NATIVE_PLAN_JOIN_INCOMPLETE")
        p2c_claim = "SYNTHETIC_NATIVE_ID_CONSISTENT_UNAUTHENTICATED"
    links = manifest.get("links")
    if not isinstance(links, dict) or set(links) != _LINKS or any(not _name(links[k]) for k in _LINKS):
        raise ValueError("LINK_SCHEMA_MISMATCH")
    opportunities = c.get("opportunities")
    if not isinstance(opportunities, list) or not any(isinstance(r, dict) and r.get("opportunity_id") == links["p2c_opportunity_id"] for r in opportunities):
        raise ValueError("P2C_LINK_NOT_FOUND")
    for track, plan, wanted, requested in (
        ("p2d", dp, links["p2d_session_id"], d.get("request", {}).get("session_id") if isinstance(d.get("request"), dict) else None),
        ("p2e", ep, links["p2e_session_id"], e.get("review_context", {}).get("target_session_id") if isinstance(e.get("review_context"), dict) else None),
    ):
        sessions = plan.get("sessions")
        if not isinstance(sessions, list) or not any(isinstance(r, dict) and r.get("id") == wanted for r in sessions) or requested != wanted:
            raise ValueError("TARGET_SESSION_LINK_MISMATCH_" + track.upper())
    bindings = manifest.get("evidence_bindings")
    if not isinstance(bindings, list):
        raise ValueError("EVIDENCE_BINDINGS_LIST_REQUIRED")
    native = {}
    for track in TRACKS:
        index = {}
        for record in _raw_events(snaps[track], track):
            event_id = record.get("event_id")
            if not _name(event_id):
                raise ValueError("NATIVE_EVENT_ID_INVALID")
            if event_id in index and index[event_id] != record:
                raise ValueError("CONFLICTING_NATIVE_EVENT_ID")
            index[event_id] = record
        native[track] = index
    seen = set()
    seen_ledger = set()
    validated = []
    excluded = Counter()
    for b in bindings:
        if not isinstance(b, dict) or set(b) != _BINDING or b.get("track") not in TRACKS:
            raise ValueError("EVIDENCE_BINDING_SCHEMA_MISMATCH")
        track, event_id = b["track"], b["event_id"]
        if (not _name(event_id) or not _name(b["source_id"])
                or not _name(b["lineage_root_id"]) or not _sha(b["payload_sha256"])
                or not _name(b["ledger_event_id"]) or type(b["source_revision"]) is not int
                or b["source_revision"] < 1):
            raise ValueError("EVIDENCE_BINDING_REQUIRED_FIELDS")
        key = (track, event_id)
        if key in seen:
            raise ValueError("DUPLICATE_EVIDENCE_BINDING")
        seen.add(key)
        if b["ledger_event_id"] in seen_ledger:
            raise ValueError("AMBIGUOUS_CROSS_TRACK_LEDGER_ID")
        seen_ledger.add(b["ledger_event_id"])
        record = native[track].get(event_id)
        if record is None or record.get("athlete_id") != manifest["athlete_id"] or record.get("source_id") != b["source_id"]:
            raise ValueError("NATIVE_EVIDENCE_LINK_MISMATCH")
        # P2-C's native events have a plan version only. Never infer a plan ID.
        if track == "p2c":
            if record.get("plan_version") != version or record.get("opportunity_id") != links["p2c_opportunity_id"]:
                raise ValueError("P2C_EVIDENCE_PLAN_MISMATCH")
        if track == "p2d" and record.get("session_id") != links["p2d_session_id"]:
            raise ValueError("P2D_EVIDENCE_SESSION_MISMATCH")
        if track == "p2e" and (record.get("plan_id") != plan_id or record.get("plan_version") != version or record.get("session_id") != links["p2e_session_id"]):
            raise ValueError("P2E_EVIDENCE_PLAN_MISMATCH")
        if "source_revision" in record and record["source_revision"] != b["source_revision"]:
            raise ValueError("SOURCE_REVISION_MISMATCH")
        observed = _instant(b["observed_at"])
        available = _instant(b["available_at"])
        if (_instant(record.get("observed_at")) != observed or _instant(record.get("available_at")) != available):
            raise ValueError("EVIDENCE_TIME_MISMATCH")
        if available < observed:
            raise ValueError("EVIDENCE_AVAILABILITY_BACKDATED")
        if observed > as_of or available > as_of:
            excluded["AFTER_DECISION_CUTOFF"] += 1
            continue
        if (not isinstance(record.get("status", "measured"), str)
                or record.get("status") in {"unknown", "missing_upload", "not_collected", "invalid", "withheld"}
                or ("quality" in record and record["quality"] != "good")):
            excluded["UNVERIFIED_NATIVE_RECORD"] += 1
            continue
        validated.append({"track": track, "event_id": event_id,
                          "ledger_event_id": b["ledger_event_id"],
                          "source_revision": b["source_revision"],
                          "source_id": b["source_id"],
                          "payload_sha256": b["payload_sha256"], "lineage_root_id": b["lineage_root_id"]})
    status = "SYNTHETIC_ASSERTED_CONSISTENCY_ONLY"
    if ledger_projection is None:
        status = "DEFER_LEDGER_PROJECTION_ABSENT"
    else:
        if not isinstance(ledger_projection, dict) or any(ledger_projection.get(k) != v for k, v in (
            ("athlete_id", manifest["athlete_id"]), ("plan_id", plan_id),
            ("plan_version", version), ("opportunity_id", manifest["opportunity_id"]),
        )):
            raise ValueError("LEDGER_PROJECTION_SCOPE_MISMATCH")
        if _instant(ledger_projection.get("as_of")) != as_of:
            raise ValueError("LEDGER_PROJECTION_CUTOFF_MISMATCH")
        if ledger_projection.get("ledger_tip") != manifest["ledger_tip"]:
            raise ValueError("LEDGER_TIP_MISMATCH")
        active = ledger_projection.get("active_records")
        if not isinstance(active, list) or any(not isinstance(r, dict) for r in active):
            raise ValueError("LEDGER_ACTIVE_RECORDS_INVALID")
        active_index = {}
        for r in active:
            if not _name(r.get("event_id")) or r["event_id"] in active_index:
                raise ValueError("LEDGER_DUPLICATE_ACTIVE_EVENT")
            active_index[r["event_id"]] = r
        for v in validated:
            r = active_index.get(v["ledger_event_id"])
            if r is None:
                excluded["NOT_ACTIVE_IN_LEDGER"] += 1
            elif r.get("source_id") != v["source_id"] or r.get("payload_digest") != v["payload_sha256"]:
                raise ValueError("LEDGER_BINDING_MISMATCH")
        if (ledger_projection.get("withdrawal_event_ids") or ledger_projection.get("revoked_sources")
                or ledger_projection.get("corrected_event_ids")):
            status = "DEFER_LEDGER_CORRECTION_WITHDRAWAL"
        elif excluded or len(validated) == 0 or any(v["ledger_event_id"] not in active_index for v in validated):
            status = "DEFER_UNVERIFIED_EVIDENCE"
    # Reused source lineage roots indicate potential correlation, not votes.
    root_counts = Counter(v["lineage_root_id"] for v in validated)
    correlated = sorted(k for k, n in root_counts.items() if n > 1)
    return {
        "schema": SCHEMA, "athlete_id": manifest["athlete_id"],
        "plan_id": plan_id, "plan_version": version,
        "opportunity_id": manifest["opportunity_id"], "as_of": as_of.isoformat(),
        "status": status, "identity_claim": "CALLER_ASSERTED_SYNTHETIC_ONLY",
        "p2c_native_plan_id": p2c_claim,
        "native_linkage": dict(sorted(links.items())),
        "evidence": sorted(validated, key=lambda x: (x["track"], x["event_id"])),
        "potentially_correlated_lineage_roots": correlated,
        "excluded_by_reason": dict(sorted(excluded.items())),
        "source_authentication": "NOT_AUTHENTICATED",
        "plan_revision_digest": manifest["plan_revision_sha256"],
        "ledger_tip": manifest["ledger_tip"],
        "safe_to_auto_apply": False, "can_write_plan": False,
        "authenticated_athlete_approval": False, "coaching_authority": "NONE_SYNTHETIC_RESEARCH",
        "gate2": "CLOSED",
    }
