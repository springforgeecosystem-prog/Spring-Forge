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

        // STEP 1 — SHOW DIALOG TO COLLECT PROJECT INFO
        val dlg = CreateProjectDialog()
        if (!dlg.showAndGet()) return

        val artifactId = dlg.getArtifactId()
        val packageRoot = dlg.getPackageRoot()
        val arch = dlg.getArchitecture()
        val deps = dlg.getDependencies()

        // STEP 2 — DIRECTORY CHOOSER (THIS FIXES EMPTY PROJECT ISSUE)
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Directory to Create New Spring Boot Project"

        val selectedFolder = FileChooser.chooseFile(descriptor, project, null) ?: return
        val targetDir = File(selectedFolder.path, artifactId)

        // Build Spring Initializr parameters
        val params = buildParams(artifactId, packageRoot, deps)

        // START BACKGROUND TASK
        object : Task.Backgroundable(project, "Creating Spring Boot Project...", false) {
            override fun run(indicator: ProgressIndicator) {

                val tmpZip = File(System.getProperty("java.io.tmpdir"), "springforge_${artifactId}.zip")

                try {
                    indicator.text = "Downloading Spring Boot Starter…"

                    val client = SpringInitializrClient()
                    client.downloadStarterZip(params, tmpZip)

                    indicator.text = "Extracting Project…"

                    // STEP 3 — FIXED EXTRACTION PROCESS
                    val tempUnzipDir = File(targetDir.parentFile, "${artifactId}_unzipped")
                    if (tempUnzipDir.exists()) tempUnzipDir.deleteRecursively()
                    tempUnzipDir.mkdirs()

                    // Unzip Spring Initializr output
                    ZipUtils.unzip(tmpZip, tempUnzipDir)

                    // Spring Initializr ZIP ALWAYS contains a root directory named: <artifactId>
                    val realProjectRoot = File(tempUnzipDir, artifactId)
                    if (!realProjectRoot.exists()) {
                        throw RuntimeException("Unexpected ZIP structure. Could not find folder: $artifactId")
                    }

                    // MOVE unzipped <artifactId> → targetDir (FIXES NESTED FOLDER ISSUE)
                    if (!targetDir.exists()) targetDir.mkdirs()
                    realProjectRoot.copyRecursively(targetDir, overwrite = true)

                    // CLEAN TEMP
                    tempUnzipDir.deleteRecursively()

                    indicator.text = "Applying Architecture Template…"

                    // STEP 4 — GENERATE ARCHITECTURE TEMPLATE CORRECTLY INSIDE PROJECT
                    TemplateGenerator.generate(targetDir, packageRoot, arch)

                    // Refresh FS
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir.parentFile)
                        ?.refresh(true, true)

                    indicator.text = "Opening project…"

                    // STEP 5 — OPEN NEW PROJECT (FIXES IDE SHOWING EMPTY WINDOW)
                    ApplicationManager.getApplication().invokeLater {
                        try {
                            val pm = ProjectManagerEx.getInstanceEx()
                            pm.openProject(
                                targetDir.toPath(),
                                OpenProjectTask.build()
                            )

                            Notifications.Bus.notify(
                                Notification(
                                    "SpringForge",
                                    "Project Opened",
                                    "Spring Boot project '$artifactId' created and opened.",
                                    NotificationType.INFORMATION
                                ),
                                project
                            )

                        } catch (ex: Exception) {
                            Notifications.Bus.notify(
                                Notification(
                                    "SpringForge",
                                    "Open Project Failed",
                                    "Created project but could not open it: ${ex.message}",
                                    NotificationType.WARNING
                                ),
                                project
                            )
                        }
                    }

                } catch (ex: Exception) {

                    Notifications.Bus.notify(
                        Notification(
                            "SpringForge",
                            "Project Creation Failed",
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

    // GroupId extractor
    private fun extractGroupId(pkg: String): String =
        if (pkg.contains(".")) pkg.substringBeforeLast(".") else "com.example"

    // Build Spring Initializr URL params
    private fun buildParams(artifactId: String, packageRoot: String, deps: List<String>): String {

        val cleanedArtifact = artifactId.replace("[^a-zA-Z0-9_-]".toRegex(), "")
        val groupId = extractGroupId(packageRoot)

        val sb = StringBuilder()
        sb.append("type=maven-project")
        sb.append("&language=java")
        sb.append("&bootVersion=3.4.0") // VALID SPRING BOOT VERSION
        sb.append("&baseDir=").append(cleanedArtifact)
        sb.append("&groupId=").append(groupId)
        sb.append("&artifactId=").append(cleanedArtifact)

        if (deps.isNotEmpty())
            sb.append("&dependencies=").append(deps.joinToString(","))

        return sb.toString()
    }
}
