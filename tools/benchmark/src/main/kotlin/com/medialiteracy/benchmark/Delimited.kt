package com.medialiteracy.benchmark

/**
 * A small CSV reader for published datasets: quoted fields may hold the
 * separator, doubled quotes and line breaks. A quote only opens a quoted
 * field at the start of a field, so apostrophes and stray quotes inside
 * plain text stay text.
 */
object Delimited {
    fun parse(text: String, separator: Char): List<List<String>> {
        val s = text.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (field.isEmpty()) inQuotes = true else field.append(c)
                    separator -> { row.add(field.toString()); field.clear() }
                    '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                    '\r' -> Unit
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
