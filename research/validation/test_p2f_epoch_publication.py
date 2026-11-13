"""Invented-only coordinated publication schedules."""
import sys
import threading
import unittest
from unittest.mock import patch
from p2f_private_authority import Anchor, Denied, Inventory, Issuer, Ledger, StateCoordinator
from p2f_private_custody import Broker, FakeKey
import test_p2f_private_authority as fixtures


class EpochPublicationTests(unittest.TestCase):
    def setUp(self):
        self.x = fixtures.AuthorityMatrix(); self.x.setUp()

    def run_mutation(self, mutate, *, lookup='verify', between=False):
        x, calls, armed = self.x, [], []
        before = x.broker.coordinator.epoch
        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name == lookup:
                    calls.append(True)
                    if (between and armed) or (not between and len(calls) == 7):
                        armed.clear()
                        mutate()
                return super().__hash__()
        def hook(phase):
            if between and phase == 'published_chunk': armed.append(True)
        with self.assertRaises(Denied):
            x.broker.read(x.scope, Obj('object'), 1, hook=hook,
                          chunks=('first', 'bad') if between else ('bad',))
        self.assertGreater(x.broker.coordinator.epoch, before)
        self.assertEqual(['first'] if between else [], x.key.output)
        self.assertEqual(['first'] if between else [], x.broker.escaped)
        self.assertFalse(x.broker.busy)
        self.assertIsNone(x.broker._read_owner)
        self.assertFalse(x.broker._gate.locked())

    def test_anchor_during_inventory(self):
        self.run_mutation(lambda: self.x.anchor.advance(0))

    def test_inventory_during_data(self):
        self.run_mutation(lambda: self.x.inventory.set_object('object', (self.x.scope, 0, 'active')), lookup='__getitem__')

    def test_version_during_inventory(self):
        self.run_mutation(lambda: self.x.broker.data_versions.__setitem__('object', 0))

    def test_ledger_during_inventory(self):
        self.run_mutation(lambda: self.x.ledger.set_head(self.x.ledger.head))

    def test_issuer_during_inventory(self):
        self.run_mutation(lambda: self.x.issuer.set_fault('available', True))

    def test_two_mutations_during_inventory(self):
        def mutate():
            self.x.issuer.set_fault('available', True)
            self.x.inventory.set_generation(0)
        self.run_mutation(mutate)

    def test_changed_then_restored_state_still_invalidates(self):
        def mutate():
            self.x.inventory.set_generation(-1)
            self.x.inventory.set_generation(0)
        self.run_mutation(mutate)

    def test_mutation_then_exception_cleans_up(self):
        x, calls = self.x, []
        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name == 'verify':
                    calls.append(True)
                    if len(calls) == 7:
                        x.inventory.set_generation(0)
                        raise RuntimeError('callback fault')
                return super().__hash__()
        with self.assertRaisesRegex(RuntimeError, 'callback fault'):
            x.broker.read(x.scope, Obj('object'), 1)
        self.assertEqual([], x.key.output)
        self.assertIsNone(x.broker._read_owner)
        self.assertFalse(x.broker._gate.locked())
        self.assertFalse(x.broker.busy)

    def test_mutation_in_initial_validation_denies(self):
        x, calls = self.x, []
        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name == 'verify' and not calls:
                    calls.append(True)
                    x.inventory.set_generation(0)
                return super().__hash__()
        with self.assertRaisesRegex(Denied, 'epoch'):
            x.broker.read(x.scope, Obj('object'), 1)
        self.assertEqual([], x.key.output)

    def test_unexpected_audit_mutation_invalidates(self):
        self.run_mutation(lambda: self.x.broker.audit.append({'kind': 'external'}))

    def test_mutation_between_chunks_keeps_first(self):
        self.run_mutation(lambda: self.x.inventory.set_generation(0), between=True)

    def test_normal_read_accounts_for_internal_audit(self):
        before = self.x.broker.coordinator.epoch
        self.assertEqual('published', self.x.read())
        self.assertEqual(before + 2, self.x.broker.coordinator.epoch)
        self.assertEqual(['published'], self.x.key.output)

    def test_key_output_is_not_a_mutable_alias(self):
        self.assertEqual('published', self.x.read())
        self.x.key.output.append('forged')
        self.assertEqual(['published'], self.x.key.output)
        with self.assertRaises(AttributeError): self.x.broker.key = object()

    def test_fake_key_effect_is_not_a_public_mutation_path(self):
        with self.assertRaises(AttributeError): self.x.key.step('release')
        self.assertEqual([], self.x.key.events)

    def test_nested_output_and_exposure_do_not_leak_mutable_aliases(self):
        payload = {'parts': ['original']}
        self.x.read(chunks=(payload,))
        payload['parts'].append('input mutation')
        output = self.x.key.output
        output[0]['parts'].append('output mutation')
        self.x.broker.receive_withdrawal(self.x.scope)
        escaped = self.x.broker.escaped
        escaped[0]['parts'].append('exposure mutation')
        self.assertEqual([{'parts': ['original']}], self.x.key.output)
        self.assertEqual([{'parts': ['original']}], self.x.broker.escaped)

    def test_key_fault_is_coordinated(self):
        before = self.x.broker.coordinator.epoch
        self.x.key.set_fault('release')
        self.assertEqual(before + 1, self.x.broker.coordinator.epoch)
        with self.assertRaisesRegex(Denied, 'release'): self.x.read()
        self.assertEqual([], self.x.key.output)

    def test_admission_flags_are_not_directly_writable(self):
        for name in ('closed', 'withdrawal', 'enrolled', 'scope', 'anchor_identity', 'restored_head'):
            with self.subTest(name=name), self.assertRaises(AttributeError):
                setattr(self.x.broker, name, None)

    def test_busy_flag_cannot_bypass_nested_transition_invalidation(self):
        x = self.x
        def hook(phase):
            if phase == 'decrypt':
                with self.assertRaises(AttributeError): x.broker.busy = False
                try: x.broker.transition(x.scope, 0, 'revoke', 'nested')
                except Denied: pass
        with self.assertRaises(Denied): x.read(hook=hook)
        self.assertEqual([], x.key.output)

    def test_interrupt_during_read_initialization_releases_gate_and_owner(self):
        x = self.x
        with patch.object(x.broker.coordinator, 'mutate', side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt): x.read()
        self.assertFalse(x.broker.busy)
        self.assertIsNone(x.broker._read_owner)
        self.assertFalse(x.broker._gate.locked())
        self.assertEqual('published', x.read())

    def test_current_rejects_mutation_during_scope_comparison(self):
        x, invoked = self.x, []
        class Scope(tuple):
            def __eq__(self, other):
                if not invoked:
                    invoked.append(True)
                    x.inventory.set_generation(0)
                return super().__eq__(other)
        with self.assertRaisesRegex(Denied, 'epoch'):
            x.broker.current(Scope(x.scope))
        self.assertEqual([True], invoked)

    def test_custom_coordinator_cannot_enter_publication_boundary(self):
        class CustomCoordinator(StateCoordinator): pass
        coordinator = CustomCoordinator()
        ledger = Ledger(Anchor('a', coordinator=coordinator), Issuer(coordinator=coordinator))
        inventory = Inventory(0, {}, coordinator=coordinator)
        with self.assertRaises(Denied): Broker(ledger, inventory, FakeKey())

    def test_custom_ledger_and_inventory_cannot_supply_fake_audit(self):
        class CustomLedger(Ledger): pass
        class CustomInventory(Inventory): pass
        state = StateCoordinator()
        anchor, issuer = Anchor('a', coordinator=state), Issuer(coordinator=state)
        with self.assertRaises(Denied):
            Broker(CustomLedger(anchor, issuer), Inventory(0, {}, coordinator=state), FakeKey())
        with self.assertRaises(Denied):
            Broker(Ledger(anchor, issuer), CustomInventory(0, {}, coordinator=state), FakeKey())

    def test_custom_broker_cannot_override_final_epoch_lookup(self):
        class CustomBroker(Broker): pass
        with self.assertRaises(Denied):
            CustomBroker(self.x.ledger, self.x.inventory, FakeKey())

    def test_withdrawal_blocks_other_open_broker_with_same_scope(self):
        x = self.x
        other = Broker(x.ledger, x.inventory, FakeKey())
        other.attach(x.scope, 'anchor-A', {'object': 1}, approved=True)
        other.reconcile(channel_clear=True)
        self.assertEqual('published', other.read(x.scope, 'object', 1, chunks=('earlier',)))
        x.broker.receive_withdrawal(x.scope)
        with self.assertRaises(Denied): other.read(x.scope, 'object', 1)
        self.assertEqual(['earlier'], other.key.output)
        self.assertEqual(['earlier'], other.escaped)
        x.broker.receive_withdrawal(x.scope)
        self.assertEqual(['earlier'], other.escaped)

    def test_shared_key_cannot_have_two_active_brokers(self):
        x = self.x
        with self.assertRaises(Denied):
            Broker(x.ledger, x.inventory, x.key)
        x.broker.restart()
        replacement = Broker(x.ledger, x.inventory, x.key)
        x.broker.reconcile(channel_clear=True)
        with self.assertRaises(Denied): x.read()
        replacement.attach(x.scope, 'anchor-A', {'object': 1}, approved=True)
        replacement.reconcile(channel_clear=True)
        self.assertEqual('published', replacement.read(x.scope, 'object', 1))

    def test_withdrawal_accounts_output_across_key_transfer_once(self):
        x = self.x
        x.read(chunks=('old',))
        x.broker.restart()
        replacement = Broker(x.ledger, x.inventory, x.key)
        replacement.attach(x.scope, 'anchor-A', {'object': 1}, approved=True)
        replacement.reconcile(channel_clear=True)
        replacement.read(x.scope, 'object', 1, chunks=('new',))
        replacement.receive_withdrawal(x.scope)
        replacement.receive_withdrawal(x.scope)
        self.assertEqual(['old'], x.broker.escaped)
        self.assertEqual(['new'], replacement.escaped)

    def test_competing_transition_during_locked_validation_does_not_deadlock(self):
        x, calls, denied = self.x, [], []
        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name == 'verify':
                    calls.append(True)
                    if len(calls) == 7:
                        def compete():
                            try: x.broker.transition(x.scope, 0, 'revoke', 'other')
                            except Denied: denied.append(True)
                        thread = threading.Thread(target=compete)
                        thread.start(); thread.join(1)
                        if thread.is_alive(): raise RuntimeError('transition blocked')
                return super().__hash__()
        self.assertEqual('published', x.broker.read(x.scope, Obj('object'), 1))
        self.assertEqual([True], denied)


if __name__ == '__main__': unittest.main()
