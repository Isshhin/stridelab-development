package io.stridelab.privatealpha

internal enum class NotificationClass {
    SAFETY_ALERT,
    BLOCKING_CLARIFICATION,
    ACTIONABLE_REVISION,
    OPTIONAL_CLARIFICATION,
    INFORMATIONAL_GUIDANCE,
    WEEKLY_SUMMARY
}

internal data class CoachingNotificationCandidate(
    val notificationId: String,
    val notificationClass: NotificationClass,
    val title: String,
    val body: String,
    val affectsImminent48hSession: Boolean = false,
    val isUnresolvedAmbiguousMatch: Boolean = false,
    val isUnexplained14dGap: Boolean = false,
    val isMissingRirOrTechniqueOnProgressingLift: Boolean = false
)

internal data class CoachingCopyAuditResult(
    val clean: Boolean,
    val sanitizedText: String,
    val violations: List<String>
)

/**
 * Engine 1.9 Epistemic Phrasing Guard & Priority-Budgeted Notification Queue
 * (Track 7 Refinements §12, POL-054, CD-13, CD-16).
 */
internal object Engine19ExplanationAndNotificationPolicy {

    private val forbiddenAbsoluteClaims = listOf(
        "your fitness improved" to "you held stronger output at similar reported effort across recent sessions",
        "your technique was clean" to "you reported clean technique",
        "you have recovered" to "no adverse fatigue or symptom signals were logged",
        "this workout is safe" to "this session aligns with your current progression rules",
        "your physiological threshold was achieved" to "your effort stayed inside the target threshold guidance band"
    )

    fun validateAndSanitizeCopy(
        rawText: String,
        techniqueCoachVerified: Boolean = false
    ): CoachingCopyAuditResult {
        var text = rawText.replace("—", ", ").replace("--", "-")
        val foundViolations = mutableListOf<String>()
        if (rawText.contains("—") || rawText.contains("--")) {
            foundViolations += "EM_DASH_OR_DOUBLE_HYPHEN"
        }

        for ((forbidden, replacement) in forbiddenAbsoluteClaims) {
            if (forbidden == "your technique was clean" && techniqueCoachVerified) continue
            if (text.contains(forbidden, ignoreCase = true)) {
                foundViolations += "UNSUPPORTED_CLAIM:$forbidden"
                text = text.replace(forbidden, replacement, ignoreCase = true)
            }
        }

        return CoachingCopyAuditResult(
            clean = foundViolations.isEmpty(),
            sanitizedText = text.trim(),
            violations = foundViolations
        )
    }

    /**
     * Filters and ranks candidate notifications (Track 7 §12, POL-054).
     * Safety (H1-H3) and Blocking Clarification are ALWAYS exempt from session/week budgets (`CD-16`).
     */
    fun filterAndPrioritizeNotifications(
        candidates: List<CoachingNotificationCandidate>,
        optionalAlreadySentThisSession: Int = 0,
        optionalAlreadySentThisWeek: Int = 0
    ): List<CoachingNotificationCandidate> {
        val priorityOrder = compareBy<CoachingNotificationCandidate>(
            {
                when (it.notificationClass) {
                    NotificationClass.SAFETY_ALERT -> 0
                    NotificationClass.BLOCKING_CLARIFICATION -> 1
                    NotificationClass.ACTIONABLE_REVISION -> 2
                    NotificationClass.OPTIONAL_CLARIFICATION -> 3
                    NotificationClass.INFORMATIONAL_GUIDANCE -> 4
                    NotificationClass.WEEKLY_SUMMARY -> 5
                }
            },
            { if (it.affectsImminent48hSession) 0 else 1 },
            { if (it.isUnresolvedAmbiguousMatch) 0 else 1 },
            { if (it.isUnexplained14dGap) 0 else 1 },
            { if (it.isMissingRirOrTechniqueOnProgressingLift) 0 else 1 },
            { it.notificationId }
        )

        val sorted = candidates.distinctBy { it.notificationId }.sortedWith(priorityOrder)
        val out = mutableListOf<CoachingNotificationCandidate>()
        var sessRemaining = (Engine19CoachingPolicyRegistry.MAX_OPTIONAL_PROMPTS_PER_SESSION - optionalAlreadySentThisSession)
            .coerceAtLeast(0)
        var weekRemaining = (Engine19CoachingPolicyRegistry.MAX_OPTIONAL_PROMPTS_PER_WEEK - optionalAlreadySentThisWeek)
            .coerceAtLeast(0)

        for (item in sorted) {
            val isExempt = item.notificationClass in setOf(
                NotificationClass.SAFETY_ALERT,
                NotificationClass.BLOCKING_CLARIFICATION
            )
            if (isExempt) {
                out += item
            } else if (sessRemaining > 0 && weekRemaining > 0) {
                out += item
                sessRemaining--
                weekRemaining--
            }
        }
        return out
    }
}
