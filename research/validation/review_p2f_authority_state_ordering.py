"""Review-only synthetic state-ordering reproductions; failures identify defects."""
import sys
import unittest

import test_p2f_private_authority as fixtures
from p2f_private_authority import Denied


class Review(unittest.TestCase):
    def setUp(self):
        self.x = fixtures.AuthorityMatrix()
        self.x.setUp()

    def test_anchor_change_during_final_inventory_validation(self):
        x = self.x
        calls = []

        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name == 'verify':
                    calls.append(1)
                    if len(calls) == 7:
                        x.anchor.advance(0)
                return super().__hash__()

        try:
            x.broker.read(x.scope, Obj('object'), 1)
        except Denied:
            pass
        self.assertEqual(1, x.anchor.generation)
        self.assertEqual([], x.key.output, 'output appended after anchor advanced')

    def test_inventory_change_during_final_data_lookup(self):
        x = self.x
        calls = []

        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name == '__getitem__':
                    calls.append(1)
                    if len(calls) == 7:
                        x.inventory.set_object('object', (x.scope, 0, 'active'))
                return super().__hash__()

        try:
            x.broker.read(x.scope, Obj('object'), 1)
        except Denied:
            pass
        self.assertEqual(0, x.inventory.objects['object'][1])
        self.assertEqual([], x.key.output, 'output appended after inventory changed')

    def test_both_flags_during_final_inventory_validation(self):
        x = self.x
        calls = []

        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name == 'verify':
                    calls.append(1)
                    if len(calls) == 7:
                        x.broker.receive_withdrawal(x.scope)
                        try:
                            x.read()
                        except Denied:
                            pass
                return super().__hash__()

        with self.assertRaises(Denied):
            x.broker.read(x.scope, Obj('object'), 1)
        self.assertTrue(x.broker.withdrawal)
        self.assertTrue(x.broker._violation)
        self.assertEqual([], x.key.output)
        self.assertFalse(x.broker.busy)
        self.assertIsNone(x.broker._read_owner)
        self.assertTrue(x.broker._gate.acquire(blocking=False))
        x.broker._gate.release()


if __name__ == '__main__':
    unittest.main(verbosity=2)
