package org.springforge.codegeneration.validation

object YamlSchema {

    val PACKAGE_REGEX = Regex("^[a-z]+(\\.[a-z][a-z0-9]*)*\$")

    val PASCAL_CASE_REGEX = Regex("^[A-Z][a-zA-Z0-9]*\$")

    val CAMEL_CASE_REGEX = Regex("^[a-z][a-zA-Z0-9]*\$")

    val VALID_TYPES = setOf(
        "String", "Long", "Integer", "Double", "Float",
        "Boolean", "LocalDate", "LocalDateTime", "BigDecimal"
    )

    val RELATIONSHIP_TYPES = setOf(
        "OneToOne", "OneToMany", "ManyToOne", "ManyToMany"
    )

    val ARCHITECTURES = setOf("layered", "mvc", "clean")
}