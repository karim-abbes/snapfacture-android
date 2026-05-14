package com.snapfacture.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.relation.InvoiceWithDetails
import com.snapfacture.data.preferences.CountryPreferences
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

enum class StatsPeriod { Month, Quarter, Year }

data class StatsLeader(val name: String, val totalCents: Long, val count: Int)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.Quarter,
    val revenueHtCents: Long = 0L,
    val revenueTtcCents: Long = 0L,
    val taxCents: Long = 0L,
    val invoiceCount: Int = 0,
    val averageTicketCents: Long = 0L,
    val topProducts: List<StatsLeader> = emptyList(),
    val topClients: List<StatsLeader> = emptyList(),
    val periodLabel: String = "",
    val taxLabel: String = "TVA",
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    repo: InvoiceRepository,
    countryPrefs: CountryPreferences,
) : ViewModel() {

    private val period = MutableStateFlow(StatsPeriod.Quarter)

    fun setPeriod(p: StatsPeriod) = period.update { p }

    val state: StateFlow<StatsUiState> = combine(
        repo.observeAll(),
        period,
        countryPrefs.flow,
    ) { invoices, p, country ->
        val (start, label) = boundsFor(p)
        val inPeriod = invoices.filter { it.invoice.issueDate >= start }

        val revenueTtc = inPeriod.sumOf { it.invoice.totalTtcCents }
        val revenueHt = inPeriod.sumOf { it.invoice.totalHtCents }
        val tax = inPeriod.sumOf { it.invoice.totalVatCents }
        val invoiceCount = inPeriod.count { it.invoice.type == InvoiceType.INVOICE }
        val averageTicket = if (invoiceCount > 0) revenueTtc / invoiceCount else 0L

        StatsUiState(
            period = p,
            revenueHtCents = revenueHt,
            revenueTtcCents = revenueTtc,
            taxCents = tax,
            invoiceCount = invoiceCount,
            averageTicketCents = averageTicket,
            topProducts = topProducts(inPeriod),
            topClients = topClients(inPeriod),
            periodLabel = label,
            taxLabel = country.profile.taxLabel,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun topProducts(invoices: List<InvoiceWithDetails>): List<StatsLeader> {
        data class Acc(var total: Long = 0, var count: Int = 0)
        val map = HashMap<String, Acc>()
        invoices.forEach { inv ->
            inv.lines.forEach { line ->
                val acc = map.getOrPut(line.description) { Acc() }
                acc.total += line.lineTtcCents
                acc.count += line.quantity
            }
        }
        return map.entries
            .map { StatsLeader(it.key, it.value.total, it.value.count) }
            .sortedByDescending { it.totalCents }
            .take(5)
    }

    private fun topClients(invoices: List<InvoiceWithDetails>): List<StatsLeader> {
        data class Acc(var total: Long = 0, var count: Int = 0)
        val map = LinkedHashMap<String, Acc>()
        invoices.forEach { inv ->
            val acc = map.getOrPut(inv.client.name) { Acc() }
            acc.total += inv.invoice.totalTtcCents
            if (inv.invoice.type == InvoiceType.INVOICE) acc.count += 1
        }
        return map.entries
            .map { StatsLeader(it.key, it.value.total, it.value.count) }
            .sortedByDescending { it.totalCents }
            .take(5)
    }

    private fun boundsFor(p: StatsPeriod): Pair<Long, String> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return when (p) {
            StatsPeriod.Month -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.timeInMillis to monthLabel(cal)
            }
            StatsPeriod.Quarter -> {
                val month = cal.get(Calendar.MONTH)
                val firstMonth = (month / 3) * 3
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.MONTH, firstMonth)
                cal.timeInMillis to quarterLabel(cal)
            }
            StatsPeriod.Year -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis to cal.get(Calendar.YEAR).toString()
            }
        }
    }

    private fun monthLabel(cal: Calendar): String =
        cal.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault()) +
            " " + cal.get(Calendar.YEAR)

    private fun quarterLabel(cal: Calendar): String {
        val q = cal.get(Calendar.MONTH) / 3 + 1
        return "T$q ${cal.get(Calendar.YEAR)}"
    }
}
