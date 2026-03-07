package me.voltual.pyrolysis.manager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.voltual.pyrolysis.ARG_PACKAGE_NAME
import me.voltual.pyrolysis.ARG_PACKAGE_NAMES
import me.voltual.pyrolysis.ARG_REPOSITORY_ID
import me.voltual.pyrolysis.ARG_REPOSITORY_IDS
import me.voltual.pyrolysis.BBQApplication
import me.voltual.pyrolysis.manager.installer.AppInstaller
import me.voltual.pyrolysis.feature.store.worker.WorkerManager
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ActionReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        const val COMMAND_CANCEL_SYNC = "cancel_sync"
        const val COMMAND_CANCEL_SYNC_ALL = "cancel_sync_all"
        const val COMMAND_CANCEL_DOWNLOAD = "cancel_download"
        const val COMMAND_CANCEL_DOWNLOAD_ALL = "cancel_download_all"
        const val COMMAND_CANCEL_INSTALL = "cancel_install"
        const val COMMAND_BATCH_UPDATE = "batch_update"
    }

    val installer: AppInstaller by inject()
    val wm: WorkerManager by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val pending = goAsync()
        val appScope = (context.applicationContext as BBQApplication).applicationScope

        appScope.launch {
            try {
                when (intent.action) {
                    COMMAND_CANCEL_DOWNLOAD     -> {
                        val packageName = intent.getStringExtra(ARG_PACKAGE_NAME)
                        wm.cancelDownload(packageName)
                    }

                    COMMAND_BATCH_UPDATE        -> {
                        val packageNames: Array<String> =
                            intent.getStringArrayExtra(ARG_PACKAGE_NAMES) ?: emptyArray()
                        val repoIds: Array<Long> =
                            intent.getLongArrayExtra(ARG_REPOSITORY_IDS)?.toTypedArray()
                                ?: emptyArray()
                        wm.update(*packageNames.zip(repoIds).toTypedArray())
                    }

                    COMMAND_CANCEL_DOWNLOAD_ALL -> {
                        wm.cancelDownloadAll()
                    }

                    COMMAND_CANCEL_SYNC         -> {
                        val repoId = intent.getLongExtra(ARG_REPOSITORY_ID, -1)
                        wm.cancelSyncAll()
                    }

                    COMMAND_CANCEL_SYNC_ALL     -> {
                        wm.cancelSyncAll()
                    }

                    COMMAND_CANCEL_INSTALL      -> {
                        intent.getStringExtra(ARG_PACKAGE_NAME)
                            ?.let { packageName ->
                                wm.cancelInstall(packageName)
                                installer.cancelInstall(packageName)
                            }
                    }

                    else                        -> {}
                }
            } finally {
                pending.finish()
            }
        }
    }
}