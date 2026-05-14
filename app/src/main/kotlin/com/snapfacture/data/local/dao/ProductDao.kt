package com.snapfacture.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.snapfacture.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE active = 1 ORDER BY sortOrder ASC, priceTtcCents ASC")
    fun observeActive(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY active DESC, sortOrder ASC, priceTtcCents ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM products")
    suspend fun maxSortOrder(): Int?

    @Query("UPDATE products SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ProductEntity): Long

    @Update
    suspend fun update(item: ProductEntity)
}
