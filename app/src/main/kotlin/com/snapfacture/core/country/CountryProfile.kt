package com.snapfacture.core.country

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * Per-country business rules. Anything that depends on jurisdiction
 * (tax, legal mentions, ID labels, number sequence requirements, etc.)
 * lives here so the rest of the app stays neutral.
 */
sealed interface CountryProfile {
    val code: String
    val displayName: String
    val locale: Locale
    val currency: Currency
    val dateFormat: String
    val legalIdLabel: String
    val taxLabel: String
    val defaultTaxRatePermille: Int
    val sequentialNumberingRequired: Boolean
    val antiFraudHashChain: Boolean

    /** Optional small-print line drawn at the bottom of every PDF. */
    fun footerMention(taxOptedOut: Boolean): String?

    fun formatMoney(cents: Long): String {
        val nf = NumberFormat.getCurrencyInstance(locale).apply {
            currency = this@CountryProfile.currency
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return nf.format(cents / 100.0)
    }

    fun formatDate(millis: Long): String =
        SimpleDateFormat(dateFormat, locale).format(Date(millis))
}

object FranceProfile : CountryProfile {
    override val code = "FR"
    override val displayName = "France"
    override val locale: Locale = Locale.forLanguageTag("fr-FR")
    override val currency: Currency = Currency.getInstance("EUR")
    override val dateFormat = "dd/MM/yyyy"
    override val legalIdLabel = "SIREN"
    override val taxLabel = "TVA"
    override val defaultTaxRatePermille = 200
    override val sequentialNumberingRequired = true
    override val antiFraudHashChain = true

    override fun footerMention(taxOptedOut: Boolean): String? =
        if (taxOptedOut) "TVA non applicable, art. 293 B du CGI" else null
}

object UsaProfile : CountryProfile {
    override val code = "US"
    override val displayName = "United States"
    override val locale: Locale = Locale.US
    override val currency: Currency = Currency.getInstance("USD")
    override val dateFormat = "MM/dd/yyyy"
    override val legalIdLabel = "EIN / Tax ID"
    override val taxLabel = "Sales Tax"
    override val defaultTaxRatePermille = 0
    override val sequentialNumberingRequired = false
    override val antiFraudHashChain = false

    override fun footerMention(taxOptedOut: Boolean): String? = null
}

object CountryProfiles {
    val all: List<CountryProfile> = listOf(FranceProfile, UsaProfile)

    fun byCode(code: String?): CountryProfile = when (code?.uppercase()) {
        "US" -> UsaProfile
        else -> FranceProfile
    }

    /** Map a free-text country (the value users type in CompanyInfo) to a profile. */
    fun byCountryName(name: String?): CountryProfile {
        val normalised = name?.trim()?.lowercase().orEmpty()
        return when (normalised) {
            "", "fr", "france" -> FranceProfile
            "us", "usa", "u.s.", "u.s.a.", "united states", "united states of america" -> UsaProfile
            else -> FranceProfile
        }
    }

    /** Pick a profile from the device locale (fallback, used before the company is filled). */
    fun detect(locale: Locale = Locale.getDefault()): CountryProfile =
        when (locale.country.uppercase()) {
            "US" -> UsaProfile
            else -> FranceProfile
        }
}
