package io.stridelab.privatealpha

/** Small reviewed subset of the existing StrideLab exercise IDs. Never invent catalog assets. */
internal enum class Engine2Equipment { DUMBBELLS, BENCH, BAND, ANCHOR, PULLUP_BAR, RAISED_SURFACE, MAT, WALL, PARALLETTES }
internal enum class Engine2Skill { FOUNDATION, DEVELOPING, ADVANCED }
internal enum class Engine2Movement { SQUAT, HINGE, PRESS, PULL, CORE, SKILL }
internal enum class Engine2ExerciseDiscipline { STRENGTH, CALISTHENICS }

internal data class Engine2ExerciseOption(
    val catalogId: String,
    val discipline: Engine2ExerciseDiscipline,
    val movement: Engine2Movement,
    val skill: Engine2Skill,
    val needs: Set<Engine2Equipment> = emptySet(),
    val confirmedPrerequisites: Set<String> = emptySet(),
    /** Guide assets explain a progression but MUST NOT become executable prescriptions. */
    val executable: Boolean = true
)

/**
 * An equipment-aware, explicitly staged library, not the entire catalog.
 * Listed prerequisites are a conservative software gate, not a clinical form assessment.
 */
internal object Engine2ExerciseSelection {
    val reviewed = listOf(
        Engine2ExerciseOption("rl", Engine2ExerciseDiscipline.STRENGTH, Engine2Movement.SQUAT, Engine2Skill.FOUNDATION),
        Engine2ExerciseOption("gs", Engine2ExerciseDiscipline.STRENGTH, Engine2Movement.SQUAT, Engine2Skill.FOUNDATION, setOf(Engine2Equipment.DUMBBELLS)),
        Engine2ExerciseOption("dh", Engine2ExerciseDiscipline.STRENGTH, Engine2Movement.HINGE, Engine2Skill.FOUNDATION, setOf(Engine2Equipment.DUMBBELLS)),
        Engine2ExerciseOption("fp", Engine2ExerciseDiscipline.STRENGTH, Engine2Movement.PRESS, Engine2Skill.FOUNDATION, setOf(Engine2Equipment.DUMBBELLS)),
        Engine2ExerciseOption("dr", Engine2ExerciseDiscipline.STRENGTH, Engine2Movement.PULL, Engine2Skill.FOUNDATION, setOf(Engine2Equipment.DUMBBELLS)),
        Engine2ExerciseOption("br", Engine2ExerciseDiscipline.STRENGTH, Engine2Movement.PULL, Engine2Skill.FOUNDATION,
            setOf(Engine2Equipment.BAND, Engine2Equipment.ANCHOR)),
        Engine2ExerciseOption("sq", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.SQUAT, Engine2Skill.FOUNDATION),
        Engine2ExerciseOption("hg", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.HINGE, Engine2Skill.FOUNDATION),
        Engine2ExerciseOption("ip", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.PRESS, Engine2Skill.FOUNDATION, setOf(Engine2Equipment.RAISED_SURFACE)),
        Engine2ExerciseOption("e4", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.PRESS, Engine2Skill.DEVELOPING),
        Engine2ExerciseOption("fc", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.CORE, Engine2Skill.FOUNDATION),
        Engine2ExerciseOption("core-tuck", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.CORE, Engine2Skill.FOUNDATION, setOf(Engine2Equipment.MAT), executable = false),
        Engine2ExerciseOption("core-hollow", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.CORE, Engine2Skill.DEVELOPING,
            setOf(Engine2Equipment.MAT), setOf("core-tuck"), executable = false),
        Engine2ExerciseOption("pull-hang", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.PULL, Engine2Skill.FOUNDATION, setOf(Engine2Equipment.PULLUP_BAR), executable = false),
        Engine2ExerciseOption("pull-scap", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.PULL, Engine2Skill.DEVELOPING,
            setOf(Engine2Equipment.PULLUP_BAR), setOf("pull-hang"), executable = false),
        Engine2ExerciseOption("ap", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.PULL, Engine2Skill.FOUNDATION,
            setOf(Engine2Equipment.PULLUP_BAR, Engine2Equipment.BAND)),
        Engine2ExerciseOption("hand-pike", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.SKILL, Engine2Skill.FOUNDATION, executable = false),
        Engine2ExerciseOption("hand-wall", Engine2ExerciseDiscipline.CALISTHENICS, Engine2Movement.SKILL, Engine2Skill.DEVELOPING,
            setOf(Engine2Equipment.WALL), setOf("hand-pike"), executable = false)
    )
    init {
        require(reviewed.map { it.catalogId }.distinct().size == reviewed.size)
        require(reviewed.all { it.confirmedPrerequisites.all { previous ->
            reviewed.any { candidate -> candidate.catalogId == previous }
        } })
    }

    /** All source catalog guide IDs are discoverable, but are never returned as workouts. */
    val guideOnlyIds = setOf("core-tuck", "core-hollow", "pull-hang", "pull-scap",
        "hand-pike", "hand-wall")

    /** Returned options are suitable to review, not automatically assigned prescriptions. */
    fun available(
        discipline: Engine2ExerciseDiscipline,
        movement: Engine2Movement,
        equipment: Set<Engine2Equipment>,
        confirmedSkills: Set<String> = emptySet(),
        maximumSkill: Engine2Skill = Engine2Skill.FOUNDATION
    ): List<Engine2ExerciseOption> = reviewed.filter { option ->
        option.discipline == discipline && option.movement == movement &&
            option.executable && option.skill.ordinal <= maximumSkill.ordinal &&
            equipment.containsAll(option.needs) &&
            confirmedSkills.containsAll(option.confirmedPrerequisites)
    }.sortedWith(compareBy<Engine2ExerciseOption> { it.skill.ordinal }.thenBy { it.catalogId })
}
