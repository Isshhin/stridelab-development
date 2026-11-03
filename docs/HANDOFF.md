# StrideLab Handoff / Session Recovery

Updated: 3 November 2026

This is the current high-level recovery document for active development.

For product direction, read [PRODUCT_VISION.md](PRODUCT_VISION.md).

For execution order, read [ROADMAP.md](ROADMAP.md).

Historical Phase 4D/4E status files remain valid as historical evidence, but they are no longer the current development roadmap.

## Current product direction

**StrideLab**

**Train the whole athlete.**

Current mission:

> Help athletes improve performance through adaptive, evidence-based training across endurance, strength, movement, conditioning, and recovery.

The current milestone is a private November 2026 dogfood build.

Do not treat the current milestone as a public release.

## Current development priorities

Priority order:

1. finish Trackside 3.0 rendered parity
2. finish Engine 1.5 release reliability
3. integrate Trackside and Engine 1.5
4. verify the complete training loop
5. dogfood the app with real training
6. fix failures found through actual use
7. increase Engine 2.0 research effort once the daily-use path is stable

Do not expand scope ahead of this sequence without a concrete release reason.

## Active Trackside work

Branch:

`design/trackside3-figma-parity-no-build-20261103`

Last inspected head:

`0dee06b676a0aca8545843429357f0b7cfe0e1b5`

Latest known state:

- native plan-builder reconstruction substantially implemented
- Strength Setup implemented
- Calisthenics weekly setup implemented
- equipment selection implemented
- availability interaction fixes implemented
- screenshot and responsive QA infrastructure added
- UI foundation checks at `0bfa1c1` passed
- emulator smoke tests at `0bfa1c1` passed
- exact-head responsive QA at `0dee06b` was still running at this handoff

Next Trackside gate:

**inspect the rendered Android screenshots and compare them against the approved Figma before making visual acceptance claims.**

Required viewport checks:

- 360 × 800dp at 100%
- 412 × 950dp at 100%
- 360 × 800dp at 130%
- 412 × 950dp at 150%

Use 100% captures for Figma parity.

Use larger-font captures for accessibility/layout robustness.

Do not declare a screen 1:1 from Compose source inspection alone.

## Active Engine 1.5 work

Branch:

`feature/engine15-versioned-foundation-20261103`

Draft PR:

[PR #37](https://github.com/Isshhin/stridelab-development/pull/37)

Last inspected head:

`1d673bb5d63acc118dde6f24d666490a1bb1779a`

Known reliable checkpoint before the current head:

`5979b7af38cea88b6694cc85241e8e3d0d0a7f31`

At `5979b7a`:

- main Engine 1.5 verification passed
- Android device verification passed

Newer commits added test-first availability-reconciliation work.

The current head `1d673bb` implements safe deterministic relocation of only sessions affected by availability changes.

At this handoff, exact-head verification for `1d673bb` was still running.

Do not call that availability batch complete until both the normal and Android device workflows are green.

Engine 1.5 must remain:

- draft
- unmerged
- disabled in Trackside until integration is explicitly tested

Do not turn Engine 1.5 into Engine 2.0.

## Active Engine 2.0 research

Branch:

`research/runna-garmin-coaching-20261102`

Draft PR:

[PR #34](https://github.com/Isshhin/stridelab-development/pull/34)

Last inspected head:

`b5e82ff6f1912576167e075dee73e0fb2ee9a7d4`

Latest inspected research verification was green.

The research branch studies coaching behavior and validation separately from the release engine.

Do not move research behavior into production merely because a research test passes.

## Trackside source of truth

Approved Figma:

https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE

During the current reconstruction:

- use Figma as the visual source of truth
- obtain rendered Android screenshots
- compare source and implementation
- fix P0/P1/P2 mismatches
- rerender
- verify again

The current Figma is not assumed to be the permanent final design.

Future redesign should be informed by dogfooding.

## Engine 1.5 release rules

Before marking a major Engine 1.5 batch complete:

1. verify the exact remote head
2. inspect all relevant CI
3. run/inspect JVM and Android unit verification
4. run/inspect device verification when persistence/runtime behavior changes
5. preserve Engine 1.0 compatibility
6. fail closed on stale or invalid state
7. keep PR #37 draft/unmerged until integration readiness is explicitly reviewed

Do not weaken safety tests to increase scheduling success.

## End-to-end dogfood target

The minimum trustworthy workflow is:

1. open app
2. profile/configure athlete
3. choose goals
4. choose availability
5. generate plan
6. review plan
7. edit placements
8. save
9. open Today
10. perform/import workout
11. connect completed activity to planned work
12. update future plan safely
13. close/reopen offline
14. find the same trustworthy state

If that loop fails, fix it before expanding product scope.

## Development rules

- Verify branch heads before continuing.
- Inspect newer commits instead of assuming a handoff is current.
- Do not mix Trackside, Engine 1.5, and Engine 2.0 changes in one branch.
- Do not create a release APK unless explicitly assigned.
- Do not claim CI, device, or visual verification without inspecting the evidence.
- Preserve user edits, completed training, and session identity.
- Avoid silent data/session loss.
- Prefer root-cause debugging over speculative fixes.
- Keep canonical docs current when a milestone materially changes.

## Historical recovery documents

Older development checkpoints are preserved separately.

See:

- [PHASE4D3A_STATUS.md](PHASE4D3A_STATUS.md)
- [PHASE4D_FINAL_STATUS.md](PHASE4D_FINAL_STATUS.md)
- [PHASE4E_STATUS.md](PHASE4E_STATUS.md)
- [PHASE4E-FIX1-CHECKPOINT.md](PHASE4E-FIX1-CHECKPOINT.md)
- [WS4_STATUS.md](WS4_STATUS.md)
- [WS5_STATUS.md](WS5_STATUS.md)

Do not use historical phase banners as the current roadmap when this handoff and ROADMAP.md provide newer direction.
