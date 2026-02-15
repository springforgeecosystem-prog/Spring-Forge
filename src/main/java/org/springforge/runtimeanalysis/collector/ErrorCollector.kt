package org.springforge.runtimeanalysis.collector

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import java.nio.file.Files
import java.nio.file.Paths

object ErrorCollector {

    fun buildErrorPayload(stacktrace: String, project: Project): Map<String, Any> {
        val classes = extractClassesFromStacktrace(stacktrace)
        val paths = findFilesForClasses(project, classes)
        val files = getFileContents(paths)
        val classified = FileClassifier.classifyFiles(project, stacktrace, files)

        return mapOf(
                "error" to stacktrace,
                "code_context" to classified
        )
    }

    /**
     * Extract fully-qualified class names both from:
     * 1) stacktrace lines (at com.example.YourClass.method(...))
     * 2) exception messages (e.g., NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.YourClass')
     */
    private fun extractClassesFromStacktrace(stacktrace: String): List<String> {
        val classes = mutableSetOf<String>()

        // 1. Classes from stack frames
        val regexAt = Regex("""at ([\w.]+)\.""")
        regexAt.findAll(stacktrace).forEach { classes.add(it.groupValues[1]) }

        // 2. Classes mentioned in exception messages
        val regexMessage = Regex("""\b([\w.]+\.[A-Z]\w*)\b""") // FQCNs starting with package + capitalized class
        regexMessage.findAll(stacktrace).forEach { classes.add(it.groupValues[1]) }

        // 3. Optional: filter out common JDK/Spring packages if needed
        return classes.filterNot { it.startsWith("java.") || it.startsWith("javax.") || it.startsWith("org.springframework.") }
    }

    private fun findFilesForClasses(project: Project, classNames: List<String>): List<String> {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val files = mutableListOf<String>()

        classNames.forEach { name ->
            val psiClass: PsiClass? =
                    psiFacade.findClass(name, GlobalSearchScope.projectScope(project))
            psiClass?.containingFile?.virtualFile?.path?.let { files.add(it) }
        }

        return files
    }

    private fun getFileContents(paths: List<String>): List<Map<String, String>> {
        return paths.map { path ->
            mapOf(
                    "path" to path,
                    "content" to Files.readString(Paths.get(path))
            )
        }
    }
}
