package com.snapfacture.data.repository

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuoteRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var invoiceRepo: InvoiceRepository
    private lateinit var quoteRepo: QuoteRepository
    private var clientId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.companyDao().upsert(Seed.Company.copy(name = "Test SARL", siren = "123456789"))
        clientId = db.clientDao().insert(ClientEntity(name = "Client Devis"))
        val prefs = CountryPreferences(context, db.companyDao())
        val backupManager = BackupManager(
            context = context,
            prefs = BackupPreferences(context),
            database = db,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        invoiceRepo = InvoiceRepository(
            db = db,
            invoiceDao = db.invoiceDao(),
            companyDao = db.companyDao(),
            auditDao = db.auditDao(),
            backupManager = backupManager,
            countryPrefs = prefs,
        )
        quoteRepo = QuoteRepository(
            db = db,
            quoteDao = db.quoteDao(),
            companyDao = db.companyDao(),
            invoiceRepo = invoiceRepo,
            countryPrefs = prefs,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val line = DraftLine("Pose chaudiere", null, 2_000L, 45_000L, 100)

    private fun quoteInput() = CreateQuoteInput(
        clientId = clientId,
        lines = listOf(line),
        issueDateMillis = 1_750_000_000_000L,
    )

    @Test
    fun `quote numbering is independent from invoice numbering`() = runBlocking {
        val q1 = quoteRepo.create(quoteInput())
        invoiceRepo.issue(
            IssueInvoiceInput(
                clientId = clientId,
                lines = listOf(line),
                paymentMethod = PaymentMethod.CASH,
                issueDateMillis = 1_750_000_000_000L,
                deliveryDateMillis = null,
                issuerName = "Testeur",
            )
        )
        val q2 = quoteRepo.create(quoteInput())

        assertEquals(1, quoteRepo.get(q1)!!.quote.number)
        assertEquals(2, quoteRepo.get(q2)!!.quote.number)
        // The invoice sequence must not have been consumed by the quotes.
        assertEquals(2, db.companyDao().peekNextInvoiceNumber())
        assertEquals(3, db.companyDao().peekNextQuoteNumber())
    }

    @Test
    fun `quote totals use line-level rounding and validity is 30 days`() = runBlocking {
        val id = quoteRepo.create(quoteInput())
        val q = quoteRepo.get(id)!!.quote
        // 450.00 x 2 at 10%: TTC 900.00, HT = round(90000000/1100) = 81818
        assertEquals(90_000L, q.totalTtcCents)
        assertEquals(81_818L, q.totalHtCents)
        assertEquals(90_000L, q.totalHtCents + q.totalVatCents)
        assertEquals(q.issueDate + 30L * 24 * 60 * 60 * 1000, q.validUntil)
        assertEquals("Test SARL", q.companyNameAtIssue)
    }

    @Test
    fun `conversion issues an invoice with the quote's frozen lines and links back`() = runBlocking {
        val quoteId = quoteRepo.create(quoteInput())
        val invoiceId = quoteRepo.convertToInvoice(quoteId, PaymentMethod.TRANSFER)

        val invoice = invoiceRepo.get(invoiceId)!!
        val quote = quoteRepo.get(quoteId)!!.quote
        assertEquals(quote.totalTtcCents, invoice.invoice.totalTtcCents)
        assertEquals(quote.totalHtCents, invoice.invoice.totalHtCents)
        assertEquals(PaymentMethod.TRANSFER, invoice.invoice.paymentMethod)
        assertEquals(1, invoice.invoice.number)
        assertEquals(invoiceId, quote.convertedInvoiceId)
        assertNotNull(invoice.lines.firstOrNull { it.description == "Pose chaudiere" })

        try {
            quoteRepo.convertToInvoice(quoteId, PaymentMethod.CASH)
            fail("a quote must not be convertible twice")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
