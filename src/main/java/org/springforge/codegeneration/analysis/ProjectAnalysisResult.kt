package org.springforge.codegeneration.analysis

data class ProjectAnalysisResult(
    val detectedArchitecture: String,
    val basePackage: String,
    val layers: List<String>,
    val namingConventions: Map<String, String>
) {
    companion object {
        fun empty() = ProjectAnalysisResult(
            detectedArchitecture = "unknown",
            basePackage = "",
            layers = emptyList(),
            namingConventions = emptyMap()
        )
    }
}
