/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.worker

import android.app.NotificationChannel
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import android.app.NotificationManager
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import me.voltual.pyrolysis.ContextWrapperX
import me.voltual.pyrolysis.NOTIFICATION_CHANNEL_DOWNLOADING
import me.voltual.pyrolysis.NOTIFICATION_CHANNEL_DOWNLOAD_STATS
import me.voltual.pyrolysis.NOTIFICATION_CHANNEL_SYNCING
import me.voltual.pyrolysis.NOTIFICATION_CHANNEL_UPDATES
import me.voltual.pyrolysis.NOTIFICATION_CHANNEL_VULNS
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.TAG_BATCH_SYNC_ONETIME
import me.voltual.pyrolysis.TAG_BATCH_SYNC_PERIODIC
import me.voltual.pyrolysis.data.content.Preferences
import me.voltual.pyrolysis.feature.store.repository.InstalledRepository
import me.voltual.pyrolysis.feature.store.repository.InstallsRepository
import me.voltual.pyrolysis.feature.store.repository.ProductsRepository
import me.voltual.pyrolysis.feature.store.repository.RepositoriesRepository
import me.voltual.pyrolysis.manager.installer.AppInstaller
import me.voltual.pyrolysis.manager.service.ActionReceiver
import me.voltual.pyrolysis.core.utils.Utils
import me.voltual.pyrolysis.core.utils.extension.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

class WorkerManager(private val appContext: Context) : KoinComponent {

    private val workManager: WorkManager by inject()
    private val actionReceiver: ActionReceiver by inject()
    private val langContext: Context = ContextWrapperX.wrap(appContext)
    private val notificationManager: NotificationManagerCompat by inject()
    private val productRepo: ProductsRepository by inject()
    private val reposRepo: RepositoriesRepository by inject()
    private val installedRepo: InstalledRepository by inject()
    private val installsRepo: InstallsRepository by inject()
    private val installer: AppInstaller by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        appContext.registerReceiver(actionReceiver, IntentFilter())
        if (Android.sdk(Build.VERSION_CODES.O)) createNotificationChannels()

        workManager.pruneWork()
        monitorWorkProgress()
    }

    fun release(): WorkerManager? {
        appContext.unregisterReceiver(actionReceiver)
        scope.cancel()
        return null
    }

    fun prune() {
        workManager.pruneWork()
    }

    private fun monitorWorkProgress() {
        scope.launch {
            while (isActive) {
                try {
                    workManager.getWorkInfos(
                        WorkQuery.Builder
                            .fromStates(listOf(WorkInfo.State.RUNNING))
                            .build()
                    ).get().filter { it.runAttemptCount > 5 }.forEach { wi ->
                        workManager.cancelWorkById(wi.id)
                    }

                    try {
                        val healthCheckCleaned = installer.checkQueueHealth()
                        if (healthCheckCleaned) {
                            Log.d(TAG, "Periodic queue health check performed cleanup")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during periodic queue health check", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in work monitoring", e)
                }
                delay(TimeUnit.MINUTES.toMillis(5))
            }
        }
    }

    fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        request: OneTimeWorkRequest
    ) = workManager.enqueueUniqueWork(uniqueWorkName, existingWorkPolicy, request)

    internal fun updatePeriodicSyncJob(force: Boolean) {
        val reschedule =
            force || workManager.getWorkInfosForUniqueWork(TAG_BATCH_SYNC_PERIODIC).get().isEmpty()
        if (reschedule) {
            when (val autoSync = Preferences[Preferences.Key.AutoSync]) {
                is Preferences.AutoSync.Never,
                    -> {
                    workManager.cancelUniqueWork(TAG_BATCH_SYNC_PERIODIC)
                    Log.i(this::javaClass.name, "Canceled next auto-sync run.")
                }

                is Preferences.AutoSync.Always,
                is Preferences.AutoSync.Wifi,
                is Preferences.AutoSync.WifiBattery,
                is Preferences.AutoSync.Battery,
                    -> {
                    autoSync(
                        connectionType = autoSync.connectionType(),
                        chargingBattery = autoSync.requireBattery(),
                    )
                }
            }
        }
    }

    private fun autoSync(
        connectionType: NetworkType,
        chargingBattery: Boolean = false,
    ) {
        BatchSyncWorker.enqueuePeriodic(
            connectionType = connectionType,
            chargingBattery = chargingBattery,
        )
    }

    fun cancelSyncAll() {
        BatchSyncWorker::class.qualifiedName?.let {
            workManager.cancelAllWorkByTag(TAG_BATCH_SYNC_ONETIME)
            prune()
        }
    }

    fun cancelDownloadAll() {
        DownloadWorker::class.qualifiedName?.let {
            workManager.cancelAllWorkByTag(it)
            prune()
        }
    }

    fun cancelDownload(packageName: String?) {
        DownloadWorker::class.qualifiedName?.let {
            workManager.cancelAllWorkByTag(
                if (packageName != null) "download_$packageName"
                else it
            )
        }
    }

    fun cancelInstall(packageName: String) {
        DownloadWorker::class.qualifiedName?.let {
            workManager.cancelUniqueWork("Installer_$packageName")
            scope.launch {
                installsRepo.delete(packageName)
            }
            //prune()
        }
    }

    fun install(vararg product: Pair<String, Long>) = batchUpdate(product.toList(), true)

    fun update(vararg product: Pair<String, Long>) = batchUpdate(product.toList(), false)

    private fun batchUpdate(productItems: List<Pair<String, Long>>, enforce: Boolean = false) {
        scope.launch {
            productItems.map { (packageName, repoId) ->
                async {
                    val installed = installedRepo.load(packageName)
                    val repo = reposRepo.load(repoId)

                    if ((enforce || installed != null) && repo != null) {
                        Triple(packageName, installed, repo)
                    } else null
                }
            }.awaitAll()
                .filterNotNull()
                .forEach { (packageName, installed, repo) ->
                    val productRepository = productRepo.loadProduct(packageName)
                        .filter { eProduct -> eProduct.product.repositoryId == repo.id }
                        .map { eProduct -> Pair(eProduct, repo) }
                    Utils.startUpdate(
                        packageName,
                        installed,
                        productRepository
                    )
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannels() {
        NotificationChannel(
            NOTIFICATION_CHANNEL_DOWNLOADING,
            langContext.getString(R.string.downloading),
            NotificationManager.IMPORTANCE_LOW
        )
            .apply { setShowBadge(false) }
            .let(notificationManager::createNotificationChannel)
        NotificationChannel(
            NOTIFICATION_CHANNEL_SYNCING,
            langContext.getString(R.string.syncing),
            NotificationManager.IMPORTANCE_LOW
        )
            .apply { setShowBadge(false) }
            .let(notificationManager::createNotificationChannel)
        NotificationChannel(
            NOTIFICATION_CHANNEL_UPDATES,
            langContext.getString(R.string.updates), NotificationManager.IMPORTANCE_LOW
        ).let(notificationManager::createNotificationChannel)
        NotificationChannel(
            NOTIFICATION_CHANNEL_VULNS,
            langContext.getString(R.string.vulnerabilities), NotificationManager.IMPORTANCE_HIGH
        ).let(notificationManager::createNotificationChannel)
        NotificationChannel(
            NOTIFICATION_CHANNEL_DOWNLOAD_STATS,
            langContext.getString(R.string.download_stats),
            NotificationManager.IMPORTANCE_LOW,
        )
            .apply { setShowBadge(false) }
            .let(notificationManager::createNotificationChannel)
    }

    companion object {
        const val TAG = "WorkerManager"
    }
}

val workmanagerModule = module {
    single { WorkerManager(get()) }
    single { WorkManager.getInstance(get()) }
    single { ActionReceiver() }
    single { NotificationManagerCompat.from(get()) }
    singleOf(::UpdatesNotificationManager)
}
