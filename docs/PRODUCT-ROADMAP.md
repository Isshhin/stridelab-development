# StrideLab — Product and Engineering Roadmap

**Last updated:** 2026-10-27  
**Status:** Working product roadmap; proposed features are not claims of implemented functionality.  
**Distribution:** Private, personal-use Android alpha. No public release is planned.  
**Source of truth:** This document records *product scope, priorities, decisions, dependencies and acceptance criteria*. GitHub PRs, exact-head CI runs and source inspections are the source of truth for *implementation status*.

> **Immediate product priorities, in owner-defined order:** (1) Direct Garmin integration, (2) visual-first Android UI/UX reconstruction, (3) Unified Coaching Engine 2.0. Completing the already-started Android integration is a prerequisite, not a competing product priority.

## Product vision

Build one adaptive, offline-capable fitness application across **running, strength/gym, calisthenics and conditioning**. The user retains control of all approved training. The web frontend is maintenance-only; the native Android app is the development priority.

### Non-negotiable product rules

- **Visual-first usability.** Use meaningful GPS maps, charts, structured interval ribbons, exercise imagery, set progress and calendar indicators instead of large blocks of bare text. Never fabricate data to fill a visualization; use truthful, compact fallbacks.
- **User is the master of the plan.** The coach can assess recent activity and recovery and propose changes to an individual session, week or longer plan. **No proposed workout, mileage, intensity, day or goal change takes effect without explicit user approval.** Show original vs proposed, reasoning and evidence; allow approve, reject or postpone. No unexpected changes on workout day.
- **One unified training context.** Consider running, strength and calisthenics workloads together rather than generating conflicting standalone plans.
- **Offline-capable deterministic core.** Training-plan generation, local workout logging, drafts and essential calendar functions must work without an LLM or permanent internet connection. External synchronization can be deferred and retried.
- **Recorded vs prescribed remain distinct.** Never substitute planned pace, distance, reps or effort for actual measurements. Never mark a workout complete before the real record/confirmation is persisted. Never infer a missed workout solely from an absent or incomplete sync.
- **Real data and provenance.** Track provider identities, timestamps, units, available sensor streams, duplicates and sync freshness. A disconnected source does not mean zero training.
- **Preserve user data.** Verify signing compatibility and migrations before offering an APK update; do not uninstall the existing app to bypass a signing conflict.
- **Developer-only releases.** No public APKs, public coaching claims or premature Engine 2.0 launch.

## Milestones

These are **focus milestones**, not a commitment to a fixed delivery date. Architecture for later milestones may be prepared while Copilot implements the current one. Work should be split into small, independently verifiable increments.

| Milestone | Primary outcome | State as of 2026-10-27 | Gate for completion |
|---|---|---|---|
| **M0 — Stable native foundation** | Combine verified guided-workout and multi-session scheduling work without losing offline logs, Engine 1.0 or existing app data | **Integration undergoing review**; [draft PR #3](https://github.com/Isshhin/stridelab-development/pull/3); [multi-session draft PR #2](https://github.com/Isshhin/stridelab-development/pull/2) | Final exact-head CI, reliable emulator regression tests, data/signing review and a safe Android implementation baseline |
| **M1 — Direct Garmin data import** | Read personal Garmin activities and wellness data into normalized, cached StrideLab records | **Planned; not verified in app** | Authorized import of authentic activities/FIT and supported wellness records; secure credentials; deduplication, sync retry and offline reopening |
| **M2 — Visual-first Android usability** | Rebuild the existing UI around useful visual information and intuitive task flows | **C2 designs approved; implementation pending** | Daily workflows work on device with accessible charts/cards, no clipped controls, transparent save/sync states and no regression in existing features |
| **M3 — Plan-to-recorded-run linking and automation** | Associate Garmin/Intervals.icu-recorded runs with prescribed sessions | **Future engineering; linking capabilities must be source-audited** | Reliable link/unlink, unique identities, conservative automatic matches, confirmation for ambiguity, no duplicate completions |
| **M4 — Unified Coaching Engine 2.0 and plan goals** | Offline deterministic, coordinated training and approval-first adaptations across disciplines | **Architecture and feature planning** | Explainable plans; safe mileage/efficiency/strength progression; user-approved changes only; scenario and regression tests |
| **M5 — Defensible fitness intelligence** | Meaningful recorded performance, aerobic and strength trends, goal progress and activity explanations | **Partly conceptual; audit what exists** | Every insight grounded in stored evidence and comparable observations; unsupported estimates absent or explicitly qualified |
| **Continuous — Private-alpha hardening** | Trustworthy day-to-day personal use | **Always active** | Crash recovery, APK signing and upgrade safety, migrations, accessibility, performance, backup, CI and device acceptance |

### M0 — What must be preserved

- Verified guided player: prescribed target vs actual entry vs saved set, exercise-by-exercise flow, pause/resume and +15s rest, Review/Finish, local drafts, offline reopening and figure credits.
- Separate Engine 1.0 multi-session policy: multiple independent sessions on one day where permitted; recovery/time-budget/conflict checks; no accidental changes to legacy Engine 1.0 behavior.
- Maintain [PR #3](https://github.com/Isshhin/stridelab-development/pull/3) as an independent integration change until verified; do not merge draft branches automatically.
- A short baseline/source audit should answer actual status semantics, manual activity-linking capability, offline persistence and current Activity Detail parity. Findings override design assumptions.

### M1 — Garmin-first personal integration

This is a **single-user private-alpha integration first**, not a claim that commercial/multi-user Garmin access is approved.

1. **Access and scope gate:** Confirm Garmin Connect Personal API eligibility for the owner's account, the applicable terms and documented read-only endpoints. Do not attempt unauthorized endpoints or publish credentials.
2. **Credential lifecycle:** User provides credentials only through secure on-device/app setup; protect keys, obtain/renew short-lived tokens, handle expiration/revocation and allow disconnect. Never store tokens or keys in Git.
3. **Activity ingestion:** Fetch activity list/detail and documented FIT files as available; preserve original provider activity ID, timestamps, sport, distance/duration and measured streams/laps/route without assuming every record has them.
4. **Wellness ingestion:** Fetch supported sleep, HRV, resting HR and other supported data; preserve collection date, units, provenance and HRV measurement type when present.
5. **Normalized local store:** Cache data; incremental pagination/backfill; deduplicate imports and coexist with existing Intervals.icu data without dropping history.
6. **Recovery and UI:** Clear syncing/last-success/offline/reconnect-needed states; verify restart/retry, account isolation and zero duplicate imports.

**Official reference:** [Garmin Connect Developer Program overview](https://developer.garmin.com/gc-developer-program/overview/), [Personal API FAQ](https://developer.garmin.com/gc-developer-program/personal/personal-faq/), [Personal API endpoint reference](https://developer.garmin.com/gc-developer-program/personal/swagger-docs/). The Personal API is documented for personal, pull-only access; sending workouts to the watch is **a separate future feasibility study**, not part of M1.

### M2 — Visual-first UX, not just cosmetic changes

Approved design areas, to implement incrementally against the *actual integrated native source*:

- **Overview:** Today actions; truthful recorded-versus-planned seven-day volume visualization; discipline distribution; recent activity; dated raw wellness; compact missing-data and sync status.
- **Training & Calendar:** Week/Month/Plan navigation, clear date windows, independent same-day sessions, conflict-aware move actions, compact plan row, distinct Missed/Skipped with reasons.
- **Guided workout:** Per-exercise and per-set progress, accurate target/draft/recorded distinction, clear timer and persistent bottom actions; preserve working set and rest logic.
- **Workout Details:** Running shows real structured intervals when available and honest fallbacks; strength/calisthenics shows planned exercises and actual logged sets.
- **Recorded Activity Detail:** Preserve the approved **Strava-like** direction: automatic genuine GPS route when available, immersive map, accessible information panel, Overview/Splits/Charts/Intelligence and interactive linked highlights as data permits. First audit which interactions are implemented/partial/missing.
- **Shared design system:** Confirm actual Compose tokens, accessible text contrast, 48dp touch targets, large-text behavior, consistent labels. Lime is intentional brand/primary-action emphasis, not every informational highlight.

**Minimum alpha usability acceptance:** Open today's plan; understand its visual workout structure; complete or resume guided sets; sync/read real recorded running data; review activity details; continue independently through another session; reopen offline without losing confirmed values.

### M3 — Link planned running to actual Garmin/Intervals.icu activities

- Verify which manual linking and uniqueness guarantees **already exist** before replacing UI or writing a matching algorithm.
- Keep planned prescription immutable; link via normalized activity identity; transactional persistence; one recorded activity should not silently complete multiple scheduled workouts.
- Build deterministic candidate discovery using validated date/time, sport, duration, distance and structured workout information *only when present*.
- Auto-link only when a unique, sufficiently reliable candidate is established; uncertain or conflicting cases require explicit confirmation.
- Permit manual linking/unlinking and recovery from an incorrect association without deleting authentic imported activity history.
- Do not duplicate records imported via Garmin and Intervals.icu. Offline operation and retries must not duplicate completion.

### M4 — Unified Coaching Engine 2.0

**One coordinated engine** with a shared athlete profile/history, equipment and exercise capabilities, goal hierarchy, running workload, strength/calisthenics progression, conditioning, recovery context, rest days and long-run/quality constraints. The deterministic engine remains local-first; optional cloud AI explains and assists, never gates basic planning.

**First-class running plan goals:**

| Plan type | Goal and defining behavior |
|---|---|
| **Build weekly mileage** | User sets target km/week, chooses maintain vs gradual ramp vs recommended progression, preferred timeframe or flexible timeline. Use actual recent baseline, history, frequency, intensity, long-run distribution, other training and recovery; propose appropriately conservative growth, easier weeks and a stabilization period. No universal fixed 10% increase. |
| **Improve aerobic efficiency** | Improve pace at comparable easy heart rate/effort over time; compare reasonably matched routes/conditions, account for heat/hills/measurement uncertainty; **never force easy pace faster** just to hit a weekly pace target. |
| **Race performance** | Distance-specific 5K/10K/half/marathon time or finish objectives when desired. |
| **Maintain fitness** | Sustain current mileage/fitness without unnecessary progression. |
| **Return to running** | Rebuild frequency and volume after interruption using current evidence, not an automatic return to peak mileage. |

User may combine **one primary and a compatible secondary goal** (e.g. build 40→65 km/week while improving easy efficiency) so the engine coordinates, rather than stacks, workload.

**Approval-first adaptation contract:**
- Coach may proactively *propose* changes based on recent sessions and appropriately interpreted wellness/recovery; planned calendar does not change until approval.
- Proposal must display original vs proposed session/volume/date, reason, source freshness, tradeoff and impact on the remainder of the week; options: Approve, Keep original, Postpone.
- Preserve the approved plan after rejection; no silent next-day surprises or unexplained modifications.
- Missing wellness is uncertainty, not a forced recovery verdict; user can explicitly override suggestions.
- Benchmark observable plan behavior against documented training principles and publicly described Runna/Freeletics experiences without asserting access to proprietary algorithms.

**Exercise library foundation:** Prioritize a **curated, high-quality** cross-discipline library with rights-cleared imagery, instructions, equipment, safe alternatives and calisthenics progression relationships; expand catalogue after quality and engine validation.

### M5 — Fitness intelligence

- Visual recorded weekly/monthly running volume and training consistency, easy aerobic trends under comparable conditions, and authentic activity-level pace/HR/splits/cadence/elevation where available.
- Strength/calisthenics trends from actual reps/sets/holds/load and progression state, without merging incompatible quantities.
- Distance-specific fitness and adjustable goal progress only with clearly defined, defensible calculations. Do not present race predictions as measured fitness.
- Human-readable coaching analysis should explain what evidence means and what optional training adjustment is recommended; avoid repeating raw metrics without interpretation.

## Planned delivery and review practice

1. **One active implementation branch per non-overlapping workstream.** Copilot codes, builds and tests; Claude may work ahead on product/design/architecture; ChatGPT can review GitHub evidence and coordinate priorities.
2. **No speculative implementation claim.** Link each milestone to an exact commit, CI run, PR and documented acceptance result when its state changes.
3. **Implement cohesive user journeys in small testable increments**; group related low-risk UI fixes, but isolate data-model and engine changes.
4. **Emulator/CI first; phone tests only with owner consent and signing/data safety confirmed.** Keep existing private app data.
5. **No automatic merging** or public release. Failed tests are investigated, not removed to claim success.
6. Archive approved design/engineering specifications in GitHub as separate, versioned reference documents when practical; this roadmap summarizes decisions rather than replacing detailed acceptance documents.

## Decisions and open technical checks

**Confirmed product decisions (owner):** Priority order Garmin → UX → Engine 2.0; all-in-one fitness; personal Android alpha; visual-first experience; gradual weekly mileage and easy-aerobic-efficiency plans; high-quality exercise library first; approval required before adaptive workout/plan changes; data truth and offline core.

**Requires technical verification:** Current branch baseline, source-level activity linking, Garmin credential/auth/terms and supported fields, duplicate identity strategy, structured running prescription model, Compose theme tokens, Activity Detail implementation parity, APK signing/migration path. These are engineering questions, not reasons to overwrite product priorities.

## Links and documentation notes

- [Historical engineering roadmap](ROADMAP.md) tracks earlier Phase 4D/4E milestones; it is retained for historical context and **is not the canonical forward product roadmap**.
- [Guided + multi-session integration PR #3](https://github.com/Isshhin/stridelab-development/pull/3) and [multi-session scheduling PR #2](https://github.com/Isshhin/stridelab-development/pull/2).
- Future PRs should update this document when a **product milestone** changes, not on every minor code commit.
