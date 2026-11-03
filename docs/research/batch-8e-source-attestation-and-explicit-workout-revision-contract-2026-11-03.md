# Batch 8E — Source attestation and explicit workout-to-week revision contract

**Verified:** November 3, 2026 (Asia/Manila). **Project:** StrideLab independent Coaching Algorithm Research Lab. **Research branch:** `research/runna-garmin-coaching-20261102` in private `Isshhin/stridelab-development`. **PR:** [#34](https://github.com/Isshhin/stridelab-development/pull/34), draft and unmerged.

**CI evidence:** [GitHub Actions successful run 41588077190](https://github.com/Isshhin/stridelab-development/actions/runs/41588077190) at source commit `f9d592ad1439258551d6f1513f498d9d4c93ccb5`; 109 Python unit tests passed, 0 failed. Historical lanes also passed with their same scope: 58/58 old toy structural contracts, 55/58 new B0/H1 structural checks with three predictions BLOCKED, 288/288 seeded structural adversarial scenarios, 4/4 multiweek synthetic profiles covering 32 athlete-weeks.

This batch does **not** port, replace or run the user's local Python Athlete Simulation Lab and does **not** modify Android Engine 1.0/1.5/2.0 production.

## 1. Actual source audit and integration status

At the time of this check, the connected GitHub installation exposed four repositories under `Isshhin`: `pia-test`, `pia-ml-api`, `stride-lab` and `stridelab-development`. The historically local independent Python Athlete Simulation Lab `E:\Project\stridelab-copilot\stridelab-coaching-lab` was **not available as an identified GitHub repository or verified source artifact**. Do not infer it is absent from the user's PC, present in another service or already synced. The protected dataset at `F:\StrideLab DataSets` remains inaccessible, not inspected, and should never be uploaded without deliberate authorization.

Created [adapter_probe.py](../../research/validation/adapter_probe.py): it explicitly reports B0 and H1 as `CONNECTED_RESEARCH_PROTOTYPE_ONLY`, the original local lab as `BLOCKED_SOURCE_NOT_CONNECTED`, and the Android production engine as `OUT_OF_SCOPE_NOT_TESTED`. The probe emits `research-adapter-status.json` in GitHub Actions and has tests asserting that a prototype cannot silently be reported as the local engine.

## 2. Signed source-link attestation gate (research only)

[Source link gate](../../research/validation/source_link_gate.py) and its [15 adversarial unit tests](../../research/validation/test_source_link_gate.py) enforce **source-to-plan association as an explicit separate operation**, rather than taking a provider upload's user-controlled `verified_session_id` on trust.

The gate:
- Always removes any `verified_session_id` and athlete identity claimed by the raw upload.
- Allows a link only when a separately supplied HMAC-SHA256 signed attestation matches **pseudonymous athlete ID, source provider, immutable provider activity ID, target planned session, issuer/reviewer, provider connection ID, signed evidence reference and issue/expiry window**, and the configured accepted reviewer/connection set authorizes it.
- Rejects invalid signatures, expired grants, revoked evidence, cross-athlete claims, wrong source/external IDs and ambiguous multiple attestations. These imports remain unlinked and are not counted as verified planned-session completion.
- Preserves raw dose records in an append-only SQLite research store; identical source replays are idempotent and contradictory reuse of an activity ID raises a conflict.
- Never guesses identity from matching GPS track, clock time or distance.

**Very important limitations:** HMAC signature integrity does **not** prove a real Garmin, Strava, COROS or other provider account, nor is the issuer identity actually authenticated here. The signing key and allowlist are **runtime inputs**, and the test suite uses a clearly marked synthetic key. This is an isolated integrity gate and proof of concept, **not** a deployable OAuth flow, authorized identity provider, credential store or complete threat model. Production must verify the provider connection, user ownership, credential lifecycle, issuer's authority, deletion/revocation semantics and cross-source matching evidence.

## 3. Explicit workout-to-week proposal contract

[week_change_contract.py](../../research/validation/week_change_contract.py) and its [15 unit tests](../../research/validation/test_week_change_contract.py) establish a **pure, copy-on-write** proposal validator. It can generate a versioned candidate diff, not a self-executing revision. It permits only:
1. An explicitly identified single-session move to a concrete target day, with unchanged training dose.
2. An explicit running-step replacement with concrete easy/hard/recovery step durations and distances. The current research contract permits equal/reduced weekly km and hard work time; **no automatic dose increase** is permitted. That restriction is a temporary engineering guard, not a validated training rule.

It rejects unknown/unsupported disciplines, invalid or missing dose units, absent concrete target day or step tree, proposed increase in running km/hard seconds, missing evidence references, stale input plan versions, locked days, daily session-count limits, unavailable time budgets and a specified weekly running cap violation. Time budgets and running caps are configured test inputs, **not derived or scientifically approved by the algorithm**.

The result contains exact before/after plans and SHA-256 hashes, reason, evidence references, planned running km, easy/hard/recovery seconds, daily time/capacity, `requires_explicit_athlete_approval=true`, and `applied=false`. The existing SQLite research store is tested separately for approve/reject/rollback. **The proposal contract alone is not an authorization mechanism.** A caller could forge an input evidence label if called directly.

## 4. End-to-end evidence binding, without false authentication claims

To avoid treating a caller-supplied `evidence_status=verified` as trustworthy, [proposal_authority.py](../../research/validation/proposal_authority.py) and [10 integration tests](../../research/validation/test_proposal_authority.py) bind both gates:

- Authenticate the signed research attestation against the event's actual provider/source ID, intended athlete, active connection and planned session set.
- Require a confirmed **completed, partial or skipped** observation; unobserved work is not accepted as confirmed source evidence.
- Replace **all untrusted caller-provided evidence labels and references** with validated, signed research-attestation references.
- Block an absent, expired, tampered, cross-athlete or revoked attestation, and block an H1 move proposal lacking an explicit target day.
- Accept **research-policy-sourced draft proposals only**. Actual athlete-initiated requests are explicitly `ATHLETE_AUTHENTICATION_NOT_CONNECTED`, since a proper authenticated application pathway does not exist in this research repository.
- Emit only a pending proposal; real athlete approval remains a separate unperformed action.

These checks do **not** prove the source event's full numerical content is genuine, that an attestation reviewer truly owns a provider account, or that any resulting plan is physiologically sensible. A signed link authorizes association according to its synthetic rules, not its medical or coaching quality.

## 5. Latest verified CI results (and one caught fix)

A GitHub Actions run before the completed change correctly failed a test because the locked-day and time-budget rules returned the less specific blocker. Reordering the validation checks fixed that error. A second run correctly exposed that an empty attestation was being classified as an ID mismatch rather than missing evidence; this too was corrected.

**Final successful run 41588077190** on commit `f9d592a` reports:

| Validation lane | Verified result | Meaning |
| --- | --- | --- |
| Research Python unit, negative-control and integration tests | **109 PASS, 0 FAIL** | Includes 15 link tests, 15 workout proposal tests, 3 adapter-origin tests, 10 signed-evidence-to-proposal tests and 66 previous unit tests |
| Older tiny toy contract model | **58 structural PASS, 0 FAIL** | Separate toy implementation, not Android production |
| New research B0/H1 fixed cases | **55 structural PASS, 0 FAIL, 3 predictive BLOCKED** | The 3 forecast cases still have no calibrated model |
| Seeded adversarial research cases | **288 PASS, 0 FAIL** | New synthetic examples, not external athlete data |
| Eight-week synthetic histories | **4 profiles PASS, 32 athlete-weeks** | Plan accounting, source replay and audit; fixture-defined plans |
| Original local Python Athlete Simulation Lab | **BLOCKED_SOURCE_NOT_CONNECTED** | No source/manifest/runner was accessible |
| Android production coaching implementation | **OUT_OF_SCOPE_NOT_TESTED** | Research PR contains no production integration |

**These are different, sometimes overlapping test lanes. They are not additive proof of real-coaching effectiveness.** The original registered E002 Runna mobile generator remains 0/16, and controlled paired Garmin Run Coach outputs are still absent.

## 6. Readiness and remaining work

**Batch 8E status: COMPLETE for isolated research guards.** These are concrete improvements in *software decision boundaries*, not evidence of a trained or validated coaching algorithm.

**Still blocked or unavailable:**
- The existing Python Athlete Simulation Lab's actual versioned policy source, tests, schema, fixture manifest and independent comparator.
- Real authenticated provider ownership, live OAuth connections, signed issuer authority and secure revocation/audit persistence.
- A real athlete authentication/approval endpoint. The current integrated proposal authority deliberately blocks `athlete_request` until one exists.
- A coherent multiweek progression policy that creates next-week workout prescriptions from actual completed training, with independently vetted acceptance criteria. The previous 8D sequences were fixed synthetic plans approved in fixture code, not H1-generated training.
- An exercise substitution candidate library with validated progression/feasibility and a measurable time-proximity/dose-based recovery model. H1's binary overlap flag is not an individualized prescription.
- Calibrated personal workout forecasts; longitudinal real-athlete measures and prospective follow-up; implementation-specific Android Room crash/migration tests.

### Recommended next batch: 8F — source-of-truth adapter and outcome specification

**Stop expanding synthetic PASS counts as a proxy for engineering readiness.** Obtain the real original lab's source through an authorized private repository/attachment if available, inspect its schemas/runner and frozen read-only source manifest, and build a minimal adapter with explicit policy provenance. If not available, draft an implementation-neutral outcome/fixture contract with *external/held-out* metrics and preserve the lab as BLOCKED; never fabricate local outputs.

A future workout progression component should first have **explicit, auditable non-clinical acceptance criteria** and user approval paths. It must explain what was proposed in hard-work seconds, easy minutes, long-run km and per-exercise strength/skill dose, and expose uncertainty and limitations. Only authorized real longitudinal observations could begin evaluating speed at matched easy HR and threshold progression.

Research PR #34 must remain **draft and unmerged**. Android Engine 1.0/1.5, Trackside PR #36, local lab and protected athlete data remain unchanged. Existing daily 09:00 Manila Runna/Garmin evidence automation remains active; do not duplicate.
