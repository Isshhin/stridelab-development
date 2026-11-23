package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ManualDisciplineCodecTest {
    private val owner = "local-plans-example-123"
    private val session = ManualDisciplineSession(
        id = "manual-session-1",
        owner = owner,
        discipline = ManualDiscipline.STRENGTH,
        title = "Leg day",
        performedOn = LocalDate.parse("2026-11-18"),
        exercises = listOf(
            ManualExerciseEntry("Squat", 3, reps = 12, weightKg = 20.0),
            ManualExerciseEntry("Wall sit", 2, seconds = 45)
        ),
        notes = "Felt steady"
    )

    @Test fun roundTripPreservesUserMeasurementsAndNoSyntheticTime() {
        assertEquals(session, ManualDisciplineCodec.decode(
            ManualDisciplineCodec.encode(session), owner))
        val json = org.json.JSONObject(ManualDisciplineCodec.encode(session))
        assertEquals("2026-11-18", json.getString("performedOn"))
        assertEquals("manual", json.getString("source"))
        assertFalse(json.has("started_at"))
        assertFalse(json.has("moving_time_s"))
    }

    @Test fun signedOutAndSignedInOwnersAreSeparate() {
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineCodec.decode(ManualDisciplineCodec.encode(session), "other-owner")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineBackup.import(ManualDisciplineBackup.export(owner, listOf(session)),
                "other-owner")
        }
    }

    @Test fun allManualDisciplinesRoundTrip() {
        for (discipline in ManualDiscipline.entries) {
            val other = session.copy(discipline = discipline)
            assertEquals(other, ManualDisciplineCodec.decode(
                ManualDisciplineCodec.encode(other), owner))
        }
    }

    @Test fun backupValidatesChecksumAndRetainsAllEntries() {
        val second = session.copy(id = "session-2", discipline = ManualDiscipline.CONDITIONING)
        val data = ManualDisciplineBackup.export(owner, listOf(session,second))
        assertEquals(listOf(session,second), ManualDisciplineBackup.import(data, owner))
        val root = org.json.JSONObject(data)
        root.put("sha256", "0000")
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineBackup.import(root.toString(), owner)
        }
    }

    @Test fun backupRejectsDuplicateIdsAndInvalidExerciseData() {
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineBackup.export(owner, listOf(session, session))
        }
        val invalid = session.copy(exercises = listOf(ManualExerciseEntry("Squat", 3)))
        assertThrows(IllegalArgumentException::class.java) { ManualDisciplineCodec.encode(invalid) }
        val invalidWeight = session.copy(exercises =
            listOf(ManualExerciseEntry("Squat", 3, reps=10, weightKg=Double.NaN)))
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineCodec.encode(invalidWeight)
        }
    }

    @Test fun onlyManualSourceAndKnownSchemaCanBeDecoded() {
        val root = org.json.JSONObject(ManualDisciplineCodec.encode(session))
        root.put("source", "garmin")
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineCodec.decode(root.toString(), owner)
        }
        root.put("source", "manual")
        root.put("schema", 500)
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineCodec.decode(root.toString(), owner)
        }
    }
}
