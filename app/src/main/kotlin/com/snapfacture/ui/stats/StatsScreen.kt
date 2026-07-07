package com.snapfacture.ui.stats

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfacture.R
import com.snapfacture.core.country.LocalCountryProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    vm: StatsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PeriodChips(state.period, vm::setPeriod) }
            item { PeriodHeader(state.periodLabel) }
            item { KpiGrid(state) }
            item { TaxCard(state) }
            item { LeadersCard(stringResource(R.string.stats_top_products), state.topProducts) }
            item { LeadersCard(stringResource(R.string.stats_top_clients), state.topClients) }
        }
    }
}

@Composable
private fun PeriodChips(current: StatsPeriod, onChange: (StatsPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current == StatsPeriod.Month,
            onClick = { onChange(StatsPeriod.Month) },
            label = { Text(stringResource(R.string.stats_period_month)) },
        )
        FilterChip(
            selected = current == StatsPeriod.Quarter,
            onClick = { onChange(StatsPeriod.Quarter) },
            label = { Text(stringResource(R.string.stats_period_quarter)) },
        )
        FilterChip(
            selected = current == StatsPeriod.Year,
            onClick = { onChange(StatsPeriod.Year) },
            label = { Text(stringResource(R.string.stats_period_year)) },
        )
    }
}

@Composable
private fun PeriodHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun KpiGrid(state: StatsUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_kpi_revenue_ttc),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                LocalCountryProfile.current.formatMoney(state.revenueTtcCents),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.stats_kpi_revenue_ht_value, LocalCountryProfile.current.formatMoney(state.revenueHtCents)),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        MiniKpi(
            label = stringResource(R.string.stats_kpi_invoice_count),
            value = state.invoiceCount.toString(),
            modifier = Modifier.weight(1f),
        )
        MiniKpi(
            label = stringResource(R.string.stats_kpi_avg_ticket),
            value = LocalCountryProfile.current.formatMoney(state.averageTicketCents),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MiniKpi(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TaxCard(state: StatsUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_tax_collected, state.taxLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                LocalCountryProfile.current.formatMoney(state.taxCents),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.stats_tax_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LeadersCard(title: String, items: List<StatsLeader>) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.stats_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                items.forEachIndexed { index, leader ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(leader.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                            Text(
                                stringResource(R.string.stats_count_units, leader.count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            LocalCountryProfile.current.formatMoney(leader.totalCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (index < items.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
