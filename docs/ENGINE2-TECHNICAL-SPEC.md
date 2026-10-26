# Unified Coaching Engine 2.0

Status: implementation specification, 26 October 2026. Based on the approved requirements recovered from the prior conversation and this task. No verbatim master-spec file was found. This document records implementation choices, not additional product approval.

## Verified starting point

- Private development hub: Isshhin/stridelab-development, main 368b202128b0df46e76328e188dd971bafa501b7. Its Git tree contains workflows, reports and archive indexes, not application source.
- Accepted Phase 4D source: StrideLab-Phase4D-Final-candidate.zip, SHA256 915c9c37e3816291bfc9ab3ead1f37b400711886056a43fd1af101b602cdaa6b. CI 40254593565 completed successfully. 273 passes are reported in the checkpoint notes; this session must independently rerun regressions before claiming unchanged behavior.
- Phase 4E Fix1 CI 40258790376 completed successfully at f62653017514ae186288eea0229479ef28db02d1. Existing evidence records 5 unit and 2 emulator tests. Signing secrets absent; physical-device acceptance, stable upgrades and updater remain incomplete. No new APK is authorized for distribution.
- Older Next.js repository Isshhin/stride-lab: feature/v4-foundation-recovery at 8e6c0687109ce4f1206245988ec3876b94cc28ed. Package manifest: Next 16, React 19, Supabase SSR, FIT SDK, vinext/Vite and Cloudflare. Supabase migrations and edge functions exist. This is a separate application, not the accepted offline HTML/Android source. Do not overwrite it or pretend its cloud features exist offline.

## Architecture and contracts

One dependency-free UMD module runs in Node, a browser, Next.js through an adapter, or Android WebView. No network, randomness or system clock inside planning. Caller supplies ISO local dates; calculations use UTC day arithmetic without changing the user's date. Versioned JSON athlete context includes primary and secondary discipline goals, separate abilities, equipment quantities, available days and minutes, completed training history with provenance, optional wellness, self-reported recovery and feedback. Unknown wearable values remain unknown. Demo workouts and simulated run timers are never evidence of fitness.

Pipeline: validate context -> normalize actual history -> assess recovery -> build per-discipline prescriptions -> coordinate one schedule -> validate invariants -> return explainable draft. Workload is a vector: minutes by discipline, running km where measured, strength sets and demanding lower-body exposures. Session RPE times actual minutes is a descriptive shared measure only when both were recorded; it is not an injury prediction score or a conversion between km and lifted kg.

Plan schema: version, policyVersion, deterministic id, start/end, context snapshot, sessions with stable ids, prescriptions, rationale and explicit unmet requests. Draft is separate from accepted plan. Adaptation produces another draft with base revision and a human-readable change list. Acceptance verifies the base revision, recomputes the draft from its context, then writes the entire afterglow2 payload atomically before publishing calendar state. Stale tabs, altered drafts and storage failures cannot silently replace a plan. Older state keys are retained. Unknown future engine schema is rejected without erasing data.

## Initial policy (conservative engineering defaults)

- One session per available day, at least one rest day per rolling week. Requested frequencies are ceilings, never promises. Primary goal gets scheduling priority; secondary goals receive available compatible slots. Unmet work is reported, never silently stacked.
- Beginner run/walk uses duration and RPE, no invented pace. Established running volume starts from observed recent completed duration; optional declared baseline is explicitly user supplied. The first horizon does not automatically grow because calendar weeks elapsed. Quality is at most one running session per week and requires established running ability plus sufficient observed/declared volume.
- Running quality, longer running and resistance work involving legs share a demanding-exposure constraint: no adjacent demanding days, including week boundaries. This is a conservative scheduling heuristic, not a universal physiological law. No ACWR safety threshold or universal 10-percent rule.
- Resistance prescriptions: 2 sets initially, 3 for established trainees, controlled reps, 2–3 reps in reserve and 90–120s rest. Equipment is selected from explicitly available quantities. No unsupported pull-up, rack, bench or load assumptions. If a balanced pattern cannot be supplied, disclose the gap. Session duration includes estimated work/rest and warm-up; reduce sets or omit the session if the time budget cannot fit it.
- Calisthenics stage progresses only after two recorded successful, pain-free, technique-confirmed exposures at the current stage. Load/repetition progression likewise needs completion feedback; time alone cannot grant progression. Do not infer strength ability from running ability.
- Active illness, injury or red-flag symptoms block exercise prescriptions and request recovery review. This engine does not diagnose or prescribe rehabilitation. Recent return or fatigue reduces volume and removes running quality. Missing HRV is not bad HRV. Isolated sensor anomalies prompt review, never a diagnosis or automatic clearance.
- Missed sessions are not carried as debt. Adaptation preserves past dates and completed sessions, removes missed future obligations, and never adds compensatory volume. All plan replacements require explicit approval. Recovery warnings may appear immediately without changing the accepted plan.

These defaults are versioned and intentionally limited. Multiple quality sessions, same-day doubles, competitive race periodization, injury-specific substitutions and clinician-directed return-to-sport require later policy work, not an implicit advanced mode.

## Evidence and competitor comparison

Reviewed primary public sources 26 October 2026:

- ACSM 2026 overview: https://acsm.org/resistance-training-guidelines-update-2026/ . Individualization, consistency, all major muscle groups at least twice weekly, bodyweight/bands effective. Initial engine may provide less than this when schedule/equipment constrain it and must disclose that limitation. It is not yet an optimized hypertrophy or power program.
- ACSM progression position stand (2009): https://pubmed.ncbi.nlm.nih.gov/19204579/ . Historical progression guidance; current 2026 overview takes precedence for broad healthy-adult defaults.
- Runna skip/rearrange guidance: https://support.runna.com/en/articles/15012850-how-and-when-to-skip-a-run-managing-missed-sessions-in-your-training-plan . Missing work is not training debt; adapt to completed work and avoid cramming. No claim to Runna's proprietary algorithm or equivalent race results.
- Freeletics dictionary: https://www.freeletics.com/en/blog/posts/the-freeletics-dictionary/ . Adapt for equipment, time and excluded body areas; session feedback influences subsequent programming. Our first version supports equipment/time and feedback progression; injury-specific body-area programming is deferred and injury blocks prescriptions.

These sources ground design principles. Exact numerical defaults above are product heuristics unless explicitly supported. They are not clinically validated policies.

## Milestones and acceptance

1. Shared model and planner: deterministic replay, strict validation, separate abilities, provenance, bounded prescriptions, weekly and boundary recovery invariants, realistic scenario tests.
2. Adaptation and persistence: feedback-based progression, missed work, illness/injury, explicit approval, stale revision rejection, save failure atomicity and offline reload.
3. Interface: optional Engine 2.0 builder, plain-language input controls, preview and unmet requests, approval, calendar/workout projection; preserve legacy builder and imports. No generated AI is required.
4. Integration hardening: inherited 273-test gate against integrated HTML, responsive browser checks, no new network dependency, unchanged protected engine and assets. Exact archive SHA manifest and private feature commits.
5. Later: full wearable/cloud adapter and Next.js port, richer long-term periodization, quantified equipment/loading history, Android engine packaging/backup migration and device acceptance. Do not deploy risky DB migrations or change stable Android assets during the foundation milestone.

Acceptance scenarios include beginner without sensors; established runner; strength-only; calisthenics with no bar; mixed goals; one-day availability; missed/busy week; fatigue; active illness/injury; sparse/duplicate/imported history; over-subscribed schedules; storage quota failure and two tabs. Test reports distinguish executable assertions from design comparisons and manual/device limits.
