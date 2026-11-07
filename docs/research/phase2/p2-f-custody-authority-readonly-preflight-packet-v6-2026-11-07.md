# P2-F Read-only Preflight Artifact Packet v6

Status: source frozen; binary identity blocked. This packet is not executed and does not authorize target access.

## Frozen source

Source: `docs/research/phase2/preflight/stridelab_p2f_preflight.c`
Source SHA-256: `00d06f56ce8ed62bab00fe72822dcb6f39c17022a7a0d804dbebf741e7bd4eff`
Purpose: exactly two future information calls, `RtlGetVersion` and `GetNativeSystemInfo`; console output only. No TPM/TBS, registry, network, installation, elevation, identifiers, filesystem output, child process or wrapper.

## Build and artifact identity

Recipe: build an x64 Windows console executable in an explicitly non-target development environment using a pinned MSVC/Windows SDK toolchain; compile the frozen source with `/nologo /O1 /GS- /GR- /EHs-c- /DUNICODE /D_UNICODE`, link with `/SUBSYSTEM:CONSOLE /DYNAMICBASE /NXCOMPAT`, and inspect the resulting PE without executing it. Exact compiler, linker, SDK, binary name, size, SHA-256, imports and signature remain `BLOCKED` because this checkout does not establish an authorized non-target build environment. No binary was compiled.

The eventual packet must record the actual import table, dynamic symbol-resolution list, static call audit, PE identity, signature status (`UNSIGNED RESEARCH ARTIFACT` if applicable), and provenance. It must not infer imports from this recipe.

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

`READONLY_PREFLIGHT_PACKET_SOURCE_FROZEN_BINARY_IDENTITY_BLOCKED`
