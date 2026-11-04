"""Strict, synthetic-only FIT subset. Never authorizes human ingestion or B0."""
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from hashlib import sha256

EPOCH = datetime(1989, 12, 31, tzinfo=timezone.utc)
# FIT profile scalar fields used here: (wire size, base type).
TIME = (4, 0x86)
PROFILE = {18: {253: TIME, 2: TIME}, 20: {253: TIME, 3: (1, 2), 5: TIME},
           21: {253: TIME, 0: (1, 0), 1: (1, 0), 4: (1, 2)}}
MIN_ABSOLUTE_TIME = 0x10000000


class Quarantine(ValueError):
    pass


def crc16(data):
    crc = 0
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ (0xA001 if crc & 1 else 0)
    return crc


@dataclass(frozen=True)
class Sample:
    at: datetime
    hr_bpm: int | None
    distance_m: float | None
    timer_running: bool | None


@dataclass(frozen=True)
class Trace:
    source_sha256: str
    session_index: int
    start: datetime
    end: datetime
    samples: tuple
    timer_events: tuple

    @property
    def coverage(self):
        """Describe observed gaps; no interpolated values or acceptance thresholds."""
        points = self.samples
        return {
            "leading_gap_s": int((points[0].at - self.start).total_seconds()),
            "trailing_gap_s": int((self.end - points[-1].at).total_seconds()),
            "max_record_gap_s": max((int((b.at - a.at).total_seconds())
                                     for a, b in zip(points, points[1:])), default=None),
            "missing_hr": sum(p.hr_bpm is None for p in points),
            "zero_hr": sum(p.hr_bpm == 0 for p in points),
            "missing_distance": sum(p.distance_m is None for p in points),
            "unknown_timer": sum(p.timer_running is None for p in points),
        }

    @property
    def b0_eligible(self):
        return False


def _read(data):
    if not isinstance(data, bytes) or not 16 <= len(data) <= 2 * 1024 * 1024:
        raise Quarantine("invalid synthetic input size")
    h = data[0]
    if h not in (12, 14) or data[8:12] != b".FIT" or data[1] >> 4 not in (1, 2):
        raise Quarantine("unsupported FIT header")
    end = h + int.from_bytes(data[4:8], "little")
    if end + 2 != len(data):
        raise Quarantine("length mismatch")
    if h == 14 and crc16(data[:h]) or crc16(data):
        raise Quarantine("CRC mismatch")
    pos = h

    def take(n):
        nonlocal pos
        if pos + n > end:
            raise Quarantine("truncated message")
        chunk = data[pos:pos + n]
        pos += n
        return chunk

    definitions = {}
    while pos < end:
        hdr = take(1)[0]
        if hdr & 0xB0:  # compressed timestamp, developer fields or reserved bit
            raise Quarantine("unsupported FIT feature")
        local = hdr & 15
        if hdr & 64:
            reserved, architecture = take(2)
            if reserved or architecture not in (0, 1):
                raise Quarantine("invalid definition")
            order = ("little", "big")[architecture]
            msg = int.from_bytes(take(2), order)
            fields = [tuple(take(3)) for _ in range(take(1)[0])]
            if len({n for n, _, _ in fields}) != len(fields) or any(s == 0 for _, s, _ in fields):
                raise Quarantine("invalid fields")
            definitions[local] = (msg, order, fields)
        else:
            if local not in definitions:
                raise Quarantine("undefined local message")
            msg, order, fields = definitions[local]
            values = {}
            for num, size, base in fields:
                raw = take(size)
                expected = PROFILE.get(msg, {}).get(num)
                if expected is None:
                    continue
                if expected != (size, base):
                    raise Quarantine("field encoding mismatch")
                value = int.from_bytes(raw, order)
                values[num] = None if value == (1 << (size * 8)) - 1 else value
            if msg in PROFILE:
                yield msg, values


def inspect_fit(data, *, session_index=None):
    """Index by session message order; closed time windows must have unique ownership."""
    sessions, rows = [], []
    for msg, vals in _read(data):
        t = vals.get(253)
        if t is None:
            raise Quarantine("missing timestamp")
        if t < MIN_ABSOLUTE_TIME:
            raise Quarantine("device-relative timestamp")
        if msg == 18:
            start = vals.get(2)
            if start is None or start >= t:
                raise Quarantine("invalid session bounds")
            if start < MIN_ABSOLUTE_TIME:
                raise Quarantine("device-relative session start")
            sessions.append((start, t))
        elif msg == 20:
            rows.append((t, "record", (vals.get(3), vals.get(5))))
        elif vals.get(0) is None:
            raise Quarantine("unknown event kind")
        elif vals[0] == 0:
            if 4 in vals and vals[4] != 0:
                raise Quarantine("unsupported timer group")
            kind = vals.get(1)
            if kind not in (0, 1, 4):  # start, stop, stop_all
                raise Quarantine("unsupported timer event")
            rows.append((t, "timer", kind))
    if not sessions or len(sessions) > 512:
        raise Quarantine("missing or excessive sessions")
    ordered = sorted(sessions)
    if any(a[1] >= b[0] for a, b in zip(ordered, ordered[1:])):
        raise Quarantine("ambiguous session bounds")
    if session_index is None and len(sessions) == 1:
        session_index = 0
    if type(session_index) is not int or not 0 <= session_index < len(sessions):
        raise Quarantine("explicit valid session index required")
    from bisect import bisect_right
    starts = [s for s, _ in ordered]
    selected = sessions[session_index]
    records, timers = [], []
    for t, group, val in rows:
        i = bisect_right(starts, t) - 1
        if i < 0 or t > ordered[i][1]:
            raise Quarantine("orphan record or timer")
        if ordered[i] == selected:
            stream = records if group == "record" else timers
            if stream and t <= stream[-1][0]:
                raise Quarantine("duplicate or reversed stream timestamp")
            stream.append((t, val))
    if not records:
        raise Quarantine("no records in selected session")
    state = None
    events = []
    for t, kind in timers:
        active = kind == 0
        if active == state:
            raise Quarantine("contradictory timer transition")
        state = active
        events.append((EPOCH + timedelta(seconds=t), ("start", "stop", None, None, "stop_all")[kind]))
    state, j, samples, last_distance = None, 0, [], None
    for t, (hr, distance) in records:
        if distance is not None:
            if last_distance is not None and distance < last_distance:
                raise Quarantine("distance regression in selected session")
            last_distance = distance
        while j < len(timers) and timers[j][0] <= t:
            state = timers[j][1] == 0
            j += 1
        samples.append(Sample(EPOCH + timedelta(seconds=t), hr,
                              None if distance is None else distance / 100, state))
    return Trace(sha256(data).hexdigest(), session_index,
                 *(EPOCH + timedelta(seconds=t) for t in selected), tuple(samples), tuple(events))
