package io.stridelab.privatealpha

internal object ConditioningDebriefAnalyzer {
    fun analyze(evidence: CrossTrainingDebriefEvidence): List<DebriefInsight> {
        val dose = evidence.dose(holdsOnly = true) ?: return emptyList()
        val ceiling = when {
            !evidence.session.quality -> 5
            evidence.session.title == "Threshold intervals" -> 8
            else -> 7
        }
        return listOfNotNull(
            DebriefCopyTemplates.dose(DebriefDiscipline.CONDITIONING, dose, dose.tone),
            evidence.effort(ceiling)
        )
    }
}
