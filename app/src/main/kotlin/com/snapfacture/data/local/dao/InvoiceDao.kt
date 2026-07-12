package com.snapfacture.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.snapfacture.data.local.entity.InvoiceEntity
import com.snapfacture.data.local.entity.InvoiceLineEntity
import com.snapfacture.data.local.relation.InvoiceWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY issueDate DESC, number DESC")
    fun observeAllWithDetails(): Flow<List<InvoiceWithDetails>>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getWithDetails(id: Long): InvoiceWithDetails?

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun get(id: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE type = 'CREDIT_NOTE' AND linkedInvoiceId = :originalId LIMIT 1")
    suspend fun findCreditFor(originalId: Long): InvoiceEntity?

    @Query("SELECT MAX(number) FROM invoices")
    suspend fun maxNumber(): Int?

    @Insert
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert
    suspend fun insertLines(lines: List<InvoiceLineEntity>): List<Long>

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("UPDATE invoices SET pdfPath = :path WHERE id = :id")
    suspend fun setPdfPath(id: Long, path: String)

    @Query("SELECT SUM(totalTtcCents) FROM invoices WHERE status != 'CANCELLED' AND issueDate >= :since")
    fun observeRevenueSince(since: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM invoices WHERE status != 'CANCELLED' AND issueDate >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    // Per-rate VAT breakdown over a period: the exact figures a French
    // quarterly VAT filing (or a US sales-tax filing) asks for. Credit
    // note lines are stored negative, so they net out naturally.
    @Query(
        """SELECT l.vatRatePermille AS rateBp,
                  SUM(l.lineHtCents) AS htCents,
                  SUM(l.lineVatCents) AS vatCents,
                  SUM(l.lineTtcCents) AS ttcCents
           FROM invoice_lines l JOIN invoices i ON i.id = l.invoiceId
           WHERE i.status != 'CANCELLED' AND i.issueDate >= :from AND i.issueDate < :to
           GROUP BY l.vatRatePermille
           ORDER BY l.vatRatePermille DESC"""
    )
    fun vatBreakdown(from: Long, to: Long): Flow<List<VatRateBreakdown>>
}

data class VatRateBreakdown(
    val rateBp: Int,
    val htCents: Long,
    val vatCents: Long,
    val ttcCents: Long,
)
