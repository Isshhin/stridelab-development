package io.stridelab.privatealpha

import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/** Source-record equality is not proof that two different provider IDs represent one workout. */
internal data class ImportedSourceIdentity(
    val accountId: String,
    val provider: String,
    val externalId: String? = null,
    val sha256: String? = null
) {
    init {
        require(accountId.isNotBlank() && accountId.length<=256)
        require(provider.isNotBlank() && provider.length<=128)
        require(!externalId.isNullOrBlank() || !sha256.isNullOrBlank())
        require(externalId==null || externalId.length<=512)
        require(sha256==null || sha256.matches(Regex("[0-9a-fA-F]{64}")))
    }

    fun sameOriginal(other:ImportedSourceIdentity):Boolean {
        if(accountId!=other.accountId) return false
        if(sha256!=null && other.sha256!=null &&
            sha256.equals(other.sha256,ignoreCase=true)) return true
        return provider.lowercase(Locale.ROOT)==other.provider.lowercase(Locale.ROOT) &&
            externalId!=null && externalId==other.externalId
    }
}

internal data class ImportedActivityCandidate(
    val identity: ImportedSourceIdentity,
    val discipline: String,
    val startedAt: Instant?,
    val durationSeconds: Double?,
    val distanceMeters: Double?
) {
    init {
        require(discipline.isNotBlank() && discipline.length<=128)
        require(durationSeconds==null || (durationSeconds.isFinite() && durationSeconds>=0))
        require(distanceMeters==null || (distanceMeters.isFinite() && distanceMeters>=0))
    }
}

internal enum class ImportedActivityDisposition {
    NEW_SOURCE,
    ALREADY_SEEN_SOURCE,
    REVIEW_POSSIBLE_SAME_EVENT
}

/**
 * Read-only import candidate classification. Even an exact source ID match
 * MUST NOT auto-link a training session or overwrite an existing activity.
 */
internal object ActivityImportMatcher {
    fun inspect(candidate:ImportedActivityCandidate,
                existing:List<ImportedActivityCandidate>):ImportedActivityDisposition {
        val own=existing.filter { it.identity.accountId==candidate.identity.accountId }
        if(own.any { it.identity.sameOriginal(candidate.identity) })
            return ImportedActivityDisposition.ALREADY_SEEN_SOURCE
        if(own.any { possibleSamePhysicalActivity(candidate,it) })
            return ImportedActivityDisposition.REVIEW_POSSIBLE_SAME_EVENT
        return ImportedActivityDisposition.NEW_SOURCE
    }

    private fun possibleSamePhysicalActivity(a:ImportedActivityCandidate,
                                             b:ImportedActivityCandidate):Boolean {
        if(!a.discipline.equals(b.discipline,ignoreCase=true)) return false
        val aStart=a.startedAt ?: return false
        val bStart=b.startedAt ?: return false
        if(abs(Duration.between(aStart,bStart).seconds)>5*60) return false
        val durationA=a.durationSeconds ?: return false
        val durationB=b.durationSeconds ?: return false
        if(abs(durationA-durationB)>maxOf(60.0,0.1*maxOf(durationA,durationB))) return false
        val distanceA=a.distanceMeters
        val distanceB=b.distanceMeters
        return distanceA==null || distanceB==null ||
            abs(distanceA-distanceB)<=maxOf(200.0,0.1*maxOf(distanceA,distanceB))
    }
}
