package com.snapfacture.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoice_lines",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("invoiceId")]
)
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val description: String,
    val extraNote: String? = null,
    // Milli-units (1500 = 1.5). The column keeps its pre-v4 name; the v3→v4
    // migration multiplied every stored value by 1000.
    @ColumnInfo(name = "quantity") val quantityMilliUnits: Long,
    val unitPriceHtCents: Long,
    val vatRatePermille: Int,
    val lineHtCents: Long,
    val lineVatCents: Long,
    val lineTtcCents: Long,
    val position: Int = 0,
)
