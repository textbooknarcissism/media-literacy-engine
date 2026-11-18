package com.medialiteracy.benchmark

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FetchTest {
    private val body = "hello".toByteArray()
    private fun source() = ExternalSource("t", SourceKind.TONE_CHECKTHAT, "https://example.invalid/t", sha256Hex(body), "t.tsv", "CC-BY-4.0", 2024, "test")
    private fun tempDir() = createTempDirectory("fetch").toFile().apply { deleteOnExit() }

    @Test
    fun `offline with nothing cached is a skip, not an error`() {
        assertNull(Fetch.cached(source(), tempDir(), offline = true) { error("must not download") })
    }

    @Test
    fun `downloads, verifies and then reuses the cache`() {
        val dir = tempDir()
        val file = Fetch.cached(source(), dir, offline = false) { body }
        assertNotNull(file)
        assertContentEquals(body, file.readBytes())
        assertNotNull(Fetch.cached(source(), dir, offline = true) { error("the cached copy must be used") })
    }

    @Test
    fun `a changed upstream file is refused`() {
        assertFailsWith<IllegalStateException> {
            Fetch.cached(source(), tempDir(), offline = false) { "tampered".toByteArray() }
        }
    }
}
