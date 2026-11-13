# Session G | What may change the next workout?

4 November 2026 · Synthesis drafted, corrections ready for independent re-review · Phase 2 protocols proposed. At entry, GitHub compared the reviewed checkpoint `faf98c5595598d41d643a1dfff39145e3f01791e` with `research/runna-garmin-coaching-20261102`: identical, zero commits ahead or behind. PR #34 was open, draft and unmerged.

During writing, the branch advanced five commits to `580843c56868fcdb0a7590cad9a95b14c62c90f4`, adding the final prompt and continuity material. This synthesis builds on that newer tree.

## Decision

Start with the evidence chain from a timestamped prescription to what the athlete actually did. A training log can show a short run. Without the plan, the reason it ended and the later response, it cannot tell us whether reducing tomorrow’s threshold session would help. Build the research comparison around those missing facts. Open Phase 2 with authorized export admission (E009), repeated comparable easy segments (E010), and prospective capture of plan versions and outcomes. Keep new coaching rules in shadow evaluation.

Use the [candidate and competitive register](2026-11-04-engine2x-session-g-candidates.md), [state dictionary](2026-11-04-engine2x-session-g-state.md) and [experiment protocols](2026-11-04-engine2x-session-g-phase2.md) as parts of this report. No new athlete observations, model fits or intervention outcomes were collected.

## What survived discovery

| Input | Finding that changes the design | Limit |
|---|---|---|
| A | Runna distinguishes mileage, hard dose and pace. Garmin Run Coach revises a plan. Garmin Daily Suggested Workouts selects daily sessions. Compare those outputs separately. | Eight historical Runna plans span 92 weeks. Current E002 is 0/16, E007 is 0/6 and E008 has zero verified Garmin Run Coach pairs. |
| B | Time-stamp recent exposure and perceived response before interpreting optional HRV or sleep. Test incremental value against history plus a brief check-in. | Selection into completed sessions and labels derived from predictor inputs can manufacture prediction. |
| C | Separate interval, longest-run and total-week exposure after interruptions. | A recent PR and a tolerated weekly total say little about the last comparable long run. |
| D | Measure steady speed at comparable easy HR and HR at comparable speed separately. Retain route, sensor and typical error. | Changed pace alone cannot distinguish training response from heat, fatigue or GPS error. |
| E | Choose the smallest feasible edit that preserves the intended stimulus. Recheck the week. | Revision/replay tests demonstrate that a change can be represented. Choosing an effective change remains open. |
| F | Admit original exports by purpose, validate parsing, then link decisions to actual dose and independent future outcomes. | Cohort associations and selected injury windows cannot identify the effect of the alternative session. |
| H | Test each physiological claim separately. Monitoring at a comparable HR does not require an age formula. | Mechanism, individual threshold agreement, prediction and benefit require different experiments. |

The pre-synthesis review changes four design decisions. The register grades mechanism and evidence separately. ABSTAIN_NEED_MORE_INFORMATION has its own output contract. Several athlete dimensions can coexist. Promotion requires practical decision utility, coverage and burden on unseen athletes and future dates.

Batch 1 separates work-step dose from workout distance. Batch 2 keeps external work and internal response separate. Batches 3–4 supply interruption and distance-first versus load-aware comparators. Batches 5–6 preserve discipline-native dose and lift feedback. Batch 7 supplies traceability. Batch 8B–F supplies frozen B0/H1 cases, adversarial replay, crash recovery, multiweek reconciliation, explicit revisions and admission boundaries. Reuse these as engineering baselines.

## Contradiction register

All references to investigators and vendor comparisons below refer to the linked discovery reports and their primary-source registers. SCIENCE supports a specific measurement or population association, VENDOR a published behavior, OBSERVATION a dated capture, ATHLETE an admitted record, SYNTHETIC a fixture result, and HYPOTHESIS a proposed decision. Keep all six labels on their respective records.

| Question and opposing evidence | Why findings differ | Current boundary and interim decision | Discriminating test |
|---|---|---|---|
| HRV-guided intervention results vs pooled/field nulls (B,H) | Within-person endpoints, protocol and selection differ | Input contracts exist. Keep HRV optional, condition on baseline and symptoms. | A: same eligible decisions with and without HRV, independent future outcome |
| Faster at matched HR vs apparently better ratio during fatigue (B,D,H) | Physiological state, sensor and route confound direction | Field protocol exists. Describe eligible repeats with effort and uncertainty. | B: repeatability, reversed-route control and subsequent independent performance |
| Maffetone's age formula development vs absent independent threshold agreement (D,H) | Group relationship vs individual agreement | No formula-based dose. Retain self-selected easy protocol. | Blind comparison to independently assessed threshold with limits of agreement |
| Seiler-style polarized vs pyramidal or threshold distributions (H) | Session count vs time in zone, season and population | Store numerator, denominator and phase. No universal allocation. | Compare strategy by phase with identical classification and outcome |
| Brooks/San Millán lactate models vs interchangeable watch zones (D,H) | Substrate physiology and device label are different constructs | Preserve LT1, VT1, Fatmax and watch method separately. | Paired lab protocol, independent reference and individual error |
| Buist weekly 10% trial vs single-run spike cohort vs ACWR (C,H) | Intervention vs observational association, week vs session and mathematical coupling | Existing bounded progression is a safeguard. Log separate exposures. | C: lagged exposure against simple history, with true session distances and at-risk denominators |
| Mujika taper evidence vs routine deload or rebuild (C,H) | Target race performance, accumulated fatigue and lost exposure have distinct endpoints | Route each reason separately. No calendar deload rule. | C: reason-stratified natural follow-up and later controlled test |
| Concurrent exercise nulls for maximal strength/hypertrophy vs explosive losses (H, Batch 5) | Endpoint, local muscles and same-session order differ | Scheduling contracts exist. Flag competing priority only. | E: compare next-session quality and chronic endpoint separately |
| Active recovery preference vs weak evidence for compulsory activity (E,H) | Comfort and subsequent performance differ | Permit optional recovery or rest where appropriate. | D: preference-aware comparison with burden and next-session tolerance |
| Heat-related strain vs transferable pace correction (D,A,H) | Group effects differ from individual outdoor response | Keep original target, actual context and modified target. | B: matched vs corrected vs unadjusted, with no-correction fallback |
| Completion vs adaptation (A,B,E,F) | Plan adherence and underlying fitness can diverge | Record partial and declined sessions with reason. | A/D: separate prescription execution from blinded follow-up |
| Training interruption vs missing upload (B,C,F) | An observation gap has multiple causes | Existing replay handles known events. Ask one reason only if it changes eligibility. | F/C: diary audit and masked-upload negative control |

## Decision architecture and precedence

1. **Observe and admit.** Preserve athlete, source event, protocol, device, consent scope, event time, available-at time and plan version. Reject ineligible records before computing baselines. Deduplicate correlated vendor summaries and their underlying signals.
2. **Apply hard gates before inference and again before commit.** A supplied restriction or athlete stop blocks the affected proposal regardless of favorable physiology. A lock constrains editing, never obliges execution. Availability and equipment bound feasibility. Unresolved conflict goes to review. A new restriction invalidates an old proposal.
3. **Estimate dimensions at decision time.** Carry observed dose, short response, longer trend, context and confidence separately. Unknown interruption reason blocks a graded return. A hot, hilly run can be evidence of experienced strain while ineligible for estimating fitness change.
4. **Select intent and allowable exposure.** Anchor to the approved plan, current phase, recent tolerated comparable exposure and the athlete’s priorities. Encode easy aerobic work, long exposure, threshold, high intensity, mechanics, race-specific work, hypertrophy, maximal or explosive strength, skill, conditioning and recovery distinctly. Cross-discipline constraints apply before ranking feasible edits.
5. **Choose one primary action.** Context and recent tolerance may narrow feasible options. Optional sleep, HRV and resting HR cannot independently override symptoms, stops, current restrictions or stale exposure. Progression opportunity ranks last. Positive support for the approved session permits KEEP. Unresolved decision-critical evidence emits ABSTAIN_NEED_MORE_INFORMATION, leaves plan version intact, identifies the missing fact and triggers reevaluation only when it arrives. Any independent safety hold persists.
6. **Simulate downstream effects before asking the athlete.** Re-evaluate adjacent quality, long run, lower-body lifting, locks, daily capacity, actual accumulated exposure and next week. Discard hidden catch-up or a second unapproved edit. If no feasible revision exists, expose the constraint.
7. **Review then execute.** Show original and proposed doses, intent retained/lost, evidence for and against, uncertainty, alternatives, affected days and expiry. Accept against the exact plan version, dismiss, or retain the approved plan. Recheck changed inputs, preserve idempotent offline replay and rollback. Execution records actual dose, partial/skipped/substituted/unknown, RPE, symptoms/context and subsequent response. Only future decisions may use these outcomes.

### G-R2 | Control outcomes at the decision boundary

Assess scopes separately. For an affected workout the precedence is HARD_HOLD, then NO_AUTOMATED_DECISION, then ABSTAIN_NEED_MORE_INFORMATION, then a supported KEEP or reviewed revision. Record all applicable reasons even if only one primary outcome is shown. Re-evaluation creates a fresh versioned decision. Retaining a calendar entry never grants execution clearance.

| Control | Plan and execution | Question and review surface | Logged reason codes | Expiry, reevaluation and automatic clearing |
|---|---|---|---|---|
| KEEP | Retain approved version. Existing execution permission continues only after constraints pass and positive evidence supports the same intent/dose. | No routine question. Show grounds, counterevidence and decision time. | SUPPORTED_DOSE, CURRENT_CONSTRAINTS_MET | Expires on new material event, version change or session start review. May recompute automatically; never cache as default. |
| ABSTAIN_NEED_MORE_INFORMATION | Preserve approved plan. Withhold automated recommendation and execution endorsement; manual athlete choice is separate, subject to existing restrictions. | Ask one low-burden question only if its answer would resolve the decision. Show what is missing and why it matters. | UNKNOWN_INTERRUPTION_REASON, CONFLICTING_RESPONSE, STALE_EXPOSURE | Re-evaluate when that fact arrives. Can resolve automatically only through a fresh eligible decision with affirmative grounds; elapsed time cannot turn it into KEEP. |
| NO_AUTOMATED_DECISION | Preserve plan. Disable automated prescription/launch for this scope. Independent manual review remains available under applicable restrictions. | Show the unsupported domain or invalid lineage and route to manual review. Do not ask an athlete to repair policy validation. | OUTSIDE_DOMAIN, INVALID_LINEAGE, POLICY_UNVALIDATED | Requires documented domain eligibility, verified provenance repair or approved policy version. No automatic clearance from routine athlete signals. Rerun only after the underlying gate is restored. |
| HARD_HOLD | Preserve plan history, mark affected execution blocked and suppress conflicting recommendations. Independent unaffected activities retain their own gates. | Show scope, source and release condition. Request clarification only from an appropriate source when needed. No ordinary accept/dismiss bypass. | ATHLETE_STOP, SUPPLIED_RESTRICTION | No automatic clearance. Explicit athlete release for an athlete stop; documented release per source terms for a supplied restriction. Time or favorable physiology may trigger review only. Recheck all overlapping holds and other controls before execution. |

Example: normal HRV plus an explicit athlete stop yields HARD_HOLD. A missing upload plus confirmed cross-training records the other activity; if running exposure remains decision-critical, abstain. Unsupported sensor provenance yields NO_AUTOMATED_DECISION for the affected estimate even if the athlete can independently choose an approved activity.

### Interpreting a concrete week

A runner completes three of four planned threshold bouts before class. Easy speed at the same HR looked better last week, today’s route was hotter, and recent long-run exposure is thin. Keep those facts separate. A confirmed time limit with three controlled bouts supports recording partial execution without fitness penalty or compensatory work. Repeated fade late in each bout raises a different hypothesis about bout duration. If no reason is known and tomorrow’s dose depends on it, abstain and ask what ended the session. A locked long run stays visible for review if the proposed move would crowd it.

### Progression, re-entry and dose

Track weekly distance/time, frequency, longest session, hard minutes, bout length, hard-day count, sets by movement and local cross-discipline exposure independently. PROGRESS a single eligible dimension only after comparable recent tolerance; MAINTAIN when supported and further exposure is unnecessary; REDUCE the affected dimension after documented intolerance; DELOAD considers recurring strain across sessions. Distinguish planned race taper from reactive deload. REBUILD_EXPOSURE follows a confirmed nonclinical interruption. RESTORE_LONG_RUN and RESTORE_QUALITY require separate exposure evidence. DEFER_INTENSITY can coexist with rebuilding easy volume. Illness, injury, post-race and unexplained absences have separate routing. Supplied restrictions constrain every option. No fixed weekly percentage, ACWR threshold, catch-up debt, calendar deload or automatic return to former intensity.

| Action | Candidate reason | Dose and downstream tradeoff |
|---|---|---|
| KEEP | Comparable dose tolerated, constraints fit | Preserve approved week and record positive grounds |
| REDUCE_VOLUME | Time or accumulated dose limits total work | Trim easy duration or total sets, retain priority component |
| REDUCE_INTENSITY | Target effort unsustainable from the outset | Lower target, relabel intent if threshold stimulus is lost |
| SHORTEN_INTERVALS | Late-bout fade with controlled starts | Shorter continuous stimulus, review total hard time |
| REDUCE_REPS | Individual bouts controlled but total dose excessive | Preserve bout duration, remove a repeat without repayment |
| SUBSTITUTE | Venue, equipment or modality unavailable | State which stimulus transfers and which exposure is lost |
| MOVE | One schedule conflict with a feasible alternative | Recompute neighboring sessions and require approval |
| RECOVERY | Athlete wants feasible low-demand activity | Offer optional dose and rest alternative |
| REST | Documented need or explicit choice to stop | Record reason and revisit affected future sessions |
| ABSTAIN_NEED_MORE_INFORMATION | Critical input missing, stale, conflicting or outside validated scope | Preserve approved history, surface unknown and any independent hold |

### Response, environment and mixed disciplines

Easy response uses steady comparable observations. Threshold response uses controlled work duration, effort and recovery. Speed tolerance uses completed fast bouts, quality and local symptoms. Long-run durability compares early and late standardized segments with prior work, fueling and conditions. Strength response uses achieved load, reps, range, RIR and movement-specific history. Adherence measures execution opportunity. Accumulated fatigue is latent. An increase in any one performance measure requires replication beyond typical error and an independent endpoint before it can justify a dose decision.

Speed at comparable HR and HR at comparable speed are descriptive first, separate candidate state inputs after repeatability. HR drift at fixed external demand and signed pace/HR decoupling are descriptive within-session measures. Durability and threshold trends are research-only progression modifiers pending independent criteria. A watch threshold update triggers a provenance review. Stage measurement feasibility, criterion agreement, forward prediction and coaching benefit separately.

Match route, direction, grade, surface, altitude, velocity/wind direction, sun exposure, temperature and humidity. Record dew point or vapor pressure with derivation and units, acclimation, stops, hydration and fueling where supplied. Distinguish station weather from personal exposure, elevation gain from altitude, and missing wind from calm. Compare only on common support; label unmatched efforts contextual. A numerical correction must beat matching and no correction in held-out climate/device strata with interval coverage, useful eligible coverage and bounded error. Outside support, return the raw observation with uncertainty.

For running, strength, calisthenics and conditioning, preserve movement, local muscle demand, eccentric novelty, experience, priority, spacing, order and accumulated work. A heavy eccentric leg session and an upper-body skill practice imply different questions for a subsequent run. Compare next-session quality separately from long-term explosive power, maximal force, hypertrophy and endurance. No exact spacing rule or universal conversion of sets into running distance.

## Milestones, differentiation and implementation order

**One tier behind** requires a reproducible goal plan plus multidimensional athlete state, response-aware progression, reason-aware re-entry, adaptive dose, context-aware aerobic trends, explicit uncertainty and abstention, deterministic reviewable revisions and real-data calibration for core decisions. Every applicable row must clear its corresponding gate in the register. Assess comparable plan scenarios against dated product captures; document disagreements and unknowns.

**On-par candidate** additionally requires longitudinal individual adaptation, robust calibration with missing or conflicting signals, mature strength and cross-discipline decisions, forward-time and athlete-held-out validation, prospective shadow use, controlled outcome studies for major adaptations, and no regression in Engine 1.5 reliability. Comparable claims require matched goals, population, conditions, horizon and endpoints; proprietary inputs or screenshots alone cannot establish efficacy. Neither milestone is demonstrated.

| Candidate differentiator | Classification | What exists and what would test comparative value |
|---|---|---|
| Explicit uncertainty and abstention | HYPOTHESIS | Four controls specified. Test whether users understand them and unnecessary changes decrease at matched coverage. |
| Evidence provenance | PROTOTYPE | Research source-attestation and linkage contracts. Audit whether reviewers reconstruct input lineage more accurately. |
| Reviewed adaptations | EXISTING | Engine 1.5 offers reviewed feedback. Compare acceptance comprehension and appropriate revisions at equal burden. |
| Transparent counterevidence | HYPOTHESIS | Review contract calls for evidence against each proposal. Blind-test comprehension against reasons alone. |
| Mixed-discipline coordination | PROTOTYPE | Scheduling and research overlap mechanisms exist. Test protected-session quality and goal-specific outcomes. |
| Environment vs fitness separation | HYPOTHESIS | Matching protocol specified. Test false fitness alerts and coverage across climates. |
| Offline deterministic replay | PROTOTYPE | Research ledger and synthetic replay cover selected paths. Compare real-device recovery after interruption. |
| Rollback | PROTOTYPE | Research revision ledger exercises rollback. Audit reconstruction, safe recovery and user success. |
| Conservative missing-data handling | PROTOTYPE | Selected synthetic cases defer on uncertain sources. Test difficult cases and disclose coverage loss. |

These grades describe the differentiator as scoped. VALIDATED requires direct comparative evidence for the claimed advantage. No row qualifies.

After Phase 2 validation, introduce only passed components in dependency order: provenance and plan linkage, review/rollback and safe abstention, descriptive measures, calibrated state, exposure/re-entry suggestions, workout-level edits, then endpoint-specific mixed-discipline and strength decisions. Release decisions would require separate authorization and Engine 1.5 fallback verification. Research-only questions include actionable HRV thresholds, personal heat corrections, MAF prescription, dose from drift, causal injury prevention, spacing rules and equivalence of substitutes. The open design choices are minimum baseline length, acceptable abstention by group, smallest useful change by endpoint and the cost of false restriction versus escalation. Freeze those choices with athletes/reviewers before unblinding results.

## Source reports

- [engine2x-pre-synthesis-review](2026-11-04-engine2x-pre-synthesis-review.md)
- [engine2x-session-f-real-data-and-validation](2026-11-04-engine2x-session-f-real-data-and-validation.md)
- [focused-engine2-evidence-and-experiment-agenda](2026-11-04-focused-engine2-evidence-and-experiment-agenda.md)
- [session-a-competitive-coaching-audit](2026-11-04-session-a-competitive-coaching-audit.md)
- [session-b-athlete-state-and-training-response](2026-11-04-session-b-athlete-state-and-training-response.md)
- [session-c-progression-deload-and-re-entry](2026-11-04-session-c-progression-deload-and-re-entry.md)
- [session-d-aerobic-efficiency-and-environment](2026-11-04-session-d-aerobic-efficiency-and-environment.md)
- [session-e-adaptive-prescription](2026-11-04-session-e-adaptive-prescription.md)
- [session-h-competing-coaching-frameworks](2026-11-04-session-h-competing-coaching-frameworks.md)
