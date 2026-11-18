package com.medialiteracy.benchmark

import java.io.File

object Fetch {
    /**
     * The cached copy of [source], downloading it first unless [offline].
     * Returns null when offline and not cached. Throws when a download does
     * not match its pinned checksum: the upstream file changed, and it must be
     * re-verified before the pin is updated.
     */
    fun cached(
        source: ExternalSource,
        cacheDir: File,
        offline: Boolean,
        download: (String) -> ByteArray = Http::bytes,
    ): File? {
        val target = File(cacheDir, source.fileName)
        if (target.exists() && sha256Hex(target.readBytes()) == source.sha256) return target
        if (offline) return null
        val bytes = download(source.url)
        val actual = sha256Hex(bytes)
        check(actual == source.sha256) {
            "Checksum mismatch for ${source.id}: expected ${source.sha256}, got $actual. The upstream file changed; re-verify it before updating the pin."
        }
        cacheDir.mkdirs()
        target.writeBytes(bytes)
        return target
    }
}
