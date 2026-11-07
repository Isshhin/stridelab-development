import unittest

from .pipeline import AthleteState, build_synthetic_fixture, report, run, speed_blinded, AnalysisConfig, _segment, _route_compatible


class Dev0Tests(unittest.TestCase):
    def test_synthetic_matrix_and_deterministic_replay(self):
        activities = build_synthetic_fixture()
        first = run(activities)
        self.assertEqual(first, run(activities))
        self.assertEqual(first["athletes"]["healthy"]["state"], AthleteState.ESTIMABLE.value)
        self.assertEqual(first["athletes"]["healthy"]["final_pair_count"], 3)
        self.assertEqual(first["athletes"]["hr_mismatch"]["state"], AthleteState.INSUFFICIENT_HR_MATCHES.value)
        self.assertEqual(first["athletes"]["route_mismatch"]["state"], AthleteState.INSUFFICIENT_ROUTE_OVERLAP.value)
        self.assertEqual(first["athletes"]["trace_bad"]["state"], AthleteState.INSUFFICIENT_TRACE_QUALITY.value)
        self.assertEqual(first["athletes"]["unsupported"]["state"], AthleteState.UNSUPPORTED_MEASUREMENT.value)

    def test_endpoint_blinding_and_duplicate_isolation(self):
        activities = build_synthetic_fixture()
        baseline = run(activities)
        membership = lambda r: [(p["athlete_id"], p["earlier"], p["later"]) for p in r["pairs"]]
        self.assertEqual(membership(baseline), membership(run(speed_blinded(activities))))
        self.assertEqual(baseline["athletes"]["healthy"]["final_pair_count"], 3)
        self.assertEqual(report(activities), report(activities))

    def test_fixed_window_time_weighted_hr_and_tcx_adapter(self):
        segment = run(build_synthetic_fixture())["segments"]["synthetic-dev0|healthy|healthy-01"]
        self.assertEqual(segment["duration_s"], 1200)
        self.assertNotEqual(segment["mean_hr_bpm"], 150)
        from .pipeline import FOUR_TU_ADMISSION, parse_tcx
        self.assertEqual(FOUR_TU_ADMISSION, "RESEARCH_ONLY_ISOLATED")
        self.assertEqual(parse_tcx(b"<TrainingCenterDatabase xmlns='x'><Activities/></TrainingCenterDatabase>"), [])

    def test_scoped_duplicates_conflicts_and_funnel(self):
        activities = list(build_synthetic_fixture())
        duplicate = activities[0]
        other_athlete = type(duplicate)("other", duplicate.activity_id, duplicate.date, duplicate.samples)
        other_source = type(duplicate)(duplicate.athlete_id, duplicate.activity_id, duplicate.date, duplicate.samples, "other-source")
        result = run(activities + [duplicate, other_athlete, other_source])
        self.assertEqual(result["raw_input_count"], len(activities) + 3)
        self.assertEqual(result["duplicate_count"], 2)
        self.assertEqual(result["canonical_candidate_count"], len(activities) + 1)
        self.assertIn("other", result["athletes"])
        self.assertEqual(result["athletes"]["healthy"]["raw_input_count"], 8)
        self.assertEqual(result["athletes"]["healthy"]["duplicate_count"], 1)
        self.assertEqual(result["athletes"]["healthy"]["canonical_candidate_count"], 7)
        duplicate_only = run(activities + [duplicate])
        self.assertEqual(duplicate_only["athletes"]["healthy"]["final_pair_count"], run(activities)["athletes"]["healthy"]["final_pair_count"])
        self.assertEqual(result["athletes"]["other"]["duplicate_count"], 0)
        with self.assertRaises(ValueError):
            run(activities + [type(duplicate)(duplicate.athlete_id, duplicate.activity_id, duplicate.date, tuple(dict(s, speed=4) for s in duplicate.samples))])

    def test_named_sensitivities_and_route_features(self):
        result = run(build_synthetic_fixture())
        self.assertEqual(result["analysis"]["primary"]["config"], {"window_s": 1200, "start_s": 720, "hr_caliper_bpm": 3, "route_tolerance": "STRICT"})
        self.assertEqual(set(result["analysis"]["sensitivities"]), {"window_900s", "hr_2bpm", "hr_5bpm", "route_MODERATE", "route_LOOSE"})
        self.assertIn("path_length", result["segments"]["synthetic-dev0|healthy|healthy-01"]["route_features"])
        self.assertIn("shape", result["segments"]["synthetic-dev0|healthy|healthy-01"]["route_features"])

    def test_nonzero_te_fixture(self):
        activities = list(build_synthetic_fixture())[:0]
        base = build_synthetic_fixture()[0]
        speeds = (2.94, 3.00, 3.06, 3.00, 2.96, 3.00)
        for i, speed in enumerate(speeds):
            activities.append(type(base)("stats", f"stats-{i}", f"2026-01-{i+1:02d}T00:00:00+00:00", tuple(dict(s, speed=speed) for s in base.samples)))
        stats = run(activities)["athletes"]["stats"]
        self.assertAlmostEqual(stats["signed_bias"], 0.013333, places=5)
        self.assertAlmostEqual(stats["sd_differences"], 0.064291, places=5)
        self.assertAlmostEqual(stats["TE"], 0.045461, places=5)
        self.assertEqual(stats["agreement_limits"], [-0.112677, 0.139344])

    def test_adversarial_trace_matrix_and_direction(self):
        fixture = build_synthetic_fixture()
        trace_result = run(fixture)
        self.assertEqual(trace_result["segments"]["synthetic-dev0|trace_bad|trace_bad-00"]["qc_status"], "PASS")
        self.assertFalse(trace_result["segments"]["synthetic-dev0|trace_bad|trace_bad-00"]["supported_measurement"])
        self.assertIn("UNRECOVERABLE_GAP", run(fixture)["segments"]["synthetic-dev0|trace_bad|trace_bad-01"]["exclusion_reasons"])
        self.assertIn("UNPHYSICAL_SPEED", run(fixture)["segments"]["synthetic-dev0|trace_bad|trace_bad-02"]["exclusion_reasons"])
        base = fixture[0]
        reversed_samples = type(base)("reverse", "reverse-00", base.date, tuple(reversed(base.samples)))
        self.assertIn("NON_MONOTONIC_TIMESTAMP", run((reversed_samples,))["segments"]["synthetic-dev0|reverse|reverse-00"]["exclusion_reasons"])
        turned = type(base)("reverse", "reverse-01", "2026-01-02T00:00:00+00:00", base.samples, route_direction="BACK")
        self.assertFalse(_route_compatible(_segment(base, AnalysisConfig()), _segment(turned, AnalysisConfig()), "STRICT"))

    def test_trace_qc_is_separate_from_measurement_support(self):
        base = build_synthetic_fixture()[0]
        cases = {
            "gps": ("unsupported", "unsupported-00"),
            "hr": ("hr_support", "hr_support-00"),
        }
        for bad, (athlete, activity_id) in cases.items():
            activity = next(a for a in build_synthetic_fixture() if a.activity_id == f"{athlete}-00") if bad == "gps" else type(base)(athlete, activity_id, base.date, tuple(dict(s, hr=None) if 820 <= s["t"] < 920 else s for s in base.samples))
            result = run((activity,))
            segment = next(iter(result["segments"].values()))
            self.assertTrue(segment["fixed_window_available"])
            self.assertEqual(segment["qc_status"], "PASS")
            self.assertFalse(segment["supported_measurement"])
            self.assertEqual(result["athletes"][athlete]["state"], AthleteState.UNSUPPORTED_MEASUREMENT.value)
            self.assertEqual(result["route_compatible_count"], 0)

        speed = type(base)("speed_support", "speed_support-00", base.date, tuple(dict(s, speed=None) if 820 <= s["t"] < 920 else s for s in base.samples))
        segment = next(iter(run((speed,))["segments"].values()))
        self.assertEqual(segment["qc_status"], "PASS")
        self.assertFalse(segment["supported_measurement"])

    def test_structural_trace_failures(self):
        base = build_synthetic_fixture()[0]
        cases = {
            "reverse": tuple(reversed(base.samples)),
            "gap": tuple(s for s in base.samples if s["t"] < 820 or s["t"] > 850),
            "pause": tuple(dict(s, pause=820 <= s["t"] < 850) for s in base.samples),
            "bounds": tuple(dict(s, hr=250) if s["t"] == 1220 else s for s in base.samples),
        }
        for name, samples in cases.items():
            segment = next(iter(run((type(base)(name, f"{name}-00", base.date, samples),))["segments"].values()))
            self.assertEqual(segment["qc_status"], "REJECT")

    def test_same_route_jitter_and_interior_shape_are_tolerance_sensitive(self):
        base = build_synthetic_fixture()[0]
        jitter = tuple(dict(s, lat=s["lat"] + .0002, lon=s["lon"] + .0001) for s in base.samples)
        different = tuple(dict(s, lat=s["lat"] + (.001 if 1000 <= s["t"] <= 1600 else 0)) for s in base.samples)
        a = _segment(base, AnalysisConfig()); b = _segment(type(base)("x", "b", base.date, jitter), AnalysisConfig()); c = _segment(type(base)("x", "c", base.date, different), AnalysisConfig())
        self.assertTrue(_route_compatible(a, b, "STRICT"))
        self.assertFalse(_route_compatible(a, c, "STRICT"))
        self.assertTrue(_route_compatible(a, c, "MODERATE"))

    def test_funnel_stages_and_states_are_explicit(self):
        result = run(build_synthetic_fixture())
        primary = result["analysis"]["primary"]
        self.assertEqual({k for k in ("raw_input_count", "canonical_candidate_count", "duplicate_count", "fixed_window_count", "qc_pass_count", "supported_measurement_count", "route_compatible_count", "chronological_candidate_pair_count", "hr_compatible_pair_count", "final_pair_count") if k in result}, {"raw_input_count", "canonical_candidate_count", "duplicate_count", "fixed_window_count", "qc_pass_count", "supported_measurement_count", "route_compatible_count", "chronological_candidate_pair_count", "hr_compatible_pair_count", "final_pair_count"})
        self.assertEqual(primary["chronological_candidate_pair_count"], 4)
        self.assertGreater(primary["chronological_candidate_pair_count"], primary["hr_compatible_pair_count"])
        self.assertEqual(result["athletes"]["route_mismatch"]["state"], AthleteState.INSUFFICIENT_ROUTE_OVERLAP.value)
        self.assertEqual(result["athletes"]["hr_mismatch"]["chronological_candidate_pair_count"], 1)
        self.assertEqual(result["athletes"]["hr_mismatch"]["hr_compatible_pair_count"], 0)

    def test_unavailable_window_and_doi(self):
        from .pipeline import FOUR_TU_DOI
        base = build_synthetic_fixture()[0]
        short = type(base)("short", "short-00", base.date, tuple(s for s in base.samples if s["t"] < 1800))
        result = run((short,))
        self.assertEqual(result["fixed_window_count"], 0)
        self.assertEqual(result["qc_pass_count"], 0)
        self.assertEqual(FOUR_TU_DOI, "10.4121/efa64223-ba51-48fa-91be-53f0e48460b4.v1")

    def test_same_route_jitter_uses_grouping_for_state(self):
        base = build_synthetic_fixture()[0]
        activities = tuple(type(base)("jitter", f"jitter-{i}", f"2026-01-{i+1:02d}T00:00:00+00:00", tuple(dict(s, lat=s["lat"] + i*.0002, lon=s["lon"] + i*.0001) for s in base.samples)) for i in range(6))
        result = run(activities)
        self.assertEqual(result["athletes"]["jitter"]["state"], AthleteState.ESTIMABLE.value)


if __name__ == "__main__":
    unittest.main()
