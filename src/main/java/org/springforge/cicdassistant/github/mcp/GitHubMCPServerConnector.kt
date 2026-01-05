package org.springforge.cicdassistant.github.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springforge.cicdassistant.config.EnvironmentConfig
import java.io.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages GitHub MCP Server Docker container lifecycle and JSON-RPC communication
 *
 * This connector handles:
 * - Starting GitHub MCP Server in a Docker container
 * - JSON-RPC 2.0 communication via stdio (stdin/stdout)
 * - Tool invocation (repository_read, code_read, etc.)
 * - Error handling and connection lifecycle
 *
 * Usage:
 * ```kotlin
 * val connector = GitHubMCPServerConnector()
 * try {
 *     connector.connect()
 *     val result = connector.callTool("repository_read:get_file_contents", mapOf(
 *         "owner" to "spring-projects",
 *         "repo" to "spring-boot",
 *         "path" to "pom.xml"
 *     ))
 *     println(result["content"])
 * } finally {
 *     connector.disconnect()
 * }
 * ```
 */
class GitHubMCPServerConnector {

    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val requestIdCounter = AtomicInteger(0)

    private var process: Process? = null
    private var outputReader: BufferedReader? = null
    private var inputWriter: BufferedWriter? = null
    private var errorReader: BufferedReader? = null

    @Volatile
    private var isConnected = false

    /**
     * Start GitHub MCP Server in Docker container using stdio communication
     *
     * Prerequisites:
     * - Docker Desktop must be installed and running
     * - GITHUB_PERSONAL_ACCESS_TOKEN must be configured in .env
     *
     * @throws IllegalStateException if GitHub PAT is not configured
     * @throws MCPException if Docker process fails to start
     */
    fun connect() {
        if (isConnected) {
            println("GitHub MCP Server already connected")
            return
        }

        val token = EnvironmentConfig.GitHub.personalAccessToken
            ?: throw IllegalStateException(
                "GITHUB_PERSONAL_ACCESS_TOKEN not configured. " +
                "Please add it to your .env file."
            )

        val dockerCommand = buildDockerCommand(token)

        try {
            println("Starting GitHub MCP Server via Docker...")
            println("Command: ${dockerCommand.joinToString(" ").replace(token, "***")}")

            val processBuilder = ProcessBuilder(dockerCommand)
            processBuilder.redirectErrorStream(false)

            process = processBuilder.start()

            outputReader = BufferedReader(InputStreamReader(process!!.inputStream))
            inputWriter = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            errorReader = BufferedReader(InputStreamReader(process!!.errorStream))

            // Start error reader thread to capture Docker/MCP errors
            startErrorReaderThread()

            // Wait for server to be ready (give it time to initialize)
            println("  Waiting for server to initialize...")
            Thread.sleep(2000) // 2 seconds should be enough

            // Verify process is still running
            if (!process!!.isAlive) {
                throw MCPException("GitHub MCP Server process died immediately after starting")
            }

            // MCP Protocol: Send 'initialize' request (required before tools/call)
            println("  Sending MCP 'initialize' request...")
            val initRequest = MCPRequest(
                id = "init-1",
                method = "initialize",
                params = mapOf(
                    "protocolVersion" to "2024-11-05",
                    "capabilities" to mapOf<String, Any>(),
                    "clientInfo" to mapOf(
                        "name" to "SpringForge-CICD-Assistant",
                        "version" to "1.0.0"
                    )
                )
            )
            val initJson = mapper.writeValueAsString(initRequest)
            inputWriter?.write(initJson)
            inputWriter?.write("\n")
            inputWriter?.flush()

            // Read initialize response
            val initResponse = outputReader?.readLine()
            if (initResponse == null) {
                throw MCPException("Server not responding to 'initialize' request. Server may not be ready.")
            }
            println("  ✓ Received 'initialize' response")

            // MCP Protocol: Send 'initialized' notification (no response expected)
            println("  Sending MCP 'initialized' notification...")
            val initializedNotification = mapOf(
                "jsonrpc" to "2.0",
                "method" to "notifications/initialized"
            )
            val notificationJson = mapper.writeValueAsString(initializedNotification)
            inputWriter?.write(notificationJson)
            inputWriter?.write("\n")
            inputWriter?.flush()

            // Give server time to fully initialize after the notification
            Thread.sleep(1000)

            // Verify process is still alive after initialization
            if (!process!!.isAlive) {
                val exitCode = process!!.exitValue()
                throw MCPException(
                    "GitHub MCP Server process died after initialization sequence (exit code: $exitCode)\n" +
                    "Recent error messages:\n${getRecentErrors()}"
                )
            }

            println("  ✓ MCP session initialized successfully")

            isConnected = true
            println("✓ GitHub MCP Server connected successfully")

        } catch (e: IOException) {
            val errorMessage = "Failed to start GitHub MCP Server Docker container. " +
                "Is Docker running? Error: ${e.message}"
            throw MCPException(errorMessage, e)
        } catch (e: Exception) {
            throw MCPException("Unexpected error starting GitHub MCP Server: ${e.message}", e)
        }
    }

    /**
     * Build Docker command with environment variables
     */
    private fun buildDockerCommand(token: String): List<String> {
        val toolsets = EnvironmentConfig.GitHub.mcpToolsets
        val readOnly = if (EnvironmentConfig.GitHub.readOnly) "1" else "0"
        val host = EnvironmentConfig.GitHub.host

        return listOf(
            "docker", "run",
            "-i",           // Interactive (keep stdin open)
            "--rm",         // Remove container after exit
            "-e", "GITHUB_PERSONAL_ACCESS_TOKEN=$token",
            "-e", "GITHUB_TOOLSETS=$toolsets",
            "-e", "GITHUB_READ_ONLY=$readOnly",
            "-e", "GITHUB_HOST=$host",
            "ghcr.io/github/github-mcp-server"
        )
    }

    /**
     * Start background thread to read stderr and log errors
     */
    private val errorMessages = mutableListOf<String>()

    private fun startErrorReaderThread() {
        Thread {
            try {
                errorReader?.forEachLine { errorLine ->
                    if (errorLine.isNotBlank()) {
                        errorMessages.add(errorLine)
                        System.err.println("[GitHub MCP Server Error] $errorLine")
                    }
                }
            } catch (e: IOException) {
                // Stream closed, this is expected on disconnect
            }
        }.apply {
            isDaemon = true
            name = "GitHubMCPServer-ErrorReader"
        }.start()
    }

    private fun getRecentErrors(): String {
        return if (errorMessages.isEmpty()) {
            "No error messages captured"
        } else {
            errorMessages.takeLast(10).joinToString("\n")
        }
    }

    /**
     * Call a GitHub MCP Server tool
     *
     * @param toolName MCP tool name (e.g., "repository_read:get_file_contents")
     * @param arguments Tool-specific parameters
     * @return Tool result as a Map
     * @throws IllegalStateException if not connected
     * @throws GitHubMCPToolException if tool call fails
     * @throws MCPException if communication error occurs
     */
    fun callTool(toolName: String, arguments: Map<String, Any>): Map<String, Any> {
        if (!isConnected) {
            throw IllegalStateException("Not connected to GitHub MCP Server. Call connect() first.")
        }

        val requestId = requestIdCounter.incrementAndGet().toString()
        val request = MCPRequest(
            id = requestId,
            method = "tools/call",
            params = mapOf(
                "name" to toolName,
                "arguments" to arguments
            )
        )

        try {
            // Send request
            val requestJson = mapper.writeValueAsString(request)
            println("→ MCP Request: $requestJson")

            // Check if process is still alive before sending
            if (process?.isAlive == false) {
                val exitCode = process!!.exitValue()
                throw MCPException(
                    "GitHub MCP Server process has died before sending request (exit code: $exitCode)\n" +
                    "Recent error messages:\n${getRecentErrors()}"
                )
            }

            println("  Writing to stdin...")
            inputWriter?.write(requestJson)
            inputWriter?.write("\n")  // Force Unix newline instead of system-dependent newLine()
            inputWriter?.flush()
            println("  ✓ Request sent to stdin")

            // Check if process is still alive after sending
            if (process?.isAlive == false) {
                throw MCPException("GitHub MCP Server process died after sending request")
            }

            println("  Waiting for response from stdout...")
            // Check if data is available
            if (outputReader?.ready() == true) {
                println("  Data is available in stdout buffer")
            } else {
                println("  No immediate data in stdout buffer, waiting for readLine()...")
            }

            // Read response
            val responseLine = outputReader?.readLine()
                ?: throw MCPException("No response from MCP Server (connection closed)")

            println("← MCP Response: $responseLine")

            val response: MCPResponse = try {
                mapper.readValue(responseLine)
            } catch (e: Exception) {
                throw MCPException("Failed to parse MCP response: ${e.message}\nResponse: $responseLine", e)
            }

            // Check for errors
            if (response.error != null) {
                throw GitHubMCPToolException(
                    toolName = toolName,
                    errorCode = response.error.code,
                    message = response.error.message
                )
            }

            return response.result ?: emptyMap()

        } catch (e: GitHubMCPToolException) {
            throw e // Re-throw tool exceptions as-is
        } catch (e: IOException) {
            throw MCPException("I/O error during MCP communication: ${e.message}", e)
        } catch (e: Exception) {
            throw MCPException("Unexpected error calling tool '$toolName': ${e.message}", e)
        }
    }

    /**
     * List available tools from the MCP Server
     *
     * Useful for debugging and discovering available GitHub operations
     *
     * @return List of tool names
     */
    fun listTools(): List<MCPTool> {
        if (!isConnected) {
            throw IllegalStateException("Not connected to GitHub MCP Server")
        }

        val request = MCPRequest(
            id = requestIdCounter.incrementAndGet().toString(),
            method = "tools/list",
            params = null
        )

        try {
            val requestJson = mapper.writeValueAsString(request)
            inputWriter?.write(requestJson)
            inputWriter?.newLine()
            inputWriter?.flush()

            val responseLine = outputReader?.readLine() ?: return emptyList()
            val response: MCPResponse = mapper.readValue(responseLine)

            if (response.error != null) {
                println("Error listing tools: ${response.error.message}")
                return emptyList()
            }

            @Suppress("UNCHECKED_CAST")
            val tools = response.result?.get("tools") as? List<Map<String, Any>> ?: emptyList()

            return tools.map { toolMap ->
                MCPTool(
                    name = toolMap["name"] as? String ?: "",
                    description = toolMap["description"] as? String,
                    inputSchema = toolMap["inputSchema"] as? Map<String, Any>
                )
            }

        } catch (e: Exception) {
            println("Failed to list tools: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Check if the connector is currently connected
     */
    fun isConnected(): Boolean = isConnected

    /**
     * Disconnect from GitHub MCP Server and cleanup resources
     *
     * This method is safe to call multiple times
     */
    fun disconnect() {
        if (!isConnected) {
            return
        }

        try {
            println("Disconnecting from GitHub MCP Server...")

            // Close streams
            inputWriter?.close()
            outputReader?.close()
            errorReader?.close()

            // Terminate Docker container
            process?.destroy()

            // Wait for process to exit (max 5 seconds)
            val exited = process?.waitFor(5, TimeUnit.SECONDS) ?: true
            if (!exited) {
                println("Warning: GitHub MCP Server did not exit gracefully, forcing termination")
                process?.destroyForcibly()
                process?.waitFor(2, TimeUnit.SECONDS)
            }

            isConnected = false
            println("✓ GitHub MCP Server disconnected")

        } catch (e: Exception) {
            println("Error during disconnect: ${e.message}")
            isConnected = false
        } finally {
            process = null
            inputWriter = null
            outputReader = null
            errorReader = null
        }
    }

    /**
     * Auto-disconnect when object is garbage collected
     */
    protected fun finalize() {
        disconnect()
    }
}

/**
 * Execute a block with an auto-managed GitHub MCP Server connection
 *
 * Example:
 * ```kotlin
 * withGitHubMCP { connector ->
 *     val result = connector.callTool("repository_read:get_file_contents", ...)
 *     // Connection automatically closed after this block
 * }
 * ```
 */
inline fun <T> withGitHubMCP(block: (GitHubMCPServerConnector) -> T): T {
    val connector = GitHubMCPServerConnector()
    return try {
        connector.connect()
        block(connector)
    } finally {
        connector.disconnect()
    }
}
