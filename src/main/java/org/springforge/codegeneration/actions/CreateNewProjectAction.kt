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
        val bootVersion = dlg.getBootVersion()

        // STEP 2 — DIRECTORY CHOOSER
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Directory to Create New Spring Boot Project"

        val selectedFolder = FileChooser.chooseFile(descriptor, project, null) ?: return
        val targetDir = File(selectedFolder.path, artifactId)

        // Build Params
        val params = buildParams(artifactId, packageRoot, deps, javaVersion, buildTool, bootVersion)

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

                    // Post-process: Lombok version fix + annotation processing
                    indicator.text = "Fixing build configuration..."
                    fixLombokAnnotationProcessor(targetDir, buildTool)
                    enableAnnotationProcessing(targetDir)

                    indicator.text = "Applying Architecture Template..."
                    // Use the package Spring Initializr actually placed the app class in,
                    // which may differ from user input if Initializr sanitized the name.
                    val effectivePackage = detectActualPackage(targetDir) ?: packageRoot
                    TemplateGenerator.generate(targetDir, effectivePackage, arch)

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
                                    "Project Created",
                                    "Spring Boot project '$artifactId' created successfully!\n" +
                                    "Annotation processing has been auto-enabled.\n" +
                                    "Tip: If IDE still shows errors, do Maven → Reload All Maven Projects.",
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

    private fun detectActualPackage(projectRoot: File): String? {
        val srcMainJava = File(projectRoot, "src/main/java")
        if (!srcMainJava.exists()) return null
        val appFile = srcMainJava.walkTopDown()
            .firstOrNull { it.isFile && it.name.endsWith("Application.java") }
            ?: return null
        val packageDir = appFile.parentFile ?: return null
        return packageDir.relativeTo(srcMainJava).path.replace(File.separatorChar, '.')
    }

    private fun extractGroupId(pkg: String): String =
        if (pkg.contains(".")) pkg.substringBeforeLast(".") else "com.example"

    private fun buildParams(
        artifactId: String,
        packageRoot: String,
        deps: List<String>,
        javaVersion: String,
        buildTool: String,
        bootVersion: String
    ): String {

        val cleanedArtifact = artifactId.replace("[^a-zA-Z0-9_-]".toRegex(), "")
        val groupId = extractGroupId(packageRoot)

        val sb = StringBuilder()
        sb.append("type=").append(buildTool)
        sb.append("&language=java")
        sb.append("&javaVersion=").append(javaVersion)
        sb.append("&bootVersion=").append(bootVersion)
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

    // ═══════════════════════════════════════════════════════════
    //  POM.XML POST-PROCESSING
    // ═══════════════════════════════════════════════════════════

    /**
     * Ensures the Lombok annotation processor in pom.xml has an explicit version.
     *
     * Spring Initializr generates the annotation processor path without a version:
     *   <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></path>
     *
     * This can cause issues because Maven's maven-compiler-plugin may not resolve
     * the version from the Spring Boot BOM for annotation processor paths.
     * We fix this by adding <version>${lombok.version}</version> to the path.
     */
    /**
     * Creates .idea/compiler.xml with annotation processing enabled.
     * This eliminates the need for users to manually enable it in IntelliJ settings.
     * Without this, Lombok-generated methods (getters, setters, builders, constructors)
     * won't be recognized by IntelliJ's code analyzer, causing false errors.
     */
    private fun enableAnnotationProcessing(projectDir: File) {
        val ideaDir = File(projectDir, ".idea")
        if (!ideaDir.exists()) ideaDir.mkdirs()

        val compilerXml = File(ideaDir, "compiler.xml")
        compilerXml.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="CompilerConfiguration">
    <annotationProcessing>
      <profile default="true" name="Default" enabled="true">
        <processorPath useClasspath="true" />
      </profile>
    </annotationProcessing>
  </component>
</project>
""".trimStart()
        )
    }

    private fun fixLombokAnnotationProcessor(projectDir: File, buildTool: String) {
        // Only applies to Maven projects
        if (!buildTool.contains("maven")) return

        val pomFile = File(projectDir, "pom.xml")
        if (!pomFile.exists()) return

        var content = pomFile.readText()

        // Pattern: <path> with lombok artifactId but NO <version> tag
        // We need to add <version>${lombok.version}</version> after the <artifactId>lombok</artifactId>
        val lombokPathWithoutVersion = Regex(
            """(<annotationProcessorPaths>\s*\n\s*<path>\s*\n\s*<groupId>org\.projectlombok</groupId>\s*\n\s*<artifactId>lombok</artifactId>)\s*\n(\s*</path>)"""
        )

        if (lombokPathWithoutVersion.containsMatchIn(content)) {
            content = lombokPathWithoutVersion.replace(content) { match ->
                "${match.groupValues[1]}\n\t\t\t\t\t\t\t<version>\${lombok.version}</version>\n${match.groupValues[2]}"
            }
            pomFile.writeText(content)
        }
    }
}