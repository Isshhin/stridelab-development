"""Synthetic-only operator rehearsal. Accepts a staging parent, never a recording.

This process controls only its newly created temporary directory. The SQLite
register is separate from the simulated data backup. This exercises logical
suppression and observed file absence, not encryption, system ACLs or erasure.
"""
import argparse
from contextlib import closing
from hashlib import sha256
import json
import multiprocessing
from pathlib import Path
import shutil
import sqlite3
import stat
import tempfile

import p2_trace
from p2_pilot_probe import inspect_linked_tcx
from p2f_linkage import CurrentAuthority, PilotLedger, Quarantine, digest
from test_p2_trace import point, tcx, tick  # generated XML only


ARTIFACTS = ("immutable-original/source.tcx", "working/source.tcx",
             "normalized/points.json", "linkage/journal.json", "trace/segment.json",
             "independent-reference/expected.json", "comparison/result.json",
             "audit-log/disposition.json", "temporary/decoder-copy.tcx")
ACCOUNTS = ("synthetic-A","synthetic-B")
SOURCE_KEY = ("direct","original","fixture","tcx")
EXPECTED_METRES = 3600  # fixed synthetic reference before decode: 3 m/s x 1200 s


class DrillFailure(Exception):
    """Carries only a fixed non-identifying stage code, safe for console output."""


def verify(ok, stage):
    if not ok:
        raise DrillFailure(stage)


def stamp(seconds):
    return tick(seconds).isoformat()


def scope_for(account):
    return (account, "study-"+account, "grant-"+account, "development-pilot", "reviewer")


def create_register(path):
    # Called only in a newly created temporary directory. Recovery never calls it.
    with closing(sqlite3.connect(path)) as db, db:
        db.execute("CREATE TABLE grants(account TEXT PRIMARY KEY, withdrawn INTEGER NOT NULL,"
                   " excluded INTEGER NOT NULL CHECK(excluded=1), head TEXT)")
        db.execute("CREATE TABLE aliases(alias TEXT PRIMARY KEY, account TEXT NOT NULL)")
        for account in ACCOUNTS:
            db.execute("INSERT INTO grants VALUES(?,0,1,?)",(account,digest({})))
            db.executemany("INSERT INTO aliases VALUES(?,?)",
                           [(account+suffix,account) for suffix in ("","-old-plan","-old-bout")])


def current_register(path):
    # mode=rw never creates a blank replacement when the independent file is missing.
    return sqlite3.connect(path.resolve().as_uri()+"?mode=rw",uri=True)


def lookup(db, alias, partition="development-pilot"):
    entry = db.execute("SELECT g.account,g.withdrawn,g.excluded,g.head FROM aliases a "
                       "JOIN grants g ON g.account=a.account WHERE a.alias=?", (alias,)).fetchone()
    if entry is None or entry[1] or entry[2] != 1 or partition != "development-pilot":
        raise Quarantine("scope unavailable")
    return entry[0], entry[3]


def access(db, alias, operation, partition="development-pilot"):
    account, _ = lookup(db,alias,partition)
    return operation(account)


def open_ledger(db, account):
    canonical, head = lookup(db,account)
    scope = scope_for(canonical)
    authority = CurrentAuthority(lambda: stamp(5000))
    authority.register(scope,stamp(-100),stamp(6000),(canonical,))
    authority.advance(scope,digest({}),head)
    return PilotLedger(scope,stamp(-100),{SOURCE_KEY:"bout"},{"review":("bout","opp","rev")},
                       authority=authority)


def write_json(path, value):
    path.write_text(json.dumps(value,sort_keys=True),encoding="utf-8")


def journal(scope, raw):
    def event(event_id, kind, seconds, **data):
        time = stamp(seconds)
        return dict(id=event_id,kind=kind,scope=scope,recorded=time,effective=time,
                    available=time,data=data)
    return [
        event("plan","initial",-90,opportunity="opp",plan="plan",version="v1",revision="rev",
              locked=False,parent=None,parent_version=None,intent="easy"),
        event("delivery","receipt",-80,opportunity="opp",revision="rev"),
        event("file","source",3600,activity="bout",performed_at=stamp(0),ended_at=stamp(1800),
              imported_at=stamp(3600),connection=SOURCE_KEY[0],namespace=SOURCE_KEY[1],
              external=SOURCE_KEY[2],format=SOURCE_KEY[3],digest=sha256(raw).hexdigest()),
        event("edge","link",3610,activity="bout",opportunity="opp",revision="rev",evidence="review")]


def stage(db, directory, account, expected_hr):
    scope = scope_for(account)
    ledger = open_ledger(db,account)
    for name in ARTIFACTS:
        (directory/name).parent.mkdir(parents=True,exist_ok=True)
    original, working = (directory/ARTIFACTS[i] for i in (0,1))
    raw = tcx(point(t,h=expected_hr) for t in range(0,1801,5))
    with original.open("xb") as stream:
        stream.write(raw)
    original.chmod(stat.S_IREAD)
    verify(not (original.stat().st_mode & stat.S_IWUSR),"READ_ONLY_ATTRIBUTE")
    shutil.copyfile(original,working)
    shutil.copyfile(working,directory/ARTIFACTS[-1])
    expected = {"distance_m":EXPECTED_METRES,"quarter_mean_hr_bpm":[expected_hr]*4}
    worksheet = directory/"independent-reference/expected.json"
    write_json(worksheet,expected)
    frozen = worksheet.read_bytes()
    used = working.read_bytes()
    rows = journal(scope,used)
    ledger.append(scope,rows)
    before = ledger.replay(scope,stamp(4000))
    ledger.append(scope,rows)
    verify(ledger.replay(scope,stamp(4000)) == before,"DUPLICATE_REPLAY")
    checkpoint = ledger.checkpoint(scope)
    write_json(directory/"linkage/journal.json",checkpoint)
    with db:
        db.execute("UPDATE grants SET head=? WHERE account=?",(digest(checkpoint),account))
    verify("edge" in before["links"],"LINK")
    trace = p2_trace.decode(used,"tcx")
    write_json(directory/"normalized/points.json",[
        (p.at.isoformat(),p.meters,p.hr) for p in trace.points])
    ref = p2_trace.Reference(sha256(raw).hexdigest(),"fixture",("fixture",),"calibrated_footpod",
          "fixture-cal","electrical_chest_strap",(tick(600),),((tick(0),"start"),(tick(1800),"stop")),
          1200,"fixture-reference",True,True,5,"fixture-route","fixture-firmware")
    measured = inspect_linked_tcx(ledger,scope,stamp(4000),"edge","file",used,ref,
                                 reference_frozen_at=stamp(3700))
    verify(worksheet.read_bytes() == frozen and original.read_bytes() == raw,"INTAKE_INTEGRITY")
    observed = {key:measured[key] for key in expected}
    verify(json.loads(json.dumps(observed)) == json.loads(frozen),"SYNTHETIC_COMPARISON")
    write_json(directory/"trace/segment.json",measured)
    write_json(directory/"comparison/result.json",{"expected":expected,"observed":observed})
    write_json(directory/"audit-log/disposition.json",{"status":"SYNTHETIC_PASS"})
    return ledger


def denied_before_open(db, alias, callback, partition="development-pilot"):
    invoked = False
    def monitored(account):
        nonlocal invoked
        invoked = True
        return callback(account)
    try:
        access(db,alias,monitored,partition)
    except Quarantine:
        verify(not invoked,"DENIAL_AFTER_OPEN")
        return
    raise DrillFailure("SUPPRESSION_BYPASS")


def challenge(root):
    """Called in fresh interpreter after restoring stale bytes into quarantine."""
    recovered = root/"isolated-restore"
    a,b = ACCOUNTS
    verify(all((recovered/a/p).is_file() for p in ARTIFACTS),"STALE_SNAPSHOT_INCOMPLETE")
    with closing(current_register(root/"separate-register"/"control.sqlite")) as db:
        for alias in (a,a+"-old-plan",a+"-old-bout"):
            for path in ("immutable-original/source.tcx","linkage/journal.json",
                         "trace/segment.json","comparison/result.json"):
                denied_before_open(db,alias,lambda account:(recovered/account/path).read_bytes())
        def reimport(account):
            raw = (recovered/account/"immutable-original/source.tcx").read_bytes()
            ledger = open_ledger(db,account)
            ledger.append(scope_for(account),journal(scope_for(account),raw))
        for alias in (a,a+"-old-plan",a+"-old-bout","synthetic-unknown-alias"):
            denied_before_open(db,alias,reimport)
        denied_before_open(db,a,lambda account:None,"confirmatory")
        denied_before_open(db,b,lambda account:None,"confirmatory")
        verify(db.execute("SELECT withdrawn,excluded,head FROM grants WHERE account=?",
                          (a,)).fetchone() == (1,1,None),"SUPPRESSION_REGISTER")
        healthy = open_ledger(db,b)
        scope = scope_for(b)
        healthy.restore(scope,lambda:json.loads((recovered/b/"linkage/journal.json").read_bytes()))
        verify(healthy.replay(scope,stamp(4000))["links"]["edge"]["revision"] == "rev",
               "SECOND_ACCOUNT_REPLAY")
        comparison = access(db,b,lambda account:json.loads(
            (recovered/account/"comparison/result.json").read_bytes()))
        verify(comparison["expected"] == comparison["observed"],"SECOND_ACCOUNT_DERIVATIVE")


def worker(root, pipe):
    try:
        challenge(root)
        pipe.send("PASS")
    except DrillFailure as exc:
        pipe.send(str(exc))
    except Exception:
        pipe.send("RECOVERY_ERROR")
    finally:
        pipe.close()


def run(parent):
    with tempfile.TemporaryDirectory(prefix="stridelab-synthetic-",dir=parent) as tmp:
        root = Path(tmp)
        authority = root/"separate-register"
        authority.mkdir()
        database = authority/"control.sqlite"
        create_register(database)
        live = root/"workspace"
        a,b = ACCOUNTS
        with closing(current_register(database)) as db:
            ledger = stage(db,live/a,a,132)
            stage(db,live/b,b,129)
            shutil.copytree(live,root/"backup")
            # Suppress durably first. A crash during unlink leaves processing denied.
            with db:
                db.execute("UPDATE grants SET withdrawn=1,head=NULL WHERE account=?",(a,))
            ledger.withdraw(scope_for(a))
            try:
                ledger.append(scope_for(a),[{"id":"synthetic-late-reimport"}])
            except Quarantine:
                pass
            else:
                raise DrillFailure("LIVE_REIMPORT")
            for name in ARTIFACTS:
                target = live/a/name
                target.chmod(stat.S_IREAD | stat.S_IWRITE)
                target.unlink()
                verify(not target.exists(),"DELETION_FAILED")
            verify(not any(x.is_file() for x in (live/a).rglob("*")),"RESIDUAL_LIVE_FILE")
            verify(all((live/b/p).is_file() for p in ARTIFACTS),"SECOND_ACCOUNT_LOST")
        shutil.copytree(root/"backup",root/"isolated-restore")
        ctx = multiprocessing.get_context("spawn")
        read, send = ctx.Pipe(duplex=False)
        child = ctx.Process(target=worker,args=(root,send))
        child.start()
        send.close()
        try:
            if not read.poll(30):
                raise DrillFailure("RECOVERY_TIMEOUT")
            outcome = read.recv()
            child.join(5)
            verify(child.exitcode == 0 and outcome == "PASS",outcome if outcome != "PASS" else "RECOVERY_EXIT")
        finally:
            if child.is_alive():
                child.terminate()
                child.join()
            read.close()
    verify(not root.exists(),"TEMPORARY_CLEANUP")


class SafeArguments(argparse.ArgumentParser):
    def error(self, message):
        self.exit(2,"SYNTHETIC_REHEARSAL=FAIL INVALID_ARGUMENTS\n")


def main():
    parser = SafeArguments(description=__doc__,prog="p2_operator_rehearsal",allow_abbrev=False)
    parser.add_argument("--workspace-parent",required=True,type=Path,
                        help="existing writable parent for an isolated synthetic drill")
    args = parser.parse_args()
    try:
        run(args.workspace_parent)
    except DrillFailure as exc:
        print("SYNTHETIC_REHEARSAL=FAIL",str(exc))
        return 1
    except Exception:
        print("SYNTHETIC_REHEARSAL=FAIL STAGING_OR_RUNTIME")
        return 1
    print("SYNTHETIC_REHEARSAL=PASS")
    print("LIVE_GENERATED_FILES=VERIFIED_DELETED (logical scope)")
    print("STALE_RESTORE=SUPPRESSED_FROM_PROCESSING SECOND_ACCOUNT=PASS")
    print("CONFIRMATORY_EXCLUSION=PASS DEVICE_SECURITY=UNVERIFIED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
