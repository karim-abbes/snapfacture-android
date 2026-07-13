package com.snapfacture.spike

import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.Part
import com.google.ai.edge.localagents.core.proto.Schema
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.core.proto.Type

// Catalogue de tools identique au §5.1 de docs/DESIGN-ASSISTANT-IA.md.
// Les montants et quantités transitent en chaînes décimales, jamais en flottants.
object SnapfactureTools {

    private const val PERIODS = "this_month, last_month, this_quarter, last_quarter, this_year, last_year, all"

    private fun str(desc: String): Schema =
        Schema.newBuilder().setType(Type.STRING).setDescription(desc).build()

    private fun int(desc: String): Schema =
        Schema.newBuilder().setType(Type.INTEGER).setDescription(desc).build()

    private fun obj(required: List<String> = emptyList(), vararg props: Pair<String, Schema>): Schema {
        val b = Schema.newBuilder().setType(Type.OBJECT)
        props.forEach { (name, schema) -> b.putProperties(name, schema) }
        required.forEach { b.addRequired(it) }
        return b.build()
    }

    private val lineSchema: Schema = obj(
        required = listOf("description", "unit_price"),
        "description" to str("Description of the product or service, as spoken by the user."),
        "quantity" to str("Quantity as a decimal string, e.g. \"2\" or \"1.5\". Defaults to \"1\"."),
        "unit_price" to str("Unit price including tax, as a decimal string, e.g. \"120\" or \"85.50\". Never compute totals."),
        "vat_rate" to str("VAT / sales tax rate in percent as a decimal string, e.g. \"20\" or \"10\". Omit to use the default rate."),
    )

    private val linesSchema: Schema = Schema.newBuilder()
        .setType(Type.ARRAY)
        .setDescription("The invoice lines. One entry per distinct product or service.")
        .setItems(lineSchema)
        .build()

    val tool: Tool = Tool.newBuilder()
        .addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName("search_clients")
                .setDescription("Searches existing clients by name. Use it when the user mentions a client you are not sure exists, or asks anything about a client.")
                .setParameters(obj(listOf("query"), "query" to str("Part of the client name to search for.")))
        )
        .addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName("list_invoices")
                .setDescription("Lists issued invoices, optionally filtered by period, client name or type.")
                .setParameters(
                    obj(
                        emptyList(),
                        "period" to str("One of: $PERIODS."),
                        "client_name" to str("Filter by client name."),
                        "type" to str("One of: invoice, credit_note."),
                    )
                )
        )
        .addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName("get_invoice")
                .setDescription("Returns one invoice by its number.")
                .setParameters(obj(listOf("number"), "number" to int("The invoice number.")))
        )
        .addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName("get_revenue")
                .setDescription("Returns the total revenue (turnover) collected over a period.")
                .setParameters(obj(listOf("period"), "period" to str("One of: $PERIODS.")))
        )
        .addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName("get_tax_summary")
                .setDescription("Returns the VAT / sales tax summary (collected tax by rate) for a quarter.")
                .setParameters(
                    obj(
                        emptyList(),
                        "quarter" to int("Quarter number 1-4. Omit for the current quarter."),
                        "year" to int("Year, e.g. 2026. Omit for the current year."),
                    )
                )
        )
        .addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName("list_products")
                .setDescription("Lists the products and services of the user's catalog.")
                .setParameters(obj())
        )
        .addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName("propose_invoice")
                .setDescription("Prepares an invoice DRAFT for the user to review. Nothing is created until the user confirms. Only use amounts and client names explicitly given by the user, never invent them.")
                .setParameters(
                    obj(
                        listOf("client_name", "lines"),
                        "client_name" to str("The client name exactly as given by the user."),
                        "lines" to linesSchema,
                        "payment_method" to str("One of: cash, card, transfer, check. Omit if not mentioned."),
                    )
                )
        )
        .addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName("propose_quote")
                .setDescription("Prepares a quote (estimate) DRAFT for the user to review. Nothing is created until the user confirms.")
                .setParameters(
                    obj(
                        listOf("client_name", "lines"),
                        "client_name" to str("The client name exactly as given by the user."),
                        "lines" to linesSchema,
                    )
                )
        )
        .addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName("propose_credit_note")
                .setDescription("Prepares a credit note DRAFT to cancel an issued invoice. Invoices are immutable: cancelling or correcting one always goes through a credit note.")
                .setParameters(
                    obj(
                        listOf("invoice_number"),
                        "invoice_number" to int("The number of the invoice to cancel."),
                        "reason" to str("Reason for the credit note, if given."),
                    )
                )
        )
        .build()

    fun systemInstruction(lang: String, todayIso: String): Content {
        val text = if (lang == "fr") {
            """
            Tu es l'assistant de Snapfacture, une application de facturation pour artisans et indépendants.
            Nous sommes le $todayIso. La devise est l'euro.
            Tu ne peux RIEN modifier toi-même : tu appelles des fonctions de lecture, ou tu prépares des brouillons
            (propose_*) que l'utilisateur validera lui-même. Les factures émises sont immuables : toute correction
            passe par un avoir (propose_credit_note).
            N'invente jamais un montant, une quantité ou un nom de client. Si une information indispensable manque
            ou est ambiguë, pose la question ou utilise search_clients. Ne fais jamais de calculs : transmets les
            montants unitaires tels que donnés. Réponds en français.
            """.trimIndent()
        } else {
            """
            You are the assistant of Snapfacture, an invoicing app for tradespeople and freelancers.
            Today is $todayIso. The currency is US dollars.
            You cannot modify ANYTHING yourself: you either call read functions, or prepare drafts (propose_*)
            that the user reviews and confirms. Issued invoices are immutable: any correction goes through a
            credit note (propose_credit_note).
            Never invent an amount, a quantity or a client name. If a required piece of information is missing or
            ambiguous, ask, or use search_clients. Never do arithmetic: pass unit amounts exactly as given.
            Answer in English.
            """.trimIndent()
        }
        return Content.newBuilder()
            .setRole("system")
            .addParts(Part.newBuilder().setText(text))
            .build()
    }
}
