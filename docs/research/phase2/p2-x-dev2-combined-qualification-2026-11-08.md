# P2-X DEV-2 — Batches 3–4 integrated regression and foundation qualification checkpoint

Date: 2026-11-08 (Asia/Manila)
Research branch: `research/p2x-dev2-plan-lineage-review-20261108`
Scope: **author-side synthetic research qualification only**, not independently accepted production coaching or real-source access.

## Batch 3 — Exact-blob hosted combined regression

All 18 source/test files for these eight research suites were compared byte-for-byte using Git blob SHA-1 against the tree of research commit `5ef07a8a744c17338d2fc5a49f4bf84db8d8e687`. Every local source/test blob matches the Git repository. They were assembled without downloading or executing any real athlete/provider/FIT data.

| Research test module | Passing tests |
| --- | ---: |
| P2-A athlete-state | 31 |
| P2-A feature availability comparison | 40 |
| P2-C progression and re-entry | 70 |
| P2-D adaptive shadow decisions | 65 |
| P2-E cross-discipline synthetic coordination | 90 |
| P2-X DEV-0 cross-track audit | 44 |
| P2-X DEV-1 receipt/withdrawal fencing | 50 |
| P2-X DEV-2 plan/evidence lineage | 50 |
| **Total** | **440** |

- Python 3.13.5, `PYTHONHASHSEED=7`, explicit 8-module unittest invocation: **440 passed / 0 failed / 0 errors**.
- Python 3.13.5, `PYTHONHASHSEED=213`, unittest discovery of the eight staged synthetic research modules: **440 passed / 0 failed / 0 errors**.
- `python -m compileall -q .`: **PASS**.
- The older full repository P2-F/foundation/legacy research suite was **not** materialized or run. This evidence covers the enumerated 440 cases only, and no forecast or athlete outcome is validated.

### GitHub-hosted CI

An isolated workflow `.github/workflows/research-p2x-dev2-ubuntu.yml` was created with `runs-on: ubuntu-latest` and a GitHub-hosted Linux environment check. It does not target the user's Windows/MARC runner. Workflow commit `3f38c874b5254c81e058ed283afef2ca24b480e0` triggered [Actions run #42481627243](https://github.com/Isshhin/stridelab-development/actions/runs/42481627243).

**Run conclusion: FAILURE BEFORE RUNNER ALLOCATION.** GitHub reports zero executed steps, no runner name, and no runner group; no Python assertions ran in GitHub Actions. The similar earlier GitHub-hosted run #42476987211 failed at the same allocation boundary. Do not falsely equate either failure with a failing source test or with successful GitHub CI. Do not reroute to self-hosted user workstation.

## Batch 4 — Foundation qualification review and release decision

Review categories:

| Requirement | Assessment |
| --- | --- |
| Synthetic A/C/D/E/X DEV-0/DEV-1/DEV-2 software behavior | **Author-side PASS** on enumerated 440 synthetic tests |
| New DEV-2 cross-track plan identity consistency | **Implemented**, caller-asserted invented fixtures only |
| P2-C native plan ID | **Copy-on-write synthetic migration helper**, no trusted planner authority |
| DEV-1 hash chain, revisions, withdrawals, shadow receipt | **Synthetic conformance**, no independent custody authority or signature |
| Clinical/stop and non-executable plan-change boundaries | **Contract-tested**, no real-world safety evidence |
| Combined exact-head GitHub Actions CI | **BLOCKED** by unavailable runner allocation |
| Separate independent technical/code/security review | **NOT COMPLETED**; author-side review is not independent acceptance |
| Legacy full research regression | **NOT RUN** in this wave |
| Real P2-F source rights, private pilot admission and Gate 2 manifest | **NOT AUTHORIZED** |
| Prospective athlete outcome prediction, utility, calibration and safety association | **NOT ESTABLISHED** |
| Engine 1.5/Android production integration | **NOT AUTHORIZED / NOT MODIFIED** |

**Verdict:** `P2X_DEV2_COMBINED_SYNTHETIC_440_PASS__FOUNDATION_ACCEPTANCE_BLOCKED`.

The code has reached a consolidated synthetic checkpoint but **must not** be labeled an independently accepted, production-ready, security-certified or physiologically validated coaching foundation. Do not merge the frozen PR #34 or any isolated research branch into production on this basis.

## Roadmap after this checkpoint

1. Obtain separately scoped independent code/conformance review of P2-A/C/D/E/X and P2-X DEV-2; correct findings on isolated branches, rerun full synthetic suite.
2. Resolve runner allocation on permitted non-user-PC infrastructure; get an exact-head CI pass with evidence. Treat run allocation as an operational blocker, not a reason to use MARC.
3. Design and independently qualify actual plan-authority and source custody, version migrations and issuance/revocation semantics. Fixture hashes and injected IDs do not replace an authenticated trust root.
4. Separately complete P2-F lawful source admission and device/reconciled evidence requirements before requesting Gate 2; no user PC access is required for current synthetic research.
5. Only after independent review, data admission and future-outcome measurements may P2-B real repeatability, P2-A athlete model prediction, P2-C/D/E coaching effectiveness and controlled production pilots proceed.

No Work, Codex, user-PC configuration or operation, TPM use, participant/provider data, Android changes, Engine 1.5 changes or P2-F admission occurred. PR #34 remains open, draft and unmerged at the review gate.
