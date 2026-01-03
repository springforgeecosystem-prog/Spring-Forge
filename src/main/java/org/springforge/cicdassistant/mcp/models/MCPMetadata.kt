package org.springforge.cicdassistant.mcp.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * MCPMetadata contains analysis timestamp and versioning information.
 * Follows MCP standard for metadata structure.
 *
 * @property analysisTimestamp ISO-8601 formatted timestamp of when the analysis was performed
 * @property pluginVersion Version of the SpringForge plugin that performed the analysis
 * @property mcpVersion Version of the MCP specification being used (currently "1.0")
 * @property architectureType Type of project architecture ("SPRING_BOOT" or "INTELLIJ_PLUGIN")
 * @property defaultBranch Default branch name for the repository (e.g., "main", "master")
 */
data class MCPMetadata(
    @JsonProperty("analysis_timestamp")
    val analysisTimestamp: String = Instant.now().toString(),

    @JsonProperty("plugin_version")
    val pluginVersion: String = "1.0-SNAPSHOT",

    @JsonProperty("mcp_version")
    val mcpVersion: String = "1.0",

    @JsonProperty("architecture_type")
    val architectureType: String = "SPRING_BOOT",

    @JsonProperty("default_branch")
    val defaultBranch: String = "main"
)
