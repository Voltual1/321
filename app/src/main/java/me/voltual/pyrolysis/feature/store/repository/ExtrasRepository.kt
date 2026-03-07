/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.core.database.dao.ExtrasDao
import me.voltual.pyrolysis.core.database.entity.Extras
import kotlinx.coroutines.flow.Flow

class ExtrasRepository(
    private val extrasDao: ExtrasDao,
) {
    fun get(packageName: String): Flow<Extras?> = extrasDao.getFlow(packageName)

    fun getAll(): Flow<List<Extras>> = extrasDao.getAllFlow()

    fun getAllFavorites(): Flow<List<String>> = extrasDao.getFavoritesFlow()

    suspend fun load(packageName: String): Extras? = extrasDao[packageName]

    suspend fun upsert(vararg extras: Extras) = extrasDao.upsert(*extras)

    suspend fun upsertExtra(packageName: String, updateFunc: suspend ExtrasDao.(Extras?) -> Unit) =
        extrasDao.upsertExtra(packageName, updateFunc)

    suspend fun setIgnoredVersion(packageName: String, versionCode: Long) =
        upsertExtra(packageName) {
            if (it != null) updateIgnoredVersion(packageName, versionCode)
            else insert(Extras(packageName, ignoredVersion = versionCode))
        }

    suspend fun setIgnoreUpdates(packageName: String, setBoolean: Boolean) =
        upsertExtra(packageName) {
            if (it != null) updateIgnoreUpdates(packageName, setBoolean)
            else insert(Extras(packageName, ignoreUpdates = setBoolean))
        }

    suspend fun setIgnoreVulns(packageName: String, setBoolean: Boolean) =
        upsertExtra(packageName) {
            if (it != null) updateIgnoreVulns(packageName, setBoolean)
            else insert(Extras(packageName, ignoreVulns = setBoolean))
        }

    suspend fun setFavorite(packageName: String, setBoolean: Boolean) =
        upsertExtra(packageName) {
            if (it != null) updateFavorite(packageName, setBoolean)
            else insert(Extras(packageName, favorite = setBoolean))
        }
}