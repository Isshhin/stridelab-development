"""Durable synthetic F2.2 journal. Not an admission, encryption or custody system.

Only synthetic normalized events are supported. Authority is supplied separately
and must never be restored from this database. No human data may be stored here.
"""
import json
import hashlib
import os
import re
import uuid
from contextlib import closing, contextmanager
import sqlite3
import threading
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from p2f_foundation import FoundationLedger, Quarantine, SCHEMA
from p2f_projector_v1 import project_v1, Quarantine as PinnedQuarantine
from p2f_projector_v34 import project_v34, Quarantine as V34Quarantine


SCHEMA_V2 = 'F2.2-synthetic.2'
SCHEMA_V3 = 'F2.2-synthetic.3'
ENVELOPE = {'id', 'event_id', 'account_id', 'kind', 'schema_version', 'recorded_at',
            'effective_at', 'available_at', 'origin_id', 'view_id', 'purpose_grant_id',
            'partition_id', 'lineage_class', 'data'}


def require(condition, message='contract violation'):
    if not condition:
        raise Quarantine(message)


def ident(value):
    require(isinstance(value, str) and bool(value.strip()), 'missing identity')
    return value


def instant(value):
    try:
        parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
        require(parsed.utcoffset() is not None, 'timestamp needs offset')
        return parsed.astimezone(timezone.utc)
    except (AttributeError, TypeError, ValueError, OverflowError) as error:
        raise Quarantine('invalid timestamp') from error


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)


def digest(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


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
    """Non-durable, process-local test authority with a shared mutation guard."""
    def __init__(self, accounts):
        self._lock = threading.RLock()
        self._accounts = set(accounts)
        self._suppressed = set()
        self._grants = set()
        self._epoch = 0
        self._authority_id = uuid.uuid4().hex

    @property
    def suppressed(self):
        with self._lock:
            return frozenset(self._suppressed)

    def guard(self):
        return self._lock

    def grant(self, account, view, purpose, partition):
        require(not getattr(_final_barrier, 'active', False), 'authority changed during replay')
        with self._lock:
            require(account in self._accounts and account not in self._suppressed, 'access denied')
            scope = tuple(ident(x) for x in (account, view, purpose, partition))
            if scope not in self._grants:
                self._grants.add(scope)
                self._epoch += 1

    def revoke(self, account, view, purpose, partition):
        require(not getattr(_final_barrier, 'active', False), 'authority changed during replay')
        with self._lock:
            scope = (account, view, purpose, partition)
            if scope in self._grants:
                self._grants.remove(scope)
                self._epoch += 1

    def check(self, account, view=None, purpose=None, partition=None):
        with self._lock:
            require(account in self._accounts and account not in self._suppressed, 'access denied')
            if view is not None:
                require((account, view, purpose, partition) in self._grants, 'access denied')

    def withdraw(self, account):
        require(not getattr(_final_barrier, 'active', False), 'authority changed during replay')
        with self._lock:
            account = ident(account)
            if account not in self._suppressed:
                self._suppressed.add(account)
                self._epoch += 1

    def snapshot(self):
        with self._lock:
            state = {'accounts': sorted(self._accounts), 'grants': sorted(self._grants),
                     'suppressed': sorted(self._suppressed), 'epoch': self._epoch, 'authority_id': self._authority_id}
            return {'authority_id': self._authority_id, 'epoch': self._epoch, 'digest': digest(state),
                    'suppression_digest': digest(sorted(self._suppressed))}


_final_barrier = threading.local()


@dataclass(frozen=True)
class DurableProjection:
    account_id: str
    generation: int
    scope: tuple
    data: dict
    authority_snapshot: dict | None = None


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

    def _authority_snapshot(self):
        try:
            snapshot = self.authority.snapshot()
            require(isinstance(snapshot, dict) and set(snapshot) == {'authority_id', 'epoch', 'digest', 'suppression_digest'} and
                    isinstance(snapshot['authority_id'], str) and re.fullmatch(r'[0-9a-f]{32}', snapshot['authority_id']) and
                    type(snapshot['epoch']) is int and snapshot['epoch'] >= 0 and
                    all(isinstance(snapshot[key], str) and re.fullmatch(r'[0-9a-f]{64}', snapshot[key])
                        for key in ('digest', 'suppression_digest')), 'invalid authority snapshot')
            return dict(snapshot)
        except Quarantine:
            raise
        except Exception as error:
            raise Quarantine('authority snapshot unavailable') from error

    @contextmanager
    def _validity_barrier(self):
        require(not getattr(_final_barrier, 'active', False), 'nested validity barrier')
        with closing(self._connect()) as barrier:
            barrier.execute('PRAGMA busy_timeout=0')
            try:
                barrier.execute('BEGIN IMMEDIATE')
            except sqlite3.OperationalError as error:
                raise Quarantine('journal busy during replay') from error
            try:
                require(type(self.authority) is SyntheticAuthority, 'authority guard unavailable')
                guard = self.authority.guard()
                require(guard.acquire(blocking=False), 'authority busy during replay')
                try:
                    _final_barrier.active = True
                    try:
                        yield barrier
                    finally:
                        try:
                            barrier.execute('ROLLBACK')
                        finally:
                            _final_barrier.active = False
                finally:
                    guard.release()
            finally:
                if barrier.in_transaction:
                    barrier.execute('ROLLBACK')

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
        require(not getattr(_final_barrier, 'active', False), 'journal changed during replay')
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
        authority = self._authority_snapshot() if current else None
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
        if projector_version in ('3', '4'):
            try:
                projection = project_v34(account, cutoff, watermark, projector_version, current, generation, all_rows, stop)
            except V34Quarantine as error:
                raise Quarantine(str(error)) from error
        else:
            full_rows = all_rows
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
        self._check(account, *scope)
        if current:
            require(authority == self._authority_snapshot(), 'authority changed during replay')
            require(self.head(account) == generation, 'journal changed during replay')
        if current and projector_version == '4':
            invalidations = [event for _, _, event in all_rows
                             if event['kind'] in ('source_correction', 'link_revocation')
                             and instant(event['available_at']) <= current]
            validity = {'as_of': current.isoformat(), 'authority': authority,
                        'invalidation_digest': digest(sorted(invalidations, key=lambda e: (instant(e['available_at']), e['event_id']))),
                        'invalidation_event_ids': [e['event_id'] for e in sorted(invalidations, key=lambda e: (instant(e['available_at']), e['event_id']))],
                        'invalidation_schema_versions': sorted({e['schema_version'] for e in invalidations}),
                        'projector_version': projector_version, 'source_schema_versions': projection['source_schema_versions'],
                        'event_set_digest': projection['event_set_digest'], 'exclusions': projection['exclusions'],
                        'exclusion_counts': projection.get('exclusion_counts', {}), 'scope': list(scope),
                        'historical_cutoff': stop.isoformat(), 'watermark': generation if watermark is None else watermark,
                        'journal_head': generation, 'journal_sequence_digest': projection['journal_sequence_digest']}
            validity['validity_digest'] = digest(validity)
            projection['validity'] = validity
        if current:
            with self._validity_barrier() as barrier:
                head = barrier.execute('SELECT generation FROM heads WHERE account=?', (account,)).fetchone()
                require((head[0] if head else 0) == generation, 'journal changed during replay')
                self._check(account, *scope)
                require(authority == self._authority_snapshot(), 'authority changed during replay')
                return DurableProjection(account, generation, scope, projection, authority)
        return DurableProjection(account, generation, scope, projection)

    def assert_current(self, projection):
        if projection.authority_snapshot is not None:
            with self._validity_barrier() as barrier:
                self._check(projection.account_id, *projection.scope)
                head = barrier.execute('SELECT generation FROM heads WHERE account=?', (projection.account_id,)).fetchone()
                require((head[0] if head else 0) == projection.generation, 'projection invalidated')
                require(self._authority_snapshot() == projection.authority_snapshot, 'authority changed')
                self._check(projection.account_id, *projection.scope)
                return projection
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
