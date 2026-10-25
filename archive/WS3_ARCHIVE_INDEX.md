# WS3 Archive Index

These records describe user-provided ZIP files independently hashed in the working session on 2026-10-25. **Listing an archive here does not mean its binary has been uploaded to GitHub.** Upload original ZIPs under `archives/ws3/` (or attach to a private release if supported). Compare checksums after upload.

| Archive | SHA-256 |
|---|---|
| StrideLab-WS3-A.zip | `853ddbe1d0ba5fc19e57cbe63dc83f2f0eb3089b30cfc80a1e178457eceb751d` |
| StrideLab-WS3-B.zip | `c3fb47af93cbe509df18e1ba52e6fb0adabd23fa9e6f1da2133a56ef702de12b` |
| StrideLab-WS3-C.zip | `99bf407f2ff5e2622c320ec186570e4ec409f213c0acd3be49479b89d1e2036b` |
| StrideLab-WS3-D.zip | `2f4790b3b8ca7d627d83b877f80694ebb8b832d72e4c1fdca99b0d3d29f3f302` |
| StrideLab-WS3-D-recovery.zip | `97a22c69236b14da1d395d45a823608f7b884445142a3600939619e979b5c53d` |
| **StrideLab-WS3-E.zip (authoritative)** | **`4afde2aa145031244a73926bc24db34b9571065c27a1411e3a9bd731f4d78671`** |
| StrideLab-WS3-E-recovery.zip | `751cadc4e4b5f57b8033ebbe832550d59a125cce54433147d00c46213857fd09` |

The additional `StrideLab-WS3-D(1).zip` is byte-identical to WS3-D and does not need separate storage.

Associated source evidence: `WS3-E-ACCEPTANCE-REPORT.md` and `WS3-E-TRIAGE-36.md` (provided alongside archives), plus ZIP-internal scripts, tests, assets, manifests and evidence.

GitHub private repo warning: do not change visibility. Avoid committing all large compressed ZIPs into normal Git history if a private asset archive is available. Use `archives/ws3/` as a simple fallback for these moderate-size files.
