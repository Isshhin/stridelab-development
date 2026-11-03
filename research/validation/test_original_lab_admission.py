"""Independent negative controls for original lab provenance admission.

Everything here is an artificial source tree. No actual user code, dataset,
FIT/GPX, device credentials, or local PC is accessed.
"""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from original_lab_admission import inspect_source


class LabAdmissionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="stride-lab-gate-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.package = self.root / "src" / "coaching_lab"
        self.package.mkdir(parents=True)
        self.source = self.package / "policy.py"
        self.source.write_text("def sample_policy(x):\n    return x\n", encoding="utf-8")
        self.manifest = {
            "schema": "stridelab-original-lab-source-handoff-v1",
            "lab_id": "candidate-existing-independent-lab",
            "source_commit": "a" * 40,
            "entrypoint_file": "src/coaching_lab/policy.py",
            "source_files": [{
                "path": "src/coaching_lab/policy.py",
                "sha256": hashlib.sha256(self.source.read_bytes()).hexdigest(),
            }],
            "fixture_manifest_sha256": "f" * 64,
            "policy_ids": ["baseline-v1"],
        }
        self.save()

    def save(self):
        (self.root / "lab-handoff.json").write_text(
            json.dumps(self.manifest, sort_keys=True), encoding="utf-8")

    def test_missing_real_lab_blocks_not_passes(self):
        report = inspect_source(None)
        self.assertEqual("BLOCKED_SOURCE_NOT_PROVIDED", report["original_lab_status"])
        self.assertEqual("NOT_PERFORMED", report["execution"])
        self.assertEqual(0, report["files_verified"])
        self.assertFalse(report["origin_verified"])

    def test_manifest_hash_match_still_requires_manual_origin_review(self):
        report = inspect_source(self.root)
        self.assertEqual("READY_FOR_MANUAL_ORIGIN_AND_INTERFACE_REVIEW",
                         report["original_lab_status"])
        self.assertEqual("MANIFEST_AND_LISTED_FILE_HASHES_MATCH",
                         report["verification"])
        self.assertFalse(report["origin_verified"])
        self.assertEqual("NOT_PERFORMED", report["execution"])
        self.assertEqual(1, report["files_verified"])

    def test_edited_source_fails_hash_check(self):
        self.source.write_text("print('different code')\n")
        report = inspect_source(self.root)
        self.assertEqual("REJECTED_SOURCE_HANDOFF", report["original_lab_status"])
        self.assertEqual("SOURCE_HASH_MISMATCH", report["rejection_code"])

    def test_missing_inventory_entrypoint_rejected(self):
        self.manifest["entrypoint_file"] = "src/coaching_lab/missing.py"
        self.save()
        self.assertEqual("ENTRYPOINT_NOT_IN_SOURCE_MANIFEST",
                         inspect_source(self.root)["rejection_code"])

    def test_source_data_directory_forbidden(self):
        self.manifest["entrypoint_file"] = "data/private.py"
        self.manifest["source_files"][0]["path"] = "data/private.py"
        self.save()
        self.assertEqual("PRIVATE_SOURCE_PATH_FORBIDDEN",
                         inspect_source(self.root)["rejection_code"])

    def test_parent_escape_path_rejected(self):
        self.manifest["entrypoint_file"] = "src/../policy.py"
        self.manifest["source_files"][0]["path"] = "src/../policy.py"
        self.save()
        self.assertEqual("UNSAFE_SOURCE_PATH",
                         inspect_source(self.root)["rejection_code"])

    def test_absolute_path_and_windows_style_rejected(self):
        for bad in ("/etc/passwd", r"src\not-really\policy.py"):
            with self.subTest(bad=bad):
                self.manifest["source_files"][0]["path"] = bad
                self.save()
                self.assertIn(inspect_source(self.root)["rejection_code"],
                              {"UNSAFE_SOURCE_PATH", "SOURCE_PATH_NOT_ALLOWLISTED"})

    def test_duplicate_source_entries_rejected(self):
        self.manifest["source_files"].append(dict(self.manifest["source_files"][0]))
        self.save()
        self.assertEqual("DUPLICATE_SOURCE_FILE",
                         inspect_source(self.root)["rejection_code"])

    def test_nonexistent_source_blocks_until_provided(self):
        missing = self.root / "never-copied"
        result = inspect_source(missing)
        self.assertEqual("BLOCKED_SOURCE_OR_MANIFEST_MISSING", result["original_lab_status"])

    def test_invalid_commit_or_dataset_hash_rejected(self):
        self.manifest["source_commit"] = "maybe-commit"
        self.save()
        self.assertEqual("INVALID_SOURCE_COMMIT",
                         inspect_source(self.root)["rejection_code"])
        self.manifest["source_commit"] = "a" * 40
        self.manifest["fixture_manifest_sha256"] = "not-a-hash"
        self.save()
        self.assertEqual("INVALID_FIXTURE_REFERENCE",
                         inspect_source(self.root)["rejection_code"])

    def test_symlink_inside_source_inventory_never_followed(self):
        original = self.root / "src" / "coaching_lab" / "actual.py"
        original.write_text("x=1\n")
        self.source.unlink()
        try:
            self.source.symlink_to(original)
        except (NotImplementedError, OSError):
            self.skipTest("Symlinks not available in runner")
        self.manifest["source_files"][0]["sha256"] = hashlib.sha256(original.read_bytes()).hexdigest()
        self.save()
        self.assertEqual("SOURCE_FILE_MISSING_OR_SYMLINK",
                         inspect_source(self.root)["rejection_code"])

    def test_duplicate_policy_names_rejected(self):
        self.manifest["policy_ids"] = ["baseline-v1", "baseline-v1"]
        self.save()
        self.assertEqual("INVALID_POLICY_IDS",
                         inspect_source(self.root)["rejection_code"])


if __name__ == "__main__":
    unittest.main()
