# Operator-kit baseline verification

The verified baseline is `29b8cb547ad5101f05c4a21ecb7684fd3618f8ab`. On 2026-11-04 UTC the research branch and open, draft, unmerged PR #34 both pointed to this commit. Its sole parent was `32c9ed71e3ddaf7d77e8aba09009234f1ee1d4ea`. [Baseline commit](https://github.com/Isshhin/stridelab-development/commit/29b8cb547ad5101f05c4a21ecb7684fd3618f8ab) · [Research PR](https://github.com/Isshhin/stridelab-development/pull/34)

## Local reproduction

CPython 3.12.10 ran in an isolated Windows workspace with a writable temporary directory. The verifier matched all 53 inputs used to reconstruct the local suite against the commit's Git blob IDs. These covered validation Python sources, the synthetic frozen-policy fixture, and the four research registry CSVs. At the workspace root, run `python -B -m unittest discover -s research/validation -p "test_p2*.py" -v` for the first five rows below. Use `-p "test_*.py"` for the full suite.

| P2-F module | Discovered | Passed | Skipped |
| --- | ---: | ---: | ---: |
| `test_p2_fit_trace.py` | 10 | 10 | 0 |
| `test_p2_pilot_probe.py` | 6 | 6 | 0 |
| `test_p2_trace.py` | 16 | 16 | 0 |
| `test_p2f_linkage.py` | 22 | 22 | 0 |
| `test_p2f_linkage_recovery.py` | 11 | 11 | 0 |
| **P2-F subtotal** | **65** | **65** | **0** |
| Other research tests | 164 | 163 | 1 |
| **Full local suite** | **229** | **228** | **1** |

The full run had zero failures or errors. `test_original_lab_admission.LabAdmissionTests.test_symlink_inside_source_inventory_never_followed` skipped with `Symlinks not available in runner`. Its call to `symlink_to` must succeed before it can exercise the assertion that the original-lab inventory rejects a symlink. The local result gives no pass credit for that assertion. All 65 P2-F test methods ran.

## Scope and fixtures

The milestone changed 21 paths, comprising 11 research documents and ten Python files under `research/validation/`. The five test modules construct inputs from fabricated literals, generated TCX XML, generated FIT bytes, synthetic scope IDs and fake clocks. The private custody, participant, and audit artifacts carry fields for later private completion. Inspection found no participant answer, private identifier, signed grant, diary, or human export among the changes. The full, untruncated tree listed no `.fit`, `.tcx` or `.gpx` file. These checks cover the milestone's contents and tracked names.

The change list contains no Engine 1.5, Trackside or other production path. All six Session G architecture documents under `docs/research/` that existed at `26a8331e642c888dd73acb94755dded063baab8d` match their baseline blob IDs, including `2026-11-04-engine2x-session-g-phase2.md`.

| Input | Implemented scope verified from code and synthetic tests |
| --- | --- |
| TCX | One Running activity, one lap and one track in the tested v2 dialect. Checks include offset-bearing activity/lap identity, ordered timestamped distance, optional HR and native cadence, and present lap-summary fields. The reported lap time and the trace span stay distinct from independently corroborated timer evidence. A scoped source, grant, matching digest, independently frozen reference and corroborated instrument/marker/timer evidence gate private QC. Extensions and ambiguous structures quarantine. |
| FIT | Separate synthetic reader for selected session, record and supported timer-event fields. Tests cover CRC, session isolation, device-relative timestamps, unsupported timer groups, missing samples, native gaps and distance resets. A real FIT device audit remains outstanding. |
| GPX | Segment-count check rejects ambiguous multiple segments. Even a single GPX segment quarantines for insufficient distance and timer evidence. |
| Custody and audit | Scoped authority is consulted before reading a restored checkpoint or returning derived data. Fixtures cover aliases, corrected originals, audit pins and withdrawals. The tests exercise the research model. Encryption, filesystem ACLs, workstation restore and deletion on the future private machine require separate evidence. |

## GitHub Actions at the baseline

The workflow `.github/workflows/research-engine2-contract-validation.yml` discovers `test_*.py`. Both events target the same commit and each requests one `[self-hosted, Windows, X64]` runner. Results below apply only to the baseline commit.

- The [PR run 41810595031](https://github.com/Isshhin/stridelab-development/actions/runs/41810595031) completed successfully at 10:03:32 UTC. Job `126508693500` logged checkout of GitHub's proposed merge `989f60c`, which included the requested PR head. Discovery reported 229 tests, `OK`, and all 65 P2-F methods. The runner executed and passed the symlink test that skipped locally.
- The [push run 41810587053](https://github.com/Isshhin/stridelab-development/actions/runs/41810587053) completed successfully at 10:04:27 UTC. Job `126508538504` logged checkout of the full `29b8cb5` branch SHA, 229 tests, `OK`, all 65 P2-F methods, and a pass for the symlink case. Both jobs completed on the first attempt.

Baseline CI classification is `CI_EXACT_HEAD_PASSED`. The evidence supports the synthetic engineering and operator-preparation scope. S1 is unadmitted, the real-device comparison awaits approval and execution, Gate 2 is unsigned, and P2-B remains protocol-only.
