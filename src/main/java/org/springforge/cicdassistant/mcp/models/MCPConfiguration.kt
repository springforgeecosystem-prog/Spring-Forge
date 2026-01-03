package org.springforge.cicdassistant.mcp.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * MCPConfiguration represents Spring Boot application configuration extracted from
 * application.properties or application.yml files.
 * Maps to MCP "configuration" resource type.
 *
 * @property serverPort Server port the application runs on (default: 8080)
 * @property datasourceUrl JDBC connection URL for the database
 * @property datasourceDriver JDBC driver class name
 * @property datasourceUsername Database username (sensitive - should be filtered)
 * @property datasourcePassword Database password (sensitive - should be filtered)
 * @property activeProfiles Active Spring profiles (e.g., "dev", "prod")
 * @property additionalProperties Map of other configuration properties
 */
data class MCPConfiguration(
    @JsonProperty("server_port")
    val serverPort: String? = null,

    @JsonProperty("datasource_url")
    val datasourceUrl: String? = null,

    @JsonProperty("datasource_driver")
    val datasourceDriver: String? = null,

    @JsonProperty("datasource_username")
    val datasourceUsername: String? = null,

    @JsonProperty("datasource_password")
    val datasourcePassword: String? = null,

    @JsonProperty("active_profiles")
    val activeProfiles: List<String> = emptyList(),

    @JsonProperty("additional_properties")
    val additionalProperties: Map<String, String> = emptyMap()
)
