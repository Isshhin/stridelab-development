"""P2-E DEV-0 synthetic-only native multi-discipline exposure ledger.

No source authentication, real data admission, policy training, medical triage,
fitness estimate or workout changes. Pure function; no I/O or side effects.
"""
from __future__ import annotations

from collections import Counter, defaultdict
from copy import deepcopy
from datetime import datetime, timezone
from math import isfinite
from typing import Any

SCHEMA = "p2e-dev0-native-exposure-v1"
DISCIPLINES = {"running", "strength", "calisthenics", "conditioning"}
EVENT_STATUSES = {"completed", "partial", "confirmed_skipped", "unknown"}
RUN_INTENTS = {"easy", "threshold", "intervals", "long_run", "recovery"}
ROLES = {"upper", "lower", "core", "full_body"}
OBSERVATION_LABELS = {"outcome", "future_outcome", "target_outcome",
                      "prediction", "next_session_result", "heldout_label"}


def _name(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _instant(value: Any) -> datetime:
    if not isinstance(value, str):
        raise ValueError("OFFSET_TIMESTAMP_REQUIRED")
    try:
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if dt.tzinfo is None or dt.utcoffset() is None:
            raise ValueError()
        return dt.astimezone(timezone.utc)
    except (ValueError, OverflowError) as exc:
        raise ValueError("INVALID_OFFSET_TIMESTAMP") from exc


def _number(value: Any) -> bool:
    try:
        return type(value) in (int, float) and isfinite(value) and value >= 0
    except OverflowError:
        return False


def _integer(value: Any) -> bool:
    return type(value) is int and value >= 0


def _reject_labels(value: Any) -> None:
    if isinstance(value, dict):
        if OBSERVATION_LABELS.intersection(value):
            raise ValueError("OUTCOME_LABEL_LEAKAGE")
        for child in value.values():
            _reject_labels(child)
    elif isinstance(value, list):
        for child in value:
            _reject_labels(child)


def _native_dose(discipline: str, dose: Any) -> dict:
    if not isinstance(dose, dict):
        raise ValueError("NATIVE_DOSE_REQUIRED")
    if discipline == "running":
        for key in ("km", "easy_s", "hard_s", "recovery_s"):
            if not ((_number(dose.get(key)) if key == "km"
                     else _integer(dose.get(key)))):
                raise ValueError("RUN_NATIVE_DOSE_INVALID")
        return {key: dose[key] for key in ("km", "easy_s", "hard_s", "recovery_s")}
    if discipline == "strength":
        if not _name(dose.get("exercise_id")) or not _integer(dose.get("sets")) \
                or not _integer(dose.get("reps")) or not _number(dose.get("load_kg")):
            raise ValueError("STRENGTH_NATIVE_DOSE_INVALID")
        rir = dose.get("rir")
        if rir is not None and (not _integer(rir) or rir > 10):
            raise ValueError("STRENGTH_RIR_INVALID")
        roles = dose.get("muscle_roles")
        if not isinstance(roles, list) or any(not isinstance(r, str) or r not in ROLES
                                               for r in roles) or len(roles) != len(set(roles)):
            raise ValueError("STRENGTH_MUSCLE_ROLES_INVALID")
        if dose.get("reported_demand") not in ("high", "moderate", "low", "unknown"):
            raise ValueError("STRENGTH_DEMAND_INVALID")
        return {"exercise_id": dose["exercise_id"], "sets": dose["sets"],
                "reps": dose["reps"], "load_kg": dose["load_kg"], "rir": rir,
                "muscle_roles": sorted(roles),
                "reported_demand": dose["reported_demand"]}
    if discipline == "calisthenics":
        if not _name(dose.get("skill_node")) or not _integer(dose.get("reps")) \
                or not _integer(dose.get("hold_s")):
            raise ValueError("CALISTHENICS_NATIVE_DOSE_INVALID")
        if dose.get("form_quality") not in ("good", "poor", "unknown") \
                or dose.get("prerequisites_verified") not in (True, False, None) \
                or dose.get("muscle_region") not in ("upper", "lower", "core", "unknown") \
                or dose.get("reported_demand") not in ("high", "moderate", "low", "unknown"):
            raise ValueError("CALISTHENICS_CONTEXT_INVALID")
        return {key: dose[key] for key in ("skill_node", "reps", "hold_s",
                "form_quality", "prerequisites_verified", "muscle_region",
                "reported_demand")}
    if discipline == "conditioning":
        if not _integer(dose.get("rounds")) or not _integer(dose.get("work_s")) \
                or not _integer(dose.get("rest_s")):
            raise ValueError("CONDITIONING_NATIVE_DOSE_INVALID")
        impacts = dose.get("landing_count")
        if impacts is not None and not _integer(impacts):
            raise ValueError("CONDITIONING_IMPACTS_INVALID")
        if dose.get("impact_profile") not in ("jump_heavy", "low_impact", "unknown") \
                or dose.get("reported_demand") not in ("high", "moderate", "low", "unknown"):
            raise ValueError("CONDITIONING_CONTEXT_INVALID")
        return {"rounds": dose["rounds"], "work_s": dose["work_s"],
                "rest_s": dose["rest_s"], "landing_count": impacts,
                "impact_profile": dose["impact_profile"],
                "reported_demand": dose["reported_demand"]}
    raise ValueError("UNSUPPORTED_DISCIPLINE")


def summarize(snapshot: dict[str, Any]) -> dict[str, Any]:
    """Deterministically reconcile invented source events with a single plan."""
    if not isinstance(snapshot, dict) or snapshot.get("synthetic_only") is not True:
        raise ValueError("SYNTHETIC_ONLY_REQUIRED")
    if not _name(snapshot.get("athlete_id")) or not _name(snapshot.get("opportunity_id")):
        raise ValueError("SCOPED_ID_REQUIRED")
    _reject_labels(snapshot)
    as_of = _instant(snapshot.get("as_of"))
    plan = snapshot.get("plan")
    if not isinstance(plan, dict) or type(plan.get("version")) is not int \
            or plan["version"] < 1 or not _name(plan.get("id")):
        raise ValueError("PLAN_VERSION_OR_ID_INVALID")
    if plan.get("athlete_id") != snapshot["athlete_id"]:
        raise ValueError("FOREIGN_PLAN")
    sessions = plan.get("sessions")
    if not isinstance(sessions, list):
        raise ValueError("SESSIONS_LIST_REQUIRED")
    planned: dict[str, dict] = {}
    for session in sessions:
        if not isinstance(session, dict) or not _name(session.get("id")):
            raise ValueError("INVALID_SESSION_ID")
        if session["id"] in planned:
            raise ValueError("DUPLICATE_PLANNED_SESSION")
        if session.get("discipline") not in DISCIPLINES:
            raise ValueError("INVALID_DISCIPLINE")
        if not _integer(session.get("duration_min")):
            raise ValueError("PLANNED_DURATION_INVALID")
        scheduled = _instant(session.get("scheduled_at"))
        if session["discipline"] == "running" and session.get("intent") not in RUN_INTENTS:
            raise ValueError("RUN_INTENT_REQUIRED")
        local_date = datetime.fromisoformat(
            session["scheduled_at"].replace("Z", "+00:00")).date().isoformat()
        planned[session["id"]] = {**deepcopy(session), "_scheduled": scheduled,
                                   "_local_date": local_date}
    events = snapshot.get("observations")
    if not isinstance(events, list):
        raise ValueError("OBSERVATIONS_LIST_REQUIRED")
    identities: dict[str, dict] = {}
    duplicates = 0
    for e in events:
        if not isinstance(e, dict) or not _name(e.get("event_id")):
            raise ValueError("INVALID_EVENT_ID")
        if e.get("athlete_id") != snapshot["athlete_id"] or e.get("plan_id") != plan["id"]:
            raise ValueError("CROSS_IDENTITY_EVENT")
        if e.get("plan_version") != plan["version"]:
            raise ValueError("STALE_OR_FOREIGN_PLAN_EVENT")
        if e["event_id"] in identities:
            if identities[e["event_id"]] != e:
                raise ValueError("CONFLICTING_EVENT_ID")
            duplicates += 1
        else:
            identities[e["event_id"]] = deepcopy(e)
    groups: dict[str, list] = defaultdict(list)
    excluded: Counter[str] = Counter()
    for e in (identities[k] for k in sorted(identities)):
        sid = e.get("session_id")
        if not _name(sid) or sid not in planned:
            excluded["UNLINKED_OR_UNKNOWN_SESSION"] += 1
            continue
        observed, available = _instant(e.get("observed_at")), _instant(e.get("available_at"))
        if observed > as_of or available > as_of:
            excluded["UNAVAILABLE_AT_DECISION"] += 1
            continue
        if available < observed:
            excluded["INCONSISTENT_AVAILABILITY"] += 1
            continue
        if observed < planned[sid]["_scheduled"]:
            excluded["BEFORE_VERSIONED_SESSION"] += 1
            continue
        if not _name(e.get("source_id")):
            excluded["MISSING_SOURCE_ID"] += 1
            continue
        if e.get("link_status") != "synthetic_verified":
            excluded["LINK_NOT_SYNTHETICALLY_VERIFIED"] += 1
            continue
        if e.get("quality") != "good":
            excluded["LOW_QUALITY"] += 1
            continue
        if e.get("status") not in EVENT_STATUSES:
            excluded["INVALID_EVENT_STATUS"] += 1
            continue
        if e.get("discipline") != planned[sid]["discipline"]:
            excluded["DISCIPLINE_MISMATCH"] += 1
            continue
        if e.get("status") in ("unknown", "confirmed_skipped"):
            if e.get("dose") is not None:
                raise ValueError("NONEXPOSURE_HAS_DOSE")
            if e["status"] == "confirmed_skipped" and not _name(e.get("reason")):
                raise ValueError("SKIP_REASON_REQUIRED")
            actual = None
        else:
            actual = _native_dose(e["discipline"], e.get("dose"))
        if e.get("symptom_report", "unknown") not in ("pain", "none", "unknown"):
            excluded["INVALID_SYMPTOM_REPORT"] += 1
            continue
        groups[sid].append({**deepcopy(e), "_observed": observed,
                            "native_dose": actual})
    roster = []
    totals = {"running_km": 0.0, "running_easy_s": 0,
              "running_hard_s": 0, "running_recovery_s": 0,
              "strength_sets": 0, "calis_reps": 0, "calis_hold_s": 0,
              "conditioning_rounds": 0, "conditioning_work_s": 0,
              "conditioning_rest_s": 0}
    incomplete = False
    for sid in sorted(planned):
        s = planned[sid]
        recs = groups.get(sid, [])
        row = {"session_id": sid, "discipline": s["discipline"],
                "intent": s.get("intent"), "scheduled_at": s["_scheduled"].isoformat(),
               "scheduled_local_date": s["_local_date"],
               "duration_min": s["duration_min"], "status": "unknown",
               "evidence_ids": [], "observed_at": None, "native_dose": None,
               "symptom_report": "unknown"}
        if len(recs) == 1:
            e = recs[0]
            row.update(status=e["status"], evidence_ids=[e["event_id"]],
                       observed_at=e["_observed"].isoformat(),
                       native_dose=e["native_dose"],
                       symptom_report=e.get("symptom_report", "unknown"))
            if e["status"] in ("completed", "partial"):
                v = e["native_dose"]
                if s["discipline"] == "running":
                    for name, field in (("running_km", "km"), ("running_easy_s", "easy_s"),
                                        ("running_hard_s", "hard_s"),
                                        ("running_recovery_s", "recovery_s")):
                        totals[name] += v[field]
                elif s["discipline"] == "strength":
                    totals["strength_sets"] += v["sets"]
                elif s["discipline"] == "calisthenics":
                    totals["calis_reps"] += v["reps"]
                    totals["calis_hold_s"] += v["hold_s"]
                else:
                    for name, field in (("conditioning_rounds", "rounds"),
                                        ("conditioning_work_s", "work_s"),
                                        ("conditioning_rest_s", "rest_s")):
                        totals[name] += v[field]
        elif len(recs) > 1:
            row.update(status="conflict", evidence_ids=sorted(r["event_id"] for r in recs))
        # Unobserved future sessions do not count as incomplete past exposure.
        if (s["_scheduled"] <= as_of and row["status"] in ("unknown", "conflict")):
            incomplete = True
        roster.append(row)
    return {"schema": SCHEMA, "athlete_id": snapshot["athlete_id"],
            "opportunity_id": snapshot["opportunity_id"], "as_of": as_of.isoformat(),
            "plan_id": plan["id"], "plan_version": plan["version"],
            "roster": roster, "known_native_totals": totals,
            "past_coverage_complete": not incomplete,
            "duplicate_inputs": duplicates,
            "excluded_by_reason": dict(sorted(excluded.items())),
            "unlinked_or_conflicted_sessions": sorted(r["session_id"] for r in roster
                                                   if r["status"] == "conflict"),
            "coaching_authority": "NONE_SYNTHETIC_RESEARCH", "gate2": "CLOSED"}
