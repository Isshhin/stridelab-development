package io.stridelab.privatealpha

/**
 * Curated, zero-network expansion catalog for universal logging, templates,
 * exercise info sheets, and anatomical muscle maps across Strength, Calisthenics,
 * Plyometrics, Isometrics, Conditioning, and Mobility.
 *
 * Intentionally separate from the SHA-256 anchored 66-record ExerciseLibraryV2 baseline
 * so deterministic Engine 1.5 coached plan generation remains unchanged.
 */
internal object ExpandedExerciseCatalog {

    private fun entry(
        id: String,
        name: String,
        category: String,
        subtitle: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>,
        equipment: Set<String> = emptySet(),
        difficulty: String = "developing",
        technique: List<String>,
        easier: String,
        harder: String
    ): ExerciseLibraryRecordV2 = ExerciseLibraryRecordV2(
        id = id,
        name = name,
        kind = "exercise",
        categories = listOf(category),
        movementPatterns = listOf(subtitle),
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        equipmentOptions = listOf(equipment),
        setupNotes = emptyList(),
        difficulty = difficulty,
        prerequisites = emptySet(),
        prerequisiteNotes = emptyList(),
        regressions = emptyList(),
        progressions = emptyList(),
        alternatives = emptyList(),
        technique = technique,
        easier = easier,
        harder = harder,
        caveats = emptyList(),
        provenance = emptyList(),
        media = ExerciseMediaV2(
            status = "placeholder",
            bundledFile = null,
            author = null,
            license = null,
            licenseUrl = null,
            verificationUrls = emptyList(),
            reason = "curated-expanded-catalog",
            fit = ""
        )
    )

    private fun strength(
        id: String,
        name: String,
        pattern: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>,
        equipment: Set<String>,
        difficulty: String = "developing",
        technique: List<String>,
        easier: String,
        harder: String
    ) = entry(id, name, "strength_gym", pattern, primaryMuscles, secondaryMuscles, equipment, difficulty, technique, easier, harder)

    private fun calisthenics(
        id: String,
        name: String,
        pattern: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>,
        equipment: Set<String> = emptySet(),
        difficulty: String = "developing",
        technique: List<String>,
        easier: String,
        harder: String
    ) = entry(id, name, "calisthenics", pattern, primaryMuscles, secondaryMuscles, equipment, difficulty, technique, easier, harder)

    private fun plyo(
        id: String,
        name: String,
        pattern: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>,
        equipment: Set<String> = emptySet(),
        difficulty: String = "developing",
        technique: List<String>,
        easier: String,
        harder: String
    ) = entry(id, name, "plyometrics", "Plyometrics · $pattern", primaryMuscles, secondaryMuscles, equipment, difficulty, technique, easier, harder)

    private fun iso(
        id: String,
        name: String,
        pattern: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>,
        equipment: Set<String> = emptySet(),
        difficulty: String = "foundation",
        technique: List<String>,
        easier: String,
        harder: String
    ) = entry(id, name, "isometrics", "Isometrics · $pattern", primaryMuscles, secondaryMuscles, equipment, difficulty, technique, easier, harder)

    private fun conditioning(
        id: String,
        name: String,
        pattern: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>,
        equipment: Set<String> = emptySet(),
        difficulty: String = "developing",
        technique: List<String>,
        easier: String,
        harder: String
    ) = entry(id, name, "conditioning", "Conditioning · $pattern", primaryMuscles, secondaryMuscles, equipment, difficulty, technique, easier, harder)

    private fun mobility(
        id: String,
        name: String,
        pattern: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>,
        equipment: Set<String> = emptySet(),
        difficulty: String = "foundation",
        technique: List<String>,
        easier: String,
        harder: String
    ) = entry(id, name, "mobility", "Mobility · $pattern", primaryMuscles, secondaryMuscles, equipment, difficulty, technique, easier, harder)

    // -------------------------------------------------------------------------
    // PLYOMETRICS (22 total: 10 preserved legacy IDs + 12 new movements)
    // -------------------------------------------------------------------------
    val plyometricsRecords: List<ExerciseLibraryRecordV2> = listOf(
        plyo(
            "plyo-pogo-hops", "Pogo hops", "Ankle stiffness",
            listOf("calves", "foot_stabilizers"), listOf("quadriceps", "tibialis_anterior"),
            difficulty = "foundation",
            technique = listOf(
                "Stay tall through the trunk and rebound cleanly off the balls of your feet.",
                "Keep knee bend minimal so the Achilles and calves handle elastic recoil.",
                "Use crisp, quiet ground contacts with steady vertical rhythm."
            ),
            easier = "Bilateral ankle bounces with brief pause",
            harder = "Single-leg pogo hops"
        ),
        plyo(
            "plyo-box-jump", "Box jump", "Lower power",
            listOf("quadriceps", "gluteus_maximus"), listOf("calves", "hamstrings"),
            equipment = setOf("box"),
            difficulty = "foundation",
            technique = listOf(
                "Swing the arms decisively as you extend hips, knees, and ankles together.",
                "Pull knees up smoothly and land softly in an athletic half squat.",
                "Step down one foot at a time between reps to preserve power quality."
            ),
            easier = "Low box jump (30 to 40 cm)",
            harder = "Seated box jump or depth jump"
        ),
        plyo(
            "plyo-broad-jump", "Broad jump", "Horizontal power",
            listOf("gluteus_maximus", "quadriceps"), listOf("hamstrings", "calves"),
            technique = listOf(
                "Load hips back with arms drawn behind your torso before exploding forward.",
                "Drive hips and chest forward at an athletic 45 degree projection angle.",
                "Absorb the landing with bent knees and hips held square for 1 second."
            ),
            easier = "Short horizontal jump to stick",
            harder = "Continuous three-jump broad bound"
        ),
        plyo(
            "plyo-depth-drop", "Depth drop", "Landing mechanics",
            listOf("quadriceps", "gluteus_maximus"), listOf("calves", "trunk_stabilizers"),
            equipment = setOf("box"),
            difficulty = "foundation",
            technique = listOf(
                "Step straight off the box rather than jumping upward first.",
                "Catch the ground cleanly in an athletic stance with knees tracking over toes.",
                "Freeze the landing silently for 2 seconds to reinforce eccentric control."
            ),
            easier = "Snap-down on flat ground",
            harder = "Depth jump to immediate vertical rebound"
        ),
        plyo(
            "plyo-single-leg-hop", "Single-leg hop", "Unilateral reactivity",
            listOf("calves", "quadriceps"), listOf("gluteus_medius", "foot_stabilizers"),
            technique = listOf(
                "Keep your pelvis level and avoid knee collapse inward on push-off.",
                "Rebound quickly through the forefoot while keeping torso upright.",
                "Match contact stiffness and distance symmetry across left and right sides."
            ),
            easier = "Single-leg hop to 2-second stick",
            harder = "Single-leg lateral hurdle hop"
        ),
        plyo(
            "plyo-split-squat-jump", "Split squat jump", "Explosive stride",
            listOf("quadriceps", "gluteus_maximus"), listOf("calves", "gluteus_medius"),
            technique = listOf(
                "Drop smoothly into a balanced split lunge with upright ribs.",
                "Drive through the front midfoot to switch legs cleanly in midair.",
                "Land centered with both knees stacked and absorb without twisting."
            ),
            easier = "Alternating split lunge without jump",
            harder = "Weighted dumbbell split squat jump"
        ),
        plyo(
            "plyo-skater-hops", "Skater hops", "Lateral reactive",
            listOf("gluteus_medius", "quadriceps"), listOf("calves", "adductors"),
            technique = listOf(
                "Push laterally off the outside edge of the stance foot toward the opposite side.",
                "Sink into the landing hip cleanly before redirecting laterally.",
                "Use an athletic arm swing across the body to stabilize rotation."
            ),
            easier = "Step-to-step lateral skate with pause",
            harder = "Continuous lateral skater bound for distance"
        ),
        plyo(
            "plyo-bounds", "Alternate leg bounds", "Running power",
            listOf("gluteus_maximus", "hamstrings"), listOf("calves", "quadriceps"),
            technique = listOf(
                "Drive the swing knee upward and forward with each stride extension.",
                "Strike actively underneath your center of mass rather than reaching with the heel.",
                "Maintain tall posture and rhythmic arm swing throughout the run-out."
            ),
            easier = "Skipping for height",
            harder = "Single-leg uphill bounding"
        ),
        plyo(
            "plyo-a-skip", "A-skip", "Coordination and stiffness",
            listOf("hip_flexors", "calves"), listOf("gluteus_maximus", "abdominals"),
            difficulty = "foundation",
            technique = listOf(
                "Snap the knee up cleanly with the foot dorsiflexed beneath the knee.",
                "Strike down vertically beneath the hip onto a stiff ankle.",
                "Pair opposite arm and leg action smoothly without leaning back."
            ),
            easier = "A-march in place",
            harder = "Fast-leg A-switch"
        ),
        plyo(
            "plyo-tuck-jump", "Tuck jump", "Vertical elastic",
            listOf("quadriceps", "hip_flexors"), listOf("calves", "abdominals"),
            difficulty = "advanced",
            technique = listOf(
                "Jump vertically first, then bring knees crisply toward chest at apex.",
                "Keep the spine braced and avoid rounding the lower back in flight.",
                "Open the legs early to meet the floor with soft, springy knees."
            ),
            easier = "Countermovement vertical jump",
            harder = "Continuous reactive tuck jumps"
        ),
        plyo(
            "plyo-hurdle-hops", "Continuous hurdle hops", "Fast stretch-shortening",
            listOf("calves", "quadriceps"), listOf("gluteus_maximus", "foot_stabilizers"),
            equipment = setOf("hurdles"),
            technique = listOf(
                "Pre-tension the calves and core before each contact.",
                "Keep ground contact brief while clearing low wickets cleanly.",
                "Reset if landing noise increases or posture collapses."
            ),
            easier = "Two-foot pogo hops over floor tape",
            harder = "Single-leg mini-hurdle hops"
        ),
        plyo(
            "plyo-lateral-box-jump", "Lateral box jump", "Frontal plane power",
            listOf("gluteus_medius", "quadriceps"), listOf("calves", "adductors"),
            equipment = setOf("box"),
            technique = listOf(
                "Stand sideways beside the box and load both hips evenly.",
                "Drive sideways and up together, landing softly centered on top.",
                "Step down carefully and repeat equally facing both directions."
            ),
            easier = "Lateral hop over low line",
            harder = "Single-leg lateral step-up jump"
        ),
        plyo(
            "plyo-single-leg-box-jump", "Single-leg box jump", "Unilateral vertical power",
            listOf("quadriceps", "gluteus_maximus"), listOf("gluteus_medius", "calves"),
            equipment = setOf("box"),
            difficulty = "advanced",
            technique = listOf(
                "Use a short, crisp countermovement on one leg while swinging arms up.",
                "Land centered and balanced on the same foot on top of a conservative box height.",
                "Step down slowly to save impact load for quality reps."
            ),
            easier = "Single-leg hop to stick on flat ground",
            harder = "Higher box or lateral single-leg box jump"
        ),
        plyo(
            "plyo-depth-jump", "Depth jump", "Reactive vertical power",
            listOf("quadriceps", "calves"), listOf("gluteus_maximus", "foot_stabilizers"),
            equipment = setOf("box"),
            difficulty = "advanced",
            technique = listOf(
                "Step off the box and hit the floor expecting an immediate rebound.",
                "Minimize ground contact time while driving upward as high as possible.",
                "Keep total reps low (10 to 15 contacts) with full rest between sets."
            ),
            easier = "Depth drop with pause",
            harder = "Depth jump over hurdle"
        ),
        plyo(
            "plyo-drop-stick", "Snap-down to stick", "Deceleration control",
            listOf("quadriceps", "gluteus_maximus"), listOf("hamstrings", "trunk_stabilizers"),
            difficulty = "foundation",
            technique = listOf(
                "Rise onto your toes with arms overhead, then snap down fast into a half squat.",
                "Stop momentum instantaneously without knees caving inward.",
                "Hold the balanced bottom position steady for 2 seconds."
            ),
            easier = "Slow athletic squat catch",
            harder = "Single-leg snap-down"
        ),
        plyo(
            "plyo-medball-chest-pass", "Medicine ball chest pass", "Upper horizontal power",
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "trunk_stabilizers"),
            equipment = setOf("med_ball"),
            difficulty = "foundation",
            technique = listOf(
                "Hold the ball near the sternum with elbows roughly 45 degrees from ribs.",
                "Step forward into the throw and snap both hands through the ball.",
                "Keep ribs stacked over pelvis rather than arching the lower back."
            ),
            easier = "Lighter ball or kneeling chest pass",
            harder = "Explosive step-and-pass into wall rebound"
        ),
        plyo(
            "plyo-medball-slam", "Overhead medicine ball slam", "Total-body triple extension",
            listOf("abdominals", "latissimus_dorsi"), listOf("triceps", "gluteus_maximus"),
            equipment = setOf("med_ball"),
            difficulty = "foundation",
            technique = listOf(
                "Reach tall overhead onto the balls of your feet without over-arching.",
                "Whip the ball down aggressively using lats, abs, and rapid hip hinge.",
                "Catch or gather cleanly with a neutral spine."
            ),
            easier = "Half-kneeling overhead slam",
            harder = "Rotational scoop slam"
        ),
        plyo(
            "plyo-medball-rotational-throw", "Rotational medicine ball throw", "Transverse power",
            listOf("obliques", "trunk_rotators"), listOf("gluteus_maximus", "anterior_deltoid"),
            equipment = setOf("med_ball"),
            technique = listOf(
                "Load weight cleanly onto the back hip before unwinding toward the wall.",
                "Pivot the back foot and let the hips lead the shoulders and hands.",
                "Decelerate smoothly with strong trunk bracing after release."
            ),
            easier = "Half-kneeling rotational pass",
            harder = "Step-behind rotational shotput throw"
        ),
        plyo(
            "plyo-pushup", "Plyometric push-up", "Upper explosive drive",
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "serratus_anterior"),
            technique = listOf(
                "Lower under control with elbows tucked about 45 degrees from your torso.",
                "Press explosively through the palms so hands lift cleanly off the surface.",
                "Catch with soft elbows and tight abs to protect shoulders and wrists."
            ),
            easier = "Incline plyometric push-up on bench",
            harder = "Clap push-up"
        ),
        plyo(
            "plyo-b-skip", "B-skip", "Hamstring snap and cycle",
            listOf("hamstrings", "calves"), listOf("hip_flexors", "gluteus_maximus"),
            technique = listOf(
                "Cycle from a tall A-knee up into a relaxed lower-leg extension.",
                "Whip the foot downward and slightly back under your hip cleanly.",
                "Keep pelvis neutral and torso tall without leaning backward."
            ),
            easier = "Walking B-march",
            harder = "Fast alternating straight-leg bounds"
        ),
        plyo(
            "plyo-fast-leg-switches", "Fast-leg switches", "Hip speed and elasticity",
            listOf("hip_flexors", "calves"), listOf("hamstrings", "abdominals"),
            technique = listOf(
                "Start tall and snap both legs through a sharp scissoring switch in midair.",
                "Strike down rapidly onto the forefoot directly under the hip.",
                "Pause briefly in single-leg balance every 3rd switch to confirm control."
            ),
            easier = "Single wall-supported knee switch",
            harder = "Continuous running high-knee switches"
        ),
        plyo(
            "plyo-scissors-jump", "Scissor box exchange", "Alternating vertical drive",
            listOf("quadriceps", "gluteus_maximus"), listOf("calves", "gluteus_medius"),
            equipment = setOf("box"),
            difficulty = "foundation",
            technique = listOf(
                "Start with one foot atop a low sturdy box and drive vertically through that heel.",
                "Switch lead legs smoothly near the top of the jump.",
                "Land softly with torso upright before repeating cleanly."
            ),
            easier = "Alternating fast box toe taps",
            harder = "Weighted dumbbell box switch jumps"
        )
    )

    // -------------------------------------------------------------------------
    // ISOMETRICS (20 total: 9 preserved legacy IDs + 11 new holds)
    // -------------------------------------------------------------------------
    val isometricsRecords: List<ExerciseLibraryRecordV2> = listOf(
        iso(
            "iso-wall-sit", "Wall sit", "Quad endurance",
            listOf("quadriceps"), listOf("gluteus_maximus", "calves"),
            technique = listOf(
                "Press lower and upper back flat against the wall with feet shoulder-width.",
                "Sink thighs toward parallel with shins vertical and weight balanced mid-foot.",
                "Breathe steadily without pushing hands down onto your knees."
            ),
            easier = "Higher knee angle (60 degrees)",
            harder = "Single-leg wall sit"
        ),
        iso(
            "iso-calf-hold", "Single-leg calf isometric hold", "Achilles and soleus",
            listOf("calves"), listOf("foot_stabilizers", "tibialis_anterior"),
            technique = listOf(
                "Rise cleanly onto the ball of one foot with light fingertip wall support.",
                "Hold heel above neutral while keeping weight evenly across big and little toe.",
                "Keep knee slightly soft or straight depending on soleus vs gastroc focus."
            ),
            easier = "Two-leg calf raise isometric hold",
            harder = "Bent-knee single-leg heavy calf hold"
        ),
        iso(
            "iso-split-squat-hold", "Split squat isometric hold", "Single-leg stability",
            listOf("quadriceps", "gluteus_maximus"), listOf("gluteus_medius", "trunk_stabilizers"),
            technique = listOf(
                "Hover the back knee a few centimeters above the floor with vertical torso.",
                "Drive the front heel gently into the floor to recruit glute and quad tension.",
                "Keep front knee tracking cleanly over the middle toes."
            ),
            easier = "Higher quarter-depth split squat hold",
            harder = "Front-foot elevated weighted split squat hold"
        ),
        iso(
            "iso-glute-bridge-hold", "Glute bridge isometric hold", "Posterior chain",
            listOf("gluteus_maximus"), listOf("hamstrings", "spinal_extensors"),
            technique = listOf(
                "Tuck pelvis slightly at lockout so ribs stay stacked instead of flaring.",
                "Drive evenly through both heels and squeeze glutes firmly.",
                "Avoid overarching the lumbar spine at the top."
            ),
            easier = "Short 15-second bilateral hold",
            harder = "Single-leg glute bridge hold"
        ),
        iso(
            "iso-forearm-plank", "Forearm plank", "Core bracing",
            listOf("abdominals", "trunk_stabilizers"), listOf("shoulder_stabilizers", "gluteus_maximus"),
            technique = listOf(
                "Press forearms actively into the floor to keep shoulder blades protracted.",
                "Squeeze glutes and quads to form one crisp line from ears to heels.",
                "Exhale long and steady without letting hips sag."
            ),
            easier = "Elevated hands incline plank",
            harder = "Long-lever forearm plank"
        ),
        iso(
            "iso-side-plank", "Side plank", "Lateral trunk",
            listOf("obliques", "lateral_trunk_muscles"), listOf("gluteus_medius", "shoulder_stabilizers"),
            technique = listOf(
                "Stack elbow directly beneath shoulder and lift hips into a straight side line.",
                "Pull top shoulder slightly back so torso does not rotate toward the floor.",
                "Push the bottom foot or knee firmly down to engage lateral hip stability."
            ),
            easier = "Kneeling bent-knee side plank",
            harder = "Side plank with top leg raise"
        ),
        iso(
            "iso-dead-hang", "Dead hang", "Grip and shoulder",
            listOf("forearm_grip", "latissimus_dorsi"), listOf("shoulder_stabilizers", "lower_trapezius"),
            equipment = setOf("pullup_bar"),
            technique = listOf(
                "Wrap thumb cleanly around the bar at shoulder-width or slightly wider.",
                "Allow lats and ribcage to decompress while keeping shoulders organized.",
                "Step down smoothly before grip fails suddenly."
            ),
            easier = "Foot-supported partial-weight hang",
            harder = "Single-arm alternating bar hang"
        ),
        iso(
            "iso-hamstring-hold", "Hamstring bridge isometric hold", "Hamstring tendon",
            listOf("hamstrings"), listOf("gluteus_maximus", "calves"),
            technique = listOf(
                "Place heels further forward with knees bent around 20 to 30 degrees.",
                "Lift hips slightly off the ground by driving heels down and back into the floor.",
                "Keep lumbar spine neutral and focus tension through the mid-hamstring."
            ),
            easier = "Closer-heel glute-ham bridge hold",
            harder = "Single-leg long-lever hamstring bridge hold"
        ),
        iso(
            "iso-hollow-hold", "Hollow body hold", "Anterior core",
            listOf("abdominals"), listOf("hip_flexors", "trunk_stabilizers"),
            technique = listOf(
                "Press the lower back firmly into the floor with zero daylight underneath.",
                "Reach legs long and ribs down while keeping shoulders lifted off the mat.",
                "Bend knees or bring arms forward at any moment if lower back arches."
            ),
            easier = "Tucked hollow hold with hands by hips",
            harder = "Hollow body rocks"
        ),
        iso(
            "iso-copenhagen-plank", "Copenhagen plank hold", "Adductor and lateral core",
            listOf("adductors", "obliques"), listOf("lateral_trunk_muscles", "trunk_stabilizers"),
            equipment = setOf("bench"),
            difficulty = "developing",
            technique = listOf(
                "Support the inside of your top knee or ankle on a sturdy bench.",
                "Lift hips into straight alignment and press down through the bench leg.",
                "Keep pelvis square forward without rolling backward."
            ),
            easier = "Short-lever knee-supported Copenhagen plank",
            harder = "Long-lever ankle-supported Copenhagen plank"
        ),
        iso(
            "iso-horse-stance", "Horse stance hold", "Hip opening and quad isometric",
            listOf("quadriceps", "adductors"), listOf("gluteus_medius", "trunk_stabilizers"),
            difficulty = "developing",
            technique = listOf(
                "Take a wide stance with feet angled outward slightly and torso tall.",
                "Sink hips downward while pushing knees steadily outward over toes.",
                "Keep spine upright and breathe through the deep adductor stretch."
            ),
            easier = "Narrower sumo squat hold",
            harder = "Dumbbell goblet horse stance hold"
        ),
        iso(
            "iso-lunge-bottom-hold", "Overhead lunge isometric hold", "Postural lower endurance",
            listOf("quadriceps", "gluteus_maximus"), listOf("anterior_deltoid", "trunk_stabilizers"),
            equipment = setOf("dumbbell"),
            difficulty = "developing",
            technique = listOf(
                "Hold dumbbells locked cleanly overhead with ribs tucked over hips.",
                "Drop into a steady split lunge without touching the back knee down.",
                "Keep gaze straight ahead and breathe behind active abdominal bracing."
            ),
            easier = "Bodyweight lunge isometric hold",
            harder = "Heavier overhead or front-rack lunge hold"
        ),
        iso(
            "iso-nordic-hold", "Nordic hamstring isometric hold", "Eccentric hamstring strength",
            listOf("hamstrings"), listOf("gluteus_maximus", "calves"),
            equipment = setOf("pad"),
            difficulty = "advanced",
            technique = listOf(
                "Anchor ankles securely and keep a straight line from knees to head.",
                "Hinge forward cleanly at the knees until hamstring tension peaks, then hold.",
                "Catch lightly with hands before posture breaks at the hips."
            ),
            easier = "Band-assisted Nordic hold",
            harder = "Deeper lean angle without support"
        ),
        iso(
            "iso-barbell-iso-pull", "Mid-thigh isometric pull", "Maximal overcoming force",
            listOf("hamstrings", "gluteus_maximus"), listOf("spinal_extensors", "forearm_grip", "trapezius"),
            equipment = setOf("barbell"),
            difficulty = "advanced",
            technique = listOf(
                "Set bar against immovable safety pins at mid-thigh power position.",
                "Brace spine neutral, set shoulders back, and build tension progressively.",
                "Drive feet through the floor for 3 to 5 second maximal efforts."
            ),
            easier = "Submaximal 70 percent effort pulls",
            harder = "Maximal 5-second overcoming pins pull"
        ),
        iso(
            "iso-pushup-bottom-hold", "Push-up bottom isometric hold", "Pec and triceps stability",
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "trunk_stabilizers"),
            technique = listOf(
                "Lower until chest hovers 2 to 3 cm above the floor with elbows angled 45 degrees.",
                "Pull shoulder blades down away from ears and hold body rigid.",
                "Keep glutes and quads active so hips never dip below chest."
            ),
            easier = "Incline push-up bottom hold",
            harder = "Deficit push-up bottom hold"
        ),
        iso(
            "iso-flexed-arm-hang", "Flexed-arm chin-up hang", "Pulling top hold",
            listOf("elbow_flexors", "latissimus_dorsi"), listOf("forearm_grip", "rhomboids"),
            equipment = setOf("pullup_bar"),
            difficulty = "developing",
            technique = listOf(
                "Use a box to start with chin clearly above a supinated grip bar.",
                "Pull shoulder blades down and back, keeping elbows close to ribs.",
                "Hold steady without shrugging shoulders toward ears."
            ),
            easier = "Band-assisted top chin-up hold",
            harder = "90-degree bent-arm isometric hang"
        ),
        iso(
            "iso-ring-support-hold", "Ring support isometric hold", "Straight-arm shoulder stability",
            listOf("triceps", "shoulder_stabilizers"), listOf("pectoralis_major", "abdominals"),
            equipment = setOf("rings"),
            difficulty = "developing",
            technique = listOf(
                "Lock elbows straight and press shoulders firmly downward away from ears.",
                "Keep rings tucked smoothly beside hips without excessive shake.",
                "Turn rings slightly outward only once basic support feels rock-solid."
            ),
            easier = "Toe-assisted ring support",
            harder = "Rings turned out (RTO) support hold"
        ),
        iso(
            "iso-superman-hold", "Superman arch isometric hold", "Posterior chain extension",
            listOf("spinal_extensors", "gluteus_maximus"), listOf("posterior_deltoid", "hamstrings"),
            technique = listOf(
                "Lie prone and squeeze glutes before lifting chest and thighs off the mat.",
                "Keep neck neutral looking down at the mat rather than craning upward.",
                "Reach hands long overhead or in a W-shape for mid-back focus."
            ),
            easier = "Alternating opposite arm and leg arch hold",
            harder = "Weighted plate hold or arch rocks"
        ),
        iso(
            "iso-reverse-plank", "Reverse plank hold", "Posterior chain and shoulder",
            listOf("gluteus_maximus", "hamstrings"), listOf("posterior_deltoid", "spinal_extensors"),
            technique = listOf(
                "Place hands under shoulders with fingers angled out slightly for wrist comfort.",
                "Press chest open and squeeze glutes to lift hips into a straight line.",
                "Avoid hyperextending elbows or letting hips sink."
            ),
            easier = "Bent-knee tabletop bridge hold",
            harder = "Single-leg reverse plank hold"
        ),
        iso(
            "iso-farmer-hold", "Standing farmer isometric hold", "Grip and postural bracing",
            listOf("forearm_grip", "trapezius"), listOf("trunk_stabilizers", "quadriceps"),
            equipment = setOf("dumbbell"),
            difficulty = "developing",
            technique = listOf(
                "Stand tall holding heavy dumbbells or kettlebells quietly at your sides.",
                "Keep shoulder blades lightly packed and ribcage stacked over pelvis.",
                "Breathe calmly through your nose while fighting side-to-side sway."
            ),
            easier = "Lighter load for 20 seconds",
            harder = "Single-arm suitcase isometric hold"
        )
    )

    // -------------------------------------------------------------------------
    // STRENGTH GYM (65 new exercises covering every pattern + lateral_deltoid)
    // -------------------------------------------------------------------------
    val strengthRecords: List<ExerciseLibraryRecordV2> = listOf(
        // Lower Push / Squat & Knee Dominant
        strength(
            "str-front-squat", "Barbell front squat", "Lower push · Squat",
            listOf("quadriceps", "gluteus_maximus"), listOf("trunk_stabilizers", "spinal_extensors"),
            equipment = setOf("barbell", "rack"),
            technique = listOf(
                "Rest the bar high across anterior delts with elbows driven high.",
                "Maintain an upright torso while sitting between your heels.",
                "Keep elbows lifted out of the bottom so upper back stays rigid."
            ),
            easier = "Goblet squat",
            harder = "Paused barbell front squat"
        ),
        strength(
            "str-hack-squat", "Machine hack squat", "Lower push · Squat",
            listOf("quadriceps"), listOf("gluteus_maximus", "calves"),
            equipment = setOf("machine"),
            difficulty = "foundation",
            technique = listOf(
                "Keep shoulders and lower back flush against the sled pad throughout.",
                "Lower deeply until hamstrings meet calves without lifting heels.",
                "Drive smoothly through midfoot while tracking knees over toes."
            ),
            easier = "Seated machine leg press",
            harder = "3-second eccentric hack squat"
        ),
        strength(
            "str-leg-extension", "Seated leg extension", "Lower push · Knee isolation",
            listOf("quadriceps"), listOf("hip_flexors"),
            equipment = setOf("machine"),
            difficulty = "foundation",
            technique = listOf(
                "Align knee joint directly with the machine pivot pin.",
                "Extend smoothly to full lockout with a brief squeeze at the top.",
                "Control the lowering phase over 2 seconds without letting weight slam."
            ),
            easier = "Lighter load or partial range",
            harder = "Single-leg extension"
        ),
        strength(
            "str-zercher-squat", "Zercher squat", "Lower push · Anterior load",
            listOf("quadriceps", "gluteus_maximus"), listOf("elbow_flexors", "trunk_stabilizers", "trapezius"),
            equipment = setOf("barbell"),
            difficulty = "advanced",
            technique = listOf(
                "Cradle the bar snugly in the crook of bent elbows against your upper torso.",
                "Pull shoulder blades tight and drop hips straight down between knees.",
                "Drive feet firmly into the floor to stand tall without leaning forward."
            ),
            easier = "Dual dumbbell front squat",
            harder = "Dead-stop Zercher squat from pins"
        ),
        strength(
            "str-walking-lunge", "Dumbbell walking lunge", "Lower push · Unilateral",
            listOf("quadriceps", "gluteus_maximus"), listOf("gluteus_medius", "hamstrings"),
            equipment = setOf("dumbbell"),
            difficulty = "foundation",
            technique = listOf(
                "Take a controlled stride long enough for front shin to stay near vertical.",
                "Tap or hover the back knee softly before driving cleanly through front foot.",
                "Keep hips level and avoid stepping onto a tightrope line."
            ),
            easier = "Bodyweight reverse lunge",
            harder = "Barbell walking lunge"
        ),
        strength(
            "str-step-up", "Weighted box step-up", "Lower push · Unilateral",
            listOf("quadriceps", "gluteus_maximus"), listOf("gluteus_medius", "calves"),
            equipment = setOf("dumbbell", "box"),
            difficulty = "foundation",
            technique = listOf(
                "Plant the full working foot squarely on the box before initiating drive.",
                "Press through the elevated heel without pushing off the trailing toe.",
                "Lower slowly for 2 to 3 seconds under full single-leg control."
            ),
            easier = "Low box bodyweight step-up",
            harder = "Front-rack barbell step-up"
        ),
        strength(
            "str-pendulum-squat", "Pendulum squat", "Lower push · Squat",
            listOf("quadriceps", "gluteus_maximus"), listOf("adductors", "calves"),
            equipment = setOf("machine"),
            technique = listOf(
                "Position feet low-to-mid on the footplate for maximal knee flexion.",
                "Lower into full depth while keeping lower back pinned to the pad.",
                "Drive cleanly through full foot without bouncing at bottom."
            ),
            easier = "Machine hack squat",
            harder = "Paused bottom pendulum squat"
        ),

        // Lower Hinge / Posterior Chain
        strength(
            "str-conv-deadlift", "Conventional barbell deadlift", "Lower hinge · Bilateral",
            listOf("hamstrings", "gluteus_maximus", "spinal_extensors"), listOf("quadriceps", "forearm_grip", "latissimus_dorsi"),
            equipment = setOf("barbell"),
            technique = listOf(
                "Stand with bar over midfoot and pull slack out of the bar before liftoff.",
                "Brace trunk rigid and push the floor away smoothly while bar hugs shins.",
                "Stand tall by driving hips forward without leaning back excessively."
            ),
            easier = "Romanian deadlift",
            harder = "Deficit conventional deadlift"
        ),
        strength(
            "str-sumo-deadlift", "Sumo barbell deadlift", "Lower hinge · Wide stance",
            listOf("gluteus_maximus", "adductors", "quadriceps"), listOf("hamstrings", "spinal_extensors", "forearm_grip"),
            equipment = setOf("barbell"),
            technique = listOf(
                "Take a wide stance with toes angled out and shins vertical against the bar.",
                "Wedge hips close to the bar with chest tall and shoulders directly over bar.",
                "Spread the floor apart with your feet as you drive up smoothly."
            ),
            easier = "Kettlebell sumo deadlift",
            harder = "Paused sumo deadlift"
        ),
        strength(
            "str-trap-bar-deadlift", "Trap bar deadlift", "Lower hinge · Hybrid squat-hinge",
            listOf("gluteus_maximus", "quadriceps", "hamstrings"), listOf("spinal_extensors", "forearm_grip", "trapezius"),
            equipment = setOf("barbell"),
            difficulty = "foundation",
            technique = listOf(
                "Center feet inside the hex bar and grip the handles at midfoot alignment.",
                "Set spine long, lock lats down, and push straight through the floor.",
                "Control the descent to a clean tap between each rep."
            ),
            easier = "High-handle trap bar deadlift",
            harder = "Low-handle trap bar deadlift"
        ),
        strength(
            "str-barbell-hip-thrust", "Barbell hip thrust", "Lower hinge · Glute drive",
            listOf("gluteus_maximus"), listOf("hamstrings", "quadriceps"),
            equipment = setOf("barbell", "bench"),
            difficulty = "foundation",
            technique = listOf(
                "Rest mid-shoulder blades against the bench edge with shins vertical at lockout.",
                "Keep chin slightly tucked and ribs down as you drive hips to full extension.",
                "Pause 1 second at top lockout without hyperextending lower back."
            ),
            easier = "Dumbbell floor glute bridge",
            harder = "Single-leg hip thrust"
        ),
        strength(
            "str-single-leg-rdl", "Dumbbell single-leg RDL", "Lower hinge · Unilateral",
            listOf("hamstrings", "gluteus_maximus"), listOf("gluteus_medius", "spinal_extensors", "foot_stabilizers"),
            equipment = setOf("dumbbell"),
            technique = listOf(
                "Hinge the hip cleanly backward while keeping pelvis square toward the floor.",
                "Slide weights down near the stance shin with a soft, stable knee bend.",
                "Drive hips forward strongly through the stance heel to return upright."
            ),
            easier = "Kickstand single-leg RDL",
            harder = "Barbell single-leg RDL"
        ),
        strength(
            "str-lying-leg-curl", "Lying machine leg curl", "Lower hinge · Knee flexion",
            listOf("hamstrings"), listOf("calves", "gluteus_maximus"),
            equipment = setOf("machine"),
            difficulty = "foundation",
            technique = listOf(
                "Press hips gently into the bench pad so pelvis does not lift as heels curl.",
                "Curl pad smoothly toward glutes and lower over a steady 2 to 3 seconds.",
                "Keep feet slightly dorsiflexed or neutral for consistent hamstring tension."
            ),
            easier = "Seated machine leg curl",
            harder = "Two-up one-down eccentric lying curl"
        ),
        strength(
            "str-back-extension", "45-degree back extension", "Lower hinge · Posterior chain",
            listOf("spinal_extensors", "gluteus_maximus"), listOf("hamstrings"),
            equipment = setOf("bench"),
            difficulty = "foundation",
            technique = listOf(
                "Set hip pad slightly below pelvis crease so movement comes from the hips.",
                "Hinge forward smoothly with a neutral back and squeeze glutes to rise.",
                "Stop once torso forms a straight line with your legs."
            ),
            easier = "Bodyweight 45-degree back extension",
            harder = "Barbell or plate-loaded back extension"
        ),
        strength(
            "str-good-morning", "Barbell good morning", "Lower hinge · Long lever",
            listOf("hamstrings", "spinal_extensors"), listOf("gluteus_maximus", "trunk_stabilizers"),
            equipment = setOf("barbell", "rack"),
            difficulty = "advanced",
            technique = listOf(
                "Set bar snugly on upper back like a squat and unlock knees slightly.",
                "Push hips straight back with a braced spine until torso nears horizontal.",
                "Drive hips forcefully forward to return upright."
            ),
            easier = "Banded good morning",
            harder = "Seated barbell good morning"
        ),
        strength(
            "str-kb-swing", "Kettlebell swing", "Lower hinge · Ballistic",
            listOf("gluteus_maximus", "hamstrings"), listOf("spinal_extensors", "forearm_grip", "trunk_stabilizers"),
            equipment = setOf("kettlebell"),
            difficulty = "foundation",
            technique = listOf(
                "Hike kettlebell high between thighs while maintaining a flat back.",
                "Snap hips explosively upright so the bell floats to chest height on momentum.",
                "Wait until arms reconnect with ribs before hinging back again."
            ),
            easier = "Kettlebell deadlift",
            harder = "Single-arm kettlebell swing"
        ),
        strength(
            "str-copenhagen-adduction", "Weighted hip adduction", "Lower hip · Adductor",
            listOf("adductors"), listOf("hip_flexors", "obliques"),
            equipment = setOf("machine"),
            difficulty = "foundation",
            technique = listOf(
                "Sit upright and squeeze pads smoothly together using inner thighs.",
                "Control the eccentric return slowly to strengthen groin durability.",
                "Keep range comfortable without forcing past hip tightness."
            ),
            easier = "Seated ball squeeze",
            harder = "Copenhagen plank hold"
        ),
        strength(
            "str-hip-abduction", "Seated hip abduction machine", "Lower hip · Lateral stability",
            listOf("gluteus_medius"), listOf("gluteus_maximus"),
            equipment = setOf("machine"),
            difficulty = "foundation",
            technique = listOf(
                "Anchor pelvis against the backrest without rocking torso forward.",
                "Press knees smoothly outward against the pads and hold 1 second.",
                "Return with controlled tension without letting weight stack touch."
            ),
            easier = "Banded monster walk",
            harder = "Single-leg cable hip abduction"
        ),
        strength(
            "str-standing-calf-raise", "Standing machine calf raise", "Lower ankle · Gastrocnemius",
            listOf("calves"), listOf("foot_stabilizers"),
            equipment = setOf("machine"),
            difficulty = "foundation",
            technique = listOf(
                "Pause briefly in the stretched bottom position to take bounce out of the Achilles.",
                "Drive tall onto the big-toe mound with straight knees.",
                "Lower slowly for 2 to 3 seconds through full ankle range."
            ),
            easier = "Supported calf raise",
            harder = "Single-leg standing dumbbell calf raise"
        ),
        strength(
            "str-seated-calf-raise", "Seated calf raise", "Lower ankle · Soleus focus",
            listOf("calves"), listOf("foot_stabilizers"),
            equipment = setOf("machine"),
            difficulty = "foundation",
            technique = listOf(
                "Keep knees bent near 90 degrees to isolate the deeper soleus muscle.",
                "Lower heels cleanly into a controlled stretch before pushing up.",
                "Avoid bouncing quickly through the bottom turnaround."
            ),
            easier = "Single-dumbbell seated knee calf raise",
            harder = "Heavy paused seated calf raise"
        ),
        strength(
            "str-tibialis-raise", "Tibialis bar raise", "Lower shin · Deceleration prep",
            listOf("tibialis_anterior"), listOf("foot_stabilizers"),
            equipment = setOf("dumbbell"),
            difficulty = "foundation",
            technique = listOf(
                "Support calves cleanly off a bench edge with heels resting forward.",
                "Pull toes aggressively toward shins and squeeze for 1 second.",
                "Lower under control until foot stretches smoothly forward."
            ),
            easier = "Standing wall-lean tibialis raises",
            harder = "Weighted tib bar single-leg raise"
        ),

        // Upper Horizontal Push
        strength(
            "str-bb-bench-press", "Barbell bench press", "Upper horizontal push",
            listOf("pectoralis_major"), listOf("triceps", "anterior_deltoid"),
            equipment = setOf("barbell", "bench", "rack"),
            difficulty = "foundation",
            technique = listOf(
                "Set shoulder blades down and back against the bench with steady foot drive.",
                "Lower the bar smoothly to mid-lower chest with elbows roughly 45 degrees.",
                "Press cleanly upward and slightly back toward the rack."
            ),
            easier = "Dumbbell bench press",
            harder = "Paused barbell bench press"
        ),
        strength(
            "str-incline-bb-press", "Incline barbell press", "Upper incline push",
            listOf("pectoralis_major", "anterior_deltoid"), listOf("triceps"),
            equipment = setOf("barbell", "bench", "rack"),
            technique = listOf(
                "Set bench to 30 to 45 degrees and anchor upper back firmly.",
                "Touch the bar cleanly near upper chest / collarbone line.",
                "Keep elbows stacked beneath wrists throughout the press."
            ),
            easier = "Incline dumbbell press",
            harder = "Paused incline barbell press"
        ),
        strength(
            "str-incline-db-press", "Incline dumbbell press", "Upper incline push",
            listOf("pectoralis_major", "anterior_deltoid"), listOf("triceps"),
            equipment = setOf("dumbbell", "bench"),
            difficulty = "foundation",
            technique = listOf(
                "Drive feet gently into floor and lower weights with controlled stretch at chest.",
                "Press dumbbells up along a slight arc without clanging them together.",
                "Maintain steady scapular retraction on the bench pad."
            ),
            easier = "Dumbbell floor press",
            harder = "Alternating incline dumbbell press"
        ),
        strength(
            "str-cable-chest-fly", "Standing cable chest fly", "Upper horizontal adduction",
            listOf("pectoralis_major"), listOf("anterior_deltoid", "serratus_anterior"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Take a staggered stance and soften elbows at a fixed 15 to 20 degree bend.",
                "Sweep arms smoothly forward in a hugging arc until hands meet cleanly.",
                "Control the stretch backward without letting shoulders roll forward."
            ),
            easier = "Seated pec deck fly",
            harder = "Single-arm cable fly with staggered anti-rotation"
        ),
        strength(
            "str-pec-deck", "Machine chest fly", "Upper horizontal isolation",
            listOf("pectoralis_major"), listOf("anterior_deltoid"),
            equipment = setOf("machine"),
            difficulty = "foundation",
            technique = listOf(
                "Set seat height so handles align comfortably with mid-chest.",
                "Keep shoulder blades back and bring forearms smoothly together.",
                "Lower weight slowly under tension into a gentle chest stretch."
            ),
            easier = "Lighter load machine fly",
            harder = "3-second eccentric machine chest fly"
        ),
        strength(
            "str-weighted-dip", "Weighted chest dip", "Upper downward push",
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "serratus_anterior"),
            equipment = setOf("belt", "parallel_bars"),
            difficulty = "advanced",
            technique = listOf(
                "Lean torso gently forward while keeping shoulders depressed away from ears.",
                "Lower until upper arms approach parallel to the floor.",
                "Press back to locked support smoothly without swinging load."
            ),
            easier = "Parallel-bar dip",
            harder = "Heavy paused weighted dip"
        ),

        // Upper Vertical Push & Deltoids (Accurate lateral_deltoid!)
        strength(
            "str-overhead-press", "Barbell overhead press", "Upper vertical push",
            listOf("anterior_deltoid", "lateral_deltoid"), listOf("triceps", "trapezius", "trunk_stabilizers"),
            equipment = setOf("barbell"),
            technique = listOf(
                "Squeeze glutes and brace abs so lower back never arches during liftoff.",
                "Press bar vertically past chin and push head smoothly through at lockout.",
                "Lower elbows slightly forward back to upper-chest rack position."
            ),
            easier = "Seated dumbbell shoulder press",
            harder = "Barbell push press"
        ),
        strength(
            "str-db-shoulder-press", "Seated dumbbell shoulder press", "Upper vertical push",
            listOf("anterior_deltoid", "lateral_deltoid"), listOf("triceps", "trapezius"),
            equipment = setOf("dumbbell", "bench"),
            difficulty = "foundation",
            technique = listOf(
                "Set elbows slightly in front of the torso plane (scapular plane) for joint comfort.",
                "Press dumbbells overhead cleanly until biceps align beside ears.",
                "Lower smoothly to ear level without bouncing off the bottom."
            ),
            easier = "Single-arm half-kneeling DB press",
            harder = "Standing dumbbell overhead press"
        ),
        strength(
            "str-arnold-press", "Arnold dumbbell press", "Upper vertical push and rotation",
            listOf("anterior_deltoid", "lateral_deltoid"), listOf("triceps", "shoulder_stabilizers"),
            equipment = setOf("dumbbell"),
            technique = listOf(
                "Start with palms facing torso in front of shoulders.",
                "Rotate smoothly outward as you press overhead into full vertical lockout.",
                "Reverse the spiral cleanly back to the front rack."
            ),
            easier = "Seated dumbbell shoulder press",
            harder = "Standing single-arm Arnold press"
        ),
        strength(
            "str-db-lateral-raise", "Dumbbell lateral raise", "Shoulder abduction",
            listOf("lateral_deltoid"), listOf("trapezius", "shoulder_stabilizers"),
            equipment = setOf("dumbbell"),
            difficulty = "foundation",
            technique = listOf(
                "Raise dumbbells smoothly along a 30-degree forward scapular arc.",
                "Lead with elbows and wrists level rather than shrugging upper traps.",
                "Lower slowly for 2 seconds without swinging hips."
            ),
            easier = "Seated strict lateral raise",
            harder = "Lean-away cable lateral raise"
        ),
        strength(
            "str-cable-lateral-raise", "Cable lateral raise", "Constant-tension shoulder abduction",
            listOf("lateral_deltoid"), listOf("shoulder_stabilizers", "trapezius"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Set pulley near wrist height and let arm cross gently in front or behind hip.",
                "Sweep arm out wide to shoulder height while keeping traps relaxed.",
                "Control return smoothly to maintain bottom-range tension."
            ),
            easier = "Band lateral raise",
            harder = "Behind-the-back single-arm cable lateral raise"
        ),
        strength(
            "str-upright-row", "Cable upright row", "Vertical pull and abduction",
            listOf("lateral_deltoid", "trapezius"), listOf("elbow_flexors", "anterior_deltoid"),
            equipment = setOf("cable"),
            technique = listOf(
                "Use a shoulder-width rope or bar grip to keep wrists comfortable.",
                "Pull elbows outward and upward to chest height rather than yanking hands high.",
                "Lower smoothly without pitching torso backward."
            ),
            easier = "Dumbbell lateral raise",
            harder = "EZ-bar wide upright row"
        ),

        // Upper Horizontal Pull
        strength(
            "str-bb-row", "Barbell bent-over row", "Upper horizontal pull",
            listOf("latissimus_dorsi", "rhomboids"), listOf("posterior_deltoid", "elbow_flexors", "spinal_extensors"),
            equipment = setOf("barbell"),
            technique = listOf(
                "Hinge torso cleanly between 30 and 45 degrees with flat lumbar spine.",
                "Pull bar toward lower ribs while squeezing shoulder blades together.",
                "Lower bar smoothly to arm hang without losing hip hinge angle."
            ),
            easier = "Chest-supported dumbbell row",
            harder = "Pendlay barbell row"
        ),
        strength(
            "str-chest-supported-row", "Chest-supported dumbbell row", "Upper horizontal pull",
            listOf("rhomboids", "latissimus_dorsi"), listOf("posterior_deltoid", "elbow_flexors", "lower_trapezius"),
            equipment = setOf("dumbbell", "bench"),
            difficulty = "foundation",
            technique = listOf(
                "Set bench around 30 to 45 degrees and keep chest pinned to pad.",
                "Draw elbows backward toward hips while keeping upper traps soft.",
                "Allow full scapular protraction reach at the bottom of each rep."
            ),
            easier = "Lighter chest-supported row",
            harder = "Two-second top squeeze chest-supported row"
        ),
        strength(
            "str-seated-cable-row", "Seated cable row", "Upper horizontal pull",
            listOf("latissimus_dorsi", "rhomboids"), listOf("elbow_flexors", "posterior_deltoid"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Sit tall on sit-bones and brace abs before pulling handle to navel.",
                "Finish with shoulders down and back without excessive torso lean back.",
                "Reach forward under control to lengthen lats smoothly."
            ),
            easier = "Band row",
            harder = "Single-arm seated cable row"
        ),
        strength(
            "str-tbar-row", "T-bar row", "Upper horizontal pull",
            listOf("latissimus_dorsi", "rhomboids"), listOf("posterior_deltoid", "trapezius", "elbow_flexors"),
            equipment = setOf("barbell"),
            technique = listOf(
                "Lock hip hinge steady and brace core throughout every rep.",
                "Row handle firmly toward lower sternum and pause briefly at contraction.",
                "Control return smoothly so lower back stays stationary."
            ),
            easier = "Chest-supported machine T-bar row",
            harder = "Strict paused T-bar row"
        ),
        strength(
            "str-face-pull", "Cable face pull", "Upper horizontal external rotation",
            listOf("posterior_deltoid", "rhomboids"), listOf("lower_trapezius", "shoulder_stabilizers"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Pull rope ends apart toward eyebrows with thumbs pointing behind you.",
                "Keep ribs stacked down rather than flaring low back at peak squeeze.",
                "Finish with external shoulder rotation (double biceps pose)."
            ),
            easier = "Banded face pull",
            harder = "Half-kneeling single-arm cable external row"
        ),
        strength(
            "str-rear-delt-fly", "Reverse cable fly", "Rear shoulder horizontal abduction",
            listOf("posterior_deltoid"), listOf("rhomboids", "lower_trapezius"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Keep arms near straight and sweep hands outward in a wide rear arc.",
                "Lead with the back of the hands while keeping upper traps relaxed.",
                "Avoid snapping shoulder blades together violently on lighter sets."
            ),
            easier = "Incline bench dumbbell rear delt fly",
            harder = "Seated reverse pec deck with slow eccentric"
        ),

        // Upper Vertical Pull & Traps
        strength(
            "str-single-arm-pulldown", "Single-arm cable lat pulldown", "Upper unilateral vertical pull",
            listOf("latissimus_dorsi"), listOf("elbow_flexors", "lower_trapezius", "obliques"),
            equipment = setOf("cable"),
            technique = listOf(
                "Pull shoulder down first, then drive elbow cleanly toward your hip pocket.",
                "Keep torso tall with subtle anti-side-bend core control.",
                "Allow full overhead stretch at the top before initiating next pull."
            ),
            easier = "Seated lat pulldown",
            harder = "Heavy half-kneeling single-arm pulldown"
        ),
        strength(
            "str-straight-arm-pulldown", "Straight-arm cable pulldown", "Shoulder extension · Lats",
            listOf("latissimus_dorsi"), listOf("triceps", "serratus_anterior", "trunk_stabilizers"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Take a slight hip hinge with soft locked elbows.",
                "Sweep bar or rope smoothly down to thighs using lats, not triceps.",
                "Control return upward until lats lengthen comfortably overhead."
            ),
            easier = "Banded straight-arm pulldown",
            harder = "Kneeling straight-arm rope pulldown"
        ),
        strength(
            "str-db-pullover", "Dumbbell pullover", "Thoracic and lat extension",
            listOf("latissimus_dorsi", "pectoralis_major"), listOf("serratus_anterior", "triceps"),
            equipment = setOf("dumbbell", "bench"),
            technique = listOf(
                "Brace ribs down firmly so lumbar back does not hyperextend overhead.",
                "Lower dumbbell behind head along an arc until a comfortable lat stretch.",
                "Pull smoothly over chest with lats without turning it into a triceps press."
            ),
            easier = "Straight-arm cable pulldown",
            harder = "Cross-bench dumbbell pullover"
        ),
        strength(
            "str-db-shrug", "Dumbbell shrug", "Scapular elevation",
            listOf("trapezius"), listOf("forearm_grip"),
            equipment = setOf("dumbbell"),
            difficulty = "foundation",
            technique = listOf(
                "Hold weights cleanly at sides and elevate shoulders straight toward ears.",
                "Pause 1 second at top contraction without rolling shoulder joints in circles.",
                "Lower slowly into a full downward stretch."
            ),
            easier = "Lighter controlled shrugs",
            harder = "Barbell or trap-bar heavy shrug"
        ),

        // Arms & Forearms
        strength(
            "str-hammer-curl", "Dumbbell hammer curl", "Elbow flexion · Brachioradialis",
            listOf("elbow_flexors", "forearm_grip"), listOf("anterior_deltoid"),
            equipment = setOf("dumbbell"),
            difficulty = "foundation",
            technique = listOf(
                "Hold dumbbells with neutral palms facing inward throughout the curl.",
                "Pin elbows gently near ribs and curl without swinging hips.",
                "Lower smoothly to full arm extension on every rep."
            ),
            easier = "Alternating hammer curl",
            harder = "Incline bench hammer curl"
        ),
        strength(
            "str-incline-db-curl", "Incline dumbbell curl", "Long-head biceps stretch",
            listOf("elbow_flexors"), listOf("forearm_grip", "anterior_deltoid"),
            equipment = setOf("dumbbell", "bench"),
            technique = listOf(
                "Set bench to 45 to 60 degrees so arms hang behind torso line.",
                "Curl cleanly upward with supinated palms while upper arms stay stationary.",
                "Lower slowly into the stretched bottom position."
            ),
            easier = "Standing dumbbell curl",
            harder = "Paused eccentric incline curl"
        ),
        strength(
            "str-preacher-curl", "EZ-bar preacher curl", "Strict elbow flexion",
            listOf("elbow_flexors"), listOf("forearm_grip"),
            equipment = setOf("barbell", "bench"),
            difficulty = "foundation",
            technique = listOf(
                "Rest back of upper arms flush against the angled pad.",
                "Curl bar smoothly upward while keeping wrists neutral and strong.",
                "Stop just short of passive hyperextension at the bottom."
            ),
            easier = "Single-arm dumbbell preacher curl",
            harder = "Slow 3-second negative preacher curl"
        ),
        strength(
            "str-cable-curl", "Cable biceps curl", "Constant-tension elbow flexion",
            listOf("elbow_flexors"), listOf("forearm_grip"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Stand tall with cable pulley low and elbows tucked cleanly beside waist.",
                "Curl smoothly without letting shoulders pitch forward.",
                "Lower under steady resistance to full bottom extension."
            ),
            easier = "Standing resistance-band curl",
            harder = "Bayesian behind-body cable curl"
        ),
        strength(
            "str-wrist-curl", "Seated forearm wrist curl", "Wrist and grip strength",
            listOf("forearm_grip"), listOf("elbow_flexors"),
            equipment = setOf("dumbbell", "bench"),
            difficulty = "foundation",
            technique = listOf(
                "Rest forearms flat on thighs or bench with wrists hanging free.",
                "Let weight open gently into fingers, then curl hands strongly upward.",
                "Pair with reverse wrist curls for balanced forearm resilience."
            ),
            easier = "Lighter dumbbell wrist curl",
            harder = "Barbell wrist roller or heavy wrist curl"
        ),
        strength(
            "str-triceps-pushdown", "Cable triceps rope pushdown", "Elbow extension",
            listOf("triceps"), listOf("forearm_grip"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Lock upper arms vertically at your sides with chest tall.",
                "Extend elbows cleanly down and slightly spread rope ends at bottom.",
                "Allow forearms to hinge past 90 degrees upward without lifting elbows."
            ),
            easier = "Straight-bar cable pushdown",
            harder = "Single-arm cable pushdown"
        ),
        strength(
            "str-overhead-triceps-ext", "Overhead cable triceps extension", "Long-head triceps extension",
            listOf("triceps"), listOf("shoulder_stabilizers", "trunk_stabilizers"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Step into a split stance facing away from the pulley and brace abs.",
                "Keep upper arms angled forward and up while bending deeply at elbows.",
                "Extend cleanly upward without letting elbows flare wide."
            ),
            easier = "Seated dumbbell two-hand overhead extension",
            harder = "Single-arm overhead cable extension"
        ),
        strength(
            "str-skull-crusher", "EZ-bar skull crusher", "Supine elbow extension",
            listOf("triceps"), listOf("anterior_deltoid", "forearm_grip"),
            equipment = setOf("barbell", "bench"),
            technique = listOf(
                "Angle upper arms slightly back over head to keep triceps tension at lockout.",
                "Lower bar smoothly toward top of head or slightly behind bench edge.",
                "Extend elbows crisply back to start without flaring."
            ),
            easier = "Dumbbell neutral-grip skull crusher",
            harder = "Incline EZ-bar triceps extension"
        ),

        // Loaded Core, Anti-Rotation & Carries
        strength(
            "str-cable-woodchop", "Diagonal cable woodchop", "Rotational core power",
            listOf("obliques", "trunk_rotators"), listOf("anterior_deltoid", "gluteus_maximus"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Rotate through the upper torso and hips smoothly rather than pulling with arms.",
                "Pivot the trailing foot gently while arms stay relatively long.",
                "Return smoothly against cable pull without twisting knees."
            ),
            easier = "Kneeling cable woodchop",
            harder = "Explosive speed woodchop"
        ),
        strength(
            "str-pallof-press", "Standing cable Pallof press", "Anti-rotation core stability",
            listOf("trunk_stabilizers", "obliques"), listOf("shoulder_stabilizers", "gluteus_medius"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Step sideways until cable pulls firmly, holding handle centered at chest.",
                "Press straight out and hold 1 to 2 seconds without letting torso twist.",
                "Keep pelvis square and knees soft throughout both sides."
            ),
            easier = "Half-kneeling Pallof press",
            harder = "Pallof press with overhead raise"
        ),
        strength(
            "str-ab-wheel-rollout", "Ab wheel rollout", "Anti-extension core",
            listOf("abdominals", "trunk_stabilizers"), listOf("latissimus_dorsi", "shoulder_stabilizers"),
            equipment = setOf("ab_wheel"),
            technique = listOf(
                "Tuck pelvis slightly into posterior tilt before rolling forward.",
                "Roll out smoothly while keeping arms long and ribs pulled toward hips.",
                "Pull back with abs and lats before lower back ever dips."
            ),
            easier = "Short-range wall-stop rollout",
            harder = "Standing ab wheel rollout"
        ),
        strength(
            "str-cable-crunch", "Kneeling cable rope crunch", "Weighted spinal flexion",
            listOf("abdominals"), listOf("hip_flexors", "obliques"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Hold rope ends firmly beside temples and fix hips stationary above knees.",
                "Curl ribs downward toward pelvis by flexing spine, not sitting back.",
                "Unfurl smoothly upward into gentle abdominal extension."
            ),
            easier = "Floor weighted crunch",
            harder = "Single-arm or oblique kneeling cable crunch"
        ),
        strength(
            "str-overhead-carry", "Kettlebell overhead carry", "Loaded shoulder and core carry",
            listOf("shoulder_stabilizers", "trunk_stabilizers"), listOf("anterior_deltoid", "lateral_deltoid", "forearm_grip"),
            equipment = setOf("kettlebell"),
            technique = listOf(
                "Lock bell overhead with bicep near ear, palm forward, and ribs stacked down.",
                "Walk smoothly with quiet footsteps and steady nasal breathing.",
                "Lower bell under control to rack before switching hands."
            ),
            easier = "Bottoms-up or lighter overhead carry",
            harder = "Double kettlebell overhead walk"
        ),
        strength(
            "str-front-rack-carry", "Kettlebell front-rack carry", "Anterior core and upper-back brace",
            listOf("trunk_stabilizers", "abdominals"), listOf("rhomboids", "forearm_grip", "anterior_deltoid"),
            equipment = setOf("kettlebell"),
            difficulty = "foundation",
            technique = listOf(
                "Nestle kettlebells cleanly against forearm and bicep with straight wrists.",
                "Keep elbows gently elevated in front of ribs and pelvis tucked neutral.",
                "Walk briskly with zero backward lumbar lean."
            ),
            easier = "Single kettlebell rack walk",
            harder = "Heavy double kettlebell front-rack march"
        ),
        strength(
            "str-dumbbell-snatch", "Single-arm dumbbell snatch", "Explosive triple extension",
            listOf("gluteus_maximus", "hamstrings", "lateral_deltoid"), listOf("trapezius", "quadriceps", "forearm_grip"),
            equipment = setOf("dumbbell"),
            difficulty = "developing",
            technique = listOf(
                "Start with dumbbell centered between feet and hips hinged athletic.",
                "Drive violently through hips and pull elbow high before punching overhead.",
                "Stabilize overhead briefly with stacked ribs before lowering smoothly."
            ),
            easier = "Dumbbell hang high pull",
            harder = "Heavy alternating dumbbell snatch"
        ),
        strength(
            "str-bulgarian-barbell", "Machine belt squat", "Spine-free quad load",
            listOf("quadriceps", "gluteus_maximus"), listOf("adductors", "calves"),
            equipment = setOf("machine"),
            difficulty = "foundation",
            technique = listOf(
                "Center belt snug over iliac crests and stand tall before unlatching.",
                "Sit straight down between knees with upright torso.",
                "Drive evenly through whole feet to smooth lockout."
            ),
            easier = "Goblet squat",
            harder = "Paused belt squat"
        ),
        strength(
            "str-close-grip-bench", "Close-grip barbell bench press", "Horizontal push · Triceps focus",
            listOf("triceps", "pectoralis_major"), listOf("anterior_deltoid"),
            equipment = setOf("barbell", "bench", "rack"),
            technique = listOf(
                "Grip just inside shoulder width so wrists stay neutral without pinching.",
                "Keep elbows tucked closer to ribs during descent to lower sternum.",
                "Drive smoothly straight up to lockout."
            ),
            easier = "Close-grip push-up",
            harder = "Paused close-grip bench press"
        ),
        strength(
            "str-landmine-press", "Half-kneeling landmine press", "Acromion-friendly overhead press",
            listOf("anterior_deltoid", "serratus_anterior"), listOf("pectoralis_major", "triceps", "trunk_stabilizers"),
            equipment = setOf("barbell"),
            difficulty = "foundation",
            technique = listOf(
                "Kneel on the pressing-side knee with glute squeezed and ribs stacked.",
                "Press the sleeve upward along its natural arc until shoulder blade glides upward.",
                "Lower smoothly back toward shoulder without leaning backward."
            ),
            easier = "Two-hand kneeling landmine press",
            harder = "Standing single-arm landmine press"
        ),
        strength(
            "str-hanging-knee-raise", "Hanging bent-knee raise", "Hip flexor and anterior core",
            listOf("abdominals", "hip_flexors"), listOf("forearm_grip", "latissimus_dorsi"),
            equipment = setOf("pullup_bar"),
            difficulty = "foundation",
            technique = listOf(
                "Hang still from bar without swinging body momentum.",
                "Curl tailbone and pelvis upward so knees tuck cleanly above hip height.",
                "Lower slowly to full hang before initiating next rep."
            ),
            easier = "Floor bent-knee leg raise",
            harder = "Hanging leg raise"
        ),
        strength(
            "str-weighted-plank", "Weighted forearm plank", "Loaded anti-extension",
            listOf("abdominals", "trunk_stabilizers"), listOf("shoulder_stabilizers", "gluteus_maximus"),
            equipment = setOf("plate"),
            difficulty = "developing",
            technique = listOf(
                "Position weight plate smoothly across mid-back / shoulder blades, not lumbar curve.",
                "Press forearms hard into floor and lock glutes tight.",
                "Maintain crisp spinal neutrality for entire hold duration."
            ),
            easier = "Bodyweight forearm plank",
            harder = "Heavy loaded long-lever plank"
        ),
        strength(
            "str-cable-glute-kickback", "Cable glute kickback", "Isolated hip extension",
            listOf("gluteus_maximus"), listOf("hamstrings", "gluteus_medius"),
            equipment = setOf("cable"),
            difficulty = "foundation",
            technique = listOf(
                "Square hips toward the cable stack with soft stance knee.",
                "Sweep working leg smoothly back and slightly outward without arching lumbar back.",
                "Pause 1 second at peak hip extension before controlled return."
            ),
            easier = "Quadruped donkey kickback",
            harder = "Heavy slow-tempo cable kickback"
        ),
        strength(
            "str-glute-ham-raise", "Glute-ham raise", "Combined hip and knee posterior chain",
            listOf("hamstrings", "gluteus_maximus"), listOf("calves", "spinal_extensors"),
            equipment = setOf("bench"),
            difficulty = "advanced",
            technique = listOf(
                "Adjust footplate so knees sit just behind the main roller pad.",
                "Extend torso down with straight hips, then pull body up by flexing knees.",
                "Keep ribs and pelvis aligned throughout ascent."
            ),
            easier = "Band-assisted glute-ham raise",
            harder = "Weighted plate glute-ham raise"
        )
    )

    // -------------------------------------------------------------------------
    // CALISTHENICS (30 structured bodyweight progressions & skills)
    // -------------------------------------------------------------------------
    val calisthenicsRecords: List<ExerciseLibraryRecordV2> = listOf(
        calisthenics(
            "cal-diamond-pushup", "Diamond push-up", "Bodyweight push · Triceps emphasis",
            listOf("triceps", "pectoralis_major"), listOf("anterior_deltoid", "trunk_stabilizers"),
            technique = listOf(
                "Bring thumbs and index fingers together beneath upper sternum.",
                "Keep elbows angled smoothly backward rather than flaring straight sideways.",
                "Press body as one rigid plank to full elbow lockout."
            ),
            easier = "Close-grip knee push-up",
            harder = "Feet-elevated diamond push-up"
        ),
        calisthenics(
            "cal-decline-pushup", "Feet-elevated decline push-up", "Bodyweight upper chest push",
            listOf("pectoralis_major", "anterior_deltoid"), listOf("triceps", "serratus_anterior"),
            equipment = setOf("bench"),
            technique = listOf(
                "Elevate toes onto a sturdy bench while bracing glutes and core tight.",
                "Lower chest cleanly between hands without sagging lower back.",
                "Finish every rep with full scapular protraction at top."
            ),
            easier = "Flat standard push-up progression",
            harder = "Pike push-up"
        ),
        calisthenics(
            "cal-archer-pushup", "Archer push-up", "Unilateral bodyweight push",
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "shoulder_stabilizers"),
            difficulty = "advanced",
            technique = listOf(
                "Place hands wide with straight assisting arm extended sideways.",
                "Shift chest smoothly over one bent pressing arm while keeping hips square.",
                "Alternate left and right cleanly under full eccentric control."
            ),
            easier = "Incline archer push-up",
            harder = "Single-arm straddle push-up"
        ),
        calisthenics(
            "cal-pike-pushup", "Pike push-up", "Bodyweight vertical push",
            listOf("anterior_deltoid", "lateral_deltoid"), listOf("triceps", "serratus_anterior", "trapezius"),
            technique = listOf(
                "Fold hips high into an inverted V with hands shoulder-width apart.",
                "Shift shoulders slightly forward so crown of head tracks ahead of hands.",
                "Press smoothly back to stacked overhead shoulders."
            ),
            easier = "High-hip bent-knee pike push-up",
            harder = "Elevated-feet pike push-up"
        ),
        calisthenics(
            "cal-pseudo-planche-pushup", "Pseudo-planche push-up", "Protracted lean push",
            listOf("anterior_deltoid", "pectoralis_major"), listOf("triceps", "serratus_anterior", "abdominals"),
            difficulty = "advanced",
            technique = listOf(
                "Turn fingers out 45 degrees and lean shoulders clearly ahead of wrists.",
                "Maintain forward lean throughout both the descent and press.",
                "Hollow spine and round upper back gently at lockout."
            ),
            easier = "Planche lean hold",
            harder = "Elevated pseudo-planche push-up"
        ),
        calisthenics(
            "cal-hspu-wall", "Wall handstand push-up", "Advanced vertical bodyweight press",
            listOf("anterior_deltoid", "lateral_deltoid", "triceps"), listOf("trapezius", "serratus_anterior", "trunk_stabilizers"),
            difficulty = "advanced",
            technique = listOf(
                "Kick cleanly into tight wall handstand with ribs knitted and glutes active.",
                "Lower head softly to tripod position slightly ahead of palms.",
                "Press vertically back to full stacked shoulder extension."
            ),
            easier = "Elevated-feet box pike push-up",
            harder = "Deficit wall handstand push-up"
        ),
        calisthenics(
            "cal-ring-pushup", "Gymnastic ring push-up", "Unstable horizontal push",
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "serratus_anterior", "shoulder_stabilizers"),
            equipment = setOf("rings"),
            technique = listOf(
                "Keep rings tucked cleanly beside torso without letting straps rub shoulders.",
                "Lower chest gently below ring height for full pec stretch.",
                "Turn rings slightly outward at top lockout."
            ),
            easier = "Elevated ring push-up",
            harder = "Feet-elevated ring push-up"
        ),
        calisthenics(
            "cal-ring-dip", "Gymnastic ring dip", "Unstable vertical push",
            listOf("triceps", "pectoralis_major"), listOf("anterior_deltoid", "shoulder_stabilizers"),
            equipment = setOf("rings"),
            difficulty = "advanced",
            technique = listOf(
                "Establish steady ring support first before descending smoothly.",
                "Keep elbows tucked close to ribs until shoulders align near ring height.",
                "Press firmly to vertical lockout without flaring shoulders inward."
            ),
            easier = "Parallel-bar dip",
            harder = "Weighted ring dip"
        ),
        calisthenics(
            "cal-chinup", "Chin-up", "Supinated vertical pull",
            listOf("latissimus_dorsi", "elbow_flexors"), listOf("rhomboids", "forearm_grip", "abdominals"),
            equipment = setOf("pullup_bar"),
            technique = listOf(
                "Grip shoulder-width with palms facing you and body slightly hollowed.",
                "Drive elbows cleanly down toward hips until chin clears bar.",
                "Control the negative descent all the way to straight-arm hang."
            ),
            easier = "Band-assisted chin-up",
            harder = "Chest-to-bar chin-up"
        ),
        calisthenics(
            "cal-wide-pullup", "Wide-grip pull-up", "Outer-lat vertical pull",
            listOf("latissimus_dorsi"), listOf("rhomboids", "posterior_deltoid", "forearm_grip"),
            equipment = setOf("pullup_bar"),
            difficulty = "advanced",
            technique = listOf(
                "Take a comfortable grip slightly wider than shoulder width.",
                "Lead chest smoothly upward toward bar while pulling elbows out and down.",
                "Avoid kipping hips or craning neck."
            ),
            easier = "Pull-up progression",
            harder = "Weighted wide-grip pull-up"
        ),
        calisthenics(
            "cal-commando-pullup", "Commando neutral pull-up", "Alternating vertical pull",
            listOf("latissimus_dorsi", "elbow_flexors"), listOf("obliques", "forearm_grip"),
            equipment = setOf("pullup_bar"),
            technique = listOf(
                "Stand sideways under bar with staggered close hands gripping along bar length.",
                "Pull smoothly until opposite shoulder touches near bar height.",
                "Alternate side per rep cleanly with zero lower-body swing."
            ),
            easier = "Neutral-grip pull-up",
            harder = "Towel commando pull-up"
        ),
        calisthenics(
            "cal-ring-row", "Gymnastic ring inverted row", "Horizontal bodyweight pull",
            listOf("rhomboids", "latissimus_dorsi"), listOf("posterior_deltoid", "elbow_flexors", "trunk_stabilizers"),
            equipment = setOf("rings"),
            difficulty = "foundation",
            technique = listOf(
                "Keep body straight as a plank from heels to head throughout the pull.",
                "Pull rings cleanly toward lower chest while rotating palms neutral to supinated.",
                "Lower slowly to full scapular stretch at bottom."
            ),
            easier = "High-angle inverted row",
            harder = "Feet-elevated ring inverted row"
        ),
        calisthenics(
            "cal-archer-row", "Archer inverted row", "Unilateral bodyweight row",
            listOf("latissimus_dorsi", "rhomboids"), listOf("elbow_flexors", "posterior_deltoid", "obliques"),
            equipment = setOf("rings"),
            difficulty = "advanced",
            technique = listOf(
                "Pull one ring smoothly to hip and ribs while extending opposite arm sideways.",
                "Keep hips level without twisting out of alignment.",
                "Lower slowly back to center hang before switching sides."
            ),
            easier = "Gymnastic ring inverted row",
            harder = "One-arm ring row"
        ),
        calisthenics(
            "cal-negative-pullup", "Eccentric negative pull-up", "Controlled vertical lowering",
            listOf("latissimus_dorsi", "elbow_flexors"), listOf("forearm_grip", "lower_trapezius"),
            equipment = setOf("pullup_bar"),
            difficulty = "foundation",
            technique = listOf(
                "Step or jump smoothly to top position with chest near bar.",
                "Lower slowly over 4 to 6 steady seconds while keeping lats engaged.",
                "Control the last third all the way into full straight arms."
            ),
            easier = "3-second chin-up negative",
            harder = "Paused 3-stop eccentric pull-up"
        ),
        calisthenics(
            "cal-muscle-up-transition", "Explosive chest-to-bar pull-up", "Muscle-up power prep",
            listOf("latissimus_dorsi", "posterior_deltoid"), listOf("elbow_flexors", "forearm_grip", "abdominals"),
            equipment = setOf("pullup_bar"),
            difficulty = "advanced",
            technique = listOf(
                "Initiate smoothly from hollow hang and pull elbows violently down and back.",
                "Bring lower sternum or upper ribs to bar height cleanly.",
                "Descend softly under control to avoid shoulder yank."
            ),
            easier = "Banded chest-to-bar pull-up",
            harder = "Strict bar muscle-up"
        ),
        calisthenics(
            "cal-skin-the-cat", "Ring skin-the-cat progression", "Shoulder extension and pull-core",
            listOf("latissimus_dorsi", "abdominals"), listOf("posterior_deltoid", "shoulder_stabilizers", "pectoralis_major"),
            equipment = setOf("rings"),
            difficulty = "developing",
            technique = listOf(
                "Tuck knees softly and invert backward smoothly under rings with straight arms.",
                "Lower into comfortable German hang only as shoulder mobility permits.",
                "Pull actively back overhead using lats and core without bouncing."
            ),
            easier = "Tuck invert and return without full German hang",
            harder = "Straight-body skin-the-cat"
        ),
        calisthenics(
            "cal-toes-to-bar", "Strict toes-to-bar", "Full-range hanging compression",
            listOf("abdominals", "hip_flexors"), listOf("latissimus_dorsi", "forearm_grip"),
            equipment = setOf("pullup_bar"),
            difficulty = "advanced",
            technique = listOf(
                "Keep arms straight and push bar gently downward with lats.",
                "Raise straight legs cleanly up until toes tap bar quietly.",
                "Lower legs slowly under control to stop body swing."
            ),
            easier = "Hanging leg raise",
            harder = "Paused toes-to-bar negatives"
        ),
        calisthenics(
            "cal-windshield-wiper", "Floor windshield wipers", "Rotational core control",
            listOf("obliques", "trunk_rotators"), listOf("abdominals", "hip_flexors"),
            technique = listOf(
                "Press shoulder blades and arms flat into floor for stable anchor.",
                "Rotate elevated legs side to side under slow oblique control.",
                "Bend knees softly if lower back begins lifting from mat."
            ),
            easier = "Bent-knee supine trunk wipers",
            harder = "Hanging bar windshield wipers"
        ),
        calisthenics(
            "cal-dragon-flag-neg", "Dragon flag negative", "Extreme anti-extension core",
            listOf("abdominals", "trunk_stabilizers"), listOf("latissimus_dorsi", "gluteus_maximus"),
            equipment = setOf("bench"),
            difficulty = "advanced",
            technique = listOf(
                "Anchor hands firmly behind head on bench and lift body onto upper shoulders.",
                "Keep hips extended straight from shoulders to toes, never hinged.",
                "Lower whole rigid body smoothly toward bench with controlled core tension."
            ),
            easier = "Single-leg tucked dragon flag negative",
            harder = "Full concentric dragon flag"
        ),
        calisthenics(
            "cal-v-up", "Bodyweight V-up", "Dynamic anterior compression",
            listOf("abdominals", "hip_flexors"), listOf("quadriceps"),
            technique = listOf(
                "Start long on mat with lower back gently anchored.",
                "Fold chest and straight legs together to reach fingers toward toes.",
                "Roll smoothly down vertebra by vertebra without slamming heels."
            ),
            easier = "Tuck-up crunch",
            harder = "Paused V-sit hold"
        ),
        calisthenics(
            "cal-crow-pose", "Crow balance hold", "Hand-balance skill progression",
            listOf("anterior_deltoid", "shoulder_stabilizers"), listOf("triceps", "abdominals", "forearm_grip"),
            technique = listOf(
                "Spread fingers wide and press fingertips actively into mat as brakes.",
                "Rest knees firmly high on upper triceps and lean chest forward gently.",
                "Lift one foot at a time into balanced float."
            ),
            easier = "Toe-tap crow shifts",
            harder = "Straight-arm crane pose"
        ),
        calisthenics(
            "cal-front-lever-tuck", "Tuck front lever hold", "Horizontal straight-arm pull",
            listOf("latissimus_dorsi", "abdominals"), listOf("rhomboids", "posterior_deltoid", "forearm_grip"),
            equipment = setOf("pullup_bar"),
            difficulty = "advanced",
            technique = listOf(
                "Keep elbows completely straight and press hands downward on bar.",
                "Tuck knees neatly toward chest until torso sits horizontal to floor.",
                "Avoid rounding shoulders upward into ears."
            ),
            easier = "Tuck front lever negatives",
            harder = "Flat-back tuck front lever"
        ),
        calisthenics(
            "cal-back-lever-tuck", "Tuck back lever hold", "Posterior straight-arm skill",
            listOf("pectoralis_major", "anterior_deltoid", "latissimus_dorsi"), listOf("abdominals", "spinal_extensors"),
            equipment = setOf("rings"),
            difficulty = "advanced",
            technique = listOf(
                "Skin-the-cat backward and pause cleanly with back level to floor.",
                "Keep knees tucked smoothly under pelvis and elbows completely straight.",
                "Exit safely forward or down before biceps tendon fatigues."
            ),
            easier = "Half tuck inverted hang",
            harder = "Straddle back lever"
        ),
        calisthenics(
            "cal-russian-twist", "Bodyweight Russian twist", "Rotational trunk endurance",
            listOf("obliques", "trunk_rotators"), listOf("abdominals", "hip_flexors"),
            difficulty = "foundation",
            technique = listOf(
                "Sit tall on sit-bones with spine elongated and chest open.",
                "Rotate shoulders evenly side to side rather than just tapping hands.",
                "Keep heels grounded first before progressing to hovering feet."
            ),
            easier = "Heels-grounded Russian twist",
            harder = "Weighted plate Russian twist"
        ),
        calisthenics(
            "cal-pistol-squat", "Pistol squat progression", "Single-leg full squat",
            listOf("quadriceps", "gluteus_maximus"), listOf("gluteus_medius", "foot_stabilizers", "hip_flexors"),
            difficulty = "advanced",
            technique = listOf(
                "Reach free leg straight ahead and counterbalance with arms forward.",
                "Sit hip down smoothly keeping working heel planted flat.",
                "Drive evenly out of the bottom without knee diving inward."
            ),
            easier = "Box-assisted single-leg squat",
            harder = "Paused pistol squat"
        ),
        calisthenics(
            "cal-shrimp-squat", "Shrimp squat", "Single-leg quad hinge balance",
            listOf("quadriceps", "gluteus_maximus"), listOf("gluteus_medius", "foot_stabilizers"),
            difficulty = "advanced",
            technique = listOf(
                "Hold or tuck trailing ankle behind you while bending forward gently.",
                "Touch back knee softly to a floor pad rather than slamming.",
                "Drive firmly through front midfoot to upright balance."
            ),
            easier = "Hand-assisted shrimp squat",
            harder = "Hands-behind-back shrimp squat"
        ),
        calisthenics(
            "cal-nordic-curl", "Nordic hamstring curl", "Bodyweight eccentric hamstring",
            listOf("hamstrings"), listOf("gluteus_maximus", "calves"),
            difficulty = "advanced",
            technique = listOf(
                "Keep hips extended straight throughout descent with glutes engaged.",
                "Resist gravity as long as possible with hamstrings before soft push-up catch.",
                "Keep lower back neutral rather than bending at waist."
            ),
            easier = "Nordic hamstring isometric hold",
            harder = "Unassisted concentric Nordic curl"
        ),
        calisthenics(
            "cal-sissy-squat", "Assisted sissy squat", "Quad lengthening and knee durability",
            listOf("quadriceps"), listOf("hip_flexors", "trunk_stabilizers"),
            difficulty = "developing",
            technique = listOf(
                "Hold a squat rack lightly for balance and rise slightly onto balls of feet.",
                "Lean torso and hips backward as one straight line while knees track forward.",
                "Drive smoothly back upright squeezing quads at top."
            ),
            easier = "Shallow-range assisted sissy squat",
            harder = "Unassisted bodyweight sissy squat"
        ),
        calisthenics(
            "cal-cossack-squat", "Cossack side squat", "Frontal-plane mobility and leg strength",
            listOf("quadriceps", "adductors", "gluteus_maximus"), listOf("gluteus_medius", "calves"),
            difficulty = "foundation",
            technique = listOf(
                "Take a wide stance and sit hips back cleanly into one heel.",
                "Rotate straight-leg toes upward while keeping working knee tracking over foot.",
                "Use arms forward for upright chest balance."
            ),
            easier = "High side lunge without full sit",
            harder = "Goblet dumbbell Cossack squat"
        ),
        calisthenics(
            "cal-sl-glute-bridge", "Single-leg floor glute bridge", "Unilateral posterior chain",
            listOf("gluteus_maximus"), listOf("hamstrings", "gluteus_medius"),
            difficulty = "foundation",
            technique = listOf(
                "Drive firmly through the stance heel while pulling opposite knee slightly toward chest.",
                "Keep pelvis square level at full lockout without rolling sideways.",
                "Pause 1 second at top before lowering cleanly."
            ),
            easier = "Floor glute bridge",
            harder = "Feet-elevated single-leg hip thrust"
        )
    )

    // -------------------------------------------------------------------------
    // CONDITIONING (18 mixed modal / erg / carry / engine movements - ZERO running)
    // -------------------------------------------------------------------------
    val conditioningRecords: List<ExerciseLibraryRecordV2> = listOf(
        conditioning(
            "cond-rowing-erg", "Rowing ergometer", "Full-body cyclic engine",
            listOf("quadriceps", "latissimus_dorsi"), listOf("hamstrings", "gluteus_maximus", "spinal_extensors", "forearm_grip"),
            equipment = setOf("erg"),
            difficulty = "foundation",
            technique = listOf(
                "Initiate each stroke by pushing legs hard first while arms stay long.",
                "Follow leg drive with gentle hip swing and crisp handle pull to lower ribs.",
                "Recover arms, hinge forward, then glide knees smoothly into catch."
            ),
            easier = "Steady conversational damper 3 to 4",
            harder = "High-output 500m intervals"
        ),
        conditioning(
            "cond-ski-erg", "SkiErg intervals", "Upper-posterior cyclic engine",
            listOf("latissimus_dorsi", "triceps", "abdominals"), listOf("posterior_deltoid", "quadriceps"),
            equipment = setOf("erg"),
            difficulty = "foundation",
            technique = listOf(
                "Reach tall at the catch onto midfoot with ribs stacked.",
                "Drive handles downward using abs and crisp hip hinge rather than arm curl.",
                "Maintain steady stroke cadence that fits your interval pacing."
            ),
            easier = "Steady aerobic 3-minute SkiErg pace",
            harder = "All-out 30-second sprint intervals"
        ),
        conditioning(
            "cond-air-bike", "Air bike", "Dual-action anaerobic power",
            listOf("quadriceps", "gluteus_maximus"), listOf("pectoralis_major", "latissimus_dorsi", "anterior_deltoid"),
            equipment = setOf("erg"),
            difficulty = "foundation",
            technique = listOf(
                "Adjust seat so knee keeps a slight soft bend at bottom of pedal stroke.",
                "Share work smoothly between pushing and pulling handles and steady leg cadence.",
                "Breathe rhythmically through steady aerobic blocks before pushing sprints."
            ),
            easier = "Zone 2 aerobic air bike flush",
            harder = "Tabata or 20-second maximal wattage sprints"
        ),
        conditioning(
            "cond-jump-rope", "Jump rope single-unders", "Rhythmic elastic conditioning",
            listOf("calves", "foot_stabilizers"), listOf("forearm_grip", "anterior_deltoid"),
            equipment = setOf("rope"),
            difficulty = "foundation",
            technique = listOf(
                "Turn rope smoothly from wrists with elbows close to waist.",
                "Rebound low just enough to clear cord quietly off balls of feet.",
                "Keep shoulders relaxed and posture upright."
            ),
            easier = "Shadow jump rope or alternating step hops",
            harder = "Jump rope double-unders"
        ),
        conditioning(
            "cond-double-unders", "Jump rope double-unders", "High-cadence elastic power",
            listOf("calves", "foot_stabilizers"), listOf("forearm_grip", "quadriceps", "abdominals"),
            equipment = setOf("rope"),
            difficulty = "advanced",
            technique = listOf(
                "Jump slightly higher with hollow upright body line rather than tucking knees.",
                "Spin faster at wrists twice per jump with consistent arm position.",
                "Keep breathing relaxed over unbroken sets."
            ),
            easier = "Single-single-double rhythm practice",
            harder = "Unbroken 50+ double-under sets"
        ),
        conditioning(
            "cond-battle-ropes", "Alternating battle ropes", "Upper lactic conditioning",
            listOf("anterior_deltoid", "forearm_grip"), listOf("latissimus_dorsi", "trunk_stabilizers", "quadriceps"),
            equipment = setOf("ropes"),
            difficulty = "foundation",
            technique = listOf(
                "Drop into a slight athletic squat brace with chest proud.",
                "Generate steady waves all the way down to the anchor point smoothly.",
                "Avoid rounding lower back as shoulders fatigue."
            ),
            easier = "Short 15-second alternating waves",
            harder = "Double-arm power slams"
        ),
        conditioning(
            "cond-sled-push", "Heavy sled push", "Low-eccentric leg drive conditioning",
            listOf("quadriceps", "gluteus_maximus", "calves"), listOf("trunk_stabilizers", "shoulder_stabilizers"),
            equipment = setOf("sled"),
            difficulty = "foundation",
            technique = listOf(
                "Set body on a forward 45-degree drive line with locked or slightly bent arms.",
                "Take powerful alternating steps pushing backward through the balls of feet.",
                "Keep neck neutral and avoid letting lumbar back sag."
            ),
            easier = "Moderate march sled push",
            harder = "Heavy sprint sled drive"
        ),
        conditioning(
            "cond-sled-pull", "Backward sled drag", "Quad and knee-health conditioning",
            listOf("quadriceps"), listOf("calves", "forearm_grip", "trunk_stabilizers"),
            equipment = setOf("sled"),
            difficulty = "foundation",
            technique = listOf(
                "Lean gently backward with arms extended holding straps taut.",
                "Step toe-to-heel backward driving actively through every quad extension.",
                "Keep smooth continuous steps without jerking harness."
            ),
            easier = "Lighter load backward drag walk",
            harder = "Heavy backward sled power march"
        ),
        conditioning(
            "cond-kb-thruster", "Kettlebell thruster", "Combined squat-to-press engine",
            listOf("quadriceps", "gluteus_maximus", "anterior_deltoid"), listOf("triceps", "lateral_deltoid", "trunk_stabilizers"),
            equipment = setOf("kettlebell"),
            technique = listOf(
                "Descend smoothly into front-rack squat with elbows forward.",
                "Accelerate out of bottom so leg momentum launches bells overhead.",
                "Absorb bells softly back into rack on next squat descent."
            ),
            easier = "Single-kettlebell goblet thruster",
            harder = "Heavy double kettlebell thruster intervals"
        ),
        conditioning(
            "cond-burpee", "Chest-to-floor burpee", "Full-body metabolic conditioning",
            listOf("quadriceps", "pectoralis_major"), listOf("anterior_deltoid", "triceps", "abdominals"),
            technique = listOf(
                "Step or drop hands down cleanly, lower chest softly, and press smoothly up.",
                "Snap feet forward flat near hands rather than landing solely on tiptoes.",
                "Finish tall with brief jump and crisp breathing cadence."
            ),
            easier = "Up-down sprawl without push-up",
            harder = "Burpee box jump-over"
        ),
        conditioning(
            "cond-mountain-climber", "Mountain climbers", "Fast plank hip drive",
            listOf("abdominals", "hip_flexors"), listOf("shoulder_stabilizers", "quadriceps"),
            difficulty = "foundation",
            technique = listOf(
                "Stack shoulders directly over wrists in crisp push-up plank.",
                "Drive alternating knees cleanly toward chest without bouncing hips high.",
                "Keep gaze forward of hands to keep spine aligned."
            ),
            easier = "Incline bench slow mountain climbers",
            harder = "Cross-body rapid mountain climbers"
        ),
        conditioning(
            "cond-bear-crawl", "Controlled bear crawl", "Cross-crawl core and shoulder engine",
            listOf("shoulder_stabilizers", "abdominals"), listOf("quadriceps", "serratus_anterior", "hip_flexors"),
            difficulty = "foundation",
            technique = listOf(
                "Hover knees just 3 to 5 cm off ground with flat horizontal back.",
                "Move opposite hand and foot cleanly together in quiet short steps.",
                "Avoid swaying hips laterally between steps."
            ),
            easier = "Stationary bear plank shoulder taps",
            harder = "Weighted sled-drag bear crawl"
        ),
        conditioning(
            "cond-box-step-over", "Dumbbell box step-over", "Low-impact aerobic grunt work",
            listOf("quadriceps", "gluteus_maximus"), listOf("calves", "forearm_grip", "gluteus_medius"),
            equipment = setOf("dumbbell", "box"),
            difficulty = "foundation",
            technique = listOf(
                "Step up firmly and alternate lead legs across every rep.",
                "Stand smoothly over the box without crashing down hard on descent.",
                "Keep breathing rhythmic across long EMOM or AMRAP rounds."
            ),
            easier = "Bodyweight box step-over",
            harder = "Higher box or heavier dual dumbbells"
        ),
        conditioning(
            "cond-wall-ball", "Medicine ball wall ball", "High-rep squat and overhead throw",
            listOf("quadriceps", "gluteus_maximus", "anterior_deltoid"), listOf("triceps", "lateral_deltoid", "trunk_stabilizers"),
            equipment = setOf("med_ball"),
            technique = listOf(
                "Catch ball cleanly into smooth squat rather than stopping and squatting twice.",
                "Drive hips forcefully up to launch ball straight onto wall target.",
                "Keep elbows tucked under ball at chest height."
            ),
            easier = "Lighter medicine ball to lower target",
            harder = "Heavy ball unbroken 20-rep sets"
        ),
        conditioning(
            "cond-turkish-getup", "Kettlebell Turkish get-up", "Total-body stability flow",
            listOf("shoulder_stabilizers", "trunk_stabilizers", "gluteus_maximus"), listOf("obliques", "quadriceps", "forearm_grip"),
            equipment = setOf("kettlebell"),
            technique = listOf(
                "Keep eyes locked steadily on the elevated bell through transitions.",
                "Move deliberately through elbow, tall sit, hip sweep, half-kneel, and stand.",
                "Reverse every checkpoint identically back down to supine position."
            ),
            easier = "Light dumbbell get-up",
            harder = "Heavy kettlebell Turkish get-up"
        ),
        conditioning(
            "cond-sandbag-carry", "Sandbag bear-hug carry", "Trunk and breathing under load",
            listOf("trunk_stabilizers", "abdominals"), listOf("elbow_flexors", "gluteus_maximus", "rhomboids"),
            equipment = setOf("sandbag"),
            technique = listOf(
                "Lock fingers tightly around the center of the bag hugged high against chest.",
                "Take short clipped steps while breathing steadily behind abdominal brace.",
                "Avoid leaning way back to balance heavy bag."
            ),
            easier = "Lighter sandbag short lap",
            harder = "Heavy sandbag alternating shoulder carry"
        ),
        conditioning(
            "cond-shuttle-cones", "Agility cone shuttles", "Change-of-direction conditioning",
            listOf("quadriceps", "calves"), listOf("gluteus_medius", "hamstrings", "adductors"),
            technique = listOf(
                "Drop hips low before every turn and plant outside foot squarely.",
                "Push sideways back into acceleration rather than rounding wide turns.",
                "Keep chest above knees on deceleration."
            ),
            easier = "75 percent pace rhythmic shuttles",
            harder = "Maximal sprint 5-10-5 shuttles"
        ),
        conditioning(
            "cond-stairs-climb", "Stadium stair climb", "Vertical aerobic endurance",
            listOf("gluteus_maximus", "quadriceps"), listOf("calves", "hamstrings"),
            technique = listOf(
                "Place full midfoot firmly on each step rather than leaning on rails.",
                "Drive tall through hip extension with smooth upright posture.",
                "Keep cadence steady for sustained aerobic climb."
            ),
            easier = "Single-step steady pace climb",
            harder = "Two-step climb or weighted vest climb"
        )
    )

    // -------------------------------------------------------------------------
    // MOBILITY & PREHAB (15 warm-up, joint-range, and durability drills)
    // -------------------------------------------------------------------------
    val mobilityRecords: List<ExerciseLibraryRecordV2> = listOf(
        mobility(
            "mob-90-90-switches", "90/90 hip switches", "Hip internal and external rotation",
            listOf("gluteus_medius", "adductors"), listOf("hip_flexors", "gluteus_maximus"),
            technique = listOf(
                "Sit tall with both knees bent 90 degrees front and side.",
                "Open the back knee slowly first before rolling knees smoothly across to other side.",
                "Use light fingertips behind torso only if needed for upright pelvis."
            ),
            easier = "Hands-supported 90/90 switches",
            harder = "Hands-free 90/90 switch to shin-box hip lift"
        ),
        mobility(
            "mob-worlds-greatest", "World's greatest stretch", "Full-body lunge and thoracic flow",
            listOf("hip_flexors", "hamstrings", "trunk_rotators"), listOf("gluteus_maximus", "adductors", "posterior_deltoid"),
            technique = listOf(
                "Step into a long lunge and drop inside elbow gently toward instep.",
                "Keep back leg active and squeeze glute softly before opening chest overhead.",
                "Shift hips smoothly back to stretch front hamstring briefly before switching."
            ),
            easier = "Back-knee-down lunge and twist",
            harder = "Hands-free active lunge rotation"
        ),
        mobility(
            "mob-thoracic-open-book", "Side-lying thoracic open book", "Upper-spine rotation",
            listOf("trunk_rotators", "pectoralis_major"), listOf("obliques", "posterior_deltoid"),
            technique = listOf(
                "Anchor top bent knee firmly onto mat or foam roller so lumbar hips stay quiet.",
                "Follow top hand with your eyes while opening ribs cleanly toward opposite floor.",
                "Exhale long at end range to invite thoracic release."
            ),
            easier = "Short-range bent-elbow thoracic opener",
            harder = "Seated thoracic windmill rotation"
        ),
        mobility(
            "mob-couch-stretch", "Wall couch stretch", "Quad and deep hip-flexor mobility",
            listOf("hip_flexors", "quadriceps"), listOf("abdominals"),
            technique = listOf(
                "Place back shin vertically near wall with padded knee support.",
                "Tuck pelvis gently into posterior tilt and squeeze glute before leaning back.",
                "Breathe calmly for 30 to 45 seconds without arching lumbar back."
            ),
            easier = "Knee further forward away from wall",
            harder = "Upright torso against wall with overhead reach"
        ),
        mobility(
            "mob-ankle-dorsiflexion", "Knee-to-wall ankle rocks", "Dorsiflexion ankle range",
            listOf("tibialis_anterior", "calves"), listOf("foot_stabilizers"),
            technique = listOf(
                "Keep heel anchored flush to floor while driving knee straight over middle toes.",
                "Pause 2 seconds at mild end-range stretch without pinching front ankle.",
                "Rock smoothly back out and repeat 10 to 12 slow reps per side."
            ),
            easier = "Standing elevated-toe dorsiflexion rock",
            harder = "Banded joint-mobilized ankle dorsiflexion rock"
        ),
        mobility(
            "mob-shoulder-dislocates", "Band shoulder pass-throughs", "Overhead shoulder mobility",
            listOf("shoulder_stabilizers", "pectoralis_major"), listOf("anterior_deltoid", "posterior_deltoid", "trapezius"),
            equipment = setOf("band"),
            technique = listOf(
                "Take a wide smooth grip on light band or dowel with straight elbows.",
                "Keep ribs pulled down so you never arch lower back overhead.",
                "Arc cleanly overhead and behind torso only within pain-free range."
            ),
            easier = "Wider grip and overhead-only arc",
            harder = "Closer grip controlled pass-throughs"
        ),
        mobility(
            "mob-scapular-wall-slide", "Forearm scapular wall slides", "Upward scapular glide",
            listOf("serratus_anterior", "lower_trapezius"), listOf("shoulder_stabilizers"),
            technique = listOf(
                "Keep forearms, wrists, and lower back comfortably close to wall.",
                "Slide forearms steadily upward while wrapping shoulder blades cleanly around ribs.",
                "Pause briefly near top without shrugging traps into neck."
            ),
            easier = "Standing step-away wall slides",
            harder = "Banded forearm wall slide with lift-off"
        ),
        mobility(
            "mob-glute-clamshell", "Banded glute clamshell", "Hip external rotation activation",
            listOf("gluteus_medius"), listOf("gluteus_maximus"),
            equipment = setOf("band"),
            technique = listOf(
                "Lie sideways with hips stacked vertically and feet aligned with spine.",
                "Open top knee against band without letting pelvis roll backward.",
                "Lower slowly to maintain glute-med activation."
            ),
            easier = "Bodyweight glute clamshell",
            harder = "Side-plank clamshell lift"
        ),
        mobility(
            "mob-monster-walk", "Lateral banded monster walk", "Hip abductor prehab",
            listOf("gluteus_medius"), listOf("gluteus_maximus", "foot_stabilizers"),
            equipment = setOf("band"),
            technique = listOf(
                "Place mini-band cleanly above knees or around ankles in athletic half-squat.",
                "Step sideways keeping toes straight forward and constant tension on band.",
                "Never allow trailing foot to snap rapidly inward."
            ),
            easier = "Higher knee band placement",
            harder = "Ankle-band crisp lateral walk"
        ),
        mobility(
            "mob-hamstring-floss", "Active supine hamstring sweeps", "Active posterior chain mobility",
            listOf("hamstrings"), listOf("calves", "hip_flexors"),
            technique = listOf(
                "Lie supine hugging one thigh gently vertical behind knee.",
                "Extend lower leg smoothly toward ceiling until gentle hamstring stretch.",
                "Pump foot slowly at top before bending knee softly back down."
            ),
            easier = "Light strap-assisted leg raise",
            harder = "Single-leg active straight-leg raises"
        ),
        mobility(
            "mob-cat-cow", "Controlled cat-cow", "Segmental spine mobility",
            listOf("spinal_extensors", "abdominals"), listOf("neck_rotators", "serratus_anterior"),
            technique = listOf(
                "Start on hands and knees with wrists directly beneath shoulders.",
                "Articulate sequentially from tailbone to neck on gentle inhale extension.",
                "Reverse smoothly by tucking pelvis and pushing floor away on exhale."
            ),
            easier = "Short comfortable mid-back arcs",
            harder = "Hovering bear-plank cat-cow"
        ),
        mobility(
            "mob-deep-squat-pry", "Deep squat thoracic pry", "Squat depth and hip opening",
            listOf("adductors", "gluteus_maximus"), listOf("tibialis_anterior", "trunk_rotators", "quadriceps"),
            technique = listOf(
                "Drop into comfortable deep squat holding ankles or light upright support.",
                "Press knees softly outward with elbows while lifting chest tall.",
                "Rotate one hand overhead cleanly to open thoracic spine."
            ),
            easier = "Heel-elevated assisted squat hold",
            harder = "Goblet dumbbell deep squat pry"
        ),
        mobility(
            "mob-wrist-prep", "Quadruped wrist flexor pulses", "Wrist extension durability",
            listOf("forearm_grip"), listOf("shoulder_stabilizers"),
            technique = listOf(
                "Spread fingers evenly on mat with palms beneath shoulders.",
                "Shift weight gently forward over fingers and back smoothly.",
                "Rotate fingers conservatively to expand wrist tolerance."
            ),
            easier = "Light tabletop finger shifts",
            harder = "First-knuckle palm lifts"
        ),
        mobility(
            "mob-hip-airplane", "Supported hip airplane", "Single-leg hip capsule rotation",
            listOf("gluteus_medius", "gluteus_maximus"), listOf("hamstrings", "foot_stabilizers", "trunk_rotators"),
            technique = listOf(
                "Hinge smoothly into shallow single-leg RDL with fingertip support.",
                "Open pelvis sideways to externally rotate stance hip.",
                "Close pelvis gently back downward past level into internal rotation."
            ),
            easier = "Two-hand rack supported hip airplane",
            harder = "Freestanding unassisted hip airplane"
        ),
        mobility(
            "mob-thread-needle", "Quadruped thread the needle", "Mid-back and rear-shoulder release",
            listOf("trunk_rotators", "posterior_deltoid"), listOf("latissimus_dorsi", "rhomboids"),
            technique = listOf(
                "Reach one arm smoothly underneath torso resting outer shoulder lightly on mat.",
                "Press supporting palm gently into floor to deepen mid-back rotation.",
                "Unwind smoothly upward reaching fingertips toward ceiling."
            ),
            easier = "Child's pose supported side thread",
            harder = "Deep reach with extended top leg"
        )
    )

    fun allRecords(): List<ExerciseLibraryRecordV2> =
        strengthRecords +
            calisthenicsRecords +
            plyometricsRecords +
            isometricsRecords +
            conditioningRecords +
            mobilityRecords
}