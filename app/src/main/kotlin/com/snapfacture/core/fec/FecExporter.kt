package com.snapfacture.core.fec

import com.snapfacture.data.local.dao.CompanyDao
import com.snapfacture.data.local.dao.InvoiceDao
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.relation.InvoiceWithDetails
import kotlinx.coroutines.flow.first
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Fichier des Écritures Comptables (art. A47 A-1 LPF, arrêté du 29/07/2013):
 * the normalized accounting file every French accountant — and the tax
 * administration — can ingest directly.
 *
 * One balanced sales-journal entry per issued document:
 *   411 Clients (TTC) / 706 Prestations (HT) / 44571 TVA collectée (TVA).
 * Credit notes swap the debit/credit sides; the FEC forbids negative
 * amounts. Labels are French by design: the FEC is a French fiscal
 * artifact, produced only on the FR profile.
 */
@Singleton
class FecExporter @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val companyDao: CompanyDao,
) {

    suspend fun exportAll(writer: Writer): Int {
        val invoices = invoiceDao.observeAllWithDetails().first()
            .sortedBy { it.invoice.number }
        writer.append(HEADER.joinToString(SEP)).append(EOL)
        invoices.forEach { writeEntry(writer, it) }
        return invoices.size
    }

    suspend fun suggestedFileName(closingDateMillis: Long): String {
        val siren = companyDao.get()?.siren?.filter { it.isDigit() }.orEmpty()
        val date = DATE_FMT.format(closingDateMillis)
        return (siren.ifBlank { "000000000" }) + "FEC" + date + ".txt"
    }

    private fun writeEntry(writer: Writer, inv: InvoiceWithDetails) {
        val isCredit = inv.invoice.type == InvoiceType.CREDIT_NOTE
        val number = inv.invoice.number
        val date = DATE_FMT.format(inv.invoice.issueDate)
        val pieceRef = (if (isCredit) "AV-" else "F-") + number
        val label = clean((if (isCredit) "Avoir N° " else "Facture N° ") + number + " - " + inv.client.name)
        val ttc = abs(inv.invoice.totalTtcCents)
        val ht = abs(inv.invoice.totalHtCents)
        val vat = abs(inv.invoice.totalVatCents)

        fun line(compte: String, compteLib: String, auxNum: String, auxLib: String, debit: Long, credit: Long) {
            writer.append(
                listOf(
                    JOURNAL_CODE, JOURNAL_LIB,
                    number.toString(), date,
                    compte, compteLib,
                    auxNum, auxLib,
                    pieceRef, date,
                    label,
                    amount(debit), amount(credit),
                    "", "",
                    date,
                    "", "",
                ).joinToString(SEP)
            ).append(EOL)
        }

        val aux = "C" + inv.invoice.clientId
        val auxLib = clean(inv.client.name)
        if (isCredit) {
            line(ACC_CLIENT, LIB_CLIENT, aux, auxLib, 0L, ttc)
            line(ACC_SALES, LIB_SALES, "", "", ht, 0L)
            if (vat != 0L) line(ACC_VAT, LIB_VAT, "", "", vat, 0L)
        } else {
            line(ACC_CLIENT, LIB_CLIENT, aux, auxLib, ttc, 0L)
            line(ACC_SALES, LIB_SALES, "", "", 0L, ht)
            if (vat != 0L) line(ACC_VAT, LIB_VAT, "", "", 0L, vat)
        }
    }

    // FEC amounts are unsigned decimals with a comma separator.
    private fun amount(cents: Long): String = "%d,%02d".format(cents / 100, cents % 100)

    // The separator and line breaks must never leak from free-text fields.
    private fun clean(s: String): String =
        s.replace('\t', ' ').replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim()

    private val DATE_FMT get() = SimpleDateFormat("yyyyMMdd", Locale.FRANCE)

    private companion object {
        const val SEP = "\t"
        const val EOL = "\r\n"
        const val JOURNAL_CODE = "VE"
        const val JOURNAL_LIB = "Ventes"
        const val ACC_CLIENT = "411000"
        const val LIB_CLIENT = "Clients"
        const val ACC_SALES = "706000"
        const val LIB_SALES = "Prestations de services"
        const val ACC_VAT = "445710"
        const val LIB_VAT = "TVA collectée"
        val HEADER = listOf(
            "JournalCode", "JournalLib", "EcritureNum", "EcritureDate",
            "CompteNum", "CompteLib", "CompAuxNum", "CompAuxLib",
            "PieceRef", "PieceDate", "EcritureLib", "Debit", "Credit",
            "EcritureLet", "DateLet", "ValidDate", "Montantdevise", "Idevise",
        )
    }
}
