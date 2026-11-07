# Long-run Activity Detail crash: memory-pressure mitigation

Branch: `fix/long-run-activity-detail-crash-20261108`
Base: `fix/garmin-full-recordings-no-2mb-cap-20261108` at `2ac233b`.

## Report and confidence

User reports that opening long Garmin running activities crashes the Android app
after activity-detail JSON persistence was allowed beyond 2 MiB. The actual
device exception / native crash trace has **not** been captured in this review.
Memory/graphics pressure is a plausible explanation, **not confirmed root cause**.
This is a targeted mitigation pending Android device verification and Logcat.

## What changed

1. `RecordedGpsRoute.renderSegments` selects at most 2,000 actual recorded
   coordinates per visual segment for large traces. It retains first and last
   points, antimeridian separation and source ordering. The full data stays
   in `ActivityDetail.samples`; raw `RecordedGpsRoute.segments` and
   continuous `displaySegments` are not altered.
2. `RecordedGpsRoute.displayGeoJson`, offline fallback Canvas and cached
   198×192 route thumbnail draw the bounded display geometry. Today's
   continuous-Strava-style display still joins existing GPS endpoints
   visually across pauses, without claiming intermediate sampled positions.
3. Colored map-mode GeoJSON caps individually styled edges at 2,000, while
   determining its color minimum/maximum from every valid source edge.
   Actual chart/analysis stream inputs remain full resolution.
4. Heavy chart widgets on the expanded sheet are no longer precomposed while
   a >4,000-sample recording is collapsed. Normal/short-run sheets keep the
   previous precomposition behavior for smooth gestures.
5. No changes to FIT decoding, recorded source coordinates, timestamps, pace,
   altitude, HR, cadence, lap data, pause metadata, engine, cloud backup or
   local SQLite schema. No data reset or recording deletion is required.

## Added regression tests

`LongRunMapRenderingTest`:
- 15,000 synthetic GPS samples remain present in source, while the route map
  serializes <= 2,000 selected original fixes.
- Analysis overlay is <= 2,000 features, and its min/max still reflect source
  extrema even when an outlier point is not in the displayed subset.
- Short-run geometry is not downsampled.

## Required device QA

- Reproduce the crash in the previous build on a long run; capture Logcat's
  `FATAL EXCEPTION`, `OutOfMemoryError`, or native `SIGSEGV` block.
- Install this branch without clearing app data, reopen the SAME long run
  online then offline. Verify route, thumbnails, split/HR charts, and sheet.
- Test full-screen map and pace/elevation/HR colored overlays on 15k sample run.
- Exercise pause/resume: line continuous but original pause analysis unchanged.
- Switch between multiple long runs and short runs, force-stop and reopen,
  confirm memory remains bounded and FIT details are not re-downloaded.
- Run focused JVM and instrumentation suites; build has not yet been executed
  in this GitHub-only workflow. Do not merge without device crash reproduction
  and before/after validation.

If the crash persists, prioritize the actual device Logcat stack trace rather
than broadening the rendering changes speculatively.
