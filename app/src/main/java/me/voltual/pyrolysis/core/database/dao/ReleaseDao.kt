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
import me.voltual.pyrolysis.core.database.entity.Release
import me.voltual.pyrolysis.core.database.entity.ReleaseTemp

@Dao
interface ReleaseDao : BaseDao<Release> {
    // This one for the mode combining releases of different sources
    @Query("SELECT * FROM `release` WHERE packageName = :packageName")
    fun get(packageName: String): List<Release>

    // This one for the separating releases of different sources
    @Query("SELECT * FROM `release` WHERE packageName = :packageName AND signature = :signature")
    fun get(packageName: String, signature: String): List<Release>

    @Query("DELETE FROM `release` WHERE repositoryId = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM `release`")
    suspend fun emptyTable()
}

@Dao
interface ReleaseTempDao : BaseDao<ReleaseTemp> {
    @Query("SELECT * FROM temporary_release")
    fun getAll(): Array<ReleaseTemp>

    @Query("DELETE FROM temporary_release")
    suspend fun emptyTable()
}
