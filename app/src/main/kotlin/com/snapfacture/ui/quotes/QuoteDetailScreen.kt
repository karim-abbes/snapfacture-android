package com.snapfacture.ui.quotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfacture.R
import com.snapfacture.core.country.LocalCountryProfile
import com.snapfacture.core.pdf.ShareInvoice
import com.snapfacture.data.local.entity.PaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteDetailScreen(
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    vm: QuoteDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val profile = LocalCountryProfile.current
    val q = state.quote

    var showConvertDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(q?.quote?.number?.let { stringResource(R.string.quote_list_n, it) } ?: "")
                },
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
        if (q == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (q.quote.convertedInvoiceId != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                stringResource(R.string.quote_converted_banner),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { q.quote.convertedInvoiceId?.let(onOpenInvoice) }) {
                                Text(stringResource(R.string.detail_credit_open_source))
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(q.client.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.detail_issued_on_m, profile.formatDate(q.quote.issueDate)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.quote_valid_until, profile.formatDate(q.quote.validUntil)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        q.quote.comment?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.detail_comment, it),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.detail_lines_section), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        q.lines.sortedBy { it.position }.forEach { l ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${l.quantity} × ${l.description}", Modifier.weight(1f))
                                Text(profile.formatMoney(l.lineTtcCents))
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        if (q.quote.totalVatCents != 0L) {
                            QuoteTotalRow(stringResource(R.string.create_total_ht), profile.formatMoney(q.quote.totalHtCents))
                            QuoteTotalRow(profile.taxLabel, profile.formatMoney(q.quote.totalVatCents))
                            Spacer(Modifier.height(6.dp))
                            QuoteTotalRow(stringResource(R.string.create_total_ttc), profile.formatMoney(q.quote.totalTtcCents), big = true)
                        } else {
                            QuoteTotalRow(stringResource(R.string.create_total_simple), profile.formatMoney(q.quote.totalTtcCents), big = true)
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        state.pdfFile?.let { file ->
                            context.startActivity(
                                ShareInvoice.intent(
                                    context = context,
                                    file = file,
                                    invoiceNumber = q.quote.number,
                                    companyName = q.quote.companyNameAtIssue.orEmpty(),
                                    recipientEmail = q.client.email,
                                    isQuote = true,
                                )
                            )
                        }
                    },
                    enabled = state.pdfFile != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.detail_share))
                }
            }
            if (q.quote.convertedInvoiceId == null) {
                item {
                    Button(
                        onClick = { showConvertDialog = true },
                        enabled = !state.isConverting,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        if (state.isConverting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.quote_detail_convert))
                        }
                    }
                }
            }
        }
    }

    if (showConvertDialog && q != null) {
        ConvertDialog(
            totalLabel = profile.formatMoney(q.quote.totalTtcCents),
            isConverting = state.isConverting,
            failed = state.convertFailed,
            onDismiss = { showConvertDialog = false },
            onConfirm = { method ->
                vm.convert(method) { invoiceId ->
                    showConvertDialog = false
                    onOpenInvoice(invoiceId)
                }
            },
        )
    }
}

@Composable
private fun QuoteTotalRow(label: String, value: String, big: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ConvertDialog(
    totalLabel: String,
    isConverting: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMethod) -> Unit,
) {
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
    val labels = listOf(
        PaymentMethod.CASH to stringResource(R.string.create_payment_cash),
        PaymentMethod.CARD to stringResource(R.string.create_payment_card),
        PaymentMethod.TRANSFER to stringResource(R.string.create_payment_transfer),
        PaymentMethod.CHECK to stringResource(R.string.create_payment_check),
    )
    AlertDialog(
        onDismissRequest = { if (!isConverting) onDismiss() },
        title = { Text(stringResource(R.string.quote_convert_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.quote_convert_dialog_body))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { (m, label) ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(label) },
                        )
                    }
                }
                if (failed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.quote_convert_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(method) }, enabled = !isConverting) {
                if (isConverting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.confirm_issue_confirm, totalLabel))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConverting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
