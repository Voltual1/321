/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.core.database.dao.DownloadStatsDao
import me.voltual.pyrolysis.core.database.dao.DownloadStatsFileDao
import me.voltual.pyrolysis.core.database.dao.ExodusInfoDao
import me.voltual.pyrolysis.core.database.dao.RBLogDao
import me.voltual.pyrolysis.core.database.dao.TrackerDao
import me.voltual.pyrolysis.core.database.entity.ClientPackageSum
import me.voltual.pyrolysis.core.database.entity.DownloadStats
import me.voltual.pyrolysis.core.database.entity.DownloadStatsFileMetadata
import me.voltual.pyrolysis.core.database.entity.ExodusInfo
import me.voltual.pyrolysis.core.database.entity.MonthlyPackageSum
import me.voltual.pyrolysis.core.database.entity.PackageSum
import me.voltual.pyrolysis.core.database.entity.RBLog
import me.voltual.pyrolysis.core.database.entity.Tracker
import me.voltual.pyrolysis.core.utils.extension.text.getIsoDateOfMonthsAgo
import me.voltual.pyrolysis.core.utils.extension.text.isoDateToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyRepository(
    private val trackerDao: TrackerDao,
    private val rbDao: RBLogDao,
    private val exodusDao: ExodusInfoDao,
    private val downloadStatsDao: DownloadStatsDao,
    private val dsFileDao: DownloadStatsFileDao,
) {
    fun getAllTrackers() = trackerDao.getAllFlow()

    fun getExodusInfos(packageName: String): Flow<List<ExodusInfo>> = exodusDao.getFlow(packageName)

    fun getRBLogs(packageName: String): Flow<List<RBLog>> = rbDao.getFlow(packageName)

    fun getRBLogsMap(packageName: String): Flow<Map<String, RBLog>> = rbDao.getFlow(packageName)
        .mapLatest { list ->
            list.groupBy(RBLog::hash)
                .mapValues { (_, rbDataList) -> rbDataList.maxByOrNull(RBLog::timestamp)!! }
        }

    fun getDownloadStats(packageName: String): Flow<List<DownloadStats>> =
        downloadStatsDao.getFlow(packageName)

    fun getLatestDownloadStats(packageName: String): Flow<List<DownloadStats>> =
        downloadStatsDao.getFlowSince(packageName, getIsoDateOfMonthsAgo(3).isoDateToInt())

    fun getSumDownloadStats(packageName: String): Flow<PackageSum> =
        downloadStatsDao.getFlowPackageSum(packageName)

    fun getSumDownloadOrder(packageName: String): Flow<Int> =
        downloadStatsDao.getFlowPackageSumOrder(packageName)

    fun getSumDownloadOrderLegacy(packageName: String): Flow<Int> =
        downloadStatsDao.getFlowPackageSumOrderLegacy(packageName)

    fun getClientSumDownloadStats(packageName: String): Flow<List<ClientPackageSum>> =
        downloadStatsDao.getFlowClientSumForPackage(packageName)

    fun getMonthlyDownloadStats(packageName: String): Flow<List<MonthlyPackageSum>> =
        downloadStatsDao.getFlowMonthlySumForPackage(packageName)

    suspend fun loadDownloadStatsModifiedMap(): Map<String, String> =
        dsFileDao.getLastModifiedDates()

    suspend fun upsertTracker(trackers: Collection<Tracker>) {
        trackerDao.multipleUpserts(trackers.toList())
    }

    suspend fun upsertRBLogs(logs: Collection<RBLog>) {
        rbDao.multipleUpserts(logs.toList())
    }

    suspend fun upsertExodusInfo(infos: ExodusInfo) {
        exodusDao.upsert(infos)
    }

    suspend fun upsertDownloadStats(downloadStats: Collection<DownloadStats>) {
        downloadStatsDao.upsert(*downloadStats.toTypedArray())
    }

    suspend fun upsertDownloadStatsFileMetadata(metadata: Collection<DownloadStatsFileMetadata>) {
        dsFileDao.multipleUpserts(metadata)
    }

    suspend fun upsertDownloadStatsFileMetadata(
        fileName: String,
        lastModified: String,
        fileSize: Long? = null,
        recordsCount: Int? = null
    ) {
        val metadata = DownloadStatsFileMetadata(
            fileName = fileName,
            lastModified = lastModified,
            lastFetched = System.currentTimeMillis(),
            fetchSuccess = true,
            fileSize = fileSize,
            recordsCount = recordsCount,
        )
        dsFileDao.upsert(metadata)
    }
}

val privacyModule = module {
}