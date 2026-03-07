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
import me.voltual.pyrolysis.ROW_ICON
import me.voltual.pyrolysis.ROW_LABEL
import me.voltual.pyrolysis.ROW_NAME
import me.voltual.pyrolysis.ROW_REPOSITORY_ID
import me.voltual.pyrolysis.TABLE_REPOCATEGORY
import me.voltual.pyrolysis.TABLE_REPOCATEGORY_TEMP
import me.voltual.pyrolysis.core.database.entity.CategoryDetails
import me.voltual.pyrolysis.core.database.entity.RepoCategory
import me.voltual.pyrolysis.core.database.entity.RepoCategoryTemp
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoCategoryDao : BaseDao<RepoCategory> {
    @Transaction
    @Query(
        """SELECT $ROW_NAME, $ROW_LABEL, $ROW_ICON
        FROM $TABLE_REPOCATEGORY
        GROUP BY $ROW_NAME HAVING 1
        ORDER BY $ROW_REPOSITORY_ID ASC"""
    )
    fun getAllCategoryDetails(): List<CategoryDetails>

    @Transaction
    @Query(
        """SELECT $ROW_NAME, $ROW_LABEL, $ROW_ICON
        FROM $TABLE_REPOCATEGORY
        GROUP BY $ROW_NAME HAVING 1
        ORDER BY $ROW_REPOSITORY_ID ASC"""
    )
    fun getAllCategoryDetailsFlow(): Flow<List<CategoryDetails>>

    @Query("DELETE FROM $TABLE_REPOCATEGORY WHERE $ROW_REPOSITORY_ID = :id")
    suspend fun deleteByRepoId(id: Long): Int

    @Query("DELETE FROM $TABLE_REPOCATEGORY")
    suspend fun emptyTable()
}

@Dao
interface RepoCategoryTempDao : BaseDao<RepoCategoryTemp> {
    @Query("SELECT * FROM $TABLE_REPOCATEGORY_TEMP")
    fun getAll(): Array<RepoCategoryTemp>

    @Query("DELETE FROM $TABLE_REPOCATEGORY_TEMP")
    suspend fun emptyTable()
}