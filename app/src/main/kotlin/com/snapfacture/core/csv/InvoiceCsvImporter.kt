package com.snapfacture.core.csv

import com.snapfacture.data.local.AppDatabase
import com.snapfacture.data.local.entity.ClientEntity
import com.snapfacture.data.local.entity.InvoiceEntity
import com.snapfacture.data.local.entity.InvoiceLineEntity
import com.snapfacture.data.local.entity.InvoiceStatus
import com.snapfacture.data.local.entity.PaymentMethod
import java.io.Reader
import java.io.StringReader
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
    private val dateUs = SimpleDateFormat("MM/dd/yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Europe/Paris")
        isLenient = false
    }
    private val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).apply {
        timeZone = TimeZone.getTimeZone("Europe/Paris")
        isLenient = false
    }

    /** One-shot path (tests, legacy): auto-detect separator and mapping. */
    suspend fun runImport(reader: Reader): ImportReport {
        val text = reader.readText()
        val separator = CsvParser.detectSeparator(text.lineSequence().firstOrNull().orEmpty())
        val rows = CsvParser.parse(StringReader(text), separator)
        if (rows.isEmpty()) return ImportReport(0, 0, null, listOf("Fichier vide"))
        return runImport(rows, ImportField.suggestMapping(rows.first()))
    }

    suspend fun runImport(rows: List<List<String>>, mapping: Map<ImportField, Int>): ImportReport {
        if (rows.size < 2) return ImportReport(0, 0, null, listOf("Fichier vide"))
        val data = rows.drop(1)

        fun get(row: List<String>, field: ImportField): String {
            val i = mapping[field] ?: return ""
            return if (i in row.indices) row[i].trim() else ""
        }

        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        var maxNumber: Int? = null
        val clientCache = mutableMapOf<String, Long>()
        val companyAtImport = db.companyDao().get()

        for ((rowIndex, row) in data.withIndex()) {
            val lineNo = rowIndex + 2
            try {
                val type = get(row, ImportField.TYPE).lowercase(Locale.FRANCE)
                if (type.isNotBlank() && !type.startsWith("facture") && !type.startsWith("invoice")) {
                    skipped++
                    continue
                }

                val numberStr = get(row, ImportField.NUMBER)
                val number = numberStr.filter { it.isDigit() }.toIntOrNull()
                if (number == null) {
                    errors += "Ligne $lineNo : numéro invalide « $numberStr »"
                    skipped++
                    continue
                }

                val clientName = get(row, ImportField.CLIENT).lines().first().trim()
                if (clientName.isBlank()) {
                    errors += "Ligne $lineNo : client vide"
                    skipped++
                    continue
                }

                val issueDate = parseDate(get(row, ImportField.ISSUE_DATE))
                if (issueDate == null) {
                    errors += "Ligne $lineNo : date d'émission invalide"
                    skipped++
                    continue
                }
                val dueDate = parseDate(get(row, ImportField.DUE_DATE)) ?: issueDate
                val deliveryDate = parseDate(get(row, ImportField.DELIVERY_DATE))

                val totalCents = parseAmountCents(get(row, ImportField.TOTAL))
                if (totalCents == null) {
                    errors += "Ligne $lineNo : montant invalide"
                    skipped++
                    continue
                }
                val htRaw = parseAmountCents(get(row, ImportField.HT))
                val vatRaw = parseAmountCents(get(row, ImportField.VAT))
                val (htCents, vatCents) = when {
                    htRaw != null && vatRaw != null -> htRaw to vatRaw
                    htRaw != null -> htRaw to (totalCents - htRaw)
                    vatRaw != null -> (totalCents - vatRaw) to vatRaw
                    else -> totalCents to 0L
                }
                val vatRateBp =
                    if (htCents > 0) Math.round((vatCents * 10_000.0) / htCents).toInt() else 2_000

                val paymentMethod = mapPayment(get(row, ImportField.PAYMENT_METHOD).lowercase(Locale.FRANCE))
                val paymentDate = parseDate(get(row, ImportField.PAYMENT_DATE)) ?: issueDate
                val issuer = get(row, ImportField.ISSUER).ifBlank { "Importé" }

                val postal = get(row, ImportField.POSTAL).ifBlank { null }
                val city = get(row, ImportField.CITY).ifBlank { null }
                val street = get(row, ImportField.STREET).ifBlank { null }

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
                            quantityMilliUnits = 1_000L,
                            unitPriceHtCents = htCents,
                            vatRateBp = vatRateBp,
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

    // dd/MM/yyyy first (FR files), then ISO, then MM/dd/yyyy as a US
    // fallback — an ambiguous date like 04/07/2026 resolves as day/month.
    private fun parseDate(s: String): Long? {
        val t = s.trim().takeIf { it.isNotBlank() } ?: return null
        for (fmt in listOf(dateFr, dateIso, dateUs)) {
            runCatching { return fmt.parse(t)!!.time }
        }
        return null
    }

    // Accepts "1 234,56", "1.234,56", "1,234.56", "1234.56", "1 234,56 €".
    // When both separators appear, the right-most one is the decimal mark.
    private fun parseAmountCents(raw: String): Long? {
        val cleaned = raw.replace(Regex("[^0-9,.\\-]"), "")
        if (cleaned.isBlank()) return null
        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) cleaned.replace(".", "").replace(',', '.')
                else cleaned.replace(",", "")
            lastComma >= 0 ->
                if (cleaned.count { it == ',' } == 1) cleaned.replace(',', '.')
                else cleaned.replace(",", "")
            else ->
                if (cleaned.count { it == '.' } <= 1) cleaned
                else cleaned.replace(".", "")
        }
        val d = normalized.toDoubleOrNull() ?: return null
        return Math.round(d * 100.0)
    }

    private fun mapPayment(label: String): PaymentMethod = when {
        "espe" in label || "cash" in label -> PaymentMethod.CASH
        "vire" in label || "transfer" in label -> PaymentMethod.TRANSFER
        "carte" in label || "card" in label || "cb" in label -> PaymentMethod.CARD
        "chèq" in label || "cheq" in label || "check" in label -> PaymentMethod.CHECK
        else -> PaymentMethod.OTHER
    }
}
