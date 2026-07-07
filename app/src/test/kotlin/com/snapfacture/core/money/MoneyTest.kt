package com.snapfacture.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    // --- htFromTtc: exact values against hand-computed references ----------

    @Test
    fun `ht from ttc at 20 percent`() {
        // 120,00 € TTC -> 100,00 € HT
        assertEquals(10_000L, Money.htFromTtc(12_000L, 200))
    }

    @Test
    fun `ht from ttc at 5_5 percent`() {
        // 105,50 € TTC -> 100,00 € HT
        assertEquals(10_000L, Money.htFromTtc(10_550L, 55))
    }

    @Test
    fun `ht from ttc at 10 percent`() {
        // 110,00 € TTC -> 100,00 € HT
        assertEquals(10_000L, Money.htFromTtc(11_000L, 100))
    }

    @Test
    fun `ht from ttc at rate zero is identity`() {
        assertEquals(9_999L, Money.htFromTtc(9_999L, 0))
    }

    @Test
    fun `ht rounds half up`() {
        // 0,99 € TTC at 20% -> exact HT is 82,5 cents -> rounds to 83
        assertEquals(83L, Money.htFromTtc(99L, 200))
    }

    @Test
    fun `ht survives large amounts without overflow`() {
        // 90 million euros TTC at 20%
        assertEquals(750_000_000_000L, Money.htFromTtc(900_000_000_000L, 200))
    }

    // --- lineAmounts: rounding must happen on the line total, not per unit --

    @Test
    fun `line rounding error does not scale with quantity`() {
        // 0,99 € TTC x 100 at 20%: line TTC = 99,00 €, exact HT = 82,50 €.
        // Per-unit rounding would give 83,00 € HT and 16,00 € VAT (33c short).
        val line = Money.lineAmounts(unitPriceTtcCents = 99L, quantityMilliUnits = 100_000L, vatRatePermille = 200)
        assertEquals(9_900L, line.ttc)
        assertEquals(8_250L, line.ht)
        assertEquals(1_650L, line.vat)
    }

    @Test
    fun `one euro times one hundred at 20 percent`() {
        // Exact HT is 8333,33 cents -> 8333; VAT 1667 (not 1700 as per-unit rounding gives)
        val line = Money.lineAmounts(unitPriceTtcCents = 100L, quantityMilliUnits = 100_000L, vatRatePermille = 200)
        assertEquals(10_000L, line.ttc)
        assertEquals(8_333L, line.ht)
        assertEquals(1_667L, line.vat)
    }

    @Test
    fun `line invariant ht plus vat equals ttc for many combinations`() {
        val prices = longArrayOf(1, 99, 100, 999, 1_050, 9_000, 12_345, 99_999)
        val unitQuantities = longArrayOf(1, 2, 3, 7, 10, 100, 999)
        val rates = intArrayOf(0, 21, 55, 85, 100, 200)
        for (p in prices) for (q in unitQuantities) for (r in rates) {
            val line = Money.lineAmounts(p, q * 1_000L, r)
            assertEquals("p=$p q=$q r=$r", line.ttc, line.ht + line.vat)
            // Whole quantities must stay exact: no rounding may creep in.
            assertEquals("p=$p q=$q r=$r", p * q, line.ttc)
            assertTrue("p=$p q=$q r=$r ht must be positive", line.ht > 0)
            if (r > 0) assertTrue("p=$p q=$q r=$r vat must not be negative", line.vat >= 0)
        }
    }

    @Test
    fun `decimal quantity keeps ht plus vat equal to ttc`() {
        val milliQuantities = longArrayOf(500, 1_500, 2_500, 12_500, 333, 1_001)
        for (p in longArrayOf(99, 100, 4_500, 12_345)) for (q in milliQuantities) for (r in intArrayOf(0, 55, 100, 200)) {
            val line = Money.lineAmounts(p, q, r)
            assertEquals("p=$p q=$q r=$r", line.ttc, line.ht + line.vat)
        }
    }

    @Test
    fun `one and a half hours at 45 euros is exact`() {
        // 45,00 € × 1,5 = 67,50 € TTC — no rounding needed.
        assertEquals(6_750L, Money.lineTtc(4_500L, 1_500L))
    }

    @Test
    fun `twelve and a half square meters at 10 euros is exact`() {
        assertEquals(12_500L, Money.lineTtc(1_000L, 12_500L))
    }

    @Test
    fun `decimal line total rounds half up on the fraction of a cent`() {
        // 0,99 € × 1,5 = 148,5 cents -> 149 (half-up)
        assertEquals(149L, Money.lineTtc(99L, 1_500L))
        // 0,33 € × 0,5 = 16,5 cents -> 17
        assertEquals(17L, Money.lineTtc(33L, 500L))
    }

    @Test
    fun `line rounding error stays under one cent`() {
        // |lineHt - exactHt| must be < 1 cent whatever the quantity.
        val rates = intArrayOf(21, 55, 85, 100, 200)
        for (r in rates) for (q in intArrayOf(1, 10, 100, 1000)) {
            val line = Money.lineAmounts(unitPriceTtcCents = 99L, quantityMilliUnits = q * 1_000L, vatRatePermille = r)
            val exactHt = (99.0 * q * 1000.0) / (1000.0 + r)
            assertTrue(
                "rate=$r qty=$q got=${line.ht} exact=$exactHt",
                kotlin.math.abs(line.ht - exactHt) <= 0.5,
            )
        }
    }

    @Test
    fun `vat from ttc is complement of ht`() {
        assertEquals(2_000L, Money.vatFromTtc(12_000L, 200))
        assertEquals(0L, Money.vatFromTtc(12_000L, 0))
    }
}
