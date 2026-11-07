"""Historical adversarial schedules retained as synthetic regressions."""
import unittest
from unittest.mock import patch
import test_p2f_private_authority as fixtures
from p2f_private_authority import Denied
class ReviewSchedules(unittest.TestCase):
 def setUp(self):
  self.x=fixtures.AuthorityMatrix(); self.x.setUp()
 def test_rolled_back_data_between_auth_and_release_is_denied(self):
  def hook(phase):
   if phase=='authorize': self.x.broker.data_versions['object']=0
  with self.assertRaises(Denied): self.x.read(hook=hook)
  self.assertNotIn('release',self.x.key.events)
 def test_interrupt_pending_revoke_leaves_reads_closed(self):
  with patch.object(self.x.anchor,'advance',side_effect=KeyboardInterrupt()):
   with self.assertRaises(KeyboardInterrupt): self.x.broker.transition(self.x.scope,0,'revoke','interrupt')
  with self.assertRaises(Denied): self.x.read()
 def test_caught_nested_read_prevents_outer_publication(self):
  def hook(phase):
   if phase=='decrypt':
    try:self.x.read()
    except Denied:pass
  with self.assertRaises(Denied): self.x.read(hook=hook)
  self.assertEqual([],self.x.key.output)
 def test_duplicate_withdrawal_receipt_does_not_double_count(self):
  self.x.read(); self.x.broker.receive_withdrawal(self.x.scope); self.x.broker.receive_withdrawal(self.x.scope)
  self.assertEqual(['published'],self.x.broker.escaped)
 def test_uncertain_advance_recovery_does_not_double_advance(self):
  self.x.anchor.set_fault('lost_result')
  with self.assertRaises(Denied):self.x.broker.transition(self.x.scope,0,'revoke','op')
  self.x.anchor.set_fault(None);self.x.broker.restart();self.x.broker.reconcile(channel_clear=True)
  self.assertEqual(1,self.x.anchor.generation)
 def test_head_mutation_during_authorize_is_denied(self):
  def hook(phase):
   if phase=='authorize':
    head=self.x.ledger.head;head['operation']='changed';self.x.ledger.set_head(head)
  with self.assertRaises(Denied):self.x.read(hook=hook)
  self.assertNotIn('release',self.x.key.events)
if __name__=='__main__':unittest.main(verbosity=2)
