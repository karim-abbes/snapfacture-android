package com.ohmybattery.invoicing.ui.company

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmybattery.invoicing.data.local.entity.CompanyEntity
import com.ohmybattery.invoicing.data.preferences.CountryPreferences
import com.ohmybattery.invoicing.data.preferences.CountrySettings
import com.ohmybattery.invoicing.data.repository.CompanyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompanyInfoViewModel @Inject constructor(
    private val repo: CompanyRepository,
    private val countryPrefs: CountryPreferences,
) : ViewModel() {

    val company: StateFlow<CompanyEntity?> =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val country: StateFlow<CountrySettings?> =
        countryPrefs.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(updated: CompanyEntity) {
        viewModelScope.launch { repo.update(updated) }
    }

    fun setTaxOptedOut(opted: Boolean) {
        viewModelScope.launch { countryPrefs.setTaxOptedOut(opted) }
    }
}
