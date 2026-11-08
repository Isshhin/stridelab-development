# Today GPS thumbnail cache

## Cold-start card and thumbnail consistency

On application startup, the repository reads at most 40 **already-rendered** 198 × 192
WebP files for its newest account-owned activity summary page. It publishes the
decoded thumbnails in the **same state update** as those activity summaries, before
marking startup complete. A previously generated thumbnail therefore appears with
its corresponding Today card instead of being fetched after the card's first frame.

Only local, account-hashed cache files are read; no FIT/GPX stream is parsed, no
thumbnail is generated, no network source is contacted, and no original GPS data
or route privacy handling is changed. Missing, corrupt, empty-route and older
out-of-page thumbnails retain the existing deferred generation/read behavior.
At most 40 small previews are decoded up front, so the initial card render may
wait for this bounded local read. The existing date-scoped request path handles
older activities beyond the recent page. If a card is displayed before its
thumbnail has *ever* been generated, one initial image appearance is expected.

**Device acceptance:** Force-stop/relaunch after viewing an activity with a
persisted GPS preview. Confirm the card and preview appear together, including
offline. Check StrideLabStartup logcat timings for startup_thumbnail_restore
and first_cached_content, check 360/412dp layouts, multiple recordings on one day,
account isolation, cache corruption and cold-start responsiveness. Repeat after
a new FIT import and after changing an activity detail to confirm the original
invalidation logic still applies.

Today requests local thumbnail files for displayed recorded activities on the selected date, newest first. Generation runs one recording at a time; cached images can load while generation is in progress. Activity Detail and original recordings are unchanged.

Each private, account-hashed cache file atomically stores a SHA-256 fingerprint of validated route segments, a checksum, and a 198 × 192 transparent WebP. Empty routes have a marker. The repository invalidates a changed fingerprint before replacing an activity-detail row; locally saved recordings without a thumbnail are generated on demand. Corrupt images are regenerated from local detail. This does not request Garmin data.

The FIT document picker currently stores and inspects selected files without creating or linking an activity record, so there is no activity thumbnail to generate until a corresponding activity detail exists. Cache work is bounded to requested dates, not an entire-history migration. Device-level cold-start timing and owner visual acceptance remain separate checks.

Known limits: decoded thumbnails remain in memory until the account state is reset, so browsing many dates can increase memory use. Cache invalidation relies on activity-detail mutations going through the repository write paths; independent direct SQLite mutations are not observed. Cloud-restored rows are fingerprinted on backfill, rather than during the restore transaction. Full process force-stop, multi-card UI rendering, Garmin/cloud write paths, and representative performance remain unverified.
