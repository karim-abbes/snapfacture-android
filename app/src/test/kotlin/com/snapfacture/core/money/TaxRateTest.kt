package com.snapfacture.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TaxRateTest {

    // --- canonicalPermille: audit payloads depend on this never changing ----

    @Test
    fun `canonical renders whole-permille rates exactly like the pre-migration Int`() {
        assertEquals("200", TaxRate.canonicalPermille(2_000))
        assertEquals("100", TaxRate.canonicalPermille(1_000))
        assertEquals("55", TaxRate.canonicalPermille(550))
        assertEquals("21", TaxRate.canonicalPermille(210))
        assertEquals("0", TaxRate.canonicalPermille(0))
    }

    @Test
    fun `canonical renders sub-permille rates with a dot`() {
        assertEquals("62.5", TaxRate.canonicalPermille(625))
        assertEquals("72.5", TaxRate.canonicalPermille(725))
    }

    // --- formatPercent: no trailing zeros, locale separator ------------------

    @Test
    fun `format percent hides decimals on whole rates`() {
        val fr = Locale.forLanguageTag("fr-FR")
        assertEquals("20", TaxRate.formatPercent(2_000, fr))
        assertEquals("10", TaxRate.formatPercent(1_000, fr))
        assertEquals("0", TaxRate.formatPercent(0, fr))
    }

    @Test
    fun `format percent uses the locale decimal separator`() {
        val fr = Locale.forLanguageTag("fr-FR")
        assertEquals("5,5", TaxRate.formatPercent(550, fr))
        assertEquals("6,25", TaxRate.formatPercent(625, fr))
        assertEquals("6.25", TaxRate.formatPercent(625, Locale.US))
        assertEquals("7.25", TaxRate.formatPercent(725, Locale.US))
    }

    // --- parsePercentToBp -----------------------------------------------------

    @Test
    fun `parse accepts comma and dot percent input`() {
        assertEquals(625, TaxRate.parsePercentToBp("6,25"))
        assertEquals(625, TaxRate.parsePercentToBp("6.25"))
        assertEquals(2_000, TaxRate.parsePercentToBp("20"))
        assertEquals(550, TaxRate.parsePercentToBp("5,5"))
        assertEquals(0, TaxRate.parsePercentToBp("0"))
        assertEquals(725, TaxRate.parsePercentToBp(" 7.25 "))
    }

    @Test
    fun `parse rejects garbage, over-precision and out-of-range`() {
        assertNull(TaxRate.parsePercentToBp(""))
        assertNull(TaxRate.parsePercentToBp("abc"))
        assertNull(TaxRate.parsePercentToBp("6,255"))
        assertNull(TaxRate.parsePercentToBp("101"))
        assertNull(TaxRate.parsePercentToBp("-5"))
        assertNull(TaxRate.parsePercentToBp("6..25"))
    }

    @Test
    fun `parse then format round-trips`() {
        for (raw in listOf("6.25", "20", "5.5", "7.25")) {
            assertEquals(raw, TaxRate.formatPercent(TaxRate.parsePercentToBp(raw)!!, Locale.US))
        }
    }
}
