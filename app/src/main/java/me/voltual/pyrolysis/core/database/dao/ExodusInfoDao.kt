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
import me.voltual.pyrolysis.core.database.entity.ExodusInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface ExodusInfoDao : BaseDao<ExodusInfo> {
    @Query("SELECT * FROM exodus_info WHERE packageName = :packageName")
    fun get(packageName: String): List<ExodusInfo>

    @Query("SELECT * FROM exodus_info WHERE packageName = :packageName")
    fun getFlow(packageName: String): Flow<List<ExodusInfo>>
}
