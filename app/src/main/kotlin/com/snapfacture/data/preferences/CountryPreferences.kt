package com.snapfacture.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.snapfacture.core.country.CountryProfile
import com.snapfacture.core.country.CountryProfiles
import com.snapfacture.data.local.dao.CompanyDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

private val Context.countryDataStore by preferencesDataStore(name = "country_prefs")

data class CountrySettings(
    val profile: CountryProfile,
    val taxOptedOut: Boolean,
)

@Singleton
class CountryPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val companyDao: CompanyDao,
) {
    private val keyTaxOptedOut = booleanPreferencesKey("tax_opted_out")

    val flow: Flow<CountrySettings> = combine(
        context.countryDataStore.data,
        companyDao.observe(),
    ) { prefs, company ->
        val profile = company?.country
            ?.let { CountryProfiles.byCountryName(it) }
            ?: CountryProfiles.detect()
        CountrySettings(
            profile = profile,
            taxOptedOut = prefs[keyTaxOptedOut] ?: false,
        )
    }

    suspend fun setTaxOptedOut(opted: Boolean) {
        context.countryDataStore.edit { it[keyTaxOptedOut] = opted }
    }
}
