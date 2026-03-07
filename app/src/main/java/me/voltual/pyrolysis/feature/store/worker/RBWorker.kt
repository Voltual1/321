/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import me.voltual.pyrolysis.ARG_EXCEPTION
import me.voltual.pyrolysis.BBQApplication
import me.voltual.pyrolysis.TAG_SYNC_PERIODIC
import me.voltual.pyrolysis.data.content.Cache
import me.voltual.pyrolysis.data.content.Preferences
import me.voltual.pyrolysis.core.database.entity.RBData
import me.voltual.pyrolysis.core.database.entity.RBLog
import me.voltual.pyrolysis.core.database.entity.RBLogs
import me.voltual.pyrolysis.feature.store.repository.PrivacyRepository
import me.voltual.pyrolysis.manager.network.Downloader
import org.koin.android.annotation.KoinWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinWorker
class RBWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val privacyRepository: PrivacyRepository by inject()

    override suspend fun doWork(): Result {
        return runCatching {
            fetchLogs()
        }.fold(
            onSuccess = {
                Result.success()
            },
            onFailure = {
                Log.e(TAG, "Failed fetching reproducible build logs", it)
                Result.failure(workDataOf(ARG_EXCEPTION to it.message))
            }
        )
    }

    private suspend fun fetchLogs() {
        val url = "${Preferences[Preferences.Key.RBProvider].url}/index.json"
        val lastModified = Preferences[Preferences.Key.RBLogsLastModified]

        // Create temporary file for download
        val tempFile = Cache.getTemporaryFile(context)

        try {
            val result = Downloader.download(
                url = url,
                target = tempFile,
                lastModified = lastModified,
                entityTag = "",
                authentication = "",
                rated = false,
                callback = { _, _, _ -> }
            )

            when {
                result.isNotModified -> {
                    Log.i(TAG, "RB index not modified")
                }

                result.success       -> {
                    // Update last modified preference
                    Preferences[Preferences.Key.RBLogsLastModified] = result.lastModified

                    // Parse and store logs
                    val logsMap = RBLogs.fromStream(tempFile.inputStream())
                    privacyRepository.upsertRBLogs(logsMap.toLogs())

                    Log.i(TAG, "Successfully fetched ${logsMap.size} RB logs")
                }

                else                 -> {
                    Log.w(TAG, "Failed to fetch RB index: ${result.statusCode}")
                }
            }
        } finally {
            // Clean up temporary file
            tempFile.delete()
        }
    }

    companion object {
        private const val TAG = "RBWorker"

        // TODO Make periodic instead of sync-bound
        fun fetchRBLogs() {
            if (Preferences[Preferences.Key.RBProvider] != Preferences.RBProvider.None) {
                BBQApplication.wm.enqueueUniqueWork(
                    "rb_index",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<RBWorker>()
                        .addTag(TAG_SYNC_PERIODIC)
                        .build()
                )
            }
        }
    }
}

private fun RBData.toLog(hash: String): RBLog = RBLog(
    hash = hash,
    repository = repository,
    apk_url = apk_url,
    appid = appid,
    version_code = version_code,
    version_name = version_name,
    tag = tag,
    commit = commit,
    timestamp = timestamp,
    reproducible = reproducible,
    error = error
)

private fun Map<String, List<RBData>>.toLogs(): List<RBLog> {
    return this.flatMap { (hash, data) ->
        data.map { it.toLog(hash) }
    }
}