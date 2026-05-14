package com.snapfacture.core.csv

import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.local.relation.InvoiceWithDetails
import com.snapfacture.data.repository.InvoiceRepository
import kotlinx.coroutines.flow.first
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoiceCsvExporter @Inject constructor(
    private val invoiceRepo: InvoiceRepository,
) {

    private val dateFr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).apply {
        timeZone = TimeZone.getTimeZone("Europe/Paris")
    }
    private val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).apply {
        timeZone = TimeZone.getTimeZone("Europe/Paris")
    }

    suspend fun exportAll(out: Writer): Int {
        val writer = CsvWriter(out)
        writer.writeRow(HEADER)
        val invoices = invoiceRepo.observeAll().first()
        val sorted = invoices.sortedBy { it.invoice.number }
        for (inv in sorted) {
            val client = inv.client
            writer.writeRow(rowFor(inv, client.addressLine, client.postalCode, client.city))
        }
        return sorted.size
    }

    private fun rowFor(
        inv: InvoiceWithDetails,
        street: String?,
        postal: String?,
        city: String?,
    ): List<String> = listOf(
        "Facture",
        inv.invoice.number.toString(),
        "",                                       // Référence
        "",                                       // Commande N°
        inv.client.name,
        "",                                       // # n° TVA
        street.orEmpty(),
        "",                                       // Étage, appartement
        city.orEmpty(),
        postal.orEmpty(),
        "FR",
        "France",
        "",                                       // Province/Etat
        dateFr.format(Date(inv.invoice.issueDate)),
        dateFr.format(Date(inv.invoice.dueDate)),
        inv.invoice.deliveryDate?.let { dateFr.format(Date(it)) }.orEmpty(),
        formatAmount(inv.invoice.totalHtCents),
        formatAmount(inv.invoice.totalVatCents),
        formatAmount(inv.invoice.discountCents),
        formatAmount(inv.invoice.totalTtcCents),
        inv.invoice.currency,
        inv.invoice.paymentDate?.let { dateIso.format(Date(it)) }.orEmpty(),
        formatAmountComma(inv.invoice.totalTtcCents),
        labelFor(inv.invoice.paymentMethod),
        inv.invoice.paymentReference.orEmpty(),
        inv.invoice.paymentNote.orEmpty(),
        inv.invoice.issuerName,
    )

    private fun formatAmount(cents: Long): String =
        String.format(Locale.US, "%.2f", cents / 100.0)

    private fun formatAmountComma(cents: Long): String =
        String.format(Locale.FRANCE, "%.2f", cents / 100.0)

    private fun labelFor(m: PaymentMethod): String = when (m) {
        PaymentMethod.CASH -> "Especes"
        PaymentMethod.TRANSFER -> "Virement"
        PaymentMethod.CARD -> "Carte"
        PaymentMethod.CHECK -> "Cheque"
        PaymentMethod.OTHER -> "Autre"
    }

    companion object {
        private val HEADER = listOf(
            "Type de pièce", "Numéro", "Référence", "Commande N°", "Client",
            "# n° TVA", "Rue", "Étage, appartement", "Ville", "Code postal",
            "Code de pays", "Pays", "Province/Etat",
            "Date d'émission", "Date d´échéance", "Date de livraison",
            "Hors TVA", "TVA", "Remise", "Montant", "Devise",
            "Date de règlement", "Montant payé", "Mode de paiement",
            "N° de référence du paiement", "Note de paiement", "Établi par",
        )
    }
}
