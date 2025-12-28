package org.springforge.codegeneration.analysis

data class ProjectAnalysisResult(
    val detectedArchitecture: String,
    val confidence: Double,
    val basePackage: String,
    val layers: List<String>,
    val namingConventions: Map<String, String>
) {
    companion object {
        fun empty() = ProjectAnalysisResult(
            detectedArchitecture = "unknown",
            confidence = 0.0,
            basePackage = "",
            layers = emptyList(),
            namingConventions = emptyMap()
        )
    }
}
