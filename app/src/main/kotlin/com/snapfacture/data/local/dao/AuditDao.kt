package com.snapfacture.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.snapfacture.data.local.entity.AuditLogEntity

@Dao
interface AuditDao {
    @Insert
    suspend fun append(entry: AuditLogEntity): Long

    @Query("SELECT payloadHash FROM audit_log ORDER BY id DESC LIMIT 1")
    suspend fun lastHash(): String?
}
