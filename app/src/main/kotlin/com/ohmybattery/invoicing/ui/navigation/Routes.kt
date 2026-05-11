package com.ohmybattery.invoicing.ui.navigation

object Routes {
    const val INVOICES = "invoices"
    const val CREATE = "invoices/create"
    const val DETAIL = "invoices/{invoiceId}"
    const val SETTINGS = "settings"
    const val CATALOG = "catalog"

    fun detail(invoiceId: Long) = "invoices/$invoiceId"
}
