# P2-X GitHub-hosted CI — Execution-Start Investigation

Date: 2026-11-08 (Asia/Manila)
Conclusion: **UNRESOLVED. No authoritative runner rejection or billing annotation was accessible through the available connector.**

## Observed repository evidence

Three isolated research workflows failed very shortly after creation, with no assigned runner name and no executed step list returned:

- [P2-X original isolated CI #42476987211](https://github.com/Isshhin/stridelab-development/actions/runs/42476987211) — two GitHub-hosted Ubuntu jobs failed before steps.
- [P2-X DEV-2 CI #42481627243](https://github.com/Isshhin/stridelab-development/actions/runs/42481627243) — GitHub-hosted Ubuntu job failed before steps.
- [P2-X qualification CI #42482594419](https://github.com/Isshhin/stridelab-development/actions/runs/42482594419) — one `ubuntu-24.04` job failed before steps.

The attempted repository Actions permission and job-inspection endpoints were not accessible through this connector's allowed fetch endpoints; no root-cause annotation or billing record was obtained. GitHub job-log retrieval did not expose useful steps from the failed-before-start cases.

A separate [StrideLab isolated Android verification run #42477158270](https://github.com/Isshhin/stridelab-development/actions/runs/42477158270) succeeded within the same period, showing that a blanket claim that *all* Actions workflows were broken would be inaccurate. Whether the owner's remaining private-repository hosted-runner allowance was exhausted after other jobs, or a policy/approval rule applied specifically to new research workflows, cannot be determined from the exposed metadata alone.

The new P2-X roster branch deliberately has no push-triggered workflow, avoiding another known-unresolved immediate-failure job. The exact-head GitHub CI status is **NOT PASS**; two controlled hosted sandbox runs prove only their own test environment.

## Documented diagnostic branches

GitHub official documentation:
- [Actions troubleshooting](https://docs.github.com/en/actions/how-tos/troubleshoot-workflows)
- [Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [Billing and budgets](https://docs.github.com/en/billing/concepts/budgets-and-alerts)

When an account owner/admin has access, inspect the **run's annotation or failure banner first**. If it identifies spending, check the repository owner's Actions monthly included usage, active spending budgets, stop-usage limit and payment status. If permissions or approval are shown, check repository Settings → Actions → General. If neither applies, provide the exact workflow/run/job URLs and 0-step evidence to GitHub Support.

Do not diagnose exhausted billing or GitHub-wide outages as fact without the corresponding account evidence. Do not automatically increase spending or change billing on the user's behalf. Never change `runs-on` to the user's Windows self-hosted runner as a workaround. No user-PC actions are needed for this research checkpoint.

## Conditions for closing the blocker

1. Capture a specific GitHub job rejection reason or account Actions setting/billing evidence.
2. Correct only the approved issue, by authorized account owner or GitHub, preserving cost controls.
3. Execute the **exact new commit SHA** on permitted non-PC GitHub-hosted infrastructure; retain logs, runner identity and full test counts.
4. Independently review plan/source protocol limitations and keep Gate 2 CLOSED until separately approved.
