package com.snapfacture.ui.quotes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfacture.R
import com.snapfacture.core.country.LocalCountryProfile

private enum class QuoteStatus { NONE, INVOICED, EXPIRED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteListScreen(
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    vm: QuoteListViewModel = hiltViewModel(),
) {
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val profile = LocalCountryProfile.current
    // Read once per composition: expiry only changes across days, no live tick needed.
    val now = remember { System.currentTimeMillis() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quotes_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.quote_list_new)) },
            )
        },
    ) { pad ->
        if (quotes.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.quotes_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(quotes, key = { it.quote.id }) { q ->
                    val status = when {
                        q.quote.convertedInvoiceId != null -> QuoteStatus.INVOICED
                        q.quote.validUntil < now -> QuoteStatus.EXPIRED
                        else -> QuoteStatus.NONE
                    }
                    Card(onClick = { onOpen(q.quote.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.quote_list_n, q.quote.number),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    if (status != QuoteStatus.NONE) StatusPill(status)
                                }
                                Text(
                                    q.client.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    profile.formatDate(q.quote.issueDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                profile.formatMoney(q.quote.totalTtcCents),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: QuoteStatus) {
    val (label, color) = when (status) {
        QuoteStatus.INVOICED -> stringResource(R.string.quote_converted_marker) to MaterialTheme.colorScheme.primary
        QuoteStatus.EXPIRED -> stringResource(R.string.quote_status_expired) to MaterialTheme.colorScheme.error
        QuoteStatus.NONE -> return
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        contentColor = color,
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
