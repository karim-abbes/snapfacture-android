package com.ohmybattery.invoicing.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ohmybattery.invoicing.core.country.CountryProfile
import com.ohmybattery.invoicing.core.country.CountryProfiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) {
    private val keyTaxOptedOut = booleanPreferencesKey("tax_opted_out")

    val flow: Flow<CountrySettings> = context.countryDataStore.data.map { prefs ->
        CountrySettings(
            profile = CountryProfiles.detect(),
            taxOptedOut = prefs[keyTaxOptedOut] ?: false,
        )
    }

    suspend fun setTaxOptedOut(opted: Boolean) {
        context.countryDataStore.edit { it[keyTaxOptedOut] = opted }
    }
}
