# Workstream 5 checkpoint archives

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
