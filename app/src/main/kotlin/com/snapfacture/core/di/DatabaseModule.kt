package com.snapfacture.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.snapfacture.data.local.AppDatabase
import com.snapfacture.data.local.Seed
import com.snapfacture.data.local.dao.AuditDao
import com.snapfacture.data.local.dao.ClientDao
import com.snapfacture.data.local.dao.CompanyDao
import com.snapfacture.data.local.dao.InvoiceDao
import com.snapfacture.data.local.dao.ProductDao
import com.snapfacture.data.local.dao.QuoteDao
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

    // v2: audit_log stores the clear payload so the anti-fraud hash chain can
    // be recomputed and verified; plus the two indexes used by every list query.
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `audit_log` ADD COLUMN `payload` TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_issueDate` ON `invoices` (`issueDate`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_linkedInvoiceId` ON `invoices` (`linkedInvoiceId`)")
        }
    }

    // v3: quotes. The DDL must match exactly what Room generates for the
    // entities (see app/schemas once exported).
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `company` ADD COLUMN `nextQuoteNumber` INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `quotes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`number` INTEGER NOT NULL, " +
                    "`clientId` INTEGER NOT NULL, " +
                    "`issueDate` INTEGER NOT NULL, " +
                    "`validUntil` INTEGER NOT NULL, " +
                    "`totalHtCents` INTEGER NOT NULL, " +
                    "`totalVatCents` INTEGER NOT NULL, " +
                    "`totalTtcCents` INTEGER NOT NULL, " +
                    "`currency` TEXT NOT NULL, " +
                    "`comment` TEXT, " +
                    "`taxOptedOutAtIssue` INTEGER NOT NULL, " +
                    "`clientSiretAtIssue` TEXT, " +
                    "`companyNameAtIssue` TEXT, " +
                    "`companySirenAtIssue` TEXT, " +
                    "`companyAddressAtIssue` TEXT, " +
                    "`companyPostalAtIssue` TEXT, " +
                    "`companyCityAtIssue` TEXT, " +
                    "`companyVatNumberAtIssue` TEXT, " +
                    "`companyManagerAtIssue` TEXT, " +
                    "`convertedInvoiceId` INTEGER, " +
                    "`pdfPath` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quotes_clientId` ON `quotes` (`clientId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_quotes_number` ON `quotes` (`number`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quotes_issueDate` ON `quotes` (`issueDate`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `quote_lines` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`quoteId` INTEGER NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`extraNote` TEXT, " +
                    "`quantity` INTEGER NOT NULL, " +
                    "`unitPriceHtCents` INTEGER NOT NULL, " +
                    "`vatRatePermille` INTEGER NOT NULL, " +
                    "`lineHtCents` INTEGER NOT NULL, " +
                    "`lineVatCents` INTEGER NOT NULL, " +
                    "`lineTtcCents` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`quoteId`) REFERENCES `quotes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quote_lines_quoteId` ON `quote_lines` (`quoteId`)")
        }
    }

    // v4: line quantities become milli-units (1500 = 1.5) so decimal
    // quantities keep exact integer arithmetic. Existing rows are
    // reinterpreted, not just retyped: 2 units → 2000 milli-units,
    // otherwise every past invoice would display its amounts divided
    // by 1000.
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE `invoice_lines` SET `quantity` = `quantity` * 1000")
            db.execSQL("UPDATE `quote_lines` SET `quantity` = `quantity` * 1000")
        }
    }

    // v5: mentions of the 2026-09-01 e-invoicing reform. Two company
    // settings (operation category, VAT-on-debits option) plus their
    // per-invoice snapshots and the optional delivery address.
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `company` ADD COLUMN `operationCategory` TEXT NOT NULL DEFAULT 'MIXED'")
            db.execSQL("ALTER TABLE `company` ADD COLUMN `vatOnDebits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `invoices` ADD COLUMN `deliveryAddress` TEXT")
            db.execSQL("ALTER TABLE `invoices` ADD COLUMN `operationCategoryAtIssue` TEXT")
            db.execSQL("ALTER TABLE `invoices` ADD COLUMN `vatOnDebitsAtIssue` INTEGER")
        }
    }

    // v6: tax rates move from permille (base 1000) to basis points
    // (base 10000) so US rates like 6.25 % are exact. Reinterpretation,
    // not retyping: every stored rate is multiplied by 10.
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE `products` SET `vatRatePermille` = `vatRatePermille` * 10")
            db.execSQL("UPDATE `invoice_lines` SET `vatRatePermille` = `vatRatePermille` * 10")
            db.execSQL("UPDATE `quote_lines` SET `vatRatePermille` = `vatRatePermille` * 10")
            db.execSQL("UPDATE `company` SET `defaultTaxPermille` = `defaultTaxPermille` * 10")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        companyDao: Lazy<CompanyDao>,
        productDao: Lazy<ProductDao>,
    ): AppDatabase {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    scope.launch {
                        companyDao.get().upsert(Seed.Company)
                        if (productDao.get().count() == 0) {
                            productDao.get().insertAll(Seed.Catalog)
                        }
                    }
                }
            })
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    @Provides fun companyDao(db: AppDatabase): CompanyDao = db.companyDao()
    @Provides fun clientDao(db: AppDatabase): ClientDao = db.clientDao()
    @Provides fun productDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides fun invoiceDao(db: AppDatabase): InvoiceDao = db.invoiceDao()
    @Provides fun auditDao(db: AppDatabase): AuditDao = db.auditDao()
    @Provides fun quoteDao(db: AppDatabase): QuoteDao = db.quoteDao()
}
