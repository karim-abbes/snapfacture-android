package com.snapfacture.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.snapfacture.data.local.entity.QuoteEntity
import com.snapfacture.data.local.entity.QuoteLineEntity
import com.snapfacture.data.local.relation.QuoteWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {

    @Transaction
    @Query("SELECT * FROM quotes ORDER BY issueDate DESC, number DESC")
    fun observeAllWithDetails(): Flow<List<QuoteWithDetails>>

    @Transaction
    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun getWithDetails(id: Long): QuoteWithDetails?

    @Insert
    suspend fun insertQuote(quote: QuoteEntity): Long

    @Insert
    suspend fun insertLines(lines: List<QuoteLineEntity>): List<Long>

    @Query("UPDATE quotes SET pdfPath = :path WHERE id = :id")
    suspend fun setPdfPath(id: Long, path: String)

    @Query("UPDATE quotes SET convertedInvoiceId = :invoiceId WHERE id = :id")
    suspend fun markConverted(id: Long, invoiceId: Long)
}
