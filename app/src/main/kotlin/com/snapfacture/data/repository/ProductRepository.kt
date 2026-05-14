package com.snapfacture.data.repository

import com.snapfacture.data.local.dao.ProductDao
import com.snapfacture.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(private val dao: ProductDao) {
    fun observeActive(): Flow<List<ProductEntity>> = dao.observeActive()
    fun observeAll(): Flow<List<ProductEntity>> = dao.observeAll()
    suspend fun insert(item: ProductEntity): Long {
        val nextOrder = (dao.maxSortOrder() ?: 0) + 1
        return dao.insert(item.copy(sortOrder = if (item.sortOrder == 0) nextOrder else item.sortOrder))
    }
    suspend fun update(item: ProductEntity) = dao.update(item)
    suspend fun setActive(id: Long, active: Boolean) = dao.setActive(id, active)
}
