# Phase 4D.2 Workstream 4 — Archive Index

Verified 2026-10-25 from the user-uploaded `stridelab ws4 c.zip`, an outer ZIP containing three inner checkpoint archives. **Only the index is in GitHub; the binary ZIP files are not yet uploaded.**

| Package | SHA-256 | Integrity |
|---|---|---|
| Outer `stridelab ws4 c.zip` | `4b0839decb872d3799f0ab66e34c0a9e51da67e532ca0840f1de9846a1163799` | CRC pass; contains A, B and C |
| `StrideLab-WS4-A.zip` | `8db16f82e7cd26d1570b19b5877e9c72a03467562606fca88f22f4c8d34a4b52` | CRC pass; 511/511 checkpoint manifest hashes match |
| `StrideLab-WS4-B.zip` | `db8b4940c789ca2437a9881202e15c9069d93e6c3ea6fb44b58af1a6a2633b9d` | CRC pass; 523/523 checkpoint manifest hashes match |
| **`StrideLab-WS4-C.zip`** | **`487c9b0758cfdeb836fe9e6af6bd545395001fa3fa7aa4635f4a9daf04e8ad44`** | **CRC pass; 540/540 checkpoint manifest hashes match** |

WS4-C:
- Candidate HTML SHA-256: `f565a67241c66717d0495d276e583abaf5a535518fab16fcd5628b559cb6f753`
- Protected 4D.1 HTML SHA-256: `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d`
- Protected engine SHA-256: `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2`
- Rebuild command: `python tools/build_p4d2.py` from the extracted `StrideLab-WS4/` directory.
- Current official suite/axe acceptance: **not tested for WS4-C**. Historical WS3-E was 221/221; do not attribute that result to WS4-C.
- Claimed direct Chromium responsiveness: 60 screen states, zero findings after 13 clipped-label fixes. Not independently rerun due to sandbox browser policy and unavailable npm dependencies.

Next: run the packaged official Playwright suite with `npm ci` and Chromium on a Windows/network-enabled environment; migrate obsolete changed-control selectors while retaining assertions; run axe and reflow audits; deliver a separately named WS4-D only when evidence supports acceptance.

Do not publish the private project or silently modify the original `Isshhin/stride-lab` repository.

## WS4-C upload destination

WS4-C is too large for GitHub's browser repository upload. Upload `StrideLab-WS4-C.zip` as an asset in a **private GitHub pre-release** tagged `ws4-c`. The official test workflow downloads it from that release and verifies the checksum. No ZIP binary is currently committed to the Git repository by this index.
