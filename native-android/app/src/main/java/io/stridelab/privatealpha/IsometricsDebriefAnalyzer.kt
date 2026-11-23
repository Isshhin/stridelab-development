package io.stridelab.privatealpha

internal object IsometricsDebriefAnalyzer {
    fun analyze(evidence: CrossTrainingDebriefEvidence): List<DebriefInsight> {
        val dose = evidence.dose(holdsOnly = true) ?: return emptyList()
        return listOfNotNull(
            DebriefCopyTemplates.dose(DebriefDiscipline.ISOMETRICS, dose, dose.tone),
            evidence.effort(6)
        )
    }
}
