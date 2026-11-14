# Engine 1.5 Batch 1 — Local Verification Record (2026-11-14)

**Status:** Local software checks pass; GitHub Actions execution and independent Gate B coaching review remain **unverified**. This is research-only; no Android production engine changes or owner approval to merge.

## Exact implementation and provenance

- Repository: `Isshhin/stridelab-development`.
- Branch: `research/engine15-roadmap-skeleton-batch1-20261114`; targets `dev`.
- Tested source commit: `580afd9670f5b91febbb728dae043a0424782df1`.
- Local harness Git blob SHA: `9905ace703e650e20c6d5fefc9d79c7df02fd663` — verified against repository.
- Local test Git blob SHA: `7446d80d3b746efccf9afec7d5624783c82247ec` — verified against repository.
- Local candidate JSON and scenario JSON Git blobs match the repository as well (`2de42e82ccbd1e5aecf5e11e86427fbb794b28cc`, `2d298ba0fbc35e1a1e72a312cfc162137517a12c`).
- Interpreter used for local replay: Python 3.13. **GitHub workflow requests Python 3.12, which has not executed on the hosted runner.**
- No real, opt-in or private athlete data used in these checks. All 16 scenarios were synthetic.

## Reproduction

From the repository root:

```sh
python -m unittest discover -s research/engine15_skeleton -p 'test_*.py' -v
python -m research.engine15_skeleton.cli \
  --cases research/engine15_skeleton/fixtures/cases.json \
  --policies research/engine15_skeleton/candidate_policies.json \
  --output reports/engine15-skeleton.json
```

**Observed locally on exact verified source:** 21/21 unit tests passed; CLI exported 32 results (16 synthetic scenarios × 2 hypothetical policies). Scenario outcomes: 22 `ok`, 6 `conflict`, 2 `needs_human_review`, and 2 `insufficient_baseline`. Feasibility is separate from structural status and must not be interpreted as demonstrated fitness adaptation.

A separate seeded, noncommitted property replay tested 3,000 additional synthetic histories with fractional capacity ceilings and 1–16 weeks. It checked no truncated normal plan, no weekly total above the configured ceiling or requested target, and no long-run distance above its share limit: **0 observed violations**. This replay is supplementary evidence, **not** a committed regression suite or physiological qualification.

## Confirmed research-harness defects fixed in successive commits

1. `cac21fb0`: infeasible configured ceiling no longer truncates the requested-week projection; emits explicit target-versus-ceiling conflict.
2. `454a5d59`: long-run allocation emits the actual binding step/target-share rule so the trace is reviewable.
3. `708f5c98`: final reporting-precision rounding cannot make emitted weekly distance or long-run share exceed a configured ceiling.
4. `580afd96`: target-weekly-mileage builds stop at the requested target; a target below the baseline is explicitly unsupported by this growth-only comparator rather than silently overshot. The optional run-duration forecast remains arithmetic only.

These fixes each have focused regression coverage in the committed unit test suite. The initial local test-copy transcription error was resolved by byte-for-byte Git blob comparison; it was never a repository bug.

## Reviewer-relevant examples (NOT prescriptions)

- **R04**, observed four-week average 60.75 km, recent two-week mean 60.5 km → conservative starting baseline **60.5 km/week**, target **80 km/week**.
- Under the hypothetical dual cap (5% relative, 2 km absolute), modeled week-12 is a scheduled recovery week at 61.2 km; arithmetic first target attainment is **2027-02-15**, outside the requested 12 weeks.
- Under the relative-only comparator (5%, no practical absolute limiter), the target first occurs **2027-01-11** and subsequent build weeks plateau at 80 km; week 12 is a scheduled recovery week at 64 km.
- **R03 / R15**: requested long-run minimum conflicts with constrained share/step; the tool reports the conflict without overriding the share rule.
- **R14**: two available days for an explicitly three-day minimum is infeasible; **R16**: no recorded or declared starting capacity produces no plan; **R12**: illness prompts human review instead of automatic progression.

Values `5%`, `2 km`, `3+1`, `35%` and `40%` in the hypothetical configurations are **not approved or validated physiological limits**. Neither a deterministic trace nor a synthetic benchmark establishes individual tolerance or injury prevention.

## Open verification and stop conditions

- GitHub Actions `Engine 1.5 research skeleton` repeatedly terminates before runner assignment (`runner_id=0`, no steps). [Latest observed run](https://github.com/Isshhin/stridelab-development/actions/runs/43583857651). No Python tests ran in GitHub Actions. Investigate runner availability, account/billing/permissions or other scheduling failure separately; the specific platform cause is **not established** by the job metadata.
- Complete GitHub CI and compare Python 3.12 behavior before merge, or record an owner-approved alternative exact-head execution on a trusted runner.
- Obtain independent full-plan **Gate B** review against the rubric precommitted before the harness. This harness is a weekly skeleton, so structured-workout dimensions remain N/A until M5a.
- Android Plan Builder full instrumentation, device QA and signed-out identity are separate milestones; **not tested or changed** here.
- PR stays draft and unmerged. No default-policy switch, owner device modification or production training claims are authorized by this record.
