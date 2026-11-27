package io.stridelab.privatealpha

import java.util.Locale

internal data class ExerciseSubstitutionResult(
    val replacement: StrengthExerciseMetadata,
    val requiresRirRediscovery: Boolean,
    val reasons: List<String>
)

/**
 * Deterministic, read-only 8-field strength exercise metadata catalog uniting the
 * audited ExerciseLibraryV2 baseline with ExpandedExerciseCatalog.strengthRecords.
 * Never mutates catalog.json or exercise-library-v2.json.
 */
internal object Engine16StrengthExerciseCatalog {

    private val coreBaselineSeeds: List<ExerciseLibraryRecordV2> by lazy {
        fun core(
            id: String,
            name: String,
            cats: List<String>,
            pats: List<String>,
            prim: List<String>,
            sec: List<String>,
            eq: List<Set<String>>,
            diff: String,
            tech: List<String>,
            easier: String,
            harder: String
        ) = ExerciseLibraryRecordV2(
            id = id,
            name = name,
            kind = "exercise",
            categories = cats,
            movementPatterns = pats,
            primaryMuscles = prim,
            secondaryMuscles = sec,
            equipmentOptions = eq,
            setupNotes = emptyList(),
            difficulty = diff,
            prerequisites = emptySet(),
            prerequisiteNotes = emptyList(),
            regressions = emptyList(),
            progressions = emptyList(),
            alternatives = emptyList(),
            technique = tech,
            easier = easier,
            harder = harder,
            caveats = emptyList(),
            provenance = emptyList(),
            media = ExerciseMediaV2("placeholder", null, null, null, null, emptyList(), "core-baseline", "")
        )

        listOf(
            core("e1", "Back squat", listOf("strength_gym"), listOf("squat"),
                listOf("quadriceps", "gluteus_maximus"), listOf("hamstrings", "spinal_extensors", "trunk_stabilizers"),
                listOf(setOf("barbell", "rack", "safeties")), "developing",
                listOf("Brace ribs down and squat evenly through midfoot.", "Drive knees out slightly over toes while torso stays tight."),
                "Goblet squat", "Barbell front squat"),
            core("e3", "Romanian deadlift", listOf("strength_gym"), listOf("hinge"),
                listOf("hamstrings", "gluteus_maximus"), listOf("spinal_extensors", "forearm_grip"),
                listOf(setOf("barbell")), "developing",
                listOf("Push hips straight back while bar skims close to thighs.", "Stop when hips reach full hinge with a flat lower back."),
                "Dumbbell Romanian deadlift", "Conventional barbell deadlift"),
            core("e4", "Push-up progression", listOf("bodyweight"), listOf("horizontal_press"),
                listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "trunk_stabilizers"),
                listOf(emptySet()), "foundation",
                listOf("Keep body rigid from heels to crown throughout press.", "Lower chest cleanly near floor with elbows angled 45 degrees."),
                "Wall push-up", "Feet-elevated decline push-up"),
            core("e6", "Bulgarian split squat", listOf("strength_gym"), listOf("unilateral"),
                listOf("quadriceps", "gluteus_maximus"), listOf("gluteus_medius", "adductors"),
                listOf(setOf("dumbbells", "bench"), setOf("bench")), "developing",
                listOf("Keep most weight on the front foot with upright ribs.", "Lower smoothly until front thigh nears parallel."),
                "Reverse lunge", "Heavy Bulgarian split squat"),
            core("gs", "Goblet squat", listOf("strength_gym"), listOf("squat"),
                listOf("quadriceps", "gluteus_maximus"), listOf("trunk_stabilizers", "upper_back" /* mapped safely */),
                listOf(setOf("dumbbells")), "foundation",
                listOf("Hold weight snug against upper chest with elbows pointing down.", "Sit between knees under smooth control."),
                "Bodyweight squat", "Barbell front squat").copy(
                secondaryMuscles = listOf("trunk_stabilizers", "anterior_deltoid")
            ),
            core("dh", "Dumbbell Romanian deadlift", listOf("strength_gym"), listOf("hinge"),
                listOf("hamstrings", "gluteus_maximus"), listOf("spinal_extensors", "forearm_grip"),
                listOf(setOf("dumbbells")), "foundation",
                listOf("Keep weights close to thighs as hips hinge backward.", "Squeeze glutes cleanly to stand tall."),
                "Unloaded hip hinge", "Romanian deadlift"),
            core("fp", "Dumbbell floor press", listOf("strength_gym"), listOf("horizontal_press"),
                listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid"),
                listOf(setOf("dumbbells")), "foundation",
                listOf("Pause gently as backs of upper arms touch floor cleanly.", "Press weights smoothly without flaring elbows wide."),
                "Push-up progression", "Dumbbell bench press"),
            core("bp", "Dumbbell bench press", listOf("strength_gym"), listOf("horizontal_press"),
                listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid"),
                listOf(setOf("dumbbells", "bench")), "developing",
                listOf("Plant feet flat and anchor shoulder blades against bench.", "Lower dumbbells smoothly to chest line before pressing up."),
                "Dumbbell floor press", "Barbell bench press"),
            core("dr", "Dumbbell row", listOf("strength_gym"), listOf("horizontal_pull"),
                listOf("latissimus_dorsi", "rhomboids"), listOf("elbow_flexors", "posterior_deltoid"),
                listOf(setOf("dumbbells")), "foundation",
                listOf("Hinge flat and pull dumbbell smoothly toward hip pocket.", "Control the lowering stretch without twisting shoulders."),
                "Band row", "Chest-supported dumbbell row"),
            core("br", "Band row", listOf("strength_gym"), listOf("horizontal_pull"),
                listOf("rhomboids", "latissimus_dorsi"), listOf("posterior_deltoid", "elbow_flexors"),
                listOf(setOf("band", "anchor"), setOf("band")), "foundation",
                listOf("Draw shoulder blades smoothly back while pulling elbows to ribs.", "Pause 1 second at squeeze."),
                "Prone W raise", "Dumbbell row"),
            core("cable-lat-pulldown", "Seated lat pulldown", listOf("strength_gym"), listOf("vertical_pull"),
                listOf("latissimus_dorsi"), listOf("elbow_flexors", "rhomboids"),
                listOf(setOf("lat_pulldown_machine"), setOf("cable")), "foundation",
                listOf("Pull bar smoothly to upper chest while keeping shoulders depressed.", "Control full overhead return."),
                "Band row", "Single-arm cable lat pulldown"),
            core("machine-chest-press", "Seated machine chest press", listOf("strength_gym"), listOf("horizontal_press"),
                listOf("pectoralis_major"), listOf("triceps", "anterior_deltoid"),
                listOf(setOf("chest_press_machine"), setOf("machine")), "foundation",
                listOf("Set seat so handles align with mid-chest.", "Press forward cleanly while keeping shoulders anchored back."),
                "Push-up progression", "Dumbbell bench press"),
            core("machine-leg-press", "Seated machine leg press", listOf("strength_gym"), listOf("squat"),
                listOf("quadriceps", "gluteus_maximus"), listOf("hamstrings", "calves"),
                listOf(setOf("leg_press_machine"), setOf("machine")), "foundation",
                listOf("Keep hips and lower back flush against backrest throughout.", "Lower sled under control to 90+ degrees knee bend."),
                "Goblet squat", "Machine hack squat"),
            core("machine-leg-curl", "Seated machine leg curl", listOf("strength_gym"), listOf("knee_flexion"),
                listOf("hamstrings"), listOf("calves"),
                listOf(setOf("leg_curl_machine"), setOf("machine")), "foundation",
                listOf("Align knee joint cleanly with pivot and lock lap pad snug.", "Curl smoothly and control 2-second return."),
                "Floor glute bridge", "Lying machine leg curl"),
            core("curl-db", "Standing dumbbell curl", listOf("strength_gym"), listOf("elbow_flexion"),
                listOf("elbow_flexors"), listOf("forearm_grip"),
                listOf(setOf("dumbbells")), "foundation",
                listOf("Pin elbows gently at sides without swinging hips.", "Lower smoothly to full arm extension."),
                "Lighter alternating curl", "Incline dumbbell curl"),
            core("calf-raise-supported", "Supported calf raise", listOf("bodyweight", "strength_gym"), listOf("calf_raise"),
                listOf("calves"), listOf("foot_stabilizers"),
                listOf(emptySet(), setOf("chair")), "foundation",
                listOf("Pause briefly at bottom stretch before pressing onto big-toe mound.", "Control 2-second descent."),
                "Two-leg floor calf raise", "Standing machine calf raise"),
            core("glute-bridge", "Floor glute bridge", listOf("bodyweight"), listOf("hip_extension"),
                listOf("gluteus_maximus"), listOf("hamstrings", "trunk_stabilizers"),
                listOf(emptySet()), "foundation",
                listOf("Drive through heels to align hips with shoulders.", "Keep ribs down at top lockout."),
                "Short-range glute bridge", "Barbell hip thrust"),
            core("hg", "Unloaded hip hinge", listOf("bodyweight"), listOf("hinge"),
                listOf("hamstrings", "gluteus_maximus"), listOf("spinal_extensors"),
                listOf(emptySet()), "foundation",
                listOf("Push hips cleanly back with soft knees and flat spine.", "Snap hips tall using glutes."),
                "Wall hip hinge touch", "Dumbbell Romanian deadlift"),
            core("rl", "Reverse lunge", listOf("bodyweight"), listOf("unilateral"),
                listOf("quadriceps", "gluteus_maximus"), listOf("gluteus_medius", "calves"),
                listOf(emptySet()), "foundation",
                listOf("Step smoothly backward onto ball of rear foot.", "Drive upright through front midfoot."),
                "Split squat", "Dumbbell walking lunge"),
            core("sq", "Bodyweight squat", listOf("bodyweight"), listOf("squat"),
                listOf("quadriceps", "gluteus_maximus"), listOf("calves", "trunk_stabilizers"),
                listOf(emptySet()), "foundation",
                listOf("Sit hips cleanly back and down between knees.", "Stand smoothly through full feet."),
                "Chair sit-to-stand", "Goblet squat"),
            core("plank", "Forearm plank", listOf("bodyweight"), listOf("anti_extension"),
                listOf("abdominals", "trunk_stabilizers"), listOf("shoulder_stabilizers", "gluteus_maximus"),
                listOf(emptySet()), "developing",
                listOf("Brace abs and glutes firmly so lower back stays flat.", "Breathe steadily behind active brace."),
                "Incline plank", "Weighted forearm plank"),
            core("side-plank", "Kneeling side plank", listOf("bodyweight"), listOf("anti_lateral_flexion"),
                listOf("obliques", "lateral_trunk_muscles"), listOf("gluteus_medius", "shoulder_stabilizers"),
                listOf(emptySet()), "foundation",
                listOf("Align elbow directly beneath shoulder and lift hips square.", "Hold evenly across both sides."),
                "Short-hold side plank", "Full straight-leg side plank"),
            core("bird-dog", "Quadruped bird dog", listOf("bodyweight"), listOf("quadruped_control"),
                listOf("trunk_stabilizers", "spinal_extensors"), listOf("gluteus_maximus", "shoulder_stabilizers"),
                listOf(emptySet()), "developing",
                listOf("Extend opposite arm and leg cleanly without rotating pelvis.", "Pause briefly while breathing softly."),
                "Alternating arm/leg lift", "Loaded dead bug / Pallof press"),
            core("wall-push", "Wall push-up", listOf("bodyweight"), listOf("horizontal_press"),
                listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid"),
                listOf(emptySet(), setOf("wall")), "foundation",
                listOf("Keep torso straight like a moving plank against wall.", "Lower chest gently and press smooth."),
                "Higher wall stance", "Push-up progression")
        )
    }

    private val unilateralIds = setOf(
        "e6", "rl", "str-walking-lunge", "str-step-up", "str-single-leg-rdl",
        "str-single-arm-pulldown", "str-landmine-press", "str-dumbbell-snatch",
        "str-cable-glute-kickback", "side-plank", "bird-dog"
    )

    private val axialIds = setOf(
        "e1", "e3", "str-front-squat", "str-zercher-squat", "str-conv-deadlift",
        "str-sumo-deadlift", "str-trap-bar-deadlift", "str-overhead-press",
        "str-good-morning", "str-bb-row", "str-tbar-row"
    )

    private val highEccentricIds = setOf(
        "e3", "dh", "e6", "str-walking-lunge", "str-step-up", "str-single-leg-rdl",
        "str-lying-leg-curl", "machine-leg-curl", "str-glute-ham-raise", "str-good-morning",
        "str-conv-deadlift", "str-standing-calf-raise", "str-seated-calf-raise"
    )

    private val ballisticIds = setOf(
        "str-kb-swing", "str-dumbbell-snatch"
    )

    private val isometricIds = setOf(
        "plank", "side-plank", "bird-dog", "str-weighted-plank", "str-pallof-press"
    )

    fun allMetadata(library: ExerciseLibraryV2? = null): List<StrengthExerciseMetadata> {
        val raw = mutableListOf<ExerciseLibraryRecordV2>()
        val seenIds = HashSet<String>()
        if (library != null) {
            for (r in library.records) {
                if (r.kind == "exercise" && ("strength_gym" in r.categories || "bodyweight" in r.categories)) {
                    if (seenIds.add(r.id)) raw += r
                }
            }
        }
        for (r in coreBaselineSeeds) {
            if (seenIds.add(r.id)) raw += r
        }
        for (r in ExpandedExerciseCatalog.strengthRecords) {
            if (seenIds.add(r.id)) raw += r
        }
        return raw.map(::toMetadata).sortedBy { it.id }
    }

    fun availableEquipmentTokens(request: PlanRequest): Set<String> = buildSet {
        if ((request.equipment["db"] ?: 0) >= 1) {
            add("dumbbell")
            add("dumbbells")
        }
        if ((request.equipment["bb"] ?: 0) >= 1) add("barbell")
        if ((request.equipment["rack"] ?: 0) >= 1) {
            add("rack")
            add("safeties")
        }
        if ((request.equipment["bench"] ?: 0) >= 1) add("bench")
        if ((request.equipment["bands"] ?: 0) >= 1) add("band")
        if ((request.equipment["anchor"] ?: 0) >= 1) add("anchor")
        if ((request.equipment["support"] ?: 0) >= 1) {
            add("raised_surface")
            add("box")
        }
        if ((request.equipment["bar"] ?: 0) >= 1) add("pullup_bar")
        if ((request.equipment["cable"] ?: 0) >= 1) {
            add("cable")
            add("lat_pulldown_machine")
        }
        if ((request.equipment["machine"] ?: 0) >= 1) {
            add("machine")
            add("chest_press_machine")
            add("leg_curl_machine")
            add("leg_press_machine")
            add("lat_pulldown_machine")
        }
        if ((request.equipment["kb"] ?: 0) >= 1) add("kettlebell")
        if ((request.equipment["box"] ?: 0) >= 1) {
            add("box")
            add("raised_surface")
        }
        // Everyday home objects are safely assumed available for bodyweight/anchor movements.
        add("chair")
        add("wall")
    }

    fun matchesEquipment(
        exercise: StrengthExerciseMetadata,
        availableTokens: Set<String>
    ): Boolean = exercise.equipmentOptions.any { option ->
        option.isEmpty() || availableTokens.containsAll(option)
    }

    fun proficiencyAllowed(
        exercise: StrengthExerciseMetadata,
        experience: Int
    ): Boolean = when {
        experience <= 0 -> exercise.difficulty == "foundation"
        experience == 1 -> exercise.difficulty in setOf("foundation", "developing")
        else -> true
    }

    fun substitute(
        targetPattern: StrengthPattern,
        preferredLaterality: StrengthLaterality? = null,
        excludedIds: Set<String>,
        availableTokens: Set<String>,
        experience: Int,
        objective: StrengthObjective,
        catalog: List<StrengthExerciseMetadata>,
        priorExercise: StrengthExerciseMetadata? = null
    ): ExerciseSubstitutionResult? {
        val candidates = catalog.filter { ex ->
            ex.pattern == targetPattern &&
                ex.id !in excludedIds &&
                matchesEquipment(ex, availableTokens) &&
                proficiencyAllowed(ex, experience) &&
                !(objective == StrengthObjective.RUNNING_SUPPORTIVE && ex.id == "str-good-morning")
        }
        if (candidates.isEmpty()) return null
        val sorted = candidates.sortedWith(
            compareByDescending<StrengthExerciseMetadata> {
                preferredLaterality == null || it.laterality == preferredLaterality
            }
                .thenByDescending { it.difficulty == "developing" && experience >= 1 }
                .thenByDescending {
                    it.loadSemanticsKind in setOf(
                        ExternalLoadSemanticsKind.TOTAL_BARBELL_MASS,
                        ExternalLoadSemanticsKind.DUMBBELL_PER_HAND,
                        ExternalLoadSemanticsKind.MACHINE_STACK_SETTING
                    )
                }
                .thenBy { it.id }
        )
        val picked = sorted.first()
        val reasons = buildList {
            if (priorExercise != null) {
                if (priorExercise.id != picked.id) add("SUBSTITUTED_EXERCISE")
                if (priorExercise.loadSemanticsKind != picked.loadSemanticsKind) add("LOAD_SEMANTICS_CHANGED")
                if (priorExercise.laterality != picked.laterality) add("LATERALITY_CHANGED")
            }
        }
        return ExerciseSubstitutionResult(
            replacement = picked,
            requiresRirRediscovery = priorExercise != null && priorExercise.id != picked.id,
            reasons = reasons
        )
    }

    fun toMetadata(record: ExerciseLibraryRecordV2): StrengthExerciseMetadata {
        val pattern = inferPattern(record)
        val laterality = if (record.id in unilateralIds ||
            record.name.contains("single", ignoreCase = true) ||
            record.name.contains("unilateral", ignoreCase = true) ||
            record.name.contains("split squat", ignoreCase = true) ||
            record.name.contains("lunge", ignoreCase = true) ||
            record.name.contains("step-up", ignoreCase = true)
        ) StrengthLaterality.UNILATERAL else StrengthLaterality.BILATERAL

        val loadSemantics = inferLoadSemanticsKind(record)
        val mechTags = buildSet {
            if (record.id in axialIds) add(MechanicalTag.AXIAL)
            if (record.id in highEccentricIds) add(MechanicalTag.HIGH_ECCENTRIC)
            if (record.id in ballisticIds) add(MechanicalTag.BALLISTIC)
            if (isEmpty() && pattern in setOf(
                    StrengthPattern.ARM_ISOLATION,
                    StrengthPattern.DELTOID_ISOLATION,
                    StrengthPattern.CORE_STABILITY,
                    StrengthPattern.QUAD_ISOLATION,
                    StrengthPattern.HAMSTRING_ISOLATION
                )
            ) add(MechanicalTag.LOW_SYSTEMIC)
        }
        val slotOrder = when {
            MechanicalTag.BALLISTIC in mechTags -> StrengthSlotOrder.POWER_SKILL
            pattern in setOf(
                StrengthPattern.SQUAT,
                StrengthPattern.HINGE,
                StrengthPattern.HORIZONTAL_PUSH,
                StrengthPattern.VERTICAL_PUSH,
                StrengthPattern.HORIZONTAL_PULL,
                StrengthPattern.VERTICAL_PULL
            ) && (MechanicalTag.AXIAL in mechTags || record.difficulty != "foundation" || record.id in setOf(
                "gs", "dh", "fp", "bp", "dr", "machine-leg-press", "machine-chest-press",
                "cable-lat-pulldown", "str-trap-bar-deadlift", "str-barbell-hip-thrust",
                "str-bb-bench-press", "str-incline-db-press", "str-db-shoulder-press",
                "str-chest-supported-row", "str-seated-cable-row"
            )) -> StrengthSlotOrder.PRIMARY_COMPOUND
            pattern == StrengthPattern.UNILATERAL_LOWER ||
                laterality == StrengthLaterality.UNILATERAL -> StrengthSlotOrder.SECONDARY_COMPOUND_OR_UNILATERAL
            else -> StrengthSlotOrder.ISOLATION_AND_CORE
        }
        val complexity = when {
            record.difficulty == "advanced" || record.id in setOf("e1", "e3", "str-front-squat", "str-conv-deadlift", "str-sumo-deadlift", "str-overhead-press") ->
                TechnicalComplexity.HIGH
            record.difficulty == "developing" || laterality == StrengthLaterality.UNILATERAL ->
                TechnicalComplexity.MODERATE
            else -> TechnicalComplexity.LOW
        }
        val minInc = when (loadSemantics) {
            ExternalLoadSemanticsKind.TOTAL_BARBELL_MASS,
            ExternalLoadSemanticsKind.DUMBBELL_PER_HAND ->
                Engine16StrengthPolicyRegistry.DEFAULT_DUMBBELL_INCREMENT_PER_HAND_KG
            ExternalLoadSemanticsKind.MACHINE_STACK_SETTING ->
                Engine16StrengthPolicyRegistry.DEFAULT_MACHINE_INCREMENT_KG
            else -> 2.5
        }
        val normEq = record.equipmentOptions.map { opt ->
            opt.map { token ->
                when (token.lowercase(Locale.ROOT)) {
                    "dumbbells" -> "dumbbell"
                    else -> token.lowercase(Locale.ROOT)
                }
            }.toSet()
        }
        return StrengthExerciseMetadata(
            id = record.id,
            name = record.name,
            pattern = pattern,
            primaryMuscles = record.primaryMuscles,
            secondaryMuscles = record.secondaryMuscles,
            equipmentOptions = normEq,
            loadSemanticsKind = loadSemantics,
            laterality = laterality,
            technicalComplexity = complexity,
            mechanicalTags = mechTags,
            slotOrder = slotOrder,
            difficulty = record.difficulty,
            minIncrementKg = minInc,
            techniqueCues = record.technique,
            easierName = record.easier,
            harderName = record.harder,
            isIsometricHold = record.id in isometricIds && record.id != "str-pallof-press"
        )
    }

    private fun inferPattern(record: ExerciseLibraryRecordV2): StrengthPattern {
        val text = (record.id + " " + record.name + " " + record.movementPatterns.joinToString(" "))
            .lowercase(Locale.ROOT)
        return when {
            record.id in ballisticIds -> StrengthPattern.POWER_BALLISTIC
            record.id in setOf("str-leg-extension") -> StrengthPattern.QUAD_ISOLATION
            record.id in setOf("machine-leg-curl", "str-lying-leg-curl", "str-glute-ham-raise") ||
                "knee_flexion" in record.movementPatterns -> StrengthPattern.HAMSTRING_ISOLATION
            "calf" in text || "tibialis" in text -> StrengthPattern.CALF_ANKLE
            record.id in setOf("str-db-lateral-raise", "str-cable-lateral-raise", "str-rear-delt-fly", "str-face-pull") ->
                StrengthPattern.DELTOID_ISOLATION
            record.id in setOf(
                "curl-db", "str-hammer-curl", "str-incline-db-curl", "str-preacher-curl", "str-cable-curl",
                "str-wrist-curl", "str-triceps-pushdown", "str-overhead-triceps-ext", "str-skull-crusher"
            ) -> StrengthPattern.ARM_ISOLATION
            record.id in setOf("e6", "rl", "str-walking-lunge", "str-step-up", "str-copenhagen-adduction", "str-hip-abduction") ||
                "unilateral" in record.movementPatterns -> StrengthPattern.UNILATERAL_LOWER
            "hinge" in text || "deadlift" in text || "glute" in text || "back extension" in text || "good morning" in text ->
                StrengthPattern.HINGE
            "squat" in text || "leg press" in text || "sit-to-stand" in text ->
                StrengthPattern.SQUAT
            "vertical push" in text || "overhead press" in text || "shoulder press" in text || "arnold" in text || "landmine press" in text ->
                StrengthPattern.VERTICAL_PUSH
            "horizontal" in text && "push" in text || "bench" in text || "floor press" in text || "chest" in text ||
                "push-up" in text || "dip" in text || "pec deck" in text ->
                StrengthPattern.HORIZONTAL_PUSH
            "vertical pull" in text || "pulldown" in text || "pullover" in text || "upright row" in text || "shrug" in text ->
                StrengthPattern.VERTICAL_PULL
            "horizontal" in text && "pull" in text || "row" in text ->
                StrengthPattern.HORIZONTAL_PULL
            else -> StrengthPattern.CORE_STABILITY
        }
    }

    private fun inferLoadSemanticsKind(record: ExerciseLibraryRecordV2): ExternalLoadSemanticsKind {
        val allEq = record.equipmentOptions.flatten().map { it.lowercase(Locale.ROOT) }.toSet()
        return when {
            record.equipmentOptions.any { it.isEmpty() } && allEq.all { it in setOf("chair", "wall") } ->
                ExternalLoadSemanticsKind.UNLOADED_BODYWEIGHT
            "barbell" in allEq -> ExternalLoadSemanticsKind.TOTAL_BARBELL_MASS
            "dumbbell" in allEq || "dumbbells" in allEq || "kettlebell" in allEq ->
                ExternalLoadSemanticsKind.DUMBBELL_PER_HAND
            "cable" in allEq || "machine" in allEq ||
                "lat_pulldown_machine" in allEq || "chest_press_machine" in allEq ||
                "leg_press_machine" in allEq || "leg_curl_machine" in allEq ->
                ExternalLoadSemanticsKind.MACHINE_STACK_SETTING
            "band" in allEq -> ExternalLoadSemanticsKind.RESISTANCE_BAND_TIER
            "belt" in allEq || "plate" in allEq -> ExternalLoadSemanticsKind.EXTERNAL_ADDED_MASS
            else -> ExternalLoadSemanticsKind.UNLOADED_BODYWEIGHT
        }
    }
}
