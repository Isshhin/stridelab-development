# WS3-D failure triage

All 36 original failure points were outdated test flows. No original case was dismissed as an environment limitation or left ambiguous. Product defects discovered by new checks are listed separately in the acceptance report. Raw original errors, migration reasons and per-case details: evidence/ws3-E-36-case-triage.json.

| Case | Original file | Original test | Classification and disposition |
|---:|---|---|---|
| 1 | p3c-workout.spec.js | W9 calisthenics: bodyweight defaults, edit added load, finish twice never changes any stored skill stage | Outdated workflow; migrated and rerun |
| 2 | part1.spec.js | calisthenics-only plan from the default sample: no crash, saved, survives reload | Outdated workflow; migrated and rerun |
| 3 | part1.spec.js | running goal can be changed and toggled; selection is exposed with aria-pressed | Outdated workflow; migrated and rerun |
| 4 | part1.spec.js | strength + calisthenics added to a running plan: steps 2-4 render with every discipline | Outdated workflow; migrated and rerun |
| 5 | part1.spec.js | starting a plan from a strength-only sample then adding running does not crash | Outdated workflow; migrated and rerun |
| 6 | part1.spec.js | available days: long-run day is single-choice and always one of the available days | Outdated workflow; migrated and rerun |
| 7 | part1.spec.js | inspecting and rescheduling a previewed workout; Next week is scoped to the wizard dialog | Outdated workflow; migrated and rerun |
| 8 | part1.spec.js | finishing a calisthenics workout leaves every stored progression stage unchanged | Outdated workflow; migrated and rerun |
| 9 | part3a.spec.js | all sample wizard defaults are finite; actual schedule stepper increments | Outdated workflow; migrated and rerun |
| 10 | part3a.spec.js | 360px 100% navigation, dates, fullscreen stacking and running controls | Outdated workflow; migrated and rerun |
| 11 | part3a.spec.js | 360px 200% navigation, dates, fullscreen stacking and running controls | Outdated workflow; migrated and rerun |
| 12 | part3a.spec.js | 390px 100% navigation, dates, fullscreen stacking and running controls | Outdated workflow; migrated and rerun |
| 13 | part3a.spec.js | 390px 200% navigation, dates, fullscreen stacking and running controls | Outdated workflow; migrated and rerun |
| 14 | part3a.spec.js | 430px 100% navigation, dates, fullscreen stacking and running controls | Outdated workflow; migrated and rerun |
| 15 | part3a.spec.js | 430px 200% navigation, dates, fullscreen stacking and running controls | Outdated workflow; migrated and rerun |
| 16 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 360px 100% | Outdated workflow; migrated and rerun |
| 17 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 360px 150% | Outdated workflow; migrated and rerun |
| 18 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 360px 200% | Outdated workflow; migrated and rerun |
| 19 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 390px 100% | Outdated workflow; migrated and rerun |
| 20 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 390px 150% | Outdated workflow; migrated and rerun |
| 21 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 390px 200% | Outdated workflow; migrated and rerun |
| 22 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 430px 100% | Outdated workflow; migrated and rerun |
| 23 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 430px 150% | Outdated workflow; migrated and rerun |
| 24 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 430px 200% | Outdated workflow; migrated and rerun |
| 25 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 768px 100% | Outdated workflow; migrated and rerun |
| 26 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 768px 150% | Outdated workflow; migrated and rerun |
| 27 | phase4b3-final-refinement.spec.js | wizard toast never covers header row, progress bar or Continue at 768px 200% | Outdated workflow; migrated and rerun |
| 28 | phase4b3-mobile-refinement.spec.js | sample athlete presets: scrollbar hidden, swipe/scroll preserved, next option partially visible | Outdated workflow; migrated and rerun |
| 29 | phase4b3-mobile-refinement.spec.js | start date: scrollbar hidden, scroll and keyboard reach preserved, selection still works | Outdated workflow; migrated and rerun |
| 30 | phase4b3-mobile-refinement.spec.js | available days: Monday to Sunday in seven columns without scrolling at 360px | Outdated workflow; migrated and rerun |
| 31 | phase4b3-mobile-refinement.spec.js | available days: Monday to Sunday in seven columns without scrolling at 375px | Outdated workflow; migrated and rerun |
| 32 | phase4b3-mobile-refinement.spec.js | available days: Monday to Sunday in seven columns without scrolling at 390px | Outdated workflow; migrated and rerun |
| 33 | phase4b3-mobile-refinement.spec.js | available days: Monday to Sunday in seven columns without scrolling at 430px | Outdated workflow; migrated and rerun |
| 34 | phase4b3-mobile-refinement.spec.js | available days: Monday to Sunday in seven columns without scrolling at 768px | Outdated workflow; migrated and rerun |
| 35 | phase4b3-mobile-refinement.spec.js | available days wrap instead of scrolling at 320px and at large text | Outdated workflow; migrated and rerun |
| 36 | phase4b3-mobile-refinement.spec.js | day pickers are keyboard operable, announce state, keep a visible unclipped focus ring and long-run/quality rows do not scroll | Outdated workflow; migrated and rerun |