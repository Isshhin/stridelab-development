"""Durable synthetic F2.2 journal. Not an admission, encryption or custody system.

Only synthetic normalized events are supported. Authority is supplied separately
and must never be restored from this database. No human data may be stored here.
"""
import json
import os
from contextlib import closing
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from p2f_foundation import FoundationLedger, Quarantine, SCHEMA, canonical, digest, ident, instant, require
from p2f_projector_v1 import project_v1, Quarantine as PinnedQuarantine


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


class SyntheticAuthority:
    """Non-durable test double; production authority is deliberately out of scope."""
    def __init__(self, accounts):
        self.accounts = set(accounts)
        self.suppressed = set()
        self.grants = set()

    def grant(self, account, view, purpose, partition):
        require(account in self.accounts and account not in self.suppressed, 'access denied')
        self.grants.add(tuple(ident(x) for x in (account, view, purpose, partition)))

    def revoke(self, account, view, purpose, partition):
        self.grants.discard((account, view, purpose, partition))

    def check(self, account, view=None, purpose=None, partition=None):
        require(account in self.accounts and account not in self.suppressed, 'access denied')
        if view is not None:
            require((account, view, purpose, partition) in self.grants, 'access denied')

    def withdraw(self, account):
        self.suppressed.add(ident(account))


@dataclass(frozen=True)
class DurableProjection:
    account_id: str
    generation: int
    scope: tuple
    data: dict


class DurableStore:
    """SQLite-backed synthetic event journal with an external live authority."""
    def __init__(self, path, authority):
        self.path = str(path)
        self.authority = authority
        with closing(self._connect()) as db:
            version = db.execute('PRAGMA user_version').fetchone()[0]
            require(version in (0, 2), 'unmigratable database version')
            db.executescript('''
                CREATE TABLE IF NOT EXISTS events (
                    account TEXT NOT NULL, event_id TEXT NOT NULL,
                    batch INTEGER NOT NULL, ordinal INTEGER NOT NULL,
                    schema_version TEXT NOT NULL, view_id TEXT NOT NULL, purpose TEXT NOT NULL, partition_id TEXT NOT NULL, payload TEXT NOT NULL,
                    PRIMARY KEY (account, event_id), UNIQUE (account, batch, ordinal));
                CREATE TABLE IF NOT EXISTS heads (account TEXT PRIMARY KEY, generation INTEGER NOT NULL);
                CREATE TRIGGER IF NOT EXISTS events_no_update BEFORE UPDATE ON events
                BEGIN SELECT RAISE(ABORT, 'immutable events'); END;
                CREATE TRIGGER IF NOT EXISTS events_no_delete BEFORE DELETE ON events
                BEGIN SELECT RAISE(ABORT, 'immutable events'); END;
                PRAGMA user_version = 2;
            ''')

    def _connect(self):
        db = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        db.execute('PRAGMA synchronous=FULL')
        return db

    def _check(self, account, view=None, purpose=None, partition=None):
        ident(account)
        try:
            self.authority.check(account, view, purpose, partition)
        except Exception as error:
            raise Quarantine('access denied') from error

    def head(self, account):
        self._check(account)
        with closing(self._connect()) as db:
            row = db.execute('SELECT generation FROM heads WHERE account=?', (account,)).fetchone()
            return row[0] if row else 0

    def _rows(self, db, account, watermark=None, scope=None):
        self._check(account)
        scopes = db.execute('SELECT DISTINCT view_id, purpose, partition_id FROM events WHERE account=?', (account,)).fetchall()
        wanted = [scope] if scope is not None else scopes
        for view, purpose, partition in wanted:
            self._check(account, view, purpose, partition)
        if scope is not None and scope not in scopes:
            return []
        sql = 'SELECT batch, schema_version, payload FROM events WHERE account=? AND batch<=?'
        args = [account, watermark if watermark is not None else 9223372036854775807]
        if scope is not None:
            sql += ' AND view_id=? AND purpose=? AND partition_id=?'
            args.extend(scope)
        rows = db.execute(sql + ' ORDER BY batch, ordinal', args).fetchall()
        result = [(batch, schema, json.loads(payload)) for batch, schema, payload in rows]
        for _, schema, event in result:
            normalize(schema, event)
            require(event['account_id'] == account and (scope is None or
                    (event['view_id'], event['purpose_grant_id'], event['partition_id']) == scope), 'stored scope mismatch')
        return result

    def append(self, account, events, expected_head, *, schema_version=SCHEMA_V3, fail_before_commit=False):
        self._check(account)
        require(schema_version in (SCHEMA_V2, SCHEMA_V3), 'unsupported source schema')
        require(type(expected_head) is int and expected_head >= 0 and isinstance(events, (list, tuple)), 'invalid append')
        db = self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            self._check(account)
            row = db.execute('SELECT generation FROM heads WHERE account=?', (account,)).fetchone()
            head = row[0] if row else 0
            existing = self._rows(db, account)
            by_id = {event['id']: (schema, canonical(event)) for _, schema, event in existing}
            pending = []
            for event in events:
                require(isinstance(event, dict) and event.get('account_id') == account, 'invalid account')
                event_id = ident(event.get('id'))
                normalize(schema_version, event)
                self._check(account, event['view_id'], event['purpose_grant_id'], event['partition_id'])
                encoded = canonical(event)
                if event_id in by_id:
                    require(by_id[event_id] == (schema_version, encoded), 'event identity collision')
                else:
                    by_id[event_id] = (schema_version, encoded)
                    pending.append(event)
            if not pending:
                db.execute('COMMIT')
                return head
            require(head == expected_head, 'stale writer')
            all_events = [e for _, _, e in existing] + pending
            predecessors = {}
            for event in all_events:
                if 'supersedes_event_id' in event:
                    prior = predecessors.get(event['supersedes_event_id'])
                    require(prior is not None and all(prior[k] == event[k] for k in
                            ('account_id', 'view_id', 'purpose_grant_id', 'partition_id')) and
                            instant(prior['available_at']) <= instant(event['available_at']), 'invalid supersession')
                predecessors[event['event_id']] = event
            self._validate([normalize(e['schema_version'], e) for e in all_events])
            for scope in {(e['view_id'], e['purpose_grant_id'], e['partition_id']) for e in all_events}:
                self._validate([normalize(e['schema_version'], e) for e in all_events
                                if (e['view_id'], e['purpose_grant_id'], e['partition_id']) == scope])
            for ordinal, event in enumerate(pending):
                db.execute('INSERT INTO events VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
                           (account, event['id'], head + 1, ordinal, schema_version, event['view_id'],
                            event['purpose_grant_id'], event['partition_id'], canonical(event)))
            db.execute('INSERT INTO heads VALUES (?, ?) ON CONFLICT(account) DO UPDATE SET generation=excluded.generation',
                       (account, head + 1))
            self._check(account)
            for event in pending:
                self._check(account, event['view_id'], event['purpose_grant_id'], event['partition_id'])
            if fail_before_commit:
                raise RuntimeError('simulated crash before commit')
            db.execute('COMMIT')
            return head + 1
        except BaseException:
            if db.in_transaction:
                db.execute('ROLLBACK')
            raise
        finally:
            db.close()

    @staticmethod
    def _validate(events):
        ledger = FoundationLedger()
        links = set()
        for row in events:
            if row.get('kind') == 'link_revocation':
                require(set(row) == {'id', 'account_id', 'kind', 'recorded_at', 'effective_at', 'available_at', 'data'}, 'invalid event envelope')
                recorded, effective, available = (instant(row[k]) for k in ('recorded_at', 'effective_at', 'available_at'))
                require(recorded <= available and effective <= available, 'invalid event chronology')
                data = row['data']
                require(isinstance(data, dict) and set(data) == {'link_event_id', 'evidence_id'}, 'invalid revocation')
                require(ident(data['link_event_id']) in links and ident(data['evidence_id']), 'unknown link')
            else:
                ledger.append([row])
                if row['kind'] == 'link':
                    links.add(row['id'])

    def decision_view(self, account, cutoff, watermark=None, projector_version='4', *, actor_view, purpose_grant_id, partition_id):
        return self._view(account, cutoff, watermark, projector_version, False, (actor_view, purpose_grant_id, partition_id))

    def current_view(self, account, cutoff, watermark=None, projector_version='4', as_of_now=None, *, actor_view, purpose_grant_id, partition_id):
        now = instant(as_of_now) if as_of_now else datetime.now(timezone.utc)
        return self._view(account, cutoff, watermark, projector_version, now, (actor_view, purpose_grant_id, partition_id))

    def _view(self, account, cutoff, watermark, projector_version, current, scope):
        self._check(account, *scope)
        require(projector_version in ('1', '2', '3', '4'), 'unknown projector')
        stop = instant(cutoff)
        require(not current or stop <= current, 'cutoff after current time')
        require(watermark is None or type(watermark) is int and watermark >= 0, 'invalid watermark')
        with closing(self._connect()) as db:
            db.execute('BEGIN')
            row = db.execute('SELECT generation FROM heads WHERE account=?', (account,)).fetchone()
            generation = row[0] if row else 0
            require(watermark is None or watermark <= generation, 'future watermark')
            all_rows = self._rows(db, account, scope=scope)
            db.execute('COMMIT')
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
        if projector_version == '1':
            try:
                projection = project_v1(account, cutoff, events, selected, all_rows, current, full_rows, watermark, stop)
            except PinnedQuarantine as error:
                raise Quarantine('pinned projection quarantined') from error
        else:
            ledger = FoundationLedger()
            try:
                for event in events:
                    if event['kind'] != 'link_revocation':
                        ledger.append([event])
                if projector_version in ('3', '4'):
                    projection = ledger._project(account, sorted(events, key=lambda row: instant(row['available_at'])))
                    projection['schema'] = SCHEMA
                else:
                    projection = ledger.replay(account, cutoff).data
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
        self._check(account, *scope)
        return DurableProjection(account, generation, scope, projection)

    def assert_current(self, projection):
        self._check(projection.account_id, *projection.scope)
        require(self.head(projection.account_id) == projection.generation, 'projection invalidated')
        self._check(projection.account_id, *projection.scope)
        return projection

    def backup(self, destination):
        require(Path(destination).resolve() != Path(self.path).resolve(), 'invalid backup destination')
        with closing(self._connect()) as source:
            source.execute('BEGIN')
            accounts = [row[0] for row in source.execute('SELECT account FROM heads')]
            for account in accounts:
                self._check(account)
                for scope in source.execute('SELECT DISTINCT view_id, purpose, partition_id FROM events WHERE account=?', (account,)):
                    self._check(account, *scope)
            descriptor = os.open(str(destination), os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            os.close(descriptor)
            with closing(sqlite3.connect(str(destination))) as target:
                source.backup(target)
            for account in accounts:
                self._check(account)
                for scope in source.execute('SELECT DISTINCT view_id, purpose, partition_id FROM events WHERE account=?', (account,)):
                    self._check(account, *scope)
            source.execute('COMMIT')
