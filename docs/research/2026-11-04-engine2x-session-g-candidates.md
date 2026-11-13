# Session G | Implementation candidates and competitive scorecard

IDs join all tables. Capability grades refer to the nearest demonstrated StrideLab mechanism, including reusable foundations. Evidence grades concern its current support for the named decision. IMPLEMENTED_CANDIDATE covers a demonstrated foundation awaiting Engine 2.x integration. RESEARCH_PROTOTYPE can cover only part of a decision, such as a revision contract. ABSENT includes design-only proposals. No row qualifies as INTEGRATED for the proposed end-to-end 2.x policy. SYNTHETIC_ONLY supports structural behavior, while HYPOTHESIS marks an unvalidated inference. No policy here qualifies as OBSERVATIONAL, PROSPECTIVELY_VALIDATED or EFFECTIVENESS_VALIDATED on the strength of external human studies. The compared product descriptions are documented functions, with E002/E007/E008 observation gaps detailed in A.

| ID | Capability | Source sessions | Evidence classes informing design | Capability maturity | Evidence maturity | Competitive classification | Comparator scope |
|---|---|---|---|---|---|---|---|
| G01 | Plan generation | A,C | SYNTHETIC/VENDOR | IMPLEMENTED_CANDIDATE | SYNTHETIC_ONLY | INSUFFICIENT_EVIDENCE | Runna event plans |
| G02 | Response-aware progression | B,C,H | SCIENCE/SYNTHETIC | RESEARCH_PROTOTYPE | SYNTHETIC_ONLY | BEHIND_SLIGHTLY | Runna build rate, Kaizen load |
| G03 | Athlete state | B,F,H | SCIENCE/HYPOTHESIS | RESEARCH_PROTOTYPE | HYPOTHESIS | BEHIND_SIGNIFICANTLY | Garmin Run Coach ongoing signals |
| G04 | Readiness and fatigue | B,H | SCIENCE/VENDOR | ABSENT | HYPOTHESIS | BEHIND_SIGNIFICANTLY | TrainerRoad and Garmin DSW recovery |
| G05 | Workout adaptation | B,E,F | SYNTHETIC/HYPOTHESIS | RESEARCH_PROTOTYPE | SYNTHETIC_ONLY | BEHIND_SIGNIFICANTLY | Runna dose edits, TrainerRoad alternates |
| G06 | Interruption and re-entry | B,C,E | SYNTHETIC/SCIENCE | RESEARCH_PROTOTYPE | SYNTHETIC_ONLY | BEHIND_SLIGHTLY | Runna and TrainerRoad time away |
| G07 | Environmental interpretation | A,D,H | SCIENCE/HYPOTHESIS | ABSENT | HYPOTHESIS | BEHIND_SIGNIFICANTLY | Runna heat options |
| G08 | Aerobic efficiency | B,D,H | SCIENCE/HYPOTHESIS | ABSENT | HYPOTHESIS | INSUFFICIENT_EVIDENCE | Runna Score is plan-relative |
| G09 | Long-run adaptation | C,D | SCIENCE/HYPOTHESIS | RESEARCH_PROTOTYPE | SYNTHETIC_ONLY | BEHIND_SLIGHTLY | Runna return long-run safeguard |
| G10 | Quality-session adaptation | C,E,H | SCIENCE/HYPOTHESIS | RESEARCH_PROTOTYPE | SYNTHETIC_ONLY | BEHIND_SIGNIFICANTLY | Runna reps vs pace choices |
| G11 | Strength autoregulation | A,H | VENDOR/HYPOTHESIS | ABSENT | HYPOTHESIS | BEHIND_SIGNIFICANTLY | Fitbod per-exercise dose, RP weekly sets |
| G12 | Mixed-discipline coordination | A,E,H | SCIENCE/SYNTHETIC | RESEARCH_PROTOTYPE | SYNTHETIC_ONLY | POTENTIAL_ADVANTAGE | DSW indirect recovery, Fitbod cardio context |
| G13 | Explainability | A,B,E | SYNTHETIC/HYPOTHESIS | RESEARCH_PROTOTYPE | SYNTHETIC_ONLY | POTENTIAL_ADVANTAGE | Runna choice and explanation |
| G14 | Athlete control | A,E,F | SYNTHETIC | IMPLEMENTED_CANDIDATE | SYNTHETIC_ONLY | POTENTIAL_ADVANTAGE | Runna options, Garmin Run Coach revision |
| G15 | Persistence and reliability | A,E,F | SYNTHETIC | IMPLEMENTED_CANDIDATE | SYNTHETIC_ONLY | INSUFFICIENT_EVIDENCE | Vendor offline guarantees unknown |
| G16 | Real-world calibration | B,D,F | HYPOTHESIS | ABSENT | HYPOTHESIS | INSUFFICIENT_EVIDENCE | Vendor equivalent held-out results unknown |
| G17 | Explicit abstention | B,D,E,F,H | HYPOTHESIS | ABSENT | HYPOTHESIS | POTENTIAL_ADVANTAGE | Equivalent comparator semantics unverified |

A significant gap denotes a documented vendor decision loop missing in the comparable StrideLab scope. A slight gap denotes a partial replayable mechanism with a narrower missing step. Potential advantage identifies a distinction for testing. Insufficient evidence leaves the comparative result open even where a mechanism exists. No row earns NEAR_PARITY. The scorecard is qualitative and endpoint-specific.

| ID | Applicability / confidence in transfer | Required inputs → candidate action/output | Safeguard | Abstention condition | Falsifier | Required experiment / next gate | Current status |
|---|---|---|---|---|---|---|---|
| G01 | Direct structural / moderate | Goal, dates, days → versioned draft | Lock and availability conflicts | No feasible slot | Unreproducible plan or displaced lock | P2-F + E002/E007 | Foundation only |
| G02 | Direct design / low | Verified weekly/native exposures → change one axis | Separate frequency and longest effort | Unknown actual exposure | Fails to beat history-only progression | P2-C | Research only |
| G03 | Direct design / low | Time-stamped history and check-in → dimension estimates | As-of joins and independent labels | No baseline or decisive conflict | No incremental calibration | P2-A | Research only |
| G04 | Context-specific / low | Optional sleep, HRV, RHR and RPE → contextual flags | Restrictions take priority | Sensor/protocol discontinuity | More false restrictions than check-in | P2-A | Research only |
| G05 | Direct design / low | Intent, reason and actual work → reviewed edit | No added training debt | Unknown reason changes choice | No advantage over generic edit | P2-D | Research only |
| G06 | Direct design / low | Reason, prior and recent tolerated dose → return proposal | Separate illness and verified nonclinical gaps | Missing upload only | Reason-aware rule fares worse than duration only | P2-C | Research only |
| G07 | Context-specific / low | Route, conditions, sensor → comparability decision | Keep raw target and actual effort | Outside matched domain | Climate stratum reverses effect | P2-B | Research only |
| G08 | Context-specific / low | Repeated steady HR/speed segments → trend with error | Replicate beyond typical error | Weak overlap or device bridge absent | Signal smaller than typical error | P2-B | Research only |
| G09 | Direct design / low | Longest actual exposure, late response → restore or defer | Separate weekly and session totals | No verified exposure | No gain over simple last-long-run baseline | P2-C | Research only |
| G10 | Direct design / low | Bout-level dose and RPE → shorten bouts or reduce reps | Preserve primary stimulus | Laps or reason missing | Same utility from simple volume reduction | P2-D | Research only |
| G11 | Context-specific / low | Prior lift load, RIR and goal → next dose proposal | Exercise identity and equipment match | New movement without comparable history | Logbook baseline equally effective | P2-E | Research only |
| G12 | Context-specific / low | Movement demands, priority, spacing → conflict review | Explicit endpoint and session locks | Unknown demand/priority changes ordering | Simple scheduling preserves quality equally | P2-E | Research only |
| G13 | Direct design / low | Decision evidence and counterevidence → review record | Show alternative and uncertainty | Missing lineage blocks change | Reviewers cannot reconstruct rationale | P2-D | Research only |
| G14 | Direct structural / moderate | Version and decision → accept/dismiss/rollback | Stale acceptance rejected | Conflicting newer revision | Silent change or irreversible edit | P2-F / P2-D | Foundation only |
| G15 | Direct structural / moderate | Decision ID, version, account → durable replay | Exactly-once effects, scope and recovery | Unresolved ledger mismatch | Divergence after offline reopen | P2-F | Foundation only |
| G16 | Direct design / low | Authorized plan, actual dose and independent outcomes → validation set | Purpose-limited access and leakage audit | Unverified rights/label | Leakage or absent follow-up denominator | P2-F | Research only |
| G17 | Direct design / low | Conflict and critical missing facts → hold automation | Separate KEEP and independent stop | Decision-critical uncertainty | Apparent accuracy improves only by exclusion | P2-A / P2-D | Research only |

Use the authoritative experiment ID map and practical gates in [Phase 2](2026-11-04-engine2x-session-g-phase2.md). Failing a falsifier keeps the proposed coaching action research-only. For G01, inspect dated matched drafts and constraints, independently of outcome grading. G13 also requires blinded users to reconstruct the intended change and tradeoff. G14–15 must pass engine reliability regression, stale-proposal, rollback and offline reopen cases before a field release could be considered.

## G-R3 | Current mechanism provenance

Locations identify the nearest demonstrated mechanism within the named scope. The transfer column describes the principal engineering disposition if later gates pass. REUSE preserves a foundation through its existing boundary. PORT carries a limited contract into 2.x and requires regression verification. NEW_IMPLEMENTATION indicates that the claimed decision loop must be built. No row currently requires a wholesale REWRITE. Trackside rendering a decision does not establish ownership of its coaching policy.

Native file names refer to the [Engine branch source snapshot](https://github.com/Isshhin/stridelab-development/tree/66fd5cf75226d8785fe599986f3c6f2d18be7668/native-android/app/src/main/java/io/stridelab/privatealpha). Research module names refer to the [isolated validation harness](../../research/validation). Neither location implies production Engine 2.x integration.

| Capability ID | CURRENT_MECHANISM_LOCATION | 2.x transfer | Mechanism boundary and remaining work |
|---|---|---|---|
| G01 | ENGINE_1_0 | REUSE | Engine1.kt and MultiSessionEngine1.kt generate deterministic base plans. Preserve versioned fallback and compare matched drafts. |
| G02 | ENGINE_1_5 | PORT | Engine15.kt bounds progression; individual exposure-response selection requires a new calibrated policy. |
| G03 | RESEARCH_CONTRACT | NEW_IMPLEMENTATION | E005 and contract_model.py represent load/state inputs. Build and validate a time-indexed estimator. |
| G04 | NOT_IMPLEMENTED | NEW_IMPLEMENTATION | Session B specifies readiness ablation; no validated recovery-driven selector. |
| G05 | RESEARCH_CONTRACT | PORT | week_change_contract.py represents explicit edits. Selecting the right edit remains new work. |
| G06 | RESEARCH_CONTRACT | PORT | policy_candidates.py and multiweek_reconciliation.py replay disruption. Fit and test reason-specific return rules separately. |
| G07 | NOT_IMPLEMENTED | NEW_IMPLEMENTATION | Session D defines matching; personalized environmental interpretation is still a protocol. |
| G08 | NOT_IMPLEMENTED | NEW_IMPLEMENTATION | E010 proposes repeatability. Build descriptive estimation after admission. |
| G09 | ENGINE_1_5 | PORT | Engine15.kt and its coaching tests provide bounded running prescriptions. Exposure-specific long-run restoration requires new logic. |
| G10 | RESEARCH_CONTRACT | PORT | week_change_contract.py represents dose revisions; bout-level attribution and selection remain unimplemented. |
| G11 | NOT_IMPLEMENTED | NEW_IMPLEMENTATION | Batch 6 proposes strength autoregulation; native logging or exercise selection alone cannot choose the next dose. |
| G12 | ENGINE_1_5 | PORT | Engine15InterferenceScheduling.kt coordinates ordering. Validate goal-specific interference separately. |
| G13 | RESEARCH_CONTRACT | PORT | difference_review.py records policy disagreement. Counterevidence presentation and comprehension need separate work. |
| G14 | ENGINE_1_5 | REUSE | Engine15Feedback.kt and repository tests cover reviewed suggestions. Recheck versioned accept/dismiss semantics for 2.x. |
| G15 | SHARED_PERSISTENCE | REUSE | TrainingRepository.kt and reopen tests supply native persistence. sqlite_ledger.py is separate research evidence for replay. |
| G16 | NOT_IMPLEMENTED | NEW_IMPLEMENTATION | original_lab_admission.py is an admission gate; prospective athlete calibration still requires a new pipeline. |
| G17 | NOT_IMPLEMENTED | NEW_IMPLEMENTATION | proposal_authority.py is a precedent for withholding authority. Implement the four distinct controls in the proposed decision contract. |
