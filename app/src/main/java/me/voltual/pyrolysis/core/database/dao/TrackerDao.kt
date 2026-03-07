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
import androidx.room.Query
import androidx.room.Transaction
import me.voltual.pyrolysis.core.database.entity.Tracker
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao : BaseDao<Tracker> {
    @Query("SELECT * FROM tracker")
    fun getAll(): List<Tracker>

    @Query("SELECT * FROM tracker")
    fun getAllFlow(): Flow<List<Tracker>>

    @Query("SELECT * FROM tracker WHERE key = :key")
    fun get(key: Int): Tracker?

    @Query("SELECT * FROM tracker WHERE key = :key")
    fun getFlow(key: Int): Flow<Tracker?>

    @Transaction
    suspend fun multipleUpserts(updates: Collection<Tracker>) {
        updates.forEach { metadata ->
            upsert(metadata)
        }
    }
}
