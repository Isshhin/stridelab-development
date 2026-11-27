---
title: "ADR-02: GPS 200 m Default Privacy Trimming in Share Studio"
area: decision
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioModels.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareRoutePresentation.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioScreen.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareCardRenderer.kt"
tags: [stridelab, adr, privacy, share-studio, gps]
---

# ADR-02: GPS 200 m Default Privacy Trimming in Share Studio

## Context
Athletes frequently share workout route graphics publicly on social feeds. Unmasked GPS tracks expose exact start and finish locations (commonly home or workplace addresses).

## Decision
Default Share Studio's route privacy control to `SharePrivacy.STANDARD` (`200 m` trimmed from both the start and finish of displayed route geometry), with `400 m` and `800 m` presets and full-activity summary stats kept unaltered.
- `ShareRenderableActivity.from(...)` trims coordinates once per privacy selection; `ShareCardRenderer` and `ShareMapSnapshot` consume only those trimmed segments.
- Switching to `SharePrivacy.OFF` preserves the athlete's previous nonzero trim preset in editor memory and immediately renders `share-trim-off-warning`.
- `SharePresetStore` saves visual layout choices only and never persists raw activity coordinates or user photos.

## Consequences
- Publicly shared images protect home/work endpoints out of the box without distorting distance, duration, pace, or elevation metrics.
- Short routes below safe trim thresholds hide the trace cleanly rather than leaking start/finish coordinates.

## Related Nodes
- [[00-StrideLab-Index]] · [[Share-Studio]] · [[Hard-Guardrails]] · [[Storage-and-FIT-Contracts]]
