package com.snapfacture.ui.quotes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.core.pdf.InvoicePdfGenerator
import com.snapfacture.core.pdf.asInvoiceForPdf
import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.local.relation.QuoteWithDetails
import com.snapfacture.data.preferences.CountryPreferences
import com.snapfacture.data.repository.QuoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class QuoteListViewModel @Inject constructor(
    repo: QuoteRepository,
) : ViewModel() {
    val quotes: StateFlow<List<QuoteWithDetails>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class QuoteDetailUiState(
    val quote: QuoteWithDetails? = null,
    val pdfFile: File? = null,
    val isConverting: Boolean = false,
    val convertFailed: Boolean = false,
)

@HiltViewModel
class QuoteDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val quoteRepo: QuoteRepository,
    private val invoiceRepo: com.snapfacture.data.repository.InvoiceRepository,
    private val companyRepo: com.snapfacture.data.repository.CompanyRepository,
    private val pdfGenerator: InvoicePdfGenerator,
    private val countryPrefs: CountryPreferences,
) : ViewModel() {

    private val quoteId: Long = handle.get<Long>("quoteId") ?: 0L

    private val _state = MutableStateFlow(QuoteDetailUiState())
    val state: StateFlow<QuoteDetailUiState> = _state.asStateFlow()

    init { load() }

    private fun load() = viewModelScope.launch {
        val q = quoteRepo.get(quoteId)
        var file = q?.quote?.pdfPath?.let { File(it).takeIf(File::exists) }
        // The quote PDF is regenerable at will (quotes are not immutable
        // legal documents), so a missing file is silently rebuilt.
        if (q != null && file == null) {
            file = runCatching { renderPdf(q) }.getOrNull()
            file?.let { quoteRepo.attachPdf(quoteId, it.absolutePath) }
        }
        _state.update { it.copy(quote = q, pdfFile = file) }
    }

    private suspend fun renderPdf(q: QuoteWithDetails): File {
        val company = companyRepo.get() ?: error("Company missing")
        val settings = countryPrefs.flow.first()
        return withContext(Dispatchers.IO) {
            pdfGenerator.generate(
                invoice = q.asInvoiceForPdf(),
                company = company,
                country = settings.profile,
                taxOptedOut = q.quote.taxOptedOutAtIssue,
                isQuote = true,
            )
        }
    }

    fun convert(paymentMethod: PaymentMethod, onDone: (Long) -> Unit) {
        val q = _state.value.quote ?: return
        if (q.quote.convertedInvoiceId != null || _state.value.isConverting) return
        _state.update { it.copy(isConverting = true, convertFailed = false) }
        viewModelScope.launch {
            val invoiceId = try {
                quoteRepo.convertToInvoice(q.quote.id, paymentMethod)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isConverting = false, convertFailed = true) }
                return@launch
            }
            // The invoice legally exists past this point; its PDF is
            // best-effort and regenerable from the invoice detail screen.
            runCatching {
                val details = invoiceRepo.get(invoiceId) ?: error("Invoice missing after conversion")
                val company = companyRepo.get() ?: error("Company missing")
                val settings = countryPrefs.flow.first()
                val file = withContext(Dispatchers.IO) {
                    pdfGenerator.generate(
                        invoice = details,
                        company = company,
                        country = settings.profile,
                        taxOptedOut = q.quote.taxOptedOutAtIssue,
                    )
                }
                invoiceRepo.attachPdf(invoiceId, file.absolutePath)
            }.onFailure { if (it is CancellationException) throw it }
            _state.update { it.copy(isConverting = false) }
            onDone(invoiceId)
        }
    }
}
