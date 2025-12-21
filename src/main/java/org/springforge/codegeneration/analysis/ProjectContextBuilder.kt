package org.springforge.codegeneration.analysis

import com.intellij.openapi.project.Project

object ProjectContextBuilder {

    fun build(project: Project): ProjectAnalysisResult {
        return ProjectAnalyzer(project).analyze()
    }
}
