package io.stridelab.privatealpha

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Compile with the exact read-only PR #25 selector fixture, never production Engine2 sources. */
class ExerciseLibrarySelectionCompatibilityTest {
    @Test fun exhaustiveReviewedSelectorContractRemainsValidAgainstEnrichedLibrary() {
        val library = ExerciseLibraryV2.load { File("native-android/app/src/main/assets", it).inputStream() }
        val tokens = mapOf(Engine2Equipment.DUMBBELLS to "dumbbells", Engine2Equipment.BENCH to "bench",
            Engine2Equipment.BAND to "band", Engine2Equipment.ANCHOR to "anchor", Engine2Equipment.PULLUP_BAR to "pullup_bar",
            Engine2Equipment.RAISED_SURFACE to "raised_surface", Engine2Equipment.MAT to "mat", Engine2Equipment.WALL to "wall",
            Engine2Equipment.PARALLETTES to "parallettes")
        val options = Engine2ExerciseSelection.reviewed
        val skillSets = listOf(emptySet<String>(), setOf("core-tuck"), setOf("pull-hang"), options.map { it.catalogId }.toSet())
        var cases = 0
        for(mask in 0 until (1 shl tokens.size)) {
            val equipment = tokens.keys.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            for(discipline in Engine2ExerciseDiscipline.entries) for(movement in Engine2Movement.entries)
                for(tier in Engine2Skill.entries) for(skills in skillSets) {
                    val selected = Engine2ExerciseSelection.available(discipline, movement, equipment, skills, tier)
                    val expected = options.filter { it.discipline==discipline && it.movement==movement && it.executable &&
                        it.skill.ordinal<=tier.ordinal && equipment.containsAll(it.needs) && skills.containsAll(it.confirmedPrerequisites) }
                        .sortedWith(compareBy<Engine2ExerciseOption> { it.skill.ordinal }.thenBy { it.catalogId })
                    assertEquals(expected, selected)
                    assertEquals(selected, Engine2ExerciseSelection.available(discipline,movement,equipment,skills,tier))
                    for(option in selected) {
                        val record = library.find(option.catalogId)!!
                        assertEquals("exercise",record.kind)
                        assertTrue(record.equipmentOptions.any { equipment.mapNotNull(tokens::get).containsAll(it) })
                    }
                    cases++
                }
        }
        assertEquals(73728, cases)
        assertEquals(18, options.size)
        assertEquals(6, options.count { !it.executable })
    }
}
