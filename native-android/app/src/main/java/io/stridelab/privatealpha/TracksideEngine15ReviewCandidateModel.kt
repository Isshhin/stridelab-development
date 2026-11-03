package io.stridelab.privatealpha

import java.util.Locale

/**
 * Design-only display input for Figma E2 states 834:5 / 69 / 133 / 190 / 247.
 *
 * Pure presentation contract. This is NOT the Engine 1.5 repository or a mechanism
 * to create/accept a workout suggestion. A future approved adapter must supply
 * values from an account-owned, current Engine15 plan and proposal.
 */
internal enum class TracksideReviewStage {
    PREVIEW, PENDING, ACCEPTED_TIME, ACCEPTED_REST, STALE
}

internal data class TracksideReviewCandidate(
    val stage: TracksideReviewStage,
    val workoutTitle: String,
    val workoutDateLabel: String,
    val plannedMinutes: Int,
    val originalDistanceKm: Double?,
    val proposedMinutes: Int?,
    val sourceHeading: String,
    val sourceDetail: String,
    val uncertainty: String = "One reported event is not a readiness score."
) {
    init {
        require(workoutTitle.isNotBlank() && workoutDateLabel.isNotBlank())
        require(plannedMinutes > 0)
        require(originalDistanceKm == null || (originalDistanceKm.isFinite() && originalDistanceKm >= 0.0))
        when (stage) {
            TracksideReviewStage.PREVIEW, TracksideReviewStage.PENDING,
            TracksideReviewStage.ACCEPTED_TIME ->
                require(proposedMinutes != null && proposedMinutes in 1 until plannedMinutes)
            TracksideReviewStage.ACCEPTED_REST -> require(proposedMinutes == 0)
            TracksideReviewStage.STALE -> require(proposedMinutes == null || proposedMinutes in 0 until plannedMinutes)
        }
        require(sourceHeading.isNotBlank() && sourceDetail.isNotBlank())
    }

    val activeMinutes: Int
        get() = when (stage) {
            TracksideReviewStage.ACCEPTED_TIME -> proposedMinutes!!
            TracksideReviewStage.ACCEPTED_REST -> 0
            else -> plannedMinutes
        }

    /** Original distance is *history*, never a fresh target after reviewed acceptance. */
    val activeDistanceTargetKm: Double?
        get() = if (stage in setOf(TracksideReviewStage.ACCEPTED_TIME, TracksideReviewStage.ACCEPTED_REST))
            null else originalDistanceKm

    val originalDistanceLabel: String
        get() = originalDistanceKm?.let { String.format(Locale.ROOT, "%.1f km", it) } ?: "None"

    val acceptedRequiresOriginalSteps: Boolean
        get() = stage !in setOf(TracksideReviewStage.ACCEPTED_TIME, TracksideReviewStage.ACCEPTED_REST)
}

/** Exact text states for the review-only 412dp candidate, never synthetic readiness values. */
internal object TracksideReviewCandidateCopy {
    fun title(stage: TracksideReviewStage): String = when(stage) {
        TracksideReviewStage.PREVIEW -> "Suggestion"
        TracksideReviewStage.PENDING -> "Review change"
        TracksideReviewStage.ACCEPTED_TIME -> "Workout updated"
        TracksideReviewStage.ACCEPTED_REST -> "Rest day"
        TracksideReviewStage.STALE -> "Review updated"
    }

    fun eyebrow(stage: TracksideReviewStage): String = when(stage) {
        TracksideReviewStage.PREVIEW -> "ENGINE 1.5 · NO CHANGES YET"
        TracksideReviewStage.PENDING -> "ENGINE 1.5 · AWAITING YOUR DECISION"
        TracksideReviewStage.ACCEPTED_TIME -> "ENGINE 1.5 · APPROVED TIME ONLY"
        TracksideReviewStage.ACCEPTED_REST -> "ENGINE 1.5 · APPROVED RECOVERY REST"
        TracksideReviewStage.STALE -> "ENGINE 1.5 · EVIDENCE CHANGED"
    }

    fun chip(stage: TracksideReviewStage): String = when(stage) {
        TracksideReviewStage.PREVIEW -> "PREVIEW"
        TracksideReviewStage.PENDING -> "PENDING"
        TracksideReviewStage.ACCEPTED_TIME -> "SAVED"
        TracksideReviewStage.ACCEPTED_REST -> "REST"
        TracksideReviewStage.STALE -> "STALE"
    }

    fun changeTitle(model: TracksideReviewCandidate): String = when(model.stage) {
        TracksideReviewStage.PREVIEW -> "A shorter easy run"
        TracksideReviewStage.PENDING -> "${model.proposedMinutes} minutes instead of ${model.plannedMinutes}"
        TracksideReviewStage.ACCEPTED_TIME -> "Previously ${model.plannedMinutes} min · ${model.originalDistanceLabel}"
        TracksideReviewStage.ACCEPTED_REST -> "${model.workoutTitle} · ${model.plannedMinutes} min"
        TracksideReviewStage.STALE -> "Previous review unavailable"
    }

    fun actions(model: TracksideReviewCandidate): Pair<String,String> = when(model.stage) {
        TracksideReviewStage.PREVIEW -> "Review suggestion" to "Keep original"
        TracksideReviewStage.PENDING -> "Accept ${model.proposedMinutes} min" to "Dismiss"
        TracksideReviewStage.ACCEPTED_TIME -> "View workout" to "Training"
        TracksideReviewStage.ACCEPTED_REST -> "Training" to "View history"
        TracksideReviewStage.STALE -> "Refresh review" to "Keep original"
    }

    fun disclosure(model: TracksideReviewCandidate): String = when(model.stage) {
        TracksideReviewStage.PREVIEW -> "The original ${model.originalDistanceLabel} value is not a target for a shorter run."
        TracksideReviewStage.PENDING -> "If accepted: ${model.proposedMinutes} minutes, with no required distance or old interval targets."
        TracksideReviewStage.ACCEPTED_TIME -> "No catch-up mileage. Do not force the old distance into ${model.activeMinutes} minutes."
        TracksideReviewStage.ACCEPTED_REST -> "No automatic redistribution of missed training."
        TracksideReviewStage.STALE -> "No adjustment was accepted. Reopen current details before deciding."
    }
}
