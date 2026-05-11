package com.ohmybattery.invoicing.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmybattery.invoicing.data.local.entity.BatteryEntity
import com.ohmybattery.invoicing.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogDraft(
    val id: Long = 0L,
    val label: String = "",
    val priceTtcEuros: String = "",
    val withInstall: Boolean = false,
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
        fun from(b: BatteryEntity) = CatalogDraft(
            id = b.id,
            label = b.label,
            priceTtcEuros = "%.2f".format(b.priceTtcCents / 100.0).replace('.', ','),
            withInstall = b.withInstall,
            active = b.active,
        )
    }
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repo: BatteryRepository,
) : ViewModel() {

    val items: StateFlow<List<BatteryEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(draft: CatalogDraft, onDone: () -> Unit) {
        val cents = draft.parsedCents ?: return
        viewModelScope.launch {
            if (draft.id == 0L) {
                repo.insert(
                    BatteryEntity(
                        label = draft.label.trim(),
                        priceTtcCents = cents,
                        withInstall = draft.withInstall,
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
                        active = draft.active,
                    )
                )
            }
            onDone()
        }
    }

    fun toggleActive(item: BatteryEntity) {
        viewModelScope.launch { repo.setActive(item.id, !item.active) }
    }
}
