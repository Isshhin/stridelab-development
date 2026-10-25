# Workstream 5 checkpoint archives

## WS5-C Fix1 — Official intermediate acceptance (2026-10-26)

**ACCEPTED as the latest WS5 intermediate checkpoint. Workstream 5 is NOT yet final.**

- Verified official GitHub Actions: [run 40245684800](https://github.com/Isshhin/stridelab-development/actions/runs/40245684800), completed **success**.
- Official Playwright Chromium with available axe checks: **248 passed, 0 failed, 0 skipped, 0 flaky** (3.3 min test execution).
- Exact archived source: [private WS5-C Fix1 prerelease](https://github.com/Isshhin/stridelab-development/releases/tag/ws5-c-fix1), asset `StrideLab-WS5-C-fix1.zip` (20,007,356 bytes), SHA-256 `d781ae49fcad50571f1893f414df498ee8c1588cdb581e25aebbe0dc73c49704`.
- Release asset checksum, ZIP CRC, **602/602** manifest hashes, deterministic candidate HTML rebuild and protected-source verification all passed.
- Rebuilt HTML SHA-256 `e4e6514d935a27d5f4280fe453366f621521289ecf0fa7f2c7e286bbcc05bd7e`.
- Coaching engine `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2` and protected Phase 4D.1 baseline `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d` unchanged.
- Prior WS5-C candidate failed 1/248 due to Library detail scrolling; Fix1 moved the guide invite after the preexisting exercise list, preserving the established modal scroll-lock test, without modifying assertions.
- Functionality now verified: 23 original trackable Library entries preserved; 21 reference-only educational movement guides and five progression pathways; running preparation, strength carries, calisthenics variants; no new tracked session semantics or adaptive coaching modifications.
- Remaining before **WS5-D final**: manual instructional accuracy / muscle metadata review, image attribution/fit and 2D mascot design decision, library guide discoverability, 360–430px/200% text, screen-reader/manual accessibility/Android device limitations documented. Prefer surgical corrections and evidence over another major feature expansion.

**Next milestone: WS5-D final quality and integration review, with its own immutable ZIP and independent official CI.**


## Historical WS5-C Fix1 candidate notes (superseded by successful run)

**NOT OFFICIALLY ACCEPTED.** Prior WS5-C [GitHub Actions run 40244039919](https://github.com/Isshhin/stridelab-development/actions/runs/40244039919) executed the complete 248-test suite: **247 passed, 1 failed**. The failure was the inherited `p4d2-scrolllock.spec.js` Library-detail test, with a 177px background shift before modal lock.

Root cause reproduced in local browser: new 210px Movement and skill guides invitation was inserted *above* search and original exercise tiles. Playwright scrolled a partially offscreen first tile into view before activating the modal, thereby shifting the page before scroll lock captured its position.

Fix1 changes one functional location in `tools/p4d2_ws5c_guides.py`: **append** the new reference-only guide invitation *after* the existing Library results instead of injecting it before search. Original 23 exercise tiles and their preexisting positions are preserved. This also means the guide promotion is lower in the page; reevaluate discoverability during later visual refinement while safeguarding scroll-lock behavior. No coaching engine, modal locking code, exercise identities, storage, guide metadata, or 248 test assertions were modified.

**Fix1 archive:** `StrideLab-WS5-C-fix1.zip` (20,007,356 bytes), SHA-256 `d781ae49fcad50571f1893f414df498ee8c1588cdb581e25aebbe0dc73c49704`. CRC passed, **602/602** manifest payload hashes verified. Deterministic HTML build SHA-256 `e4e6514d935a27d5f4280fe453366f621521289ecf0fa7f2c7e286bbcc05bd7e`. Protected engine `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2` and 4D.1 baseline `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d` unchanged.

**Next:** Upload exact ZIP to private GitHub prerelease with tag `ws5-c-fix1`; then manually run the dedicated [WS5-C Fix1 official gate](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-c-fix1-playwright.yml) (248 passes required, no failures/skips/flakes). Official GitHub CI on this exact fix has **not yet run**. WS5-B Fix2 remains the last accepted checkpoint (237/237). WS5-D is still full Workstream 5 acceptance.


> **Latest verified WS5 intermediate checkpoint (2026-10-25): WS5-B Fix2**, official Playwright/axe [237 passed, 0 failed, 0 skipped, 0 flaky](https://github.com/Isshhin/stridelab-development/actions/runs/40210796282). ZIP `9c2fe6f720f99f79f79bdc84752ecd30f55e9413d0746ed3fbdf9cf1c70d3940`, 578/578 manifest, protected engine and baseline checks passed. WS5-C may proceed; WS5-D remains the final WS5 gate. Historical contents below document earlier candidates and pending states *as of those checkpoints*.

Updated: 2026-10-25. **WS5-B is a candidate pending its first official Playwright/axe run**, not an accepted build.

The user-uploaded `StrideLab-WS5-B.zip` was independently inspected in the ChatGPT working environment.

| Artifact | Status / verification |
|---|---|
| `StrideLab-WS5-A.zip` | Baseline audit checkpoint; not yet cataloged by SHA here |
| `StrideLab-WS5-B.zip` | **Candidate**. SHA-256 `7d907c3800724930aee684a70292b665d4c4d03e8139989605c5b365434ca81c`; 19,646,213 bytes; ZIP CRC pass; manifest 574/574 pass |

### WS5-B integrity

- Runnable HTML SHA-256: `1e50e1a2b6841c6ffc31b112dd69af812640cda54eafde5fbe8d3a51ddf7188c`
- Protected engine raw script SHA-256: `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2`
- Protected 4D.1 baseline SHA-256: `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d`
- Builder `python3 tools/build_p4d2.py` deterministically reproduced runnable HTML; `python3 tools/verify_ws3_integrity.py` passed.
- New `suite/tests/p4d2-ws5-library.spec.js` and modified `suite/tests/phase4b3-library.spec.js` passed `node --check`. **No official browser test has been run on the candidate yet.**

### GitHub Actions acceptance gate

1. Upload the exact ZIP as a **private prerelease** asset, tag `ws5-b-candidate`, asset name `StrideLab-WS5-B.zip`.
2. Manually run `/.github/workflows/ws5-b-playwright.yml` on `main`.
3. Gate checks ZIP hash and CRC, 574 manifests, candidate rebuild and protected hashes, npm installation, Chromium, entire official Playwright/axe suite, and official result count: **237 passed (228 inherited + 9 new), zero skipped/failed/flaky**.
4. Record the run URL and any failures. Resolve real regressions and stale assertions, without suppressing tests or weakening checks.
5. Manual coaching/domain review of authored muscle data, licensing/image semantics, gradient contrast, real screen readers and physical devices are outside full suite acceptance. Keep as documented limitations.

The archival index is not a substitute for the binary release asset. Preserve the historical WS4-D baseline and evidence unchanged.

## WS5-B fix1 — ChatGPT regression hotfix candidate (2026-10-25)

- Original official WS5-B [run 40208080911](https://github.com/Isshhin/stridelab-development/actions/runs/40208080911): **224 passed, 13 failed**. Nine new spec cases called unsupported helpers; three axe checks reported detail-sheet contrast/heading failures; one inherited accessible-name test failed.
- `StrideLab-WS5-B-fix1.zip`: **19,606,574 bytes**, SHA-256 `22d76013694dde4d722f3b82e930101f9a7a3df97a15285b6cfddafd0957f448`. ZIP CRC and **576/576** manifest hashes passed.
- Rebuilt candidate HTML SHA-256 `868a5908dfc8b70e0ceba8066d6123cd157a19540cf4080d7b60daf041d2fad2`; deterministic build and protected-source checks passed.
- Protected engine `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2` and baseline `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d` unchanged.
- Repairs: new spec imports the established p3c-helpers; restore legacy informative accessible tile names; At a glance uses an h2 instead of h3; higher-specificity sheet pill styling fixes foreground/background colors.
- Local build, Node syntax and archive integrity checks passed; Chromium binary is not available locally. **Official fix1 verification pending.**
- To verify: upload `StrideLab-WS5-B-fix1.zip` as private prerelease tag `ws5-b-fix1`, then manually dispatch [dedicated WS5-B fix1 workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-b-fix1-playwright.yml), which requires **237 expected passes with zero failures/skips/flaky**. This is an intermediate WS5-B gate, not Workstream 5 final acceptance.

## WS5-B fix2 — test-only follow-up to official fix1 CI (2026-10-25)

- Official fix1 verification [40209861909](https://github.com/Isshhin/stridelab-development/actions/runs/40209861909) ran full 237-test Playwright/axe suite: **235 passed, 2 failed**; all integrity and axe checks passed.
- One failure was a stale test expectation of five `lats` search results while the persisted *pull* filter correctly limited them to four. Fix2 checks four with pull active and five after explicitly selecting All patterns, confirming `e5` enters only after disabling that filter.
- One failure was an inherited test expecting Purpose to be the first `h2` after fix1 correctly added At a glance as the first h2 to satisfy heading-order accessibility. Fix2 asserts both headings in correct order while retaining the broken-image fallback assertions.
- **Test-only candidate:** `StrideLab-WS5-B-fix2.zip` (19,628,345 bytes; SHA-256 `9c2fe6f720f99f79f79bdc84752ecd30f55e9413d0746ed3fbdf9cf1c70d3940`). ZIP CRC and **578/578** manifest payload hashes verified.
- Built HTML remains **byte-identical to fix1**: SHA-256 `868a5908dfc8b70e0ceba8066d6123cd157a19540cf4080d7b60daf041d2fad2`; protected engine and 4D.1 baseline unchanged.
- **Official fix2 verification pending.** Upload exact ZIP to private prerelease tag `ws5-b-fix2`, then manually run [Fix2 237-test gate](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-b-fix2-playwright.yml). No claim of final WS5 acceptance; that remains WS5-D.

## WS5-B fix2 official verification — accepted intermediate checkpoint

- GitHub Actions: https://github.com/Isshhin/stridelab-development/actions/runs/40210796282 — **237 passed (3.8m), 0 failed, 0 skipped, 0 flaky**, including available axe accessibility suites.
- GitHub private prerelease: https://github.com/Isshhin/stridelab-development/releases/tag/ws5-b-fix2
- Exact archive: `StrideLab-WS5-B-fix2.zip` SHA-256 `9c2fe6f720f99f79f79bdc84752ecd30f55e9413d0746ed3fbdf9cf1c70d3940` (19,628,345 bytes); CRC pass, 578/578 manifest pass.
- Deterministic HTML rebuild and protected coaching engine / Phase 4D.1 baseline checks passed.
- This verifies **WS5-B**, not full WS5; further WS5-C, WS5-D, manual mobile/screen reader and coaching-data reviews remain.

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


- Earlier WIP ZIP SHA-256: `cf797b7b928dec0f01e45ff3f41b1b5e05fd6a0035e8b307dcb4ae496c058ce7` (19,714,042 bytes). WIP is an intermediate source snapshot, not the authoritative final WS5-C candidate.
