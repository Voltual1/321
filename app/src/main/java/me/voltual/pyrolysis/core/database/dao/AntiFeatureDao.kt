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
import me.voltual.pyrolysis.ROW_DESCRIPTION
import me.voltual.pyrolysis.ROW_ICON
import me.voltual.pyrolysis.ROW_LABEL
import me.voltual.pyrolysis.ROW_NAME
import me.voltual.pyrolysis.ROW_REPOSITORY_ID
import me.voltual.pyrolysis.TABLE_ANTIFEATURE
import me.voltual.pyrolysis.TABLE_ANTIFEATURE_TEMP
import me.voltual.pyrolysis.core.database.entity.AntiFeature
import me.voltual.pyrolysis.core.database.entity.AntiFeatureDetails
import me.voltual.pyrolysis.core.database.entity.AntiFeatureTemp
import kotlinx.coroutines.flow.Flow

@Dao
interface AntiFeatureDao : BaseDao<AntiFeature> {
    @Transaction
    @Query(
        """SELECT $ROW_NAME, $ROW_LABEL, $ROW_DESCRIPTION, $ROW_ICON
        FROM $TABLE_ANTIFEATURE
        GROUP BY $ROW_NAME HAVING 1
        ORDER BY $ROW_REPOSITORY_ID ASC"""
    )
    fun getAllAntiFeatureDetails(): List<AntiFeatureDetails>

    @Transaction
    @Query(
        """SELECT $ROW_NAME, $ROW_LABEL, $ROW_DESCRIPTION, $ROW_ICON
        FROM $TABLE_ANTIFEATURE
        GROUP BY $ROW_NAME HAVING 1
        ORDER BY $ROW_REPOSITORY_ID ASC"""
    )
    fun getAllAntiFeatureDetailsFlow(): Flow<List<AntiFeatureDetails>>

    @Query("DELETE FROM $TABLE_ANTIFEATURE WHERE $ROW_REPOSITORY_ID = :id")
    suspend fun deleteByRepoId(id: Long): Int

    @Query("DELETE FROM $TABLE_ANTIFEATURE")
    suspend fun emptyTable()
}

@Dao
interface AntiFeatureTempDao : BaseDao<AntiFeatureTemp> {
    @Query("SELECT * FROM $TABLE_ANTIFEATURE_TEMP")
    fun getAll(): Array<AntiFeatureTemp>

    @Query("DELETE FROM $TABLE_ANTIFEATURE_TEMP")
    suspend fun emptyTable()
}