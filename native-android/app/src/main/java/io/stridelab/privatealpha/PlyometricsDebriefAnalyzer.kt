package io.stridelab.privatealpha

internal object PlyometricsDebriefAnalyzer {
    fun analyze(evidence: CrossTrainingDebriefEvidence): List<DebriefInsight> {
        // Recovery and reduced-dose sessions contain non-jumping movements.
        // Only the Engine 1.5 movements prescribed as one contact per rep qualify.
        val targets = evidence.targets.filter { it.id in setOf("pogo-hop", "line-bound") && it.reps != null }
        val cap = targets.sumOf { it.sets * (it.reps ?: 0) }
        if (cap <= 0) return emptyList()
        val byId = targets.associateBy { it.id }
        val contacts = evidence.sets.filter { it.exerciseId in byId && it.set > 0 && it.reps?.let { reps -> reps > 0 } == true }
        if (contacts.isEmpty()) return emptyList()
        val allLogged = targets.all { target ->
            contacts.count { it.exerciseId == target.id && it.set in 1..target.sets } == target.sets
        }
        return listOfNotNull(
            DebriefCopyTemplates.contacts(contacts.sumOf { it.reps ?: 0 }, cap, allLogged),
            evidence.previousHardRunDays?.let { DebriefCopyTemplates.hardRunSpacing(it, plyometric = true) }
        )
    }
}
