package org.springforge.runtimeanalysis.collector

import com.intellij.openapi.project.Project

object FileClassifier {

    fun classifyFiles(
            project: Project,
            stacktrace: String,
            codeContext: List<Map<String, String>>
    ): List<Map<String, Any>> {

        val beanNames = extractBeanNames(stacktrace)

        return codeContext.map { file ->
            val path = file["path"] ?: ""
            val content = file["content"] ?: ""

            val category = classify(content)
            val relevance = scoreRelevance(content, beanNames)

            mapOf(
                    "path" to path,
                    "category" to category,
                    "relevance" to relevance,
                    "content" to content
            )
        }
    }

    // Extract bean names or class fragments mentioned in stacktrace
    private fun extractBeanNames(stacktrace: String): List<String> {
        val regex = Regex("""([A-Za-z0-9_]+)(?:Exception|Bean|Service|Controller)?""")
        return regex.findAll(stacktrace)
                .map { it.groupValues[1] }
                .filter { it.length > 3 }
                .toList()
    }

    // Basic heuristics to identify file type
    private fun classify(content: String): String {

        return when {
            content.contains("@RestController") ||
                    content.contains("@Controller") -> "controller"

            content.contains("@Service") -> "service"

            content.contains("@Repository") -> "repository"

            content.contains("@Configuration") ||
                    content.contains("@Bean") -> "config"

            content.contains("@Entity") ||
                    content.contains("@Table") -> "entity"

            else -> "other"
        }
    }

    // Relevance score: do these files matter for the error?
    private fun scoreRelevance(content: String, beanNames: List<String>): Int {
        var score = 0

        beanNames.forEach { bean ->
            if (content.contains(bean)) score += 2
        }

        // Priority boost for controllers/services/configs
        if (content.contains("@Service")) score += 3
        if (content.contains("@Configuration")) score += 3

        return score
    }
}
