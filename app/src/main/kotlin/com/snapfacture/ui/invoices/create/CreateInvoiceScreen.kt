package com.snapfacture.ui.invoices.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfacture.R
import com.snapfacture.core.country.LocalCountryProfile
import com.snapfacture.core.money.Money
import com.snapfacture.core.money.Quantity
import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.local.entity.ProductEntity
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    onBack: () -> Unit,
    onIssued: (Long) -> Unit,
    onQuoteCreated: (Long) -> Unit,
    onOpenCatalog: () -> Unit,
    vm: CreateInvoiceViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var showFreeLine by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var editQtyProductId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title)) },
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
        bottomBar = {
            BottomCashBar(
                totalTtc = state.totalTtcCents,
                paymentMethod = state.paymentMethod,
                onPaymentMethodChange = vm::setPaymentMethod,
                enabled = state.canIssue,
                isSaving = state.isSaving,
                onIssue = { showConfirm = true },
                onQuote = { vm.createQuote(onQuoteCreated) },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ClientCard(state, vm) }
            item {
                DeliveryDateRow(
                    deliveryDateMillis = state.deliveryDateMillis,
                    onPick = { showDatePicker = true },
                    onClear = { vm.setDeliveryDate(null) },
                )
            }
            item {
                DeliveryAddressRow(
                    address = state.deliveryAddress,
                    onChange = vm::onDeliveryAddressChange,
                )
            }
            item { CatalogGrid(catalog, state, vm::addProduct, vm::decrement, onOpenCatalog, onFreeLine = { showFreeLine = true }) }
            if (state.hasInstallLine) item { DeliveryCard(state, vm) }
            if (state.cart.isNotEmpty()) item {
                CartSummary(state, onRemove = vm::decrement, onEditQuantity = { editQtyProductId = it })
            }
            if (state.cart.isNotEmpty()) item { CommentCard(state, vm) }
            state.error?.let { item { ErrorBanner(it) } }
        }
    }

    if (showFreeLine) {
        FreeLineDialog(
            onDismiss = { showFreeLine = false },
            onAdd = { label, cents ->
                showFreeLine = false
                vm.addFreeLine(label, cents)
            },
        )
    }

    if (showConfirm) {
        ConfirmIssueDialog(
            state = state,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                vm.issue(onIssued)
            },
        )
    }

    val editQtyLine = state.cart.firstOrNull { it.product.id == editQtyProductId }
    if (editQtyLine != null) {
        QuantityDialog(
            line = editQtyLine,
            onDismiss = { editQtyProductId = null },
            onConfirm = { milli ->
                vm.setQuantity(editQtyLine.product, milli)
                editQtyProductId = null
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.deliveryDateMillis ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { vm.setDeliveryDate(utcMidnightToLocalNoon(it)) }
                        showDatePicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// The M3 DatePicker returns midnight UTC: formatted in a western timezone that
// shifts to the previous day. Re-anchoring at local noon keeps the picked date
// stable whatever the device timezone.
private fun utcMidnightToLocalNoon(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            12, 0, 0,
        )
    }.timeInMillis
}

@Composable
private fun DeliveryAddressRow(
    address: String,
    onChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (!expanded && address.isBlank()) {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.create_delivery_address_add))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = address,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.create_delivery_address_label)) },
                maxLines = 2,
            )
            IconButton(onClick = { onChange(""); expanded = false }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeliveryDateRow(
    deliveryDateMillis: Long?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    if (deliveryDateMillis == null) {
        TextButton(onClick = onPick) {
            Text(stringResource(R.string.create_delivery_date_add))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPick, modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    stringResource(
                        R.string.create_delivery_date_label,
                        LocalCountryProfile.current.formatDate(deliveryDateMillis),
                    ),
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfirmIssueDialog(
    state: CreateUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val paymentLabel = when (state.paymentMethod) {
        PaymentMethod.CASH -> stringResource(R.string.create_payment_cash)
        PaymentMethod.CARD -> stringResource(R.string.create_payment_card)
        PaymentMethod.TRANSFER -> stringResource(R.string.create_payment_transfer)
        PaymentMethod.CHECK -> stringResource(R.string.create_payment_check)
        PaymentMethod.OTHER -> stringResource(R.string.create_payment_other)
    }
    val lineCount = state.cart.size
    val totalLabel = LocalCountryProfile.current.formatMoney(state.totalTtcCents)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_issue_title)) },
        text = {
            Column {
                Text(stringResource(R.string.confirm_issue_intro))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.confirm_issue_client, state.clientName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.confirm_issue_payment, paymentLabel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.confirm_issue_count, lineCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.confirm_issue_confirm, totalLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_back))
            }
        },
    )
}

@Composable
private fun ClientCard(state: CreateUiState, vm: CreateInvoiceViewModel) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.create_client_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = state.isPro,
                    onClick = { vm.onProToggle() },
                    label = { Text(stringResource(R.string.create_pro_toggle)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.clientName,
                onValueChange = vm::onClientNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.create_client_name_hint)) },
                singleLine = true,
            )
            if (state.isPro) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.clientSiret,
                    onValueChange = vm::onSiretChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.create_siret_hint)) },
                    singleLine = true,
                )
            }
            val suggestions = when {
                state.matchingClients.isNotEmpty() -> state.matchingClients
                state.clientName.isBlank() && state.selectedClient == null -> state.recentClients
                else -> emptyList()
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                suggestions.forEach { c ->
                    AssistChip(
                        onClick = { vm.selectClient(c) },
                        label = { Text(c.name) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun DeliveryCard(state: CreateUiState, vm: CreateInvoiceViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.create_delivery_section), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.clientPhone,
                onValueChange = vm::onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.create_phone_required)) },
                singleLine = true,
                isError = state.clientPhone.isBlank(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.clientEmail,
                onValueChange = vm::onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.create_email_hint)) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.clientAddress,
                onValueChange = vm::onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.create_address_required)) },
                isError = state.clientAddress.isBlank(),
            )
        }
    }
}

@Composable
private fun CatalogGrid(
    catalog: List<ProductEntity>,
    state: CreateUiState,
    onAdd: (ProductEntity) -> Unit,
    onRemove: (ProductEntity) -> Unit,
    onOpenCatalog: () -> Unit,
    onFreeLine: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.create_catalog_section), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        if (catalog.isEmpty()) {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(R.string.create_catalog_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.create_catalog_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.create_catalog_open))
                    }
                }
            }
        }
        if (catalog.isNotEmpty()) LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(((catalog.size + 1) / 2 * 130).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false,
        ) {
            items(catalog, key = { it.id }) { b ->
                val qty = state.cart.firstOrNull { it.product.id == b.id }?.quantityMilliUnits ?: 0L
                ProductTile(b, qty, onAdd, onRemove)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onFreeLine, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.create_free_line))
        }
    }
}

@Composable
private fun FreeLineDialog(
    onDismiss: () -> Unit,
    onAdd: (label: String, priceTtcCents: Long) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    val parsedCents = price.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100.0) }
    val canAdd = label.isNotBlank() && (parsedCents ?: 0L) > 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.free_line_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.free_line_label)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.free_line_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(label, parsedCents ?: 0L) },
                enabled = canAdd,
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ProductTile(
    b: ProductEntity,
    qty: Long,
    onAdd: (ProductEntity) -> Unit,
    onRemove: (ProductEntity) -> Unit,
) {
    val profile = LocalCountryProfile.current
    val selected = qty > 0
    Card(
        onClick = { onAdd(b) },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (b.withInstall) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    stringResource(if (b.withInstall) R.string.create_with_service else R.string.create_product_only),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                b.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    profile.formatMoney(b.priceTtcCents),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (selected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onRemove(b) }) {
                            Text("−", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            Quantity.format(qty, profile.locale),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = { onAdd(b) }) {
                            Text("+", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartSummary(
    state: CreateUiState,
    onRemove: (ProductEntity) -> Unit,
    onEditQuantity: (Long) -> Unit,
) {
    val profile = LocalCountryProfile.current
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.create_cart_section), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            state.cart.forEach { line ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { onEditQuantity(line.product.id) }) {
                        Text(
                            stringResource(
                                R.string.create_qty_times,
                                Quantity.format(line.quantityMilliUnits, profile.locale),
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(line.product.label, Modifier.weight(1f))
                    Text(profile.formatMoney(Money.lineTtc(line.product.priceTtcCents, line.quantityMilliUnits)))
                    // Free lines have no catalog tile to decrement from, so they
                    // get their remove affordance here.
                    if (line.product.id < 0) {
                        TextButton(onClick = { onRemove(line.product) }) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (state.totalVatCents != 0L) {
                TotalRow(stringResource(R.string.create_total_ht), state.totalHtCents)
                TotalRow(stringResource(R.string.create_total_vat), state.totalVatCents)
                Spacer(Modifier.height(6.dp))
                TotalRow(stringResource(R.string.create_total_ttc), state.totalTtcCents, big = true)
            } else {
                TotalRow(stringResource(R.string.create_total_simple), state.totalTtcCents, big = true)
            }
        }
    }
}

@Composable
private fun QuantityDialog(
    line: CartLine,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val profile = LocalCountryProfile.current
    var text by rememberSaveable {
        mutableStateOf(Quantity.format(line.quantityMilliUnits, profile.locale))
    }
    val parsed = Quantity.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.product.label, maxLines = 2) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.quantity_dialog_label)) },
                supportingText = { Text(stringResource(R.string.quantity_dialog_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null,
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TotalRow(label: String, cents: Long, big: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = if (big) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            LocalCountryProfile.current.formatMoney(cents),
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = if (big) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CommentCard(state: CreateUiState, vm: CreateInvoiceViewModel) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.create_comment_section), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.comment,
                onValueChange = vm::onCommentChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.create_comment_hint)) },
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun BottomCashBar(
    totalTtc: Long,
    paymentMethod: PaymentMethod,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    enabled: Boolean,
    isSaving: Boolean,
    onIssue: () -> Unit,
    onQuote: () -> Unit,
) {
    val cashLabel = stringResource(R.string.create_payment_cash)
    val cardLabel = stringResource(R.string.create_payment_card)
    val transferLabel = stringResource(R.string.create_payment_transfer)
    val checkLabel = stringResource(R.string.create_payment_check)
    Surface(tonalElevation = 6.dp) {
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PaymentMethod.CASH to cashLabel,
                    PaymentMethod.CARD to cardLabel,
                    PaymentMethod.TRANSFER to transferLabel,
                    PaymentMethod.CHECK to checkLabel,
                )
                    .forEach { (m, label) ->
                        FilterChip(
                            selected = paymentMethod == m,
                            onClick = { onPaymentMethodChange(m) },
                            label = { Text(label) },
                        )
                    }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onQuote,
                    enabled = enabled,
                    modifier = Modifier.weight(0.36f).height(56.dp),
                ) {
                    Text(stringResource(R.string.create_quote_button))
                }
            Button(
                onClick = onIssue,
                enabled = enabled,
                modifier = Modifier.weight(0.64f).height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        stringResource(R.string.create_cash_in, LocalCountryProfile.current.formatMoney(totalTtc)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            }
        }
    }
}
