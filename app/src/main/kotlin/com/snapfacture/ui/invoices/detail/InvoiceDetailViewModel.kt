package com.snapfacture.ui.invoices.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.core.pdf.InvoicePdfGenerator
import com.snapfacture.data.local.entity.CompanyEntity
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.relation.InvoiceWithDetails
import com.snapfacture.data.preferences.CountryPreferences
import com.snapfacture.data.repository.CompanyRepository
import com.snapfacture.data.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DetailUiState(
    val invoice: InvoiceWithDetails? = null,
    val company: CompanyEntity? = null,
    val pdfFile: File? = null,
    val linkedCreditNumber: Int? = null,
    val sourceInvoiceNumber: Int? = null,
    val sourceInvoiceDate: Long? = null,
    val isIssuingCredit: Boolean = false,
)

@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val invoiceRepo: InvoiceRepository,
    private val companyRepo: CompanyRepository,
    private val pdfGenerator: InvoicePdfGenerator,
    private val countryPrefs: CountryPreferences,
) : ViewModel() {

    val invoiceId: Long = handle.get<Long>("invoiceId") ?: 0L

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init { load() }

    private fun load() = viewModelScope.launch {
        val inv = invoiceRepo.get(invoiceId)
        val company = companyRepo.get()
        val file = inv?.invoice?.pdfPath?.let { File(it).takeIf(File::exists) }
        val linked = if (inv?.invoice?.type == InvoiceType.INVOICE) invoiceRepo.findCreditFor(invoiceId) else null
        val source = if (inv?.invoice?.type == InvoiceType.CREDIT_NOTE) {
            inv.invoice.linkedInvoiceId?.let { invoiceRepo.get(it)?.invoice }
        } else null
        _state.update {
            it.copy(
                invoice = inv,
                company = company,
                pdfFile = file,
                linkedCreditNumber = linked?.number,
                sourceInvoiceNumber = source?.number,
                sourceInvoiceDate = source?.issueDate,
            )
        }
    }

    fun regeneratePdf() = viewModelScope.launch {
        val inv = _state.value.invoice ?: return@launch
        val company = _state.value.company ?: return@launch
        val countrySettings = countryPrefs.flow.first()
        val file = pdfGenerator.generate(
            invoice = inv,
            company = company,
            country = countrySettings.profile,
            taxOptedOut = countrySettings.taxOptedOut,
            sourceInvoiceNumber = _state.value.sourceInvoiceNumber,
            sourceInvoiceDateMillis = _state.value.sourceInvoiceDate,
        )
        invoiceRepo.attachPdf(inv.invoice.id, file.absolutePath)
        _state.update { it.copy(pdfFile = file) }
    }

    fun issueCredit(reason: String?, onDone: (Long) -> Unit) {
        val inv = _state.value.invoice ?: return
        val company = _state.value.company ?: return
        if (inv.invoice.type != InvoiceType.INVOICE) return
        if (_state.value.linkedCreditNumber != null) return
        _state.update { it.copy(isIssuingCredit = true) }
        viewModelScope.launch {
            try {
                val newId = invoiceRepo.issueCredit(inv.invoice.id, reason)
                val details = invoiceRepo.get(newId) ?: error("Avoir introuvable")
                val countrySettings = countryPrefs.flow.first()
                val file = pdfGenerator.generate(
                    invoice = details,
                    company = company,
                    country = countrySettings.profile,
                    taxOptedOut = countrySettings.taxOptedOut,
                    sourceInvoiceNumber = inv.invoice.number,
                    sourceInvoiceDateMillis = inv.invoice.issueDate,
                )
                invoiceRepo.attachPdf(newId, file.absolutePath)
                _state.update { it.copy(isIssuingCredit = false) }
                onDone(newId)
            } catch (_: Throwable) {
                _state.update { it.copy(isIssuingCredit = false) }
            }
        }
    }
}
