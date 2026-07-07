package com.snapfacture.ui.company

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.data.local.dao.InvoiceDao
import com.snapfacture.data.local.entity.CompanyEntity
import com.snapfacture.data.preferences.CountryPreferences
import com.snapfacture.data.preferences.CountrySettings
import com.snapfacture.data.repository.CompanyRepository
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
    private val invoiceDao: InvoiceDao,
) : ViewModel() {

    val company: StateFlow<CompanyEntity?> =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val country: StateFlow<CountrySettings?> =
        countryPrefs.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(updated: CompanyEntity) {
        viewModelScope.launch {
            // Lowering the counter below the last issued number would create
            // duplicate invoice numbers (art. 242 nonies A CGI: gapless sequence).
            val minNext = (invoiceDao.maxNumber() ?: 0) + 1
            repo.update(updated.copy(nextInvoiceNumber = maxOf(updated.nextInvoiceNumber, minNext)))
        }
    }

    fun setTaxOptedOut(opted: Boolean) {
        viewModelScope.launch { countryPrefs.setTaxOptedOut(opted) }
    }
}
