package com.snapfacture.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class QuantityTest {

    // --- canonical: the audit payload depends on this never changing --------

    @Test
    fun `canonical renders whole quantities exactly like the pre-migration Int`() {
        assertEquals("1", Quantity.canonical(1_000L))
        assertEquals("2", Quantity.canonical(2_000L))
        assertEquals("100", Quantity.canonical(100_000L))
    }

    @Test
    fun `canonical renders fractions with a dot and no trailing zeros`() {
        assertEquals("1.5", Quantity.canonical(1_500L))
        assertEquals("12.5", Quantity.canonical(12_500L))
        assertEquals("0.5", Quantity.canonical(500L))
        assertEquals("1.25", Quantity.canonical(1_250L))
        assertEquals("1.002", Quantity.canonical(1_002L))
    }

    // --- format: locale decimal separator, no decimals on whole values ------

    @Test
    fun `format uses the french comma and hides decimals on whole values`() {
        val fr = Locale.forLanguageTag("fr-FR")
        assertEquals("1,5", Quantity.format(1_500L, fr))
        assertEquals("12,5", Quantity.format(12_500L, fr))
        assertEquals("2", Quantity.format(2_000L, fr))
    }

    @Test
    fun `format uses the dot for the us locale`() {
        assertEquals("1.5", Quantity.format(1_500L, Locale.US))
        assertEquals("2", Quantity.format(2_000L, Locale.US))
    }

    // --- parse: comma or dot in, milli-units out -----------------------------

    @Test
    fun `parse accepts comma and dot`() {
        assertEquals(1_500L, Quantity.parse("1,5"))
        assertEquals(1_500L, Quantity.parse("1.5"))
        assertEquals(2_000L, Quantity.parse("2"))
        assertEquals(500L, Quantity.parse("0,5"))
        assertEquals(500L, Quantity.parse(",5"))
        assertEquals(12_500L, Quantity.parse(" 12,5 "))
        assertEquals(1_250L, Quantity.parse("1,25"))
    }

    @Test
    fun `parse rejects garbage, zero and over-precision`() {
        assertNull(Quantity.parse(""))
        assertNull(Quantity.parse("0"))
        assertNull(Quantity.parse("0,0"))
        assertNull(Quantity.parse("abc"))
        assertNull(Quantity.parse("1,2345"))
        assertNull(Quantity.parse("1..5"))
        assertNull(Quantity.parse("-1"))
        assertNull(Quantity.parse("1 5"))
    }

    @Test
    fun `parse then canonical round-trips`() {
        for (raw in listOf("1.5", "2", "0.375", "12.5")) {
            assertEquals(raw, Quantity.canonical(Quantity.parse(raw)!!))
        }
    }
}
