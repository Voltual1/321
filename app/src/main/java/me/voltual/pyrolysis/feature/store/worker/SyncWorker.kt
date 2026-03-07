/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.worker

import me.voltual.pyrolysis.DOWNLOAD_STATS_SYNC
import me.voltual.pyrolysis.EXODUS_TRACKERS_SYNC
import me.voltual.pyrolysis.BBQApplication
import me.voltual.pyrolysis.RB_LOGS_SYNC
import me.voltual.pyrolysis.core.database.entity.Repository
import me.voltual.pyrolysis.data.entity.SyncRequest
import me.voltual.pyrolysis.feature.store.repository.RepositoriesRepository
import org.koin.java.KoinJavaComponent.get

object SyncWorker {
    fun enqueueManual(vararg repos: Pair<Long, String>) {
        repos.forEach { (repoId, _) ->

            when (repoId) {
                EXODUS_TRACKERS_SYNC -> ExodusWorker.fetchTrackers()
                RB_LOGS_SYNC         -> RBWorker.fetchRBLogs()
                DOWNLOAD_STATS_SYNC  -> DownloadStatsWorker.enqueuePeriodic()
                else                 -> {
                    BatchSyncWorker.enqueue(
                        request = SyncRequest.MANUAL,
                        repositoryIds = setOf(repoId),
                    )
                }
            }
        }
    }

    suspend fun enableRepo(repository: Repository, enabled: Boolean): Boolean {
        val reposRepo = get<RepositoriesRepository>(RepositoriesRepository::class.java)
        reposRepo.upsert(repository.enable(enabled))
        val isEnabled = !repository.enabled && repository.lastModified.isEmpty()
        val cooldownedSync = System.currentTimeMillis() -
                BBQApplication.latestSyncs.getOrDefault(repository.id, 0L) >=
                10_000L
        if (enabled && isEnabled && cooldownedSync) {
            BBQApplication.latestSyncs[repository.id] = System.currentTimeMillis()
            BatchSyncWorker.enqueue(SyncRequest.MANUAL, setOf(repository.id))
        } else {
            BBQApplication.wm.cancelSyncAll()
            BBQApplication.db.cleanUp(Pair(repository.id, false))
        }
        return true
    }

    suspend fun deleteRepo(repoId: Long): Boolean {
        val reposRepo = get<RepositoriesRepository>(RepositoriesRepository::class.java)
        val repository = reposRepo.load(repoId)
        return repository != null && run {
            enableRepo(repository, false)
            reposRepo.deleteById(repoId)
            true
        }
    }
}
