import unittest

from .pipeline import AthleteState, build_synthetic_fixture, report, run, speed_blinded


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
        segment = run(build_synthetic_fixture())["segments"]["healthy-01"]
        self.assertEqual(segment["duration_s"], 1200)
        self.assertNotEqual(segment["mean_hr_bpm"], 150)
        from .pipeline import FOUR_TU_ADMISSION, parse_tcx
        self.assertEqual(FOUR_TU_ADMISSION, "RESEARCH_ONLY_ISOLATED")
        self.assertEqual(parse_tcx(b"<TrainingCenterDatabase xmlns='x'><Activities/></TrainingCenterDatabase>"), [])


if __name__ == "__main__":
    unittest.main()
