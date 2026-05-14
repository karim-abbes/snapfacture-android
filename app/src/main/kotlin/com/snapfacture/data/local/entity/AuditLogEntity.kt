package com.snapfacture.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_log",
    indices = [Index("invoiceId")]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long?,
    val event: String,
    val payloadHash: String,
    val previousHash: String?,
    val timestamp: Long = System.currentTimeMillis(),
)
