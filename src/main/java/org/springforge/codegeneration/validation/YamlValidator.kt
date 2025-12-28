package org.springforge.codegeneration.validation

import org.springforge.codegeneration.parser.EntitySpec
import org.springforge.codegeneration.parser.FieldSpec
import org.springforge.codegeneration.parser.InputModel
import org.springforge.codegeneration.parser.RelationshipSpec

data class ValidationError(val message: String)

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<ValidationError>) : ValidationResult()
    data class AutoFixed(val fixedModel: InputModel, val warnings: List<String>) : ValidationResult()
}

object YamlValidator {

    fun validateAndFix(model: InputModel): ValidationResult {

        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()

        // -----------------------
        // 1. PROJECT VALIDATION
        // -----------------------
        val project = model.project

        if (project == null) {
            warnings.add("Project section missing. Using defaults.")
        } else {
            if (project.name.isBlank()) {
                warnings.add("Project name is empty.")
            }
            if (project.language != "java") {
                warnings.add("Only 'java' is supported. Provided: ${project.language}")
            }
            if (project.framework != "springboot") {
                warnings.add("Only 'springboot' framework supported. Provided: ${project.framework}")
            }
        }

        // -----------------------
        // 2. ENTITY VALIDATION
        // -----------------------
        val fixedEntities = model.entities.map { entity ->
            validateEntity(entity, errors, warnings)
        }

        // -----------------------
        // 3. RELATIONSHIP VALIDATION
        // -----------------------
        val fixedRelationships = model.relationships.map { rel ->
            validateRelationship(rel, errors, warnings)
        }

        // -----------------------
        // Return

        return if (errors.isNotEmpty()) {
            ValidationResult.Invalid(errors)
        } else {
            ValidationResult.AutoFixed(
                model.copy(
                    entities = fixedEntities,
                    relationships = fixedRelationships
                ),
                warnings
            )
        }
    }

    private fun validateEntity(
        e: EntitySpec,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>
    ): EntitySpec {

        // Validate entity name
        var fixedName = e.name
        if (!YamlSchema.PASCAL_CASE_REGEX.matches(fixedName)) {
            val corrected = YamlAutoFixer.fixEntityName(fixedName)
            warnings.add("Entity '$fixedName' auto-fixed to '$corrected'")
            fixedName = corrected
        }

        // Validate fields
        val fixedFields = e.fields.map { field ->
            validateField(fixedName, field, errors, warnings)
        }

        return e.copy(
            name = fixedName,
            fields = fixedFields
        )
    }

    private fun validateField(
        entityName: String,
        field: FieldSpec,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>
    ): FieldSpec {

        var fname = field.name
        if (!YamlSchema.CAMEL_CASE_REGEX.matches(fname)) {
            val fixed = YamlAutoFixer.fixFieldName(fname)
            warnings.add("Field '$fname' in entity $entityName auto-fixed to '$fixed'")
            fname = fixed
        }

        var ftype = YamlAutoFixer.fixType(field.type)
        if (ftype != field.type) {
            warnings.add("Field type '${field.type}' auto-fixed to '$ftype'")
        }

        return field.copy(name = fname, type = ftype)
    }

    private fun validateRelationship(
        r: RelationshipSpec,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>
    ): RelationshipSpec {

        if (!YamlSchema.RELATIONSHIP_TYPES.contains(r.type)) {
            errors.add(ValidationError("Invalid relationship type '${r.type}'"))
        }

        if (r.from.isBlank()) {
            errors.add(ValidationError("Relationship 'from' cannot be empty"))
        }

        if (r.to.isBlank()) {
            errors.add(ValidationError("Relationship 'to' cannot be empty"))
        }

        return r
    }
}
