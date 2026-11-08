"""Regression checks for the source-of-truth UI baseline guard."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_current_ui_baseline import extract_dock_tabs, verify_sources


class CurrentUiBaselineGuardTest(unittest.TestCase):
    def test_accepted_three_tab_navigation(self):
        source = '''
        Triple("home", "Today", R.drawable.home),
        Triple("training", "Training", R.drawable.calendar),
        Triple("settings", "More", R.drawable.more)
        '''
        self.assertEqual(
            [("home", "Today"), ("training", "Training"), ("settings", "More")],
            extract_dock_tabs(source),
        )

    def test_old_activity_or_library_dock_is_detectable(self):
        obsolete = '''
        Triple("home", "Today", R.drawable.home),
        Triple("training", "Training", R.drawable.calendar),
        Triple("activities", "Activity", R.drawable.activity),
        Triple("library", "Library", R.drawable.library),
        Triple("settings", "More", R.drawable.more)
        '''
        self.assertNotEqual(
            [("home", "Today"), ("training", "Training"), ("settings", "More")],
            extract_dock_tabs(obsolete),
        )

    def test_current_repository_source_contract(self):
        self.assertEqual([], verify_sources())


if __name__ == "__main__":
    unittest.main()
