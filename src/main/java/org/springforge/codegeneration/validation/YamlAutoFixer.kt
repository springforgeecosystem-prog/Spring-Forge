package org.springforge.codegeneration.validation

object YamlAutoFixer {

    fun fixEntityName(name: String): String {
        if (YamlSchema.PASCAL_CASE_REGEX.matches(name)) return name
        val cleaned = name.replace("[^A-Za-z0-9]".toRegex(), "")
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    fun fixFieldName(name: String): String {
        if (YamlSchema.CAMEL_CASE_REGEX.matches(name)) return name
        val cleaned = name.replace("[^A-Za-z0-9]".toRegex(), "")
        return cleaned.replaceFirstChar { it.lowercase() }
    }

    fun fixPackageName(pkg: String): String {
        return pkg.lowercase().replace("_", "").replace("-", "")
    }

    fun fixType(type: String): String {
        val t = type.trim().replace(" ", "")
        return if (YamlSchema.VALID_TYPES.contains(t)) t else "String"
    }
}
