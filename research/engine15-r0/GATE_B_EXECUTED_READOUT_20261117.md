# Gate B — R0 executed evidence and authorization (2026-11-17)

## Evidence provenance and verdict

- Source: locally executed Engine15R0WorkloadLedgerTest on the combined Q2/Q3 + PR #109 candidate; user supplied BUILD SUCCESSFUL in 37s and PowerShell displays of the scenario summary, weekly ledger and selected rule-event CSV.
- **EXECUTED:** all focused synthetic R0 assertions passed, including diagnosed-plan parity and unchanged recovery-hold rejection. Full raw CSV files remain in the user's local worktree; these numeric extracts are transcribed from the supplied console output, not independently rerun here.
- **EXECUTED within synthetic fixture:** experienced-new-app-75-to-80, 6 days, eight weeks, 75 km reported, 80 km requested, 20 km longest run, one quality session, planning pace 360 s/km. This is not a real athlete record.
- **NOT EXECUTED HERE:** M3 baseline code and alternative M4/M5 generators. No after-change mileage comparison yet.

## R0 measured results — experienced 75 → 80 km

| Week | Engine 1 baseline km | Engine 1.5 allocated km | Final distance km | Q2 delta km | Basis |
|---|---:|---:|---:|---:|---|
| 1 | 71.100 | 67.500 | 62.283 | -5.217 | Planning-pace estimate |
| 2 | 74.900 | 71.200 | 64.673 | -6.527 | Planning-pace estimate |
| 3 | 78.100 | 75.000 | 67.633 | -7.367 | Planning-pace estimate |
| 4 | 65.300 | 54.100 | 54.100 | 0.000 | Exact distance targets, recovery |
| 5 | 85.700 | 71.200 | 64.783 | -6.417 | Planning-pace estimate |
| 6 | 85.700 | 75.000 | 67.773 | -7.227 | Planning-pace estimate |
| 7 | 85.700 | 78.700 | 70.533 | -8.167 | Planning-pace estimate |
| 8 | 68.500 | 56.400 | 56.400 | 0.000 | Exact distance targets, recovery |

**Week 7 executed stage results:** Engine 1.0 baseline 85.700 → Engine 1.5 ceiling 78.700 → session capacity 98.600 (not binding) → weighted allocation 78.700 → long share 78.700 → rounding 78.700 → final role recheck 78.700 → Q2 timed conversion 70.533 estimated → post-Q2 reconciliation 70.533. Estimated target gap 9.467 = 1.300 gap before Q2 + 8.167 estimate decrease at Q2.

**Interpretation constraint:** Q2 replaces fixed-distance warm-up/cool-down with timed steps and recalculates estimated distance at an assumed pace. The delta is an **executed change in estimated prescription**, not proof the athlete would run 8.167 km less; not authorization to force that distance into workouts.

**Other synthetic peak planned/estimated km:** 50→80 four-week build 47.033; 30→35 developing 31.500; beginner 0→20 unknown time-only across all eight weeks; returning 40→50 28.300; three-day 75→80 54.600; unverified-quality 75→80 70.533. Shortfall alone never establishes unsafe or unnecessary guardrails.

## Constraint decisions

| Question | Evidence level | Gate B verdict |
|---|---|---|
| Engine 1.0 baseline as Engine 1.5 volume input | EXECUTED boundary only; internal source factors not yet traced | M4 can design a single volume skeleton; current generator unchanged |
| Engine 1.5 weekly ceiling | EXECUTED binding | Review under M4; numerical relaxation **not approved** |
| Q2 conversion | EXECUTED estimated-volume delta | Review under M5, preserve mixed-unit estimates, no catch-up |
| 40% long-run share | EXECUTED no change for selected 75→80 profile | **No change** |
| 20% work share | EXECUTED no selected-profile reduction | **No change** |
| Q3 qualified history | No new athlete efficacy evidence | **Preserve unchanged** |
| Illness, pain, recovery hold | Focused synthetic rejection preserved | **Preserve unchanged** |
| Unknown vs zero, average vs peak | Existing request lacks provenance | **Approve M3 evidence metadata only** |

## Gate B authorization

**R0 diagnostics PASS for starting an isolated M3 data contract.** This does **not** approve changes to workout generation, safety bounds, weekly progression, automatic adaptation or approval behavior.

M3 must implement: distinct UNKNOWN / CONFIRMED_ZERO / SELF_REPORTED_AVERAGE / PEAK_WEEK_ONLY / RECORDED_PARTIAL / LEGACY_UNSPECIFIED; legacy zero is unknown; peak is never substituted for typical average; sparse history is never considered full-week coverage; only explicitly attested complete weeks from IndependentRunningHistory can establish a verified mean; self-report remains provisional; contradictions are visible to M4; optional fields roundtrip; unchanged generated sessions for identical training inputs.

M4 still requires a reusable volume-skeleton interface, versioned policy, prespecified numeric outcomes and before/after comparison against all profiles. M5 cannot inflate timed cool-downs or hard work to fill a target.

**Next evidence gate:** run the focused Engine15M3AthleteBaselineTest in the local combined candidate. The present branch has not yet produced an executed post-change test result.
