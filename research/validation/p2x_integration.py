"""P2-X DEV-0: synthetic cross-track consistency audit, not a coach.

Four independent research contracts are replayed at the same athlete and
decision time. Plan identity cannot be fully joined: P2-C has a version but no
authenticated plan ID. Therefore this adapter never emits a training edit,
coaching recommendation, predictive estimate or approval.
"""
from __future__ import annotations

from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
from typing import Any

from p2a_state import interpret as athlete_state
from p2c_progression import review as progression_review
from p2d_shadow import shadow_review as adaptive_shadow
from p2e_coordination import review as multidiscipline_review

SCHEMA = "p2x-dev0-cross-track-v1"
TRACKS = ("p2a", "p2c", "p2d", "p2e")
FORBIDDEN = {
    "outcome", "future_outcome", "target_outcome", "outcome_label",
    "heldout_label", "held_out_label", "next_session_result",
    "predicted_performance", "training_benefit", "future_performance",
    "model_accuracy", "performance_label", "best_policy",
}


def _named(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _instant(value: Any) -> str:
    if not isinstance(value, str):
        raise ValueError("CROSS_TRACK_TIMESTAMP_REQUIRED")
    try:
        instant = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if instant.tzinfo is None or instant.utcoffset() is None:
            raise ValueError()
        return instant.astimezone(timezone.utc).isoformat()
    except (ValueError, OverflowError) as exc:
        raise ValueError("CROSS_TRACK_OFFSET_TIMESTAMP_REQUIRED") from exc


def _reject_future_labels(value: Any) -> None:
    if isinstance(value, dict):
        if FORBIDDEN.intersection(value):
            raise ValueError("CROSS_TRACK_OUTCOME_LEAKAGE")
        for child in value.values():
            _reject_future_labels(child)
    elif isinstance(value, list):
        for child in value:
            _reject_future_labels(child)


def _version(value: Any) -> bool:
    return type(value) is int and value > 0


def audit(bundle: dict[str, Any]) -> dict[str, Any]:
    """Fail closed for identity/version/cutoff mismatches; replay all tracks.

    The snapshots are invented test fixtures. Their matching string IDs do
    not prove actual provider identity, lawful admission or historical lineage.
    """
    if not isinstance(bundle, dict) or bundle.get("synthetic_only") is not True:
        raise ValueError("CROSS_TRACK_SYNTHETIC_ONLY")
    _reject_future_labels(bundle)
    athlete = bundle.get("athlete_id")
    opportunity = bundle.get("opportunity_id")
    if not _named(athlete) or not _named(opportunity):
        raise ValueError("CROSS_TRACK_SCOPE_REQUIRED")
    as_of = _instant(bundle.get("as_of"))
    snapshots = {}
    for name in TRACKS:
        s = bundle.get(name)
        if not isinstance(s, dict) or s.get("synthetic_only") is not True:
            raise ValueError("CROSS_TRACK_SNAPSHOT_SYNTHETIC_REQUIRED")
        if s.get("athlete_id") != athlete:
            raise ValueError("CROSS_TRACK_ATHLETE_MISMATCH")
        if _instant(s.get("as_of")) != as_of:
            raise ValueError("CROSS_TRACK_CUTOFF_MISMATCH")
        snapshots[name] = s
    c = snapshots["p2c"]
    d = snapshots["p2d"]
    e = snapshots["p2e"]
    dplan, eplan = d.get("plan"), e.get("plan")
    if not isinstance(dplan, dict) or not isinstance(eplan, dict):
        raise ValueError("CROSS_TRACK_PLAN_REQUIRED")
    if not _named(dplan.get("plan_id")) or not _named(eplan.get("id")):
        raise ValueError("CROSS_TRACK_PLAN_ID_REQUIRED")
    if dplan["plan_id"] != eplan["id"]:
        raise ValueError("CROSS_TRACK_PLAN_ID_MISMATCH")
    version = c.get("plan_version")
    if not _version(version) or dplan.get("version") != version or eplan.get("version") != version:
        raise ValueError("CROSS_TRACK_PLAN_VERSION_MISMATCH")
    if d.get("opportunity_id") != opportunity or e.get("opportunity_id") != opportunity:
        raise ValueError("CROSS_TRACK_OPPORTUNITY_MISMATCH")
    # Distinct native representations need explicit authenticated mapping.
    # P2-C currently has no plan_id; equality of a version is insufficient.
    a = athlete_state(snapshots["p2a"])
    c_result = progression_review(c)
    d_result = adaptive_shadow(d)
    e_result = multidiscipline_review(e)
    if any(r["athlete_id"] != athlete for r in (a, c_result, d_result, e_result)):
        raise ValueError("CROSS_TRACK_RESULT_IDENTITY_MISMATCH")
    if any(_instant(r["as_of"] if name != "p2d" else r["provenance"]["decision_as_of"]) != as_of
           for name, r in (("p2a", a), ("p2c", c_result), ("p2d", d_result), ("p2e", e_result))):
        raise ValueError("CROSS_TRACK_RESULT_CUTOFF_MISMATCH")
    if c_result["plan_version"] != version or d_result["provenance"]["plan_version"] != version or e_result["plan_version"] != version:
        raise ValueError("CROSS_TRACK_RESULT_VERSION_MISMATCH")
    if d_result["provenance"]["plan_id"] != dplan["plan_id"] or e_result["plan_id"] != eplan["id"]:
        raise ValueError("CROSS_TRACK_RESULT_PLAN_MISMATCH")
    # Every successful track result must remain unempowered and unmodified.
    if (a["predicted_performance"] is not None
            or a["proposed_workout_change"] is not None
            or c_result["applied"] is not False
            or c_result["plan_change"] is not None
            or d_result["applied"] is not False
            or d_result["plan_patch"] is not None
            or e_result["applied"] is not False
            or e_result["candidate_plan"] is not None):
        raise ValueError("CROSS_TRACK_AUTHORITY_VIOLATION")
    if any(r.get("coaching_authority") != "NONE_SYNTHETIC_RESEARCH" for r in (c_result, e_result)):
        raise ValueError("CROSS_TRACK_COACHING_AUTHORITY_VIOLATION")
    if any(r.get("gate2") != "CLOSED" for r in (c_result, d_result, e_result)):
        raise ValueError("CROSS_TRACK_GATE2_VIOLATION")
    if d_result.get("options") and any(option.get("applied") is not False
                                        or option.get("candidate_plan") is not None
                                        or option.get("proposed_dose") is not None
                                        for option in d_result["options"]):
        raise ValueError("CROSS_TRACK_OPTION_AUTHORITY_VIOLATION")
    c_category = c_result["review_category"]
    d_status = d_result["status"]
    e_category = e_result["review_category"]
    hard_boundary = (
        c_category == "DEFER_CLINICAL_REVIEW"
        or d_status in {"CLINICAL_BOUNDARY_DEFER", "STOP_REQUESTED"}
        or e_category in {"DEFER_STOP_REQUESTED", "DEFER_CLINICAL_CONTEXT", "DEFER_SYMPTOM_REVIEW"}
    )
    unverified = (
        c_category.startswith("DEFER_")
        or d_status == "ABSTAIN"
        or e_category.startswith("DEFER_")
        or e_category == "REVIEW_CAPACITY_CONFLICT"
    )
    if hard_boundary:
        review_status = "DEFER_SAFETY_BOUNDARY"
    elif unverified:
        review_status = "DEFER_MISSING_OR_CONFLICTING_EVIDENCE"
    elif e_category.startswith("REVIEW_"):
        review_status = "REVIEW_CONCURRENT_CONSTRAINTS"
    else:
        review_status = "STRUCTURAL_COMPARISON_ONLY"
    return {
        "schema": SCHEMA,
        "athlete_id": athlete,
        "opportunity_id": opportunity,
        "as_of": as_of,
        "common_plan_version": version,
        "p2d_p2e_asserted_plan_id": dplan["plan_id"],
        "p2c_plan_lineage": "UNVERIFIED_NO_P2C_PLAN_ID",
        "cross_track_source_linkage": "NOT_AUTHENTICATED",
        "review_status": review_status,
        "track_statuses": {
            "p2a": {
                "fitness_trend": a["fitness_trend"]["state"],
                "recent_fatigue": a["recent_fatigue"]["state"],
                "workout_tolerance": a["workout_tolerance"]["state"],
            },
            "p2c": c_category,
            "p2d": d_status,
            "p2e": e_category,
        },
        "track_reasons": {
            "p2c": c_result["reason"],
            "p2d": d_result["reason"],
            "p2e": e_result["reason"],
        },
        "source_evidence_ids": {
            "p2a": sorted(set(a["fitness_trend"]["evidence_ids"]
                              + a["recent_fatigue"]["evidence_ids"]
                              + a["workout_tolerance"]["evidence_ids"])),
            "p2c": sorted(set(x for r in c_result["exposure"]["roster"] for x in r["evidence_ids"])),
            "p2d": sorted(d_result["provenance"]["decision_evidence_ids"]),
            "p2e": sorted(e_result["source_evidence_ids"]),
        },
        "safe_to_auto_apply": False,
        "selected_action": None,
        "plan_patch": None,
        "dose_change": None,
        "predictive_skill": None,
        "effectiveness": None,
        "clinical_clearance": False,
        "applied": False,
        "coaching_authority": "NONE_SYNTHETIC_RESEARCH",
        "gate2": "CLOSED",
    }


def compare(bundles: list[dict[str, Any]]) -> dict[str, Any]:
    """Matched opportunity count across tracks, with no empirical ranking."""
    if not isinstance(bundles, list):
        raise ValueError("CROSS_TRACK_BUNDLES_LIST_REQUIRED")
    seen: set[tuple[str, str]] = set()
    records = []
    for b in bundles:
        row = audit(b)
        identity = (row["athlete_id"], row["opportunity_id"])
        if identity in seen:
            raise ValueError("CROSS_TRACK_DUPLICATE_OPPORTUNITY")
        seen.add(identity)
        records.append(row)
    records.sort(key=lambda x: (x["athlete_id"], x["opportunity_id"]))
    counts = dict(sorted(Counter(r["review_status"] for r in records).items()))
    return {
        "schema": "p2x-dev0-matched-review-v1",
        "scope": "SYNTHETIC_STRUCTURAL_CONFORMANCE_ONLY",
        "same_denominator": len(records),
        "track_counts": {name: len(records) for name in TRACKS},
        "review_status_counts": counts,
        "cases": records,
        "best_track": None,
        "training_effectiveness": None,
        "auto_plan_write": False,
        "coaching_authority": "NONE_SYNTHETIC_RESEARCH",
        "gate2": "CLOSED",
    }
