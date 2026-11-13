"""Pure in-memory research ledger for proposed plan changes.

Not Android persistence, not a database transaction, not tested for crash safety.
All public functions are copy-on-write and require explicit approval and version
matching. Completed activity observations are never altered by plan revisions.
"""
from __future__ import annotations

from copy import deepcopy
from typing import Any


class RevisionConflict(ValueError):
    pass


def new_state(plan: dict[str, Any],
              observed_activities: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "current_version": 1,
        "plan": deepcopy(plan),
        "observed_activities": deepcopy(observed_activities),
        "pending": {},
        "history": [],
    }


def _version_matches(state: dict[str, Any], expected_version: int) -> None:
    if state["current_version"] != expected_version:
        raise RevisionConflict(
            f"Stale revision: expected={expected_version}, actual={state['current_version']}")


def propose(state: dict[str, Any], proposal_id: str,
            replacement_plan: dict[str, Any], reason: str,
            *, expected_version: int, policy_id: str) -> dict[str, Any]:
    """Register a proposed plan diff without applying it."""
    _version_matches(state, expected_version)
    if not proposal_id or not reason or not policy_id:
        raise ValueError("Proposal identity, explanation and policy provenance required")
    if proposal_id in state["pending"] or any(
            record.get("proposal_id") == proposal_id for record in state["history"]):
        raise RevisionConflict("Proposal ID already used")
    copy = deepcopy(state)
    copy["pending"][proposal_id] = {
        "base_version": state["current_version"],
        "before": deepcopy(state["plan"]),
        "candidate": deepcopy(replacement_plan),
        "reason": reason,
        "policy_id": policy_id,
        "status": "pending",
    }
    return copy


def decide(state: dict[str, Any], proposal_id: str,
           decision: str, *, expected_version: int) -> dict[str, Any]:
    """Apply only an explicit approved proposal; rejected proposals do not alter plan."""
    _version_matches(state, expected_version)
    if decision not in {"approve", "reject"}:
        raise ValueError("Approval or rejection required")
    if proposal_id not in state["pending"]:
        raise RevisionConflict("No pending proposal to decide")
    pending = state["pending"][proposal_id]
    if pending["base_version"] != state["current_version"]:
        raise RevisionConflict("Proposal based on stale plan version")
    if pending["before"] != state["plan"]:
        raise RevisionConflict("Plan changed since proposal was created")
    copy = deepcopy(state)
    entry = copy["pending"].pop(proposal_id)
    if decision == "approve":
        copy["plan"] = deepcopy(entry["candidate"])
        copy["current_version"] += 1
        entry["status"] = "approved"
    else:
        entry["status"] = "rejected"
    entry["proposal_id"] = proposal_id
    entry["resolved_at_version"] = copy["current_version"]
    copy["history"].append(entry)
    return copy


def rollback(state: dict[str, Any], proposal_id: str,
             *, expected_version: int) -> dict[str, Any]:
    """Revert only the latest approved revision with an additive audit entry."""
    _version_matches(state, expected_version)
    if state["pending"]:
        raise RevisionConflict("Resolve pending proposals before rollback")
    history = state["history"]
    if not history:
        raise RevisionConflict("No revisions to roll back")
    approved = [entry for entry in history if entry["status"] == "approved"]
    if not approved:
        raise RevisionConflict("No approved revision to roll back")
    target = approved[-1]
    if target["proposal_id"] != proposal_id:
        raise RevisionConflict("Only the latest approved revision may be rolled back")
    if target["resolved_at_version"] != state["current_version"]:
        raise RevisionConflict("Approved revision is not the current plan version")
    if state["plan"] != target["candidate"]:
        raise RevisionConflict("Current plan does not match approved revision")
    copy = deepcopy(state)
    before = deepcopy(copy["plan"])
    copy["plan"] = deepcopy(target["before"])
    copy["current_version"] += 1
    copy["history"].append({
        "status": "rollback",
        "proposal_id": proposal_id,
        "before": before,
        "candidate": deepcopy(copy["plan"]),
        "resolved_at_version": copy["current_version"],
        "reason": "EXPLICIT_ROLLBACK",
    })
    return copy
