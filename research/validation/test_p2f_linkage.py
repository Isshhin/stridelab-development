"""All IDs and timestamps are fabricated. No file or custodian network access."""
import unittest

from p2f_linkage import PilotLedger, Quarantine


S = ("A","P","G","development-pilot","server")
K = ("C","recorder","x","FIT")
J = ("C","recorder","y","TCX")
H, H2 = "a"*64, "b"*64


def T(hour):
    return f"2026-10-01T{hour:02}:00:00Z"


def pilot(scope=S):
    return PilotLedger(scope, T(0), {K:"B", J:"B"}, {"E1":("B","O","R1"),
                       "E2":("B","O","R2"), "E3":("B","O","R1")}, [(K,H,H2)])


def row(id_, kind, hour, data, scope=S, **changes):
    return dict(dict(id=id_, kind=kind, scope=scope, recorded=T(hour), effective=T(hour),
                     available=T(hour), sequence=hour, data=data), **changes)


def base(locked=False):
    initial = dict(opportunity="O", plan="P1", version="V1", revision="R1", parent=None,
                   parent_version=None, intent="easy30", locked=locked)
    return [row("I","initial",1,initial), row("R","receipt",2,dict(opportunity="O",revision="R1"))]


def decision():
    return row("D","decision",3,dict(opportunity="O",revision="R1",offered=True,
                                      offered_intent="easy20",inputs=["I","R"]))


def edit(kind="manual_edit", hour=5):
    return row("X",kind,hour,dict(opportunity="O",plan="P1",version="V2",revision="R2",
            parent="R1",parent_version="V1",intent="easy20",locked=False,decision="D"))


def source(payload_digest=H, key=K, id_="S1", hour=13, **changes):
    p = dict(connection=key[0],namespace=key[1],external=key[2],format=key[3],
             digest=payload_digest, activity="B", performed_at=T(8),ended_at=T(9),imported_at=T(hour))
    return row(id_,"source",hour,dict(p,**changes))


def link(rev="R1", evidence="E1", hour=14):
    return row("L1","link",hour,dict(opportunity="O",activity="B",revision=rev,evidence=evidence))


def replay(rows, time=20):
    led = pilot()
    led.append(S,rows)
    return led.replay(S,T(time))


class Linkage(unittest.TestCase):
    def blocked(self, *rows):
        with self.assertRaises(Quarantine):
            replay(list(rows))

    def test_matched_link_requires_independent_mapping_and_prestart_receipt(self):
        self.assertEqual(replay(base()+[source(),link()])["links"]["L1"]["revision"],"R1")
        self.blocked(base()[0],source(),link())
        self.blocked(*base(),source(),link(evidence="unreviewed"))

    def test_scope_isolation_unknown_owner_and_partition(self):
        ledger = pilot()
        for index, other in ((0,"B"),(0,""),(1,"other"),(2,"expired"),(3,"held-out"),(4,"offline")):
            bad = list(S)
            bad[index] = other
            with self.assertRaises(Quarantine):
                ledger.append(S,[dict(source(),scope=bad)])
            with self.assertRaises(Quarantine):
                ledger.replay(bad,T(20))
        with self.assertRaises(Quarantine):
            pilot(("A","P","G","held-out","server"))
        self.assertFalse(ledger.replay(S,T(20))["activities"])

    def test_unplanned_activity_and_plan_without_activity(self):
        self.assertFalse(replay([source()])["activities"]["B"]["links"])
        self.assertEqual(replay(base())["opportunities"]["O"]["performance"],"unknown")

    def test_duplicate_export_and_cross_format_review(self):
        state = replay([source(),source(),source(id_="retry",hour=14),
                        source(key=J,id_="alias",hour=15,payload_digest=H2)])
        self.assertEqual((len(state["activities"]),len(state["sources"])),(1,2))
        self.blocked(source(),source(key=("C","recorder","similar","TCX"),id_="?",hour=15))
        self.blocked(source(),source(key=J,id_="alias",hour=15,activity="other"))

    def test_changed_source_requires_private_authorization_and_previous_bytes(self):
        self.blocked(source(),source(H2,id_="new",hour=15))
        self.blocked(source(),source("c"*64,id_="new",hour=15,previous_digest=H,
                                    supersedes="S1",reason="reviewed"))

    def test_corrected_source_invalidates_derived_link_historical_snapshot_stable(self):
        rows = base()+[source(),link()]
        old = replay(rows,time=15)
        corr = source(H2,id_="new",hour=16,previous_digest=H,supersedes="S1",reason="export fix")
        self.assertEqual(old,replay(rows+[corr],time=15))
        now = replay(rows+[corr])
        self.assertFalse(now["links"])
        self.assertEqual(now["opportunities"]["O"]["performance"],"unknown")
        self.assertEqual(next(iter(now["sources"].values()))["digest"],H2)
        self.assertNotEqual(now["snapshot"],old["snapshot"])

    def test_performed_not_visible_before_upload_or_offline_sync(self):
        later = dict(source(),available=T(15),sequence=15)
        rows = base()+[decision(),later]
        self.assertNotIn("B",replay(rows,time=14)["activities"])
        self.assertIn("B",replay(rows,time=16)["activities"])
        self.assertEqual(replay(rows)["decisions"]["D"]["inputs"],["I","R"])

    def test_future_measurement_or_context_not_decision_input(self):
        ctx = row("C","context",8,dict(target="D",status="reported",reason="schedule"))
        for missing in ["S1","C","X"]:
            d = decision()
            d["data"]["inputs"].append(missing)
            self.blocked(*base(),d,edit(),ctx,source())

    def test_future_revision_and_invalid_ancestry(self):
        d = decision()
        d["data"]["revision"] = "R2"
        self.blocked(*base(),d,edit())
        for key,val in [("parent","R2"),("parent","missing"),("parent_version","V99"),
                        ("revision","R1"),("version","V1")]:
            x = edit()
            x["data"][key] = val
            self.blocked(*base(),x)

    def test_choice_uses_matching_locked_state_and_exact_offered_intent(self):
        good = edit("apply_choice")
        state = replay(base()+[decision(),good])
        self.assertEqual(state["choices"]["D"]["state"],"accepted")
        self.blocked(*base(True),decision(),good)
        self.blocked(*base(True),edit())
        good["data"]["intent"] = "different"
        self.blocked(*base(),decision(),good)

    def test_stale_acceptance_after_manual_edit(self):
        later = edit("apply_choice",hour=7)
        later["id"] = "late"
        later["data"].update(version="V3",revision="R3")
        self.blocked(*base(),decision(),edit(),later)

    def test_late_backdated_edit_does_not_rewrite_prestart(self):
        x = dict(edit(hour=11),effective=T(4))
        late_receipt = row("RR","receipt",12,dict(opportunity="O",revision="R2"))
        rows = base()+[x,late_receipt,source()]
        self.assertEqual(replay(rows,time=10)["plans"]["P1"],"V1")
        self.assertEqual(replay(rows+[link()])["links"]["L1"]["revision"],"R1")
        self.blocked(*rows,link("R2","E2"))

    def test_prestart_manual_edit_and_receipt(self):
        self.blocked(*base(),edit(),source(),link())
        rows = base()+[edit(),row("R2r","receipt",6,dict(opportunity="O",revision="R2")),source()]
        self.assertEqual(replay(rows+[link("R2","E2")])["links"]["L1"]["revision"],"R2")
        self.blocked(*rows,link())

    def test_unordered_ties_and_view_watermark(self):
        a,b = base()
        b.update(available=T(1),effective=T(1),recorded=T(1))
        led = pilot()
        led.append(S,[b,a])
        self.assertEqual(led.replay(S,T(1))["event_ids"],[])
        self.assertEqual(led.replay(S,T(1),1)["event_ids"],["I"])
        self.assertEqual(led.replay(S,T(1),2)["event_ids"],["I","R"])
        self.blocked(a,dict(b,sequence=None))

    def test_reversed_input_and_identical_retry(self):
        rows = base()+[decision(),source(),link()]
        self.assertEqual(replay(rows),replay(list(reversed(rows))+rows))
        self.blocked(source(),dict(source(),effective=T(10)))

    def test_timezone_rollover_and_bad_clocks(self):
        obs = source(performed_at="2026-10-02T00:00:00+14:00",ended_at=T(11))
        self.assertEqual(replay([obs])["activities"]["B"]["performed_at"],
                         "2026-10-02T00:00:00+14:00")
        self.blocked(source(performed_at="2026-10-01T08:00:00"))
        self.blocked(source(imported_at=T(8)))
        self.blocked(source(ended_at=T(7)))
        self.blocked(dict(source(),recorded=T(14)))

    def test_manual_occurrence_unknown_dose_declined_reason(self):
        m = row("M","manual_occurrence",10,dict(activity="B",performed_at=T(8),ended_at=T(9)))
        c = row("C","context",11,dict(target="M",status="withheld",reason=None))
        rows = base()+[m,c,link()]
        state = replay(rows)
        self.assertEqual(state["activities"]["B"]["basis"],["manual_occurrence"])
        self.assertIsNone(state["activities"]["B"]["dose"])
        self.assertEqual(state["contexts"]["C"]["status"],"withheld")
        self.assertFalse(replay(base()+[m])["contexts"])
        state = replay(rows+[source()])
        self.assertEqual(len(state["activities"]),1)
        self.assertCountEqual(state["activities"]["B"]["basis"],["manual_occurrence","source"])

    def test_declined_choice_and_missing_followup(self):
        led = pilot()
        w = row("W","followup_window",5,dict(opportunity="O",protocol="F",opens=T(10),closes=T(15)))
        c = row("N","choice",4,dict(decision="D",state="rejected"))
        led.append(S,base()+[decision(),w,c])
        self.assertEqual(led.replay(S,T(20))["plans"]["P1"],"V1")
        for h,expected in [(3,"unknown"),(6,"not_due"),(12,"awaiting"),(20,"overdue_missing")]:
            self.assertEqual(led.followup_state(S,"O",T(h)),expected)

    def test_reused_identical_bytes_with_incompatible_bout(self):
        led = PilotLedger(S,T(0),{K:"B",J:"other"},{})
        led.append(S,[source(),source(key=J,id_="S2",hour=14,activity="other")])
        with self.assertRaises(Quarantine):
            led.replay(S,T(20))

    def test_late_followup_requires_independent_evidence_and_correct_window(self):
        led = PilotLedger(S,T(0),{},{},followups=[("F1","O","F",T(12))])
        w = row("W","followup_window",5,dict(opportunity="O",protocol="F",opens=T(10),closes=T(15)))
        f = row("F","followup_observation",18,dict(opportunity="O",observation="F1",observed_at=T(12)))
        led.append(S,base()+[w,f])
        self.assertEqual(led.followup_state(S,"O",T(17)),"overdue_missing")
        self.assertEqual(led.followup_state(S,"O",T(19)),"observed")
        self.blocked(*base(),w,f)
        self.assertEqual(replay(base()+[w,row("C","followup_censor",13,
                          dict(opportunity="O",reason="coverage ended"))])["followups"]["O"]["censor"]["event"],"C")

    def test_revoke_edge_leaves_bout_and_denominator(self):
        rows = base()+[source(),link(),row("U","revoke_link",15,dict(link="L1",reason="audit"))]
        self.assertFalse(replay(rows)["links"])
        self.assertTrue(replay(rows,time=15)["links"])
        self.assertEqual(replay(rows)["opportunities"]["O"]["performance"],"unknown")

    def test_withdrawal_invalidates_replay_reimport_and_preserves_other_account(self):
        other = ("B",)+S[1:]
        a,b = pilot(),pilot(other)
        rows = base()+[source(),link()]
        a.append(S,rows)
        b.append(other,[dict(r,scope=other) for r in rows])
        self.assertTrue(a.replay(S,T(20))["links"])
        before = b.replay(other,T(20))
        a.withdraw(S)
        with self.assertRaises(Quarantine):
            a.replay(S,T(20))
        with self.assertRaises(Quarantine):
            a.append(S,[source()])
        self.assertEqual(before,b.replay(other,T(20)))


if __name__ == "__main__":
    unittest.main()
