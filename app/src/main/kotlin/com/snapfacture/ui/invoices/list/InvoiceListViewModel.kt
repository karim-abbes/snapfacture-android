package com.snapfacture.ui.invoices.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.relation.InvoiceWithDetails
import com.snapfacture.data.repository.CompanyRepository
import com.snapfacture.data.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Calendar
import javax.inject.Inject

enum class Period { Month, Year, All }

data class InvoiceListUiState(
    val invoices: List<InvoiceWithDetails> = emptyList(),
    val creditedInvoiceIds: Set<Long> = emptySet(),
    val periodRevenueCents: Long = 0L,
    val periodInvoiceCount: Int = 0,
    val companyName: String = "",
    val query: String = "",
    val period: Period = Period.Month,
    val descending: Boolean = true,
    val isFirstRun: Boolean = false,
)

@HiltViewModel
class InvoiceListViewModel @Inject constructor(
    repo: InvoiceRepository,
    companyRepo: CompanyRepository,
) : ViewModel() {

    private val monthStart: Long = startOfMonthMillis()
    private val yearStart: Long = startOfYearMillis()

    private val filters = MutableStateFlow(FilterState())

    private data class FilterState(
        val query: String = "",
        val period: Period = Period.Month,
        val descending: Boolean = true,
    )

    val state: StateFlow<InvoiceListUiState> =
        combine(repo.observeAll(), filters, companyRepo.observe()) { all, f, company ->
            val creditedIds = all
                .filter { it.invoice.type == InvoiceType.CREDIT_NOTE }
                .mapNotNull { it.invoice.linkedInvoiceId }
                .toSet()
            val inPeriod = all.filter { matchesPeriod(it, f.period) }
            val revenue = inPeriod.sumOf { it.invoice.totalTtcCents }
            val invoiceCount = inPeriod.count { it.invoice.type == InvoiceType.INVOICE }

            val filtered = inPeriod
                .filter { matchesQuery(it, f.query) }
                .let { if (f.descending) it.sortedByDescending(::sortKey) else it.sortedBy(::sortKey) }

            InvoiceListUiState(
                invoices = filtered,
                creditedInvoiceIds = creditedIds,
                periodRevenueCents = revenue,
                periodInvoiceCount = invoiceCount,
                companyName = company?.name?.takeIf { it.isNotBlank() }.orEmpty(),
                isFirstRun = all.isEmpty(),
                query = f.query,
                period = f.period,
                descending = f.descending,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvoiceListUiState())

    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun setPeriod(p: Period) = filters.update { it.copy(period = p) }
    fun toggleSort() = filters.update { it.copy(descending = !it.descending) }

    private fun sortKey(inv: InvoiceWithDetails): Long = inv.invoice.issueDate

    private fun matchesPeriod(inv: InvoiceWithDetails, period: Period): Boolean = when (period) {
        Period.Month -> inv.invoice.issueDate >= monthStart
        Period.Year -> inv.invoice.issueDate >= yearStart
        Period.All -> true
    }

    private fun matchesQuery(inv: InvoiceWithDetails, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return inv.client.name.lowercase().contains(q) ||
            inv.invoice.number.toString().contains(q)
    }

    private fun startOfMonthMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfYearMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
