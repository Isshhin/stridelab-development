"""Synthetic reference for the additive F2.2 exposure-ledger contract.

This module proves deterministic contract behavior only. It is not a durable
store, source authenticator, consent service, device importer or P2-B release.
"""
from copy import deepcopy
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import math
import re


SCHEMA = "F2.2-reference.1"
NULL_STATUSES = {"not_collected", "missing_upload", "unknown", "invalid",
                 "conflict", "not_applicable", "withheld", "withdrawn"}
COMPLETION = {"completed", "partial", "confirmed_skipped", "substituted", "unknown"}
OWNERSHIP = {"planned_by_stridelab", "planned_externally",
             "observed_external_training", "unplanned_or_unknown_intent"}


class Quarantine(ValueError):
    """The requested event set cannot produce an eligible projection."""


def require(condition, message="contract violation"):
    if not condition:
        raise Quarantine(message)


def ident(value):
    require(isinstance(value, str) and bool(value.strip()), "missing identity")
    return value


def instant(value):
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        require(parsed.utcoffset() is not None, "timestamp needs offset")
        return parsed.astimezone(timezone.utc)
    except (AttributeError, TypeError, ValueError, OverflowError) as error:
        raise Quarantine("invalid timestamp") from error


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)


def digest(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def observation(value):
    require(isinstance(value, dict) and set(value) == {"status", "value", "unit", "method"},
            "invalid observation shape")
    status = value["status"]
    require(status == "measured" or status in NULL_STATUSES, "invalid observation status")
    require(isinstance(value["unit"], str) and bool(value["unit"]), "missing observation unit")
    if status == "measured":
        require(type(value["value"]) in (int, float) and math.isfinite(value["value"]),
                "measured value must be finite")
        require(isinstance(value["method"], str) and bool(value["method"]),
                "measured value needs method")
    else:
        require(value["value"] is None and value["method"] is None,
                "reason-coded null cannot carry a value")
    return deepcopy(value)


@dataclass(frozen=True)
class Projection:
    account_id: str
    cutoff: str
    generation: int
    data: dict


class FoundationLedger:
    """In-memory, account-partitioned F2.2 reference projector."""

    def __init__(self):
        self._events = {}
        self._entities = {name: {} for name in ("opportunities", "revisions", "activities", "sources")}
        self._aliases = {}
        self._digests = {}
        self._withdrawn = {}
        self._generation = {}

    def _key(self, account, entity):
        return ident(account), ident(entity)

    def _known(self, table, account, entity):
        return self._key(account, entity) in self._entities[table]

    def append(self, events):
        """Append trusted normalized events atomically; retries are idempotent."""
        require(isinstance(events, (list, tuple)), "events must be a sequence")
        trial_events = deepcopy(self._events)
        trial_entities = deepcopy(self._entities)
        trial_aliases = deepcopy(self._aliases)
        trial_digests = deepcopy(self._digests)
        changed_accounts = set()

        for raw in deepcopy(events):
            try:
                require(isinstance(raw, dict) and set(raw) == {"id", "account_id", "kind", "recorded_at",
                                     "effective_at", "available_at", "data"},
                        "invalid event envelope")
                event_id, account, kind = ident(raw["id"]), ident(raw["account_id"]), ident(raw["kind"])
                require(account not in self._withdrawn, "account withdrawn")
                recorded, effective, available = (instant(raw[name]) for name in
                                                  ("recorded_at", "effective_at", "available_at"))
                require(recorded <= available and effective <= available, "invalid event chronology")
                key = account, event_id
                require(key not in trial_events or trial_events[key] == raw, "event identity collision")
                if key in trial_events:
                    continue
                data = raw["data"]
                require(isinstance(data, dict), "invalid event data")
                self._validate_event(account, kind, data, available, trial_entities,
                                     trial_aliases, trial_digests)
                trial_events[key] = raw
                changed_accounts.add(account)
            except KeyError as error:
                raise Quarantine("missing event field") from error

        self._events = trial_events
        self._entities = trial_entities
        self._aliases = trial_aliases
        self._digests = trial_digests
        for account in changed_accounts:
            self._generation[account] = self._generation.get(account, 0) + 1

    def _validate_event(self, account, kind, data, available, entities, aliases, digests):
        def known(table, entity):
            return (account, ident(entity)) in entities[table]

        if kind == "plan_revision":
            opportunity = ident(data["opportunity_id"])
            revision = ident(data["revision_id"])
            parent = data["parent_revision_id"]
            ident(data["intent"])
            observation(data["planned_duration"])
            if parent is None:
                require((account, opportunity) not in entities["opportunities"], "duplicate opportunity root")
                entities["opportunities"][(account, opportunity)] = revision
            else:
                require(known("revisions", parent), "unknown parent revision")
                require(entities["opportunities"].get((account, opportunity)) == parent,
                        "stale revision parent")
                entities["opportunities"][(account, opportunity)] = revision
            require(not known("revisions", revision), "duplicate revision")
            entities["revisions"][(account, revision)] = opportunity
        elif kind == "delivery":
            require(known("opportunities", data["opportunity_id"]) and
                    entities["revisions"].get((account, data["revision_id"])) == data["opportunity_id"],
                    "unknown delivered revision")
        elif kind == "activity":
            activity = ident(data["activity_id"])
            require(not known("activities", activity), "duplicate activity")
            start, end = instant(data["started_at"]), instant(data["ended_at"])
            require(start <= end, "invalid activity interval")
            ident(data["discipline"])
            require(data["intent"] == "unknown" or isinstance(data["intent"], str) and data["intent"].strip(),
                    "invalid intent")
            require(data["plan_ownership"] in OWNERSHIP and data["completion"] in COMPLETION,
                    "invalid activity classification")
            if "performed_duration" in data:
                observation(data["performed_duration"])
            if "offline_created_at" in data:
                require(instant(data["offline_created_at"]) <= available, "invalid offline time")
            entities["activities"][(account, activity)] = available
        elif kind in {"source", "source_correction"}:
            source, activity = ident(data["source_id"]), ident(data["activity_id"])
            require(known("activities", activity) and not known("sources", source),
                    "unknown activity or duplicate source")
            require(entities["activities"][(account, activity)] <= available,
                    "source available before canonical activity")
            alias = data["alias"]
            require(isinstance(alias, list) and len(alias) == 2 and all(ident(x) for x in alias),
                    "invalid source alias")
            alias_key = account, tuple(alias)
            require(alias_key not in aliases, "duplicate alias")
            payload_digest = data["digest"]
            require(isinstance(payload_digest, str) and re.fullmatch(r"[0-9a-f]{64}", payload_digest),
                    "invalid source digest")
            require((account, payload_digest) not in digests or
                    digests[(account, payload_digest)] == activity, "digest assigned to another activity")
            active_for_activity = [key for key, owner in entities["sources"].items()
                                   if key[0] == account and owner == activity]
            if kind == "source":
                require(not active_for_activity or ident(data.get("alias_evidence_id")),
                        "second source needs alias adjudication")
            else:
                previous = ident(data["supersedes_source_id"])
                require(known("sources", previous) and entities["sources"][(account, previous)] == activity,
                        "unknown corrected source")
                ident(data["correction_evidence_id"])
            measurements = data["measurements"]
            require(isinstance(measurements, dict), "invalid measurements")
            for metric, value in measurements.items():
                ident(metric); observation(value)
            entities["sources"][(account, source)] = activity
            aliases[alias_key] = activity
            digests[(account, payload_digest)] = activity
        elif kind == "link":
            activity, opportunity, revision = (ident(data[name]) for name in
                                                ("activity_id", "opportunity_id", "revision_id"))
            require(known("activities", activity) and known("opportunities", opportunity) and
                    entities["revisions"].get((account, revision)) == opportunity,
                    "cross-account or unresolved link")
            ident(data["evidence_id"])
        else:
            raise Quarantine("unsupported event kind")

    def withdraw(self, account_id, reason):
        account = ident(account_id)
        ident(reason)
        self._withdrawn[account] = reason
        self._generation[account] = self._generation.get(account, 0) + 1

    def assert_current(self, projection):
        require(isinstance(projection, Projection), "invalid projection")
        require(projection.account_id not in self._withdrawn and
                projection.generation == self._generation.get(projection.account_id, 0),
                "projection invalidated")
        return projection

    def replay(self, account_id, cutoff):
        account = ident(account_id)
        require(account not in self._withdrawn, "account withdrawn")
        stop = instant(cutoff)
        rows = [deepcopy(row) for (owner, _), row in self._events.items()
                if owner == account and instant(row["available_at"]) <= stop]
        rows.sort(key=lambda row: (instant(row["available_at"]), row["id"]))
        data = self._project(account, rows)
        data["schema"] = SCHEMA
        data["event_ids"] = [row["id"] for row in rows]
        data["event_set_digest"] = digest(rows)
        return Projection(account, stop.isoformat(), self._generation.get(account, 0), data)

    def _project(self, account, rows):
        revisions, opportunities, deliveries, activities, sources, invalidated = {}, {}, {}, {}, {}, set()
        links = {}
        for row in rows:
            kind, data = row["kind"], row["data"]
            if kind == "plan_revision":
                opportunity, revision = data["opportunity_id"], data["revision_id"]
                if data["parent_revision_id"] is not None:
                    require(opportunities.get(opportunity, {}).get("active_revision") == data["parent_revision_id"],
                            "stale revision")
                revisions[revision] = deepcopy(data)
                opportunities.setdefault(opportunity, {"revision_ids": [], "delivered_revision": None,
                                                        "activity_ids": []})
                opportunities[opportunity]["revision_ids"].append(revision)
                opportunities[opportunity]["active_revision"] = revision
            elif kind == "delivery":
                opportunity, revision = data["opportunity_id"], data["revision_id"]
                require(revision in revisions and revisions[revision]["opportunity_id"] == opportunity,
                        "unresolved delivery")
                deliveries.setdefault(opportunity, []).append((instant(row["available_at"]), revision))
                opportunities[opportunity]["delivered_revision"] = revision
            elif kind == "activity":
                activity = data["activity_id"]
                performed = data.get("performed_duration", {"status": "unknown", "value": None,
                                                             "unit": "s", "method": None})
                activities[activity] = {"started_at": data["started_at"], "ended_at": data["ended_at"],
                    "discipline": data["discipline"], "intent": data["intent"],
                    "plan_ownership": data["plan_ownership"], "completion": data["completion"],
                    "occurrence_basis": data.get("occurrence_basis", "observed"),
                    "performed_duration": observation(performed), "source_ids": [],
                    "invalidated_source_ids": [], "measurements": {}, "link_ids": []}
            elif kind in {"source", "source_correction"}:
                source = data["source_id"]
                if kind == "source_correction":
                    invalidated.add(data["supersedes_source_id"])
                sources[source] = {"activity_id": data["activity_id"],
                                   "measurements": deepcopy(data["measurements"]),
                                   "alias": deepcopy(data["alias"]), "event_id": row["id"]}
            elif kind == "link":
                activity, opportunity, revision = (data[name] for name in
                                                    ("activity_id", "opportunity_id", "revision_id"))
                require(activity in activities and opportunity in opportunities and revision in revisions,
                        "unresolved link")
                start = instant(activities[activity]["started_at"])
                eligible = [(at, rev) for at, rev in deliveries.get(opportunity, []) if at < start]
                require(eligible and eligible[-1][1] == revision, "stale or unavailable delivered revision")
                links[row["id"]] = {"activity_id": activity, "opportunity_id": opportunity,
                                    "revision_id": revision, "evidence_id": data["evidence_id"]}
                activities[activity]["link_ids"].append(row["id"])
                opportunities[opportunity]["activity_ids"].append(activity)

        for source_id, source in sources.items():
            activity = activities[source["activity_id"]]
            if source_id in invalidated:
                activity["invalidated_source_ids"].append(source_id)
            else:
                activity["source_ids"].append(source_id)
        for activity in activities.values():
            activity["source_ids"].sort()
            activity["invalidated_source_ids"].sort()
            metrics = {}
            for source_id in activity["source_ids"]:
                for metric, value in sources[source_id]["measurements"].items():
                    metrics.setdefault(metric, []).append((source_id, observation(value)))
            for metric, values in metrics.items():
                first = values[0][1]
                if all(item == first for _, item in values[1:]):
                    activity["measurements"][metric] = {**first, "source_ids": [sid for sid, _ in values]}
                else:
                    activity["measurements"][metric] = {"status": "conflict", "value": None,
                        "unit": first["unit"], "method": None, "source_ids": [sid for sid, _ in values]}
        return {"opportunities": opportunities, "revisions": revisions, "activities": activities,
                "links": links, "exposure_count": len(activities),
                "exclusions": []}


def validate_trace_segment(segment):
    """Validate the minimum format-neutral trace contract for a pause-free segment."""
    required = {"account_id", "activity_id", "segment_id", "source_ids", "source_digest",
                "parser_version", "device_epoch", "sensor_epoch", "start_at", "end_at",
                "distance_method", "timer_basis", "samples", "max_gap_s"}
    require(isinstance(segment, dict) and set(segment) == required, "invalid trace contract")
    for field in ("account_id", "activity_id", "segment_id", "parser_version", "device_epoch",
                  "sensor_epoch", "distance_method", "timer_basis"):
        ident(segment[field])
    require(isinstance(segment["source_ids"], list) and segment["source_ids"] and
            len(set(segment["source_ids"])) == len(segment["source_ids"]) and
            all(ident(value) for value in segment["source_ids"]), "invalid trace sources")
    require(isinstance(segment["source_digest"], str) and
            re.fullmatch(r"[0-9a-f]{64}", segment["source_digest"]), "invalid trace digest")
    start, end = instant(segment["start_at"]), instant(segment["end_at"])
    require(start < end and type(segment["max_gap_s"]) in (int, float) and segment["max_gap_s"] > 0,
            "invalid trace bounds")
    samples = segment["samples"]
    require(isinstance(samples, list) and len(samples) >= 2, "insufficient trace samples")
    previous_time, previous_distance = None, None
    missing_hr = zero_hr = 0
    largest_gap = 0.0
    for sample in samples:
        require(isinstance(sample, dict) and set(sample) == {"at", "distance_m", "timer_state", "hr"},
                "invalid trace sample")
        at = instant(sample["at"])
        require(start <= at <= end and (previous_time is None or previous_time < at),
                "unordered trace timestamp")
        require(type(sample["distance_m"]) in (int, float) and math.isfinite(sample["distance_m"]) and
                sample["distance_m"] >= 0 and
                (previous_distance is None or sample["distance_m"] >= previous_distance),
                "invalid cumulative distance")
        require(sample["timer_state"] == "running", "pause or unknown timer state")
        hr = observation(sample["hr"])
        if hr["status"] == "measured" and hr["value"] == 0:
            zero_hr += 1
        elif hr["status"] != "measured":
            missing_hr += 1
        if previous_time is not None:
            gap = (at - previous_time).total_seconds()
            largest_gap = max(largest_gap, gap)
            require(gap <= segment["max_gap_s"], "trace gap exceeds contract")
        previous_time, previous_distance = at, sample["distance_m"]
    duration = (end - start).total_seconds()
    distance = samples[-1]["distance_m"] - samples[0]["distance_m"]
    speed = distance / duration
    return {"schema": SCHEMA, "sample_count": len(samples), "missing_hr_samples": missing_hr,
            "zero_hr_samples": zero_hr, "largest_gap_s": largest_gap,
            "distance_method": segment["distance_method"], "speed_m_per_s": speed,
            "pace_s_per_km_at_zero_speed": None}
