package com.snapfacture.core.csv

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class CsvParserTest {

    private fun parse(text: String, separator: Char = ',') =
        CsvParser.parse(StringReader(text), separator)

    @Test
    fun `simple rows`() {
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("1", "2", "3")),
            parse("a,b,c\n1,2,3"),
        )
    }

    @Test
    fun `quoted field containing the separator`() {
        assertEquals(
            listOf(listOf("Dupont, Jean", "100")),
            parse("\"Dupont, Jean\",100"),
        )
    }

    @Test
    fun `escaped double quotes inside a quoted field`() {
        assertEquals(
            listOf(listOf("say \"hi\"", "x")),
            parse("\"say \"\"hi\"\"\",x"),
        )
    }

    @Test
    fun `quoted field containing a newline`() {
        assertEquals(
            listOf(listOf("line1\nline2", "x")),
            parse("\"line1\nline2\",x"),
        )
    }

    @Test
    fun `crlf line endings and utf8 bom`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d")),
            parse("\uFEFF" + "a,b\r\nc,d"),
        )
    }

    @Test
    fun `blank rows are dropped`() {
        assertEquals(
            listOf(listOf("a", "b")),
            parse("a,b\n\n , \n"),
        )
    }

    @Test
    fun `semicolon separator`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("1,5", "2")),
            parse("a;b\n\"1,5\";2", separator = ';'),
        )
    }

    @Test
    fun `trailing newline does not create an extra row`() {
        assertEquals(1, parse("a,b\n").size)
    }
}
