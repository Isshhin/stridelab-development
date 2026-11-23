package io.stridelab.privatealpha

internal object StrengthDebriefAnalyzer {
    private val explicitRir = Regex("""(?im)^[ \t]*RIR[ \t]*:[ \t]*(10|[0-9])[ \t]*$""")

    fun analyze(evidence: CrossTrainingDebriefEvidence): List<DebriefInsight> {
        val dose = evidence.dose() ?: return emptyList()
        val durability = evidence.session.title in setOf("Strength for Runners", "Lower / Runner Durability")
        return listOfNotNull(
            DebriefCopyTemplates.dose(DebriefDiscipline.STRENGTH, dose, dose.tone),
            if (durability) {
                // There is no saved per-set RIR field. Accept only one explicit session-level entry.
                val rir = explicitRir.findAll(evidence.log.notes).take(2).toList()
                    .singleOrNull()?.groupValues?.get(1)?.toInt()
                val effort = evidence.effort(7)
                if (rir == null || effort?.tone == DebriefTone.CAUTION) effort
                else DebriefCopyTemplates.runnerRepsInReserve(rir, target = 3)
            } else null,
            evidence.previousHardRunDays?.takeIf { durability || evidence.session.lower }
                ?.let { DebriefCopyTemplates.hardRunSpacing(it, plyometric = false) }
        )
    }
}
