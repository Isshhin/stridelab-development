# WS4-D candidate checkpoint inventory

Recorded and independently verified from user-uploaded `ws4 d.zip` on 2026-10-25. This file is a manifest/index; the binary ZIPs are **not committed to GitHub**. To run official CI, upload the extracted candidate asset to a **private prerelease** tagged `ws4-d-candidate` with the exact filename `StrideLab-WS4-D-candidate.zip`.

| Archive | Bytes | SHA-256 |
|---|---:|---|
| Outer `ws4 d.zip` (contains WIP and candidate) | 36,819,471 | `abe4ce1e1211f450c81c68147fac0b6a0f278b9260dcf2a4648deeeebeb59feb` |
| `StrideLab-WS4-D-WIP.zip` | 18,428,404 | `0f0d03223fd207bba1bd382e4d77a7e3895d1272432ad70fa065c3fb18d58507` |
| **`StrideLab-WS4-D-candidate.zip`** | **19,002,816** | **`885eaac1a9ccb399a0fa0f81d530f6ba1133444d4e7d7710200b4ea6c9d3a972`** |

Both inner ZIPs CRC-checked successfully. WIP SHA256 manifest verified 541/541 payloads; candidate manifest verified **556/556**.

Candidate reconstructed HTML SHA-256: `23717f3a7a6cd1a16ff9a1c7669026c4818b522d9632474a3dc2de15b2fe9b85`.
Protected Phase 4D.1 baseline SHA-256: `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d`.
Protected engine raw SHA-256: `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2`.

The candidate was extracted, rebuilt with `python3 tools/build_p4d2.py`, and verified using `python3 tools/verify_ws3_integrity.py`; both scripts completed successfully and reproduced the recorded hashes.

**Official D regression verification is outstanding.** The prior 221/221 result applies to the WS4-C release only. The new D candidate adds seven official Playwright tests; the separate workflow `.github/workflows/ws4-d-playwright.yml` requires **228 passing** with no failures/skips/flaky tests. Do not publish a WS4-D final acceptance report until that run is completed, classified, and manual caveats are recorded.

Source continuity notes and candidate report are inside the candidate ZIP, notably `StrideLab-WS4/ws4/WS4-D-CANDIDATE-REPORT.md`.
