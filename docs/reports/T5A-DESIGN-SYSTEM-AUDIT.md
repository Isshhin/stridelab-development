# StrideLab T5A — Native Android Design System and UI/UX Audit

## Scope and verdict

**Audited source:** `25be8f3828374d1e539c7e825afa20fbcb652b56`
**Pinned checkpoint:** `checkpoint/native-guided-multisession-verified-25be8f3`
**Documentation branch:** `docs/t5a-design-system-audit-25be8f3`

The report branch was created directly from the pinned source commit. `git rev-parse HEAD` returned the full pinned SHA before the report was created. T0 was read first; its relevant findings are carried forward without treating the T0 documentation branch as an implementation baseline.

The native Android UI is a single dark Material 3 Compose design system with a Bricolage display face, lime brand token, a small set of repeated cards and controls, and a real but text-forward Training/Player/Activity flow. It has useful offline, saved-workout, activity-stream, and wellness surfaces to preserve. It does **not** yet have a centralized app string catalog, a reusable workout-status chip, a running recorder, a unified chart-component system, or the approved immersive activity-detail interaction.

This is a read-only audit. No application, test, resource, database, Gradle, or dependency file was changed. No APK was built, packaged, uploaded, installed, or signed; no physical phone was used.

## 1. Theme and color-token audit

### Authored tokens and actual roles

`StrideTheme` in [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) supplies a `darkColorScheme`. Values below are written as **#AARRGGBB**; the authored scheme colors are fully opaque.

| Token / literal | Value | Source and actual role/usages | Consistency |
|---|---:|---|---|
| `primary` | `#FFD6FF4F` | `StrideTheme`; brand lime. Used for primary Material buttons, selected chips/calendar days, headings/eyebrows, the `BrandMark`, selected progress/bar elements, and some state emphasis. | Reused as a global brand/action color, but also used for decorative headings and chart fills; no separate action-vs-decoration role. |
| `onPrimary` | `#FF0A0D02` | `StrideTheme`; foreground on lime-filled controls and selected surfaces. | Consistent scheme pairing. |
| `background` | `#FF050506` | `StrideTheme`; primary screen background. Also duplicated in shared Android `styles.xml` as `stride_black` for the native window/system bars. | Same value in Compose and XML; separate literals are required by the two layers but can drift. |
| `surface` | `#FF101013` | `StrideTheme`; `SignalCard`, activity rows, detail sheets and other card/sheet surfaces. | Reused consistently. |
| `surfaceVariant` | `#FF25252B` | `StrideTheme`; offline/message banners, selected navigation indicator and progress tracks. | Reused for both status/surface and graph track; semantic roles are broader than contrast affordance. |
| `surfaceContainer` | `#FF18181C` | `StrideTheme`; bottom navigation, metric tiles, calendar cells and sync status. | Reused consistently. |
| `surfaceContainerHigh` | `#FF25252B` | `StrideTheme`; elevated container token, same color as `surfaceVariant`. | Exact duplicate value; no visibly distinct higher elevation. |
| `onSurface` | `#FFF4F1EA` | `StrideTheme`; primary foreground and custom icon drawing. | Consistent. |
| `onBackground` | `#FFF4F1EA` | `StrideTheme`; screen foreground. Same exact value as `onSurface`. | Consistent pairing, duplicated assignment. |
| `onSurfaceVariant` | `#FFB0B0BA` | `StrideTheme`; supporting text, date labels, explanatory text, sync status. | Reused consistently and remains high contrast on the authored dark surfaces. |
| `secondary` | `#FF6FB4FF` | `StrideTheme`; activity-detail source label and recent-duration chart bars. | Shares exact color with the running discipline accent, but usages are not named/centralized as one token. |
| `secondaryContainer` | `#FF232F3D` | `StrideTheme`; container for secondary actions if used by Material components. | Authored but no intentional screen-level use was found. |
| `onSecondaryContainer` | `#FFCCE4FF` | `StrideTheme`; foreground for that container. | Authored but no intentional screen-level use was found. |
| `outline` | `#FF777780` | `StrideTheme`; Material outline role. | Authored; no broad usage found in custom components. |
| `outlineVariant` | `#FF303038` | `StrideTheme`; card borders, separators and unselected chip borders. | Reused, but too low-contrast for a border that must communicate a control boundary by itself. |
| Material 3 `error` default | `#FFF2B8B5` | Not explicitly authored in `StrideTheme`; `MaterialTheme.colorScheme.error` is used for form/player error text. This is the Material 3 dark-scheme default, not a StrideLab token. | No app-level error palette or paired error container was defined. |
| Success / warning | **Not defined** | No dedicated success or warning colors/tokens found. | Do not infer success from lime or warning from amber. |

`styles.xml` also has `android:colorAccent` `#D6FF4F` and `stride_black` `#050506`; both mirror the Compose lime/background without alpha. The native UI contains no string resources in `res/values`; visible Compose copy is authored in Kotlin.

### Discipline colors and hard-coded color values outside the theme

| UI role | Value | Source and current rendering |
|---|---:|---|
| Running | `#FF6FB4FF` | `disciplineColor("run")` in [NativeLibrary.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeLibrary.kt); also hard-coded independently in `ActivityRow` in [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt). |
| Strength | `#FFFF9A62` | `disciplineColor("str")`; the same literal is duplicated in `ActivityRow`’s strength heuristic. |
| Calisthenics | `#FFC79BFF` | `disciplineColor` fallback; purple is also used for conditioning and unknown disciplines. |
| Conditioning | `#FFC79BFF` | No unique conditioning color; receives the same `else` color as calisthenics and unrecognized disciplines. |
| Mobility / recovery | **Not defined** | No distinct discipline color found. |
| Image canvas | `#FFFFFFFF` | `Color.White` in `LibraryImage`, used as a light backing for supplied movement illustrations. |
| Transparent | `#00000000` | `Color.Transparent` in image gradients and chip containers. |

The blue and orange literals in `ActivityRow` duplicate the values in `disciplineColor`; the blue also duplicates `secondary`. This is unnecessary duplication for the same visual roles and risks divergence. Discipline styling is principally a colored card rail/text and a small calendar dot; the card itself also carries a textual discipline label. Calendar dots can still be color-only visually, though their calendar-cell semantics include session title/status. There is no separate token for status-success, missed, skipped, mobility or recovery.

### WCAG contrast check

Ratios use the WCAG relative-luminance formula on the actual opaque foreground/background hex values above. Text ratios are checked against 4.5:1 normal-text AA (large-text AA is 3:1). These calculations do not certify every Material component state, alpha overlay, image, device display or user-selected contrast setting.

| Foreground | Actual background | Ratio | Assessment |
|---|---|---:|---|
| `onSurface #F4F1EA` | `background #050506` | 18.06:1 | Pass |
| `onSurface #F4F1EA` | `surface #101013` | 16.84:1 | Pass |
| `onSurfaceVariant #B0B0BA` | `background #050506` | 9.47:1 | Pass |
| `onSurfaceVariant #B0B0BA` | `surface #101013` | 8.83:1 | Pass |
| `onSurfaceVariant #B0B0BA` | `surfaceVariant #25252B` | 7.09:1 | Pass |
| `onPrimary #0A0D02` | lime `primary #D6FF4F` | 17.06:1 | Pass; primary-button label pairing is strong |
| lime `primary #D6FF4F` | `background #050506` | 17.73:1 | Pass |
| lime `primary #D6FF4F` | `surface #101013` | 16.53:1 | Pass |
| running blue `#6FB4FF` | `surface #101013` | 8.73:1 | Pass |
| strength orange `#FF9A62` | `surface #101013` | 9.09:1 | Pass |
| calisthenics/conditioning purple `#C79BFF` | `surface #101013` | 8.65:1 | Pass |
| `onSecondaryContainer #CCE4FF` | `secondaryContainer #232F3D` | 10.42:1 | Pass |
| Material 3 default `error #F2B8B5` | `surface #101013` | 11.12:1 | Pass |
| `outline #777780` | `surface #101013` | 4.28:1 | Below 4.5:1 if used as normal text; just above 3:1 non-text boundary guidance |
| `outlineVariant #303038` | `surface #101013` | 1.45:1 | Fails 3:1 non-text contrast if relied upon alone for a boundary |

No separate disabled-text token is authored; Material 3 supplies disabled component alpha behavior. Alpha overlays such as `.65f` borders, `.10f` lime backgrounds, `.07f` activity gradients and `.15f` chart grid lines are composited against the surface at runtime and are not independent palette tokens. Decorative lime use is frequent in headings/eyebrows and fills; it is not limited to brand identity and primary actions. Keep lime as a brand token, but future screens should use it selectively rather than as the default accent for every label or chart.

No new strength or conditioning color is selected here. If the team wants discipline-specific tokens, propose and contrast-test them in the implementation design review before they become authored values.

## 2. Typography, spacing, shapes, icons and sizing

| Convention | Verified implementation |
|---|---|
| Font assets | `StrideTheme` loads `R.font.bricolage_variable` as a variable `FontFamily`; the code sets weight 800 for display use. The asset filename is `bricolage_variable.ttf`; no other bundled font family is registered for Compose. The platform window style says `sans`. |
| Custom typography | In `StrideTheme`: `headlineLarge` 34/38sp, bold, letter spacing −1sp; `headlineMedium` 30/35sp, bold, −0.7sp; `headlineSmall` 26/32sp, bold; `titleLarge` 21/27sp, semibold; `labelMedium` 12/18sp, bold, 1sp tracking. `Typography()` leaves other roles (including body, small labels and most titles) at Material 3 defaults. |
| Hierarchy | Many labels are forced to uppercase; the small 12sp, tracked `labelMedium` carries discipline, status and section metadata. Workout cards often combine several facts into that one line. |
| Global screen spacing | `Screen` uses 22dp horizontal padding, 24dp top, 32dp bottom and 18dp between list items; screen content is capped at 760dp. |
| Main card | `SignalCard`: 22dp rounded corners, 20dp padding, 12dp vertical spacing, 1dp low-contrast outline variant. |
| Workout/activity cards | `PlannedWorkoutCard`: 24dp corners, 4dp discipline rail, 18dp padding and 10dp item spacing. `ActivityRow`: 22dp corners, 20dp padding and 12dp spacing. These patterns are similar but not shared through one card primitive. |
| Other shapes | Theme medium 18dp and large 24dp; cards also locally use 18/22/24dp. Buttons/chips use pill shapes; image frames use 20dp. |
| Repeated/fixed dimensions | Bottom navigation has minimum 80dp height and 24dp total horizontal/vertical outside padding. Calendar day cells have minimum 64dp height; the horizontal week view hard-codes 46dp per day. Recorded stream chart is 110dp high; route trace 200dp. Progress bars are 4–5dp high. |
| Icons | Material icons: Home, DateRange, auto-mirrored List, Settings; Library grid is custom Canvas; `BrandMark` is custom Canvas. Some cards use the text glyph `↗`. Default tab icons have null icon descriptions because the parent tab supplies a label/semantics description. |
| Buttons and tap affordances | Shared `SignalButton` has explicit minimum 54dp height. Refresh/sync actions often specify 52dp; player footer controls specify 48dp. Other Material buttons/chips rely on Material defaults. Whole workout/activity cards are clickable. |

### Density and layout risks

Verified sources show long explanatory paragraphs and multiple facts stacked in `PlannedWorkoutCard`, `PlanOverview`, wellness cards, the library detail sheet and workout review; cards grow with content rather than being compact status-first rows. This is the main source of vertical text density and oversized calendar feeds. Week cells at 46dp wide can crowd localized weekday names and enlarged text; the Month grid reserves six rows regardless of month length. The 80dp bottom bar plus margins consumes vertical space. Large text is partly accommodated, but hiding labels at larger scale trades visual density for discoverability. Fixed chart/canvas heights are not text containers, while cards and `LazyColumn` generally wrap/scroll.

These are audit findings, not screen redesigns. No clipping behavior is claimed solely from source inspection; validate actual screen sizes, locales, keyboard states and font scales during implementation.

## 3. Shared Compose component inventory

The following are real Compose components in the pinned source. “Test” refers to existing source-level tests; no Compose `@Preview` annotation was found.

| Component / source | Parameters and actual use | Theme / accessibility | Tests, previews and reuse |
|---|---|---|---|
| `StrideTheme` — [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) | `content`; wraps `NativeMainActivity`, visual tests and other native screens. | Owns color scheme, partial typography and shapes. | Used by `NativeVisualTest` and UI tests; no preview. Must remain the single theme entry point. |
| `Screen` — [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) | `title`, `eyebrow`, optional `compactTitle`, lazy-list content; Overview, Training, Activities, Settings, builders, workout detail, Library. | Applies standard page padding/spacing, brand/profile row, `paneTitle`, heading semantics and scrollable list. | Exercised through `NativeVisualTest`, navigation/UI suites; good shared page shell. |
| `SignalCard` — [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) | Optional `modifier`, `ColumnScope` content; dashboard, empty, wellness, plans, workout, activity and feedback cards. | Surface, 22dp shape, outlineVariant, 20dp padding/12dp spacing. Content semantics depend on children. | Broadly reused; no dedicated preview. Strong candidate for preserving while centralizing compact/dense variants only if evidence calls for them. |
| `SignalButton` — [NativeLibrary.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeLibrary.kt) | `text`, `onClick`, optional `enabled`; primary actions throughout plan/player/library. | Material `Button`, lime theme primary, pill shape, min 54dp. Text-label semantics come from Material. | Workout/player and UI tests exercise actions; no dedicated preview. Reuse for primary action contract. |
| `SignalChip` / `ShelfFilter` — [NativeLibrary.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeLibrary.kt) | `SignalChip(selected,onClick,label,modifier)`; filter chips and choice/day/frequency controls. `ShelfFilter(label,options,selected,onSelect)` is a dropdown. | Theme `primary`, `onPrimary`, `onSurface`, outlineVariant; default FilterChip/OutlinedButton semantics. | Used by Library, PlanBuilder, PracticeBuilder, Training plan selection/feedback. There is no specialized workout-status chip. |
| `PlannedWorkoutCard` — [TrainingScreens.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/TrainingScreens.kt) | `session`, optional `hasDraft`, `onClick`; Home, calendar date, saved sessions and activity workout logs. | Per-discipline color rail/text; text status and persisted-log summaries. Entire Surface is clickable, tagged by workout ID; no explicit semantic role/state contract beyond Material clickable. | Covered indirectly by UI tests; key reuse point and the exact P0-1/P0-5 display surface. |
| `TrainingCalendar` / `DayCell` — [TrainingScreens.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/TrainingScreens.kt) | Plan, selected day, month/week mode, selection callback; Training. | Lime selected day, surfaceContainer, discipline/completion dot, minimum 64dp height; content description states date, session title and raw status. | Training UI tests exercise navigation; one reusable calendar, but no month/week start preference or dedicated agenda component. |
| `Metric`, `MetricPair` — [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) | Label/value and optional modifier; Overview and Activity Detail. | SurfaceContainer tile; responsive column when font scale >1.15 or width <340dp. | `NativeVisualTest` verifies large-font/narrow reflow and missing values. Reusable for real, labeled metric pairs. |
| `WeeklyDashboard`, `WellnessOverview` — [WeeklyDashboard.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WeeklyDashboard.kt) | Recorded activity history/time/zone/plans; wellness rows/refresh/enabled. Used on Overview. | SignalCard, lime headings, primary progress bars, `onSurfaceVariant` explanations; data/provenance is written as text. | Large-font and dashboard behavior covered in `NativeVisualTest`; reusable as data-backed base, not as a full visualization system. |
| `RecordedDurationChart` — [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) | `activities`; Overview. | Text labels/dates plus 5dp proportional bars using `secondary`; no chart semantics per bar. | Visual capture/assertions in `NativeVisualTest`; extend only with real values and accessible textual alternatives. |
| `RecordedActivityDetail` / `SampleGraph` — [RecordedActivityDetail.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/RecordedActivityDetail.kt) | `ActivityDetail`, `supportsPace`; Activity Detail. | SignalCard, primary-colored Canvas route/graphs, units/ranges and accessible descriptions; sample selection via taps. | `NativeVisualTest` verifies chart tap selection, samples, GPS route description and laps. Reuse graph data plumbing, not as proof of map/tabs/splits UX. |
| `ActivityRow` / `HistoryOverview` — [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) | Activity and click callback / activity list; Overview and Activities. | ActivityRow has discipline/source facts and metrics; visible clickable Surface. HistoryOverview is summary copy. | Card action/missing data tests in `NativeVisualTest`; reuse real activity identity/metrics. |
| `WorkoutPlayer` — [WorkoutPlayer.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayer.kt) | Owner/session, busy/entry values, rest state callbacks, navigation/review/confirm callbacks, error; called by `WorkoutScreen`. | Pane title, exercise heading, set progress description, rest countdown description, polite live region for save/error; fixed footer actions; Material controls. | `WorkoutPlayerTest` covers actual set confirmation, rest, guide, invalid input and 200% font; best existing guided interaction surface to retain. |
| `EmptyState` — [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) | Title/description; sign-in, activities, empty cache and library cases. | SignalCard with brand mark, heading and explanatory body. | Tested through native UI scenarios; reusable; no common retry action parameter. |
| `SyncStatus` — [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) | `TrainingState`; Overview, Activities, Settings. | SurfaceContainer with one text line for busy task, last refresh or no completed refresh. | UI tests cover offline/loading/error surfaces; supports status only, not a distinct sync-history timeline. |
| Material `AlertDialog` / `ModalBottomSheet` | Call-site-specific state and content in `WorkoutScreen`, `TrainingScreens`, `NativeLibrary`; move/missed, alternatives, plan menu, image details, actual-set sheet, form guide. | Standard Material semantics; content varies. Sheets scroll and generally use `imePadding` when editing. | Exercised by workflow tests; no common project wrapper or unified action copy. |
| `BrandMark` / `LibraryGrid` | Modifier; brand and library navigation. | Custom Canvas; brand lime and onSurface respectively. Brand mark documented as decorative, not a metric/route. | No dedicated semantics or preview; preserve as decorative only. |
| `LibraryImage` — [NativeLibrary.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeLibrary.kt) | Catalog entry and optional compact layout; Library details and guided form sheet. | Image description comes from catalog metadata; fixed max image height 180/230dp, white backing, source/license text. | Library/player tests exercise display paths. Image availability is conditional; fallback copy explains missing exact demonstration. |

**Repeated patterns to centralize later:** semantic workout-status presentation, discipline accent access, concise card metadata/action rows, button minimum-size policy, empty/error/retry presentation, and date/week labels. Keep current data/action ownership in `TrainingRepository`, `PlanOperations`, `ActivityPlanLink` and `WorkoutScreen`; visual components must not become new persistence or completion authorities.

## 4. Accessibility audit

### Verified strengths

- `Screen` sets `paneTitle`; screen titles and the player exercise use heading semantics. `NativeStrideContent` supplies navigation tab labels as content descriptions, and profile Settings has an explicit description.
- Calendar cells expose date plus sessions/status; player progress exposes recorded/total sets; rest countdown has a seconds-remaining description; errors/save notices in `WorkoutPlayer` use a polite live region.
- Route trace and sample graphs carry content descriptions. Graph headings, units/ranges, laps and missing-stream explanations are also textual.
- Library image descriptions are catalog-provided. Clickable workout/activity cards retain visible text as their label.
- Large text is specifically considered in `MetricPair`, dashboard layout and bottom-navigation labels. `NativeVisualTest` and `WorkoutPlayerTest` include synthetic 200% font-scale/narrow-width reachability scenarios. These are targeted Compose tests, not a full accessibility conformance audit.

### Concrete gaps and follow-up checks

| Priority | Evidence | Practical concern |
|---|---|---|
| High | `TrainingCalendar.DayCell` uses a fixed 46dp week-cell width and only 7dp visual dots. | Narrow cells can crowd labels; dots are visual status/discipline encoding. Semantic text exists, but do not rely on color to identify status. |
| High | `PlannedWorkoutCard` reports status in raw uppercase status IDs, including `IN_PROGRESS`; calendar dot state and card copy are the only status styling. | TalkBack text is technically present but not plain-language, structured state (“in progress” rather than enum literal), and “completed” presentation inherits P0-1’s invalid-completion risk. |
| High | `RecordedActivityDetail.SampleGraph` exposes a description but only handles `detectTapGestures`; no semantic adjustable/select action or keyboard alternative is attached to the Canvas. The GPS route is also a Canvas with descriptive text only. | Screen-reader and keyboard users cannot reliably choose/inspect chart samples or interact with the route. Add a list/stepper or semantic actions alongside the drawing, not merely a longer description. |
| Medium | `WorkoutPlayer` puts a polite live region on save/error feedback, and gives the rest number a content description, but timer ticks are not announced as a live region. | Frequent countdown announcements would be noisy; provide a deliberate pause/remaining-time announcement action/state rather than announcing every second. |
| Medium | Week/month arrow buttons contain glyphs `‹` / `›` with no explicit action descriptions. Calendar day cells have descriptions; month navigation does not. | TalkBack may announce a glyph rather than “previous month” / “next month”; provide action-specific descriptions. |
| Medium | `outlineVariant #303038` has only 1.45:1 contrast on `surface #101013`; chips/cards use it as a 1dp boundary. | Control boundaries disappear for users who need non-text contrast. Strengthen boundaries or pair them with another non-color affordance. |
| Medium | `SignalChip` relies on `FilterChip` semantics and default sizing; weekly cells are 46dp wide; not every custom control declares a 48dp minimum. | Audit actual hit bounds for chip/calendar/nav controls. Material minimum-touch expansion can affect rendered bounds, but source inspection alone cannot prove all hit targets are at least 48dp. |
| Medium | Navigation labels are hidden when `fontScale > 1.3`; `MetricPair` and dashboard change layout only at thresholds >1.15. | At large text, icon-only tabs depend on parent descriptions and lose visible labels. Test discoverability and 200% scaling across all screens, not just metric/player examples. |
| Medium | UI uses uppercase 12sp tracked labels extensively and multiple long explanatory paragraphs. | Small tracked metadata and dense card lines can be tiring at practical viewing distance and with magnification. Preserve readable body text and avoid using label style for critical state. |
| Medium | `RecordedActivityDetail` has no explicit keyboard/focusable chart interaction; no app-specific focus order or key handlers found in Compose source. | Keyboard, switch-access and alternative navigation need an end-to-end pass. Standard Material controls retain their default semantics, but custom canvases do not gain controls automatically. |
| Low | No app-level reduced-motion preference or animation control was found; native player timers use coroutine delays, and the audited Compose source has no custom animated chart transitions. | Keep future motion nonessential and honor platform animator/reduced-motion settings if motion is added. |

No claim of a universal 200%-text guarantee is made. Existing tests provide useful examples but do not cover every screen, dynamic font setting, locale, TalkBack order, keyboard path, reduced-motion setting or contrast mode.

## 5. User-visible terminology inventory (groups A–I)

The Compose UI has no `stringResource`/`R.string` call sites and no `<string>` resources in the native or reused legacy resource directories. The listed UI copy is therefore hard-coded in Kotlin (or is a dynamic value derived from persisted state). Catalog names and technique copy are data in `assets/native-library/catalog.json`, not localized string resources. This inventory covers the user-visible terminology relevant to the nine requested groups; it is not a dump of unrelated plan-builder instructional prose.

| Group | Exact current text / variants | Source and screen | Current classification |
|---|---|---|---|
| **A. Workout status** | Persisted values `planned`, `in_progress`, `completed`, `missed`, `skipped`; `PlannedWorkoutCard` renders `${s.status.uppercase()}` (`PLANNED`, `IN_PROGRESS`, `COMPLETED`, `MISSED`, `SKIPPED`). Workout detail heading also uppercases the raw status. Plan badge uses uppercase plan status (for example `DRAFT`, `APPROVED`, `PAUSED`). | `PlannedWorkoutCard` and `PlanOverview` in [TrainingScreens.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/TrainingScreens.kt); heading in [WorkoutScreen.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutScreen.kt); enum validation in [PlanBackup.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/PlanBackup.kt). | **RENAME** raw enum forms to plain user-facing status copy without changing stored values. Completion display also **NEEDS SOURCE VERIFICATION** because P0-1 can create Completed with an empty/partial log. |
| **A. Workout status** | `LINKED SOURCE RUN`; `N RECORDED MIN`; `DURATION UNRECORDED`; `N PLANNED MIN`; `Open saved workout →`; `Saved entry draft · resume without marking complete`. | `PlannedWorkoutCard`, `TrainingScreens.kt`; used on Training, Home, Library sessions and Activities workout logs. | Recorded/target distinctions are useful (**DIRECT**); simplify the all-caps metadata and make action/state labels consistent (**RENAME**). |
| **B. Actions — training** | `Preview, start or manage →`; `Open saved workout →`; `Leave workout`; `Review & finish`; `Review workout`; `Return to guided workout`; `Previous`; `Next exercise`; `Form guide`; `Adjust set`; `Review set`; `Confirm {value} {unit} & rest`; `Continue to next set`; `Saving set…`; `Finish rest & continue`; `Continue`. | `PlannedWorkoutCard` in [TrainingScreens.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/TrainingScreens.kt); player in [WorkoutPlayer.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayer.kt); actions in [WorkoutScreen.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutScreen.kt). | Guided strength/calisthenics actions are broadly **DIRECT**; generic card action **RENAME** for running (P0-5). |
| **B. Actions — activity/provider** | `Refresh history`; `Synchronize Intervals.icu`; `Check connection`; `Load recorded streams & laps`; `Refresh recorded streams`; `Link to planned run`; `Unlink recorded run`; `View activity` is not present as a standard CTA (activity cards open on tap). | `NativeMainActivity.kt`, [ActivityPlanLink.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/ActivityPlanLink.kt), Activity Detail route in `NativeMainActivity`. | The explicit sync/link/view actions are behavior-backed where present (**DIRECT**); do not imply a run recorder. Surface the activity-card tap as a visible “View activity” action if reconstruction needs a textual CTA (**RENAME**, behavior remains existing navigation). |
| **B. Actions — plans/library** | `Create a plan`; `Create / edit a reviewed draft`; `Approve this plan`; `Resume this plan`; `Edit inputs / create draft`; `Pause plan`; `Archive plan · keep records`; `Move workout`; `Save move`; `Missed / skipped`; `Save completed workout`; `Save progress for offline reopening`; `Export plans, logs & feedback`; `Prepare practice session`; `Build a running plan`. | `TrainingScreens.kt`, `WorkoutScreen.kt`, `NativeLibrary.kt`, `PracticeBuilder.kt`, `PlanBuilder.kt`. | Preserve action semantics (**DIRECT**); distinguish “Resume plan” from “Resume workout” in navigation hierarchy (**RENAME** if ambiguity appears). |
| **C. Persistence** | `Entry draft saved on this device · not yet confirmed as training`; `Entries auto-save on this device. Save progress or completion to confirm what you performed.`; `Saved entry draft · resume without marking complete`; `Your actual set`; `Enter what you performed. These entries remain a draft until you confirm the set in the workout.`; `Set saved · …`; `Saved workout`; `Saved … · N sets with recorded values`; `Saving set…`; `Saved offline`; `Save progress for offline reopening`. | `WorkoutScreen.kt`, `WorkoutPlayer.kt`, `TrainingScreens.kt`, `NativeMainActivity.SyncStatus`. | Draft-vs-confirmed language mostly **DIRECT**. “Saved workout” must not imply correctness merely from status while P0-1 remains. Do not add a Pending label; no user-visible queued plan operation is established by these strings. |
| **C. Persistence** | `Any unsaved entries for this exercise will be cleared. Previously saved sets cannot be replaced.`; `Only enter values you actually performed. Blank values stay unrecorded; no target is copied into your history.`; `Completion is your recorded confirmation, not a sensor-verified activity.` | Alternative dialog and record screen in `WorkoutScreen.kt`. | Useful boundary/actual-value language (**DIRECT**); keep explicit distinction between local draft, persisted actual set and completed state. |
| **D. Synchronization** | `Offline · Saved activities remain available`; `Working… Saved data remains on this device.`; `Saved offline · refreshed {date}`; `No completed history refresh recorded`; `Refreshing saved history`; `Unable to refresh. Saved history is still available.`; `Connection or secure storage is unavailable. Saved activities are retained; retry when connected.`; `This operation could not be verified. Existing records were retained. Please retry.` | Offline banner and `SyncStatus` in [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt); error messages in [TrainingRepository.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt). | **DIRECT** where an actual refresh/cache state exists; avoid making “Saved offline” sound like provider synchronization success. |
| **D. Synchronization** | `Connection not checked`; `Not connected`; `Connected source needs attention`; `Saved connection · {connected/disconnected/error/syncing}`; `Last checked {date}`; `Source last synchronized {date}`; `Sign in again to synchronize. Your saved activities remain available.`; `Sign in again to synchronize. Saved activities remain available.` | Intervals.icu Settings section in `NativeMainActivity.kt`; state vocabulary in [IntervalConnection.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/IntervalConnection.kt), authentication message in `TrainingRepository.kt`. | Raw `error`/`syncing` values are not friendly user state; **RENAME** to localized plain language. Connectivity/secure-storage failures are grouped in one user message; the UI does not clearly distinguish authorization from network failures (**NEEDS SOURCE VERIFICATION** before promising recovery action). |
| **E. Week terminology** | `Week` / `Month` view toggle; month title `{Month} {year}`; week title `Week {n} of {plan.weeks}`; week summary `{n} sessions · {n} completed · {minutes} planned min`; `THIS WEEK`; Home `UP NEXT` / `TODAY`; plan range `{ISO start} – {ISO end} · {n} weeks`; selected day `EEEE, d MMMM`; card date `EEE, d MMM`; weekly dashboard `LAST 7 DAYS`, explicit start/end dates and `Previous 7 days`. | Calendar and plan in [TrainingScreens.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/TrainingScreens.kt); Overview in [WeeklyDashboard.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WeeklyDashboard.kt). | Explicit dashboard ranges are **DIRECT**. `Week N of M` is a plan-relative seven-day slice, while `THIS WEEK` is a Monday–Sunday calendar window; standardize as **RENAME** to `Plan week N of M` plus visible date range for plan week, and explicit calendar date ranges for calendar weeks. |
| **F. Missed / Skipped** | `Missed / skipped`; dialog title `Why was this session missed?`; reasons `Time`, `Fatigue`, `Soreness`, `Illness`, `Other`; actions `Mark missed`, `Skip`, `Cancel`; explanation `No catch-up volume is added. Any recovery suggestion requires your explicit choice.` | `WorkoutScreen.kt`; persisted transition/reason validation in [PlanOperations.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/PlanOperations.kt). | Distinct missed vs skipped actions are **DIRECT**. The title’s “missed” does not describe the separate “Skip” action; **RENAME** the prompt/definitions so users understand the distinction before saving either state. |
| **F. Missed / Skipped** | `Your recent missed-workout reason prompted a recovery suggestion. Review the shorter alternative or choose rest; there is no extra catch-up volume.` | `HomePlanHero` in `TrainingScreens.kt`. | Behavior-backed but terse; **RENAME** only if a concise approved term is chosen. Do not imply automatic status changes. |
| **G. Empty / unavailable** | `No saved activities yet`; `No activities cached`; `No activity available`; `This activity is not in the current account’s local cache.`; `No workout scheduled`; `No eligible running sessions. Create and approve a running plan first.`; `No workout logs saved for this account yet.` | Overview/Activities/Activity Detail in `NativeMainActivity.kt`; Calendar and `ActivityPlanLink.kt`; Library sessions in `NativeLibrary.kt`. | **DIRECT** when attached to the correct cache/account context. |
| **G. Empty / unavailable** | `Not recorded`; `No recorded GPS route. Indoor activities and sources without coordinates cannot show a route.`; `No recorded laps returned. No kilometer splits have been invented.`; `No usable {metric} time-series recorded.`; `No recorded time-series or GPS points were returned. Summary metrics remain available.`; `No authentic wellness records cached. No readiness score or missing measurements are estimated.`; `No entries match. Try a broader search or another discipline.` | `ActivityRecord.kt`, `RecordedActivityDetail.kt`, `WeeklyDashboard.kt`, `NativeLibrary.kt`. | Honest missing-data copy is **DIRECT**; preserve rather than substitute zeros, inferred splits, route art or readiness scores. |
| **G. Empty / unavailable** | `This workout is not saved for the current account.`; `Workout unavailable`; `The offline library could not be opened. Original assets remain preserved in Settings.`; `No exact offline demonstration is available…`; `Original assets remain preserved…`. | `WorkoutScreen.kt`, `NativeLibrary.kt`, `WorkoutPlayer.kt`. | **DIRECT** with an optional next action; do not convert failure to success-shaped empty content. |
| **H. Source provenance** | Labels from `ActivityRecord.sourceLabel`: `Intervals.icu`, `Garmin`, `FIT import`, `Manual`, `Health Connect`; unknown source returns the raw source identifier. UI labels `ACTIVITY SOURCE`, `Source last synchronized`, `recorded value`, `{source} · recorded value`, `ACTIVITY SOURCE`, and “Values come from the activity source…”. | Source-label mapping in [ActivityRecord.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/ActivityRecord.kt); Settings/Activity Detail/Wellness in `NativeMainActivity.kt` and `WeeklyDashboard.kt`. | A display mapping exists, but a mapping is **not evidence** that a provider/parser/ingestion workflow exists. `Garmin` and `FIT import` are **NEEDS SOURCE VERIFICATION** before being presented as active data origins. No device-model field is displayed or stored in `ActivityRecord`. |
| **H. Source provenance** | `Uses your existing source connection. Manage credentials in your existing StrideLab integration settings, then check again here. Native setup and backfill are not yet available.`; `Existing local prototype data remains in the legacy app.` | Intervals settings and Overview empty-state copy in `NativeMainActivity.kt`. | **DIRECT** and important scope honesty; keep provider setup limitations visible until implementation is verified. |
| **I. Wellness** | `WELLNESS RECORDS`; `Resting heart rate`; `HRV (source metric)`; `HRV SDNN`; `Sleep duration`; `Steps`; dynamic `{value} {unit}`; `{date} · {source} · recorded value`; `Refresh wellness`. | [WeeklyDashboard.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WeeklyDashboard.kt), values/source/date from [WellnessRecord.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WellnessRecord.kt). | Preserve user/source-provided units and recorded date (**DIRECT**); no measurement method/device is shown beyond source label. |
| **I. Wellness** | `Latest available records retain their source date. HRV methods differ; no cross-method comparison or training adjustment is inferred.`; empty copy says no readiness score or missing measures are estimated. | `WellnessOverview` in `WeeklyDashboard.kt`. | **DIRECT** and evidence-aligned. Do not turn missing/stale records into an invented score. |

### Approved terminology contract mapping

| Contract | Audit classification and implementation implication |
|---|---|
| Running workouts must not advertise Start as though recording begins. | **RENAME.** Replace only the generic run-card action in `PlannedWorkoutCard`; use an honest plan-view action. Put `Sync activities`, `Link recorded activity` and `View activity` on their actual activity surfaces. There is no in-app run recorder to support a run-start claim. |
| Strength/calisthenics may use Start, Resume and Review. | **DIRECT** for guided-player navigation/review where those actions exist. Do not rename timer `Start hold` into workout completion or actual set recording. |
| Completed means successfully persisted completion. | **NEEDS SOURCE VERIFICATION** / underlying rule first. UI wording cannot repair `PlanOperations.record` allowing empty/partial completion. Preserve existing status values; do not add a new persisted status to solve presentation. |
| Unsaved = memory only; Draft = locally persisted, unconfirmed; Recorded = persisted actual; Pending only for a real queued operation. | **DIRECT** distinctions exist in player and workout copy, but `Pending` is not an evidenced user-facing native workout state. Use “Saving…” only while a write is actually in progress; never relabel a draft as recorded. |
| Calendar weeks show explicit dates; plan weeks say “Plan week N of M.” | **RENAME.** `TrainingCalendar` currently says `Week N of M` and has no visible week-range header. Show plan-relative date span and call it a plan week; retain explicit date labels in calendar views. |
| Do not display unverified originating devices as factual provenance. | **NEEDS SOURCE VERIFICATION.** Existing `sourceLabel` handles source names but ActivityRecord has no device identity. Do not infer a watch model from `Garmin`, Intervals.icu, FIT or imported source labels. |
| Missed and skipped remain distinct user choices, not time-triggered status changes. | **DIRECT** in current actions/reasons. Make the definitions clear in language; preserve underlying behavior and do not imply automation. |

## 6. T0 P0 findings — UI implications

| T0 finding | Current UI representation | Exact surfaces for later UI work | Boundary |
|---|---|---|---|
| **P0-1: completion accepts empty/partial logs** | `PlannedWorkoutCard` renders status as uppercase enum text and for completed sessions displays `LINKED SOURCE RUN`, recorded minutes or `DURATION UNRECORDED`; `PlanOverview`, calendar dots and `HomePlanHero` count completed by status. `WorkoutScreen` switches to “SAVED WORKOUT”/read-only completed presentation from `session.status=="completed"`. | `PlannedWorkoutCard`, `PlanOverview`, `TrainingCalendar.DayCell`, `HomePlanHero`, Activities workout-log list, `WorkoutScreen`, plus Overview `DashboardSummary`/`WeeklyDashboard` aggregation. | UI must not imply a successful recorded workout until the completion rule is corrected in `PlanOperations.record` and persistence behavior is verified. Do not mask the defect with a new status or solely change the badge. |
| **P0-3: link uniqueness is not database-enforced/transactional** | `ActivityPlanLink` displays one current link, offers `Link to planned run` and `Unlink recorded run`, and lists apparently unlinked plan sessions. `PlannedWorkoutCard` and `WorkoutScreen` show a linked-source label/context. | `ActivityPlanLink` selection/linked/unlinked states; activity detail card; linked workout card/calendar/history entry. | UI should not signal database-level uniqueness or race-proof behavior. Remediation is repository/storage transaction/invariant work; no database change is part of this audit. |
| **P0-5: misleading running action** | `PlannedWorkoutCard` renders the same `Preview, start or manage →` for every non-completed discipline. The card opens `WorkoutScreen`; running has no in-app run recorder. | Shared card used by `HomePlanHero`, `TrainingScreen` calendar, Activities’ native workout logs and Library Sessions. Also validate the running detail route’s available actual actions in `WorkoutScreen`; do not advertise recording there. | Use discipline-aware copy/action routing. An honest planned-run detail/open action is possible; activity sync/link/view remains on recorded activity screens. Do not implement recording in T5A. |

These remain open findings on the pinned source; this audit neither resolves nor modifies them.

## 7. Visual-first screen readiness

| Approved screen | Existing support | Verified reusable base | Missing/new work or upstream dependency |
|---|---|---|---|
| **Overview** | **Partial.** `WeeklyDashboard` computes a real last-seven-days window, recorded time/distance, daily 4dp bars, discipline-count text and previous-window context. `DashboardSummary` distinguishes cached imported runs from dated confirmed native logs and deduplicates linked runs. `WellnessOverview` shows latest cached records, source/date/unit; `SyncStatus` gives refresh status. | `Screen`, `SignalCard`, `Metric`/`MetricPair`, `WeeklyDashboard`, `RecordedDurationChart`, `WellnessOverview`, `SyncStatus`. | No compact seven-day multi-discipline visualization, planned-vs-recorded visual comparison or rich discipline distribution graphic. Sync status is one text line. Use only real cached measures; missingness and cache freshness remain explicit. |
| **Training and Calendar** | **Partial, strong functional base.** Week/Month/Plan controls, plan selector, multiple sessions on a date, session details, completion counts and moving/plan action flows exist. Date cells expose multiple-session count and semantic content description. | `TrainingScreen`, `TrainingCalendar`, `DayCell`, `PlannedWorkoutCard`, `PlanOverview`, `PlanFeedbackForm`. | Cards are text-heavy; no shared status chip, compact agenda or dedicated progress visualization. Week title is ambiguous relative to calendar week. P0-1/P0-5 limit status/action fidelity. Weekly day cells are narrow; no user-configurable week start. |
| **Guided Player** | **Partial, strongest interaction foundation.** Exercise/set progression, actual-vs-prescribed values, saved actual set confirmation, progress bar, hold/rest timers, pause/resume rest, +15 sec, form guide and stable footer actions are present. Draft persistence is handled outside the player. | `WorkoutScreen`, `WorkoutPlayer`, `SignalButton`, `SignalCard`, catalog-backed `LibraryImage`; `WorkoutPlayerTest`. | Most exercises have written cues; exact offline demonstrations are conditional, not universal. Screen remains vertically textual; timer ticks lack deliberate announcement control. Preserve local write/error and saved-set behavior during any visual implementation. |
| **Workout Details** | **Partial.** Running `PlannedSession.steps` and targets can be shown as text; strength/calisthenics show prescription and actual set fields/logs. Running source metrics are displayed separately from planned targets. | `WorkoutScreen`, `PlannedWorkoutCard`, `PlanOperations`, `ActivityPlanLink`. | No structured interval segment visualization/data contract is established by UI source; `steps` is text. Comparisons are mostly prose/fields. Do not fabricate interval execution, target-vs-actual or sensor data. |
| **Recorded Activity Detail** | **Partial, substantially short of approved experience.** Summary metrics, source/date, actual recorded laps and optional loaded sample graphs/route trace exist, with missing-data messaging and sample selection on taps. | `ActivityRecord`, `ActivityDetail`, `RecordedActivityDetail`, `SampleGraph`, `ActivityIntelligence`, `ActivityPlanLink`, `MetricPair`. | No automatic route fetch at detail entry; user must load streams. Current route is a local coordinate trace without street tiles/map SDK, not immersive map. No draggable info panel, Overview/Splits/Charts/Intelligence tabs, split-selection control, linked map/chart highlighting, or semantic chart selection alternatives. Genuine data is source-dependent. |
| **Exercise Library** | **Partial.** Bundled catalog supports filters, saved entries/collections, instructions, equipment, alternatives, easier regressions/progression descriptions and some credited/licensed images. | `LibraryScreen`, `LibraryImage`, `SignalChip`, `ShelfFilter`, `PracticeBuilder`, `ExerciseAlternatives`, bundled catalog. | Image coverage is conditional; `LibraryImage` is absent if catalog has no `image`, and exact-image fit is explicit. No claim that every movement has an image/video or complete equipment/progression data. New visual components must retain licensing/provenance and exercise identity. |

## 8. Focused design-system recommendations

### Preserve

1. Keep `StrideTheme` as the app-level Compose theme and retain the near-black canvas, dark charcoal cards, legible warm-white foreground and lime brand identity.
2. Keep the existing running blue / strength orange / calisthenics purple mapping as the **currently implemented** palette; label conditioning’s shared purple as a known gap, not as an approved discipline token.
3. Keep `Screen`, `SignalCard`, `MetricPair`, `ActivityRow`, `PlannedWorkoutCard`, `TrainingCalendar`, `WorkoutPlayer`, `RecordedActivityDetail` and their state/data boundaries where behavior still fits.
4. Preserve source, units, actual-vs-target distinction, explicit missing-data copy, offline persistence, draft/recorded distinction, provider dates and image provenance. Charts may visualize only actual stored values.

### Standardize in implementation (not implemented here)

- Centralize color tokens and replace duplicate hard-coded run/strength literals with the existing semantic discipline mapping; define no new status/warning/success/conditioning color without review and contrast testing.
- Separate color roles (brand action, discipline identity, status, chart series, border) rather than reusing lime/secondary indiscriminately.
- Introduce one reusable plain-language status presentation and one discipline-aware action row; retain persisted enum values and T0 status semantics.
- Reduce card metadata to short, scannable facts while keeping full rationale in accessible detail/on-demand sections. Do not compact by deleting working actions or provenance.
- Standardize action terminology against the contract table, including explicit date spans for plan weeks and calendar weeks.
- Make custom Canvas charts/routes operable and understandable without touch/color: provide accessible textual summaries plus semantic step/sample controls; preserve exact selected recorded sample behavior.
- Review control boundaries (especially 1.45:1 outlineVariant), all custom hit targets (48dp minimum), keyboard/focus paths, live status announcements, 200% font and reduced-motion behavior.

### Visualizations supported by current data vs data/integration needed

| Can be visualized now from existing stored records | Requires new structure, verified provider support or integration |
|---|---|
| Dated seven-day actual time/distance totals, cached activity history, actual laps and available HR/pace/elevation samples, actual workout set counts/values, actual source/dates/units, selected calendar-date sessions, recorded wellness values. | Automatic genuine route acquisition if streams are not cached; street-map tiles/SDK; validated running intervals and execution compliance; richer device/provider identity; split/map/chart synchronized selection; missing-data/quality provenance beyond current fields; new readiness or coaching derivations. |

No mockups or product-scope expansion are included.

## 9. Exact source inventory for later UI work

All paths below are on the pinned source SHA, not borrowed from a documentation branch.

| Work area | Existing sources to inspect/reuse |
|---|---|
| Theme and shared UI | [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) — `StrideTheme`, `NativeStrideContent`, `Screen`, `SignalCard`, `ActivityRow`, `RecordedDurationChart`, `Metric`, `MetricPair`, `SyncStatus`, `BrandMark`, `LibraryGrid`; [NativeLibrary.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeLibrary.kt) — `SignalButton`, `SignalChip`, `ShelfFilter`, `LibraryImage`, discipline label/color. |
| Training/Calendar | [TrainingScreens.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/TrainingScreens.kt) — `TrainingScreen`, `TrainingCalendar`, `DayCell`, `PlanOverview`, `PlannedWorkoutCard`, `HomePlanHero`; [PlanOperations.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/PlanOperations.kt) — action/status behavior. |
| Overview/wellness | [WeeklyDashboard.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WeeklyDashboard.kt) — `WeeklyDashboard`, `WellnessOverview`; [DashboardSummary.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/DashboardSummary.kt) — aggregate semantics; [WellnessRecord.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WellnessRecord.kt). |
| Guided Player/workout details | [WorkoutPlayer.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayer.kt); [WorkoutScreen.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutScreen.kt); [WorkoutEntryDraft.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEntryDraft.kt); [EngineContracts.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt) — prescriptions, sessions, workout logs; [TrainingRepository.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt) — local persistence and errors. |
| Activity Detail and link | [NativeMainActivity.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt) — Activity Detail destination; [ActivityRecord.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/ActivityRecord.kt); [ActivityDetail.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/ActivityDetail.kt); [RecordedActivityDetail.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/RecordedActivityDetail.kt); [ActivityIntelligence.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/ActivityIntelligence.kt); [ActivityPlanLink.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/ActivityPlanLink.kt). |
| Library/exercise detail | [NativeLibrary.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/NativeLibrary.kt); [PracticeBuilder.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/PracticeBuilder.kt); [ExerciseAlternatives.kt](../../native-android/app/src/main/java/io/stridelab/privatealpha/ExerciseAlternatives.kt); `native-android/app/src/main/assets/native-library/catalog.json` and bundled image assets. |
| Resource/window theme | `native-android/app/src/main/res/values/styles.xml`; bundled font `native-android/app/src/main/res/font/bricolage_variable.ttf`; dependency versions in `native-android/app/build.gradle`. No native strings resource catalog was found. |
| Existing UI regression coverage | `native-android/app/src/androidTest/java/io/stridelab/privatealpha/NativeVisualTest.kt`, `WorkoutPlayerTest.kt`, `Engine1UiTest.kt`, `NativeNavigationTest.kt`, `NativeTabRootTest.kt`, `ActivityIntelligenceUiTest.kt`; these do not constitute a complete accessibility suite. |

## 10. Outstanding technical questions for implementation planning

1. Should the native app introduce a string-resource/localization layer before or as part of screen reconstruction? Currently visible Compose copy is hard-coded.
2. Which users/locales/time zones and calendar week-start conventions must be supported? Current `TrainingCalendar` week is plan-relative; Home’s `THIS WEEK` uses Monday.
3. What plain-language distinction does product want between **Missed** and **Skipped**, while retaining existing status meanings?
4. What exact valid completion criterion should follow the separate P0-1 correction, and which empty/partial logs remain valid as progress?
5. What is the approved data/API contract for actual running intervals and structured segment targets? Current session `steps` is text and does not establish an interval execution model.
6. Which recorded activity sources actually populate `source`, `ActivityDetail`, wellness and GPS fields in production? Source-label strings alone do not prove Garmin/FIT ingestion.
7. Should the route remain a strictly offline trace, or will a permitted map provider/tiles contract be selected later? No map SDK is wired into the inspected Compose code.
8. What semantic non-color encoding and alternative chart selection controls are preferred for completion/discipline marks and recorded stream graphs?
9. Which typography scales, device widths, landscape/tablet layouts, keyboard/switch paths, and reduced-motion settings are release acceptance criteria?
10. Which existing charts are meant to be designed as historical totals versus planned-vs-recorded comparisons? Only the former has direct current data support.

## 11. Working-tree audit and checkpoint instructions

Before creating this report, the new branch was clean at the pinned commit. Pre-commit commands and observed expected result:

```text
git rev-parse HEAD
25be8f3828374d1e539c7e825afa20fbcb652b56

git status --porcelain=v1 --untracked-files=all
?? docs/reports/T5A-DESIGN-SYSTEM-AUDIT.md

git diff --stat
(empty; the sole deliverable is untracked until staged)

git ls-files --others --exclude-standard
docs/reports/T5A-DESIGN-SYSTEM-AUDIT.md
```

No modified tracked files or unrelated untracked files were present at the pre-commit audit. The sole report file is the only allowed deliverable. No tests/builds are required for this documentation-only audit; none were run.

**Checkpoint:** `docs/t5a-design-system-audit-25be8f3` branches directly from source SHA `25be8f3828374d1e539c7e825afa20fbcb652b56`. The next action is review this report and its draft PR against the verified checkpoint; do not switch the audited baseline to the T0 docs branch. No source implementation, visual reconstruction, Garmin integration, Engine 2.0 work, merge or APK action is authorized by this report.
