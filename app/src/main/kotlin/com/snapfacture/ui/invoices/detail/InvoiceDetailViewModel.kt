package com.snapfacture.ui.invoices.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.core.facturx.FacturXGenerator
import com.snapfacture.core.pdf.InvoicePdfGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import com.snapfacture.data.local.entity.CompanyEntity
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.relation.InvoiceWithDetails
import com.snapfacture.data.preferences.CountryPreferences
import com.snapfacture.data.repository.CompanyRepository
import com.snapfacture.data.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val creditFailed: Boolean = false,
)

@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val invoiceRepo: InvoiceRepository,
    private val companyRepo: CompanyRepository,
    private val pdfGenerator: InvoicePdfGenerator,
    private val facturXGenerator: FacturXGenerator,
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
        val file = withContext(Dispatchers.IO) {
            pdfGenerator.generate(
                invoice = inv,
                company = company,
                country = countrySettings.profile,
                taxOptedOut = countrySettings.taxOptedOut,
                sourceInvoiceNumber = _state.value.sourceInvoiceNumber,
                sourceInvoiceDateMillis = _state.value.sourceInvoiceDate,
            )
        }
        invoiceRepo.attachPdf(inv.invoice.id, file.absolutePath)
        _state.update { it.copy(pdfFile = file) }
    }

    // The XML is cheap and derived: regenerated on every share rather than
    // stored, so it always reflects the (immutable) invoice row.
    fun shareFacturX(onReady: (File) -> Unit) = viewModelScope.launch {
        val inv = _state.value.invoice ?: return@launch
        val file = withContext(Dispatchers.IO) {
            val xml = facturXGenerator.buildXml(inv, sourceInvoiceNumber = _state.value.sourceInvoiceNumber)
            val dir = File(context.filesDir, "invoices").apply { mkdirs() }
            val prefix = if (inv.invoice.type == InvoiceType.CREDIT_NOTE) "AV" else "F"
            File(dir, "$prefix-${inv.invoice.number}.xml").apply { writeText(xml) }
        }
        onReady(file)
    }

    fun issueCredit(reason: String?, onDone: (Long) -> Unit) {
        val inv = _state.value.invoice ?: return
        val company = _state.value.company ?: return
        if (inv.invoice.type != InvoiceType.INVOICE) return
        if (_state.value.linkedCreditNumber != null) return
        _state.update { it.copy(isIssuingCredit = true, creditFailed = false) }
        viewModelScope.launch {
            val newId = try {
                invoiceRepo.issueCredit(inv.invoice.id, reason)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isIssuingCredit = false, creditFailed = true) }
                return@launch
            }
            // The credit note legally exists past this point: a PDF failure must
            // not keep the user on a screen where a second one could be issued.
            // The PDF can be regenerated from the credit note's detail screen.
            runCatching {
                val details = invoiceRepo.get(newId) ?: error("Credit note not found after issue")
                val countrySettings = countryPrefs.flow.first()
                val file = withContext(Dispatchers.IO) {
                    pdfGenerator.generate(
                        invoice = details,
                        company = company,
                        country = countrySettings.profile,
                        taxOptedOut = countrySettings.taxOptedOut,
                        sourceInvoiceNumber = inv.invoice.number,
                        sourceInvoiceDateMillis = inv.invoice.issueDate,
                    )
                }
                invoiceRepo.attachPdf(newId, file.absolutePath)
            }.onFailure { if (it is CancellationException) throw it }
            _state.update { it.copy(isIssuingCredit = false) }
            onDone(newId)
        }
    }
}
