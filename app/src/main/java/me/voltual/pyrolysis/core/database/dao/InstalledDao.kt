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
import me.voltual.pyrolysis.core.database.entity.Installed
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledDao : BaseDao<Installed> {
    @Query("SELECT * FROM memory_installed")
    fun getAllFlow(): Flow<List<Installed>>

    @Query("SELECT * FROM memory_installed WHERE packageName = :packageName")
    suspend fun get(packageName: String): Installed?

    @Query("SELECT * FROM memory_installed WHERE packageName = :packageName")
    fun getFlow(packageName: String): Flow<Installed?>

    @Query("DELETE FROM memory_installed WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM memory_installed")
    suspend fun emptyTable()
}