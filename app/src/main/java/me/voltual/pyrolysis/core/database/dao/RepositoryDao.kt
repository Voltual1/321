/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import me.voltual.pyrolysis.core.database.entity.LatestSyncs
import me.voltual.pyrolysis.core.database.entity.Repository
import kotlinx.coroutines.flow.Flow

@Dao
interface RepositoryDao : BaseDao<Repository> {
    @Query("SELECT COUNT(id) FROM repository")
    suspend fun getCount(): Int

    @Insert
    suspend fun insertReturn(repo: Repository): Long

    suspend fun put(vararg repository: Repository) {
        val (toUpdate, toInsert) = repository.partition { it.id > 0L }
        insert(*toInsert.toTypedArray())
        update(*toUpdate.toTypedArray())
    }

    suspend fun insertOrUpdate(vararg repos: Repository) {
        val toUpsert = mutableSetOf<Repository>()
        val toInsert = mutableSetOf<Repository>()
        repos.forEach { repository ->
            val old = getByAddress(repository.address)
            if (old != null) toUpsert.add(repository.copy(id = old.id))
            else toInsert.add(repository.copy(id = 0L))
        }
        insert(*toInsert.toTypedArray())
        upsert(*toUpsert.toTypedArray())
    }

    @Query("SELECT * FROM repository WHERE id = :id")
    suspend fun get(id: Long): Repository?

    @Query("SELECT * FROM repository WHERE id = :id")
    fun getFlow(id: Long): Flow<Repository?>

    @Query("SELECT * FROM repository ORDER BY id ASC")
    suspend fun getAll(): List<Repository>

    @Query("SELECT * FROM repository ORDER BY id ASC")
    fun getAllFlow(): Flow<List<Repository>>

    @Query("SELECT * FROM repository ORDER BY id ASC")
    fun getAllMapFlow(): Flow<Map<@MapColumn("id") Long, Repository>>

    @Query("SELECT * FROM repository WHERE enabled != 0 ORDER BY id ASC")
    fun getAllEnabledFlow(): Flow<List<Repository>>

    @Query("SELECT * FROM repository WHERE address = :address")
    suspend fun getByAddress(address: String): Repository?

    @Query("SELECT name FROM repository WHERE id = :id")
    suspend fun getRepoName(id: Long): String

    @Query("SELECT id FROM repository WHERE enabled != 0 ORDER BY id ASC")
    suspend fun getAllEnabledIds(): List<Long>

    @Query("SELECT id FROM repository WHERE enabled == 0 ORDER BY id ASC")
    suspend fun getAllDisabledIds(): List<Long>

    @Query("UPDATE repository SET timestamp = '', lastModified = '', entryLastModified = '', entityTag = '', entryEntityTag = ''")
    suspend fun forgetLastModifications()

    @Query("DELETE FROM repository WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM repository WHERE address = :address")
    suspend fun deleteByAddress(address: String)

    @Query("SELECT MAX(id) FROM repository")
    suspend fun latestAddedId(): Long

    @Query("SELECT MAX(updated) AS latest, MIN(updated) AS latestAll FROM repository WHERE enabled != 0")
    fun latestUpdatesFlow(): Flow<LatestSyncs>

    @Query("SELECT EXISTS(SELECT 1 FROM repository WHERE address = :address LIMIT 1)")
    suspend fun isDuplicateAddress(address: String): Boolean

    @Query("DELETE FROM repository")
    suspend fun emptyTable()
}