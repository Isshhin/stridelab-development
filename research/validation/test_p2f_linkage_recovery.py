"""Negative controls for grant time, restore boundaries and audit supersession."""
from copy import deepcopy
from hashlib import sha256
import unittest

from p2f_linkage import CurrentAuthority, PilotLedger, Quarantine
from p2f_linkage_audit import AuditSeries
from test_p2f_linkage import S, K, T, auth, base, decision, edit, link, pilot, row, source


class Recovery(unittest.TestCase):
    def setUp(self):
        self.now = T(20)
        self.a = CurrentAuthority(lambda: self.now)
        self.a.register(S,T(0),T(22),("alias-A",))
        self.led = pilot(authority=self.a)
        self.other = ("B","other-study","grant-b",*S[3:])
        self.a.register(self.other,T(0),T(22),("alias-B",))
        self.b = pilot(self.other,self.a)

    def test_before_grant_and_expired_now_block_historical_replay(self):
        self.led.append(S,base()+[source(),link()])
        for bad in ("2026-09-30T23:59:59Z",T(22),"invalid time"):
            self.now = bad
            with self.assertRaises(Quarantine):
                self.led.replay(S,T(15))
            with self.assertRaises(Quarantine):
                self.led.append(S,[source()])
            with self.assertRaises(Quarantine):
                pilot(authority=self.a)

    def test_event_before_grant_and_future_availability(self):
        self.now = T(12)
        with self.assertRaises(Quarantine):
            self.led.append(S,[source()])
        with self.assertRaises(Quarantine):
            self.led.replay(S,T(13))
        with self.assertRaises(Quarantine):
            self.led.append(S,[dict(base()[0],effective="2026-09-30T23:00:00Z")])

    def test_restore_and_suppression_before_backup_read(self):
        self.led.append(S,base()+[source(),link()])
        saved = self.led.checkpoint(S)
        restored = pilot(authority=self.a)
        restored.restore(S,lambda: saved)
        self.assertEqual(restored.replay(S,T(19)),self.led.replay(S,T(19)))
        other_rows = [dict(r,scope=self.other) for r in base()+[source(),link()]]
        self.b.append(self.other,other_rows)
        other_state = self.b.replay(self.other,T(19))
        self.a.revoke(S)  # steward acts outside both working ledger objects
        for ledger in (self.led,restored):
            with self.assertRaises(Quarantine):
                ledger.restore(S,lambda: self.fail("withdrawn backup was opened"))
            with self.assertRaises(Quarantine):
                ledger.replay(S,T(19))
            with self.assertRaises(Quarantine):
                ledger.append(S,[source()])
        self.assertEqual(other_state,self.b.replay(self.other,T(19)))
        with self.assertRaises(Quarantine):
            deepcopy(self.a)

    def test_stale_restored_working_state_cannot_supplant_latest(self):
        self.led.append(S,base())
        old = self.led.checkpoint(S)
        fork = pilot(authority=self.a)
        fork.restore(S,lambda:old)
        self.led.append(S,[source(),link()])
        for op in (lambda:fork.restore(S,lambda:old),lambda:fork.replay(S,T(19)),
                   lambda:fork.append(S,[decision()])):
            with self.assertRaises(Quarantine):
                op()
        self.assertTrue(self.led.replay(S,T(19))["links"])

    def test_alias_collision_and_renamed_withdrawn_subject(self):
        self.a.bind_alias(S,"renamed")
        self.assertEqual(self.a.resolve(S,"renamed"),S)
        for action in (lambda:self.a.bind_alias(self.other,"renamed"),
                       lambda:self.a.resolve(self.other,"alias-A"),lambda:self.a.resolve(S,"unknown")):
            with self.assertRaises(Quarantine):
                action()
        self.led.withdraw(S)
        for alias in ("renamed","alias-A"):
            with self.assertRaises(Quarantine):
                self.a.register(("new","new","new",*S[3:]),T(0),T(22),(alias,))

    def test_external_ids_reused_between_accounts_do_not_resolve_foreign_plan_or_revision(self):
        b = [dict(r,scope=self.other) for r in base()+[source(),link()]]
        self.b.append(self.other,b)
        self.assertTrue(self.b.replay(self.other,T(19))["links"])
        # Identical external ID and digest may exist in isolated ledgers; source
        # identity in B never supplies the missing mapping or revision in A.
        empty = PilotLedger(S,T(0),{},{},authority=self.a)
        empty.append(S,[source()])
        with self.assertRaises(Quarantine):
            empty.replay(S,T(19))
        for field in ("plan","parent"):
            ledger = pilot()
            x = edit()
            x["data"][field] = "other-account-object"
            ledger.append(S,base()+[x])
            with self.assertRaises(Quarantine):
                ledger.replay(S,T(19))

    def test_backdated_context_and_offline_receipt_never_in_earlier_decision(self):
        ctx = row("C","context",17,dict(target="I",status="reported",reason="class"),
                  recorded=T(2),effective=T(2))
        self.led.append(S,base()+[decision(),ctx])
        early = self.led.replay(S,T(4))
        later = self.led.replay(S,T(19))
        self.assertNotIn("C",early["contexts"])
        self.assertIn("C",later["contexts"])
        self.assertEqual(early["decisions"],later["decisions"])


class AuditRecovery(unittest.TestCase):
    def setUp(self):
        self.raw, self.ref = b"synthetic original", b"frozen independent worksheet"
        self.h = sha256(self.raw).hexdigest()
        self.led = pilot()
        self.led.append(S,base()+[source(self.h),link()])
        def reject(raw, ref):
            raise Quarantine("unsupported sample")
        self.runners = {
            "old":lambda raw,ref: {"status":"PRIVATE_TRACE_QC_ONLY","value":1},
            "new":lambda raw,ref: {"status":"PRIVATE_TRACE_QC_ONLY","value":2},
            "bad":reject}
        self.series = AuditSeries(self.led,S,self.runners)

    def pin(self, version):
        return self.series.pin("S1","L1",version,self.ref)

    def test_quarantined_version_remains_historical_after_corrected_parser(self):
        q = self.pin("bad")
        self.assertFalse(self.series.compare(q,self.raw,self.ref)["active"])
        with self.assertRaises(Quarantine):
            self.series.current()
        new = self.pin("new")
        self.assertTrue(self.series.compare(new,self.raw,self.ref)["active"])
        self.assertEqual(self.series.compare(q,self.raw,self.ref)["disposition"],"QUARANTINED")
        self.assertEqual(self.series.current()["version"],new)

    def test_parser_upgrade_disables_old_result_until_new_audit_then_replays_old(self):
        v = self.pin("old")
        old = self.series.compare(v,self.raw,self.ref)
        self.assertTrue(old["active"])
        n = self.pin("new")
        with self.assertRaises(Quarantine):
            self.series.current()
        self.assertFalse(self.series.compare(v,self.raw,self.ref)["active"])
        fresh = self.series.compare(n,self.raw,self.ref)
        self.assertNotEqual(old["output_digest"],fresh["output_digest"])
        self.assertEqual(old["output_digest"],self.series.compare(v,self.raw,self.ref)["output_digest"])
        # A changed runner masquerading under a pinned version cannot reproduce.
        changed = AuditSeries(self.led,S,dict(self.runners,old=self.runners["new"]))
        with self.assertRaises(Quarantine):
            changed.compare(v,self.raw,self.ref)
        bad_current = AuditSeries(self.led,S,dict(self.runners,new=self.runners["old"]))
        with self.assertRaises(Quarantine):
            bad_current.compare(n,self.raw,self.ref)
        with self.assertRaises(Quarantine):
            self.series.current()

    def test_substituted_original_or_worksheet_and_withdrawal(self):
        v = self.pin("new")
        for raw,ref in [(self.raw+b"x",self.ref),(self.raw,self.ref+b"x")]:
            with self.assertRaises(Quarantine):
                self.series.compare(v,raw,ref)
        self.series.compare(v,self.raw,self.ref)
        self.led.withdraw(S)
        for action in (self.series.current,lambda:self.series.compare(v,self.raw,self.ref)):
            with self.assertRaises(Quarantine):
                action()

    def test_corrected_original_and_edge_invalidate_active_derivative(self):
        revised = b"corrected synthetic original"
        h2 = sha256(revised).hexdigest()
        ledger = PilotLedger(S,T(0),{K:"B"},{"E1":("B","O","R1"),"E3":("B","O","R1")},
                             [(K,self.h,h2)],authority=auth())
        series = AuditSeries(ledger,S,self.runners)
        ledger.append(S,base()+[source(self.h),link()])
        v = series.pin("S1","L1","old",self.ref)
        previous = series.compare(v,self.raw,self.ref)
        ledger.append(S,[source(h2,id_="new",hour=16,previous_digest=self.h,supersedes="S1",
                                reason="reviewed correction")])
        with self.assertRaises(Quarantine):
            series.current()
        with self.assertRaises(Quarantine):
            series.pin("new","L1","new",self.ref)
        ledger.append(S,[dict(link(evidence="E3",hour=17),id="L2")])
        n = series.pin("new","L2","new",b"new reference")
        self.assertTrue(series.compare(n,revised,b"new reference")["active"])
        reproduced = series.compare(v,self.raw,self.ref)
        self.assertEqual(reproduced["output_digest"],previous["output_digest"])
        self.assertFalse(reproduced["active"])
