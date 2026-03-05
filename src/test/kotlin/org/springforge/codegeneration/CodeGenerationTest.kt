package org.springforge.codegeneration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springforge.codegeneration.parser.EntitySpec
import org.springforge.codegeneration.parser.FieldSpec
import org.springforge.codegeneration.parser.InputModel
import org.springforge.codegeneration.service.CodeFileParser
import org.springforge.codegeneration.validation.ValidationResult
import org.springforge.codegeneration.validation.YamlValidator
import org.springforge.codegeneration.parser.YamlParser

class CodeGenerationTest {

    // ── Test 1: Valid YAML parses to success result with 2 entities ──────────

    @Test
    fun `YamlParser valid yaml returns success with two entities`() {
        val yaml = """
            project:
              name: demo
              language: java
              framework: springboot
            entities:
              - name: User
                fields:
                  - name: id
                    type: Long
                    primary_key: true
              - name: Order
                fields:
                  - name: orderId
                    type: Long
                    primary_key: true
            relationships: []
        """.trimIndent()

        val result = YamlParser.parse(yaml)

        assertTrue(result.isValid, "Expected isValid=true but got: ${result.errorMessage}")
        assertEquals(2, result.data!!.entities.size)
        assertEquals("User", result.data!!.entities[0].name)
        assertEquals("Order", result.data!!.entities[1].name)
    }

    // ── Test 2: Malformed YAML returns invalid result with error message ──────

    @Test
    fun `YamlParser malformed yaml returns invalid with error message`() {
        val garbage = "this: is: not: valid: yaml: ::::"

        val result = YamlParser.parse(garbage)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertNull(result.data)
    }

    // ── Test 3: snake_case entity name is auto-fixed to PascalCase ───────────

    @Test
    fun `YamlValidator snake case entity name auto fixes to PascalCase`() {
        // YamlAutoFixer strips non-alphanumeric chars then uppercases first char:
        // "userAccount" (camelCase) → removes nothing → uppercases 'u' → "UserAccount"
        val model = InputModel(
            entities = listOf(
                EntitySpec(
                    name = "userAccount",
                    fields = listOf(FieldSpec(name = "id", type = "Long"))
                )
            )
        )

        val result = YamlValidator.validateAndFix(model)

        assertTrue(result is ValidationResult.AutoFixed, "Expected AutoFixed but got: $result")
        val fixed = (result as ValidationResult.AutoFixed).fixedModel
        assertEquals("UserAccount", fixed.entities[0].name)
    }

    // ── Test 4: Two ===FILE: blocks parse into two GeneratedFile objects ─────

    @Test
    fun `CodeFileParser two file blocks returns two generated files`() {
        val raw = """
            ===FILE: src/main/java/com/example/entity/User.java===
            package com.example.entity;
            public class User {}
            ===END_FILE===
            ===FILE: src/main/java/com/example/service/UserService.java===
            package com.example.service;
            public class UserService {}
            ===END_FILE===
        """.trimIndent()

        val files = CodeFileParser.parse(raw)

        assertEquals(2, files.size)
        assertEquals("src/main/java/com/example/entity/User.java", files[0].relativePath)
        assertEquals("src/main/java/com/example/service/UserService.java", files[1].relativePath)
        assertTrue(files[0].content.contains("public class User"))
        assertTrue(files[1].content.contains("public class UserService"))
    }
}
