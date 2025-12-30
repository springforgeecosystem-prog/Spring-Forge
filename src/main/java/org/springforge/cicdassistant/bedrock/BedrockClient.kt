package org.springforge.cicdassistant.bedrock

import com.fasterxml.jackson.core.type.TypeReference
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
    
    // Helper function to parse JSON context safely
    private fun parseContext(json: String): Map<String, Any> {
        return objectMapper.readValue(json, object : TypeReference<Map<String, Any>>() {})
    }
    
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
        // Extract architecture type from MCP context JSON
        val architectureType = extractArchitectureType(mcpContext)
        
        println("[BedrockClient] Building Dockerfile for architecture: $architectureType")
        
        val prompt = buildDockerfilePrompt(mcpContext, architectureType)
        
        return if (usePrefill) {
            val prefill = buildDockerfilePrefill(mcpContext, architectureType)
            println("[BedrockClient] Using prefill technique for $architectureType")
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
     * Generates Docker Compose configuration using Claude 4 Sonnet.
     * Automatically detects services needed based on project dependencies.
     *
     * @param mcpContext The MCP-formatted project context JSON
     * @return Generated docker-compose.yml content
     * @throws BedrockException if the API call fails
     */
    fun generateDockerCompose(mcpContext: String): String {
        println("[BedrockClient] Generating Docker Compose configuration")

        val services = detectServices(mcpContext)
        println("[BedrockClient] Detected services: ${services.joinToString(", ")}")

        val prompt = buildDockerComposePrompt(mcpContext, services)
        return invokeModel(prompt)
    }

    /**
     * Generates GitHub Actions workflow YAML for Spring Boot projects.
     *
     * @param mcpContext MCP context JSON string
     * @return GitHub Actions workflow YAML content
     */
    fun generateGitHubActionsWorkflow(mcpContext: String): String {
        println("[BedrockClient] Generating GitHub Actions workflow")

        val prompt = buildGitHubActionsPrompt(mcpContext)
        val workflow = invokeModel(prompt)

        // Clean up any markdown blocks Claude might add
        val cleaned = workflow
            .removePrefix("```yaml")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        println("[BedrockClient] GitHub Actions workflow generated successfully")
        return cleaned
    }

    /**
     * Extracts architecture type from MCP context JSON.
     * Checks metadata.architecture_type (correct location in MCP format).
     * 
     * @param mcpContext JSON string containing MCP context
     * @return Architecture type ("SPRING_BOOT" or "INTELLIJ_PLUGIN")
     */
    private fun extractArchitectureType(mcpContext: String): String {
        return try {
            // Parse JSON to map
            val contextMap = parseContext(mcpContext)
            val metadata = contextMap["metadata"] as? Map<*, *>
            
            // Debug: print all metadata keys
            println("[BedrockClient] Metadata keys: ${metadata?.keys}")
            
            // Try both snake_case and camelCase (Jackson might serialize either way)
            val archType = (metadata?.get("architecture_type") ?: metadata?.get("architectureType")) as? String
            
            println("[BedrockClient] Extracted architecture type: ${archType ?: "null"}")
            
            if (archType == null) {
                println("[BedrockClient] ⚠ Architecture type is null! MCP JSON preview:")
                println(mcpContext.take(500))
            }
            
            archType ?: "SPRING_BOOT"
        } catch (e: Exception) {
            println("[BedrockClient] ❌ Error extracting architecture type: ${e.message}")
            e.printStackTrace()
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
        // Try both snake_case and camelCase (Jackson serializes as camelCase by default)
        val javaVersion = (project?.get("javaVersion") ?: project?.get("java_version")) as? String ?: "17"
        val buildTool = (project?.get("buildTool") ?: project?.get("build_tool")) as? String ?: "maven"

        // DEBUG: Print extracted Java version
        println("[BedrockClient] Prefill - Extracted Java version: $javaVersion (from MCP context)")
        println("[BedrockClient] Prefill - Full project map: $project")
        
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
     * Detects required services from MCP context (database, cache, messaging).
     */
    private fun detectServices(mcpContext: String): List<String> {
        val services = mutableListOf("app")
        
        try {
            val contextMap = parseContext(mcpContext)
            val project = contextMap["project"] as? Map<*, *>
            val deps = project?.get("dependencies") as? List<*> ?: emptyList<Any>()
            
            // Debug: Print dependencies
            println("[BedrockClient] Detected ${deps.size} dependencies:")
            deps.take(10).forEach { println("  - $it") }
            if (deps.size > 10) println("  ... and ${deps.size - 10} more")
            
            // Database service - try both camelCase and snake_case
            val dbType = (project?.get("databaseType") ?: project?.get("database_type")) as? String
            println("[BedrockClient] Database type from MCP: $dbType")
            if (dbType != null && dbType != "null") {
                services.add(dbType.lowercase())
            }
            
            // Redis if spring-boot-starter-data-redis present
            val hasRedis = deps.any { it.toString().contains("spring-boot-starter-data-redis") || 
                           it.toString().contains("spring-data-redis") }
            println("[BedrockClient] Redis dependency found: $hasRedis")
            if (hasRedis) {
                services.add("redis")
            }
            
            // Kafka if spring-kafka present
            val hasKafka = deps.any { it.toString().contains("spring-kafka") }
            println("[BedrockClient] Kafka dependency found: $hasKafka")
            if (hasKafka) {
                services.add("kafka")
                services.add("zookeeper")
            }
            
            // MongoDB if present
            val hasMongo = deps.any { it.toString().contains("spring-boot-starter-data-mongodb") ||
                           it.toString().contains("mongodb-driver") }
            println("[BedrockClient] MongoDB dependency found: $hasMongo")
            if (hasMongo) {
                if (!services.contains("mongodb")) {
                    services.add("mongodb")
                }
            }
            
            println("[BedrockClient] Final detected services: $services")
            
        } catch (e: Exception) {
            println("[BedrockClient] Warning: Could not detect services: ${e.message}")
        }
        
        return services
    }
    
    /**
     * Builds Docker Compose prompt with multi-service orchestration.
     * Architecture-aware: handles both Spring Boot and IntelliJ Plugin projects.
     */
    private fun buildDockerComposePrompt(mcpContext: String, services: List<String>): String {
        val architectureType = extractArchitectureType(mcpContext)
        
        return when (architectureType) {
            "INTELLIJ_PLUGIN" -> buildIntellijPluginComposePrompt(mcpContext)
            else -> buildSpringBootComposePrompt(mcpContext, services)
        }
    }
    
    /**
     * Builds Spring Boot-specific Docker Compose prompt with multi-service orchestration.
     */
    private fun buildSpringBootComposePrompt(mcpContext: String, services: List<String>): String {
        val projectName = extractProjectName(mcpContext)
        val port = extractPort(mcpContext)
        val envVars = buildEnvironmentVars(mcpContext, services)
        val databaseName = extractDatabaseName(mcpContext, projectName)

        return """
<role>You are a Docker Compose expert specializing in Spring Boot multi-service orchestration.</role>

<task>Generate a production-ready docker-compose.yml (version '3.8') for local development with service orchestration, health checks, and volume management.</task>

<mcp_context>
$mcpContext
</mcp_context>

<detected_services>
Services needed: ${services.joinToString(", ")}
</detected_services>

<instructions>
1. **App Service** (Spring Boot):
   - build: context: . / dockerfile: Dockerfile
   - container_name: $projectName
   - ports: ["$port:$port"]
   - environment:
$envVars
   - depends_on: ${services.filter { it !in listOf("app", "zookeeper") }.joinToString(", ") { "$it" }} (with service_healthy condition)
   - volumes: ["./logs:/app/logs"]
   - networks: [app-network]
   - restart: unless-stopped

2. **Database Services**:
   ${if (services.contains("mysql")) "- MySQL 8.0 with MYSQL_DATABASE: $databaseName, health check, named volume mysql_data, MYSQL_ROOT_PASSWORD: root" else ""}
   ${if (services.contains("postgresql")) "- PostgreSQL with POSTGRES_DB: $databaseName, health check, named volume postgres_data" else ""}
   ${if (services.contains("mongodb")) "- MongoDB with health check, named volume mongo_data" else ""}

3. **Cache Service**:
   ${if (services.contains("redis")) "- Redis 7-alpine with health check" else ""}

4. **Messaging Services**:
   ${if (services.contains("kafka")) "- Zookeeper and Kafka with proper configuration" else ""}

5. **Networks**: app-network with bridge driver

6. **Volumes**: Named volumes for all databases (mysql_data, postgres_data, mongo_data)

</instructions>

<output_format>
Generate ONLY the docker-compose.yml content.
Do NOT include markdown code blocks or explanatory text.
Start with: version: '3.8'
</output_format>

<quality_checklist>
✓ All services have health checks
✓ Dependencies use service_healthy condition
✓ Named volumes for data persistence
✓ Environment variables for all connections
✓ Restart policy configured
✓ Network isolation
</quality_checklist>
""".trimIndent()
    }
    
    /**
     * Builds IntelliJ Plugin-specific Docker Compose prompt.
     * For plugins, compose file is just for building the plugin distribution.
     */
    private fun buildIntellijPluginComposePrompt(mcpContext: String): String {
        val projectName = extractProjectName(mcpContext)

        return """
<role>You are a Docker Compose expert for IntelliJ IDEA plugin build automation.</role>

<task>Generate a minimal docker-compose.yml for building an IntelliJ Plugin distribution (NOT for running it).</task>

<mcp_context>
$mcpContext
</mcp_context>

<instructions>
1. **Purpose**: IntelliJ Plugins CANNOT run as standalone apps. This compose file is ONLY for building the plugin .zip distribution.

2. **Understanding the Dockerfile**:
   - The Dockerfile creates the plugin .zip in /build/build/distributions/ during build
   - The final stage copies the .zip to /plugin directory inside the container
   - We need to EXTRACT this .zip from the container to the host machine

3. **Builder Service Configuration**:
   - service name: plugin-builder
   - build:
     * context: .
     * dockerfile: Dockerfile
   - container_name: $projectName-builder
   - volumes:
     * Mount host directory to receive the built .zip: "./build/distributions:/output"
     * This allows copying FROM container TO host
   - command: Override to copy plugin artifact to host-mounted volume
     * Use: sh -c "cp -v /plugin/*.zip /output/ && echo '✓ Plugin built successfully!' && ls -lah /output/"
     * This copies the .zip from container's /plugin to the mounted /output (which is host's ./build/distributions)
   - networks: [build-network]
   - NO ports (plugins don't expose ports)
   - NO health checks (not a running service)
   - NO restart policy (one-time build)
   - NO depends_on (single service)

4. **Networks**:
   - build-network with bridge driver

5. **NO Named Volumes Needed**:
   - We use a bind mount (./build/distributions:/output) to extract the artifact
   - Named volumes are not needed for this simple build-only setup

6. **Comments**:
   - Add header comment: "# Docker Compose for building IntelliJ Plugin"
   - Add note: "# This is for BUILD ONLY - IntelliJ Plugins cannot run as standalone applications"
   - Add usage note: "# To use the plugin, install the .zip from build/distributions/ into IntelliJ IDEA"

7. **What NOT to include**:
   - NO Spring Boot configurations
   - NO database services
   - NO health check endpoints
   - NO port mappings (8080, etc.)
   - NO SPRING_PROFILES_ACTIVE environment variables
   - NO restart: unless-stopped
   - NO depends_on conditions
   - NO named volumes (use bind mount instead)
   - NO read-only volume flags (:ro) on output directory

</instructions>

<output_format>
Generate docker-compose.yml for IntelliJ Plugin BUILD environment.

CRITICAL REQUIREMENTS:
1. Volume mapping must be: ./build/distributions:/output (NOT :ro flag)
2. Command must copy files: sh -c "cp -v /plugin/*.zip /output/ && echo '✓ Plugin built successfully!' && ls -lah /output/"
3. Use version: '3.8'
4. Single service: plugin-builder

Do NOT include:
- Markdown code blocks (```yaml)
- Explanatory text outside YAML
- Named volumes section
- Spring Boot service configurations

Start with: # Docker Compose for building IntelliJ Plugin
</output_format>

<quality_checklist>
✓ Single builder service only
✓ No runtime configurations
✓ No Spring Boot settings
✓ Bind mount for extracting .zip (./build/distributions:/output)
✓ Command copies artifact from /plugin to /output
✓ Clear comments about build-only purpose
✓ No :ro flag on output volume
✓ No named volumes
</quality_checklist>
""".trimIndent()
    }

    /**
     * Builds prompt for GitHub Actions workflow generation.
     * Architecture-aware: handles both Spring Boot and IntelliJ Plugin projects.
     */
    private fun buildGitHubActionsPrompt(mcpContext: String): String {
        val architectureType = extractArchitectureType(mcpContext)

        return when (architectureType) {
            "INTELLIJ_PLUGIN" -> buildIntellijPluginGitHubActionsPrompt(mcpContext)
            else -> buildSpringBootGitHubActionsPrompt(mcpContext)
        }
    }

    /**
     * Builds Spring Boot-specific GitHub Actions workflow prompt.
     */
    private fun buildSpringBootGitHubActionsPrompt(mcpContext: String): String {
        val contextMap = parseContext(mcpContext)
        val project = contextMap["project"] as? Map<*, *>
        val buildTool = project?.get("buildTool") as? String ?: "gradle"
        println("[BedrockClient] Extracted build tool from MCP: $buildTool")
        val javaVersion = project?.get("javaVersion") as? String ?: "17"
        val projectName = project?.get("name") as? String ?: "spring-boot-app"
        val databaseType = (project?.get("databaseType") ?: project?.get("database_type")) as? String
        val hasDatabase = databaseType != null && databaseType != "null"
        println("[BedrockClient] Database type from MCP: $databaseType, hasDatabase: $hasDatabase")
        val defaultBranch = extractDefaultBranch(mcpContext)

        return """
You are a DevOps expert specializing in GitHub Actions CI/CD pipelines for Spring Boot applications.

<task>
Generate a production-ready GitHub Actions workflow YAML file for a Spring Boot project.
</task>

<CRITICAL_PROJECT_INFO>
🔴 BUILD TOOL: ${buildTool.uppercase()}
🔴 This project uses ${buildTool.uppercase()}, NOT ${if (buildTool.equals("Maven", ignoreCase = true)) "Gradle" else "Maven"}!
🔴 You MUST use ${if (buildTool.equals("Maven", ignoreCase = true)) "./mvnw (Maven wrapper)" else "./gradlew (Gradle wrapper)"} commands!
</CRITICAL_PROJECT_INFO>

<mcp_context>
$mcpContext
</mcp_context>

<requirements>
1. **Workflow Name**: CI/CD Pipeline
2. **Triggers**:
   - Pull requests to $defaultBranch branch (validates PRs before merge)
   - Manual workflow_dispatch (allows manual trigger on any branch)
   - DO NOT trigger on direct push to $defaultBranch (prevents auto-execution on merge)
3. **Jobs**:
   a. BUILD JOB:
      ${if (hasDatabase)
         """- ⚠️ CRITICAL: Add database service container for tests!
      - Database service: ${databaseType?.lowercase() ?: "mysql"}
      - Service configuration:
        * Image: ${when (databaseType?.lowercase()) {
            "mysql" -> "mysql:8.0"
            "postgresql", "postgres" -> "postgres:15-alpine"
            else -> "mysql:8.0"
        }}
        * Environment variables:
          ${when (databaseType?.lowercase()) {
              "mysql" -> "MYSQL_ROOT_PASSWORD: root, MYSQL_DATABASE: testdb"
              "postgresql", "postgres" -> "POSTGRES_PASSWORD: postgres, POSTGRES_DB: testdb"
              else -> "MYSQL_ROOT_PASSWORD: root, MYSQL_DATABASE: testdb"
          }}
        * Health check configured with retries
        * Port: ${when (databaseType?.lowercase()) {
            "mysql" -> "3306"
            "postgresql", "postgres" -> "5432"
            else -> "3306"
        }}
      """
      else ""
      }
      - Checkout code
      - Setup Java $javaVersion (Temurin distribution)${if (buildTool.equals("Maven", ignoreCase = true)) " with Maven" else ""}
      ${if (buildTool.equals("Maven", ignoreCase = true))
         "- ⚠️ CRITICAL: Use system Maven command 'mvn' (NOT './mvnw')! The setup-java action installs Maven, so use 'mvn' directly."
      else
         "- Make Gradle wrapper executable: chmod +x ./gradlew"
      }
      - Cache ${if (buildTool.equals("Maven", ignoreCase = true)) "Maven dependencies (~/.m2)" else "Gradle dependencies (~/.gradle)"}
      ${if (buildTool.equals("Maven", ignoreCase = true))
         """- Build command with retry logic (Maven Central can be flaky):
        Use a retry loop with 3 attempts and 10 second delays
        Command: mvn clean package -DskipTests -B
      - Test command with retry logic:
        Use a retry loop with 3 attempts and 10 second delays
        Command: mvn test -B
      - ⚠️ DO NOT use './mvnw' - use 'mvn' instead!
      - ⚠️ DO NOT add 'Make Maven wrapper executable' step!
      - ⚠️ CRITICAL: Add retry logic for BOTH build AND test commands to handle transient failures"""
      else
         """- Build command: ./gradlew build -x test
      - Test command: ./gradlew test"""
      }
      - Upload artifact from ${if (buildTool.equals("Maven", ignoreCase = true)) "target/*.jar" else "build/libs/*.jar"}

   b. DOCKER JOB (depends on build):
      - Download build artifact
      - Setup Docker Buildx
      - Login to GitHub Container Registry (ghcr.io)
      - Build and push Docker image
      - ⚠️ CRITICAL: Use metadata-action for tags with SAFE tag formats:
        * type=ref,event=pr (for pull requests, generates "pr-NUMBER")
        * type=sha (for commit SHA, generates short SHA without prefix)
        * DO NOT use type=sha with prefix={{branch}}- as it can create invalid tags
        * For latest tag: type=raw,value=latest,enable=${'$'}{{is_default_branch}}
      - Enable layer caching with type=gha

   c. SECURITY JOB (depends on docker):
      - Run Trivy vulnerability scanner on Docker image
        ⚠️ CRITICAL: Image reference must match the EXACT tags that were pushed
        The metadata-action generates these tags:
          type=ref,event=pr → "pr-9" (for PR #9)
          type=sha → "sha-b68600d" (short SHA with sha- prefix)
        For pull_request: ${'$'}{{env.REGISTRY}}/${'$'}{{env.IMAGE_NAME}}:pr-${'$'}{{github.event.pull_request.number}}
        For workflow_dispatch: ${'$'}{{env.REGISTRY}}/${'$'}{{env.IMAGE_NAME}}:sha-${'$'}{{github.sha}}
        Use conditional: ${'$'}{{ github.event_name == 'pull_request' && format('{{0}}/{{1}}:pr-{{2}}', env.REGISTRY, env.IMAGE_NAME, github.event.pull_request.number) || format('{{0}}/{{1}}:sha-{{2}}', env.REGISTRY, env.IMAGE_NAME, github.sha) }}
      - Upload Trivy results to GitHub Security (SARIF format)
      - Run Hadolint on Dockerfile
      - Upload Hadolint results to GitHub Security (SARIF format)
</requirements>

<instructions>
1. Use latest stable actions: checkout@v4, setup-java@v4, upload-artifact@v4
2. Enable dependency caching for faster builds
3. Use GITHUB_TOKEN secret (built-in, no configuration needed)
4. Set proper permissions for packages (write access)
5. Include proper job dependencies (build → docker → security)
6. Add environment variables:
   - JAVA_VERSION: '$javaVersion'
   - SPRING_BOOT_VERSION: (Spring Boot version from MCP)
   - REGISTRY: ghcr.io
   - IMAGE_NAME: ⚠️ CRITICAL - MUST be lowercase for ghcr.io!
     Use: ${'$'}{{ github.repository }} | tr '[:upper:]' '[:lower:]'
     OR: echo "${'$'}{{ github.repository }}" | awk '{{print tolower(${'$'}0)}}'
     GitHub Container Registry REJECTS uppercase image names!
7. Use ubuntu-latest runners
8. Include comments explaining each step
9. ${if (buildTool.equals("Maven", ignoreCase = true)) "CRITICAL: You MUST use 'mvn' (system Maven) for all Maven commands, NOT './mvnw'. Do NOT add 'chmod +x ./mvnw' step. The setup-java action already provides Maven." else "CRITICAL: You MUST use './gradlew' (Gradle wrapper) for all Gradle commands, NOT 'gradle'. Add 'chmod +x ./gradlew' step BEFORE first Gradle command."}
10. ${if (buildTool.equals("Maven", ignoreCase = true)) "For Maven: NO wrapper step needed. Use 'mvn' directly." else "For Gradle: The wrapper executable step must come IMMEDIATELY after Java setup and BEFORE caching"}
11. IMPORTANT: Only trigger on pull_request and workflow_dispatch. Do NOT include 'push:' trigger for $defaultBranch branch.
</instructions>

<output_format>
Generate ONLY the YAML content for .github/workflows/build.yml
Start with:
name: CI/CD Pipeline

Do NOT include markdown code blocks (```) or explanations.
</output_format>

<quality_checklist>
- [ ] Workflow triggers ONLY on pull_request and workflow_dispatch (NO push trigger)
- [ ] Java version matches project ($javaVersion)
- [ ] Build commands use ${if (buildTool.equals("Maven", ignoreCase = true)) "mvn (system Maven, NOT ./mvnw)" else "./gradlew (NOT gradle)"}
- [ ] ${if (buildTool.equals("Maven", ignoreCase = true)) "NO 'chmod +x ./mvnw' step exists (Maven wrapper not used)" else "chmod +x ./gradlew step exists BEFORE first build command"}
- [ ] Artifact path is ${if (buildTool.equals("Maven", ignoreCase = true)) "target/*.jar" else "build/libs/*.jar"}
- [ ] Caching uses ${if (buildTool.equals("Maven", ignoreCase = true)) "~/.m2" else "~/.gradle"}
- [ ] Docker image tags are VALID (no tags starting with hyphen or special characters)
- [ ] Docker metadata-action uses: type=ref,event=pr AND type=sha (WITHOUT prefix) AND type=raw for latest
- [ ] ${if (hasDatabase) "Database service container configured with proper health checks" else "No database service needed"}
- [ ] Security scanning included
- [ ] Secrets properly referenced
- [ ] Job dependencies correct (build → docker → security)
</quality_checklist>
""".trimIndent()
    }

    /**
     * Builds IntelliJ Plugin-specific GitHub Actions workflow prompt.
     * Focuses on plugin building and distribution (not JAR files).
     */
    private fun buildIntellijPluginGitHubActionsPrompt(mcpContext: String): String {
        val contextMap = parseContext(mcpContext)
        val project = contextMap["project"] as? Map<*, *>
        val buildTool = project?.get("buildTool") as? String ?: "gradle"
        val javaVersion = project?.get("javaVersion") as? String ?: "17"
        val projectName = project?.get("name") as? String ?: "intellij-plugin"
        val defaultBranch = extractDefaultBranch(mcpContext)

        return """
You are a DevOps expert specializing in GitHub Actions CI/CD pipelines for IntelliJ IDEA plugin projects.

<task>
Generate a production-ready GitHub Actions workflow YAML file for an IntelliJ IDEA plugin project.
</task>

<mcp_context>
$mcpContext
</mcp_context>

<critical_understanding>
This is an IntelliJ IDEA Plugin project, NOT a Spring Boot application.
Key differences:
- Build command: ./gradlew buildPlugin (NOT ./gradlew build)
- Artifact: build/distributions/*.zip (NOT build/libs/*.jar)
- No Spring Boot environment variables (no SPRING_BOOT_VERSION)
- Artifact name should reflect plugin nature (e.g., "intellij-plugin", NOT "spring-boot-jar")
</critical_understanding>

<requirements>
1. **Workflow Name**: CI/CD Pipeline

2. **Triggers**:
   - Pull requests to $defaultBranch branch (validates PRs before merge)
   - Manual workflow_dispatch (allows manual trigger on any branch)
   - DO NOT trigger on direct push to $defaultBranch (prevents auto-execution on merge)

3. **Environment Variables**:
   - JAVA_VERSION: '$javaVersion'
   - REGISTRY: ghcr.io
   - IMAGE_NAME: ${'$'}{{ github.repository }}
   - DO NOT include SPRING_BOOT_VERSION (this is NOT a Spring Boot app)

4. **Permissions**:
   - contents: read
   - packages: write
   - security-events: write

5. **Jobs**:

   a. BUILD JOB (Name: "Build and Test IntelliJ Plugin"):
      - Checkout code
      - Setup Java $javaVersion (Temurin distribution)
      - Cache ${if (buildTool == "maven") "Maven" else "Gradle"} dependencies
      - Make gradlew executable (chmod +x ./gradlew)
      - Build plugin: ./gradlew buildPlugin --no-daemon
      - Run tests: ./gradlew test
      - Upload test results artifact (build/reports/tests/)
      - Upload plugin artifact (build/distributions/*.zip) with name "intellij-plugin", 30 days retention

   b. DOCKER JOB (Name: "Build and Push Docker Image", depends on build):
      - Checkout code
      - Download plugin artifact from build job
      - Place artifact in build/distributions/ directory
      - Setup Docker Buildx
      - Login to GitHub Container Registry (ghcr.io)
      - Extract metadata for tags (branch, PR, SHA, latest)
      - Build and push Docker image with multi-platform support (linux/amd64,linux/arm64)
      - Enable layer caching (type=gha)
      - Output image reference and digest for security job

   c. SECURITY JOB (Name: "Security Scanning", depends on docker):
      - Checkout code
      - Run Hadolint on Dockerfile (format: sarif, no-fail: true)
      - Upload Hadolint results to GitHub Security
      - Run Trivy vulnerability scanner on built image (use digest from docker job)
      - Upload Trivy results to GitHub Security
      - Upload both SARIF files as artifacts (30 days retention)

</requirements>

<instructions>
1. Use latest stable actions: checkout@v4, setup-java@v4, upload-artifact@v4, download-artifact@v4
2. Enable Gradle dependency caching for faster builds
3. Use GITHUB_TOKEN secret (built-in, no configuration needed)
4. Set proper permissions for packages (write access)
5. Include proper job dependencies (build → docker → security)
6. Add environment variables for Java version and registry (NO Spring Boot version)
7. Use ubuntu-latest runners
8. Include comments explaining each step
9. Docker job must output image reference and digest for security scanning
10. Artifact names must reflect plugin nature (intellij-plugin, NOT spring-boot-jar)
11. Build command MUST be ./gradlew buildPlugin --no-daemon
12. Artifact path MUST be build/distributions/*.zip
</instructions>

<output_format>
Generate ONLY the YAML content for .github/workflows/build.yml
Start with:
name: CI/CD Pipeline

Do NOT include:
- Markdown code blocks (```)
- Explanatory text outside YAML
- Spring Boot references
- JAR file artifact paths
- SPRING_BOOT_VERSION environment variable
</output_format>

<quality_checklist>
- [ ] Workflow triggers on push and PR
- [ ] Java version matches project ($javaVersion)
- [ ] Build command is ./gradlew buildPlugin --no-daemon (NOT ./gradlew build)
- [ ] Artifact path is build/distributions/*.zip (NOT build/libs/*.jar)
- [ ] Artifact name is "intellij-plugin" (NOT "spring-boot-jar")
- [ ] NO SPRING_BOOT_VERSION environment variable
- [ ] Caching configured correctly for Gradle
- [ ] Docker image tagged properly
- [ ] Security scanning included (Trivy + Hadolint)
- [ ] Secrets properly referenced
- [ ] Job dependencies correct (build → docker → security)
- [ ] Docker job outputs image reference and digest
- [ ] Security job uses image digest from docker job
</quality_checklist>
""".trimIndent()
    }

    private fun extractProjectName(mcpContext: String): String {
        return try {
            val contextMap = parseContext(mcpContext)
            val project = contextMap["project"] as? Map<*, *>
            (project?.get("name") as? String)?.replace(" ", "-")?.lowercase() ?: "spring-app"
        } catch (e: Exception) {
            "spring-app"
        }
    }
    
    private fun extractPort(mcpContext: String): String {
        return try {
            val contextMap = parseContext(mcpContext)
            val config = contextMap["configuration"] as? Map<*, *>
            // Try both camelCase and snake_case (Jackson serializes as camelCase by default)
            val port = (config?.get("serverPort") ?: config?.get("server_port")) as? String ?: "8080"
            println("[BedrockClient] Extracted port from MCP: $port")
            port
        } catch (e: Exception) {
            "8080"
        }
    }

    /**
     * Extracts the default branch name from MCP context (for GitHub repos).
     * Falls back to "main" if not found.
     */
    private fun extractDefaultBranch(mcpContext: String): String {
        return try {
            val contextMap = parseContext(mcpContext)
            val metadata = contextMap["metadata"] as? Map<*, *>
            val defaultBranch = (metadata?.get("default_branch") ?: metadata?.get("defaultBranch")) as? String
            val branch = defaultBranch ?: "main"
            println("[BedrockClient] Extracted default branch from MCP: $branch")
            branch
        } catch (e: Exception) {
            println("[BedrockClient] Failed to extract default branch, using 'main': ${e.message}")
            "main"
        }
    }

    private fun extractDatabaseName(mcpContext: String, projectName: String): String {
        return try {
            val contextMap = parseContext(mcpContext)
            val config = contextMap["configuration"] as? Map<*, *>

            // Debug: Print the entire configuration map
            println("[BedrockClient] Configuration map keys: ${config?.keys}")
            println("[BedrockClient] Configuration map: $config")

            // Try both camelCase and snake_case
            val datasourceUrl = (config?.get("datasourceUrl") ?: config?.get("datasource_url")) as? String
            println("[BedrockClient] Datasource URL found: $datasourceUrl")

            if (datasourceUrl != null && datasourceUrl.isNotBlank()) {
                // Extract database name from JDBC URL (e.g., jdbc:mysql://localhost:3306/businessproject)
                val dbName = datasourceUrl.substringAfterLast("/").substringBefore("?")
                println("[BedrockClient] Extracted database name from datasource URL: '$dbName'")
                return dbName
            }

            println("[BedrockClient] No datasource URL found, using project name as database name: $projectName")
            projectName
        } catch (e: Exception) {
            println("[BedrockClient] Error extracting database name: ${e.message}, using project name: $projectName")
            projectName
        }
    }
    
    private fun buildEnvironmentVars(mcpContext: String, services: List<String>): String {
        val vars = mutableListOf<String>()

        try {
            val contextMap = parseContext(mcpContext)
            val project = contextMap["project"] as? Map<*, *>
            val projectName = (project?.get("name") as? String)?.replace(" ", "-")?.lowercase() ?: "app"

            // Extract actual database name from datasource URL in application.properties
            val databaseName = extractDatabaseName(mcpContext, projectName)

            // Database connection
            when {
                services.contains("mysql") -> {
                    vars.add("       SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/$databaseName")
                    vars.add("       SPRING_DATASOURCE_USERNAME: root")
                    vars.add("       SPRING_DATASOURCE_PASSWORD: root")
                }
                services.contains("postgresql") -> {
                    vars.add("       SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/$databaseName")
                    vars.add("       SPRING_DATASOURCE_USERNAME: postgres")
                    vars.add("       SPRING_DATASOURCE_PASSWORD: postgres")
                }
                services.contains("mongodb") -> {
                    vars.add("       SPRING_DATA_MONGODB_URI: mongodb://mongodb:27017/$databaseName")
                }
            }
            
            // Redis connection
            if (services.contains("redis")) {
                vars.add("       SPRING_REDIS_HOST: redis")
                vars.add("       SPRING_REDIS_PORT: 6379")
            }
            
            // Kafka connection
            if (services.contains("kafka")) {
                vars.add("       SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092")
            }
            
        } catch (e: Exception) {
            println("[BedrockClient] Warning: Could not build environment vars: ${e.message}")
        }
        
        return if (vars.isEmpty()) "       # No external services detected" else vars.joinToString("\n")
    }
    
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
