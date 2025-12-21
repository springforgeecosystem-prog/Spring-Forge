package org.springforge.codegeneration.analysis

/**
 * High-level project context aggregated from:
 * - Static code analysis
 * - Naming conventions
 * - Architecture inference
 * - Layer detection
 *
 * This is used as input to PromptBuilder.
 */
data class ProjectAnalysisResult(
    val detectedArchitecture: String,
    val basePackage: String,
    val layers: List<String>,
    val namingConventions: Map<String, String>
)
