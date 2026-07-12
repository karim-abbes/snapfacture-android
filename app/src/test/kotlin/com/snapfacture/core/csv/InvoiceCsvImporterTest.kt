package com.snapfacture.core.csv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.snapfacture.data.local.AppDatabase
import com.snapfacture.data.local.Seed
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvoiceCsvImporterTest {

    private lateinit var db: AppDatabase
    private lateinit var importer: InvoiceCsvImporter

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.companyDao().upsert(Seed.Company.copy(name = "Test SARL"))
        importer = InvoiceCsvImporter(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `snapfacture export header maps every field automatically`() {
        val header = listOf(
            "Type de pièce", "Numéro", "Référence", "Commande N°", "Client",
            "# n° TVA", "Rue", "Étage, appartement", "Ville", "Code postal",
            "Code de pays", "Pays", "Province/Etat",
            "Date d'émission", "Date d´échéance", "Date de livraison",
            "Hors TVA", "TVA", "Remise", "Montant", "Devise",
            "Date de règlement", "Montant payé", "Mode de paiement",
            "N° de référence du paiement", "Note de paiement", "Établi par",
        )
        val mapping = ImportField.suggestMapping(header)
        assertEquals(ImportField.entries.size, mapping.size)
        assertEquals(1, mapping[ImportField.NUMBER])
        assertEquals(4, mapping[ImportField.CLIENT])
        assertEquals(19, mapping[ImportField.TOTAL])
        // "TVA" must be the amount column, not "# n° TVA" nor "Hors TVA".
        assertEquals(17, mapping[ImportField.VAT])
        assertEquals(16, mapping[ImportField.HT])
    }

    @Test
    fun `unknown headers leave required fields unmapped`() {
        val mapping = ImportField.suggestMapping(listOf("Foo", "Bar", "Baz"))
        assertTrue(ImportField.entries.filter { it.required }.none { it in mapping })
    }

    @Test
    fun `reordered columns import through an explicit mapping`() = runBlocking {
        val rows = listOf(
            listOf("Wer", "Datum", "Summe", "Nr"),
            listOf("Dupont", "15/03/2026", "1 234,56", "42"),
        )
        val mapping = mapOf(
            ImportField.CLIENT to 0,
            ImportField.ISSUE_DATE to 1,
            ImportField.TOTAL to 2,
            ImportField.NUMBER to 3,
        )
        val report = importer.runImport(rows, mapping)
        assertEquals(report.errors.joinToString(), 1, report.imported)
        val inv = db.invoiceDao().getWithDetails(1)!!
        assertEquals(42, inv.invoice.number)
        assertEquals("Dupont", inv.client.name)
        assertEquals(123_456L, inv.invoice.totalTtcCents)
        // No HT/VAT column: falls back to tax-free totals.
        assertEquals(123_456L, inv.invoice.totalHtCents)
        assertEquals(0L, inv.invoice.totalVatCents)
        assertEquals(43, db.companyDao().peekNextInvoiceNumber())
    }

    @Test
    fun `semicolon file with french amounts imports end to end`() = runBlocking {
        val csv = "Numéro;Client;Date;Montant;Hors TVA\n7;Martin;01/02/2026;\"1 200,00\";\"1 000,00\"\n"
        val report = importer.runImport(StringReader(csv))
        assertEquals(report.errors.joinToString(), 1, report.imported)
        val inv = db.invoiceDao().getWithDetails(1)!!
        assertEquals(120_000L, inv.invoice.totalTtcCents)
        assertEquals(100_000L, inv.invoice.totalHtCents)
        // VAT derived from TTC - HT when only HT is mapped.
        assertEquals(20_000L, inv.invoice.totalVatCents)
    }

    @Test
    fun `us style amounts with thousands commas parse correctly`() = runBlocking {
        val rows = listOf(
            listOf("Number", "Customer", "Date", "Amount"),
            listOf("3", "Acme Corp", "2026-01-15", "$1,234.56"),
        )
        val report = importer.runImport(rows, ImportField.suggestMapping(rows.first()))
        assertEquals(report.errors.joinToString(), 1, report.imported)
        assertEquals(123_456L, db.invoiceDao().getWithDetails(1)!!.invoice.totalTtcCents)
    }

    @Test
    fun `rows with invalid data are reported and skipped`() = runBlocking {
        val rows = listOf(
            listOf("Numéro", "Client", "Date d'émission", "Montant"),
            listOf("abc", "Dupont", "01/01/2026", "10,00"),
            listOf("2", "", "01/01/2026", "10,00"),
            listOf("3", "Durand", "pas une date", "10,00"),
            listOf("4", "Bernard", "01/01/2026", "10,00"),
        )
        val report = importer.runImport(rows, ImportField.suggestMapping(rows.first()))
        assertEquals(1, report.imported)
        assertEquals(3, report.skipped)
        assertEquals(3, report.errors.size)
    }
}
