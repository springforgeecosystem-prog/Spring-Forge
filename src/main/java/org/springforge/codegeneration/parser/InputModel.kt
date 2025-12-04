package org.springforge.codegeneration.parser

data class InputModel(
    val projectName: String = "",
    val packageRoot: String = "com.example",
    val architecture: String? = null,
    val entities: List<EntitySpec> = emptyList()
)

data class EntitySpec(
    val name: String = "",
    val fields: List<FieldSpec> = emptyList(),
    val relationships: List<RelationshipSpec> = emptyList()
)

data class FieldSpec(
    val name: String = "",
    val type: String = "String",
    val annotations: List<String> = emptyList(),
    val constraints: Map<String, String> = emptyMap()
)

data class RelationshipSpec(
    val type: String = "",
    val targetEntity: String = "",
    val mappedBy: String? = null
)