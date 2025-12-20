package org.springforge.cicdassistant.bedrock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springforge.cicdassistant.config.EnvironmentConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse

/**
 * AWS Bedrock client for invoking Claude 4 Sonnet model.
 * Handles communication with AWS Bedrock Runtime API for AI-powered artifact generation.
 * 
 * Credentials are loaded from .env file via EnvironmentConfig:
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION (optional, defaults to us-east-1)
 * - CLAUDE_MODEL_ID (optional, defaults to Claude Sonnet 4)
 */
class BedrockClient {
    
    private val objectMapper = jacksonObjectMapper()
    
    /**
     * AWS Bedrock Runtime client initialized with credentials from .env file.
     * Supports both permanent credentials (AKIA*) and temporary credentials (ASIA* with session token).
     */
    private val bedrockClient: BedrockRuntimeClient by lazy {
        // Validate configuration
        if (!EnvironmentConfig.isConfigured()) {
            throw BedrockException("AWS credentials not configured. Please set up your .env file with AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY")
        }
        
        // Check if using temporary credentials (session token present)
        val credentialsProvider = if (EnvironmentConfig.AWS.sessionToken != null) {
            // Temporary credentials with session token
            val sessionCredentials = software.amazon.awssdk.auth.credentials.AwsSessionCredentials.create(
                EnvironmentConfig.AWS.accessKeyId,
                EnvironmentConfig.AWS.secretAccessKey,
                EnvironmentConfig.AWS.sessionToken
            )
            StaticCredentialsProvider.create(sessionCredentials)
        } else {
            // Permanent credentials
            val credentials = AwsBasicCredentials.create(
                EnvironmentConfig.AWS.accessKeyId,
                EnvironmentConfig.AWS.secretAccessKey
            )
            StaticCredentialsProvider.create(credentials)
        }
        
        BedrockRuntimeClient.builder()
            .region(EnvironmentConfig.AWS.region)
            .credentialsProvider(credentialsProvider)
            .build()
    }
    
    /**
     * Generates a Dockerfile using Claude 4 Sonnet.
     *
     * @param mcpContext The MCP-formatted project context JSON
     * @param usePrefill Whether to use prefill technique to prevent hallucination (default: true)
     * @return Generated Dockerfile content
     * @throws BedrockException if the API call fails
     */
    fun generateDockerfile(mcpContext: String, usePrefill: Boolean = true): String {
        // Extract architecture type from MCP context
        val architectureType = extractArchitectureType(mcpContext)
        
        val prompt = buildDockerfilePrompt(mcpContext, architectureType)
        
        return if (usePrefill) {
            val prefill = buildDockerfilePrefill(mcpContext, architectureType)
            invokeModelWithPrefill(prompt, prefill)
        } else {
            invokeModel(prompt)
        }
    }
    
    /**
     * Generates Kubernetes configuration (deployment.yaml, service.yaml) using Claude 4 Sonnet.
     *
     * @param mcpContext The MCP-formatted project context JSON
     * @return Generated Kubernetes YAML content
     * @throws BedrockException if the API call fails
     */
    fun generateKubernetesConfig(mcpContext: String): String {
        val prompt = buildKubernetesPrompt(mcpContext)
        return invokeModel(prompt)
    }
    
    /**
     * Generates CI/CD pipeline configuration using Claude 4 Sonnet.
     *
     * @param mcpContext The MCP-formatted project context JSON
     * @param pipelineType Type of CI/CD pipeline (github, gitlab, jenkins)
     * @return Generated pipeline configuration content
     * @throws BedrockException if the API call fails
     */
    fun generateCICDPipeline(mcpContext: String, pipelineType: String = "github"): String {
        val prompt = buildCICDPrompt(mcpContext, pipelineType)
        return invokeModel(prompt)
    }
    
    /**
     * Extracts architecture type from MCP context JSON.
     * @param mcpContext JSON string containing MCP context
     * @return Architecture type ("SPRING_BOOT" or "INTELLIJ_PLUGIN")
     */
    private fun extractArchitectureType(mcpContext: String): String {
        return try {
            val contextMap: Map<String, Any> = objectMapper.readValue(mcpContext)
            val metadata = contextMap["metadata"] as? Map<*, *>
            metadata?.get("architecture_type") as? String ?: "SPRING_BOOT"
        } catch (e: Exception) {
            "SPRING_BOOT" // Default fallback
        }
    }
    
    /**
     * Invokes Claude 4 Sonnet with assistant prefill to prevent hallucination.
     * Prefill forces Claude to start its response with specific text.
     *
     * @param prompt The complete prompt to send to Claude
     * @param prefill The text to start Claude's response with
     * @return Prefill + Claude's continuation
     * @throws BedrockException if the API call fails or response is invalid
     */
    private fun invokeModelWithPrefill(prompt: String, prefill: String): String {
        try {
            // Build the request payload with prefill (assistant message)
            val requestBody = mapOf(
                "anthropic_version" to EnvironmentConfig.Claude.anthropicVersion,
                "max_tokens" to EnvironmentConfig.Claude.maxTokens,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to prompt
                    ),
                    mapOf(
                        "role" to "assistant",
                        "content" to prefill  // Forces Claude to continue from here
                    )
                ),
                "temperature" to 0.0,  // Deterministic for prefill
                "top_p" to 0.9
            )
            
            val requestBodyJson = objectMapper.writeValueAsString(requestBody)
            
            val invokeRequest = InvokeModelRequest.builder()
                .modelId(EnvironmentConfig.Claude.modelId)
                .body(SdkBytes.fromUtf8String(requestBodyJson))
                .contentType("application/json")
                .accept("application/json")
                .build()
            
            val response = bedrockClient.invokeModel(invokeRequest)
            val responseBody = response.body().asUtf8String()
            val responseMap: Map<String, Any> = objectMapper.readValue(responseBody)
            
            val content = responseMap["content"] as? List<*>
                ?: throw BedrockException("Invalid response format: missing 'content' field")
            
            val firstContent = content.firstOrNull() as? Map<*, *>
                ?: throw BedrockException("Invalid response format: empty content array")
            
            val generatedText = firstContent["text"] as? String
                ?: throw BedrockException("Invalid response format: missing 'text' field")
            
            // Return prefill + Claude's continuation
            return prefill + generatedText.trim()
            
        } catch (e: Exception) {
            throw BedrockException("Failed to invoke Bedrock model with prefill: ${e.message}", e)
        }
    }
    
    /**
     * Invokes Claude 4 Sonnet model via AWS Bedrock Runtime API.
     *
     * @param prompt The complete prompt to send to Claude
     * @return Claude's response text
     * @throws BedrockException if the API call fails or response is invalid
     */
    private fun invokeModel(prompt: String): String {
        try {
            // Build the request payload for Claude (Anthropic Messages API format)
            val requestBody = mapOf(
                "anthropic_version" to EnvironmentConfig.Claude.anthropicVersion,
                "max_tokens" to EnvironmentConfig.Claude.maxTokens,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to prompt
                    )
                ),
                "temperature" to 0.7,
                "top_p" to 0.9
            )
            
            val requestBodyJson = objectMapper.writeValueAsString(requestBody)
            
            // Create the Bedrock invoke model request
            val invokeRequest = InvokeModelRequest.builder()
                .modelId(EnvironmentConfig.Claude.modelId)
                .body(SdkBytes.fromUtf8String(requestBodyJson))
                .contentType("application/json")
                .accept("application/json")
                .build()
            
            // Invoke the model
            val response: InvokeModelResponse = bedrockClient.invokeModel(invokeRequest)
            
            // Parse the response
            val responseBody = response.body().asUtf8String()
            val responseMap: Map<String, Any> = objectMapper.readValue(responseBody)
            
            // Extract the generated text from Claude's response
            // Response format: {"content": [{"text": "..."}], "stop_reason": "end_turn"}
            val content = responseMap["content"] as? List<*>
                ?: throw BedrockException("Invalid response format: missing 'content' field")
            
            val firstContent = content.firstOrNull() as? Map<*, *>
                ?: throw BedrockException("Invalid response format: empty content array")
            
            val generatedText = firstContent["text"] as? String
                ?: throw BedrockException("Invalid response format: missing 'text' field")
            
            return generatedText.trim()
            
        } catch (e: Exception) {
            throw BedrockException("Failed to invoke Bedrock model: ${e.message}", e)
        }
    }
    
    /**
     * Builds a prefill for Dockerfile generation to prevent hallucination.
     * Forces Claude to start with correct Dockerfile structure.
     */
    private fun buildDockerfilePrefill(mcpContext: String, architectureType: String): String {
        val mcpData = try {
            objectMapper.readValue<Map<String, Any>>(mcpContext)
        } catch (e: Exception) {
            return "FROM " // Minimal fallback
        }
        
        val project = mcpData["project"] as? Map<*, *>
        val javaVersion = project?.get("java_version") as? String ?: "17"
        val buildTool = project?.get("build_tool") as? String ?: "maven"
        
        return when (architectureType) {
            "INTELLIJ_PLUGIN" -> """# Stage 1: Build Plugin
FROM eclipse-temurin:$javaVersion-jdk-alpine AS builder

# Install bash and dos2unix for line ending conversion
RUN apk add --no-cache bash dos2unix

# Set working directory
WORKDIR /build

# Copy Gradle wrapper files
COPY gradlew ./
COPY gradlew.bat ./
COPY gradle/ ./gradle/

# Copy build configuration
COPY build.gradle.kts ./
COPY settings.gradle.kts ./

# Copy source code
COPY src/ ./src/

# Fix line endings and make Gradle wrapper executable
RUN dos2unix gradlew && chmod +x ./gradlew

# Build""".trimEnd()
            
            else -> """# Stage 1: Build
FROM eclipse-temurin:$javaVersion-jdk-alpine AS builder

# Set working directory
WORKDIR /build

# Install""".trimEnd()
        }
    }
    
    /**
     * Builds a prompt for Dockerfile generation.
     * Architecture-aware: handles both Spring Boot and IntelliJ Plugin projects.
     */
    private fun buildDockerfilePrompt(mcpContext: String, architectureType: String): String {
        return when (architectureType) {
            "INTELLIJ_PLUGIN" -> buildIntellijPluginDockerfilePrompt(mcpContext)
            else -> buildSpringBootDockerfilePrompt(mcpContext)
        }
    }
    
    /**
     * Builds Spring Boot-specific Dockerfile prompt with advanced production features.
     */
    private fun buildSpringBootDockerfilePrompt(mcpContext: String): String {
        return """
<role>You are an expert DevOps engineer specializing in production-ready Spring Boot containerization.</role>

<task>Generate a production-hardened, multi-stage Dockerfile for the Spring Boot application in the MCP context.</task>

<mcp_context>
$mcpContext
</mcp_context>

<instructions>
1. **Base Image Selection**:
   - Builder: eclipse-temurin:{java_version}-jdk-alpine
   - Runtime: eclipse-temurin:{java_version}-jre-alpine
   - Extract Java version from MCP: project.java_version

2. **Builder Stage** (Multi-stage build):
   - Set WORKDIR /build (on separate line)
   - Install build tool (Maven/Gradle based on project.build_tool)
   - Copy dependency files FIRST for layer caching (pom.xml or build.gradle.kts)
   - Run dependency download: `mvn dependency:go-offline -B` or `./gradlew dependencies --no-daemon`
   - Copy source code LAST
   - Build with JAR rename for easier reference:
     * Maven: `RUN mvn clean package -DskipTests -B && mv target/*.jar target/app.jar`
     * Gradle: `RUN ./gradlew bootJar --no-daemon && mv build/libs/*.jar build/libs/app.jar`
   - Clean cache: `rm -rf ~/.m2` or `rm -rf ~/.gradle`
   - CRITICAL: Ensure all RUN commands have space after RUN keyword

3. **Runtime Stage** (Security-hardened):
   - Install security updates: `apk update && apk upgrade`
   - Install monitoring tools: `curl` (for health checks), `tzdata` (timezone support)
   - Clean package cache: `rm -rf /var/cache/apk/*`
   - Create non-root user: `addgroup -g 1000 springuser && adduser -D -u 1000 -G springuser springuser`
   - Create app directories: `/app/logs`, `/app/tmp`
   - Set proper permissions: `chown -R springuser:springuser /app`

4. **Advanced JVM Optimization**:
   - Container support: `-XX:+UseContainerSupport`
   - Memory: `-XX:MaxRAMPercentage=75.0`
   - GC tuning: `-XX:+UseG1GC -XX:+UseStringDeduplication`
   - Entropy: `-Djava.security.egd=file:/dev/./urandom`
   - Spring profiles: `-Dspring.profiles.active=prod`

5. **Health Checks** (Actuator-aware):
   - If 'spring-boot-starter-actuator' in dependencies:
     * Use: `curl -f http://localhost:{port}/actuator/health || exit 1`
     * Set: `--start-period=60s` (for slow-starting apps)
   - Otherwise: `curl -f http://localhost:{port}/ || exit 1`
   - Settings: `--interval=30s --timeout=10s --retries=3`

6. **Port Configuration**:
   - Extract from MCP: configuration.server_port (default 8080)
   - Use EXPOSE directive with the extracted port

7. **Database Configuration** (if project.database_type is present):
   - Add ENV variables (empty values):
     * JDBC_URL={configuration.datasource_url} (use the actual datasource URL from configuration)
     * DB_USERNAME=
     * DB_PASSWORD=

8. **Metadata Labels**:
   - version="{project.version}"
   - name="{project.name}"
   - group="{project.group_id}"
   - java.version="{project.java_version}"
   - spring.boot.version="{project.spring_boot_version}"

9. **Production Features**:
   - VOLUME ["/app/logs"] for log persistence
   - ENV TZ=UTC for timezone consistency
   - ENV SPRING_MAIN_LAZY_INITIALIZATION=true (faster startup)
   - Proper signal handling: `ENTRYPOINT ["sh", "-c", "exec java ${'$'}JAVA_OPTS -jar app.jar"]`
</instructions>

<output_format>
Generate production-ready Dockerfile with ALL features above.

CRITICAL FORMATTING RULES:
1. Every Dockerfile instruction (FROM, RUN, COPY, etc.) MUST start on a new line
2. Comments MUST be on their own line, NOT merged with instructions
3. Ensure proper spacing: "RUN command" NOT "RUN.command" or "WORKDIR/path# comment"
4. Copy exact path from builder: COPY --from=builder /build/target/app.jar (for Maven)
5. Test each line would parse correctly in a real Dockerfile

Do NOT include:
- Markdown code blocks (```dockerfile)
- Explanatory text outside the Dockerfile
- Merged instructions like "WORKDIR /build# Install Maven"

Start with: FROM eclipse-temurin:...
Each instruction on separate line.
</output_format>

<quality_checklist>
✓ Multi-stage build with dependency caching
✓ Security updates installed
✓ Non-root user with proper permissions
✓ Advanced JVM tuning (G1GC, StringDeduplication)
✓ Actuator-aware health checks with start-period
✓ Database ENV variables if applicable
✓ Volume mount for logs
✓ Timezone configuration
✓ All metadata labels
</quality_checklist>
""".trimIndent()
    }
    
    /**
     * Builds IntelliJ Plugin-specific Dockerfile prompt.
     * Handles Windows line ending issues and plugin distribution.
     */
    private fun buildIntellijPluginDockerfilePrompt(mcpContext: String): String {
        return """
<role>You are an expert in IntelliJ IDEA plugin containerization and build automation.</role>

<task>Generate a Dockerfile for building and distributing an IntelliJ IDEA plugin from the MCP context.</task>

<mcp_context>
$mcpContext
</mcp_context>

<instructions>
1. **Builder Stage**:
   - Base: eclipse-temurin:{java_version}-jdk-alpine
   - Install: `bash dos2unix` (CRITICAL for Windows line ending fixes)
   - Workdir: /build

2. **Copy Order** (for layer caching):
   - Copy Gradle wrapper: `gradlew`, `gradlew.bat`, `gradle/`
   - Copy build files: `build.gradle.kts`, `settings.gradle.kts`
   - Copy source: `src/`

3. **Line Ending Fix** (CRITICAL):
   - Run: `dos2unix gradlew && chmod +x ./gradlew`
   - This prevents "bad interpreter" errors on Linux

4. **Build Plugin**:
   - Command: `RUN ./gradlew buildPlugin --no-daemon`
   - CRITICAL: Ensure space after RUN: "RUN ./gradlew" NOT "RUN./gradlew"
   - This creates .zip in `/build/build/distributions/`

5. **Verify Build**:
   - Run: `RUN ls -la /build/build/distributions/`

6. **Distribution Stage**:
   - Base: alpine:3.18 (minimal)
   - Create non-root user: `adduser -D -u 1000 appuser`
   - Workdir: /plugin
   - Copy artifacts: `COPY --from=builder --chown=appuser:appuser /build/build/distributions/*.zip ./`
   - Switch to: `USER appuser`
   - Default CMD: `["ls", "-lah", "/plugin"]`

7. **NO Runtime Execution**:
   - IntelliJ plugins are NOT run as JARs
   - They are distributed as .zip files
   - Do NOT use java -jar or ENTRYPOINT with java

8. **Labels**:
   - version="{project.version}"
   - name="{project.name}"
   - type="intellij-plugin"
   - java.version="{project.java_version}"
</instructions>

<output_format>
Generate Dockerfile for IntelliJ plugin distribution.

CRITICAL FORMATTING RULES:
1. Every instruction (FROM, RUN, COPY, etc.) MUST be on its own line
2. Ensure proper spacing: "RUN ./gradlew" NOT "RUN./gradlew"
3. Comments on separate lines, not merged with instructions
4. Verify each line is valid Dockerfile syntax

Do NOT include:
- Markdown code blocks (```dockerfile)
- Spring Boot features (health checks, JVM tuning for runtime)
- Runtime execution commands
- Merged syntax like "RUN./gradlew"

Start with: # Stage 1: Build Plugin
Each instruction on separate line with proper spacing.
</output_format>

<quality_checklist>
✓ dos2unix installed and used
✓ buildPlugin command (NOT bootJar)
✓ Distribution .zip copied (NOT runtime JAR)
✓ No java -jar execution
✓ Line endings fixed
✓ Build verification included
</quality_checklist>
""".trimIndent()
    }
    
    /**
     * Builds a prompt for Kubernetes configuration generation.
     * Generates Deployment + Service manifests with Spring Boot-aware health probes.
     */
    private fun buildKubernetesPrompt(mcpContext: String): String {
        return """
<role>You are an expert Kubernetes engineer specializing in Spring Boot microservices deployment.</role>

<task>Generate production-ready Kubernetes manifests (Deployment + Service) for the Spring Boot application in the MCP context.</task>

<mcp_context>
$mcpContext
</mcp_context>

<instructions>
1. **Deployment Manifest**:
   - apiVersion: apps/v1
   - kind: Deployment
   - metadata.name: {project.name}-deployment
   - spec.replicas: 2 (high availability)
   - spec.selector.matchLabels: app={project.name}, version={project.version}
   - Use rolling update strategy: maxSurge=1, maxUnavailable=0

2. **Container Specification**:
   - image: {project.name}:{project.version}
   - Extract server.port from MCP: configuration.server_port (default 8080)
   - containerPort: {server_port}
   - imagePullPolicy: IfNotPresent

3. **Resource Management**:
   - requests: cpu=250m, memory=512Mi
   - limits: cpu=1000m, memory=1Gi
   - Adjust based on project size and dependencies

4. **Health Probes** (Spring Boot Actuator-aware):
   - Check if 'spring-boot-starter-actuator' in project.dependencies
   - If YES:
     * livenessProbe: httpGet path=/actuator/health/liveness port={server_port}
     * readinessProbe: httpGet path=/actuator/health/readiness port={server_port}
   - If NO:
     * livenessProbe: httpGet path=/ port={server_port}
     * readinessProbe: httpGet path=/ port={server_port}
   - Set: initialDelaySeconds=30, periodSeconds=10, timeoutSeconds=3, failureThreshold=3

5. **Environment Variables**:
   - SPRING_PROFILES_ACTIVE: production
   - JAVA_OPTS: -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
   - If database configured (project.database_type is present):
     * JDBC_URL: Use ConfigMap reference
     * DB_USERNAME: Use Secret reference
     * DB_PASSWORD: Use Secret reference

6. **Service Manifest**:
   - apiVersion: v1
   - kind: Service
   - metadata.name: {project.name}-service
   - spec.type: ClusterIP (internal service)
   - spec.selector: app={project.name}
   - spec.ports: port=80, targetPort={server_port}, protocol=TCP

7. **Labels & Annotations**:
   - app: {project.name}
   - version: {project.version}
   - tier: backend
   - managed-by: springforge

8. **ConfigMap Reference** (if project.database_type present):
   - Add commented example ConfigMap for database URL
   - Name: {project.name}-config

9. **Secret Reference** (if project.database_type present):
   - Add commented example Secret for credentials
   - Name: {project.name}-secrets
</instructions>

<output_format>
Generate complete YAML manifests in this order:
1. Deployment manifest
2. '---' separator
3. Service manifest
4. '---' separator
5. ConfigMap example (commented) if project.database_type present
6. '---' separator
7. Secret example (commented) if project.database_type present

Do NOT include:
- Markdown code blocks (```yaml)
- Explanatory text outside YAML
- Additional descriptions

Start directly with: apiVersion: apps/v1
</output_format>

<quality_checklist>
✓ Deployment with 2 replicas
✓ Rolling update strategy configured
✓ Resource limits set
✓ Liveness and readiness probes configured
✓ Service exposes correct port from MCP
✓ Environment variables for Spring profiles
✓ Labels match between Deployment and Service
✓ Database config externalized if applicable
</quality_checklist>
""".trimIndent()
    }
    
    /**
     * Builds a prompt for CI/CD pipeline generation.
     * Platform-specific prompts for GitHub Actions, GitLab CI, and Jenkins.
     */
    private fun buildCICDPrompt(mcpContext: String, pipelineType: String): String {
        val platformConfig = when (pipelineType.lowercase()) {
            "github" -> PlatformConfig(
                name = "GitHub Actions",
                fileName = ".github/workflows/ci-cd.yml",
                cacheAction = "actions/cache@v3",
                dockerAction = "docker/build-push-action@v5",
                secretsPrefix = "secrets.",
                buildToolCache = "~/.gradle/caches or ~/.m2/repository"
            )
            "gitlab" -> PlatformConfig(
                name = "GitLab CI",
                fileName = ".gitlab-ci.yml",
                cacheAction = "cache: paths:",
                dockerAction = "docker build/push commands",
                secretsPrefix = "\$CI_",
                buildToolCache = ".gradle/caches or .m2/repository"
            )
            "jenkins" -> PlatformConfig(
                name = "Jenkins",
                fileName = "Jenkinsfile",
                cacheAction = "Maven/Gradle plugin caching",
                dockerAction = "docker.build/docker.push",
                secretsPrefix = "credentials()",
                buildToolCache = "workspace/.gradle or workspace/.m2"
            )
            else -> PlatformConfig("Generic CI/CD", "pipeline.yml", "", "", "", "")
        }
        
        return """
<role>You are an expert DevOps engineer specializing in ${platformConfig.name} pipelines for Spring Boot applications.</role>

<task>Generate a production-ready ${platformConfig.name} pipeline configuration for the Spring Boot application in the MCP context.</task>

<mcp_context>
$mcpContext
</mcp_context>

<instructions>
1. **Pipeline Stages**:
   - Stage 1: Build & Test
   - Stage 2: Security Scan (optional)
   - Stage 3: Docker Build & Push
   - Stage 4: Deploy to Kubernetes

2. **Build & Test Stage**:
   - Checkout code from repository
   - Set up Java {project.java_version} environment
   - Cache dependencies: ${platformConfig.buildToolCache}
   - Build tool from MCP: project.build_tool
   - For Gradle: `./gradlew clean build test`
   - For Maven: `./mvnw clean package`
   - Upload test results and coverage reports
   - Fail pipeline if tests fail

3. **Security Scan Stage** (optional):
   - Run dependency vulnerability scan
   - For GitHub: Use `anchore/scan-action` or `aquasecurity/trivy-action`
   - For GitLab: Use `container_scanning` template
   - For Jenkins: Use OWASP Dependency Check plugin

4. **Docker Build & Push Stage**:
   - Build Docker image: {project.name}:{project.version}
   - Tag with commit SHA and 'latest'
   - Push to container registry (Docker Hub, ECR, GCR, or GitLab Registry)
   - Use ${platformConfig.dockerAction}
   - Reference secrets: REGISTRY_USERNAME, REGISTRY_PASSWORD
   - Use ${platformConfig.secretsPrefix} syntax for secrets

5. **Deploy to Kubernetes Stage**:
   - Set up kubectl with kubeconfig from secrets
   - Apply Kubernetes manifests from kubernetes/ folder
   - Update Deployment image: kubectl set image deployment/{project.name}
   - Wait for rollout: kubectl rollout status
   - Run smoke tests against deployed service

6. **Platform-Specific Features**:
   - GitHub Actions: Use matrix strategy for multi-environment deploys, use environment protection rules
   - GitLab CI: Use environments with manual approval for production, define .pre and .post stages
   - Jenkins: Use parallel stages, declarative pipeline syntax, post-build notifications

7. **Triggers**:
   - GitHub: on: push (main, develop), pull_request
   - GitLab: on: push, merge_request
   - Jenkins: Poll SCM or webhook trigger

8. **Environment Variables**:
   - SPRING_PROFILES_ACTIVE: Set per environment (dev/staging/prod)
   - JAVA_OPTS: JVM optimization flags
   - Database credentials: Load from ${platformConfig.secretsPrefix}DB_USERNAME, DB_PASSWORD

9. **Caching**:
   - Use ${platformConfig.cacheAction}
   - Cache build dependencies to speed up pipeline
   - Cache Docker layers if supported

10. **Error Handling**:
    - Add failure notifications (Slack, email, etc.)
    - Rollback mechanism for failed deployments
    - Retry logic for flaky network operations
</instructions>

<output_format>
Generate the complete ${platformConfig.name} configuration file (${platformConfig.fileName}).

Do NOT include:
- Markdown code blocks (```yaml or ```groovy)
- Explanatory comments outside the pipeline
- Additional text or descriptions

For GitHub Actions: Start with: name: CI/CD Pipeline
For GitLab CI: Start with: stages:
For Jenkins: Start with: pipeline {
</output_format>

<quality_checklist>
✓ All stages defined (build, test, docker, deploy)
✓ Build tool matches project.build_tool from MCP
✓ Java version matches project.java_version
✓ Dependencies cached for faster builds
✓ Tests run before Docker build
✓ Docker image tagged with version and commit SHA
✓ Kubernetes deployment automated
✓ Secrets used for sensitive data
✓ Environment-specific configurations
✓ Failure notifications configured
</quality_checklist>
""".trimIndent()
    }
    
    /**
     * Platform-specific configuration for CI/CD prompt customization.
     */
    private data class PlatformConfig(
        val name: String,
        val fileName: String,
        val cacheAction: String,
        val dockerAction: String,
        val secretsPrefix: String,
        val buildToolCache: String
    )
    
    /**
     * Closes the Bedrock client and releases resources.
     */
    fun close() {
        bedrockClient.close()
    }
}

/**
 * Custom exception for Bedrock API errors.
 */
class BedrockException(message: String, cause: Throwable? = null) : Exception(message, cause)
