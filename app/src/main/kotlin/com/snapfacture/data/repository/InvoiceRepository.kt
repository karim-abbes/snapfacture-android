package com.snapfacture.data.repository

import androidx.room.withTransaction
import com.snapfacture.core.backup.BackupManager
import com.snapfacture.core.money.Money
import com.snapfacture.core.money.Quantity
import com.snapfacture.data.local.AppDatabase
import com.snapfacture.data.local.dao.AuditDao
import com.snapfacture.data.local.dao.CompanyDao
import com.snapfacture.data.local.dao.InvoiceDao
import com.snapfacture.data.local.entity.AuditLogEntity
import com.snapfacture.data.local.entity.InvoiceEntity
import com.snapfacture.data.local.entity.InvoiceLineEntity
import com.snapfacture.data.local.entity.InvoiceStatus
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.local.relation.InvoiceWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DraftLine(
    val description: String,
    val extraNote: String?,
    val quantityMilliUnits: Long,
    val unitPriceTtcCents: Long,
    val vatRatePermille: Int = 200,
)

data class IssueInvoiceInput(
    val clientId: Long,
    val lines: List<DraftLine>,
    val paymentMethod: PaymentMethod,
    val issueDateMillis: Long,
    val deliveryDateMillis: Long?,
    val issuerName: String,
    val comment: String? = null,
    val taxOptedOut: Boolean = false,
    val clientSiret: String? = null,
)

@Singleton
class InvoiceRepository @Inject constructor(
    private val db: AppDatabase,
    private val invoiceDao: InvoiceDao,
    private val companyDao: CompanyDao,
    private val auditDao: AuditDao,
    private val backupManager: BackupManager,
    private val countryPrefs: com.snapfacture.data.preferences.CountryPreferences,
) {

    fun observeAll(): Flow<List<InvoiceWithDetails>> = invoiceDao.observeAllWithDetails()

    fun observeRevenueSince(since: Long): Flow<Long?> = invoiceDao.observeRevenueSince(since)
    fun observeCountSince(since: Long): Flow<Int> = invoiceDao.observeCountSince(since)

    suspend fun get(id: Long): InvoiceWithDetails? = invoiceDao.getWithDetails(id)

    suspend fun findCreditFor(originalId: Long): InvoiceEntity? =
        invoiceDao.findCreditFor(originalId)

    suspend fun issue(input: IssueInvoiceInput): Long = db.withTransaction {
        require(input.lines.isNotEmpty()) { "Aucune ligne sur la facture" }

        val computedLines = input.lines.map { l ->
            val ttcUnit = l.unitPriceTtcCents
            val effectiveRate = if (input.taxOptedOut) 0 else l.vatRatePermille
            val htUnit = if (input.taxOptedOut) ttcUnit else Money.htFromTtc(ttcUnit, effectiveRate)
            val (lineHt, lineVat, lineTtc) = Money.lineAmounts(ttcUnit, l.quantityMilliUnits, effectiveRate)
            ComputedLine(
                description = l.description,
                extraNote = l.extraNote,
                quantityMilliUnits = l.quantityMilliUnits,
                unitHtCents = htUnit,
                vatRatePermille = effectiveRate,
                lineHt = lineHt,
                lineVat = lineVat,
                lineTtc = lineTtc,
            )
        }

        val totalHt = computedLines.sumOf { it.lineHt }
        val totalVat = computedLines.sumOf { it.lineVat }
        val totalTtc = computedLines.sumOf { it.lineTtc }

        val number = companyDao.peekNextInvoiceNumber()
        companyDao.bumpInvoiceNumber()
        val company = companyDao.get()
        val activeCurrency = countryPrefs.flow.first().profile.currency.currencyCode

        val invoice = InvoiceEntity(
            number = number,
            clientId = input.clientId,
            issueDate = input.issueDateMillis,
            dueDate = input.issueDateMillis,
            deliveryDate = input.deliveryDateMillis,
            totalHtCents = totalHt,
            totalVatCents = totalVat,
            totalTtcCents = totalTtc,
            currency = activeCurrency,
            paymentMethod = input.paymentMethod,
            paymentDate = input.issueDateMillis,
            status = InvoiceStatus.PAID,
            issuerName = input.issuerName,
            comment = input.comment?.takeIf { it.isNotBlank() },
            companyNameAtIssue = company?.name,
            companySirenAtIssue = company?.siren,
            companyAddressAtIssue = company?.addressLine,
            companyPostalAtIssue = company?.postalCode,
            companyCityAtIssue = company?.city,
            companyVatNumberAtIssue = company?.vatNumber,
            companyManagerAtIssue = company?.managerName,
            taxOptedOutAtIssue = input.taxOptedOut,
            clientSiretAtIssue = input.clientSiret?.filter { it.isDigit() }?.takeIf { it.isNotBlank() },
        )
        val invoiceId = invoiceDao.insertInvoice(invoice)

        val lineRows = computedLines.mapIndexed { idx, c ->
            InvoiceLineEntity(
                invoiceId = invoiceId,
                description = c.description,
                extraNote = c.extraNote,
                quantityMilliUnits = c.quantityMilliUnits,
                unitPriceHtCents = c.unitHtCents,
                vatRatePermille = c.vatRatePermille,
                lineHtCents = c.lineHt,
                lineVatCents = c.lineVat,
                lineTtcCents = c.lineTtc,
                position = idx,
            )
        }
        invoiceDao.insertLines(lineRows)

        appendAudit(invoiceId, EVENT_INVOICE_ISSUED, auditPayload(invoice, lineRows))

        invoiceId
    }.also { backupManager.triggerIfEnabled() }

    suspend fun attachPdf(invoiceId: Long, path: String) {
        invoiceDao.setPdfPath(invoiceId, path)
        appendAudit(invoiceId, EVENT_PDF_GENERATED, path)
    }

    // The whole credit note must be atomic: a crash between the number bump and
    // the insert would burn an invoice number, breaking the gapless sequence
    // required by art. 242 nonies A CGI.
    suspend fun issueCredit(originalId: Long, reason: String?): Long = db.withTransaction {
        val orig = invoiceDao.getWithDetails(originalId)
            ?: error("Facture introuvable")
        require(orig.invoice.type == InvoiceType.INVOICE) {
            "Impossible d'émettre un avoir sur un avoir"
        }

        val number = companyDao.peekNextInvoiceNumber()
        companyDao.bumpInvoiceNumber()
        val now = System.currentTimeMillis()
        val company = companyDao.get()

        val credit = InvoiceEntity(
            number = number,
            clientId = orig.invoice.clientId,
            issueDate = now,
            dueDate = now,
            deliveryDate = null,
            totalHtCents = -orig.invoice.totalHtCents,
            totalVatCents = -orig.invoice.totalVatCents,
            totalTtcCents = -orig.invoice.totalTtcCents,
            currency = orig.invoice.currency,
            paymentMethod = orig.invoice.paymentMethod,
            paymentDate = now,
            paymentNote = reason?.takeIf { it.isNotBlank() },
            status = InvoiceStatus.PAID,
            issuerName = orig.invoice.issuerName,
            type = InvoiceType.CREDIT_NOTE,
            linkedInvoiceId = originalId,
            companyNameAtIssue = company?.name,
            companySirenAtIssue = company?.siren,
            companyAddressAtIssue = company?.addressLine,
            companyPostalAtIssue = company?.postalCode,
            companyCityAtIssue = company?.city,
            companyVatNumberAtIssue = company?.vatNumber,
            companyManagerAtIssue = company?.managerName,
            taxOptedOutAtIssue = orig.invoice.taxOptedOutAtIssue ?: (orig.invoice.totalVatCents == 0L),
            clientSiretAtIssue = orig.invoice.clientSiretAtIssue,
        )
        val newId = invoiceDao.insertInvoice(credit)

        val newLines = orig.lines.map { l ->
            InvoiceLineEntity(
                invoiceId = newId,
                description = l.description,
                extraNote = l.extraNote,
                quantityMilliUnits = l.quantityMilliUnits,
                unitPriceHtCents = -l.unitPriceHtCents,
                vatRatePermille = l.vatRatePermille,
                lineHtCents = -l.lineHtCents,
                lineVatCents = -l.lineVatCents,
                lineTtcCents = -l.lineTtcCents,
                position = l.position,
            )
        }
        invoiceDao.insertLines(newLines)

        appendAudit(newId, EVENT_CREDIT_ISSUED, auditPayload(credit, newLines))
        newId
    }.also { backupManager.triggerIfEnabled() }

    private suspend fun appendAudit(invoiceId: Long?, event: String, payload: String) {
        // Hash chain is a French anti-fraud requirement (loi anti-fraude TVA 2018).
        // Skip in countries that don't impose it.
        if (!countryPrefs.flow.first().profile.antiFraudHashChain) return
        val prev = auditDao.lastHash()
        val timestamp = System.currentTimeMillis()
        auditDao.append(
            AuditLogEntity(
                invoiceId = invoiceId,
                event = event,
                payload = payload,
                payloadHash = chainHash(prev, event, payload, timestamp),
                previousHash = prev,
                timestamp = timestamp,
            )
        )
    }

    /**
     * Walks the whole audit log and re-derives every hash from the stored
     * columns, then cross-checks each issue event's payload against the
     * invoice as it exists in the database today. Any manual edit of either
     * the log or an issued invoice breaks one of the two checks.
     */
    suspend fun verifyAuditChain(): AuditVerification {
        val entries = auditDao.all()
        var prev: String? = null
        var verified = 0
        var legacy = 0
        var brokenAtEntry: Long? = null
        var tamperedInvoiceNumber: Int? = null
        for (e in entries) {
            if (e.payload.isEmpty()) {
                // Pre-v2 row: its hash included a timestamp that was never
                // stored, so it cannot be recomputed. Counted, not verified.
                legacy++
                prev = e.payloadHash
                continue
            }
            if (brokenAtEntry == null &&
                (e.previousHash != prev || e.payloadHash != chainHash(prev, e.event, e.payload, e.timestamp))
            ) {
                brokenAtEntry = e.id
            }
            prev = e.payloadHash
            if (tamperedInvoiceNumber == null && e.invoiceId != null &&
                (e.event == EVENT_INVOICE_ISSUED || e.event == EVENT_CREDIT_ISSUED)
            ) {
                val current = invoiceDao.getWithDetails(e.invoiceId)
                if (current == null || auditPayload(current.invoice, current.lines) != e.payload) {
                    tamperedInvoiceNumber = current?.invoice?.number ?: -1
                }
            }
            verified++
        }
        return AuditVerification(
            totalEntries = entries.size,
            verifiedEntries = verified,
            legacyEntries = legacy,
            brokenAtEntryId = brokenAtEntry,
            tamperedInvoiceNumber = tamperedInvoiceNumber,
        )
    }

    private data class ComputedLine(
        val description: String,
        val extraNote: String?,
        val quantityMilliUnits: Long,
        val unitHtCents: Long,
        val vatRatePermille: Int,
        val lineHt: Long,
        val lineVat: Long,
        val lineTtc: Long,
    )

    companion object {
        const val EVENT_INVOICE_ISSUED = "INVOICE_ISSUED"
        const val EVENT_CREDIT_ISSUED = "CREDIT_NOTE_ISSUED"
        const val EVENT_PDF_GENERATED = "PDF_GENERATED"

        fun chainHash(prev: String?, event: String, payload: String, timestamp: Long): String {
            val raw = (prev ?: "") + "|" + event + "|" + payload + "|" + timestamp
            return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }

        /**
         * Deterministic serialization of everything that makes the invoice a
         * legal document. '|' is the field separator, so it is stripped from
         * free-text fields before hashing.
         */
        fun auditPayload(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>): String = buildString {
            append("n=").append(invoice.number)
            append("|type=").append(invoice.type)
            append("|client=").append(invoice.clientId)
            append("|issued=").append(invoice.issueDate)
            append("|ht=").append(invoice.totalHtCents)
            append("|vat=").append(invoice.totalVatCents)
            append("|ttc=").append(invoice.totalTtcCents)
            append("|cur=").append(invoice.currency)
            append("|pay=").append(invoice.paymentMethod)
            append("|franchise=").append(invoice.taxOptedOutAtIssue)
            append("|linked=").append(invoice.linkedInvoiceId)
            lines.sortedBy { it.position }.forEach { l ->
                append("|L").append(l.position)
                    .append(":").append(l.description.replace('|', '/'))
                    // Canonical form ("2", not "2000"): pre-v4 payloads wrote the
                    // quantity as a plain Int, and they must still verify after
                    // the ×1000 milli-unit migration.
                    .append(",").append(Quantity.canonical(l.quantityMilliUnits))
                    .append(",").append(l.vatRatePermille)
                    .append(",").append(l.lineHtCents)
                    .append(",").append(l.lineVatCents)
                    .append(",").append(l.lineTtcCents)
            }
        }
    }
}

data class AuditVerification(
    val totalEntries: Int,
    val verifiedEntries: Int,
    val legacyEntries: Int,
    val brokenAtEntryId: Long?,
    val tamperedInvoiceNumber: Int?,
) {
    val ok: Boolean get() = brokenAtEntryId == null && tamperedInvoiceNumber == null
}
