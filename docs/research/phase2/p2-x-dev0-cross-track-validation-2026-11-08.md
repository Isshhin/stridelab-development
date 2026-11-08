# P2-X DEV-0 — Cross-track synthetic integration and validation checkpoint

Date: 2026-11-08 (Asia/Manila)
Branch: `research/p2x-integration-20261108`
Scope: **synthetic-only software conformance**, not real athlete intelligence or production coaching. This additive branch descends from the frozen PR #34 research checkpoint through the isolated P2-A, P2-C, P2-D and P2-E DEV-0 branches. PR #34 itself is unchanged, open, draft and unmerged.

## Four implementation batches

### Batch 1 — Cross-track contract audit

`research/validation/p2x_integration.py` replays all four synthetic research tracks at a single explicitly scoped athlete and decision-time cutoff.

- Checks athlete scope, offset-aware decision-time equality (including equivalent time zones), same declared opportunity for P2-D/P2-E, same plan version for P2-C/P2-D/P2-E, and matching P2-D/P2-E plan ID.
- Rejects incorrectly scoped snapshots, version or plan mismatches, future-outcome labels, malformed timestamps and changed-future availability.
- Preserves independent evidence IDs for each track; identical ID strings from different tracks are **not** deduplicated or treated as authenticated identity.
- Does not mutate any input plan, attach permission to a source, grant an athlete approval or interpret descriptive P2-A fitness state as a trained model.
- **Open lineage defect:** P2-C's current snapshot has only a plan version, not a plan ID. The adapter *always* returns `p2c_plan_lineage=UNVERIFIED_NO_P2C_PLAN_ID` and `cross_track_source_linkage=NOT_AUTHENTICATED`. Matching strings are insufficient to establish real-source linkage. Adding an unaudited `plan_id` attribute to the fixture does not clear that state.

### Batch 2 — Safety and uncertainty precedence

The cross-track reviewer propagates any clinical, reported stop or symptom deferral over ordinary movement/scheduling options. Missing exposure, unresolved decision evidence and capacity conflicts remain review/defer categories. Lower-body concurrent-training overlaps remain descriptive review flags, not evidence for fixed rest windows or a universal penalty.

Every output has `safe_to_auto_apply=false`, `selected_action=null`, `plan_patch=null`, `dose_change=null`, `predictive_skill=null`, `effectiveness=null`, `clinical_clearance=false`, `applied=false`, `coaching_authority=NONE_SYNTHETIC_RESEARCH`, and `gate2=CLOSED`.

### Batch 3 — Matched-denominator synthetic comparisons

`compare(bundles)` counts exactly the same invented opportunities across all four tracks. Duplicate opportunity identities are rejected, permutations are deterministic, and abstentions are not treated as adverse outcomes. There is no model training, held-out outcome, ranked policy or claimed effectiveness.

### Batch 4 — Adversarial tests, hosted replay and checkpoint

New `research/validation/test_p2x_integration.py` contributes **44 tests**. All previous synthetic research tests were then **re-executed together**, not merely added arithmetically:

| Exact test module | Tests |
| --- | ---: |
| P2-A athlete state | 31 |
| P2-A feature comparison | 40 |
| P2-C exposure/progression | 70 |
| P2-D shadow decisions | 65 |
| P2-E multi-discipline exposure/coordination | 90 |
| P2-X cross-track integration | 44 |
| **Total** | **340** |

Results in the isolated hosted sandbox:

- Python 3.13.5, direct combined module invocation: **340/340 PASS**, 0 failures, 0 errors.
- `python -m unittest discover -s . -p 'test_*.py'` with `PYTHONHASHSEED=7`: **340/340 PASS**.
- Repeated discovery with `PYTHONHASHSEED=213`: **340/340 PASS**.
- `python -m compileall -q *.py`: PASS.
- All twelve previously committed P2-A/C/D/E source/test files in the hosted sandbox were checked against their exact Git blob SHA-1 identities. The new P2-X source and test file bytes also match Git blobs `df8ea9e189ae2ada60f3d2f2ad309e7bfa86dac6` and `f3d2dfaa1591f13c5dd5e382fa43955930e44789`. No athlete data was accessed.

**GitHub Actions CI exception:** Dedicated GitHub-hosted Linux workflow `.github/workflows/research-p2x-isolated-ubuntu.yml` was created on this isolated branch, with no self-hosted runner labels. Initial [run #42476987211](https://github.com/Isshhin/stridelab-development/actions/runs/42476987211) concluded **FAILURE before any steps began**: both jobs show no assigned runner and zero executed steps; runner logs were unavailable. It is not a failed Python assertion and is **not** a successful exact-head GitHub CI run. We do not reroute it to the user's PC. The hosted sandbox results above are separately verifiable but do not substitute for an independent CI run.

The broader original research repository unit suite was not downloaded or re-executed in the hosted sandbox; only the complete P2-A/C/D/E/P2-X DEV-0 test modules (340 cases) were re-executed. No independent human/security/physiology review was conducted.

## Risk assessment and next gates

**Author-side checkpoint:** `P2X_DEV0_CROSS_TRACK_SYNTHETIC_CONFORMANCE_340_PASS`.
**Independent acceptance:** PENDING.
**GitHub-hosted exact-head CI:** NOT AVAILABLE / FAILURE BEFORE TEST STEPS.
**Source-plan lineage:** P2-C plan ID missing; P2-D/E plan ID matching only from invented fixtures.
**Production coaching authority:** NONE. **Gate 2:** CLOSED.

Next synthetic research batch should independently review the contracts and add an authenticated, versioned plan identity/observation-link schema across all tracks, with negative cases for corrected events, withdrawn source rights and stale approval fencing. Any write-capable integration requires separate athlete approval/authentication and independent review. No real-world source/athlete test may begin until lawful P2-F source admission, validation protocol and Gate 2 review are separately authorized.

No Work or Codex, no user-PC configuration or execution, and no changes to Engine 1.5, Android/Trackside, frozen Session G, P2-F custody or P2-B DEV-0. PR #34 remains draft and unmerged.
