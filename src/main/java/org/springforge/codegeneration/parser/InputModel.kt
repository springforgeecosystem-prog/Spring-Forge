package org.springforge.codegeneration.parser

data class InputModel(
    val project: ProjectSpec? = null,
    val entities: List<EntitySpec> = emptyList(),
    val relationships: List<RelationshipSpec> = emptyList()
)

data class ProjectSpec(
    val name: String = "",
    val language: String? = null,
    val framework: String? = null
)


data class EntitySpec(
    val name: String = "",
    val table_name: String? = null,
    val annotations: List<String> = emptyList(),
    val fields: List<FieldSpec> = emptyList()
)


data class FieldSpec(
    val name: String = "",
    val type: String = "",
    val primary_key: Boolean? = null,
    val unique: Boolean? = null,
    val nullable: Boolean? = null,
    val annotations: List<String> = emptyList(),
    val constraints: Map<String, String> = emptyMap()
)

data class RelationshipSpec(
    val from: String = "",
    val to: String = "",
    val type: String = "",
    val mapped_by: String? = null,
    val annotations: List<String> = emptyList()
)
