# StrideLab training-plan builder — approved lime implementation

Date: 2026-11-08
Status: Android UI implementation candidate, awaiting emulator QA.
Authority: user-supplied seven-screen reference images from 2026-11-07/08 and the current Android source. Older Figma frames are not the design authority.

## Palette sampled from approved reference screenshots

| Token | Color | Intended use |
| --- | --- | --- |
| Background | `#0B0B0B` | AMOLED-compatible builder canvas |
| Surface | `#151515` | Cards, choice rows |
| Raised surface | `#171717` | Grouped controls |
| Selection fill | `#1D240B` | Selected cards, day rows |
| Accent | `#DBF351` | CTA, selected borders, progress, icons |
| Text | `#FFFFFF` | Primary labels |
| Secondary | `#C5C5C5` | Descriptions and metadata |
| Outline | `#2B2B2B` | Unselected controls |

Source: `PlanBuilderPalette.kt`. These values apply **only** to the training-plan builder, not existing Today, Activity Detail, or Workout Player screens.

## Visual decisions

- Seven-segment progress bar, fixed bottom primary action, dense black surfaces, compact card groups, subdued unselected states.
- Selected discipline and goal cards use an olive-black fill and a lime stroke. Controls must not use low-contrast white text on filled lime.
- Availability is a seven-row list with availability, preferred long-run, and rest/unavailable states.
- Requests before generation are labeled as preferences or availability; generated workouts are shown **only after** an actual draft is saved.
- Summary metrics come from `PlanRequest` and `PlanSnapshot`; no invented mileage, session placement, or approval state.
- Progress phases map to the present functional flow: 1 disciplines; 2 goals/ability; 3 discipline sessions/setup; 4 dates; 5 availability; 6 blueprint; 7 saved draft review.
- Nested goal, equipment, day-assignment, and coaching preference screens remain available, even when the visual reference omits them.

## Logic boundary

No changes to `PlanRequest`, `WeeklyPlacement`, scheduling engines, account persistence, or `actions.generate`/`actions.approve` callback behavior are permitted by this UI pass.
The draft approval remains explicit. Pinned-day confirmation and capacity constraints are retained.
This branch does not activate Engine 1.5 or modify Engine 2.x.

## Verification gate (not yet claimed as passed)

- Kotlin compilation and Android UI instrumentation on 360×800dp/130% and 412×950dp/150%.
- Verify no clipped text, accessible scroll to every option, fixed footer visibility, and device navigation.
- Check all disciplines, race/ability goals, weekly counts, manual day assignments, protected-day confirmation, double-day capacity, plan dates, generation errors and approval.
- Verify generated plan rows correspond to real engine prescriptions and re-open offline.
- Compare implementation visually against the approved screenshots, especially icon tint, lime contrast, selected rows and summary cards.

Do not merge before emulator QA and review of upstream changes to the active Android branch.
