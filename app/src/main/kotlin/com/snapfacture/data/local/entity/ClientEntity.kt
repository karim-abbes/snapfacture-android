package com.snapfacture.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val addressLine: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val siret: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
