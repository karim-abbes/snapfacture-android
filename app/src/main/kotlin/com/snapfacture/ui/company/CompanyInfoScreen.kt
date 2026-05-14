package com.snapfacture.ui.company

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfacture.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyInfoScreen(
    onBack: () -> Unit,
    vm: CompanyInfoViewModel = hiltViewModel(),
) {
    val company by vm.company.collectAsStateWithLifecycle()
    val country by vm.country.collectAsStateWithLifecycle()
    val profile = country?.profile
    val isUs = profile?.code == "US"

    var name by remember { mutableStateOf("") }
    var siren by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var postal by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var manager by remember { mutableStateOf("") }
    var nextNumber by remember { mutableStateOf("") }
    var defaultTaxPct by remember { mutableStateOf("") }

    LaunchedEffect(company) {
        company?.let {
            name = it.name; siren = it.siren
            address = it.addressLine; postal = it.postalCode; city = it.city
            phone = it.phone; email = it.email; website = it.website
            manager = it.managerName
            nextNumber = it.nextInvoiceNumber.toString()
            defaultTaxPct = if (it.defaultTaxPermille > 0)
                "%.2f".format(it.defaultTaxPermille / 10.0).trimEnd('0').trimEnd('.', ',')
            else ""
        }
    }

    val canSave by remember(name, nextNumber) {
        derivedStateOf { name.isNotBlank() && nextNumber.toIntOrNull() != null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.company_title)) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.company_name)) }, modifier = Modifier.fillMaxWidth()) }
            item {
                val legalIdLabel = profile?.legalIdLabel ?: stringResource(R.string.company_legal_id)
                OutlinedTextField(value = siren, onValueChange = { siren = it }, label = { Text(legalIdLabel) }, modifier = Modifier.fillMaxWidth())
            }
            item { OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(stringResource(R.string.company_address)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = postal, onValueChange = { postal = it }, label = { Text(stringResource(R.string.company_postal)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.company_city)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.company_phone)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.company_email)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text(stringResource(R.string.company_website)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = manager, onValueChange = { manager = it }, label = { Text(stringResource(R.string.company_manager)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = nextNumber, onValueChange = { nextNumber = it }, label = { Text(stringResource(R.string.company_next_invoice_number)) }, modifier = Modifier.fillMaxWidth()) }

            if (isUs) {
                item {
                    OutlinedTextField(
                        value = defaultTaxPct,
                        onValueChange = { defaultTaxPct = it.replace(',', '.') },
                        label = { Text(stringResource(R.string.company_default_sales_tax)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            country?.takeIf { it.profile.code == "FR" }?.let { settings ->
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.company_tax_regime_section, settings.profile.taxLabel),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.company_franchise_label, settings.profile.taxLabel),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.company_franchise_subtitle_fr),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.taxOptedOut,
                                onCheckedChange = vm::setTaxOptedOut,
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val current = company ?: return@Button
                        val parsedPermille = defaultTaxPct
                            .replace(',', '.')
                            .toDoubleOrNull()
                            ?.let { Math.round(it * 10.0).toInt() }
                            ?: 0
                        vm.save(
                            current.copy(
                                name = name, siren = siren,
                                addressLine = address, postalCode = postal, city = city,
                                phone = phone, email = email, website = website,
                                managerName = manager,
                                nextInvoiceNumber = nextNumber.toIntOrNull() ?: current.nextInvoiceNumber,
                                defaultTaxPermille = if (isUs) parsedPermille else current.defaultTaxPermille,
                            )
                        )
                        onBack()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
