plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.10"
    id("org.jetbrains.intellij") version "1.17.0"
}

group = "org.SpringForge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // AST Analysis - JavaParser for Java code analysis
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.25.7")

    // MCP Protocol & GitHub MCP Server Integration
    // OkHttp already included above for HTTP communication
    // JSON-RPC 2.0 communication (using Jackson which is already included)

    // Kotlin Coroutines for async GitHub operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")

    // HTML → PDF rendering for the Explainability Report export
    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")

    // PostgreSQL JDBC driver for Audit Logging
    implementation("org.postgresql:postgresql:42.7.3")

    //
    implementation("org.json:json:20231013")

    // Unit Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.23")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
    // IntelliJ Gradle plugin injects its test framework which requires JUnit 4 runner classes at runtime
    testRuntimeOnly("junit:junit:4.13.2")

}

intellij {
    version.set("IU-2024.3") // 👈 IU = IntelliJ Ultimate Edition
    type.set("IU")
    plugins.set(listOf("java", "Spring")) // Spring plugin is included here

    // Disable instrumentation to avoid JDK path issues during testing
    instrumentCode.set(false)
}

tasks {
    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("253.*")   // supports up to 2025.3.x
    }

    // Bundle the .env file from project root into the JAR resources
    processResources {
        from(rootProject.file(".env")) {
            into("") // place at root of resources → accessible via classLoader.getResource(".env")
        }
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    buildSearchableOptions {
        enabled = false // Disable to speed up build and avoid sandbox issues
    }

    test {
        useJUnitPlatform()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

