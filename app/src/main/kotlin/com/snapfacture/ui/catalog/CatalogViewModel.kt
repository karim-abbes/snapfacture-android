package com.snapfacture.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.data.local.entity.ProductEntity
import com.snapfacture.data.preferences.CountryPreferences
import com.snapfacture.data.repository.CompanyRepository
import com.snapfacture.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogDraft(
    val id: Long = 0L,
    val label: String = "",
    val priceTtcEuros: String = "",
    val withInstall: Boolean = false,
    val serviceNote: String = "",
    val active: Boolean = true,
) {
    val isValid: Boolean
        get() = label.isNotBlank() && parsedCents != null && parsedCents!! > 0

    val parsedCents: Long?
        get() = priceTtcEuros
            .replace(',', '.')
            .replace(" ", "")
            .toDoubleOrNull()
            ?.let { Math.round(it * 100.0) }

    companion object {
        fun from(b: ProductEntity) = CatalogDraft(
            id = b.id,
            label = b.label,
            priceTtcEuros = "%.2f".format(b.priceTtcCents / 100.0).replace('.', ','),
            withInstall = b.withInstall,
            serviceNote = b.serviceNote.orEmpty(),
            active = b.active,
        )
    }
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repo: ProductRepository,
    private val companyRepo: CompanyRepository,
    private val countryPrefs: CountryPreferences,
) : ViewModel() {

    val items: StateFlow<List<ProductEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun resolveDefaultTaxBp(): Int {
        val companyBp = companyRepo.get()?.defaultTaxBp ?: 0
        if (companyBp > 0) return companyBp
        return countryPrefs.flow.first().profile.defaultTaxRateBp
    }

    fun save(draft: CatalogDraft, onDone: () -> Unit) {
        val cents = draft.parsedCents ?: return
        viewModelScope.launch {
            val note = draft.serviceNote.trim().ifBlank { null }
            if (draft.id == 0L) {
                repo.insert(
                    ProductEntity(
                        label = draft.label.trim(),
                        priceTtcCents = cents,
                        vatRateBp = resolveDefaultTaxBp(),
                        withInstall = draft.withInstall,
                        serviceNote = if (draft.withInstall) note else null,
                        active = draft.active,
                    )
                )
            } else {
                val current = items.value.firstOrNull { it.id == draft.id } ?: return@launch
                repo.update(
                    current.copy(
                        label = draft.label.trim(),
                        priceTtcCents = cents,
                        withInstall = draft.withInstall,
                        serviceNote = if (draft.withInstall) note else null,
                        active = draft.active,
                    )
                )
            }
            onDone()
        }
    }

    fun toggleActive(item: ProductEntity) {
        viewModelScope.launch { repo.setActive(item.id, !item.active) }
    }
}
