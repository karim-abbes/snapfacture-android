package com.snapfacture.data.repository

import com.snapfacture.data.local.dao.CompanyDao
import com.snapfacture.data.local.entity.CompanyEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanyRepository @Inject constructor(private val dao: CompanyDao) {
    fun observe(): Flow<CompanyEntity?> = dao.observe()
    suspend fun get(): CompanyEntity? = dao.get()
    suspend fun update(company: CompanyEntity) = dao.update(company)
    suspend fun peekNextInvoiceNumber(): Int = dao.peekNextInvoiceNumber()
    suspend fun bumpInvoiceNumber() = dao.bumpInvoiceNumber()
}
