"""Historical correction-review schedules retained as synthetic regressions."""
import unittest, threading
from unittest.mock import patch
import test_p2f_private_authority as fixtures
from p2f_private_authority import Denied
class Review(unittest.TestCase):
 def setUp(self): self.x=fixtures.AuthorityMatrix();self.x.setUp()
 def test_nested_generator_cannot_publish(self):
  def chunks():
   try:self.x.read()
   except Denied:pass
   yield 'must-not-publish'
  with self.assertRaises(Denied):self.x.read(chunks=chunks())
  self.assertEqual([],self.x.key.output)
 def test_rollback_after_release_is_denied(self):
  def hook(p):
   if p=='release':self.x.broker.data_versions['object']=0
  with self.assertRaises(Denied):self.x.read(hook=hook)
  self.assertNotIn('decrypt',self.x.key.events)
 def test_rollback_between_chunks_preserves_first(self):
  def hook(p):
   if p=='published_chunk':self.x.broker.data_versions['object']=0
  with self.assertRaises(Denied):self.x.read(hook=hook,chunks=('a','b'))
  self.assertEqual(['a'],self.x.key.output)
 def test_equal_chunks_count_once_each(self):
  self.x.read(chunks=('same','same'));self.x.broker.receive_withdrawal(self.x.scope);self.x.broker.receive_withdrawal(self.x.scope)
  self.assertEqual(['same','same'],self.x.broker.escaped)
 def test_nested_authorize_poison_then_later_read(self):
  def hook(p):
   if p=='authorize':
    try:self.x.read()
    except Denied:pass
  with self.assertRaises(Denied):self.x.read(hook=hook)
  self.assertEqual('published',self.x.read())
 def test_system_exit_rethrown_and_closed(self):
  with patch.object(self.x.anchor,'advance',side_effect=SystemExit(9)):
   with self.assertRaises(SystemExit) as e:self.x.broker.transition(self.x.scope,0,'revoke','x')
  self.assertEqual(9,e.exception.code);self.assertTrue(self.x.broker.closed)
 def test_other_thread_cannot_poison(self):
  res=[]
  def hook(p):
   if p=='authorize':
    def worker():
     try:self.x.read()
     except Denied:res.append('denied')
    t=threading.Thread(target=worker);t.start();t.join(2);self.assertFalse(t.is_alive())
  self.assertEqual('published',self.x.read(hook=hook));self.assertEqual(['denied'],res)
 def test_receipt_before_any_output(self):
  self.x.broker.receive_withdrawal(self.x.scope);self.x.broker.receive_withdrawal(self.x.scope)
  self.assertEqual([],self.x.broker.escaped)
if __name__=='__main__':unittest.main(verbosity=2)
