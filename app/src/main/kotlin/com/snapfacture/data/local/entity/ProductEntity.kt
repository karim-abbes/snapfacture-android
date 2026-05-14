package com.snapfacture.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val priceTtcCents: Long,
    val vatRatePermille: Int = 200,
    val withInstall: Boolean = false,
    val serviceNote: String? = null,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)
