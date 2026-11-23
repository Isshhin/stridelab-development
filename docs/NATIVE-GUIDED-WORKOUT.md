# Native guided workout iteration — 27 October 2026

The user reported that the installed version-14 ongoing-workout UI is cumbersome despite working plan/sync flows. They requested inspiration from Leap Fitness Group and specifically selected (1) one exercise at a time with rest/next controls and (2) movement demonstrations/form guidance.

Reference reviewed: the developer's [Home Workout Google Play listing](https://play.google.com/store/apps/details?hl=en_US&id=homeworkout.homeworkouts.noequipment), including its official screenshots. Its emphasis on exercise guidance informed the presentation. No Leap code, images, branding, videos, animation or training claims are copied. StrideLab retains its Afterglow Signal native theme and existing approved prescriptions.

## Implemented slice

Approved/paused, editable resistance and calisthenics sessions with saved exercises open in a native guided player. One movement/set is prominent, with progress based only on actual saved set records. The persistent main action explicitly confirms actual reps/hold time; different actual values and optional load can be entered. Only that set is saved, without marking the entire workout complete or copying planned duration/distance. Rest starts only after the saved log revision confirms the write. Browsing/next does not mark skipped sets performed. Failed/invalid writes cannot start rest.

Rest has pause/resume, +15 seconds and explicit continue. Timed holds have start/pause/reset; countdown completion never records performance. Timers pause in the background. Review & finish opens the existing actual record, completion, alternatives, scheduling and feedback controls. Running sessions without resistance prescriptions retain their existing flow in this slice.

The offline library supplies technique steps and licensed movement figures where appropriate. Related diagrams are not shown as exact demonstrations of different prescribed variants. Full-movement progression images are shown only for the saved full movement; existing labeled schematics remain labeled. Source/author/license details are preserved in the form sheet; compact figures retain attribution. Missing exact guidance stays text-only. Animated/video demonstration coverage is not claimed.

Existing in-progress entry work is integrated: raw editable input drafts are owner scoped, local only, version checked against actual logs, debounced and flushed on exit/background. Drafts never count as actual performance. Old asynchronous drafts cannot replace newer confirmed logs. No destructive schema migration was introduced. Confirmed native workout volume uses only actual dated logs and avoids counting linked source activities twice.

## Verification and delivery

Initial local checks: 84 JVM tests and 14 packaging/signing Python tests passed; 34 targeted emulator tests passed before final visual refinement. Final affected UI rerun and full CI must pass before naming a new verified checkpoint. New tests cover explicit single-set logging, rest, adjusted actuals, invalid input, timer completion without auto-logging, and 200% text. Existing root navigation, quiet sync, account/cache tests remain. Forced-process probes additionally check unconfirmed input reopening offline without a confirmed workout.

Version candidate: 15, `0.6.6-guided-workout`. The user's phone remains on exact verified version 14; no new APK/test APK was installed on it. Tests target only the explicitly selected emulator. The installed certificate matches the prior ephemeral CI job, whose private key availability has not been established. A new CI artifact is a disposable test artifact unless its installed-certificate gate passes. Do not uninstall or clear the phone, claim update compatibility, or start Engine 2.0. Web and preserved legacy assets remain frozen.

Recovery anchor before this iteration: `checkpoint/native-engine1-goals-verified-7d5acdf` (`7d5acdf4bec15f8d95ee07d75fe152532e765d95`), successful run [40372696555](https://github.com/Isshhin/stridelab-development/actions/runs/40372696555). Preserve all earlier checkpoints. Recover the new source from its feature-branch commit and private canonical source archive only after its exact CI evidence is checked.


## Set-entry refinement after CI

Run 40443610373 at f5b4f26 passed 84 JVM tests but had one intermittent UI failure opening inline actual-rep fields (75 of 76 emulator suite cases passed); no installable artifact was produced. Actual set entry is now a dedicated keyboard-aware sheet instead of expanding inside the scrolling guide. Its regression assertions retain adjusted reps/load, unchanged original targets, and no performance record before explicit confirmation. The editor waits for the actual field semantics rather than relying on a transient inline layout.

Draft ordering also now uses a local monotonic edit stamp seeded from the saved draft. Storage rejects an older same-log-revision write, and newer identical-content writes advance the stored stamp. The repository regression verifies that a deliberately delayed obsolete edit cannot replace newer text and that the newer text reopens offline. The actual-log revision guard remains separate. This is local entry durability, not plan cloud synchronization or Engine 2.0.

Run 40446098834 exposed two guide/editor opening failures in the full suite despite local passes. Form guide and Adjust set now live in a fixed footer outside asynchronous image and scrolling layout. Touch interaction and all original record assertions remain in the tests; modal-field readiness is checked explicitly. This also keeps both user controls available without scrolling. No failed attempt is a verified checkpoint.


Run 40448375989 passed all five player cases but failed the existing Library-modal case with SnapshotStateObserver multithreaded access. The complete stack shows TestMonotonicFrameClock and ApplyingContinuationInterceptor applying a frame on DefaultDispatcher-worker after an IO continuation. A trial of the officially recommended queued test dispatcher exposed readiness incompatibilities with this project's existing direct-Activity test setup and was not retained. The existing rules/assertions remain unchanged. Instead, asynchronous catalog/figure loading is explicitly enclosed in a Main.immediate context, keeping IO reads off-thread and UI delivery/continuations on the UI thread. The same handoff is used by the new player and practice builder, and lifecycle collection of displayed repository/connectivity state explicitly uses Main.immediate. Runtime Compose/BOM/dependency versions remain unchanged. No failed run published an installable APK.
