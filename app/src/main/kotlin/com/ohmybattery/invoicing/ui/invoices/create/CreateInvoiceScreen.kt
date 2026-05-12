package com.ohmybattery.invoicing.ui.invoices.create

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmybattery.invoicing.core.money.Money
import com.ohmybattery.invoicing.data.local.entity.ProductEntity
import com.ohmybattery.invoicing.data.local.entity.PaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    onBack: () -> Unit,
    onIssued: (Long) -> Unit,
    vm: CreateInvoiceViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle facture") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
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
                onIssue = { vm.issue(onIssued) },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ClientCard(state, vm) }
            item { CatalogGrid(catalog, state, vm::addProduct, vm::decrement) }
            if (state.hasInstallLine) item { DeliveryCard(state, vm) }
            if (state.cart.isNotEmpty()) item { CartSummary(state) }
            if (state.cart.isNotEmpty()) item { CommentCard(state, vm) }
            state.error?.let { item { ErrorBanner(it) } }
        }
    }
}

@Composable
private fun ClientCard(state: CreateUiState, vm: CreateInvoiceViewModel) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text("Client", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.clientName,
                onValueChange = vm::onClientNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nom du client") },
                singleLine = true,
            )
            if (state.matchingClients.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                state.matchingClients.forEach { c ->
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
                Text("Coordonnées du service", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.clientPhone,
                onValueChange = vm::onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Téléphone *") },
                singleLine = true,
                isError = state.clientPhone.isBlank(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.clientEmail,
                onValueChange = vm::onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Email (pour envoi auto de la facture)") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.clientAddress,
                onValueChange = vm::onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Adresse complète *") },
                isError = state.clientAddress.isBlank(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Text("Véhicule (optionnel)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.vehicleModel,
                    onValueChange = vm::onVehicleModelChange,
                    modifier = Modifier.weight(1.5f),
                    placeholder = { Text("Modèle") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.vehicleRegistration,
                    onValueChange = vm::onVehicleRegistrationChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Immat.") },
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun CatalogGrid(
    catalog: List<ProductEntity>,
    state: CreateUiState,
    onAdd: (ProductEntity) -> Unit,
    onRemove: (ProductEntity) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.size(8.dp))
            Text("Catalogue", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(((catalog.size + 1) / 2 * 130).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false,
        ) {
            items(catalog, key = { it.id }) { b ->
                val qty = state.cart.firstOrNull { it.product.id == b.id }?.quantity ?: 0
                ProductTile(b, qty, onAdd, onRemove)
            }
        }
    }
}

@Composable
private fun ProductTile(
    b: ProductEntity,
    qty: Int,
    onAdd: (ProductEntity) -> Unit,
    onRemove: (ProductEntity) -> Unit,
) {
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
                    if (b.withInstall) "Avec service à domicile" else "Produit seul",
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
                    Money.formatEurPlain(b.priceTtcCents),
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
                            "$qty",
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
private fun CartSummary(state: CreateUiState) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Détail", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            state.cart.forEach { line ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${line.quantity} × ${line.product.label}", Modifier.weight(1f))
                    Text(Money.formatEurPlain(line.product.priceTtcCents * line.quantity))
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (state.totalVatCents != 0L) {
                TotalRow("Total H.T.", state.totalHtCents)
                TotalRow("TVA (20%)", state.totalVatCents)
                Spacer(Modifier.height(6.dp))
                TotalRow("Total TTC", state.totalTtcCents, big = true)
            } else {
                TotalRow("Total", state.totalTtcCents, big = true)
            }
        }
    }
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
            Money.formatEurPlain(cents),
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
                Text("Commentaire", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.comment,
                onValueChange = vm::onCommentChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Particularités, notes internes (optionnel)") },
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
) {
    Surface(tonalElevation = 6.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaymentMethod.CASH to "Espèces", PaymentMethod.CARD to "Carte", PaymentMethod.TRANSFER to "Virement")
                    .forEach { (m, label) ->
                        FilterChip(
                            selected = paymentMethod == m,
                            onClick = { onPaymentMethodChange(m) },
                            label = { Text(label) },
                        )
                    }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onIssue,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(56.dp),
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
                        "Encaisser • ${Money.formatEurPlain(totalTtc)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
