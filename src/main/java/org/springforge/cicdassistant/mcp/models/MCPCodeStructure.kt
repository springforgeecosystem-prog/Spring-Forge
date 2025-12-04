package org.springforge.cicdassistant.mcp.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * MCPCodeStructure represents the analyzed code structure of the Spring Boot project.
 * Maps to MCP "code_structure" resource type.
 *
 * @property controllers List of REST controller classes with their endpoints
 * @property services List of service classes
 * @property repositories List of repository/DAO classes
 * @property entities List of entity/model classes
 * @property restEndpoints Detailed information about REST API endpoints
 */
data class MCPCodeStructure(
    @JsonProperty("controllers")
    val controllers: List<String> = emptyList(),

    @JsonProperty("services")
    val services: List<String> = emptyList(),

    @JsonProperty("repositories")
    val repositories: List<String> = emptyList(),

    @JsonProperty("entities")
    val entities: List<String> = emptyList(),

    @JsonProperty("rest_endpoints")
    val restEndpoints: List<RestEndpoint> = emptyList()
) {
    /**
     * Represents a single REST API endpoint.
     *
     * @property httpMethod HTTP method (GET, POST, PUT, DELETE, etc.)
     * @property path URL path/route
     * @property controller Controller class that handles this endpoint
     * @property method Method name that implements the endpoint
     */
    data class RestEndpoint(
        @JsonProperty("http_method")
        val httpMethod: String,

        @JsonProperty("path")
        val path: String,

        @JsonProperty("controller")
        val controller: String,

        @JsonProperty("method")
        val method: String
    )
}
