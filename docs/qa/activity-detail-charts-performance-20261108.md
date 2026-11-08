# Activity Detail: Splits and Charts responsiveness

## Source finding
Overview is mostly lightweight presentation. When entering Charts, the old
`TracksideActivitySourceChart` item was building metric arrays, segment/gap
lists, trimmed display scales (including a sorted full stream), insight text
and pause evidence **during Compose composition**, potentially for four
independent 20,000-sample streams. Heart-rate zones also scanned the full
source data during the initial chart composition.

This can interrupt sheet gestures and smooth scrolling on physical devices.
It is a source-level hypothesis, not a device-measured timing result.

## Change
- `ActivityChartPreparation` is a pure presentation model. One immutable
  `ActivityDetail` sample list is reused for all four metric computations.
- Chart preparation is performed on `Dispatchers.Default`, progressively
  publishing ready datasets. Old in-flight work is cancelled when the
  recording changes via the `produceState` key.
- Full original measured points are retained for displayed arithmetic
  averages, ranges, chart analysis, selected cursor interpretation and
  privacy/pause evidence. Rendering remains bounded to the existing
  approximately 450 sampled plotted points per metric; pause circles are
  additionally bounded to approximately 450 while the source pause evidence
  count stays full.
- Full heart-rate zone distribution is also moved off the Compose UI thread.
- No FIT/database/source-provider access, saved GPS route geometry, device
  preferences or activity data is changed.

## Acceptance required on the physical device
1. Compare Overview -> Splits -> Charts transitions for a short run and a
   large Garmin activity. Verify both section-tab taps and manual scroll.
2. Confirm sheets still drag/collapse and the map can still independently
   pan/pinch while chart preparation is running.
3. Verify each chart's distance/time cursor, pace/HR/elevation/cadence
   measurements, axis labels, average line, selected map point and chart
   analysis against the same saved recording before this patch.
4. Check that pause gaps are not joined and amber pause evidence remains
   visible. Verify missing metrics show no invented chart, and transparent
   loading placeholders clear once preparation finishes.
5. Validate long recordings with up to 20,000 FIT samples and multiple
   pauses, 360dp/412dp layouts, accessibility text, portrait/rotation,
   offline reopen and repeat visits.
6. Capture a device frame trace/FrameMetrics report before/after when
   possible. Report actual timing separately; no FPS improvement is claimed
   from source review alone.

**Known limit:** Source-level changes and a pure JVM regression test are
committed; the actual Android build, on-device visual defects, profiler
measurements and user screenshot acceptance are outstanding.
