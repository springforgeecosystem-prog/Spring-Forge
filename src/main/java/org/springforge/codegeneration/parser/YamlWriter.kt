package org.springforge.codegeneration.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object YamlWriter {

    private val mapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

    fun write(model: InputModel): String {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(model)
    }
}