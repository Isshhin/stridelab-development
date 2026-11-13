"""Public P2-R measurement replay. Run directly with official Polar HRM files."""
import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from statistics import median

VERSION = "p2-r-rr/1"
SOURCE = "10.13026/wce5-fj54"
LICENSE = "CC BY 4.0"
ACTIONS = frozenset(("KEEP", "PROGRESS", "MAINTAIN", "REDUCE", "RE_ENTRY",
                     "ABSTAIN_NEED_MORE_INFORMATION", "NO_AUTOMATED_DECISION"))


@dataclass(frozen=True)
class Beat:
    record: str
    sequence: int
    end_ms: int
    available_ms: int
    rr_ms: int


def polar(text, record):
    """Decode only the RR export mode used by this pinned PhysioNet release."""
    lines = [line.strip() for line in text.splitlines()]
    if "Interval=238" not in lines or lines.count("[HRData]") != 1:
        raise ValueError("Expected a Polar beat-interval export")
    elapsed = 0
    for sequence, line in enumerate(lines[lines.index("[HRData]") + 1:], 1):
        if not line:
            continue
        rr = int(line)
        if rr <= 0:
            raise ValueError("Nonpositive RR breaks the relative clock")
        elapsed += rr
        yield Beat(record, sequence, elapsed, elapsed, rr)


def replay(beats, record, cutoff_ms):
    """Relative device clock. Historical device availability is simulated."""
    if cutoff_ms < 0:
        raise ValueError("Negative replay cutoff")
    visible = []
    for beat in beats:
        if beat.record != record or beat.available_ms > cutoff_ms:
            continue
        if not 0 < beat.end_ms <= beat.available_ms or beat.rr_ms <= 0:
            raise ValueError("Invalid relative clock or interval")
        visible.append(beat)
    visible.sort(key=lambda b: (b.end_ms, b.sequence))
    if len({b.sequence for b in visible}) != len(visible):
        raise ValueError("Ambiguous beat revision")
    rr = [b.rr_ms for b in visible]
    return dict(dataset=SOURCE, license=LICENSE, boundary="RESEARCH_ONLY_ISOLATED",
                version=VERSION, record=record, cutoff_ms=cutoff_ms,
                clock="relative RR accumulation; simulated arrival at beat end",
                sensor="Polar RS800 Multi", applicability="PARTIAL",
                observation_ids=[b.sequence for b in visible], n=len(rr),
                rr_range_ms=[min(rr), max(rr)] if rr else None,
                median_rr_ms=median(rr) if rr else None,
                missing=["verified phase", "reference ECG agreement", "personal baseline"],
                action="ABSTAIN_NEED_MORE_INFORMATION", confidence="insufficient for readiness")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("hrm", type=Path)
    parser.add_argument("--beats", type=int, default=32)
    args = parser.parse_args()
    if args.beats < 1:
        parser.error("--beats must be positive")
    from itertools import islice
    record = args.hrm.stem
    prefix = list(islice(polar(args.hrm.read_text(encoding="latin1"), record), args.beats))
    print(json.dumps(replay(prefix, record, prefix[-1].end_ms if prefix else 0), sort_keys=True))
