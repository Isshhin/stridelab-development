"""Independent wire fixtures, no downloaded device files."""
from datetime import datetime, timezone
from hashlib import sha256
import struct
import unittest

from p2_fit_trace import Quarantine, crc16, inspect_fit


def wire_crc(data):
    nibble = [0, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
              0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400]
    value = 0
    for byte in data:
        for shift in (0, 4):
            value = (value >> 4) ^ nibble[(value ^ (byte >> shift)) & 15]
    return value


def fixture(*, sessions=((10, 35), (40, 60)), records=None, timers=None,
            endian="<", extra=b""):
    if records is None:
        records = [(10, 120, 0), (19, 132, 1450), (21, 128, 1500),
                   (25, 130, 1550), (35, 140, 4500), (42, 0, 0), (55, 255, 0xffffffff)]
    if timers is None:
        timers = [(10, 0, 0), (20, 0, 1), (25, 0, 0), (35, 0, 4)]
    body = bytearray()
    def emit(local, global_id, fields, fmt, values):
        body.extend(bytes((0x40 | local, 0, int(endian == ">"))))
        body.extend(struct.pack(endian + "HB", global_id, len(fields)))
        for f in fields:
            body.extend(bytes(f))
        for row in values:
            body.append(local)
            body.extend(struct.pack(endian + fmt, *row))
    emit(0, 20, [(253, 4, 134), (3, 1, 2), (5, 4, 134)], "IBI", records)
    emit(1, 21, [(253, 4, 134), (0, 1, 0), (1, 1, 0)], "IBB", timers)
    emit(2, 18, [(2, 4, 134), (253, 4, 134)], "II", sessions)
    body.extend(extra)
    header = struct.pack("<BBHI4s", 14, 16, 2100, len(body), b".FIT")
    blob = header + struct.pack("<H", wire_crc(header)) + body
    return blob + struct.pack("<H", wire_crc(blob))


class FitTraceTests(unittest.TestCase):
    def test_records_timer_and_session_isolation(self):
        for order in "<>":
            data = fixture(endian=order)
            one, two = (inspect_fit(data, session_index=i) for i in (0, 1))
            self.assertEqual(one.source_sha256, sha256(data).hexdigest())
            self.assertEqual(one.samples[0].at, datetime(1989,12,31,0,0,10,tzinfo=timezone.utc))
            self.assertEqual([(p.hr_bpm, p.distance_m, p.timer_running) for p in one.samples],
                             [(120,0,True), (132,14.5,True), (128,15,False),
                              (130,15.5,True), (140,45,False)])
            self.assertEqual([(t.second,k) for t,k in one.timer_events],
                             [(10,"start"), (20,"stop"), (25,"start"), (35,"stop_all")])
            self.assertEqual([(p.hr_bpm,p.distance_m,p.timer_running) for p in two.samples],
                             [(0,0,None), (None,None,None)])
            self.assertFalse(one.b0_eligible)
            self.assertFalse(two.b0_eligible)

    def test_crc_header_payload_and_truncation(self):
        self.assertEqual(crc16(b"123456789"), 0xBB3D)
        for i in (12, 40, -1):
            bad = bytearray(fixture())
            bad[i] ^= 2
            if i == 12:
                bad[-2:] = struct.pack("<H", wire_crc(bad[:-2]))
            with self.assertRaisesRegex(Quarantine, "CRC"):
                inspect_fit(bytes(bad), session_index=0)
        with self.assertRaisesRegex(Quarantine, "length"):
            inspect_fit(fixture()[:-1], session_index=0)

    def test_unique_ownership_and_selection(self):
        for index in (None, -1, 2, True):
            with self.assertRaisesRegex(Quarantine, "index"):
                inspect_fit(fixture(), session_index=index)
        for bounds in [((10,40),(40,60)), ((10,55),(40,60))]:
            with self.assertRaisesRegex(Quarantine, "ambiguous"):
                inspect_fit(fixture(sessions=bounds), session_index=0)
        with self.assertRaisesRegex(Quarantine, "orphan"):
            inspect_fit(fixture(sessions=((10,35),)), session_index=0)
        single = inspect_fit(fixture(sessions=((10,60),)))
        self.assertEqual(single.session_index, 0)

    def test_reject_unknown_or_ambiguous_timer_and_records(self):
        cases = [(dict(timers=[(12,0,8)]), "unsupported timer"),
                 (dict(timers=[(12,0,0),(13,0,0)]), "contradictory"),
                 (dict(timers=[(12,0,1),(12,0,0)]), "duplicate"),
                 (dict(records=[(12,100,0),(11,101,50)]), "reversed"),
                 (dict(records=[(0xffffffff,100,0)]), "missing timestamp"),
                 (dict(extra=b"\x80"), "unsupported FIT feature"),
                 (dict(records=[(42,111,15)]), "no records")]
        for opts, reason in cases:
            with self.subTest(reason=reason), self.assertRaisesRegex(Quarantine, reason):
                inspect_fit(fixture(**opts), session_index=0)


if __name__ == "__main__":
    unittest.main()
