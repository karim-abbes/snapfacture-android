package com.snapfacture.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.snapfacture.data.local.dao.AuditDao
import com.snapfacture.data.local.dao.ClientDao
import com.snapfacture.data.local.dao.CompanyDao
import com.snapfacture.data.local.dao.InvoiceDao
import com.snapfacture.data.local.dao.ProductDao
import com.snapfacture.data.local.entity.AuditLogEntity
import com.snapfacture.data.local.entity.ClientEntity
import com.snapfacture.data.local.entity.CompanyEntity
import com.snapfacture.data.local.entity.InvoiceEntity
import com.snapfacture.data.local.entity.InvoiceLineEntity
import com.snapfacture.data.local.entity.ProductEntity

@Database(
    entities = [
        CompanyEntity::class,
        ClientEntity::class,
        ProductEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        AuditLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun clientDao(): ClientDao
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val DB_NAME = "snapfacture.db"
    }
}
