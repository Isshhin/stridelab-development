# Engine 1.5 B7 — Deterministic release scenario matrix

**Scope:** Existing Engine 1.5 release-reliability tests only. Branch `feature/engine15-versioned-foundation-20261103`, draft PR #37. No Trackside, Engine 2.0 or release APK work.

## Scenario design

- 45 explicit generated running-only combinations: frequency 2–6/week × weekly mileage 18/35/65 km × plan lengths 1/4/8 weeks. The matrix varies experienced/beginner, returning status, and quality 0/1/2 under the existing conservative input rules.
- 6 deliberately unsupported running constraints: seven weekly runs (current policy ceiling is six), insufficient easy space for two quality runs, unavailable long/quality preference, non-finite mileage and invalid policy.
- 1 Engine 1.0 exact compatibility/fallback and owner-scoped backup test.
- 4 supported mixed cases: single-session 3R+1S+1C, single-session 4R+1S+1C, multi-session 5R+2C, and interference policy 5R+2S+2C across six available weekdays.
- 4 deliberately unsupported mixed constraints: too little daily time, long/quality weekday conflict, and infeasible compatible pairing capacity.
- 1 mixed owner-bound backup and duplicate-safe restore case.
- 3 edited/reconciled cases: lock+completed log+availability relocation and persisted backup; removed long-run day requiring unassignment; locked placement rejecting unavailable day and unsafe move.

**New deterministic cases: 64** (45+6+1+4+4+1+3). These are test scenarios, not 64 independently validated athletes. They supplement the pre-existing 64-profile, 300-scenario and B3–B6 test suites, not replace them. Every case has a stable ID and input. Requested session counts, unique identity, deload/quality ceilings, daily budgets, interference, availability, history immutability, owner/backup and compatibility are checked. Seven running days are intentionally rejected by current policy.

## CI execution blocker (not a test result)

At initial B7 head `968899db457c4ca928ebe468842dd2b240e8acb1`, [cloud verification 41671677135](https://github.com/Isshhin/stridelab-development/actions/runs/41671677135) and [device verification 41671666972](https://github.com/Isshhin/stridelab-development/actions/runs/41671666972) both **failed in 2–6 seconds before any job steps were recorded**. GitHub's workflow log endpoint returned `BlobNotFound`. A single retry of the cloud run also failed before steps. The plan-only parent `11069b4` likewise failed before checkout. This does **not** establish a code or test failure and must not be counted as a passing suite. Check GitHub Actions job annotations/account runner allocation/usage in the UI before retrying; do not guess about the precise reason.

**B7 status: not verified.** Once runners execute normally, require exact-head offline/JVM, Android unit, protected hashes and Android 35 emulator checks; debug actual test failures via root-cause analysis and test-first correction. Request independent review or disclose that only self-review was possible. After B7 passes, stop adding Engine 1.5 features and move to separate Trackside integration and dogfooding review.
