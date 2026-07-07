package com.snapfacture.ui.invoices.detail

import android.print.PrintAttributes
import android.print.PrintManager
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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.entity.PaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    invoiceId: Long,
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    vm: InvoiceDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inv = state.invoice

    var showCreditDialog by rememberSaveable { mutableStateOf(false) }

    val isCredit = inv?.invoice?.type == InvoiceType.CREDIT_NOTE
    val title = inv?.invoice?.number?.let { n ->
        stringResource(if (isCredit) R.string.detail_credit_n else R.string.detail_invoice_n, n)
    } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isCredit) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        if (inv == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isCredit && state.sourceInvoiceNumber != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.detail_credit_for, state.sourceInvoiceNumber ?: 0),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                state.sourceInvoiceDate?.let {
                                    Text(
                                        stringResource(
                                            R.string.detail_credit_source_date,
                                            LocalCountryProfile.current.formatDate(it),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                            TextButton(
                                onClick = { inv.invoice.linkedInvoiceId?.let(onOpenInvoice) },
                            ) { Text(stringResource(R.string.detail_credit_open_source)) }
                        }
                    }
                }
            }
            if (!isCredit && state.linkedCreditNumber != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                stringResource(R.string.detail_credited_by, state.linkedCreditNumber ?: 0),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        val profile = LocalCountryProfile.current
                        Text(inv.client.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                if (isCredit) R.string.detail_issued_on_m else R.string.detail_issued_on_f,
                                profile.formatDate(inv.invoice.issueDate),
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.detail_payment_method, paymentLabel(inv.invoice.paymentMethod)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        inv.invoice.paymentNote?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.detail_motif, it), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inv.invoice.comment?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.detail_comment, it), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        val profile = LocalCountryProfile.current
                        Text(stringResource(R.string.detail_lines_section), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        inv.lines.sortedBy { it.position }.forEach { l ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("${com.snapfacture.core.money.Quantity.format(l.quantityMilliUnits, profile.locale)} × ${l.description}")
                                    l.extraNote?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(profile.formatMoney(l.lineTtcCents))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        val hasVat = inv.invoice.totalVatCents != 0L
                        if (hasVat) {
                            TotalRow(stringResource(R.string.create_total_ht), inv.invoice.totalHtCents)
                            val ratePct = if (inv.invoice.totalHtCents != 0L) {
                                val r = (inv.invoice.totalVatCents.toDouble() / inv.invoice.totalHtCents) * 100
                                val rounded = Math.round(r * 10) / 10.0
                                if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString()
                                else "%.1f".format(rounded).replace('.', ',')
                            } else "20"
                            TotalRow("${profile.taxLabel} ($ratePct%)", inv.invoice.totalVatCents)
                            Spacer(Modifier.height(4.dp))
                        }
                        TotalRow(
                            when {
                                isCredit -> stringResource(R.string.detail_to_refund)
                                hasVat -> stringResource(R.string.create_total_ttc)
                                else -> stringResource(R.string.create_total_simple)
                            },
                            inv.invoice.totalTtcCents,
                            big = true,
                            credit = isCredit,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val file = state.pdfFile ?: return@Button
                            context.startActivity(
                                ShareInvoice.intent(
                                    context = context,
                                    file = file,
                                    invoiceNumber = inv.invoice.number,
                                    companyName = inv.invoice.companyNameAtIssue.orEmpty(),
                                    recipientEmail = inv.client.email,
                                ),
                            )
                        },
                        enabled = state.pdfFile != null,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.detail_share))
                    }
                    val printJobTitle = title.replace(' ', '-').replace("N°", "")
                    OutlinedButton(
                        onClick = {
                            val file = state.pdfFile ?: return@OutlinedButton
                            val pm = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                            pm.print(
                                printJobTitle.ifBlank { "Invoice" },
                                FilePrintAdapter(file),
                                PrintAttributes.Builder()
                                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                    .build(),
                            )
                        },
                        enabled = state.pdfFile != null,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.detail_print))
                    }
                }
            }
            if (!isCredit && state.linkedCreditNumber == null) {
                item {
                    OutlinedButton(
                        onClick = { showCreditDialog = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.detail_issue_credit))
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = vm::regeneratePdf,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.detail_regenerate_pdf)) }
            }
        }
    }

    if (showCreditDialog) {
        CreditDialog(
            isSaving = state.isIssuingCredit,
            failed = state.creditFailed,
            onDismiss = { showCreditDialog = false },
            onConfirm = { reason ->
                vm.issueCredit(reason) { newId ->
                    showCreditDialog = false
                    onOpenInvoice(newId)
                }
            },
        )
    }
}

@Composable
private fun CreditDialog(
    isSaving: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.credit_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.credit_dialog_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.credit_dialog_motif_label)) },
                    placeholder = { Text(stringResource(R.string.credit_dialog_motif_hint)) },
                )
                if (failed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.credit_dialog_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.ifBlank { null }) },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.credit_dialog_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun TotalRow(label: String, cents: Long, big: Boolean = false, credit: Boolean = false) {
    val color = when {
        credit -> MaterialTheme.colorScheme.error
        big -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            LocalCountryProfile.current.formatMoney(cents),
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun paymentLabel(m: PaymentMethod): String = stringResource(
    when (m) {
        PaymentMethod.CASH -> R.string.create_payment_cash
        PaymentMethod.TRANSFER -> R.string.create_payment_transfer
        PaymentMethod.CARD -> R.string.create_payment_card
        PaymentMethod.CHECK -> R.string.create_payment_check
        PaymentMethod.OTHER -> R.string.create_payment_other
    },
)
