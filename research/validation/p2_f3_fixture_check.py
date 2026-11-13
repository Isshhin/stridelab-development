"""Run explicitly with the pinned coaching lab src/ on PYTHONPATH and fitdecode 0.11.0.
Generates small synthetic FIT/TCX/GPX in a temporary directory. No athlete data.
"""
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from hashlib import sha256
from pathlib import Path
import struct
from tempfile import TemporaryDirectory
import unittest

from coaching_lab import activity, file_activity_adapter as adapter
import fitdecode
from fitdecode.utils import compute_crc
from p2_f3_reconciliation import (
    Prescription, Quarantine, ReviewedEdge, Source, coverage, parse_reviewed, reconcile,
)


def synthetic_fit(second_lap=1):
    body = bytearray()

    def emit(local, number, schema, records):
        body.extend(struct.pack("<BBBHB", 0x40 + local, 0, 0, number, len(schema)))
        for field, fmt, base in schema:
            body.extend((field, struct.calcsize("<" + fmt), base))
        for values in records:
            body.append(local)
            body.extend(struct.pack("<" + "".join(fmt for _, fmt, _ in schema), *values))

    def uint(field):
        return (field, "I", 0x86)

    def ushort(field):
        return (field, "H", 0x84)

    def byte(field):
        return (field, "B", 0x02)

    epoch = datetime(1989, 12, 31, tzinfo=timezone.utc)
    when = int((datetime(2020, 1, 2, tzinfo=timezone.utc) - epoch).total_seconds())
    emit(0, 19, [ushort(254), uint(7), uint(8), uint(9)],
         [(0, 60000, 50000, 10000), (second_lap, 65000, 45000, 9000)])
    emit(1, 18, [uint(2), (5, "B", 0), uint(7), uint(8), uint(9),
                  ushort(25), ushort(26), byte(16), byte(18), ushort(20), ushort(14)],
         [(when, 1, 60000, 50000, 10000, 0, 1, 130, 82, 200, 2000),
          (when + 120, 1, 65000, 45000, 9000, 1, 1, 0, 0, 0, 0)])
    header = struct.pack("<BBHI4s", 14, 0x10, 2100, len(body), b".FIT")
    header += struct.pack("<H", compute_crc(header))
    payload = header + body
    return payload + struct.pack("<H", compute_crc(payload))


START = datetime(2020, 1, 2, tzinfo=timezone.utc)
ACQUIRED = START + timedelta(days=1)
AS_OF = ACQUIRED + timedelta(days=1)
TCX = """<TrainingCenterDatabase><Activities><Activity Sport="Running" Id="2020-01-02T08:00:00+08:00"><Lap>
<DistanceMeters>100</DistanceMeters><TotalTimeSeconds>60</TotalTimeSeconds>
<AverageHeartRateBpm><Value>130</Value></AverageHeartRateBpm><Cadence>0</Cadence>
</Lap></Activity></Activities></TrainingCenterDatabase>"""
GPX = """<gpx><trk><trkseg><trkpt lat="0" lon="0"><time>2020-01-01T19:00:00-05:00</time></trkpt>
<trkpt lat="0" lon="0"><time>2020-01-01T19:01:00-05:00</time></trkpt></trkseg></trk></gpx>"""


class FixtureChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pins = {activity: "63d0525d25d17b07cd6e30b1df9e549322a6d43d8c1676e97805f7b09ffcc4dc",
                adapter: "07d9bc798bf344e4efe64989ad6677ce024c9eee5a837d44c79239a639fd013a"}
        for module, digest in pins.items():
            content = Path(module.__file__).read_text(encoding="utf-8").encode()
            if sha256(content).hexdigest() != digest:
                raise RuntimeError("unexpected lab parser source")
        if fitdecode.__version__ != "0.11.0":
            raise RuntimeError("unexpected FIT decoder version")

    def setUp(self):
        tmp = TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.prescription = Prescription("a", "s", "r1", START, START)

    def stage(self, fmt, data, ordinal=0):
        path = self.root / ("fixture." + fmt)
        data = data.encode() if isinstance(data, str) else data
        path.write_bytes(data)
        src = Source("a", sha256(data).hexdigest(), ordinal, fmt, ACQUIRED)
        return src, parse_reviewed(path, src, adapter.parse_activity_file)

    def edge(self, src):
        return ReviewedEdge(src, "actual", "s", "r1", ACQUIRED, "partial")

    def link(self, pair, edges, plans=None, as_of=AS_OF, account="a"):
        return reconcile(account, *pair, as_of, edges, [self.prescription] if plans is None else plans)

    def test_fit_session_lap_units_and_cadence(self):
        for i, expected in enumerate([(60, 50, 0.1, 130, 164), (65, 45, 0.09, 0, 0)]):
            _, parsed = self.stage("fit", synthetic_fit(), i)
            o = parsed.observation
            self.assertEqual((o.actual_duration_seconds, o.actual_timer_duration_seconds,
                              o.actual_distance_km, o.average_heart_rate_bpm, o.average_cadence_spm), expected)
            self.assertEqual(len(o.laps), 1)
            self.assertEqual((o.laps[0]["elapsed_duration_s"], o.laps[0]["duration_s"]), expected[:2])
        self.assertIsNone(adapter._fit_running_cadence_spm("cycling", 82, .5))
        self.assertEqual(adapter._fit_running_cadence_spm("running", 82, .5), 165)

    def test_tcx_gpx_and_timezone_rollover(self):
        _, tcx = self.stage("tcx", TCX)
        _, gpx = self.stage("gpx", GPX)
        self.assertEqual(tcx.observation.activity_start_time, gpx.observation.activity_start_time)
        self.assertEqual(gpx.observation.activity_start_time, START)
        self.assertEqual(gpx.observation.actual_distance_km, 0)
        self.assertIsNone(gpx.observation.average_heart_rate_bpm)
        self.assertIsNone(tcx.observation.actual_timer_duration_seconds)
        self.assertEqual(tcx.observation.average_cadence_rpm, 0)

    def test_raw_power_and_speed_are_unavailable_in_adapter(self):
        data = synthetic_fit()
        with fitdecode.FitReader(data) as reader:
            sessions = [{f.name: (f.value, f.units) for f in frame.fields}
                        for frame in reader if isinstance(frame, fitdecode.FitDataMessage)
                        and frame.name == "session"]
        self.assertEqual(sessions[0]["avg_power"], (200, "watts"))
        self.assertEqual(sessions[0]["avg_speed"], (2.0, "m/s"))
        self.assertEqual(sessions[1]["avg_speed"], (0.0, "m/s"))
        _, parsed = self.stage("fit", data)
        fields = vars(parsed.observation)
        self.assertFalse(any(token in name for name in fields for token in ("speed", "power", "pace", "moving")))
        # A stationary sample has zero speed. Its reciprocal pace is undefined.

    def test_reject_bad_inputs(self):
        for fmt, data in [("fit", synthetic_fit()[:-3]), ("fit", synthetic_fit(second_lap=2)), ("tcx", "<"),
                          ("gpx", GPX.replace("19:01", "18:59")),
                          ("gpx", GPX.replace("</trkseg>", "</trkseg><trkseg/>")),
                          ("gpx", GPX.replace("<time>2020-01-01T19:01:00-05:00</time>", "")),
                          ("tcx", TCX.replace(">130<", ">NaN<")),
                          ("tcx", TCX.replace(">130<", ">999<")),
                          ("tcx", TCX.replace("</Lap>", "</Lap><Lap/>")),
                          ("tcx", TCX.replace("+08:00", ""))]:
            with self.subTest(fmt=fmt, data=str(data)[:24]), self.assertRaises(Quarantine):
                self.stage(fmt, data)

    def test_late_and_unmatched(self):
        pair = self.stage("tcx", TCX)
        self.assertEqual(self.link(pair, [self.edge(pair[0])], as_of=START), ("NOT_YET_AVAILABLE",))
        self.assertEqual(self.link(pair, []), ("UNLINKED",))
        self.assertEqual(self.link(pair, [replace(self.edge(pair[0]), reviewed_at=AS_OF)], as_of=ACQUIRED), ("UNLINKED",))

    def test_identity_revision_and_conflicts(self):
        pair = self.stage("fit", synthetic_fit())
        edge = self.edge(pair[0])
        new = replace(self.prescription, revision="r2", accepted_at=ACQUIRED)
        self.assertEqual(self.link(pair, [edge], [new, self.prescription])[-2:], ("r1", "partial"))
        for kwargs in [dict(edges=[replace(edge, revision="r2")]),
                       dict(edges=[edge, replace(edge, activity="other")]),
                       dict(edges=[edge], account="b"),
                       dict(edges=[edge], plans=[replace(self.prescription, account="b")])]:
            with self.subTest(kwargs=kwargs), self.assertRaises(Quarantine):
                self.link(pair, **kwargs)
        with self.assertRaises(Quarantine):
            self.link((replace(pair[0], session_index=1), pair[1]), [edge])

    def test_duplicate_formats_replay_and_manual_completion(self):
        fit = self.stage("fit", synthetic_fit())
        tcx = self.stage("tcx", TCX)
        edges = [self.edge(fit[0]), self.edge(tcx[0])]
        rows = [self.link(pair, edges) for pair in [fit, tcx, fit]]
        summary = coverage("a", ["s", "extra", "missing"], rows, ["s", "extra"])
        self.assertEqual(summary, coverage("a", ["missing", "extra", "s"], reversed(rows), ["extra", "s"]))
        self.assertEqual(summary, {"opportunities": 3, "reviewed_actuals": 1, "observed": ["s"],
                                   "manual_only": ["extra"], "no_observed_evidence": ["missing"]})
        with self.assertRaises(Quarantine):
            coverage("a", ["s", "other"], [rows[0], ("a", "actual", "other", "r1", "partial")])


if __name__ == "__main__":
    unittest.main()
