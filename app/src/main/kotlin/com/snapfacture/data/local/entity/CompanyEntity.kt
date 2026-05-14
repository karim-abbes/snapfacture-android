package com.snapfacture.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val defaultTaxPermille: Int = 0,
)
