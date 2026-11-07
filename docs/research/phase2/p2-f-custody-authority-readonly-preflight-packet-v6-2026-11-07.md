# P2-F Read-only Preflight Artifact Packet v6

Status: `READONLY_PREFLIGHT_PACKET_READY_FOR_INDEPENDENT_REVIEW`. The successful v7 static artifact freeze below supersedes earlier blocked identity records. The collector was NEVER EXECUTED; this packet does not authorize execution or target access.

## Frozen source

Source: `docs/research/phase2/preflight/stridelab_p2f_preflight.c`
Final built source SHA-256 (artifact bytes, CRLF): `478c2a1b7049b6a5d30d527b8d36623064346560e561f45d7852a6fc891157a6`
Committed source SHA-256 (Git blob, LF): `540674b092c0a6960400f6435facbc92806652d003f48625d68bcdb8cdf49bef`. The downloaded artifact source equals the committed blob byte-for-byte after CRLF-to-LF normalization. Earlier source hashes are superseded.
Purpose: exactly two future information calls, `RtlGetVersion` and `GetNativeSystemInfo`; console output only. No TPM/TBS, registry, network, installation, elevation, identifiers, filesystem output, child process or wrapper.

## Build and artifact identity

Actual recipe and observed toolchain identity are frozen in the v7 static artifact record below. The authorized self-hosted development build compiled and linked the collector, then inspected the PE without executing it. This establishes artifact identity only; it does not qualify a target or prove reproducible builds.

The following v7 record reports observed imports, dynamic symbol resolution, static call audit, PE identity, signature and provenance from the workflow log and downloaded artifact, rather than inferring them from the recipe.

## Future capture procedure (not authorized here)

Use a standard non-elevated Windows console. Verify the exact binary hash and target/principal assumptions. Run directly with no wrapper, redirection, screenshot, logging helper, telemetry collector or second binary. Visually observe exactly:

```text
OS_VERSION=<major>.<minor>.<build>
NATIVE_ARCH=<numeric architecture>
```

Manually transcribe those two lines. Stop immediately on any unexpected line, import, network/elevation/write/child-process/identifier behavior. Record incidental OS/AV/EDR/audit effects separately. Do not execute in this task; the artifact has not been executed on the target.

## Boundaries and threat model

One compromised/reset/rolled-back validator consumes the one-Byzantine budget; more than one invalidates quorum. Common administration/restore domains violate independence. A compromised coordinator or log administrator can suppress or DoS but honest validators reject illegal transitions. A compromised gateway admin/kernel/SYSTEM can bypass application guarantees. Stolen credentials are bounded only by the epoch/certificate/current-state rules actually proven. A compromised client does not prove human consent.

## Decision

`READONLY_PREFLIGHT_PACKET_READY_FOR_INDEPENDENT_REVIEW`. Independent v7 review has not been performed.


## v7 static-build attempt — 2026-11-07

Repaired workflow commit: `2e1f4fcb35c9f4e479ff4045e8b5d18fafdb845e7`. Manual run `42368016764` accepted the workflow and created the `build` job on explicit `windows-2025`, but GitHub did not start it because recent account payments failed or the spending limit must be increased. No runner/compiler/linker/SDK, source or binary hash, binary size, PE imports, signature/provenance, or artifact evidence exists. The collector executable was never executed. Canonical vector self-check remains previously passing; hosted static identity is unavailable.

Decision: `P2F_CUSTODY_AUTHORITY_V7_CORRECTIONS_INCOMPLETE`.

## v7 successful static artifact freeze — 2026-11-07

This record supersedes the earlier blocked build attempts; historical entries remain below/above for provenance. Starting branch head: `fd9b1acefbadc280f82ade268ced08423947753c`. Source-only correction: `449e87743c96b87caf6d78c1ea8df54a9ac2a30b` (`fix: remove CRT memset dependency from P2-F collector`). The correction removes aggregate zero initialization under `/NODEFAULTLIB`, fixes the reversal loop's double increment, and uses the supplied null-handle checks. No workflow, protocol, test-fixture, or runtime-authority code changed.

- Workflow: [research-p2f-preflight-build run 42373365386](https://github.com/Isshhin/stridelab-development/actions/runs/42373365386), attempt 1, SUCCESS; checkout/build head `449e87743c96b87caf6d78c1ea8df54a9ac2a30b`, branch/ref `research/runna-garmin-coaching-20261102`.
- Job `128405869887`, actual runner `MARC`; labels `self-hosted`, `Windows`, `X64`.
- Visual Studio 2026 Build Tools `18.11.3`; MSVC compiler `19.51.36260`; toolset `14.51.36231`; linker/COFF dumper `14.51.36260`; Windows SDK `10.0.26100.0`.
- Compile: `cl /nologo /O1 /GS- /TC /c docs/research/phase2/preflight/stridelab_p2f_preflight.c /Fo:preflight.obj`.
- Link: `link /nologo /SUBSYSTEM:CONSOLE /ENTRY:mainCRTStartup /NODEFAULTLIB /DYNAMICBASE /NXCOMPAT /MACHINE:X64 preflight.obj kernel32.lib /OUT:stridelab_p2f_preflight.exe`.
- Built source SHA-256 (CRLF, 1,821 bytes): `478c2a1b7049b6a5d30d527b8d36623064346560e561f45d7852a6fc891157a6`.
- Git source blob SHA-256 (LF): `540674b092c0a6960400f6435facbc92806652d003f48625d68bcdb8cdf49bef`; normalized artifact/source equality verified.
- Binary `stridelab_p2f_preflight.exe`: 3,072 bytes; SHA-256 `96cd9069205a891c383f23d085e1c10c0f678f4d132b9c25cb7185902af3f8dc`. Workflow hashes and independently recomputed downloaded-file hashes match.
- Artifact ID `13573973275`, name `p2f-preflight-static-artifact`; [artifact](https://github.com/Isshhin/stridelab-development/actions/runs/42373365386/artifacts/13573973275). GitHub archive digest `sha256:710b58988d8d12294def3bcb97c7fa41b0dd97e945ceac607d776962e42f0915`; this is the archive digest, not the EXE digest.

### Exact observed PE imports and call audit

One import descriptor, `KERNEL32.dll`, with exactly these six named imports in observed order:

```text
ExitProcess
GetNativeSystemInfo
GetModuleHandleW
GetProcAddress
WriteConsoleA
GetStdHandle
```

No ordinal or delay imports. `RtlGetVersion` is absent from static imports. The source and PE strings contain `ntdll.dll` and `RtlGetVersion`; disassembly shows `GetModuleHandleW` followed by `GetProcAddress`, a null check, and an indirect `call rax`. This confirms the dynamic-resolution path statically; resolution success at runtime was not tested.

The entire 510-byte `.text` disassembly contains the decimal formatter and entry routine, including inlined console writes. The only direct internal call target is the formatter; imported calls use the six-entry IAT, and the only other indirect call is the resolved version function. No `memset`, `memcpy`, CRT initialization, stack/security-cookie helper, arithmetic runtime helper, CRT DLL, or unexpected helper remains. Aggregate stack zeroing is absent. The formatter's reversal advances its left pointer once per iteration. The only information-gathering operations remain `RtlGetVersion` and `GetNativeSystemInfo`; module/symbol lookup, standard-output handling and exit are support operations.

PE: x64 PE32+, Windows console, entry RVA `0x1060`, sections `.text`, `.rdata`, `.pdata`; dynamic-base, high-entropy VA and NX flags set. No resource/manifest directory, TLS directory, load-configuration directory, delay-import directory or embedded certificate table. No embedded elevation request. Absence of a manifest does not establish the principal of any future launch; any separately authorized future capture must use a standard non-elevated console.

### Signature, provenance and verification limits

`UNSIGNED RESEARCH ARTIFACT`: Authenticode inspection returned `NotSigned`, signer certificate null, and the PE certificate directory is zero. Provenance is the Git commit, observed workflow/toolchain/job/runner, artifact ID/archive digest, source equality and matching source/binary hashes above. No signing, independent provenance attestation, toolchain-independent reproduction or runtime validation is claimed. Existing workflow action references are unchanged.

The compile and link succeeded. The unchanged metadata probe `cl /Bv` printed tool versions and diagnostic `D8003: missing source filename` because it supplies no compilation input; subsequent static inspection succeeded and the job/run concluded SUCCESS. This diagnostic is not a collector compile/link failure. No workflow correction was made.

Source regression checks passed; the existing `research/tools/p2f_v7_closure_vectors.py` regenerated its six vectors and reported its self-check passed, leaving the tracked fixture unchanged. This is implementation-side closure verification, not independent acceptance of the protocol or rejection fixtures.

Collector-fix-head research-validation run `42373362277` concluded SUCCESS. Graphify mirror run `42373358262` failed before startup with a GitHub billing/spending-limit annotation. That mirror failure is separate from the successful static artifact build and is not reported as a CI pass. The final documentation head's exact CI result must be reported separately at delivery.

### Current decisions and next permitted task

The other v7 closure artifacts remain unchanged, and the closure supplement's generator/static-build prerequisites are satisfied. Set `READONLY_PREFLIGHT_PACKET_READY_FOR_INDEPENDENT_REVIEW` and `P2F_CUSTODY_AUTHORITY_V7_READY_FOR_INDEPENDENT_REVIEW`. These are review-readiness statuses, not independent acceptance or execution permission.

Exact next permitted task: **one new independent v7 design/security review of the final branch head, including the closure supplement, canonical vectors, frozen source and this static artifact packet**. Do not perform that review in this task.

The collector was **NEVER EXECUTED** in this task or this workflow. No target qualification/access, TPM/TBS, keys, participant/provider/private data, runtime-authority deployment, Engine 1.5, Trackside, APK/release, Session G, P2-B or Gate 2 changes occurred. PR #34 remains OPEN, DRAFT, UNMERGED. Private admission remains NO; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.
