package com.snapfacture.core.facturx

import com.snapfacture.data.local.entity.ClientEntity
import com.snapfacture.data.local.entity.InvoiceEntity
import com.snapfacture.data.local.entity.InvoiceLineEntity
import com.snapfacture.data.local.entity.InvoiceStatus
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.local.relation.InvoiceWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class FacturXGeneratorTest {

    private val generator = FacturXGenerator()

    private fun invoice(
        type: InvoiceType = InvoiceType.INVOICE,
        taxOptedOut: Boolean = false,
        totalHt: Long = 12_000L,
        totalVat: Long = 2_400L,
        totalTtc: Long = 14_400L,
    ) = InvoiceEntity(
        id = 1,
        number = 42,
        clientId = 1,
        issueDate = 1_750_000_000_000L,
        dueDate = 1_750_000_000_000L,
        deliveryDate = null,
        totalHtCents = totalHt,
        totalVatCents = totalVat,
        totalTtcCents = totalTtc,
        currency = "EUR",
        paymentMethod = PaymentMethod.CARD,
        paymentDate = 1_750_000_000_000L,
        status = InvoiceStatus.PAID,
        issuerName = "Testeur",
        type = type,
        companyNameAtIssue = "Plomberie Saadi",
        companySirenAtIssue = "123 456 789",
        companyAddressAtIssue = "1 rue des Artisans",
        companyPostalAtIssue = "75011",
        companyCityAtIssue = "Paris",
        companyVatNumberAtIssue = "FR 32 123456789",
        taxOptedOutAtIssue = taxOptedOut,
        clientSiretAtIssue = "98765432100012",
    )

    private fun line(
        ht: Long = 12_000L,
        vat: Long = 2_400L,
        ttc: Long = 14_400L,
        rateBp: Int = 2_000,
        qtyMilli: Long = 1_000L,
        description: String = "Depannage",
    ) = InvoiceLineEntity(
        id = 1,
        invoiceId = 1,
        description = description,
        quantityMilliUnits = qtyMilli,
        unitPriceHtCents = ht,
        vatRateBp = rateBp,
        lineHtCents = ht,
        lineVatCents = vat,
        lineTtcCents = ttc,
        position = 0,
    )

    private fun details(
        inv: InvoiceEntity = invoice(),
        lines: List<InvoiceLineEntity> = listOf(line()),
        clientName: String = "Dupont",
    ) = InvoiceWithDetails(
        invoice = inv,
        client = ClientEntity(id = 1, name = clientName, addressLine = "2 avenue du Client", postalCode = "69001", city = "Lyon"),
        lines = lines,
    )

    private fun parse(xml: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private fun Document.text(tag: String): String =
        getElementsByTagName(tag).item(0)?.textContent ?: ""

    @Test
    fun `standard invoice carries type 380, totals and seller identifiers`() {
        val xml = generator.buildXml(details())
        val doc = parse(xml)
        assertEquals("CrossIndustryInvoice", doc.documentElement.localName)
        val docId = (doc.getElementsByTagName("rsm:ExchangedDocument").item(0) as Element)
            .getElementsByTagName("ram:ID").item(0).textContent
        assertEquals("42", docId)
        assertEquals("380", doc.text("ram:TypeCode"))
        assertEquals("urn:cen.eu:en16931:2017",
            (doc.getElementsByTagName("ram:GuidelineSpecifiedDocumentContextParameter").item(0) as Element)
                .getElementsByTagName("ram:ID").item(0).textContent)
        assertEquals("EUR", doc.text("ram:InvoiceCurrencyCode"))
        assertEquals("120.00", doc.text("ram:TaxBasisTotalAmount"))
        assertEquals("24.00", doc.text("ram:TaxTotalAmount"))
        assertEquals("144.00", doc.text("ram:GrandTotalAmount"))
        // Paid at issue: prepaid = total, nothing due.
        assertEquals("144.00", doc.text("ram:TotalPrepaidAmount"))
        assertEquals("0.00", doc.text("ram:DuePayableAmount"))
        // SIREN digits only, ISO 6523 scheme 0002.
        val sellerLegalId = (doc.getElementsByTagName("ram:SpecifiedLegalOrganization").item(0) as Element)
            .getElementsByTagName("ram:ID").item(0) as Element
        assertEquals("123456789", sellerLegalId.textContent)
        assertEquals("0002", sellerLegalId.getAttribute("schemeID"))
        assertEquals("20", doc.text("ram:RateApplicablePercent"))
        assertEquals("S", doc.text("ram:CategoryCode"))
        val paymentCode = (doc.getElementsByTagName("ram:SpecifiedTradeSettlementPaymentMeans").item(0) as Element)
            .getElementsByTagName("ram:TypeCode").item(0).textContent
        assertEquals("48", paymentCode)
        // BT-23 cadre de facturation, mandatory in the French reform.
        val businessProcess = (doc.getElementsByTagName("ram:BusinessProcessSpecifiedDocumentContextParameter").item(0) as Element)
            .getElementsByTagName("ram:ID").item(0).textContent
        assertEquals("A1", businessProcess)
        // BT-34 / BT-49 routing electronic addresses on both parties.
        val uris = doc.getElementsByTagName("ram:URIUniversalCommunication")
        assertEquals(2, uris.length)
        val seller = (doc.getElementsByTagName("ram:SellerTradeParty").item(0) as Element)
            .getElementsByTagName("ram:URIID").item(0) as Element
        assertEquals("123456789", seller.textContent)
        assertEquals("0002", seller.getAttribute("schemeID"))
        val buyer = (doc.getElementsByTagName("ram:BuyerTradeParty").item(0) as Element)
            .getElementsByTagName("ram:URIID").item(0) as Element
        assertEquals("98765432100012", buyer.textContent)
        assertEquals("0009", buyer.getAttribute("schemeID"))
    }

    @Test
    fun `franchise invoice is exempt with the 293 B reason`() {
        val inv = invoice(taxOptedOut = true, totalHt = 12_000L, totalVat = 0L, totalTtc = 12_000L)
        val xml = generator.buildXml(details(inv, listOf(line(ht = 12_000L, vat = 0L, ttc = 12_000L, rateBp = 0))))
        val doc = parse(xml)
        assertEquals("E", doc.text("ram:CategoryCode"))
        assertTrue(doc.text("ram:ExemptionReason").contains("293 B"))
        assertEquals("0", doc.text("ram:RateApplicablePercent"))
    }

    @Test
    fun `credit note is type 381 with positive amounts and a source reference`() {
        val inv = invoice(type = InvoiceType.CREDIT_NOTE, totalHt = -12_000L, totalVat = -2_400L, totalTtc = -14_400L)
        val creditLine = line(ht = -12_000L, vat = -2_400L, ttc = -14_400L)
        val xml = generator.buildXml(details(inv, listOf(creditLine)), sourceInvoiceNumber = 41)
        val doc = parse(xml)
        assertEquals("381", doc.text("ram:TypeCode"))
        assertEquals("144.00", doc.text("ram:GrandTotalAmount"))
        assertEquals("120.00", doc.text("ram:TaxBasisTotalAmount"))
        assertTrue(xml.contains("<ram:InvoiceReferencedDocument><ram:IssuerAssignedID>41</ram:IssuerAssignedID>"))
        assertTrue("no negative amount may remain", !xml.contains(">-"))
    }

    @Test
    fun `us style rate and decimal quantity serialize exactly`() {
        val inv = invoice(totalHt = 10_000L, totalVat = 625L, totalTtc = 10_625L)
        val xml = generator.buildXml(
            details(inv, listOf(line(ht = 10_000L, vat = 625L, ttc = 10_625L, rateBp = 625, qtyMilli = 1_500L)))
        )
        val doc = parse(xml)
        assertEquals("6.25", doc.text("ram:RateApplicablePercent"))
        val qty = doc.getElementsByTagName("ram:BilledQuantity").item(0) as Element
        assertEquals("1.5", qty.textContent)
        assertEquals("C62", qty.getAttribute("unitCode"))
    }

    @Test
    fun `special characters are escaped`() {
        val xml = generator.buildXml(details(clientName = "Dupont & Fils <SARL>"))
        assertTrue(xml.contains("Dupont &amp; Fils &lt;SARL&gt;"))
        // And the parsed document round-trips the original text.
        val buyerName = (parse(xml).getElementsByTagName("ram:BuyerTradeParty").item(0) as Element)
            .getElementsByTagName("ram:Name").item(0).textContent
        assertEquals("Dupont & Fils <SARL>", buyerName)
    }

    @Test
    fun `two rates produce two tax breakdowns that sum to the totals`() {
        val l20 = line(ht = 10_000L, vat = 2_000L, ttc = 12_000L, rateBp = 2_000)
        val l10 = line(ht = 10_000L, vat = 1_000L, ttc = 11_000L, rateBp = 1_000).copy(position = 1)
        val inv = invoice(totalHt = 20_000L, totalVat = 3_000L, totalTtc = 23_000L)
        val doc = parse(generator.buildXml(details(inv, listOf(l20, l10))))
        val headerTaxes = (doc.getElementsByTagName("ram:ApplicableHeaderTradeSettlement").item(0) as Element)
            .getElementsByTagName("ram:ApplicableTradeTax")
        assertEquals(2, headerTaxes.length)
        val basisSum = (0 until headerTaxes.length).sumOf {
            (headerTaxes.item(it) as Element).getElementsByTagName("ram:BasisAmount")
                .item(0).textContent.replace(".", "").toLong()
        }
        assertEquals(20_000L, basisSum)
    }
}
