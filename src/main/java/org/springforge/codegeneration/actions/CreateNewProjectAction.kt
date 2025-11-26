package org.springforge.codegeneration.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.LocalFileSystem
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

        val params = buildParams(artifactId, packageRoot, deps)

        object : Task.Backgroundable(project, "Creating Spring Boot Project", false) {
            override fun run(indicator: ProgressIndicator) {

                val tmpZip = File(System.getProperty("java.io.tmpdir"), "springforge_${artifactId}.zip")

                try {
                    indicator.text = "Downloading starter from Spring Initializr..."
                    val client = SpringInitializrClient()
                    client.downloadStarterZip(params, tmpZip)

                    indicator.text = "Extracting starter..."

                    val base = project.basePath ?: System.getProperty("user.home")
                    val projectRootDir = File(base).parentFile.resolve(artifactId)

                    if (projectRootDir.exists()) {
                        val fallback = File(projectRootDir.parent, "${artifactId}_${System.currentTimeMillis()}")
                        fallback.mkdirs()
                        ZipUtils.unzip(tmpZip, fallback)
                        TemplateGenerator.generate(fallback, packageRoot, arch)
                    } else {
                        projectRootDir.mkdirs()
                        ZipUtils.unzip(tmpZip, projectRootDir)
                        TemplateGenerator.generate(projectRootDir, packageRoot, arch)
                    }

                    // Refresh correct directory
                    LocalFileSystem.getInstance()
                        .refreshAndFindFileByIoFile(projectRootDir.parentFile)
                        ?.refresh(true, true)

                    Notifications.Bus.notify(
                        Notification("SpringForge", "Project created",
                            "Created project $artifactId with $arch template",
                            NotificationType.INFORMATION
                        ),
                        project
                    )

                } catch (ex: Exception) {
                    Notifications.Bus.notify(
                        Notification("SpringForge", "Creation failed",
                            ex.message ?: "Unknown error",
                            NotificationType.ERROR
                        ),
                        project
                    )
                } finally {
                    if (tmpZip.exists()) tmpZip.delete()
                }
            }
        }.queue()
    }

    private fun extractGroupId(pkg: String): String {
        return if (pkg.contains(".")) pkg.substringBeforeLast(".") else "com.example"
    }

    private fun buildParams(artifactId: String, packageRoot: String, deps: List<String>): String {

        val cleanedArtifact = artifactId.replace("[^a-zA-Z0-9_-]".toRegex(), "")
        val groupId = extractGroupId(packageRoot)

        val sb = StringBuilder()
        sb.append("type=maven-project")
        sb.append("&language=java")
        sb.append("&bootVersion=3.4.0")
        sb.append("&baseDir=").append(cleanedArtifact)
        sb.append("&groupId=").append(groupId)
        sb.append("&artifactId=").append(cleanedArtifact)

        if (deps.isNotEmpty()) {
            sb.append("&dependencies=").append(deps.joinToString(","))
        }

        return sb.toString()
    }
}
