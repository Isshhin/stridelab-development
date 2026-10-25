# StrideLab Phase 4D.2 — Workstream 5 Status

## WS5-C implementation candidate (2026-10-25)

**Delivered, NOT officially accepted.** Source baseline is the [WS5-B Fix2 release](https://github.com/Isshhin/stridelab-development/releases/tag/ws5-b-fix2), independently accepted at 237/237 in [run 40210796282](https://github.com/Isshhin/stridelab-development/actions/runs/40210796282).

- Deliverables: `StrideLab-WS5-C-WIP.zip` and `StrideLab-WS5-C-candidate.zip`, both self-contained.
- Candidate ZIP SHA-256: `91d6b79ce1ebebb9001bcc5b18070352c7752c4595330fb6f95426c741aed7fa` (19,983,629 bytes).
- ZIP CRC and all **600/600** candidate manifest file hashes passed.
- Deterministic rebuilt HTML SHA-256: `90ef7d9fd954d5927949acb4d6d3966e57dc2be856c0a950ef74d2a50ef47b90`.
- Protected engine `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2` and Phase 4D.1 baseline `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d` unchanged.
- Implemented **21 supplemental educational movement guides** with running preparation/drills, two carries, and 14 calisthenics references. Five distinct progression learning paths link steps including canonical pull-up Library IDs. Dedicated Library modal provides live search, discipline and pattern filtering, including **Carry**.
- These are **reference-only**, with no practice/save/log/plan or automatic prescription; accepted 23 trackable Library entries remain unchanged, as do their IDs and persistence. No new image assets or mascot introduced.
- Local direct Python Chromium smoke test: **129 grouped checks pass**, all 21 detail views, 360/390/430px and 100%/200% text, keyboard/Escape and zero page errors. Official Playwright/axe tests have **not yet run on WS5-C**.
- Added 11 official Playwright cases in `suite/tests/p4d2-ws5c-guides.spec.js`; expected entire suite **248** (237 inherited + 11).
- **Next:** upload exact candidate as a **private prerelease** tagged `ws5-c-candidate`, asset name `StrideLab-WS5-C-candidate.zip`. Then manually run the [dedicated WS5-C official Actions gate](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-c-playwright.yml) and record real results before proceeding to WS5-D.
- Qualified coaching review, licensed accurate technique images, screen reader and physical Android checks remain open. **Do not label full WS5 accepted before WS5-D.**


**Current: WS5-B Fix2 accepted as an intermediate verified checkpoint; WS5-C next. Full WS5 acceptance NOT YET reached.** Updated 2026-10-25.

## Official WS5-B Fix2 verification

- GitHub Actions run: https://github.com/Isshhin/stridelab-development/actions/runs/40210796282
- **237 passed, 0 failed, 0 skipped, 0 flaky**, using official Playwright Chromium with available axe test suites.
- Release: https://github.com/Isshhin/stridelab-development/releases/tag/ws5-b-fix2
- Archive: `StrideLab-WS5-B-fix2.zip` SHA-256 `9c2fe6f720f99f79f79bdc84752ecd30f55e9413d0746ed3fbdf9cf1c70d3940`. ZIP CRC passed, 578/578 manifest hashes validated.
- Rebuilt HTML SHA-256 `868a5908dfc8b70e0ceba8066d6123cd157a19540cf4080d7b60daf041d2fad2`; builder deterministic.
- Protected coaching engine SHA-256 `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2` and Phase 4D.1 baseline `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d` unchanged.
- Fix1 and Fix2 resolved original WS5-B 13 failures: unsupported test helpers, inaccessible exercise tiles, heading order and contrast; the last two failures were corrected test assertions, not app regressions.
- The manual muscle list/domain review, licenses and specific diagram fit, real Android, screen readers and other browsers **are not certified** by this test pass.

## WS5-B functional changes preserved

- Library still has 23 exercise entries; no IDs changed.
- Exercise images disclose whether a diagram exactly matches, is a progression endpoint, is merely related, or is an original schematic.
- Running entries use session structure (warm-up, main, cooldown) instead of gym-style loads and sets.
- Derived movement-family, muscle/focus and equipment metadata; search, pattern filters, Library sheet improvements and linked progression paths.
- Retain Everkinetic attribution. No exclusive StrideLab mascot has been created.

## Next: WS5-C

1. Expand and verify gaps: strides, warm-ups, drills, carries, calisthenics ladders for rows, dips, handstands and core.
2. Require medically/exercise-scientifically sensible muscle/skill/progression metadata and proper image licensing. Avoid inaccurate instructional artwork; preserve the existing 2D visual language without copying a third-party character as an exclusive mascot.
3. Add new functionality incrementally with source ZIP checkpoints and focused regression tests. Keep protected engine, baseline, persistence, identities and accepted accessibility fixes unchanged.
4. Independently verify each candidate through GitHub Actions; **WS5-D remains the final workstream acceptance gate**.

## Historical runs

- WS4-D: [40205353927](https://github.com/Isshhin/stridelab-development/actions/runs/40205353927) 228/228 passed.
- WS5-B original: [40208080911](https://github.com/Isshhin/stridelab-development/actions/runs/40208080911) 224 passed / 13 failed.
- WS5-B fix1: [40209861909](https://github.com/Isshhin/stridelab-development/actions/runs/40209861909) 235 passed / 2 failed.
- WS5-B fix2: [40210796282](https://github.com/Isshhin/stridelab-development/actions/runs/40210796282) **237/237 passed**.
