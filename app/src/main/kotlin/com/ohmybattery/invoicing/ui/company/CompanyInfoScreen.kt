package com.ohmybattery.invoicing.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyInfoScreen(
    onBack: () -> Unit,
    vm: CompanyInfoViewModel = hiltViewModel(),
) {
    val company by vm.company.collectAsStateWithLifecycle()
    val country by vm.country.collectAsStateWithLifecycle()

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

    LaunchedEffect(company) {
        company?.let {
            name = it.name; siren = it.siren
            address = it.addressLine; postal = it.postalCode; city = it.city
            phone = it.phone; email = it.email; website = it.website
            manager = it.managerName
            nextNumber = it.nextInvoiceNumber.toString()
        }
    }

    val canSave by remember(name, nextNumber) {
        derivedStateOf { name.isNotBlank() && nextNumber.toIntOrNull() != null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entreprise") },
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
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Raison sociale") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = siren, onValueChange = { siren = it }, label = { Text("SIREN") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = postal, onValueChange = { postal = it }, label = { Text("Code postal") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Ville") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text("Site web") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = manager, onValueChange = { manager = it }, label = { Text("Gérant") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = nextNumber, onValueChange = { nextNumber = it }, label = { Text("Prochain n° de facture") }, modifier = Modifier.fillMaxWidth()) }

            country?.let { settings ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Régime de ${settings.profile.taxLabel}", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Franchise de ${settings.profile.taxLabel}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    if (settings.profile.code == "FR")
                                        "Auto-entrepreneur sous le seuil — la facture affichera « TVA non applicable, art. 293 B du CGI ». Désactivez si vous facturez avec TVA."
                                    else
                                        "Cette entreprise n'applique pas de taxe sur ses ventes.",
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
                        vm.save(
                            current.copy(
                                name = name, siren = siren,
                                addressLine = address, postalCode = postal, city = city,
                                phone = phone, email = email, website = website,
                                managerName = manager,
                                nextInvoiceNumber = nextNumber.toIntOrNull() ?: current.nextInvoiceNumber,
                            )
                        )
                        onBack()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enregistrer") }
            }
        }
    }
}
