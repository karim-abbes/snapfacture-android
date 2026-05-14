package com.snapfacture.core.money

/** All monetary amounts are stored as Long cents to avoid floating-point drift. */
object Money {

    fun htFromTtc(ttcCents: Long, vatRatePermille: Int): Long {
        val ht = (ttcCents.toDouble() * 1000.0) / (1000.0 + vatRatePermille.toDouble())
        return Math.round(ht)
    }

    fun vatFromTtc(ttcCents: Long, vatRatePermille: Int): Long =
        ttcCents - htFromTtc(ttcCents, vatRatePermille)

    fun toDouble(cents: Long): Double = cents / 100.0
}
