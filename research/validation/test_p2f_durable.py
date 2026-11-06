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


class LinkMembershipRegressionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'store.sqlite'
        self.auth = SyntheticAuthority({'A'})
        self.auth.grant('A', 'coach', 'research', 'development')
        self.store = FixtureStore(self.path, self.auth)
        links = [event(name, 'A', 'link', minute, activity_id='a1', opportunity_id='o1',
                       revision_id='r1', evidence_id='review-'+name)
                 for name, minute in (('l1', 22), ('l2', 23))]
        self.store.append('A', planned() + [activity(), source('s1', 21, 'x', 1)] + links, 0)

    def ids(self, cutoff=30, version='3', current=False):
        view = self.store.current_view if current else self.store.decision_view
        return view('A', ts(cutoff), projector_version=version).data

    def revoke(self, name, target, minute, head):
        self.store.append('A', [event(name, 'A', 'link_revocation', minute,
                                      link_event_id=target, evidence_id='review')], head)

    def test_duplicate_links_unique_with_pinned_historical_versions(self):
        for version in ('1', '2'):
            self.assertEqual(['a1', 'a1'], self.ids(version=version)['opportunities']['o1']['activity_ids'])
        data = self.ids()
        self.assertEqual(['a1'], data['opportunities']['o1']['activity_ids'])
        self.assertEqual(['l1', 'l2'], sorted(data['links']))
        self.assertEqual(['l1', 'l2'], sorted(data['activities']['a1']['link_ids']))

    def test_partial_and_complete_revocation_keep_membership_consistent(self):
        self.revoke('v1', 'l1', 25, 1)
        partial = self.ids()
        self.assertEqual(['a1'], partial['opportunities']['o1']['activity_ids'])
        self.assertEqual(['l2'], sorted(partial['links']))
        self.revoke('v2', 'l2', 26, 2)
        complete = self.ids()
        self.assertEqual([], complete['opportunities']['o1']['activity_ids'])
        self.assertEqual({}, complete['links'])
        self.assertEqual([], complete['activities']['a1']['link_ids'])

    def test_historical_cutoff_and_current_revocation(self):
        self.revoke('v1', 'l1', 25, 1)
        self.revoke('v2', 'l2', 26, 2)
        before = self.ids(cutoff=24)
        self.assertEqual(['a1'], before['opportunities']['o1']['activity_ids'])
        self.assertEqual(['l1', 'l2'], sorted(before['links']))
        self.assertEqual([], self.ids(cutoff=24, current=True)['opportunities']['o1']['activity_ids'])

    def test_correction_invalidates_both_links(self):
        correction = event('s2', 'A', 'source_correction', 30, source_id='s2', activity_id='a1',
                           alias=['fit','new'], digest='b'*64, measurements={'distance': measured(2, 'm')},
                           supersedes_source_id='s1', correction_evidence_id='review')
        self.store.append('A', [correction], 1)
        data = self.ids(cutoff=35)
        self.assertEqual([], data['opportunities']['o1']['activity_ids'])
        self.assertEqual({}, data['links'])
        self.assertEqual(['l1', 'l2'], sorted(data['lineage']['revoked_link_ids']))

    def test_reopen_populated_journal_without_mutating_events(self):
        old = self.ids(version='1')
        first = self.ids()
        reopened = FixtureStore(self.path, self.auth)
        new = reopened.decision_view('A', ts(30), projector_version='3').data
        self.assertEqual(first, new)
        self.assertEqual(old, reopened.decision_view('A', ts(30), projector_version='1').data)
        self.assertEqual(old['event_set_digest'], new['event_set_digest'])


class UnsafeHistoryRegressionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.auth = SyntheticAuthority({'A'})
        self.auth.grant('A', 'coach', 'research', 'development')
        self.store = DurableStore(Path(self.tmp.name) / 'store.sqlite', self.auth)
        self.scope = dict(actor_view='coach', purpose_grant_id='research', partition_id='development')

    def test_uninterpreted_supersession_is_quarantined_at_relevant_time(self):
        first = full_event(activity())
        second = activity(); second['id'] = 'a2'; second['data']['activity_id'] = 'a2'
        second['recorded_at'] = second['effective_at'] = second['available_at'] = ts(30)
        second = full_event(second); second['supersedes_event_id'] = 'a1'
        self.store.append('A', [first, second], 0)
        self.assertEqual(1, self.store.decision_view('A', ts(25), **self.scope).data['exposure_count'])
        self.assertEqual(2, self.store.decision_view('A', ts(40), projector_version='2', **self.scope).data['exposure_count'])
        with self.assertRaisesRegex(Quarantine, 'unsupported supersession'):
            self.store.decision_view('A', ts(40), **self.scope)
        with self.assertRaisesRegex(Quarantine, 'unsupported supersession'):
            self.store.current_view('A', ts(25), as_of_now=ts(40), **self.scope)

    def test_backup_never_overwrites_existing_database(self):
        existing = Path(self.tmp.name) / 'existing.sqlite'
        with closing(sqlite3.connect(existing)) as db:
            db.execute('CREATE TABLE sentinel (value TEXT)')
            db.execute("INSERT INTO sentinel VALUES ('keep')")
            db.commit()
        before = existing.read_bytes()
        with self.assertRaises((Quarantine, FileExistsError)):
            self.store.backup(existing)
        self.assertEqual(before, existing.read_bytes())

    def test_equal_availability_without_actor_sequence_is_quarantined(self):
        root, delivery = planned()
        root['id'] = 'z-root'; delivery['id'] = 'a-delivery'
        delivery['recorded_at'] = delivery['effective_at'] = delivery['available_at'] = ts(0)
        self.store.append('A', [full_event(root), full_event(delivery)], 0)
        with self.assertRaises(Quarantine):
            self.store.decision_view('A', ts(1), projector_version='2', **self.scope)
        with self.assertRaisesRegex(Quarantine, 'ambiguous availability tie'):
            self.store.decision_view('A', ts(1), **self.scope)

    def test_same_event_set_different_journal_order_has_distinct_sequence_digest(self):
        one = full_event(activity())
        two = activity(); two['id'] = 'a2'; two['data']['activity_id'] = 'a2'
        two['recorded_at'] = two['effective_at'] = two['available_at'] = ts(21); two = full_event(two)
        other = DurableStore(Path(self.tmp.name) / 'other.sqlite', self.auth)
        self.store.append('A', [one, two], 0)
        other.append('A', [two, one], 0)
        left = self.store.decision_view('A', ts(30), **self.scope).data
        right = other.decision_view('A', ts(30), **self.scope).data
        self.assertEqual(left['event_set_digest'], right['event_set_digest'])
        self.assertNotEqual(left['journal_sequence_digest'], right['journal_sequence_digest'])
        self.assertEqual(['a1', 'a2'], left['journal_event_ids'])
        self.assertEqual(['a2', 'a1'], right['journal_event_ids'])

    def test_correction_cannot_precede_its_source(self):
        first = source('s1', 30, 'x', 1)
        correction = event('s2', 'A', 'source_correction', 25, source_id='s2', activity_id='a1',
                           alias=['fit', 'y'], digest='b'*64, measurements={'distance': measured(2, 'm')},
                           supersedes_source_id='s1', correction_evidence_id='review')
        self.store.append('A', [full_event(e) for e in [activity(), first, correction]], 0)
        with self.assertRaisesRegex(Quarantine, 'invalid correction chronology'):
            self.store.decision_view('A', ts(35), **self.scope)

    def test_revocation_cannot_precede_its_link(self):
        link = event('link', 'A', 'link', 30, activity_id='a1', opportunity_id='o1', revision_id='r1', evidence_id='review')
        revocation = event('revoke', 'A', 'link_revocation', 25, link_event_id='link', evidence_id='review')
        self.store.append('A', [full_event(e) for e in planned()+[activity(), link, revocation]], 0)
        with self.assertRaisesRegex(Quarantine, 'invalid revocation chronology'):
            self.store.current_view('A', ts(24), as_of_now=ts(35), **self.scope)


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
