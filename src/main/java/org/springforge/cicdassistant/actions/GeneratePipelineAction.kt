package org.springforge.cicdassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking
import org.springforge.cicdassistant.bedrock.BedrockClient
import org.springforge.cicdassistant.github.GitHubMCPClient
import org.springforge.cicdassistant.github.mcp.GitHubMCPServerConnector
import org.springforge.cicdassistant.mcp.models.MCPContext
import org.springforge.cicdassistant.services.ClaudeService
import org.springforge.cicdassistant.services.ProjectAnalyzerService
import org.springforge.cicdassistant.ui.SourceSelectionDialog
import org.springforge.cicdassistant.ui.SourceType
import java.io.File

/**
 * Action to generate CI/CD artifacts using Claude Sonnet 4.5
 */
class GeneratePipelineAction : AnAction("Generate CI/CD Pipeline") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Step 1: Show source selection dialog (Local vs GitHub)
        val sourceDialog = SourceSelectionDialog(project)
        if (!sourceDialog.showAndGet()) return // User cancelled

        val selectedSource = sourceDialog.selectedSource
        val githubUrl = sourceDialog.githubUrl
        val githubBranch = sourceDialog.githubBranch

        // Step 2: Show generation options dialog
        val options = arrayOf(
            "Dockerfile",
            "GitHub Actions Workflow",
            "Docker Compose",
            "Kubernetes Manifests",
            "All CI/CD Files"
        )

        val sourceLabel = if (selectedSource == SourceType.LOCAL) {
            "Local Project"
        } else {
            "GitHub: ${githubUrl.substringAfterLast("/")}"
        }

        val choice = Messages.showChooseDialog(
            project,
            "Source: $sourceLabel\n\nSelect what to generate:",
            "SpringForge - CI/CD Generator",
            Messages.getQuestionIcon(),
            options,
            options[0]
        )

        if (choice < 0) return // User cancelled

        // Step 3: Run generation in background with progress
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating CI/CD Files with Claude Sonnet 4.5", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Get MCP Context based on source type
                    val mcpContext = when (selectedSource) {
                        SourceType.LOCAL -> {
                            indicator.text = "Analyzing local Spring Boot project..."
                            indicator.fraction = 0.1

                            val analyzerService = ProjectAnalyzerService()
                            analyzerService.analyzeProjectWithMCP(project)
                        }
                        SourceType.GITHUB -> {
                            indicator.text = "Connecting to GitHub MCP Server..."
                            indicator.fraction = 0.1

                            val connector = GitHubMCPServerConnector()
                            val githubClient = GitHubMCPClient(connector)

                            indicator.text = "Analyzing GitHub repository: $githubUrl (branch: $githubBranch)..."
                            indicator.fraction = 0.2

                            runBlocking {
                                githubClient.analyzeGitHubRepository(githubUrl, githubBranch)
                            }
                        }
                    }

                    // Legacy code for backward compatibility
                    val analyzerService = ProjectAnalyzerService()
                    val projectInfo = analyzerService.analyzeProject(project)
                    val projectInfoStr = analyzerService.formatProjectInfoForPrompt(projectInfo)

                    indicator.text = "Connecting to Claude Sonnet 4.5..."
                    indicator.fraction = 0.3

                    val claudeService = ClaudeService()

                    when (choice) {
                        0 -> generateDockerfile(mcpContext, project, indicator)
                        1 -> generateGitHubActionsWorkflow(mcpContext, project, indicator)
                        2 -> generateDockerComposeWithMCP(mcpContext, project, indicator)
                        3 -> generateKubernetesManifests(claudeService, projectInfoStr, project, indicator)
                        4 -> generateAllFiles(mcpContext, claudeService, projectInfoStr, project, indicator)
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
        mcpContext: MCPContext,
        project: com.intellij.openapi.project.Project,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Generating Dockerfile with Claude 4 Sonnet..."
        indicator.fraction = 0.5

        indicator.text = "Calling AWS Bedrock with MCP context..."
        indicator.fraction = 0.7

        // Use BedrockClient with architecture detection and prefill
        val bedrockClient = BedrockClient()
        val mcpContextJson = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mcpContext)
        val dockerfile = bedrockClient.generateDockerfile(mcpContextJson, usePrefill = true)
        bedrockClient.close()

        saveToFile(project, "Dockerfile", dockerfile)

        indicator.text = "Dockerfile generated!"
        indicator.fraction = 0.9
    }

    /**
     * Generates GitHub Actions workflow file using MCP context and BedrockClient.
     */
    private fun generateGitHubActionsWorkflow(
        mcpContext: MCPContext,
        project: com.intellij.openapi.project.Project,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Generating GitHub Actions workflow..."
        indicator.fraction = 0.3

        val bedrockClient = BedrockClient()
        val mcpContextJson = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mcpContext)
        val workflow = bedrockClient.generateGitHubActionsWorkflow(mcpContextJson)
        bedrockClient.close()

        indicator.text = "Writing .github/workflows/build.yml..."
        indicator.fraction = 0.7

        // Create .github/workflows directory
        val basePath = project.basePath ?: throw IllegalStateException("Project path not found")
        val workflowDir = File(basePath, ".github/workflows")
        workflowDir.mkdirs()

        // Write workflow file
        val workflowFile = File(workflowDir, "build.yml")
        workflowFile.writeText(workflow)

        indicator.text = "GitHub Actions workflow created successfully!"
        indicator.fraction = 1.0

        println("GitHub Actions workflow created at: ${workflowFile.absolutePath}")
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
    
    private fun generateDockerComposeWithMCP(
        mcpContext: MCPContext,
        project: com.intellij.openapi.project.Project,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Generating docker-compose.yml with MCP context..."
        indicator.fraction = 0.5
        
        val bedrockClient = BedrockClient()
        val mcpContextJson = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mcpContext)
        
        indicator.text = "Detecting services (database, cache, messaging)..."
        indicator.fraction = 0.6
        
        val compose = bedrockClient.generateDockerCompose(mcpContextJson)
        bedrockClient.close()
        
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
        mcpContext: MCPContext,
        claudeService: ClaudeService,
        projectInfo: String,
        project: com.intellij.openapi.project.Project,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Generating Dockerfile with MCP context..."
        indicator.fraction = 0.2

        // Use BedrockClient for Dockerfile generation
        val bedrockClient = BedrockClient()
        val mcpContextJson = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mcpContext)
        val dockerfile = bedrockClient.generateDockerfile(mcpContextJson, usePrefill = true)
        bedrockClient.close()

        saveToFile(project, "Dockerfile", dockerfile)

        indicator.text = "Generating GitHub Actions workflow with MCP context..."
        indicator.fraction = 0.4
        generateGitHubActionsWorkflow(mcpContext, project, indicator)

        indicator.text = "Generating docker-compose.yml with MCP context..."
        indicator.fraction = 0.6
        val bedrockClient2 = BedrockClient()
        val compose = bedrockClient2.generateDockerCompose(mcpContextJson)
        bedrockClient2.close()
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

