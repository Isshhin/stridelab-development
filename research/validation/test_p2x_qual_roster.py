"""Scoped synthetic roster identity attacks against P2-X DEV-2."""
from copy import deepcopy
import unittest

from p2x_lineage import assess
from test_p2x_lineage import fixture


class RosterIdentityAttacks(unittest.TestCase):
    def test_duplicate_p2c_target_id_rejected(self):
        b, m, l = fixture()
        b["p2c"]["opportunities"].append(deepcopy(b["p2c"]["opportunities"][0]))
        with self.assertRaisesRegex(ValueError, "P2C_OPPORTUNITY_ID_DUPLICATE"):
            assess(b, m, l)

    def test_duplicate_p2d_target_id_rejected(self):
        b, m, l = fixture()
        b["p2d"]["plan"]["sessions"].append(deepcopy(b["p2d"]["plan"]["sessions"][0]))
        with self.assertRaisesRegex(ValueError, "P2D_SESSION_ID_DUPLICATE"):
            assess(b, m, l)

    def test_duplicate_p2e_target_id_rejected(self):
        b, m, l = fixture()
        b["p2e"]["plan"]["sessions"].append(deepcopy(b["p2e"]["plan"]["sessions"][0]))
        with self.assertRaisesRegex(ValueError, "P2E_SESSION_ID_DUPLICATE"):
            assess(b, m, l)

    def test_missing_p2c_extra_opportunity_id_rejected(self):
        b, m, l = fixture()
        b["p2c"]["opportunities"].append({"plan_version": 7})
        with self.assertRaisesRegex(ValueError, "P2C_OPPORTUNITY_ID_INVALID"):
            assess(b, m, l)

    def test_duplicate_non_target_p2d_session_id_rejected(self):
        b, m, l = fixture()
        b["p2d"]["plan"]["sessions"] += [{"id": "OTHER"}, {"id": "OTHER"}]
        with self.assertRaisesRegex(ValueError, "P2D_SESSION_ID_DUPLICATE"):
            assess(b, m, l)

    def test_duplicate_non_target_p2e_session_id_rejected(self):
        b, m, l = fixture()
        b["p2e"]["plan"]["sessions"] += [{"id": "OTHER"}, {"id": "OTHER"}]
        with self.assertRaisesRegex(ValueError, "P2E_SESSION_ID_DUPLICATE"):
            assess(b, m, l)

    def test_unique_non_target_sessions_remain_valid(self):
        b, m, l = fixture()
        b["p2d"]["plan"]["sessions"].append({"id": "OTHER-D"})
        b["p2e"]["plan"]["sessions"].append({"id": "OTHER-E"})
        self.assertEqual("SYNTHETIC_ASSERTED_CONSISTENCY_ONLY", assess(b, m, l)["status"])

    def test_distinct_extra_p2c_opportunity_remains_valid(self):
        b, m, l = fixture()
        b["p2c"]["opportunities"].append({"opportunity_id": "OTHER-C", "plan_version": 7})
        self.assertEqual("SYNTHETIC_ASSERTED_CONSISTENCY_ONLY", assess(b, m, l)["status"])


if __name__ == "__main__":
    unittest.main()
