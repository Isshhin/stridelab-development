package io.stridelab.privatealpha

internal object CalisthenicsDebriefAnalyzer {
    fun analyze(evidence: CrossTrainingDebriefEvidence): List<DebriefInsight> {
        val dose = evidence.dose() ?: return emptyList()
        return listOf(DebriefCopyTemplates.dose(DebriefDiscipline.CALISTHENICS, dose, dose.tone))
    }
}
