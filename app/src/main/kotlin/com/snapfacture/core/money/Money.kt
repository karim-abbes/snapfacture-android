package com.snapfacture.core.money

/** All monetary amounts are stored as Long cents to avoid floating-point drift. */
object Money {

    // Pure integer half-up rounding of ttc * 10000 / (10000 + rate):
    // floor((2a + b) / 2b) == round(a / b) for the magnitudes handled here.
    fun htFromTtc(ttcCents: Long, vatRateBp: Int): Long {
        val numerator = ttcCents * 10_000L
        val denominator = 10_000L + vatRateBp
        return Math.floorDiv(2L * numerator + denominator, 2L * denominator)
    }

    fun vatFromTtc(ttcCents: Long, vatRateBp: Int): Long =
        ttcCents - htFromTtc(ttcCents, vatRateBp)

    /**
     * Splits a line total (unit TTC × quantity) into HT/VAT/TTC cents.
     * Rounding happens once, on the line total — never per unit — so the
     * rounding error stays under half a cent regardless of the quantity.
     */
    fun lineAmounts(unitPriceTtcCents: Long, quantityMilliUnits: Long, vatRateBp: Int): LineAmounts {
        val ttc = lineTtc(unitPriceTtcCents, quantityMilliUnits)
        val ht = if (vatRateBp == 0) ttc else htFromTtc(ttc, vatRateBp)
        return LineAmounts(ht = ht, vat = ttc - ht, ttc = ttc)
    }

    /** Line total in cents: unit price × quantity in milli-units, half-up. */
    fun lineTtc(unitPriceTtcCents: Long, quantityMilliUnits: Long): Long {
        val numerator = unitPriceTtcCents * quantityMilliUnits
        return Math.floorDiv(2L * numerator + 1000L, 2_000L)
    }

    data class LineAmounts(val ht: Long, val vat: Long, val ttc: Long)
}
