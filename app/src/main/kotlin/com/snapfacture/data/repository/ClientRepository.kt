package com.snapfacture.data.repository

import com.snapfacture.data.local.dao.ClientDao
import com.snapfacture.data.local.entity.ClientEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRepository @Inject constructor(private val dao: ClientDao) {
    fun observeAll(): Flow<List<ClientEntity>> = dao.observeAll()
    suspend fun search(query: String): List<ClientEntity> =
        if (query.isBlank()) emptyList() else dao.search(query.trim())
    suspend fun get(id: Long): ClientEntity? = dao.get(id)
    suspend fun recent(limit: Int = 5): List<ClientEntity> = dao.recent(limit)
    suspend fun insert(client: ClientEntity): Long = dao.insert(client)
    suspend fun update(client: ClientEntity) = dao.update(client)

    /**
     * If a client with the same name exists, merge non-blank fields into it
     * and return its id. Otherwise insert a new one.
     */
    suspend fun upsertByName(
        name: String,
        phone: String? = null,
        email: String? = null,
        addressLine: String? = null,
        siret: String? = null,
    ): Long {
        val trimmedName = name.trim()
        val cleanSiret = siret?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
        val existing = dao.search(trimmedName).firstOrNull { it.name.equals(trimmedName, ignoreCase = true) }
        if (existing == null) {
            return dao.insert(
                ClientEntity(
                    name = trimmedName,
                    phone = phone?.takeIf { it.isNotBlank() },
                    email = email?.takeIf { it.isNotBlank() },
                    addressLine = addressLine?.takeIf { it.isNotBlank() },
                    siret = cleanSiret,
                )
            )
        }
        val merged = existing.copy(
            phone = phone?.takeIf { it.isNotBlank() } ?: existing.phone,
            email = email?.takeIf { it.isNotBlank() } ?: existing.email,
            addressLine = addressLine?.takeIf { it.isNotBlank() } ?: existing.addressLine,
            siret = cleanSiret ?: existing.siret,
        )
        if (merged != existing) dao.update(merged)
        return existing.id
    }
}
