# Engine 1.5 M8a — Longitudinal shadow comparison, not real plan adaptation

Date: 2026-11-15. Base: draft PR #83 (M7b). This is research only.

## Implemented
\`IndependentLongitudinalShadow.compare\` accepts the M79 research model's **caller-asserted** completed-week labels and tolerances plus the M7b linked-source audit decision. It verifies plan ID, the two recent ledger weeks, the adjacent planning week and one consistency bound (recorded linked subset cannot exceed claimed full-week total). It produces a shadow comparison between:
- **M6a constrained baseline**: one-week suggested mileage under explicit per-day and long-run limits. This is NOT the legacy Android Engine 1.5 output.
- **M79 response candidate**: next week's synthetic proposed volume from a claimed sequence of completed BUILD and reviewed RECOVERY weeks, or a reason to defer.

A plan with two completed 60 km build weeks remains at 60 km without an separately approved increment. Two heavily undercompleted 60 km build weeks with 30 km actuals produce a 30 km reduced-repeat *illustration*. A 60 km completed build followed by a 40 km deliberately reviewed recovery week returns a 60 km no-catch-up *illustration*. These are not athlete prescriptions or future accuracy claims.

M7b remains **not independently authenticated**, even when every planned session has a linked Garmin/FIT record. \`IndependentLongitudinalShadowDecision.Comparison\` always has \`evidenceVerified=false\`, \`authorizedForPlanMutation=false\`, \`authorizedForProduction=false\`. \`productionAdmission\` explicitly blocks all outcomes; there is no caller-controlled override. Unlinked/ambiguous plan sessions are flagged, not called verified zero workouts. An M7b ledger rejection, cross-plan mismatch, absent week, excess linked subset, unknown tolerance or week coverage all fail closed.

## Tests / status
New source \`IndependentLongitudinalShadow.kt\`, 11 focused JUnit cases in \`IndependentLongitudinalShadowTest.kt\`. These cover undercompletion, reviewed recovery, no unapproved increase, unlinked and fully linked unverified coverage, contradictory partial-vs-declared mileage, plan/week mismatch, unknown tolerance/coverage, M7 rejection, invalid multweek foresight and deterministic nonmutating output.

**Tests are NOT yet executed on this branch.** User has reported both successful focused M6/M7 Gradle command and successful full JVM + debug build under \`E:\stridelab-m7-validation\`; however, \`git worktree add\` failed due to an existing directory, so the checkout HEAD must be verified (`git rev-parse HEAD`) before attributing that run to PR #83. The green owner result does not validate M8a.

## Commands for safe M8a validation
Create a new **unique** worktree folder to avoid the existing worktree collision, and leave both earlier checkouts unchanged.

```powershell
cd E:\stridelab-validation
git fetch origin research/engine15-m8a-longitudinal-shadow-comparison-20261115
git worktree add --detach E:\stridelab-m8a-validation-02 origin/research/engine15-m8a-longitudinal-shadow-comparison-20261115
cd E:\stridelab-m8a-validation-02\native-android
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
"sdk.dir=$($sdk.Replace('\', '/'))" | Set-Content ".\local.properties" -Encoding ASCII
.\gradlew.bat :app:testDebugUnitTest --tests '*IndependentLongitudinalShadowTest'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

No physical phone QA until final M11, per owner request.

## Remaining work
M8b: longitudinal decision-quality policy study, independent scenario qualification and true source-attestation requirements. M9: actual quality workout intensity/dose and time-to-distance reconciliation without invented threshold parameters. M10: opt-in experimental Android integration, owner-isolated signed-out local plans, rollback and UI. M11: JVM/instrumentation/device and finally physical-phone QA.

No production coach, Android UI, plan database, account state, phone APK, or Engine 2.x code changed by M8a.
