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

        // Fix package root
        var fixedPackage = model.packageRoot
        if (!YamlSchema.PACKAGE_REGEX.matches(model.packageRoot)) {
            fixedPackage = YamlAutoFixer.fixPackageName(model.packageRoot)
            warnings.add("Package root auto-fixed to: $fixedPackage")
        }

        // Fix architecture
        var arch = model.architecture?.lowercase()
        if (arch != null && !YamlSchema.ARCHITECTURES.contains(arch)) {
            warnings.add("Invalid architecture '$arch', defaulting to 'layered'")
            arch = "layered"
        }

        val fixedEntities = model.entities.map { entity ->
            validateEntity(entity, errors, warnings)
        }

        return if (errors.isNotEmpty()) {
            ValidationResult.Invalid(errors)
        } else {
            ValidationResult.AutoFixed(
                model.copy(
                    packageRoot = fixedPackage,
                    architecture = arch,
                    entities = fixedEntities
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

        var entityName = e.name
        if (!YamlSchema.PASCAL_CASE_REGEX.matches(entityName)) {
            val fixed = YamlAutoFixer.fixEntityName(entityName)
            warnings.add("Entity '$entityName' auto-fixed to '$fixed'")
            entityName = fixed
        }

        val fixedFields = e.fields.map { field ->
            validateField(entityName, field, errors, warnings)
        }

        val fixedRelations = e.relationships.map { r ->
            validateRelationship(r, errors)
        }

        return e.copy(
            name = entityName,
            fields = fixedFields,
            relationships = fixedRelations
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
        errors: MutableList<ValidationError>
    ): RelationshipSpec {

        if (!YamlSchema.RELATIONSHIP_TYPES.contains(r.type)) {
            errors.add(ValidationError("Invalid relationship type '${r.type}'"))
        }

        return r
    }
}