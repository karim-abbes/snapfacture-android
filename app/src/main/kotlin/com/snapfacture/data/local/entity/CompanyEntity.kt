package com.snapfacture.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Mention "catégorie d'opération" required on French invoices from
// 2026-09-01. Company-level: the target user (artisan, one-person shop)
// has a single activity — no per-invoice choice.
enum class OperationCategory { GOODS, SERVICES, MIXED }

@Entity(tableName = "company")
data class CompanyEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val legalForm: String,
    val siren: String,
    val vatNumber: String?,
    val addressLine: String,
    val postalCode: String,
    val city: String,
    val country: String,
    val phone: String,
    val email: String,
    val website: String,
    val managerName: String,
    val iban: String?,
    val nextInvoiceNumber: Int,
    @ColumnInfo(defaultValue = "1") val nextQuoteNumber: Int = 1,
    val defaultTaxPermille: Int = 0,
    @ColumnInfo(defaultValue = "MIXED") val operationCategory: OperationCategory = OperationCategory.MIXED,
    @ColumnInfo(defaultValue = "0") val vatOnDebits: Boolean = false,
)
