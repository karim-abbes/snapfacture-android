package com.ohmybattery.invoicing.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ohmybattery.invoicing.data.local.dao.AuditDao
import com.ohmybattery.invoicing.data.local.dao.BatteryDao
import com.ohmybattery.invoicing.data.local.dao.ClientDao
import com.ohmybattery.invoicing.data.local.dao.CompanyDao
import com.ohmybattery.invoicing.data.local.dao.InvoiceDao
import com.ohmybattery.invoicing.data.local.entity.AuditLogEntity
import com.ohmybattery.invoicing.data.local.entity.BatteryEntity
import com.ohmybattery.invoicing.data.local.entity.ClientEntity
import com.ohmybattery.invoicing.data.local.entity.CompanyEntity
import com.ohmybattery.invoicing.data.local.entity.InvoiceEntity
import com.ohmybattery.invoicing.data.local.entity.InvoiceLineEntity

@Database(
    entities = [
        CompanyEntity::class,
        ClientEntity::class,
        BatteryEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        AuditLogEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun clientDao(): ClientDao
    abstract fun batteryDao(): BatteryDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val DB_NAME = "ohmybattery.db"
    }
}
