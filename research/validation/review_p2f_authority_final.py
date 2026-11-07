"""Independent review-only schedules; one known publication failure remains."""
import sys
import threading
import unittest
import test_p2f_private_authority as fixtures
from p2f_private_authority import Denied

class Review(unittest.TestCase):
    def setUp(self):
        self.x = fixtures.AuthorityMatrix(); self.x.setUp()

    def nested(self):
        try: self.x.read()
        except Denied: pass

    def test_nested_before_first_yield(self):
        def chunks():
            self.nested(); yield 'bad'
        with self.assertRaises(Denied): self.x.read(chunks=chunks())
        self.assertEqual([], self.x.key.output); self.assertEqual([], self.x.broker.escaped)

    def test_nested_between_yields(self):
        def chunks():
            yield 'first'; self.nested(); yield 'bad'
        with self.assertRaises(Denied): self.x.read(chunks=chunks())
        self.assertEqual(['first'], self.x.key.output); self.assertEqual(['first'], self.x.broker.escaped)

    def test_ordinary_generator(self):
        self.assertEqual('published', self.x.read(chunks=(v for v in ('a', 'b'))))
        self.assertEqual(['a', 'b'], self.x.key.output)

    def test_competing_thread_is_not_owner(self):
        results=[]
        def chunks():
            def other():
                try: self.x.read()
                except Denied: results.append('denied')
            t=threading.Thread(target=other);t.start();t.join(2)
            self.assertFalse(t.is_alive());yield 'ok'
        self.assertEqual('published', self.x.read(chunks=chunks()));self.assertEqual(['denied'],results)

    def test_withdrawal_before_yield(self):
        def chunks():
            self.x.broker.receive_withdrawal(self.x.scope);yield 'bad'
        with self.assertRaises(Denied): self.x.read(chunks=chunks())
        self.assertEqual([],self.x.key.output)

    def test_data_change_before_yield(self):
        def chunks():
            self.x.broker.data_versions['object']=0;yield 'bad'
        with self.assertRaises(Denied):self.x.read(chunks=chunks())
        self.assertEqual([],self.x.key.output)

    def test_authority_change_before_yield(self):
        def chunks():
            self.x.anchor.set_generation(self.x.anchor.generation+1);yield 'bad'
        with self.assertRaises(Denied):self.x.read(chunks=chunks())
        self.assertEqual([],self.x.key.output)

    def test_iterator_exception_and_reuse(self):
        def chunks():
            yield 'first';raise RuntimeError('fault')
        with self.assertRaisesRegex(RuntimeError,'fault'):self.x.read(chunks=chunks())
        self.assertEqual(['first'],self.x.broker.escaped)
        self.assertEqual('published',self.x.read(chunks=('second',)))

    def test_duplicate_receipt_preserves_equal_chunks(self):
        self.x.read(chunks=('x','x'));self.x.broker.receive_withdrawal(self.x.scope);self.x.broker.receive_withdrawal(self.x.scope)
        self.assertEqual(['x','x'],self.x.broker.escaped)

    def test_owner_and_lock_reset(self):
        def chunks():
            self.nested();yield 'bad'
        with self.assertRaises(Denied):self.x.read(chunks=chunks())
        self.assertIsNone(self.x.broker._read_owner);self.assertFalse(self.x.broker._gate.locked())
        self.assertEqual('published',self.x.read())

    def test_nested_during_locked_final_inventory_validation(self):
        x=self.x; calls=[]
        class ObjectId(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name=='verify':
                    calls.append(1)
                    if len(calls)==7:
                        try: x.read()
                        except Denied: pass
                return super().__hash__()
        with self.assertRaises(Denied):x.broker.read(x.scope,ObjectId('object'),1)
        self.assertEqual([],x.key.output)
        self.assertEqual([],x.broker.escaped)

if __name__=='__main__':unittest.main(verbosity=2)
