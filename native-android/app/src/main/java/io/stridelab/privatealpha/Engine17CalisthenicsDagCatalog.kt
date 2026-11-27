package io.stridelab.privatealpha

/**
 * Formal AND/OR Calisthenics Prerequisite Hypergraph (Track 6 §7):
 * Unifies baseline seeds (`wall-push`, `e4`, `ap`, `e2`, `br`, `fc`, `side-plank`, `core-hollow`)
 * with all 30 progressions in ExpandedExerciseCatalog.calisthenicsRecords across all 8 Core Domains,
 * without mutating SHA-256 anchored JSON catalogs.
 */
internal object Engine17CalisthenicsDagCatalog {

    fun availableEquipmentTokens(request: PlanRequest): Set<String> = buildSet {
        if ((request.equipment["bar"] ?: 0) >= 1 || (request.equipment["pullup_bar"] ?: 0) >= 1) {
            add("pullup_bar")
        }
        if ((request.equipment["bands"] ?: 0) >= 1) add("band")
        if ((request.equipment["anchor"] ?: 0) >= 1) add("anchor")
        if ((request.equipment["wall"] ?: 0) >= 1) add("wall")
        if ((request.equipment["bench"] ?: 0) >= 1 || (request.equipment["box"] ?: 0) >= 1) add("bench")
        if ((request.equipment["rings"] ?: 0) >= 1) add("rings")
        if ((request.equipment["dip_belt"] ?: 0) >= 1 || (request.equipment["dip_bars"] ?: 0) >= 1) add("dip_belt")
    }

    fun buildNodes(catalogRecords: List<ExerciseLibraryRecordV2> = ExpandedExerciseCatalog.calisthenicsRecords): List<CalisthenicsDagNode> {
        val metaById = LinkedHashMap<String, StrengthExerciseMetadata>()

        fun registerCore(
            id: String,
            name: String,
            pattern: StrengthPattern,
            primary: List<String>,
            secondary: List<String>,
            eq: List<Set<String>>,
            difficulty: String,
            cue: String,
            bilateral: Boolean = true,
            isometric: Boolean = false
        ) {
            metaById[id] = StrengthExerciseMetadata(
                id = id,
                name = name,
                pattern = pattern,
                primaryMuscles = primary,
                secondaryMuscles = secondary,
                equipmentOptions = eq,
                loadSemanticsKind = ExternalLoadSemanticsKind.UNLOADED_BODYWEIGHT,
                laterality = if (bilateral) StrengthLaterality.BILATERAL else StrengthLaterality.UNILATERAL,
                technicalComplexity = when (difficulty) {
                    "advanced" -> TechnicalComplexity.HIGH
                    "developing" -> TechnicalComplexity.MODERATE
                    else -> TechnicalComplexity.LOW
                },
                mechanicalTags = setOf(MechanicalTag.LOW_SYSTEMIC),
                slotOrder = if (pattern == StrengthPattern.CORE_STABILITY) {
                    StrengthSlotOrder.ISOLATION_AND_CORE
                } else {
                    StrengthSlotOrder.PRIMARY_COMPOUND
                },
                difficulty = difficulty,
                techniqueCues = listOf(cue),
                isIsometricHold = isometric
            )
        }

        // Baseline catalog seeds (from ExerciseLibraryV2 baseline, mapped to ExerciseMuscleCatalog.STANDARD_MUSCLES)
        registerCore(
            "wall-push", "Wall or elevated push-up", StrengthPattern.HORIZONTAL_PUSH,
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "trunk_stabilizers"), listOf(emptySet()),
            "foundation", "Keep one rigid line from shoulders to heels and press smoothly."
        )
        registerCore(
            "e4", "Incline or floor push-up", StrengthPattern.HORIZONTAL_PUSH,
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "trunk_stabilizers"), listOf(emptySet()),
            "developing", "Brace glutes and abs cleanly without letting lower back sag."
        )
        registerCore(
            "br", "Bodyweight inverted row", StrengthPattern.HORIZONTAL_PULL,
            listOf("rhomboids", "latissimus_dorsi"), listOf("elbow_flexors", "trunk_stabilizers"), listOf(setOf("anchor"), emptySet()),
            "foundation", "Keep torso rigid and pull chest smoothly between hands."
        )
        registerCore(
            "ap", "Band-assisted pull-up", StrengthPattern.VERTICAL_PULL,
            listOf("latissimus_dorsi", "elbow_flexors"), listOf("rhomboids", "trunk_stabilizers"), listOf(setOf("pullup_bar")),
            "developing", "Initiate by depressing shoulder blades before driving elbows down."
        )
        registerCore(
            "e2", "Strict pull-up", StrengthPattern.VERTICAL_PULL,
            listOf("latissimus_dorsi", "elbow_flexors"), listOf("rhomboids", "forearm_grip"), listOf(setOf("pullup_bar")),
            "advanced", "Pull chest smoothly to bar height with zero lower-body swing."
        )
        registerCore(
            "fc", "Dead bug or plank control", StrengthPattern.CORE_STABILITY,
            listOf("abdominals", "trunk_stabilizers"), listOf("hip_flexors"), listOf(emptySet()),
            "foundation", "Keep ribcage stacked quietly over pelvis and breathe steadily.",
            isometric = true
        )
        registerCore(
            "side-plank", "Side plank hold", StrengthPattern.CORE_STABILITY,
            listOf("obliques", "trunk_stabilizers"), listOf("gluteus_medius", "shoulder_stabilizers"), listOf(emptySet()),
            "developing", "Lift hips cleanly in line with torso and avoid rolling forward.",
            bilateral = false, isometric = true
        )
        registerCore(
            "core-hollow", "Hollow body hold", StrengthPattern.CORE_STABILITY,
            listOf("abdominals", "trunk_stabilizers"), listOf("hip_flexors"), listOf(emptySet()),
            "developing", "Press lower back firmly into mat and breathe softly behind brace.",
            isometric = true
        )

        // Core Track 6 DAG gateway nodes
        registerCore(
            "cal-scap-hang", "Scapular dead hang", StrengthPattern.VERTICAL_PULL,
            listOf("latissimus_dorsi", "forearm_grip"), listOf("shoulder_stabilizers"), listOf(setOf("pullup_bar")),
            "foundation", "Relax into straight arms then gently depress shoulders down away from ears.",
            isometric = true
        )
        registerCore(
            "cal-support-hold", "Parallel bar support hold", StrengthPattern.VERTICAL_PUSH,
            listOf("triceps", "anterior_deltoid"), listOf("pectoralis_major", "trunk_stabilizers"), listOf(emptySet()),
            "foundation", "Lock elbows tall and press shoulders cleanly downward.",
            isometric = true
        )
        registerCore(
            "cal-dip-assisted", "Band-assisted parallel dip", StrengthPattern.VERTICAL_PUSH,
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "trunk_stabilizers"), listOf(emptySet()),
            "developing", "Keep elbows tucked smoothly backward and stop cleanly before front-shoulder pinch."
        )
        registerCore(
            "cal-wall-plank", "Wall plank and shoulder lean", StrengthPattern.VERTICAL_PUSH,
            listOf("anterior_deltoid", "trunk_stabilizers"), listOf("triceps"), listOf(emptySet()),
            "foundation", "Grip floor with active fingertips and push tall through shoulder blades.",
            isometric = true
        )
        registerCore(
            "cal-chest-to-wall-hs", "Chest-to-wall handstand hold", StrengthPattern.VERTICAL_PUSH,
            listOf("anterior_deltoid", "triceps"), listOf("trunk_stabilizers"), listOf(emptySet()),
            "developing", "Stack wrists, shoulders, and hips vertically with ribs tucked smoothly.",
            isometric = true
        )
        registerCore(
            "cal-split-squat", "Bodyweight split squat", StrengthPattern.UNILATERAL_LOWER,
            listOf("quadriceps", "gluteus_maximus"), listOf("calves", "trunk_stabilizers"), listOf(emptySet()),
            "foundation", "Track front knee smoothly over toes while keeping torso tall.",
            bilateral = false
        )
        registerCore(
            "cal-rfess", "Rear-foot-elevated split squat", StrengthPattern.UNILATERAL_LOWER,
            listOf("quadriceps", "gluteus_maximus"), listOf("hamstrings", "trunk_stabilizers"), listOf(setOf("bench"), emptySet()),
            "developing", "Lower back knee under full 3-second control with level pelvis.",
            bilateral = false
        )
        registerCore(
            "cal-weighted-pullup", "Weighted strict pull-up", StrengthPattern.VERTICAL_PULL,
            listOf("latissimus_dorsi"), listOf("elbow_flexors", "forearm_grip", "trunk_stabilizers"), listOf(setOf("pullup_bar")),
            "advanced", "Keep added weight steady beneath center of mass with strict dead-hang lockout."
        )
        registerCore(
            "cal-weighted-dip", "Weighted parallel dip", StrengthPattern.VERTICAL_PUSH,
            listOf("pectoralis_major", "triceps"), listOf("anterior_deltoid", "trunk_stabilizers"), listOf(emptySet()),
            "advanced", "Descend smoothly to parallel arm bend with ribs braced over hips."
        )

        // All 30 structured progressions from ExpandedExerciseCatalog.calisthenicsRecords
        for (raw in catalogRecords) {
            if (raw.id in metaById) continue
            val adapted = Engine16StrengthExerciseCatalog.toMetadata(raw)
            val isHold = "hold" in raw.id || "tuck" in raw.id || "crow" in raw.id || "scap" in raw.id
            metaById[raw.id] = adapted.copy(
                loadSemanticsKind = ExternalLoadSemanticsKind.UNLOADED_BODYWEIGHT,
                isIsometricHold = adapted.isIsometricHold || isHold
            )
        }

        return metaById.values.map { meta -> toDagNode(meta) }
    }

    private fun toDagNode(meta: StrengthExerciseMetadata): CalisthenicsDagNode = when (meta.id) {
        // 1. PULL_UP domain (OR pathways + tendon eccentric awareness)
        "cal-scap-hang" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PULL_UP, 0, CalisthenicsPrerequisiteGate.None, defaultHoldSeconds = 25)
        "br" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PULL_UP, 0, CalisthenicsPrerequisiteGate.None)
        "cal-ring-row" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PULL_UP, 0, CalisthenicsPrerequisiteGate.None, regressionNodeId = "br")
        "ap" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PULL_UP, 1, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-scap-hang", "br"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "br")
        "cal-negative-pullup" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PULL_UP, 1, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-scap-hang", "br"), setOf(CalisthenicsBottleneck.STRENGTH)), isEccentricHighTendonStrain = true, regressionNodeId = "ap")
        "e2", "cal-chinup" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PULL_UP, 2, CalisthenicsPrerequisiteGate.RequireAny(listOf("ap", "cal-negative-pullup", "cal-ring-row"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "ap")
        "cal-wide-pullup", "cal-commando-pullup" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PULL_UP, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("e2"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "e2")
        "cal-archer-row" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PULL_UP, 2, CalisthenicsPrerequisiteGate.RequireAny(listOf("cal-ring-row", "e2"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.COORDINATION_SKILL)), regressionNodeId = "cal-ring-row")
        "cal-muscle-up-transition" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PULL_UP, 4, CalisthenicsPrerequisiteGate.RequireAll(listOf("e2"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.COORDINATION_SKILL)), isSkillPracticeOnly = true, regressionNodeId = "e2")

        // 2. PUSH_UP domain
        "wall-push" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PUSH_UP, 0, CalisthenicsPrerequisiteGate.None)
        "e4" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PUSH_UP, 1, CalisthenicsPrerequisiteGate.RequireAll(listOf("wall-push"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "wall-push")
        "cal-diamond-pushup", "cal-decline-pushup", "cal-ring-pushup" ->
            CalisthenicsDagNode(meta, CalisthenicsDomain.PUSH_UP, 2, CalisthenicsPrerequisiteGate.RequireAll(listOf("e4"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "e4")
        "cal-pike-pushup" -> CalisthenicsDagNode(meta, CalisthenicsDomain.PUSH_UP, 2, CalisthenicsPrerequisiteGate.RequireAll(listOf("e4"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.MOBILITY_ROM)), regressionNodeId = "e4")
        "cal-archer-pushup", "cal-pseudo-planche-pushup" ->
            CalisthenicsDagNode(meta, CalisthenicsDomain.PUSH_UP, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("e4", "cal-decline-pushup"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.BALANCE_PROPRIOCEPTION)), regressionNodeId = "e4")

        // 3. DIP domain (AND Support Hold + Floor Push-Up)
        "cal-support-hold" -> CalisthenicsDagNode(meta, CalisthenicsDomain.DIP, 0, CalisthenicsPrerequisiteGate.None, defaultHoldSeconds = 30)
        "cal-dip-assisted" -> CalisthenicsDagNode(meta, CalisthenicsDomain.DIP, 1, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-support-hold", "e4"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "e4")
        "cal-ring-dip" -> CalisthenicsDagNode(meta, CalisthenicsDomain.DIP, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-dip-assisted", "cal-support-hold"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.BALANCE_PROPRIOCEPTION)), regressionNodeId = "cal-dip-assisted")

        // 4. HANDSTAND_SKILL domain (AND Wrist/Overhead + Wall Plank -> Crow / Chest-to-Wall -> Wall HSPU)
        "cal-wall-plank" -> CalisthenicsDagNode(meta, CalisthenicsDomain.HANDSTAND_SKILL, 0, CalisthenicsPrerequisiteGate.None, isSkillPracticeOnly = true, mobilityCheckRequired = "Wrist extension and overhead flexion check", defaultHoldSeconds = 25)
        "cal-crow-pose" -> CalisthenicsDagNode(meta, CalisthenicsDomain.HANDSTAND_SKILL, 1, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-wall-plank"), setOf(CalisthenicsBottleneck.BALANCE_PROPRIOCEPTION, CalisthenicsBottleneck.MOBILITY_ROM)), isSkillPracticeOnly = true, regressionNodeId = "cal-wall-plank", mobilityCheckRequired = "Wrist loading tolerance check", defaultHoldSeconds = 20)
        "cal-chest-to-wall-hs" -> CalisthenicsDagNode(meta, CalisthenicsDomain.HANDSTAND_SKILL, 1, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-wall-plank", "cal-pike-pushup"), setOf(CalisthenicsBottleneck.BALANCE_PROPRIOCEPTION, CalisthenicsBottleneck.MOBILITY_ROM)), isSkillPracticeOnly = true, regressionNodeId = "cal-wall-plank", mobilityCheckRequired = "Overhead shoulder flexion check", defaultHoldSeconds = 25)
        "cal-hspu-wall" -> CalisthenicsDagNode(meta, CalisthenicsDomain.HANDSTAND_SKILL, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-chest-to-wall-hs", "cal-pike-pushup"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.BALANCE_PROPRIOCEPTION)), regressionNodeId = "cal-pike-pushup")

        // 5. CORE_AND_L_SIT domain
        "fc" -> CalisthenicsDagNode(meta, CalisthenicsDomain.CORE_AND_L_SIT, 0, CalisthenicsPrerequisiteGate.None, defaultHoldSeconds = 25)
        "side-plank" -> CalisthenicsDagNode(meta, CalisthenicsDomain.CORE_AND_L_SIT, 1, CalisthenicsPrerequisiteGate.RequireAll(listOf("fc"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "fc", defaultHoldSeconds = 20)
        "core-hollow" -> CalisthenicsDagNode(meta, CalisthenicsDomain.CORE_AND_L_SIT, 1, CalisthenicsPrerequisiteGate.RequireAll(listOf("fc"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "fc", defaultHoldSeconds = 20)
        "cal-russian-twist" -> CalisthenicsDagNode(meta, CalisthenicsDomain.CORE_AND_L_SIT, 1, CalisthenicsPrerequisiteGate.None, regressionNodeId = "fc")
        "cal-v-up", "cal-windshield-wiper" -> CalisthenicsDagNode(meta, CalisthenicsDomain.CORE_AND_L_SIT, 2, CalisthenicsPrerequisiteGate.RequireAll(listOf("core-hollow"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.MOBILITY_ROM)), regressionNodeId = "core-hollow")
        "cal-toes-to-bar", "cal-dragon-flag-neg" -> CalisthenicsDagNode(meta, CalisthenicsDomain.CORE_AND_L_SIT, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("core-hollow", "cal-v-up"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "cal-v-up")

        // 6. SINGLE_LEG_SQUAT domain
        "cal-split-squat" -> CalisthenicsDagNode(meta, CalisthenicsDomain.SINGLE_LEG_SQUAT, 0, CalisthenicsPrerequisiteGate.None, regressionNodeId = null)
        "cal-cossack-squat", "cal-sl-glute-bridge" -> CalisthenicsDagNode(meta, CalisthenicsDomain.SINGLE_LEG_SQUAT, 1, CalisthenicsPrerequisiteGate.None)
        "cal-rfess" -> CalisthenicsDagNode(meta, CalisthenicsDomain.SINGLE_LEG_SQUAT, 1, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-split-squat"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.MOBILITY_ROM)), regressionNodeId = "cal-split-squat", mobilityCheckRequired = "Ankle dorsiflexion and hip flexion check")
        "cal-sissy-squat" -> CalisthenicsDagNode(meta, CalisthenicsDomain.SINGLE_LEG_SQUAT, 2, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-split-squat"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.MOBILITY_ROM)), regressionNodeId = "cal-split-squat")
        "cal-pistol-squat", "cal-shrimp-squat" -> CalisthenicsDagNode(meta, CalisthenicsDomain.SINGLE_LEG_SQUAT, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-rfess", "cal-cossack-squat"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.MOBILITY_ROM, CalisthenicsBottleneck.BALANCE_PROPRIOCEPTION)), regressionNodeId = "cal-rfess", mobilityCheckRequired = "Ankle dorsiflexion and hip flexion check")
        "cal-nordic-curl" -> CalisthenicsDagNode(meta, CalisthenicsDomain.SINGLE_LEG_SQUAT, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-sl-glute-bridge"), setOf(CalisthenicsBottleneck.STRENGTH)), isEccentricHighTendonStrain = true, regressionNodeId = "cal-sl-glute-bridge")

        // 7. WEIGHTED_CALISTHENICS
        "cal-weighted-pullup" -> CalisthenicsDagNode(meta, CalisthenicsDomain.WEIGHTED_CALISTHENICS, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("e2"), setOf(CalisthenicsBottleneck.STRENGTH)), isVerticalSuspendedWeightedEligible = true, regressionNodeId = "e2")
        "cal-weighted-dip" -> CalisthenicsDagNode(meta, CalisthenicsDomain.WEIGHTED_CALISTHENICS, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-dip-assisted"), setOf(CalisthenicsBottleneck.STRENGTH)), isVerticalSuspendedWeightedEligible = true, regressionNodeId = "cal-dip-assisted")

        // 8. ISOMETRIC_STATIC_HOLDS domain
        "cal-skin-the-cat" -> CalisthenicsDagNode(meta, CalisthenicsDomain.ISOMETRIC_STATIC_HOLDS, 2, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-scap-hang", "cal-ring-row"), setOf(CalisthenicsBottleneck.MOBILITY_ROM, CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "cal-scap-hang", defaultHoldSeconds = 20)
        "cal-front-lever-tuck" -> CalisthenicsDagNode(meta, CalisthenicsDomain.ISOMETRIC_STATIC_HOLDS, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("e2", "core-hollow"), setOf(CalisthenicsBottleneck.STRENGTH)), regressionNodeId = "cal-scap-hang", defaultHoldSeconds = 15)
        "cal-back-lever-tuck" -> CalisthenicsDagNode(meta, CalisthenicsDomain.ISOMETRIC_STATIC_HOLDS, 3, CalisthenicsPrerequisiteGate.RequireAll(listOf("cal-skin-the-cat", "e2"), setOf(CalisthenicsBottleneck.STRENGTH, CalisthenicsBottleneck.MOBILITY_ROM)), regressionNodeId = "cal-skin-the-cat", defaultHoldSeconds = 15)

        else -> CalisthenicsDagNode(meta, CalisthenicsDomain.CORE_AND_L_SIT, 1, CalisthenicsPrerequisiteGate.None)
    }
}
