package com.snapfacture.core.money

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Tax rates are stored as Int basis points (base 10 000): 2000 = 20 %,
 * 625 = 6.25 % — exact representation for US sales-tax rates that
 * permille (base 1000) could not hold.
 */
object TaxRate {

    /**
     * Audit-payload serialization. The payload historically wrote the rate
     * in permille ("200" for 20 %), and payloads are hashed — so the
     * canonical form stays permille-based forever: 2000 bp → "200"
     * (byte-identical to pre-migration), 625 bp → "62.5".
     */
    fun canonicalPermille(rateBp: Int): String {
        val whole = rateBp / 10
        val frac = rateBp % 10
        return if (frac == 0) whole.toString() else "$whole.$frac"
    }

    /** "20", "5,5", "6,25" — percent value, no trailing zeros, no unit. */
    fun formatPercent(rateBp: Int, locale: Locale): String {
        val whole = rateBp / 100
        val frac = rateBp % 100
        if (frac == 0) return whole.toString()
        val sep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return "$whole$sep" + frac.toString().padStart(2, '0').trimEnd('0')
    }

    /** Parses "6,25" or "6.25" percent into basis points. Null if invalid. */
    fun parsePercentToBp(text: String): Int? {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty() || cleaned.any { it != '.' && !it.isDigit() }) return null
        val parts = cleaned.split('.')
        if (parts.size > 2) return null
        val whole = parts[0].ifEmpty { "0" }.toIntOrNull() ?: return null
        val fracRaw = parts.getOrNull(1).orEmpty()
        if (fracRaw.length > 2) return null
        val frac = fracRaw.padEnd(2, '0').toInt()
        val bp = whole * 100 + frac
        return bp.takeIf { it in 0..10_000 }
    }
}
