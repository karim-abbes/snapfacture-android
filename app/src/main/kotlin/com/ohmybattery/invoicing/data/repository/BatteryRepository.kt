package com.ohmybattery.invoicing.data.repository

import com.ohmybattery.invoicing.data.local.dao.BatteryDao
import com.ohmybattery.invoicing.data.local.entity.BatteryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepository @Inject constructor(private val dao: BatteryDao) {
    fun observeActive(): Flow<List<BatteryEntity>> = dao.observeActive()
    fun observeAll(): Flow<List<BatteryEntity>> = dao.observeAll()
    suspend fun insert(item: BatteryEntity): Long {
        val nextOrder = (dao.maxSortOrder() ?: 0) + 1
        return dao.insert(item.copy(sortOrder = if (item.sortOrder == 0) nextOrder else item.sortOrder))
    }
    suspend fun update(item: BatteryEntity) = dao.update(item)
    suspend fun setActive(id: Long, active: Boolean) = dao.setActive(id, active)
}
