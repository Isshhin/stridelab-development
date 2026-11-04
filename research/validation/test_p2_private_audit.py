"""Executable generated-only CLI rehearsal. Never consumes participant files/arguments."""
from datetime import timedelta
from hashlib import sha256
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import p2_private_audit as audit
from test_p2_trace import tcx, point, tick


def save(path, data):
    raw = json.dumps(data, sort_keys=True).encode()
    path.write_bytes(raw)
    return sha256(raw).hexdigest()


def stage(base):
    """Caller supplies a new, EMPTY generated-test directory only."""
    if any(base.iterdir()):
        raise ValueError("nonempty synthetic base")
    control, root = base / "synthetic-authority", base / "synthetic-staging"
    control.mkdir()
    root.mkdir()
    for name in ("linkage", "immutable-original", "independent-reference", "comparison"):
        (root/name).mkdir()
    (control/"lease.lock").write_bytes(b"L")
    scope = ["synthetic-account","synthetic-study","synthetic-grant","development-pilot","synthetic-view"]
    key = ["synthetic-connection","synthetic-namespace","synthetic-source","tcx"]
    raw = tcx(point(t) for t in range(0,1801,5))
    checksum = sha256(raw).hexdigest()
    original = root/"immutable-original"/"source.tcx"
    original.write_bytes(raw)
    original.chmod(stat.S_IREAD)
    stamp = lambda t: tick(t).isoformat()
    def event(eid, kind, sec, **data):
        return {"id":eid,"kind":kind,"scope":scope,"effective":stamp(sec),
                "recorded":stamp(sec),"available":stamp(sec),"data":data}
    events = [
        event("plan","initial",-90,opportunity="opp",plan="plan",version="v1",revision="r1",
              parent=None,parent_version=None,intent="easy",locked=False),
        event("receipt","receipt",-80,opportunity="opp",revision="r1"),
        event("source","source",3600,activity="bout",performed_at=stamp(0),ended_at=stamp(1800),
              imported_at=stamp(3600),connection=key[0],namespace=key[1],external=key[2],
              format="tcx",digest=checksum),
        event("edge","link",3650,activity="bout",opportunity="opp",revision="r1",evidence="review")]
    cfg = {"events":events,"source_key":key,"activity":"bout","review_id":"review",
           "review_edge":["bout","opp","r1"],"source_id":"source","edge_id":"edge","cutoff":stamp(4000)}
    # Reference created from fixture arithmetic, never populated by the measured output.
    expected = {"identity":{"account":scope[0],"study":scope[1],"activity":"bout","opportunity":"opp",
                "revision":"r1","plan":"plan","source_id":"source","start_utc":stamp(0)},
                "points":[[stamp(t),3*t,132,None] for t in range(0,1801,5)],
                "qc":{"source_session_ordinal":0,"source_lap_ordinal":0,
                      "first_to_last_sample_elapsed_s":1800,"lap_reported_time_s":None,
                      "lap_reported_m":None,"lap_reported_mean_hr_bpm":None,
                      "lap_reported_max_hr_bpm":None,"lap_native_cadence":None,
                      "duration_s":1200,"distance_m":3600,"start_offset_s":600,
                      "quarter_valid_s":[300]*4,"quarter_mean_hr_bpm":[132]*4,
                      "quarter_distance_m":[900]*4,"max_observed_interval_s":5}}
    sealed = {"frozen_at":stamp(3700),"reference":{"source_digest":checksum,"device_epoch":"syn-epoch",
              "epochs_seen":["syn-epoch"],"method":"calibrated_footpod","calibration":"syn-cal",
              "sensor":"electrical_chest_strap","marker_crossings":[stamp(600)],
              "timer_events":[[stamp(0),"start"],[stamp(1800),"stop"]],"segment_timer_seconds":1200,
              "reference_id":"synthetic-ref","reference_complete":True,"artifact_free":True,
              "configured_interval_s":5,"route_card_version":"syn-route","firmware":"syn-fw"},
              "expected":expected}
    now = audit.now()
    record = {"workspace":str(root),"decision":"SOURCE_ADMITTED","suppressed":False,
              "confirmatory_excluded":True,"custody_verified":True,"current_registry_verified":True,
              "reference_sealed":True,"receipt_ref":"SYNTHETIC ONLY", "steward_ref":"SYNTHETIC ONLY",
              "independence_review_ref":"SYNTHETIC ONLY","operations":dict.fromkeys(audit.OPS, True),
              "grant_start":stamp(-100),"grant_end":(now+timedelta(days=1)).isoformat(),
              "verified_at":now.isoformat(),"execution_expires":(now+timedelta(minutes=5)).isoformat(),
              "scope":scope,"aliases":["synthetic-alias"],"partition":"development-pilot",
              "code_commit":"0"*40,"modules":{k:sha256(Path(audit.__file__).with_name(k).read_bytes()).hexdigest()
                                                for k in audit.FILES},
              "command":audit.VERSION,"schema":audit.SCHEMA,"parser":audit.p2_trace.VERSION,
              "original_sha256":checksum,"format":"tcx",
              "journal_sha256":save(root/"linkage"/"journal.json", cfg),
              "reference_sha256":save(root/"independent-reference"/"sealed.json", sealed)}
    save(control/"current.json",record)
    return control, root, record


def invoke(control, *args):
    return subprocess.run([sys.executable,"-E","-s","-B",str(Path(audit.__file__)),*args],
        cwd=control, env={**os.environ,"P2F_AUTHORITY_DIR":str(control)},
        capture_output=True, text=True, check=False, timeout=45)


def expect(control, args, status):
    p = invoke(control,*args)
    if (p.returncode,p.stdout.strip(),p.stderr) != (audit.CODES[status],status,""):
        raise AssertionError("unexpected private command disposition")


def rehearsal():
    with tempfile.TemporaryDirectory(prefix="generated-p2f-") as tmp:
        control, root, record = stage(Path(tmp))
        expect(control,["compare"],"PASS")
        if json.loads((root/"comparison"/"comparison.json").read_text())["status"] != "PASS":
            raise AssertionError("private comparison missing")
        record.update(decision="WITHDRAWN",suppressed=True)
        save(control/"current.json",record)  # no other process in this generated fixture
        expect(control,["compare"],"QUARANTINE")
        expect(control,["withdrawal-check"],"PASS")


class PrivateCommand(unittest.TestCase):
    def test_cli_rehearsal_including_post_withdrawal(self):
        rehearsal()

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="generated-p2f-")
        self.addCleanup(self.tmp.cleanup)
        self.control,self.root,self.record = stage(Path(self.tmp.name))

    def change(self, **values):
        self.record.update(values)
        save(self.control/"current.json", self.record)

    def test_bad_cli_paths_format_and_flags_never_echo(self):
        expect(self.control,["PRIVATE-MARKER"],"QUARANTINE")
        self.change(format="fit")
        expect(self.control,["compare"],"UNSUPPORTED")
        self.change(format="tcx",workspace=str(self.root/"PRIVATE-MARKER"))
        expect(self.control,["compare"],"QUARANTINE")

    def test_unadmitted_or_expired_does_not_open_any_working_file(self):
        for changes in ({"decision":"UNADMITTED"}, {"suppressed":True}, {"grant_end":tick(0).isoformat()},
                        {"operations":dict.fromkeys(audit.OPS,False)}):
            with self.subTest(case=next(iter(changes))):
                record = {**self.record,**changes}
                with patch.object(audit,"read",side_effect=AssertionError("read attempted")):
                    with self.assertRaises(audit.Quarantine):
                        audit.inspect(self.control,record)

    def test_wrong_digest_or_unsealed_ref_blocks_before_original(self):
        origin = self.root/"immutable-original"/"source.tcx"
        actual_read = audit.read
        def intercept(path,*args):
            self.assertNotEqual(path,origin)
            return actual_read(path,*args)
        with patch.object(audit,"read",side_effect=intercept):
            for changes in ({"reference_sha256":"1"*64},{"reference_sealed":False},
                            {"reference_sha256":None},{"journal_sha256":None}):
                with self.subTest(case=next(iter(changes))), self.assertRaises(audit.Quarantine):
                    audit.inspect(self.control,{**self.record,**changes})
            sealed = self.root/"independent-reference"/"sealed.json"
            sheet = json.loads(sealed.read_bytes())
            del sheet["expected"]["qc"]["quarter_mean_hr_bpm"]
            with self.assertRaises(audit.Quarantine):
                audit.inspect(self.control,{**self.record,"reference_sha256":save(sealed,sheet)})

    def test_wrong_identity_and_trace_disagreement_are_preserved(self):
        frozen = self.root/"independent-reference"/"sealed.json"
        ref = json.loads(frozen.read_bytes())
        ref["expected"]["points"][1][2] = 12
        self.change(reference_sha256=save(frozen,ref))
        expect(self.control,["compare"],"QUARANTINE")
        out = self.root/"comparison"/"comparison.json"
        previous = out.read_bytes()
        expect(self.control,["compare"],"QUARANTINE")
        self.assertEqual(previous,out.read_bytes())
        ref["expected"]["identity"]["revision"] = "wrong"
        self.change(reference_sha256=save(frozen,ref))
        out.rename(out.with_name("first-synthetic-disagreement.json"))
        expect(self.control,["compare"],"CRITICAL_MISMATCH")

    def test_writable_original_and_stale_output_rejected(self):
        original = self.root/"immutable-original"/"source.tcx"
        original.chmod(stat.S_IWRITE | stat.S_IREAD)
        expect(self.control,["compare"],"QUARANTINE")
        original.chmod(stat.S_IREAD)
        (self.root/"comparison"/"comparison.json").write_text("synthetic prior output")
        expect(self.control,["compare"],"QUARANTINE")

    def test_unapproved_optional_context_rejected_before_original(self):
        j = self.root/"linkage"/"journal.json"
        cfg = json.loads(j.read_bytes())
        cfg["events"][0]["data"]["diary"] = "synthetic sensitive content"
        self.change(journal_sha256=save(j,cfg))
        expect(self.control,["compare"],"QUARANTINE")


if __name__ == "__main__":
    if sys.argv[1:] == ["--synthetic-demo"]:
        try:
            rehearsal()
        except Exception:
            print("QUARANTINE")
            sys.exit(2)
        print("PASS")
    else:
        unittest.main()
