package org.springforge.codegeneration.service

import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source   // <-- Import this!
import java.io.File

/**
 * Simple client to download a Spring Initializr starter zip.
 *
 * Uses the public start.spring.io endpoint. Returns the File object pointing to the downloaded zip.
 */
class SpringInitializrClient(
    private val baseUrl: String = "https://start.spring.io"
) {
    private val client = OkHttpClient()

    /**
     * Request a starter zip with given parameters and download to given target file.
     * Example params: "type=maven-project&language=java&bootVersion=3.2.4&dependencies=web,jpa,lombok&groupId=com.example&artifactId=demo"
     */
    fun downloadStarterZip(params: String, targetFile: File): File {
        val url = "$baseUrl/starter.zip?$params"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("Spring Initializr failed: ${resp.code} ${resp.message}")
            }
            resp.body?.byteStream()?.use { input ->
                targetFile.parentFile?.mkdirs()
                val sink = targetFile.sink().buffer()
                sink.writeAll(input.source())   // works now!
                sink.close()
            } ?: throw RuntimeException("No body in response")
        }
        return targetFile
    }
}