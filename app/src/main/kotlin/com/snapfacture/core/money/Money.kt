package com.snapfacture.core.money

/** All monetary amounts are stored as Long cents to avoid floating-point drift. */
object Money {

    // Pure integer half-up rounding of ttc * 1000 / (1000 + rate):
    // floor((2a + b) / 2b) == round(a / b) for the magnitudes handled here.
    fun htFromTtc(ttcCents: Long, vatRatePermille: Int): Long {
        val numerator = ttcCents * 1000L
        val denominator = 1000L + vatRatePermille
        return Math.floorDiv(2L * numerator + denominator, 2L * denominator)
    }

    fun vatFromTtc(ttcCents: Long, vatRatePermille: Int): Long =
        ttcCents - htFromTtc(ttcCents, vatRatePermille)

    /**
     * Splits a line total (unit TTC × quantity) into HT/VAT/TTC cents.
     * Rounding happens once, on the line total — never per unit — so the
     * rounding error stays under half a cent regardless of the quantity.
     */
    fun lineAmounts(unitPriceTtcCents: Long, quantity: Int, vatRatePermille: Int): LineAmounts {
        val ttc = unitPriceTtcCents * quantity
        val ht = if (vatRatePermille == 0) ttc else htFromTtc(ttc, vatRatePermille)
        return LineAmounts(ht = ht, vat = ttc - ht, ttc = ttc)
    }

    data class LineAmounts(val ht: Long, val vat: Long, val ttc: Long)
}
