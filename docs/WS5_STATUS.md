# StrideLab Phase 4D.2 — Workstream 5 Status

## WS5-D final Library candidate (2026-10-26)

**Implementation packaged; official acceptance pending.** Latest accepted upstream is **WS5-C Fix1**, [248/248 verified in run 40245684800](https://github.com/Isshhin/stridelab-development/actions/runs/40245684800).

- New final candidate: `StrideLab-WS5-D-candidate.zip` (20,115,804 bytes), SHA-256 `451d53c3387362b9dd17f61457197373fdd66a8399fd8c58e59d60c267de2271`; CRC and **607/607** manifest payload hashes verified.
- Earlier WIP recovery ZIP: `StrideLab-WS5-D-WIP.zip` (19,986,391 bytes), SHA-256 `bbbab26449237e39c67200da9fd74f4bfc8759635de0f1988382c11a1ec85458`. This is a pre-final snapshot, not the authoritative candidate.
- Deterministic candidate HTML SHA-256 `4de57902e2f5dfaf6b79807bbb01fcc3df540d6151ed3054ff2ff6de6d89e5fb`.
- Protected coaching engine `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2` and original 4D.1 baseline `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d` are unchanged; all assets byte-for-byte match accepted WS5-C Fix1.
- Tight final accuracy/a11y corrections: label reverse-lunge illustration **related** because it shows dumbbells while the Library movement is unloaded; guide status now says **1 matching guide** in the singular. Five new regression cases, preserving all inherited 248 tests. **Official suite target 253**.
- Local focused Chromium probe passed six viewport/text-size combinations (360/390/430px at 100% and 200%), all 21 supplemental guide details, no document/sheet horizontal overflow and no captured page errors. Local tests **do not** establish official Chromium/axe, screen reader, image rendering, physical Android or coaching-domain certification.
- All five progression pathways remain links to reviewed references, not automatic exercise prescriptions. Original 23 executable exercises, 21 reference-only guides, tracking and engine behavior retained.
- Next: upload exact ZIP as private GitHub prerelease tag `ws5-d-candidate`, then manually dispatch [WS5-D official Playwright and axe workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-d-playwright.yml); **253 passes, 0 failures/skips/flaky required** before automated Workstream 5 acceptance. Manual accessibility and professional exercise review remain documented scope limitations.


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


## Historical WS5-C Fix1 pre-verification notes (superseded by successful run)

**NOT OFFICIALLY ACCEPTED.** Prior WS5-C [GitHub Actions run 40244039919](https://github.com/Isshhin/stridelab-development/actions/runs/40244039919) executed the complete 248-test suite: **247 passed, 1 failed**. The failure was the inherited `p4d2-scrolllock.spec.js` Library-detail test, with a 177px background shift before modal lock.

Root cause reproduced in local browser: new 210px Movement and skill guides invitation was inserted *above* search and original exercise tiles. Playwright scrolled a partially offscreen first tile into view before activating the modal, thereby shifting the page before scroll lock captured its position.

Fix1 changes one functional location in `tools/p4d2_ws5c_guides.py`: **append** the new reference-only guide invitation *after* the existing Library results instead of injecting it before search. Original 23 exercise tiles and their preexisting positions are preserved. This also means the guide promotion is lower in the page; reevaluate discoverability during later visual refinement while safeguarding scroll-lock behavior. No coaching engine, modal locking code, exercise identities, storage, guide metadata, or 248 test assertions were modified.

**Fix1 archive:** `StrideLab-WS5-C-fix1.zip` (20,007,356 bytes), SHA-256 `d781ae49fcad50571f1893f414df498ee8c1588cdb581e25aebbe0dc73c49704`. CRC passed, **602/602** manifest payload hashes verified. Deterministic HTML build SHA-256 `e4e6514d935a27d5f4280fe453366f621521289ecf0fa7f2c7e286bbcc05bd7e`. Protected engine `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2` and 4D.1 baseline `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d` unchanged.

**Next:** Upload exact ZIP to private GitHub prerelease with tag `ws5-c-fix1`; then manually run the dedicated [WS5-C Fix1 official gate](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-c-fix1-playwright.yml) (248 passes required, no failures/skips/flakes). Official GitHub CI on this exact fix has **not yet run**. WS5-B Fix2 remains the last accepted checkpoint (237/237). WS5-D is still full Workstream 5 acceptance.


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
