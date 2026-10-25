# StrideLab Phase 4D.2 — WS3-E stabilization and acceptance

Authoritative input: **StrideLab-WS3-D.zip**, SHA-256 2f4790b3b8ca7d627d83b877f80694ebb8b832d72e4c1fdca99b0d3d29f3f302. Historical A/B/C packages were not used. D's input archive CRC and every manifest payload hash were verified. E extends D's existing build sources; --upto 3 reproduces D and the complete build adds only the WS3 stabilization patch.

## Verdict and independently verified results
**PASS within the documented local Chromium regression scope.** Official Playwright: **221 passed / 0 failed / 0 skipped / 0 flaky**, retries disabled. Authoritative result: evidence/ws3-E-final-verified.json. Earlier runs and failure reproductions remain diagnostic history; their counts are not added to this result. All inherited test files remain in the official full run.

All available axe specs passed, including seven guided-wizard states. Six additional saved-plan views (week/month/overview at 390px normal text and 360px 200% text) have zero automatic violations, zero page errors and no document horizontal overflow. Axe retains incomplete color-contrast checks for gradients, pseudo-elements and obscured content. They require manual review and are not converted into passes or suppressed. This is not a claim of complete accessibility conformance. Physical devices, other browser engines and hosted synchronization were not tested in this local Chromium pass.

## Analysis of the 36 WS3-D failures
| Group | Original failures | Resolution |
|---|---:|---|
| Outdated tests | 36 | All migrated and rerun in the full official suite |
| Confirmed product defects at those original failure points | 0 | The old tests stopped before their intended behavior could be exercised |
| Environmental limitations at those failure points | 0 | No original failure was dismissed as an environment error |
| Ambiguous original failure points | 0 | Source selectors/navigation and migrated runs explained each case |

Files: part1 (7), part3a (7), p3c-workout (1), phase4b3-final-refinement (12), phase4b3-mobile-refinement (9). See TRIAGE-36.md and evidence/ws3-E-36-case-triage.json for every original title, error and migration reason.

Removed sample-athlete UI controls were replaced with the retained internal fixture API only in fixture tests. User flows select disciplines and visit each selected setup step, then Schedule → Review → Generate → Preview → Create. Goal/deselection assertions now distinguish exclusive goal choices from removing a discipline. Calendar selection, details and moves use the current accessible controls. Long-run-day tests assert the approved required-day protection, change the long-run day, then verify the old day can be removed. Seven-day layout tests retain all seven days, state, keyboard and 44px checks while allowing the approved narrow-screen wrapping. Workout checks verify every prescribed set instead of assuming a historical fixed three-set fixture. No test was removed, skipped, retried into a pass or given weaker error tolerances to hide a defect.

## Confirmed product defects found during stabilization
These were found by new checks, separately from the 36 obsolete failure points:

1. Creation ignored save() returning false and could publish unsaved plan state. Reproduced on the exact D HTML with a valid, noncolliding preview and injected quota error. E builds/validates a complete replacement, serializes it and performs one atomic localStorage write before publishing runtime state. Failure keeps the prior saved value, plan, date map, workout/exercise dictionaries and stages unchanged; an actionable inline alert keeps the wizard open for retry.
2. D's one-ID-per-date representation forced same-day rejection. E maps each date to a list of unique stable session IDs. Week/month detail and Overview expose every same-day workout. Regeneration from saved inputs/moves retains counts, content and Runner-Focused Strength adaptations. Moving one saved roommate keeps all others; saves, later edits, reloads and quota-error retries retain the full collection.
3. Legacy date-based moves were displayed after reload but lost by guided editing. Reproduced before the compatibility fix. Valid D-era move overrides are transactionally converted into per-session plan moves. Incomplete/unrecognized overrides or unavailable storage are kept unchanged and block unsafe editing/moving with an explanation.
4. Date targets at 360px were below 44px. Removing the calendar card's extra side padding preserves the seven-column calendar and restores 44px targets. Tests check 360/390/430px.
5. Calendar column headers had only hidden visible letters, and the month heading skipped a level. Axe found empty-table-header and heading-order violations. Added reader-visible full weekday names and corrected the heading level while preserving appearance.
6. The activity sheet's unrelated .xr flex styling overrode saved workout exercise rows; enlarged text became nearly vertical and run labels clipped. Visual review and a failing reflow check confirmed this. Scoped the hero rows, let metadata reflow below names, and allowed run segments to wrap/reflow without clipping. Normal card styling, colors and engine prescriptions are preserved.

New regression probes against exact D: 2 passed / 4 failed as expected (quota handling, legacy moves, 360px touch target, calendar axe failed; 390/430px target checks passed). Evidence: ws3-E-confirmed-on-D.json. Intermediate E compatibility and reflow failures are also retained. The final full run includes the corrected checks.

## Same-day transaction boundary
The durable source remains plan inputs plus per-session move overrides, marked calendarVersion 2. Derived date lists, workout definitions, exercises and stage updates are prepared without mutating live state. Unique IDs, complete counts, valid dates and training-week bounds are checked, and creation verifies that the regenerated sessions match the displayed preview. A single browser-origin localStorage.setItem writes the complete candidate; only success publishes the new runtime projection. No backend/server transaction is claimed. Invalid or stale previews and failed writes leave the previous plan intact. Calendar warnings remain visible; the engine and Runner-Focused Strength safety adapter are not weakened to allow additional load.

## Coverage
- Arbitrary starts: 84 start/configuration adapter cases plus the engine's 12 scenarios inside tests; these are not 96 additional Playwright cases.
- Weekly totals, preview arrows/keyboard, month boundaries, date limits, clearing date-keyed moves on a start change, manual markers and UI swaps.
- Same-day triple occupancy, stable ID uniqueness, exercise content and total minutes, save/edit/reload, separate agenda cards, month/Overview, individual same-name log identity, moving one roommate, and old scalar/date-based data.
- Storage failure with and without a prior plan, valid noncolliding write failure, retry, creation/build rollback, invalid duplicate preview and atomic state preservation.
- 360/390/430px calendar targets, normal/200% text, enlarged saved-card reflow, broader responsive suite through desktop widths, keyboard/focus and modal scroll locking.
- Existing Library/workout/run/map/state-recovery checks, available axe specs and Runner-Focused Strength compatibility remain in the full suite.

## Integrity
| Protected artifact | SHA-256 |
|---|---|
| Coaching-engine raw script bytes | 3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2 |
| Protected 4D.1 baseline | cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d |
| WS3-E runnable candidate | d5dd7a8da04646995d3e3f1b78f171207115cbf095b9cc7436b8b92bb1d657ec |

All 21 assets are byte-identical to D. Build assertions and tools/verify_ws3_integrity.py check protected raw bytes and inline-script syntax. SHA256-MANIFEST.json covers the package payload; the final ZIP is reopened, CRC-tested and every manifest hash verified. The adjacent .zip.sha256 covers the archive itself.

## Run and continue
Open **stridelab-afterglow-phase4d2-wip.html**, keeping assets beside it. The checkpoint4-wip.html is an inherited historical reference, not E's entry point. Rebuild: python tools/build_p4d2.py. Windows tests: npm.cmd ci in suite; npx.cmd playwright install chromium; set PROTO to the absolute runnable HTML path; npx.cmd playwright test. tools/run-ws3.ps1 runs the full official suite after rebuilding. tools/capture_ws3_e.cjs refreshes settled same-day screenshots and saved-plan axe evidence. No private credentials or installed node_modules are packaged.

See HANDOFF.md, CHANGELOG.md, CONTINUATION-NOTES.md, TRIAGE-36.md, evidence/ws3-E-changed-files.json and ws3-E-source.diff. Prior claimed 16/16 and other inherited reports remain historical; only the E results above establish this acceptance. No Workstream 4, Engine 2.0 or Android packaging was started.