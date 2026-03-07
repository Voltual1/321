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
import androidx.compose.ui.util.fastFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
//import com.machiav3lli.derdiedas.config.BuildConfig
import me.voltual.pyrolysis.ARG_PACKAGE_NAME
import me.voltual.pyrolysis.ARG_VERSION_CODE
import me.voltual.pyrolysis.ARG_WORK_TYPE
import me.voltual.pyrolysis.BBQApplication
import me.voltual.pyrolysis.data.content.Cache
import me.voltual.pyrolysis.data.content.Preferences
import me.voltual.pyrolysis.core.database.entity.ExodusData
import me.voltual.pyrolysis.core.database.entity.ExodusInfo
import me.voltual.pyrolysis.core.database.entity.Tracker
import me.voltual.pyrolysis.core.database.entity.Trackers
import me.voltual.pyrolysis.feature.store.repository.PrivacyRepository
import me.voltual.pyrolysis.manager.network.Downloader
import org.koin.android.annotation.KoinWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinWorker
class ExodusWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val privacyRepository: PrivacyRepository by inject()

    override suspend fun doWork(): Result {
        val type = WorkType.entries[
            inputData.getInt(ARG_WORK_TYPE, 0)
        ]

        when (type) {
            WorkType.TRACKERS -> fetchTrackers()
            WorkType.DATA     -> fetchExodusData(
                inputData.getString(ARG_PACKAGE_NAME)!!,
                inputData.getLong(ARG_VERSION_CODE, -1)
            )
        }

        return Result.success()
    }

    private suspend fun fetchTrackers() {
        if (Preferences[Preferences.Key.ShowTrackers]) {
            runCatching {
                val url = "${EXODUS_API_BASE}/trackers"
                val tempFile = Cache.getTemporaryFile(context)
                try {
                    val result = Downloader.download(
                        url = url,
                        target = tempFile,
                        lastModified = Preferences[Preferences.Key.TrackersLastModified],
                        entityTag = "",
                        authentication = EXODUS_AUTHENTICATION,
                        rated = false,
                        callback = { _, _, _ -> }
                    )

                    when {
                        result.isNotModified -> {
                            Log.i(TAG, "Trackers not modified, skipping update")
                        }

                        result.success       -> {
                            if (!result.isNotModified) {
                                val trackerList = Trackers.fromStream(tempFile.inputStream())

                                // Update last modified timestamp
                                Preferences[Preferences.Key.TrackersLastModified] =
                                    result.lastModified

                                // Update DB with the trackers
                                privacyRepository.upsertTracker(
                                    trackerList.trackers
                                        .map { (key, value) ->
                                            Tracker(
                                                key.toInt(),
                                                value.name,
                                                value.network_signature,
                                                value.code_signature,
                                                value.creation_date,
                                                value.website,
                                                value.description,
                                                value.categories
                                            )
                                        }
                                )
                            } else {
                            }
                        }

                        else                 -> {
                            Log.w(TAG, "Failed to fetch trackers: ${result.statusCode}")
                        }
                    }
                } finally {
                    tempFile.delete()
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed fetching exodus trackers", e)
            }
        }
    }

    private suspend fun fetchExodusData(packageName: String, versionCode: Long) {
        if (Preferences[Preferences.Key.ShowTrackers]) {
            runCatching {
                val url = "${EXODUS_API_BASE}/search/$packageName/details"
                val tempFile = Cache.getTemporaryFile(context)

                try {
                    val result = Downloader.download(
                        url = url,
                        target = tempFile,
                        lastModified = "",
                        entityTag = "",
                        authentication = EXODUS_AUTHENTICATION,
                        rated = false,
                        callback = { _, _, _ -> }
                    )

                    if (result.success) {
                        val exodusDataList = ExodusData.listFromStream(tempFile.inputStream())

                        val sourceFiltered = exodusDataList.let {
                            it.fastFilter { info -> info.source == "fdroid" }
                                .ifEmpty { it }
                        }
                        val latestExodusApp = sourceFiltered
                            .fastFilter { it.version_code.toLong() == versionCode }
                            .firstOrNull()
                            ?: sourceFiltered.maxByOrNull { it.version_code.toLong() }
                            ?: ExodusInfo()

                        val exodusInfo = latestExodusApp.toExodusInfo(packageName)
                        privacyRepository.upsertExodusInfo(exodusInfo)
                    } else {
                        Log.w(
                            TAG,
                            "Failed to fetch exodus data for $packageName: ${result.statusCode}"
                        )
                    }
                } finally {
                    tempFile.delete()
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed fetching exodus info", e)
            }
        }
    }

    enum class WorkType { TRACKERS, DATA }

    companion object {
        private const val TAG = "ExodusWorker"
        private const val EXODUS_API_BASE = "https://reports.exodus-privacy.eu.org/api"
        private const val EXODUS_AUTHENTICATION = "Token 81f30e4903bde25023857719e71c94829a41e6a5"

        fun fetchTrackers() {
            val data = workDataOf(
                ARG_WORK_TYPE to WorkType.TRACKERS.ordinal,
            )

            BBQApplication.wm.enqueueUniqueWork(
                WorkType.TRACKERS.name,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ExodusWorker>()
                    .setInputData(data)
                    .addTag(WorkType.TRACKERS.toString())
                    .build()
            )
        }

        fun fetchExodusInfo(packageName: String, versionCode: Long) {
            val data = workDataOf(
                ARG_WORK_TYPE to WorkType.DATA.ordinal,
                ARG_PACKAGE_NAME to packageName,
                ARG_VERSION_CODE to versionCode,
            )

            BBQApplication.wm.enqueueUniqueWork(
                WorkType.TRACKERS.name,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ExodusWorker>()
                    .setInputData(data)
                    .addTag(WorkType.TRACKERS.toString())
                    .build()
            )
        }
    }
}