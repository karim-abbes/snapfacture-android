package com.snapfacture.core.fec

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.snapfacture.core.backup.BackupManager
import com.snapfacture.data.local.AppDatabase
import com.snapfacture.data.local.Seed
import com.snapfacture.data.local.entity.ClientEntity
import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.preferences.BackupPreferences
import com.snapfacture.data.preferences.CountryPreferences
import com.snapfacture.data.repository.DraftLine
import com.snapfacture.data.repository.InvoiceRepository
import com.snapfacture.data.repository.IssueInvoiceInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringWriter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FecExporterTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: InvoiceRepository
    private lateinit var exporter: FecExporter
    private var clientId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.companyDao().upsert(Seed.Company.copy(name = "Test SARL", siren = "123 456 789"))
        clientId = db.clientDao().insert(ClientEntity(name = "Client\tTab|Pipe"))
        val prefs = CountryPreferences(context, db.companyDao())
        repo = InvoiceRepository(
            db = db,
            invoiceDao = db.invoiceDao(),
            companyDao = db.companyDao(),
            auditDao = db.auditDao(),
            backupManager = BackupManager(context, BackupPreferences(context), db, CoroutineScope(SupervisorJob() + Dispatchers.IO)),
            countryPrefs = prefs,
        )
        exporter = FecExporter(db.invoiceDao(), db.companyDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun issue(): Long = repo.issue(
        IssueInvoiceInput(
            clientId = clientId,
            lines = listOf(DraftLine("Depannage", null, 1, 12_000L, 200)),
            paymentMethod = PaymentMethod.CASH,
            issueDateMillis = 1_750_000_000_000L,
            deliveryDateMillis = null,
            issuerName = "Testeur",
        )
    )

    @Test
    fun `entries are balanced, unsigned and credit notes swap sides`() = runBlocking {
        val invId = issue()
        repo.issueCredit(invId, null)

        val out = StringWriter()
        val count = exporter.exportAll(out)
        assertEquals(2, count)

        val rows = out.toString().trim().split("\r\n")
        assertEquals("JournalCode", rows.first().split("\t").first())
        assertEquals(18, rows.first().split("\t").size)
        val data = rows.drop(1).map { it.split("\t") }
        // 2 documents x 3 lines (411 / 706 / 44571)
        assertEquals(6, data.size)
        data.forEach { cols ->
            assertEquals(18, cols.size)
            assertTrue("no negative amounts", !cols[11].startsWith("-") && !cols[12].startsWith("-"))
            assertTrue("no separator leak", cols[10].none { it == '|' })
        }
        fun cents(s: String): Long =
            if (s == "0,00") 0L else s.replace(",", "").toLong()
        val debits = data.sumOf { cents(it[11]) }
        val credits = data.sumOf { cents(it[12]) }
        assertEquals("debits must equal credits", debits, credits)
        // Invoice: client 411 debited with TTC; credit note: client 411 credited.
        val invoiceClientRow = data.first { it[8] == "F-1" && it[4] == "411000" }
        assertEquals("120,00", invoiceClientRow[11])
        val creditClientRow = data.first { it[8] == "AV-2" && it[4] == "411000" }
        assertEquals("120,00", creditClientRow[12])
        val expectedDate = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.FRANCE)
            .format(1_750_000_000_000L)
        assertEquals(expectedDate, invoiceClientRow[3])
    }

    @Test
    fun `file name follows the SIREN FEC date convention`() = runBlocking {
        val ts = 1_783_468_800_000L
        val expectedDate = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.FRANCE).format(ts)
        assertEquals("123456789FEC$expectedDate.txt", exporter.suggestedFileName(ts))
    }
}
