// EnhancedPredictionResult.kt
package org.springforge.qualityassurance.model

data class EnhancedPredictionResult(
    val architecture_pattern: String,
    val total_files_analyzed: Int,
    val total_violations: Int,
    val anti_patterns: List<AntiPatternDetail>,
    val summary: String
)