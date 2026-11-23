package io.stridelab.privatealpha

internal val builderEquipmentLabels = listOf(
    "db" to "Dumbbells", "bb" to "Barbell",
    "bands" to "Resistance bands", "bench" to "Bench",
    "bar" to "Pull-up bar", "rack" to "Squat rack",
    "anchor" to "Band anchors", "support" to "Stable supports"
)

internal val builderEquipmentIds = builderEquipmentLabels.map { it.first }.toSet()

internal fun builderEquipmentFor(state: TrainingState, request: PlanRequest): Map<String, Int> {
    val owner = state.account ?: state.localPlanOwner
    if (owner == null) return request.equipment
    val compatible = state.plans.asSequence()
        .filter { it.owner == owner && it.status != "draft" && Engine1Engines.canManage(it) }
        .filter {
            it.request.strengthDays > 0 && strengthTrack(it.request) == strengthTrack(request)
        }
        .maxByOrNull { it.createdAt }
    return state.savedEquipment ?: compatible?.request?.equipment ?: request.equipment
}
