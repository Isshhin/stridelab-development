# P2-F read-only preflight v2 — incomplete, not authorized

2026-11-07. Additive revision to [v1](p2-f-custody-authority-readonly-preflight-plan-2026-11-07.md), accompanying [v3](p2-f-custody-authority-correction-protocol-v3-2026-11-07.md). No operation has been executed on target and none is authorized by this plan. The following is a reviewer-facing proposed allowlist, not a script, implementation, permission or evidence of side-effect freedom.

## Private target freeze

PRE-ACCESS_IDENTITY_FIELDS: privately identify the exact physical workstation by an independently supplied asset record and in-person observer; approved physical location, human operator and observer; approved non-elevated interactive principal and identity-verification method; approved operation IDs and order; trusted executable provenance/hash and how it is delivered without installation; execution/evidence handling method; explicit stop authority and contact; permitted indirect audit/event effects; private retention and redaction plan; approved scope and independent reviewer. The target's identity must be checked without executing an unapproved command; if that cannot be done, stop. Do not commit any identifier, name, path, SID, serial, raw output or evidence to Git.

AUTHORIZED_DISCOVERY_FIELDS: OS version/build and architecture may be unknown and collected only by the individually approved operations below. No TPM, TBS, filesystem, service or DACL details are prerequisites for this first two-call scope. Version-dependent incompatibility stops; do not expand discovery ad hoc.

POST-DISCOVERY_VERIFICATION_FIELDS: privately compare the reported build and architecture to the independently approved support envelope and record results, collector identity, review and deviations; unknown, mismatch, changed target or principal closes. This is not proof of identity or security configuration.

## Proposed two-call scope

A frozen, independently reviewed native caller is required but not identified or created here. Its executable, load chain, signatures/hash, delivery, behavior and side effects are unresolved. Calls below are specifications, not permission to create/run a helper. Both operations: caller is approved standard interactive observer, no elevation or special privileges; only in-memory output; no intended filesystem, registry, SCM, network, TPM or TBS interaction and no service activation requested. DLL loading, Windows audit/ETW, telemetry, security software, pagefile, caches and OS/provider activity may create incidental persistent effects. Their precise effects are unknown and must be accepted explicitly by an independent reviewer or the call excluded. No claim of zero side effects. No disk logging or automatic upload. A private observer records only minimal outputs using an independently approved method. Redact identifiers, paths, names, machine details and raw traces from public reports. Stop on elevation request, unexpected access/network/provider activity, mismatch, error or changed conditions. Cleanup: release caller memory; do not alter or delete system data. These preconditions apply to every operation.

| ID | Exact call and constraints | Rights/elevation and output | Rationale and unresolved effects |
|---|---|---|---|
| OS-1 | `ntdll.dll!RtlGetVersion(&info)`, zero-initialized `RTL_OSVERSIONINFOW`, `dwOSVersionInfoSize=sizeof(info)`; exactly one call. | Standard token; no object access requested; no elevation. Collect major/minor/build on success; omit all other data. | Identify proposed OS support envelope. [Microsoft](https://learn.microsoft.com/en-us/windows/win32/devnotes/rtlgetversion). DLL loading and audit/ETW effects unbounded; caller provenance unselected. |
| OS-2 | `kernel32.dll!GetNativeSystemInfo(&info)`, zero-initialized `SYSTEM_INFO`; exactly one call. | Standard token; no object access requested; no elevation. Record only `wProcessorArchitecture`; redact all other values. | Identify native architecture. [Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/sysinfoapi/nf-sysinfoapi-getnativesysteminfo). DLL loading and indirect effects unbounded; caller provenance unselected. |

Both API calls return information, but public documentation does not promise absence of incidental telemetry/audit writes or security-software interaction. No exact target or executable has been frozen. Thus these entries remain candidates; an independent reviewer may reject either.

## Explicit exclusions

- Volume metadata, service configuration, owner/DACL inspection: excluded from this smallest scope until object identity, reparse-safe handles, access rights and side effects are separately specified and reviewed. No SCM access or registry enumeration is permitted.
- TPM/TBS, including `Tbsi_Context_Create`, `Tbsi_GetDeviceInfo`, TBS command submission, WMI/CIM and PowerShell TPM discovery: excluded. [Tbsi_GetDeviceInfo](https://learn.microsoft.com/en-us/windows/win32/api/tbs/nf-tbs-tbsi_getdeviceinfo) documents version retrieval and a TPM-not-found result but does not establish all service activation or hardware interactions. Manufacturer/firmware, command-policy exposure and TBS availability therefore remain unknown. No inference from an absent observation.
- No NV access, reset/clear, TPM key creation, DPAPI, account/service configuration, private data, network request, authority operation, target mutation test, file creation or cleanup deletion.

Read-only discovery cannot establish crash/power-loss durability, atomicity, nonrollback, exclusivity, TPM policy boundaries, key isolation or safe authorization. Those remain later separate design and integrated qualification work. Deadline-dependent behavior remains disabled; no trusted external clock is required for these calls.

## Remaining decisions

Select and independently review the precise executable, delivery chain, loaded modules, allowed indirect effects, principal, target freeze, observer, capture method and stop procedure before any possible authorization. Independent design review does not itself authorize target access.

READONLY_PREFLIGHT_PLAN_INCOMPLETE
