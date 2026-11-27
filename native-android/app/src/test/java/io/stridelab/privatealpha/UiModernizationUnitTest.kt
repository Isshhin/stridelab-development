package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class UiModernizationUnitTest {
    @Test fun accountProfileNeverDisplaysRawUuidAndPrefersDisplayNameOrEmail() {
        val uuid = "3474c5ae-56ec-43cd-9317-ff22704ed33e"
        assertTrue(AccountProfilePresentation.isUuid(uuid))
        assertFalse(AccountProfilePresentation.isUuid("runner@example.test"))

        assertEquals(
            "StrideLab Account" to "Signed in",
            AccountProfilePresentation.resolve(uuid, null, null)
        )
        assertEquals(
            "runner@stridelab.io" to "Signed in",
            AccountProfilePresentation.resolve(uuid, null, "runner@stridelab.io")
        )
        assertEquals(
            "Alex Runner" to "runner@stridelab.io",
            AccountProfilePresentation.resolve(uuid, "Alex Runner", "runner@stridelab.io")
        )
        assertEquals(
            "runner@example.test" to "Signed in",
            AccountProfilePresentation.resolve("runner@example.test", null, null)
        )
    }

    @Test fun jwtProfileExtractsDisplayNameAndEmailSafely() {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(
            JSONObject()
                .put("sub", "3474c5ae-56ec-43cd-9317-ff22704ed33e")
                .put("email", "marc@stridelab.io")
                .put("user_metadata", JSONObject().put("display_name", "Marc"))
                .toString()
                .toByteArray()
        )
        val token = "$header.$body.sig"
        assertEquals("Marc" to "marc@stridelab.io", AccountProfilePresentation.extractJwtProfile(token))
        assertEquals(null to null, AccountProfilePresentation.extractJwtProfile("not-a-jwt"))
    }

    @Test fun universalExerciseCatalogDefaultsToAllExcludesRunningAndFiltersByDiscipline() {
        val mixedCatalog = listOf(
            exercise("back_squat", "Back squat", listOf("strength_gym")),
            exercise("assisted_pull_up", "Assisted pull-up", listOf("strength_gym", "calisthenics")),
            exercise("easy_run", "Easy run", listOf("running"))
        )
        val all = FreeformExerciseCatalog.filter(
            catalog = mixedCatalog,
            scope = PickerScope.ALL,
            query = "",
            recentNames = emptyList()
        )
        assertTrue(all.any { it.name == "Back squat" })
        assertTrue(all.any { it.name == "Pogo hops" })
        assertTrue(all.any { it.name == "Wall sit" })
        assertFalse(all.any { it.name == "Easy run" })

        val plyo = FreeformExerciseCatalog.filter(
            catalog = mixedCatalog,
            scope = PickerScope.PLYOMETRICS,
            query = "",
            recentNames = emptyList()
        )
        assertTrue(plyo.isNotEmpty())
        assertTrue(plyo.any { it.name == "Pogo hops" })
        assertTrue(plyo.any { it.name == "Box jump" })
        assertFalse(plyo.any { it.name == "Back squat" || it.name == "Assisted pull-up" })

        val iso = FreeformExerciseCatalog.filter(
            catalog = mixedCatalog,
            scope = PickerScope.ISOMETRICS,
            query = "",
            recentNames = emptyList()
        )
        assertTrue(iso.any { it.name == "Wall sit" })
        assertFalse(iso.any { it.name == "Back squat" })

        val recent = FreeformExerciseCatalog.filter(
            catalog = mixedCatalog,
            scope = PickerScope.RECENT,
            query = "",
            recentNames = listOf("Box jump", "Custom ankle hop")
        )
        assertEquals(listOf("Box jump", "Custom ankle hop"), recent.map { it.name })
    }

    private fun exercise(id: String, name: String, categories: List<String>) = ExerciseLibraryRecordV2(
        id = id,
        name = name,
        kind = "exercise",
        categories = categories,
        movementPatterns = listOf("squat"),
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList(),
        equipmentOptions = listOf(emptySet()),
        setupNotes = emptyList(),
        difficulty = "all",
        prerequisites = emptySet(),
        prerequisiteNotes = emptyList(),
        regressions = emptyList(),
        progressions = emptyList(),
        alternatives = emptyList(),
        technique = emptyList(),
        easier = "",
        harder = "",
        caveats = emptyList(),
        provenance = emptyList(),
        media = ExerciseMediaV2(
            status = "none",
            bundledFile = null,
            author = null,
            license = null,
            licenseUrl = null,
            verificationUrls = emptyList(),
            reason = "test",
            fit = ""
        )
    )
}
