# Batch 8M — First semantically aligned original vs B0/H1 policy comparison

**November 3, 2026 (Manila).** Private development repository, branch `research/batch8m-comparable-policy-checks-20261103` stacked on [draft Engine 2.0 PR #39](https://github.com/Isshhin/stridelab-development/pull/39) and original [draft PR #34](https://github.com/Isshhin/stridelab-development/pull/34). This is isolated Python research, **not** the Android Engine 2.0 implementation.

## What was independently executed

The original Python Lab's [Batch 8M input-semantics producer](https://github.com/Isshhin/stridelab-coaching-lab/blob/research/batch8m-capacity-semantics-20261103/src/coaching_lab/semantic_handoff_8m.py) produced a manifest of its seven **synthetic-only** original athlete states. It binds these inputs to the existing, manually verified **Batch 8L actual original-policy output** checksum. The original Lab's [Actions 41607764599](https://github.com/Isshhin/stridelab-coaching-lab/actions/runs/41607764599) ran **134/134 unit tests on Python 3.11 and 134/134 on Python 3.12**, with identical compact JSON.

**Source original 8L policy-output SHA-256:** `1e860cdcbdef28002bd62bb9966d908d711523178442a885fbefa7faf1cbf0cd`.

**Source original 8M input-semantics SHA-256:** `be24c58a8421cb0abbe31feb4e19141beae2c397774f08e06ad50d4e3419ce70`.

The existing GitHub connection was used to manually transfer the exact 8M compact JSON, **not a JS-reformatted copy**, into `research/validation/evidence/original_case_semantics_8m.json`, along with a separate producer-run review pin. Source repo was not checked out/executed here. No new cross-repository credentials, raw FIT/TCX/GPX, GPS, private activity histories or personal athlete files were used.

**Receiver:** `research/validation/capacity_semantic_comparison_8m.py`. First independently validates the pinned original Lab's 8L evidence, then the exact 8M source-side state, case IDs, original source and fixture fingerprints, computed body SHA-256s, manually reviewed pins, configured capacity classification, day availability, scheduling infeasibility and absent sensor/real-outcome fields.

**Original generator vs research policies have different output types:**
- Original lab = six-week scheduled workouts, including target versus actual allocated distances.
- B0/H1 = a snapshot evaluator returning **KEEP, DEFER or PROPOSE** decisions (not a six-week workout generator).
- Consequently, the only currently equivalent field for cross-policy validation is **boolean capacity arithmetic**, and even that requires treating an original configured synthetic capacity as a *hypothetical test number*, never a real medically verified training capacity. No full action comparisons or ranking are permitted.

## Verified results

[**Development GitHub Actions 41608513105**](https://github.com/Isshhin/stridelab-development/actions/runs/41608513105) at `8c59e84fc0b7d831a8a149ddd69ba6f88e426bb0`: **151/151 Python research tests passed**; the comparison returned `HYPOTHETICAL_CAPACITY_FLAGS_MATCH_ONLY`.

| Exact structural observation | Count |
| --- | ---: |
| Original source policy outcomes | 42 |
| Original plan outcomes explicitly infeasible | 6 |
| Generated six-week original plans | 36 |
| Original generated plan-weeks with valid distance values | 216 |
| B0 + H1 hypothetical capacity Boolean checks | 432 |
| Original scheduled weeks exceeding **synthetic configured** capacity | 3 |
| Original plans with `workload.capacity` validation errors | 3 |
| Original weeks where scheduled kilometers differ from declared weekly targets by >0.011 km | **108** |
| Whole B0/H1 action versus original full plan decisions graded | **0** |
| Real athlete outcomes measured | **0** |

**Three capacity violations** are all from the base `fixed_10_percent` policy in three intermediate-like frozen scenarios, consistent with original validator errors. The **108 target/scheduled mileage mismatches** occur across all 18 generated base-policy plans (six weeks each); the 18 generated combined-policy plans had no mismatch above this tolerance. These may be scheduler arithmetic defects or a design mismatch between 'target' and 'allocated total'; they require separate code review and must **not** be silently normalized by altering the original evidence.

**All 432 B0/H1 capacity Booleans agree with the independently derived scheduled-km > configured-cap condition**, given explicitly equal hypothetical numbers. This does not imply that both choose equivalent actions, make accurate coaching prescriptions or outperform the original generator: 0 such comparisons were valid on the present data.

Ten new negative controls prove that source digest/case tampering, false real-world claims, 'verified capacity' promotion and missing true links are rejected. They also force one of the B0 flags to the wrong value and verify that the paired consistency gate fails rather than reporting a pass.

## What we can improve with current data

The frozen outputs and synthetic fixtures are already sufficient to investigate and independently test **weekly target conservation, capped progression, calendar feasibility, incomplete-record handling, idempotent revisions and source provenance**. That is practical Engine 2.0 software engineering progress.

**They are not enough** to calibrate actual easy/threshold intensity, quantify athlete response, validate recovery-driven reductions or compare Runna/Garmin fitness improvements. The separate [Groningen observational cohort](https://doi.org/10.34894/UWU9PV) has real selected seven-day activity histories but **no verified linked StrideLab prescriptions and future outcome effects**. It cannot rescue the missing causal evidence.

## Next best work

1. Produce a **new research candidate** that conserves target weekly mileage and has explicit, athlete-safe bounded progression and scheduler infeasibility. Preserve the original broken outputs as regression fixtures; do not merge directly to Android.
2. Start a local owner-approved Batch 8K pilot with **authentic planned sessions, matching original FIT/TCX/GPX recordings and nonoverlapping follow-up observations**, keeping all raw activities off GitHub.
3. Freeze a separate plan-quality rubric with informed training/coaching review and meaningful conditional comparators, then test out-of-sample across athletes and time.
4. Consider broader volunteer cohorts after the single-owner intake/consent/privacy and data-quality process is proven.

The source original policy is unchanged; draft Lab PRs #1–#6 and draft Engine research PRs #34/#39/#40 remain unmerged. Android Engine 1.0/1.5 and Trackside untouched.
