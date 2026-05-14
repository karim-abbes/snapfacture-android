package com.snapfacture.core.country

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CountryProfileTest {

    // --- Money formatting -------------------------------------------------

    @Test
    fun `france formats money with euro suffix and comma decimal`() {
        val formatted = FranceProfile.formatMoney(13_000)
        // NumberFormat may use NBSP or regular space; just check digits + currency
        assertTrue("Expected € in '$formatted'", formatted.contains("€"))
        assertTrue("Expected '130,00' in '$formatted'", formatted.contains("130,00"))
    }

    @Test
    fun `usa formats money with dollar prefix and period decimal`() {
        val formatted = UsaProfile.formatMoney(13_000)
        assertEquals("\$130.00", formatted)
    }

    @Test
    fun `usa handles cents correctly`() {
        assertEquals("\$0.99", UsaProfile.formatMoney(99))
        assertEquals("\$1.00", UsaProfile.formatMoney(100))
        assertEquals("\$1,234.56", UsaProfile.formatMoney(123_456))
    }

    @Test
    fun `negative amounts on credit notes still format`() {
        // Credit notes store negative cents — must render readable.
        val frResult = FranceProfile.formatMoney(-13_000)
        assertTrue("Expected '130,00' in '$frResult'", frResult.contains("130,00"))
        val usResult = UsaProfile.formatMoney(-13_000)
        assertTrue("Expected '130.00' in '$usResult'", usResult.contains("130.00"))
        assertTrue("Expected minus sign in '$usResult'", usResult.contains("-") || usResult.contains("("))
    }

    @Test
    fun `zero amount renders as zero`() {
        val fr = FranceProfile.formatMoney(0)
        val us = UsaProfile.formatMoney(0)
        assertTrue("FR zero malformed: '$fr'", fr.contains("0,00") || fr.contains("0.00"))
        assertEquals("\$0.00", us)
    }

    // --- Date formatting --------------------------------------------------

    private fun millisFor(day: Int, month: Int, year: Int): Long {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.parse("%02d/%02d/%04d".format(day, month, year))!!.time
    }

    @Test
    fun `france dates use dd slash MM slash yyyy`() {
        val millis = millisFor(5, 3, 2026)
        assertEquals("05/03/2026", FranceProfile.formatDate(millis))
    }

    @Test
    fun `usa dates use MM slash dd slash yyyy`() {
        val millis = millisFor(5, 3, 2026)
        assertEquals("03/05/2026", UsaProfile.formatDate(millis))
    }

    // --- Profile lookup ---------------------------------------------------

    @Test
    fun `byCountryName maps United States variants to UsaProfile`() {
        assertEquals(UsaProfile, CountryProfiles.byCountryName("United States"))
        assertEquals(UsaProfile, CountryProfiles.byCountryName("united states"))
        assertEquals(UsaProfile, CountryProfiles.byCountryName("USA"))
        assertEquals(UsaProfile, CountryProfiles.byCountryName("US"))
        assertEquals(UsaProfile, CountryProfiles.byCountryName("u.s.a."))
    }

    @Test
    fun `byCountryName maps France variants to FranceProfile`() {
        assertEquals(FranceProfile, CountryProfiles.byCountryName("France"))
        assertEquals(FranceProfile, CountryProfiles.byCountryName("france"))
        assertEquals(FranceProfile, CountryProfiles.byCountryName("FR"))
    }

    @Test
    fun `byCountryName falls back to France for unknown or blank`() {
        assertEquals(FranceProfile, CountryProfiles.byCountryName(null))
        assertEquals(FranceProfile, CountryProfiles.byCountryName(""))
        assertEquals(FranceProfile, CountryProfiles.byCountryName("Canada"))
    }

    // --- Country-specific rules -------------------------------------------

    @Test
    fun `france has anti-fraud hash chain enabled`() {
        assertTrue(FranceProfile.antiFraudHashChain)
    }

    @Test
    fun `usa skips the anti-fraud hash chain`() {
        // The chain is a French legal requirement (loi anti-fraude TVA 2018).
        // US invoices have no equivalent.
        assertTrue(!UsaProfile.antiFraudHashChain)
    }

    @Test
    fun `france requires sequential numbering`() {
        assertTrue(FranceProfile.sequentialNumberingRequired)
    }

    @Test
    fun `usa does not legally require sequential numbering`() {
        assertTrue(!UsaProfile.sequentialNumberingRequired)
    }

    @Test
    fun `france emits the franchise mention when tax opted out`() {
        val mention = FranceProfile.footerMention(taxOptedOut = true)
        assertNotNull(mention)
        assertTrue("Expected art. 293 B in '$mention'", mention!!.contains("293 B"))
    }

    @Test
    fun `france has no footer mention when not opted out`() {
        assertNull(FranceProfile.footerMention(taxOptedOut = false))
    }

    @Test
    fun `usa never emits a footer mention`() {
        assertNull(UsaProfile.footerMention(taxOptedOut = false))
        assertNull(UsaProfile.footerMention(taxOptedOut = true))
    }

    @Test
    fun `france default vat rate is 20 percent`() {
        assertEquals(200, FranceProfile.defaultTaxRatePermille)
    }

    @Test
    fun `usa default tax rate is zero`() {
        // No federal sales tax; user must configure their state rate.
        assertEquals(0, UsaProfile.defaultTaxRatePermille)
    }

    @Test
    fun `legal id labels match expectations`() {
        assertEquals("SIREN", FranceProfile.legalIdLabel)
        assertEquals("EIN / Tax ID", UsaProfile.legalIdLabel)
    }

    @Test
    fun `tax labels match expectations`() {
        assertEquals("TVA", FranceProfile.taxLabel)
        assertEquals("Sales Tax", UsaProfile.taxLabel)
    }
}
