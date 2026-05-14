package com.snapfacture.core.csv

import java.io.Writer

/**
 * Minimal RFC 4180 CSV writer that always quotes fields, matching the format
 * produced by the legacy export the importer is fed with.
 */
class CsvWriter(private val out: Writer, private val separator: Char = ',') {

    fun writeRow(fields: List<String>) {
        fields.forEachIndexed { i, field ->
            if (i > 0) out.write(separator.toString())
            out.write("\"" + field.replace("\"", "\"\"") + "\"")
        }
        out.write("\r\n")
    }
}
