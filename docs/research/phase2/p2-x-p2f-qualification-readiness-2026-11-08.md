# StrideLab Engine 2.x — P2-X/P2-F Qualification Readiness Register

Date: 2026-11-08 (Asia/Manila)
Source baseline: `6142d7a8f8976f9ea4647850ff9e6ea51ff289df`
Isolated branch: `research/p2x-qualification-readiness-20261108`
Status: **READINESS GAP REGISTER / RESEARCH ONLY. NOT A SECURITY CERTIFICATION OR GATE 2 ACCEPTANCE.**

This register was assembled through connected GitHub source inspection in ordinary ChatGPT. It does not use Codex, Work, self-hosted Windows CI, live Garmin/provider/athlete data or user workstation resources. It makes no executable modification to the frozen research protocol or Android/Engine 1.5.

## 1. Evidence hierarchy and corrected P2-F status

**Critical status precedence:** The normative `docs/research/phase2/p2-f-custody-authority-v7-correction-closure-2026-11-07.md` opens with the *historical author-side* line `P2F_CUSTODY_AUTHORITY_V7_CORRECTIONS_INCOMPLETE`. That line is not the latest independent acceptance verdict. The newer:
- `docs/research/phase2/p2-f-custody-authority-v7-historical-key-role-independent-review-2026-11-07.md`
- `docs/research/handoffs/engine2x-current-handoff.md`
- `docs/research/handoffs/engine2x-p2f-current-session-handoff.md`

record a separately scoped historical role-key independent review on exact head `4f3805f84ba9fb5828eb94d513be643fc37cf9c9` and verdict **`P2F_CUSTODY_AUTHORITY_V7_DESIGN_ACCEPTABLE_FOR_PREFLIGHT_AUTHORIZATION_REVIEW`**. This closes the earlier F1a/F1b role-key design/vector defect for the **reviewed head only** and permits a **new, separate, read-only preflight authorization review**. The correction design and its verified static frozen packet are NOT permission to run the collector, inspect a target workstation, perform TPM/TBS operations, provision operational keys, process private data, admit participants, activate a pilot or open Gate 2. No current external human/organizational certification or operational witnessed custody is claimed.

The v7 correction closure is still the sole normative v7 schema/validator source. Historical canonical v1 and earlier proposals are superseded for protocol conformance. This readiness register has no normative authority over v7.

## 2. Actual P2-X software checkpoint

The isolated P2-X synthetic branch `research/p2x-integrity-wave4-20261108` at SHA `6142d7a8f8976f9ea4647850ff9e6ea51ff289df` contains:
- P2-A athlete state; P2-C progression; P2-D shadow adaptation; P2-E multi-discipline accounting; P2-X lineage, provenance and joined-contract checks.
- 481 combined author-side Python synthetic tests reported PASS twice under different hash seeds, with exact tested Git blobs recorded. Not an exact-head GitHub-hosted CI result or real-life effectiveness measure.
- No endorsed training dose, auto plan edit, recovery prediction, clinical clearance, authenticated athlete approval, provider custody, real-world data admission or production Engine 2.x activation.
- P2-X `assess` explicitly returns `identity_claim=CALLER_ASSERTED_SYNTHETIC_ONLY`, `source_authentication=NOT_AUTHENTICATED`, `safe_to_auto_apply=false`, `can_write_plan=false`, and `gate2=CLOSED`. P2-C identity is a mechanical copy-on-write synthetic migration, not a validated plan-authority certificate.

## 3. Gap register, with owners and acceptance evidence

| ID | Gate | Missing evidence or condition | Next authorized owner/action | Definition of closure |
|---|---|---|---|---|
| Q1 | P2-X independent software qualification | No separately staffed / external exact-head conformance review of the entire P2-A/C/D/E/X research descendant branch | Independent reviewer, or another distinctly scoped review process that reports its limits | Exact SHA, novel independent counterexamples, recorded PASS/CORRECTION_REQUIRED/INCOMPLETE_EVIDENCE verdict, and all findings disposed |
| Q2 | Hosted CI execution | Three isolated research GitHub-hosted jobs failed **before any job steps**; no runner name, rejection reason or usable job logs exposed by connector. Another Android workflow succeeded in the same repo | Repository owner/admin checks run banner, repository Actions policy, Actions minutes/budget/payment controls and support diagnosis; then permitted non-PC execution | Exact branch SHA executes on GitHub-hosted Linux with full logs, runner identity and tests; no self-hosted Windows fallback |
| Q3 | Plan authority | P2-X uses caller-supplied plan/revision digests; P2-C migration cannot establish original authenticated plan lineage | Separate plan-authority designer/reviewer working alongside P2-F constraints, no implementation of v7 implied | Independently trusted canonical plan namespace/revision/finality proof, anti-replay/rollback and all-track native binding; independently reviewed |
| Q4 | Source authority | Synthetic source IDs/event hashes are self-consistent but unauthenticated | Designated source-rights/custody steward and independently reviewed P2-F protocol authority | Lawful file-level rights and privacy permissions, source acquisition provenance, withdrawal/expiry controls and verified admission evidence |
| Q5 | P2-F preflight | Bounded v7 design review permits only a **separate authorization review**, not target use | Separately scoped authorized reviewer, no collector/TPM/target action during review | Explicit review decision approving or denying precise read-only preflight boundaries; no inference of execution permission |
| Q6 | Device/data qualification | No approved real Garmin/FIT evidence and prospectively qualified decoder/device/availability history for research | Authorized device/decoder and data steward teams, after admission | Provenance-controlled fixture qualification, declared error model and rights; gate-by-gate approval |
| Q7 | Physiological/coaching validity | Synthetic conformance cannot establish adaptive-coaching effectiveness, injury/recovery prediction or training outcomes | Qualified training methodology / research reviewers with ethics and prospective protocol approval | Independently defined endpoints, data quality criteria, negative controls, uncertainty, and study results |
| Q8 | Athlete plan write | No signed/verified current athlete approval or conditional commit authority in synthetic reviewer | Product/security approval designers **after** custody and safety gates | Authenticated current approval with replay/withdrawal fences, non-automatic authority, adversarial tests and recorded approval; not granted now |

**Global decision:** `P2X_P2F_READINESS_REGISTER_COMPLETE_RESEARCH_ONLY`. Independent foundation acceptance **PENDING**. P2-F preflight **NOT AUTHORIZED**. Gate 2 **CLOSED**. Production Engine 2.x **NOT APPROVED**.

## 4. CI evidence and diagnosis boundaries

Research runs:
- [#42476987211](https://github.com/Isshhin/stridelab-development/actions/runs/42476987211)
- [#42481627243](https://github.com/Isshhin/stridelab-development/actions/runs/42481627243)
- [#42482594419](https://github.com/Isshhin/stridelab-development/actions/runs/42482594419)

Each ended without a reported assigned runner or a logged test step. The job-steps API returned an empty list for the most recent run, and redirected log retrieval produced an inaccessible blob error. No source-test assertion is established.

Counterexample to a blanket Actions outage: the same repository's separate [Android verification workflow #42486561531](https://github.com/Isshhin/stridelab-development/actions/runs/42486561531) completed SUCCESS. Private-repository Actions billing, usage budgets or policy are hypotheses, **not diagnosed facts**. Administrative budget and permission controls were not accessible through ordinary GitHub connector fetch; do not change billing or add spend without owner authorization. See [GitHub's billing explanation](https://docs.github.com/en/actions/concepts/billing-and-usage) and [workflow troubleshooting](https://docs.github.com/en/actions/how-tos/troubleshoot-workflows).

## 5. Execution handoff, in safe order

1. Repository admin reviews **one** failing research run's failure banner and Actions/budget/policy settings; collect exact rejection. Do not trigger repeated jobs on speculation. If unresolved, file a GitHub Support ticket referencing the three failed research run IDs and successful Android counterexample.
2. Independent reviewer evaluates the **exact P2-X candidate SHA** and the evidence packet `docs/research/phase2/p2-x-independent-review-packet-2026-11-08.md`. Reviewer writes novel tests and a traceable verdict; internal author cannot self-certify.
3. When approved GitHub-hosted runners are available, execute the full eight-module P2-X synthetic suite at **exact candidate SHA** with at least two deterministic replay seeds; capture runner image, source hashes and full logs.
4. Separately review the existing frozen P2-F read-only preflight authorization scope without executing the collector or accessing target hardware. This **must not be combined** with a P2-X source-custody or production activation decision.
5. Once source rights, device quality, operational custody and protocol authorization gates are independently accepted, propose (not execute) a bounded real-data qualification study. Gate 2 remains closed unless specifically opened by the authorized owner/reviewer.

## 6. In-chat versus execution-only work

**Ordinary ChatGPT with connected GitHub:** read source/docs/PR state, compare protocol and schema boundaries, author risk and architecture records, prepare independent-review packets and test vectors, draft synthetic code, create isolated GitHub commits, inspect exposed workflow metadata, and reason about failures. It can run limited hosted Python if source/test bytes are available. None of that creates independent human acceptance.

**Work/Codex/real execution environment:** clone/check out and run *full private repository* tests at an exact head, instrument/debug test failures, inspect/modify the Android source tree, build/install APKs and run emulator/UI integration checks, run and instrument a local authenticated test runner, compare generated FIT/GPX decoder outputs, navigate repo/admin UI where connected permissions allow, and produce full build artifacts. Work/Codex **do not** grant private-data rights or security approval merely by executing a task.

**Human/owner/organizational authority regardless of tool:** billing/permissions decisions, external independent audit signoff, lawful participant/source consent, ethics and pilot authorization, target-device operational permission, production release approval, opening Gate 2.

No original PR #34 change, merge, source admission, production change, workstation access, self-hosted Windows CI, Codex or Work was performed while preparing this register.
