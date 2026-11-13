# P2-F first read-only preflight v3 — proposed, incomplete, not authorized

2026-11-07. Additive to [v2](p2-f-custody-authority-readonly-preflight-plan-v2-2026-11-07.md) and [authority v4](p2-f-custody-authority-correction-protocol-v4-2026-11-07.md). No executable has been built, delivered, approved or run; no target commands have been executed.

## Proposed collector and exact scope

A reviewer would first approve a single purpose-built native PE executable from inspectable, version-pinned source and reproducible build inputs, reviewed source-to-binary correspondence, a pinned SHA-256 digest and verified publisher signature and signer chain. Signer, digest, artifact, build inputs and offline verification procedure must be frozen privately before authorization; none exists yet. Delivery must require no installation, driver, service, persistent configuration, scheduled task or elevation. The signed binary must not be executed until independently approved. Provenance verification and delivery are separate prior activities, not secretly included in this two-call preflight.

Application-level imports must be exactly `ntdll.dll!RtlGetVersion` and `kernel32.dll!GetNativeSystemInfo`, once each, with no delay-load, runtime dynamic loading, CRT startup imports, subprocesses or telemetry code. This is a proposed binary constraint, not a claim that the Windows loader or operating system makes exactly two internal calls or loads only these DLLs. Reviewer must inspect PE import tables, entrypoint, dependencies, build/ABI and execution method; if a normal entrypoint/termination or output channel requires additional application APIs, do not pretend this constraint is met: revise and re-review the exact allowlist. No application filesystem, registry, network, TPM/TBS, service manager, DPAPI or key operations.

1. Zero initialize `RTL_OSVERSIONINFOW`, set size, call `RtlGetVersion` once; capture status, major, minor and build only.
2. Zero initialize `SYSTEM_INFO`, call `GetNativeSystemInfo` once; capture processor architecture only. No other fields retained.

Output is confined to in-memory result observed by an independently approved observer, with no application disk output, network, logging or upload. The means to transfer/display that memory without adding unreviewed APIs or observer actions has not been fixed; stdout requires additional handle/write behavior and must not be implied by the two-import rule. A separately reviewed debugger/observer may itself have side effects and privileges: no observer is selected or authorized.

Run only as a privately identified standard non-elevated interactive user on a privately frozen physical target, with independently identified operator and observer and agreed stop authority; do not disclose identifiers, raw output, paths or machine details in public Git. If target identity/principal cannot be established by separately approved non-command means, stop. The two information calls do not prove device identity, feature support or security posture. [Microsoft RtlGetVersion](https://learn.microsoft.com/en-us/windows/win32/devnotes/rtlgetversion) documents version retrieval; [GetNativeSystemInfo](https://learn.microsoft.com/en-us/windows/win32/api/sysinfoapi/nf-sysinfoapi-getnativesysteminfo) documents that on some ARM64/emulation configurations an x86/x64 caller can receive emulated information. Do not infer native architecture on those configurations; freeze native caller architecture and compatible target envelope without adding another call, or classify architecture unknown and stop.

## Incidental effects and stopping

The proposed application requests no writes, but loader activity, code integrity, security software, Windows auditing/ETW, telemetry, memory paging and caches may create incidental reads or persistent effects. No zero-write or complete enumeration guarantee is claimed. Reviewer and target owner must explicitly accept that bounded-in-intent but not fully bounded-in-implementation incidental envelope, including delivery and observer actions, or reject the operation. Unexpected prompt/elevation, unknown artifact/signature/hash, import mismatch, unexpected behavior or access, failed status, target/principal mismatch, capture uncertainty, unapproved incidental effects or architecture ambiguity => stop, no fallback or expanded probing. Preserve only approved minimal observer notes; do not delete or alter system artifacts.

Explicitly excluded: filesystem probing, service inspection, DACL inspection, registry enumeration, network access, TPM/TBS discovery, DPAPI, account creation and NV/key actions. No trusted time is required for the calls; signature validation may have its own separate trust/time requirements and must be reviewed.

The API purposes are documented, but the actual artifact, target, verification, imports, memory capture, observer and accepted side-effect envelope are not frozen. An independent reviewer cannot yet approve a specific executable and exact execution/capture chain on the frozen target. No claim that the calls are inherently unsafe; the plan remains incomplete.

READONLY_PREFLIGHT_PLAN_INCOMPLETE
