# P2-F read-only preflight artifact specification v5 — 2026-11-07

Status: frozen design artifact ready for independent review; not executed.

## Scope

The first preflight is one no-install x64 Windows executable run by a standard non-elevated user. The collector makes exactly two information-gathering calls:

1. `RtlGetVersion`
2. `GetNativeSystemInfo`

It does not use TPM/TBS, enumerate the registry, use the network, install or elevate, create filesystem output, or collect hostname, username, machine/account identifiers, participant/provider data, or other unnecessary identifiers. Console output is limited to OS version and native architecture.

Support/runtime calls needed only to start the process and print output are enumerated and audited separately; they are not counted as information-gathering calls. A minimal-runtime/no-CRT build is preferred if practical, but no unsupported toolchain claim may be made.

## Approval packet

Before authorization, independently review and freeze: source text; source/provenance hash; build recipe and exact toolchain version; PE import list; compiled binary hash, signature and provenance; principal/manifest; capture method; call trace; and the incidental-effect envelope. The packet must be reviewable without running the artifact.

Manual observer capture or another separately frozen non-invasive capture mechanism records the two console values. The collector itself creates no intended file output.

## Stop conditions

Stop and reject the artifact if review or observation finds an unexpected import, network access, elevation/privilege request, child process, write behavior, registry enumeration, TPM/TBS access, identifier collection, or target mismatch. Do not continue to qualification from a stopped artifact.

Windows, AV/EDR, audit logging, and OS telemetry may create incidental effects. Literal zero writes must not be claimed; incidental effects must be recorded separately from collector-intended behavior.

## Non-claims

This preflight cannot prove authority continuity, witness quorum, target qualification, cryptographic custody, filesystem durability, integrated behavior, or Gate 2 readiness. It must not be executed in this task.

## Status

`READONLY_PREFLIGHT_ARTIFACT_SPEC_READY_FOR_INDEPENDENT_REVIEW`
