---
title: "ADR-03: Offline-First Independent Local Ownership"
area: decision
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/GarminCredentialVault.kt"
  - "android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java"
tags: [stridelab, adr, offline-first, garmin, ownership]
---

# ADR-03: Offline-First Independent Local Ownership

## Context
Athletes train in areas with unreliable connectivity and may link Garmin Connect or import `.FIT` files without creating or signing into a StrideLab cloud account.

## Decision
Maintain independent local owner namespaces (`localGarminOwner`, `localPlanOwner`) alongside encrypted local credential storage (`GarminCredentialVault`, `SessionVault`) and non-destructive SQLite storage (`TrainingStore`, `ManualDisciplineStore`).
- `GarminAutoSyncWorker`, `.FIT` decoding/export, Engine 1.5 plan generation/diagnostics/adaptation, Guided Workout Player checkpoints, Freeform logging, and Offline Map downloads all operate with `state.account == null`.
- `TracksideMoreScreen` displays StrideLab cloud sign-in status separately from Garmin Connect status.
- Corrupt SQLite files raise `SQLiteDatabaseCorruptException` instead of auto-deleting unsynced local history.

## Consequences
- Zero cloud-account gating blocks core training, planning, or Garmin sync workflows.
- Signed-out and signed-in profiles retain isolated plans, Freeform logs, and workout receipts without accidental cross-profile data mixing.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Storage-and-FIT-Contracts]] · [[Garmin-and-Activity-Import]] · [[Hard-Guardrails]]
