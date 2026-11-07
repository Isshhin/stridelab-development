"""Historical withdrawal-review schedules retained as synthetic regressions."""
import sys
import threading
import unittest
import test_p2f_private_authority as fixtures
from p2f_private_authority import Denied

class Review(unittest.TestCase):
    def setUp(self):
        self.x=fixtures.AuthorityMatrix();self.x.setUp()

    def test_withdrawal_in_final_inventory_hash_prevents_append(self):
        x=self.x;calls=[]
        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name=='verify':
                    calls.append(1)
                    if len(calls)==7:x.broker.receive_withdrawal(x.scope)
                return super().__hash__()
        with self.assertRaises(Denied):x.broker.read(x.scope,Obj('object'),1)
        self.assertEqual([],x.key.output)
        self.assertEqual([],x.broker.escaped)

    def test_withdrawal_in_final_inventory_equality_prevents_append(self):
        x=self.x;calls=[]
        class Version(int):
            def __eq__(self,other):
                if sys._getframe(1).f_code.co_name=='verify':
                    calls.append(1)
                    if len(calls)==7:x.broker.receive_withdrawal(x.scope)
                return super().__eq__(other)
        with self.assertRaises(Denied):x.broker.read(x.scope,'object',Version(1))
        self.assertEqual([],x.key.output)

    def test_withdrawal_in_data_lookup_preserves_only_prior_chunk(self):
        x=self.x;armed=[]
        class Obj(str):
            def __hash__(self):
                if armed and sys._getframe(1).f_code.co_name=='__getitem__':
                    armed.clear();x.broker.receive_withdrawal(x.scope)
                return super().__hash__()
        def hook(phase):
            if phase=='published_chunk':armed.append(True)
        with self.assertRaises(Denied):x.broker.read(x.scope,Obj('object'),1,hook=hook,chunks=('first','bad'))
        self.assertEqual(['first'],x.key.output)
        self.assertEqual(['first'],x.broker.escaped)

    def test_benign_custom_object(self):
        class Obj(str):
            def __hash__(self):return super().__hash__()
            def __eq__(self,other):return super().__eq__(other)
        self.assertEqual('published',self.x.broker.read(self.x.scope,Obj('object'),1))

    def test_normal_generator_and_competitor(self):
        denied=[];x=self.x
        def chunks():
            def other():
                try:x.read()
                except Denied:denied.append(True)
            thread=threading.Thread(target=other);thread.start();thread.join(2)
            self.assertFalse(thread.is_alive());yield 'one';yield 'two'
        self.assertEqual('published',x.read(chunks=chunks()))
        self.assertEqual([True],denied);self.assertEqual(['one','two'],x.key.output)

    def test_prior_nested_hash_is_rejected(self):
        x=self.x;calls=[]
        class Obj(str):
            def __hash__(self):
                if sys._getframe(1).f_code.co_name=='verify':
                    calls.append(1)
                    if len(calls)==7:
                        try:x.read()
                        except Denied:pass
                return super().__hash__()
        with self.assertRaises(Denied):x.broker.read(x.scope,Obj('object'),1)
        self.assertEqual([],x.key.output);self.assertIsNone(x.broker._read_owner)
        self.assertEqual('published',x.read())

if __name__=='__main__':unittest.main(verbosity=2)
