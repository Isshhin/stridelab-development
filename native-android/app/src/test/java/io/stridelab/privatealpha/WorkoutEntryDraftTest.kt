package io.stridelab.privatealpha

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class WorkoutEntryDraftTest {
    private fun draft(owner:String="synthetic-owner",plan:String="plan",session:String="session")=WorkoutEntryDraft(owner,plan,session,0,"1.","","","Private unfinished note","2026-10-27",JSONObject().put("sq|1|reps","1.").put("old|1|kg","20").toString(),1)
    @Test fun incompleteStringsRoundtripWithoutBecomingActualNumbers() {
        val d=draft();assertEquals(d,WorkoutEntryDraft.decode(d.encode(),d.owner));assertEquals("1.",WorkoutEntryDraft.decode(d.encode(),d.owner).minutes)
        assertThrows(IllegalArgumentException::class.java) { WorkoutEntryDraft.decode(d.encode(),"different-owner") }
    }
    @Test fun draftIdentifiersAndEntryFilteringDoNotTransferAReplacedLoad() {
        val d=draft();assertEquals(d.recordId,d.copy(notes="Changed input").recordId)
        assertNotEquals(draft(plan="a/b",session="c").recordId,draft(plan="a",session="b/c").recordId)
        val entries=JSONObject(d.visibleEntries(listOf(ExercisePrescription("sq","Squat",2,6))))
        assertEquals("1.",entries.getString("sq|1|reps"));assertFalse(entries.has("old|1|kg"))
    }
    @Test fun oversizedOrMalformedStagingCannotReplaceDurableData() {
        assertThrows(IllegalArgumentException::class.java) { draft().copy(notes="x".repeat(2001)).validate() }
        assertThrows(IllegalArgumentException::class.java) { draft().copy(entries="{} trailing").validate() }
        assertThrows(IllegalArgumentException::class.java) { draft().copy(logRevision=-1).validate() }
    }
}
