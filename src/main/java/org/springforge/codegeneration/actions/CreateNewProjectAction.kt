package org.springforge.codegeneration.actions

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import org.springforge.codegeneration.service.SpringInitializrClient
import org.springforge.codegeneration.service.TemplateGenerator
import org.springforge.codegeneration.ui.CreateProjectDialog
import org.springforge.codegeneration.utils.ZipUtils
import java.io.File

class CreateNewProjectAction : AnAction("SpringForge: Create New Spring Boot Project") {

    override fun actionPerformed(e: AnActionEvent) {

        val project = e.project ?: return

        // STEP 1 — SHOW DIALOG
        val dlg = CreateProjectDialog()
        if (!dlg.showAndGet()) return

        val artifactId = dlg.getArtifactId()
        val packageRoot = dlg.getPackageRoot()
        val arch = dlg.getArchitecture()
        val deps = dlg.getDependencies()
        val javaVersion = dlg.getJavaVersion()
        val buildTool = dlg.getBuildTool()
        // Note: bootVersion is removed from here

        // STEP 2 — DIRECTORY CHOOSER
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Directory to Create New Spring Boot Project"

        val selectedFolder = FileChooser.chooseFile(descriptor, project, null) ?: return
        val targetDir = File(selectedFolder.path, artifactId)

        // Build Params (No bootVersion passed)
        val params = buildParams(artifactId, packageRoot, deps, javaVersion, buildTool)

        // START BACKGROUND TASK
        object : Task.Backgroundable(project, "Creating Spring Boot Project...", false) {
            override fun run(indicator: ProgressIndicator) {

                val tmpZip = File(System.getProperty("java.io.tmpdir"), "springforge_${artifactId}.zip")

                try {
                    indicator.text = "Downloading Spring Boot Starter..."

                    val client = SpringInitializrClient()
                    client.downloadStarterZip(params, tmpZip)

                    indicator.text = "Extracting Project..."

                    val tempUnzipDir = File(targetDir.parentFile, "${artifactId}_unzipped")
                    if (tempUnzipDir.exists()) tempUnzipDir.deleteRecursively()
                    tempUnzipDir.mkdirs()

                    ZipUtils.unzip(tmpZip, tempUnzipDir)

                    val realProjectRoot = File(tempUnzipDir, artifactId)
                    if (!realProjectRoot.exists()) {
                        // Fallback logic if zip structure differs
                        if (tempUnzipDir.listFiles()?.isNotEmpty() == true) {
                            tempUnzipDir.listFiles()?.first()?.copyRecursively(targetDir, true)
                        } else {
                            throw RuntimeException("Empty ZIP file received")
                        }
                    } else {
                        if (!targetDir.exists()) targetDir.mkdirs()
                        realProjectRoot.copyRecursively(targetDir, overwrite = true)
                    }

                    tempUnzipDir.deleteRecursively()

                    indicator.text = "Applying Architecture Template..."
                    TemplateGenerator.generate(targetDir, packageRoot, arch)

                    indicator.text = "Syncing filesystem..."
                    val targetVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir)
                    targetVFile?.refresh(false, true)

                    indicator.text = "Opening project..."

                    ApplicationManager.getApplication().invokeLater {
                        try {
                            val pm = ProjectManagerEx.getInstanceEx()
                            pm.openProject(targetDir.toPath(), OpenProjectTask.build())

                            Notifications.Bus.notify(
                                Notification(
                                    "SpringForge",
                                    "Project Opened",
                                    "Spring Boot project '$artifactId' created.",
                                    NotificationType.INFORMATION
                                ),
                                project
                            )

                        } catch (ex: Exception) {
                            Notifications.Bus.notify(
                                Notification("SpringForge", "Open Project Failed", ex.message ?: "Error", NotificationType.WARNING),
                                project
                            )
                        }
                    }

                } catch (ex: Exception) {
                    Notifications.Bus.notify(
                        Notification("SpringForge", "Project Creation Failed", ex.message ?: "Unknown error", NotificationType.ERROR),
                        project
                    )
                } finally {
                    if (tmpZip.exists()) tmpZip.delete()
                }
            }
        }.queue()
    }

    private fun extractGroupId(pkg: String): String =
        if (pkg.contains(".")) pkg.substringBeforeLast(".") else "com.example"

    private fun buildParams(
        artifactId: String,
        packageRoot: String,
        deps: List<String>,
        javaVersion: String,
        buildTool: String
    ): String {

        val cleanedArtifact = artifactId.replace("[^a-zA-Z0-9_-]".toRegex(), "")
        val groupId = extractGroupId(packageRoot)

        val sb = StringBuilder()
        sb.append("type=").append(buildTool)
        sb.append("&language=java")
        sb.append("&javaVersion=").append(javaVersion)

        // REMOVED: sb.append("&bootVersion=...")
        // By omitting this, the server automatically selects the latest stable version.

        sb.append("&baseDir=").append(cleanedArtifact)
        sb.append("&groupId=").append(groupId)
        sb.append("&artifactId=").append(cleanedArtifact)
        sb.append("&name=").append(cleanedArtifact)
        sb.append("&description=Demo project")
        sb.append("&packageName=").append(packageRoot)
        sb.append("&packaging=jar")

        if (deps.isNotEmpty()) {
            sb.append("&dependencies=").append(deps.joinToString(","))
        }

        return sb.toString()
    }
}