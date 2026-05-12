package com.ohmybattery.invoicing.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ohmybattery.invoicing.data.local.dao.AuditDao
import com.ohmybattery.invoicing.data.local.dao.ClientDao
import com.ohmybattery.invoicing.data.local.dao.CompanyDao
import com.ohmybattery.invoicing.data.local.dao.InvoiceDao
import com.ohmybattery.invoicing.data.local.dao.ProductDao
import com.ohmybattery.invoicing.data.local.entity.AuditLogEntity
import com.ohmybattery.invoicing.data.local.entity.ClientEntity
import com.ohmybattery.invoicing.data.local.entity.CompanyEntity
import com.ohmybattery.invoicing.data.local.entity.InvoiceEntity
import com.ohmybattery.invoicing.data.local.entity.InvoiceLineEntity
import com.ohmybattery.invoicing.data.local.entity.ProductEntity

@Database(
    entities = [
        CompanyEntity::class,
        ClientEntity::class,
        ProductEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        AuditLogEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun clientDao(): ClientDao
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val DB_NAME = "ohmybattery.db"
    }
}
