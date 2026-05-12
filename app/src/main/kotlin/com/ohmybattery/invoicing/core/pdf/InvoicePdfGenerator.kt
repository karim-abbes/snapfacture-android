package com.ohmybattery.invoicing.core.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.ohmybattery.invoicing.core.country.CountryProfile
import com.ohmybattery.invoicing.core.country.FranceProfile
import com.ohmybattery.invoicing.core.money.Money
import com.ohmybattery.invoicing.data.local.entity.CompanyEntity
import com.ohmybattery.invoicing.data.local.entity.InvoiceType
import com.ohmybattery.invoicing.data.local.entity.PaymentMethod
import com.ohmybattery.invoicing.data.local.relation.InvoiceWithDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoicePdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dateFr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

    fun generate(
        invoice: InvoiceWithDetails,
        company: CompanyEntity,
        country: CountryProfile = FranceProfile,
        taxOptedOut: Boolean = false,
        sourceInvoiceNumber: Int? = null,
        sourceInvoiceDateMillis: Long? = null,
    ): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        drawHeader(canvas, company, invoice)
        var cursor = MARGIN + 230f
        if (invoice.invoice.type == InvoiceType.CREDIT_NOTE && sourceInvoiceNumber != null) {
            cursor = drawCreditReference(canvas, sourceInvoiceNumber, sourceInvoiceDateMillis, cursor)
        }
        cursor = drawClientBlock(canvas, invoice, cursor)
        cursor = drawInvoiceMetaBlock(canvas, invoice, cursor)
        cursor = drawLinesTable(canvas, invoice, cursor + 24f)
        cursor = drawComment(canvas, invoice, cursor + 8f)
        cursor = drawTotalsCard(canvas, invoice, cursor + 16f)
        drawPaidStamp(canvas, invoice, cursor + 18f)
        drawFooter(canvas, company, invoice, country, taxOptedOut)

        pdf.finishPage(page)

        val outDir = File(context.filesDir, "invoices").apply { mkdirs() }
        val prefix = if (invoice.invoice.type == InvoiceType.CREDIT_NOTE) "AV" else "F"
        val file = File(outDir, "$prefix-${invoice.invoice.number}.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    fun shareUriFor(file: File): android.net.Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // --- Drawing helpers ---------------------------------------------------

    private fun drawHeader(canvas: android.graphics.Canvas, company: CompanyEntity, inv: InvoiceWithDetails) {
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

        val title = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(legalName.uppercase(Locale.FRANCE), MARGIN, 70f, title)

        val sub = Paint().apply {
            color = Color.WHITE
            textSize = 12f
            isAntiAlias = true
        }
        canvas.drawText("$legalAddress, $legalPostal $legalCity", MARGIN, 92f, sub)
        canvas.drawText("Tél. ${company.phone}  •  ${company.email}  •  ${company.website}", MARGIN, 110f, sub)
        canvas.drawText("SIREN $legalSiren", MARGIN, 128f, sub)

        val docLabel = Paint().apply {
            color = Color.WHITE
            textSize = if (isCredit) 22f else 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val docTitle = if (isCredit) "FACTURE D'AVOIR N° ${inv.invoice.number}" else "FACTURE N° ${inv.invoice.number}"
        canvas.drawText(docTitle, PAGE_W - MARGIN, 70f, docLabel)

        val factureDate = Paint().apply {
            color = Color.WHITE
            textSize = 12f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val emissionLabel = if (isCredit) "Émis le" else "Émise le"
        canvas.drawText("$emissionLabel ${dateFr.format(Date(inv.invoice.issueDate))}", PAGE_W - MARGIN, 92f, factureDate)
    }

    private fun drawCreditReference(
        canvas: android.graphics.Canvas,
        sourceNumber: Int,
        sourceDateMillis: Long?,
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
        val dateSuffix = sourceDateMillis?.let { " du ${dateFr.format(Date(it))}" } ?: ""
        canvas.drawText(
            "Annule et remplace la facture N° $sourceNumber$dateSuffix",
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
        canvas.drawText("FACTURÉ À", MARGIN, top, label)

        val name = Paint().apply {
            color = INK
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(inv.client.name, MARGIN, top + 22f, name)

        val sub = Paint().apply { color = INK; textSize = 11f; isAntiAlias = true }
        var y = top + 40f
        listOfNotNull(
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

        val vehicleLine = listOfNotNull(inv.invoice.vehicleModel, inv.invoice.vehicleRegistration)
            .filter { it.isNotBlank() }
            .joinToString("  •  ")
        if (vehicleLine.isNotBlank()) {
            val vehicleLabel = Paint().apply {
                color = MUTED
                textSize = 9f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            y += 4f
            canvas.drawText("VÉHICULE", MARGIN, y, vehicleLabel)
            y += 13f
            canvas.drawText(vehicleLine, MARGIN, y, sub)
            y += 14f
        }
        return y
    }

    private fun drawInvoiceMetaBlock(canvas: android.graphics.Canvas, inv: InvoiceWithDetails, top: Float): Float {
        val xLabel = PAGE_W - MARGIN - 200f
        val xValue = PAGE_W - MARGIN

        val label = Paint().apply { color = MUTED; textSize = 10f; isAntiAlias = true }
        val value = Paint().apply {
            color = INK; textSize = 11f; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val rows = buildList {
            add("Date d'émission" to dateFr.format(Date(inv.invoice.issueDate)))
            add("Échéance" to dateFr.format(Date(inv.invoice.dueDate)))
            inv.invoice.deliveryDate?.let { add("Livraison" to dateFr.format(Date(it))) }
            add("Mode de paiement" to paymentLabel(inv.invoice.paymentMethod))
        }

        var y = top - 20f
        rows.forEach { (l, v) ->
            canvas.drawText(l, xLabel, y, label)
            canvas.drawText(v, xValue, y, value)
            y += 18f
        }
        return maxOf(top, y)
    }

    private fun drawLinesTable(canvas: android.graphics.Canvas, inv: InvoiceWithDetails, top: Float): Float {
        val headerPaint = Paint().apply { color = SOFT_BG }
        canvas.drawRect(MARGIN, top, PAGE_W - MARGIN, top + 28f, headerPaint)

        val headLabel = Paint().apply {
            color = INK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DESCRIPTION", MARGIN + 8f, top + 18f, headLabel)
        canvas.drawText("QTÉ", MARGIN + 320f, top + 18f, headLabel.right())
        canvas.drawText("P.U. HT", MARGIN + 400f, top + 18f, headLabel.right())
        canvas.drawText("TVA", MARGIN + 460f, top + 18f, headLabel.right())
        canvas.drawText("TOTAL TTC", PAGE_W - MARGIN - 8f, top + 18f, headLabel.right())

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
            canvas.drawText(Money.formatEurPlain(l.unitPriceHtCents), MARGIN + 400f, y + 18f, rowNum)
            canvas.drawText(Money.formatEurPlain(l.lineVatCents), MARGIN + 460f, y + 18f, rowNum)
            canvas.drawText(Money.formatEurPlain(l.lineTtcCents), PAGE_W - MARGIN - 8f, y + 18f, rowNum)
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
        canvas.drawText("COMMENTAIRE", MARGIN, top + 12f, label)

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

    private fun drawTotalsCard(canvas: android.graphics.Canvas, inv: InvoiceWithDetails, top: Float): Float {
        val isCredit = inv.invoice.type == InvoiceType.CREDIT_NOTE
        val totalColor = if (isCredit) CREDIT_BAND else BRAND
        val cardLeft = PAGE_W - MARGIN - 240f
        val cardRight = PAGE_W - MARGIN
        val cardBottom = top + 92f

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
        canvas.drawText("Sous-total HT", cardLeft + 14f, y, label)
        canvas.drawText(Money.formatEurPlain(inv.invoice.totalHtCents), cardRight - 14f, y, value)
        y += 22f
        canvas.drawText("TVA (20%)", cardLeft + 14f, y, label)
        canvas.drawText(Money.formatEurPlain(inv.invoice.totalVatCents), cardRight - 14f, y, value)
        y += 28f
        val totalText = if (isCredit) "À REMBOURSER" else "TOTAL TTC"
        canvas.drawText(totalText, cardLeft + 14f, y, totalLabel)
        canvas.drawText(Money.formatEurPlain(inv.invoice.totalTtcCents), cardRight - 14f, y, totalValue)

        return cardBottom
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
        val stamp = if (isCredit) "REMBOURSÉ • ${paymentLabel(inv.invoice.paymentMethod)}"
        else "PAYÉE • ${paymentLabel(inv.invoice.paymentMethod)}"
        canvas.drawText(stamp, MARGIN + 14f, top + 23f, label)
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
        canvas.drawText("$legalName — ${country.legalIdLabel} $legalSiren", MARGIN, PAGE_H - 70f, small)
        canvas.drawText("$legalAddress, $legalPostal $legalCity, ${company.country}", MARGIN, PAGE_H - 58f, small)
        canvas.drawText("Tél. ${company.phone}  •  ${company.email}  •  ${company.website}", MARGIN, PAGE_H - 46f, small)
        country.footerMention(taxOptedOut)?.let { mention ->
            canvas.drawText(mention, MARGIN, PAGE_H - 32f, small)
        }

        val signature = Paint().apply {
            color = MUTED; textSize = 10f; isAntiAlias = true; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("$legalManager • Gérant", PAGE_W - MARGIN, PAGE_H - 46f, signature)
    }

    private fun paymentLabel(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "Espèces"
        PaymentMethod.TRANSFER -> "Virement"
        PaymentMethod.CARD -> "Carte bancaire"
        PaymentMethod.CHECK -> "Chèque"
        PaymentMethod.OTHER -> "Autre"
    }

    private fun Paint.right(): Paint = Paint(this).apply { textAlign = Paint.Align.RIGHT }

    companion object {
        // A4 @ 72dpi-ish: 595 x 842 points
        private const val PAGE_W = 595
        private const val PAGE_H = 842
        private const val MARGIN = 40f

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
