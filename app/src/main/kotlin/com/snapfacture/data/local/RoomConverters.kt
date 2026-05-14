package com.snapfacture.data.local

import androidx.room.TypeConverter
import com.snapfacture.data.local.entity.InvoiceStatus
import com.snapfacture.data.local.entity.InvoiceType
import com.snapfacture.data.local.entity.PaymentMethod

class RoomConverters {
    @TypeConverter fun statusToString(s: InvoiceStatus): String = s.name
    @TypeConverter fun statusFromString(s: String): InvoiceStatus = InvoiceStatus.valueOf(s)

    @TypeConverter fun paymentToString(p: PaymentMethod): String = p.name
    @TypeConverter fun paymentFromString(s: String): PaymentMethod = PaymentMethod.valueOf(s)

    @TypeConverter fun typeToString(t: InvoiceType): String = t.name
    @TypeConverter fun typeFromString(s: String): InvoiceType = InvoiceType.valueOf(s)
}
