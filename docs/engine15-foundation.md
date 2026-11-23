# Engine 1.5: Batch A architecture checkpoint

Source baseline: pre-Trackside Android integration PR #32, commit `5515709b15499f5bc41b0bb3dae7a54667ca8dfb`.
Branch: `feature/engine15-versioned-foundation-20261103`. This workstream does not edit Trackside 3.0 UI or the research-only Engine 2.0.

## Scope of this checkpoint

- Introduce an explicitly selected `engine15` policy with exact version `1.5.0` and stable policy ID `stridelab.engine15.foundation-20261103.1`.
- Preserve the original Engine 1.0 generator, coordinated policy, multi-session policy, and saved legacy plans.
- Reuse the existing PlanRequest/PlanSnapshot schema version 1; no migration or user-plan regeneration is required.
- Recognize exactly supported version/policy pairs in saved-plan decode, management, backup, and log paths. Preserve unknown policy snapshots as opaque, read-only data.
- Keep Engine 1.5 hidden from the Plan Builder in Batch A. A programmatic explicit opt-in can generate a versioned compatibility draft, but its prescriptions and scheduling are *identical* to the verified Engine 1.0 baseline.

## Not yet implemented

This checkpoint **does not deliver any improved coaching decisions**. Batches B and C must implement, test, and document genuine bounded progression, workload safeguards and reviewed completion-informed proposals before Engine 1.5 can be promoted for general use. Do not market the foundation as a finished coaching engine.

## Gates before integration

1. Compile Android, execute JVM engine tests, and run protected-source SHA validation on this branch. Fix confirmed errors on this branch only.
2. Verify versioned snapshot roundtrip, account isolation, unknown policy opacity, backup compatibility and unchanged Engine 1.0 fallback.
3. Review deterministic running/multi-discipline examples without treating synthetic fixtures as real athlete-response evidence.
4. Only after subsequent batches and tests, open an explicit integration review against Trackside when its owner declares the UI branch ready. Never merge or overwrite PR #36 as part of this workstream.
