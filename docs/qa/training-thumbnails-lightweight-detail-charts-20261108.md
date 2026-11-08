# Training previews and lightweight Activity Detail

## Root causes verified from source

- **Today** displays small persistent account-scoped WebP previews stored in
  `TrainingState.gpsThumbnails`. The Training timeline previously read
  `TrainingState.details`, reconstructed a `RecordedGpsRoute` from full FIT
  sample arrays, and drew that route inside each card. The repository holds
  only a few full details hot, so many Training cards showed no GPS image or
  re-rendered routes unnecessarily, even when a thumbnail was already saved.
- Activity Detail formerly had a separate "Paused time" summary and drew
  per-chart pause bands, dots, and auxiliary notices. Those cost UI work and
  visual space. The source pause records remain important for truthful chart
  breaks and GPS handling.

## Implementation

- Training cards use the same persisted WebP images as Today. An explicit
  bounded nearby-date query requests previews for up to **48 source IDs**
  around the visible date, including grouped activity aliases. This is not a
  full-history scan and does not request Garmin or FIT data if a WebP exists.
- The repository retains at most **96 decoded bitmaps** in memory. Older
  bitmaps are still on disk, account-separated, and are reloaded when needed.
  The already implemented cold-start restore for the newest 40 activities
  remains unchanged.
- Removed Activity Detail's **visible timer-pause summary, amber pause
  bands, pause dots and their note**, and the optional outlier dots and
  scale-caption decoration. Original `ActivityDetail.pauseIntervals`,
  timestamps, raw measurements and GPS route are NOT removed. Chart line
  segments still break at source pause intervals and missing/gapped samples.
- Chart preparation uses a cursor through validated sorted pause intervals
  instead of rescanning the entire pause list for every source measurement.
  Each genuine chart section now draws one line Path instead of hundreds of
  separate draw calls. Full-stream analysis, statistics, HR zones, splits,
  axis labels, average lines, cursor and map interaction remain available.
- The earlier off-UI-thread chart preparation and heart-rate zone calculation
  from draft PR #62 are included. This is a presentation/performance pass,
  not a change to the workout, privacy or recorded-data logic.

## Physical-device acceptance

1. Force-stop/reopen after visiting GPS activities. Confirm Today and
   Training show their existing WebPs quickly, including when offline.
2. Scroll Training through 60+ recorded activities across many dates; check
   that previews appear for older pages and that scrolling remains responsive.
   Check multiple source aliases, genuinely no-GPS activities and the
   absence of fake routes. Revisit dates after viewing over 96 thumbnails.
3. Test Activity Detail Overview -> Splits -> Charts and dragging the sheet
   with short and long (up to 20k samples) Garmin recordings. Verify the
   no-pause-overlays visual and responsiveness on physical hardware.
4. Verify chart lines do NOT connect across real missing measurements or
   timer pauses. Check all original measured values, HR zones, chart
   analysis, x/y axes and selection sync with map; the chart cursor still
   uses real recorded timestamps.
5. Compare lag/frame traces and startup time before/after if available.
   Check landscape, 360/412dp screens and larger accessibility font size.
6. Do not clear app data or uninstall; preserve original Garmin recordings,
   the permanent thumbnail cache and user's plan/feedback records.

**Verification:** GitHub source-level and structural checks only. No Gradle
compile, instrumentation, physical-device trace or screenshot comparison was
available in the connected session. Success must be independently confirmed
by the owner's local build and device run.
