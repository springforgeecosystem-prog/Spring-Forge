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
        File(packageDir, "service${File.separator}impl").mkdirs()
        File(packageDir, "repository").mkdirs()
        File(packageDir, "entity").mkdirs()
        File(packageDir, "dto").mkdirs()
        File(packageDir, "exception").mkdirs()
        File(packageDir, "config").mkdirs()
        File(packageDir, "README_LAYERED.txt").writeText(
            "Layered architecture folders: controller, service, service/impl, repository, entity, dto, exception, config"
        )
        createPackageInfo(packageDir)
    }

    private fun createMVC(packageDir: File) {
        File(packageDir, "controller").mkdirs()
        File(packageDir, "model").mkdirs()
        File(packageDir, "service").mkdirs()
        File(packageDir, "view").mkdirs()
        File(packageDir, "README_MVC.txt").writeText(
            "MVC architecture folders: controller, model, service, view"
        )
        createPackageInfo(packageDir)
    }

    private fun createClean(packageDir: File) {
        File(packageDir, "domain${File.separator}model").mkdirs()
        File(packageDir, "domain${File.separator}port${File.separator}in").mkdirs()
        File(packageDir, "domain${File.separator}port${File.separator}out").mkdirs()
        File(packageDir, "application${File.separator}service").mkdirs()
        File(packageDir, "adapter${File.separator}in${File.separator}web").mkdirs()
        File(packageDir, "adapter${File.separator}out${File.separator}persistence").mkdirs()
        File(packageDir, "infrastructure${File.separator}config").mkdirs()
        File(packageDir, "README_CLEAN.txt").writeText(
            "Clean/Hexagonal architecture folders:\n" +
            "  domain/model       - Domain entities and value objects\n" +
            "  domain/port/in     - Input ports (use case interfaces)\n" +
            "  domain/port/out    - Output ports (repository interfaces)\n" +
            "  application/service - Use case implementations\n" +
            "  adapter/in/web     - REST controllers (driving adapters)\n" +
            "  adapter/out/persistence - Repository implementations (driven adapters)\n" +
            "  infrastructure/config   - Configuration classes"
        )
        createPackageInfo(packageDir)
    }

    private fun createPackageInfo(packageDir: File) {
        // create a package-info.java placeholder in base package to show package in IDE
        val pi = File(packageDir, "package-info.java")
        if (!pi.exists()) {
            pi.writeText("/** Generated package-info placeholder for SpringForge template */")
        }
    }
}