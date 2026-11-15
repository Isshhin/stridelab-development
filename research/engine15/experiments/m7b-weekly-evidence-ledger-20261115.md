# StrideLab Engine 1.5 M7b — Weekly evidence ledger research

Date: 2026-11-15. Based on M7a draft PR #82. This component is **read-only**, isolated from saved-plan mutation and the production Android plan generator.

## Research question

How can StrideLab tell the difference between a *known linked run*, an unknown/ambiguous plan completion, an explicit missed workout, and an incomplete week's activity record, without inventing completed mileage or workout tolerance?

## Implementation

\`IndependentWeeklyEvidenceLedger.inspectPlan\` audits every saved running session in one owner-asserted, approved or paused plan through the M7a attribution checker, then groups session audit results into Monday-start weeks based on **planned dates**, not inferred activity dates.

Each week exposes:
- \`observedLinkedKmPartial\`: sum of unambiguous manually linked source distances with recorded valid values. **Only a partial plan-linked sum**, not all recorded athlete mileage.
- \`linkedWithKnownDistanceCount\`: observed links that have a source distance.
- \`attributedButMissingDistanceCount\`: accepted exact links whose source metrics have no distance.
- \`deferredOrUnlinkedSessionCount\`: missing, suspicious, incompatible, duplicate or other unverified completion claims.
- \`savedMissedOrSkippedCount\`: explicit saved missed/skipped statuses, without equating those statuses to real-world zero activity.
- \`verifiedCompleteWeekKm=null\`, \`weekCoverageAuthenticated=false\`, \`workloadResponseVerified=false\` even if every planned session is linked and has a distance.

A grouped ledger must never become M3's \`confirmedCompleteMondays\` or M8's allowed training-capacity baseline automatically. The app needs independently authenticated source coverage, stable identity/cross-provider duplicate review, plan-to-session identity and separate training intent/effort evidence to promote it.

## Tests and owner validation

- New \`IndependentWeeklyEvidenceLedger.kt\` and \`IndependentWeeklyEvidenceLedgerTest.kt\`.
- **12 new focused JUnit cases**, including partial linked mileage, fully linked but unverified coverage, missing distance, explicit missed session, multiple calendar weeks, cross-provider possible duplicates, account mismatch, duplicate plans/session IDs, no running sessions and deterministic read-only results.
- M7a added **19** earlier focused cases. Combined M7a+M7b = **31 new focused JUnit cases**, **not yet executed**.
- M6a and M6b also have **24 new unexecuted focused tests**. The last owner-reported fully passing tests are from PR #79, not these research branches.

### Validate all stacked research changes in a new clean worktree

\`\`\`powershell
cd E:\stridelab-validation
git fetch origin research/engine15-m7b-weekly-evidence-ledger-20261115
git worktree add --detach E:\stridelab-m7-validation origin/research/engine15-m7b-weekly-evidence-ledger-20261115
cd E:\stridelab-m7-validation\native-android

$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
"sdk.dir=$($sdk.Replace('\', '/'))" | Set-Content ".\local.properties" -Encoding ASCII

.\gradlew.bat :app:testDebugUnitTest --tests '*IndependentConstrainedMultiweekPlannerTest' --tests '*IndependentQualitySpacingPlannerTest' --tests '*IndependentCompletionAttributionTest' --tests '*IndependentWeeklyEvidenceLedgerTest'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
\`\`\`

If the new worktree directory already exists, choose a different empty path. Do not delete the owner's previous modified checkout or previous phone-QA worktree. Compile/assembly are not yet validated on this branch.

## Remaining M8–M11 gates

M8: Develop longitudinal adaptive coaching with explicit *trusted* source coverage/identity, confirmed performed effort and reviewed recovery vs missed-training intent. The M7 evidence can currently expose missing/disputed/partial data but **cannot** authenticate it; fail closed when readiness is unknown. Compare modes and trajectories with evidence-limited outcomes clearly labeled.

M9: Produce coherent session prescriptions including supported quality dose and distance reconciliation without inventing threshold pace or extending user time constraints.

M10: Experimental Android integration with explicit opt-in, owner isolation and offline signed-out operation, retaining rollback, existing saved plans and reviewed Week Preview UI changes.

M11: Full JVM and instrumentation, emulator visual and accessibility checks, source/import/offline regressions, and final **owner-controlled physical-phone QA**. User explicitly deferred phone QA until end of roadmap.

No merge or auto-activation of these draft PRs; Engine 2.x unchanged.
