package com.snapfacture.core.facturx

import com.snapfacture.core.money.Quantity
import com.snapfacture.core.money.TaxRate
import com.snapfacture.data.local.entity.InvoiceLineEntity
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.entity.PaymentMethod
import com.snapfacture.data.local.relation.InvoiceWithDetails
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the structured e-invoice XML of the French 2026-2027 reform:
 * UN/CEFACT Cross Industry Invoice (CII), EN 16931 profile — the payload
 * of a Factur-X file, and one of the three formats a PDP accepts as-is.
 * A credit note is emitted as TypeCode 381 with positive amounts.
 */
@Singleton
class FacturXGenerator @Inject constructor() {

    fun buildXml(details: InvoiceWithDetails, sourceInvoiceNumber: Int? = null): String {
        val inv = details.invoice
        val isCredit = inv.type == InvoiceType.CREDIT_NOTE
        val franchise = inv.taxOptedOutAtIssue ?: (inv.totalVatCents == 0L)
        val lines = details.lines.sortedBy { it.position }
        val currency = inv.currency

        // Credit notes store negative amounts; type 381 carries them positive.
        fun cents(v: Long): Long = if (isCredit) -v else v

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<rsm:CrossIndustryInvoice xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100" xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100" xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100">"""
        ).append('\n')

        sb.tag("rsm:ExchangedDocumentContext") {
            tag("ram:GuidelineSpecifiedDocumentContextParameter") {
                leaf("ram:ID", "urn:cen.eu:en16931:2017")
            }
        }

        sb.tag("rsm:ExchangedDocument") {
            leaf("ram:ID", inv.number.toString())
            leaf("ram:TypeCode", if (isCredit) "381" else "380")
            tag("ram:IssueDateTime") {
                leaf("udt:DateTimeString", date(inv.issueDate), """ format="102"""")
            }
        }

        sb.tag("rsm:SupplyChainTradeTransaction") {
            lines.forEachIndexed { idx, l -> lineItem(idx + 1, l, franchise, ::cents) }

            tag("ram:ApplicableHeaderTradeAgreement") {
                tag("ram:SellerTradeParty") {
                    leaf("ram:Name", inv.companyNameAtIssue.orEmpty())
                    inv.companySirenAtIssue?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }?.let {
                        tag("ram:SpecifiedLegalOrganization") {
                            leaf("ram:ID", it, """ schemeID="0002"""")
                        }
                    }
                    tag("ram:PostalTradeAddress") {
                        inv.companyPostalAtIssue?.takeIf { it.isNotBlank() }?.let { leaf("ram:PostcodeCode", it) }
                        inv.companyAddressAtIssue?.takeIf { it.isNotBlank() }?.let { leaf("ram:LineOne", it) }
                        inv.companyCityAtIssue?.takeIf { it.isNotBlank() }?.let { leaf("ram:CityName", it) }
                        leaf("ram:CountryID", "FR")
                    }
                    inv.companyVatNumberAtIssue?.takeIf { it.isNotBlank() }?.let {
                        tag("ram:SpecifiedTaxRegistration") {
                            leaf("ram:ID", it.replace(" ", ""), """ schemeID="VA"""")
                        }
                    }
                }
                tag("ram:BuyerTradeParty") {
                    leaf("ram:Name", details.client.name)
                    inv.clientSiretAtIssue?.takeIf { it.length == 14 }?.let {
                        tag("ram:SpecifiedLegalOrganization") {
                            leaf("ram:ID", it, """ schemeID="0009"""")
                        }
                    }
                    tag("ram:PostalTradeAddress") {
                        details.client.postalCode?.takeIf { it.isNotBlank() }?.let { leaf("ram:PostcodeCode", it) }
                        details.client.addressLine?.takeIf { it.isNotBlank() }?.let { leaf("ram:LineOne", it) }
                        details.client.city?.takeIf { it.isNotBlank() }?.let { leaf("ram:CityName", it) }
                        leaf("ram:CountryID", "FR")
                    }
                }
            }

            tag("ram:ApplicableHeaderTradeDelivery") {
                inv.deliveryDate?.let {
                    tag("ram:ActualDeliverySupplyChainEvent") {
                        tag("ram:OccurrenceDateTime") {
                            leaf("udt:DateTimeString", date(it), """ format="102"""")
                        }
                    }
                }
            }

            tag("ram:ApplicableHeaderTradeSettlement") {
                leaf("ram:InvoiceCurrencyCode", currency)
                tag("ram:SpecifiedTradeSettlementPaymentMeans") {
                    leaf("ram:TypeCode", paymentCode(inv.paymentMethod))
                }
                // One VAT breakdown per rate, faithful to the line-level
                // rounding that the legal PDF and the audit chain carry.
                lines.groupBy { it.vatRateBp }.toSortedMap(compareByDescending { it }).forEach { (rateBp, group) ->
                    tag("ram:ApplicableTradeTax") {
                        leaf("ram:CalculatedAmount", amount(cents(group.sumOf { it.lineVatCents })))
                        leaf("ram:TypeCode", "VAT")
                        if (franchise) leaf("ram:ExemptionReason", "TVA non applicable, art. 293 B du CGI")
                        leaf("ram:BasisAmount", amount(cents(group.sumOf { it.lineHtCents })))
                        leaf("ram:CategoryCode", categoryCode(rateBp, franchise))
                        leaf("ram:RateApplicablePercent", TaxRate.formatPercent(rateBp, Locale.US))
                    }
                }
                tag("ram:SpecifiedTradePaymentTerms") {
                    tag("ram:DueDateDateTime") {
                        leaf("udt:DateTimeString", date(inv.dueDate), """ format="102"""")
                    }
                }
                tag("ram:SpecifiedTradeSettlementHeaderMonetarySummation") {
                    leaf("ram:LineTotalAmount", amount(cents(lines.sumOf { it.lineHtCents })))
                    leaf("ram:TaxBasisTotalAmount", amount(cents(inv.totalHtCents)))
                    leaf("ram:TaxTotalAmount", amount(cents(inv.totalVatCents)), """ currencyID="$currency"""")
                    leaf("ram:GrandTotalAmount", amount(cents(inv.totalTtcCents)))
                    if (inv.paymentDate != null) {
                        leaf("ram:TotalPrepaidAmount", amount(cents(inv.totalTtcCents)))
                        leaf("ram:DuePayableAmount", "0.00")
                    } else {
                        leaf("ram:DuePayableAmount", amount(cents(inv.totalTtcCents)))
                    }
                }
                if (isCredit && sourceInvoiceNumber != null) {
                    tag("ram:InvoiceReferencedDocument") {
                        leaf("ram:IssuerAssignedID", sourceInvoiceNumber.toString())
                    }
                }
            }
        }

        sb.append("</rsm:CrossIndustryInvoice>").append('\n')
        return sb.toString()
    }

    private fun StringBuilder.lineItem(
        position: Int,
        l: InvoiceLineEntity,
        franchise: Boolean,
        cents: (Long) -> Long,
    ) {
        tag("ram:IncludedSupplyChainTradeLineItem") {
            tag("ram:AssociatedDocumentLineDocument") {
                leaf("ram:LineID", position.toString())
            }
            tag("ram:SpecifiedTradeProduct") {
                leaf("ram:Name", listOfNotNull(l.description, l.extraNote?.takeIf { it.isNotBlank() }).joinToString(" — "))
            }
            tag("ram:SpecifiedLineTradeAgreement") {
                tag("ram:NetPriceProductTradePrice") {
                    leaf("ram:ChargeAmount", amount(cents(l.unitPriceHtCents)))
                }
            }
            tag("ram:SpecifiedLineTradeDelivery") {
                leaf("ram:BilledQuantity", Quantity.canonical(l.quantityMilliUnits), """ unitCode="C62"""")
            }
            tag("ram:SpecifiedLineTradeSettlement") {
                tag("ram:ApplicableTradeTax") {
                    leaf("ram:TypeCode", "VAT")
                    leaf("ram:CategoryCode", categoryCode(l.vatRateBp, franchise))
                    leaf("ram:RateApplicablePercent", TaxRate.formatPercent(l.vatRateBp, Locale.US))
                }
                tag("ram:SpecifiedTradeSettlementLineMonetarySummation") {
                    leaf("ram:LineTotalAmount", amount(cents(l.lineHtCents)))
                }
            }
        }
    }

    // S = standard rate; E = exempt (franchise, art. 293 B); Z = zero-rated.
    private fun categoryCode(rateBp: Int, franchise: Boolean): String = when {
        franchise -> "E"
        rateBp == 0 -> "Z"
        else -> "S"
    }

    private fun paymentCode(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "10"
        PaymentMethod.CHECK -> "20"
        PaymentMethod.TRANSFER -> "30"
        PaymentMethod.CARD -> "48"
        PaymentMethod.OTHER -> "ZZZ"
    }

    private fun amount(centsValue: Long): String {
        val sign = if (centsValue < 0) "-" else ""
        val abs = kotlin.math.abs(centsValue)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    private fun date(millis: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.FRANCE).format(Date(millis))

    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private inline fun StringBuilder.tag(name: String, body: StringBuilder.() -> Unit) {
        append('<').append(name).append('>')
        body()
        append("</").append(name).append('>').append('\n')
    }

    private fun StringBuilder.leaf(name: String, value: String, attributes: String = "") {
        append('<').append(name).append(attributes).append('>')
        append(escape(value))
        append("</").append(name).append('>')
    }
}
