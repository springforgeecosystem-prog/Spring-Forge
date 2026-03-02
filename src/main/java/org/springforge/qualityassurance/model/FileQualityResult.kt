package org.springforge.qualityassurance.model

data class FileQualityResult(
    val file_name: String = "",
    val file_path: String = "",
    val layer: String = "unknown",
    val quality_score: Double = 0.0,
    val quality_label: String = "Unknown",
    val quality_emoji: String = "",
    val quality_display: String = "",
    val issues: List<String> = emptyList()
)