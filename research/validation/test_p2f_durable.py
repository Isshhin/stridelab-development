import tempfile
from contextlib import closing
import subprocess
import sqlite3
import sys
import threading
import unittest
from pathlib import Path

from p2f_foundation import Quarantine
from p2f_durable import DurableStore, SyntheticAuthority, SCHEMA_V2, SCHEMA_V3
from test_p2f_foundation import event, measured, planned, ts


def activity(account='A'):
    return event('a1', account, 'activity', 20, activity_id='a1', started_at=ts(10), ended_at=ts(20), discipline='running', intent='unknown', plan_ownership='unplanned_or_unknown_intent', completion='completed')


def source(sid, minute, alias, value, **extra):
    return event(sid, 'A', 'source', minute, source_id=sid, activity_id='a1', alias=['fit', alias], digest=(str(minute % 10) * 64), measurements={'distance': measured(value, 'm')}, **extra)


class FixtureStore(DurableStore):
    """Give old synthetic fixtures explicit scope; never used for stored legacy data."""
    def append(self, account, events, expected_head, *, schema_version=SCHEMA_V3, **kwargs):
        if schema_version == 'F2.2-reference.1':
            schema_version = SCHEMA_V2
        wrapped = []
        for event in events:
            row = full_event(event)
            row['schema_version'] = schema_version
            wrapped.append(row)
        return super().append(account, wrapped, expected_head, schema_version=schema_version, **kwargs)

    def decision_view(self, account, cutoff, *args, **kwargs):
        return super().decision_view(account, cutoff, *args, actor_view='coach',
                                     purpose_grant_id='research', partition_id='development', **kwargs)

    def current_view(self, account, cutoff, *args, **kwargs):
        return super().current_view(account, cutoff, *args, actor_view='coach',
                                    purpose_grant_id='research', partition_id='development', **kwargs)


class DurableTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'store.sqlite'
        self.auth = SyntheticAuthority({'A', 'B'})
        self.auth.grant('A', 'coach', 'research', 'development')
        self.auth.grant('B', 'coach', 'research', 'development')
        self.store = FixtureStore(self.path, self.auth)

    def test_crash_rollback(self):
        with self.assertRaises(RuntimeError):
            self.store.append('A', [activity()], 0, fail_before_commit=True)
        self.assertEqual(0, self.store.head('A'))
        self.assertEqual(0, self.store.decision_view('A', ts(30)).data['exposure_count'])

    def test_process_death_before_commit_rolls_back(self):
        script = r"""
import os, sys
from p2f_durable import SyntheticAuthority
from test_p2f_durable import activity, FixtureStore
auth = SyntheticAuthority({'A'}); auth.grant('A', 'coach', 'research', 'development')
store = FixtureStore(sys.argv[1], auth)
connect = store._connect
class Connection:
    def __init__(self): self.db = connect()
    def execute(self, sql, *args):
        if sql == 'COMMIT': os._exit(17)
        return self.db.execute(sql, *args)
    def __getattr__(self, name): return getattr(self.db, name)
store._connect = Connection
store.append('A', [activity()], 0)
"""
        result = subprocess.run([sys.executable, '-c', script, str(self.path)], cwd=Path(__file__).parent)
        self.assertEqual(17, result.returncode)
        self.assertEqual(0, self.store.head('A'))
        self.assertEqual(0, self.store.decision_view('A', ts(30)).data['exposure_count'])

    def test_simultaneous_writers_are_serialized(self):
        barrier = threading.Barrier(2)
        results = []
        def write(row):
            barrier.wait()
            try:
                results.append(self.store.append('A', [row], 0))
            except Quarantine:
                results.append('stale')
        second = activity(); second['id'] = 'a2'; second['data']['activity_id'] = 'a2'
        threads = [threading.Thread(target=write, args=(row,)) for row in (activity(), second)]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        self.assertCountEqual([1, 'stale'], results)
        self.assertEqual(1, self.store.decision_view('A', ts(30)).data['exposure_count'])

    def test_current_view_does_not_apply_future_invalidation(self):
        self.store.append('A', [activity(), source('s1', 21, 'x', 1)], 0)
        correction = event('s2', 'A', 'source_correction', 30, source_id='s2', activity_id='a1', alias=['fit', 'y'], digest='b'*64, measurements={'distance': measured(2, 'm')}, supersedes_source_id='s1', correction_evidence_id='review')
        self.store.append('A', [correction], 1)
        before = self.store.current_view('A', ts(25), as_of_now=ts(29)).data
        after = self.store.current_view('A', ts(25), as_of_now=ts(31)).data
        self.assertEqual(['s1'], before['activities']['a1']['source_ids'])
        self.assertEqual([], after['activities']['a1']['source_ids'])

    def test_stale_concurrent_writers_and_idempotent_retry(self):
        other = FixtureStore(self.path, self.auth)
        self.store.append('A', [activity()], 0)
        self.assertEqual(1, other.append('A', [activity()], 0))
        with self.assertRaises(Quarantine):
            other.append('A', [source('s1', 21, 'x', 1)], 0)
        self.assertEqual(1, self.store.head('A'))

    def test_withdrawal_before_restore(self):
        self.store.append('A', [activity()], 0)
        backup = Path(self.tmp.name) / 'backup.sqlite'
        self.store.backup(backup)
        self.auth.withdraw('A')
        restored = FixtureStore(backup, self.auth)
        with self.assertRaises(Quarantine):
            restored.decision_view('A', ts(30))
        with self.assertRaises(Quarantine):
            restored.current_view('A', ts(30))

    def test_cross_account_keys(self):
        self.store.append('A', [activity()], 0)
        with self.assertRaises(Quarantine):
            self.store.append('B', [activity()], 0)
        self.assertEqual(0, self.store.decision_view('B', ts(30)).data['exposure_count'])
        foreign = event('s', 'B', 'source', 21, source_id='s', activity_id='a1', alias=['fit', 'x'], digest='a'*64, measurements={})
        with self.assertRaises(Quarantine):
            self.store.append('B', [foreign], 0)

    def test_duplicate_aliases_and_explicit_adjudication(self):
        self.store.append('A', [activity(), source('s1', 21, 'x', 1)], 0)
        with self.assertRaises(Quarantine):
            self.store.append('A', [source('s2', 22, 'x', 2, alias_evidence_id='review')], 1)
        with self.assertRaises(Quarantine):
            self.store.append('A', [source('s2', 22, 'y', 2)], 1)
        self.store.append('A', [source('s2', 22, 'y', 2, alias_evidence_id='review')], 1)
        view = self.store.current_view('A', ts(30)).data
        self.assertEqual(1, view['exposure_count'])
        self.assertEqual('conflict', view['activities']['a1']['measurements']['distance']['status'])

    def test_late_offline_and_watermark(self):
        row = activity(); row['available_at'] = ts(90); row['recorded_at'] = ts(31); row['effective_at'] = ts(20); row['data']['offline_created_at'] = ts(31)
        self.store.append('A', [row], 0)
        self.assertEqual(0, self.store.decision_view('A', ts(60)).data['exposure_count'])
        self.assertEqual(1, self.store.decision_view('A', ts(100)).data['exposure_count'])
        self.assertEqual(0, self.store.decision_view('A', ts(100), watermark=0).data['exposure_count'])

    def test_correction_and_link_revocation_invalidate_current(self):
        rows = planned() + [activity(), source('s1', 21, 'x', 1), event('link', 'A', 'link', 22, activity_id='a1', opportunity_id='o1', revision_id='r1', evidence_id='review')]
        self.store.append('A', rows, 0)
        correction = event('s2', 'A', 'source_correction', 30, source_id='s2', activity_id='a1', alias=['fit', 'y'], digest='b'*64, measurements={'distance': measured(2, 'm')}, supersedes_source_id='s1', correction_evidence_id='review')
        revocation = event('revoke', 'A', 'link_revocation', 31, link_event_id='link', evidence_id='review')
        self.store.append('A', [correction, revocation], 1)
        historical = self.store.decision_view('A', ts(25)).data
        self.assertIn('link', historical['links'])
        current = self.store.current_view('A', ts(25)).data
        self.assertNotIn('link', current['links'])
        self.assertNotIn('s1', current['activities']['a1']['source_ids'])
        self.assertIn('s1', current['activities']['a1']['invalidated_source_ids'])
        self.assertIn('s1', current['lineage']['invalidated_source_ids'])

    def test_old_projector_replay_and_schema_migration(self):
        self.store.append('A', [activity()], 0, schema_version='F2.2-reference.1')
        first = self.store.decision_view('A', ts(30), projector_version='1').data
        newer = source('s1', 21, 'new', 1); newer['schema_version'] = SCHEMA_V3
        self.store.append('A', [newer], 1, schema_version=SCHEMA_V3)
        reopened = FixtureStore(self.path, self.auth)
        second = reopened.decision_view('A', ts(30), watermark=1, projector_version='2').data
        self.assertEqual(first['event_set_digest'], second['event_set_digest'])
        self.assertEqual(first['activities'], second['activities'])
        self.assertEqual('2', second['projector_version'])
        self.assertEqual([SCHEMA_V2], second['source_schema_versions'])
        mixed = reopened.decision_view('A', ts(30), projector_version='2').data
        self.assertEqual([SCHEMA_V2, SCHEMA_V3], mixed['source_schema_versions'])
        self.assertEqual(1, mixed['activities']['a1']['measurements']['distance']['value'])


def full_event(row, view='coach', grant='research', partition='development'):
    row = dict(row)
    row.update(event_id=row['id'], schema_version='F2.2-synthetic.3', origin_id='synthetic-origin',
               view_id=view, purpose_grant_id=grant, partition_id=partition,
               lineage_class='synthetic')
    return row


class ScopedContractTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'scoped.sqlite'
        self.authority = SyntheticAuthority({'A', 'B'})
        self.authority.grant('A', 'coach', 'research', 'development')
        self.authority.grant('A', 'athlete', 'research', 'development')
        self.authority.grant('B', 'coach', 'research', 'development')
        self.store = DurableStore(self.path, self.authority)

    def view(self, view='coach', grant='research', partition='development', account='A', **kwargs):
        return self.store.decision_view(account, ts(60), actor_view=view,
                                        purpose_grant_id=grant, partition_id=partition, **kwargs)

    def test_old_unscoped_database_fails_closed_without_rewrite(self):
        old = Path(self.tmp.name) / 'unscoped.sqlite'
        with closing(sqlite3.connect(old)) as db:
            db.execute('CREATE TABLE events (payload TEXT)')
            db.execute("INSERT INTO events VALUES ('unmigratable')")
            db.execute('PRAGMA user_version = 1')
            db.commit()
        with self.assertRaises(Quarantine): DurableStore(old, self.authority)
        with closing(sqlite3.connect(old)) as db:
            self.assertEqual(1, db.execute('PRAGMA user_version').fetchone()[0])
            self.assertEqual('unmigratable', db.execute('SELECT payload FROM events').fetchone()[0])

    def test_actor_isolation_and_unauthorized_view(self):
        self.store.append('A', [full_event(activity())], 0)
        self.assertEqual(1, self.view().data['exposure_count'])
        self.assertEqual(0, self.view(view='athlete').data['exposure_count'])
        with self.assertRaises(Quarantine): self.view(view='intruder')

    def test_scoped_grant_revocation_restore_and_sibling(self):
        self.store.append('A', [full_event(activity())], 0)
        self.store.append('B', [full_event(activity('B'))], 0)
        backup = Path(self.tmp.name) / 'backup.sqlite'
        self.store.backup(backup)
        for kwargs in ({'grant': 'other'}, {'partition': 'held-out'}):
            with self.assertRaises(Quarantine): self.view(**kwargs)
        self.authority.revoke('A', 'coach', 'research', 'development')
        self.store = DurableStore(backup, self.authority)
        with self.assertRaises(Quarantine): self.view()
        self.assertEqual(1, self.view(account='B').data['exposure_count'])

    def test_required_envelope_and_no_fabricated_legacy(self):
        for key in ('event_id', 'schema_version', 'origin_id', 'view_id',
                    'purpose_grant_id', 'partition_id', 'lineage_class'):
            row = full_event(activity()); del row[key]
            with self.subTest(key=key), self.assertRaises(Quarantine):
                self.store.append('A', [row], 0)
        with self.assertRaises(Quarantine): self.store.append('A', [activity()], 0)

    def test_revoked_projection_and_cross_scope_supersession(self):
        row = full_event(activity())
        self.store.append('A', [row], 0)
        projection = self.view()
        other = activity(); other['id'] = 'a2'; other['data']['activity_id'] = 'a2'
        other = full_event(other, view='athlete'); other['supersedes_event_id'] = 'a1'
        with self.assertRaises(Quarantine): self.store.append('A', [other], 1)
        self.authority.revoke('A', 'coach', 'research', 'development')
        with self.assertRaises(Quarantine): self.store.assert_current(projection)
        self.assertEqual(0, self.view(view='athlete').data['exposure_count'])

    def test_held_out_and_non_synthetic_are_rejected_even_if_granted(self):
        self.authority.grant('A', 'coach', 'research', 'held-out')
        row = full_event(activity(), partition='held-out')
        with self.assertRaises(Quarantine): self.store.append('A', [row], 0)
        row = full_event(activity()); row['lineage_class'] = 'human'
        with self.assertRaises(Quarantine): self.store.append('A', [row], 0)

    def test_pinned_projector_independent_and_migration_no_future(self):
        self.store.append('A', [full_event(activity())], 0)
        before = self.view(projector_version='1').data
        later = activity(); later['id'] = 'a2'; later['data']['activity_id'] = 'a2'
        later['recorded_at'] = later['effective_at'] = later['available_at'] = ts(90)
        self.store.append('A', [full_event(later)], 1)
        from unittest.mock import patch
        with patch('p2f_foundation.FoundationLedger.replay', side_effect=AssertionError('current changed')):
            old = self.view(projector_version='1').data
        new = self.view(projector_version='2').data
        self.assertEqual(before['activities'], old['activities'])
        self.assertEqual(old['activities'], new['activities'])
        self.assertEqual(old['event_set_digest'], new['event_set_digest'])
        self.assertNotIn('a2', new['activities'])
        self.assertEqual(new, self.view(projector_version='2').data)


if __name__ == '__main__':
    unittest.main()
