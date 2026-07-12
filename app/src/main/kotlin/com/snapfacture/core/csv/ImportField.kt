package com.snapfacture.core.csv

import java.util.Locale

/**
 * Target fields of an invoice import, with the header names third-party
 * tools commonly use. `synonyms` drive the auto-suggested mapping: a file
 * exported by Snapfacture itself must map 100 % without user action.
 */
enum class ImportField(val required: Boolean, val synonyms: List<String>) {
    NUMBER(true, listOf("numéro", "number", "invoice number", "n°", "num", "no", "facture n°")),
    CLIENT(true, listOf("client", "customer", "customer name", "client name", "nom du client")),
    ISSUE_DATE(true, listOf("date d'émission", "issue date", "invoice date", "date", "émission")),
    TOTAL(true, listOf("montant", "total", "amount", "total ttc", "montant ttc", "grand total", "total amount")),
    HT(false, listOf("hors tva", "ht", "total ht", "montant ht", "subtotal", "hors taxe", "net amount", "net")),
    VAT(false, listOf("tva", "vat", "tax", "taxe", "sales tax", "tax amount", "montant tva")),
    TYPE(false, listOf("type de pièce", "type", "document type", "doc type")),
    DUE_DATE(false, listOf("date d'échéance", "due date", "échéance")),
    DELIVERY_DATE(false, listOf("date de livraison", "delivery date", "livraison")),
    PAYMENT_METHOD(false, listOf("mode de paiement", "payment method", "paiement", "payment")),
    PAYMENT_DATE(false, listOf("date de règlement", "payment date", "paid on", "règlement")),
    ISSUER(false, listOf("établi par", "issued by", "issuer")),
    STREET(false, listOf("rue", "street", "adresse", "address", "address line")),
    POSTAL(false, listOf("code postal", "postal code", "zip", "zip code", "cp")),
    CITY(false, listOf("ville", "city", "town"));

    companion object {
        fun normalize(header: String): String =
            header.trim().lowercase(Locale.FRANCE)
                .replace('´', '\'')
                .replace('`', '\'')

        /**
         * Exact match on normalized names only: a wrong guess costs the user
         * more than an empty dropdown ("Montant payé" must not steal TOTAL,
         * "# n° TVA" must not steal VAT). Each column maps to one field.
         */
        fun suggestMapping(headers: List<String>): Map<ImportField, Int> {
            val normalized = headers.map { normalize(it) }
            val used = mutableSetOf<Int>()
            val out = mutableMapOf<ImportField, Int>()
            for (field in entries) {
                val idx = field.synonyms.asSequence()
                    .map { syn -> normalized.indexOfFirst { it == syn } }
                    .firstOrNull { it >= 0 && it !in used }
                if (idx != null) {
                    out[field] = idx
                    used += idx
                }
            }
            return out
        }
    }
}
