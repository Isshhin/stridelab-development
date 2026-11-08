# StrideLab Pre-QA Batch 4 — Synthetic + Android Acceptance Test Matrix

Date: 2026-11-08 (Asia/Manila)
Working branch: research/qa-readiness-batches34-20261108
Source specs: Batch 1 branch inventory; Batch 2 synthetic contract freeze; Batch 3 Android current-implementation and approved-UX reconciliation.
Status: TEST PLAN ONLY. Every scenario below is NOT_RUN in this Batch 4. Historical successful CI is separate evidence, never auto-counted as a pass of these exact scenarios.

## Candidate pins and execution constraints

Engine 2.x synthetic implementation candidate: 6142d7a8f8976f9ea4647850ff9e6ea51ff289df.
Android Plan Builder/Training candidate: f1a0a6ce735f3f62b9a45e8a9253e7ded03da921.
Research frozen PR #34: 5cd9b785d72d8f72914c32dc3f4f7d987b21a86e — OPEN / DRAFT / UNMERGED. Never merge or edit in QA prep.
Historical Engine 2.x author-side combined test baseline: 481/481 on two distinct hash seeds; NOT independently verified exact-head GitHub CI.
Historical Android exact-head successful jobs: 42486561531 (Android) and 42486561683 (Windows emulator). Does not establish user phone acceptance or exact scenarios below.

No private provider keys, participant data, unapproved real FIT, token logs, production signing changes, personal-PC runner setup, destructive data reset or unauthorized P2-F preflight activity. Use invented synthetic fixtures or an isolated authorized emulator and separately approved credentials only when permitted. Capture no secrets in logs/artifacts.

## A. Engine 2.x synthetic contract cases

Every synthetic case is a NEW QA ACCEPTANCE EXPECTATION, not newly executed evidence. Suites should use existing tests first and then independent, reviewer-authored probes. The expected behavior is about contract safety, not coaching efficacy.

| ID | P | Synthetic setup / mutation | Expected result and assertion |
|---|---|---|---|
| E01 | P0 | Complete four-track invented snapshots, same athlete/plan/version/opportunity, valid times and complete ledger. | Deterministic synthetic consistency; source remains NOT_AUTHENTICATED, can_write_plan=false, gate2=CLOSED; input unchanged. |
| E02 | P0 | Swap one P2-C athlete ID without changing others. | Reject cross-scope / manifest mismatch. No plan action returned. |
| E03 | P0 | Use two distinct plan IDs with same numeric plan version in P2-D vs P2-E. | Reject cross-plan identity; version equality alone never permits join. |
| E04 | P0 | Claim migrated native P2-C plan ID when native snapshot has none. | Reject unsupported identity claim; copying synthetic ID alone never changes authority. |
| E05 | P1 | Duplicate identical native record with same event ID in each of P2-A/C/D/E. | Reject duplicate identical native event; no silent collapse. |
| E06 | P1 | Duplicate native record ID with conflicting value or source revision. | Reject conflicting native ID. |
| E07 | P1 | Duplicate planned session/opportunity ID including a non-target roster member. | Reject ambiguous roster rather than existentially matching target. |
| E08 | P0 | Change a bound event payload and leave manifest/ledger digest unchanged. | Reject native payload digest mismatch, no success on two matching caller claims. |
| E09 | P0 | Remove three of four eligible bindings while keeping source events available. | Defer; explicitly count three unbound eligible events; never call set complete. |
| E10 | P1 | Add unbound invalid native event, available before cutoff. | Defer with unverified/missing evidence reason; not interpreted as skipped workout. |
| E11 | P1 | Add future source event or later upload to replay of earlier as-of cutoff. | Exclude from historical review without retroactively adding evidence; late event may invalidate later receipt. |
| E12 | P0 | Backdate availability before observed time; try timezone-free or invalid timestamp. | Reject; never infer local time or rewrite source chronology. |
| E13 | P0 | Remove correction, withdrawal or source-revocation list from ledger projection. | Reject schema; absent list does not equal explicitly empty list. |
| E14 | P0 | Withdraw/revoke an active source and attempt to reuse the previous review receipt. | Defer/STALE receipt; can_write_plan=false; no replay approval. |
| E15 | P0 | Add extra active ledger record absent from complete native/binding set. | Defer; never report fully reconciled state. |
| E16 | P0 | Inject future_outcome, outcome, future_performance or best_policy into decision-time bundle. | Reject forbidden decision-time label; separate holdout outcomes from inputs. |
| E17 | P1 | Change plan revision, policy version or ledger tip after issuing shadow receipt. | Receipt invalidated; never yields permission to edit a plan. |
| E18 | P0 | Construct fully forged but mutually matching caller manifest and source digests. | May achieve synthetic structural consistency, but MUST remain unauthenticated with no approval, safety authority or plan writes. Report the residual trust gap. |
| E19 | P0 | Attempt to invoke or mimic P2-F witness/gateway authorization from P2-X fixture. | No operational custody or authenticated authority; fail closed. Gate 2 remains CLOSED. |
| E20 | P1 | Change dictionary insertion order, evidence-binding order, hash seed and repeat replay. | Deterministic schema-consistent result and reason sets; no mutation of original fixtures. |
| E21 | P1 | Run all combined synthetic modules at pinned implementation SHA under hash seeds 7 and 213. | Same test census and zero failures; collect executable source SHA, environment, stdout/stderr and full logs. Historical count 481 is a baseline, not a pass threshold if tests changed. |
| E22 | P0 | Run the exact-head suite under permitted GitHub-hosted Linux, record runner/steps. | If job never starts, report BLOCKED and exact reason if available; do not call it FAIL of software tests or reroute to self-hosted PC. |

## B. Android functional and Plan Builder cases

Use isolated/emulator account and invented plan inputs. UI test evidence requires screenshot plus exact branch SHA, display metrics, steps, expected/actual and state snapshot. Never make data-preserving upgrades by clearing app storage.

| ID | P | Scenario / stimulus | Expected result and evidence |
|---|---|---|---|
| A01 | P0 | Enter Plan Builder fresh; navigate seven stages forward/back repeatedly. | Seven-stage progress and fixed CTA correct; selections survive Back, no fabricated generated workouts before saving actual draft. |
| A02 | P1 | Verify approved Plan Builder palette on two font/device profiles. | AMOLED canvas #0B0B0B, lime #DBF351, readable text on selected cards, all controls accessible; no clipping at 360×800dp/130% or 412×950dp/150%. |
| A03 | P0 | Configure valid supported Running 5K goal and generate draft. | No stale unrelated error banner; actual PlanSnapshot generated and displayed; explicit approval required before plan is active. |
| A04 | P0 | Configure unsupported/missing goal; correct it and retry. | Clear contextual error with no phantom draft; corrective input removes/updates stale status, retry succeeds only with valid request. |
| A05 | P1 | Toggle Coach decides and Choose my days multiple times at six weekly sessions. | Both options remain discoverable; generated quality count is explainable under engine safety/volume constraints, not silently forced. |
| A06 | P0 | Set Saturday preferred long run, Thursday unavailable, Tue/Wed adaptable, Sunday easy; attempt conflicts. | Availability and protected-day rules respected; conflict explained, no workout scheduled on blocked day without explicit permitted override. |
| A07 | P1 | Generate weekly volume below/around quality eligibility cap; include deload/return-to-running. | Engine 1.5 quality eligibility/limits enforced with truthful requested-versus-generated explanation. No unsafe extra quality session to match a UI toggle. |
| A08 | P0 | Save, approve, force-stop/reopen offline, inspect plan and record a workout. | Plan and workout preserved locally; no hidden cloud sign-in prerequisite; no duplicate generated sessions. |
| A09 | P0 | Explicitly select Engine 1.0 rollback for a NEW draft, then return to Engine 1.5. | Controlled fallback works; existing persisted plans are not rewritten or treated as foreign; stale feedback is rejected. |
| A10 | P1 | Navigate Today/Training/Activity/More from actual build and return with Back. | Document discrepancy between approved four-tab shell and source's current three visible primary tabs; confirm actual Activity history access and route ownership; fail approved-parity only after grounded visual capture. |
| A11 | P2 | Today past/today/future date, running/strength/calisthenics transitions and multi-recorded runs. | Today's workout only on today; historical recorded cards truthful; conditional fade only on discipline switch, no scrolling sign-out hero. |
| A12 | P0 | Training day with many recorded activities and GPS/non-GPS sources; scroll fast away/back. | Correct record cards and thumbnails, no stale image/id crossover, no accidental extra mileage, no runaway generation; record thumbnail requests and frame traces. |
| A13 | P1 | Cold-start Today and Training with already cached thumbnail, offline. | Preview and card become available consistently; absent preview has honest bounded loading; no network needed for cached asset. |
| A14 | P1 | Corrupt cached thumbnail and replace source FIT detail; reopen. | Recompute from valid local source when allowed, no cross-account leakage, avoid stale fingerprint. |
| A15 | P0 | Open known long Garmin recording (>2 MiB detail, ideally 12k–15k source samples) and reopen after force-stop. | No CursorWindow/OOM, full sample count/laps/HR intact, display geometry may downsample without changing analysis or raw recording. |
| A16 | P0 | Open Activity Detail sheet, drag between map/intermediate/expanded, scroll last section, Back/collapse. | Map remains pan/zoom independent; sheet can reveal map fully, expand to safe top without clipping; no over-scroll past final section, Back collapses first. |
| A17 | P1 | Render pace, HR, elevation, cadence, zone charts and splits with full/partial/missing streams. | Truthful source statistics and zones, readable axes/background/mean lines; no invented sample values or crashes; compare summaries to FIT records. |
| A18 | P1 | Test compact splits and analyses, Overview/Splits Analysis/section headers at large font scale. | Compact rows, consistent spacing, correct labels and analyses only when evidence supports them. |
| A19 | P1 | Change map zoom/drag on dark style, with pause/resume route and privacy cut. | Streets/parks readable, attribution visible, display line may join real pause endpoints, does not fabricate track coordinates or reconnect privacy cuts/antimeridian. |
| A20 | P1 | Open one GPS workout and one true indoor no-GPS workout. | GPS route shown from actual fixes; indoor workout states no GPS accurately; summary-only Garmin recording states "not loaded yet", not fabricated absent GPS. |
| A21 | P0 | Garmin primary live sync when separately authorized: FIT success, individual 404, rate-limit, expired credential, manual retry. | 404 for one record doesn't abort all; distinguish auth/network/rate limit/storage; no credentials in logs; summaries survive detail failures. |
| A22 | P1 | Run repeated foreground Garmin backfill beyond 24 missing details and reopen offline. | Gradually persist source-valid details, no unbounded network loop; map/chart/thumbnail can load offline when previously saved. |
| A23 | P0 | Fresh install without network and without StrideLab account; create plan, log and favorite. | Local profile usable without login and survives force-stop; optional cloud backup off until explicit opt-in. |
| A24 | P0 | Connect/disconnect same cloud account and try different cloud owner. | Local records not lost on sign-out; no implicit cross-owner binding, restore remains insert-only and isolated. |
| A25 | P0 | Verify installed APK package/version/certificate before replacement. | If mismatch, STOP; do not uninstall, wipe app, or overwrite preserved owner data; use disposable emulator/profile. |
| A26 | P1 | Activity Detail duration stat, pace wording, source attribution, max/LTHR/HRR zones. | Approved text/metric distinction retained; Duration label-only, raw timer values unchanged; map attribution available. |
| A27 | P2 | Share Studio export with recorded source geometry and explicit privacy trim. | Export uses same privacy-safe route and genuine data, no leak of cropped endpoints; existing record not mutated. |
| A28 | P1 | Stress list with 40+ newest recordings, older pagination, 2+ records/day, cloud and Garmin duplicates. | Recent-first order, account isolation, reliable older pages, no duplicate total volume from display consolidation. |

## C. Performance, safety and evidence capture

| ID | P | Action | Objective acceptance / capture |
|---|---|---|---|
| P01 | P1 | Navigation repeated 30x Today/Training/Activity accessible/More. | Count screen hangs, dropped frames, composition work, state mismatches; record median/P95 frame time and device. Compare against same-build baseline; no invented budget. |
| P02 | P1 | Alternate short and long Activity Details (15k samples). | Capture peak memory, frame-time traces, crash logs and database reads; raw samples remain unchanged. |
| P03 | P1 | Rapid Training scroll with 100+ representative activities and varied thumbnails. | Measure cache hit/miss and decode/request count, no wrong preview or stale identity; preserve performance trace and screenshots. |
| P04 | P0 | Forced offline, terminated app, interrupted import/outbox/backup and account switch. | Atomic persistence, no cross-owner data, no lost completed plans/workouts; capture exact before/after IDs and row counts (not private payload). |
| P05 | P0 | Privacy-cut GPX/FIT source and Share export. | No route or preview reconnects removed privacy segment; fails closed if boundary untrusted; audit thumbnail/map/export separately. |
| P06 | P0 | Safe rollback, installation and source migration. | No destructive cleanup, no incompatible signer install, migration proof with complete local history and no silent cross-account transfer. |

## D. Evidence classification and severity

Every test line remains NOT_RUN until individually executed and supported by a test log or UI capture. Allowed statuses:
- PASS — exact recorded assertions passed on stated SHA/device; include evidence.
- FAIL — actual execution violates specified result; reproduce and triage. P0 stops affected promotion.
- BLOCKED — environment, runner, credential authorization, device, signing or consent prevented execution; not a software test failure and NEVER counted as PASS.
- NOT_RUN — deliberately not attempted.
- NEEDS_REVIEW — design intent conflicts or reliable oracle is absent; separate user-approved decision before asserting parity.

For crashes, collect Logcat FATAL EXCEPTION/native trace without secrets, exact application/build SHA, Android API, memory and steps. For visual QA capture current device screenshot, not outdated Figma alone. For Garmin do not synthesize an authenticated run to stand in for actual provider testing. Do not count 481 research synthetic tests as biological validity or 2 Android CI successes as exhaustive device QA.

## E. Nov 12 execution sequence (dry-run checklist, not executed here)

1. Read Batch 1–4 docs; inspect clean checkout heads of BOTH isolated research and Android branches, confirm PR #34 unchanged. Disposition mismatches before test.
2. Run Engine 2.x pinned synthetic suites on a permitted hosted Linux environment; collect full logs under two hash seeds. Independently review P2-X risk packet. If research workflow fails before starting, record BLOCKED and gather actual runner rejection. Never use user PC as fallback.
3. Build and run Android unit and instrumentation on isolated emulator, starting P0: A01, A03, A04, A06, A08, A09, A10, A12, A15, A16, A20, A23–25, P04–06.
4. Run P1/P2 UI, Garmin-with-authorization, fidelity and performance cases; record representative device sizes, font scale, offline conditions and baseline.
5. Apply only isolated, fail-first fixes to verified defects and retest impacted suites. No automatic merge, provider-data admission, production Engine 2.x activation or Gate 2.
6. Generate acceptance report with complete PASS/FAIL/BLOCKED/NOT_RUN counts and linked evidence for every row. An unexecuted row can never be silently marked green.

Batch 4 verdict: B4_EXACT_SCENARIO_TEST_MATRIX_PREPARED_NOT_EXECUTED.
