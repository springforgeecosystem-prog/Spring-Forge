package org.springforge.cicdassistant.explainability

/**
 * Contract for per-artifact AI explainers.
 * Mirrors the Validator interface pattern from the validation module.
 */
interface ExplainabilityAnalyzer {
    fun analyze(filePath: String, content: String): ExplainabilityResult
    fun getName(): String
    fun canAnalyze(filePath: String): Boolean
}
