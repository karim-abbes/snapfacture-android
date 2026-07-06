package com.snapfacture.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.snapfacture.data.local.entity.ClientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE name LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY name COLLATE NOCASE LIMIT 20")
    suspend fun search(query: String): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun get(id: Long): ClientEntity?

    @Query("SELECT * FROM clients ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ClientEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(client: ClientEntity): Long

    @Update
    suspend fun update(client: ClientEntity)
}
