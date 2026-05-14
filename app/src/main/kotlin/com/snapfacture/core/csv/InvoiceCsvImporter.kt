package com.snapfacture.core.csv

import com.snapfacture.data.local.AppDatabase
import com.snapfacture.data.local.entity.ClientEntity
import com.snapfacture.data.local.entity.InvoiceEntity
import com.snapfacture.data.local.entity.InvoiceLineEntity
import com.snapfacture.data.local.entity.InvoiceStatus
import com.snapfacture.data.local.entity.PaymentMethod
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class ImportReport(
    val imported: Int,
    val skipped: Int,
    val maxImportedNumber: Int?,
    val errors: List<String>,
)

@Singleton
class InvoiceCsvImporter @Inject constructor(
    private val db: AppDatabase,
) {

    private val dateFr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).apply {
        timeZone = TimeZone.getTimeZone("Europe/Paris")
        isLenient = false
    }
    private val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).apply {
        timeZone = TimeZone.getTimeZone("Europe/Paris")
        isLenient = false
    }

    suspend fun runImport(reader: Reader): ImportReport {
        val rows = CsvParser.parse(reader)
        if (rows.isEmpty()) return ImportReport(0, 0, null, listOf("Fichier vide"))

        val header = rows.first().map { it.trim().lowercase(Locale.FRANCE) }
        val idx = Headers(header)
        val data = rows.drop(1)

        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        var maxNumber: Int? = null
        val clientCache = mutableMapOf<String, Long>()
        val companyAtImport = db.companyDao().get()

        for ((rowIndex, row) in data.withIndex()) {
            val lineNo = rowIndex + 2
            try {
                val type = idx.get(row, "type de pièce").lowercase(Locale.FRANCE)
                if (type.isNotBlank() && !type.startsWith("facture")) {
                    skipped++
                    continue
                }

                val numberStr = idx.get(row, "numéro")
                val number = numberStr.trim().toIntOrNull()
                if (number == null) {
                    errors += "Ligne $lineNo : numéro invalide « $numberStr »"
                    skipped++
                    continue
                }

                val clientName = idx.get(row, "client").trim().lines().first().trim()
                if (clientName.isBlank()) {
                    errors += "Ligne $lineNo : client vide"
                    skipped++
                    continue
                }

                val issueDate = parseFr(idx.get(row, "date d'émission"))
                if (issueDate == null) {
                    errors += "Ligne $lineNo : date d'émission invalide"
                    skipped++
                    continue
                }
                val dueDate = parseFr(idx.get(row, "date d´échéance"))
                    ?: parseFr(idx.get(row, "date d'échéance")) ?: issueDate
                val deliveryDate = parseFr(idx.get(row, "date de livraison"))

                val htCents = parseAmountCents(idx.get(row, "hors tva"))
                val vatCents = parseAmountCents(idx.get(row, "tva"))
                val totalCents = parseAmountCents(idx.get(row, "montant"))
                if (htCents == null || vatCents == null || totalCents == null) {
                    errors += "Ligne $lineNo : montants invalides"
                    skipped++
                    continue
                }
                val vatRatePermille = if (htCents > 0) Math.round((vatCents * 1000.0) / htCents).toInt() else 200

                val paymentLabel = idx.get(row, "mode de paiement").trim().lowercase(Locale.FRANCE)
                val paymentMethod = mapPayment(paymentLabel)
                val paymentDate = parseIso(idx.get(row, "date de règlement")) ?: issueDate
                val issuer = idx.get(row, "établi par").ifBlank { "Importé" }

                val country = idx.get(row, "pays").ifBlank { "France" }
                val postal = idx.get(row, "code postal").ifBlank { null }
                val city = idx.get(row, "ville").ifBlank { null }
                val street = idx.get(row, "rue").ifBlank { null }

                val clientId = clientCache.getOrPut(clientName.lowercase(Locale.FRANCE)) {
                    val existing = db.clientDao().search(clientName).firstOrNull { it.name.equals(clientName, ignoreCase = true) }
                    existing?.id ?: db.clientDao().insert(
                        ClientEntity(
                            name = clientName,
                            addressLine = street,
                            postalCode = postal,
                            city = city,
                        )
                    )
                }

                val invoiceId = db.invoiceDao().insertInvoice(
                    InvoiceEntity(
                        number = number,
                        clientId = clientId,
                        issueDate = issueDate,
                        dueDate = dueDate,
                        deliveryDate = deliveryDate,
                        totalHtCents = htCents,
                        totalVatCents = vatCents,
                        totalTtcCents = totalCents,
                        paymentMethod = paymentMethod,
                        paymentDate = paymentDate,
                        status = InvoiceStatus.PAID,
                        issuerName = issuer,
                        companyNameAtIssue = companyAtImport?.name,
                        companySirenAtIssue = companyAtImport?.siren,
                        companyAddressAtIssue = companyAtImport?.addressLine,
                        companyPostalAtIssue = companyAtImport?.postalCode,
                        companyCityAtIssue = companyAtImport?.city,
                        companyVatNumberAtIssue = companyAtImport?.vatNumber,
                        companyManagerAtIssue = companyAtImport?.managerName,
                        taxOptedOutAtIssue = vatCents == 0L,
                    )
                )
                db.invoiceDao().insertLines(
                    listOf(
                        InvoiceLineEntity(
                            invoiceId = invoiceId,
                            description = "Facture importée (archive)",
                            extraNote = null,
                            quantity = 1,
                            unitPriceHtCents = htCents,
                            vatRatePermille = vatRatePermille,
                            lineHtCents = htCents,
                            lineVatCents = vatCents,
                            lineTtcCents = totalCents,
                            position = 0,
                        )
                    )
                )
                imported++
                maxNumber = maxOf(maxNumber ?: 0, number)
            } catch (t: Throwable) {
                errors += "Ligne $lineNo : ${t.message ?: t::class.simpleName}"
                skipped++
            }
        }

        maxNumber?.let { max ->
            val company = db.companyDao().get()
            if (company != null && company.nextInvoiceNumber <= max) {
                db.companyDao().upsert(company.copy(nextInvoiceNumber = max + 1))
            }
        }

        return ImportReport(imported, skipped, maxNumber, errors)
    }

    private fun parseFr(s: String): Long? =
        s.trim().takeIf { it.isNotBlank() }?.let { runCatching { dateFr.parse(it)!!.time }.getOrNull() }

    private fun parseIso(s: String): Long? =
        s.trim().takeIf { it.isNotBlank() }?.let { runCatching { dateIso.parse(it)!!.time }.getOrNull() }

    private fun parseAmountCents(raw: String): Long? {
        val cleaned = raw.trim().replace(" ", "").replace(",", ".")
        if (cleaned.isBlank()) return null
        val d = cleaned.toDoubleOrNull() ?: return null
        return Math.round(d * 100.0)
    }

    private fun mapPayment(label: String): PaymentMethod = when {
        "espe" in label || "cash" in label -> PaymentMethod.CASH
        "vire" in label || "transfer" in label -> PaymentMethod.TRANSFER
        "carte" in label || "card" in label || "cb" in label -> PaymentMethod.CARD
        "chèq" in label || "cheq" in label -> PaymentMethod.CHECK
        else -> PaymentMethod.OTHER
    }

    private class Headers(headers: List<String>) {
        private val map: Map<String, Int> = headers.mapIndexed { i, h -> normalize(h) to i }.toMap()
        fun get(row: List<String>, label: String): String {
            val i = map[normalize(label)] ?: return ""
            return if (i < row.size) row[i] else ""
        }
        private fun normalize(s: String): String =
            s.trim().lowercase(Locale.FRANCE)
                .replace('´', '\'')
                .replace('`', '\'')
    }
}
