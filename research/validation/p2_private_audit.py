"""Offline single-original P2-F audit. CLI emits a fixed disposition only."""
from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile

try:
    import p2_trace
    from p2_pilot_probe import inspect_linked_tcx
    from p2f_linkage import CurrentAuthority, PilotLedger, Quarantine, SCHEMA, instant
except Exception:
    if __name__ == "__main__":
        print("QUARANTINE")
        sys.exit(2)
    raise

VERSION = "private-audit/1"
FILES = ("p2_private_audit.py", "p2_trace.py", "p2_pilot_probe.py", "p2f_linkage.py")
OPS = {"original_inspection", "normalization", "within_person_derivatives",
       "diary_linkage", "development_testing"}
CODES = {"PASS": 0, "QUARANTINE": 2, "UNSUPPORTED": 3, "CRITICAL_MISMATCH": 4}
REPO = Path(__file__).resolve().parents[2]


def guard(ok):
    if not ok:
        raise Quarantine("private precondition failed")


def pairs(items):
    data = dict(items)
    guard(len(data) == len(items))
    return data


def parse(raw):
    return json.loads(raw.decode("utf-8-sig"), object_pairs_hook=pairs,
                      parse_constant=lambda _: guard(False))


def clean_path(path, *, directory=False):
    """Restricted to existing local, non-reparse paths outside source control."""
    p = Path(path)
    guard(p.is_absolute() and ".." not in p.parts and not str(p).startswith(("//", "\\\\")))
    guard(not p.is_relative_to(REPO) and not REPO.is_relative_to(p)
          and not any((a / ".git").exists() for a in (p, *p.parents)))
    for parent in (*reversed(p.parents), p):
        st = parent.lstat()
        guard(not (stat.S_ISLNK(st.st_mode) or getattr(st, "st_file_attributes", 0) & 1024))
    guard(stat.S_ISDIR(st.st_mode) if directory else stat.S_ISREG(st.st_mode) and st.st_nlink == 1)
    return p


def read(path, limit, digest=None):
    with clean_path(path).open("rb") as stream:
        data = stream.read(limit + 1)
    guard(0 < len(data) <= limit)
    if digest is not None:
        guard(sha256(data).hexdigest() == digest)
    return data


@contextmanager
def lease(control):
    """Steward updates and audit commits share this SAME private lock file."""
    with clean_path(control / "lease.lock").open("r+b") as stream:
        guard(stream.read(1) == b"L")
        stream.seek(0)
        if os.name == "nt":
            import msvcrt
            msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        # Closing the descriptor releases the OS lock even on exceptions.
        yield


def now():
    return datetime.now(timezone.utc)


def active(record):
    guard(record["decision"] == "SOURCE_ADMITTED" and record["suppressed"] is False
          and record["confirmatory_excluded"] is True and record["custody_verified"] is True
          and record["current_registry_verified"] is True and record["reference_sealed"] is True)
    guard(record["receipt_ref"].strip() and record["steward_ref"].strip()
          and record["independence_review_ref"].strip())
    guard(set(record["operations"]) == OPS and all(v is True for v in record["operations"].values()))
    guard(instant(record["grant_start"]) <= now() < instant(record["grant_end"]))
    guard(instant(record["verified_at"]) <= now() < instant(record["execution_expires"]))
    guard(all(isinstance(record[k], str) and re.fullmatch(r"[0-9a-f]{64}", record[k])
              for k in ("original_sha256","journal_sha256","reference_sha256")))
    guard(record["partition"] == "development-pilot" and record["scope"][3] == record["partition"])
    guard(re.fullmatch(r"[0-9a-f]{40}", record["code_commit"]) and record["parser"] == p2_trace.VERSION
          and record["schema"] == SCHEMA and record["command"] == VERSION)
    guard(set(record["modules"]) == set(FILES))
    for filename in FILES:
        guard(sha256(Path(__file__).with_name(filename).read_bytes()).hexdigest() == record["modules"][filename])


def evidence(ref):
    doc = dict(ref)
    doc["epochs_seen"] = tuple(doc["epochs_seen"])
    doc["marker_crossings"] = tuple(instant(t) for t in doc["marker_crossings"])
    doc["timer_events"] = tuple((instant(t), action) for t, action in doc["timer_events"])
    return p2_trace.Reference(**doc)


def observations(ledger, cfg, original, ref):
    scope = ledger.scope
    view = ledger.replay(scope, cfg["cutoff"])
    guard(len(view["sources"]) == len(view["links"]) == len(view["activities"]) == 1)
    edge = view["links"][cfg["edge_id"]]
    source = next(iter(view["sources"].values()))
    qc = inspect_linked_tcx(ledger, scope, cfg["cutoff"], cfg["edge_id"], cfg["source_id"],
                            original, evidence(ref["reference"]), reference_frozen_at=ref["frozen_at"])
    trace = p2_trace.decode(original, "tcx")
    ledger._access(scope)
    return {"identity": {"account":scope[0], "study":scope[1], "activity": source["activity"], "opportunity": edge["opportunity"],
                "revision": edge["revision"], "plan": view["opportunities"][edge["opportunity"]]["plan"],
                "source_id": source["id"], "start_utc": trace.start.isoformat()},
            "qc": {k: v for k, v in qc.items() if k not in ("parser","status")},
            "points": [[p.at.isoformat(), p.meters, p.source_hr, p.cadence] for p in trace.points]}


def compare(got, want):
    """Outer comparison, exact types for booleans/missing, exact native numeric values."""
    if isinstance(got, dict) and isinstance(want, dict):
        return got.keys() == want.keys() and all(compare(got[k], want[k]) for k in got)
    if isinstance(got, (list, tuple)) and isinstance(want, (list, tuple)):
        return len(got) == len(want) and all(compare(a,b) for a,b in zip(got,want))
    if type(got) in (int,float) and type(want) in (int,float):
        return got == want
    return type(got) is type(want) and got == want


def make_ledger(record, cfg):
    scope = tuple(record["scope"])
    current = CurrentAuthority(lambda: now().isoformat())
    current.register(scope, record["grant_start"], record["grant_end"], record["aliases"])
    key = tuple(cfg["source_key"])
    guard(len(key) == 4 and key[-1] == "tcx")
    return PilotLedger(scope, record["grant_start"], {key: cfg["activity"]},
                       {cfg["review_id"]: tuple(cfg["review_edge"])}, authority=current)


def inspect(control, record):
    active(record)
    root = clean_path(record["workspace"], directory=True)
    guard(not root.is_relative_to(control) and not control.is_relative_to(root))
    if record["format"] != "tcx":
        return "UNSUPPORTED"
    comparison = clean_path(root / "comparison", directory=True)
    output = comparison / "comparison.json"
    guard(not os.path.lexists(output))
    cfg = parse(read(root / "linkage" / "journal.json", 4_000_000, record["journal_sha256"]))
    guard(set(cfg) == {"source_key","activity","review_id","review_edge","events","source_id","edge_id","cutoff"})
    # Narrow first audit. No optional diary/context payloads or free-text rows.
    allowed = {"initial":{"opportunity","plan","version","revision","locked","parent","parent_version","intent"},
               "receipt":{"opportunity","revision"},
               "source":{"activity","performed_at","ended_at","imported_at","connection","namespace","external","format","digest"},
               "link":{"activity","opportunity","revision","evidence"}}
    guard(len(cfg["events"]) == 4 and {e["kind"] for e in cfg["events"]} == set(allowed))
    guard(all(set(e) <= {"id","kind","scope","effective","recorded","available","sequence","data"}
              and set(e["data"]) == allowed[e["kind"]] for e in cfg["events"]))
    guard(next(e for e in cfg["events"] if e["kind"] == "initial")["data"]["intent"] == "easy")
    ref = parse(read(root / "independent-reference" / "sealed.json", 16_000_000, record["reference_sha256"]))
    guard(set(ref) == {"reference","expected","frozen_at"})
    guard(ref["reference"]["source_digest"] == record["original_sha256"])
    guard(instant(ref["frozen_at"]) <= instant(record["verified_at"]))
    scope = tuple(record["scope"])
    ledger = make_ledger(record, cfg)
    ledger.append(scope, cfg["events"])
    view = ledger.replay(scope, cfg["cutoff"])
    guard(len(view["sources"]) == len(view["links"]) == len(view["activities"]) == 1
          and cfg["edge_id"] in view["links"])
    source = next(iter(view["sources"].values()))
    guard(source["id"] == cfg["source_id"] and source["digest"] == record["original_sha256"]
          and source["format"] == "tcx" and source["activity"] == view["links"][cfg["edge_id"]]["activity"])
    # Check permissions, the independent freeze and its historical availability before original read.
    guard(instant(source["imported_at"]) <= instant(ref["frozen_at"]) < instant(cfg["cutoff"]))
    active(record)
    original_path = clean_path(root / "immutable-original" / "source.tcx")
    guard(not original_path.stat().st_mode & (stat.S_IWUSR | stat.S_IWGRP | stat.S_IWOTH))
    original = read(original_path, 16_000_000, record["original_sha256"])
    got = observations(ledger, cfg, original, ref)
    replay = make_ledger(record, cfg)
    replay.append(scope, list(reversed(cfg["events"])))
    replay.append(scope, cfg["events"])
    replay_ok = compare(got, observations(replay, cfg, original, ref))
    identity_ok = compare(got["identity"], ref["expected"]["identity"])
    equal = compare(got, ref["expected"])
    status = "PASS" if replay_ok and equal else "QUARANTINE"
    if not replay_ok or not identity_ok:
        status = "CRITICAL_MISMATCH"
    # Preserve complete private outer comparison, including unexpected/missing rows.
    result = json.dumps({"status":status, "replay_equal":replay_ok, "observed":got,
                         "expected":ref["expected"], "parser":p2_trace.VERSION, "schema":SCHEMA,
                         "command":VERSION, "commit":record["code_commit"],
                         "reference_sha256":record["reference_sha256"]}, allow_nan=False).encode()
    active(record)
    # Exclusive, same-volume publication. Partial writes never appear under the final name.
    with tempfile.NamedTemporaryFile(dir=comparison, prefix=".pending-", delete=False) as f:
        pending = Path(f.name)
    try:
        with pending.open("wb") as f:
            f.write(result)
            f.flush()
            os.fsync(f.fileno())
        active(record)
        if os.name == "nt":
            os.rename(pending, output)  # Windows rename is atomic and refuses existing targets
        else:
            os.link(pending, output)
    finally:
        pending.unlink(missing_ok=True)
    return status


def run(mode, control):
    guard(sys.version_info[:2] == (3, 12) and mode in ("compare","withdrawal-check"))
    control = clean_path(control, directory=True)
    with lease(control):
        record = parse(read(control / "current.json", 64_000))
        if mode == "withdrawal-check":
            guard(record["decision"] == "WITHDRAWN" and record["suppressed"] is True
                  and record["confirmatory_excluded"] is True and record["current_registry_verified"] is True)
            guard(instant(record["verified_at"]) <= now() < instant(record["execution_expires"])
                  and len(record["scope"]) == 5 and record["scope"][3] == "development-pilot"
                  and record["aliases"] and record["steward_ref"].strip())
            try:
                active(record)
            except Quarantine:
                return "PASS"  # denied by the same gate compare uses, before any workspace read
            return "CRITICAL_MISMATCH"
        return inspect(control, record)


def main(argv):
    try:
        guard(len(argv) == 1)
        status = run(argv[0], os.environ["P2F_AUTHORITY_DIR"])
    except Exception:
        # Never print an exception, filename, argument, parsed value, or traceback from private processing.
        status = "QUARANTINE"
    print(status)
    return CODES[status]


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
