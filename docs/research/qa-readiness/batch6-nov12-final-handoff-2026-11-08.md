# StrideLab — Final November 12 Validation and QA Handoff (Batch 6)

Prepared 2026-11-08 (Asia/Manila); target execution **after the usage reset on November 12, 2026**, only when the owner explicitly starts an authorized session.
Status: **PREPARATION COMPLETE. NO NEW QA EXECUTION OR AUTO-MERGE AUTHORIZED.**

## 0. Read first, in this exact order

1. Repository `AGENTS.md` **at the actual target branch HEAD**, not a cached copy. Standing rule: Codex edits source only; Android Studio builds, emulators and owner visual acceptance belong to the owner unless the owner gives a specific exception. Do not create/switch/clean the owner's worktree without consent.
2. `docs/research/qa-readiness/batch1-foundation-consolidation-2026-11-08.md`: branch ancestry, separation, known blockers.
3. `docs/research/qa-readiness/batch2-engine2-synthetic-contract-freeze-2026-11-08.md`: the 12 frozen synthetic invariants; freeze is for candidate QA **not** production approval.
4. `docs/research/qa-readiness/batch3-android-approved-ux-defect-reconciliation-2026-11-08.md`: 17 Android UI and reliability reconciliation items and approved visual source.
5. `docs/research/qa-readiness/batch4-engine-android-qa-case-matrix-2026-11-08.md`: 22 E, 28 A, 6 P cases, each with priority and expected outcome.
6. `docs/research/qa-readiness/batch5-validation-execution-playbook-2026-11-08.md`: exact test commands, owner roles, signer safety, CI troubleshooting and report requirements.
7. **Data-entry file:** `docs/research/qa-readiness/nov12-qa-evidence-ledger-template.csv`. Contains 56 unique cases and initializes every status to NOT_RUN. Update a copied ledger with real evidence; keep the template intact.

The exact creation commit of this final handoff is in this Git branch's history. If a later source HEAD changes, **update this handoff’s tested-candidate refs before doing any execution**.

## 1. Pinned candidate refs — never confuse the three

| Scope | Branch / immutable SHA | Permitted action |
|---|---|---|
| Research candidate | `research/p2x-integrity-wave4-20261108` / `6142d7a8f8976f9ea4647850ff9e6ea51ff289df` | Research-only synthetic conformance check at exact SHA; no private data or plan writes |
| Android candidate | `fix/plan-builder-training-wave12-20261108` / `f1a0a6ce735f3f62b9a45e8a9253e7ded03da921` | Owner-authorized unit, emulator, visual, offline/persistence, and safe integration checks |
| Preparation branch | `research/qa-readiness-batches56-20261108` | Documentation/QA templates only; **never** infer tests were run on this later SHA |
| Frozen P2-F/Engine 2.x research PR #34 | `research/runna-garmin-coaching-20261102` / `5cd9b785d72d8f72914c32dc3f4f7d987b21a86e` | Keep OPEN, DRAFT, UNMERGED; no forced sync or edits |

Historical evidence: **481/481** synthetic tests twice on hosted author-side Python (seeds 7, 213); exact-head GitHub-hosted P2-X CI remains **BLOCKED before test steps**. Android exact-head Wave 1–2 Actions [#42486561531](https://github.com/Isshhin/stridelab-development/actions/runs/42486561531) and [#42486561683](https://github.com/Isshhin/stridelab-development/actions/runs/42486561683) passed on the user's existing Windows self-hosted runner, not the owner's physical Android handset or all 56 new scenarios.

**P2-F status correction:** latest bounded historical-key-role review recorded `P2F_CUSTODY_AUTHORITY_V7_DESIGN_ACCEPTABLE_FOR_PREFLIGHT_AUTHORIZATION_REVIEW`. This authorizes a **separate preflight authorization review only**, not collector execution, TPM/TBS, operational key use, private source admission, a pilot, or Gate 2. Do not repeat the superseded v1 protocol as normative.

## 2. Recommended execution sequence and gates

**Gate 0 / state safety (mandatory):** Verify active repository/remote SHA, clean or preserved worktree, agent/owner authority, test device isolation, package/certificate compatibility and no concurrent runner jobs. Preserve old user data. If conflicting head or signer => **BLOCKED** until reconciled, not a silent reset.

**Gate 1 / Engine 2.x:** From a dedicated exact-head research checkout, execute the **ten** targeted Python modules under two hash seeds and capture logs (see Batch 5). Check full source blob hashes and sample adversarial E01–E22. Do not rely only on the older GitHub research workflow, which checks eight modules. P2-X is a **synthetic contract**, not an approved coach. Independently review the pinned candidate using the existing review packet.

**Gate 2 / GitHub Actions scheduling blocker:** Use permitted repository-owner UI/settings to obtain the actual rejection reason for [#42482594419](https://github.com/Isshhin/stridelab-development/actions/runs/42482594419). Check Actions budget/billing/policy *without modifying spending/permissions*. Test hosted Linux only when authorized. If no hosted runner starts, record **BLOCKED** with run/job URL and zero executed steps, no fake CI PASS. **Never reroute to user's Windows PC.** This label is *QA execution gate 2*, not product **P2-F Gate 2**, which remains CLOSED.

**Gate 3 / Android automated QA:** Separate Android checkout at exact SHA. Owner runs Android Studio/Gradle with JDK 17, Gradle wrapper 8.13, SDK 36 and API 35 isolated emulator. Run focused JVM + debug APK build, instrumentation APK compile, then the seven targeted classes listed in Batch 5. If owner asks Codex to run checks, require explicit owner authorization overriding default AGENTS.md and isolate runner from sensitive workstation. Existing workflow's Windows runner use in historical CI is not authorization to dispatch it now.

**Gate 4 / app-specific P0:** Execute A01/A03/A04/A06/A08/A09/A10/A12/A15/A16/A20/A23/A24/A25 and P04/P05/P06, capture offline reopen and screenshots. Pay special attention to: approved four-tab dock vs implemented three-tab dock; generated plan goal/quality consistency; Saturday long run / unavailable Thursday constraints; thumbnail identity on crowded Training days; large FIT detail opening; draggable map/sheet gestures; accurate GPS/no-GPS state; owner-scope persistence and signer-safe upgrades.

**Gate 5 / P1/P2 and performance:** Perform the remaining A/P cases in priority order, with actual frame/memory measurements and a stable baseline rather than invented frame or memory thresholds. Only test provider interaction when separately authorized, with no secrets in capture. Never reconnect privacy cuts or invent route samples to hide UI defects.

**Gate 6 / follow-up defects:** Fail-first reproducible defect -> smallest isolated code fix -> targeted new test -> full related regression -> exact new SHA evidence -> owner acceptance. Do not automatically merge branches, change P2-F v7, activate Engine 2.x, or release a new APK.

## 3. Explicit QA data / authority restrictions

- Use invented synthetic plans, activities, FIT/GPX fixtures and disposable test identities. Real Garmin, athlete or participant records are **not** approved merely by having a connector, app login or device access.
- No use of Personal API keys, passwords, signing material, session tokens or sensitive payloads in chat, GitHub docs, logs or artifacts. Use sanitized metadata and source hashes.
- Never uninstall, clear application data, swap signing certificates, reset the database, migrate accounts across owners, or alter the user's existing active app to force a pass.
- No preflight collector, target-workstation inspection, TPM/TBS operations, private-data admission, or operational authority execution under the P2-F documentation.
- **No production Engine 2.x or Gate 2 authorization**, irrespective of synthetic result.
- An external independent conformance or security verdict is a separate gate. ChatGPT/Codex/Work self-review is NOT a staffed independent acceptance or human ethical approval.

## 4. QA outcome accounting

Before running a case, use the copied 56-row CSV. The allowed statuses are `PASS`, `FAIL`, `BLOCKED`, `NOT_RUN`, `NEEDS_REVIEW`. Every row needs an actual SHA and evidence link before PASS; each blocked row requires the precise missing authority/dependency. End-of-day totals must sum to **56**.

Aggregate report structure:

```text
STRIDELAB NOV 12 QA — EVIDENCE CHECKPOINT
Repository: Isshhin/stridelab-development
Engine candidate SHA: <exact SHA>
Android candidate SHA: <exact SHA>
Preparation docs SHA: <exact SHA>
PR #34 status: OPEN / DRAFT / UNMERGED (verify)
Owner authorization and execution environment: <specific>

E cases (22): PASS __ FAIL __ BLOCKED __ NOT_RUN __ NEEDS_REVIEW __
A cases (28): PASS __ FAIL __ BLOCKED __ NOT_RUN __ NEEDS_REVIEW __
P cases (6):  PASS __ FAIL __ BLOCKED __ NOT_RUN __ NEEDS_REVIEW __
TOTAL (56):    PASS __ FAIL __ BLOCKED __ NOT_RUN __ NEEDS_REVIEW __

Research: Python version, seeds, exact blob hashes, test totals, logs
Hosted CI: run/job URL, runner name, completed steps, rejection if any
Android: Gradle version, API/AVD, target SHA, test reports, screenshots
Offline/persistence: source IDs and counts, privacy-safe evidence only
Signing gate: package/version/certificate metadata and stop decision
P0 failures: ID, expected/actual, minimal repro, isolated branch, next action
Unknowns: distinct CI vs test vs independent approval blockers
Decision: INTERNAL_QA_<PASS|FAIL|PARTIAL|BLOCKED> / GATE2_CLOSED
Next authorized action: <one specific task>
```

Mark the overall internal QA PASS only if all required in-scope executed cases pass; if critical cases remain BLOCKED or NOT_RUN, call the report PARTIAL/BLOCKED. No automatic release or real-data admission follows any internal result.

## 5. Reusable task prompt for an authorized November 12 agent

**User may paste the below into a NEW Work/Codex conversation when ready. This handoff does not itself grant the explicit authorization to run local Android tests.**

```text
TASK: StrideLab November 12 QA — verify exact heads and execute only within my explicitly authorized environment.

Repository: Isshhin/stridelab-development.
Read AGENTS.md first from the checked-out branch. Honor its persistent constraints: Codex is code-edit-only by default; the owner runs Android Studio builds and UI acceptance unless I explicitly authorize an exception. Do not access or set up my Windows PC or dispatch self-hosted workflows without explicit approval.

First read these exact docs from research/qa-readiness-batches56-20261108:
- docs/research/qa-readiness/batch1-foundation-consolidation-2026-11-08.md
- docs/research/qa-readiness/batch2-engine2-synthetic-contract-freeze-2026-11-08.md
- docs/research/qa-readiness/batch3-android-approved-ux-defect-reconciliation-2026-11-08.md
- docs/research/qa-readiness/batch4-engine-android-qa-case-matrix-2026-11-08.md
- docs/research/qa-readiness/batch5-validation-execution-playbook-2026-11-08.md
- docs/research/qa-readiness/batch6-nov12-final-handoff-2026-11-08.md
- docs/research/qa-readiness/nov12-qa-evidence-ledger-template.csv

Pinned candidate research SHA: 6142d7a8f8976f9ea4647850ff9e6ea51ff289df
Pinned candidate Android SHA: f1a0a6ce735f3f62b9a45e8a9253e7ded03da921
Original frozen PR #34 head: 5cd9b785d72d8f72914c32dc3f4f7d987b21a86e (must remain OPEN/DRAFT/UNMERGED).

GOAL: Reproduce and qualify the existing synthetic research and Android app behavior, not introduce new features. Inspect remote/local branch and worktree state before testing. If heads changed, report the difference and rebase nothing automatically. Run only tests allowed in this session; keep use of GitHub-hosted CI separate from the owner's PC. Prior hosted research CI failed before executing steps, root cause unknown; do not treat the 481 author-side tests as exact-head GitHub CI evidence. Do not call the two Android historical CI successes phone or exhaustive QA acceptance.

Perform the authorized checks in the Batch 5 sequence, prioritizing P0 cases. Fill a COPY of the 56-row CSV with PASS/FAIL/BLOCKED/NOT_RUN/NEEDS_REVIEW, real evidence URLs, exact SHA and reproducible findings. Report total accounting, P0 stop decisions, any independent-review/billing/runner authority gaps, and next authorized actions.

Strict protections: no self-hosted PC dispatch or configuration; no credential or personal activity disclosure; no clear-data/uninstall or signer bypass; no guessed GPS data; no changes to Engine 1.5, original PR #34, or P2-F normative authority; no Gate 2 activation, automated plan writes, branch merge or production release.

If a code defect is verified, create a minimal isolated correction with a fail-first regression and stop at the owner's requested edit/test boundary. Never manufacture a PASS; label unavailable evidence BLOCKED. Give me exact commits, paths, outcome counts and evidence links.
```

## 6. Completion statement

The six preparation batches are a **documentation/specification package**. They reduce what must be decided during validation but do **not** guarantee QA-only work: critical bugs uncovered on November 12 may require a new implementation and regression cycle.

**Batch 6 verdict:** `B6_NOV12_EXECUTION_HANDOFF_PACKAGED_QA_NOT_STARTED`.
