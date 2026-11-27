package io.stridelab.privatealpha

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.util.UUID

internal enum class WorkoutHubCategory(
    val label: String,
    val discipline: ManualDiscipline,
    val accentColor: Color
) {
    RUNNING("Running", ManualDiscipline.RUNNING, Color(0xFFD4FF33)),
    STRENGTH("Strength", ManualDiscipline.STRENGTH, Color(0xFF60A5FA)),
    CALISTHENICS("Calisthenics", ManualDiscipline.CALISTHENICS, Color(0xFF34D399)),
    CONDITIONING("Conditioning", ManualDiscipline.CONDITIONING, Color(0xFFFB923C)),
    PLYOMETRICS("Plyometrics", ManualDiscipline.PLYOMETRICS, Color(0xFFFBBF24)),
    ISOMETRICS("Isometrics", ManualDiscipline.ISOMETRICS, Color(0xFF2DD4BF)),
    MOBILITY("Mobility", ManualDiscipline.CALISTHENICS, Color(0xFFA78BFA)),
    RECOVERY("Recovery", ManualDiscipline.CONDITIONING, Color(0xFF38BDF8))
}

internal enum class WorkoutDifficulty(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced")
}

internal enum class WorkoutIntensity(val label: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High")
}

internal enum class WorkoutEquipment(val label: String) {
    NONE("Bodyweight"),
    DUMBBELLS("Dumbbells"),
    BARBELL("Barbell"),
    PULL_UP_BAR("Pull-up bar"),
    BANDS("Resistance bands"),
    KETTLEBELL("Kettlebell"),
    BENCH("Bench")
}

internal data class CuratedExerciseStep(
    val name: String,
    val sets: Int,
    val reps: Int? = null,
    val holdSeconds: Int? = null,
    val restSeconds: Int = 45,
    val loadKg: Double? = null,
    val cue: String = "",
    val workSeconds: Int? = null
) {
    fun toFreeformExercise(): FreeformExercise {
        val measure = FreeformMeasure(
            quantity = reps?.toString().orEmpty(),
            seconds = (holdSeconds ?: workSeconds)?.toString().orEmpty(),
            kg = loadKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()
        )
        val setList = List(sets.coerceAtLeast(1)) {
            FreeformSet(
                target = measure,
                restSeconds = if (restSeconds > 0) restSeconds.toString() else ""
            )
        }
        return FreeformExercise(
            name = name,
            sets = setList,
            notes = cue
        )
    }
}

internal data class CuratedWorkout(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: WorkoutHubCategory,
    val durationMinutes: Int,
    val difficulty: WorkoutDifficulty,
    val intensity: WorkoutIntensity,
    val equipment: Set<WorkoutEquipment>,
    val targetFocus: String,
    val coachingNotes: String,
    val steps: List<CuratedExerciseStep>,
    val runType: String = "",
    val distanceKm: String = ""
) {
    fun toManualTemplate(owner: String): ManualWorkoutTemplate {
        val isRunning = category == WorkoutHubCategory.RUNNING
        val recipe = WorkoutRecipe(
            notes = coachingNotes,
            runType = if (isRunning) runType else "",
            distanceKm = if (isRunning) distanceKm else "",
            durationMinutes = if (isRunning) durationMinutes.toString() else "",
            exercises = if (isRunning) emptyList() else steps.map { it.toFreeformExercise() }
        )
        return ManualWorkoutTemplate(
            id = "curated-$id",
            owner = owner,
            name = title,
            discipline = category.discipline,
            exercises = emptyList(),
            recipe = recipe
        )
    }

    fun toFreeformWorkout(owner: String, date: LocalDate, now: Long): FreeformWorkout {
        val isRunning = category == WorkoutHubCategory.RUNNING
        return FreeformWorkout(
            id = UUID.randomUUID().toString(),
            owner = owner,
            discipline = category.discipline,
            performedOn = date.toString(),
            startedAt = now,
            modifiedAt = now,
            templateId = "curated-$id",
            workoutName = title,
            notes = coachingNotes,
            runType = if (isRunning) runType else "",
            targetDistanceKm = if (isRunning) distanceKm else "",
            targetDurationMinutes = if (isRunning) durationMinutes.toString() else "",
            durationMinutes = if (isRunning) durationMinutes.toString() else "",
            exercises = if (isRunning) emptyList() else steps.map { it.toFreeformExercise() }
        )
    }

    fun toGuidedCompletionDraft(
        owner: String,
        date: LocalDate,
        now: Long,
        elapsedMinutes: Int
    ): FreeformWorkout {
        val loggedMinutes = elapsedMinutes.coerceAtLeast(1).toString()
        val base = toFreeformWorkout(owner, date, now)
        return if (category.discipline == ManualDiscipline.RUNNING) {
            base.copy(
                distanceKm = distanceKm.ifBlank { "5.0" },
                durationMinutes = loggedMinutes
            )
        } else {
            base.copy(
                exercises = base.exercises.map { ex ->
                    ex.copy(
                        sets = ex.sets.map { set ->
                            set.copy(actual = set.target, completed = true)
                        }
                    )
                }
            )
        }
    }
}

internal object CuratedWorkoutLibrary {
    fun byId(id: String): CuratedWorkout? = all.firstOrNull { it.id == id }

    val all: List<CuratedWorkout> = listOf(
        // RUNNING (5)
        CuratedWorkout(
            id = "run_aerobic_shakeout",
            title = "Aerobic Base Shakeout",
            subtitle = "Relaxed conversational pace to build capillary density",
            category = WorkoutHubCategory.RUNNING,
            durationMinutes = 30,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Aerobic base & recovery",
            coachingNotes = "Keep breathing nasal or conversational. Stay in Zone 1 to Zone 2 throughout.",
            steps = listOf(
                CuratedExerciseStep("Easy aerobic run", sets = 1, workSeconds = 1500, restSeconds = 0, cue = "Tall posture, quick light cadence"),
                CuratedExerciseStep("Relaxed strides", sets = 4, workSeconds = 20, restSeconds = 40, cue = "Smooth acceleration, never strain")
            ),
            runType = "Easy",
            distanceKm = "5.0"
        ),
        CuratedWorkout(
            id = "run_cruise_threshold",
            title = "Cruise Threshold Intervals",
            subtitle = "Controlled tempo repeats to raise lactate clearance",
            category = WorkoutHubCategory.RUNNING,
            durationMinutes = 42,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Lactate threshold",
            coachingNotes = "10 min easy warmup, 4 x 5 min at threshold effort with 90s jog recovery, 10 min cooldown.",
            steps = listOf(
                CuratedExerciseStep("Easy warmup jog", sets = 1, workSeconds = 600, restSeconds = 60, cue = "Settle rhythm before work"),
                CuratedExerciseStep("Threshold repeat", sets = 4, workSeconds = 300, restSeconds = 90, cue = "Comfortably hard, steady effort"),
                CuratedExerciseStep("Easy cooldown jog", sets = 1, workSeconds = 480, restSeconds = 0, cue = "Drop heart rate smoothly")
            ),
            runType = "Tempo",
            distanceKm = "8.0"
        ),
        CuratedWorkout(
            id = "run_400m_speed",
            title = "Track 400m Speed Repeats",
            subtitle = "Crisp neuromuscular turnover with full walk-jog recoveries",
            category = WorkoutHubCategory.RUNNING,
            durationMinutes = 38,
            difficulty = WorkoutDifficulty.ADVANCED,
            intensity = WorkoutIntensity.HIGH,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "VO2max & running economy",
            coachingNotes = "8 x 90s fast intervals with 90s easy jog recovery between reps.",
            steps = listOf(
                CuratedExerciseStep("Warmup & drills", sets = 1, workSeconds = 600, restSeconds = 60, cue = "Include A-skips and high knees"),
                CuratedExerciseStep("400m fast repeat", sets = 8, workSeconds = 90, restSeconds = 90, cue = "Fast relaxed arms, midfoot strike"),
                CuratedExerciseStep("Cooldown jog", sets = 1, workSeconds = 480, restSeconds = 0, cue = "Walk final 2 minutes")
            ),
            runType = "Intervals",
            distanceKm = "7.5"
        ),
        CuratedWorkout(
            id = "run_playful_fartlek",
            title = "Rolling Fartlek Builder",
            subtitle = "Alternating surges to teach pace gear changes",
            category = WorkoutHubCategory.RUNNING,
            durationMinutes = 35,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Pace control & stamina",
            coachingNotes = "6 rounds of 2 min steady surge followed by 2 min easy float.",
            steps = listOf(
                CuratedExerciseStep("Easy warmup", sets = 1, workSeconds = 480, restSeconds = 30, cue = "Find loose stride"),
                CuratedExerciseStep("2 min surge / 2 min float", sets = 6, workSeconds = 120, restSeconds = 120, cue = "Float stays aerobic, never stop"),
                CuratedExerciseStep("Cooldown jog", sets = 1, workSeconds = 360, restSeconds = 0, cue = "Shake out calves")
            ),
            runType = "Fartlek",
            distanceKm = "6.5"
        ),
        CuratedWorkout(
            id = "run_hill_power",
            title = "Short Hill Sprints",
            subtitle = "Low-impact steep inclines for stride power and stiffness",
            category = WorkoutHubCategory.RUNNING,
            durationMinutes = 32,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.HIGH,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Posterior chain power",
            coachingNotes = "Find a 6-8% hill. Drive knees for 15s and walk all the way down.",
            steps = listOf(
                CuratedExerciseStep("Easy flat warmup", sets = 1, workSeconds = 600, restSeconds = 60, cue = "Warm ankles and hips"),
                CuratedExerciseStep("15s steep hill sprint", sets = 8, workSeconds = 15, restSeconds = 90, cue = "Full recovery walk down"),
                CuratedExerciseStep("Easy cooldown jog", sets = 1, workSeconds = 480, restSeconds = 0, cue = "Keep stride short and light")
            ),
            runType = "Hills",
            distanceKm = "5.5"
        ),

        // STRENGTH (5)
        CuratedWorkout(
            id = "str_runner_durability",
            title = "Runner Durability Strength",
            subtitle = "Single-leg stability and posterior chain armor for miles",
            category = WorkoutHubCategory.STRENGTH,
            durationMinutes = 35,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.DUMBBELLS),
            targetFocus = "Glutes, hamstrings & calves",
            coachingNotes = "Control the eccentric for 3 seconds on every rep. Leave 2 reps in reserve.",
            steps = listOf(
                CuratedExerciseStep("Bulgarian split squat", sets = 3, reps = 8, restSeconds = 75, loadKg = 14.0, cue = "Each side, tall torso"),
                CuratedExerciseStep("Single-leg Romanian deadlift", sets = 3, reps = 10, restSeconds = 60, loadKg = 14.0, cue = "Hinge hips back, square pelvis"),
                CuratedExerciseStep("Standing dumbbell calf raise", sets = 3, reps = 15, restSeconds = 45, loadKg = 16.0, cue = "Full pause at top and bottom"),
                CuratedExerciseStep("Dead bug", sets = 3, reps = 12, restSeconds = 45, cue = "Press lower back into floor")
            )
        ),
        CuratedWorkout(
            id = "str_full_body_foundation",
            title = "Full Body Dumbbell Foundation",
            subtitle = "Balanced push, pull, squat, and hinge session",
            category = WorkoutHubCategory.STRENGTH,
            durationMinutes = 40,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.DUMBBELLS, WorkoutEquipment.BENCH),
            targetFocus = "Full body strength",
            coachingNotes = "Focus on clean range of motion and consistent tempo.",
            steps = listOf(
                CuratedExerciseStep("Goblet squat", sets = 3, reps = 10, restSeconds = 75, loadKg = 18.0, cue = "Elbows inside knees"),
                CuratedExerciseStep("Dumbbell bench press", sets = 3, reps = 10, restSeconds = 75, loadKg = 16.0, cue = "45-degree elbow tuck"),
                CuratedExerciseStep("One-arm dumbbell row", sets = 3, reps = 10, restSeconds = 60, loadKg = 18.0, cue = "Pull elbow toward hip"),
                CuratedExerciseStep("Dumbbell Romanian deadlift", sets = 3, reps = 10, restSeconds = 75, loadKg = 20.0, cue = "Soft knee, flat back")
            )
        ),
        CuratedWorkout(
            id = "str_upper_push_pull",
            title = "Upper Body Push & Pull",
            subtitle = "Posture, shoulder health, and upper torso strength",
            category = WorkoutHubCategory.STRENGTH,
            durationMinutes = 36,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.DUMBBELLS, WorkoutEquipment.BENCH),
            targetFocus = "Chest, back & shoulders",
            coachingNotes = "Pair horizontal push with horizontal pull for balanced shoulder mechanics.",
            steps = listOf(
                CuratedExerciseStep("Incline dumbbell press", sets = 4, reps = 8, restSeconds = 75, loadKg = 16.0, cue = "Control bottom stretch"),
                CuratedExerciseStep("Chest-supported dumbbell row", sets = 4, reps = 10, restSeconds = 75, loadKg = 16.0, cue = "Squeeze shoulder blades"),
                CuratedExerciseStep("Seated overhead dumbbell press", sets = 3, reps = 10, restSeconds = 60, loadKg = 12.0, cue = "Ribs stacked over pelvis"),
                CuratedExerciseStep("Band face pull", sets = 3, reps = 15, restSeconds = 45, cue = "External rotation at finish")
            )
        ),
        CuratedWorkout(
            id = "str_heavy_lower_barbell",
            title = "Barbell Lower Power",
            subtitle = "Low-rep compound strength for force production",
            category = WorkoutHubCategory.STRENGTH,
            durationMinutes = 45,
            difficulty = WorkoutDifficulty.ADVANCED,
            intensity = WorkoutIntensity.HIGH,
            equipment = setOf(WorkoutEquipment.BARBELL),
            targetFocus = "Maximal lower body strength",
            coachingNotes = "Rest 2 minutes between heavy sets. Keep bar speed crisp.",
            steps = listOf(
                CuratedExerciseStep("Barbell back squat", sets = 4, reps = 5, restSeconds = 120, loadKg = 60.0, cue = "Brace core before descent"),
                CuratedExerciseStep("Barbell Romanian deadlift", sets = 3, reps = 6, restSeconds = 105, loadKg = 60.0, cue = "Bar stays close to shins"),
                CuratedExerciseStep("Weighted step-up", sets = 3, reps = 8, restSeconds = 60, loadKg = 12.0, cue = "Drive through front heel")
            )
        ),
        CuratedWorkout(
            id = "str_kettlebell_hinge",
            title = "Kettlebell Hinge & Carry",
            subtitle = "Posterior power and anti-rotation trunk stability",
            category = WorkoutHubCategory.STRENGTH,
            durationMinutes = 30,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.KETTLEBELL),
            targetFocus = "Hips, grip & core",
            coachingNotes = "Snap hips cleanly on swings; walk tall on carries.",
            steps = listOf(
                CuratedExerciseStep("Kettlebell swing", sets = 4, reps = 15, restSeconds = 60, loadKg = 16.0, cue = "Hinge late, snap hips"),
                CuratedExerciseStep("Kettlebell goblet reverse lunge", sets = 3, reps = 10, restSeconds = 60, loadKg = 16.0, cue = "Alternate legs smoothly"),
                CuratedExerciseStep("Suitcase carry", sets = 3, holdSeconds = 40, restSeconds = 45, loadKg = 20.0, cue = "Do not lean toward bell")
            )
        ),

        // CALISTHENICS (5)
        CuratedWorkout(
            id = "cal_pullup_ladder",
            title = "Pull-Up Strength Builder",
            subtitle = "Strict vertical pulling volume with scapular control",
            category = WorkoutHubCategory.CALISTHENICS,
            durationMinutes = 30,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.PULL_UP_BAR),
            targetFocus = "Lats, biceps & scapulae",
            coachingNotes = "Start every rep from a dead hang with depressed shoulders.",
            steps = listOf(
                CuratedExerciseStep("Scapular pull-up", sets = 3, reps = 8, restSeconds = 45, cue = "Straight arms, pull chest up"),
                CuratedExerciseStep("Strict pull-up", sets = 4, reps = 6, restSeconds = 90, cue = "Chin clears bar cleanly"),
                CuratedExerciseStep("Inverted row or negatives", sets = 3, reps = 8, restSeconds = 60, cue = "3-second lowering phase"),
                CuratedExerciseStep("Dead hang", sets = 2, holdSeconds = 35, restSeconds = 45, cue = "Relax neck, grip tight")
            )
        ),
        CuratedWorkout(
            id = "cal_pushup_mastery",
            title = "Push-Up & Dip Mastery",
            subtitle = "Bodyweight pressing volume for chest, triceps, and core",
            category = WorkoutHubCategory.CALISTHENICS,
            durationMinutes = 28,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Upper body push & hollow core",
            coachingNotes = "Keep glutes squeezed and body in one rigid line.",
            steps = listOf(
                CuratedExerciseStep("Tempo push-up", sets = 4, reps = 10, restSeconds = 60, cue = "2s down, 1s pause, fast press"),
                CuratedExerciseStep("Pike push-up", sets = 3, reps = 8, restSeconds = 60, cue = "Head moves forward of hands"),
                CuratedExerciseStep("Diamond push-up", sets = 3, reps = 8, restSeconds = 60, cue = "Elbows track back"),
                CuratedExerciseStep("Hollow body hold", sets = 3, holdSeconds = 30, restSeconds = 45, cue = "Lower back glued to mat")
            )
        ),
        CuratedWorkout(
            id = "cal_core_skill_prep",
            title = "Gymnastic Core & Compression",
            subtitle = "L-sit prep and anti-extension trunk control",
            category = WorkoutHubCategory.CALISTHENICS,
            durationMinutes = 25,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Hip flexors, abs & compression",
            coachingNotes = "Quality of position beats rep count.",
            steps = listOf(
                CuratedExerciseStep("Seated pike leg lift", sets = 3, reps = 10, restSeconds = 45, cue = "Point toes, keep knees locked"),
                CuratedExerciseStep("Tuck L-sit hold", sets = 4, holdSeconds = 20, restSeconds = 60, cue = "Push shoulders down hard"),
                CuratedExerciseStep("Hollow rock", sets = 3, reps = 15, restSeconds = 45, cue = "Smooth rocking arc"),
                CuratedExerciseStep("Arch body hold", sets = 3, holdSeconds = 25, restSeconds = 45, cue = "Balance anterior core work")
            )
        ),
        CuratedWorkout(
            id = "cal_pistol_progression",
            title = "Single-Leg Squat Skill",
            subtitle = "Ankle mobility and single-leg strength toward pistol squats",
            category = WorkoutHubCategory.CALISTHENICS,
            durationMinutes = 30,
            difficulty = WorkoutDifficulty.ADVANCED,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Unilateral leg strength & balance",
            coachingNotes = "Use a bench or box target if bottom position rounds.",
            steps = listOf(
                CuratedExerciseStep("Shrimp squat", sets = 3, reps = 6, restSeconds = 75, cue = "Each leg, touch knee lightly"),
                CuratedExerciseStep("Box pistol squat", sets = 4, reps = 5, restSeconds = 75, cue = "Control sit, stand without momentum"),
                CuratedExerciseStep("Single-leg glute bridge", sets = 3, reps = 12, restSeconds = 45, cue = "Full hip extension")
            )
        ),
        CuratedWorkout(
            id = "cal_bodyweight_flow",
            title = "No-Gear Full Body Calisthenics",
            subtitle = "Clean hotel-room or park strength circuit",
            category = WorkoutHubCategory.CALISTHENICS,
            durationMinutes = 26,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "General bodyweight fitness",
            coachingNotes = "Move smoothly with controlled breathing.",
            steps = listOf(
                CuratedExerciseStep("Bodyweight squat", sets = 3, reps = 15, restSeconds = 45, cue = "Full depth, heels flat"),
                CuratedExerciseStep("Incline or floor push-up", sets = 3, reps = 12, restSeconds = 45, cue = "Full chest touch"),
                CuratedExerciseStep("Prone Y-T-W raise", sets = 3, reps = 10, restSeconds = 45, cue = "Activate upper back"),
                CuratedExerciseStep("Forearm plank", sets = 3, holdSeconds = 40, restSeconds = 45, cue = "Push floor away")
            )
        ),

        // CONDITIONING (5)
        CuratedWorkout(
            id = "cond_emom_engine",
            title = "20-Min Engine EMOM",
            subtitle = "Every minute on the minute aerobic-power intervals",
            category = WorkoutHubCategory.CONDITIONING,
            durationMinutes = 22,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.HIGH,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Work capacity & repeatability",
            coachingNotes = "Finish each minute's work in 35-40s to earn 20s rest.",
            steps = listOf(
                CuratedExerciseStep("Burpee to step-back", sets = 5, workSeconds = 40, restSeconds = 20, cue = "Steady repeatable pace"),
                CuratedExerciseStep("Alternating jump lunge", sets = 5, workSeconds = 40, restSeconds = 20, cue = "Soft knee landing"),
                CuratedExerciseStep("Mountain climber sprint", sets = 5, workSeconds = 40, restSeconds = 20, cue = "Keep hips level"),
                CuratedExerciseStep("Fast air squat", sets = 5, workSeconds = 40, restSeconds = 20, cue = "Smooth rhythm")
            )
        ),
        CuratedWorkout(
            id = "cond_tabata_burner",
            title = "Tabata Metabolic Finisher",
            subtitle = "Short 20s max / 10s rest blocks for anaerobic punch",
            category = WorkoutHubCategory.CONDITIONING,
            durationMinutes = 16,
            difficulty = WorkoutDifficulty.ADVANCED,
            intensity = WorkoutIntensity.HIGH,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Anaerobic capacity",
            coachingNotes = "4 rounds of 4 minutes with 60s rest between blocks.",
            steps = listOf(
                CuratedExerciseStep("High-knee sprint", sets = 4, workSeconds = 20, restSeconds = 10, cue = "Quick ground contact"),
                CuratedExerciseStep("Squat thrust", sets = 4, workSeconds = 20, restSeconds = 10, cue = "Crisp plank snap"),
                CuratedExerciseStep("Skater bound", sets = 4, workSeconds = 20, restSeconds = 10, cue = "Stick lateral landing"),
                CuratedExerciseStep("Plank shoulder tap", sets = 4, workSeconds = 20, restSeconds = 10, cue = "Zero hip sway")
            )
        ),
        CuratedWorkout(
            id = "cond_kb_amrap",
            title = "Kettlebell Conditioning Complex",
            subtitle = "18-minute low-impact heart rate builder",
            category = WorkoutHubCategory.CONDITIONING,
            durationMinutes = 20,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.HIGH,
            equipment = setOf(WorkoutEquipment.KETTLEBELL),
            targetFocus = "Cardiorespiratory & grip stamina",
            coachingNotes = "Move cleanly between exercises without putting the bell down mid-round.",
            steps = listOf(
                CuratedExerciseStep("Kettlebell swing", sets = 4, reps = 15, restSeconds = 20, loadKg = 16.0, cue = "Crisp hip drive"),
                CuratedExerciseStep("Goblet squat", sets = 4, reps = 10, restSeconds = 20, loadKg = 16.0, cue = "Breathe at top"),
                CuratedExerciseStep("Alternating clean & press", sets = 4, reps = 8, restSeconds = 60, loadKg = 14.0, cue = "Zipper pull close to body")
            )
        ),
        CuratedWorkout(
            id = "cond_zone2_circuit",
            title = "Aerobic Tempo Circuit",
            subtitle = "Continuous low-stress conditioning without joint pounding",
            category = WorkoutHubCategory.CONDITIONING,
            durationMinutes = 24,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Aerobic conditioning",
            coachingNotes = "Keep effort at 6/10 throughout so heart rate stays steady.",
            steps = listOf(
                CuratedExerciseStep("Shadow boxing", sets = 4, workSeconds = 60, restSeconds = 20, cue = "Light footwork, relaxed shoulders"),
                CuratedExerciseStep("Step-back lunge", sets = 4, workSeconds = 45, restSeconds = 20, cue = "Alternating smooth reps"),
                CuratedExerciseStep("Inchworm walkout", sets = 4, workSeconds = 45, restSeconds = 30, cue = "Strong plank pause")
            )
        ),
        CuratedWorkout(
            id = "cond_band_metcon",
            title = "Band & Bodyweight Sprint",
            subtitle = "Joint-friendly conditioning using light resistance bands",
            category = WorkoutHubCategory.CONDITIONING,
            durationMinutes = 22,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.BANDS),
            targetFocus = "Muscular endurance",
            coachingNotes = "Keep tension on the band through every rep.",
            steps = listOf(
                CuratedExerciseStep("Banded squat to press", sets = 4, workSeconds = 40, restSeconds = 20, cue = "Full extension overhead"),
                CuratedExerciseStep("Banded bent-over row", sets = 4, workSeconds = 40, restSeconds = 20, cue = "Fast pull, controlled return"),
                CuratedExerciseStep("Jumping jack", sets = 4, workSeconds = 40, restSeconds = 30, cue = "Light on balls of feet")
            )
        ),

        // PLYOMETRICS (4)
        CuratedWorkout(
            id = "plyo_reactive_ankles",
            title = "Reactive Ankle & Tendon Pop",
            subtitle = "Low-amplitude hops for stiff, springy running ground contact",
            category = WorkoutHubCategory.PLYOMETRICS,
            durationMinutes = 18,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Achilles & calf elasticity",
            coachingNotes = "Minimize ground contact time. Stop immediately if form gets heavy.",
            steps = listOf(
                CuratedExerciseStep("Two-foot pogo hop", sets = 4, reps = 20, restSeconds = 45, cue = "Dorsiflex toes, bounce off midfoot"),
                CuratedExerciseStep("Single-leg pogo hop", sets = 3, reps = 12, restSeconds = 60, cue = "Each leg, tall ankle"),
                CuratedExerciseStep("A-skip", sets = 3, workSeconds = 25, restSeconds = 45, cue = "Crisp downward strike")
            )
        ),
        CuratedWorkout(
            id = "plyo_runner_power",
            title = "Horizontal & Lateral Bound",
            subtitle = "Multi-directional force absorption and stride projection",
            category = WorkoutHubCategory.PLYOMETRICS,
            durationMinutes = 22,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.HIGH,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Stride power & hip stability",
            coachingNotes = "Rest fully between sets so every jump is maximal quality.",
            steps = listOf(
                CuratedExerciseStep("Broad jump with stick", sets = 4, reps = 5, restSeconds = 75, cue = "Hold landing for 2 seconds"),
                CuratedExerciseStep("Single-leg lateral bound", sets = 4, reps = 8, restSeconds = 75, cue = "Push off outside edge"),
                CuratedExerciseStep("Split squat jump", sets = 3, reps = 8, restSeconds = 75, cue = "Quiet soft landing")
            )
        ),
        CuratedWorkout(
            id = "plyo_explosive_vert",
            title = "Explosive Vertical Power",
            subtitle = "Maximal intent countermovement and tuck jumps",
            category = WorkoutHubCategory.PLYOMETRICS,
            durationMinutes = 24,
            difficulty = WorkoutDifficulty.ADVANCED,
            intensity = WorkoutIntensity.HIGH,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Rate of force development",
            coachingNotes = "Perform fresh before heavy fatigue sets in.",
            steps = listOf(
                CuratedExerciseStep("Countermovement jump", sets = 4, reps = 5, restSeconds = 90, cue = "Reach as high as possible"),
                CuratedExerciseStep("Snap-down to vertical jump", sets = 4, reps = 4, restSeconds = 90, cue = "Fast catch and rebound"),
                CuratedExerciseStep("Alternating bounding", sets = 3, reps = 10, restSeconds = 90, cue = "Drive knee and opposite arm")
            )
        ),
        CuratedWorkout(
            id = "plyo_warmup_primer",
            title = "Pre-Run Neural Primer",
            subtitle = "8-minute fast-twitch activation before speed work or races",
            category = WorkoutHubCategory.PLYOMETRICS,
            durationMinutes = 10,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Neuromuscular readiness",
            coachingNotes = "Leave feeling bouncy, never fatigued.",
            steps = listOf(
                CuratedExerciseStep("Ankle bounce", sets = 2, reps = 15, restSeconds = 30, cue = "Light rhythmic hops"),
                CuratedExerciseStep("Skipping for height", sets = 2, reps = 12, restSeconds = 40, cue = "Pop off forefoot"),
                CuratedExerciseStep("Quick-foot lateral shuffle", sets = 2, workSeconds = 15, restSeconds = 30, cue = "Stay athletic and low")
            )
        ),

        // ISOMETRICS (4)
        CuratedWorkout(
            id = "iso_tendon_shield",
            title = "Patellar & Achilles Tendon Shield",
            subtitle = "Heavy-yield isometric holds for runner tendon resilience",
            category = WorkoutHubCategory.ISOMETRICS,
            durationMinutes = 20,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Knee & Achilles tendon health",
            coachingNotes = "Ideal on recovery days or when tendons feel mildly stiff.",
            steps = listOf(
                CuratedExerciseStep("Single-leg wall sit", sets = 4, holdSeconds = 35, restSeconds = 60, cue = "60-degree knee angle"),
                CuratedExerciseStep("Bent-knee soleus calf hold", sets = 4, holdSeconds = 40, restSeconds = 45, cue = "Hold high on forefoot"),
                CuratedExerciseStep("Spanish squat isometric", sets = 3, holdSeconds = 45, restSeconds = 60, cue = "Vertical shins, sit back")
            )
        ),
        CuratedWorkout(
            id = "iso_core_pillars",
            title = "Anti-Rotation Core Pillars",
            subtitle = "Long-lever static holds to lock down running posture",
            category = WorkoutHubCategory.ISOMETRICS,
            durationMinutes = 18,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Trunk stiffness & pelvic control",
            coachingNotes = "Breathe steadily behind a braced abdominal wall.",
            steps = listOf(
                CuratedExerciseStep("RKC plank", sets = 3, holdSeconds = 30, restSeconds = 45, cue = "Pull elbows toward toes"),
                CuratedExerciseStep("Side plank with hip abduction", sets = 3, holdSeconds = 30, restSeconds = 45, cue = "Each side, stack hips"),
                CuratedExerciseStep("Copenhagen adductor hold", sets = 3, holdSeconds = 25, restSeconds = 45, cue = "Each side, top leg supports")
            )
        ),
        CuratedWorkout(
            id = "iso_lower_stamina",
            title = "Lower Body Isometric Stamina",
            subtitle = "Split squat and hinge holds for muscular endurance",
            category = WorkoutHubCategory.ISOMETRICS,
            durationMinutes = 22,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Quad & hamstring endurance",
            coachingNotes = "Keep weight evenly distributed across the front foot.",
            steps = listOf(
                CuratedExerciseStep("Split squat bottom hold", sets = 3, holdSeconds = 40, restSeconds = 50, cue = "Back knee 2 inches off floor"),
                CuratedExerciseStep("Single-leg glute bridge hold", sets = 3, holdSeconds = 35, restSeconds = 45, cue = "Each side, level hips"),
                CuratedExerciseStep("Wall sit", sets = 3, holdSeconds = 50, restSeconds = 60, cue = "Press lower back into wall")
            )
        ),
        CuratedWorkout(
            id = "iso_hamstring_armor",
            title = "Hamstring & Hip Flexor Armor",
            subtitle = "Long-lever posterior holds to guard against sprint strains",
            category = WorkoutHubCategory.ISOMETRICS,
            durationMinutes = 20,
            difficulty = WorkoutDifficulty.ADVANCED,
            intensity = WorkoutIntensity.MODERATE,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Long-lever hamstring strength",
            coachingNotes = "Drive heels down hard without cramping.",
            steps = listOf(
                CuratedExerciseStep("Long-lever single-leg bridge hold", sets = 4, holdSeconds = 30, restSeconds = 50, cue = "Knee bent ~150 degrees"),
                CuratedExerciseStep("L-sit floor compression hold", sets = 3, holdSeconds = 20, restSeconds = 45, cue = "Lift heels cleanly"),
                CuratedExerciseStep("Horse stance hold", sets = 3, holdSeconds = 45, restSeconds = 45, cue = "Wide stance, upright spine")
            )
        ),

        // MOBILITY (4)
        CuratedWorkout(
            id = "mob_hip_ankle_opener",
            title = "Runner Hip & Ankle Mobility",
            subtitle = "Restore stride extension and ankle dorsiflexion range",
            category = WorkoutHubCategory.MOBILITY,
            durationMinutes = 15,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Hip flexors, glutes & ankles",
            coachingNotes = "Move slowly into end ranges without bouncing.",
            steps = listOf(
                CuratedExerciseStep("Half-kneeling ankle rock", sets = 2, reps = 12, restSeconds = 20, cue = "Keep heel glued to floor"),
                CuratedExerciseStep("90/90 hip switch", sets = 2, reps = 10, restSeconds = 20, cue = "Sit tall through transitions"),
                CuratedExerciseStep("World's greatest stretch", sets = 2, reps = 6, restSeconds = 20, cue = "Reach chest toward ceiling")
            )
        ),
        CuratedWorkout(
            id = "mob_thoracic_shoulder",
            title = "Thoracic & Shoulder Flow",
            subtitle = "Undo desk posture for cleaner arm swing and overhead lifts",
            category = WorkoutHubCategory.MOBILITY,
            durationMinutes = 14,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "T-spine rotation & shoulders",
            coachingNotes = "Exhale as you rotate deeper into each rep.",
            steps = listOf(
                CuratedExerciseStep("Open book thoracic rotation", sets = 2, reps = 8, restSeconds = 20, cue = "Keep knees pinned together"),
                CuratedExerciseStep("Wall slide", sets = 3, reps = 10, restSeconds = 20, cue = "Wrists and elbows stay on wall"),
                CuratedExerciseStep("Cat-cow", sets = 2, reps = 10, restSeconds = 15, cue = "Segment each vertebra")
            )
        ),
        CuratedWorkout(
            id = "mob_deep_squat_prep",
            title = "Deep Squat & Overhead Prep",
            subtitle = "Dynamic warmup before lower-body strength or calisthenics",
            category = WorkoutHubCategory.MOBILITY,
            durationMinutes = 12,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Squat depth & hip capsule",
            coachingNotes = "Spend 5 breaths in the bottom squat pry.",
            steps = listOf(
                CuratedExerciseStep("Deep squat pry", sets = 3, holdSeconds = 30, restSeconds = 20, cue = "Press knees out with elbows"),
                CuratedExerciseStep("Cossack squat", sets = 2, reps = 10, restSeconds = 30, cue = "Shift smoothly side to side"),
                CuratedExerciseStep("Scapular push-up", sets = 2, reps = 12, restSeconds = 20, cue = "Glide shoulder blades")
            )
        ),
        CuratedWorkout(
            id = "mob_morning_reset",
            title = "10-Min Morning Joint Reset",
            subtitle = "Gentle daily CARs sequence for hips, spine, and ankles",
            category = WorkoutHubCategory.MOBILITY,
            durationMinutes = 10,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Full body joint lubrication",
            coachingNotes = "Smooth, tension-free circles.",
            steps = listOf(
                CuratedExerciseStep("Standing hip circle", sets = 2, reps = 8, restSeconds = 15, cue = "Each direction"),
                CuratedExerciseStep("Thoracic windmill", sets = 2, reps = 8, restSeconds = 15, cue = "Follow hand with eyes"),
                CuratedExerciseStep("Ankle alphabet", sets = 2, holdSeconds = 30, restSeconds = 15, cue = "Each foot")
            )
        ),

        // RECOVERY (4)
        CuratedWorkout(
            id = "rec_post_long_run",
            title = "Post-Long Run Flush & Stretch",
            subtitle = "Downregulate nervous system and ease calf and hip tightness",
            category = WorkoutHubCategory.RECOVERY,
            durationMinutes = 16,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Parasympathetic recovery",
            coachingNotes = "4-second inhale, 6-second exhale on every hold.",
            steps = listOf(
                CuratedExerciseStep("Legs up the wall", sets = 1, holdSeconds = 180, restSeconds = 15, cue = "Let calves drain completely"),
                CuratedExerciseStep("Pigeon hip stretch", sets = 2, holdSeconds = 60, restSeconds = 15, cue = "Each side, soften glutes"),
                CuratedExerciseStep("Seated hamstring stretch", sets = 2, holdSeconds = 45, restSeconds = 15, cue = "Hinge from hips, not waist"),
                CuratedExerciseStep("Standing gastrocnemius stretch", sets = 2, holdSeconds = 45, restSeconds = 15, cue = "Each calf, back heel down")
            )
        ),
        CuratedWorkout(
            id = "rec_evening_downshift",
            title = "Evening Sleep Downshift",
            subtitle = "Breathing and gentle floor stretches to boost overnight HRV",
            category = WorkoutHubCategory.RECOVERY,
            durationMinutes = 12,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Sleep readiness & HRV",
            coachingNotes = "Dim lights and keep nasal breathing slow.",
            steps = listOf(
                CuratedExerciseStep("Child's pose with side reach", sets = 2, holdSeconds = 45, restSeconds = 15, cue = "Breathe into back ribs"),
                CuratedExerciseStep("Supine spinal twist", sets = 2, holdSeconds = 45, restSeconds = 15, cue = "Each side, shoulders heavy"),
                CuratedExerciseStep("Box breathing 4-4-6-2", sets = 1, holdSeconds = 180, restSeconds = 0, cue = "Long slow exhale")
            )
        ),
        CuratedWorkout(
            id = "rec_active_walk_mobility",
            title = "Active Recovery Walk & Flush",
            subtitle = "Easy circulation session for sore legs or high-strain days",
            category = WorkoutHubCategory.RECOVERY,
            durationMinutes = 20,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.NONE),
            targetFocus = "Blood flow without fatigue",
            coachingNotes = "Heart rate stays under 60% max throughout.",
            steps = listOf(
                CuratedExerciseStep("Brisk easy walk", sets = 1, workSeconds = 900, restSeconds = 30, cue = "Relaxed arm swing"),
                CuratedExerciseStep("Couch quad stretch", sets = 2, holdSeconds = 45, restSeconds = 15, cue = "Squeeze glute gently")
            )
        ),
        CuratedWorkout(
            id = "rec_band_decompression",
            title = "Upper Back & Hip Decompression",
            subtitle = "Gentle band-assisted traction after heavy lifting weeks",
            category = WorkoutHubCategory.RECOVERY,
            durationMinutes = 15,
            difficulty = WorkoutDifficulty.BEGINNER,
            intensity = WorkoutIntensity.LOW,
            equipment = setOf(WorkoutEquipment.BANDS),
            targetFocus = "Joint decompression",
            coachingNotes = "Zero strain; let the band create gentle space.",
            steps = listOf(
                CuratedExerciseStep("Band shoulder dislocate", sets = 2, reps = 12, restSeconds = 20, cue = "Wide grip, smooth arc"),
                CuratedExerciseStep("Banded hamstring floss", sets = 2, reps = 12, restSeconds = 20, cue = "Each leg, slow knee extension"),
                CuratedExerciseStep("Diaphragmatic floor breathing", sets = 1, holdSeconds = 150, restSeconds = 0, cue = "Belly rises first")
            )
        )
    )
}
