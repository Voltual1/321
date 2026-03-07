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
import me.voltual.pyrolysis.ROW_ENABLED
import me.voltual.pyrolysis.ROW_ID
import me.voltual.pyrolysis.ROW_NAME
import me.voltual.pyrolysis.ROW_REPOSITORY_ID
import me.voltual.pyrolysis.TABLE_CATEGORY
import me.voltual.pyrolysis.TABLE_CATEGORY_TEMP
import me.voltual.pyrolysis.TABLE_REPOSITORY
import me.voltual.pyrolysis.core.database.entity.Category
import me.voltual.pyrolysis.core.database.entity.CategoryTemp
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao : BaseDao<Category> {
    @Query(
        """SELECT DISTINCT $TABLE_CATEGORY.$ROW_NAME
        FROM $TABLE_CATEGORY
        JOIN $TABLE_REPOSITORY
        ON $TABLE_CATEGORY.$ROW_REPOSITORY_ID = $TABLE_REPOSITORY.$ROW_ID
        WHERE $TABLE_REPOSITORY.$ROW_ENABLED != 0"""
    )
    fun getAllNames(): List<String>

    @Query(
        """SELECT DISTINCT $TABLE_CATEGORY.$ROW_NAME
        FROM $TABLE_CATEGORY
        JOIN $TABLE_REPOSITORY
        ON $TABLE_CATEGORY.$ROW_REPOSITORY_ID = $TABLE_REPOSITORY.$ROW_ID
        WHERE $TABLE_REPOSITORY.$ROW_ENABLED != 0"""
    )
    fun getAllNamesFlow(): Flow<List<String>>

    @Query("DELETE FROM $TABLE_CATEGORY WHERE $ROW_REPOSITORY_ID = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM $TABLE_CATEGORY")
    suspend fun emptyTable()
}

@Dao
interface CategoryTempDao : BaseDao<CategoryTemp> {
    @Query("SELECT * FROM $TABLE_CATEGORY_TEMP")
    fun getAll(): Array<CategoryTemp>

    @Query("DELETE FROM $TABLE_CATEGORY_TEMP")
    suspend fun emptyTable()
}