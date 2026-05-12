package com.ohmybattery.invoicing.core.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ohmybattery.invoicing.data.local.AppDatabase
import com.ohmybattery.invoicing.data.local.MIGRATION_1_2
import com.ohmybattery.invoicing.data.local.MIGRATION_2_3
import com.ohmybattery.invoicing.data.local.MIGRATION_3_4
import com.ohmybattery.invoicing.data.local.MIGRATION_4_5
import com.ohmybattery.invoicing.data.local.MIGRATION_5_6
import com.ohmybattery.invoicing.data.local.Seed
import com.ohmybattery.invoicing.data.local.dao.AuditDao
import com.ohmybattery.invoicing.data.local.dao.BatteryDao
import com.ohmybattery.invoicing.data.local.dao.ClientDao
import com.ohmybattery.invoicing.data.local.dao.CompanyDao
import com.ohmybattery.invoicing.data.local.dao.InvoiceDao
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        companyDao: Lazy<CompanyDao>,
        batteryDao: Lazy<BatteryDao>,
    ): AppDatabase {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    scope.launch {
                        companyDao.get().upsert(Seed.Company)
                        if (batteryDao.get().count() == 0) {
                            batteryDao.get().insertAll(Seed.Catalog)
                        }
                    }
                }
            })
            .build()
    }

    @Provides fun companyDao(db: AppDatabase): CompanyDao = db.companyDao()
    @Provides fun clientDao(db: AppDatabase): ClientDao = db.clientDao()
    @Provides fun batteryDao(db: AppDatabase): BatteryDao = db.batteryDao()
    @Provides fun invoiceDao(db: AppDatabase): InvoiceDao = db.invoiceDao()
    @Provides fun auditDao(db: AppDatabase): AuditDao = db.auditDao()
}
