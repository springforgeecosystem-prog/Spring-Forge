package org.springforge.cicdassistant.github

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springforge.cicdassistant.config.EnvironmentConfig
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

class GitHubApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class GitHubFileNotFoundException(path: String) : RuntimeException("File not found: $path")

/**
 * Direct GitHub REST API client — replaces the Docker-based GitHubMCPServerConnector.
 *
 * Uses GITHUB_PERSONAL_ACCESS_TOKEN from .env as a Bearer token.
 * No Docker required.
 */
class GitHubApiClient(
    private val token: String = EnvironmentConfig.GitHub.personalAccessToken
        ?: throw IllegalStateException(
            "GITHUB_PERSONAL_ACCESS_TOKEN not configured. Add it to your .env file."
        )
) {
    private val objectMapper = jacksonObjectMapper()
    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private fun apiBase(): String {
        val host = EnvironmentConfig.GitHub.host.trimEnd('/')
        return if (host == "https://github.com" || host.isBlank()) {
            "https://api.github.com"
        } else {
            "$host/api/v3"
        }
    }

    private fun buildRequest(url: String): HttpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()
            .build()

    private fun executeRequest(url: String): String {
        val response = httpClient.send(buildRequest(url), HttpResponse.BodyHandlers.ofString())
        val status = response.statusCode()
        val body = response.body()

        return when {
            status == 404 -> throw GitHubFileNotFoundException(url)
            status == 429 -> throw GitHubApiException("GitHub API rate limit exceeded. Try again later.")
            status == 403 && response.headers().firstValue("x-ratelimit-remaining").orElse("1") == "0" ->
                throw GitHubApiException("GitHub API rate limit exceeded. Try again later.")
            status in 200..299 -> body
            else -> throw GitHubApiException("GitHub API error $status for $url: ${body.take(300)}")
        }
    }

    /** Encode a file path, preserving `/` separators but encoding each segment. */
    private fun encodePath(path: String): String =
        path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }

    // -------------------------------------------------------------------------
    // Public API methods
    // -------------------------------------------------------------------------

    /**
     * Returns the default branch of the repository (e.g. "main" or "master").
     * Uses GET /repos/{owner}/{repo} which always includes a `default_branch` field.
     */
    fun getRepoDefaultBranch(owner: String, repo: String): String {
        val url = "${apiBase()}/repos/$owner/$repo"
        val body = executeRequest(url)
        val repoData: Map<String, Any> = objectMapper.readValue(body, object : TypeReference<Map<String, Any>>() {})
        return repoData["default_branch"] as? String ?: "main"
    }

    /**
     * Lists branch names for a repository (up to 100).
     * Uses GET /repos/{owner}/{repo}/branches
     */
    fun listBranches(owner: String, repo: String): List<String> {
        val url = "${apiBase()}/repos/$owner/$repo/branches?per_page=100"
        val body = executeRequest(url)
        val branches: List<Map<String, Any>> = objectMapper.readValue(
            body, object : TypeReference<List<Map<String, Any>>>() {}
        )
        return branches.mapNotNull { it["name"] as? String }
    }

    /**
     * Returns the decoded text content of a file in the repository.
     * Uses GET /repos/{owner}/{repo}/contents/{path}?ref={ref}
     *
     * The GitHub API returns content as base64 with embedded newlines (MIME format).
     * These newlines are stripped before decoding.
     */
    fun getFileContents(owner: String, repo: String, path: String, ref: String): String {
        val encodedPath = encodePath(path)
        val encodedRef = URLEncoder.encode(ref, "UTF-8")
        val url = "${apiBase()}/repos/$owner/$repo/contents/$encodedPath?ref=$encodedRef"
        val body = executeRequest(url)

        val fileData: Map<String, Any> = objectMapper.readValue(body, object : TypeReference<Map<String, Any>>() {})

        val encoding = fileData["encoding"] as? String
        val rawContent = fileData["content"] as? String

        if (rawContent == null) {
            // File larger than 1 MB — GitHub omits content and returns download_url instead
            val downloadUrl = fileData["download_url"] as? String
                ?: throw GitHubApiException("File content unavailable for $path (possibly too large)")
            return executeRequest(downloadUrl)
        }

        if (encoding != "base64") {
            throw GitHubApiException("Unexpected encoding '$encoding' for $path")
        }

        // Strip embedded newlines before Base64 decoding (GitHub uses MIME line breaks every 60 chars)
        val clean = rawContent.replace("\n", "").replace("\r", "").trim()
        return String(Base64.getDecoder().decode(clean), Charsets.UTF_8)
    }

    /**
     * Searches code in a repository for the given query string.
     * Uses GET /search/code?q={query}+repo:{owner}/{repo}
     * Returns a list of file paths that match.
     *
     * Note: GitHub Search API allows 30 requests/min for authenticated users.
     */
    fun searchCode(owner: String, repo: String, query: String): List<String> {
        val encodedQuery = URLEncoder.encode("$query repo:$owner/$repo", "UTF-8")
        val url = "${apiBase()}/search/code?q=$encodedQuery&per_page=30"
        val body = executeRequest(url)

        val searchData: Map<String, Any> = objectMapper.readValue(body, object : TypeReference<Map<String, Any>>() {})

        @Suppress("UNCHECKED_CAST")
        val items = searchData["items"] as? List<Map<String, Any>> ?: emptyList()
        return items.mapNotNull { it["path"] as? String }
    }
}
