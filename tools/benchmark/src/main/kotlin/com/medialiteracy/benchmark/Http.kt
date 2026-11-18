package com.medialiteracy.benchmark

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream

/**
 * Plain HTTP GET with gzip sniffing. Java's HttpClient never decompresses,
 * and some feeds (The Independent) send gzip even when asked not to, so the
 * magic bytes are checked instead of trusting the header.
 */
object Http {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    fun bytes(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "MLE-benchmark/1.0")
            .timeout(Duration.ofSeconds(120))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        require(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} for $url" }
        val body = response.body()
        val gzipped = body.size > 2 && body[0] == 0x1f.toByte() && body[1] == 0x8b.toByte()
        return if (gzipped) GZIPInputStream(body.inputStream()).use { it.readBytes() } else body
    }

    fun text(url: String): String = String(bytes(url), Charsets.UTF_8)
}
