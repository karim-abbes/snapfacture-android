package com.snapfacture.ui.navigation

object Routes {
    const val WELCOME = "welcome"
    const val INVOICES = "invoices"
    const val CREATE = "invoices/create"
    const val DETAIL = "invoices/{invoiceId}"
    const val SETTINGS = "settings"
    const val CATALOG = "catalog"
    const val IMPORT = "import"
    const val EXPORT = "export"
    const val BACKUP = "backup"
    const val COMPANY = "company"
    const val SECURITY = "security"
    const val STATS = "stats"

    fun detail(invoiceId: Long) = "invoices/$invoiceId"
}
