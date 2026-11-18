package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ManualWorkoutTemplateTest {
    private val owner = "test-owner-templates"
    private val template = ManualWorkoutTemplate(
        "template-1", owner, "Morning strength", ManualDiscipline.STRENGTH,
        listOf(
            ManualExerciseEntry("Squat", 3, reps = 12, weightKg = 20.0),
            ManualExerciseEntry("Plank", 3, seconds = 40)
        )
    )
    private val session = ManualDisciplineSession(
        "session-1", owner, ManualDiscipline.CALISTHENICS, "Bodyweight",
        LocalDate.parse("2026-11-17"), listOf(ManualExerciseEntry("Pull-up", 3, reps = 6))
    )

    @Test fun templateRoundTripPreservesOnlyUserEnteredExercises() {
        val parsed = ManualWorkoutTemplateCodec.decode(
            ManualWorkoutTemplateCodec.encode(template), owner)
        assertEquals(template, parsed)
        val json = org.json.JSONObject(ManualWorkoutTemplateCodec.encode(template))
        assertEquals("manual-template", json.getString("kind"))
        assertFalse(json.has("performedOn"))
        assertFalse(json.has("completed"))
    }

    @Test fun templateDataIsOwnerBoundAndSchemaValidated() {
        assertThrows(IllegalArgumentException::class.java) {
            ManualWorkoutTemplateCodec.decode(
                ManualWorkoutTemplateCodec.encode(template), "another-owner")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManualWorkoutTemplateCodec.encode(template.copy(exercises = emptyList()))
        }
    }

    @Test fun combinedArchiveRetainsLogsAndForms() {
        val file = ManualDisciplineArchive.export(owner, listOf(session), listOf(template))
        val parsed = ManualDisciplineArchive.import(file, owner)
        assertEquals(listOf(session), parsed.sessions)
        assertEquals(listOf(template), parsed.templates)
    }

    @Test fun oldSessionOnlyBackupStillImportsInVersionTwo() {
        val legacy = ManualDisciplineBackup.export(owner, listOf(session))
        val parsed = ManualDisciplineArchive.import(legacy, owner)
        assertEquals(listOf(session), parsed.sessions)
        assertEquals(emptyList<ManualWorkoutTemplate>(), parsed.templates)
    }

    @Test fun invalidArchiveAndDuplicateTemplateIdsFailBeforeRestore() {
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineArchive.export(owner,listOf(session),listOf(template,template))
        }
        val archive = org.json.JSONObject(
            ManualDisciplineArchive.export(owner,listOf(session),listOf(template)))
        archive.put("sha256","bad")
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineArchive.import(archive.toString(),owner)
        }
    }
}
