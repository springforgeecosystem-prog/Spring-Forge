package org.springforge.cicdassistant.prompts

import org.springforge.cicdassistant.services.ProjectAnalyzerService.ProjectInfo
import org.springforge.cicdassistant.parsers.ArchitectureType
/**
 * Builds intelligent Dockerfile generation prompts based on AST-analyzed project structure
 * Rather than hardcoded conditions, uses actual code analysis
 */
class DockerfilePromptBuilder {

    fun buildPrompt(projectInfo: ProjectInfo): String {
        val codeStructure = projectInfo.codeStructure

        //  Handle IntelliJ Plugin projects differently
        if (projectInfo.architectureType == "INTELLIJ_PLUGIN") {
            return buildIntellijPluginPrompt(projectInfo)
        }

        return buildString {
            appendLine("# Task: Generate a production-ready Dockerfile")
            appendLine()
            appendLine("## Project Analysis:")
            appendLine("- Name: ${projectInfo.projectName}")
            appendLine("- Type: ${projectInfo.architectureType}")
            appendLine("- Spring Boot: ${projectInfo.springBootVersion}")
            appendLine("- Java: ${projectInfo.javaVersion}")
            appendLine("- Build Tool: ${projectInfo.buildTool}")
            appendLine()

            //  Code Structure Insights (from AST)
            if (codeStructure != null) {
                appendLine("## Code Structure Analysis:")
                appendLine("- Main Class: ${codeStructure.mainClass}")
                appendLine("- REST Controllers: ${codeStructure.restControllers.size}")
                appendLine("- API Endpoints: ${projectInfo.apiEndpointCount}")
                
                if (codeStructure.restControllers.isNotEmpty()) {
                    appendLine("- Exposed Endpoints:")
                    codeStructure.restControllers.forEach { controller ->
                        controller.endpoints.take(3).forEach { endpoint ->
                            appendLine("  * ${endpoint.method} ${endpoint.path}")
                        }
                    }
                }

                appendLine("- Services: ${codeStructure.services.size}")
                appendLine("- Repositories: ${codeStructure.repositories.size}")
                appendLine("- Database Entities: ${codeStructure.entities.size}")

                if (codeStructure.scheduledTasks.isNotEmpty()) {
                    appendLine("- Has Scheduled Tasks: YES (${codeStructure.scheduledTasks.size} tasks)")
                }

                if (codeStructure.messageListeners.isNotEmpty()) {
                    appendLine("- Message Listeners: ${codeStructure.messageListeners.size}")
                    codeStructure.messageListeners.forEach { listener ->
                        appendLine("  * ${listener.listenerType}: ${listener.className}.${listener.methodName}")
                    }
                }

                if (codeStructure.securityConfig != null) {
                    appendLine("- Security: ${if (codeStructure.securityConfig.hasWebSecurity) "Enabled" else "None"}")
                    if (codeStructure.securityConfig.hasOAuth2) {
                        appendLine("  * OAuth2/JWT detected")
                    }
                }
                appendLine()
            }

            // Database Configuration
            if (projectInfo.hasDatabase) {
                appendLine("## Database:")
                appendLine("- Type: ${projectInfo.databaseType}")
                if (codeStructure?.entities?.isNotEmpty() == true) {
                    appendLine("- JPA Entities: ${codeStructure.entities.size}")
                    appendLine("- Tables: ${codeStructure.entities.joinToString(", ") { it.tableName }}")
                }
                appendLine()
            }

            // Messaging & Async
            val messagingServices = listOfNotNull(
                if (projectInfo.hasKafka) "Kafka" else null,
                if (projectInfo.hasRabbitMQ) "RabbitMQ" else null,
                if (projectInfo.hasRedis) "Redis" else null
            )
            if (messagingServices.isNotEmpty()) {
                appendLine("## Messaging/Caching:")
                messagingServices.forEach { appendLine("- $it") }
                appendLine()
            }

            // Architecture-Specific Requirements
            appendLine("## Dockerfile Requirements:")
            appendLine()
            appendLine("### Base Requirements:")
            appendLine("1. Use multi-stage build for optimization")
            appendLine("2. Base image: eclipse-temurin:${projectInfo.javaVersion}-jdk-alpine")
            appendLine("3. Create non-root user 'appuser' with UID 1000")
            appendLine("4. Set working directory: /app")
            appendLine("5. Copy only necessary files (avoid bloat)")
            appendLine()

            when (projectInfo.architectureType) {
                "REACTIVE_WEBFLUX" -> {
                    appendLine("### Reactive Architecture Optimizations:")
                    appendLine("- Use Netty server (Spring WebFlux default)")
                    appendLine("- Set JVM options for reactive workloads:")
                    appendLine("  * -Xmx512m -Xms256m")
                    appendLine("  * -XX:+UseG1GC (for low-latency)")
                    appendLine("- No Tomcat configuration needed")
                }
                "EVENT_DRIVEN" -> {
                    appendLine("### Event-Driven Architecture Considerations:")
                    appendLine("- Application listens to ${codeStructure?.messageListeners?.size ?: 0} event streams")
                    appendLine("- May not expose HTTP ports (check code)")
                    appendLine("- Add health check on actuator if available")
                    appendLine("- Ensure graceful shutdown for message processing")
                }
                "MICROSERVICE" -> {
                    appendLine("### Microservice Optimizations:")
                    appendLine("- Keep image size minimal (< 150MB)")
                    appendLine("- Fast startup time critical")
                    appendLine("- Include actuator health checks")
                    appendLine("- Support service mesh readiness probes")
                }
                else -> {
                    appendLine("### Standard Spring Boot Configuration:")
                    appendLine("- Default Tomcat server")
                    appendLine("- JVM options: -Xmx512m -Xms256m")
                    appendLine("- Standard health checks")
                }
            }
            appendLine()

            // Port Configuration
            appendLine("### Network Configuration:")
            appendLine("- Primary Port: ${projectInfo.port} (from analysis)")
            if (projectInfo.hasRestControllers) {
                appendLine("- Expose port ${projectInfo.port} for REST API")
            }
            appendLine("- Health Check: GET /actuator/health on port ${projectInfo.port}")
            appendLine()

            // Security
            appendLine("### Security Hardening:")
            appendLine("1. Run as non-root user (UID 1000)")
            appendLine("2. Remove unnecessary packages in Alpine image")
            appendLine("3. No secrets in environment variables")
            appendLine("4. Set read-only root filesystem if possible")
            if (codeStructure?.securityConfig?.hasOAuth2 == true) {
                appendLine("5. Ensure OAuth2 secrets come from external config")
            }
            appendLine()

            // Build Instructions
            appendLine("### Build Process:")
            when (projectInfo.buildTool.name) {
                "MAVEN" -> {
                    appendLine("```dockerfile")
                    appendLine("# Stage 1: Build")
                    appendLine("FROM eclipse-temurin:${projectInfo.javaVersion}-jdk-alpine AS builder")
                    appendLine("WORKDIR /build")
                    appendLine("COPY pom.xml .")
                    appendLine("COPY src ./src")
                    appendLine("RUN ./mvnw clean package -DskipTests")
                    appendLine()
                    appendLine("# Stage 2: Runtime")
                    appendLine("FROM eclipse-temurin:${projectInfo.javaVersion}-jre-alpine")
                    appendLine("# ... (continue with best practices)")
                    appendLine("```")
                }
                "GRADLE" -> {
                    appendLine("```dockerfile")
                    appendLine("# Stage 1: Build")
                    appendLine("FROM eclipse-temurin:${projectInfo.javaVersion}-jdk-alpine AS builder")
                    appendLine("WORKDIR /build")
                    appendLine("COPY build.gradle.kts settings.gradle.kts gradlew .")
                    appendLine("COPY gradle ./gradle")
                    appendLine("COPY src ./src")
                    appendLine("RUN ./gradlew clean build -x test")
                    appendLine("```")
                }
                else -> {}
            }
            appendLine()

            appendLine("## Output:")
            appendLine("Generate a complete, production-ready Dockerfile that:")
            appendLine("- Implements all requirements above")
            appendLine("- Follows Docker best practices")
            appendLine("- Is optimized for ${projectInfo.architectureType} architecture")
            appendLine("- Includes inline comments explaining each step")
            appendLine("- Passes Hadolint validation")
        }
    }

    /**
     *  Build IntelliJ Plugin-specific Dockerfile prompt
     * NOW WITH ACTUAL PROJECT STRUCTURE VERIFICATION
     * 
     * Uses Claude's prompt engineering best practices:
     * 1. Be clear and direct with XML tags
     * 2. Use examples (multishot)
     * 3. Chain of thought reasoning
     * 4. Prefill expectations
     */
    private fun buildIntellijPluginPrompt(projectInfo: ProjectInfo): String {
        //  Analyze actual project structure (NO ASSUMPTIONS!)
        val structureAnalyzer = org.springforge.cicdassistant.analyzer.ProjectStructureAnalyzer()
        val projectDir = java.io.File(System.getProperty("user.dir"))
        val structure = structureAnalyzer.analyze(projectDir)
        
        return buildString {
            // Use XML tags for clear structure (Claude best practice #5)
            appendLine("<task>")
            appendLine("Generate a production-ready Dockerfile for an IntelliJ IDEA Plugin.")
            appendLine("</task>")
            appendLine()
            
            // Give Claude a role (Claude best practice #6)
            appendLine("<role>")
            appendLine("You are an expert DevOps engineer specializing in containerizing IntelliJ IDEA plugins.")
            appendLine("You MUST verify all facts against the provided project structure before generating.")
            appendLine("</role>")
            appendLine()
            
            // Be clear and direct (Claude best practice #2)
            appendLine("<project_info>")
            appendLine("- Name: ${projectInfo.projectName}")
            appendLine("- Type: IntelliJ IDEA Plugin")
            appendLine("- Java Version: ${projectInfo.javaVersion}")
            appendLine("- Build Tool: ${projectInfo.buildTool}")
            appendLine("</project_info>")
            appendLine()
            
            // VERIFIED facts with XML structure
            appendLine("<verified_structure>")
            appendLine("The following project structure has been VERIFIED by analyzing the actual filesystem:")
            appendLine()
            appendLine("<gradle_wrapper>")
            appendLine("Status: ${if (structure.hasGradleWrapper) "EXISTS" else "MISSING"}")
            appendLine("Files: ${if (structure.hasGradleWrapper) "gradlew, gradlew.bat" else "NONE"}")
            appendLine("Action: ${if (structure.hasGradleWrapper) "USE ./gradlew" else "INSTALL gradle"}")
            appendLine("</gradle_wrapper>")
            appendLine()
            appendLine("<gradle_directory>")
            appendLine("Status: ${if (structure.hasGradleDir) "EXISTS" else "MISSING"}")
            appendLine("Path: ${if (structure.hasGradleDir) "gradle/" else "NONE"}")
            appendLine("</gradle_directory>")
            appendLine()
            appendLine("<build_files>")
            structure.buildFiles.forEach { file ->
                appendLine("- $file")
            }
            appendLine("</build_files>")
            appendLine()
            appendLine("<source_directories>")
            structure.sourceDirectories.forEach { dir ->
                appendLine("- $dir")
            }
            appendLine("</source_directories>")
            appendLine()
            appendLine("<plugin_xml_location>")
            appendLine("${structure.pluginXmlLocation ?: "NOT FOUND"}")
            appendLine("Note: This is INSIDE src/ directory - do NOT copy separately")
            appendLine("</plugin_xml_location>")
            appendLine("</verified_structure>")
            appendLine()
            // Clear requirements with XML tags
            appendLine("<requirements>")
            appendLine("<stage_1_build>")
            appendLine("- Base image: eclipse-temurin:${projectInfo.javaVersion}-jdk-alpine")
            appendLine("- Install bash AND dos2unix: RUN apk add --no-cache bash dos2unix")
            appendLine("- Working directory: /build")
            appendLine("- Fix gradlew line endings: RUN dos2unix gradlew && chmod +x ./gradlew")
            appendLine("  Reason: Windows CRLF line endings cause '/bin/sh: ./gradlew: not found' on Linux")
            appendLine("- Build command: ${if (structure.hasGradleWrapper) "./gradlew buildPlugin --no-daemon" else "gradle buildPlugin --no-daemon"}")
            appendLine("- Output: build/distributions/*.zip")
            appendLine("</stage_1_build>")
            appendLine()
            appendLine("<stage_2_distribution>")
            appendLine("- Base image: alpine:3.18")
            appendLine("- Create non-root user: adduser -D -u 1000 appuser")
            appendLine("- Working directory: /plugin")
            appendLine("- Copy ONLY: /build/build/distributions/*.zip")
            appendLine("- User: appuser")
            appendLine("- CMD: Show artifacts (not a running service)")
            appendLine("</stage_2_distribution>")
            appendLine()
            appendLine("<security>")
            appendLine("- Non-root user execution")
            appendLine("- Minimal Alpine base")
            appendLine("- No unnecessary packages")
            appendLine("- Do NOT use 'COPY . .' (copies secrets like .env)")
            appendLine("</security>")
            appendLine("</requirements>")
            appendLine()
            // Use multishot examples (Claude best practice #3)
            appendLine("<copy_instructions>")
            appendLine("These are the EXACT, VERIFIED commands to use. Do NOT deviate:")
            appendLine()
            val copyInstructions = structureAnalyzer.generateCopyInstructions(structure)
            copyInstructions.forEach { instruction ->
                appendLine(instruction)
            }
            if (structure.hasGradleWrapper) {
                appendLine("RUN dos2unix gradlew && chmod +x ./gradlew")
            }
            appendLine("</copy_instructions>")
            appendLine()
            appendLine("<forbidden_actions>")
            appendLine("The following actions are FORBIDDEN and will cause build failure:")
            appendLine()
            appendLine("1. NEVER use 'COPY . .'")
            appendLine("   Why: Copies secrets (.env), build artifacts, .git history")
            appendLine("   Impact: Security vulnerability, bloated image size")
            appendLine()
            appendLine("2. NEVER install Gradle if gradlew EXISTS")
            appendLine("   Wrapper status: ${if (structure.hasGradleWrapper) "EXISTS" else "MISSING"}")
            appendLine("   Correct action: ${if (structure.hasGradleWrapper) "Use ./gradlew" else "Install Gradle"}")
            appendLine()
            appendLine("3. NEVER copy plugin.xml or META-INF/ separately")
            appendLine("   Location: ${structure.pluginXmlLocation}")
            appendLine("   Reason: Already included in 'COPY src/ ./src/'")
            appendLine()
            appendLine("4. NEVER use 'gradle build' - must use 'buildPlugin'")
            appendLine("   Reason: IntelliJ plugins require buildPlugin task for .zip creation")
            appendLine("</forbidden_actions>")
            appendLine()
            // Mandatory structure instead of example
            appendLine("<mandatory_structure>")
            appendLine("Your Dockerfile MUST follow this exact structure:")
            appendLine()
            appendLine("STAGE 1 Requirements:")
            appendLine("- FROM eclipse-temurin:${projectInfo.javaVersion}-jdk-alpine AS builder")
            appendLine("- Install bash AND dos2unix: RUN apk add --no-cache bash dos2unix")
            appendLine("- WORKDIR /build")
            appendLine("- Use EXACT COPY commands from <copy_instructions>")
            appendLine("- Fix line endings THEN make executable: RUN dos2unix gradlew && chmod +x ./gradlew")
            appendLine("- RUN ${if (structure.hasGradleWrapper) "./gradlew" else "gradle"} buildPlugin --no-daemon")
            appendLine("- Verification: RUN ls -la /build/build/distributions/")
            appendLine()
            appendLine("STAGE 2 Requirements:")
            appendLine("- FROM alpine:3.18")
            appendLine("- RUN adduser -D -u 1000 appuser")
            appendLine("- WORKDIR /plugin")
            appendLine("- COPY --from=builder --chown=appuser:appuser /build/build/distributions/*.zip ./")
            appendLine("- USER appuser")
            appendLine("- CMD [\"ls\", \"-lah\", \"/plugin\"]")
            appendLine("</mandatory_structure>")
            appendLine()
            appendLine("<output_format>")
            appendLine("CRITICAL - Your response format:")
            appendLine("1. Continue from where the assistant prefill left off")
            appendLine("2. Include ONLY Dockerfile instructions (COPY, RUN, FROM, etc.)")
            appendLine("3. NO markdown code fences (```dockerfile)")
            appendLine("4. NO explanatory text or summaries before/after")
            appendLine("5. NO comments like 'wrapper is missing' or 'based on analysis'")
            appendLine("6. Use ONLY the COPY commands from <copy_instructions>")
            appendLine("7. Comments should explain what each step does, not what was detected")
            appendLine("</output_format>")
            appendLine()
            appendLine("<output_instructions>")
            appendLine("Generate a complete, production-ready Dockerfile that:")
            appendLine()
            appendLine("MUST include:")
            appendLine("- Two-stage build (builder + distribution)")
            appendLine("- Exact COPY commands from <copy_instructions>")
            appendLine("- ${if (structure.hasGradleWrapper) "./gradlew buildPlugin" else "gradle buildPlugin"} command")
            appendLine("- Non-root user in final stage")
            appendLine("- Comments explaining each step")
            appendLine()
            appendLine("MUST NOT include:")
            appendLine("- 'COPY . .' command")
            appendLine("- ${if (structure.hasGradleWrapper) "Gradle installation (wrapper exists)" else "Wrapper files (missing)"}")
            appendLine("- Separate plugin.xml or META-INF/ copies")
            appendLine("- EXPOSE directives")
            appendLine("- HEALTHCHECK directives")
            appendLine("</output_instructions>")
            appendLine()
            appendLine("<verification_checklist>")
            appendLine("Your Dockerfile MUST have:")
            appendLine("✓ Starting with: # Stage 1: Build Plugin (from prefill)")
            appendLine("✓ FROM eclipse-temurin:${projectInfo.javaVersion}-jdk-alpine AS builder")
            appendLine("✓ Install bash AND dos2unix: RUN apk add --no-cache bash dos2unix")
            appendLine("✓ NO gradle installation (wrapper exists)")
            appendLine("✓ COPY gradlew ./")
            appendLine("✓ COPY gradle/ ./gradle/")
            appendLine("✓ Fix line endings: RUN dos2unix gradlew && chmod +x ./gradlew")
            appendLine("✓ RUN ${if (structure.hasGradleWrapper) "./gradlew" else "gradle"} buildPlugin --no-daemon")
            appendLine("✓ NO 'COPY . .' command")
            appendLine("✓ NO EXPOSE or HEALTHCHECK directives")
            appendLine("✓ Non-root user in stage 2")
            appendLine("</verification_checklist>")
        }
    }
}
