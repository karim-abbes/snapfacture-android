package com.ohmybattery.invoicing.ui.invoices.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmybattery.invoicing.R
import com.ohmybattery.invoicing.core.money.Money
import com.ohmybattery.invoicing.data.local.entity.InvoiceType
import com.ohmybattery.invoicing.data.local.relation.InvoiceWithDetails
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    vm: InvoiceListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.companyName.ifBlank { stringResource(R.string.app_name) }) },
                actions = {
                    IconButton(onClick = onStats) {
                        Icon(Icons.Default.BarChart, contentDescription = stringResource(R.string.stats_title))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.invoice_list_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.invoice_list_new)) },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.padding(pad).fillMaxSize()) {
            PeriodSummary(state.period, state.periodRevenueCents, state.periodInvoiceCount)
            FilterBar(state, vm)
            if (state.invoices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.query.isNotBlank()) stringResource(R.string.invoice_list_empty_query, state.query)
                        else stringResource(R.string.invoice_list_empty_period),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.invoices, key = { it.invoice.id }) { inv ->
                        InvoiceRow(
                            inv = inv,
                            isCredited = inv.invoice.id in state.creditedInvoiceIds,
                            onOpen = onOpen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSummary(period: Period, revenue: Long, count: Int) {
    val title = when (period) {
        Period.Month -> stringResource(R.string.invoice_list_ca_month)
        Period.Year -> stringResource(R.string.invoice_list_ca_year)
        Period.All -> stringResource(R.string.invoice_list_ca_total)
    }
    val countLabel = when (period) {
        Period.Month -> stringResource(R.string.invoice_list_count_month, count)
        Period.Year -> stringResource(R.string.invoice_list_count_year, count)
        Period.All -> stringResource(R.string.invoice_list_count_all, count)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                Money.formatEurPlain(revenue),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                countLabel,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FilterBar(state: InvoiceListUiState, vm: InvoiceListViewModel) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.invoice_list_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodChip(state.period, Period.Month, stringResource(R.string.invoice_list_period_month), vm::setPeriod)
            PeriodChip(state.period, Period.Year, stringResource(R.string.invoice_list_period_year), vm::setPeriod)
            PeriodChip(state.period, Period.All, stringResource(R.string.invoice_list_period_all), vm::setPeriod)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::toggleSort) {
                Icon(
                    imageVector = if (state.descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = if (state.descending)
                        stringResource(R.string.invoice_list_sort_desc_desc)
                    else
                        stringResource(R.string.invoice_list_sort_desc_asc),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PeriodChip(current: Period, value: Period, label: String, onClick: (Period) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onClick(value) },
        label = { Text(label) },
    )
}

@Composable
private fun InvoiceRow(inv: InvoiceWithDetails, isCredited: Boolean, onOpen: (Long) -> Unit) {
    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val isCredit = inv.invoice.type == InvoiceType.CREDIT_NOTE
    val amountColor = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val title = if (isCredit)
        stringResource(R.string.invoice_list_credit_n, inv.invoice.number)
    else
        stringResource(R.string.invoice_list_invoice_n, inv.invoice.number)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(inv.invoice.id) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    inv.client.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    fmt.format(Date(inv.invoice.issueDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                if (isCredited) {
                    Text(
                        stringResource(R.string.invoice_list_credited_marker),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                Money.formatEurPlain(inv.invoice.totalTtcCents),
                style = MaterialTheme.typography.titleLarge,
                color = amountColor,
            )
        }
    }
}
