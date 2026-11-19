# Engine 1.5 — Train Your Way mileage-target integrity (2026-11-16)

## Reproduction

A signed-out user without confirmed recent running mileage sets Train Your Way's target weekly mileage to 80 km, then generates a draft. The persisted request has `weeklyKm=0` and `targetWeeklyKm=80`. The current Engine 1.5 zero-baseline policy correctly produces short easy run/walk sessions but previously allowed them to be approved under an inherited `Improve 5K` goal without a visible target/actual distinction.

## Correction

- Train Your Way now explicitly sets its own intent and a mileage-oriented foundation *style* (not an inherited 5K race goal). Its saved plan is labeled `Train Your Way`.
- Confirmed *recent* mileage and the selected *future target* are never interchangeable. Running Ability warns when recent mileage is zero or unconfirmed.
- After generation, the Week Review shows the recent reported mileage, requested target, and the **estimated peak planned weekly mileage**. Time-only weeks are shown as **unknown**, not zero or a fabricated 80 km figure. Mixed-unit estimates remain estimates.
- For Train Your Way plans, or Engine 1.5 drafts with a zero recent baseline, an unknown projected distance or >=10% shortfall from the requested target requires an explicit decision before approval. The Finalize button is disabled, and the core `PlanOperations.approve` boundary independently rejects attempts to bypass it.
- The user can go back to Running Ability to enter actual reported recent mileage and longest run, or explicitly choose `Use foundation plan instead`. The latter creates a **new** draft labeled `Foundation · toward 80.0 km/week` (as a *future* goal, not a delivered mileage plan) and retains the original conservative prescriptions.
- Separate fields `trainYourWay` and `foundationAcknowledged` use optional JSON keys; old serialized requests default to `false`. Completed activities and existing saved approved plans are not rewritten. Switching to guided/race mode clears these flags; editing mileage clears an earlier foundation acknowledgment.
- Q2 mixed time/distance work and Q3 evidence gates remain unchanged, as do other discipline prescriptions.

## Focused acceptance

`Engine15MileageTargetIntegrityTest` covers zero-current/80-target rejection, explicit foundation rename and approval, large shortfalls with a nonzero current baseline, separate supported targets, route isolation, and request/plan serialization. No broad UI suite is authorized.

Code committed; **test execution and emulator visual acceptance are still pending**.
