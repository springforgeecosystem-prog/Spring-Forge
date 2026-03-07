package org.springforge.qualityassurance.model

data class CombinedAnalysisResult(
    val architecture_pattern: String = "",
    val total_files_analyzed: Int = 0,
    val analysis_date: String = "",
    val overall_score: Double = 0.0,
    val overall_label: String = "",
    val overall_display: String = "",
    val layer_scores: List<LayerQualitySummary> = emptyList(),
    val total_violations: Int = 0,
    val anti_patterns: List<AntiPatternDetail> = emptyList(),
    val clean_files: List<String> = emptyList(),
    val files: List<FileQualityResult> = emptyList(),
    val avg_loc: Double = 0.0,
    val avg_cross_layer_deps: Double = 0.0,
    val files_with_violations: Int = 0,
    val projected_score_after_fixes: Double = 0.0,
    val quality_summary: String = "",
    val violation_summary: String = "",
    // v3: LLM validation summary
    val llm_enhanced: Boolean = false,
    val false_positives_filtered: Int = 0,
    val fix_suggestions: List<FixSuggestion> = emptyList()
)