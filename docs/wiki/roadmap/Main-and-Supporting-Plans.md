---
title: "Main & Supporting Plans (Concept)"
area: roadmap
status: idea
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/CustomWorkoutSchedule.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutTemplateScreens.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEvidence.kt"
tags: [stridelab, roadmap, idea, planning, multi-discipline]
---

# Main & Supporting Plans (Concept — `status: idea`)

> [!NOTE]
> **Unimplemented Concept (`status: idea`):** This node documents a future product concept only. None of the tiering or 48-hour scaling behavior described here exists in shipped app code at commit `36a5eeb8`.

## Concept Overview
Pair **1 active Main Plan** (`Running`, `Strength`, or `Calisthenics`) with **up to 2 flexible Supporting tracks** (`Mobility`, `Plyometrics`, `Isometrics`, `Conditioning`, or low-fatigue `Strength`) designed to complement rather than compete with primary training stimulus.

## Intended Design Constraints
- **Engine 1.5 Untouched:** Build read-only on top of [[Templates-and-Custom-Planner]] and [[Progress-and-History]] without modifying [[Engine-1.5]] periodization or `PLAN_SCHEMA` contracts (consistent with [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]).
- **48-Hour Main Plan Awareness:** Inspect upcoming and recently completed Main Plan sessions inside a rolling 48-hour window before surfacing Supporting work.
- **3-Tier Dynamic Scaling:**
  1. **Full:** Normal Supporting dose when 48h Main Plan fatigue exposure is low.
  2. **Light:** Reduced volume/contacts when a key Main session falls within 24–48h.
  3. **Mobility / Freshness:** Switches to low-fatigue mobility/activation only on race/peak or hard consecutive Main Plan days.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Templates-and-Custom-Planner]] · [[Progress-and-History]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]
