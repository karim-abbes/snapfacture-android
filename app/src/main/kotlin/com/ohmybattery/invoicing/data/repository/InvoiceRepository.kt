package com.ohmybattery.invoicing.data.repository

import androidx.room.withTransaction
import com.ohmybattery.invoicing.core.backup.BackupManager
import com.ohmybattery.invoicing.core.money.Money
import com.ohmybattery.invoicing.data.local.AppDatabase
import com.ohmybattery.invoicing.data.local.dao.AuditDao
import com.ohmybattery.invoicing.data.local.dao.CompanyDao
import com.ohmybattery.invoicing.data.local.dao.InvoiceDao
import com.ohmybattery.invoicing.data.local.entity.AuditLogEntity
import com.ohmybattery.invoicing.data.local.entity.InvoiceEntity
import com.ohmybattery.invoicing.data.local.entity.InvoiceLineEntity
import com.ohmybattery.invoicing.data.local.entity.InvoiceStatus
import com.ohmybattery.invoicing.data.local.entity.InvoiceType
import com.ohmybattery.invoicing.data.local.entity.PaymentMethod
import com.ohmybattery.invoicing.data.local.relation.InvoiceWithDetails
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DraftLine(
    val description: String,
    val extraNote: String?,
    val quantity: Int,
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
    val vehicleModel: String? = null,
    val vehicleRegistration: String? = null,
)

@Singleton
class InvoiceRepository @Inject constructor(
    private val db: AppDatabase,
    private val invoiceDao: InvoiceDao,
    private val companyDao: CompanyDao,
    private val auditDao: AuditDao,
    private val backupManager: BackupManager,
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
            val htUnit = Money.htFromTtc(ttcUnit, l.vatRatePermille)
            val lineTtc = ttcUnit * l.quantity
            val lineHt = htUnit * l.quantity
            val lineVat = lineTtc - lineHt
            ComputedLine(
                description = l.description,
                extraNote = l.extraNote,
                quantity = l.quantity,
                unitHtCents = htUnit,
                vatRatePermille = l.vatRatePermille,
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

        val invoice = InvoiceEntity(
            number = number,
            clientId = input.clientId,
            issueDate = input.issueDateMillis,
            dueDate = input.issueDateMillis,
            deliveryDate = input.deliveryDateMillis,
            totalHtCents = totalHt,
            totalVatCents = totalVat,
            totalTtcCents = totalTtc,
            paymentMethod = input.paymentMethod,
            paymentDate = input.issueDateMillis,
            status = InvoiceStatus.PAID,
            issuerName = input.issuerName,
            vehicleModel = input.vehicleModel?.takeIf { it.isNotBlank() },
            vehicleRegistration = input.vehicleRegistration?.takeIf { it.isNotBlank() },
        )
        val invoiceId = invoiceDao.insertInvoice(invoice)

        val lineRows = computedLines.mapIndexed { idx, c ->
            InvoiceLineEntity(
                invoiceId = invoiceId,
                description = c.description,
                extraNote = c.extraNote,
                quantity = c.quantity,
                unitPriceHtCents = c.unitHtCents,
                vatRatePermille = c.vatRatePermille,
                lineHtCents = c.lineHt,
                lineVatCents = c.lineVat,
                lineTtcCents = c.lineTtc,
                position = idx,
            )
        }
        invoiceDao.insertLines(lineRows)

        appendAudit(invoiceId, "INVOICE_ISSUED", number.toString() + "|" + totalTtc.toString())

        invoiceId
    }.also { backupManager.triggerIfEnabled() }

    suspend fun attachPdf(invoiceId: Long, path: String) {
        invoiceDao.setPdfPath(invoiceId, path)
        appendAudit(invoiceId, "PDF_GENERATED", path)
    }

    suspend fun issueCredit(originalId: Long, reason: String?): Long {
        val orig = invoiceDao.getWithDetails(originalId)
            ?: error("Facture introuvable")
        require(orig.invoice.type == InvoiceType.INVOICE) {
            "Impossible d'émettre un avoir sur un avoir"
        }

        val number = companyDao.peekNextInvoiceNumber()
        companyDao.bumpInvoiceNumber()
        val now = System.currentTimeMillis()

        val credit = InvoiceEntity(
            number = number,
            clientId = orig.invoice.clientId,
            issueDate = now,
            dueDate = now,
            deliveryDate = null,
            totalHtCents = -orig.invoice.totalHtCents,
            totalVatCents = -orig.invoice.totalVatCents,
            totalTtcCents = -orig.invoice.totalTtcCents,
            paymentMethod = orig.invoice.paymentMethod,
            paymentDate = now,
            paymentNote = reason?.takeIf { it.isNotBlank() },
            status = InvoiceStatus.PAID,
            issuerName = orig.invoice.issuerName,
            vehicleModel = orig.invoice.vehicleModel,
            vehicleRegistration = orig.invoice.vehicleRegistration,
            type = InvoiceType.CREDIT_NOTE,
            linkedInvoiceId = originalId,
        )
        val newId = invoiceDao.insertInvoice(credit)

        val newLines = orig.lines.map { l ->
            InvoiceLineEntity(
                invoiceId = newId,
                description = l.description,
                extraNote = l.extraNote,
                quantity = l.quantity,
                unitPriceHtCents = -l.unitPriceHtCents,
                vatRatePermille = l.vatRatePermille,
                lineHtCents = -l.lineHtCents,
                lineVatCents = -l.lineVatCents,
                lineTtcCents = -l.lineTtcCents,
                position = l.position,
            )
        }
        invoiceDao.insertLines(newLines)

        appendAudit(newId, "CREDIT_NOTE_ISSUED", "${orig.invoice.number}->$number")
        backupManager.triggerIfEnabled()
        return newId
    }

    private suspend fun appendAudit(invoiceId: Long?, event: String, payload: String) {
        val prev = auditDao.lastHash()
        val md = MessageDigest.getInstance("SHA-256")
        val raw = (prev ?: "") + "|" + event + "|" + payload + "|" + System.currentTimeMillis()
        val hash = md.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        auditDao.append(
            AuditLogEntity(
                invoiceId = invoiceId,
                event = event,
                payloadHash = hash,
                previousHash = prev,
            )
        )
    }

    private data class ComputedLine(
        val description: String,
        val extraNote: String?,
        val quantity: Int,
        val unitHtCents: Long,
        val vatRatePermille: Int,
        val lineHt: Long,
        val lineVat: Long,
        val lineTtc: Long,
    )
}
