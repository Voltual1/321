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
import me.voltual.pyrolysis.core.database.entity.RBLog
import kotlinx.coroutines.flow.Flow

@Dao
interface RBLogDao : BaseDao<RBLog> {
    @Query("SELECT * FROM rb_log WHERE packageName = :packageName")
    fun get(packageName: String): List<RBLog>

    @Query("SELECT * FROM rb_log WHERE packageName = :packageName")
    fun getFlow(packageName: String): Flow<List<RBLog>>

    @Transaction
    suspend fun multipleUpserts(updates: Collection<RBLog>) {
        updates.forEach { metadata ->
            upsert(metadata)
        }
    }
}
