package com.snapfacture.ui.taxreport

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snapfacture.R
import com.snapfacture.core.country.LocalCountryProfile
import com.snapfacture.data.local.dao.InvoiceDao
import com.snapfacture.data.local.dao.VatRateBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Calendar
import javax.inject.Inject

data class QuarterOption(val label: String, val from: Long, val to: Long)

data class TaxReportUiState(
    val quarters: List<QuarterOption> = emptyList(),
    val selected: Int = 0,
    val rows: List<VatRateBreakdown> = emptyList(),
) {
    val totalHt: Long get() = rows.sumOf { it.htCents }
    val totalVat: Long get() = rows.sumOf { it.vatCents }
    val totalTtc: Long get() = rows.sumOf { it.ttcCents }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaxReportViewModel @Inject constructor(
    @ApplicationContext context: Context,
    invoiceDao: InvoiceDao,
) : ViewModel() {

    // The current quarter plus the three before it: what a quarterly
    // filing actually needs. Older periods live in the FEC export.
    private val quarters: List<QuarterOption> = buildList {
        val cal = Calendar.getInstance()
        repeat(4) {
            val quarter = cal.get(Calendar.MONTH) / 3
            val start = (cal.clone() as Calendar).apply {
                set(Calendar.MONTH, quarter * 3)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 3) }
            add(
                QuarterOption(
                    label = context.getString(R.string.stats_quarter_label, quarter + 1, start.get(Calendar.YEAR)),
                    from = start.timeInMillis,
                    to = end.timeInMillis,
                )
            )
            cal.add(Calendar.MONTH, -3)
        }
    }

    private val selected = MutableStateFlow(0)

    fun select(index: Int) = selected.update { index }

    val state: StateFlow<TaxReportUiState> = combine(
        selected,
        selected.flatMapLatest { idx ->
            val q = quarters[idx]
            invoiceDao.vatBreakdown(q.from, q.to)
        },
    ) { idx, rows ->
        TaxReportUiState(quarters = quarters, selected = idx, rows = rows)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TaxReportUiState(quarters = quarters),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxReportScreen(
    onBack: () -> Unit,
    vm: TaxReportViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile = LocalCountryProfile.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tax_report_title, profile.taxLabel)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.quarters.forEachIndexed { idx, q ->
                        FilterChip(
                            selected = state.selected == idx,
                            onClick = { vm.select(idx) },
                            label = { Text(q.label) },
                        )
                    }
                }
            }
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.tax_report_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                stringResource(R.string.tax_report_total_tax, profile.taxLabel),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                profile.formatMoney(state.totalVat),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                stringResource(R.string.stats_tax_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            state.rows.forEach { row ->
                                val rate = row.ratePermille / 10.0
                                val rateLabel = if (rate == rate.toInt().toDouble()) {
                                    rate.toInt().toString()
                                } else {
                                    String.format(profile.locale, "%.1f", rate)
                                }
                                Text(
                                    stringResource(R.string.tax_report_rate, rateLabel),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                ReportRow(stringResource(R.string.tax_report_ht), profile.formatMoney(row.htCents))
                                ReportRow(profile.taxLabel, profile.formatMoney(row.vatCents))
                                Spacer(Modifier.height(10.dp))
                            }
                            HorizontalDivider(Modifier.padding(bottom = 8.dp))
                            ReportRow(stringResource(R.string.tax_report_total_ht), profile.formatMoney(state.totalHt), big = true)
                            ReportRow(stringResource(R.string.tax_report_total_ttc), profile.formatMoney(state.totalTtc), big = true)
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.tax_report_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String, big: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
