"""Static exercise library identity test based on real native assets, not invented IDs.

Run via: python3 -m unittest discover -s native-android/tools/tests -v
"""
import json
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
CATALOG = ROOT / "app/src/main/assets/native-library/catalog.json"
SELECTOR = ROOT / "app/src/main/java/io/stridelab/privatealpha/Engine2ExerciseSelection.kt"


class Engine2ExerciseCatalogContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.entries = json.loads(CATALOG.read_text(encoding="utf-8"))["entries"]
        cls.source = SELECTOR.read_text(encoding="utf-8")
        cls.source_ids = re.findall(r'Engine2ExerciseOption\("([^"]+)"', cls.source)

    def test_source_ids_exist_and_never_duplicate(self):
        catalog_ids = [entry["id"] for entry in self.entries]
        self.assertEqual(len(catalog_ids), len(set(catalog_ids)))
        self.assertEqual(len(self.source_ids), len(set(self.source_ids)))
        self.assertTrue(set(self.source_ids) <= set(catalog_ids),
                        "Engine 2.0 points at nonexistent native catalog IDs")

    def test_every_guide_id_is_non_executable(self):
        by_id = {e["id"]: e for e in self.entries}
        # Match a reviewed record's complete constructor including nested setOf(...).
        # End the constructor at its line before the next record.
        segments = self.source.split("Engine2ExerciseOption(")[1:]
        for segment in segments:
            match = re.match(r'"([^"]+)"', segment)
            if match is None:  # data-class definition, not a catalog entry
                continue
            item_id = match.group(1)
            body = segment.split("Engine2ExerciseOption(")[0]
            if by_id[item_id].get("guide", False):
                self.assertIn("executable = false", body,
                              f"Instructional guide {item_id} was marked executable")
        self.assertIn("option.executable &&", self.source)

    def test_every_selector_guide_reference_is_actual_guide(self):
        declaration = re.search(
            r"val guideOnlyIds = setOf\((.*?)\)", self.source, flags=re.S
        )
        self.assertIsNotNone(declaration)
        guide_ids = set(re.findall(r'"([^"]+)"', declaration.group(1)))
        catalog = {e["id"]: e for e in self.entries}
        self.assertEqual(guide_ids,
                         {id_ for id_ in self.source_ids if catalog[id_].get("guide", False)})


if __name__ == "__main__":
    unittest.main()
