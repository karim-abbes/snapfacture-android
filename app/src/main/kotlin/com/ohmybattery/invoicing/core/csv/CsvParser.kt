package com.ohmybattery.invoicing.core.csv

import java.io.PushbackReader
import java.io.Reader

object CsvParser {

    private const val BOM: Char = '\uFEFF'

    fun parse(input: Reader, separator: Char = ','): List<List<String>> {
        val reader = PushbackReader(input, 4)
        val rows = mutableListOf<List<String>>()
        val current = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var first = true

        fun endField() {
            current += field.toString()
            field.setLength(0)
        }
        fun endRow() {
            endField()
            rows += current.toList()
            current.clear()
        }

        while (true) {
            val r = reader.read()
            if (r == -1) break
            val c = r.toChar()
            if (first) {
                first = false
                if (c == BOM) continue
            }
            if (inQuotes) {
                if (c == '"') {
                    val n = reader.read()
                    if (n == '"'.code) {
                        field.append('"')
                    } else {
                        if (n != -1) reader.unread(n)
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    separator -> endField()
                    '\r' -> {
                        val n = reader.read()
                        if (n != '\n'.code && n != -1) reader.unread(n)
                        endRow()
                    }
                    '\n' -> endRow()
                    else -> field.append(c)
                }
            }
        }
        if (field.isNotEmpty() || current.isNotEmpty()) endRow()
        return rows.filter { row -> row.any { it.isNotBlank() } }
    }
}
