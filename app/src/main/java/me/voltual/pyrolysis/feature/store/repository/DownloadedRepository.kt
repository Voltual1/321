/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.core.database.dao.DownloadedDao
import me.voltual.pyrolysis.core.database.entity.Downloaded
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadedRepository(
    private val downloadedDao: DownloadedDao,
) {
    fun getAllFlow() = downloadedDao.getAllFlow()

    fun getLatestFlow(packageName: Flow<String>) = packageName.flatMapLatest {
        downloadedDao.getLatestFlow(it)
    }

    suspend fun update(value: Downloaded) {
        downloadedDao.upsert(value)
    }

    suspend fun delete(downloaded: Downloaded) {
        downloadedDao.delete(
            downloaded.packageName,
            downloaded.version,
            downloaded.repositoryId,
            downloaded.cacheFileName
        )
    }
}