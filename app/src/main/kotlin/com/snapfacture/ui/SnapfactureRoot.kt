package com.snapfacture.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.snapfacture.ui.settings.SettingsScreen
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
        NavHost(navController = nav, startDestination = start) {
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
                onStats = { nav.navigate(Routes.STATS) },
                onOpenCatalog = { nav.navigate(Routes.CATALOG) },
            )
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.CREATE) {
            CreateInvoiceScreen(
                onBack = { nav.popBackStack() },
                onIssued = { id ->
                    nav.popBackStack()
                    nav.navigate(Routes.detail(id))
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
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenCatalog = { nav.navigate(Routes.CATALOG) },
                onOpenImport = { nav.navigate(Routes.IMPORT) },
                onOpenExport = { nav.navigate(Routes.EXPORT) },
                onOpenBackup = { nav.navigate(Routes.BACKUP) },
                onOpenCompany = { nav.navigate(Routes.COMPANY) },
                onOpenSecurity = { nav.navigate(Routes.SECURITY) },
            )
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
