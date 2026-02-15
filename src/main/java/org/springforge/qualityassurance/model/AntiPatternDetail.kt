// src/main/java/org/springforge/qualityassurance/model/AntiPatternDetail.kt
package org.springforge.qualityassurance.model

data class AntiPatternDetail(
    val type: String,
    val severity: String,
    val affected_layer: String,
    val confidence: Double,
    val files: List<String>,
    val description: String,
    val recommendation: String
)