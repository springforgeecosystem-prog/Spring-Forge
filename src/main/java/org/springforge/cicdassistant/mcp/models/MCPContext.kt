package org.springforge.cicdassistant.mcp.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * MCPContext is the root container for all project context packaged in MCP format.
 * This is the main data structure that will be serialized and sent to AWS Bedrock.
 *
 * The MCP (Model Context Protocol) format provides a standardized way to package
 * project information for LLM consumption, enabling:
 * - Consistent structure across different project types
 * - Easy serialization/deserialization
 * - Clear separation of concerns (project metadata, configuration, code structure)
 * - Extensibility for future resource types
 *
 * @property metadata Analysis metadata (timestamp, versions)
 * @property project Project-level information (name, version, dependencies)
 * @property configuration Application configuration (ports, database, profiles)
 * @property codeStructure Code structure analysis (controllers, services, repositories)
 * @property rawFiles Optional map of file paths to file contents for direct file access
 */
data class MCPContext(
    @JsonProperty("metadata")
    val metadata: MCPMetadata,

    @JsonProperty("project")
    val project: MCPProject,

    @JsonProperty("configuration")
    val configuration: MCPConfiguration? = null,

    @JsonProperty("code_structure")
    val codeStructure: MCPCodeStructure? = null,

    @JsonProperty("raw_files")
    val rawFiles: Map<String, String> = emptyMap()
) {
    /**
     * Calculates the approximate size of this context in characters.
     * Useful for estimating token count before sending to LLM.
     *
     * @return Approximate size in characters
     */
    fun estimateSizeInCharacters(): Int {
        return toString().length
    }

    /**
     * Estimates the token count for this context.
     * Uses approximation: 4 characters ≈ 1 token (Claude/GPT tokenization rule of thumb)
     *
     * @return Estimated token count
     */
    fun estimateTokenCount(): Int {
        return estimateSizeInCharacters() / 4
    }
}
