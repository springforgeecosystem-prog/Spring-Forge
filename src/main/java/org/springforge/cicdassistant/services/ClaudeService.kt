package org.springforge.cicdassistant.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springforge.cicdassistant.config.EnvironmentConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest

/**
 * Service for interacting with AWS Bedrock Claude 4 Opus
 * Handles CI/CD artifact generation for Spring Boot projects
 *
 * Configuration is loaded from .env file or environment variables:
 * - AWS_REGION
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_SESSION_TOKEN (optional)
 * - CLAUDE_MODEL_ID
 */
class ClaudeService {

    private val client: BedrockRuntimeClient
    private val objectMapper = jacksonObjectMapper()

    init {
        // Validate configuration on initialization
        EnvironmentConfig.validate()

        // Build AWS credentials from environment
        val credentials = if (EnvironmentConfig.AWS.sessionToken != null) {
            // Use session credentials if token is provided
            AwsSessionCredentials.create(
                EnvironmentConfig.AWS.accessKeyId!!,
                EnvironmentConfig.AWS.secretAccessKey!!,
                EnvironmentConfig.AWS.sessionToken!!
            )
        } else {
            // Use basic credentials
            AwsBasicCredentials.create(
                EnvironmentConfig.AWS.accessKeyId!!,
                EnvironmentConfig.AWS.secretAccessKey!!
            )
        }

        // Build Bedrock client with credentials from environment
        client = BedrockRuntimeClient.builder()
            .region(EnvironmentConfig.AWS.region)
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build()
    }

    /**
     * Generate a production-ready Dockerfile for Spring Boot application
     * @param projectInfo Project metadata (dependencies, Java version, build tool)
     * @return Generated Dockerfile content
     */
    fun generateDockerfile(projectInfo: String): String {
        val prompt = """
            You are an expert DevOps engineer specializing in containerization.
            
            Generate a production-ready, optimized Dockerfile for a Spring Boot application with the following details:
            
            $projectInfo
            
            Requirements:
            1. Use multi-stage build to minimize image size
            2. Use appropriate base images (e.g., eclipse-temurin for Java)
            3. Implement proper layer caching
            4. Add security best practices (non-root user, minimal image)
            5. Include health check configuration
            6. Optimize for Spring Boot fat JAR
            7. Add comments explaining each step
            
            Return ONLY the Dockerfile content, no explanations before or after.
        """.trimIndent()

        return callClaude(prompt)
    }

    /**
     * Generate GitHub Actions workflow for CI/CD
     * @param projectInfo Project metadata
     * @return Generated GitHub Actions YAML content
     */
    fun generateGitHubActionsWorkflow(projectInfo: String): String {
        val prompt = """
            You are an expert in CI/CD pipelines and GitHub Actions.
            
            Generate a comprehensive GitHub Actions workflow for a Spring Boot project with:
            
            $projectInfo
            
            The workflow should include:
            1. Build and test on push/pull request
            2. Run unit and integration tests
            3. Code quality checks (if applicable)
            4. Build Docker image
            5. Security scanning
            6. Deploy to staging/production (basic structure)
            7. Use caching for dependencies
            8. Matrix builds for multiple Java versions (if needed)
            9. Proper secrets management
            
            Return ONLY the YAML workflow file content, no explanations.
        """.trimIndent()

        return callClaude(prompt)
    }

    /**
     * Generate docker-compose.yml for local development
     * @param projectInfo Project metadata including services needed
     * @return Generated docker-compose.yml content
     */
    fun generateDockerCompose(projectInfo: String): String {
        val prompt = """
            You are an expert in Docker and microservices orchestration.
            
            Generate a docker-compose.yml file for local development with:
            
            $projectInfo
            
            Include:
            1. Spring Boot application service
            2. Database service (if mentioned in project info)
            3. Any other required services (Redis, RabbitMQ, etc.)
            4. Proper networking configuration
            5. Volume mounts for persistence
            6. Environment variables
            7. Health checks
            8. Development-friendly settings (hot reload if possible)
            
            Return ONLY the docker-compose.yml content, no explanations.
        """.trimIndent()

        return callClaude(prompt)
    }

    /**
     * Generate Kubernetes deployment manifests
     * @param projectInfo Project metadata
     * @return Generated Kubernetes YAML content
     */
    fun generateKubernetesManifests(projectInfo: String): String {
        val prompt = """
            You are a Kubernetes expert specializing in Spring Boot deployments.
            
            Generate Kubernetes manifests for:
            
            $projectInfo
            
            Create:
            1. Deployment manifest with proper resource limits
            2. Service manifest (ClusterIP or LoadBalancer)
            3. ConfigMap for configuration
            4. Secret template (with placeholders)
            5. HorizontalPodAutoscaler
            6. Ingress (basic configuration)
            7. Health probes (liveness and readiness)
            
            Return all manifests separated by '---', no explanations.
        """.trimIndent()

        return callClaude(prompt)
    }

    /**
     * Generate .gitignore file optimized for Spring Boot
     * @param buildTool Maven or Gradle
     * @return Generated .gitignore content
     */
    fun generateGitIgnore(buildTool: String): String {
        val prompt = """
            Generate a comprehensive .gitignore file for a Spring Boot project using $buildTool.
            
            Include:
            1. IDE files (IntelliJ, Eclipse, VS Code)
            2. Build tool artifacts
            3. OS-specific files
            4. Logs and temporary files
            5. Sensitive configuration files
            6. Docker and deployment artifacts
            
            Return ONLY the .gitignore content, no explanations.
        """.trimIndent()

        return callClaude(prompt)
    }

    /**
     * Analyze Dockerfile for best practices and suggest improvements
     * @param dockerfileContent Existing Dockerfile content
     * @return Analysis and suggestions
     */
    fun analyzeDockerfile(dockerfileContent: String): String {
        val prompt = """
            You are a Docker security and optimization expert.
            
            Analyze this Dockerfile and provide:
            1. Security vulnerabilities
            2. Performance optimization opportunities
            3. Best practice violations
            4. Size optimization suggestions
            5. Specific actionable improvements
            
            Dockerfile:
            ```
            $dockerfileContent
            ```
            
            Provide a structured analysis with severity levels (HIGH, MEDIUM, LOW).
        """.trimIndent()

        return callClaude(prompt)
    }

    /**
     * Core method to call Claude 4 Opus via AWS Bedrock
     * @param prompt The prompt to send to Claude
     * @param maxTokens Maximum tokens for response (default: from config)
     * @return Claude's response text
     */
    fun callClaude(prompt: String, maxTokens: Int = EnvironmentConfig.Claude.maxTokens): String {
        return try {
            val payload = mapOf(
                "anthropic_version" to EnvironmentConfig.Claude.anthropicVersion,
                "max_tokens" to maxTokens,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to prompt
                    )
                )
            )

            val request = InvokeModelRequest.builder()
                .modelId(EnvironmentConfig.Claude.modelId)
                .body(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(payload)))
                .build()

            val response = client.invokeModel(request)
            val responseBody = objectMapper.readTree(response.body().asUtf8String())

            responseBody["content"][0]["text"].asText()
        } catch (e: Exception) {
            throw ClaudeServiceException("Failed to call Claude API: ${e.message}", e)
        }
    }

    /**
     * Test connection to AWS Bedrock
     * @return true if connection successful
     */
    fun testConnection(): Boolean {
        return try {
            val response = callClaude("Respond with just 'OK'", maxTokens = 10)
            response.contains("OK", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Close the AWS client
     */
    fun close() {
        client.close()
    }
}

/**
 * Custom exception for Claude service errors
 */
class ClaudeServiceException(message: String, cause: Throwable? = null) : Exception(message, cause)

