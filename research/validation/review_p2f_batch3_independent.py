"""Additional invented-only coordinated-state review schedules."""
import sys
import unittest
from unittest.mock import patch

import test_p2f_private_authority as fixtures
from p2f_private_authority import Denied


class Review(unittest.TestCase):
    def setUp(self):
        self.x = fixtures.AuthorityMatrix()
        self.x.setUp()

    def test_failed_mutation_during_final_inventory_invalidates(self):
        x, calls = self.x, []
        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name == 'verify':
                    calls.append(True)
                    if len(calls) == 7:
                        try:
                            x.issuer.set_fault('available', 'invalid')
                        except Denied:
                            pass
                return super().__hash__()
        with self.assertRaisesRegex(Denied, 'epoch'):
            x.broker.read(x.scope, Obj('object'), 1)
        self.assertEqual([], x.key.output)
        self.assertFalse(x.broker.busy)
        self.assertIsNone(x.broker._read_owner)
        self.assertFalse(x.broker._gate.locked())

    def test_system_exit_during_read_initialization_cleans_up(self):
        x = self.x
        with patch.object(x.broker.coordinator, 'mutate', side_effect=SystemExit(3)):
            with self.assertRaises(SystemExit):
                x.read()
        self.assertFalse(x.broker.busy)
        self.assertIsNone(x.broker._read_owner)
        self.assertFalse(x.broker._gate.locked())
        self.assertEqual('published', x.read())

    def test_successful_read_epoch_accounting(self):
        x, seen = self.x, []
        before = x.broker.coordinator.epoch
        x.read(hook=lambda phase: seen.append((phase, x.broker.coordinator.epoch)))
        self.assertEqual(before + 1, dict(seen)['authorize'])
        self.assertEqual(before + 2, dict(seen)['audit'])
        self.assertEqual(before + 2, x.broker.coordinator.epoch)


if __name__ == '__main__':
    unittest.main(verbosity=2)
