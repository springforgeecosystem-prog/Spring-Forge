package org.springforge.cicdassistant.explainability

data class ExplainabilityInsight(
    val category: InsightCategory,
    val sectionName: String,
    val title: String,
    val explanation: String,
    val filePath: String,
    val lineStart: Int? = null,
    val lineEnd: Int? = null,
    val recommendations: List<String> = emptyList(),
    val priority: Int = 0
) {
    fun getLineRange(): String = when {
        lineStart == null                          -> "—"
        lineEnd == null || lineEnd == lineStart    -> "$lineStart"
        else                                       -> "$lineStart–$lineEnd"
    }

    fun getCategoryLabel(): String = when (category) {
        InsightCategory.SECURITY      -> "SECURITY"
        InsightCategory.PERFORMANCE   -> "PERFORMANCE"
        InsightCategory.BUILD         -> "BUILD"
        InsightCategory.CONFIGURATION -> "CONFIG"
        InsightCategory.RELIABILITY   -> "RELIABILITY"
        InsightCategory.DESIGN        -> "DESIGN"
    }
}
