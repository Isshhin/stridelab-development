"""Pure invented-fixture tests for the P2-X DEV-2 plan-identity contract."""
from copy import deepcopy
import json
import unittest

from p2x_lineage import assess, migrate_p2c_fixture, _native_digest, SCHEMA

NOW = "2026-11-08T12:00:00+00:00"
LATER = "2026-11-09T12:00:00+00:00"
BEFORE = "2026-11-07T12:00:00+00:00"
X = "a" * 64
Y = "b" * 64


def rec(name, **changes):
    v = {"event_id": name, "athlete_id": "A-invented", "source_id": "source-invented",
         "observed_at": BEFORE, "available_at": BEFORE,
         "status": "measured", "quality": "good", "source_revision": 1,
         "lineage_root_id": {"A1": "p2a:root", "C1": "p2c:root",
                             "D1": "p2d:root", "E1": "p2e:root"}.get(name, "other:root")}
    v.update(changes)
    return v


def fixture():
    bundle = {"synthetic_only": True, "athlete_id": "A-invented", "opportunity_id": "O-1", "as_of": NOW,
              "p2a": {"synthetic_only": True, "athlete_id": "A-invented", "as_of": NOW,
                      "observations": [rec("A1")]},
              "p2c": {"synthetic_only": True, "athlete_id": "A-invented", "as_of": NOW,
                      "plan_version": 7, "opportunities": [{"opportunity_id": "C-OP", "plan_version": 7}],
                      "events": [rec("C1", opportunity_id="C-OP", plan_version=7, status="completed")]},
              "p2d": {"synthetic_only": True, "athlete_id": "A-invented", "as_of": NOW,
                      "opportunity_id": "O-1", "plan": {"plan_id": "P-1", "version": 7,
                                                          "sessions": [{"id": "D-S"}]},
                      "request": {"session_id": "D-S"},
                      "observations": [rec("D1", session_id="D-S")]},
              "p2e": {"synthetic_only": True, "athlete_id": "A-invented", "as_of": NOW,
                      "opportunity_id": "O-1", "plan": {"id": "P-1", "version": 7,
                                                          "sessions": [{"id": "E-S"}]},
                      "review_context": {"target_session_id": "E-S"},
                      "observations": [rec("E1", plan_id="P-1", plan_version=7,
                                           session_id="E-S", status="completed")]}}
    m = {"schema": SCHEMA, "synthetic_only": True, "athlete_id": "A-invented",
         "plan_id": "P-1", "plan_version": 7, "opportunity_id": "O-1", "as_of": NOW,
         "plan_revision_sha256": X, "ledger_tip": Y, "p2c_native_plan_id": None,
         "links": {"p2c_opportunity_id": "C-OP", "p2d_session_id": "D-S", "p2e_session_id": "E-S"},
         "evidence_bindings": []}
    for track, event in (("p2a", "A1"), ("p2c", "C1"), ("p2d", "D1"), ("p2e", "E1")):
        m["evidence_bindings"].append({"track": track, "event_id": event, "source_id": "source-invented",
                                        "ledger_event_id": track + ":" + event, "source_revision": 1,
                                        "lineage_root_id": track + ":root", "payload_sha256":
                                        _native_digest(bundle[track]["events" if track == "p2c" else "observations"][0]),
                                        "observed_at": BEFORE, "available_at": BEFORE})
    ledger = {"athlete_id": "A-invented", "plan_id": "P-1", "plan_version": 7,
              "opportunity_id": "O-1", "as_of": NOW, "ledger_tip": Y,
              "active_records": [{"event_id": t + ":" + eid, "source_id": "source-invented",
                                  "payload_digest": next(b["payload_sha256"] for b in m["evidence_bindings"] if b["track"] == t)}
                                 for t, eid in (("p2a", "A1"), ("p2c", "C1"),
                                                ("p2d", "D1"), ("p2e", "E1"))],
              "withdrawal_event_ids": [], "revoked_sources": [], "corrected_event_ids": []}
    return bundle, m, ledger


class LineageTests(unittest.TestCase):
    def test_01_consistent_fixture_is_not_authenticated(self):
        b, m, l = fixture()
        out = assess(b, m, l)
        self.assertEqual("SYNTHETIC_ASSERTED_CONSISTENCY_ONLY", out["status"])
        self.assertEqual("NOT_AUTHENTICATED", out["source_authentication"])
        self.assertFalse(out["safe_to_auto_apply"])
        self.assertFalse(out["authenticated_athlete_approval"])
        self.assertEqual("CLOSED", out["gate2"])

    def test_02_without_ledger_projection_deferred(self):
        b, m, l = fixture()
        self.assertEqual("DEFER_LEDGER_PROJECTION_ABSENT", assess(b, m)["status"])

    def test_03_no_bindings_deferred(self):
        b, m, l = fixture()
        m["evidence_bindings"] = []
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", assess(b, m, l)["status"])

    def test_04_p2c_plan_id_missing_still_unverified(self):
        b, m, l = fixture()
        self.assertEqual("UNVERIFIED_MISSING_NATIVE_CONTRACT", assess(b, m, l)["p2c_native_plan_id"])

    def test_05_p2c_id_injected_cannot_prove_identity(self):
        b, m, l = fixture()
        b["p2c"]["plan_id"] = "P-1"
        m["p2c_native_plan_id"] = "P-1"
        with self.assertRaisesRegex(ValueError, "P2C_NATIVE_PLAN_JOIN_INCOMPLETE"):
            assess(b, m, l)

    def test_06_injected_p2c_mismatch_rejected(self):
        b, m, l = fixture()
        m["p2c_native_plan_id"] = "different"
        with self.assertRaisesRegex(ValueError, "P2C_PLAN_ID_CLAIM_MISMATCH"):
            assess(b, m, l)

    def test_07_mismatched_p2d_plan_rejected(self):
        b, m, l = fixture()
        b["p2d"]["plan"]["plan_id"] = "P-2"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_PLAN_MISMATCH"):
            assess(b, m, l)

    def test_08_mismatched_p2e_plan_rejected(self):
        b, m, l = fixture()
        b["p2e"]["plan"]["id"] = "P-2"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_PLAN_MISMATCH"):
            assess(b, m, l)

    def test_09_mismatched_p2c_version_rejected(self):
        b, m, l = fixture()
        b["p2c"]["plan_version"] = 8
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_PLAN_MISMATCH"):
            assess(b, m, l)

    def test_10_boolean_version_rejected(self):
        b, m, l = fixture()
        m["plan_version"] = True
        with self.assertRaisesRegex(ValueError, "PLAN_VERSION_INVALID"):
            assess(b, m, l)

    def test_11_cross_athlete_rejected(self):
        b, m, l = fixture()
        b["p2e"]["athlete_id"] = "other"
        with self.assertRaisesRegex(ValueError, "TRACK_SCOPE_OR_CUTOFF_MISMATCH"):
            assess(b, m, l)

    def test_12_mismatched_cutoff_rejected(self):
        b, m, l = fixture()
        b["p2d"]["as_of"] = LATER
        with self.assertRaisesRegex(ValueError, "TRACK_SCOPE_OR_CUTOFF_MISMATCH"):
            assess(b, m, l)

    def test_13_equivalent_time_offset_accepted(self):
        b, m, l = fixture()
        b["p2d"]["as_of"] = "2026-11-08T20:00:00+08:00"
        self.assertEqual("SYNTHETIC_ASSERTED_CONSISTENCY_ONLY", assess(b, m, l)["status"])

    def test_14_naive_time_fails_closed(self):
        b, m, l = fixture()
        m["as_of"] = "2026-11-08T12:00:00"
        with self.assertRaisesRegex(ValueError, "OFFSET_TIME_REQUIRED"):
            assess(b, m, l)

    def test_15_opportunity_mismatch(self):
        b, m, l = fixture()
        b["p2e"]["opportunity_id"] = "O-other"
        with self.assertRaisesRegex(ValueError, "CROSS_TRACK_OPPORTUNITY_MISMATCH"):
            assess(b, m, l)

    def test_16_p2c_target_not_found(self):
        b, m, l = fixture()
        m["links"]["p2c_opportunity_id"] = "missing"
        with self.assertRaisesRegex(ValueError, "P2C_LINK_NOT_FOUND"):
            assess(b, m, l)

    def test_17_p2d_target_mismatch(self):
        b, m, l = fixture()
        m["links"]["p2d_session_id"] = "other"
        with self.assertRaisesRegex(ValueError, "TARGET_SESSION_LINK_MISMATCH_P2D"):
            assess(b, m, l)

    def test_18_p2e_target_mismatch(self):
        b, m, l = fixture()
        m["links"]["p2e_session_id"] = "other"
        with self.assertRaisesRegex(ValueError, "TARGET_SESSION_LINK_MISMATCH_P2E"):
            assess(b, m, l)

    def test_19_native_evidence_missing(self):
        b, m, l = fixture()
        m["evidence_bindings"][0]["event_id"] = "unknown"
        with self.assertRaisesRegex(ValueError, "NATIVE_EVIDENCE_LINK_MISMATCH"):
            assess(b, m, l)

    def test_20_native_source_mismatch(self):
        b, m, l = fixture()
        b["p2d"]["observations"][0]["source_id"] = "another"
        with self.assertRaisesRegex(ValueError, "NATIVE_EVIDENCE_LINK_MISMATCH"):
            assess(b, m, l)

    def test_21_duplicate_mapping_rejected(self):
        b, m, l = fixture()
        m["evidence_bindings"].append(deepcopy(m["evidence_bindings"][0]))
        with self.assertRaisesRegex(ValueError, "DUPLICATE_EVIDENCE_BINDING"):
            assess(b, m, l)

    def test_22_reused_ledger_identity_rejected(self):
        b, m, l = fixture()
        m["evidence_bindings"][1]["ledger_event_id"] = m["evidence_bindings"][0]["ledger_event_id"]
        with self.assertRaisesRegex(ValueError, "AMBIGUOUS_CROSS_TRACK_LEDGER_ID"):
            assess(b, m, l)

    def test_23_same_native_event_id_different_tracks_namespaced(self):
        b, m, l = fixture()
        b["p2d"]["observations"][0]["event_id"] = "C1"
        m["evidence_bindings"][2]["event_id"] = "C1"
        m["evidence_bindings"][2]["ledger_event_id"] = "p2d:C1"
        l["active_records"][2]["event_id"] = "p2d:C1"
        m["evidence_bindings"][2]["payload_sha256"] = _native_digest(b["p2d"]["observations"][0])
        l["active_records"][2]["payload_digest"] = m["evidence_bindings"][2]["payload_sha256"]
        self.assertEqual(4, len(assess(b, m, l)["evidence"]))

    def test_24_wrong_payload_rejected(self):
        b, m, l = fixture()
        l["active_records"][0]["payload_digest"] = Y
        with self.assertRaisesRegex(ValueError, "LEDGER_BINDING_MISMATCH"):
            assess(b, m, l)

    def test_25_missing_active_record_defers(self):
        b, m, l = fixture()
        l["active_records"].pop()
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", assess(b, m, l)["status"])

    def test_26_source_revocation_defers(self):
        b, m, l = fixture()
        l["revoked_sources"] = ["source-invented"]
        self.assertEqual("DEFER_LEDGER_CORRECTION_WITHDRAWAL", assess(b, m, l)["status"])

    def test_27_correction_defers(self):
        b, m, l = fixture()
        l["corrected_event_ids"] = ["c1"]
        self.assertEqual("DEFER_LEDGER_CORRECTION_WITHDRAWAL", assess(b, m, l)["status"])

    def test_28_withdrawal_defers(self):
        b, m, l = fixture()
        l["withdrawal_event_ids"] = ["w1"]
        self.assertEqual("DEFER_LEDGER_CORRECTION_WITHDRAWAL", assess(b, m, l)["status"])

    def test_29_ledger_tip_change_rejected(self):
        b, m, l = fixture()
        l["ledger_tip"] = X
        with self.assertRaisesRegex(ValueError, "LEDGER_TIP_MISMATCH"):
            assess(b, m, l)

    def test_30_future_evidence_is_excluded(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["available_at"] = LATER
        m["evidence_bindings"][0]["available_at"] = LATER
        out = assess(b, m, l)
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", out["status"])
        self.assertEqual(1, out["excluded_by_reason"]["AFTER_DECISION_CUTOFF"])

    def test_31_backdated_availability_rejected(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["available_at"] = "2026-11-06T12:00:00+00:00"
        m["evidence_bindings"][0]["available_at"] = "2026-11-06T12:00:00+00:00"
        with self.assertRaisesRegex(ValueError, "EVIDENCE_AVAILABILITY_BACKDATED"):
            assess(b, m, l)

    def test_32_p2c_foreign_plan_link_rejected(self):
        b, m, l = fixture()
        b["p2c"]["events"][0]["opportunity_id"] = "other"
        with self.assertRaisesRegex(ValueError, "P2C_EVIDENCE_PLAN_MISMATCH"):
            assess(b, m, l)

    def test_33_p2d_foreign_session_rejected(self):
        b, m, l = fixture()
        b["p2d"]["observations"][0]["session_id"] = "other"
        with self.assertRaisesRegex(ValueError, "P2D_EVIDENCE_SESSION_MISMATCH"):
            assess(b, m, l)

    def test_34_p2e_foreign_plan_rejected(self):
        b, m, l = fixture()
        b["p2e"]["observations"][0]["plan_id"] = "other"
        with self.assertRaisesRegex(ValueError, "P2E_EVIDENCE_PLAN_MISMATCH"):
            assess(b, m, l)

    def test_35_source_revision_mismatch_rejected(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["source_revision"] = 2
        with self.assertRaisesRegex(ValueError, "SOURCE_REVISION_MISMATCH"):
            assess(b, m, l)

    def test_36_unmeasured_data_deferred(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["status"] = "missing_upload"
        out = assess(b, m, l)
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", out["status"])

    def test_37_unhashable_status_is_deferred_not_crash(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["status"] = ["unknown"]
        out = assess(b, m, l)
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", out["status"])

    def test_38_immutable_and_json_serializable(self):
        b, m, l = fixture()
        previous = deepcopy((b, m, l))
        out = assess(b, m, l)
        self.assertEqual((b, m, l), previous)
        json.dumps(out, allow_nan=False, sort_keys=True)

    def test_39_input_binding_order_deterministic(self):
        b, m, l = fixture()
        out = assess(b, m, l)
        m["evidence_bindings"] = list(reversed(m["evidence_bindings"]))
        self.assertEqual(out, assess(b, m, l))

    def test_40_correlated_roots_reported_no_votes(self):
        b, m, l = fixture()
        m["evidence_bindings"][1]["lineage_root_id"] = m["evidence_bindings"][0]["lineage_root_id"]
        b["p2c"]["events"][0]["lineage_root_id"] = m["evidence_bindings"][1]["lineage_root_id"]
        m["evidence_bindings"][1]["payload_sha256"] = _native_digest(b["p2c"]["events"][0])
        l["active_records"][1]["payload_digest"] = m["evidence_bindings"][1]["payload_sha256"]
        out = assess(b, m, l)
        self.assertEqual(["p2a:root"], out["potentially_correlated_lineage_roots"])
        self.assertNotIn("readiness", out)


    def test_41_migrate_p2c_copy_adds_full_native_id(self):
        b, m, l = fixture()
        old = deepcopy(b["p2c"])
        b["p2c"] = migrate_p2c_fixture(b["p2c"], "P-1")
        m["p2c_native_plan_id"] = "P-1"
        m["evidence_bindings"][1]["payload_sha256"] = _native_digest(b["p2c"]["events"][0])
        l["active_records"][1]["payload_digest"] = m["evidence_bindings"][1]["payload_sha256"]
        self.assertNotIn("plan_id", old)
        out = assess(b, m, l)
        self.assertEqual("SYNTHETIC_NATIVE_ID_CONSISTENT_UNAUTHENTICATED", out["p2c_native_plan_id"])
        self.assertEqual("NOT_AUTHENTICATED", out["source_authentication"])

    def test_42_migrate_p2c_does_not_mutate_original(self):
        b, m, l = fixture()
        before = deepcopy(b["p2c"])
        migrated = migrate_p2c_fixture(b["p2c"], "P-1")
        self.assertEqual(before, b["p2c"])
        self.assertNotIn("plan_id", before)
        self.assertEqual("P-1", migrated["events"][0]["plan_id"])

    def test_43_migrate_foreign_native_id_rejected(self):
        b, m, l = fixture()
        b["p2c"]["events"][0]["plan_id"] = "another"
        with self.assertRaisesRegex(ValueError, "P2C_MIGRATION_CONFLICTING_NATIVE_ID"):
            migrate_p2c_fixture(b["p2c"], "P-1")

    def test_44_migrate_wrong_plan_version_rejected(self):
        b, m, l = fixture()
        b["p2c"]["events"][0]["plan_version"] = 8
        with self.assertRaisesRegex(ValueError, "P2C_MIGRATION_VERSION_MISMATCH"):
            migrate_p2c_fixture(b["p2c"], "P-1")

    def test_45_missing_native_plan_id_blocks_claim(self):
        b, m, l = fixture()
        m["p2c_native_plan_id"] = "P-1"
        with self.assertRaisesRegex(ValueError, "P2C_NATIVE_ID_CLAIM_UNBACKED"):
            assess(b, m, l)

    def test_46_migrated_mismatch_event_native_id_rejected(self):
        b, m, l = fixture()
        b["p2c"] = migrate_p2c_fixture(b["p2c"], "P-1")
        m["p2c_native_plan_id"] = "P-1"
        b["p2c"]["events"][0]["plan_id"] = "other"
        with self.assertRaisesRegex(ValueError, "P2C_NATIVE_PLAN_JOIN_INCOMPLETE"):
            assess(b, m, l)

    def test_47_future_outcome_label_rejected_anywhere(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["future_outcome"] = "improved"
        with self.assertRaisesRegex(ValueError, "FUTURE_OUTCOME_LABEL_FORBIDDEN"):
            assess(b, m, l)

    def test_48_badly_nested_outcome_label_rejected(self):
        b, m, l = fixture()
        b["p2c"]["nested"] = [{"heldout_label": 1}]
        with self.assertRaisesRegex(ValueError, "FUTURE_OUTCOME_LABEL_FORBIDDEN"):
            assess(b, m, l)

    def test_49_unqualified_quality_object_deferred(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["quality"] = {"bad": 1}
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", assess(b, m, l)["status"])

    def test_50_unauthorized_manifest_fields_refused(self):
        b, m, l = fixture()
        m["source_authorized"] = True
        with self.assertRaisesRegex(ValueError, "MANIFEST_SCHEMA_MISMATCH"):
            assess(b, m, l)

    def test_51_payload_mutation_must_not_pass_silent_consistency(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["value"] = 999999
        with self.assertRaisesRegex(ValueError, "NATIVE_PAYLOAD_DIGEST_MISMATCH"):
            assess(b, m, l)

    def test_52_omitting_three_eligible_native_bindings_defers(self):
        b, m, l = fixture()
        m["evidence_bindings"] = m["evidence_bindings"][:1]
        out = assess(b, m, l)
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", out["status"])
        self.assertEqual(3, out["excluded_by_reason"]["UNBOUND_ELIGIBLE_NATIVE_EVENT"])

    def test_53_missing_native_source_revision_defers(self):
        b, m, l = fixture()
        del b["p2a"]["observations"][0]["source_revision"]
        out = assess(b, m, l)
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", out["status"])
        self.assertEqual(1, out["excluded_by_reason"]["MISSING_NATIVE_SOURCE_REVISION"])

    def test_54_incorrect_native_lineage_root_fails(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["lineage_root_id"] = "forged-root"
        with self.assertRaisesRegex(ValueError, "NATIVE_LINEAGE_ROOT_MISMATCH"):
            assess(b, m, l)

    def test_55_changed_planned_workout_payload_fails(self):
        b, m, l = fixture()
        b["p2d"]["observations"][0]["distance_km"] = 42
        with self.assertRaisesRegex(ValueError, "NATIVE_PAYLOAD_DIGEST_MISMATCH"):
            assess(b, m, l)

    def test_56_extra_eligible_record_requires_binding(self):
        b, m, l = fixture()
        b["p2a"]["observations"].append(rec("A2"))
        out = assess(b, m, l)
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", out["status"])
        self.assertEqual(1, out["excluded_by_reason"]["UNBOUND_ELIGIBLE_NATIVE_EVENT"])

    def test_57_unhashable_native_status_is_deferred(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["status"] = ["measured"]
        out = assess(b, m, l)
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", out["status"])
        self.assertEqual(1, out["excluded_by_reason"]["UNVERIFIED_NATIVE_RECORD"])

    def test_58_unknown_native_status_cannot_pass(self):
        b, m, l = fixture()
        b["p2a"]["observations"][0]["status"] = "invented_status"
        out = assess(b, m, l)
        self.assertEqual("DEFER_UNVERIFIED_EVIDENCE", out["status"])
        self.assertEqual(1, out["excluded_by_reason"]["UNVERIFIED_NATIVE_RECORD"])


if __name__ == "__main__":
    unittest.main()
