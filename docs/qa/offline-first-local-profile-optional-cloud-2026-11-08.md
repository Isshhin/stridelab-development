# Offline-first local profile / optional cloud authentication

Implementation branch: `feature/offline-local-profile-optional-cloud-20261108`.
Base: `fix/garmin-recent-fit-first-20261108` (`b66d938`). No engine or FIT-sample mutation.

## Contract

- A stable local owner is created on first launch and stored under the existing
  `native-account-v1/active` preference. Existing `active` namespaces are kept
  unchanged on upgrade; this is not a destructive data migration.
- `TrainingState.account` is the local storage namespace, not an authentication
  signal. It can operate offline with no Supabase session or cloud account.
- StrideLab sign-in only sets `TrainingState.cloudAccount` and an encrypted
  `SessionVault` entry. It does **not** replace the active local owner, rebuild
  the training cache, or silently import cloud history.
- Local plans, logs, activity history, library, and Garmin credentials are
  scoped to the local owner. Garmin login uses its own credential vault; its
  manual/automatic FIT imports do not require StrideLab sign-in.
- Cloud backup is independently opt-in and requires a valid StrideLab session.
  The cloud owner is verified before converting the owner field of a local
  Garmin snapshot to the server owner at upload. On restore, the verified cloud
  owner is translated back to the active local profile. Original snapshots on
  both sides are preserved; restore remains insert-only.
- The local profile becomes bound to the first cloud account it connects.
  Connecting a different cloud owner to the same local profile fails without
  transferring training data. Cloud sign-out clears only that cloud session.
- Old backend history automatic refresh is retired. Automatic Garmin sync is
  allowed for local profiles and cloud backup occurs only when cloud-connected
  and separately enabled.

## Android acceptance checks

1. Fresh install, no network: Today / Training / Library / plan creation must
   be available without login; save a favorite or plan; force-stop and reopen;
   saved local data remains.
2. Connect Garmin without StrideLab account: sync a run, persist its full FIT,
   force-stop, reopen offline, show GPS/splits/charts and any cached thumbnail.
3. Sign in to StrideLab cloud account: the local profile and its existing
   workouts, plans and Garmin connection remain unchanged.
4. Opt into Garmin cloud backup: verify only source-validated, account-owned
   Garmin records upload; restore verified cloud records into the same local
   namespace. Verify without backend writes to public activities.
5. Disconnect StrideLab cloud account: local profile, Garmin connection,
   activity history, plans and drafts remain visible and editable.
6. Reconnect the same cloud account: opt-in backup can resume. Different cloud
   account sign-in must refuse implicit reuse of the local profile.
7. Existing installation with `active` account: local data remains readable
   after upgrade even if its cloud credentials expire or are missing.

## Verification status

Source changes and focused instrumentation tests are committed on the branch.
Device compilation, instrumentation execution, and full cold-start testing
must be performed before merging. The pre-existing `TrainingRepositoryTest`
contains assertions based on mandatory login and account-switch semantics;
those test expectations require refactoring for this new contract.

## Limitations

- Signing out of the **previous app version** cleared its active-local-owner
  preference. Pre-existing, owner-scoped SQLite records remain on disk but
  cannot safely be reassigned to an automatically generated profile; explicit
  recovery/import tooling is a separate task.
- Cloud backup currently covers verified Garmin activity/detail snapshots,
  not all plan/library data. Local plan JSON export/import remains available.
- The native local FIT picker still stages files without creating a new
  activity record. This branch does not change its importer contract.
- No automatic migration across two different cloud owners is permitted.
