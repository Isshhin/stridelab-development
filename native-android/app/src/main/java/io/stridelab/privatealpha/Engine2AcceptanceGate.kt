package io.stridelab.privatealpha

/**
 * Acceptance checklist state supplied by independent CI/device verification.
 * These booleans MUST be populated from verified evidence, never inferred from a commit.
 * This evaluator does not itself run tests or authenticate the evidence.
 */
internal data class Engine2VerificationEvidence(
    val exactHeadCompiled: Boolean = false,
    val engineUnitTestsPassed: Boolean = false,
    val datasetBehaviorTestsPassed: Boolean = false,
    val androidInstrumentationPassed: Boolean = false,
    val schemaUpgradePassed: Boolean = false,
    val offlineReopenPassed: Boolean = false,
    val accountIsolationPassed: Boolean = false,
    val liveProviderAuthorizationVerified: Boolean = false,
    val providerDedupVerified: Boolean = false,
    val installedSigningCompatible: Boolean = false,
    val ownerRequestedInstallableApk: Boolean = false
)

internal data class Engine2AcceptanceReport(
    val eligibleForDomainCodeReview: Boolean,
    val eligibleForPersistenceIntegration: Boolean,
    val eligibleForLiveProviderIntegration: Boolean,
    val eligibleForPhoneUpdate: Boolean,
    val blockers: List<String>
)

internal object Engine2AcceptanceGate {
    fun assess(e: Engine2VerificationEvidence): Engine2AcceptanceReport {
        val domain = e.exactHeadCompiled && e.engineUnitTestsPassed && e.datasetBehaviorTestsPassed
        val persistence = domain && e.schemaUpgradePassed && e.offlineReopenPassed &&
            e.accountIsolationPassed && e.androidInstrumentationPassed
        val provider = persistence && e.liveProviderAuthorizationVerified &&
            e.providerDedupVerified
        val phone = persistence && e.installedSigningCompatible &&
            e.ownerRequestedInstallableApk
        val blockers = buildList {
            if (!e.exactHeadCompiled) add("Exact-head Kotlin/Android compilation remains unverified.")
            if (!e.engineUnitTestsPassed) add("Engine 2.0 JVM unit tests have not passed.")
            if (!e.datasetBehaviorTestsPassed) add("Dataset cases have not been asserted against actual engine outputs.")
            if (!e.schemaUpgradePassed) add("Non-destructive SQLite schema migration is unverified.")
            if (!e.offlineReopenPassed) add("Offline process restart and durable decision receipts are unverified.")
            if (!e.accountIsolationPassed) add("Persistent account isolation is unverified.")
            if (!e.androidInstrumentationPassed) add("Android emulator/instrumentation acceptance is unverified.")
            if (!e.liveProviderAuthorizationVerified) add("Real authorized provider integration is unverified.")
            if (!e.providerDedupVerified) add("Cross-provider physical-event deduplication is unverified.")
            if (!e.installedSigningCompatible) add("Installed APK signing compatibility is unverified.")
            if (!e.ownerRequestedInstallableApk) add("Owner has not requested a distributable APK.")
        }
        return Engine2AcceptanceReport(domain, persistence, provider, phone, blockers)
    }
}
