package com.snapfacture.ui.catalog

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfacture.R
import com.snapfacture.core.country.LocalCountryProfile
import com.snapfacture.data.local.entity.ProductEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onBack: () -> Unit,
    vm: CatalogViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CatalogDraft?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog_title)) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = CatalogDraft() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.catalog_add)) },
            )
        },
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.catalog_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { b ->
                    ProductListItem(
                        product = b,
                        onEdit = { editing = CatalogDraft.from(b) },
                        onToggleActive = { vm.toggleActive(b) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    editing?.let { draft ->
        EditSheet(
            initial = draft,
            onDismiss = { editing = null },
            onSave = { newDraft -> vm.save(newDraft) { editing = null } },
        )
    }
}

@Composable
private fun ProductListItem(
    product: ProductEntity,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
) {
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (product.active) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (product.withInstall) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            stringResource(R.string.catalog_with_service),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    product.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (product.active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    LocalCountryProfile.current.formatMoney(product.priceTtcCents),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (product.active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                if (!product.active) {
                    Text(
                        stringResource(R.string.catalog_inactive),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Switch(checked = product.active, onCheckedChange = { onToggleActive() })
            Spacer(Modifier.size(4.dp))
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    initial: CatalogDraft,
    onDismiss: () -> Unit,
    onSave: (CatalogDraft) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(initial) }
    val isNew = initial.id == 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(if (isNew) R.string.catalog_edit_new_title else R.string.catalog_edit_existing_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it) },
                label = { Text(stringResource(R.string.catalog_edit_label_label)) },
                placeholder = { Text(stringResource(R.string.catalog_edit_label_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = draft.priceTtcEuros,
                onValueChange = { draft = draft.copy(priceTtcEuros = it) },
                label = { Text(stringResource(R.string.catalog_edit_price_label)) },
                placeholder = { Text(stringResource(R.string.catalog_edit_price_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.catalog_edit_service_label), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.catalog_edit_service_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = draft.withInstall,
                    onCheckedChange = { draft = draft.copy(withInstall = it) },
                )
            }

            if (draft.withInstall) {
                OutlinedTextField(
                    value = draft.serviceNote,
                    onValueChange = { draft = draft.copy(serviceNote = it) },
                    label = { Text(stringResource(R.string.catalog_edit_service_note_label)) },
                    placeholder = { Text(stringResource(R.string.catalog_edit_service_note_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }

            if (!isNew) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.catalog_edit_active_label), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = draft.active,
                        onCheckedChange = { draft = draft.copy(active = it) },
                    )
                }
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { onDismiss() } },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = { onSave(draft) },
                    enabled = draft.isValid,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(stringResource(if (isNew) R.string.catalog_edit_save_new else R.string.catalog_edit_save_existing))
                }
            }
        }
    }
}
