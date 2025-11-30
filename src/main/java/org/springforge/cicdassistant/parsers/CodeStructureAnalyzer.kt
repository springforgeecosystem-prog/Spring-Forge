package org.springforge.cicdassistant.parsers

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import java.io.File

/**
 * Analyzes Spring Boot source code using AST to understand application structure
 */
class CodeStructureAnalyzer {

    private val javaParser = JavaParser()

    /**
     * Analyzes a Spring Boot project directory and returns code structure
     */
    fun analyze(projectDir: File): CodeStructure {
        val srcDir = File(projectDir, "src/main/java")
        if (!srcDir.exists()) {
            throw IllegalArgumentException("Source directory not found: ${srcDir.absolutePath}")
        }

        val controllers = mutableListOf<ControllerInfo>()
        val services = mutableListOf<ServiceInfo>()
        val repositories = mutableListOf<RepositoryInfo>()
        val entities = mutableListOf<EntityInfo>()
        val scheduledTasks = mutableListOf<ScheduledTaskInfo>()
        val messageListeners = mutableListOf<ListenerInfo>()
        var mainClass: String? = null
        var securityConfig: SecurityInfo? = null

        // Walk through all Java files
        srcDir.walkTopDown().forEach { file ->
            if (file.extension == "java") {
                try {
                    val cu = javaParser.parse(file).result.orElse(null)
                    cu?.let { compilationUnit ->
                        analyzeSingleFile(
                            compilationUnit,
                            controllers,
                            services,
                            repositories,
                            entities,
                            scheduledTasks,
                            messageListeners
                        )

                        // Find main class
                        if (mainClass == null) {
                            mainClass = findMainClass(compilationUnit)
                        }

                        // Find security config
                        if (securityConfig == null) {
                            securityConfig = findSecurityConfig(compilationUnit)
                        }
                    }
                } catch (e: Exception) {
                    println("Failed to parse ${file.name}: ${e.message}")
                }
            }
        }

        return CodeStructure(
            mainClass = mainClass ?: "Unknown",
            restControllers = controllers,
            services = services,
            repositories = repositories,
            entities = entities,
            scheduledTasks = scheduledTasks,
            messageListeners = messageListeners,
            securityConfig = securityConfig,
            architectureType = detectArchitectureType(controllers, messageListeners, services)
        )
    }

    private fun analyzeSingleFile(
        cu: CompilationUnit,
        controllers: MutableList<ControllerInfo>,
        services: MutableList<ServiceInfo>,
        repositories: MutableList<RepositoryInfo>,
        entities: MutableList<EntityInfo>,
        scheduledTasks: MutableList<ScheduledTaskInfo>,
        messageListeners: MutableList<ListenerInfo>
    ) {
        cu.types.forEach { typeDeclaration ->
            if (typeDeclaration is ClassOrInterfaceDeclaration) {
                val className = typeDeclaration.nameAsString

                // Check for @RestController or @Controller
                if (hasAnnotation(typeDeclaration, "RestController", "Controller")) {
                    controllers.add(parseController(typeDeclaration))
                }

                // Check for @Service
                if (hasAnnotation(typeDeclaration, "Service")) {
                    services.add(parseService(typeDeclaration))
                }

                // Check for @Repository
                if (hasAnnotation(typeDeclaration, "Repository")) {
                    repositories.add(parseRepository(typeDeclaration))
                }

                // Check for @Entity
                if (hasAnnotation(typeDeclaration, "Entity")) {
                    entities.add(parseEntity(typeDeclaration))
                }

                // Check for @Scheduled methods
                typeDeclaration.methods.forEach { method ->
                    if (hasAnnotation(method, "Scheduled")) {
                        scheduledTasks.add(parseScheduledTask(method, className))
                    }
                }

                // Check for message listeners
                typeDeclaration.methods.forEach { method ->
                    if (hasAnnotation(method, "KafkaListener", "RabbitListener", "JmsListener")) {
                        messageListeners.add(parseMessageListener(method, className))
                    }
                }
            }
        }
    }

    private fun parseController(classDecl: ClassOrInterfaceDeclaration): ControllerInfo {
        val className = classDecl.fullyQualifiedName.orElse(classDecl.nameAsString)
        val basePath = extractRequestMapping(classDecl)
        val endpoints = mutableListOf<EndpointInfo>()

        classDecl.methods.forEach { method ->
            val endpoint = parseEndpoint(method, basePath)
            if (endpoint != null) {
                endpoints.add(endpoint)
            }
        }

        return ControllerInfo(
            className = className,
            basePath = basePath,
            endpoints = endpoints
        )
    }

    private fun parseEndpoint(method: MethodDeclaration, basePath: String): EndpointInfo? {
        val mappingAnnotations = listOf(
            "GetMapping", "PostMapping", "PutMapping", 
            "DeleteMapping", "PatchMapping", "RequestMapping"
        )

        for (annotationName in mappingAnnotations) {
            val annotation = method.getAnnotationByName(annotationName).orElse(null) ?: continue
            
            val httpMethod = when (annotationName) {
                "GetMapping" -> HttpMethod.GET
                "PostMapping" -> HttpMethod.POST
                "PutMapping" -> HttpMethod.PUT
                "DeleteMapping" -> HttpMethod.DELETE
                "PatchMapping" -> HttpMethod.PATCH
                "RequestMapping" -> HttpMethod.GET // Default
                else -> HttpMethod.GET
            }

            val path = extractPathFromAnnotation(annotation, basePath)
            val returnType = method.typeAsString
            val isAsync = hasAnnotation(method, "Async") || 
                          returnType.contains("CompletableFuture") ||
                          returnType.contains("Mono") ||
                          returnType.contains("Flux")

            return EndpointInfo(
                method = httpMethod,
                path = path,
                returnType = returnType,
                isAsync = isAsync
            )
        }

        return null
    }

    private fun parseService(classDecl: ClassOrInterfaceDeclaration): ServiceInfo {
        return ServiceInfo(
            className = classDecl.fullyQualifiedName.orElse(classDecl.nameAsString),
            hasAsyncMethods = classDecl.methods.any { hasAnnotation(it, "Async") },
            hasTransactional = hasAnnotation(classDecl, "Transactional") ||
                              classDecl.methods.any { hasAnnotation(it, "Transactional") }
        )
    }

    private fun parseRepository(classDecl: ClassOrInterfaceDeclaration): RepositoryInfo {
        return RepositoryInfo(
            className = classDecl.fullyQualifiedName.orElse(classDecl.nameAsString),
            extendsJpaRepository = classDecl.extendedTypes.any { 
                it.nameAsString.contains("JpaRepository") ||
                it.nameAsString.contains("CrudRepository") ||
                it.nameAsString.contains("MongoRepository")
            }
        )
    }

    private fun parseEntity(classDecl: ClassOrInterfaceDeclaration): EntityInfo {
        val tableAnnotation = classDecl.getAnnotationByName("Table").orElse(null)
        val tableName = if (tableAnnotation != null && tableAnnotation.isSingleMemberAnnotationExpr) {
            val memberValue = tableAnnotation.asSingleMemberAnnotationExpr().memberValue
            if (memberValue.isStringLiteralExpr) {
                memberValue.asStringLiteralExpr().value
            } else {
                classDecl.nameAsString
            }
        } else {
            classDecl.nameAsString
        }

        return EntityInfo(
            className = classDecl.fullyQualifiedName.orElse(classDecl.nameAsString),
            tableName = tableName
        )
    }

    private fun parseScheduledTask(method: MethodDeclaration, className: String): ScheduledTaskInfo {
        val scheduleExpr = method.getAnnotationByName("Scheduled")
            .map { it.toString() }
            .orElse("Unknown")

        return ScheduledTaskInfo(
            className = className,
            methodName = method.nameAsString,
            schedule = scheduleExpr
        )
    }

    private fun parseMessageListener(method: MethodDeclaration, className: String): ListenerInfo {
        val listenerType = when {
            hasAnnotation(method, "KafkaListener") -> "Kafka"
            hasAnnotation(method, "RabbitListener") -> "RabbitMQ"
            hasAnnotation(method, "JmsListener") -> "JMS"
            else -> "Unknown"
        }

        return ListenerInfo(
            className = className,
            methodName = method.nameAsString,
            listenerType = listenerType
        )
    }

    private fun findMainClass(cu: CompilationUnit): String? {
        cu.types.forEach { typeDeclaration ->
            if (typeDeclaration is ClassOrInterfaceDeclaration) {
                if (hasAnnotation(typeDeclaration, "SpringBootApplication")) {
                    return typeDeclaration.fullyQualifiedName.orElse(typeDeclaration.nameAsString)
                }
            }
        }
        return null
    }

    private fun findSecurityConfig(cu: CompilationUnit): SecurityInfo? {
        cu.types.forEach { typeDeclaration ->
            if (typeDeclaration is ClassOrInterfaceDeclaration) {
                if (hasAnnotation(typeDeclaration, "EnableWebSecurity", "EnableGlobalMethodSecurity")) {
                    return SecurityInfo(
                        hasWebSecurity = true,
                        hasMethodSecurity = hasAnnotation(typeDeclaration, "EnableGlobalMethodSecurity"),
                        hasOAuth2 = hasAnnotation(typeDeclaration, "EnableOAuth2Client", "EnableResourceServer")
                    )
                }
            }
        }
        return null
    }

    private fun detectArchitectureType(
        controllers: List<ControllerInfo>,
        messageListeners: List<ListenerInfo>,
        services: List<ServiceInfo>
    ): ArchitectureType {
        return when {
            messageListeners.isNotEmpty() -> ArchitectureType.EVENT_DRIVEN
            controllers.any { it.endpoints.any { e -> e.isAsync } } -> ArchitectureType.REACTIVE_WEBFLUX
            controllers.size <= 5 && services.size <= 10 -> ArchitectureType.MICROSERVICE
            else -> ArchitectureType.TRADITIONAL_MVC
        }
    }

    private fun hasAnnotation(annotatedNode: com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<*>, vararg names: String): Boolean {
        return names.any { name -> annotatedNode.getAnnotationByName(name).isPresent }
    }

    private fun extractRequestMapping(classDecl: ClassOrInterfaceDeclaration): String {
        val annotation = classDecl.getAnnotationByName("RequestMapping").orElse(null) ?: return "/"
        if (annotation.isSingleMemberAnnotationExpr) {
            val memberValue = annotation.asSingleMemberAnnotationExpr().memberValue
            if (memberValue.isStringLiteralExpr) {
                return memberValue.asStringLiteralExpr().value
            }
        }
        return "/"
    }

    private fun extractPathFromAnnotation(annotation: AnnotationExpr, basePath: String): String {
        val path = if (annotation.isSingleMemberAnnotationExpr) {
            val memberValue = annotation.asSingleMemberAnnotationExpr().memberValue
            if (memberValue.isStringLiteralExpr) {
                memberValue.asStringLiteralExpr().value
            } else {
                ""
            }
        } else {
            ""
        }
        return "$basePath$path".replace("//", "/")
    }
}

// Data Models
data class CodeStructure(
    val mainClass: String,
    val restControllers: List<ControllerInfo>,
    val services: List<ServiceInfo>,
    val repositories: List<RepositoryInfo>,
    val entities: List<EntityInfo>,
    val scheduledTasks: List<ScheduledTaskInfo>,
    val messageListeners: List<ListenerInfo>,
    val securityConfig: SecurityInfo?,
    val architectureType: ArchitectureType
)

data class ControllerInfo(
    val className: String,
    val basePath: String,
    val endpoints: List<EndpointInfo>
)

data class EndpointInfo(
    val method: HttpMethod,
    val path: String,
    val returnType: String,
    val isAsync: Boolean
)

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH
}

data class ServiceInfo(
    val className: String,
    val hasAsyncMethods: Boolean,
    val hasTransactional: Boolean
)

data class RepositoryInfo(
    val className: String,
    val extendsJpaRepository: Boolean
)

data class EntityInfo(
    val className: String,
    val tableName: String
)

data class ScheduledTaskInfo(
    val className: String,
    val methodName: String,
    val schedule: String
)

data class ListenerInfo(
    val className: String,
    val methodName: String,
    val listenerType: String // Kafka, RabbitMQ, JMS
)

data class SecurityInfo(
    val hasWebSecurity: Boolean,
    val hasMethodSecurity: Boolean,
    val hasOAuth2: Boolean
)

enum class ArchitectureType {
    TRADITIONAL_MVC,
    REACTIVE_WEBFLUX,
    MICROSERVICE,
    MONOLITH,
    EVENT_DRIVEN
}
