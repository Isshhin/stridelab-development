# StrideLab Garmin Automatic Sync and Opt-In Cloud Backup

Branch: ui/trackside-dev. Android Kotlin/Compose, Garmin Personal API, SQLite, optional authenticated Supabase backups. No Engine 1.5 or 2.x modifications.

## Scheduling

Automatic Garmin retrieval is enabled by default for connected Garmin accounts. More > Garmin Connect > Automatic sync can disable it. It does nothing without Garmin credentials.

- App-resume checks are throttled to a minimum of 30 minutes.
- Separate WorkManager periodic work is scheduled every 6 hours, subject to Android network and battery constraints; exact timing is not guaranteed.
- An automatic pass scans at most 200 latest Garmin summaries and downloads up to 3 missing FIT recordings.
- Manual Sync activities retains the broader scan and up to 24 missing recordings per pass.
- Unsupported recordings are deprioritized for 24 hours so later healthy runs are still tried. Original summaries remain.
- Network, authentication, service and rate-limit failures stop further FIT downloads. Repository mutex and account-scoped due-time claims coordinate work.

## Privacy, data flow and backup

Cloud backup is OFF by default and requires a separate confirmation. Automatic sync does not require cloud backup.

1. Validated Garmin summaries, FIT samples and laps commit to account-scoped local SQLite first.
2. Only when cloud backup has been enabled do authenticated requests upload those already-saved source records.
3. Upload to public.stridelab_garmin_backups, not to public.activities. The existing activities primary key uses UUID while offline Garmin IDs are source-derived strings.
4. Immutable insert uses SHA-256 payload digests and PostgREST conflict-ignore preference. No existing cloud snapshots are overwritten.
5. Account-scoped preferences retain only boolean settings, attempt timestamps and hashed upload receipts. No Garmin API key, bearer token, FIT ZIP or StrideLab refresh token goes to cloud backups.
6. Background cloud work uploads at most 6 eligible rows per pass; manual Back up now uploads at most 12. Subsequent passes resume from local rows that lack upload receipts.
7. A single recording above 2.5 MB is retained locally and skipped so it cannot starve the rest of the backup queue. It is NOT marked uploaded.
8. Explicit Restore validates user and record identities, inserts only missing local entries, and uses durable paging (up to 50 summaries and 10 recordings per tap). Existing local records are never overwritten.
9. Disabling backup stops future uploads; it does not delete preexisting cloud backups. This is account-restricted cloud storage, not end-to-end encryption.

## Supabase schema

Applied migration: 20261107012345_stridelab_garmin_local_first_opt_in_backup.
Source: supabase/migrations/20261107012345_stridelab_garmin_local_first_opt_in_backup.sql.

- RLS is enabled with owner-matching auth.uid() SELECT and INSERT policies.
- Anonymous SELECT and INSERT are denied.
- Authenticated UPDATE and DELETE are denied.
- The cloud row owner must equal the payload user_id, and IDs/kinds must match.
- Android uses the authenticated StrideLab session and publishable key, never the service-role key.

## Device QA (local Android Studio)

- Pull ui/trackside-dev; compile the debug variant and run existing Garmin source, SQLite and UI tests.
- Sign in and verify automatic checks on resume and WorkManager without creating duplicate activities.
- Verify manual sync saves locally before an opt-in cloud request; disconnect network halfway and reopen cached history.
- Confirm the cloud opt-in dialog must be accepted before backup controls appear.
- Enable cloud backup, tap Back up now, confirm an owner-only row in Supabase, and retry to check conflict-ignore.
- On a clean test installation, sign in as the same account and tap Restore repeatedly. Original local data must not be overwritten.
- Verify token/key values never appear in cloud payloads.
- Test oversized FIT, missing GPS, rate limiting, sign-out, airplane mode, rapid app resumes and process restart.
- Check memory and frame timing on the physical high-refresh device; WorkManager must not block Compose.

Added tests: GarminCloudBackupGatewayTest; GarminAutomaticSyncPreferencesTest; TracksideGarminModalTest; GarminGpsDetailTest.

## Verification boundary

Supabase migration application and owner-only grants/RLS were verified through the connected database. Kotlin compilation, Android instrumentation, Garmin account API requests and real authenticated cloud upload/restore have not been run in this session.

For public multiuser release, verify commercial Garmin API authorization. Background polling is not a webhook, and periodic Android work may be delayed. Large-file cloud backup, deletion/export controls and automatic restore need separate future review.
