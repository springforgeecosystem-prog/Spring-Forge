package org.springforge.cicdassistant.mcp.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * MCPProject represents project-level information extracted from build files.
 * Maps to MCP "project" resource type.
 *
 * @property name Project name (artifactId for Maven, rootProject.name for Gradle)
 * @property groupId Maven group ID or equivalent namespace
 * @property version Project version
 * @property buildTool Build tool being used ("maven" or "gradle")
 * @property javaVersion Java version the project targets
 * @property springBootVersion Spring Boot framework version
 * @property databaseType Database type (e.g., "postgresql", "mysql", "mongodb")
 * @property dependencies List of project dependencies in format "groupId:artifactId:version"
 * @property modules List of multi-module project modules (for Maven/Gradle multi-module projects)
 */
data class MCPProject(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("group_id")
    val groupId: String,

    @JsonProperty("version")
    val version: String,

    @JsonProperty("build_tool")
    val buildTool: String,

    @JsonProperty("java_version")
    val javaVersion: String? = null,

    @JsonProperty("spring_boot_version")
    val springBootVersion: String? = null,

    @JsonProperty("database_type")
    val databaseType: String? = null,

    @JsonProperty("dependencies")
    val dependencies: List<String> = emptyList(),

    @JsonProperty("modules")
    val modules: List<String> = emptyList()
)
