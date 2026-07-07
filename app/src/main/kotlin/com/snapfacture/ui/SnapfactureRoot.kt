package com.snapfacture.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.snapfacture.R
import com.snapfacture.core.country.LocalCountryProfile
import com.snapfacture.ui.backup.BackupScreen
import com.snapfacture.ui.company.CompanyInfoScreen
import com.snapfacture.ui.csvexport.ExportScreen
import com.snapfacture.ui.csvimport.ImportScreen
import com.snapfacture.ui.catalog.CatalogScreen
import com.snapfacture.ui.invoices.create.CreateInvoiceScreen
import com.snapfacture.ui.invoices.detail.InvoiceDetailScreen
import com.snapfacture.ui.invoices.list.InvoiceListScreen
import com.snapfacture.ui.navigation.Routes
import com.snapfacture.ui.security.SecurityScreen
import com.snapfacture.ui.fec.FecScreen
import com.snapfacture.ui.quotes.QuoteDetailScreen
import com.snapfacture.ui.quotes.QuoteListScreen
import com.snapfacture.ui.settings.SettingsScreen
import com.snapfacture.ui.taxreport.TaxReportScreen
import com.snapfacture.ui.stats.StatsScreen
import com.snapfacture.ui.welcome.WelcomeScreen

@Composable
fun SnapfactureRoot(vm: StartupViewModel = hiltViewModel()) {
    val startup by vm.state.collectAsStateWithLifecycle()
    val resolved = startup
    if (resolved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val nav = rememberNavController()
    val start = if (resolved.needsOnboarding) Routes.WELCOME else Routes.INVOICES
    CompositionLocalProvider(LocalCountryProfile provides resolved.profile) {
        val backStackEntry by nav.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        Scaffold(
            bottomBar = {
                if (currentRoute in TOP_LEVEL_ROUTES) {
                    SnapBottomBar(currentRoute) { route -> nav.switchTab(route) }
                }
            },
        ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(innerPadding),
        ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onDone = {
                    nav.navigate(Routes.INVOICES) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.INVOICES) {
            InvoiceListScreen(
                onCreate = { nav.navigate(Routes.CREATE) },
                onOpen = { nav.navigate(Routes.detail(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCatalog = { nav.navigate(Routes.CATALOG) },
            )
        }
        composable(Routes.STATS) {
            StatsScreen()
        }
        composable(Routes.CREATE) {
            CreateInvoiceScreen(
                onBack = { nav.popBackStack() },
                onIssued = { id ->
                    nav.popBackStack()
                    nav.navigate(Routes.detail(id))
                },
                onQuoteCreated = { id ->
                    nav.popBackStack()
                    nav.navigate(Routes.quoteDetail(id))
                },
                onOpenCatalog = { nav.navigate(Routes.CATALOG) },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("invoiceId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("invoiceId") ?: 0L
            InvoiceDetailScreen(
                invoiceId = id,
                onBack = { nav.popBackStack() },
                onOpenInvoice = { other ->
                    nav.navigate(Routes.detail(other)) {
                        popUpTo(Routes.INVOICES)
                    }
                },
            )
        }
        composable(Routes.QUOTES) {
            QuoteListScreen(
                onOpen = { nav.navigate(Routes.quoteDetail(it)) },
                onCreate = { nav.navigate(Routes.CREATE) },
            )
        }
        composable(
            Routes.QUOTE_DETAIL,
            arguments = listOf(navArgument("quoteId") { type = NavType.LongType }),
        ) {
            QuoteDetailScreen(
                onBack = { nav.popBackStack() },
                onOpenInvoice = { id ->
                    nav.navigate(Routes.detail(id)) {
                        popUpTo(Routes.INVOICES)
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenCatalog = { nav.navigate(Routes.CATALOG) },
                onOpenImport = { nav.navigate(Routes.IMPORT) },
                onOpenExport = { nav.navigate(Routes.EXPORT) },
                onOpenFec = { nav.navigate(Routes.FEC) },
                onOpenTaxReport = { nav.navigate(Routes.TAX_REPORT) },
                onOpenBackup = { nav.navigate(Routes.BACKUP) },
                onOpenCompany = { nav.navigate(Routes.COMPANY) },
                onOpenSecurity = { nav.navigate(Routes.SECURITY) },
            )
        }
        composable(Routes.TAX_REPORT) {
            TaxReportScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.FEC) {
            FecScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.COMPANY) {
            CompanyInfoScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SECURITY) {
            SecurityScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.CATALOG) {
            CatalogScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.IMPORT) {
            ImportScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.EXPORT) {
            ExportScreen(onBack = { nav.popBackStack() })
        }
        }
        }
    }
}

private val TOP_LEVEL_ROUTES = setOf(Routes.INVOICES, Routes.QUOTES, Routes.STATS)

private data class Tab(val route: String, val icon: ImageVector, val labelRes: Int)

private val TABS = listOf(
    Tab(Routes.INVOICES, Icons.Default.ReceiptLong, R.string.nav_invoices),
    Tab(Routes.QUOTES, Icons.Default.Description, R.string.nav_quotes),
    Tab(Routes.STATS, Icons.Default.BarChart, R.string.nav_stats),
)

// Standard Compose tab navigation: never stack tabs, save/restore each tab's
// own back stack, and keep a single instance of the tapped destination.
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.INVOICES) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun SnapBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { if (currentRoute != tab.route) onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}
