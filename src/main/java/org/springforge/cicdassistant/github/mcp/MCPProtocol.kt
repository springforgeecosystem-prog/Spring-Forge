package org.springforge.cicdassistant.github.mcp

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * JSON-RPC 2.0 Request for Model Context Protocol (MCP)
 * Used to communicate with GitHub MCP Server via stdio
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MCPRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: Map<String, Any>? = null
)

/**
 * JSON-RPC 2.0 Response from MCP Server
 */
data class MCPResponse(
    val jsonrpc: String,
    val id: String,
    val result: Map<String, Any>?,
    val error: MCPError?
)

/**
 * JSON-RPC Error object
 */
data class MCPError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

/**
 * MCP Tool Call Parameters
 * Used when calling tools via tools/call method
 */
data class MCPToolCallParams(
    val name: String,
    val arguments: Map<String, Any>
)

/**
 * MCP Tool Definition
 * Returned by tools/list method
 */
data class MCPTool(
    val name: String,
    val description: String?,
    val inputSchema: Map<String, Any>?
)

// ==========================================
// GitHub-specific Tool Parameters
// ==========================================

/**
 * Parameters for repository_read:get_file_contents tool
 * Retrieves file content from a GitHub repository
 */
data class GetFileContentsParams(
    val owner: String,
    val repo: String,
    val path: String,
    val ref: String? = null  // Optional branch/commit SHA
)

/**
 * Parameters for code_read:search_code tool
 * Searches code within a repository
 */
data class SearchCodeParams(
    val owner: String,
    val repo: String,
    val query: String,
    val page: Int? = null,
    @JsonProperty("per_page") val perPage: Int? = null
)

/**
 * Parameters for repository_read:get tool
 * Gets repository metadata and information
 */
data class RepositoryInfoParams(
    val owner: String,
    val repo: String
)

/**
 * Parameters for repository_read:list_files tool
 * Lists files in a repository directory
 */
data class ListFilesParams(
    val owner: String,
    val repo: String,
    val path: String? = null,  // Optional directory path
    val ref: String? = null     // Optional branch/commit
)

/**
 * Parameters for issue_read:get tool
 * Gets a specific issue from a repository
 */
data class GetIssueParams(
    val owner: String,
    val repo: String,
    @JsonProperty("issue_number") val issueNumber: Int
)

/**
 * Parameters for pull_request_read:get tool
 * Gets a specific pull request
 */
data class GetPullRequestParams(
    val owner: String,
    val repo: String,
    @JsonProperty("pull_number") val pullNumber: Int
)

/**
 * Parameters for repository_read:search_repositories tool
 * Searches for repositories on GitHub
 */
data class SearchRepositoriesParams(
    val query: String,
    val sort: String? = null,  // stars, forks, updated
    val order: String? = null,  // asc, desc
    val page: Int? = null,
    @JsonProperty("per_page") val perPage: Int? = null
)

/**
 * Result container for file contents
 */
data class FileContentsResult(
    val content: String,
    val encoding: String? = null,
    val sha: String? = null,
    val size: Int? = null
)

/**
 * Result container for code search
 */
data class CodeSearchResult(
    @JsonProperty("total_count") val totalCount: Int,
    val items: List<CodeSearchItem>
)

/**
 * Individual code search result item
 */
data class CodeSearchItem(
    val name: String,
    val path: String,
    val sha: String,
    val url: String,
    @JsonProperty("html_url") val htmlUrl: String,
    val repository: RepositoryInfo? = null
)

/**
 * Repository information
 */
data class RepositoryInfo(
    val id: Long,
    val name: String,
    @JsonProperty("full_name") val fullName: String,
    val description: String? = null,
    val private: Boolean,
    @JsonProperty("html_url") val htmlUrl: String,
    val language: String? = null,
    @JsonProperty("stargazers_count") val stargazersCount: Int? = null,
    @JsonProperty("forks_count") val forksCount: Int? = null,
    @JsonProperty("default_branch") val defaultBranch: String? = null
)

/**
 * Exception thrown when MCP Server communication fails
 */
class MCPException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Exception thrown when GitHub MCP Server tool call fails
 */
class GitHubMCPToolException(
    val toolName: String,
    val errorCode: Int,
    message: String
) : RuntimeException("GitHub MCP tool '$toolName' failed (code $errorCode): $message")
