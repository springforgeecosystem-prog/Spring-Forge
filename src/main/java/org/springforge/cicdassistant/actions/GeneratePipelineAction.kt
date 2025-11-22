package org.springforge.cicdassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import org.springforge.cicdassistant.services.ClaudeService
import org.springforge.cicdassistant.services.ProjectAnalyzerService
import java.io.File

/**
 * Action to generate CI/CD artifacts using Claude 4 Opus
 */
class GeneratePipelineAction : AnAction("Generate CI/CD Pipeline") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Show generation options dialog
        val options = arrayOf(
            "Dockerfile",
            "GitHub Actions Workflow",
            "Docker Compose",
            "Kubernetes Manifests",
            "All CI/CD Files"
        )

        val choice = Messages.showChooseDialog(
            project,
            "Select what to generate for your Spring Boot project:",
            "SpringForge - CI/CD Generator",
            Messages.getQuestionIcon(),
            options,
            options[0]
        )

        if (choice < 0) return // User cancelled

        // Run generation in background with progress
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating CI/CD Files with Claude 4 Opus", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Analyzing Spring Boot project..."
                    indicator.fraction = 0.1

                    val analyzerService = ProjectAnalyzerService()
                    val projectInfo = analyzerService.analyzeProject(project)
                    val projectInfoStr = analyzerService.formatProjectInfoForPrompt(projectInfo)

                    indicator.text = "Connecting to Claude 4 Opus..."
                    indicator.fraction = 0.3

                    val claudeService = ClaudeService()

                    when (choice) {
                        0 -> generateDockerfile(claudeService, projectInfoStr, project, indicator)
                        1 -> generateGitHubActions(claudeService, projectInfoStr, project, indicator)
                        2 -> generateDockerCompose(claudeService, projectInfoStr, project, indicator)
                        3 -> generateKubernetesManifests(claudeService, projectInfoStr, project, indicator)
                        4 -> generateAllFiles(claudeService, projectInfoStr, project, indicator)
                    }

                    claudeService.close()

                    indicator.fraction = 1.0

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "✅ CI/CD files generated successfully!\n\nCheck your project root directory.",
                            "SpringForge - Success"
                        )
                    }

                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "❌ Failed to generate files:\n${ex.message}\n\nCheck if:\n1. AWS credentials are valid\n2. Claude 4 access is enabled\n3. Internet connection is active",
                            "SpringForge - Generation Error"
                        )
                    }
                    ex.printStackTrace()
                }
            }
        })
    }

    private fun generateDockerfile(
        claudeService: ClaudeService,
        projectInfo: String,
        project: com.intellij.openapi.project.Project,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Generating Dockerfile with Claude 4 Opus..."
        indicator.fraction = 0.5

        val dockerfile = claudeService.generateDockerfile(projectInfo)
        saveToFile(project, "Dockerfile", dockerfile)

        indicator.text = "Dockerfile generated!"
        indicator.fraction = 0.9
    }

    private fun generateGitHubActions(
        claudeService: ClaudeService,
        projectInfo: String,
        project: com.intellij.openapi.project.Project,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Generating GitHub Actions workflow..."
        indicator.fraction = 0.5

        val workflow = claudeService.generateGitHubActionsWorkflow(projectInfo)

        // Create .github/workflows directory
        val basePath = project.basePath ?: return
        val workflowDir = File(basePath, ".github/workflows")
        workflowDir.mkdirs()

        File(workflowDir, "ci-cd.yml").writeText(workflow)

        indicator.text = "GitHub Actions workflow generated!"
        indicator.fraction = 0.9
    }

    private fun generateDockerCompose(
        claudeService: ClaudeService,
        projectInfo: String,
        project: com.intellij.openapi.project.Project,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Generating docker-compose.yml..."
        indicator.fraction = 0.5

        val compose = claudeService.generateDockerCompose(projectInfo)
        saveToFile(project, "docker-compose.yml", compose)

        indicator.text = "docker-compose.yml generated!"
        indicator.fraction = 0.9
    }

    private fun generateKubernetesManifests(
        claudeService: ClaudeService,
        projectInfo: String,
        project: com.intellij.openapi.project.Project,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Generating Kubernetes manifests..."
        indicator.fraction = 0.5

        val manifests = claudeService.generateKubernetesManifests(projectInfo)

        // Create k8s directory
        val basePath = project.basePath ?: return
        val k8sDir = File(basePath, "k8s")
        k8sDir.mkdirs()

        File(k8sDir, "deployment.yml").writeText(manifests)

        indicator.text = "Kubernetes manifests generated!"
        indicator.fraction = 0.9
    }

    private fun generateAllFiles(
        claudeService: ClaudeService,
        projectInfo: String,
        project: com.intellij.openapi.project.Project,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Generating Dockerfile..."
        indicator.fraction = 0.2
        val dockerfile = claudeService.generateDockerfile(projectInfo)
        saveToFile(project, "Dockerfile", dockerfile)

        indicator.text = "Generating GitHub Actions workflow..."
        indicator.fraction = 0.4
        val workflow = claudeService.generateGitHubActionsWorkflow(projectInfo)
        val basePath = project.basePath ?: return
        val workflowDir = File(basePath, ".github/workflows")
        workflowDir.mkdirs()
        File(workflowDir, "ci-cd.yml").writeText(workflow)

        indicator.text = "Generating docker-compose.yml..."
        indicator.fraction = 0.6
        val compose = claudeService.generateDockerCompose(projectInfo)
        saveToFile(project, "docker-compose.yml", compose)

        indicator.text = "Generating .gitignore..."
        indicator.fraction = 0.8
        val gitignore = claudeService.generateGitIgnore("Maven") // You can detect this
        saveToFile(project, ".gitignore", gitignore)

        indicator.text = "All files generated!"
        indicator.fraction = 0.9
    }

    private fun saveToFile(project: com.intellij.openapi.project.Project, filename: String, content: String) {
        val basePath = project.basePath ?: return
        val file = File(basePath, filename)
        file.writeText(content)
    }

    override fun update(e: AnActionEvent) {
        // Only enable if a project is open
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

