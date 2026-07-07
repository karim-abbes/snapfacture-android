package com.snapfacture.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Quotes live in their own table with their own number sequence: they carry
// no legal numbering obligation and must never consume invoice numbers.
@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [Index("clientId"), Index(value = ["number"], unique = true), Index("issueDate")]
)
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val clientId: Long,
    val issueDate: Long,
    val validUntil: Long,
    val totalHtCents: Long,
    val totalVatCents: Long,
    val totalTtcCents: Long,
    val currency: String = "EUR",
    val comment: String? = null,
    val taxOptedOutAtIssue: Boolean = false,
    val clientSiretAtIssue: String? = null,
    val companyNameAtIssue: String? = null,
    val companySirenAtIssue: String? = null,
    val companyAddressAtIssue: String? = null,
    val companyPostalAtIssue: String? = null,
    val companyCityAtIssue: String? = null,
    val companyVatNumberAtIssue: String? = null,
    val companyManagerAtIssue: String? = null,
    val convertedInvoiceId: Long? = null,
    val pdfPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "quote_lines",
    foreignKeys = [
        ForeignKey(
            entity = QuoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["quoteId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("quoteId")]
)
data class QuoteLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quoteId: Long,
    val description: String,
    val extraNote: String? = null,
    // Milli-units (1500 = 1.5), same convention and migration as invoice_lines.
    @ColumnInfo(name = "quantity") val quantityMilliUnits: Long,
    val unitPriceHtCents: Long,
    val vatRatePermille: Int,
    val lineHtCents: Long,
    val lineVatCents: Long,
    val lineTtcCents: Long,
    val position: Int = 0,
)
