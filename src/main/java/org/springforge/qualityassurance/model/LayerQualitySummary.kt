package org.springforge.qualityassurance.model

data class LayerQualitySummary(
    val layer: String = "",
    val file_count: Int = 0,
    val mean_score: Double = 0.0,
    val quality_label: String = "",
    val quality_emoji: String = "",
    val quality_display: String = "",
    val files: List<FileQualityResult> = emptyList()
)