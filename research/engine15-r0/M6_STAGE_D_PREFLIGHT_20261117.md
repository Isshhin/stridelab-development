# M6 Stage D — independent multiweek periodization review (preflight)

Frozen 2026-11-17, before implementation. Stacked on M6C PR #119, which passed the selected Q3 regression suites (`BUILD SUCCESSFUL in 27s`). Shadow-only, NOT a new workout generator or production admission.

## Scope
Read-only, deterministic multiweek AUDIT of the independent M3→M4B→M5A/B/C→M6A/B/C pipeline. Recompute M6C from the original M4B result and supplied M6C caller input. Do not call Engine1, Engine15.generate, existing PlannedSession or mutate any existing workout/calendar/approval. Do NOT invent a second mileage progression, new quality dose, physiological adaptation, individual periodization policy, actual fitness or recovery assurance.

## Prespecified invariants
1. Every M6C calendar week must correspond exactly to its M4B week (1-based index, type and budget); every scheduled EASY/LONG run must be in that week, positive, typed and sum to its M4B budget. Every weekly value must be in 100m units, with no hidden carryover or catch-up.
2. No numeric week may exceed M4B's retained existing-policy ceiling, provisional session exposure cap, returning ceiling, or recovery ceiling where present. Every fourth week must be a RECOVERY week and no quality opportunity may fall there; loading weeks must match M4B's LOADING type.
3. Recovery week budget cannot exceed 80% of the preceding proposed weekly budget (100m tolerance). The existing M5A recovery long-run continuity bound must remain unchanged. This is a cap, not proof of safe deload or athlete recovery.
4. Attach interpretable week phase tags BUILD_1/BUILD_2/BUILD_3/RECOVERY to the existing workload pattern. Phase tags report what M4B already did: they do NOT independently prescribe a new load, progression or periodization strategy.
5. Carry original typed M6C candidate, its quality opportunities, external sessions and dates through unchanged. An output report contains metrics (budget, long run, slot count, quality opportunity count, target shortfall, projected total kilometres), all derived from existing inputs. No new sessions, dates or distances.
6. Provisional baseline reviewAfterCompletedWeek must be surfaced as an athlete-evidence reassessment checkpoint; after this week later projections remain unverified, not automatically approved or adapted. For returning runners M6B quality holds remain; time-only/unknown/legacy/hold states defer upstream.
7. When quality opportunities exist, only their dates/opportunity counts enter the report. Timed quality actual km, intensity, source authenticity, week-specific readiness and execution remain unknown. No arithmetic-only distance claim can upgrade this status.
8. Target gap and peak must be copied from M4B and independently compared with the same original weekly budgets. Do not claim 80 km delivered from 78.7 km *proposed* peak. A target reached by budget still does NOT imply executed mileage.
9. Result flags must remain false: `verifiedAthleteResponse`, `qualityIntensityAuthenticated`, `actualDistanceVerified`, `timeBudgetValidated`, `productionReady`, `executable`, `authorizedForProduction`; `allFutureWeeksProvisional=true`.

## Acceptance scenarios
- Eight-week six-run 75→80 km week budgets [67.5,71.2,75,60,71.2,75,78.7,62.9]. Phase tags BUILD_1/2/3/RECOVERY repeated; deload caps at weeks 4/8; peak 78.7, target gap 1.3 km, no invented 80 km claim. Two-week self-reported review checkpoint.
- Optional reviewed Tue quality opportunities W1/W2 with Thu upper strength and Sun long: opportunity counts 1,1,0,...; all date/volume/output objects unchanged. Never convert timed quality to km.
- Returning 40→50, four running days, no quality: retain return ceiling and reviewAfterCompletedWeek=2; no automatic quality promotion.
- Two-day 20→25 first week, no distinguished long: retain two EASY runs, exact 18 km.
- Missing external calendar assertion, unknown baseline, recovery hold, blocked long-run and tampered M4B: defer with upstream reason.
- Deterministic replay, 4- and 8-week cases and recovery boundaries; non-executable flags always false.

## Gate
Local Q3 full selected suite M3/M4A/M4B/M5A/B/C/M6A/B/C/D/Q2/Q3/R0 must pass; test-only acceptance is NOT production approval. Next: wider multiweek synthetic counterexample matrix and full independent pipeline acceptance, then source/dose authority and emulator tests.
