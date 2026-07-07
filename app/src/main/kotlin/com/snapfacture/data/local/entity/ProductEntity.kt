package com.snapfacture.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val priceTtcCents: Long,
    // Basis points (2000 = 20 %); column keeps its pre-v6 name.
    @ColumnInfo(name = "vatRatePermille") val vatRateBp: Int = 2000,
    val withInstall: Boolean = false,
    val serviceNote: String? = null,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)
