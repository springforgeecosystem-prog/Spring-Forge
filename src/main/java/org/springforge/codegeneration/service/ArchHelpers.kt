package org.springforge.codegeneration.service

fun String.toArchEnum(): ArchitectureTemplate {
    return when (this.lowercase()) {
        "mvc" -> ArchitectureTemplate.MVC
        "clean" -> ArchitectureTemplate.CLEAN
        else -> ArchitectureTemplate.LAYERED
    }
}