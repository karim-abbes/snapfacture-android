package com.snapfacture.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class InvoiceStatus { DRAFT, ISSUED, PAID, CANCELLED }
enum class PaymentMethod { CASH, TRANSFER, CARD, CHECK, OTHER }
enum class InvoiceType { INVOICE, CREDIT_NOTE }

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [
        Index("clientId"),
        Index(value = ["number"], unique = true),
        Index("issueDate"),
        Index("linkedInvoiceId"),
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val clientId: Long,
    val issueDate: Long,
    val dueDate: Long,
    val deliveryDate: Long? = null,
    val totalHtCents: Long,
    val totalVatCents: Long,
    val totalTtcCents: Long,
    val discountCents: Long = 0,
    val currency: String = "EUR",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val paymentDate: Long? = null,
    val paymentReference: String? = null,
    val paymentNote: String? = null,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val issuerName: String,
    val pdfPath: String? = null,
    val type: InvoiceType = InvoiceType.INVOICE,
    val linkedInvoiceId: Long? = null,
    val comment: String? = null,
    val companyNameAtIssue: String? = null,
    val companySirenAtIssue: String? = null,
    val companyAddressAtIssue: String? = null,
    val companyPostalAtIssue: String? = null,
    val companyCityAtIssue: String? = null,
    val companyVatNumberAtIssue: String? = null,
    val companyManagerAtIssue: String? = null,
    val taxOptedOutAtIssue: Boolean? = null,
    val clientSiretAtIssue: String? = null,
    // Réforme e-facturation 2026-09-01: delivery address when it differs
    // from the client's billing address, plus two company-setting snapshots
    // (frozen at issue like the company block above). All null on pre-v5 rows.
    val deliveryAddress: String? = null,
    val operationCategoryAtIssue: OperationCategory? = null,
    val vatOnDebitsAtIssue: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
