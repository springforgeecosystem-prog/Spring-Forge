package org.springforge.codegeneration.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.LocalFileSystem
import org.springforge.codegeneration.service.ArchitectureTemplate
import org.springforge.codegeneration.service.SpringInitializrClient
import org.springforge.codegeneration.service.TemplateGenerator
import org.springforge.codegeneration.ui.CreateProjectDialog
import org.springforge.codegeneration.utils.ZipUtils
import java.io.File

class CreateNewProjectAction : AnAction("SpringForge: Create New Spring Boot Project") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val dlg = CreateProjectDialog()
        if (!dlg.showAndGet()) return

        val artifactId = dlg.getArtifactId()
        val packageRoot = dlg.getPackageRoot()
        val arch = dlg.getArchitecture()
        val deps = dlg.getDependencies()

        // Build params
        val params = buildParams(artifactId, packageRoot, deps)

        // run in background to download and extract
        object : Task.Backgroundable(project, "Creating Spring Boot Project", false) {
            override fun run(indicator: ProgressIndicator) {
                val tmpZip = File(System.getProperty("java.io.tmpdir"), "springforge_${artifactId}.zip")
                try {
                    indicator.text = "Downloading starter from Spring Initializr..."
                    val client = SpringInitializrClient()
                    client.downloadStarterZip(params, tmpZip)

                    indicator.text = "Extracting starter..."
                    val projectRootDir = File(project.basePath, artifactId)
                    if (projectRootDir.exists()) {
                        // if already exists, append timestamp
                        val fallback = File(project.basePath, "${artifactId}_${System.currentTimeMillis()}")
                        fallback.mkdirs()
                        ZipUtils.unzip(tmpZip, fallback)
                        // generate templates into fallback
                        TemplateGenerator.generate(fallback, packageRoot, when (arch) {
                            ArchitectureTemplate.LAYERED -> ArchitectureTemplate.LAYERED
                            ArchitectureTemplate.MVC -> ArchitectureTemplate.MVC
                            ArchitectureTemplate.CLEAN -> ArchitectureTemplate.CLEAN
                        })
                    } else {
                        projectRootDir.mkdirs()
                        ZipUtils.unzip(tmpZip, projectRootDir)
                        TemplateGenerator.generate(projectRootDir, packageRoot, when (arch) {
                            ArchitectureTemplate.LAYERED -> ArchitectureTemplate.LAYERED
                            ArchitectureTemplate.MVC -> ArchitectureTemplate.MVC
                            ArchitectureTemplate.CLEAN -> ArchitectureTemplate.CLEAN
                        })
                    }

                    // refresh VFS
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(project.basePath))
                        ?.refresh(true, true)

                    Notifications.Bus.notify(
                        Notification("SpringForge", "Project created", "Created project $artifactId with $arch template", NotificationType.INFORMATION),
                        project
                    )
                } catch (ex: Exception) {
                    Notifications.Bus.notify(
                        Notification("SpringForge", "Creation failed", ex.message ?: "Unknown error", NotificationType.ERROR),
                        project
                    )
                } finally {
                    if (tmpZip.exists()) tmpZip.delete()
                }
            }
        }.queue()
    }

    private fun buildParams(artifactId: String, packageRoot: String, deps: List<String>): String {
        val sb = StringBuilder()
        sb.append("type=maven-project")
        sb.append("&language=java")
        sb.append("&bootVersion=3.2.4") // you can make this selectable later
        sb.append("&baseDir=").append(artifactId)
        sb.append("&groupId=").append(packageRoot.substringBeforeLast('.', "com.example"))
        sb.append("&artifactId=").append(artifactId)
        if (deps.isNotEmpty()) {
            sb.append("&dependencies=").append(deps.joinToString(","))
        }
        return sb.toString()
    }
}