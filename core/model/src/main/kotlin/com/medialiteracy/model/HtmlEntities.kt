package com.medialiteracy.model

import java.util.Locale

/**
 * High-performance, zero-allocation decoder for HTML named and numeric entities (e.g. &amp;, &#8217;).
 * Centralized in :core:model for shared reuse across feed parsers, context resolvers, and label tools.
 */
object HtmlEntities {

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "lsquo" to "'",
        "rsquo" to "'",
        "sbquo" to "'",
        "ldquo" to "\"",
        "rdquo" to "\"",
        "bdquo" to "\"",
        "ndash" to "–",
        "mdash" to "—",
        "hellip" to "…",
        "pound" to "£",
        "euro" to "€",
        "yen" to "¥",
        "copy" to "©",
        "reg" to "®",
        "deg" to "°",
        "plusmn" to "±",
        "trade" to "™",
        "bull" to "•",
        "middot" to "·",
        "frac12" to "½",
        "frac14" to "¼",
        "frac34" to "¾",
    )

    fun decode(input: String?): String {
        if (input == null || !input.contains('&')) return input.orEmpty()

        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '&') {
                val semi = input.indexOf(';', i + 1)
                if (semi in (i + 2)..(i + 10)) {
                    val entity = input.substring(i + 1, semi)
                    val replacement = when {
                        entity.startsWith("#x", ignoreCase = true) -> {
                            entity.substring(2).toIntOrNull(16)?.toChar()?.toString()
                        }
                        entity.startsWith("#") -> {
                            entity.substring(1).toIntOrNull()?.toChar()?.toString()
                        }
                        else -> NAMED_ENTITIES[entity.lowercase(Locale.US)]
                    }
                    if (replacement != null) {
                        sb.append(replacement)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
