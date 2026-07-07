package com.snapfacture.data.repository

import androidx.room.withTransaction
import com.snapfacture.core.money.Money
import com.snapfacture.data.local.AppDatabase
import com.snapfacture.data.local.dao.CompanyDao
import com.snapfacture.data.local.dao.QuoteDao
import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.local.entity.QuoteEntity
import com.snapfacture.data.local.entity.QuoteLineEntity
import com.snapfacture.data.local.relation.QuoteWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class CreateQuoteInput(
    val clientId: Long,
    val lines: List<DraftLine>,
    val issueDateMillis: Long,
    val comment: String? = null,
    val taxOptedOut: Boolean = false,
    val clientSiret: String? = null,
)

@Singleton
class QuoteRepository @Inject constructor(
    private val db: AppDatabase,
    private val quoteDao: QuoteDao,
    private val companyDao: CompanyDao,
    private val invoiceRepo: InvoiceRepository,
    private val countryPrefs: com.snapfacture.data.preferences.CountryPreferences,
) {

    fun observeAll(): Flow<List<QuoteWithDetails>> = quoteDao.observeAllWithDetails()

    suspend fun get(id: Long): QuoteWithDetails? = quoteDao.getWithDetails(id)

    suspend fun create(input: CreateQuoteInput): Long = db.withTransaction {
        require(input.lines.isNotEmpty()) { "Empty quote" }

        val computed = input.lines.map { l ->
            val effectiveRate = if (input.taxOptedOut) 0 else l.vatRatePermille
            val amounts = Money.lineAmounts(l.unitPriceTtcCents, l.quantityMilliUnits, effectiveRate)
            Triple(l, effectiveRate, amounts)
        }

        val number = companyDao.peekNextQuoteNumber()
        companyDao.bumpQuoteNumber()
        val company = companyDao.get()
        val currency = countryPrefs.flow.first().profile.currency.currencyCode

        val quoteId = quoteDao.insertQuote(
            QuoteEntity(
                number = number,
                clientId = input.clientId,
                issueDate = input.issueDateMillis,
                validUntil = input.issueDateMillis + VALIDITY_MILLIS,
                totalHtCents = computed.sumOf { it.third.ht },
                totalVatCents = computed.sumOf { it.third.vat },
                totalTtcCents = computed.sumOf { it.third.ttc },
                currency = currency,
                comment = input.comment?.takeIf { it.isNotBlank() },
                taxOptedOutAtIssue = input.taxOptedOut,
                clientSiretAtIssue = input.clientSiret?.filter { it.isDigit() }?.takeIf { it.isNotBlank() },
                companyNameAtIssue = company?.name,
                companySirenAtIssue = company?.siren,
                companyAddressAtIssue = company?.addressLine,
                companyPostalAtIssue = company?.postalCode,
                companyCityAtIssue = company?.city,
                companyVatNumberAtIssue = company?.vatNumber,
                companyManagerAtIssue = company?.managerName,
            )
        )
        quoteDao.insertLines(
            computed.mapIndexed { idx, (l, rate, amounts) ->
                QuoteLineEntity(
                    quoteId = quoteId,
                    description = l.description,
                    extraNote = l.extraNote,
                    quantityMilliUnits = l.quantityMilliUnits,
                    unitPriceHtCents = if (rate == 0) l.unitPriceTtcCents
                    else Money.htFromTtc(l.unitPriceTtcCents, rate),
                    vatRatePermille = rate,
                    lineHtCents = amounts.ht,
                    lineVatCents = amounts.vat,
                    lineTtcCents = amounts.ttc,
                    position = idx,
                )
            }
        )
        quoteId
    }

    suspend fun attachPdf(quoteId: Long, path: String) = quoteDao.setPdfPath(quoteId, path)

    /**
     * Turns an accepted quote into a real invoice with the quote's frozen
     * lines and prices. Atomic: the invoice issue and the quote linkage
     * commit together, so a converted quote can never offer a second
     * conversion after a crash.
     */
    suspend fun convertToInvoice(quoteId: Long, paymentMethod: PaymentMethod): Long = db.withTransaction {
        val quote = quoteDao.getWithDetails(quoteId) ?: error("Quote not found")
        require(quote.quote.convertedInvoiceId == null) { "Quote already invoiced" }

        val unitTtc = { line: QuoteLineEntity ->
            // Half-up round of lineTtc / (milli / 1000); pure integer.
            if (line.quantityMilliUnits == 0L) 0L
            else Math.floorDiv(
                2_000L * line.lineTtcCents + line.quantityMilliUnits,
                2L * line.quantityMilliUnits,
            )
        }
        val invoiceId = invoiceRepo.issue(
            IssueInvoiceInput(
                clientId = quote.quote.clientId,
                lines = quote.lines.sortedBy { it.position }.map { l ->
                    DraftLine(
                        description = l.description,
                        extraNote = l.extraNote,
                        quantityMilliUnits = l.quantityMilliUnits,
                        unitPriceTtcCents = unitTtc(l),
                        vatRatePermille = l.vatRatePermille,
                    )
                },
                paymentMethod = paymentMethod,
                issueDateMillis = System.currentTimeMillis(),
                deliveryDateMillis = null,
                issuerName = quote.quote.companyManagerAtIssue.orEmpty()
                    .ifBlank { quote.quote.companyNameAtIssue.orEmpty() },
                comment = quote.quote.comment,
                taxOptedOut = quote.quote.taxOptedOutAtIssue,
                clientSiret = quote.quote.clientSiretAtIssue,
            )
        )
        quoteDao.markConverted(quoteId, invoiceId)
        invoiceId
    }

    private companion object {
        const val VALIDITY_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
