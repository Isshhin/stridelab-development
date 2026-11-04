# Trackside 3.0 Functional Behavior Audit — 2026-11-05

**Starting SHA:** `9d3c559e4b92a366a3b52934b19ea37fb0e65084`  
**Starting branch:** `origin/integration/trackside3-engine15-20261105`  
**Approved Figma:** `v73TsYo6gKxUoiUock2sSE`  
**Status vocabulary:** PASS, BROKEN, MISSING, AMBIGUOUS, FIXED, DEFERRED, NOT RUN.

This is a living audit. Initial classifications are based on source/data-flow inspection and the two reproduced physical-device reports. Rows become PASS or FIXED only after behavioral verification.

| Area | Screen | Control / gesture | Intended behavior / authority | Figma | Implementation / owner | Current behavioral test | Initial | Sev | Root cause / correction | Final |
|---|---|---|---|---|---|---|---|---|---|---|
| Today | Today active | Workout card / Open | Open the intended session, preserve multiple sessions and completion state | `651:2` gallery | `TracksideToday.kt`, NavHost, repository | partial route smoke | NOT RUN | P1 | Audit real route and correct-item callback | NOT RUN |
| Today | Today active | Scroll / action controls | All cards/actions reachable above navigation at responsive profiles | latest approved Today | Today surfaces | responsive screenshots | NOT RUN | P1 | Outcome and reachability checks | NOT RUN |
| Training | Training | Day / Agenda | Switch view without losing selected day/week | `602:2` | `TracksideParityTraining.kt` | partial UI | NOT RUN | P1 | Production-route state restoration | NOT RUN |
| Training | Training | Day selection / week nav | Correct day selected; first/last/Saturday reachable | `602:2` | calendar state | responsive partial | NOT RUN | P1 | Audit stale index and compact widths | NOT RUN |
| Training | Training | Workout card | Open the tapped workout, not first item | approved workout details | navigation callback | existence/click partial | NOT RUN | P1 | Assert opened ID through route | NOT RUN |
| Training | Training | Manage Plan | Navigate and return with state intact | `732:2` | NavHost | partial nav | NOT RUN | P1 | Back-stack and rapid-tap checks | NOT RUN |
| Manage | Manage Plan | Pause / resume | Revision-gated action, truthful state, offline persistence | `732:2` | `TracksideManagePlan.kt`, repository | Engine/UI partial | NOT RUN | P1 | Production-route persistence | NOT RUN |
| Manage | Move Workout | Session and destination | Move selected session; capacity/swap/conflict truthful | `645:2`, `732:2` | move sheet / PlanActions | partial | NOT RUN | P1 | Audit wrong-item/stale selection | NOT RUN |
| Manage | Missed Workout | Reason / suggestion | Correct workout and reviewed Engine 1.5 behavior | `732:2`, `834:2` | manage + Engine15 feedback | matrix tests | NOT RUN | P1 | Verify routed state and stale rejection | NOT RUN |
| Manage | Review Changes | Accept / dismiss | Apply only current revision; dismiss persists | `834:2` | Engine15 management | targeted UI/repository | NOT RUN | P1 | Full route/offline reopen | NOT RUN |
| Activity | Activity list | Activity card / refresh | Open correct activity; update source state without duplication | approved Activity | `NativeMainActivity.kt` | partial | NOT RUN | P1 | ID and refresh outcome tests | NOT RUN |
| Activity | Detail | Drag handle / sheet | Finger-following collapsed/expanded anchored sheet, stable velocity snap | `551:235`, `551:365` | `TracksideFigmaActivityDetailSurface.kt` | red/green drag + 16-test builder/activity class | BROKEN | P1 | Replaced fixed padding with Material anchored sheet state and nested scroll | FIXED |
| Activity | Detail | Inner content scroll | Scroll details/charts when expanded without fighting sheet | `551:235` independent scroll | same | none | BROKEN | P1 | No sheet/inner-scroll arbitration | NOT RUN |
| Activity | Detail | Back | Collapse expanded sheet before leaving; ordinary back otherwise | approved interaction intent | route + surface | collapse-first instrumentation test | MISSING | P1 | Added system and map-back collapse-first behavior | FIXED |
| Activity | Detail | Accessibility action | Expand/collapse without drag; expose state | approved interactive sheet | surface semantics | expand/collapse action instrumentation test | MISSING | P1 | Added state description and semantic expand/collapse actions | FIXED |
| Activity | Detail | Overview / Splits / Charts | Preserve tab and sheet state; recorded-only values | `551:235`, `551:365` | selectedTab route state | existence partial | NOT RUN | P1 | Gesture/tab/recreation tests | NOT RUN |
| Activity | Detail | Map / route | Map fixed behind sheet; recorded GPS only; illustration labeled | `551:235`, `551:365` | map hero | composition partial | NOT RUN | P1 | Verify throughout gestures/missing GPS | NOT RUN |
| Activity | Detail | Plan link / refresh | Link intended run; refresh exact activity, offline truthful | Activity docs | repository/actions | partial | NOT RUN | P1 | Production-route outcomes | NOT RUN |
| Builder | Discipline | Multi-select cards | Preserve chosen disciplines and clear removed stale goals/slots | `551:580` | `PlanBuilder.kt` | partial | NOT RUN | P1 | Rapid/back/reopen tests | NOT RUN |
| Builder | Running Goal | Goal / race intent / distance | Correct state and navigation | `551:603`, `551:796` | builder state | partial | NOT RUN | P1 | Documentation/Figma comparison | NOT RUN |
| Builder | Ability | Experience / mileage | Save truthful controls and restore | `551:865` | builder state | partial | NOT RUN | P1 | Edge values/recreation | NOT RUN |
| Builder | Advanced / Guided | Preferences | Truthful advisory/enforced behavior and navigation | latest running nodes | builder | partial | NOT RUN | P2 | Copy/behavior audit | NOT RUN |
| Builder | Running Weekly Setup | Occupied weekday | Select the owning Long/Quality/Easy slot without moving another run | `548:95`; weekday checkpoint | `TracksideFigmaRunningSetup.kt`, selected slot | red/green all-slot identity instrumentation | BROKEN | P1 | Occupied compact days now select their owning slot without assignment | FIXED |
| Builder | Running Weekly Setup | Destination weekday | Move selected slot; swap occupied; preserve slot identity, longDay, qualityDays | `548:95` | WeeklyPlacement + builder | destination move + full 16-test UI class + JVM suite | NOT RUN | P1 | Explicit editor row remains the only assignment surface | PASS |
| Builder | Running Weekly Setup | Remove selected | Clear actual selected slot, never accidental Long Run | `548:95` | builder callback | partial | NOT RUN | P1 | Selected owner and persistence assertions | NOT RUN |
| Builder | Running Weekly Setup | Count / quality / auto-manual | Resize safely, block incomplete, preserve exact manual choices, reset explicit auto | `548:95` | WeeklyPlacement | JVM partial | NOT RUN | P1 | Stale index and restore audit | NOT RUN |
| Builder | Other discipline slots | Session / weekday | Select correct Strength/Cali/Conditioning slot, swap/clear safely | `698:2`, `724:2` | `TracksideFigmaOtherDisciplineSlots.kt` | strength/cali instrumentation + JVM placement suite | NOT RUN | P1 | Explicit destination row is not subject to compact-owner ambiguity | PASS |
| Builder | Plan Dates | Date / duration / custom weeks | Save valid dates/weeks; controls reachable | `720:546` | builder dates | responsive partial | NOT RUN | P1 | Back/reopen/large font | NOT RUN |
| Builder | Availability | Day / occupied removal / double day | Correct day, explicit removal, incomplete blocking, safe pairing | `614:88` | availability + WeeklyPlacement | substantial UI | NOT RUN | P1 | Repeat/edge/reopen | NOT RUN |
| Builder | Week Preview | Week nav / session | Truthful request vs generated draft; all weeks/sessions reachable | `614:412` | `TracksideFigmaWeekReview.kt` | substantial responsive | NOT RUN | P1 | Full route and final partial week | NOT RUN |
| Builder | Draft Approval | Generate / approve | Saved draft, separate explicit approval, offline persistence | `651:2` | PlanBuilder/repository | partial integration | NOT RUN | P1 | Production-route recreate/account | NOT RUN |
| Workout | Player / logging | Open / step / log / back / complete | Correct workout; drafts persist; completion and reopen truthful | approved workout pages | WorkoutScreen/repository | existing suites | NOT RUN | P1 | Audit cancel/rapid/reopen | NOT RUN |
| Persistence | App state | Process restart / offline reopen | Drafts, plans, logs, owner, engine version persist | handoffs/contracts | SQLite/repository | repository suites | NOT RUN | P1 | Fresh exact-head regression | NOT RUN |
| Persistence | Accounts | Switch / restore | No cross-account data or Garmin credential contamination | contracts | repository/secure store | partial | NOT RUN | P0 | Full isolation checks | NOT RUN |
| Engine | Engine 1.5 | Generate / approve / revise | Frozen bounded policies and reviewed changes remain verified | Engine 1.5 handoffs | engine/repository | extensive JVM/device | NOT RUN | P0 | Full regression after UI changes | NOT RUN |
| Engine | Engine 1.0 | Rollback / history | Existing snapshots remain compatible and immutable | Engine baseline | registry/repository | existing | NOT RUN | P0 | Exact-head regression | NOT RUN |
| Responsive | Core flows | 360/1.0, 360/1.3, 412/1.0, 412/1.5 | No clipping, obscured actions, or sub-target shrinking | responsive Figma | all Compose | workflow profiles | NOT RUN | P1 | Run four profiles after corrections | NOT RUN |
| Accessibility | Core flows | Semantics / selected / disabled / gestures | State exposed; drag alternative; useful targets/order | component descriptions | Compose semantics | partial | NOT RUN | P1 | Add core outcome checks | NOT RUN |
| Garmin | Settings | Connect / sync / disconnect | Official Personal API only; secure key/token; concise state | Settings approved page | provider/settings | fixture provider only | MISSING | P2 | Implement documented/tested foundation after usability | NOT RUN |
| Garmin | Import | Normalize / dedupe / provenance | Independent source, upstream IDs, no double count or silent delete | source contracts | activity provider/repository | fixtures only | MISSING | P1 | Add adapter boundary and mocks | NOT RUN |
| Garmin | Engine boundary | Health observations | Store provenance only; no hidden Engine 1.5 decisions | explicit directive | source adapter | none | MISSING | P0 | Enforce no coaching mutation | NOT RUN |

## Conflicts and decisions

- Historical Activity documentation required details before map; the newer approved master gallery and latest Trackside handoff require a map-backed sheet. The newer approved Figma behavior controls this pass.
- Older weekday checkpoint text says selecting a session then tapping a day; the explicit current physical-device acceptance adds occupied-day ownership selection. Occupied-day selection now takes precedence and must not mutate placement.
- Engine 1.5 recommendation nodes `834:2` and responsive nodes `840:2` were historically marked for approval. Runtime integration exists at the frozen baseline; this pass verifies its current bounded behavior without resurrecting unapproved redesign work.

