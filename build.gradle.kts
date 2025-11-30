plugins {
    kotlin("jvm") version "1.9.23"
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

    // AWS Bedrock Dependencies - Use platform() for BOM
    implementation(platform("software.amazon.awssdk:bom:2.39.1"))
    implementation("software.amazon.awssdk:bedrockruntime")
    implementation("software.amazon.awssdk:auth")

    // AST Analysis - JavaParser for Java code analysis
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.25.7")

}

intellij {
    version.set("IU-2024.3") // 👈 IU = IntelliJ Ultimate Edition
    type.set("IU")
    plugins.set(listOf("java", "Spring")) // Spring plugin is included here

    // Disable instrumentation to avoid JDK path issues during testing
    instrumentCode.set(false)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }

    buildSearchableOptions {
        enabled = false // Disable to speed up build and avoid sandbox issues
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

