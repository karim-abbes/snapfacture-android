package com.snapfacture.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.snapfacture.data.local.entity.ClientEntity
import com.snapfacture.data.local.entity.QuoteEntity
import com.snapfacture.data.local.entity.QuoteLineEntity

data class QuoteWithDetails(
    @Embedded val quote: QuoteEntity,
    @Relation(parentColumn = "clientId", entityColumn = "id")
    val client: ClientEntity,
    @Relation(parentColumn = "id", entityColumn = "quoteId")
    val lines: List<QuoteLineEntity>,
)
