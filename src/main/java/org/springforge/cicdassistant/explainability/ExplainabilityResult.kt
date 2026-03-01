package org.springforge.cicdassistant.explainability

import java.time.Instant

data class ExplainabilityResult(
    val insights: List<ExplainabilityInsight>,
    val timestamp: Instant = Instant.now(),
    val filesAnalyzed: Int = 0,
    val durationMs: Long = 0
) {
    fun getInsightsByFile(): Map<String, List<ExplainabilityInsight>> =
        insights.groupBy { it.filePath }

    fun getInsightsByCategory(): Map<InsightCategory, List<ExplainabilityInsight>> =
        insights.groupBy { it.category }

    fun getCountByCategory(cat: InsightCategory): Int =
        insights.count { it.category == cat }

    fun getTotalCount(): Int = insights.size

    fun getSummary(): String {
        val total = insights.size
        val files = filesAnalyzed
        return "$total insight${if (total != 1) "s" else ""} across $files file${if (files != 1) "s" else ""}"
    }
}
