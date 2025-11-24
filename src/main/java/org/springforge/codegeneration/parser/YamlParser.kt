package org.springforge.codegeneration.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

object YamlParser {

    private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    fun parse(text: String): ParseResult {
        return try {
            val model: InputModel = mapper.readValue(text)

            // Extract constraints out of annotations
            val updatedEntities = model.entities.map { entity ->
                val updatedFields = entity.fields.map { field ->
                    val constraints = mutableMapOf<String, String>()

                    field.annotations.forEach { ann ->
                        if (ann.contains("(") && ann.contains(")")) {
                            val inside = ann.substringAfter("(").substringBefore(")")
                            inside.split(",").map { it.trim() }.forEach { kv ->
                                val parts = kv.split("=")
                                if (parts.size == 2) {
                                    constraints[parts[0].trim()] = parts[1].trim()
                                }
                            }
                        }
                    }

                    field.copy(constraints = constraints)
                }

                entity.copy(fields = updatedFields)
            }

            ParseResult(true, null, model.copy(entities = updatedEntities))

        } catch (ex: Exception) {
            ParseResult(false, "YAML parse error: ${ex.message}", null)
        }
    }
}