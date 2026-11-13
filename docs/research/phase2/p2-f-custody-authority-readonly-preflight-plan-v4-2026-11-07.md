# P2-F read-only preflight plan v4 — 2026-11-07

Status: plan ready for independent review; not executed.

This additive revision keeps the first preflight to exactly two information calls: `RtlGetVersion` and `GetNativeSystemInfo`. It does not access TPM/TBS, provision keys, inspect participant/provider data, write target files, enumerate the registry, use the network, install anything, or modify system state by design.

## Approval artifact

Before execution, an independent reviewer must receive the collector source and provenance, build recipe, executable hash/signature, exact import list, call trace, principal/manifest, capture method and incidental-effect envelope. The collector must be no-install and run under a standard non-elevated principal.

The reviewer must confirm that the artifact performs exactly the two approved information calls, captures results in memory or through an approved observer, and has no child process, elevation, network, TPM/TBS, registry-enumeration or explicit filesystem-write path.

## Incidental effects and stop conditions

The plan does not claim zero incidental Windows, security-software, audit or observer writes. Any such effects must be documented as observations rather than silently treated as impossible. Stop immediately on an unexpected import, handle or child process, elevation request, network activity, file/registry activity, TPM/TBS access, or output outside the approved capture envelope.

This plan produces environment information only. It cannot establish authority continuity, target qualification, cryptographic custody, filesystem durability, or Gate 2 readiness.

## Status

`READONLY_PREFLIGHT_PLAN_READY_FOR_INDEPENDENT_REVIEW`

