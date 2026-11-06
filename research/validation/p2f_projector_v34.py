"""Pinned synthetic F2.2 projectors 3 and 4; do not modify for new projector versions."""
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




SCHEMA_V2 = 'F2.2-synthetic.2'
SCHEMA_V3 = 'F2.2-synthetic.3'
ENVELOPE = {'id', 'event_id', 'account_id', 'kind', 'schema_version', 'recorded_at',
            'effective_at', 'available_at', 'origin_id', 'view_id', 'purpose_grant_id',
            'partition_id', 'lineage_class', 'data'}


def normalize(schema, event):
    require(isinstance(event, dict) and schema in (SCHEMA_V2, SCHEMA_V3), 'unmigratable source schema')
    require(set(event) in (ENVELOPE, ENVELOPE | {'supersedes_event_id'}), 'invalid F2.2 envelope')
    for field in ('id', 'event_id', 'account_id', 'kind', 'schema_version', 'origin_id',
                  'view_id', 'purpose_grant_id', 'partition_id', 'lineage_class'):
        ident(event[field])
    require(event['id'] == event['event_id'] and event['schema_version'] == schema, 'invalid identity or schema')
    require(event['partition_id'] == 'development' and event['lineage_class'] == 'synthetic', 'synthetic development only')
    if 'supersedes_event_id' in event:
        ident(event['supersedes_event_id'])
    return {key: event[key] for key in ('id', 'account_id', 'kind', 'recorded_at', 'effective_at', 'available_at', 'data')}


def project_v34(account, cutoff, watermark, projector_version, current, generation, all_rows, stop):
    require(projector_version in ('3', '4'), 'unknown pinned projector')
    full_rows = all_rows
    if projector_version in ('3', '4'):
        visible = [event for batch, _, event in full_rows
                   if (current or watermark is None or batch <= watermark)
                   and instant(event['available_at']) <= (current or stop)]
        times = [instant(event['available_at']) for event in visible]
        require(len(times) == len(set(times)), 'ambiguous availability tie')
        require(not any('supersedes_event_id' in event for event in visible),
                'unsupported supersession')
        by_id = {event['id']: event for _, _, event in full_rows}
        sources = {event['data']['source_id']: event for _, _, event in full_rows
                   if event['kind'] in ('source', 'source_correction')}
        for event in visible:
            if projector_version == '4' and event['kind'] == 'activity':
                require(instant(event['data']['ended_at']) <= instant(event['available_at']),
                        'activity ends after availability')
            if event['kind'] == 'link_revocation':
                link = by_id.get(event['data']['link_event_id'])
                require(link is not None and link['kind'] == 'link' and
                        instant(link['available_at']) < instant(event['available_at']) and
                        instant(link['effective_at']) <= instant(event['effective_at']),
                        'invalid revocation chronology')
            if event['kind'] == 'source_correction':
                prior = sources.get(event['data']['supersedes_source_id'])
                require(prior is not None and
                        instant(prior['available_at']) < instant(event['available_at']) and
                        instant(prior['effective_at']) <= instant(event['effective_at']),
                        'invalid correction chronology')
    all_rows = [(batch, schema, normalize(schema, event)) for batch, schema, event in all_rows]
    selected = [(batch, schema, event) for batch, schema, event in all_rows
                if (watermark is None or batch <= watermark) and instant(event['available_at']) <= stop]
    events = [event for _, _, event in selected]
    ledger = FoundationLedger()
    try:
        for event in events:
            if event['kind'] != 'link_revocation':
                ledger.append([event])
        projection = ledger._project(account, sorted(events, key=lambda row: instant(row['available_at'])))
        projection['schema'] = SCHEMA
    except Quarantine as error:
        raise Quarantine('pinned projection quarantined') from error
    # Current invalidations are applied to the historical event set without importing later values.
    invalidations = [event for _, _, event in (all_rows if current else selected)
                     if not current or instant(event['available_at']) <= current]
    revoked = {e['data']['link_event_id'] for e in invalidations if e['kind'] == 'link_revocation'}
    corrected = {e['data']['supersedes_source_id'] for e in invalidations if e['kind'] == 'source_correction'}
    source_activities = {e['data']['source_id']: e['data']['activity_id'] for e in events if e['kind'] in ('source', 'source_correction')}
    affected = {source_activities[s] for s in corrected if s in source_activities}
    revoked.update(key for key, link in projection['links'].items() if link['activity_id'] in affected)
    for link_id in revoked:
        link = projection['links'].pop(link_id, None)
        if link:
            activity = projection['activities'][link['activity_id']]
            opportunity = projection['opportunities'][link['opportunity_id']]
            if link_id in activity['link_ids']:
                activity['link_ids'].remove(link_id)
            if (link['activity_id'] in opportunity['activity_ids'] and not any(
                    other['activity_id'] == link['activity_id'] and other['opportunity_id'] == link['opportunity_id']
                    for other in projection['links'].values())):
                opportunity['activity_ids'].remove(link['activity_id'])
    if affected:
        # Reproject historical rows without superseded sources so conflicting fields recalculate.
        kept = [e for e in events if not (e['kind'] in ('source', 'source_correction') and e['data']['source_id'] in corrected)]
        source_rows = {e['data']['source_id']: e for e in kept if e['kind'] in ('source', 'source_correction')}
        for aid in affected:
            activity = projection['activities'][aid]
            removed = sorted(set(activity['source_ids']) & corrected)
            activity['source_ids'] = [s for s in activity['source_ids'] if s not in corrected]
            activity['invalidated_source_ids'] = sorted(set(activity['invalidated_source_ids']) | set(removed))
            metrics = {}
            for sid in activity['source_ids']:
                for name, value in source_rows[sid]['data']['measurements'].items():
                    metrics.setdefault(name, []).append((sid, value))
            activity['measurements'] = {}
            for name, values in metrics.items():
                first = values[0][1]
                value = dict(first) if all(v == first for _, v in values) else {'status': 'conflict', 'value': None, 'unit': first['unit'], 'method': None}
                activity['measurements'][name] = {**value, 'source_ids': [sid for sid, _ in values]}
    projection['event_ids'] = [e['id'] for e in sorted(events, key=lambda e: (instant(e['available_at']), e['id']))]
    full_events = [event for batch, _, event in full_rows
                   if (watermark is None or batch <= watermark) and instant(event['available_at']) <= stop]
    projection['event_set_digest'] = digest(sorted(full_events, key=lambda e: (instant(e['available_at']), e['event_id'])))
    projection['projector_version'] = projector_version
    projection['source_schema_versions'] = sorted({schema for _, schema, _ in selected})
    projection['lineage'] = {'event_ids': projection['event_ids'], 'invalidation_event_ids': sorted(e['id'] for e in invalidations if e['kind'] in ('source_correction', 'link_revocation')), 'invalidated_source_ids': sorted(corrected & set(source_activities)), 'revoked_link_ids': sorted(revoked)}
    projection['exclusion_counts'] = {'invalidated_sources': len(corrected & set(source_activities)), 'revoked_links': len(revoked)}
    if projector_version in ('3', '4'):
        sequence = [(batch, event['id']) for batch, _, event in full_rows
                    if (watermark is None or batch <= watermark) and instant(event['available_at']) <= stop]
        projection['journal_event_ids'] = [event_id for _, event_id in sequence]
        projection['journal_sequence_digest'] = digest(sequence)
        projection['watermark'] = generation if watermark is None else watermark
        for opportunity_id, opportunity in projection['opportunities'].items():
            opportunity['activity_ids'] = list(dict.fromkeys(
                link['activity_id'] for link in projection['links'].values()
                if link['opportunity_id'] == opportunity_id))
    return projection
