package org.springforge.codegeneration.service

import java.io.File

enum class ArchitectureTemplate {
    LAYERED,
    MVC,
    CLEAN
}

object TemplateGenerator {

    /**
     * Create folders for selected architecture under src/main/java/<packagePath>.
     * basePath should be the project root (where pom.xml or build.gradle is located).
     * packageRoot e.g. com.example.demo -> converted to com/example/demo
     */
    fun generate(projectRoot: File, packageRoot: String, arch: ArchitectureTemplate) {
        val srcMainJava = File(projectRoot, "src/main/java")
        val packageDir = File(srcMainJava, packageRoot.replace('.', File.separatorChar))
        packageDir.mkdirs()

        when (arch) {
            ArchitectureTemplate.LAYERED -> createLayered(packageDir)
            ArchitectureTemplate.MVC -> createMVC(packageDir)
            ArchitectureTemplate.CLEAN -> createClean(packageDir)
        }

        // create resources folder
        val resources = File(projectRoot, "src/main/resources")
        resources.mkdirs()
    }

    private fun createLayered(packageDir: File) {
        File(packageDir, "controller").mkdirs()
        File(packageDir, "service").mkdirs()
        File(packageDir, "repository").mkdirs()
        File(packageDir, "entity").mkdirs()
        File(packageDir, "dto").mkdirs()
        // create marker README files to make structure visible in VFS
        File(packageDir, "README_LAYERED.txt").writeText("Layered architecture folders: controller, service, repository, entity, dto")
    }

    private fun createMVC(packageDir: File) {
        File(packageDir, "controller").mkdirs()
        File(packageDir, "service").mkdirs()
        File(packageDir, "model").mkdirs()
        File(packageDir, "view").mkdirs()
        File(packageDir, "README_MVC.txt").writeText("MVC architecture folders: controller, service, model, view")
    }

    private fun createClean(packageDir: File) {
        File(packageDir, "domain").mkdirs()
        File(packageDir, "usecase").mkdirs()
        File(packageDir, "adapter").mkdirs()
        File(packageDir, "infrastructure").mkdirs()
        File(packageDir, "README_CLEAN.txt").writeText("Clean/Hexagonal architecture folders: domain, usecase, adapter, infrastructure")
    }
}