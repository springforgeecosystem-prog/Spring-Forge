package org.springforge.codegeneration.parser

data class ParseResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val data: InputModel? = null
)