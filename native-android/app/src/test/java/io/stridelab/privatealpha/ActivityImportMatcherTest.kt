package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ActivityImportMatcherTest {
    private val whenRecorded=Instant.parse("2026-10-27T10:00:00Z")
    private val hash="0123456789abcdef".repeat(4)
    private fun candidate(account:String,source:String,id:String,time:Instant=whenRecorded,
                          type:String="running",distance:Double?=5000.0)=ImportedActivityCandidate(
        ImportedSourceIdentity(account,source,id),type,time,1800.0,distance
    )

    @Test fun exactSourceRecordIdentityNeverBecomesANewImport() {
        val previous=candidate("owner","garmin","run-one")
        val repeat=candidate("owner","garmin","run-one")
        assertEquals(ImportedActivityDisposition.ALREADY_SEEN_SOURCE,
            ActivityImportMatcher.inspect(repeat,listOf(previous)))
    }

    @Test fun differentProvidersAreOnlyManualReviewCandidates() {
        val previous=candidate("owner","intervals","intervals-a")
        val sameTime=candidate("owner","garmin","garmin-b")
        assertEquals(ImportedActivityDisposition.REVIEW_POSSIBLE_SAME_EVENT,
            ActivityImportMatcher.inspect(sameTime,listOf(previous)))
    }

    @Test fun fileHashCanProveByteIdenticalSourceButCannotAlterAnotherAccount() {
        val a=ImportedActivityCandidate(ImportedSourceIdentity("owner-a","fit-file",sha256=hash),
            "strength",whenRecorded,1800.0,null)
        val b=ImportedActivityCandidate(ImportedSourceIdentity("owner-a","imported-file",sha256=hash),
            "strength",whenRecorded,1800.0,null)
        assertEquals(ImportedActivityDisposition.ALREADY_SEEN_SOURCE,
            ActivityImportMatcher.inspect(b,listOf(a)))
        val alien=b.copy(identity=b.identity.copy(accountId="owner-b"))
        assertEquals(ImportedActivityDisposition.NEW_SOURCE,
            ActivityImportMatcher.inspect(alien,listOf(a)))
    }

    @Test fun unrelatedRunningSessionDoesNotMergeBasedOnDateOnly() {
        val one=candidate("owner","intervals","a")
        val later=candidate("owner","garmin","b",whenRecorded.plusSeconds(8*60))
        assertEquals(ImportedActivityDisposition.NEW_SOURCE,
            ActivityImportMatcher.inspect(later,listOf(one)))
    }

    @Test fun missingMeasurementsCannotInventPhysicalEquivalence() {
        val one=candidate("owner","intervals","a").copy(durationSeconds=null)
        val two=candidate("owner","garmin","b")
        assertEquals(ImportedActivityDisposition.NEW_SOURCE,
            ActivityImportMatcher.inspect(two,listOf(one)))
    }
}
