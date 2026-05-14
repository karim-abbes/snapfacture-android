package com.snapfacture.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.snapfacture.data.local.entity.ClientEntity
import com.snapfacture.data.local.entity.InvoiceEntity
import com.snapfacture.data.local.entity.InvoiceLineEntity

data class InvoiceWithDetails(
    @Embedded val invoice: InvoiceEntity,
    @Relation(parentColumn = "clientId", entityColumn = "id")
    val client: ClientEntity,
    @Relation(parentColumn = "id", entityColumn = "invoiceId")
    val lines: List<InvoiceLineEntity>,
)
