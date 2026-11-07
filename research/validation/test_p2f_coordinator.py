"""Synthetic coordinator ownership and ordering tests."""
import threading
import unittest

from p2f_private_authority import Anchor, Inventory, Issuer, Ledger, StateCoordinator
from p2f_private_custody import Broker, FakeKey, Versions


class CoordinatorTests(unittest.TestCase):
    def test_mutable_copy_override_cannot_leak_state_alias(self):
        state = StateCoordinator()
        class Unsafe:
            def __deepcopy__(self, memo): return self
        value = Unsafe()
        ledger = Ledger(Anchor('a', coordinator=state), Issuer(coordinator=state), coordinator=state)
        with self.assertRaises(ValueError): ledger.set_head({'value': value})
        self.assertIsNone(ledger.head)
        self.assertEqual(1, state.epoch)

    def test_owned_domain_mutations_do_not_leak_aliases(self):
        state = StateCoordinator()
        anchor = Anchor('a', coordinator=state)
        issuer = Issuer(coordinator=state)
        ledger = Ledger(anchor, issuer, coordinator=state)
        inventory = Inventory(0, {'o': (('a',), 1, 'active')}, coordinator=state)
        scope = ('a',)
        issuer.set_fault('available', False)
        self.assertEqual(1, state.epoch)
        issuer.set_fault('available', True)
        ledger.commit(scope, 0, 'active', 'op')
        old_epoch = state.epoch
        head = ledger.head
        head['state'] = 'suppressed'
        self.assertEqual('active', ledger.head['state'])
        self.assertEqual(old_epoch, state.epoch)
        inventory.set_object('o', (scope, 2, 'active'))
        self.assertEqual(old_epoch + 1, state.epoch)
        copy = inventory.objects
        copy['o'] = (scope, 3, 'active')
        self.assertEqual(2, inventory.objects['o'][1])
        with self.assertRaises(AttributeError): inventory.generation = 5

    def test_nested_maps_are_copy_out_and_removal_is_coordinated(self):
        state = StateCoordinator()
        anchor = Anchor('a', coordinator=state)
        issuer = Issuer(coordinator=state)
        ledger = Ledger(anchor, issuer, coordinator=state)
        inventory = Inventory(0, {}, coordinator=state)
        value = {'nested': ['original']}
        ledger.set_operation('op', value)
        issuer.set_commitment(0, value)
        issuer.set_intent('op', value)
        value['nested'].append('external')
        for view in (ledger.operations, issuer.commitments, issuer.intents):
            item = next(iter(view.values()))
            self.assertEqual(['original'], item['nested'])
            item['nested'].append('copy')
        self.assertEqual(['original'], ledger.operations['op']['nested'])
        self.assertEqual(['original'], issuer.commitments[0]['nested'])
        self.assertEqual(['original'], issuer.intents['op']['nested'])
        inventory.set_object('o', (('a',), 1, 'active'))
        before = state.epoch
        inventory.delete_object('o')
        ledger.delete_operation('op')
        issuer.delete_commitment(0)
        issuer.delete_intent('op')
        self.assertEqual(before + 4, state.epoch)

    def test_mixed_coordinators_are_rejected(self):
        one, two = StateCoordinator(), StateCoordinator()
        with self.assertRaisesRegex(ValueError, 'mixed coordinator'):
            Ledger(Anchor('a', coordinator=one), Issuer(coordinator=two))

    def test_failure_and_noop_attempts_invalidate(self):
        state = StateCoordinator()
        issuer = Issuer(coordinator=state)
        issuer.set_fault('available', True)
        self.assertEqual(1, state.epoch)
        with self.assertRaises(ValueError): issuer.set_fault('available', 'bad')
        self.assertEqual(2, state.epoch)
        with self.assertRaises(KeyError): issuer.delete_intent('missing')
        self.assertEqual(3, state.epoch)

    def test_broker_shared_coordinator_and_restore_epoch(self):
        state = StateCoordinator()
        anchor = Anchor('a', coordinator=state)
        issuer = Issuer(coordinator=state)
        ledger = Ledger(anchor, issuer, coordinator=state)
        scope = ('account', 'source', 'purpose', 'partition')
        inventory = Inventory(0, {'object': (scope, 1, 'active')}, coordinator=state)
        broker = Broker(ledger, inventory, FakeKey())
        self.assertIs(state.lock, broker._state)
        self.assertIs(state, broker.coordinator)
        broker.enroll(scope, 'a', 'object', 1, approved=True)
        broker.reconcile(channel_clear=True)
        snapshot = broker.checkpoint()
        before = state.epoch
        broker.restore_closed(snapshot)
        self.assertGreater(state.epoch, before)
        broker.reconcile(channel_clear=True)
        self.assertEqual('published', broker.read(scope, 'object', 1))
        with self.assertRaises(AttributeError): broker.data_versions = {}
        with self.assertRaises(AttributeError): broker.ledger = ledger

    def test_restore_versions_and_failed_deletes_count(self):
        state = StateCoordinator()
        versions = Versions(state, {'a': 1})
        versions.replace({'b': 2})
        with self.assertRaises(KeyError): del versions['missing']
        self.assertEqual(2, state.epoch)
        self.assertEqual({'b': 2}, versions.snapshot())

    def test_audit_entries_are_coordinated_and_copy_out(self):
        state = StateCoordinator()
        ledger = Ledger(Anchor('a', coordinator=state), Issuer(coordinator=state), coordinator=state)
        record = {'kind': 'suppressed', 'detail': ['original']}
        ledger.audit.append(record)
        self.assertEqual(1, state.epoch)
        record['detail'].append('input')
        copy = ledger.audit[-1]
        copy['detail'].append('output')
        self.assertEqual(['original'], ledger.audit[-1]['detail'])
        with self.assertRaises(AttributeError): ledger.audit = []

    def test_each_domain_mutation_uses_one_shared_epoch(self):
        state = StateCoordinator()
        anchor = Anchor('a', coordinator=state)
        issuer = Issuer(coordinator=state)
        ledger = Ledger(anchor, issuer, coordinator=state)
        inventory = Inventory(0, {}, coordinator=state)
        versions = Versions(state)
        actions = (
            lambda: anchor.set_generation(4),
            lambda: issuer.set_fault('available', False),
            lambda: ledger.set_head({'state': 'corrupt'}),
            lambda: ledger.set_intent(('intent',)),
            lambda: ledger.set_operation('op', ('value',)),
            lambda: inventory.set_generation(4),
            lambda: inventory.set_object('obj', (('scope',), 1, 'active')),
            lambda: versions.__setitem__('obj', 1),
        )
        for index, action in enumerate(actions, 1):
            action()
            self.assertEqual(index, state.epoch)

    def test_inventory_and_authority_recovery_fault_controls_advance_epoch(self):
        state = StateCoordinator()
        anchor = Anchor('a', coordinator=state)
        issuer = Issuer(coordinator=state)
        ledger = Ledger(anchor, issuer, coordinator=state)
        inventory = Inventory(0, {'object': (('a',), 1, 'active')}, coordinator=state)
        inventory.set_generation(-1)
        inventory.set_object('object', (('a',), 0, 'withdrawn'))
        ledger.set_intent(('recovery',))
        issuer.set_intent('recovery', ('pending',))
        before = state.epoch
        inventory.set_generation(0)
        inventory.set_object('object', (('a',), 1, 'active'))
        ledger.set_intent(None)
        issuer.delete_intent('recovery')
        self.assertEqual(before + 4, state.epoch)
        inventory.verify(0, ('a',), 'object', 1)
        self.assertIsNone(ledger.intent)

    def test_anchor_advance_and_fault_attempts_share_epoch(self):
        state = StateCoordinator()
        anchor = Anchor('a', coordinator=state)
        anchor.advance(0)
        self.assertEqual(1, state.epoch)
        anchor.set_fault('ambiguous_before')
        self.assertEqual(2, state.epoch)
        with self.assertRaises(ValueError): anchor.advance(1)
        self.assertEqual(3, state.epoch)
        self.assertEqual(1, anchor.generation)
        with self.assertRaises(AttributeError): anchor.generation = 2

    def test_versions_are_owned_and_copy_out(self):
        state = StateCoordinator()
        versions = Versions(state, {'a': 1})
        versions['a'] = 2
        self.assertEqual(1, state.epoch)
        snapshot = versions.snapshot()
        snapshot['a'] = 99
        self.assertEqual(2, versions['a'])
        del versions['a']
        self.assertEqual(2, state.epoch)
        versions.replace({'b': 3})
        self.assertEqual(3, state.epoch)
        self.assertEqual({'b': 3}, versions.snapshot())

    def test_initial_epoch_and_attempts_are_monotonic(self):
        state = StateCoordinator()
        self.assertEqual(0, state.epoch)
        self.assertEqual('ok', state.mutate(lambda: 'ok'))
        self.assertEqual(1, state.epoch)
        with self.assertRaisesRegex(ValueError, 'fault'):
            state.mutate(lambda: (_ for _ in ()).throw(ValueError('fault')))
        self.assertEqual(2, state.epoch)
        state.mutate(lambda: None)
        self.assertEqual(3, state.epoch)

    def test_nested_mutation_is_reentrant(self):
        state = StateCoordinator()
        self.assertEqual('nested', state.mutate(lambda: state.mutate(lambda: 'nested')))
        self.assertEqual(2, state.epoch)

    def test_other_thread_waits_for_shared_lock(self):
        state = StateCoordinator()
        started, finished = threading.Event(), threading.Event()
        def worker():
            started.set()
            state.mutate(lambda: None)
            finished.set()
        with state.lock:
            thread = threading.Thread(target=worker)
            thread.start()
            self.assertTrue(started.wait(2))
            self.assertFalse(finished.wait(.05))
        thread.join(2)
        self.assertFalse(thread.is_alive())
        self.assertEqual(1, state.epoch)


if __name__ == '__main__':
    unittest.main()
