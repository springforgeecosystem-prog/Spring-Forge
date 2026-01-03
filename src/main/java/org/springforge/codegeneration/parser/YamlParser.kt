package org.springforge.codegeneration.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

object YamlParser {

    private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    fun parse(text: String): ParseResult {
        return try {

            // Read YAML directly into updated data model
            val model: InputModel = mapper.readValue(text)

            // Build constraints using:
            // - primary_key, unique, nullable flags
            // - annotation-based constraints
            val updatedEntities = model.entities.map { entity ->
                val updatedFields = entity.fields.map { field ->

                    // Build constraints from flags
                    val flagConstraints = mutableMapOf<String, String>()
                    if (field.primary_key == true) flagConstraints["primary_key"] = "true"
                    if (field.unique == true) flagConstraints["unique"] = "true"
                    if (field.nullable == false) flagConstraints["nullable"] = "false"

                    // Extract constraints from annotations
                    val annotationConstraints = mutableMapOf<String, String>()
                    field.annotations.forEach { ann ->
                        if (ann.contains("(")) {
                            val inside = ann.substringAfter("(").substringBefore(")")
                            inside.split(",").map { it.trim() }.forEach { entry ->
                                val parts = entry.split("=")
                                if (parts.size == 2) {
                                    annotationConstraints[parts[0].trim()] = parts[1].trim()
                                }
                            }
                        }
                    }

                    val mergedConstraints =
                        annotationConstraints + flagConstraints + field.constraints

                    field.copy(constraints = mergedConstraints)
                }

                entity.copy(fields = updatedFields)
            }

            ParseResult(
                isValid = true,
                errorMessage = null,
                data = model.copy(entities = updatedEntities)
            )

        } catch (ex: Exception) {
            ParseResult(false, "YAML parse error: ${ex.message}", null)
        }
    }
}
