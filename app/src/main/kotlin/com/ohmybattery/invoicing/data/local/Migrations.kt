package com.ohmybattery.invoicing.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN vehicleModel TEXT")
        db.execSQL("ALTER TABLE invoices ADD COLUMN vehicleRegistration TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN type TEXT NOT NULL DEFAULT 'INVOICE'")
        db.execSQL("ALTER TABLE invoices ADD COLUMN linkedInvoiceId INTEGER")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN comment TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN companyNameAtIssue TEXT")
        db.execSQL("ALTER TABLE invoices ADD COLUMN companySirenAtIssue TEXT")
        db.execSQL("ALTER TABLE invoices ADD COLUMN companyAddressAtIssue TEXT")
        db.execSQL("ALTER TABLE invoices ADD COLUMN companyPostalAtIssue TEXT")
        db.execSQL("ALTER TABLE invoices ADD COLUMN companyCityAtIssue TEXT")
        db.execSQL("ALTER TABLE invoices ADD COLUMN companyVatNumberAtIssue TEXT")
        db.execSQL("ALTER TABLE invoices ADD COLUMN companyManagerAtIssue TEXT")
        db.execSQL(
            "UPDATE invoices SET " +
                "companyNameAtIssue = (SELECT name FROM company WHERE id = 1), " +
                "companySirenAtIssue = (SELECT siren FROM company WHERE id = 1), " +
                "companyAddressAtIssue = (SELECT addressLine FROM company WHERE id = 1), " +
                "companyPostalAtIssue = (SELECT postalCode FROM company WHERE id = 1), " +
                "companyCityAtIssue = (SELECT city FROM company WHERE id = 1), " +
                "companyVatNumberAtIssue = (SELECT vatNumber FROM company WHERE id = 1), " +
                "companyManagerAtIssue = (SELECT managerName FROM company WHERE id = 1)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE invoices SET " +
                "companyNameAtIssue = (SELECT name FROM company WHERE id = 1), " +
                "companySirenAtIssue = (SELECT siren FROM company WHERE id = 1), " +
                "companyAddressAtIssue = (SELECT addressLine FROM company WHERE id = 1), " +
                "companyPostalAtIssue = (SELECT postalCode FROM company WHERE id = 1), " +
                "companyCityAtIssue = (SELECT city FROM company WHERE id = 1), " +
                "companyVatNumberAtIssue = (SELECT vatNumber FROM company WHERE id = 1), " +
                "companyManagerAtIssue = (SELECT managerName FROM company WHERE id = 1) " +
                "WHERE companyNameAtIssue IS NULL"
        )
    }
}
