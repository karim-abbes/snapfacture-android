package com.snapfacture.core.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.snapfacture.R
import com.snapfacture.core.country.CountryProfile
import com.snapfacture.core.country.FranceProfile
import com.snapfacture.data.local.entity.CompanyEntity
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.local.relation.InvoiceWithDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoicePdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun generate(
        invoice: InvoiceWithDetails,
        company: CompanyEntity,
        country: CountryProfile = FranceProfile,
        taxOptedOut: Boolean = false,
        sourceInvoiceNumber: Int? = null,
        sourceInvoiceDateMillis: Long? = null,
        isQuote: Boolean = false,
    ): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        drawHeader(canvas, company, invoice, country, isQuote)
        var cursor = MARGIN + 230f
        if (invoice.invoice.type == InvoiceType.CREDIT_NOTE && sourceInvoiceNumber != null) {
            cursor = drawCreditReference(canvas, sourceInvoiceNumber, sourceInvoiceDateMillis, country, cursor)
        }
        cursor = drawClientBlock(canvas, invoice, cursor)
        cursor = drawInvoiceMetaBlock(canvas, invoice, country, cursor, isQuote)
        cursor = drawLinesTable(canvas, invoice, country, cursor + 24f)
        cursor = drawComment(canvas, invoice, cursor + 8f)
        cursor = drawTotalsCard(canvas, invoice, country, cursor + 16f)
        drawPaidStamp(canvas, invoice, cursor + 18f)
        if (!isQuote) drawB2bMentions(canvas, invoice, country)
        drawFooter(canvas, company, invoice, country, taxOptedOut)

        pdf.finishPage(page)

        val outDir = File(context.filesDir, "invoices").apply { mkdirs() }
        val prefix = when {
            isQuote -> "D"
            invoice.invoice.type == InvoiceType.CREDIT_NOTE -> "AV"
            else -> "F"
        }
        val file = File(outDir, "$prefix-${invoice.invoice.number}.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    fun shareUriFor(file: File): android.net.Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // --- Drawing helpers ---------------------------------------------------

    private fun drawHeader(
        canvas: android.graphics.Canvas,
        company: CompanyEntity,
        inv: InvoiceWithDetails,
        country: CountryProfile,
        isQuote: Boolean = false,
    ) {
        val isCredit = inv.invoice.type == InvoiceType.CREDIT_NOTE
        val bandColor = if (isCredit) CREDIT_BAND else BRAND
        val accentColor = if (isCredit) CREDIT_ACCENT else ACCENT

        val bandPaint = Paint().apply { color = bandColor }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 150f, bandPaint)

        val accentPaint = Paint().apply { color = accentColor }
        canvas.drawRect(0f, 150f, PAGE_W.toFloat(), 156f, accentPaint)

        val legalName = inv.invoice.companyNameAtIssue ?: company.name
        val legalAddress = inv.invoice.companyAddressAtIssue ?: company.addressLine
        val legalPostal = inv.invoice.companyPostalAtIssue ?: company.postalCode
        val legalCity = inv.invoice.companyCityAtIssue ?: company.city
        val legalSiren = inv.invoice.companySirenAtIssue ?: company.siren

        // The doc title is measured first so the company name knows how much
        // width it may use: both share the y=70 baseline and a long name used
        // to run straight into "FACTURE N° X".
        val docLabel = Paint().apply {
            color = Color.WHITE
            textSize = if (isCredit) 22f else 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val docTitle = context.getString(
            when {
                isQuote -> R.string.pdf_title_quote
                isCredit -> R.string.pdf_title_credit
                else -> R.string.pdf_title_invoice
            },
            inv.invoice.number,
        )

        val title = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        var name = legalName.uppercase(country.locale)
        val availableNameWidth =
            PAGE_W - MARGIN - MARGIN - docLabel.measureText(docTitle) - COLLISION_GAP
        while (title.measureText(name) > availableNameWidth && title.textSize > MIN_TITLE_SIZE) {
            title.textSize -= 1f
        }
        if (title.measureText(name) > availableNameWidth) {
            while (name.isNotEmpty() && title.measureText(name + "…") > availableNameWidth) {
                name = name.dropLast(1)
            }
            name += "…"
        }
        canvas.drawText(name, MARGIN, 70f, title)

        val sub = Paint().apply {
            color = Color.WHITE
            textSize = 12f
            isAntiAlias = true
        }
        joinNonBlank(legalAddress, joinNonBlank(legalPostal, legalCity, separator = " "))
            ?.let { canvas.drawText(it, MARGIN, 92f, sub) }
        contactLine(company)?.let { canvas.drawText(it, MARGIN, 110f, sub) }
        legalSiren.takeIf { it.isNotBlank() }
            ?.let { canvas.drawText("${country.legalIdLabel} $it", MARGIN, 128f, sub) }

        canvas.drawText(docTitle, PAGE_W - MARGIN, 70f, docLabel)

        val factureDate = Paint().apply {
            color = Color.WHITE
            textSize = 12f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val emission = context.getString(
            if (isCredit || isQuote) R.string.pdf_issued_on_m else R.string.pdf_issued_on_f,
            country.formatDate(inv.invoice.issueDate),
        )
        canvas.drawText(emission, PAGE_W - MARGIN, 92f, factureDate)
    }

    private fun drawCreditReference(
        canvas: android.graphics.Canvas,
        sourceNumber: Int,
        sourceDateMillis: Long?,
        country: CountryProfile,
        top: Float,
    ): Float {
        val tag = Paint().apply { color = CREDIT_BG }
        val rect = RectF(MARGIN, top, PAGE_W - MARGIN, top + 36f)
        canvas.drawRoundRect(rect, 8f, 8f, tag)
        val label = Paint().apply {
            color = CREDIT_BAND
            textSize = 13f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val dateSuffix = sourceDateMillis?.let {
            context.getString(R.string.pdf_credit_reference_date_suffix, country.formatDate(it))
        } ?: ""
        canvas.drawText(
            context.getString(R.string.pdf_credit_reference, sourceNumber, dateSuffix),
            MARGIN + 14f,
            top + 23f,
            label,
        )
        return top + 48f
    }

    private fun drawClientBlock(canvas: android.graphics.Canvas, inv: InvoiceWithDetails, top: Float): Float {
        val label = Paint().apply {
            color = MUTED
            textSize = 10f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(context.getString(R.string.pdf_billed_to), MARGIN, top, label)

        val name = Paint().apply {
            color = INK
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(inv.client.name, MARGIN, top + 22f, name)

        val sub = Paint().apply { color = INK; textSize = 11f; isAntiAlias = true }
        var y = top + 40f
        val siretLine = inv.invoice.clientSiretAtIssue?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.pdf_client_siret, it)
        }
        listOfNotNull(
            siretLine,
            inv.client.addressLine,
            listOfNotNull(inv.client.postalCode, inv.client.city)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null },
            inv.client.phone,
            inv.client.email,
        ).forEach { line ->
            canvas.drawText(line, MARGIN, y, sub)
            y += 14f
        }
        return y
    }

    private fun drawInvoiceMetaBlock(
        canvas: android.graphics.Canvas,
        inv: InvoiceWithDetails,
        country: CountryProfile,
        top: Float,
        isQuote: Boolean = false,
    ): Float {
        val xLabel = PAGE_W - MARGIN - 200f
        val xValue = PAGE_W - MARGIN

        val label = Paint().apply { color = MUTED; textSize = 10f; isAntiAlias = true }
        val value = Paint().apply {
            color = INK; textSize = 11f; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val rows = buildList {
            add(context.getString(R.string.pdf_meta_issue_date) to country.formatDate(inv.invoice.issueDate))
            if (isQuote) {
                // dueDate carries the quote's validity end when rendering a quote.
                add(context.getString(R.string.pdf_meta_valid_until) to country.formatDate(inv.invoice.dueDate))
            } else {
                add(context.getString(R.string.pdf_meta_due_date) to country.formatDate(inv.invoice.dueDate))
                inv.invoice.deliveryDate?.let {
                    add(context.getString(R.string.pdf_meta_delivery_date) to country.formatDate(it))
                }
                add(context.getString(R.string.pdf_meta_payment_method) to paymentLabel(inv.invoice.paymentMethod))
            }
        }

        var y = top - 20f
        rows.forEach { (l, v) ->
            canvas.drawText(l, xLabel, y, label)
            canvas.drawText(v, xValue, y, value)
            y += 18f
        }
        return maxOf(top, y)
    }

    private fun drawLinesTable(
        canvas: android.graphics.Canvas,
        inv: InvoiceWithDetails,
        country: CountryProfile,
        top: Float,
    ): Float {
        val showVat = inv.invoice.totalVatCents != 0L
        val headerPaint = Paint().apply { color = SOFT_BG }
        canvas.drawRect(MARGIN, top, PAGE_W - MARGIN, top + 28f, headerPaint)

        val headLabel = Paint().apply {
            color = INK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(context.getString(R.string.pdf_col_description), MARGIN + 8f, top + 18f, headLabel)
        canvas.drawText(context.getString(R.string.pdf_col_qty), MARGIN + 320f, top + 18f, headLabel.right())
        canvas.drawText(
            context.getString(if (showVat) R.string.pdf_col_unit_ht else R.string.pdf_col_unit),
            MARGIN + 400f, top + 18f, headLabel.right(),
        )
        if (showVat) {
            canvas.drawText(context.getString(R.string.pdf_col_vat), MARGIN + 460f, top + 18f, headLabel.right())
        }
        canvas.drawText(
            context.getString(if (showVat) R.string.pdf_col_total_ttc else R.string.pdf_col_total),
            PAGE_W - MARGIN - 8f, top + 18f, headLabel.right(),
        )

        var y = top + 28f
        val rowDesc = Paint().apply { color = INK; textSize = 12f; isAntiAlias = true }
        val rowNote = Paint().apply { color = MUTED; textSize = 10f; isAntiAlias = true }
        val rowNum = Paint().apply {
            color = INK; textSize = 12f; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val divider = Paint().apply { color = DIVIDER; strokeWidth = 0.6f }

        inv.lines.sortedBy { it.position }.forEach { l ->
            val rowH = if (l.extraNote.isNullOrBlank()) 32f else 48f
            canvas.drawText(l.description, MARGIN + 8f, y + 18f, rowDesc)
            l.extraNote?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, MARGIN + 8f, y + 33f, rowNote)
            }
            canvas.drawText(l.quantity.toString(), MARGIN + 320f, y + 18f, rowNum)
            canvas.drawText(country.formatMoney(l.unitPriceHtCents), MARGIN + 400f, y + 18f, rowNum)
            if (showVat) {
                canvas.drawText(country.formatMoney(l.lineVatCents), MARGIN + 460f, y + 18f, rowNum)
            }
            canvas.drawText(country.formatMoney(l.lineTtcCents), PAGE_W - MARGIN - 8f, y + 18f, rowNum)
            y += rowH
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, divider)
        }
        return y
    }

    private fun drawComment(canvas: android.graphics.Canvas, inv: InvoiceWithDetails, top: Float): Float {
        val comment = inv.invoice.comment?.takeIf { it.isNotBlank() } ?: return top
        val label = Paint().apply {
            color = MUTED
            textSize = 9f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(context.getString(R.string.pdf_comment), MARGIN, top + 12f, label)

        val body = Paint().apply {
            color = INK
            textSize = 11f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        val maxWidth = (PAGE_W - 2f * MARGIN - 260f).toInt()
        val lines = wrapText(comment, body, maxWidth)
        var y = top + 28f
        lines.take(4).forEach {
            canvas.drawText(it, MARGIN, y, body)
            y += 14f
        }
        return y
    }

    private fun wrapText(text: String, paint: Paint, maxWidthPx: Int): List<String> {
        val words = text.replace("\n", " \n ").split(' ').filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (word == "\n") {
                if (current.isNotEmpty()) { out += current.toString(); current = StringBuilder() }
                continue
            }
            val tentative = if (current.isEmpty()) word else current.toString() + " " + word
            if (paint.measureText(tentative) <= maxWidthPx) {
                current = StringBuilder(tentative)
            } else {
                if (current.isNotEmpty()) out += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private fun drawTotalsCard(
        canvas: android.graphics.Canvas,
        inv: InvoiceWithDetails,
        country: CountryProfile,
        top: Float,
    ): Float {
        val isCredit = inv.invoice.type == InvoiceType.CREDIT_NOTE
        val totalColor = if (isCredit) CREDIT_BAND else BRAND
        val showVat = inv.invoice.totalVatCents != 0L
        val cardLeft = PAGE_W - MARGIN - 240f
        val cardRight = PAGE_W - MARGIN
        val cardBottom = top + if (showVat) 92f else 48f

        val card = Paint().apply { color = SOFT_BG }
        canvas.drawRoundRect(RectF(cardLeft, top, cardRight, cardBottom), 10f, 10f, card)

        val label = Paint().apply { color = MUTED; textSize = 11f; isAntiAlias = true }
        val value = Paint().apply {
            color = INK; textSize = 12f; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val totalLabel = Paint().apply {
            color = totalColor; textSize = 14f; isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val totalValue = Paint().apply {
            color = totalColor; textSize = 18f; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        var y = top + 22f
        if (showVat) {
            canvas.drawText(context.getString(R.string.pdf_totals_ht), cardLeft + 14f, y, label)
            canvas.drawText(country.formatMoney(inv.invoice.totalHtCents), cardRight - 14f, y, value)
            y += 22f
            val ratePct = computeVatRatePct(inv.invoice.totalHtCents, inv.invoice.totalVatCents, country.locale)
            canvas.drawText("${country.taxLabel} ($ratePct%)", cardLeft + 14f, y, label)
            canvas.drawText(country.formatMoney(inv.invoice.totalVatCents), cardRight - 14f, y, value)
            y += 28f
        }
        val totalText = context.getString(
            when {
                isCredit -> R.string.pdf_totals_to_refund
                showVat -> R.string.pdf_totals_total_ttc
                else -> R.string.pdf_totals_total
            },
        )
        canvas.drawText(totalText, cardLeft + 14f, y, totalLabel)
        canvas.drawText(country.formatMoney(inv.invoice.totalTtcCents), cardRight - 14f, y, totalValue)

        return cardBottom
    }

    private fun computeVatRatePct(htCents: Long, vatCents: Long, locale: Locale = Locale.US): String {
        if (htCents == 0L) return "0"
        val rate = (vatCents.toDouble() / htCents.toDouble()) * 100.0
        val rounded = Math.round(rate * 10) / 10.0
        return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString()
        else String.format(locale, "%.1f", rounded)
    }

    private fun drawPaidStamp(canvas: android.graphics.Canvas, inv: InvoiceWithDetails, top: Float) {
        if (inv.invoice.paymentDate == null) return
        val isCredit = inv.invoice.type == InvoiceType.CREDIT_NOTE
        val tag = Paint().apply { color = if (isCredit) CREDIT_BG else ACCENT_SOFT }
        val rect = RectF(MARGIN, top, MARGIN + 230f, top + 36f)
        canvas.drawRoundRect(rect, 8f, 8f, tag)
        val label = Paint().apply {
            color = if (isCredit) CREDIT_BAND else INK
            textSize = 13f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val stamp = context.getString(
            if (isCredit) R.string.pdf_refunded_stamp else R.string.pdf_paid_stamp,
            paymentLabel(inv.invoice.paymentMethod),
        )
        canvas.drawText(stamp, MARGIN + 14f, top + 23f, label)
    }

    private fun drawB2bMentions(canvas: android.graphics.Canvas, inv: InvoiceWithDetails, country: CountryProfile) {
        if (country !is FranceProfile) return
        val small = Paint().apply {
            color = MUTED
            textSize = 8.5f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        // Escompte terms are mandatory on every French invoice (art. L441-9 C. com.);
        // penalty mentions only apply between businesses.
        val mentions = buildList {
            if (!inv.invoice.clientSiretAtIssue.isNullOrBlank()) {
                add(context.getString(R.string.pdf_b2b_penalties))
                add(context.getString(R.string.pdf_b2b_recovery))
            }
            add(context.getString(R.string.pdf_escompte))
        }
        var y = PAGE_H - 104f - 12f * (mentions.size - 1)
        mentions.forEach { line ->
            canvas.drawText(line, MARGIN, y, small)
            y += 12f
        }
    }

    private fun drawFooter(
        canvas: android.graphics.Canvas,
        company: CompanyEntity,
        inv: InvoiceWithDetails,
        country: CountryProfile,
        taxOptedOut: Boolean,
    ) {
        val legalName = inv.invoice.companyNameAtIssue ?: company.name
        val legalSiren = inv.invoice.companySirenAtIssue ?: company.siren
        val legalAddress = inv.invoice.companyAddressAtIssue ?: company.addressLine
        val legalPostal = inv.invoice.companyPostalAtIssue ?: company.postalCode
        val legalCity = inv.invoice.companyCityAtIssue ?: company.city
        val legalManager = inv.invoice.companyManagerAtIssue ?: company.managerName

        val divider = Paint().apply { color = DIVIDER; strokeWidth = 0.8f }
        canvas.drawLine(MARGIN, PAGE_H - 90f, PAGE_W - MARGIN, PAGE_H - 90f, divider)

        val small = Paint().apply { color = MUTED; textSize = 9f; isAntiAlias = true }
        val vatNumber = (inv.invoice.companyVatNumberAtIssue ?: company.vatNumber)
            ?.takeIf { it.isNotBlank() }
        val identityLine = joinNonBlank(
            joinNonBlank(legalName, company.legalForm.takeIf { it.isNotBlank() }?.let { "($it)" }, separator = " "),
            legalSiren.takeIf { it.isNotBlank() }?.let { "${country.legalIdLabel} $it" },
            vatNumber?.let { context.getString(R.string.pdf_vat_number, it) },
            separator = " — ",
        )
        identityLine?.let { canvas.drawText(it, MARGIN, PAGE_H - 70f, small) }
        joinNonBlank(legalAddress, joinNonBlank(legalPostal, legalCity, separator = " "), company.country)
            ?.let { canvas.drawText(it, MARGIN, PAGE_H - 58f, small) }
        contactLine(company)?.let { canvas.drawText(it, MARGIN, PAGE_H - 46f, small) }
        val effectiveTaxOptedOut = inv.invoice.taxOptedOutAtIssue ?: (inv.invoice.totalVatCents == 0L)
        country.footerMention(effectiveTaxOptedOut)?.let { mention ->
            canvas.drawText(mention, MARGIN, PAGE_H - 32f, small)
        }

        val signature = Paint().apply {
            color = MUTED; textSize = 10f; isAntiAlias = true; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            context.getString(R.string.pdf_footer_manager, legalManager),
            PAGE_W - MARGIN, PAGE_H - 46f, signature,
        )
    }

    // Empty optional company fields must drop the whole fragment, never leave
    // an orphan separator behind ("Tel. · ·", a lone comma, "SIREN " with no
    // value). Same idiom as the client block's listOfNotNull/ifBlank chain.
    private fun joinNonBlank(vararg parts: String?, separator: String = ", "): String? =
        parts.filterNot { it.isNullOrBlank() }.joinToString(separator).ifBlank { null }

    private fun contactLine(company: CompanyEntity): String? {
        val phonePart = company.phone.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.pdf_contact_phone_prefix, it) }
        return joinNonBlank(phonePart, company.email, company.website, separator = "  •  ")
    }

    private fun paymentLabel(method: PaymentMethod): String = context.getString(
        when (method) {
            PaymentMethod.CASH -> R.string.create_payment_cash
            PaymentMethod.TRANSFER -> R.string.create_payment_transfer
            PaymentMethod.CARD -> R.string.create_payment_card
            PaymentMethod.CHECK -> R.string.create_payment_check
            PaymentMethod.OTHER -> R.string.create_payment_other
        },
    )

    private fun Paint.right(): Paint = Paint(this).apply { textAlign = Paint.Align.RIGHT }

    companion object {
        // A4 @ 72dpi-ish: 595 x 842 points
        private const val PAGE_W = 595
        private const val PAGE_H = 842
        private const val MARGIN = 40f
        private const val COLLISION_GAP = 20f
        private const val MIN_TITLE_SIZE = 18f

        private val BRAND = Color.rgb(0x0D, 0x47, 0xA1)
        private val ACCENT = Color.rgb(0xFF, 0xB3, 0x00)
        private val ACCENT_SOFT = Color.rgb(0xFF, 0xE0, 0x82)
        private val CREDIT_BAND = Color.rgb(0xB7, 0x1C, 0x1C)
        private val CREDIT_ACCENT = Color.rgb(0xFF, 0x8A, 0x80)
        private val CREDIT_BG = Color.rgb(0xFF, 0xEB, 0xEE)
        private val INK = Color.rgb(0x11, 0x14, 0x18)
        private val MUTED = Color.rgb(0x6B, 0x74, 0x80)
        private val SOFT_BG = Color.rgb(0xF1, 0xF5, 0xFA)
        private val DIVIDER = Color.rgb(0xDF, 0xE5, 0xEC)
    }
}

/**
 * Renders a quote through the invoice PDF pipeline: same layout, quote
 * title, validity date in place of the due date, no paid stamp (payment
 * date is null) and no invoice-only statutory mentions.
 */
fun com.snapfacture.data.local.relation.QuoteWithDetails.asInvoiceForPdf(): InvoiceWithDetails =
    InvoiceWithDetails(
        invoice = com.snapfacture.data.local.entity.InvoiceEntity(
            id = quote.id,
            number = quote.number,
            clientId = quote.clientId,
            issueDate = quote.issueDate,
            dueDate = quote.validUntil,
            totalHtCents = quote.totalHtCents,
            totalVatCents = quote.totalVatCents,
            totalTtcCents = quote.totalTtcCents,
            currency = quote.currency,
            paymentDate = null,
            status = com.snapfacture.data.local.entity.InvoiceStatus.DRAFT,
            issuerName = quote.companyManagerAtIssue.orEmpty(),
            comment = quote.comment,
            companyNameAtIssue = quote.companyNameAtIssue,
            companySirenAtIssue = quote.companySirenAtIssue,
            companyAddressAtIssue = quote.companyAddressAtIssue,
            companyPostalAtIssue = quote.companyPostalAtIssue,
            companyCityAtIssue = quote.companyCityAtIssue,
            companyVatNumberAtIssue = quote.companyVatNumberAtIssue,
            companyManagerAtIssue = quote.companyManagerAtIssue,
            taxOptedOutAtIssue = quote.taxOptedOutAtIssue,
            clientSiretAtIssue = quote.clientSiretAtIssue,
        ),
        client = client,
        lines = lines.map { l ->
            com.snapfacture.data.local.entity.InvoiceLineEntity(
                invoiceId = quote.id,
                description = l.description,
                extraNote = l.extraNote,
                quantity = l.quantity,
                unitPriceHtCents = l.unitPriceHtCents,
                vatRatePermille = l.vatRatePermille,
                lineHtCents = l.lineHtCents,
                lineVatCents = l.lineVatCents,
                lineTtcCents = l.lineTtcCents,
                position = l.position,
            )
        },
    )
