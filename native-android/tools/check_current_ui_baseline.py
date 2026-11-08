#!/usr/bin/env python3
"""Fail fast when StrideLab UI work starts from an obsolete branch or shell.

This checks source-level invariants only. Android Studio/device screenshot
acceptance and data-upgrade safety remain separate gates.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
# The first combined branch with the current real three-tab Today UI,
# approved lime Plan Builder, SQLite large-record fix, and Share Studio 2.0.
# Do not silently update: require owner acceptance of a new baseline.
REQUIRED_BASELINE = "2c43bdcb01941b253658222a57b7ba8b9953ab20"
ANDROID = ROOT / "native-android/app/src/main/java/io/stridelab/privatealpha"


def extract_dock_tabs(source: str) -> list[tuple[str, str]]:
    """Extract actual UI destinations, not unrelated internal NavHost routes."""
    return re.findall(r'Triple\(\s*"([^"]+)"\s*,\s*"([^"]+)"', source)


def read_source(relative: str, failures: list[str]) -> str:
    file = ANDROID / relative
    if not file.is_file():
        failures.append(f"Missing current Android source: {file.relative_to(ROOT)}")
        return ""
    return file.read_text(encoding="utf-8")


def verify_sources() -> list[str]:
    failures: list[str] = []
    navigation = read_source("TracksideFloatingNavigation.kt", failures)
    tabs = extract_dock_tabs(navigation)
    expected = [("home", "Today"), ("training", "Training"), ("settings", "More")]
    if tabs != expected:
        failures.append(f"Dock destinations changed: {tabs!r}; expected {expected!r}")

    main = read_source("NativeMainActivity.kt", failures)
    if "StrideLabTodayScreen(" not in main:
        failures.append("NativeMainActivity no longer uses the current Today screen")
    if "TracksideFloatingNavigation(" not in main:
        failures.append("NativeMainActivity no longer uses the current floating dock")

    today = read_source("StrideLabTodayScreen.kt", failures)
    if "TodayCompactRecordedCards(" not in today:
        failures.append("Current compact recorded Today cards are missing")

    builder = read_source("PlanBuilderPalette.kt", failures)
    if "internal object PlanBuilderPalette" not in builder:
        failures.append("Approved lime Plan Builder palette is missing")

    share = read_source("ShareStudioModels.kt", failures)
    if not re.search(r"shareRoutePresentation\s*\(", share):
        failures.append("Share Studio no longer uses shared privacy route presentation")
    if "val transparent:Boolean" not in share:
        failures.append("Share Studio 2.0 transparent export configuration is missing")

    route = read_source("ShareRoutePresentation.kt", failures)
    if "fun shareRoutePresentation(" not in route:
        failures.append("Share Studio Stage 1 privacy route pipeline is missing")
    return failures


def verify_ancestry() -> list[str]:
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", REQUIRED_BASELINE, "HEAD"],
        cwd=ROOT, check=False, capture_output=True, text=True,
    )
    if result.returncode == 0:
        return []
    if result.returncode == 1:
        return [
            "UI checkout does not descend from the combined modern Android "
            f"baseline {REQUIRED_BASELINE}. Do not copy or merge an older "
            "branch into the current app. Start a narrow integration from the "
            "current candidate after protecting existing local changes."
        ]
    return [
        "Could not verify Git ancestry (missing history or not a Git worktree). "
        "Fetch the required history and inspect the checkout; do not bypass "
        f"the guard. Git: {result.stderr.strip()}"
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--require-ancestry", action="store_true",
        help="Reject commits that do not descend from the integrated UI baseline",
    )
    args = parser.parse_args()
    failures = verify_sources()
    if args.require_ancestry:
        failures.extend(verify_ancestry())
    for failure in failures:
        print(f"FAIL: {failure}", file=sys.stderr)
    if failures:
        return 1
    print("PASS: current three-tab Android UI, Plan Builder and privacy/export source checks")
    if args.require_ancestry:
        print(f"PASS: HEAD descends from {REQUIRED_BASELINE}")
    print("NOTE: source checks do not replace Gradle, Logcat or device visual QA")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
