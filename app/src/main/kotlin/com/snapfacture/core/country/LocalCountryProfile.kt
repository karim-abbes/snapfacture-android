package com.snapfacture.core.country

import androidx.compose.runtime.compositionLocalOf

/**
 * Active CountryProfile available to any composable below the provider.
 * The provider lives in SnapfactureRoot and tracks the company.country field.
 */
val LocalCountryProfile = compositionLocalOf<CountryProfile> { FranceProfile }
