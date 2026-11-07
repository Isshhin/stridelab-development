# Garmin full FIT persistence + continuous GPS presentation

Branch: `fix/garmin-full-recordings-no-2mb-cap-20261108`
Base: `feature/offline-local-profile-optional-cloud-20261108`, head `754cd76`

## Problem investigation

Garmin activity-summary ingestion is separate from full FIT import. A summary
is durably saved even if its FIT cannot be retrieved/verified/stored. This is
expected partial-sync resilience, not proof that Garmin did not record GPS.

Confirmed code-level failure paths:
- `RecordPolicy.record` rejected any serialized JSON over 2 MiB, including
  `activity_detail`. Long Garmin FIT recordings expand to many repeated sample
  JSON keys and can exceed this despite their smaller source ZIP/FIT size.
- A single Garmin FIT HTTP 404/410 was previously treated as a service-wide
  interruption in the recording loop; all following missing recordings in that
  pass were skipped.
- Automatic sync imports at most three missing FITs per eligible check;
  foreground sync imports at most 24 missing FITs per pass. Older runs can
  remain summary-only until backfill.
- Deprioritized failures are retried automatically only after a 24-hour
  cooldown. Foreground manual sync now permits retrying those deferred entries
  after other eligible work.
- The Activity Detail map previously described not-yet-loaded recordings as
  "No recorded GPS track", which could not distinguish an absent FIT from a
  genuine recorded run without GPS coordinates.

No assumption is made that any specific user FIT file hit the size cap;
device or sample-file verification is still required.

## Fixes

1. `activity_detail` uses full JSON-object structural validation without the
   generic 2 MiB size restriction. Other small record kinds retain their
   existing size/identifier policy. SQLite TEXT stores the complete data
   transactionally; source FIT checksum/format, sample count, source match,
   account isolation and chronology checks remain in place.
2. Manual sync overrides the previous failed-detail cooldown and continues
   new/healthy FIT imports first; per-activity 404/410 errors are skipped and
   recorded rather than aborting the batch. Authentication/network/rate limits
   and service-wide failures still stop the batch safely.
3. Activity Detail auto-download of a missing Garmin FIT and the explicit
   "Load Garmin recording" control no longer depend on StrideLab cloud sign-in.
4. The UI uses "Recording not loaded yet" when no detail is available.
5. GPS routes in the main map, offline fallback and cached Today thumbnails
   draw a single visual line connecting successive **actual** recorded GPS
   fixes, including on either side of timer pauses. No fabricated sample
   coordinates, extra distance, changed pause timestamps or edited source data.
   The factual route segments and gap classifications remain available for
   analysis. The display deliberately never connects across the anti-meridian.
   Repeated `GPS gap` labels and dashed gap overlays are removed.
6. WebP cache format advances from v2 to v3 so thumbnails created with the
   older split-line styling are automatically regenerated on cache misses.

## Regression checks added

- Java record policy: over-2 MiB detail is allowed, oversized summaries and
  malformed JSON still fail.
- Android SQLite persistence: 12,000 complete recorded GPS points (>2 MiB)
  survive an offline database close/reopen without truncation.
- Kotlin route geometry: displayed polyline joins actual fixes across
  missing/pause intervals while underlying original segments/gap metadata
  remain unchanged; no anti-meridian stitch.
- Android thumbnail renderer: a route composed of disconnected source fixes
  produces a cached continuous visual WebP.
- Garmin sync fixture: an individually missing FIT export (404) does not stop
  subsequent recording attempts.

## Device QA required before merging

1. Build/install the branch over an existing local profile (no data reset).
2. Open a known long run (Oct 31 or Nov 7 example) and retry "Load Garmin
   recording" if still summary-only. Inspect the per-record error if it fails.
3. Retry a short summary-only run. If the FIT remains missing, examine
   Garmin's response and current validation error; absence of GPS must not be
   inferred from the summary alone.
4. Perform manual Garmin sync repeatedly to cover pending historical FIT
   recordings beyond the foreground 24-record-per-pass budget.
5. Force-stop and reopen offline. Confirm GPS map, laps, charts and cached
   Today route thumbnails appear without provider access.
6. Compare normal pause/resume runs in both route thumbnail and main map:
   visually joined line, with unchanged pause summaries and raw analysis.
7. Test a true no-GPS indoor activity: retain honest "No recorded GPS track"
   when a complete FIT contains valid non-GPS measurements.

## Known limitations

- Instrumentation tests, full Kotlin compilation and device QA have **not**
  been run by this GitHub-only edit workflow.
- FIT decoding's structural limits (16 MiB source FIT, 20,000 recorded
  samples, valid checksums) remain as safety boundaries. The change removes
  only the inappropriate 2 MiB JSON persistence limit for full details.
- Optional cloud snapshots still have a separate payload-size cap and may
  skip very large FIT-derived detail backups; complete local persistence does
  not imply that all recordings are cloud-backed up.
- A visually joined gap is a straight connector between observed endpoints,
  not a claim that the watch measured the unknown intermediate path.
