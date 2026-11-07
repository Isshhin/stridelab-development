# Today GPS thumbnail cache

Today requests local thumbnail files for displayed recorded activities on the selected date, newest first. Generation runs one recording at a time; cached images can load while generation is in progress. Activity Detail and original recordings are unchanged.

Each private, account-hashed cache file atomically stores a SHA-256 fingerprint of validated route segments, a checksum, and a 198 × 192 transparent WebP. Empty routes have a marker. The repository invalidates a changed fingerprint before replacing an activity-detail row; locally saved recordings without a thumbnail are generated on demand. Corrupt images are regenerated from local detail. This does not request Garmin data.

The FIT document picker currently stores and inspects selected files without creating or linking an activity record, so there is no activity thumbnail to generate until a corresponding activity detail exists. Cache work is bounded to requested dates, not an entire-history migration. Device-level cold-start timing and owner visual acceptance remain separate checks.

Known limits: decoded thumbnails remain in memory until the account state is reset, so browsing many dates can increase memory use. Cache invalidation relies on activity-detail mutations going through the repository write paths; independent direct SQLite mutations are not observed. Cloud-restored rows are fingerprinted on backfill, rather than during the restore transaction. Full process force-stop, multi-card UI rendering, Garmin/cloud write paths, and representative performance remain unverified.
