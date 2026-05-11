package com.ohmybattery.invoicing.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ohmybattery.invoicing.ui.catalog.CatalogScreen
import com.ohmybattery.invoicing.ui.invoices.create.CreateInvoiceScreen
import com.ohmybattery.invoicing.ui.invoices.detail.InvoiceDetailScreen
import com.ohmybattery.invoicing.ui.invoices.list.InvoiceListScreen
import com.ohmybattery.invoicing.ui.navigation.Routes
import com.ohmybattery.invoicing.ui.settings.SettingsScreen

@Composable
fun OhmybatteryRoot() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.INVOICES) {
        composable(Routes.INVOICES) {
            InvoiceListScreen(
                onCreate = { nav.navigate(Routes.CREATE) },
                onOpen = { nav.navigate(Routes.detail(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CREATE) {
            CreateInvoiceScreen(
                onBack = { nav.popBackStack() },
                onIssued = { id ->
                    nav.popBackStack()
                    nav.navigate(Routes.detail(id))
                },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("invoiceId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("invoiceId") ?: 0L
            InvoiceDetailScreen(invoiceId = id, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenCatalog = { nav.navigate(Routes.CATALOG) },
            )
        }
        composable(Routes.CATALOG) {
            CatalogScreen(onBack = { nav.popBackStack() })
        }
    }
}
