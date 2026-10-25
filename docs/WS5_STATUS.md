# StrideLab Phase 4D.2 — Workstream 5 status

WS5-B Fix2 is the current candidate (NOT accepted): GitHub Actions fix1 run 40209861909 was 235/237 (2 stale test assertions, 0 axe failures). Fix2 changes tests only, preserving identical HTML/engine. ZIP SHA256 `9c2fe6f720f99f79f79bdc84752ecd30f55e9413d0746ed3fbdf9cf1c70d3940`; 578/578 manifest passed. To verify, upload it to private prerelease `ws5-b-fix2`, run [WS5-B Fix2 Actions](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-b-fix2-playwright.yml). Do not start WS5-C until official 237/237, and do not label WS5 final before WS5-D.

**WS5-B fix1 is now packaged by ChatGPT but NOT accepted.** The failed WS5-B run [40208080911](https://github.com/Isshhin/stridelab-development/actions/runs/40208080911) completed at 224/237. The targeted repair ZIP is `StrideLab-WS5-B-fix1.zip` (SHA-256 `22d76013694dde4d722f3b82e930101f9a7a3df97a15285b6cfddafd0957f448`, 576/576 manifest). The [WS5-B fix1 official CI workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-b-fix1-playwright.yml) is ready but needs a private prerelease tag `ws5-b-fix1` and the exact ZIP asset to be uploaded, then manually dispatched. Preserve 228/228 WS4-D as the last independently accepted baseline. Never interpret local checks as official WS5-B success.

**Current:** WS5-A audit checkpoint completed; WS5-B functional candidate delivered but **not yet accepted**.

**Last independently accepted baseline:** WS4-D, official GitHub Actions [40205353927](https://github.com/Isshhin/stridelab-development/actions/runs/40205353927): 228 passed, zero failed/skipped/flaky.

## WS5-B changes (from the candidate source/notes)

- Library uses 23 existing entries (10 strength, 8 calisthenics, 5 running), with no new exercise IDs.
- Image-fit disclosures: matches this movement, progression end point, related diagram or original schematic. No claim that related diagrams are exact demonstrations.
- Running cards switch from generic crowd photo to a session thumbnail; sheets show warm-up, main, cooldown times/efforts rather than gym-style sets or load advice.
- Derived metadata: muscles / running focus, movement pattern and equipment, plus pattern filter and search by muscles. Only new persisted UI key: `S.libPat`.
- Primary Practice/Save actions moved to top of detail sheet; existing 5 sections preserved.
- Ten reviewed progression links, including explicit equipment-step distinctions; related alternatives explain pattern, muscle, level, equipment and filtered-out count.
- Existing Everkinetic CC BY-SA diagrams retained with attribution; no exclusive/original mascot created.
- Coverage expansion (running drills, strides, carries, calisthenics progressions, actual image replacements) deferred to WS5-C.

## Evidence

- Uploaded WS5-B ZIP SHA-256: `7d907c3800724930aee684a70292b665d4c4d03e8139989605c5b365434ca81c`.
- Local ZIP CRC and 574/574 SHA256 manifest checked independently, deterministic rebuild and coaching-engine/protected-baseline verification passed.
- Claude's reported 203 Python Playwright checks are **focused probes, not official suite acceptance**.
- New WS5 Playwright spec defines **nine cases** (six named tests plus three width parameterizations), making the expected official count **237** if inherited 228 remain.
- Changed Library spec and new WS5 spec pass JavaScript syntax checks.
- The first official WS5-B Playwright/axe run is pending. See the manual [WS5-B Actions workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-b-playwright.yml).
- Authored muscle metadata should be professionally reviewed before treating it as authoritative exercise guidance.

## Next

Upload `StrideLab-WS5-B.zip` to a private GitHub pre-release tagged `ws5-b-candidate`. Trigger the dedicated Actions workflow, inspect official failures if any, and only accept WS5-B after passing and recording caveats. WS5-C adds exercise coverage/progression paths and additional accessibility checks while preserving all protected baselines.
