package org.springforge.codegeneration.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Client for calling Google Gemini API.
 *
 * Designed to be swappable later with AWS Bedrock (Claude).
 * The only public method is [generate] which takes a prompt and returns raw text.
 *
 * API key resolution order:
 *  1. Explicitly passed [apiKey] constructor parameter
 *  2. System environment variable GEMINI_API_KEY
 *  3. .env file in the project root (key=value format)
 */
class GeminiClient(
    private val apiKey: String = resolveApiKey(),
    private val model: String = "gemini-2.5-flash",
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
) {

    companion object {
        /**
         * Resolve the API key from environment variable, bundled .env, or filesystem .env.
         * Search order:
         *  1. System env var GEMINI_API_KEY
         *  2. Bundled .env inside the JAR (packed at build time by Gradle processResources)
         *  3. .env in the target project root (if provided)
         *  4. ~/.springforge/.env  (user home)
         *  5. Current working directory .env
         */
        fun resolveApiKey(projectRoot: String? = null): String {
            // 1. Check system environment variable
            val envKey = System.getenv("GEMINI_API_KEY")
            if (!envKey.isNullOrBlank()) return envKey

            // 2. Check the bundled .env resource inside the JAR
            //    (The .env from the plugin project root is copied into JAR
            //     resources by the processResources task in build.gradle.kts)
            try {
                val stream = GeminiClient::class.java.classLoader.getResourceAsStream(".env")
                if (stream != null) {
                    val bundledKey = parseDotEnvStream(stream)["GEMINI_API_KEY"]
                    if (!bundledKey.isNullOrBlank()) return bundledKey
                }
            } catch (_: Exception) { /* ignore classpath resolution failures */ }

            // 3. Gather filesystem .env file locations as fallback
            val dotEnvLocations = mutableListOf<File>()

            // Target project root
            if (projectRoot != null) {
                dotEnvLocations.add(File(projectRoot, ".env"))
            }

            // User home directory
            val userHome = System.getProperty("user.home")
            if (userHome != null) {
                dotEnvLocations.add(File(userHome, ".springforge/.env"))
                dotEnvLocations.add(File(userHome, ".env"))
            }

            // Current working directory
            dotEnvLocations.add(File(".env"))
            dotEnvLocations.add(File(System.getProperty("user.dir"), ".env"))

            for (dotEnv in dotEnvLocations) {
                if (dotEnv.exists()) {
                    val key = parseDotEnv(dotEnv)["GEMINI_API_KEY"]
                    if (!key.isNullOrBlank()) return key
                }
            }

            return ""
        }

        /**
         * Parse a simple .env file (KEY=VALUE lines, # comments, blank lines).
         */
        private fun parseDotEnv(file: File): Map<String, String> {
            val map = mutableMapOf<String, String>()
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val eqIndex = trimmed.indexOf('=')
                    if (eqIndex > 0) {
                        val key = trimmed.substring(0, eqIndex).trim()
                        val value = trimmed.substring(eqIndex + 1).trim()
                        map[key] = value
                    }
                }
            }
            return map
        }

        /**
         * Parse a .env-formatted InputStream (for reading bundled JAR resources).
         */
        private fun parseDotEnvStream(stream: java.io.InputStream): Map<String, String> {
            val map = mutableMapOf<String, String>()
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val eqIndex = trimmed.indexOf('=')
                        if (eqIndex > 0) {
                            val key = trimmed.substring(0, eqIndex).trim()
                            val value = trimmed.substring(eqIndex + 1).trim()
                            map[key] = value
                        }
                    }
                }
            }
            return map
        }
    }

    private val mapper = jacksonObjectMapper()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)   // 5 min — LLM needs more time for large prompts with existing entity context
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Send a prompt to Gemini and return the raw text response.
     *
     * @throws IllegalStateException if GEMINI_API_KEY is not set
     * @throws RuntimeException on HTTP / API errors
     */
    fun generate(prompt: String): String {
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "GEMINI_API_KEY not found. Set it in one of these locations:\n" +
                        "  1. System environment variable: GEMINI_API_KEY=your_key\n" +
                        "  2. .env file in your project root (GEMINI_API_KEY=your_key)\n" +
                        "  3. ~/.springforge/.env file"
            )
        }

        val url = "$baseUrl/models/$model:generateContent?key=$apiKey"

        val payload = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to prompt)
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.2,          // low temp for deterministic code
                "maxOutputTokens" to 16384,    // 16K is enough for typical entity generation; avoids slow 65K inference
                "topP" to 0.95
            )
        )

        val json = mapper.writeValueAsString(payload)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string()
                ?: throw RuntimeException("Empty response from Gemini API")

            if (!resp.isSuccessful) {
                throw RuntimeException(
                    "Gemini API error ${resp.code}: $respBody"
                )
            }

            // Parse response: { candidates: [ { content: { parts: [ { text: "..." } ] } } ] }
            val tree = mapper.readTree(respBody)

            val candidates = tree["candidates"]
                ?: throw RuntimeException("No 'candidates' in Gemini response: $respBody")

            val text = candidates[0]
                ?.get("content")
                ?.get("parts")
                ?.get(0)
                ?.get("text")
                ?.asText()
                ?: throw RuntimeException("Could not extract text from Gemini response: $respBody")

            return text
        }
    }
}
