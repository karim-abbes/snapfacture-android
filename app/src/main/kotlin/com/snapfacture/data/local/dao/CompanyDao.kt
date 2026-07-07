package com.snapfacture.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.snapfacture.data.local.entity.CompanyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM company WHERE id = 1")
    fun observe(): Flow<CompanyEntity?>

    @Query("SELECT * FROM company WHERE id = 1")
    suspend fun get(): CompanyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(company: CompanyEntity)

    @Update
    suspend fun update(company: CompanyEntity)

    @Query("UPDATE company SET nextInvoiceNumber = nextInvoiceNumber + 1 WHERE id = 1")
    suspend fun bumpInvoiceNumber()

    @Query("SELECT nextInvoiceNumber FROM company WHERE id = 1")
    suspend fun peekNextInvoiceNumber(): Int

    @Query("UPDATE company SET nextQuoteNumber = nextQuoteNumber + 1 WHERE id = 1")
    suspend fun bumpQuoteNumber()

    @Query("SELECT nextQuoteNumber FROM company WHERE id = 1")
    suspend fun peekNextQuoteNumber(): Int
}
