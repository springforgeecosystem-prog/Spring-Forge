// src/main/java/org/springforge/qualityassurance/model/AntiPatternDetail.kt
package org.springforge.qualityassurance.model

data class AntiPatternDetail(
    val type: String,
    val severity: String,
    val affected_layer: String,
    val confidence: Double,
    val files: List<String>,
    val description: String,
    val recommendation: String,
    // v3: LLM validation fields
    val llm_validated: Boolean = false,
    val llm_description: String = "",
    val fix_suggestion: Map<String, Any>? = null
)