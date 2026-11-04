"""End-to-end controls from synthetic original bytes to a private linked QC result."""
from dataclasses import replace
from hashlib import sha256
import unittest

from p2_pilot_probe import inspect_linked_tcx
from p2_trace import Reference
from p2f_linkage import PilotLedger, Quarantine
from test_p2_trace import tick, point, tcx


class IntegratedPilot(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = tcx(point(t) for t in range(0, 1801, 5))
        cls.checksum = sha256(cls.raw).hexdigest()
        cls.ref = Reference(source_digest=cls.checksum, device_epoch="fixture",
                            epochs_seen=("fixture",), method="calibrated_footpod",
                            calibration="fixture-cal", sensor="electrical_chest_strap",
                            marker_crossings=(tick(600),), timer_events=((tick(0), "start"),),
                            segment_timer_seconds=1200, reference_id="fixture-ref",
                            reference_complete=True, artifact_free=True, configured_interval_s=5,
                            route_card_version="route1", firmware="fixture-v1")

    def setUp(self):
        self.scope = ("a", "p", "grant", "development-pilot", "reviewer")
        key = ("direct", "original", "file", "tcx")
        self.ledger = PilotLedger(self.scope, tick(-100).isoformat(), {key: "bout"},
                                  {"review": ("bout", "opp", "rev")})
        def row(event, kind, t, **data):
            stamp = tick(t).isoformat()
            return dict(id=event, kind=kind, scope=self.scope, effective=stamp,
                        recorded=stamp, available=stamp, data=data)
        self.rows = [
            row("plan", "initial", -90, opportunity="opp", plan="plan", version="v1",
                revision="rev", locked=False, parent=None, parent_version=None, intent="easy"),
            row("delivery", "receipt", -80, opportunity="opp", revision="rev"),
            row("file", "source", 3600, activity="bout", performed_at=tick(0).isoformat(),
                ended_at=tick(1800).isoformat(), imported_at=tick(3600).isoformat(),
                connection=key[0], namespace=key[1], external=key[2], format=key[3],
                digest=self.checksum),
            row("edge", "link", 3610, activity="bout", opportunity="opp", revision="rev",
                evidence="review")]
        self.ledger.append(self.scope, list(reversed(self.rows)))

    def inspect(self, *, cutoff=4000, raw=None, ref=None, scope=None):
        return inspect_linked_tcx(self.ledger, scope or self.scope, tick(cutoff).isoformat(),
                                  "edge", "file", self.raw if raw is None else raw, ref or self.ref)

    def test_linked_measurement_is_replayable_after_duplicate(self):
        original = self.inspect()
        self.ledger.append(self.scope, self.rows)
        self.assertEqual(self.inspect(), original)
        self.assertEqual((original["status"], original["distance_m"]), ("PRIVATE_TRACE_QC_ONLY", 3600))

    def test_late_upload_or_unreviewed_link(self):
        for cutoff in (1801, 3605):
            with self.subTest(cutoff=cutoff), self.assertRaises(Quarantine):
                self.inspect(cutoff=cutoff)

    def test_wrong_original_or_reference(self):
        with self.assertRaises(Quarantine):
            self.inspect(raw=self.raw + b" ")
        with self.assertRaises(Quarantine):
            self.inspect(ref=replace(self.ref, source_digest="0"*64))

    def test_scope_and_revocation_before_decoding(self):
        with self.assertRaises(Quarantine):
            self.inspect(scope=("other", *self.scope[1:]))
        self.ledger.withdraw(self.scope)
        with self.assertRaises(Quarantine):
            self.inspect()
