package io.stridelab.privatealpha

/** A finding can explain a pattern without claiming a training effect or a physiological cause. */
internal data class CoachingFinding(
    val observation: String,
    val interpretation: String,
    val evidence: List<String>,
    val recommendation: String,
    val confidence: String
)
