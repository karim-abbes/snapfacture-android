package com.snapfacture.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.snapfacture.core.backup.BackupManager
import com.snapfacture.data.local.AppDatabase
import com.snapfacture.data.local.Seed
import com.snapfacture.data.local.entity.ClientEntity
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.preferences.BackupPreferences
import com.snapfacture.data.preferences.CountryPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvoiceRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: InvoiceRepository
    private var clientId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.companyDao().upsert(
            Seed.Company.copy(name = "Test SARL", siren = "123456789", managerName = "Testeur")
        )
        clientId = db.clientDao().insert(ClientEntity(name = "Client Test"))
        val prefs = CountryPreferences(context, db.companyDao())
        val backupManager = BackupManager(
            context = context,
            prefs = BackupPreferences(context),
            database = db,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        repo = InvoiceRepository(
            db = db,
            invoiceDao = db.invoiceDao(),
            companyDao = db.companyDao(),
            auditDao = db.auditDao(),
            backupManager = backupManager,
            countryPrefs = prefs,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun input(
        lines: List<DraftLine>,
        taxOptedOut: Boolean = false,
    ) = IssueInvoiceInput(
        clientId = clientId,
        lines = lines,
        paymentMethod = PaymentMethod.CASH,
        issueDateMillis = 1_750_000_000_000L,
        deliveryDateMillis = null,
        issuerName = "Testeur",
        taxOptedOut = taxOptedOut,
    )

    private val simpleLine = DraftLine(
        description = "Depannage",
        extraNote = null,
        quantityMilliUnits = 1_000L,
        unitPriceTtcCents = 12_000L,
        vatRatePermille = 200,
    )

    @Test
    fun `invoice numbers are sequential without gaps`() = runBlocking {
        val ids = (1..3).map { repo.issue(input(listOf(simpleLine))) }
        val numbers = ids.map { repo.get(it)!!.invoice.number }
        assertEquals(listOf(1, 2, 3), numbers)
        assertEquals(4, db.companyDao().peekNextInvoiceNumber())
    }

    @Test
    fun `totals derive from line-level rounding and company is snapshotted`() = runBlocking {
        val id = repo.issue(
            input(
                listOf(
                    // 0.99 x 100 at 20%: line HT must be 82.50, not 83.00
                    DraftLine("Joint", null, 100_000L, 99L, 200),
                    // 45.00 x 1 at 10%: HT = round(4500000/1100) = 4091
                    DraftLine("Main d'oeuvre", null, 1_000L, 4_500L, 100),
                )
            )
        )
        val inv = repo.get(id)!!.invoice
        assertEquals(14_400L, inv.totalTtcCents)
        assertEquals(8_250L + 4_091L, inv.totalHtCents)
        assertEquals(1_650L + 409L, inv.totalVatCents)
        assertEquals(inv.totalTtcCents, inv.totalHtCents + inv.totalVatCents)
        assertEquals("Test SARL", inv.companyNameAtIssue)
        assertEquals("123456789", inv.companySirenAtIssue)
        assertEquals("Testeur", inv.companyManagerAtIssue)
    }

    @Test
    fun `decimal quantities give exact totals and a verifiable audit chain`() = runBlocking {
        val id = repo.issue(
            input(
                listOf(
                    // 45,00 € × 1,5 h at 10%: TTC 67,50 €
                    DraftLine("Main d'oeuvre", null, 1_500L, 4_500L, 100),
                    // 12,00 € × 2 at 20%: TTC 24,00 €
                    DraftLine("Fourniture", null, 2_000L, 1_200L, 200),
                )
            )
        )
        val inv = repo.get(id)!!.invoice
        assertEquals(6_750L + 2_400L, inv.totalTtcCents)
        assertEquals(inv.totalTtcCents, inv.totalHtCents + inv.totalVatCents)

        // The payload serializes quantities canonically: "1.5" for fractions,
        // and "2" (not "2000") for whole values — byte-identical to what
        // pre-migration payloads contained, so they keep verifying.
        val payload = db.auditDao().all()
            .first { it.event == InvoiceRepository.EVENT_INVOICE_ISSUED }
            .payload
        assertTrue(payload.contains(",1.5,"))
        assertTrue(payload.contains(",2,"))

        assertTrue(repo.verifyAuditChain().ok)
    }

    @Test
    fun `franchise invoices carry zero vat`() = runBlocking {
        val id = repo.issue(input(listOf(simpleLine), taxOptedOut = true))
        val inv = repo.get(id)!!.invoice
        assertEquals(0L, inv.totalVatCents)
        assertEquals(inv.totalTtcCents, inv.totalHtCents)
        assertEquals(true, inv.taxOptedOutAtIssue)
    }

    @Test
    fun `credit note negates the original and consumes the next number`() = runBlocking {
        val originalId = repo.issue(input(listOf(simpleLine)))
        val creditId = repo.issueCredit(originalId, "erreur de saisie")
        val original = repo.get(originalId)!!.invoice
        val credit = repo.get(creditId)!!.invoice

        assertEquals(InvoiceType.CREDIT_NOTE, credit.type)
        assertEquals(original.number + 1, credit.number)
        assertEquals(originalId, credit.linkedInvoiceId)
        assertEquals(-original.totalTtcCents, credit.totalTtcCents)
        assertEquals(-original.totalHtCents, credit.totalHtCents)
        assertEquals(-original.totalVatCents, credit.totalVatCents)

        try {
            repo.issueCredit(creditId, null)
            fail("issuing a credit note on a credit note must be refused")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `audit chain verifies cleanly after normal activity`() = runBlocking {
        val firstId = repo.issue(input(listOf(simpleLine)))
        repo.issue(input(listOf(simpleLine)))
        repo.issueCredit(firstId, null)

        val result = repo.verifyAuditChain()
        assertTrue(result.ok)
        assertEquals(3, result.verifiedEntries)
        assertEquals(0, result.legacyEntries)
        assertNull(result.brokenAtEntryId)
        assertNull(result.tamperedInvoiceNumber)
    }

    @Test
    fun `verification detects an invoice modified behind the app's back`() = runBlocking {
        repo.issue(input(listOf(simpleLine)))
        db.openHelper.writableDatabase.execSQL(
            "UPDATE invoices SET totalTtcCents = totalTtcCents + 100 WHERE number = 1"
        )
        val result = repo.verifyAuditChain()
        assertTrue(!result.ok)
        assertEquals(1, result.tamperedInvoiceNumber)
    }

    @Test
    fun `vat breakdown groups by rate and nets out credit notes`() = runBlocking {
        val firstId = repo.issue(
            input(
                listOf(
                    DraftLine("Fourniture", null, 1_000L, 12_000L, 200),
                    DraftLine("Renovation", null, 1_000L, 11_000L, 100),
                )
            )
        )
        repo.issue(input(listOf(DraftLine("Depannage", null, 1_000L, 12_000L, 200))))
        repo.issueCredit(firstId, null)

        val rows = db.invoiceDao().vatBreakdown(0, Long.MAX_VALUE).first()
        val r20 = rows.first { it.ratePermille == 200 }
        assertEquals(10_000L, r20.htCents)
        assertEquals(2_000L, r20.vatCents)
        assertEquals(12_000L, r20.ttcCents)
        // The credited invoice's 10% line nets to zero.
        val r10 = rows.first { it.ratePermille == 100 }
        assertEquals(0L, r10.htCents)
        assertEquals(0L, r10.vatCents)
    }

    @Test
    fun `verification detects a rewritten audit log`() = runBlocking {
        repo.issue(input(listOf(simpleLine)))
        db.openHelper.writableDatabase.execSQL(
            "UPDATE audit_log SET payload = 'forged' WHERE id = 1"
        )
        val result = repo.verifyAuditChain()
        assertTrue(!result.ok)
        assertNotNull(result.brokenAtEntryId)
    }
}
