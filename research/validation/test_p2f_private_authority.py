"""Invented-only authority/custody model acceptance tests."""
import unittest
import threading
from unittest.mock import patch
from copy import deepcopy
from p2f_private_authority import Anchor, Issuer, Ledger, Inventory, Denied
from p2f_private_custody import Broker, FakeKey


class AuthorityMatrix(unittest.TestCase):
    def setUp(self):
        self.anchor = Anchor('anchor-A')
        self.issuer = Issuer()
        self.ledger = Ledger(self.anchor, self.issuer)
        self.scope = ('A', 'source', 'research', 'development')
        self.inventory = Inventory(0, {'object': (self.scope, 1, 'active')})
        self.key = FakeKey()
        self.broker = Broker(self.ledger, self.inventory, self.key)
        self.broker.enroll(self.scope, 'anchor-A', 'object', 1, approved=True)
        self.broker.reconcile(channel_clear=True)

    def read(self, **kwargs):
        return self.broker.read(self.scope, 'object', 1, **kwargs)

    def denied(self, reason, fn):
        with self.assertRaisesRegex(Denied, reason): fn()

    def test_stale_grant_and_generation(self):
        old = deepcopy(self.ledger.head)
        self.broker.transition(self.scope, 0, 'revoke', 'revoke-1')
        self.ledger.head = old
        self.denied('generation', self.read)
        self.denied('generation', lambda: self.anchor.verify(0))

    def test_ledger_rollback_and_generation_mismatch(self):
        self.anchor.advance(0)
        self.denied('generation', self.read)
        self.assertTrue(self.broker.closed)

    def test_data_rollback_and_removed_unknown_descendants(self):
        self.inventory.objects['object'] = (self.scope, 2, 'active')
        self.denied('inventory', self.read)
        for state in ('removed', 'withdrawn', 'unknown'):
            self.inventory.objects['object'] = (self.scope, 1, state)
            self.denied('inventory', self.read)

    def test_missing_unavailable_corrupt_and_wrong_anchor(self):
        for fault in ('missing', 'unavailable', 'corrupt', 'wrong_identity'):
            with self.subTest(fault=fault):
                self.anchor.fault = fault
                self.denied('anchor', self.read)
                self.anchor.fault = None
                self.broker.reconcile(channel_clear=True)

    def test_corrupt_authority_and_unavailable_issuer(self):
        self.ledger.head['authenticated'] = False
        self.denied('authority', self.read)
        self.ledger.head['authenticated'] = True
        self.broker.reconcile(channel_clear=True)
        self.issuer.available = False
        self.denied('issuer', self.read)

    def test_detected_and_hidden_forks(self):
        self.issuer.detected_fork = True
        self.denied('issuer', self.read)
        self.issuer.detected_fork = False
        self.broker.reconcile(channel_clear=True)
        self.issuer.hidden_fork = True
        self.assertEqual('violated', self.issuer.assumption_status())
        self.assertEqual('published', self.read())  # The presented head cannot reveal a hidden fork.

    def test_wrong_account_purpose_and_untrusted_inventory(self):
        for scope in (('B','source','research','development'), ('A','source','other','development')):
            self.denied('scope', lambda: self.broker.read(scope, 'object', 1))
        for fault in ('missing','corrupt','rollback','mismatch'):
            self.inventory.fault = fault
            self.denied('inventory', self.read)
        self.inventory.fault = None

    def test_replayed_operation_idempotent_and_collision_denied(self):
        self.broker.transition(self.scope, 0, 'revoke', 'op')
        self.assertEqual('acknowledged', self.broker.transition(self.scope, 0, 'revoke', 'op'))
        self.assertEqual(1, self.anchor.read())
        self.denied('collision', lambda: self.broker.transition(self.scope, 0, 'delete', 'op'))

    def test_ambiguous_advance_is_not_blindly_retried(self):
        self.anchor.fault = 'ambiguous_after'
        self.denied('ambiguous', lambda: self.broker.transition(self.scope, 0, 'revoke', 'op'))
        self.assertEqual(1, self.anchor.generation)
        self.anchor.fault = None
        self.broker.restart(); self.broker.reconcile(channel_clear=True)
        self.assertEqual(1, self.anchor.read())
        self.denied('scope', self.read)

    def test_crash_at_each_transition_boundary(self):
        for point in ('intent', 'before_advance', 'after_advance', 'readback', 'ledger', 'inventory', 'audit', 'ack'):
            with self.subTest(point=point):
                a=Anchor('anchor-A'); i=Issuer(); l=Ledger(a,i); inv=Inventory(0, {'object': (self.scope,1,'active')}); b=Broker(l,inv,FakeKey())
                b.enroll(self.scope,'anchor-A','object',1, approved=True); b.reconcile(channel_clear=True)
                self.denied('crash', lambda: b.transition(self.scope,0,'revoke','op', fault=point))
                b.restart()
                self.denied('closed', lambda: b.read(self.scope,'object',1))
                try: b.reconcile(channel_clear=True)
                except Denied: pass
                self.denied('scope', lambda: b.read(self.scope,'object',1))
                self.assertLessEqual(a.generation,1)

    def test_restore_restart_reinstall_and_copied_store(self):
        old = self.broker.checkpoint()
        self.broker.restore_closed(old)
        self.denied('closed', self.read)
        self.broker.reconcile(channel_clear=True)
        self.assertEqual('published', self.read())
        self.broker.transition(self.scope,0,'revoke','op')
        self.broker.restore_closed(old)
        self.denied('closed', self.read)
        self.denied('generation', lambda: self.broker.reconcile(channel_clear=True))
        self.broker.restart()
        self.denied('closed', self.read)
        self.denied('reconcile', lambda: self.broker.reconcile(channel_clear=False))
        copied=Broker(self.ledger,self.inventory,self.key)
        self.denied('enroll', lambda: copied.reconcile(channel_clear=True))

    def test_failed_key_audit_time_and_mutation(self):
        for fault in ('authorize','release','decrypt','publish','audit'):
            self.key.fault = fault
            self.denied(fault, self.read)
            self.assertNotIn('published', self.key.output)
            self.key.output.clear()
        self.key.fault = None
        for clock in ('missing','stale','rollback','uncertain'):
            self.denied('time', lambda: self.read(time_required=True, trusted_time=clock))
        self.denied('reentrant', lambda: self.read(hook=lambda phase: self.broker.transition(self.scope,0,'revoke','nested')))

    def test_read_revoke_order_and_partial_output(self):
        self.broker.receive_withdrawal(self.scope)
        self.denied('scope', self.read)
        self.assertEqual([], self.key.events)
        self.broker.finish_withdrawal('op')
        self.assertEqual(1,self.anchor.read())

    def test_withdrawal_during_read_and_during_output(self):
        def revoke_during_read(phase):
            if phase == 'decrypt':
                self.broker.receive_withdrawal(self.scope)
                self.denied('in flight', lambda: self.broker.finish_withdrawal('early'))
        self.denied('withdrawal', lambda: self.read(hook=revoke_during_read))
        self.assertEqual([], self.key.output)
        self.assertEqual('acknowledged', self.broker.finish_withdrawal('op'))

    def test_withdrawal_after_partial_publication_records_escape(self):
        def revoke_after_first(phase):
            if phase == 'published_chunk':
                self.broker.receive_withdrawal(self.scope)
        self.denied('withdrawal', lambda: self.read(chunks=('one','two'), hook=revoke_after_first))
        self.assertEqual(['one'], self.key.output)
        self.assertEqual(['one'], self.broker.escaped)
        self.assertEqual('acknowledged', self.broker.finish_withdrawal('op'))

    def test_restore_cannot_replace_live_authority_or_inventory(self):
        old = self.broker.checkpoint()
        self.broker.transition(self.scope, 0, 'revoke', 'op')
        live_head = deepcopy(self.ledger.head); live_inventory = deepcopy(self.inventory.objects)
        self.broker.restore_closed(old)
        self.assertEqual(live_head, self.ledger.head)
        self.assertEqual(live_inventory, self.inventory.objects)
        self.denied('closed', self.read)
        self.denied('generation', lambda: self.broker.reconcile(channel_clear=True))

    def test_authenticated_head_fields_must_match_commitment(self):
        self.ledger.head['scope'] = ('B','source','research','development')
        self.denied('authority', self.read)

    def test_publication_and_withdrawal_have_shared_fence(self):
        def withdraw(phase):
            if phase == 'published_chunk':
                self.broker.receive_withdrawal(self.scope)
                self.denied('in flight', lambda: self.broker.finish_withdrawal('early'))
        self.denied('withdrawal', lambda: self.read(chunks=('first','second'), hook=withdraw))
        self.assertEqual(['first'], self.broker.escaped)
        self.assertEqual(['first'], self.key.output)
        self.assertEqual('acknowledged', self.broker.finish_withdrawal('op'))
        self.denied('scope', self.read)

    def test_failed_audit_cannot_acknowledge_revoke(self):
        self.key.fault = 'audit'
        self.denied('audit', lambda: self.broker.transition(self.scope, 0, 'revoke', 'op'))
        self.assertNotIn('op', self.ledger.operations)
        self.assertTrue(self.broker.closed)

    def test_replayed_operation_requires_current_authority(self):
        self.broker.transition(self.scope, 0, 'revoke', 'op')
        self.anchor.generation += 1
        self.denied('generation', lambda: self.broker.transition(self.scope, 0, 'revoke', 'op'))

    def test_tampered_pending_intent_never_recovers(self):
        self.denied('crash', lambda: self.broker.transition(self.scope, 0, 'revoke', 'op', fault='after_advance'))
        self.ledger.intent = (self.scope, 0, 'delete', 'forged')
        self.broker.restart()
        self.denied('intent', lambda: self.broker.reconcile(channel_clear=True))
        self.assertTrue(self.broker.closed)

    def test_reentrant_mutation_cannot_be_swallowed_then_publish(self):
        def attempt(phase):
            if phase == 'decrypt':
                try: self.broker.transition(self.scope, 0, 'revoke', 'nested')
                except Denied: pass
        self.denied('reentrant', lambda: self.read(hook=attempt))
        self.assertEqual([], self.key.output)

    def test_restore_after_delete_never_reactivates_data(self):
        old = self.broker.checkpoint()
        self.broker.transition(self.scope, 0, 'delete', 'delete-op')
        self.broker.restore_closed(old)
        self.denied('closed', self.read)
        self.denied('generation', lambda: self.broker.reconcile(channel_clear=True))
        self.assertEqual('withdrawn', self.inventory.objects['object'][2])

    def test_stale_data_with_current_authority_is_rejected(self):
        old = self.broker.checkpoint()
        self.inventory.objects['object'] = (self.scope, 2, 'active')
        self.broker.restore_closed(old)
        self.broker.reconcile(channel_clear=True)
        self.denied('inventory', lambda: self.broker.read(self.scope, 'object', 1))

    def test_withdrawal_after_authorization_prevents_key_release(self):
        def withdraw(phase):
            if phase == 'authorize': self.broker.receive_withdrawal(self.scope)
        self.denied('withdrawal', lambda: self.read(hook=withdraw))
        self.assertNotIn('release', self.key.events)
        self.assertEqual([], self.key.output)

    def test_two_authenticated_heads_same_generation_fail(self):
        head = dict(self.ledger.head)
        head['commitment'] = ('different',)
        self.denied('duplicate generation', lambda: self.issuer.issue(head))

    def test_crash_before_durable_intent_requires_channel_reconciliation(self):
        self.denied('crash', lambda: self.broker.transition(self.scope,0,'revoke','op',fault='before_intent'))
        self.assertEqual(0, self.anchor.generation)
        self.broker.restart()
        self.denied('reconcile', lambda: self.broker.reconcile(channel_clear=False))
        self.denied('closed', self.read)

    def test_crash_after_advance_recovers_only_restrictive_intent(self):
        self.denied('crash', lambda: self.broker.transition(self.scope,0,'revoke','op',fault='after_advance'))
        self.assertEqual(1, self.anchor.generation)
        self.assertEqual(0, self.ledger.head['generation'])
        self.broker.restart(); self.broker.reconcile(channel_clear=True)
        self.assertEqual('suppressed', self.ledger.head['state'])
        self.assertEqual(1, self.anchor.generation)
        self.assertEqual('acknowledged', self.broker.transition(self.scope,0,'revoke','op'))
        self.denied('scope', self.read)

    def test_crash_after_ledger_before_ack_is_idempotent(self):
        self.denied('crash', lambda: self.broker.transition(self.scope,0,'revoke','op',fault='ack'))
        self.assertEqual(1, self.anchor.generation)
        self.broker.restart(); self.broker.reconcile(channel_clear=True)
        self.assertEqual('acknowledged', self.broker.transition(self.scope,0,'revoke','op'))
        self.assertEqual(1, self.anchor.generation)
        self.denied('scope', self.read)

    def test_wrong_anchor_during_recovery_never_advances(self):
        self.denied('crash', lambda: self.broker.transition(self.scope,0,'revoke','op',fault='intent'))
        self.anchor.identity = 'untrusted'
        self.broker.restart()
        self.denied('anchor', lambda: self.broker.reconcile(channel_clear=True))
        self.assertEqual(0, self.anchor.generation)
        self.assertTrue(self.broker.closed)

    def test_lost_advance_response_is_reconciled_without_retry(self):
        self.anchor.fault = 'lost_result'
        self.denied('ambiguous', lambda: self.broker.transition(self.scope,0,'revoke','op'))
        self.assertEqual(1,self.anchor.generation)
        self.anchor.fault = None
        self.broker.restart(); self.broker.reconcile(channel_clear=True)
        self.assertEqual(1,self.anchor.generation)
        self.denied('scope', self.read)

    def test_threaded_read_and_withdrawal_are_fenced(self):
        reached = threading.Event(); proceed = threading.Event(); result = []
        def hook(phase):
            if phase == 'decrypt':
                reached.set()
                if not proceed.wait(2): raise AssertionError('test synchronization timed out')
        def reader():
            try: result.append(self.read(hook=hook))
            except Denied as error: result.append(str(error))
        thread = threading.Thread(target=reader); thread.start()
        try:
            self.assertTrue(reached.wait(2))
            self.broker.receive_withdrawal(self.scope)
            self.denied('in flight', lambda: self.broker.finish_withdrawal('op'))
        finally:
            proceed.set(); thread.join(3)
        self.assertFalse(thread.is_alive())
        self.assertEqual(['withdrawal pending'], result)
        self.assertEqual([], self.key.output)
        self.assertEqual('acknowledged', self.broker.finish_withdrawal('op'))

    def test_read_completed_before_withdrawal_is_recorded_as_escaped(self):
        self.assertEqual('published', self.read())
        self.broker.receive_withdrawal(self.scope)
        self.assertEqual(['published'], self.broker.escaped)
        self.assertEqual('acknowledged', self.broker.finish_withdrawal('op'))
        self.denied('scope', self.read)

    def test_audit_failure_cannot_be_bypassed_by_restart_reconcile(self):
        self.key.fault = 'audit'
        self.denied('audit', lambda: self.broker.transition(self.scope,0,'revoke','op'))
        self.broker.restart()
        self.denied('audit', lambda: self.broker.reconcile(channel_clear=True))
        self.assertTrue(self.broker.closed)
        self.assertNotIn('op', self.ledger.operations)
        self.key.fault = None
        self.broker.reconcile(channel_clear=True)
        self.assertEqual('acknowledged', self.broker.transition(self.scope,0,'revoke','op'))

    def test_audit_records_never_contain_raw_output(self):
        self.assertEqual('published', self.read(chunks=('invented-token',)))
        self.assertEqual('read_allowed', self.broker.audit[-1]['kind'])
        self.assertNotIn('invented-token', repr(self.broker.audit))
        self.broker.transition(self.scope,0,'revoke','op')
        self.assertEqual('suppressed', self.broker.audit[-1]['kind'])
        self.assertEqual(1, self.broker.audit[-1]['generation'])

    def test_ambiguous_before_advance_requires_readback(self):
        self.anchor.fault = 'ambiguous_before'
        self.denied('ambiguous', lambda: self.broker.transition(self.scope,0,'revoke','op'))
        self.assertEqual(0, self.anchor.generation)
        self.anchor.fault = None
        self.broker.restart(); self.broker.reconcile(channel_clear=True)
        self.assertEqual(1, self.anchor.generation)
        self.denied('scope', self.read)

    def test_new_broker_restart_requires_explicit_enrollment_and_reconciliation(self):
        self.assertEqual('published', self.read())
        reopened = Broker(self.ledger, self.inventory, FakeKey())
        self.assertEqual(self.broker.audit, reopened.audit)
        self.denied('enroll', lambda: reopened.reconcile(channel_clear=True))
        self.denied('enroll', lambda: reopened.attach(self.scope, 'anchor-A', {'object':1}, approved=False))
        reopened.attach(self.scope, 'anchor-A', {'object':1}, approved=True)
        self.denied('closed', lambda: reopened.read(self.scope,'object',1))
        reopened.reconcile(channel_clear=True)
        self.assertEqual('published', reopened.read(self.scope,'object',1))

    def test_partial_output_on_callback_failure_is_recorded(self):
        def fail(phase):
            if phase == 'published_chunk': raise RuntimeError('synthetic publication failure')
        with self.assertRaisesRegex(RuntimeError, 'publication failure'):
            self.read(chunks=('first', 'second'), hook=fail)
        self.assertEqual(['first'], self.key.output)
        self.assertEqual(['first'], self.broker.escaped)
        self.assertEqual('partial_exposure', self.broker.audit[-1]['kind'])
        self.assertNotIn('first', repr(self.broker.audit))

    def test_restart_during_read_cancels_before_publication(self):
        def restart(phase):
            if phase == 'decrypt': self.broker.restart()
        self.denied('reentrant', lambda: self.read(hook=restart))
        self.assertEqual([], self.key.output)
        self.assertTrue(self.broker.closed)

    def test_restore_and_reconcile_cannot_run_inside_read(self):
        snapshot = self.broker.checkpoint()
        for method in (lambda: self.broker.restore_closed(snapshot),
                       lambda: self.broker.reconcile(channel_clear=True)):
            def attempt(phase):
                if phase == 'decrypt':
                    try: method()
                    except Denied: pass
            self.denied('reentrant', lambda: self.read(hook=attempt))
            self.assertEqual([], self.key.output)

    def test_fresh_enrollment_remains_closed_until_reconciled(self):
        anchor = Anchor('other-anchor'); issuer = Issuer(); ledger = Ledger(anchor, issuer)
        inventory = Inventory(0, {'object': (self.scope, 1, 'active')}); broker = Broker(ledger,inventory,FakeKey())
        broker.enroll(self.scope,'other-anchor','object',1, approved=True)
        self.assertTrue(broker.closed)
        self.denied('closed', lambda: broker.read(self.scope,'object',1))
        broker.reconcile(channel_clear=True)
        self.assertEqual('published', broker.read(self.scope,'object',1))

    def test_denials_are_reason_coded_without_scope_disclosure(self):
        self.denied('scope', lambda: self.broker.read(('B','source','research','development'),'object',1))
        record = self.broker.audit[-1]
        self.assertEqual('denied', record['kind'])
        self.assertEqual('scope denied', record['reason'])
        self.assertNotIn('B', repr(record))

    def test_inventory_binds_full_scope_not_only_account(self):
        self.inventory.objects['object'] = (self.scope, 1, 'active')
        self.assertEqual('published', self.read())
        self.inventory.objects['object'] = (('A','other-source','research','development'), 1, 'active')
        self.denied('inventory', self.read)

    def test_bootstrap_needs_explicit_trusted_enrollment(self):
        anchor = Anchor('new'); issuer = Issuer(); ledger = Ledger(anchor,issuer)
        inventory = Inventory(0, {'object': (self.scope,1,'active')}); broker = Broker(ledger,inventory,FakeKey())
        self.denied('enroll', lambda: broker.enroll(self.scope,'new','object',1))
        self.assertTrue(broker.closed)
        self.assertIsNone(ledger.head)
        self.assertEqual(0, anchor.generation)

    def test_replayed_operation_with_changed_expected_generation_is_collision(self):
        self.broker.transition(self.scope, 0, 'revoke', 'op')
        self.denied('collision', lambda: self.broker.transition(self.scope, 99, 'revoke', 'op'))
        self.assertEqual(1, self.anchor.generation)

    def test_stale_inventory_generation_denies_before_key_release(self):
        self.inventory.generation = -1
        self.denied('inventory', self.read)
        self.assertEqual([], self.key.events)

    def test_missing_anchor_object_denies_before_key_release(self):
        self.ledger.anchor = None
        self.denied('anchor', self.read)
        self.assertEqual([], self.key.events)
        self.assertTrue(self.broker.closed)

    def test_wrong_scope_never_releases_key(self):
        for scope in (('B','source','research','development'), ('A','source','other','development')):
            self.denied('scope', lambda: self.broker.read(scope,'object',1))
        self.assertEqual([], self.key.events)

    def test_time_bound_read_requires_explicit_trusted_time(self):
        self.denied('time', lambda: self.read(time_required=True))
        self.assertEqual([], self.key.events)
        self.assertEqual('published', self.read(time_required=True, trusted_time='trusted'))

    def test_checkpoint_during_read_fails_closed(self):
        def attempt(phase):
            if phase == 'decrypt':
                try: self.broker.checkpoint()
                except Denied: pass
        self.denied('reentrant', lambda: self.read(hook=attempt))
        self.assertEqual([], self.key.output)

    def test_missing_durable_intent_cannot_be_ignored_on_restart(self):
        self.denied('crash', lambda: self.broker.transition(self.scope,0,'revoke','op',fault='before_advance'))
        self.ledger.intent = None  # Simulate a rolled-back or missing authority record.
        self.broker.restart()
        self.denied('intent', lambda: self.broker.reconcile(channel_clear=True))
        self.assertTrue(self.broker.closed)
        self.assertEqual(0, self.anchor.generation)

    def test_repeated_withdrawal_ack_does_not_advance_again(self):
        self.broker.receive_withdrawal(self.scope)
        self.assertEqual('acknowledged', self.broker.finish_withdrawal('op'))
        self.assertEqual('acknowledged', self.broker.finish_withdrawal('op'))
        self.assertEqual(1, self.anchor.generation)

    def test_withdrawal_invalidates_scoped_descendants_not_sibling(self):
        self.inventory.objects['derived'] = (self.scope, 2, 'active')
        sibling = ('B','other-source','research','development')
        self.inventory.objects['sibling'] = (sibling, 1, 'active')
        self.broker.transition(self.scope,0,'revoke','op')
        self.assertEqual('withdrawn', self.inventory.objects['object'][2])
        self.assertEqual('withdrawn', self.inventory.objects['derived'][2])
        self.assertEqual('active', self.inventory.objects['sibling'][2])

    def test_time_bound_grant_cannot_be_bypassed_by_caller_flag(self):
        anchor = Anchor('new'); issuer = Issuer(); ledger = Ledger(anchor,issuer)
        inventory = Inventory(0, {'object': (self.scope,1,'active')}); key=FakeKey()
        broker = Broker(ledger,inventory,key)
        broker.enroll(self.scope,'new','object',1,approved=True,time_bound=True)
        broker.reconcile(channel_clear=True)
        self.denied('time', lambda: broker.read(self.scope,'object',1,time_required=False))
        self.assertEqual([], key.events)
        self.assertEqual('published', broker.read(self.scope,'object',1,trusted_time='trusted'))

    def test_changed_inventory_between_authorize_and_release_denies(self):
        def change(phase):
            if phase == 'authorize': self.inventory.objects['object'] = (self.scope,1,'withdrawn')
        self.denied('inventory', lambda: self.read(hook=change))
        self.assertNotIn('release', self.key.events)
        self.assertEqual([], self.key.output)

    def test_changed_anchor_between_authorize_and_release_denies(self):
        def change(phase):
            if phase == 'authorize': self.anchor.generation += 1
        self.denied('generation', lambda: self.read(hook=change))
        self.assertNotIn('release', self.key.events)
        self.assertEqual([], self.key.output)

    def test_contention_fails_closed(self):
        self.broker._gate.acquire()
        try:
            self.denied('contention', self.read)
        finally:
            self.broker._gate.release()

    def test_data_rollback_at_each_read_boundary(self):
        for changed_after in ('authorize', 'release', 'decrypt', 'publish', 'published_chunk'):
            with self.subTest(changed_after=changed_after):
                self.setUp()
                def rollback(phase):
                    if phase == changed_after: self.broker.data_versions['object'] = 0
                self.denied('inventory', lambda: self.read(hook=rollback, chunks=('one', 'two')))
                if changed_after == 'authorize': self.assertNotIn('release', self.key.events)
                self.assertEqual(['one'] if changed_after == 'published_chunk' else [], self.key.output)

    def test_data_version_mutations_use_publication_fence(self):
        started, finished = threading.Event(), threading.Event()
        def rollback():
            started.set()
            self.broker.data_versions.update({'object': 0})
            finished.set()
        with self.broker._state:
            thread = threading.Thread(target=rollback)
            thread.start()
            self.assertTrue(started.wait(2))
            self.assertFalse(finished.wait(0.05))
        thread.join(timeout=2)
        self.assertFalse(thread.is_alive())
        self.denied('inventory', self.read)

    def test_interrupt_before_and_after_anchor_advance_recovers_closed(self):
        for after in (False, True):
            with self.subTest(after=after):
                self.setUp()
                advance = self.anchor.advance
                def interrupt(expected):
                    if after: advance(expected)
                    raise KeyboardInterrupt()
                with patch.object(self.anchor, 'advance', side_effect=interrupt):
                    with self.assertRaises(KeyboardInterrupt):
                        self.broker.transition(self.scope, 0, 'revoke', 'interrupt')
                self.denied('closed', self.read)
                self.broker.restart()
                self.broker.reconcile(channel_clear=True)
                self.assertEqual(1, self.anchor.generation)
                self.denied('scope', self.read)

    def test_caught_nested_read_fences_outer_read(self):
        def nested(phase):
            if phase == 'decrypt':
                try: self.read()
                except Denied: pass
        self.denied('reentrant', lambda: self.read(hook=nested))
        self.assertEqual([], self.key.output)

    def test_other_thread_read_contention_does_not_cancel_owner(self):
        attempted = []
        def contend(phase):
            if phase == 'authorize':
                def reader():
                    try: self.read()
                    except Denied: attempted.append('denied')
                thread = threading.Thread(target=reader)
                thread.start()
                thread.join(timeout=2)
                self.assertFalse(thread.is_alive())
        self.assertEqual('published', self.read(hook=contend))
        self.assertEqual(['denied'], attempted)

    def test_repeated_receipt_and_partial_failure_count_output_once(self):
        self.read(chunks=('same', 'same'))
        def fail(phase):
            if phase == 'published_chunk': raise RuntimeError('interrupted')
        with self.assertRaises(RuntimeError): self.read(hook=fail, chunks=('same', 'later'))
        self.broker.receive_withdrawal(self.scope)
        self.broker.receive_withdrawal(self.scope)
        self.assertEqual(['same', 'same', 'same'], self.broker.escaped)


    def test_nested_generator_cannot_publish(self):
        def chunks():
            try: self.read()
            except Denied: pass
            yield 'must-not-publish'
        self.denied('reentrant', lambda: self.read(chunks=chunks()))
        self.assertEqual([], self.key.output)
        self.assertEqual([], self.broker.escaped)
        self.assertEqual('published', self.read())

    def test_nested_generator_between_chunks_preserves_first_once(self):
        def chunks():
            yield 'first'
            try: self.read()
            except Denied: pass
            yield 'must-not-publish'
        self.denied('reentrant', lambda: self.read(chunks=chunks()))
        self.assertEqual(['first'], self.key.output)
        self.assertEqual(['first'], self.broker.escaped)
        self.broker.receive_withdrawal(self.scope)
        self.assertEqual(['first'], self.broker.escaped)

    def test_ordinary_generator_publishes_all_chunks(self):
        self.assertEqual('published', self.read(chunks=(x for x in ('one', 'two'))))
        self.assertEqual(['one', 'two'], self.key.output)

    def test_other_thread_contention_inside_generator_does_not_poison(self):
        denied = []
        def chunks():
            def competing():
                try: self.read()
                except Denied: denied.append(True)
            thread = threading.Thread(target=competing)
            thread.start()
            thread.join(timeout=2)
            self.assertFalse(thread.is_alive())
            yield 'valid'
        self.assertEqual('published', self.read(chunks=chunks()))
        self.assertEqual([True], denied)
        self.assertEqual(['valid'], self.key.output)

    def test_generator_withdrawal_before_yield_denies_output(self):
        def chunks():
            self.broker.receive_withdrawal(self.scope)
            yield 'must-not-publish'
        self.denied('withdrawal', lambda: self.read(chunks=chunks()))
        self.assertEqual([], self.key.output)
        self.assertEqual([], self.broker.escaped)

    def test_generator_data_change_before_yield_denies_output(self):
        def chunks():
            self.broker.data_versions['object'] = 0
            yield 'must-not-publish'
        self.denied('inventory', lambda: self.read(chunks=chunks()))
        self.assertEqual([], self.key.output)

    def test_generator_authority_change_before_yield_denies_output(self):
        def chunks():
            self.anchor.generation += 1
            yield 'must-not-publish'
        self.denied('generation', lambda: self.read(chunks=chunks()))
        self.assertEqual([], self.key.output)

    def test_generator_exception_preserves_first_exposure(self):
        def chunks():
            yield 'first'
            raise RuntimeError('iterator failure')
        with self.assertRaisesRegex(RuntimeError, 'iterator failure'):
            self.read(chunks=chunks())
        self.assertEqual(['first'], self.key.output)
        self.assertEqual(['first'], self.broker.escaped)


if __name__ == '__main__':
    unittest.main()
