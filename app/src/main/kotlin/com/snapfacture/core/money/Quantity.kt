package com.snapfacture.core.money

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Quantities are stored as Long milli-units (1500 = 1.5, 12500 = 12.5) —
 * the same integer pattern as vatRatePermille, so line math stays exact.
 */
object Quantity {
    const val ONE = 1_000L

    // Above this, unitPrice × quantity could overflow Long cents.
    private const val MAX_MILLI = 1_000_000L * ONE

    /**
     * Locale-independent serialization used by the audit payload. Whole
     * quantities render with no decimals ("2"), byte-identical to the Int
     * the payload contained before the milli-unit migration — payloads
     * hashed back then must still verify after rows were multiplied by 1000.
     */
    fun canonical(milliUnits: Long): String {
        val sign = if (milliUnits < 0) "-" else ""
        val abs = kotlin.math.abs(milliUnits)
        val whole = abs / ONE
        val frac = abs % ONE
        if (frac == 0L) return "$sign$whole"
        return "$sign$whole." + frac.toString().padStart(3, '0').trimEnd('0')
    }

    fun format(milliUnits: Long, locale: Locale): String =
        canonical(milliUnits).replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

    /** Parses "1,5" or "1.5" into milli-units. Null if invalid or non-positive. */
    fun parse(text: String): Long? {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty() || cleaned.any { it != '.' && !it.isDigit() }) return null
        val parts = cleaned.split('.')
        if (parts.size > 2) return null
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        val fracRaw = parts.getOrNull(1).orEmpty()
        if (fracRaw.length > 3) return null
        val frac = fracRaw.padEnd(3, '0').toLong()
        val milli = whole * ONE + frac
        return milli.takeIf { it in 1..MAX_MILLI }
    }
}
